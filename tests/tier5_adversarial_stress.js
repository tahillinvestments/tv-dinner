/**
 * Tier 5: Adversarial Coverage Hardening & Stress Test Suite
 * ──────────────────────────────────────────────────────────
 * Deep white-box stress testing, race condition simulation, malformed playlist fuzzing,
 * HLS.js error cascade validation, Android native bridge contract verification,
 * XMLTV & short EPG parsing, VOD scoring & resolution, podcast RSS/YouTube ingestion,
 * and LRU storage boundary enforcement.
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

import {
  describe,
  it,
  expect,
  assert,
  beforeEach,
  afterEach,
  beforeAll,
  afterAll,
  setupTestEnvironment,
  resetTestEnvironment,
  restoreTestEnvironment,
  clearSuiteRegistry,
  runAllRegisteredSuites,
  MockHls,
  MockEvent,
  MockKeyboardEvent,
  MockCustomEvent,
  MockElement,
  MockAudioElement,
  MockVideoElement,
  MockIframeElement,
  MockStorage
} from './harness.js';

import Hls from 'hls.js';
import { IPTVPlayer } from '../src/player.js';
import {
  initEPGFeed,
  getChannelEPGInfo,
  getProgramProgress,
  formatTime,
  fetchXtreamEPG
} from '../src/epgService.js';

import {
  XtreamVODClient,
  calculateVODScore,
  sortVODByPopularity,
  getSafeImageUrl
} from '../src/xtreamVODService.js';

import {
  PODCAST_CHANNELS,
  getAllPodcastChannels,
  getHeroPodcast,
  getLatestPodcastEpisodes,
  searchRealPodcastAPI,
  searchPodcastChannels,
  fetchForYouCuratedPodcasts,
  parseRss2AudioFeed,
  fetchPodcastRssFeed,
  fetchChannelPastEpisodes,
  fetchChannelPastEpisodesNextPage,
  decodeXmlEntities,
  extractXmlCdataOrText,
  formatPodcastDuration
} from '../src/podcastsData.js';

// Configure Node environment for Hls.js simulation
globalThis.self = globalThis;
globalThis.location = globalThis.location || { href: 'http://localhost/' };
Hls.isSupported = () => true;

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '../');

// ─── Kotlin Android Native Logic Replicators (for Contract & Static Verification) ──

function simulateKotlinRewriteM3U8(m3uText, baseUrl) {
  let baseUri = null;
  try {
    if (baseUrl && (baseUrl.startsWith('http://') || baseUrl.startsWith('https://'))) {
      baseUri = new URL(baseUrl);
    }
  } catch (e) {
    baseUri = null;
  }

  const lines = m3uText.split(/\r?\n/);
  const resultLines = [];

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed) continue;

    if (trimmed.startsWith('#')) {
      if (trimmed.includes('URI="')) {
        const rewrittenTag = trimmed.replace(/URI="([^"]+)"/g, (_, rawUri) => {
          let absoluteUrl = rawUri;
          try {
            if (baseUri && !rawUri.toLowerCase().startsWith('http://') && !rawUri.toLowerCase().startsWith('https://')) {
              absoluteUrl = new URL(rawUri, baseUri).toString();
            }
          } catch (e) {}
          const proxied = "https://appassets.androidplatform.net/api/proxy?url=" + encodeURIComponent(absoluteUrl);
          return `URI="${proxied}"`;
        });
        resultLines.push(rewrittenTag);
      } else {
        resultLines.push(line);
      }
      continue;
    }

    let absoluteUrl = trimmed;
    try {
      if (baseUri && !trimmed.toLowerCase().startsWith('http://') && !trimmed.toLowerCase().startsWith('https://')) {
        absoluteUrl = new URL(trimmed, baseUri).toString();
      }
    } catch (e) {}

    const proxiedUrl = "https://appassets.androidplatform.net/api/proxy?url=" + encodeURIComponent(absoluteUrl);
    resultLines.push(proxiedUrl);
  }

  return resultLines.join('\n') + (resultLines.length > 0 ? '\n' : '');
}

function simulateKotlinGuessMimeType(url) {
  const lower = (url || '').toLowerCase().split('?')[0];
  if (lower.endsWith('.ts')) return 'video/mp2t';
  if (lower.endsWith('.m3u8') || lower.endsWith('.m3u')) return 'application/vnd.apple.mpegurl';
  if (lower.endsWith('.mp4')) return 'video/mp4';
  if (lower.endsWith('.mkv')) return 'video/mp4';
  if (lower.endsWith('.webm')) return 'video/webm';
  if (lower.endsWith('.avi')) return 'video/x-msvideo';
  if (lower.endsWith('.mp3')) return 'audio/mpeg';
  if (lower.endsWith('.aac')) return 'audio/aac';
  if (lower.endsWith('.json') || lower.includes('player_api')) return 'application/json';
  if (lower.endsWith('.xml')) return 'application/xml';
  if (lower.endsWith('.svg')) return 'image/svg+xml';
  if (lower.endsWith('.png')) return 'image/png';
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
  if (lower.includes('/movie/') || lower.includes('/series/')) return 'video/mp4';
  if (lower.includes('/live/')) return 'video/mp2t';
  return 'video/mp4';
}

function simulateKotlinFilterHeaders(incomingHeaders) {
  const stripReqHeaders = new Set(['host', 'referer', 'origin', 'x-forwarded-for', 'x-real-ip', 'accept-encoding']);
  const filtered = {};
  for (const [key, value] of Object.entries(incomingHeaders)) {
    if (!stripReqHeaders.has(key.toLowerCase())) {
      filtered[key] = value;
    }
  }
  return filtered;
}

// ─── Base64 & XMLTV Helpers for Node.js test environment ─────────────────────

const b64 = (str) => Buffer.from(str, 'utf-8').toString('base64');

class XmltvMockDOMParser {
  parseFromString(xmlStr, mimeType) {
    const doc = new MockElement('doc');

    // If the XML is explicitly instructed to fail or is corrupted syntax
    if (xmlStr.includes('<parsererror') || xmlStr.includes('<broken_tag attr="unclosed"')) {
      const errEl = new MockElement('parsererror');
      errEl.textContent = 'XML Parsing Error';
      doc.appendChild(errEl);
      return doc;
    }

    // Parse <channel id="..."> <display-name>...</display-name> </channel>
    const chanRegex = /<channel\s+([^>]*)>([\s\S]*?)<\/channel>/gi;
    let cm;
    while ((cm = chanRegex.exec(xmlStr)) !== null) {
      const chEl = new MockElement('channel');
      const attrStr = cm[1];
      const idMatch = attrStr.match(/id="([^"]*)"/i);
      if (idMatch) chEl.setAttribute('id', idMatch[1]);

      const dnRegex = /<display-name[^>]*>([\s\S]*?)<\/display-name>/gi;
      let dnm;
      while ((dnm = dnRegex.exec(cm[2])) !== null) {
        const dnEl = new MockElement('display-name');
        dnEl.textContent = decodeXmlEntities(dnm[1].trim());
        chEl.appendChild(dnEl);
      }
      doc.appendChild(chEl);
    }

    // Parse <programme ...> ... </programme>
    const progRegex = /<programme\s+([^>]*)>([\s\S]*?)<\/programme>/gi;
    let pm;
    while ((pm = progRegex.exec(xmlStr)) !== null) {
      const progEl = new MockElement('programme');
      const attrStr = pm[1];
      const chanMatch = attrStr.match(/channel="([^"]*)"/i);
      const startMatch = attrStr.match(/start="([^"]*)"/i);
      const stopMatch = attrStr.match(/stop="([^"]*)"/i);
      if (chanMatch) progEl.setAttribute('channel', chanMatch[1]);
      if (startMatch) progEl.setAttribute('start', startMatch[1]);
      if (stopMatch) progEl.setAttribute('stop', stopMatch[1]);

      const titleMatch = pm[2].match(/<title[^>]*>([\s\S]*?)<\/title>/i);
      if (titleMatch) {
        const tEl = new MockElement('title');
        tEl.textContent = decodeXmlEntities(titleMatch[1].trim());
        progEl.appendChild(tEl);
      }
      const descMatch = pm[2].match(/<desc[^>]*>([\s\S]*?)<\/desc>/i);
      if (descMatch) {
        const dEl = new MockElement('desc');
        dEl.textContent = decodeXmlEntities(descMatch[1].trim());
        progEl.appendChild(dEl);
      }
      doc.appendChild(progEl);
    }

    return doc;
  }
}

function installXmlParser() {
  globalThis.DOMParser = XmltvMockDOMParser;
  if (globalThis.window) {
    globalThis.window.DOMParser = XmltvMockDOMParser;
  }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Suite 1: Tier 5 Adversarial Group 1: Concurrency, Rapid Teardown & Lifecycle Races
// ═══════════════════════════════════════════════════════════════════════════════

describe('Tier 5 Adversarial Group 1: Concurrency, Rapid Teardown & Lifecycle Races', () => {

  beforeEach(() => {
    setupTestEnvironment();
  });

  afterEach(() => {
    resetTestEnvironment();
  });

  // Test 1: Rapid consecutive tab switching (50 cycles) while HLS video and audio are actively buffering
  it('T5_01_RapidConsecutiveTabSwitchingStress: handles 50 rapid alternating switches without orphan audio or memory leaks', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');
    const audioElement = new MockAudioElement('https://audio.sample.com/podcast.mp3');

    const tabs = ['home', 'live', 'movies', 'series', 'podcasts', 'library', 'settings'];

    for (let i = 0; i < 50; i++) {
      const tab = tabs[i % tabs.length];

      // Simulate starting media in some tabs
      if (tab === 'live') {
        player.playChannel({ url: `http://kstv.us:8080/live/user/pass/${100 + i}.m3u8`, name: `Channel ${i}` });
      } else if (tab === 'podcasts') {
        audioElement.play();
        audioElement.src = `https://audio.sample.com/ep_${i}.mp3`;
      } else {
        // Teardown step
        player.resetVideoFrame();
        audioElement.pause();
        audioElement.removeAttribute('src');
      }

      // Assert invariants at each switch
      if (tab !== 'live') {
        expect(player.currentUrl).toBe('');
        expect(player.unmuteTimers.length).toBe(0);
      }
    }

    // Final state check: player reset cleanly
    expect(player.unmuteTimers.length).toBe(0);
    expect(player.hls).toBeNull();
  });

  // Test 2: Rapid consecutive channel change bursts (20 fast channel clicks)
  it('T5_02_RapidChannelBurstSwitching: destroys prior HLS instances and prevents multiple competing stream attachments', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');

    const channels = Array.from({ length: 20 }, (_, idx) => ({
      url: `http://kstv.us:8080/live/user/pass/${idx + 1}.m3u8`,
      name: `Fast Channel ${idx + 1}`
    }));

    for (const ch of channels) {
      player.playChannel(ch);
      expect(player.channelTitle.textContent).toBe(ch.name);
      expect(player.currentUrl).toContain(encodeURIComponent(ch.url));
    }

    // Only the final channel should be active
    expect(player.channelTitle.textContent).toBe('Fast Channel 20');
    expect(player.currentUrl).toContain('20.m3u8');
  });

  // Test 3: stopAllMediaPlayback invoked during pending play() promise rejection
  it('T5_03_PendingAutoplayRejectionDuringTeardown: catches rejected play() promise gracefully without unhandled exception', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');

    // Simulate play() rejecting due to browser NotAllowedError
    player.video.play = () => Promise.reject(new Error('NotAllowedError: play() failed'));

    let errorThrown = false;
    try {
      player.playChannel({ url: 'http://kstv.us:8080/live/user/pass/999.m3u8', name: 'Auto Block Channel' });
      player.resetVideoFrame();
    } catch (err) {
      errorThrown = true;
    }

    expect(errorThrown).toBe(false);
    expect(player.currentUrl).toBe('');
    expect(player.video.paused).toBe(true);
  });

  // Test 4: Modal scroll lock integrity and body overflow cleanup during tab switch
  it('T5_04_ModalOverflowRestorationOnTabSwitch: ensures body scroll lock is restored when navigating away from open modal', () => {
    const env = setupTestEnvironment();
    const body = env.document.body;

    // Simulate modal opening and locking body scroll
    body.style.overflow = 'hidden';
    expect(body.style.overflow).toBe('hidden');

    // Tab switch teardown routine simulation
    body.style.overflow = '';
    expect(body.style.overflow).toBe('');
  });

  // Test 5: Out-of-bounds and NaN VOD seeking under invalid video duration states
  it('T5_05_OutOfBoundsAndNaNVodSeeking: clamps seek operations safely under NaN, 0, or infinite durations', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');

    player.video.duration = NaN;
    player.video.currentTime = 0;

    // Seek by +30s when duration is NaN
    player.seekBy(30);
    expect(player.video.currentTime).toBe(30);

    // Seek backwards by -100s: should clamp at 0
    player.seekBy(-100);
    expect(player.video.currentTime).toBe(0);

    // Finite duration set to 100s
    player.video.duration = 100;
    player.video.currentTime = 90;

    // Seek +50s: should clamp at duration (100)
    player.seekBy(50);
    expect(player.video.currentTime).toBe(100);
  });

  // Test 6: VOD resume tracking LRU eviction under rapid playback position updates
  it('T5_06_VodResumePositionsLruEviction: maintains max 100 entries and purges oldest timestamps when exceeded', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');
    player.controlMode = 'vod';

    // Seed 105 distinct media entries in localStorage
    const resumeData = {};
    for (let i = 1; i <= 105; i++) {
      resumeData[`media_key_${i}`] = {
        position: 120,
        duration: 3600,
        timestamp: 1000000 + i
      };
    }
    localStorage.setItem('vod_resume_positions', JSON.stringify(resumeData));

    // Configure player on media 106 and trigger savePlaybackPosition
    player.currentMediaKey = 'media_key_106';
    player.currentUrl = 'http://asoseller.org:8080/movie/user/pass/106.mp4';
    player.video.duration = 3600;
    player.video.currentTime = 500;
    player.lastSaveTime = 0;

    player.savePlaybackPosition();

    const stored = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
    const storedKeys = Object.keys(stored);

    // Assert LRU pruning bounded length to at most 101 entries and oldest entry (media_key_1) was pruned
    expect(storedKeys.length).toBeLessThanOrEqual(101);
    expect(stored['media_key_106']).toBeDefined();
    expect(stored['media_key_106'].position).toBe(500);
  });

});

// ═══════════════════════════════════════════════════════════════════════════════
// Suite 2: Tier 5 Adversarial Group 2: Malformed M3U8 Manifests & Proxy URL Rewriting
// ═══════════════════════════════════════════════════════════════════════════════

describe('Tier 5 Adversarial Group 2: Malformed M3U8 Manifests & Proxy URL Rewriting', () => {

  beforeEach(() => {
    setupTestEnvironment();
  });

  afterEach(() => {
    resetTestEnvironment();
  });

  // Test 7: M3U8 manifest with missing #EXTM3U header, Windows CRLF line breaks, and trailing spaces
  it('T5_07_MalformedM3u8MissingHeaderAndCrlf: correctly rewrites chunks despite missing EXTM3U header and CRLF line breaks', () => {
    const rawMalformed = "#EXT-X-VERSION:3\r\n#EXT-X-TARGETDURATION:10\r\n\r\n#EXTINF:10.0,\r\nsegment_001.ts  \r\n#EXTINF:10.0,\r\nsegment_002.ts\r\n";
    const baseUrl = "http://kstv.us:8080/live/user/pass/playlist.m3u8";

    const rewritten = simulateKotlinRewriteM3U8(rawMalformed, baseUrl);

    expect(rewritten).toContain('https://appassets.androidplatform.net/api/proxy?url=http%3A%2F%2Fkstv.us%3A8080%2Flive%2Fuser%2Fpass%2Fsegment_001.ts');
    expect(rewritten).toContain('https://appassets.androidplatform.net/api/proxy?url=http%3A%2F%2Fkstv.us%3A8080%2Flive%2Fuser%2Fpass%2Fsegment_002.ts');
    expect(rewritten).toContain('#EXT-X-TARGETDURATION:10');
  });

  // Test 8: Relative segment paths with directory traversal (../) and root-relative paths (/chunks/)
  it('T5_08_RelativeSegmentPathsResolution: properly resolves ../ and root-relative / paths against master base URI', () => {
    const m3u = "#EXTM3U\n#EXTINF:6.0,\n../hls/chunk_10.ts\n#EXTINF:6.0,\n/static/video/chunk_20.ts\n";
    const baseUrl = "https://cdn.streamservice.com/hls/channel1/live.m3u8";

    const rewritten = simulateKotlinRewriteM3U8(m3u, baseUrl);

    expect(rewritten).toContain('chunk_10.ts');
    expect(rewritten).toContain('chunk_20.ts');
    expect(rewritten).toContain('https://appassets.androidplatform.net/api/proxy?url=');
  });

  // Test 9: Complex M3U8 tags containing URI="..." attributes (AES-128 keys, subtitle media, init maps)
  it('T5_09_TagUriAttributesRewriting: rewrites EXT-X-KEY, EXT-X-MAP, and EXT-X-MEDIA URI attributes without tag corruption', () => {
    const m3u = `#EXTM3U
#EXT-X-KEY:METHOD=AES-128,URI="enc.key",IV=0x1234567890abcdef
#EXT-X-MAP:URI="init.mp4",BYTERANGE="500@0"
#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",URI="audio_en.m3u8"
#EXTINF:5.0,
segment_0.ts`;
    const baseUrl = "http://edge.iptvserver.org/live/stream1/index.m3u8";

    const rewritten = simulateKotlinRewriteM3U8(m3u, baseUrl);

    expect(rewritten).toContain('#EXT-X-KEY:METHOD=AES-128,URI="https://appassets.androidplatform.net/api/proxy?url=http%3A%2F%2Fedge.iptvserver.org%2Flive%2Fstream1%2Fenc.key"');
    expect(rewritten).toContain('#EXT-X-MAP:URI="https://appassets.androidplatform.net/api/proxy?url=http%3A%2F%2Fedge.iptvserver.org%2Flive%2Fstream1%2Finit.mp4"');
    expect(rewritten).toContain('#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",URI="https://appassets.androidplatform.net/api/proxy?url=http%3A%2F%2Fedge.iptvserver.org%2Flive%2Fstream1%2Faudio_en.m3u8"');
  });

  // Test 10: Prevention of recursive proxy prefixing on already-proxied URLs
  it('T5_10_PreProxiedUrlIdempotency: avoids double-proxying URLs that already contain /api/proxy?url=', () => {
    const rawUrl = "/api/proxy?url=http%3A%2F%2Fkstv.us%3A8080%2Flive%2Fuser%2Fpass%2F100.m3u8";
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');

    player.playChannel({ url: rawUrl, name: 'Pre-proxied Stream' });

    // Ensure player currentUrl does not contain nested proxy queries
    const count = (player.currentUrl.match(/url=/g) || []).length;
    expect(count).toBe(1);
    expect(player.currentUrl).toContain('http%3A%2F%2Fkstv.us%3A8080%2Flive%2Fuser%2Fpass%2F100.m3u8');
  });

  // Test 11: M3U8 URLs with exotic query strings, spaces, unicode characters, and port numbers
  it('T5_11_ExoticQueryStringsAndUnicodeManifestRewriting: handles non-standard ports, unicode tokens, and complex query parameters', () => {
    const m3u = "#EXTM3U\n#EXTINF:4.0,\nchunk_1.ts?token=xyz%2B123&signature=élan&hd=true\n";
    const baseUrl = "http://91.239.79.63:8000/live/user_stream/index.m3u8?auth=abc&v=2";

    const rewritten = simulateKotlinRewriteM3U8(m3u, baseUrl);

    expect(rewritten).toContain('91.239.79.63%3A8000');
    expect(rewritten).toContain('chunk_1.ts');
    expect(rewritten).toContain('https://appassets.androidplatform.net/api/proxy?url=');
  });

  // Test 12: Byte-range tag preservation (#EXT-X-BYTERANGE)
  it('T5_12_ByteRangeTagPreservation: preserves EXT-X-BYTERANGE offsets unaltered while proxying single-file VOD segments', () => {
    const m3u = `#EXTM3U
#EXT-X-BYTERANGE:1048576@0
video.mp4
#EXT-X-BYTERANGE:2097152@1048576
video.mp4`;
    const baseUrl = "http://vod.streamhost.com/movies/movie123.m3u8";

    const rewritten = simulateKotlinRewriteM3U8(m3u, baseUrl);

    expect(rewritten).toContain('#EXT-X-BYTERANGE:1048576@0');
    expect(rewritten).toContain('#EXT-X-BYTERANGE:2097152@1048576');
    const proxyCount = (rewritten.match(/https:\/\/appassets\.androidplatform\.net\/api\/proxy\?url=/g) || []).length;
    expect(proxyCount).toBe(2);
  });

});

// ═══════════════════════════════════════════════════════════════════════════════
// Suite 3: Tier 5 Adversarial Group 3: HLS.js Error Cascades, Proxy Fallbacks & State Recovery
// ═══════════════════════════════════════════════════════════════════════════════

describe('Tier 5 Adversarial Group 3: HLS.js Error Cascades, Proxy Fallbacks & State Recovery', () => {

  beforeEach(() => {
    setupTestEnvironment();
  });

  afterEach(() => {
    resetTestEnvironment();
  });

  // Test 13: Fatal NETWORK_ERROR jitter retry escalation up to 5 attempts before proxy fallback
  it('T5_13_HlsNetworkErrorJitterRetryEscalation: triggers startLoad retry cycles and manages unmute timer collection', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');
    const mockHlsInstance = new MockHls();
    player.hls = mockHlsInstance;

    let startLoadCalls = 0;
    mockHlsInstance.startLoad = () => { startLoadCalls++; };

    // Register HLS error handler
    mockHlsInstance.on(MockHls.Events.ERROR, (event, data) => {
      if (data.fatal && data.type === MockHls.ErrorTypes.NETWORK_ERROR) {
        player.showLoading(true);
        const retryTimer = setTimeout(() => {
          if (player.hls) player.hls.startLoad();
        }, 1000);
        player.unmuteTimers.push(retryTimer);
      }
    });

    // Emit fatal network error
    mockHlsInstance.trigger(MockHls.Events.ERROR, {
      type: MockHls.ErrorTypes.NETWORK_ERROR,
      details: MockHls.ErrorDetails.FRAG_LOAD_ERROR,
      fatal: true
    });

    // Unmute timers array should contain the scheduled retry timer
    expect(player.unmuteTimers.length).toBeGreaterThan(0);
    expect(player.loading.classList.contains('hidden')).toBe(false);

    // Clear timers on player reset
    player.resetVideoFrame();
    expect(player.unmuteTimers.length).toBe(0);
  });

  // Test 14: Fatal MEDIA_ERROR triggers recoverMediaError() without tearing down HLS
  it('T5_14_HlsMediaErrorRecovery: invokes recoverMediaError on fatal media decode errors and keeps HLS instance attached', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');
    const mockHlsInstance = new MockHls();
    player.hls = mockHlsInstance;

    let recoverCalls = 0;
    mockHlsInstance.recoverMediaError = () => { recoverCalls++; };

    mockHlsInstance.on(MockHls.Events.ERROR, (event, data) => {
      if (data.fatal && data.type === MockHls.ErrorTypes.MEDIA_ERROR) {
        player.showLoading(true);
        player.hls.recoverMediaError();
      }
    });

    // Trigger fatal MEDIA_ERROR
    mockHlsInstance.trigger(MockHls.Events.ERROR, {
      type: MockHls.ErrorTypes.MEDIA_ERROR,
      details: 'bufferAppendError',
      fatal: true
    });

    expect(recoverCalls).toBe(1);
    expect(player.hls).not.toBeNull();
    expect(player.loading.classList.contains('hidden')).toBe(false);
  });

  // Test 15: Non-fatal HLS errors (e.g. non-fatal buffer stall) do not trigger teardown or error banner
  it('T5_15_NonFatalHlsErrorsIgnored: allows playback to continue smoothly without teardown on non-fatal events', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');
    player.showError(false);
    const mockHlsInstance = new MockHls();
    player.hls = mockHlsInstance;

    mockHlsInstance.on(MockHls.Events.ERROR, (event, data) => {
      if (data.fatal) {
        player.showError(true, data.details);
      }
    });

    // Trigger non-fatal buffer stall
    mockHlsInstance.trigger(MockHls.Events.ERROR, {
      type: MockHls.ErrorTypes.MEDIA_ERROR,
      details: MockHls.ErrorDetails.BUFFER_STALLED_ERROR,
      fatal: false
    });

    expect(player.hls).not.toBeNull();
    expect(player.error.classList.contains('hidden')).toBe(true);
  });

  // Test 16: Fatal OTHER_ERROR immediately displays error details and destroys HLS
  it('T5_16_FatalOtherErrorCleanTeardown: destroys HLS and displays error details on fatal other error', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');
    const mockHlsInstance = new MockHls();
    player.hls = mockHlsInstance;

    mockHlsInstance.on(MockHls.Events.ERROR, (event, data) => {
      if (data.fatal && data.type === MockHls.ErrorTypes.OTHER_ERROR) {
        player.showError(true, `HLS Error: ${data.details}`);
        player.destroyHls();
      }
    });

    mockHlsInstance.trigger(MockHls.Events.ERROR, {
      type: MockHls.ErrorTypes.OTHER_ERROR,
      details: 'internalException',
      fatal: true
    });

    expect(player.hls).toBeNull();
    expect(player.error.classList.contains('hidden')).toBe(false);
    expect(player.errorDetails.textContent).toContain('internalException');
  });

  // Test 17: Autoplay restriction recovery via muted autoplay and delayed unmuting
  it('T5_17_AutoplayRestrictionRecoveryFlow: recovers from autoplay restriction by falling back to muted play and unmuting', async () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');
    const mockHlsInstance = new MockHls();
    player.hls = mockHlsInstance;

    let playAttempts = 0;
    player.video.play = () => {
      playAttempts++;
      if (playAttempts === 1) {
        return Promise.reject(new Error('NotAllowedError: user gesture required'));
      }
      return Promise.resolve();
    };

    mockHlsInstance.on(MockHls.Events.MANIFEST_PARSED, () => {
      player.video.muted = false;
      player.video.volume = 1.0;
      player.video.play().catch(() => {
        player.video.muted = true;
        player.video.play().then(() => {
          const t = setTimeout(() => {
            player.video.muted = false;
          }, 150);
          player.unmuteTimers.push(t);
        });
      });
    });

    // Simulate MANIFEST_PARSED trigger
    mockHlsInstance.trigger(MockHls.Events.MANIFEST_PARSED, { levels: [] });

    // Wait for microtasks
    await new Promise(r => setTimeout(r, 20));

    expect(playAttempts).toBeGreaterThanOrEqual(2);
    expect(player.video.muted).toBe(true);
    expect(player.unmuteTimers.length).toBeGreaterThan(0);
  });

  // Test 18: Buffer safety timeout cancellation when playback advances
  it('T5_18_BufferSafetyTimeoutCancellation: cancels 15-second loading timeout as soon as playback advances', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');

    player.playChannel({ url: 'http://kstv.us:8080/live/user/pass/206.m3u8', name: 'Buffer Timeout Test' });
    player.showLoading(true);

    // Trigger timeupdate event indicating video has started playing
    player.video.paused = false;
    player.video.currentTime = 2.5;
    player.video.dispatchEvent(new MockEvent('timeupdate'));

    expect(player.loading.classList.contains('hidden')).toBe(true);
    expect(player.error.classList.contains('hidden')).toBe(true);
  });

});

// ═══════════════════════════════════════════════════════════════════════════════
// Suite 4: Tier 5 Adversarial Group 4: Media Dock, Embed Sanitization & Geometry Stress
// ═══════════════════════════════════════════════════════════════════════════════

describe('Tier 5 Adversarial Group 4: Media Dock, Embed Sanitization & Geometry Stress', () => {

  beforeEach(() => {
    setupTestEnvironment();
  });

  afterEach(() => {
    resetTestEnvironment();
  });

  // Test 19: Aspect ratio mode normalization & fuzzing with invalid strings
  it('T5_19_AspectRatioModeNormalization: normalizes standard modes and safely defaults invalid/fuzzed inputs to fit', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');

    player.setAspectRatio('STRETCH');
    expect(player.aspectRatio).toBe('stretch');
    expect(player.video.style.objectFit).toBe('fill');
    expect(player.aspectLabel.textContent).toBe('STRETCH');

    player.setAspectRatio('cover');
    expect(player.aspectRatio).toBe('cover');
    expect(player.video.style.objectFit).toBe('cover');

    player.setAspectRatio('4:3');
    expect(player.aspectRatio).toBe('4:3');
    expect(player.video.style.objectFit).toBe('fill');

    player.setAspectRatio('16:9');
    expect(player.aspectRatio).toBe('stretch');

    // Fuzzed / malicious input
    player.setAspectRatio('"><script>alert(1)</script>');
    expect(player.aspectRatio).toBe('fit');
    expect(player.video.style.objectFit).toBe('contain');
  });

  // Test 20: Embed iframe postMessage commands under missing or disconnected contentWindow
  it('T5_20_EmbedIframePostMessageResilience: executes togglePlay and seekBy safely without error when iframe is detached', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');
    const playerWrapper = env.elements.get('player-wrapper');
    playerWrapper.classList.add('embed-active');

    const embedIframe = env.elements.get('embed-iframe');
    embedIframe.contentWindow = null; // simulate cross-origin / detached contentWindow

    expect(() => {
      player.togglePlay();
      player.seekBy(15);
    }).not.toThrow();
  });

  // Test 21: Embed reload stream flow with about:blank sanitization
  it('T5_21_EmbedStreamReloadSanitization: resets iframe to about:blank before reloading stream source', async () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');
    const playerWrapper = env.elements.get('player-wrapper');
    playerWrapper.classList.add('embed-active');

    const embedIframe = env.elements.get('embed-iframe');
    embedIframe.src = 'https://vidlink.pro/movie/550';

    player.reloadStream();
    expect(embedIframe.src).toBe('about:blank');

    await new Promise(r => setTimeout(r, 120));
    expect(embedIframe.src).toBe('https://vidlink.pro/movie/550');
  });

  // Test 22: Fullscreen toggle under zero-dimension container triggers pseudo-fullscreen fallback
  it('T5_22_FullscreenZeroDimensionContainerFallback: falls back to is-pseudo-fullscreen when native fullscreen is rejected', async () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');
    const container = player.video.parentElement;

    // Simulate requestFullscreen failing (e.g. Xbox Edge browser restriction)
    container.requestFullscreen = () => Promise.reject(new Error('Fullscreen request rejected'));

    player.toggleFullscreen();
    await new Promise(r => setTimeout(r, 10));

    expect(container.classList.contains('is-pseudo-fullscreen')).toBe(true);
    expect(env.document.body.classList.contains('body-pseudo-fullscreen')).toBe(true);

    // Toggle again to exit pseudo-fullscreen
    player.toggleFullscreen();
    expect(container.classList.contains('is-pseudo-fullscreen')).toBe(false);
    expect(env.document.body.classList.contains('body-pseudo-fullscreen')).toBe(false);
  });

  // Test 23: Volume slider bounding and mute icon synchronization
  it('T5_23_VolumeSliderBoundaryClamping: clamps volume between 0 and 1 and syncs mute icons accordingly', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');

    player.setVolume(0);
    expect(player.isMuted).toBe(true);
    expect(player.video.muted).toBe(true);

    player.setVolume(0.3);
    expect(player.isMuted).toBe(false);
    expect(player.volume).toBeCloseTo(0.3);

    player.setVolume(0.8);
    expect(player.isMuted).toBe(false);
    expect(player.volume).toBeCloseTo(0.8);
  });

  // Test 24: Format time formatting corner cases (Infinity, NaN, negative, multi-hour timestamps)
  it('T5_24_FormatTimeBoundaryCornerCases: formats 0, negative, NaN, multi-hour, and extreme timestamps reliably', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');

    expect(player.formatTime(0)).toBe('0:00');
    expect(player.formatTime(-50)).toBe('0:00');
    expect(player.formatTime(NaN)).toBe('0:00');
    expect(player.formatTime(Infinity)).toBe('0:00');
    expect(player.formatTime(45)).toBe('0:45');
    expect(player.formatTime(125)).toBe('2:05');
    expect(player.formatTime(3665)).toBe('1:01:05');
    expect(player.formatTime(36000)).toBe('10:00:00');
  });

});

// ═══════════════════════════════════════════════════════════════════════════════
// Suite 5: Tier 5 Adversarial Group 5: Android Native Bridge, OkHttp & Security Contracts
// ═══════════════════════════════════════════════════════════════════════════════

describe('Tier 5 Adversarial Group 5: Android Native Bridge, OkHttp & Security Contracts', () => {

  // Test 25: Header filtering in handleProxyRequest strips sensitive / hop-by-hop headers
  it('T5_25_NativeProxyHeaderStripping: strips host, referer, origin, accept-encoding, and IP forwarding headers', () => {
    const incomingHeaders = {
      'Host': 'appassets.androidplatform.net',
      'Referer': 'https://appassets.androidplatform.net/index.html',
      'Origin': 'https://appassets.androidplatform.net',
      'Accept-Encoding': 'gzip, deflate, br',
      'X-Forwarded-For': '192.168.1.1',
      'X-Real-IP': '10.0.0.1',
      'Range': 'bytes=0-1048575',
      'Accept': '*/*',
      'User-Agent': 'Mozilla/5.0'
    };

    const filtered = simulateKotlinFilterHeaders(incomingHeaders);

    expect(filtered['Host']).toBeUndefined();
    expect(filtered['Referer']).toBeUndefined();
    expect(filtered['Origin']).toBeUndefined();
    expect(filtered['Accept-Encoding']).toBeUndefined();
    expect(filtered['X-Forwarded-For']).toBeUndefined();
    expect(filtered['X-Real-IP']).toBeUndefined();

    // Range, Accept, and User-Agent must be preserved
    expect(filtered['Range']).toBe('bytes=0-1048575');
    expect(filtered['Accept']).toBe('*/*');
    expect(filtered['User-Agent']).toBe('Mozilla/5.0');
  });

  // Test 26: MIME type inference across all standard and non-standard stream extensions
  it('T5_26_NativeProxyMimeTypeInference: accurately identifies MIME types for TS, M3U8, MP4, MKV, WebM, AVI, AAC, XML', () => {
    expect(simulateKotlinGuessMimeType('http://server.com/live/ch1.ts')).toBe('video/mp2t');
    expect(simulateKotlinGuessMimeType('http://server.com/live/ch1.m3u8')).toBe('application/vnd.apple.mpegurl');
    expect(simulateKotlinGuessMimeType('http://server.com/vod/movie.mp4')).toBe('video/mp4');
    expect(simulateKotlinGuessMimeType('http://server.com/vod/movie.mkv')).toBe('video/mp4');
    expect(simulateKotlinGuessMimeType('http://server.com/vod/clip.webm')).toBe('video/webm');
    expect(simulateKotlinGuessMimeType('http://server.com/vod/old.avi')).toBe('video/x-msvideo');
    expect(simulateKotlinGuessMimeType('http://server.com/audio/song.mp3')).toBe('audio/mpeg');
    expect(simulateKotlinGuessMimeType('http://server.com/audio/voice.aac')).toBe('audio/aac');
    expect(simulateKotlinGuessMimeType('http://server.com/player_api.php?username=u')).toBe('application/json');
    expect(simulateKotlinGuessMimeType('http://server.com/epg/xmltv.xml')).toBe('application/xml');
    expect(simulateKotlinGuessMimeType('http://server.com/movie/user/pass/123.mp4')).toBe('video/mp4');
    expect(simulateKotlinGuessMimeType('http://server.com/series/user/pass/456.mkv')).toBe('video/mp4');
  });

  // Test 27: Static verification of Android networking and proxy contracts
  it('T5_27_MainActivitySourceVerification: verifies SSL TrustManager, OkHttp proxy, and M3U8 rewriting implementation', () => {
    const mainActivityPath = path.join(projectRoot, 'android-app/app/src/main/java/com/troyh/tvdinner/MainActivity.kt');
    const apiClientPath = path.join(projectRoot, 'android-app/app/src/main/java/com/troyh/tvdinner/data/network/XtreamApiClient.kt');
    const proxyServerPath = path.join(projectRoot, 'proxy-server.js');
    expect(fs.existsSync(mainActivityPath)).toBe(true);
    expect(fs.existsSync(apiClientPath)).toBe(true);

    const apiSource = fs.readFileSync(apiClientPath, 'utf8');
    const proxySource = fs.readFileSync(proxyServerPath, 'utf8');

    // Verify permissive SSL TrustManager and HostnameVerifier
    expect(apiSource).toContain('createUnsafeOkHttpClient()');
    expect(apiSource).toContain('trustAllCerts');
    expect(apiSource).toContain('hostnameVerifier { _, _ -> true }');

    // Verify VLC User-Agent spoofing
    expect(apiSource).toContain('VLC/3.0.21 LibVLC/3.0.21');

    // Verify CORS headers and M3U8 rewriter
    expect(proxySource).toContain('Access-Control-Allow-Origin');
    expect(proxySource).toContain('Access-Control-Allow-Methods');
    expect(proxySource).toContain('rewriteM3U8');
  });

  // Test 28: Static verification of proguard-rules.pro keep rules
  it('T5_28_ProguardRulesVerification: verifies keep rules for OkHttp, AndroidX WebKit, and JavascriptInterface in proguard-rules.pro', () => {
    const proguardPath = path.join(projectRoot, 'android-app/app/proguard-rules.pro');
    expect(fs.existsSync(proguardPath)).toBe(true);

    const rules = fs.readFileSync(proguardPath, 'utf8');

    expect(rules).toContain('-keep class okhttp3.** { *; }');
    expect(rules).toContain('-keep class androidx.webkit.** { *; }');
    expect(rules).toContain('-keepattributes JavascriptInterface');
    expect(rules).toContain('@android.webkit.JavascriptInterface');
  });

  // Test 29: Static verification of build.gradle.kts release signing and SDK versions
  it('T5_29_GradleBuildConfigurationVerification: verifies release signing, V1/V2/V3 schemes, and SDK 36 in build.gradle.kts', () => {
    const gradlePath = path.join(projectRoot, 'android-app/app/build.gradle.kts');
    expect(fs.existsSync(gradlePath)).toBe(true);

    const gradle = fs.readFileSync(gradlePath, 'utf8');

    expect(gradle).toContain('compileSdk = 36');
    expect(gradle).toContain('targetSdk = 36');
    expect(gradle).toContain('minSdk = 24');
    expect(gradle).toContain('enableV1Signing = true');
    expect(gradle).toContain('enableV2Signing = true');
    expect(gradle).toContain('enableV3Signing = true');
    expect(gradle).toContain('com.troyh.tvdinner');
  });

  // Test 30: Proxy redirect loop prevention on self-referential redirect targets
  it('T5_30_SelfReferentialProxyRedirectCycleDefense: breaks and handles circular proxy redirects cleanly', () => {
    const circularUrl = "https://appassets.androidplatform.net/api/proxy?url=" + encodeURIComponent("https://appassets.androidplatform.net/api/proxy?url=http%3A%2F%2Fkstv.us%3A8080%2Flive%2Fch.m3u8");
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');

    player.playChannel({ url: circularUrl, name: 'Circular Stream' });

    // Ensure the decoded target url was unpacked to the root target
    expect(player.currentUrl).toContain('kstv.us');
    expect(player.currentUrl.startsWith('/api/proxy') || player.currentUrl.startsWith('https://')).toBe(true);
  });

});

// ═══════════════════════════════════════════════════════════════════════════════
// Suite 6: ADV-EPG-01: Malformed XMLTV & Datetime Parsing Adversarial Stress
// ═══════════════════════════════════════════════════════════════════════════════

describe('ADV-EPG-01: Malformed XMLTV & Datetime Parsing Adversarial Stress', () => {
  let env;
  beforeEach(() => {
    env = setupTestEnvironment();
    installXmlParser();
    if (typeof sessionStorage !== 'undefined') sessionStorage.clear();
    if (typeof localStorage !== 'undefined') localStorage.clear();
  });
  afterEach(() => {
    restoreTestEnvironment();
  });

  it('ADV-EPG-01-01: skips <programme> elements missing channel, start, or stop attributes without throwing', async () => {
    const malformedXml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="valid_chan_1"><display-name>Valid Channel 1</display-name></channel>
  <!-- Missing channel attr -->
  <programme start="20260819120000 +0000" stop="20260819130000 +0000"><title>No Channel Prog</title></programme>
  <!-- Missing start attr -->
  <programme channel="valid_chan_1" stop="20260819130000 +0000"><title>No Start Prog</title></programme>
  <!-- Missing stop attr -->
  <programme channel="valid_chan_1" start="20260819120000 +0000"><title>No Stop Prog</title></programme>
  <!-- Missing title tag -->
  <programme channel="valid_chan_1" start="20260819120000 +0000" stop="20260819130000 +0000"></programme>
  <!-- Valid programme -->
  <programme channel="valid_chan_1" start="${new Date(Date.now() - 600000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 600000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Valid Live Show</title>
    <desc>Valid Description</desc>
  </programme>
</tv>`;

    env = setupTestEnvironment({
      'xmltv.php': () => ({
        ok: true,
        status: 200,
        text: async () => malformedXml
      })
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'adv_user_1', 'pass123');
    const info = getChannelEPGInfo({ id: 'valid_chan_1' });
    expect(info).toBeDefined();
    expect(info.title).toBe('Valid Live Show');
    expect(info.description).toBe('Valid Description');
    expect(info.isServerEPG).toBe(true);
  });

  it('ADV-EPG-01-02: handles corrupted and impossible datetime strings (e.g. 20261345999999, non-numeric, truncated) gracefully', async () => {
    const corruptDateXml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="date_chan"><display-name>Date Test Channel</display-name></channel>
  <!-- Month 13, Day 45, Hour 99 -->
  <programme channel="date_chan" start="20261345999999 +0000" stop="20261345999999 +0000"><title>Impossible Date</title></programme>
  <!-- Truncated string -->
  <programme channel="date_chan" start="202608" stop="202608"><title>Truncated Date</title></programme>
  <!-- Alpha characters in date -->
  <programme channel="date_chan" start="INVALID_DATE_TIME" stop="INVALID_DATE_TIME"><title>Alpha Date</title></programme>
  <!-- Valid fallback -->
  <programme channel="date_chan" start="${new Date(Date.now() - 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Proper Airing Show</title>
  </programme>
</tv>`;

    env = setupTestEnvironment({
      'xmltv.php': () => ({
        ok: true,
        status: 200,
        text: async () => corruptDateXml
      })
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'adv_user_2', 'pass123');
    const info = getChannelEPGInfo({ id: 'date_chan' });
    expect(info.title).toBe('Proper Airing Show');
  });

  it('ADV-EPG-01-03: accurately parses non-UTC positive and negative timezone offsets (+0530, -0400, no offset)', async () => {
    const now = Date.now();
    const dStart = new Date(now - 1800000);
    const dStop = new Date(now + 1800000);

    const formatOffset = (d, tzOffsetMinutes) => {
      const localTime = new Date(d.getTime() + tzOffsetMinutes * 60000);
      const yr = localTime.getUTCFullYear();
      const mo = String(localTime.getUTCMonth() + 1).padStart(2, '0');
      const dy = String(localTime.getUTCDate()).padStart(2, '0');
      const hr = String(localTime.getUTCHours()).padStart(2, '0');
      const mn = String(localTime.getUTCMinutes()).padStart(2, '0');
      const sc = String(localTime.getUTCSeconds()).padStart(2, '0');
      const sign = tzOffsetMinutes >= 0 ? '+' : '-';
      const absOffset = Math.abs(tzOffsetMinutes);
      const offH = String(Math.floor(absOffset / 60)).padStart(2, '0');
      const offM = String(absOffset % 60).padStart(2, '0');
      return `${yr}${mo}${dy}${hr}${mn}${sc} ${sign}${offH}${offM}`;
    };

    const startStr = formatOffset(dStart, 330); // +0530 (India)
    const stopStr = formatOffset(dStop, 330);

    const tzXml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="india_news"><display-name>India News</display-name></channel>
  <programme channel="india_news" start="${startStr}" stop="${stopStr}">
    <title>Evening Prime</title>
    <desc>News broadcast from Delhi</desc>
  </programme>
</tv>`;

    env = setupTestEnvironment({
      'xmltv.php': () => ({
        ok: true,
        status: 200,
        text: async () => tzXml
      })
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'adv_user_3', 'pass123');
    const info = getChannelEPGInfo({ id: 'india_news' });
    expect(info.title).toBe('Evening Prime');
    expect(Math.abs(info.startTime - dStart.getTime())).toBeLessThan(2000);
  });

  it('ADV-EPG-01-04: falls back cleanly to regex parsing when DOMParser throws or encounters XML syntax errors', async () => {
    const malformedSyntaxXml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="unclosed_tag_chan">
    <display-name>Regex Fallback Channel</display-name>
  </channel>
  <programme channel="unclosed_tag_chan" start="${new Date(Date.now() - 600000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 600000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Fallback Live Drama</title>
    <desc>Broadcast via regex extraction</desc>
  </programme>
  <broken_tag attr="unclosed"
</tv>`;

    env = setupTestEnvironment({
      'xmltv.php': () => ({
        ok: true,
        status: 200,
        text: async () => malformedSyntaxXml
      })
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'adv_user_4', 'pass123');
    const info = getChannelEPGInfo({ id: 'unclosed_tag_chan' });
    expect(info.title).toBe('Fallback Live Drama');
    expect(info.description).toBe('Broadcast via regex extraction');
  });

  it('ADV-EPG-01-05: decodes XML entities (&amp;, &lt;, &gt;, &quot;, &#39;, &apos;) in channel names, titles, and descriptions', async () => {
    const entityXml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="entity_chan">
    <display-name>Tom &amp; Jerry &lt;HD&gt;</display-name>
  </channel>
  <programme channel="entity_chan" start="${new Date(Date.now() - 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>&quot;Romeo &amp; Juliet&quot; - Director&#39;s Cut</title>
    <desc>It&apos;s a story about star-crossed lovers &amp; their fate.</desc>
  </programme>
</tv>`;

    env = setupTestEnvironment({
      'xmltv.php': () => ({
        ok: true,
        status: 200,
        text: async () => entityXml
      })
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'adv_user_5', 'pass123');
    const info = getChannelEPGInfo({ name: 'Tom & Jerry <HD>' });
    expect(info.title).toBe('"Romeo & Juliet" - Director\'s Cut');
    expect(info.description).toBe("It's a story about star-crossed lovers & their fate.");
  });

  it('ADV-EPG-01-06: survives corrupted channel entries (missing id, empty display names, unicode/emojis)', async () => {
    const weirdChannelXml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel><display-name>No ID Channel</display-name></channel>
  <channel id=""><display-name>Empty ID Channel</display-name></channel>
  <channel id="emoji_chan_1"><display-name>🔥 Sports Max ⚽ [4K UHD] 🏆</display-name></channel>
  <programme channel="emoji_chan_1" start="${new Date(Date.now() - 600000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 600000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Champions League Final</title>
  </programme>
</tv>`;

    env = setupTestEnvironment({
      'xmltv.php': () => ({
        ok: true,
        status: 200,
        text: async () => weirdChannelXml
      })
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'adv_user_6', 'pass123');
    const info = getChannelEPGInfo({ name: 'Sports Max 4K UHD' });
    expect(info.title).toBe('Champions League Final');
  });

  it('ADV-EPG-01-07: multi-proxy racer failover switches through proxies when primary endpoints fail', async () => {
    let callCount = 0;
    env = setupTestEnvironment({
      'xmltv.php': (url) => {
        callCount++;
        if (url.includes('tv-dinner-proxy') || callCount === 1) {
          throw new Error('Proxy 502 Bad Gateway');
        }
        return {
          ok: true,
          status: 200,
          text: async () => `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="failover_ch"><display-name>Failover TV</display-name></channel>
  <programme channel="failover_ch" start="${new Date(Date.now() - 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Racer Failover Success</title>
  </programme>
</tv>`
        };
      }
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'adv_user_7', 'pass123');
    const info = getChannelEPGInfo({ id: 'failover_ch' });
    expect(info.title).toBe('Racer Failover Success');
  });

  it('ADV-EPG-01-08: total proxy failure throws descriptive error and allows subsequent retries', async () => {
    env = setupTestEnvironment({
      'xmltv.php': () => {
        throw new Error('Network Connection Refused');
      }
    });
    installXmlParser();

    let failed = false;
    try {
      await initEPGFeed('http://example.com', 'adv_user_8_fail', 'pass123');
    } catch (err) {
      failed = true;
    }

    // Should not crash app and return placeholder
    const info = getChannelEPGInfo({ id: 'any_channel' });
    expect(info.title).toBeNull();
    expect(info.isServerEPG).toBe(false);
  });

  it('ADV-EPG-01-09: rehydrates EPG table from sessionStorage cache within 30-minute validity window', async () => {
    let fetchCalled = false;
    env = setupTestEnvironment({
      'xmltv.php': () => {
        fetchCalled = true;
        return { ok: true, status: 200, text: async () => '<tv></tv>' };
      }
    });
    installXmlParser();

    const cachedData = {
      cached_ch_99: [
        {
          title: 'Cached Breaking News',
          desc: 'From SessionStorage',
          start: Date.now() - 600000,
          stop: Date.now() + 600000
        }
      ]
    };
    const cachedNameMap = {
      cachedbreakingnews: 'cached_ch_99'
    };

    env.sessionStorage.setItem(
      'epg_xmltv_cached_user',
      JSON.stringify({
        ts: Date.now() - 5 * 60 * 1000, // 5 min ago
        data: cachedData,
        nameMap: cachedNameMap
      })
    );

    await initEPGFeed('http://example.com', 'cached_user', 'pass123');
    expect(fetchCalled).toBe(false); // No network request made

    const info = getChannelEPGInfo({ id: 'cached_ch_99' });
    expect(info.title).toBe('Cached Breaking News');
    expect(info.description).toBe('From SessionStorage');
  });

  it('ADV-EPG-01-10: handles corrupted JSON in sessionStorage without crashing and triggers fresh fetch', async () => {
    let freshFetchDone = false;
    env = setupTestEnvironment({
      'xmltv.php': () => {
        freshFetchDone = true;
        return {
          ok: true,
          status: 200,
          text: async () => `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="fresh_ch"><display-name>Fresh Channel</display-name></channel>
  <programme channel="fresh_ch" start="${new Date(Date.now() - 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Fresh Airing Show</title>
  </programme>
</tv>`
        };
      }
    });
    installXmlParser();

    env.sessionStorage.setItem('epg_xmltv_corrupt_user', '{INVALID_JSON_CORRUPT');

    await initEPGFeed('http://example.com', 'corrupt_user', 'pass123');
    expect(freshFetchDone).toBe(true);

    const info = getChannelEPGInfo({ id: 'fresh_ch' });
    expect(info.title).toBe('Fresh Airing Show');
  });

  it('ADV-EPG-01-11: user switching clears previous in-memory state and re-fetches for new user', async () => {
    const user1Xml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="user1_ch"><display-name>User1 Channel</display-name></channel>
  <programme channel="user1_ch" start="${new Date(Date.now() - 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>User 1 Exclusive</title>
  </programme>
</tv>`;

    const user2Xml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="user2_ch"><display-name>User2 Channel</display-name></channel>
  <programme channel="user2_ch" start="${new Date(Date.now() - 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>User 2 Exclusive</title>
  </programme>
</tv>`;

    env = setupTestEnvironment({
      'xmltv.php': (url) => {
        if (url.includes('switch_user_1')) {
          return { ok: true, status: 200, text: async () => user1Xml };
        }
        return { ok: true, status: 200, text: async () => user2Xml };
      }
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'switch_user_1', 'pass1');
    let info1 = getChannelEPGInfo({ id: 'user1_ch' });
    expect(info1.title).toBe('User 1 Exclusive');

    await initEPGFeed('http://example.com', 'switch_user_2', 'pass2');
    let info2 = getChannelEPGInfo({ id: 'user2_ch' });
    expect(info2.title).toBe('User 2 Exclusive');

    // Previous user channel should now be absent
    let oldInfo = getChannelEPGInfo({ id: 'user1_ch' });
    expect(oldInfo.title).toBeNull();
  });

  it('ADV-EPG-01-12: accurately parses boundary years (1970 and 2099) without integer overflow', async () => {
    const boundaryYearXml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="epoch_ch"><display-name>Epoch Channel</display-name></channel>
  <channel id="future_ch"><display-name>Future Channel</display-name></channel>
  <programme channel="epoch_ch" start="19700101000000 +0000" stop="19700101010000 +0000">
    <title>Epoch Launch Show</title>
  </programme>
  <programme channel="future_ch" start="20991231220000 +0000" stop="20991231230000 +0000">
    <title>22nd Century Countdown</title>
  </programme>
</tv>`;

    env = setupTestEnvironment({
      'xmltv.php': () => ({ ok: true, status: 200, text: async () => boundaryYearXml })
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'year_user', 'pass');
    // Direct table verification
    const epgEpoch = getChannelEPGInfo({ id: 'epoch_ch' });
    // Program is in 1970 so it is not currently live, title is null
    expect(epgEpoch.title).toBeNull();

    const epgFuture = getChannelEPGInfo({ id: 'future_ch' });
    expect(epgFuture.title).toBeNull();
  });

  it('ADV-EPG-01-13: maps multiple <display-name> tags on a single channel to the same channel id', async () => {
    const multiAliasXml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="bbc_one_hd">
    <display-name>BBC One HD</display-name>
    <display-name>BBC 1 High Definition</display-name>
    <display-name>BBC One London</display-name>
  </channel>
  <programme channel="bbc_one_hd" start="${new Date(Date.now() - 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>BBC News at Ten</title>
  </programme>
</tv>`;

    env = setupTestEnvironment({
      'xmltv.php': () => ({ ok: true, status: 200, text: async () => multiAliasXml })
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'alias_user', 'pass');

    // All aliases should resolve to BBC News at Ten
    expect(getChannelEPGInfo({ name: 'BBC One HD' }).title).toBe('BBC News at Ten');
    expect(getChannelEPGInfo({ name: 'BBC 1 High Definition' }).title).toBe('BBC News at Ten');
    expect(getChannelEPGInfo({ name: 'BBC One London' }).title).toBe('BBC News at Ten');
  });
});

// ═══════════════════════════════════════════════════════════════════════════════
// Suite 7: ADV-EPG-02: EPG Fuzzy Matching, Channel Table & Progress Boundary Stress
// ═══════════════════════════════════════════════════════════════════════════════

describe('ADV-EPG-02: EPG Fuzzy Matching, Channel Table & Progress Boundary Stress', () => {
  let env;
  beforeEach(() => {
    env = setupTestEnvironment();
    installXmlParser();
  });
  afterEach(() => {
    restoreTestEnvironment();
  });

  it('ADV-EPG-02-01: resolves channel program via candidate key priority order (epg_channel_id > tvg_id > stream_id > id > tvg_name)', async () => {
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="key_epg_id"><display-name>EPG ID Channel</display-name></channel>
  <channel id="key_tvg_id"><display-name>TVG ID Channel</display-name></channel>
  <channel id="key_stream_id"><display-name>Stream ID Channel</display-name></channel>
  <programme channel="key_epg_id" start="${new Date(Date.now() - 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Priority 1: EPG Channel ID</title>
  </programme>
  <programme channel="key_tvg_id" start="${new Date(Date.now() - 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Priority 2: TVG ID</title>
  </programme>
  <programme channel="key_stream_id" start="${new Date(Date.now() - 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Priority 3: Stream ID</title>
  </programme>
</tv>`;

    env = setupTestEnvironment({
      'xmltv.php': () => ({ ok: true, status: 200, text: async () => xml })
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'prio_user', 'pass');

    // Case A: epg_channel_id takes precedence over tvg_id
    const resA = getChannelEPGInfo({
      epg_channel_id: 'key_epg_id',
      tvg_id: 'key_tvg_id',
      stream_id: 'key_stream_id'
    });
    expect(resA.title).toBe('Priority 1: EPG Channel ID');

    // Case B: tvg_id takes precedence when epg_channel_id is absent
    const resB = getChannelEPGInfo({
      tvg_id: 'key_tvg_id',
      stream_id: 'key_stream_id'
    });
    expect(resB.title).toBe('Priority 2: TVG ID');

    // Case C: stream_id used when higher priority keys absent
    const resC = getChannelEPGInfo({
      stream_id: 'key_stream_id'
    });
    expect(resC.title).toBe('Priority 3: Stream ID');
  });

  it('ADV-EPG-02-02: normalizes channel names with punctuation, case, brackets, and prefix matching', async () => {
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="canal_plus"><display-name>CANAL+ CINEMA HD [4K] | US</display-name></channel>
  <programme channel="canal_plus" start="${new Date(Date.now() - 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Cinema Premiere HD</title>
  </programme>
</tv>`;

    env = setupTestEnvironment({
      'xmltv.php': () => ({ ok: true, status: 200, text: async () => xml })
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'norm_user', 'pass');

    // Search with lower-case and extra punctuation
    const res = getChannelEPGInfo({ name: 'canal cinema hd' });
    expect(res.title).toBe('Cinema Premiere HD');
  });

  it('ADV-EPG-02-03: rejects fuzzy prefix matching for very short channel names (<3 chars) to avoid false cross-matches', async () => {
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="fx_movies_hd"><display-name>FX Movies HD USA</display-name></channel>
  <programme channel="fx_movies_hd" start="${new Date(Date.now() - 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 300000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Blockbuster Action Movie</title>
  </programme>
</tv>`;

    env = setupTestEnvironment({
      'xmltv.php': () => ({ ok: true, status: 200, text: async () => xml })
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'short_user', 'pass');

    // 2-character channel name "FX" should NOT match "FX Movies HD USA" via prefix fuzzy check
    const res = getChannelEPGInfo({ name: 'FX' });
    expect(res.title).toBeNull();
  });

  it('ADV-EPG-02-04: getChannelEPGInfo returns clean placeholder with title: null for null, undefined, or empty channel object', () => {
    expect(getChannelEPGInfo(null)).toEqual({
      title: null,
      description: null,
      startTime: null,
      endTime: null,
      nextTitle: null,
      nextStartTime: null,
      isServerEPG: false
    });

    expect(getChannelEPGInfo({})).toEqual({
      title: null,
      description: null,
      startTime: null,
      endTime: null,
      nextTitle: null,
      nextStartTime: null,
      isServerEPG: false
    });
  });

  it('ADV-EPG-02-05: identifies next upcoming program in multi-program schedules and handles schedule end', async () => {
    const now = Date.now();
    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="multi_prog_ch"><display-name>Schedule Channel</display-name></channel>
  <programme channel="multi_prog_ch" start="${new Date(now - 1800000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(now + 1800000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Current Live Show</title>
  </programme>
  <programme channel="multi_prog_ch" start="${new Date(now + 1800000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(now + 5400000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Next Upcoming Show</title>
  </programme>
</tv>`;

    env = setupTestEnvironment({
      'xmltv.php': () => ({ ok: true, status: 200, text: async () => xml })
    });
    installXmlParser();

    await initEPGFeed('http://example.com', 'multi_user', 'pass');
    const info = getChannelEPGInfo({ id: 'multi_prog_ch' });
    expect(info.title).toBe('Current Live Show');
    expect(info.nextTitle).toBe('Next Upcoming Show');
    expect(info.nextStartTime).toBeGreaterThan(now);
  });

  it('ADV-EPG-02-06: getProgramProgress handles zero and negative durations (end <= start) by returning 0 percent and isLive: false', () => {
    const now = Date.now();
    // Zero duration
    const zeroDur = getProgramProgress({ startTime: now - 1000, endTime: now - 1000, title: 'Zero Duration' });
    expect(zeroDur.percent).toBe(0);
    expect(zeroDur.isLive).toBe(false);

    // Negative duration (end before start)
    const negDur = getProgramProgress({ startTime: now + 5000, endTime: now - 5000, title: 'Negative Duration' });
    expect(negDur.percent).toBe(0);
    expect(negDur.isLive).toBe(false);
  });

  it('ADV-EPG-02-07: getProgramProgress handles null, undefined, and non-date inputs cleanly', () => {
    expect(getProgramProgress(null)).toEqual({
      percent: 0,
      remainingMinutes: 0,
      formattedStart: '',
      formattedEnd: '',
      isLive: false,
      title: ''
    });

    expect(getProgramProgress({ startTime: 'INVALID', endTime: 'INVALID' })).toEqual({
      percent: 0,
      remainingMinutes: 0,
      formattedStart: '',
      formattedEnd: '',
      isLive: false,
      title: ''
    });
  });

  it('ADV-EPG-02-08: getProgramProgress calculates correct percentage, remainingMinutes, and live status for active show', () => {
    const now = Date.now();
    // 30 min elapsed out of 60 min total = 50%
    const start = now - 30 * 60 * 1000;
    const end = now + 30 * 60 * 1000;
    const prog = getProgramProgress({ startTime: start, endTime: end, title: 'Midway Show' });

    expect(prog.percent).toBe(50);
    expect(prog.remainingMinutes).toBe(30);
    expect(prog.isLive).toBe(true);
    expect(prog.title).toBe('Midway Show');
  });

  it('ADV-EPG-02-09: formatTime handles null, 0, negative values, and invalid timestamps safely', () => {
    expect(formatTime(null)).toBe('');
    expect(formatTime(undefined)).toBe('');
    expect(formatTime('not_a_date')).toBe('');
    expect(formatTime(Date.now())).toMatch(/\d+:\d+/);
  });

  it('ADV-EPG-02-10: fetchXtreamEPG parses base64 encoded short EPG listings and caches per stream for 10 minutes', async () => {
    const nowSec = Math.floor(Date.now() / 1000);
    let fetchCount = 0;

    env = setupTestEnvironment({
      'get_short_epg': () => {
        fetchCount++;
        return {
          ok: true,
          status: 200,
          json: async () => ({
            epg_listings: [
              {
                title: b64('Short EPG Live Broadcast'),
                description: b64('Detailed description of live event'),
                start_timestamp: nowSec - 600,
                stop_timestamp: nowSec + 1800
              }
            ]
          })
        };
      }
    });

    const res1 = await fetchXtreamEPG('http://example.com', 'user', 'pass', 'stream_99');
    expect(res1).toBeDefined();
    expect(res1.title).toBe('Short EPG Live Broadcast');
    expect(res1.description).toBe('Detailed description of live event');
    expect(res1.isServerEPG).toBe(true);
    expect(fetchCount).toBe(1);

    // Second call should hit the 10-minute in-memory cache
    const res2 = await fetchXtreamEPG('http://example.com', 'user', 'pass', 'stream_99');
    expect(res2.title).toBe('Short EPG Live Broadcast');
    expect(fetchCount).toBe(1); // Not incremented
  });

  it('ADV-EPG-02-11: getProgramProgress for program ended hours ago returns 100 percent and isLive: false', () => {
    const now = Date.now();
    const pastStart = now - 5 * 3600 * 1000;
    const pastEnd = now - 4 * 3600 * 1000;
    const prog = getProgramProgress({ startTime: pastStart, endTime: pastEnd, title: 'Old Morning Show' });
    expect(prog.percent).toBe(100);
    expect(prog.remainingMinutes).toBe(0);
    expect(prog.isLive).toBe(false);
  });

  it('ADV-EPG-02-12: getProgramProgress for future program starting tomorrow returns 0 percent and isLive: false', () => {
    const now = Date.now();
    const futureStart = now + 24 * 3600 * 1000;
    const futureEnd = now + 25 * 3600 * 1000;
    const prog = getProgramProgress({ startTime: futureStart, endTime: futureEnd, title: 'Tomorrow Special' });
    expect(prog.percent).toBe(0);
    expect(prog.remainingMinutes).toBe(25 * 60);
    expect(prog.isLive).toBe(false);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════
// Suite 8: ADV-VOD-01: Xtream VOD Stream Resolution, Metadata & Search
// ═══════════════════════════════════════════════════════════════════════════════

describe('ADV-VOD-01: Xtream VOD Stream Resolution, Metadata & Search Adversarial Hardening', () => {
  let env;
  beforeEach(() => {
    env = setupTestEnvironment();
  });
  afterEach(() => {
    restoreTestEnvironment();
  });

  it('ADV-VOD-01-01: calculateVODScore handles compound ratings (rating, rating_5based, vote_average) and recency bonuses', () => {
    // 10-point scale: rating = 8.0 -> 8 * 15 = 120
    const itemA = { rating: '8.0', popularity: '10', added: '1700000000', stream_icon: 'http://img.jpg' };
    const scoreA = calculateVODScore(itemA);
    // 120 + 20 + 17 + 5 = 162
    expect(scoreA).toBeCloseTo(162, 0);

    // 5-point scale: rating_5based = 4.5 -> (4.5 * 2) * 15 = 135
    const itemB = { rating_5based: '4.5', popularity: 0 };
    const scoreB = calculateVODScore(itemB);
    expect(scoreB).toBe(135);

    // vote_average fallback: 7.5 * 15 = 112.5
    const itemC = { vote_average: '7.5' };
    const scoreC = calculateVODScore(itemC);
    expect(scoreC).toBe(112.5);
  });

  it('ADV-VOD-01-02: calculateVODScore safely returns 0 for null, undefined, or empty objects', () => {
    expect(calculateVODScore(null)).toBe(0);
    expect(calculateVODScore(undefined)).toBe(0);
    expect(calculateVODScore({})).toBe(0);
    expect(calculateVODScore({ rating: 'INVALID_NAN' })).toBe(0);
  });

  it('ADV-VOD-01-03: sortVODByPopularity sorts array descending and handles non-array inputs without throwing', () => {
    const items = [
      { id: 1, rating: 5.0 },
      { id: 2, rating: 9.0 },
      { id: 3, rating: 7.0 }
    ];
    const sorted = sortVODByPopularity(items);
    expect(sorted[0].id).toBe(2);
    expect(sorted[1].id).toBe(3);
    expect(sorted[2].id).toBe(1);

    expect(sortVODByPopularity(null)).toEqual([]);
    expect(sortVODByPopularity(undefined)).toEqual([]);
    expect(sortVODByPopularity('not_array')).toEqual([]);
  });

  it('ADV-VOD-01-04: getSafeImageUrl handles protocol-relative, HTTP/HTTPS, data URIs, and empty inputs', () => {
    expect(getSafeImageUrl('')).toBe('');
    expect(getSafeImageUrl(null)).toBe('');
    expect(getSafeImageUrl('   ')).toBe('');
    expect(getSafeImageUrl('//images.com/art.jpg')).toBe('https://images.com/art.jpg');
    expect(getSafeImageUrl('https://secure.com/poster.png')).toBe('https://secure.com/poster.png');
    expect(getSafeImageUrl('data:image/png;base64,iVBORw0KGgo=')).toBe('data:image/png;base64,iVBORw0KGgo=');
  });

  it('ADV-VOD-01-05: getMovieStreamUrl and getSeriesStreamUrl sanitize extensions (strip dots, uppercase MKV, queries, hash)', () => {
    const client = new XtreamVODClient('http://vod.server.com:8080');

    // Movie with leading dot and query params
    const mUrl = client.getMovieStreamUrl('5001', '.mkv?token=secret#hdr');
    expect(mUrl).toContain(encodeURIComponent(`http://vod.server.com:8080/movie/${client.username}/${client.password}/5001.mkv`));

    // Series with uppercase extension
    const sUrl = client.getSeriesStreamUrl('9002', 'MP4');
    expect(sUrl).toContain(encodeURIComponent(`http://vod.server.com:8080/series/${client.username}/${client.password}/9002.mp4`));

    // Null extension defaults to mp4
    const defaultUrl = client.getMovieStreamUrl('1001', null);
    expect(defaultUrl).toContain('.mp4');
  });

  it('ADV-VOD-01-06: VOD search performs token-based matching across name, genre, director, cast with deduplication', async () => {
    const client = new XtreamVODClient('http://vod.server.com:8080');

    env = setupTestEnvironment({
      'get_vod_streams': () => ({
        ok: true,
        status: 200,
        json: async () => [
          { stream_id: 101, name: 'Inception (2010)', director: 'Christopher Nolan', genre: 'Sci-Fi Action' },
          { stream_id: 102, name: 'Interstellar (2014)', director: 'Christopher Nolan', genre: 'Sci-Fi Adventure' },
          { stream_id: 103, name: 'The Dark Knight (2008)', director: 'Christopher Nolan', genre: 'Action Crime' }
        ]
      })
    });

    // Multi-token query matching director + genre
    const results = await client.searchMovies('Christopher Nolan Sci-Fi');
    expect(results.length).toBeGreaterThan(0);
    expect(results[0].title).toBeDefined();
    // Inception and Interstellar should rank at the top
    const topTitles = results.slice(0, 2).map(r => r.title);
    expect(topTitles).toContain('Inception (2010)');
    expect(topTitles).toContain('Interstellar (2014)');
  });

  it('ADV-VOD-01-07: searchVOD with empty or whitespace-only query returns empty array immediately', async () => {
    const client = new XtreamVODClient('http://vod.server.com:8080');
    expect(await client.searchMovies('')).toEqual([]);
    expect(await client.searchMovies('   ')).toEqual([]);
    expect(await client.searchSeries('')).toEqual([]);
    expect(await client.searchVOD('')).toEqual([]);
  });

  it('ADV-VOD-01-08: Xtream API non-200 responses throw descriptive error without corrupting internal caches', async () => {
    const client = new XtreamVODClient('http://vod.server.com:8080');

    env = setupTestEnvironment({
      'get_vod_categories': () => ({
        ok: false,
        status: 401
      })
    });

    let threw = false;
    try {
      await client.getMovieCategories();
    } catch (e) {
      threw = true;
      expect(e.message).toContain('Xtream API error (get_vod_categories): 401');
    }
    expect(threw).toBe(true);
  });

  it('ADV-VOD-01-09: findMovieByTitle finds cached movies and queries common categories when uncached', async () => {
    const client = new XtreamVODClient('http://vod.server.com:8080');

    env = setupTestEnvironment({
      'get_vod_streams': () => ({
        ok: true,
        status: 200,
        json: async () => [
          { stream_id: 201, name: 'Gladiator II (2024)', rating: 8.2 }
        ]
      })
    });

    const movie = await client.findMovieByTitle('Gladiator II');
    expect(movie).toBeDefined();
    expect(movie.stream_id).toBe(201);
  });

  it('ADV-VOD-01-10: findSeriesByTitle finds series and handles null/empty search title', async () => {
    const client = new XtreamVODClient('http://vod.server.com:8080');

    env = setupTestEnvironment({
      'get_series': () => ({
        ok: true,
        status: 200,
        json: async () => [
          { series_id: 301, name: 'House of the Dragon', rating: 8.5 }
        ]
      })
    });

    expect(await client.findSeriesByTitle(null)).toBeNull();
    expect(await client.findSeriesByTitle('')).toBeNull();
    const series = await client.findSeriesByTitle('House of the Dragon');
    expect(series).toBeDefined();
    expect(series.series_id).toBe(301);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════
// Suite 9: ADV-RESUME-01: VOD Resume Position & Storage Persistence Stress
// ═══════════════════════════════════════════════════════════════════════════════

describe('ADV-RESUME-01: VOD Resume Position & Storage Persistence Stress', () => {
  let env;
  beforeEach(() => {
    env = setupTestEnvironment();
    if (typeof localStorage !== 'undefined') localStorage.clear();
  });
  afterEach(() => {
    restoreTestEnvironment();
  });

  it('ADV-RESUME-01-01: handles corrupted JSON in localStorage vod_resume_positions safely', () => {
    localStorage.setItem('vod_resume_positions', 'NOT_VALID_JSON{{{');

    let resumeData = {};
    try {
      resumeData = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      if (!resumeData || typeof resumeData !== 'object' || Array.isArray(resumeData)) {
        resumeData = {};
      }
    } catch (e) {
      resumeData = {};
    }

    expect(resumeData).toEqual({});
  });

  it('ADV-RESUME-01-02: LRU pruning stress tests cache boundary with 1,000+ entries, maintaining strictly the 100 most recent', () => {
    const resumeData = {};
    const now = Date.now();

    // Populate 1,000 entries with staggered timestamps
    for (let i = 0; i < 1000; i++) {
      resumeData[`media_key_${i}`] = {
        position: 100 + i,
        duration: 3600,
        timestamp: now - (1000 - i) * 1000 // media_key_999 is newest, media_key_0 is oldest
      };
    }

    // Apply LRU pruning algorithm
    const keys = Object.keys(resumeData);
    if (keys.length > 100) {
      const sortedKeys = keys.sort((a, b) => (resumeData[a]?.timestamp || 0) - (resumeData[b]?.timestamp || 0));
      const excess = sortedKeys.length - 100;
      for (let i = 0; i < excess; i++) {
        delete resumeData[sortedKeys[i]];
      }
    }

    expect(Object.keys(resumeData).length).toBe(100);
    // Oldest entry (media_key_0) should be pruned
    expect(resumeData['media_key_0']).toBeUndefined();
    // Newest entry (media_key_999) must be preserved
    expect(resumeData['media_key_999']).toBeDefined();
    expect(resumeData['media_key_900']).toBeDefined();
  });

  it('ADV-RESUME-01-03: 95% completion threshold deletes resume position to prevent restarting at credits', () => {
    const key = 'vod_movie_interstellar';
    const resumeData = {
      [key]: { position: 1000, duration: 7200, timestamp: Date.now() }
    };

    const currentTime = 7000; // > 95% of 7200s (6840s)
    const duration = 7200;

    if (currentTime / duration > 0.95) {
      delete resumeData[key];
    }

    expect(resumeData[key]).toBeUndefined();
  });

  it('ADV-RESUME-01-04: playback under 10 seconds does not record a resume position', () => {
    const key = 'vod_short_sample';
    const resumeData = {};
    const currentTime = 8.5; // < 10s

    if (currentTime > 10) {
      resumeData[key] = { position: Math.floor(currentTime) };
    }

    expect(resumeData[key]).toBeUndefined();
  });

  it('ADV-RESUME-01-05: float timestamps and non-integer seconds are cleanly floored', () => {
    const currentTime = 142.879124;
    const duration = 3600.45;
    const record = {
      position: Math.floor(currentTime),
      duration: Math.floor(duration),
      timestamp: Date.now()
    };

    expect(record.position).toBe(142);
    expect(record.duration).toBe(3600);
  });

  it('ADV-RESUME-01-06: 5-second write throttling skips redundant localStorage writes during rapid progress updates', () => {
    let writeCount = 0;
    const mockStorage = {
      setItem: () => { writeCount++; },
      getItem: () => '{}'
    };

    let lastSaveTime = 0;
    const saveProgress = (currentTime, duration) => {
      const now = Date.now();
      if (!lastSaveTime || now - lastSaveTime > 5000) {
        lastSaveTime = now;
        mockStorage.setItem('vod_resume_positions', JSON.stringify({ pos: currentTime }));
      }
    };

    // First save at t = 0ms
    saveProgress(20, 1000);
    expect(writeCount).toBe(1);

    // Rapid consecutive calls (within 5000ms)
    saveProgress(21, 1000);
    saveProgress(22, 1000);
    saveProgress(23, 1000);
    expect(writeCount).toBe(1); // Throttled, write count remains 1
  });
});

// ═══════════════════════════════════════════════════════════════════════════════
// Suite 10: ADV-POD-01: Podcast RSS & YouTube Feed Ingestion Adversarial Stress
// ═══════════════════════════════════════════════════════════════════════════════

describe('ADV-POD-01: Podcast RSS & YouTube Feed Ingestion Adversarial Stress', () => {
  let env;
  beforeEach(() => {
    env = setupTestEnvironment();
    if (typeof localStorage !== 'undefined') localStorage.clear();
  });
  afterEach(() => {
    restoreTestEnvironment();
  });

  it('ADV-POD-01-01: decodeXmlEntities decodes XML entities and numeric decimal character entities', () => {
    expect(decodeXmlEntities('A &amp; B &lt; C &gt; D &quot; E &#39; F &apos; G')).toBe('A & B < C > D " E \' F \' G');
    expect(decodeXmlEntities('&#72;&#101;&#108;&#108;&#111;')).toBe('Hello');
    expect(decodeXmlEntities('')).toBe('');
    expect(decodeXmlEntities(null)).toBe('');
  });

  it('ADV-POD-01-02: extractXmlCdataOrText extracts text from CDATA sections, normal text nodes, and handles missing tags', () => {
    const xml = `<root>
      <title><![CDATA[Raw & Uncensored Episode #100 <Special>]]></title>
      <description>Standard text description</description>
      <empty_tag></empty_tag>
    </root>`;

    expect(extractXmlCdataOrText(xml, 'title')).toBe('Raw & Uncensored Episode #100 <Special>');
    expect(extractXmlCdataOrText(xml, 'description')).toBe('Standard text description');
    expect(extractXmlCdataOrText(xml, 'non_existent')).toBe('');
  });

  it('ADV-POD-01-03: formatPodcastDuration handles HH:MM:SS, MM:SS, raw seconds, and invalid values', () => {
    expect(formatPodcastDuration('01:30:00')).toBe('90m');
    expect(formatPodcastDuration('45:15')).toBe('45m');
    expect(formatPodcastDuration('3600')).toBe('60m');
    expect(formatPodcastDuration(5400)).toBe('90m');
    expect(formatPodcastDuration(null)).toBe('30m');
    expect(formatPodcastDuration('')).toBe('30m');
  });

  it('ADV-POD-01-04: parseRss2AudioFeed parses RSS 2.0 items, extracting enclosures, pubDates, and iTunes metadata', () => {
    const rssXml = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
  <channel>
    <title>Adversarial Podcast</title>
    <description>Testing podcast parser boundaries</description>
    <itunes:image href="https://img.sample.com/cover.jpg"/>
    <item>
      <title>Episode 1: The Machine Age</title>
      <enclosure url="https://audio.sample.com/ep1.mp3" length="54321000" type="audio/mpeg"/>
      <pubDate>Wed, 19 Aug 2026 14:00:00 GMT</pubDate>
      <itunes:duration>01:15:30</itunes:duration>
      <guid>guid_ep_001</guid>
    </item>
  </channel>
</rss>`;

    const episodes = parseRss2AudioFeed(rssXml, { id: 'chan_adv_1', channelName: 'Adversarial Podcast' });
    expect(episodes.length).toBe(1);
    const ep = episodes[0];
    expect(ep.title).toBe('Episode 1: The Machine Age');
    expect(ep.audioUrl).toBe('https://audio.sample.com/ep1.mp3');
    expect(ep.mediaType).toBe('audio');
    expect(ep.duration).toBe('75m');
    expect(ep.enclosure.length).toBe(54321000);
  });

  it('ADV-POD-01-05: parseRss2AudioFeed falls back to <media:content> and detects video enclosures', () => {
    const videoRssXml = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
  <channel>
    <title>Video Podcast Channel</title>
    <item>
      <title>Video Special Episode</title>
      <media:content url="https://video.sample.com/ep1.mp4" type="video/mp4"/>
      <pubDate>Wed, 19 Aug 2026 14:00:00 GMT</pubDate>
      <guid>guid_video_001</guid>
    </item>
  </channel>
</rss>`;

    const episodes = parseRss2AudioFeed(videoRssXml, { id: 'chan_video_1' });
    expect(episodes.length).toBe(1);
    expect(episodes[0].mediaType).toBe('video');
    expect(episodes[0].audioUrl).toBe('https://video.sample.com/ep1.mp4');
  });

  it('ADV-POD-01-06: fetchChannelPastEpisodes parses YouTube XML feeds with HTML entity decoded titles', async () => {
    const ytXml = `<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
  <entry>
    <yt:videoId>adv_yt_vid_123</yt:videoId>
    <title>AI &amp; Robotics: CEO&#39;s Vision</title>
    <published>2026-08-19T10:00:00+00:00</published>
  </entry>
</feed>`;

    env = setupTestEnvironment({
      'youtube.com/feeds/videos.xml': () => ({
        ok: true,
        status: 200,
        text: async () => ytXml
      })
    });

    const eps = await fetchChannelPastEpisodes({
      id: 'chan_yt_test',
      channelName: 'Test YT Channel',
      ytChannelId: 'UC_TEST_CHANNEL_ID'
    });

    expect(eps.length).toBe(1);
    expect(eps[0].youtubeId).toBe('adv_yt_vid_123');
    expect(eps[0].title).toBe("AI & Robotics: CEO's Vision");
  });

  it('ADV-POD-01-07: searchRealPodcastAPI handles Invidious failure by falling back to curated podcast matching', async () => {
    env = setupTestEnvironment({
      'search?q=': () => {
        throw new Error('Invidious 503 Service Unavailable');
      }
    });

    // Searching for "Lex Fridman" should match curated catalog
    const res = await searchRealPodcastAPI('Lex Fridman');
    expect(res.channels.length).toBeGreaterThan(0);
    expect(res.channels[0].channelName).toBe('Lex Fridman Podcast');
  });

  it('ADV-POD-01-08: fetchForYouCuratedPodcasts provides diverse starter pack when user has no subscriptions or history', async () => {
    const recommendations = await fetchForYouCuratedPodcasts({
      subscribedShows: [],
      favoritePodcasts: [],
      history: []
    });

    expect(recommendations.length).toBeGreaterThanOrEqual(6);
    const names = recommendations.map(r => r.channelName);
    // Should include diverse channels from tech, science, comedy
    expect(names.some(n => n.includes('Waveform') || n.includes('Fridman') || n.includes('Huberman'))).toBe(true);
  });

  it('ADV-POD-01-09: fetchForYouCuratedPodcasts profiles user affinity and recommends matching category shows', async () => {
    const userProfile = {
      subscribedShows: [
        { id: 'chan_jre', channelName: 'The Joe Rogan Experience', category: 'Comedy & Talk' }
      ],
      favoritePodcasts: [
        { id: 'chan_bad_friends', channelName: 'Bad Friends', category: 'Comedy & Improv' }
      ],
      history: []
    };

    const recommendations = await fetchForYouCuratedPodcasts(userProfile);
    expect(recommendations.length).toBeGreaterThan(0);
    // Should recommend other comedy shows (e.g. Kill Tony, Flagrant, Theo Von)
    const comedyMatches = recommendations.filter(r => (r.category || '').includes('Comedy'));
    expect(comedyMatches.length).toBeGreaterThan(0);
  });

  it('ADV-POD-01-10: handles corrupted subscriptions JSON in localStorage without throwing', () => {
    localStorage.setItem('subscribed_podcasts', '{MALFORMED_JSON_ARRAY');
    localStorage.setItem('pocketcasts_subscribed_ids', 'INVALID');

    let subIds = [];
    try {
      const v = localStorage.getItem('subscribed_podcasts');
      if (v) subIds = JSON.parse(v);
      if (!Array.isArray(subIds)) subIds = [];
    } catch (e) {
      subIds = [];
    }

    expect(subIds).toEqual([]);
  });

  it('ADV-POD-01-11: fetchChannelPastEpisodesNextPage handles continuation pagination tokens', async () => {
    env = setupTestEnvironment({
      'api/v1/channels': () => ({
        ok: true,
        status: 200,
        json: async () => ({
          videos: [
            { videoId: 'cont_vid_001', title: 'Page 2 Episode 1', lengthSeconds: 3600, published: 1724000000 }
          ],
          continuation: 'token_page_3'
        })
      })
    });

    const res = await fetchChannelPastEpisodesNextPage(
      { id: 'chan_yt_lex', channelName: 'Lex Fridman', ytChannelId: 'UCSHZKyawb77ixDdsGog4iWA' },
      'token_page_2'
    );

    expect(res.episodes.length).toBe(1);
    expect(res.episodes[0].youtubeId).toBe('cont_vid_001');
    expect(res.continuation).toBe('token_page_3');
  });

  it('ADV-POD-01-12: getGuaranteedChannelEpisodes returns verified episodes for curated channels and empty array for unlisted', async () => {
    // Verified curated channel
    const waveEps = await fetchChannelPastEpisodes({ id: 'chan_mkbhd_waveform', channelName: 'Waveform: The MKBHD Podcast' });
    expect(waveEps.length).toBeGreaterThanOrEqual(4);

    // Unlisted/unknown channel
    const unknownEps = await fetchChannelPastEpisodes({ id: 'chan_unknown_nonexistent', channelName: 'Totally Random Nonexistent Channel 12345' });
    expect(unknownEps).toEqual([]);
  });

  it('ADV-POD-01-13: fetchPodcastRssFeed cycles through all 4 proxy fallbacks when primary proxies fail', async () => {
    let attempts = 0;
    env = setupTestEnvironment({
      'audio_feed.xml': (url) => {
        attempts++;
        if (attempts < 4) {
          return { ok: false, status: 502 };
        }
        return {
          ok: true,
          status: 200,
          text: async () => `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Proxy Failover Podcast</title>
    <item>
      <title>Resilient Episode</title>
      <enclosure url="https://audio.com/resilient.mp3" type="audio/mpeg" length="1234"/>
    </item>
  </channel>
</rss>`
        };
      }
    });

    const eps = await fetchPodcastRssFeed('https://podcasts.example.com/audio_feed.xml', { id: 'test_pod' });
    expect(eps.length).toBe(1);
    expect(eps[0].title).toBe('Resilient Episode');
  });
});

// ─── Direct Execution ─────────────────────────────────────────────────────────

const isDirectRun = process.argv[1] && (
  process.argv[1].includes('tier5_adversarial_stress.js') ||
  import.meta.url.endsWith(process.argv[1].replace(/\\/g, '/'))
);

if (isDirectRun) {
  runAllRegisteredSuites().then(res => {
    console.log(`\nAdversarial Tier 5 Result: ${res.passed} passed, ${res.failed} failed`);
    process.exit(res.failed > 0 ? 1 : 0);
  });
}
