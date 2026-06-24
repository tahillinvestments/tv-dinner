// Vyla Stream API Service
const BASE_URL = 'https://missourimonster-vyla.hf.space/api';

/**
 * Polls the Vyla endpoint until the HuggingFace Space wakes up (status 200),
 * then opens an SSE EventSource to stream results.
 *
 * @param {Object} mediaParams - { type, id, season, episode }
 * @param {Object} callbacks - { onMeta, onSource, onDone, onError, onWaking }
 * @returns {{ cancel: Function }} - Call cancel() to abort polling + SSE
 */
export function getStreamSources(
  { type, id, season, episode },
  { onMeta, onSource, onDone, onError, onWaking }
) {
  const queryParams = new URLSearchParams({ id });

  if (type === 'tv') {
    queryParams.append('season', season);
    queryParams.append('episode', episode);
  }

  const endpoint = type === 'tv' ? 'tv' : 'movie';
  const sseUrl = `${BASE_URL}/${endpoint}?${queryParams.toString()}`;

  let cancelled = false;
  let eventSource = null;
  let pollTimer = null;

  async function wake() {
    if (cancelled) return;

    try {
      const res = await fetch(sseUrl, { headers: { Accept: 'text/event-stream' } });

      if (cancelled) return;

      if (res.status === 200 && res.headers.get('content-type')?.includes('text/event-stream')) {
        // Space is fully awake — open EventSource
        connect();
      } else if (res.status === 200) {
        // Space awake but wrong content type on this fetch — try EventSource
        connect();
      } else {
        // Still loading (206 or other) — notify caller and retry
        if (onWaking) onWaking();
        pollTimer = setTimeout(wake, 2000);
      }
    } catch (err) {
      if (cancelled) return;
      if (onWaking) onWaking();
      pollTimer = setTimeout(wake, 3000);
    }
  }

  function connect() {
    if (cancelled) return;
    console.log(`[Vyla] Connecting SSE: ${sseUrl}`);

    eventSource = new EventSource(sseUrl);

    eventSource.addEventListener('meta', (event) => {
      try {
        const data = JSON.parse(event.data);
        if (onMeta) onMeta(data);
      } catch (err) {
        console.error('[Vyla] Failed to parse meta event:', err);
      }
    });

    eventSource.addEventListener('source', (event) => {
      try {
        const data = JSON.parse(event.data);
        if (onSource) onSource(data);
      } catch (err) {
        console.error('[Vyla] Failed to parse source event:', err);
      }
    });

    eventSource.addEventListener('done', () => {
      console.log('[Vyla] Stream search complete.');
      eventSource.close();
      if (onDone) onDone();
    });

    eventSource.addEventListener('error', (err) => {
      console.error('[Vyla] EventSource error:', err);
      eventSource.close();
      if (onError) onError(err);
    });
  }

  // Kick off
  wake();

  return {
    cancel() {
      cancelled = true;
      clearTimeout(pollTimer);
      if (eventSource) {
        eventSource.close();
        eventSource = null;
      }
    }
  };
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
