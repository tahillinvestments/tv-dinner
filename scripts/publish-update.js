import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');

const updatesJsonPath = path.join(projectRoot, 'updates.json');
if (!fs.existsSync(updatesJsonPath)) {
  console.error('❌ updates.json not found!');
  process.exit(1);
}

const updatesData = JSON.parse(fs.readFileSync(updatesJsonPath, 'utf8'));
const targetRelease = updatesData.releases.find(r => r.status === 'STAGED') || updatesData.releases[0];

if (!targetRelease) {
  console.error('❌ No staged release found to publish.');
  process.exit(1);
}

// Mark target as published and older published as archived
updatesData.releases.forEach(r => {
  if (r.id === targetRelease.id) {
    r.status = 'PUBLISHED';
    r.publishedAt = new Date().toISOString();
  } else if (r.status === 'PUBLISHED') {
    r.status = 'ARCHIVED';
  }
});

updatesData.currentVersion = {
  versionCode: targetRelease.versionCode,
  versionName: targetRelease.versionName,
  title: targetRelease.title,
  releaseNotes: targetRelease.releaseNotes,
  apkUrl: targetRelease.apkUrl,
  mandatory: targetRelease.mandatory || false,
  publishedAt: new Date().toISOString()
};

fs.writeFileSync(updatesJsonPath, JSON.stringify(updatesData, null, 2), 'utf8');

// Write public/version.json
const publicVersionJson = path.join(projectRoot, 'public', 'version.json');
fs.writeFileSync(publicVersionJson, JSON.stringify(updatesData.currentVersion, null, 2), 'utf8');

// Write android-app assets version.json
const androidVersionJson = path.join(projectRoot, 'android-app', 'app', 'src', 'main', 'assets', 'version.json');
if (fs.existsSync(path.dirname(androidVersionJson))) {
  fs.writeFileSync(androidVersionJson, JSON.stringify(updatesData.currentVersion, null, 2), 'utf8');
}

// Update dashboard html
const dashboardHtmlPath = path.join(projectRoot, 'update-dashboard.html');
if (fs.existsSync(dashboardHtmlPath)) {
  let html = fs.readFileSync(dashboardHtmlPath, 'utf8');
  const manifestJsonStr = JSON.stringify(updatesData, null, 2)
    .split('\n')
    .map((line, idx) => idx === 0 ? line : '    ' + line)
    .join('\n');
  html = html.replace(/const DEFAULT_MANIFEST = \{[\s\S]*?\n    \};/, `const DEFAULT_MANIFEST = ${manifestJsonStr};`);
  fs.writeFileSync(dashboardHtmlPath, html, 'utf8');
}

console.log('\n======================================================');
console.log(`🚀 VERSION v${targetRelease.versionName} (Build ${targetRelease.versionCode}) IS NOW PUBLISHED LIVE!`);
console.log(`- Active manifest written to: public/version.json`);
console.log(`- In-app asset updated: android-app/app/src/main/assets/version.json`);
console.log('======================================================\n');
