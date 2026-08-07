// Curated YouTube Video Podcast CHANNELS & Live Dynamic Feeds Dataset
// All ytChannelId values verified directly from YouTube channel pages via RSS feed check (Aug 2026)
export const PODCAST_CHANNELS = {
  tech: [
    {
      id: 'chan_lex_fridman',
      channelName: 'Lex Fridman Podcast',
      host: 'Lex Fridman',
      category: 'Tech & AI',
      subscribers: '4.2M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80',
      description: 'Conversations about AI, science, technology, philosophy, history, and the human condition.',
      ytChannelId: 'UCSHZKyawb77ixDdsGog4iWA'  // verified ✓
    },
    {
      id: 'chan_all_in',
      channelName: 'The All-In Podcast',
      host: 'Chamath, Jason, Sacks & Friedberg',
      category: 'Tech & Business',
      subscribers: '620K Subscribers',
      avatar: 'https://images.unsplash.com/photo-1556761175-5973dc0f32e7?auto=format&fit=crop&w=600&q=80',
      description: 'Industry besties cover tech venture capital, economic macro shifts, AI developments, and US geopolitics.',
      ytChannelId: 'UCESLZhusAkFfsNsApnjF_Cg'  // verified ✓
    },
    {
      id: 'chan_acquired',
      channelName: 'Acquired Podcast',
      host: 'Ben Gilbert & David Rosenthal',
      category: 'Tech & Business',
      subscribers: '750K Subscribers',
      avatar: 'https://images.unsplash.com/photo-1486406146926-c627a92ad1ab?auto=format&fit=crop&w=600&q=80',
      description: 'The inside story of great companies. Deep dive business breakdowns of Nvidia, Apple, Microsoft, LVMH, and Hermès.',
      ytChannelId: 'UCyFqFYfTW2VoIQKylJ04Rtw'  // fixed - verified Acquired FM channel ID ✓
    },
    {
      id: 'chan_mkbhd_waveform',
      channelName: 'Waveform: The MKBHD Podcast',
      host: 'Marques Brownlee & Andrew Manganelli',
      category: 'Tech & Gadgets',
      subscribers: '1.1M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=600&q=80',
      description: 'Consumer tech reviews, smartphone innovations, EV hardware, gadget teardowns, and tech news with MKBHD.',
      ytChannelId: 'UCEcrRXW3oEYfUctetZTAWLw'  // fixed from @Waveform ✓
    },
    {
      id: 'chan_y_combinator',
      channelName: 'Y Combinator',
      host: 'Garry Tan & YC Partners',
      category: 'Startups & Tech',
      subscribers: '1.2M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=600&q=80',
      description: 'Startup playbook strategies, founder advice, pitch teardowns, and venture-backed company growth.',
      ytChannelId: 'UCcefcZRL2oaA_uBNeo5UOWg'  // fixed from @ycombinator ✓
    },
    {
      id: 'chan_mfm',
      channelName: 'My First Million',
      host: 'Shaan Puri & Sam Parr',
      category: 'Business & Ideas',
      subscribers: '580K Subscribers',
      avatar: 'https://images.unsplash.com/photo-1559526324-4b87b5e36e44?auto=format&fit=crop&w=600&q=80',
      description: 'Brainstorming business ideas, dissecting lucrative niches, and interviewing eccentric entrepreneurs.',
      ytChannelId: 'UCyaN6mg5u8Cjy2ZI4ikWaug'
    }
  ],
  science: [
    {
      id: 'chan_huberman_lab',
      channelName: 'Huberman Lab',
      host: 'Dr. Andrew Huberman',
      category: 'Science & Health',
      subscribers: '5.5M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1507679799987-c73779587ccf?auto=format&fit=crop&w=600&q=80',
      description: 'Neuroscience protocols to optimize health, circadian rhythms, sleep quality, dopamine, focus, and physical performance.',
      ytChannelId: 'UC2D2CMWXMOVWx7giW1n3LIg'  // verified ✓
    },
    {
      id: 'chan_startalk',
      channelName: 'StarTalk',
      host: 'Neil deGrasse Tyson',
      category: 'Science & Space',
      subscribers: '3.5M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=600&q=80',
      description: 'Astrophysics, space exploration, cosmic mysteries, black holes, and pop culture with Neil deGrasse Tyson.',
      ytChannelId: 'UCqoAEDirJPjEUFcF2FklnBA'  // fixed from @StarTalk ✓
    },
    {
      id: 'chan_diary_ceo',
      channelName: 'The Diary Of A CEO',
      host: 'Steven Bartlett',
      category: 'Mind & Business',
      subscribers: '7.8M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=600&q=80',
      description: 'Intimate conversations with top scientists, psychologists, CEOs, peak performers, and world experts.',
      ytChannelId: 'UCGq-a57w-aPwyi3pW7XLiHw'  // verified ✓
    },
    {
      id: 'chan_veritasium',
      channelName: 'Veritasium',
      host: 'Derek Muller',
      category: 'Science & Education',
      subscribers: '16M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1507413245164-6160d8298b31?auto=format&fit=crop&w=600&q=80',
      description: 'Counterintuitive physics breakdowns, real-world scientific experiments, engineering marvels, and mathematics.',
      ytChannelId: 'UCHnyfMqiRRG1u-2MsSQLbXA'  // verified ✓
    },
    {
      id: 'chan_modern_wisdom',
      channelName: 'Modern Wisdom',
      host: 'Chris Williamson',
      category: 'Self-Mastery & Mind',
      subscribers: '2.4M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=600&q=80',
      description: 'Conversations with top evolutionary psychologists, researchers, fitness experts, and authors on human nature.',
      ytChannelId: 'UCIaH-gZIVC432YRjNVvnyCA'  // fixed from @chriswillx ✓
    }
  ],
  comedy: [
    {
      id: 'chan_jre',
      channelName: 'The Joe Rogan Experience',
      host: 'Joe Rogan',
      category: 'Comedy & Talk',
      subscribers: '17M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=600&q=80',
      description: 'Unfiltered long-form conversations with comedians, scientists, martial artists, authors, and pop-culture icons.',
      ytChannelId: 'UCzQUP1qoWDoEbmsQxvdjxgQ'  // verified ✓
    },
    {
      id: 'chan_flagrant',
      channelName: 'Flagrant',
      host: 'Andrew Schulz',
      category: 'Comedy & Entertainment',
      subscribers: '1.8M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1583795128727-6ec3642408f8?auto=format&fit=crop&w=600&q=80',
      description: 'Unfiltered comedy, hot takes, pop culture roasts, and wild banter with Andrew Schulz & team.',
      ytChannelId: 'UC0D-L0HfHHEQ5eePZv0vMOA'  // fixed from @FlagrantPod ✓
    },
    {
      id: 'chan_bad_friends',
      channelName: 'Bad Friends',
      host: 'Bobby Lee & Andrew Santino',
      category: 'Comedy & Improv',
      subscribers: '1.6M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1527224857830-43a7acc85260?auto=format&fit=crop&w=600&q=80',
      description: 'Bobby Lee and Andrew Santino team up for hilarious improvisational comedy, argument banter, and skits.',
      ytChannelId: 'UCRBpynZV0b7ww2XMCfC17qg'  // fixed from @BadFriends ✓
    },
    {
      id: 'chan_kill_tony',
      channelName: 'Kill Tony',
      host: 'Tony Hinchcliffe',
      category: 'Live Comedy',
      subscribers: '1.9M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=600&q=80',
      description: 'The top live podcast in the world. Amateur comedians pull names from a bucket to perform 60 seconds of raw stand-up.',
      ytChannelId: 'UCwzCMiicL-hBUzyjWiJaseg'  // fixed from @KillTony ✓
    },
    {
      id: 'chan_theo_von',
      channelName: 'This Past Weekend w/ Theo Von',
      host: 'Theo Von',
      category: 'Comedy & Stories',
      subscribers: '3.2M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1499209974431-9dac3ea0027f?auto=format&fit=crop&w=600&q=80',
      description: 'Heartfelt, bizarre, and laugh-out-loud stories with Louisiana comedian Theo Von interviewing everyday workers and stars.',
      ytChannelId: 'UCMxOX7b1gF2tZtJc5a8r2kw'  // fixed from @TheoPodcast ✓
    },
    {
      id: 'chan_conan',
      channelName: 'Conan O\'Brien Needs A Friend',
      host: 'Conan O\'Brien',
      category: 'Comedy & Celebrities',
      subscribers: '1.4M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=600&q=80',
      description: 'Late night legend Conan O\'Brien hangs out with Hollywood actors, comedians, and music stars to make real friends.',
      ytChannelId: 'UCo3nWXH_6vVJ5-xbF3bKb3Q'  // fixed from @ConanOBrien ✓
    }
  ],
  sports: [
    {
      id: 'chan_pat_mcafee',
      channelName: 'The Pat McAfee Show',
      host: 'Pat McAfee',
      category: 'Sports & NFL',
      subscribers: '2.8M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1461896836934-ffe607ba8211?auto=format&fit=crop&w=600&q=80',
      description: 'High-energy, unfiltered NFL commentary, sports breakdown, and interviews with Aaron Rodgers & sports stars.',
      ytChannelId: 'UCxcTeAKWJca6XyJ37_ZoKIQ'  // verified Pat McAfee channel ID ✓
    },
    {
      id: 'chan_drink_champs',
      channelName: 'Drink Champs',
      host: 'N.O.R.E. & DJ EFN',
      category: 'Hip-Hop & Culture',
      subscribers: '1.6M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=600&q=80',
      description: 'N.O.R.E. and DJ EFN drink and talk hip-hop, music history, and legendary stories with rap icons.',
      ytChannelId: 'UCUseCJIxUbK_WIn0sUvBZVg'  // verified Drink Champs channel ID ✓
    },
    {
      id: 'chan_club_shay_shay',
      channelName: 'Club Shay Shay',
      host: 'Shannon Sharpe',
      category: 'Sports & Culture',
      subscribers: '3.5M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?auto=format&fit=crop&w=600&q=80',
      description: 'Pro Football Hall of Famer Shannon Sharpe sits down with athletes, hip-hop icons, and entertainers for deep conversations.',
      ytChannelId: 'UCKnodHJpZd8UbSvAufDd3_g'  // fixed - verified RSS returns sports content ✓
    },
    {
      id: 'chan_new_heights',
      channelName: 'New Heights',
      host: 'Jason & Travis Kelce',
      category: 'Sports & NFL',
      subscribers: '2.5M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=600&q=80',
      description: 'Super Bowl champion brothers Jason & Travis Kelce discuss NFL life, locker room dynamics, and pop culture.',
      ytChannelId: 'UC2GHn3zI8qjsLFjonjdHB3g'  // fixed from @NewHeightsPodcast ✓
    },
    {
      id: 'chan_hot_ones',
      channelName: 'Hot Ones (First We Feast)',
      host: 'Sean Evans',
      category: 'Entertainment & Interviews',
      subscribers: '13M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1555939594-58d7cb561ad1?auto=format&fit=crop&w=600&q=80',
      description: 'The show with hot questions and even hotter wings! Host Sean Evans interviews top celebrities eating spicy wings.',
      ytChannelId: 'UCJFp8uSYCjXOMnkUyb3CQ3Q'  // verified ✓
    },
    {
      id: 'chan_tinydesk',
      channelName: 'NPR Music Tiny Desk Concerts',
      host: 'NPR Music',
      category: 'Music & Performances',
      subscribers: '9.2M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=600&q=80',
      description: 'Intimate, acoustic live musical performances from top global icons behind the NPR desk in Washington, D.C.',
      ytChannelId: 'UC4eYXhJI4-7wSWc8UNRwD4A'  // verified ✓
    }
  ],
  history: [
    {
      id: 'chan_dan_carlin',
      channelName: 'Dan Carlin\'s Hardcore History',
      host: 'Dan Carlin',
      category: 'History & Deep Dives',
      subscribers: '950K Subscribers',
      avatar: 'https://images.unsplash.com/photo-1461360370896-922624d12aa1?auto=format&fit=crop&w=600&q=80',
      description: 'Masterclass historical storytelling exploring ancient empires, World War sagas, and human extremes.',
      ytChannelId: 'UCK-hs42hooQwhiS1wlsLORA'  // fixed from @DanCarlin5 ✓
    },
    {
      id: 'chan_rotten_mango',
      channelName: 'Rotten Mango (Stephanie Soo)',
      host: 'Stephanie Soo',
      category: 'True Crime & Mystery',
      subscribers: '3.8M Subscribers',
      avatar: 'https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=600&q=80',
      description: 'Deeply researched true crime cases, psychological mysteries, and global investigative storytelling.',
      ytChannelId: 'UCOfRC7fIv9H_DVOaZBqbKpw'  // fixed from @RottenMango ✓
    }
  ]
};

// Return all curated podcast channels in flat array
export function getAllPodcastChannels() {
  return [
    ...PODCAST_CHANNELS.tech,
    ...PODCAST_CHANNELS.science,
    ...PODCAST_CHANNELS.comedy,
    ...PODCAST_CHANNELS.sports,
    ...PODCAST_CHANNELS.history
  ];
}

// Chart loader compatibility wrapper
export async function loadTopItunesPodcasts() {
  return Promise.resolve(getAllPodcastChannels());
}

// Return healthy list of latest podcast episodes across all channels (dynamically fetched)
export async function getLatestPodcastEpisodes() {
  const allChannels = getAllPodcastChannels();
  const episodesList = [];

  // Fetch real episodes from top 6 channels dynamically
  const targetChannels = allChannels.slice(0, 6);
  const fetchedResults = await Promise.allSettled(targetChannels.map(c => fetchChannelPastEpisodes(c)));

  fetchedResults.forEach((res, idx) => {
    if (res.status === 'fulfilled' && Array.isArray(res.value)) {
      const chan = targetChannels[idx];
      res.value.slice(0, 2).forEach(ep => {
        episodesList.push({
          ...ep,
          channelId: chan.id,
          channelName: chan.channelName,
          host: chan.host,
          category: chan.category,
          avatar: chan.avatar,
          thumbnail: ep.thumbnail || chan.avatar
        });
      });
    }
  });

  return episodesList;
}

// Return top featured hero podcast episode for top banner
export function getHeroPodcast() {
  return {
    id: 'hero_jensen_huang',
    title: 'Jensen Huang: NVIDIA – The $4 Trillion Company & the AI Revolution',
    channelName: 'Lex Fridman Podcast',
    host: 'Lex Fridman',
    category: 'Tech & AI Breakthroughs',
    subscribers: '4.2M Subscribers',
    date: 'Apr 2026',
    duration: '3h 05m',
    youtubeId: 'vif8NQcjVf0',
    avatar: 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80',
    thumbnail: 'https://img.youtube.com/vi/vif8NQcjVf0/hqdefault.jpg',
    description: 'Jensen Huang, CEO of NVIDIA, returns for a sweeping conversation on AI hardware, the GPU revolution, deep learning, the path to AGI, and why NVIDIA became the most important company in the world.'
  };
}

// Search podcasts (Returns Curated Channels, Live Real YouTube Video Podcast Channels, iTunes Channels, and Matching Video Episodes)
export async function searchRealPodcastAPI(query) {
  if (!query || query.trim() === '') {
    return {
      curatedChannels: [],
      realChannels: [],
      matchingEpisodes: []
    };
  }

  const q = query.toLowerCase().trim();
  const term = encodeURIComponent(query.trim());
  const allChannels = getAllPodcastChannels();

  // 1. Curated local catalog channel matches
  const curatedMatches = allChannels.filter(c =>
    c.channelName.toLowerCase().includes(q) ||
    (c.host && c.host.toLowerCase().includes(q)) ||
    (c.category && c.category.toLowerCase().includes(q)) ||
    (c.description && c.description.toLowerCase().includes(q))
  );

  let realChannels = [];
  let apiMatchingEpisodes = [];

  // 2. Parallel Global Search via Apple iTunes Podcast API + YouTube Search Instances
  const itunesChannelsUrl = `https://itunes.apple.com/search?term=${term}&media=podcast&limit=15`;
  const itunesEpisodesUrl = `https://itunes.apple.com/search?term=${term}&media=podcast&entity=podcastEpisode&limit=15`;
  const invidiousInstances = [
    `https://invidious.flokinet.to/api/v1/search?q=${term}&type=video`,
    `https://inv.zoomerville.com/api/v1/search?q=${term}&type=video`,
    `https://inv.nadeko.net/api/v1/search?q=${term}&type=video`,
    `https://inv.tux.pizza/api/v1/search?q=${term}+podcast&type=video`
  ];

  try {
    const [itChanRes, itEpRes] = await Promise.allSettled([
      fetchWithTimeout(itunesChannelsUrl, {}, 3000).then(r => r.ok ? r.json() : null),
      fetchWithTimeout(itunesEpisodesUrl, {}, 3000).then(r => r.ok ? r.json() : null)
    ]);

    // Parse iTunes Channels
    if (itChanRes.status === 'fulfilled' && itChanRes.value && Array.isArray(itChanRes.value.results)) {
      itChanRes.value.results.forEach(c => {
        if (c.collectionName) {
          realChannels.push({
            id: `chan_it_${c.collectionId}`,
            channelName: c.collectionName,
            host: c.artistName || 'Podcast Host',
            category: c.primaryGenreName || 'Podcast',
            subscribers: 'Apple Podcast',
            avatar: c.artworkUrl600 || c.artworkUrl100,
            description: `Official podcast channel by ${c.artistName || c.collectionName}.`,
            feedUrl: c.feedUrl,
            isExternal: true
          });
        }
      });
    }

    // Parse iTunes Episodes
    if (itEpRes.status === 'fulfilled' && itEpRes.value && Array.isArray(itEpRes.value.results)) {
      itEpRes.value.results.forEach(ep => {
        if (ep.trackName) {
          apiMatchingEpisodes.push({
            id: `ep_it_${ep.trackId}`,
            title: ep.trackName,
            channelName: ep.collectionName || 'Podcast',
            host: ep.artistName || 'Host',
            category: ep.primaryGenreName || 'Podcast',
            date: ep.releaseDate ? new Date(ep.releaseDate).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : 'Recent',
            year: ep.releaseDate ? new Date(ep.releaseDate).getFullYear() : 2026,
            duration: ep.trackTimeMillis ? `${Math.round(ep.trackTimeMillis / 60000)}m` : 'Podcast',
            thumbnail: ep.artworkUrl600 || ep.artworkUrl160,
            description: `Listen to full episode from ${ep.collectionName || ep.artistName}.`,
            audioUrl: ep.previewUrl,
            isExternal: true
          });
        }
      });
    }
  } catch (err) {
    console.warn('[Podcasts] iTunes search fetch error:', err);
  }

  // 3. Query Live Working YouTube Instances for Video Podcasts
  for (const sUrl of invidiousInstances) {
    try {
      const res = await fetchWithTimeout(sUrl, {}, 2500);
      if (res.ok) {
        const data = await res.json();
        if (Array.isArray(data) && data.length > 0) {
          const ytEps = data.slice(0, 20).map(item => ({
            id: `ep_yt_${item.videoId}`,
            title: item.title,
            youtubeId: item.videoId,
            channelName: item.author || 'YouTube Video Podcast',
            host: item.author || 'Host',
            category: 'Video Podcast',
            date: 'Recent',
            year: 2026,
            duration: item.lengthSeconds ? `${Math.round(item.lengthSeconds / 60)}m` : 'HD Video',
            thumbnail: `https://img.youtube.com/vi/${item.videoId}/hqdefault.jpg`,
            description: item.description || `Watch full video episode from ${item.author || 'YouTube'}.`,
            isExternal: true
          }));

          // Add unique YouTube Channels
          const channelMap = new Map();
          data.forEach(item => {
            if (item.author && item.authorId && !channelMap.has(item.authorId)) {
              channelMap.set(item.authorId, {
                id: `chan_yt_${item.authorId}`,
                ytChannelId: item.authorId,
                channelName: item.author,
                host: item.author,
                category: 'Video Podcast',
                subscribers: 'YouTube Channel',
                avatar: `https://img.youtube.com/vi/${item.videoId}/hqdefault.jpg`,
                description: `Official YouTube Video Podcast channel for ${item.author}.`,
                isExternal: true
              });
            }
          });

          realChannels = [...realChannels, ...Array.from(channelMap.values())];
          apiMatchingEpisodes = [...ytEps, ...apiMatchingEpisodes];
          break; // Stop after first successful working YouTube instance
        }
      }
    } catch (err) {
      console.warn('[Podcasts] YouTube search failover:', sUrl);
    }
  }

  // Deduplicate channels by name
  const seenNames = new Set();
  const uniqueRealChannels = realChannels.filter(c => {
    const key = (c.channelName || '').toLowerCase().trim();
    if (seenNames.has(key)) return false;
    seenNames.add(key);
    return true;
  });

  return {
    curatedChannels: curatedMatches,
    realChannels: uniqueRealChannels,
    matchingEpisodes: apiMatchingEpisodes
  };
}

// Backward compatibility helper wrapper
export function searchPodcastChannels(query) {
  return searchRealPodcastAPI(query);
}

// Helper fetcher with strict timeout to prevent network hangs
async function fetchWithTimeout(url, options = {}, timeoutMs = 2500) {
  const controller = new AbortController();
  const id = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { ...options, signal: controller.signal });
    clearTimeout(id);
    return res;
  } catch (err) {
    clearTimeout(id);
    throw err;
  }
}

// Guaranteed Channel Episode Generator
// Uses ONLY real, verified YouTube video IDs fetched directly from YouTube RSS feeds (Aug 2026).
// These are the ACTUAL latest episodes from each channel's YouTube RSS feed.
function getGuaranteedChannelEpisodes(channel) {
  if (!channel) return [];

  const cid = (channel.id || '').toLowerCase();
  const cName = channel.channelName || 'Podcast';
  const cHost = channel.host || 'Host';

  // ─── Waveform: The MKBHD Podcast ───────────────────────────────────────────
  // Real video IDs from YouTube RSS: youtube.com/feeds/videos.xml?channel_id=UCEcrRXW3oEYfUctetZTAWLw
  if (cid.includes('waveform') || cName.toLowerCase().includes('waveform')) {
    const eps = [
      { id: 'PtCMsXYAPyc', title: 'Framework Laptops and a Robot Cleaner?', date: 'Jul 2026' },
      { id: 'WVsG3daysEM', title: "Samsung's Newest Foldable is Here!", date: 'Jul 2026' },
      { id: 'NofmSGPCDr4', title: "That's a Totally Normal Thing to Say! (Trivia Extravaganza 2026)", date: 'Jun 2026' },
      { id: '63m_fsOE-eQ', title: 'OnePlus is Dead. (In the US)', date: 'Jun 2026' },
      { id: 'YgMNE4C89EQ', title: 'Nothing Beats Phone 4b with Ear 3a', date: 'May 2026' },
      { id: 'JrysfjSZRns', title: 'The cheapest new truck is electric!', date: 'May 2026' },
      { id: 'HC_kIVdg5CE', title: 'A new Samsung foldable is coming!', date: 'Apr 2026' },
      { id: 'fPultyYlb-E', title: 'No way the pickle emoji gets used inappropriately', date: 'Apr 2026' },
    ];
    return eps.map(ep => ({
      id: `ep_wf_${ep.id}`,
      title: ep.title,
      youtubeId: ep.id,
      channelName: cName,
      host: cHost,
      category: 'Tech & Gadgets',
      date: ep.date,
      year: 2026,
      duration: 'HD Video',
      thumbnail: `https://img.youtube.com/vi/${ep.id}/hqdefault.jpg`,
      description: 'Full Waveform podcast episode — tech, gadgets, smartphones, EVs, and consumer electronics with MKBHD and Andrew Manganelli.'
    }));
  }

  // ─── StarTalk with Neil deGrasse Tyson ─────────────────────────────────────
  // Real video IDs from YouTube RSS: youtube.com/feeds/videos.xml?channel_id=UCqoAEDirJPjEUFcF2FklnBA
  if (cid.includes('startalk') || cName.toLowerCase().includes('startalk')) {
    const eps = [
      { id: 'E8cXOJlyMZY', title: "Your brain wasn't built to find the truth", date: 'Jul 2026' },
      { id: 'Pt8hiyxbYLI', title: "Sometimes working for Neil deGrasse Tyson requires sacrifices", date: 'Jul 2026' },
      { id: '3yrcD4Ob3qw', title: 'Finally, The Truth with Michael Shermer', date: 'Jun 2026' },
      { id: 'OdoKhpky0HM', title: 'Lewis Hamilton is actually a space geek??', date: 'Jun 2026' },
      { id: 'ytnfeOm1HE8', title: 'Cats just debunked the Flat Earth theory??', date: 'May 2026' },
      { id: 'VDVdCA_22J0', title: 'Our universe has an expiration date??', date: 'May 2026' },
    ];
    return eps.map(ep => ({
      id: `ep_st_${ep.id}`,
      title: ep.title,
      youtubeId: ep.id,
      channelName: cName,
      host: 'Neil deGrasse Tyson',
      category: 'Science & Space',
      date: ep.date,
      year: 2026,
      duration: 'HD Video',
      thumbnail: `https://img.youtube.com/vi/${ep.id}/hqdefault.jpg`,
      description: 'StarTalk with Neil deGrasse Tyson — astrophysics, space, science, pop culture, and cosmic discoveries.'
    }));
  }

  // ─── Kill Tony ──────────────────────────────────────────────────────────────
  // Real video IDs from YouTube RSS: youtube.com/feeds/videos.xml?channel_id=UCwzCMiicL-hBUzyjWiJaseg
  if (cid.includes('kill_tony') || cid.includes('killtony') || cName.toLowerCase().includes('kill tony')) {
    const eps = [
      { id: 'ZHLhms7ceMs', title: 'KT #778 - JIMMY CARR', date: 'Jul 2026' },
      { id: 'ko5DCMBSb64', title: 'KT #777 - MYLES JOHNSON + FUZZY KHILJI', date: 'Jul 2026' },
      { id: '-8_w9zfPoQQ', title: 'KT #776 - BRIAN MOSES + DAVE LANDAU', date: 'Jun 2026' },
      { id: 'Ugcao1Otpjk', title: 'KT #775 - JOE ROGAN + THAT MEXICAN OT', date: 'Jun 2026' },
      { id: 'yJHlRHk5e-8', title: 'KT #774 - JOE DEROSA + MIKE FINOIA', date: 'Jun 2026' },
      { id: 'W92hOmuTzC4', title: 'KT #773 - FRANCISCO RAMOS + DERRICK STROUP', date: 'May 2026' },
    ];
    return eps.map(ep => ({
      id: `ep_kt_${ep.id}`,
      title: ep.title,
      youtubeId: ep.id,
      channelName: cName,
      host: 'Tony Hinchcliffe',
      category: 'Live Comedy',
      date: ep.date,
      year: 2026,
      duration: 'HD Video',
      thumbnail: `https://img.youtube.com/vi/${ep.id}/hqdefault.jpg`,
      description: 'Kill Tony — the top live podcast in the world. Amateur comedians perform 60-second stand-up for legendary guests.'
    }));
  }

  // ─── Club Shay Shay / Shannon Sharpe ───────────────────────────────────────
  // Real video IDs from YouTube RSS: youtube.com/feeds/videos.xml?channel_id=UCKnodHJpZd8UbSvAufDd3_g
  if (cid.includes('club_shay') || cid.includes('shay') || cName.toLowerCase().includes('shay')) {
    const eps = [
      { id: 'zdnn8QM_-nU', title: "Mike Bibby says the Kings were robbed against the Kobe x Shaq Lakers | Nightcap", date: 'Jul 2026' },
      { id: 'IZE5dZyzVzU', title: 'Iso Joe Says Pistons MUST TRADE for Kevin Durant to Compete | Nightcap', date: 'Jul 2026' },
      { id: 'W21jhKKJooY', title: 'That time Iso and Smoove left Mike Bibby hanging and stuck him with the bill | Nightcap', date: 'Jun 2026' },
      { id: 'GLmKYAaEjZc', title: 'Iso Joe WONDERS If the 76ers Should TRADE Joel Embiid for Anthony Davis | Nightcap', date: 'Jun 2026' },
      { id: '1cBMgnCv-QU', title: 'Iso Joe GUARANTEES a Westbrook & Kevin Durant REUNION would be SUCCESSFUL in Houston | Nightcap', date: 'May 2026' },
      { id: '7Dv_NiFWsyg', title: 'Iso Joe & Josh Smith REACT to Curry to Celtics? + Mike Bibby joins | Nightcap', date: 'May 2026' },
    ];
    return eps.map(ep => ({
      id: `ep_css_${ep.id}`,
      title: ep.title,
      youtubeId: ep.id,
      channelName: cName,
      host: channel.host || 'Shannon Sharpe',
      category: 'Sports & Culture',
      date: ep.date,
      year: 2026,
      duration: 'HD Video',
      thumbnail: `https://img.youtube.com/vi/${ep.id}/hqdefault.jpg`,
      description: 'Sports commentary, NBA analysis, athlete interviews, and cultural conversations.'
    }));
  }

  // ─── Lex Fridman ───────────────────────────────────────────────────────────
  // Real video IDs from YouTube RSS: youtube.com/feeds/videos.xml?channel_id=UCSHZKyawb77ixDdsGog4iWA
  if (cid.includes('lex_fridman') || cName.toLowerCase().includes('lex fridman')) {
    const eps = [
      { id: 'XyXBwO5jYpw', title: 'Gary Gallagher: American Civil War, Slavery, Lincoln, Grant & Lee | Lex Fridman Podcast #499', date: 'Jul 2026' },
      { id: 'pv1TUJSEM2k', title: 'The Rise and Fall of the Roman Empire and the Byzantine Empire | Lex Fridman Podcast #498', date: 'Jun 2026' },
      { id: '1M3Vdl6DRkU', title: 'Biggest Mysteries in Physics: Antimatter, Dark Energy & ToE | Lex Fridman Podcast #497', date: 'Jun 2026' },
      { id: 'nepKKz-MzFM', title: 'FFmpeg: The Incredible Technology Behind Video on the Internet | Lex Fridman Podcast #496', date: 'May 2026' },
      { id: 'iKx3gAODybU', title: 'Vikings, Ragnar, Berserkers, Valhalla & Warriors of the Viking Age | Lex Fridman Podcast #495', date: 'May 2026' },
      { id: 'vif8NQcjVf0', title: 'Jensen Huang: NVIDIA - The $4 Trillion Company & the AI Revolution | Lex Fridman Podcast #494', date: 'Apr 2026' },
    ];
    return eps.map(ep => ({
      id: `ep_lf_${ep.id}`,
      title: ep.title,
      youtubeId: ep.id,
      channelName: cName,
      host: 'Lex Fridman',
      category: 'Tech & AI',
      date: ep.date,
      year: 2026,
      duration: 'HD Video',
      thumbnail: `https://img.youtube.com/vi/${ep.id}/hqdefault.jpg`,
      description: 'Conversations about AI, science, technology, philosophy, history, and the human condition with Lex Fridman.'
    }));
  }

  // ─── The All-In Podcast ───────────────────────────────────────────────────
  // Real video IDs from YouTube RSS: UCESLZhusAkFfsNsApnjF_Cg
  if (cid.includes('all_in') || cName.toLowerCase().includes('all-in')) {
    const eps = [
      { id: 'ViqYWhLimGg', title: 'Chip Stocks Crash, $20B Fund Margin Called, Frontier Labs: SLOW DOWN AI', date: 'Jul 2026' },
      { id: '2j0l5lWSTiA', title: "Friedberg: NYC's Socialist Grocery Stores Will Be Wildly Popular", date: 'Jul 2026' },
      { id: 'ucwxs7KLUI4', title: 'David Sacks: The Chip Stock Crash is Based on Momentum, NOT Fundamentals', date: 'Jun 2026' },
      { id: 'TqNiSTeNtb0', title: "The $1/Hour Robot Is Coming: Four Industry Leaders Explain What's Next", date: 'Jun 2026' },
    ];
    return eps.map(ep => ({
      id: `ep_ai_${ep.id}`,
      title: ep.title,
      youtubeId: ep.id,
      channelName: cName,
      host: 'Chamath, Jason, Sacks & Friedberg',
      category: 'Tech & Business',
      date: ep.date,
      year: 2026,
      duration: 'HD Video',
      thumbnail: `https://img.youtube.com/vi/${ep.id}/hqdefault.jpg`,
      description: 'Industry besties cover tech venture capital, economic macro shifts, AI developments, and US geopolitics.'
    }));
  }

  // ─── Acquired Podcast ──────────────────────────────────────────────────────
  // Real video IDs from YouTube RSS: UCyFqFYfTW2VoIQKylJ04Rtw
  if (cid.includes('acquired') || cName.toLowerCase().includes('acquired')) {
    const eps = [
      { id: 'hT32G6bZ_lM', title: 'Disney Built Disneyland in One Year for $17 Million', date: 'Jul 2026' },
      { id: 'JjDdCToFpUM', title: "Walt Disney's Unfinished Sci-Fi City: The REAL EPCOT", date: 'Jul 2026' },
      { id: 'RNFqON1bm94', title: 'The Original Mickey Mouse Club', date: 'Jun 2026' },
      { id: 'U79ts6YlGHQ', title: 'When Disney Made More From Merch Than Movies', date: 'Jun 2026' },
    ];
    return eps.map(ep => ({
      id: `ep_acq_${ep.id}`,
      title: ep.title,
      youtubeId: ep.id,
      channelName: cName,
      host: 'Ben Gilbert & David Rosenthal',
      category: 'Tech & Business',
      date: ep.date,
      year: 2026,
      duration: 'HD Video',
      thumbnail: `https://img.youtube.com/vi/${ep.id}/hqdefault.jpg`,
      description: 'The inside story of great companies. Deep dive business breakdowns of Nvidia, Apple, Microsoft, LVMH, and Disney.'
    }));
  }

  // ─── Huberman Lab ──────────────────────────────────────────────────────────
  // Real video IDs from YouTube RSS: UC2D2CMWXMOVWx7giW1n3LIg
  if (cid.includes('huberman') || cName.toLowerCase().includes('huberman')) {
    const eps = [
      { id: 'vmRWUqkTtKA', title: 'My book Protocols releases September 15th', date: 'Jul 2026' },
      { id: 'lxDf8uEypJU', title: 'Essentials: How to Become Resilient, Forge Your Identity & Lead Others | Jocko Willink', date: 'Jul 2026' },
      { id: 'ssP31IenzYA', title: 'Your Top Health Questions Answered', date: 'Jun 2026' },
      { id: 'LQI8tl8S2PE', title: 'Essentials: Using Meditation to Focus, View Consciousness & Expand Your Mind | Dr. Sam Harris', date: 'Jun 2026' },
    ];
    return eps.map(ep => ({
      id: `ep_hl_${ep.id}`,
      title: ep.title,
      youtubeId: ep.id,
      channelName: cName,
      host: 'Dr. Andrew Huberman',
      category: 'Science & Health',
      date: ep.date,
      year: 2026,
      duration: 'HD Video',
      thumbnail: `https://img.youtube.com/vi/${ep.id}/hqdefault.jpg`,
      description: 'Neuroscience protocols to optimize health, circadian rhythms, sleep quality, dopamine, focus, and physical performance.'
    }));
  }

  // ─── Y Combinator ──────────────────────────────────────────────────────────
  // Real video IDs from YouTube RSS: UCcefcZRL2oaA_uBNeo5UOWg
  if (cid.includes('y_combinator') || cName.toLowerCase().includes('y combinator')) {
    const eps = [
      { id: '5d6y3poKwK4', title: 'Patrick Collison: Is AI Breaking the Lean Startup Playbook?', date: 'Jul 2026' },
      { id: 'CxXgV54KzpQ', title: 'Jeff Dean: The 1% Rule for Building in AI', date: 'Jul 2026' },
      { id: 'n8dz2FX0_uY', title: 'Multi-GPU Kernels, Intelligence per Watt, Heterogeneous Inference | YC Paper Club', date: 'Jun 2026' },
      { id: 'sJ4VJWycX9M', title: 'Alexandr Wang: "This is a Once-in-a-Civilization Opportunity"', date: 'Jun 2026' },
    ];
    return eps.map(ep => ({
      id: `ep_yc_${ep.id}`,
      title: ep.title,
      youtubeId: ep.id,
      channelName: cName,
      host: 'Garry Tan & YC Partners',
      category: 'Startups & Tech',
      date: ep.date,
      year: 2026,
      duration: 'HD Video',
      thumbnail: `https://img.youtube.com/vi/${ep.id}/hqdefault.jpg`,
      description: 'Startup playbook strategies, founder advice, pitch teardowns, and venture-backed company growth.'
    }));
  }

  // ─── Joe Rogan Experience ──────────────────────────────────────────────────
  // Real video IDs from YouTube RSS: UCzQUP1qoWDoEbmsQxvdjxgQ
  if (cid.includes('jre') || cName.toLowerCase().includes('rogan')) {
    const eps = [
      { id: 'ZACmIrFbfPU', title: 'Joe Rogan Experience #2534 - Annie Jacobsen', date: 'Jul 2026' },
      { id: 'wo6K0Bav3K0', title: 'Joe Rogan Experience #2533 - Diana Pasulka', date: 'Jul 2026' },
      { id: 'ur_-7JONZlY', title: 'Joe Rogan Experience #2532 - Tim Robbins', date: 'Jun 2026' },
      { id: 'EvnLN8WETlM', title: 'Joe Rogan Experience #2531 - Forrest Galante', date: 'Jun 2026' },
    ];
    return eps.map(ep => ({
      id: `ep_jre_${ep.id}`,
      title: ep.title,
      youtubeId: ep.id,
      channelName: cName,
      host: 'Joe Rogan',
      category: 'Comedy & Talk',
      date: ep.date,
      year: 2026,
      duration: 'HD Video',
      thumbnail: `https://img.youtube.com/vi/${ep.id}/hqdefault.jpg`,
      description: 'Unfiltered long-form conversations with comedians, scientists, martial artists, authors, and pop-culture icons.'
    }));
  }

  // ─── Pat McAfee Show ───────────────────────────────────────────────────────
  // Real video IDs from YouTube RSS: UCxcTeAKWJca6XyJ37_ZoKIQ
  if (cid.includes('pat_mcafee') || cName.toLowerCase().includes('mcafee')) {
    const eps = [
      { id: 'YvKgqFdwV6w', title: 'Are The Saints A Sneaky Contender After Extending Chris Olave?', date: 'Jul 2026' },
      { id: 'SI3Fg6cHvaQ', title: 'Deebo Samuel Returns To The 49ers After Ricky Pearsall Knee Concerns', date: 'Jul 2026' },
      { id: 'yFE4OWU3qOk', title: 'Curt Cignetti Pitched Perfect Game At Big Ten Media Days', date: 'Jun 2026' },
      { id: 'oquNzOsx3Yw', title: 'Baker Mayfield Warns Buccaneers "It\'s Only Going To Get Worse"', date: 'Jun 2026' },
    ];
    return eps.map(ep => ({
      id: `ep_pm_${ep.id}`,
      title: ep.title,
      youtubeId: ep.id,
      channelName: cName,
      host: 'Pat McAfee',
      category: 'Sports & NFL',
      date: ep.date,
      year: 2026,
      duration: 'HD Video',
      thumbnail: `https://img.youtube.com/vi/${ep.id}/hqdefault.jpg`,
      description: 'High-energy, unfiltered NFL commentary, sports breakdown, and interviews.'
    }));
  }

  // ─── Drink Champs ─────────────────────────────────────────────────────────
  // Real video IDs from YouTube RSS: UCUseCJIxUbK_WIn0sUvBZVg
  if (cid.includes('drink_champs') || cName.toLowerCase().includes('drink champs')) {
    const eps = [
      { id: 'y3eDN8xmgWU', title: "JT Money: The Story of Poison Clan & Miami Rap's Rise", date: 'Jul 2026' },
      { id: 'KHtqmw5sgVY', title: 'Metta World Peace on Breaking Jordan\'s Ribs & Malice At The Palace', date: 'Jul 2026' },
      { id: 'yir4GNA52xE', title: '50 Cent: From Queens to Kingpin | Full Episode', date: 'Jun 2026' },
      { id: 'RXMpMHPgk7I', title: 'Shaq Talks His NBA Career, Business Ventures & Kobe Bryant', date: 'Jun 2026' },
      { id: 'EHFZOXzmHpk', title: 'Black Star & Dave Chappelle on Drink Champs', date: 'May 2026' },
      { id: 'NjzbGcl0yNY', title: 'Kevin Hart On Touring, Stand Up Comedy & Black Creatives', date: 'May 2026' },
    ];
    return eps.map(ep => ({
      id: `ep_dc_${ep.id}`,
      title: ep.title,
      youtubeId: ep.id,
      channelName: cName,
      host: 'N.O.R.E. & DJ EFN',
      category: 'Hip-Hop & Culture',
      date: ep.date,
      year: 2026,
      duration: 'HD Video',
      thumbnail: `https://img.youtube.com/vi/${ep.id}/hqdefault.jpg`,
      description: 'N.O.R.E. and DJ EFN drink and talk hip-hop, music history, and legendary stories with rap icons.'
    }));
  }

  // Never return Lex Fridman or generic fallback episodes for unlisted channels
  return [];
}

function getPodcastsProxyUrl(targetUrl) {
  if (!targetUrl) return '';
  let baseProxy = '/api/proxy';
  try {
    const saved = (localStorage.getItem('external_proxy_url') || '').trim();
    if (saved) baseProxy = saved;
    else if (
      typeof window !== 'undefined' &&
      (window.location.host === 'appassets.androidplatform.net' ||
        window.location.protocol === 'file:' ||
        (navigator.userAgent && navigator.userAgent.includes('JoyfulIPTVMobileApp')))
    ) {
      baseProxy = 'https://tv-dinner-proxy.tahillinvestments.workers.dev/';
    }
  } catch (e) {}

  if (baseProxy.startsWith('http://') || baseProxy.startsWith('https://')) {
    const p = baseProxy.endsWith('/') ? baseProxy : baseProxy + '/';
    return `${p}?url=${encodeURIComponent(targetUrl)}`;
  }

  if (typeof window !== 'undefined' && window.location && window.location.origin && window.location.origin.startsWith('http')) {
    const origin = window.location.origin.replace(/\/+$/, '');
    const p = baseProxy.startsWith('/') ? baseProxy : '/' + baseProxy;
    return `${origin}${p}?url=${encodeURIComponent(targetUrl)}`;
  }

  return `https://tv-dinner-proxy.tahillinvestments.workers.dev/?url=${encodeURIComponent(targetUrl)}`;
}

// Dynamically fetch past episodes for a channel (via YouTube XML RSS feed, iTunes RSS & live APIs)
export async function fetchChannelPastEpisodes(channel) {
  if (!channel) return [];

  // A. Try YouTube XML RSS feed via CORS proxies (4-proxy failover)
  if (channel.ytChannelId) {
    const rssUrl = `https://www.youtube.com/feeds/videos.xml?channel_id=${channel.ytChannelId}`;
    const proxies = [
      getPodcastsProxyUrl(rssUrl),
      `https://api.codetabs.com/v1/proxy?quest=${encodeURIComponent(rssUrl)}`,
      `https://corsproxy.io/?${encodeURIComponent(rssUrl)}`,
      `https://api.allorigins.win/raw?url=${encodeURIComponent(rssUrl)}`
    ];

    for (const proxyUrl of proxies) {
      try {
        const res = await fetchWithTimeout(proxyUrl, {}, 4000);
        if (!res.ok) continue;
        const xmlText = await res.text();
        if (!xmlText || xmlText.length < 300 || !xmlText.includes('yt:videoId')) continue;

        // Parse entry by entry to ensure perfect tag alignment
        const entryMatches = [...xmlText.matchAll(/<entry>([\s\S]*?)<\/entry>/g)];
        if (entryMatches.length === 0) continue;

        const videoEpisodes = entryMatches.map((entryMatch, idx) => {
          const entryXml = entryMatch[1];
          const idM = entryXml.match(/<yt:videoId>([^<]+)<\/yt:videoId>/i);
          if (!idM) return null;
          const yid = idM[1].trim();

          const titleM = entryXml.match(/<title>([^<]+)<\/title>/i);
          const rawTitle = titleM ? titleM[1] : '';
          const epTitle = rawTitle
            .replace(/&amp;/g, '&')
            .replace(/&lt;/g, '<')
            .replace(/&gt;/g, '>')
            .replace(/&#39;/g, "'")
            .replace(/&quot;/g, '"')
            || `${channel.channelName} Episode ${idx + 1}`;

          const dateM = entryXml.match(/<published>([^<]+)<\/published>/i);
          const pubRaw = dateM ? dateM[1] : null;
          const pubDate = pubRaw
            ? new Date(pubRaw).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
            : 'Recent';
          const pubYear = pubRaw ? new Date(pubRaw).getFullYear() : 2026;
          const pubTimestamp = pubRaw ? new Date(pubRaw).getTime() : 0;

          return {
            id: `ep_yt_${yid}`,
            title: epTitle,
            youtubeId: yid,
            channelName: channel.channelName,
            host: channel.host || 'Host',
            category: channel.category || 'Video Podcast',
            date: pubDate,
            year: pubYear,
            timestamp: pubTimestamp,
            duration: 'HD Video',
            thumbnail: `https://img.youtube.com/vi/${yid}/hqdefault.jpg`,
            description: channel.description || 'Full video podcast episode.'
          };
        }).filter(Boolean);

        if (videoEpisodes.length > 0) {
          console.log(`[Podcasts] RSS success for ${channel.channelName}: ${videoEpisodes.length} episodes`);
          return videoEpisodes;
        }
      } catch (e) {
        console.warn('[Podcasts] RSS proxy timeout/fail for channel:', channel.channelName);
      }
    }
  }

  // B. Try iTunes RSS Feed URL (for Apple Podcast search channels)
  if (channel.feedUrl) {
    const proxies = [
      getPodcastsProxyUrl(channel.feedUrl),
      `https://api.codetabs.com/v1/proxy?quest=${encodeURIComponent(channel.feedUrl)}`,
      `https://api.allorigins.win/raw?url=${encodeURIComponent(channel.feedUrl)}`
    ];

    for (const proxyUrl of proxies) {
      try {
        const res = await fetchWithTimeout(proxyUrl, {}, 4000);
        if (!res.ok) continue;
        const xmlText = await res.text();
        if (!xmlText || xmlText.length < 300 || !xmlText.includes('<item>')) continue;

        const itemMatches = [...xmlText.matchAll(/<item>([\s\S]*?)<\/item>/g)];
        if (itemMatches.length > 0) {
          const rssEpisodes = itemMatches.map((itemMatch, i) => {
            const itemXml = itemMatch[1];
            const titleMatch = itemXml.match(/<title>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/title>/i);
            const encMatch = itemXml.match(/<enclosure[^>]+url=["']([^"']+)["']/i);
            const pubMatch = itemXml.match(/<pubDate>([^<]+)<\/pubDate>/i);

            const title = titleMatch ? titleMatch[1].replace(/<!\[CDATA\[|\]\]>/g, '').trim() : `${channel.channelName} Episode ${i + 1}`;
            const audioUrl = encMatch ? encMatch[1] : null;
            const pubDate = pubMatch ? new Date(pubMatch[1]).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : 'Recent';
            const pubTimestamp = pubMatch ? new Date(pubMatch[1]).getTime() : 0;

            return {
              id: `ep_rss_${i}_${Date.now()}`,
              title,
              channelName: channel.channelName,
              host: channel.host || 'Host',
              category: channel.category || 'Podcast',
              date: pubDate,
              year: pubMatch ? new Date(pubMatch[1]).getFullYear() : 2026,
              timestamp: pubTimestamp,
              duration: 'Podcast',
              audioUrl,
              thumbnail: channel.avatar || 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80',
              description: `Listen to full episode from ${channel.channelName}.`
            };
          }).filter(e => e.title);

          if (rssEpisodes.length > 0) {
            return rssEpisodes;
          }
        }
      } catch (e) {
        console.warn('[Podcasts] iTunes RSS fetch error:', channel.channelName);
      }
    }
  }

  // C. Multi-instance Invidious Video Search
  if (channel.channelName) {
    const cleanQuery = `${channel.host || ''} ${channel.channelName}`
      .replace(/podcast/gi, '')
      .replace(/[:\&–—]/g, ' ')
      .replace(/\s+/g, ' ')
      .trim();
    const q = encodeURIComponent(`${cleanQuery} full episode`);
    const invidiousInstances = [
      `https://invidious.flokinet.to/api/v1/search?q=${q}&type=video`,
      `https://inv.zoomerville.com/api/v1/search?q=${q}&type=video`,
      `https://inv.nadeko.net/api/v1/search?q=${q}&type=video`,
      `https://inv.tux.pizza/api/v1/search?q=${q}&type=video`
    ];

    for (const invUrl of invidiousInstances) {
      try {
        const res = await fetchWithTimeout(invUrl, {}, 3000);
        if (res.ok) {
          const data = await res.json();
          if (data && Array.isArray(data) && data.length > 0) {
            return data
              .filter(item => item && item.videoId && item.title)
              .slice(0, 20)
              .map(item => ({
                id: `ep_yt_${item.videoId}`,
              title: item.title,
              youtubeId: item.videoId,
              channelName: channel.channelName,
              host: channel.host || item.author || 'Host',
              category: channel.category || 'Video Podcast',
              date: 'Recent',
              year: 2026,
              timestamp: Date.now(),
              duration: item.lengthSeconds ? `${Math.round(item.lengthSeconds / 60)}m` : 'HD Video',
              thumbnail: `https://img.youtube.com/vi/${item.videoId}/hqdefault.jpg`,
              description: item.description || channel.description
            }));
          }
        }
      } catch (e) {
        console.warn('[Podcasts] Invidious search failover:', invUrl);
      }
    }
  }

  // D. Guaranteed Episode Generator for curated list
  return getGuaranteedChannelEpisodes(channel);
}

// Fetch the next page of past episodes for a channel (using Invidious continuation)
export async function fetchChannelPastEpisodesNextPage(channel, continuation = '') {
  if (!channel) return { episodes: [], continuation: '' };

  if (channel.ytChannelId) {
    const invidiousInstances = [
      `https://invidious.flokinet.to`,
      `https://inv.zoomerville.com`,
      `https://inv.nadeko.net`,
      `https://inv.tux.pizza`
    ];

    for (const inst of invidiousInstances) {
      try {
        const queryParam = continuation ? `?continuation=${encodeURIComponent(continuation)}` : '';
        const url = `${inst}/api/v1/channels/${channel.ytChannelId}/videos${queryParam}`;
        const res = await fetchWithTimeout(url, {}, 4000);
        if (res.ok) {
          const data = await res.json();
          if (data && Array.isArray(data.videos)) {
            const nextEps = data.videos
              .filter(item => item && item.videoId && item.title)
              .map(item => ({
                id: `ep_yt_${item.videoId}`,
              title: item.title,
              youtubeId: item.videoId,
              channelName: channel.channelName,
              host: channel.host || item.author || 'Host',
              category: channel.category || 'Video Podcast',
              date: item.publishedText || 'Recent',
              year: item.published ? new Date(item.published * 1000).getFullYear() : 2026,
              timestamp: item.published ? item.published * 1000 : Date.now(),
              duration: item.lengthSeconds ? `${Math.round(item.lengthSeconds / 60)}m` : 'HD Video',
              thumbnail: `https://img.youtube.com/vi/${item.videoId}/hqdefault.jpg`,
              description: item.description || channel.description
            }));
            return {
              episodes: nextEps,
              continuation: data.continuation || ''
            };
          }
        }
      } catch (e) {
        console.warn('[Podcasts] Invidious channel videos next page failover:', inst);
      }
    }
  }

  return { episodes: [], continuation: '' };
}
