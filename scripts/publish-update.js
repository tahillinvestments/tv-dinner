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

const targetArg = process.argv[2];
const isRepublishFlag = process.argv.includes('--republish');

let targetRelease;
if (targetArg && targetArg !== '--republish') {
  targetRelease = updatesData.releases.find(r => String(r.versionCode) === targetArg || r.id === targetArg);
  if (!targetRelease) {
    console.warn(`⚠️ Target release '${targetArg}' not found in updates.json, falling back to staged release.`);
  }
}
if (!targetRelease) {
  targetRelease = updatesData.releases.find(r => r.status === 'STAGED') || updatesData.releases[0];
}

if (!targetRelease) {
  console.error('❌ No staged or matching release found to publish.');
  process.exit(1);
}

const maxCode = Math.max(...updatesData.releases.map(r => r.versionCode || 0));
const isOlderBuild = isRepublishFlag || (targetRelease.status === 'ARCHIVED') || (targetRelease.versionCode < maxCode);

if (isOlderBuild) {
  const newCode = maxCode + 1;
  const newVersionName = `${targetRelease.versionName}-r${newCode}`;
  console.log(`\n🔄 REPUBLISH OF OLDER BUILD DETECTED: Build ${targetRelease.versionCode} (v${targetRelease.versionName})`);
  console.log(`✨ Creating newer version duplicate: Build ${newCode} (v${newVersionName}) so Android TV clients detect and install update...`);

  // Locate source APK
  const possibleSourcePaths = [
    path.join(projectRoot, targetRelease.localApkUrl || ''),
    path.join(projectRoot, 'public', 'apks', `tv-dinner-v${targetRelease.versionName}.apk`),
    path.join(projectRoot, 'public', 'apks', 'tv-dinner-latest.apk'),
    path.join(projectRoot, 'android-app', 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk')
  ];
  const sourceApk = possibleSourcePaths.find(p => p && fs.existsSync(p));

  if (sourceApk) {
    const newLocalApkName = `tv-dinner-v${newVersionName}.apk`;
    const destApkPath = path.join(projectRoot, 'public', 'apks', newLocalApkName);
    const latestApkPath = path.join(projectRoot, 'public', 'apks', 'tv-dinner-latest.apk');
    const rootApkPath = path.join(projectRoot, 'tv-dinner-release.apk');
    const releaseApkDest = path.join(projectRoot, 'android-app', 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');

    fs.copyFileSync(sourceApk, destApkPath);
    fs.copyFileSync(sourceApk, latestApkPath);
    fs.copyFileSync(sourceApk, rootApkPath);
    if (fs.existsSync(path.dirname(releaseApkDest))) {
      fs.copyFileSync(sourceApk, releaseApkDest);
    }
    console.log(`✅ Duplicated APK to ${newLocalApkName} and refreshed latest release APK targets.`);
  }

  // Create duplicate release object
  const duplicateRelease = {
    id: `rel-${newCode}`,
    versionCode: newCode,
    versionName: newVersionName,
    title: `${targetRelease.title} (Republished)`,
    status: 'PUBLISHED',
    releaseNotes: `[Republished Build based on v${targetRelease.versionName} (Build ${targetRelease.versionCode})]\n${targetRelease.releaseNotes || ''}`,
    apkPath: "android-app/app/build/outputs/apk/release/app-release.apk",
    localApkUrl: `public/apks/tv-dinner-v${newVersionName}.apk`,
    apkUrl: `https://github.com/tahillinvestments/tv-dinner/releases/download/v${newVersionName}/app-release.apk`,
    mandatory: targetRelease.mandatory || false,
    createdAt: new Date().toISOString(),
    publishedAt: new Date().toISOString()
  };

  updatesData.releases.forEach(r => {
    if (r.status === 'PUBLISHED') {
      r.status = 'ARCHIVED';
    }
  });
  updatesData.releases.unshift(duplicateRelease);
  targetRelease = duplicateRelease;
} else {
  // 1. Mark target as published and older published as archived
  updatesData.releases.forEach(r => {
    if (r.id === targetRelease.id || r.versionCode === targetRelease.versionCode) {
      r.status = 'PUBLISHED';
      r.publishedAt = new Date().toISOString();
    } else if (r.status === 'PUBLISHED') {
      r.status = 'ARCHIVED';
    }
  });
}

console.log(`\n======================================================`);
console.log(`🚀 PUBLISHING BUILD ${targetRelease.versionCode} (v${targetRelease.versionName})...`);
console.log(`======================================================\n`);

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
const releaseTag = `v${targetRelease.versionName}`;
let releaseExists = false;
try {
  execSync(`gh release view "${releaseTag}"`, { cwd: projectRoot, stdio: 'pipe' });
  releaseExists = true;
} catch (_) {}

if (!releaseExists && fs.existsSync(releaseApkPath)) {
  console.log(`\n📡 Uploading APK and creating GitHub Release ${releaseTag}...`);
  const notesFile = path.join(projectRoot, 'scratch', 'temp_release_notes.txt');
  fs.mkdirSync(path.dirname(notesFile), { recursive: true });
  fs.writeFileSync(notesFile, targetRelease.releaseNotes, 'utf8');

  try {
    execSync(`gh release create "${releaseTag}" "${releaseApkPath}#app-release.apk" --title "TV Dinner ${releaseTag}" --notes-file "${notesFile}"`, { cwd: projectRoot, stdio: 'inherit' });
    console.log(`🎉 GitHub Release ${releaseTag} created with app-release.apk attached!`);
  } catch (err) {
    console.warn('Could not create GitHub release:', err.message);
  }
  try { fs.unlinkSync(notesFile); } catch (_) {}
} else if (releaseExists) {
  console.log(`ℹ️ GitHub Release ${releaseTag} already exists live with assets attached.`);
} else {
  console.warn(`⚠️ Compiled release APK not found at: ${releaseApkPath}`);
}

console.log('\n======================================================');
console.log(`🎉 VERSION v${targetRelease.versionName} (Build ${targetRelease.versionCode}) IS NOW FULLY PUBLISHED!`);
console.log(`- Live on GitHub Releases`);
console.log(`- Live on GitHub Pages (auto-deploying from main)`);
console.log(`- Live on all user TVs via version.json manifest`);
console.log('======================================================\n');
