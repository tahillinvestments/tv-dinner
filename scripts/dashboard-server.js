import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { exec } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const PORT = 3888;

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.js':   'application/javascript; charset=utf-8',
  '.css':  'text/css; charset=utf-8',
  '.apk':  'application/vnd.android.package-archive',
  '.png':  'image/png',
  '.jpg':  'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg':  'image/svg+xml'
};

function serveFile(res, filePath, attachment = false) {
  if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not found');
    return;
  }
  const ext = path.extname(filePath).toLowerCase();
  const headers = {
    'Content-Type': MIME[ext] || 'application/octet-stream',
    'Content-Length': fs.statSync(filePath).size
  };
  if (attachment) {
    headers['Content-Disposition'] = `attachment; filename="${path.basename(filePath)}"`;
  }
  res.writeHead(200, headers);
  fs.createReadStream(filePath).pipe(res);
}

function handlePublish(req, res) {
  let body = '';
  req.on('data', chunk => { body += chunk; });
  req.on('end', () => {
    let parsed = {};
    try { if (body.trim()) parsed = JSON.parse(body); } catch (_) {}

    const target = parsed.versionCode || parsed.relId || '';
    console.log(`\n[Dashboard] Publish request${target ? ` for Build ${target}` : ''}...`);

    const cmd = target ? `node scripts/publish-update.js "${target}"` : 'node scripts/publish-update.js';
    exec(cmd, { cwd: projectRoot }, (err, stdout, stderr) => {
      if (err) {
        console.error('[Dashboard] Publish error:', stderr || err.message);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: stderr || err.message }));
      } else {
        console.log(stdout);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true }));
      }
    });
  });
}

const server = http.createServer((req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }

  const url = new URL(req.url, `http://localhost:${PORT}`);
  const pathname = url.pathname;

  // POST /api/publish → trigger publish-update.js
  if (req.method === 'POST' && pathname === '/api/publish') {
    handlePublish(req, res);
    return;
  }

  // GET /api/manifest → serve live updates.json
  if (req.method === 'GET' && pathname === '/api/manifest') {
    serveFile(res, path.join(projectRoot, 'updates.json'));
    return;
  }

  // GET /staging-apks/{filename} → serve private test APKs
  if (req.method === 'GET' && pathname.startsWith('/staging-apks/')) {
    const filename = path.basename(pathname); // prevent directory traversal
    serveFile(res, path.join(projectRoot, 'staging-apks', filename), true);
    return;
  }

  // Default: serve static files from project root (dashboard, public assets)
  const staticTarget = (pathname === '/' || pathname === '/update-dashboard.html')
    ? path.join(projectRoot, 'update-dashboard.html')
    : path.join(projectRoot, pathname);

  serveFile(res, staticTarget, staticTarget.endsWith('.apk'));
});

server.listen(PORT, '127.0.0.1', () => {
  const url = `http://localhost:${PORT}`;
  console.log('\n══════════════════════════════════════════════════');
  console.log(`🌐 TV Dinner Dashboard: ${url}`);
  console.log('   Review staged builds → click Publish when ready');
  console.log('   Press Ctrl+C to stop');
  console.log('══════════════════════════════════════════════════\n');

  exec(`powershell -Command "Start-Process '${url}'"`, err => {
    if (err) console.warn('Could not auto-launch browser:', err.message);
  });
});
