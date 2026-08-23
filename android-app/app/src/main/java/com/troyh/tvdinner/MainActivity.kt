package com.troyh.tvdinner

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.troyh.tvdinner.data.network.XtreamApiClient
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
    }

    private lateinit var authRepository: AuthRepository
    private lateinit var xtreamApiClient: XtreamApiClient
    private lateinit var podcastService: YouTubePodcastService
    private lateinit var catalogManager: CatalogManager
    private lateinit var playerManager: ExoPlayerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen awake to avoid TV screen saver during video playback and app use
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        authRepository = AuthRepository(this)
        xtreamApiClient = XtreamApiClient()
        podcastService = YouTubePodcastService(xtreamApiClient.okHttpClient)
        catalogManager = CatalogManager(authRepository, xtreamApiClient, podcastService)
        playerManager = ExoPlayerManager(this, xtreamApiClient).apply {
            authRepo = authRepository
        }

        // Instant Proxy Pre-Warming & 3-Minute Keep-Alive Heartbeat
        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val req = Request.Builder().url("https://tv-dinner-proxy.onrender.com/health").build()
                    xtreamApiClient.okHttpClient.newCall(req).execute().close()
                } catch (_: Exception) {}
                delay(3 * 60 * 1000L)
            }
        }

        setContent {
            TVDinnerTheme {
                var isActivated by remember { mutableStateOf(authRepository.isActivated()) }

                if (!isActivated) {
                    ActivationScreen(
                        authRepo = authRepository,
                        onActivated = {
                            isActivated = true
                        }
                    )
                } else {
                    MainAppScreen(
                        authRepo = authRepository,
                        apiClient = xtreamApiClient,
                        catalogManager = catalogManager,
                        playerManager = playerManager,
                        onSignOut = {
                            playerManager.stop()
                            catalogManager.clearAllCaches()
                            isActivated = false
                        }
                    )
                }
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
                    } else if (isVODFullscreenActive) {
                        playerManager.togglePlayPause()
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
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    if (isYouTubeActive) {
                        YouTubeRemoteBridge.seekRewind()
                    } else {
                        playerManager.seekRewind10s()
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        playerManager.release()
    }
}
