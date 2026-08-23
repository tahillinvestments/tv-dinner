package com.troyh.tvdinner.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.troyh.tvdinner.MainActivity
import com.troyh.tvdinner.data.network.XtreamApiClient
import com.troyh.tvdinner.data.repository.AuthRepository
import com.troyh.tvdinner.data.repository.CatalogManager
import com.troyh.tvdinner.player.ExoPlayerManager
import com.troyh.tvdinner.ui.components.TvFocusableCard
import com.troyh.tvdinner.ui.player.NativePlayerView
import com.troyh.tvdinner.ui.player.YouTubePlayerView
import com.troyh.tvdinner.ui.player.YouTubeRemoteBridge
import com.troyh.tvdinner.ui.theme.*

enum class AppTab(val label: String, val icon: ImageVector) {
    LIVE("Live TV", Icons.Default.Tv),
    MOVIES("Movies", Icons.Default.Movie),
    SERIES("Series", Icons.Default.VideoLibrary),
    PODCASTS("Podcasts", Icons.Default.Podcasts),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun MainAppScreen(
    authRepo: AuthRepository,
    apiClient: XtreamApiClient,
    catalogManager: CatalogManager,
    playerManager: ExoPlayerManager,
    onSignOut: () -> Unit
) {
    var activeTab by remember { mutableStateOf(AppTab.LIVE) }

    // Live TV In-Place Fullscreen State
    var isLiveTvFullscreen by remember { mutableStateOf(false) }

    // Fullscreen Playback States for VOD and YouTube
    var fullscreenMedia by remember { mutableStateOf<Pair<String, String>?>(null) } // (url, title)
    var fullscreenYouTube by remember { mutableStateOf<Pair<String, String>?>(null) } // (videoId, title)

    // Sync VOD fullscreen state with MainActivity for remote key interception
    LaunchedEffect(fullscreenMedia) {
        MainActivity.isVODFullscreenActive = (fullscreenMedia != null)
    }

    // Hierarchical Back Button Handler
    BackHandler(enabled = fullscreenMedia != null || fullscreenYouTube != null || isLiveTvFullscreen || activeTab != AppTab.LIVE) {
        if (fullscreenMedia != null) {
            playerManager.stop()
            fullscreenMedia = null
        } else if (fullscreenYouTube != null) {
            YouTubeRemoteBridge.activeWebView = null
            fullscreenYouTube = null
        } else if (isLiveTvFullscreen) {
            isLiveTvFullscreen = false
        } else if (activeTab != AppTab.LIVE) {
            playerManager.stop()
            activeTab = AppTab.LIVE
        }
    }

    // Stop playback if switching away from active tab
    LaunchedEffect(activeTab) {
        if (activeTab != AppTab.LIVE && fullscreenMedia == null) {
            playerManager.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CinemaBackground)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth > 600.dp

            if (isWideScreen) {
                // TV / Landscape Layout: Sidebar hides smoothly during fullscreen without recreating screens
                Row(modifier = Modifier.fillMaxSize()) {
                    if (!isLiveTvFullscreen) {
                        Surface(
                            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                            color = CinemaSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 20.dp, horizontal = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // App Brand with Official Logo (Enlarged, centered, no redundant text)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(id = com.troyh.tvdinner.R.drawable.app_logo),
                                        contentDescription = "TV Dinner",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        modifier = Modifier
                                            .size(96.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Navigation Items
                                for (tab in AppTab.values()) {
                                    val isSelected = activeTab == tab
                                    TvFocusableCard(
                                        onClick = {
                                            if (activeTab != tab) {
                                                if (activeTab != AppTab.LIVE) {
                                                    playerManager.stop()
                                                }
                                                activeTab = tab
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        backgroundColor = if (isSelected) CinemaPrimary else Color.Transparent,
                                        focusedBorderColor = CinemaFocus,
                                        focusedScale = 1.04f,
                                        modifier = Modifier.fillMaxWidth().height(46.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.label,
                                                tint = if (isSelected) Color.White else TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = tab.label,
                                                color = if (isSelected) Color.White else TextSecondary,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Main Content View (Maintains 100% state persistence)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (activeTab) {
                            AppTab.LIVE -> LiveTvScreen(
                                authRepo = authRepo,
                                apiClient = apiClient,
                                catalogManager = catalogManager,
                                playerManager = playerManager,
                                isFullscreen = isLiveTvFullscreen,
                                onToggleFullscreen = { isLiveTvFullscreen = it }
                            )
                            AppTab.MOVIES -> MoviesScreen(
                                authRepo = authRepo,
                                apiClient = apiClient,
                                catalogManager = catalogManager,
                                onPlayMovie = { url, title, startPos, streamKey ->
                                    playerManager.playStream(url, title, isLive = false, startPositionMs = startPos, streamKey = streamKey)
                                    fullscreenMedia = Pair(url, title)
                                }
                            )
                            AppTab.SERIES -> SeriesScreen(
                                authRepo = authRepo,
                                apiClient = apiClient,
                                catalogManager = catalogManager,
                                onPlayEpisode = { url, title, startPos, streamKey ->
                                    playerManager.playStream(url, title, isLive = false, startPositionMs = startPos, streamKey = streamKey)
                                    fullscreenMedia = Pair(url, title)
                                }
                            )
                            AppTab.PODCASTS -> PodcastsScreen(
                                authRepo = authRepo,
                                catalogManager = catalogManager,
                                onPlayYouTubeVideo = { videoId, title ->
                                    fullscreenYouTube = Pair(videoId, title)
                                }
                            )
                            AppTab.SETTINGS -> SettingsScreen(
                                authRepo = authRepo,
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
            } else {
                // Mobile Portrait Layout: Bottom Navigation Bar
                Scaffold(
                    bottomBar = {
                        if (!isLiveTvFullscreen) {
                            NavigationBar(
                                containerColor = CinemaSurface,
                                contentColor = TextPrimary
                            ) {
                                for (tab in AppTab.values()) {
                                    NavigationBarItem(
                                        selected = activeTab == tab,
                                        onClick = {
                                            if (activeTab != tab) {
                                                if (activeTab != AppTab.LIVE) {
                                                    playerManager.stop()
                                                }
                                                activeTab = tab
                                            }
                                        },
                                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                                        label = { Text(tab.label, fontSize = 11.sp) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = CinemaAccent,
                                            selectedTextColor = CinemaAccent,
                                            indicatorColor = CinemaPrimary.copy(alpha = 0.2f),
                                            unselectedIconColor = TextSecondary,
                                            unselectedTextColor = TextSecondary
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        when (activeTab) {
                            AppTab.LIVE -> LiveTvScreen(
                                authRepo = authRepo,
                                apiClient = apiClient,
                                catalogManager = catalogManager,
                                playerManager = playerManager,
                                isFullscreen = isLiveTvFullscreen,
                                onToggleFullscreen = { isLiveTvFullscreen = it }
                            )
                            AppTab.MOVIES -> MoviesScreen(
                                authRepo = authRepo,
                                apiClient = apiClient,
                                catalogManager = catalogManager,
                                onPlayMovie = { url, title, startPos, streamKey ->
                                    playerManager.playStream(url, title, isLive = false, startPositionMs = startPos, streamKey = streamKey)
                                    fullscreenMedia = Pair(url, title)
                                }
                            )
                            AppTab.SERIES -> SeriesScreen(
                                authRepo = authRepo,
                                apiClient = apiClient,
                                catalogManager = catalogManager,
                                onPlayEpisode = { url, title, startPos, streamKey ->
                                    playerManager.playStream(url, title, isLive = false, startPositionMs = startPos, streamKey = streamKey)
                                    fullscreenMedia = Pair(url, title)
                                }
                            )
                            AppTab.PODCASTS -> PodcastsScreen(
                                authRepo = authRepo,
                                catalogManager = catalogManager,
                                onPlayYouTubeVideo = { videoId, title ->
                                    fullscreenYouTube = Pair(videoId, title)
                                }
                            )
                            AppTab.SETTINGS -> SettingsScreen(
                                authRepo = authRepo,
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
            }
        }

        // Fullscreen Overlays for VOD and YouTube
        if (fullscreenMedia != null) {
            val (_, title) = fullscreenMedia!!
            NativePlayerView(
                playerManager = playerManager,
                onBack = {
                    playerManager.stop()
                    fullscreenMedia = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (fullscreenYouTube != null) {
            val (videoId, title) = fullscreenYouTube!!
            YouTubePlayerView(
                videoId = videoId,
                title = title,
                onBack = {
                    YouTubeRemoteBridge.activeWebView = null
                    fullscreenYouTube = null
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
