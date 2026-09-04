# Project: TV Dinner Android Multimedia Application Overhaul & Stabilization

## Architecture
TV Dinner is a high-performance multimedia client designed for Android and web environments.
- **Frontend Layer**: Vanilla JavaScript (ES modules), HTML5, and CSS3 bundled via Vite. Single Page Application architecture with 7 distinct views (`home`, `live`, `movies`, `series`, `podcasts`, `library`, `settings`).
- **Media Playback Engine**: Unified `IPTVPlayer` wrapping HTML5 `<video>` and `Hls.js`, complemented by dedicated HTML5 `<audio>` engine for podcast audio and YouTube iframe embed for video podcasts.
- **Native Android Host & In-App Proxy Bridge**: Jetpack Compose `ComponentActivity` hosting AndroidX `WebView` served securely via `WebViewAssetLoader` from `https://appassets.androidplatform.net`. Native `OkHttpClient` intercepts streaming, VOD, and API requests to handle CORS headers, VLC User-Agent spoofing, M3U8 manifest rewriting, SSL trust bypass, 302/307 redirects, and HTTP 206 Partial Content byte ranges.

## Code Layout
- `src/main.js`: Main SPA controller, view routing, activation, channel rendering, VOD/podcast modals, event listeners.
- `src/player.js`: `IPTVPlayer` implementation, HLS.js lifecycle, volume/mute state, seeking, fullscreen, aspect ratio, resume tracking.
- `src/epgService.js`: XMLTV and short-EPG data fetcher, parser, fuzzy channel matching, program progress calculation.
- `src/xtreamVODService.js`: Xtream API client for Movies and Series metadata, category fetching, and stream URL resolution.
- `src/podcastsData.js`: Curated podcast directory, feed parser, category mapping.
- `src/style.css`: Responsive CSS layout (desktop sidebar, mobile bottom nav, player overlay, modal styling).
- `index.html`: Base SPA markup, sidebar, mobile drawer, player containers, modals.
- `android-app/`: Native Android project (`com.tvdinner`).
  - `app/src/main/java/com/tvdinner/MainActivity.kt`: Native Activity, WebViewAssetLoader, OkHttp proxy & M3U8 rewriter.
  - `app/src/main/AndroidManifest.xml`: Permissions, hardware acceleration, cleartext traffic.
  - `app/build.gradle.kts`: Gradle build configuration, dependencies, signing.
- `scratch/build_apk_assets.js`: Staging script copying Vite `dist/` to Android assets with module script transforms.
- `tests/`: Automated unit and E2E test suites.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Responsive Navigation Layout | Unified sidebar (>1024px) & fixed bottom nav (<1024px) responsive across touch, mouse, and remote D-pad | M2 | R1 |
| 2 | Clean Module State Teardown | Complete media teardown on tab switch; stop video/audio/iframes without frozen UI or orphan background audio | M2 | R1 |
| 3 | Instant Activation Unlocking | LocalStorage credential persistence with instant unlocking across all views without page refresh | M2 | R1 |
| 4 | Channel Grid Rendering Optimization | Batching/virtualization of channel grid to eliminate main-thread freeze during category browsing | M2 | R1 |
| 5 | Scoped Error Fixes & Modal Stability | Fix undefined `videoEl` in `closeDetailsView` and ensure consistent body scroll restoration | M2 | R1 |
| 6 | Instant Live TV HLS Playback | Fast buffering, credential normalization, and network jitter retry via HLS.js & proxy | M3 | R2 |
| 7 | Native Proxy Live Stream Rewriting | Dynamic M3U8 playlist segment rewriting, VLC User-Agent spoofing, and redirect following | M1, M3 | R2, R5 |
| 8 | Real-Time EPG Program Guide | Dual-tier XMLTV bulk parsing + short EPG fallback, progress bar calculation & live 30s ticker updates | M3 | R2 |
| 9 | Fullscreen Live Player Controls | Custom controls for play/pause, volume slider, mute toggle, channel switcher, and aspect-ratio toggle | M3 | R2 |
| 10 | Xtream Movies & Series VOD Streaming | Direct streaming for MP4/MKV video streams with HTTP Range 206 seeking | M4 | R3 |
| 11 | Dynamic Series Container Extension | Dynamic resolution of episode container extensions (.mp4, .mkv, .avi) preventing playback errors | M4 | R3 |
| 12 | Rich VOD Details & Episode Picker | Details modal with backdrop, metadata, synopsis, season selector, and episode grid | M4 | R3 |
| 13 | VOD Playback Resume Tracking | Throttled LRU playback position persistence in localStorage with auto-resume on load | M4 | R3 |
| 14 | Audio Podcasts & Dock Player | Instantiated HTML5 Audio player engine with dock controls (play/pause, skip, progress slider) | M5 | R4 |
| 15 | Podcast RSS Feed & YouTube Ingestion | RSS 2.0 enclosure parsing + YouTube XML feed parsing with Invidious fallback | M5 | R4 |
| 16 | Podcast Subscription & Episode Tracking | LocalStorage persistence for subscribed channels and episode playback history | M5 | R4 |
| 17 | Video Podcast Playback & Fullscreen | Embedded video playback with fullscreen expansion support | M5 | R4 |
| 18 | Permissive SSL Trust in Native Proxy | Custom TrustManager & HostnameVerifier in OkHttpClient for untrusted IPTV stream sources | M1 | R5 |
| 19 | Android Native Unit Test Stabilization | Fix obsolete `MainScreenViewModelTest.kt` to ensure `./gradlew.bat testDebugUnitTest` passes cleanly | M1 | R5 |
| 20 | Android Release Packaging & Proguard | Complete `proguard-rules.pro`, remove deprecated settings, and verify signed release APK build | M1 | R5 |
| 21 | Comprehensive E2E Test Suite | 4-tier opaque-box test suite covering all features, boundaries, combinations, and scenarios | E2E Track | Criteria |
| 22 | Adversarial Hardening & Smoke Verification | White-box stress testing, coverage hardening (Tier 5), and Android emulator smoke verification | M6 | Criteria |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Native Android Bridge & Build Hardening | SSL trust, OkHttp proxy, Gradle config, Proguard rules, fix unit tests, release build verification | none | DONE |
| M2 | Navigation, Module Transitions & UI Optimization | Responsive sidebar/bottom nav, D-pad support, channel grid batching, state teardown, orphan audio fix, activation flow | M1 | DONE |
| M3 | Live TV & Real-Time EPG Engine | HLS stream buffering, proxy integration, dual-tier XMLTV/short EPG parser, progress tickers, fullscreen player controls | M1, M2 | DONE |
| M4 | Xtream VOD (Movies & Series) Engine | Direct MP4/MKV streaming, dynamic episode extensions, Range 206 seeking, rich details, season/episode picker, resume tracking | M1, M2 | DONE |
| M5 | Podcasts Engine & Audio/Video Media | Audio player initialization, dock player controls, RSS 2.0 & YouTube feed parsing, subscription/history tracking, video fullscreen | M1, M2 | DONE |
| M6 | Final Verification, E2E Pass & Adversarial Hardening | Pass 100% E2E test suite (Tiers 1-4), adversarial hardening (Tier 5), release APK compilation & emulator smoke verification | M1, M2, M3, M4, M5 | DONE |

## Interface Contracts
### Media Controller ↔ Player Lifecycle
- `stopAllMediaPlayback({ keepPlayer = false, keepHls = false, resetState = true })`: Synchronously pauses `<video>`, tears down `Hls.js`, pauses `Audio`, sets iframe src to `'about:blank'`, clears unmanaged unmute timeouts, and resets loading/error overlays.
- `IPTVPlayer.prototype.playStream(url, isLive, options)`: Accepts normalized stream URL, configures HLS or native HTML5 video, binds resume tracking for VOD, and handles autoplay policy with tracked timers.
- `IPTVPlayer.prototype.destroyHls()`: Unbinds listeners and calls `hls.destroy()` synchronously.

### EPG Service ↔ Live View
- `initEPGFeed(username, password)`: Returns Promise resolving to structured `xmltvEpgTable`.
- `getProgramProgress(epgInfo)`: Returns `{ percent: number, remainingMinutes: number, formattedStart: string, formattedEnd: string, isLive: boolean, title: string }`.

### Xtream VOD Service ↔ VOD Modals
- `getSeriesInfo(seriesId)`: Returns `{ info: Object, episodes: Object.<seasonNum, Array<Episode>> }`.
- `getSeriesStreamUrl(streamId, containerExtension)`: Constructs authenticated stream URL with provided extension (defaulting to `'mp4'` if not specified).

### Native Proxy ↔ Web Client
- `/api/proxy?url={encodedUrl}`: OkHttp proxy endpoint returning original status (200/206), rewritten M3U8 or streamed binary payload, with permissive CORS headers.
