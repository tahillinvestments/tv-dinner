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
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.net.InetAddress
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

        // Resilient DNS resolver: queries system DNS first; on residential WiFi ISP timeout/NXDOMAIN falls back to InetAddress
        val resilientDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                try {
                    val systemResult = Dns.SYSTEM.lookup(hostname)
                    if (systemResult.isNotEmpty()) return systemResult
                } catch (_: Exception) {}
                return try {
                    InetAddress.getAllByName(hostname).toList()
                } catch (_: Exception) {
                    Dns.SYSTEM.lookup(hostname)
                }
            }
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .dns(resilientDns)
            .connectionPool(ConnectionPool(16, 5, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_1_1))
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val orig = chain.request()
                val reqBuilder = orig.newBuilder()
                    .header("User-Agent", "VLC/3.0.21 LibVLC/3.0.21")
                    .header("Accept", "*/*")
                chain.proceed(reqBuilder.build())
            }
            .build()
    }

    private val fastDirectClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchJsonFast(rawUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(rawUrl).build()
            val resp = okHttpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank() && (body.trim().startsWith("[") || body.trim().startsWith("{"))) {
                    return@withContext body
                }
            }
            resp.close()
        } catch (e: Exception) {
            Log.w(tag, "Direct API fetch error for $rawUrl: ${e.message}")
        }
        null
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

    /**
     * Build live stream URL. Uses .m3u8 (HLS) format by default for native ExoPlayer
     * local playback on residential WiFi.
     */
    fun buildLiveStreamUrl(portalUrl: String, user: String, pswd: String, streamId: Int, ext: String = "m3u8"): String {
        val cleanExt = ext.removePrefix(".").ifBlank { "m3u8" }
        return "$portalUrl/live/$user/$pswd/$streamId.$cleanExt"
    }

    fun buildRawLiveStreamUrl(portalUrl: String, user: String, pswd: String, streamId: Int, ext: String = "ts"): String {
        val cleanExt = ext.removePrefix(".").ifBlank { "ts" }
        return "$portalUrl/live/$user/$pswd/$streamId.$cleanExt"
    }

    fun buildMovieStreamUrl(portalUrl: String, user: String, pswd: String, streamId: Int, ext: String = "mp4"): String {
        val cleanExt = ext.removePrefix(".").ifBlank { "mp4" }
        return "$portalUrl/movie/$user/$pswd/$streamId.$cleanExt"
    }

    fun buildSeriesStreamUrl(portalUrl: String, user: String, pswd: String, streamId: Int, ext: String = "mp4"): String {
        val cleanExt = ext.removePrefix(".").ifBlank { "mp4" }
        return "$portalUrl/series/$user/$pswd/$streamId.$cleanExt"
    }

    suspend fun testCredentials(portalUrl: String, user: String, pswd: String): AuthResult = withContext(Dispatchers.IO) {
        if (user.isBlank() || pswd.isBlank()) {
            return@withContext AuthResult(false, "Invalid", "Username and Password cannot be empty.")
        }
        try {
            val url = "$portalUrl/player_api.php?username=$user&password=$pswd"
            val json = fetchJsonFast(url)
            if (json.isNullOrBlank()) {
                return@withContext AuthResult(false, "Inactive", "Unable to connect to IPTV server. Check network connection.")
            }
            val jsonObj = try {
                gson.fromJson(json, com.google.gson.JsonObject::class.java)
            } catch (_: Exception) {
                null
            }

            val userInfo = jsonObj?.getAsJsonObject("user_info")
            if (userInfo != null) {
                val auth = try { userInfo.get("auth")?.asInt ?: 1 } catch (_: Exception) { 1 }
                val status = try { userInfo.get("status")?.asString ?: "Active" } catch (_: Exception) { "Active" }
                val expDate = try { userInfo.get("exp_date")?.asString } catch (_: Exception) { null }
                val maxConn = try { userInfo.get("max_connections")?.asString } catch (_: Exception) { null }

                val isActive = auth == 1 && status.equals("Active", ignoreCase = true)
                if (isActive) {
                    return@withContext AuthResult(
                        isValid = true,
                        status = "Active",
                        message = "Active & Verified • Connected to IPTV Server (Max Cons: ${maxConn ?: "1"})",
                        expDate = expDate,
                        maxConnections = maxConn
                    )
                } else {
                    return@withContext AuthResult(
                        isValid = false,
                        status = status,
                        message = "Account Status: $status (Authentication Failed)"
                    )
                }
            }

            // Fallback check: If categories can be retrieved, credentials are active
            val cats = getLiveCategories(portalUrl, user, pswd)
            if (cats.isNotEmpty()) {
                return@withContext AuthResult(
                    isValid = true,
                    status = "Active",
                    message = "Active & Verified • ${cats.size} Categories Available"
                )
            }

            AuthResult(false, "Inactive", "Authentication failed. Inactive or invalid credentials.")
        } catch (e: Exception) {
            Log.e(tag, "testCredentials error: ${e.message}")
            AuthResult(false, "Error", "Connection error: ${e.localizedMessage ?: e.message}")
        }
    }
}

data class AuthResult(
    val isValid: Boolean,
    val status: String,
    val message: String,
    val expDate: String? = null,
    val maxConnections: String? = null
)
