package com.tvdinner.ui.screens

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tvdinner.data.model.MusicVideo
import com.tvdinner.data.repository.AuthRepository
import com.tvdinner.data.repository.CatalogManager
import com.tvdinner.data.repository.YouTubeQueueItem
import com.tvdinner.ui.components.AccessRestrictedView
import com.tvdinner.ui.components.AppSearchBar
import com.tvdinner.ui.components.TvFocusableCard
import com.tvdinner.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import com.tvdinner.player.ExoPlayerManager
import com.tvdinner.ui.components.UniversalIntegratedPreview

@Composable
fun MusicScreen(
    authRepo: AuthRepository,
    catalogManager: CatalogManager,
    playerManager: ExoPlayerManager,
    onPlayYouTubeVideo: (String, String, (() -> Unit)?, String?, (() -> Unit)?) -> Unit,
    onExpandPreview: () -> Unit = {},
    isPlayingFullscreen: Boolean = false,
    onOpenSettings: (() -> Unit)? = null,
    onRequestFocusSidebar: (() -> Unit)? = null,
    previewFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    val isCredentialsVerified by authRepo.isCredentialsVerifiedState.collectAsState()
    val activeUsername by authRepo.activeUsernameState.collectAsState()
    val activePassword by authRepo.activePasswordState.collectAsState()
    val isAccessAllowed = activeUsername.isNotBlank() && activePassword.isNotBlank() && isCredentialsVerified

    var searchQuery by remember { mutableStateOf(catalogManager.musicSearchQuery) }
    var debouncedQuery by remember { mutableStateOf(catalogManager.musicSearchQuery) }
    var selectedGenreId by remember { mutableStateOf(catalogManager.musicSelectedGenreId) }
    var isInitialMount by remember { mutableStateOf(true) }

    var videos by remember { mutableStateOf<List<MusicVideo>>(catalogManager.musicCurrentVideos) }
    var currentPage by remember { mutableIntStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val musicPreviewFocus = previewFocusRequester ?: remember { FocusRequester() }
    val selectedGenreFocusRequester = remember { FocusRequester() }
    val firstGenreFocusRequester = remember { FocusRequester() }
    val searchBarFocusRequester = remember { FocusRequester() }
    val firstVideoFocusRequester = remember { FocusRequester() }
    val visibleVideoFocusRequester = remember { FocusRequester() }

    suspend fun fetchMoreVideosInternal() {
        if (!isAccessAllowed || isLoading || isLoadingMore || !hasMore || selectedGenreId == "history") return
        isLoadingMore = true
        var targetPage = currentPage + 1
        var freshVideos = emptyList<MusicVideo>()
        val existingIds = videos.map { it.videoId }.toSet()

        // Try up to 4 consecutive pages to find fresh, non-duplicate videos
        for (attempt in 0..3) {
            val nextResults = if (debouncedQuery.isNotBlank()) {
                catalogManager.searchMusicVideos(debouncedQuery, page = targetPage)
            } else {
                catalogManager.getMusicForGenre(selectedGenreId, page = targetPage)
            }
            val fresh = nextResults.filter { !existingIds.contains(it.videoId) }
            if (fresh.isNotEmpty()) {
                freshVideos = fresh
                break
            }
            targetPage++
        }

        if (freshVideos.isNotEmpty()) {
            val updated = videos + freshVideos
            videos = updated
            catalogManager.musicCurrentVideos = updated
            currentPage = targetPage
            val additionalQueueItems = freshVideos.map {
                YouTubeQueueItem(
                    videoId = it.videoId,
                    title = "${it.artistName} - ${it.title}",
                    subtitle = it.artistName,
                    artworkUrl = it.thumbnailUrl
                )
            }
            catalogManager.activeYouTubeQueue = catalogManager.activeYouTubeQueue + additionalQueueItems
        } else {
            currentPage = targetPage
            if (currentPage > 80) {
                hasMore = false
            }
        }
        isLoadingMore = false
    }

    var playVideoAtIndex: (Int) -> Unit = {}

    val playNextMusicVideo: () -> Unit = {
        val nextItem = catalogManager.advanceYouTubeQueue()
        if (nextItem != null) {
            playerManager.setYouTubeMedia(nextItem.videoId, nextItem.title)
            val curIdx = catalogManager.activeYouTubeQueueIndex
            if (curIdx in videos.indices) {
                authRepo.addMusicToHistory(videos[curIdx])
            }
            if (curIdx >= videos.size - 4 && hasMore && !isLoadingMore && selectedGenreId != "history") {
                coroutineScope.launch {
                    fetchMoreVideosInternal()
                }
            }
        } else {
            // Queue exhausted: fetch more if available, otherwise wrap around to top of feed
            if (hasMore && !isLoadingMore && selectedGenreId != "history") {
                coroutineScope.launch {
                    fetchMoreVideosInternal()
                    val retryNext = catalogManager.advanceYouTubeQueue()
                    if (retryNext != null) {
                        playerManager.setYouTubeMedia(retryNext.videoId, retryNext.title)
                        val curIdx = catalogManager.activeYouTubeQueueIndex
                        if (curIdx in videos.indices) {
                            authRepo.addMusicToHistory(videos[curIdx])
                        }
                    } else if (videos.isNotEmpty()) {
                        playVideoAtIndex(0)
                    }
                }
            } else if (videos.isNotEmpty()) {
                playVideoAtIndex(0)
            }
        }
    }

    val playPrevMusicVideo: () -> Unit = {
        val prevItem = catalogManager.retreatYouTubeQueue()
        if (prevItem != null) {
            playerManager.setYouTubeMedia(prevItem.videoId, prevItem.title)
            val curIdx = catalogManager.activeYouTubeQueueIndex
            if (curIdx in videos.indices) {
                authRepo.addMusicToHistory(videos[curIdx])
            }
        } else if (videos.isNotEmpty()) {
            playVideoAtIndex(videos.size - 1)
        }
    }

    playVideoAtIndex = { index ->
        if (isAccessAllowed && index in videos.indices) {
            val current = videos[index]

            // Already playing in preview -> expand directly to fullscreen!
            if (playerManager.activeYouTubeVideoId.value == current.videoId) {
                onExpandPreview()
            } else {
                authRepo.addMusicToHistory(current)

                val queueItems = videos.map {
                    YouTubeQueueItem(
                        videoId = it.videoId,
                        title = "${it.artistName} - ${it.title}",
                        subtitle = it.artistName,
                        artworkUrl = it.thumbnailUrl
                    )
                }
                catalogManager.setYouTubeQueue(queueItems, index)

                if (index >= videos.size - 4 && hasMore && !isLoadingMore && selectedGenreId != "history") {
                    coroutineScope.launch {
                        fetchMoreVideosInternal()
                    }
                }
                val next = videos.getOrNull(index + 1)
                onPlayYouTubeVideo(
                    current.videoId,
                    "${current.artistName} - ${current.title}",
                    playNextMusicVideo,
                    next?.let { "${it.artistName} - ${it.title}" },
                    playPrevMusicVideo
                )
            }
        }
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uiModeManager = remember { context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager }
    val hasTouchScreen = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) }
    val isTv = remember {
        uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION) ||
        !hasTouchScreen
    }
    val isCompact = configuration.screenWidthDp < 600
    val isMobile = !isTv && (configuration.orientation == Configuration.ORIENTATION_PORTRAIT || isCompact)

    // Debounce search input (350ms)
    LaunchedEffect(searchQuery) {
        delay(350)
        debouncedQuery = searchQuery.trim()
    }

    // Initial Load & Category / Search Change Handler
    LaunchedEffect(debouncedQuery, selectedGenreId, isAccessAllowed) {
        if (!isAccessAllowed) {
            videos = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        if (isInitialMount) {
            isInitialMount = false
            if (videos.isNotEmpty() && debouncedQuery == catalogManager.musicSearchQuery && selectedGenreId == catalogManager.musicSelectedGenreId) {
                // Reusing preserved state from previous screen visit
                return@LaunchedEffect
            }
        }
        catalogManager.musicSearchQuery = debouncedQuery
        catalogManager.musicSelectedGenreId = selectedGenreId
        isLoading = true
        currentPage = 1
        hasMore = (selectedGenreId != "history")

        val initialResults = if (debouncedQuery.isNotBlank()) {
            catalogManager.searchMusicVideos(debouncedQuery, page = 1)
        } else if (selectedGenreId == "history") {
            authRepo.getMusicHistory()
        } else {
            catalogManager.getMusicForGenre(selectedGenreId, page = 1)
        }

        videos = initialResults
        catalogManager.musicCurrentVideos = initialResults
        isLoading = false
        if (gridState.firstVisibleItemIndex > 0) {
            gridState.scrollToItem(0)
        }
    }

    // Continuous Endless Scrolling via snapshotFlow
    LaunchedEffect(gridState, debouncedQuery, selectedGenreId, isAccessAllowed) {
        if (!isAccessAllowed) return@LaunchedEffect
        snapshotFlow {
            val total = gridState.layoutInfo.totalItemsCount
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            Pair(total, last)
        }.collect { (total, last) ->
            if (total > 0 && last >= total - 12 && !isLoading && !isLoadingMore && hasMore) {
                fetchMoreVideosInternal()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CinemaBackground)) {
        if (!isAccessAllowed) {
            AccessRestrictedView(
                featureName = "Music",
                onOpenSettings = onOpenSettings
            )
        } else {
            val allGenres = remember {
                listOf(
                    com.tvdinner.data.model.MusicGenre("history", "🕒 History", "🕒")
                ) + com.tvdinner.data.music.MusicData.GENRES
            }

            if (isMobile) {
                // Mobile Portrait Layout: Top 16:9 Integrated Preview + Content Column
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color.Black)
                    ) {
                        UniversalIntegratedPreview(
                            playerManager = playerManager,
                            onExpand = onExpandPreview,
                            onClose = {
                                playerManager.stop()
                                playerManager.clearYouTubeMedia()
                                catalogManager.clearYouTubeQueue()
                            },
                            isPlayingFullscreen = isPlayingFullscreen,
                            onNextVideo = playNextMusicVideo,
                            onPreviousVideo = playPrevMusicVideo,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = CinemaPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "MUSIC",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary
                                )
                            }

                            AppSearchBar(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = "Search songs, artists, videos...",
                                onSearch = { debouncedQuery = searchQuery.trim() },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(allGenres) { genre ->
                                val isSelected = (debouncedQuery.isBlank() && selectedGenreId == genre.id)
                                TvFocusableCard(
                                    onClick = {
                                        searchQuery = ""
                                        debouncedQuery = ""
                                        selectedGenreId = genre.id
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    backgroundColor = if (isSelected) CinemaPrimary else CinemaSurfaceVariant,
                                    focusedBorderColor = CinemaFocus,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = genre.name,
                                            color = if (isSelected) Color.White else TextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // Content Grid
                        MusicContentGrid(
                            isLoading = isLoading,
                            isLoadingMore = isLoadingMore,
                            selectedGenreId = selectedGenreId,
                            videos = videos,
                            gridState = gridState,
                            isMobile = true,
                            onFetchMore = {
                                coroutineScope.launch { fetchMoreVideosInternal() }
                            },
                            onPlayVideo = { playVideoAtIndex(it) }
                        )
                    }
                }
            } else {
                // TV / Desktop Layout: Dedicated Left Vertical Category Sidebar (280dp) + Right Content Grid
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left Column: Genres Vertical Sidebar with Integrated Preview
                    Surface(
                        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                        color = CinemaSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                        modifier = Modifier
                            .width(280.dp)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 12.dp, horizontal = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            UniversalIntegratedPreview(
                                playerManager = playerManager,
                                onExpand = onExpandPreview,
                                onClose = {
                                    playerManager.stop()
                                    playerManager.clearYouTubeMedia()
                                    catalogManager.clearYouTubeQueue()
                                },
                                isPlayingFullscreen = isPlayingFullscreen,
                                onNextVideo = playNextMusicVideo,
                                onPreviousVideo = playPrevMusicVideo,
                                onMoveLeft = { onRequestFocusSidebar?.invoke() },
                                onMoveRight = {
                                    try {
                                        searchBarFocusRequester.requestFocus()
                                    } catch (_: Exception) {
                                        try {
                                            visibleVideoFocusRequester.requestFocus()
                                        } catch (_: Exception) {
                                            try {
                                                firstVideoFocusRequester.requestFocus()
                                            } catch (_: Exception) {
                                                try { focusManager.moveFocus(FocusDirection.Right) } catch (_: Exception) {}
                                            }
                                        }
                                    }
                                },
                                onMoveDown = {
                                    try {
                                        selectedGenreFocusRequester.requestFocus()
                                    } catch (_: Exception) {
                                        try {
                                            firstGenreFocusRequester.requestFocus()
                                        } catch (_: Exception) {
                                            try { focusManager.moveFocus(FocusDirection.Down) } catch (_: Exception) {}
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .padding(bottom = 4.dp)
                                    .focusRequester(musicPreviewFocus)
                            )

                            Text(
                                text = "GENRES",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = CinemaAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .focusProperties {
                                        up = FocusRequester.Cancel
                                    }
                            ) {
                                itemsIndexed(allGenres) { genreIndex, genre ->
                                    val isCurrentGenre = (selectedGenreId == genre.id)
                                    val isSelected = (debouncedQuery.isBlank() && isCurrentGenre)
                                    val isFirstGenre = genreIndex == 0
                                    TvFocusableCard(
                                        onClick = {
                                            searchQuery = ""
                                            debouncedQuery = ""
                                            selectedGenreId = genre.id
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        backgroundColor = if (isSelected) CinemaPrimary else CinemaSurfaceVariant,
                                        focusedBorderColor = CinemaFocus,
                                        focusedScale = 1.04f,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusProperties {
                                                if (isFirstGenre || genreIndex == 0) {
                                                    up = FocusRequester.Cancel
                                                }
                                            }
                                            .then(if (isFirstGenre) Modifier.focusRequester(firstGenreFocusRequester) else Modifier)
                                            .then(if (isCurrentGenre || isFirstGenre) Modifier.focusRequester(selectedGenreFocusRequester) else Modifier)
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.key == Key.DirectionUp && (isFirstGenre || genreIndex == 0)) {
                                                    true
                                                } else if (keyEvent.type == KeyEventType.KeyDown) {
                                                    when (keyEvent.key) {
                                                        Key.DirectionLeft -> {
                                                            try {
                                                                musicPreviewFocus.requestFocus()
                                                                true
                                                            } catch (_: Exception) {
                                                                onRequestFocusSidebar?.invoke()
                                                                true
                                                            }
                                                        }
                                                        Key.DirectionRight -> {
                                                            try {
                                                                searchBarFocusRequester.requestFocus()
                                                                true
                                                            } catch (_: Exception) { false }
                                                        }
                                                        else -> false
                                                    }
                                                } else false
                                            }
                                    ) {
                                        Text(
                                            text = genre.name,
                                            color = if (isSelected) Color.White else TextSecondary,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Right Column: Search Bar + Results Header + Music Videos Grid
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Top Bar: Title & Search
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "MUSIC VIDEOS",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )

                            AppSearchBar(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = "Search songs, artists, albums, or music videos...",
                                onSearch = { debouncedQuery = searchQuery.trim() },
                                onMoveLeft = {
                                    try {
                                        selectedGenreFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                },
                                onMoveRight = {
                                    try {
                                        visibleVideoFocusRequester.requestFocus()
                                    } catch (_: Exception) {
                                        try {
                                            firstVideoFocusRequester.requestFocus()
                                        } catch (_: Exception) {
                                            try {
                                                focusManager.moveFocus(FocusDirection.Down)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                },
                                onMoveDown = {
                                    try {
                                        visibleVideoFocusRequester.requestFocus()
                                    } catch (_: Exception) {
                                        try {
                                            firstVideoFocusRequester.requestFocus()
                                        } catch (_: Exception) {
                                            try {
                                                focusManager.moveFocus(FocusDirection.Down)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(searchBarFocusRequester)
                            )
                        }

                        // Results Header Summary
                        if (debouncedQuery.isNotBlank()) {
                            Text(
                                text = "Results matching \"$debouncedQuery\"",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        } else if (selectedGenreId == "history") {
                            Text(
                                text = "Recently Played Music Videos",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = CinemaAccent
                            )
                        }

                        // Content Grid
                        MusicContentGrid(
                            isLoading = isLoading,
                            isLoadingMore = isLoadingMore,
                            selectedGenreId = selectedGenreId,
                            videos = videos,
                            gridState = gridState,
                            firstVideoFocusRequester = firstVideoFocusRequester,
                            visibleVideoFocusRequester = visibleVideoFocusRequester,
                            searchBarFocusRequester = searchBarFocusRequester,
                            isMobile = false,
                            onFetchMore = {
                                coroutineScope.launch { fetchMoreVideosInternal() }
                            },
                            onPlayVideo = { playVideoAtIndex(it) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun MusicContentGrid(
    isLoading: Boolean,
    isLoadingMore: Boolean,
    selectedGenreId: String,
    videos: List<MusicVideo>,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    firstVideoFocusRequester: FocusRequester? = null,
    visibleVideoFocusRequester: FocusRequester? = null,
    searchBarFocusRequester: FocusRequester? = null,
    isMobile: Boolean,
    onFetchMore: (() -> Unit)? = null,
    onPlayVideo: (Int) -> Unit
) {
    val focusManager = LocalFocusManager.current
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = CinemaAccent)
        }
    } else if (videos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicOff,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(40.dp)
                )
                Text(
                    text = if (selectedGenreId == "history") "No recently played music videos" else "No music videos found",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        }
    } else {
        val numCols = if (isMobile) 2 else 3
        LazyVerticalGrid(
            columns = GridCells.Fixed(numCols),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(if (isMobile) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (isMobile) 10.dp else 14.dp),
            modifier = Modifier
                .fillMaxSize()
                .focusProperties {
                    exit = { direction ->
                        if (direction == FocusDirection.Down) FocusRequester.Cancel else FocusRequester.Default
                    }
                }
        ) {
            gridItems(videos, key = { it.id }) { video ->
                val idx = videos.indexOfFirst { it.id == video.id }
                val isBottomRow = idx >= (videos.size - numCols)
                TvFocusableCard(
                    onClick = {
                        onPlayVideo(if (idx >= 0) idx else 0)
                    },
                    shape = RoundedCornerShape(10.dp),
                    backgroundColor = CinemaSurface,
                    focusedBorderColor = CinemaFocus,
                    focusedScale = 1.04f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties {
                            if (isBottomRow) {
                                down = FocusRequester.Cancel
                            }
                        }
                        .then(if (idx == 0 && firstVideoFocusRequester != null) Modifier.focusRequester(firstVideoFocusRequester) else Modifier)
                        .then(if (idx == gridState.firstVisibleItemIndex && visibleVideoFocusRequester != null) Modifier.focusRequester(visibleVideoFocusRequester) else Modifier)
                        .onPreviewKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionDown -> {
                                        if (isBottomRow) {
                                            onFetchMore?.invoke()
                                            true
                                        } else false
                                    }
                                    Key.DirectionLeft -> {
                                        try {
                                            val moved = focusManager.moveFocus(FocusDirection.Left)
                                            if (!moved && searchBarFocusRequester != null) {
                                                searchBarFocusRequester.requestFocus()
                                                true
                                            } else true
                                        } catch (_: Exception) {
                                            try {
                                                searchBarFocusRequester?.requestFocus()
                                                true
                                            } catch (_: Exception) { false }
                                        }
                                    }
                                    Key.DirectionUp -> {
                                        try {
                                            val moved = focusManager.moveFocus(FocusDirection.Up)
                                            if (!moved && searchBarFocusRequester != null) {
                                                searchBarFocusRequester.requestFocus()
                                                true
                                            } else true
                                        } catch (_: Exception) {
                                            try {
                                                searchBarFocusRequester?.requestFocus()
                                                true
                                            } catch (_: Exception) { false }
                                        }
                                    }
                                    else -> false
                                }
                            } else false
                        }
                ) {
                    Column {
                        // 16:9 Thumbnail Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = video.thumbnailUrl,
                                contentDescription = video.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Play Badge Overlay
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.65f),
                                modifier = Modifier.align(Alignment.Center).size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            // Duration / Info Badge
                            if (video.duration.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        text = video.duration,
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Video Details
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = video.title,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = video.artistName,
                                color = CinemaAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${video.views} • ${video.published}",
                                color = TextMuted,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Endless Scrolling Loading Spinner Footer
            if (isLoadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CinemaAccent,
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
            }
        }
    }
}
