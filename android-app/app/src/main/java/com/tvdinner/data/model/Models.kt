package com.tvdinner.data.model

import com.google.gson.*
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

data class CredentialEntry(
    val user: String,
    val pswd: String,
    val phone: String = ""
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
    @SerializedName(value = "id", alternate = ["stream_id", "episode_id"]) val id: String = "",
    @SerializedName(value = "episode_num", alternate = ["episode_number", "episode"]) val episodeNum: Int = 0,
    @SerializedName("title") val title: String = "",
    @SerializedName(value = "container_extension", alternate = ["extension", "ext", "container"]) val containerExtension: String = "mp4",
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

class EpgProgramDeserializer : JsonDeserializer<EpgProgram> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): EpgProgram {
        if (json == null || !json.isJsonObject) return EpgProgram()
        val obj = json.asJsonObject

        fun getStringVal(vararg keys: String): String? {
            for (key in keys) {
                val elem = obj.get(key)
                if (elem != null && !elem.isJsonNull) {
                    val s = if (elem.isJsonPrimitive) elem.asString else elem.toString()
                    val trimmed = s.trim()
                    if (trimmed.isNotEmpty() && trimmed != "null") return trimmed
                }
            }
            return null
        }

        val id = getStringVal("id")
        val epgId = getStringVal("epg_id")
        val title = getStringVal("title", "name", "event_name", "program_name", "title_base64")
        val lang = getStringVal("lang", "language")
        val start = getStringVal("start", "start_time")
        val end = getStringVal("end", "stop", "stop_time", "end_time")
        val description = getStringVal("description", "descr", "plot", "short_desc", "summary")
        val channelId = getStringVal("channel_id", "stream_id")
        val startTimestamp = getStringVal("start_timestamp", "start_time", "start_epoch", "time_from")
        val stopTimestamp = getStringVal("stop_timestamp", "end_timestamp", "stop_time", "stop_epoch", "time_to")

        val nowPlayingElem = obj.get("now_playing") ?: obj.get("nowplaying") ?: obj.get("has_now_playing")
        val nowPlaying = when {
            nowPlayingElem == null || nowPlayingElem.isJsonNull -> 0
            nowPlayingElem.isJsonPrimitive && nowPlayingElem.asJsonPrimitive.isBoolean -> if (nowPlayingElem.asBoolean) 1 else 0
            nowPlayingElem.isJsonPrimitive && nowPlayingElem.asJsonPrimitive.isNumber -> nowPlayingElem.asInt
            nowPlayingElem.isJsonPrimitive && nowPlayingElem.asJsonPrimitive.isString -> {
                val s = nowPlayingElem.asString.trim()
                if (s == "1" || s.equals("true", ignoreCase = true)) 1 else 0
            }
            else -> 0
        }

        return EpgProgram(
            id = id,
            epgId = epgId,
            title = title,
            lang = lang,
            start = start,
            end = end,
            description = description,
            channelId = channelId,
            startTimestamp = startTimestamp,
            stopTimestamp = stopTimestamp,
            nowPlaying = nowPlaying
        )
    }
}

class ShortEpgResponseDeserializer : JsonDeserializer<ShortEpgResponse> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): ShortEpgResponse {
        if (json == null || json.isJsonNull) return ShortEpgResponse(emptyList())
        val programs = mutableListOf<EpgProgram>()

        if (json.isJsonArray) {
            for (elem in json.asJsonArray) {
                if (elem.isJsonObject) {
                    val prog = context?.deserialize<EpgProgram>(elem, EpgProgram::class.java)
                    if (prog != null && (!prog.title.isNullOrBlank() || !prog.start.isNullOrBlank())) {
                        programs.add(prog)
                    }
                }
            }
        } else if (json.isJsonObject) {
            val obj = json.asJsonObject
            val listingsElem = obj.get("epg_listings") ?: obj.get("listings") ?: obj.get("epg")
            if (listingsElem != null && listingsElem.isJsonArray) {
                for (elem in listingsElem.asJsonArray) {
                    if (elem.isJsonObject) {
                        val prog = context?.deserialize<EpgProgram>(elem, EpgProgram::class.java)
                        if (prog != null && (!prog.title.isNullOrBlank() || !prog.start.isNullOrBlank())) {
                            programs.add(prog)
                        }
                    }
                }
            } else if (listingsElem != null && listingsElem.isJsonObject) {
                for ((_, value) in listingsElem.asJsonObject.entrySet()) {
                    if (value.isJsonObject) {
                        val prog = context?.deserialize<EpgProgram>(value, EpgProgram::class.java)
                        if (prog != null && (!prog.title.isNullOrBlank() || !prog.start.isNullOrBlank())) {
                            programs.add(prog)
                        }
                    }
                }
            } else {
                for ((_, value) in obj.entrySet()) {
                    if (value.isJsonObject) {
                        val prog = context?.deserialize<EpgProgram>(value, EpgProgram::class.java)
                        if (prog != null && (!prog.title.isNullOrBlank() || !prog.start.isNullOrBlank())) {
                            programs.add(prog)
                        }
                    }
                }
            }
        }
        return ShortEpgResponse(programs)
    }
}


