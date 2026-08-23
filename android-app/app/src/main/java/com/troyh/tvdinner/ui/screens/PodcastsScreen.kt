package com.troyh.tvdinner.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.troyh.tvdinner.data.model.PodcastChannel
import com.troyh.tvdinner.data.model.PodcastEpisode
import com.troyh.tvdinner.data.podcasts.PodcastsData
import com.troyh.tvdinner.data.repository.AuthRepository
import com.troyh.tvdinner.data.repository.CatalogManager
import com.troyh.tvdinner.ui.components.TvFocusableCard
import com.troyh.tvdinner.ui.theme.*

@Composable
fun PodcastsScreen(
    authRepo: AuthRepository,
    catalogManager: CatalogManager,
    onPlayYouTubeVideo: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        "🔥 Trending",
        "⭐ Subscribed",
        "🤖 AI & Tech",
        "💼 Business & Ideas",
        "🧠 Science & Health",
        "🎙️ Culture & Talk",
        "📰 News & Politics"
    )
    var selectedCategory by rememberSaveable { mutableStateOf("🔥 Trending") }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    var liveChannels by remember { mutableStateOf<List<PodcastChannel>>(emptyList()) }
    var liveEpisodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }
    var selectedChannel by remember { mutableStateOf<PodcastChannel?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Pagination / Infinite Scroll States
    var currentPage by remember { mutableIntStateOf(1) }
    var isFetchingMore by remember { mutableStateOf(false) }
    var canLoadMore by remember { mutableStateOf(true) }

    var subscribedIds by remember { mutableStateOf(authRepo.getSubscribedPodcastIds()) }
    val gridState = rememberLazyGridState()

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
    LaunchedEffect(selectedCategory, searchQuery) {
        isLoading = true
        selectedChannel = null
        currentPage = 1
        canLoadMore = true

        if (searchQuery.isNotBlank()) {
            val channels = catalogManager.getLivePodcastChannels(searchQuery)
            liveChannels = channels
            val eps = catalogManager.getLivePodcastEpisodes(searchQuery)
            liveEpisodes = eps
        } else if (selectedCategory == "⭐ Subscribed") {
            val allChannels = mutableListOf<PodcastChannel>()
            for (id in subscribedIds) {
                val curatedMatch = PodcastsData.CHANNELS.find { it.id == id }
                if (curatedMatch != null) {
                    allChannels.add(curatedMatch)
                } else {
                    allChannels.add(
                        PodcastChannel(
                            id = id,
                            channelName = id.removePrefix("chan_").replace("_", " ").replaceFirstChar { it.uppercase() },
                            host = "Subscribed Podcast",
                            category = "Subscribed",
                            subscribers = "Subscribed Feed",
                            avatar = "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=300&q=80",
                            description = "Your saved subscription channel.",
                            ytChannelId = if (id.startsWith("yt_chan_")) id.removePrefix("yt_chan_") else ""
                        )
                    )
                }
            }
            liveChannels = allChannels
            if (allChannels.isNotEmpty()) {
                selectedChannel = allChannels.first()
                liveEpisodes = catalogManager.getPodcastEpisodesForChannel(allChannels.first())
            } else {
                liveEpisodes = emptyList()
            }
        } else {
            val catClean = selectedCategory.replace(Regex("[^a-zA-Z &]"), "").trim()
            val channels = catalogManager.getLivePodcastChannels(catClean)
            liveChannels = channels
            val eps = catalogManager.getLivePodcastEpisodes(catClean)
            liveEpisodes = eps
        }
        isLoading = false
    }

    // When a channel is explicitly selected
    LaunchedEffect(selectedChannel) {
        selectedChannel?.let { ch ->
            isLoading = true
            val eps = catalogManager.getPodcastEpisodesForChannel(ch)
            if (eps.isNotEmpty()) {
                liveEpisodes = eps
            }
            isLoading = false
        }
    }

    // Infinite Scroll detection
    val isNearBottom by remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && last >= total - 4
        }
    }

    LaunchedEffect(isNearBottom) {
        if (isNearBottom && !isLoading && !isFetchingMore && canLoadMore) {
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
                    canLoadMore = false
                }
            } else {
                canLoadMore = false
            }
            isFetchingMore = false
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CinemaBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Bar: Title & Live Search
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search podcast channels & shows...", color = TextMuted, fontSize = 13.sp) },
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

            // Category Chips Row (Shown when not actively searching and not inside a specific channel view)
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
            } else if (searchQuery.isNotBlank()) {
                Text(
                    text = "Channels matching \"$searchQuery\"",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            // Channel Header Banner (When a specific channel is selected)
            if (selectedChannel != null) {
                val ch = selectedChannel!!
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
                            onClick = { selectedChannel = null },
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
                            onClick = {
                                authRepo.togglePodcastSubscription(ch.id)
                                subscribedIds = authRepo.getSubscribedPodcastIds()
                            },
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
                            onClick = { selectedChannel = ch },
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
                                    onClick = {
                                        authRepo.togglePodcastSubscription(ch.id)
                                        subscribedIds = authRepo.getSubscribedPodcastIds()
                                    },
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CinemaAccent)
                }
            } else if (liveEpisodes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (selectedCategory == "⭐ Subscribed") "No subscribed podcasts yet. Click the bookmark icon on any channel to save it here!" else "No live podcast episodes found",
                        color = TextMuted,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 220.dp),
                    state = gridState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(liveEpisodes, key = { it.id }) { ep ->
                        TvFocusableCard(
                            onClick = { onPlayYouTubeVideo(ep.videoId, ep.title) },
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = CinemaSurface,
                            focusedBorderColor = CinemaFocus,
                            focusedScale = 1.03f,
                            modifier = Modifier.fillMaxWidth().wrapContentHeight()
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
                                        // Channel button to switch to that channel
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
}
