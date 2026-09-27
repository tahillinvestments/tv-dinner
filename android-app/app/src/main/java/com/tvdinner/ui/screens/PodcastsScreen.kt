package com.tvdinner.ui.screens

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.tvdinner.data.model.PodcastChannel
import com.tvdinner.data.model.PodcastEpisode
import com.tvdinner.data.podcasts.PodcastsData
import com.tvdinner.data.repository.AuthRepository
import com.tvdinner.data.repository.CatalogManager
import com.tvdinner.data.repository.YouTubeQueueItem
import com.tvdinner.ui.components.AccessRestrictedView
import com.tvdinner.ui.components.AppSearchBar
import com.tvdinner.ui.components.TvFocusableCard
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.LazyColumn
import com.tvdinner.player.ExoPlayerManager
import com.tvdinner.ui.components.UniversalIntegratedPreview
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import com.tvdinner.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PodcastsScreen(
    authRepo: AuthRepository,
    catalogManager: CatalogManager,
    onPlayYouTubeVideo: (String, String, (() -> Unit)?, String?, (() -> Unit)?) -> Unit,
    playerManager: ExoPlayerManager? = null,
    onExpandPreview: () -> Unit = {},
    isPlayingFullscreen: Boolean = false,
    onOpenSettings: (() -> Unit)? = null,
    onRequestFocusSidebar: (() -> Unit)? = null,
    previewFocusRequester: FocusRequester? = null,
    targetEpisode: PodcastEpisode? = null,
    onTargetEpisodeConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isCredentialsVerified by authRepo.isCredentialsVerifiedState.collectAsState()
    val activeUsername by authRepo.activeUsernameState.collectAsState()
    val activePassword by authRepo.activePasswordState.collectAsState()
    val isAccessAllowed = activeUsername.isNotBlank() && activePassword.isNotBlank() && isCredentialsVerified

    val targetPodcastFocusRequester = remember { FocusRequester() }
    val podcastsPreviewFocus = previewFocusRequester ?: remember { FocusRequester() }
    val selectedCategoryFocusRequester = remember { FocusRequester() }
    val firstCategoryFocusRequester = remember { FocusRequester() }
    val searchBarFocusRequester = remember { FocusRequester() }
    val firstContentFocusRequester = remember { FocusRequester() }
    val visiblePodcastFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    val categories = listOf(
        "🔥 Trending",
        "⭐ Subscribed",
        "🕒 History",
        "🤖 AI & Tech",
        "💼 Business & Ideas",
        "🧠 Science & Health",
        "🎙️ Culture & Talk",
        "📰 News & Politics",
        "🔍 True Crime & Mystery"
    )
    var selectedCategory by remember { mutableStateOf(catalogManager.podcastSelectedCategory) }
    var searchQuery by remember { mutableStateOf(catalogManager.podcastSearchQuery) }
    var debouncedQuery by remember { mutableStateOf(catalogManager.podcastSearchQuery) }
    var isInitialMount by remember { mutableStateOf(true) }

    var liveChannels by remember { mutableStateOf<List<PodcastChannel>>(catalogManager.podcastLiveChannels) }
    var mainFeedEpisodes by remember { mutableStateOf<List<PodcastEpisode>>(catalogManager.podcastMainEpisodes) }
    var liveEpisodes by remember { mutableStateOf<List<PodcastEpisode>>(catalogManager.podcastLiveEpisodes) }
    var selectedChannel by remember { mutableStateOf<PodcastChannel?>(catalogManager.podcastSelectedChannel) }
    var isLoading by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // Deep navigation from Now page: open podcast channel and scroll/focus target episode
    LaunchedEffect(targetEpisode) {
        if (targetEpisode != null) {
            val matchingChannel = liveChannels.firstOrNull { it.channelName.equals(targetEpisode.channelName, ignoreCase = true) }
                ?: PodcastsData.CHANNELS.firstOrNull { it.channelName.equals(targetEpisode.channelName, ignoreCase = true) }
            if (matchingChannel != null && selectedChannel != matchingChannel) {
                selectedChannel = matchingChannel
            }
        }
    }

    LaunchedEffect(targetEpisode, liveEpisodes) {
        if (targetEpisode != null && liveEpisodes.isNotEmpty()) {
            val idx = liveEpisodes.indexOfFirst { it.videoId == targetEpisode.videoId }
            if (idx >= 0) {
                gridState.scrollToItem(idx)
                delay(150)
                try {
                    targetPodcastFocusRequester.requestFocus()
                } catch (_: Exception) {}
                onTargetEpisodeConsumed()
            }
        }
    }

    // Remote Back-button handler: return to previous view (whether search results or category feeds)
    BackHandler(enabled = isAccessAllowed && selectedChannel != null) {
        selectedChannel = null
    }

    // Pagination / Infinite Scroll States
    var currentPage by remember { mutableIntStateOf(1) }
    var isFetchingMore by remember { mutableStateOf(false) }
    var canLoadMore by remember { mutableStateOf(true) }
    var subscribedIds by remember { mutableStateOf(authRepo.getSubscribedPodcastIds()) }

    suspend fun fetchMoreEpisodesInternal() {
        if (!isAccessAllowed || isLoading || isFetchingMore || !canLoadMore) return
        isFetchingMore = true
        var targetPage = currentPage + 1
        var freshBatch = emptyList<PodcastEpisode>()
        val currentIds = liveEpisodes.map { it.id }.toSet()

        // Try up to 4 consecutive pages to find fresh, non-duplicate episodes
        for (attempt in 0..3) {
            val nextBatch = if (selectedChannel != null) {
                catalogManager.getPodcastEpisodesForChannelNextPage(selectedChannel!!, page = targetPage)
            } else if (debouncedQuery.isNotBlank()) {
                catalogManager.searchPodcastEpisodesNextPage(debouncedQuery, page = targetPage)
            } else {
                val catClean = selectedCategory.replace(Regex("[^a-zA-Z &]"), "").trim()
                catalogManager.getLivePodcastEpisodesNextPage(catClean, page = targetPage)
            }
            val unique = nextBatch.filter { !currentIds.contains(it.id) }
            if (unique.isNotEmpty()) {
                freshBatch = unique
                break
            }
            targetPage++
        }

        if (freshBatch.isNotEmpty()) {
            val updated = liveEpisodes + freshBatch
            liveEpisodes = updated
            currentPage = targetPage
            val additionalQueueItems = freshBatch.map {
                YouTubeQueueItem(
                    videoId = it.videoId,
                    title = "${it.channelName} - ${it.title}",
                    subtitle = it.channelName,
                    artworkUrl = it.thumbnailUrl
                )
            }
            catalogManager.activeYouTubeQueue = catalogManager.activeYouTubeQueue + additionalQueueItems
        } else {
            currentPage = targetPage
            if (currentPage > 80) {
                canLoadMore = false
            }
        }
        isFetchingMore = false
    }

    var playEpisodeAtIndex: (Int) -> Unit = {}

    val playNextPodcastEpisode: () -> Unit = {
        val nextItem = catalogManager.advanceYouTubeQueue()
        if (nextItem != null) {
            playerManager?.setYouTubeMedia(nextItem.videoId, nextItem.title)
            val curIdx = catalogManager.activeYouTubeQueueIndex
            if (curIdx in liveEpisodes.indices) {
                authRepo.addPodcastToHistory(liveEpisodes[curIdx])
            }
            if (curIdx >= liveEpisodes.size - 4 && canLoadMore && !isFetchingMore) {
                coroutineScope.launch {
                    fetchMoreEpisodesInternal()
                }
            }
        } else {
            if (canLoadMore && !isFetchingMore) {
                coroutineScope.launch {
                    fetchMoreEpisodesInternal()
                    val retryNext = catalogManager.advanceYouTubeQueue()
                    if (retryNext != null) {
                        playerManager?.setYouTubeMedia(retryNext.videoId, retryNext.title)
                        val curIdx = catalogManager.activeYouTubeQueueIndex
                        if (curIdx in liveEpisodes.indices) {
                            authRepo.addPodcastToHistory(liveEpisodes[curIdx])
                        }
                    } else if (liveEpisodes.isNotEmpty()) {
                        playEpisodeAtIndex(0)
                    }
                }
            } else if (liveEpisodes.isNotEmpty()) {
                playEpisodeAtIndex(0)
            }
        }
    }

    val playPrevPodcastEpisode: () -> Unit = {
        val prevItem = catalogManager.retreatYouTubeQueue()
        if (prevItem != null) {
            playerManager?.setYouTubeMedia(prevItem.videoId, prevItem.title)
            val curIdx = catalogManager.activeYouTubeQueueIndex
            if (curIdx in liveEpisodes.indices) {
                authRepo.addPodcastToHistory(liveEpisodes[curIdx])
            }
        } else if (liveEpisodes.isNotEmpty()) {
            playEpisodeAtIndex(liveEpisodes.size - 1)
        }
    }

    playEpisodeAtIndex = { index ->
        if (isAccessAllowed && index in liveEpisodes.indices) {
            val current = liveEpisodes[index]

            // Already playing in preview -> expand directly to fullscreen!
            if (playerManager?.activeYouTubeVideoId?.value == current.videoId) {
                onExpandPreview()
            } else {
                authRepo.addPodcastToHistory(current)

                val queueItems = liveEpisodes.map {
                    YouTubeQueueItem(
                        videoId = it.videoId,
                        title = "${it.channelName} - ${it.title}",
                        subtitle = it.channelName,
                        artworkUrl = it.thumbnailUrl
                    )
                }
                catalogManager.setYouTubeQueue(queueItems, index)

                if (index >= liveEpisodes.size - 4 && canLoadMore && !isFetchingMore) {
                    coroutineScope.launch {
                        fetchMoreEpisodesInternal()
                    }
                }

                val next = liveEpisodes.getOrNull(index + 1)
                onPlayYouTubeVideo(
                    current.videoId,
                    "${current.channelName} - ${current.title}",
                    playNextPodcastEpisode,
                    next?.let { "${it.channelName} - ${it.title}" },
                    playPrevPodcastEpisode
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

    // Helper to switch to any episode's channel
    val onNavigateToEpisodeChannel: (PodcastEpisode) -> Unit = { ep ->
        val cleanEpChan = ep.channelName.trim()
        val matching = liveChannels.find {
            it.channelName.equals(cleanEpChan, ignoreCase = true) ||
            it.channelName.contains(cleanEpChan, ignoreCase = true) ||
            cleanEpChan.contains(it.channelName, ignoreCase = true)
        } ?: PodcastsData.CHANNELS.find {
            it.channelName.equals(cleanEpChan, ignoreCase = true) ||
            it.channelName.contains(cleanEpChan, ignoreCase = true) ||
            cleanEpChan.contains(it.channelName, ignoreCase = true)
        } ?: PodcastChannel(
            id = ep.channelId.ifBlank { "chan_${cleanEpChan.replace(" ", "_").lowercase()}" },
            channelName = cleanEpChan,
            host = cleanEpChan,
            category = selectedCategory,
            subscribers = "YouTube Podcast Channel",
            avatar = ep.thumbnailUrl,
            description = "Episodes from $cleanEpChan",
            ytChannelId = ""
        )
        selectedChannel = matching
    }

    // Debounce search input (350ms)
    LaunchedEffect(searchQuery) {
        delay(350)
        debouncedQuery = searchQuery.trim()
    }

    // Fetch live data for selected category or search query
    LaunchedEffect(selectedCategory, debouncedQuery, isAccessAllowed) {
        if (!isAccessAllowed) {
            liveChannels = emptyList()
            mainFeedEpisodes = emptyList()
            liveEpisodes = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        if (isInitialMount) {
            isInitialMount = false
            if (liveEpisodes.isNotEmpty() && debouncedQuery == catalogManager.podcastSearchQuery && selectedCategory == catalogManager.podcastSelectedCategory && selectedChannel == catalogManager.podcastSelectedChannel) {
                // Reusing preserved episodes and channels
                return@LaunchedEffect
            }
        }
        catalogManager.podcastSelectedCategory = selectedCategory
        catalogManager.podcastSearchQuery = debouncedQuery
        isLoading = true
        selectedChannel = null
        catalogManager.podcastSelectedChannel = null
        currentPage = 1
        canLoadMore = true

        if (debouncedQuery.isNotBlank()) {
            launch {
                try {
                    val channels = catalogManager.getLivePodcastChannels(debouncedQuery)
                    liveChannels = channels
                    catalogManager.podcastLiveChannels = channels
                } catch (_: Exception) {}
            }
            val eps = catalogManager.searchPodcastEpisodes(debouncedQuery, page = 1)
            mainFeedEpisodes = eps
            liveEpisodes = eps
            catalogManager.podcastMainEpisodes = eps
            catalogManager.podcastLiveEpisodes = eps
        } else if (selectedCategory == "🕒 History") {
            liveChannels = emptyList()
            catalogManager.podcastLiveChannels = emptyList()
            val eps = authRepo.getPodcastHistory()
            mainFeedEpisodes = eps
            liveEpisodes = eps
            catalogManager.podcastMainEpisodes = eps
            catalogManager.podcastLiveEpisodes = eps
            canLoadMore = false
        } else if (selectedCategory == "⭐ Subscribed") {
            val allChannels = mutableListOf<PodcastChannel>()
            val savedCustomChannels = authRepo.getSubscribedPodcastChannels()
            for (id in subscribedIds) {
                val match = PodcastsData.CHANNELS.find { it.id == id }
                    ?: savedCustomChannels.find { it.id == id }
                    ?: liveChannels.find { it.id == id }
                if (match != null && !allChannels.any { it.id == match.id }) {
                    allChannels.add(match)
                }
            }
            liveChannels = allChannels
            catalogManager.podcastLiveChannels = allChannels
            selectedChannel = null
            catalogManager.podcastSelectedChannel = null
            if (allChannels.isNotEmpty()) {
                val allEpisodes = mutableListOf<PodcastEpisode>()
                for (ch in allChannels) {
                    try {
                        val eps = catalogManager.getPodcastEpisodesForChannel(ch)
                        allEpisodes.addAll(eps.take(8))
                    } catch (_: Exception) {}
                }
                val mixed = com.tvdinner.data.podcasts.PodcastsData.interleaveEpisodes(allEpisodes, maxConsecutive = 1)
                mainFeedEpisodes = mixed
                liveEpisodes = mixed
                catalogManager.podcastMainEpisodes = mixed
                catalogManager.podcastLiveEpisodes = mixed
            } else {
                mainFeedEpisodes = emptyList()
                liveEpisodes = emptyList()
                catalogManager.podcastMainEpisodes = emptyList()
                catalogManager.podcastLiveEpisodes = emptyList()
            }
        } else {
            val catClean = selectedCategory.replace(Regex("[^a-zA-Z &]"), "").trim()
            // 1. Immediately provide instant curated episodes for this category so UI responds without delay
            val instantCurated = com.tvdinner.data.podcasts.PodcastsData.getCuratedEpisodesForCategory(catClean)
            if (instantCurated.isNotEmpty()) {
                mainFeedEpisodes = instantCurated
                liveEpisodes = instantCurated
                catalogManager.podcastMainEpisodes = instantCurated
                catalogManager.podcastLiveEpisodes = instantCurated
            }
            // 2. Load channels in parallel non-blocking
            launch {
                try {
                    val channels = catalogManager.getLivePodcastChannels(catClean)
                    liveChannels = channels
                    catalogManager.podcastLiveChannels = channels
                } catch (_: Exception) {}
            }
            // 3. Fetch live episodes and interleave
            val eps = catalogManager.getLivePodcastEpisodes(catClean)
            if (eps.isNotEmpty()) {
                val mixed = com.tvdinner.data.podcasts.PodcastsData.interleaveEpisodes(eps, maxConsecutive = 1)
                mainFeedEpisodes = mixed
                liveEpisodes = mixed
                catalogManager.podcastMainEpisodes = mixed
                catalogManager.podcastLiveEpisodes = mixed
            }
        }
        isLoading = false
    }

    // When a channel is explicitly selected or unselected (via back button)
    LaunchedEffect(selectedChannel, isAccessAllowed) {
        if (!isAccessAllowed) return@LaunchedEffect
        if (selectedChannel != null) {
            isLoading = true
            currentPage = 1
            canLoadMore = true
            try {
                gridState.scrollToItem(0)
            } catch (_: Exception) {}
            val targetChannel = selectedChannel!!
            val eps = catalogManager.getPodcastEpisodesForChannel(targetChannel)
            if (eps.isNotEmpty()) {
                liveEpisodes = eps
            } else {
                // Fallback: show episodes from main feed that match this channel name
                val fallbackEps = mainFeedEpisodes.filter {
                    it.channelName.equals(targetChannel.channelName, ignoreCase = true) ||
                    it.channelName.contains(targetChannel.channelName, ignoreCase = true) ||
                    targetChannel.channelName.contains(it.channelName, ignoreCase = true)
                }
                liveEpisodes = fallbackEps
            }
            isLoading = false
        } else {
            // Restoring previous view (search results or category episodes)
            liveEpisodes = mainFeedEpisodes
            currentPage = 1
            canLoadMore = true
        }
    }


    // Continuous Endless Scrolling via snapshotFlow
    LaunchedEffect(gridState, selectedCategory, debouncedQuery, selectedChannel, isAccessAllowed) {
        if (!isAccessAllowed) return@LaunchedEffect
        snapshotFlow {
            val total = gridState.layoutInfo.totalItemsCount
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            Pair(total, last)
        }.collect { (total, last) ->
            if (total > 0 && last >= total - 12 && !isLoading && !isFetchingMore && canLoadMore) {
                fetchMoreEpisodesInternal()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CinemaBackground)) {
        if (!isAccessAllowed) {
            AccessRestrictedView(
                featureName = "Podcasts",
                onOpenSettings = onOpenSettings
            )
        } else {
            if (isMobile) {
                // Mobile Portrait Layout: Top 16:9 Integrated Preview + Content Column
                Column(modifier = Modifier.fillMaxSize()) {
                    if (playerManager != null) {
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
                                onNextVideo = playNextPodcastEpisode,
                                onPreviousVideo = playPrevPodcastEpisode,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
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
                                imageVector = Icons.Default.Podcasts,
                                contentDescription = null,
                                tint = CinemaPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "PODCASTS",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                        }

                        AppSearchBar(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search podcasts...",
                            onSearch = {
                                debouncedQuery = searchQuery.trim()
                                catalogManager.podcastSearchQuery = debouncedQuery
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (searchQuery.isBlank() && selectedChannel == null) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(categories) { cat ->
                                val isSelected = selectedCategory == cat
                                TvFocusableCard(
                                    onClick = {
                                        selectedChannel = null
                                        selectedCategory = cat
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    backgroundColor = if (isSelected) CinemaPrimary else CinemaSurfaceVariant,
                                    focusedBorderColor = CinemaFocus,
                                    focusedScale = 1.05f
                                ) {
                                    Text(
                                        text = cat,
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    PodcastContentList(
                        searchQuery = searchQuery,
                        selectedChannel = selectedChannel,
                        liveChannels = liveChannels,
                        liveEpisodes = liveEpisodes,
                        subscribedIds = subscribedIds,
                        isLoading = isLoading,
                        isFetchingMore = isFetchingMore,
                        gridState = gridState,
                        isMobile = true,
                        selectedCategory = selectedCategory,
                        targetEpisode = targetEpisode,
                        targetPodcastFocusRequester = targetPodcastFocusRequester,
                        onBackToAllShows = { selectedChannel = null },
                        onToggleSubscription = { ch ->
                            authRepo.togglePodcastSubscription(ch)
                            subscribedIds = authRepo.getSubscribedPodcastIds()
                        },
                        onSelectChannel = { selectedChannel = it },
                        onNavigateToEpisodeChannel = onNavigateToEpisodeChannel,
                        onPlayEpisodeAtIndex = { playEpisodeAtIndex(it) },
                        onFetchMore = {
                            coroutineScope.launch {
                                fetchMoreEpisodesInternal()
                            }
                        }
                    )
                }
            }
        } else {
                // TV / Landscape Layout: 280dp Vertical Sidebar with UniversalIntegratedPreview + Right Content
                Row(modifier = Modifier.fillMaxSize()) {
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
                            if (playerManager != null) {
                                UniversalIntegratedPreview(
                                    playerManager = playerManager,
                                    onExpand = onExpandPreview,
                                    onClose = {
                                        playerManager.stop()
                                        playerManager.clearYouTubeMedia()
                                        catalogManager.clearYouTubeQueue()
                                    },
                                    isPlayingFullscreen = isPlayingFullscreen,
                                    onNextVideo = playNextPodcastEpisode,
                                    onPreviousVideo = playPrevPodcastEpisode,
                                    onMoveLeft = { onRequestFocusSidebar?.invoke() },
                                    onMoveRight = {
                                        try {
                                            searchBarFocusRequester.requestFocus()
                                        } catch (_: Exception) {
                                            try {
                                                visiblePodcastFocusRequester.requestFocus()
                                            } catch (_: Exception) {
                                                try {
                                                    firstContentFocusRequester.requestFocus()
                                                } catch (_: Exception) {
                                                    try { focusManager.moveFocus(FocusDirection.Right) } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                    },
                                    onMoveDown = {
                                        try {
                                            selectedCategoryFocusRequester.requestFocus()
                                        } catch (_: Exception) {
                                            try {
                                                firstCategoryFocusRequester.requestFocus()
                                            } catch (_: Exception) {
                                                try { focusManager.moveFocus(FocusDirection.Down) } catch (_: Exception) {}
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .padding(bottom = 4.dp)
                                        .focusRequester(podcastsPreviewFocus)
                                )
                            }

                            Text(
                                text = "CATEGORIES",
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
                                itemsIndexed(categories) { catIndex, cat ->
                                    val isCurrentCat = (selectedCategory == cat)
                                    val isSelected = (searchQuery.isBlank() && selectedChannel == null && isCurrentCat)
                                    val isFirstCat = catIndex == 0
                                    TvFocusableCard(
                                        onClick = {
                                            selectedChannel = null
                                            selectedCategory = cat
                                            searchQuery = ""
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        backgroundColor = if (isSelected) CinemaPrimary else CinemaSurfaceVariant,
                                        focusedBorderColor = CinemaFocus,
                                        focusedScale = 1.04f,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusProperties {
                                                if (isFirstCat || catIndex == 0) {
                                                    up = FocusRequester.Cancel
                                                }
                                            }
                                            .then(if (isFirstCat) Modifier.focusRequester(firstCategoryFocusRequester) else Modifier)
                                            .then(if (isCurrentCat || isFirstCat) Modifier.focusRequester(selectedCategoryFocusRequester) else Modifier)
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.key == Key.DirectionUp && (isFirstCat || catIndex == 0)) {
                                                    true
                                                } else if (keyEvent.type == KeyEventType.KeyDown) {
                                                    when (keyEvent.key) {
                                                        Key.DirectionLeft -> {
                                                            try {
                                                                podcastsPreviewFocus.requestFocus()
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
                                                            } catch (_: Exception) {
                                                                try {
                                                                    visiblePodcastFocusRequester.requestFocus()
                                                                    true
                                                                } catch (_: Exception) {
                                                                    try {
                                                                        firstContentFocusRequester.requestFocus()
                                                                        true
                                                                    } catch (_: Exception) {
                                                                        focusManager.moveFocus(FocusDirection.Right)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        else -> false
                                                    }
                                                } else false
                                            }
                                    ) {
                                        Text(
                                            text = cat,
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

                    // Right Column: Search + Content Area
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Podcasts,
                                    contentDescription = null,
                                    tint = CinemaPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "PODCASTS",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CinemaRed.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaRed.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "LIVE FEEDS",
                                        color = CinemaRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            AppSearchBar(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = "Search podcast channels & shows...",
                                onSearch = {
                                    debouncedQuery = searchQuery.trim()
                                    catalogManager.podcastSearchQuery = debouncedQuery
                                },
                                onMoveLeft = {
                                    try {
                                        selectedCategoryFocusRequester.requestFocus()
                                    } catch (_: Exception) {
                                        try {
                                            firstCategoryFocusRequester.requestFocus()
                                        } catch (_: Exception) {
                                            focusManager.moveFocus(FocusDirection.Left)
                                        }
                                    }
                                },
                                onMoveRight = {
                                    try {
                                        visiblePodcastFocusRequester.requestFocus()
                                    } catch (_: Exception) {
                                        try {
                                            firstContentFocusRequester.requestFocus()
                                        } catch (_: Exception) {
                                            try {
                                                focusManager.moveFocus(FocusDirection.Down)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                },
                                onMoveDown = {
                                    try {
                                        visiblePodcastFocusRequester.requestFocus()
                                    } catch (_: Exception) {
                                        try {
                                            firstContentFocusRequester.requestFocus()
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

                        PodcastContentList(
                            searchQuery = searchQuery,
                            selectedChannel = selectedChannel,
                            liveChannels = liveChannels,
                            liveEpisodes = liveEpisodes,
                            subscribedIds = subscribedIds,
                            isLoading = isLoading,
                            isFetchingMore = isFetchingMore,
                            gridState = gridState,
                            isMobile = false,
                            selectedCategory = selectedCategory,
                            targetEpisode = targetEpisode,
                            targetPodcastFocusRequester = targetPodcastFocusRequester,
                            firstContentFocusRequester = firstContentFocusRequester,
                            visiblePodcastFocusRequester = visiblePodcastFocusRequester,
                            searchBarFocusRequester = searchBarFocusRequester,
                            onBackToAllShows = { selectedChannel = null },
                            onToggleSubscription = { ch ->
                                authRepo.togglePodcastSubscription(ch)
                                subscribedIds = authRepo.getSubscribedPodcastIds()
                            },
                            onSelectChannel = { selectedChannel = it },
                            onNavigateToEpisodeChannel = onNavigateToEpisodeChannel,
                            onPlayEpisodeAtIndex = { playEpisodeAtIndex(it) },
                            onFetchMore = {
                                coroutineScope.launch {
                                    fetchMoreEpisodesInternal()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun PodcastContentList(
    searchQuery: String,
    selectedChannel: PodcastChannel?,
    liveChannels: List<PodcastChannel>,
    liveEpisodes: List<PodcastEpisode>,
    subscribedIds: Set<String>,
    isLoading: Boolean,
    isFetchingMore: Boolean,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    isMobile: Boolean,
    selectedCategory: String,
    targetEpisode: PodcastEpisode?,
    targetPodcastFocusRequester: FocusRequester,
    firstContentFocusRequester: FocusRequester? = null,
    visiblePodcastFocusRequester: FocusRequester? = null,
    searchBarFocusRequester: FocusRequester? = null,
    onBackToAllShows: () -> Unit,
    onToggleSubscription: (PodcastChannel) -> Unit,
    onSelectChannel: (PodcastChannel) -> Unit,
    onNavigateToEpisodeChannel: (PodcastEpisode) -> Unit,
    onPlayEpisodeAtIndex: (Int) -> Unit,
    onFetchMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val channelHeaderFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedChannel) {
        if (selectedChannel != null) {
            delay(100)
            try {
                channelHeaderFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isMobile) 8.dp else 12.dp)
    ) {
        if (searchQuery.isNotBlank()) {
            Text(
                text = "Episodes matching \"$searchQuery\"",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        // Channel Header Banner (When a specific channel is selected)
        if (selectedChannel != null) {
            val ch = selectedChannel
            val isSubscribed = subscribedIds.contains(ch.id)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CinemaSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TvFocusableCard(
                        onClick = onBackToAllShows,
                        shape = RoundedCornerShape(8.dp),
                        backgroundColor = CinemaSurfaceVariant,
                        focusedBorderColor = CinemaFocus,
                        modifier = Modifier.focusRequester(channelHeaderFocusRequester)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("All Shows", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (ch.avatar.isNotBlank()) {
                        AsyncImage(
                            model = ch.avatar,
                            contentDescription = ch.channelName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = ch.channelName,
                            color = CinemaAccent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${ch.host} • ${ch.subscribers} • Latest Episodes (Newest to Oldest)",
                            color = TextMuted,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    TvFocusableCard(
                        onClick = { onToggleSubscription(ch) },
                        shape = RoundedCornerShape(8.dp),
                        backgroundColor = if (isSubscribed) CinemaPrimary else CinemaSurfaceVariant,
                        focusedBorderColor = CinemaFocus
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isSubscribed) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Subscribe",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isSubscribed) "Subscribed" else "Subscribe",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Episode Grid
        if (isLoading && liveEpisodes.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CinemaAccent)
            }
        } else if (liveEpisodes.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (selectedCategory == "⭐ Subscribed") "No subscribed podcasts yet. Long press on any episode to see its channel and subscribe!" else if (selectedCategory == "🕒 History") "No recently played podcast episodes in your history." else "No live podcast episodes found",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (isMobile) 150.dp else 165.dp),
                state = gridState,
                verticalArrangement = Arrangement.spacedBy(if (isMobile) 10.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isMobile) 10.dp else 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusProperties {
                        exit = { direction ->
                            if (direction == FocusDirection.Down) FocusRequester.Cancel else FocusRequester.Default
                        }
                    }
            ) {
                itemsIndexed(liveEpisodes, key = { _, it -> it.id }) { index, ep ->
                    val idx = index
                    val numCols = if (isMobile) 2 else 6
                    val isBottomRow = index >= (liveEpisodes.size - numCols)
                    TvFocusableCard(
                        onClick = { onPlayEpisodeAtIndex(if (idx >= 0) idx else 0) },
                        onLongClick = { onNavigateToEpisodeChannel(ep) },
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = CinemaSurface,
                        focusedBorderColor = CinemaFocus,
                        focusedScale = 1.03f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .focusProperties {
                                if (isBottomRow) {
                                    down = FocusRequester.Cancel
                                }
                            }
                            .then(if (index == 0 && selectedChannel == null && firstContentFocusRequester != null) Modifier.focusRequester(firstContentFocusRequester) else Modifier)
                            .then(if (index == gridState.firstVisibleItemIndex && visiblePodcastFocusRequester != null) Modifier.focusRequester(visiblePodcastFocusRequester) else Modifier)
                            .then(if (ep.videoId == targetEpisode?.videoId) Modifier.focusRequester(targetPodcastFocusRequester) else Modifier)
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
                            // 16:9 Thumbnail
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            ) {
                                AsyncImage(
                                    model = ep.thumbnailUrl,
                                    contentDescription = ep.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.25f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Red,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Metadata
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = ep.title,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Action bar: Channel Name & Go to Channel & Date
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CinemaPrimary.copy(alpha = 0.15f),
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .clickable { onNavigateToEpisodeChannel(ep) }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountCircle,
                                                contentDescription = "Go to Channel",
                                                tint = CinemaAccent,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                text = ep.channelName,
                                                color = CinemaAccent,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Text(
                                        text = ep.published,
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom pagination loading indicator
                if (isFetchingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = CinemaAccent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
