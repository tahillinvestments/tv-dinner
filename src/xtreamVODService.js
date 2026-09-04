import { fetchFromTMDB } from './tmdb.js';

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
  constructor(options = '') {
    if (typeof options === 'object' && options !== null) {
      this._baseUrl = options.baseUrl || '';
      this._username = options.username || '';
      this._password = options.password || '';
    } else {
      this._baseUrl = typeof options === 'string' ? options : '';
      this._username = '';
      this._password = '';
    }
    this._vodCache = new Map();
  }

  get baseUrl() {
    if (this._baseUrl && typeof this._baseUrl === 'string') return this._baseUrl.trim().replace(/\/+$/, '');
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('xtream_vod_portal') || localStorage.getItem('iptv_portal_url');
      if (saved && saved.trim()) return saved.trim().replace(/\/+$/, '');
    }
    return 'http://vpn.uhdp.top:80';
  }

  get username() {
    if (this._username && typeof this._username === 'string') return this._username.trim();
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('xtream_vod_username') || localStorage.getItem('iptv_username');
      if (saved && saved.trim()) return saved.trim();
    }
    return '954ee56a56';
  }

  get password() {
    if (this._password && typeof this._password === 'string') return this._password.trim();
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('xtream_vod_password') || localStorage.getItem('iptv_password');
      if (saved && saved.trim()) {
        return saved.trim();
      }
    }
    return '2b0dd524f955';
  }

  async fetchApi(action, params = {}) {
    const query = new URLSearchParams({
      username: this.username,
      password: this.password,
      action,
      ...params
    });

    const targetUrl = `${this.baseUrl}/player_api.php?${query.toString()}`;
    
    const isAndroid = typeof window !== 'undefined' && 
      (window.location.host === 'appassets.androidplatform.net' || 
       window.location.protocol === 'file:' || 
       (navigator.userAgent && (navigator.userAgent.includes('TVDinnerMobileApp') || navigator.userAgent.includes('JoyfulIPTVMobileApp'))));

    let proxyBase = '';
    try {
      proxyBase = (localStorage.getItem('external_proxy_url') || '').trim();
    } catch (e) {}
    if (!proxyBase) {
      if (typeof window !== 'undefined' && (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')) {
        proxyBase = '/api/proxy';
      } else {
        proxyBase = 'https://tv-dinner-proxy.tahillinvestments.workers.dev/';
      }
    }

    const buildUrl = (base) => {
      if (base.startsWith('http://') || base.startsWith('https://')) {
        const p = base.endsWith('/') ? base : base + '/';
        return `${p}?url=${encodeURIComponent(targetUrl)}`;
      }
      return `${base}?url=${encodeURIComponent(targetUrl)}`;
    };

    let lastStatus = null;
    try {
      const res = await fetch(buildUrl(proxyBase));
      if (res.ok) return await res.json();
      lastStatus = res.status;
    } catch (e) {
      console.warn(`[Xtream] Primary proxy fetch failed for ${action}:`, e.message);
    }

    // Fallback to secondary proxy endpoint
    const fallbackBase = proxyBase.includes('workers.dev') ? '/api/proxy' : 'https://tv-dinner-proxy.tahillinvestments.workers.dev/';
    try {
      const res = await fetch(buildUrl(fallbackBase));
      if (res.ok) return await res.json();
      lastStatus = res.status;
    } catch (e) {
      console.warn(`[Xtream] Fallback proxy fetch failed for ${action}:`, e.message);
    }

    throw new Error(`Xtream API error (${action}): ${lastStatus || 'unable to fetch data'}`);
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
    try {
      const [movieCats, seriesCats] = await Promise.allSettled([
        this.getMovieCategories(),
        this.getSeriesCategories()
      ]);

      const mCats = movieCats.status === 'fulfilled' && Array.isArray(movieCats.value) ? movieCats.value : [];
      const sCats = seriesCats.status === 'fulfilled' && Array.isArray(seriesCats.value) ? seriesCats.value : [];

      const prioritizedMovieCatIds = mCats
        .filter(c => /\[EN\]|ENGLISH|IMDB|MULTISUB|4K|NEW|ACTION|COMEDY|DOCUMENTARY/i.test(c.category_name || ''))
        .map(c => String(c.category_id))
        .slice(0, 10);
      
      const prioritizedSeriesCatIds = sCats
        .filter(c => /\[EN\]|ENGLISH|MULTISUB|NETFLIX|DISNEY|APPLE|HULU|NEW/i.test(c.category_name || ''))
        .map(c => String(c.category_id))
        .slice(0, 10);

      const fallbackMovieCats = ['633', '1160', '1239', '1243', '759', '1241', '1242', '927', '390', '650', '1257', '393', '1001', '561'];
      const fallbackSeriesCats = ['848', '558', '486', '1154', '1219', '1136', '1146', '1174', '1176', '1137', '1138', '1398', '1433', '1145', '713'];

      const targetMovieCats = Array.from(new Set([...prioritizedMovieCatIds, ...fallbackMovieCats])).slice(0, 12);
      const targetSeriesCats = Array.from(new Set([...prioritizedSeriesCatIds, ...fallbackSeriesCats])).slice(0, 12);

      await Promise.allSettled([
        ...targetMovieCats.map(cat => this.getMovies(cat)),
        ...targetSeriesCats.map(cat => this.getSeries(cat))
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
    const commonCats = ['633', '1160', '1239', '1243', '759', '1241', '1242', '927', '390', '650', '1257', '393', '1001', '561'];
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

    const seriesCats = ['848', '558', '486', '1154', '1219', '1136', '1146', '1174', '1176', '1137', '1138', '1398', '1433', '1145', '713'];
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
    const commonCats = ['633', '1160', '1239', '1243', '759', '1241', '1242', '927', '390', '650', '1257', '393', '581', '649', '1369', '1180', '1054', '1001'];
    if (this._vodCache.size < 2) {
      try {
        const cats = await this.getMovieCategories();
        const enCatIds = (Array.isArray(cats) ? cats : [])
          .filter(c => /\[EN\]|ENGLISH|IMDB|MULTISUB|4K|NEW|ACTION|COMEDY|HORROR/i.test(c.category_name || ''))
          .map(c => String(c.category_id))
          .slice(0, 10);
        const targetCats = Array.from(new Set([...enCatIds, ...commonCats])).slice(0, 12);
        await Promise.allSettled(targetCats.map(cat => this.getMovies(cat)));
      } catch (_) {
        await Promise.allSettled(commonCats.map(cat => this.getMovies(cat)));
      }
    }

    let allMovies = [];
    for (const [k, v] of this._vodCache.entries()) {
      if (k.startsWith('movies_cat_') && Array.isArray(v)) {
        allMovies = allMovies.concat(v);
      }
    }

    if (allMovies.length === 0) {
      try {
        const cat633 = await this.getMovies('633');
        if (Array.isArray(cat633)) allMovies = allMovies.concat(cat633);
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
    const seriesCats = ['848', '558', '486', '1154', '1219', '1136', '1146', '1174', '1176', '1137', '1138', '1398', '1433', '1145', '713'];
    if (this._vodCache.size < 2) {
      try {
        const cats = await this.getSeriesCategories();
        const enCatIds = (Array.isArray(cats) ? cats : [])
          .filter(c => /\[EN\]|ENGLISH|MULTISUB|NETFLIX|DISNEY|APPLE|HULU|NEW|COMEDY/i.test(c.category_name || ''))
          .map(c => String(c.category_id))
          .slice(0, 10);
        const targetCats = Array.from(new Set([...enCatIds, ...seriesCats])).slice(0, 12);
        await Promise.allSettled(targetCats.map(cat => this.getSeries(cat)));
      } catch (_) {
        await Promise.allSettled(seriesCats.map(cat => this.getSeries(cat)));
      }
    }

    let allSeries = [];
    for (const [k, v] of this._vodCache.entries()) {
      if (k.startsWith('series_cat_') && Array.isArray(v)) {
        allSeries = allSeries.concat(v);
      }
    }

    if (allSeries.length === 0) {
      try {
        const cat848 = await this.getSeries('848');
        if (Array.isArray(cat848)) allSeries = allSeries.concat(cat848);
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
    const cleanExt = (ext || 'mp4').toString().trim().split('?')[0].split('#')[0].replace(/^\.+/, '').trim().toLowerCase() || 'mp4';
    const raw = `${this.baseUrl}/movie/${this.username}/${this.password}/${streamId}.${cleanExt}`;
    let proxyBase = '';
    try {
      proxyBase = (localStorage.getItem('external_proxy_url') || '').trim();
    } catch (e) {}
    if (!proxyBase) {
      if (typeof window !== 'undefined' && (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')) {
        proxyBase = '/api/proxy';
      } else {
        proxyBase = 'https://tv-dinner-proxy.tahillinvestments.workers.dev/';
      }
    }
    const p = proxyBase.startsWith('http://') || proxyBase.startsWith('https://')
      ? (proxyBase.endsWith('/') ? proxyBase : proxyBase + '/')
      : (proxyBase.startsWith('/') ? proxyBase : '/' + proxyBase);
    return `${p}?url=${encodeURIComponent(raw)}`;
  }

  getSeriesStreamUrl(streamId, ext = 'mp4') {
    const cleanExt = (ext || 'mp4').toString().trim().split('?')[0].split('#')[0].replace(/^\.+/, '').trim().toLowerCase() || 'mp4';
    const raw = `${this.baseUrl}/series/${this.username}/${this.password}/${streamId}.${cleanExt}`;
    let proxyBase = '';
    try {
      proxyBase = (localStorage.getItem('external_proxy_url') || '').trim();
    } catch (e) {}
    if (!proxyBase) {
      if (typeof window !== 'undefined' && (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1')) {
        proxyBase = '/api/proxy';
      } else {
        proxyBase = 'https://tv-dinner-proxy.tahillinvestments.workers.dev/';
      }
    }
    const p = proxyBase.startsWith('http://') || proxyBase.startsWith('https://')
      ? (proxyBase.endsWith('/') ? proxyBase : proxyBase + '/')
      : (proxyBase.startsWith('/') ? proxyBase : '/' + proxyBase);
    return `${p}?url=${encodeURIComponent(raw)}`;
  }
}

export const xtreamVOD = new XtreamVODClient();

