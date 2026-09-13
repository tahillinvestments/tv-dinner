package com.tvdinner.ui.screens

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.tvdinner.data.model.Channel
import com.tvdinner.data.model.EpgProgram
import com.tvdinner.data.model.Movie
import com.tvdinner.data.model.PodcastEpisode
import com.tvdinner.data.model.Series
import com.tvdinner.data.model.ShortEpgResponse
import com.tvdinner.data.network.XtreamApiClient
import com.tvdinner.data.podcasts.PodcastsData
import com.tvdinner.data.repository.AuthRepository
import com.tvdinner.data.repository.CatalogManager
import com.tvdinner.player.ExoPlayerManager
import com.tvdinner.ui.components.AccessRestrictedView
import com.tvdinner.ui.components.TvFocusableCard
import com.tvdinner.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class NowItem {
    data class LiveChannelItem(val channel: Channel) : NowItem()
    data class MovieItem(val movie: Movie) : NowItem()
    data class SeriesItem(val series: Series) : NowItem()
    data class PodcastItem(val episode: PodcastEpisode) : NowItem()
}

private fun canonicalChannelBrand(rawName: String): String {
    val clean = CatalogManager.cleanChannelDisplayName(rawName).uppercase()
    return clean
        .replace(Regex("\\b(EAST|WEST|FEED|BACKUP|BK|RAW|ALT|FHD|UHD|4K|HD|SD|HEVC|H265|720P|1080P)\\b"), "")
        .replace(Regex("[\\(\\)\\[\\]\\|\\:\\-\\.]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

@Composable
fun NowScreen(
    authRepo: AuthRepository,
    apiClient: XtreamApiClient,
    catalogManager: CatalogManager,
    playerManager: ExoPlayerManager,
    onPlayLiveChannel: (Channel) -> Unit,
    onPlayMovie: (Movie) -> Unit,
    onPlaySeries: (Series) -> Unit,
    onPlayYouTubeVideo: (String, String, (() -> Unit)?, String?, (() -> Unit)?) -> Unit,
    onSelectPodcast: ((PodcastEpisode) -> Unit)? = null,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCredentialsVerified by authRepo.isCredentialsVerifiedState.collectAsState()
    val activeUsername by authRepo.activeUsernameState.collectAsState()
    val activePassword by authRepo.activePasswordState.collectAsState()
    val isAccessAllowed = activeUsername.isNotBlank() && activePassword.isNotBlank() && isCredentialsVerified

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val uiModeManager = remember { context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager }
    val isTv = remember { uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION }
    val isCompact = configuration.screenWidthDp < 600
    val isMobile = !isTv && (configuration.orientation == Configuration.ORIENTATION_PORTRAIT || isCompact)

    // Current Time Clock State (updates every 30 seconds)
    var currentTimeString by remember {
        mutableStateOf(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            currentTimeString = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        }
    }

    val isPersonalizationEnabled = remember { authRepo.isNowPersonalizationEnabled() }

    // Live Data States
    var liveBroadcastChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var epgMap by remember { mutableStateOf<Map<Int, ShortEpgResponse>>(emptyMap()) }
    var trendingMovies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var trendingSeries by remember { mutableStateOf<List<Series>>(emptyList()) }
    var podcastEpisodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }
    var favoriteChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var watchlistMovies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var watchlistSeries by remember { mutableStateOf<List<Series>>(emptyList()) }
    var mashupItems by remember { mutableStateOf<List<NowItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch and Curate Data
    LaunchedEffect(isPersonalizationEnabled, isAccessAllowed) {
        if (!isAccessAllowed) {
            liveBroadcastChannels = emptyList()
            trendingMovies = emptyList()
            trendingSeries = emptyList()
            podcastEpisodes = emptyList()
            favoriteChannels = emptyList()
            watchlistMovies = emptyList()
            watchlistSeries = emptyList()
            mashupItems = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        coroutineScope {
            val liveDeferred = async(Dispatchers.IO) {
                try {
                    catalogManager.getLiveChannels("all")
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val moviesDeferred = async(Dispatchers.IO) {
                try {
                    catalogManager.getMovies()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val watchMoviesDeferred = async(Dispatchers.IO) {
                try {
                    catalogManager.getWatchlistMovies()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val seriesDeferred = async(Dispatchers.IO) {
                try {
                    catalogManager.getSeries()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val watchSeriesDeferred = async(Dispatchers.IO) {
                try {
                    catalogManager.getWatchlistSeries()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            val podcastsDeferred = async(Dispatchers.IO) {
                try {
                    val trendingPodcastChannels = PodcastsData.CHANNELS.take(6)
                    val gathered = mutableListOf<PodcastEpisode>()
                    for (ch in trendingPodcastChannels) {
                        val fetchedEps = catalogManager.getPodcastEpisodesForChannel(ch)
                        if (fetchedEps.isNotEmpty()) {
                            gathered.add(fetchedEps.first())
                        }
                    }
                    gathered
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val allLive = liveDeferred.await()
            val movies = moviesDeferred.await()
            val watchMovies = watchMoviesDeferred.await()
            val seriesList = seriesDeferred.await()
            val watchSeries = watchSeriesDeferred.await()
            val eps = podcastsDeferred.await()

            // 1. Process Live Channels & Favorites
            val favIds = authRepo.getFavoriteChannelIds()
            val favChannels = allLive.filter { favIds.contains(it.streamId) }
            favoriteChannels = favChannels

            // Pick top featured broadcast channels (HBO, ESPN, Fox Sports, CNN, NBC, etc.) deduplicated by canonical brand
            val featuredKeywords = listOf("HBO", "ESPN", "FOX SPORTS", "FS1", "CNN", "NBC", "CBS", "DISCOVERY", "COMEDY", "TNT", "USA")
            val candidateChannels = (if (allLive.isNotEmpty()) {
                val matched = allLive.filter { ch ->
                    val u = ch.name.uppercase()
                    featuredKeywords.any { u.contains(it) } && !CatalogManager.isAdultName(ch.name)
                }
                if (matched.isNotEmpty()) matched else allLive
            } else emptyList()).distinctBy { canonicalChannelBrand(it.name) }

            // Fetch EPG for candidate live channels concurrently in small chunks
            val newEpgMap = java.util.concurrent.ConcurrentHashMap<Int, ShortEpgResponse>()
            candidateChannels.take(24).chunked(6).forEach { chunk ->
                val deferreds = chunk.map { ch ->
                    async(Dispatchers.IO) {
                        try {
                            val epg = catalogManager.getFullEpgForChannel(ch.streamId)
                            if (epg != null) {
                                newEpgMap[ch.streamId] = epg
                            }
                        } catch (_: Exception) {}
                    }
                }
                deferreds.awaitAll()
            }
            epgMap = newEpgMap

            // Deduplicate programs so identical shows are not suggested multiple times
            val seenPrograms = mutableSetOf<String>()
            val curatedLive = mutableListOf<Channel>()
            for (ch in candidateChannels) {
                val prog = catalogManager.resolveCurrentProgram(newEpgMap[ch.streamId]?.epgListings)?.title?.trim()
                if (!prog.isNullOrBlank() && prog.length > 2) {
                    val normProg = prog.uppercase().replace(Regex("[^A-Z0-9]"), "")
                    if (seenPrograms.contains(normProg)) {
                        continue
                    }
                    seenPrograms.add(normProg)
                }
                curatedLive.add(ch)
                if (curatedLive.size >= 16) break
            }
            liveBroadcastChannels = curatedLive

            // 2. Movies & Watchlist
            watchlistMovies = watchMovies
            trendingMovies = movies.take(20)

            // 3. Series & Watchlist
            watchlistSeries = watchSeries
            trendingSeries = seriesList.take(20)

            // 4. Podcasts
            podcastEpisodes = eps

            // 5. Build Surf-Saver Smart Mashup
            val mashup = mutableListOf<NowItem>()
            if (isPersonalizationEnabled) {
                // Personalized: Interleave saved watchlist movies/series, favorites, and top podcasts
                if (watchMovies.isNotEmpty()) {
                    watchMovies.take(3).forEach { mashup.add(NowItem.MovieItem(it)) }
                }
                if (favChannels.isNotEmpty()) {
                    favChannels.take(3).forEach { mashup.add(NowItem.LiveChannelItem(it)) }
                }
                if (watchSeries.isNotEmpty()) {
                    watchSeries.take(3).forEach { mashup.add(NowItem.SeriesItem(it)) }
                }
                if (eps.isNotEmpty()) {
                    eps.take(2).forEach { mashup.add(NowItem.PodcastItem(it)) }
                }
                // If user has minimal saves yet, fill with top picks
                if (mashup.size < 6) {
                    trendingMovies.take(3).forEach { mashup.add(NowItem.MovieItem(it)) }
                    trendingSeries.take(2).forEach { mashup.add(NowItem.SeriesItem(it)) }
                }
            } else {
                // Generic Trending Mode: High-octane blend of global hits
                val mSample = trendingMovies.take(4)
                val sSample = trendingSeries.take(3)
                val pSample = eps.take(3)
                val lSample = liveBroadcastChannels.take(3)

                var i = 0
                while (i < 4) {
                    if (i < mSample.size) mashup.add(NowItem.MovieItem(mSample[i]))
                    if (i < lSample.size) mashup.add(NowItem.LiveChannelItem(lSample[i]))
                    if (i < sSample.size) mashup.add(NowItem.SeriesItem(sSample[i]))
                    if (i < pSample.size) mashup.add(NowItem.PodcastItem(pSample[i]))
                    i++
                }
            }
            mashupItems = mashup
            isLoading = false
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CinemaBackground)) {
        if (!isAccessAllowed) {
            AccessRestrictedView(
                featureName = "The Now page",
                onOpenSettings = onOpenSettings
            )
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CinemaAccent)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isMobile) 12.dp else 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Header / Hero Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CinemaPrimary.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaPrimary.copy(alpha = 0.4f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Now Hub",
                                    tint = CinemaAccent,
                                    modifier = Modifier.padding(8.dp).size(26.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "NOW PLAYING & DISCOVERY",
                                    fontSize = if (isMobile) 18.sp else 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Real-time broadcasts, smart recommendations & 1-click play",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        // Live Clock & Mode Pill Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CinemaSurfaceVariant,
                                border = androidx.compose.foundation.BorderStroke(1.dp, CinemaSurfaceLight)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Time",
                                        tint = CinemaAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = currentTimeString,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                            }

                            // Mode Pill
                            TvFocusableCard(
                                onClick = onOpenSettings,
                                shape = RoundedCornerShape(8.dp),
                                backgroundColor = if (isPersonalizationEnabled) CinemaPrimary.copy(alpha = 0.25f) else CinemaYellow.copy(alpha = 0.2f),
                                focusedBorderColor = CinemaFocus,
                                modifier = Modifier.wrapContentSize()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPersonalizationEnabled) Icons.Default.Tune else Icons.Default.Whatshot,
                                        contentDescription = "Mode",
                                        tint = if (isPersonalizationEnabled) CinemaAccent else CinemaYellow,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isPersonalizationEnabled) "PERSONALIZED" else "GENERIC TRENDING",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isPersonalizationEnabled) CinemaAccent else CinemaYellow
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 1: On Air Right Now (Live Broadcasts with EPG progress)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(CinemaRed)
                            )
                            Text(
                                text = "ON AIR RIGHT NOW",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "• Live TV EPG Progress",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(liveBroadcastChannels, key = { it.streamId }) { channel ->
                                val shortEpg = epgMap[channel.streamId]
                                val currentProg = catalogManager.resolveCurrentProgram(shortEpg?.epgListings)
                                val currentEpoch = System.currentTimeMillis() / 1000L
                                val startSec = catalogManager.parseEpgEpoch(currentProg?.startTimestamp, currentProg?.start)
                                val stopSec = catalogManager.parseEpgEpoch(currentProg?.stopTimestamp, currentProg?.end)

                                val progress: Float = if (startSec != null && stopSec != null && stopSec > startSec) {
                                    ((currentEpoch - startSec).toFloat() / (stopSec - startSec).toFloat()).coerceIn(0.05f, 1f)
                                } else {
                                    0.45f
                                }

                                val remainingMin: Long = if (stopSec != null && stopSec > currentEpoch) {
                                    ((stopSec - currentEpoch) / 60).coerceAtLeast(1)
                                } else {
                                    25
                                }

                                val nextProg = shortEpg?.epgListings?.let { list ->
                                    val idx = list.indexOfFirst { it.id == currentProg?.id }
                                    if (idx >= 0 && idx + 1 < list.size) list[idx + 1] else null
                                }

                                TvFocusableCard(
                                    onClick = { onPlayLiveChannel(channel) },
                                    shape = RoundedCornerShape(12.dp),
                                    backgroundColor = CinemaSurface,
                                    focusedBorderColor = CinemaFocus,
                                    focusedScale = 1.06f,
                                    modifier = Modifier.width(if (isMobile) 240.dp else 280.dp).height(175.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(14.dp),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = CatalogManager.cleanChannelDisplayName(channel.name),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = CinemaRed,
                                                modifier = Modifier.padding(start = 6.dp)
                                            ) {
                                                Text(
                                                    text = "LIVE",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        // Currently Airing Program
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                text = currentProg?.decodedTitle ?: "Live Transmission",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            // Progress Bar
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    color = CinemaAccent,
                                                    trackColor = CinemaSurfaceLight,
                                                    modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp))
                                                )
                                                Text(
                                                    text = "${remainingMin}m left",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = CinemaAccent
                                                )
                                            }
                                        }

                                        // Up Next
                                        Text(
                                            text = if (nextProg != null) "Next: ${nextProg.decodedTitle}" else "Next: Scheduled Broadcast",
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 2: Surf-Saver Smart Mashup
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "Mashup",
                                    tint = CinemaYellow,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "SURF-SAVER SMART MASHUP",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (isPersonalizationEnabled) "• Tailored to your tastes" else "• Trending hits",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(mashupItems) { item ->
                                when (item) {
                                    is NowItem.MovieItem -> {
                                        val movie = item.movie
                                        TvFocusableCard(
                                            onClick = { onPlayMovie(movie) },
                                            shape = RoundedCornerShape(12.dp),
                                            backgroundColor = CinemaSurface,
                                            focusedBorderColor = CinemaFocus,
                                            focusedScale = 1.06f,
                                            modifier = Modifier.width(if (isMobile) 130.dp else 160.dp).wrapContentHeight()
                                        ) {
                                            Column {
                                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                                                    MoviePosterImage(
                                                        movie = movie,
                                                        catalogManager = catalogManager,
                                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                                                        color = CinemaPrimary.copy(alpha = 0.95f),
                                                        modifier = Modifier.align(Alignment.TopStart)
                                                    ) {
                                                        Text("MOVIE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                    }
                                                }
                                                Text(
                                                    text = movie.displayTitle,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                    is NowItem.SeriesItem -> {
                                        val series = item.series
                                        TvFocusableCard(
                                            onClick = { onPlaySeries(series) },
                                            shape = RoundedCornerShape(12.dp),
                                            backgroundColor = CinemaSurface,
                                            focusedBorderColor = CinemaFocus,
                                            focusedScale = 1.06f,
                                            modifier = Modifier.width(if (isMobile) 130.dp else 160.dp).wrapContentHeight()
                                        ) {
                                            Column {
                                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                                                    SeriesPosterImage(
                                                        series = series,
                                                        catalogManager = catalogManager,
                                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                                                        color = CinemaSecondary.copy(alpha = 0.95f),
                                                        modifier = Modifier.align(Alignment.TopStart)
                                                    ) {
                                                        Text("SERIES", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                    }
                                                }
                                                Text(
                                                    text = series.displayTitle,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                    is NowItem.PodcastItem -> {
                                        val ep = item.episode
                                        TvFocusableCard(
                                            onClick = { onSelectPodcast?.invoke(ep) ?: onPlayYouTubeVideo(ep.videoId, "${ep.channelName} - ${ep.title}", null, null, null) },
                                            shape = RoundedCornerShape(12.dp),
                                            backgroundColor = CinemaSurface,
                                            focusedBorderColor = CinemaFocus,
                                            focusedScale = 1.06f,
                                            modifier = Modifier.width(if (isMobile) 180.dp else 220.dp).wrapContentHeight()
                                        ) {
                                            Column {
                                                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                                                    AsyncImage(
                                                        model = ep.thumbnailUrl,
                                                        contentDescription = ep.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(bottomEnd = 6.dp),
                                                        color = CinemaAccent.copy(alpha = 0.95f),
                                                        modifier = Modifier.align(Alignment.TopStart)
                                                    ) {
                                                        Text("PODCAST", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                    }
                                                }
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(ep.channelName, fontSize = 10.sp, color = CinemaAccent, fontWeight = FontWeight.Bold, maxLines = 1)
                                                    Text(ep.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                    is NowItem.LiveChannelItem -> {
                                        val ch = item.channel
                                        TvFocusableCard(
                                            onClick = { onPlayLiveChannel(ch) },
                                            shape = RoundedCornerShape(12.dp),
                                            backgroundColor = CinemaSurface,
                                            focusedBorderColor = CinemaFocus,
                                            focusedScale = 1.06f,
                                            modifier = Modifier.width(if (isMobile) 180.dp else 220.dp).wrapContentHeight()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Surface(shape = RoundedCornerShape(4.dp), color = CinemaRed) {
                                                        Text("LIVE TV", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                                    }
                                                    Text(CatalogManager.cleanChannelDisplayName(ch.name), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                Text("Watch Live Broadcast", fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 3: Trending Premieres (Movies & Series)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = "Trending",
                                tint = CinemaPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "TRENDING PREMIERES & BLOCKBUSTERS",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(trendingMovies.take(12), key = { "m_${it.streamId}" }) { movie ->
                                TvFocusableCard(
                                    onClick = { onPlayMovie(movie) },
                                    shape = RoundedCornerShape(12.dp),
                                    backgroundColor = CinemaSurface,
                                    focusedBorderColor = CinemaFocus,
                                    focusedScale = 1.06f,
                                    modifier = Modifier.width(if (isMobile) 130.dp else 160.dp).wrapContentHeight()
                                ) {
                                    Column {
                                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                                            MoviePosterImage(
                                                movie = movie,
                                                catalogManager = catalogManager,
                                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                            )
                                            if (movie.rating5Based != null && movie.rating5Based > 0) {
                                                Surface(
                                                    shape = RoundedCornerShape(bottomStart = 6.dp),
                                                    color = Color.Black.copy(alpha = 0.8f),
                                                    modifier = Modifier.align(Alignment.TopEnd)
                                                ) {
                                                    Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Star, contentDescription = null, tint = CinemaYellow, modifier = Modifier.size(10.dp))
                                                        Text(String.format(Locale.US, "%.1f", movie.rating5Based), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                        Text(
                                            text = movie.displayTitle,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 4: Fresh Podcasts
                if (podcastEpisodes.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Podcasts,
                                    contentDescription = "Podcasts",
                                    tint = CinemaAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "FRESH PODCASTS & SHOWS",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(podcastEpisodes, key = { it.videoId }) { ep ->
                                    TvFocusableCard(
                                        onClick = { onSelectPodcast?.invoke(ep) ?: onPlayYouTubeVideo(ep.videoId, "${ep.channelName} - ${ep.title}", null, null, null) },
                                        shape = RoundedCornerShape(12.dp),
                                        backgroundColor = CinemaSurface,
                                        focusedBorderColor = CinemaFocus,
                                        focusedScale = 1.06f,
                                        modifier = Modifier.width(if (isMobile) 200.dp else 240.dp).wrapContentHeight()
                                    ) {
                                        Column {
                                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                                                AsyncImage(
                                                    model = ep.thumbnailUrl,
                                                    contentDescription = ep.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                                                    color = Color.Black.copy(alpha = 0.75f),
                                                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(ep.channelName, fontSize = 11.sp, color = CinemaAccent, fontWeight = FontWeight.Bold, maxLines = 1)
                                                Text(ep.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 5: My Favorites (Live Channels)
                if (favoriteChannels.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Star, contentDescription = "Favorites", tint = CinemaYellow, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "MY FAVORITE CHANNELS",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(favoriteChannels, key = { "fav_${it.streamId}" }) { ch ->
                                    TvFocusableCard(
                                        onClick = { onPlayLiveChannel(ch) },
                                        shape = RoundedCornerShape(12.dp),
                                        backgroundColor = CinemaSurface,
                                        focusedBorderColor = CinemaFocus,
                                        focusedScale = 1.06f,
                                        modifier = Modifier.width(if (isMobile) 160.dp else 200.dp).wrapContentHeight()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            val logo = CatalogManager.resolveChannelLogoUrl(ch.name, ch.streamIcon)
                                            if (!logo.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = logo,
                                                    contentDescription = ch.name,
                                                    modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp))
                                                )
                                            }
                                            Text(
                                                text = CatalogManager.cleanChannelDisplayName(ch.name),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
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

                // SECTION 6: My Watchlists (Movies & Series)
                if (watchlistMovies.isNotEmpty() || watchlistSeries.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = "Watchlist", tint = CinemaAccent, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "MY WATCHLIST",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            }

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(watchlistMovies, key = { "wl_m_${it.streamId}" }) { movie ->
                                    TvFocusableCard(
                                        onClick = { onPlayMovie(movie) },
                                        shape = RoundedCornerShape(12.dp),
                                        backgroundColor = CinemaSurface,
                                        focusedBorderColor = CinemaFocus,
                                        focusedScale = 1.06f,
                                        modifier = Modifier.width(if (isMobile) 130.dp else 160.dp).wrapContentHeight()
                                    ) {
                                        Column {
                                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                                                MoviePosterImage(
                                                    movie = movie,
                                                    catalogManager = catalogManager,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                                                    color = CinemaPrimary,
                                                    modifier = Modifier.align(Alignment.TopStart)
                                                ) {
                                                    Text("MOVIE", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                }
                                            }
                                            Text(
                                                text = movie.displayTitle,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                }

                                items(watchlistSeries, key = { "wl_s_${it.seriesId}" }) { series ->
                                    TvFocusableCard(
                                        onClick = { onPlaySeries(series) },
                                        shape = RoundedCornerShape(12.dp),
                                        backgroundColor = CinemaSurface,
                                        focusedBorderColor = CinemaFocus,
                                        focusedScale = 1.06f,
                                        modifier = Modifier.width(if (isMobile) 130.dp else 160.dp).wrapContentHeight()
                                    ) {
                                        Column {
                                            Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                                                SeriesPosterImage(
                                                    series = series,
                                                    catalogManager = catalogManager,
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                                                    color = CinemaSecondary,
                                                    modifier = Modifier.align(Alignment.TopStart)
                                                ) {
                                                    Text("SERIES", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                }
                                            }
                                            Text(
                                                text = series.displayTitle,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextPrimary,
                                                maxLines = 1,
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
        }
    }
}
