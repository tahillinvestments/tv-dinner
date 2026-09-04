package com.tvdinner.data.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.tvdinner.data.model.MusicArtist
import com.tvdinner.data.model.MusicVideo
import com.tvdinner.data.music.MusicData
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class YouTubeMusicService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val tag = "YouTubeMusicService"
    private val gson = Gson()
    private val defaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * Fetches artists for a specific music genre.
     */
    suspend fun fetchArtistsForGenre(genreTagOrName: String): List<MusicArtist> = withContext(Dispatchers.IO) {
        val clean = genreTagOrName.replace(Regex("[^a-zA-Z &]"), "").trim().lowercase()
        val curated = MusicData.ARTISTS.filter {
            if (clean == "trending" || clean == "all" || clean.isBlank() || clean.contains("trending")) true
            else it.genre.lowercase().contains(clean) || clean.contains(it.genre.lowercase().replace(Regex("[^a-zA-Z &]"), "").trim())
        }

        if (curated.isNotEmpty()) {
            return@withContext curated
        }

        // Live YouTube artist channel search fallback
        searchLiveArtists(genreTagOrName)
    }

    /**
     * Parses human relative published strings like "2 hours ago", "3 days ago", "1 month ago" into epoch timestamps.
     */
    private fun parseRelativePublishedTimestamp(publishedText: String, title: String): Long {
        val now = System.currentTimeMillis()
        val lower = publishedText.lowercase().trim()
        val titleLower = title.lowercase()

        var bonus = 0L
        if (titleLower.contains("2026")) {
            bonus = 365L * 86400_000L * 2
        } else if (titleLower.contains("2025")) {
            bonus = 365L * 86400_000L
        }

        val num = Regex("""\d+""").find(lower)?.value?.toLongOrNull() ?: 1L
        val delta = when {
            lower.contains("second") || lower.contains("sec") -> num * 1000L
            lower.contains("minute") || lower.contains("min") -> num * 60_000L
            lower.contains("hour") -> num * 3600_000L
            lower.contains("day") -> num * 86400_000L
            lower.contains("week") -> num * 7L * 86400_000L
            lower.contains("month") -> num * 30L * 86400_000L
            lower.contains("year") -> num * 365L * 86400_000L
            else -> 180L * 86400_000L
        }
        return (now - delta) + bonus
    }

    /**
     * Fetches top official music videos for a specific artist, strictly sorted by latest video first.
     */
    suspend fun fetchMusicVideosForArtist(artist: MusicArtist): List<MusicVideo> = withContext(Dispatchers.IO) {
        val results = mutableListOf<MusicVideo>()
        val seen = mutableSetOf<String>()

        if (artist.ytChannelId.isNotBlank()) {
            val rssVideos = fetchVideosViaRss(artist.ytChannelId, artist.artistName)
            for (v in rssVideos) {
                if (seen.add(v.videoId)) {
                    results.add(v)
                }
            }
        }

        val searchVideos = searchLiveMusicVideos("${artist.artistName} official music video", page = 1) +
                           searchLiveMusicVideos("${artist.artistName} latest songs", page = 2)
        for (v in searchVideos) {
            if (seen.add(v.videoId)) {
                results.add(v)
            }
        }

        // Strictly sort artist results by latest video first (newest to oldest)
        results.sortedByDescending { it.publishedTimestamp }
    }

    /**
     * Fetches music videos for a specific genre/category with fast parallel queries and unbounded pagination.
     */
    suspend fun fetchMusicForGenre(genreTagOrName: String, page: Int = 1): List<MusicVideo> = withContext(Dispatchers.IO) {
        val clean = genreTagOrName.replace(Regex("[^a-zA-Z &]"), "").trim().lowercase()
        val queries = when {
            clean.contains("trending") || clean == "all" || clean.isBlank() -> when (page) {
                1 -> listOf("trending official music video 2026", "billboard hot 100 official music video", "top hits official video 2026")
                2 -> listOf("latest hit music releases official video", "new official music video 2026", "popular songs official video")
                3 -> listOf("global top music videos", "viral hit songs official music video", "top chart music videos 2026")
                4 -> listOf("billboard hot songs official video", "trending songs 2026 official video", "new music releases this week")
                5 -> listOf("vevo top music videos 2026", "hit songs music countdown", "popular music video tracks")
                6 -> listOf("award winning music videos", "platinum songs official video", "top 40 hit songs 2026")
                7 -> listOf("viral international music videos", "summer music video anthems", "chart topping hits official")
                8 -> listOf("essential music videos playlist", "official music video premiere 2026", "top worldwide music hits")
                else -> listOf(
                    "official music video hits collection part $page",
                    "top music videos 2026 mix $page",
                    "viral songs official video compilation $page"
                )
            }
            clean.contains("hiphop") || clean.contains("rap") -> when (page) {
                1 -> listOf("top hip hop rap official music video 2026", "latest rap hits official video", "new rap music releases 2026")
                2 -> listOf("popular hip hop songs official video", "rap music video essentials 2026", "trending rap official music video")
                3 -> listOf("hip hop hits official video", "classic 90s 2000s hip hop music videos", "hot rap tracks 2026")
                4 -> listOf("new hip hop songs 2026 official", "rap anthems official video", "rap music video playlist")
                5 -> listOf("underground and mainstream hip hop videos", "top rap freestyles and official videos", "drill and trap music videos 2026")
                6 -> listOf("legendary hip hop tracks official video", "rap billboard hot 100 hits", "southern hip hop music videos")
                else -> listOf("hip hop rap music videos mix $page", "rap hits 2026 collection $page", "hip hop songs playlist $page")
            }
            clean.contains("rnb") || clean.contains("soul") -> when (page) {
                1 -> listOf("top rnb soul official music video 2026", "r&b hits official video", "new r&b songs official music video")
                2 -> listOf("soul music video essentials 2026", "smooth r&b tracks official", "rnb music video hits")
                3 -> listOf("classic r&b music videos 90s 2000s", "contemporary r&b official video", "r&b soul anthems")
                4 -> listOf("rnb hits 2026 official video", "soul music video releases", "rnb music playlist")
                5 -> listOf("neo soul and modern r&b music videos", "slow jams r&b official videos", "r&b love songs official video")
                6 -> listOf("urban r&b billboard hits 2026", "afro r&b soul fusion videos", "smooth soul live sessions")
                else -> listOf("rnb soul music videos mix $page", "r&b songs playlist $page", "soul hits collection $page")
            }
            clean.contains("pop") || clean.contains("top 40") -> when (page) {
                1 -> listOf("pop hits official music video 2026", "top 40 songs official video", "billboard pop hits official video")
                2 -> listOf("new pop music releases 2026", "global pop anthems official video", "dance pop music videos")
                3 -> listOf("top 40 pop hits 2026 official", "viral pop songs official video", "pop music chart hits")
                4 -> listOf("pop anthems 2026", "popular songs official video", "pop music video playlist")
                5 -> listOf("synth pop and electro pop official videos", "acoustic pop official sessions 2026", "teen pop chart toppers")
                6 -> listOf("radio pop countdown official videos", "pop superstar music videos 2026", "viral tiktok pop hits official")
                else -> listOf("pop music video hits mix $page", "top 40 pop collection $page", "pop songs playlist $page")
            }
            clean.contains("rock") || clean.contains("alt") -> when (page) {
                1 -> listOf("rock alternative official music video 2026", "modern rock hits official video", "indie rock music videos")
                2 -> listOf("classic rock music videos", "hard rock anthems official music video", "alternative rock 90s 2000s")
                3 -> listOf("new rock songs 2026 official video", "alt rock chart hits", "rock music video playlist")
                4 -> listOf("rock essentials official video", "indie alternative music videos", "rock anthems")
                5 -> listOf("heavy metal and hard rock official videos", "punk rock and grunge music videos", "psychedelic and indie rock official")
                6 -> listOf("stadium rock live official videos", "modern alternative rock hits 2026", "rock guitar solos and music videos")
                else -> listOf("rock music videos mix $page", "alternative rock collection $page", "rock hits playlist $page")
            }
            clean.contains("country") || clean.contains("americana") -> when (page) {
                1 -> listOf("country music official video 2026", "top country hits official music video", "new country songs 2026 official video")
                2 -> listOf("nashville country music videos", "classic country hits official video", "americana folk music videos")
                3 -> listOf("country chart hits official video", "hot country songs 2026", "country music essentials")
                4 -> listOf("country music video playlist", "top country anthems 2026", "americana country hits")
                5 -> listOf("outlaw country and southern rock videos", "acoustic country sessions official", "bluegrass and folk music videos")
                6 -> listOf("stadium country music videos 2026", "contemporary country pop hits", "country heartland official videos")
                else -> listOf("country music videos mix $page", "country hits collection $page", "country songs playlist $page")
            }
            clean.contains("latin") || clean.contains("reggaeton") -> when (page) {
                1 -> listOf("top latin reggaeton official music video 2026", "latin hits 2026 official video", "musica urbana official video")
                2 -> listOf("latin pop hits official video", "bachata salsa reggaeton official video", "latin music essentials 2026")
                3 -> listOf("reggaeton hits 2026 official", "latin urban music video", "exitos latinos 2026")
                4 -> listOf("musica latina official video", "latin reggaeton hits", "latin playlist 2026")
                5 -> listOf("corridos tumbados official music videos", "latin trap and reggaeton 2026", "cumbia and salsa music videos")
                6 -> listOf("top latin billboard music videos", "exitos del momento musica latina", "latin festival anthems official")
                else -> listOf("latin reggaeton music videos mix $page", "latin hits collection $page", "reggaeton playlist $page")
            }
            clean.contains("electronic") || clean.contains("dance") || clean.contains("edm") -> when (page) {
                1 -> listOf("edm dance official music video 2026", "electronic music video hits", "house music official video 2026")
                2 -> listOf("festival edm hits official video", "club dance anthems official video", "electronic dance tracks")
                3 -> listOf("electronic music video playlist 2026", "techno house edm official", "dance pop edm video")
                4 -> listOf("club hits 2026 official video", "edm festival tracks", "electronic anthems")
                5 -> listOf("future bass and dubstep official videos", "trance and progressive house music videos", "deep house chillout official video")
                6 -> listOf("tomorrowland and edc festival anthems", "electro house chart hits 2026", "rave and dance music videos")
                else -> listOf("electronic dance music videos mix $page", "edm hits collection $page", "dance music playlist $page")
            }
            clean.contains("afro") || clean.contains("global") -> when (page) {
                1 -> listOf("afrobeats official music video 2026", "top afrobeats hits official video", "amapiano official music video 2026")
                2 -> listOf("african music hits 2026", "global afro fusion official video", "afro pop music videos 2026")
                3 -> listOf("afrobeats dance official video", "amapiano hits 2026", "afro music chart hits")
                4 -> listOf("afrobeats essentials 2026", "african pop official video", "afro anthems")
                5 -> listOf("nigerian and ghanaian afrobeats hits 2026", "south african amapiano official videos", "bongo flava and afro soul videos")
                6 -> listOf("global afrobeat dance challenges", "afropop superstars official videos", "afro fusion worldwide hits")
                else -> listOf("afrobeats global music videos mix $page", "afro hits collection $page", "amapiano playlist $page")
            }
            clean.contains("jazz") || clean.contains("blues") -> when (page) {
                1 -> listOf("jazz blues music video", "classic jazz performances official", "smooth jazz official video 2026")
                2 -> listOf("blues music live official", "modern jazz instrumental official", "soul blues tracks 2026")
                3 -> listOf("jazz fusion official video", "blues guitar live video", "jazz lounge music video")
                4 -> listOf("smooth jazz essentials", "blues anthems official", "jazz playlist")
                5 -> listOf("chicago blues and delta blues official", "contemporary jazz artists 2026", "jazz saxophone and piano sessions")
                6 -> listOf("montreux jazz festival live official", "blues rock guitar anthems", "vocal jazz standards official video")
                else -> listOf("jazz blues music mix $page", "jazz hits collection $page", "blues songs playlist $page")
            }
            else -> listOf(
                "$genreTagOrName official music video 2026 page $page",
                "$genreTagOrName song hits 2026 mix $page",
                "$genreTagOrName music video collection $page"
            )
        }

        // Run queries in parallel for sub-second smart loading
        val queryResults = coroutineScope {
            queries.map { q ->
                async(Dispatchers.IO) {
                    queryYouTubeMusicVideos(q)
                }
            }.awaitAll().flatten()
        }

        val allVideos = mutableListOf<MusicVideo>()
        val seenIds = mutableSetOf<String>()

        for (v in queryResults) {
            if (seenIds.add(v.videoId)) {
                allVideos.add(v)
            }
        }

        // Fallback: If pagination yielded fewer than 10 videos, supplement with artists from this genre
        if (allVideos.size < 10) {
            val genreArtists = MusicData.ARTISTS.filter {
                if (clean.contains("trending") || clean == "all" || clean.isBlank()) true
                else it.genre.lowercase().contains(clean) || clean.contains(it.genre.lowercase().replace(Regex("[^a-zA-Z &]"), "").trim())
            }
            if (genreArtists.isNotEmpty()) {
                val candidateArtists = genreArtists.shuffled().take(3)
                for (art in candidateArtists) {
                    val artVideos = fetchMusicVideosForArtist(art)
                    for (av in artVideos) {
                        if (seenIds.add(av.videoId)) {
                            allVideos.add(av)
                        }
                    }
                }
            }
        }

        // Give stronger weight to latest titles
        allVideos.sortedByDescending { it.publishedTimestamp }
    }

    /**
     * Searches YouTube for music videos with fast parallel queries, prioritizing official videos and latest releases.
     */
    suspend fun searchLiveMusicVideos(query: String, page: Int = 1): List<MusicVideo> = withContext(Dispatchers.IO) {
        val q = query.trim()
        val queries = when (page) {
            1 -> {
                if (q.contains("video", ignoreCase = true) || q.contains("official", ignoreCase = true)) {
                    listOf(q, "$q 2026", "$q song", "$q audio")
                } else {
                    listOf("$q official music video", "$q official music video 2026", "$q song", "$q official audio")
                }
            }
            2 -> listOf("$q official video hits", "$q full song 2026", "$q music video hd", "$q live official")
            3 -> listOf("$q album tracks", "$q live music video", "$q official audio release", "$q top track video")
            4 -> listOf("$q songs playlist", "$q essential music video", "$q music hd", "$q vevo official")
            5 -> listOf("$q greatest hits video", "$q live acoustic official", "$q music collection", "$q singles video")
            6 -> listOf("$q live performance 2026", "$q audio tracks official", "$q best songs video", "$q concert live")
            else -> listOf("$q official music video mix $page", "$q song collection $page", "$q music video hd $page")
        }

        // Run search queries concurrently
        val queryResults = coroutineScope {
            queries.map { sq ->
                async(Dispatchers.IO) {
                    queryYouTubeMusicVideos(sq)
                }
            }.awaitAll().flatten()
        }

        val allVideos = mutableListOf<MusicVideo>()
        val seenIds = mutableSetOf<String>()

        for (v in queryResults) {
            if (seenIds.add(v.videoId)) {
                allVideos.add(v)
            }
        }

        // Rank latest titles first
        allVideos.sortedByDescending { it.publishedTimestamp }
    }

    /**
     * Searches artists matching query.
     */
    suspend fun searchLiveArtists(query: String): List<MusicArtist> = withContext(Dispatchers.IO) {
        val curatedMatches = MusicData.ARTISTS.filter {
            it.artistName.contains(query, ignoreCase = true) || it.genre.contains(query, ignoreCase = true)
        }
        if (curatedMatches.isNotEmpty()) {
            return@withContext curatedMatches
        }

        try {
            val encoded = URLEncoder.encode("$query artist topic", "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encoded&sp=EgIQAg%253D%253D"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", defaultUserAgent)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext emptyList()

            val initialDataMatch = Regex("""var ytInitialData = (\{.+?\});</script>""").find(html)
                ?: Regex("""ytInitialData = (\{.+?\});</script>""").find(html)
            val jsonStr = initialDataMatch?.groupValues?.get(1) ?: return@withContext emptyList()

            val list = mutableListOf<MusicArtist>()
            val root = gson.fromJson(jsonStr, JsonObject::class.java)
            val contents = root.getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnSearchResultsRenderer")
                ?.getAsJsonObject("primaryContents")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents") ?: return@withContext emptyList()

            for (section in contents) {
                val itemSection = section.asJsonObject.getAsJsonObject("itemSectionRenderer") ?: continue
                val items = itemSection.getAsJsonArray("contents") ?: continue
                for (item in items) {
                    val cr = item.asJsonObject.getAsJsonObject("channelRenderer") ?: continue
                    val channelId = cr.get("channelId")?.asString ?: continue
                    val title = cr.getAsJsonObject("title")?.get("simpleText")?.asString ?: "Music Artist"
                    val subs = cr.getAsJsonObject("subscriberCountText")?.get("simpleText")?.asString ?: "YouTube Artist"
                    val desc = cr.getAsJsonObject("descriptionSnippet")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: ""
                    val avatarThumb = cr.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString ?: ""

                    val avatar = if (avatarThumb.startsWith("//")) "https:$avatarThumb" else avatarThumb

                    list.add(
                        MusicArtist(
                            id = "yt_art_$channelId",
                            artistName = title.replace(" - Topic", "").trim(),
                            genre = "Music",
                            subscribers = subs,
                            avatar = avatar,
                            bio = desc.ifBlank { "$title - Official YouTube Music Channel" },
                            ytChannelId = channelId
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            Log.e(tag, "searchLiveArtists error: ${e.message}")
            emptyList()
        }
    }

    private fun queryYouTubeMusicVideos(query: String): List<MusicVideo> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", defaultUserAgent)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return emptyList()

            val initialDataMatch = Regex("""var ytInitialData = (\{.+?\});</script>""").find(html)
                ?: Regex("""ytInitialData = (\{.+?\});</script>""").find(html)
            val jsonStr = initialDataMatch?.groupValues?.get(1)

            val list = mutableListOf<MusicVideo>()
            val seenVideoIds = mutableSetOf<String>()

            if (!jsonStr.isNullOrBlank()) {
                try {
                    val root = gson.fromJson(jsonStr, JsonObject::class.java)
                    val contents = root.getAsJsonObject("contents")
                        ?.getAsJsonObject("twoColumnSearchResultsRenderer")
                        ?.getAsJsonObject("primaryContents")
                        ?.getAsJsonObject("sectionListRenderer")
                        ?.getAsJsonArray("contents")

                    if (contents != null) {
                        for (section in contents) {
                            val itemSection = section.asJsonObject.getAsJsonObject("itemSectionRenderer") ?: continue
                            val items = itemSection.getAsJsonArray("contents") ?: continue
                            for (item in items) {
                                val vr = item.asJsonObject.getAsJsonObject("videoRenderer")
                                    ?: item.asJsonObject.getAsJsonObject("compactVideoRenderer")
                                if (vr != null) {
                                    val videoId = vr.get("videoId")?.asString ?: continue
                                    if (seenVideoIds.add(videoId)) {
                                        val title = vr.getAsJsonObject("title")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString
                                            ?: vr.getAsJsonObject("title")?.get("simpleText")?.asString ?: "Music Video"
                                        val artistName = vr.getAsJsonObject("ownerText")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString
                                            ?: vr.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: "Artist"
                                        val published = vr.getAsJsonObject("publishedTimeText")?.get("simpleText")?.asString ?: "Recent"
                                        val views = vr.getAsJsonObject("viewCountText")?.get("simpleText")?.asString ?: "Official Video"
                                        val length = vr.getAsJsonObject("lengthText")?.get("simpleText")?.asString ?: "Music Video"
                                        val thumb = vr.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString
                                            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                        val fullThumb = if (thumb.startsWith("//")) "https:$thumb" else thumb

                                        list.add(
                                            MusicVideo(
                                                id = "yt_$videoId",
                                                title = title,
                                                artistName = artistName,
                                                videoId = videoId,
                                                thumbnailUrl = fullThumb,
                                                duration = length,
                                                views = views,
                                                published = published,
                                                publishedTimestamp = parseRelativePublishedTimestamp(published, title)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "JSON parsing in queryYouTubeMusicVideos: ${e.message}")
                }
            }

            // Fallback Regex Extraction if json parsing had limited results
            if (list.size < 6) {
                val videoMatches = Regex(""""videoId":"([a-zA-Z0-9_-]{11})","thumbnail":\{"thumbnails":\[\{"url":"([^"]+)".*?"title":\{"runs":\[\{"text":"([^"]+)".*?"ownerText":\{"runs":\[\{"text":"([^"]+)"""").findAll(html)
                for (m in videoMatches) {
                    val (vid, thumbUrl, title, artist) = m.destructured
                    if (seenVideoIds.add(vid)) {
                        list.add(
                            MusicVideo(
                                id = "yt_$vid",
                                title = title.replace("\\u0026", "&").replace("\\\"", "\""),
                                artistName = artist.replace("\\u0026", "&"),
                                videoId = vid,
                                thumbnailUrl = if (thumbUrl.startsWith("//")) "https:$thumbUrl" else thumbUrl.replace("\\u0026", "&"),
                                duration = "Music Video",
                                views = "Official Video",
                                published = "Popular",
                                publishedTimestamp = parseRelativePublishedTimestamp("Recent", title)
                            )
                        )
                    }
                }
            }

            list
        } catch (e: Exception) {
            Log.e(tag, "queryYouTubeMusicVideos error: ${e.message}")
            emptyList()
        }
    }

    private fun fetchVideosViaRss(channelId: String, artistName: String): List<MusicVideo> {
        return try {
            val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
            val req = Request.Builder().url(feedUrl).header("User-Agent", defaultUserAgent).build()
            val resp = client.newCall(req).execute()
            val xml = resp.body?.string() ?: return emptyList()
            parseYouTubeXmlFeed(xml, artistName)
        } catch (e: Exception) {
            Log.e(tag, "fetchVideosViaRss error: ${e.message}")
            emptyList()
        }
    }

    private fun parseYouTubeXmlFeed(xml: String, artistName: String): List<MusicVideo> {
        val videos = mutableListOf<MusicVideo>()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var inEntry = false
            var currentTitle = ""
            var currentVideoId = ""
            var currentPublished = ""
            var currentPublishedTimestamp = 0L
            var currentThumbnail = ""

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name.equals("entry", ignoreCase = true)) {
                            inEntry = true
                            currentTitle = ""
                            currentVideoId = ""
                            currentPublished = ""
                            currentPublishedTimestamp = 0L
                            currentThumbnail = ""
                        } else if (inEntry) {
                            when {
                                name.equals("title", ignoreCase = true) -> {
                                    currentTitle = parser.nextText()
                                }
                                name.equals("yt:videoId", ignoreCase = true) || name.equals("videoId", ignoreCase = true) -> {
                                    currentVideoId = parser.nextText()
                                }
                                name.equals("published", ignoreCase = true) -> {
                                    val rawPub = parser.nextText()
                                    currentPublished = rawPub.take(10)
                                    currentPublishedTimestamp = try {
                                        dateFormat.parse(rawPub)?.time ?: 0L
                                    } catch (_: Throwable) {
                                        0L
                                    }
                                }
                                name.equals("media:thumbnail", ignoreCase = true) -> {
                                    val url = parser.getAttributeValue(null, "url")
                                    if (!url.isNullOrBlank() && currentThumbnail.isBlank()) {
                                        currentThumbnail = url
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name.equals("entry", ignoreCase = true)) {
                            inEntry = false
                            if (currentVideoId.isNotBlank() || currentTitle.isNotBlank()) {
                                if (currentThumbnail.isBlank() && currentVideoId.isNotBlank()) {
                                    currentThumbnail = "https://i.ytimg.com/vi/$currentVideoId/hqdefault.jpg"
                                }
                                videos.add(
                                    MusicVideo(
                                        id = "yt_$currentVideoId",
                                        title = currentTitle,
                                        artistName = artistName,
                                        videoId = currentVideoId,
                                        thumbnailUrl = currentThumbnail,
                                        duration = "Official Music Video",
                                        views = "Official Video",
                                        published = currentPublished,
                                        publishedTimestamp = currentPublishedTimestamp
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(tag, "Music XML parsing error: ${e.message}")
        }
        return videos.sortedByDescending { it.publishedTimestamp }
    }
}
