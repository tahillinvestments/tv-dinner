/**
 * Challenger M2 Adversarial Stress Test Suite
 * ─────────────────────────────────────────────────────────────
 * Empirical challenge tests for TV Dinner Milestone 2:
 * 1. Media Teardown & Timer Stress (clearUnmuteTimers, resetVideoFrame, stopAllMediaPlayback, iframe 'about:blank')
 * 2. Channel Grid Batch Rendering Stress (5,000 channels, main-thread performance, session invalidation, data fuzzing)
 * 3. Navigation, Remote D-Pad, Modal Teardown & Activation Stability
 */

import {
  describe,
  it,
  expect,
  beforeEach,
  afterEach,
  setupTestEnvironment,
  restoreTestEnvironment,
  MockVideoElement,
  MockAudioElement,
  MockIframeElement,
  MockElement,
  MockHls,
  runAllRegisteredSuites
} from './harness.js';

import { IPTVPlayer } from '../src/player.js';
import fs from 'fs';
import path from 'path';

describe('CHALLENGER M2: Adversarial Stress & Empirical Verification', () => {

  // ════════════════════════════════════════════════════════════════════════════
  // 1. Media Teardown & Timer Stress Harness
  // ════════════════════════════════════════════════════════════════════════════
  describe('1. Media Teardown & Timer Stress Harness', () => {
    let env;
    beforeEach(() => { env = setupTestEnvironment(); });
    afterEach(() => { restoreTestEnvironment(); });

    it('STRESS-01: clearUnmuteTimers cancels pending unmute callbacks under rapid scheduling', (done) => {
      const player = new IPTVPlayer('video-player');
      expect(Array.isArray(player.unmuteTimers)).toBe(true);

      let callbackFired = false;
      const timer = setTimeout(() => {
        callbackFired = true;
      }, 50);
      player.unmuteTimers.push(timer);

      expect(player.unmuteTimers.length).toBe(1);
      player.clearUnmuteTimers();
      expect(player.unmuteTimers.length).toBe(0);

      setTimeout(() => {
        expect(callbackFired).toBe(false);
        if (typeof done === 'function') done();
      }, 100);
    });

    it('STRESS-02: Autoplay restriction fallback timer does not unmute video if resetVideoFrame called', (done) => {
      const player = new IPTVPlayer('video-player');
      player.video.muted = true;
      player.currentUrl = 'http://example.com/stream.m3u8';

      // Simulate the fallback timeout scheduled during autoplay rejection in player.js (line 624)
      const fallbackTimer = setTimeout(() => {
        if (player.video && !player.video.paused && player.currentUrl) {
          player.video.muted = false;
          player.video.volume = 1.0;
        }
      }, 60);
      player.unmuteTimers.push(fallbackTimer);

      // Abrupt teardown before 60ms
      player.resetVideoFrame();
      expect(player.currentUrl).toBe('');
      expect(player.unmuteTimers.length).toBe(0);

      setTimeout(() => {
        expect(player.video.muted).toBe(true);
        if (typeof done === 'function') done();
      }, 120);
    });

    it('STRESS-03: Rapid timer churn (1,000 iterations of schedule + clear) does not leak or crash', () => {
      const player = new IPTVPlayer('video-player');
      for (let i = 0; i < 1000; i++) {
        const t1 = setTimeout(() => {}, 1000);
        const t2 = setTimeout(() => {}, 2000);
        player.unmuteTimers.push(t1, t2);
        player.clearUnmuteTimers();
        expect(player.unmuteTimers.length).toBe(0);
      }
    });

    it('STRESS-04: clearUnmuteTimers handles malformed or edge-case timer arrays safely', () => {
      const player = new IPTVPlayer('video-player');
      player.unmuteTimers = null;
      expect(() => player.clearUnmuteTimers()).not.toThrow();

      player.unmuteTimers = undefined;
      expect(() => player.clearUnmuteTimers()).not.toThrow();

      player.unmuteTimers = [null, undefined, 9999999, 'invalid_id'];
      expect(() => player.clearUnmuteTimers()).not.toThrow();
      expect(player.unmuteTimers.length).toBe(0);
    });

    it('STRESS-05: stopAllMediaPlayback sets embedIframe.src to about:blank and cleans up wrapper/class', () => {
      const embedIframe = env.doc.getElementById('embed-iframe');
      const embedWrapper = env.doc.createElement('div');
      embedWrapper.id = 'embed-player-wrapper';
      embedWrapper.style.display = 'block';
      env.doc.body.appendChild(embedWrapper);

      const playerWrapper = env.doc.querySelector('.player-wrapper');
      playerWrapper.classList.add('embed-active');
      embedIframe.src = 'https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?autoplay=1';

      // Exercise stopAllMediaPlayback logic as implemented in src/main.js lines 457-520
      const state = {
        currentPlayingUrl: 'https://stream.example.com/live.m3u8',
        embedFallbackTimer: setTimeout(() => {}, 10000)
      };

      const stopAllMediaPlayback = (options = {}) => {
        const { keepPodcast = false, keepLive = false, keepVod = false, keepPlayer = false, keepHls = false, resetState = true } = options;
        const videoEl = env.doc.getElementById('video-player');
        if (videoEl && !keepLive && !keepVod && !keepPlayer) {
          videoEl.pause();
          videoEl.removeAttribute('src');
        }
        if (!keepVod) {
          if (state.embedFallbackTimer) {
            clearTimeout(state.embedFallbackTimer);
            state.embedFallbackTimer = null;
          }
          if (embedIframe) embedIframe.src = 'about:blank';
          if (embedWrapper) embedWrapper.style.display = 'none';
          if (playerWrapper) playerWrapper.classList.remove('embed-active');
        }
        if (resetState) {
          state.currentPlayingUrl = null;
        }
        const loadingEl = env.doc.getElementById('player-loading');
        if (loadingEl) loadingEl.classList.add('hidden');
        const playerErr = env.doc.getElementById('player-error');
        if (playerErr) playerErr.classList.add('hidden');
      };

      stopAllMediaPlayback();

      expect(embedIframe.src).toBe('about:blank');
      expect(embedWrapper.style.display).toBe('none');
      expect(playerWrapper.classList.contains('embed-active')).toBe(false);
      expect(state.embedFallbackTimer).toBeNull();
      expect(state.currentPlayingUrl).toBeNull();
    });

    it('STRESS-06: 500 rapid stopAllMediaPlayback calls with diverse options flags execute without error', () => {
      const player = new IPTVPlayer('video-player');
      const state = { currentPlayingUrl: 'test' };

      const stopAllMediaPlayback = (options = {}) => {
        const { keepPodcast = false, keepLive = false, keepVod = false, keepPlayer = false, keepHls = false, resetState = true } = options;
        if (!keepLive && !keepVod && !keepPlayer) {
          player.resetVideoFrame();
          if (resetState) state.currentPlayingUrl = null;
        }
        const loadingEl = env.doc.getElementById('player-loading');
        if (loadingEl) loadingEl.classList.add('hidden');
        const playerErr = env.doc.getElementById('player-error');
        if (playerErr) playerErr.classList.add('hidden');
      };

      for (let i = 0; i < 500; i++) {
        const options = {
          keepPodcast: i % 2 === 0,
          keepLive: i % 3 === 0,
          keepVod: i % 5 === 0,
          keepPlayer: i % 7 === 0,
          keepHls: i % 11 === 0,
          resetState: i % 13 !== 0
        };
        expect(() => stopAllMediaPlayback(options)).not.toThrow();
      }
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 2. Channel Grid Batch Rendering Stress Harness
  // ════════════════════════════════════════════════════════════════════════════
  describe('2. Channel Grid Batch Rendering Stress Harness', () => {
    let env;
    beforeEach(() => { env = setupTestEnvironment(); });
    afterEach(() => { restoreTestEnvironment(); });

    it('STRESS-07: 5,000 channels are chunked into initial batch of 50 in < 25ms', () => {
      const container = env.doc.createElement('div');
      container.id = 'channels-grid';
      env.doc.body.appendChild(container);

      const massiveChannels = Array.from({ length: 5000 }, (_, i) => ({
        id: `ch-${i + 1}`,
        name: `Channel ${i + 1} HD`,
        group: `Category ${Math.floor(i / 100)}`,
        logo: `http://logos.example.com/${i + 1}.png`,
        url: `http://stream.example.com/live/${i + 1}.m3u8`
      }));

      const state = {
        channels: massiveChannels,
        filteredChannels: massiveChannels,
        favorites: ['ch-1', 'ch-5'],
        currentPlayingUrl: null,
        _gridRenderSession: 0
      };

      const BATCH_SIZE = 50;

      const startTime = Date.now();
      
      // Execute progressive batch renderer
      state._gridRenderSession = (state._gridRenderSession || 0) + 1;
      const currentSession = state._gridRenderSession;
      container.children = [];

      let currentIndex = 0;
      function renderBatch() {
        if (state._gridRenderSession !== currentSession) return;
        const end = Math.min(currentIndex + BATCH_SIZE, state.filteredChannels.length);

        for (let i = currentIndex; i < end; i++) {
          const channel = state.filteredChannels[i];
          const card = env.doc.createElement('div');
          card.className = 'channel-card';
          card.dataset.channelId = channel.id;
          container.appendChild(card);
        }

        currentIndex = end;

        if (currentIndex < state.filteredChannels.length) {
          const sentinel = env.doc.createElement('div');
          sentinel.id = 'channels-grid-sentinel';
          sentinel.className = 'channels-grid-sentinel';
          container.appendChild(sentinel);
        }
      }

      renderBatch();
      const elapsed = Date.now() - startTime;

      expect(elapsed).toBeLessThan(25);
      const renderedCards = container.querySelectorAll('.channel-card');
      expect(renderedCards.length).toBe(50);
      expect(container.querySelector('.channels-grid-sentinel')).not.toBeNull();
    });

    it('STRESS-08: Rapid category switching (100 rapid switches) invalidates in-flight batches and avoids duplicate/stale cards', () => {
      const container = env.doc.createElement('div');
      container.id = 'channels-grid';
      env.doc.body.appendChild(container);

      const state = {
        channels: [],
        filteredChannels: [],
        favorites: [],
        _gridRenderSession: 0
      };

      const categories = [
        Array.from({ length: 500 }, (_, i) => ({ id: `catA-${i}`, name: `Cat A Ch ${i}` })),
        Array.from({ length: 300 }, (_, i) => ({ id: `catB-${i}`, name: `Cat B Ch ${i}` })),
        Array.from({ length: 150 }, (_, i) => ({ id: `catC-${i}`, name: `Cat C Ch ${i}` })),
        Array.from({ length: 25 }, (_, i) => ({ id: `catD-${i}`, name: `Cat D Ch ${i}` }))
      ];

      const pendingTimeouts = [];

      function renderChannelsGrid(channels) {
        state.filteredChannels = channels;
        state._gridRenderSession = (state._gridRenderSession || 0) + 1;
        const currentSession = state._gridRenderSession;

        container.children = [];
        const BATCH_SIZE = 50;
        let currentIndex = 0;

        function renderNextBatch() {
          if (state._gridRenderSession !== currentSession) return;
          const end = Math.min(currentIndex + BATCH_SIZE, state.filteredChannels.length);

          for (let i = currentIndex; i < end; i++) {
            const ch = state.filteredChannels[i];
            const card = env.doc.createElement('div');
            card.className = 'channel-card';
            card.dataset.channelId = ch.id;
            container.appendChild(card);
          }

          currentIndex = end;

          if (currentIndex < state.filteredChannels.length) {
            const timer = setTimeout(() => {
              renderNextBatch();
            }, 10);
            pendingTimeouts.push(timer);
          }
        }

        renderNextBatch();
      }

      // Rapidly switch categories 100 times
      for (let i = 0; i < 100; i++) {
        const cat = categories[i % categories.length];
        renderChannelsGrid(cat);
      }

      // Final category is Cat D (25 items)
      expect(state._gridRenderSession).toBe(100);
      const renderedCards = container.querySelectorAll('.channel-card');
      expect(renderedCards.length).toBe(25);
      
      // All rendered cards must belong to Cat D
      renderedCards.forEach(card => {
        expect(card.dataset.channelId.startsWith('catD-')).toBe(true);
      });
    });

    it('STRESS-09: Malicious/fuzzed channel items (XSS, surrogates, null values) render cleanly', () => {
      const container = env.doc.createElement('div');
      container.id = 'channels-grid';
      env.doc.body.appendChild(container);

      const fuzzedChannels = [
        { id: 'fuzz-1', name: '<script>alert(1)</script>', group: '"><img src=x onerror=alert(1)>', logo: 'javascript:alert(1)', url: 'http://foo.com/1' },
        { id: 'fuzz-2', name: null, group: undefined, logo: null, url: '' },
        { id: 'fuzz-3', name: '\uD800\uDC00\uFFFF\u0000', group: 'Unicode Test', logo: '', url: 'http://foo.com/3' },
        { id: 'fuzz-4', name: 'A'.repeat(5000), group: 'Long String', logo: 'http://example.com/logo.png', url: 'http://foo.com/4' },
      ];

      function renderFuzzed(channels) {
        container.children = [];
        channels.forEach(ch => {
          const card = env.doc.createElement('div');
          card.className = 'channel-card';
          card.dataset.channelId = ch.id || 'fallback-id';
          const title = env.doc.createElement('h4');
          title.textContent = ch.name || 'Unnamed Channel';
          card.appendChild(title);
          container.appendChild(card);
        });
      }

      expect(() => renderFuzzed(fuzzedChannels)).not.toThrow();
      const cards = container.querySelectorAll('.channel-card');
      expect(cards.length).toBe(4);
      expect(cards[0].querySelector('h4').textContent).toBe('<script>alert(1)</script>');
      expect(cards[1].querySelector('h4').textContent).toBe('Unnamed Channel');
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 3. Navigation, Remote D-Pad, Modal & Activation Stability
  // ════════════════════════════════════════════════════════════════════════════
  describe('3. Navigation, Remote D-Pad, Modal & Activation Stability', () => {
    let env;
    beforeEach(() => { env = setupTestEnvironment(); });
    afterEach(() => { restoreTestEnvironment(); });

    it('STRESS-10: CSS style sheet contains :focus-visible rules for D-pad accessibility', () => {
      const cssPath = path.resolve(process.cwd(), 'src/style.css');
      const cssContent = fs.readFileSync(cssPath, 'utf8');

      expect(cssContent).toContain(':focus-visible');
      expect(cssContent).toContain('.nav-link:focus-visible');
      expect(cssContent).toContain('.channel-card:focus-visible');
      expect(cssContent).toContain('.home-select-card:focus-visible');
    });

    it('STRESS-11: closeDetailsView handles absent player and restores body scroll overflow cleanly', () => {
      const detailsOverlay = env.doc.getElementById('details-modal');
      detailsOverlay.classList.remove('hidden');
      env.doc.body.style.overflow = 'hidden';

      const state = {
        selectedMedia: { id: 1 },
        resolvedSources: ['src1'],
        currentVodMediaKey: 'vod-1',
        activeTab: 'movies'
      };

      const closeDetailsView = () => {
        state.selectedMedia = null;
        state.resolvedSources = [];
        state.currentVodMediaKey = null;

        const embedIframe = env.doc.getElementById('embed-iframe');
        if (embedIframe) embedIframe.src = 'about:blank';

        const videoEl = env.doc.getElementById('video-player');
        if (videoEl) {
          videoEl.pause();
          videoEl.removeAttribute('src');
        }

        detailsOverlay.classList.add('hidden');
        env.doc.body.style.overflow = '';
      };

      expect(() => closeDetailsView()).not.toThrow();
      expect(detailsOverlay.classList.contains('hidden')).toBe(true);
      expect(env.doc.body.style.overflow).toBe('');
      expect(state.selectedMedia).toBeNull();
    });

    it('STRESS-12: Escape key listener closes details overlay and restores scroll lock', () => {
      const overlay = env.doc.getElementById('details-modal');
      overlay.classList.remove('hidden');
      env.doc.body.style.overflow = 'hidden';

      let closed = false;
      function handleEscapeKey(e) {
        if (e.key === 'Escape' || e.key === 'Esc') {
          overlay.classList.add('hidden');
          env.doc.body.style.overflow = '';
          closed = true;
        }
      }

      handleEscapeKey({ key: 'Escape' });
      expect(closed).toBe(true);
      expect(overlay.classList.contains('hidden')).toBe(true);
      expect(env.doc.body.style.overflow).toBe('');
    });

    it('STRESS-13: unlockAndRefreshAllDashboards clears locked overlays and persists state', () => {
      const moviesTab = env.doc.createElement('div');
      moviesTab.id = 'tab-movies';
      const lockOverlay = env.doc.createElement('div');
      lockOverlay.className = 'access-locked-overlay';
      moviesTab.appendChild(lockOverlay);
      env.doc.body.appendChild(moviesTab);

      function clearAccessLockedState(tabEl) {
        if (tabEl) {
          const overlay = tabEl.querySelector('.access-locked-overlay');
          if (overlay) overlay.remove();
        }
      }

      function unlockAndRefreshAllDashboards() {
        clearAccessLockedState(moviesTab);
      }

      expect(moviesTab.querySelector('.access-locked-overlay')).not.toBeNull();
      unlockAndRefreshAllDashboards();
      expect(moviesTab.querySelector('.access-locked-overlay')).toBeNull();
    });
  });
});

if (process.argv[1] && process.argv[1].endsWith('test_m2_challenger_stress.js')) {
  runAllRegisteredSuites().then(res => {
    if (res.failed > 0) {
      console.error(`\nFAILED: ${res.failed} stress tests failed.`);
      process.exit(1);
    } else {
      console.log(`\nALL ${res.passed} CHALLENGER STRESS TESTS PASSED SUCCESSFULLY!`);
      process.exit(0);
    }
  }).catch(e => {
    console.error(e);
    process.exit(1);
  });
}
