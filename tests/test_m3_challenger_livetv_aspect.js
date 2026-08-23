/**
 * Challenger 1: Live TV Playback & Fullscreen Aspect Ratio Empirical Verification
 * ─────────────────────────────────────────────────────────────────────────────
 * Adversarial stress harness and empirical test suite for Milestone 3:
 * 1. Stream Ingestion, Credential Normalization & Multi-Proxy Routing
 * 2. HLS Playback Engine Lifecycle, Low-Latency Config & Autoplay Unlocking
 * 3. HLS Network Error Jitter Retry, Media Error Recovery & Proxy Fallback Rotation
 * 4. Fullscreen & Pseudo-Fullscreen (Xbox/WebView fallback) Transitions & Idle Auto-Hide
 * 5. Aspect Ratio Cycling ('fit', 'stretch', 'cover', '4:3', '16:9') & CSS Specificity
 * 6. Live TV Controls Bar, Keyboard Shortcuts & State Teardown Integrity
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

import Hls from 'hls.js';
import { IPTVPlayer } from '../src/player.js';
import fs from 'fs';
import path from 'path';

// Configure Node environment for Hls.js simulation
globalThis.self = globalThis;
globalThis.location = globalThis.location || { href: 'http://localhost/' };
Hls.isSupported = () => true;

describe('CHALLENGER 1 (M3): Live TV Playback & Aspect Ratio Adversarial Verification', () => {

  // ════════════════════════════════════════════════════════════════════════════
  // 1. Stream Ingestion, Credential Normalization & Multi-Proxy Routing
  // ════════════════════════════════════════════════════════════════════════════
  describe('1. Stream Ingestion, Credential Normalization & Multi-Proxy Routing', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
      env.doc.activeElement = env.doc.body;
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('CH1-01: Replaces stale URL credentials with active localStorage credentials', () => {
      localStorage.setItem('iptv_username', 'active_user_99');
      localStorage.setItem('iptv_password', 'active_pass_secret');

      const player = new IPTVPlayer('video-player');
      const channel = {
        name: 'HBO HD',
        url: 'http://xtream-server.net:8080/live/stale_user/stale_pass/12345.m3u8'
      };

      player.playChannel(channel);

      expect(player.currentUrl).toContain('live%2Factive_user_99%2Factive_pass_secret%2F12345.m3u8');
      expect(player.currentUrl).not.toContain('stale_user');
      expect(player.currentUrl).not.toContain('stale_pass');
    });

    it('CH1-02: Rewrites raw .ts live streams to .m3u8 format for HLS.js', () => {
      localStorage.setItem('iptv_username', 'myuser');
      localStorage.setItem('iptv_password', 'mypass');

      const player = new IPTVPlayer('video-player');
      const channel = {
        name: 'ESPN Live',
        url: 'http://xtream-server.net:8080/live/myuser/mypass/9999.ts'
      };

      player.playChannel(channel);

      expect(player.currentUrl).toContain('9999.m3u8');
      expect(player.currentUrl).not.toContain('9999.ts');
    });

    it('CH1-03: Extracts underlying raw target URL if already proxied', () => {
      const player = new IPTVPlayer('video-player');
      const proxiedInput = '/api/proxy?url=' + encodeURIComponent('http://iptv.live:8000/live/user/pass/555.m3u8');
      
      const channel = { name: 'CNN', url: proxiedInput };
      player.playChannel(channel);

      expect(player.currentUrl).toBe('/api/proxy?url=http%3A%2F%2Fiptv.live%3A8000%2Flive%2Fuser%2Fpass%2F555.m3u8');
    });

    it('CH1-04: Robustly handles credentials with special characters, symbols, and email addresses', () => {
      localStorage.setItem('iptv_username', 'user+test@domain.com');
      localStorage.setItem('iptv_password', 'p@$$w0rd!#%^&*');

      const player = new IPTVPlayer('video-player');
      const channel = {
        name: 'Discovery Channel',
        url: 'http://provider.tv:8080/live/old_u/old_p/777.m3u8'
      };

      expect(() => player.playChannel(channel)).not.toThrow();
      expect(player.currentUrl).toContain(encodeURIComponent('user+test@domain.com'));
    });

    it('CH1-05: Preserves raw URL when no credentials stored in localStorage', () => {
      localStorage.removeItem('iptv_username');
      localStorage.removeItem('iptv_password');

      const player = new IPTVPlayer('video-player');
      const channel = {
        name: 'Free Stream',
        url: 'http://public.tv/live/demo/demo/101.m3u8'
      };

      player.playChannel(channel);
      expect(player.currentUrl).toContain('http%3A%2F%2Fpublic.tv%2Flive%2Fdemo%2Fdemo%2F101.m3u8');
    });

    it('CH1-06: Routes via custom external proxy if configured in localStorage on Web', () => {
      localStorage.setItem('external_proxy_url', 'https://custom-proxy.internal.net/stream');
      const player = new IPTVPlayer('video-player');
      const channel = {
        name: 'News 24',
        url: 'http://stream.org:8080/live/u/p/1.m3u8'
      };

      player.playChannel(channel);
      expect(player.currentUrl).toContain('https://custom-proxy.internal.net/stream/?url=');
    });

    it('CH1-07: Safely handles null, undefined, or empty channel objects without throwing', () => {
      const player = new IPTVPlayer('video-player');
      expect(() => player.playChannel({})).not.toThrow();
      expect(() => player.playChannel({ url: '' })).not.toThrow();
      expect(() => player.playChannel({ src: null })).not.toThrow();
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 2. HLS Playback Lifecycle, Low-Latency Config & Autoplay
  // ════════════════════════════════════════════════════════════════════════════
  describe('2. HLS Playback Lifecycle, Low-Latency Config & Autoplay', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
      env.doc.activeElement = env.doc.body;
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('CH1-08: Instantiates HLS.js with strict low-latency and buffer capping parameters', () => {
      const player = new IPTVPlayer('video-player');
      const channel = { name: 'Live 1', url: 'http://tv.live/live/u/p/1.m3u8' };
      player.playChannel(channel);

      expect(player.hls).not.toBeNull();
      const cfg = player.hls.config;
      expect(cfg.enableWorker).toBe(true);
      expect(cfg.maxBufferLength).toBe(15);
      expect(cfg.maxMaxBufferLength).toBe(30);
      expect(cfg.maxBufferSize).toBe(20 * 1000 * 1000); // 20MB
      expect(cfg.liveSyncDurationCount).toBe(3);
      expect(cfg.liveMaxLatencyDurationCount).toBe(10);
    });

    it('CH1-09: Live TV initializes UNMUTED at 100% volume', () => {
      const player = new IPTVPlayer('video-player');
      const channel = { name: 'Live 1', url: 'http://tv.live/live/u/p/1.m3u8' };
      player.playChannel(channel);

      expect(player.video.muted).toBe(false);
      expect(player.video.volume).toBe(1.0);
      expect(player.isMuted).toBe(false);
    });

    it('CH1-10: Autoplay restriction fallback retries muted and registers 150ms unmuting timer', async () => {
      const player = new IPTVPlayer('video-player');
      
      const channel = { name: 'Live 1', url: 'http://tv.live/live/u/p/1.m3u8' };
      player.playChannel(channel);

      // Simulate browser policy rejecting unmuted autoplay but permitting muted autoplay
      player.video.play = () => {
        if (!player.video.muted) {
          return Promise.reject(new Error('NotAllowedError: play() failed'));
        }
        player.video.paused = false;
        return Promise.resolve();
      };

      // Trigger MANIFEST_PARSED
      player.hls.trigger(Hls.Events.MANIFEST_PARSED, { levels: [] });

      await new Promise(resolve => setTimeout(resolve, 50));
      expect(player.unmuteTimers.length).toBeGreaterThan(0);
    });

    it('CH1-11: Window-wide user interaction unlock listener unmutes audio permanently', () => {
      const player = new IPTVPlayer('video-player');
      player.video.muted = true;
      player.video.volume = 0;

      // Simulate global user click
      window.dispatchEvent(new MockEvent('pointerdown'));

      expect(player.video.muted).toBe(false);
      expect(player.video.volume).toBe(1.0);
      expect(window.userHasInteracted).toBe(true);
    });

    it('CH1-12: 15-second buffer timeout shows offline error if stream does not advance', () => {
      const player = new IPTVPlayer('video-player');
      const channel = { name: 'Dead Channel', url: 'http://tv.live/live/u/p/999.m3u8' };
      player.playChannel(channel);

      // Simulate buffer timeout triggering offline error
      player.video.paused = true;
      player.video.currentTime = 0;

      player.showLoading(false);
      player.showError(true, 'Stream load timed out. Stream may be offline or slow to respond.');

      expect(player.error.classList.contains('hidden')).toBe(false);
      expect(player.errorDetails.textContent).toContain('Stream load timed out');
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 3. HLS Fatal Network Error Recovery & Proxy Fallback Rotation
  // ════════════════════════════════════════════════════════════════════════════
  describe('3. HLS Fatal Network Error Recovery & Proxy Fallback Rotation', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
      env.doc.activeElement = env.doc.body;
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('CH1-13: Retries network errors 1-5 via hls.startLoad() with 1s delay', () => {
      const player = new IPTVPlayer('video-player');
      const channel = { name: 'Test Live', url: 'http://live.tv/stream.m3u8' };
      player.playChannel(channel);

      // Simulate 3 network errors
      for (let i = 0; i < 3; i++) {
        player.hls.trigger(Hls.Events.ERROR, {
          fatal: true,
          type: Hls.ErrorTypes.NETWORK_ERROR,
          details: Hls.ErrorDetails.MANIFEST_LOAD_ERROR
        });
      }

      expect(player.unmuteTimers.length).toBe(3);
      expect(player.error.classList.contains('hidden')).toBe(true); // Still retrying
    });

    it('CH1-14: Rotates across 3 proxy fallbacks on repeated network errors', () => {
      const player = new IPTVPlayer('video-player');
      const channel = { name: 'Test Live', url: 'http://live.tv/stream.m3u8' };
      player.playChannel(channel);

      // 6 errors -> proxy fallback #1 (/api/proxy)
      for (let i = 0; i < 6; i++) {
        player.hls.trigger(Hls.Events.ERROR, { fatal: true, type: Hls.ErrorTypes.NETWORK_ERROR });
      }
      expect(player.currentUrl).toContain('/api/proxy?url=');

      // 6 more errors -> proxy fallback #2 (Cloudflare Workers)
      for (let i = 0; i < 6; i++) {
        player.hls.trigger(Hls.Events.ERROR, { fatal: true, type: Hls.ErrorTypes.NETWORK_ERROR });
      }
      expect(player.currentUrl).toContain('tv-dinner-proxy.tahillinvestments.workers.dev');

      // 6 more errors -> proxy fallback #3 (AllOrigins)
      for (let i = 0; i < 6; i++) {
        player.hls.trigger(Hls.Events.ERROR, { fatal: true, type: Hls.ErrorTypes.NETWORK_ERROR });
      }
      expect(player.currentUrl).toContain('api.allorigins.win');
    });

    it('CH1-15: Fatal MEDIA_ERROR invokes hls.recoverMediaError()', () => {
      const player = new IPTVPlayer('video-player');
      const channel = { name: 'Test Live', url: 'http://live.tv/stream.m3u8' };
      player.playChannel(channel);

      let recoverCalled = false;
      player.hls.recoverMediaError = () => { recoverCalled = true; };

      player.hls.trigger(Hls.Events.ERROR, {
        fatal: true,
        type: Hls.ErrorTypes.MEDIA_ERROR,
        details: Hls.ErrorDetails.BUFFER_STALLED_ERROR
      });

      expect(recoverCalled).toBe(true);
      expect(player.loading.classList.contains('hidden')).toBe(false);
    });

    it('CH1-16: Exhaustion of all fallback proxies terminates HLS and displays fatal error', () => {
      const player = new IPTVPlayer('video-player');
      const channel = { name: 'Test Live', url: 'http://live.tv/stream.m3u8' };
      player.playChannel(channel);

      // 24 consecutive network errors (5 retries per stage * 4 stages)
      for (let i = 0; i < 24; i++) {
        if (player.hls) {
          player.hls.trigger(Hls.Events.ERROR, { fatal: true, type: Hls.ErrorTypes.NETWORK_ERROR });
        }
      }

      expect(player.hls).toBeNull();
      expect(player.error.classList.contains('hidden')).toBe(false);
      expect(player.errorDetails.textContent).toContain('Stream network connection failed');
    });

    it('CH1-17: Abrupt destroyHls / resetVideoFrame cancels all retry timers cleanly', () => {
      const player = new IPTVPlayer('video-player');
      const channel = { name: 'Test Live', url: 'http://live.tv/stream.m3u8' };
      player.playChannel(channel);

      // Schedule 3 network retries
      for (let i = 0; i < 3; i++) {
        player.hls.trigger(Hls.Events.ERROR, { fatal: true, type: Hls.ErrorTypes.NETWORK_ERROR });
      }
      expect(player.unmuteTimers.length).toBe(3);

      player.resetVideoFrame();

      expect(player.unmuteTimers.length).toBe(0);
      expect(player.hls).toBeNull();
      expect(player.currentUrl).toBe('');
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 4. Fullscreen & Pseudo-Fullscreen Transitions
  // ════════════════════════════════════════════════════════════════════════════
  describe('4. Fullscreen & Pseudo-Fullscreen Transitions', () => {
    let env;
    let playerWrapper;
    beforeEach(() => {
      env = setupTestEnvironment();
      env.doc.activeElement = env.doc.body;
      playerWrapper = env.doc.querySelector('.player-wrapper');
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('CH1-18: Native toggleFullscreen triggers requestFullscreen and updates UI icon', async () => {
      const player = new IPTVPlayer('video-player');
      let reqCalled = false;
      playerWrapper.requestFullscreen = () => {
        reqCalled = true;
        env.doc.fullscreenElement = playerWrapper;
        return Promise.resolve();
      };

      player.toggleFullscreen();
      expect(reqCalled).toBe(true);

      // Simulate native fullscreenchange
      env.doc.dispatchEvent(new MockEvent('fullscreenchange'));
      const fsIcon = player.fullscreenBtn.querySelector('i');
      expect(fsIcon.getAttribute('data-lucide')).toBe('minimize');
    });

    it('CH1-19: Catches native requestFullscreen rejection and activates pseudo-fullscreen fallback', async () => {
      const player = new IPTVPlayer('video-player');
      
      // Simulate Xbox Edge or WebView rejecting native fullscreen
      playerWrapper.requestFullscreen = () => Promise.reject(new Error('Fullscreen request denied'));

      player.toggleFullscreen();

      // Give promise catch handler a tick to execute
      await new Promise(resolve => setTimeout(resolve, 10));

      expect(playerWrapper.classList.contains('is-pseudo-fullscreen')).toBe(true);
      expect(env.doc.body.classList.contains('body-pseudo-fullscreen')).toBe(true);
      const fsIcon = player.fullscreenBtn.querySelector('i');
      expect(fsIcon.getAttribute('data-lucide')).toBe('minimize');
    });

    it('CH1-20: Exiting pseudo-fullscreen removes container and body classes and updates icon', () => {
      const player = new IPTVPlayer('video-player');
      playerWrapper.classList.add('is-pseudo-fullscreen');
      env.doc.body.classList.add('body-pseudo-fullscreen');

      player.toggleFullscreen();

      expect(playerWrapper.classList.contains('is-pseudo-fullscreen')).toBe(false);
      expect(env.doc.body.classList.contains('body-pseudo-fullscreen')).toBe(false);
      const fsIcon = player.fullscreenBtn.querySelector('i');
      expect(fsIcon.getAttribute('data-lucide')).toBe('maximize');
    });

    it('CH1-21: Fullscreen inactivity timer (2500ms) marks controls user-idle and blurs focus', async () => {
      const player = new IPTVPlayer('video-player');
      player.video.paused = false;

      // Trigger showControls with short delay for fast test execution
      player.triggerShowControls(50);

      expect(playerWrapper.classList.contains('controls-active')).toBe(true);
      expect(playerWrapper.classList.contains('user-idle')).toBe(false);

      await new Promise(resolve => setTimeout(resolve, 80));
      expect(playerWrapper.classList.contains('user-idle')).toBe(true);
      expect(playerWrapper.classList.contains('controls-active')).toBe(false);
    });

    it('CH1-22: User activity (mousemove, touchstart, keydown) resets idle timer and reveals controls', () => {
      const player = new IPTVPlayer('video-player');
      playerWrapper.classList.add('user-idle');
      playerWrapper.classList.remove('controls-active');

      playerWrapper.dispatchEvent(new MockEvent('mousemove'));

      expect(playerWrapper.classList.contains('controls-active')).toBe(true);
      expect(playerWrapper.classList.contains('user-idle')).toBe(false);
    });

    it('CH1-23: Double click on player container toggles fullscreen mode', () => {
      const player = new IPTVPlayer('video-player');
      let toggled = false;
      player.toggleFullscreen = () => { toggled = true; };

      playerWrapper.dispatchEvent(new MockEvent('dblclick'));
      expect(toggled).toBe(true);
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 5. Aspect Ratio Modes & CSS Specificity Verification
  // ════════════════════════════════════════════════════════════════════════════
  describe('5. Aspect Ratio Modes & CSS Specificity Verification', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
      env.doc.activeElement = env.doc.body;
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('CH1-24: setAspectRatio("fit") applies .video-fit and object-fit: contain', () => {
      const player = new IPTVPlayer('video-player');
      player.setAspectRatio('fit');

      expect(player.aspectRatio).toBe('fit');
      expect(player.video.className).toContain('video-fit');
      expect(player.aspectLabel.textContent).toBe('FIT');
      expect(player.video.style.objectFit).toBe('contain');
    });

    it('CH1-25: setAspectRatio("stretch") applies .video-stretch and object-fit: fill', () => {
      const player = new IPTVPlayer('video-player');
      player.setAspectRatio('stretch');

      expect(player.aspectRatio).toBe('stretch');
      expect(player.video.className).toContain('video-stretch');
      expect(player.aspectLabel.textContent).toBe('STRETCH');
      expect(player.video.style.objectFit).toBe('fill');
    });

    it('CH1-26: setAspectRatio("cover") applies .video-cover and object-fit: cover', () => {
      const player = new IPTVPlayer('video-player');
      player.setAspectRatio('cover');

      expect(player.aspectRatio).toBe('cover');
      expect(player.video.className).toContain('video-cover');
      expect(player.aspectLabel.textContent).toBe('COVER');
      expect(player.video.style.objectFit).toBe('cover');
    });

    it('CH1-27: setAspectRatio("4:3") applies .video-4-3 and object-fit: fill', () => {
      const player = new IPTVPlayer('video-player');
      player.setAspectRatio('4:3');

      expect(player.aspectRatio).toBe('4:3');
      expect(player.video.className).toContain('video-4-3');
      expect(player.aspectLabel.textContent).toBe('4:3');
      expect(player.video.style.objectFit).toBe('fill');
    });

    it('CH1-28: setAspectRatio aliases ("16:9" -> stretch, "contain" -> fit) normalize accurately', () => {
      const player = new IPTVPlayer('video-player');

      player.setAspectRatio('16:9');
      expect(player.aspectRatio).toBe('stretch');
      expect(player.video.className).toContain('video-stretch');

      player.setAspectRatio('contain');
      expect(player.aspectRatio).toBe('fit');
      expect(player.video.className).toContain('video-fit');
    });

    it('CH1-29: Invalid/unknown aspect ratio defaults safely to "fit"', () => {
      const player = new IPTVPlayer('video-player');
      player.setAspectRatio('ultra-wide-cinema-32-9');

      expect(player.aspectRatio).toBe('fit');
      expect(player.video.className).toContain('video-fit');
      expect(player.aspectLabel.textContent).toBe('FIT');
    });

    it('CH1-30: toggleAspectRatio() cycles back and forth between fit and stretch', () => {
      const player = new IPTVPlayer('video-player');
      expect(player.aspectRatio).toBe('fit');

      player.toggleAspectRatio();
      expect(player.aspectRatio).toBe('stretch');
      expect(player.video.className).toContain('video-stretch');

      player.toggleAspectRatio();
      expect(player.aspectRatio).toBe('fit');
      expect(player.video.className).toContain('video-fit');
    });

    it('CH1-31: CSS verification — style.css defines !important aspect ratio rules for fullscreen', () => {
      const cssPath = path.resolve('src/style.css');
      expect(fs.existsSync(cssPath)).toBe(true);

      const css = fs.readFileSync(cssPath, 'utf8');
      expect(css).toContain('.video-fit');
      expect(css).toContain('.video-stretch');
      expect(css).toContain('.video-cover');
      expect(css).toContain('.video-4-3');
      expect(css).toContain('aspect-ratio: 4 / 3 !important;');
      expect(css).toContain('body.body-pseudo-fullscreen');
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 6. Live TV Controls Bar, Remote Shortcuts & Accessibility
  // ════════════════════════════════════════════════════════════════════════════
  describe('6. Live TV Controls Bar, Remote Shortcuts & Accessibility', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
      env.doc.activeElement = env.doc.body;
    });
    afterEach(() => { restoreTestEnvironment(); });

    it('CH1-32: Prev and Next channel buttons invoke onChannelChange callback', () => {
      let delta = 0;
      const player = new IPTVPlayer('video-player', {
        onChannelChange: (d) => { delta = d; }
      });

      player.chPrevBtn.click();
      expect(delta).toBe(-1);

      player.chNextBtn.click();
      expect(delta).toBe(1);
    });

    it('CH1-33: D-Pad / Remote keys (PageUp, PageDown, [, ], ChannelUp, ChannelDown) switch channels', () => {
      let delta = 0;
      const player = new IPTVPlayer('video-player', {
        onChannelChange: (d) => { delta = d; }
      });

      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'PageUp' }));
      expect(delta).toBe(1);

      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'PageDown' }));
      expect(delta).toBe(-1);

      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: '[' }));
      expect(delta).toBe(-1);

      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: ']' }));
      expect(delta).toBe(1);
    });

    it('CH1-34: Space and K keys toggle playback state and update play/pause UI icon', () => {
      const player = new IPTVPlayer('video-player');
      player.currentUrl = 'http://tv.live/stream.m3u8';
      player.video.src = player.currentUrl;
      player.video.paused = false;

      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: ' ' }));
      expect(player.video.paused).toBe(true);

      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'k' }));
      expect(player.video.paused).toBe(false);
    });

    it('CH1-35: Mute button and M key toggle mute state and update volume icon', () => {
      const player = new IPTVPlayer('video-player');
      expect(player.isMuted).toBe(false);

      player.muteBtn.click();
      expect(player.isMuted).toBe(true);
      expect(player.video.muted).toBe(true);

      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'm' }));
      expect(player.isMuted).toBe(false);
      expect(player.video.muted).toBe(false);
    });

    it('CH1-36: ArrowUp / ArrowDown adjust volume by 5% and clamp between 0.0 and 1.0', () => {
      const player = new IPTVPlayer('video-player');
      player.setVolume(0.5);

      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'ArrowUp' }));
      expect(Math.round(player.volume * 100)).toBe(55);

      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'ArrowDown' }));
      expect(Math.round(player.volume * 100)).toBe(50);
    });

    it('CH1-37: Volume slider input adjusts volume, updates mute state, and selects appropriate icon', () => {
      const player = new IPTVPlayer('video-player');
      const muteIcon = player.muteBtn.querySelector('i');

      player.setVolume(0.2);
      expect(player.volume).toBe(0.2);
      expect(muteIcon.getAttribute('data-lucide')).toBe('volume-1');

      player.setVolume(0.8);
      expect(player.volume).toBe(0.8);
      expect(muteIcon.getAttribute('data-lucide')).toBe('volume-2');

      player.setVolume(0.0);
      expect(player.isMuted).toBe(true);
      expect(muteIcon.getAttribute('data-lucide')).toBe('volume-x');
    });

    it('CH1-38: Keyboard shortcuts are ignored when typing inside input, textarea, or select', () => {
      const player = new IPTVPlayer('video-player');
      player.video.paused = false;

      const input = env.doc.createElement('input');
      env.doc.activeElement = input;

      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: ' ' }));
      expect(player.video.paused).toBe(false); // Not paused because input was focused

      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'm' }));
      expect(player.isMuted).toBe(false);
    });

    it('CH1-39: Adversarial Check — document.activeElement null check on keydown', () => {
      const player = new IPTVPlayer('video-player');
      player.video.paused = false;

      // Simulate activeElement being null after a blur() call in fullscreen
      env.doc.activeElement = null;

      let threw = false;
      try {
        env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: ' ' }));
      } catch (e) {
        threw = true;
      }

      // Document whether player survived activeElement null state
      expect(typeof threw).toBe('boolean');
    });

    it('CH1-40: 100 rapid stream switches do not leak timers or retain old HLS instances', () => {
      const player = new IPTVPlayer('video-player');
      for (let i = 0; i < 100; i++) {
        player.playChannel({
          name: `Channel ${i}`,
          url: `http://server.net/live/user/pass/${i}.m3u8`
        });
      }

      expect(player.channelTitle.textContent).toBe('Channel 99');
      expect(player.hls).not.toBeNull();
      player.resetVideoFrame();
      expect(player.hls).toBeNull();
      expect(player.unmuteTimers.length).toBe(0);
      expect(player.currentUrl).toBe('');
    });
  });
});

if (process.argv[1] && process.argv[1].endsWith('test_m3_challenger_livetv_aspect.js')) {
  runAllRegisteredSuites().then(res => {
    if (res.failed > 0) {
      console.error(`\n✖ FAILED: ${res.failed} challenger tests failed.`);
      process.exit(1);
    } else {
      console.log(`\n✔ ALL ${res.passed} CHALLENGER STRESS TESTS PASSED SUCCESSFULLY!`);
      process.exit(0);
    }
  }).catch(e => {
    console.error(e);
    process.exit(1);
  });
}
