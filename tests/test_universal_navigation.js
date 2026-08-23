/**
 * Automated Universal Navigation Tests: BlueStacks, Android Phone, and Fire TV Stick
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
  MockElement
} from './harness.js';

import { spatialNav } from '../src/spatialNav.js';

describe('Universal Navigation & Spatial Engine Suite', () => {
  beforeEach(() => {
    setupTestEnvironment();
    spatialNav.init();
  });

  afterEach(() => {
    restoreTestEnvironment();
  });

  it('NAV-01: spatialNav engine initializes with active candidate scanning and native bridge hooks', () => {
    assert(spatialNav !== null, 'spatialNav should be instantiated');
    assert(typeof window.__tvdinner_handle_back === 'function', '__tvdinner_handle_back should be exposed');
    assert(typeof window.__tvdinner_media_play_pause === 'function', '__tvdinner_media_play_pause should be exposed');
    assert(typeof window.__tvdinner_media_ff === 'function', '__tvdinner_media_ff should be exposed');
    assert(typeof window.__tvdinner_media_rw === 'function', '__tvdinner_media_rw should be exposed');
    assert(typeof window.__tvdinner_channel_up === 'function', '__tvdinner_channel_up should be exposed');
    assert(typeof window.__tvdinner_channel_down === 'function', '__tvdinner_channel_down should be exposed');
    assert(typeof window.__tvdinner_toggle_menu === 'function', '__tvdinner_toggle_menu should be exposed');
  });

  it('NAV-02: 2D directional scoring finds closest horizontal and vertical candidates', () => {
    // Construct 2x2 grid of cards
    const card1 = document.createElement('button');
    card1.className = 'home-select-card';
    card1.getBoundingClientRect = () => ({ left: 0, top: 0, right: 100, bottom: 100, width: 100, height: 100 });

    const card2 = document.createElement('button');
    card2.className = 'home-select-card';
    card2.getBoundingClientRect = () => ({ left: 150, top: 0, right: 250, bottom: 100, width: 100, height: 100 });

    const card3 = document.createElement('button');
    card3.className = 'home-select-card';
    card3.getBoundingClientRect = () => ({ left: 0, top: 150, right: 100, bottom: 250, width: 100, height: 100 });

    const card4 = document.createElement('button');
    card4.className = 'home-select-card';
    card4.getBoundingClientRect = () => ({ left: 150, top: 150, right: 250, bottom: 250, width: 100, height: 100 });

    document.body.appendChild(card1);
    document.body.appendChild(card2);
    document.body.appendChild(card3);
    document.body.appendChild(card4);

    spatialNav.focusElement(card1);
    assert(document.activeElement === card1, 'card1 should be initially focused');

    // Move RIGHT -> card2
    spatialNav.moveFocus('right');
    assert(document.activeElement === card2, 'moveFocus right should focus card2');

    // Move DOWN -> card4
    spatialNav.moveFocus('down');
    assert(document.activeElement === card4, 'moveFocus down should focus card4');

    // Move LEFT -> card3
    spatialNav.moveFocus('left');
    assert(document.activeElement === card3, 'moveFocus left should focus card3');

    // Move UP -> card1
    spatialNav.moveFocus('up');
    assert(document.activeElement === card1, 'moveFocus up should focus card1');
  });

  it('NAV-03: Modal focus trapping restricts spatial candidates strictly to open modal dialog', () => {
    // Background button outside modal
    const bgBtn = document.createElement('button');
    bgBtn.id = 'bg-btn';
    bgBtn.getBoundingClientRect = () => ({ left: 10, top: 10, right: 60, bottom: 40, width: 50, height: 30 });
    document.body.appendChild(bgBtn);

    // Modal overlay container
    const modal = document.createElement('div');
    modal.id = 'details-overlay';
    modal.offsetHeight = 500;
    modal.getBoundingClientRect = () => ({ left: 0, top: 0, right: 800, bottom: 600, width: 800, height: 600 });

    const modalWatchBtn = document.createElement('button');
    modalWatchBtn.id = 'details-watch-btn';
    modalWatchBtn.getBoundingClientRect = () => ({ left: 100, top: 100, right: 250, bottom: 150, width: 150, height: 50 });

    const modalCloseBtn = document.createElement('button');
    modalCloseBtn.id = 'details-close-btn';
    modalCloseBtn.getBoundingClientRect = () => ({ left: 100, top: 200, right: 250, bottom: 250, width: 150, height: 50 });

    modal.appendChild(modalWatchBtn);
    modal.appendChild(modalCloseBtn);
    document.body.appendChild(modal);

    // Get active scope
    const scope = spatialNav.getActiveScope();
    assert(scope === modal, 'Active scope should be details-overlay modal');

    const candidates = spatialNav.getFocusableElements(scope);
    assert(candidates.includes(modalWatchBtn), 'Candidates should include modal watch button');
    assert(candidates.includes(modalCloseBtn), 'Candidates should include modal close button');
    assert(!candidates.includes(bgBtn), 'Candidates must NOT include background button behind modal');
  });

  it('NAV-04: handleGlobalBackAction cascades from modals -> subtabs -> root exit', () => {
    // Setup modal
    const modal = document.createElement('div');
    modal.id = 'details-overlay';
    modal.offsetHeight = 500;
    const closeBtn = document.createElement('button');
    closeBtn.id = 'details-close-btn';
    let closeClicked = false;
    closeBtn.addEventListener('click', () => {
      closeClicked = true;
      modal.classList.add('hidden');
    });
    modal.appendChild(closeBtn);
    document.body.appendChild(modal);

    // 1. When modal is open, back closes modal
    const handledModal = spatialNav.handleGlobalBackAction();
    assert(handledModal === true, 'Back should handle open modal');
    assert(closeClicked === true, 'Close button should have been triggered');

    // 2. When on sub-tab (e.g. movies tab), back returns to home
    const homeTab = document.createElement('div');
    homeTab.id = 'tab-home';
    homeTab.classList.add('hidden'); // Home not active yet

    const moviesTab = document.createElement('div');
    moviesTab.id = 'tab-movies';

    const homeNavBtn = document.createElement('button');
    homeNavBtn.className = 'nav-link';
    homeNavBtn.setAttribute('data-tab', 'home');
    let homeNavClicked = false;
    homeNavBtn.addEventListener('click', () => {
      homeNavClicked = true;
      homeTab.classList.remove('hidden');
      moviesTab.classList.add('hidden');
    });

    document.body.appendChild(homeTab);
    document.body.appendChild(moviesTab);
    document.body.appendChild(homeNavBtn);

    const handledSubtab = spatialNav.handleGlobalBackAction();
    assert(handledSubtab === true, 'Back should navigate from subtab back to home');
    assert(homeNavClicked === true, 'Home nav button should have been clicked');

    // 3. When at root home tab with nothing open, back returns false to allow Android OS app exit
    const handledRoot = spatialNav.handleGlobalBackAction();
    assert(handledRoot === false, 'Back at root Home should return false to delegate OS exit');
  });

  it('NAV-05: Remote media hooks trigger registered playback and navigation callbacks', () => {
    let playPauseCalled = false;
    let ffCalled = false;
    let rwCalled = false;
    let chUpCalled = false;
    let chDownCalled = false;
    let menuCalled = false;

    spatialNav.setMediaHook('onPlayPause', () => { playPauseCalled = true; });
    spatialNav.setMediaHook('onFastForward', () => { ffCalled = true; });
    spatialNav.setMediaHook('onRewind', () => { rwCalled = true; });
    spatialNav.setMediaHook('onChannelUp', () => { chUpCalled = true; });
    spatialNav.setMediaHook('onChannelDown', () => { chDownCalled = true; });
    spatialNav.setMediaHook('onToggleMenu', () => { menuCalled = true; });

    window.__tvdinner_media_play_pause();
    assert(playPauseCalled === true, 'play_pause hook should execute');

    window.__tvdinner_media_ff();
    assert(ffCalled === true, 'ff hook should execute');

    window.__tvdinner_media_rw();
    assert(rwCalled === true, 'rw hook should execute');

    window.__tvdinner_channel_up();
    assert(chUpCalled === true, 'channel_up hook should execute');

    window.__tvdinner_channel_down();
    assert(chDownCalled === true, 'channel_down hook should execute');

    window.__tvdinner_toggle_menu();
    assert(menuCalled === true, 'toggle_menu hook should execute');
  });

  it('NAV-06: Input mode switching toggles spatial focus ring visibility', () => {
    const btn = document.createElement('button');
    btn.className = 'channel-card';
    btn.getBoundingClientRect = () => ({ left: 0, top: 0, right: 100, bottom: 50, width: 100, height: 50 });
    document.body.appendChild(btn);

    spatialNav.focusElement(btn);
    assert(btn.classList.contains('spatial-focused'), 'Button should have spatial-focused class');

    // Mouse movement clears spatial focus ring
    window.dispatchEvent(new Event('mousemove'));
    assert(!btn.classList.contains('spatial-focused'), 'Mouse move should remove spatial-focused class');
  });

  it('NAV-07: Podcast show card provides focusable Play Latest button and interactive actions', () => {
    const card = document.createElement('div');
    card.className = 'podcast-show-card';
    card.setAttribute('tabindex', '0');
    card.getBoundingClientRect = () => ({ left: 100, top: 100, right: 280, bottom: 350, width: 180, height: 250 });

    const playBtn = document.createElement('button');
    playBtn.className = 'podcast-show-play-btn';
    playBtn.getBoundingClientRect = () => ({ left: 110, top: 300, right: 190, bottom: 335, width: 80, height: 35 });

    const subBtn = document.createElement('button');
    subBtn.className = 'podcast-show-sub-btn';
    subBtn.getBoundingClientRect = () => ({ left: 195, top: 300, right: 270, bottom: 335, width: 75, height: 35 });

    card.appendChild(playBtn);
    card.appendChild(subBtn);
    document.body.appendChild(card);

    const scope = spatialNav.getActiveScope();
    const candidates = spatialNav.getFocusableElements(scope);

    assert(candidates.includes(card), 'Candidates should include podcast-show-card');
    assert(candidates.includes(playBtn), 'Candidates should include podcast-show-play-btn');
    assert(candidates.includes(subBtn), 'Candidates should include podcast-show-sub-btn');
  });

  it('NAV-08: Clean start does not auto-populate default phone credentials', () => {
    // Verify fresh localStorage has no pre-seeded phone activation
    const phone = localStorage.getItem('activated_phone');
    assert(!phone, 'Fresh install should not have activated_phone pre-seeded');
  });
});
