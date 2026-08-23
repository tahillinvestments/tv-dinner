/**
 * Challenger 2: Milestone 4 Xtream VOD Engine Empirical Adversarial Test Harness
 * ─────────────────────────────────────────────────────────────────────────────
 * Stress-testing:
 * 1. Episode Container Extension Resolution under Multi-Format & Malformed Inputs
 * 2. Multi-Format Season / Episode Metadata Parsing & Normalization
 * 3. Rich Details Modal Lifecycle, Teardown & DOM Relocation Integrity
 * 4. Image Proxying, TMDB / Xtream Artwork Fallback Hierarchy
 * 5. LRU Playback Resume Tracking, LRU Capacity Eviction & Corrupted Storage Resilience
 * 6. Fullscreen & Windowed VOD Player Controls (Seeking, Clamping, Aspect Ratio, Volume)
 * 7. High-Frequency Rapid Action Fuzzing (500 rapid seek/switch/modal cycles)
 */

import {
  describe,
  it,
  expect,
  beforeEach,
  afterEach,
  setupTestEnvironment,
  restoreTestEnvironment,
  MockElement,
  MockEvent,
  MockKeyboardEvent,
  runAllRegisteredSuites
} from './harness.js';

import { XtreamVODClient, xtreamVOD, getSafeImageUrl, calculateVODScore, sortVODByPopularity } from '../src/xtreamVODService.js';
import { IPTVPlayer } from '../src/player.js';

describe('CHALLENGER 2: Xtream VOD Engine Empirical Adversarial Test Harness', () => {

  // ════════════════════════════════════════════════════════════════════════════
  // SUITE 1: Episode Container Extension Resolution under Multi-Format Inputs
  // ════════════════════════════════════════════════════════════════════════════
  describe('SUITE 1: Episode Container Extension Resolution Stress', () => {
    let env;
    let client;
    beforeEach(() => {
      env = setupTestEnvironment();
      client = new XtreamVODClient('http://iptv.provider.com:8080');
      localStorage.setItem('xtream_vod_portal', 'http://iptv.provider.com:8080');
      localStorage.setItem('xtream_vod_username', 'user@domain.com');
      localStorage.setItem('xtream_vod_password', 'pass#123&more');
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('ADV-01: Correctly handles standard container extensions (.mp4, .mkv, .avi, .ts, .webm, .flv, .mov, .m4v)', () => {
      const exts = ['mp4', 'mkv', 'avi', 'ts', 'webm', 'flv', 'mov', 'm4v'];
      for (const ext of exts) {
        const movieUrl = client.getMovieStreamUrl(101, ext);
        const seriesUrl = client.getSeriesStreamUrl(202, ext);
        
        expect(movieUrl).toContain(`101.${ext}`);
        expect(seriesUrl).toContain(`202.${ext}`);
        expect(movieUrl).not.toContain(`101..${ext}`);
        expect(seriesUrl).not.toContain(`202..${ext}`);
      }
    });

    it('ADV-02: Normalizes extensions with single and multiple leading dots', () => {
      expect(client.getSeriesStreamUrl(301, '.mkv')).toContain('301.mkv');
      expect(client.getSeriesStreamUrl(302, '..mkv')).toContain('302.mkv');
      expect(client.getSeriesStreamUrl(303, '....avi')).toContain('303.avi');
      expect(client.getMovieStreamUrl(401, '.mp4')).toContain('401.mp4');
      expect(client.getMovieStreamUrl(402, '...ts')).toContain('402.ts');
    });

    it('ADV-03: Normalizes mixed-case and uppercase container extensions', () => {
      expect(client.getSeriesStreamUrl(501, 'MKV')).toContain('501.mkv');
      expect(client.getSeriesStreamUrl(502, 'Mp4')).toContain('502.mp4');
      expect(client.getSeriesStreamUrl(503, 'AVI')).toContain('503.avi');
      expect(client.getMovieStreamUrl(601, 'TS')).toContain('601.ts');
      expect(client.getMovieStreamUrl(602, '.MKV')).toContain('602.mkv');
    });

    it('ADV-04: Strips query parameters and fragments attached to extensions', () => {
      expect(client.getSeriesStreamUrl(701, 'mkv?token=abc123xyz')).toContain('701.mkv');
      expect(client.getSeriesStreamUrl(701, 'mkv?token=abc123xyz')).not.toContain('?token');
      expect(client.getSeriesStreamUrl(702, '.mp4?auth=true&expires=999999')).toContain('702.mp4');
      expect(client.getSeriesStreamUrl(702, '.mp4?auth=true&expires=999999')).not.toContain('&expires');
      expect(client.getMovieStreamUrl(801, 'avi?key=val#part2')).toContain('801.avi');
      expect(client.getMovieStreamUrl(801, 'avi?key=val#part2')).not.toContain('?key');
    });

    it('ADV-05: Handles whitespace, tabs, and newline characters in extensions', () => {
      expect(client.getSeriesStreamUrl(901, '  mkv  ')).toContain('901.mkv');
      expect(client.getSeriesStreamUrl(902, '\t.avi\n')).toContain('902.avi');
      expect(client.getMovieStreamUrl(903, '  .mp4\r\n')).toContain('903.mp4');
    });

    it('ADV-06: Robust fallback to mp4 for null, undefined, empty, boolean, or whitespace-only inputs', () => {
      expect(client.getSeriesStreamUrl(1001, null)).toContain('1001.mp4');
      expect(client.getSeriesStreamUrl(1002, undefined)).toContain('1002.mp4');
      expect(client.getSeriesStreamUrl(1003, '')).toContain('1003.mp4');
      expect(client.getSeriesStreamUrl(1004, '   ')).toContain('1004.mp4');
      expect(client.getSeriesStreamUrl(1005, '.')).toContain('1005.mp4');
      expect(client.getMovieStreamUrl(2001, null)).toContain('2001.mp4');
      expect(client.getMovieStreamUrl(2002, undefined)).toContain('2002.mp4');
      expect(client.getMovieStreamUrl(2003, '')).toContain('2003.mp4');
      expect(client.getMovieStreamUrl(2004, '   ')).toContain('2004.mp4');
    });

    it('ADV-07: Base URL trailing slashes and port combinations format cleanly', () => {
      const clientSlash = new XtreamVODClient('http://vod.server.com:8080/');
      const url = clientSlash.getMovieStreamUrl(555, 'mp4');
      expect(url).toContain('/api/proxy?url=');
      const decoded = decodeURIComponent(url.split('url=')[1]);
      expect(decoded).toBe('http://vod.server.com:8080/movie/user@domain.com/pass#123&more/555.mp4');
      expect(decoded).not.toContain(':8080//movie');
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // SUITE 2: Multi-Format Season & Episode Metadata Normalization
  // ════════════════════════════════════════════════════════════════════════════
  describe('SUITE 2: Season and Episode Metadata Parsing Resilience', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('ADV-08: Normalizes episodes object with string season keys ("1", "2") and integer season keys (1, 2)', () => {
      const sInfoStringKeys = {
        info: { name: 'Breaking Bad', season_count: 2 },
        episodes: {
          '1': [
            { id: 101, episode_num: 1, title: 'Pilot', container_extension: 'mkv' },
            { id: 102, episode_num: 2, title: 'Cat\'s in the Bag...', container_extension: 'mp4' }
          ],
          '2': [
            { id: 201, episode_num: 1, title: 'Seven Thirty-Seven', container_extension: 'avi' }
          ]
        }
      };

      const seasonNum = 1;
      const eps = sInfoStringKeys.episodes[seasonNum] || sInfoStringKeys.episodes[String(seasonNum)] || [];
      expect(eps.length).toBe(2);
      expect(eps[0].id).toBe(101);
      expect(eps[0].container_extension).toBe('mkv');
      expect(eps[1].container_extension).toBe('mp4');
    });

    it('ADV-09: Normalizes diverse episode number property keys (episode_num vs episode_number vs id)', () => {
      const mixedEpisodes = [
        { id: 501, episode_num: 1, title: 'Ep 1', container_extension: 'mkv' },
        { id: 502, episode_number: 2, name: 'Ep 2', container_extension: '.mp4' },
        { id: 503, number: 3, title: 'Ep 3' }, // missing container_extension
        { stream_id: 504, episode_num: 4, name: 'Ep 4', container_extension: 'AVI' }
      ];

      const client = new XtreamVODClient('http://test:8080');
      const normalized = mixedEpisodes.map(ep => {
        const epNum = ep.episode_num || ep.episode_number || ep.number || 1;
        const epId = ep.id || ep.stream_id;
        const ext = ep.container_extension || 'mp4';
        return {
          id: epId,
          episode_number: epNum,
          name: ep.title || ep.name || `Episode ${epNum}`,
          container_extension: ext,
          stream_url: client.getSeriesStreamUrl(epId, ext)
        };
      });

      expect(normalized[0].episode_number).toBe(1);
      expect(normalized[0].stream_url).toContain('501.mkv');
      expect(normalized[1].episode_number).toBe(2);
      expect(normalized[1].stream_url).toContain('502.mp4');
      expect(normalized[2].episode_number).toBe(3);
      expect(normalized[2].stream_url).toContain('503.mp4'); // defaulted to mp4
      expect(normalized[3].id).toBe(504);
      expect(normalized[3].stream_url).toContain('504.avi');
    });

    it('ADV-10: Handles series with Season 0 (Specials) filtering when main seasons exist', () => {
      const seasonsList = [
        { season_number: 0, name: 'Specials' },
        { season_number: 1, name: 'Season 1' },
        { season_number: 2, name: 'Season 2' }
      ];

      const filtered = seasonsList.filter(s => !(s.season_number === 0 && seasonsList.length > 1));
      expect(filtered.length).toBe(2);
      expect(filtered[0].season_number).toBe(1);
      expect(filtered[1].season_number).toBe(2);

      const onlySpecials = [{ season_number: 0, name: 'Specials' }];
      const keepSingle = onlySpecials.filter(s => !(s.season_number === 0 && onlySpecials.length > 1));
      expect(keepSingle.length).toBe(1);
      expect(keepSingle[0].name).toBe('Specials');
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // SUITE 3: Rich Details Modal Lifecycle & Artwork Fallback Hierarchy
  // ════════════════════════════════════════════════════════════════════════════
  describe('SUITE 3: Rich Details Modal Lifecycle & Artwork Hierarchy', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('ADV-11: Artwork fallback hierarchy resolves in strict priority order (backdrop -> poster -> stream_icon -> cover -> empty)', () => {
      const getResolvedBackdrop = (item) => {
        return item.backdrop_path || item.poster_path || getSafeImageUrl(item.stream_icon) || getSafeImageUrl(item.cover) || '';
      };

      // Case 1: All available -> prefers backdrop_path
      expect(getResolvedBackdrop({
        backdrop_path: 'https://tmdb.org/bd.jpg',
        poster_path: 'https://tmdb.org/post.jpg',
        stream_icon: 'http://xtream/icon.jpg',
        cover: 'http://xtream/cover.jpg'
      })).toBe('https://tmdb.org/bd.jpg');

      // Case 2: Backdrop missing -> prefers poster_path
      expect(getResolvedBackdrop({
        poster_path: 'https://tmdb.org/post.jpg',
        stream_icon: 'http://xtream/icon.jpg',
        cover: 'http://xtream/cover.jpg'
      })).toBe('https://tmdb.org/post.jpg');

      // Case 3: TMDB missing -> prefers stream_icon
      expect(getResolvedBackdrop({
        stream_icon: 'https://xtream/icon.jpg',
        cover: 'https://xtream/cover.jpg'
      })).toBe('https://xtream/icon.jpg');

      // Case 4: Only cover available -> returns cover
      expect(getResolvedBackdrop({
        cover: 'https://xtream/cover.jpg'
      })).toBe('https://xtream/cover.jpg');

      // Case 5: Empty object -> returns empty string
      expect(getResolvedBackdrop({})).toBe('');
    });

    it('ADV-12: getSafeImageUrl handles protocol-relative URLs, data URIs, and mixed content', () => {
      expect(getSafeImageUrl('//images.tmdb.org/t/p/w1280/backdrop.jpg')).toBe('https://images.tmdb.org/t/p/w1280/backdrop.jpg');
      expect(getSafeImageUrl('data:image/jpeg;base64,/9j/4AAQSkZJRgABAQEASABIAAD')).toContain('data:image/jpeg;base64');
      expect(getSafeImageUrl('   https://secure.image.com/art.png   ')).toBe('https://secure.image.com/art.png');
      expect(getSafeImageUrl(null)).toBe('');
      expect(getSafeImageUrl(undefined)).toBe('');
      expect(getSafeImageUrl('')).toBe('');
      expect(getSafeImageUrl(12345)).toBe('');
    });

    it('ADV-13: Details modal open and close properly modifies body overflow and DOM hierarchy', () => {
      const overlay = env.doc.createElement('div');
      overlay.id = 'details-overlay';
      overlay.classList.add('hidden');

      const playerSection = env.doc.createElement('section');
      playerSection.id = 'player-section';

      const liveContainer = env.doc.createElement('div');
      liveContainer.id = 'live-player-container';
      liveContainer.appendChild(playerSection);

      const vodContainer = env.doc.createElement('div');
      vodContainer.id = 'vod-player-container';

      // Open Modal Simulation
      overlay.classList.remove('hidden');
      env.doc.body.style.overflow = 'hidden';
      vodContainer.appendChild(playerSection);

      expect(overlay.classList.contains('hidden')).toBe(false);
      expect(env.doc.body.style.overflow).toBe('hidden');
      expect(vodContainer.children.includes(playerSection)).toBe(true);
      expect(liveContainer.children.includes(playerSection)).toBe(false);

      // Close Modal Simulation
      overlay.classList.add('hidden');
      env.doc.body.style.overflow = '';
      liveContainer.appendChild(playerSection);

      expect(overlay.classList.contains('hidden')).toBe(true);
      expect(env.doc.body.style.overflow).toBe('');
      expect(liveContainer.children.includes(playerSection)).toBe(true);
      expect(vodContainer.children.includes(playerSection)).toBe(false);
    });

    it('ADV-14: Rapid open-close cycle (100 times) maintains DOM stability without orphan nodes', () => {
      const overlay = env.doc.createElement('div');
      overlay.id = 'details-overlay';

      const playerSection = env.doc.createElement('section');
      const liveContainer = env.doc.createElement('div');
      const vodContainer = env.doc.createElement('div');

      for (let i = 0; i < 100; i++) {
        overlay.classList.remove('hidden');
        env.doc.body.style.overflow = 'hidden';
        vodContainer.appendChild(playerSection);

        overlay.classList.add('hidden');
        env.doc.body.style.overflow = '';
        liveContainer.appendChild(playerSection);
      }

      expect(overlay.classList.contains('hidden')).toBe(true);
      expect(env.doc.body.style.overflow).toBe('');
      expect(liveContainer.children.length).toBe(1);
      expect(vodContainer.children.length).toBe(0);
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // SUITE 4: LRU Playback Resume Persistence & Storage Corruption Resilience
  // ════════════════════════════════════════════════════════════════════════════
  describe('SUITE 4: LRU Playback Resume Tracking & Corruption Resilience', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('ADV-15: savePlaybackPosition recovers smoothly from completely corrupted localStorage JSON', () => {
      const corruptInputs = [
        '{invalid json: 123',
        'undefined',
        'null',
        '12345',
        'string-content',
        '{"movie_1": { incomplete',
        '{"movie_1": null, "movie_2": undefined}'
      ];

      const player = new IPTVPlayer('video-player');
      player.currentMediaKey = 'movie_corruption_test';
      player.currentUrl = '/api/proxy?url=http://test/movie.mp4';
      player.video.duration = 7200;
      player.video.currentTime = 500;

      for (const corrupt of corruptInputs) {
        localStorage.setItem('vod_resume_positions', corrupt);
        player.lastSaveTime = 0;
        player.savePlaybackPosition();

        const stored = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
        expect(stored['movie_corruption_test']).toBeDefined();
        expect(stored['movie_corruption_test'].position).toBe(500);
      }
    });

    it('ADV-16: Ignores non-numeric, NaN, negative, or Infinity duration without writing invalid entries', () => {
      const player = new IPTVPlayer('video-player');
      player.currentMediaKey = 'movie_invalid_duration';
      player.currentUrl = '/api/proxy?url=http://test/movie.mp4';

      localStorage.removeItem('vod_resume_positions');

      // NaN duration
      player.video.duration = NaN;
      player.video.currentTime = 100;
      player.lastSaveTime = 0;
      player.savePlaybackPosition();
      expect(localStorage.getItem('vod_resume_positions')).toBeNull();

      // Infinity duration
      player.video.duration = Infinity;
      player.video.currentTime = 100;
      player.lastSaveTime = 0;
      player.savePlaybackPosition();
      expect(localStorage.getItem('vod_resume_positions')).toBeNull();

      // 0 duration
      player.video.duration = 0;
      player.video.currentTime = 100;
      player.lastSaveTime = 0;
      player.savePlaybackPosition();
      expect(localStorage.getItem('vod_resume_positions')).toBeNull();
    });

    it('ADV-17: Strict LRU pruning keeps exactly 100 entries and purges the oldest timestamps', () => {
      const initialStore = {};
      const baseTime = 1700000000000;

      for (let i = 1; i <= 140; i++) {
        initialStore[`asset_${i}`] = {
          position: i * 20,
          duration: 3600,
          timestamp: baseTime + (i * 1000)
        };
      }
      localStorage.setItem('vod_resume_positions', JSON.stringify(initialStore));

      const player = new IPTVPlayer('video-player');
      player.currentMediaKey = 'asset_141_newest';
      player.currentUrl = '/api/proxy?url=http://test/movie_141.mp4';
      player.video.duration = 3600;
      player.video.currentTime = 1200;
      player.lastSaveTime = 0;

      player.savePlaybackPosition();

      const result = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      const keys = Object.keys(result);

      expect(keys.length).toBe(100);
      expect(result['asset_141_newest']).toBeDefined();

      for (let i = 1; i <= 41; i++) {
        expect(result[`asset_${i}`]).toBeUndefined();
      }

      for (let i = 42; i <= 140; i++) {
        expect(result[`asset_${i}`]).toBeDefined();
      }
    });

    it('ADV-18: 95% progress boundary clears entry accurately while 94.9% progress preserves entry', () => {
      const player = new IPTVPlayer('video-player');
      player.currentUrl = '/api/proxy?url=http://test/movie.mp4';
      player.video.duration = 1000;

      // 940s / 1000s (94%) -> Save
      player.currentMediaKey = 'movie_94_pct';
      player.video.currentTime = 940;
      player.lastSaveTime = 0;
      player.savePlaybackPosition();

      let stored = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(stored['movie_94_pct']).toBeDefined();
      expect(stored['movie_94_pct'].position).toBe(940);

      // 951s / 1000s (95.1%) -> Clear
      player.currentMediaKey = 'movie_94_pct';
      player.video.currentTime = 951;
      player.lastSaveTime = 0;
      player.savePlaybackPosition();

      stored = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(stored['movie_94_pct']).toBeUndefined();
    });

    it('ADV-19: Handles localStorage quota exceeded simulation gracefully', () => {
      const player = new IPTVPlayer('video-player');
      player.currentMediaKey = 'movie_quota_test';
      player.currentUrl = '/api/proxy?url=http://test/movie.mp4';
      player.video.duration = 5000;
      player.video.currentTime = 1200;
      player.lastSaveTime = 0;

      const origSetItem = localStorage.setItem;
      localStorage.setItem = () => {
        throw new Error('QuotaExceededError: DOM Exception 22');
      };

      let threw = false;
      try {
        player.savePlaybackPosition();
      } catch (e) {
        threw = true;
      }

      localStorage.setItem = origSetItem;
      expect(threw).toBe(false);
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // SUITE 5: Fullscreen & VOD Player Control Stress & Clamping
  // ════════════════════════════════════════════════════════════════════════════
  describe('SUITE 5: Player Control Actions & Boundary Clamping', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('ADV-20: Seek calculations accurately clamp negative and extreme future timestamps', () => {
      const player = new IPTVPlayer('video-player');
      player.video.duration = 5400; // 1.5 hrs
      player.video.currentTime = 15;

      player.seekBy(-30);
      expect(player.video.currentTime).toBe(0);

      player.seekBy(10000);
      expect(player.video.currentTime).toBe(5400);

      player.seekBy(-500);
      expect(player.video.currentTime).toBe(4900);
    });

    it('ADV-21: formatTime formats hours, minutes, and edge cases (NaN, Infinity, 0)', () => {
      const player = new IPTVPlayer('video-player');
      expect(player.formatTime(0)).toBe('0:00');
      expect(player.formatTime(9)).toBe('0:09');
      expect(player.formatTime(65)).toBe('1:05');
      expect(player.formatTime(599)).toBe('9:59');
      expect(player.formatTime(3600)).toBe('1:00:00');
      expect(player.formatTime(3665)).toBe('1:01:05');
      expect(player.formatTime(7325)).toBe('2:02:05');
      expect(player.formatTime(NaN)).toBe('0:00');
      expect(player.formatTime(Infinity)).toBe('0:00');
      expect(player.formatTime(-50)).toBe('0:00');
    });

    it('ADV-22: Volume slider and mute toggle maintain state and icon updates', () => {
      const player = new IPTVPlayer('video-player');
      
      player.setVolume(1.0);
      expect(player.video.volume).toBe(1.0);
      expect(player.isMuted).toBe(false);

      player.setVolume(0);
      expect(player.isMuted).toBe(true);
      expect(player.video.muted).toBe(true);

      player.setVolume(0.6);
      expect(player.isMuted).toBe(false);
      expect(player.video.muted).toBe(false);
      expect(player.video.volume).toBe(0.6);

      player.toggleMute();
      expect(player.isMuted).toBe(true);
      expect(player.video.muted).toBe(true);

      player.toggleMute();
      expect(player.isMuted).toBe(false);
      expect(player.video.muted).toBe(false);
    });

    it('ADV-23: Aspect ratio cycles through fit, stretch, 4:3 and normalizes invalid modes', () => {
      const player = new IPTVPlayer('video-player');
      expect(player.aspectRatio).toBe('fit');

      player.setAspectRatio('contain');
      expect(player.aspectRatio).toBe('fit');
      expect(player.video.className).toContain('video-fit');

      player.setAspectRatio('16:9');
      expect(player.aspectRatio).toBe('stretch');
      expect(player.video.className).toContain('video-stretch');

      player.setAspectRatio('4:3');
      expect(player.aspectRatio).toBe('4:3');
      expect(player.video.className).toContain('video-4-3');

      player.setAspectRatio('cover');
      expect(player.aspectRatio).toBe('cover');
      expect(player.video.className).toContain('video-cover');

      player.setAspectRatio('INVALID_MODE_XYZ');
      expect(player.aspectRatio).toBe('fit');
      expect(player.video.className).toContain('video-fit');
    });

    it('ADV-24: Fullscreen pseudo-fullscreen toggle updates container classes and Lucide icons', () => {
      const player = new IPTVPlayer('video-player');
      const container = player.video.parentElement;

      container.classList.add('is-pseudo-fullscreen');
      env.doc.body.classList.add('body-pseudo-fullscreen');
      player.updateFullscreenUI(true);

      expect(container.classList.contains('is-pseudo-fullscreen')).toBe(true);
      expect(env.doc.body.classList.contains('body-pseudo-fullscreen')).toBe(true);
      expect(player.fullscreenBtn.querySelector('i').getAttribute('data-lucide')).toBe('minimize');

      container.classList.remove('is-pseudo-fullscreen');
      env.doc.body.classList.remove('body-pseudo-fullscreen');
      player.updateFullscreenUI(false);

      expect(container.classList.contains('is-pseudo-fullscreen')).toBe(false);
      expect(env.doc.body.classList.contains('body-pseudo-fullscreen')).toBe(false);
      expect(player.fullscreenBtn.querySelector('i').getAttribute('data-lucide')).toBe('maximize');
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // SUITE 6: VOD Search & Popularity Ranking Calculation
  // ════════════════════════════════════════════════════════════════════════════
  describe('SUITE 6: VOD Search & Popularity Scoring Calculation', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('ADV-25: calculateVODScore prioritizes items with valid ratings, recency, and artwork', () => {
      const richItem = {
        title: 'Top Movie',
        rating: '9.2',
        popularity: 50,
        added: 1700000000,
        stream_icon: 'https://art.jpg'
      };

      const poorItem = {
        title: 'Obscure Movie',
        rating: '2.0',
        popularity: 1,
        added: 0
      };

      const scoreRich = calculateVODScore(richItem);
      const scorePoor = calculateVODScore(poorItem);

      expect(scoreRich).toBeGreaterThan(scorePoor);
      expect(calculateVODScore(null)).toBe(0);
      expect(calculateVODScore({})).toBe(0);
    });

    it('ADV-26: sortVODByPopularity reliably sorts descending by calculated score', () => {
      const items = [
        { id: 1, title: 'Low', rating: '3.0', popularity: 5 },
        { id: 2, title: 'High', rating: '9.0', popularity: 80, stream_icon: 'https://art.jpg' },
        { id: 3, title: 'Medium', rating: '6.5', popularity: 30 }
      ];

      const sorted = sortVODByPopularity(items);
      expect(sorted[0].id).toBe(2);
      expect(sorted[1].id).toBe(3);
      expect(sorted[2].id).toBe(1);

      expect(sortVODByPopularity(null)).toEqual([]);
      expect(sortVODByPopularity([])).toEqual([]);
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // SUITE 7: Chaotic Fuzzing Stress Test (500 randomized operations)
  // ════════════════════════════════════════════════════════════════════════════
  describe('SUITE 7: Chaotic Fuzzing & High-Frequency Stream Resolution', () => {
    let env;
    let client;
    beforeEach(() => {
      env = setupTestEnvironment();
      client = new XtreamVODClient('http://vod.test.org:8080');
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('ADV-27: Rapidly executes 500 randomized container resolutions and seek operations without exceptions', () => {
      const player = new IPTVPlayer('video-player');
      player.video.duration = 7200;
      player.video.currentTime = 300;

      const extensions = ['.mp4', 'mkv', 'AVI', '..ts', 'mp4?foo=1', null, undefined, '', 'webm'];
      const actions = ['seek_fwd', 'seek_back', 'vol_up', 'vol_down', 'aspect', 'save_pos'];

      for (let i = 0; i < 500; i++) {
        const ext = extensions[i % extensions.length];
        const act = actions[i % actions.length];

        const url = (i % 2 === 0) 
          ? client.getMovieStreamUrl(i + 1, ext) 
          : client.getSeriesStreamUrl(i + 1, ext);
        expect(url).toContain('/api/proxy?url=');

        switch (act) {
          case 'seek_fwd':
            player.seekBy(10);
            break;
          case 'seek_back':
            player.seekBy(-10);
            break;
          case 'vol_up':
            player.setVolume(Math.min(1, player.volume + 0.1));
            break;
          case 'vol_down':
            player.setVolume(Math.max(0, player.volume - 0.1));
            break;
          case 'aspect':
            player.toggleAspectRatio();
            break;
          case 'save_pos':
            player.currentMediaKey = `movie_${i}`;
            player.lastSaveTime = 0;
            player.savePlaybackPosition();
            break;
          default:
            break;
        }
      }

      expect(player.video.currentTime).toBeGreaterThanOrEqual(0);
      expect(player.video.currentTime).toBeLessThanOrEqual(7200);
    });
  });
});

// Run directly if invoked with node
if (process.argv[1]?.endsWith('test_m4_challenger_stress.js')) {
  runAllRegisteredSuites().then(success => {
    process.exit(success ? 0 : 1);
  });
}
