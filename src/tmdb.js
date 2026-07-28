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
  const data = await fetchFromTMDB('/search/multi', {
    query: query.trim(),
    page,
    include_adult: 'false'
  });
  
  // Filter only movies and tv shows, and sort by popularity
  data.results = (data.results || [])
    .filter(item => item.media_type === 'movie' || item.media_type === 'tv')
    .sort((a, b) => (b.popularity || 0) - (a.popularity || 0));
    
  return data;
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
