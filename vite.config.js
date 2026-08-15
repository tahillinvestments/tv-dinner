import { defineConfig } from 'vite';
import http from 'http';
import https from 'https';
import fs from 'fs';
import path from 'path';
import { URL } from 'url';

// Headers to strip from incoming proxy requests
const STRIP_REQ_HEADERS = new Set(['host', 'referer', 'origin', 'x-forwarded-for', 'x-real-ip', 'accept-encoding']);

// Known IPTV portal hosts that need the VLC user-agent spoof
const IPTV_HOSTS = new Set(['portal5458.com', 'kstv.us']);

/**
 * Rewrite an HLS playlist so all segment and sub-playlist URLs are
 * proxied back through our local /api/proxy endpoint.
 */
function rewriteM3U8(m3uText, baseUrl, proxySegments = false) {
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
        return `/api/proxy?url=${encodeURIComponent(absolute)}`;
      }
      return absolute;
    } catch {
      return line;
    }
  }).join('\n');
}

function proxyRequest(req, res, targetUrlStr, proxySegments = false) {
  let targetUrl;
  try {
    targetUrl = new URL(targetUrlStr);
  } catch {
    res.statusCode = 400;
    res.end('Invalid URL parameter');
    return;
  }

  const isIptvHost = IPTV_HOSTS.has(targetUrl.hostname) || targetUrl.hostname.includes('portal5458') || targetUrl.pathname.includes('player_api') || targetUrl.pathname.includes('.m3u') || targetUrl.pathname.includes('/live/');
  const transport = targetUrl.protocol === 'https:' ? https : http;

  const outHeaders = {};
  for (const [k, v] of Object.entries(req.headers)) {
    if (!STRIP_REQ_HEADERS.has(k.toLowerCase())) {
      outHeaders[k] = v;
    }
  }
  outHeaders['host'] = targetUrl.host;

  if (isIptvHost) {
    for (const key of Object.keys(outHeaders)) {
      if (key.toLowerCase() === 'user-agent') delete outHeaders[key];
    }
    outHeaders['user-agent'] = 'VLC/3.0.21 LibVLC/3.0.21';
    outHeaders['connection'] = 'close';
  }

  function doRequest(url, redirectsLeft, originalUrl) {
    const parsed = new URL(url);
    const t = parsed.protocol === 'https:' ? https : http;

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
      if ([301, 302, 303, 307, 308].includes(proxyRes.statusCode) && proxyRes.headers.location && redirectsLeft > 0) {
        proxyRes.resume();
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

      const resHeaders = { ...proxyRes.headers };
      resHeaders['Access-Control-Allow-Origin'] = '*';
      resHeaders['Access-Control-Allow-Methods'] = 'GET, POST, OPTIONS, PUT, PATCH, DELETE';
      resHeaders['Access-Control-Allow-Headers'] = '*';
      delete resHeaders['connection'];
      delete resHeaders['transfer-encoding'];
      delete resHeaders['content-encoding'];

      if (isM3U8) {
        let body = '';
        proxyRes.setEncoding('utf8');
        proxyRes.on('data', chunk => { body += chunk; });
        proxyRes.on('end', () => {
          const rewritten = rewriteM3U8(body, url, proxySegments);
          const buf = Buffer.from(rewritten, 'utf8');
          resHeaders['content-length'] = buf.byteLength;
          resHeaders['content-type'] = 'application/vnd.apple.mpegurl';
          res.writeHead(proxyRes.statusCode || 200, resHeaders);
          res.end(buf);
        });
      } else {
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

function getLocalCatalog() {
  try {
    const catalogPath = path.resolve(process.cwd(), 'data', 'vod_catalog.json');
    if (fs.existsSync(catalogPath)) {
      return JSON.parse(fs.readFileSync(catalogPath, 'utf8'));
    }
  } catch (e) {
    console.error('Failed to read local vod_catalog.json:', e);
  }
  return { vod_categories: [], vod_streams: [], series_categories: [], series: [], series_info: {} };
}

export default defineConfig({
  server: {
    port: 5173,
    host: true,
  },
  plugins: [
    {
      name: 'dynamic-cors-proxy-and-vod',
      configureServer(server) {
        // 1. Xtream Codes API Emulator & VOD endpoints
        server.middlewares.use((req, res, next) => {
          const urlObj = new URL(req.url, 'http://localhost:5173');
          const pathname = urlObj.pathname;

          // Enable CORS
          res.setHeader('Access-Control-Allow-Origin', '*');
          res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
          res.setHeader('Access-Control-Allow-Headers', '*');

          if (req.method === 'OPTIONS') {
            res.writeHead(204).end();
            return;
          }

          // 1. Forward Xtream player_api.php queries to local vxparser (port 8888) with fallback
          if (pathname === '/player_api.php') {
            const targetUrl = `http://127.0.0.1:8888${req.url}`;
            proxyRequest(req, res, targetUrl, false);
            return;
          }

          // 2. Forward movie and series streaming requests to vxparser
          if (pathname.startsWith('/movie/') || pathname.startsWith('/series/')) {
            const targetUrl = `http://127.0.0.1:8888${req.url}`;
            proxyRequest(req, res, targetUrl, true);
            return;
          }

          const seriesMatch = pathname.match(/^\/series\/([^/]+)\/([^/]+)\/(\d+)\.(\w+)$/);
          if (seriesMatch) {
            const streamId = seriesMatch[3];
            const catalog = getLocalCatalog();
            let directUrl = null;
            if (catalog.series_info) {
              for (const sInfo of Object.values(catalog.series_info)) {
                if (sInfo.episodes) {
                  for (const epList of Object.values(sInfo.episodes)) {
                    const ep = epList.find(e => String(e.id) === String(streamId));
                    if (ep && ep.direct_source) {
                      directUrl = ep.direct_source;
                      break;
                    }
                  }
                }
                if (directUrl) break;
              }
            }
            if (directUrl) {
              proxyRequest(req, res, directUrl, false);
              return;
            }
          }

          next();
        });

        // 2. CORS Proxy for HLS and media segments
        server.middlewares.use('/api/proxy', (req, res) => {
          if (req.method === 'OPTIONS') {
            res.writeHead(204, {
              'Access-Control-Allow-Origin': '*',
              'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
              'Access-Control-Allow-Headers': '*',
            });
            res.end();
            return;
          }

          const qIndex = req.url.indexOf('?');
          if (qIndex === -1) { res.statusCode = 400; res.end('Missing url parameter'); return; }
          const params = new URLSearchParams(req.url.slice(qIndex + 1));
          const targetUrl = params.get('url');
          const proxySegments = params.get('proxySegments') === '1' || params.get('proxySegments') === 'true';

          if (!targetUrl) {
            res.statusCode = 400;
            res.end('Missing url parameter');
            return;
          }

          proxyRequest(req, res, targetUrl, proxySegments);
        });
      }
    }
  ]
});
