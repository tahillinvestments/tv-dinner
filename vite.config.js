import { defineConfig } from 'vite';
import http from 'http';
import https from 'https';
import url from 'url';

export default defineConfig({
  server: {
    port: 5173,
    host: true,
    proxy: {},
  },
  plugins: [
    {
      name: 'dynamic-cors-proxy',
      configureServer(server) {
        server.middlewares.use('/api/proxy', (req, res, next) => {
          const parsedUrl = url.parse(req.url, true);
          const targetUrl = parsedUrl.query.url;

          if (!targetUrl) {
            res.statusCode = 400;
            res.end('Missing url parameter');
            return;
          }

          const targetParsed = url.parse(targetUrl);
          const transport = targetParsed.protocol === 'https:' ? https : http;

          // Strip incoming headers that might conflict or reveal local info
          const headers = { ...req.headers };
          delete headers.host;
          delete headers.referer;
          delete headers.origin;

          const proxyReq = transport.request(targetUrl, {
            method: req.method,
            headers: {
              ...headers,
              host: targetParsed.host,
            }
          }, (proxyRes) => {
            // Forward headers, but overwrite Access-Control-Allow-Origin for CORS
            const resHeaders = { ...proxyRes.headers };
            resHeaders['Access-Control-Allow-Origin'] = '*';
            resHeaders['Access-Control-Allow-Methods'] = 'GET, POST, OPTIONS, PUT, PATCH, DELETE';
            resHeaders['Access-Control-Allow-Headers'] = '*';

            res.writeHead(proxyRes.statusCode || 200, resHeaders);
            proxyRes.pipe(res);
          });

          proxyReq.on('error', (err) => {
            console.error('[Proxy Error]:', err.message);
            res.statusCode = 500;
            res.end('Proxy error: ' + err.message);
          });

          req.pipe(proxyReq);
        });
      }
    }
  ]
});
