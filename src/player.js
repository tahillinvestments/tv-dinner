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
    this.muteBtn = document.getElementById('mute-btn');
    this.volumeSlider = document.getElementById('volume-slider');
    this.fullscreenBtn = document.getElementById('fullscreen-btn');
    this.pipBtn = document.getElementById('pip-btn');
    this.aspectRatioBtn = document.getElementById('aspect-ratio-btn');
    this.aspectLabel = document.getElementById('aspect-label');
    this.channelTitle = document.getElementById('player-channel-title');
    this.reloadBtn = document.getElementById('reload-btn');
    this.retryBtn = document.getElementById('retry-stream-btn');

    // Seek VOD Controls
    this.seekContainer = document.getElementById('seek-container');
    this.seekSlider = document.getElementById('seek-slider');
    this.currentTimeLabel = document.getElementById('current-time');
    this.durationTimeLabel = document.getElementById('duration-time');

    // States
    this.isMuted = false;
    this.volume = 1;
    this.aspectRatio = 'fit'; // 'fit' or 'stretch'
    
    this.initEventListeners();
  }

  initEventListeners() {
    // Play/Pause toggling
    this.playPauseBtn.addEventListener('click', () => this.togglePlay());
    this.video.addEventListener('click', () => this.togglePlay());
    
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
      if (controlsHideTimeout) clearTimeout(controlsHideTimeout);
      controlsHideTimeout = setTimeout(() => {
        if (this.video && !this.video.paused) {
          container.classList.remove('controls-active');
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
        
        // If user watched more than 95% of video, reset it; otherwise save if played > 10s
        if (this.video.currentTime / this.video.duration > 0.95) {
          delete resumeData[this.currentUrl];
        } else if (this.video.currentTime > 10) {
          resumeData[this.currentUrl] = {
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

  playChannel(channel) {
    this.currentUrl = channel.url;
    this.channelTitle.textContent = channel.name;
    
    // Hide poster
    if (this.poster) {
      this.poster.classList.add('opacity-0', 'pointer-events-none');
    }
    
    this.showError(false);
    this.showLoading(true);

    // Destroy existing HLS instance
    this.destroyHls();

    // Check for saved resume position
    const resumeData = JSON.parse(localStorage.getItem('vod_resume_positions') || '{}');
    const saved = resumeData[this.currentUrl];

    // Check HLS compatibility
    if (Hls.isSupported()) {
      this.hls = new Hls({
        enableWorker: true,
        lowLatencyMode: true,
        maxBufferSize: 30 * 1000 * 1000, // 30MB
      });
      
      this.hls.loadSource(this.currentUrl);
      this.hls.attachMedia(this.video);
      
      this.hls.on(Hls.Events.MANIFEST_PARSED, () => {
        if (saved && saved.position > 0) {
          this.video.currentTime = saved.position;
          this.showToast(`Resumed from ${this.formatTime(saved.position)}`);
        }
        this.video.play().catch(e => {
          console.warn("Autoplay blocked by browser policy, waiting for user interaction.", e);
          this.updatePlayPauseUI(true); // show play icon
        });
      });

      this.hls.on(Hls.Events.ERROR, (event, data) => {
        if (data.fatal) {
          switch (data.type) {
            case Hls.ErrorTypes.NETWORK_ERROR:
              console.error("Fatal network error in HLS playback. Trying recovery...");
              this.hls.startLoad();
              break;
            case Hls.ErrorTypes.MEDIA_ERROR:
              console.error("Fatal media error. Attempting recovery...");
              this.hls.recoverMediaError();
              break;
            default:
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
        if (saved && saved.position > 0) {
          this.video.currentTime = saved.position;
          this.showToast(`Resumed from ${this.formatTime(saved.position)}`);
        }
      }, { once: true });

      this.video.play().catch(e => {
        console.warn("Native HLS autoplay blocked", e);
        this.updatePlayPauseUI(true);
      });
    } else {
      this.showError(true, "Your browser does not support HLS streaming.");
    }
    
    this.updatePlayPauseUI(false);
  }

  togglePlay() {
    if (!this.currentUrl) return;
    
    if (this.video.paused) {
      this.video.play();
      this.updatePlayPauseUI(false);
      this.showToast("Playback resumed");
    } else {
      this.video.pause();
      this.updatePlayPauseUI(true);
      this.showToast("Playback paused");
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
    if (!this.currentUrl) return;
    this.showToast("Reloading stream...");
    this.playChannel({ url: this.currentUrl, name: this.channelTitle.textContent });
  }

  handleNativeError(event) {
    if (this.video.error && this.currentUrl) {
      console.error("Native HTML5 video error:", this.video.error);
      this.showError(true, `Playback error code: ${this.video.error.code}. Stream might be offline.`);
    }
  }

  destroyHls() {
    if (this.hls) {
      this.hls.destroy();
      this.hls = null;
    }
  }
}
