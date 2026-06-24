// Vyla Stream API Service
const BASE_URL = 'https://missourimonster-vyla.hf.space/api';

/**
 * Connects to Vyla stream-api Server-Sent Events (SSE) to fetch verified streaming links in real-time.
 * 
 * @param {string} type - 'movie' or 'tv'
 * @param {Object} params - { id, season, episode }
 * @param {Function} onMeta - Callback for 'meta' event (subtitles, metadata)
 * @param {Function} onSource - Callback for 'source' event (stream sources resolved)
 * @param {Function} onDone - Callback when done/connection closes
 * @param {Function} onError - Callback for errors
 * @returns {EventSource} - The active EventSource object (which can be closed manually to cancel)
 */
export function getStreamSources({ type, id, season, episode }, { onMeta, onSource, onDone, onError }) {
  const queryParams = new URLSearchParams({ id });
  
  if (type === 'tv') {
    queryParams.append('season', season);
    queryParams.append('episode', episode);
  }
  
  const sseUrl = `${BASE_URL}/${type === 'tv' ? 'tv' : 'movie'}?${queryParams.toString()}`;
  console.log(`Connecting to Vyla Stream SSE: ${sseUrl}`);
  
  const eventSource = new EventSource(sseUrl);
  
  eventSource.addEventListener('meta', (event) => {
    try {
      const data = JSON.parse(event.data);
      if (onMeta) onMeta(data);
    } catch (err) {
      console.error('Failed to parse meta SSE payload:', err);
    }
  });
  
  eventSource.addEventListener('source', (event) => {
    try {
      const data = JSON.parse(event.data);
      if (onSource) onSource(data);
    } catch (err) {
      console.error('Failed to parse source SSE payload:', err);
    }
  });
  
  eventSource.addEventListener('done', () => {
    console.log('Vyla Stream API completed search.');
    eventSource.close();
    if (onDone) onDone();
  });
  
  eventSource.addEventListener('error', (err) => {
    console.error('Vyla Stream API EventSource error:', err);
    eventSource.close();
    if (onError) onError(err);
  });
  
  return eventSource;
}

/**
 * Checks the status/latency of all stream providers.
 * @returns {Promise<Object>}
 */
export async function getHealthStatus() {
  const response = await fetch(`${BASE_URL}/health`);
  if (!response.ok) {
    throw new Error('Failed to fetch provider health status');
  }
  return response.json();
}
