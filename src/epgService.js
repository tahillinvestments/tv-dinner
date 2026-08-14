/**
 * EPG Service for Live TV Programming Information
 * Handles fetching server-provided EPG data (Xtream API) and procedural EPG generation
 * for fallback coverage across all channels.
 */

// Cache for channel EPG data
const epgCache = new Map();

/**
 * Formats a timestamp or date string into a 12-hour formatted time (e.g., "8:30 PM")
 * @param {number|string|Date} val 
 * @returns {string}
 */
export function formatTime(val) {
  if (!val) return '';
  const date = new Date(val);
  if (isNaN(date.getTime())) return '';
  return date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

/**
 * Computes live progress metrics for a given program
 * @param {Object} program { startTime, endTime, title, description, nextProgram }
 * @returns {Object} { percent, remainingMinutes, formattedStart, formattedEnd, isLive }
 */
export function getProgramProgress(program) {
  if (!program || !program.startTime || !program.endTime) {
    return { percent: 0, remainingMinutes: 0, formattedStart: '', formattedEnd: '', isLive: false };
  }

  const now = Date.now();
  const start = new Date(program.startTime).getTime();
  const end = new Date(program.endTime).getTime();

  if (isNaN(start) || isNaN(end) || end <= start) {
    return { percent: 0, remainingMinutes: 0, formattedStart: '', formattedEnd: '', isLive: false };
  }

  const total = end - start;
  const elapsed = Math.max(0, now - start);
  const remaining = Math.max(0, end - now);

  const percent = Math.min(100, Math.max(0, Math.round((elapsed / total) * 100)));
  const remainingMinutes = Math.ceil(remaining / (1000 * 60));

  return {
    percent,
    remainingMinutes,
    formattedStart: formatTime(start),
    formattedEnd: formatTime(end),
    isLive: now >= start && now <= end
  };
}

/**
 * Fetches EPG for a specific stream from Xtream Codes API if credentials available
 */
export async function fetchXtreamEPG(portalUrl, username, password, streamId) {
  if (!portalUrl || !username || !password || !streamId) return null;

  const cacheKey = `${username}_${streamId}`;
  if (epgCache.has(cacheKey)) {
    const cached = epgCache.get(cacheKey);
    // Cache valid for 10 minutes
    if (Date.now() - cached.timestamp < 10 * 60 * 1000) {
      return cached.data;
    }
  }

  try {
    const cleanPortal = portalUrl.trim().replace(/\/+$/, '');
    const epgUrl = `${cleanPortal}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&action=get_short_epg&stream_id=${encodeURIComponent(streamId)}&limit=6`;
    
    // Use window.getProxyUrl if available in app scope, else fallback to direct fetch
    const fetchUrl = (typeof window !== 'undefined' && window.getProxyUrl) ? window.getProxyUrl(epgUrl) : epgUrl;
    const res = await fetch(fetchUrl, { signal: AbortSignal.timeout(8000) });
    
    if (res.ok) {
      const json = await res.json();
      if (json && Array.isArray(json.epg_listings) && json.epg_listings.length > 0) {
        const listings = json.epg_listings;
        const now = Date.now();

        // Find current listing
        let currentIdx = listings.findIndex(item => {
          const s = item.start_timestamp ? item.start_timestamp * 1000 : new Date(item.start).getTime();
          const e = item.stop_timestamp ? item.stop_timestamp * 1000 : new Date(item.end).getTime();
          return now >= s && now <= e;
        });

        if (currentIdx === -1) currentIdx = 0;

        const cur = listings[currentIdx];
        const next = listings[currentIdx + 1] || null;

        const data = {
          title: decodeBase64IfNeeded(cur.title || 'Live Broadcast'),
          description: decodeBase64IfNeeded(cur.description || 'Live programming stream.'),
          startTime: cur.start_timestamp ? cur.start_timestamp * 1000 : cur.start,
          endTime: cur.stop_timestamp ? cur.stop_timestamp * 1000 : cur.end,
          nextTitle: next ? decodeBase64IfNeeded(next.title) : null,
          nextStartTime: next ? (next.start_timestamp ? next.start_timestamp * 1000 : next.start) : null,
          isServerEPG: true
        };

        epgCache.set(cacheKey, { timestamp: Date.now(), data });
        return data;
      }
    }
  } catch (e) {
    console.debug(`[EPG] Server EPG fetch skipped for stream ${streamId}:`, e.message);
  }

  return null;
}

function decodeBase64IfNeeded(str) {
  if (!str) return '';
  try {
    if (/^[A-Za-z0-9+/=]+$/.test(str) && str.length % 4 === 0 && str.length > 12) {
      return decodeURIComponent(escape(atob(str)));
    }
  } catch (e) {}
  return str;
}

/**
 * Smart Procedural EPG Schedule Generator
 * Dynamically computes a realistic schedule for channels when server EPG is not provided.
 * @param {Object} channel { name, group, id }
 * @returns {Object} { title, description, startTime, endTime, nextTitle, nextStartTime, isServerEPG }
 */
export function getChannelEPGInfo(channel) {
  if (!channel) return getFallbackProgram('Live TV');

  const now = new Date();
  const currentHour = now.getHours();
  const name = (channel.name || '').toUpperCase();
  const group = (channel.group || '').toUpperCase();

  // Pick program template set based on category & name keywords
  let scheduleTemplates = ENTERTAINMENT_SCHEDULE;

  if (name.includes('ESPN') || name.includes('SPORT') || name.includes('NFL') || name.includes('NBA') || name.includes('MLB') || name.includes('UFC') || group.includes('SPORT')) {
    scheduleTemplates = SPORTS_SCHEDULE;
  } else if (name.includes('CNN') || name.includes('FOX') || name.includes('MSNBC') || name.includes('NEWS') || name.includes('BBC') || name.includes('CNBC') || group.includes('NEWS')) {
    scheduleTemplates = NEWS_SCHEDULE;
  } else if (name.includes('HBO') || name.includes('CINEMAX') || name.includes('SHOWTIME') || name.includes('MOVIE') || name.includes('FILM') || group.includes('MOVIE')) {
    scheduleTemplates = MOVIES_SCHEDULE;
  } else if (name.includes('DISNEY') || name.includes('NICK') || name.includes('CARTOON') || name.includes('KIDS') || name.includes('ANIME') || group.includes('KIDS')) {
    scheduleTemplates = KIDS_SCHEDULE;
  } else if (name.includes('DISCOVERY') || name.includes('NAT GEO') || name.includes('HISTORY') || name.includes('DOC') || group.includes('DOCUMENTARY')) {
    scheduleTemplates = DOCS_SCHEDULE;
  }

  // Seed deterministic index using channel name hash + current hour block
  const hash = hashString(channel.name || channel.id || 'tv');
  // 1-hour program blocks
  const slotIndex = (currentHour + hash) % scheduleTemplates.length;
  const nextSlotIndex = (slotIndex + 1) % scheduleTemplates.length;

  const curProg = scheduleTemplates[slotIndex];
  const nextProg = scheduleTemplates[nextSlotIndex];

  // Calculate start of current hour block and end of hour block
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), currentHour, 0, 0, 0);
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), currentHour + 1, 0, 0, 0);
  const nextEnd = new Date(now.getFullYear(), now.getMonth(), now.getDate(), currentHour + 2, 0, 0, 0);

  return {
    title: curProg.title,
    description: curProg.description,
    startTime: start.getTime(),
    endTime: end.getTime(),
    nextTitle: nextProg.title,
    nextStartTime: end.getTime(),
    isServerEPG: false
  };
}

function hashString(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0;
  }
  return Math.abs(hash);
}

function getFallbackProgram(groupName) {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), now.getHours(), 0, 0, 0);
  const end = new Date(now.getFullYear(), now.getMonth(), now.getDate(), now.getHours() + 1, 0, 0, 0);
  return {
    title: `${groupName} Special Broadcast`,
    description: 'Live continuous programming stream.',
    startTime: start.getTime(),
    endTime: end.getTime(),
    nextTitle: 'Upcoming Programming',
    nextStartTime: end.getTime(),
    isServerEPG: false
  };
}

// Preset Category Schedule Lists
const SPORTS_SCHEDULE = [
  { title: 'Live Sports Center & Game Highlights', description: 'Comprehensive coverage of top games, breaking sports news, expert analysis, and key match plays.' },
  { title: 'Championship Live Matchday', description: 'Live coverage of match play with play-by-play commentary and field breakdown.' },
  { title: 'Prime Time Sports Debate Live', description: 'Hot takes, deep analysis, and spirited debates on today\'s big games and player moves.' },
  { title: 'Inside the League: Special Report', description: 'Exclusive interviews, post-game reaction, and tactical breakdown from top coaches.' }
];

const NEWS_SCHEDULE = [
  { title: 'Morning News Live & Market Update', description: 'Top headlines from around the globe, financial markets update, and local weather outlook.' },
  { title: 'Prime World News Hour', description: 'In-depth reporting on global breaking news, political developments, and world affairs.' },
  { title: 'The Evening Press & Live Discussion', description: 'Panel of experts break down major stories, investigative reports, and political analysis.' },
  { title: 'Nightline Global Wrap-Up', description: 'Late-night roundup of key international events, economy updates, and tomorrow\'s headlines.' }
];

const MOVIES_SCHEDULE = [
  { title: 'Hollywood Blockbuster Presentation', description: 'Featured cinema presentation starring award-winning cast members in high definition.' },
  { title: 'Action Cinema Special', description: 'High-octane action-thriller feature film packed with stunts and suspense.' },
  { title: 'Prime Time Feature Film', description: 'Critically acclaimed drama feature presentation brought to you uninterrupted.' },
  { title: 'Late Night Sci-Fi Thriller', description: 'Futuristic sci-fi thriller exploring uncharted dimensions and intense action.' }
];

const ENTERTAINMENT_SCHEDULE = [
  { title: 'Prime Time Entertainment Weekly', description: 'Celebrity news, red carpet interviews, music releases, and behind-the-scenes previews.' },
  { title: 'Hit Comedy Series Marathon', description: 'Back-to-back hilarious episodes of top-rated comedy series.' },
  { title: 'Reality TV Live Edition', description: 'Dramatic twists and competition unspool as contestants battle for the championship.' },
  { title: 'Late Night Talk & Live Performances', description: 'Celebrity guests, hilarious comedy monologues, and exclusive musical guest performances.' }
];

const KIDS_SCHEDULE = [
  { title: 'Morning Cartoon Adventures', description: 'Fun, energetic animated series featuring fan-favorite characters and heroic adventures.' },
  { title: 'Animated Feature Special', description: 'Full-length animated movie adventure suitable for the entire family.' },
  { title: 'Afternoon Kids Zone & Toons', description: 'Non-stop laughs, animated shorts, and exciting magical stories.' },
  { title: 'Family Movie Night', description: 'Heartwarming family adventure filled with laughter and memorable moments.' }
];

const DOCS_SCHEDULE = [
  { title: 'Wonders of Planet Earth', description: 'Breathtaking wildlife photography and deep exploration of nature\'s rarest ecosystems.' },
  { title: 'Ancient Civilizations Uncovered', description: 'Historians examine archaeological discoveries and mysteries of ancient empires.' },
  { title: 'Frontiers of Space & Universe', description: 'Journey into deep space with astrophysicists examining galaxies and cosmic phenomena.' },
  { title: 'Science & Technology Tomorrow', description: 'Exploring breakthroughs in AI, robotics, renewable energy, and future innovations.' }
];
