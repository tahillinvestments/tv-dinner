package com.troyh.tvdinner.data.music

import com.troyh.tvdinner.data.model.MusicArtist
import com.troyh.tvdinner.data.model.MusicGenre

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
        MusicGenre("jazz", "🎹 Jazz & Blues", "🎹")
    )

    val ARTISTS = listOf(
        // Hip-Hop
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
            id = "kendrick_lamar",
            artistName = "Kendrick Lamar",
            genre = "Hip-Hop & Rap",
            avatar = "https://i.ytimg.com/vi/tvTRZJ-4EyI/hqdefault.jpg",
            subscribers = "13.4M subscribers",
            bio = "Pulitzer Prize and Grammy-winning visionary rapper and cultural icon.",
            ytChannelId = "UC3lBXqo7qUcMWnpfb-AGJMA"
        ),
        MusicArtist(
            id = "eminem",
            artistName = "Eminem",
            genre = "Hip-Hop & Rap",
            avatar = "https://i.ytimg.com/vi/YVkUvmDQ3HY/hqdefault.jpg",
            subscribers = "60.2M subscribers",
            bio = "One of the best-selling music artists of all time and hip-hop legend.",
            ytChannelId = "UCfM3zsQsOnfWNUppiycmBuw"
        ),
        MusicArtist(
            id = "travis_scott",
            artistName = "Travis Scott",
            genre = "Hip-Hop & Rap",
            avatar = "https://i.ytimg.com/vi/6ONRf7h3Mdk/hqdefault.jpg",
            subscribers = "17.9M subscribers",
            bio = "Astroworld and Utopia creator known for cinematic beats and sonic energy.",
            ytChannelId = "UCH52o_B6u8fD3-gO0Jp_qDA"
        ),
        MusicArtist(
            id = "future",
            artistName = "Future",
            genre = "Hip-Hop & Rap",
            avatar = "https://i.ytimg.com/vi/mI3uGqD6R1Y/hqdefault.jpg",
            subscribers = "15.1M subscribers",
            bio = "Pioneering Atlanta trap titan with countless platinum anthems.",
            ytChannelId = "UCuP_nQ2qZ23W8G1_Xo-Y_mg"
        ),
        MusicArtist(
            id = "j_cole",
            artistName = "J. Cole",
            genre = "Hip-Hop & Rap",
            avatar = "https://i.ytimg.com/vi/e82VE83tWJU/hqdefault.jpg",
            subscribers = "7.8M subscribers",
            bio = "Dreamville founder and lyricist celebrated for storytelling mastery.",
            ytChannelId = "UCu_i_1x_1Y4e7_s4kY5hSpg"
        ),

        // R&B & Soul
        MusicArtist(
            id = "sza",
            artistName = "SZA",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/SQnc1Q36N20/hqdefault.jpg",
            subscribers = "6.5M subscribers",
            bio = "Grammy-winning R&B superstar behind critically acclaimed SOS and Ctrl.",
            ytChannelId = "UCkTaStG9_3T6eD5DkG0H_eQ"
        ),
        MusicArtist(
            id = "the_weeknd",
            artistName = "The Weeknd",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/4NRXx6U8ABQ/hqdefault.jpg",
            subscribers = "34.7M subscribers",
            bio = "Global record-breaker known for Blinding Lights, Starboy, and cinematic R&B.",
            ytChannelId = "UC0WP5P-ufpRfjbNrmOWwLBQ"
        ),
        MusicArtist(
            id = "bruno_mars",
            artistName = "Bruno Mars",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/PMivT7MJ41M/hqdefault.jpg",
            subscribers = "38.5M subscribers",
            bio = "15-time Grammy winner, showman, and Silk Sonic co-founder.",
            ytChannelId = "UCtZ648L_iYgK1dC_gJ7_6_g"
        ),
        MusicArtist(
            id = "chris_brown",
            artistName = "Chris Brown",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/W4fLz4oXUvU/hqdefault.jpg",
            subscribers = "26.1M subscribers",
            bio = "Dynamic R&B singer and dancer with hundreds of chart-topping singles.",
            ytChannelId = "UC1Oa17_A2o4zEaG6272oF0w"
        ),
        MusicArtist(
            id = "usher",
            artistName = "Usher",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/t5XNwf8CUeE/hqdefault.jpg",
            subscribers = "8.2M subscribers",
            bio = "King of R&B and Super Bowl halftime headliner with timeless hits.",
            ytChannelId = "UCjJ8j_2W4Z5r5U5H9k-F1mg"
        ),
        MusicArtist(
            id = "beyonce",
            artistName = "Beyoncé",
            genre = "R&B & Soul",
            avatar = "https://i.ytimg.com/vi/23f8n8xU7n0/hqdefault.jpg",
            subscribers = "27.4M subscribers",
            bio = "Most awarded artist in Grammy history and cultural tour-de-force.",
            ytChannelId = "UC9U_U4_yM79G4i7V20G26vA"
        ),

        // Pop
        MusicArtist(
            id = "taylor_swift",
            artistName = "Taylor Swift",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/b1kbLwvqugk/hqdefault.jpg",
            subscribers = "60.5M subscribers",
            bio = "14-time Grammy winner and global record-breaking Eras Tour creator.",
            ytChannelId = "UCqECaJ8Gagnn7YCbPEzWH6g"
        ),
        MusicArtist(
            id = "billie_eilish",
            artistName = "Billie Eilish",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/V1Pl8CzNzCw/hqdefault.jpg",
            subscribers = "50.1M subscribers",
            bio = "Oscar and Grammy-winning pioneer of modern pop and alternative sound.",
            ytChannelId = "UCiGm_E4ZwYSHV3bcW1pnSeQ"
        ),
        MusicArtist(
            id = "ariana_grande",
            artistName = "Ariana Grande",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/tcYodQoapMg/hqdefault.jpg",
            subscribers = "54.0M subscribers",
            bio = "Vocal powerhouse with multi-platinum albums and eternal sunshine.",
            ytChannelId = "UC9CoOnJ6LwgBi3yzkB56p5w"
        ),
        MusicArtist(
            id = "dua_lipa",
            artistName = "Dua Lipa",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/TUVcZfQe-Kw/hqdefault.jpg",
            subscribers = "23.8M subscribers",
            bio = "Dance-pop sensation behind Future Nostalgia and Radical Optimism.",
            ytChannelId = "UC-J-KZfRV8c13fGA264aggQ"
        ),
        MusicArtist(
            id = "olivia_rodrigo",
            artistName = "Olivia Rodrigo",
            genre = "Pop & Top 40",
            avatar = "https://i.ytimg.com/vi/Z-9gPh3y4_4/hqdefault.jpg",
            subscribers = "13.2M subscribers",
            bio = "Multi-Grammy winner behind SOUR and GUTS chart-topping anthems.",
            ytChannelId = "UCy3zgWom-5AGypGX_FVTKpg"
        ),

        // Rock
        MusicArtist(
            id = "linkin_park",
            artistName = "Linkin Park",
            genre = "Rock & Alternative",
            avatar = "https://i.ytimg.com/vi/kXYiU_JCYtU/hqdefault.jpg",
            subscribers = "21.6M subscribers",
            bio = "Legendary rock band spanning In the End, Numb, and From Zero.",
            ytChannelId = "UCZU9T1ceaOgwfLRq7OKFU4Q"
        ),
        MusicArtist(
            id = "red_hot_chili_peppers",
            artistName = "Red Hot Chili Peppers",
            genre = "Rock & Alternative",
            avatar = "https://i.ytimg.com/vi/sbX_aEl53bU/hqdefault.jpg",
            subscribers = "9.5M subscribers",
            bio = "Funk rock icons inducted into the Rock and Roll Hall of Fame.",
            ytChannelId = "UCEuOwB9vSL1oPKGNdONB4ig"
        ),
        MusicArtist(
            id = "foo_fighters",
            artistName = "Foo Fighters",
            genre = "Rock & Alternative",
            avatar = "https://i.ytimg.com/vi/SBjQ9tuuTJQ/hqdefault.jpg",
            subscribers = "4.2M subscribers",
            bio = "Dave Grohl-fronted stadium rock champions with 15 Grammys.",
            ytChannelId = "UCi2KNss4Yx73NG0Jxj5jf5A"
        ),

        // Country
        MusicArtist(
            id = "morgan_wallen",
            artistName = "Country & Americana",
            genre = "Country & Americana",
            avatar = "https://i.ytimg.com/vi/d_q4t6jB8h0/hqdefault.jpg",
            subscribers = "3.8M subscribers",
            bio = "Country superstar with historic multi-week Billboard Hot 100 #1 hits.",
            ytChannelId = "UC2_bV81FpX4r46N_d6e3Btw"
        ),
        MusicArtist(
            id = "luke_combs",
            artistName = "Luke Combs",
            genre = "Country & Americana",
            avatar = "https://i.ytimg.com/vi/rTI8s0H31ys/hqdefault.jpg",
            subscribers = "4.1M subscribers",
            bio = "CMA Entertainer of the Year known for Fast Car and authentic country grit.",
            ytChannelId = "UC1Z7J5oYk7fQoK_a2g0p89A"
        ),
        MusicArtist(
            id = "zach_bryan",
            artistName = "Zach Bryan",
            genre = "Country & Americana",
            avatar = "https://i.ytimg.com/vi/F2X3Hh0zK6w/hqdefault.jpg",
            subscribers = "2.4M subscribers",
            bio = "Grammy-winning folk and country singer-songwriter capturing millions.",
            ytChannelId = "UC9Q0n6y4v4D5wQ7o2J6u29A"
        ),

        // Afrobeats
        MusicArtist(
            id = "burna_boy",
            artistName = "Burna Boy",
            genre = "Afrobeats & Global",
            avatar = "https://i.ytimg.com/vi/EDZ25P43c9E/hqdefault.jpg",
            subscribers = "4.6M subscribers",
            bio = "African Giant and Grammy winner spearheading global Afrobeats.",
            ytChannelId = "UCEzDdNqNkV-7rSfS6IO995A"
        ),
        MusicArtist(
            id = "wizkid",
            artistName = "Wizkid",
            genre = "Afrobeats & Global",
            avatar = "https://i.ytimg.com/vi/jipQpjUA_o8/hqdefault.jpg",
            subscribers = "3.2M subscribers",
            bio = "Starboy and Essence creator bringing Lagos sound to the world.",
            ytChannelId = "UC0q02C63pQ1o1h3Dk8QGfvw"
        ),
        MusicArtist(
            id = "tems",
            artistName = "Tems",
            genre = "Afrobeats & Global",
            avatar = "https://i.ytimg.com/vi/EOrFWBjiZLE/hqdefault.jpg",
            subscribers = "1.8M subscribers",
            bio = "Grammy and Oscar-nominated soulful vocalist and songwriter.",
            ytChannelId = "UC2_Y3eK9N0vB0r3Pq7V_08Q"
        ),

        // Latin
        MusicArtist(
            id = "bad_bunny",
            artistName = "Bad Bunny",
            genre = "Latin & Reggaeton",
            avatar = "https://i.ytimg.com/vi/Ws3m13u8W4M/hqdefault.jpg",
            subscribers = "48.2M subscribers",
            bio = "Puerto Rican superstar and the most streamed global artist.",
            ytChannelId = "UCmBA_wu8xGg1OfOkfW13Q0Q"
        ),
        MusicArtist(
            id = "karol_g",
            artistName = "Karol G",
            genre = "Latin & Reggaeton",
            avatar = "https://i.ytimg.com/vi/5G_1i7n5c0w/hqdefault.jpg",
            subscribers = "36.8M subscribers",
            bio = "La Bichota and Grammy winner leading contemporary Latin urban music.",
            ytChannelId = "UCyX5_F3F1_aD6q0Q7F1k89A"
        ),
        MusicArtist(
            id = "peso_pluma",
            artistName = "Peso Pluma",
            genre = "Latin & Reggaeton",
            avatar = "https://i.ytimg.com/vi/Q8_1x5hYk3A/hqdefault.jpg",
            subscribers = "6.5M subscribers",
            bio = "Mexican sensation leading the global explosion of Corridos Tumbados.",
            ytChannelId = "UC3yV1g6_y0G7V9q3L5F1k89"
        )
    )
}
