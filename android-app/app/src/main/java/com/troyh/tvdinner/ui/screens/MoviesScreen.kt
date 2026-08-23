package com.troyh.tvdinner.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.troyh.tvdinner.data.model.Movie
import com.troyh.tvdinner.data.model.MovieCategory
import com.troyh.tvdinner.data.network.XtreamApiClient
import com.troyh.tvdinner.data.repository.AuthRepository
import com.troyh.tvdinner.data.repository.CatalogManager
import com.troyh.tvdinner.ui.components.TvFocusableCard
import com.troyh.tvdinner.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MoviesScreen(
    authRepo: AuthRepository,
    apiClient: XtreamApiClient,
    catalogManager: CatalogManager,
    onPlayMovie: (String, String, Long, String) -> Unit, // (url, title, startPosMs, streamKey)
    modifier: Modifier = Modifier
) {
    val sortOptions = listOf("🔥 Latest & Trending", "⭐ Top Rated", "📅 New Releases", "🔤 A - Z")
    var currentSort by rememberSaveable { mutableStateOf("🔥 Latest & Trending") }

    var categories by remember { mutableStateOf<List<MovieCategory>>(emptyList()) }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>("all") }
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var resumePromptMovie by remember { mutableStateOf<Movie?>(null) }
    var sortedAndFilteredMovies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var isSorting by remember { mutableStateOf(false) }

    val portal = remember { authRepo.getVodPortalUrl() }
    val user = remember { authRepo.getVodUsername() }
    val pswd = remember { authRepo.getVodPassword() }

    val gridState = rememberLazyGridState()

    // Smart Fast Loading (Loads first category in ~150ms instead of 30,000 movies)
    LaunchedEffect(Unit) {
        if (categories.isEmpty()) {
            isLoading = true
            val cats = catalogManager.getMovieCategories()
            categories = cats
            val initialCatId = cats.firstOrNull { it.categoryId != "all" }?.categoryId ?: "all"
            selectedCategoryId = initialCatId
            movies = catalogManager.getMovies(initialCatId)
            isLoading = false
        }
    }

    LaunchedEffect(selectedCategoryId) {
        if (selectedCategoryId != null && categories.isNotEmpty()) {
            isLoading = true
            movies = catalogManager.getMovies(selectedCategoryId)
            isLoading = false
        }
    }

    // High-Performance Background Sorting (Zero UI lag / No Fire TV Stick crash)
    LaunchedEffect(movies, searchQuery, currentSort) {
        isSorting = true
        sortedAndFilteredMovies = withContext(Dispatchers.Default) {
            val base = if (searchQuery.isBlank()) {
                movies
            } else {
                movies.filter { it.displayTitle.contains(searchQuery, ignoreCase = true) }
            }

            when (currentSort) {
                "⭐ Top Rated" -> base.sortedByDescending { it.rating5Based ?: 0.0 }
                "📅 New Releases" -> {
                    base.sortedWith(
                        compareByDescending<Movie> { extractMovieReleaseYear(it) }
                            .thenByDescending { it.added?.toLongOrNull() ?: 0L }
                            .thenByDescending { it.streamId }
                    )
                }
                "🔤 A - Z" -> base.sortedBy { it.displayTitle.lowercase() }
                else -> {
                    // "🔥 Latest & Trending": 2026/2025/2024 releases, newest added timestamp, high rating, or stream_id descending
                    base.sortedWith(
                        compareByDescending<Movie> { extractMovieReleaseYear(it).coerceAtLeast(2023) }
                            .thenByDescending { it.added?.toLongOrNull() ?: (it.streamId.toLong()) }
                            .thenByDescending { it.rating5Based ?: 0.0 }
                            .thenByDescending { it.streamId }
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
            // Header, Search & Sort
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "MOVIES VOD",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search movies catalog...", color = TextMuted, fontSize = 13.sp) },
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

            // Movie Grid
            if (isLoading || isSorting) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CinemaAccent)
                }
            } else if (sortedAndFilteredMovies.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No movies found in this category", color = TextMuted)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    state = gridState,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
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
                                if (currentSavedPos >= 5_000L && (currentSavedDur <= 0L || currentSavedPos < currentSavedDur - 15_000L)) {
                                    resumePromptMovie = movie
                                } else {
                                    // Automatic direct playback for new/unwatched movies
                                    onPlayMovie(streamUrl, movie.displayTitle, 0L, streamKey)
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
                                    if (!movie.streamIcon.isNullOrBlank()) {
                                        AsyncImage(
                                            model = movie.streamIcon,
                                            contentDescription = movie.displayTitle,
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
                                                    imageVector = Icons.Default.Movie,
                                                    contentDescription = null,
                                                    tint = TextMuted,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
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
