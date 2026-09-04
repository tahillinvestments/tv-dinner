package com.tvdinner

import com.tvdinner.data.model.Channel
import com.tvdinner.data.network.XtreamApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTvAndRemoteTest {

    @Test
    fun testLiveTvStreamUrl_strictlyUsesMainPortal() {
        val client = XtreamApiClient()
        val mainPortal = "http://vpn.uhdp.top:80"
        val user = "954ee56a56"
        val pswd = "2b0dd524f955"

        val streamUrl = client.buildLiveStreamUrl(mainPortal, user, pswd, 1001, "m3u8")
        assertEquals("http://vpn.uhdp.top:80/live/954ee56a56/2b0dd524f955/1001.m3u8", streamUrl)
        assertTrue("URL must reference main portal", streamUrl.contains("vpn.uhdp.top"))
        assertFalse("Live TV must never use asoseller", streamUrl.contains("asoseller"))
    }

    @Test
    fun testMainActivity_fullscreenFlags() {
        MainActivity.isVODFullscreenActive = false
        MainActivity.isLiveFullscreenActive = false

        assertFalse(MainActivity.isVODFullscreenActive)
        assertFalse(MainActivity.isLiveFullscreenActive)

        MainActivity.isLiveFullscreenActive = true
        assertTrue(MainActivity.isLiveFullscreenActive)

        MainActivity.isLiveFullscreenActive = false
        MainActivity.isVODFullscreenActive = true
        assertTrue(MainActivity.isVODFullscreenActive)
    }

    @Test
    fun testChannelModel_preservesMainPortalAssignment() {
        val ch = Channel(
            num = 1,
            name = "ESPN HD",
            streamId = 5555,
            streamIcon = null,
            categoryId = "Sports",
            directStreamUrl = null,
            portalUrl = "http://vpn.uhdp.top:80",
            streamUser = "954ee56a56",
            streamPassword = "2b0dd524f955"
        )

        assertEquals("http://vpn.uhdp.top:80", ch.portalUrl)
        assertEquals("954ee56a56", ch.streamUser)
        assertEquals("2b0dd524f955", ch.streamPassword)
        assertFalse(ch.portalUrl?.contains("asoseller") == true)
    }

    @Test
    fun testEpgResolutionAndDecoding() {
        val rawBase64 = java.util.Base64.getEncoder().encodeToString("The Late Night Show".toByteArray(Charsets.UTF_8))
        val prog1 = com.tvdinner.data.model.EpgProgram(
            id = "1",
            title = rawBase64,
            nowPlaying = 1,
            startTimestamp = "1700000000",
            stopTimestamp = "1700003600"
        )
        assertEquals("The Late Night Show", prog1.decodedTitle)

        val progRaw = com.tvdinner.data.model.EpgProgram(
            id = "2",
            title = "Morning News Live",
            nowPlaying = 0
        )
        assertEquals("Morning News Live", progRaw.decodedTitle)
    }

    @Test
    fun testUsEnglishChannelFiltering() {
        fun isUsEnglish(name: String, cat: String): Boolean {
            val n = name.uppercase()
            val c = cat.uppercase()
            if (n.contains("DEPORTES") || n.contains("DEPORTE") || n.contains("TUDN") || c.contains("DEPORTES")) return false
            if (n.contains("PPV") || n.contains("PAY PER VIEW") || n.contains("MAIN EVENT") || n.contains("EVENTS") || n.contains("UFC") || n.contains("BOXING") || n.contains("WWE")) return true
            return n.contains("US |") || n.contains("USA") || n.contains("US:") ||
                    n.contains("US -") || n.contains("US ") || n.contains("[US]") ||
                    n.contains("(US)") || n.contains("EN |") || n.contains("ENGLISH") ||
                    c.contains("US") || c.contains("ENGLISH") || c.contains("USA")
        }

        assertTrue(isUsEnglish("US | ESPN HD", "Live"))
        assertTrue(isUsEnglish("USA: HBO", "Movies"))
        assertTrue(isUsEnglish("[US] CBS News", "News"))
        assertTrue(isUsEnglish("Sky Sports", "US Premium"))
        assertTrue(isUsEnglish("EN | BBC ONE", "Entertainment"))
        assertFalse(isUsEnglish("FR | CANAL+", "France Channels"))
        assertFalse(isUsEnglish("ES | TELECINCO", "Spain Channels"))

        // PPV and Main Event must remain true
        assertTrue(isUsEnglish("PPV: UFC 300 MAIN CARD", "Events"))
        assertTrue(isUsEnglish("MAIN EVENT: BOXING CHAMPIONSHIP", "Sports"))

        // Deportes must be eliminated
        assertFalse(isUsEnglish("ESPN DEPORTES HD", "Sports"))
        assertFalse(isUsEnglish("FOX DEPORTES", "US Sports"))
        assertFalse(isUsEnglish("TUDN USA", "Deportes"))
        assertTrue(isUsEnglish("US | ESPN HD", "Sports"))
        assertTrue(isUsEnglish("PPV 01: MAIN EVENT", "PPV"))
    }

    @Test
    fun testUsEnglishCategoryFiltering() {
        // Test Live categories
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("US| ENTERTAINMENT"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("US| SPORTS NETWORK"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("4K RELAX UHD 3840P"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("24/7 MOVIES & SERIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("WORLD LIVE SPORTS"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("UK| SKY SPORTS"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("IT| SKY CINEMA"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("ES| LALIGA"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("DE| SKY SPORT"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("FR| CANAL+"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("SWEDEN SPORT HD"))

        // Test Latina categories inclusion in Live TV
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("LAT| MEXICO"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("LAT| PUERTO RICO"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("LAT| R. DOMINICANA"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("LAT| COLOMBIA"))

        // Test Movie categories (No 'All Movies' category)
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsMovieCategory("ALL"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsMovieCategory("ALL MOVIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsMovieCategory("|EN| 4K MOVIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsMovieCategory("NETFLIX MOVIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsMovieCategory("DISNEY+ MOVIES"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsMovieCategory("|FR| NOUVEAUTES"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsMovieCategory("|NL| FILMS"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsMovieCategory("|DE| FILME"))

        // Test Series categories (No 'All Series' category)
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsSeriesCategory("ALL"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsSeriesCategory("ALL SERIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsSeriesCategory("|EN| 4K SERIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsSeriesCategory("|MULTI| NETFLIX SERIES"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsSeriesCategory("|FR| SERIES"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsSeriesCategory("|NL| VIDEOLAND"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsSeriesCategory("|DE| SERIEN"))

        // Test Adult/18+ category inclusion
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsLiveCategory("18| FOR ADULTS"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isEnglishOrUsMovieCategory("FOR ADULTS"))

        // Test Priority Ordering (New Releases & Top Genres First, Adult Last)
        val pNewMovies = com.tvdinner.data.repository.CatalogManager.getMovieCategoryPriority("|EN| NEW RELEASED")
        val p4kMovies = com.tvdinner.data.repository.CatalogManager.getMovieCategoryPriority("|EN| 4K MOVIES")
        val pActionMovies = com.tvdinner.data.repository.CatalogManager.getMovieCategoryPriority("|EN| ACTION/THRILLER")
        val pAdultMovies = com.tvdinner.data.repository.CatalogManager.getMovieCategoryPriority("FOR ADULTS")

        assertTrue(pNewMovies < p4kMovies)
        assertTrue(p4kMovies < pActionMovies)
        assertTrue(pActionMovies < pAdultMovies)
        assertEquals(9999, pAdultMovies)

        val pLatestSeries = com.tvdinner.data.repository.CatalogManager.getSeriesCategoryPriority("|EN| LATEST RELEASES")
        val p4kSeries = com.tvdinner.data.repository.CatalogManager.getSeriesCategoryPriority("|EN| 4K SERIES")
        val pAdultSeries = com.tvdinner.data.repository.CatalogManager.getSeriesCategoryPriority("FOR ADULTS")

        assertTrue(pLatestSeries < p4kSeries)
        assertTrue(p4kSeries < pAdultSeries)
        assertEquals(9999, pAdultSeries)

        // Test isAdultCategory and isAdultName detection (including |+18| HANIME)
        assertTrue(com.tvdinner.data.repository.CatalogManager.isAdultCategory("18| FOR ADULTS"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isAdultCategory("FOR ADULTS"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isAdultCategory("XXX MOVIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isAdultCategory("|EN| 18+ MOVIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isAdultCategory("PORN CATEGORY"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isAdultCategory("|18+| HANIME"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isAdultCategory("|+18| HANIME"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isAdultCategory("+18 HANIME"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isAdultName("18| Hustler TV"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isAdultName("XXX Channel HD"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isAdultCategory("US| ENTERTAINMENT"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isAdultCategory("|EN| NEW RELEASED"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isAdultCategory("LAT| MEXICO"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isAdultName("ESPN HD"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isAdultName("HBO Movies"))

        // Test US Category Sorting Priority: |US| categories in front, then |PPV|
        val pUsEnt = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("|US| ENTERTAINMENT")
        val pPpv = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("|PPV| EVENTS")
        val pNowCinema = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("|NOW| CINEMA")
        val p4k = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("4K ULTRA HD")
        val pUk = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("|UK| SPORTS")
        val pAdult = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("|18+| HANIME")

        assertTrue("|US| must be in front of |PPV|", pUsEnt < pPpv)
        assertTrue("|PPV| must be in front of |NOW|", pPpv < pNowCinema)
        assertTrue("|NOW| must be in front of generic", pNowCinema < p4k)
        assertTrue("Generic/4K must be in front of |UK|", p4k < pUk)
        assertTrue("|UK| must be in front of Adult", pUk < pAdult)
        assertEquals(9999, pAdult)

        // Test US filter allowlist logic (US Toggle: only |US|, |PPV|, and |+18|)
        assertTrue(com.tvdinner.data.repository.CatalogManager.isUsOrAllowedCategory("|US| NEWS"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isUsOrAllowedCategory("US| ENTERTAINMENT"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isUsOrAllowedCategory("|PPV| MAIN EVENTS"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isUsOrAllowedCategory("PPV EVENTS"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isUsOrAllowedCategory("|+18| HANIME"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isUsOrAllowedCategory("|18+| HANIME"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isUsOrAllowedCategory("|NOW| CINEMA"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isUsOrAllowedCategory("24/7 MOVIES"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isUsOrAllowedCategory("4K CHANNELS"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isUsOrAllowedCategory("|UK| SKY SPORTS"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isUsOrAllowedCategory("|CA| TSN"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isUsOrAllowedCategory("|FR| CANAL+"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isUsOrAllowedCategory("|LAT| MEXICO"))

        // Test Movie categories specific exclusions when US filter is on
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredMovieCategory("|AF| MOVIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredMovieCategory("|CA| CINEMA"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredMovieCategory("|NORDIC| FILMS"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredMovieCategory("|SC| CINEMA"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredMovieCategory("|DANSKE| BIO"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredMovieCategory("ORIGINAL TABII MULTI"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredMovieCategory("|VE| MOVIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredMovieCategory("|JP| ANIME"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredMovieCategory("|KU| FILMS"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredMovieCategory("|AM| MOVIES"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isFilteredMovieCategory("|EN| 4K MOVIES"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isFilteredMovieCategory("NETFLIX MOVIES"))

        // Test Series categories specific exclusions when US filter is on
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredSeriesCategory("|AM| SERIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredSeriesCategory("|EX| SERIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredSeriesCategory("|SC| DRAMA"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredSeriesCategory("|CA| SHOWS"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredSeriesCategory("|AF| SERIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredSeriesCategory("|NO| NORDIC"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isFilteredSeriesCategory("|NORDEC| SERIES"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isFilteredSeriesCategory("|EN| 4K SERIES"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isFilteredSeriesCategory("AMAZON SERIES"))

        // Test VOD non-English filtering
        assertTrue(com.tvdinner.data.repository.CatalogManager.isNonEnglishVodCategory("|FR| COMEDIE"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isNonEnglishVodCategory("|ES| PELICULAS"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isNonEnglishVodCategory("|LAT| SERIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isNonEnglishVodCategory("HINDI MOVIES"))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isNonEnglishVodCategory("|DE| FILME"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isNonEnglishVodCategory("|EN| 4K MOVIES"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isNonEnglishVodCategory("|US| ACTION"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isNonEnglishVodCategory("NETFLIX MOVIES"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isNonEnglishVodCategory("DISNEY+ MOVIES"))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isNonEnglishVodCategory("TOP IMDB"))
    }

    @Test
    fun testMusicDataCuratedGenresAndArtists() {
        val genres = com.tvdinner.data.music.MusicData.GENRES
        assertTrue(genres.isNotEmpty())
        assertTrue(genres.any { it.name.contains("Hip-Hop", ignoreCase = true) })
        assertTrue(genres.any { it.name.contains("R&B", ignoreCase = true) })
        assertTrue(genres.any { it.name.contains("Pop", ignoreCase = true) })

        val artists = com.tvdinner.data.music.MusicData.ARTISTS
        assertTrue(artists.isNotEmpty())
        assertTrue(artists.any { it.artistName.contains("Drake", ignoreCase = true) })
        assertTrue(artists.any { it.artistName.contains("Kendrick Lamar", ignoreCase = true) })
        assertTrue(artists.any { it.artistName.contains("SZA", ignoreCase = true) })
    }

    @Test
    fun testCleanChannelDisplayNameAndQuality() {
        assertEquals("ESPN", com.tvdinner.data.repository.CatalogManager.cleanChannelDisplayName("US| ESPN HD"))
        assertEquals("HD", com.tvdinner.data.repository.CatalogManager.extractChannelQuality("US| ESPN HD"))

        assertEquals("HBO EAST", com.tvdinner.data.repository.CatalogManager.cleanChannelDisplayName("US: HBO EAST FHD"))
        assertEquals("FHD", com.tvdinner.data.repository.CatalogManager.extractChannelQuality("US: HBO EAST FHD"))

        assertEquals("DISCOVERY", com.tvdinner.data.repository.CatalogManager.cleanChannelDisplayName("|US| DISCOVERY 4K"))
        assertEquals("4K", com.tvdinner.data.repository.CatalogManager.extractChannelQuality("|US| DISCOVERY 4K"))

        assertEquals("CNN", com.tvdinner.data.repository.CatalogManager.cleanChannelDisplayName("[US] CNN"))
        assertEquals("FOX NEWS", com.tvdinner.data.repository.CatalogManager.cleanChannelDisplayName("(US) FOX NEWS 60FPS"))
        assertEquals("60FPS", com.tvdinner.data.repository.CatalogManager.extractChannelQuality("(US) FOX NEWS 60FPS"))

        assertEquals("CINEMAX", com.tvdinner.data.repository.CatalogManager.cleanChannelDisplayName("VIP US: CINEMAX HEVC"))
        assertEquals("HEVC", com.tvdinner.data.repository.CatalogManager.extractChannelQuality("VIP US: CINEMAX HEVC"))
    }

    @Test
    fun testRequestedLiveCategoryPrioritySequence() {
        val pEntertainment = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("US | ENTERTAINMENT")
        val pNews = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("NEWS NETWORK")
        val pSports = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("SPORTS NETWORK")
        val pMovie = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("MOVIE NETWORK")
        val pMovies = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("MOVIES NETWORK")
        val pKids = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("KIDS NETWORK")
        val pPrime = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("PRIME")
        val pPpv = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("NA| PPV & LIVE EVENTS")
        val pRelax = com.tvdinner.data.repository.CatalogManager.getLiveCategoryPriority("4K RELAX UHD 3840P")

        assertEquals(1, pEntertainment)
        assertEquals(2, pNews)
        assertEquals(3, pSports)
        assertEquals(4, pMovie)
        assertEquals(4, pMovies)
        assertEquals(5, pKids)
        assertEquals(6, pPrime)
        assertEquals(7, pPpv)
        assertEquals(8, pRelax)

        assertTrue(pEntertainment < pNews)
        assertTrue(pNews < pSports)
        assertTrue(pSports < pMovie)
        assertTrue(pMovie < pKids)
        assertTrue(pKids < pPrime)
        assertTrue(pPrime < pPpv)
        assertTrue(pPpv < pRelax)
    }

    @Test
    fun testAdultSearchFilteringRules() {
        // Adult categories & names
        val adultCat = "18| FOR ADULTS"
        val adultChannelName = "18| Hustler TV"
        val normalCat = "US| SPORTS NETWORK"
        val normalChannelName = "ESPN HD"
        val adultMovieName = "XXX Wild Nights 2024"
        val normalMovieName = "The Dark Knight"

        assertTrue(com.tvdinner.data.repository.CatalogManager.isAdultCategory(adultCat))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isAdultCategory(normalCat))

        assertTrue(com.tvdinner.data.repository.CatalogManager.isAdultName(adultChannelName))
        assertTrue(com.tvdinner.data.repository.CatalogManager.isAdultName(adultMovieName))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isAdultName(normalChannelName))
        assertFalse(com.tvdinner.data.repository.CatalogManager.isAdultName(normalMovieName))

        // Simulate search pool with adult and non-adult channels
        val allChannels = listOf(
            Channel(num = 1, name = normalChannelName, streamId = 101, categoryId = "sports"),
            Channel(num = 2, name = adultChannelName, streamId = 102, categoryId = "adult_18"),
            Channel(num = 3, name = "Adult Swim Live", streamId = 103, categoryId = "animation") // contains ADULT in name
        )
        val adultCategoryIds = setOf("adult_18")

        // 1. Search when non-adult category is selected:
        val isAdultSelected = false
        val nonAdultSearchResults = allChannels.filter { ch ->
            if (isAdultSelected) {
                (adultCategoryIds.contains(ch.categoryId) || com.tvdinner.data.repository.CatalogManager.isAdultCategory(ch.name) || com.tvdinner.data.repository.CatalogManager.isAdultName(ch.name))
            } else {
                !adultCategoryIds.contains(ch.categoryId) &&
                !com.tvdinner.data.repository.CatalogManager.isAdultCategory(ch.name) &&
                !com.tvdinner.data.repository.CatalogManager.isAdultName(ch.name)
            }
        }
        assertEquals(1, nonAdultSearchResults.size)
        assertEquals("ESPN HD", nonAdultSearchResults[0].name)

        // 2. Search when adult category IS selected:
        val adultSelectedSearchResults = allChannels.filter { ch ->
            adultCategoryIds.contains(ch.categoryId) || com.tvdinner.data.repository.CatalogManager.isAdultCategory(ch.name) || com.tvdinner.data.repository.CatalogManager.isAdultName(ch.name)
        }
        assertEquals(2, adultSelectedSearchResults.size)
        assertTrue(adultSelectedSearchResults.any { it.name == adultChannelName })
    }

    @Test
    fun testLiveRewindConstants() {
        assertEquals(120_000L, com.tvdinner.player.ExoPlayerManager.MAX_LIVE_REWIND_MS)
        assertEquals(10_000L, com.tvdinner.player.ExoPlayerManager.LIVE_SEEK_STEP_MS)
        assertEquals(2_500L, com.tvdinner.player.ExoPlayerManager.LIVE_EDGE_TOLERANCE_MS)
    }

    @Test
    fun testEnglishCaptionsMatching() {
        fun isEnglishTextTrack(lang: String?, label: String?): Boolean {
            val l = lang?.lowercase()
            val lbl = label?.lowercase() ?: ""
            return l == "en" || l == "eng" || l == "en-us" || l == "en-gb" ||
                    lbl.contains("english") || lbl.contains(" en ") || lbl.startsWith("en")
        }

        assertTrue(isEnglishTextTrack("en", "English"))
        assertTrue(isEnglishTextTrack("eng", "English [CC]"))
        assertTrue(isEnglishTextTrack("en-US", "Closed Captions"))
        assertTrue(isEnglishTextTrack(null, "English Subtitles"))
        assertFalse(isEnglishTextTrack("es", "Spanish"))
        assertFalse(isEnglishTextTrack("fr", "French"))
        assertFalse(isEnglishTextTrack("de", "Deutsch"))
    }
}

