package com.troyh.tvdinner.ui.player

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.troyh.tvdinner.ui.components.TvFocusableCard
import com.troyh.tvdinner.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object YouTubeRemoteBridge {
    var activeWebView: WebView? = null

    private val _scrubBadge = MutableStateFlow<String?>(null)
    val scrubBadge: StateFlow<String?> = _scrubBadge.asStateFlow()

    private var lastSeekTime = 0L
    private var seekMagnitudeSec = 15

    fun togglePlayPause() {
        activeWebView?.evaluateJavascript(
            "if (window.ytPlayer) { if (window.ytPlayer.getPlayerState() === 1) window.ytPlayer.pauseVideo(); else window.ytPlayer.playVideo(); }",
            null
        )
    }

    fun play() {
        activeWebView?.evaluateJavascript("if (window.ytPlayer) window.ytPlayer.playVideo();", null)
    }

    fun pause() {
        activeWebView?.evaluateJavascript("if (window.ytPlayer) window.ytPlayer.pauseVideo();", null)
    }

    fun seekRewind() {
        val now = System.currentTimeMillis()
        seekMagnitudeSec = if (now - lastSeekTime < 1400) {
            when (seekMagnitudeSec) {
                15 -> 30
                30 -> 60
                60 -> 120
                120 -> 300
                300 -> 600
                600 -> 900
                900 -> 1800
                else -> 1800
            }
        } else {
            15
        }
        lastSeekTime = now
        val label = if (seekMagnitudeSec >= 60) "-${seekMagnitudeSec / 60}m" else "-${seekMagnitudeSec}s"
        _scrubBadge.value = label

        activeWebView?.evaluateJavascript(
            "if (window.ytPlayer) window.ytPlayer.seekTo(Math.max(0, window.ytPlayer.getCurrentTime() - $seekMagnitudeSec), true);",
            null
        )
    }

    fun seekForward() {
        val now = System.currentTimeMillis()
        seekMagnitudeSec = if (now - lastSeekTime < 1400) {
            when (seekMagnitudeSec) {
                15 -> 30
                30 -> 60
                60 -> 120
                120 -> 300
                300 -> 600
                600 -> 900
                900 -> 1800
                else -> 1800
            }
        } else {
            15
        }
        lastSeekTime = now
        val label = if (seekMagnitudeSec >= 60) "+${seekMagnitudeSec / 60}m" else "+${seekMagnitudeSec}s"
        _scrubBadge.value = label

        activeWebView?.evaluateJavascript(
            "if (window.ytPlayer) window.ytPlayer.seekTo(window.ytPlayer.getCurrentTime() + $seekMagnitudeSec, true);",
            null
        )
    }

    fun clearScrubBadge() {
        _scrubBadge.value = null
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerView(
    videoId: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scrubBadge by YouTubeRemoteBridge.scrubBadge.collectAsState()

    // Auto-hide controls
    LaunchedEffect(showControls, lastInteractionTime) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    // Auto-clear scrub badge
    LaunchedEffect(scrubBadge) {
        if (scrubBadge != null) {
            showControls = true
            delay(1500)
            YouTubeRemoteBridge.clearScrubBadge()
        }
    }

    DisposableEffect(videoId) {
        onDispose {
            YouTubeRemoteBridge.activeWebView = null
            webViewInstance?.let { wv ->
                wv.loadUrl("about:blank")
                wv.stopLoading()
                wv.destroy()
            }
            webViewInstance = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp) {
                    val code = keyEvent.nativeKeyEvent.keyCode
                    if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        YouTubeRemoteBridge.togglePlayPause()
                        showControls = true
                        lastInteractionTime = System.currentTimeMillis()
                        return@onKeyEvent true
                    } else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_MEDIA_REWIND) {
                        YouTubeRemoteBridge.seekRewind()
                        showControls = true
                        lastInteractionTime = System.currentTimeMillis()
                        return@onKeyEvent true
                    } else if (code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                        YouTubeRemoteBridge.seekForward()
                        showControls = true
                        lastInteractionTime = System.currentTimeMillis()
                        return@onKeyEvent true
                    }
                }
                false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
                lastInteractionTime = System.currentTimeMillis()
            }
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // Hardware Acceleration & Optimal Streaming Settings
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    setBackgroundColor(android.graphics.Color.BLACK)

                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                            <style>
                                * { margin: 0; padding: 0; box-sizing: border-box; background-color: #000; }
                                html, body { width: 100%; height: 100%; overflow: hidden; background: #000; }
                                #player { width: 100%; height: 100%; position: absolute; top:0; left:0; border: none; }
                            </style>
                        </head>
                        <body>
                            <div id="player"></div>
                            <script src="https://www.youtube.com/iframe_api"></script>
                            <script>
                                var ytPlayer;
                                function onYouTubeIframeAPIReady() {
                                    ytPlayer = new YT.Player('player', {
                                        videoId: '$videoId',
                                        playerVars: {
                                            'autoplay': 1,
                                            'controls': 1,
                                            'modestbranding': 1,
                                            'rel': 0,
                                            'fs': 1,
                                            'playsinline': 1,
                                            'enablejsapi': 1,
                                            'iv_load_policy': 3
                                        },
                                        events: {
                                            'onReady': function(e) {
                                                window.ytPlayer = e.target;
                                                e.target.playVideo();
                                            }
                                        }
                                    });
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()

                    loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                    webViewInstance = this
                    YouTubeRemoteBridge.activeWebView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Scrub Acceleration Badge
        if (scrubBadge != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaAccent),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (scrubBadge!!.startsWith("+")) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = CinemaAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Seeking: $scrubBadge",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Navigation Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TvFocusableCard(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        backgroundColor = CinemaSurfaceVariant.copy(alpha = 0.8f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }

                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Center Remote Seek Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvFocusableCard(
                        onClick = {
                            YouTubeRemoteBridge.seekRewind()
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        backgroundColor = CinemaSurfaceVariant.copy(alpha = 0.8f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.FastRewind, contentDescription = "Rewind", tint = Color.White)
                        }
                    }

                    TvFocusableCard(
                        onClick = {
                            YouTubeRemoteBridge.togglePlayPause()
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        backgroundColor = CinemaPrimary
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }

                    TvFocusableCard(
                        onClick = {
                            YouTubeRemoteBridge.seekForward()
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        backgroundColor = CinemaSurfaceVariant.copy(alpha = 0.8f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Default.FastForward, contentDescription = "Forward", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}
