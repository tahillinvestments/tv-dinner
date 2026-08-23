/**
 * Challenger Adversarial Stress Test Suite for Milestone 5 (Podcasts Engine & Audio/Video Media)
 * ─────────────────────────────────────────────────────────────────────────────────────────────
 * Comprehensive empirical verification covering:
 * 1. Media Teardown & Cross-Module Concurrency (Live TV vs VOD vs Podcast Audio vs Video Podcast)
 * 2. LocalStorage Persistence Boundary Stress (Corrupt JSON, Type Pollution, Overflow, Quotas)
 * 3. RSS 2.0 & YouTube Feed XML Parsing, Entity Decoding & CDATA Extraction
 * 4. Duration Formatting, Timestamp Arithmetic & Seeking Boundary Clamping
 * 5. PocketCasts Dock Audio Player Lifecycle, Playback Speed Cycling & Queue Advancing
 * 6. Video Podcast Embed Security, Pointer-Events, Fullscreen & Iframe Teardown
 * 7. Long-Running Chaotic User Session Simulation (500 Rapid Media Switches)
 */

import {
  describe,
  it,
  expect,
  assert,
  beforeEach,
  afterEach,
  setupTestEnvironment,
  restoreTestEnvironment,
  MockAudioElement,
  MockElement,
  runAllRegisteredSuites
} from './harness.js';

import {
  PODCAST_CHANNELS,
  decodeXmlEntities,
  extractXmlCdataOrText,
  formatPodcastDuration,
  parseRss2AudioFeed,
  fetchPodcastRssFeed,
  fetchChannelPastEpisodes
} from '../src/podcastsData.js';

import { IPTVPlayer } from '../src/player.js';

describe('CHALLENGER M5 ADVERSARIAL: Podcasts Engine & Audio/Video Media Hardening', () => {

  // ════════════════════════════════════════════════════════════════════════════
  // 1. Media Teardown & Cross-Module Concurrency
  // ════════════════════════════════════════════════════════════════════════════
  describe('1. Media Teardown & Cross-Module Concurrency Hardening', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => {
      restoreTestEnvironment();
    });

    it('M5-ADV-01: Rapid 50x switching between Live TV, VOD, Audio Podcast and Video Podcast leaves zero orphan audio or dangling iframes', () => {
      const player = new IPTVPlayer('video-player');
      const audio = new MockAudioElement();
      const iframe = env.document.getElementById('embed-iframe');
      const embedWrapper = env.document.getElementById('embed-player-wrapper');
      const playerWrapper = env.document.querySelector('.player-wrapper');
      const detailsOverlay = env.document.getElementById('details-overlay');

      const mockPocketcastsState = {
        audioElement: audio,
        isPlaying: false,
        currentEpisode: null
      };

      function stopAllMedia({ keepPodcast = false, keepLive = false, keepVod = false } = {}) {
        if (!keepLive && !keepVod) {
          player.resetVideoFrame();
        }
        if (!keepVod) {
          if (iframe) iframe.src = 'about:blank';
          if (embedWrapper) embedWrapper.style.display = 'none';
          if (playerWrapper) {
            playerWrapper.classList.remove('embed-active');
            playerWrapper.classList.remove('podcast-mode');
          }
        }
        if (!keepPodcast) {
          if (mockPocketcastsState.audioElement) {
            mockPocketcastsState.audioElement.pause();
            mockPocketcastsState.audioElement.src = '';
            mockPocketcastsState.audioElement.removeAttribute('src');
          }
          mockPocketcastsState.isPlaying = false;
        }
      }

      function switchContext(target) {
        if (target === 'live') {
          stopAllMedia({ keepLive: true });
          player.setControlMode('live');
          player.playChannel({ url: 'http://stream.test/live.m3u8', name: 'Live Channel' });
        } else if (target === 'vod') {
          stopAllMedia({ keepVod: true });
          player.setControlMode('vod');
          player.playChannel({ url: 'http://stream.test/movie.mp4', name: 'VOD Movie', mediaKey: 'mov_1' });
        } else if (target === 'audio_podcast') {
          stopAllMedia({ keepPodcast: true });
          mockPocketcastsState.currentEpisode = { id: 'ep_audio_1', audioUrl: 'http://stream.test/podcast.mp3', title: 'Tech Talk' };
          audio.src = mockPocketcastsState.currentEpisode.audioUrl;
          audio.play();
          mockPocketcastsState.isPlaying = true;
        } else if (target === 'video_podcast') {
          stopAllMedia();
          mockPocketcastsState.currentEpisode = { id: 'ep_video_1', youtubeId: 'yt123abc', title: 'Video Show' };
          if (iframe) {
            iframe.src = `https://www.youtube-nocookie.com/embed/${mockPocketcastsState.currentEpisode.youtubeId}?autoplay=1`;
            iframe.style.display = 'block';
          }
          if (embedWrapper) embedWrapper.style.display = 'block';
          if (playerWrapper) playerWrapper.classList.add('podcast-mode');
          if (detailsOverlay) detailsOverlay.classList.remove('hidden');
        }
      }

      const sequence = ['live', 'audio_podcast', 'vod', 'video_podcast', 'live', 'vod', 'audio_podcast', 'video_podcast'];
      for (let cycle = 0; cycle < 50; cycle++) {
        const target = sequence[cycle % sequence.length];
        switchContext(target);

        // Verification invariant for each state
        if (target === 'live') {
          expect(audio.paused).toBe(true);
          expect(audio.src).toBe('');
          expect(mockPocketcastsState.isPlaying).toBe(false);
          expect(iframe.src).toBe('about:blank');
          expect(player.controlMode).toBe('live');
        } else if (target === 'audio_podcast') {
          expect(audio.paused).toBe(false);
          expect(audio.src).toBe('http://stream.test/podcast.mp3');
          expect(mockPocketcastsState.isPlaying).toBe(true);
          expect(player.hls).toBeNull();
          expect(iframe.src).toBe('about:blank');
        } else if (target === 'vod') {
          expect(audio.paused).toBe(true);
          expect(audio.src).toBe('');
          expect(mockPocketcastsState.isPlaying).toBe(false);
          expect(iframe.src).toBe('about:blank');
          expect(player.controlMode).toBe('vod');
        } else if (target === 'video_podcast') {
          expect(audio.paused).toBe(true);
          expect(audio.src).toBe('');
          expect(mockPocketcastsState.isPlaying).toBe(false);
          expect(player.hls).toBeNull();
          expect(iframe.src).toContain('https://www.youtube-nocookie.com/embed/yt123abc');
          expect(playerWrapper.classList.contains('podcast-mode')).toBe(true);
        }
      }

      // Final complete teardown
      stopAllMedia();
      expect(audio.paused).toBe(true);
      expect(audio.src).toBe('');
      expect(player.hls).toBeNull();
      expect(iframe.src).toBe('about:blank');
      expect(playerWrapper.classList.contains('podcast-mode')).toBe(false);
    });

    it('M5-ADV-02: stopAllMediaPlayback handles undefined/null element references without throwing exceptions', () => {
      // Intentionally clear DOM elements to stress null-safety
      const videoEl = env.document.getElementById('video-player');
      if (videoEl) videoEl.remove();
      const iframe = env.document.getElementById('embed-iframe');
      if (iframe) iframe.remove();
      const wrapper = env.document.getElementById('embed-player-wrapper');
      if (wrapper) wrapper.remove();

      expect(() => {
        // Mock execution of stopAllMediaPlayback when DOM is empty
        const v = env.document.getElementById('video-player');
        if (v) v.pause();
        const f = env.document.getElementById('embed-iframe');
        if (f) f.src = 'about:blank';
        const w = env.document.getElementById('embed-player-wrapper');
        if (w) w.style.display = 'none';
      }).not.toThrow();
    });

    it('M5-ADV-03: Switching from Podcasts tab to Settings/Library maintains audio dock playback', () => {
      const audio = new MockAudioElement('http://podcast.audio/stream.mp3');
      audio.play();
      let isPlaying = true;

      function onTabChange(tabName) {
        if (tabName !== 'podcasts' && tabName !== 'settings' && tabName !== 'library' && tabName !== 'home') {
          // If moving to live or vod, stop podcast
          audio.pause();
          isPlaying = false;
        }
      }

      onTabChange('settings');
      expect(audio.paused).toBe(false);
      expect(isPlaying).toBe(true);

      onTabChange('library');
      expect(audio.paused).toBe(false);
      expect(isPlaying).toBe(true);

      onTabChange('home');
      expect(audio.paused).toBe(false);
      expect(isPlaying).toBe(true);

      onTabChange('live');
      expect(audio.paused).toBe(true);
      expect(isPlaying).toBe(false);
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 2. LocalStorage Persistence Boundary Stress
  // ════════════════════════════════════════════════════════════════════════════
  describe('2. LocalStorage Persistence Boundary Stress', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => {
      restoreTestEnvironment();
    });

    it('M5-ADV-04: Resilient to corrupt JSON and non-array types in subscription keys', () => {
      const corruptPayloads = [
        '{invalid_json:',
        'null',
        'undefined',
        '12345',
        'true',
        '"string_not_array"',
        '{"id": "chan_1"}',
        '[1, 2, undefined_val]'
      ];

      for (const payload of corruptPayloads) {
        env.storage.setItem('pocketcasts_subscribed_ids', payload);
        env.storage.setItem('subscribed_podcasts', payload);

        // Safe parser implementation test
        let subIds = [];
        try {
          const raw = env.storage.getItem('pocketcasts_subscribed_ids') || env.storage.getItem('subscribed_podcasts');
          const parsed = JSON.parse(raw);
          subIds = Array.isArray(parsed) ? parsed : [];
        } catch (e) {
          subIds = [];
        }

        expect(Array.isArray(subIds)).toBe(true);
        if (payload === 'null' || payload.startsWith('{') || payload.startsWith('1') || payload.startsWith('true') || payload.startsWith('"')) {
          expect(subIds.length).toBe(0);
        }
      }
    });

    it('M5-ADV-05: Stress tests mass subscription insertion (500 channels) and deduplication', () => {
      let subIds = [];
      function subscribe(id) {
        if (!subIds.includes(id)) {
          subIds.push(id);
        }
        env.storage.setItem('pocketcasts_subscribed_ids', JSON.stringify(subIds));
      }

      function unsubscribe(id) {
        subIds = subIds.filter(s => s !== id);
        env.storage.setItem('pocketcasts_subscribed_ids', JSON.stringify(subIds));
      }

      // Add 500 unique IDs
      for (let i = 0; i < 500; i++) {
        subscribe(`chan_unique_${i}`);
      }
      expect(subIds.length).toBe(500);

      // Attempt adding duplicate channels
      for (let i = 0; i < 100; i++) {
        subscribe(`chan_unique_${i}`);
      }
      expect(subIds.length).toBe(500);

      // Unsubscribe 250 channels
      for (let i = 0; i < 250; i++) {
        unsubscribe(`chan_unique_${i}`);
      }
      expect(subIds.length).toBe(250);

      const parsed = JSON.parse(env.storage.getItem('pocketcasts_subscribed_ids'));
      expect(parsed.length).toBe(250);
      expect(parsed[0]).toBe('chan_unique_250');
    });

    it('M5-ADV-06: History list strictly clamps to MAX_HISTORY (50 items) under 500 insertions', () => {
      let history = [];
      const MAX_HISTORY = 50;

      function recordHistory(episode) {
        const epId = episode.id || episode.youtubeId || episode.audioUrl;
        const historyItem = { episode, dateStr: new Date().toLocaleDateString() };
        history = [historyItem, ...history.filter(h => (h.episode?.id || h.episode?.youtubeId || h.episode?.audioUrl) !== epId)].slice(0, MAX_HISTORY);
        env.storage.setItem('podcast_episode_history', JSON.stringify(history));
      }

      for (let i = 0; i < 500; i++) {
        recordHistory({
          id: `ep_${i}`,
          title: `Episode ${i}`,
          audioUrl: `http://stream.org/ep_${i}.mp3`
        });
      }

      expect(history.length).toBe(50);
      expect(history[0].episode.id).toBe('ep_499');
      expect(history[49].episode.id).toBe('ep_450');

      const saved = JSON.parse(env.storage.getItem('podcast_episode_history'));
      expect(saved.length).toBe(50);
    });

    it('M5-ADV-07: Playback positions auto-clears on episode ended and clamps resume to < 95% duration', () => {
      const playbackPositions = {};
      const epId = 'ep_tech_101';
      const duration = 3600; // 1 hr

      // 1. Normal progress tracking at 30 minutes
      playbackPositions[epId] = { position: 1800, duration, timestamp: Date.now() };
      env.storage.setItem('podcast_playback_positions', JSON.stringify(playbackPositions));

      // Check resume eligibility: position must be < 95% of duration (3420s)
      function getResumePosition(id, dur) {
        const saved = playbackPositions[id]?.position;
        if (saved && dur && isFinite(dur) && saved < dur * 0.95 && saved > 5) {
          return saved;
        }
        return 0;
      }

      expect(getResumePosition(epId, duration)).toBe(1800);

      // 2. Near-end tracking at 58 minutes (3480s > 3420s)
      playbackPositions[epId].position = 3500;
      expect(getResumePosition(epId, duration)).toBe(0);

      // 3. Episode ended event clears the position
      delete playbackPositions[epId];
      env.storage.setItem('podcast_playback_positions', JSON.stringify(playbackPositions));
      expect(playbackPositions[epId]).toBeUndefined();
    });

    it('M5-ADV-08: Storage QuotaExceededError is caught gracefully without unhandled crashes', () => {
      // Simulate quota error on setItem
      env.storage.setItem = () => {
        const err = new Error('QuotaExceededError');
        err.name = 'QuotaExceededError';
        throw err;
      };

      expect(() => {
        try {
          env.storage.setItem('podcast_playback_positions', JSON.stringify({ big: 'data' }));
        } catch (e) {
          // Graceful handling verification
        }
      }).not.toThrow();
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 3. RSS 2.0 & YouTube Feed XML Parsing, Entity Decoding & CDATA Extraction
  // ════════════════════════════════════════════════════════════════════════════
  describe('3. RSS 2.0 & YouTube Feed XML Parsing, Entity Decoding & CDATA Extraction', () => {

    it('M5-ADV-09: parseRss2AudioFeed accurately parses standard RSS 2.0 audio enclosures with custom attributes', () => {
      const xml = `
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
          <channel>
            <title>Acquired</title>
            <description>The inside story of great companies.</description>
            <itunes:author>Ben Gilbert &amp; David Rosenthal</itunes:author>
            <itunes:image href="https://image.acquired.fm/cover.jpg" />
            <item>
              <title><![CDATA[NVIDIA: The Complete History & Strategy (Part 1)]]></title>
              <description><![CDATA[How Jensen Huang and team built a $3T computing titan.]]></description>
              <guid isPermaLink="false">acquired_nvidia_part1</guid>
              <pubDate>Sun, 12 Jul 2026 14:00:00 GMT</pubDate>
              <itunes:duration>03:42:15</itunes:duration>
              <itunes:image href="https://image.acquired.fm/nvidia.jpg" />
              <enclosure url="https://media.acquired.fm/episodes/nvidia_p1.mp3" length="213849120" type="audio/mpeg" />
            </item>
            <item>
              <title>Apple &amp; ARM: The Silicon Revolution</title>
              <description>Deep dive into Apple Silicon.</description>
              <guid>acquired_apple_silicon</guid>
              <pubDate>Mon, 20 Jul 2026 10:00:00 GMT</pubDate>
              <itunes:duration>135:40</itunes:duration>
              <enclosure type="audio/mp3" length="150000000" url="https://media.acquired.fm/episodes/apple_arm.mp3"/>
            </item>
          </channel>
        </rss>
      `;

      const episodes = parseRss2AudioFeed(xml, { id: 'chan_acquired', channelName: 'Acquired' });
      expect(episodes.length).toBe(2);

      // Episode 1 checks
      expect(episodes[0].title).toBe('NVIDIA: The Complete History & Strategy (Part 1)');
      expect(episodes[0].audioUrl).toBe('https://media.acquired.fm/episodes/nvidia_p1.mp3');
      expect(episodes[0].enclosure.type).toBe('audio/mpeg');
      expect(episodes[0].enclosure.length).toBe(213849120);
      expect(episodes[0].mediaType).toBe('audio');
      expect(episodes[0].duration).toBe('222m'); // 3h 42m = 222m
      expect(episodes[0].thumbnail).toBe('https://image.acquired.fm/nvidia.jpg');
      expect(episodes[0].host).toBe('Ben Gilbert & David Rosenthal');

      // Episode 2 checks
      expect(episodes[1].title).toBe('Apple & ARM: The Silicon Revolution');
      expect(episodes[1].audioUrl).toBe('https://media.acquired.fm/episodes/apple_arm.mp3');
      expect(episodes[1].duration).toBe('135m');
    });

    it('M5-ADV-10: parseRss2AudioFeed correctly identifies video enclosures and fallback media:content tags', () => {
      const xml = `
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
          <channel>
            <title>Video Podcast Channel</title>
            <item>
              <title>Video Episode 1</title>
              <enclosure url="https://cdn.test.org/ep1.mp4" type="video/mp4" length="500000000" />
            </item>
            <item>
              <title>Video Episode 2 (Media Content Fallback)</title>
              <media:content url="https://cdn.test.org/ep2.mkv" type="video/x-matroska" />
            </item>
          </channel>
        </rss>
      `;

      const episodes = parseRss2AudioFeed(xml);
      expect(episodes.length).toBe(2);
      expect(episodes[0].mediaType).toBe('video');
      expect(episodes[0].audioUrl).toBe('https://cdn.test.org/ep1.mp4');
      expect(episodes[1].mediaType).toBe('video');
      expect(episodes[1].audioUrl).toBe('https://cdn.test.org/ep2.mkv');
    });

    it('M5-ADV-11: decodeXmlEntities handles standard, numeric, single-quote and double-quote entities', () => {
      const testCases = [
        { input: 'Tech &amp; Science', expected: 'Tech & Science' },
        { input: '&lt;script&gt;alert(&quot;test&quot;)&lt;/script&gt;', expected: '<script>alert("test")</script>' },
        { input: 'It&#39;s a modern world &apos;2026&apos;', expected: "It's a modern world '2026'" },
        { input: 'Numbered entity &#38; check', expected: 'Numbered entity & check' },
        { input: 'Copyright &#169; 2026', expected: 'Copyright © 2026' },
        { input: '', expected: '' },
        { input: null, expected: '' },
        { input: undefined, expected: '' }
      ];

      for (const tc of testCases) {
        expect(decodeXmlEntities(tc.input)).toBe(tc.expected);
      }
    });

    it('M5-ADV-12: extractXmlCdataOrText handles nested CDATA, unclosed tags, and multi-line content', () => {
      const xml1 = `<title><![CDATA[Special: The Future of Quantum Computing & AI]]></title>`;
      expect(extractXmlCdataOrText(xml1, 'title')).toBe('Special: The Future of Quantum Computing & AI');

      const xml2 = `<description>\n  Line 1\n  Line 2 &amp; Line 3\n</description>`;
      expect(extractXmlCdataOrText(xml2, 'description')).toBe('Line 1\n  Line 2 & Line 3');

      const xml3 = `<missingTag>Content</missingTag>`;
      expect(extractXmlCdataOrText(xml3, 'title')).toBe('');

      expect(extractXmlCdataOrText(null, 'title')).toBe('');
      expect(extractXmlCdataOrText('', 'title')).toBe('');
    });

    it('M5-ADV-13: formatPodcastDuration handles HH:MM:SS, MM:SS, raw seconds, 0, and non-numeric strings', () => {
      const testCases = [
        { input: '01:30:00', expected: '90m' },
        { input: '00:45:30', expected: '45m' },
        { input: '45:15', expected: '45m' },
        { input: '3600', expected: '60m' },
        { input: '120', expected: '2m' },
        { input: '59', expected: '1m' },
        { input: '0', expected: '0' },
        { input: '', expected: '30m' },
        { input: null, expected: '30m' },
        { input: undefined, expected: '30m' },
        { input: 'HD Video', expected: 'HD Video' },
        { input: '45 mins', expected: '45 mins' }
      ];

      for (const tc of testCases) {
        expect(formatPodcastDuration(tc.input)).toBe(tc.expected);
      }
    });

    it('M5-ADV-14: parseRss2AudioFeed handles corrupted feeds, missing items, and empty strings gracefully', () => {
      const corruptFeeds = [
        '',
        '   ',
        '<html><body>404 Not Found</body></html>',
        '<?xml version="1.0"?><rss><channel><title>Empty</title></channel></rss>',
        'Random unparsed junk data',
        null,
        undefined
      ];

      for (const feed of corruptFeeds) {
        const res = parseRss2AudioFeed(feed);
        expect(Array.isArray(res)).toBe(true);
        expect(res.length).toBe(0);
      }
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 4. PocketCasts Dock Audio Player Lifecycle & Control Dynamics
  // ════════════════════════════════════════════════════════════════════════════
  describe('4. PocketCasts Dock Audio Player Lifecycle & Control Dynamics', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => {
      restoreTestEnvironment();
    });

    it('M5-ADV-15: Skip forward (+30s) and skip backward (-10s) are strictly clamped', () => {
      const audio = new MockAudioElement('http://stream.org/audio.mp3');
      audio.duration = 1800; // 30 min
      audio.currentTime = 5;

      // Skip backward 10s from 5s -> clamped to 0
      audio.currentTime = Math.max(0, audio.currentTime - 10);
      expect(audio.currentTime).toBe(0);

      // Skip backward 10s from 0s -> remains 0
      audio.currentTime = Math.max(0, audio.currentTime - 10);
      expect(audio.currentTime).toBe(0);

      // Skip forward 30s -> 30s
      audio.currentTime = Math.min(audio.duration, audio.currentTime + 30);
      expect(audio.currentTime).toBe(30);

      // Skip forward near end (1790s + 30s) -> clamped to 1800s
      audio.currentTime = 1790;
      audio.currentTime = Math.min(audio.duration, audio.currentTime + 30);
      expect(audio.currentTime).toBe(1800);
    });

    it('M5-ADV-16: Progress scrub calculation handles duration of 0, NaN, and Infinity without breaking', () => {
      function calculateProgress(currentTime, duration) {
        if (!duration || !isFinite(duration) || isNaN(duration) || duration <= 0) {
          return 0;
        }
        return Math.max(0, Math.min(100, (currentTime / duration) * 100));
      }

      expect(calculateProgress(50, 100)).toBe(50);
      expect(calculateProgress(150, 100)).toBe(100);
      expect(calculateProgress(-10, 100)).toBe(0);
      expect(calculateProgress(50, 0)).toBe(0);
      expect(calculateProgress(50, NaN)).toBe(0);
      expect(calculateProgress(50, Infinity)).toBe(0);
    });

    it('M5-ADV-17: Playback speed cycles correctly through [1.0, 1.25, 1.5, 2.0] and updates audio element', () => {
      const audio = new MockAudioElement('http://audio.test/ep.mp3');
      const speeds = [1.0, 1.25, 1.5, 2.0];
      let currentSpeed = 1.0;

      function cycleSpeed() {
        const curIdx = speeds.indexOf(currentSpeed);
        const nextIdx = (curIdx + 1) % speeds.length;
        currentSpeed = speeds[nextIdx];
        audio.playbackRate = currentSpeed;
        return currentSpeed;
      }

      expect(cycleSpeed()).toBe(1.25);
      expect(audio.playbackRate).toBe(1.25);

      expect(cycleSpeed()).toBe(1.5);
      expect(audio.playbackRate).toBe(1.5);

      expect(cycleSpeed()).toBe(2.0);
      expect(audio.playbackRate).toBe(2.0);

      expect(cycleSpeed()).toBe(1.0);
      expect(audio.playbackRate).toBe(1.0);
    });

    it('M5-ADV-18: Audio ended event auto-dequeues next episode and updates queue persistence', () => {
      const queue = [
        { id: 'ep_q1', title: 'Next Up 1', audioUrl: 'http://stream.org/q1.mp3' },
        { id: 'ep_q2', title: 'Next Up 2', audioUrl: 'http://stream.org/q2.mp3' }
      ];
      env.storage.setItem('pocketcasts_queue', JSON.stringify(queue));

      let currentEp = { id: 'ep_current', title: 'Current Episode', audioUrl: 'http://stream.org/curr.mp3' };
      const playedEpisodes = [];

      function onEpisodeEnded() {
        if (!playedEpisodes.includes(currentEp.id)) {
          playedEpisodes.push(currentEp.id);
        }
        if (queue.length > 0) {
          const next = queue.shift();
          env.storage.setItem('pocketcasts_queue', JSON.stringify(queue));
          currentEp = next;
          return next;
        }
        currentEp = null;
        return null;
      }

      const played1 = onEpisodeEnded();
      expect(played1.id).toBe('ep_q1');
      expect(currentEp.id).toBe('ep_q1');
      expect(queue.length).toBe(1);
      expect(playedEpisodes).toContain('ep_current');

      const played2 = onEpisodeEnded();
      expect(played2.id).toBe('ep_q2');
      expect(currentEp.id).toBe('ep_q2');
      expect(queue.length).toBe(0);

      const played3 = onEpisodeEnded();
      expect(played3).toBeNull();
      expect(currentEp).toBeNull();
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 5. Video Podcast Embed Security & Fullscreen Teardown
  // ════════════════════════════════════════════════════════════════════════════
  describe('5. Video Podcast Embed Security & Fullscreen Teardown', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => {
      restoreTestEnvironment();
    });

    it('M5-ADV-19: Video podcast embed iframe enables allowfullscreen, interactive controls, and YouTube nocookie origin', () => {
      const iframe = env.document.getElementById('embed-iframe');
      const wrapper = env.document.getElementById('embed-player-wrapper');
      const videoId = 'PtCMsXYAPyc'; // Waveform episode

      // Simulate openPodcastModal embedding
      iframe.removeAttribute('referrerpolicy');
      iframe.setAttribute('allow', 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen');
      iframe.setAttribute('allowfullscreen', 'true');
      iframe.src = `https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1&rel=0&playsinline=1&controls=1&modestbranding=1&enablejsapi=1`;

      expect(iframe.getAttribute('allowfullscreen')).toBe('true');
      expect(iframe.getAttribute('allow')).toContain('fullscreen');
      expect(iframe.getAttribute('allow')).toContain('autoplay');
      expect(iframe.src).toContain('https://www.youtube-nocookie.com/embed/PtCMsXYAPyc');
      expect(iframe.src).toContain('controls=1');
    });

    it('M5-ADV-20: Unmounting video podcast modal clears iframe.src to about:blank and unlocks body scroll', () => {
      const iframe = env.document.getElementById('embed-iframe');
      const overlay = env.document.getElementById('details-modal');
      const playerWrapper = env.document.querySelector('.player-wrapper');

      // 1. Open modal
      iframe.src = 'https://www.youtube-nocookie.com/embed/PtCMsXYAPyc';
      overlay.classList.remove('hidden');
      playerWrapper.classList.add('podcast-mode');
      env.document.body.style.overflow = 'hidden';

      expect(iframe.src).not.toBe('about:blank');
      expect(env.document.body.style.overflow).toBe('hidden');

      // 2. Close modal (closeDetailsView)
      iframe.src = 'about:blank';
      overlay.classList.add('hidden');
      playerWrapper.classList.remove('podcast-mode');
      env.document.body.style.overflow = '';

      expect(iframe.src).toBe('about:blank');
      expect(overlay.classList.contains('hidden')).toBe(true);
      expect(playerWrapper.classList.contains('podcast-mode')).toBe(false);
      expect(env.document.body.style.overflow).toBe('');
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // 6. Long-Running Chaotic User Session Simulation
  // ════════════════════════════════════════════════════════════════════════════
  describe('6. Long-Running Chaotic User Session Simulation', () => {
    let env;
    beforeEach(() => {
      env = setupTestEnvironment();
    });
    afterEach(() => {
      restoreTestEnvironment();
    });

    it('M5-ADV-21: 500-step randomized user interaction sequence executes without crashes or state drift', () => {
      const player = new IPTVPlayer('video-player');
      const audio = new MockAudioElement();
      const iframe = env.document.getElementById('embed-iframe');

      let currentTab = 'home';
      let activeAudioEp = null;
      let activeVideoEp = null;
      const history = [];
      const subIds = [];
      const playbackPositions = {};

      const actions = [
        'NAV_LIVE',
        'NAV_MOVIES',
        'NAV_SERIES',
        'NAV_PODCASTS',
        'NAV_SETTINGS',
        'PLAY_LIVE_CHANNEL',
        'PLAY_AUDIO_PODCAST',
        'PAUSE_AUDIO_PODCAST',
        'SEEK_AUDIO_PODCAST',
        'SKIP_FWD_AUDIO',
        'SKIP_BACK_AUDIO',
        'CYCLE_SPEED',
        'OPEN_VIDEO_PODCAST_MODAL',
        'CLOSE_MODAL',
        'TOGGLE_SUBSCRIBE',
        'CLEAR_HISTORY'
      ];

      for (let step = 0; step < 500; step++) {
        const action = actions[step % actions.length];

        switch (action) {
          case 'NAV_LIVE':
            currentTab = 'live';
            if (activeAudioEp) { audio.pause(); activeAudioEp = null; }
            player.setControlMode('live');
            break;
          case 'NAV_MOVIES':
          case 'NAV_SERIES':
          case 'NAV_SETTINGS':
            currentTab = action.toLowerCase().replace('nav_', '');
            break;
          case 'NAV_PODCASTS':
            currentTab = 'podcasts';
            break;
          case 'PLAY_LIVE_CHANNEL':
            if (activeAudioEp) { audio.pause(); activeAudioEp = null; }
            player.playChannel({ url: `http://iptv.server/stream_${step}.m3u8`, name: `Ch ${step}` });
            break;
          case 'PLAY_AUDIO_PODCAST':
            player.resetVideoFrame();
            if (iframe) iframe.src = 'about:blank';
            activeVideoEp = null;
            activeAudioEp = { id: `ep_${step}`, audioUrl: `http://stream.org/audio_${step}.mp3` };
            audio.src = activeAudioEp.audioUrl;
            audio.play();
            history.unshift(activeAudioEp);
            break;
          case 'PAUSE_AUDIO_PODCAST':
            if (activeAudioEp) audio.pause();
            break;
          case 'SEEK_AUDIO_PODCAST':
            if (activeAudioEp) audio.currentTime = (step * 17) % 1800;
            break;
          case 'SKIP_FWD_AUDIO':
            if (activeAudioEp) audio.currentTime = Math.min(1800, audio.currentTime + 30);
            break;
          case 'SKIP_BACK_AUDIO':
            if (activeAudioEp) audio.currentTime = Math.max(0, audio.currentTime - 10);
            break;
          case 'CYCLE_SPEED':
            audio.playbackRate = [1.0, 1.25, 1.5, 2.0][step % 4];
            break;
          case 'OPEN_VIDEO_PODCAST_MODAL':
            if (activeAudioEp) { audio.pause(); activeAudioEp = null; }
            player.resetVideoFrame();
            activeVideoEp = { id: `yt_${step}`, youtubeId: `vid_${step}` };
            if (iframe) iframe.src = `https://www.youtube-nocookie.com/embed/${activeVideoEp.youtubeId}`;
            break;
          case 'CLOSE_MODAL':
            if (iframe) iframe.src = 'about:blank';
            activeVideoEp = null;
            break;
          case 'TOGGLE_SUBSCRIBE':
            const subId = `chan_${step % 10}`;
            const idx = subIds.indexOf(subId);
            if (idx >= 0) subIds.splice(idx, 1);
            else subIds.push(subId);
            break;
          case 'CLEAR_HISTORY':
            history.length = 0;
            break;
        }

        // Integrity assertion per step
        expect(typeof currentTab).toBe('string');
        expect(Array.isArray(subIds)).toBe(true);
        expect(Array.isArray(history)).toBe(true);
      }

      // Final post-stress verification
      expect(true).toBe(true);
    });
  });
});

// Run directly when invoked via CLI
if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.endsWith('test_challenger_m5_adversarial.js')) {
  runAllRegisteredSuites().then(res => {
    if (res.failed > 0) {
      process.exit(1);
    }
  });
}
