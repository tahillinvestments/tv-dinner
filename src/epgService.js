/**
 * EPG Service for Live TV Programming Information
 * ─────────────────────────────────────────────────
 * Only serves verified data:
 *  1. Bulk XMLTV feed from the Xtream portal  (/xmltv.php)
 *  2. Per-stream get_short_epg API call       (on player open)
 *  3. Placeholder                             (no data available)
 *
 * No fake/procedural titles are generated.
 */

// ─── In-memory state ─────────────────────────────────────────────────────────
/** Maps epg_channel_id → [ { title, desc, start (ms), stop (ms) } ] */
const xmltvEpgTable = new Map();
let xmltvLoaded = false;
let xmltvLoadPromise = null;

/** Per-stream short EPG cache */
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
        console.log(`[EPG] Loaded XMLTV from cache (${xmltvEpgTable.size} channels)`);
        return;
      }
    }
  } catch (_) {}

  const cleanPortal = portalUrl.trim().replace(/\/+$/, '');
  const xmlUrl = `${cleanPortal}/xmltv.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`;
  const proxied = _buildProxyUrl(xmlUrl);

  console.log('[EPG] Fetching XMLTV feed…');
  const res = await fetch(proxied, { signal: AbortSignal.timeout(25000) });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);

  const text = await res.text();
  if (!text.includes('<tv') && !text.includes('</programme>')) {
    throw new Error('Response does not look like XMLTV');
  }

  _parseXmltvText(text);

  // Persist to sessionStorage
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
  const external = (() => {
    try { return localStorage.getItem('external_proxy_url'); } catch (_) { return null; }
  })();
  const base = external || '/api/proxy';
  if (base.startsWith('http://') || base.startsWith('https://')) {
    const p = base.endsWith('/') ? base : base + '/';
    return `${p}?url=${encodeURIComponent(url)}`;
  }
  return `${base}?url=${encodeURIComponent(url)}`;
}

// ─── XMLTV Parser ─────────────────────────────────────────────────────────────
function _parseXmltvText(xml) {
  if (typeof DOMParser !== 'undefined') {
    try {
      const doc = new DOMParser().parseFromString(xml, 'text/xml');
      const programmes = doc.querySelectorAll('programme');
      programmes.forEach(prog => {
        const chanId  = prog.getAttribute('channel');
        if (!chanId) return;
        const startMs = _xmltvDateToMs(prog.getAttribute('start'));
        const stopMs  = _xmltvDateToMs(prog.getAttribute('stop'));
        if (!startMs || !stopMs) return;
        const title = (prog.querySelector('title')?.textContent || '').trim();
        const desc  = (prog.querySelector('desc')?.textContent  || '').trim();
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
    const startMs = _xmltvDateToMs((attrs.match(/start="([^"]*)"/) || [])[1]);
    const stopMs  = _xmltvDateToMs((attrs.match(/stop="([^"]*)"/)  || [])[1]);
    if (!startMs || !stopMs) continue;
    const title = ((body.match(/<title[^>]*>([^<]*)<\/title>/i) || [])[1] || '').trim();
    const desc  = ((body.match(/<desc[^>]*>([\s\S]*?)<\/desc>/i)  || [])[1] || '').trim();
    if (!title) continue;
    if (!xmltvEpgTable.has(chanId)) xmltvEpgTable.set(chanId, []);
    xmltvEpgTable.get(chanId).push({ title, desc, start: startMs, stop: stopMs });
  }
}

/** Parse XMLTV datetime: "20260813220000 +0000" or "-0400" */
function _xmltvDateToMs(str) {
  if (!str) return null;
  const m = str.trim().match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})\s*([+-]\d{4})?/);
  if (!m) return null;
  const [, yr, mo, dy, hr, mn, sc, tz] = m;
  const tzStr = tz ? `${tz.slice(0, 3)}:${tz.slice(3)}` : '+00:00';
  const ms = Date.parse(`${yr}-${mo}-${dy}T${hr}:${mn}:${sc}${tzStr}`);
  return isNaN(ms) ? null : ms;
}

// ─── Placeholder ──────────────────────────────────────────────────────────────
function _placeholder() {
  return {
    title:         null,  // null = no verified data
    description:   null,
    startTime:     null,
    endTime:       null,
    nextTitle:     null,
    nextStartTime: null,
    isServerEPG:   false
  };
}

// ─── Public: Get channel program info ─────────────────────────────────────────
/**
 * Returns real EPG object from XMLTV table, or a placeholder with null fields.
 * Callers must check `epg.title !== null` before displaying.
 */
export function getChannelEPGInfo(channel) {
  if (!channel) return _placeholder();

  // 1. Match by epg_channel_id (tvg-id) — the canonical XMLTV key
  const epgId = (channel.epg_channel_id || channel.id || '').trim();
  if (epgId && xmltvEpgTable.has(epgId)) {
    const prog = _findCurrentProgram(xmltvEpgTable.get(epgId));
    if (prog) return prog;
  }

  // 2. Fuzzy-match channel name across all XMLTV keys (handles minor formatting diffs)
  if (xmltvLoaded && xmltvEpgTable.size > 0) {
    const nameNorm = _normalize(channel.name || '');
    if (nameNorm.length >= 3) {
      for (const [key, listings] of xmltvEpgTable) {
        const keyNorm = _normalize(key);
        if (keyNorm === nameNorm || keyNorm.startsWith(nameNorm) || nameNorm.startsWith(keyNorm)) {
          const prog = _findCurrentProgram(listings);
          if (prog) return prog;
        }
      }
    }
  }

  // 3. No verified data
  return _placeholder();
}

function _normalize(str) {
  return str.toLowerCase().replace(/[^a-z0-9]/g, '');
}

function _findCurrentProgram(listings) {
  if (!listings || !listings.length) return null;
  const now = Date.now();
  const sorted = [...listings].sort((a, b) => a.start - b.start);
  const idx = sorted.findIndex(p => now >= p.start && now < p.stop);
  if (idx === -1) return null;
  const cur  = sorted[idx];
  const next = sorted[idx + 1] || null;
  return {
    title:         cur.title,
    description:   cur.desc || '',
    startTime:     cur.start,
    endTime:       cur.stop,
    nextTitle:     next ? next.title : null,
    nextStartTime: next ? next.start : null,
    isServerEPG:   true
  };
}

// ─── Public: Fetch Short EPG for player (per-stream, on channel click) ────────
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
        title:         _decodeB64(cur.title || '') || null,
        description:   _decodeB64(cur.description || '') || '',
        startTime:     cur.start_timestamp ? cur.start_timestamp * 1000 : Date.parse(cur.start),
        endTime:       cur.stop_timestamp  ? cur.stop_timestamp  * 1000 : Date.parse(cur.end),
        nextTitle:     next ? (_decodeB64(next.title || '') || null) : null,
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

// ─── Progress helpers ─────────────────────────────────────────────────────────
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
  const now     = Date.now();
  const start   = new Date(program.startTime).getTime();
  const end     = new Date(program.endTime).getTime();
  if (isNaN(start) || isNaN(end) || end <= start) {
    return { percent: 0, remainingMinutes: 0, formattedStart: '', formattedEnd: '', isLive: false };
  }
  const total     = end - start;
  const elapsed   = Math.max(0, now - start);
  const remaining = Math.max(0, end - now);
  return {
    percent:          Math.min(100, Math.max(0, Math.round((elapsed / total) * 100))),
    remainingMinutes: Math.ceil(remaining / 60000),
    formattedStart:   formatTime(start),
    formattedEnd:     formatTime(end),
    isLive:           now >= start && now <= end
  };
}
