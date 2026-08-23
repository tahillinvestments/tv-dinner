package com.troyh.tvdinner.ui.screens

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.troyh.tvdinner.data.model.Channel
import com.troyh.tvdinner.data.model.LiveCategory
import com.troyh.tvdinner.data.model.ShortEpgResponse
import com.troyh.tvdinner.data.network.XtreamApiClient
import com.troyh.tvdinner.data.repository.AuthRepository
import com.troyh.tvdinner.data.repository.CatalogManager
import com.troyh.tvdinner.player.ExoPlayerManager
import com.troyh.tvdinner.ui.components.TvFocusableCard
import com.troyh.tvdinner.ui.player.NativePlayerView
import com.troyh.tvdinner.ui.theme.*
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
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var categories by remember { mutableStateOf<List<LiveCategory>>(emptyList()) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>("all") }
    var allChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeChannel by remember { mutableStateOf<Channel?>(null) }
    var activeFullEpg by remember { mutableStateOf<ShortEpgResponse?>(null) }
    var channelBannerChannel by remember { mutableStateOf<Channel?>(null) }
    var favoriteChannelIds by remember { mutableStateOf(authRepo.getFavoriteChannelIds()) }

    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentTitle by playerManager.currentTitle.collectAsState()
    val credentials = remember { authRepo.getActiveLiveCredentials() }

    val channelListState = rememberLazyListState()
    val previewInfoScrollState = rememberScrollState()
    val activeCardFocusRequester = remember { FocusRequester() }

    // Seamless back handling for Live TV fullscreen
    BackHandler(enabled = isFullscreen) {
        onToggleFullscreen(false)
    }

    // Auto-hide channel surfing banner
    LaunchedEffect(channelBannerChannel) {
        if (channelBannerChannel != null) {
            delay(3500)
            channelBannerChannel = null
        }
    }

    // Direct & Fast Live TV Loading
    LaunchedEffect(Unit) {
        if (categories.isEmpty() || allChannels.isEmpty()) {
            isLoading = true
            val rawCategories = catalogManager.getLiveCategories()
            val favoritesCategory = LiveCategory(categoryId = "favorites", categoryName = "⭐ Favorites")
            val allCategory = LiveCategory(categoryId = "all", categoryName = "All Channels")
            categories = listOf(allCategory, favoritesCategory) + rawCategories.filter { it.categoryId != "all" && it.categoryId != "favorites" }
            allChannels = catalogManager.getLiveChannels()
            isLoading = false
        }

        if (activeChannel == null && currentTitle.isNotBlank()) {
            activeChannel = allChannels.firstOrNull { it.name == currentTitle }
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

    val filteredChannels = remember(allChannels, selectedCategoryId, searchQuery, favoriteChannelIds) {
        allChannels.filter { channel ->
            val matchCategory = when (selectedCategoryId) {
                "favorites" -> favoriteChannelIds.contains(channel.streamId)
                "all", null -> true
                else -> channel.categoryId == selectedCategoryId
            }
            val matchSearch = searchQuery.isBlank() || channel.name.contains(searchQuery, ignoreCase = true)
            matchCategory && matchSearch
        }
    }

    // Reset scroll offset safely when category changes
    LaunchedEffect(selectedCategoryId, searchQuery) {
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

        val portal = authRepo.getLivePortalUrl()
        val user = credentials?.user ?: "DGOLD001"
        val pswd = credentials?.pswd ?: "Louisville"
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

    Box(modifier = modifier.fillMaxSize().background(CinemaBackground)) {
        if (isFullscreen) {
            // Fullscreen Live TV: Full-bleed with Remote Up/Down Channel Surfing
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusable()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp) {
                            when (keyEvent.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                                    tuneChannel(-1)
                                    return@onKeyEvent true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                                    tuneChannel(1)
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
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CinemaPrimary,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (ch.num > 0) "${ch.num}" else "TV",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ch.name,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
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
        } else {
            // Split Guide / Preview View
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Categories, Search, Channels List with Live EPG
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
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search channels...", color = TextMuted, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = CinemaSurface,
                                unfocusedContainerColor = CinemaSurface,
                                focusedBorderColor = CinemaAccent,
                                unfocusedBorderColor = CinemaSurfaceVariant,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        )
                    }

                    // Categories Row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategoryId == cat.categoryId
                            TvFocusableCard(
                                onClick = { selectedCategoryId = cat.categoryId },
                                shape = RoundedCornerShape(8.dp),
                                backgroundColor = if (isSelected) CinemaPrimary else CinemaSurfaceVariant,
                                focusedBorderColor = CinemaFocus,
                                focusedScale = 1.05f
                            ) {
                                Text(
                                    text = cat.categoryName,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
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

                                TvFocusableCard(
                                    onClick = {
                                        try {
                                            val portal = authRepo.getLivePortalUrl()
                                            val user = credentials?.user ?: "DGOLD001"
                                            val pswd = credentials?.pswd ?: "Louisville"
                                            val streamUrl = if (!channel.directStreamUrl.isNullOrBlank()) {
                                                channel.directStreamUrl
                                            } else {
                                                apiClient.buildLiveStreamUrl(portal, user, pswd, channel.streamId)
                                            }

                                            if (isActive) {
                                                onToggleFullscreen(true)
                                            } else {
                                                activeChannel = channel
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
                                    modifier = if (isActive) Modifier.focusRequester(activeCardFocusRequester) else Modifier
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        if (!channel.streamIcon.isNullOrBlank()) {
                                            AsyncImage(
                                                model = channel.streamIcon,
                                                contentDescription = channel.name,
                                                contentScale = ContentScale.Fit,
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color.Black.copy(alpha = 0.3f))
                                            )
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = CinemaSurfaceVariant,
                                                modifier = Modifier.size(42.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Tv,
                                                        contentDescription = null,
                                                        tint = TextMuted,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                if (favoriteChannelIds.contains(channel.streamId)) {
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = "Favorite",
                                                        tint = CinemaAccent,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                                Text(
                                                    text = channel.name,
                                                    color = if (isActive) CinemaAccent else TextPrimary,
                                                    fontSize = 14.sp,
                                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            if (!channelEpg.isNullOrBlank()) {
                                                Text(
                                                    text = "▶ $channelEpg",
                                                    color = if (isActive) CinemaAccent else CinemaAccent.copy(alpha = 0.85f),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            } else {
                                                Text(
                                                    text = if (channel.num > 0) "CH ${channel.num} • Live HD" else "Live • ${channel.categoryId ?: "Broadcast"}",
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

                    val currentIdx = remember(epgList, currentEpoch) {
                        if (epgList.isEmpty()) -1
                        else {
                            val directNowIdx = epgList.indexOfFirst { it.nowPlaying == 1 }
                            if (directNowIdx >= 0) directNowIdx
                            else {
                                val timeIdx = epgList.indexOfFirst { prog ->
                                    val startSec = prog.startTimestamp?.toLongOrNull()
                                    val stopSec = prog.stopTimestamp?.toLongOrNull()
                                    if (startSec != null && stopSec != null && startSec > 0 && stopSec > startSec) {
                                        currentEpoch in startSec until stopSec
                                    } else {
                                        false
                                    }
                                }
                                if (timeIdx >= 0) timeIdx
                                else {
                                    val futureIdx = epgList.indexOfFirst { prog ->
                                        val stopSec = prog.stopTimestamp?.toLongOrNull()
                                        stopSec != null && stopSec > currentEpoch
                                    }
                                    if (futureIdx >= 0) futureIdx else 0
                                }
                            }
                        }
                    }

                    val currentProgram = if (currentIdx >= 0 && currentIdx < epgList.size) epgList[currentIdx] else epgList.firstOrNull()
                    val nextProgram = if (currentIdx >= 0 && currentIdx + 1 < epgList.size) epgList[currentIdx + 1] else if (epgList.size > 1) epgList[1] else null

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
                                .verticalScroll(previewInfoScrollState),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Channel Name & Number Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = activeChannel?.name ?: currentTitle.ifBlank { "No Channel Selected" },
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (activeChannel?.num != null && activeChannel!!.num > 0) {
                                        Text(
                                            text = "Channel ${activeChannel!!.num} • 1080p 60fps Live",
                                            fontSize = 12.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                // Quick Action Buttons: Stop/Resume, Favorite, Fullscreen
                                if (activeChannel != null || currentTitle.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Stop / Resume Live Toggle
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
                                            modifier = Modifier.height(38.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = if (isPlaying) "Pause" else "Resume",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = if (isPlaying) "Stop" else "Resume",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        // Favorite Button
                                        activeChannel?.let { ch ->
                                            TvFocusableCard(
                                                onClick = {
                                                    authRepo.toggleFavoriteChannel(ch.streamId)
                                                    favoriteChannelIds = authRepo.getFavoriteChannelIds()
                                                },
                                                backgroundColor = if (isCurrentActiveFavorited) CinemaAccent.copy(alpha = 0.25f) else CinemaSurfaceVariant,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(38.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isCurrentActiveFavorited) Icons.Default.Star else Icons.Default.StarBorder,
                                                        contentDescription = "Favorite",
                                                        tint = if (isCurrentActiveFavorited) CinemaAccent else Color.White,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = if (isCurrentActiveFavorited) "Favorited" else "Favorite",
                                                        color = if (isCurrentActiveFavorited) CinemaAccent else Color.White,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // Fullscreen Button
                                        TvFocusableCard(
                                            onClick = {
                                                onToggleFullscreen(true)
                                            },
                                            backgroundColor = CinemaPrimary,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(38.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxHeight().padding(horizontal = 12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Fullscreen,
                                                    contentDescription = "Fullscreen",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Fullscreen",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = CinemaSurfaceLight, thickness = 1.dp)

                            // NOW PLAYING Live Program Details in US Eastern Time
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
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    val startFormatted = formatEpgTimeEastern(currentProgram.startTimestamp, currentProgram.start)
                                    val endFormatted = formatEpgTimeEastern(currentProgram.stopTimestamp, currentProgram.end)
                                    if (startFormatted.isNotBlank() && endFormatted.isNotBlank()) {
                                        Text(
                                            text = "Time (EST): $startFormatted - $endFormatted",
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

                            // UP NEXT Scheduled Program Box in US Eastern Time
                            if (nextProgram != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CinemaSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
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
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val nextStartFormatted = formatEpgTimeEastern(nextProgram.startTimestamp, nextProgram.start)
                                            if (nextStartFormatted.isNotBlank()) {
                                                Text(
                                                    text = "Starts: $nextStartFormatted EST",
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

fun formatEpgTimeEastern(rawTimestamp: String?, rawDateStr: String?): String {
    val easternTz = TimeZone.getTimeZone("America/New_York")
    val outFormat = SimpleDateFormat("h:mm a", Locale.US).apply {
        timeZone = easternTz
    }

    val epochSeconds = rawTimestamp?.toLongOrNull() ?: rawDateStr?.toLongOrNull()
    if (epochSeconds != null && epochSeconds > 1000000000L) {
        return outFormat.format(Date(epochSeconds * 1000L))
    }

    val str = rawDateStr?.trim() ?: rawTimestamp?.trim() ?: return ""
    if (str.isBlank()) return ""

    return try {
        if (str.contains("+") || str.endsWith("Z", ignoreCase = true)) {
            val isoFormat = SimpleDateFormat(if (str.contains("T")) "yyyy-MM-dd'T'HH:mm:ss" else "yyyy-MM-dd HH:mm:ss Z", Locale.US)
            val parsed = isoFormat.parse(str)
            if (parsed != null) return outFormat.format(parsed)
        }

        val standardFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = easternTz
        }
        val parsedStandard = standardFormat.parse(str)
        if (parsedStandard != null) {
            return outFormat.format(parsedStandard)
        }

        val parts = str.split(" ")
        val timePart = if (parts.size > 1) parts[1] else parts[0]
        val timePieces = timePart.split(":")
        if (timePieces.size >= 2) {
            val hour = timePieces[0].toIntOrNull() ?: return str
            val min = timePieces[1].toIntOrNull() ?: return str
            val ampm = if (hour >= 12) "PM" else "AM"
            val displayHour = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }
            return String.format(Locale.US, "%d:%02d %s", displayHour, min, ampm)
        }
        str
    } catch (_: Throwable) {
        str
    }
}
