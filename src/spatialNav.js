/**
 * Universal 2D Geometric Spatial Navigation Engine
 * Designed for Fire TV / Android Sticks (D-Pad Remote), BlueStacks (Keyboard/Mouse), and Mobile.
 */

class SpatialNavigationEngine {
  constructor() {
    this.enabled = true;
    this.currentFocusedElement = null;
    this.lastFocusedPerScope = new Map();
    this.inputMode = 'spatial'; // 'spatial' | 'mouse' | 'touch'
    this.onBackCallbacks = [];
    this.mediaHooks = {
      onPlayPause: null,
      onFastForward: null,
      onRewind: null,
      onChannelUp: null,
      onChannelDown: null,
      onToggleMenu: null
    };

    this.init();
  }

  init() {
    if (typeof window === 'undefined' || typeof document === 'undefined') return;

    // Attach global keydown listener for remote / keyboard navigation
    window.addEventListener('keydown', (e) => this.handleKeyDown(e), true);

    // Mouse & Touch coexistence: track input mode
    window.addEventListener('mousemove', () => {
      if (this.inputMode !== 'mouse') {
        this.inputMode = 'mouse';
        this.clearSpatialFocusRing();
      }
    }, { passive: true });

    window.addEventListener('touchstart', () => {
      if (this.inputMode !== 'touch') {
        this.inputMode = 'touch';
        this.clearSpatialFocusRing();
      }
    }, { passive: true });

    // Focus tracking
    document.addEventListener('focusin', (e) => {
      const target = e.target;
      if (target && target.nodeType === 1) {
        this.currentFocusedElement = target;
        if (this.inputMode === 'spatial') {
          target.classList.add('spatial-focused');
        }
      }
    });

    document.addEventListener('focusout', (e) => {
      if (e.target && e.target.nodeType === 1) {
        e.target.classList.remove('spatial-focused');
      }
    });

    // Expose global hooks for native Android Activity bridge
    this.bindNativeBridge();
  }

  bindNativeBridge() {
    if (typeof window === 'undefined') return;

    window.__tvdinner_handle_back = () => this.handleGlobalBackAction();
    window.__tvdinner_media_play_pause = () => this.triggerMediaAction('onPlayPause');
    window.__tvdinner_media_ff = () => this.triggerMediaAction('onFastForward');
    window.__tvdinner_media_rw = () => this.triggerMediaAction('onRewind');
    window.__tvdinner_channel_up = () => this.triggerMediaAction('onChannelUp');
    window.__tvdinner_channel_down = () => this.triggerMediaAction('onChannelDown');
    window.__tvdinner_toggle_menu = () => this.triggerMediaAction('onToggleMenu');
    window.__tvdinner_spatial_nav = this;
  }

  setMediaHook(action, fn) {
    if (action in this.mediaHooks) {
      this.mediaHooks[action] = fn;
    }
  }

  triggerMediaAction(action) {
    if (typeof this.mediaHooks[action] === 'function') {
      try {
        this.mediaHooks[action]();
        return true;
      } catch (err) {
        console.error(`[SpatialNav] Error executing media hook ${action}:`, err);
      }
    }
    return false;
  }

  clearSpatialFocusRing() {
    const focused = document.querySelectorAll('.spatial-focused');
    focused.forEach(el => el.classList.remove('spatial-focused'));
  }

  /**
   * Determine the current active focus scope (e.g. open modal dialog or active tab container)
   */
  getActiveScope() {
    // 1. Check for open modals / overlays in priority order
    const openModals = [
      document.getElementById('details-overlay'),
      document.getElementById('podcast-channel-modal'),
      document.getElementById('podcast-channel-overlay'),
      document.getElementById('mobile-drawer-overlay'),
      document.getElementById('admin-cred-modal')
    ];

    for (const modal of openModals) {
      if (modal && !modal.classList.contains('hidden') && modal.style.display !== 'none' && (modal.offsetHeight === undefined || modal.offsetHeight > 0)) {
        return modal;
      }
    }

    // 2. Fallback to the whole active document
    return document.body;
  }

  /**
   * Query all visible, focusable interactive elements within the active scope
   */
  getFocusableElements(scope = null) {
    const root = scope || this.getActiveScope();
    if (!root) return [];

    const selectors = [
      'button',
      'a[href]',
      'input',
      'select',
      '[tabindex="0"]',
      '.nav-link',
      '.mobile-drawer-link',
      '.home-select-card',
      '.channel-card',
      '.detail-item-card',
      '.vod-card',
      '.episode-card',
      '.episode-item',
      '.podcast-card',
      '.podcast-show-card',
      '.podcast-channel-card',
      '.podcast-ep-item',
      '.podcast-show-play-btn',
      '.podcast-show-sub-btn',
      '.ep-btn-play',
      '.podcast-nav-btn',
      '.podcast-cat-pill',
      '.podcast-browse-cat-btn',
      '.category-btn',
      '.category-pill',
      '.genre-pill',
      '.genre-chip',
      '.ctrl-btn',
      '.settings-tab-btn',
      '.settings-btn'
    ];

    const rawSet = new Set();
    for (const sel of selectors) {
      try {
        const list = root.querySelectorAll(sel);
        for (let i = 0; i < list.length; i++) {
          rawSet.add(list[i]);
        }
      } catch (_) {}
    }

    // Also include sidebar nav links if scope is root body
    if (root === document.body) {
      try {
        const sidebarLinks = Array.from(document.querySelectorAll('.app-sidebar-nav .nav-link, .mobile-header button'));
        sidebarLinks.forEach(item => rawSet.add(item));
      } catch (_) {}
    }

    const rawList = Array.from(rawSet);

    // Filter by actual visibility, disabled state, and bounding rect
    return rawList.filter(el => {
      if (el.disabled) return false;
      if (el.getAttribute && el.getAttribute('tabindex') === '-1') return false;

      const rect = (typeof el.getBoundingClientRect === 'function') ? el.getBoundingClientRect() : { width: 100, height: 100, bottom: 100, right: 100, left: 0, top: 0 };
      if (rect.width <= 0 || rect.height <= 0) return false;
      if (rect.bottom < 0 || rect.right < 0) return false;

      // Check CSS visibility
      const style = (typeof window !== 'undefined' && typeof window.getComputedStyle === 'function')
        ? window.getComputedStyle(el)
        : (el.style || {});
      if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') {
        return false;
      }

      // Ensure not inside a hidden parent container
      if (typeof el.closest === 'function') {
        const hiddenParent = el.closest('.hidden');
        if (hiddenParent && !root.contains(hiddenParent)) return false;
      }

      return true;
    });
  }

  /**
   * Find currently focused element or select default
   */
  getCurrentFocus(scope = null) {
    const active = document.activeElement;
    const currentScope = scope || this.getActiveScope();

    if (active && active !== document.body && currentScope.contains(active)) {
      const rect = active.getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) {
        return active;
      }
    }

    // Try last focused element in this scope
    const lastFocused = this.lastFocusedPerScope.get(currentScope);
    if (lastFocused && document.body.contains(lastFocused) && currentScope.contains(lastFocused)) {
      const rect = lastFocused.getBoundingClientRect();
      if (rect.width > 0 && rect.height > 0) {
        return lastFocused;
      }
    }

    // Default to first focusable candidate
    const candidates = this.getFocusableElements(currentScope);
    return candidates.length > 0 ? candidates[0] : null;
  }

  /**
   * Core 2D geometric navigation algorithm
   */
  moveFocus(direction) {
    this.inputMode = 'spatial';
    const scope = this.getActiveScope();
    const current = this.getCurrentFocus(scope);
    const candidates = this.getFocusableElements(scope);

    if (candidates.length === 0) return false;

    if (!current || !candidates.includes(current)) {
      // Focus first available candidate
      this.focusElement(candidates[0]);
      return true;
    }

    const currentRect = current.getBoundingClientRect();
    const currentCenter = {
      x: currentRect.left + currentRect.width / 2,
      y: currentRect.top + currentRect.height / 2
    };

    let bestCandidate = null;
    let minScore = Infinity;

    for (const candidate of candidates) {
      if (candidate === current) continue;

      const candRect = candidate.getBoundingClientRect();
      const candCenter = {
        x: candRect.left + candRect.width / 2,
        y: candRect.top + candRect.height / 2
      };

      const dx = candCenter.x - currentCenter.x;
      const dy = candCenter.y - currentCenter.y;

      let isDirectionalMatch = false;
      let primaryDist = 0;
      let secondaryDist = 0;

      switch (direction) {
        case 'up':
          if (candRect.bottom <= currentRect.bottom + 10 && dy < -5) {
            isDirectionalMatch = true;
            primaryDist = -dy;
            secondaryDist = Math.abs(dx);
          }
          break;
        case 'down':
          if (candRect.top >= currentRect.top - 10 && dy > 5) {
            isDirectionalMatch = true;
            primaryDist = dy;
            secondaryDist = Math.abs(dx);
          }
          break;
        case 'left':
          if (candRect.right <= currentRect.right + 10 && dx < -5) {
            isDirectionalMatch = true;
            primaryDist = -dx;
            secondaryDist = Math.abs(dy);
          }
          break;
        case 'right':
          if (candRect.left >= currentRect.left - 10 && dx > 5) {
            isDirectionalMatch = true;
            primaryDist = dx;
            secondaryDist = Math.abs(dy);
          }
          break;
      }

      if (isDirectionalMatch) {
        // Geometric score calculation: primary axis weight + orthogonal penalty
        // Overlap bonus if elements share horizontal or vertical projection
        let overlapBonus = 0;
        if (direction === 'up' || direction === 'down') {
          const overlap = Math.max(0, Math.min(currentRect.right, candRect.right) - Math.max(currentRect.left, candRect.left));
          if (overlap > 0) overlapBonus = -30;
        } else {
          const overlap = Math.max(0, Math.min(currentRect.bottom, candRect.bottom) - Math.max(currentRect.top, candRect.top));
          if (overlap > 0) overlapBonus = -30;
        }

        const score = primaryDist + (secondaryDist * 2.2) + overlapBonus;
        if (score < minScore) {
          minScore = score;
          bestCandidate = candidate;
        }
      }
    }

    // Sidebar Fallbacks:
    // If moving LEFT from main content and no leftward candidate exists, jump to sidebar nav
    if (!bestCandidate && direction === 'left' && scope === document.body) {
      const sidebarLinks = Array.from(document.querySelectorAll('.app-sidebar-nav .nav-link'));
      const visibleSidebar = sidebarLinks.filter(l => l.getBoundingClientRect().width > 0);
      if (visibleSidebar.length > 0 && !visibleSidebar.includes(current)) {
        // Pick the sidebar link closest vertically to current item
        bestCandidate = visibleSidebar.reduce((closest, link) => {
          const linkRect = link.getBoundingClientRect();
          const dist = Math.abs((linkRect.top + linkRect.height / 2) - currentCenter.y);
          if (!closest || dist < closest.dist) return { el: link, dist };
          return closest;
        }, null)?.el;
      }
    }

    // If moving RIGHT from sidebar nav, jump into the active tab content
    if (!bestCandidate && direction === 'right' && scope === document.body && current.closest('.app-sidebar-nav')) {
      const activeTabEl = document.querySelector('.tab-screen:not(.hidden)');
      if (activeTabEl) {
        const tabCandidates = this.getFocusableElements(activeTabEl);
        if (tabCandidates.length > 0) {
          bestCandidate = tabCandidates[0];
        }
      }
    }

    if (bestCandidate) {
      this.focusElement(bestCandidate);
      return true;
    }

    return false;
  }

  focusElement(el) {
    if (!el || typeof el.focus !== 'function') return;

    this.inputMode = 'spatial';
    this.clearSpatialFocusRing();

    el.focus();
    el.classList.add('spatial-focused');
    this.currentFocusedElement = el;

    const scope = this.getActiveScope();
    this.lastFocusedPerScope.set(scope, el);

    // Smoothly ensure element is visible in its scrollable parent and viewport
    if (typeof el.scrollIntoView === 'function') {
      try {
        el.scrollIntoView({
          behavior: 'smooth',
          block: 'nearest',
          inline: 'nearest'
        });
      } catch (e) {
        try { el.scrollIntoView(false); } catch (_) {}
      }
    }
  }

  /**
   * Reset focus to the primary interactive element in the current view/modal
   */
  resetFocus(scope = null) {
    const targetScope = scope || this.getActiveScope();
    const candidates = this.getFocusableElements(targetScope);
    if (candidates.length > 0) {
      this.focusElement(candidates[0]);
    }
  }

  /**
   * Handle global Back key action hierarchy
   */
  handleGlobalBackAction() {
    // 1. Custom registered back callbacks
    for (let i = this.onBackCallbacks.length - 1; i >= 0; i--) {
      const cb = this.onBackCallbacks[i];
      try {
        if (cb() === true) return true;
      } catch (err) {
        console.error('[SpatialNav] Back callback error:', err);
      }
    }

    // 2. Check open Fullscreen
    if (document.fullscreenElement || document.webkitFullscreenElement) {
      if (document.exitFullscreen) {
        document.exitFullscreen();
      } else if (document.webkitExitFullscreen) {
        document.webkitExitFullscreen();
      }
      return true;
    }

    // 3. Check VOD / Episode Details Modal
    const detailsOverlay = document.getElementById('details-overlay');
    if (detailsOverlay && !detailsOverlay.classList.contains('hidden') && detailsOverlay.style.display !== 'none') {
      const closeBtn = document.getElementById('details-close-btn') || detailsOverlay.querySelector('.modal-close-btn');
      if (closeBtn) closeBtn.click();
      else detailsOverlay.classList.add('hidden');
      return true;
    }

    // 4. Check Podcast Modal
    const podcastModal = document.getElementById('podcast-channel-modal') || document.getElementById('podcast-channel-overlay');
    if (podcastModal && !podcastModal.classList.contains('hidden') && podcastModal.style.display !== 'none') {
      const closeBtn = document.getElementById('podcast-channel-close-btn') || podcastModal.querySelector('.modal-close-btn');
      if (closeBtn) closeBtn.click();
      else {
        podcastModal.classList.add('hidden');
        podcastModal.style.display = 'none';
        document.body.style.overflow = '';
      }
      return true;
    }

    // 5. Check Mobile Drawer Menu Overlay
    const drawerOverlay = document.getElementById('mobile-drawer-overlay');
    if (drawerOverlay && !drawerOverlay.classList.contains('hidden') && drawerOverlay.style.display !== 'none') {
      const closeBtn = document.getElementById('mobile-drawer-close');
      if (closeBtn) closeBtn.click();
      else drawerOverlay.classList.add('hidden');
      return true;
    }

    // 6. Check if Search Input is active / focused
    const searchInputs = document.querySelectorAll('.search-field-input, .live-search-input, .podcast-search-input');
    for (const input of searchInputs) {
      if (document.activeElement === input && input.value.trim().length > 0) {
        input.value = '';
        input.dispatchEvent(new Event('input', { bubbles: true }));
        input.blur();
        return true;
      }
    }

    // 7. If on a sub-tab other than 'home', navigate back to 'home'
    const homeTab = document.getElementById('tab-home');
    const isHomeActive = homeTab && !homeTab.classList.contains('hidden');
    if (!isHomeActive) {
      const homeNavBtn = document.querySelector('.nav-link[data-tab="home"], .mobile-drawer-link[data-tab="home"]');
      if (homeNavBtn) {
        homeNavBtn.click();
        return true;
      }
    }

    // 8. At root Home tab with nothing open: return false so native Android finishes activity
    return false;
  }

  /**
   * Keyboard and Remote Event Dispatcher
   */
  handleKeyDown(e) {
    const key = e.key;
    const target = e.target;
    const isInput = target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT');

    // Directional navigation keys
    switch (key) {
      case 'ArrowUp':
      case 'Up':
      case 'UIKeyInputUpArrow':
        if (isInput && target.tagName !== 'SELECT') {
          // Allow moving out of input on ArrowUp
          e.preventDefault();
          this.moveFocus('up');
        } else if (!isInput) {
          e.preventDefault();
          this.moveFocus('up');
        }
        break;

      case 'ArrowDown':
      case 'Down':
      case 'UIKeyInputDownArrow':
        if (isInput && target.tagName !== 'SELECT') {
          // Allow moving out of input on ArrowDown
          e.preventDefault();
          this.moveFocus('down');
        } else if (!isInput) {
          e.preventDefault();
          this.moveFocus('down');
        }
        break;

      case 'ArrowLeft':
      case 'Left':
      case 'UIKeyInputLeftArrow':
        if (isInput) {
          // If cursor is at the start of text field, allow navigation left
          if (target.selectionStart === 0 && target.selectionEnd === 0) {
            e.preventDefault();
            this.moveFocus('left');
          }
        } else {
          e.preventDefault();
          this.moveFocus('left');
        }
        break;

      case 'ArrowRight':
      case 'Right':
      case 'UIKeyInputRightArrow':
        if (isInput) {
          // If cursor is at end of text field, allow navigation right
          if (target.selectionStart === target.value.length && target.selectionEnd === target.value.length) {
            e.preventDefault();
            this.moveFocus('right');
          }
        } else {
          e.preventDefault();
          this.moveFocus('right');
        }
        break;

      case 'Enter':
      case 'Select':
      case 'Ok':
        if (!isInput) {
          e.preventDefault();
          const current = this.getCurrentFocus();
          if (current) {
            current.click();
          }
        }
        break;

      case 'Escape':
      case 'Back':
      case 'BrowserBack':
      case 'GoBack':
        e.preventDefault();
        this.handleGlobalBackAction();
        break;

      case 'MediaPlayPause':
      case 'PlayPause':
        e.preventDefault();
        this.triggerMediaAction('onPlayPause');
        break;

      case 'MediaFastForward':
      case 'FastForward':
        e.preventDefault();
        this.triggerMediaAction('onFastForward');
        break;

      case 'MediaRewind':
      case 'Rewind':
        e.preventDefault();
        this.triggerMediaAction('onRewind');
        break;

      case 'ChannelUp':
      case 'PageUp':
        if (!isInput) {
          e.preventDefault();
          this.triggerMediaAction('onChannelUp');
        }
        break;

      case 'ChannelDown':
      case 'PageDown':
        if (!isInput) {
          e.preventDefault();
          this.triggerMediaAction('onChannelDown');
        }
        break;

      case 'Menu':
      case 'ContextMenu':
        if (!isInput) {
          e.preventDefault();
          this.triggerMediaAction('onToggleMenu');
        }
        break;
    }
  }

  registerBackCallback(cb) {
    this.onBackCallbacks.push(cb);
    return () => {
      this.onBackCallbacks = this.onBackCallbacks.filter(fn => fn !== cb);
    };
  }
}

// Global Singleton Instance
export const spatialNav = new SpatialNavigationEngine();
export default spatialNav;
