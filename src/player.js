import Hls from 'hls.js';

export class IPTVPlayer {
  constructor(videoElementId, options = {}) {
    this.video = document.getElementById(videoElementId);
    this.hls = null;
    this.currentUrl = '';
    
    // UI elements
    this.poster = document.getElementById('player-poster');
    this.loading = document.getElementById('player-loading');
    this.error = document.getElementById('player-error');
    this.errorDetails = document.getElementById('error-details');
    this.toast = document.getElementById('player-toast');
    this.toastMessage = document.getElementById('toast-message');
    
    // Controls
    this.playPauseBtn = document.getElementById('play-pause-btn');
    this.chPrevBtn = document.getElementById('ch-prev-btn');
    this.chNextBtn = document.getElementById('ch-next-btn');
    this.muteBtn = document.getElementById('mute-btn');
    this.volumeSlider = document.getElementById('volume-slider');
    this.fullscreenBtn = document.getElementById('fullscreen-btn');
    this.pipBtn = document.getElementById('pip-btn');
    this.aspectRatioBtn = document.getElementById('aspect-ratio-btn');
    this.aspectLabel = document.getElementById('aspect-label');
    this.channelTitle = document.getElementById('player-channel-title');
    this.reloadBtn = document.getElementById('reload-btn');
    this.retryBtn = document.getElementById('retry-stream-btn');

    // Callback handlers
    this.onChannelChange = options.onChannelChange || null;

    // Seek VOD Controls & Buttons
    this.seekContainer = document.getElementById('seek-container');
    this.seekSlider = document.getElementById('seek-slider');
    this.currentTimeLabel = document.getElementById('current-time');
    this.durationTimeLabel = document.getElementById('duration-time');
    this.seekBack30Btn = document.getElementById('seek-back-30-btn');
    this.seekBack10Btn = document.getElementById('seek-back-10-btn');
    this.seekFwd10Btn = document.getElementById('seek-fwd-10-btn');
    this.seekFwd30Btn = document.getElementById('seek-fwd-30-btn');

    // States
    this.isMuted = false;
    this.volume = 1;
    this.aspectRatio = 'fit'; // 'fit' or 'stretch'
    
    this.initEventListeners();
  }

  initEventListeners() {
    // Play/Pause toggling
    if (this.playPauseBtn) {
      this.playPauseBtn.addEventListener('click', () => this.togglePlay());
    }
    if (this.video) {
      this.video.addEventListener('click', () => this.togglePlay());
    }

    // VOD Seek Buttons (-30s, -10s, +10s, +30s)
    if (this.seekBack30Btn) this.seekBack30Btn.addEventListener('click', () => this.seekBy(-30));
    if (this.seekBack10Btn) this.seekBack10Btn.addEventListener('click', () => this.seekBy(-10));
    if (this.seekFwd10Btn) this.seekFwd10Btn.addEventListener('click', () => this.seekBy(10));
    if (this.seekFwd30Btn) this.seekFwd30Btn.addEventListener('click', () => this.seekBy(30));

    // Channel Up / Channel Down buttons (Live TV)
    if (this.chPrevBtn) {
      this.chPrevBtn.addEventListener('click', () => {
        if (typeof this.onChannelChange === 'function') {
          this.onChannelChange(-1);
        }
      });
    }
    if (this.chNextBtn) {
      this.chNextBtn.addEventListener('click', () => {
        if (typeof this.onChannelChange === 'function') {
          this.onChannelChange(1);
        }
      });
    }
    
    // Volume & Muting
    this.muteBtn.addEventListener('click', () => this.toggleMute());
    this.volumeSlider.addEventListener('input', (e) => {
      this.setVolume(e.target.value);
    });

    // Fullscreen
    if (this.fullscreenBtn) {
      this.fullscreenBtn.addEventListener('click', () => this.toggleFullscreen());
    }
    this.video.addEventListener('dblclick', () => this.toggleFullscreen());

    const container = this.video ? this.video.parentElement : null;
    if (container) {
      container.addEventListener('dblclick', (e) => {
        if (e.target.closest('.video-controls-bar')) return;
        this.toggleFullscreen();
      });
    }

    // Mouse movement, gamepad focus & touch controls auto-hide timer
    let controlsHideTimeout = null;
    const showControls = (delay = 2500) => {
      if (!container) return;
      container.classList.add('controls-active');
      container.classList.remove('user-idle');
      if (controlsHideTimeout) clearTimeout(controlsHideTimeout);
      controlsHideTimeout = setTimeout(() => {
        if (this.video && !this.video.paused) {
          container.classList.remove('controls-active');
          container.classList.add('user-idle');
          if (document.activeElement && container.contains(document.activeElement)) {
            document.activeElement.blur();
          }
        }
      }, delay);
    };
    this.triggerShowControls = showControls;

    const updateFullscreenState = () => {
      const isFS = !!(
        document.fullscreenElement ||
        document.webkitFullscreenElement ||
        document.mozFullScreenElement ||
        document.msFullscreenElement ||
        (container && container.classList.contains('is-pseudo-fullscreen'))
      );

      this.updateFullscreenUI(isFS);
      if (isFS) {
        if (document.activeElement) document.activeElement.blur();
        showControls(2500);
      } else if (container) {
        container.classList.remove('user-idle');
        container.classList.add('controls-active');
      }
    };

    document.addEventListener('fullscreenchange', updateFullscreenState);
    document.addEventListener('webkitfullscreenchange', updateFullscreenState);
    document.addEventListener('mozfullscreenchange', updateFullscreenState);
    document.addEventListener('MSFullscreenChange', updateFullscreenState);

    if (container) {
      container.addEventListener('mousemove', () => showControls(2500));
      container.addEventListener('touchstart', () => showControls(2500), { passive: true });
      container.addEventListener('focusin', () => showControls(2500));
      container.addEventListener('keydown', () => showControls(2500));
      container.addEventListener('mouseleave', () => {
        if (this.video && !this.video.paused) {
          container.classList.remove('controls-active');
          container.classList.add('user-idle');
        }
      });
    }

    // Seeking
    if (this.seekSlider) {
      this.seekSlider.addEventListener('input', (e) => {
        if (!this.video.duration) return;
        const seekTime = (e.target.value / 100) * this.video.duration;
        if (this.currentTimeLabel) {
          this.currentTimeLabel.textContent = this.formatTime(seekTime);
        }
      });

      this.seekSlider.addEventListener('change', (e) => {
        if (!this.video.duration) return;
        const seekTime = (e.target.value / 100) * this.video.duration;
        this.video.currentTime = seekTime;
      });
    }

    // PiP
    if (this.pipBtn) {
      if (document.pictureInPictureEnabled || this.video.webkitSupportsPresentationMode) {
        this.pipBtn.addEventListener('click', () => this.togglePiP());
      } else {
        this.pipBtn.classList.add('hidden');
      }
    }

    // Aspect Ratio
    this.aspectRatioBtn.addEventListener('click', () => this.toggleAspectRatio());

    // Reload / Retry
    this.reloadBtn.addEventListener('click', () => this.reloadStream());
    if (this.retryBtn) {
      this.retryBtn.addEventListener('click', () => this.reloadStream());
    }

    // Video native events
    this.video.addEventListener('pause', () => {
      if (container) {
        container.classList.remove('user-idle');
        container.classList.add('controls-active');
      }
    });
    this.video.addEventListener('play', () => {
      showControls(2500);
    });
    this.video.addEventListener('waiting', () => {
      if (this.currentUrl) this.showLoading(true);
    });
    this.video.addEventListener('playing', () => {
      this.showLoading(false);
      this.showError(false);
    });
    this.video.addEventListener('canplay', () => {
      this.showLoading(false);
      this.showError(false);
    });
    this.video.addEventListener('canplaythrough', () => {
      this.showLoading(false);
    });
    this.video.addEventListener('loadstart', () => {
      if (this.currentUrl) this.showLoading(true);
    });
    this.video.addEventListener('error', (e) => this.handleNativeError(e));

    // Video events for updating progress
    this.video.addEventListener('timeupdate', () => {
      if (this.loading && !this.loading.classList.contains('hidden') && !this.video.paused && this.video.currentTime > 0) {
        this.showLoading(false);
        this.showError(false);
      }
      this.updateProgress();
      this.savePlaybackPosition();
    });
    this.video.addEventListener('durationchange', () => this.updateDuration());
    this.video.addEventListener('loadedmetadata', () => this.updateDuration());

    // Keyboard shortcuts
    document.addEventListener('keydown', (e) => {
      // Don't trigger if user is typing in inputs or select fields
      if (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA' || document.activeElement.tagName === 'SELECT') {
        return;
      }

      const key = e.key.toLowerCase();
      if (key === ' ' || key === 'k') { // Space or K for Play/Pause
        e.preventDefault();
        this.togglePlay();
      } else if (key === 'f') { // F for Fullscreen
        e.preventDefault();
        this.toggleFullscreen();
      } else if (key === 'm') { // M for Mute
        e.preventDefault();
        this.toggleMute();
      } else if (key === 'arrowright') { // Arrow Right to seek +10s
        e.preventDefault();
        if (this.video.duration && isFinite(this.video.duration)) {
          this.video.currentTime = Math.min(this.video.duration, this.video.currentTime + 10);
          this.showToast("Seeked +10s");
        }
      } else if (key === 'arrowleft') { // Arrow Left to seek -10s
        e.preventDefault();
        if (this.video.duration && isFinite(this.video.duration)) {
          this.video.currentTime = Math.max(0, this.video.currentTime - 10);
          this.showToast("Seeked -10s");
        }
      } else if (key === 'pageup' || key === ']' || key === 'channelup') {
        e.preventDefault();
        if (typeof this.onChannelChange === 'function') this.onChannelChange(1);
      } else if (key === 'pagedown' || key === '[' || key === 'channeldown') {
        e.preventDefault();
        if (typeof this.onChannelChange === 'function') this.onChannelChange(-1);
      } else if (key === 'arrowup') { // Arrow Up to increase volume
        e.preventDefault();
        const newVolume = Math.min(1, this.volume + 0.05);
        this.setVolume(newVolume);
        this.showToast(`Volume: ${Math.round(newVolume * 100)}%`);
      } else if (key === 'arrowdown') { // Arrow Down to decrease volume
        e.preventDefault();
        const newVolume = Math.max(0, this.volume - 0.05);
        this.setVolume(newVolume);
        this.showToast(`Volume: ${Math.round(newVolume * 100)}%`);
      }
    });
  }

  // Periodic VOD playback position saver
  savePlaybackPosition() {
    if (!this.currentUrl || !this.video.duration || isNaN(this.video.duration) || this.video.duration === Infinity) return;
    
    // Only save progress for VOD assets (has finite duration > 0)
    if (this.video.duration > 0) {
      const now = Date.now();
      // Throttled position writing to localStorage (every 5 seconds)
      if (!this.lastSaveTime || now - this.lastSaveTime > 5000) {
        this.lastSaveTime = now;
        
        const resumeData = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
        const keys = Object.keys(resumeData);
        if (keys.length > 100) {
          delete resumeData[keys[0]]; // remove oldest record
        }
        
        const key = this.currentMediaKey || this.currentUrl;
        
        // If user watched more than 95% of video, reset it; otherwise save if played > 10s
        if (this.video.currentTime / this.video.duration > 0.95) {
          delete resumeData[key];
        } else if (this.video.currentTime > 10) {
          resumeData[key] = {
            position: this.video.currentTime,
            timestamp: now
          };
        }
        localStorage.setItem('vod_resume_positions', JSON.stringify(resumeData));
      }
    }
  }

  showToast(message, duration = 3000) {
    if (!this.toast) return;
    this.toastMessage.textContent = message;
    this.toast.classList.remove('opacity-0');
    this.toast.classList.add('opacity-100');
    
    if (this.toastTimeout) clearTimeout(this.toastTimeout);
    this.toastTimeout = setTimeout(() => {
      this.toast.classList.remove('opacity-100');
      this.toast.classList.add('opacity-0');
    }, duration);
  }

  showLoading(isLoading) {
    if (!this.loading) return;
    if (isLoading) {
      this.loading.classList.remove('hidden');
    } else {
      this.loading.classList.add('hidden');
    }
  }

  showError(isError, message = '') {
    if (!this.error) return;
    if (isError) {
      this.error.classList.remove('hidden');
      this.errorDetails.textContent = message;
      this.showLoading(false);
    } else {
      this.error.classList.add('hidden');
    }
  }

  playChannel(channel, startPosition = 0) {
    this.resetVideoFrame();
    this.currentMediaKey = channel.mediaKey || null;
    
    let rawUrl = channel.url || channel.src || '';

    // Extract underlying raw target URL if already proxied
    let rawTargetUrl = rawUrl;
    if (rawUrl.includes('url=')) {
      try {
        const parts = rawUrl.split('url=');
        const extracted = decodeURIComponent(parts[parts.length - 1]);
        if (extracted.startsWith('http://') || extracted.startsWith('https://')) {
          rawTargetUrl = extracted;
        }
      } catch (e) {}
    }

    // Ensure stream URL uses the user's active entered credentials & .m3u8 format for HLS
    const activeUser = (localStorage.getItem('iptv_username') || '').trim();
    const activePass = (localStorage.getItem('iptv_password') || '').trim();
    if (activeUser && activePass && rawTargetUrl.includes('/live/')) {
      rawTargetUrl = rawTargetUrl.replace(/\/live\/[^/]+\/[^/]+\//, `/live/${activeUser}/${activePass}/`);
    }
    if (rawTargetUrl.includes('/live/') && rawTargetUrl.endsWith('.ts')) {
      rawTargetUrl = rawTargetUrl.replace(/\.ts$/, '.m3u8');
    }
    
    // Video streams always go through native /api/proxy (Vercel Node.js runtime)
    const proxiedUrl = `/api/proxy?url=${encodeURIComponent(rawTargetUrl)}`;

    this.currentUrl = proxiedUrl;
    this.channelTitle.textContent = channel.name || 'Live TV Stream';
    
    // Hide poster & hide error overlay
    if (this.poster) {
      this.poster.classList.add('opacity-0', 'pointer-events-none');
    }
    
    this.showError(false);
    if (this.error) this.error.classList.add('hidden');
    this.showLoading(true);

    // Unmute audio by default at 100% volume
    this.isMuted = false;
    this.volume = 1;
    if (this.video) {
      this.video.muted = false;
      this.video.volume = 1;
    }
    if (this.volumeSlider) this.volumeSlider.value = 1;
    this.updateMuteUI();

    // Check for saved resume position
    const resumeData = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
    const key = this.currentMediaKey || this.currentUrl;
    const saved = resumeData[key];
    const resumePos = (typeof startPosition === 'number' && startPosition > 0)
      ? startPosition
      : (saved ? saved.position : 0);

    // Safety timeout to prevent Buffering Stream... from getting stuck indefinitely
    if (this.bufferTimeout) clearTimeout(this.bufferTimeout);
    const initialTime = this.video ? this.video.currentTime : 0;
    this.bufferTimeout = setTimeout(() => {
      if (this.loading && !this.loading.classList.contains('hidden')) {
        const hasAdvanced = this.video && !this.video.paused && this.video.currentTime > initialTime;
        if (!hasAdvanced) {
          console.warn('[Player] Stream loading timed out.');
          this.showLoading(false);
          this.showError(true, 'Stream load timed out. Stream may be offline or slow to respond.');
        } else {
          this.showLoading(false);
        }
      }
    }, 15000);

    let networkRetryIndex = 0;
    const originalUrl = rawTargetUrl;

    const proxyFallbacks = [
      (url) => `/api/proxy?url=${encodeURIComponent(url)}`,
      (url) => `https://corsproxy.io/?${encodeURIComponent(url)}`
    ];

    // Check HLS compatibility
    let hlsRetryAttempts = 0;

    if (Hls.isSupported()) {
      this.destroyHls();
      this.hls = new Hls({
        enableWorker: true,
        lowLatencyMode: false,
        startPosition: (resumePos && resumePos > 0) ? resumePos : -1,
        maxBufferLength: 45,
        maxMaxBufferLength: 90,
        maxBufferSize: 60 * 1000 * 1000, // 60MB
        manifestLoadingTimeOut: 20000,
        manifestLoadingMaxRetry: 6,
        levelLoadingTimeOut: 20000,
        levelLoadingMaxRetry: 6,
        fragLoadingTimeOut: 25000,
        fragLoadingMaxRetry: 8,
        liveSyncDurationCount: 3,
        liveMaxLatencyDurationCount: 10,
        capLevelToPlayerSize: true,
      });
      
      this.hls.loadSource(this.currentUrl);
      this.hls.attachMedia(this.video);
      
      this.hls.on(Hls.Events.FRAG_BUFFERED, () => {
        if (this.loading && !this.loading.classList.contains('hidden') && !this.video.paused) {
          this.showLoading(false);
        }
      });

      this.hls.on(Hls.Events.MANIFEST_PARSED, () => {
        if (this.bufferTimeout) clearTimeout(this.bufferTimeout);
        this.showLoading(false);
        this.showError(false);

        if (resumePos && resumePos > 0) {
          try {
            this.video.currentTime = resumePos;
            this.showToast(`Resumed from ${this.formatTime(resumePos)}`);
          } catch (e) {
            console.warn("HLS seek error:", e);
          }
        }

        this.video.play().then(() => {
          this.updatePlayPauseUI(false);
        }).catch(e => {
          console.warn("Autoplay blocked by browser policy, waiting for user interaction.", e);
          this.updatePlayPauseUI(true); // show play icon
        });
      });

      this.hls.on(Hls.Events.ERROR, (event, data) => {
        console.warn('[HLS Event Error]', data.type, data.details, 'fatal:', data.fatal);
        if (data.fatal) {
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              hlsRetryAttempts++;
              if (hlsRetryAttempts <= 5) {
                console.warn(`[Player] HLS network jitter retry #${hlsRetryAttempts}...`);
                this.showLoading(true);
                setTimeout(() => {
                  if (this.hls) {
                    this.hls.startLoad();
                  }
                }, 1000);
              } else if (networkRetryIndex < proxyFallbacks.length) {
                hlsRetryAttempts = 0;
                const nextProxyFunc = proxyFallbacks[networkRetryIndex++];
                const fallbackUrl = nextProxyFunc(originalUrl);
                console.log(`[Player] Trying proxy fallback #${networkRetryIndex}:`, fallbackUrl);
                this.currentUrl = fallbackUrl;
                this.showLoading(true);
                this.hls.loadSource(fallbackUrl);
                this.hls.startLoad();
              } else {
                this.showError(true, 'Stream network connection failed. Stream is offline or blocked by browser CORS/security.');
                this.destroyHls();
              }
              break;
            case Hls.ErrorTypes.MEDIA_ERROR:
              console.warn("[Player] Fatal media error. Attempting media recovery...");
              this.showLoading(true);
              this.hls.recoverMediaError();
              break;
            default:
              console.error("[Player] Unrecoverable HLS error:", data.details);
              this.showError(true, `HLS Error: ${data.details || 'Unknown fatal error'}`);
              this.destroyHls();
              break;
          }
        }
      });
    } else if (this.video.canPlayType('application/vnd.apple.mpegurl')) {
      // Native Safari/iOS support
      this.video.src = this.currentUrl;
      
      this.video.addEventListener('loadedmetadata', () => {
        if (this.bufferTimeout) clearTimeout(this.bufferTimeout);
        this.showLoading(false);
        if (resumePos && resumePos > 0) {
          this.video.currentTime = resumePos;
          this.showToast(`Resumed from ${this.formatTime(resumePos)}`);
        }
      }, { once: true });

      this.video.play().then(() => {
        this.updatePlayPauseUI(false);
      }).catch(e => {
        console.warn("Native HLS autoplay blocked", e);
        if (this.bufferTimeout) clearTimeout(this.bufferTimeout);
        this.showLoading(false);
        this.updatePlayPauseUI(true);
      });
    } else {
      this.showError(true, "Your browser does not support HLS streaming.");
    }
    
    this.updatePlayPauseUI(false);
  }

  togglePlay() {
    const embedIframe = document.getElementById('embed-iframe');
    const playerWrapper = document.querySelector('.player-wrapper');
    const isEmbed = playerWrapper && playerWrapper.classList.contains('embed-active');

    if (isEmbed && embedIframe && embedIframe.contentWindow) {
      const shield = document.getElementById('embed-shield');
      if (shield && shield.style.display !== 'none') {
        shield.style.display = 'none';
      }

      this.isEmbedPaused = !this.isEmbedPaused;
      
      try {
        const action = this.isEmbedPaused ? 'pause' : 'play';
        embedIframe.contentWindow.postMessage({ event: 'command', func: this.isEmbedPaused ? 'pauseVideo' : 'playVideo' }, '*');
        embedIframe.contentWindow.postMessage(JSON.stringify({ event: 'command', func: this.isEmbedPaused ? 'pauseVideo' : 'playVideo' }), '*');
        embedIframe.contentWindow.postMessage(JSON.stringify({ method: action }), '*');
        embedIframe.contentWindow.postMessage(action, '*');
      } catch (e) {}

      this.updatePlayPauseUI(this.isEmbedPaused);
      this.showToast(this.isEmbedPaused ? "Embed stream paused — Click video directly to play" : "Embed stream playing");
      return;
    }
    
    if (!this.currentUrl && (!this.video || !this.video.src)) return;

    if (this.video.paused) {
      if (this.hls && typeof this.hls.startLoad === 'function') {
        this.hls.startLoad();
      }
      this.video.play().then(() => {
        this.updatePlayPauseUI(false);
        this.showToast("Playback resumed");
      }).catch(err => {
        console.warn("Play request failed:", err);
      });
    } else {
      this.video.pause();
      this.updatePlayPauseUI(true);
      this.showToast("Playback paused");
    }
  }

  seekBy(seconds) {
    const embedIframe = document.getElementById('embed-iframe');
    const playerWrapper = document.querySelector('.player-wrapper');
    const isEmbed = playerWrapper && playerWrapper.classList.contains('embed-active');
    const label = seconds > 0 ? `+${seconds}s` : `${seconds}s`;

    if (isEmbed && embedIframe && embedIframe.contentWindow) {
      const shield = document.getElementById('embed-shield');
      if (shield && shield.style.display !== 'none') {
        shield.style.display = 'none';
      }

      try {
        embedIframe.contentWindow.postMessage({ event: 'command', func: 'seekBy', args: [seconds] }, '*');
        embedIframe.contentWindow.postMessage(JSON.stringify({ event: 'command', func: 'seekBy', args: [seconds] }), '*');
      } catch (e) {}

      this.showToast(`Seeking ${label} on embed feed...`);
      return;
    }

    if (this.video && this.video.duration && isFinite(this.video.duration)) {
      const newTime = Math.max(0, Math.min(this.video.duration, this.video.currentTime + seconds));
      this.video.currentTime = newTime;
      this.showToast(`Seeked ${label} (${this.formatTime(newTime)})`);
    } else if (this.video && this.video.currentTime !== undefined) {
      const newTime = Math.max(0, this.video.currentTime + seconds);
      this.video.currentTime = newTime;
      this.showToast(`Seeked ${label}`);
    }
  }

  setControlMode(mode = 'vod') {
    const controlsBar = document.getElementById('video-controls');
    if (!controlsBar) return;
    if (mode === 'live') {
      controlsBar.classList.remove('vod-mode');
      controlsBar.classList.add('live-mode');
    } else {
      controlsBar.classList.remove('live-mode');
      controlsBar.classList.add('vod-mode');
    }
  }

  updatePlayPauseUI(isPaused) {
    if (!this.playPauseBtn) return;
    const icon = this.playPauseBtn.querySelector('i');
    if (icon) {
      if (isPaused) {
        icon.setAttribute('data-lucide', 'play');
        icon.classList.add('fill-white');
      } else {
        icon.setAttribute('data-lucide', 'pause');
        icon.classList.remove('fill-white');
      }
      // Re-initialize Lucide icon
      if (window.lucide) {
        window.lucide.createIcons();
      }
    }
  }

  formatTime(seconds) {
    if (isNaN(seconds) || seconds === Infinity) return "0:00";
    const hrs = Math.floor(seconds / 3600);
    const mins = Math.floor((seconds % 3600) / 60);
    const secs = Math.floor(seconds % 60);
    
    if (hrs > 0) {
      return `${hrs}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  }

  updateProgress() {
    if (!this.video.duration || isNaN(this.video.duration) || this.video.duration === Infinity) return;
    const progress = (this.video.currentTime / this.video.duration) * 100;
    if (this.seekSlider) {
      this.seekSlider.value = progress;
    }
    if (this.currentTimeLabel) {
      this.currentTimeLabel.textContent = this.formatTime(this.video.currentTime);
    }
  }

  updateDuration() {
    const duration = this.video.duration;
    const liveDot = document.getElementById('live-indicator-dot');
    const liveText = document.getElementById('live-indicator-text');
    
    if (duration && isFinite(duration) && duration > 0) {
      if (this.seekContainer) {
        this.seekContainer.style.display = 'flex';
      }
      if (this.durationTimeLabel) {
        this.durationTimeLabel.textContent = this.formatTime(duration);
      }
      if (liveDot) liveDot.style.display = 'none';
      if (liveText) liveText.style.display = 'none';
    } else {
      if (this.seekContainer) {
        this.seekContainer.style.display = 'none';
      }
      if (liveDot) liveDot.style.display = '';
      if (liveText) liveText.style.display = '';
    }
  }

  toggleMute() {
    this.isMuted = !this.isMuted;
    this.video.muted = this.isMuted;
    this.updateMuteUI();
    this.showToast(this.isMuted ? "Audio muted" : "Audio unmuted");
  }

  setVolume(val) {
    this.volume = parseFloat(val);
    this.video.volume = this.volume;
    
    if (this.volume === 0) {
      this.isMuted = true;
      this.video.muted = true;
    } else {
      this.isMuted = false;
      this.video.muted = false;
    }
    
    this.updateMuteUI();
  }

  updateMuteUI() {
    const icon = this.muteBtn.querySelector('i');
    if (!icon) return;

    if (this.isMuted) {
      icon.setAttribute('data-lucide', 'volume-x');
    } else if (this.volume < 0.4) {
      icon.setAttribute('data-lucide', 'volume-1');
    } else {
      icon.setAttribute('data-lucide', 'volume-2');
    }
    
    this.volumeSlider.value = this.isMuted ? 0 : this.volume;
    
    if (window.lucide) {
      window.lucide.createIcons();
    }
  }

  toggleFullscreen() {
    const container = this.video ? (this.video.parentElement || document.getElementById('player-section')) : null;
    if (!container) return;

    const nativeFS = !!(
      document.fullscreenElement ||
      document.webkitFullscreenElement ||
      document.mozFullScreenElement ||
      document.msFullscreenElement
    );

    const isPseudo = container.classList.contains('is-pseudo-fullscreen');

    const exitPseudo = () => {
      container.classList.remove('is-pseudo-fullscreen');
      document.body.classList.remove('body-pseudo-fullscreen');
      this.updateFullscreenUI(false);
      this.showToast("Exited Fullscreen");
    };

    const enterPseudo = () => {
      container.classList.add('is-pseudo-fullscreen');
      document.body.classList.add('body-pseudo-fullscreen');
      this.updateFullscreenUI(true);
      this.showToast("Entered Fullscreen Mode");
      if (document.activeElement) document.activeElement.blur();
      if (this.triggerShowControls) this.triggerShowControls(2500);
    };

    if (nativeFS || isPseudo) {
      if (isPseudo) {
        exitPseudo();
      }
      if (nativeFS) {
        if (document.exitFullscreen) {
          document.exitFullscreen().catch(() => {});
        } else if (document.webkitExitFullscreen) {
          document.webkitExitFullscreen();
        } else if (document.mozCancelFullScreen) {
          document.mozCancelFullScreen();
        } else if (document.msExitFullscreen) {
          document.msExitFullscreen();
        }
      }
    } else {
      let reqPromise = null;
      if (container.requestFullscreen) {
        reqPromise = container.requestFullscreen();
      } else if (container.webkitRequestFullscreen) {
        reqPromise = container.webkitRequestFullscreen();
      } else if (container.webkitRequestFullScreen) {
        reqPromise = container.webkitRequestFullScreen();
      } else if (container.mozRequestFullScreen) {
        reqPromise = container.mozRequestFullScreen();
      } else if (container.msRequestFullscreen) {
        reqPromise = container.msRequestFullscreen();
      } else if (this.video && this.video.webkitEnterFullscreen) {
        this.video.webkitEnterFullscreen();
        return;
      }

      if (reqPromise && typeof reqPromise.catch === 'function') {
        reqPromise.catch(err => {
          console.warn('Native requestFullscreen failed (Xbox Edge fallback triggered):', err);
          enterPseudo();
        });
      } else if (!reqPromise && !this.video.webkitEnterFullscreen) {
        enterPseudo();
      }
    }
  }

  updateFullscreenUI(isFS) {
    if (this.fullscreenBtn) {
      const icon = this.fullscreenBtn.querySelector('i');
      if (icon) {
        icon.setAttribute('data-lucide', isFS ? 'minimize' : 'maximize');
      }
      this.fullscreenBtn.setAttribute('title', isFS ? 'Exit Fullscreen' : 'Fullscreen');
      if (window.lucide) window.lucide.createIcons();
    }
  }

  async togglePiP() {
    try {
      if (document.pictureInPictureElement) {
        await document.exitPictureInPicture();
        this.showToast("Exited Picture-in-Picture");
      } else {
        await this.video.requestPictureInPicture();
        this.showToast("Entered Picture-in-Picture");
      }
    } catch (error) {
      console.error("Picture in Picture failed:", error);
      // Safari fallback check
      if (this.video.webkitSupportsPresentationMode && this.video.webkitSetPresentationMode) {
        const mode = this.video.webkitPresentationMode === 'picture-in-picture' ? 'inline' : 'picture-in-picture';
        this.video.webkitSetPresentationMode(mode);
      } else {
        this.showToast("Picture-in-Picture not supported on this stream");
      }
    }
  }

  toggleAspectRatio() {
    if (this.aspectRatio === 'fit') {
      this.aspectRatio = 'stretch';
      this.video.className = "w-full h-full video-stretch";
      this.aspectLabel.textContent = "STRETCH";
      this.showToast("Aspect Ratio: Stretch to fill");
    } else {
      this.aspectRatio = 'fit';
      this.video.className = "w-full h-full video-fit";
      this.aspectLabel.textContent = "FIT";
      this.showToast("Aspect Ratio: Fit screen");
    }
  }

  reloadStream() {
    const embedIframe = document.getElementById('embed-iframe');
    const playerWrapper = document.querySelector('.player-wrapper');
    const isEmbed = playerWrapper && playerWrapper.classList.contains('embed-active');

    if (isEmbed && embedIframe) {
      const currentSrc = embedIframe.src;
      if (currentSrc && currentSrc !== 'about:blank') {
        embedIframe.src = 'about:blank';
        setTimeout(() => { embedIframe.src = currentSrc; }, 100);
        this.showToast("Embed feed reloaded");
      }
      return;
    }

    if (!this.currentUrl) return;
    this.showToast("Reloading stream...");
    this.playChannel({ url: this.currentUrl, name: this.channelTitle.textContent, mediaKey: this.currentMediaKey });
  }

  handleNativeError(event) {
    if (this.video.error && this.currentUrl) {
      console.error("Native HTML5 video error:", this.video.error);
      this.showError(true, `Playback error code: ${this.video.error.code}. Stream might be offline.`);
    }
  }

  resetVideoFrame() {
    this.destroyHls();
    this.showError(false);
    this.showLoading(false);
    if (this.error) this.error.classList.add('hidden');
    if (this.loading) this.loading.classList.add('hidden');
    if (this.video) {
      this.video.pause();
      this.video.removeAttribute('src');
      try { this.video.load(); } catch (e) {}
    }
    this.currentUrl = '';
  }

  destroyHls() {
    if (this.bufferTimeout) {
      clearTimeout(this.bufferTimeout);
      this.bufferTimeout = null;
    }
    if (this.hls) {
      try { this.hls.detachMedia(); } catch (e) {}
      this.hls.destroy();
      this.hls = null;
    }
  }
}
