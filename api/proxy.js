// Node.js Serverless Function (NOT Edge runtime).
// Edge runtime blocks direct IP address access (e.g. http://206.212.244.182:25461/...)
// which is where IPTV HLS segments are served from after a portal redirect.
// Node.js runtime has no such restriction.

import { Readable } from 'node:stream';

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

export default async function handler(req, res) {
  // CORS preflight
  if (req.method === 'OPTIONS') {
    res
      .writeHead(204, {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        'Access-Control-Allow-Headers': '*',
      })
      .end();
    return;
  }

  const host = req.headers.host || 'tvdinner.vercel.app';
  const parsedUrl = new URL(req.url, `https://${host}`);
  const targetUrlStr = parsedUrl.searchParams.get('url');

  if (!targetUrlStr) {
    res.status(400).end('Missing url parameter');
    return;
  }

  let targetUrl;
  try {
    targetUrl = new URL(targetUrlStr);
  } catch {
    res.status(400).end('Invalid URL parameter');
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

    // Set CORS + copy upstream headers
    const outHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': '*',
    };
    for (const [k, v] of response.headers.entries()) {
      if (!STRIP_RES_HEADERS.has(k.toLowerCase())) {
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

    // Stream binary content (TS segments, JSON API, etc.) without buffering
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
