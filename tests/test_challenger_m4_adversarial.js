/**
 * Challenger Adversarial Stress Test Suite for Milestone 4 (Xtream VOD Engine)
 * ─────────────────────────────────────────────────────────────────────────────
 * Independent adversarial verification covering:
 * 1. Container Extension Fuzzing & Unusual Inputs
 * 2. LRU Resume Tracking Boundary Analysis & 150-Item Pruning Stress
 * 3. VOD Seek Control Clamping & Lifecycle Mode Transitions
 * 4. Resiliency under Corrupt Storage & Malformed Payloads
 * 5. Player Time Formatting & Null Safety Under Stress
 */

import {
  describe,
  it,
  expect,
  beforeEach,
  afterEach,
  setupTestEnvironment,
  restoreTestEnvironment,
  runAllRegisteredSuites
} from './harness.js';

import { XtreamVODClient, xtreamVOD, getSafeImageUrl, calculateVODScore } from '../src/xtreamVODService.js';
import { IPTVPlayer } from '../src/player.js';

describe('CHALLENGER ADVERSARIAL: Milestone 4 Xtream VOD Engine', () => {

  // ════════════════════════════════════════════════════════════════════════════
  // 1. Container Extension Fuzzing & Unusual Inputs
  // ════════════════════════════════════════════════════════════════════════════
  describe('1. Container Extension Fuzzing & Unusual Inputs', () => {
    let client;
    beforeEach(() => {
      client = new XtreamVODClient('http://stream.server.org:8000', 'testuser', 'secretpass');
    });

    it('ADV-01: Fuzzes container extensions with uppercase, mixed case, and spaces', () => {
      const testCases = [
        { input: 'MKV', expectedExt: 'mkv' },
        { input: 'Mp4', expectedExt: 'mp4' },
        { input: '  avi  ', expectedExt: 'avi' },
        { input: '\tTS\n', expectedExt: 'ts' },
        { input: 'FLV', expectedExt: 'flv' },
        { input: '  webm  ', expectedExt: 'webm' }
      ];

      for (const tc of testCases) {
        const movieUrl = client.getMovieStreamUrl(101, tc.input);
        const seriesUrl = client.getSeriesStreamUrl(202, tc.input);

        expect(movieUrl).toContain(`101.${tc.expectedExt}`);
        expect(seriesUrl).toContain(`202.${tc.expectedExt}`);
        expect(movieUrl).not.toContain('..');
        expect(seriesUrl).not.toContain('..');
      }
    });

    it('ADV-02: Handles single leading dot (.mkv -> 101.mkv)', () => {
      const dotCases = [
        { input: '.mp4', expected: '101.mp4' },
        { input: '.mkv', expected: '101.mkv' },
        { input: '.avi', expected: '101.avi' },
        { input: '.ts', expected: '101.ts' }
      ];

      for (const dc of dotCases) {
        const url = client.getMovieStreamUrl(101, dc.input);
        expect(url).toContain(dc.expected);
        expect(url).not.toContain('101..');
      }
    });

    it('ADV-03: Fuzzes with query strings, hashes, and parameter injections', () => {
      const queryCases = [
        { input: 'mkv?token=secret123&expire=9999', expected: '101.mkv' },
        { input: '.mp4?sig=abc&h=def', expected: '101.mp4' },
        { input: 'avi?a=1?b=2', expected: '101.avi' }
      ];

      for (const qc of queryCases) {
        const url = client.getSeriesStreamUrl(101, qc.input);
        const decoded = decodeURIComponent(url);
        expect(decoded).toContain(qc.expected);
        expect(decoded).not.toContain('secret123');
        expect(decoded).not.toContain('token=');
      }
    });

    it('ADV-04: Resilient to non-string, empty, null, and undefined inputs', () => {
      const edgeCases = [
        null,
        undefined,
        '',
        '   ',
        123,
        false,
        true,
        {},
        []
      ];

      for (const ec of edgeCases) {
        expect(() => {
          const mUrl = client.getMovieStreamUrl(555, ec);
          const sUrl = client.getSeriesStreamUrl(777, ec);
          expect(mUrl).toContain('555.');
          expect(sUrl).toContain('777.');
        }).not.toThrow();
      }
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 2. LRU Resume Tracking Boundary Analysis & 150-Item Pruning Stress
  // ════════════════════════════════════════════════════════════════════════════
  describe('2. LRU Resume Tracking Boundary Analysis & 150-Item Pruning Stress', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('ADV-05: Precise boundary testing around 10-second threshold (< 10s vs >= 10s)', () => {
      const player = new IPTVPlayer('video-player');
      player.currentUrl = '/api/proxy?url=http://vod.test/movie.mp4';
      player.video.duration = 1000;

      // Case 1: 0 seconds -> not saved
      player.currentMediaKey = 'key_0s';
      player.video.currentTime = 0;
      player.lastSaveTime = null;
      player.savePlaybackPosition();

      // Case 2: 9.9 seconds -> not saved
      player.currentMediaKey = 'key_9_9s';
      player.video.currentTime = 9.9;
      player.lastSaveTime = null;
      player.savePlaybackPosition();

      // Case 3: 10.0 seconds -> not saved (condition is currentTime > 10)
      player.currentMediaKey = 'key_10_0s';
      player.video.currentTime = 10.0;
      player.lastSaveTime = null;
      player.savePlaybackPosition();

      // Case 4: 10.1 seconds -> SAVED
      player.currentMediaKey = 'key_10_1s';
      player.video.currentTime = 10.1;
      player.lastSaveTime = null;
      player.savePlaybackPosition();

      const saved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(saved['key_0s']).toBeUndefined();
      expect(saved['key_9_9s']).toBeUndefined();
      expect(saved['key_10_0s']).toBeUndefined();
      expect(saved['key_10_1s']).toBeDefined();
      expect(saved['key_10_1s'].position).toBe(10);
    });

    it('ADV-06: Precise boundary testing around 95% completion threshold (<= 95% vs > 95%)', () => {
      const player = new IPTVPlayer('video-player');
      player.currentUrl = '/api/proxy?url=http://vod.test/movie.mp4';
      const duration = 1000;
      player.video.duration = duration;

      // Clean storage
      localStorage.removeItem('vod_resume_positions');

      // Case 1: 94.9% (949s) -> SAVED
      player.currentMediaKey = 'key_94_9pct';
      player.video.currentTime = 949;
      player.lastSaveTime = null;
      player.savePlaybackPosition();

      // Case 2: 95.0% (950s) -> SAVED (condition is > 0.95)
      player.currentMediaKey = 'key_95_0pct';
      player.video.currentTime = 950;
      player.lastSaveTime = null;
      player.savePlaybackPosition();

      let saved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(saved['key_94_9pct']).toBeDefined();
      expect(saved['key_94_9pct'].position).toBe(949);
      expect(saved['key_95_0pct']).toBeDefined();
      expect(saved['key_95_0pct'].position).toBe(950);

      // Pre-seed entries for clearing tests
      saved['key_95_1pct'] = { position: 500, duration: 1000, timestamp: Date.now() - 5000 };
      saved['key_100pct'] = { position: 500, duration: 1000, timestamp: Date.now() - 5000 };
      localStorage.setItem('vod_resume_positions', JSON.stringify(saved));

      // Case 3: 95.1% (951s) -> CLEARED
      player.currentMediaKey = 'key_95_1pct';
      player.video.currentTime = 951;
      player.lastSaveTime = null;
      player.savePlaybackPosition();

      // Case 4: 100% (1000s) -> CLEARED
      player.currentMediaKey = 'key_100pct';
      player.video.currentTime = 1000;
      player.lastSaveTime = null;
      player.savePlaybackPosition();

      const finalSaved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(finalSaved['key_94_9pct']).toBeDefined();
      expect(finalSaved['key_95_0pct']).toBeDefined();
      expect(finalSaved['key_95_1pct']).toBeUndefined();
      expect(finalSaved['key_100pct']).toBeUndefined();
    });

    it('ADV-07: Abnormal duration and currentTime values (NaN, Infinity, 0, negative)', () => {
      const player = new IPTVPlayer('video-player');
      player.currentUrl = '/api/proxy?url=http://vod.test/movie.mp4';
      player.currentMediaKey = 'key_abnormal';

      const abnormalCases = [
        { dur: NaN, cur: 50 },
        { dur: Infinity, cur: 50 },
        { dur: 0, cur: 50 },
        { dur: -100, cur: 50 },
        { dur: 1000, cur: -5 }
      ];

      for (const ac of abnormalCases) {
        localStorage.removeItem('vod_resume_positions');
        player.video.duration = ac.dur;
        player.video.currentTime = ac.cur;
        player.lastSaveTime = null;

        expect(() => {
          player.savePlaybackPosition();
        }).not.toThrow();

        const saved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
        expect(saved['key_abnormal']).toBeUndefined();
      }
    });

    it('ADV-08: 150-item mass insertion stress test verifying strict 100-cap LRU pruning', () => {
      const baseTime = 1700000000000;
      const initialStore = {};

      // Seed 150 items with timestamps 1..150
      for (let i = 1; i <= 150; i++) {
        initialStore[`item_${i}`] = {
          position: i * 10,
          duration: 3600,
          timestamp: baseTime + i * 1000
        };
      }
      localStorage.setItem('vod_resume_positions', JSON.stringify(initialStore));

      const player = new IPTVPlayer('video-player');
      player.currentUrl = '/api/proxy?url=http://vod.test/new_movie.mp4';
      player.currentMediaKey = 'item_new_151';
      player.video.duration = 7200;
      player.video.currentTime = 500;
      player.lastSaveTime = null;

      player.savePlaybackPosition();

      const saved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      const savedKeys = Object.keys(saved);

      // Must be capped at exactly 100
      expect(savedKeys.length).toBe(100);

      // The new item must be present
      expect(saved['item_new_151']).toBeDefined();

      // Oldest items (1 through 51) must have been evicted
      for (let i = 1; i <= 51; i++) {
        expect(saved[`item_${i}`]).toBeUndefined();
      }

      // Newest items (52 through 150) must remain
      for (let i = 52; i <= 150; i++) {
        expect(saved[`item_${i}`]).toBeDefined();
      }
    });

    it('ADV-09: LRU touch updates timestamp and protects accessed item from eviction', () => {
      const baseTime = 1700000000000;
      const initialStore = {};

      // Seed 100 items: item_1 to item_100
      for (let i = 1; i <= 100; i++) {
        initialStore[`item_${i}`] = {
          position: i * 10,
          duration: 3600,
          timestamp: baseTime + i * 1000
        };
      }
      localStorage.setItem('vod_resume_positions', JSON.stringify(initialStore));

      const player = new IPTVPlayer('video-player');
      player.video.duration = 3600;

      // Touch item_5 (give it newest timestamp)
      player.currentUrl = '/api/proxy?url=http://vod.test/item_5.mp4';
      player.currentMediaKey = 'item_5';
      player.video.currentTime = 600;
      player.lastSaveTime = null;
      player.savePlaybackPosition();

      // Now insert item_101
      player.currentUrl = '/api/proxy?url=http://vod.test/item_101.mp4';
      player.currentMediaKey = 'item_101';
      player.video.currentTime = 700;
      player.lastSaveTime = null;
      player.savePlaybackPosition();

      const saved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(Object.keys(saved).length).toBe(100);

      // item_1 was oldest -> evicted
      expect(saved['item_1']).toBeUndefined();
      // item_5 was refreshed -> MUST BE PRESENT
      expect(saved['item_5']).toBeDefined();
      // item_101 was added -> MUST BE PRESENT
      expect(saved['item_101']).toBeDefined();
    });

    it('ADV-10: Handles corrupted JSON in localStorage gracefully without throwing', () => {
      localStorage.setItem('vod_resume_positions', '{corrupted::invalid_json[[[');

      const player = new IPTVPlayer('video-player');
      player.currentUrl = '/api/proxy?url=http://vod.test/movie.mp4';
      player.currentMediaKey = 'item_resilience';
      player.video.duration = 3600;
      player.video.currentTime = 300;
      player.lastSaveTime = null;

      expect(() => {
        player.savePlaybackPosition();
      }).not.toThrow();

      const saved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(saved['item_resilience']).toBeDefined();
    });

    it('ADV-11: 5-second throttling prevents redundant localStorage disk writes', () => {
      const player = new IPTVPlayer('video-player');
      player.currentUrl = '/api/proxy?url=http://vod.test/movie.mp4';
      player.currentMediaKey = 'item_throttle';
      player.video.duration = 3600;
      player.video.currentTime = 200;
      player.lastSaveTime = null;

      // 1st save -> succeeds
      player.savePlaybackPosition();
      let saved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(saved['item_throttle'].position).toBe(200);

      // 2nd save immediately after (currentTime = 204) -> throttled (ignored)
      player.video.currentTime = 204;
      player.savePlaybackPosition();
      saved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(saved['item_throttle'].position).toBe(200); // Unchanged

      // Advance time > 5000ms
      player.lastSaveTime = Date.now() - 5001;
      player.video.currentTime = 210;
      player.savePlaybackPosition();
      saved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(saved['item_throttle'].position).toBe(210); // Updated
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 3. VOD Seek Control Clamping & Lifecycle Mode Transitions
  // ════════════════════════════════════════════════════════════════════════════
  describe('3. VOD Seek Control Clamping & Lifecycle Mode Transitions', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('ADV-12: Seek underflow and overflow clamping tests with various increments', () => {
      const player = new IPTVPlayer('video-player');
      player.video.duration = 500;
      player.video.currentTime = 5;

      // Seek -10 from 5 -> clamps to 0
      player.seekBy(-10);
      expect(player.video.currentTime).toBe(0);

      // Seek -30 from 0 -> stays 0
      player.seekBy(-30);
      expect(player.video.currentTime).toBe(0);

      // Seek +30 from 0 -> 30
      player.seekBy(30);
      expect(player.video.currentTime).toBe(30);

      // Seek +490 from 30 -> 500 (max)
      player.seekBy(490);
      expect(player.video.currentTime).toBe(500);

      // Seek +10 beyond max -> stays 500
      player.seekBy(10);
      expect(player.video.currentTime).toBe(500);
    });

    it('ADV-13: Full playback mode transitions (Live TV HLS -> VOD MP4 -> VOD MKV -> Live TV HLS)', () => {
      const player = new IPTVPlayer('video-player');

      // 1. Start in Live TV mode with simulated HLS stream
      player.setControlMode('live');
      const controlsBar = document.getElementById('video-controls');
      expect(controlsBar.classList.contains('live-mode')).toBe(true);
      expect(controlsBar.classList.contains('vod-mode')).toBe(false);

      // Simulate active HLS
      player.hls = {
        destroy: () => { player.hls = null; },
        startLoad: () => {},
        stopLoad: () => {}
      };

      // 2. Switch to VOD Movie (MP4)
      const movieUrl = '/api/proxy?url=' + encodeURIComponent('http://iptv.server:8080/movie/user/pass/999.mp4');
      player.playChannel({
        url: movieUrl,
        name: 'The Matrix (1999)',
        mediaKey: 'movie_999'
      });
      player.setControlMode('vod');

      expect(player.hls).toBeNull(); // HLS destroyed for direct VOD
      expect(player.video.src).toBe(movieUrl);
      expect(controlsBar.classList.contains('vod-mode')).toBe(true);
      expect(controlsBar.classList.contains('live-mode')).toBe(false);

      // 3. Switch to VOD Series Episode (MKV)
      const episodeUrl = '/api/proxy?url=' + encodeURIComponent('http://iptv.server:8080/series/user/pass/555.mkv');
      player.playChannel({
        url: episodeUrl,
        name: 'Breaking Bad S01E01',
        mediaKey: 'series_555'
      });

      expect(player.video.src).toBe(episodeUrl);
      expect(player.currentMediaKey).toBe('series_555');

      // 4. Switch back to Live TV
      player.setControlMode('live');
      expect(controlsBar.classList.contains('live-mode')).toBe(true);
      expect(controlsBar.classList.contains('vod-mode')).toBe(false);
    });

    it('ADV-14: VOD Details Modal error resilience with missing/malformed series data', () => {
      // Test calculateVODScore with empty / missing values
      expect(calculateVODScore({})).toBe(0);
      expect(calculateVODScore(null)).toBe(0);
      expect(calculateVODScore(undefined)).toBe(0);
      expect(calculateVODScore({ rating: 'invalid', rating_5based: 'none' })).toBe(0);
      expect(calculateVODScore({ rating: '8.5' })).toBeGreaterThan(0);
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 4. Player Time Formatting & Null Safety Under Stress
  // ════════════════════════════════════════════════════════════════════════════
  describe('4. Player Time Formatting & Null Safety Under Stress', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('ADV-15: Time formatting verifies standard and edge cases (0, multi-minute, multi-hour, NaN, Infinity)', () => {
      const player = new IPTVPlayer('video-player');

      expect(player.formatTime(0)).toBe('0:00');
      expect(player.formatTime(59)).toBe('0:59');
      expect(player.formatTime(60)).toBe('1:00');
      expect(player.formatTime(3599)).toBe('59:59');
      expect(player.formatTime(3600)).toBe('1:00:00');
      expect(player.formatTime(3665)).toBe('1:01:05');
      expect(player.formatTime(NaN)).toBe('0:00');
      expect(player.formatTime(Infinity)).toBe('0:00');
    });

    it('ADV-16: Null safety when player controls are manipulated without loaded media', () => {
      const player = new IPTVPlayer('video-player');
      player.currentUrl = null;
      player.video.src = '';

      expect(() => {
        player.togglePlay();
        player.seekBy(10);
        player.seekBy(-10);
        player.savePlaybackPosition();
        player.setControlMode('vod');
        player.setControlMode('live');
      }).not.toThrow();
    });
  });
});

// Run directly if invoked with node
if (process.argv[1]?.endsWith('test_challenger_m4_adversarial.js')) {
  runAllRegisteredSuites().then(success => {
    process.exit(success ? 0 : 1);
  });
}
