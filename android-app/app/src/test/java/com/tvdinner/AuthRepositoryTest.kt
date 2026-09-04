package com.tvdinner

import com.tvdinner.data.model.CredentialEntry
import com.tvdinner.data.network.XtreamApiClient
import com.tvdinner.data.podcasts.PodcastsData
import com.tvdinner.data.repository.AuthRepository
import org.junit.Assert.*
import org.junit.Test

class AuthRepositoryTest {

    @Test
    fun testDefaultCredentials_isDecoupledAndEmpty() {
        val creds = AuthRepository.DEFAULT_CREDENTIALS
        assertTrue(creds.isEmpty())
        assertEquals("http://vpn.uhdp.top:80", AuthRepository.DEFAULT_SERVER_URL)
    }

    @Test
    fun testXtreamUrlBuilders() {
        val client = XtreamApiClient()
        val portal = "http://vpn.uhdp.top:80"
        val user = "954ee56a56"
        val pswd = "2b0dd524f955"

        // Live stream URLs use direct local playback for ExoPlayer on residential WiFi
        val liveUrl = client.buildLiveStreamUrl(portal, user, pswd, 12345, "m3u8")
        assertEquals("http://vpn.uhdp.top:80/live/954ee56a56/2b0dd524f955/12345.m3u8", liveUrl)

        // Movie and series URLs use direct local playback
        val movieUrl = client.buildMovieStreamUrl("http://vpn.uhdp.top:80", "954ee56a56", "2b0dd524f955", 999, "mp4")
        assertEquals("http://vpn.uhdp.top:80/movie/954ee56a56/2b0dd524f955/999.mp4", movieUrl)

        val seriesUrl = client.buildSeriesStreamUrl("http://vpn.uhdp.top:80", "954ee56a56", "2b0dd524f955", 888, ".mkv")
        assertEquals("http://vpn.uhdp.top:80/series/954ee56a56/2b0dd524f955/888.mkv", seriesUrl)
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
