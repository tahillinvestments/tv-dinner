package com.tvdinner.ui.screens

import android.app.UiModeManager
import android.content.Context
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
import com.tvdinner.ui.components.AppSearchBar
import com.tvdinner.ui.components.TvFocusableCard
import com.tvdinner.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MusicScreen(
    authRepo: AuthRepository,
    catalogManager: CatalogManager,
    onPlayYouTubeVideo: (String, String, (() -> Unit)?, String?, (() -> Unit)?) -> Unit,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isAccessAllowed = remember(authRepo.getActiveUsername(), authRepo.getActivePassword()) {
        authRepo.hasVerifiedActiveCredentials()
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var selectedGenreId by rememberSaveable { mutableStateOf("trending") }

    var videos by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
    var currentPage by remember { mutableIntStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    suspend fun fetchMoreVideosInternal() {
        if (!isAccessAllowed || isLoading || isLoadingMore || !hasMore || selectedGenreId == "history") return
        isLoadingMore = true
        val nextPage = currentPage + 1
        val nextResults = if (debouncedQuery.isNotBlank()) {
            catalogManager.searchMusicVideos(debouncedQuery, page = nextPage)
        } else {
            catalogManager.getMusicForGenre(selectedGenreId, page = nextPage)
        }

        val existingIds = videos.map { it.videoId }.toSet()
        val freshVideos = nextResults.filter { !existingIds.contains(it.videoId) }

        if (freshVideos.isNotEmpty()) {
            videos = videos + freshVideos
            currentPage = nextPage
        } else {
            val fallbackPage = nextPage + 1
            val fallbackResults = if (debouncedQuery.isNotBlank()) {
                catalogManager.searchMusicVideos(debouncedQuery, page = fallbackPage)
            } else {
                catalogManager.getMusicForGenre(selectedGenreId, page = fallbackPage)
            }
            val fallbackFresh = fallbackResults.filter { !existingIds.contains(it.videoId) }
            if (fallbackFresh.isNotEmpty()) {
                videos = videos + fallbackFresh
                currentPage = fallbackPage
            } else if (currentPage > 30) {
                hasMore = false
            }
        }
        isLoadingMore = false
    }

    fun playVideoAtIndex(index: Int) {
        if (!isAccessAllowed || index !in videos.indices) return
        val current = videos[index]
        authRepo.addMusicToHistory(current)
        if (index >= videos.size - 4 && hasMore && !isLoadingMore && selectedGenreId != "history") {
            coroutineScope.launch {
                fetchMoreVideosInternal()
            }
        }
        val next = videos.getOrNull(index + 1)
        val onNext: (() -> Unit) = {
            if (index + 1 < videos.size) {
                playVideoAtIndex(index + 1)
            } else if (selectedGenreId != "history") {
                coroutineScope.launch {
                    fetchMoreVideosInternal()
                    if (index + 1 < videos.size) {
                        playVideoAtIndex(index + 1)
                    }
                }
            }
        }
        val onPrevious: (() -> Unit) = {
            if (index > 0) {
                playVideoAtIndex(index - 1)
            }
        }
        onPlayYouTubeVideo(
            current.videoId,
            "${current.artistName} - ${current.title}",
            onNext,
            next?.let { "${it.artistName} - ${it.title}" },
            onPrevious
        )
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uiModeManager = remember { context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager }
    val isTv = remember { uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION }
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
            if (total > 0 && last >= total - 8 && !isLoading && !isLoadingMore && hasMore) {
                fetchMoreVideosInternal()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CinemaBackground)) {
        if (!isAccessAllowed) {
            Box(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.widthIn(max = 480.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = CinemaPrimary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CinemaPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Access Restricted",
                                tint = CinemaAccent,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    Text(
                        text = "ACCESS RESTRICTED",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Music and Podcasts require verified active credentials. Please enter and verify your active subscription credentials in Settings to unlock access.",
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    if (onOpenSettings != null) {
                        TvFocusableCard(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(10.dp),
                            backgroundColor = CinemaPrimary,
                            focusedBorderColor = CinemaFocus,
                            focusedScale = 1.05f,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("Open Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                .padding(if (isMobile) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isMobile) 8.dp else 12.dp)
        ) {
            // Top Bar: Title & Search
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isMobile) 10.dp else 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = CinemaPrimary,
                        modifier = Modifier.size(if (isMobile) 22.dp else 28.dp)
                    )
                    Text(
                        text = "MUSIC",
                        fontSize = if (isMobile) 18.sp else 24.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }

                AppSearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = if (isMobile) "Search songs, artists, videos..." else "Search songs, artists, albums, or music videos...",
                    modifier = Modifier.weight(1f)
                )
            }

            // Categories Bar (Trending, History and Genres)
            val allGenres = remember {
                listOf(
                    com.tvdinner.data.model.MusicGenre("history", "🕒 History", "🕒")
                ) + com.tvdinner.data.music.MusicData.GENRES
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

            // Main Content: Infinite Scrolling Music Videos Grid
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CinemaAccent)
                }
            } else if (videos.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isMobile) 2 else 4),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(if (isMobile) 10.dp else 14.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isMobile) 10.dp else 14.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    gridItems(videos, key = { it.id }) { video ->
                        val idx = videos.indexOfFirst { it.id == video.id }
                        TvFocusableCard(
                            onClick = {
                                playVideoAtIndex(if (idx >= 0) idx else 0)
                            },
                            shape = RoundedCornerShape(10.dp),
                            backgroundColor = CinemaSurface,
                            focusedBorderColor = CinemaFocus,
                            focusedScale = 1.04f,
                            modifier = Modifier.fillMaxWidth()
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
    }
}
}
