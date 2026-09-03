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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
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
    MUSIC("Music", Icons.Default.MusicNote),
    PODCASTS("Podcasts", Icons.Default.Podcasts),
    SETTINGS("Settings", Icons.Default.Settings)
}

data class FullscreenMediaState(
    val url: String,
    val title: String,
    val onNextEpisode: (() -> Unit)? = null,
    val nextEpisodeTitle: String? = null
)

data class FullscreenYouTubeState(
    val videoId: String,
    val title: String,
    val onNextVideo: (() -> Unit)? = null,
    val nextVideoTitle: String? = null,
    val onPreviousVideo: (() -> Unit)? = null
)

@Composable
fun MainAppScreen(
    authRepo: AuthRepository,
    apiClient: XtreamApiClient,
    catalogManager: CatalogManager,
    playerManager: ExoPlayerManager,
    onSignOut: () -> Unit
) {
    var activeTab by remember { mutableStateOf(AppTab.LIVE) }
    val liveTabFocusRequester = remember { FocusRequester() }
    val liveContentFocusRequester = remember { FocusRequester() }

    // Live TV In-Place Fullscreen State
    var isLiveTvFullscreen by remember { mutableStateOf(false) }

    // Fullscreen Playback States for VOD and YouTube
    var fullscreenMedia by remember { mutableStateOf<FullscreenMediaState?>(null) }
    var fullscreenYouTube by remember { mutableStateOf<FullscreenYouTubeState?>(null) }

    // Sync VOD, YouTube and Live TV fullscreen states and next-item callbacks with MainActivity for remote key interception
    LaunchedEffect(fullscreenMedia, isLiveTvFullscreen, fullscreenYouTube) {
        MainActivity.isVODFullscreenActive = (fullscreenMedia != null)
        MainActivity.isLiveFullscreenActive = isLiveTvFullscreen
        MainActivity.onNextEpisodeCallback = fullscreenMedia?.onNextEpisode
        MainActivity.onNextYouTubeCallback = fullscreenYouTube?.onNextVideo
        MainActivity.onPreviousYouTubeCallback = fullscreenYouTube?.onPreviousVideo
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

    // Handle tab change: stop playback if leaving Live TV without fullscreen media
    LaunchedEffect(activeTab) {
        if (activeTab != AppTab.LIVE && fullscreenMedia == null) {
            playerManager.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CinemaBackground)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth > 600.dp

            if (isWideScreen) {
                // TV / Landscape Layout: Compact Icon-Only Sidebar
                Row(modifier = Modifier.fillMaxSize()) {
                    if (!isLiveTvFullscreen) {
                        Surface(
                            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                            color = CinemaSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                            modifier = Modifier
                                .width(76.dp)
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 16.dp, horizontal = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // App Brand with Official Logo
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(id = com.troyh.tvdinner.R.drawable.app_logo),
                                        contentDescription = "TV Dinner",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }

                                // Navigation Icon Items
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
                                        shape = RoundedCornerShape(12.dp),
                                        backgroundColor = if (isSelected) CinemaPrimary else Color.Transparent,
                                        focusedBorderColor = CinemaFocus,
                                        focusedScale = 1.08f,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .then(if (tab == AppTab.LIVE) Modifier.focusRequester(liveTabFocusRequester) else Modifier)
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight) {
                                                    if (activeTab == AppTab.LIVE) {
                                                        try {
                                                            liveContentFocusRequester.requestFocus()
                                                            true
                                                        } catch (_: Exception) {
                                                            false
                                                        }
                                                    } else {
                                                        false
                                                    }
                                                } else {
                                                    false
                                                }
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.label,
                                                tint = if (isSelected) Color.White else TextSecondary,
                                                modifier = Modifier.size(24.dp)
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
                                onToggleFullscreen = { isLiveTvFullscreen = it },
                                contentFocusRequester = liveContentFocusRequester,
                                onRequestFocusSidebar = {
                                    try {
                                        liveTabFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                }
                            )
                            AppTab.MOVIES -> MoviesScreen(
                                authRepo = authRepo,
                                apiClient = apiClient,
                                catalogManager = catalogManager,
                                isPlayingFullscreen = (fullscreenMedia != null),
                                onPlayMovie = { url, title, startPos, streamKey ->
                                    playerManager.playStream(url, title, isLive = false, startPositionMs = startPos, streamKey = streamKey)
                                    fullscreenMedia = FullscreenMediaState(
                                        url = url,
                                        title = title,
                                        onNextEpisode = null,
                                        nextEpisodeTitle = null
                                    )
                                }
                            )
                            AppTab.SERIES -> SeriesScreen(
                                authRepo = authRepo,
                                apiClient = apiClient,
                                catalogManager = catalogManager,
                                isPlayingFullscreen = (fullscreenMedia != null),
                                onPlayEpisode = { url, title, startPos, streamKey, onNext, nextTitle ->
                                    playerManager.playStream(url, title, isLive = false, startPositionMs = startPos, streamKey = streamKey)
                                    fullscreenMedia = FullscreenMediaState(
                                        url = url,
                                        title = title,
                                        onNextEpisode = onNext,
                                        nextEpisodeTitle = nextTitle
                                    )
                                }
                            )
                            AppTab.MUSIC -> MusicScreen(
                                authRepo = authRepo,
                                catalogManager = catalogManager,
                                onPlayYouTubeVideo = { videoId, title, onNext, nextTitle, onPrev ->
                                    fullscreenYouTube = FullscreenYouTubeState(videoId, title, onNext, nextTitle, onPrev)
                                }
                            )
                            AppTab.PODCASTS -> PodcastsScreen(
                                authRepo = authRepo,
                                catalogManager = catalogManager,
                                onPlayYouTubeVideo = { videoId, title, onNext, nextTitle, onPrev ->
                                    fullscreenYouTube = FullscreenYouTubeState(videoId, title, onNext, nextTitle, onPrev)
                                }
                            )
                            AppTab.SETTINGS -> SettingsScreen(
                                authRepo = authRepo,
                                catalogManager = catalogManager,
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
            } else {
                // Mobile Portrait Layout: Bottom Navigation Bar with Symbols Only
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
                                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.label, modifier = Modifier.size(24.dp)) },
                                        alwaysShowLabel = false,
                                        colors = NavigationBarItemDefaults.colors(
                                             selectedIconColor = CinemaAccent,
                                             indicatorColor = CinemaPrimary.copy(alpha = 0.25f),
                                             unselectedIconColor = TextSecondary
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
                                isPlayingFullscreen = (fullscreenMedia != null),
                                onPlayMovie = { url, title, startPos, streamKey ->
                                    playerManager.playStream(url, title, isLive = false, startPositionMs = startPos, streamKey = streamKey)
                                    fullscreenMedia = FullscreenMediaState(
                                        url = url,
                                        title = title,
                                        onNextEpisode = null,
                                        nextEpisodeTitle = null
                                    )
                                }
                            )
                            AppTab.SERIES -> SeriesScreen(
                                authRepo = authRepo,
                                apiClient = apiClient,
                                catalogManager = catalogManager,
                                isPlayingFullscreen = (fullscreenMedia != null),
                                onPlayEpisode = { url, title, startPos, streamKey, onNext, nextTitle ->
                                    playerManager.playStream(url, title, isLive = false, startPositionMs = startPos, streamKey = streamKey)
                                    fullscreenMedia = FullscreenMediaState(
                                        url = url,
                                        title = title,
                                        onNextEpisode = onNext,
                                        nextEpisodeTitle = nextTitle
                                    )
                                }
                            )
                            AppTab.MUSIC -> MusicScreen(
                                authRepo = authRepo,
                                catalogManager = catalogManager,
                                onPlayYouTubeVideo = { videoId, title, onNext, nextTitle, onPrev ->
                                    fullscreenYouTube = FullscreenYouTubeState(videoId, title, onNext, nextTitle, onPrev)
                                }
                            )
                            AppTab.PODCASTS -> PodcastsScreen(
                                authRepo = authRepo,
                                catalogManager = catalogManager,
                                onPlayYouTubeVideo = { videoId, title, onNext, nextTitle, onPrev ->
                                    fullscreenYouTube = FullscreenYouTubeState(videoId, title, onNext, nextTitle, onPrev)
                                }
                            )
                            AppTab.SETTINGS -> SettingsScreen(
                                authRepo = authRepo,
                                catalogManager = catalogManager,
                                onSignOut = onSignOut
                            )
                        }
                    }
                }
            }
        }

        // Fullscreen Overlays for VOD and YouTube
        if (fullscreenMedia != null) {
            val mediaState = fullscreenMedia!!
            NativePlayerView(
                playerManager = playerManager,
                onBack = {
                    playerManager.stop()
                    fullscreenMedia = null
                },
                onNextEpisode = mediaState.onNextEpisode,
                nextEpisodeTitle = mediaState.nextEpisodeTitle,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (fullscreenYouTube != null) {
            val ytState = fullscreenYouTube!!
            YouTubePlayerView(
                videoId = ytState.videoId,
                title = ytState.title,
                onBack = {
                    YouTubeRemoteBridge.activeWebView = null
                    fullscreenYouTube = null
                },
                onNextVideo = ytState.onNextVideo,
                nextVideoTitle = ytState.nextVideoTitle,
                onPreviousVideo = ytState.onPreviousVideo,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
