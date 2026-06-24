import { createIcons, Menu, X, Play, Pause, Tv, Search, Info, AlertTriangle, RefreshCw, Volume2, Volume1, VolumeX, Maximize, SquareStack, ExternalLink, Star, Monitor } from 'lucide';
import { fetchAndParseM3U } from './parser';
import { IPTVPlayer } from './player';
import './style.css';

// Initialize Lucide icons
const iconConfig = {
  icons: {
    Menu, X, Play, Pause, Tv, Search, Info, AlertTriangle, RefreshCw, Volume2, Volume1, VolumeX, Maximize, SquareStack, ExternalLink, Star, Monitor
  }
};

// Global State
const state = {
  channels: [],
  filteredChannels: [],
  categories: [],
  selectedCategory: 'All Channels',
  searchQuery: '',
  favorites: JSON.parse(localStorage.getItem('iptv_favorites') || '[]'),
  recents: JSON.parse(localStorage.getItem('iptv_recents') || '[]'),
  currentPlayingUrl: null
};

// Constants
const ENGLISH_PLAYLIST_URL = 'https://cdn.jsdelivr.net/gh/iptv-org/iptv@gh-pages/languages/eng.m3u';
const MAX_RECENTS = 20;

// Initialize components
let player;

document.addEventListener('DOMContentLoaded', async () => {
  // Init player
  player = new IPTVPlayer('video-player');
  
  // Render initial icons
  createIcons(iconConfig);

  // Setup DOM interaction
  setupSearch();
  setupMobileSidebar();

  // Load playlist
  try {
    const channelCountEl = document.getElementById('channel-count');
    state.channels = await fetchAndParseM3U(ENGLISH_PLAYLIST_URL);
    
    // Extract unique categories (groups)
    const groups = new Set();
    state.channels.forEach(ch => {
      if (ch.group) groups.add(ch.group);
    });
    
    state.categories = ['All Channels', 'Favorites', 'Recents', ...Array.from(groups).sort()];
    
    // Update loading text
    channelCountEl.textContent = `${state.channels.length} English Channels`;
    
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
      // Close mobile sidebar if open
      closeMobileSidebar();
      // Render active state on sidebar buttons
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

// Search Inputs
function setupSearch() {
  const searchInput = document.getElementById('search-input');
  const searchInputMobile = document.getElementById('search-input-mobile');

  const handleSearch = (e) => {
    state.searchQuery = e.target.value.toLowerCase().trim();
    
    // Sync other input
    if (e.target === searchInput) {
      searchInputMobile.value = e.target.value;
    } else {
      searchInput.value = e.target.value;
    }
    
    applyFilterAndRender();
  };

  searchInput.addEventListener('input', handleSearch);
  searchInputMobile.addEventListener('input', handleSearch);
}

// State filtering engine
function applyFilterAndRender() {
  let list = [];

  // 1. Apply category selection
  if (state.selectedCategory === 'All Channels') {
    list = [...state.channels];
  } else if (state.selectedCategory === 'Favorites') {
    list = state.channels.filter(ch => state.favorites.includes(ch.id));
  } else if (state.selectedCategory === 'Recents') {
    // Keep recent order
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

// Dynamic rendering of cards
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
    
    // Setup mouse move coordinates for glow effect
    card.addEventListener('mousemove', (e) => {
      const rect = card.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      card.style.setProperty('--mouse-x', `${x}px`);
      card.style.setProperty('--mouse-y', `${y}px`);
    });

    // Content container (clickable to play)
    const content = document.createElement('div');
    content.className = "flex items-center gap-3 min-w-0 flex-1";
    content.addEventListener('click', () => playChannel(channel));

    // Logo render
    const logoContainer = document.createElement('div');
    logoContainer.className = "w-11 h-11 rounded-lg bg-slate-900 border border-slate-800/80 flex items-center justify-center shrink-0 overflow-hidden relative";
    
    if (channel.logo) {
      const img = document.createElement('img');
      img.src = channel.logo;
      img.alt = channel.name;
      img.className = "w-full h-full object-contain p-1";
      img.loading = "lazy";
      // Handle loading error - show placeholder
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

    // Favorite Button Action (not triggering card click)
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
  
  // Get initials
  const parts = name.split(/\s+/).filter(Boolean);
  let initials = "";
  if (parts.length > 0) initials += parts[0][0];
  if (parts.length > 1) initials += parts[1][0];
  if (!initials) initials = "TV";
  
  placeholder.textContent = initials.substring(0, 2);
  return placeholder;
}

// Action triggers
function playChannel(channel) {
  state.currentPlayingUrl = channel.url;
  player.playChannel(channel);
  
  // Track recents list
  addToRecents(channel.id);
  
  // Update selected highlight in the grid
  document.querySelectorAll('.channel-card').forEach(card => {
    card.classList.remove('active');
  });
  
  // Refresh current grid rendering to make active channel selected
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
  
  // Re-render sidebar category counts
  renderCategories();
  
  // Refresh grid showing changes
  applyFilterAndRender();
}

function addToRecents(id) {
  const index = state.recents.indexOf(id);
  if (index !== -1) {
    // Move to top/front
    state.recents.splice(index, 1);
  }
  state.recents.unshift(id);
  
  // Cap recents count
  if (state.recents.length > MAX_RECENTS) {
    state.recents.pop();
  }
  
  localStorage.setItem('iptv_recents', JSON.stringify(state.recents));
  
  // Refresh category counts
  renderCategories();
}
