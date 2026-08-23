import http from 'node:http';
import https from 'node:https';
import fs from 'node:fs';
import path from 'node:path';
import { URL } from 'node:url';

// Prevent unhandled stream or parser exceptions from crashing the proxy server
process.on('uncaughtException', (err) => {
  console.error('[UNCAUGHT EXCEPTION]', err && err.stack ? err.stack : err);
});

process.on('unhandledRejection', (reason) => {
  console.error('[UNHANDLED REJECTION]', reason);
});

const PORT = process.env.PORT || 8080;
const STRIP_RES_HEADERS = new Set([
  'content-encoding', 'transfer-encoding', 'connection', 'keep-alive',
]);

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

function rewriteM3U8(m3uText, finalUrl, proxyOrigin) {
  const base = new URL(finalUrl);
  return m3uText.split('\n').map(line => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) return line;
    try {
      const absoluteUrl = new URL(trimmed, base);
      if (base.search && !absoluteUrl.search) {
        absoluteUrl.search = base.search;
      }
      return `${proxyOrigin}?url=${encodeURIComponent(absoluteUrl.href)}`;
    } catch {
      return line;
    }
  }).join('\n');
}

/**
 * Crash-proof native HTTP/HTTPS stream forwarder.
 * Uses native Node.js streams without undici/fetch to eliminate assertion crashes.
 */
function forwardStream(req, res, targetUrlStr, maxRedirects = 5) {
  if (maxRedirects < 0) {
    if (!res.headersSent) {
      res.writeHead(502, { 'Access-Control-Allow-Origin': '*' });
      res.end('Too many redirects');
    }
    return;
  }

  let targetUrl;
  try {
    targetUrl = new URL(targetUrlStr);
  } catch {
    if (!res.headersSent) {
      res.writeHead(400, { 'Access-Control-Allow-Origin': '*' });
      res.end('Invalid URL parameter');
    }
    return;
  }

  const isHttps = targetUrl.protocol === 'https:';
  const client = isHttps ? https : http;

  const isIptvHost = targetUrl.hostname.includes('portal5458') ||
    targetUrl.hostname.includes('kstv') ||
    targetUrl.hostname.includes('asoseller') ||
    targetUrl.pathname.includes('/live/') ||
    targetUrl.pathname.includes('/movie/') ||
    targetUrl.pathname.includes('/series/');

  const outgoingHeaders = {
    'user-agent': isIptvHost ? 'VLC/3.0.21 LibVLC/3.0.21' : (req.headers['user-agent'] || 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'),
    'accept': '*/*',
  };
  if (req.headers.range) {
    outgoingHeaders['range'] = req.headers.range;
  }

  const requestOptions = {
    method: req.method === 'POST' ? 'POST' : 'GET',
    headers: outgoingHeaders,
  };

  let proxyReq;
  try {
    proxyReq = client.request(targetUrl, requestOptions, (proxyRes) => {
      // Clear connect timeout once stream headers arrive so long-running live streams never abort
      proxyReq.setTimeout(0);

      // Follow 301, 302, 303, 307, 308 redirects automatically
      if ([301, 302, 303, 307, 308].includes(proxyRes.statusCode) && proxyRes.headers.location) {
        try {
          const redirectUrl = new URL(proxyRes.headers.location, targetUrl).href;
          proxyRes.resume();
          return forwardStream(req, res, redirectUrl, maxRedirects - 1);
        } catch (e) {
          console.error('[Redirect Parse Error]', e.message);
        }
      }

      const contentType = (proxyRes.headers['content-type'] || '').toLowerCase();
      const isM3U8 = contentType.includes('mpegurl') ||
        targetUrl.pathname.endsWith('.m3u8') ||
        targetUrl.pathname.endsWith('.m3u');

      const outHeaders = {};
      for (const [k, v] of Object.entries(proxyRes.headers)) {
        if (!STRIP_RES_HEADERS.has(k.toLowerCase())) {
          outHeaders[k] = v;
        }
      }
      outHeaders['Access-Control-Allow-Origin'] = '*';
      outHeaders['Access-Control-Allow-Methods'] = 'GET, POST, OPTIONS';
      outHeaders['Access-Control-Allow-Headers'] = '*';

      if (isM3U8) {
        let rawBody = '';
        proxyRes.setEncoding('utf8');
        proxyRes.on('data', (chunk) => { rawBody += chunk; });
        proxyRes.on('end', () => {
          const host = req.headers.host || `localhost:${PORT}`;
          const proto = req.headers['x-forwarded-proto'] || (host.includes('localhost') || host.includes('127.0.0.1') ? 'http' : 'https');
          const proxyOrigin = `${proto}://${host}/`;
          const rewritten = rewriteM3U8(rawBody, targetUrl.href, proxyOrigin);
          outHeaders['content-type'] = 'application/vnd.apple.mpegurl';
          outHeaders['content-length'] = Buffer.byteLength(rewritten).toString();
          if (!res.headersSent) {
            res.writeHead(proxyRes.statusCode || 200, outHeaders);
            res.end(rewritten);
          }
        });
        proxyRes.on('error', (err) => {
          console.error('[M3U8 Read Error]', err.message);
          if (!res.headersSent) {
            res.writeHead(502, { 'Access-Control-Allow-Origin': '*' });
            res.end('M3U8 Read Error: ' + err.message);
          }
        });
        return;
      }

      // Stream binary media directly with backpressure handling
      if (!res.headersSent) {
        res.writeHead(proxyRes.statusCode || 200, outHeaders);
      }

      proxyRes.pipe(res);

      proxyRes.on('error', (err) => {
        console.error('[Pipe Error]', err.message);
        if (!res.writableEnded) res.end();
      });
    });

    proxyReq.on('error', (err) => {
      console.error('[ProxyReq Error]', err.message);
      if (!res.headersSent) {
        res.writeHead(502, { 'Access-Control-Allow-Origin': '*' });
        res.end('Proxy Error: ' + err.message);
      } else if (!res.writableEnded) {
        res.end();
      }
    });

    proxyReq.on('timeout', () => {
      proxyReq.destroy(new Error('Connection timed out'));
    });

    // Cleanup upstream connection immediately if client aborts/navigates away
    req.on('close', () => {
      proxyReq.destroy();
    });
    res.on('close', () => {
      proxyReq.destroy();
    });

    if (req.method === 'POST') {
      req.pipe(proxyReq);
    } else {
      proxyReq.end();
    }
  } catch (err) {
    console.error('[Forward Error]', err.message);
    if (!res.headersSent) {
      res.writeHead(502, { 'Access-Control-Allow-Origin': '*' });
      res.end('Proxy Exception: ' + err.message);
    }
  }
}

const server = http.createServer((req, res) => {
  // CORS Preflight
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': '*',
    }).end();
    return;
  }

  const host = req.headers.host || `localhost:${PORT}`;
  const parsedUrl = new URL(req.url, `http://${host}`);
  const pathname = parsedUrl.pathname;
  const targetUrlStr = parsedUrl.searchParams.get('url');

  if (pathname === '/health') {
    res.writeHead(200, { 'Content-Type': 'text/plain', 'Access-Control-Allow-Origin': '*' });
    res.end('TV Dinner Standalone Node Proxy Server Running');
    return;
  }

  // Handle player_api.php Xtream standard queries
  if (pathname === '/player_api.php') {
    const action = parsedUrl.searchParams.get('action');
    const categoryId = parsedUrl.searchParams.get('category_id');
    const seriesId = parsedUrl.searchParams.get('series_id');
    const vodId = parsedUrl.searchParams.get('vod_id');
    const catalog = getLocalCatalog();

    res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });

    if (action === 'get_vod_categories') {
      res.end(JSON.stringify(catalog.vod_categories || []));
      return;
    }
    if (action === 'get_vod_streams') {
      let streams = catalog.vod_streams || [];
      if (categoryId) {
        streams = streams.filter(s => String(s.category_id) === String(categoryId));
      }
      res.end(JSON.stringify(streams));
      return;
    }
    if (action === 'get_vod_info') {
      const movie = (catalog.vod_streams || []).find(s => String(s.stream_id) === String(vodId));
      res.end(JSON.stringify({ info: movie || {}, movie_data: movie || {} }));
      return;
    }
    if (action === 'get_series_categories') {
      res.end(JSON.stringify(catalog.series_categories || []));
      return;
    }
    if (action === 'get_series') {
      let series = catalog.series || [];
      if (categoryId) {
        series = series.filter(s => String(s.category_id) === String(categoryId));
      }
      res.end(JSON.stringify(series));
      return;
    }
    if (action === 'get_series_info') {
      const info = (catalog.series_info && catalog.series_info[seriesId]) || { seasons: [], episodes: {}, info: {} };
      res.end(JSON.stringify(info));
      return;
    }

    res.end(JSON.stringify({
      user_info: { username: 'LocalUser', status: 'Active', exp_date: '1999999999', is_trial: '0', active_cons: '1' },
      server_info: { url: 'localhost', port: String(PORT), server_protocol: 'http' }
    }));
    return;
  }

  // Handle movie and series stream routes
  const movieMatch = pathname.match(/^\/movie\/([^/]+)\/([^/]+)\/(\d+)\.(\w+)$/);
  if (movieMatch) {
    const streamId = parseInt(movieMatch[3]);
    const catalog = getLocalCatalog();
    const movie = (catalog.vod_streams || []).find(s => s.stream_id === streamId);
    if (movie && movie.direct_source) {
      return forwardStream(req, res, movie.direct_source);
    }
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
      return forwardStream(req, res, directUrl);
    }
  }

  if (!targetUrlStr) {
    res.writeHead(400, { 'Access-Control-Allow-Origin': '*' });
    res.end('Missing url parameter');
    return;
  }

  forwardStream(req, res, targetUrlStr);
});

server.listen(PORT, () => {
  console.log(`Standalone TV Dinner Proxy Server listening on port ${PORT}`);
});
