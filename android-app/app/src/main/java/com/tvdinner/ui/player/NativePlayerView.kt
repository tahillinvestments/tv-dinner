package com.tvdinner.ui.player

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tvdinner.player.ExoPlayerManager
import com.tvdinner.ui.components.TvFocusableCard
import com.tvdinner.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun NativePlayerView(
    playerManager: ExoPlayerManager,
    onBack: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    nextEpisodeTitle: String? = null,
    modifier: Modifier = Modifier
) {
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val title by playerManager.currentTitle.collectAsState()
    val isLive by playerManager.isLiveStream.collectAsState()
    val isLiveRewound by playerManager.isLiveRewound.collectAsState()
    val liveRewindOffsetSeconds by playerManager.liveRewindOffsetSeconds.collectAsState()
    val position by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()
    val errorMessage by playerManager.errorMessage.collectAsState()
    val isStreamStalled by playerManager.isStreamStalled.collectAsState()
    val resizeMode by playerManager.resizeMode.collectAsState()
    val isCcEnabled by playerManager.isClosedCaptionsEnabled.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val focusRequester = remember { FocusRequester() }

    var showAspectHud by remember { mutableStateOf(false) }
    var lastObservedResizeMode by remember { mutableIntStateOf(resizeMode) }

    LaunchedEffect(resizeMode) {
        if (resizeMode != lastObservedResizeMode) {
            lastObservedResizeMode = resizeMode
            showAspectHud = true
            delay(2200)
            showAspectHud = false
        }
    }

    var showCcHud by remember { mutableStateOf(false) }
    var lastObservedCcState by remember { mutableStateOf(isCcEnabled) }

    LaunchedEffect(isCcEnabled) {
        if (isCcEnabled != lastObservedCcState) {
            lastObservedCcState = isCcEnabled
            showCcHud = true
            delay(2200)
            showCcHud = false
        }
    }

    var showLiveRewindHud by remember { mutableStateOf(false) }
    var lastObservedLiveOffset by remember { mutableIntStateOf(liveRewindOffsetSeconds) }

    LaunchedEffect(isLiveRewound, liveRewindOffsetSeconds) {
        if (isLive && (isLiveRewound || liveRewindOffsetSeconds != lastObservedLiveOffset)) {
            lastObservedLiveOffset = liveRewindOffsetSeconds
            showLiveRewindHud = true
            delay(2500)
            showLiveRewindHud = false
        }
    }

    // Request active focus on mount ONLY in standalone / fullscreen mode
    LaunchedEffect(onBack) {
        if (onBack != null) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Auto-hide controls after 4 seconds of inactivity ONLY when actively playing
    LaunchedEffect(showControls, isPlaying, lastInteractionTime) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .then(if (onBack != null) Modifier.focusRequester(focusRequester).focusable() else Modifier)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            playerManager.togglePlayPause()
                            showControls = true
                            lastInteractionTime = System.currentTimeMillis()
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            if (!isLive) {
                                playerManager.seekRewind10s()
                            } else {
                                playerManager.rewindLive()
                            }
                            showControls = true
                            lastInteractionTime = System.currentTimeMillis()
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            if (!isLive) {
                                playerManager.seekForward10s()
                            } else {
                                playerManager.forwardLive()
                            }
                            showControls = true
                            lastInteractionTime = System.currentTimeMillis()
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_S -> {
                            playerManager.toggleClosedCaptions()
                            showControls = true
                            lastInteractionTime = System.currentTimeMillis()
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, KeyEvent.KEYCODE_A -> {
                            playerManager.cycleAudioTrack()
                            showControls = true
                            lastInteractionTime = System.currentTimeMillis()
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_MENU,
                        KeyEvent.KEYCODE_INFO,
                        KeyEvent.KEYCODE_PROG_YELLOW,
                        KeyEvent.KEYCODE_PROG_BLUE,
                        KeyEvent.KEYCODE_WINDOW,
                        228 /* KEYCODE_ASPECT_RATIO */ -> {
                            playerManager.cycleAspectRatio()
                            lastInteractionTime = System.currentTimeMillis()
                            return@onKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (!isLive) {
                                playerManager.cycleAspectRatio()
                                lastInteractionTime = System.currentTimeMillis()
                                return@onKeyEvent true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!isLive) {
                                playerManager.toggleClosedCaptions()
                                lastInteractionTime = System.currentTimeMillis()
                                return@onKeyEvent true
                            }
                        }
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
        // ExoPlayer View
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    this.resizeMode = resizeMode
                    player = playerManager.player
                    setBackgroundColor(android.graphics.Color.BLACK)
                    subtitleView?.apply {
                        setApplyEmbeddedStyles(false)
                        setStyle(
                            androidx.media3.ui.CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.argb(180, 0, 0, 0),
                                android.graphics.Color.TRANSPARENT,
                                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null
                            )
                        )
                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
                    }
                }
            },
            update = { playerView ->
                playerView.player = playerManager.player
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Buffering / Interrupted Stream Overlay
        if (isBuffering || isStreamStalled || errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (errorMessage != null || isStreamStalled) Color.Black.copy(alpha = 0.78f)
                        else Color.Transparent
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    if (isBuffering && !isStreamStalled && errorMessage == null) {
                        CircularProgressIndicator(
                            color = CinemaAccent,
                            modifier = Modifier.size(52.dp),
                            strokeWidth = 4.dp
                        )
                    } else {
                        CircularProgressIndicator(
                            color = CinemaAccent,
                            modifier = Modifier.size(36.dp),
                            strokeWidth = 3.dp
                        )
                        Text(
                            text = errorMessage ?: "Stream interrupted. Auto-recovering...",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TvFocusableCard(
                            onClick = {
                                playerManager.reconnectCurrentStream()
                            },
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = CinemaPrimary,
                            focusedBorderColor = CinemaAccent
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Reconnect Stream",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Player Controls HUD Overlay (Visible when showControls is true or when paused)
        AnimatedVisibility(
            visible = showControls || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.75f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                            text = title.ifBlank { "TV Dinner Stream" },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (isLive) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CinemaRed)
                                )
                                Text(
                                    text = "LIVE BROADCAST",
                                    color = CinemaRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Controls in Header: Closed Captions & Aspect Ratio
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Closed Captions Toggle
                        TvFocusableCard(
                            onClick = {
                                playerManager.toggleClosedCaptions()
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = if (isCcEnabled) CinemaPrimary else CinemaSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isCcEnabled) "CC: ON" else "CC: OFF",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Aspect Ratio Toggle
                        TvFocusableCard(
                            onClick = {
                                playerManager.cycleAspectRatio()
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = CinemaSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Aspect: Stretch"
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Aspect: Zoom"
                                        else -> "Aspect: Fit"
                                    },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Reconnect Stream Button in HUD
                        TvFocusableCard(
                            onClick = {
                                playerManager.reconnectCurrentStream()
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = CinemaPrimary,
                            focusedBorderColor = CinemaAccent,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reconnect Stream",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Reconnect",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Center Play/Pause & Skip Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvFocusableCard(
                        onClick = {
                            if (!isLive) {
                                playerManager.seekRewind10s()
                            } else {
                                playerManager.rewindLive()
                            }
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        backgroundColor = CinemaSurfaceVariant.copy(alpha = 0.8f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FastRewind,
                                contentDescription = "Rewind",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    TvFocusableCard(
                        onClick = {
                            playerManager.togglePlayPause()
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier.size(68.dp),
                        shape = CircleShape,
                        backgroundColor = CinemaPrimary
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    TvFocusableCard(
                        onClick = {
                            if (!isLive) {
                                playerManager.seekForward10s()
                            } else {
                                playerManager.forwardLive()
                            }
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        backgroundColor = if (isLive && !isLiveRewound) CinemaSurfaceVariant.copy(alpha = 0.35f) else CinemaSurfaceVariant.copy(alpha = 0.8f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FastForward,
                                contentDescription = "Forward",
                                tint = if (isLive && !isLiveRewound) Color.White.copy(alpha = 0.4f) else Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                // Bottom Jump to Live for Live TV Time-Shift
                if (isLive && isLiveRewound) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TvFocusableCard(
                            onClick = {
                                playerManager.returnToLive()
                                lastInteractionTime = System.currentTimeMillis()
                            },
                            shape = RoundedCornerShape(20.dp),
                            backgroundColor = CinemaAccent.copy(alpha = 0.9f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color.Red, shape = CircleShape)
                                )
                                Text(
                                    text = "-${liveRewindOffsetSeconds}s • Jump to Live",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                // Bottom Progress Bar & Time Stamps for VOD
                if (!isLive) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(position),
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (duration > 0) formatTime(duration) else "--:--",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (duration > 0) {
                            Slider(
                                value = position.toFloat().coerceIn(0f, duration.toFloat()),
                                onValueChange = {
                                    playerManager.seekTo(it.toLong())
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                valueRange = 0f..duration.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = CinemaAccent,
                                    activeTrackColor = CinemaPrimary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(
                                color = CinemaPrimary,
                                trackColor = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.fillMaxWidth().height(4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Live TV Rewind / Catchup HUD Pill Overlay (Animated on remote left/right rewind actions)
        androidx.compose.animation.AnimatedVisibility(
            visible = isLive && (isLiveRewound || showLiveRewindHud),
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 28.dp, start = 28.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isLiveRewound) CinemaAccent else CinemaPrimary)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLiveRewound) {
                        Icon(
                            imageVector = Icons.Default.FastRewind,
                            contentDescription = null,
                            tint = CinemaAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "-${liveRewindOffsetSeconds}s (Press ▶▶ for Live)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color.Red, shape = CircleShape)
                        )
                        Text(
                            text = "LIVE FEED",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Aspect Ratio HUD Pill Overlay (Animated on remote aspect toggles)
        androidx.compose.animation.AnimatedVisibility(
            visible = showAspectHud,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 28.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaFocus)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AspectRatio,
                        contentDescription = null,
                        tint = CinemaFocus,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Aspect: Stretch (16:9)"
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Aspect: Zoom (Fill)"
                            else -> "Aspect: Fit (Original)"
                        },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Closed Captions / Subtitles HUD Pill Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = showCcHud,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = if (showAspectHud) 80.dp else 28.dp, end = 28.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, if (isCcEnabled) CinemaAccent else CinemaSurfaceLight)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = null,
                        tint = if (isCcEnabled) CinemaAccent else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isCcEnabled) "Subtitles: ON (English)" else "Subtitles: OFF",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
