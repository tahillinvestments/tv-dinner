/**
 * Bug Verification & Reproduction Script for M4 Xtream VOD Engine
 */

import { XtreamVODClient, xtreamVOD } from '../src/xtreamVODService.js';
import { IPTVPlayer } from '../src/player.js';
import { setupTestEnvironment, restoreTestEnvironment } from './harness.js';

console.log('=== EMPIRICAL BUG REPRODUCTION TESTS ===\n');

// -------------------------------------------------------------
// BUG 1: Extension Sanitization Order & Multi-Dot Stripping
// -------------------------------------------------------------
console.log('--- TEST 1: Extension Sanitization Order & Multi-Dot ---');
const client = new XtreamVODClient('http://test.portal:8080');

const testCases = [
  { input: '\t.avi\n', expected: 'avi', desc: 'Whitespace preceding leading dot' },
  { input: '..mkv', expected: 'mkv', desc: 'Multiple leading dots' },
  { input: 'mkv#fragment', expected: 'mkv', desc: 'Hash fragments in extension' }
];

for (const tc of testCases) {
  const url = client.getSeriesStreamUrl(101, tc.input);
  const decoded = decodeURIComponent(url);
  const matched = decoded.includes(`101.${tc.expected}`);
  console.log(`[${matched ? 'PASS' : 'BUG CONFIRMED'}] ${tc.desc}: input="${JSON.stringify(tc.input)}" -> URL="${decoded}"`);
}

// -------------------------------------------------------------
// BUG 2: BaseUrl Trailing Slash preservation
// -------------------------------------------------------------
console.log('\n--- TEST 2: BaseUrl Trailing Slash ---');
const clientWithSlash = new XtreamVODClient('http://test.portal:8080/');
const urlWithSlash = decodeURIComponent(clientWithSlash.getMovieStreamUrl(202, 'mp4'));
const hasDoubleSlash = urlWithSlash.includes(':8080//movie');
console.log(`[${hasDoubleSlash ? 'BUG CONFIRMED' : 'PASS'}] BaseUrl with trailing slash creates double slash: "${urlWithSlash}"`);

// -------------------------------------------------------------
// BUG 3: JSON.parse("null") / Non-Object Crash in Resume Storage
// -------------------------------------------------------------
console.log('\n--- TEST 3: Corrupted LocalStorage Resume Object Parsing ---');
const env = setupTestEnvironment();
const player = new IPTVPlayer('video-player');
player.currentMediaKey = 'movie_test_bug3';
player.currentUrl = '/api/proxy?url=http://test/movie.mp4';
player.video.duration = 3600;
player.video.currentTime = 500;

// Test with "null"
localStorage.setItem('vod_resume_positions', 'null');
player.lastSaveTime = 0;
let bug3NullCrash = false;
try {
  player.savePlaybackPosition();
} catch (e) {
  bug3NullCrash = true;
  console.log(`[BUG CONFIRMED] savePlaybackPosition crashed when localStorage is "null": ${e.message}`);
}

// Test with primitive "12345"
localStorage.setItem('vod_resume_positions', '12345');
player.lastSaveTime = 0;
let bug3NumberCrash = false;
try {
  player.savePlaybackPosition();
} catch (e) {
  bug3NumberCrash = true;
  console.log(`[BUG CONFIRMED] savePlaybackPosition crashed when localStorage is "12345": ${e.message}`);
}

// -------------------------------------------------------------
// BUG 4: Negative seconds formatting in formatTime
// -------------------------------------------------------------
console.log('\n--- TEST 4: Negative seconds formatting in formatTime ---');
const negFormat = player.formatTime(-50);
console.log(`[${negFormat === '-1:-50' ? 'BUG CONFIRMED' : 'PASS'}] formatTime(-50) returned "${negFormat}" (expected "0:00")`);

restoreTestEnvironment();

console.log('\n=== REPRODUCTION COMPLETE ===');
