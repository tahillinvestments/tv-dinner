package com.troyh.tvdinner.data.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.troyh.tvdinner.data.model.PodcastChannel
import com.troyh.tvdinner.data.model.PodcastEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class YouTubePodcastService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val tag = "YouTubePodcastService"
    private val gson = Gson()

    private val defaultUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Pings real live podcast channels combining curated roster + iTunes Podcast directory + YouTube search.
     */
    suspend fun fetchLivePodcastChannels(category: String): List<PodcastChannel> = withContext(Dispatchers.IO) {
        val catClean = category.replace(Regex("[^a-zA-Z &]"), "").trim().lowercase()
        val curated = com.troyh.tvdinner.data.podcasts.PodcastsData.CHANNELS.filter {
            if (catClean == "trending" || catClean == "all" || catClean.isBlank()) true
            else it.category.lowercase().contains(catClean) || catClean.contains(it.category.lowercase().replace(Regex("[^a-zA-Z &]"), "").trim())
        }

        val searchTerm = when (catClean) {
            "all", "trending" -> "top trending podcast"
            "tech", "ai & tech", "tech & ai" -> "technology artificial intelligence podcast"
            "business", "business & ideas" -> "business startups investing podcast"
            "science", "science & health" -> "science neuroscience health podcast"
            "culture", "culture & talk", "comedy" -> "comedy interview culture talk show podcast"
            "news", "news & politics" -> "news politics current events podcast"
            else -> "$category podcast"
        }

        val itunesChannels = fetchItunesPodcastChannels(searchTerm, category)
        val combined = mutableListOf<PodcastChannel>()
        val seenNames = mutableSetOf<String>()

        // Curated channels first
        for (ch in curated) {
            val key = ch.channelName.lowercase().trim()
            if (seenNames.add(key)) {
                combined.add(ch)
            }
        }

        // Live iTunes channels
        for (ch in itunesChannels) {
            val key = ch.channelName.lowercase().trim()
            if (seenNames.add(key)) {
                combined.add(ch)
            }
        }

        if (combined.size < 10) {
            val ytChannels = searchLiveYouTubeChannels(searchTerm, category)
            for (ch in ytChannels) {
                val key = ch.channelName.lowercase().trim()
                if (seenNames.add(key)) {
                    combined.add(ch)
                }
            }
        }

        combined
    }

    private fun fetchItunesPodcastChannels(searchTerm: String, categoryTag: String): List<PodcastChannel> {
        return try {
            val encoded = URLEncoder.encode(searchTerm, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encoded&media=podcast&entity=podcast&limit=40"
            val req = Request.Builder().url(url).header("User-Agent", defaultUserAgent).build()
            val resp = client.newCall(req).execute()
            val json = resp.body?.string() ?: return emptyList()

            val root = gson.fromJson(json, JsonObject::class.java)
            val results = root.getAsJsonArray("results") ?: return emptyList()

            val list = mutableListOf<PodcastChannel>()
            for (elem in results) {
                val obj = elem.asJsonObject
                val trackName = obj.get("trackName")?.asString ?: obj.get("collectionName")?.asString ?: continue
                val artistName = obj.get("artistName")?.asString ?: "Host"
                val artwork = obj.get("artworkUrl600")?.asString ?: obj.get("artworkUrl100")?.asString ?: ""
                val genre = obj.get("primaryGenreName")?.asString ?: categoryTag
                val trackId = obj.get("trackId")?.asLong ?: obj.get("collectionId")?.asLong ?: System.currentTimeMillis()

                list.add(
                    PodcastChannel(
                        id = "itunes_$trackId",
                        channelName = trackName,
                        host = artistName,
                        category = genre,
                        subscribers = "Apple Podcast Directory",
                        avatar = artwork,
                        description = "$trackName hosted by $artistName. Real-time trending podcast in $genre.",
                        ytChannelId = ""
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(tag, "fetchItunesPodcastChannels error: ${e.message}")
            emptyList()
        }
    }

    private fun searchLiveYouTubeChannels(searchTerm: String, categoryTag: String): List<PodcastChannel> {
        return try {
            val encoded = URLEncoder.encode("$searchTerm podcast", "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encoded&sp=EgIQAg%253D%253D"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", defaultUserAgent)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return emptyList()

            val initialDataMatch = Regex("""var ytInitialData = (\{.+?\});</script>""").find(html)
                ?: Regex("""ytInitialData = (\{.+?\});</script>""").find(html)
            val jsonStr = initialDataMatch?.groupValues?.get(1) ?: return emptyList()

            val list = mutableListOf<PodcastChannel>()
            val root = gson.fromJson(jsonStr, JsonObject::class.java)
            val contents = root.getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnSearchResultsRenderer")
                ?.getAsJsonObject("primaryContents")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents") ?: return emptyList()

            for (section in contents) {
                val itemSection = section.asJsonObject.getAsJsonObject("itemSectionRenderer") ?: continue
                val items = itemSection.getAsJsonArray("contents") ?: continue
                for (item in items) {
                    val cr = item.asJsonObject.getAsJsonObject("channelRenderer") ?: continue
                    val channelId = cr.get("channelId")?.asString ?: continue
                    val title = cr.getAsJsonObject("title")?.get("simpleText")?.asString ?: "Podcast Channel"
                    val subs = cr.getAsJsonObject("subscriberCountText")?.get("simpleText")?.asString ?: "YouTube Podcast"
                    val desc = cr.getAsJsonObject("descriptionSnippet")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: ""
                    val avatarThumb = cr.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString ?: ""

                    val avatar = if (avatarThumb.startsWith("//")) "https:$avatarThumb" else avatarThumb

                    list.add(
                        PodcastChannel(
                            id = "yt_chan_$channelId",
                            channelName = title,
                            host = title,
                            category = categoryTag,
                            subscribers = subs,
                            avatar = avatar,
                            description = desc.ifBlank { "$title - Official YouTube Podcast Channel" },
                            ytChannelId = channelId
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            Log.e(tag, "searchLiveYouTubeChannels error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Pings real live YouTube search for trending / category video podcast episodes with pagination.
     */
    suspend fun searchLiveEpisodes(categoryOrQuery: String, page: Int = 1): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        val catClean = categoryOrQuery.replace(Regex("[^a-zA-Z &]"), "").trim().lowercase()

        // Curate queries based on category and page
        val queries = when (catClean) {
            "all", "trending" -> when (page) {
                1 -> listOf("trending podcast full episode", "joe rogan theo von kill tony podcast full episode")
                2 -> listOf("popular comedy talk podcast full episode", "top video podcast full episode")
                else -> listOf("podcast full episode 2026")
            }
            "tech", "ai & tech", "tech & ai" -> when (page) {
                1 -> listOf("artificial intelligence tech podcast full episode", "lex fridman all in podcast full episode")
                2 -> listOf("mkbhd waveform y combinator podcast full episode", "silicon valley founders tech podcast")
                else -> listOf("future AI technology podcast interview")
            }
            "business", "business & ideas" -> when (page) {
                1 -> listOf("business entrepreneurship investing podcast full episode", "diary of a ceo acquired podcast full episode")
                2 -> listOf("my first million startup podcast full episode", "billionaire business strategy podcast")
                else -> listOf("finance economics wealth building podcast")
            }
            "science", "science & health" -> when (page) {
                1 -> listOf("huberman lab science health podcast full episode", "startalk veritasium science podcast")
                2 -> listOf("neuroscience longevity modern wisdom podcast full episode", "biology physics psychology podcast")
                else -> listOf("medical scientific breakthroughs podcast")
            }
            "culture", "culture & talk", "comedy" -> when (page) {
                1 -> listOf("comedy interview talk podcast full episode", "flagrant bad friends conan o brien podcast")
                2 -> listOf("hot ones drink champs club shay shay full episode", "tinydesk music podcast interview")
                else -> listOf("celebrity talk show podcast full episode")
            }
            "news", "news & politics" -> when (page) {
                1 -> listOf("daily news politics podcast full episode", "pbd podcast shawn ryan show full episode")
                2 -> listOf("ben shapiro the daily ny times podcast", "world geopolitics investigative news podcast")
                else -> listOf("breaking news analysis podcast")
            }
            else -> listOf(
                if (categoryOrQuery.contains("podcast", ignoreCase = true)) categoryOrQuery
                else "$categoryOrQuery podcast full episode"
            )
        }

        val allEpisodes = mutableListOf<PodcastEpisode>()
        val seenVideoIds = mutableSetOf<String>()

        for (q in queries) {
            val results = queryYouTubeEpisodes(q)
            for (ep in results) {
                if (seenVideoIds.add(ep.videoId)) {
                    allEpisodes.add(ep)
                }
            }
        }

        // Guaranteed fallback: If live YouTube search returned few results, fetch from curated channel RSS feeds
        if (allEpisodes.size < 10) {
            val curatedChannels = com.troyh.tvdinner.data.podcasts.PodcastsData.CHANNELS.filter {
                if (catClean == "trending" || catClean == "all" || catClean.isBlank()) true
                else it.category.lowercase().contains(catClean) || catClean.contains(it.category.lowercase().replace(Regex("[^a-zA-Z &]"), "").trim())
            }
            for (ch in curatedChannels) {
                if (ch.ytChannelId.isNotBlank()) {
                    val rssList = fetchEpisodesViaRss(ch.ytChannelId, ch.channelName, ch.id)
                    for (ep in rssList) {
                        if (seenVideoIds.add(ep.videoId)) {
                            allEpisodes.add(ep)
                        }
                    }
                }
            }
        }

        allEpisodes
    }

    private fun queryYouTubeEpisodes(query: String): List<PodcastEpisode> {
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
            val jsonStr = initialDataMatch?.groupValues?.get(1) ?: return emptyList()

            val list = mutableListOf<PodcastEpisode>()
            val root = gson.fromJson(jsonStr, JsonObject::class.java)
            val contents = root.getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnSearchResultsRenderer")
                ?.getAsJsonObject("primaryContents")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents") ?: return emptyList()

            for (section in contents) {
                val itemSection = section.asJsonObject.getAsJsonObject("itemSectionRenderer") ?: continue
                val items = itemSection.getAsJsonArray("contents") ?: continue
                for (item in items) {
                    val vr = item.asJsonObject.getAsJsonObject("videoRenderer") ?: continue
                    val videoId = vr.get("videoId")?.asString ?: continue
                    val title = vr.getAsJsonObject("title")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString
                        ?: vr.getAsJsonObject("title")?.get("simpleText")?.asString ?: "Podcast Episode"
                    val channelName = vr.getAsJsonObject("ownerText")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString
                        ?: vr.getAsJsonObject("longBylineText")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: "YouTube Podcast"
                    val published = vr.getAsJsonObject("publishedTimeText")?.get("simpleText")?.asString ?: "Recent"
                    val desc = vr.getAsJsonObject("descriptionSnippet")?.getAsJsonArray("runs")?.firstOrNull()?.asJsonObject?.get("text")?.asString ?: ""
                    val thumb = vr.getAsJsonObject("thumbnail")?.getAsJsonArray("thumbnails")?.lastOrNull()?.asJsonObject?.get("url")?.asString
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                    val fullThumb = if (thumb.startsWith("//")) "https:$thumb" else thumb

                    list.add(
                        PodcastEpisode(
                            id = "yt_$videoId",
                            title = title,
                            description = desc,
                            published = published,
                            thumbnailUrl = fullThumb,
                            videoId = videoId,
                            channelName = channelName,
                            channelId = "chan_${channelName.replace(" ", "_").lowercase()}",
                            publishedTimestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            Log.e(tag, "queryYouTubeEpisodes error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetches podcast episodes for a specific channel, strictly sorted in descending chronological order (newest to oldest).
     */
    suspend fun fetchEpisodesForChannel(channel: PodcastChannel): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        if (channel.ytChannelId.isNotBlank()) {
            val rssEpisodes = fetchEpisodesViaRss(channel.ytChannelId, channel.channelName, channel.id)
            if (rssEpisodes.isNotEmpty()) {
                return@withContext rssEpisodes.sortedByDescending { it.publishedTimestamp }
            }
        }

        // Search live YouTube episodes for channel name, sorted descending
        val results = searchLiveEpisodes("${channel.channelName} podcast")
        results.filter {
            it.channelName.contains(channel.channelName, ignoreCase = true) ||
            channel.channelName.contains(it.channelName, ignoreCase = true) ||
            it.title.contains(channel.channelName, ignoreCase = true)
        }.ifEmpty { results }
    }

    private fun fetchEpisodesViaRss(channelId: String, channelName: String, localId: String): List<PodcastEpisode> {
        return try {
            val feedUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
            val req = Request.Builder().url(feedUrl).header("User-Agent", defaultUserAgent).build()
            val resp = client.newCall(req).execute()
            val xml = resp.body?.string() ?: return emptyList()
            parseYouTubeXmlFeed(xml, channelName, localId)
        } catch (e: Exception) {
            Log.e(tag, "fetchEpisodesViaRss error: ${e.message}")
            emptyList()
        }
    }

    private fun parseYouTubeXmlFeed(xml: String, channelName: String, channelId: String): List<PodcastEpisode> {
        val episodes = mutableListOf<PodcastEpisode>()
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
            var currentDescription = ""
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
                            currentDescription = ""
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
                                name.equals("media:description", ignoreCase = true) || name.equals("description", ignoreCase = true) -> {
                                    currentDescription = parser.nextText().take(300)
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
                                episodes.add(
                                    PodcastEpisode(
                                        id = "yt_$currentVideoId",
                                        title = currentTitle,
                                        description = currentDescription,
                                        published = currentPublished,
                                        thumbnailUrl = currentThumbnail,
                                        videoId = currentVideoId,
                                        channelName = channelName,
                                        channelId = channelId,
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
            Log.e(tag, "XML parsing error: ${e.message}")
        }
        // Ensure episodes are sorted from latest/newest to oldest
        return episodes.sortedByDescending { it.publishedTimestamp }
    }
}
