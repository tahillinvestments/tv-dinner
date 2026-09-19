package com.tvdinner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val currentTitle by playerManager.currentTitle.collectAsState()
    val isLiveStream by playerManager.isLiveStream.collectAsState()
    val currentStreamUrl by playerManager.currentStreamUrl.collectAsState()
    val playerYtId by playerManager.activeYouTubeVideoId.collectAsState()
    val playerYtTitle by playerManager.activeYouTubeTitle.collectAsState()

    val effectiveYtId = activeYouTubeVideoId ?: playerYtId
    val effectiveYtTitle = activeYouTubeTitle ?: playerYtTitle

    val isNativeActive = currentStreamUrl.isNotBlank() || isPlaying || isBuffering
    val isYouTubeActive = !effectiveYtId.isNullOrBlank()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isNativeActive || isYouTubeActive) CinemaAccent else CinemaSurfaceLight
        ),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
    ) {
        when {
            isNativeActive -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    NativePlayerView(
                        playerManager = playerManager,
                        onBack = null,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top Bar: Badge, Title & Close 'X' Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isLiveStream) CinemaRed else CinemaPrimary
                                ) {
                                    Text(
                                        text = if (isLiveStream) "LIVE" else "VOD",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = currentTitle.ifBlank { "Playing Stream" },
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Stop Playback",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Bottom Bar: Expand to Fullscreen & Play/Pause
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TvFocusableCard(
                                onClick = onExpand,
                                shape = RoundedCornerShape(6.dp),
                                backgroundColor = CinemaPrimary.copy(alpha = 0.7f),
                                focusedBorderColor = CinemaAccent
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInFull,
                                        contentDescription = "Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Fullscreen",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(
                                onClick = { playerManager.togglePlayPause() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            isYouTubeActive -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    YouTubePlayerView(
                        videoId = effectiveYtId!!,
                        title = effectiveYtTitle ?: "YouTube Media",
                        onBack = null,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top Bar: Badge, Title & Close 'X' Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CinemaRed
                                ) {
                                    Text(
                                        text = "YT",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = effectiveYtTitle ?: "YouTube Media",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = {
                                    playerManager.clearYouTubeMedia()
                                    onCloseYouTube?.invoke() ?: onClose()
                                },
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Stop Playback",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Bottom Bar: Expand
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TvFocusableCard(
                                onClick = onExpand,
                                shape = RoundedCornerShape(6.dp),
                                backgroundColor = CinemaPrimary.copy(alpha = 0.7f),
                                focusedBorderColor = CinemaAccent
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInFull,
                                        contentDescription = "Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Fullscreen",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
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
    }
}
