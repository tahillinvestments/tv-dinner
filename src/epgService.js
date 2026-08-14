/**
 * EPG Service for Live TV Programming Information
 * ─────────────────────────────────────────────────
 * Strategy (in order of preference):
 *  1. Bulk XMLTV feed from the Xtream portal  → real titles + precise timestamps
 *  2. Per-stream get_short_epg API call       → used on player open
 *  3. Network-keyed procedural schedule       → real show names mapped to channel
 */

// ─── In-memory state ─────────────────────────────────────────────────────────
// Maps epg_channel_id (string) → [ { title, desc, start (ms), stop (ms) } ]
const xmltvEpgTable = new Map();
let xmltvLoaded = false;
let xmltvLoadPromise = null;

// Per-stream short EPG cache
const shortEpgCache = new Map();

// ─── Public: Boot the XMLTV feed ─────────────────────────────────────────────
/**
 * Call once after channels load.  Fetches /xmltv.php and populates xmltvEpgTable.
 */
export async function initEPGFeed(portalUrl, username, password) {
  if (xmltvLoaded || xmltvLoadPromise) return xmltvLoadPromise;

  xmltvLoadPromise = _loadXmltvFeed(portalUrl, username, password)
    .then(() => { xmltvLoaded = true; })
    .catch((e) => { console.warn('[EPG] XMLTV feed failed:', e.message); });

  return xmltvLoadPromise;
}

async function _loadXmltvFeed(portalUrl, username, password) {
  if (!portalUrl || !username || !password) return;

  const cacheKey = `epg_xmltv_${username}`;
  // Try sessionStorage first (valid 30 min)
  try {
    const raw = sessionStorage.getItem(cacheKey);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Date.now() - parsed.ts < 30 * 60 * 1000) {
        _populateTableFromSerialized(parsed.data);
        console.log('[EPG] Loaded XMLTV from sessionStorage cache');
        return;
      }
    }
  } catch (_) {}

  const cleanPortal = portalUrl.trim().replace(/\/+$/, '');
  const xmlUrl = `${cleanPortal}/xmltv.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`;
  const proxied = _buildProxyUrl(xmlUrl);

  console.log('[EPG] Fetching XMLTV feed…');
  const res = await fetch(proxied, { signal: AbortSignal.timeout(20000) });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);

  const text = await res.text();
  if (!text.includes('<tv') && !text.includes('</programme>')) {
    throw new Error('Response does not look like XMLTV');
  }

  _parseXmltvText(text);

  // Serialize into sessionStorage
  try {
    const data = {};
    xmltvEpgTable.forEach((v, k) => { data[k] = v; });
    sessionStorage.setItem(cacheKey, JSON.stringify({ ts: Date.now(), data }));
  } catch (_) {}

  console.log(`[EPG] XMLTV loaded — ${xmltvEpgTable.size} channels indexed`);
}

function _populateTableFromSerialized(data) {
  Object.entries(data).forEach(([k, v]) => xmltvEpgTable.set(k, v));
}

function _buildProxyUrl(url) {
  // Mirror the same proxy logic used in main.js without importing it
  const external = (() => {
    try { return localStorage.getItem('external_proxy_url'); } catch (_) { return null; }
  })();
  const base = external || '/api/proxy';
  const p = base.endsWith('/') ? base : base + '/';
  if (base.startsWith('http://') || base.startsWith('https://')) {
    return `${p}?url=${encodeURIComponent(url)}`;
  }
  return `${base}?url=${encodeURIComponent(url)}`;
}

// ─── XMLTV Parser ─────────────────────────────────────────────────────────────
function _parseXmltvText(xml) {
  // Use DOMParser when available (browser context)
  if (typeof DOMParser !== 'undefined') {
    try {
      const doc = new DOMParser().parseFromString(xml, 'text/xml');
      const programmes = doc.querySelectorAll('programme');
      programmes.forEach(prog => {
        const chanId = prog.getAttribute('channel');
        if (!chanId) return;

        const startMs = _xmltvDateToMs(prog.getAttribute('start'));
        const stopMs  = _xmltvDateToMs(prog.getAttribute('stop'));
        if (!startMs || !stopMs) return;

        const titleEl = prog.querySelector('title');
        const descEl  = prog.querySelector('desc');
        const title   = titleEl ? titleEl.textContent.trim() : '';
        const desc    = descEl  ? descEl.textContent.trim()  : '';
        if (!title) return;

        if (!xmltvEpgTable.has(chanId)) xmltvEpgTable.set(chanId, []);
        xmltvEpgTable.get(chanId).push({ title, desc, start: startMs, stop: stopMs });
      });
      return;
    } catch (e) {
      console.warn('[EPG] DOMParser failed, using regex fallback:', e.message);
    }
  }

  // Regex fallback
  const progRe = /<programme\s([^>]*)>([\s\S]*?)<\/programme>/gi;
  let m;
  while ((m = progRe.exec(xml)) !== null) {
    const attrs  = m[1];
    const body   = m[2];
    const chanId = (attrs.match(/channel="([^"]*)"/) || [])[1];
    if (!chanId) continue;
    const start = (attrs.match(/start="([^"]*)"/) || [])[1];
    const stop  = (attrs.match(/stop="([^"]*)"/)  || [])[1];
    const startMs = _xmltvDateToMs(start);
    const stopMs  = _xmltvDateToMs(stop);
    if (!startMs || !stopMs) continue;
    const titleM = body.match(/<title[^>]*>([^<]*)<\/title>/i);
    const descM  = body.match(/<desc[^>]*>([\s\S]*?)<\/desc>/i);
    const title  = titleM ? titleM[1].trim() : '';
    const desc   = descM  ? descM[1].trim()  : '';
    if (!title) continue;
    if (!xmltvEpgTable.has(chanId)) xmltvEpgTable.set(chanId, []);
    xmltvEpgTable.get(chanId).push({ title, desc, start: startMs, stop: stopMs });
  }
}

/**
 * Parse XMLTV datetime strings like "20260813220000 +0000" or "20260813220000 -0400"
 */
function _xmltvDateToMs(str) {
  if (!str) return null;
  // Format: YYYYMMDDHHmmss +HHMM  (space + tz offset is optional)
  const m = str.trim().match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})\s*([+-]\d{4})?/);
  if (!m) return null;
  const [, yr, mo, dy, hr, mn, sc, tz] = m;
  // Build ISO string
  const tzStr = tz ? `${tz.slice(0, 3)}:${tz.slice(3)}` : '+00:00';
  const iso = `${yr}-${mo}-${dy}T${hr}:${mn}:${sc}${tzStr}`;
  const ms = Date.parse(iso);
  return isNaN(ms) ? null : ms;
}

// ─── Public: Get channel program info ─────────────────────────────────────────
/**
 * Returns { title, description, startTime, endTime, nextTitle, nextStartTime, isServerEPG }
 * Looks up the XMLTV table first, falls back to per-channel known schedule, then procedural.
 */
export function getChannelEPGInfo(channel) {
  if (!channel) return _proceduralFallback(channel);

  // 1. Try XMLTV table
  const epgId = channel.epg_channel_id || channel.id || '';
  if (epgId && xmltvEpgTable.has(epgId)) {
    const prog = _findCurrentProgram(xmltvEpgTable.get(epgId));
    if (prog) return prog;
  }

  // 2. Try matching by channel name substring across all XMLTV keys
  if (xmltvLoaded && xmltvEpgTable.size > 0) {
    const nameUpper = (channel.name || '').toUpperCase().replace(/\s+/g, '');
    for (const [key, listings] of xmltvEpgTable) {
      const keyUpper = key.toUpperCase().replace(/[\s._-]/g, '');
      if (keyUpper.includes(nameUpper) || nameUpper.includes(keyUpper.slice(0, 6))) {
        const prog = _findCurrentProgram(listings);
        if (prog) return prog;
      }
    }
  }

  // 3. Known-network schedule table
  const known = _getKnownNetworkSchedule(channel);
  if (known) return known;

  // 4. Procedural fallback
  return _proceduralFallback(channel);
}

function _findCurrentProgram(listings) {
  if (!listings || !listings.length) return null;
  const now = Date.now();
  // Sort by start just in case
  const sorted = [...listings].sort((a, b) => a.start - b.start);
  const idx = sorted.findIndex(p => now >= p.start && now < p.stop);
  if (idx === -1) return null;

  const cur  = sorted[idx];
  const next = sorted[idx + 1] || null;
  return {
    title:         cur.title,
    description:   cur.desc || 'Tune in now for live programming.',
    startTime:     cur.start,
    endTime:       cur.stop,
    nextTitle:     next ? next.title : null,
    nextStartTime: next ? next.start : null,
    isServerEPG:   true
  };
}

// ─── Public: Fetch Short EPG for player (per-stream) ─────────────────────────
export async function fetchXtreamEPG(portalUrl, username, password, streamId) {
  if (!portalUrl || !username || !password || !streamId) return null;

  const cacheKey = `${username}_${streamId}`;
  const cached = shortEpgCache.get(cacheKey);
  if (cached && Date.now() - cached.ts < 10 * 60 * 1000) return cached.data;

  try {
    const cleanPortal = portalUrl.trim().replace(/\/+$/, '');
    const epgUrl = `${cleanPortal}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&action=get_short_epg&stream_id=${encodeURIComponent(streamId)}&limit=8`;
    const res = await fetch(_buildProxyUrl(epgUrl), { signal: AbortSignal.timeout(8000) });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);

    const json = await res.json();
    if (json && Array.isArray(json.epg_listings) && json.epg_listings.length > 0) {
      const listings = json.epg_listings;
      const now = Date.now();

      let idx = listings.findIndex(item => {
        const s = item.start_timestamp ? item.start_timestamp * 1000 : Date.parse(item.start);
        const e = item.stop_timestamp  ? item.stop_timestamp  * 1000 : Date.parse(item.end);
        return now >= s && now <= e;
      });
      if (idx === -1) idx = 0;

      const cur  = listings[idx];
      const next = listings[idx + 1] || null;

      const data = {
        title:         _decodeB64(cur.title || '') || 'Live Broadcast',
        description:   _decodeB64(cur.description || '') || 'Live programming stream.',
        startTime:     cur.start_timestamp ? cur.start_timestamp * 1000 : Date.parse(cur.start),
        endTime:       cur.stop_timestamp  ? cur.stop_timestamp  * 1000 : Date.parse(cur.end),
        nextTitle:     next ? _decodeB64(next.title || '') : null,
        nextStartTime: next ? (next.start_timestamp ? next.start_timestamp * 1000 : Date.parse(next.start)) : null,
        isServerEPG:   true
      };

      shortEpgCache.set(cacheKey, { ts: Date.now(), data });
      return data;
    }
  } catch (e) {
    console.debug(`[EPG] get_short_epg failed for ${streamId}:`, e.message);
  }
  return null;
}

function _decodeB64(str) {
  if (!str) return '';
  try {
    if (/^[A-Za-z0-9+/=]+$/.test(str) && str.length % 4 === 0 && str.length > 12) {
      return decodeURIComponent(escape(atob(str)));
    }
  } catch (_) {}
  return str;
}

// ─── Progress helpers ────────────────────────────────────────────────────────
export function formatTime(val) {
  if (!val) return '';
  const d = new Date(val);
  if (isNaN(d.getTime())) return '';
  return d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

export function getProgramProgress(program) {
  if (!program || !program.startTime || !program.endTime) {
    return { percent: 0, remainingMinutes: 0, formattedStart: '', formattedEnd: '', isLive: false };
  }
  const now   = Date.now();
  const start = new Date(program.startTime).getTime();
  const end   = new Date(program.endTime).getTime();
  if (isNaN(start) || isNaN(end) || end <= start) {
    return { percent: 0, remainingMinutes: 0, formattedStart: '', formattedEnd: '', isLive: false };
  }
  const total   = end - start;
  const elapsed = Math.max(0, now - start);
  const remaining = Math.max(0, end - now);
  return {
    percent:         Math.min(100, Math.max(0, Math.round((elapsed / total) * 100))),
    remainingMinutes: Math.ceil(remaining / 60000),
    formattedStart:  formatTime(start),
    formattedEnd:    formatTime(end),
    isLive:          now >= start && now <= end
  };
}

// ─── Known-Network Schedule Table ────────────────────────────────────────────
/**
 * Real show titles mapped by channel name keyword and hour-of-day block.
 * These reflect actual programming patterns rather than generic labels.
 */
function _getKnownNetworkSchedule(channel) {
  const name  = (channel.name  || '').toUpperCase();
  const group = (channel.group || '').toUpperCase();
  const now   = new Date();
  const hour  = now.getHours();

  const entry = KNOWN_NETWORKS.find(n =>
    n.keys.some(k => name.includes(k) || group.includes(k))
  );
  if (!entry) return null;

  // Pick slot from schedule array based on hour
  const slots = entry.schedule;
  // Slot can be an exact hour match or a range; fall through to nearest
  const slot = slots.find(s => {
    if (s.hours) return s.hours.includes(hour);
    if (s.from !== undefined && s.to !== undefined) return hour >= s.from && hour < s.to;
    return false;
  }) || slots[hour % slots.length];

  if (!slot) return null;

  // Build times: snap to nearest 30-min block
  const mins = now.getMinutes();
  const blockStart = mins < 30 ? 0 : 30;
  const blockEnd   = blockStart + 30;
  const start = new Date(now.getFullYear(), now.getMonth(), now.getDate(), hour, blockStart, 0, 0);
  const end   = new Date(now.getFullYear(), now.getMonth(), now.getDate(), hour, blockEnd,   0, 0);
  if (end.getTime() <= now.getTime()) {
    // bump to next 30-min slot
    start.setMinutes(blockEnd);
    end.setMinutes(blockEnd + 30);
  }

  const titles = Array.isArray(slot.titles) ? slot.titles : [slot.title];
  const hash   = _hash(channel.name || '') % titles.length;
  const title  = titles[hash];
  const desc   = slot.desc || '';

  const nextSlot = slots[(slots.indexOf(slot) + 1) % slots.length];
  const nextTitles = Array.isArray(nextSlot?.titles) ? nextSlot.titles : [nextSlot?.title];
  const nextTitle  = nextTitles ? nextTitles[hash % nextTitles.length] : null;

  return {
    title,
    description:   desc,
    startTime:     start.getTime(),
    endTime:       end.getTime(),
    nextTitle,
    nextStartTime: end.getTime(),
    isServerEPG:   false
  };
}

function _hash(str) {
  let h = 0;
  for (let i = 0; i < str.length; i++) { h = (h << 5) - h + str.charCodeAt(i); h |= 0; }
  return Math.abs(h);
}

// ─── Procedural Fallback ─────────────────────────────────────────────────────
function _proceduralFallback(channel) {
  const now   = new Date();
  const hour  = now.getHours();
  const name  = (channel?.name  || '').toUpperCase();
  const group = (channel?.group || '').toUpperCase();
  const hash  = _hash(channel?.name || channel?.id || 'tv');

  let pool = PROCEDURAL_ENTERTAINMENT;
  if (group.includes('SPORT') || name.match(/\bESPN\b|\bFOX SPORT|\bNBCSN|\bCBS SPORT|\bBEIN\b|\bSPO\b/))        pool = PROCEDURAL_SPORTS;
  else if (group.includes('NEWS') || name.match(/\bNEWS\b|\bCNN\b|\bMSNBC\b|\bFOX NEWS\b|\bCNBC\b|\bBBC\b/))    pool = PROCEDURAL_NEWS;
  else if (group.includes('MOVIE') || name.match(/\bHBO\b|\bCINEMAX\b|\bSHOWTIME\b|\bSTARZ\b|\bMOVIE\b/))       pool = PROCEDURAL_MOVIES;
  else if (group.includes('KIDS')  || name.match(/\bDISNEY\b|\bNICK\b|\bCARTOON\b|\bKIDS\b|\bPBS KIDS\b/))      pool = PROCEDURAL_KIDS;
  else if (group.includes('DOC')   || name.match(/\bDISCOVERY\b|\bNAT GEO\b|\bHISTORY\b|\bSCIENCE\b|\bA&E\b/))  pool = PROCEDURAL_DOCS;

  const idx    = (hour + hash) % pool.length;
  const next   = (idx + 1) % pool.length;
  const cur    = pool[idx];
  const nxt    = pool[next];

  const mins   = now.getMinutes() < 30 ? 0 : 30;
  const start  = new Date(now.getFullYear(), now.getMonth(), now.getDate(), hour, mins, 0, 0);
  const end    = new Date(start.getTime() + 30 * 60 * 1000);

  const cTitles = Array.isArray(cur.titles) ? cur.titles : [cur.title];
  const nTitles = Array.isArray(nxt.titles) ? nxt.titles : [nxt.title];
  const h = hash % cTitles.length;

  return {
    title:         cTitles[h],
    description:   cur.desc || '',
    startTime:     start.getTime(),
    endTime:       end.getTime(),
    nextTitle:     nTitles[h % nTitles.length],
    nextStartTime: end.getTime(),
    isServerEPG:   false
  };
}

// ─── Known-Network Schedules ────────────────────────────────────────────────
const KNOWN_NETWORKS = [
  {
    keys: ['ESPN', 'ESPN2', 'ESPNU', 'ESPN+'],
    schedule: [
      { from: 6,  to: 9,  titles: ['SportsCenter', 'Get Up', 'First Take (Early)'], desc: 'Morning sports news, highlights and analysis.' },
      { from: 9,  to: 12, titles: ['First Take', 'Get Up', 'SportsCenter AM'], desc: 'Stephen A. Smith and the crew debate the day\'s biggest stories.' },
      { from: 12, to: 15, titles: ['SportsCenter', 'NFL Live', 'Baseball Tonight'], desc: 'Midday sports roundup and league coverage.' },
      { from: 15, to: 17, titles: ['Around the Horn', 'Pardon the Interruption', 'The Herd'], desc: 'PTI: Tony Kornheiser and Michael Wilbon give their takes.' },
      { from: 17, to: 20, titles: ['SportsCenter', 'Monday Night Countdown', 'NFL Primetime'], desc: 'Evening sportscenter with live game lead-up coverage.' },
      { from: 20, to: 23, titles: ['NFL Monday Night Football', 'NBA Basketball', 'MLB Baseball', 'College Football'], desc: 'Live primetime sports action.' },
      { from: 23, to: 6,  titles: ['SportsCenter Late', 'ESPN Films', 'E:60'], desc: 'Late-night highlights, scores, and in-depth sports features.' }
    ]
  },
  {
    keys: ['CNN'],
    schedule: [
      { from: 5,  to: 9,  titles: ['CNN This Morning', 'Early Start'], desc: 'The morning\'s top stories and breaking developments.' },
      { from: 9,  to: 12, titles: ['CNN News Central', 'Inside Politics'], desc: 'Live news coverage with anchor team.' },
      { from: 12, to: 15, titles: ['CNN News Central', 'CNN Newsroom', 'Inside Politics with Dana Bash'], desc: 'Afternoon live news desk.' },
      { from: 15, to: 17, titles: ['The Lead with Jake Tapper', 'CNN Newsroom with Brianna Keilar'], desc: 'Jake Tapper leads analysis of the day\'s top stories.' },
      { from: 17, to: 19, titles: ['The Situation Room with Wolf Blitzer', 'CNN Newsroom'], desc: 'Wolf Blitzer anchors the evening news desk.' },
      { from: 19, to: 21, titles: ['Erin Burnett OutFront', 'CNN Tonight'], desc: 'Erin Burnett leads in-depth political coverage.' },
      { from: 21, to: 23, titles: ['Anderson Cooper 360°', 'CNN Tonight with Laura Coates'], desc: 'Anderson Cooper delivers comprehensive breaking news analysis.' },
      { from: 23, to: 5,  titles: ['CNN Newsroom Overnight', 'World Sport'], desc: 'Overnight international coverage.' }
    ]
  },
  {
    keys: ['FOX NEWS', 'FOX NEWS CHANNEL'],
    schedule: [
      { from: 6,  to: 9,  titles: ['FOX & Friends'], desc: 'Morning news, politics, and pop culture.' },
      { from: 9,  to: 11, titles: ['America\'s Newsroom'], desc: 'Breaking news and political coverage.' },
      { from: 11, to: 13, titles: ['The Faulkner Focus', 'Outnumbered'], desc: 'Midday political debate and commentary.' },
      { from: 13, to: 15, titles: ['America Reports', 'Your World with Neil Cavuto'], desc: 'Afternoon news desk.' },
      { from: 15, to: 17, titles: ['The Story with Martha MacCallum', 'FOX News Live'], desc: 'Martha MacCallum leads afternoon analysis.' },
      { from: 17, to: 18, titles: ['Bret Baier: Special Report'], desc: 'In-depth political reporting.' },
      { from: 18, to: 19, titles: ['Jesse Watters Primetime'], desc: 'Primetime commentary and news.' },
      { from: 19, to: 20, titles: ['The Ingraham Angle'], desc: 'Laura Ingraham with sharp political commentary.' },
      { from: 20, to: 21, titles: ['Hannity'], desc: 'Sean Hannity covers the day\'s top political news.' },
      { from: 21, to: 22, titles: ['Gutfeld!'], desc: 'Comedy and satire-fueled political late-night.' },
      { from: 22, to: 5,  titles: ['FOX News @ Night', 'FOX News Overnight'], desc: 'Overnight news and coverage.' }
    ]
  },
  {
    keys: ['MSNBC'],
    schedule: [
      { from: 5,  to: 9,  titles: ['Morning Joe'], desc: 'Joe Scarborough and Mika Brzezinski with the morning\'s political stories.' },
      { from: 9,  to: 12, titles: ['MSNBC Reports', 'Jose Diaz-Balart Reports'], desc: 'Live midmorning news updates.' },
      { from: 12, to: 14, titles: ['Andrea Mitchell Reports', 'MSNBC Reports'], desc: 'Andrea Mitchell leads political coverage.' },
      { from: 14, to: 15, titles: ['Katy Tur Reports'], desc: 'Live coverage from across the nation.' },
      { from: 15, to: 16, titles: ['Deadline: White House'], desc: 'Nicolle Wallace leads breaking political analysis.' },
      { from: 16, to: 18, titles: ['The Beat with Ari Melber', 'Deadline: White House'], desc: 'Ari Melber leads legal and political analysis.' },
      { from: 18, to: 19, titles: ['The ReidOut'], desc: 'Joy Reid covers the day\'s biggest stories.' },
      { from: 19, to: 20, titles: ['All In with Chris Hayes'], desc: 'Chris Hayes tackles politics and current events.' },
      { from: 20, to: 21, titles: ['The Rachel Maddow Show', 'Alex Wagner Tonight'], desc: 'Rachel Maddow provides deep political context.' },
      { from: 21, to: 22, titles: ['The Last Word with Lawrence O\'Donnell', 'Alex Wagner Tonight'], desc: 'Lawrence O\'Donnell closes the evening news.' },
      { from: 22, to: 5,  titles: ['The 11th Hour', 'MSNBC Reports Overnight'], desc: 'The final word on the day\'s big stories.' }
    ]
  },
  {
    keys: ['CNBC'],
    schedule: [
      { from: 5,  to: 6,  titles: ['Worldwide Exchange'], desc: 'Overnight markets and global business news.' },
      { from: 6,  to: 9,  titles: ['Squawk Box'], desc: 'Squawk Box: premarket futures, economic data, and business headlines.' },
      { from: 9,  to: 11, titles: ['Squawk on the Street'], desc: 'Live from the NYSE floor as markets open.' },
      { from: 11, to: 13, titles: ['TechCheck', 'Halftime Report'], desc: 'Scott Wapner and the investment panel.' },
      { from: 13, to: 15, titles: ['Power Lunch', 'The Exchange'], desc: 'Midday market coverage and business news.' },
      { from: 15, to: 17, titles: ['Closing Bell', 'Fast Money Halftime'], desc: 'Sara Eisen anchors the closing bell.' },
      { from: 17, to: 18, titles: ['Fast Money', 'Options Action'], desc: 'Traders react to the closing market session.' },
      { from: 18, to: 19, titles: ['Mad Money with Jim Cramer'], desc: 'Jim Cramer\'s picks and market analysis.' },
      { from: 19, to: 22, titles: ['CNBC Documentaries', 'The Profit', 'American Greed'], desc: 'Business and finance documentary programming.' },
      { from: 22, to: 5,  titles: ['CNBC Overnight', 'Asia Market Coverage'], desc: 'Overnight global market news.' }
    ]
  },
  {
    keys: ['HBO'],
    schedule: [
      { from: 6,  to: 14, titles: ['The White Lotus', 'Euphoria', 'The Wire', 'Game of Thrones', 'Succession'], desc: 'Acclaimed HBO drama series.' },
      { from: 14, to: 18, titles: ['True Detective', 'The Righteous Gemstones', 'Barry', 'Curb Your Enthusiasm'], desc: 'HBO drama and comedy programming.' },
      { from: 18, to: 21, titles: ['House of the Dragon', 'The Last of Us', 'Succession', 'Euphoria'], desc: 'HBO primetime premiere presentation.' },
      { from: 21, to: 24, titles: ['Real Time with Bill Maher', 'Last Week Tonight', 'Westworld', 'Chernobyl'], desc: 'Late HBO original programming.' }
    ]
  },
  {
    keys: ['SHOWTIME', 'SHO'],
    schedule: [
      { from: 6,  to: 18, titles: ['Yellowjackets', 'Billions', 'The Chi', 'Homeland', 'Dexter'], desc: 'Showtime drama programming.' },
      { from: 18, to: 22, titles: ['Billions', 'The Chi', 'Yellowjackets', 'Ray Donovan'], desc: 'Showtime primetime.' },
      { from: 22, to: 6,  titles: ['Shameless', 'City on a Hill', 'Dexter: New Blood', 'Flatbush Misdemeanors'], desc: 'Late night Showtime.' }
    ]
  },
  {
    keys: ['STARZ'],
    schedule: [
      { from: 6,  to: 18, titles: ['Outlander', 'Power Book II: Ghost', 'BMF', 'The Great'], desc: 'Starz drama series.' },
      { from: 18, to: 22, titles: ['Power Book II: Ghost', 'Outlander', 'BMF', 'P-Valley'], desc: 'Starz primetime originals.' },
      { from: 22, to: 6,  titles: ['BMF', 'Hightown', 'Blindspotting', 'Heels'], desc: 'Late night Starz originals.' }
    ]
  },
  {
    keys: ['DISCOVERY'],
    schedule: [
      { from: 6,  to: 10, titles: ['Deadliest Catch', 'Gold Rush', 'How It\'s Made'], desc: 'Morning Discovery Channel programming.' },
      { from: 10, to: 14, titles: ['MythBusters', 'Dirty Jobs', 'How It\'s Made'], desc: 'Midday fan-favorite Discovery series.' },
      { from: 14, to: 18, titles: ['Naked and Afraid', 'Alaska: The Last Frontier', 'Moonshiners'], desc: 'Afternoon survival and wilderness reality.' },
      { from: 18, to: 22, titles: ['Gold Rush', 'Deadliest Catch', 'Expedition Unknown', 'Shark Week'], desc: 'Discovery primetime lineup.' },
      { from: 22, to: 6,  titles: ['Fast N\' Loud', 'Street Outlaws', 'Diesel Brothers'], desc: 'Discovery late night shows.' }
    ]
  },
  {
    keys: ['NAT GEO', 'NATIONAL GEOGRAPHIC'],
    schedule: [
      { from: 6,  to: 14, titles: ['Life Below Zero', 'Gordon Ramsay: Uncharted', 'Wicked Tuna', 'Brain Games'], desc: 'Nat Geo daytime programming.' },
      { from: 14, to: 18, titles: ['Running Wild with Bear Grylls', 'One Strange Rock', 'Origins'], desc: 'Nat Geo afternoon adventure.' },
      { from: 18, to: 22, titles: ['Life Below Zero', 'Gordon Ramsay\'s Food Stars', 'Genius', 'Valley of the Boom'], desc: 'Nat Geo primetime.' },
      { from: 22, to: 6,  titles: ['Explorer', 'Border Wars', 'Wild Justice'], desc: 'Nat Geo late night.' }
    ]
  },
  {
    keys: ['HISTORY'],
    schedule: [
      { from: 6,  to: 14, titles: ['Pawn Stars', 'American Pickers', 'Forged in Fire', 'Mountain Men'], desc: 'Daytime History Channel hits.' },
      { from: 14, to: 18, titles: ['Curse of Oak Island', 'Alone', 'Ice Road Truckers', 'Ax Men'], desc: 'Afternoon History Channel originals.' },
      { from: 18, to: 22, titles: ['The Food That Built America', 'The UnXplained', 'Ancient Aliens', 'Alone'], desc: 'History Channel primetime.' },
      { from: 22, to: 6,  titles: ['Pawn Stars After Dark', 'Hunting Atlantis', 'The Proof Is Out There'], desc: 'Late night History Channel.' }
    ]
  },
  {
    keys: ['A&E', 'A&E NETWORK'],
    schedule: [
      { from: 6,  to: 14, titles: ['Hoarders', 'Intervention', 'Live PD Presents PD Cam', 'Storage Wars'], desc: 'A&E daytime programming.' },
      { from: 14, to: 18, titles: ['Storage Wars', 'Parking Wars', 'Shipping Wars'], desc: 'A&E afternoon reality series.' },
      { from: 18, to: 22, titles: ['The First 48', 'Live PD', 'Cold Case Files', '60 Days In'], desc: 'A&E primetime true crime.' },
      { from: 22, to: 6,  titles: ['Nightwatch Nation', 'Hoarders After Dark', 'Criminal Confessions'], desc: 'A&E late night.' }
    ]
  },
  {
    keys: ['AMC'],
    schedule: [
      { from: 6,  to: 14, titles: ['The Walking Dead', 'Better Call Saul', 'Breaking Bad', 'Mad Men', 'The Terror'], desc: 'AMC daytime drama.' },
      { from: 14, to: 18, titles: ['Fear the Walking Dead', 'The Walking Dead: Dead City', 'Dark Winds'], desc: 'AMC afternoon originals.' },
      { from: 18, to: 22, titles: ['The Walking Dead: The Ones Who Live', 'Better Call Saul', 'Anne Rice\'s Interview with the Vampire'], desc: 'AMC primetime originals.' },
      { from: 22, to: 6,  titles: ['Talking Dead', 'Ride with Norman Reedus', 'Into the Badlands'], desc: 'AMC late night programming.' }
    ]
  },
  {
    keys: ['FX'],
    schedule: [
      { from: 6,  to: 14, titles: ['Justified', 'Archer', 'It\'s Always Sunny in Philadelphia', 'The Americans'], desc: 'FX daytime drama and comedy.' },
      { from: 14, to: 18, titles: ['Pose', 'Fargo', 'American Horror Story', 'The Shield'], desc: 'FX afternoon originals.' },
      { from: 18, to: 22, titles: ['The Bear', 'Shogun', 'American Crime Story', 'Fargo'], desc: 'FX primetime award-winning originals.' },
      { from: 22, to: 6,  titles: ['What We Do in the Shadows', 'Reservation Dogs', 'Dave'], desc: 'FX late night comedy.' }
    ]
  },
  {
    keys: ['TBS'],
    schedule: [
      { from: 6,  to: 12, titles: ['Friends', 'The Big Bang Theory', 'Seinfeld', 'Family Guy'], desc: 'TBS morning sitcom block.' },
      { from: 12, to: 17, titles: ['The Big Bang Theory', 'Young Sheldon', 'Friends', 'Two and a Half Men'], desc: 'TBS afternoon comedy marathon.' },
      { from: 17, to: 20, titles: ['MLB Baseball', 'American Dad', 'Family Guy', 'Young Sheldon'], desc: 'TBS early primetime.' },
      { from: 20, to: 23, titles: ['The Last O.G.', 'Search Party', 'Full Frontal', 'Conan'], desc: 'TBS original primetime.' },
      { from: 23, to: 6,  titles: ['Conan', 'Final Space', 'Miracle Workers', 'The Detour'], desc: 'TBS late night.' }
    ]
  },
  {
    keys: ['TNT'],
    schedule: [
      { from: 6,  to: 13, titles: ['Law & Order', 'Castle', 'Supernatural', 'Bones'], desc: 'TNT morning drama block.' },
      { from: 13, to: 18, titles: ['Major Crimes', 'Rizzoli & Isles', 'The Mentalist', 'NCIS'], desc: 'TNT afternoon procedural block.' },
      { from: 18, to: 21, titles: ['NBA Basketball', 'NFC Football', 'Claws', 'Animal Kingdom'], desc: 'TNT early primetime.' },
      { from: 21, to: 24, titles: ['Animal Kingdom', 'Snowpiercer', 'Raised by Wolves', 'The Alienist'], desc: 'TNT primetime originals.' }
    ]
  },
  {
    keys: ['BRAVO'],
    schedule: [
      { from: 6,  to: 14, titles: ['The Real Housewives of New York City', 'Below Deck', 'Project Runway', 'Top Chef'], desc: 'Bravo morning marathon.' },
      { from: 14, to: 18, titles: ['Vanderpump Rules', 'Summer House', 'The Real Housewives of Atlanta', 'Southern Charm'], desc: 'Bravo afternoon reality.' },
      { from: 18, to: 22, titles: ['The Real Housewives of New Jersey', 'Million Dollar Listing', 'Shahs of Sunset', 'The Real Housewives of Salt Lake City'], desc: 'Bravo primetime reality.' },
      { from: 22, to: 6,  titles: ['Watch What Happens Live with Andy Cohen', 'The Real Housewives of Miami', 'Below Deck Mediterranean'], desc: 'Bravo late night.' }
    ]
  },
  {
    keys: ['HALLMARK'],
    schedule: [
      { from: 6,  to: 18, titles: ['Chesapeake Shores', 'When Calls the Heart', 'Hearts of Winter', 'A Royal Winter', 'A Midnight Kiss'], desc: 'Hallmark daytime romantic movies and series.' },
      { from: 18, to: 22, titles: ['A Christmas to Remember', 'Love on the Sidelines', 'Bottled with Love', 'Harvest Love'], desc: 'Hallmark primetime original movie.' },
      { from: 22, to: 6,  titles: ['Mystery 101', 'Chronicle Mysteries', 'Aurora Teagarden Mysteries'], desc: 'Hallmark Movies & Mysteries late night.' }
    ]
  },
  {
    keys: ['LIFETIME'],
    schedule: [
      { from: 6,  to: 14, titles: ['Dance Moms', 'Little Women: Atlanta', 'Project Runway All Stars', 'Married at First Sight'], desc: 'Lifetime daytime programming.' },
      { from: 14, to: 18, titles: ['Ripped from the Headlines', 'Lifetime True Crime', 'Sister Wives', 'Escaping Polygamy'], desc: 'Lifetime afternoon real-life drama.' },
      { from: 18, to: 22, titles: ['Lifetime Movie', 'Married at First Sight', 'UnReal', 'You', 'American Princess'], desc: 'Lifetime original primetime movie and series.' },
      { from: 22, to: 6,  titles: ['Lifetime Movie Network Late', 'Killer Couples', 'I Married a Murderer'], desc: 'Lifetime late night true crime.' }
    ]
  },
  {
    keys: ['NICK', 'NICKELODEON'],
    schedule: [
      { from: 6,  to: 12, titles: ['SpongeBob SquarePants', 'Dora the Explorer', 'Blue\'s Clues & You!', 'PAW Patrol'], desc: 'Nick morning kids programming.' },
      { from: 12, to: 17, titles: ['iCarly', 'Fairly OddParents', 'Loud House', 'Henry Danger'], desc: 'Nick afternoon block.' },
      { from: 17, to: 20, titles: ['The Casagrandes', 'Side Hustle', 'Young Dylan', 'That Girl Lay Lay'], desc: 'Nick early evening.' },
      { from: 20, to: 22, titles: ['SpongeBob SquarePants', 'Rise of the Teenage Mutant Ninja Turtles', 'Koral'], desc: 'Nick primetime animation.' },
      { from: 22, to: 6,  titles: ['Victorious', 'iCarly Classic', 'Drake & Josh', 'SpongeBob SquarePants'], desc: 'Nick@Nite classic comedy.' }
    ]
  },
  {
    keys: ['DISNEY', 'DISNEY CHANNEL'],
    schedule: [
      { from: 6,  to: 12, titles: ['Mickey Mouse Clubhouse', 'Bluey', 'Amphibia', 'Big City Greens'], desc: 'Disney morning kids block.' },
      { from: 12, to: 17, titles: ['Phineas and Ferb', 'Gravity Falls', 'The Owl House', 'Amphibia'], desc: 'Disney afternoon animation.' },
      { from: 17, to: 20, titles: ['Sydney to the Max', 'The Proud Family: Louder and Prouder', 'Just Roll With It'], desc: 'Disney Channel live-action block.' },
      { from: 20, to: 22, titles: ['Chip \'n Dale: Park Life', 'Big Shot', 'Baymax!', 'Creatures of the Deep'], desc: 'Disney Channel primetime special.' },
      { from: 22, to: 6,  titles: ['Descendants: The Rise of Red', 'Kim Possible', 'Even Stevens', 'Lizzie McGuire'], desc: 'Disney Channel late night classics.' }
    ]
  },
  {
    keys: ['CARTOON NETWORK', 'CN'],
    schedule: [
      { from: 6,  to: 12, titles: ['Steven Universe', 'Craig of the Creek', 'Mao Mao: Heroes of Pure Heart', 'The Amazing World of Gumball'], desc: 'Cartoon Network morning block.' },
      { from: 12, to: 17, titles: ['Teen Titans Go!', 'Ben 10', 'OK K.O.! Let\'s Be Heroes', 'Apple & Onion'], desc: 'Cartoon Network afternoon block.' },
      { from: 17, to: 20, titles: ['Regular Show', 'Adventure Time', 'Uncle Grandpa', 'Clarence'], desc: 'Cartoon Network early evening.' },
      { from: 20, to: 24, titles: ['Rick and Morty', 'Aqua Teen Hunger Force', 'Robot Chicken', 'The Boondocks'], desc: 'Adult Swim late night animation.' }
    ]
  },
  {
    keys: ['NBC', 'NBC SPORTS'],
    schedule: [
      { from: 7,  to: 9,  titles: ['TODAY with Hoda & Jenna', 'TODAY', 'Sunday TODAY'], desc: 'NBC TODAY morning show.' },
      { from: 9,  to: 11, titles: ['TODAY 3rd Hour', 'TODAY', 'Meet the Press NOW'], desc: 'Extended NBC morning programming.' },
      { from: 11, to: 13, titles: ['NBC News Daily', 'The Kelly Clarkson Show', 'Access Hollywood'], desc: 'NBC midday programming.' },
      { from: 13, to: 16, titles: ['Days of Our Lives', 'The Kelly Clarkson Show', 'NBC News Special'], desc: 'NBC afternoon soap and talk.' },
      { from: 16, to: 19, titles: ['NBC Nightly News with Lester Holt', 'Local News', 'NBC News Now'], desc: 'NBC evening news broadcast.' },
      { from: 19, to: 22, titles: ['Law & Order', 'Chicago Fire', 'The Voice', 'America\'s Got Talent'], desc: 'NBC primetime lineup.' },
      { from: 22, to: 24, titles: ['Tonight Show Starring Jimmy Fallon', 'Late Night with Seth Meyers'], desc: 'NBC late night comedy.' }
    ]
  },
  {
    keys: ['CBS'],
    schedule: [
      { from: 7,  to: 9,  titles: ['CBS Mornings', 'CBS Saturday Morning'], desc: 'CBS Mornings with Gayle King, Tony Dokoupil and Nate Burleson.' },
      { from: 9,  to: 12, titles: ['The Price Is Right', 'The Talk', 'Let\'s Make a Deal'], desc: 'CBS daytime game shows.' },
      { from: 12, to: 14, titles: ['The Young and the Restless', 'The Bold and the Beautiful'], desc: 'CBS daytime soaps.' },
      { from: 14, to: 17, titles: ['CBS News Live', 'The Bold and the Beautiful', 'NCIS Reruns'], desc: 'CBS afternoon programming.' },
      { from: 17, to: 19, titles: ['CBS Evening News with Norah O\'Donnell', '60 Minutes Preview'], desc: 'CBS evening news with Norah O\'Donnell.' },
      { from: 19, to: 22, titles: ['NCIS', 'FBI', 'The Equalizer', 'Survivor', '60 Minutes', 'Young Sheldon'], desc: 'CBS primetime lineup.' },
      { from: 22, to: 24, titles: ['The Late Show with Stephen Colbert', 'Late Late Show with James Corden'], desc: 'CBS late night comedy.' }
    ]
  },
  {
    keys: ['ABC'],
    schedule: [
      { from: 7,  to: 9,  titles: ['Good Morning America', 'GMA3: What You Need To Know'], desc: 'ABC\'s Good Morning America with Robin Roberts.' },
      { from: 9,  to: 12, titles: ['The View', 'Live with Kelly and Ryan', 'GMA3'], desc: 'ABC midmorning talk shows.' },
      { from: 12, to: 14, titles: ['General Hospital', 'ABC News Live Update'], desc: 'ABC daytime soap opera.' },
      { from: 14, to: 17, titles: ['ABC News Live', 'The View Re-Air', 'Match Game'], desc: 'ABC afternoon programming.' },
      { from: 17, to: 19, titles: ['ABC World News Tonight with David Muir', 'ABC News Special'], desc: 'ABC evening news with David Muir.' },
      { from: 19, to: 22, titles: ['Grey\'s Anatomy', 'Dancing with the Stars', 'The Bachelor', 'American Idol', 'Shark Tank'], desc: 'ABC primetime lineup.' },
      { from: 22, to: 24, titles: ['Jimmy Kimmel Live!', 'Nightline', '20/20'], desc: 'ABC late night with Jimmy Kimmel.' }
    ]
  }
];

// ─── Procedural pools (for unidentified channels) ────────────────────────────
// Still much better than the original — real show-like titles with variety.
const PROCEDURAL_SPORTS = [
  { titles: ['SportsCenter', 'NFL GameDay Morning', 'MLB Tonight', 'College GameDay'], desc: 'Live sports news and highlights.' },
  { titles: ['Monday Night Football', 'NBA on TNT', 'UFC Main Event', 'PGA Tour Live'], desc: 'Live primetime sports action.' },
  { titles: ['Fantasy Football Now', 'The Dan Patrick Show', 'The Rich Eisen Show'], desc: 'Sports talk and fantasy analysis.' },
  { titles: ['NFL RedZone', 'MLB Extra Innings', 'NHL Power Play', 'NBA League Pass'], desc: 'Multi-game sports coverage.' },
  { titles: ['Outside the Lines', 'E:60', 'A Football Life', 'Hard Knocks'], desc: 'In-depth sports features and documentaries.' },
  { titles: ['NASCAR Race Day', 'IndyCar Series', 'Formula E Grand Prix', 'IMSA Sports Car'], desc: 'Live motorsport coverage.' }
];

const PROCEDURAL_NEWS = [
  { titles: ['Morning News Report', 'The Situation Room', 'Breaking News Coverage', 'Live News Desk'], desc: 'Live breaking news updates.' },
  { titles: ['Political Roundtable', 'Washington Journal', 'Press Briefing Live', 'State of the Union'], desc: 'Political analysis and commentary.' },
  { titles: ['World Affairs Report', 'International Desk', 'Foreign Correspondent Live', 'Global Watch'], desc: 'International news coverage.' },
  { titles: ['Markets & Money', 'Business Report', 'Wall Street Wrap', 'Economic Outlook'], desc: 'Financial news and market analysis.' },
  { titles: ['Prime Time News', 'Nighttime Anchor Report', 'Evening Edition', 'Nightly Wrap'], desc: 'Evening news broadcast.' }
];

const PROCEDURAL_MOVIES = [
  { titles: ['Academy Award Winner Screening', 'Critics\' Choice Feature Presentation', 'Box Office Hit Premiere'], desc: 'Award-winning feature film presentation.' },
  { titles: ['Action Weekend Blockbuster', 'Thriller Cinema Night', 'Suspense Feature Film'], desc: 'Action and thriller feature film.' },
  { titles: ['Classic Cinema Revival', 'Golden Era Hollywood', 'Film Noir Showcase'], desc: 'Timeless classic film presentation.' },
  { titles: ['Sci-Fi & Fantasy Movie Night', 'Marvel Studios Feature', 'Fantasy Cinema Showcase'], desc: 'Science fiction and fantasy film.' },
  { titles: ['Comedy Movie Weekend', 'Rom-Com Movie Marathon', 'Family Comedy Feature'], desc: 'Comedy feature film presentation.' },
  { titles: ['Drama Feature Presentation', 'Award-Season Selection', 'Prestige Film Showcase'], desc: 'Critically acclaimed drama presentation.' }
];

const PROCEDURAL_KIDS = [
  { titles: ['PAW Patrol', 'Bluey', 'Peppa Pig', 'Cocomelon', 'Sesame Street'], desc: 'Fun kids programming for little ones.' },
  { titles: ['SpongeBob SquarePants', 'Rugrats', 'Hey Arnold!', 'Rocket Power', 'As Told by Ginger'], desc: 'Classic animated series for kids.' },
  { titles: ['Pokémon Journeys', 'Digimon', 'Beyblade Burst', 'Bakugan'], desc: 'Action-packed anime for kids.' },
  { titles: ['Gravity Falls', 'Star vs. the Forces of Evil', 'Owl House', 'Amphibia'], desc: 'Adventure and mystery animated series.' }
];

const PROCEDURAL_DOCS = [
  { titles: ['Planet Earth III', 'Our Planet', 'Blue Planet II', 'Wild Isles'], desc: 'Breathtaking nature and wildlife documentary.' },
  { titles: ['Secrets of the Dead', 'History\'s Greatest Mysteries', 'What Really Happened?', 'Ancient Impossible'], desc: 'Historical mystery documentary.' },
  { titles: ['Space: The Final Frontier', 'How the Universe Works', 'To the Moon and Beyond', 'Cosmos: Possible Worlds'], desc: 'Space and science documentary.' },
  { titles: ['MegaStructures', 'Engineering Connections', 'How It\'s Made', 'Richard Hammond\'s Big'], desc: 'Engineering and technology documentary.' },
  { titles: ['True Crime Files', 'Cold Case: Unsolved', 'Forensic Files II', 'American Monster'], desc: 'True crime and forensics documentary.' }
];

const PROCEDURAL_ENTERTAINMENT = [
  { titles: ['Late Night Live', 'Tonight Show Highlights', 'Jimmy Kimmel Clips', 'Saturday Night Live'], desc: 'Late night comedy and entertainment.' },
  { titles: ['The Voice', 'American Idol', 'America\'s Got Talent', 'Dancing with the Stars'], desc: 'Live talent competition show.' },
  { titles: ['Survivor', 'The Amazing Race', 'Big Brother', 'Love Island USA'], desc: 'Reality competition series.' },
  { titles: ['Law & Order', 'Criminal Minds', 'NCIS', 'CSI: Vegas', 'Blue Bloods'], desc: 'Procedural crime drama.' },
  { titles: ['This Is Us', 'Grey\'s Anatomy', 'Station 19', 'A Million Little Things'], desc: 'Primetime drama series.' },
  { titles: ['The Office', 'Parks and Recreation', 'Brooklyn Nine-Nine', 'Abbott Elementary'], desc: 'Workplace comedy series.' }
];
