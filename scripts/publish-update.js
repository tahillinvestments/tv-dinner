import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

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

console.log(`\n======================================================`);
console.log(`🚀 PUBLISHING BUILD ${targetRelease.versionCode} (v${targetRelease.versionName})...`);
console.log(`======================================================\n`);

// 1. Mark target as published and older published as archived
updatesData.releases.forEach(r => {
  if (r.id === targetRelease.id || r.versionCode === targetRelease.versionCode) {
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
console.log(`✅ Updated updates.json (currentVersion = ${targetRelease.versionName})`);

// 2. Write public/version.json
const publicVersionJson = path.join(projectRoot, 'public', 'version.json');
fs.writeFileSync(publicVersionJson, JSON.stringify(updatesData.currentVersion, null, 2), 'utf8');
console.log(`✅ Updated public/version.json`);

// 3. Write android-app assets version.json
const androidVersionJson = path.join(projectRoot, 'android-app', 'app', 'src', 'main', 'assets', 'version.json');
if (fs.existsSync(path.dirname(androidVersionJson))) {
  fs.writeFileSync(androidVersionJson, JSON.stringify(updatesData.currentVersion, null, 2), 'utf8');
  console.log(`✅ Updated android-app assets version.json`);
}

// 4. Update dashboard html
const dashboardHtmlPath = path.join(projectRoot, 'update-dashboard.html');
if (fs.existsSync(dashboardHtmlPath)) {
  let html = fs.readFileSync(dashboardHtmlPath, 'utf8');
  const manifestJsonStr = JSON.stringify(updatesData, null, 2)
    .split('\n')
    .map((line, idx) => idx === 0 ? line : '    ' + line)
    .join('\n');
  html = html.replace(/const DEFAULT_MANIFEST = \{[\s\S]*?\n    \};/, `const DEFAULT_MANIFEST = ${manifestJsonStr};`);
  fs.writeFileSync(dashboardHtmlPath, html, 'utf8');
  console.log(`✅ Updated update-dashboard.html`);
}

// 5. Git Commit and Push to main
try {
  console.log('\n📦 Staging and committing release files to Git...');
  execSync('git add updates.json public/version.json android-app/app/src/main/assets/version.json update-dashboard.html version-notes.json android-app/app/build.gradle.kts android-app/app/src/main/java/com/tvdinner/ package.json scripts/', { cwd: projectRoot, stdio: 'inherit' });
  const commitMsg = `Publish Build ${targetRelease.versionCode} (v${targetRelease.versionName}): ${targetRelease.title}`;
  execSync(`git commit -m "${commitMsg.replace(/"/g, '\\"')}"`, { cwd: projectRoot, stdio: 'inherit' });
  console.log('✅ Git commit created successfully.');

  console.log('🚀 Pushing to GitHub (main)...');
  execSync('git push origin main', { cwd: projectRoot, stdio: 'inherit' });
  console.log('✅ Pushed to GitHub main successfully.');
} catch (e) {
  console.warn('⚠️ Git step notice:', e.message);
}

// 6. GitHub Release Creation via gh CLI
const releaseApkPath = path.join(projectRoot, 'android-app', 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
if (fs.existsSync(releaseApkPath)) {
  const releaseTag = `v${targetRelease.versionName}`;
  console.log(`\n📡 Uploading APK and creating GitHub Release ${releaseTag}...`);
  const notesFile = path.join(projectRoot, 'scratch', 'temp_release_notes.txt');
  fs.mkdirSync(path.dirname(notesFile), { recursive: true });
  fs.writeFileSync(notesFile, targetRelease.releaseNotes, 'utf8');

  try {
    execSync(`gh release create "${releaseTag}" "${releaseApkPath}#app-release.apk" --title "TV Dinner ${releaseTag}" --notes-file "${notesFile}"`, { cwd: projectRoot, stdio: 'inherit' });
    console.log(`🎉 GitHub Release ${releaseTag} created with app-release.apk attached!`);
  } catch (err) {
    console.log(`Release ${releaseTag} already exists, uploading/overwriting asset...`);
    try {
      execSync(`gh release upload "${releaseTag}" "${releaseApkPath}#app-release.apk" --clobber`, { cwd: projectRoot, stdio: 'inherit' });
      console.log(`🎉 APK uploaded to GitHub Release ${releaseTag}!`);
    } catch (uploadErr) {
      console.warn('Could not upload to GitHub release:', uploadErr.message);
    }
  }
  try { fs.unlinkSync(notesFile); } catch (_) {}
} else {
  console.warn(`⚠️ Compiled release APK not found at: ${releaseApkPath}`);
}

console.log('\n======================================================');
console.log(`🎉 VERSION v${targetRelease.versionName} (Build ${targetRelease.versionCode}) IS NOW FULLY PUBLISHED!`);
console.log(`- Live on GitHub Releases`);
console.log(`- Live on GitHub Pages (auto-deploying from main)`);
console.log(`- Live on all user TVs via version.json manifest`);
console.log('======================================================\n');
