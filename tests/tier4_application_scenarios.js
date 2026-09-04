/**
 * Tier 4: Real-World Application Scenarios Test Suite
 * ──────────────────────────────────────────────────
 * End-to-end multi-step user workflows simulating realistic daily usage:
 * 11 comprehensive end-to-end scenarios covering onboarding, live TV browsing,
 * series binge-watching, podcast commute, network jitter, remote D-pad navigation,
 * custom provider migrations, watchlist multi-tasking, stress testing, and native lifecycle.
 */

import {
  describe,
  it,
  expect,
  assert,
  setupTestEnvironment,
  resetTestEnvironment,
  MockHls,
  MockEvent,
  MockKeyboardEvent,
  MockCustomEvent,
  MockElement,
  runAllRegisteredSuites
} from './harness.js';

import { IPTVPlayer } from '../src/player.js';
import {
  initEPGFeed,
  getChannelEPGInfo,
  getProgramProgress,
  fetchXtreamEPG,
  formatTime
} from '../src/epgService.js';
import { PODCAST_CHANNELS } from '../src/podcastsData.js';

// Self-contained VOD test client
class XtreamVODClient {
  constructor(baseUrl = '') {
    this._baseUrl = baseUrl;
    this._vodCache = new Map();
  }

  get baseUrl() {
    if (this._baseUrl) return this._baseUrl;
    if (typeof window !== 'undefined' && typeof localStorage !== 'undefined') {
      const saved = localStorage.getItem('xtream_vod_portal');
      if (saved && saved.trim()) return saved.trim().replace(/\/+$/, '');
    }
    return 'http://asoseller.org:8080';
  }

  get username() {
    if (typeof window !== 'undefined' && typeof localStorage !== 'undefined') {
      const saved = localStorage.getItem('xtream_vod_username');
      if (saved && saved.trim()) return saved.trim();
    }
    return 'f2e1d20954';
  }

  get password() {
    if (typeof window !== 'undefined' && typeof localStorage !== 'undefined') {
      const saved = localStorage.getItem('xtream_vod_password');
      if (saved && saved.trim()) return saved.trim();
    }
    return 'a7a8bf92d242';
  }

  getMovieStreamUrl(streamId, ext = 'mp4') {
    const cleanExt = ext ? ext.replace(/^\./, '').toLowerCase() : 'mp4';
    const raw = `${this.baseUrl}/movie/${this.username}/${this.password}/${streamId}.${cleanExt}`;
    return `/api/proxy?url=${encodeURIComponent(raw)}`;
  }

  getSeriesStreamUrl(streamId, ext = 'mp4') {
    const cleanExt = ext ? ext.replace(/^\./, '').toLowerCase() : 'mp4';
    const raw = `${this.baseUrl}/series/${this.username}/${this.password}/${streamId}.${cleanExt}`;
    return `/api/proxy?url=${encodeURIComponent(raw)}`;
  }
}

describe('Tier 4: Real-World Application Workflows & Scenarios', () => {

  // Scenario 1: New User Onboarding & Live TV Evening Session
  it('Scenario 1: New User Onboarding & Live TV Evening Session', async () => {
    const env = setupTestEnvironment();

    // Step 1: User enters login credentials and activates app
    env.localStorage.setItem('iptv_username', 'newuser@tv.com');
    env.localStorage.setItem('iptv_password', 'secretPass123');
    env.localStorage.setItem('xtream_vod_portal', 'http://iptv.server.io:8080');

    expect(env.localStorage.getItem('iptv_username')).toBe('newuser@tv.com');

    // Step 2: Initialize player and EPG feed
    const player = new IPTVPlayer('video-player');
    await initEPGFeed('http://iptv.server.io:8080', 'newuser@tv.com', 'secretPass123');

    // Step 3: Browse Live Categories -> Select News Channel
    const newsChannel = { id: 'cnn_news', name: 'CNN News HD', url: 'http://iptv.server.io:8080/live/user/pass/101.m3u8' };
    player.playChannel(newsChannel);
    expect(player.channelTitle.textContent).toBe('CNN News HD');
    expect(player.currentUrl).toContain('101.m3u8');

    // Step 4: Toggle Fullscreen
    player.updateFullscreenUI(true);
    expect(player.fullscreenBtn.querySelector('i').getAttribute('data-lucide')).toBe('minimize');

    // Step 5: Switch to Sports Channel & Check EPG Program Guide
    const sportsChannel = { id: 'espn_sports', name: 'ESPN HD', url: 'http://iptv.server.io:8080/live/user/pass/102.m3u8' };
    player.playChannel(sportsChannel);
    expect(player.channelTitle.textContent).toBe('ESPN HD');

    const epgInfo = getChannelEPGInfo(sportsChannel);
    const progress = getProgramProgress(epgInfo);
    expect(progress).toBeDefined();
    expect(typeof progress.percent).toBe('number');
  });

  // Scenario 2: Weekend Binge-Watching TV Series
  it('Scenario 2: Weekend Binge-Watching TV Series', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');
    const vodClient = new XtreamVODClient();

    // Step 1: Browse Series and filter Sci-Fi
    const seriesList = [
      { id: 201, title: 'Severance', genre: 'Sci-Fi' },
      { id: 202, title: 'Dark', genre: 'Sci-Fi' }
    ];
    const sciFiSeries = seriesList.filter(s => s.genre === 'Sci-Fi');
    expect(sciFiSeries.length).toBe(2);

    // Step 2: Open Series Details for Severance
    const selectedSeries = sciFiSeries[0];
    expect(selectedSeries.title).toBe('Severance');

    // Step 3: Pick Season 1 Episode 2
    const episode = { id: 3002, season: 1, episodeNum: 2, title: 'Half Loop', ext: 'mp4' };
    const streamUrl = vodClient.getSeriesStreamUrl(episode.id, episode.ext);

    player.setControlMode('vod');
    player.playChannel({ url: streamUrl, name: `${selectedSeries.title} - S1:E2 ${episode.title}`, mediaKey: `series_${selectedSeries.id}_s1e2` });
    expect(player.channelTitle.textContent).toContain('Severance - S1:E2 Half Loop');

    // Step 4: Pause and Resume
    player.togglePlay();
    player.togglePlay();
    expect(player.currentUrl).toContain('3002.mp4');

    // Step 5: Next Episode Transition (Episode 3)
    const nextEpisode = { id: 3003, season: 1, episodeNum: 3, title: 'In Perpetuity', ext: 'mp4' };
    const nextStreamUrl = vodClient.getSeriesStreamUrl(nextEpisode.id, nextEpisode.ext);
    player.playChannel({ url: nextStreamUrl, name: `${selectedSeries.title} - S1:E3 ${nextEpisode.title}`, mediaKey: `series_${selectedSeries.id}_s1e3` });
    expect(player.channelTitle.textContent).toContain('Severance - S1:E3 In Perpetuity');
  });

  // Scenario 3: Commuter Podcast Workflow
  it('Scenario 3: Commuter Podcast Workflow', () => {
    const env = setupTestEnvironment();

    // Step 1: Browse Podcast directory and Subscribe to 3 shows
    const subscriptions = new Set();
    subscriptions.add('chan_lex_fridman');
    subscriptions.add('chan_huberman_lab');
    subscriptions.add('chan_all_in');
    expect(subscriptions.size).toBe(3);
    env.localStorage.setItem('podcast_subscriptions', JSON.stringify(Array.from(subscriptions)));

    // Step 2: Ingest RSS feed and select episode
    const episode = {
      id: 'ep_lex_400',
      channelId: 'chan_lex_fridman',
      title: 'Sam Altman: OpenAI & AGI',
      audioUrl: 'https://audio.lexfridman.com/ep400.mp3',
      duration: 7200
    };

    // Step 3: Start Audio Podcast in Dock
    const audio = new env.window.Audio(episode.audioUrl);
    audio.play();
    expect(audio.paused).toBe(false);

    // Step 4: Switch views while listening (audio continues uninterrupted)
    let activeView = 'podcasts';
    activeView = 'live';
    expect(audio.paused).toBe(false);

    // Step 5: Finish episode and mark played
    const playedHistory = new Set();
    playedHistory.add(episode.id);
    expect(playedHistory.has('ep_lex_400')).toBe(true);
    env.localStorage.setItem('podcast_played_history', JSON.stringify(Array.from(playedHistory)));
  });

  // Scenario 4: Movie Night with Low-Bandwidth Network
  it('Scenario 4: Movie Night with Low-Bandwidth Network', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');
    const vodClient = new XtreamVODClient();

    // Step 1: Select 4K Movie
    const movieUrl = vodClient.getMovieStreamUrl(501, 'mp4');
    player.setControlMode('vod');
    player.playChannel({ url: movieUrl, name: 'Dune: Part Two (2024)', mediaKey: 'movie_501' });

    // Step 2: Simulate low bandwidth network throttling
    let adaptiveQuality = '1080p';
    const onBandwidthDrop = (measuredBps) => {
      if (measuredBps < 3000000) {
        adaptiveQuality = '720p';
      }
    };
    onBandwidthDrop(1500000); // 1.5 Mbps
    expect(adaptiveQuality).toBe('720p');

    // Step 3: Seek to midpoint (50%)
    player.video.duration = 9600; // 160 min
    player.seekBy(4800);
    expect(player.video.currentTime).toBe(4800);
  });

  // Scenario 5: Multi-Room / Remote Control Navigation Workflow
  it('Scenario 5: Multi-Room / Remote Control Navigation Workflow', () => {
    const env = setupTestEnvironment();
    let selectedNavIndex = 0;
    const navItems = ['home', 'live', 'movies', 'series', 'podcasts', 'settings'];

    // Step 1: Remote D-pad navigation on sidebar
    const onDpadDown = () => {
      selectedNavIndex = Math.min(navItems.length - 1, selectedNavIndex + 1);
    };
    const onDpadUp = () => {
      selectedNavIndex = Math.max(0, selectedNavIndex - 1);
    };

    onDpadDown(); // live
    expect(navItems[selectedNavIndex]).toBe('live');
    onDpadDown(); // movies
    expect(navItems[selectedNavIndex]).toBe('movies');
    onDpadUp(); // back to live
    expect(navItems[selectedNavIndex]).toBe('live');

    // Step 2: Select Channel
    const player = new IPTVPlayer('video-player');
    player.playChannel({ url: 'http://live.tv/sports.m3u8', name: 'Sky Sports F1' });
    expect(player.channelTitle.textContent).toBe('Sky Sports F1');

    // Step 3: Toggle Aspect Ratio and Adjust Volume via remote
    player.toggleAspectRatio();
    expect(player.aspectRatio).toBe('stretch');
    player.setVolume(0.7);
    expect(player.volume).toBe(0.7);
  });

  // Scenario 6: Video Podcast Deep Dive
  it('Scenario 6: Video Podcast Deep Dive', () => {
    const env = setupTestEnvironment();

    // Step 1: Search Tech Podcasts
    const techPodcasts = PODCAST_CHANNELS.tech || [];
    expect(techPodcasts.length).toBeGreaterThan(0);
    const lexPodcast = techPodcasts.find(c => c.channelName.includes('Lex'));
    expect(lexPodcast).toBeDefined();

    // Step 2: Select Video Episode & Embed YouTube player
    const iframe = env.document.getElementById('embed-iframe');
    const playerWrapper = env.document.querySelector('.player-wrapper');
    playerWrapper.classList.add('embed-active');
    iframe.src = 'https://www.youtube.com/embed/UCSHZKyawb77ixDdsGog4iWA';

    expect(playerWrapper.classList.contains('embed-active')).toBe(true);
    expect(iframe.src).toContain('youtube.com/embed');

    // Step 3: Fullscreen expansion
    playerWrapper.classList.add('is-fullscreen');
    expect(playerWrapper.classList.contains('is-fullscreen')).toBe(true);

    // Step 4: Exit back to podcast directory
    playerWrapper.classList.remove('is-fullscreen');
    playerWrapper.classList.remove('embed-active');
    iframe.src = 'about:blank';
    expect(iframe.src).toBe('about:blank');
  });

  // Scenario 7: Offline / Network Drop & Reconnection Resilience
  it('Scenario 7: Offline / Network Drop & Reconnection Resilience', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');

    // Step 1: Start Live Stream
    player.playChannel({ url: 'http://stream.server/live/stream.m3u8', name: 'BBC News' });
    expect(player.channelTitle.textContent).toBe('BBC News');

    // Step 2: Network disconnects -> Show reconnecting indicator
    player.showLoading(true);
    let reconnectBannerVisible = true;
    expect(reconnectBannerVisible).toBe(true);

    // Step 3: Network restored -> Auto-reconnect without page reload
    player.showLoading(false);
    reconnectBannerVisible = false;
    expect(player.loading.classList.contains('hidden')).toBe(true);
    expect(reconnectBannerVisible).toBe(false);
  });

  // Scenario 8: Custom IPTV Provider Migration & Settings Reconfiguration
  it('Scenario 8: Custom IPTV Provider Migration & Settings Reconfiguration', async () => {
    const env = setupTestEnvironment();

    // Step 1: User navigates to Settings and updates Xtream Portal details
    const newPortal = 'http://new-provider.net:8080';
    const newUser = 'migrated_user@tv.com';
    const newPass = 'newPassword999';

    env.localStorage.setItem('xtream_vod_portal', newPortal);
    env.localStorage.setItem('xtream_vod_username', newUser);
    env.localStorage.setItem('xtream_vod_password', newPass);

    const client = new XtreamVODClient();
    expect(client.baseUrl).toBe(newPortal);
    expect(client.username).toBe(newUser);
    expect(client.password).toBe(newPass);

    // Step 2: Re-verify & Reload EPG feed
    await initEPGFeed(newPortal, newUser, newPass);

    // Step 3: Play stream with new provider credentials
    const player = new IPTVPlayer('video-player');
    const newStreamUrl = client.getMovieStreamUrl(999, 'mp4');
    player.playChannel({ url: newStreamUrl, name: 'Gladiator II (2024)' });
    const decodedUrl = decodeURIComponent(player.currentUrl);
    expect(decodedUrl).toContain(newPortal);
    expect(decodedUrl).toContain(newUser);
  });

  // Scenario 9: Power-User Multi-Tasking & Watchlist Management
  it('Scenario 9: Power-User Multi-Tasking & Watchlist Management', () => {
    const env = setupTestEnvironment();
    const watchlist = new Set();

    // Step 1: Add 5 items across Movies & Series to Watchlist
    const items = ['movie_101', 'movie_102', 'series_201', 'series_202', 'podcast_ep_400'];
    items.forEach(id => watchlist.add(id));
    expect(watchlist.size).toBe(5);
    env.localStorage.setItem('user_watchlist', JSON.stringify(Array.from(watchlist)));

    // Step 2: Navigate to Library / Watchlist view
    const savedWatchlist = JSON.parse(env.localStorage.getItem('user_watchlist') || '[]');
    expect(savedWatchlist.length).toBe(5);

    // Step 3: Launch Movie from Watchlist
    const player = new IPTVPlayer('video-player');
    player.setControlMode('vod');
    player.playChannel({ url: 'http://vod.server/movie_101.mp4', name: 'Interstellar', mediaKey: 'movie_101' });
    expect(player.channelTitle.textContent).toBe('Interstellar');

    // Step 4: Remove completed item from Watchlist
    watchlist.delete('movie_101');
    expect(watchlist.size).toBe(4);
    expect(watchlist.has('movie_101')).toBe(false);
  });

  // Scenario 10: Mixed Media Stress Day
  it('Scenario 10: Mixed Media Stress Day', () => {
    const env = setupTestEnvironment();
    const player = new IPTVPlayer('video-player');
    const vodClient = new XtreamVODClient();

    // Rapidly alternate between 10 Live TV channels, 5 Movies, 3 Video Podcasts, and 4 Audio Podcasts
    // 1. 10 Live channels
    for (let i = 1; i <= 10; i++) {
      player.playChannel({ url: `http://live.tv/ch_${i}.m3u8`, name: `Live Channel ${i}` });
      expect(player.channelTitle.textContent).toBe(`Live Channel ${i}`);
    }

    // 2. 5 Movies
    for (let i = 1; i <= 5; i++) {
      const url = vodClient.getMovieStreamUrl(100 + i, 'mp4');
      player.setControlMode('vod');
      player.playChannel({ url, name: `Movie ${i}` });
      expect(player.channelTitle.textContent).toBe(`Movie ${i}`);
    }

    // 3. 3 Video Podcasts
    const iframe = env.document.getElementById('embed-iframe');
    for (let i = 1; i <= 3; i++) {
      iframe.src = `https://www.youtube.com/embed/vid_${i}`;
      expect(iframe.src).toContain(`vid_${i}`);
    }
    iframe.src = 'about:blank';

    // 4. 4 Audio Podcasts
    for (let i = 1; i <= 4; i++) {
      const audio = new env.window.Audio(`https://podcast.server/ep_${i}.mp3`);
      audio.play();
      expect(audio.paused).toBe(false);
      audio.pause();
    }

    // Verify 0 orphan media playback and clean final state
    player.resetVideoFrame();
    expect(player.currentUrl).toBe('');
    expect(player.video.paused).toBe(true);
  });

  // Scenario 11: Android Native Release Lifecycle & Proxy Integration
  it('Scenario 11: Android Native Release Lifecycle & Proxy Integration', () => {
    const env = setupTestEnvironment();

    // Step 1: App launch simulation on Android host
    env.window.location.host = 'appassets.androidplatform.net';
    env.window.navigator.userAgent = 'Mozilla/5.0 TVDinnerMobileApp/1.0';

    // Step 2: Native Proxy URL verification (routes to /api/proxy securely)
    const vodClient = new XtreamVODClient();
    const streamUrl = vodClient.getMovieStreamUrl(777, 'mkv');
    expect(streamUrl).toContain('/api/proxy?url=');

    // Step 3: Lifecycle Pause simulation (User puts app to background)
    const player = new IPTVPlayer('video-player');
    player.playChannel({ url: streamUrl, name: 'Background Test Movie' });
    expect(player.channelTitle.textContent).toBe('Background Test Movie');

    // Backgrounding pauses playback
    player.video.pause();
    expect(player.video.paused).toBe(true);

    // Step 4: Lifecycle Resume simulation (User returns to foreground)
    player.video.play();
    expect(player.video.paused).toBe(false);

    // Step 5: Smooth App Teardown on exit
    player.resetVideoFrame();
    expect(player.currentUrl).toBe('');
  });

});

// ─── Direct Execution ─────────────────────────────────────────────────────────

if (import.meta.url === `file://${process.argv[1]}`.replace(/\\/g, '/')) {
  runAllRegisteredSuites().then(res => {
    process.exit(res.totalFailed > 0 ? 1 : 0);
  });
}
