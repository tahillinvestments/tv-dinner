package com.tvdinner.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tvdinner.data.model.CredentialEntry

class AuthRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tvdinner_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_ACTIVATED_PHONE = "activated_phone"
        private const val KEY_ADMIN_CREDENTIALS = "admin_credentials"
        private const val KEY_IPTV_PORTAL = "iptv_portal_url"
        private const val KEY_BACKUP_IPTV_PORTAL = "backup_iptv_portal_url"
        private const val KEY_VOD_PORTAL = "vod_portal_url"
        private const val KEY_ACTIVE_USERNAME = "active_xtream_username"
        private const val KEY_ACTIVE_PASSWORD = "active_xtream_password"
        const val DEFAULT_SERVER_URL = "http://vpn.uhdp.top:80"
        const val BACKUP_SERVER_URL = "http://vpn.uhd4.top:80"

        val DEFAULT_CREDENTIALS = emptyList<CredentialEntry>()
    }

    fun isActivated(): Boolean {
        return true
    }

    fun hasValidCredentials(): Boolean {
        return getActiveUsername().isNotBlank() && getActivePassword().isNotBlank()
    }

    fun getActivatedPhone(): String? {
        return prefs.getString(KEY_ACTIVATED_PHONE, null)
    }

    fun getAllCredentials(): List<CredentialEntry> {
        val json = prefs.getString(KEY_ADMIN_CREDENTIALS, null)
        if (json.isNullOrBlank()) {
            return emptyList()
        }
        return try {
            val type = object : TypeToken<List<CredentialEntry>>() {}.type
            gson.fromJson<List<CredentialEntry>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCredentials(list: List<CredentialEntry>) {
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_ADMIN_CREDENTIALS, json).apply()
    }

    fun normalizePhone(phone: String): String {
        return phone.replace(Regex("\\D"), "")
    }

    fun formatPhone(digits: String): String {
        val clean = normalizePhone(digits)
        return if (clean.length == 10) {
            "(${clean.substring(0, 3)}) ${clean.substring(3, 6)}-${clean.substring(6)}"
        } else {
            digits
        }
    }

    fun activatePhone(rawPhone: String): Boolean {
        val clean = normalizePhone(rawPhone)
        if (clean.length < 10) return false
        val formatted = formatPhone(clean)
        prefs.edit().putString(KEY_ACTIVATED_PHONE, formatted).apply()
        return true
    }

    fun signOut() {
        prefs.edit().remove(KEY_ACTIVATED_PHONE).apply()
    }

    fun getActiveUsername(): String {
        val direct = prefs.getString(KEY_ACTIVE_USERNAME, null)
        if (!direct.isNullOrBlank()) return direct.trim()
        val fromPhone = getActivatedPhone()?.let { phone ->
            getAllCredentials().firstOrNull { normalizePhone(it.phone) == normalizePhone(phone) }?.user
        }
        return fromPhone?.trim() ?: ""
    }

    fun getActivePassword(): String {
        val direct = prefs.getString(KEY_ACTIVE_PASSWORD, null)
        if (!direct.isNullOrBlank()) return direct.trim()
        val fromPhone = getActivatedPhone()?.let { phone ->
            getAllCredentials().firstOrNull { normalizePhone(it.phone) == normalizePhone(phone) }?.pswd
        }
        return fromPhone?.trim() ?: ""
    }

    fun getActiveLiveCredentials(): CredentialEntry {
        val u = getActiveUsername()
        val p = getActivePassword()
        val phone = getActivatedPhone() ?: "(317) 515-0204"
        return CredentialEntry(phone, u, p)
    }

    fun getLivePortalUrl(): String {
        val saved = prefs.getString(KEY_IPTV_PORTAL, null)
        return if (!saved.isNullOrBlank()) {
            saved.trim().removeSuffix("/")
        } else {
            DEFAULT_SERVER_URL
        }
    }

    fun setLivePortalUrl(url: String) {
        val clean = url.trim().removeSuffix("/")
        prefs.edit().putString(KEY_IPTV_PORTAL, clean).apply()
    }

    fun getBackupPortalUrl(): String {
        val saved = prefs.getString(KEY_BACKUP_IPTV_PORTAL, null)
        return if (!saved.isNullOrBlank()) {
            saved.trim().removeSuffix("/")
        } else {
            BACKUP_SERVER_URL
        }
    }

    fun setBackupPortalUrl(url: String) {
        val clean = url.trim().removeSuffix("/")
        prefs.edit().putString(KEY_BACKUP_IPTV_PORTAL, clean).apply()
    }

    fun getFailoverUrl(currentUrl: String): String {
        val primary = getLivePortalUrl()
        val backup = getBackupPortalUrl()
        return if (currentUrl.contains(primary)) {
            currentUrl.replace(primary, backup)
        } else if (currentUrl.contains(backup)) {
            currentUrl.replace(backup, primary)
        } else if (currentUrl.contains("vpn.uhdp.top:80")) {
            currentUrl.replace("vpn.uhdp.top:80", "vpn.uhd4.top:80")
        } else if (currentUrl.contains("vpn.uhd4.top:80")) {
            currentUrl.replace("vpn.uhd4.top:80", "vpn.uhdp.top:80")
        } else {
            currentUrl
        }
    }

    fun getVodPortalUrl(): String {
        val saved = prefs.getString(KEY_VOD_PORTAL, null)
        return if (!saved.isNullOrBlank()) {
            saved.trim().removeSuffix("/")
        } else {
            DEFAULT_SERVER_URL
        }
    }

    fun setVodPortalUrl(url: String) {
        val clean = url.trim().removeSuffix("/")
        prefs.edit().putString(KEY_VOD_PORTAL, clean).apply()
    }

    fun getVodUsername(): String = getActiveUsername()
    fun getVodPassword(): String = getActivePassword()

    fun setDirectCredentials(user: String, pswd: String) {
        val cleanU = user.trim()
        val cleanP = pswd.trim()
        prefs.edit()
            .putString(KEY_ACTIVE_USERNAME, cleanU)
            .putString(KEY_ACTIVE_PASSWORD, cleanP)
            .apply()
        val phone = getActivatedPhone() ?: "(317) 515-0204"
        if (cleanU.isNotBlank() && cleanP.isNotBlank()) {
            addOrUpdateCredential(phone, cleanU, cleanP)
        }
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_ACTIVE_USERNAME)
            .remove(KEY_ACTIVE_PASSWORD)
            .apply()
    }

    fun addOrUpdateCredential(phone: String, user: String, pswd: String) {
        val list = getAllCredentials().toMutableList()
        val clean = normalizePhone(phone)
        val formatted = formatPhone(clean)
        val idx = list.indexOfFirst { normalizePhone(it.phone) == clean }
        if (idx >= 0) {
            list[idx] = CredentialEntry(formatted, user, pswd)
        } else {
            list.add(0, CredentialEntry(formatted, user, pswd))
        }
        saveCredentials(list)
    }

    fun deleteCredential(phone: String) {
        val clean = normalizePhone(phone)
        val list = getAllCredentials().filter { normalizePhone(it.phone) != clean }
        saveCredentials(list)
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
