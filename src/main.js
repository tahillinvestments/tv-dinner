import { createIcons, Menu, X, Play, Pause, Tv, Search, Info, AlertTriangle, RefreshCw, Volume2, Volume1, VolumeX, Maximize, SquareStack, ExternalLink, Star, Monitor, Settings, ArrowLeft, Home, Film, ChevronRight, ChevronLeft, Radio, Globe } from 'lucide';
import { fetchAndParseM3U, parseM3U } from './parser';
import { IPTVPlayer } from './player';
import { searchMulti, getMovieDetails, getTVShowDetails, getTVSeasonDetails, getTMDBImageUrl, getTrending, getTrendingMovies, getTrendingTV, getTopRated, getTopRatedTV, getByGenre } from './tmdb';
import { getStreamSources } from './streamApi';
import {
  PODCAST_CHANNELS,
  getAllPodcastChannels,
  getHeroPodcast,
  getLatestPodcastEpisodes,
  loadTopItunesPodcasts,
  searchPodcastChannels,
  searchRealPodcastAPI,
  fetchChannelPastEpisodes,
  fetchChannelPastEpisodesNextPage,
  fetchForYouCuratedPodcasts
} from './podcastsData';
import './style.css';

// Initialize Lucide icons
const iconConfig = {
  icons: {
    Menu, X, Play, Pause, Tv, Search, Info, AlertTriangle, RefreshCw, Volume2, Volume1, VolumeX, Maximize, SquareStack, ExternalLink, Star, Monitor, Settings, ArrowLeft, Home, Film, ChevronRight, ChevronLeft, Radio, Globe
  }
};

// Global Popup Guard: Intercept popup windows & redirects from third-party scripts
(function setupPopupGuard() {
  try {
    const originalOpen = window.open;
    window.open = function(url, target, features) {
      console.warn('[Popup Guard] Blocked external popup window open:', url);
      return null;
    };

    // Capture & block popup anchor clicks
    document.addEventListener('click', (e) => {
      const link = e.target.closest('a');
      if (link && link.target === '_blank') {
        const href = link.getAttribute('href') || '';
        if (href.includes('about:blank') || href.startsWith('http') && !href.includes(window.location.hostname)) {
          // Allow internal app links, block untrusted ad popups spawned from player
          const playerWrapper = document.querySelector('.player-wrapper');
          if (playerWrapper && playerWrapper.classList.contains('embed-active')) {
            e.preventDefault();
            e.stopPropagation();
            console.warn('[Popup Guard] Prevented target=_blank ad click:', href);
          }
        }
      }
    }, true);
  } catch (e) {
    console.warn('[Popup Guard] Setup notice:', e);
  }
})();

// Default Proxy URL fallback
const DEFAULT_RENDER_PROXY = '/api/proxy';

// Global State
const state = {
  activeTab: 'home', // 'home', 'search', 'live', 'library', 'settings'
  channels: [],
  filteredChannels: [],
  categories: [],
  selectedCategory: 'All Channels',
  searchQuery: '',
  favorites: JSON.parse(localStorage.getItem('iptv_favorites') || '[]'),
  favoritePodcasts: JSON.parse(localStorage.getItem('iptv_favorite_podcasts') || '[]'),
  recents: JSON.parse(localStorage.getItem('iptv_recents') || '[]'),
  watchlist: JSON.parse(localStorage.getItem('vod_watchlist') || '[]'),
  currentPlayingUrl: null,
  usOnly: JSON.parse(localStorage.getItem('iptv_us_only') ?? 'true'),
  
  // Movies & TV Spotlight / Carousel State
  trendingItems: [],
  moviesSearchResults: [],
  selectedMedia: null,
  activeStreamSse: null,
  resolvedSources: [],
  activeSourceIndex: -1
};

// ==========================================================================
// POCKET CASTS REDESIGN STATE & APP CONTROLLER (Moved to top to prevent TDZ error)
// ==========================================================================
let pocketcastsState = {
  subscribedShowIds: JSON.parse(localStorage.getItem('pocketcasts_subscribed_ids') || '[]'),
  subscribedShows: JSON.parse(localStorage.getItem('pocketcasts_subscribed_shows') || '[]'),
  queue: JSON.parse(localStorage.getItem('pocketcasts_queue') || '[]'),
  history: JSON.parse(localStorage.getItem('pocketcasts_history') || '[]'),
  currentView: 'discover',
  activeCategory: 'all',
  currentEpisode: null,
  audioElement: null,
  isPlaying: false,
  playbackSpeed: 1.0,
  navInitialized: false
};


// Helper to construct dynamic IPTV playlist URL from localStorage or credentials
function getPlaylistUrl() {
  const portalUrl = localStorage.getItem('iptv_portal_url') || 'http://portal5458.com:8080';
  const username = (localStorage.getItem('iptv_username') || 'SAPPTV12').trim();
  const password = (localStorage.getItem('iptv_password') || 'REMOTE6202').trim();
  
  if (portalUrl && username && password) {
    return `${portalUrl}/get.php?username=${username}&password=${password}&type=m3u_plus&output=ts`;
  }
  return '';
}

const MAX_RECENTS = 20;

// Embed providers — audited & verified working HD sources
const EMBED_PROVIDERS = [
  {
    name: 'VidLink PRO (Clean HD)',
    movie: (id) => `https://vidlink.pro/movie/${id}`,
    tv: (id, s, e) => `https://vidlink.pro/tv/${id}/${s}/${e}`,
  },
  {
    name: 'VidSrc PRO',
    movie: (id) => `https://vidsrc.me/embed/movie?tmdb=${id}`,
    tv: (id, s, e) => `https://vidsrc.me/embed/tv?tmdb=${id}&season=${s}&episode=${e}`,
  },
  {
    name: 'Videasy (Multi-Sub)',
    movie: (id) => `https://player.videasy.net/movie/${id}`,
    tv: (id, s, e) => `https://player.videasy.net/tv/${id}/${s}/${e}`,
  },
  {
    name: 'VidSrc.net (Fast HD)',
    movie: (id) => `https://vidsrc.net/embed/movie/${id}`,
    tv: (id, s, e) => `https://vidsrc.net/embed/tv/${id}/${s}/${e}`,
  },
  {
    name: 'VidSrc.pm Server',
    movie: (id) => `https://vidsrc.pm/embed/movie/${id}`,
    tv: (id, s, e) => `https://vidsrc.pm/embed/tv/${id}/${s}/${e}`,
  },
  {
    name: 'VixSrc Backup',
    movie: (id) => `https://vixsrc.to/movie/${id}`,
    tv: (id, s, e) => `https://vixsrc.to/tv/${id}/${s}/${e}`,
  },
];

// Initialize components
let player;
let searchDebounceTimer;
let appInitialized = false;

async function initApp() {
  if (appInitialized) return;
  appInitialized = true;
  window.state = state;
  window.fetchChannelPastEpisodesNextPage = fetchChannelPastEpisodesNextPage;

  console.log('[App Init] Initializing application components...');
  // Init player with Channel Up / Channel Down handler
  player = new IPTVPlayer('video-player', {
    onChannelChange: (dir) => changeLiveChannel(dir)
  });
  
  // Initially append player to live container
  const playerSection = document.getElementById('player-section');
  const liveContainer = document.getElementById('live-player-container');
  if (playerSection && liveContainer) {
    liveContainer.appendChild(playerSection);
  }

  // Setup DOM interaction
  setupNavigation();
  setupSearch();
  setupDetailsView();
  setupSettingsScreen();

  // Setup US-Only Toggle & Channel Switcher listeners
  setupLiveChannelControls();

  // Render initial icons
  createIcons(iconConfig);

  // Set default landing tab to Home Select Screen
  switchTab('home');

  // Load playlist (Live TV mode data) in background
  loadIPTVPlaylist();
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initApp);
} else {
  initApp();
}

// Setup tab-based screen navigation
function setupNavigation() {
  const navButtons = document.querySelectorAll('.nav-links .nav-link, .mobile-drawer-link');
  
  navButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const tabName = btn.getAttribute('data-tab');
      switchTab(tabName);
      // Close mobile drawer overlay if open
      const mobileDrawer = document.getElementById('mobile-drawer-overlay');
      if (mobileDrawer) mobileDrawer.classList.add('hidden');
    });
  });

  // Home Selection Cards Click Handler
  document.querySelectorAll('.home-select-card[data-nav]').forEach(card => {
    card.addEventListener('click', () => {
      const targetTab = card.getAttribute('data-nav');
      if (targetTab) switchTab(targetTab);
    });
  });

  // Mobile hamburger button toggle
  const mobileToggle = document.getElementById('sidebar-toggle');
  const mobileDrawer = document.getElementById('mobile-drawer-overlay');
  const mobileClose = document.getElementById('mobile-drawer-close');

  if (mobileToggle && mobileDrawer) {
    mobileToggle.addEventListener('click', () => {
      mobileDrawer.classList.remove('hidden');
    });
  }

  if (mobileClose && mobileDrawer) {
    mobileClose.addEventListener('click', () => {
      mobileDrawer.classList.add('hidden');
    });
  }
}

// Stop and discontinue all active streams, audio playback, embed frames, and leftover artifacts
function stopAllMediaPlayback(options = {}) {
  const { keepPodcast = false, keepLive = false, keepVod = false } = options;
  console.log('[Player Cleanup] Executing media reset...');

  // 1. Reset Live TV / Shared Video Player
  if (!keepLive && !keepVod) {
    if (typeof player !== 'undefined' && player) {
      try {
        player.resetVideoFrame();
      } catch (e) {}
    } else {
      const videoEl = document.getElementById('video-player');
      if (videoEl) {
        videoEl.pause();
        videoEl.removeAttribute('src');
        try { videoEl.load(); } catch (e) {}
      }
    }
    state.currentPlayingUrl = null;
  }

  // 2. Clear VOD Embed Iframe & Wrapper
  if (!keepVod) {
    const embedIframe = document.getElementById('embed-iframe');
    if (embedIframe) embedIframe.src = '';
    const embedWrapper = document.getElementById('embed-player-wrapper');
    if (embedWrapper) embedWrapper.style.display = 'none';
    const playerWrapper = document.querySelector('.player-wrapper');
    if (playerWrapper) playerWrapper.classList.remove('embed-active');
  }

  // 3. Stop PocketCasts Audio Player
  if (!keepPodcast) {
    if (typeof pocketcastsState !== 'undefined' && pocketcastsState) {
      if (pocketcastsState.audioElement) {
        try {
          pocketcastsState.audioElement.pause();
          pocketcastsState.audioElement.removeAttribute('src');
        } catch (e) {}
      }
      pocketcastsState.isPlaying = false;
      if (typeof updateDockPlayerUI === 'function') {
        updateDockPlayerUI();
      }
    }
  }

  // Hide player loading spinner overlay
  const loadingEl = document.getElementById('player-loading');
  if (loadingEl) loadingEl.classList.add('hidden');
}

function stopActiveLiveTVFeed() {
  stopAllMediaPlayback();
}

// Purge previous VOD/podcast state tokens and restore default Live TV player UI
function resetPlayerUiToDefaultLiveTv() {
  const tvSelectors = document.getElementById('tv-selectors');
  const sourcesPanel = document.querySelector('.sources-panel');
  const chPrevBtn = document.getElementById('ch-prev-btn');
  const chNextBtn = document.getElementById('ch-next-btn');
  const liveIndicatorDot = document.getElementById('live-indicator-dot');
  const liveIndicatorText = document.getElementById('live-indicator-text');
  const seekContainer = document.getElementById('seek-container');
  const videoControls = document.getElementById('video-controls');
  const embedWrapper = document.getElementById('embed-player-wrapper');
  const embedIframe = document.getElementById('embed-iframe');
  const playerSpinner = document.getElementById('player-spinner');
  const playerErr = document.getElementById('player-error');

  if (tvSelectors) tvSelectors.style.display = '';
  if (sourcesPanel) sourcesPanel.style.display = 'none';
  if (chPrevBtn) chPrevBtn.style.display = '';
  if (chNextBtn) chNextBtn.style.display = '';
  if (liveIndicatorDot) liveIndicatorDot.style.display = '';
  if (liveIndicatorText) liveIndicatorText.textContent = 'LIVE';
  if (seekContainer) seekContainer.style.display = 'none';
  if (videoControls) videoControls.style.display = '';
  if (playerSpinner) playerSpinner.style.display = 'none';
  if (playerErr) playerErr.classList.add('hidden');

  if (embedWrapper) embedWrapper.style.display = 'none';
  if (embedIframe) embedIframe.src = 'about:blank';

  if (player && typeof player.setControlMode === 'function') {
    player.setControlMode('live');
  }
}

// Switch between tabs
function switchTab(tabName) {
  state.activeTab = tabName;

  // Auto-hide podcast channel details overlay when switching tabs
  const podcastOverlay = document.getElementById('podcast-channel-modal');
  if (podcastOverlay) {
    podcastOverlay.classList.add('hidden');
    podcastOverlay.style.display = 'none';
  }
  document.body.style.overflow = '';

  // Clean up any playing media from previous context
  if (tabName !== 'podcasts') {
    stopAllMediaPlayback({ keepLive: tabName === 'live' });
  } else {
    stopAllMediaPlayback({ keepPodcast: true });
  }
  
  // Update buttons state
  document.querySelectorAll('.nav-links .nav-link, .mobile-drawer-link').forEach(btn => {
    if (btn.getAttribute('data-tab') === tabName) {
      btn.classList.add('active');
    } else {
      btn.classList.remove('active');
    }
  });

  // Hide all screens & show active screen
  document.querySelectorAll('.tab-screen').forEach(screen => {
    if (screen.id === `tab-${tabName}`) {
      screen.classList.remove('hidden');
    } else {
      screen.classList.add('hidden');
    }
  });

  // Shared player relocator
  const playerSection = document.getElementById('player-section');
  const liveContainer = document.getElementById('live-player-container');
  const vodContainer = document.getElementById('vod-player-container');
  const holder = document.getElementById('shared-player-holder');

  if (tabName === 'live') {
    // Purge carry-over podcast/VOD player state
    resetPlayerUiToDefaultLiveTv();

    // Append player to live TV panel
    if (liveContainer && playerSection) {
      liveContainer.appendChild(playerSection);
    }
    const username = (localStorage.getItem('iptv_username') || '').trim();
    const password = (localStorage.getItem('iptv_password') || '').trim();
    if (!username || !password) {
      renderUnactivatedState();
    } else {
      if (!state.channels || state.channels.length === 0) {
        console.log('[IPTV] Live TV tab opened but channels empty. Loading playlist...');
        loadIPTVPlaylist();
      } else {
        renderCategories();
        applyFilterAndRender();
      }
    }
  } else if (tabName === 'home') {
    const username = (localStorage.getItem('iptv_username') || '').trim();
    const password = (localStorage.getItem('iptv_password') || '').trim();
    if (!username || !password) {
      const homeStatusText = document.getElementById('home-status-text');
      if (homeStatusText) homeStatusText.textContent = 'Sign In Required';
    }
  } else {
    if (tabName === 'movies') {
      loadMoviesDashboard();
    } else if (tabName === 'series') {
      loadSeriesDashboard();
    } else if (tabName === 'podcasts') {
      loadPodcastsDashboard();
    } else if (tabName === 'library') {
      renderLibraryScreen();
    }

    if (state.selectedMedia === null && holder && playerSection) {
      holder.appendChild(playerSection);
    }
  }

  createIcons(iconConfig);
}

// Helper to verify if Live TV credentials or phone activation are active
function isLiveTvActive() {
  const activatedPhone = (localStorage.getItem('activated_phone') || '').trim();
  const username = (localStorage.getItem('iptv_username') || '').trim();
  const password = (localStorage.getItem('iptv_password') || '').trim();
  
  if (activatedPhone && activatedPhone.length >= 10) {
    return true;
  }
  
  if (username && password) {
    return true;
  }
  
  return false;
}

// Render locked access state view for VOD and Podcasts when Live TV credentials are inactive
function renderAccessLockedState(containerId, featureName) {
  const container = document.getElementById(containerId);
  if (!container) return;

  container.innerHTML = `
    <div class="col-span-full py-16 px-6 text-center bg-slate-900/90 rounded-2xl border border-amber-500/30 shadow-2xl max-w-lg mx-auto my-12 backdrop-blur-xl">
      <div class="w-20 h-20 mx-auto rounded-3xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center text-amber-400 mb-6 shadow-inner animate-pulse">
        <i data-lucide="lock" class="w-10 h-10"></i>
      </div>
      <span class="inline-block px-3 py-1 bg-amber-500/20 text-amber-400 text-xs font-bold rounded-full mb-3 border border-amber-500/40">
        ACTIVE SUBSCRIBER ACCESS ONLY
      </span>
      <h3 class="text-xl font-bold text-white mb-2">${featureName} Locked</h3>
      <p class="text-sm text-slate-300 mb-6 leading-relaxed">
        Access to ${featureName} requires active Live TV account credentials. Please sign in with your active credentials or 10-digit phone number in Settings to unlock.
      </p>
      <button class="lock-settings-btn btn btn-primary font-bold shadow-lg shadow-amber-900/30 flex items-center justify-center gap-2 mx-auto px-6 py-2.5 bg-gradient-to-r from-amber-500 to-orange-600 hover:from-amber-400 hover:to-orange-500 text-white rounded-xl transition-all">
        <i data-lucide="key" class="w-4 h-4"></i> Sign In in Settings to Unlock
      </button>
    </div>
  `;
  createIcons(iconConfig);

  const btn = container.querySelector('.lock-settings-btn');
  if (btn) {
    btn.addEventListener('click', () => {
      switchTab('settings');
    });
  }
}

// Load Movies Dashboard hero + curated category carousels
async function loadMoviesDashboard() {
  if (!isLiveTvActive()) {
    renderAccessLockedState('tab-movies', 'Movies & VOD');
    return;
  }
  const trendingRow = document.getElementById('row-trending-movies');
  const topRatedRow = document.getElementById('row-top-rated-movies');
  const actionRow = document.getElementById('row-action-movies');
  const comedyRow = document.getElementById('row-comedy-movies');
  const scifiRow = document.getElementById('row-scifi-movies');
  const horrorRow = document.getElementById('row-horror-movies');
  const animationRow = document.getElementById('row-animation-movies');
  const watchlistRow = document.getElementById('row-movies-watchlist');
  const watchlistSection = document.getElementById('row-movies-watchlist-section');

  // Load Movies Watchlist
  const movieWatchlist = state.watchlist.filter(x => x.media_type === 'movie');
  if (watchlistSection && watchlistRow) {
    if (movieWatchlist.length > 0) {
      watchlistSection.style.display = 'block';
      renderCardRow(movieWatchlist, watchlistRow);
    } else {
      watchlistSection.style.display = 'none';
    }
  }

  try {
    const data = await getTrendingMovies();
    const movies = data.results || [];
    
    if (movies.length > 0) {
      const heroItem = movies[0];
      const title = heroItem.title || 'Featured Movie';
      const year = (heroItem.release_date || '').split('-')[0] || '2026';
      const rating = heroItem.vote_average ? heroItem.vote_average.toFixed(1) : 'N/A';
      const overview = heroItem.overview || 'Explore details and stream this trending movie instantly.';
      const bgUrl = getTMDBImageUrl(heroItem.backdrop_path, 'original') || getTMDBImageUrl(heroItem.poster_path, 'original');

      const titleEl = document.getElementById('movies-hero-title');
      if (titleEl) titleEl.textContent = title;
      const yearEl = document.getElementById('movies-hero-year');
      if (yearEl) yearEl.textContent = year;
      const ratingEl = document.getElementById('movies-hero-rating');
      if (ratingEl) ratingEl.textContent = `⭐ ${rating}`;
      const overviewEl = document.getElementById('movies-hero-overview');
      if (overviewEl) overviewEl.textContent = overview;
      
      const backdropEl = document.getElementById('movies-hero-backdrop');
      if (backdropEl && bgUrl) {
        backdropEl.style.backgroundImage = `url(${bgUrl})`;
      }

      const playBtn = document.getElementById('movies-hero-play-btn');
      if (playBtn) {
        const newPlayBtn = playBtn.cloneNode(true);
        playBtn.parentNode.replaceChild(newPlayBtn, playBtn);
        newPlayBtn.addEventListener('click', () => openDetailsView(heroItem));
      }
    }

    if (trendingRow) renderCardRow(movies, trendingRow);

    // Fetch movie curation category rows concurrently
    Promise.allSettled([
      getTopRated().then(res => topRatedRow && renderCardRow(res.results, topRatedRow)),
      getByGenre(28, 'movie').then(res => actionRow && renderCardRow(res.results, actionRow)),
      getByGenre(35, 'movie').then(res => comedyRow && renderCardRow(res.results, comedyRow)),
      getByGenre(878, 'movie').then(res => scifiRow && renderCardRow(res.results, scifiRow)),
      getByGenre(27, 'movie').then(res => horrorRow && renderCardRow(res.results, horrorRow)),
      getByGenre(16, 'movie').then(res => animationRow && renderCardRow(res.results, animationRow))
    ]);

  } catch (err) {
    console.error("Movies dashboard load error:", err);
    if (trendingRow) trendingRow.innerHTML = '<span class="text-xs text-slate-500 py-4">Failed to load trending movies.</span>';
  }
}

// Load TV Series Dashboard hero + curated category carousels
async function loadSeriesDashboard() {
  if (!isLiveTvActive()) {
    renderAccessLockedState('tab-series', 'TV Series & VOD');
    return;
  }
  const trendingRow = document.getElementById('row-trending-tv');
  const topRatedRow = document.getElementById('row-top-rated-series');
  const actionRow = document.getElementById('row-action-series');
  const comedyRow = document.getElementById('row-comedy-series');
  const scifiRow = document.getElementById('row-scifi-series');
  const crimeRow = document.getElementById('row-crime-series');
  const animationRow = document.getElementById('row-animation-series');
  const watchlistRow = document.getElementById('row-series-watchlist');
  const watchlistSection = document.getElementById('row-series-watchlist-section');

  // Load TV Series Watchlist
  const seriesWatchlist = state.watchlist.filter(x => x.media_type === 'tv');
  if (watchlistSection && watchlistRow) {
    if (seriesWatchlist.length > 0) {
      watchlistSection.style.display = 'block';
      renderCardRow(seriesWatchlist, watchlistRow);
    } else {
      watchlistSection.style.display = 'none';
    }
  }

  try {
    const data = await getTrendingTV();
    const series = data.results || [];
    
    if (series.length > 0) {
      const heroItem = series[0];
      const title = heroItem.name || 'Featured Series';
      const year = (heroItem.first_air_date || '').split('-')[0] || '2026';
      const rating = heroItem.vote_average ? heroItem.vote_average.toFixed(1) : 'N/A';
      const overview = heroItem.overview || 'Explore details and stream this trending series instantly.';
      const bgUrl = getTMDBImageUrl(heroItem.backdrop_path, 'original') || getTMDBImageUrl(heroItem.poster_path, 'original');

      const titleEl = document.getElementById('series-hero-title');
      if (titleEl) titleEl.textContent = title;
      const yearEl = document.getElementById('series-hero-year');
      if (yearEl) yearEl.textContent = year;
      const ratingEl = document.getElementById('series-hero-rating');
      if (ratingEl) ratingEl.textContent = `⭐ ${rating}`;
      const overviewEl = document.getElementById('series-hero-overview');
      if (overviewEl) overviewEl.textContent = overview;
      
      const backdropEl = document.getElementById('series-hero-backdrop');
      if (backdropEl && bgUrl) {
        backdropEl.style.backgroundImage = `url(${bgUrl})`;
      }

      const playBtn = document.getElementById('series-hero-play-btn');
      if (playBtn) {
        const newPlayBtn = playBtn.cloneNode(true);
        playBtn.parentNode.replaceChild(newPlayBtn, playBtn);
        newPlayBtn.addEventListener('click', () => openDetailsView(heroItem));
      }
    }

    if (trendingRow) renderCardRow(series, trendingRow);

    // Fetch series curation category rows concurrently
    Promise.allSettled([
      getTopRatedTV().then(res => topRatedRow && renderCardRow(res.results, topRatedRow)),
      getByGenre(10759, 'tv').then(res => actionRow && renderCardRow(res.results, actionRow)),
      getByGenre(35, 'tv').then(res => comedyRow && renderCardRow(res.results, comedyRow)),
      getByGenre(10765, 'tv').then(res => scifiRow && renderCardRow(res.results, scifiRow)),
      getByGenre(80, 'tv').then(res => crimeRow && renderCardRow(res.results, crimeRow)),
      getByGenre(16, 'tv').then(res => animationRow && renderCardRow(res.results, animationRow))
    ]);

  } catch (err) {
    console.error("Series dashboard load error:", err);
    if (trendingRow) trendingRow.innerHTML = '<span class="text-xs text-slate-500 py-4">Failed to load trending series.</span>';
  }
}

// Global state for active podcast channel overlay
let activePodcastModalChannel = null;
let activePodcastAllEpisodes = [];
let activePodcastContinuationToken = '';
let activePodcastHasMore = true;
let podcastEpisodesSearchQuery = '';
let podcastEpisodesSortMode = 'newest';
let podcastEpisodesPage = 1;
const PODCAST_EPISODES_PER_PAGE = 9;

// POCKET CASTS REDESIGN STATE & APP CONTROLLER (Moved to top)
// ==========================================================================

// Main Entry Point called when user switches to Podcasts tab
function loadPodcastsDashboard() {
  if (!isLiveTvActive()) {
    renderAccessLockedState('tab-podcasts', 'Podcasts');
    return;
  }
  if (!pocketcastsState.navInitialized) {
    initPocketCastsNav();
    pocketcastsState.navInitialized = true;
  }
  renderPocketCastsView();
  updatePocketCastsBadges();
}

function initPocketCastsNav() {
  // Navigation tabs
  const navBtns = document.querySelectorAll('.podcast-nav-btn');
  navBtns.forEach(btn => {
    btn.onclick = () => {
      const view = btn.getAttribute('data-view');
      pocketcastsState.currentView = view;
      navBtns.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      renderPocketCastsView();
    };
  });

  // Category filter pills
  const catPills = document.querySelectorAll('.podcast-cat-pill');
  catPills.forEach(pill => {
    pill.onclick = () => {
      catPills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      pocketcastsState.activeCategory = pill.getAttribute('data-category');
      renderPocketCastsDiscover();
    };
  });

  // Live search input
  const searchInput = document.getElementById('podcasts-search-input');
  const clearBtn = document.getElementById('podcasts-search-clear');
  const closeSearchBtn = document.getElementById('podcasts-search-close-btn');

  if (searchInput) {
    let searchDebounceTimer;
    searchInput.oninput = (e) => {
      const query = e.target.value.trim();
      if (clearBtn) {
        if (query) clearBtn.classList.remove('hidden');
        else clearBtn.classList.add('hidden');
      }

      clearTimeout(searchDebounceTimer);
      if (!query) {
        hidePodcastSearchResults();
        return;
      }

      searchDebounceTimer = setTimeout(() => {
        handlePocketCastsSearch(query);
      }, 350);
    };
  }

  if (clearBtn) {
    clearBtn.onclick = () => {
      if (searchInput) searchInput.value = '';
      clearBtn.classList.add('hidden');
      hidePodcastSearchResults();
    };
  }

  if (closeSearchBtn) {
    closeSearchBtn.onclick = () => {
      if (searchInput) searchInput.value = '';
      if (clearBtn) clearBtn.classList.add('hidden');
      hidePodcastSearchResults();
    };
  }

  // Pocket Casts Dock Mini Player controls
  initPocketCastsDockControls();

  // Clear queue button
  const clearQueueBtn = document.getElementById('podcast-clear-queue-btn');
  if (clearQueueBtn) {
    clearQueueBtn.onclick = () => {
      pocketcastsState.queue = [];
      localStorage.setItem('pocketcasts_queue', JSON.stringify([]));
      renderPocketCastsQueue();
      updatePocketCastsBadges();
    };
  }

  // Clear history button
  const clearHistoryBtn = document.getElementById('podcast-clear-history-btn');
  if (clearHistoryBtn) {
    clearHistoryBtn.onclick = () => {
      pocketcastsState.history = [];
      localStorage.setItem('pocketcasts_history', JSON.stringify([]));
      renderPocketCastsHistory();
    };
  }

  // Hero Spotlight setup
  const heroChannel = PODCAST_CHANNELS.tech[0];
  const heroPlayBtn = document.getElementById('podcast-hero-play-btn');
  const heroShowBtn = document.getElementById('podcast-hero-show-btn');

  if (heroChannel && heroChannel.episodes && heroChannel.episodes[0]) {
    const heroEp = heroChannel.episodes[0];
    const heroBg = document.getElementById('podcast-hero-bg');
    const heroTitle = document.getElementById('podcast-hero-title');
    const heroShow = document.getElementById('podcast-hero-show');
    const heroDesc = document.getElementById('podcast-hero-desc');

    if (heroBg) heroBg.style.backgroundImage = `url(${heroEp.thumbnail || heroChannel.avatar})`;
    if (heroTitle) heroTitle.textContent = heroEp.title;
    if (heroShow) heroShow.textContent = `${heroChannel.channelName} • Hosted by ${heroChannel.host}`;
    if (heroDesc) heroDesc.textContent = heroEp.description || heroChannel.description;

    if (heroPlayBtn) {
      heroPlayBtn.onclick = () => playPodcastEpisodeInDock(heroEp, heroChannel);
    }
  }

  if (heroShowBtn && heroChannel) {
    heroShowBtn.onclick = () => openPodcastChannelModal(heroChannel);
  }

  // Fetch top iTunes podcasts to populate discover directory
  loadTopItunesPodcasts().then(() => {
    if (pocketcastsState.currentView === 'discover') renderPocketCastsDiscover();
  });
}

function updatePocketCastsBadges() {
  const subBadge = document.getElementById('podcast-subscribed-badge');
  const queueBadge = document.getElementById('podcast-queue-badge');

  if (subBadge) {
    const count = pocketcastsState.subscribedShowIds.length;
    subBadge.textContent = count;
    if (count > 0) subBadge.classList.remove('hidden');
    else subBadge.classList.add('hidden');
  }

  if (queueBadge) {
    const count = pocketcastsState.queue.length;
    queueBadge.textContent = count;
    if (count > 0) queueBadge.classList.remove('hidden');
    else queueBadge.classList.add('hidden');
  }
}

function hidePodcastSearchResults() {
  const searchResultsSection = document.getElementById('podcasts-search-results-section');
  const discoverView = document.getElementById('podcast-view-discover');
  if (searchResultsSection) searchResultsSection.classList.add('hidden');
  if (discoverView) discoverView.classList.remove('hidden');
}

function renderPocketCastsView() {
  const views = {
    discover: document.getElementById('podcast-view-discover'),
    subscribed: document.getElementById('podcast-view-subscribed'),
    queue: document.getElementById('podcast-view-queue'),
    history: document.getElementById('podcast-view-history')
  };

  const searchResultsSection = document.getElementById('podcasts-search-results-section');
  if (searchResultsSection) searchResultsSection.classList.add('hidden');

  Object.keys(views).forEach(vKey => {
    if (views[vKey]) {
      if (vKey === pocketcastsState.currentView) {
        views[vKey].classList.remove('hidden');
      } else {
        views[vKey].classList.add('hidden');
      }
    }
  });

  if (pocketcastsState.currentView === 'discover') renderPocketCastsDiscover();
  else if (pocketcastsState.currentView === 'subscribed') renderPocketCastsSubscribed();
  else if (pocketcastsState.currentView === 'queue') renderPocketCastsQueue();
  else if (pocketcastsState.currentView === 'history') renderPocketCastsHistory();

  createIcons(iconConfig);
}

function renderPocketCastsDiscover() {
  const grid = document.getElementById('podcast-discover-grid');
  if (!grid) return;

  const allChannels = getAllPodcastChannels();
  let channelsToDisplay = allChannels;

  if (pocketcastsState.activeCategory && pocketcastsState.activeCategory !== 'all') {
    channelsToDisplay = allChannels.filter(c => 
      c.category && c.category.toLowerCase().includes(pocketcastsState.activeCategory.toLowerCase().split(',')[0].trim())
    );
  }

  grid.innerHTML = '';
  channelsToDisplay.forEach(channel => {
    grid.appendChild(createPocketCastsShowCard(channel));
  });
  renderForYouPodcasts();
  createIcons(iconConfig);
}

function renderPocketCastsSubscribed() {
  const grid = document.getElementById('podcast-subscribed-grid');
  const emptyCard = document.getElementById('podcast-subscribed-empty');
  const countText = document.getElementById('podcast-subscribed-count-text');

  if (!grid) return;

  // Combine curated channels with all dynamically saved subscribed show objects
  const allCurated = getAllPodcastChannels();
  const showMap = new Map();

  allCurated.forEach(c => { if (c && c.id) showMap.set(c.id, c); });
  (pocketcastsState.subscribedShows || []).forEach(c => { if (c && c.id) showMap.set(c.id, c); });

  const subscribedChannels = pocketcastsState.subscribedShowIds
    .map(id => showMap.get(id))
    .filter(Boolean);

  // Synchronize stored IDs with valid mapped objects so badge count and grid always match 1:1
  pocketcastsState.subscribedShowIds = subscribedChannels.map(c => c.id);
  localStorage.setItem('pocketcasts_subscribed_ids', JSON.stringify(pocketcastsState.subscribedShowIds));
  
  const subBadge = document.getElementById('podcast-subscribed-badge');
  if (subBadge) {
    subBadge.textContent = subscribedChannels.length;
    if (subscribedChannels.length > 0) subBadge.classList.remove('hidden');
    else subBadge.classList.add('hidden');
  }

  if (countText) countText.textContent = `${subscribedChannels.length} Shows`;

  if (subscribedChannels.length === 0) {
    grid.innerHTML = '';
    if (emptyCard) emptyCard.classList.remove('hidden');
    const goDiscoverBtn = document.getElementById('podcast-go-discover-btn');
    if (goDiscoverBtn) {
      goDiscoverBtn.onclick = () => {
        const discoverNav = document.getElementById('podcast-nav-discover');
        if (discoverNav) discoverNav.click();
      };
    }
    return;
  }

  if (emptyCard) emptyCard.classList.add('hidden');
  grid.innerHTML = '';
  subscribedChannels.forEach(channel => {
    grid.appendChild(createPocketCastsShowCard(channel, true));
  });
  createIcons(iconConfig);
}

function createPocketCastsShowCard(channel, isSubscribedView = false) {
  const card = document.createElement('div');
  card.className = 'podcast-show-card';
  const isSubscribed = pocketcastsState.subscribedShowIds.includes(channel.id);
  const epCount = channel.episodes ? channel.episodes.length : 12;
  const avatarUrl = channel.avatar || channel.thumbnail || 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80';

  card.innerHTML = `
    <div class="podcast-show-thumb-wrap">
      <img src="${avatarUrl}" alt="${channel.channelName}" class="podcast-show-thumb" loading="lazy" onerror="this.onerror=null; this.src='https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80';">
      ${isSubscribed ? `<span class="podcast-show-unplayed-badge">${epCount} eps</span>` : ''}
    </div>
    <div class="podcast-show-info">
      <h4 class="podcast-show-title" title="${channel.channelName}">${channel.channelName}</h4>
      <p class="podcast-show-author">${channel.host || 'Host'} • ${channel.category || 'Podcast'}</p>
      <button class="podcast-show-sub-btn ${isSubscribed ? 'is-subscribed' : ''}">
        <i data-lucide="${isSubscribed ? 'check' : 'plus'}" class="w-3.5 h-3.5"></i>
        <span>${isSubscribed ? 'Subscribed' : 'Subscribe'}</span>
      </button>
    </div>
  `;

  card.addEventListener('click', (e) => {
    if (e.target.closest('.podcast-show-sub-btn')) return;
    openPodcastChannelModal(channel);
  });

  const subBtn = card.querySelector('.podcast-show-sub-btn');
  if (subBtn) {
    subBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      togglePodcastSubscription(channel);
    });
  }

  return card;
}

// Memory cache for "For You" smart query results
let forYouCache = {
  key: '',
  items: []
};

// Render freshly curated podcasts in "For You" section via live smart query
async function renderForYouPodcasts() {
  const section = document.getElementById('podcasts-for-you-section');
  const grid = document.getElementById('podcasts-for-you-grid');
  if (!section || !grid) return;

  section.classList.remove('hidden');

  const subscribedShows = pocketcastsState.subscribedShows || [];
  const activeCat = pocketcastsState.activeCategory || 'all';

  const cacheKey = `${activeCat}_${subscribedShows.map(s => s.id).sort().join(',')}`;

  if (forYouCache.key === cacheKey && forYouCache.items.length > 0) {
    grid.innerHTML = '';
    forYouCache.items.forEach(channel => {
      grid.appendChild(createPocketCastsShowCard(channel));
    });
    createIcons(iconConfig);
    return;
  }

  // Render shimmer loading skeleton
  grid.innerHTML = Array(6).fill(0).map(() => `
    <div class="podcast-show-card animate-pulse">
      <div class="podcast-show-thumb-wrap bg-slate-800/60 rounded-xl h-44 w-full"></div>
      <div class="podcast-show-info space-y-2 mt-3">
        <div class="h-4 bg-slate-800/80 rounded w-3/4"></div>
        <div class="h-3 bg-slate-800/50 rounded w-1/2"></div>
      </div>
    </div>
  `).join('');

  try {
    const freshRecommended = await fetchForYouCuratedPodcasts(subscribedShows, activeCat);
    if (!freshRecommended || freshRecommended.length === 0) {
      if (subscribedShows.length === 0) {
        section.classList.add('hidden');
      }
      grid.innerHTML = '';
      return;
    }

    forYouCache = {
      key: cacheKey,
      items: freshRecommended
    };

    grid.innerHTML = '';
    freshRecommended.forEach(channel => {
      grid.appendChild(createPocketCastsShowCard(channel));
    });
    createIcons(iconConfig);
  } catch (err) {
    console.warn('[ForYou] Failed to fetch smart query recommendations:', err);
    if (subscribedShows.length === 0) {
      section.classList.add('hidden');
    }
  }
}


function togglePodcastSubscription(channel) {
  if (!channel || !channel.id) return;
  const idx = pocketcastsState.subscribedShowIds.indexOf(channel.id);
  if (idx >= 0) {
    pocketcastsState.subscribedShowIds.splice(idx, 1);
    pocketcastsState.subscribedShows = (pocketcastsState.subscribedShows || []).filter(s => s.id !== channel.id);
  } else {
    pocketcastsState.subscribedShowIds.push(channel.id);
    if (!pocketcastsState.subscribedShows) pocketcastsState.subscribedShows = [];
    if (!pocketcastsState.subscribedShows.some(s => s.id === channel.id)) {
      pocketcastsState.subscribedShows.push(channel);
    }
  }
  localStorage.setItem('pocketcasts_subscribed_ids', JSON.stringify(pocketcastsState.subscribedShowIds));
  localStorage.setItem('pocketcasts_subscribed_shows', JSON.stringify(pocketcastsState.subscribedShows));
  updatePocketCastsBadges();
  renderForYouPodcasts();
  if (pocketcastsState.currentView === 'subscribed') renderPocketCastsSubscribed();
  else if (pocketcastsState.currentView === 'discover') renderPocketCastsDiscover();
}

async function handlePocketCastsSearch(query) {
  const searchResultsSection = document.getElementById('podcasts-search-results-section');
  const discoverView = document.getElementById('podcast-view-discover');
  const showsGrid = document.getElementById('podcasts-search-shows-grid');
  const statusHeading = document.getElementById('podcasts-search-status-heading');
  const emptyState = document.getElementById('podcasts-search-empty-state');
  const epSection = document.getElementById('podcasts-search-episodes-section');
  const epList = document.getElementById('podcasts-search-episodes-list');

  if (!searchResultsSection) return;

  searchResultsSection.classList.remove('hidden');
  if (discoverView) discoverView.classList.add('hidden');
  if (statusHeading) statusHeading.textContent = `Searching podcasts for "${query}"...`;
  if (emptyState) emptyState.classList.add('hidden');

  if (showsGrid) {
    showsGrid.innerHTML = `
      <div class="col-span-full flex flex-col items-center justify-center py-12 gap-3 text-slate-400">
        <div class="w-7 h-7 border-2 border-red-500 border-t-transparent rounded-full animate-spin"></div>
        <span class="text-xs font-semibold text-slate-300">Searching global Apple & YouTube podcast database...</span>
      </div>
    `;
  }

  try {
    const results = await searchRealPodcastAPI(query);
    const { curatedChannels = [], realChannels = [], matchingEpisodes = [] } = results;
    const allShows = [...curatedChannels, ...realChannels];

    if (allShows.length === 0 && matchingEpisodes.length === 0) {
      if (showsGrid) showsGrid.innerHTML = '';
      if (epSection) epSection.classList.add('hidden');
      if (emptyState) emptyState.classList.remove('hidden');
      if (statusHeading) statusHeading.textContent = `No podcasts found for "${query}"`;
      return;
    }

    if (emptyState) emptyState.classList.add('hidden');
    if (statusHeading) statusHeading.textContent = `Search Results for "${query}" (${allShows.length} shows found)`;

    if (showsGrid) {
      showsGrid.innerHTML = '';
      allShows.forEach(show => {
        showsGrid.appendChild(createPocketCastsShowCard(show));
      });
    }

    if (matchingEpisodes.length > 0 && epList && epSection) {
      epSection.classList.remove('hidden');
      epList.innerHTML = '';
      matchingEpisodes.forEach(ep => {
        const epRow = document.createElement('div');
        epRow.className = 'flex items-center justify-between p-3 rounded-xl bg-slate-900/80 border border-slate-800 hover:border-slate-700 transition-all gap-3';
        epRow.innerHTML = `
          <div class="flex items-center gap-3 overflow-hidden">
            <img src="${ep.thumbnail || ep.channelAvatar || ''}" class="w-10 h-10 rounded-lg object-cover flex-shrink-0" onerror="this.src='https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80';">
            <div class="overflow-hidden">
              <h4 class="text-xs font-bold text-white truncate">${ep.title}</h4>
              <p class="text-[11px] text-red-400 font-semibold truncate flex items-center gap-1.5"><span>${ep.channelName || 'Podcast Episode'}</span> <span class="text-slate-400 font-normal">• 📅 ${ep.date || ep.year || 'Recent'}</span></p>
            </div>
          </div>
          <button class="flex-shrink-0 px-3 py-1.5 rounded-lg bg-red-600 hover:bg-red-500 text-white font-bold text-xs flex items-center gap-1">
            <i data-lucide="play" class="w-3.5 h-3.5 fill-white"></i> Play
          </button>
        `;
        epRow.querySelector('button').onclick = () => playPodcastEpisodeInDock(ep);
        epList.appendChild(epRow);
      });
    } else if (epSection) {
      epSection.classList.add('hidden');
    }
  } catch (err) {
    console.error('Podcast search error:', err);
  }
  createIcons(iconConfig);
}

function addToPodcastQueue(episode) {
  const epId = episode.id || episode.youtubeId;
  if (!pocketcastsState.queue.some(e => (e.id || e.youtubeId) === epId)) {
    pocketcastsState.queue.push(episode);
    localStorage.setItem('pocketcasts_queue', JSON.stringify(pocketcastsState.queue));
    updatePocketCastsBadges();
    if (player && typeof player.showToast === 'function') {
      player.showToast(`Added to Up Next Queue: ${episode.title.slice(0, 30)}...`);
    }
    if (pocketcastsState.currentView === 'queue') renderPocketCastsQueue();
  }
}

function removeFromPodcastQueue(epId) {
  pocketcastsState.queue = pocketcastsState.queue.filter(e => (e.id || e.youtubeId) !== epId);
  localStorage.setItem('pocketcasts_queue', JSON.stringify(pocketcastsState.queue));
  updatePocketCastsBadges();
  renderPocketCastsQueue();
}

function renderPocketCastsQueue() {
  const queueList = document.getElementById('podcast-queue-list');
  const emptyCard = document.getElementById('podcast-queue-empty');
  const countText = document.getElementById('podcast-queue-count-text');

  if (!queueList) return;

  if (countText) countText.textContent = `${pocketcastsState.queue.length} Episodes`;

  if (pocketcastsState.queue.length === 0) {
    queueList.innerHTML = '';
    if (emptyCard) emptyCard.classList.remove('hidden');
    return;
  }

  if (emptyCard) emptyCard.classList.add('hidden');
  queueList.innerHTML = '';

  pocketcastsState.queue.forEach((ep, idx) => {
    const epId = ep.id || ep.youtubeId;
    const row = document.createElement('div');
    row.className = 'flex items-center justify-between p-3 rounded-xl bg-slate-900/90 border border-slate-800 hover:border-slate-700 transition-all gap-3';
    row.innerHTML = `
      <div class="flex items-center gap-3 min-w-0">
        <span class="text-xs font-bold text-slate-500 w-4 text-center">${idx + 1}</span>
        <img src="${ep.thumbnail || ep.avatar || ''}" class="w-11 h-11 rounded-lg object-cover flex-shrink-0" onerror="this.src='https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80';">
        <div class="min-w-0">
          <h4 class="text-xs font-bold text-white truncate">${ep.title}</h4>
          <p class="text-[11px] text-red-400 font-semibold truncate">${ep.channelName || 'Podcast'} • ${ep.duration || 'Audio'}</p>
        </div>
      </div>
      <div class="flex items-center gap-2 flex-shrink-0">
        <button class="queue-play-btn p-2 rounded-lg bg-red-600 hover:bg-red-500 text-white font-bold text-xs" title="Play Now">
          <i data-lucide="play" class="w-4 h-4 fill-white"></i>
        </button>
        <button class="queue-remove-btn p-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-400 hover:text-red-400 text-xs" title="Remove from Queue">
          <i data-lucide="trash-2" class="w-4 h-4"></i>
        </button>
      </div>
    `;

    row.querySelector('.queue-play-btn').onclick = () => playPodcastEpisodeInDock(ep);
    row.querySelector('.queue-remove-btn').onclick = () => removeFromPodcastQueue(epId);

    queueList.appendChild(row);
  });
  createIcons(iconConfig);
}

function renderPocketCastsHistory() {
  const historyList = document.getElementById('podcast-history-list');
  const emptyCard = document.getElementById('podcast-history-empty');

  if (!historyList) return;

  if (pocketcastsState.history.length === 0) {
    historyList.innerHTML = '';
    if (emptyCard) emptyCard.classList.remove('hidden');
    return;
  }

  if (emptyCard) emptyCard.classList.add('hidden');
  historyList.innerHTML = '';

  pocketcastsState.history.forEach((item) => {
    const ep = item.episode || item;
    const row = document.createElement('div');
    row.className = 'flex items-center justify-between p-3 rounded-xl bg-slate-900/90 border border-slate-800 hover:border-slate-700 transition-all gap-3';
    row.innerHTML = `
      <div class="flex items-center gap-3 min-w-0">
        <img src="${ep.thumbnail || ep.avatar || ''}" class="w-11 h-11 rounded-lg object-cover flex-shrink-0" onerror="this.src='https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80';">
        <div class="min-w-0">
          <h4 class="text-xs font-bold text-white truncate">${ep.title}</h4>
          <p class="text-[11px] text-slate-400 font-medium truncate">${ep.channelName || 'Podcast'} • ${item.dateStr || 'Recently Played'}</p>
        </div>
      </div>
      <button class="history-replay-btn px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-red-600 text-white font-bold text-xs flex items-center gap-1.5 transition-all">
        <i data-lucide="play" class="w-3.5 h-3.5 fill-white"></i> Play
      </button>
    `;

    row.querySelector('.history-replay-btn').onclick = () => playPodcastEpisodeInDock(ep);
    historyList.appendChild(row);
  });
  createIcons(iconConfig);
}

async function playPodcastEpisodeInDock(episode, showInfo = null) {
  pocketcastsState.currentEpisode = episode;

  // Add to history + mark as played
  const epId = episode.id || episode.youtubeId;
  if (epId) {
    if (!podcastPlayedEpisodes.includes(epId)) {
      podcastPlayedEpisodes.push(epId);
      localStorage.setItem('podcast_played_episodes', JSON.stringify(podcastPlayedEpisodes));
    }
  }
  const historyItem = { episode, dateStr: new Date().toLocaleDateString() };
  pocketcastsState.history = [historyItem, ...pocketcastsState.history.filter(h => (h.episode?.id || h.episode?.youtubeId) !== (episode.id || episode.youtubeId))].slice(0, 30);
  localStorage.setItem('pocketcasts_history', JSON.stringify(pocketcastsState.history));

  // Dynamically resolve MP3 audio URL from Apple Podcasts if missing
  if (!episode.audioUrl && (episode.title || episode.channelName)) {
    try {
      const cleanTitle = (episode.title || '')
        .replace(/^#?\d+[\s–—-]*/, '')
        .replace(/^E\d+:?\s*/i, '')
        .replace(/#\d+/g, '')
        .trim();
      const searchTerm = encodeURIComponent(`${episode.channelName || ''} ${cleanTitle}`.trim());
      const res = await fetch(`https://itunes.apple.com/search?media=podcast&entity=podcastEpisode&term=${searchTerm}&limit=1`);
      if (res.ok) {
        const data = await res.json();
        if (data.results && data.results[0] && (data.results[0].episodeUrl || data.results[0].previewUrl)) {
          episode.audioUrl = data.results[0].episodeUrl || data.results[0].previewUrl;
          if (data.results[0].artworkUrl600) episode.thumbnail = data.results[0].artworkUrl600;
        }
      }
    } catch (e) {
      console.warn('[Podcasts] Dock audio stream resolution error:', e);
    }
  }

  if (episode.audioUrl) {
    if (!pocketcastsState.audioElement) {
      pocketcastsState.audioElement = new Audio();
    }
    const audio = pocketcastsState.audioElement;
    audio.src = episode.audioUrl;
    audio.playbackRate = pocketcastsState.playbackSpeed;
    audio.play().then(() => {
      pocketcastsState.isPlaying = true;
      updateDockPlayerUI();
    }).catch(e => console.warn('Audio autoplay handled:', e));

    audio.ontimeupdate = () => {
      const cur = audio.currentTime;
      const dur = audio.duration || 1;
      const pct = (cur / dur) * 100;
      const progressBar = document.getElementById('dock-progress-bar');
      const timeCur = document.getElementById('dock-time-current');
      const timeDur = document.getElementById('dock-time-duration');
      if (progressBar) progressBar.value = pct;
      if (timeCur) timeCur.textContent = formatTime(cur);
      if (timeDur) timeDur.textContent = formatTime(dur);
    };

    audio.onended = () => {
      pocketcastsState.isPlaying = false;
      updateDockPlayerUI();
      if (pocketcastsState.queue.length > 0) {
        const nextEp = pocketcastsState.queue.shift();
        localStorage.setItem('pocketcasts_queue', JSON.stringify(pocketcastsState.queue));
        updatePocketCastsBadges();
        playPodcastEpisodeInDock(nextEp);
      }
    };
  } else {
    openPodcastModal(episode);
  }

  updateDockPlayerUI();
}

function formatTime(seconds) {
  if (isNaN(seconds)) return '00:00';
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  const h = Math.floor(m / 60);
  if (h > 0) {
    const remM = m % 60;
    return `${h}:${remM < 10 ? '0' : ''}${remM}:${s < 10 ? '0' : ''}${s}`;
  }
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

function updateDockPlayerUI() {
  const dock = document.getElementById('pocketcasts-player-dock');
  const thumb = document.getElementById('dock-thumb');
  const title = document.getElementById('dock-title');
  const show = document.getElementById('dock-show');
  const playBtn = document.getElementById('dock-play-pause');

  if (!dock) return;

  const ep = pocketcastsState.currentEpisode;
  if (!ep) {
    dock.classList.add('hidden');
    return;
  }

  dock.classList.remove('hidden');
  if (thumb) thumb.src = ep.thumbnail || ep.avatar || 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80';
  if (title) title.textContent = ep.title;
  if (show) show.textContent = ep.channelName || 'Podcast Episode';

  if (playBtn) {
    if (pocketcastsState.isPlaying) {
      playBtn.innerHTML = `<i data-lucide="pause" class="w-5 h-5 fill-white"></i>`;
    } else {
      playBtn.innerHTML = `<i data-lucide="play" class="w-5 h-5 fill-white"></i>`;
    }
    createIcons(iconConfig);
  }
}

function initPocketCastsDockControls() {
  const playBtn = document.getElementById('dock-play-pause');
  const skipBackBtn = document.getElementById('dock-skip-back');
  const skipFwdBtn = document.getElementById('dock-skip-fwd');
  const speedBtn = document.getElementById('dock-speed-btn');
  const queueBtn = document.getElementById('dock-queue-btn');
  const expandBtn = document.getElementById('dock-expand-btn');
  const progressBar = document.getElementById('dock-progress-bar');

  if (playBtn) {
    playBtn.onclick = () => {
      const audio = pocketcastsState.audioElement;
      if (!audio) return;
      if (pocketcastsState.isPlaying) {
        audio.pause();
        pocketcastsState.isPlaying = false;
      } else {
        audio.play();
        pocketcastsState.isPlaying = true;
      }
      updateDockPlayerUI();
    };
  }

  if (skipBackBtn) {
    skipBackBtn.onclick = () => {
      const audio = pocketcastsState.audioElement;
      if (audio) audio.currentTime = Math.max(0, audio.currentTime - 10);
    };
  }

  if (skipFwdBtn) {
    skipFwdBtn.onclick = () => {
      const audio = pocketcastsState.audioElement;
      if (audio) audio.currentTime = Math.min(audio.duration || 0, audio.currentTime + 30);
    };
  }

  const speeds = [1.0, 1.25, 1.5, 2.0];
  if (speedBtn) {
    speedBtn.onclick = () => {
      const curIdx = speeds.indexOf(pocketcastsState.playbackSpeed);
      const nextIdx = (curIdx + 1) % speeds.length;
      pocketcastsState.playbackSpeed = speeds[nextIdx];
      speedBtn.textContent = `${pocketcastsState.playbackSpeed}x`;
      if (pocketcastsState.audioElement) {
        pocketcastsState.audioElement.playbackRate = pocketcastsState.playbackSpeed;
      }
    };
  }

  if (queueBtn) {
    queueBtn.onclick = () => {
      const navQueue = document.getElementById('podcast-nav-queue');
      if (navQueue) navQueue.click();
    };
  }

  if (expandBtn) {
    expandBtn.onclick = () => {
      if (pocketcastsState.currentEpisode) openPodcastModal(pocketcastsState.currentEpisode);
    };
  }

  if (progressBar) {
    progressBar.oninput = (e) => {
      const audio = pocketcastsState.audioElement;
      if (audio && audio.duration) {
        const targetSec = (e.target.value / 100) * audio.duration;
        audio.currentTime = targetSec;
      }
    };
  }
}

// Global state for Podcast Detail Modal & Episode filtering
let podcastActiveTab = 'all'; // 'all', 'unplayed', 'saved'
let podcastPlayedEpisodes = JSON.parse(localStorage.getItem('podcast_played_episodes') || '[]');
let podcastSavedEpisodes = JSON.parse(localStorage.getItem('podcast_saved_episodes') || '[]');

function toggleEpisodePlayed(epId) {
  const idx = podcastPlayedEpisodes.indexOf(epId);
  if (idx >= 0) {
    podcastPlayedEpisodes.splice(idx, 1);
  } else {
    podcastPlayedEpisodes.push(epId);
  }
  localStorage.setItem('podcast_played_episodes', JSON.stringify(podcastPlayedEpisodes));
  renderChannelEpisodesGrid();
}

function toggleEpisodeSaved(epId) {
  const idx = podcastSavedEpisodes.indexOf(epId);
  if (idx >= 0) {
    podcastSavedEpisodes.splice(idx, 1);
  } else {
    podcastSavedEpisodes.push(epId);
  }
  localStorage.setItem('podcast_saved_episodes', JSON.stringify(podcastSavedEpisodes));
  renderChannelEpisodesGrid();
}

// Channel Up (+1) and Channel Down (-1) Navigation Function for Live TV
function changeLiveChannel(direction) {
  if (state.activeTab === 'podcasts' || (state.selectedMedia && (state.selectedMedia.audioUrl || state.selectedMedia.category === 'Podcast' || state.selectedMedia.channelName))) {
    return;
  }

  const channelList = (state.filteredChannels && state.filteredChannels.length > 0) 
    ? state.filteredChannels 
    : (state.channels || []);

  if (!channelList || channelList.length === 0) return;

  let currentIndex = -1;
  if (state.currentChannel) {
    currentIndex = channelList.findIndex(c => c.id === state.currentChannel.id || c.name === state.currentChannel.name || c.url === state.currentChannel.url);
  }

  if (currentIndex === -1) {
    currentIndex = 0;
  }

  let nextIndex = currentIndex + direction;
  if (nextIndex < 0) {
    nextIndex = channelList.length - 1;
  } else if (nextIndex >= channelList.length) {
    nextIndex = 0;
  }

  const targetChannel = channelList[nextIndex];
  if (!targetChannel) return;

  state.currentChannel = targetChannel;

  // Highlight card in DOM grid if visible
  const allCards = document.querySelectorAll('.channel-card');
  allCards.forEach(c => c.classList.remove('active', 'ring-2', 'ring-red-500'));
  const activeCard = Array.from(allCards).find(card => card.dataset.channelId === targetChannel.id);
  if (activeCard) {
    activeCard.classList.add('active', 'ring-2', 'ring-red-500');
    activeCard.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
  }

  if (player && typeof player.playChannel === 'function') {
    player.playChannel(targetChannel);
    if (typeof player.showToast === 'function') {
      player.showToast(`CH ${nextIndex + 1}/${channelList.length}: ${targetChannel.name}`);
    }
  }
}

// Open Channel Episodes View Modal (With Search, Sort & Pagination)
async function openPodcastChannelModal(channel) {
  if (!isLiveTvActive()) {
    if (typeof player !== 'undefined' && player && typeof player.showToast === 'function') {
      player.showToast("Sign in with active Live TV credentials to unlock Podcasts");
    }
    switchTab('settings');
    return;
  }
  const overlay = document.getElementById('podcast-channel-overlay');
  if (!overlay) return;

  activePodcastModalChannel = channel;
  activePodcastContinuationToken = '';
  activePodcastHasMore = true;
  podcastEpisodesSearchQuery = '';
  podcastEpisodesSortMode = 'newest';
  podcastEpisodesPage = 1;
  podcastActiveTab = 'all';

  const avatar = document.getElementById('podcast-channel-avatar');
  const title = document.getElementById('podcast-channel-title');
  const host = document.getElementById('podcast-channel-host');
  const desc = document.getElementById('podcast-channel-desc');
  const subsBadge = document.getElementById('podcast-channel-subs-badge');
  const closeBtn = document.getElementById('podcast-channel-close-btn');
  const shareBtn = document.getElementById('podcast-channel-share-btn');
  const playLatestBtn = document.getElementById('podcast-channel-play-latest-btn');
  const searchInput = document.getElementById('podcast-episodes-search');
  const sortSelect = document.getElementById('podcast-episodes-sort');
  const loadMoreBtn = document.getElementById('podcast-episodes-load-more');
  const grid = document.getElementById('podcast-channel-episodes-grid');
  
  const tabAll = document.getElementById('podcast-tab-all');
  const tabUnplayed = document.getElementById('podcast-tab-unplayed');
  const tabSaved = document.getElementById('podcast-tab-saved');

  if (avatar) avatar.src = channel.avatar;
  if (title) title.textContent = channel.channelName;
  if (host) host.textContent = `Hosted by ${channel.host || 'YouTube Creator'} • ${channel.category}`;
  if (desc) desc.textContent = channel.description || 'Watch latest full video podcast episodes.';
  if (subsBadge) subsBadge.textContent = channel.subscribers || 'YouTube Channel';

  // IMMEDIATELY open overlay modal for instant UI responsiveness
  overlay.classList.remove('hidden');
  overlay.style.display = 'flex';
  document.body.style.overflow = 'hidden';
  createIcons(iconConfig);

  if (shareBtn) {
    shareBtn.onclick = () => {
      const showUrl = window.location.origin + '?podcast=' + encodeURIComponent(channel.id);
      navigator.clipboard.writeText(showUrl).then(() => {
        if (player && typeof player.showToast === 'function') {
          player.showToast('Show link copied to clipboard!');
        } else {
          alert('Show link copied to clipboard!');
        }
      });
    };
  }

  if (closeBtn) {
    closeBtn.onclick = () => {
      overlay.classList.add('hidden');
      overlay.style.display = 'none';
      document.body.style.overflow = '';
    };
  }

  // Load and fetch channel episodes dynamically
  activePodcastAllEpisodes = await fetchChannelPastEpisodes(channel);
  window.activePodcastAllEpisodes = activePodcastAllEpisodes;

  if (playLatestBtn) {
    playLatestBtn.onclick = () => {
      if (activePodcastAllEpisodes && activePodcastAllEpisodes.length > 0) {
        const ep = activePodcastAllEpisodes[0];
        openPodcastModal({
          ...ep,
          channelName: channel.channelName,
          category: channel.category,
          thumbnail: ep.thumbnail || channel.avatar,
          description: ep.description || channel.description
        });
      }
    };
  }

  // Wire Filter Tabs
  const setTabActive = (tabName, btnEl) => {
    podcastActiveTab = tabName;
    podcastEpisodesPage = 1;
    [tabAll, tabUnplayed, tabSaved].forEach(b => {
      if (b) {
        b.className = 'podcast-ep-tab px-3 py-1.5 rounded-lg text-xs font-bold transition-all text-slate-400 hover:text-white';
      }
    });
    if (btnEl) {
      btnEl.className = 'podcast-ep-tab active px-3 py-1.5 rounded-lg text-xs font-bold transition-all text-white bg-red-600';
    }
    renderChannelEpisodesGrid();
  };

  if (tabAll) tabAll.onclick = () => setTabActive('all', tabAll);
  if (tabUnplayed) tabUnplayed.onclick = () => setTabActive('unplayed', tabUnplayed);
  if (tabSaved) tabSaved.onclick = () => setTabActive('saved', tabSaved);

  // Wire search input inside modal
  if (searchInput) {
    searchInput.value = '';
    searchInput.oninput = (e) => {
      podcastEpisodesSearchQuery = e.target.value.toLowerCase().trim();
      podcastEpisodesPage = 1;
      renderChannelEpisodesGrid();
    };
  }

  // Wire sort select inside modal
  if (sortSelect) {
    sortSelect.value = 'newest';
    sortSelect.onchange = (e) => {
      podcastEpisodesSortMode = e.target.value;
      podcastEpisodesPage = 1;
      renderChannelEpisodesGrid();
    };
  }

  // Wire load more pagination button
  if (loadMoreBtn) {
    loadMoreBtn.onclick = async () => {
      const currentTotal = activePodcastAllEpisodes.length;
      const totalToShow = (podcastEpisodesPage + 1) * PODCAST_EPISODES_PER_PAGE;

      if (totalToShow > currentTotal && activePodcastHasMore) {
        const originalText = loadMoreBtn.textContent;
        loadMoreBtn.textContent = 'Fetching Older Episodes...';
        loadMoreBtn.disabled = true;
        try {
          const res = await fetchChannelPastEpisodesNextPage(activePodcastModalChannel, activePodcastContinuationToken);
          if (res && Array.isArray(res.episodes) && res.episodes.length > 0) {
            const existingIds = new Set(activePodcastAllEpisodes.map(ep => ep.id || ep.youtubeId));
            const newUnique = res.episodes.filter(ep => !existingIds.has(ep.id || ep.youtubeId));
            
            if (newUnique.length > 0) {
              activePodcastAllEpisodes = [...activePodcastAllEpisodes, ...newUnique];
              window.activePodcastAllEpisodes = activePodcastAllEpisodes;
              activePodcastContinuationToken = res.continuation || '';
            } else {
              activePodcastHasMore = false;
            }
          } else {
            activePodcastHasMore = false;
          }
        } catch (e) {
          console.warn('[Podcasts] Error loading next page of episodes:', e);
          activePodcastHasMore = false;
        }
        loadMoreBtn.textContent = originalText;
        loadMoreBtn.disabled = false;
      }

      podcastEpisodesPage++;
      renderChannelEpisodesGrid();
    };
  }

  renderChannelEpisodesGrid();
}

// Render paginated & filtered episode grid inside Channel Modal
function renderChannelEpisodesGrid() {
  const grid = document.getElementById('podcast-channel-episodes-grid');
  const countBadge = document.getElementById('podcast-episodes-count');
  const loadMoreWrap = document.getElementById('podcast-episodes-load-more-wrap');

  if (!grid || !activePodcastModalChannel) return;

  let filtered = [...activePodcastAllEpisodes];

  // Apply tab filter
  if (podcastActiveTab === 'unplayed') {
    filtered = filtered.filter(ep => !podcastPlayedEpisodes.includes(ep.id || ep.youtubeId));
  } else if (podcastActiveTab === 'saved') {
    filtered = filtered.filter(ep => podcastSavedEpisodes.includes(ep.id || ep.youtubeId));
  }

  // Apply episode search filter
  if (podcastEpisodesSearchQuery) {
    filtered = filtered.filter(ep =>
      ep.title.toLowerCase().includes(podcastEpisodesSearchQuery) ||
      (ep.description && ep.description.toLowerCase().includes(podcastEpisodesSearchQuery))
    );
  }

  const getEpTime = (ep) => {
    if (ep.timestamp) return ep.timestamp;
    if (ep.date && ep.date !== 'Recent') {
      const parsed = Date.parse(ep.date);
      if (!isNaN(parsed)) return parsed;
    }
    return ep.year ? new Date(ep.year, 0, 1).getTime() : 0;
  };

  // Apply sorting
  if (podcastEpisodesSortMode === 'newest') {
    filtered.sort((a, b) => getEpTime(b) - getEpTime(a));
  } else if (podcastEpisodesSortMode === 'oldest') {
    filtered.sort((a, b) => getEpTime(a) - getEpTime(b));
  } else if (podcastEpisodesSortMode === 'duration') {
    filtered.sort((a, b) => (b.duration || '').localeCompare(a.duration || ''));
  }

  if (countBadge) countBadge.textContent = `${filtered.length} Episodes`;

  const totalToShow = podcastEpisodesPage * PODCAST_EPISODES_PER_PAGE;
  const visibleEpisodes = filtered.slice(0, totalToShow);

  grid.innerHTML = '';
  if (visibleEpisodes.length === 0) {
    grid.innerHTML = `
      <div class="col-span-full py-12 text-center text-slate-400 bg-slate-900/60 rounded-2xl border border-slate-800">
        <i data-lucide="alert-circle" class="w-8 h-8 mx-auto text-slate-500 mb-2"></i>
        <p class="text-xs font-semibold text-slate-300">No episodes match your search or filter selection.</p>
        <p class="text-[11px] text-slate-500 mt-1">Try switching tabs or clearing your search term.</p>
      </div>
    `;
    if (loadMoreWrap) loadMoreWrap.style.display = 'none';
    createIcons(iconConfig);
    return;
  }

  const channelAvatar = activePodcastModalChannel ? activePodcastModalChannel.avatar : 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80';

  visibleEpisodes.forEach(ep => {
    const epId = ep.id || ep.youtubeId;
    const isPlayed = podcastPlayedEpisodes.includes(epId);
    const isSaved = podcastSavedEpisodes.includes(epId);

    const card = document.createElement('div');
    card.className = `podcast-episode-card ${isPlayed ? 'is-played' : ''}`;
    const thumbUrl = ep.thumbnail || channelAvatar;

    const isInQueue = pocketcastsState.queue.some(e => (e.id || e.youtubeId) === epId);

    card.innerHTML = `
      <div class="ep-thumb-wrapper ep-play-trigger">
        <img src="${thumbUrl}" alt="${ep.title}" class="ep-thumb-img" onerror="this.onerror=null; this.src='${channelAvatar}';">
        <div class="ep-play-overlay">
          <div class="ep-play-disc">
            <i data-lucide="play" class="w-5 h-5 fill-white text-white ml-0.5"></i>
          </div>
        </div>
        <span class="ep-duration-badge">${ep.duration || 'Video'}</span>
        <span class="ep-date-badge">📅 ${ep.date || ep.year || 'Recent'}</span>
        ${isPlayed ? '<span class="ep-status-badge played">✓ PLAYED</span>' : '<span class="ep-status-badge hd">HD 🔴</span>'}
      </div>

      <div class="ep-body">
        <div>
          <h4 class="ep-title ep-play-trigger">${ep.title}</h4>
          <p class="ep-desc">${ep.description || 'Full podcast episode video conversation.'}</p>
        </div>

        <div class="ep-footer">
          <span class="ep-date">📅 Published ${ep.date || ep.year || 'Recent'}</span>

          <div class="ep-actions">
            <button class="ep-btn-icon ep-save-btn" title="${isSaved ? 'Remove Bookmark' : 'Save Bookmark'}">
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="${isSaved ? '#f59e0b' : 'none'}" stroke="${isSaved ? '#f59e0b' : 'currentColor'}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m19 21-7-4-7 4V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16z"/></svg>
            </button>
            <button class="ep-btn-icon ep-played-btn" title="${isPlayed ? 'Mark Unplayed' : 'Mark Played'}">
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="${isPlayed ? '#10b981' : 'currentColor'}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
            </button>
            <button class="ep-btn-icon ep-queue-btn" title="${isInQueue ? 'Remove from Queue' : 'Add to Up Next'}">
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="${isInQueue ? '#f59e0b' : 'none'}" stroke="${isInQueue ? '#f59e0b' : 'currentColor'}" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="12" y1="5" x2="12" y2="19"/>
                <line x1="5" y1="12" x2="19" y2="12"/>
              </svg>
            </button>
            <button class="ep-btn-play ep-play-trigger">
              <span>Play</span>
              <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="white" stroke="white" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg>
            </button>
          </div>
        </div>
      </div>
    `;

    // Click triggers
    const triggers = card.querySelectorAll('.ep-play-trigger');
    triggers.forEach(t => {
      t.addEventListener('click', () => {
        openPodcastModal({
          ...ep,
          channelName: activePodcastModalChannel.channelName,
          category: activePodcastModalChannel.category,
          thumbnail: ep.thumbnail || channelAvatar,
          description: ep.description || activePodcastModalChannel.description
        });
      });
    });

    const saveBtn = card.querySelector('.ep-save-btn');
    if (saveBtn) {
      saveBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleEpisodeSaved(epId);
      });
    }

    const playedBtn = card.querySelector('.ep-played-btn');
    if (playedBtn) {
      playedBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        toggleEpisodePlayed(epId);
      });
    }

    const queueBtn = card.querySelector('.ep-queue-btn');
    if (queueBtn) {
      queueBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        const epInQueue = pocketcastsState.queue.some(q => (q.id || q.youtubeId) === epId);
        if (epInQueue) {
          removeFromPodcastQueue(epId);
        } else {
          addToPodcastQueue({
            ...ep,
            channelName: activePodcastModalChannel.channelName,
            category: activePodcastModalChannel.category,
            thumbnail: ep.thumbnail || channelAvatar,
            description: ep.description || activePodcastModalChannel.description
          });
        }
        renderChannelEpisodesGrid();
      });
    }

    grid.appendChild(card);
  });

  if (loadMoreWrap) {
    loadMoreWrap.style.display = (totalToShow < filtered.length) ? 'block' : 'none';
  }

  createIcons(iconConfig);
}

// Toggle Favorite Podcast Channel
function togglePodcastFavorite(podcast) {
  if (!state.favoritePodcasts) state.favoritePodcasts = [];
  const index = state.favoritePodcasts.findIndex(f => f.id === podcast.id);
  if (index >= 0) {
    state.favoritePodcasts.splice(index, 1);
  } else {
    state.favoritePodcasts.push(podcast);
  }
  localStorage.setItem('iptv_favorite_podcasts', JSON.stringify(state.favoritePodcasts));
  loadPodcastsDashboard();
}

// Reset video player window before loading any new stream request
function resetPlayerWindow() {
  console.log('[resetPlayerWindow] Resetting player window state');
  closeActiveSse();

  if (document.fullscreenElement) {
    try { document.exitFullscreen().catch(() => {}); } catch (e) {}
  }

  const videoEl = document.getElementById('video-player');
  const embedWrapper = document.getElementById('embed-player-wrapper');
  const embedIframe = document.getElementById('embed-iframe');
  const playerWrapper = document.querySelector('.player-wrapper');
  const shield = document.getElementById('embed-shield');
  const poster = document.getElementById('player-poster');
  const sourcesList = document.getElementById('sources-list');
  const statusText = document.getElementById('sources-status');

  if (playerWrapper) {
    playerWrapper.classList.remove('is-pseudo-fullscreen');
    playerWrapper.classList.remove('embed-active');
    playerWrapper.classList.remove('user-idle');
    playerWrapper.classList.add('controls-active');
  }
  document.body.classList.remove('body-pseudo-fullscreen');

  if (videoEl) {
    videoEl.pause();
    videoEl.removeAttribute('src');
    try { videoEl.load(); } catch (e) {}
    videoEl.style.display = 'none';
  }

  if (embedIframe) {
    embedIframe.src = 'about:blank';
    embedIframe.removeAttribute('src');
  }

  if (embedWrapper) {
    embedWrapper.style.display = 'none';
  }

  if (shield) shield.style.display = 'none';

  if (poster) {
    poster.style.display = 'flex';
    poster.style.backgroundImage = '';
  }

  if (sourcesList) sourcesList.innerHTML = '';
  if (statusText) statusText.textContent = 'Select a stream or episode to play.';

  // Hide podcast-specific in-player tray
  const moreSection = document.getElementById('player-more-episodes-section');
  if (moreSection) moreSection.classList.add('hidden');

  // Restore video controls bar visibility for standard media
  const videoControls = document.getElementById('video-controls');
  if (videoControls) videoControls.style.display = '';
}

// Open HD Video Player Modal (Podcast Episode)
async function openPodcastModal(podcast) {
  stopActiveLiveTVFeed();
  resetPlayerWindow();
  state.selectedMedia = podcast;

  // Add to history + mark as played
  const epId = podcast.id || podcast.youtubeId;
  if (epId) {
    if (!podcastPlayedEpisodes.includes(epId)) {
      podcastPlayedEpisodes.push(epId);
      localStorage.setItem('podcast_played_episodes', JSON.stringify(podcastPlayedEpisodes));
    }
    const historyItem = { episode: podcast, dateStr: new Date().toLocaleDateString() };
    pocketcastsState.history = [historyItem, ...pocketcastsState.history.filter(h => (h.episode?.id || h.episode?.youtubeId) !== epId)].slice(0, 30);
    localStorage.setItem('pocketcasts_history', JSON.stringify(pocketcastsState.history));
    updatePocketCastsBadges();
    if (pocketcastsState.currentView === 'history') renderPocketCastsHistory();
  }

  const overlay = document.getElementById('details-overlay');
  if (!overlay) return;

  // Dynamically resolve real YouTube Video ID if missing
  if (!podcast.youtubeId && (podcast.title || podcast.channelName)) {
    try {
      const cleanTitle = (podcast.title || '')
        .replace(/^#?\d+[\s–—-]*/, '')
        .replace(/^E\d+:?\s*/i, '')
        .replace(/#\d+/g, '')
        .trim();
      const q = encodeURIComponent(`${podcast.channelName || ''} ${cleanTitle}`.trim());
      const res = await fetch(`https://inv.tux.pizza/api/v1/search?q=${q}&type=video`);
      if (res.ok) {
        const data = await res.json();
        if (data && data[0] && data[0].videoId) {
          podcast.youtubeId = data[0].videoId;
          podcast.thumbnail = `https://img.youtube.com/vi/${podcast.youtubeId}/hqdefault.jpg`;
        }
      }
    } catch (err) {
      console.warn('[Podcasts] Invidious YouTube video resolution error:', err);
    }
  }

  // Move shared player section into details container
  const playerSection = document.getElementById('player-section');
  const vodContainer = document.getElementById('vod-player-container');
  if (playerSection && vodContainer) {
    vodContainer.appendChild(playerSection);
  }

  // Hide Live TV controls & Stream Sources panel completely for Podcasts
  const tvSelectors = document.getElementById('tv-selectors');
  const sourcesPanel = document.querySelector('.sources-panel');
  const chPrevBtn = document.getElementById('ch-prev-btn');
  const chNextBtn = document.getElementById('ch-next-btn');
  const liveIndicatorDot = document.getElementById('live-indicator-dot');
  const liveIndicatorText = document.getElementById('live-indicator-text');
  const seekContainer = document.getElementById('seek-container');
  const seekSlider = document.getElementById('seek-slider');
  const currentTimeEl = document.getElementById('current-time');
  const durationTimeEl = document.getElementById('duration-time');

  if (tvSelectors) tvSelectors.style.display = 'none';
  if (sourcesPanel) sourcesPanel.style.display = 'none';
  if (chPrevBtn) chPrevBtn.style.display = 'none';
  if (chNextBtn) chNextBtn.style.display = 'none';
  if (liveIndicatorDot) liveIndicatorDot.style.display = 'none';
  if (liveIndicatorText) liveIndicatorText.textContent = 'VIDEO PODCAST';
  if (seekContainer) seekContainer.style.display = 'flex';

  const titleEl = document.getElementById('details-title');
  const metaEl = document.getElementById('details-meta-info');
  const overviewEl = document.getElementById('details-overview');
  const backdropEl = document.getElementById('details-backdrop');
  const embedWrapper = document.getElementById('embed-player-wrapper');
  const embedIframe = document.getElementById('embed-iframe');
  const videoEl = document.getElementById('video-player');
  const poster = document.getElementById('player-poster');
  const shield = document.getElementById('embed-shield');

  if (titleEl) titleEl.textContent = podcast.title;

  // Guarantee valid YouTube Video ID for every podcast episode (uses real verified IDs only)
  if (!podcast.youtubeId) {
    if (podcast.id === 'chan_mkbhd_waveform' || (podcast.channelName && podcast.channelName.toLowerCase().includes('waveform'))) {
      podcast.youtubeId = 'PtCMsXYAPyc'; // Waveform: Framework Laptops & Robot Cleaner (verified Jul 2026)
    } else if (podcast.id === 'chan_startalk' || (podcast.channelName && podcast.channelName.toLowerCase().includes('startalk'))) {
      podcast.youtubeId = 'E8cXOJlyMZY'; // StarTalk: Your brain wasn't built to find the truth (verified Jul 2026)
    } else if (podcast.id === 'chan_kill_tony' || (podcast.channelName && podcast.channelName.toLowerCase().includes('kill tony'))) {
      podcast.youtubeId = 'ZHLhms7ceMs'; // Kill Tony #778 - JIMMY CARR (verified Jul 2026)
    } else {
      podcast.youtubeId = 'vif8NQcjVf0'; // Lex Fridman: Jensen Huang / NVIDIA (verified Apr 2026)
    }
  }

  const ytFallbackBtn = `
    <a href="https://www.youtube.com/watch?v=${podcast.youtubeId}" target="_blank" rel="noopener noreferrer" class="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-red-600 hover:bg-red-500 text-white font-bold text-xs transition-all shadow-md ml-2">
      <svg xmlns="http://www.w3.org/2000/svg" width="12" height="12" viewBox="0 0 24 24" fill="currentColor"><path d="M23.498 6.186a3.016 3.016 0 0 0-2.122-2.136C19.505 3.545 12 3.545 12 3.545s-7.505 0-9.377.505A3.017 3.017 0 0 0 .502 6.186C0 8.07 0 12 0 12s0 3.93.502 5.814a3.016 3.016 0 0 0 2.122 2.136c1.871.505 9.376.505 9.376.505s7.505 0 9.377-.505a3.015 3.015 0 0 0 2.122-2.136C24 15.93 24 12 24 12s0-3.93-.502-5.814zM9.545 15.568V8.432L15.818 12l-6.273 3.568z"/></svg>
      <span>Watch on YouTube</span>
    </a>
  `;

  const yearEl = document.getElementById('details-year');
  const ratingEl = document.getElementById('details-rating');
  const runtimeEl = document.getElementById('details-runtime');

  if (yearEl) yearEl.textContent = `📅 ${podcast.date || 'Recent'}`;
  if (ratingEl) ratingEl.innerHTML = `🎙️ ${podcast.channelName || 'Podcast'}`;
  if (runtimeEl) runtimeEl.innerHTML = `<span class="px-2 py-0.5 rounded bg-red-500/20 text-red-400 font-medium">🔴 HD Video Episode</span>`;

  if (overviewEl) overviewEl.textContent = podcast.description || 'Watch full video podcast episodes directly inside TV DINNER.';
  if (backdropEl) backdropEl.style.backgroundImage = `url(${podcast.thumbnail || podcast.avatar || ''})`;

  if (shield) {
    shield.style.display = 'none';
    shield.style.pointerEvents = 'none';
  }
  if (poster) poster.style.display = 'none';

  if (videoEl) {
    videoEl.style.display = 'none';
    videoEl.pause();
    videoEl.removeAttribute('src');
  }

  // Hide custom video controls bar for podcasts (YouTube provides native interactive controls)
  const videoControls = document.getElementById('video-controls');
  if (videoControls) videoControls.style.display = 'none';

  // Clear buffering spinner, player error, and previous embed overlays
  const playerSpinner = document.getElementById('player-spinner');
  const playerErr = document.getElementById('player-error');
  if (playerSpinner) playerSpinner.style.display = 'none';
  if (playerErr) playerErr.classList.add('hidden');

  const existingEmbedError = document.getElementById('embed-error-overlay');
  if (existingEmbedError) existingEmbedError.remove();

  // Support direct audio URL if provided, otherwise load YouTube Video Player embed
  if (podcast.audioUrl || podcast.enclosureUrl) {
    const audioUrl = podcast.audioUrl || podcast.enclosureUrl;
    if (embedWrapper) embedWrapper.style.display = 'none';
    if (embedIframe) embedIframe.src = 'about:blank';
    if (videoEl) {
      videoEl.style.display = 'block';
      player.playChannel({
        url: audioUrl,
        name: podcast.title || 'Podcast Episode'
      });
    }
  } else {
    if (embedWrapper) {
      embedWrapper.style.display = 'block';
      embedWrapper.style.pointerEvents = 'auto';
      embedWrapper.style.position = 'absolute';
      embedWrapper.style.inset = '0';
      embedWrapper.style.width = '100%';
      embedWrapper.style.height = '100%';
    }
    if (embedIframe) {
      embedIframe.style.pointerEvents = 'auto';
      embedIframe.style.display = 'block';
      embedIframe.removeAttribute('referrerpolicy'); // Critical for YouTube referrer check
      embedIframe.setAttribute('allow', 'accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share; fullscreen *');
      embedIframe.setAttribute('allowfullscreen', 'true');
      const origin = encodeURIComponent(window.location.origin);
      embedIframe.src = `https://www.youtube-nocookie.com/embed/${podcast.youtubeId}?autoplay=1&rel=0&playsinline=1&enablejsapi=1&origin=${origin}`;
    }
  }

  // Populate In-Player "More Episodes from Channel" section
  const moreSection = document.getElementById('player-more-episodes-section');
  const moreGrid = document.getElementById('player-more-episodes-grid');
  const moreChannelLabel = document.getElementById('player-more-episodes-channel');

  if (moreSection && moreGrid) {
    if (activePodcastModalChannel && activePodcastAllEpisodes.length > 1) {
      moreSection.classList.remove('hidden');
      if (moreChannelLabel) moreChannelLabel.textContent = activePodcastModalChannel.channelName;
      moreGrid.innerHTML = '';

      const otherEpisodes = activePodcastAllEpisodes.filter(e => e.youtubeId !== podcast.youtubeId).slice(0, 6);
      otherEpisodes.forEach(ep => {
        const card = document.createElement('div');
        card.className = 'bg-slate-900 border border-slate-800 rounded-lg p-2.5 hover:border-red-500 cursor-pointer transition-all flex items-center gap-3 group';
        card.innerHTML = `
          <img src="${ep.thumbnail}" class="w-16 h-10 object-cover rounded shrink-0 group-hover:scale-105 transition-transform" alt="${ep.title}">
          <div class="flex-1 min-w-0">
            <h5 class="text-xs font-bold text-white truncate group-hover:text-red-400">${ep.title}</h5>
            <span class="text-[10px] text-slate-400">${ep.duration || 'Video'} • ${ep.date || 'Past'}</span>
          </div>
        `;
        card.onclick = () => {
          openPodcastModal({
            ...ep,
            channelName: activePodcastModalChannel.channelName,
            category: activePodcastModalChannel.category
          });
        };
        moreGrid.appendChild(card);
      });
    } else {
      moreSection.classList.add('hidden');
    }
  }

  overlay.classList.remove('hidden');
  document.body.style.overflow = 'hidden';
  createIcons(iconConfig);
}

// Render list of cards inside scrolling rows
function renderCardRow(items, container) {
  if (!container) return;
  container.innerHTML = '';
  if (!items || items.length === 0) {
    container.innerHTML = '<span class="text-xs text-slate-600 py-4">No content items.</span>';
    return;
  }

  items.forEach(item => {
    const title = item.title || item.name || 'Untitled';
    const isTV = item.media_type === 'tv' || (!item.release_date && item.first_air_date);
    const mediaItem = { ...item, media_type: isTV ? 'tv' : 'movie' };
    const year = (item.release_date || item.first_air_date || '').split('-')[0] || 'N/A';
    const rating = item.vote_average ? item.vote_average.toFixed(1) : 'N/A';
    const posterPath = getTMDBImageUrl(item.poster_path, 'w185') || 'https://via.placeholder.com/185x278/090e1a/475569?text=No+Poster';

    const inWatchlist = state.watchlist.some(x => Number(x.id) === Number(item.id) && (x.media_type === mediaItem.media_type));

    const card = document.createElement('div');
    card.className = 'detail-item-card hover-scale cursor-pointer relative';
    card.setAttribute('role', 'button');
    card.setAttribute('tabindex', '0');
    card.innerHTML = `
      ${inWatchlist ? `
        <button class="card-remove-btn" title="Remove from Watchlist">
          <i data-lucide="trash-2" class="w-3.5 h-3.5"></i>
        </button>
      ` : ''}
      <img src="${posterPath}" alt="${title}" class="detail-item-poster" loading="lazy">
      <div class="detail-item-info">
        <h4 class="detail-item-title">${title}</h4>
        <div class="detail-item-meta">
          <span class="detail-item-year">${year} • ${isTV ? 'TV' : 'Movie'}</span>
          <span class="detail-item-rating flex items-center gap-1">
            <i data-lucide="star" class="w-3 h-3 fill-amber-400 text-amber-400"></i> ${rating}
          </span>
        </div>
      </div>
    `;

    if (inWatchlist) {
      const delBtn = card.querySelector('.card-remove-btn');
      if (delBtn) {
        delBtn.addEventListener('click', (e) => {
          e.stopPropagation();
          e.preventDefault();
          removeFromWatchlist(item.id, isTV ? 'tv' : 'movie');
        });
      }
    }

    const handleOpen = (e) => {
      if (e) e.stopPropagation();
      openDetailsView(mediaItem);
    };

    card.addEventListener('click', handleOpen);
    card.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        handleOpen(e);
      }
    });

    container.appendChild(card);
  });
  createIcons(iconConfig);
}

// Load popular searches as default searches
async function loadPopularSearches() {
  if (state.searchQuery) return;
  const grid = document.getElementById('search-results-grid');
  const heading = document.getElementById('search-status-heading');
  if (heading) heading.textContent = 'Popular Search Titles';
  
  try {
    const data = await getTrending();
    const trending = data.results || [];
    if (grid) renderSearchGrid(trending, grid);
  } catch (err) {
    console.error("Failed to load trending searches:", err);
  }
}

// Render search results grid
function renderSearchGrid(results, gridContainer) {
  const grid = gridContainer || document.getElementById('movies-search-results-grid');
  if (!grid) return;
  grid.innerHTML = '';

  if (!results || results.length === 0) return;

  results.forEach(item => {
    const title = item.title || item.name || 'Untitled';
    const isTV = item.media_type === 'tv' || (!item.release_date && item.first_air_date);
    const mediaItem = { ...item, media_type: isTV ? 'tv' : 'movie' };
    const year = (item.release_date || item.first_air_date || '').split('-')[0] || 'N/A';
    const rating = item.vote_average ? item.vote_average.toFixed(1) : 'N/A';
    const posterPath = getTMDBImageUrl(item.poster_path, 'w185') || 'https://via.placeholder.com/185x278/090e1a/475569?text=No+Poster';

    const card = document.createElement('div');
    card.className = 'detail-item-card hover-scale cursor-pointer';
    card.setAttribute('role', 'button');
    card.setAttribute('tabindex', '0');
    card.innerHTML = `
      <img src="${posterPath}" alt="${title}" class="detail-item-poster" loading="lazy">
      <div class="detail-item-info">
        <h4 class="detail-item-title">${title}</h4>
        <div class="detail-item-meta">
          <span class="detail-item-year">${year} • ${isTV ? 'TV' : 'Movie'}</span>
          <span class="detail-item-rating">
            <i data-lucide="star" class="w-3 h-3 fill-amber-400 text-amber-400"></i> ${rating}
          </span>
        </div>
      </div>
    `;

    const handleOpen = (e) => {
      if (e) e.stopPropagation();
      openDetailsView(mediaItem);
    };

    card.addEventListener('click', handleOpen);
    card.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        handleOpen(e);
      }
    });

    grid.appendChild(card);
  });
  createIcons(iconConfig);
}

// Setup search & genre chip logic for Movies and TV Series
function setupSearch() {
  // Live channel inline search
  const liveSearchInput = document.getElementById('live-search-input');
  if (liveSearchInput) {
    liveSearchInput.addEventListener('input', (e) => {
      state.searchQuery = e.target.value.trim();
      applyFilterAndRender();
    });
  }

  // Movies search
  const moviesSearchInput = document.getElementById('movies-search-input');
  const moviesSearchResultsSection = document.getElementById('movies-search-results-section');
  const moviesDefaultSection = document.getElementById('movies-default-section');

  if (moviesSearchInput) {
    moviesSearchInput.addEventListener('input', (e) => {
      const query = e.target.value.trim();
      state.searchQuery = query;

      if (!query) {
        if (moviesSearchResultsSection) moviesSearchResultsSection.classList.add('hidden');
        if (moviesDefaultSection) moviesDefaultSection.classList.remove('hidden');
        return;
      }

      if (moviesSearchResultsSection) moviesSearchResultsSection.classList.remove('hidden');
      if (moviesDefaultSection) moviesDefaultSection.classList.add('hidden');

      clearTimeout(searchDebounceTimer);
      searchDebounceTimer = setTimeout(() => {
        triggerSearchQuery(query, 'movie');
      }, 400);
    });
  }

  // Series search
  const seriesSearchInput = document.getElementById('series-search-input');
  const seriesSearchResultsSection = document.getElementById('series-search-results-section');
  const seriesDefaultSection = document.getElementById('series-default-section');

  if (seriesSearchInput) {
    seriesSearchInput.addEventListener('input', (e) => {
      const query = e.target.value.trim();
      state.searchQuery = query;

      if (!query) {
        if (seriesSearchResultsSection) seriesSearchResultsSection.classList.add('hidden');
        if (seriesDefaultSection) seriesDefaultSection.classList.remove('hidden');
        return;
      }

      if (seriesSearchResultsSection) seriesSearchResultsSection.classList.remove('hidden');
      if (seriesDefaultSection) seriesDefaultSection.classList.add('hidden');

      clearTimeout(searchDebounceTimer);
      searchDebounceTimer = setTimeout(() => {
        triggerSearchQuery(query, 'tv');
      }, 400);
    });
  }

  // Podcasts Live Search (Curated Channels, Global Real Search Channels, and Matching Episodes)
  const podcastsSearchInput = document.getElementById('podcasts-search-input');
  const podcastsSearchResultsSection = document.getElementById('podcasts-search-results-section');
  const podcastsDefaultSection = document.getElementById('podcasts-default-section');
  let podcastsSearchDebounceTimer;

  if (podcastsSearchInput) {
    podcastsSearchInput.addEventListener('input', (e) => {
      const query = e.target.value.trim();
      clearTimeout(podcastsSearchDebounceTimer);

      filterInAppPodcastDashboard(query);

      if (!query) {
        if (podcastsSearchResultsSection) podcastsSearchResultsSection.classList.add('hidden');
        if (podcastsDefaultSection) podcastsDefaultSection.classList.remove('hidden');
        return;
      }

      if (podcastsSearchResultsSection) podcastsSearchResultsSection.classList.remove('hidden');

      const grid = document.getElementById('podcasts-search-results-grid');
      const heading = document.getElementById('podcasts-search-status-heading');
      const emptyState = document.getElementById('podcasts-search-empty-state');

      if (heading) heading.textContent = `Searching global podcast catalog for "${query}"...`;
      if (emptyState) emptyState.classList.add('hidden');
      if (grid) {
        grid.innerHTML = `
          <div class="flex flex-col items-center justify-center py-12 gap-3 text-slate-400">
            <div class="w-7 h-7 border-2 border-red-500 border-t-transparent rounded-full animate-spin"></div>
            <span class="text-sm font-semibold text-slate-300">Searching millions of real podcasts & episodes...</span>
          </div>
        `;
      }

      podcastsSearchDebounceTimer = setTimeout(async () => {
        try {
          const results = await searchRealPodcastAPI(query);
          const { curatedChannels = [], realChannels = [], matchingEpisodes = [] } = results;
          const totalResults = curatedChannels.length + realChannels.length + matchingEpisodes.length;

          if (totalResults === 0) {
            if (grid) grid.innerHTML = '';
            if (emptyState) emptyState.classList.remove('hidden');
            if (heading) heading.textContent = `No podcasts found for "${query}"`;
            return;
          }

          if (emptyState) emptyState.classList.add('hidden');
          if (heading) heading.textContent = `Podcast Search Results for "${query}" (${totalResults} matches)`;
          if (grid) {
            grid.innerHTML = '';

            // 1. Curated Catalog Channels
            if (curatedChannels.length > 0) {
              const section = document.createElement('div');
              section.className = 'space-y-3';
              section.innerHTML = `
                <h3 class="text-sm font-bold text-white flex items-center gap-2 border-b border-slate-800 pb-2">
                  <i data-lucide="radio" class="w-4 h-4 text-red-500"></i> Featured Catalog Shows (${curatedChannels.length})
                </h3>
                <div class="carousel-list custom-scrollbar py-2"></div>
              `;
              const rowContainer = section.querySelector('.carousel-list');
              renderPodcastChannelRow(curatedChannels, rowContainer);
              grid.appendChild(section);
            }

            // 2. Global Real Podcast Channels
            if (realChannels.length > 0) {
              const section = document.createElement('div');
              section.className = 'space-y-3';
              section.innerHTML = `
                <h3 class="text-sm font-bold text-white flex items-center gap-2 border-b border-slate-800 pb-2">
                  <i data-lucide="globe" class="w-4 h-4 text-amber-500"></i> Global Podcast Channels (${realChannels.length})
                </h3>
                <div class="carousel-list custom-scrollbar py-2"></div>
              `;
              const rowContainer = section.querySelector('.carousel-list');
              renderPodcastChannelRow(realChannels, rowContainer);
              grid.appendChild(section);
            }

            // 3. Direct Matching Episodes
            if (matchingEpisodes.length > 0) {
              const section = document.createElement('div');
              section.className = 'space-y-3';
              section.innerHTML = `
                <h3 class="text-sm font-bold text-white flex items-center gap-2 border-b border-slate-800 pb-2">
                  <i data-lucide="film" class="w-4 h-4 text-red-500"></i> Direct Episode Matches (${matchingEpisodes.length})
                </h3>
                <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4"></div>
              `;
              const epGrid = section.querySelector('.grid');
              matchingEpisodes.forEach(ep => {
                const card = document.createElement('div');
                card.className = 'bg-slate-900/80 border border-slate-800 rounded-xl overflow-hidden hover:border-red-500 cursor-pointer transition-all flex flex-col group relative';

                const isAudio = !!ep.audioUrl;
                const badgeType = isAudio ? '🎙️ Audio' : '🔴 Video';
                const badgeColor = isAudio ? 'bg-amber-500/20 text-amber-300 border-amber-500/30' : 'bg-red-500/20 text-red-300 border-red-500/30';

                card.innerHTML = `
                  <div class="relative aspect-video overflow-hidden">
                    <img src="${ep.thumbnail}" alt="${ep.title}" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" onerror="this.onerror=null; this.src='https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80';">
                    <div class="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                      <div class="w-10 h-10 rounded-full bg-red-600 flex items-center justify-center shadow-lg">
                        <i data-lucide="play" class="w-5 h-5 fill-white text-white ml-0.5"></i>
                      </div>
                    </div>
                    <span class="absolute bottom-2 right-2 px-2 py-0.5 rounded bg-black/80 text-[10px] font-semibold text-white">${ep.duration || 'Episode'}</span>
                    <span class="absolute top-2 left-2 px-2 py-0.5 rounded text-[10px] font-bold border ${badgeColor}">${badgeType}</span>
                  </div>
                  <div class="p-3 flex-1 flex flex-col justify-between space-y-1">
                    <span class="text-[10px] font-bold text-red-400 uppercase tracking-wider truncate">${ep.channelName}</span>
                    <h4 class="text-xs sm:text-sm font-bold text-white line-clamp-2 leading-snug group-hover:text-red-400">${ep.title}</h4>
                    <div class="flex items-center justify-between text-[11px] text-slate-400 pt-1">
                      <span>📅 ${ep.date || 'Recent'}</span>
                    </div>
                  </div>
                `;
                card.onclick = () => {
                  openPodcastModal({
                    ...ep,
                    channelName: ep.channelName,
                    category: ep.category
                  });
                };
                epGrid.appendChild(card);
              });
              grid.appendChild(section);
            }

            createIcons(iconConfig);
          }
        } catch (err) {
          console.error('Podcast live search error:', err);
        }
      }, 350);
    });
  }

  // Movies genre chips
  const moviesChips = document.querySelectorAll('.movies-genre-chip');
  moviesChips.forEach(chip => {
    chip.addEventListener('click', () => {
      moviesChips.forEach(c => c.classList.remove('active'));
      chip.classList.add('active');

      const targetGenre = chip.getAttribute('data-genre');
      const rows = document.querySelectorAll('.movies-rows-section .row-container');

      rows.forEach(row => {
        const rowGenre = row.getAttribute('data-row-genre');
        if (targetGenre === 'all' || rowGenre === targetGenre || rowGenre === 'all') {
          row.style.display = 'block';
        } else {
          row.style.display = 'none';
        }
      });
    });
  });

  // Series genre chips
  const seriesChips = document.querySelectorAll('.series-genre-chip');
  seriesChips.forEach(chip => {
    chip.addEventListener('click', () => {
      seriesChips.forEach(c => c.classList.remove('active'));
      chip.classList.add('active');

      const targetGenre = chip.getAttribute('data-genre');
      const rows = document.querySelectorAll('.series-rows-section .row-container');

      rows.forEach(row => {
        const rowGenre = row.getAttribute('data-row-genre');
        if (targetGenre === 'all' || rowGenre === targetGenre || rowGenre === 'all') {
          row.style.display = 'block';
        } else {
          row.style.display = 'none';
        }
      });
    });
  });

  setupSeeMoreListeners();
}

// Category Page State
const categoryViewState = {
  activeConfig: null,
  currentPage: 1,
  isLoading: false
};

// Bind See More buttons and Category Grid controls
function setupSeeMoreListeners() {
  const seeMoreBtns = document.querySelectorAll('.see-more-btn');
  seeMoreBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const type = btn.getAttribute('data-category-type');
      const genreId = btn.getAttribute('data-genre-id');
      const mediaType = btn.getAttribute('data-media-type');
      const title = btn.getAttribute('data-category-title') || 'Category Titles';

      openCategoryPage({ type, genreId, mediaType, title });
    });
  });

  // Movies Category Back button
  const moviesBackBtn = document.getElementById('movies-category-back-btn');
  if (moviesBackBtn) {
    moviesBackBtn.addEventListener('click', () => {
      const defaultSection = document.getElementById('movies-default-section');
      const searchSection = document.getElementById('movies-search-results-section');
      const categorySection = document.getElementById('movies-category-section');

      if (categorySection) categorySection.classList.add('hidden');
      if (searchSection) searchSection.classList.add('hidden');
      if (defaultSection) defaultSection.classList.remove('hidden');
    });
  }

  // Series Category Back button
  const seriesBackBtn = document.getElementById('series-category-back-btn');
  if (seriesBackBtn) {
    seriesBackBtn.addEventListener('click', () => {
      const defaultSection = document.getElementById('series-default-section');
      const searchSection = document.getElementById('series-search-results-section');
      const categorySection = document.getElementById('series-category-section');

      if (categorySection) categorySection.classList.add('hidden');
      if (searchSection) searchSection.classList.add('hidden');
      if (defaultSection) defaultSection.classList.remove('hidden');
    });
  }

  // Movies Load More button
  const moviesLoadMoreBtn = document.getElementById('movies-category-load-more-btn');
  if (moviesLoadMoreBtn) {
    moviesLoadMoreBtn.addEventListener('click', () => {
      categoryViewState.currentPage += 1;
      loadCategoryPageItems(false);
    });
  }

  // Series Load More button
  const seriesLoadMoreBtn = document.getElementById('series-category-load-more-btn');
  if (seriesLoadMoreBtn) {
    seriesLoadMoreBtn.addEventListener('click', () => {
      categoryViewState.currentPage += 1;
      loadCategoryPageItems(false);
    });
  }
}

// Open Expanded Category Full Grid Page
async function openCategoryPage(config) {
  categoryViewState.activeConfig = config;
  categoryViewState.currentPage = 1;
  categoryViewState.isLoading = false;

  const isTV = config.mediaType === 'tv' || config.type === 'trending_tv' || config.type === 'top_rated_tv';
  
  const defaultSection = isTV ? document.getElementById('series-default-section') : document.getElementById('movies-default-section');
  const searchSection = isTV ? document.getElementById('series-search-results-section') : document.getElementById('movies-search-results-section');
  const categorySection = isTV ? document.getElementById('series-category-section') : document.getElementById('movies-category-section');
  const titleEl = isTV ? document.getElementById('series-category-view-title') : document.getElementById('movies-category-view-title');
  const gridEl = isTV ? document.getElementById('series-category-view-grid') : document.getElementById('movies-category-view-grid');

  if (defaultSection) defaultSection.classList.add('hidden');
  if (searchSection) searchSection.classList.add('hidden');
  if (categorySection) categorySection.classList.remove('hidden');

  if (titleEl) titleEl.textContent = config.title;
  if (gridEl) {
    gridEl.innerHTML = `
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
    `;
  }

  await loadCategoryPageItems(true);
}

// Load Paginated Category Items
async function loadCategoryPageItems(isReset = false) {
  if (categoryViewState.isLoading || !categoryViewState.activeConfig) return;
  categoryViewState.isLoading = true;

  const config = categoryViewState.activeConfig;
  const page = categoryViewState.currentPage;
  const isTV = config.mediaType === 'tv' || config.type === 'trending_tv' || config.type === 'top_rated_tv';
  const gridEl = isTV ? document.getElementById('series-category-view-grid') : document.getElementById('movies-category-view-grid');

  try {
    let items = [];
    if (config.type === 'trending_movies') {
      const data = await getTrendingMovies(page);
      items = data.results || [];
    } else if (config.type === 'trending_tv') {
      const data = await getTrendingTV(page);
      items = data.results || [];
    } else if (config.type === 'top_rated') {
      const data = await getTopRated(page);
      items = data.results || [];
    } else if (config.type === 'top_rated_tv') {
      const data = await getTopRatedTV(page);
      items = data.results || [];
    } else if (config.type === 'genre') {
      const data = await getByGenre(config.genreId, config.mediaType || 'movie', page);
      items = data.results || [];
    }

    if (isReset && gridEl) {
      gridEl.innerHTML = '';
    }

    if (items.length > 0 && gridEl) {
      items.forEach(item => {
        const card = document.createElement('div');
        card.className = 'detail-item-card hover-scale';
        const imgUrl = getTMDBImageUrl(item.poster_path, 'w342') || getTMDBImageUrl(item.backdrop_path, 'w342');
        const title = item.title || item.name || 'Untitled';
        const year = (item.release_date || item.first_air_date || '').split('-')[0] || '';
        const rating = item.vote_average ? item.vote_average.toFixed(1) : 'N/A';

        card.innerHTML = `
          <img src="${imgUrl}" alt="${title}" class="detail-item-poster" loading="lazy">
          <div class="detail-item-info">
            <h4 class="detail-item-title">${title}</h4>
            <div class="detail-item-sub">
              <span>${year}</span>
              <span>⭐ ${rating}</span>
            </div>
          </div>
        `;
        card.addEventListener('click', () => openDetailsView({ ...item, media_type: isTV ? 'tv' : 'movie' }));
        gridEl.appendChild(card);
      });
    }
  } catch (err) {
    console.error("Failed to load category items:", err);
  } finally {
    categoryViewState.isLoading = false;
  }
}

// Execute Movies / Series Search
async function triggerSearchQuery(query, filterType = 'all') {
  const isTV = filterType === 'tv';
  const grid = isTV ? document.getElementById('series-search-results-grid') : document.getElementById('movies-search-results-grid');
  const heading = isTV ? document.getElementById('series-search-status-heading') : document.getElementById('movies-search-status-heading');
  const emptyState = isTV ? document.getElementById('series-search-empty-state') : document.getElementById('movies-search-empty-state');
  
  if (!query) return;

  if (heading) heading.textContent = `Search results for "${query}"`;
  if (grid) {
    grid.innerHTML = `
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
    `;
  }
  if (emptyState) emptyState.classList.add('hidden');

  try {
    const data = await searchMulti(query);
    let results = data.results || [];

    if (filterType === 'movie') {
      let filtered = results.filter(item => item.media_type === 'movie');
      if (filtered.length === 0) {
        // Fallback search specifically for movie
        try {
          const mData = await fetchFromTMDB('/search/movie', { query });
          filtered = (mData.results || []).map(m => ({ ...m, media_type: 'movie' }));
        } catch (e) {
          console.warn("Fallback movie search failed:", e);
        }
      }
      results = filtered;
    } else if (filterType === 'tv') {
      let filtered = results.filter(item => item.media_type === 'tv');
      if (filtered.length === 0) {
        // Fallback search specifically for tv
        try {
          const tData = await fetchFromTMDB('/search/tv', { query });
          filtered = (tData.results || []).map(t => ({ ...t, media_type: 'tv' }));
        } catch (e) {
          console.warn("Fallback tv search failed:", e);
        }
      }
      results = filtered;
    }

    if (results.length === 0) {
      if (grid) grid.innerHTML = '';
      if (emptyState) emptyState.classList.remove('hidden');
      return;
    }

    if (grid) {
      renderSearchGrid(results, grid);
    }
  } catch (err) {
    console.error("Search failed:", err);
    if (grid) grid.innerHTML = '<div class="col-span-full text-center text-red-400 py-8">Failed to fetch search results. Please check your connection.</div>';
  }
}



// Render watchlist items in Library tab
function renderLibraryScreen() {
  if (!isLiveTvActive()) {
    renderAccessLockedState('library-results-grid', 'My Library');
    const emptyState = document.getElementById('library-empty-state');
    if (emptyState) emptyState.classList.add('hidden');
    return;
  }
  const grid = document.getElementById('library-results-grid');
  const emptyState = document.getElementById('library-empty-state');
  if (!grid) return;
  grid.innerHTML = '';

  const favChannels = state.channels ? state.channels.filter(ch => state.favorites.includes(ch.id)) : [];
  const favVOD = state.watchlist || [];
  const favPodcasts = state.favoritePodcasts || [];

  const totalFavs = favChannels.length + favVOD.length + favPodcasts.length;

  if (totalFavs === 0) {
    if (emptyState) emptyState.classList.remove('hidden');
    return;
  }
  if (emptyState) emptyState.classList.add('hidden');

  // 1. Render VOD Favorites (Movies & TV)
  favVOD.forEach(item => {
    const title = item.title || item.name || 'Untitled';
    const isTV = item.media_type === 'tv';
    const year = (item.release_date || item.first_air_date || '').split('-')[0] || 'N/A';
    const rating = item.vote_average ? item.vote_average.toFixed(1) : 'N/A';
    const posterPath = getTMDBImageUrl(item.poster_path, 'w185') || 'https://via.placeholder.com/185x278/090e1a/475569?text=No+Poster';

    const card = document.createElement('div');
    card.className = 'detail-item-card hover-scale cursor-pointer relative';
    card.innerHTML = `
      <button class="card-remove-btn" title="Remove from Watchlist">
        <i data-lucide="trash-2" class="w-3.5 h-3.5"></i>
      </button>
      <img src="${posterPath}" alt="${title}" class="detail-item-poster" loading="lazy">
      <div class="detail-item-info">
        <h4 class="detail-item-title">${title}</h4>
        <div class="detail-item-meta">
          <span class="detail-item-year">${year} • ${isTV ? 'TV' : 'Movie'}</span>
          <span class="detail-item-rating flex items-center gap-1">
            <i data-lucide="star" class="w-3 h-3 fill-amber-400 text-amber-400"></i> ${rating}
          </span>
        </div>
      </div>
    `;

    const delBtn = card.querySelector('.card-remove-btn');
    if (delBtn) {
      delBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        e.preventDefault();
        removeFromWatchlist(item.id, isTV ? 'tv' : 'movie');
      });
    }

    card.addEventListener('click', () => openDetailsView(item));
    grid.appendChild(card);
  });

  // 2. Render Favorite Live TV Channels
  favChannels.forEach(channel => {
    const card = document.createElement('div');
    card.className = 'detail-item-card hover-scale cursor-pointer border border-amber-500/20 relative';
    card.innerHTML = `
      <button class="card-remove-btn" title="Remove from Saved Channels">
        <i data-lucide="trash-2" class="w-3.5 h-3.5"></i>
      </button>
      <div class="w-full h-36 bg-slate-900/80 flex items-center justify-center p-3 relative rounded-t-xl overflow-hidden">
        ${channel.logo ? `<img src="${channel.logo}" alt="${channel.name}" class="max-h-full max-w-full object-contain" loading="lazy" onerror="this.style.display='none'">` : `<i data-lucide="tv" class="w-10 h-10 text-amber-400/60"></i>`}
        <span class="absolute top-2 left-2 bg-amber-500/20 text-amber-400 text-[10px] font-bold px-2 py-0.5 rounded-full border border-amber-500/40">LIVE</span>
      </div>
      <div class="detail-item-info">
        <h4 class="detail-item-title truncate">${channel.name}</h4>
        <div class="detail-item-meta">
          <span class="detail-item-year">${channel.group || 'Live TV'}</span>
          <span class="detail-item-rating flex items-center gap-1 text-amber-400 font-semibold">
            <i data-lucide="play" class="w-3 h-3 fill-amber-400"></i> Play Channel
          </span>
        </div>
      </div>
    `;

    const delBtn = card.querySelector('.card-remove-btn');
    if (delBtn) {
      delBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        e.preventDefault();
        toggleFavorite(channel.id);
        renderLibraryScreen();
      });
    }

    card.addEventListener('click', () => {
      switchTab('live');
      playChannel(channel);
    });
    grid.appendChild(card);
  });

  // 3. Render Favorite Podcasts
  favPodcasts.forEach(podcast => {
    const card = document.createElement('div');
    card.className = 'detail-item-card hover-scale cursor-pointer border border-purple-500/20 relative';
    card.innerHTML = `
      <button class="card-remove-btn" title="Remove from Saved Podcasts">
        <i data-lucide="trash-2" class="w-3.5 h-3.5"></i>
      </button>
      <div class="w-full h-36 bg-slate-900/80 flex items-center justify-center p-3 relative rounded-t-xl overflow-hidden">
        ${podcast.image || podcast.artwork ? `<img src="${podcast.image || podcast.artwork}" alt="${podcast.title}" class="max-h-full max-w-full object-contain" loading="lazy">` : `<i data-lucide="radio" class="w-10 h-10 text-purple-400/60"></i>`}
        <span class="absolute top-2 left-2 bg-purple-500/20 text-purple-400 text-[10px] font-bold px-2 py-0.5 rounded-full border border-purple-500/40">PODCAST</span>
      </div>
      <div class="detail-item-info">
        <h4 class="detail-item-title truncate">${podcast.title || podcast.name}</h4>
        <div class="detail-item-meta">
          <span class="detail-item-year">${podcast.author || 'Podcast'}</span>
          <span class="detail-item-rating flex items-center gap-1 text-purple-400 font-semibold">
            <i data-lucide="play" class="w-3 h-3 fill-purple-400"></i> Play Audio
          </span>
        </div>
      </div>
    `;

    const delBtn = card.querySelector('.card-remove-btn');
    if (delBtn) {
      delBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        e.preventDefault();
        if (typeof togglePodcastFavorite === 'function') {
          togglePodcastFavorite(podcast);
          renderLibraryScreen();
        }
      });
    }

    card.addEventListener('click', () => {
      switchTab('podcasts');
      if (typeof playPodcastChannel === 'function') {
        playPodcastChannel(podcast);
      }
    });
    grid.appendChild(card);
  });

  createIcons(iconConfig);
}

// Setup VOD Details modal
function setupDetailsView() {
  const closeBtn = document.getElementById('details-close-btn');
  if (closeBtn) closeBtn.addEventListener('click', closeDetailsView);

  const watchlistBtn = document.getElementById('watchlist-toggle-btn');
  if (watchlistBtn) {
    watchlistBtn.addEventListener('click', toggleWatchlist);
  }

  const seasonSelect = document.getElementById('season-select');
  if (seasonSelect) {
    seasonSelect.addEventListener('change', (e) => {
      if (state.selectedMedia && state.selectedMedia.media_type === 'tv') {
        loadTVEpisodes(state.selectedMedia.id, e.target.value);
      }
    });
  }
}

// Toggle VOD watchlist item
function toggleWatchlist() {
  if (!state.selectedMedia) return;
  const index = state.watchlist.findIndex(x => Number(x.id) === Number(state.selectedMedia.id) && x.media_type === state.selectedMedia.media_type);
  
  if (index === -1) {
    // Add to watchlist
    state.watchlist.unshift({
      id: state.selectedMedia.id,
      media_type: state.selectedMedia.media_type,
      title: state.selectedMedia.title,
      name: state.selectedMedia.name,
      release_date: state.selectedMedia.release_date,
      first_air_date: state.selectedMedia.first_air_date,
      vote_average: state.selectedMedia.vote_average,
      poster_path: state.selectedMedia.poster_path,
      backdrop_path: state.selectedMedia.backdrop_path,
      overview: state.selectedMedia.overview
    });
    if (typeof player !== 'undefined' && player && typeof player.showToast === 'function') {
      player.showToast("Added to Watchlist");
    }
  } else {
    // Remove from watchlist
    state.watchlist.splice(index, 1);
    if (typeof player !== 'undefined' && player && typeof player.showToast === 'function') {
      player.showToast("Removed from Watchlist");
    }
  }

  localStorage.setItem('vod_watchlist', JSON.stringify(state.watchlist));
  renderWatchlistButtonState();
  loadMoviesDashboard();
  loadSeriesDashboard();
  
  // Refresh library list if active
  if (state.activeTab === 'library') {
    renderLibraryScreen();
  }
}

// Remove item from VOD watchlist
function removeFromWatchlist(id, mediaType) {
  const targetId = Number(id) || id;
  const index = state.watchlist.findIndex(x => Number(x.id) === targetId && (x.media_type === mediaType || (!x.media_type && mediaType === 'movie')));
  if (index >= 0) {
    state.watchlist.splice(index, 1);
    localStorage.setItem('vod_watchlist', JSON.stringify(state.watchlist));
    if (typeof player !== 'undefined' && player && typeof player.showToast === 'function') {
      player.showToast("Removed from Watchlist");
    }
    loadMoviesDashboard();
    loadSeriesDashboard();
    if (state.activeTab === 'library') {
      renderLibraryScreen();
    }
  }
}

// Show active/inactive watchlist button state
function renderWatchlistButtonState() {
  const icon = document.getElementById('watchlist-icon');
  const label = document.getElementById('watchlist-text');
  if (!icon || !label || !state.selectedMedia) return;

  const inWatchlist = state.watchlist.some(x => x.id === state.selectedMedia.id && x.media_type === state.selectedMedia.media_type);
  if (inWatchlist) {
    icon.setAttribute('class', 'w-4 h-4 fill-amber-400 text-amber-400');
    label.textContent = 'In Watchlist';
  } else {
    icon.setAttribute('class', 'w-4 h-4 text-slate-400');
    label.textContent = 'Add to Watchlist';
  }
  createIcons(iconConfig);
}

// Open detailed info for selected Movie / TV Show
async function openDetailsView(mediaItem) {
  if (!isLiveTvActive()) {
    if (typeof player !== 'undefined' && player && typeof player.showToast === 'function') {
      player.showToast("Sign in with active Live TV credentials to unlock VOD");
    }
    switchTab('settings');
    return;
  }
  try {
    console.log("[openDetailsView] mediaItem:", mediaItem);
    stopActiveLiveTVFeed();
    resetPlayerWindow();
    state.selectedMedia = mediaItem;
    
    // Render details overlay container
    const overlay = document.getElementById('details-overlay');
    if (overlay) {
      overlay.classList.remove('hidden');
      overlay.scrollTop = 0;
      document.body.style.overflow = 'hidden';
    }

    // Move player container from hidden holder to details container
    const playerSection = document.getElementById('player-section');
    const vodContainer = document.getElementById('vod-player-container');
    if (playerSection && vodContainer) {
      vodContainer.appendChild(playerSection);
    }

    renderWatchlistButtonState();

    // Setup placeholder details
    const title = mediaItem.title || mediaItem.name || 'Loading...';
    document.getElementById('details-title').textContent = title;
    const rawDate = String(mediaItem.release_date || mediaItem.first_air_date || 'N/A');
    document.getElementById('details-year').textContent = rawDate.split('-')[0];
    document.getElementById('details-rating').innerHTML = `⭐ ${mediaItem.vote_average ? mediaItem.vote_average.toFixed(1) : 'N/A'}`;
    document.getElementById('details-runtime').textContent = '';
    document.getElementById('details-overview').textContent = mediaItem.overview || 'Synopsis loading...';
    const sourcesPanel = document.querySelector('.sources-panel');
    if (sourcesPanel) sourcesPanel.style.display = '';

    document.getElementById('tv-selectors').classList.add('hidden');

    const backdropBg = document.getElementById('details-backdrop');
    const backdropUrl = getTMDBImageUrl(mediaItem.backdrop_path, 'w1280') || getTMDBImageUrl(mediaItem.poster_path, 'w1280');
    if (backdropBg && backdropUrl) {
      backdropBg.style.backgroundImage = `url(${backdropUrl})`;
    } else if (backdropBg) {
      backdropBg.style.backgroundImage = '';
    }

    // Reset stream sources
    document.getElementById('sources-list').innerHTML = '';
    document.getElementById('sources-status').textContent = 'Choose a server to play.';
    closeActiveSse();
    renderWatchlistButtonState();

    // Reset player wrapper visual aspects (hide frame, show poster splash)
    const videoEl = document.getElementById('video-player');
    const embedWrapper = document.getElementById('embed-player-wrapper');
    const embedIframe = document.getElementById('embed-iframe');
    
    if (videoEl) {
      videoEl.style.display = 'none';
      videoEl.pause();
      videoEl.removeAttribute('src');
      videoEl.load();
    }
    if (embedWrapper) embedWrapper.style.display = 'none';
    if (embedIframe) embedIframe.src = '';
    
    // Show no active stream poster initially
    const poster = document.getElementById('player-poster');
    if (poster) {
      poster.style.display = 'flex';
      if (mediaItem.backdrop_path) {
        poster.style.backgroundImage = `url(${getTMDBImageUrl(mediaItem.backdrop_path, 'w780')})`;
      } else {
        poster.style.backgroundImage = '';
      }
    }

    // Set Seek controls for VOD VOD controls
    if (player && typeof player.setControlMode === 'function') {
      player.setControlMode('vod');
    }
    const seekContainer = document.getElementById('seek-container');
    if (seekContainer) seekContainer.style.display = 'flex';
    const liveIndicatorDot = document.getElementById('live-indicator-dot');
    const liveIndicatorText = document.getElementById('live-indicator-text');
    if (liveIndicatorDot) liveIndicatorDot.style.display = 'none';
    if (liveIndicatorText) liveIndicatorText.style.display = 'none';

    createIcons(iconConfig);

    // Check and show resume position banner for Movies
    const isTV = mediaItem.media_type === 'tv';
    if (!isTV) {
      const mediaKey = `movie_${mediaItem.id}`;
      state.currentVodMediaKey = mediaKey;
      state.currentVodTitle = title;
      checkAndShowVodResumeBanner(mediaKey, title, () => {
        startStreamResolution({ type: 'movie', id: mediaItem.id });
      });
    } else {
      document.getElementById('sources-status').textContent = 'Select an episode below to start streaming.';
    }

    if (isTV) {
      try {
        const details = await getTVShowDetails(mediaItem.id);
        document.getElementById('details-runtime').textContent = `${details?.number_of_seasons || 1} Seasons`;

        // Show TV selectors
        document.getElementById('tv-selectors').classList.remove('hidden');

        // Populate seasons dropdown list
        const seasonSelect = document.getElementById('season-select');
        seasonSelect.innerHTML = '';
        (details?.seasons || []).forEach(season => {
          if (season.season_number === 0 && (details?.seasons?.length || 0) > 1) return;
          const opt = document.createElement('option');
          opt.value = season.season_number;
          opt.textContent = season.name || `Season ${season.season_number}`;
          seasonSelect.appendChild(opt);
        });

        // Load episodes for the first season (auto-trigger Episode 1)
        if (details?.seasons && details.seasons.length > 0) {
          const firstSeasonNum = seasonSelect.options.length > 0 ? seasonSelect.options[0].value : 1;
          seasonSelect.value = firstSeasonNum;
          await loadTVEpisodes(mediaItem.id, firstSeasonNum);
        }
      } catch (e) {
        console.warn("Failed to load TV details:", e);
      }
    } else {
      try {
        const details = await getMovieDetails(mediaItem.id);
        document.getElementById('details-runtime').textContent = `${details?.runtime || 'N/A'} min`;
      } catch (e) {
        console.warn("Failed to load movie runtime details:", e);
      }
    }
  } catch (err) {
    console.error("[openDetailsView] Error opening details view:", err);
  }
}

// Load episodes list grid for TV Show (auto-selects Episode 1)
async function loadTVEpisodes(tvId, seasonNumber) {
  const grid = document.getElementById('episodes-grid');
  grid.innerHTML = '<span class="text-xs text-slate-500">Loading episodes...</span>';

  try {
    const data = await getTVSeasonDetails(tvId, seasonNumber);
    grid.innerHTML = '';
    
    const episodes = data.episodes || [];
    episodes.forEach((ep) => {
      const btn = document.createElement('button');
      btn.className = 'episode-btn';
      btn.textContent = ep.episode_number;
      btn.title = ep.name || `Episode ${ep.episode_number}`;
      
      btn.addEventListener('click', () => {
        document.querySelectorAll('.episode-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        
        const mediaKey = `tv_${tvId}_s${seasonNumber}_e${ep.episode_number}`;
        const title = `${state.selectedMedia?.name || 'TV Show'} S${seasonNumber} E${ep.episode_number}`;
        state.currentVodMediaKey = mediaKey;
        state.currentVodTitle = title;

        checkAndShowVodResumeBanner(mediaKey, title, () => {
          document.getElementById('sources-status').textContent = `Resolving Season ${seasonNumber} Episode ${ep.episode_number}...`;
          startStreamResolution({ type: 'tv', id: tvId, season: seasonNumber, episode: ep.episode_number });
        });
      });

      grid.appendChild(btn);
    });

    // Auto-select Episode 1 on load
    if (episodes.length > 0) {
      const firstEpBtn = grid.querySelector('.episode-btn');
      if (firstEpBtn) {
        firstEpBtn.click();
      }
    }
  } catch (err) {
    console.error("Failed to load TV episodes:", err);
    grid.innerHTML = '<span class="text-xs text-red-400">Failed to load episodes.</span>';
  }
}



// Start streaming resolution queries
function startStreamResolution({ type, id, season = 1, episode = 1 }) {
  closeActiveSse();

  const listContainer = document.getElementById('sources-list');
  const statusText = document.getElementById('sources-status');
  const embedWrapper = document.getElementById('embed-player-wrapper');
  const embedIframe = document.getElementById('embed-iframe');

  listContainer.innerHTML = '';
  state.resolvedSources = [];
  state.activeSourceIndex = -1;
  if (embedWrapper) embedWrapper.style.display = 'none';
  if (embedIframe) embedIframe.src = '';

  statusText.textContent = 'Resolving streams...';

  // Populate fallback embed servers
  EMBED_PROVIDERS.forEach((provider) => {
    const embedUrl = type === 'tv'
      ? provider.tv(id, season, episode)
      : provider.movie(id);

    state.resolvedSources.push({ name: provider.name, url: embedUrl, type: 'embed' });
  });

  renderSourcesUI();
  if (state.resolvedSources.length > 0) {
    selectActiveSource(0);
  }

  // Trigger HuggingFace Spaces Vyla stream API resolver in background
  state.activeStreamSse = getStreamSources(
    { type, id, season, episode },
    {
      onWaking: () => {
        statusText.textContent = 'Streaming server waking, resolving direct links...';
      },
      onMeta: (meta) => {
        console.log('[Vyla] Meta resolved:', meta);
      },
      onSource: (source) => {
        console.log('[Vyla] Direct source resolved:', source);
        if (source && source.url) {
          const directSource = {
            name: source.name || 'Direct HD Server',
            url: source.url,
            type: 'stream'
          };
          
          state.resolvedSources.unshift(directSource);
          renderSourcesUI();
          
          if (state.activeSourceIndex === -1 || state.resolvedSources[state.activeSourceIndex]?.type === 'embed') {
            selectActiveSource(0);
          }
        }
      },
      onDone: () => {
        if (state.resolvedSources.some(s => s.type === 'stream')) {
          statusText.textContent = 'Direct streams ready. Choose a server to play.';
        } else {
          statusText.textContent = 'No direct streams found. Streaming via fallback embed.';
        }
      },
      onError: (err) => {
        console.error('[Vyla] SSE resolution error:', err);
        statusText.textContent = 'Using embed servers.';
      }
    }
  );
}

// Render the source selector buttons list
function renderSourcesUI() {
  const listContainer = document.getElementById('sources-list');
  if (!listContainer) return;
  listContainer.innerHTML = '';

  state.resolvedSources.forEach((source, index) => {
    const btn = document.createElement('button');
    btn.className = `source-item-btn ${index === state.activeSourceIndex ? 'active' : ''}`;
    btn.innerHTML = `
      <span>${source.name}</span>
      <span class="source-item-badge">${source.type === 'stream' ? 'Direct HD' : 'Embed'}</span>
    `;
    btn.addEventListener('click', () => selectActiveSource(index));
    listContainer.appendChild(btn);
  });
}

let shieldTimer = null;

// Automatically enter fullscreen when VOD stream playback begins
function requestAutoFullscreen() {
  try {
    const elem = document.querySelector('.player-wrapper') || document.getElementById('player-section') || document.documentElement;
    if (elem) {
      if (elem.requestFullscreen) {
        elem.requestFullscreen().catch(() => {});
      } else if (elem.webkitRequestFullscreen) {
        elem.webkitRequestFullscreen();
      } else if (elem.msRequestFullscreen) {
        elem.msRequestFullscreen();
      }
    }
  } catch (e) {}
}

// Load server source in the shared player
function selectActiveSource(index) {
  if (index < 0 || index >= state.resolvedSources.length) return;

  state.activeSourceIndex = index;
  const source = state.resolvedSources[index];

  // Auto enter fullscreen at beginning of VOD play
  requestAutoFullscreen();

  if (player && typeof player.setControlMode === 'function') {
    player.setControlMode('vod');
  }

  renderSourcesUI();

  const embedIframe = document.getElementById('embed-iframe');
  const embedWrapper = document.getElementById('embed-player-wrapper');
  const videoEl = document.getElementById('video-player');
  const playerWrapper = document.querySelector('.player-wrapper');
  const shield = document.getElementById('embed-shield');
  const poster = document.getElementById('player-poster');

  // Stop video element playback and reset it without closing VOD modal
  stopAllMediaPlayback({ keepVod: true });
  if (videoEl) {
    videoEl.pause();
    videoEl.removeAttribute('src');
    videoEl.load();
  }

  // Hide splash poster & connection error overlay once we select a source
  if (poster) poster.style.display = 'none';
  const playerErr = document.getElementById('player-error');
  if (playerErr) playerErr.classList.add('hidden');

  // Start active VOD tracking timer
  startVodPlaybackTracking();

  if (source.type === 'stream') {
    if (embedIframe) embedIframe.src = '';
    if (embedWrapper) embedWrapper.style.display = 'none';
    if (videoEl) videoEl.style.display = '';
    if (playerWrapper) playerWrapper.classList.remove('embed-active');
    if (shield) shield.style.display = 'none';

    player.playChannel({
      url: source.url,
      name: state.selectedMedia ? (state.selectedMedia.title || state.selectedMedia.name) : 'VOD Stream',
      mediaKey: state.currentVodMediaKey
    }, state.resumePlaybackTime);
  } else {
    // Cleanly reset previous video frame
    if (player && typeof player.resetVideoFrame === 'function') {
      player.resetVideoFrame();
    }

    if (videoEl) videoEl.style.display = 'none';
    if (embedWrapper) embedWrapper.style.display = 'block';
    if (playerWrapper) playerWrapper.classList.add('embed-active');
    if (embedIframe) {
      embedIframe.src = 'about:blank';
      embedIframe.setAttribute('allow', 'autoplay *; fullscreen *; picture-in-picture *; encrypted-media *; accelerometer; gyroscope; web-share; audio *');
      embedIframe.removeAttribute('referrerpolicy');
      embedIframe.removeAttribute('sandbox');
      
      // Override window.open on main window to defeat pop-up escapes
      window.open = function() {
        console.log('[Popup Shield] Pop-up window open blocked.');
        return null;
      };

      let embedUrl = source.url;
      const sep = embedUrl.includes('?') ? '&' : '?';
      embedUrl += `${sep}autoplay=true&autoplay=1&muted=false&muted=0&volume=100`;

      if (state.resumePlaybackTime && state.resumePlaybackTime > 10) {
        const t = Math.floor(state.resumePlaybackTime);
        embedUrl += `&t=${t}&start=${t}&time=${t}&progress=${t}#t=${t}`;
      }

      setTimeout(() => {
        embedIframe.src = embedUrl;
      }, 50);
    }

    // Un-shield embed player to allow direct control interaction
    if (shield) {
      shield.style.display = 'none';
    }
  }

  const statusText = document.getElementById('sources-status');
  if (statusText) {
    if (source.type === 'stream') {
      statusText.textContent = `Streaming direct source via ${source.name}. Use player controls below.`;
    } else {
      statusText.textContent = `Streaming via ${source.name}. Use player controls on video.`;
    }
  }
}

// Close active stream EventSource connection
function closeActiveSse() {
  if (state.activeStreamSse) {
    console.log('Closing VOD stream resolver');
    if (typeof state.activeStreamSse.cancel === 'function') {
      state.activeStreamSse.cancel();
    } else if (typeof state.activeStreamSse.close === 'function') {
      state.activeStreamSse.close();
    }
    state.activeStreamSse = null;
  }
}
function getVodMediaKey(type, id, season = 1, episode = 1) {
  if (type === 'movie') return `movie_${id}`;
  return `tv_${id}_s${season}_e${episode}`;
}

// Format seconds into readable string (e.g. 1h 24m or 18:45)
function formatPlaybackTime(seconds) {
  if (!seconds || isNaN(seconds) || seconds <= 0) return '0:00';
  const hrs = Math.floor(seconds / 3600);
  const mins = Math.floor((seconds % 3600) / 60);
  const secs = Math.floor(seconds % 60);
  if (hrs > 0) {
    return `${hrs}h ${mins}m ${secs}s`;
  }
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

// Retrieve saved VOD playback position
function getVodPlaybackPosition(mediaKey) {
  try {
    const data = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
    return data[mediaKey] || null;
  } catch (e) {
    return null;
  }
}

// Save VOD playback position
function saveVodPlaybackPosition(mediaKey, seconds, duration = 0, title = '') {
  if (!mediaKey || isNaN(seconds) || seconds < 5) return;
  try {
    const data = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
    if (duration > 0 && seconds / duration > 0.95) {
      delete data[mediaKey];
    } else {
      data[mediaKey] = {
        position: Math.floor(seconds),
        duration: Math.floor(duration),
        timestamp: Date.now(),
        title: title
      };
    }
    localStorage.setItem('vod_resume_positions', JSON.stringify(data));
  } catch (e) {
    console.warn("Failed to save VOD resume position:", e);
  }
}

// Continuous VOD Playback Position Tracking
function startVodPlaybackTracking() {
  stopVodPlaybackTracking();
  state.vodPlayStartTime = Date.now();

  state.vodSaveInterval = setInterval(() => {
    saveCurrentVodPosition();
  }, 3000);
}

function stopVodPlaybackTracking() {
  if (state.vodSaveInterval) {
    clearInterval(state.vodSaveInterval);
    state.vodSaveInterval = null;
  }
}

function saveCurrentVodPosition() {
  if (!state.currentVodMediaKey) return;
  
  const videoEl = document.getElementById('video-player');
  let playedPos = 0;
  let totalDur = 0;

  if (videoEl && videoEl.style.display !== 'none' && videoEl.currentTime > 5) {
    playedPos = videoEl.currentTime;
    totalDur = videoEl.duration || 0;
  } else if (state.vodPlayStartTime) {
    const elapsed = (Date.now() - state.vodPlayStartTime) / 1000;
    if (elapsed > 5) {
      playedPos = (state.resumePlaybackTime || 0) + elapsed;
    }
  }

  if (playedPos > 10) {
    saveVodPlaybackPosition(state.currentVodMediaKey, playedPos, totalDur, state.currentVodTitle || 'VOD Item');
  }
}

// Window unload & visibility listeners for saving progress
window.addEventListener('beforeunload', () => saveCurrentVodPosition());
document.addEventListener('visibilitychange', () => {
  if (document.hidden) saveCurrentVodPosition();
});

// Delete saved VOD playback position
function deleteVodPlaybackPosition(mediaKey) {
  if (!mediaKey) return;
  try {
    const data = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
    delete data[mediaKey];
    localStorage.setItem('vod_resume_positions', JSON.stringify(data));
  } catch (e) {}
}

// Display resume choice modal if previous incomplete playback exists
function checkAndShowVodResumeBanner(mediaKey, title, onChoice) {
  const saved = getVodPlaybackPosition(mediaKey);
  if (saved && saved.position > 10) {
    const modal = document.getElementById('vod-resume-modal');
    const titleEl = document.getElementById('resume-modal-title');
    const timeEl = document.getElementById('resume-modal-time');
    const btnTextEl = document.getElementById('resume-modal-btn-text');
    const resumeBtn = document.getElementById('resume-modal-resume-btn');
    const startOverBtn = document.getElementById('resume-modal-start-over-btn');

    if (modal && resumeBtn && startOverBtn) {
      const formatted = formatPlaybackTime(saved.position);
      if (titleEl) titleEl.textContent = title;
      if (timeEl) timeEl.textContent = formatted;
      if (btnTextEl) btnTextEl.textContent = `Resume at ${formatted}`;

      modal.classList.remove('hidden');
      createIcons(iconConfig);

      const handleResume = () => {
        modal.classList.add('hidden');
        cleanup();
        state.resumePlaybackTime = saved.position;
        if (typeof onChoice === 'function') onChoice(saved.position);
      };

      const handleRestart = () => {
        modal.classList.add('hidden');
        cleanup();
        state.resumePlaybackTime = 0;
        deleteVodPlaybackPosition(mediaKey);
        if (typeof onChoice === 'function') onChoice(0);
      };

      const cleanup = () => {
        resumeBtn.removeEventListener('click', handleResume);
        startOverBtn.removeEventListener('click', handleRestart);
      };

      resumeBtn.addEventListener('click', handleResume);
      startOverBtn.addEventListener('click', handleRestart);

      return true; // True indicates modal was presented
    }
  }

  state.resumePlaybackTime = 0;
  if (typeof onChoice === 'function') onChoice(0);
  return false;
}

// Close details overlay modal
function closeDetailsView() {
  saveCurrentVodPosition();
  stopVodPlaybackTracking();
  closeActiveSse();

  state.selectedMedia = null;
  state.resolvedSources = [];
  state.currentVodMediaKey = null;
  state.currentVodTitle = null;
  state.vodPlayStartTime = null;
  state.resumePlaybackTime = 0;

  const embedIframe = document.getElementById('embed-iframe');
  const embedWrapper = document.getElementById('embed-player-wrapper');
  const playerWrapper = document.querySelector('.player-wrapper');
  const playerErr = document.getElementById('player-error');
  if (playerErr) playerErr.classList.add('hidden');

  if (embedIframe) embedIframe.src = 'about:blank';
  if (embedWrapper) embedWrapper.style.display = 'none';
  if (playerWrapper) playerWrapper.classList.remove('embed-active');

  if (player && typeof player.resetVideoFrame === 'function') {
    player.resetVideoFrame();
  } else if (videoEl) {
    videoEl.pause();
    videoEl.removeAttribute('src');
    videoEl.load();
    videoEl.style.display = '';
  }

  // Restore live indicators
  const liveDot = document.getElementById('live-indicator-dot');
  const liveText = document.getElementById('live-indicator-text');
  const seekContainer = document.getElementById('seek-container');
  if (liveDot) liveDot.style.display = '';
  if (liveText) liveText.style.display = '';
  if (seekContainer) seekContainer.style.display = 'none';

  // Hide details panel modal
  const overlay = document.getElementById('details-overlay');
  if (overlay) {
    overlay.classList.add('hidden');
  }
  document.body.style.overflow = '';

  // Relocate player to live container or hidden holder
  const playerSection = document.getElementById('player-section');
  const liveContainer = document.getElementById('live-player-container');
  const holder = document.getElementById('shared-player-holder');
  if (playerSection) {
    if (state.activeTab === 'live' && liveContainer) {
      liveContainer.appendChild(playerSection);
    } else if (holder) {
      holder.appendChild(playerSection);
    }
  }
}

function getProxyBase() {
  let saved = '';
  try {
    saved = (localStorage.getItem('external_proxy_url') || '').trim();
  } catch (e) {}
  if (saved) return saved;

  if (
    typeof window !== 'undefined' &&
    (window.location.host === 'appassets.androidplatform.net' ||
      window.location.protocol === 'file:' ||
      (navigator.userAgent && navigator.userAgent.includes('JoyfulIPTVMobileApp')))
  ) {
    return 'https://tv-dinner-proxy.tahillinvestments.workers.dev/';
  }

  return DEFAULT_RENDER_PROXY;
}

/**
 * getProxyUrl - routes all HTTP requests through Vercel or Cloudflare proxy
 */
function getProxyUrl(targetUrl) {
  if (!targetUrl) return '';

  let cleanTarget = targetUrl;
  if (targetUrl.includes('url=')) {
    try {
      const parts = targetUrl.split('url=');
      const extracted = decodeURIComponent(parts[parts.length - 1]);
      if (extracted.startsWith('http://') || extracted.startsWith('https://')) {
        cleanTarget = extracted;
      }
    } catch (e) {}
  }

  const baseProxy = getProxyBase();
  if (baseProxy.startsWith('http://') || baseProxy.startsWith('https://')) {
    const p = baseProxy.endsWith('/') ? baseProxy : baseProxy + '/';
    return `${p}?url=${encodeURIComponent(cleanTarget)}`;
  }

  return `${baseProxy}?url=${encodeURIComponent(cleanTarget)}`;
}

function renderUnactivatedState() {
  const headerBadge = document.getElementById('channel-count-header');
  if (headerBadge) headerBadge.textContent = 'Sign In Required';

  const homeStatusText = document.getElementById('home-status-text');
  if (homeStatusText) {
    homeStatusText.textContent = 'Sign In Required';
  }

  const catContainer = document.getElementById('categories-container');
  const catName = document.getElementById('current-category-name');
  const catInfo = document.getElementById('channel-list-info');
  const grid = document.getElementById('channels-grid');

  if (catName) catName.textContent = 'Live TV (Sign In Required)';
  if (catInfo) catInfo.textContent = 'Sign in with your credentials or 10-digit phone number in Settings to start streaming.';

  if (catContainer) {
    catContainer.innerHTML = `
      <button class="category-btn active">
        <i data-lucide="lock" class="w-4 h-4 text-amber-400"></i>
        <span class="cat-name">Sign In Required</span>
      </button>
    `;
  }

  if (grid && state.activeTab === 'live') {
    grid.innerHTML = `
      <div class="col-span-full py-16 px-6 text-center bg-slate-800/40 rounded-2xl border border-slate-700/60 shadow-xl max-w-md mx-auto my-8">
        <div class="w-16 h-16 mx-auto rounded-2xl bg-amber-500/10 border border-amber-500/30 flex items-center justify-center text-amber-400 mb-4 shadow-inner">
          <i data-lucide="lock" class="w-8 h-8"></i>
        </div>
        <h3 class="text-lg font-bold text-white mb-2">Account Sign In Required</h3>
        <p class="text-sm text-slate-300 mb-6 leading-relaxed">
          Please sign in with your account credentials or 10-digit phone number in Settings to unlock and watch Live TV channels.
        </p>
        <button id="go-to-settings-btn" class="btn btn-primary mt-4 font-bold shadow-lg shadow-red-900/30 flex items-center gap-2">
          <i data-lucide="log-in" class="w-4 h-4"></i> Go to Sign In / Settings
        </button>
      </div>
    `;
    createIcons(iconConfig);

    const btn = document.getElementById('go-to-settings-btn');
    if (btn) {
      btn.addEventListener('click', () => {
        switchTab('settings');
      });
    }
  }
}

// Load settings form credentials
function setupSettingsScreen() {
  const tmdbKeyInput = document.getElementById('settings-tmdb-key');
  const sandboxInput = document.getElementById('settings-strict-sandbox');
  const usernameInput = document.getElementById('settings-iptv-username');
  const passwordInput = document.getElementById('settings-iptv-password');
  const proxyInput = document.getElementById('settings-custom-proxy');
  const saveBtn = document.getElementById('settings-save-btn');

  // Phone Auth & Admin Panel Elements
  const phoneInput = document.getElementById('settings-phone-number');
  const activatePhoneBtn = document.getElementById('activate-phone-btn');
  const phoneActivationSection = document.getElementById('phone-activation-section');
  const activatedIndicatorSection = document.getElementById('activated-indicator-section');
  const signOutBtn = document.getElementById('sign-out-btn');

  const adminToggleHeader = document.getElementById('admin-toggle-header');
  const adminPanelContainer = document.getElementById('admin-panel-container');
  const adminPasswordInput = document.getElementById('admin-password');
  const adminLoginBtn = document.getElementById('admin-login-btn');
  const adminLoginSection = document.getElementById('admin-login-section');
  const adminDashboardSection = document.getElementById('admin-dashboard-section');
  const adminCredentialsTable = document.getElementById('admin-credentials-table');

  // Admin Credentials Storage & CRUD
  const DEFAULT_ADMIN_CREDENTIALS = [
    { phone: '123-456-7894', user: 'SGmUC7q2U', pswd: '4WM9WVsjG' },
    { phone: '317-363-1751', user: 'MW2Y2h6e7', pswd: '5DwU7wTuA' },
    { phone: '317-900-3473', user: 'Hn9a6bus9', pswd: 'JaKXrfMP7' },
    { phone: '317-902-1240', user: 'TONE01', pswd: 'TV4LIFE' },
    { phone: '317-795-7627', user: 'SAPPTV12', pswd: 'REMOTE6202' },
    { phone: '317-261-1596', user: 'DAMETV', pswd: '2611596317' }
  ];

  function getAdminCredentials() {
    const raw = localStorage.getItem('admin_iptv_credentials');
    if (!raw) {
      localStorage.setItem('admin_iptv_credentials', JSON.stringify(DEFAULT_ADMIN_CREDENTIALS));
      return DEFAULT_ADMIN_CREDENTIALS;
    }
    try {
      const list = JSON.parse(raw);
      return Array.isArray(list) ? list : DEFAULT_ADMIN_CREDENTIALS;
    } catch (e) {
      return DEFAULT_ADMIN_CREDENTIALS;
    }
  }

  function saveAdminCredentials(list) {
    localStorage.setItem('admin_iptv_credentials', JSON.stringify(list));
  }

  // ── Bandwidth / Usage Tracking ─────────────────────────────────────────────
  const BW_KEY = 'vercel_bw_stats';
  const BW_MONTHLY_LIMIT_GB = 100;
  const BW_LIMIT_BYTES = BW_MONTHLY_LIMIT_GB * 1024 * 1024 * 1024;

  function getBWStats() {
    try {
      const raw = localStorage.getItem(BW_KEY);
      if (raw) return JSON.parse(raw);
    } catch (e) {}
    return {
      bytesUsed: 0,
      streamRequests: 0,
      apiCalls: 0,
      resetDate: new Date().toISOString()
    };
  }

  function saveBWStats(stats) {
    localStorage.setItem(BW_KEY, JSON.stringify(stats));
  }

  function recordProxyUsage(bytes, isStream) {
    const stats = getBWStats();
    stats.bytesUsed += bytes;
    if (isStream) {
      stats.streamRequests = (stats.streamRequests || 0) + 1;
    } else {
      stats.apiCalls = (stats.apiCalls || 0) + 1;
    }
    saveBWStats(stats);
    updateBWDisplay(stats);
  }

  function formatBytes(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  }

  function updateBWDisplay(stats) {
    const usedLabel = document.getElementById('bw-used-label');
    const progressBar = document.getElementById('bw-progress-bar');
    const pctLabel = document.getElementById('bw-pct-label');
    const remainingLabel = document.getElementById('bw-remaining-label');
    const streamReqs = document.getElementById('bw-stream-requests');
    const apiCallsEl = document.getElementById('bw-api-calls');
    const watchTimeEl = document.getElementById('bw-watch-time');
    const resetDateEl = document.getElementById('bw-reset-date');
    if (!usedLabel) return;

    const used = stats.bytesUsed || 0;
    const pct = Math.min((used / BW_LIMIT_BYTES) * 100, 100);
    const remaining = Math.max(BW_LIMIT_BYTES - used, 0);

    // Estimate watch time: avg .ts segment ~3MB, ~4 segments/min = ~12MB/min
    const estMinutes = Math.round(used / (12 * 1024 * 1024));
    const watchStr = estMinutes >= 60
      ? `${Math.floor(estMinutes / 60)}h ${estMinutes % 60}m`
      : `${estMinutes} min`;

    usedLabel.textContent = formatBytes(used);
    progressBar.style.width = `${pct.toFixed(2)}%`;

    // Color shift: green → yellow → red based on usage
    if (pct < 50) {
      progressBar.style.background = 'linear-gradient(90deg,#8b5cf6,#6366f1)';
    } else if (pct < 80) {
      progressBar.style.background = 'linear-gradient(90deg,#f59e0b,#d97706)';
    } else {
      progressBar.style.background = 'linear-gradient(90deg,#ef4444,#dc2626)';
    }

    pctLabel.textContent = `${pct.toFixed(1)}% used`;
    remainingLabel.textContent = `${formatBytes(remaining)} remaining`;
    if (streamReqs) streamReqs.textContent = (stats.streamRequests || 0).toLocaleString();
    if (apiCallsEl) apiCallsEl.textContent = (stats.apiCalls || 0).toLocaleString();
    if (watchTimeEl) watchTimeEl.textContent = watchStr;
    if (resetDateEl && stats.resetDate) {
      const d = new Date(stats.resetDate);
      resetDateEl.textContent = `Since ${d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}`;
    }
  }

  // Intercept fetch to track /api/proxy usage
  (function installBWTracker() {
    const _origFetch = window.fetch.bind(window);
    window.fetch = async function(input, init) {
      const url = typeof input === 'string' ? input : (input?.url || '');
      const isProxyCall = url.startsWith('/api/proxy');
      const isStream = isProxyCall && (url.includes('.ts') || url.includes('.m3u8') || url.includes('/live/'));

      const res = await _origFetch(input, init);

      if (isProxyCall) {
        const cl = res.headers.get('content-length');
        if (cl) {
          recordProxyUsage(parseInt(cl, 10), isStream);
        } else {
          // Estimate: stream segments ~3MB, API calls ~50KB
          recordProxyUsage(isStream ? 3 * 1024 * 1024 : 50 * 1024, isStream);
        }
      }
      return res;
    };
  })();

  // Reset button handler (wired after DOM ready)
  const bwResetBtn = document.getElementById('bw-reset-btn');
  if (bwResetBtn) {
    bwResetBtn.addEventListener('click', () => {
      saveBWStats({ bytesUsed: 0, streamRequests: 0, apiCalls: 0, resetDate: new Date().toISOString() });
      updateBWDisplay(getBWStats());
    });
  }

  // Initial display
  updateBWDisplay(getBWStats());

  function renderAdminTable() {
    if (!adminCredentialsTable) return;
    const credsList = getAdminCredentials();
    adminCredentialsTable.innerHTML = '';

    if (credsList.length === 0) {
      adminCredentialsTable.innerHTML = `
        <tr>
          <td colspan="4" class="py-8 px-4 text-center text-xs text-slate-400">
            No account credentials registered yet. Click "Add Account" above to create one.
          </td>
        </tr>
      `;
      return;
    }

    credsList.forEach((cred, idx) => {
      const tr = document.createElement('tr');
      tr.className = "hover:bg-slate-800/60 transition-colors group border-b border-slate-800/80";
      tr.innerHTML = `
        <td class="py-3 px-4 font-mono font-bold text-emerald-400 text-xs">
          <div class="flex items-center gap-2">
            <i data-lucide="phone" class="w-3.5 h-3.5 text-slate-400"></i> ${cred.phone}
          </div>
        </td>
        <td class="py-3 px-4">
          <span class="bg-slate-800/90 border border-slate-700/80 px-2.5 py-1 rounded-md text-blue-400 font-mono text-xs font-semibold inline-block">
            ${cred.user}
          </span>
        </td>
        <td class="py-3 px-4">
          <span class="bg-slate-800/90 border border-slate-700/80 px-2.5 py-1 rounded-md text-purple-400 font-mono text-xs font-semibold inline-block">
            ${cred.pswd}
          </span>
        </td>
        <td class="py-3 px-4 text-right">
          <div class="flex items-center justify-end gap-1.5">
            <button class="admin-edit-row-btn btn btn-indigo btn-sm" data-index="${idx}" title="Edit Account">
              <i data-lucide="edit-3" class="w-3.5 h-3.5"></i> Edit
            </button>
            <button class="admin-delete-row-btn btn btn-danger btn-sm" data-index="${idx}" title="Delete Account">
              <i data-lucide="trash-2" class="w-3.5 h-3.5"></i> Delete
            </button>
          </div>
        </td>
      `;
      adminCredentialsTable.appendChild(tr);
    });

    createIcons(iconConfig);

    // Attach edit / delete handlers
    adminCredentialsTable.querySelectorAll('.admin-edit-row-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const index = parseInt(btn.getAttribute('data-index'));
        openAdminModal(index);
      });
    });

    adminCredentialsTable.querySelectorAll('.admin-delete-row-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const index = parseInt(btn.getAttribute('data-index'));
        deleteAdminCredential(index);
      });
    });
  }

  // Admin Modal CRUD Logic
  const credModal = document.getElementById('admin-cred-modal');
  const modalTitle = document.getElementById('admin-modal-title');
  const modalForm = document.getElementById('admin-cred-form');
  const modalEditIndex = document.getElementById('admin-modal-edit-index');
  const modalPhone = document.getElementById('admin-modal-phone');
  const modalUser = document.getElementById('admin-modal-user');
  const modalPass = document.getElementById('admin-modal-pass');
  const modalCloseBtn = document.getElementById('admin-modal-close');
  const modalCancelBtn = document.getElementById('admin-modal-cancel');
  const adminAddBtn = document.getElementById('admin-add-btn');

  if (modalPhone) {
    modalPhone.addEventListener('input', function (e) {
      let x = e.target.value.replace(/\D/g, '').match(/(\d{0,3})(\d{0,3})(\d{0,4})/);
      e.target.value = !x[2] ? x[1] : '(' + x[1] + ') ' + x[2] + (x[3] ? '-' + x[3] : '');
    });
  }

  function openAdminModal(editIndex = -1) {
    if (!credModal) return;
    modalEditIndex.value = editIndex;
    
    if (editIndex >= 0) {
      const list = getAdminCredentials();
      const item = list[editIndex];
      if (item) {
        if (modalTitle) modalTitle.innerHTML = `<i data-lucide="edit-3" class="w-5 h-5 text-indigo-400"></i> Edit Account Credential`;
        if (modalPhone) modalPhone.value = item.phone;
        if (modalUser) modalUser.value = item.user;
        if (modalPass) modalPass.value = item.pswd;
      }
    } else {
      if (modalTitle) modalTitle.innerHTML = `<i data-lucide="user-plus" class="w-5 h-5 text-emerald-400"></i> Add Account Credential`;
      if (modalPhone) modalPhone.value = '';
      if (modalUser) modalUser.value = '';
      if (modalPass) modalPass.value = '';
    }
    
    createIcons(iconConfig);
    credModal.classList.remove('hidden');
  }

  function closeAdminModal() {
    if (credModal) credModal.classList.add('hidden');
  }

  if (adminAddBtn) {
    adminAddBtn.addEventListener('click', () => openAdminModal(-1));
  }
  if (modalCloseBtn) modalCloseBtn.addEventListener('click', closeAdminModal);
  if (modalCancelBtn) modalCancelBtn.addEventListener('click', closeAdminModal);

  if (modalForm) {
    modalForm.addEventListener('submit', (e) => {
      e.preventDefault();
      const stripped = modalPhone.value.replace(/\D/g, '');
      if (stripped.length !== 10) {
        player.showToast("Please enter a valid 10-digit phone number.");
        return;
      }

      const formattedPhone = `${stripped.substring(0, 3)}-${stripped.substring(3, 6)}-${stripped.substring(6, 10)}`;
      const userVal = modalUser.value.trim();
      const passVal = modalPass.value.trim();

      if (!userVal || !passVal) {
        player.showToast("Username and Password are required.");
        return;
      }

      const idx = parseInt(modalEditIndex.value);
      const list = getAdminCredentials();

      if (idx >= 0 && idx < list.length) {
        list[idx] = { phone: formattedPhone, user: userVal, pswd: passVal };
        player.showToast("Account updated successfully!");
      } else {
        list.push({ phone: formattedPhone, user: userVal, pswd: passVal });
        player.showToast("New account credential added!");
      }

      saveAdminCredentials(list);
      renderAdminTable();
      closeAdminModal();
    });
  }

  function deleteAdminCredential(index) {
    const list = getAdminCredentials();
    if (index >= 0 && index < list.length) {
      const item = list[index];
      if (confirm(`Are you sure you want to delete credential for ${item.phone}?`)) {
        list.splice(index, 1);
        saveAdminCredentials(list);
        renderAdminTable();
        player.showToast(`Deleted credential for ${item.phone}`);
      }
    }
  }

  // Phone Input Masking (US Format)
  if (phoneInput) {
    phoneInput.addEventListener('input', function (e) {
      let x = e.target.value.replace(/\D/g, '').match(/(\d{0,3})(\d{0,3})(\d{0,4})/);
      e.target.value = !x[2] ? x[1] : '(' + x[1] + ') ' + x[2] + (x[3] ? '-' + x[3] : '');
    });
  }

  // Check if phone is already activated
  const savedPhone = localStorage.getItem('activated_phone');
  if (savedPhone && phoneActivationSection && activatedIndicatorSection) {
    phoneActivationSection.classList.add('hidden');
    activatedIndicatorSection.classList.remove('hidden');
  }

  // Phone Activation Logic (queries dynamic admin credentials database)
  if (activatePhoneBtn) {
    activatePhoneBtn.addEventListener('click', () => {
      const phoneVal = phoneInput.value;
      const strippedPhone = phoneVal.replace(/\D/g, '');
      if (strippedPhone.length !== 10) {
        player.showToast("Please enter a valid 10-digit phone number.");
        return;
      }
      const formattedPhone = `${strippedPhone.substring(0, 3)}-${strippedPhone.substring(3, 6)}-${strippedPhone.substring(6, 10)}`;
      
      const adminList = getAdminCredentials();
      const creds = adminList.find(c => c.phone === formattedPhone);
      if (creds) {
        localStorage.setItem('activated_phone', formattedPhone);
        localStorage.setItem('iptv_username', creds.user);
        localStorage.setItem('iptv_password', creds.pswd);
        if (usernameInput) usernameInput.value = creds.user;
        if (passwordInput) passwordInput.value = creds.pswd;
        
        phoneActivationSection.classList.add('hidden');
        activatedIndicatorSection.classList.remove('hidden');
        player.showToast("Account Activated!");
        
        // Immediately refresh all dashboards to unlock Movies, Series, Podcasts, and Library without requiring page refresh
        loadMoviesDashboard();
        loadSeriesDashboard();
        loadPodcastsDashboard();
        renderLibraryScreen();

        // Reload IPTV channels with new credentials and switch to Live TV
        loadIPTVPlaylist();
        switchTab('live');
      } else {
        player.showToast("Phone number not recognized in activation table.");
      }
    });
  }

  // Sign Out Logic
  if (signOutBtn) {
    signOutBtn.addEventListener('click', () => {
      localStorage.removeItem('activated_phone');
      localStorage.removeItem('iptv_username');
      localStorage.removeItem('iptv_password');
      if (usernameInput) usernameInput.value = '';
      if (passwordInput) passwordInput.value = '';
      if (phoneInput) phoneInput.value = '';
      state.channels = [];
      
      stopAllMediaPlayback();

      activatedIndicatorSection.classList.add('hidden');
      phoneActivationSection.classList.remove('hidden');
      player.showToast("Signed out.");
      renderUnactivatedState();
    });
  }

  // Admin Toggle
  if (adminToggleHeader) {
    adminToggleHeader.addEventListener('click', () => {
      adminPanelContainer.classList.toggle('hidden');
    });
  }

  // Admin Login Logic
  if (adminLoginBtn) {
    adminLoginBtn.addEventListener('click', () => {
      if (adminPasswordInput.value === '5678721') {
        adminLoginSection.classList.add('hidden');
        adminDashboardSection.classList.remove('hidden');
        renderAdminTable();
        player.showToast("Admin access granted.");
      } else {
        player.showToast("Incorrect admin password.");
      }
    });
  }

  // Populate inputs on load
  const activeUser = (localStorage.getItem('iptv_username') || '').trim();
  const activePass = (localStorage.getItem('iptv_password') || '').trim();
  if (tmdbKeyInput) tmdbKeyInput.value = localStorage.getItem('tmdb_api_key') || '';
  if (sandboxInput) sandboxInput.checked = localStorage.getItem('strict_sandbox') === 'true';
  if (usernameInput) usernameInput.value = activeUser;
  if (passwordInput) passwordInput.value = activePass;
  if (proxyInput) proxyInput.value = localStorage.getItem('external_proxy_url') || DEFAULT_RENDER_PROXY;

  // Auto-save logic on input change
  if (tmdbKeyInput) {
    tmdbKeyInput.addEventListener('input', () => {
      const key = tmdbKeyInput.value.trim();
      if (key) {
        localStorage.setItem('tmdb_api_key', key);
      } else {
        localStorage.removeItem('tmdb_api_key');
      }
    });
  }

  if (sandboxInput) {
    sandboxInput.addEventListener('change', () => {
      localStorage.setItem('strict_sandbox', sandboxInput.checked ? 'true' : 'false');
    });
  }

  if (usernameInput) {
    usernameInput.addEventListener('change', () => {
      localStorage.setItem('iptv_username', usernameInput.value.trim());
      loadMoviesDashboard();
      loadSeriesDashboard();
      loadPodcastsDashboard();
      renderLibraryScreen();
    });
  }

  if (passwordInput) {
    passwordInput.addEventListener('change', () => {
      localStorage.setItem('iptv_password', passwordInput.value.trim());
      loadMoviesDashboard();
      loadSeriesDashboard();
      loadPodcastsDashboard();
      renderLibraryScreen();
    });
  }

  if (proxyInput) {
    proxyInput.addEventListener('change', () => {
      const oldProxy = localStorage.getItem('external_proxy_url');
      const newProxy = proxyInput.value.trim() || DEFAULT_RENDER_PROXY;
      localStorage.setItem('external_proxy_url', newProxy);

      const isAct = !!localStorage.getItem('activated_phone');
      if (isAct && newProxy !== oldProxy) {
        console.log("[IPTV] Proxy changed, reloading channels...");
        loadIPTVPlaylist();
      }
    });
  }
}

// Parallel proxy racer for sub-second IPTV channel and category data retrieval
async function fetchWithFallback(url) {
  const proxyEndpoints = [
    getProxyUrl(url),
    `https://corsproxy.io/?${encodeURIComponent(url)}`,
    `https://api.allorigins.win/raw?url=${encodeURIComponent(url)}`
  ];

  if (window.location.protocol === 'http:' || url.startsWith('https://')) {
    proxyEndpoints.unshift(url);
  }

  const fetchTask = async (pUrl) => {
    const res = await fetch(pUrl, { signal: AbortSignal.timeout(6000) });
    if (res && res.ok) return res;
    throw new Error('Proxy failed');
  };

  try {
    return await Promise.any(proxyEndpoints.map(p => fetchTask(p)));
  } catch (e) {
    console.warn('[Xtream] All parallel proxy fetches failed for:', url);
    return null;
  }
}

// Fetch Xtream playlist dynamically using user's active credentials
async function fetchXtreamPlaylist(portalUrl, username, password) {
  if (!username || !password) return '';
  console.log(`[Xtream] Fetching channel playlist for user "${username}"...`);

  const primaryApi = `${portalUrl}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&action=get_live_streams`;
  const primaryCat = `${portalUrl}/player_api.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&action=get_live_categories`;

  try {
    console.log('[Xtream] Fetching streams via multi-pass fetcher...');
    const [streamsRes, catsRes] = await Promise.all([
      fetchWithFallback(primaryApi),
      fetchWithFallback(primaryCat)
    ]);

    if (streamsRes && streamsRes.ok) {
      const streams = await streamsRes.json();
      if (Array.isArray(streams) && streams.length > 0) {
        console.log(`[Xtream] Loaded ${streams.length} channels for "${username}"`);

        let categoriesMap = {};
        if (catsRes && catsRes.ok) {
          try {
            const cats = await catsRes.json();
            if (Array.isArray(cats)) {
              cats.forEach(c => { categoriesMap[c.category_id] = c.category_name; });
            }
          } catch (e) {}
        }

        let m3uLines = ['#EXTM3U'];
        streams.forEach(s => {
          const catName = categoriesMap[s.category_id] || 'Live TV';
          const streamUrl = `${portalUrl}/live/${username}/${password}/${s.stream_id}.m3u8`;
          m3uLines.push(`#EXTINF:-1 tvg-id="${s.epg_channel_id || ''}" tvg-name="${s.name || ''}" tvg-logo="${s.stream_icon || ''}" group-title="${catName}",${s.name}`);
          m3uLines.push(streamUrl);
        });
        return m3uLines.join('\n');
      }
    }
  } catch (e) {
    console.warn('[Xtream] API fetch error:', e.message);
  }

  // Fallback: Fetch direct m3u_plus playlist format
  try {
    const getUrl = `${portalUrl}/get.php?username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}&type=m3u_plus&output=hls`;
    const proxyGetUrl = getProxyUrl(getUrl);
    console.log('[Xtream] Trying M3U_PLUS fallback:', proxyGetUrl);
    const getRes = await fetch(proxyGetUrl, { signal: AbortSignal.timeout(25000) });
    if (getRes && getRes.ok) {
      const text = await getRes.text();
      if (text && text.includes('#EXTM3U')) {
        console.log('[Xtream] Loaded channels via M3U_PLUS fallback');
        return text;
      }
    }
  } catch (e) {
    console.warn('[Xtream] M3U_PLUS fallback error:', e.message);
  }

  return '';
}

// Load IPTV playlist from credentials
async function loadIPTVPlaylist() {
  let rawPortalUrl = 'http://portal5458.com:8080';
  let username = '';
  let password = '';

  try {
    rawPortalUrl = localStorage.getItem('iptv_portal_url') || 'http://portal5458.com:8080';
    username = (localStorage.getItem('iptv_username') || '').trim();
    password = (localStorage.getItem('iptv_password') || '').trim();
  } catch (e) {}

  const headerBadge = document.getElementById('channel-count-header');

  if (!username || !password) {
    console.log('[IPTV] No active credentials found. Showing sign-in prompt.');
    state.channels = [];
    renderUnactivatedState();
    return;
  }

  try {
    let portalUrl = rawPortalUrl.trim().replace(/\/+$/, '');
    if (!portalUrl.startsWith('http://') && !portalUrl.startsWith('https://')) {
      portalUrl = 'http://' + portalUrl;
    }

    console.log("[IPTV] Loading Xtream playlist for user:", username);
    let rawM3U = '';

    try {
      rawM3U = await fetchXtreamPlaylist(portalUrl, username, password);
    } catch (e) {
      console.warn("[IPTV] fetchXtreamPlaylist error:", e);
    }

    if (rawM3U) {
      state.channels = parseM3U(rawM3U);
    }

    if (!state.channels || state.channels.length === 0) {
      throw new Error("No live channels returned. Please check your IPTV credentials.");
    }

    updateCategoriesList();

    const countText = `${state.channels.length} Live Channels`;
    if (headerBadge) headerBadge.textContent = countText;

    const homeStatusText = document.getElementById('home-status-text');
    if (homeStatusText) {
      homeStatusText.textContent = `${state.channels.length} Channels Loaded & Ready`;
    }

    renderCategories();
    applyFilterAndRender();
  } catch (error) {
    console.error("Failed to load IPTV playlist:", error);
    if (headerBadge) headerBadge.textContent = 'Playlist Offline';

    if (state.activeTab === 'live') {
      const grid = document.getElementById('channels-grid');
      grid.innerHTML = `
        <div class="col-span-full py-12 text-center">
          <div class="w-12 h-12 mx-auto rounded-xl bg-red-500/10 border border-red-500/20 flex items-center justify-center text-red-400 mb-3">
            <i data-lucide="alert-triangle" class="w-6 h-6"></i>
          </div>
          <h3 class="text-sm font-semibold text-slate-200">Failed to load channels</h3>
          <p class="text-xs text-slate-500 mt-1">Please check your IPTV credentials in Settings or verify your network connection.</p>
          <button id="reload-playlist-btn" class="mt-4 px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-semibold inline-flex items-center gap-1.5 transition-colors">
            <i data-lucide="refresh-cw" class="w-3.5 h-3.5"></i> Retry Playlist Load
          </button>
        </div>
      `;
      createIcons(iconConfig);
      
      const retryBtn = document.getElementById('reload-playlist-btn');
      if (retryBtn) {
        retryBtn.addEventListener('click', () => {
          retryBtn.disabled = true;
          retryBtn.textContent = 'Retrying...';
          loadIPTVPlaylist();
        });
      }
    }
  }
}

// US-Only Helper functions
function isUSCategory(cat) {
  if (!cat) return false;
  if (cat === 'All Channels' || cat === 'Favorites' || cat === 'Recents') return true;
  const upper = String(cat).toUpperCase().trim();
  return /^USA?[\s\-_|:]/i.test(upper) || upper.startsWith('US ') || upper === 'US' || upper === 'USA';
}

function isExplicitNonUSCategory(cat) {
  if (!cat || cat === 'All Channels' || cat === 'Favorites' || cat === 'Recents') return false;
  if (isUSCategory(cat)) return false;
  const upper = String(cat).toUpperCase().trim();
  
  // Non-US country/region/language keywords (full names and 2-letter codes)
  const nonUsPattern = /\b(BRAZIL|BRAZILIAN|BR|CANADA|CANADIAN|CA|DEPORTES|DEPORTE|DEPORTIVAS|LATINO|LATINA|LATIN|PORTUGAL|PORTUGUESE|PT|UK|UNITED KINGDOM|MEXICO|MEXICAN|MX|FRANCE|FRENCH|FR|GERMANY|GERMAN|DE|SPAIN|SPANISH|ES|ITALY|ITALIAN|IT|ARGENTINA|AR|TURKEY|TURKISH|TR|POLAND|POLISH|PL|ROMANIA|ROMANIAN|RO|RUSSIA|RUSSIAN|RU|ALBANIA|AL|EX-YU|GREECE|GREEK|GR|NETHERLANDS|DUTCH|NL|INDIA|INDIAN|IN|PAKISTAN|PK|ARABIC|AFRICA|AFRICAN|UKRAINE|UKRAINIAN|ASIA|ASIAN|AUSTRALIA|AU|NEW ZEALAND|NZ)\b/i;
  const prefixPattern = /^(UK|CA|MX|FR|DE|ES|IT|AR|BR|TR|PL|RO|RU|AL|EX-YU|GR|NL|PT|IN|PK|AU|NZ)([\s\-_|:]|$)/i;

  return nonUsPattern.test(upper) || prefixPattern.test(upper);
}

function updateCategoriesList() {
  const groups = new Set();
  state.channels.forEach(ch => {
    if (ch.group) groups.add(ch.group);
  });
  
  let groupList = Array.from(groups);

  if (state.usOnly) {
    // 1. Hide explicitly non-US categories
    groupList = groupList.filter(cat => !isExplicitNonUSCategory(cat));

    // 2. Put US categories in front (sorted alphabetically), followed by remaining generic categories
    const usCats = groupList.filter(cat => isUSCategory(cat)).sort();
    const otherCats = groupList.filter(cat => !isUSCategory(cat)).sort();
    groupList = [...usCats, ...otherCats];
  } else {
    groupList.sort();
  }

  state.categories = ['All Channels', 'Favorites', 'Recents', ...groupList];

  if (!state.categories.includes(state.selectedCategory)) {
    state.selectedCategory = 'All Channels';
  }
}

// Setup Live Channel Controls (US Only switch & Prev/Next buttons)
function setupLiveChannelControls() {
  const usOnlySwitch = document.getElementById('us-only-switch');
  if (usOnlySwitch) {
    usOnlySwitch.checked = state.usOnly;
    usOnlySwitch.addEventListener('change', (e) => {
      state.usOnly = e.target.checked;
      localStorage.setItem('iptv_us_only', JSON.stringify(state.usOnly));
      updateCategoriesList();
      renderCategories();
      applyFilterAndRender();
    });
  }

  const prevBtn = document.getElementById('ch-prev-btn');
  const nextBtn = document.getElementById('ch-next-btn');
  if (prevBtn) prevBtn.addEventListener('click', () => changeLiveChannel(-1));
  if (nextBtn) nextBtn.addEventListener('click', () => changeLiveChannel(1));

  const quickPrev = document.getElementById('quick-prev-ch-btn');
  const quickNext = document.getElementById('quick-next-ch-btn');
  if (quickPrev) quickPrev.addEventListener('click', () => changeLiveChannel(-1));
  if (quickNext) quickNext.addEventListener('click', () => changeLiveChannel(1));

  // Global Keyboard Shortcuts for Live TV Channel Switching
  document.addEventListener('keydown', (e) => {
    if (state.activeTab !== 'live') return;
    
    // Ignore keypresses if user is typing in a text field or search bar
    const tag = document.activeElement ? document.activeElement.tagName.toLowerCase() : '';
    if (tag === 'input' || tag === 'textarea' || tag === 'select') return;

    if (e.key === 'PageUp' || e.key === 'p' || e.key === 'P') {
      e.preventDefault();
      changeLiveChannel(-1);
    } else if (e.key === 'PageDown' || e.key === 'n' || e.key === 'N') {
      e.preventDefault();
      changeLiveChannel(1);
    }
  });
}

// Render categories selection in Live TV sidebar
function renderCategories() {
  const container = document.getElementById('categories-container');
  if (!container) return;
  container.innerHTML = '';

  if (!state.categories || state.categories.length === 0) {
    container.innerHTML = `
      <div class="p-3 text-xs text-slate-400 flex items-center gap-2 animate-pulse">
        <i data-lucide="refresh-cw" class="w-3.5 h-3.5 animate-spin"></i>
        <span>Loading categories...</span>
      </div>
    `;
    createIcons(iconConfig);
    return;
  }

  state.categories.forEach(category => {
    const btn = document.createElement('button');
    btn.className = `category-btn ${state.selectedCategory === category ? 'active' : ''}`;
    
    let iconName = 'tv';
    let count = 0;
    
    if (category === 'All Channels') {
      iconName = 'tv';
      if (state.usOnly) {
        count = state.channels.filter(ch => isUSCategory(ch.group) || /^USA?[\s\-_|:]/i.test(ch.name) || !isExplicitNonUSCategory(ch.group)).length;
      } else {
        count = state.channels.length;
      }
    } else if (category === 'Favorites') {
      iconName = 'star';
      count = state.favorites.length;
    } else if (category === 'Recents') {
      iconName = 'refresh-cw';
      count = state.recents.length;
    } else {
      iconName = 'tv';
      count = state.channels.filter(ch => ch.group === category).length;
    }

    btn.innerHTML = `
      <div class="flex items-center gap-2.5 truncate">
        <i data-lucide="${iconName}" class="w-4 h-4 opacity-75 shrink-0"></i>
        <span class="truncate">${category}</span>
      </div>
      <span class="text-[10px] bg-slate-900 border border-slate-800/80 text-slate-500 px-1.5 py-0.5 rounded-md min-w-5 text-center">${count}</span>
    `;

    btn.addEventListener('click', () => {
      state.selectedCategory = category;
      document.querySelectorAll('.category-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      applyFilterAndRender();
    });

    container.appendChild(btn);
  });

  createIcons(iconConfig);
}

// Apply filter logic to Live channels
function applyFilterAndRender() {
  let list = [];

  const liveSearchInput = document.getElementById('live-search-input');
  const searchQ = (liveSearchInput && liveSearchInput.value) ? liveSearchInput.value.trim() : (state.searchQuery || '');

  if (state.selectedCategory === 'All Channels') {
    if (state.usOnly) {
      list = state.channels.filter(ch => isUSCategory(ch.group) || /^USA?[\s\-_|:]/i.test(ch.name) || !isExplicitNonUSCategory(ch.group));
    } else {
      list = [...state.channels];
    }
  } else if (state.selectedCategory === 'Favorites') {
    list = state.channels.filter(ch => state.favorites.includes(ch.id));
  } else if (state.selectedCategory === 'Recents') {
    list = state.recents
      .map(id => state.channels.find(ch => ch.id === id))
      .filter(Boolean);
  } else {
    list = state.channels.filter(ch => ch.group === state.selectedCategory);
  }

  // Filter by Search Query if present
  if (searchQ) {
    const q = searchQ.toLowerCase();
    list = list.filter(ch => 
      (ch.name && ch.name.toLowerCase().includes(q)) || 
      (ch.group && ch.group.toLowerCase().includes(q)) ||
      (ch.id && ch.id.toLowerCase().includes(q))
    );
  }

  state.filteredChannels = list;

  const countEl = document.getElementById('filtered-count');
  if (countEl) countEl.textContent = `${list.length} channels`;
  
  const subtitleEl = document.getElementById('channel-list-info');
  if (subtitleEl) {
    const usSuffix = state.usOnly ? ' (US-Only)' : '';
    const querySuffix = searchQ ? ` matching "${searchQ}"` : '';
    subtitleEl.textContent = `Showing results in ${state.selectedCategory}${usSuffix}${querySuffix}`;
  }

  renderChannelsGrid();
}

// Render Live TV Channel Cards
function renderChannelsGrid() {
  const container = document.getElementById('channels-grid');
  const emptyState = document.getElementById('no-channels-found');
  if (!container) return;
  container.innerHTML = '';

  if (state.channels.length === 0) {
    container.innerHTML = `
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
      <div class="skeleton-card"></div>
    `;
    if (emptyState) emptyState.classList.add('hidden');
    return;
  }

  if (state.filteredChannels.length === 0) {
    if (emptyState) emptyState.classList.remove('hidden');
    return;
  }
  if (emptyState) emptyState.classList.add('hidden');

  state.filteredChannels.forEach(channel => {
    const isFav = state.favorites.includes(channel.id);
    const isActive = state.currentPlayingUrl === channel.url;
    
    const card = document.createElement('div');
    card.className = `channel-card hover-scale ${isActive ? 'active' : ''}`;

    const content = document.createElement('div');
    content.className = "flex items-center gap-3 min-w-0 flex-1";
    content.addEventListener('click', () => playChannel(channel));

    const logoContainer = document.createElement('div');
    logoContainer.className = "channel-card-logo-wrap";
    
    if (channel.logo) {
      const img = document.createElement('img');
      img.src = channel.logo;
      img.alt = channel.name;
      img.loading = "lazy";
      img.onerror = () => {
        img.remove();
        logoContainer.appendChild(createPlaceholderLogo(channel.name));
      };
      logoContainer.appendChild(img);
    } else {
      logoContainer.appendChild(createPlaceholderLogo(channel.name));
    }

    const details = document.createElement('div');
    details.className = "channel-card-info";
    details.innerHTML = `
      <h4 class="channel-card-title">${channel.name}</h4>
      <span class="channel-card-group">${channel.group || 'Live TV'}</span>
    `;

    content.appendChild(logoContainer);
    content.appendChild(details);

    const favBtn = document.createElement('button');
    favBtn.className = `channel-fav-btn ${isFav ? 'active' : ''}`;
    favBtn.innerHTML = `<i data-lucide="star" class="${isFav ? 'fill-amber-400 text-amber-400' : ''}"></i>`;
    favBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      toggleFavorite(channel.id);
    });

    card.appendChild(content);
    card.appendChild(favBtn);

    container.appendChild(card);
  });

  createIcons(iconConfig);
}

// Avatar fallback initial name
function createPlaceholderLogo(name) {
  const placeholder = document.createElement('div');
  placeholder.className = "w-full h-full flex items-center justify-center font-bold text-[10px] uppercase text-indigo-400 bg-indigo-950/20";
  
  const parts = name.split(/\s+/).filter(Boolean);
  let initials = "";
  if (parts.length > 0) initials += parts[0][0];
  if (parts.length > 1) initials += parts[1][0];
  if (!initials) initials = "TV";
  
  placeholder.textContent = initials.substring(0, 2);
  return placeholder;
}

// Play Live Channel HLS
function playChannel(channel) {
  closeActiveSse();

  state.currentPlayingUrl = channel.url;
  
  // Relocate shared player section to live container if needed
  const playerSection = document.getElementById('player-section');
  const liveContainer = document.getElementById('live-player-container');
  if (playerSection && liveContainer && playerSection.parentElement !== liveContainer) {
    liveContainer.appendChild(playerSection);
  }
  
  // Shift player wrapper controls live indicators
  const liveDot = document.getElementById('live-indicator-dot');
  const liveText = document.getElementById('live-indicator-text');
  const seekContainer = document.getElementById('seek-container');
  if (liveDot) liveDot.style.display = 'block';
  if (liveText) liveText.style.display = 'block';
  if (seekContainer) seekContainer.style.display = 'none';

  // Reset embed player wrappers
  const embedIframe = document.getElementById('embed-iframe');
  if (embedIframe) embedIframe.src = '';
  const embedWrapper = document.getElementById('embed-player-wrapper');
  if (embedWrapper) embedWrapper.style.display = 'none';
  const playerWrapper = document.querySelector('.player-wrapper');
  if (playerWrapper) playerWrapper.classList.remove('embed-active');
  if (player && typeof player.setControlMode === 'function') {
    player.setControlMode('live');
  }

  player.playChannel(channel);
  addToRecents(channel.id);
  
  document.querySelectorAll('.channel-card').forEach(card => {
    card.classList.remove('active');
  });
  
  applyFilterAndRender();
}

// Toggle Live favorite channels
function toggleFavorite(id) {
  const index = state.favorites.indexOf(id);
  if (index === -1) {
    state.favorites.push(id);
    player.showToast("Added to Favorites");
  } else {
    state.favorites.splice(index, 1);
    player.showToast("Removed from Favorites");
  }
  
  localStorage.setItem('iptv_favorites', JSON.stringify(state.favorites));
  renderCategories();
  applyFilterAndRender();
}

// Add played live channel to recents
function addToRecents(id) {
  const index = state.recents.indexOf(id);
  if (index !== -1) {
    state.recents.splice(index, 1);
  }
  state.recents.unshift(id);
  
  if (state.recents.length > MAX_RECENTS) {
    state.recents.pop();
  }
  
  localStorage.setItem('iptv_recents', JSON.stringify(state.recents));
  renderCategories();
}
