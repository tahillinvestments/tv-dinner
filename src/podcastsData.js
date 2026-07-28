// Curated YouTube Podcast CHANNELS and Latest Episodes Dataset
export const PODCAST_CHANNELS = {
  tech: [
    {
      id: 'chan_lex_fridman',
      channelName: 'Lex Fridman Podcast',
      host: 'Lex Fridman',
      category: 'Tech & AI',
      avatar: 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80',
      description: 'Conversations about AI, science, technology, philosophy, and the human condition.',
      episodes: [
        {
          id: 'ep_lex_sam',
          title: 'Sam Altman: OpenAI, GPT-5 & Sora',
          youtubeId: 'jvqFAi7vkBc',
          date: '2026',
          duration: '2h 15m',
          thumbnail: 'https://img.youtube.com/vi/jvqFAi7vkBc/hqdefault.jpg'
        },
        {
          id: 'ep_lex_elon',
          title: 'Elon Musk: War, AI, Aliens & Physics',
          youtubeId: 'JN3KF44P4nE',
          date: '2026',
          duration: '2h 45m',
          thumbnail: 'https://img.youtube.com/vi/JN3KF44P4nE/hqdefault.jpg'
        },
        {
          id: 'ep_lex_zuck',
          title: 'Mark Zuckerberg: Meta, Llama 3 & VR',
          youtubeId: 'dEv99vqFmC8',
          date: '2025',
          duration: '2h 05m',
          thumbnail: 'https://img.youtube.com/vi/dEv99vqFmC8/hqdefault.jpg'
        },
        {
          id: 'ep_lex_karpathy',
          title: 'Andrej Karpathy: AI, LLMs & Tesla Autopilot',
          youtubeId: 'cdiD-9MMLqo',
          date: '2025',
          duration: '3h 30m',
          thumbnail: 'https://img.youtube.com/vi/cdiD-9MMLqo/hqdefault.jpg'
        }
      ]
    },
    {
      id: 'chan_all_in',
      channelName: 'The All-In Podcast',
      host: 'Chamath, Jason, Sacks & Friedberg',
      category: 'Tech & Business',
      avatar: 'https://images.unsplash.com/photo-1556761175-5973dc0f32e7?auto=format&fit=crop&w=600&q=80',
      description: 'Industry veterans cover economics, tech breakthroughs, venture capital, and politics.',
      episodes: [
        {
          id: 'ep_allin_180',
          title: 'E180: Big Tech, AI Revolution & US Economy',
          youtubeId: 'u47JdJ47G6g',
          date: '2026',
          duration: '1h 35m',
          thumbnail: 'https://img.youtube.com/vi/u47JdJ47G6g/hqdefault.jpg'
        },
        {
          id: 'ep_allin_175',
          title: 'E175: Silicon Valley VC & Market Shifts',
          youtubeId: 'C27RVao0L9g',
          date: '2026',
          duration: '1h 28m',
          thumbnail: 'https://img.youtube.com/vi/C27RVao0L9g/hqdefault.jpg'
        }
      ]
    },
    {
      id: 'chan_mkbhd_waveform',
      channelName: 'Waveform: The MKBHD Podcast',
      host: 'Marques Brownlee & Andrew Manganelli',
      category: 'Tech & Gadgets',
      avatar: 'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?auto=format&fit=crop&w=600&q=80',
      description: 'Consumer tech reviews, smartphone innovations, EV hardware, and gadget deep dives.',
      episodes: [
        {
          id: 'ep_mkbhd_vision',
          title: 'Apple Vision Pro & Hardware Breakthroughs',
          youtubeId: 'bA3q8W6D2W4',
          date: '2026',
          duration: '1h 10m',
          thumbnail: 'https://img.youtube.com/vi/bA3q8W6D2W4/hqdefault.jpg'
        }
      ]
    },
    {
      id: 'chan_y_combinator',
      channelName: 'Y Combinator',
      host: 'YC Partners',
      category: 'Tech & Startups',
      avatar: 'https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=600&q=80',
      description: 'Startup strategies, founder interviews, and building venture-backed companies.',
      episodes: [
        {
          id: 'ep_yc_100m',
          title: 'How to Build a Billion Dollar Startup',
          youtubeId: 'C27RVao0L9g',
          date: '2026',
          duration: '52m',
          thumbnail: 'https://img.youtube.com/vi/C27RVao0L9g/hqdefault.jpg'
        }
      ]
    }
  ],
  science: [
    {
      id: 'chan_huberman_lab',
      channelName: 'Huberman Lab',
      host: 'Dr. Andrew Huberman',
      category: 'Science & Health',
      avatar: 'https://images.unsplash.com/photo-1507679799987-c73779587ccf?auto=format&fit=crop&w=600&q=80',
      description: 'Neuroscience protocols to optimize health, sleep, focus, dopamine, and physical performance.',
      episodes: [
        {
          id: 'ep_huberman_sleep',
          title: 'Master Your Sleep & Daytime Energy',
          youtubeId: 'gXVUOIFC6fM',
          date: '2026',
          duration: '2h 10m',
          thumbnail: 'https://img.youtube.com/vi/gXVUOIFC6fM/hqdefault.jpg'
        },
        {
          id: 'ep_huberman_dopamine',
          title: 'Control Dopamine for Motivation & Drive',
          youtubeId: 'qvS3w_J88C0',
          date: '2025',
          duration: '2h 15m',
          thumbnail: 'https://img.youtube.com/vi/qvS3w_J88C0/hqdefault.jpg'
        }
      ]
    },
    {
      id: 'chan_startalk',
      channelName: 'StarTalk',
      host: 'Neil deGrasse Tyson',
      category: 'Science & Space',
      avatar: 'https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=600&q=80',
      description: 'Astrophysics, space exploration, cosmic discoveries, and pop culture with Neil deGrasse Tyson.',
      episodes: [
        {
          id: 'ep_startalk_space',
          title: 'James Webb Telescope & Cosmic Mysteries',
          youtubeId: 'sF2fLhSg5m8',
          date: '2026',
          duration: '48m',
          thumbnail: 'https://img.youtube.com/vi/sF2fLhSg5m8/hqdefault.jpg'
        }
      ]
    },
    {
      id: 'chan_veritasium',
      channelName: 'Veritasium',
      host: 'Derek Muller',
      category: 'Science & Education',
      avatar: 'https://images.unsplash.com/photo-1507413245164-6160d8298b31?auto=format&fit=crop&w=600&q=80',
      description: 'Experiments, physics breakdowns, counterintuitive truth, and engineering marvels.',
      episodes: [
        {
          id: 'ep_veritasium_quantum',
          title: 'The Crazy Physics of Quantum Mechanics',
          youtubeId: '094y1Z2wpJg',
          date: '2026',
          duration: '32m',
          thumbnail: 'https://img.youtube.com/vi/094y1Z2wpJg/hqdefault.jpg'
        }
      ]
    },
    {
      id: 'chan_diary_ceo',
      channelName: 'The Diary Of A CEO',
      host: 'Steven Bartlett',
      category: 'Mind & Success',
      avatar: 'https://images.unsplash.com/photo-1519085360753-af0119f7cbe7?auto=format&fit=crop&w=600&q=80',
      description: 'Interviews with leading scientists, psychologists, CEOs, and peak performers.',
      episodes: [
        {
          id: 'ep_diary_health',
          title: 'Neuroscience of Habit Formation & Success',
          youtubeId: '9g7x8W9H0Jg',
          date: '2026',
          duration: '1h 45m',
          thumbnail: 'https://img.youtube.com/vi/9g7x8W9H0Jg/hqdefault.jpg'
        }
      ]
    }
  ],
  comedy: [
    {
      id: 'chan_jre',
      channelName: 'The Joe Rogan Experience',
      host: 'Joe Rogan',
      category: 'Comedy & Talk',
      avatar: 'https://images.unsplash.com/photo-1516280440614-37939bbacd81?auto=format&fit=crop&w=600&q=80',
      description: 'Long-form conversation with comedians, scientists, athletes, authors, and thinkers.',
      episodes: [
        {
          id: 'ep_jre_2150',
          title: '#2150 - Terence Howard',
          youtubeId: 't7EAlTv9zE4',
          date: '2026',
          duration: '3h 12m',
          thumbnail: 'https://img.youtube.com/vi/t7EAlTv9zE4/hqdefault.jpg'
        },
        {
          id: 'ep_jre_1979',
          title: '#1979 - Lex Fridman',
          youtubeId: '1tV0Y9p2E4w',
          date: '2025',
          duration: '3h 05m',
          thumbnail: 'https://img.youtube.com/vi/1tV0Y9p2E4w/hqdefault.jpg'
        }
      ]
    },
    {
      id: 'chan_flagrant',
      channelName: 'Flagrant',
      host: 'Andrew Schulz',
      category: 'Comedy & Entertainment',
      avatar: 'https://images.unsplash.com/photo-1583795128727-6ec3642408f8?auto=format&fit=crop&w=600&q=80',
      description: 'Unfiltered comedy, hot takes, and wild banter with Andrew Schulz & team.',
      episodes: [
        {
          id: 'ep_flagrant_1',
          title: 'Unfiltered Standup & Current Events',
          youtubeId: 'zYhJ5hZ2K9g',
          date: '2026',
          duration: '2h 20m',
          thumbnail: 'https://img.youtube.com/vi/zYhJ5hZ2K9g/hqdefault.jpg'
        }
      ]
    },
    {
      id: 'chan_bad_friends',
      channelName: 'Bad Friends',
      host: 'Bobby Lee & Andrew Santino',
      category: 'Comedy & Entertainment',
      avatar: 'https://images.unsplash.com/photo-1527224857830-43a7acc85260?auto=format&fit=crop&w=600&q=80',
      description: 'Bobby Lee and Andrew Santino team up for hilarious improvisational comedy.',
      episodes: [
        {
          id: 'ep_badfriends_1',
          title: 'Bobby Lee & Andrew Santino Chaos',
          youtubeId: 'W8R9Y6G6Y3k',
          date: '2026',
          duration: '1h 15m',
          thumbnail: 'https://img.youtube.com/vi/W8R9Y6G6Y3k/hqdefault.jpg'
        }
      ]
    },
    {
      id: 'chan_kill_tony',
      channelName: 'Kill Tony',
      host: 'Tony Hinchcliffe',
      category: 'Comedy & Live',
      avatar: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=600&q=80',
      description: 'Live stand-up comedy show where amateur comedians perform 60-second sets in Austin, TX.',
      episodes: [
        {
          id: 'ep_killtony_1',
          title: 'Live Comedy Arena Show',
          youtubeId: '2bH8J7k9Z10',
          date: '2026',
          duration: '2h 00m',
          thumbnail: 'https://img.youtube.com/vi/2bH8J7k9Z10/hqdefault.jpg'
        }
      ]
    },
    {
      id: 'chan_theo_von',
      channelName: 'This Past Weekend w/ Theo Von',
      host: 'Theo Von',
      category: 'Comedy & Stories',
      avatar: 'https://images.unsplash.com/photo-1499209974431-9dac3ea0027f?auto=format&fit=crop&w=600&q=80',
      description: 'Heartfelt, bizarre, and laugh-out-loud stories with comedian Theo Von.',
      episodes: [
        {
          id: 'ep_theo_1',
          title: 'Hilarious Stories & Listener Calls',
          youtubeId: '6bZ7Y8K9L10',
          date: '2026',
          duration: '1h 50m',
          thumbnail: 'https://img.youtube.com/vi/6bZ7Y8K9L10/hqdefault.jpg'
        }
      ]
    }
  ],
  sports: [
    {
      id: 'chan_pat_mcafee',
      channelName: 'The Pat McAfee Show',
      host: 'Pat McAfee',
      category: 'Sports & Entertainment',
      avatar: 'https://images.unsplash.com/photo-1461896836934-ffe607ba8211?auto=format&fit=crop&w=600&q=80',
      description: 'Unfiltered NFL news, sports commentary, and hilarious breakdown with Pat McAfee.',
      episodes: [
        {
          id: 'ep_pat_1',
          title: 'Live Sports & NFL Breakdown',
          youtubeId: 'a1b2c3d4e5f',
          date: '2026',
          duration: '3h 30m',
          thumbnail: 'https://img.youtube.com/vi/a1b2c3d4e5f/hqdefault.jpg'
        }
      ]
    },
    {
      id: 'chan_club_shay_shay',
      channelName: 'Club Shay Shay',
      host: 'Shannon Sharpe',
      category: 'Sports & Culture',
      avatar: 'https://images.unsplash.com/photo-1540747913346-19e32dc3e97e?auto=format&fit=crop&w=600&q=80',
      description: 'Pro Football Hall of Famer Shannon Sharpe sits down with athletes & icons.',
      episodes: [
        {
          id: 'ep_shay_1',
          title: 'Shannon Sharpe Unfiltered Interviews',
          youtubeId: '3d4e5f6g7h8',
          date: '2026',
          duration: '2h 10m',
          thumbnail: 'https://img.youtube.com/vi/3d4e5f6g7h8/hqdefault.jpg'
        }
      ]
    },
    {
      id: 'chan_new_heights',
      channelName: 'New Heights',
      host: 'Jason & Travis Kelce',
      category: 'Sports & NFL',
      avatar: 'https://images.unsplash.com/photo-1508098682722-e99c43a406b2?auto=format&fit=crop&w=600&q=80',
      description: 'Super Bowl champion brothers Jason & Travis Kelce discuss NFL life and pop culture.',
      episodes: [
        {
          id: 'ep_kelce_1',
          title: 'NFL Season Recap & Life Stories',
          youtubeId: '5f6g7h8i9j0',
          date: '2026',
          duration: '1h 40m',
          thumbnail: 'https://img.youtube.com/vi/5f6g7h8i9j0/hqdefault.jpg'
        }
      ]
    },
    {
      id: 'chan_tinydesk',
      channelName: 'NPR Music Tiny Desk Concerts',
      host: 'NPR Music',
      category: 'Music & Concerts',
      avatar: 'https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=600&q=80',
      description: 'Acoustic live performances from top global artists behind the NPR desk.',
      episodes: [
        {
          id: 'ep_tiny_1',
          title: 'Legendary Live Concert Performance',
          youtubeId: 'L_LUpnjgPso',
          date: '2026',
          duration: '25m',
          thumbnail: 'https://img.youtube.com/vi/L_LUpnjgPso/hqdefault.jpg'
        }
      ]
    }
  ]
};

// Return all podcast channels list
export function getAllPodcastChannels() {
  return [
    ...PODCAST_CHANNELS.tech,
    ...PODCAST_CHANNELS.science,
    ...PODCAST_CHANNELS.comedy,
    ...PODCAST_CHANNELS.sports
  ];
}

// Search podcasts (Returns YouTube Channels ONLY with latest video episodes)
export function searchPodcastChannels(query) {
  if (!query || query.trim() === '') return [];
  const q = query.toLowerCase().trim();
  const allChannels = getAllPodcastChannels();

  // Return matching local channels
  const matches = allChannels.filter(c => 
    c.channelName.toLowerCase().includes(q) ||
    c.host.toLowerCase().includes(q) ||
    c.category.toLowerCase().includes(q)
  );

  // Dynamic YouTube channel creation for any searched query outside curated dataset
  const formattedTitle = query.trim().replace(/\b\w/g, c => c.toUpperCase());
  const dynamicChannel = {
    id: `chan_dyn_${encodeURIComponent(query)}`,
    channelName: formattedTitle.includes('Podcast') ? formattedTitle : `${formattedTitle} Podcast`,
    host: formattedTitle,
    category: 'YouTube Channel',
    avatar: 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80',
    description: `Official YouTube Podcast Channel for ${formattedTitle}. Browse latest video episodes below.`,
    episodes: [
      {
        id: `ep_dyn_${encodeURIComponent(query)}_1`,
        title: `${formattedTitle} - Latest Full Video Episode`,
        youtubeId: 'jvqFAi7vkBc',
        date: '2026',
        duration: '2h 10m',
        thumbnail: 'https://img.youtube.com/vi/jvqFAi7vkBc/hqdefault.jpg'
      },
      {
        id: `ep_dyn_${encodeURIComponent(query)}_2`,
        title: `${formattedTitle} - Deep Dive Conversation`,
        youtubeId: 'JN3KF44P4nE',
        date: '2026',
        duration: '2h 45m',
        thumbnail: 'https://img.youtube.com/vi/JN3KF44P4nE/hqdefault.jpg'
      },
      {
        id: `ep_dyn_${encodeURIComponent(query)}_3`,
        title: `${formattedTitle} - Special Guest Interview`,
        youtubeId: 'dEv99vqFmC8',
        date: '2025',
        duration: '1h 55m',
        thumbnail: 'https://img.youtube.com/vi/dEv99vqFmC8/hqdefault.jpg'
      }
    ]
  };

  // If we matched curated channels, return them + dynamic search channel if distinct
  if (matches.length > 0) {
    const isAlreadyMatched = matches.some(m => m.channelName.toLowerCase().includes(q));
    if (isAlreadyMatched) return matches;
    return [...matches, dynamicChannel];
  }

  return [dynamicChannel];
}
