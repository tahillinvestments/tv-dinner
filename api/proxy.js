export const config = {
  runtime: 'edge',
};

const STRIP_REQ_HEADERS = new Set(['host', 'referer', 'origin', 'x-forwarded-for', 'cf-connecting-ip', 'cf-ipcountry', 'cf-ray', 'cf-visitor']);
const IPTV_HOSTS = new Set(['portal5458.com', 'kstv.us']);

function rewriteM3U8(m3uText, baseUrl, proxyOrigin) {
  const base = new URL(baseUrl);
  return m3uText.split('\n').map(line => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) return line;
    try {
      const absolute = new URL(trimmed, base).href;
      return `${proxyOrigin}?url=${encodeURIComponent(absolute)}`;
    } catch {
      return line;
    }
  }).join('\n');
}

export default async function handler(request) {
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
  if (!targetUrlStr) {
    return new Response('Missing url parameter', { status: 400 });
  }

  let targetUrl;
  try {
    targetUrl = new URL(targetUrlStr);
  } catch {
    return new Response('Invalid URL parameter', { status: 400 });
  }

  const newHeaders = new Headers();
  for (const [k, v] of request.headers.entries()) {
    if (!STRIP_REQ_HEADERS.has(k.toLowerCase())) {
      newHeaders.set(k, v);
    }
  }
  newHeaders.set('host', targetUrl.host);
  newHeaders.set('User-Agent', 'VLC/3.0.21 LibVLC/3.0.21');

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
      const proxyOrigin = `${requestUrl.protocol}//${requestUrl.host}${requestUrl.pathname}`;
      const rewritten = rewriteM3U8(text, targetUrl.href, proxyOrigin);
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
}
