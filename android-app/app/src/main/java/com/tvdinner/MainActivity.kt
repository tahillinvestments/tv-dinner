package com.tvdinner

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
import com.tvdinner.data.network.XtreamApiClient
import com.tvdinner.data.network.YouTubeMusicService
import com.tvdinner.data.network.YouTubePodcastService
import com.tvdinner.data.repository.AuthRepository
import com.tvdinner.data.repository.CatalogManager
import com.tvdinner.player.ExoPlayerManager
import com.tvdinner.ui.player.YouTubeRemoteBridge
import com.tvdinner.ui.screens.MainAppScreen
import com.tvdinner.ui.theme.TVDinnerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request

class MainActivity : ComponentActivity() {
    companion object {
        var isVODFullscreenActive by mutableStateOf(false)
        var isLiveFullscreenActive by mutableStateOf(false)
        var isYouTubeFullscreenActive by mutableStateOf(false)
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
    private var fullscreenCenterKeyDown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen awake to avoid TV screen saver during video playback and app use
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        authRepository = AuthRepository(this)
        xtreamApiClient = XtreamApiClient()

        // Configure high-performance Coil ImageLoader with robust timeouts, parallel host dispatcher, SSL bypass, and disk/memory cache
        val imageDispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 24
        }
        val imageOkHttpClient = xtreamApiClient.okHttpClient.newBuilder()
            .dispatcher(imageDispatcher)
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(okhttp3.ConnectionPool(64, 5, java.util.concurrent.TimeUnit.MINUTES))
            .build()

        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(imageOkHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200L * 1024L * 1024L)
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
            val currentTheme by authRepository.appThemeState.collectAsState()
            TVDinnerTheme(themeKey = currentTheme) {
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
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_SELECT -> {
                    if (isYouTubeFullscreenActive) {
                        // YouTube: WebView steals Compose focus, so dispatchKeyEvent must handle it
                        fullscreenCenterKeyDown = true
                        return true
                    } else if (isLiveFullscreenActive || isVODFullscreenActive) {
                        // Native: Compose NativePlayerView's onKeyEvent handles play/pause.
                        // Just consume ACTION_DOWN to prevent double-propagation, but don't act on it.
                        fullscreenCenterKeyDown = true
                        return super.dispatchKeyEvent(event) // let Compose get this
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (isYouTubeFullscreenActive) {
                        onPreviousYouTubeCallback?.invoke()
                        return true
                    } else if (isVODFullscreenActive) {
                        playerManager.cycleAspectRatio()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (isYouTubeFullscreenActive) {
                        onNextYouTubeCallback?.invoke()
                        return true
                    } else if (isVODFullscreenActive) {
                        playerManager.toggleClosedCaptions()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (isYouTubeFullscreenActive) {
                        YouTubeRemoteBridge.seekRewind()
                        return true
                    } else if (isVODFullscreenActive) {
                        playerManager.seekRewind10s()
                        return true
                    } else if (isLiveFullscreenActive) {
                        playerManager.rewindLive()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isYouTubeFullscreenActive) {
                        YouTubeRemoteBridge.seekForward()
                        return true
                    } else if (isVODFullscreenActive) {
                        playerManager.seekForward10s()
                        return true
                    } else if (isLiveFullscreenActive) {
                        playerManager.forwardLive()
                        return true
                    }
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (isYouTubeFullscreenActive || (isYouTubeActive && !playerManager.isPlaying.value)) {
                        YouTubeRemoteBridge.togglePlayPause()
                    } else {
                        playerManager.togglePlayPause()
                    }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    if (isYouTubeFullscreenActive || (isYouTubeActive && !playerManager.isPlaying.value)) {
                        YouTubeRemoteBridge.play()
                    } else {
                        playerManager.play()
                    }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    if (isYouTubeFullscreenActive || (isYouTubeActive && !playerManager.isPlaying.value)) {
                        YouTubeRemoteBridge.pause()
                    } else {
                        playerManager.pause()
                    }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    if (isYouTubeFullscreenActive || (isYouTubeActive && !playerManager.isPlaying.value)) {
                        YouTubeRemoteBridge.seekForward()
                    } else if (isLiveFullscreenActive) {
                        playerManager.forwardLive()
                    } else {
                        playerManager.seekForward10s()
                    }
                    return true
                }
                KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_S -> {
                    if (isYouTubeFullscreenActive) {
                        YouTubeRemoteBridge.toggleClosedCaptions()
                        return true
                    } else if (isVODFullscreenActive || isLiveFullscreenActive) {
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
                    if (isYouTubeFullscreenActive || (isYouTubeActive && !playerManager.isPlaying.value)) {
                        YouTubeRemoteBridge.seekRewind()
                    } else if (isLiveFullscreenActive) {
                        playerManager.rewindLive()
                    } else {
                        playerManager.seekRewind10s()
                    }
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_P -> {
                    if (isYouTubeFullscreenActive) {
                        onPreviousYouTubeCallback?.invoke()
                        return true
                    }
                }
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_FORWARD -> {
                    if (isYouTubeFullscreenActive) {
                        onNextYouTubeCallback?.invoke()
                        return true
                    } else if (isVODFullscreenActive) {
                        onNextEpisodeCallback?.invoke()
                        return true
                    }
                }
                KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (isYouTubeFullscreenActive) {
                        onNextYouTubeCallback?.invoke()
                        return true
                    } else if (isVODFullscreenActive) {
                        onNextEpisodeCallback?.invoke()
                        return true
                    }
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (isYouTubeFullscreenActive) {
                        onPreviousYouTubeCallback?.invoke()
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
        } else if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_SELECT -> {
                    if (fullscreenCenterKeyDown) {
                        fullscreenCenterKeyDown = false
                        if (isYouTubeFullscreenActive) {
                            // YouTube: WebView blocks Compose, so we toggle play/pause here
                            YouTubeRemoteBridge.togglePlayPause()
                            return true
                        } else if (isLiveFullscreenActive || isVODFullscreenActive) {
                            // Native: Compose NativePlayerView.onKeyEvent handles this.
                            // Pass through so Compose receives the ACTION_UP and toggles once.
                            return super.dispatchKeyEvent(event)
                        }
                    }
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (isYouTubeFullscreenActive || isVODFullscreenActive) {
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (isYouTubeFullscreenActive || isVODFullscreenActive || isLiveFullscreenActive) {
                        return true
                    }
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_P,
                KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_FORWARD -> {
                    if (isYouTubeFullscreenActive || isVODFullscreenActive) {
                        return true
                    }
                }
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (isYouTubeFullscreenActive || isVODFullscreenActive) {
                        return true
                    }
                }
                KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_S,
                KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, KeyEvent.KEYCODE_A -> {
                    if (isYouTubeFullscreenActive || isVODFullscreenActive || isLiveFullscreenActive) {
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
