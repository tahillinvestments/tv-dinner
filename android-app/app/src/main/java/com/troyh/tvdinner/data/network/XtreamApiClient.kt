package com.troyh.tvdinner.data.network

import android.annotation.SuppressLint
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.troyh.tvdinner.data.model.Channel
import com.troyh.tvdinner.data.model.LiveCategory
import com.troyh.tvdinner.data.model.Movie
import com.troyh.tvdinner.data.model.MovieCategory
import com.troyh.tvdinner.data.model.Series
import com.troyh.tvdinner.data.model.SeriesCategory
import com.troyh.tvdinner.data.model.SeriesInfoResponse
import com.troyh.tvdinner.data.model.ShortEpgResponse
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class XtreamApiClient {
    private val tag = "XtreamApiClient"
    private val gson = Gson()

    val okHttpClient: OkHttpClient by lazy {
        createUnsafeOkHttpClient()
    }

    private fun createUnsafeOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )
        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, SecureRandom())
        }
        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "VLC/3.0.21 LibVLC/3.0.21")
                    .header("Accept", "*/*")
                    .build()
                chain.proceed(req)
            }
            .addNetworkInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "VLC/3.0.21 LibVLC/3.0.21")
                    .header("Accept", "*/*")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    private suspend fun fetchJsonFast(rawUrl: String): String? = coroutineScope {
        val encodedRaw = try {
            java.net.URLEncoder.encode(rawUrl, "UTF-8")
        } catch (_: Exception) {
            rawUrl
        }
        val endpoints = listOf(
            rawUrl,
            "https://tv-dinner-proxy.onrender.com/?url=$encodedRaw"
        )

        val channel = kotlinx.coroutines.channels.Channel<String?>(endpoints.size)
        val jobs = endpoints.map { url ->
            launch(Dispatchers.IO) {
                try {
                    val req = Request.Builder().url(url).build()
                    val resp = okHttpClient.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrBlank() && (body.trim().startsWith("[") || body.trim().startsWith("{"))) {
                            channel.trySend(body)
                            return@launch
                        }
                    }
                } catch (_: Exception) {}
                channel.trySend(null)
            }
        }

        var result: String? = null
        var received = 0
        while (received < endpoints.size) {
            val res = channel.receive()
            received++
            if (res != null) {
                result = res
                break
            }
        }
        jobs.forEach { job -> job.cancel() }
        result
    }

    suspend fun getLiveCategories(portalUrl: String, user: String, pswd: String): List<LiveCategory> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$portalUrl/player_api.php?username=$user&password=$pswd&action=get_live_categories"
                val json = fetchJsonFast(url) ?: return@withContext emptyList()
                val type = object : TypeToken<List<LiveCategory>>() {}.type
                gson.fromJson<List<LiveCategory>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e(tag, "getLiveCategories error: ${e.message}")
                emptyList()
            }
        }

    suspend fun getLiveStreams(
        portalUrl: String,
        user: String,
        pswd: String,
        categoryId: String? = null
    ): List<Channel> = withContext(Dispatchers.IO) {
        try {
            var url = "$portalUrl/player_api.php?username=$user&password=$pswd&action=get_live_streams"
            if (!categoryId.isNullOrBlank() && categoryId != "all") {
                url += "&category_id=$categoryId"
            }
            val json = fetchJsonFast(url) ?: return@withContext emptyList()
            val type = object : TypeToken<List<Channel>>() {}.type
            val list = gson.fromJson<List<Channel>>(json, type) ?: emptyList()
            list.filter { it.streamId > 0 && it.name.isNotBlank() }
        } catch (e: Exception) {
            Log.e(tag, "getLiveStreams error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getVodCategories(portalUrl: String, user: String, pswd: String): List<MovieCategory> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$portalUrl/player_api.php?username=$user&password=$pswd&action=get_vod_categories"
                val json = fetchJsonFast(url) ?: return@withContext emptyList()
                val type = object : TypeToken<List<MovieCategory>>() {}.type
                gson.fromJson<List<MovieCategory>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e(tag, "getVodCategories error: ${e.message}")
                emptyList()
            }
        }

    suspend fun getVodStreams(
        portalUrl: String,
        user: String,
        pswd: String,
        categoryId: String? = null
    ): List<Movie> = withContext(Dispatchers.IO) {
        try {
            var url = "$portalUrl/player_api.php?username=$user&password=$pswd&action=get_vod_streams"
            if (!categoryId.isNullOrBlank() && categoryId != "all") {
                url += "&category_id=$categoryId"
            }
            val json = fetchJsonFast(url) ?: return@withContext emptyList()
            val type = object : TypeToken<List<Movie>>() {}.type
            val list = gson.fromJson<List<Movie>>(json, type) ?: emptyList()
            list.filter { it.streamId > 0 && it.displayTitle.isNotBlank() }
        } catch (e: Exception) {
            Log.e(tag, "getVodStreams error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getSeriesCategories(portalUrl: String, user: String, pswd: String): List<SeriesCategory> =
        withContext(Dispatchers.IO) {
            try {
                val url = "$portalUrl/player_api.php?username=$user&password=$pswd&action=get_series_categories"
                val json = fetchJsonFast(url) ?: return@withContext emptyList()
                val type = object : TypeToken<List<SeriesCategory>>() {}.type
                gson.fromJson<List<SeriesCategory>>(json, type) ?: emptyList()
            } catch (e: Exception) {
                Log.e(tag, "getSeriesCategories error: ${e.message}")
                emptyList()
            }
        }

    suspend fun getSeries(
        portalUrl: String,
        user: String,
        pswd: String,
        categoryId: String? = null
    ): List<Series> = withContext(Dispatchers.IO) {
        try {
            var url = "$portalUrl/player_api.php?username=$user&password=$pswd&action=get_series"
            if (!categoryId.isNullOrBlank() && categoryId != "all") {
                url += "&category_id=$categoryId"
            }
            val json = fetchJsonFast(url) ?: return@withContext emptyList()
            val type = object : TypeToken<List<Series>>() {}.type
            val list = gson.fromJson<List<Series>>(json, type) ?: emptyList()
            list.filter { it.seriesId > 0 && it.displayTitle.isNotBlank() }
        } catch (e: Exception) {
            Log.e(tag, "getSeries error: ${e.message}")
            emptyList()
        }
    }

    suspend fun getSeriesInfo(
        portalUrl: String,
        user: String,
        pswd: String,
        seriesId: Int
    ): SeriesInfoResponse? = withContext(Dispatchers.IO) {
        try {
            val url = "$portalUrl/player_api.php?username=$user&password=$pswd&action=get_series_info&series_id=$seriesId"
            val json = fetchJsonFast(url) ?: return@withContext null
            gson.fromJson(json, SeriesInfoResponse::class.java)
        } catch (e: Exception) {
            Log.e(tag, "getSeriesInfo error: ${e.message}")
            null
        }
    }

    suspend fun fetchCleanIptvPreset(m3uUrl: String): List<Channel> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(m3uUrl).build()
            val resp = okHttpClient.newCall(req).execute()
            val content = resp.body?.string() ?: return@withContext emptyList()
            parseM3uToChannels(content)
        } catch (e: Exception) {
            Log.e(tag, "fetchCleanIptvPreset error: ${e.message}")
            emptyList()
        }
    }

    private fun parseM3uToChannels(m3uContent: String): List<Channel> {
        val lines = m3uContent.lines()
        val channels = mutableListOf<Channel>()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = "General"
        var currentId = 1000

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:")) {
                val nameMatch = Regex(""","([^"]*)$""").find(trimmed) ?: Regex(""",(.*)$""").find(trimmed)
                currentName = nameMatch?.groupValues?.get(1)?.trim() ?: "Channel $currentId"

                val logoMatch = Regex("""tvg-logo="([^"]+)"""").find(trimmed)
                currentLogo = logoMatch?.groupValues?.get(1) ?: ""

                val groupMatch = Regex("""group-title="([^"]+)"""").find(trimmed)
                currentGroup = groupMatch?.groupValues?.get(1) ?: "General"
            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                channels.add(
                    Channel(
                        num = channels.size + 1,
                        name = currentName.ifBlank { "Channel ${channels.size + 1}" },
                        streamId = currentId++,
                        streamIcon = currentLogo.ifBlank { null },
                        categoryId = currentGroup,
                        directStreamUrl = trimmed
                    )
                )
                currentName = ""
                currentLogo = ""
            }
        }
        return channels
    }

    suspend fun getShortEpg(
        portalUrl: String,
        user: String,
        pswd: String,
        streamId: Int,
        limit: Int = 10
    ): ShortEpgResponse? = withContext(Dispatchers.IO) {
        try {
            val url = "$portalUrl/player_api.php?username=$user&password=$pswd&action=get_short_epg&stream_id=$streamId&limit=$limit"
            val json = fetchJsonFast(url) ?: return@withContext null
            gson.fromJson(json, ShortEpgResponse::class.java)
        } catch (e: Exception) {
            Log.e(tag, "getShortEpg error: ${e.message}")
            null
        }
    }

    fun buildLiveStreamUrl(portalUrl: String, user: String, pswd: String, streamId: Int, ext: String = "ts"): String {
        val cleanExt = ext.removePrefix(".").ifBlank { "ts" }
        val rawUrl = "$portalUrl/live/$user/$pswd/$streamId.$cleanExt"
        val encoded = try { java.net.URLEncoder.encode(rawUrl, "UTF-8") } catch (_: Exception) { rawUrl }
        return "https://tv-dinner-proxy.onrender.com/?url=$encoded"
    }

    fun buildRawLiveStreamUrl(portalUrl: String, user: String, pswd: String, streamId: Int, ext: String = "ts"): String {
        val cleanExt = ext.removePrefix(".").ifBlank { "ts" }
        return "$portalUrl/live/$user/$pswd/$streamId.$cleanExt"
    }

    fun buildMovieStreamUrl(portalUrl: String, user: String, pswd: String, streamId: Int, ext: String = "mp4"): String {
        val cleanExt = ext.removePrefix(".").ifBlank { "mp4" }
        val rawUrl = "$portalUrl/movie/$user/$pswd/$streamId.$cleanExt"
        val encoded = try { java.net.URLEncoder.encode(rawUrl, "UTF-8") } catch (_: Exception) { rawUrl }
        return "https://tv-dinner-proxy.onrender.com/?url=$encoded"
    }

    fun buildSeriesStreamUrl(portalUrl: String, user: String, pswd: String, streamId: Int, ext: String = "mp4"): String {
        val cleanExt = ext.removePrefix(".").ifBlank { "mp4" }
        val rawUrl = "$portalUrl/series/$user/$pswd/$streamId.$cleanExt"
        val encoded = try { java.net.URLEncoder.encode(rawUrl, "UTF-8") } catch (_: Exception) { rawUrl }
        return "https://tv-dinner-proxy.onrender.com/?url=$encoded"
    }
}
