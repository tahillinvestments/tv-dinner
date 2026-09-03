package com.troyh.tvdinner.ui.screens

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.widget.Toast
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import com.troyh.tvdinner.data.model.Movie
import com.troyh.tvdinner.data.model.MovieCategory
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

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState

@Composable
fun MoviesScreen(
    authRepo: AuthRepository,
    apiClient: XtreamApiClient,
    catalogManager: CatalogManager,
    onPlayMovie: (String, String, Long, String) -> Unit, // (url, title, startPosMs, streamKey)
    isPlayingFullscreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    var categories by remember { mutableStateOf<List<MovieCategory>>(emptyList()) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(authRepo.getLastMovieCategoryId()) }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var lastPlayedMovieId by rememberSaveable { mutableIntStateOf(authRepo.getLastMovieStreamId()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var resumePromptMovie by remember { mutableStateOf<Movie?>(null) }
    var sortedAndFilteredMovies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isSorting by remember { mutableStateOf(false) }
    var movieWatchlistIds by remember { mutableStateOf(authRepo.getMovieWatchlistIds()) }
    val coroutineScope = rememberCoroutineScope()

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
            if (newCatId != "watchlist") {
                authRepo.setLastMovieCategoryId(newCatId)
            }
        }
    }

    // Smart Fast Loading (Loads first or remembered category)
    LaunchedEffect(Unit) {
        if (categories.isEmpty()) {
            isLoading = true
            val rawCats = catalogManager.getMovieCategories()
            val cats = listOf(MovieCategory("watchlist", "⭐ Watchlist")) + rawCats
            categories = cats
            val savedCat = authRepo.getLastMovieCategoryId()
            val initialCatId = if (savedCat != null && cats.any { it.categoryId == savedCat }) {
                savedCat
            } else if (rawCats.isNotEmpty()) {
                rawCats.first().categoryId
            } else {
                "watchlist"
            }
            selectedCategoryId = initialCatId
            movies = if (initialCatId == "watchlist") {
                catalogManager.getWatchlistMovies()
            } else if (initialCatId.isNotBlank()) {
                catalogManager.getMovies(initialCatId)
            } else {
                emptyList()
            }
            isLoading = false
        }
    }

    LaunchedEffect(selectedCategoryId) {
        if (!selectedCategoryId.isNullOrBlank() && categories.isNotEmpty() && searchQuery.isBlank()) {
            isLoading = true
            if (selectedCategoryId != "watchlist") {
                authRepo.setLastMovieCategoryId(selectedCategoryId ?: "")
            }
            movies = if (selectedCategoryId == "watchlist") {
                catalogManager.getWatchlistMovies()
            } else {
                catalogManager.getMovies(selectedCategoryId)
            }
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

    // High-performance search across entire VOD movie catalog with debounce (Adult excluded unless Adult tab selected)
    LaunchedEffect(searchQuery, selectedCategoryId) {
        if (searchQuery.isNotBlank()) {
            delay(300) // Debounce rapid keystrokes to prevent OOM / network spikes
            isLoading = true
            searchResults = catalogManager.searchMovies(searchQuery, selectedCategoryId)
            isLoading = false
        } else {
            searchResults = emptyList()
        }
    }

    // High-Performance Smart Sorting (Zero UI lag / No Fire TV Stick crash)
    LaunchedEffect(movies, searchResults, searchQuery) {
        isSorting = true
        sortedAndFilteredMovies = withContext(Dispatchers.Default) {
            val base = if (searchQuery.isNotBlank()) {
                searchResults
            } else {
                movies
            }

            // Smart Sort: Newest releases (2026/2025/2024), added timestamp, rating, and streamId descending
            base.sortedWith(
                compareByDescending<Movie> { extractMovieReleaseYear(it).coerceAtLeast(2023) }
                    .thenByDescending { it.added?.toLongOrNull() ?: (it.streamId.toLong()) }
                    .thenByDescending { it.rating5Based ?: 0.0 }
                    .thenByDescending { it.streamId }
            )
        }
        isSorting = false
    }

    // Restore scroll position when category changes or default to top
    LaunchedEffect(selectedCategoryId, sortedAndFilteredMovies) {
        if (selectedCategoryId != null && sortedAndFilteredMovies.isNotEmpty() && searchQuery.isBlank()) {
            val savedPos = categoryScrollPositions[selectedCategoryId]
            if (savedPos != null && savedPos in sortedAndFilteredMovies.indices) {
                gridState.scrollToItem(savedPos)
            } else {
                gridState.scrollToItem(0)
            }
        }
    }

    // Proactively preload posters for the first visible page in viewing priority
    LaunchedEffect(sortedAndFilteredMovies) {
        if (sortedAndFilteredMovies.isNotEmpty()) {
            catalogManager.preloadMoviePosters(sortedAndFilteredMovies, limit = 24)
        }
    }

    // Auto-scroll to the movie card that was just playing when returning from fullscreen
    LaunchedEffect(isPlayingFullscreen, lastPlayedMovieId) {
        if (!isPlayingFullscreen && lastPlayedMovieId > 0 && sortedAndFilteredMovies.isNotEmpty()) {
            val idx = sortedAndFilteredMovies.indexOfFirst { it.streamId == lastPlayedMovieId }
            if (idx >= 0) {
                gridState.scrollToItem((idx - 2).coerceAtLeast(0))
            }
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
                        text = "MOVIES VOD",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )

                    AppSearchBar(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search movies...",
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

                // Movie Grid
                if (isLoading || isSorting) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CinemaAccent)
                    }
                } else if (sortedAndFilteredMovies.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = if (selectedCategoryId == "watchlist") "Your Movie Watchlist is empty" else "No movies found in this category",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (selectedCategoryId == "watchlist") {
                                Text(
                                    text = "Click and hold (long press) on any movie to save to Watchlist!",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 105.dp),
                        state = gridState,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        items(sortedAndFilteredMovies, key = { it.streamId }) { movie ->
                            val streamKey = "movie_${movie.streamId}"
                            val savedPos = authRepo.getPlaybackPosition(streamKey)
                            val savedDur = authRepo.getPlaybackDuration(streamKey)
                            val hasHistory = savedPos >= 5_000L && (savedDur <= 0L || savedPos < savedDur - 15_000L)

                            TvFocusableCard(
                                onClick = {
                                    val currentSavedPos = authRepo.getPlaybackPosition(streamKey)
                                    val currentSavedDur = authRepo.getPlaybackDuration(streamKey)
                                    val ext = movie.containerExtension.ifBlank { "mp4" }
                                    val streamUrl = apiClient.buildMovieStreamUrl(portal, user, pswd, movie.streamId, ext)
                                    lastPlayedMovieId = movie.streamId
                                    authRepo.setLastMovieStreamId(movie.streamId)
                                    if (currentSavedPos >= 5_000L && (currentSavedDur <= 0L || currentSavedPos < currentSavedDur - 15_000L)) {
                                        resumePromptMovie = movie
                                    } else {
                                        onPlayMovie(streamUrl, movie.displayTitle, 0L, streamKey)
                                    }
                                },
                                onLongClick = {
                                    val added = authRepo.toggleMovieWatchlist(movie.streamId)
                                    movieWatchlistIds = authRepo.getMovieWatchlistIds()
                                    Toast.makeText(
                                        context,
                                        if (added) "Saved '${movie.displayTitle}' to Watchlist ⭐" else "Removed '${movie.displayTitle}' from Watchlist",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (selectedCategoryId == "watchlist") {
                                        coroutineScope.launch {
                                            movies = catalogManager.getWatchlistMovies()
                                        }
                                    }
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
                                        MoviePosterImage(movie = movie, catalogManager = catalogManager)

                                        // Watchlist Bookmark Tag
                                        if (movieWatchlistIds.contains(movie.streamId)) {
                                            Surface(
                                                shape = RoundedCornerShape(bottomEnd = 8.dp),
                                                color = CinemaAccent.copy(alpha = 0.95f),
                                                modifier = Modifier.align(Alignment.TopStart)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Bookmark,
                                                    contentDescription = "In Watchlist",
                                                    tint = Color.Black,
                                                    modifier = Modifier.padding(4.dp).size(12.dp)
                                                )
                                            }
                                        }

                                        // Rating Tag
                                        if (movie.rating5Based != null && movie.rating5Based > 0.0) {
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
                                                        text = String.format("%.1f", movie.rating5Based),
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // Resume Progress Bar
                                        if (hasHistory && savedDur > 0L) {
                                            val progress = (savedPos.toFloat() / savedDur.toFloat()).coerceIn(0f, 1f)
                                            Surface(
                                                color = Color.Black.copy(alpha = 0.7f),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .align(Alignment.BottomCenter)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(progress)
                                                        .background(CinemaPrimary)
                                                )
                                            }
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = movie.displayTitle,
                                            color = TextPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
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

                // Right Main Content: Search & Movie Posters Grid
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
                            text = "MOVIES VOD",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )

                        AppSearchBar(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = "Search movies catalog...",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Movie Grid
                    if (isLoading || isSorting) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CinemaAccent)
                        }
                    } else if (sortedAndFilteredMovies.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(24.dp)) {
                                Text(
                                    text = if (selectedCategoryId == "watchlist") "Your Movie Watchlist is empty" else "No movies found in this category",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                if (selectedCategoryId == "watchlist") {
                                    Text(
                                        text = "Click and hold (long press) on any movie title to save it to your Watchlist!",
                                        color = TextMuted,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 140.dp),
                            state = gridState,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                    items(sortedAndFilteredMovies, key = { it.streamId }) { movie ->
                        val streamKey = "movie_${movie.streamId}"
                        val savedPos = authRepo.getPlaybackPosition(streamKey)
                        val savedDur = authRepo.getPlaybackDuration(streamKey)
                        val hasHistory = savedPos >= 5_000L && (savedDur <= 0L || savedPos < savedDur - 15_000L)

                        TvFocusableCard(
                            onClick = {
                                val currentSavedPos = authRepo.getPlaybackPosition(streamKey)
                                val currentSavedDur = authRepo.getPlaybackDuration(streamKey)
                                val ext = movie.containerExtension.ifBlank { "mp4" }
                                val streamUrl = apiClient.buildMovieStreamUrl(portal, user, pswd, movie.streamId, ext)
                                lastPlayedMovieId = movie.streamId
                                if (currentSavedPos >= 5_000L && (currentSavedDur <= 0L || currentSavedPos < currentSavedDur - 15_000L)) {
                                    resumePromptMovie = movie
                                } else {
                                    // Automatic direct playback for new/unwatched movies
                                    onPlayMovie(streamUrl, movie.displayTitle, 0L, streamKey)
                                }
                            },
                            onLongClick = {
                                val added = authRepo.toggleMovieWatchlist(movie.streamId)
                                movieWatchlistIds = authRepo.getMovieWatchlistIds()
                                Toast.makeText(
                                    context,
                                    if (added) "Saved '${movie.displayTitle}' to Watchlist ⭐" else "Removed '${movie.displayTitle}' from Watchlist",
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (selectedCategoryId == "watchlist") {
                                    coroutineScope.launch {
                                        movies = catalogManager.getWatchlistMovies()
                                    }
                                }
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
                                    MoviePosterImage(movie = movie, catalogManager = catalogManager)

                                    // Watchlist Bookmark Tag
                                    if (movieWatchlistIds.contains(movie.streamId)) {
                                        Surface(
                                            shape = RoundedCornerShape(bottomEnd = 8.dp),
                                            color = CinemaAccent.copy(alpha = 0.95f),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bookmark,
                                                contentDescription = "In Watchlist",
                                                tint = Color.Black,
                                                modifier = Modifier.padding(4.dp).size(13.dp)
                                            )
                                        }
                                    }

                                    // Rating Tag
                                    if (movie.rating5Based != null && movie.rating5Based > 0.0) {
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
                                                    text = String.format("%.1f", movie.rating5Based),
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Resume Progress Bar at bottom of poster if partially watched
                                    if (hasHistory && savedDur > 0L) {
                                        val progress = (savedPos.toFloat() / savedDur.toFloat()).coerceIn(0f, 1f)
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .align(Alignment.BottomCenter)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(progress)
                                                    .background(CinemaPrimary)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = movie.displayTitle,
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

    // Resume or Play from Beginning Dialog
    if (resumePromptMovie != null) {
            val movie = resumePromptMovie!!
            val streamKey = "movie_${movie.streamId}"
            val savedPos = authRepo.getPlaybackPosition(streamKey)
            val ext = movie.containerExtension.ifBlank { "mp4" }
            val streamUrl = apiClient.buildMovieStreamUrl(portal, user, pswd, movie.streamId, ext)

            Dialog(onDismissRequest = { resumePromptMovie = null }) {
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
                            text = movie.displayTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        Text(
                            text = "You previously watched this movie up to ${formatTimeMs(savedPos)}. Would you like to resume or start over?",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Resume Button
                            TvFocusableCard(
                                onClick = {
                                    lastPlayedMovieId = movie.streamId
                                    resumePromptMovie = null
                                    onPlayMovie(streamUrl, movie.displayTitle, savedPos, streamKey)
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
                                    Text("Resume (${formatTimeMs(savedPos)})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // Play from Beginning Button
                            TvFocusableCard(
                                onClick = {
                                    lastPlayedMovieId = movie.streamId
                                    authRepo.clearPlaybackPosition(streamKey)
                                    resumePromptMovie = null
                                    onPlayMovie(streamUrl, movie.displayTitle, 0L, streamKey)
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

private fun extractMovieReleaseYear(movie: Movie): Int {
    val rd = movie.releaseDate
    if (!rd.isNullOrBlank()) {
        val trimmed = rd.trim()
        val match = Regex("""(19\d{2}|20\d{2})""").find(trimmed)
        if (match != null) {
            val y = match.value.toIntOrNull()
            if (y != null && y in 1900..2030) return y
        }
    }
    val title = movie.displayTitle
    val titleMatch = Regex("""\((19\d{2}|20\d{2})\)""").find(title)
        ?: Regex("""\b(202[0-9]|201[0-9])\b""").find(title)
    if (titleMatch != null) {
        val yearStr = titleMatch.groupValues.getOrNull(1) ?: titleMatch.value
        val y = yearStr.filter { it.isDigit() }.toIntOrNull()
        if (y != null && y in 1900..2030) return y
    }
    return 0
}

@Composable
fun MoviePosterImage(
    movie: Movie,
    catalogManager: CatalogManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var posterUrl by remember(movie.streamId, movie.streamIcon) {
        val initial = if (!movie.streamIcon.isNullOrBlank() && movie.streamIcon.startsWith("http") &&
            !movie.streamIcon.endsWith(".ts") && !movie.streamIcon.endsWith(".m3u8")) {
            movie.streamIcon
        } else {
            catalogManager.getCachedPosterUrl(movie.displayTitle, isSeries = false)
        }
        mutableStateOf(initial)
    }

    LaunchedEffect(movie.streamId, movie.displayTitle, movie.streamIcon) {
        if (posterUrl.isNullOrBlank() || posterUrl?.startsWith("http") != true) {
            val resolved = catalogManager.resolvePosterUrl(movie.displayTitle, isSeries = false)
            if (!resolved.isNullOrBlank()) {
                posterUrl = resolved
            }
        }
    }

    if (!posterUrl.isNullOrBlank()) {
        val imageRequest = remember(posterUrl) {
            coil.request.ImageRequest.Builder(context)
                .data(posterUrl)
                .crossfade(true)
                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .build()
        }
        SubcomposeAsyncImage(
            model = imageRequest,
            contentDescription = movie.displayTitle,
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
                    val resolved = catalogManager.resolvePosterUrl(movie.displayTitle, isSeries = false)
                    if (!resolved.isNullOrBlank() && resolved != posterUrl) {
                        posterUrl = resolved
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
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
