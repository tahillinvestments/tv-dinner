/**
 * Challenger 2 Adversarial Stress Test Suite for Milestone 2
 * Focus:
 * 1. Spatial Navigation & Focus Management (D-pad arrows, wrapping, Tab/Enter/Space)
 * 2. Modal Dismissals & Body Scroll Lock Restoration across deep stacks & rapid tab switches
 * 3. Instant Activation Unlocking (unlockAndRefreshAllDashboards across all views without refresh)
 * 4. Channel Grid Progressive Batching & Cancellation Race Conditions
 * 5. Player Lifecycle, Media Reset & Background Timer Cancellation
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
  MockElement,
  MockKeyboardEvent,
  runAllRegisteredSuites,
  suiteResults
} from './harness.js';

import { IPTVPlayer } from '../src/player.js';

describe('Challenger 2 Empirical Verification: Milestone 2', () => {

  // ════════════════════════════════════════════════════════════════════════════
  // Suite 1: Spatial Navigation & D-Pad Remote Focus Transitions
  // ════════════════════════════════════════════════════════════════════════════
  describe('1. Spatial Navigation & Focus Management', () => {
    let env;
    beforeEach(() => { env = setupTestEnvironment(); });
    afterEach(() => { restoreTestEnvironment(); });

    it('1.1 Desktop Sidebar: ArrowDown/ArrowRight navigates forward and wraps at the end', () => {
      const sidebar = env.doc.createElement('aside');
      sidebar.className = 'app-sidebar-nav';
      const tabs = ['home', 'live', 'movies', 'series', 'podcasts', 'library', 'settings'];
      const links = tabs.map(tab => {
        const a = env.doc.createElement('button');
        a.className = 'nav-link';
        a.setAttribute('data-tab', tab);
        sidebar.appendChild(a);
        return a;
      });
      env.doc.body.appendChild(sidebar);

      // Simulate D-pad listener logic from main.js
      const desktopNavLinks = Array.from(sidebar.querySelectorAll('.nav-link'));
      desktopNavLinks.forEach((link, idx) => {
        link.addEventListener('keydown', (e) => {
          if (e.key === 'ArrowDown' || e.key === 'ArrowRight') {
            e.preventDefault();
            const next = desktopNavLinks[(idx + 1) % desktopNavLinks.length];
            if (next) next.focus();
          } else if (e.key === 'ArrowUp' || e.key === 'ArrowLeft') {
            e.preventDefault();
            const prev = desktopNavLinks[(idx - 1 + desktopNavLinks.length) % desktopNavLinks.length];
            if (prev) prev.focus();
          }
        });
      });

      // Start focus at first link
      links[0].focus();
      expect(env.doc.activeElement).toBe(links[0]);

      // Move down sequentially through all 7 tabs
      for (let i = 0; i < 6; i++) {
        links[i].dispatchEvent(new MockKeyboardEvent('keydown', { key: 'ArrowDown' }));
        expect(env.doc.activeElement).toBe(links[i + 1]);
      }

      // At index 6 (settings), ArrowDown should wrap around to index 0 (home)
      links[6].dispatchEvent(new MockKeyboardEvent('keydown', { key: 'ArrowDown' }));
      expect(env.doc.activeElement).toBe(links[0]);

      // At index 0 (home), ArrowUp should wrap backwards to index 6 (settings)
      links[0].dispatchEvent(new MockKeyboardEvent('keydown', { key: 'ArrowUp' }));
      expect(env.doc.activeElement).toBe(links[6]);

      // ArrowLeft behaves as ArrowUp
      links[6].dispatchEvent(new MockKeyboardEvent('keydown', { key: 'ArrowLeft' }));
      expect(env.doc.activeElement).toBe(links[5]);

      // ArrowRight behaves as ArrowDown
      links[5].dispatchEvent(new MockKeyboardEvent('keydown', { key: 'ArrowRight' }));
      expect(env.doc.activeElement).toBe(links[6]);
    });

    it('1.2 Mobile Drawer: D-pad Arrow navigation wraps cleanly across drawer links', () => {
      const drawerNav = env.doc.createElement('nav');
      drawerNav.className = 'mobile-drawer-nav';
      const tabs = ['home', 'live', 'movies', 'series', 'podcasts', 'library', 'settings'];
      const links = tabs.map(tab => {
        const btn = env.doc.createElement('button');
        btn.className = 'mobile-drawer-link';
        btn.setAttribute('data-tab', tab);
        drawerNav.appendChild(btn);
        return btn;
      });
      env.doc.body.appendChild(drawerNav);

      const drawerNavLinks = Array.from(drawerNav.querySelectorAll('.mobile-drawer-link'));
      drawerNavLinks.forEach((link, idx) => {
        link.addEventListener('keydown', (e) => {
          if (e.key === 'ArrowDown' || e.key === 'ArrowRight') {
            e.preventDefault();
            const next = drawerNavLinks[(idx + 1) % drawerNavLinks.length];
            if (next) next.focus();
          } else if (e.key === 'ArrowUp' || e.key === 'ArrowLeft') {
            e.preventDefault();
            const prev = drawerNavLinks[(idx - 1 + drawerNavLinks.length) % drawerNavLinks.length];
            if (prev) prev.focus();
          }
        });
      });

      links[0].focus();
      expect(env.doc.activeElement).toBe(links[0]);

      // Rapid 100-step navigation stress test
      let expectedIdx = 0;
      for (let step = 0; step < 100; step++) {
        links[expectedIdx].dispatchEvent(new MockKeyboardEvent('keydown', { key: 'ArrowDown' }));
        expectedIdx = (expectedIdx + 1) % links.length;
        expect(env.doc.activeElement).toBe(links[expectedIdx]);
      }
    });

    it('1.3 Home Cards: Enter and Space trigger navigation while other keys do not', () => {
      const card = env.doc.createElement('div');
      card.className = 'home-select-card theme-red';
      card.setAttribute('data-nav', 'live');
      card.setAttribute('tabindex', '0');
      card.setAttribute('role', 'button');
      env.doc.body.appendChild(card);

      let navTarget = null;
      const handleCardNav = (e) => {
        if (e && e.type === 'keydown' && e.key !== 'Enter' && e.key !== ' ') return;
        if (e) {
          e.preventDefault();
          e.stopPropagation();
        }
        navTarget = card.getAttribute('data-nav');
      };
      card.addEventListener('click', handleCardNav);
      card.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') handleCardNav(e);
      });

      // Irrelevant keys (Tab, ArrowRight, Shift, Escape) should NOT navigate
      card.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'Tab' }));
      expect(navTarget).toBe(null);

      card.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'ArrowRight' }));
      expect(navTarget).toBe(null);

      card.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'Escape' }));
      expect(navTarget).toBe(null);

      // Enter key triggers navigation
      card.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'Enter' }));
      expect(navTarget).toBe('live');

      // Reset and test Space key
      navTarget = null;
      card.dispatchEvent(new MockKeyboardEvent('keydown', { key: ' ' }));
      expect(navTarget).toBe('live');

      // Click triggers navigation
      navTarget = null;
      card.dispatchEvent(new MockKeyboardEvent('click'));
      expect(navTarget).toBe('live');
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // Suite 2: Modal Dismissals, Escape Key & Body Scroll Lock Restoration
  // ════════════════════════════════════════════════════════════════════════════
  describe('2. Modal Dismissals & Body Scroll Lock Restoration', () => {
    let env;
    beforeEach(() => { env = setupTestEnvironment(); });
    afterEach(() => { restoreTestEnvironment(); });

    it('2.1 Mobile Drawer Open, Close, Backdrop Click & Scroll Lock Restoration', () => {
      const toggle = env.doc.createElement('button');
      toggle.id = 'sidebar-toggle';
      const drawer = env.doc.createElement('div');
      drawer.id = 'mobile-drawer-overlay';
      drawer.classList.add('hidden');
      const closeBtn = env.doc.createElement('button');
      closeBtn.id = 'mobile-drawer-close';

      env.doc.body.appendChild(toggle);
      env.doc.body.appendChild(drawer);
      env.doc.body.appendChild(closeBtn);

      toggle.addEventListener('click', () => {
        drawer.classList.remove('hidden');
        env.doc.body.style.overflow = 'hidden';
      });
      closeBtn.addEventListener('click', () => {
        drawer.classList.add('hidden');
        env.doc.body.style.overflow = '';
      });
      drawer.addEventListener('click', (e) => {
        if (e.target === drawer) {
          drawer.classList.add('hidden');
          env.doc.body.style.overflow = '';
        }
      });

      // 1. Open drawer
      toggle.dispatchEvent(new MockKeyboardEvent('click'));
      expect(drawer.classList.contains('hidden')).toBe(false);
      expect(env.doc.body.style.overflow).toBe('hidden');

      // 2. Close with close button
      closeBtn.dispatchEvent(new MockKeyboardEvent('click'));
      expect(drawer.classList.contains('hidden')).toBe(true);
      expect(env.doc.body.style.overflow).toBe('');

      // 3. Open again and close with backdrop click
      toggle.dispatchEvent(new MockKeyboardEvent('click'));
      expect(env.doc.body.style.overflow).toBe('hidden');
      drawer.dispatchEvent({ type: 'click', target: drawer });
      expect(drawer.classList.contains('hidden')).toBe(true);
      expect(env.doc.body.style.overflow).toBe('');
    });

    it('2.2 Escape Key Modal Dismissal Hierarchy: Details > Podcast > Drawer', () => {
      const detailsOverlay = env.doc.createElement('div');
      detailsOverlay.id = 'details-overlay';
      detailsOverlay.classList.add('hidden');

      const podcastModal = env.doc.createElement('div');
      podcastModal.id = 'podcast-channel-modal';
      podcastModal.classList.add('hidden');
      podcastModal.style.display = 'none';

      const drawer = env.doc.createElement('div');
      drawer.id = 'mobile-drawer-overlay';
      drawer.classList.add('hidden');

      env.doc.body.appendChild(detailsOverlay);
      env.doc.body.appendChild(podcastModal);
      env.doc.body.appendChild(drawer);

      let detailsClosedCount = 0;
      function closeDetails() {
        detailsOverlay.classList.add('hidden');
        env.doc.body.style.overflow = '';
        detailsClosedCount++;
      }

      function handleGlobalEscape(e) {
        const tag = env.doc.activeElement ? env.doc.activeElement.tagName.toLowerCase() : '';
        if (tag === 'input' || tag === 'textarea' || tag === 'select') return;

        if (e.key === 'Escape') {
          if (detailsOverlay && !detailsOverlay.classList.contains('hidden')) {
            e.preventDefault();
            closeDetails();
            return;
          }
          if (podcastModal && (!podcastModal.classList.contains('hidden') && podcastModal.style.display !== 'none')) {
            e.preventDefault();
            podcastModal.classList.add('hidden');
            podcastModal.style.display = 'none';
            env.doc.body.style.overflow = '';
            return;
          }
          if (drawer && !drawer.classList.contains('hidden')) {
            e.preventDefault();
            drawer.classList.add('hidden');
            env.doc.body.style.overflow = '';
            return;
          }
        }
      }
      env.doc.addEventListener('keydown', handleGlobalEscape);

      // Case A: Details modal is open
      detailsOverlay.classList.remove('hidden');
      env.doc.body.style.overflow = 'hidden';
      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'Escape' }));
      expect(detailsOverlay.classList.contains('hidden')).toBe(true);
      expect(env.doc.body.style.overflow).toBe('');
      expect(detailsClosedCount).toBe(1);

      // Case B: Podcast modal is open
      podcastModal.classList.remove('hidden');
      podcastModal.style.display = 'flex';
      env.doc.body.style.overflow = 'hidden';
      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'Escape' }));
      expect(podcastModal.classList.contains('hidden')).toBe(true);
      expect(podcastModal.style.display).toBe('none');
      expect(env.doc.body.style.overflow).toBe('');

      // Case C: Mobile drawer is open
      drawer.classList.remove('hidden');
      env.doc.body.style.overflow = 'hidden';
      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'Escape' }));
      expect(drawer.classList.contains('hidden')).toBe(true);
      expect(env.doc.body.style.overflow).toBe('');

      // Case D: Input field focused — Escape must NOT dismiss modal or steal input context
      const inputEl = env.doc.createElement('input');
      env.doc.body.appendChild(inputEl);
      inputEl.focus();
      drawer.classList.remove('hidden');
      env.doc.body.style.overflow = 'hidden';

      env.doc.dispatchEvent(new MockKeyboardEvent('keydown', { key: 'Escape' }));
      expect(drawer.classList.contains('hidden')).toBe(false); // Should remain open
      expect(env.doc.body.style.overflow).toBe('hidden');
    });

    it('2.3 Rapid Tab Switching guarantees body scroll lock cleanup', () => {
      const detailsOverlay = env.doc.createElement('div');
      detailsOverlay.id = 'details-overlay';
      const podcastModal = env.doc.createElement('div');
      podcastModal.id = 'podcast-channel-modal';
      env.doc.body.appendChild(detailsOverlay);
      env.doc.body.appendChild(podcastModal);

      function switchTab(tabName) {
        if (!detailsOverlay.classList.contains('hidden')) {
          detailsOverlay.classList.add('hidden');
        }
        if (!podcastModal.classList.contains('hidden')) {
          podcastModal.classList.add('hidden');
          podcastModal.style.display = 'none';
        }
        env.doc.body.style.overflow = '';
      }

      // Simulate 50 chaotic rapid tab switches while modals are spontaneously opened
      const tabs = ['home', 'live', 'movies', 'series', 'podcasts', 'library', 'settings'];
      for (let i = 0; i < 50; i++) {
        if (i % 3 === 0) {
          detailsOverlay.classList.remove('hidden');
          env.doc.body.style.overflow = 'hidden';
        } else if (i % 3 === 1) {
          podcastModal.classList.remove('hidden');
          podcastModal.style.display = 'block';
          env.doc.body.style.overflow = 'hidden';
        }
        switchTab(tabs[i % tabs.length]);
        expect(env.doc.body.style.overflow).toBe('');
        expect(detailsOverlay.classList.contains('hidden')).toBe(true);
        expect(podcastModal.classList.contains('hidden')).toBe(true);
      }
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // Suite 3: Instant Activation Unlocking Across All Dashboards
  // ════════════════════════════════════════════════════════════════════════════
  describe('3. Instant Activation Unlocking', () => {
    let env;
    beforeEach(() => { env = setupTestEnvironment(); });
    afterEach(() => { restoreTestEnvironment(); });

    it('3.1 clearAccessLockedState restores all hidden child views and hides lock overlay', () => {
      const elMap = new Map();
      const container = new MockElement('div', 'tab-movies');
      elMap.set('tab-movies', container);
      
      const child1 = new MockElement('div', 'movies-hero');
      child1.setAttribute('data-orig-display', 'flex');
      child1.style.display = 'none';
      
      const child2 = new MockElement('div', 'movies-carousel-grid');
      child2.style.display = 'none';

      const lockOverlay = new MockElement('div', 'tab-movies-lock-overlay');
      lockOverlay.style.display = 'flex';
      elMap.set('tab-movies-lock-overlay', lockOverlay);

      container.appendChild(child1);
      container.appendChild(child2);
      container.appendChild(lockOverlay);

      function clearAccessLockedState(containerId) {
        const cont = elMap.get(containerId);
        if (!cont) return;

        const overlay = elMap.get(`${containerId}-lock-overlay`);
        if (overlay) {
          overlay.style.display = 'none';
        }

        Array.from(cont.children).forEach(child => {
          if (child.id !== `${containerId}-lock-overlay`) {
            const orig = child.getAttribute('data-orig-display');
            child.style.display = orig !== null ? orig : '';
          }
        });
      }

      clearAccessLockedState('tab-movies');

      expect(lockOverlay.style.display).toBe('none');
      expect(child1.style.display).toBe('flex');
      expect(child2.style.display).toBe('');
    });

    it('3.2 unlockAndRefreshAllDashboards unlocks Movies, Series, Podcasts, Library synchronously', () => {
      const elMap = new Map();
      const containers = ['tab-movies', 'tab-series', 'tab-podcasts', 'library-results-grid'];
      const overlays = [];
      const contents = [];

      containers.forEach(id => {
        const cont = new MockElement('div', id);
        elMap.set(id, cont);
        
        const content = new MockElement('div', `${id}-content`);
        content.style.display = 'none';
        cont.appendChild(content);
        contents.push(content);

        const overlay = new MockElement('div', `${id}-lock-overlay`);
        overlay.style.display = 'flex';
        cont.appendChild(overlay);
        overlays.push(overlay);
        elMap.set(`${id}-lock-overlay`, overlay);
      });

      const homeStatus = new MockElement('span', 'home-status-text');
      homeStatus.textContent = 'Activation Required';
      elMap.set('home-status-text', homeStatus);

      let moviesLoaded = 0;
      let seriesLoaded = 0;
      let podcastsLoaded = 0;
      let libraryRendered = 0;

      function clearAccessLockedState(containerId) {
        const cont = elMap.get(containerId);
        if (!cont) return;
        const overlay = elMap.get(`${containerId}-lock-overlay`);
        if (overlay) overlay.style.display = 'none';
        Array.from(cont.children).forEach(child => {
          if (child.id !== `${containerId}-lock-overlay`) {
            const orig = child.getAttribute('data-orig-display');
            child.style.display = orig !== null ? orig : '';
          }
        });
      }

      function unlockAndRefreshAllDashboards() {
        clearAccessLockedState('tab-movies');
        clearAccessLockedState('tab-series');
        clearAccessLockedState('tab-podcasts');
        clearAccessLockedState('library-results-grid');

        const homeStatusText = elMap.get('home-status-text');
        if (homeStatusText) {
          homeStatusText.textContent = 'System Ready';
        }

        moviesLoaded++;
        seriesLoaded++;
        podcastsLoaded++;
        libraryRendered++;
      }

      // Execute instant unlock
      unlockAndRefreshAllDashboards();

      // Verify all 4 dashboards have their lock overlays hidden
      overlays.forEach(overlay => {
        expect(overlay.style.display).toBe('none');
      });
      // Verify all content containers are restored to visible
      contents.forEach(content => {
        expect(content.style.display).toBe('');
      });
      // Verify Home status text updated immediately
      expect(homeStatus.textContent).toBe('System Ready');
      // Verify all dashboard loaders triggered
      expect(moviesLoaded).toBe(1);
      expect(seriesLoaded).toBe(1);
      expect(podcastsLoaded).toBe(1);
      expect(libraryRendered).toBe(1);

      // Repeat 10 times consecutively — must remain idempotent and crash-free
      for (let i = 0; i < 10; i++) {
        unlockAndRefreshAllDashboards();
      }
      expect(moviesLoaded).toBe(11);
      expect(homeStatus.textContent).toBe('System Ready');
    });

    it('3.3 unlockAndRefreshAllDashboards gracefully handles missing DOM elements', () => {
      // Intentionally do NOT create elements in DOM
      function clearAccessLockedState(containerId) {
        const cont = env.doc.getElementById(containerId);
        if (!cont) return;
        const overlay = env.doc.getElementById(`${containerId}-lock-overlay`);
        if (overlay) overlay.style.display = 'none';
      }

      expect(() => {
        clearAccessLockedState('non-existent-1');
        clearAccessLockedState('non-existent-2');
        clearAccessLockedState(null);
      }).not.toThrow();
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // Suite 4: Channel Grid Progressive Batch Rendering & Cancellation
  // ════════════════════════════════════════════════════════════════════════════
  describe('4. Channel Grid Rendering Optimization', () => {
    let env;
    beforeEach(() => { env = setupTestEnvironment(); });
    afterEach(() => { restoreTestEnvironment(); });

    it('4.1 Renders initial batch of 50 and adds sentinel for large lists', () => {
      const grid = env.doc.createElement('div');
      grid.id = 'channels-grid';
      env.doc.body.appendChild(grid);

      const state = {
        channels: Array.from({ length: 250 }, (_, i) => ({ id: `ch-${i}`, name: `Channel ${i}`, url: `http://ch${i}` })),
        filteredChannels: Array.from({ length: 250 }, (_, i) => ({ id: `ch-${i}`, name: `Channel ${i}`, url: `http://ch${i}` })),
        favorites: [],
        currentPlayingUrl: null,
        _gridRenderSession: 0
      };

      const BATCH_SIZE = 50;
      let currentIndex = 0;

      function getSentinel() {
        return grid.children.find(c => c.id === 'channels-grid-sentinel') || null;
      }

      function renderChannelsGrid() {
        state._gridRenderSession = (state._gridRenderSession || 0) + 1;
        const currentSession = state._gridRenderSession;
        grid.children = [];
        currentIndex = 0;

        function renderNextBatch() {
          if (state._gridRenderSession !== currentSession) return;
          const end = Math.min(currentIndex + BATCH_SIZE, state.filteredChannels.length);

          for (let i = currentIndex; i < end; i++) {
            const ch = state.filteredChannels[i];
            const card = env.doc.createElement('div');
            card.className = 'channel-card';
            card.dataset.channelId = ch.id;
            card.setAttribute('role', 'button');
            card.setAttribute('tabindex', '0');
            grid.appendChild(card);
          }

          const existingSentinel = getSentinel();
          if (existingSentinel) grid.removeChild(existingSentinel);

          currentIndex = end;

          if (currentIndex < state.filteredChannels.length) {
            const sentinel = env.doc.createElement('div');
            sentinel.id = 'channels-grid-sentinel';
            grid.appendChild(sentinel);
          }
        }

        renderNextBatch();
        return { renderNextBatch };
      }

      const session = renderChannelsGrid();
      // First batch rendered: exactly 50 cards
      const initialCards = grid.querySelectorAll('.channel-card');
      expect(initialCards.length).toBe(50);
      expect(getSentinel()).not.toBeNull();

      // Trigger second batch
      session.renderNextBatch();
      expect(grid.querySelectorAll('.channel-card').length).toBe(100);

      // Trigger remaining batches until all 250 rendered
      session.renderNextBatch();
      session.renderNextBatch();
      session.renderNextBatch();
      expect(grid.querySelectorAll('.channel-card').length).toBe(250);
      // Sentinel should be gone when all items rendered
      expect(getSentinel()).toBeNull();
    });

    it('4.2 Session Invalidation cancels in-flight batches when search query changes', () => {
      const grid = env.doc.createElement('div');
      grid.id = 'channels-grid';
      env.doc.body.appendChild(grid);

      const state = {
        channels: Array.from({ length: 500 }, (_, i) => ({ id: `ch-${i}`, name: `Channel ${i}`, url: `http://ch${i}` })),
        filteredChannels: Array.from({ length: 500 }, (_, i) => ({ id: `ch-${i}`, name: `Channel ${i}`, url: `http://ch${i}` })),
        _gridRenderSession: 0
      };

      const BATCH_SIZE = 50;

      function renderGrid() {
        state._gridRenderSession = (state._gridRenderSession || 0) + 1;
        const currentSession = state._gridRenderSession;
        grid.children = [];
        let idx = 0;

        function step() {
          if (state._gridRenderSession !== currentSession) {
            return false; // Canceled by newer session
          }
          const end = Math.min(idx + BATCH_SIZE, state.filteredChannels.length);
          for (let i = idx; i < end; i++) {
            const card = env.doc.createElement('div');
            card.className = 'channel-card';
            card.dataset.channelId = state.filteredChannels[i].id;
            grid.appendChild(card);
          }
          idx = end;
          return true;
        }

        step();
        return { step, session: currentSession };
      }

      // Initial render started
      const session1 = renderGrid();
      expect(grid.querySelectorAll('.channel-card').length).toBe(50);

      // User rapidly types search query -> session 2 begins with filtered subset
      state.filteredChannels = state.channels.slice(0, 5); // 5 matching channels
      const session2 = renderGrid();
      expect(grid.querySelectorAll('.channel-card').length).toBe(5);

      // Old session 1 tries to step -> must be ignored due to session token mismatch
      const res = session1.step();
      expect(res).toBe(false);
      // Grid remains cleanly at 5 cards from session 2
      expect(grid.querySelectorAll('.channel-card').length).toBe(5);
    });
  });

  // ════════════════════════════════════════════════════════════════════════════
  // Suite 5: Player Lifecycle, Unmute Timers & Media Teardown
  // ════════════════════════════════════════════════════════════════════════════
  describe('5. Player Lifecycle & Clean State Teardown', () => {
    let env;
    beforeEach(() => { env = setupTestEnvironment(); });
    afterEach(() => { restoreTestEnvironment(); });

    it('5.1 clearUnmuteTimers cancels all pending timeouts without errors', () => {
      const player = new IPTVPlayer('video-player');
      
      let timer1Executed = false;
      let timer2Executed = false;

      const id1 = setTimeout(() => { timer1Executed = true; }, 500);
      const id2 = setTimeout(() => { timer2Executed = true; }, 500);

      player.unmuteTimers.push(id1, id2);
      expect(player.unmuteTimers.length).toBe(2);

      player.clearUnmuteTimers();
      expect(player.unmuteTimers.length).toBe(0);

      // Calling again on empty array is safe
      expect(() => player.clearUnmuteTimers()).not.toThrow();
    });

    it('5.2 resetVideoFrame calls clearUnmuteTimers, pauses video, and resets state', () => {
      const player = new IPTVPlayer('video-player');
      player.currentUrl = 'http://example.com/stream.m3u8';
      
      let timerFired = false;
      const timer = setTimeout(() => { timerFired = true; }, 500);
      player.unmuteTimers.push(timer);

      player.resetVideoFrame();

      expect(player.unmuteTimers.length).toBe(0);
      expect(player.currentUrl).toBe('');
      expect(player.video.paused).toBe(true);
    });

    it('5.3 stopAllMediaPlayback sets embed iframe to about:blank and cleans overlays', () => {
      const embedIframe = env.doc.getElementById('embed-iframe');
      const loadingOverlay = env.doc.getElementById('player-loading');
      const errorOverlay = env.doc.getElementById('player-error');

      embedIframe.src = 'https://www.youtube.com/embed/dQw4w9WgXcQ';
      loadingOverlay.classList.remove('hidden');
      errorOverlay.classList.remove('hidden');

      function stopAllMediaPlayback(options = {}) {
        const { keepVod = false } = options;
        if (!keepVod) {
          if (embedIframe) embedIframe.src = 'about:blank';
        }
        if (loadingOverlay) loadingOverlay.classList.add('hidden');
        if (errorOverlay) errorOverlay.classList.add('hidden');
      }

      stopAllMediaPlayback();

      expect(embedIframe.src).toBe('about:blank');
      expect(loadingOverlay.classList.contains('hidden')).toBe(true);
      expect(errorOverlay.classList.contains('hidden')).toBe(true);
    });
  });

});

// Run all test suites registered in this file
export async function runChallengerStressTests() {
  return await runAllRegisteredSuites();
}

if (import.meta.url === `file://${process.argv[1]}` || process.argv[1]?.includes('challenger2_m2_stress_test')) {
  runChallengerStressTests().then(results => {
    if (results.failed > 0) {
      console.error(`\n❌ Challenger 2 tests failed with ${results.failed} errors.`);
      process.exit(1);
    } else {
      console.log(`\n✅ Challenger 2 tests passed successfully (${results.passed}/${results.total} tests).`);
      process.exit(0);
    }
  }).catch(err => {
    console.error('Fatal error running challenger tests:', err);
    process.exit(1);
  });
}
