import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { Readable } from 'node:stream';

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

async function forwardStream(req, res, targetUrlStr) {
  let targetUrl;
  try {
    targetUrl = new URL(targetUrlStr);
  } catch {
    res.writeHead(400, { 'Access-Control-Allow-Origin': '*' });
    res.end('Invalid URL parameter');
    return;
  }

  const isIptvHost = targetUrl.hostname.includes('portal5458') || targetUrl.hostname.includes('kstv') || targetUrl.hostname.includes('asoseller') || targetUrl.pathname.includes('/live/') || targetUrl.pathname.includes('/movie/') || targetUrl.pathname.includes('/series/');
  const outgoingHeaders = {
    'user-agent': isIptvHost ? 'VLC/3.0.21 LibVLC/3.0.21' : (req.headers['user-agent'] || 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'),
    'accept': '*/*',
  };
  if (req.headers.range) {
    outgoingHeaders['range'] = req.headers.range;
  }

  try {
    const response = await fetch(targetUrl.href, {
      method: req.method === 'POST' ? 'POST' : 'GET',
      headers: outgoingHeaders,
      redirect: 'follow',
    });

    const finalUrl = response.url || targetUrl.href;
    const contentType = (response.headers.get('content-type') || '').toLowerCase();
    const isM3U8 =
      contentType.includes('mpegurl') ||
      targetUrl.pathname.endsWith('.m3u8') ||
      targetUrl.pathname.endsWith('.m3u');

    const outHeaders = {};
    for (const [k, v] of response.headers.entries()) {
      const lower = k.toLowerCase();
      if (!STRIP_RES_HEADERS.has(lower)) {
        outHeaders[k] = v;
      }
    }
    outHeaders['Access-Control-Allow-Origin'] = '*';
    outHeaders['Access-Control-Allow-Methods'] = 'GET, POST, OPTIONS';
    outHeaders['Access-Control-Allow-Headers'] = '*';

    if (isM3U8) {
      const text = await response.text();
      const host = req.headers.host || `localhost:${PORT}`;
      const proto = req.headers['x-forwarded-proto'] || (host.includes('localhost') || host.includes('127.0.0.1') ? 'http' : 'https');
      const proxyOrigin = `${proto}://${host}/`;
      const rewritten = rewriteM3U8(text, finalUrl, proxyOrigin);
      outHeaders['content-type'] = 'application/vnd.apple.mpegurl';
      outHeaders['content-length'] = Buffer.byteLength(rewritten).toString();
      res.writeHead(response.status, outHeaders);
      res.end(rewritten);
      return;
    }

    res.writeHead(response.status, outHeaders);
    if (response.body) {
      const nodeStream = Readable.fromWeb(response.body);
      nodeStream.pipe(res);
    } else {
      res.end();
    }
  } catch (err) {
    res.writeHead(502, { 'Access-Control-Allow-Origin': '*' });
    res.end('Proxy Error: ' + err.message);
  }
}

const server = http.createServer(async (req, res) => {
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
