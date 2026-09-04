package com.tvdinner.data.repository

import android.util.Log
import com.tvdinner.data.model.*
import com.tvdinner.data.network.XtreamApiClient
import com.tvdinner.data.network.YouTubeMusicService
import com.tvdinner.data.network.YouTubePodcastService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class CatalogManager(
    private val authRepo: AuthRepository,
    private val apiClient: XtreamApiClient,
    private val podcastService: YouTubePodcastService,
    private val musicService: YouTubeMusicService = YouTubeMusicService()
) {
    private val tag = "CatalogManager"
    private val mutex = Mutex()

    // In-Memory Music Cache
    private val cachedMusicArtistsByGenre = mutableMapOf<String, List<MusicArtist>>()
    private val cachedMusicVideosByArtist = mutableMapOf<String, List<MusicVideo>>()

    // In-Memory Live TV Cache
    var cachedLiveCategories: List<LiveCategory>? = null
        private set
    var cachedLiveChannels: List<Channel>? = null
        private set
    private val cachedLiveChannelsByCat = mutableMapOf<String, List<Channel>>()
    private val cachedEpgByStreamId = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val cachedFullEpgByStreamId = java.util.concurrent.ConcurrentHashMap<Int, ShortEpgResponse>()

    fun parseEpgEpoch(rawTimestamp: String?, rawDateStr: String?): Long? {
        val num = rawTimestamp?.toLongOrNull() ?: rawDateStr?.toLongOrNull()
        if (num != null) {
            if (num > 100000000000L) return num / 1000L
            if (num > 1000000000L) return num
        }
        val str = rawDateStr?.trim() ?: rawTimestamp?.trim() ?: return null
        if (str.isBlank()) return null
        val patterns = arrayOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyyMMddHHmmss",
            "yyyyMMddHHmmss Z"
        )
        for (p in patterns) {
            try {
                val sdf = java.text.SimpleDateFormat(p, java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val d = sdf.parse(str)
                if (d != null) return d.time / 1000L
            } catch (_: Exception) {}
        }
        return null
    }

    fun resolveCurrentProgram(listings: List<EpgProgram>?): EpgProgram? {
        if (listings.isNullOrEmpty()) return null
        val currentEpoch = System.currentTimeMillis() / 1000L

        // Parse and sort valid listings by start epoch
        val parsedListings = listings.mapNotNull { prog ->
            val startSec = parseEpgEpoch(prog.startTimestamp, prog.start)
            val stopSec = parseEpgEpoch(prog.stopTimestamp, prog.end)
            if (startSec != null && stopSec != null && stopSec > startSec) {
                Triple(prog, startSec, stopSec)
            } else if (startSec != null) {
                Triple(prog, startSec, startSec + 3600L)
            } else {
                null
            }
        }.sortedBy { it.second }

        // 1. Exact current time window match
        val currentWindow = parsedListings.firstOrNull { (_, startSec, stopSec) ->
            currentEpoch in startSec until stopSec
        }
        if (currentWindow != null) return currentWindow.first

        // 2. Validate nowPlaying flag only if it is within 2 hours of current time
        val directNow = listings.firstOrNull { it.nowPlaying == 1 }
        if (directNow != null) {
            val start = parseEpgEpoch(directNow.startTimestamp, directNow.start)
            val stop = parseEpgEpoch(directNow.stopTimestamp, directNow.end)
            if (start == null || stop == null || (currentEpoch >= start - 3600L && currentEpoch <= stop + 3600L)) {
                return directNow
            }
        }

        // 3. Nearest upcoming program
        val upcoming = parsedListings.firstOrNull { (_, startSec, _) ->
            startSec >= currentEpoch
        }
        if (upcoming != null) return upcoming.first

        // 4. Most recent past program
        val mostRecent = parsedListings.lastOrNull()
        if (mostRecent != null) return mostRecent.first

        return listings.firstOrNull()
    }

    private fun generateFallbackEpg(channelName: String, categoryId: String?): ShortEpgResponse {
        val currentEpoch = System.currentTimeMillis() / 1000L
        val hourSec = 3600L
        val currentBlockStart = (currentEpoch / hourSec) * hourSec
        val nameUpper = channelName.uppercase()

        val genreLabel = when {
            nameUpper.contains("ESPN") || nameUpper.contains("SPORTS") || nameUpper.contains("FS1") || nameUpper.contains("NFL") || nameUpper.contains("NBA") || nameUpper.contains("MLB") || nameUpper.contains("NHL") || nameUpper.contains("GOLF") || nameUpper.contains("TENNIS") || nameUpper.contains("UFC") || nameUpper.contains("WWE") -> "Live Sports Coverage"
            nameUpper.contains("NEWS") || nameUpper.contains("CNN") || nameUpper.contains("FOX NEWS") || nameUpper.contains("MSNBC") || nameUpper.contains("BBC") || nameUpper.contains("CNBC") || nameUpper.contains("WEATHER") -> "Live News & Current Affairs"
            nameUpper.contains("HBO") || nameUpper.contains("CINEMAX") || nameUpper.contains("SHOWTIME") || nameUpper.contains("STARZ") || nameUpper.contains("MOVIE") || nameUpper.contains("TCM") || nameUpper.contains("FXM") -> "Feature Presentation"
            nameUpper.contains("DISNEY") || nameUpper.contains("NICK") || nameUpper.contains("CARTOON") || nameUpper.contains("KIDS") || nameUpper.contains("BOOMERANG") -> "Family & Kids Programming"
            nameUpper.contains("DISCOVERY") || nameUpper.contains("HIST") || nameUpper.contains("NAT GEO") || nameUpper.contains("ANIMAL") || nameUpper.contains("SCIENCE") -> "Documentary & Discovery"
            nameUpper.contains("COMEDY") || nameUpper.contains("TBS") || nameUpper.contains("TNT") || nameUpper.contains("USA") || nameUpper.contains("FX") || nameUpper.contains("BRAVO") || nameUpper.contains("E!") || nameUpper.contains("HGTV") || nameUpper.contains("FOOD") || nameUpper.contains("TLC") -> "Primetime Entertainment"
            else -> "Live HD Broadcast"
        }

        val programs = listOf(
            EpgProgram(
                id = "synth_1",
                title = "$genreLabel - Afternoon Segment",
                startTimestamp = "${currentBlockStart - hourSec}",
                stopTimestamp = "$currentBlockStart",
                description = "Live high-definition transmission and scheduled entertainment on $channelName."
            ),
            EpgProgram(
                id = "synth_2",
                title = "$genreLabel Live",
                startTimestamp = "$currentBlockStart",
                stopTimestamp = "${currentBlockStart + hourSec}",
                nowPlaying = 1,
                description = "Currently streaming live broadcast in 1080p full high definition on $channelName."
            ),
            EpgProgram(
                id = "synth_3",
                title = "$genreLabel - Evening Block",
                startTimestamp = "${currentBlockStart + hourSec}",
                stopTimestamp = "${currentBlockStart + (2 * hourSec)}",
                description = "Upcoming scheduled programming and featured presentation on $channelName."
            ),
            EpgProgram(
                id = "synth_4",
                title = "$genreLabel - Night Edition",
                startTimestamp = "${currentBlockStart + (2 * hourSec)}",
                stopTimestamp = "${currentBlockStart + (3 * hourSec)}",
                description = "Late night broadcast and scheduled line-up on $channelName."
            )
        )
        return ShortEpgResponse(epgListings = programs)
    }

    suspend fun getFullEpgForChannel(streamId: Int): ShortEpgResponse? = withContext(Dispatchers.IO) {
        if (streamId <= 0) return@withContext null
        cachedFullEpgByStreamId[streamId]?.let { return@withContext it }
        val ch = cachedLiveChannels?.firstOrNull { it.streamId == streamId }
            ?: cachedLiveChannelsByCat.values.flatten().firstOrNull { it.streamId == streamId }
        val credentials = authRepo.getActiveLiveCredentials()
        val portal = ch?.portalUrl ?: authRepo.getLivePortalUrl()
        val user = ch?.streamUser ?: credentials?.user ?: "f2e1d20954"
        val pswd = ch?.streamPassword ?: credentials?.pswd ?: "a7a8bf92d242"
        try {
            val shortEpg = apiClient.getShortEpg(portal, user, pswd, streamId, limit = 8)
            if (shortEpg != null && !shortEpg.epgListings.isNullOrEmpty()) {
                cachedFullEpgByStreamId[streamId] = shortEpg
                val currentProg = resolveCurrentProgram(shortEpg.epgListings)
                val title = currentProg?.decodedTitle
                if (!title.isNullOrBlank()) {
                    cachedEpgByStreamId[streamId] = title
                }
                return@withContext shortEpg
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch full EPG for streamId $streamId: ${e.message}")
        }

        // Tier 3 Synthesis Fallback: Always provide continuous time-accurate schedule
        val fallback = generateFallbackEpg(ch?.name ?: "Live Channel", ch?.categoryId)
        cachedFullEpgByStreamId[streamId] = fallback
        val currentProg = resolveCurrentProgram(fallback.epgListings)
        val title = currentProg?.decodedTitle
        if (!title.isNullOrBlank()) {
            cachedEpgByStreamId[streamId] = title
        }
        return@withContext fallback
    }

    fun getCachedFullEpg(streamId: Int): ShortEpgResponse? = cachedFullEpgByStreamId[streamId]

    suspend fun getEpgTitleForChannel(streamId: Int): String? = withContext(Dispatchers.IO) {
        if (streamId <= 0) return@withContext null
        cachedEpgByStreamId[streamId]?.let { return@withContext it }
        val full = getFullEpgForChannel(streamId)
        return@withContext resolveCurrentProgram(full?.epgListings)?.decodedTitle
    }

    private val activePrefetchJobs = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    suspend fun prefetchEpgForChannels(channels: List<Channel>, limit: Int = 50) = withContext(Dispatchers.IO) {
        val targetChannels = channels.take(limit).filter { it.streamId > 0 && !cachedEpgByStreamId.containsKey(it.streamId) }
        if (targetChannels.isEmpty()) return@withContext

        // Proactively provide synthesized schedule so UI shows immediate now-playing metadata
        for (ch in targetChannels) {
            if (!cachedFullEpgByStreamId.containsKey(ch.streamId)) {
                val fallback = generateFallbackEpg(ch.name, ch.categoryId)
                cachedFullEpgByStreamId[ch.streamId] = fallback
                val title = resolveCurrentProgram(fallback.epgListings)?.decodedTitle ?: ""
                if (title.isNotBlank()) {
                    cachedEpgByStreamId[ch.streamId] = title
                }
            }
        }

        val credentials = authRepo.getActiveLiveCredentials()
        val semaphore = Semaphore(8)
        coroutineScope {
            for (ch in targetChannels) {
                if (activePrefetchJobs.putIfAbsent(ch.streamId, true) == null) {
                    launch {
                        semaphore.withPermit {
                            val portal = ch.portalUrl ?: authRepo.getLivePortalUrl()
                            val user = ch.streamUser ?: credentials?.user ?: "f2e1d20954"
                            val pswd = ch.streamPassword ?: credentials?.pswd ?: "a7a8bf92d242"
                            try {
                                val shortEpg = apiClient.getShortEpg(portal, user, pswd, ch.streamId, limit = 8)
                                if (shortEpg != null && !shortEpg.epgListings.isNullOrEmpty()) {
                                    cachedFullEpgByStreamId[ch.streamId] = shortEpg
                                    val currentProg = resolveCurrentProgram(shortEpg.epgListings)
                                    val title = currentProg?.decodedTitle
                                    if (!title.isNullOrBlank()) {
                                        cachedEpgByStreamId[ch.streamId] = title
                                    }
                                }
                            } catch (_: Exception) {
                            } finally {
                                activePrefetchJobs.remove(ch.streamId)
                            }
                        }
                    }
                }
            }
        }
    }

    fun getCachedEpg(streamId: Int): String? {
        val title = cachedEpgByStreamId[streamId]
        if (!title.isNullOrBlank()) return title
        cachedFullEpgByStreamId[streamId]?.let { full ->
            val prog = resolveCurrentProgram(full.epgListings)?.decodedTitle
            if (!prog.isNullOrBlank()) {
                cachedEpgByStreamId[streamId] = prog
                return prog
            }
        }
        return null
    }

    // In-Memory VOD Movies Cache
    var cachedMovieCategories: List<MovieCategory>? = null
        private set
    private val cachedMoviesByCat = mutableMapOf<String, List<Movie>>()

    // In-Memory VOD Series Cache
    var cachedSeriesCategories: List<SeriesCategory>? = null
        private set
    private val cachedSeriesByCat = mutableMapOf<String, List<Series>>()
    private val cachedSeriesInfoMap = mutableMapOf<Int, SeriesInfoResponse>()

    // In-Memory Podcast Cache
    private val cachedPodcastChannelsByCat = mutableMapOf<String, List<PodcastChannel>>()
    private val cachedPodcastEpisodesByChannel = mutableMapOf<String, List<PodcastEpisode>>()
    private val cachedPodcastEpisodesByCat = mutableMapOf<String, List<PodcastEpisode>>()

    companion object {
        fun cleanChannelName(name: String): String {
            return name
                .replace(Regex("""^\s*\|?\s*[A-Z]{2,4}\s*\|\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*\[.*?\]\s*"""), "")
                .replace(Regex("""\s*\(\d+p\).*$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\b(4K|UHD|FHD|1080P|720P|HD|HEVC|H\.265|RAW|EAST|WEST|PACIFIC|CENTRAL)\b""", RegexOption.IGNORE_CASE), "")
                .trim()
        }

        fun getChannelInitials(name: String): String {
            val clean = cleanChannelName(name)
            val parts = clean.split(" ").filter { it.isNotBlank() }
            return when {
                parts.isEmpty() -> "TV"
                parts.size == 1 -> parts[0].take(4).uppercase()
                else -> parts.take(3).map { it.take(1) }.joinToString("").uppercase()
            }
        }

        fun resolveChannelLogoUrl(channelName: String, rawIcon: String?): String? {
            if (!rawIcon.isNullOrBlank() && rawIcon.startsWith("http") && !rawIcon.endsWith(".ts") && !rawIcon.endsWith(".m3u8")) {
                return rawIcon
            }

            val upper = cleanChannelName(channelName).uppercase()
            return when {
                upper.contains("HBO") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/HBO_logo.svg/512px-HBO_logo.svg.png"
                upper.contains("CINEMAX") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e0/Cinemax_logo_2020.svg/512px-Cinemax_logo_2020.svg.png"
                upper.contains("SHOWTIME") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/2/22/Showtime.svg/512px-Showtime.svg.png"
                upper.contains("STARZ") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/Starz_2022.svg/512px-Starz_2022.svg.png"
                upper.contains("ESPN2") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/ESPN2_logo.svg/512px-ESPN2_logo.svg.png"
                upper.contains("ESPNU") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/7/77/ESPNU_logo.svg/512px-ESPNU_logo.svg.png"
                upper.contains("ESPNEWS") || upper.contains("ESPN NEWS") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e6/ESPNews_2022_logo.svg/512px-ESPNews_2022_logo.svg.png"
                upper.contains("ESPN") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2f/ESPN_wordmark.svg/512px-ESPN_wordmark.svg.png"
                upper.contains("FS1") || upper.contains("FOX SPORTS 1") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/3/37/Fox_Sports_1_logo.svg/512px-Fox_Sports_1_logo.svg.png"
                upper.contains("FS2") || upper.contains("FOX SPORTS 2") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/7/75/Fox_Sports_2_logo.svg/512px-Fox_Sports_2_logo.svg.png"
                upper.contains("FOX NEWS") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/6/67/Fox_News_Channel_logo.svg/512px-Fox_News_Channel_logo.svg.png"
                upper.contains("FOX BUSINESS") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8a/Fox_Business.svg/512px-Fox_Business.svg.png"
                upper.contains("FOX") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/9/95/FOX_wordmark-red.svg/512px-FOX_wordmark-red.svg.png"
                upper.contains("CNN") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b1/CNN.svg/512px-CNN.svg.png"
                upper.contains("MSNBC") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/2/25/MSNBC_2021_logo.svg/512px-MSNBC_2021_logo.svg.png"
                upper.contains("CNBC") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e3/CNBC_logo.svg/512px-CNBC_logo.svg.png"
                upper.contains("TNT") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/TNT_Logo_2016.svg/512px-TNT_Logo_2016.svg.png"
                upper.contains("TBS") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/9/90/TBS_logo_2016.svg/512px-TBS_logo_2016.svg.png"
                upper.contains("USA NETWORK") || upper.contains("USA NET") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/2/26/USA_Network_logo_2016.svg/512px-USA_Network_logo_2016.svg.png"
                upper.contains("FXM") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ad/FXM_logo.svg/512px-FXM_logo.svg.png"
                upper.contains("FXX") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/9/92/FXX_2013.svg/512px-FXX_2013.svg.png"
                upper.contains("FX") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/4/43/FX_logo.svg/512px-FX_logo.svg.png"
                upper.contains("AMC") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1d/Amc_logo.svg/512px-Amc_logo.svg.png"
                upper.contains("BRAVO") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/5/52/Bravo_2017_logo.svg/512px-Bravo_2017_logo.svg.png"
                upper.contains("INVESTIGATION DISCOVERY") || upper.contains(" ID ") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/Investigation_Discovery_2020.svg/512px-Investigation_Discovery_2020.svg.png"
                upper.contains("DISCOVERY") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/2/20/Discovery_Channel_2019.svg/512px-Discovery_Channel_2019.svg.png"
                upper.contains("HGTV") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c1/HGTV_2015_logo.svg/512px-HGTV_2015_logo.svg.png"
                upper.contains("FOOD NETWORK") || upper.contains("FOOD") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/9/99/Food_Network_logo.svg/512px-Food_Network_logo.svg.png"
                upper.contains("TLC") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/0/05/TLC_Logo_2020.svg/512px-TLC_Logo_2020.svg.png"
                upper.contains("HISTORY") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f5/History_Logo.svg/512px-History_Logo.svg.png"
                upper.contains("A&E") || upper.contains("A & E") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/b/be/A%26E_Network_logo_2021.svg/512px-A%26E_Network_logo_2021.svg.png"
                upper.contains("ANIMAL PLANET") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/2/24/Animal_Planet_2018.svg/512px-Animal_Planet_2018.svg.png"
                upper.contains("NAT GEO WILD") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/Nat_Geo_Wild_logo.svg/512px-Nat_Geo_Wild_logo.svg.png"
                upper.contains("NATIONAL GEOGRAPHIC") || upper.contains("NAT GEO") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6a/National_Geographic_Logo.svg/512px-National_Geographic_Logo.svg.png"
                upper.contains("COMEDY CENTRAL") || upper.contains("COMEDY") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e4/Comedy_Central_2018.svg/512px-Comedy_Central_2018.svg.png"
                upper.contains("MTV") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/0/02/MTV_logo_%282021%29.svg/512px-MTV_logo_%282021%29.svg.png"
                upper.contains("VH1") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/2/29/VH1_logo_2013.svg/512px-VH1_logo_2013.svg.png"
                upper.contains("BET") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/1/14/BET_logo_2021.svg/512px-BET_logo_2021.svg.png"
                upper.contains("DISNEY JUNIOR") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/Disney_Junior_2020.svg/512px-Disney_Junior_2020.svg.png"
                upper.contains("DISNEY XD") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7b/Disney_XD_2015.svg/512px-Disney_XD_2015.svg.png"
                upper.contains("DISNEY") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d2/2019_Disney_Channel_logo.svg/512px-2019_Disney_Channel_logo.svg.png"
                upper.contains("NICK JR") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a7/Nick_Jr._2023.svg/512px-Nick_Jr._2023.svg.png"
                upper.contains("NICKELODEON") || upper.contains("NICK") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/7/72/Nickelodeon_2023_logo.svg/512px-Nickelodeon_2023_logo.svg.png"
                upper.contains("CARTOON NETWORK") || upper.contains("CARTOON") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/8/80/Cartoon_Network_2010_logo.svg/512px-Cartoon_Network_2010_logo.svg.png"
                upper.contains("BOOMERANG") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/8/84/Boomerang_2014_logo.svg/512px-Boomerang_2014_logo.svg.png"
                upper.contains("SYFY") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b5/Syfy_2017.svg/512px-Syfy_2017.svg.png"
                upper.contains("PARAMOUNT") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/8/89/Paramount_Network.svg/512px-Paramount_Network.svg.png"
                upper.contains("HALLMARK") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/Hallmark_Channel_Logo.svg/512px-Hallmark_Channel_Logo.svg.png"
                upper.contains("LIFETIME") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/5/52/Lifetime_logo_2020.svg/512px-Lifetime_logo_2020.svg.png"
                upper.contains("OXYGEN") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/d/df/Oxygen_True_Crime_2022.svg/512px-Oxygen_True_Crime_2022.svg.png"
                upper.contains("E!") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5e/E%21_logo_2012.svg/512px-E%21_logo_2012.svg.png"
                upper.contains("CW") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a3/The_CW_2024.svg/512px-The_CW_2024.svg.png"
                upper.contains("ION") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/b/bb/Ion_Television_logo_2016.svg/512px-Ion_Television_logo_2016.svg.png"
                upper.contains("ABC") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/ABC_Logo_2021.svg/512px-ABC_Logo_2021.svg.png"
                upper.contains("CBS") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/CBS_logo_%282020%29.svg/512px-CBS_logo_%282020%29.svg.png"
                upper.contains("NBC") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/NBC_logo_2022.svg/512px-NBC_logo_2022.svg.png"
                upper.contains("NFL REDZONE") || upper.contains("REDZONE") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5d/NFL_RedZone_logo.svg/512px-NFL_RedZone_logo.svg.png"
                upper.contains("NFL") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7b/NFL_Network_logo.svg/512px-NFL_Network_logo.svg.png"
                upper.contains("NBA TV") || upper.contains("NBA") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7b/NBA_TV_logo.svg/512px-NBA_TV_logo.svg.png"
                upper.contains("MLB") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/0/07/MLB_Network_logo.svg/512px-MLB_Network_logo.svg.png"
                upper.contains("NHL") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/5/51/NHL_Network_logo.svg/512px-NHL_Network_logo.svg.png"
                upper.contains("GOLF") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/8/84/Golf_Channel_logo_2014.svg/512px-Golf_Channel_logo_2014.svg.png"
                upper.contains("TENNIS") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4c/Tennis_Channel_logo.svg/512px-Tennis_Channel_logo.svg.png"
                upper.contains("WEATHER") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/The_Weather_Channel_logo_2005.svg/512px-The_Weather_Channel_logo_2005.svg.png"
                upper.contains("C-SPAN") || upper.contains("CSPAN") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7a/C-SPAN_logo_2019.svg/512px-C-SPAN_logo_2019.svg.png"
                upper.contains("TELEMUNDO") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/0/09/Telemundo_logo_2018.svg/512px-Telemundo_logo_2018.svg.png"
                upper.contains("UNIVISION") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a2/Univision_logo_2019.svg/512px-Univision_logo_2019.svg.png"
                upper.contains("RELAX") -> "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=200&q=80"
                else -> null
            }
        }

        // MARK: - Category Filtering & Priority Helpers
        fun isEnglishOrUsLiveCategory(catName: String): Boolean {
            val n = catName.trim().uppercase()
            if (n == "ALL" || n == "ALL CHANNELS" || n.contains("FAVORITE")) return true
            if (n.startsWith("18|") || n.contains("FOR ADULTS") || n.contains("ADULT")) return true
            if (isUsCategory(n)) return true
            if (n.startsWith("LAT|") || n.startsWith("LAT |")) return true
            if (n.startsWith("4K ") || n.startsWith("4K|")) return true
            if (n.startsWith("24/7 ") || n.startsWith("24/7|")) return true
            if (n.startsWith("NA|") || n.startsWith("NA |") || n.startsWith("|NA|")) return true
            if (n == "WORLD LIVE SPORTS" || n == "HR| HORSE RACING") return true
            return false
        }

        fun isEnglishOrUsMovieCategory(catName: String): Boolean {
            val n = catName.trim().uppercase()
            if (n == "ALL" || n == "ALL MOVIES") return false
            if (n.contains("FOR ADULTS") || n.contains("ADULT") || n.contains("18+")) return true
            if (n.contains("[EN]") || n.contains("ENGLISH") || n.contains("[MULTISUB]") || n.contains("MULTISUB")) return true
            if (n.startsWith("|EN|") || n.startsWith("|US|") || n.startsWith("EN|") || n.startsWith("US|")) return true
            if (n.startsWith("NETFLIX") || n.startsWith("DISNEY+") || n.startsWith("APPLE+") || n.startsWith("TOP IMDB") || n.startsWith("4K NETFLIX") || n.startsWith("WORLDCUP") || n.startsWith("4K ")) return true
            return false
        }

        fun isAdultCategory(catName: String): Boolean {
            val n = catName.trim().uppercase()
            return n.startsWith("18|") || n.startsWith("18+") || n.startsWith("18 ") ||
                    n.startsWith("|18+|") || n.startsWith("|18|") || n.startsWith("|+18|") ||
                    n.startsWith("+18|") || n.startsWith("+18 ") || n.startsWith("+18+") ||
                    n.contains("|+18|") || n.contains("|18+|") || n.contains("|18|") || n.contains("+18") ||
                    n.startsWith("XXX") || n.contains("FOR ADULTS") || n.contains("ADULT") ||
                    n.contains("XXX") || n.contains("18+") || n.contains("PORN") ||
                    n.contains("EROTIC") || n.contains("PLAYBOY") || n.contains("HUSTLER") ||
                    n.contains("PENTHOUSE") || n.contains("BRAZZERS") || n.contains("HANIME") ||
                    n.contains("HENTAI") || n.contains("REDLIGHT") || n.contains("VIVID") ||
                    n.contains("BABES") || n.contains("PASSION") || n.contains("DORCEL") ||
                    n.contains("SEXY") || n.contains("CENTO") || n.contains("EXPLICIT")
        }

        fun isAdultName(name: String): Boolean {
            val n = name.trim().uppercase()
            return n.startsWith("18|") || n.startsWith("18+") || n.startsWith("18 ") ||
                    n.startsWith("|18+|") || n.startsWith("|18|") || n.startsWith("|+18|") ||
                    n.startsWith("+18|") || n.startsWith("+18 ") || n.startsWith("+18+") ||
                    n.contains("|+18|") || n.contains("|18+|") || n.contains("|18|") || n.contains("+18") ||
                    n.startsWith("XXX") || n.contains("XXX") || n.contains("ADULT") ||
                    n.contains("FOR ADULTS") || n.contains("PORN") || n.contains("EROTIC") ||
                    n.contains("PLAYBOY") || n.contains("HUSTLER") || n.contains("BRAZZERS") ||
                    n.contains("HANIME") || n.contains("HENTAI") || n.contains("REDLIGHT") ||
                    n.contains("VIVID") || n.contains("BABES") || n.contains("PASSION") ||
                    n.contains("DORCEL") || n.contains("SEXY") || n.contains("CENTO") ||
                    n.contains("EXPLICIT")
        }

        fun isUsCategory(catName: String): Boolean {
            val n = catName.trim().uppercase()
            return n.startsWith("|NA| USA") || n.startsWith("NA| USA") || n.startsWith("|US|") ||
                   n.startsWith("US|") || n.startsWith("US |") || n.startsWith("US:") ||
                   n.startsWith("US :") || n.startsWith("US -") || n.startsWith("USA|") ||
                   n.startsWith("USA:") || n.startsWith("USA -") || n.startsWith("USA ") ||
                   n == "US" || n == "USA" || n.contains("|NA| USA") || n.contains("|US|") ||
                   n.contains("|USA|") || n.contains("USA GENERAL") || n.contains("USA SPORTS") ||
                   n.contains("USA NEWS") || n.contains("USA MOVIES") || n.contains("USA ABC") ||
                   n.contains("USA CBS") || n.contains("USA FOX") || n.contains("USA NBC") ||
                   n.contains("USA PBS") || n.contains("USA CW") || n.contains("USA KIDS") ||
                   n.contains("USA HBO") || n.contains("USA ESPN") || n.contains("USA NBA") ||
                   n.contains("USA NFL") || n.contains("USA NHL") || n.contains("USA MLB") ||
                   n.contains("USA NCAA") || n.contains("USA SPECTRUM") || n.contains("24/7 ENGLISH") ||
                   n.contains("PPV LIVE EVENT")
        }

        fun isPpvCategory(catName: String): Boolean {
            val n = catName.trim().uppercase()
            return n.startsWith("|PPV|") || n.startsWith("PPV|") || n.startsWith("PPV ") ||
                   n.startsWith("PPV:") || n.startsWith("PPV -") || n.contains("|PPV|") ||
                   n.contains("PPV EVENTS") || n.contains("PAY PER VIEW") || n.contains("PPV LIVE")
        }

        fun isNowCategory(catName: String): Boolean {
            val n = catName.trim().uppercase()
            return n.startsWith("|NOW|") || n.startsWith("NOW|") || n.startsWith("NOW :") ||
                   n.startsWith("NOW -") || n.contains("|NOW|")
        }

        fun isNonUsCountryCategory(catName: String): Boolean {
            val n = catName.trim().uppercase()
            if (isUsCategory(n)) return false
            val nonUsPrefixes = listOf(
                "|UK|", "UK|", "|CA|", "CA|", "|AU|", "AU|", "|NZ|", "NZ|",
                "|FR|", "FR|", "|DE|", "DE|", "|IT|", "IT|", "|ES|", "ES|",
                "|PT|", "PT|", "|LAT|", "LAT|", "|MX|", "MX|", "|AR|", "AR|",
                "|BR|", "BR|", "|CO|", "CO|", "|CL|", "CL|", "|PE|", "PE|",
                "|TR|", "TR|", "|RU|", "RU|", "|PL|", "PL|", "|NL|", "NL|",
                "|BE|", "BE|", "|GR|", "GR|", "|RO|", "RO|", "|AL|", "AL|", "|ALB|",
                "|BLN|", "|EX-YU|", "|YU|", "|BG|", "|CZ|", "|SK|", "|HU|", "|SE|",
                "|NO|", "|DK|", "|FI|", "|IS|", "|IN|", "|PK|", "|BD|", "|AFG|", "|AF|",
                "|IR|", "|ARABIC|", "|AR|", "|AFRICA|", "|ASIA|", "|AS|", "|AM|", "|SA|",
                "|PH|", "|VIET|", "|THAI|", "|KOREA|", "|JAPAN|", "|CN|", "|HK|", "|TW|", "|ISR|"
            )
            return nonUsPrefixes.any { n.startsWith(it) || n.contains(it) }
        }

        fun isNonEnglishVodCategory(catName: String): Boolean {
            val n = catName.trim().uppercase()
            if (isAdultCategory(n)) return false
            if (n.contains("[EN]") || n.contains("ENGLISH") || n.contains("[MULTISUB]") || n.contains("MULTISUB")) return false
            val nonEnPrefixes = listOf(
                "|FR|", "FR|", "|DE|", "DE|", "|IT|", "IT|", "|ES|", "ES|",
                "|LAT|", "LAT|", "|MX|", "MX|", "|AR|", "AR|", "|BR|", "BR|",
                "|PT|", "PT|", "|TR|", "TR|", "|RU|", "RU|", "|PL|", "PL|",
                "|NL|", "NL|", "|BE|", "BE|", "|GR|", "GR|", "|RO|", "RO|",
                "|AL|", "AL|", "|ALB|", "|CZ|", "|SK|", "|HU|", "|BG|", "|SE|",
                "|NO|", "|DK|", "|FI|", "|IS|", "|IN|", "|HINDI|", "|TAMIL|",
                "|TELUGU|", "|MALAYALAM|", "|KANNADA|", "|PUNJABI|",
                "|PK|", "|BD|", "|AFG|", "|IR|", "|ARABIC|", "|AFRICA|",
                "|ASIA|", "|PH|", "|VIET|", "|THAI|", "|KOREA|", "|KOREAN|",
                "|JAPAN|", "|JAPANESE|", "|CN|", "|CHINESE|", "|HK|", "|TW|",
                "|ISR|", "|HEBREW|", "|KURD|", "|PERSIA|", "|EX-YU|", "|YU|"
            )
            if (nonEnPrefixes.any { n.startsWith(it) || n.contains(it) }) return true

            val nonEnKeywords = listOf(
                "FRANCAIS", "FRENCH", "DEUTSCH", "GERMAN", "ITALIANO", "ITALIAN",
                "ESPAÑOL", "ESPANOL", "SPANISH", "LATINO", "PORTUGUÊS", "PORTUGUES", "PORTUGUESE",
                "TÜRKÇE", "TURKISH", "RUSSIAN", "POLSKI", "POLISH", "NEDERLANDS", "DUTCH",
                "GREEK", "ROMANIAN", "SHQIP", "ALBANIAN", "HINDI", "TAMIL", "TELUGU",
                "MALAYALAM", "KANNADA", "PUNJABI", "ARABIC", "TAGALOG", "VIETNAMESE",
                "KOREAN", "CHINESE", "HEBREW"
            )
            if (!n.startsWith("|EN|") && !n.startsWith("EN|") && !n.startsWith("|US|") && !n.startsWith("US|")) {
                if (nonEnKeywords.any { n.contains(it) }) return true
            }
            return false
        }

        fun isFilteredMovieCategory(catName: String): Boolean {
            val n = catName.trim().uppercase()
            if (isAdultCategory(n)) return false
            if (n.contains("[EN]") || n.contains("ENGLISH") || n.contains("[MULTISUB]") || n.contains("MULTISUB")) return false

            // Explicitly requested US filter exclusions for Movies:
            // AF, CA, NORDIC, SC, DANSKE, ORIGINAL TABII MULTI, VE, JP, KU, AM
            val movieExclusions = listOf(
                "|AF|", "AF|", "AF |", "AF -",
                "|CA|", "CA|", "CA |", "CA -",
                "|NORDIC|", "NORDIC|", "NORDIC ",
                "|SC|", "SC|", "SC |", "SC -",
                "|DANSKE|", "DANSKE|", "DANSKE ",
                "ORIGINAL TABII MULTI", "|TABII|", "TABII|", "TABII ",
                "|VE|", "VE|", "VE |", "VE -",
                "|JP|", "JP|", "JP |", "JP -",
                "|KU|", "KU|", "KU |", "KU -",
                "|AM|", "AM|", "AM -"
            )
            if (movieExclusions.any { n.startsWith(it) || n.contains(it) }) return true

            return isNonEnglishVodCategory(n)
        }

        fun isFilteredSeriesCategory(catName: String): Boolean {
            val n = catName.trim().uppercase()
            if (isAdultCategory(n)) return false
            if (n.contains("[EN]") || n.contains("ENGLISH") || n.contains("[MULTISUB]") || n.contains("MULTISUB")) return false

            // Explicitly requested US filter exclusions for Series:
            // AM, EX, SC, CA, AF, NO, NORDEC
            val seriesExclusions = listOf(
                "|AM|", "AM|", "AM |", "AM -",
                "|EX|", "EX|", "EX |", "EX -",
                "|SC|", "SC|", "SC |", "SC -",
                "|CA|", "CA|", "CA |", "CA -",
                "|AF|", "AF|", "AF |", "AF -",
                "|NO|", "NO|", "NO |", "NO -",
                "|NORDEC|", "NORDEC|", "NORDEC ",
                "|NORDIC|", "NORDIC|", "NORDIC "
            )
            if (seriesExclusions.any { n.startsWith(it) || n.contains(it) }) return true

            return isNonEnglishVodCategory(n)
        }

        fun isUsOrAllowedCategory(catName: String): Boolean {
            val n = catName.trim().uppercase()
            if (n == "ALL" || n == "ALL CHANNELS" || n.contains("FAVORITE")) return true
            if (isUsCategory(n)) return true
            if (isPpvCategory(n)) return true
            if (isAdultCategory(n)) return true
            return false
        }

        fun isUsOrAllowedChannel(ch: Channel): Boolean {
            val n = ch.name.trim().uppercase()
            val cat = (ch.categoryId ?: "").uppercase()
            if (isUsCategory(n) || isUsCategory(cat)) return true
            if (isPpvCategory(n) || isPpvCategory(cat)) return true
            if (isAdultName(n) || isAdultCategory(cat)) return true
            return false
        }

        fun getLiveChannelPriority(ch: Channel): Int {
            val n = ch.name.trim().uppercase()
            val cat = (ch.categoryId ?: "").uppercase()
            return when {
                isAdultName(n) -> 9999
                n.startsWith("|NA| USA") || n.startsWith("|US|") || n.startsWith("US|") || n.startsWith("US:") || n.startsWith("USA") || isUsCategory(cat) -> 10
                n.startsWith("|PPV|") || n.startsWith("PPV|") || isPpvCategory(cat) -> 20
                n.startsWith("|NOW|") || n.startsWith("NOW|") || isNowCategory(cat) -> 30
                else -> 50
            }
        }

        fun isEnglishOrUsSeriesCategory(catName: String): Boolean {
            val n = catName.trim().uppercase()
            if (n == "ALL" || n == "ALL SERIES") return false
            if (n.contains("FOR ADULTS") || n.contains("ADULT") || n.startsWith("18|") || n.contains("18+")) return true
            if (n.contains("[EN]") || n.contains("ENGLISH") || n.contains("[MULTISUB]") || n.contains("MULTISUB")) return true
            if (n.startsWith("|EN|") || n.startsWith("|US|") || n.startsWith("EN|") || n.startsWith("US|") || n.startsWith("|MULTI|")) return true
            return false
        }

        fun getMovieCategoryPriority(catName: String): Int {
            val n = catName.trim().uppercase()
            return when {
                isAdultCategory(n) -> 9999
                n.contains("[EN]") || n.contains("ENGLISH") -> 5
                isUsCategory(n) -> 6
                n.contains("[MULTISUB]") || n.contains("MULTISUB") -> 7
                n.contains("NEW ADDED") || n.contains("NEW RELEASE") || n.contains("NEW RELEASED") || n.contains("LATEST") -> 10
                n.contains("4K") && n.contains("MOVIE") -> 20
                n.contains("TOP IMDB") || n.contains("TOP 500") || n.contains("OSCAR") -> 30
                n.contains("ACTION") || n.contains("THRILLER") -> 40
                n.contains("COMEDY") && !n.contains("STAND-UP") -> 50
                n.contains("HORROR") -> 60
                n.contains("SCIENCE FICTION") || n.contains("FANTASY") || n.contains("FANTASTY") -> 70
                n.contains("ADVENTURE") -> 80
                n.contains("MARVEL") || n.contains("DC") -> 90
                n.contains("NETFLIX") -> 100
                n.contains("DISNEY+") || n.contains("DISNEY") -> 110
                n.contains("APPLE+") || n.contains("APPLE") -> 120
                n.contains("HBO") -> 130
                n.contains("HULU") -> 140
                n.contains("PRIME") -> 150
                n.contains("CHILDREN") || n.contains("FAMILY") || n.contains("PIXAR") || n.contains("CARTOON") || n.contains("KIDS") -> 160
                n.contains("ANIME") -> 170
                n.contains("STAND-UP") -> 180
                n.contains("MAFIA") || n.contains("GANGSTER") -> 190
                n.contains("DRAMA") -> 200
                n.contains("DOCUMENTARY") -> 210
                n.contains("WESTERN") -> 220
                n.contains("WAR") -> 230
                else -> 500
            }
        }

        fun getSeriesCategoryPriority(catName: String): Int {
            val n = catName.trim().uppercase()
            return when {
                isAdultCategory(n) -> 9999
                n.contains("[EN]") || n.contains("ENGLISH") -> 5
                isUsCategory(n) -> 6
                n.contains("[MULTISUB]") || n.contains("MULTISUB") -> 7
                n.contains("LATEST") || n.contains("NEW ADDED") || n.contains("NEW RELEASE") || n.contains("NEW RELEASED") -> 10
                n.contains("4K") -> 20
                n.contains("TOP SERIES") || n.contains("POPULAR") -> 30
                n.contains("ENGLISH SERIES") -> 40
                n.contains("NETFLIX") -> 50
                n.contains("HBO") -> 60
                n.contains("AMAZON") || n.contains("PRIME") -> 70
                n.contains("APPLE") -> 80
                n.contains("DISNEY") -> 90
                n.contains("HULU") || n.contains("FX") || n.contains("FOX") || n.contains("ABC") -> 100
                n.contains("PARAMOUNT") || n.contains("CBS") -> 110
                n.contains("PEACOCK") || n.contains("NBC") -> 120
                n.contains("SHOWTIME") || n.contains("STARZ") || n.contains("AMC") || n.contains("MGM") -> 130
                n.contains("COMEDY") -> 140
                n.contains("REALITY") -> 150
                n.contains("GANGSTER") || n.contains("MAFIA") || n.contains("CRIME") -> 160
                n.contains("ACTION") || n.contains("THRILLER") || n.contains("DRAMA") -> 170
                n.contains("DOCUMENTAR") -> 180
                n.contains("KIDS") || n.contains("ANIMATION") || n.contains("CARTOON") -> 190
                n.contains("ANIME") -> 200
                else -> 500
            }
        }

        fun cleanCategoryDisplayName(catName: String): String {
            return catName
                .replace(Regex("""^\s*\|?\s*US\s*\|\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*\|\s*US\s*-\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*US\s*:\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*US\s*-\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*\|\s*US\s*\|\s*""", RegexOption.IGNORE_CASE), "")
                .trim()
        }

        fun cleanChannelDisplayName(rawName: String): String {
            var name = rawName
                .replace(Regex("""^\s*\|?\s*NA\s*\|\s*USA\s*\|\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*\|?\s*NA\s*\|\s*US\s*\|\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*\|?\s*US\s*\|\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*\|\s*US\s*-\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*US\s*:\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*US\s*-\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*USA\s*:\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*USA\s*\|\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*USA\s*-\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*\[US\]\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*\(US\)\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*VIP\s*US\s*:\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*VIP\s*\|\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*\|?\s*EN\s*\|\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^\s*US\s+""", RegexOption.IGNORE_CASE), "")
                .trim()

            name = name
                .replace(Regex("""\b(4K|UHD|FHD|1080P|720P|HD|HEVC|H\.265|60FPS|50FPS|RAW)\b""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+"""), " ")
                .replace(Regex("""[-:|\s]+$"""), "")
                .trim()

            return if (name.isBlank()) rawName.trim() else name
        }

        fun extractChannelQuality(name: String): String? {
            val u = name.uppercase()
            return when {
                u.contains("4K") || u.contains("UHD") -> "4K"
                u.contains("FHD") || u.contains("1080P") -> "FHD"
                u.contains("HEVC") || u.contains("H.265") -> "HEVC"
                u.contains("60FPS") -> "60FPS"
                u.contains("HD") || u.contains("720P") -> "HD"
                else -> null
            }
        }

        fun getLiveCategoryPriority(catName: String): Int {
            val n = catName.trim().uppercase()
            val cleaned = cleanCategoryDisplayName(catName).trim().uppercase()
            if (n == "ALL" || n == "ALL CHANNELS" || n.contains("FAVORITE")) return 0
            if (isAdultCategory(n)) return 9999

            // 1. ENTERTAINMENT category up top (Priority 1)
            if (cleaned == "ENTERTAINMENT" || cleaned.startsWith("ENTERTAINMENT") || n.contains("ENTERTAINMENT")) return 1

            // Direct ordered categories under ENTERTAINMENT:
            // 2. NEWS NETWORK
            if (cleaned.contains("NEWS NETWORK") || (n.contains("NEWS") && n.contains("NETWORK"))) return 2

            // 3. SPORTS NETWORK
            if (cleaned.contains("SPORTS NETWORK") || (n.contains("SPORTS") && n.contains("NETWORK"))) return 3

            // 4. MOVIE NETWORK / MOVIES NETWORK
            if (cleaned.contains("MOVIE NETWORK") || cleaned.contains("MOVIES NETWORK") ||
                ((n.contains("MOVIE") || n.contains("MOVIES")) && n.contains("NETWORK"))) return 4

            // 5. KIDS NETWORK
            if (cleaned.contains("KIDS NETWORK") || (n.contains("KIDS") && n.contains("NETWORK"))) return 5

            // 6. PRIME
            if (cleaned == "PRIME" || cleaned.startsWith("PRIME") || n.contains("| PRIME") || n.contains("US| PRIME") || n == "PRIME") return 6

            // 7. NA| PPV & LIVE EVENTS
            if (n.contains("PPV & LIVE EVENTS") || n.contains("PPV & EVENTS") || (n.contains("PPV") && n.contains("LIVE EVENTS"))) return 7

            // 8. 4K RELAX UHD 3840P
            if (n.contains("4K RELAX") || cleaned.contains("4K RELAX")) return 8

            // 2. Sort other US channels/categories in front
            if (isUsCategory(n)) {
                return when {
                    n.contains("GENERAL") -> 10
                    n.contains("ABC") -> 11
                    n.contains("CBS") -> 12
                    n.contains("FOX") -> 13
                    n.contains("NBC") && !n.contains("NBCS") -> 14
                    n.contains("CW") || n.contains("MY") -> 15
                    n.contains("PBS") -> 16
                    n.contains("SPECTRUM") -> 17
                    n.contains("NEWS") -> 20
                    n.contains("SPORTS") || n.contains("SPORT") || n.contains("ESPN") -> 30
                    n.contains("NBA") || n.contains("WNBA") -> 31
                    n.contains("NFL") -> 32
                    n.contains("NHL") || n.contains("HOCKEY") -> 33
                    n.contains("MLB") || n.contains("MILB") -> 34
                    n.contains("NCAA") -> 35
                    n.contains("DAZN") || n.contains("B1G+") || n.contains("FLO") || n.contains("MLS") || n.contains("TUDN") -> 36
                    n.contains("MOVIES") || n.contains("CINEMA") || n.contains("HBO") || n.contains("SHOWTIME") -> 40
                    n.contains("HULU") -> 41
                    n.contains("PEACOCK") -> 42
                    n.contains("PARAMOUNT") -> 43
                    n.contains("NETFLIX") -> 44
                    n.contains("KIDS") || n.contains("FAMILY") || n.contains("DISNEY") -> 50
                    n.contains("DOCUMENTARY") || n.contains("DISCOVERY") -> 55
                    n.contains("MUSIC") -> 58
                    n.contains("24/7") -> 60
                    n.contains("PPV") -> 70
                    n.contains("LOCALS") || n.contains("LOCAL") -> 75
                    n.contains("TELEMUNDO") || n.contains("UNIVISION") -> 80
                    else -> 90
                }
            }

            // 3. |PPV| Categories
            if (isPpvCategory(n)) {
                return 100
            }

            // 3. |NOW| Categories
            if (isNowCategory(n)) {
                return 110
            }

            // 4. Standalone / Unique Categories (24/7, 4K, World Sports, VIP)
            if (!isNonUsCountryCategory(n)) {
                return when {
                    n.contains("WORLD SPORTS") || n.contains("SPORTS") -> 120
                    n.contains("24/7") -> 130
                    n.contains("4K") -> 140
                    n.contains("VIP") -> 150
                    else -> 160
                }
            }

            // 5. Other country categories (e.g. UK, CA, LAT, etc.)
            return when {
                n.startsWith("|UK|") || n.startsWith("UK|") -> 200
                n.startsWith("|CA|") || n.startsWith("CA|") -> 210
                n.startsWith("|LAT|") || n.startsWith("LAT|") -> 220
                else -> 500
            }
        }
    }

    // MARK: - Live TV
    suspend fun getLiveCategories(forceRefresh: Boolean = false): List<LiveCategory> = withContext(Dispatchers.IO) {
        val showAdult = authRepo.isAdultContentEnabled()
        if (!forceRefresh && cachedLiveCategories != null) {
            var list = cachedLiveCategories!!
            if (!showAdult) list = list.filter { !isAdultCategory(it.categoryName) }
            return@withContext list
        }
        mutex.withLock {
            val showAdultInner = authRepo.isAdultContentEnabled()
            if (!forceRefresh && cachedLiveCategories != null) {
                var list = cachedLiveCategories!!
                if (!showAdultInner) list = list.filter { !isAdultCategory(it.categoryName) }
                return@withLock list
            }
            val credentials = authRepo.getActiveLiveCredentials()
            val portal = authRepo.getLivePortalUrl()
            val user = credentials?.user ?: "f2e1d20954"
            val pswd = credentials?.pswd ?: "a7a8bf92d242"
            val fetched = apiClient.getLiveCategories(portal, user, pswd)
            val sorted = fetched.sortedBy { getLiveCategoryPriority(it.categoryName) }

            // All Channels category removed per specification
            val fullList = sorted.filter { it.categoryId != "all" && !it.categoryName.equals("All Channels", ignoreCase = true) }
            cachedLiveCategories = fullList
            var result = fullList
            if (!showAdultInner) result = result.filter { !isAdultCategory(it.categoryName) }
            result
        }
    }

    suspend fun getLiveChannels(categoryId: String? = "all", forceRefresh: Boolean = false): List<Channel> = withContext(Dispatchers.IO) {
        val showAdult = authRepo.isAdultContentEnabled()
        val key = categoryId ?: "all"
        if (!forceRefresh && cachedLiveChannelsByCat.containsKey(key)) {
            var cached = cachedLiveChannelsByCat[key]!!
            if (cached.isNotEmpty() || key == "all") {
                if (!showAdult) cached = cached.filter { !isAdultCategory(it.categoryId ?: "") && !isAdultName(it.name) }
                return@withContext cached
            }
        }
        mutex.withLock {
            val showAdultInner = authRepo.isAdultContentEnabled()
            if (!forceRefresh && cachedLiveChannelsByCat.containsKey(key)) {
                var cached = cachedLiveChannelsByCat[key]!!
                if (cached.isNotEmpty() || key == "all") {
                    if (!showAdultInner) cached = cached.filter { !isAdultCategory(it.categoryId ?: "") && !isAdultName(it.name) }
                    return@withLock cached
                }
            }
            val credentials = authRepo.getActiveLiveCredentials()
            val portal = authRepo.getLivePortalUrl()
            val user = credentials?.user ?: "f2e1d20954"
            val pswd = credentials?.pswd ?: "a7a8bf92d242"

            var fetched = if (key == "all") {
                apiClient.getLiveStreams(portal, user, pswd, null)
            } else {
                apiClient.getLiveStreams(portal, user, pswd, key)
            }

            // Failover to backup portal if primary returned empty
            if (fetched.isEmpty()) {
                val backupPortal = authRepo.getBackupPortalUrl()
                if (backupPortal.isNotBlank() && backupPortal != portal) {
                    fetched = if (key == "all") {
                        apiClient.getLiveStreams(backupPortal, user, pswd, null)
                    } else {
                        apiClient.getLiveStreams(backupPortal, user, pswd, key)
                    }
                }
            }

            // Fallback: If specific category_id returned empty from server, check full catalog if cached
            if (fetched.isEmpty() && key != "all" && cachedLiveChannelsByCat.containsKey("all")) {
                val allChannels = cachedLiveChannelsByCat["all"] ?: emptyList()
                fetched = allChannels.filter { it.categoryId == key }
            }

            // Filter out placeholder separator banners (e.g. "##### USA GENERAL #####")
            val bannerPattern = Regex("^[#*=_~\\s]{2,}")
            fetched = fetched.filter { !bannerPattern.containsMatchIn(it.name.trim()) }

            for (ch in fetched) {
                ch.portalUrl = portal
                ch.streamUser = user
                ch.streamPassword = pswd
            }

            // Sort all US channels in front when viewing all channels
            if (key == "all") {
                fetched = fetched.sortedBy { getLiveChannelPriority(it) }
            }

            cachedLiveChannelsByCat[key] = fetched
            if (key == "all") {
                cachedLiveChannels = fetched
            }
            var result = fetched
            if (!showAdultInner) result = result.filter { !isAdultCategory(it.categoryId ?: "") && !isAdultName(it.name) }
            result
        }
    }

    // MARK: - VOD Movies
    suspend fun getMovieCategories(forceRefresh: Boolean = false): List<MovieCategory> = withContext(Dispatchers.IO) {
        val showAdult = authRepo.isAdultContentEnabled()
        if (!forceRefresh && cachedMovieCategories != null) {
            var list = cachedMovieCategories!!
            if (!showAdult) list = list.filter { !isAdultCategory(it.categoryName) }
            return@withContext list
        }
        mutex.withLock {
            val showAdultInner = authRepo.isAdultContentEnabled()
            if (!forceRefresh && cachedMovieCategories != null) {
                var list = cachedMovieCategories!!
                if (!showAdultInner) list = list.filter { !isAdultCategory(it.categoryName) }
                return@withLock list
            }
            val portal = authRepo.getVodPortalUrl()
            val user = authRepo.getVodUsername()
            val pswd = authRepo.getVodPassword()
            val fetched = apiClient.getVodCategories(portal, user, pswd)
            val sorted = fetched.sortedBy { getMovieCategoryPriority(it.categoryName) }
            cachedMovieCategories = sorted
            var result = sorted
            if (!showAdultInner) result = result.filter { !isAdultCategory(it.categoryName) }
            result
        }
    }

    // In-memory cache for TMDB poster lookups to ensure viewable cards display loaded title images
    private val resolvedPosterCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun sanitizeTitleForTmdb(title: String): String {
        return title
            .replace(Regex("""^\s*(\|?\s*EN\s*\||\bEN\b\s*[-|:]|\[.*?\])""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*\(\d{4}\).*$"""), "")
            .replace(Regex("""\b(4K|UHD|FHD|1080P|720P|HD|HEVC|H\.265|H\.264|BLURAY|WEBRIP|HDR|CAM|TS|TELESYNC)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[._]"""), " ")
            .trim()
    }

    suspend fun resolvePosterUrl(title: String, isSeries: Boolean = false): String? = withContext(Dispatchers.IO) {
        val clean = sanitizeTitleForTmdb(title)
        if (clean.isBlank()) return@withContext null
        val cacheKey = "${if (isSeries) "tv" else "movie"}_$clean"
        resolvedPosterCache[cacheKey]?.let { return@withContext it }

        try {
            val endpoint = if (isSeries) "tv" else "movie"
            val encodedTitle = java.net.URLEncoder.encode(clean, "UTF-8")
            val url = "https://api.themoviedb.org/3/search/$endpoint?api_key=04c35731a5ee918f014970082a0088b1&query=$encodedTitle"
            val json = apiClient.fetchJsonFast(url)
            if (!json.isNullOrBlank()) {
                val jsonObj = com.google.gson.JsonParser.parseString(json).asJsonObject
                val results = jsonObj.getAsJsonArray("results")
                if (results != null && results.size() > 0) {
                    val first = results.get(0).asJsonObject
                    val posterPath = if (first.has("poster_path") && !first.get("poster_path").isJsonNull) {
                        first.get("poster_path").asString
                    } else null
                    if (!posterPath.isNullOrBlank()) {
                        val fullPoster = "https://image.tmdb.org/t/p/w342$posterPath"
                        resolvedPosterCache[cacheKey] = fullPoster
                        return@withContext fullPoster
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    fun getCachedPosterUrl(title: String, isSeries: Boolean = false): String? {
        val clean = sanitizeTitleForTmdb(title)
        val cacheKey = "${if (isSeries) "tv" else "movie"}_$clean"
        return resolvedPosterCache[cacheKey]
    }

    suspend fun preloadMoviePosters(movies: List<Movie>, limit: Int = 30) = withContext(Dispatchers.IO) {
        val target = movies.take(limit)
        target.chunked(6).forEach { chunk ->
            chunk.map { m ->
                async {
                    val icon = m.streamIcon
                    val needsResolve = icon.isNullOrBlank() || !icon.startsWith("http") ||
                        icon.endsWith(".ts") || icon.endsWith(".m3u8") ||
                        icon.contains("dead") || icon.contains("placeholder")
                    if (needsResolve) {
                        resolvePosterUrl(m.displayTitle, isSeries = false)
                    }
                }
            }.forEach { it.await() }
        }
    }

    suspend fun preloadSeriesPosters(seriesList: List<Series>, limit: Int = 30) = withContext(Dispatchers.IO) {
        val target = seriesList.take(limit)
        target.chunked(6).forEach { chunk ->
            chunk.map { s ->
                async {
                    val cover = s.cover
                    val needsResolve = cover.isNullOrBlank() || !cover.startsWith("http") ||
                        cover.endsWith(".ts") || cover.endsWith(".m3u8") ||
                        cover.contains("dead") || cover.contains("placeholder")
                    if (needsResolve) {
                        resolvePosterUrl(s.displayTitle, isSeries = true)
                    }
                }
            }.forEach { it.await() }
        }
    }

    suspend fun getMovies(categoryId: String? = null, forceRefresh: Boolean = false): List<Movie> = withContext(Dispatchers.IO) {
        val targetCatId = categoryId ?: getMovieCategories().firstOrNull()?.categoryId ?: return@withContext emptyList()
        val key = targetCatId
        if (!forceRefresh && cachedMoviesByCat.containsKey(key)) {
            val cached = cachedMoviesByCat[key]!!
            if (cached.isNotEmpty()) {
                return@withContext cached
            }
        }
        mutex.withLock {
            if (!forceRefresh && cachedMoviesByCat.containsKey(key)) {
                val cached = cachedMoviesByCat[key]!!
                if (cached.isNotEmpty()) {
                    return@withLock cached
                }
            }
            val portal = authRepo.getVodPortalUrl()
            val user = authRepo.getVodUsername()
            val pswd = authRepo.getVodPassword()
            var fetched = apiClient.getVodStreams(portal, user, pswd, targetCatId)
            if (fetched.isEmpty()) {
                val backupPortal = authRepo.getBackupPortalUrl()
                if (backupPortal.isNotBlank() && backupPortal != portal) {
                    fetched = apiClient.getVodStreams(backupPortal, user, pswd, targetCatId)
                }
            }
            cachedMoviesByCat[key] = fetched
            fetched
        }
    }

    suspend fun getWatchlistMovies(): List<Movie> = withContext(Dispatchers.IO) {
        val watchlistIds = authRepo.getMovieWatchlistIds()
        if (watchlistIds.isEmpty()) return@withContext emptyList()
        val allCached = cachedMoviesByCat.values.flatten()
        val found = allCached.filter { watchlistIds.contains(it.streamId) }.distinctBy { it.streamId }
        val missingIds = watchlistIds - found.map { it.streamId }.toSet()
        if (missingIds.isNotEmpty()) {
            val allMovies = getMovies("all")
            val additional = allMovies.filter { missingIds.contains(it.streamId) }
            return@withContext (found + additional).distinctBy { it.streamId }
        }
        found
    }

    // Fast & Safe Multi-Category Movie Search (Comprehensive & Zero OOM Crashes)
    suspend fun searchMovies(query: String, selectedCategoryId: String? = null): List<Movie> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()

        val allCats = getMovieCategories()
        val portal = authRepo.getVodPortalUrl()
        val user = authRepo.getVodUsername()
        val pswd = authRepo.getVodPassword()

        val selectedCat = allCats.firstOrNull { it.categoryId == selectedCategoryId }
        val isSearchingInAdult = selectedCat != null && isAdultCategory(selectedCat.categoryName)

        // Filter the search categories: If adult category NOT selected, exclude adult categories
        val targetCats = if (isSearchingInAdult) {
            allCats.filter { isAdultCategory(it.categoryName) }
        } else {
            allCats.filter { !isAdultCategory(it.categoryName) }
        }

        val targetCatIds = targetCats.map { it.categoryId }.toSet()

        // 1. Immediate search across target cached categories
        val cachedMatches = synchronized(cachedMoviesByCat) {
            cachedMoviesByCat.filterKeys { targetCatIds.contains(it) }
                .values.flatten()
                .filter { it.displayTitle.contains(q, ignoreCase = true) }
                .filter { m ->
                    if (isSearchingInAdult) {
                        isAdultCategory(m.name) || isAdultName(m.name) || isAdultName(m.displayTitle) || targetCatIds.contains(m.categoryId)
                    } else {
                        !isAdultCategory(m.name) && !isAdultName(m.name) && !isAdultName(m.displayTitle) && !isAdultCategory(m.categoryId ?: "")
                    }
                }
                .distinctBy { it.streamId }
        }

        // 2. Fetch uncached target categories concurrently in small batches to avoid rate limits / OOM
        val uncachedCats = targetCats.filter { !cachedMoviesByCat.containsKey(it.categoryId) }
        if (uncachedCats.isEmpty()) {
            return@withContext cachedMatches.take(250)
        }

        val allResults = cachedMatches.toMutableList()
        val seenStreamIds = cachedMatches.map { it.streamId }.toMutableSet()

        uncachedCats.chunked(4).forEach { chunk ->
            if (allResults.size >= 250) return@forEach
            val deferreds = chunk.map { cat ->
                async(Dispatchers.IO) {
                    try {
                        val list = apiClient.getVodStreams(portal, user, pswd, cat.categoryId)
                        synchronized(cachedMoviesByCat) {
                            cachedMoviesByCat[cat.categoryId] = list
                        }
                        list.filter { it.displayTitle.contains(q, ignoreCase = true) }
                            .filter { m ->
                                if (isSearchingInAdult) {
                                    isAdultCategory(m.name) || isAdultName(m.name) || isAdultName(m.displayTitle) || targetCatIds.contains(m.categoryId)
                                } else {
                                    !isAdultCategory(m.name) && !isAdultName(m.name) && !isAdultName(m.displayTitle) && !isAdultCategory(m.categoryId ?: "")
                                }
                            }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
            val chunkMatches = deferreds.awaitAll().flatten()
            for (m in chunkMatches) {
                if (seenStreamIds.add(m.streamId)) {
                    allResults.add(m)
                }
            }
        }

        allResults.take(250)
    }

    // MARK: - VOD Series
    suspend fun getSeriesCategories(forceRefresh: Boolean = false): List<SeriesCategory> = withContext(Dispatchers.IO) {
        val showAdult = authRepo.isAdultContentEnabled()
        if (!forceRefresh && cachedSeriesCategories != null) {
            var list = cachedSeriesCategories!!
            if (!showAdult) list = list.filter { !isAdultCategory(it.categoryName) }
            return@withContext list
        }
        mutex.withLock {
            val showAdultInner = authRepo.isAdultContentEnabled()
            if (!forceRefresh && cachedSeriesCategories != null) {
                var list = cachedSeriesCategories!!
                if (!showAdultInner) list = list.filter { !isAdultCategory(it.categoryName) }
                return@withLock list
            }
            val portal = authRepo.getVodPortalUrl()
            val user = authRepo.getVodUsername()
            val pswd = authRepo.getVodPassword()
            val fetched = apiClient.getSeriesCategories(portal, user, pswd)
            val sorted = fetched.sortedBy { getSeriesCategoryPriority(it.categoryName) }
            cachedSeriesCategories = sorted
            var result = sorted
            if (!showAdultInner) result = result.filter { !isAdultCategory(it.categoryName) }
            result
        }
    }

    suspend fun getSeries(categoryId: String? = null, forceRefresh: Boolean = false): List<Series> = withContext(Dispatchers.IO) {
        val targetCatId = categoryId ?: getSeriesCategories().firstOrNull()?.categoryId ?: return@withContext emptyList()
        val key = targetCatId
        if (!forceRefresh && cachedSeriesByCat.containsKey(key)) {
            val cached = cachedSeriesByCat[key]!!
            if (cached.isNotEmpty()) {
                return@withContext cached
            }
        }
        mutex.withLock {
            if (!forceRefresh && cachedSeriesByCat.containsKey(key)) {
                val cached = cachedSeriesByCat[key]!!
                if (cached.isNotEmpty()) return@withLock cached
            }
            val portal = authRepo.getVodPortalUrl()
            val user = authRepo.getVodUsername()
            val pswd = authRepo.getVodPassword()

            var fetched = apiClient.getSeries(portal, user, pswd, key)
            if (fetched.isEmpty()) {
                val backupPortal = authRepo.getBackupPortalUrl()
                if (backupPortal.isNotBlank() && backupPortal != portal) {
                    fetched = apiClient.getSeries(backupPortal, user, pswd, key)
                }
            }
            cachedSeriesByCat[key] = fetched
            fetched
        }
    }

    suspend fun getWatchlistSeries(): List<Series> = withContext(Dispatchers.IO) {
        val watchlistIds = authRepo.getSeriesWatchlistIds()
        if (watchlistIds.isEmpty()) return@withContext emptyList()
        val allCached = cachedSeriesByCat.values.flatten()
        val found = allCached.filter { watchlistIds.contains(it.seriesId) }.distinctBy { it.seriesId }
        val missingIds = watchlistIds - found.map { it.seriesId }.toSet()
        if (missingIds.isNotEmpty()) {
            val allSeries = getSeries("all")
            val additional = allSeries.filter { missingIds.contains(it.seriesId) }
            return@withContext (found + additional).distinctBy { it.seriesId }
        }
        found
    }

    // Fast & Safe Multi-Category Series Search (Comprehensive & Zero OOM Crashes)
    suspend fun searchSeries(query: String, selectedCategoryId: String? = null): List<Series> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()

        val allCats = getSeriesCategories()
        val portal = authRepo.getVodPortalUrl()
        val user = authRepo.getVodUsername()
        val pswd = authRepo.getVodPassword()

        val selectedCat = allCats.firstOrNull { it.categoryId == selectedCategoryId }
        val isSearchingInAdult = selectedCat != null && isAdultCategory(selectedCat.categoryName)

        // Filter the search categories: If adult category NOT selected, exclude adult categories
        val targetCats = if (isSearchingInAdult) {
            allCats.filter { isAdultCategory(it.categoryName) }
        } else {
            allCats.filter { !isAdultCategory(it.categoryName) }
        }

        val targetCatIds = targetCats.map { it.categoryId }.toSet()

        // 1. Immediate search across target cached series categories
        val cachedMatches = synchronized(cachedSeriesByCat) {
            cachedSeriesByCat.filterKeys { targetCatIds.contains(it) }
                .values.flatten()
                .filter { it.displayTitle.contains(q, ignoreCase = true) }
                .filter { s ->
                    if (isSearchingInAdult) {
                        isAdultCategory(s.name) || isAdultName(s.name) || isAdultName(s.displayTitle) || targetCatIds.contains(s.categoryId)
                    } else {
                        !isAdultCategory(s.name) && !isAdultName(s.name) && !isAdultName(s.displayTitle) && !isAdultCategory(s.categoryId ?: "")
                    }
                }
                .distinctBy { it.seriesId }
        }

        // 2. Fetch uncached target categories concurrently in small batches (capped at 4 concurrent)
        val uncachedCats = targetCats.filter { !cachedSeriesByCat.containsKey(it.categoryId) }
        if (uncachedCats.isEmpty()) {
            return@withContext cachedMatches.take(250)
        }

        val allResults = cachedMatches.toMutableList()
        val seenSeriesIds = cachedMatches.map { it.seriesId }.toMutableSet()

        uncachedCats.chunked(4).forEach { chunk ->
            if (allResults.size >= 250) return@forEach
            val deferreds = chunk.map { cat ->
                async(Dispatchers.IO) {
                    try {
                        val list = apiClient.getSeries(portal, user, pswd, cat.categoryId)
                        synchronized(cachedSeriesByCat) {
                            cachedSeriesByCat[cat.categoryId] = list
                        }
                        list.filter { it.displayTitle.contains(q, ignoreCase = true) }
                            .filter { s ->
                                if (isSearchingInAdult) {
                                    isAdultCategory(s.name) || isAdultName(s.name) || isAdultName(s.displayTitle) || targetCatIds.contains(s.categoryId)
                                } else {
                                    !isAdultCategory(s.name) && !isAdultName(s.name) && !isAdultName(s.displayTitle) && !isAdultCategory(s.categoryId ?: "")
                                }
                            }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
            val chunkMatches = deferreds.awaitAll().flatten()
            for (m in chunkMatches) {
                if (seenSeriesIds.add(m.seriesId)) {
                    allResults.add(m)
                }
            }
        }

        allResults.take(250)
    }

    suspend fun getSeriesInfo(seriesId: Int, forceRefresh: Boolean = false): SeriesInfoResponse? = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedSeriesInfoMap.containsKey(seriesId)) {
            return@withContext cachedSeriesInfoMap[seriesId]
        }
        mutex.withLock {
            if (!forceRefresh && cachedSeriesInfoMap.containsKey(seriesId)) return@withLock cachedSeriesInfoMap[seriesId]
            val portal = authRepo.getVodPortalUrl()
            val user = authRepo.getVodUsername()
            val pswd = authRepo.getVodPassword()
            val info = apiClient.getSeriesInfo(portal, user, pswd, seriesId)
            if (info != null) {
                cachedSeriesInfoMap[seriesId] = info
            }
            info
        }
    }

    // MARK: - Live Podcasts
    suspend fun getLivePodcastChannels(category: String, forceRefresh: Boolean = false): List<PodcastChannel> = withContext(Dispatchers.IO) {
        val key = category.lowercase().trim()
        if (!forceRefresh && cachedPodcastChannelsByCat.containsKey(key)) {
            return@withContext cachedPodcastChannelsByCat[key]!!
        }
        mutex.withLock {
            if (!forceRefresh && cachedPodcastChannelsByCat.containsKey(key)) return@withLock cachedPodcastChannelsByCat[key]!!
            val channels = podcastService.fetchLivePodcastChannels(category)
            cachedPodcastChannelsByCat[key] = channels
            channels
        }
    }

    suspend fun getLivePodcastEpisodes(categoryOrQuery: String, forceRefresh: Boolean = false): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        val key = categoryOrQuery.lowercase().trim()
        if (!forceRefresh && cachedPodcastEpisodesByCat.containsKey(key)) {
            return@withContext cachedPodcastEpisodesByCat[key]!!
        }
        mutex.withLock {
            if (!forceRefresh && cachedPodcastEpisodesByCat.containsKey(key)) return@withLock cachedPodcastEpisodesByCat[key]!!
            val episodes = podcastService.searchLiveEpisodes(categoryOrQuery, page = 1)
            cachedPodcastEpisodesByCat[key] = episodes
            episodes
        }
    }

    suspend fun getLivePodcastEpisodesNextPage(categoryOrQuery: String, page: Int): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        podcastService.searchLiveEpisodes(categoryOrQuery, page = page)
    }

    suspend fun getPodcastEpisodesForChannel(channel: PodcastChannel, forceRefresh: Boolean = false): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedPodcastEpisodesByChannel.containsKey(channel.id)) {
            return@withContext cachedPodcastEpisodesByChannel[channel.id]!!
        }
        mutex.withLock {
            if (!forceRefresh && cachedPodcastEpisodesByChannel.containsKey(channel.id)) return@withLock cachedPodcastEpisodesByChannel[channel.id]!!
            val episodes = podcastService.fetchEpisodesForChannel(channel)
            cachedPodcastEpisodesByChannel[channel.id] = episodes
            episodes
        }
    }

    suspend fun getPodcastEpisodesForChannelNextPage(channel: PodcastChannel, page: Int): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        podcastService.searchLiveEpisodes("${channel.channelName} podcast", page = page)
    }

    // Music API Integration
    suspend fun getMusicForGenre(genreTagOrName: String, page: Int = 1): List<MusicVideo> = withContext(Dispatchers.IO) {
        musicService.fetchMusicForGenre(genreTagOrName, page)
    }

    suspend fun getOrganicTrendingMusic(page: Int = 1): List<MusicVideo> = withContext(Dispatchers.IO) {
        musicService.fetchMusicForGenre("trending", page)
    }

    suspend fun getMusicArtists(genre: String, forceRefresh: Boolean = false): List<MusicArtist> = withContext(Dispatchers.IO) {
        val key = genre.lowercase().trim()
        if (!forceRefresh && cachedMusicArtistsByGenre.containsKey(key)) {
            return@withContext cachedMusicArtistsByGenre[key]!!
        }
        mutex.withLock {
            if (!forceRefresh && cachedMusicArtistsByGenre.containsKey(key)) return@withLock cachedMusicArtistsByGenre[key]!!
            val artists = musicService.fetchArtistsForGenre(genre)
            cachedMusicArtistsByGenre[key] = artists
            artists
        }
    }

    suspend fun getMusicVideosForArtist(artist: MusicArtist, forceRefresh: Boolean = false): List<MusicVideo> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedMusicVideosByArtist.containsKey(artist.id)) {
            return@withContext cachedMusicVideosByArtist[artist.id]!!
        }
        mutex.withLock {
            if (!forceRefresh && cachedMusicVideosByArtist.containsKey(artist.id)) return@withLock cachedMusicVideosByArtist[artist.id]!!
            val videos = musicService.fetchMusicVideosForArtist(artist)
            cachedMusicVideosByArtist[artist.id] = videos
            videos
        }
    }

    suspend fun searchMusicVideos(query: String, page: Int = 1): List<MusicVideo> = withContext(Dispatchers.IO) {
        musicService.searchLiveMusicVideos(query, page)
    }

    suspend fun searchMusicArtists(query: String): List<MusicArtist> = withContext(Dispatchers.IO) {
        musicService.searchLiveArtists(query)
    }

    fun clearAllCaches() {
        cachedLiveCategories = null
        cachedLiveChannels = null
        cachedLiveChannelsByCat.clear()
        cachedEpgByStreamId.clear()
        cachedFullEpgByStreamId.clear()
        cachedMovieCategories = null
        cachedMoviesByCat.clear()
        cachedSeriesCategories = null
        cachedSeriesByCat.clear()
        cachedSeriesInfoMap.clear()
        cachedPodcastChannelsByCat.clear()
        cachedPodcastEpisodesByChannel.clear()
        cachedPodcastEpisodesByCat.clear()
        cachedMusicArtistsByGenre.clear()
        cachedMusicVideosByArtist.clear()
    }
}
