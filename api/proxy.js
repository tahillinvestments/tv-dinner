import http from 'http';
import https from 'https';
import { URL } from 'url';

const STRIP_REQ_HEADERS = new Set(['host', 'referer', 'origin', 'x-forwarded-for', 'x-real-ip']);
const IPTV_HOSTS = new Set(['portal5458.com', 'kstv.us']);

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
      
      // IPTV hosts or HTTP origins on HTTPS sites REQUIRE proxying for VLC User-Agent spoofing and HTTP-to-HTTPS mixed content bypass.
      if (proxySegments || isNestedPlaylist || isIptvOrHttp) {
        return `/api/proxy?url=${encodeURIComponent(absolute)}`;
      }
      return absolute;
    } catch {
      return line;
    }
  }).join('\n');
}

export default function handler(req, res) {
  if (req.method === 'OPTIONS') {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, PUT, PATCH, DELETE');
    res.setHeader('Access-Control-Allow-Headers', '*');
    return res.status(204).end();
  }

  const targetUrlStr = req.query.url;
  const shouldProxySegments = req.query.proxySegments === '1' || req.query.proxySegments === 'true';
  if (!targetUrlStr) {
    return res.status(400).send('Missing url parameter');
  }

  let targetUrl;
  try {
    targetUrl = new URL(targetUrlStr);
  } catch {
    return res.status(400).send('Invalid URL parameter');
  }

  const isIptvHost = IPTV_HOSTS.has(targetUrl.hostname) || targetUrl.hostname.includes('portal5458') || targetUrl.pathname.includes('player_api') || targetUrl.pathname.includes('.m3u') || targetUrl.pathname.includes('/live/');
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

  function doRequest(urlStr, redirectsLeft) {
    const parsed = new URL(urlStr);
    const transport = parsed.protocol === 'https:' ? https : http;

    const options = {
      hostname: parsed.hostname,
      port: parsed.port || (parsed.protocol === 'https:' ? 443 : 80),
      path: parsed.pathname + parsed.search,
      method: req.method,
      headers: { ...outHeaders, host: parsed.host },
    };

    const proxyReq = transport.request(options, (proxyRes) => {
      if ([301, 302, 303, 307, 308].includes(proxyRes.statusCode) && proxyRes.headers.location && redirectsLeft > 0) {
        proxyRes.resume();
        let redirectUrl = proxyRes.headers.location;
        if (!redirectUrl.startsWith('http')) {
          redirectUrl = `${parsed.protocol}//${parsed.host}${redirectUrl}`;
        }
        return doRequest(redirectUrl, redirectsLeft - 1);
      }

      const contentType = (proxyRes.headers['content-type'] || '').toLowerCase();
      const isM3U8 = contentType.includes('mpegurl') || urlStr.includes('.m3u8');

      res.setHeader('Access-Control-Allow-Origin', '*');
      res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS, PUT, PATCH, DELETE');
      res.setHeader('Access-Control-Allow-Headers', '*');

      if (proxyRes.headers['content-type']) {
        res.setHeader('Content-Type', proxyRes.headers['content-type']);
      }

      if (isM3U8) {
        let body = '';
        proxyRes.setEncoding('utf8');
        proxyRes.on('data', chunk => { body += chunk; });
        proxyRes.on('end', () => {
          const rewritten = rewriteM3U8(body, urlStr, shouldProxySegments);
          res.setHeader('Content-Type', 'application/vnd.apple.mpegurl');
          return res.status(proxyRes.statusCode || 200).send(rewritten);
        });
      } else {
        res.status(proxyRes.statusCode || 200);
        proxyRes.pipe(res);
      }
    });

    proxyReq.on('error', (err) => {
      console.error('[Vercel Proxy Error]:', err.message);
      if (!res.headersSent) {
        res.setHeader('Access-Control-Allow-Origin', '*');
        return res.status(502).send('Proxy error: ' + err.message);
      }
    });

    if (req.method !== 'GET' && req.method !== 'HEAD') {
      req.pipe(proxyReq);
    } else {
      proxyReq.end();
    }
  }

  doRequest(targetUrlStr, 5);
}
