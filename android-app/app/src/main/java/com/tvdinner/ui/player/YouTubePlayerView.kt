package com.tvdinner.ui.player

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.tvdinner.ui.components.TvFocusableCard
import com.tvdinner.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object YouTubeRemoteBridge {
    var activeWebView: WebView? = null
    var activeVideoId: String? = null
    var activeIsPreview: Boolean? = null
    var lastPlaybackSeconds: Int = 0
    var onNextVideoCallback: (() -> Unit)? = null
    var onPreviousVideoCallback: (() -> Unit)? = null

    // Track user playback intent across view transitions (fullscreen <-> preview)
    var isActuallyPlaying = true
    var isTransitioning = false

    fun notifyTransition() {
        isTransitioning = true
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isTransitioning = false
        }, 1000)
    }

    private val _scrubBadge = MutableStateFlow<String?>(null)
    val scrubBadge: StateFlow<String?> = _scrubBadge.asStateFlow()

    private var lastSeekTime = 0L
    private var seekMagnitudeSec = 15

    fun togglePlayPause() {
        isActuallyPlaying = !isActuallyPlaying
        activeWebView?.evaluateJavascript(
            "if (window.ytPlayer) { if (window.ytPlayer.getPlayerState() === 1) window.ytPlayer.pauseVideo(); else window.ytPlayer.playVideo(); }",
            null
        )
    }

    fun play() {
        isActuallyPlaying = true
        activeWebView?.evaluateJavascript("if (window.ytPlayer) window.ytPlayer.playVideo();", null)
    }

    fun pause() {
        isActuallyPlaying = false
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
            "if (window.ytPlayer) { var t = Math.max(0, window.ytPlayer.getCurrentTime() - $seekMagnitudeSec); window.ytPlayer.seekTo(t, true); if (window.AndroidBridge && window.AndroidBridge.onTimeChange) window.AndroidBridge.onTimeChange(t); }",
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
            "if (window.ytPlayer) { var t = window.ytPlayer.getCurrentTime() + $seekMagnitudeSec; window.ytPlayer.seekTo(t, true); if (window.AndroidBridge && window.AndroidBridge.onTimeChange) window.AndroidBridge.onTimeChange(t); }",
            null
        )
    }

    fun clearScrubBadge() {
        _scrubBadge.value = null
    }

    fun toggleClosedCaptions() {
        activeWebView?.evaluateJavascript(
            """
            (function() {
                if (!window.ytPlayer) return;
                try {
                    var current = null;
                    try { current = window.ytPlayer.getOption('captions', 'track'); } catch(e) {}
                    if (current && (current.languageCode || current.displayName)) {
                        try { window.ytPlayer.setOption('captions', 'track', {}); } catch(e) {}
                        try { window.ytPlayer.unloadModule('captions'); } catch(e) {}
                    } else {
                        try { window.ytPlayer.loadModule('captions'); } catch(e) {}
                        setTimeout(function() {
                            try {
                                var tracks = window.ytPlayer.getOption('captions', 'tracklist') || [];
                                if (tracks.length > 0) {
                                    window.ytPlayer.setOption('captions', 'track', tracks[0]);
                                } else {
                                    window.ytPlayer.setOption('captions', 'track', {'languageCode': 'en'});
                                }
                            } catch(e) {}
                        }, 500);
                    }
                } catch(e) {
                    try { window.ytPlayer.unloadModule('captions'); } catch(_) {}
                }
            })();
            """.trimIndent(),
            null
        )
    }
}

class YouTubeBridgeInterface(private val onEndedProvider: () -> (() -> Unit)?) {
    @android.webkit.JavascriptInterface
    fun onVideoEnded() {
        YouTubeRemoteBridge.isActuallyPlaying = false
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            val cb = onEndedProvider() ?: YouTubeRemoteBridge.onNextVideoCallback
            cb?.invoke()
        }
    }

    @android.webkit.JavascriptInterface
    fun onTimeChange(seconds: Float) {
        YouTubeRemoteBridge.lastPlaybackSeconds = seconds.toInt()
    }

    @android.webkit.JavascriptInterface
    fun onPlayerStateChange(state: Int) {
        // YT.PlayerState: -1 = UNSTARTED, 0 = ENDED, 1 = PLAYING, 2 = PAUSED, 3 = BUFFERING
        if (state == 1) {
            YouTubeRemoteBridge.isActuallyPlaying = true
        } else if (state == 2) {
            if (!YouTubeRemoteBridge.isTransitioning) {
                YouTubeRemoteBridge.isActuallyPlaying = false
            }
        } else if (state == 0) {
            YouTubeRemoteBridge.isActuallyPlaying = false
        }
    }
}

private fun buildYouTubeHtml(videoId: String, isPreview: Boolean, captionsEnabled: Boolean, startSeconds: Int = 0): String {
    val ccPolicy = if (!isPreview && captionsEnabled) 1 else 0
    val startParam = if (startSeconds > 0) "'start': $startSeconds," else ""
    return """
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
                    function applyCaptions(player, enable) {
                        if (!player) return;
                        try {
                            if (enable) {
                                player.loadModule('captions');
                                setTimeout(function() {
                                    try {
                                        var tr = player.getOption('captions', 'tracklist') || [];
                                        if (tr.length > 0) {
                                            player.setOption('captions', 'track', tr[0]);
                                        } else {
                                            player.setOption('captions', 'track', {'languageCode': 'en'});
                                        }
                                    } catch(_) {}
                                }, 600);
                            } else {
                                player.setOption('captions', 'track', {});
                                player.unloadModule('captions');
                            }
                        } catch(_) {}
                    }
                    ytPlayer = new YT.Player('player', {
                        videoId: vId,
                        playerVars: {
                            'autoplay': 1,
                            'controls': ${if (isPreview) 0 else 1},
                            'modestbranding': 1,
                            'rel': 0,
                            'fs': ${if (isPreview) 0 else 1},
                            'playsinline': 1,
                            'enablejsapi': 1,
                            'origin': 'https://www.youtube-nocookie.com',
                            $startParam
                            'iv_load_policy': 3,
                            'cc_load_policy': $ccPolicy,
                            'cc_lang_pref': 'en',
                            'hl': 'en'
                        },
                        events: {
                            'onReady': function(e) {
                                window.ytPlayer = e.target;
                                applyCaptions(e.target, ${if (!isPreview && captionsEnabled) "true" else "false"});
                                if (window.pendingVideoId) {
                                    e.target.loadVideoById(window.pendingVideoId);
                                    window.pendingVideoId = null;
                                } else {
                                    try {
                                        e.target.playVideo();
                                    } catch(err) {}
                                }
                                setInterval(function() {
                                    if (window.ytPlayer && typeof window.ytPlayer.getCurrentTime === 'function') {
                                        var t = window.ytPlayer.getCurrentTime();
                                        if (window.AndroidBridge && window.AndroidBridge.onTimeChange) {
                                            window.AndroidBridge.onTimeChange(t);
                                        }
                                    }
                                }, 500);
                            },
                            'onError': function(e) {
                                console.log('YT Error:', e.data);
                            },
                            'onStateChange': function(e) {
                                if (window.AndroidBridge && window.AndroidBridge.onPlayerStateChange) {
                                    window.AndroidBridge.onPlayerStateChange(e.data);
                                }
                                if (e.data === 1) {
                                    applyCaptions(e.target, ${if (captionsEnabled) "true" else "false"});
                                }
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
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerView(
    videoId: String,
    title: String = "",
    onBack: (() -> Unit)? = null,
    onNextVideo: (() -> Unit)? = null,
    nextVideoTitle: String? = null,
    onPreviousVideo: (() -> Unit)? = null,
    onExpandPreview: (() -> Unit)? = null,
    captionsEnabled: Boolean = false,
    isPreview: Boolean = (onBack == null),
    modifier: Modifier = Modifier
) {
    val currentOnNextVideo by rememberUpdatedState(onNextVideo)
    val currentOnPreviousVideo by rememberUpdatedState(onPreviousVideo)

    SideEffect {
        YouTubeRemoteBridge.onNextVideoCallback = currentOnNextVideo
        YouTubeRemoteBridge.onPreviousVideoCallback = currentOnPreviousVideo
    }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var showControls by remember { mutableStateOf(false) }
    var isCcOn by remember(captionsEnabled, isPreview) { mutableStateOf(if (isPreview) false else captionsEnabled) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scrubBadge by YouTubeRemoteBridge.scrubBadge.collectAsState()
    val mountTimestamp = remember { System.currentTimeMillis() }
    var centerKeyDownReceived by remember { mutableStateOf(false) }

    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

    LaunchedEffect(Unit) {
        if (!isPreview) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isCcOn, isPreview) {
        if (isPreview || !isCcOn) {
            webViewInstance?.evaluateJavascript(
                "if (window.ytPlayer) { try { window.ytPlayer.setOption('captions', 'track', {}); } catch(_) {} try { window.ytPlayer.unloadModule('captions'); } catch(_) {} }",
                null
            )
        } else {
            webViewInstance?.evaluateJavascript(
                """
                if (window.ytPlayer) {
                    try {
                        window.ytPlayer.loadModule('captions');
                        setTimeout(function() {
                            try {
                                var tracks = window.ytPlayer.getOption('captions', 'tracklist') || [];
                                if (tracks.length > 0) {
                                    window.ytPlayer.setOption('captions', 'track', tracks[0]);
                                } else {
                                    window.ytPlayer.setOption('captions', 'track', {'languageCode': 'en'});
                                }
                            } catch(_) {}
                        }, 500);
                    } catch(_) {}
                }
                """.trimIndent(),
                null
            )
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, lastInteractionTime) {
        if (showControls) {
            delay(4000)
            showControls = false
            if (!isPreview) {
                try {
                    focusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }
    }

    // Auto-clear scrub badge
    LaunchedEffect(scrubBadge) {
        if (scrubBadge != null) {
            delay(1500)
            YouTubeRemoteBridge.clearScrubBadge()
        }
    }

    LaunchedEffect(videoId) {
        if (YouTubeRemoteBridge.activeVideoId != videoId) {
            YouTubeRemoteBridge.activeVideoId = videoId
            webViewInstance?.evaluateJavascript(
                """
                if (window.ytPlayer && typeof window.ytPlayer.loadVideoById === 'function') {
                    window.ytPlayer.loadVideoById('$videoId');
                    try { window.ytPlayer.playVideo(); } catch(_) {}
                } else {
                    window.pendingVideoId = '$videoId';
                }
                """.trimIndent(),
                null
            )
        }
    }

    DisposableEffect(isPreview) {
        onDispose {
            if (!isPreview) {
                YouTubeRemoteBridge.notifyTransition()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (!isPreview) {
                    Modifier
                        .focusRequester(focusRequester)
                        .focusable()
                        .onKeyEvent { keyEvent ->
                            val keyCode = keyEvent.nativeKeyEvent.keyCode
                            val isSelectKey = keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                    keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
                                    keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER ||
                                    keyCode == android.view.KeyEvent.KEYCODE_BUTTON_A ||
                                    keyCode == android.view.KeyEvent.KEYCODE_BUTTON_SELECT

                            if (keyEvent.type == KeyEventType.KeyDown && isSelectKey) {
                                centerKeyDownReceived = true
                                return@onKeyEvent false
                            }

                            if (keyEvent.type == KeyEventType.KeyUp) {
                                if (isSelectKey) {
                                    val wasKeyDown = centerKeyDownReceived
                                    centerKeyDownReceived = false
                                    val isPastMountDebounce = (System.currentTimeMillis() - mountTimestamp) > 350L
                                    if (wasKeyDown && isPastMountDebounce) {
                                        YouTubeRemoteBridge.togglePlayPause()
                                        showControls = true
                                        lastInteractionTime = System.currentTimeMillis()
                                    }
                                    return@onKeyEvent true
                                }
                                when (keyCode) {
                                    android.view.KeyEvent.KEYCODE_DPAD_UP,
                                    android.view.KeyEvent.KEYCODE_CHANNEL_UP,
                                    android.view.KeyEvent.KEYCODE_PAGE_UP -> {
                                        currentOnPreviousVideo?.invoke()
                                        true
                                    }
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                                    android.view.KeyEvent.KEYCODE_CHANNEL_DOWN,
                                    android.view.KeyEvent.KEYCODE_PAGE_DOWN -> {
                                        currentOnNextVideo?.invoke()
                                        true
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            showControls = !showControls
                            lastInteractionTime = System.currentTimeMillis()
                        }
                } else Modifier
            )
    ) {
        AndroidView<WebView>(
            factory = { context ->
                val existingWv = YouTubeRemoteBridge.activeWebView
                if (existingWv != null) {
                    (existingWv.parent as? ViewGroup)?.removeView(existingWv)
                    existingWv.apply {
                        isFocusable = false
                        isFocusableInTouchMode = false
                        resumeTimers()
                        if (isPreview) {
                            setOnTouchListener { _, event ->
                                if (event.action == android.view.MotionEvent.ACTION_UP) {
                                    onExpandPreview?.invoke()
                                }
                                true
                            }
                        } else {
                            setOnTouchListener(null)
                        }
                        val needsReloadForPreviewMode = YouTubeRemoteBridge.activeIsPreview != isPreview
                        if (needsReloadForPreviewMode) {
                            YouTubeRemoteBridge.activeIsPreview = isPreview
                            YouTubeRemoteBridge.activeVideoId = videoId
                            val html = buildYouTubeHtml(videoId, isPreview, captionsEnabled, YouTubeRemoteBridge.lastPlaybackSeconds)
                            loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                        } else if (videoId.isNotBlank()) {
                            if (YouTubeRemoteBridge.activeVideoId != videoId) {
                                YouTubeRemoteBridge.activeVideoId = videoId
                                YouTubeRemoteBridge.lastPlaybackSeconds = 0
                                YouTubeRemoteBridge.isActuallyPlaying = true
                                post {
                                    evaluateJavascript(
                                        "if (window.ytPlayer && typeof window.ytPlayer.loadVideoById === 'function') { window.ytPlayer.loadVideoById('$videoId'); try { window.ytPlayer.playVideo(); } catch(_) {} } else { window.pendingVideoId = '$videoId'; }",
                                        null
                                    )
                                }
                            } else if (YouTubeRemoteBridge.isActuallyPlaying) {
                                postDelayed({
                                    evaluateJavascript(
                                        "if (window.ytPlayer && typeof window.ytPlayer.playVideo === 'function') { try { window.ytPlayer.playVideo(); } catch(_) {} }",
                                        null
                                    )
                                }, 100)
                                postDelayed({
                                    evaluateJavascript(
                                        "if (window.ytPlayer && typeof window.ytPlayer.playVideo === 'function') { try { window.ytPlayer.playVideo(); } catch(_) {} }",
                                        null
                                    )
                                }, 300)
                            }
                        }
                    }
                    webViewInstance = existingWv
                    existingWv
                } else {
                    YouTubeRemoteBridge.activeVideoId = videoId
                    YouTubeRemoteBridge.activeIsPreview = isPreview

                    WebView(context).apply {
                        isFocusable = false
                        isFocusableInTouchMode = false
                        resumeTimers()
                        if (isPreview) {
                            setOnTouchListener { _, event ->
                                if (event.action == android.view.MotionEvent.ACTION_UP) {
                                    onExpandPreview?.invoke()
                                }
                                true
                            }
                        } else {
                            setOnTouchListener(null)
                        }
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

                        val html = buildYouTubeHtml(videoId, isPreview, captionsEnabled, 0)
                        loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                        webViewInstance = this
                        YouTubeRemoteBridge.activeWebView = this
                    }
                }
            },
            update = { wv ->
            wv.resumeTimers()
            YouTubeRemoteBridge.activeWebView = wv
            if (isPreview) {
                wv.setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        onExpandPreview?.invoke()
                    }
                    true
                }
            } else {
                wv.setOnTouchListener(null)
            }
            if (YouTubeRemoteBridge.activeIsPreview != isPreview) {
                YouTubeRemoteBridge.activeIsPreview = isPreview
                val html = buildYouTubeHtml(videoId, isPreview, captionsEnabled, YouTubeRemoteBridge.lastPlaybackSeconds)
                wv.loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
            } else if (videoId.isNotBlank()) {
                if (YouTubeRemoteBridge.activeVideoId != videoId) {
                    YouTubeRemoteBridge.activeVideoId = videoId
                    YouTubeRemoteBridge.lastPlaybackSeconds = 0
                    YouTubeRemoteBridge.isActuallyPlaying = true
                    wv.post {
                        wv.evaluateJavascript(
                            "if (window.ytPlayer && typeof window.ytPlayer.loadVideoById === 'function') { window.ytPlayer.loadVideoById('$videoId'); try { window.ytPlayer.playVideo(); } catch(_) {} } else { window.pendingVideoId = '$videoId'; }",
                            null
                        )
                    }
                } else if (YouTubeRemoteBridge.isActuallyPlaying) {
                    wv.postDelayed({
                        wv.evaluateJavascript(
                            "if (window.ytPlayer && typeof window.ytPlayer.playVideo === 'function') { try { window.ytPlayer.playVideo(); } catch(_) {} }",
                            null
                        )
                    }, 100)
                }
            }
        },
            modifier = Modifier.fillMaxSize()
        )

        // Touch Media Navigation (Up / Down) for Phone / Touch Users in fullscreen
        // to go to previous and next media, identical to Live TV (hidden on TV and non-touch displays)
        val context = androidx.compose.ui.platform.LocalContext.current
        val hasTouchScreen = remember {
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
        }
        val isTv = remember {
            val uiModeManager = context.getSystemService(android.content.Context.UI_MODE_SERVICE) as? android.app.UiModeManager
            uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK) ||
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION) ||
            !hasTouchScreen
        }

        if (!isPreview && !isTv && hasTouchScreen && (currentOnPreviousVideo != null || currentOnNextVideo != null)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (currentOnPreviousVideo != null) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaAccent.copy(alpha = 0.85f)),
                            modifier = Modifier
                                .size(52.dp)
                                .clickable { currentOnPreviousVideo?.invoke() }
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Previous Media",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }

                    if (currentOnNextVideo != null) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaAccent.copy(alpha = 0.85f)),
                            modifier = Modifier
                                .size(52.dp)
                                .clickable { currentOnNextVideo?.invoke() }
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Next Media",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Scrub Acceleration Badge
        if (!isPreview && scrubBadge != null) {
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
            visible = !isPreview && showControls,
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
                    if (onBack != null) {
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
            }
        }
    }
}

