package com.troyh.tvdinner.ui.screens

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.troyh.tvdinner.data.model.Episode
import com.troyh.tvdinner.data.model.Series
import com.troyh.tvdinner.data.model.SeriesCategory
import com.troyh.tvdinner.data.model.SeriesInfoResponse
import com.troyh.tvdinner.data.network.XtreamApiClient
import com.troyh.tvdinner.data.repository.AuthRepository
import com.troyh.tvdinner.data.repository.CatalogManager
import com.troyh.tvdinner.ui.components.AppSearchBar
import com.troyh.tvdinner.ui.components.TvFocusableCard
import com.troyh.tvdinner.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.lazy.rememberLazyListState

data class EpisodeResumePrompt(
    val title: String,
    val streamUrl: String,
    val streamKey: String,
    val savedPos: Long,
    val onNext: (() -> Unit)? = null,
    val nextTitle: String? = null
)

@Composable
fun SeriesScreen(
    authRepo: AuthRepository,
    apiClient: XtreamApiClient,
    catalogManager: CatalogManager,
    onPlayEpisode: (String, String, Long, String, (() -> Unit)?, String?) -> Unit, // (url, title, startPosMs, streamKey, onNext, nextTitle)
    isPlayingFullscreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    var categories by remember { mutableStateOf<List<SeriesCategory>>(emptyList()) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(authRepo.getLastSeriesCategoryId()) }
    var seriesList by remember { mutableStateOf<List<Series>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<Series>>(emptyList()) }
    var lastSelectedSeriesId by rememberSaveable { mutableIntStateOf(authRepo.getLastSeriesId()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortedAndFilteredSeries by remember { mutableStateOf<List<Series>>(emptyList()) }
    var isSorting by remember { mutableStateOf(false) }

    var selectedSeries by remember { mutableStateOf<Series?>(null) }
    var seriesInfo by remember { mutableStateOf<SeriesInfoResponse?>(null) }
    var isLoadingInfo by remember { mutableStateOf(false) }
    var selectedSeason by remember { mutableStateOf("1") }
    var resumePromptEpisode by remember { mutableStateOf<EpisodeResumePrompt?>(null) }

    val portal = authRepo.getVodPortalUrl()
    val user = authRepo.getActiveUsername()
    val pswd = authRepo.getActivePassword()

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uiModeManager = remember { context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager }
    val isTv = remember { uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION }
    val isCompact = configuration.screenWidthDp < 600
    val isMobile = !isTv && (configuration.orientation == Configuration.ORIENTATION_PORTRAIT || isCompact)

    val gridState = rememberLazyGridState()
    val categoryListState = rememberLazyListState()
    val categoryScrollPositions = remember { mutableMapOf<String, Int>() }

    fun selectCategory(newCatId: String) {
        if (selectedCategoryId != newCatId) {
            selectedCategoryId?.let { current ->
                categoryScrollPositions[current] = gridState.firstVisibleItemIndex
            }
            selectedCategoryId = newCatId
            authRepo.setLastSeriesCategoryId(newCatId)
        }
    }

    // Smart Fast Loading (Loads first or remembered category)
    LaunchedEffect(Unit) {
        if (categories.isEmpty()) {
            isLoading = true
            val cats = catalogManager.getSeriesCategories()
            categories = cats
            val savedCat = authRepo.getLastSeriesCategoryId()
            val initialCatId = if (cats.any { it.categoryId == savedCat }) {
                savedCat ?: ""
            } else {
                cats.firstOrNull()?.categoryId ?: ""
            }
            selectedCategoryId = initialCatId
            if (initialCatId.isNotBlank()) {
                seriesList = catalogManager.getSeries(initialCatId)
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedCategoryId) {
        if (!selectedCategoryId.isNullOrBlank() && categories.isNotEmpty() && searchQuery.isBlank()) {
            isLoading = true
            seriesList = catalogManager.getSeries(selectedCategoryId)
            isLoading = false
        }
    }

    // Auto-scroll category into view in sidebar
    LaunchedEffect(selectedCategoryId, categories) {
        val idx = categories.indexOfFirst { it.categoryId == selectedCategoryId }
        if (idx >= 0) {
            categoryListState.animateScrollToItem((idx - 2).coerceAtLeast(0))
        }
    }

    // High-performance search across entire VOD series catalog with debounce (Adult excluded unless Adult tab selected)
    LaunchedEffect(searchQuery, selectedCategoryId) {
        if (searchQuery.isNotBlank()) {
            delay(300) // Debounce rapid keystrokes to prevent OOM / network spikes
            isLoading = true
            searchResults = catalogManager.searchSeries(searchQuery, selectedCategoryId)
            isLoading = false
        } else {
            searchResults = emptyList()
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

    // Scroll to the series card when the episode selector dialog is closed
    LaunchedEffect(selectedSeries, lastSelectedSeriesId, sortedAndFilteredSeries) {
        if (selectedSeries == null && lastSelectedSeriesId > 0 && sortedAndFilteredSeries.isNotEmpty()) {
            val idx = sortedAndFilteredSeries.indexOfFirst { it.seriesId == lastSelectedSeriesId }
            if (idx >= 0) {
                gridState.scrollToItem((idx - 2).coerceAtLeast(0))
            }
        }
    }

    // High-Performance Smart Sorting (Zero UI lag / No Fire TV Stick crash)
    LaunchedEffect(seriesList, searchResults, searchQuery) {
        isSorting = true
        sortedAndFilteredSeries = withContext(Dispatchers.Default) {
            val base = if (searchQuery.isNotBlank()) {
                searchResults
            } else {
                seriesList
            }

            // Smart Sort: Newest release year, highest rating, and seriesId descending
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
        isSorting = false
    }

    // Restore scroll position when category changes or default to top
    LaunchedEffect(selectedCategoryId, sortedAndFilteredSeries) {
        if (selectedCategoryId != null && sortedAndFilteredSeries.isNotEmpty() && searchQuery.isBlank()) {
            val savedPos = categoryScrollPositions[selectedCategoryId]
            if (savedPos != null && savedPos in sortedAndFilteredSeries.indices) {
                gridState.scrollToItem(savedPos)
            } else {
                gridState.scrollToItem(0)
            }
        }
    }

    // Proactively preload posters for the first visible page in viewing priority
    LaunchedEffect(sortedAndFilteredSeries) {
        if (sortedAndFilteredSeries.isNotEmpty()) {
            catalogManager.preloadSeriesPosters(sortedAndFilteredSeries, limit = 24)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CinemaBackground)) {
        if (isMobile) {
            // Mobile Portrait / Compact View: Single Column with horizontal categories
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header, Search & Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "TV SERIES VOD",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )

                    AppSearchBar(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search series...",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Categories Horizontal Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategoryId == cat.categoryId
                        TvFocusableCard(
                            onClick = { selectCategory(cat.categoryId) },
                            shape = RoundedCornerShape(8.dp),
                            backgroundColor = if (isSelected) CinemaPrimary else CinemaSurfaceVariant,
                            focusedBorderColor = CinemaFocus,
                            focusedScale = 1.05f
                        ) {
                            Text(
                                text = cat.categoryName,
                                color = if (isSelected) Color.White else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Series Grid
                if (isLoading || isSorting) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CinemaAccent)
                    }
                } else if (sortedAndFilteredSeries.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No series found in this category", color = TextMuted)
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 105.dp),
                        state = gridState,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        items(sortedAndFilteredSeries, key = { it.seriesId }) { series ->
                            TvFocusableCard(
                                onClick = {
                                    selectedSeries = series
                                    lastSelectedSeriesId = series.seriesId
                                    authRepo.setLastSeriesId(series.seriesId)
                                },
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
                                        SeriesPosterImage(series = series, catalogManager = catalogManager)

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
        } else {
            // TV / Desktop Layout: Dedicated Left Vertical Category Sidebar + Right Content Grid
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Categories Vertical Sidebar (Spacious, Vertical Scroll)
                Surface(
                    shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                    color = CinemaSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight),
                    modifier = Modifier
                        .width(230.dp)
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
                                TvFocusableCard(
                                    onClick = { selectCategory(cat.categoryId) },
                                    shape = RoundedCornerShape(10.dp),
                                    backgroundColor = if (isSelected) CinemaPrimary else CinemaSurfaceVariant,
                                    focusedBorderColor = CinemaFocus,
                                    focusedScale = 1.04f,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = cat.categoryName,
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

                // Right Main Content: Search & Series Posters Grid
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header & Search
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "TV SERIES VOD",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )

                        AppSearchBar(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search series catalog...",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Series Grid
                    if (isLoading || isSorting) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CinemaAccent)
                        }
                    } else if (sortedAndFilteredSeries.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No series found in this category", color = TextMuted)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            state = gridState,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            items(sortedAndFilteredSeries, key = { it.seriesId }) { series ->
                                TvFocusableCard(
                                    onClick = {
                                        selectedSeries = series
                                        lastSelectedSeriesId = series.seriesId
                                        authRepo.setLastSeriesId(series.seriesId)
                                    },
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
                                            SeriesPosterImage(series = series, catalogManager = catalogManager)

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
            }
        }

        // Series Episodes Selector Dialog (Hidden during fullscreen playback, restored upon escape)
        if (selectedSeries != null && !isPlayingFullscreen) {
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

                                        fun computeNextEpisode(currentEpId: String, season: String): Pair<(() -> Unit)?, String?> {
                                            val eps = seriesInfo?.episodes?.get(season) ?: emptyList()
                                            val idx = eps.indexOfFirst { it.id == currentEpId }
                                            val targetEp: Episode?
                                            val targetSeason: String
                                            if (idx in 0 until eps.size - 1) {
                                                targetEp = eps[idx + 1]
                                                targetSeason = season
                                            } else {
                                                val seasons = seriesInfo?.episodes?.keys?.sortedBy { it.toIntOrNull() ?: 0 } ?: emptyList()
                                                val sIdx = seasons.indexOf(season)
                                                if (sIdx in 0 until seasons.size - 1) {
                                                    targetSeason = seasons[sIdx + 1]
                                                    targetEp = seriesInfo?.episodes?.get(targetSeason)?.firstOrNull()
                                                } else {
                                                    targetEp = null
                                                    targetSeason = season
                                                }
                                            }

                                            if (targetEp != null) {
                                                val targetExt = targetEp.containerExtension.ifBlank { "mp4" }
                                                val targetStreamId = targetEp.id.toIntOrNull() ?: 0
                                                val targetUrl = apiClient.buildSeriesStreamUrl(portal, user, pswd, targetStreamId, targetExt)
                                                val targetTitle = "${series.displayTitle} - S${targetSeason}E${targetEp.episodeNum}: ${targetEp.title}"
                                                val targetKey = "ep_${series.seriesId}_${targetEp.id}"
                                                val (nextNextCallback, nextNextTitle) = computeNextEpisode(targetEp.id, targetSeason)
                                                val onNext: () -> Unit = {
                                                    onPlayEpisode(targetUrl, targetTitle, 0L, targetKey, nextNextCallback, nextNextTitle)
                                                }
                                                return Pair(onNext, "Next: S${targetSeason}E${targetEp.episodeNum}")
                                            }
                                            return Pair(null, null)
                                        }

                                        TvFocusableCard(
                                            onClick = {
                                                val currentSavedPos = authRepo.getPlaybackPosition(streamKey)
                                                val currentSavedDur = authRepo.getPlaybackDuration(streamKey)
                                                val (nextCallback, nextTitle) = computeNextEpisode(ep.id, selectedSeason)
                                                if (currentSavedPos >= 5_000L && (currentSavedDur <= 0L || currentSavedPos < currentSavedDur - 15_000L)) {
                                                    resumePromptEpisode = EpisodeResumePrompt(
                                                        title = epTitle,
                                                        streamUrl = streamUrl,
                                                        streamKey = streamKey,
                                                        savedPos = currentSavedPos,
                                                        onNext = nextCallback,
                                                        nextTitle = nextTitle
                                                    )
                                                } else {
                                                    // Start playback while retaining episode selection dialog state for return
                                                    onPlayEpisode(streamUrl, epTitle, 0L, streamKey, nextCallback, nextTitle)
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
                                    onPlayEpisode(pr.streamUrl, pr.title, pr.savedPos, pr.streamKey, pr.onNext, pr.nextTitle)
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
                                    onPlayEpisode(pr.streamUrl, pr.title, 0L, pr.streamKey, pr.onNext, pr.nextTitle)
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

@Composable
fun SeriesPosterImage(
    series: Series,
    catalogManager: CatalogManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var coverUrl by remember(series.seriesId, series.cover) {
        val initial = if (!series.cover.isNullOrBlank() && series.cover.startsWith("http") &&
            !series.cover.endsWith(".ts") && !series.cover.endsWith(".m3u8")) {
            series.cover
        } else {
            catalogManager.getCachedPosterUrl(series.displayTitle, isSeries = true)
        }
        mutableStateOf(initial)
    }

    LaunchedEffect(series.seriesId, series.displayTitle, series.cover) {
        if (coverUrl.isNullOrBlank() || coverUrl?.startsWith("http") != true) {
            val resolved = catalogManager.resolvePosterUrl(series.displayTitle, isSeries = true)
            if (!resolved.isNullOrBlank()) {
                coverUrl = resolved
            }
        }
    }

    if (!coverUrl.isNullOrBlank()) {
        val imageRequest = remember(coverUrl) {
            coil.request.ImageRequest.Builder(context)
                .data(coverUrl)
                .crossfade(true)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .build()
        }
        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = series.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
            loading = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CinemaSurfaceVariant)
                )
            },
            onError = {
                coroutineScope.launch {
                    val resolved = catalogManager.resolvePosterUrl(series.displayTitle, isSeries = true)
                    if (!resolved.isNullOrBlank() && resolved != coverUrl) {
                        coverUrl = resolved
                    }
                }
            }
        )
    } else {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = CinemaSurfaceVariant,
            modifier = modifier.fillMaxSize()
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
}
