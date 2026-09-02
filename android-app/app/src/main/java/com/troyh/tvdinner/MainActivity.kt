package com.troyh.tvdinner

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.troyh.tvdinner.data.network.XtreamApiClient
import com.troyh.tvdinner.data.network.YouTubeMusicService
import com.troyh.tvdinner.data.network.YouTubePodcastService
import com.troyh.tvdinner.data.repository.AuthRepository
import com.troyh.tvdinner.data.repository.CatalogManager
import com.troyh.tvdinner.player.ExoPlayerManager
import com.troyh.tvdinner.ui.player.YouTubeRemoteBridge
import com.troyh.tvdinner.ui.screens.ActivationScreen
import com.troyh.tvdinner.ui.screens.MainAppScreen
import com.troyh.tvdinner.ui.theme.TVDinnerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request

class MainActivity : ComponentActivity() {
    companion object {
        var isVODFullscreenActive = false
        var isLiveFullscreenActive = false
        var onNextEpisodeCallback: (() -> Unit)? = null
        var onNextYouTubeCallback: (() -> Unit)? = null
        var onPreviousYouTubeCallback: (() -> Unit)? = null
        var isNetworkAvailable by mutableStateOf(true)
    }

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private lateinit var authRepository: AuthRepository
    private lateinit var xtreamApiClient: XtreamApiClient
    private lateinit var podcastService: YouTubePodcastService
    private lateinit var musicService: YouTubeMusicService
    private lateinit var catalogManager: CatalogManager
    private lateinit var playerManager: ExoPlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen awake to avoid TV screen saver during video playback and app use
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        authRepository = AuthRepository(this)
        xtreamApiClient = XtreamApiClient()

        // Configure high-performance Coil ImageLoader with SSL bypass, custom User-Agent, and disk/memory cache
        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(xtreamApiClient.okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)

        // Connectivity Monitoring & Auto-Refresh from inside app
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val networkReq = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                isNetworkAvailable = false
                runOnUiThread {
                    Toast.makeText(applicationContext, "Network Connection Lost", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAvailable(network: Network) {
                val wasDisconnected = !isNetworkAvailable
                isNetworkAvailable = true
                if (wasDisconnected) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Connection Restored — Auto-Refreshing", Toast.LENGTH_SHORT).show()
                        try {
                            xtreamApiClient.okHttpClient.connectionPool.evictAll()
                        } catch (_: Exception) {}
                        if (isVODFullscreenActive) {
                            playerManager.recoverVodStream()
                        } else if (isLiveFullscreenActive) {
                            playerManager.recoverLiveStream()
                        }
                    }
                }
            }
        }
        try {
            connectivityManager?.registerNetworkCallback(networkReq, networkCallback!!)
        } catch (_: Exception) {}

        podcastService = YouTubePodcastService(xtreamApiClient.okHttpClient)
        musicService = YouTubeMusicService(xtreamApiClient.okHttpClient)
        catalogManager = CatalogManager(authRepository, xtreamApiClient, podcastService, musicService)
        playerManager = ExoPlayerManager(this, xtreamApiClient).apply {
            authRepo = authRepository
        }

        setContent {
            TVDinnerTheme {
                MainAppScreen(
                    authRepo = authRepository,
                    apiClient = xtreamApiClient,
                    catalogManager = catalogManager,
                    playerManager = playerManager,
                    onSignOut = {
                        playerManager.stop()
                        catalogManager.clearAllCaches()
                    }
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isYouTubeActive = YouTubeRemoteBridge.activeWebView != null

            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (isYouTubeActive) {
                        YouTubeRemoteBridge.togglePlayPause()
                        return true
                    }
                    if (isVODFullscreenActive || isLiveFullscreenActive) {
                        playerManager.togglePlayPause()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (isYouTubeActive) {
                        onPreviousYouTubeCallback?.invoke()
                        return true
                    } else if (isVODFullscreenActive) {
                        playerManager.cycleAspectRatio()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (isYouTubeActive) {
                        onNextYouTubeCallback?.invoke()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (isYouTubeActive) {
                        YouTubeRemoteBridge.seekRewind()
                        return true
                    } else if (isVODFullscreenActive) {
                        playerManager.seekRewind10s()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isYouTubeActive) {
                        YouTubeRemoteBridge.seekForward()
                        return true
                    } else if (isVODFullscreenActive) {
                        playerManager.seekForward10s()
                        return true
                    }
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (isYouTubeActive) {
                        YouTubeRemoteBridge.togglePlayPause()
                    } else {
                        playerManager.togglePlayPause()
                    }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    if (isYouTubeActive) {
                        YouTubeRemoteBridge.play()
                    } else {
                        playerManager.play()
                    }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    if (isYouTubeActive) {
                        YouTubeRemoteBridge.pause()
                    } else {
                        playerManager.pause()
                    }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    if (isYouTubeActive) {
                        YouTubeRemoteBridge.seekForward()
                    } else {
                        playerManager.seekForward10s()
                    }
                    return true
                }
                KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_S -> {
                    if (isVODFullscreenActive || isLiveFullscreenActive) {
                        playerManager.toggleClosedCaptions()
                        return true
                    }
                }
                KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, KeyEvent.KEYCODE_A -> {
                    if (isVODFullscreenActive || isLiveFullscreenActive) {
                        playerManager.cycleAudioTrack()
                        return true
                    }
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    if (isYouTubeActive) {
                        YouTubeRemoteBridge.seekRewind()
                    } else {
                        playerManager.seekRewind10s()
                    }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_P, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (isYouTubeActive) {
                        onPreviousYouTubeCallback?.invoke()
                        return true
                    }
                }
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_FORWARD -> {
                    if (isYouTubeActive) {
                        onNextYouTubeCallback?.invoke()
                        return true
                    } else if (isVODFullscreenActive) {
                        onNextEpisodeCallback?.invoke()
                        return true
                    }
                }
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_INFO,
                KeyEvent.KEYCODE_PROG_YELLOW,
                KeyEvent.KEYCODE_PROG_BLUE,
                KeyEvent.KEYCODE_WINDOW,
                228 /* KEYCODE_ASPECT_RATIO */ -> {
                    if (isVODFullscreenActive || isLiveFullscreenActive) {
                        playerManager.cycleAspectRatio()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        try {
            playerManager.release()
        } catch (_: Exception) {}
    }
}
