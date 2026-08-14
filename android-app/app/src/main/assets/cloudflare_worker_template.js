/**
 * Cloudflare Worker Stream & CORS Proxy
 * 
 * 1. Log into dash.cloudflare.com -> Workers & Pages -> Create Application -> Create Worker
 * 2. Paste this code into the Cloudflare Worker editor and click "Save and Deploy".
 * 3. Copy your Worker URL (e.g., https://my-iptv-proxy.user.workers.dev)
 * 4. In the app Settings, set Proxy Mode to "Cloudflare Worker / Custom Proxy"
 *    and paste your Worker URL into the "Custom Proxy URL" field!
 * 
 * Benefits: 100,000 requests/day free, ZERO bandwidth/egress costs!
 */

const STRIP_REQ_HEADERS = new Set(['host', 'referer', 'origin', 'x-forwarded-for', 'cf-connecting-ip', 'cf-ipcountry', 'cf-ray', 'cf-visitor']);
const IPTV_HOSTS = new Set(['portal5458.com', 'kstv.us']);

function rewriteM3U8(m3uText, baseUrl, workerUrl, proxySegments) {
  const base = new URL(baseUrl);
  const isIptvOrHttp = IPTV_HOSTS.has(base.hostname) || base.hostname.includes('portal5458') || base.protocol === 'http:' || base.pathname.includes('/live/');

  return m3uText.split('\n').map(line => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) return line;
    try {
      const absolute = new URL(trimmed, base).href;
      const lower = absolute.toLowerCase();
      const isNestedPlaylist = lower.includes('.m3u8') || lower.includes('.m3u') || lower.includes('player_api.php');
      
      if (proxySegments || isNestedPlaylist || isIptvOrHttp) {
        return `${workerUrl}?url=${encodeURIComponent(absolute)}`;
      }
      return absolute;
    } catch {
      return line;
    }
  }).join('\n');
}

export default {
  async fetch(request, env, ctx) {
    const requestUrl = new URL(request.url);

    // CORS Preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, OPTIONS, PUT, PATCH, DELETE',
          'Access-Control-Allow-Headers': '*',
        },
      });
    }

    const targetUrlStr = requestUrl.searchParams.get('url');
    const proxySegments = requestUrl.searchParams.get('proxySegments') === '1' || requestUrl.searchParams.get('proxySegments') === 'true';

    if (!targetUrlStr) {
      return new Response('Missing url parameter', { status: 400 });
    }

    let targetUrl;
    try {
      targetUrl = new URL(targetUrlStr);
    } catch {
      return new Response('Invalid URL parameter', { status: 400 });
    }

    // Copy incoming headers except stripped ones
    const newHeaders = new Headers();
    for (const [k, v] of request.headers.entries()) {
      if (!STRIP_REQ_HEADERS.has(k.toLowerCase())) {
        newHeaders.set(k, v);
      }
    }
    newHeaders.set('host', targetUrl.host);

    const isIptvHost = IPTV_HOSTS.has(targetUrl.hostname) || targetUrl.hostname.includes('portal5458') || targetUrl.pathname.includes('player_api') || targetUrl.pathname.includes('.m3u') || targetUrl.pathname.includes('/live/');

    if (isIptvHost) {
      newHeaders.set('User-Agent', 'VLC/3.0.21 LibVLC/3.0.21');
    }

    try {
      const response = await fetch(targetUrl.href, {
        method: request.method,
        headers: newHeaders,
        redirect: 'follow',
      });

      const contentType = (response.headers.get('content-type') || '').toLowerCase();
      const isM3U8 = contentType.includes('mpegurl') || targetUrl.pathname.endsWith('.m3u8') || targetUrl.pathname.endsWith('.m3u');

      const outHeaders = new Headers(response.headers);
      outHeaders.set('Access-Control-Allow-Origin', '*');
      outHeaders.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, PUT, PATCH, DELETE');
      outHeaders.set('Access-Control-Allow-Headers', '*');

      if (isM3U8) {
        const text = await response.text();
        const workerOrigin = `${requestUrl.protocol}//${requestUrl.host}${requestUrl.pathname}`;
        const rewritten = rewriteM3U8(text, targetUrl.href, workerOrigin, proxySegments);
        outHeaders.set('Content-Type', 'application/vnd.apple.mpegurl');
        return new Response(rewritten, {
          status: response.status,
          headers: outHeaders,
        });
      }

      return new Response(response.body, {
        status: response.status,
        headers: outHeaders,
      });
    } catch (err) {
      return new Response('Proxy Error: ' + err.message, {
        status: 502,
        headers: { 'Access-Control-Allow-Origin': '*' },
      });
    }
  },
};
