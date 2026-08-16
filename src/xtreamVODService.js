import { fetchFromTMDB } from './tmdb';

// Safe image proxy helper to prevent mixed content blocks
export function getSafeImageUrl(url) {
  if (!url || typeof url !== 'string' || !url.trim()) return '';
  const trimmed = url.trim();
  if (trimmed.startsWith('//')) return `https:${trimmed}`;
  if (trimmed.startsWith('https://') || trimmed.startsWith('data:')) return trimmed;
  if (trimmed.startsWith('http://')) {
    if (typeof window !== 'undefined' && window.location.protocol === 'https:') {
      return `/api/proxy?url=${encodeURIComponent(trimmed)}`;
    }
    return trimmed;
  }
  return trimmed;
}

export function calculateVODScore(item) {
  if (!item) return 0;
  let rating = 0;
  if (item.rating && !isNaN(Number(item.rating))) {
    rating = Number(item.rating);
  } else if (item.rating_5based && !isNaN(Number(item.rating_5based))) {
    rating = Number(item.rating_5based) * 2;
  } else if (item.vote_average && !isNaN(Number(item.vote_average))) {
    rating = Number(item.vote_average);
  }
  
  let added = 0;
  if (item.added && !isNaN(Number(item.added))) {
    added = Number(item.added);
  } else if (item.last_modified && !isNaN(Number(item.last_modified))) {
    added = Number(item.last_modified);
  }

  let popularity = 0;
  if (item.popularity && !isNaN(Number(item.popularity))) {
    popularity = Number(item.popularity);
  }

  const hasArt = (item.stream_icon || item.cover || item.poster_path || item.backdrop_path) ? 5 : 0;
  const recencyScore = added > 0 ? (added / 100000000) : 0;
  
  return (rating * 15) + (popularity * 2) + recencyScore + hasArt;
}

export function sortVODByPopularity(items) {
  if (!Array.isArray(items)) return [];
  return [...items].sort((a, b) => calculateVODScore(b) - calculateVODScore(a));
}

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
      if (saved && saved.trim() && saved.includes('@')) return saved.trim();
    }
    return 'gj3526@gmail.com';
  }

  get password() {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('xtream_vod_password');
      if (saved && saved.trim() && saved !== 'Louisville' && saved !== 'ADFREE2026' && saved !== 'TV4LIFE' && saved !== 'REMOTE6202' && saved !== '2611596317' && saved !== '4WM9WVsjG' && saved !== '5DwU7wTuA' && saved !== 'JaKXrfMP7') {
        return saved.trim();
      }
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
    
    // In Android app, route directly; in web browsers, route via Cloudflare Worker proxy to prevent HTTPS mixed-content blocks
    const isAndroid = typeof window !== 'undefined' && 
      (window.location.host === 'appassets.androidplatform.net' || 
       window.location.protocol === 'file:' || 
       (navigator.userAgent && navigator.userAgent.includes('JoyfulIPTVMobileApp')));

    let proxyBase = '';
    try {
      proxyBase = (localStorage.getItem('external_proxy_url') || '').trim();
    } catch (e) {}
    if (!proxyBase) {
      proxyBase = '/api/proxy';
    }

    let url;
    if (isAndroid) {
      url = targetUrl;
    } else if (proxyBase.startsWith('http://') || proxyBase.startsWith('https://')) {
      const p = proxyBase.endsWith('/') ? proxyBase : proxyBase + '/';
      url = `${p}?url=${encodeURIComponent(targetUrl)}`;
    } else {
      url = `${proxyBase}?url=${encodeURIComponent(targetUrl)}`;
    }

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
    const commonMovieCats = ['1', '2', '4', '17', '11', '3', '5', '8', '7', '10', '18'];
    const commonSeriesCats = ['21', '22', '24', '30', '25', '23', '27', '28'];
    try {
      await Promise.allSettled([
        ...commonMovieCats.map(cat => this.getMovies(cat)),
        ...commonSeriesCats.map(cat => this.getSeries(cat))
      ]);
    } catch (e) {}
  }

  async findMovieByTitle(title) {
    if (!title) return null;
    const cleanTitle = title.toLowerCase().replace(/[^a-z0-9]/g, '');
    if (!cleanTitle) return null;

    // 1. Check all already-cached categories in memory
    for (const [k, list] of this._vodCache.entries()) {
      if (k.startsWith('movies_cat_') && Array.isArray(list)) {
        const found = list.find(m => {
          const mName = (m.name || m.title || '').toLowerCase().replace(/[^a-z0-9]/g, '');
          return mName.includes(cleanTitle) || cleanTitle.includes(mName);
        });
        if (found) return found;
      }
    }

    // 2. Query common categories in parallel
    const commonCats = ['1', '2', '4', '17', '11', '3', '5', '8', '7', '10', '18'];
    const results = await Promise.allSettled(
      commonCats.map(cat => this.getMovies(cat))
    );

    for (const res of results) {
      if (res.status === 'fulfilled' && Array.isArray(res.value)) {
        const found = res.value.find(m => {
          const mName = (m.name || m.title || '').toLowerCase().replace(/[^a-z0-9]/g, '');
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

    for (const [k, list] of this._vodCache.entries()) {
      if (k.startsWith('series_cat_') && Array.isArray(list)) {
        const found = list.find(s => {
          const sName = (s.name || s.title || '').toLowerCase().replace(/[^a-z0-9]/g, '');
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
          const sName = (s.name || s.title || '').toLowerCase().replace(/[^a-z0-9]/g, '');
          return sName.includes(cleanTitle) || cleanTitle.includes(sName);
        });
        if (found) return found;
      }
    }
    return null;
  }

  /**
   * Searches exclusively within the Xtream VOD Movie Catalogue
   */
  async searchMovies(query) {
    if (!query || !query.trim()) return [];
    const trimmed = query.trim().toLowerCase();
    const cleanTokens = trimmed.split(/\s+/).filter(Boolean);

    // Ensure common movie categories are populated
    const commonCats = ['1', '2', '4', '17', '11', '3', '5', '8', '7', '10', '18'];
    if (this._vodCache.size < 2) {
      await Promise.allSettled(commonCats.map(cat => this.getMovies(cat)));
    }

    let allMovies = [];
    for (const [k, v] of this._vodCache.entries()) {
      if (k.startsWith('movies_cat_') && Array.isArray(v)) {
        allMovies = allMovies.concat(v);
      }
    }

    if (allMovies.length === 0) {
      try {
        const cat1 = await this.getMovies('1');
        if (Array.isArray(cat1)) allMovies = allMovies.concat(cat1);
      } catch (e) {}
    }

    // Deduplicate by stream_id
    const seen = new Set();
    const uniqueMovies = [];
    for (const m of allMovies) {
      const sId = m.stream_id || m.id;
      if (sId && !seen.has(sId)) {
        seen.add(sId);
        uniqueMovies.push(m);
      }
    }

    const matches = [];
    for (const movie of uniqueMovies) {
      const name = (movie.name || movie.title || '').toLowerCase();
      const genre = (movie.genre || movie.category_name || '').toLowerCase();
      const director = (movie.director || '').toLowerCase();
      const cast = (movie.cast || '').toLowerCase();
      const combined = `${name} ${genre} ${director} ${cast}`;

      let matchScore = 0;
      if (name === trimmed) {
        matchScore += 100;
      } else if (name.startsWith(trimmed)) {
        matchScore += 60;
      } else if (name.includes(trimmed)) {
        matchScore += 40;
      } else {
        const tokenMatches = cleanTokens.filter(t => combined.includes(t));
        if (tokenMatches.length === cleanTokens.length) {
          matchScore += 30;
        } else if (tokenMatches.length > 0) {
          matchScore += (tokenMatches.length / cleanTokens.length) * 20;
        }
      }

      if (matchScore > 0) {
        const popScore = calculateVODScore(movie);
        const posterUrl = movie.stream_icon || movie.cover || movie.poster_path;
        matches.push({
          item: {
            ...movie,
            media_type: 'movie',
            id: movie.stream_id || movie.id,
            stream_id: movie.stream_id || movie.id,
            title: movie.name || movie.title,
            container_extension: movie.container_extension || 'mp4',
            stream_icon: posterUrl,
            poster_path: posterUrl
          },
          score: matchScore * 10 + popScore
        });
      }
    }

    matches.sort((a, b) => b.score - a.score);
    return matches.slice(0, 50).map(m => m.item);
  }

  /**
   * Searches exclusively within the Xtream VOD TV Series Catalogue
   */
  async searchSeries(query) {
    if (!query || !query.trim()) return [];
    const trimmed = query.trim().toLowerCase();
    const cleanTokens = trimmed.split(/\s+/).filter(Boolean);

    const seriesCats = ['21', '22', '24', '30', '25', '23', '27', '28'];
    if (this._vodCache.size < 2) {
      await Promise.allSettled(seriesCats.map(cat => this.getSeries(cat)));
    }

    let allSeries = [];
    for (const [k, v] of this._vodCache.entries()) {
      if (k.startsWith('series_cat_') && Array.isArray(v)) {
        allSeries = allSeries.concat(v);
      }
    }

    if (allSeries.length === 0) {
      try {
        const cat21 = await this.getSeries('21');
        if (Array.isArray(cat21)) allSeries = allSeries.concat(cat21);
      } catch (e) {}
    }

    // Deduplicate by series_id
    const seen = new Set();
    const uniqueSeries = [];
    for (const s of allSeries) {
      const sId = s.series_id || s.id;
      if (sId && !seen.has(sId)) {
        seen.add(sId);
        uniqueSeries.push(s);
      }
    }

    const matches = [];
    for (const s of uniqueSeries) {
      const name = (s.name || s.title || '').toLowerCase();
      const genre = (s.genre || s.category_name || '').toLowerCase();
      const plot = (s.plot || s.overview || '').toLowerCase();
      const cast = (s.cast || '').toLowerCase();
      const director = (s.director || '').toLowerCase();
      const combined = `${name} ${genre} ${plot} ${cast} ${director}`;

      let matchScore = 0;
      if (name === trimmed) {
        matchScore += 100;
      } else if (name.startsWith(trimmed)) {
        matchScore += 60;
      } else if (name.includes(trimmed)) {
        matchScore += 40;
      } else {
        const tokenMatches = cleanTokens.filter(t => combined.includes(t));
        if (tokenMatches.length === cleanTokens.length) {
          matchScore += 30;
        } else if (tokenMatches.length > 0) {
          matchScore += (tokenMatches.length / cleanTokens.length) * 20;
        }
      }

      if (matchScore > 0) {
        const popScore = calculateVODScore(s);
        const posterUrl = s.cover || s.stream_icon || s.poster_path;
        matches.push({
          item: {
            ...s,
            media_type: 'tv',
            id: s.series_id || s.id,
            series_id: s.series_id || s.id,
            name: s.name || s.title,
            title: s.name || s.title,
            cover: posterUrl,
            poster_path: posterUrl
          },
          score: matchScore * 10 + popScore
        });
      }
    }

    matches.sort((a, b) => b.score - a.score);
    return matches.slice(0, 50).map(m => m.item);
  }

  /**
   * Unified VOD search confined strictly to Xtream catalogue
   */
  async searchVOD(query, type = 'all') {
    if (type === 'movie') {
      return this.searchMovies(query);
    }
    if (type === 'tv') {
      return this.searchSeries(query);
    }
    const [movies, series] = await Promise.allSettled([
      this.searchMovies(query),
      this.searchSeries(query)
    ]);
    const mList = movies.status === 'fulfilled' ? movies.value : [];
    const sList = series.status === 'fulfilled' ? series.value : [];
    return [...mList, ...sList].sort((a, b) => calculateVODScore(b) - calculateVODScore(a));
  }

  getMovieStreamUrl(streamId, ext = 'mp4') {
    const raw = `${this.baseUrl}/movie/${this.username}/${this.password}/${streamId}.${ext}`;
    const isAndroid = typeof window !== 'undefined' && 
      (window.location.host === 'appassets.androidplatform.net' || 
       window.location.protocol === 'file:' || 
       (navigator.userAgent && navigator.userAgent.includes('JoyfulIPTVMobileApp')));
    if (isAndroid) return raw;
    let proxyBase = (localStorage.getItem('external_proxy_url') || '').trim() || '/api/proxy';
    const p = proxyBase.startsWith('http://') || proxyBase.startsWith('https://')
      ? (proxyBase.endsWith('/') ? proxyBase : proxyBase + '/')
      : (proxyBase.startsWith('/') ? proxyBase : '/' + proxyBase);
    return `${p}?url=${encodeURIComponent(raw)}`;
  }

  getSeriesStreamUrl(streamId, ext = 'mp4') {
    const raw = `${this.baseUrl}/series/${this.username}/${this.password}/${streamId}.${ext}`;
    const isAndroid = typeof window !== 'undefined' && 
      (window.location.host === 'appassets.androidplatform.net' || 
       window.location.protocol === 'file:' || 
       (navigator.userAgent && navigator.userAgent.includes('JoyfulIPTVMobileApp')));
    if (isAndroid) return raw;
    let proxyBase = (localStorage.getItem('external_proxy_url') || '').trim() || '/api/proxy';
    const p = proxyBase.startsWith('http://') || proxyBase.startsWith('https://')
      ? (proxyBase.endsWith('/') ? proxyBase : proxyBase + '/')
      : (proxyBase.startsWith('/') ? proxyBase : '/' + proxyBase);
    return `${p}?url=${encodeURIComponent(raw)}`;
  }
}

export const xtreamVOD = new XtreamVODClient();

