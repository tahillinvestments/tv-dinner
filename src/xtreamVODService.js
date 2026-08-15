// Xtream Codes VOD Client Service
// Connects to active Xtream Codes servers with live movie and series catalogs

export class XtreamVODClient {
  constructor(baseUrl = '') {
    this._baseUrl = baseUrl;
    this._vodCache = new Map();
  }

  get baseUrl() {
    if (this._baseUrl) return this._baseUrl;
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('xtream_vod_portal');
      if (saved && saved.trim()) return saved.trim().replace(/\/+$/, '');
    }
    return 'http://asoseller.org:8080';
  }

  get username() {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('xtream_vod_username');
      if (saved && saved.trim()) return saved.trim();
    }
    return 'gj3526@gmail.com';
  }

  get password() {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('xtream_vod_password');
      if (saved && saved.trim()) return saved.trim();
    }
    return 'ck9sd6Nc4TZA';
  }

  async fetchApi(action, params = {}) {
    const query = new URLSearchParams({
      username: this.username,
      password: this.password,
      action,
      ...params
    });

    const targetUrl = `${this.baseUrl}/player_api.php?${query.toString()}`;
    
    // In web browsers (Vercel, localhost, etc.), route via CORS proxy to prevent HTTPS mixed-content blocks
    const isAndroid = typeof window !== 'undefined' && 
      (window.location.host === 'appassets.androidplatform.net' || 
       window.location.protocol === 'file:' || 
       (navigator.userAgent && navigator.userAgent.includes('JoyfulIPTVMobileApp')));

    const url = isAndroid ? targetUrl : `/api/proxy?url=${encodeURIComponent(targetUrl)}`;

    const res = await fetch(url);
    if (!res.ok) {
      throw new Error(`Xtream API error (${action}): ${res.status}`);
    }
    return res.json();
  }

  async getMovieCategories() {
    const key = 'vod_cats';
    if (this._vodCache.has(key)) return this._vodCache.get(key);
    const res = await this.fetchApi('get_vod_categories');
    this._vodCache.set(key, res || []);
    return res || [];
  }

  async getMovies(categoryId = null) {
    const key = `movies_cat_${categoryId || 'all'}`;
    if (this._vodCache.has(key)) return this._vodCache.get(key);
    const params = categoryId ? { category_id: categoryId } : {};
    const res = await this.fetchApi('get_vod_streams', params);
    this._vodCache.set(key, res || []);
    return res || [];
  }

  async getMovieInfo(vodId) {
    return this.fetchApi('get_vod_info', { vod_id: vodId });
  }

  async getSeriesCategories() {
    const key = 'series_cats';
    if (this._vodCache.has(key)) return this._vodCache.get(key);
    const res = await this.fetchApi('get_series_categories');
    this._vodCache.set(key, res || []);
    return res || [];
  }

  async getSeries(categoryId = null) {
    const key = `series_cat_${categoryId || 'all'}`;
    if (this._vodCache.has(key)) return this._vodCache.get(key);
    const params = categoryId ? { category_id: categoryId } : {};
    const res = await this.fetchApi('get_series', params);
    this._vodCache.set(key, res || []);
    return res || [];
  }

  async getSeriesInfo(seriesId) {
    const key = `series_info_${seriesId}`;
    if (this._vodCache.has(key)) return this._vodCache.get(key);
    const res = await this.fetchApi('get_series_info', { series_id: seriesId });
    this._vodCache.set(key, res || {});
    return res || {};
  }

  async preloadCommon() {
    const commonCats = ['1', '2', '4', '17', '11', '3', '5', '8', '7', '10', '18'];
    try {
      await Promise.allSettled(commonCats.map(cat => this.getMovies(cat)));
    } catch (e) {}
  }

  async findMovieByTitle(title) {
    if (!title) return null;
    const cleanTitle = title.toLowerCase().replace(/[^a-z0-9]/g, '');
    if (!cleanTitle) return null;

    // 1. Check all already-cached categories in memory
    for (const list of this._vodCache.values()) {
      if (Array.isArray(list)) {
        const found = list.find(m => {
          const mName = (m.name || '').toLowerCase().replace(/[^a-z0-9]/g, '');
          return mName.includes(cleanTitle) || cleanTitle.includes(mName);
        });
        if (found) return found;
      }
    }

    // 2. Search common categories in PARALLEL
    const commonCats = ['1', '2', '4', '17', '11', '3', '5', '8', '7', '10', '18'];
    const results = await Promise.allSettled(
      commonCats.map(cat => this.getMovies(cat))
    );

    for (const res of results) {
      if (res.status === 'fulfilled' && Array.isArray(res.value)) {
        const found = res.value.find(m => {
          const mName = (m.name || '').toLowerCase().replace(/[^a-z0-9]/g, '');
          return mName.includes(cleanTitle) || cleanTitle.includes(mName);
        });
        if (found) return found;
      }
    }
    return null;
  }

  async findSeriesByTitle(title) {
    if (!title) return null;
    const cleanTitle = title.toLowerCase().replace(/[^a-z0-9]/g, '');
    if (!cleanTitle) return null;

    for (const list of this._vodCache.values()) {
      if (Array.isArray(list)) {
        const found = list.find(s => {
          const sName = (s.name || '').toLowerCase().replace(/[^a-z0-9]/g, '');
          return sName.includes(cleanTitle) || cleanTitle.includes(sName);
        });
        if (found) return found;
      }
    }

    const seriesCats = ['21', '22', '24', '30', '25', '23', '27', '28'];
    const results = await Promise.allSettled(
      seriesCats.map(cat => this.getSeries(cat))
    );

    for (const res of results) {
      if (res.status === 'fulfilled' && Array.isArray(res.value)) {
        const found = res.value.find(s => {
          const sName = (s.name || '').toLowerCase().replace(/[^a-z0-9]/g, '');
          return sName.includes(cleanTitle) || cleanTitle.includes(sName);
        });
        if (found) return found;
      }
    }
    return null;
  }

  getMovieStreamUrl(streamId, ext = 'mp4') {
    return `${this.baseUrl}/movie/${this.username}/${this.password}/${streamId}.${ext}`;
  }

  getSeriesStreamUrl(streamId, ext = 'mp4') {
    return `${this.baseUrl}/series/${this.username}/${this.password}/${streamId}.${ext}`;
  }
}

export const xtreamVOD = new XtreamVODClient();
