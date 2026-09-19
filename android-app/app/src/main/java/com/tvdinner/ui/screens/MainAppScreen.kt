package com.tvdinner.ui.screens

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
import com.tvdinner.MainActivity
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import com.tvdinner.data.model.PodcastEpisode
import com.tvdinner.data.network.XtreamApiClient
import com.tvdinner.data.repository.AuthRepository
import com.tvdinner.data.repository.CatalogManager
import com.tvdinner.player.ExoPlayerManager
import com.tvdinner.ui.components.TvFocusableCard
import com.tvdinner.ui.player.NativePlayerView
import com.tvdinner.ui.player.YouTubePlayerView
import com.tvdinner.ui.player.YouTubeRemoteBridge
import com.tvdinner.ui.theme.*

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
    val coroutineScope = rememberCoroutineScope()
    var activeTab by remember { mutableStateOf(AppTab.LIVE) }
    var credentialsRevision by remember { mutableIntStateOf(0) }
    val liveTabFocusRequester = remember { FocusRequester() }
    val liveContentFocusRequester = remember { FocusRequester() }

    // Live TV In-Place Fullscreen State
    var isLiveTvFullscreen by remember { mutableStateOf(false) }

    // Fullscreen Playback States for VOD and YouTube
    var fullscreenMedia by remember { mutableStateOf<FullscreenMediaState?>(null) }
    var fullscreenYouTube by remember { mutableStateOf<FullscreenYouTubeState?>(null) }
    val isMusicCaptionsEnabled by authRepo.isMusicPodcastsCaptionsEnabledState.collectAsState()
    val isPersistentPreviewEnabled by authRepo.isPersistentPreviewEnabledState.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentTitle by playerManager.currentTitle.collectAsState()
    val isLiveStream by playerManager.isLiveStream.collectAsState()
    val currentStreamUrl by playerManager.currentStreamUrl.collectAsState()
    val isMediaActive = currentStreamUrl.isNotBlank() || isPlaying

    // Target Navigation States from NowScreen
    var targetChannelId by remember { mutableStateOf<Int?>(null) }
    var targetLiveCategoryId by remember { mutableStateOf<String?>(null) }
    var targetMovieId by remember { mutableStateOf<Int?>(null) }
    var targetMovieCategoryId by remember { mutableStateOf<String?>(null) }
    var targetSeriesId by remember { mutableStateOf<Int?>(null) }
    var targetSeriesCategoryId by remember { mutableStateOf<String?>(null) }
    var targetPodcastEpisode by remember { mutableStateOf<PodcastEpisode?>(null) }

    // Sync VOD, YouTube and Live TV fullscreen states and next-item callbacks with MainActivity for remote key interception
    LaunchedEffect(fullscreenMedia, isLiveTvFullscreen, fullscreenYouTube) {
        MainActivity.isVODFullscreenActive = (fullscreenMedia != null)
        MainActivity.isLiveFullscreenActive = isLiveTvFullscreen
        MainActivity.onNextEpisodeCallback = fullscreenMedia?.onNextEpisode
        MainActivity.onNextYouTubeCallback = fullscreenYouTube?.onNextVideo
        MainActivity.onPreviousYouTubeCallback = fullscreenYouTube?.onPreviousVideo
    }

    // Auto-vet credentials against the 4 approved portals on app startup
    LaunchedEffect(Unit) {
        if (authRepo.hasValidCredentials()) {
            val u = authRepo.getActiveUsername()
            val p = authRepo.getActivePassword()
            val portals = authRepo.getOrderedServerPortals()
            val jobs = portals.map { portal ->
                async(Dispatchers.IO) {
                    portal to apiClient.testCredentials(portal, u, p)
                }
            }
            val results = jobs.awaitAll()
            val match = results.firstOrNull { it.second.isValid }
            if (match != null) {
                val (activePortal, _) = match
                authRepo.setLivePortalUrl(activePortal)
                authRepo.setVodPortalUrl(activePortal)
                authRepo.setCredentialsVerified(true)
            }
        }
    }

    fun switchTab(newTab: AppTab) {
        if (activeTab != newTab) {
            val isPersistent = authRepo.isPersistentPreviewEnabled()
            if (!isPersistent) {
                playerManager.stop()
                YouTubeRemoteBridge.activeWebView?.let { wv ->
                    try {
                        wv.onPause()
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                    } catch (_: Exception) {}
                }
                YouTubeRemoteBridge.activeWebView = null
            }
            fullscreenYouTube = null
            fullscreenMedia = null
            isLiveTvFullscreen = false
            activeTab = newTab
        }
    }

    val expandCurrentMedia: () -> Unit = {
        if (playerManager.activeYouTubeVideoId.value != null) {
            fullscreenYouTube = FullscreenYouTubeState(
                videoId = playerManager.activeYouTubeVideoId.value!!,
                title = playerManager.activeYouTubeTitle.value ?: "YouTube Media"
            )
        } else if (playerManager.currentStreamUrl.value.isNotBlank()) {
            if (playerManager.isLiveStream.value) {
                switchTab(AppTab.LIVE)
                isLiveTvFullscreen = true
            } else {
                fullscreenMedia = FullscreenMediaState(
                    url = playerManager.currentStreamUrl.value,
                    title = playerManager.currentTitle.value
                )
            }
        }
    }

    // Hierarchical Back Button Handler
    BackHandler(enabled = fullscreenMedia != null || fullscreenYouTube != null || isLiveTvFullscreen || activeTab != AppTab.LIVE) {
        if (fullscreenMedia != null) {
            fullscreenMedia = null
        } else if (fullscreenYouTube != null) {
            fullscreenYouTube = null
        } else if (isLiveTvFullscreen) {
            isLiveTvFullscreen = false
        } else if (activeTab != AppTab.LIVE) {
            switchTab(AppTab.LIVE)
        }
    }

    // Handle tab change: stop playback if persistent preview is disabled
    LaunchedEffect(activeTab) {
        val isPersistent = authRepo.isPersistentPreviewEnabled()
        if (!isPersistent) {
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
                                        painter = androidx.compose.ui.res.painterResource(id = com.tvdinner.R.drawable.app_logo),
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
                                        onClick = { switchTab(tab) },
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
                        if (activeTab == AppTab.SETTINGS) {
                            SettingsScreen(
                                authRepo = authRepo,
                                catalogManager = catalogManager,
                                playerManager = playerManager,
                                onSignOut = onSignOut,
                                onCredentialsChanged = { credentialsRevision++ },
                                onVerificationSuccess = {
                                    coroutineScope.launch {
                                        delay(1200)
                                        switchTab(AppTab.LIVE)
                                    }
                                }
                            )
                        } else {
                            key(credentialsRevision) {
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
                                        },
                                        onOpenSettings = { switchTab(AppTab.SETTINGS) },
                                        targetChannelId = targetChannelId,
                                        targetCategoryId = targetLiveCategoryId,
                                        onTargetChannelConsumed = {
                                            targetChannelId = null
                                            targetLiveCategoryId = null
                                        }
                                    )
                                    AppTab.MOVIES -> MoviesScreen(
                                        authRepo = authRepo,
                                        apiClient = apiClient,
                                        catalogManager = catalogManager,
                                        playerManager = playerManager,
                                        onExpandPreview = expandCurrentMedia,
                                        isPlayingFullscreen = (fullscreenMedia != null),
                                        onPlayMovie = { url, title, startPos, streamKey ->
                                            YouTubeRemoteBridge.activeWebView = null
                                            fullscreenYouTube = null
                                            isLiveTvFullscreen = false
                                            playerManager.playStream(url, title, isLive = false, startPositionMs = startPos, streamKey = streamKey)
                                            fullscreenMedia = FullscreenMediaState(
                                                url = url,
                                                title = title,
                                                onNextEpisode = null,
                                                nextEpisodeTitle = null
                                            )
                                        },
                                        onOpenSettings = { switchTab(AppTab.SETTINGS) },
                                        targetMovieId = targetMovieId,
                                        targetCategoryId = targetMovieCategoryId,
                                        onTargetMovieConsumed = {
                                            targetMovieId = null
                                            targetMovieCategoryId = null
                                        }
                                    )
                                    AppTab.SERIES -> SeriesScreen(
                                        authRepo = authRepo,
                                        apiClient = apiClient,
                                        catalogManager = catalogManager,
                                        playerManager = playerManager,
                                        onExpandPreview = expandCurrentMedia,
                                        isPlayingFullscreen = (fullscreenMedia != null),
                                        onPlayEpisode = { url, title, startPos, streamKey, onNext, nextTitle ->
                                            YouTubeRemoteBridge.activeWebView = null
                                            fullscreenYouTube = null
                                            isLiveTvFullscreen = false
                                            playerManager.playStream(url, title, isLive = false, startPositionMs = startPos, streamKey = streamKey)
                                            fullscreenMedia = FullscreenMediaState(
                                                url = url,
                                                title = title,
                                                onNextEpisode = onNext,
                                                nextEpisodeTitle = nextTitle
                                            )
                                        },
                                        onOpenSettings = { switchTab(AppTab.SETTINGS) },
                                        targetSeriesId = targetSeriesId,
                                        targetCategoryId = targetSeriesCategoryId,
                                        onTargetSeriesConsumed = {
                                            targetSeriesId = null
                                            targetSeriesCategoryId = null
                                        }
                                    )
                                    AppTab.MUSIC -> MusicScreen(
                                        authRepo = authRepo,
                                        catalogManager = catalogManager,
                                        playerManager = playerManager,
                                        onExpandPreview = expandCurrentMedia,
                                        onPlayYouTubeVideo = { videoId, title, onNext, nextTitle, onPrev ->
                                            fullscreenMedia = null
                                            isLiveTvFullscreen = false
                                            playerManager.setYouTubeMedia(videoId, title)
                                            fullscreenYouTube = FullscreenYouTubeState(videoId, title, onNext, nextTitle, onPrev)
                                        },
                                        onOpenSettings = { switchTab(AppTab.SETTINGS) }
                                    )
                                    AppTab.PODCASTS -> PodcastsScreen(
                                        authRepo = authRepo,
                                        catalogManager = catalogManager,
                                        playerManager = playerManager,
                                        onExpandPreview = expandCurrentMedia,
                                        onPlayYouTubeVideo = { videoId, title, onNext, nextTitle, onPrev ->
                                            fullscreenMedia = null
                                            isLiveTvFullscreen = false
                                            playerManager.setYouTubeMedia(videoId, title)
                                            fullscreenYouTube = FullscreenYouTubeState(videoId, title, onNext, nextTitle, onPrev)
                                        },
                                        onOpenSettings = { switchTab(AppTab.SETTINGS) },
                                        targetEpisode = targetPodcastEpisode,
                                        onTargetEpisodeConsumed = {
                                            targetPodcastEpisode = null
                                        }
                                    )
                                    AppTab.SETTINGS -> {}
                                }
                            }
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
                                            switchTab(tab)
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
                        if (activeTab == AppTab.SETTINGS) {
                            SettingsScreen(
                                authRepo = authRepo,
                                catalogManager = catalogManager,
                                playerManager = playerManager,
                                onSignOut = onSignOut,
                                onCredentialsChanged = { credentialsRevision++ },
                                onVerificationSuccess = {
                                    coroutineScope.launch {
                                        delay(1200)
                                        switchTab(AppTab.LIVE)
                                    }
                                }
                            )
                        } else {
                            key(credentialsRevision) {
                                when (activeTab) {

                                    AppTab.LIVE -> LiveTvScreen(
                                        authRepo = authRepo,
                                        apiClient = apiClient,
                                        catalogManager = catalogManager,
                                        playerManager = playerManager,
                                        isFullscreen = isLiveTvFullscreen,
                                        onToggleFullscreen = { isLiveTvFullscreen = it },
                                        onOpenSettings = { switchTab(AppTab.SETTINGS) },
                                        targetChannelId = targetChannelId,
                                        targetCategoryId = targetLiveCategoryId,
                                        onTargetChannelConsumed = {
                                            targetChannelId = null
                                            targetLiveCategoryId = null
                                        }
                                    )
                                    AppTab.MOVIES -> MoviesScreen(
                                        authRepo = authRepo,
                                        apiClient = apiClient,
                                        catalogManager = catalogManager,
                                        playerManager = playerManager,
                                        onExpandPreview = expandCurrentMedia,
                                        isPlayingFullscreen = (fullscreenMedia != null),
                                        onPlayMovie = { url, title, startPos, streamKey ->
                                            fullscreenYouTube = null
                                            isLiveTvFullscreen = false
                                            playerManager.playStream(url, title, isLive = false, startPositionMs = startPos, streamKey = streamKey)
                                            fullscreenMedia = FullscreenMediaState(
                                                url = url,
                                                title = title,
                                                onNextEpisode = null,
                                                nextEpisodeTitle = null
                                            )
                                        },
                                        onOpenSettings = { switchTab(AppTab.SETTINGS) },
                                        targetMovieId = targetMovieId,
                                        targetCategoryId = targetMovieCategoryId,
                                        onTargetMovieConsumed = {
                                            targetMovieId = null
                                            targetMovieCategoryId = null
                                        }
                                    )
                                    AppTab.SERIES -> SeriesScreen(
                                        authRepo = authRepo,
                                        apiClient = apiClient,
                                        catalogManager = catalogManager,
                                        playerManager = playerManager,
                                        onExpandPreview = expandCurrentMedia,
                                        isPlayingFullscreen = (fullscreenMedia != null),
                                        onPlayEpisode = { url, title, startPos, streamKey, onNext, nextTitle ->
                                            fullscreenYouTube = null
                                            isLiveTvFullscreen = false
                                            playerManager.playStream(url, title, isLive = false, startPositionMs = startPos, streamKey = streamKey)
                                            fullscreenMedia = FullscreenMediaState(
                                                url = url,
                                                title = title,
                                                onNextEpisode = onNext,
                                                nextEpisodeTitle = nextTitle
                                            )
                                        },
                                        onOpenSettings = { switchTab(AppTab.SETTINGS) },
                                        targetSeriesId = targetSeriesId,
                                        targetCategoryId = targetSeriesCategoryId,
                                        onTargetSeriesConsumed = {
                                            targetSeriesId = null
                                            targetSeriesCategoryId = null
                                        }
                                    )
                                    AppTab.MUSIC -> MusicScreen(
                                        authRepo = authRepo,
                                        catalogManager = catalogManager,
                                        playerManager = playerManager,
                                        onExpandPreview = expandCurrentMedia,
                                        onPlayYouTubeVideo = { videoId, title, onNext, nextTitle, onPrev ->
                                            fullscreenMedia = null
                                            isLiveTvFullscreen = false
                                            playerManager.setYouTubeMedia(videoId, title)
                                            fullscreenYouTube = FullscreenYouTubeState(videoId, title, onNext, nextTitle, onPrev)
                                        },
                                        onOpenSettings = { switchTab(AppTab.SETTINGS) }
                                    )
                                    AppTab.PODCASTS -> PodcastsScreen(
                                        authRepo = authRepo,
                                        catalogManager = catalogManager,
                                        playerManager = playerManager,
                                        onExpandPreview = expandCurrentMedia,
                                        onPlayYouTubeVideo = { videoId, title, onNext, nextTitle, onPrev ->
                                            fullscreenMedia = null
                                            isLiveTvFullscreen = false
                                            playerManager.setYouTubeMedia(videoId, title)
                                            fullscreenYouTube = FullscreenYouTubeState(videoId, title, onNext, nextTitle, onPrev)
                                        },
                                        onOpenSettings = { switchTab(AppTab.SETTINGS) },
                                        targetEpisode = targetPodcastEpisode,
                                        onTargetEpisodeConsumed = {
                                            targetPodcastEpisode = null
                                        }
                                    )
                                    AppTab.SETTINGS -> {}
                                }
                            }
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
                captionsEnabled = isMusicCaptionsEnabled,
                onBack = {
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
