import http from 'node:http';
import { Readable } from 'node:stream';

const PORT = process.env.PORT || 8080;
const STRIP_RES_HEADERS = new Set([
  'content-encoding', 'transfer-encoding', 'connection', 'keep-alive',
]);

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
  const targetUrlStr = parsedUrl.searchParams.get('url');

  if ((parsedUrl.pathname === '/health' || parsedUrl.pathname === '/') && !targetUrlStr) {
    res.writeHead(200, { 'Content-Type': 'text/plain', 'Access-Control-Allow-Origin': '*' });
    res.end('TV Dinner Standalone Node Proxy Server Running');
    return;
  }

  if (!targetUrlStr) {
    res.writeHead(400, { 'Access-Control-Allow-Origin': '*' });
    res.end('Missing url parameter');
    return;
  }

  let targetUrl;
  try {
    targetUrl = new URL(targetUrlStr);
  } catch {
    res.writeHead(400, { 'Access-Control-Allow-Origin': '*' });
    res.end('Invalid URL parameter');
    return;
  }

  const outgoingHeaders = {
    'user-agent': 'VLC/3.0.21 LibVLC/3.0.21',
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

    const outHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': '*',
    };
    for (const [k, v] of response.headers.entries()) {
      const lower = k.toLowerCase();
      if (!STRIP_RES_HEADERS.has(lower) && !lower.startsWith('access-control-')) {
        outHeaders[k] = v;
      }
    }

    if (isM3U8) {
      const text = await response.text();
      const proto = req.headers['x-forwarded-proto'] || (host.includes('localhost') || host.includes('127.0.0.1') ? 'http' : 'https');
      const proxyOrigin = `${proto}://${host}${parsedUrl.pathname}`;
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
});

server.listen(PORT, () => {
  console.log(`Standalone TV Dinner Proxy Server listening on port ${PORT}`);
});
