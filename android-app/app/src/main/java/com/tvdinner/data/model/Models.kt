package com.tvdinner.data.model

import com.google.gson.annotations.SerializedName

data class CredentialEntry(
    val phone: String,
    val user: String,
    val pswd: String
)

data class LiveCategory(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("parent_id") val parentId: Int = 0
)

data class Channel(
    @SerializedName("num") val num: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("stream_type") val streamType: String? = "live",
    @SerializedName("stream_id") val streamId: Int = 0,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("epg_channel_id") val epgChannelId: String? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("custom_sid") val customSid: String? = null,
    @SerializedName("tv_archive") val tvArchive: Int = 0,
    @SerializedName("direct_source") val directSource: String? = null,
    val directStreamUrl: String? = null,
    var portalUrl: String? = null,
    var streamUser: String? = null,
    var streamPassword: String? = null
)

data class MovieCategory(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("parent_id") val parentId: Int = 0
)

data class Movie(
    @SerializedName("num") val num: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("title") val title: String? = null,
    @SerializedName("stream_type") val streamType: String? = "movie",
    @SerializedName("stream_id") val streamId: Int = 0,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("rating_5based") val rating5Based: Double? = null,
    @SerializedName("added") val added: String? = null,
    @SerializedName("category_id") val categoryId: String? = null,
    @SerializedName("container_extension") val containerExtension: String = "mp4",
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null
) {
    val displayTitle: String get() = if (title.isNullOrBlank()) name else title
    val displayPoster: String? get() = streamIcon
}

data class SeriesCategory(
    @SerializedName("category_id") val categoryId: String,
    @SerializedName("category_name") val categoryName: String,
    @SerializedName("parent_id") val parentId: Int = 0
)

data class Series(
    @SerializedName("num") val num: Int = 0,
    @SerializedName("name") val name: String = "",
    @SerializedName("title") val title: String? = null,
    @SerializedName("series_id") val seriesId: Int = 0,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("cast") val cast: String? = null,
    @SerializedName("director") val director: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("releaseDate") val releaseDate: String? = null,
    @SerializedName("rating") val rating: String? = null,
    @SerializedName("rating_5based") val rating5Based: Double? = null,
    @SerializedName("category_id") val categoryId: String? = null
) {
    val displayTitle: String get() = if (title.isNullOrBlank()) name else title
    val displayCover: String? get() = cover
}

data class EpisodeInfo(
    @SerializedName("duration_secs") val durationSecs: Int? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("plot") val plot: String? = null,
    @SerializedName("movie_image") val movieImage: String? = null,
    @SerializedName("bitrate") val bitrate: Int? = null
)

data class Episode(
    @SerializedName("id") val id: String = "",
    @SerializedName("episode_num") val episodeNum: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName("container_extension") val containerExtension: String = "mp4",
    @SerializedName("info") val info: EpisodeInfo? = null,
    @SerializedName("season") val season: Int = 1
)

data class SeriesInfoResponse(
    @SerializedName("seasons") val seasons: List<Map<String, Any>>? = null,
    @SerializedName("info") val info: Map<String, Any>? = null,
    @SerializedName("episodes") val episodes: Map<String, List<Episode>>? = null
)

data class PodcastChannel(
    val id: String,
    val channelName: String,
    val host: String,
    val category: String,
    val subscribers: String,
    val avatar: String,
    val description: String,
    val ytChannelId: String
)

data class PodcastEpisode(
    val id: String,
    val title: String,
    val description: String,
    val published: String,
    val thumbnailUrl: String,
    val videoId: String,
    val channelName: String,
    val channelId: String,
    val publishedTimestamp: Long = 0L
)

data class EpgProgram(
    @SerializedName("id") val id: String? = null,
    @SerializedName("epg_id") val epgId: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("lang") val lang: String? = null,
    @SerializedName("start") val start: String? = null,
    @SerializedName("end") val end: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("channel_id") val channelId: String? = null,
    @SerializedName("start_timestamp") val startTimestamp: String? = null,
    @SerializedName("stop_timestamp") val stopTimestamp: String? = null,
    @SerializedName("now_playing") val nowPlaying: Int = 0
) {
    val decodedTitle: String
        get() {
            val raw = title ?: return "Live Broadcast"
            return decodeBase64OrRaw(raw).ifBlank { "Live Broadcast" }
        }

    val decodedDescription: String?
        get() {
            val raw = description ?: return null
            return decodeBase64OrRaw(raw)
        }

    companion object {
        fun decodeBase64OrRaw(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.length >= 4 && trimmed.length % 4 == 0 && trimmed.matches(Regex("^[A-Za-z0-9+/=]+$"))) {
                try {
                    val bytes = try {
                        android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
                    } catch (_: Throwable) {
                        java.util.Base64.getDecoder().decode(trimmed)
                    }
                    val decoded = String(bytes, Charsets.UTF_8).trim()
                    if (decoded.isNotEmpty() && decoded.none { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }) {
                        return decoded
                    }
                } catch (_: Throwable) {}
            }
            return raw
        }
    }
}

data class ShortEpgResponse(
    @SerializedName("epg_listings") val epgListings: List<EpgProgram>? = null
)

