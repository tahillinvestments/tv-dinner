package com.troyh.tvdinner.data.repository

import android.util.Log
import com.troyh.tvdinner.data.model.*
import com.troyh.tvdinner.data.network.XtreamApiClient
import com.troyh.tvdinner.data.network.YouTubePodcastService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

class CatalogManager(
    private val authRepo: AuthRepository,
    private val apiClient: XtreamApiClient,
    private val podcastService: YouTubePodcastService
) {
    private val tag = "CatalogManager"
    private val mutex = Mutex()

    // In-Memory Live TV Cache
    var cachedLiveCategories: List<LiveCategory>? = null
        private set
    var cachedLiveChannels: List<Channel>? = null
        private set
    private val cachedEpgByStreamId = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val cachedFullEpgByStreamId = java.util.concurrent.ConcurrentHashMap<Int, ShortEpgResponse>()

    suspend fun getFullEpgForChannel(streamId: Int): ShortEpgResponse? = withContext(Dispatchers.IO) {
        if (streamId <= 0) return@withContext null
        cachedFullEpgByStreamId[streamId]?.let { return@withContext it }
        val credentials = authRepo.getActiveLiveCredentials()
        val portal = authRepo.getLivePortalUrl()
        val user = credentials?.user ?: "DGOLD001"
        val pswd = credentials?.pswd ?: "Louisville"
        try {
            val shortEpg = apiClient.getShortEpg(portal, user, pswd, streamId, limit = 5)
            if (shortEpg != null && !shortEpg.epgListings.isNullOrEmpty()) {
                cachedFullEpgByStreamId[streamId] = shortEpg
                val title = shortEpg.epgListings.firstOrNull()?.decodedTitle
                if (!title.isNullOrBlank()) {
                    cachedEpgByStreamId[streamId] = title
                }
                return@withContext shortEpg
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch full EPG for streamId $streamId: ${e.message}")
        }
        return@withContext null
    }

    fun getCachedFullEpg(streamId: Int): ShortEpgResponse? = cachedFullEpgByStreamId[streamId]

    suspend fun getEpgTitleForChannel(streamId: Int): String? = withContext(Dispatchers.IO) {
        if (streamId <= 0) return@withContext null
        cachedEpgByStreamId[streamId]?.let { return@withContext it }
        val full = getFullEpgForChannel(streamId)
        return@withContext full?.epgListings?.firstOrNull()?.decodedTitle
    }

    private val activePrefetchJobs = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    suspend fun prefetchEpgForChannels(channels: List<Channel>, limit: Int = 30) = withContext(Dispatchers.IO) {
        val targetChannels = channels.take(limit).filter { it.streamId > 0 && !cachedEpgByStreamId.containsKey(it.streamId) }
        if (targetChannels.isEmpty()) return@withContext

        val credentials = authRepo.getActiveLiveCredentials()
        val portal = authRepo.getLivePortalUrl()
        val user = credentials?.user ?: "DGOLD001"
        val pswd = credentials?.pswd ?: "Louisville"

        val semaphore = Semaphore(4)
        coroutineScope {
            for (ch in targetChannels) {
                if (activePrefetchJobs.putIfAbsent(ch.streamId, true) == null) {
                    launch {
                        semaphore.withPermit {
                            try {
                                val shortEpg = apiClient.getShortEpg(portal, user, pswd, ch.streamId, limit = 5)
                                if (shortEpg != null && !shortEpg.epgListings.isNullOrEmpty()) {
                                    cachedFullEpgByStreamId[ch.streamId] = shortEpg
                                    val title = shortEpg.epgListings.firstOrNull()?.decodedTitle
                                    if (!title.isNullOrBlank()) {
                                        cachedEpgByStreamId[ch.streamId] = title
                                    }
                                } else {
                                    cachedEpgByStreamId[ch.streamId] = ""
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
        return if (title.isNullOrBlank()) null else title
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

    // MARK: - Live TV
    suspend fun getLiveCategories(forceRefresh: Boolean = false): List<LiveCategory> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedLiveCategories != null) {
            return@withContext cachedLiveCategories!!
        }
        mutex.withLock {
            if (!forceRefresh && cachedLiveCategories != null) return@withLock cachedLiveCategories!!
            val credentials = authRepo.getActiveLiveCredentials()
            val portal = authRepo.getLivePortalUrl()
            val user = credentials?.user ?: "DGOLD001"
            val pswd = credentials?.pswd ?: "Louisville"
            val fetched = apiClient.getLiveCategories(portal, user, pswd)
            val list = listOf(LiveCategory("all", "All Channels")) + fetched
            cachedLiveCategories = list
            list
        }
    }

    suspend fun getLiveChannels(forceRefresh: Boolean = false): List<Channel> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedLiveChannels != null) {
            return@withContext cachedLiveChannels!!
        }
        mutex.withLock {
            if (!forceRefresh && cachedLiveChannels != null) return@withLock cachedLiveChannels!!
            val credentials = authRepo.getActiveLiveCredentials()
            val portal = authRepo.getLivePortalUrl()
            val user = credentials?.user ?: "DGOLD001"
            val pswd = credentials?.pswd ?: "Louisville"
            val channels = apiClient.getLiveStreams(portal, user, pswd)
            cachedLiveChannels = channels
            channels
        }
    }

    // MARK: - VOD Movies
    suspend fun getMovieCategories(forceRefresh: Boolean = false): List<MovieCategory> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedMovieCategories != null) {
            return@withContext cachedMovieCategories!!
        }
        mutex.withLock {
            if (!forceRefresh && cachedMovieCategories != null) return@withLock cachedMovieCategories!!
            val portal = authRepo.getVodPortalUrl()
            val user = authRepo.getVodUsername()
            val pswd = authRepo.getVodPassword()
            val fetched = apiClient.getVodCategories(portal, user, pswd)
            val list = listOf(MovieCategory("all", "All Movies")) + fetched
            cachedMovieCategories = list
            list
        }
    }

    suspend fun getMovies(categoryId: String? = "all", forceRefresh: Boolean = false): List<Movie> = withContext(Dispatchers.IO) {
        val key = categoryId ?: "all"
        if (!forceRefresh && cachedMoviesByCat.containsKey(key)) {
            val cached = cachedMoviesByCat[key]!!
            if (cached.isNotEmpty() || key == "all") {
                return@withContext cached
            }
        }
        mutex.withLock {
            if (!forceRefresh && cachedMoviesByCat.containsKey(key)) {
                val cached = cachedMoviesByCat[key]!!
                if (cached.isNotEmpty() || key == "all") return@withLock cached
            }
            val portal = authRepo.getVodPortalUrl()
            val user = authRepo.getVodUsername()
            val pswd = authRepo.getVodPassword()

            var fetched = if (key == "all") {
                apiClient.getVodStreams(portal, user, pswd, null)
            } else {
                apiClient.getVodStreams(portal, user, pswd, key)
            }

            // Lazy fallback: If specific category_id returned empty from server, check full catalog
            if (fetched.isEmpty() && key != "all") {
                val allMovies = if (cachedMoviesByCat.containsKey("all")) {
                    cachedMoviesByCat["all"]!!
                } else {
                    val all = apiClient.getVodStreams(portal, user, pswd, null)
                    cachedMoviesByCat["all"] = all
                    all
                }
                val catName = cachedMovieCategories?.firstOrNull { it.categoryId == key }?.categoryName
                fetched = allMovies.filter { m ->
                    m.categoryId == key || (catName != null && m.genre?.contains(catName, ignoreCase = true) == true)
                }
            }

            cachedMoviesByCat[key] = fetched
            fetched
        }
    }

    // MARK: - VOD Series
    suspend fun getSeriesCategories(forceRefresh: Boolean = false): List<SeriesCategory> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedSeriesCategories != null) {
            return@withContext cachedSeriesCategories!!
        }
        mutex.withLock {
            if (!forceRefresh && cachedSeriesCategories != null) return@withLock cachedSeriesCategories!!
            val portal = authRepo.getVodPortalUrl()
            val user = authRepo.getVodUsername()
            val pswd = authRepo.getVodPassword()
            val fetched = apiClient.getSeriesCategories(portal, user, pswd)
            val list = listOf(SeriesCategory("all", "All Series")) + fetched
            cachedSeriesCategories = list
            list
        }
    }

    suspend fun getSeries(categoryId: String? = "all", forceRefresh: Boolean = false): List<Series> = withContext(Dispatchers.IO) {
        val key = categoryId ?: "all"
        if (!forceRefresh && cachedSeriesByCat.containsKey(key)) {
            val cached = cachedSeriesByCat[key]!!
            if (cached.isNotEmpty() || key == "all") {
                return@withContext cached
            }
        }
        mutex.withLock {
            if (!forceRefresh && cachedSeriesByCat.containsKey(key)) {
                val cached = cachedSeriesByCat[key]!!
                if (cached.isNotEmpty() || key == "all") return@withLock cached
            }
            val portal = authRepo.getVodPortalUrl()
            val user = authRepo.getVodUsername()
            val pswd = authRepo.getVodPassword()

            var fetched = if (key == "all") {
                apiClient.getSeries(portal, user, pswd, null)
            } else {
                apiClient.getSeries(portal, user, pswd, key)
            }

            // Lazy fallback: If server returned empty for this specific category_id (e.g. Adventure), check full catalog
            if (fetched.isEmpty() && key != "all") {
                val allSeries = if (cachedSeriesByCat.containsKey("all")) {
                    cachedSeriesByCat["all"]!!
                } else {
                    val all = apiClient.getSeries(portal, user, pswd, null)
                    cachedSeriesByCat["all"] = all
                    all
                }
                val catName = cachedSeriesCategories?.firstOrNull { it.categoryId == key }?.categoryName
                fetched = allSeries.filter { s ->
                    s.categoryId == key || (catName != null && s.genre?.contains(catName, ignoreCase = true) == true)
                }
            }

            cachedSeriesByCat[key] = fetched
            fetched
        }
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

    fun clearAllCaches() {
        cachedLiveCategories = null
        cachedLiveChannels = null
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
    }
}
