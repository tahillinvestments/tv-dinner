package com.tvdinner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvdinner.MainActivity
import com.tvdinner.player.ExoPlayerManager
import com.tvdinner.ui.player.NativePlayerView
import com.tvdinner.ui.player.YouTubePlayerView
import com.tvdinner.ui.theme.*

@Composable
fun UniversalIntegratedPreview(
    playerManager: ExoPlayerManager,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    activeYouTubeVideoId: String? = null,
    activeYouTubeTitle: String? = null,
    onCloseYouTube: (() -> Unit)? = null
) {
    val isPlaying by playerManager.isPlaying.collectAsState()
    val isBuffering by playerManager.isBuffering.collectAsState()
    val currentStreamUrl by playerManager.currentStreamUrl.collectAsState()
    val playerYtId by playerManager.activeYouTubeVideoId.collectAsState()
    val playerYtTitle by playerManager.activeYouTubeTitle.collectAsState()

    val effectiveYtId = activeYouTubeVideoId ?: playerYtId
    val effectiveYtTitle = activeYouTubeTitle ?: playerYtTitle

    val isNativeActive = currentStreamUrl.isNotBlank() || isPlaying || isBuffering
    val isYouTubeActive = !effectiveYtId.isNullOrBlank()

    val focusManager = LocalFocusManager.current

    // The entire preview window is a single TV focusable card optimized for remote control
    TvFocusableCard(
        onClick = onExpand,
        shape = RoundedCornerShape(12.dp),
        backgroundColor = Color.Black,
        focusedBorderColor = CinemaAccent,
        focusedScale = 1.02f,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionDown -> {
                            try {
                                focusManager.moveFocus(FocusDirection.Down)
                                true
                            } catch (_: Exception) { false }
                        }
                        Key.DirectionRight -> {
                            try {
                                focusManager.moveFocus(FocusDirection.Right)
                                true
                            } catch (_: Exception) { false }
                        }
                        Key.DirectionLeft -> {
                            try {
                                focusManager.moveFocus(FocusDirection.Left)
                                true
                            } catch (_: Exception) { false }
                        }
                        Key.DirectionUp -> {
                            try {
                                focusManager.moveFocus(FocusDirection.Up)
                                true
                            } catch (_: Exception) { false }
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onExpand()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Avoid dual PlayerView surface contention or duplicate WebView when any fullscreen overlay is showing
            val isFullscreenShowing = MainActivity.isVODFullscreenActive ||
                                      MainActivity.isLiveFullscreenActive ||
                                      MainActivity.isYouTubeFullscreenActive

            if (!isFullscreenShowing) {
                when {
                    isNativeActive -> {
                        NativePlayerView(
                            playerManager = playerManager,
                            onBack = null,
                            isPreview = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    isYouTubeActive -> {
                        YouTubePlayerView(
                            videoId = effectiveYtId,
                            title = effectiveYtTitle ?: "YouTube Media",
                            onBack = null,
                            isPreview = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> {
                        // Idle state: Clean Universal TV DINNER Preview
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CinemaSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = "TV DINNER Preview",
                                    tint = CinemaAccent,
                                    modifier = Modifier.size(30.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "TV DINNER Preview",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Select any content to play",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }

            // Non-intrusive Buffering Card: Only visible when actively buffering
            if (isBuffering && isNativeActive) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaAccent.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            color = CinemaAccent,
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Buffering...",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
