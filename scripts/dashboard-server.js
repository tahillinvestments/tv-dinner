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

function ensureStagingApk() {
  const stagingDir = path.join(projectRoot, 'staging-apks');
  fs.mkdirSync(stagingDir, { recursive: true });

  const releaseApk = path.join(projectRoot, 'android-app', 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
  if (fs.existsSync(releaseApk)) {
    try {
      const gradlePath = path.join(projectRoot, 'android-app', 'app', 'build.gradle.kts');
      const gradleContent = fs.readFileSync(gradlePath, 'utf8');
      const codeMatch = gradleContent.match(/versionCode\s*=\s*(\d+)/);
      const nameMatch = gradleContent.match(/versionName\s*=\s*"([^"]+)"/);
      const code = codeMatch ? codeMatch[1] : 'latest';
      const name = nameMatch ? nameMatch[1] : 'latest';
      const targetName = `tv-dinner-v${name}-b${code}.apk`;
      const targetPath = path.join(stagingDir, targetName);
      if (!fs.existsSync(targetPath)) {
        fs.copyFileSync(releaseApk, targetPath);
        console.log(`[Dashboard] Auto-synced release APK to staging-apks/${targetName}`);
      }
    } catch (_) {}
  }
}

function resolveLocalApk(params = {}) {
  const code = params.code || params.versionCode;
  const name = params.name || params.version || params.versionName;
  const file = params.file;

  const candidatePaths = [];

  if (file) {
    candidatePaths.push(path.join(projectRoot, 'staging-apks', path.basename(file)));
    candidatePaths.push(path.join(projectRoot, 'public', 'apks', path.basename(file)));
    candidatePaths.push(path.join(projectRoot, 'dist', 'apks', path.basename(file)));
  }

  if (name && code) {
    candidatePaths.push(path.join(projectRoot, 'staging-apks', `tv-dinner-v${name}-b${code}.apk`));
    candidatePaths.push(path.join(projectRoot, 'public', 'apks', `tv-dinner-v${name}-b${code}.apk`));
    candidatePaths.push(path.join(projectRoot, 'staging-apks', `tv-dinner-v${name}.apk`));
    candidatePaths.push(path.join(projectRoot, 'public', 'apks', `tv-dinner-v${name}.apk`));
  } else if (name) {
    candidatePaths.push(path.join(projectRoot, 'staging-apks', `tv-dinner-v${name}.apk`));
    candidatePaths.push(path.join(projectRoot, 'public', 'apks', `tv-dinner-v${name}.apk`));
    candidatePaths.push(path.join(projectRoot, 'dist', 'apks', `tv-dinner-v${name}.apk`));
  }

  if (code) {
    for (const dir of ['staging-apks', 'public/apks', 'dist/apks']) {
      const fullDir = path.join(projectRoot, dir);
      if (fs.existsSync(fullDir)) {
        try {
          const files = fs.readdirSync(fullDir);
          for (const f of files) {
            if (f.endsWith('.apk') && (f.includes(`b${code}`) || f.includes(`r${code}`))) {
              candidatePaths.push(path.join(fullDir, f));
            }
          }
        } catch (_) {}
      }
    }
  }

  // Always fallback to latest compiled release build if available
  candidatePaths.push(path.join(projectRoot, 'android-app', 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk'));
  candidatePaths.push(path.join(projectRoot, 'public', 'apks', 'tv-dinner-latest.apk'));
  candidatePaths.push(path.join(projectRoot, 'public', 'apks', 'app-release.apk'));
  candidatePaths.push(path.join(projectRoot, 'dist', 'apks', 'tv-dinner-latest.apk'));

  for (const p of candidatePaths) {
    if (fs.existsSync(p)) {
      try {
        const stat = fs.statSync(p);
        if (stat.isFile() && stat.size > 1000000) {
          return p;
        }
      } catch (_) {}
    }
  }

  return null;
}

function serveFile(req, res, filePath, attachment = false) {
  if (!fs.existsSync(filePath) || !fs.statSync(filePath).isFile()) {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Not found');
    return;
  }
  const ext = path.extname(filePath).toLowerCase();
  const stat = fs.statSync(filePath);
  const headers = {
    'Content-Type': MIME[ext] || 'application/octet-stream',
    'Content-Length': stat.size,
    'Accept-Ranges': 'bytes'
  };
  if (attachment || ext === '.apk') {
    headers['Content-Disposition'] = `attachment; filename="${path.basename(filePath)}"`;
    headers['Cache-Control'] = 'no-cache';
  }
  res.writeHead(200, headers);
  if (req.method === 'HEAD') {
    res.end();
    return;
  }
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
  res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }

  const url = new URL(req.url, `http://localhost:${PORT}`);
  const pathname = url.pathname;
  const isGetOrHead = req.method === 'GET' || req.method === 'HEAD';

  // POST /api/publish → trigger publish-update.js
  if (req.method === 'POST' && pathname === '/api/publish') {
    handlePublish(req, res);
    return;
  }

  // GET /api/manifest → serve live updates.json
  if (isGetOrHead && pathname === '/api/manifest') {
    serveFile(req, res, path.join(projectRoot, 'updates.json'));
    return;
  }

  // GET /api/download-apk → robust local APK download
  if (isGetOrHead && pathname === '/api/download-apk') {
    const code = url.searchParams.get('code') || url.searchParams.get('versionCode');
    const name = url.searchParams.get('name') || url.searchParams.get('version');
    const file = url.searchParams.get('file');

    const resolved = resolveLocalApk({ code, name, file });
    if (resolved) {
      const outFilename = name
        ? (code ? `tv-dinner-v${name}-b${code}.apk` : `tv-dinner-v${name}.apk`)
        : path.basename(resolved);
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': `attachment; filename="${outFilename}"`,
        'Content-Length': fs.statSync(resolved).size,
        'Cache-Control': 'no-cache'
      });
      if (req.method === 'HEAD') {
        res.end();
        return;
      }
      fs.createReadStream(resolved).pipe(res);
      return;
    }

    res.writeHead(404, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'APK file not found on server' }));
    return;
  }

  // GET /staging-apks/{filename} → serve private test APKs (with fallback resolver)
  if (isGetOrHead && pathname.startsWith('/staging-apks/')) {
    const filename = path.basename(pathname);
    let apkPath = path.join(projectRoot, 'staging-apks', filename);
    if (!fs.existsSync(apkPath)) {
      const fallback = resolveLocalApk({ file: filename });
      if (fallback) apkPath = fallback;
    }
    serveFile(req, res, apkPath, true);
    return;
  }

  // GET /public/apks/{filename} or /dist/apks/{filename}
  if (isGetOrHead && (pathname.startsWith('/public/apks/') || pathname.startsWith('/dist/apks/') || pathname.startsWith('/apks/'))) {
    const filename = path.basename(pathname);
    const resolved = resolveLocalApk({ file: filename });
    if (resolved) {
      serveFile(req, res, resolved, true);
      return;
    }
  }

  // Default: serve static files from project root (dashboard, public assets)
  const staticTarget = (pathname === '/' || pathname === '/update-dashboard.html')
    ? path.join(projectRoot, 'update-dashboard.html')
    : path.join(projectRoot, pathname);

  serveFile(req, res, staticTarget, staticTarget.endsWith('.apk'));
});

ensureStagingApk();

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
