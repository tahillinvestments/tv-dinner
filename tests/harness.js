/**
 * TV Dinner Test Harness & Mock Environment
 * ──────────────────────────────────────────
 * Zero heavy external dependencies. Pure Node.js testing infrastructure.
 * Provides assertion library, test suite runner, DOM mocking, and API mocks.
 */

// ─── Assertion Library ────────────────────────────────────────────────────────

export function assert(condition, message = 'Assertion failed') {
  if (!condition) {
    const err = new Error(message);
    err.name = 'AssertionError';
    throw err;
  }
}

function deepEqual(a, b) {
  if (a === b) return true;
  if (a == null || b == null) return false;
  if (typeof a !== 'object' || typeof b !== 'object') return false;

  if (Array.isArray(a)) {
    if (!Array.isArray(b) || a.length !== b.length) return false;
    for (let i = 0; i < a.length; i++) {
      if (!deepEqual(a[i], b[i])) return false;
    }
    return true;
  }

  if (a instanceof Map && b instanceof Map) {
    if (a.size !== b.size) return false;
    for (const [k, v] of a) {
      if (!b.has(k) || !deepEqual(v, b.get(k))) return false;
    }
    return true;
  }

  if (a instanceof Set && b instanceof Set) {
    if (a.size !== b.size) return false;
    for (const v of a) {
      if (!b.has(v)) return false;
    }
    return true;
  }

  const keysA = Object.keys(a);
  const keysB = Object.keys(b);
  if (keysA.length !== keysB.length) return false;

  for (const key of keysA) {
    if (!Object.prototype.hasOwnProperty.call(b, key)) return false;
    if (!deepEqual(a[key], b[key])) return false;
  }
  return true;
}

function safeStringify(val) {
  if (val === undefined) return 'undefined';
  if (val === null) return 'null';
  if (typeof val === 'function') return `[Function ${val.name || 'anonymous'}]`;
  if (typeof val === 'symbol') return val.toString();
  if (typeof val !== 'object') return String(val);
  try {
    return JSON.stringify(val);
  } catch (_) {
    if (val.tagName) return `<${val.tagName.toLowerCase()}${val.id ? ` id="${val.id}"` : ''}${val.className ? ` class="${val.className}"` : ''}>`;
    return String(val);
  }
}

export function expect(actual) {
  const matchers = (isNot = false) => ({
    toBe(expected) {
      const pass = Object.is(actual, expected) || actual === expected;
      if (isNot ? pass : !pass) {
        throw new Error(`Expected ${safeStringify(actual)} ${isNot ? 'NOT to be' : 'to be'} ${safeStringify(expected)}`);
      }
    },
    toEqual(expected) {
      const pass = deepEqual(actual, expected);
      if (isNot ? pass : !pass) {
        throw new Error(`Expected ${safeStringify(actual)} ${isNot ? 'NOT to equal' : 'to equal'} ${safeStringify(expected)}`);
      }
    },
    toBeTruthy() {
      const pass = Boolean(actual);
      if (isNot ? pass : !pass) {
        throw new Error(`Expected ${safeStringify(actual)} ${isNot ? 'NOT to be truthy' : 'to be truthy'}`);
      }
    },
    toBeFalsy() {
      const pass = !actual;
      if (isNot ? pass : !pass) {
        throw new Error(`Expected ${safeStringify(actual)} ${isNot ? 'NOT to be falsy' : 'to be falsy'}`);
      }
    },
    toBeNull() {
      const pass = actual === null;
      if (isNot ? pass : !pass) {
        throw new Error(`Expected ${safeStringify(actual)} ${isNot ? 'NOT to be null' : 'to be null'}`);
      }
    },
    toBeDefined() {
      const pass = actual !== undefined;
      if (isNot ? pass : !pass) {
        throw new Error(`Expected value ${isNot ? 'to be undefined' : 'to be defined'}`);
      }
    },
    toBeUndefined() {
      const pass = actual === undefined;
      if (isNot ? pass : !pass) {
        throw new Error(`Expected ${safeStringify(actual)} ${isNot ? 'NOT to be undefined' : 'to be undefined'}`);
      }
    },
    toBeGreaterThan(expected) {
      const pass = actual > expected;
      if (isNot ? pass : !pass) {
        throw new Error(`Expected ${actual} ${isNot ? 'NOT to be >' : 'to be >'} ${expected}`);
      }
    },
    toBeGreaterThanOrEqual(expected) {
      const pass = actual >= expected;
      if (isNot ? pass : !pass) {
        throw new Error(`Expected ${actual} ${isNot ? 'NOT to be >=' : 'to be >='} ${expected}`);
      }
    },
    toBeLessThan(expected) {
      const pass = actual < expected;
      if (isNot ? pass : !pass) {
        throw new Error(`Expected ${actual} ${isNot ? 'NOT to be <' : 'to be <'} ${expected}`);
      }
    },
    toBeLessThanOrEqual(expected) {
      const pass = actual <= expected;
      if (isNot ? pass : !pass) {
        throw new Error(`Expected ${actual} ${isNot ? 'NOT to be <=' : 'to be <='} ${expected}`);
      }
    },
    toContain(item) {
      let pass = false;
      if (typeof actual === 'string') {
        pass = actual.includes(item);
      } else if (Array.isArray(actual)) {
        pass = actual.includes(item) || actual.some(x => deepEqual(x, item));
      } else if (actual instanceof Set || actual instanceof Map) {
        pass = actual.has(item);
      }
      if (isNot ? pass : !pass) {
        throw new Error(`Expected ${safeStringify(actual)} ${isNot ? 'NOT to contain' : 'to contain'} ${safeStringify(item)}`);
      }
    },
    toMatch(regex) {
      const pass = regex instanceof RegExp ? regex.test(String(actual)) : String(actual).includes(String(regex));
      if (isNot ? pass : !pass) {
        throw new Error(`Expected ${safeStringify(actual)} ${isNot ? 'NOT to match' : 'to match'} ${regex}`);
      }
    },
    toBeCloseTo(expected, precision = 2) {
      const pass = Math.abs(actual - expected) < Math.pow(10, -precision) / 2;
      if (isNot ? pass : !pass) {
        throw new Error(`Expected ${actual} ${isNot ? 'NOT to be close to' : 'to be close to'} ${expected} (precision ${precision})`);
      }
    },
    toThrow(expectedSubstringOrType) {
      if (typeof actual !== 'function') {
        throw new Error(`Expected actual to be a function, received ${typeof actual}`);
      }
      let threw = false;
      let error = null;
      try {
        actual();
      } catch (e) {
        threw = true;
        error = e;
      }
      if (isNot ? threw : !threw) {
        throw new Error(`Expected function ${isNot ? 'NOT to throw' : 'to throw'}, but it ${threw ? 'threw: ' + error.message : 'did not throw'}`);
      }
      if (threw && expectedSubstringOrType) {
        if (typeof expectedSubstringOrType === 'string') {
          if (!error.message.includes(expectedSubstringOrType)) {
            throw new Error(`Expected error message "${error.message}" to contain "${expectedSubstringOrType}"`);
          }
        } else if (typeof expectedSubstringOrType === 'function') {
          if (!(error instanceof expectedSubstringOrType)) {
            throw new Error(`Expected error to be instance of ${expectedSubstringOrType.name}, received ${error.name}`);
          }
        }
      }
    }
  });

  const obj = matchers(false);
  obj.not = matchers(true);
  return obj;
}

// ─── Test Suite Execution Context ─────────────────────────────────────────────

let currentSuite = null;
export const suiteResults = [];

export function describe(name, fn) {
  const suite = {
    name,
    tests: [],
    beforeEach: [],
    afterEach: [],
    beforeAll: [],
    afterAll: [],
    passed: 0,
    failed: 0,
    errors: []
  };

  const prevSuite = currentSuite;
  currentSuite = suite;

  try {
    fn();
  } finally {
    currentSuite = prevSuite;
  }

  suiteResults.push(suite);
  return suite;
}

export function it(name, fn) {
  if (!currentSuite) {
    describe('Default Suite', () => {
      currentSuite.tests.push({ name, fn });
    });
    return;
  }
  currentSuite.tests.push({ name, fn });
}

export const test = it;

export function beforeEach(fn) {
  if (currentSuite) currentSuite.beforeEach.push(fn);
}

export function afterEach(fn) {
  if (currentSuite) currentSuite.afterEach.push(fn);
}

export function beforeAll(fn) {
  if (currentSuite) currentSuite.beforeAll.push(fn);
}

export function afterAll(fn) {
  if (currentSuite) currentSuite.afterAll.push(fn);
}

export function clearSuiteRegistry() {
  suiteResults.length = 0;
  currentSuite = null;
}

export async function runSuite(suite) {
  console.log(`\n▶ Running Suite: \x1b[36m${suite.name}\x1b[0m (${suite.tests.length} tests)`);
  const startTime = Date.now();
  suite.total = suite.tests.length;
  suite.passed = 0;
  suite.failed = 0;

  for (const hook of suite.beforeAll) {
    await hook();
  }

  for (const t of suite.tests) {
    for (const hook of suite.beforeEach) {
      await hook();
    }

    const testStart = Date.now();
    try {
      if (t.fn.length > 0) {
        await new Promise((resolve, reject) => {
          t.fn((err) => (err ? reject(err) : resolve()));
        });
      } else {
        const result = t.fn();
        if (result && typeof result.then === 'function') {
          await result;
        }
      }
      suite.passed++;
      t.status = 'passed';
      const duration = Date.now() - testStart;
      console.log(`  \x1b[32m✔\x1b[0m ${t.name} \x1b[90m(${duration}ms)\x1b[0m`);
    } catch (err) {
      suite.failed++;
      t.status = 'failed';
      t.error = err;
      const duration = Date.now() - testStart;
      console.log(`  \x1b[31m✖\x1b[0m ${t.name} \x1b[90m(${duration}ms)\x1b[0m`);
      console.log(`    \x1b[31mError:\x1b[0m ${err.message}`);
      if (err.stack) {
        const firstStack = err.stack.split('\n').slice(1, 3).join('\n');
        console.log(`    \x1b[90m${firstStack}\x1b[0m`);
      }
      suite.errors.push({ testName: t.name, error: err });
    }

    for (const hook of suite.afterEach) {
      try { await hook(); } catch (e) { console.warn('afterEach hook error:', e.message); }
    }
  }

  for (const hook of suite.afterAll) {
    try { await hook(); } catch (e) { console.warn('afterAll hook error:', e.message); }
  }

  const totalTime = Date.now() - startTime;
  console.log(`Summary: \x1b[32m${suite.passed} passed\x1b[0m, \x1b[31m${suite.failed} failed\x1b[0m (${totalTime}ms)`);
  return suite;
}

export async function runAllRegisteredSuites() {
  let totalPassed = 0;
  let totalFailed = 0;
  const start = Date.now();

  const suitesToRun = [...suiteResults];
  for (const suite of suitesToRun) {
    await runSuite(suite);
    totalPassed += suite.passed;
    totalFailed += suite.failed;
  }

  const duration = Date.now() - start;
  console.log('\n======================================================');
  console.log(`Total: \x1b[32m${totalPassed} passed\x1b[0m, \x1b[31m${totalFailed} failed\x1b[0m (${duration}ms)`);
  console.log('======================================================');

  return {
    total: totalPassed + totalFailed,
    passed: totalPassed,
    failed: totalFailed,
    duration,
    suites: suitesToRun
  };
}

// ─── Mock DOM Environment ─────────────────────────────────────────────────────

export class MockClassList {
  constructor(element) {
    this.element = element;
    this.classes = new Set();
    this._syncFromClassName();
  }

  _syncFromClassName() {
    this.classes.clear();
    if (this.element.className) {
      this.element.className.trim().split(/\s+/).forEach(c => {
        if (c) this.classes.add(c);
      });
    }
  }

  _syncToClassName() {
    this.element.className = Array.from(this.classes).join(' ');
  }

  add(...classNames) {
    classNames.forEach(c => { if (c) this.classes.add(c); });
    this._syncToClassName();
  }

  remove(...classNames) {
    classNames.forEach(c => this.classes.delete(c));
    this._syncToClassName();
  }

  contains(className) {
    return this.classes.has(className);
  }

  toggle(className, force) {
    if (force !== undefined) {
      if (force) this.add(className);
      else this.remove(className);
      return force;
    }
    const has = this.contains(className);
    if (has) this.remove(className);
    else this.add(className);
    return !has;
  }

  get length() {
    return this.classes.size;
  }
}

export class MockElement {
  constructor(tagName = 'div', id = '') {
    this.tagName = tagName.toUpperCase();
    this.id = id;
    this._className = '';
    this.classList = new MockClassList(this);
    this.style = {};
    this.attributes = new Map();
    this.children = [];
    this.parentElement = null;
    this.parentNode = null;
    this.listeners = new Map();
    this.textContent = '';
    this._innerHTML = '';
    this.value = '';
    this.src = '';
    this.muted = false;
    this.volume = 1.0;
    this.duration = NaN;
    this.currentTime = 0;
    this.paused = true;
    this.controls = false;
    this.autoplay = false;
    this.error = null;
    this.videoWidth = 0;
    this.videoHeight = 0;
    this.dataset = {};
    this.contentWindow = null;

    if (this.tagName === 'IFRAME') {
      this.contentWindow = {
        postMessage: (msg, targetOrigin) => {
          this.lastPostedMessage = msg;
          this.lastTargetOrigin = targetOrigin;
        }
      };
    }
  }

  get className() {
    return this._className;
  }

  set className(val) {
    this._className = String(val || '');
    this.classList._syncFromClassName();
  }

  get innerHTML() {
    return this._innerHTML;
  }

  set innerHTML(val) {
    this._innerHTML = String(val || '');
    this.children = [];
    if (!this._innerHTML) {
      return;
    }
    // Auto-parse basic elements if contains HTML tags
    if (this._innerHTML.includes('<') && this._innerHTML.includes('>')) {
      const tagRegex = /<([a-z0-9-]+)([^>]*)>(.*?)<\/\1>/gi;
      let match;
      while ((match = tagRegex.exec(this._innerHTML)) !== null) {
        const childTag = match[1];
        const attrStr = match[2] || '';
        const child = new MockElement(childTag);
        if (attrStr) {
          const classM = attrStr.match(/class=["']([^"']+)["']/i);
          if (classM) child.className = classM[1];
          const idM = attrStr.match(/id=["']([^"']+)["']/i);
          if (idM) child.id = idM[1];
        }
        child.textContent = match[3] || '';
        child.parentElement = this;
        child.parentNode = this;
        this.children.push(child);
      }
    }
  }

  setAttribute(name, val) {
    this.attributes.set(name, String(val));
    if (name === 'id') this.id = String(val);
    if (name === 'class') this.className = String(val);
    if (name.startsWith('data-')) {
      const key = name.slice(5).replace(/-([a-z])/g, (_, l) => l.toUpperCase());
      this.dataset[key] = String(val);
    }
  }

  getAttribute(name) {
    return this.attributes.has(name) ? this.attributes.get(name) : null;
  }

  removeAttribute(name) {
    this.attributes.delete(name);
  }

  hasAttribute(name) {
    return this.attributes.has(name);
  }

  appendChild(child) {
    if (child) {
      if (child.parentElement && typeof child.parentElement.removeChild === 'function') {
        child.parentElement.removeChild(child);
      }
      child.parentElement = this;
      child.parentNode = this;
      this.children.push(child);
    }
    return child;
  }

  removeChild(child) {
    const idx = this.children.indexOf(child);
    if (idx !== -1) {
      this.children.splice(idx, 1);
      child.parentElement = null;
      child.parentNode = null;
    }
    return child;
  }

  remove() {
    if (this.parentElement) {
      this.parentElement.removeChild(this);
    }
  }

  addEventListener(type, handler, options = {}) {
    if (!this.listeners.has(type)) {
      this.listeners.set(type, []);
    }
    this.listeners.get(type).push({ handler, options });
  }

  removeEventListener(type, handler) {
    if (this.listeners.has(type)) {
      const arr = this.listeners.get(type).filter(l => l.handler !== handler);
      this.listeners.set(type, arr);
    }
  }

  dispatchEvent(event) {
    event.target = this;
    event.currentTarget = this;
    if (this.listeners.has(event.type)) {
      const list = [...this.listeners.get(event.type)];
      for (const item of list) {
        item.handler.call(this, event);
        if (item.options && item.options.once) {
          this.removeEventListener(event.type, item.handler);
        }
      }
    }
    return true;
  }

  querySelector(selector) {
    return this._matchSelector(selector, false);
  }

  querySelectorAll(selector) {
    const results = [];
    this._matchSelectorAll(selector, results);
    return results;
  }

  _matchSelector(selector, checkSelf = true) {
    if (checkSelf && this._matches(selector)) return this;
    for (const child of this.children) {
      const res = child._matchSelector(selector, true);
      if (res) return res;
    }
    return null;
  }

  _matchSelectorAll(selector, results) {
    if (this._matches(selector)) results.push(this);
    for (const child of this.children) {
      child._matchSelectorAll(selector, results);
    }
  }

  _matches(selector) {
    if (!selector) return false;
    const s = selector.trim();
    if (s.includes(',')) {
      return s.split(',').some(sub => this._matches(sub.trim()));
    }
    if (s.startsWith('#')) {
      return this.id === s.slice(1);
    }
    if (s.startsWith('.')) {
      if (s.includes('[')) {
        const cls = s.substring(1, s.indexOf('['));
        const attr = s.substring(s.indexOf('['));
        return this.classList.contains(cls) && this._matches(attr);
      }
      return this.classList.contains(s.slice(1));
    }
    if (s.startsWith('[') && s.endsWith(']')) {
      const inner = s.slice(1, -1);
      if (inner.includes('=')) {
        const [k, v] = inner.split('=');
        const cleanV = v.replace(/^["']|["']$/g, '');
        return this.getAttribute(k.trim()) === cleanV;
      }
      return this.hasAttribute(inner);
    }
    return this.tagName.toLowerCase() === s.toLowerCase();
  }

  closest(selector) {
    let curr = this;
    while (curr) {
      if (curr._matches(selector)) return curr;
      curr = curr.parentElement;
    }
    return null;
  }

  contains(el) {
    let curr = el;
    while (curr) {
      if (curr === this) return true;
      curr = curr.parentElement;
    }
    return false;
  }

  blur() {
    this.isFocused = false;
    if (globalThis.document && globalThis.document.activeElement === this) {
      globalThis.document.activeElement = null;
    }
  }

  focus() {
    this.isFocused = true;
    if (globalThis.document) {
      globalThis.document.activeElement = this;
    }
  }

  click() {
    this.dispatchEvent(new MockEvent('click', { bubbles: true }));
  }

  play() {
    this.paused = false;
    this.dispatchEvent(new MockEvent('play'));
    this.dispatchEvent(new MockEvent('playing'));
    return Promise.resolve();
  }

  pause() {
    this.paused = true;
    this.dispatchEvent(new MockEvent('pause'));
  }

  load() {
    this.dispatchEvent(new MockEvent('loadstart'));
  }

  canPlayType(type) {
    if (type.includes('apple.mpegurl') || type.includes('mp4') || type.includes('mkv')) {
      return 'probably';
    }
    return '';
  }

  requestFullscreen() {
    if (globalThis.document) {
      globalThis.document.fullscreenElement = this;
    }
    return Promise.resolve();
  }

  webkitRequestFullscreen() {
    return Promise.resolve();
  }

  async requestPictureInPicture() {
    return Promise.resolve();
  }
}

export class MockEvent {
  constructor(type, init = {}) {
    this.type = type;
    this.bubbles = init.bubbles || false;
    this.cancelable = init.cancelable || false;
    this.defaultPrevented = false;
    this.target = null;
    this.currentTarget = null;
  }

  preventDefault() {
    this.defaultPrevented = true;
  }

  stopPropagation() {}
}

export class MockKeyboardEvent extends MockEvent {
  constructor(type, init = {}) {
    super(type, init);
    this.key = init.key || '';
    this.code = init.code || '';
    this.ctrlKey = init.ctrlKey || false;
    this.shiftKey = init.shiftKey || false;
    this.altKey = init.altKey || false;
  }
}

export class MockCustomEvent extends MockEvent {
  constructor(type, init = {}) {
    super(type, init);
    this.detail = init.detail || null;
  }
}

export class MockStorage {
  constructor() {
    this.store = new Map();
  }

  getItem(key) {
    return this.store.has(String(key)) ? this.store.get(String(key)) : null;
  }

  setItem(key, val) {
    this.store.set(String(key), String(val));
  }

  removeItem(key) {
    this.store.delete(String(key));
  }

  clear() {
    this.store.clear();
  }

  get length() {
    return this.store.size;
  }

  key(index) {
    const keys = Array.from(this.store.keys());
    return keys[index] || null;
  }
}

// ─── Mock HLS.js ──────────────────────────────────────────────────────────────

export class MockHls {
  static isSupported() {
    return true;
  }

  static Events = {
    MANIFEST_PARSED: 'hlsManifestParsed',
    FRAG_BUFFERED: 'hlsFragBuffered',
    ERROR: 'hlsError',
    LEVEL_SWITCHED: 'hlsLevelSwitched',
    DESTROYING: 'hlsDestroying'
  };

  static ErrorTypes = {
    NETWORK_ERROR: 'networkError',
    MEDIA_ERROR: 'mediaError',
    OTHER_ERROR: 'otherError'
  };

  static ErrorDetails = {
    MANIFEST_LOAD_ERROR: 'manifestLoadError',
    MANIFEST_PARSING_ERROR: 'manifestParsingError',
    BUFFER_STALLED_ERROR: 'bufferStalledError',
    FRAG_LOAD_ERROR: 'fragLoadError'
  };

  constructor(config = {}) {
    this.config = config;
    this.listeners = new Map();
    this.media = null;
    this.attachedMedia = null;
    this.url = null;
    this.loadedSource = '';
    this.isDestroyed = false;
    this.destroyed = false;
    this.autoTriggerManifest = config.autoTriggerManifest !== false;
    this.currentLevel = -1;
    this.autoLevelEnabled = true;
    this.levels = [
      { width: 1920, height: 1080, bitrate: 5000000, name: '1080p' },
      { width: 1280, height: 720, bitrate: 2500000, name: '720p' },
      { width: 854, height: 480, bitrate: 1200000, name: '480p' }
    ];
  }

  on(event, handler) {
    if (!this.listeners.has(event)) {
      this.listeners.set(event, []);
    }
    this.listeners.get(event).push(handler);
  }

  off(event, handler) {
    if (this.listeners.has(event)) {
      const filtered = this.listeners.get(event).filter(h => h !== handler);
      this.listeners.set(event, filtered);
    }
  }

  trigger(event, data) {
    if (this.listeners.has(event)) {
      this.listeners.get(event).forEach(h => h(event, data));
    }
  }

  loadSource(url) {
    this.url = url;
    this.loadedSource = url;
    if (this.autoTriggerManifest) {
      setTimeout(() => {
        if (!this.isDestroyed) {
          this.trigger(MockHls.Events.MANIFEST_PARSED, { levels: this.levels });
        }
      }, 10);
    }
  }

  attachMedia(media) {
    this.media = media;
    this.attachedMedia = media;
  }

  detachMedia() {
    this.media = null;
    this.attachedMedia = null;
  }

  destroy() {
    this.isDestroyed = true;
    this.destroyed = true;
    this.trigger(MockHls.Events.DESTROYING, {});
    this.listeners.clear();
    this.media = null;
    this.attachedMedia = null;
    this.loadedSource = '';
  }

  startLoad() {
    setTimeout(() => {
      if (!this.isDestroyed) {
        this.trigger(MockHls.Events.FRAG_BUFFERED, { frag: { duration: 6 } });
      }
    }, 10);
  }

  recoverMediaError() {
    setTimeout(() => {
      if (!this.isDestroyed) {
        this.trigger(MockHls.Events.FRAG_BUFFERED, { frag: { duration: 6 } });
      }
    }, 10);
  }
}

// ─── Environment Setup ────────────────────────────────────────────────────────

export function setupTestEnvironment(customRoutes = {}) {
  const elements = new Map();

  function getOrCreateElement(id, tagName = 'div') {
    if (elements.has(id)) return elements.get(id);
    const el = new MockElement(tagName, id);
    elements.set(id, el);
    return el;
  }

  // Pre-seed common SPA elements
  const videoPlayer = getOrCreateElement('video-player', 'video');
  const playerWrapper = getOrCreateElement('player-wrapper', 'div');
  playerWrapper.classList.add('player-wrapper');
  playerWrapper.appendChild(videoPlayer);

  const playerPoster = getOrCreateElement('player-poster', 'div');
  const playerLoading = getOrCreateElement('player-loading', 'div');
  const playerError = getOrCreateElement('player-error', 'div');
  const errorDetails = getOrCreateElement('error-details', 'div');
  const playerToast = getOrCreateElement('player-toast', 'div');
  const toastMessage = getOrCreateElement('toast-message', 'div');

  const playPauseBtn = getOrCreateElement('play-pause-btn', 'button');
  const playPauseIcon = new MockElement('i');
  playPauseBtn.appendChild(playPauseIcon);

  const chPrevBtn = getOrCreateElement('ch-prev-btn', 'button');
  const chNextBtn = getOrCreateElement('ch-next-btn', 'button');
  const muteBtn = getOrCreateElement('mute-btn', 'button');
  const muteIcon = new MockElement('i');
  muteBtn.appendChild(muteIcon);

  const volumeSlider = getOrCreateElement('volume-slider', 'input');
  const fullscreenBtn = getOrCreateElement('fullscreen-btn', 'button');
  const fullscreenIcon = new MockElement('i');
  fullscreenBtn.appendChild(fullscreenIcon);
  const pipBtn = getOrCreateElement('pip-btn', 'button');
  const aspectRatioBtn = getOrCreateElement('aspect-ratio-btn', 'button');
  const aspectLabel = getOrCreateElement('aspect-label', 'span');
  const channelTitle = getOrCreateElement('player-channel-title', 'h2');
  const reloadBtn = getOrCreateElement('reload-btn', 'button');
  const retryBtn = getOrCreateElement('retry-stream-btn', 'button');

  const seekContainer = getOrCreateElement('seek-container', 'div');
  const seekSlider = getOrCreateElement('seek-slider', 'input');
  const currentTimeLabel = getOrCreateElement('current-time', 'span');
  const durationTimeLabel = getOrCreateElement('duration-time', 'span');
  const seekBack30Btn = getOrCreateElement('seek-back-30-btn', 'button');
  const seekBack10Btn = getOrCreateElement('seek-back-10-btn', 'button');
  const seekFwd10Btn = getOrCreateElement('seek-fwd-10-btn', 'button');
  const seekFwd30Btn = getOrCreateElement('seek-fwd-30-btn', 'button');

  const videoControls = getOrCreateElement('video-controls', 'div');
  const liveIndicatorDot = getOrCreateElement('live-indicator-dot', 'div');
  const liveIndicatorText = getOrCreateElement('live-indicator-text', 'span');
  const embedIframe = getOrCreateElement('embed-iframe', 'iframe');
  const embedShield = getOrCreateElement('embed-shield', 'div');
  const playerSection = getOrCreateElement('player-section', 'section');

  const detailsModal = getOrCreateElement('details-modal', 'div');
  const settingsModal = getOrCreateElement('settings-modal', 'div');
  const modalBackdrop = getOrCreateElement('modal-backdrop', 'div');
  const channelGrid = getOrCreateElement('channel-grid', 'div');
  const categoryList = getOrCreateElement('category-list', 'div');
  const searchInput = getOrCreateElement('search-input', 'input');
  const sidebar = getOrCreateElement('sidebar', 'nav');
  const mobileNav = getOrCreateElement('mobile-nav', 'nav');
  const podcastDockPlayer = getOrCreateElement('podcast-dock-player', 'div');

  const docListeners = new Map();
  const winListeners = new Map();

  const mockDocument = {
    getElementById: (id) => elements.get(id) || mockDocument.body?._matchSelector?.('#' + id) || null,
    querySelector: (sel) => {
      if (sel.startsWith('#')) return elements.get(sel.slice(1)) || mockDocument.body?._matchSelector?.(sel) || null;
      if (sel === '.player-wrapper') return playerWrapper;
      if (sel === 'body') return mockDocument.body;
      return mockDocument.body?._matchSelector?.(sel) || null;
    },
    querySelectorAll: (sel) => {
      const res = [];
      for (const el of elements.values()) {
        if (el._matches(sel) && !res.includes(el)) res.push(el);
      }
      if (mockDocument.body) {
        const bodyMatches = mockDocument.body.querySelectorAll(sel);
        for (const el of bodyMatches) {
          if (!res.includes(el)) res.push(el);
        }
      }
      return res;
    },
    createElement: (tag) => {
      const el = new MockElement(tag);
      return el;
    },
    createTextNode: (text) => ({ textContent: text }),
    addEventListener: (type, handler) => {
      if (!docListeners.has(type)) docListeners.set(type, []);
      docListeners.get(type).push(handler);
    },
    removeEventListener: (type, handler) => {
      if (docListeners.has(type)) {
        docListeners.set(type, docListeners.get(type).filter(h => h !== handler));
      }
    },
    dispatchEvent: (event) => {
      if (docListeners.has(event.type)) {
        docListeners.get(event.type).forEach(h => h(event));
      }
      return true;
    },
    body: getOrCreateElement('body', 'body'),
    head: getOrCreateElement('head', 'head'),
    activeElement: null,
    fullscreenElement: null,
    pictureInPictureEnabled: true,
    exitFullscreen: () => {
      mockDocument.fullscreenElement = null;
      return Promise.resolve();
    }
  };

  const localStorage = new MockStorage();
  const sessionStorage = new MockStorage();

  const mockWindow = {
    location: {
      host: 'localhost:3000',
      protocol: 'http:',
      href: 'http://localhost:3000/'
    },
    navigator: {
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
    },
    localStorage,
    sessionStorage,
    document: mockDocument,
    innerWidth: 1280,
    innerHeight: 720,
    addEventListener: (type, handler) => {
      if (!winListeners.has(type)) winListeners.set(type, []);
      winListeners.get(type).push(handler);
    },
    removeEventListener: (type, handler) => {
      if (winListeners.has(type)) {
        winListeners.set(type, winListeners.get(type).filter(h => h !== handler));
      }
    },
    dispatchEvent: (event) => {
      if (winListeners.has(event.type)) {
        winListeners.get(event.type).forEach(h => h(event));
      }
      return true;
    },
    lucide: {
      createIcons: () => {}
    },
    Audio: MockAudioElement,
    Image: MockElement,
    Event: MockEvent,
    KeyboardEvent: MockKeyboardEvent,
    CustomEvent: MockCustomEvent,
    DOMParser: class MockDOMParser {
      parseFromString(str, mimeType) {
        const doc = new MockElement('doc');
        doc.querySelector = (sel) => null;
        doc.querySelectorAll = (sel) => [];
        return doc;
      }
    }
  };

  // Mock Fetch implementation
  const mockFetch = async (url, options = {}) => {
    const urlStr = String(url);
    let decodedUrl = urlStr;
    try { decodedUrl = decodeURIComponent(urlStr); } catch (_) {}

    if (customRoutes[urlStr]) {
      const handler = customRoutes[urlStr];
      return typeof handler === 'function' ? handler(urlStr, options) : handler;
    }

    for (const [pattern, handler] of Object.entries(customRoutes)) {
      if (urlStr.includes(pattern) || decodedUrl.includes(pattern)) {
        return typeof handler === 'function' ? handler(urlStr, options) : handler;
      }
    }

    // Default EPG XMLTV mock
    if (urlStr.includes('xmltv.php') || decodedUrl.includes('xmltv.php') || urlStr.includes('epg') || decodedUrl.includes('epg')) {
      if (urlStr.includes('get_short_epg') || decodedUrl.includes('get_short_epg')) {
        // Fall through to short EPG below
      } else {
        const xml = `<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="cnn_news"><display-name>CNN News HD</display-name></channel>
  <channel id="espn_sports"><display-name>ESPN HD</display-name></channel>
  <channel id="hbo_movies"><display-name>HBO East</display-name></channel>
  <programme channel="cnn_news" start="${new Date(Date.now() - 1800000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 1800000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>Situation Room</title>
    <desc>Live political news coverage</desc>
  </programme>
  <programme channel="espn_sports" start="${new Date(Date.now() - 3600000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000" stop="${new Date(Date.now() + 3600000).toISOString().replace(/[-:T]/g, '').slice(0, 14)} +0000">
    <title>SportsCenter Live</title>
    <desc>Highlight recap and analysis</desc>
  </programme>
</tv>`;
        return {
          ok: true,
          status: 200,
          text: async () => xml,
          json: async () => ({})
        };
      }
    }

    // Default Xtream short EPG
    if (urlStr.includes('get_short_epg') || decodedUrl.includes('get_short_epg')) {
      const nowSec = Math.floor(Date.now() / 1000);
      return {
        ok: true,
        status: 200,
        json: async () => ({
          epg_listings: [
            {
              title: Buffer.from('Breaking News Hour').toString('base64'),
              description: Buffer.from('Current events coverage').toString('base64'),
              start_timestamp: nowSec - 900,
              stop_timestamp: nowSec + 2700
            },
            {
              title: Buffer.from('Nightly Report').toString('base64'),
              description: Buffer.from('Late night roundup').toString('base64'),
              start_timestamp: nowSec + 2700,
              stop_timestamp: nowSec + 6300
            }
          ]
        })
      };
    }

    // Default VOD categories
    if (urlStr.includes('get_vod_categories')) {
      return {
        ok: true,
        status: 200,
        json: async () => [
          { category_id: '1', category_name: 'Action Movies' },
          { category_id: '2', category_name: 'Sci-Fi & Fantasy' }
        ]
      };
    }

    // Default VOD movies
    if (urlStr.includes('get_vod_streams')) {
      return {
        ok: true,
        status: 200,
        json: async () => [
          { stream_id: 101, name: 'Interstellar (2014)', rating: 8.7, stream_icon: 'https://img.sample.com/interstellar.jpg' },
          { stream_id: 102, name: 'The Dark Knight (2008)', rating: 9.0, stream_icon: 'https://img.sample.com/darkknight.jpg' }
        ]
      };
    }

    // Default Series categories
    if (urlStr.includes('get_series_categories')) {
      return {
        ok: true,
        status: 200,
        json: async () => [
          { category_id: '21', category_name: 'Drama Series' },
          { category_id: '22', category_name: 'Sci-Fi Series' }
        ]
      };
    }

    // Default Series list
    if (urlStr.includes('get_series') && !urlStr.includes('get_series_info')) {
      return {
        ok: true,
        status: 200,
        json: async () => [
          { series_id: 201, name: 'Severance', rating: 8.8, cover: 'https://img.sample.com/severance.jpg' }
        ]
      };
    }

    // Default Series info
    if (urlStr.includes('get_series_info')) {
      return {
        ok: true,
        status: 200,
        json: async () => ({
          info: { name: 'Severance', plot: 'Lumon Industries mystery.', rating: '8.8' },
          episodes: {
            '1': [
              { id: '3001', episode_num: 1, title: 'Good News About Hell', container_extension: 'mp4' },
              { id: '3002', episode_num: 2, title: 'Half Loop', container_extension: 'mp4' }
            ]
          }
        })
      };
    }

    // Default RSS Podcast feed
    if (urlStr.includes('rss') || urlStr.includes('feed') || urlStr.includes('youtube.com/feeds')) {
      const rssXml = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Lex Fridman Podcast</title>
    <item>
      <title>Sam Altman: OpenAI and Future of AGI</title>
      <enclosure url="https://audio.sample.com/ep400.mp3" length="123456" type="audio/mpeg"/>
      <pubDate>Mon, 18 Aug 2026 12:00:00 GMT</pubDate>
    </item>
  </channel>
</rss>`;
      return {
        ok: true,
        status: 200,
        text: async () => rssXml,
        json: async () => ({})
      };
    }

    return {
      ok: true,
      status: 200,
      text: async () => 'OK',
      json: async () => ({})
    };
  };

  // Bind globals
  globalThis.window = mockWindow;
  globalThis.document = mockDocument;
  globalThis.localStorage = localStorage;
  globalThis.sessionStorage = sessionStorage;
  try {
    Object.defineProperty(globalThis, 'navigator', {
      value: mockWindow.navigator,
      configurable: true,
      writable: true
    });
  } catch (_) {
    try { globalThis.navigator = mockWindow.navigator; } catch (_) {}
  }
  globalThis.fetch = mockFetch;
  globalThis.Event = MockEvent;
  globalThis.KeyboardEvent = MockKeyboardEvent;
  globalThis.CustomEvent = MockCustomEvent;
  globalThis.Audio = class MockAudio extends MockElement {
    constructor(src = '') {
      super('audio');
      this.src = src;
    }
  };
  globalThis.Image = class MockImage extends MockElement {
    constructor() {
      super('img');
    }
  };
  globalThis.DOMParser = class MockDOMParser {
    parseFromString(str, mimeType) {
      // Basic mock parser
      const doc = new MockElement('doc');
      doc.querySelector = (sel) => null;
      doc.querySelectorAll = (sel) => [];
      return doc;
    }
  };

  return {
    elements,
    document: mockDocument,
    window: mockWindow,
    doc: mockDocument,
    win: mockWindow,
    storage: localStorage,
    localStorage,
    sessionStorage,
    mockFetch,
    getOrCreateElement
  };
}

export class MockVideoElement extends MockElement {
  constructor(id = '') {
    super('video', id);
  }
}

export class MockAudioElement extends MockElement {
  constructor(src = '') {
    super('audio');
    this.src = src;
  }
}

export class MockIframeElement extends MockElement {
  constructor(id = '') {
    super('iframe', id);
  }
}

export const mockDOM = setupTestEnvironment;
export const mockLocalStorage = (init = {}) => new MockStorage(init);
export const mockAudio = (src = '') => new MockAudioElement(src);
export const mockVideo = (id = '') => new MockVideoElement(id);
export const mockIframe = (id = '') => new MockIframeElement(id);
export const mockHls = (config = {}) => new MockHls(config);

export function resetTestEnvironment() {
  if (globalThis.localStorage) globalThis.localStorage.clear();
  if (globalThis.sessionStorage) globalThis.sessionStorage.clear();
}

export const restoreTestEnvironment = resetTestEnvironment;

