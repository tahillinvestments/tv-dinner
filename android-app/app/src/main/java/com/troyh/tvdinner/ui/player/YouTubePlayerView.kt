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

class YouTubeBridgeInterface(private val onEndedProvider: () -> (() -> Unit)?) {
    @android.webkit.JavascriptInterface
    fun onVideoEnded() {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            onEndedProvider()?.invoke()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerView(
    videoId: String,
    title: String,
    onBack: () -> Unit,
    onNextVideo: (() -> Unit)? = null,
    nextVideoTitle: String? = null,
    onPreviousVideo: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val currentOnNextVideo by rememberUpdatedState(onNextVideo)
    val currentOnPreviousVideo by rememberUpdatedState(onPreviousVideo)
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

    LaunchedEffect(videoId) {
        webViewInstance?.evaluateJavascript(
            """
            if (window.ytPlayer && typeof window.ytPlayer.loadVideoById === 'function') {
                window.ytPlayer.loadVideoById('$videoId');
            } else {
                window.pendingVideoId = '$videoId';
            }
            """.trimIndent(),
            null
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            if (YouTubeRemoteBridge.activeWebView == webViewInstance) {
                YouTubeRemoteBridge.activeWebView = null
            }
            webViewInstance?.let { wv ->
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
            }
            webViewInstance = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
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
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = true
                        allowContentAccess = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun getDefaultVideoPoster(): android.graphics.Bitmap? {
                            return android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
                        }
                    }
                    webViewClient = WebViewClient()
                    setBackgroundColor(android.graphics.Color.BLACK)

                    addJavascriptInterface(
                        YouTubeBridgeInterface { currentOnNextVideo },
                        "AndroidBridge"
                    )

                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                            <style>
                                html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; background: #000; }
                                #player { width: 100%; height: 100%; position: absolute; top:0; left:0; border: none; }
                            </style>
                        </head>
                        <body>
                            <div id="player"></div>
                            <script src="https://www.youtube.com/iframe_api"></script>
                            <script>
                                var ytPlayer;
                                function onYouTubeIframeAPIReady() {
                                    var vId = window.pendingVideoId || '$videoId';
                                    ytPlayer = new YT.Player('player', {
                                        videoId: vId,
                                        playerVars: {
                                            'autoplay': 1,
                                            'controls': 1,
                                            'modestbranding': 1,
                                            'rel': 0,
                                            'fs': 1,
                                            'playsinline': 1,
                                            'enablejsapi': 1,
                                            'origin': 'https://www.youtube-nocookie.com',
                                            'iv_load_policy': 3
                                        },
                                        events: {
                                            'onReady': function(e) {
                                                window.ytPlayer = e.target;
                                                if (window.pendingVideoId) {
                                                    e.target.loadVideoById(window.pendingVideoId);
                                                    window.pendingVideoId = null;
                                                } else {
                                                    try {
                                                        e.target.playVideo();
                                                    } catch(err) {}
                                                }
                                            },
                                            'onError': function(e) {
                                                console.log('YT Error:', e.data);
                                            },
                                            'onStateChange': function(e) {
                                                if (e.data === 0) {
                                                    if (window.AndroidBridge && window.AndroidBridge.onVideoEnded) {
                                                        window.AndroidBridge.onVideoEnded();
                                                    }
                                                }
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
            update = { wv ->
                YouTubeRemoteBridge.activeWebView = wv
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
                        .background(Color.Black.copy(alpha = 0.75f))
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

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (!nextVideoTitle.isNullOrBlank()) {
                            Text(
                                text = "Next up: $nextVideoTitle",
                                color = CinemaAccent,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Center Remote Seek Controls & Next Button
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
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

                    if (onPreviousVideo != null) {
                        TvFocusableCard(
                            onClick = {
                                onPreviousVideo()
                            },
                            shape = RoundedCornerShape(24.dp),
                            backgroundColor = CinemaSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous Video", tint = Color.White, modifier = Modifier.size(22.dp))
                                Text(
                                    text = "Previous (▲)",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (onNextVideo != null) {
                        TvFocusableCard(
                            onClick = {
                                onNextVideo()
                            },
                            shape = RoundedCornerShape(24.dp),
                            backgroundColor = CinemaAccent,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxHeight().padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next Video", tint = Color.Black, modifier = Modifier.size(22.dp))
                                Text(
                                    text = if (!nextVideoTitle.isNullOrBlank()) "Next Video" else "Skip",
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
