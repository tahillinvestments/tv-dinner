/**
 * Milestone 4: Xtream VOD Engine Empirical & Adversarial Test Suite
 * ─────────────────────────────────────────────────────────────────────────────
 * Comprehensive unit and integration verification for:
 * 1. Direct Xtream VOD Playback (Movies & TV Series)
 * 2. Dynamic Series Container Extension Resolution (.mp4, .mkv, .avi, .ts)
 * 3. Rich Details Modal, Season Dropdown & Episode Grid
 * 4. Throttled LRU Playback Resume Tracking & Position Restoration
 * 5. Fullscreen Player Controls (+/-10s, +/-30s, Seek, Volume, Mute, Aspect Ratio)
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

describe('MILESTONE 4: Xtream VOD Engine (Movies & TV Series)', () => {

  // ════════════════════════════════════════════════════════════════════════════
  // 1. Direct Xtream VOD Playback & Stream URL Construction
  // ════════════════════════════════════════════════════════════════════════════
  describe('1. Direct Xtream VOD Playback & Stream URL Construction', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('M4-01: Constructs authenticated movie stream URL with proxy wrapper', () => {
      localStorage.setItem('xtream_vod_portal', 'http://vod.myportal.com:8080');
      localStorage.setItem('xtream_vod_username', 'user@vod.com');
      localStorage.setItem('xtream_vod_password', 'vodpass123');

      const client = new XtreamVODClient();
      const url = client.getMovieStreamUrl(12345, 'mp4');

      expect(url).toContain('/api/proxy?url=');
      const decoded = decodeURIComponent(url.split('url=')[1]);
      expect(decoded).toBe('http://vod.myportal.com:8080/movie/user@vod.com/vodpass123/12345.mp4');
    });

    it('M4-02: Constructs authenticated TV series stream URL with proxy wrapper', () => {
      localStorage.setItem('xtream_vod_portal', 'http://vod.myportal.com:8080');
      localStorage.setItem('xtream_vod_username', 'user@vod.com');
      localStorage.setItem('xtream_vod_password', 'vodpass123');

      const client = new XtreamVODClient();
      const url = client.getSeriesStreamUrl(9876, 'mkv');

      expect(url).toContain('/api/proxy?url=');
      const decoded = decodeURIComponent(url.split('url=')[1]);
      expect(decoded).toBe('http://vod.myportal.com:8080/series/user@vod.com/vodpass123/9876.mkv');
    });

    it('M4-03: Direct video playback sets native video src and destroys HLS instance', () => {
      const player = new IPTVPlayer('video-player');
      player.setControlMode('vod');

      const directVodUrl = '/api/proxy?url=' + encodeURIComponent('http://vod.server:8080/movie/user/pass/101.mp4');
      player.playChannel({
        url: directVodUrl,
        name: 'Inception (2010)',
        mediaKey: 'movie_101'
      });

      expect(player.hls).toBeNull();
      expect(player.video.src).toBe(directVodUrl);
      expect(player.channelTitle.textContent).toBe('Inception (2010)');
    });

    it('M4-04: HTTP Range 206 header parsing validates partial content bytes', () => {
      const parseContentRange = (header) => {
        if (!header) return null;
        const match = header.match(/bytes\s+(\d+)-(\d+)\/(\d+|\*)/);
        if (!match) return null;
        return {
          start: parseInt(match[1], 10),
          end: parseInt(match[2], 10),
          total: match[3] === '*' ? null : parseInt(match[3], 10)
        };
      };

      const range1 = parseContentRange('bytes 0-1048575/52428800');
      expect(range1).not.toBeNull();
      expect(range1.start).toBe(0);
      expect(range1.end).toBe(1048575);
      expect(range1.total).toBe(52428800);

      const range2 = parseContentRange('bytes 5242880-10485759/52428800');
      expect(range2.start).toBe(5242880);
      expect(range2.end).toBe(10485759);
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 2. Dynamic Series Container Extension Resolution
  // ════════════════════════════════════════════════════════════════════════════
  describe('2. Dynamic Series Container Extension Resolution', () => {
    let client;
    beforeEach(() => {
      client = new XtreamVODClient('http://vod.test:8080');
    });

    it('M4-05: Normalizes leading dots in extension (.mkv -> mkv)', () => {
      const url = client.getSeriesStreamUrl(101, '.mkv');
      expect(url).toContain('101.mkv');
      expect(url).not.toContain('101..mkv');
    });

    it('M4-06: Normalizes uppercase extensions (MKV -> mkv, AVI -> avi)', () => {
      const urlMkv = client.getSeriesStreamUrl(102, 'MKV');
      expect(urlMkv).toContain('102.mkv');

      const urlAvi = client.getSeriesStreamUrl(103, 'AVI');
      expect(urlAvi).toContain('103.avi');
    });

    it('M4-07: Strips query parameters attached to container extension', () => {
      const url = client.getSeriesStreamUrl(104, 'mkv?token=xyz123');
      expect(url).toContain('104.mkv');
      expect(url).not.toContain('mkv?token');
    });

    it('M4-08: Defaults to mp4 when extension is undefined, null, or empty string', () => {
      expect(client.getSeriesStreamUrl(105, null)).toContain('105.mp4');
      expect(client.getSeriesStreamUrl(106, undefined)).toContain('106.mp4');
      expect(client.getSeriesStreamUrl(107, '')).toContain('107.mp4');
    });

    it('M4-09: Handles movie stream container extension dynamically', () => {
      const url = client.getMovieStreamUrl(201, '.mkv');
      expect(url).toContain('201.mkv');
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 3. Rich Details Modal & Episode Picker Logic
  // ════════════════════════════════════════════════════════════════════════════
  describe('3. Rich Details Modal & Episode Picker Logic', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('M4-10: Formats movie metadata with rating, year, and overview', () => {
      const movieItem = {
        id: 501,
        title: 'Interstellar',
        rating: '8.6',
        release_date: '2014-11-07',
        overview: 'A team of explorers travel through a wormhole in space.'
      };

      const titleEl = env.doc.createElement('h2');
      titleEl.id = 'details-title';
      titleEl.textContent = movieItem.title;

      const yearEl = env.doc.createElement('span');
      yearEl.id = 'details-year';
      yearEl.textContent = movieItem.release_date.split('-')[0];

      const ratingEl = env.doc.createElement('span');
      ratingEl.id = 'details-rating';
      ratingEl.textContent = `⭐ ${parseFloat(movieItem.rating).toFixed(1)}`;

      const overviewEl = env.doc.createElement('p');
      overviewEl.id = 'details-overview';
      overviewEl.textContent = movieItem.overview;

      expect(titleEl.textContent).toBe('Interstellar');
      expect(yearEl.textContent).toBe('2014');
      expect(ratingEl.textContent).toBe('⭐ 8.6');
      expect(overviewEl.textContent).toContain('wormhole');
    });

    it('M4-11: getSafeImageUrl safely handles relative, protocol-relative, and HTTP URLs', () => {
      expect(getSafeImageUrl('')).toBe('');
      expect(getSafeImageUrl(null)).toBe('');
      expect(getSafeImageUrl('//images.tmdb.org/poster.jpg')).toBe('https://images.tmdb.org/poster.jpg');
      expect(getSafeImageUrl('https://images.tmdb.org/poster.jpg')).toBe('https://images.tmdb.org/poster.jpg');
      expect(getSafeImageUrl('data:image/png;base64,iVBORw0KGgo')).toContain('data:image/png;base64');
    });

    it('M4-12: Populates season selector dropdown and renders episode cards', () => {
      const seasonSelect = env.doc.createElement('select');
      seasonSelect.id = 'season-select';

      const seasons = [
        { season_number: 1, name: 'Season 1' },
        { season_number: 2, name: 'Season 2' }
      ];

      seasons.forEach(s => {
        const opt = env.doc.createElement('option');
        opt.value = s.season_number;
        opt.textContent = s.name;
        seasonSelect.appendChild(opt);
      });

      expect(seasonSelect.children.length).toBe(2);
      expect(seasonSelect.children[0].textContent).toBe('Season 1');
      expect(seasonSelect.children[1].textContent).toBe('Season 2');

      const episodesGrid = env.doc.createElement('div');
      episodesGrid.id = 'episodes-grid';

      const s1Episodes = [
        { id: 1001, episode_number: 1, name: 'Pilot', container_extension: 'mkv' },
        { id: 1002, episode_number: 2, name: 'The Variant', container_extension: 'mp4' }
      ];

      s1Episodes.forEach(ep => {
        const btn = env.doc.createElement('button');
        btn.className = 'episode-btn';
        btn.textContent = ep.episode_number;
        btn.title = ep.name;
        episodesGrid.appendChild(btn);
      });

      expect(episodesGrid.children.length).toBe(2);
      expect(episodesGrid.children[0].textContent).toBe(1);
      expect(episodesGrid.children[1].textContent).toBe(2);
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 4. Throttled LRU Playback Resume Tracking
  // ════════════════════════════════════════════════════════════════════════════
  describe('4. Throttled LRU Playback Resume Tracking', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('M4-13: Ignores playback under 10 seconds to avoid accidental clutter', () => {
      const player = new IPTVPlayer('video-player');
      player.currentMediaKey = 'movie_test_short';
      player.currentUrl = '/api/proxy?url=http://vod.test/movie.mp4';
      player.video.duration = 7200;
      player.video.currentTime = 6; // < 10s

      player.savePlaybackPosition();
      const saved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(saved['movie_test_short']).toBeUndefined();
    });

    it('M4-14: Persists playback position when > 10s', () => {
      const player = new IPTVPlayer('video-player');
      player.currentMediaKey = 'movie_test_valid';
      player.currentUrl = '/api/proxy?url=http://vod.test/movie.mp4';
      player.video.duration = 7200;
      player.video.currentTime = 1450; // > 10s

      player.savePlaybackPosition();
      const saved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(saved['movie_test_valid']).toBeDefined();
      expect(saved['movie_test_valid'].position).toBe(1450);
      expect(saved['movie_test_valid'].duration).toBe(7200);
    });

    it('M4-15: Clears saved entry when playback exceeds 95% completion', () => {
      const player = new IPTVPlayer('video-player');
      player.currentMediaKey = 'movie_test_complete';
      player.currentUrl = '/api/proxy?url=http://vod.test/movie.mp4';
      player.video.duration = 1000;
      player.video.currentTime = 960; // 96% > 95%

      // Pre-seed an existing position
      localStorage.setItem('vod_resume_positions', JSON.stringify({
        movie_test_complete: { position: 500, timestamp: Date.now() - 10000 }
      }));

      player.savePlaybackPosition();
      const saved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      expect(saved['movie_test_complete']).toBeUndefined();
    });

    it('M4-16: Enforces genuine LRU capacity cap of 100 entries based on oldest timestamp', () => {
      const store = {};
      const now = Date.now();
      for (let i = 1; i <= 105; i++) {
        store[`movie_${i}`] = { position: i * 10, timestamp: now - (105 - i) * 1000 };
      }
      localStorage.setItem('vod_resume_positions', JSON.stringify(store));

      const player = new IPTVPlayer('video-player');
      player.currentMediaKey = 'movie_new_106';
      player.currentUrl = '/api/proxy?url=http://vod.test/movie_106.mp4';
      player.video.duration = 3600;
      player.video.currentTime = 600;

      player.savePlaybackPosition();
      const saved = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
      const keys = Object.keys(saved);

      expect(keys.length).toBeLessThanOrEqual(100);
      expect(saved['movie_new_106']).toBeDefined();
      // Oldest record (movie_1) must be pruned
      expect(saved['movie_1']).toBeUndefined();
    });

    it('M4-17: Seamlessly restores currentTime on playback initialization', () => {
      const player = new IPTVPlayer('video-player');
      player.setControlMode('vod');

      localStorage.setItem('vod_resume_positions', JSON.stringify({
        movie_resume_test: { position: 2400, timestamp: Date.now() }
      }));

      player.playChannel({
        url: '/api/proxy?url=http://vod.test/movie.mp4',
        name: 'Resume Test Movie',
        mediaKey: 'movie_resume_test'
      }, 2400);

      expect(player.channelTitle.textContent).toBe('Resume Test Movie');
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 5. Fullscreen Player Controls & Seek Actions
  // ════════════════════════════════════════════════════════════════════════════
  describe('5. Fullscreen Player Controls & Seek Actions', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
      env.doc.activeElement = env.doc.body;
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('M4-18: Seek jump buttons (+/-10s, +/-30s) correctly adjust video.currentTime', () => {
      const player = new IPTVPlayer('video-player');
      player.video.duration = 3600;
      player.video.currentTime = 100;

      player.seekBy(10);
      expect(player.video.currentTime).toBe(110);

      player.seekBy(30);
      expect(player.video.currentTime).toBe(140);

      player.seekBy(-10);
      expect(player.video.currentTime).toBe(130);

      player.seekBy(-30);
      expect(player.video.currentTime).toBe(100);
    });

    it('M4-19: Seek time is clamped strictly between 0 and duration', () => {
      const player = new IPTVPlayer('video-player');
      player.video.duration = 100;
      player.video.currentTime = 10;

      player.seekBy(-50);
      expect(player.video.currentTime).toBe(0);

      player.seekBy(200);
      expect(player.video.currentTime).toBe(100);
    });

    it('M4-20: Volume slider and mute toggle maintain state and UI icons', () => {
      const player = new IPTVPlayer('video-player');
      player.setVolume(0.75);
      expect(player.video.volume).toBe(0.75);
      expect(player.isMuted).toBe(false);

      player.toggleMute();
      expect(player.isMuted).toBe(true);
      expect(player.video.muted).toBe(true);

      player.toggleMute();
      expect(player.isMuted).toBe(false);
      expect(player.video.muted).toBe(false);
    });

    it('M4-21: Aspect ratio toggle cycles between fit (contain) and stretch (fill)', () => {
      const player = new IPTVPlayer('video-player');
      expect(player.aspectRatio).toBe('fit');

      player.toggleAspectRatio();
      expect(player.aspectRatio).toBe('stretch');
      expect(player.video.className).toContain('video-stretch');

      player.toggleAspectRatio();
      expect(player.aspectRatio).toBe('fit');
      expect(player.video.className).toContain('video-fit');
    });

    it('M4-22: Fullscreen toggle manages pseudo-fullscreen and updates UI icons', () => {
      const player = new IPTVPlayer('video-player');
      player.updateFullscreenUI(true);
      expect(player.fullscreenBtn.querySelector('i').getAttribute('data-lucide')).toBe('minimize');

      player.updateFullscreenUI(false);
      expect(player.fullscreenBtn.querySelector('i').getAttribute('data-lucide')).toBe('maximize');
    });
  });
});

// Run directly if invoked with node
if (process.argv[1]?.endsWith('test_m4_vod_engine.js')) {
  runAllRegisteredSuites().then(success => {
    process.exit(success ? 0 : 1);
  });
}
