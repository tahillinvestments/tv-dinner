package com.troyh.tvdinner

import com.troyh.tvdinner.data.model.CredentialEntry
import com.troyh.tvdinner.data.network.XtreamApiClient
import com.troyh.tvdinner.data.podcasts.PodcastsData
import com.troyh.tvdinner.data.repository.AuthRepository
import org.junit.Assert.*
import org.junit.Test

class AuthRepositoryTest {

    @Test
    fun testDefaultCredentials_hasAllRequiredAccounts() {
        val creds = AuthRepository.DEFAULT_CREDENTIALS
        assertTrue(creds.isNotEmpty())

        val phones = creds.map { it.phone.replace(Regex("\\D"), "") }
        assertTrue("Must have (317) 515-0204", phones.contains("3175150204"))
        assertTrue("Must have 123-456-7891", phones.contains("1234567891"))
        assertTrue("Must have 215-917-3255", phones.contains("2159173255"))
        assertTrue("Must have 317-363-1751", phones.contains("3173631751"))
        assertTrue("Must have 317-900-3473", phones.contains("3179003473"))
        assertTrue("Must have 317-902-1240", phones.contains("3179021240"))
        assertTrue("Must have 317-795-7627", phones.contains("3177957627"))
        assertTrue("Must have 317-261-1596", phones.contains("3172611596"))

        // Check usernames
        val users = creds.map { it.user }
        assertTrue(users.contains("DGOLD001"))
        assertTrue(users.contains("SGmUC7q2U"))
        assertTrue(users.contains("TONE2"))
        assertTrue(users.contains("SAPPTV13"))
        assertTrue(users.contains("DAMETV"))
    }

    @Test
    fun testXtreamUrlBuilders() {
        val client = XtreamApiClient()
        val portal = "http://portal5458.com:8080"
        val user = "DGOLD001"
        val pswd = "Louisville"

        val rawLiveUrl = client.buildRawLiveStreamUrl(portal, user, pswd, 12345, "ts")
        assertEquals("http://portal5458.com:8080/live/DGOLD001/Louisville/12345.ts", rawLiveUrl)

        val proxiedLiveUrl = client.buildLiveStreamUrl(portal, user, pswd, 12345, "ts")
        assertTrue(proxiedLiveUrl.startsWith("https://tv-dinner-proxy.onrender.com/?url="))
        assertTrue(proxiedLiveUrl.contains("12345.ts"))

        val movieUrl = client.buildMovieStreamUrl("http://asoseller.org:8080", "gj3526@gmail.com", "ck9sd6Nc4TZA", 999, "mp4")
        assertTrue(movieUrl.startsWith("https://tv-dinner-proxy.onrender.com/?url="))
        assertTrue(movieUrl.contains("999.mp4"))

        val seriesUrl = client.buildSeriesStreamUrl("http://asoseller.org:8080", "gj3526@gmail.com", "ck9sd6Nc4TZA", 888, ".mkv")
        assertTrue(seriesUrl.startsWith("https://tv-dinner-proxy.onrender.com/?url="))
        assertTrue(seriesUrl.contains("888.mkv"))
    }

    @Test
    fun testCuratedPodcastsDataset() {
        val channels = PodcastsData.CHANNELS
        assertTrue(channels.isNotEmpty())
        for (ch in channels) {
            assertTrue("Channel ID must be non-blank", ch.id.isNotBlank())
            assertTrue("Channel name must be non-blank", ch.channelName.isNotBlank())
            assertTrue("YouTube channel ID must be non-blank", ch.ytChannelId.isNotBlank())
            assertTrue("Avatar must be non-blank", ch.avatar.isNotBlank())
        }
    }
}
