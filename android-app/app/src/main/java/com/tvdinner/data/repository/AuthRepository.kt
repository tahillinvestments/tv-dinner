package com.tvdinner.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tvdinner.data.model.CredentialEntry
import com.tvdinner.data.model.MusicVideo
import com.tvdinner.data.model.PodcastEpisode

class AuthRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tvdinner_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_IPTV_PORTAL = "iptv_portal_url"
        private const val KEY_BACKUP_IPTV_PORTAL = "backup_iptv_portal_url"
        private const val KEY_VOD_PORTAL = "vod_portal_url"
        private const val KEY_ACTIVE_USERNAME = "active_xtream_username"
        private const val KEY_ACTIVE_PASSWORD = "active_xtream_password"
        private const val KEY_CREDENTIALS_VERIFIED = "credentials_verified"
        private const val KEY_CREDENTIALS_CLEARED = "credentials_explicitly_cleared"
        const val DEFAULT_SERVER_URL = "http://vpn.uhdp.top:80"
        const val BACKUP_SERVER_URL = "http://tv.wd.uhdp.top:80"

        val SERVER_PORTALS = listOf(
            "http://vpn.uhdp.top:80",
            "http://tv.wd.uhdp.top:80",
            "http://vpn.uhd4.top:80",
            "http://tv.wd.uhd4.top:80"
        )
    }

    private val _isCredentialsVerified = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean(KEY_CREDENTIALS_VERIFIED, false))
    val isCredentialsVerifiedState: kotlinx.coroutines.flow.StateFlow<Boolean> = _isCredentialsVerified

    private val _activeUsername = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString(KEY_ACTIVE_USERNAME, "")?.trim() ?: "")
    val activeUsernameState: kotlinx.coroutines.flow.StateFlow<String> = _activeUsername

    private val _activePassword = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString(KEY_ACTIVE_PASSWORD, "")?.trim() ?: "")
    val activePasswordState: kotlinx.coroutines.flow.StateFlow<String> = _activePassword

    private val _isMusicPodcastsCaptionsEnabled = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean("music_podcasts_captions_enabled", true))
    val isMusicPodcastsCaptionsEnabledState: kotlinx.coroutines.flow.StateFlow<Boolean> = _isMusicPodcastsCaptionsEnabled

    private val _appTheme = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString("app_theme", "light") ?: "light")
    val appThemeState: kotlinx.coroutines.flow.StateFlow<String> = _appTheme

    private val _isPersistentPreviewEnabled = kotlinx.coroutines.flow.MutableStateFlow(prefs.getBoolean("persistent_preview_enabled", true))
    val isPersistentPreviewEnabledState: kotlinx.coroutines.flow.StateFlow<Boolean> = _isPersistentPreviewEnabled

    fun getAppTheme(): String = prefs.getString("app_theme", "light") ?: "light"

    fun setAppTheme(theme: String) {
        prefs.edit().putString("app_theme", theme).apply()
        _appTheme.value = theme
    }

    fun isPersistentPreviewEnabled(): Boolean = prefs.getBoolean("persistent_preview_enabled", true)

    fun setPersistentPreviewEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("persistent_preview_enabled", enabled).apply()
        _isPersistentPreviewEnabled.value = enabled
    }

    fun isActivated(): Boolean {
        return true
    }

    fun isCredentialsVerified(): Boolean {
        return prefs.getBoolean(KEY_CREDENTIALS_VERIFIED, false)
    }

    fun setCredentialsVerified(verified: Boolean) {
        prefs.edit().putBoolean(KEY_CREDENTIALS_VERIFIED, verified).apply()
        _isCredentialsVerified.value = verified
    }

    fun hasValidCredentials(): Boolean {
        return getActiveUsername().isNotBlank() && getActivePassword().isNotBlank()
    }

    fun hasVerifiedActiveCredentials(): Boolean {
        return hasValidCredentials() && isCredentialsVerified()
    }

    fun isCredentialsCleared(): Boolean {
        return prefs.getBoolean(KEY_CREDENTIALS_CLEARED, false)
    }

    fun signOut() {
        clearCredentials()
    }

    fun getActiveUsername(): String {
        if (isCredentialsCleared()) return ""
        return prefs.getString(KEY_ACTIVE_USERNAME, "")?.trim() ?: ""
    }

    fun getActivePassword(): String {
        if (isCredentialsCleared()) return ""
        return prefs.getString(KEY_ACTIVE_PASSWORD, "")?.trim() ?: ""
    }

    fun getActiveLiveCredentials(): CredentialEntry {
        return CredentialEntry(user = getActiveUsername(), pswd = getActivePassword())
    }

    fun getOrderedServerPortals(): List<String> {
        val saved = prefs.getString(KEY_IPTV_PORTAL, null)?.trim()?.removeSuffix("/")
        if (!saved.isNullOrBlank() && SERVER_PORTALS.contains(saved)) {
            return listOf(saved) + SERVER_PORTALS.filter { it != saved }
        }
        return SERVER_PORTALS
    }

    fun getLivePortalUrl(): String {
        val saved = prefs.getString(KEY_IPTV_PORTAL, null)?.trim()?.removeSuffix("/")
        return if (!saved.isNullOrBlank() && SERVER_PORTALS.contains(saved)) {
            saved
        } else {
            DEFAULT_SERVER_URL
        }
    }

    fun setLivePortalUrl(url: String) {
        val clean = url.trim().removeSuffix("/")
        if (SERVER_PORTALS.contains(clean)) {
            prefs.edit().putString(KEY_IPTV_PORTAL, clean).apply()
        }
    }

    fun getBackupPortalUrl(): String {
        val saved = prefs.getString(KEY_BACKUP_IPTV_PORTAL, null)?.trim()?.removeSuffix("/")
        return if (!saved.isNullOrBlank() && SERVER_PORTALS.contains(saved)) {
            saved
        } else {
            BACKUP_SERVER_URL
        }
    }

    fun setBackupPortalUrl(url: String) {
        val clean = url.trim().removeSuffix("/")
        if (SERVER_PORTALS.contains(clean)) {
            prefs.edit().putString(KEY_BACKUP_IPTV_PORTAL, clean).apply()
        }
    }

    fun getFailoverUrl(currentUrl: String): String {
        for (i in SERVER_PORTALS.indices) {
            val portal = SERVER_PORTALS[i]
            if (currentUrl.contains(portal)) {
                val nextPortal = SERVER_PORTALS[(i + 1) % SERVER_PORTALS.size]
                return currentUrl.replace(portal, nextPortal)
            }
        }
        return currentUrl
    }

    fun getVodPortalUrl(): String {
        val saved = prefs.getString(KEY_VOD_PORTAL, null)?.trim()?.removeSuffix("/")
        return if (!saved.isNullOrBlank() && SERVER_PORTALS.contains(saved)) {
            saved
        } else {
            DEFAULT_SERVER_URL
        }
    }

    fun setVodPortalUrl(url: String) {
        val clean = url.trim().removeSuffix("/")
        if (SERVER_PORTALS.contains(clean)) {
            prefs.edit().putString(KEY_VOD_PORTAL, clean).apply()
        }
    }

    fun getVodUsername(): String = getActiveUsername()
    fun getVodPassword(): String = getActivePassword()

    fun setDirectCredentials(user: String, pswd: String) {
        val cleanU = user.trim()
        val cleanP = pswd.trim()
        val changed = cleanU != getActiveUsername() || cleanP != getActivePassword()

        val editor = prefs.edit()
            .putString(KEY_ACTIVE_USERNAME, cleanU)
            .putString(KEY_ACTIVE_PASSWORD, cleanP)
            .putBoolean(KEY_CREDENTIALS_CLEARED, false)

        if (changed) {
            editor.remove(KEY_CREDENTIALS_VERIFIED)
            _isCredentialsVerified.value = false
        }
        editor.apply()
        _activeUsername.value = cleanU
        _activePassword.value = cleanP
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_ACTIVE_USERNAME)
            .remove(KEY_ACTIVE_PASSWORD)
            .remove(KEY_CREDENTIALS_VERIFIED)
            .putBoolean(KEY_CREDENTIALS_CLEARED, true)
            .apply()
        _activeUsername.value = ""
        _activePassword.value = ""
        _isCredentialsVerified.value = false
    }

    // Podcast Subscriptions
    fun getSubscribedPodcastIds(): Set<String> {
        return prefs.getStringSet("subscribed_podcasts", emptySet()) ?: emptySet()
    }

    fun isPodcastSubscribed(channelId: String): Boolean {
        return getSubscribedPodcastIds().contains(channelId)
    }

    fun togglePodcastSubscription(channelId: String): Boolean {
        val current = getSubscribedPodcastIds().toMutableSet()
        val willSubscribe = !current.contains(channelId)
        if (willSubscribe) {
            current.add(channelId)
        } else {
            current.remove(channelId)
        }
        prefs.edit().putStringSet("subscribed_podcasts", current).apply()
        return willSubscribe
    }

    // VOD Watch History & Resume Memory
    fun savePlaybackPosition(streamKey: String, posMs: Long, durationMs: Long) {
        if (streamKey.isNotBlank()) {
            prefs.edit()
                .putLong("pos_$streamKey", posMs)
                .putLong("dur_$streamKey", durationMs)
                .apply()
        }
    }

    fun getPlaybackPosition(streamKey: String): Long {
        if (streamKey.isBlank()) return 0L
        return prefs.getLong("pos_$streamKey", 0L)
    }

    fun getPlaybackDuration(streamKey: String): Long {
        if (streamKey.isBlank()) return 0L
        return prefs.getLong("dur_$streamKey", 0L)
    }

    fun clearPlaybackPosition(streamKey: String) {
        if (streamKey.isNotBlank()) {
            prefs.edit()
                .remove("pos_$streamKey")
                .remove("dur_$streamKey")
                .apply()
        }
    }

    // Now Page Personalization Preference
    fun isNowPersonalizationEnabled(): Boolean {
        return prefs.getBoolean("now_personalization_enabled", true)
    }

    fun setNowPersonalizationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("now_personalization_enabled", enabled).apply()
    }

    // Favorite Live TV Channels
    fun getFavoriteChannelIds(): Set<Int> {
        val rawSet = prefs.getStringSet("favorite_channels", emptySet()) ?: emptySet()
        return rawSet.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun isFavoriteChannel(streamId: Int): Boolean {
        return getFavoriteChannelIds().contains(streamId)
    }

    fun toggleFavoriteChannel(streamId: Int): Boolean {
        val current = getFavoriteChannelIds().toMutableSet()
        val willFavorite = !current.contains(streamId)
        if (willFavorite) {
            current.add(streamId)
        } else {
            current.remove(streamId)
        }
        prefs.edit().putStringSet("favorite_channels", current.map { it.toString() }.toSet()).apply()
        return willFavorite
    }

    // Movie Watchlist (VOD)
    fun getMovieWatchlistIds(): Set<Int> {
        val rawSet = prefs.getStringSet("movie_watchlist", emptySet()) ?: emptySet()
        return rawSet.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun isMovieInWatchlist(streamId: Int): Boolean {
        return getMovieWatchlistIds().contains(streamId)
    }

    fun toggleMovieWatchlist(streamId: Int): Boolean {
        val current = getMovieWatchlistIds().toMutableSet()
        val willAdd = !current.contains(streamId)
        if (willAdd) {
            current.add(streamId)
        } else {
            current.remove(streamId)
        }
        prefs.edit().putStringSet("movie_watchlist", current.map { it.toString() }.toSet()).apply()
        return willAdd
    }

    // Series Watchlist (VOD)
    fun getSeriesWatchlistIds(): Set<Int> {
        val rawSet = prefs.getStringSet("series_watchlist", emptySet()) ?: emptySet()
        return rawSet.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun isSeriesInWatchlist(seriesId: Int): Boolean {
        return getSeriesWatchlistIds().contains(seriesId)
    }

    fun toggleSeriesWatchlist(seriesId: Int): Boolean {
        val current = getSeriesWatchlistIds().toMutableSet()
        val willAdd = !current.contains(seriesId)
        if (willAdd) {
            current.add(seriesId)
        } else {
            current.remove(seriesId)
        }
        prefs.edit().putStringSet("series_watchlist", current.map { it.toString() }.toSet()).apply()
        return willAdd
    }

    // Channel History (Last 5 watched channels)
    fun getChannelHistoryIds(): List<Int> {
        val raw = prefs.getString("live_channel_history", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Int>>() {}.type
            gson.fromJson<List<Int>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addChannelToHistory(streamId: Int) {
        if (streamId <= 0) return
        val current = getChannelHistoryIds().filter { it != streamId }.toMutableList()
        current.add(0, streamId)
        val limited = current.take(5)
        prefs.edit().putString("live_channel_history", gson.toJson(limited)).apply()
    }

    fun clearChannelHistory() {
        prefs.edit().remove("live_channel_history").apply()
    }

    // Movie Watch History (Last 30 watched movies)
    fun getMovieHistoryIds(): List<Int> {
        val raw = prefs.getString("movie_history", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Int>>() {}.type
            gson.fromJson<List<Int>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addMovieToHistory(streamId: Int) {
        if (streamId <= 0) return
        val current = getMovieHistoryIds().filter { it != streamId }.toMutableList()
        current.add(0, streamId)
        val limited = current.take(30)
        prefs.edit().putString("movie_history", gson.toJson(limited)).apply()
    }

    fun clearMovieHistory() {
        prefs.edit().remove("movie_history").apply()
    }

    // Series Watch History (Last 30 watched series)
    fun getSeriesHistoryIds(): List<Int> {
        val raw = prefs.getString("series_history", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Int>>() {}.type
            gson.fromJson<List<Int>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addSeriesToHistory(seriesId: Int) {
        if (seriesId <= 0) return
        val current = getSeriesHistoryIds().filter { it != seriesId }.toMutableList()
        current.add(0, seriesId)
        val limited = current.take(30)
        prefs.edit().putString("series_history", gson.toJson(limited)).apply()
    }

    fun clearSeriesHistory() {
        prefs.edit().remove("series_history").apply()
    }

    // Music Video Playback History (Last 40 played music videos)
    fun getMusicHistory(): List<MusicVideo> {
        val raw = prefs.getString("music_history", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<MusicVideo>>() {}.type
            gson.fromJson<List<MusicVideo>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addMusicToHistory(video: MusicVideo) {
        if (video.videoId.isBlank()) return
        val current = getMusicHistory().filter { it.videoId != video.videoId }.toMutableList()
        current.add(0, video)
        val limited = current.take(40)
        prefs.edit().putString("music_history", gson.toJson(limited)).apply()
    }

    fun clearMusicHistory() {
        prefs.edit().remove("music_history").apply()
    }

    // Podcast Episode Playback History (Last 40 played podcast episodes)
    fun getPodcastHistory(): List<PodcastEpisode> {
        val raw = prefs.getString("podcast_history", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<PodcastEpisode>>() {}.type
            gson.fromJson<List<PodcastEpisode>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addPodcastToHistory(episode: PodcastEpisode) {
        if (episode.videoId.isBlank()) return
        val current = getPodcastHistory().filter { it.videoId != episode.videoId }.toMutableList()
        current.add(0, episode)
        val limited = current.take(40)
        prefs.edit().putString("podcast_history", gson.toJson(limited)).apply()
    }

    fun clearPodcastHistory() {
        prefs.edit().remove("podcast_history").apply()
    }

    fun clearAllHistory() {
        clearChannelHistory()
        clearMovieHistory()
        clearSeriesHistory()
        clearMusicHistory()
        clearPodcastHistory()
    }

    // Live TV Focus & Selection Memory
    fun getLastLiveCategoryId(): String {
        return prefs.getString("last_live_category_id", null) ?: "672"
    }

    fun setLastLiveCategoryId(categoryId: String) {
        if (categoryId.isNotBlank()) {
            prefs.edit().putString("last_live_category_id", categoryId).apply()
        }
    }

    fun getLastLiveStreamId(): Int {
        return prefs.getInt("last_live_stream_id", 0)
    }

    fun setLastLiveStreamId(streamId: Int) {
        if (streamId > 0) {
            prefs.edit().putInt("last_live_stream_id", streamId).apply()
        }
    }

    // Movies Focus & Selection Memory
    fun getLastMovieCategoryId(): String? {
        return prefs.getString("last_movie_category_id", null)
    }

    fun setLastMovieCategoryId(categoryId: String) {
        if (categoryId.isNotBlank()) {
            prefs.edit().putString("last_movie_category_id", categoryId).apply()
        }
    }

    fun getLastMovieStreamId(): Int {
        return prefs.getInt("last_movie_stream_id", 0)
    }

    fun setLastMovieStreamId(streamId: Int) {
        if (streamId > 0) {
            prefs.edit().putInt("last_movie_stream_id", streamId).apply()
        }
    }

    // Series Focus & Selection Memory
    fun getLastSeriesCategoryId(): String? {
        return prefs.getString("last_series_category_id", null)
    }

    fun setLastSeriesCategoryId(categoryId: String) {
        if (categoryId.isNotBlank()) {
            prefs.edit().putString("last_series_category_id", categoryId).apply()
        }
    }

    fun getLastSeriesId(): Int {
        return prefs.getInt("last_series_id", 0)
    }

    fun setLastSeriesId(seriesId: Int) {
        if (seriesId > 0) {
            prefs.edit().putInt("last_series_id", seriesId).apply()
        }
    }

    // Movie & Series Subtitles (Closed Captions) Preference (Default: OFF)
    fun isVodSubtitlesEnabled(): Boolean {
        return prefs.getBoolean("vod_subtitles_enabled", false)
    }

    fun setVodSubtitlesEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("vod_subtitles_enabled", enabled).apply()
    }

    // Music & Podcasts Closed Captions Preference (Default: ON)
    fun isMusicPodcastsCaptionsEnabled(): Boolean {
        return prefs.getBoolean("music_podcasts_captions_enabled", true)
    }

    fun setMusicPodcastsCaptionsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("music_podcasts_captions_enabled", enabled).apply()
        _isMusicPodcastsCaptionsEnabled.value = enabled
    }

    // Adult Content (18+) Filter Toggle (Default: OFF)
    fun isAdultContentEnabled(): Boolean {
        return prefs.getBoolean("show_adult_content", false)
    }

    fun setAdultContentEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("show_adult_content", enabled).apply()
    }

    // US Channels & Categories Filter Toggle (Default: OFF)
    fun isUsOnly(): Boolean {
        return prefs.getBoolean("is_us_only", false)
    }

    fun setUsOnly(enabled: Boolean) {
        prefs.edit().putBoolean("is_us_only", enabled).putBoolean("is_us_english_only", enabled).apply()
    }

    // Legacy US-English Filter Toggle
    fun isUsEnglishOnly(): Boolean {
        return isUsOnly()
    }

    fun setUsEnglishOnly(enabled: Boolean) {
        setUsOnly(enabled)
    }
}
