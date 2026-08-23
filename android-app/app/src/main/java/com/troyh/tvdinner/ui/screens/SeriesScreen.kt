package com.troyh.tvdinner.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.troyh.tvdinner.data.model.Episode
import com.troyh.tvdinner.data.model.Series
import com.troyh.tvdinner.data.model.SeriesCategory
import com.troyh.tvdinner.data.model.SeriesInfoResponse
import com.troyh.tvdinner.data.network.XtreamApiClient
import com.troyh.tvdinner.data.repository.AuthRepository
import com.troyh.tvdinner.data.repository.CatalogManager
import com.troyh.tvdinner.ui.components.TvFocusableCard
import com.troyh.tvdinner.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EpisodeResumePrompt(
    val title: String,
    val streamUrl: String,
    val streamKey: String,
    val savedPos: Long
)

@Composable
fun SeriesScreen(
    authRepo: AuthRepository,
    apiClient: XtreamApiClient,
    catalogManager: CatalogManager,
    onPlayEpisode: (String, String, Long, String) -> Unit, // (url, title, startPosMs, streamKey)
    modifier: Modifier = Modifier
) {
    val sortOptions = listOf("🔥 Latest & Trending", "⭐ Top Rated", "📅 New Releases", "🔤 A - Z")
    var currentSort by rememberSaveable { mutableStateOf("🔥 Latest & Trending") }

    var categories by remember { mutableStateOf<List<SeriesCategory>>(emptyList()) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>("all") }
    var seriesList by remember { mutableStateOf<List<Series>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortedAndFilteredSeries by remember { mutableStateOf<List<Series>>(emptyList()) }
    var isSorting by remember { mutableStateOf(false) }

    var selectedSeries by remember { mutableStateOf<Series?>(null) }
    var seriesInfo by remember { mutableStateOf<SeriesInfoResponse?>(null) }
    var isLoadingInfo by remember { mutableStateOf(false) }
    var selectedSeason by remember { mutableStateOf("1") }
    var resumePromptEpisode by remember { mutableStateOf<EpisodeResumePrompt?>(null) }

    val portal = remember { authRepo.getVodPortalUrl() }
    val user = remember { authRepo.getVodUsername() }
    val pswd = remember { authRepo.getVodPassword() }

    val gridState = rememberLazyGridState()

    // Smart Fast Loading (Loads first category in ~150ms instead of all series)
    LaunchedEffect(Unit) {
        if (categories.isEmpty()) {
            isLoading = true
            val cats = catalogManager.getSeriesCategories()
            categories = cats
            val initialCatId = cats.firstOrNull { it.categoryId != "all" }?.categoryId ?: "all"
            selectedCategoryId = initialCatId
            seriesList = catalogManager.getSeries(initialCatId)
            isLoading = false
        }
    }

    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null && categories.isNotEmpty()) {
            isLoading = true
            seriesList = catalogManager.getSeries(selectedCategoryId)
            isLoading = false
        }
    }

    LaunchedEffect(selectedSeries) {
        selectedSeries?.let { s ->
            isLoadingInfo = true
            seriesInfo = catalogManager.getSeriesInfo(s.seriesId)
            val firstSeason = seriesInfo?.episodes?.keys?.sortedBy { it.toIntOrNull() ?: 0 }?.firstOrNull() ?: "1"
            selectedSeason = firstSeason
            isLoadingInfo = false
        }
    }

    // High-Performance Background Sorting (Zero UI lag / No Fire TV Stick crash)
    LaunchedEffect(seriesList, searchQuery, currentSort) {
        isSorting = true
        sortedAndFilteredSeries = withContext(Dispatchers.Default) {
            val base = if (searchQuery.isBlank()) {
                seriesList
            } else {
                seriesList.filter { it.displayTitle.contains(searchQuery, ignoreCase = true) }
            }

            when (currentSort) {
                "⭐ Top Rated" -> base.sortedByDescending { it.rating5Based ?: 0.0 }
                "📅 New Releases" -> {
                    base.sortedWith(
                        compareByDescending<Series> {
                            val rd = it.releaseDate
                            if (!rd.isNullOrBlank() && rd.length >= 4) {
                                rd.substring(0, 4).toIntOrNull() ?: 0
                            } else 0
                        }
                        .thenByDescending { it.seriesId }
                    )
                }
                "🔤 A - Z" -> base.sortedBy { it.displayTitle.lowercase() }
                else -> {
                    base.sortedWith(
                        compareByDescending<Series> {
                            val rd = it.releaseDate
                            if (!rd.isNullOrBlank() && rd.length >= 4) {
                                rd.substring(0, 4).toIntOrNull() ?: 0
                            } else 0
                        }
                        .thenByDescending { it.rating5Based ?: 0.0 }
                        .thenByDescending { it.seriesId }
                    )
                }
            }
        }
        isSorting = false
    }

    Box(modifier = modifier.fillMaxSize().background(CinemaBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header, Search & Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "TV SERIES VOD",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search series...", color = TextMuted, fontSize = 13.sp) },
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

            // Modern Sorting Filter Pills
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Sort by:",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                for (opt in sortOptions) {
                    val isOptSelected = currentSort == opt
                    TvFocusableCard(
                        onClick = { currentSort = opt },
                        shape = RoundedCornerShape(6.dp),
                        backgroundColor = if (isOptSelected) CinemaAccent.copy(alpha = 0.2f) else Color.Transparent,
                        focusedBorderColor = CinemaFocus,
                        focusedScale = 1.03f
                    ) {
                        Text(
                            text = opt,
                            color = if (isOptSelected) CinemaAccent else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isOptSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Series Grid
            if (isLoading || isSorting) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CinemaAccent)
                }
            } else if (sortedAndFilteredSeries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No series found in this category", color = TextMuted)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    state = gridState,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedAndFilteredSeries, key = { it.seriesId }) { series ->
                        TvFocusableCard(
                            onClick = { selectedSeries = series },
                            shape = RoundedCornerShape(12.dp),
                            backgroundColor = CinemaSurface,
                            focusedBorderColor = CinemaFocus,
                            focusedScale = 1.05f,
                            modifier = Modifier.fillMaxWidth().wrapContentHeight()
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                ) {
                                    if (!series.cover.isNullOrBlank()) {
                                        AsyncImage(
                                            model = series.cover,
                                            contentDescription = series.displayTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = CinemaSurfaceVariant,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.VideoLibrary,
                                                    contentDescription = null,
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (series.rating5Based != null && series.rating5Based > 0.0) {
                                        Surface(
                                            shape = RoundedCornerShape(bottomStart = 8.dp),
                                            color = Color.Black.copy(alpha = 0.75f),
                                            modifier = Modifier.align(Alignment.TopEnd)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = CinemaAccent,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = String.format("%.1f", series.rating5Based),
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = series.displayTitle,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Series Episodes Selector Dialog
        if (selectedSeries != null) {
            val series = selectedSeries!!
            val seasonsMap = seriesInfo?.episodes ?: emptyMap()
            val availableSeasons = seasonsMap.keys.sortedBy { it.toIntOrNull() ?: 0 }
            val currentEpisodes = seasonsMap[selectedSeason] ?: emptyList()

            Dialog(onDismissRequest = { selectedSeries = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CinemaSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                    modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Series Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = series.displayTitle,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Select Season and Episode to Play",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }

                            IconButton(onClick = { selectedSeries = null }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                            }
                        }

                        if (isLoadingInfo) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = CinemaAccent)
                            }
                        } else {
                            // Season Tabs
                            if (availableSeasons.isNotEmpty()) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(availableSeasons) { sNum ->
                                        val isSelSeason = selectedSeason == sNum
                                        TvFocusableCard(
                                            onClick = { selectedSeason = sNum },
                                            shape = RoundedCornerShape(8.dp),
                                            backgroundColor = if (isSelSeason) CinemaPrimary else CinemaSurfaceVariant,
                                            focusedBorderColor = CinemaFocus,
                                            focusedScale = 1.05f
                                        ) {
                                            Text(
                                                text = "Season $sNum",
                                                color = if (isSelSeason) Color.White else TextSecondary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelSeason) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Episode List
                            if (currentEpisodes.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("No episodes available for this season", color = TextMuted)
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(currentEpisodes) { ep ->
                                        val ext = ep.containerExtension.ifBlank { "mp4" }
                                        val streamIdInt = ep.id.toIntOrNull() ?: 0
                                        val streamUrl = apiClient.buildSeriesStreamUrl(portal, user, pswd, streamIdInt, ext)
                                        val epTitle = "${series.displayTitle} - S${selectedSeason}E${ep.episodeNum}: ${ep.title}"
                                        val streamKey = "ep_${series.seriesId}_${ep.id}"
                                        val savedPos = authRepo.getPlaybackPosition(streamKey)
                                        val savedDur = authRepo.getPlaybackDuration(streamKey)
                                        val hasHistory = savedPos >= 5_000L && (savedDur <= 0L || savedPos < savedDur - 15_000L)

                                        TvFocusableCard(
                                            onClick = {
                                                val currentSavedPos = authRepo.getPlaybackPosition(streamKey)
                                                val currentSavedDur = authRepo.getPlaybackDuration(streamKey)
                                                if (currentSavedPos >= 5_000L && (currentSavedDur <= 0L || currentSavedPos < currentSavedDur - 15_000L)) {
                                                    resumePromptEpisode = EpisodeResumePrompt(
                                                        title = epTitle,
                                                        streamUrl = streamUrl,
                                                        streamKey = streamKey,
                                                        savedPos = currentSavedPos
                                                    )
                                                } else {
                                                    // Automatic direct playback
                                                    selectedSeries = null
                                                    onPlayEpisode(streamUrl, epTitle, 0L, streamKey)
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            backgroundColor = CinemaSurfaceVariant,
                                            focusedBorderColor = CinemaFocus,
                                            focusedScale = 1.02f
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = CinemaPrimary,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = "E${ep.episodeNum}",
                                                            color = Color.White,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = ep.title.ifBlank { "Episode ${ep.episodeNum}" },
                                                        color = TextPrimary,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    if (hasHistory) {
                                                        Text(
                                                            text = "Watched to ${formatTimeMs(savedPos)}",
                                                            color = CinemaAccent,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    } else if (ep.info?.duration != null) {
                                                        Text(
                                                            text = "Duration: ${ep.info.duration}",
                                                            color = TextMuted,
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }

                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Play",
                                                    tint = CinemaAccent,
                                                    modifier = Modifier.size(24.dp)
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

        // Resume Episode Prompt
        if (resumePromptEpisode != null) {
            val prompt = resumePromptEpisode!!
            Dialog(onDismissRequest = { resumePromptEpisode = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CinemaSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                    modifier = Modifier.fillMaxWidth(0.9f).wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = prompt.title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Text(
                            text = "You previously watched this episode up to ${formatTimeMs(prompt.savedPos)}. Would you like to resume or start over?",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvFocusableCard(
                                onClick = {
                                    val pr = prompt
                                    resumePromptEpisode = null
                                    selectedSeries = null
                                    onPlayEpisode(pr.streamUrl, pr.title, pr.savedPos, pr.streamKey)
                                },
                                backgroundColor = CinemaPrimary,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Resume", tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Resume (${formatTimeMs(prompt.savedPos)})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            TvFocusableCard(
                                onClick = {
                                    val pr = prompt
                                    authRepo.clearPlaybackPosition(pr.streamKey)
                                    resumePromptEpisode = null
                                    selectedSeries = null
                                    onPlayEpisode(pr.streamUrl, pr.title, 0L, pr.streamKey)
                                },
                                backgroundColor = CinemaSurfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Replay, contentDescription = "Restart", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Start Over", color = TextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
