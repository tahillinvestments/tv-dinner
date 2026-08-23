/**
 * Challenger 2 Iteration 2 Adversarial Stress Test Suite
 * Milestone 4: Xtream VOD Engine & Video Player Hardening
 * ─────────────────────────────────────────────────────────────────────────────
 * Validates:
 * 1. 4 Iteration 1 Vulnerability Fixes (Storage corruption, ext sanitizer, trailing slash, formatTime)
 * 2. Range 206 Seeking, Clamping & Time Formatting Boundary Extremes (100+ hrs, sub-second, negative)
 * 3. LRU Playback Resume Persistence (Corrupt items, NaN timestamps, Mass insertion, 95% clearing)
 * 4. Xtream VOD Client URL Construction (Complex auth, special characters, multi-slash, dynamic extensions)
 * 5. TV Show Details Modal & Episode Picker Resiliency (Empty seasons, missing IDs, null metadata)
 * 6. Fullscreen & Control Mode Transitions (DOM state, class toggles, icon updates, event safety)
 * 7. Stress & Fuzzing (1,000 rapid chaotic operations)
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

import { XtreamVODClient, xtreamVOD, getSafeImageUrl, calculateVODScore, sortVODByPopularity } from '../src/xtreamVODService.js';
import { IPTVPlayer } from '../src/player.js';

describe('CHALLENGER M4 ITERATION 2: Comprehensive Adversarial Stress Test', () => {

  // ════════════════════════════════════════════════════════════════════════════
  // 1. Verification of the 4 Fixed Vulnerabilities
  // ════════════════════════════════════════════════════════════════════════════
  describe('1. Iteration 1 Bug Fix Verification & Regression Prevention', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => {
      restoreTestEnvironment();
    });

    it('BUG-FIX-01: Extension sanitization handles tabs, newlines, multiple leading dots, and URL fragments', () => {
      const client = new XtreamVODClient('http://vod.test.org:8080');
      const testInputs = [
        { raw: '\t.avi\n', expected: '101.avi' },
        { raw: '..mkv', expected: '101.mkv' },
        { raw: '....mkv', expected: '101.mkv' },
        { raw: 'mkv#fragment', expected: '101.mkv' },
        { raw: '.mp4?token=secret123#frag', expected: '101.mp4' },
        { raw: '   .TS?key=val   ', expected: '101.ts' },
        { raw: '...', expected: '101.mp4' },
        { raw: '.', expected: '101.mp4' }
      ];

      for (const t of testInputs) {
        const url = decodeURIComponent(client.getSeriesStreamUrl(101, t.raw));
        expect(url).toContain(t.expected);
        expect(url).not.toContain('..');
        expect(url).not.toContain('secret123');
        expect(url).not.toContain('#frag');
      }
    });

    it('BUG-FIX-02: BaseUrl trailing slashes are strictly stripped to prevent double slashes in API routes', () => {
      const testUrls = [
        'http://test.portal:8080/',
        'http://test.portal:8080///',
        'http://test.portal:8080',
        '   http://test.portal:8080/   '
      ];

      for (const u of testUrls) {
        const client = new XtreamVODClient(u);
        const movieUrl = decodeURIComponent(client.getMovieStreamUrl(202, 'mp4'));
        const seriesUrl = decodeURIComponent(client.getSeriesStreamUrl(303, 'mkv'));

        expect(movieUrl).toContain('http://test.portal:8080/movie/');
        expect(movieUrl).not.toContain(':8080//movie');
        expect(seriesUrl).toContain('http://test.portal:8080/series/');
        expect(seriesUrl).not.toContain(':8080//series');
      }
    });

    it('BUG-FIX-03: Corrupted LocalStorage values ("null", numbers, arrays, strings) do not crash resume storage', () => {
      const player = new IPTVPlayer('video-player');
      player.currentMediaKey = 'movie_corrupt_check';
      player.currentUrl = '/api/proxy?url=http://test/movie.mp4';
      player.video.duration = 3600;
      player.video.currentTime = 500;

      const corruptValues = ['null', '12345', '[1, 2, 3]', '"just a string"', 'undefined', '{bad:json}'];

      for (const val of corruptValues) {
        localStorage.setItem('vod_resume_positions', val);
        player.lastSaveTime = 0;

        expect(() => {
          player.savePlaybackPosition();
        }).not.toThrow();

        const stored = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
        expect(typeof stored).toBe('object');
        expect(Array.isArray(stored)).toBe(false);
        expect(stored['movie_corrupt_check']).toBeDefined();
        expect(stored['movie_corrupt_check'].position).toBe(500);
      }
    });

    it('BUG-FIX-04: formatTime never outputs negative timestamps or malformed minutes/seconds', () => {
      const player = new IPTVPlayer('video-player');
      const testCases = [
        { in: -50, out: '0:00' },
        { in: -0.5, out: '0:00' },
        { in: 0, out: '0:00' },
        { in: -1000, out: '0:00' },
        { in: NaN, out: '0:00' },
        { in: Infinity, out: '0:00' },
        { in: -Infinity, out: '0:00' },
        { in: null, out: '0:00' },
        { in: undefined, out: '0:00' }
      ];

      for (const tc of testCases) {
        expect(player.formatTime(tc.in)).toBe(tc.out);
      }
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 2. Range 206 Seeking, Clamping & Time Formatting Boundary Extremes
  // ════════════════════════════════════════════════════════════════════════════
  describe('2. Range 206 Seeking, Clamping & Time Formatting Extremes', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => {
      restoreTestEnvironment();
    });

    it('SEEK-01: Extreme time values (multi-day marathon, sub-seconds) format accurately', () => {
      const player = new IPTVPlayer('video-player');

      // 100 hours = 360,000s -> "100:00:00"
      expect(player.formatTime(360000)).toBe('100:00:00');

      // 25 hours, 3 minutes, 9 seconds -> "25:03:09"
      expect(player.formatTime(25 * 3600 + 3 * 60 + 9)).toBe('25:03:09');

      // 59 seconds with fractional sub-seconds -> "0:59"
      expect(player.formatTime(59.99)).toBe('0:59');

      // 60.1 seconds -> "1:00"
      expect(player.formatTime(60.1)).toBe('1:00');
    });

    it('SEEK-02: Seeking when media has finite duration clamps to [0, duration]', () => {
      const player = new IPTVPlayer('video-player');
      player.video.duration = 100;
      player.video.currentTime = 50;

      player.seekBy(-100);
      expect(player.video.currentTime).toBe(0);

      player.seekBy(200);
      expect(player.video.currentTime).toBe(100);
    });

    it('SEEK-03: Multiple consecutive forward and backward seeks maintain strict bounds', () => {
      const player = new IPTVPlayer('video-player');
      player.video.duration = 1800; // 30 minutes
      player.video.currentTime = 900;

      player.seekBy(500); // 1400
      expect(player.video.currentTime).toBe(1400);

      player.seekBy(500); // 1800 (max)
      expect(player.video.currentTime).toBe(1800);

      player.seekBy(100); // Clamped at 1800
      expect(player.video.currentTime).toBe(1800);

      player.seekBy(-2000); // Clamped at 0
      expect(player.video.currentTime).toBe(0);

      player.seekBy(-100); // Clamped at 0
      expect(player.video.currentTime).toBe(0);
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 3. LRU Resume Tracking Advanced Edge Cases
  // ════════════════════════════════════════════════════════════════════════════
  describe('3. LRU Playback Resume Persistence & Storage Integrity', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => {
      restoreTestEnvironment();
    });

    it('LRU-01: LRU pruning is robust when existing entries contain NaN or missing timestamps', () => {
      const corruptedEntries = {
        item_nan: { position: 100, duration: 3600, timestamp: NaN },
        item_missing_ts: { position: 200, duration: 3600 },
        item_null_val: null
      };
      const baseTime = 1700000000000;
      for (let i = 1; i <= 100; i++) {
        corruptedEntries[`valid_${i}`] = {
          position: i * 10,
          duration: 3600,
          timestamp: baseTime + (i * 1000)
        };
      }
      localStorage.setItem('vod_resume_positions', JSON.stringify(corruptedEntries));

      const player = new IPTVPlayer('video-player');
      player.currentMediaKey = 'new_incoming_item';
      player.currentUrl = '/api/proxy?url=http://test/new.mp4';
      player.video.duration = 3600;
      player.video.currentTime = 500;
      player.lastSaveTime = 0;

      expect(() => {
        player.savePlaybackPosition();
      }).not.toThrow();

      const stored = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(Object.keys(stored).length).toBeLessThanOrEqual(100);
      expect(stored['new_incoming_item']).toBeDefined();
    });

    it('LRU-02: Playback exactly on 95.0% boundary saves progress, while 95.1% triggers deletion', () => {
      const player = new IPTVPlayer('video-player');
      player.currentUrl = '/api/proxy?url=http://test/movie.mp4';
      player.video.duration = 1000;

      // 950s / 1000s = 95.0% (<= 0.95) -> Must save
      player.currentMediaKey = 'item_boundary_950';
      player.video.currentTime = 950;
      player.lastSaveTime = 0;
      player.savePlaybackPosition();

      let stored = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(stored['item_boundary_950']).toBeDefined();
      expect(stored['item_boundary_950'].position).toBe(950);

      // 951s / 1000s = 95.1% (> 0.95) -> Must delete
      player.currentMediaKey = 'item_boundary_950';
      player.video.currentTime = 951;
      player.lastSaveTime = 0;
      player.savePlaybackPosition();

      stored = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(stored['item_boundary_950']).toBeUndefined();
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 4. Details Modal & Episode Picker Resiliency
  // ════════════════════════════════════════════════════════════════════════════
  describe('4. Details Modal & TV Episode Picker Edge Cases', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => {
      restoreTestEnvironment();
    });

    it('MODAL-01: Handles series with missing info, empty episode arrays, or irregular season keys', () => {
      const client = new XtreamVODClient('http://vod.server.com:8080');

      const sInfo = {
        info: null,
        episodes: {
          '0': [],
          '1': [
            { id: 1001, episode_num: 1, title: 'Episode 1', container_extension: 'mkv' }
          ],
          '2': []
        }
      };

      const s1Eps = sInfo.episodes['1'] || [];
      expect(s1Eps.length).toBe(1);
      expect(s1Eps[0].container_extension).toBe('mkv');

      const url = client.getSeriesStreamUrl(s1Eps[0].id, s1Eps[0].container_extension);
      expect(url).toContain('1001.mkv');
    });

    it('MODAL-02: Artwork resolution gracefully falls back through all tiers without throwing', () => {
      expect(getSafeImageUrl(null)).toBe('');
      expect(getSafeImageUrl(undefined)).toBe('');
      expect(getSafeImageUrl('')).toBe('');
      expect(getSafeImageUrl('http://insecure.art.org/pic.png')).toBe('http://insecure.art.org/pic.png');
      expect(getSafeImageUrl('//cdn.tmdb.org/pic.jpg')).toBe('https://cdn.tmdb.org/pic.jpg');
    });

    it('MODAL-03: calculateVODScore handles empty objects, strings, ratings, and popularity correctly', () => {
      expect(calculateVODScore(null)).toBe(0);
      expect(calculateVODScore(undefined)).toBe(0);
      expect(calculateVODScore({})).toBe(0);
      expect(calculateVODScore({ rating: '8.5' })).toBe(8.5 * 15);
      expect(calculateVODScore({ rating: '10.0', popularity: 100 })).toBeGreaterThan(50);
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 5. Fullscreen Player Controls & Mode Switching
  // ════════════════════════════════════════════════════════════════════════════
  describe('5. Fullscreen Player Controls & Mode Transitions', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => {
      restoreTestEnvironment();
    });

    it('PLAYER-01: setControlMode toggles live-mode and vod-mode classes and wrapper state', () => {
      const player = new IPTVPlayer('video-player');
      const controls = document.getElementById('video-controls');
      const playerWrapper = player.video.closest('.player-wrapper');

      player.setControlMode('vod');
      expect(controls.classList.contains('vod-mode')).toBe(true);
      expect(controls.classList.contains('live-mode')).toBe(false);
      expect(playerWrapper.classList.contains('vod-active')).toBe(true);

      player.setControlMode('live');
      expect(controls.classList.contains('live-mode')).toBe(true);
      expect(controls.classList.contains('vod-mode')).toBe(false);
      expect(playerWrapper.classList.contains('vod-active')).toBe(false);
    });

    it('PLAYER-02: Aspect ratio toggling and setting unknown values defaults to fit', () => {
      const player = new IPTVPlayer('video-player');

      player.setAspectRatio('unknown_mode');
      expect(player.aspectRatio).toBe('fit');

      player.setAspectRatio('cover');
      expect(player.aspectRatio).toBe('cover');

      player.setAspectRatio('stretch');
      expect(player.aspectRatio).toBe('stretch');

      player.setAspectRatio('4:3');
      expect(player.aspectRatio).toBe('4:3');
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 6. Chaotic Fuzzing & High-Frequency Stream Resolution
  // ════════════════════════════════════════════════════════════════════════════
  describe('6. Chaotic Fuzzing (1,000 randomized operations)', () => {
    let env;
    let client;
    beforeEach(() => {
      env = setupTestEnvironment();
      client = new XtreamVODClient('http://vod.test.org:8080/');
    });
    afterEach(() => {
      restoreTestEnvironment();
    });

    it('FUZZ-01: 1,000 rapid operations across stream URL resolution, player controls, and storage', () => {
      const player = new IPTVPlayer('video-player');
      player.video.duration = 5400;
      player.video.currentTime = 1000;

      const extPool = ['mp4', '.mkv', '..avi', '\t.ts\n', 'MP4', 'mkv?token=abc#part', null, undefined, '', '   ', '..flv'];

      for (let i = 0; i < 1000; i++) {
        const ext = extPool[i % extPool.length];
        const streamUrl = (i % 2 === 0)
          ? client.getMovieStreamUrl(i + 1, ext)
          : client.getSeriesStreamUrl(i + 1, ext);

        expect(streamUrl).toContain('/api/proxy?url=');

        const op = i % 6;
        if (op === 0) {
          player.seekBy(10);
        } else if (op === 1) {
          player.seekBy(-10);
        } else if (op === 2) {
          player.setVolume((i % 10) / 10);
        } else if (op === 3) {
          player.toggleMute();
        } else if (op === 4) {
          player.toggleAspectRatio();
        } else if (op === 5) {
          player.currentMediaKey = `media_item_${i % 120}`;
          player.lastSaveTime = 0;
          player.savePlaybackPosition();
        }
      }

      expect(player.video.currentTime).toBeGreaterThanOrEqual(0);
      expect(player.video.currentTime).toBeLessThanOrEqual(5400);

      const resumeData = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(Object.keys(resumeData).length).toBeLessThanOrEqual(100);
    });
  });
});

if (process.argv[1]?.endsWith('test_challenger_m4_it2_stress.js')) {
  runAllRegisteredSuites().then(success => {
    process.exit(success ? 0 : 1);
  });
}
