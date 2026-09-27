package com.tvdinner.data.podcasts

import com.tvdinner.data.model.PodcastChannel
import com.tvdinner.data.model.PodcastEpisode

object PodcastsData {
    val CHANNELS: List<PodcastChannel> = listOf(
        // ─── Trending & Comedy ───
        PodcastChannel(
            id = "chan_jre",
            channelName = "The Joe Rogan Experience",
            host = "Joe Rogan",
            category = "🔥 Trending",
            subscribers = "17.5M Subscribers",
            avatar = "https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=600&q=80",
            description = "Unfiltered long-form conversations with comedians, scientists, martial artists, authors, and pop-culture icons.",
            ytChannelId = "UCzQUP1qoWDoEbmsQxvdjxgQ"
        ),
        PodcastChannel(
            id = "chan_kill_tony",
            channelName = "Kill Tony",
            host = "Tony Hinchcliffe",
            category = "🔥 Trending",
            subscribers = "2.1M Subscribers",
            avatar = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=600&q=80",
            description = "The top live comedy podcast in the world with Tony Hinchcliffe, Brian Redban, and amateur comics.",
            ytChannelId = "UCwzCMiicL-hBUzyjWiJaseg"
        ),
        PodcastChannel(
            id = "chan_theo_von",
            channelName = "This Past Weekend w/ Theo Von",
            host = "Theo Von",
            category = "🔥 Trending",
            subscribers = "3.4M Subscribers",
            avatar = "https://images.unsplash.com/photo-1499209974431-9dac3ea0027f?auto=format&fit=crop&w=600&q=80",
            description = "Heartfelt, hilarious, and bizarre conversations with Louisiana comedian Theo Von and special guests.",
            ytChannelId = "UCMxOX7b1gF2tZtJc5a8r2kw"
        ),
        PodcastChannel(
            id = "chan_bad_friends",
            channelName = "Bad Friends",
            host = "Bobby Lee & Andrew Santino",
            category = "🔥 Trending",
            subscribers = "1.8M Subscribers",
            avatar = "https://images.unsplash.com/photo-1527224857830-43a7acc85260?auto=format&fit=crop&w=600&q=80",
            description = "Bobby Lee and Andrew Santino team up for hilarious improvisational comedy, skits, and banter.",
            ytChannelId = "UCRBpynZV0b7ww2XMCfC17qg"
        ),
        PodcastChannel(
            id = "chan_flagrant",
            channelName = "Flagrant",
            host = "Andrew Schulz",
            category = "🔥 Trending",
            subscribers = "1.9M Subscribers",
            avatar = "https://images.unsplash.com/photo-1583795128727-6ec3642408f8?auto=format&fit=crop&w=600&q=80",
            description = "Unfiltered comedy, hot takes, pop culture roasts, and wild conversations with Andrew Schulz & team.",
            ytChannelId = "UC0D-L0HfHHEQ5eePZv0vMOA"
        ),
        PodcastChannel(
            id = "chan_new_heights",
            channelName = "New Heights",
            host = "Jason & Travis Kelce",
            category = "🔥 Trending",
            subscribers = "2.6M Subscribers",
            avatar = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=600&q=80",
            description = "Super Bowl champions Jason & Travis Kelce discuss NFL life, locker room dynamics, and pop culture.",
            ytChannelId = "UC2GHn3zI8qjsLFjonjdHB3g"
        ),
        PodcastChannel(
            id = "chan_matt_shane",
            channelName = "Matt and Shane's Secret Podcast",
            host = "Matt McCusker & Shane Gillis",
            category = "🔥 Trending",
            subscribers = "1.2M Subscribers",
            avatar = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=600&q=80",
            description = "The #1 comedy podcast on Patreon. Shane Gillis and Matt McCusker share wild historical laughs and hilarious commentary.",
            ytChannelId = "UC4fZeoNzGBp0DYU533y-brQ"
        ),

        // ─── AI & Tech ───
        PodcastChannel(
            id = "chan_lex_fridman",
            channelName = "Lex Fridman Podcast",
            host = "Lex Fridman",
            category = "🤖 AI & Tech",
            subscribers = "4.4M Subscribers",
            avatar = "https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80",
            description = "Deep dive conversations about artificial intelligence, science, robotics, physics, and the human condition.",
            ytChannelId = "UCSHZKyawb77ixDdsGog4iWA"
        ),
        PodcastChannel(
            id = "chan_mkbhd_waveform",
            channelName = "Waveform: The MKBHD Podcast",
            host = "Marques Brownlee & Andrew Manganelli",
            category = "🤖 AI & Tech",
            subscribers = "1.2M Subscribers",
            avatar = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=600&q=80",
            description = "Consumer tech reviews, smartphone innovations, EV hardware, gadget teardowns, and tech news with MKBHD.",
            ytChannelId = "UCEcrRXW3oEYfUctetZTAWLw"
        ),
        PodcastChannel(
            id = "chan_all_in",
            channelName = "The All-In Podcast",
            host = "Chamath, Jason, Sacks & Friedberg",
            category = "🤖 AI & Tech",
            subscribers = "650K Subscribers",
            avatar = "https://images.unsplash.com/photo-1556761175-5973dc0f32e7?auto=format&fit=crop&w=600&q=80",
            description = "Industry besties cover tech venture capital, economic macro shifts, AI developments, and US geopolitics.",
            ytChannelId = "UCESLZhusAkFfsNsApnjF_Cg"
        ),
        PodcastChannel(
            id = "chan_acquired",
            channelName = "Acquired Podcast",
            host = "Ben Gilbert & David Rosenthal",
            category = "🤖 AI & Tech",
            subscribers = "820K Subscribers",
            avatar = "https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=600&q=80",
            description = "The inside story of great companies. Deep dive breakdowns of Nvidia, Apple, Microsoft, TSMC, and Hermes.",
            ytChannelId = "UCyFqFYfTW2VoIQKylJ04Rtw"
        ),
        PodcastChannel(
            id = "chan_dwarkesh",
            channelName = "Dwarkesh Podcast",
            host = "Dwarkesh Patel",
            category = "🤖 AI & Tech",
            subscribers = "380K Subscribers",
            avatar = "https://images.unsplash.com/photo-1507679799987-c73779587ccf?auto=format&fit=crop&w=600&q=80",
            description = "Deep, rigorous interviews with leading AI researchers, frontier lab founders, and historians.",
            ytChannelId = "UC5w-71Y5iLp_hP_1sI4yXzg"
        ),
        PodcastChannel(
            id = "chan_hard_fork",
            channelName = "Hard Fork (The New York Times)",
            host = "Kevin Roose & Casey Newton",
            category = "🤖 AI & Tech",
            subscribers = "450K Subscribers",
            avatar = "https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=600&q=80",
            description = "The New York Times podcast exploring the rapid rise of AI and the changing future of tech.",
            ytChannelId = "UCqnbDFdCpuN8CMEg0VuEBqA"
        ),

        // ─── Business & Ideas ───
        PodcastChannel(
            id = "chan_y_combinator",
            channelName = "Y Combinator",
            host = "Garry Tan & YC Partners",
            category = "💼 Business & Ideas",
            subscribers = "1.3M Subscribers",
            avatar = "https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=600&q=80",
            description = "Startup playbook strategies, founder advice, pitch teardowns, and venture-backed company growth.",
            ytChannelId = "UCcefcZRL2oaA_uBNeo5UOWg"
        ),
        PodcastChannel(
            id = "chan_diary_ceo",
            channelName = "The Diary Of A CEO",
            host = "Steven Bartlett",
            category = "💼 Business & Ideas",
            subscribers = "8.2M Subscribers",
            avatar = "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=600&q=80",
            description = "Intimate conversations with top scientists, psychologists, CEOs, peak performers, and world experts.",
            ytChannelId = "UCGq-a57w-aPwyi3pW7XLiHw"
        ),
        PodcastChannel(
            id = "chan_mfm",
            channelName = "My First Million",
            host = "Shaan Puri & Sam Parr",
            category = "💼 Business & Ideas",
            subscribers = "620K Subscribers",
            avatar = "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?auto=format&fit=crop&w=600&q=80",
            description = "Brainstorming business ideas, dissecting lucrative niches, and interviewing eccentric self-made entrepreneurs.",
            ytChannelId = "UCyaN6mg5u8Cjy2ZI4ikWaug"
        ),
        PodcastChannel(
            id = "chan_colin_samir",
            channelName = "The Colin and Samir Show",
            host = "Colin Rosenblum & Samir Chaudry",
            category = "💼 Business & Ideas",
            subscribers = "1.5M Subscribers",
            avatar = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?auto=format&fit=crop&w=600&q=80",
            description = "The premier breakdown of the creator economy, digital media empires, and content entrepreneurship.",
            ytChannelId = "UCaH8_vW-7-e3L7gK7_y3Z7g"
        ),
        PodcastChannel(
            id = "chan_tim_ferriss",
            channelName = "The Tim Ferriss Show",
            host = "Tim Ferriss",
            category = "💼 Business & Ideas",
            subscribers = "1.4M Subscribers",
            avatar = "https://images.unsplash.com/photo-1507679799987-c73779587ccf?auto=format&fit=crop&w=600&q=80",
            description = "Deconstructing world-class performers in business, sports, investing, and writing to extract their tactics.",
            ytChannelId = "UCzgY4Qc_m3f1gG7vP2_k9Xw"
        ),

        // ─── Science & Health ───
        PodcastChannel(
            id = "chan_huberman_lab",
            channelName = "Huberman Lab",
            host = "Dr. Andrew Huberman",
            category = "🧠 Science & Health",
            subscribers = "5.8M Subscribers",
            avatar = "https://images.unsplash.com/photo-1507679799987-c73779587ccf?auto=format&fit=crop&w=600&q=80",
            description = "Neuroscience protocols to optimize health, circadian rhythms, sleep quality, dopamine, and physical performance.",
            ytChannelId = "UC2D2CMWXMOVWx7giW1n3LIg"
        ),
        PodcastChannel(
            id = "chan_startalk",
            channelName = "StarTalk",
            host = "Neil deGrasse Tyson",
            category = "🧠 Science & Health",
            subscribers = "3.6M Subscribers",
            avatar = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=600&q=80",
            description = "Astrophysics, cosmic mysteries, black holes, space missions, and pop culture with Neil deGrasse Tyson.",
            ytChannelId = "UCqoAEDirJPjEUFcF2FklnBA"
        ),
        PodcastChannel(
            id = "chan_modern_wisdom",
            channelName = "Modern Wisdom",
            host = "Chris Williamson",
            category = "🧠 Science & Health",
            subscribers = "2.6M Subscribers",
            avatar = "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=600&q=80",
            description = "Conversations with top evolutionary psychologists, researchers, fitness experts, and authors on human nature.",
            ytChannelId = "UCIaH-gZIVC432YRjNVvnyCA"
        ),
        PodcastChannel(
            id = "chan_veritasium",
            channelName = "Veritasium",
            host = "Derek Muller",
            category = "🧠 Science & Health",
            subscribers = "16.5M Subscribers",
            avatar = "https://images.unsplash.com/photo-1532094349884-543bc11b234d?auto=format&fit=crop&w=600&q=80",
            description = "Counterintuitive physics breakdowns, real-world scientific experiments, engineering marvels, and mathematics.",
            ytChannelId = "UCHnyfMqiRRG1u-2MsSQLbXA"
        ),
        PodcastChannel(
            id = "chan_peter_attia",
            channelName = "The Peter Attia Drive",
            host = "Dr. Peter Attia",
            category = "🧠 Science & Health",
            subscribers = "850K Subscribers",
            avatar = "https://images.unsplash.com/photo-1507679799987-c73779587ccf?auto=format&fit=crop&w=600&q=80",
            description = "Deep dive medical discussions on longevity, metabolic health, cardiovascular disease, cancer prevention, and exercise.",
            ytChannelId = "UCn_0vE5b-m2B_Pq7V_08Qgw"
        ),

        // ─── Culture, Talk & Comedy ───
        PodcastChannel(
            id = "chan_club_shay_shay",
            channelName = "Club Shay Shay",
            host = "Shannon Sharpe",
            category = "🎙️ Culture & Talk",
            subscribers = "3.8M Subscribers",
            avatar = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?auto=format&fit=crop&w=600&q=80",
            description = "Pro Football Hall of Famer Shannon Sharpe sits down with athletes, hip-hop icons, and entertainers for viral conversations.",
            ytChannelId = "UCKnodHJpZd8UbSvAufDd3_g"
        ),
        PodcastChannel(
            id = "chan_hot_ones",
            channelName = "Hot Ones (First We Feast)",
            host = "Sean Evans",
            category = "🎙️ Culture & Talk",
            subscribers = "13.5M Subscribers",
            avatar = "https://images.unsplash.com/photo-1555939594-58d7cb561ad1?auto=format&fit=crop&w=600&q=80",
            description = "The show with hot questions and even hotter wings! Host Sean Evans interviews top global celebrities eating spicy wings.",
            ytChannelId = "UCJFp8uSYCjXOMnkUyb3CQ3Q"
        ),
        PodcastChannel(
            id = "chan_conan",
            channelName = "Conan O'Brien Needs A Friend",
            host = "Conan O'Brien",
            category = "🎙️ Culture & Talk",
            subscribers = "1.5M Subscribers",
            avatar = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=600&q=80",
            description = "Late night legend Conan O'Brien hangs out with Hollywood actors, comedians, and music stars to make real friends.",
            ytChannelId = "UCo3nWXH_6vVJ5-xbF3bKb3Q"
        ),
        PodcastChannel(
            id = "chan_pat_mcafee",
            channelName = "The Pat McAfee Show",
            host = "Pat McAfee",
            category = "🎙️ Culture & Talk",
            subscribers = "2.9M Subscribers",
            avatar = "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?auto=format&fit=crop&w=600&q=80",
            description = "High-energy, unfiltered sports commentary, athlete interviews, and hilarious locker room breakdowns.",
            ytChannelId = "UCxcTeAKWJca6XyJ37_ZoKIQ"
        ),
        PodcastChannel(
            id = "chan_drink_champs",
            channelName = "Drink Champs",
            host = "N.O.R.E. & DJ EFN",
            category = "🎙️ Culture & Talk",
            subscribers = "1.7M Subscribers",
            avatar = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=600&q=80",
            description = "N.O.R.E. and DJ EFN drink and talk hip-hop, music history, and legendary untold stories with rap royalty.",
            ytChannelId = "UCUseCJIxUbK_WIn0sUvBZVg"
        ),
        PodcastChannel(
            id = "chan_tinydesk_pod",
            channelName = "NPR Music Tiny Desk Concerts",
            host = "NPR Music",
            category = "🎙️ Culture & Talk",
            subscribers = "9.5M Subscribers",
            avatar = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=600&q=80",
            description = "Intimate, acoustic live musical performances from top global icons behind the NPR desk in Washington, D.C.",
            ytChannelId = "UC4eYXhJI4-7wSWc8UNRwD4A"
        ),

        // ─── True Crime & Mystery ───
        PodcastChannel(
            id = "chan_rotten_mango",
            channelName = "Rotten Mango (Stephanie Soo)",
            host = "Stephanie Soo",
            category = "🔍 True Crime & Mystery",
            subscribers = "3.9M Subscribers",
            avatar = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=600&q=80",
            description = "Deeply researched true crime cases, psychological mysteries, and global investigative storytelling.",
            ytChannelId = "UCOfRC7fIv9H_DVOaZBqbKpw"
        ),
        PodcastChannel(
            id = "chan_mrballen",
            channelName = "MrBallen Podcast: Strange, Dark & Mysterious",
            host = "MrBallen (John Allen)",
            category = "🔍 True Crime & Mystery",
            subscribers = "9.2M Subscribers",
            avatar = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=600&q=80",
            description = "The internet's top storyteller of the strange, dark, and mysterious delivered in narrative story format.",
            ytChannelId = "UCtY9Qn0Z4t7_L3a_7W_3gug"
        ),
        PodcastChannel(
            id = "chan_dan_carlin",
            channelName = "Dan Carlin's Hardcore History",
            host = "Dan Carlin",
            category = "🔍 True Crime & Mystery",
            subscribers = "980K Subscribers",
            avatar = "https://images.unsplash.com/photo-1461360370896-922624d12aa1?auto=format&fit=crop&w=600&q=80",
            description = "Masterclass historical storytelling exploring ancient empires, World War sagas, and human extremes.",
            ytChannelId = "UCK-hs42hooQwhiS1wlsLORA"
        ),

        // ─── News & Politics ───
        PodcastChannel(
            id = "chan_pbd_podcast",
            channelName = "PBD Podcast",
            host = "Patrick Bet-David",
            category = "📰 News & Politics",
            subscribers = "2.4M Subscribers",
            avatar = "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?auto=format&fit=crop&w=600&q=80",
            description = "Patrick Bet-David discusses current events, business, politics, and macroeconomics with guests.",
            ytChannelId = "UCGX7nGXpz-CMO_Arg-cgJ7A"
        ),
        PodcastChannel(
            id = "chan_shawn_ryan",
            channelName = "The Shawn Ryan Show",
            host = "Shawn Ryan",
            category = "📰 News & Politics",
            subscribers = "3.3M Subscribers",
            avatar = "https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?auto=format&fit=crop&w=600&q=80",
            description = "Former Navy SEAL and CIA contractor Shawn Ryan interviews military veterans, intelligence experts, and whistleblowers.",
            ytChannelId = "UCkoujZQZatbu0mKA2Ucy-kw"
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
        )
    )

    val CURATED_EPISODES: List<PodcastEpisode> = listOf(
        // ─── Trending ───
        PodcastEpisode(
            id = "ep_kt_ZHLhms7ceMs",
            title = "Kill Tony #778 - JIMMY CARR",
            description = "The top live comedy podcast in the world. Tony Hinchcliffe, Brian Redban, and special guest Jimmy Carr.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/ZHLhms7ceMs/hqdefault.jpg",
            videoId = "ZHLhms7ceMs",
            channelName = "Kill Tony",
            channelId = "chan_kill_tony",
            publishedTimestamp = 1754000000000L
        ),
        PodcastEpisode(
            id = "ep_kt_Ugcao1Otpjk",
            title = "Kill Tony #775 - JOE ROGAN + THAT MEXICAN OT",
            description = "Live from the Comedy Mothership in Austin, Texas with Joe Rogan and That Mexican OT.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/Ugcao1Otpjk/hqdefault.jpg",
            videoId = "Ugcao1Otpjk",
            channelName = "Kill Tony",
            channelId = "chan_kill_tony",
            publishedTimestamp = 1753500000000L
        ),
        PodcastEpisode(
            id = "ep_jre_ZACmIrFbfPU",
            title = "Joe Rogan Experience #2534 - Annie Jacobsen",
            description = "Annie Jacobsen is an investigative journalist and author of Nuclear War: A Scenario.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/ZACmIrFbfPU/hqdefault.jpg",
            videoId = "ZACmIrFbfPU",
            channelName = "The Joe Rogan Experience",
            channelId = "chan_jre",
            publishedTimestamp = 1753900000000L
        ),
        PodcastEpisode(
            id = "ep_jre_EvnLN8WETlM",
            title = "Joe Rogan Experience #2531 - Forrest Galante",
            description = "Forrest Galante is a wildlife biologist, conservationist, and television host.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/EvnLN8WETlM/hqdefault.jpg",
            videoId = "EvnLN8WETlM",
            channelName = "The Joe Rogan Experience",
            channelId = "chan_jre",
            publishedTimestamp = 1753000000000L
        ),
        PodcastEpisode(
            id = "ep_tv_cFpQV49JqWI",
            title = "This Past Weekend w/ Theo Von #512: Summer Road Trips",
            description = "Heartfelt, hilarious, and bizarre stories with Louisiana comedian Theo Von.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/cFpQV49JqWI/hqdefault.jpg",
            videoId = "cFpQV49JqWI",
            channelName = "This Past Weekend w/ Theo Von",
            channelId = "chan_theo_von",
            publishedTimestamp = 1753800000000L
        ),
        PodcastEpisode(
            id = "ep_bf_2yuvDzOzqLM",
            title = "Trash Rummaging with Rudy | Ep 339 | Bad Friends",
            description = "Bobby Lee and Andrew Santino team up for hilarious improvisational comedy and banter.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/2yuvDzOzqLM/hqdefault.jpg",
            videoId = "2yuvDzOzqLM",
            channelName = "Bad Friends",
            channelId = "chan_bad_friends",
            publishedTimestamp = 1753700000000L
        ),
        PodcastEpisode(
            id = "ep_fl_YzQ9DlVXoXg",
            title = "Sky Diving is Wild: Andrew Schulz & Flagrant Crew",
            description = "Unfiltered comedy, hot takes, pop culture roasts, and wild studio banter.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/YzQ9DlVXoXg/hqdefault.jpg",
            videoId = "YzQ9DlVXoXg",
            channelName = "Flagrant",
            channelId = "chan_flagrant",
            publishedTimestamp = 1753600000000L
        ),
        PodcastEpisode(
            id = "ep_nh_silQLgVCZVw",
            title = "Jason & Travis Kelce React to Jalen Hurts' Game-Winning Drive",
            description = "Super Bowl champions Jason & Travis Kelce discuss NFL life, locker room dynamics, and pop culture.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/silQLgVCZVw/hqdefault.jpg",
            videoId = "silQLgVCZVw",
            channelName = "New Heights",
            channelId = "chan_new_heights",
            publishedTimestamp = 1753500000000L
        ),

        // ─── AI & Tech ───
        PodcastEpisode(
            id = "ep_lf_vif8NQcjVf0",
            title = "Jensen Huang: NVIDIA – The $4 Trillion Company & the AI Revolution",
            description = "Jensen Huang, CEO of NVIDIA, joins Lex Fridman to discuss GPUs, deep learning, and the path to AGI.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/vif8NQcjVf0/hqdefault.jpg",
            videoId = "vif8NQcjVf0",
            channelName = "Lex Fridman Podcast",
            channelId = "chan_lex_fridman",
            publishedTimestamp = 1754000000000L
        ),
        PodcastEpisode(
            id = "ep_lf_nepKKz_MzFM",
            title = "FFmpeg: The Incredible Technology Behind Video on the Internet",
            description = "Deep dive into FFmpeg, video codecs, compression algorithms, and open source engineering.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/nepKKz-MzFM/hqdefault.jpg",
            videoId = "nepKKz-MzFM",
            channelName = "Lex Fridman Podcast",
            channelId = "chan_lex_fridman",
            publishedTimestamp = 1753200000000L
        ),
        PodcastEpisode(
            id = "ep_wf_PtCMsXYAPyc",
            title = "Framework Laptops and a Robot Cleaner? | Waveform",
            description = "Marques Brownlee and Andrew Manganelli discuss modular laptops, smart cleaning robotics, and tech news.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/PtCMsXYAPyc/hqdefault.jpg",
            videoId = "PtCMsXYAPyc",
            channelName = "Waveform: The MKBHD Podcast",
            channelId = "chan_mkbhd_waveform",
            publishedTimestamp = 1753900000000L
        ),
        PodcastEpisode(
            id = "ep_wf_WVsG3daysEM",
            title = "Samsung's Newest Foldable is Here! | Waveform",
            description = "Hands on teardown and engineering breakdown of the newest foldable display technology.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/WVsG3daysEM/hqdefault.jpg",
            videoId = "WVsG3daysEM",
            channelName = "Waveform: The MKBHD Podcast",
            channelId = "chan_mkbhd_waveform",
            publishedTimestamp = 1753700000000L
        ),
        PodcastEpisode(
            id = "ep_ai_ViqYWhLimGg",
            title = "Chip Stocks Crash, $20B Fund Margin Called, Frontier Labs: SLOW DOWN AI",
            description = "The All-In besties break down semiconductor valuation shocks, venture fund liquidity, and frontier AI safety.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/ViqYWhLimGg/hqdefault.jpg",
            videoId = "ViqYWhLimGg",
            channelName = "The All-In Podcast",
            channelId = "chan_all_in",
            publishedTimestamp = 1753800000000L
        ),
        PodcastEpisode(
            id = "ep_ai_TqNiSTeNtb0",
            title = "The $1/Hour Robot Is Coming: Four Industry Leaders Explain What's Next",
            description = "Robotics automation, humanoid labor costs, and the economic inflection point of embodied AI.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/TqNiSTeNtb0/hqdefault.jpg",
            videoId = "TqNiSTeNtb0",
            channelName = "The All-In Podcast",
            channelId = "chan_all_in",
            publishedTimestamp = 1753300000000L
        ),
        PodcastEpisode(
            id = "ep_yc_5d6y3poKwK4",
            title = "Patrick Collison: Is AI Breaking the Lean Startup Playbook?",
            description = "Stripe CEO Patrick Collison joins Y Combinator to analyze capital efficiency and product velocity.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/5d6y3poKwK4/hqdefault.jpg",
            videoId = "5d6y3poKwK4",
            channelName = "Y Combinator",
            channelId = "chan_y_combinator",
            publishedTimestamp = 1753800000000L
        ),
        PodcastEpisode(
            id = "ep_yc_CxXgV54KzpQ",
            title = "Jeff Dean: The 1% Rule for Building in AI",
            description = "Google Chief Scientist Jeff Dean shares foundational wisdom on scale, architectures, and engineering persistence.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/CxXgV54KzpQ/hqdefault.jpg",
            videoId = "CxXgV54KzpQ",
            channelName = "Y Combinator",
            channelId = "chan_y_combinator",
            publishedTimestamp = 1753600000000L
        ),

        // ─── Business & Ideas ───
        PodcastEpisode(
            id = "ep_acq_hT32G6bZ_lM",
            title = "Disney Built Disneyland in One Year for $17 Million",
            description = "The inside story of how Walt Disney engineered Disneyland with unmatched ambition, speed, and creative obsession.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/hT32G6bZ_lM/hqdefault.jpg",
            videoId = "hT32G6bZ_lM",
            channelName = "Acquired Podcast",
            channelId = "chan_acquired",
            publishedTimestamp = 1753900000000L
        ),
        PodcastEpisode(
            id = "ep_acq_JjDdCToFpUM",
            title = "Walt Disney's Unfinished Sci-Fi City: The REAL EPCOT",
            description = "Ben Gilbert & David Rosenthal explore Walt Disney's original futuristic vision for EPCOT.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/JjDdCToFpUM/hqdefault.jpg",
            videoId = "JjDdCToFpUM",
            channelName = "Acquired Podcast",
            channelId = "chan_acquired",
            publishedTimestamp = 1753500000000L
        ),
        PodcastEpisode(
            id = "ep_mfm_PcTU0yaDfd4",
            title = "The $3 Billion Business Built on Nursery Rhymes",
            description = "Shaan Puri & Sam Parr dissect how digital media brands turned simple concepts into billion-dollar juggernauts.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/PcTU0yaDfd4/hqdefault.jpg",
            videoId = "PcTU0yaDfd4",
            channelName = "My First Million",
            channelId = "chan_mfm",
            publishedTimestamp = 1754000000000L
        ),
        PodcastEpisode(
            id = "ep_doac_8DsalSn5tUk",
            title = "Would You Press the AI Button? Future of Society & Work",
            description = "Steven Bartlett explores the transformative impact of artificial intelligence on careers, creativity, and human relationships.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/8DsalSn5tUk/hqdefault.jpg",
            videoId = "8DsalSn5tUk",
            channelName = "The Diary Of A CEO",
            channelId = "chan_diary_ceo",
            publishedTimestamp = 1753800000000L
        ),
        PodcastEpisode(
            id = "ep_doac_aSch4W4_aLM",
            title = "Druski: A Gun to My Head Changed My Life Forever",
            description = "Intimate and raw conversation with comedy superstar Druski about fear, resilience, and building a cultural empire.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/aSch4W4-aLM/hqdefault.jpg",
            videoId = "aSch4W4-aLM",
            channelName = "The Diary Of A CEO",
            channelId = "chan_diary_ceo",
            publishedTimestamp = 1753600000000L
        ),

        // ─── Science & Health ───
        PodcastEpisode(
            id = "ep_st_E8cXOJlyMZY",
            title = "Your Brain Wasn't Built to Find the Truth | StarTalk",
            description = "Astrophysicist Neil deGrasse Tyson investigates cognitive biases, perception illusions, and the nature of objective reality.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/E8cXOJlyMZY/hqdefault.jpg",
            videoId = "E8cXOJlyMZY",
            channelName = "StarTalk",
            channelId = "chan_startalk",
            publishedTimestamp = 1753900000000L
        ),
        PodcastEpisode(
            id = "ep_st_3yrcD4Ob3qw",
            title = "Finally, The Truth with Michael Shermer | StarTalk",
            description = "Scientific skepticism, belief formation, and understanding how pseudoscience tricks the modern mind.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/3yrcD4Ob3qw/hqdefault.jpg",
            videoId = "3yrcD4Ob3qw",
            channelName = "StarTalk",
            channelId = "chan_startalk",
            publishedTimestamp = 1753400000000L
        ),
        PodcastEpisode(
            id = "ep_hl_vmRWUqkTtKA",
            title = "My Book Protocols: Science-Backed Daily Health Tools",
            description = "Dr. Andrew Huberman summarizes the core protocols for sleep, exercise, nutrition, and cognitive performance.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/vmRWUqkTtKA/hqdefault.jpg",
            videoId = "vmRWUqkTtKA",
            channelName = "Huberman Lab",
            channelId = "chan_huberman_lab",
            publishedTimestamp = 1753900000000L
        ),
        PodcastEpisode(
            id = "ep_hl_lxDf8uEypJU",
            title = "Essentials: How to Become Resilient & Lead Others | Jocko Willink",
            description = "Neurobiology of stress resilience, disciplined mental protocols, and leadership principles under extreme pressure.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/lxDf8uEypJU/hqdefault.jpg",
            videoId = "lxDf8uEypJU",
            channelName = "Huberman Lab",
            channelId = "chan_huberman_lab",
            publishedTimestamp = 1753600000000L
        ),
        PodcastEpisode(
            id = "ep_ver_VKlulHwMxgU",
            title = "What Happens When You Open the Valve? | Veritasium",
            description = "Derek Muller explores counterintuitive fluid dynamics, vacuum physics, and mind-bending thermodynamic experiments.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/VKlulHwMxgU/hqdefault.jpg",
            videoId = "VKlulHwMxgU",
            channelName = "Veritasium",
            channelId = "chan_veritasium",
            publishedTimestamp = 1753800000000L
        ),
        PodcastEpisode(
            id = "ep_ver_JsBZOcqZerk",
            title = "The Insane Real Engineering of the Nazi Enigma Machine",
            description = "Complete mechanical and cryptographic breakdown of the German WWII rotor cipher device.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/JsBZOcqZerk/hqdefault.jpg",
            videoId = "JsBZOcqZerk",
            channelName = "Veritasium",
            channelId = "chan_veritasium",
            publishedTimestamp = 1753300000000L
        ),
        PodcastEpisode(
            id = "ep_mw_k1iQI4GKfyo",
            title = "How Do You Pace a Full Habit Reset? Protocols for Focus",
            description = "Chris Williamson shares evolutionary psychology tools to overcome distraction and rebuild deep focus habits.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/k1iQI4GKfyo/hqdefault.jpg",
            videoId = "k1iQI4GKfyo",
            channelName = "Modern Wisdom",
            channelId = "chan_modern_wisdom",
            publishedTimestamp = 1753900000000L
        ),

        // ─── Culture & Talk ───
        PodcastEpisode(
            id = "ep_css_zdnn8QM__nU",
            title = "Mike Bibby says Kings Were Robbed Against Kobe x Shaq Lakers | Nightcap",
            description = "Shannon Sharpe and Chad Ochocinco interview Mike Bibby about the controversial 2002 Western Conference Finals.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/zdnn8QM_-nU/hqdefault.jpg",
            videoId = "zdnn8QM_-nU",
            channelName = "Club Shay Shay",
            channelId = "chan_club_shay_shay",
            publishedTimestamp = 1753900000000L
        ),
        PodcastEpisode(
            id = "ep_ho_tzyK0R1h9Jw",
            title = "Sean Evans Takes On Nuclear Hot Wings & Celebrity Hot Takes",
            description = "The show with hot questions and even hotter wings! Host Sean Evans breaks down the art of the interview.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/tzyK0R1h9Jw/hqdefault.jpg",
            videoId = "tzyK0R1h9Jw",
            channelName = "Hot Ones (First We Feast)",
            channelId = "chan_hot_ones",
            publishedTimestamp = 1753900000000L
        ),
        PodcastEpisode(
            id = "ep_co_7p4E_vP4Hi0",
            title = "Jimmy Fallon Fills In for Conan on 'Late Night' Nostalgia",
            description = "Conan O'Brien and Jimmy Fallon reflect on the wild history of Late Night television and comedic memories.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/7p4E_vP4Hi0/hqdefault.jpg",
            videoId = "7p4E_vP4Hi0",
            channelName = "Conan O'Brien Needs A Friend",
            channelId = "chan_conan",
            publishedTimestamp = 1753800000000L
        ),
        PodcastEpisode(
            id = "ep_pm_YvKgqFdwV6w",
            title = "Are The Saints A Sneaky Contender After Extending Chris Olave?",
            description = "Pat McAfee and the boys break down NFL contracts, locker room culture, and NFC playoff races.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/YvKgqFdwV6w/hqdefault.jpg",
            videoId = "YvKgqFdwV6w",
            channelName = "The Pat McAfee Show",
            channelId = "chan_pat_mcafee",
            publishedTimestamp = 1753900000000L
        ),
        PodcastEpisode(
            id = "ep_dc_yir4GNA52xE",
            title = "50 Cent: From Queens to Kingpin | Full Episode | Drink Champs",
            description = "N.O.R.E. and DJ EFN sit down with 50 Cent for a legendary, hilarious, and historic hip-hop conversation.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/yir4GNA52xE/hqdefault.jpg",
            videoId = "yir4GNA52xE",
            channelName = "Drink Champs",
            channelId = "chan_drink_champs",
            publishedTimestamp = 1753500000000L
        ),
        PodcastEpisode(
            id = "ep_td_IN5lC_T7yOA",
            title = "Tori Kelly: NPR Music Tiny Desk Concert (Acoustic Set)",
            description = "Grammy-winning vocalist Tori Kelly delivers an extraordinary, intimate acoustic vocal performance.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/IN5lC_T7yOA/hqdefault.jpg",
            videoId = "IN5lC_T7yOA",
            channelName = "NPR Music Tiny Desk Concerts",
            channelId = "chan_tinydesk_pod",
            publishedTimestamp = 1753900000000L
        ),

        // ─── True Crime & Mystery ───
        PodcastEpisode(
            id = "ep_rm_S3IIGkHMcv0",
            title = "Lindsay Clancy & the 13 Medications Prescribed: 48 Hours of No Sleep",
            description = "Stephanie Soo presents an in-depth investigative breakdown of the tragic Clancy case and psychiatric medications.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/S3IIGkHMcv0/hqdefault.jpg",
            videoId = "S3IIGkHMcv0",
            channelName = "Rotten Mango (Stephanie Soo)",
            channelId = "chan_rotten_mango",
            publishedTimestamp = 1754000000000L
        ),
        PodcastEpisode(
            id = "ep_rm_CYJ7dk5_EIg",
            title = "Lindsay Clancy Trial Breakdown: Psychological Mysteries Unraveled",
            description = "Comprehensive analysis of medical witness testimonies, court arguments, and forensic details.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/CYJ7dk5_EIg/hqdefault.jpg",
            videoId = "CYJ7dk5_EIg",
            channelName = "Rotten Mango (Stephanie Soo)",
            channelId = "chan_rotten_mango",
            publishedTimestamp = 1753800000000L
        ),
        PodcastEpisode(
            id = "ep_dc_Gu4syP_IzQs",
            title = "Show 74 - Mania for Subjugation IV (Full Sagas)",
            description = "Dan Carlin masterfully chronicles the brutal geopolitical games, ancient conflicts, and human extremes.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/Gu4syP_IzQs/hqdefault.jpg",
            videoId = "Gu4syP_IzQs",
            channelName = "Dan Carlin's Hardcore History",
            channelId = "chan_dan_carlin",
            publishedTimestamp = 1753700000000L
        ),
        PodcastEpisode(
            id = "ep_dc_fI2xUkLdoGo",
            title = "Show 73 - Mania for Subjugation III: Empires and Conflicts",
            description = "A deep dive into historical empire-building, military logistics, and the costs of total war.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/fI2xUkLdoGo/hqdefault.jpg",
            videoId = "fI2xUkLdoGo",
            channelName = "Dan Carlin's Hardcore History",
            channelId = "chan_dan_carlin",
            publishedTimestamp = 1752500000000L
        ),

        // ─── News & Politics ───
        PodcastEpisode(
            id = "ep_pbd_QjZ5e8u_pRw",
            title = "PBD Podcast: Economic Realities, Election Predictions & World Affairs",
            description = "Patrick Bet-David and the panel discuss macroeconomics, federal debt, and geopolitical power struggles.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/QjZ5e8u_pRw/hqdefault.jpg",
            videoId = "QjZ5e8u_pRw",
            channelName = "PBD Podcast",
            channelId = "chan_pbd_podcast",
            publishedTimestamp = 1753900000000L
        ),
        PodcastEpisode(
            id = "ep_sr_mE8Q1_X1jZg",
            title = "Shawn Ryan Show: Special Forces Operations & Intelligence Truths",
            description = "Former Navy SEAL Shawn Ryan interviews veteran operators on covert operations and national security.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/mE8Q1_X1jZg/hqdefault.jpg",
            videoId = "mE8Q1_X1jZg",
            channelName = "The Shawn Ryan Show",
            channelId = "chan_shawn_ryan",
            publishedTimestamp = 1753800000000L
        ),
        PodcastEpisode(
            id = "ep_td_jNQXAC9IVRw",
            title = "The Daily: Inside the Battle for the Middle East | NY Times",
            description = "Michael Barbaro and New York Times foreign correspondents investigate diplomatic negotiations.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/jNQXAC9IVRw/hqdefault.jpg",
            videoId = "jNQXAC9IVRw",
            channelName = "The Daily (NY Times)",
            channelId = "chan_the_daily",
            publishedTimestamp = 1753900000000L
        ),
        PodcastEpisode(
            id = "ep_td_sK6bO_wL9pU",
            title = "The Daily: What Silicon Valley's Political Shift Means for America",
            description = "An exploration of venture capitalists, tech founders, and the changing political dynamics of modern technology.",
            published = "Recent",
            thumbnailUrl = "https://img.youtube.com/vi/sK6bO_wL9pU/hqdefault.jpg",
            videoId = "sK6bO_wL9pU",
            channelName = "The Daily (NY Times)",
            channelId = "chan_the_daily",
            publishedTimestamp = 1753700000000L
        )
    )

    fun interleaveEpisodes(episodes: List<PodcastEpisode>, maxConsecutive: Int = 1): List<PodcastEpisode> {
        if (episodes.size <= 2) return episodes
        val result = mutableListOf<PodcastEpisode>()
        val remaining = episodes.toMutableList()
        var lastChannel = ""
        var consecutiveCount = 0

        while (remaining.isNotEmpty()) {
            val nextIdx = remaining.indexOfFirst { ep ->
                val ch = ep.channelName.trim().lowercase()
                ch != lastChannel || consecutiveCount < maxConsecutive
            }
            val chosenIdx = if (nextIdx >= 0) nextIdx else 0
            val chosen = remaining.removeAt(chosenIdx)
            val chosenChannel = chosen.channelName.trim().lowercase()

            if (chosenChannel == lastChannel) {
                consecutiveCount++
            } else {
                lastChannel = chosenChannel
                consecutiveCount = 1
            }
            result.add(chosen)
        }
        return result
    }

    fun getCuratedEpisodesForCategory(category: String): List<PodcastEpisode> {
        val clean = category.lowercase().replace(Regex("[^a-z0-9]"), "").trim()
        val rawList = if (clean.contains("trending") || clean == "all" || clean.isBlank()) {
            CURATED_EPISODES
        } else {
            CURATED_EPISODES.filter { ep ->
                val ch = CHANNELS.find { it.channelName.equals(ep.channelName, ignoreCase = true) }
                val cat = ch?.category?.lowercase() ?: ""
                when {
                    clean.contains("tech") || clean.contains("ai") -> cat.contains("tech") || cat.contains("ai") || ep.channelName.contains("Lex", true) || ep.channelName.contains("MKBHD", true) || ep.channelName.contains("Combinator", true) || ep.channelName.contains("All-In", true)
                    clean.contains("business") || clean.contains("idea") -> cat.contains("business") || cat.contains("idea") || ep.channelName.contains("Acquired", true) || ep.channelName.contains("Million", true) || ep.channelName.contains("CEO", true)
                    clean.contains("science") || clean.contains("health") -> cat.contains("science") || cat.contains("health") || ep.channelName.contains("StarTalk", true) || ep.channelName.contains("Huberman", true) || ep.channelName.contains("Veritasium", true) || ep.channelName.contains("Wisdom", true)
                    clean.contains("culture") || clean.contains("talk") || clean.contains("comedy") -> cat.contains("culture") || cat.contains("talk") || cat.contains("comedy") || ep.channelName.contains("Shay", true) || ep.channelName.contains("Hot Ones", true) || ep.channelName.contains("Conan", true) || ep.channelName.contains("McAfee", true) || ep.channelName.contains("Drink", true) || ep.channelName.contains("Tiny", true)
                    clean.contains("crime") || clean.contains("mystery") -> cat.contains("crime") || cat.contains("mystery") || ep.channelName.contains("Rotten", true) || ep.channelName.contains("Carlin", true) || ep.channelName.contains("MrBallen", true)
                    clean.contains("news") || clean.contains("politics") -> cat.contains("news") || cat.contains("politics") || ep.channelName.contains("PBD", true) || ep.channelName.contains("Shawn", true) || ep.channelName.contains("Daily", true)
                    else -> true
                }
            }.ifEmpty { CURATED_EPISODES }
        }
        return interleaveEpisodes(rawList, maxConsecutive = 1)
    }

    fun getCuratedEpisodesForChannel(channelName: String): List<PodcastEpisode> {
        val clean = channelName.lowercase().trim()
        val direct = CURATED_EPISODES.filter {
            it.channelName.lowercase().contains(clean) || clean.contains(it.channelName.lowercase())
        }
        if (direct.isNotEmpty()) return direct
        return CURATED_EPISODES.take(8)
    }

    fun getCuratedEpisodesForChannel(channel: PodcastChannel): List<PodcastEpisode> {
        return getCuratedEpisodesForChannel(channel.channelName)
    }
}

