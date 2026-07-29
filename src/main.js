import { createIcons, Menu, X, Play, Pause, Tv, Search, Info, AlertTriangle, RefreshCw, Volume2, Volume1, VolumeX, Maximize, SquareStack, ExternalLink, Star, Monitor, Settings, ArrowLeft, Home, Film, ChevronRight, Radio } from 'lucide';
import { fetchAndParseM3U, parseM3U } from './parser';
import { IPTVPlayer } from './player';
import { searchMulti, getMovieDetails, getTVShowDetails, getTVSeasonDetails, getTMDBImageUrl, getTrending, getTrendingMovies, getTrendingTV, getTopRated, getTopRatedTV, getByGenre } from './tmdb';
import { getStreamSources } from './streamApi';
import { PODCAST_CHANNELS, getAllPodcastChannels, getHeroPodcast, searchPodcastChannels, fetchChannelPastEpisodes } from './podcastsData';
import './style.css';

// Initialize Lucide icons
const iconConfig = {
  icons: {
    Menu, X, Play, Pause, Tv, Search, Info, AlertTriangle, RefreshCw, Volume2, Volume1, VolumeX, Maximize, SquareStack, ExternalLink, Star, Monitor, Settings, ArrowLeft, Home, Film, ChevronRight, Radio
  }
};

// Global State
const state = {
  activeTab: 'home', // 'home', 'search', 'live', 'library', 'settings'
  channels: [],
  filteredChannels: [],
  categories: [],
  selectedCategory: 'All Channels',
  searchQuery: '',
  favorites: JSON.parse(localStorage.getItem('iptv_favorites') || '[]'),
  recents: JSON.parse(localStorage.getItem('iptv_recents') || '[]'),
  watchlist: JSON.parse(localStorage.getItem('vod_watchlist') || '[]'),
  currentPlayingUrl: null,
  
  // Movies & TV Spotlight / Carousel State
  trendingItems: [],
  moviesSearchResults: [],
  selectedMedia: null,
  activeStreamSse: null,
  resolvedSources: [],
  activeSourceIndex: -1
};

// Helper to construct dynamic IPTV playlist URL from localStorage or credentials
function getPlaylistUrl() {
  const portalUrl = localStorage.getItem('iptv_portal_url') || 'http://portal5458.com:8080';
  const username = localStorage.getItem('iptv_username') || 'SGmUC7q2U';
  const password = localStorage.getItem('iptv_password') || '4WM9WVsjG';
  
  if (portalUrl && username && password) {
    return `${portalUrl}/get.php?username=${username}&password=${password}&type=m3u_plus&output=ts`;
  }
  return './o_all.m3u';
}

const MAX_RECENTS = 20;

// Embed providers — curated to working sources only (as of 2026)
// Primary servers tried first; fallbacks used when primary fails
const EMBED_PROVIDERS = [
  {
    name: 'VidLink PRO',
    movie: (id) => `https://vidlink.pro/movie/${id}`,
    tv: (id, s, e) => `https://vidlink.pro/tv/${id}/${s}/${e}`,
  },
  {
    name: 'Videasy (Multi-Sub)',
    movie: (id) => `https://player.videasy.net/movie/${id}`,
    tv: (id, s, e) => `https://player.videasy.net/tv/${id}/${s}/${e}`,
  },
  {
    name: 'VixSrc (Fast HD)',
    movie: (id) => `https://vixsrc.to/movie/${id}`,
    tv: (id, s, e) => `https://vixsrc.to/tv/${id}/${s}/${e}`,
  },
  {
    name: 'SuperEmbed',
    movie: (id) => `https://multiembed.mov/directstream.php?video_id=${id}&tmdb=1`,
    tv: (id, s, e) => `https://multiembed.mov/directstream.php?video_id=${id}&tmdb=1&s=${s}&e=${e}`,
  },
  {
    name: 'VidSrc PRO',
    movie: (id) => `https://vidsrc.cc/v2/embed/movie/${id}`,
    tv: (id, s, e) => `https://vidsrc.cc/v2/embed/tv/${id}/${s}/${e}`,
  },
];

// Initialize components
let player;
let searchDebounceTimer;

document.addEventListener('DOMContentLoaded', async () => {
  // Init player
  player = new IPTVPlayer('video-player');
  
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

  // Render initial icons
  createIcons(iconConfig);

  // Set default tab to Live TV
  switchTab('live');

  // Load playlist (Live TV mode data) in background
  loadIPTVPlaylist();
});

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

// Stop and discontinue any active Live TV stream feed to prevent playback & audio conflicts
function stopActiveLiveTVFeed() {
  console.log('[IPTV] Discontinuing active Live TV stream feed');
  if (typeof player !== 'undefined' && player) {
    try {
      player.destroyHls();
    } catch (e) {}
  }
  const videoEl = document.getElementById('video-player');
  if (videoEl) {
    videoEl.pause();
    videoEl.removeAttribute('src');
    try { videoEl.load(); } catch (e) {}
  }
  state.currentPlayingUrl = null;
}

// Switch between tabs
function switchTab(tabName) {
  state.activeTab = tabName;
  
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
    // Append player to live TV panel
    if (liveContainer && playerSection) {
      liveContainer.appendChild(playerSection);
    }
    // Pause embed player if active
    const embedIframe = document.getElementById('embed-iframe');
    if (embedIframe) embedIframe.src = '';
    const embedWrapper = document.getElementById('embed-player-wrapper');
    if (embedWrapper) embedWrapper.style.display = 'none';
    const playerWrapper = document.querySelector('.player-wrapper');
    if (playerWrapper) playerWrapper.classList.remove('embed-active');

    renderCategories();
    applyFilterAndRender();
  } else {
    stopActiveLiveTVFeed();
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

// Load Movies Dashboard hero + curated category carousels
async function loadMoviesDashboard() {
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
let podcastEpisodesSearchQuery = '';
let podcastEpisodesSortMode = 'newest';
let podcastEpisodesPage = 1;
const PODCAST_EPISODES_PER_PAGE = 9;

// Load YouTube Podcasts Dashboard
function loadPodcastsDashboard() {
  const techRow = document.getElementById('row-podcasts-tech');
  const scienceRow = document.getElementById('row-podcasts-science');
  const comedyRow = document.getElementById('row-podcasts-comedy');
  const sportsRow = document.getElementById('row-podcasts-sports');
  const historyRow = document.getElementById('row-podcasts-history');
  const favoritesRow = document.getElementById('row-podcasts-favorites');
  const favoritesSection = document.getElementById('row-podcasts-favorites-section');

  // Load Favorite Podcast Channels
  if (state.favoritePodcasts && state.favoritePodcasts.length > 0) {
    if (favoritesSection) favoritesSection.style.display = 'block';
    if (favoritesRow) renderPodcastChannelRow(state.favoritePodcasts, favoritesRow);
  } else {
    if (favoritesSection) favoritesSection.style.display = 'none';
  }

  if (techRow) renderPodcastChannelRow(PODCAST_CHANNELS.tech, techRow);
  if (scienceRow) renderPodcastChannelRow(PODCAST_CHANNELS.science, scienceRow);
  if (comedyRow) renderPodcastChannelRow(PODCAST_CHANNELS.comedy, comedyRow);
  if (sportsRow) renderPodcastChannelRow(PODCAST_CHANNELS.sports, sportsRow);
  if (historyRow) renderPodcastChannelRow(PODCAST_CHANNELS.history, historyRow);
}

// Render Podcast Channels inside horizontal carousels
function renderPodcastChannelRow(channels, container) {
  if (!container) return;
  container.innerHTML = '';

  if (!channels || channels.length === 0) {
    container.innerHTML = '<span class="text-xs text-slate-500 py-4">No podcast channels found.</span>';
    return;
  }

  const favs = state.favoritePodcasts || [];
  channels.forEach(channel => {
    const card = document.createElement('div');
    card.className = 'detail-item-card hover-scale min-w-[210px] sm:min-w-[230px]';
    const isFav = favs.some(f => f.id === channel.id);
    const epCount = channel.episodes ? channel.episodes.length : 1;

    card.innerHTML = `
      <div style="position: relative;">
        <img src="${channel.avatar}" alt="${channel.channelName}" class="detail-item-poster" style="aspect-ratio: 1/1; object-fit: cover; border-radius: 12px 12px 0 0;" loading="lazy" onerror="this.onerror=null; this.src='https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80';">
        <button class="podcast-fav-btn ${isFav ? 'active' : ''}" title="${isFav ? 'Remove Favorite Channel' : 'Add to Favorite Channels'}" style="position: absolute; top: 8px; right: 8px; background: rgba(15,23,42,0.85); border: 1px solid rgba(255,255,255,0.2); border-radius: 9999px; width: 28px; height: 28px; display: flex; align-items: center; justify-content: center; color: ${isFav ? '#f59e0b' : '#cbd5e1'}; cursor: pointer;">
          <i data-lucide="star" style="width: 14px; height: 14px; fill: ${isFav ? '#f59e0b' : 'none'};"></i>
        </button>
        <span style="position: absolute; bottom: 8px; left: 8px; background: rgba(0,0,0,0.8); padding: 2px 8px; border-radius: 6px; font-size: 10px; font-weight: 700; color: #ef4444; border: 1px solid rgba(239,68,68,0.3);">
          ${channel.subscribers || channel.category}
        </span>
      </div>
      <div class="detail-item-info">
        <h4 class="detail-item-title">${channel.channelName}</h4>
        <div class="detail-item-meta">
          <span class="detail-item-year">${channel.host || 'Podcast Host'}</span>
          <span class="px-2 py-0.5 rounded bg-red-500/20 text-red-400 font-semibold text-[10px]">${epCount}+ Episodes</span>
        </div>
      </div>
    `;

    // Favorite Channel button toggle
    const favBtn = card.querySelector('.podcast-fav-btn');
    if (favBtn) {
      favBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        togglePodcastFavorite(channel);
      });
    }

    // Clicking channel card opens its expanded episodes view modal!
    card.addEventListener('click', () => openPodcastChannelModal(channel));
    container.appendChild(card);
  });
  createIcons(iconConfig);
}

// Open Channel Episodes View Modal (With Search, Sort & Pagination)
async function openPodcastChannelModal(channel) {
  const overlay = document.getElementById('podcast-channel-overlay');
  if (!overlay) return;

  activePodcastModalChannel = channel;
  podcastEpisodesSearchQuery = '';
  podcastEpisodesSortMode = 'newest';
  podcastEpisodesPage = 1;

  const avatar = document.getElementById('podcast-channel-avatar');
  const title = document.getElementById('podcast-channel-title');
  const host = document.getElementById('podcast-channel-host');
  const desc = document.getElementById('podcast-channel-desc');
  const subsBadge = document.getElementById('podcast-channel-subs-badge');
  const closeBtn = document.getElementById('podcast-channel-close-btn');
  const favBtn = document.getElementById('podcast-channel-favorite-btn');
  const searchInput = document.getElementById('podcast-episodes-search');
  const sortSelect = document.getElementById('podcast-episodes-sort');
  const loadMoreBtn = document.getElementById('podcast-episodes-load-more');

  if (avatar) avatar.src = channel.avatar;
  if (title) title.textContent = channel.channelName;
  if (host) host.textContent = `Hosted by ${channel.host || 'YouTube Creator'} • ${channel.category}`;
  if (desc) desc.textContent = channel.description || 'Watch latest full video podcast episodes.';
  if (subsBadge) subsBadge.textContent = channel.subscribers || 'YouTube Channel';

  // Update Favorite button state inside modal
  const updateFavBtnUI = () => {
    const favs = state.favoritePodcasts || [];
    const isFav = favs.some(f => f.id === channel.id);
    if (favBtn) {
      favBtn.innerHTML = `<i data-lucide="star" class="w-4 h-4" style="fill: ${isFav ? '#f59e0b' : 'none'}; color: ${isFav ? '#f59e0b' : 'currentColor'};"></i> ${isFav ? 'Favorited Channel' : 'Add to Favorites'}`;
    }
  };
  updateFavBtnUI();

  if (favBtn) {
    favBtn.onclick = () => {
      togglePodcastFavorite(channel);
      updateFavBtnUI();
    };
  }

  if (closeBtn) {
    closeBtn.onclick = () => {
      overlay.classList.add('hidden');
      document.body.style.overflow = '';
    };
  }

  // Load and fetch channel episodes (combining static catalog + dynamic RSS feeds)
  activePodcastAllEpisodes = await fetchChannelPastEpisodes(channel);

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
    loadMoreBtn.onclick = () => {
      podcastEpisodesPage++;
      renderChannelEpisodesGrid();
    };
  }

  renderChannelEpisodesGrid();

  overlay.classList.remove('hidden');
  document.body.style.overflow = 'hidden';
  createIcons(iconConfig);
}

// Render paginated & filtered episode grid inside Channel Modal
function renderChannelEpisodesGrid() {
  const grid = document.getElementById('podcast-channel-episodes-grid');
  const countBadge = document.getElementById('podcast-episodes-count');
  const loadMoreWrap = document.getElementById('podcast-episodes-load-more-wrap');

  if (!grid || !activePodcastModalChannel) return;

  let filtered = [...activePodcastAllEpisodes];

  // Apply episode search filter
  if (podcastEpisodesSearchQuery) {
    filtered = filtered.filter(ep =>
      ep.title.toLowerCase().includes(podcastEpisodesSearchQuery) ||
      (ep.description && ep.description.toLowerCase().includes(podcastEpisodesSearchQuery))
    );
  }

  // Apply sorting
  if (podcastEpisodesSortMode === 'newest') {
    filtered.sort((a, b) => (b.year || 2026) - (a.year || 2026));
  } else if (podcastEpisodesSortMode === 'oldest') {
    filtered.sort((a, b) => (a.year || 2026) - (b.year || 2026));
  } else if (podcastEpisodesSortMode === 'duration') {
    filtered.sort((a, b) => (b.duration || '').localeCompare(a.duration || ''));
  }

  if (countBadge) countBadge.textContent = `${filtered.length} Past Episodes`;

  const totalToShow = podcastEpisodesPage * PODCAST_EPISODES_PER_PAGE;
  const visibleEpisodes = filtered.slice(0, totalToShow);

  grid.innerHTML = '';
  if (visibleEpisodes.length === 0) {
    grid.innerHTML = `
      <div class="col-span-full py-8 text-center text-slate-400 bg-slate-900/40 rounded-xl border border-slate-800">
        <i data-lucide="alert-circle" class="w-8 h-8 mx-auto text-slate-500 mb-2"></i>
        <p class="text-xs font-semibold">No episodes match your search criteria.</p>
      </div>
    `;
    if (loadMoreWrap) loadMoreWrap.style.display = 'none';
    return;
  }

  const channelAvatar = activePodcastModalChannel ? activePodcastModalChannel.avatar : 'https://images.unsplash.com/photo-1590602847861-f357a9332bbc?auto=format&fit=crop&w=600&q=80';

  visibleEpisodes.forEach(ep => {
    const card = document.createElement('div');
    card.className = 'bg-slate-900/80 border border-slate-800/80 rounded-xl overflow-hidden hover:border-red-500 transition-all cursor-pointer group flex flex-col shadow-md hover:shadow-red-950/20';
    const thumbUrl = ep.thumbnail || channelAvatar;

    card.innerHTML = `
      <div class="relative aspect-video overflow-hidden bg-slate-950">
        <img src="${thumbUrl}" alt="${ep.title}" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" onerror="this.onerror=null; this.src='${channelAvatar}';">
        <div class="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
          <div class="w-12 h-12 rounded-full bg-red-600 flex items-center justify-center shadow-lg">
            <i data-lucide="play" class="w-6 h-6 fill-white text-white ml-0.5"></i>
          </div>
        </div>
        <span class="absolute bottom-2 right-2 px-2 py-0.5 rounded bg-black/80 text-[10px] font-semibold text-white border border-white/10">${ep.duration || 'Video'}</span>
        <span class="absolute top-2 left-2 px-2 py-0.5 rounded bg-red-600/90 text-[10px] font-bold text-white uppercase tracking-wider">HD 🔴</span>
      </div>
      <div class="p-3 flex-1 flex flex-col justify-between space-y-2">
        <h4 class="text-xs sm:text-sm font-bold text-white line-clamp-2 leading-snug group-hover:text-red-400 transition-colors">${ep.title}</h4>
        <p class="text-[11px] text-slate-400 line-clamp-2">${ep.description || 'Full podcast episode video conversation.'}</p>
        <div class="flex items-center justify-between text-xs text-slate-400 pt-1 border-t border-slate-800/60">
          <span class="text-[11px] text-slate-400">📅 ${ep.date || ep.year || 'Past'}</span>
          <span class="text-red-400 font-bold text-[11px] flex items-center gap-1">Watch Now <i data-lucide="chevron-right" class="w-3 h-3"></i></span>
        </div>
      </div>
    `;

    card.addEventListener('click', () => {
      openPodcastModal({
        ...ep,
        channelName: activePodcastModalChannel.channelName,
        category: activePodcastModalChannel.category,
        thumbnail: ep.thumbnail || channelAvatar,
        description: ep.description || activePodcastModalChannel.description
      });
    });

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
}

// Open YouTube Video Modal (Podcast Episode)
function openPodcastModal(podcast) {
  stopActiveLiveTVFeed();
  resetPlayerWindow();
  state.selectedMedia = podcast;

  const overlay = document.getElementById('details-overlay');
  if (!overlay) return;

  // Move shared player section into details container
  const playerSection = document.getElementById('player-section');
  const vodContainer = document.getElementById('vod-player-container');
  if (playerSection && vodContainer) {
    vodContainer.appendChild(playerSection);
  }

  const titleEl = document.getElementById('details-title');
  const metaEl = document.getElementById('details-meta-info');
  const overviewEl = document.getElementById('details-overview');
  const backdropEl = document.getElementById('details-backdrop');
  const embedWrapper = document.getElementById('embed-player-wrapper');
  const embedIframe = document.getElementById('embed-iframe');
  const videoEl = document.getElementById('video-player');
  const poster = document.getElementById('player-poster');
  const activeSourceName = document.getElementById('active-source-name');
  const embedSourcesList = document.getElementById('embed-sources-list');
  const tvSelectors = document.getElementById('tv-selectors');
  const shield = document.getElementById('embed-shield');

  if (tvSelectors) tvSelectors.classList.add('hidden');
  if (titleEl) titleEl.textContent = podcast.title;
  if (metaEl) metaEl.innerHTML = `<span class="text-slate-300 font-semibold">${podcast.channelName || 'Podcast'}</span> • <span class="px-2 py-0.5 rounded bg-red-500/20 text-red-400 font-medium">${podcast.category || 'YouTube Video'}</span>`;
  if (overviewEl) overviewEl.textContent = podcast.description || 'Watch top YouTube podcast episode and channel content directly inside TVISION.';
  if (backdropEl) backdropEl.style.backgroundImage = `url(${podcast.thumbnail})`;

  if (activeSourceName) activeSourceName.textContent = `YouTube Video (${podcast.channelName || 'Podcast'})`;
  if (embedSourcesList) embedSourcesList.innerHTML = `<span class="px-3 py-1.5 rounded-lg bg-red-600/30 border border-red-500/40 text-red-300 text-xs font-semibold">🔴 YouTube HD Stream</span>`;

  if (poster) poster.style.display = 'none';
  if (shield) shield.style.display = 'none';
  if (videoEl) {
    videoEl.style.display = 'none';
    videoEl.pause();
    videoEl.removeAttribute('src');
  }

  if (embedWrapper) embedWrapper.style.display = 'block';
  if (embedIframe) {
    embedIframe.setAttribute('allow', 'autoplay; fullscreen; picture-in-picture; encrypted-media; accelerometer; gyroscope; web-share');
    embedIframe.removeAttribute('referrerpolicy');
    embedIframe.removeAttribute('sandbox');
    embedIframe.src = `https://www.youtube.com/embed/${podcast.youtubeId}?autoplay=1`;
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
    liveSearchInput.addEventListener('input', () => {
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

  // Podcasts Search (Curated Channels, Related YouTube Channels, and Matching Episodes)
  const podcastsSearchInput = document.getElementById('podcasts-search-input');
  const podcastsSearchResultsSection = document.getElementById('podcasts-search-results-section');
  const podcastsDefaultSection = document.getElementById('podcasts-default-section');

  if (podcastsSearchInput) {
    podcastsSearchInput.addEventListener('input', (e) => {
      const query = e.target.value.trim();
      if (!query) {
        if (podcastsSearchResultsSection) podcastsSearchResultsSection.classList.add('hidden');
        if (podcastsDefaultSection) podcastsDefaultSection.classList.remove('hidden');
        return;
      }

      if (podcastsSearchResultsSection) podcastsSearchResultsSection.classList.remove('hidden');
      if (podcastsDefaultSection) podcastsDefaultSection.classList.add('hidden');

      const grid = document.getElementById('podcasts-search-results-grid');
      const heading = document.getElementById('podcasts-search-status-heading');
      const emptyState = document.getElementById('podcasts-search-empty-state');

      if (heading) heading.textContent = `Podcast Search Results for "${query}"`;

      const results = searchPodcastChannels(query);
      const { curatedChannels, relatedYoutubeChannels, matchingEpisodes } = results;
      const totalResults = (curatedChannels.length + relatedYoutubeChannels.length + matchingEpisodes.length);

      if (totalResults === 0) {
        if (grid) grid.innerHTML = '';
        if (emptyState) emptyState.classList.remove('hidden');
        return;
      }

      if (emptyState) emptyState.classList.add('hidden');
      if (grid) {
        grid.innerHTML = '';

        // 1. Curated Channels Section
        if (curatedChannels.length > 0) {
          const section = document.createElement('div');
          section.className = 'space-y-3';
          section.innerHTML = `
            <h3 class="text-sm font-bold text-white flex items-center gap-2 border-b border-slate-800 pb-2">
              <i data-lucide="radio" class="w-4 h-4 text-red-500"></i> Top Curated Podcast Channels (${curatedChannels.length})
            </h3>
            <div class="carousel-list custom-scrollbar py-2"></div>
          `;
          const rowContainer = section.querySelector('.carousel-list');
          renderPodcastChannelRow(curatedChannels, rowContainer);
          grid.appendChild(section);
        }

        // 2. Related YouTube Channels Section
        if (relatedYoutubeChannels.length > 0) {
          const section = document.createElement('div');
          section.className = 'space-y-3';
          section.innerHTML = `
            <h3 class="text-sm font-bold text-white flex items-center gap-2 border-b border-slate-800 pb-2">
              <i data-lucide="tv" class="w-4 h-4 text-red-500"></i> Best Related YouTube Channels & Creators (${relatedYoutubeChannels.length})
            </h3>
            <div class="carousel-list custom-scrollbar py-2"></div>
          `;
          const rowContainer = section.querySelector('.carousel-list');
          renderPodcastChannelRow(relatedYoutubeChannels, rowContainer);
          grid.appendChild(section);
        }

        // 3. Direct Matching Episodes Section
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
            card.className = 'bg-slate-900/80 border border-slate-800 rounded-xl overflow-hidden hover:border-red-500 cursor-pointer transition-all flex flex-col group';
            card.innerHTML = `
              <div class="relative aspect-video overflow-hidden">
                <img src="${ep.thumbnail}" alt="${ep.title}" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300">
                <div class="absolute inset-0 bg-black/40 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                  <div class="w-10 h-10 rounded-full bg-red-600 flex items-center justify-center shadow-lg">
                    <i data-lucide="play" class="w-5 h-5 fill-white text-white ml-0.5"></i>
                  </div>
                </div>
                <span class="absolute bottom-2 right-2 px-2 py-0.5 rounded bg-black/80 text-[10px] font-semibold text-white">${ep.duration || 'Video'}</span>
              </div>
              <div class="p-3 flex-1 flex flex-col justify-between space-y-1">
                <span class="text-[10px] font-bold text-red-400 uppercase tracking-wider">${ep.channelName}</span>
                <h4 class="text-xs sm:text-sm font-bold text-white line-clamp-2 leading-snug group-hover:text-red-400">${ep.title}</h4>
                <span class="text-[11px] text-slate-400 pt-1">📅 ${ep.date || 'Recent'}</span>
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
  const grid = document.getElementById('library-results-grid');
  const emptyState = document.getElementById('library-empty-state');
  grid.innerHTML = '';

  if (state.watchlist.length === 0) {
    emptyState.classList.remove('hidden');
    return;
  }
  emptyState.classList.add('hidden');

  state.watchlist.forEach(item => {
    const title = item.title || item.name || 'Untitled';
    const isTV = item.media_type === 'tv';
    const year = (item.release_date || item.first_air_date || '').split('-')[0] || 'N/A';
    const rating = item.vote_average ? item.vote_average.toFixed(1) : 'N/A';
    const posterPath = getTMDBImageUrl(item.poster_path, 'w185') || 'https://via.placeholder.com/185x278/090e1a/475569?text=No+Poster';

    const card = document.createElement('div');
    card.className = 'detail-item-card hover-scale';
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
    card.addEventListener('click', () => openDetailsView(mediaItem));
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
  const index = state.watchlist.findIndex(x => x.id === state.selectedMedia.id && x.media_type === state.selectedMedia.media_type);
  
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
    player.showToast("Added to Watchlist");
  } else {
    // Remove from watchlist
    state.watchlist.splice(index, 1);
    player.showToast("Removed from Watchlist");
  }

  localStorage.setItem('vod_watchlist', JSON.stringify(state.watchlist));
  renderWatchlistButtonState();
  
  // Refresh library list if active
  if (state.activeTab === 'library') {
    renderLibraryScreen();
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
    const seekContainer = document.getElementById('seek-container');
    if (seekContainer) seekContainer.style.display = 'flex';
    const liveIndicatorDot = document.getElementById('live-indicator-dot');
    const liveIndicatorText = document.getElementById('live-indicator-text');
    if (liveIndicatorDot) liveIndicatorDot.style.display = 'none';
    if (liveIndicatorText) liveIndicatorText.style.display = 'none';

    createIcons(iconConfig);

    // For Movies, start stream resolution immediately. For TV series, wait for user to select an episode button.
    const isTV = mediaItem.media_type === 'tv';
    if (!isTV) {
      startStreamResolution({
        type: 'movie',
        id: mediaItem.id
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

        // Load episodes for the first season (without auto-playing)
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

// Load episodes list grid for TV Show (waits for episode click to play)
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
        
        // Start stream resolution on explicit episode click
        document.getElementById('sources-status').textContent = `Resolving Season ${seasonNumber} Episode ${ep.episode_number}...`;
        startStreamResolution({ 
          type: 'tv', 
          id: tvId, 
          season: seasonNumber, 
          episode: ep.episode_number 
        });
      });

      grid.appendChild(btn);
    });
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

  // Auto-play the first fallback embed server immediately
  selectActiveSource(0);
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

// Load server source in the shared player
function selectActiveSource(index) {
  if (index < 0 || index >= state.resolvedSources.length) return;

  state.activeSourceIndex = index;
  const source = state.resolvedSources[index];

  renderSourcesUI();

  const embedIframe = document.getElementById('embed-iframe');
  const embedWrapper = document.getElementById('embed-player-wrapper');
  const videoEl = document.getElementById('video-player');
  const playerWrapper = document.querySelector('.player-wrapper');
  const shield = document.getElementById('embed-shield');
  const poster = document.getElementById('player-poster');

  // Stop video element playback and reset it
  stopActiveLiveTVFeed();
  if (videoEl) {
    videoEl.pause();
    videoEl.removeAttribute('src');
    videoEl.load();
  }

  // Hide splash poster once we select a source and play
  if (poster) poster.style.display = 'none';

  if (source.type === 'stream') {
    if (embedIframe) embedIframe.src = '';
    if (embedWrapper) embedWrapper.style.display = 'none';
    if (videoEl) videoEl.style.display = '';
    if (playerWrapper) playerWrapper.classList.remove('embed-active');
    if (shield) shield.style.display = 'none';

    player.playChannel({
      url: source.url,
      name: state.selectedMedia ? (state.selectedMedia.title || state.selectedMedia.name) : 'VOD Stream'
    });
  } else {
    if (videoEl) videoEl.style.display = 'none';
    if (embedWrapper) embedWrapper.style.display = 'block';
    if (playerWrapper) playerWrapper.classList.add('embed-active');
    if (embedIframe) {
      embedIframe.setAttribute('allow', 'autoplay *; fullscreen *; picture-in-picture *; encrypted-media *; accelerometer; gyroscope; web-share');
      embedIframe.removeAttribute('referrerpolicy');
      const useStrict = localStorage.getItem('strict_sandbox') === 'true';
      if (useStrict) {
        embedIframe.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-forms allow-presentation');
      } else {
        embedIframe.removeAttribute('sandbox');
      }
      embedIframe.src = source.url;
    }

    // Keep shield hidden so video frame receives clicks cleanly without being blocked
    if (shield) shield.style.display = 'none';
  }

  const statusText = document.getElementById('sources-status');
  if (statusText) {
    if (source.type === 'stream') {
      statusText.textContent = `Streaming direct source via ${source.name}. Use player controls below.`;
    } else {
      statusText.textContent = `Streaming embed via ${source.name}. Click frame to play. (Warning: Embed ads may trigger popups).`;
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

// Close details overlay modal
function closeDetailsView() {
  closeActiveSse();
  state.selectedMedia = null;
  state.resolvedSources = [];

  const embedIframe = document.getElementById('embed-iframe');
  const embedWrapper = document.getElementById('embed-player-wrapper');
  const videoEl = document.getElementById('video-player');
  const playerWrapper = document.querySelector('.player-wrapper');

  if (embedIframe) embedIframe.src = '';
  if (embedWrapper) embedWrapper.style.display = 'none';
  if (playerWrapper) playerWrapper.classList.remove('embed-active');

  // Pause playing video tag
  if (videoEl) {
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

// Helper to construct proxy URL based on user settings
function getProxyUrl(targetUrl) {
  const mode = localStorage.getItem('proxy_mode') || 'direct_segments';
  const customProxy = (localStorage.getItem('external_proxy_url') || '').trim();

  if (mode === 'direct') {
    return targetUrl;
  }
  if (mode === 'external' && customProxy) {
    const glue = customProxy.includes('?') ? (customProxy.endsWith('?') || customProxy.endsWith('&') ? '' : '&') : '?url=';
    return `${customProxy}${glue}${encodeURIComponent(targetUrl)}`;
  }
  if (mode === 'full') {
    return `/api/proxy?url=${encodeURIComponent(targetUrl)}&proxySegments=1`;
  }
  // Default: direct_segments (Proxy manifest only, direct video segments)
  return `/api/proxy?url=${encodeURIComponent(targetUrl)}`;
}

// Load settings form credentials
function setupSettingsScreen() {
  const tmdbKeyInput = document.getElementById('settings-tmdb-key');
  const sandboxInput = document.getElementById('settings-strict-sandbox');
  const usernameInput = document.getElementById('settings-iptv-username');
  const passwordInput = document.getElementById('settings-iptv-password');
  const proxyModeSelect = document.getElementById('settings-proxy-mode');
  const externalProxyInput = document.getElementById('settings-external-proxy-url');
  const externalProxyContainer = document.getElementById('settings-external-proxy-container');
  const saveBtn = document.getElementById('settings-save-btn');

  // Populate inputs on load
  if (tmdbKeyInput) tmdbKeyInput.value = localStorage.getItem('tmdb_api_key') || '';
  if (sandboxInput) sandboxInput.checked = localStorage.getItem('strict_sandbox') === 'true';
  if (usernameInput) usernameInput.value = localStorage.getItem('iptv_username') || 'SGmUC7q2U';
  if (passwordInput) passwordInput.value = localStorage.getItem('iptv_password') || '4WM9WVsjG';

  const savedProxyMode = localStorage.getItem('proxy_mode') || 'direct_segments';
  if (proxyModeSelect) {
    proxyModeSelect.value = savedProxyMode;
  }
  if (externalProxyInput) {
    externalProxyInput.value = localStorage.getItem('external_proxy_url') || '';
  }
  if (externalProxyContainer) {
    externalProxyContainer.style.display = savedProxyMode === 'external' ? 'block' : 'none';
  }

  if (proxyModeSelect) {
    proxyModeSelect.addEventListener('change', () => {
      if (externalProxyContainer) {
        externalProxyContainer.style.display = proxyModeSelect.value === 'external' ? 'block' : 'none';
      }
    });
  }

  if (saveBtn) {
    saveBtn.addEventListener('click', () => {
      const key = tmdbKeyInput.value.trim();
      if (key) {
        localStorage.setItem('tmdb_api_key', key);
      } else {
        localStorage.removeItem('tmdb_api_key');
      }

      localStorage.setItem('strict_sandbox', sandboxInput.checked ? 'true' : 'false');

      const oldUsername = localStorage.getItem('iptv_username');
      const oldPassword = localStorage.getItem('iptv_password');
      const oldProxyMode = localStorage.getItem('proxy_mode');

      const newUsername = usernameInput.value.trim();
      const newPassword = passwordInput.value.trim();
      const newProxyMode = proxyModeSelect ? proxyModeSelect.value : 'direct_segments';
      const newExternalProxy = externalProxyInput ? externalProxyInput.value.trim() : '';

      localStorage.setItem('iptv_portal_url', 'http://portal5458.com:8080');
      localStorage.setItem('iptv_username', newUsername);
      localStorage.setItem('iptv_password', newPassword);
      localStorage.setItem('iptv_epg', `http://portal5458.com:8080/xmltv.php?username=${newUsername}&password=${newPassword}`);
      localStorage.setItem('proxy_mode', newProxyMode);
      localStorage.setItem('external_proxy_url', newExternalProxy);

      player.showToast("Settings Saved Successfully");

      // Reload IPTV channels if credentials or proxy mode changed
      if (newUsername !== oldUsername || newPassword !== oldPassword || newProxyMode !== oldProxyMode) {
        console.log("[IPTV] Credentials or proxy settings changed, reloading channels...");
        loadIPTVPlaylist();
      }
    });
  }
}

// Load IPTV playlist from credentials
// Fetch playlist via Xtream Codes player API and dynamically format as M3U
async function fetchXtreamPlaylist(portalUrl, username, password) {
  let cleanPortalUrl = (portalUrl || 'http://portal5458.com:8080').trim().replace(/\/+$/, '');
  if (!cleanPortalUrl.startsWith('http://') && !cleanPortalUrl.startsWith('https://')) {
    cleanPortalUrl = 'http://' + cleanPortalUrl;
  }

  const catTarget = `${cleanPortalUrl}/player_api.php?username=${username}&password=${password}&action=get_live_categories`;
  const streamTarget = `${cleanPortalUrl}/player_api.php?username=${username}&password=${password}&action=get_live_streams`;

  async function fetchJsonWithFallback(targetUrl) {
    // 1. Try direct fetch first
    try {
      const res = await fetch(targetUrl);
      if (res.ok) {
        const data = await res.json();
        if (Array.isArray(data) && data.length > 0) {
          console.log(`[Xtream] Direct API fetch succeeded (${data.length} items)`);
          return data;
        }
      }
    } catch (e) {
      console.warn(`[Xtream] Direct fetch failed for ${targetUrl}, trying proxy...`, e);
    }

    // 2. Try proxy options
    const proxies = [
      getProxyUrl(targetUrl),
      `https://corsproxy.io/?${encodeURIComponent(targetUrl)}`,
      `https://api.allorigins.win/raw?url=${encodeURIComponent(targetUrl)}`
    ];

    for (const pUrl of proxies) {
      try {
        const res = await fetch(pUrl);
        if (res.ok) {
          const data = await res.json();
          if (Array.isArray(data) && data.length > 0) {
            console.log(`[Xtream] Successfully fetched API payload via ${pUrl} (${data.length} items)`);
            return data;
          }
        }
      } catch (e) {
        console.warn(`[Xtream] Proxy attempt failed for ${pUrl}:`, e);
      }
    }

    return [];
  }

  console.log("[Xtream] Fetching categories from API...");
  const categories = await fetchJsonWithFallback(catTarget);

  console.log("[Xtream] Fetching streams from API...");
  const streams = await fetchJsonWithFallback(streamTarget);

  // Create category map
  const catMap = {};
  if (Array.isArray(categories)) {
    categories.forEach(cat => {
      catMap[cat.category_id] = cat.category_name;
    });
  }

  // Convert streams to M3U format — stream URL goes through getProxyUrl()
  let m3uLines = ['#EXTM3U'];
  if (Array.isArray(streams) && streams.length > 0) {
    streams.forEach(stream => {
      const streamName = stream.name || 'Unknown Channel';
      const streamId = stream.stream_id;
      const logo = stream.stream_icon || '';
      const categoryName = catMap[stream.category_id] || 'General';
      const rawStreamUrl = `${cleanPortalUrl}/live/${username}/${password}/${streamId}.m3u8`;
      const streamUrl = getProxyUrl(rawStreamUrl);

      m3uLines.push(`#EXTINF:-1 tvg-id="" tvg-name="${streamName}" tvg-logo="${logo}" group-title="${categoryName}",${streamName}`);
      m3uLines.push(streamUrl);
    });
    return m3uLines.join('\n');
  }

  return '';
}

// Load IPTV playlist from credentials
async function loadIPTVPlaylist() {
  const headerBadge = document.getElementById('channel-count-header');
  
  try {
    const rawPortalUrl = localStorage.getItem('iptv_portal_url') || 'http://portal5458.com:8080';
    const username = (localStorage.getItem('iptv_username') || 'SGmUC7q2U').trim();
    const password = (localStorage.getItem('iptv_password') || '4WM9WVsjG').trim();
    
    let portalUrl = rawPortalUrl.trim().replace(/\/+$/, '');
    if (!portalUrl.startsWith('http://') && !portalUrl.startsWith('https://')) {
      portalUrl = 'http://' + portalUrl;
    }

    console.log("[IPTV] Loading Xtream playlist from:", portalUrl);
    let rawM3U = '';
    
    if (portalUrl && username && password) {
      try {
        rawM3U = await fetchXtreamPlaylist(portalUrl, username, password);
      } catch (e) {
        console.warn("[IPTV] fetchXtreamPlaylist error:", e);
      }

      // Fallback 1: Try get.php m3u_plus export URL via proxy
      if (!rawM3U || rawM3U.trim().length === 0) {
        try {
          const directM3uUrl = getProxyUrl(`${portalUrl}/get.php?username=${username}&password=${password}&type=m3u_plus&output=ts`);
          console.log("[IPTV] Fetching fallback M3U export from get.php:", directM3uUrl);
          const res = await fetch(directM3uUrl);
          if (res.ok) {
            rawM3U = await res.text();
          }
        } catch (e) {
          console.warn("[IPTV] Direct get.php M3U fallback failed:", e);
        }
      }
    }
    
    // Fallback 2: Try local default playlist (/all.m3u) if Xtream API returned no streams
    if (!rawM3U || rawM3U.trim().length === 0) {
      try {
        console.log("[IPTV] Loading default static M3U playlist fallback (/all.m3u)...");
        const res = await fetch('./all.m3u');
        if (res.ok) {
          rawM3U = await res.text();
        }
      } catch (e) {
        console.warn("[IPTV] Failed to fetch static /all.m3u fallback:", e);
      }
    }

    if (rawM3U) {
      state.channels = parseM3U(rawM3U);
    }

    if (!state.channels || state.channels.length === 0) {
      throw new Error("No live channels returned from your Xtream IPTV account. Please check your credentials in Settings.");
    }
    
    // Extract category groups
    const groups = new Set();
    state.channels.forEach(ch => {
      if (ch.group) groups.add(ch.group);
    });
    
    state.categories = ['All Channels', 'Favorites', 'Recents', ...Array.from(groups).sort()];
    
    const countText = `${state.channels.length} Live Channels`;
    if (headerBadge) headerBadge.textContent = countText;
    
    const countEl = document.getElementById('filtered-count');
    if (countEl) countEl.textContent = countText;

    renderCategories();
    applyFilterAndRender();
  } catch (error) {
    console.error("Failed to parse IPTV playlist:", error);
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

// Render categories selection in Live TV sidebar
function renderCategories() {
  const container = document.getElementById('categories-container');
  if (!container) return;
  container.innerHTML = '';

  state.categories.forEach(category => {
    const btn = document.createElement('button');
    btn.className = `category-btn ${state.selectedCategory === category ? 'active' : ''}`;
    
    let iconName = 'tv';
    let count = 0;
    
    if (category === 'All Channels') {
      iconName = 'tv';
      count = state.channels.length;
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

  if (state.selectedCategory === 'All Channels') {
    list = [...state.channels];
  } else if (state.selectedCategory === 'Favorites') {
    list = state.channels.filter(ch => state.favorites.includes(ch.id));
  } else if (state.selectedCategory === 'Recents') {
    list = state.recents
      .map(id => state.channels.find(ch => ch.id === id))
      .filter(Boolean);
  } else {
    list = state.channels.filter(ch => ch.group === state.selectedCategory);
  }

  state.filteredChannels = list;

  const countEl = document.getElementById('filtered-count');
  if (countEl) countEl.textContent = `${list.length} channels`;
  
  const subtitleEl = document.getElementById('channel-list-info');
  if (subtitleEl) {
    subtitleEl.textContent = `Showing matching results in ${state.selectedCategory}`;
  }

  renderChannelsGrid();
}

// Render Live TV Channel Cards
function renderChannelsGrid() {
  const container = document.getElementById('channels-grid');
  const emptyState = document.getElementById('no-channels-found');
  if (!container) return;
  container.innerHTML = '';

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
  const videoEl = document.getElementById('video-player');
  if (videoEl) videoEl.style.display = '';

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
