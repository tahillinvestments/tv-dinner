package com.troyh.tvdinner.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.troyh.tvdinner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

data class UpdateManifest(
    @SerializedName("versionCode") val versionCode: Int = 0,
    @SerializedName("versionName") val versionName: String = "",
    @SerializedName("title") val title: String? = null,
    @SerializedName("releaseNotes") val releaseNotes: String = "",
    @SerializedName("apkUrl") val apkUrl: String = "",
    @SerializedName("mandatory") val mandatory: Boolean = false,
    @SerializedName("publishedAt") val publishedAt: String? = null
)

class UpdateManager(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    companion object {
        private const val TAG = "UpdateManager"
        // Primary update manifest URL (GitHub Releases or local / production endpoint)
        const val DEFAULT_MANIFEST_URL = "https://raw.githubusercontent.com/tahillinvestments/tv-dinner/main/public/version.json"
    }

    var lastCheckError: String? = null
        private set

    suspend fun checkForUpdates(manifestUrl: String = DEFAULT_MANIFEST_URL): UpdateManifest? = withContext(Dispatchers.IO) {
        lastCheckError = null
        try {
            val request = Request.Builder()
                .url(manifestUrl)
                .header("Cache-Control", "no-cache")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = "Server error (HTTP ${response.code})"
                    Log.w(TAG, msg)
                    lastCheckError = msg
                    return@withContext null
                }

                val body = response.body?.string() ?: run {
                    lastCheckError = "Empty manifest response"
                    return@withContext null
                }
                val manifest = gson.fromJson(body, UpdateManifest::class.java)

                val currentCode = BuildConfig.VERSION_CODE
                Log.d(TAG, "Current versionCode: $currentCode, Remote versionCode: ${manifest.versionCode}")

                if (manifest.versionCode > currentCode) {
                    return@withContext manifest
                }
            }
        } catch (e: Exception) {
            val msg = "Connection error: ${e.message}"
            Log.e(TAG, msg, e)
            lastCheckError = msg
        }
        return@withContext null
    }

    fun getCachedApk(versionCode: Int): File? {
        val targetName = "tv-dinner-v$versionCode.apk"
        val cacheFile = File(File(context.cacheDir, "updates"), targetName)
        if (cacheFile.exists() && cacheFile.length() > 5_000_000L) {
            return cacheFile
        }
        val extDir = context.getExternalFilesDir("updates")
        if (extDir != null) {
            val extFile = File(extDir, targetName)
            if (extFile.exists() && extFile.length() > 5_000_000L) {
                return extFile
            }
        }
        return null
    }

    suspend fun downloadApk(
        apkUrl: String,
        versionCode: Int = 0,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val targetFileName = if (versionCode > 0) "tv-dinner-v$versionCode.apk" else "tv-dinner-update.apk"
        try {
            val request = Request.Builder().url(apkUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed with HTTP ${response.code}")
                    return@withContext null
                }

                val body = response.body ?: return@withContext null
                val totalBytes = body.contentLength()

                val updatesDir = context.getExternalFilesDir("updates") ?: File(context.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()

                val destinationFile = File(updatesDir, targetFileName)
                if (destinationFile.exists()) destinationFile.delete()

                var bytesRead = 0L
                val buffer = ByteArray(8192)

                body.byteStream().use { input: InputStream ->
                    FileOutputStream(destinationFile).use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesRead += read
                            if (totalBytes > 0) {
                                val progress = (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                withContext(Dispatchers.Main) {
                                    onProgress(progress)
                                }
                            }
                        }
                        output.flush()
                    }
                }

                withContext(Dispatchers.Main) {
                    onProgress(1f)
                }
                return@withContext destinationFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download error: ${e.message}", e)
            return@withContext null
        }
    }

    fun installApk(apkFile: File): Boolean {
        return try {
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file not found: ${apkFile.absolutePath}")
                return false
            }

            // Android 8.0+ Unknown App Sources Permission Check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Log.w(TAG, "REQUEST_INSTALL_PACKAGES permission not granted. Launching settings...")
                    val permissionIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(permissionIntent)
                    return false
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer: ${e.message}", e)
            false
        }
    }
}
