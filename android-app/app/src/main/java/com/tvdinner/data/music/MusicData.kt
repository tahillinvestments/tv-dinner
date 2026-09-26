package com.tvdinner.data.music

import com.tvdinner.data.model.MusicArtist
import com.tvdinner.data.model.MusicGenre

object MusicData {
    val GENRES = listOf(
        MusicGenre("trending", "🔥 Trending Hits", "🔥"),
        MusicGenre("hiphop", "🎤 Hip-Hop & Rap", "🎤"),
        MusicGenre("rnb", "🎷 R&B & Soul", "🎷"),
        MusicGenre("pop", "✨ Pop & Top 40", "✨"),
        MusicGenre("rock", "🎸 Rock & Alternative", "🎸"),
        MusicGenre("country", "🤠 Country & Americana", "🤠"),
        MusicGenre("afrobeats", "🌍 Afrobeats & Global", "🌍"),
        MusicGenre("latin", "💃 Latin & Reggaeton", "💃"),
        MusicGenre("electronic", "⚡ Electronic & Dance", "⚡"),
        MusicGenre("jazz", "🎹 Jazz & Acoustic", "🎹")
    )

    val ARTISTS = listOf(
        // ─── Trending / Top 2026 Hits ───
        MusicArtist(
            id = "sabrina_carpenter",
            artistName = "Sabrina Carpenter",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/k6sw4fE6sng/hqdefault.jpg",
            subscribers = "9.8M subscribers",
            bio = "Global pop phenomenon behind Espresso, Please Please Please, and Short n' Sweet.",
            ytChannelId = "UCu_Xw92bBqj6H1YJzE8_0tw"
        ),
        MusicArtist(
            id = "chappell_roan",
            artistName = "Chappell Roan",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/yG75P9HhM7g/hqdefault.jpg",
            subscribers = "3.4M subscribers",
            bio = "Breakout pop sensation behind Good Luck Babe! and The Rise and Fall of a Midwest Princess.",
            ytChannelId = "UCm_yqE8F7_UjYwZk8dE7wug"
        ),
        MusicArtist(
            id = "post_malone",
            artistName = "Post Malone",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/eJ3i9nQ9W28/hqdefault.jpg",
            subscribers = "25.4M subscribers",
            bio = "Multi-diamond certified hitmaker blending hip-hop, pop, and modern country.",
            ytChannelId = "UCtLHmQp_D6s6s2x8Fj3s56g"
        ),
        MusicArtist(
            id = "charli_xcx",
            artistName = "Charli xcx",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/W1_jYwN_i-w/hqdefault.jpg",
            subscribers = "3.8M subscribers",
            bio = "Hyperpop pioneer and Brat cultural icon setting global trends.",
            ytChannelId = "UC1l_9L8M7f6n3m0A_o_Ew7w"
        ),

        // ─── Hip-Hop & Rap ───
        MusicArtist(
            id = "kendrick_lamar",
            artistName = "Kendrick Lamar",
            genre = "Hip-Hop & Rap",
            avatar = "https://i.ytimg.com/vi/tvTRZJ-4EyI/hqdefault.jpg",
            subscribers = "14.5M subscribers",
            bio = "Pulitzer Prize and Grammy-winning visionary rapper and cultural icon behind Not Like Us.",
            ytChannelId = "UC3lBXqo7qUcMWnpfb-AGJMA"
        ),
        MusicArtist(
            id = "drake",
            artistName = "Drake",
            genre = "Hip-Hop & Rap",
            avatar = "https://i.ytimg.com/vi/uxpDa-c-4Mc/hqdefault.jpg",
            subscribers = "29.8M subscribers",
            bio = "Canadian rapper, singer, and songwriter. Grammy Award-winning global superstar.",
            ytChannelId = "UCByOQJjav0CUDwxCk-jVNRQ"
        ),
        MusicArtist(
            id = "travis_scott",
            artistName = "Travis Scott",
            genre = "Hip-Hop & Rap",
            avatar = "https://i.ytimg.com/vi/6ONRf7h3Mdk/hqdefault.jpg",
            subscribers = "18.2M subscribers",
            bio = "Astroworld and Utopia creator known for cinematic beats, stadium concerts, and sonic energy.",
            ytChannelId = "UCH52o_B6u8fD3-gO0Jp_qDA"
        ),
        MusicArtist(
            id = "future",
            artistName = "Future",
            genre = "Hip-Hop & Rap",
            avatar = "https://i.ytimg.com/vi/mI3uGqD6R1Y/hqdefault.jpg",
            subscribers = "15.4M subscribers",
            bio = "Pioneering Atlanta trap titan with countless platinum anthems and We Don't Trust You.",
            ytChannelId = "UCuP_nQ2qZ23W8G1_Xo-Y_mg"
        ),
        MusicArtist(
            id = "metro_boomin",
            artistName = "Metro Boomin",
            genre = "Hip-Hop & Rap",
            avatar = "https://i.ytimg.com/vi/f17n3Lq1mX0/hqdefault.jpg",
            subscribers = "5.2M subscribers",
            bio = "Generational hip-hop superproducer behind Heroes & Villains and historic collaborations.",
            ytChannelId = "UCy8pW1o1Q3F6pG_lV7n3_9A"
        ),
        MusicArtist(
            id = "j_cole",
            artistName = "J. Cole",
            genre = "Hip-Hop & Rap",
            avatar = "https://i.ytimg.com/vi/e82VE83tWJU/hqdefault.jpg",
            subscribers = "8.1M subscribers",
            bio = "Dreamville founder and elite lyricist celebrated for authentic storytelling.",
            ytChannelId = "UCu_i_1x_1Y4e7_s4kY5hSpg"
        ),
        MusicArtist(
            id = "eminem",
            artistName = "Eminem",
            genre = "Hip-Hop & Rap",
            avatar = "https://i.ytimg.com/vi/YVkUvmDQ3HY/hqdefault.jpg",
            subscribers = "61.5M subscribers",
            bio = "One of the best-selling music artists of all time and hip-hop legend behind The Death of Slim Shady.",
            ytChannelId = "UCfM3zsQsOnfWNUppiycmBuw"
        ),
        MusicArtist(
            id = "21_savage",
            artistName = "21 Savage",
            genre = "Hip-Hop & Rap",
            avatar = "https://i.ytimg.com/vi/d_q4t6jB8h0/hqdefault.jpg",
            subscribers = "11.2M subscribers",
            bio = "Grammy-winning Atlanta star behind american dream and Her Loss.",
            ytChannelId = "UCAw_t0bE1oZq2p_1h4yXzgQ"
        ),

        // ─── R&B & Soul ───
        MusicArtist(
            id = "sza",
            artistName = "SZA",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/SQnc1Q36N20/hqdefault.jpg",
            subscribers = "7.2M subscribers",
            bio = "Grammy-winning R&B superstar behind critically acclaimed SOS, Kill Bill, and Ctrl.",
            ytChannelId = "UCkTaStG9_3T6eD5DkG0H_eQ"
        ),
        MusicArtist(
            id = "the_weeknd",
            artistName = "The Weeknd",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
            subscribers = "35.8M subscribers",
            bio = "Global record-breaker known for Blinding Lights, Starboy, and cinematic dark R&B.",
            ytChannelId = "UC0WP5P-ufpRfjbNrmOWwLBQ"
        ),
        MusicArtist(
            id = "bruno_mars",
            artistName = "Bruno Mars",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/PMivT7MJ41M/hqdefault.jpg",
            subscribers = "39.2M subscribers",
            bio = "15-time Grammy winner, showman, and Silk Sonic co-founder.",
            ytChannelId = "UCtZ648L_iYgK1dC_gJ7_6_g"
        ),
        MusicArtist(
            id = "teddy_swims",
            artistName = "Teddy Swims",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/pP5gq5d9e1s/hqdefault.jpg",
            subscribers = "4.5M subscribers",
            bio = "Soulful vocal powerhouse behind Lose Control and I've Tried Everything But Therapy.",
            ytChannelId = "UC1m_uG5F1oD6q8_w9A1k0jw"
        ),
        MusicArtist(
            id = "chris_brown",
            artistName = "Chris Brown",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/W4fLz4oXUvU/hqdefault.jpg",
            subscribers = "26.8M subscribers",
            bio = "Dynamic R&B singer and dancer with hundreds of Billboard chart-topping singles.",
            ytChannelId = "UC1Oa17_A2o4zEaG6272oF0w"
        ),
        MusicArtist(
            id = "usher",
            artistName = "Usher",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/t5XNwf8CUeE/hqdefault.jpg",
            subscribers = "8.6M subscribers",
            bio = "King of modern R&B and Super Bowl halftime headliner with timeless platinum classics.",
            ytChannelId = "UCjJ8j_2W4Z5r5U5H9k-F1mg"
        ),
        MusicArtist(
            id = "beyonce",
            artistName = "Beyoncé",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/23f8n8xU7n0/hqdefault.jpg",
            subscribers = "27.9M subscribers",
            bio = "Most awarded artist in Grammy history behind Renaissance and Cowboy Carter.",
            ytChannelId = "UC9U_U4_yM79G4i7V20G26vA"
        ),
        MusicArtist(
            id = "brent_faiyaz",
            artistName = "Brent Faiyaz",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/3y7G9m0_1wQ/hqdefault.jpg",
            subscribers = "2.8M subscribers",
            bio = "Independent alternative R&B icon known for smooth melodies and Sonder sound.",
            ytChannelId = "UC9F6m1o1Q3F6pG_lV7n3_9A"
        ),

        // ─── Pop & Top 40 ───
        MusicArtist(
            id = "taylor_swift",
            artistName = "Taylor Swift",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/b1kbLwvqugk/hqdefault.jpg",
            subscribers = "61.2M subscribers",
            bio = "14-time Grammy winner, historic Eras Tour creator, and global cultural force.",
            ytChannelId = "UCqECaJ8Gagnn7YCbPEzWH6g"
        ),
        MusicArtist(
            id = "billie_eilish",
            artistName = "Billie Eilish",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/V1Pl8CzNzCw/hqdefault.jpg",
            subscribers = "51.0M subscribers",
            bio = "Oscar and Grammy-winning pioneer behind HIT ME HARD AND SOFT and Bad Guy.",
            ytChannelId = "UCiGm_E4ZwYSHV3bcW1pnSeQ"
        ),
        MusicArtist(
            id = "olivia_rodrigo",
            artistName = "Olivia Rodrigo",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/Z-9gPh3y4_4/hqdefault.jpg",
            subscribers = "13.8M subscribers",
            bio = "Multi-Grammy winner behind SOUR and GUTS chart-topping stadium anthems.",
            ytChannelId = "UCy3zgWom-5AGypGX_FVTKpg"
        ),
        MusicArtist(
            id = "dua_lipa",
            artistName = "Dua Lipa",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/TUVcZfQe-Kw/hqdefault.jpg",
            subscribers = "24.5M subscribers",
            bio = "Dance-pop sensation behind Future Nostalgia, Levitating, and Radical Optimism.",
            ytChannelId = "UC-J-KZfRV8c13fGA264aggQ"
        ),
        MusicArtist(
            id = "ariana_grande",
            artistName = "Ariana Grande",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/tcYodQoapMg/hqdefault.jpg",
            subscribers = "54.8M subscribers",
            bio = "Vocal powerhouse with multi-platinum albums and eternal sunshine.",
            ytChannelId = "UC9CoOnJ6LwgBi3yzkB56p5w"
        ),
        MusicArtist(
            id = "benson_boone",
            artistName = "Benson Boone",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/e1X_y8q9W7M/hqdefault.jpg",
            subscribers = "4.9M subscribers",
            bio = "Chart-topping pop-rock vocalist behind the viral smash Beautiful Things.",
            ytChannelId = "UCw7tL6n3m0A_o_Ew7w_1l_9"
        ),

        // ─── Rock & Alternative ───
        MusicArtist(
            id = "linkin_park",
            artistName = "Linkin Park",
            genre = "Rock & Alternative",
            avatar = "https://i.ytimg.com/vi/kXYiU_JCYtU/hqdefault.jpg",
            subscribers = "22.5M subscribers",
            bio = "Legendary rock band spanning In the End, Numb, and From Zero stadium tours.",
            ytChannelId = "UCZU9T1ceaOgwfLRq7OKFU4Q"
        ),
        MusicArtist(
            id = "red_hot_chili_peppers",
            artistName = "Red Hot Chili Peppers",
            genre = "Rock & Alternative",
            avatar = "https://i.ytimg.com/vi/sbX_aEl53bU/hqdefault.jpg",
            subscribers = "9.8M subscribers",
            bio = "Funk rock icons inducted into the Rock and Roll Hall of Fame with timeless anthems.",
            ytChannelId = "UCEuOwB9vSL1oPKGNdONB4ig"
        ),
        MusicArtist(
            id = "foo_fighters",
            artistName = "Foo Fighters",
            genre = "Rock & Alternative",
            avatar = "https://i.ytimg.com/vi/SBjQ9tuuTJQ/hqdefault.jpg",
            subscribers = "4.4M subscribers",
            bio = "Dave Grohl-fronted stadium rock champions with 15 Grammy Awards.",
            ytChannelId = "UCi2KNss4Yx73NG0Jxj5jf5A"
        ),
        MusicArtist(
            id = "blink_182",
            artistName = "blink-182",
            genre = "Rock & Alternative",
            avatar = "https://i.ytimg.com/vi/rTI8s0H31ys/hqdefault.jpg",
            subscribers = "4.2M subscribers",
            bio = "Pop-punk royalty behind All The Small Things, What's My Age Again?, and ONE MORE TIME.",
            ytChannelId = "UC3yV1g6_y0G7V9q3L5F1k89"
        ),
        MusicArtist(
            id = "green_day",
            artistName = "Green Day",
            genre = "Rock & Alternative",
            avatar = "https://i.ytimg.com/vi/Soa3gO7tL-c/hqdefault.jpg",
            subscribers = "7.1M subscribers",
            bio = "Rock legends behind American Idiot, Dookie, and Saviors.",
            ytChannelId = "UCy8pW1o1Q3F6pG_lV7n3_9A"
        ),
        MusicArtist(
            id = "hozier",
            artistName = "Hozier",
            genre = "Rock & Alternative",
            avatar = "https://i.ytimg.com/vi/u9NStVpBFzA/hqdefault.jpg",
            subscribers = "6.2M subscribers",
            bio = "Irish singer-songwriter with deep blues-rock soul behind Too Sweet and Take Me to Church.",
            ytChannelId = "UCqnbDFdCpuN8CMEg0VuEBqA"
        ),

        // ─── Country & Americana ───
        MusicArtist(
            id = "morgan_wallen",
            artistName = "Morgan Wallen",
            genre = "Country & Americana",
            avatar = "https://i.ytimg.com/vi/d_q4t6jB8h0/hqdefault.jpg",
            subscribers = "4.2M subscribers",
            bio = "Country superstar with historic multi-week Billboard Hot 100 #1 hits and stadium tours.",
            ytChannelId = "UC2_bV81FpX4r46N_d6e3Btw"
        ),
        MusicArtist(
            id = "zach_bryan",
            artistName = "Zach Bryan",
            genre = "Country & Americana",
            avatar = "https://i.ytimg.com/vi/F2X3Hh0zK6w/hqdefault.jpg",
            subscribers = "2.9M subscribers",
            bio = "Grammy-winning folk and country singer-songwriter capturing millions with raw authenticity.",
            ytChannelId = "UC9Q0n6y4v4D5wQ7o2J6u29A"
        ),
        MusicArtist(
            id = "luke_combs",
            artistName = "Luke Combs",
            genre = "Country & Americana",
            avatar = "https://i.ytimg.com/vi/rTI8s0H31ys/hqdefault.jpg",
            subscribers = "4.5M subscribers",
            bio = "CMA Entertainer of the Year known for Fast Car and authentic country grit.",
            ytChannelId = "UC1Z7J5oYk7fQoK_a2g0p89A"
        ),
        MusicArtist(
            id = "chris_stapleton",
            artistName = "Chris Stapleton",
            genre = "Country & Americana",
            avatar = "https://i.ytimg.com/vi/4zAThXFOy2c/hqdefault.jpg",
            subscribers = "3.1M subscribers",
            bio = "Multiple Grammy and CMA winner renowned for his gritty, emotional soul-country vocals.",
            ytChannelId = "UCuP_nQ2qZ23W8G1_Xo-Y_mg"
        ),
        MusicArtist(
            id = "jelly_roll",
            artistName = "Jelly Roll",
            genre = "Country & Americana",
            avatar = "https://i.ytimg.com/vi/4m2pZ5u_91s/hqdefault.jpg",
            subscribers = "3.8M subscribers",
            bio = "Genre-bending country powerhouse behind Save Me and I Am Not Okay.",
            ytChannelId = "UCByOQJjav0CUDwxCk-jVNRQ"
        ),

        // ─── Afrobeats & Global ───
        MusicArtist(
            id = "burna_boy",
            artistName = "Burna Boy",
            genre = "Afrobeats & Global",
            avatar = "https://i.ytimg.com/vi/EDZ25P43c9E/hqdefault.jpg",
            subscribers = "4.9M subscribers",
            bio = "African Giant and Grammy winner spearheading global Afrobeats.",
            ytChannelId = "UCEzDdNqNkV-7rSfS6IO995A"
        ),
        MusicArtist(
            id = "rema",
            artistName = "Rema",
            genre = "Afrobeats & Global",
            avatar = "https://i.ytimg.com/vi/WcIcVapfqXw/hqdefault.jpg",
            subscribers = "4.8M subscribers",
            bio = "Global hitmaker behind Calm Down and the pioneer of Afrorave sound.",
            ytChannelId = "UCqnbDFdCpuN8CMEg0VuEBqA"
        ),
        MusicArtist(
            id = "wizkid",
            artistName = "Wizkid",
            genre = "Afrobeats & Global",
            avatar = "https://i.ytimg.com/vi/jipQpjUA_o8/hqdefault.jpg",
            subscribers = "3.5M subscribers",
            bio = "Starboy and Essence creator bringing Lagos sound to worldwide arenas.",
            ytChannelId = "UC0q02C63pQ1o1h3Dk8QGfvw"
        ),
        MusicArtist(
            id = "tems",
            artistName = "Tems",
            genre = "Afrobeats & Global",
            avatar = "https://i.ytimg.com/vi/EOrFWBjiZLE/hqdefault.jpg",
            subscribers = "2.1M subscribers",
            bio = "Grammy and Oscar-nominated soulful vocalist behind Born in the Wild.",
            ytChannelId = "UC2_Y3eK9N0vB0r3Pq7V_08Q"
        ),
        MusicArtist(
            id = "asake",
            artistName = "Asake",
            genre = "Afrobeats & Global",
            avatar = "https://i.ytimg.com/vi/yG75P9HhM7g/hqdefault.jpg",
            subscribers = "1.9M subscribers",
            bio = "Fuji-infused Afrobeats sensation filling stadium tours across the globe.",
            ytChannelId = "UCmBA_wu8xGg1OfOkfW13Q0Q"
        ),

        // ─── Latin & Reggaeton ───
        MusicArtist(
            id = "bad_bunny",
            artistName = "Bad Bunny",
            genre = "Latin & Reggaeton",
            avatar = "https://i.ytimg.com/vi/Ws3m13u8W4M/hqdefault.jpg",
            subscribers = "49.0M subscribers",
            bio = "Puerto Rican superstar and the most streamed global Latin artist in history.",
            ytChannelId = "UCmBA_wu8xGg1OfOkfW13Q0Q"
        ),
        MusicArtist(
            id = "karol_g",
            artistName = "Karol G",
            genre = "Latin & Reggaeton",
            avatar = "https://i.ytimg.com/vi/5G_1i7n5c0w/hqdefault.jpg",
            subscribers = "37.5M subscribers",
            bio = "La Bichota and Grammy winner leading contemporary Latin urban music.",
            ytChannelId = "UCyX5_F3F1_aD6q0Q7F1k89A"
        ),
        MusicArtist(
            id = "peso_pluma",
            artistName = "Peso Pluma",
            genre = "Latin & Reggaeton",
            avatar = "https://i.ytimg.com/vi/Q8_1x5hYk3A/hqdefault.jpg",
            subscribers = "7.2M subscribers",
            bio = "Mexican sensation leading the global explosion of Corridos Tumbados and ÉXODO.",
            ytChannelId = "UC3yV1g6_y0G7V9q3L5F1k89"
        ),
        MusicArtist(
            id = "feid",
            artistName = "Feid (FERXXO)",
            genre = "Latin & Reggaeton",
            avatar = "https://i.ytimg.com/vi/Soa3gO7tL-c/hqdefault.jpg",
            subscribers = "6.8M subscribers",
            bio = "Colombian reggaeton innovator known for neon green aesthetics and catchy melodies.",
            ytChannelId = "UCByOQJjav0CUDwxCk-jVNRQ"
        ),
        MusicArtist(
            id = "rauw_alejandro",
            artistName = "Rauw Alejandro",
            genre = "Latin & Reggaeton",
            avatar = "https://i.ytimg.com/vi/W4fLz4oXUvU/hqdefault.jpg",
            subscribers = "14.2M subscribers",
            bio = "Futuristic reggaeton and R&B dancer-singer known for energetic live performances.",
            ytChannelId = "UCH52o_B6u8fD3-gO0Jp_qDA"
        ),

        // ─── Electronic & Dance ───
        MusicArtist(
            id = "calvin_harris",
            artistName = "Calvin Harris",
            genre = "Electronic & Dance",
            avatar = "https://i.ytimg.com/vi/ebXbLfLAC34/hqdefault.jpg",
            subscribers = "19.5M subscribers",
            bio = "Scottish DJ, producer, and singer behind record-breaking EDM anthems.",
            ytChannelId = "UCYbmff210a_604q_Y06A4vA"
        ),
        MusicArtist(
            id = "fred_again",
            artistName = "Fred again..",
            genre = "Electronic & Dance",
            avatar = "https://i.ytimg.com/vi/6ONRf7h3Mdk/hqdefault.jpg",
            subscribers = "1.8M subscribers",
            bio = "Grammy-winning electronic producer revolutionizing live dance sets worldwide.",
            ytChannelId = "UC-J-KZfRV8c13fGA264aggQ"
        ),
        MusicArtist(
            id = "skrillex",
            artistName = "Skrillex",
            genre = "Electronic & Dance",
            avatar = "https://i.ytimg.com/vi/2vjPBrBU-TM/hqdefault.jpg",
            subscribers = "20.8M subscribers",
            bio = "Electronic icon and 9-time Grammy winner defining modern bass and club culture.",
            ytChannelId = "UC5w-71Y5iLp_hP_1sI4yXzg"
        ),
        MusicArtist(
            id = "david_guetta",
            artistName = "David Guetta",
            genre = "Electronic & Dance",
            avatar = "https://i.ytimg.com/vi/JRfuAukYTKg/hqdefault.jpg",
            subscribers = "26.8M subscribers",
            bio = "French DJ and music producer crowned multiple times as the world's #1 DJ.",
            ytChannelId = "UC-J-KZfRV8c13fGA264aggQ"
        ),
        MusicArtist(
            id = "peggy_gou",
            artistName = "Peggy Gou",
            genre = "Electronic & Dance",
            avatar = "https://i.ytimg.com/vi/TUVcZfQe-Kw/hqdefault.jpg",
            subscribers = "850K subscribers",
            bio = "Berlin-based DJ sensation behind global summer anthem (It Goes Like) Nanana.",
            ytChannelId = "UC1l_9L8M7f6n3m0A_o_Ew7w"
        ),
        MusicArtist(
            id = "rufus_du_sol",
            artistName = "RÜFÜS DU SOL",
            genre = "Electronic & Dance",
            avatar = "https://i.ytimg.com/vi/PMivT7MJ41M/hqdefault.jpg",
            subscribers = "1.4M subscribers",
            bio = "Australian live electronic trio renowned for emotive, atmospheric soundscapes.",
            ytChannelId = "UCEuOwB9vSL1oPKGNdONB4ig"
        ),

        // ─── Jazz & Acoustic ───
        MusicArtist(
            id = "tinydesk",
            artistName = "NPR Music Tiny Desk Concerts",
            genre = "Jazz & Acoustic",
            avatar = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=600&q=80",
            subscribers = "9.5M subscribers",
            bio = "Intimate, acoustic live musical performances from top global icons behind the NPR desk.",
            ytChannelId = "UC4eYXhJI4-7wSWc8UNRwD4A"
        ),
        MusicArtist(
            id = "lofi_girl",
            artistName = "Lofi Girl",
            genre = "Jazz & Acoustic",
            avatar = "https://i.ytimg.com/vi/jfKfPfyJRdk/hqdefault.jpg",
            subscribers = "14.2M subscribers",
            bio = "The world's premier peaceful lofi hip hop, jazz, and study beats broadcast.",
            ytChannelId = "UCSJ4gkVC6NrvII8umztf0Ow"
        ),
        MusicArtist(
            id = "laufey",
            artistName = "Laufey",
            genre = "Jazz & Acoustic",
            avatar = "https://i.ytimg.com/vi/u9NStVpBFzA/hqdefault.jpg",
            subscribers = "2.9M subscribers",
            bio = "Grammy-winning Icelandic-Chinese musician bringing traditional jazz pop to a new generation.",
            ytChannelId = "UCy8pW1o1Q3F6pG_lV7n3_9A"
        ),
        MusicArtist(
            id = "norah_jones",
            artistName = "Norah Jones",
            genre = "Jazz & Acoustic",
            avatar = "https://i.ytimg.com/vi/tO4dxvguQDk/hqdefault.jpg",
            subscribers = "2.1M subscribers",
            bio = "9-time Grammy-winning jazz and soul singer-songwriter and pianist.",
            ytChannelId = "UC3yV1g6_y0G7V9q3L5F1k89"
        ),
        MusicArtist(
            id = "jacob_collier",
            artistName = "Jacob Collier",
            genre = "Jazz & Acoustic",
            avatar = "https://i.ytimg.com/vi/4fk2prKnYnI/hqdefault.jpg",
            subscribers = "1.8M subscribers",
            bio = "Multi-instrumentalist and harmony genius bridging jazz, soul, and orchestral music.",
            ytChannelId = "UCf8tD1g0V5LhZ1z_08G4Lsw"
        ),
        MusicArtist(
            id = "miles_davis",
            artistName = "Miles Davis",
            genre = "Jazz & Acoustic",
            avatar = "https://i.ytimg.com/vi/zqNTltOGh5c/hqdefault.jpg",
            subscribers = "720K subscribers",
            bio = "Visionary trumpeter, bandleader, and composer who revolutionized jazz history.",
            ytChannelId = "UCf8tD1g0V5LhZ1z_08G4Lsw"
        )
    )
}
