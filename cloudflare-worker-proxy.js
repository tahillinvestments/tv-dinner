/**
 * TV Dinner IPTV Proxy — Cloudflare Worker
 *
 * Deploy at: https://dash.cloudflare.com → Workers → Create Worker → Paste this code
 * Free tier: 100,000 requests/day, unlimited bandwidth (no fast-transfer billing)
 *
 * After deploying, copy your worker URL (e.g. https://tv-dinner-proxy.YOUR-NAME.workers.dev)
 * and paste it into Settings → External Proxy URL in the TV Dinner app.
 */

const STRIP_RES_HEADERS = new Set([
  'content-encoding', 'transfer-encoding', 'connection', 'keep-alive',
  'set-cookie', 'strict-transport-security', 'x-frame-options',
]);

/** Rewrite M3U8 segment URLs to go back through this proxy */
function rewriteM3U8(text, finalUrl, workerOrigin) {
  const base = new URL(finalUrl);
  return text.split(/\r?\n|\r/).map(line => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) return line;
    try {
      const abs = new URL(trimmed, base);
      if (base.search && !abs.search) abs.search = base.search;
      return `${workerOrigin}?url=${encodeURIComponent(abs.href)}`;
    } catch {
      return line;
    }
  }).join('\n');
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);

    // CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
          'Access-Control-Allow-Headers': '*',
        },
      });
    }

    // Health check
    if ((url.pathname === '/' || url.pathname === '/health') && !url.searchParams.get('url')) {
      return new Response('TV Dinner Cloudflare Proxy Running', {
        status: 200,
        headers: { 'Access-Control-Allow-Origin': '*' },
      });
    }

    const targetUrlStr = url.searchParams.get('url');
    if (!targetUrlStr) {
      return new Response('Missing url parameter', {
        status: 400,
        headers: { 'Access-Control-Allow-Origin': '*' },
      });
    }

    let targetUrl;
    try {
      targetUrl = new URL(targetUrlStr);
    } catch {
      return new Response('Invalid URL parameter', {
        status: 400,
        headers: { 'Access-Control-Allow-Origin': '*' },
      });
    }

    const outgoingHeaders = {
      'user-agent': 'VLC/3.0.21 LibVLC/3.0.21',
      'accept': '*/*',
    };
    const rangeHeader = request.headers.get('range');
    if (rangeHeader) outgoingHeaders['range'] = rangeHeader;

    try {
      const response = await fetch(targetUrl.href, {
        method: request.method === 'POST' ? 'POST' : 'GET',
        headers: outgoingHeaders,
        redirect: 'follow',
      });

      const finalUrl = response.url || targetUrl.href;
      const contentType = (response.headers.get('content-type') || '').toLowerCase();
      const isM3U8 =
        contentType.includes('mpegurl') ||
        targetUrl.pathname.endsWith('.m3u8') ||
        targetUrl.pathname.endsWith('.m3u');

      // Build clean response headers
      const resHeaders = new Headers();
      resHeaders.set('Access-Control-Allow-Origin', '*');
      resHeaders.set('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
      resHeaders.set('Access-Control-Allow-Headers', '*');
      for (const [k, v] of response.headers.entries()) {
        if (!STRIP_RES_HEADERS.has(k.toLowerCase())) {
          resHeaders.set(k, v);
        }
      }

      if (isM3U8) {
        const text = await response.text();
        const workerOrigin = `${url.origin}${url.pathname}`;
        const rewritten = rewriteM3U8(text, finalUrl, workerOrigin);
        resHeaders.set('content-type', 'application/vnd.apple.mpegurl');
        resHeaders.delete('content-length'); // length changes after rewrite
        return new Response(rewritten, { status: response.status, headers: resHeaders });
      }

      // Stream binary content directly (TS segments, JSON)
      return new Response(response.body, { status: response.status, headers: resHeaders });

    } catch (err) {
      return new Response('Proxy Error: ' + err.message, {
        status: 502,
        headers: { 'Access-Control-Allow-Origin': '*' },
      });
    }
  },
};
