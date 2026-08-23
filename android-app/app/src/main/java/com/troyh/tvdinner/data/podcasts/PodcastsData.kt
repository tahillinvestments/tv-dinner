package com.troyh.tvdinner.data.podcasts

import com.troyh.tvdinner.data.model.PodcastChannel

object PodcastsData {
    val CHANNELS: List<PodcastChannel> = listOf(
        PodcastChannel(
            id = "chan_jre",
            channelName = "The Joe Rogan Experience",
            host = "Joe Rogan",
            category = "🔥 Trending",
            subscribers = "17M Subscribers",
            avatar = "https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=600&q=80",
            description = "Unfiltered long-form conversations with comedians, scientists, martial artists, and thinkers.",
            ytChannelId = "UCzQUP1qoWDoEbmsQxvdjxgQ"
        ),
        PodcastChannel(
            id = "chan_kill_tony",
            channelName = "Kill Tony",
            host = "Tony Hinchcliffe",
            category = "🔥 Trending",
            subscribers = "1.9M Subscribers",
            avatar = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=600&q=80",
            description = "The top live comedy podcast in the world with Tony Hinchcliffe and Brian Redban.",
            ytChannelId = "UCwzCMiicL-hBUzyjWiJaseg"
        ),
        PodcastChannel(
            id = "chan_theo_von",
            channelName = "This Past Weekend w/ Theo Von",
            host = "Theo Von",
            category = "🔥 Trending",
            subscribers = "3.2M Subscribers",
            avatar = "https://images.unsplash.com/photo-1499209974431-9dac3ea0027f?auto=format&fit=crop&w=600&q=80",
            description = "Heartfelt, hilarious, and bizarre conversations with Theo Von and special guests.",
            ytChannelId = "UCMxOX7b1gF2tZtJc5a8r2kw"
        ),
        PodcastChannel(
            id = "chan_lex_fridman",
            channelName = "Lex Fridman Podcast",
            host = "Lex Fridman",
            category = "🤖 AI & Tech",
            subscribers = "4.2M Subscribers",
            avatar = "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80",
            description = "Conversations about AI, science, technology, philosophy, history, and the human condition.",
            ytChannelId = "UCSHZKyawb77ixDdsGog4iWA"
        ),
        PodcastChannel(
            id = "chan_all_in",
            channelName = "The All-In Podcast",
            host = "Chamath, Jason, Sacks & Friedberg",
            category = "🤖 AI & Tech",
            subscribers = "620K Subscribers",
            avatar = "https://images.unsplash.com/photo-1556761175-5973dc0f32e7?auto=format&fit=crop&w=600&q=80",
            description = "Industry besties cover tech venture capital, economic macro shifts, AI developments, and US geopolitics.",
            ytChannelId = "UCESLZhusAkFfsNsApnjF_Cg"
        ),
        PodcastChannel(
            id = "chan_acquired",
            channelName = "Acquired Podcast",
            host = "Ben Gilbert & David Rosenthal",
            category = "💼 Business & Ideas",
            subscribers = "750K Subscribers",
            avatar = "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=600&q=80",
            description = "The inside story of great companies. Deep dive business breakdowns of Nvidia, Apple, Microsoft, and LVMH.",
            ytChannelId = "UCyFqFYfTW2VoIQKylJ04Rtw"
        ),
        PodcastChannel(
            id = "chan_mkbhd_waveform",
            channelName = "Waveform: The MKBHD Podcast",
            host = "Marques Brownlee & Andrew Manganelli",
            category = "🤖 AI & Tech",
            subscribers = "1.1M Subscribers",
            avatar = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=600&q=80",
            description = "Consumer tech reviews, smartphone innovations, EV hardware, gadget teardowns, and tech news with MKBHD.",
            ytChannelId = "UCEcrRXW3oEYfUctetZTAWLw"
        ),
        PodcastChannel(
            id = "chan_huberman_lab",
            channelName = "Huberman Lab",
            host = "Dr. Andrew Huberman",
            category = "🧠 Science & Health",
            subscribers = "5.5M Subscribers",
            avatar = "https://images.unsplash.com/photo-1507679799987-c73779587ccf?auto=format&fit=crop&w=600&q=80",
            description = "Neuroscience protocols to optimize health, circadian rhythms, sleep quality, dopamine, and physical performance.",
            ytChannelId = "UC2D2CMWXMOVWx7giW1n3LIg"
        ),
        PodcastChannel(
            id = "chan_startalk",
            channelName = "StarTalk",
            host = "Neil deGrasse Tyson",
            category = "🧠 Science & Health",
            subscribers = "3.5M Subscribers",
            avatar = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=600&q=80",
            description = "Astrophysics, space exploration, cosmic mysteries, black holes, and pop culture with Neil deGrasse Tyson.",
            ytChannelId = "UCqoAEDirJPjEUFcF2FklnBA"
        ),
        PodcastChannel(
            id = "chan_diary_ceo",
            channelName = "The Diary Of A CEO",
            host = "Steven Bartlett",
            category = "💼 Business & Ideas",
            subscribers = "7.8M Subscribers",
            avatar = "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=600&q=80",
            description = "Intimate conversations with top scientists, psychologists, CEOs, peak performers, and world experts.",
            ytChannelId = "UCGq-a57w-aPwyi3pW7XLiHw"
        ),
        PodcastChannel(
            id = "chan_veritasium",
            channelName = "Veritasium",
            host = "Derek Muller",
            category = "🧠 Science & Health",
            subscribers = "16M Subscribers",
            avatar = "https://images.unsplash.com/photo-1532094349884-543bc11b234d?auto=format&fit=crop&w=600&q=80",
            description = "An element of truth: fascinating videos about science, physics experiments, and discoveries.",
            ytChannelId = "UCHnyfMqiRRG1u-2MsSQLbXA"
        ),
        PodcastChannel(
            id = "chan_modern_wisdom",
            channelName = "Modern Wisdom",
            host = "Chris Williamson",
            category = "🧠 Science & Health",
            subscribers = "2.4M Subscribers",
            avatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=600&q=80",
            description = "Conversations with top evolutionary psychologists, researchers, fitness experts, and authors on human nature.",
            ytChannelId = "UCIaH-gZIVC432YRjNVvnyCA"
        ),
        PodcastChannel(
            id = "chan_y_combinator",
            channelName = "Y Combinator",
            host = "Garry Tan & YC Partners",
            category = "💼 Business & Ideas",
            subscribers = "1.2M Subscribers",
            avatar = "https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=600&q=80",
            description = "Startup playbook strategies, founder advice, pitch teardowns, and venture-backed company growth.",
            ytChannelId = "UCcefcZRL2oaA_uBNeo5UOWg"
        ),
        PodcastChannel(
            id = "chan_mfm",
            channelName = "My First Million",
            host = "Shaan Puri & Sam Parr",
            category = "💼 Business & Ideas",
            subscribers = "580K Subscribers",
            avatar = "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?auto=format&fit=crop&w=600&q=80",
            description = "Brainstorming business ideas, dissecting lucrative niches, and interviewing eccentric entrepreneurs.",
            ytChannelId = "UCyaN6mg5u8Cjy2ZI4ikWaug"
        ),
        PodcastChannel(
            id = "chan_flagrant",
            channelName = "Flagrant",
            host = "Andrew Schulz",
            category = "🎙️ Culture & Talk",
            subscribers = "1.8M Subscribers",
            avatar = "https://images.unsplash.com/photo-1583795128727-6ec3642408f8?auto=format&fit=crop&w=600&q=80",
            description = "Unfiltered comedy, hot takes, pop culture roasts, and wild banter with Andrew Schulz & team.",
            ytChannelId = "UC0D-L0HfHHEQ5eePZv0vMOA"
        ),
        PodcastChannel(
            id = "chan_bad_friends",
            channelName = "Bad Friends",
            host = "Bobby Lee & Andrew Santino",
            category = "🎙️ Culture & Talk",
            subscribers = "1.6M Subscribers",
            avatar = "https://images.unsplash.com/photo-1527224857830-43a7acc85260?auto=format&fit=crop&w=600&q=80",
            description = "Bobby Lee and Andrew Santino team up for hilarious improvisational comedy and banter.",
            ytChannelId = "UCRBpynZV0b7ww2XMCfC17qg"
        ),
        PodcastChannel(
            id = "chan_conan",
            channelName = "Conan O'Brien Needs A Friend",
            host = "Conan O'Brien",
            category = "🎙️ Culture & Talk",
            subscribers = "1.4M Subscribers",
            avatar = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=600&q=80",
            description = "Late night legend Conan O'Brien hangs out with Hollywood actors, comedians, and music stars.",
            ytChannelId = "UCo3nWXH_6vVJ5-xbF3bKb3Q"
        ),
        PodcastChannel(
            id = "chan_pat_mcafee",
            channelName = "The Pat McAfee Show",
            host = "Pat McAfee",
            category = "🎙️ Culture & Talk",
            subscribers = "2.8M Subscribers",
            avatar = "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?auto=format&fit=crop&w=600&q=80",
            description = "High-energy, unfiltered NFL commentary, sports breakdown, and interviews.",
            ytChannelId = "UCxcTeAKWJca6XyJ37_ZoKIQ"
        ),
        PodcastChannel(
            id = "chan_club_shay_shay",
            channelName = "Club Shay Shay",
            host = "Shannon Sharpe",
            category = "🎙️ Culture & Talk",
            subscribers = "3.5M Subscribers",
            avatar = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?auto=format&fit=crop&w=600&q=80",
            description = "Pro Football Hall of Famer Shannon Sharpe sits down with athletes, icons, and entertainers.",
            ytChannelId = "UCKnodHJpZd8UbSvAufDd3_g"
        ),
        PodcastChannel(
            id = "chan_new_heights",
            channelName = "New Heights",
            host = "Jason & Travis Kelce",
            category = "🔥 Trending",
            subscribers = "2.5M Subscribers",
            avatar = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=600&q=80",
            description = "Super Bowl champion brothers Jason & Travis Kelce discuss NFL life, sports, and pop culture.",
            ytChannelId = "UC2GHn3zI8qjsLFjonjdHB3g"
        ),
        PodcastChannel(
            id = "chan_hot_ones",
            channelName = "Hot Ones (First We Feast)",
            host = "Sean Evans",
            category = "🎙️ Culture & Talk",
            subscribers = "13M Subscribers",
            avatar = "https://images.unsplash.com/photo-1555939594-58d7cb561ad1?auto=format&fit=crop&w=600&q=80",
            description = "The show with hot questions and even hotter wings! Host Sean Evans interviews top celebrities.",
            ytChannelId = "UCJFp8uSYCjXOMnkUyb3CQ3Q"
        ),
        PodcastChannel(
            id = "chan_drink_champs",
            channelName = "Drink Champs",
            host = "N.O.R.E. & DJ EFN",
            category = "🎙️ Culture & Talk",
            subscribers = "1.6M Subscribers",
            avatar = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=600&q=80",
            description = "N.O.R.E. and DJ EFN drink and talk hip-hop, music history, and legendary stories with rap icons.",
            ytChannelId = "UCUseCJIxUbK_WIn0sUvBZVg"
        ),
        PodcastChannel(
            id = "chan_dan_carlin",
            channelName = "Dan Carlin's Hardcore History",
            host = "Dan Carlin",
            category = "🎙️ Culture & Talk",
            subscribers = "950K Subscribers",
            avatar = "https://images.unsplash.com/photo-1461360370896-922624d12aa1?auto=format&fit=crop&w=600&q=80",
            description = "Masterclass historical storytelling exploring ancient empires, World War sagas, and human extremes.",
            ytChannelId = "UCK-hs42hooQwhiS1wlsLORA"
        ),
        PodcastChannel(
            id = "chan_rotten_mango",
            channelName = "Rotten Mango (Stephanie Soo)",
            host = "Stephanie Soo",
            category = "🎙️ Culture & Talk",
            subscribers = "3.8M Subscribers",
            avatar = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=600&q=80",
            description = "Deeply researched true crime cases, psychological mysteries, and global investigative storytelling.",
            ytChannelId = "UCOfRC7fIv9H_DVOaZBqbKpw"
        ),
        PodcastChannel(
            id = "chan_pbd_podcast",
            channelName = "PBD Podcast",
            host = "Patrick Bet-David",
            category = "📰 News & Politics",
            subscribers = "2.2M Subscribers",
            avatar = "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?auto=format&fit=crop&w=600&q=80",
            description = "Patrick Bet-David discusses current events, business, politics, and macroeconomics with guests.",
            ytChannelId = "UCGX7nGXpz-CMO_Arg-cgJ7A"
        ),
        PodcastChannel(
            id = "chan_shawn_ryan",
            channelName = "The Shawn Ryan Show",
            host = "Shawn Ryan",
            category = "📰 News & Politics",
            subscribers = "3.1M Subscribers",
            avatar = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?auto=format&fit=crop&w=600&q=80",
            description = "Former Navy SEAL and CIA contractor Shawn Ryan interviews military veterans, intelligence experts, and whistleblowers.",
            ytChannelId = "UCkoujZQZatbu0mKA2Ucy-kw"
        ),
        PodcastChannel(
            id = "chan_ben_shapiro",
            channelName = "The Ben Shapiro Show",
            host = "Ben Shapiro",
            category = "📰 News & Politics",
            subscribers = "7M Subscribers",
            avatar = "https://images.unsplash.com/photo-1507679799987-c73779587ccf?auto=format&fit=crop&w=600&q=80",
            description = "Daily news analysis, political commentary, and cultural breakdowns from The Daily Wire.",
            ytChannelId = "UCaeO5vkdj5xOQHp4UmIN6dw"
        ),
        PodcastChannel(
            id = "chan_the_daily",
            channelName = "The Daily (NY Times)",
            host = "Michael Barbaro & Sabrina Tavernise",
            category = "📰 News & Politics",
            subscribers = "5M Listeners",
            avatar = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?auto=format&fit=crop&w=600&q=80",
            description = "This is what the news should sound like. The biggest stories of our time from The New York Times.",
            ytChannelId = "UCqnbDFdCpuN8CMEg0VuEBqA"
        ),
        PodcastChannel(
            id = "chan_tinydesk",
            channelName = "NPR Music Tiny Desk Concerts",
            host = "NPR Music",
            category = "🎙️ Culture & Talk",
            subscribers = "9.2M Subscribers",
            avatar = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=600&q=80",
            description = "Intimate, acoustic live musical performances from top global icons behind the NPR desk.",
            ytChannelId = "UC4eYXhJI4-7wSWc8UNRwD4A"
        )
    )
}
