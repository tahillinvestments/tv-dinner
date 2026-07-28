// TMDB Metadata Service
const BASE_URL = 'https://api.themoviedb.org/3';
const DEFAULT_KEY = '04c35731a5ee918f014970082a0088b1'; // Public/fallback key

function getApiKey() {
  return localStorage.getItem('tmdb_api_key') || DEFAULT_KEY;
}

export async function fetchFromTMDB(endpoint, params = {}) {
  const apiKey = getApiKey();
  const queryParams = new URLSearchParams({
    api_key: apiKey,
    ...params
  });
  
  const response = await fetch(`${BASE_URL}${endpoint}?${queryParams.toString()}`);
  if (!response.ok) {
    throw new Error(`TMDB API Error: ${response.status} ${response.statusText}`);
  }
  return response.json();
}

/**
 * Searches movies and TV shows on TMDB
 */
export async function searchMulti(query, page = 1) {
  if (!query || query.trim() === '') return { results: [] };
  const trimmed = query.trim();

  // If query is an IMDb ID (e.g. tt1375666 or tt0111161)
  if (/^tt\d+$/i.test(trimmed)) {
    try {
      const findData = await fetchFromTMDB(`/find/${trimmed}`, { external_source: 'imdb_id' });
      const results = [];
      (findData.movie_results || []).forEach(m => { m.media_type = 'movie'; results.push(m); });
      (findData.tv_results || []).forEach(t => { t.media_type = 'tv'; results.push(t); });
      if (results.length > 0) return { results };
    } catch (e) {
      console.warn("IMDb ID find failed:", e);
    }
  }

  // If query is a pure numeric ID (e.g. 1081003 or 27205)
  if (/^\d+$/.test(trimmed)) {
    const results = [];
    try {
      const mDetails = await fetchFromTMDB(`/movie/${trimmed}`);
      if (mDetails && mDetails.id) {
        mDetails.media_type = 'movie';
        results.push(mDetails);
      }
    } catch (e) {}

    try {
      const tDetails = await fetchFromTMDB(`/tv/${trimmed}`);
      if (tDetails && tDetails.id) {
        tDetails.media_type = 'tv';
        results.push(tDetails);
      }
    } catch (e) {}

    if (results.length > 0) return { results };
  }

  // Primary multi search
  let results = [];
  try {
    const data = await fetchFromTMDB('/search/multi', {
      query: trimmed,
      page,
      include_adult: 'false'
    });
    results = (data.results || [])
      .filter(item => item.media_type === 'movie' || item.media_type === 'tv');
  } catch (e) {
    console.warn("TMDB multi search error:", e);
  }

  // Fallback: search movie and tv directly if multi-search returns nothing
  if (results.length === 0) {
    try {
      const [movieData, tvData] = await Promise.allSettled([
        fetchFromTMDB('/search/movie', { query: trimmed, page }),
        fetchFromTMDB('/search/tv', { query: trimmed, page })
      ]);
      if (movieData.status === 'fulfilled' && movieData.value.results) {
        movieData.value.results.forEach(m => { m.media_type = 'movie'; results.push(m); });
      }
      if (tvData.status === 'fulfilled' && tvData.value.results) {
        tvData.value.results.forEach(t => { t.media_type = 'tv'; results.push(t); });
      }
    } catch (e) {
      console.warn("Fallback direct search error:", e);
    }
  }

  results.sort((a, b) => (b.popularity || 0) - (a.popularity || 0));
  return { results };
}

/**
 * Fetches trending movies and TV shows
 */
export async function getTrending(page = 1) {
  const data = await fetchFromTMDB('/trending/all/day', { page });
  data.results = (data.results || [])
    .filter(item => item.media_type === 'movie' || item.media_type === 'tv');
  return data;
}

export async function getTrendingMovies(page = 1) {
  const data = await fetchFromTMDB('/trending/movie/day', { page });
  (data.results || []).forEach(item => { item.media_type = 'movie'; });
  return data;
}

export async function getTrendingTV(page = 1) {
  const data = await fetchFromTMDB('/trending/tv/day', { page });
  (data.results || []).forEach(item => { item.media_type = 'tv'; });
  return data;
}

/**
 * Fetches top rated movies
 */
export async function getTopRated(page = 1) {
  const data = await fetchFromTMDB('/movie/top_rated', { page });
  (data.results || []).forEach(item => { item.media_type = 'movie'; });
  return data;
}

/**
 * Fetches top rated TV shows
 */
export async function getTopRatedTV(page = 1) {
  const data = await fetchFromTMDB('/tv/top_rated', { page });
  (data.results || []).forEach(item => { item.media_type = 'tv'; });
  return data;
}

/**
 * Fetches movies/TV by genre ID
 */
export async function getByGenre(genreId, mediaType = 'movie', page = 1) {
  const endpoint = mediaType === 'tv' ? '/discover/tv' : '/discover/movie';
  const data = await fetchFromTMDB(endpoint, {
    with_genres: genreId,
    sort_by: 'popularity.desc',
    page
  });
  (data.results || []).forEach(item => { item.media_type = mediaType; });
  return data;
}

/**
 * Fetches details for a specific movie
 */
export async function getMovieDetails(id) {
  return fetchFromTMDB(`/movie/${id}`);
}

/**
 * Fetches details for a specific TV show
 */
export async function getTVShowDetails(id) {
  return fetchFromTMDB(`/tv/${id}`);
}

/**
 * Fetches details for a specific TV season
 */
export async function getTVSeasonDetails(tvId, seasonNumber) {
  return fetchFromTMDB(`/tv/${tvId}/season/${seasonNumber}`);
}

/**
 * Gets TMDB Image URL
 */
export function getTMDBImageUrl(path, size = 'w342') {
  if (!path) return '';
  return `https://image.tmdb.org/t/p/${size}${path}`;
}
