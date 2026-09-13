package com.tvdinner

import com.tvdinner.data.model.CredentialEntry
import com.tvdinner.data.network.XtreamApiClient
import com.tvdinner.data.podcasts.PodcastsData
import com.tvdinner.data.repository.AuthRepository
import org.junit.Assert.*
import org.junit.Test

class AuthRepositoryTest {

    @Test
    fun testAuthorizedPortals() {
        assertEquals("http://vpn.uhdp.top:80", AuthRepository.DEFAULT_SERVER_URL)
        assertEquals(4, AuthRepository.SERVER_PORTALS.size)
        assertEquals("http://vpn.uhdp.top:80", AuthRepository.SERVER_PORTALS[0])
        assertEquals("http://tv.wd.uhdp.top:80", AuthRepository.SERVER_PORTALS[1])
        assertEquals("http://vpn.uhd4.top:80", AuthRepository.SERVER_PORTALS[2])
        assertEquals("http://tv.wd.uhd4.top:80", AuthRepository.SERVER_PORTALS[3])
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

    @Test
    fun testAuthResultModel() {
        val activeResult = com.tvdinner.data.network.AuthResult(
            isValid = true,
            status = "Active",
            message = "Account Status: Active (Expires: Jan 15, 2027 | Max Connections: 1)"
        )
        assertTrue(activeResult.isValid)
        assertEquals("Active", activeResult.status)
        assertTrue(activeResult.message.contains("Active"))

        val inactiveResult = com.tvdinner.data.network.AuthResult(
            isValid = false,
            status = "Inactive",
            message = "Account Status: Inactive / Invalid Credentials (HTTP 513)"
        )
        assertFalse(inactiveResult.isValid)
        assertEquals("Inactive", inactiveResult.status)
        assertTrue(inactiveResult.message.contains("513"))
    }

    @Test
    fun testLiveAuthentication() = kotlinx.coroutines.runBlocking {
        val client = XtreamApiClient()
        val res = client.testCredentials("http://vpn.uhdp.top:80", "d73c8ca2ba", "19072e3c75ce")
        println("testLiveAuthentication result: isValid=${res.isValid}, status=${res.status}, message=${res.message}")
        assertTrue("Live authentication must be valid", res.isValid)
    }
}
