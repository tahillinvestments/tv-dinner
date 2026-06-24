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
    this.fullscreenBtn.addEventListener('click', () => this.toggleFullscreen());
    this.video.addEventListener('dblclick', () => this.toggleFullscreen());

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
    this.video.addEventListener('loadstart', () => {
      if (this.currentUrl) this.showLoading(true);
    });
    this.video.addEventListener('error', (e) => this.handleNativeError(e));
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
    if (!document.fullscreenElement) {
      const container = this.video.parentElement;
      if (container.requestFullscreen) {
        container.requestFullscreen();
      } else if (this.video.webkitEnterFullscreen) {
        // iOS Safari native fullscreen fallback
        this.video.webkitEnterFullscreen();
      }
    } else {
      document.exitFullscreen();
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
      this.video.className = "w-full h-full video-stretch pointer-events-none";
      this.aspectLabel.textContent = "STRETCH";
      this.showToast("Aspect Ratio: Stretch to fill");
    } else {
      this.aspectRatio = 'fit';
      this.video.className = "w-full h-full video-fit pointer-events-none";
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
