package com.tvdinner.ui.screens

import android.app.UiModeManager
import android.content.Context
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
import com.tvdinner.ui.components.AccessRestrictedView
import com.tvdinner.ui.components.AppSearchBar
import com.tvdinner.ui.components.TvFocusableCard
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.LazyColumn
import com.tvdinner.player.ExoPlayerManager
import com.tvdinner.ui.components.UniversalIntegratedPreview
import com.tvdinner.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun PodcastsScreen(
    authRepo: AuthRepository,
    catalogManager: CatalogManager,
    onPlayYouTubeVideo: (String, String, (() -> Unit)?, String?, (() -> Unit)?) -> Unit,
    playerManager: ExoPlayerManager? = null,
    onExpandPreview: () -> Unit = {},
    onOpenSettings: (() -> Unit)? = null,
    targetEpisode: PodcastEpisode? = null,
    onTargetEpisodeConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isCredentialsVerified by authRepo.isCredentialsVerifiedState.collectAsState()
    val activeUsername by authRepo.activeUsernameState.collectAsState()
    val activePassword by authRepo.activePasswordState.collectAsState()
    val isAccessAllowed = activeUsername.isNotBlank() && activePassword.isNotBlank() && isCredentialsVerified

    val targetPodcastFocusRequester = remember { FocusRequester() }

    val categories = listOf(
        "🔥 Trending",
        "⭐ Subscribed",
        "🕒 History",
        "🤖 AI & Tech",
        "💼 Business & Ideas",
        "🧠 Science & Health",
        "🎙️ Culture & Talk",
        "📰 News & Politics"
    )
    var selectedCategory by rememberSaveable { mutableStateOf("🔥 Trending") }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    var liveChannels by remember { mutableStateOf<List<PodcastChannel>>(emptyList()) }
    var mainFeedEpisodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }
    var liveEpisodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<PodcastChannel?>(null) }
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

    fun playEpisodeAtIndex(index: Int) {
        if (!isAccessAllowed || index !in liveEpisodes.indices) return
        val current = liveEpisodes[index]
        authRepo.addPodcastToHistory(current)
        val next = liveEpisodes.getOrNull(index + 1)
        val onNext: (() -> Unit)? = if (index + 1 < liveEpisodes.size) {
            { playEpisodeAtIndex(index + 1) }
        } else {
            null
        }
        val onPrevious: (() -> Unit)? = if (index > 0) {
            { playEpisodeAtIndex(index - 1) }
        } else {
            null
        }
        onPlayYouTubeVideo(
            current.videoId,
            "${current.channelName} - ${current.title}",
            onNext,
            next?.let { "${it.channelName} - ${it.title}" },
            onPrevious
        )
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uiModeManager = remember { context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager }
    val isTv = remember { uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION }
    val isCompact = configuration.screenWidthDp < 600
    val isMobile = !isTv && (configuration.orientation == Configuration.ORIENTATION_PORTRAIT || isCompact)

    // Helper to switch to any episode's channel
    val onNavigateToEpisodeChannel: (PodcastEpisode) -> Unit = { ep ->
        val matching = liveChannels.find { it.channelName.equals(ep.channelName, ignoreCase = true) }
            ?: PodcastsData.CHANNELS.find { it.channelName.equals(ep.channelName, ignoreCase = true) }
            ?: PodcastChannel(
                id = ep.channelId,
                channelName = ep.channelName,
                host = ep.channelName,
                category = selectedCategory,
                subscribers = "YouTube Podcast Channel",
                avatar = ep.thumbnailUrl,
                description = "Episodes from ${ep.channelName}",
                ytChannelId = ""
            )
        selectedChannel = matching
    }

    // Fetch live data for selected category or search query
    LaunchedEffect(selectedCategory, searchQuery, isAccessAllowed) {
        if (!isAccessAllowed) {
            liveChannels = emptyList()
            mainFeedEpisodes = emptyList()
            liveEpisodes = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        selectedChannel = null
        currentPage = 1
        canLoadMore = true

        if (searchQuery.isNotBlank()) {
            val channels = catalogManager.getLivePodcastChannels(searchQuery)
            liveChannels = channels
            val eps = catalogManager.getLivePodcastEpisodes(searchQuery)
            mainFeedEpisodes = eps
            liveEpisodes = eps
        } else if (selectedCategory == "🕒 History") {
            liveChannels = emptyList()
            val eps = authRepo.getPodcastHistory()
            mainFeedEpisodes = eps
            liveEpisodes = eps
            canLoadMore = false
        } else if (selectedCategory == "⭐ Subscribed") {
            val allChannels = mutableListOf<PodcastChannel>()
            for (id in subscribedIds) {
                val curatedMatch = PodcastsData.CHANNELS.find { it.id == id }
                if (curatedMatch != null) {
                    allChannels.add(curatedMatch)
                }
            }
            liveChannels = allChannels
            if (allChannels.isNotEmpty()) {
                selectedChannel = allChannels.first()
                val eps = catalogManager.getPodcastEpisodesForChannel(allChannels.first())
                mainFeedEpisodes = eps
                liveEpisodes = eps
            } else {
                mainFeedEpisodes = emptyList()
                liveEpisodes = emptyList()
            }
        } else {
            val catClean = selectedCategory.replace(Regex("[^a-zA-Z &]"), "").trim()
            val channels = catalogManager.getLivePodcastChannels(catClean)
            liveChannels = channels
            val eps = catalogManager.getLivePodcastEpisodes(catClean)
            mainFeedEpisodes = eps
            liveEpisodes = eps
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
            val eps = catalogManager.getPodcastEpisodesForChannel(selectedChannel!!)
            if (eps.isNotEmpty()) {
                liveEpisodes = eps
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
    LaunchedEffect(gridState, selectedCategory, searchQuery, selectedChannel, isAccessAllowed) {
        if (!isAccessAllowed) return@LaunchedEffect
        snapshotFlow {
            val total = gridState.layoutInfo.totalItemsCount
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            Pair(total, last)
        }.collect { (total, last) ->
            if (total > 0 && last >= total - 8 && !isLoading && !isFetchingMore && canLoadMore) {
                isFetchingMore = true
                val nextPage = currentPage + 1
                val nextBatch = if (selectedChannel != null) {
                    catalogManager.getPodcastEpisodesForChannelNextPage(selectedChannel!!, page = nextPage)
                } else {
                    val queryParam = if (searchQuery.isNotBlank()) searchQuery else selectedCategory.replace(Regex("[^a-zA-Z &]"), "").trim()
                    catalogManager.getLivePodcastEpisodesNextPage(queryParam, page = nextPage)
                }
                if (nextBatch.isNotEmpty()) {
                    val currentIds = liveEpisodes.map { it.id }.toSet()
                    val uniqueNew = nextBatch.filter { !currentIds.contains(it.id) }
                    if (uniqueNew.isNotEmpty()) {
                        liveEpisodes = liveEpisodes + uniqueNew
                        currentPage = nextPage
                    } else {
                        val fallbackPage = nextPage + 1
                        val fallbackBatch = if (selectedChannel != null) {
                            catalogManager.getPodcastEpisodesForChannelNextPage(selectedChannel!!, page = fallbackPage)
                        } else {
                            val queryParam = if (searchQuery.isNotBlank()) searchQuery else selectedCategory.replace(Regex("[^a-zA-Z &]"), "").trim()
                            catalogManager.getLivePodcastEpisodesNextPage(queryParam, page = fallbackPage)
                        }
                        val uniqueFallback = fallbackBatch.filter { !currentIds.contains(it.id) }
                        if (uniqueFallback.isNotEmpty()) {
                            liveEpisodes = liveEpisodes + uniqueFallback
                            currentPage = fallbackPage
                        } else {
                            canLoadMore = false
                        }
                    }
                } else {
                    canLoadMore = false
                }
                isFetchingMore = false
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
                // Mobile Portrait Layout: Top Bar + LazyRow Categories + Content
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
                        onToggleSubscription = { id ->
                            authRepo.togglePodcastSubscription(id)
                            subscribedIds = authRepo.getSubscribedPodcastIds()
                        },
                        onSelectChannel = { selectedChannel = it },
                        onNavigateToEpisodeChannel = onNavigateToEpisodeChannel,
                        onPlayEpisodeAtIndex = { playEpisodeAtIndex(it) }
                    )
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
                                    onClose = { playerManager.stop() },
                                    modifier = Modifier.padding(bottom = 4.dp)
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
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) {
                                items(categories) { cat ->
                                    val isSelected = (searchQuery.isBlank() && selectedChannel == null && selectedCategory == cat)
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
                                        modifier = Modifier.fillMaxWidth()
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
                                modifier = Modifier.weight(1f)
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
                            onBackToAllShows = { selectedChannel = null },
                            onToggleSubscription = { id ->
                                authRepo.togglePodcastSubscription(id)
                                subscribedIds = authRepo.getSubscribedPodcastIds()
                            },
                            onSelectChannel = { selectedChannel = it },
                            onNavigateToEpisodeChannel = onNavigateToEpisodeChannel,
                            onPlayEpisodeAtIndex = { playEpisodeAtIndex(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

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
    onBackToAllShows: () -> Unit,
    onToggleSubscription: (String) -> Unit,
    onSelectChannel: (PodcastChannel) -> Unit,
    onNavigateToEpisodeChannel: (PodcastEpisode) -> Unit,
    onPlayEpisodeAtIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isMobile) 8.dp else 12.dp)
    ) {
        if (searchQuery.isNotBlank()) {
            Text(
                text = "Channels matching \"$searchQuery\"",
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
                        focusedBorderColor = CinemaFocus
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
                        onClick = { onToggleSubscription(ch.id) },
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
        } else if (liveChannels.isNotEmpty()) {
            // Channels Carousel (Browse channels in category or search)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(liveChannels, key = { it.id }) { ch ->
                    val isSelected = selectedChannel?.id == ch.id
                    val isSubscribed = subscribedIds.contains(ch.id)
                    TvFocusableCard(
                        onClick = { onSelectChannel(ch) },
                        shape = RoundedCornerShape(10.dp),
                        backgroundColor = if (isSelected) CinemaSurfaceLight else CinemaSurface,
                        focusedBorderColor = CinemaFocus,
                        focusedScale = 1.04f,
                        modifier = Modifier.width(240.dp).height(68.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (ch.avatar.isNotBlank()) {
                                AsyncImage(
                                    model = ch.avatar,
                                    contentDescription = ch.channelName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ch.channelName,
                                    color = if (isSelected) CinemaAccent else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = ch.host.ifBlank { ch.category },
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }

                            IconButton(
                                onClick = { onToggleSubscription(ch.id) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSubscribed) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Subscribe",
                                    tint = if (isSubscribed) CinemaAccent else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Episode Grid
        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CinemaAccent)
            }
        } else if (liveEpisodes.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (selectedCategory == "⭐ Subscribed") "No subscribed podcasts yet. Click the bookmark icon on any channel to save it here!" else if (selectedCategory == "🕒 History") "No recently played podcast episodes in your history." else "No live podcast episodes found",
                    color = TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = if (isMobile) 160.dp else 220.dp),
                state = gridState,
                verticalArrangement = Arrangement.spacedBy(if (isMobile) 10.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isMobile) 10.dp else 12.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(liveEpisodes, key = { it.id }) { ep ->
                    val idx = liveEpisodes.indexOfFirst { it.id == ep.id }
                    TvFocusableCard(
                        onClick = { onPlayEpisodeAtIndex(if (idx >= 0) idx else 0) },
                        shape = RoundedCornerShape(12.dp),
                        backgroundColor = CinemaSurface,
                        focusedBorderColor = CinemaFocus,
                        focusedScale = 1.03f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .then(if (ep.videoId == targetEpisode?.videoId) Modifier.focusRequester(targetPodcastFocusRequester) else Modifier)
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
