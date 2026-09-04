package com.tvdinner.ui.screens

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tvdinner.data.model.Channel
import com.tvdinner.data.model.LiveCategory
import com.tvdinner.data.model.ShortEpgResponse
import com.tvdinner.data.network.XtreamApiClient
import com.tvdinner.data.repository.AuthRepository
import com.tvdinner.data.repository.CatalogManager
import com.tvdinner.player.ExoPlayerManager
import com.tvdinner.ui.components.AppSearchBar
import com.tvdinner.ui.components.TvFocusableCard
import com.tvdinner.ui.player.NativePlayerView
import com.tvdinner.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun LiveTvScreen(
    authRepo: AuthRepository,
    apiClient: XtreamApiClient,
    catalogManager: CatalogManager,
    playerManager: ExoPlayerManager,
    isFullscreen: Boolean = false,
    onToggleFullscreen: (Boolean) -> Unit = {},
    contentFocusRequester: FocusRequester? = null,
    onRequestFocusSidebar: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<LiveCategory>>(emptyList()) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(authRepo.getLastLiveCategoryId()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeChannel by remember { mutableStateOf<Channel?>(null) }
    var activeFullEpg by remember { mutableStateOf<ShortEpgResponse?>(null) }
    var channelBannerChannel by remember { mutableStateOf<Channel?>(null) }
    var favoriteChannelIds by remember { mutableStateOf(authRepo.getFavoriteChannelIds()) }

    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentTitle by playerManager.currentTitle.collectAsState()
    val resizeMode by playerManager.resizeMode.collectAsState()
    val isCcEnabled by playerManager.isClosedCaptionsEnabled.collectAsState()
    val credentials = remember { authRepo.getActiveLiveCredentials() }

    val categoryListState = rememberLazyListState()
    val channelListState = rememberLazyListState()
    val previewInfoScrollState = rememberScrollState()
    val activeCardFocusRequester = remember { FocusRequester() }
    val visibleChannelFocusRequester = remember { FocusRequester() }
    val firstChannelFocusRequester = remember { FocusRequester() }
    var lastFocusedChannelIndex by remember { mutableIntStateOf(0) }
    val selectedCategoryFocusRequester = remember { FocusRequester() }
    val playControlFocusRequester = remember { FocusRequester() }
    val favoriteControlFocusRequester = remember { FocusRequester() }
    val ccControlFocusRequester = remember { FocusRequester() }
    val aspectControlFocusRequester = remember { FocusRequester() }
    val reconnectControlFocusRequester = remember { FocusRequester() }
    val fullscreenControlFocusRequester = remember { FocusRequester() }
    val fullscreenFocusRequester = remember { FocusRequester() }
    val searchBarFocusRequester = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uiModeManager = remember { context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager }
    val isTv = remember { uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION }
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    val isCompactWidth = configuration.screenWidthDp < 600
    val isMobileLayout = !isTv && (isPortrait || isCompactWidth)

    // Seamless back handling for Live TV fullscreen
    BackHandler(enabled = isFullscreen) {
        onToggleFullscreen(false)
    }

    // Auto-focus fullscreen box when entering fullscreen
    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            delay(100)
            try {
                fullscreenFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Auto-hide channel surfing banner
    LaunchedEffect(channelBannerChannel) {
        if (channelBannerChannel != null) {
            delay(3500)
            channelBannerChannel = null
        }
    }

    // Direct & Fast Live TV Category Loading
    LaunchedEffect(Unit) {
        if (categories.isEmpty()) {
            isLoading = true
            val rawCategories = catalogManager.getLiveCategories()
            val favoritesCategory = LiveCategory(categoryId = "favorites", categoryName = "⭐ Favorites")
            val historyCategory = LiveCategory(categoryId = "history", categoryName = "🕒 History")
            val combined = listOf(favoritesCategory, historyCategory) + rawCategories.filter { it.categoryId != "all" && it.categoryId != "favorites" && it.categoryId != "history" }
            categories = combined

            val savedCat = authRepo.getLastLiveCategoryId()
            val initialCat = if (savedCat != null && combined.any { it.categoryId == savedCat }) {
                savedCat
            } else {
                combined.firstOrNull { it.categoryId != "all" && it.categoryId != "favorites" && it.categoryId != "history" && !CatalogManager.isAdultCategory(it.categoryName) }?.categoryId
                    ?: "favorites"
            }
            selectedCategoryId = initialCat
            channels = when (initialCat) {
                "favorites" -> {
                    val all = catalogManager.getLiveChannels("all")
                    all.filter { favoriteChannelIds.contains(it.streamId) }
                }
                "history" -> {
                    val all = catalogManager.getLiveChannels("all")
                    val histIds = authRepo.getChannelHistoryIds()
                    val map = all.associateBy { it.streamId }
                    histIds.mapNotNull { map[it] }
                }
                else -> {
                    catalogManager.getLiveChannels(initialCat)
                }
            }
            val lastStreamId = authRepo.getLastLiveStreamId()
            if (lastStreamId > 0) {
                activeChannel = channels.firstOrNull { it.streamId == lastStreamId }
            }
            isLoading = false
        }
    }

    // Auto-sync active channel with current title
    LaunchedEffect(currentTitle, channels) {
        if (activeChannel != null && activeChannel?.name != currentTitle && currentTitle.isNotBlank()) {
            val matchingChannel = channels.firstOrNull { it.name == currentTitle }
            if (matchingChannel != null) {
                activeChannel = matchingChannel
                authRepo.setLastLiveStreamId(matchingChannel.streamId)
                authRepo.addChannelToHistory(matchingChannel.streamId)
            }
        }

        if (activeChannel == null && currentTitle.isNotBlank()) {
            activeChannel = channels.firstOrNull { it.name == currentTitle }
        }
    }

    // Load Channels whenever Category Changes
    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null && categories.isNotEmpty()) {
            isLoading = true
            authRepo.setLastLiveCategoryId(selectedCategoryId ?: "672")
            channels = when (selectedCategoryId) {
                "favorites" -> {
                    val all = catalogManager.getLiveChannels("all")
                    all.filter { favoriteChannelIds.contains(it.streamId) }
                }
                "history" -> {
                    val all = catalogManager.getLiveChannels("all")
                    val histIds = authRepo.getChannelHistoryIds()
                    val map = all.associateBy { it.streamId }
                    histIds.mapNotNull { map[it] }
                }
                else -> {
                    catalogManager.getLiveChannels(selectedCategoryId)
                }
            }
            val lastStreamId = authRepo.getLastLiveStreamId()
            if (activeChannel == null && lastStreamId > 0) {
                activeChannel = channels.firstOrNull { it.streamId == lastStreamId }
            }
            isLoading = false
        }
    }

    // Fetch Full EPG for Active Channel
    LaunchedEffect(activeChannel) {
        activeChannel?.let { ch ->
            if (ch.streamId > 0) {
                activeFullEpg = catalogManager.getFullEpgForChannel(ch.streamId)
            } else {
                activeFullEpg = null
            }
        }
    }

    var allCatalogChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }

    // Load full Live catalog when searching if not already loaded
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank() && allCatalogChannels.isEmpty()) {
            isLoading = true
            allCatalogChannels = catalogManager.getLiveChannels("all")
            isLoading = false
        }
    }

    val filteredChannels = remember(channels, allCatalogChannels, selectedCategoryId, searchQuery, favoriteChannelIds, categories) {
        val selectedCat = categories.firstOrNull { it.categoryId == selectedCategoryId }
        val isAdultSelected = selectedCat != null && CatalogManager.isAdultCategory(selectedCat.categoryName)

        if (searchQuery.isNotBlank()) {
            val searchPool = if (allCatalogChannels.isNotEmpty()) allCatalogChannels else channels
            val adultCatIds = categories.filter { CatalogManager.isAdultCategory(it.categoryName) }.map { it.categoryId }.toSet()
            if (isAdultSelected) {
                searchPool.filter { ch ->
                    (adultCatIds.contains(ch.categoryId) || CatalogManager.isAdultCategory(ch.name) || CatalogManager.isAdultName(ch.name)) &&
                    ch.name.contains(searchQuery, ignoreCase = true)
                }
            } else {
                searchPool.filter { ch ->
                    !adultCatIds.contains(ch.categoryId) &&
                    !CatalogManager.isAdultCategory(ch.name) &&
                    !CatalogManager.isAdultName(ch.name) &&
                    ch.name.contains(searchQuery, ignoreCase = true)
                }
            }
        } else if (selectedCategoryId == "favorites") {
            channels.filter { favoriteChannelIds.contains(it.streamId) }
        } else {
            channels
        }
    }

    // Smart EPG Prefetching for Filtered Channels
    LaunchedEffect(filteredChannels) {
        if (filteredChannels.isNotEmpty()) {
            catalogManager.prefetchEpgForChannels(filteredChannels, limit = 60)
        }
    }

    // Auto-scroll selected category into view in the sidebar
    LaunchedEffect(selectedCategoryId, categories) {
        val catIdx = categories.indexOfFirst { it.categoryId == selectedCategoryId }
        if (catIdx >= 0) {
            categoryListState.animateScrollToItem((catIdx - 2).coerceAtLeast(0))
        }
    }

    // Reset scroll offset safely when category changes
    LaunchedEffect(selectedCategoryId, searchQuery) {
        lastFocusedChannelIndex = 0
        if (channelListState.firstVisibleItemIndex > 0) {
            channelListState.scrollToItem(0)
        }
    }

    // Scroll active channel into view and restore focus when exiting fullscreen
    LaunchedEffect(isFullscreen) {
        if (!isFullscreen && activeChannel != null) {
            val idx = filteredChannels.indexOfFirst { it.streamId == activeChannel?.streamId }
            if (idx >= 0) {
                channelListState.scrollToItem((idx - 1).coerceAtLeast(0))
                delay(120)
                try {
                    activeCardFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }
    }

    // Remote Up / Down Channel Surfing Logic
    fun tuneChannel(delta: Int) {
        if (filteredChannels.isEmpty()) return
        val currentIdx = filteredChannels.indexOfFirst { it.streamId == activeChannel?.streamId }
        val newIdx = if (currentIdx == -1) 0 else {
            (currentIdx + delta).mod(filteredChannels.size)
        }
        val target = filteredChannels[newIdx]
        activeChannel = target
        authRepo.setLastLiveStreamId(target.streamId)
        authRepo.addChannelToHistory(target.streamId)

        val portal = target.portalUrl ?: authRepo.getLivePortalUrl()
        val user = target.streamUser ?: authRepo.getActiveUsername()
        val pswd = target.streamPassword ?: authRepo.getActivePassword()
        val streamUrl = if (!target.directStreamUrl.isNullOrBlank()) {
            target.directStreamUrl
        } else {
            apiClient.buildLiveStreamUrl(portal, user, pswd, target.streamId)
        }
        playerManager.playStream(streamUrl, target.name, isLive = true)
        channelBannerChannel = target

        if (catalogManager.getCachedEpg(target.streamId) == null && target.streamId > 0) {
            coroutineScope.launch {
                catalogManager.getFullEpgForChannel(target.streamId)
                if (channelBannerChannel?.streamId == target.streamId) {
                    channelBannerChannel = target
                }
            }
        }
    }

    val isCurrentActiveFavorited = activeChannel?.let { favoriteChannelIds.contains(it.streamId) } ?: false

    fun navigateBackToChannels(): Boolean {
        if (filteredChannels.isEmpty()) {
            try {
                selectedCategoryFocusRequester.requestFocus()
            } catch (_: Exception) {
                try {
                    focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left)
                } catch (_: Exception) {}
            }
            return true
        }

        val activeIdx = filteredChannels.indexOfFirst { it.streamId == activeChannel?.streamId }
        val targetIdx = if (activeIdx >= 0) {
            activeIdx
        } else {
            lastFocusedChannelIndex.coerceIn(0, filteredChannels.size - 1)
        }

        var focused = false
        try {
            activeCardFocusRequester.requestFocus()
            focused = true
        } catch (_: Exception) {}
        if (!focused) {
            try {
                visibleChannelFocusRequester.requestFocus()
                focused = true
            } catch (_: Exception) {}
        }
        if (!focused) {
            try {
                firstChannelFocusRequester.requestFocus()
                focused = true
            } catch (_: Exception) {}
        }
        if (!focused) {
            try {
                focused = focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left)
            } catch (_: Exception) {}
        }

        coroutineScope.launch {
            try {
                channelListState.animateScrollToItem((targetIdx - 1).coerceAtLeast(0))
                delay(50)
                try {
                    activeCardFocusRequester.requestFocus()
                } catch (_: Exception) {
                    try {
                        visibleChannelFocusRequester.requestFocus()
                    } catch (_: Exception) {
                        try {
                            firstChannelFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
        return true
    }

    Box(modifier = modifier.fillMaxSize().background(CinemaBackground)) {
        if (isFullscreen) {
            // Fullscreen Live TV: Full-bleed with Remote Up/Down Channel Surfing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(fullscreenFocusRequester)
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> {
                                    tuneChannel(-1)
                                    return@onKeyEvent true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> {
                                    tuneChannel(1)
                                    return@onKeyEvent true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                    playerManager.togglePlayPause()
                                    return@onKeyEvent true
                                }
                            }
                        }
                        false
                    }
            ) {
                NativePlayerView(
                    playerManager = playerManager,
                    onBack = { onToggleFullscreen(false) },
                    modifier = Modifier.fillMaxSize()
                )

                // Channel Banner Overlay during Fullscreen Surfing
                if (channelBannerChannel != null) {
                    val ch = channelBannerChannel!!
                    val epgTitle = catalogManager.getCachedEpg(ch.streamId)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, CinemaAccent),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 28.dp)
                            .fillMaxWidth(0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ChannelLogoImage(
                                channel = ch,
                                size = 40.dp
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                val cleanChName = remember(ch.name) { CatalogManager.cleanChannelDisplayName(ch.name) }
                                val chQuality = remember(ch.name) { CatalogManager.extractChannelQuality(ch.name) }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = cleanChName,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (chQuality != null) {
                                        val qColor = when (chQuality) {
                                            "4K" -> CinemaYellow
                                            "FHD" -> CinemaAccent
                                            "HEVC" -> CinemaSecondary
                                            "60FPS" -> CinemaGreen
                                            else -> CinemaFocus
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = qColor.copy(alpha = 0.2f),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, qColor.copy(alpha = 0.6f))
                                        ) {
                                            Text(
                                                text = chQuality,
                                                color = qColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (!epgTitle.isNullOrBlank()) "Now: $epgTitle" else "Live Broadcast",
                                    color = CinemaAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        } else if (isMobileLayout) {
            // Mobile Phone Layout (Portrait / Compact View)
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Top Section: 16:9 Video Player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    if (activeChannel != null || currentTitle.isNotBlank()) {
                        NativePlayerView(
                            playerManager = playerManager,
                            onBack = { onToggleFullscreen(false) },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Floating Overlay: LIVE Badge & Fullscreen Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .align(Alignment.TopEnd),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CinemaPrimary,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = "● LIVE",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            IconButton(
                                onClick = { onToggleFullscreen(true) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(CinemaSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = CinemaAccent,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "Select a channel below to watch",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // 2. Active Channel Info Strip
                if (activeChannel != null) {
                    Surface(
                        color = CinemaSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CinemaPrimary,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (activeChannel!!.num > 0) "${activeChannel!!.num}" else "TV",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeChannel!!.name,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val nowTitle = catalogManager.resolveCurrentProgram(activeFullEpg?.epgListings)?.decodedTitle
                                if (!nowTitle.isNullOrBlank()) {
                                    Text(
                                        text = "▶ $nowTitle",
                                        color = CinemaAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    activeChannel?.let { ch ->
                                        authRepo.toggleFavoriteChannel(ch.streamId)
                                        favoriteChannelIds = authRepo.getFavoriteChannelIds()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCurrentActiveFavorited) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isCurrentActiveFavorited) CinemaAccent else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    AppSearchBar(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search channels...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 4. Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategoryId == cat.categoryId
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) CinemaPrimary else CinemaSurfaceVariant,
                            modifier = Modifier.clickable { selectedCategoryId = cat.categoryId }
                        ) {
                            Text(
                                text = CatalogManager.cleanCategoryDisplayName(cat.categoryName),
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // 5. Channel List
                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CinemaAccent)
                    }
                } else if (filteredChannels.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No channels found", color = TextMuted, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        state = channelListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        items(filteredChannels, key = { it.streamId }) { channel ->
                            val isActive = activeChannel?.streamId == channel.streamId
                            var channelEpg by remember(channel.streamId) {
                                mutableStateOf(catalogManager.getCachedEpg(channel.streamId))
                            }
                            LaunchedEffect(channel.streamId) {
                                if (channelEpg == null && channel.streamId > 0) {
                                    channelEpg = catalogManager.getEpgTitleForChannel(channel.streamId)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) CinemaSurfaceLight else CinemaSurface,
                                border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, CinemaAccent) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val portal = channel.portalUrl ?: authRepo.getLivePortalUrl()
                                        val user = channel.streamUser ?: authRepo.getActiveUsername()
                                        val pswd = channel.streamPassword ?: authRepo.getActivePassword()
                                        val streamUrl = if (!channel.directStreamUrl.isNullOrBlank()) {
                                            channel.directStreamUrl
                                        } else {
                                            apiClient.buildLiveStreamUrl(portal, user, pswd, channel.streamId)
                                        }
                                        activeChannel = channel
                                        authRepo.setLastLiveStreamId(channel.streamId)
                                        playerManager.playStream(streamUrl, channel.name, isLive = true)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ChannelQualityBadge(
                                        channelName = channel.name
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            if (favoriteChannelIds.contains(channel.streamId)) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = "Favorite",
                                                    tint = CinemaAccent,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                            val cleanChName = remember(channel.name) { CatalogManager.cleanChannelDisplayName(channel.name) }
                                            Text(
                                                text = cleanChName,
                                                color = if (isActive) CinemaAccent else TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                        }
                                        if (!channelEpg.isNullOrBlank()) {
                                            Text(
                                                text = "▶ $channelEpg",
                                                color = if (isActive) CinemaAccent else CinemaAccent.copy(alpha = 0.85f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            Text(
                                                text = if (channel.num > 0) "CH ${channel.num} • Live HD" else "Live • ${channel.categoryId ?: "Broadcast"}",
                                                color = TextMuted,
                                                fontSize = 10.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    if (isActive) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = CinemaPrimary
                                        ) {
                                            Text(
                                                text = "PLAYING",
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Split Guide / Preview View (3-Column Spacious Layout)
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Categories Vertical Sidebar (Spacious, Vertical Scroll)
                Surface(
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    color = CinemaSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp, horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CATEGORIES",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = CinemaAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        LazyColumn(
                            state = categoryListState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            items(categories, key = { it.categoryId }) { cat ->
                                val isSelected = selectedCategoryId == cat.categoryId
                                val isFirstCat = cat == categories.firstOrNull()
                                TvFocusableCard(
                                    onClick = {
                                        selectedCategoryId = cat.categoryId
                                        authRepo.setLastLiveCategoryId(cat.categoryId)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    backgroundColor = if (isSelected) CinemaPrimary else CinemaSurfaceVariant,
                                    focusedBorderColor = CinemaFocus,
                                    focusedScale = 1.04f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                selectedCategoryId = cat.categoryId
                                                authRepo.setLastLiveCategoryId(cat.categoryId)
                                            }
                                        }
                                        .then(
                                            if (isSelected || (selectedCategoryId == null && isFirstCat)) {
                                                if (contentFocusRequester != null) Modifier.focusRequester(selectedCategoryFocusRequester).focusRequester(contentFocusRequester)
                                                else Modifier.focusRequester(selectedCategoryFocusRequester)
                                            } else Modifier
                                        )
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.key) {
                                                    Key.DirectionLeft -> {
                                                        if (onRequestFocusSidebar != null) {
                                                            onRequestFocusSidebar()
                                                        } else {
                                                            try {
                                                                focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left)
                                                            } catch (_: Exception) {}
                                                        }
                                                        true
                                                    }
                                                    Key.DirectionRight -> {
                                                        selectedCategoryId = cat.categoryId
                                                        authRepo.setLastLiveCategoryId(cat.categoryId)
                                                        var moved = false
                                                        try {
                                                            if (activeChannel != null && filteredChannels.any { it.streamId == activeChannel?.streamId }) {
                                                                activeCardFocusRequester.requestFocus()
                                                                moved = true
                                                            }
                                                        } catch (_: Exception) {}
                                                        if (!moved) {
                                                            try {
                                                                visibleChannelFocusRequester.requestFocus()
                                                                moved = true
                                                            } catch (_: Exception) {}
                                                        }
                                                        if (!moved) {
                                                            try {
                                                                firstChannelFocusRequester.requestFocus()
                                                                moved = true
                                                            } catch (_: Exception) {}
                                                        }
                                                        if (!moved) {
                                                            try {
                                                                searchBarFocusRequester.requestFocus()
                                                                moved = true
                                                            } catch (_: Exception) {}
                                                        }
                                                        if (!moved) {
                                                            try {
                                                                moved = focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right)
                                                            } catch (_: Exception) {}
                                                        }
                                                        if (!moved) {
                                                            coroutineScope.launch {
                                                                delay(60)
                                                                try {
                                                                    visibleChannelFocusRequester.requestFocus()
                                                                } catch (_: Exception) {
                                                                    try {
                                                                        firstChannelFocusRequester.requestFocus()
                                                                    } catch (_: Exception) {}
                                                                }
                                                            }
                                                        }
                                                        true
                                                    }
                                                    else -> false
                                                }
                                            } else {
                                                false
                                            }
                                        }
                                ) {
                                    Text(
                                        text = CatalogManager.cleanCategoryDisplayName(cat.categoryName),
                                        color = if (isSelected) Color.White else TextSecondary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Middle Column: Search & Channels List with Live EPG
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header & Search
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "LIVE TV",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )

                        AppSearchBar(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search channels...",
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchBarFocusRequester),
                            onMoveDown = {
                                try {
                                    if (activeChannel != null && filteredChannels.any { it.streamId == activeChannel?.streamId }) {
                                        activeCardFocusRequester.requestFocus()
                                    } else {
                                        firstChannelFocusRequester.requestFocus()
                                    }
                                } catch (_: Exception) {
                                    try {
                                        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down)
                                    } catch (_: Exception) {}
                                }
                            },
                            onMoveLeft = {
                                try {
                                    selectedCategoryFocusRequester.requestFocus()
                                } catch (_: Exception) {
                                    try {
                                        focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left)
                                    } catch (_: Exception) {}
                                }
                            },
                            onMoveRight = {
                                try {
                                    playControlFocusRequester.requestFocus()
                                } catch (_: Exception) {
                                    try {
                                        favoriteControlFocusRequester.requestFocus()
                                    } catch (_: Exception) {}
                                }
                            }
                        )
                    }

                    // Channels List with Real-Time EPG Information
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CinemaAccent)
                        }
                    } else if (filteredChannels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Icon(
                                    imageVector = if (selectedCategoryId == "favorites") Icons.Default.StarBorder else Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = CinemaAccent,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (selectedCategoryId == "favorites") "No favorite channels yet" else "No channels found in this category",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (selectedCategoryId == "favorites") "Click ⭐ Favorite under the player to add channels to Favorites!" else "Try selecting another category or clear search",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = channelListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(filteredChannels, key = { _, channel -> channel.streamId }) { index, channel ->
                                val isActive = activeChannel?.streamId == channel.streamId
                                val isFirstVisible = index == channelListState.firstVisibleItemIndex
                                val isFirstChannel = index == 0
                                val hasActiveInList = remember(filteredChannels, activeChannel) {
                                    filteredChannels.any { it.streamId == activeChannel?.streamId }
                                }
                                val isTargetFocus = if (hasActiveInList) isActive else (index == lastFocusedChannelIndex.coerceIn(0, (filteredChannels.size - 1).coerceAtLeast(0)))

                                var channelEpg by remember(channel.streamId) {
                                    mutableStateOf(catalogManager.getCachedEpg(channel.streamId))
                                }

                                LaunchedEffect(channel.streamId) {
                                    if (channelEpg == null && channel.streamId > 0) {
                                        channelEpg = catalogManager.getEpgTitleForChannel(channel.streamId)
                                    }
                                }

                                TvFocusableCard(
                                    onClick = {
                                        try {
                                            val portal = channel.portalUrl ?: authRepo.getLivePortalUrl()
                                            val user = channel.streamUser ?: authRepo.getActiveUsername()
                                            val pswd = channel.streamPassword ?: authRepo.getActivePassword()
                                            val streamUrl = if (!channel.directStreamUrl.isNullOrBlank()) {
                                                channel.directStreamUrl
                                            } else {
                                                apiClient.buildLiveStreamUrl(portal, user, pswd, channel.streamId)
                                            }

                                            if (isActive) {
                                                onToggleFullscreen(true)
                                            } else {
                                                activeChannel = channel
                                                authRepo.setLastLiveStreamId(channel.streamId)
                                                authRepo.addChannelToHistory(channel.streamId)
                                                playerManager.playStream(streamUrl, channel.name, isLive = true)
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("LiveTvScreen", "Error launching channel: ${e.message}", e)
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    backgroundColor = if (isActive) CinemaSurfaceLight else CinemaSurface,
                                    focusedBorderColor = CinemaFocus,
                                    focusedScale = 1.02f,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { if (it.isFocused) lastFocusedChannelIndex = index }
                                        .then(if (isTargetFocus) Modifier.focusRequester(activeCardFocusRequester) else Modifier)
                                        .then(if (isFirstVisible) Modifier.focusRequester(visibleChannelFocusRequester) else Modifier)
                                        .then(if (isFirstChannel) Modifier.focusRequester(firstChannelFocusRequester) else Modifier)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown) {
                                                when (keyEvent.key) {
                                                    Key.DirectionLeft -> {
                                                        var moved = false
                                                        try {
                                                            selectedCategoryFocusRequester.requestFocus()
                                                            moved = true
                                                        } catch (_: Exception) {}
                                                        if (!moved) {
                                                            try {
                                                                moved = focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Left)
                                                            } catch (_: Exception) {}
                                                        }
                                                        moved
                                                    }
                                                    Key.DirectionRight -> {
                                                        var moved = false
                                                        try {
                                                            playControlFocusRequester.requestFocus()
                                                            moved = true
                                                        } catch (_: Exception) {}
                                                        if (!moved) {
                                                            try {
                                                                favoriteControlFocusRequester.requestFocus()
                                                                moved = true
                                                            } catch (_: Exception) {}
                                                        }
                                                        if (!moved) {
                                                            try {
                                                                ccControlFocusRequester.requestFocus()
                                                                moved = true
                                                            } catch (_: Exception) {}
                                                        }
                                                        if (!moved) {
                                                            try {
                                                                aspectControlFocusRequester.requestFocus()
                                                                moved = true
                                                            } catch (_: Exception) {}
                                                        }
                                                        if (!moved) {
                                                            try {
                                                                fullscreenControlFocusRequester.requestFocus()
                                                                moved = true
                                                            } catch (_: Exception) {}
                                                        }
                                                        if (!moved) {
                                                            try {
                                                                moved = focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Right)
                                                            } catch (_: Exception) {}
                                                        }
                                                        moved
                                                    }
                                                    else -> false
                                                }
                                            } else {
                                                false
                                            }
                                        }
                                ) { isCardFocused ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        ChannelQualityBadge(
                                            channelName = channel.name
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                if (favoriteChannelIds.contains(channel.streamId)) {
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = "Favorite",
                                                        tint = CinemaAccent,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                                val cleanChName = remember(channel.name) { CatalogManager.cleanChannelDisplayName(channel.name) }
                                                Text(
                                                    text = cleanChName,
                                                    color = if (isActive) CinemaAccent else TextPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = if (isCardFocused) TextOverflow.Clip else TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .weight(1f, fill = false)
                                                        .then(if (isCardFocused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier)
                                                )
                                            }

                                            if (!channelEpg.isNullOrBlank()) {
                                                Text(
                                                    text = "▶ $channelEpg",
                                                    color = if (isActive) CinemaAccent else CinemaAccent.copy(alpha = 0.85f),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = if (isCardFocused) TextOverflow.Clip else TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .then(if (isCardFocused) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier)
                                                )
                                            } else {
                                                Text(
                                                    text = if (channel.num > 0) "CH ${channel.num} • Live" else "Live • ${channel.categoryId ?: "Broadcast"}",
                                                    color = TextMuted,
                                                    fontSize = 11.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        if (isActive) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = CinemaPrimary
                                            ) {
                                                Text(
                                                    text = "PLAYING",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Column: Live Stream Preview & Rich Comprehensive EPG Info
                Column(
                    modifier = Modifier
                        .weight(1.45f)
                        .fillMaxHeight()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Preview Video Player
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        if (activeChannel != null || currentTitle.isNotBlank()) {
                            NativePlayerView(
                                playerManager = playerManager,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(CinemaSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = CinemaPrimary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "Select a channel on the left to start streaming",
                                        color = TextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Click once to preview • Click again for Fullscreen",
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // Rich Comprehensive EPG Card
                    val epgList = activeFullEpg?.epgListings ?: emptyList()
                    val currentEpoch = System.currentTimeMillis() / 1000L

                    val currentProgram = remember(epgList, currentEpoch) {
                        catalogManager.resolveCurrentProgram(epgList)
                    }
                    val currentIdx = remember(epgList, currentProgram) {
                        if (currentProgram == null) -1 else epgList.indexOf(currentProgram)
                    }
                    val nextProgram = if (currentIdx >= 0 && currentIdx + 1 < epgList.size) {
                        epgList[currentIdx + 1]
                    } else if (epgList.size > 1 && currentIdx != 1) {
                        epgList[1]
                    } else null

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CinemaSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .verticalScroll(previewInfoScrollState)
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                                                coroutineScope.launch {
                                                    previewInfoScrollState.animateScrollTo((previewInfoScrollState.value + 250).coerceIn(0, previewInfoScrollState.maxValue))
                                                }
                                                false
                                            }
                                            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                                                if (previewInfoScrollState.value > 0) {
                                                    coroutineScope.launch {
                                                        previewInfoScrollState.animateScrollTo((previewInfoScrollState.value - 250).coerceIn(0, previewInfoScrollState.maxValue))
                                                    }
                                                    false
                                                } else {
                                                    false
                                                }
                                            }
                                            else -> false
                                        }
                                    } else false
                                },
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Row 1: Channel Name & Number Header (Spacious, full width, no truncation)
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val rawChTitle = activeChannel?.name ?: currentTitle.ifBlank { "No Channel Selected" }
                                    val cleanPreviewTitle = remember(rawChTitle) { CatalogManager.cleanChannelDisplayName(rawChTitle) }
                                    val previewQuality = remember(rawChTitle) { CatalogManager.extractChannelQuality(rawChTitle) }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = cleanPreviewTitle,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip,
                                            modifier = Modifier.weight(1f, fill = false).basicMarquee(iterations = Int.MAX_VALUE)
                                        )
                                        if (previewQuality != null) {
                                            val qColor = when (previewQuality) {
                                                "4K" -> CinemaYellow
                                                "FHD" -> CinemaAccent
                                                "HEVC" -> CinemaSecondary
                                                "60FPS" -> CinemaGreen
                                                else -> CinemaFocus
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = qColor.copy(alpha = 0.2f),
                                                border = androidx.compose.foundation.BorderStroke(0.5.dp, qColor.copy(alpha = 0.6f))
                                            ) {
                                                Text(
                                                    text = previewQuality,
                                                    color = qColor,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (activeChannel != null || currentTitle.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = CinemaPrimary,
                                            modifier = Modifier.padding(start = 8.dp)
                                        ) {
                                            Text(
                                                text = "● LIVE",
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                val chNum = activeChannel?.num ?: 0
                                Text(
                                    text = if (chNum > 0) "Channel $chNum • 1080p 60fps Live" else "Live Broadcast • HD High Quality Stream",
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            // Row 2: Action Controls Bar (Resume/Stop, Favorite, CC, Aspect, Fullscreen) - Icon-Only Symbols
                            if (activeChannel != null || currentTitle.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // 1. Stop / Resume Live Toggle (Icon only)
                                    TvFocusableCard(
                                        onClick = {
                                            if (isPlaying) {
                                                playerManager.pause()
                                            } else {
                                                playerManager.resumeLive()
                                            }
                                        },
                                        backgroundColor = CinemaSurfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .size(38.dp)
                                            .focusRequester(playControlFocusRequester)
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    when (keyEvent.key) {
                                                        Key.DirectionLeft, Key.Back, Key.Escape -> {
                                                            navigateBackToChannels()
                                                        }
                                                        Key.DirectionRight -> {
                                                            try {
                                                                if (activeChannel != null) {
                                                                    favoriteControlFocusRequester.requestFocus()
                                                                } else {
                                                                    ccControlFocusRequester.requestFocus()
                                                                }
                                                                true
                                                            } catch (_: Exception) {
                                                                false
                                                            }
                                                        }
                                                        else -> false
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
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause" else "Resume",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // 2. Favorite Button (Icon only)
                                    activeChannel?.let { ch ->
                                        TvFocusableCard(
                                            onClick = {
                                                authRepo.toggleFavoriteChannel(ch.streamId)
                                                favoriteChannelIds = authRepo.getFavoriteChannelIds()
                                            },
                                            backgroundColor = if (isCurrentActiveFavorited) CinemaAccent.copy(alpha = 0.25f) else CinemaSurfaceVariant,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .size(38.dp)
                                                .focusRequester(favoriteControlFocusRequester)
                                                .onPreviewKeyEvent { keyEvent ->
                                                    if (keyEvent.type == KeyEventType.KeyDown) {
                                                        when (keyEvent.key) {
                                                            Key.DirectionLeft -> {
                                                                try {
                                                                    playControlFocusRequester.requestFocus()
                                                                    true
                                                                } catch (_: Exception) {
                                                                    navigateBackToChannels()
                                                                }
                                                            }
                                                            Key.DirectionRight -> {
                                                                try {
                                                                    ccControlFocusRequester.requestFocus()
                                                                    true
                                                                } catch (_: Exception) {
                                                                    false
                                                                }
                                                            }
                                                            Key.Back, Key.Escape -> {
                                                                navigateBackToChannels()
                                                            }
                                                            else -> false
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
                                                    imageVector = if (isCurrentActiveFavorited) Icons.Default.Star else Icons.Default.StarBorder,
                                                    contentDescription = "Favorite",
                                                    tint = if (isCurrentActiveFavorited) CinemaAccent else Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    // 3. Closed Captions (CC) Toggle Button (Icon only)
                                    TvFocusableCard(
                                        onClick = {
                                            playerManager.toggleClosedCaptions()
                                        },
                                        backgroundColor = if (isCcEnabled) CinemaPrimary else CinemaSurfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .size(38.dp)
                                            .focusRequester(ccControlFocusRequester)
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    when (keyEvent.key) {
                                                        Key.DirectionLeft -> {
                                                            try {
                                                                if (activeChannel != null) {
                                                                    favoriteControlFocusRequester.requestFocus()
                                                                } else {
                                                                    playControlFocusRequester.requestFocus()
                                                                }
                                                                true
                                                            } catch (_: Exception) {
                                                                navigateBackToChannels()
                                                            }
                                                        }
                                                        Key.DirectionRight -> {
                                                            try {
                                                                aspectControlFocusRequester.requestFocus()
                                                                true
                                                            } catch (_: Exception) {
                                                                false
                                                            }
                                                        }
                                                        Key.Back, Key.Escape -> {
                                                            navigateBackToChannels()
                                                        }
                                                        else -> false
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
                                                imageVector = Icons.Default.ClosedCaption,
                                                contentDescription = "Closed Captions",
                                                tint = if (isCcEnabled) Color.White else TextMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // 4. Aspect Ratio Toggle Button (Icon only)
                                    TvFocusableCard(
                                        onClick = {
                                            playerManager.cycleAspectRatio()
                                        },
                                        backgroundColor = CinemaSurfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .size(38.dp)
                                            .focusRequester(aspectControlFocusRequester)
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    when (keyEvent.key) {
                                                        Key.DirectionLeft -> {
                                                            try {
                                                                ccControlFocusRequester.requestFocus()
                                                                true
                                                            } catch (_: Exception) {
                                                                navigateBackToChannels()
                                                            }
                                                        }
                                                        Key.DirectionRight -> {
                                                            try {
                                                                reconnectControlFocusRequester.requestFocus()
                                                                true
                                                            } catch (_: Exception) {
                                                                false
                                                            }
                                                        }
                                                        Key.Back, Key.Escape -> {
                                                            navigateBackToChannels()
                                                        }
                                                        else -> false
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
                                                imageVector = Icons.Default.AspectRatio,
                                                contentDescription = "Aspect Ratio",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // 5. Reconnect Button (Icon only)
                                    TvFocusableCard(
                                        onClick = {
                                            Toast.makeText(context, "Reconnecting stream...", Toast.LENGTH_SHORT).show()
                                            playerManager.reconnectCurrentStream()
                                        },
                                        backgroundColor = CinemaPrimary.copy(alpha = 0.85f),
                                        focusedBorderColor = CinemaAccent,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .size(38.dp)
                                            .focusRequester(reconnectControlFocusRequester)
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    when (keyEvent.key) {
                                                        Key.DirectionLeft -> {
                                                            try {
                                                                aspectControlFocusRequester.requestFocus()
                                                                true
                                                            } catch (_: Exception) {
                                                                navigateBackToChannels()
                                                            }
                                                        }
                                                        Key.DirectionRight -> {
                                                            try {
                                                                fullscreenControlFocusRequester.requestFocus()
                                                                true
                                                            } catch (_: Exception) {
                                                                false
                                                            }
                                                        }
                                                        Key.Back, Key.Escape -> {
                                                            navigateBackToChannels()
                                                        }
                                                        else -> false
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
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Reconnect Stream",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // 6. Fullscreen Button (Icon only)
                                    TvFocusableCard(
                                        onClick = {
                                            onToggleFullscreen(true)
                                        },
                                        backgroundColor = CinemaPrimary,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .size(38.dp)
                                            .focusRequester(fullscreenControlFocusRequester)
                                            .onPreviewKeyEvent { keyEvent ->
                                                if (keyEvent.type == KeyEventType.KeyDown) {
                                                    when (keyEvent.key) {
                                                        Key.DirectionLeft -> {
                                                            try {
                                                                reconnectControlFocusRequester.requestFocus()
                                                                true
                                                            } catch (_: Exception) {
                                                                false
                                                            }
                                                        }
                                                        Key.Back, Key.Escape -> {
                                                            navigateBackToChannels()
                                                        }
                                                        else -> false
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
                                                imageVector = Icons.Default.Fullscreen,
                                                contentDescription = "Fullscreen",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = CinemaSurfaceLight, thickness = 1.dp)

                            // NOW PLAYING Live Program Details in Local Time
                            if (currentProgram != null) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = CinemaRed.copy(alpha = 0.2f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaRed.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = "NOW PLAYING",
                                                color = CinemaRed,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            text = currentProgram.decodedTitle,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CinemaAccent,
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip,
                                            modifier = Modifier.weight(1f, fill = false).basicMarquee(iterations = Int.MAX_VALUE)
                                        )
                                    }

                                    val startFormatted = formatEpgTimeLocal(currentProgram.startTimestamp, currentProgram.start)
                                    val endFormatted = formatEpgTimeLocal(currentProgram.stopTimestamp, currentProgram.end)
                                    if (startFormatted.isNotBlank() && endFormatted.isNotBlank()) {
                                        Text(
                                            text = "Time: $startFormatted - $endFormatted",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextSecondary
                                        )
                                    }

                                    // Program Plot Summary
                                    val desc = currentProgram.decodedDescription
                                    if (!desc.isNullOrBlank()) {
                                        Text(
                                            text = desc,
                                            fontSize = 12.sp,
                                            color = TextPrimary,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = if (activeChannel != null || currentTitle.isNotBlank()) "Live Broadcast • HD High Quality Stream" else "Select a channel on the left to view programming details",
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }

                            // UP NEXT Scheduled Program Box in Local Time (Focusable for D-Pad Remote Scrolling)
                            if (nextProgram != null) {
                                TvFocusableCard(
                                    onClick = { /* Informational focus */ },
                                    shape = RoundedCornerShape(8.dp),
                                    backgroundColor = CinemaSurfaceVariant,
                                    focusedBorderColor = CinemaFocus,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .onPreviewKeyEvent { keyEvent ->
                                            if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.DirectionLeft || keyEvent.key == Key.Back || keyEvent.key == Key.Escape)) {
                                                navigateBackToChannels()
                                            } else {
                                                false
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = CinemaPrimary.copy(alpha = 0.3f)
                                        ) {
                                            Text(
                                                text = "UP NEXT",
                                                color = CinemaAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = nextProgram.decodedTitle,
                                                color = TextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Clip,
                                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                            )
                                            val nextStartFormatted = formatEpgTimeLocal(nextProgram.startTimestamp, nextProgram.start)
                                            if (nextStartFormatted.isNotBlank()) {
                                                Text(
                                                    text = "Starts: $nextStartFormatted",
                                                    color = TextMuted,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun formatEpgTimeLocal(rawTimestamp: String?, rawDateStr: String?): String {
    val localTz = TimeZone.getDefault()
    val outFormat = SimpleDateFormat("h:mm a", Locale.US).apply {
        timeZone = localTz
    }

    val epoch = rawTimestamp?.toLongOrNull() ?: rawDateStr?.toLongOrNull()
    if (epoch != null) {
        val ms = if (epoch > 100000000000L) epoch else epoch * 1000L
        return outFormat.format(Date(ms))
    }

    val str = rawDateStr?.trim() ?: rawTimestamp?.trim() ?: return ""
    if (str.isBlank()) return ""

    val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss Z",
        "yyyy-MM-dd HH:mm:ss",
        "yyyyMMddHHmmss"
    )

    for (p in patterns) {
        try {
            val sdf = SimpleDateFormat(p, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = sdf.parse(str)
            if (parsed != null) {
                return outFormat.format(parsed)
            }
        } catch (_: Exception) {}
    }

    return str
}

@Composable
fun ChannelQualityBadge(
    channelName: String,
    modifier: Modifier = Modifier
) {
    val rawQuality = remember(channelName) { CatalogManager.extractChannelQuality(channelName) }
    val resolvedQuality = rawQuality ?: if (channelName.contains("4K", ignoreCase = true) || channelName.contains("UHD", ignoreCase = true)) "4K" else "HD"
    val qColor = when (resolvedQuality) {
        "4K" -> CinemaYellow
        "FHD" -> CinemaAccent
        "HEVC" -> CinemaSecondary
        "60FPS" -> CinemaGreen
        else -> CinemaFocus
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = qColor.copy(alpha = 0.16f),
        border = androidx.compose.foundation.BorderStroke(1.dp, qColor.copy(alpha = 0.55f)),
        modifier = modifier.width(42.dp).height(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = resolvedQuality,
                color = qColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun ChannelLogoImage(
    channel: Channel,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 42.dp
) {
    val logoUrl = remember(channel.streamId, channel.name, channel.streamIcon) {
        CatalogManager.resolveChannelLogoUrl(channel.name, channel.streamIcon)
    }

    if (!logoUrl.isNullOrBlank()) {
        val context = LocalContext.current
        val imageRequest = remember(logoUrl) {
            coil.request.ImageRequest.Builder(context)
                .data(logoUrl)
                .crossfade(true)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .build()
        }
        coil.compose.SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = channel.name,
            contentScale = ContentScale.Fit,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.35f)),
            error = {
                ChannelFallbackBadge(name = channel.name, size = size, modifier = modifier)
            },
            loading = {
                ChannelFallbackBadge(name = channel.name, size = size, modifier = modifier)
            }
        )
    } else {
        ChannelFallbackBadge(name = channel.name, size = size, modifier = modifier)
    }
}

@Composable
fun ChannelFallbackBadge(
    name: String,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    modifier: Modifier = Modifier
) {
    val initials = remember(name) { CatalogManager.getChannelInitials(name) }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = CinemaSurfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                color = CinemaAccent,
                fontSize = if (size < 40.dp) 10.sp else 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}
