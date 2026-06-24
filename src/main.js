import { createIcons, Menu, X, Play, Pause, Tv, Search, Info, AlertTriangle, RefreshCw, Volume2, Volume1, VolumeX, Maximize, SquareStack, ExternalLink, Star, Monitor, Settings, ArrowLeft } from 'lucide';
import { fetchAndParseM3U } from './parser';
import { IPTVPlayer } from './player';
import { searchMulti, getMovieDetails, getTVShowDetails, getTVSeasonDetails, getTMDBImageUrl, getTrending } from './tmdb';
import { getStreamSources } from './streamApi';
import './style.css';

// Initialize Lucide icons
const iconConfig = {
  icons: {
    Menu, X, Play, Pause, Tv, Search, Info, AlertTriangle, RefreshCw, Volume2, Volume1, VolumeX, Maximize, SquareStack, ExternalLink, Star, Monitor, Settings, ArrowLeft
  }
};

// Global State
const state = {
  currentMode: 'live', // 'live' or 'movies'
  channels: [],
  filteredChannels: [],
  categories: [],
  selectedCategory: 'All Channels',
  searchQuery: '',
  favorites: JSON.parse(localStorage.getItem('iptv_favorites') || '[]'),
  recents: JSON.parse(localStorage.getItem('iptv_recents') || '[]'),
  currentPlayingUrl: null,
  
  // Movies & TV State
  moviesSearchResults: [],
  selectedMedia: null,
  activeStreamSse: null,
  resolvedSources: [],
  activeSourceIndex: -1
};

// Constants
const PLAYLIST_URL = window.location.hostname.endsWith('github.io')
  ? 'https://tahillinvestments.github.io/tv-dinner/o_all.m3u'
  : './o_all.m3u';
const MAX_RECENTS = 20;

// Initialize components
let player;
let searchDebounceTimer;

document.addEventListener('DOMContentLoaded', async () => {
  // Init player
  player = new IPTVPlayer('video-player');
  
  // Render initial icons
  createIcons(iconConfig);

  // Setup DOM interaction
  setupSearch();
  setupMobileSidebar();
  setupModeToggle();
  setupSettingsModal();
  setupDetailsView();

  // Load playlist (Live TV mode data)
  try {
    const channelCountEl = document.getElementById('channel-count');
    state.channels = await fetchAndParseM3U(PLAYLIST_URL);
    
    // Extract unique categories (groups)
    const groups = new Set();
    state.channels.forEach(ch => {
      if (ch.group) groups.add(ch.group);
    });
    
    state.categories = ['All Channels', 'Favorites', 'Recents', ...Array.from(groups).sort()];
    
    // Update loading text
    channelCountEl.textContent = `${state.channels.length} Global Channels`;
    
    renderCategories();
    applyFilterAndRender();
  } catch (error) {
    console.error("Failed to load IPTV playlist:", error);
    const grid = document.getElementById('channels-grid');
    grid.innerHTML = `
      <div class="col-span-full py-12 text-center">
        <div class="w-12 h-12 mx-auto rounded-xl bg-red-500/10 border border-red-500/20 flex items-center justify-center text-red-400 mb-3">
          <i data-lucide="alert-triangle" class="w-6 h-6"></i>
        </div>
        <h3 class="text-sm font-semibold text-slate-200">Failed to load channels</h3>
        <p class="text-xs text-slate-500 mt-1">Make sure you are connected to the internet. We couldn't download the channel playlist.</p>
        <button onclick="window.location.reload()" class="mt-4 px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-semibold inline-flex items-center gap-1.5 transition-colors">
          <i data-lucide="refresh-cw" class="w-3.5 h-3.5"></i> Reload Application
        </button>
      </div>
    `;
    createIcons(iconConfig);
  }
});

// Setup sidebar layout categories list
function renderCategories() {
  const container = document.getElementById('categories-container');
  container.innerHTML = '';

  if (state.currentMode === 'movies') {
    // Show static guides/filters for movies mode
    const categories = [
      { label: 'Trending Movies', icon: 'monitor', filter: 'movie' },
      { label: 'Trending Series', icon: 'tv', filter: 'tv' },
      { label: 'Search Catalog', icon: 'search', filter: 'search' },
    ];
    categories.forEach(cat => {
      const btn = document.createElement('button');
      btn.className = 'category-btn flex items-center justify-between w-full px-3 py-2 text-xs rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-900/60 border border-transparent transition-all text-left';
      btn.innerHTML = `
        <div class="flex items-center gap-2.5 truncate">
          <i data-lucide="${cat.icon}" class="w-4 h-4 opacity-75 shrink-0"></i>
          <span class="truncate">${cat.label}</span>
        </div>
      `;
      btn.addEventListener('click', () => {
        // Mark active
        container.querySelectorAll('.category-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        closeDetailsView();
        if (cat.filter === 'search') {
          const searchInput = document.getElementById('search-input');
          if (searchInput) searchInput.focus();
        } else {
          state.searchQuery = '';
          state.moviesSearchResults = [];
          getTrending().then(data => {
            state.moviesSearchResults = (data.results || []).filter(r => r.media_type === cat.filter);
            renderMoviesCatalog(cat.label);
          }).catch(err => {
            console.error('Trending fetch error:', err);
            player.showToast('Failed to load ' + cat.label);
          });
        }
      });
      container.appendChild(btn);
    });
    createIcons(iconConfig);
    // Auto-trigger first tab (Trending Movies)
    if (container.querySelector('.category-btn')) {
      container.querySelector('.category-btn').click();
    }
    return;
  }

  state.categories.forEach(category => {
    const btn = document.createElement('button');
    btn.className = `category-btn flex items-center justify-between w-full px-3 py-2 text-xs rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-900/60 border border-transparent transition-all text-left ${
      state.selectedCategory === category ? 'active' : ''
    }`;
    
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
      closeMobileSidebar();
      document.querySelectorAll('.category-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      
      applyFilterAndRender();
      
      const gridContainer = document.querySelector('.channels-grid-container');
      if (gridContainer) {
        gridContainer.scrollTop = 0;
      }
    });

    container.appendChild(btn);
  });

  createIcons(iconConfig);
}

// Mobile sidebar controls
function setupMobileSidebar() {
  const toggle = document.getElementById('sidebar-toggle');
  const close = document.getElementById('sidebar-close');
  const sidebar = document.getElementById('app-sidebar');
  const overlay = document.getElementById('sidebar-overlay');

  const openDrawer = () => {
    sidebar.classList.remove('sidebar-closed');
    sidebar.classList.add('sidebar-open');
    overlay.classList.remove('hidden');
  };

  const closeDrawer = () => {
    sidebar.classList.remove('sidebar-open');
    sidebar.classList.add('sidebar-closed');
    overlay.classList.add('hidden');
  };

  toggle.addEventListener('click', openDrawer);
  close.addEventListener('click', closeDrawer);
  overlay.addEventListener('click', closeDrawer);
}

function closeMobileSidebar() {
  const sidebar = document.getElementById('app-sidebar');
  const overlay = document.getElementById('sidebar-overlay');
  sidebar.classList.remove('sidebar-open');
  sidebar.classList.add('sidebar-closed');
  overlay.classList.add('hidden');
}

// Mode Selection Toggle (Live TV vs Movies & TV)
function setupModeToggle() {
  const btnLive = document.getElementById('btn-mode-live');
  const btnMovies = document.getElementById('btn-mode-movies');
  const channelCountEl = document.getElementById('channel-count');

  const setMode = (mode) => {
    if (state.currentMode === mode) return;
    state.currentMode = mode;

    // Reset current selection views
    closeDetailsView();

    // Toggle active classes on buttons
    if (mode === 'live') {
      btnLive.classList.add('active');
      btnMovies.classList.remove('active');
      channelCountEl.textContent = `${state.channels.length} Global Channels`;
      
      // Reset inputs & values
      document.getElementById('search-input').placeholder = "Search channel by name...";
      document.getElementById('search-input-mobile').placeholder = "Search channels...";
      document.getElementById('search-input').value = '';
      document.getElementById('search-input-mobile').value = '';
      state.searchQuery = '';
      
      renderCategories();
      applyFilterAndRender();
    } else {
      btnLive.classList.remove('active');
      btnMovies.classList.add('active');
      channelCountEl.textContent = "Movies & Shows";
      
      // Update placeholders
      document.getElementById('search-input').placeholder = "Search movies & TV shows...";
      document.getElementById('search-input-mobile').placeholder = "Search movies/shows...";
      document.getElementById('search-input').value = '';
      document.getElementById('search-input-mobile').value = '';
      state.searchQuery = '';
      state.moviesSearchResults = [];
      
      renderCategories();
    }
  };

  btnLive.addEventListener('click', () => setMode('live'));
  btnMovies.addEventListener('click', () => setMode('movies'));
}

// Settings Modal Handling
function setupSettingsModal() {
  const toggleBtn = document.getElementById('settings-toggle-btn');
  const modal = document.getElementById('settings-modal');
  const closeBtn = document.getElementById('settings-close-btn');
  const cancelBtn = document.getElementById('settings-cancel-btn');
  const saveBtn = document.getElementById('settings-save-btn');
  const keyInput = document.getElementById('settings-tmdb-key');

  const openModal = () => {
    keyInput.value = localStorage.getItem('tmdb_api_key') || '';
    modal.classList.remove('hidden');
  };

  const closeModal = () => {
    modal.classList.add('hidden');
  };

  const saveSettings = () => {
    const key = keyInput.value.trim();
    if (key) {
      localStorage.setItem('tmdb_api_key', key);
      player.showToast("Settings Saved Successfully");
    } else {
      localStorage.removeItem('tmdb_api_key');
      player.showToast("Default fallback API key activated");
    }
    closeModal();
    
    // Refresh search results if in movies mode
    if (state.currentMode === 'movies' && state.searchQuery) {
      performMoviesSearch(state.searchQuery);
    }
  };

  toggleBtn.addEventListener('click', openModal);
  closeBtn.addEventListener('click', closeModal);
  cancelBtn.addEventListener('click', closeModal);
  saveBtn.addEventListener('click', saveSettings);
  
  // Close on outer backdrop click
  modal.addEventListener('click', (e) => {
    if (e.target === modal) closeModal();
  });
}

// Search Inputs
function setupSearch() {
  const searchInput = document.getElementById('search-input');
  const searchInputMobile = document.getElementById('search-input-mobile');

  const handleSearch = (e) => {
    const query = e.target.value.toLowerCase().trim();
    state.searchQuery = query;
    
    // Sync other input
    if (e.target === searchInput) {
      searchInputMobile.value = e.target.value;
    } else {
      searchInput.value = e.target.value;
    }

    if (state.currentMode === 'live') {
      applyFilterAndRender();
    } else {
      // Debounce TMDB search API calls
      clearTimeout(searchDebounceTimer);
      searchDebounceTimer = setTimeout(() => {
        performMoviesSearch(query);
      }, 500);
    }
  };

  searchInput.addEventListener('input', handleSearch);
  searchInputMobile.addEventListener('input', handleSearch);
}

// Search TMDB API
async function performMoviesSearch(query) {
  if (!query) {
    state.moviesSearchResults = [];
    renderMoviesCatalog();
    return;
  }

  // Show skeleton loading loaders
  const container = document.getElementById('channels-grid');
  container.innerHTML = `
    <div class="skeleton-card"></div>
    <div class="skeleton-card"></div>
    <div class="skeleton-card"></div>
    <div class="skeleton-card"></div>
  `;

  try {
    const data = await searchMulti(query);
    state.moviesSearchResults = data.results || [];
    renderMoviesCatalog();
  } catch (err) {
    console.error("TMDB Search failed:", err);
    player.showToast("Failed to fetch catalog search results");
    container.innerHTML = '';
    document.getElementById('no-channels-found').classList.remove('hidden');
  }
}

// Render Movies and TV Shows grid catalog list
function renderMoviesCatalog(categoryLabel) {
  const container = document.getElementById('channels-grid');
  const emptyState = document.getElementById('no-channels-found');
  container.innerHTML = '';

  const label = categoryLabel || (state.searchQuery ? 'Search Results' : 'Trending Movies');
  document.getElementById('current-category-name').textContent = label;
  document.getElementById('channel-list-info').textContent = state.searchQuery
    ? `Matching results for "${state.searchQuery}"`
    : 'Popular movies & television shows today';

  // Always use moviesSearchResults — populated by either trending or search
  const results = state.moviesSearchResults;

  if (results.length === 0) {
    if (state.searchQuery) {
      // Search returned no results
      emptyState.classList.remove('hidden');
      document.getElementById('filtered-count').textContent = '0 results';
      return;
    }

    // No results and no query — show placeholder
    emptyState.classList.add('hidden');
    document.getElementById('filtered-count').textContent = 'Catalog ready';
    container.innerHTML = `
      <div class="col-span-full py-12 text-center text-slate-400">
        <div class="w-12 h-12 mx-auto rounded-xl bg-slate-900 border border-slate-800/80 flex items-center justify-center mb-3">
          <i data-lucide="search" class="w-6 h-6"></i>
        </div>
        <h3 class="text-sm font-semibold text-slate-200">Catalog Search</h3>
        <p class="text-xs text-slate-500 mt-1 max-w-xs mx-auto">Type in the search bar above to look up movies or TV series and stream them instantly.</p>
      </div>
    `;
    createIcons(iconConfig);
    return;
  }

  emptyState.classList.add('hidden');
  document.getElementById('filtered-count').textContent = `${results.length} items`;

  results.forEach(item => {
    const title = item.title || item.name || 'Untitled';
    const isTV = item.media_type === 'tv';
    const releaseDate = item.release_date || item.first_air_date || '';
    const year = releaseDate ? releaseDate.split('-')[0] : 'N/A';
    const rating = item.vote_average ? item.vote_average.toFixed(1) : 'N/A';
    const imgUrl = getTMDBImageUrl(item.poster_path, 'w185') || 'https://via.placeholder.com/185x278/090e1a/475569?text=No+Poster';

    const card = document.createElement('div');
    card.className = "detail-item-card hover-scale";
    
    card.innerHTML = `
      <img src="${imgUrl}" alt="${title}" class="detail-item-poster" loading="lazy">
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

    card.addEventListener('click', () => openDetailsView(item));
    container.appendChild(card);
  });

  createIcons(iconConfig);
}

// Setup details view interactions
function setupDetailsView() {
  const backBtn = document.getElementById('details-back-btn');
  backBtn.addEventListener('click', closeDetailsView);

  const seasonSelect = document.getElementById('season-select');
  seasonSelect.addEventListener('change', (e) => {
    if (state.selectedMedia && state.selectedMedia.media_type === 'tv') {
      loadTVEpisodes(state.selectedMedia.id, e.target.value);
    }
  });
}

// Open detailed info for selected Movie / TV Show
async function openDetailsView(mediaItem) {
  state.selectedMedia = mediaItem;

  // Hide catalog grid, show info panel below player
  document.getElementById('channels-grid').parentElement.parentElement.classList.add('hidden');
  document.getElementById('details-section').classList.remove('hidden');

  // Show iframe in the shared player area, hide video tag
  const videoEl = document.getElementById('video-player');
  const embedWrapper = document.getElementById('embed-player-wrapper');
  const embedIframe = document.getElementById('embed-iframe');
  videoEl.style.display = 'none';
  embedWrapper.style.display = 'block';
  embedIframe.src = ''; // will be set by selectActiveSource

  // Set initial placeholders
  const title = mediaItem.title || mediaItem.name || 'Loading...';
  document.getElementById('details-title').textContent = title;
  document.getElementById('details-year').textContent = (mediaItem.release_date || mediaItem.first_air_date || 'N/A').split('-')[0];
  document.getElementById('details-rating').innerHTML = `⭐ ${mediaItem.vote_average ? mediaItem.vote_average.toFixed(1) : 'N/A'}`;
  document.getElementById('details-runtime').textContent = '';
  document.getElementById('tv-selectors').classList.add('hidden');

  // Reset sources
  document.getElementById('sources-list').innerHTML = '';
  document.getElementById('sources-status').textContent = 'Choose a server to stream.';
  closeActiveSse();

  createIcons(iconConfig);

  // Load detailed information via API
  const isTV = mediaItem.media_type === 'tv';
  try {
    if (isTV) {
      const details = await getTVShowDetails(mediaItem.id);
      document.getElementById('details-runtime').textContent = `${details.number_of_seasons} Seasons`;

      // Show TV selectors
      document.getElementById('tv-selectors').classList.remove('hidden');

      // Populate seasons dropdown list
      const seasonSelect = document.getElementById('season-select');
      seasonSelect.innerHTML = '';
      details.seasons.forEach(season => {
        if (season.season_number === 0 && details.seasons.length > 1) return;
        const opt = document.createElement('option');
        opt.value = season.season_number;
        opt.textContent = season.name || `Season ${season.season_number}`;
        seasonSelect.appendChild(opt);
      });

      // Load episodes for the first season
      if (details.seasons.length > 0) {
        const firstSeasonNum = details.seasons[0].season_number === 0 && details.seasons.length > 1
          ? details.seasons[1].season_number
          : details.seasons[0].season_number;
        seasonSelect.value = firstSeasonNum;
        loadTVEpisodes(mediaItem.id, firstSeasonNum);
      }
    } else {
      const details = await getMovieDetails(mediaItem.id);
      document.getElementById('details-runtime').textContent = `${details.runtime || 'N/A'} min`;
      startStreamResolution({ type: 'movie', id: mediaItem.id });
    }
  } catch (err) {
    console.error("Failed to load TMDB details:", err);
    player.showToast("Failed to retrieve detailed metadata");
  }
}

// Load episodes list grid for TV Show
async function loadTVEpisodes(tvId, seasonNumber) {
  const grid = document.getElementById('episodes-grid');
  grid.innerHTML = '<span class="text-xs text-slate-500">Loading episodes...</span>';

  try {
    const data = await getTVSeasonDetails(tvId, seasonNumber);
    grid.innerHTML = '';
    
    (data.episodes || []).forEach(ep => {
      const btn = document.createElement('button');
      btn.className = "episode-btn";
      btn.textContent = ep.episode_number;
      btn.title = ep.name || `Episode ${ep.episode_number}`;
      
      btn.addEventListener('click', () => {
        document.querySelectorAll('.episode-btn').forEach(b => b.classList.remove('active'));
        btn.classList.add('active');
        
        // Start streaming resolving
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

// Embed providers — all accept TMDB IDs directly
const EMBED_PROVIDERS = [
  {
    name: 'Server 1',
    movie: (id) => `https://vidsrc.xyz/embed/movie?tmdb=${id}`,
    tv: (id, s, e) => `https://vidsrc.xyz/embed/tv?tmdb=${id}&season=${s}&episode=${e}`,
  },
  {
    name: 'Server 2',
    movie: (id) => `https://embed.su/embed/movie/${id}`,
    tv: (id, s, e) => `https://embed.su/embed/tv/${id}/${s}/${e}`,
  },
  {
    name: 'Server 3',
    movie: (id) => `https://vidsrc.to/embed/movie/${id}`,
    tv: (id, s, e) => `https://vidsrc.to/embed/tv/${id}/${s}/${e}`,
  },
  {
    name: 'Server 4',
    movie: (id) => `https://www.2embed.cc/embed/${id}`,
    tv: (id, s, e) => `https://www.2embed.cc/embedtv/${id}&s=${s}&e=${e}`,
  },
  {
    name: 'Server 5',
    movie: (id) => `https://multiembed.mov/?video_id=${id}&tmdb=1`,
    tv: (id, s, e) => `https://multiembed.mov/?video_id=${id}&tmdb=1&s=${s}&e=${e}`,
  },
];

// Start streaming via iframe embed providers
function startStreamResolution({ type, id, season, episode }) {
  closeActiveSse();

  const listContainer = document.getElementById('sources-list');
  const statusText = document.getElementById('sources-status');
  const embedWrapper = document.getElementById('embed-player-wrapper');
  const embedIframe = document.getElementById('embed-iframe');

  listContainer.innerHTML = '';
  state.resolvedSources = [];
  state.activeSourceIndex = -1;
  embedWrapper.style.display = 'none';
  embedIframe.src = '';

  statusText.textContent = 'Choose a server to start streaming:';

  EMBED_PROVIDERS.forEach((provider, index) => {
    const embedUrl = type === 'tv'
      ? provider.tv(id, season, episode)
      : provider.movie(id);

    state.resolvedSources.push({ name: provider.name, url: embedUrl });

    const btn = document.createElement('button');
    btn.className = `source-item-btn ${index === 0 ? 'active' : ''}`;
    btn.innerHTML = `
      <span>${provider.name}</span>
      <span class="source-item-badge">Embed</span>
    `;
    btn.addEventListener('click', () => selectActiveSource(index));
    listContainer.appendChild(btn);
  });

  // Auto-load first server
  selectActiveSource(0);
}


// Select source and load in the shared iframe player
let shieldTimer = null;

function selectActiveSource(index) {
  if (index < 0 || index >= state.resolvedSources.length) return;

  state.activeSourceIndex = index;
  const source = state.resolvedSources[index];

  // Update UI button active state
  const buttons = document.querySelectorAll('.source-item-btn');
  buttons.forEach((btn, idx) => {
    btn.classList.toggle('active', idx === index);
  });

  // Load URL into the shared player iframe
  const embedIframe = document.getElementById('embed-iframe');
  const shield = document.getElementById('embed-shield');

  if (embedIframe) embedIframe.src = source.url;

  // Shield logic: block stray popup-triggering clicks, drop on intent
  if (shield) {
    shield.style.display = 'block';
    clearTimeout(shieldTimer);
    shield.onclick = () => {
      shield.style.display = 'none';
      clearTimeout(shieldTimer);
      shieldTimer = setTimeout(() => {
        if (shield) shield.style.display = 'block';
      }, 5000);
    };
  }

  const statusText = document.getElementById('sources-status');
  if (statusText) statusText.textContent = `Streaming via ${source.name}. Click player to interact. Switch servers if it doesn't load.`;
}

// Close ongoing EventSource client stream resolver
function closeActiveSse() {
  if (state.activeStreamSse) {
    console.log('Closing active stream connection');
    if (typeof state.activeStreamSse.cancel === 'function') {
      state.activeStreamSse.cancel();
    } else if (typeof state.activeStreamSse.close === 'function') {
      state.activeStreamSse.close();
    }
    state.activeStreamSse = null;
  }
}

// Return to channels listing catalog
function closeDetailsView() {
  closeActiveSse();
  state.selectedMedia = null;
  state.resolvedSources = [];

  // Stop and hide iframe, restore video player
  const embedIframe = document.getElementById('embed-iframe');
  const embedWrapper = document.getElementById('embed-player-wrapper');
  const videoEl = document.getElementById('video-player');
  if (embedIframe) embedIframe.src = '';
  if (embedWrapper) embedWrapper.style.display = 'none';
  if (videoEl) videoEl.style.display = '';

  // Hide details panel & reveal catalog grid
  document.getElementById('details-section').classList.add('hidden');
  document.getElementById('channels-grid').parentElement.parentElement.classList.remove('hidden');
}

// State filtering engine for Live IPTV channels
function applyFilterAndRender() {
  let list = [];

  // 1. Apply category selection
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

  // 2. Apply search query
  if (state.searchQuery) {
    list = list.filter(ch => ch.name.toLowerCase().includes(state.searchQuery));
  }

  state.filteredChannels = list;

  // Update DOM headers
  document.getElementById('current-category-name').textContent = state.selectedCategory;
  document.getElementById('filtered-count').textContent = `${list.length} channels`;
  document.getElementById('channel-list-info').textContent = state.searchQuery 
    ? `Filtered by search "${state.searchQuery}"` 
    : `Showing matching results in ${state.selectedCategory}`;

  renderChannelsGrid();
}

// Dynamic rendering of Live TV channel cards
function renderChannelsGrid() {
  const container = document.getElementById('channels-grid');
  const emptyState = document.getElementById('no-channels-found');
  container.innerHTML = '';

  if (state.filteredChannels.length === 0) {
    emptyState.classList.remove('hidden');
    return;
  }
  emptyState.classList.add('hidden');

  state.filteredChannels.forEach(channel => {
    const isFav = state.favorites.includes(channel.id);
    const isActive = state.currentPlayingUrl === channel.url;
    
    const card = document.createElement('div');
    card.className = `channel-card hover-scale p-4 rounded-xl flex items-center justify-between gap-3 cursor-pointer select-none ${
      isActive ? 'active' : ''
    }`;
    
    card.addEventListener('mousemove', (e) => {
      const rect = card.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      card.style.setProperty('--mouse-x', `${x}px`);
      card.style.setProperty('--mouse-y', `${y}px`);
    });

    const content = document.createElement('div');
    content.className = "flex items-center gap-3 min-w-0 flex-1";
    content.addEventListener('click', () => playChannel(channel));

    const logoContainer = document.createElement('div');
    logoContainer.className = "w-11 h-11 rounded-lg bg-slate-900 border border-slate-800/80 flex items-center justify-center shrink-0 overflow-hidden relative";
    
    if (channel.logo) {
      const img = document.createElement('img');
      img.src = channel.logo;
      img.alt = channel.name;
      img.className = "w-full h-full object-contain p-1";
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
    details.className = "min-w-0 flex-1";
    details.innerHTML = `
      <h4 class="text-xs font-bold text-slate-200 truncate pr-2 group-hover:text-indigo-400 transition-colors">${channel.name}</h4>
      <span class="text-[9px] bg-slate-950 border border-slate-900 text-slate-500 px-1.5 py-0.5 rounded-full mt-1.5 inline-block truncate max-w-full">${channel.group}</span>
    `;

    content.appendChild(logoContainer);
    content.appendChild(details);

    const favBtn = document.createElement('button');
    favBtn.className = `p-2 hover:bg-slate-800/80 rounded-lg text-slate-500 hover:text-amber-400 transition-colors shrink-0 ${isFav ? 'text-amber-400' : ''}`;
    favBtn.ariaLabel = "Add to Favorites";
    favBtn.innerHTML = `<i data-lucide="star" class="w-4 h-4 ${isFav ? 'fill-amber-400 text-amber-400' : ''}"></i>`;
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

// Dynamic avatar generation if no channel logo
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

// Action triggers for Live channels
function playChannel(channel) {
  // If playing live channel, make sure to close any active movie streams SSE connection
  closeActiveSse();

  state.currentPlayingUrl = channel.url;
  player.playChannel(channel);
  
  addToRecents(channel.id);
  
  document.querySelectorAll('.channel-card').forEach(card => {
    card.classList.remove('active');
  });
  
  applyFilterAndRender();
}

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
