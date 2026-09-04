import http from 'http';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { exec } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');
const PORT = 3888;

function getContentType(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  switch (ext) {
    case '.html': return 'text/html; charset=utf-8';
    case '.json': return 'application/json; charset=utf-8';
    case '.js': return 'application/javascript; charset=utf-8';
    case '.css': return 'text/css; charset=utf-8';
    case '.apk': return 'application/vnd.android.package-archive';
    case '.png': return 'image/png';
    case '.jpg': case '.jpeg': return 'image/jpeg';
    case '.svg': return 'image/svg+xml';
    default: return 'application/octet-stream';
  }
}

function handlePublish(req, res) {
  let body = '';
  req.on('data', chunk => { body += chunk; });
  req.on('end', () => {
    console.log('\n[Dashboard Server] Received Publish request from Dashboard UI...');
    exec('node scripts/publish-update.js', { cwd: projectRoot }, (err, stdout, stderr) => {
      if (err) {
        console.error('[Dashboard Server] Publish error:', stderr || err.message);
        res.writeHead(500, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: stderr || err.message }));
      } else {
        console.log(stdout);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true, message: 'Release published live to GitHub!' }));
      }
    });
  });
}

const server = http.createServer((req, res) => {
  // Enable CORS
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const parsedUrl = new URL(req.url, `http://localhost:${PORT}`);
  let pathname = parsedUrl.pathname;

  if (req.method === 'POST' && pathname === '/api/publish') {
    handlePublish(req, res);
    return;
  }

  if (pathname === '/' || pathname === '/update-dashboard.html') {
    pathname = '/update-dashboard.html';
  }

  const filePath = path.join(projectRoot, pathname);

  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    const contentType = getContentType(filePath);
    res.writeHead(200, { 'Content-Type': contentType });
    fs.createReadStream(filePath).pipe(res);
  } else {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('File not found');
  }
});

server.listen(PORT, '127.0.0.1', () => {
  const url = `http://localhost:${PORT}`;
  console.log(`\n======================================================`);
  console.log(`🌐 TV Dinner Update Dashboard running at: ${url}`);
  console.log(`- Review staged builds and click 'Publish' when ready`);
  console.log(`- Press Ctrl+C to stop the dashboard server`);
  console.log(`======================================================\n`);

  exec(`powershell -Command "Start-Process ${url}"`, (err) => {
    if (err) console.warn('Could not auto-launch browser:', err.message);
  });
});
