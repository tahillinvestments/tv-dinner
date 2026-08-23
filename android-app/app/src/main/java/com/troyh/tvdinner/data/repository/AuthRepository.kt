package com.troyh.tvdinner.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.troyh.tvdinner.data.model.CredentialEntry

class AuthRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tvdinner_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_ACTIVATED_PHONE = "activated_phone"
        private const val KEY_ADMIN_CREDENTIALS = "admin_credentials"
        private const val KEY_IPTV_PORTAL = "iptv_portal_url"
        private const val KEY_VOD_PORTAL = "vod_portal_url"

        val DEFAULT_CREDENTIALS = listOf(
            CredentialEntry("(317) 515-0204", "DGOLD001", "Louisville"),
            CredentialEntry("(215) 917-3255", "SGmUC7q2U", "4WM9WVsjG"),
            CredentialEntry("(123) 456-7891", "SGmUC7q2U", "4WM9WVsjG"),
            CredentialEntry("317-363-1751", "MW2Y2h6e7", "5DwU7wTuA"),
            CredentialEntry("317-900-3473", "Hn9a6bus9", "JaKXrfMP7"),
            CredentialEntry("317-902-1240", "TONE2", "TV4LIFE"),
            CredentialEntry("317-795-7627", "SAPPTV13", "REMOTE6202"),
            CredentialEntry("317-261-1596", "DAMETV", "2611596317")
        )
    }

    fun isActivated(): Boolean {
        val phone = prefs.getString(KEY_ACTIVATED_PHONE, null)
        if (phone != null && normalizePhone(phone) == "1234567898") {
            signOut()
            return false
        }
        return !phone.isNullOrBlank()
    }

    fun getActivatedPhone(): String? {
        val phone = prefs.getString(KEY_ACTIVATED_PHONE, null)
        if (phone != null && normalizePhone(phone) == "1234567898") {
            signOut()
            return null
        }
        return phone
    }

    fun getAllCredentials(): List<CredentialEntry> {
        val json = prefs.getString(KEY_ADMIN_CREDENTIALS, null)
        if (json.isNullOrBlank()) {
            return DEFAULT_CREDENTIALS
        }
        return try {
            val type = object : TypeToken<List<CredentialEntry>>() {}.type
            val list: List<CredentialEntry> = gson.fromJson(json, type) ?: DEFAULT_CREDENTIALS
            // Filter out any removed credentials such as 1234567898
            val sanitized = list.filter { normalizePhone(it.phone) != "1234567898" }
            val existingClean = sanitized.map { normalizePhone(it.phone) }.toSet()
            val missingDefaults = DEFAULT_CREDENTIALS.filter { !existingClean.contains(normalizePhone(it.phone)) }
            val combined = if (missingDefaults.isNotEmpty()) sanitized + missingDefaults else sanitized
            if (combined.size != list.size) {
                saveCredentials(combined)
            }
            combined
        } catch (e: Exception) {
            DEFAULT_CREDENTIALS
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

        val list = getAllCredentials()
        val match = list.firstOrNull { normalizePhone(it.phone) == clean }
        return if (match != null) {
            val formatted = formatPhone(clean)
            prefs.edit().putString(KEY_ACTIVATED_PHONE, formatted).apply()
            true
        } else {
            false
        }
    }

    fun signOut() {
        prefs.edit().remove(KEY_ACTIVATED_PHONE).apply()
    }

    fun getActiveLiveCredentials(): CredentialEntry? {
        val activePhone = getActivatedPhone() ?: return null
        val clean = normalizePhone(activePhone)
        return getAllCredentials().firstOrNull { normalizePhone(it.phone) == clean }
    }

    fun getLivePortalUrl(): String {
        val saved = prefs.getString(KEY_IPTV_PORTAL, null)
        return if (!saved.isNullOrBlank()) {
            saved.trim().removeSuffix("/")
        } else {
            "http://portal5458.com:8080"
        }
    }

    fun getVodPortalUrl(): String {
        val saved = prefs.getString(KEY_VOD_PORTAL, null)
        return if (!saved.isNullOrBlank()) {
            saved.trim().removeSuffix("/")
        } else {
            "http://asoseller.org:8080"
        }
    }

    fun getVodUsername(): String = "gj3526@gmail.com"
    fun getVodPassword(): String = "ck9sd6Nc4TZA"

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
}
