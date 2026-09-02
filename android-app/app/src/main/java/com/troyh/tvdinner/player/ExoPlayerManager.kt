package com.troyh.tvdinner.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import com.troyh.tvdinner.data.network.XtreamApiClient
import com.troyh.tvdinner.data.repository.AuthRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val shortLabel: String,
    val language: String?,
    val mimeType: String?,
    val channelCount: Int,
    val isSelected: Boolean,
    val isSupported: Boolean
)

class ExoPlayerManager(
    private val context: Context,
    private val xtreamApiClient: XtreamApiClient
) {
    private val tag = "ExoPlayerManager"

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentStreamUrl = MutableStateFlow("")
    val currentStreamUrl: StateFlow<String> = _currentStreamUrl.asStateFlow()

    private val _currentTitle = MutableStateFlow("")
    val currentTitle: StateFlow<String> = _currentTitle.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isLiveStream = MutableStateFlow(false)
    val isLiveStream: StateFlow<Boolean> = _isLiveStream.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isStreamStalled = MutableStateFlow(false)
    val isStreamStalled: StateFlow<Boolean> = _isStreamStalled.asStateFlow()

    private val _resizeMode = MutableStateFlow(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    val resizeMode: StateFlow<Int> = _resizeMode.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrackInfo>> = _audioTracks.asStateFlow()

    private val _selectedAudioTrack = MutableStateFlow<AudioTrackInfo?>(null)
    val selectedAudioTrack: StateFlow<AudioTrackInfo?> = _selectedAudioTrack.asStateFlow()

    var player: ExoPlayer? = null
        private set

    var authRepo: AuthRepository? = null
    var currentStreamKey: String? = null

    // Scrub Acceleration State
    private var lastSeekTime = 0L
    private var seekMagnitudeMs = 15_000L
    private var liveRecoveryAttempt = 0
    private var vodRecoveryAttempt = 0

    private var progressJob: Job? = null
    private var bufferWatchdogJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        initializePlayer()
    }

    fun recoverLiveStream(forceFailover: Boolean = false) {
        val currentUrl = _currentStreamUrl.value
        if (currentUrl.isBlank() || !_isLiveStream.value) return
        liveRecoveryAttempt++
        Log.w(tag, "Recovering live stream: attempt $liveRecoveryAttempt (current: $currentUrl, forceFailover: $forceFailover)")

        // 1. Immediately terminate hung connections and free socket descriptors
        try {
            xtreamApiClient.okHttpClient.dispatcher.cancelAll()
            xtreamApiClient.okHttpClient.connectionPool.evictAll()
        } catch (_: Exception) {}

        // 2. Stop player to close current socket
        try {
            player?.stop()
            player?.clearMediaItems()
        } catch (_: Exception) {}

        // 3. Failover to backup portal if bad HTTP status or second attempt
        var nextUrl = currentUrl
        if (forceFailover || liveRecoveryAttempt >= 2) {
            val failover = authRepo?.getFailoverUrl(currentUrl)
            if (!failover.isNullOrBlank() && failover != currentUrl) {
                Log.i(tag, "Failing over live stream to backup portal: $failover")
                nextUrl = failover
            }
        }

        // 4. Re-initiate stream fresh
        playStream(
            url = nextUrl,
            title = _currentTitle.value,
            isLive = true,
            recoveryAttempt = liveRecoveryAttempt
        )
    }

    fun recoverVodStream(forceFailover: Boolean = false) {
        val currentUrl = _currentStreamUrl.value
        if (currentUrl.isBlank() || _isLiveStream.value) return
        vodRecoveryAttempt++
        val resumePos = _currentPosition.value
        Log.w(tag, "Recovering VOD stream: attempt $vodRecoveryAttempt from ${resumePos}ms (url: $currentUrl, forceFailover: $forceFailover)")

        // 1. Evict any hung sockets in OkHttp connection pool
        try {
            xtreamApiClient.okHttpClient.dispatcher.cancelAll()
            xtreamApiClient.okHttpClient.connectionPool.evictAll()
        } catch (_: Exception) {}

        // 2. Stop player
        try {
            player?.stop()
            player?.clearMediaItems()
        } catch (_: Exception) {}

        // 3. Failover to backup portal if bad HTTP status or second attempt
        var nextUrl = currentUrl
        if (forceFailover || vodRecoveryAttempt >= 2) {
            val failover = authRepo?.getFailoverUrl(currentUrl)
            if (!failover.isNullOrBlank() && failover != currentUrl) {
                Log.i(tag, "Failing over VOD stream to backup portal: $failover")
                nextUrl = failover
            }
        }

        playStream(
            url = nextUrl,
            title = _currentTitle.value,
            isLive = false,
            startPositionMs = resumePos,
            streamKey = currentStreamKey
        )
    }

    fun reconnectCurrentStream() {
        liveRecoveryAttempt = 0
        vodRecoveryAttempt = 0
        _errorMessage.value = null
        if (_isLiveStream.value) {
            recoverLiveStream(forceFailover = true)
        } else {
            recoverVodStream(forceFailover = true)
        }
    }

    private fun initializePlayer() {
        if (player != null) return

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioCapabilities(androidx.media3.exoplayer.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(true)
                    .build()
            }
        }.apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
            setEnableAudioTrackPlaybackParams(true)
            setEnableAudioFloatOutput(false)
        }

        // Buffer durations tuned for instant direct start and zero artificial latency
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                3000,  // minBufferMs (3s buffer threshold)
                30000, // maxBufferMs (30s max buffer)
                500,   // bufferForPlaybackMs (instant startup in 500ms)
                1000   // bufferForPlaybackAfterRebufferMs (1s recovery)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
            )
        }

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(xtreamApiClient.okHttpClient)
            .setUserAgent("VLC/3.0.21 LibVLC/3.0.21")

        val dataSourceFactory = DefaultDataSource.Factory(context, okHttpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguage("en")
                    .setSelectUndeterminedTextLanguage(true)
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setAllowAudioNonSeamlessAdaptiveness(true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            )
        }

        player = ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                volume = 1.0f
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _isBuffering.value = true
                                bufferWatchdogJob?.cancel()
                                bufferWatchdogJob = scope.launch {
                                    if (_isLiveStream.value) {
                                        delay(3500)
                                        if (_isBuffering.value && _isLiveStream.value) {
                                            _isStreamStalled.value = true
                                        }
                                        delay(1500)
                                        if (_isBuffering.value && _isLiveStream.value && player != null) {
                                            Log.w(tag, "Live stream stalled in buffering for 5s. Auto-recovering...")
                                            recoverLiveStream(forceFailover = liveRecoveryAttempt >= 1)
                                        }
                                    } else {
                                        delay(4000)
                                        if (_isBuffering.value && !_isLiveStream.value) {
                                            _isStreamStalled.value = true
                                        }
                                        delay(4000)
                                        if (_isBuffering.value && !_isLiveStream.value && player != null) {
                                            Log.w(tag, "VOD playback stalled in buffering for 8s. Auto-recovering at ${_currentPosition.value}ms...")
                                            recoverVodStream(forceFailover = vodRecoveryAttempt >= 1)
                                        }
                                    }
                                }
                            }
                            Player.STATE_READY -> {
                                _isBuffering.value = false
                                _isStreamStalled.value = false
                                bufferWatchdogJob?.cancel()
                                liveRecoveryAttempt = 0
                                vodRecoveryAttempt = 0
                                _isPlaying.value = playWhenReady
                                _duration.value = if (duration == C.TIME_UNSET) 0L else duration
                                _errorMessage.value = null
                            }
                            Player.STATE_ENDED -> {
                                _isBuffering.value = false
                                _isStreamStalled.value = false
                                bufferWatchdogJob?.cancel()
                                _isPlaying.value = false
                                if (_isLiveStream.value) {
                                    recoverLiveStream()
                                } else {
                                    val key = currentStreamKey ?: _currentStreamUrl.value
                                    authRepo?.clearPlaybackPosition(key)
                                }
                            }
                            Player.STATE_IDLE -> {
                                _isBuffering.value = false
                                bufferWatchdogJob?.cancel()
                            }
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                    }

                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        ensureAudioTrackSelected(tracks)
                        updateAudioTracks(tracks)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(tag, "ExoPlayer playback error: ${error.message} (${error.errorCodeName})", error)
                        _isBuffering.value = false
                        _isPlaying.value = false
                        _isStreamStalled.value = true
                        bufferWatchdogJob?.cancel()

                        val isBadHttpStatus = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                                              error.cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException

                        _errorMessage.value = if (isBadHttpStatus) {
                            "Connection interrupted (HTTP error). Reconnecting..."
                        } else {
                            "Playback issue (${error.errorCodeName}). Reconnecting..."
                        }

                        if (_isLiveStream.value && liveRecoveryAttempt < 6) {
                            scope.launch {
                                delay(1000)
                                recoverLiveStream(forceFailover = isBadHttpStatus || liveRecoveryAttempt >= 1)
                            }
                            return
                        } else if (!_isLiveStream.value && vodRecoveryAttempt < 6) {
                            scope.launch {
                                delay(1200)
                                recoverVodStream(forceFailover = isBadHttpStatus || vodRecoveryAttempt >= 1)
                            }
                            return
                        }

                        _errorMessage.value = if (isBadHttpStatus) {
                            "Stream disconnected (Server busy/HTTP error). Click Reconnect Stream."
                        } else {
                            "Playback error: ${error.errorCodeName}. Click Reconnect Stream."
                        }
                    }
                })
            }

        startProgressTracking()
    }

    private fun ensureAudioTrackSelected(tracks: androidx.media3.common.Tracks) {
        var hasSelected = false
        var firstSupportedTrackIndex = -1
        var firstSupportedGroup: androidx.media3.common.Tracks.Group? = null

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (tIdx in 0 until group.length) {
                    if (group.isTrackSelected(tIdx)) {
                        hasSelected = true
                    }
                    if (group.isTrackSupported(tIdx) && firstSupportedGroup == null) {
                        firstSupportedTrackIndex = tIdx
                        firstSupportedGroup = group
                    }
                }
            }
        }

        if (!hasSelected && firstSupportedGroup != null && firstSupportedTrackIndex >= 0) {
            player?.let { p ->
                val override = androidx.media3.common.TrackSelectionOverride(
                    firstSupportedGroup.mediaTrackGroup,
                    listOf(firstSupportedTrackIndex)
                )
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setOverrideForType(override)
                    .build()
            }
        }
    }

    fun flushPositionNow() {
        player?.let { p ->
            val pos = p.currentPosition
            val dur = if (p.duration == C.TIME_UNSET) 0L else p.duration
            if (!_isLiveStream.value && pos >= 5_000L) {
                val key = currentStreamKey ?: _currentStreamUrl.value
                if (key.isNotBlank()) {
                    authRepo?.savePlaybackPosition(key, pos, dur)
                }
            }
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var saveCounter = 0
            while (isActive) {
                player?.let { p ->
                    if (p.isPlaying) {
                        val pos = p.currentPosition
                        _currentPosition.value = pos
                        val dur = p.duration
                        if (dur != C.TIME_UNSET && dur > 0) {
                            _duration.value = dur
                        }

                        // Periodic flush every ~2s for non-live VOD
                        if (!_isLiveStream.value && p.currentPosition >= 5_000L) {
                            saveCounter++
                            if (saveCounter >= 4) {
                                saveCounter = 0
                                flushPositionNow()
                            }
                        }
                    }
                }
                delay(500)
            }
        }
    }

    fun playStream(
        url: String,
        title: String,
        isLive: Boolean = false,
        startPositionMs: Long = 0L,
        streamKey: String? = null,
        recoveryAttempt: Int = 0
    ) {
        if (url.isBlank()) return
        Log.i(tag, "playStream: $title ($url) live=$isLive pos=$startPositionMs key=$streamKey attempt=$recoveryAttempt")
        liveRecoveryAttempt = recoveryAttempt

        // Flush old stream position if applicable
        flushPositionNow()

        val effectiveUrl = url

        _currentStreamUrl.value = effectiveUrl
        _currentTitle.value = title
        _isLiveStream.value = isLive
        currentStreamKey = streamKey
        _errorMessage.value = null
        _isBuffering.value = true

        initializePlayer()

        player?.apply {
            volume = 1.0f
            stop()
            clearMediaItems()
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(Uri.parse(effectiveUrl))
            if (isLive) {
                mediaItemBuilder.setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setMaxPlaybackSpeed(1.02f)
                        .setMinPlaybackSpeed(0.98f)
                        .build()
                )
            }
            if (effectiveUrl.contains(".m3u8", ignoreCase = true)) {
                mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
            }
            val mediaItem = mediaItemBuilder.build()
            setMediaItem(mediaItem)
            if (startPositionMs > 0 && !isLive) {
                seekTo(startPositionMs)
            }
            if (!isLive && authRepo?.isVodSubtitlesEnabled() == true) {
                _isClosedCaptionsEnabled.value = true
                val builder = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage("en")
                    .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE)
                    .setSelectUndeterminedTextLanguage(true)
                trackSelectionParameters = builder.build()
            }
            prepare()
            playWhenReady = true
        }
    }

    fun togglePlayPause() {
        player?.let { p ->
            if (p.isPlaying) {
                p.pause()
                _isPlaying.value = false
                flushPositionNow()
            } else {
                if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
                    if (_currentStreamUrl.value.isNotBlank()) {
                        playStream(_currentStreamUrl.value, _currentTitle.value, isLive = _isLiveStream.value)
                        return
                    } else {
                        p.prepare()
                    }
                }
                p.playWhenReady = true
                p.play()
                _isPlaying.value = true
            }
        }
    }

    fun play() {
        player?.let { p ->
            if (p.playbackState == Player.STATE_IDLE) {
                p.prepare()
            }
            p.playWhenReady = true
            p.play()
            _isPlaying.value = true
        }
    }

    fun pause() {
        player?.let { p ->
            p.pause()
            _isPlaying.value = false
            flushPositionNow()
        }
    }

    fun seekTo(posMs: Long) {
        player?.seekTo(posMs)
        _currentPosition.value = posMs
    }

    fun seekForward10s() {
        val now = System.currentTimeMillis()
        if (now - lastSeekTime < 1200) {
            seekMagnitudeMs = when (seekMagnitudeMs) {
                15_000L -> 30_000L
                30_000L -> 60_000L
                60_000L -> 120_000L
                120_000L -> 300_000L
                else -> 300_000L
            }
        } else {
            seekMagnitudeMs = 15_000L
        }
        lastSeekTime = now

        player?.let { p ->
            val target = (p.currentPosition + seekMagnitudeMs).coerceAtMost(if (p.duration > 0) p.duration else Long.MAX_VALUE)
            p.seekTo(target)
            _currentPosition.value = target
        }
    }

    fun seekRewind10s() {
        val now = System.currentTimeMillis()
        if (now - lastSeekTime < 1200) {
            seekMagnitudeMs = when (seekMagnitudeMs) {
                15_000L -> 30_000L
                30_000L -> 60_000L
                60_000L -> 120_000L
                120_000L -> 300_000L
                else -> 300_000L
            }
        } else {
            seekMagnitudeMs = 15_000L
        }
        lastSeekTime = now

        player?.let { p ->
            val target = (p.currentPosition - seekMagnitudeMs).coerceAtLeast(0)
            p.seekTo(target)
            _currentPosition.value = target
        }
    }

    fun cycleAspectRatio() {
        _resizeMode.value = when (_resizeMode.value) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private val _isClosedCaptionsEnabled = MutableStateFlow(false)
    val isClosedCaptionsEnabled: StateFlow<Boolean> = _isClosedCaptionsEnabled.asStateFlow()

    fun toggleClosedCaptions() {
        val newEnabled = !_isClosedCaptionsEnabled.value
        _isClosedCaptionsEnabled.value = newEnabled
        player?.let { p ->
            val builder = p.trackSelectionParameters.buildUpon()
            if (newEnabled) {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage("en")
                    .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE)
                    .setSelectUndeterminedTextLanguage(true)

                val currentTracks = p.currentTracks
                for (group in currentTracks.groups) {
                    if (group.type == C.TRACK_TYPE_TEXT && group.length > 0) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSupported(i)) {
                                builder.setOverrideForType(
                                    androidx.media3.common.TrackSelectionOverride(
                                        group.mediaTrackGroup,
                                        listOf(i)
                                    )
                                )
                                break
                            }
                        }
                    }
                }
            } else {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setPreferredTextLanguage(null)
                    .setPreferredTextRoleFlags(0)
            }
            p.trackSelectionParameters = builder.build()
        }
    }

    private fun updateAudioTracks(tracks: androidx.media3.common.Tracks) {
        val trackList = mutableListOf<AudioTrackInfo>()
        var selectedTrack: AudioTrackInfo? = null
        var hasSupportedSelected = false
        var firstSupportedTrackInfo: AudioTrackInfo? = null

        for ((gIdx, group) in tracks.groups.withIndex()) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (tIdx in 0 until group.length) {
                    val format = group.getTrackFormat(tIdx)
                    val isSelected = group.isTrackSelected(tIdx)
                    val isSupported = group.isTrackSupported(tIdx)

                    val (fullLabel, shortLabel) = formatAudioTrackNames(format, tIdx)
                    val info = AudioTrackInfo(
                        groupIndex = gIdx,
                        trackIndex = tIdx,
                        label = fullLabel,
                        shortLabel = shortLabel,
                        language = format.language,
                        mimeType = format.sampleMimeType,
                        channelCount = format.channelCount,
                        isSelected = isSelected,
                        isSupported = isSupported
                    )
                    trackList.add(info)

                    if (isSelected) {
                        selectedTrack = info
                        if (isSupported) {
                            hasSupportedSelected = true
                        }
                    }

                    if (isSupported && firstSupportedTrackInfo == null) {
                        firstSupportedTrackInfo = info
                    }
                }
            }
        }

        _audioTracks.value = trackList
        _selectedAudioTrack.value = selectedTrack

        // Automatic recovery: If the currently selected audio track is NOT supported
        // (e.g. unsupported DTS / surround codec on this hardware) but a supported audio track exists,
        // automatically select the supported audio track!
        if (!hasSupportedSelected && firstSupportedTrackInfo != null && trackList.size > 1) {
            Log.w(tag, "Selected audio track is unsupported. Automatically switching to supported audio track: ${firstSupportedTrackInfo.label}")
            selectAudioTrack(firstSupportedTrackInfo)
        }
    }

    private fun formatAudioTrackNames(format: androidx.media3.common.Format, trackIndex: Int): Pair<String, String> {
        val lang = format.language?.let {
            when (it.lowercase()) {
                "en", "eng" -> "English"
                "es", "spa" -> "Spanish"
                "fr", "fra", "fre" -> "French"
                "de", "deu", "ger" -> "German"
                "it", "ita" -> "Italian"
                "pt", "por" -> "Portuguese"
                "ru", "rus" -> "Russian"
                "zh", "zho", "chi" -> "Chinese"
                "ja", "jpn" -> "Japanese"
                "ko", "kor" -> "Korean"
                "ar", "ara" -> "Arabic"
                "hi", "hin" -> "Hindi"
                "und" -> null
                else -> it.uppercase()
            }
        }

        val codecName = when (format.sampleMimeType) {
            androidx.media3.common.MimeTypes.AUDIO_E_AC3, "audio/eac3" -> "Dolby Digital Plus (E-AC-3)"
            androidx.media3.common.MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Atmos (E-AC-3 JOC)"
            androidx.media3.common.MimeTypes.AUDIO_AC3, "audio/ac3" -> "Dolby Digital (AC-3)"
            androidx.media3.common.MimeTypes.AUDIO_AAC, "audio/mp4a-latm" -> "AAC"
            androidx.media3.common.MimeTypes.AUDIO_DTS, "audio/vnd.dts" -> "DTS"
            androidx.media3.common.MimeTypes.AUDIO_DTS_HD, "audio/vnd.dts.hd" -> "DTS-HD"
            androidx.media3.common.MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
            androidx.media3.common.MimeTypes.AUDIO_MPEG, "audio/mpeg" -> "MP3"
            androidx.media3.common.MimeTypes.AUDIO_OPUS, "audio/opus" -> "Opus"
            androidx.media3.common.MimeTypes.AUDIO_FLAC, "audio/flac" -> "FLAC"
            androidx.media3.common.MimeTypes.AUDIO_RAW -> "PCM"
            else -> format.sampleMimeType?.substringAfterLast('/')?.uppercase() ?: "Audio"
        }

        val channels = when (format.channelCount) {
            1 -> "Mono"
            2 -> "Stereo"
            6 -> "5.1 Surround"
            8 -> "7.1 Surround"
            else -> if (format.channelCount > 0) "${format.channelCount}ch" else ""
        }

        val customLabel = format.label?.takeIf { it.isNotBlank() }

        val fullLabel = buildString {
            if (!customLabel.isNullOrBlank()) {
                append(customLabel)
                append(" - ")
            } else if (!lang.isNullOrBlank()) {
                append(lang)
                append(" - ")
            } else {
                append("Track ${trackIndex + 1} - ")
            }
            append(codecName)
            if (channels.isNotBlank()) {
                append(" (")
                append(channels)
                append(")")
            }
        }

        val shortLabel = buildString {
            if (!lang.isNullOrBlank()) {
                append(lang)
                append(" ")
            }
            append(when (format.sampleMimeType) {
                androidx.media3.common.MimeTypes.AUDIO_E_AC3, "audio/eac3" -> "EAC3"
                androidx.media3.common.MimeTypes.AUDIO_AC3, "audio/ac3" -> "AC3"
                androidx.media3.common.MimeTypes.AUDIO_AAC, "audio/mp4a-latm" -> "AAC"
                androidx.media3.common.MimeTypes.AUDIO_DTS, "audio/vnd.dts" -> "DTS"
                else -> codecName.take(6)
            })
            if (channels == "5.1 Surround") append(" 5.1")
            else if (channels == "7.1 Surround") append(" 7.1")
        }.trim().ifBlank { "Audio ${trackIndex + 1}" }

        return Pair(fullLabel, shortLabel)
    }

    fun selectAudioTrack(trackInfo: AudioTrackInfo) {
        player?.let { p ->
            val groups = p.currentTracks.groups
            if (trackInfo.groupIndex in groups.indices) {
                val group = groups[trackInfo.groupIndex]
                val override = androidx.media3.common.TrackSelectionOverride(
                    group.mediaTrackGroup,
                    listOf(trackInfo.trackIndex)
                )
                p.trackSelectionParameters = p.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setOverrideForType(override)
                    .build()

                _selectedAudioTrack.value = trackInfo.copy(isSelected = true)
                _audioTracks.value = _audioTracks.value.map {
                    it.copy(isSelected = (it.groupIndex == trackInfo.groupIndex && it.trackIndex == trackInfo.trackIndex))
                }
            }
        }
    }

    fun cycleAudioTrack(): AudioTrackInfo? {
        val tracks = _audioTracks.value
        if (tracks.isEmpty()) return null
        val currentSelected = _selectedAudioTrack.value
        val currentIndex = if (currentSelected != null) {
            tracks.indexOfFirst { it.groupIndex == currentSelected.groupIndex && it.trackIndex == currentSelected.trackIndex }
        } else {
            -1
        }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % tracks.size else 0
        val nextTrack = tracks[nextIndex]
        selectAudioTrack(nextTrack)
        return nextTrack
    }

    fun resumeLive() {
        if (_currentStreamUrl.value.isNotBlank()) {
            if (player?.playbackState == Player.STATE_IDLE || (player?.mediaItemCount ?: 0) == 0) {
                playStream(_currentStreamUrl.value, _currentTitle.value, _isLiveStream.value)
            } else {
                player?.play()
            }
        }
    }

    fun stop() {
        flushPositionNow()
        player?.stop()
        player?.clearMediaItems()
        _isPlaying.value = false
        _isBuffering.value = false
    }

    fun release() {
        flushPositionNow()
        progressJob?.cancel()
        player?.release()
        player = null
    }
}
