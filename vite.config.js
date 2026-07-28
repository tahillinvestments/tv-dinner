import { defineConfig } from 'vite';
import http from 'http';
import https from 'https';
import { URL } from 'url';

// Headers to strip from incoming proxy requests
const STRIP_REQ_HEADERS = new Set(['host', 'referer', 'origin', 'x-forwarded-for', 'x-real-ip']);

// Known IPTV portal hosts that need the VLC user-agent spoof
const IPTV_HOSTS = new Set(['portal5458.com', 'kstv.us']);

/**
 * Rewrite an HLS playlist so all segment and sub-playlist URLs are
 * proxied back through our local /api/proxy endpoint.
 * @param {string} m3uText  - raw m3u8 content
 * @param {string} baseUrl  - the absolute URL the m3u8 was fetched from (used to resolve relative URIs)
 * @returns {string}        - rewritten m3u8 content
 */
function rewriteM3U8(m3uText, baseUrl) {
  const base = new URL(baseUrl);
  return m3uText.split('\n').map(line => {
    const trimmed = line.trim();
    // Skip comment / tag lines
    if (!trimmed || trimmed.startsWith('#')) return line;
    try {
      // Resolve relative or absolute segment/playlist URIs
      const absolute = new URL(trimmed, base).href;
      return `/api/proxy?url=${encodeURIComponent(absolute)}`;
    } catch {
      return line; // leave malformed lines as-is
    }
  }).join('\n');
}

function proxyRequest(req, res, targetUrlStr) {
  let targetUrl;
  try {
    targetUrl = new URL(targetUrlStr);
  } catch {
    res.statusCode = 400;
    res.end('Invalid URL parameter');
    return;
  }

  const isIptvHost = IPTV_HOSTS.has(targetUrl.hostname);
  const transport = targetUrl.protocol === 'https:' ? https : http;

  // Build clean outbound headers (strip browser-specific ones)
  const outHeaders = {};
  for (const [k, v] of Object.entries(req.headers)) {
    if (!STRIP_REQ_HEADERS.has(k.toLowerCase())) {
      outHeaders[k] = v;
    }
  }
  outHeaders['host'] = targetUrl.host;

  // Spoof VLC user-agent for IPTV portal requests
  if (isIptvHost) {
    for (const key of Object.keys(outHeaders)) {
      if (key.toLowerCase() === 'user-agent') delete outHeaders[key];
    }
    outHeaders['user-agent'] = 'VLC/3.0.21 LibVLC/3.0.21';
    // Disable keep-alive to prevent connection reuse/token issues
    outHeaders['connection'] = 'close';
  }

  function doRequest(url, redirectsLeft, originalUrl) {
    const parsed = new URL(url);
    const t = parsed.protocol === 'https:' ? https : http;

    const isOriginalIptvHost = IPTV_HOSTS.has(new URL(originalUrl).hostname);

    // When following redirects to CDN, keep using VLC UA since CDN segments
    // are authorized by token in the URL (no UA check on CDN usually)
    const reqHeaders = { ...outHeaders, host: parsed.host };

    const options = {
      hostname: parsed.hostname,
      port: parsed.port || (parsed.protocol === 'https:' ? 443 : 80),
      path: parsed.pathname + parsed.search,
      method: req.method,
      headers: reqHeaders,
    };

    const proxyReq = t.request(options, (proxyRes) => {
      console.log(`[Proxy] ${req.method} ${url} -> ${proxyRes.statusCode}`);
      // Follow redirects
      if ([301, 302, 303, 307, 308].includes(proxyRes.statusCode) && proxyRes.headers.location && redirectsLeft > 0) {
        proxyRes.resume(); // drain body
        let redirectUrl = proxyRes.headers.location;
        if (!redirectUrl.startsWith('http')) {
          redirectUrl = `${parsed.protocol}//${parsed.host}${redirectUrl}`;
        }
        console.log(`[Proxy] Redirect -> ${redirectUrl.slice(0, 80)}`);
        doRequest(redirectUrl, redirectsLeft - 1, originalUrl);
        return;
      }

      const contentType = (proxyRes.headers['content-type'] || '').toLowerCase();
      const isM3U8 = contentType.includes('mpegurl') || url.includes('.m3u8') || originalUrl.includes('.m3u8');

      // Build response headers with CORS overrides
      const resHeaders = { ...proxyRes.headers };
      resHeaders['Access-Control-Allow-Origin'] = '*';
      resHeaders['Access-Control-Allow-Methods'] = 'GET, POST, OPTIONS, PUT, PATCH, DELETE';
      resHeaders['Access-Control-Allow-Headers'] = '*';
      // Remove hop-by-hop headers
      delete resHeaders['connection'];
      delete resHeaders['transfer-encoding'];

      if (isM3U8) {
        // Buffer the m3u8 so we can rewrite URLs before sending
        let body = '';
        proxyRes.setEncoding('utf8');
        proxyRes.on('data', chunk => { body += chunk; });
        proxyRes.on('end', () => {
          // Use the final (possibly redirected) URL as base for resolution
          const rewritten = rewriteM3U8(body, url);
          const buf = Buffer.from(rewritten, 'utf8');
          resHeaders['content-length'] = buf.byteLength;
          resHeaders['content-type'] = 'application/vnd.apple.mpegurl';
          res.writeHead(proxyRes.statusCode || 200, resHeaders);
          res.end(buf);
        });
      } else {
        // Pass through binary data (TS segments, etc.) unchanged
        res.writeHead(proxyRes.statusCode || 200, resHeaders);
        proxyRes.pipe(res, { end: true });
      }
    });

    proxyReq.on('error', (err) => {
      console.error('[Proxy Error]:', err.message, 'URL:', url);
      if (!res.headersSent) {
        res.statusCode = 502;
        res.end('Upstream error: ' + err.message);
      }
    });

    if (req.method !== 'GET' && req.method !== 'HEAD') {
      req.pipe(proxyReq, { end: true });
    } else {
      proxyReq.end();
    }
  }

  doRequest(targetUrlStr, 5, targetUrlStr);
}

export default defineConfig({
  server: {
    port: 5173,
    host: true,
  },
  plugins: [
    {
      name: 'dynamic-cors-proxy',
      configureServer(server) {
        server.middlewares.use('/api/proxy', (req, res) => {
          // Handle CORS preflight
          if (req.method === 'OPTIONS') {
            res.writeHead(204, {
              'Access-Control-Allow-Origin': '*',
              'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
              'Access-Control-Allow-Headers': '*',
            });
            res.end();
            return;
          }

          // Parse ?url= query param
          const qIndex = req.url.indexOf('?');
          if (qIndex === -1) { res.statusCode = 400; res.end('Missing url parameter'); return; }
          const params = new URLSearchParams(req.url.slice(qIndex + 1));
          const targetUrl = params.get('url');

          if (!targetUrl) {
            res.statusCode = 400;
            res.end('Missing url parameter');
            return;
          }

          proxyRequest(req, res, targetUrl);
        });
      }
    }
  ]
});
