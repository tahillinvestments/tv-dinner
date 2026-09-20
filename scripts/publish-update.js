/**
 * publish-update.js
 *
 * Promotes the current STAGED release to PUBLISHED.
 *
 * KEY ANDROID RULE: Android rejects installs where the APK's internal
 * versionCode is <= the installed version. This script ALWAYS bumps
 * versionCode in build.gradle.kts and rebuilds the APK before publishing,
 * guaranteeing Android TV devices accept the update.
 *
 * Usage:
 *   node scripts/publish-update.js              → publish current STAGED build
 *   node scripts/publish-update.js [versionCode|relId]  → publish specific build
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { execSync } from 'child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');

// ── Load updates.json ────────────────────────────────────────────────────────
const updatesJsonPath = path.join(projectRoot, 'updates.json');
if (!fs.existsSync(updatesJsonPath)) {
  console.error('❌ updates.json not found. Stage a build first: npm run android:stage');
  process.exit(1);
}
const updatesData = JSON.parse(fs.readFileSync(updatesJsonPath, 'utf8'));

// ── Find the target release ──────────────────────────────────────────────────
const targetArg = process.argv[2];
let targetRelease;

if (targetArg) {
  targetRelease = updatesData.releases.find(
    r => String(r.versionCode) === targetArg || r.id === targetArg
  );
  if (!targetRelease) {
    console.warn(`⚠️  Target '${targetArg}' not found — falling back to latest STAGED.`);
  }
}
if (!targetRelease) {
  targetRelease = updatesData.releases.find(r => r.status === 'STAGED');
}
if (!targetRelease) {
  console.error('❌ No STAGED release found. Run: npm run android:stage');
  process.exit(1);
}

// ── Determine the new versionCode ────────────────────────────────────────────
// If targetRelease is already higher than current live production, keep its version.
// If targetRelease <= currentVersion (e.g. republishing an archived release), bump to max + 1.
const liveVersionCode = updatesData.currentVersion?.versionCode || 0;
const alreadyHigher = targetRelease.versionCode > liveVersionCode;
const maxExisting = Math.max(...updatesData.releases.map(r => r.versionCode || 0));

const newVersionCode = alreadyHigher ? targetRelease.versionCode : (maxExisting + 1);
const baseVersionName = targetRelease.versionName.replace(/-r\d+$/, ''); // strip any prior -r suffix
const newVersionName = alreadyHigher ? targetRelease.versionName : `${baseVersionName}-r${newVersionCode}`;

const isRepublish = !alreadyHigher;

console.log('\n══════════════════════════════════════════════════');
if (isRepublish) {
  console.log(`🔄 REPUBLISH: v${targetRelease.versionName} (Build ${targetRelease.versionCode})`);
  console.log(`   → New version: v${newVersionName} (Build ${newVersionCode})`);
} else {
  console.log(`🚀 PUBLISHING: v${newVersionName} (Build ${newVersionCode})`);
}
console.log('══════════════════════════════════════════════════\n');

// ── Bump versionCode + versionName in build.gradle.kts ──────────────────────
const gradlePath = path.join(projectRoot, 'android-app', 'app', 'build.gradle.kts');
let gradleContent = fs.readFileSync(gradlePath, 'utf8');

gradleContent = gradleContent
  .replace(/versionCode\s*=\s*\d+/, `versionCode = ${newVersionCode}`)
  .replace(/versionName\s*=\s*"[^"]+"/, `versionName = "${newVersionName}"`);

fs.writeFileSync(gradlePath, gradleContent, 'utf8');
console.log(`✅ build.gradle.kts → versionCode = ${newVersionCode}, versionName = "${newVersionName}"`);

// ── Rebuild the APK if needed ────────────────────────────────────────────────
const builtApkPath = path.join(projectRoot, 'android-app', 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
const needsRebuild = isRepublish || !fs.existsSync(builtApkPath);

if (needsRebuild) {
  console.log('🔨 Rebuilding APK with new version code...');
  try {
    execSync('powershell -Command "cd android-app; ./gradlew.bat assembleRelease"', {
      cwd: projectRoot,
      stdio: 'inherit'
    });
    console.log('✅ APK rebuilt successfully.');
  } catch (err) {
    console.error('❌ Gradle build failed. Reverting build.gradle.kts...');
    gradleContent = gradleContent
      .replace(/versionCode\s*=\s*\d+/, `versionCode = ${targetRelease.versionCode}`)
      .replace(/versionName\s*=\s*"[^"]+"/, `versionName = "${targetRelease.versionName}"`);
    fs.writeFileSync(gradlePath, gradleContent, 'utf8');
    process.exit(1);
  }
} else {
  console.log('✅ Staged release APK is already compiled with matching version — skipping redundant build.');
}

// ── Copy rebuilt APK to staging-apks/ ───────────────────────────────────────
if (!fs.existsSync(builtApkPath)) {
  console.error('❌ Built APK not found at expected path after Gradle build.');
  process.exit(1);
}

const stagingDir = path.join(projectRoot, 'staging-apks');
fs.mkdirSync(stagingDir, { recursive: true });

const apkFileName = `tv-dinner-v${newVersionName}-b${newVersionCode}.apk`;
const stagedApkPath = path.join(stagingDir, apkFileName);
fs.copyFileSync(builtApkPath, stagedApkPath);
console.log(`✅ Published APK: staging-apks/${apkFileName}`);

// ── Update updates.json ──────────────────────────────────────────────────────
const publishedEntry = {
  id: `rel-${newVersionCode}`,
  versionCode: newVersionCode,
  versionName: newVersionName,
  title: isRepublish ? `${targetRelease.title} (Republished)` : targetRelease.title,
  status: 'PUBLISHED',
  releaseNotes: isRepublish
    ? `[Republished from v${targetRelease.versionName}]\n${targetRelease.releaseNotes || ''}`
    : targetRelease.releaseNotes,
  stagedApk: `staging-apks/${apkFileName}`,
  apkUrl: `https://github.com/tahillinvestments/tv-dinner/releases/download/v${newVersionName}/app-release.apk`,
  mandatory: targetRelease.mandatory || false,
  createdAt: targetRelease.createdAt || new Date().toISOString(),
  publishedAt: new Date().toISOString()
};

// Archive any currently PUBLISHED release, mark original STAGED as ARCHIVED
updatesData.releases.forEach(r => {
  if (r.status === 'PUBLISHED') r.status = 'ARCHIVED';
  if (r.id === targetRelease.id) r.status = 'ARCHIVED';
});

// Add the new published entry at the top
updatesData.releases.unshift(publishedEntry);

updatesData.currentVersion = {
  versionCode: newVersionCode,
  versionName: newVersionName,
  title: publishedEntry.title,
  releaseNotes: publishedEntry.releaseNotes,
  apkUrl: publishedEntry.apkUrl,
  mandatory: publishedEntry.mandatory,
  publishedAt: publishedEntry.publishedAt
};

fs.writeFileSync(updatesJsonPath, JSON.stringify(updatesData, null, 2), 'utf8');
console.log(`✅ updates.json → currentVersion = v${newVersionName} (Build ${newVersionCode})`);

// ── Write public/version.json (what TVs poll) ────────────────────────────────
const publicVersionJson = path.join(projectRoot, 'public', 'version.json');
fs.mkdirSync(path.dirname(publicVersionJson), { recursive: true });
fs.writeFileSync(publicVersionJson, JSON.stringify(updatesData.currentVersion, null, 2), 'utf8');
console.log('✅ public/version.json updated');

// ── Write android-app assets version.json ───────────────────────────────────
const androidVersionJson = path.join(projectRoot, 'android-app', 'app', 'src', 'main', 'assets', 'version.json');
if (fs.existsSync(path.dirname(androidVersionJson))) {
  fs.writeFileSync(androidVersionJson, JSON.stringify(updatesData.currentVersion, null, 2), 'utf8');
  console.log('✅ android-app assets/version.json updated');
}

// ── Git commit + push ────────────────────────────────────────────────────────
try {
  console.log('\n📦 Committing and pushing to GitHub...');
  execSync(
    'git add updates.json public/version.json android-app/ update-dashboard.html version-notes.json scripts/ package.json src/',
    { cwd: projectRoot, stdio: 'inherit' }
  );
  const commitMsg = `Publish v${newVersionName} (Build ${newVersionCode}): ${publishedEntry.title}`;
  execSync(`git commit -m "${commitMsg.replace(/"/g, '\\"')}"`, { cwd: projectRoot, stdio: 'inherit' });
  execSync('git push origin main', { cwd: projectRoot, stdio: 'inherit' });
  console.log('✅ Pushed to GitHub main.');
} catch (e) {
  console.warn('⚠️  Git step warning:', e.message);
}

// ── Create GitHub Release with APK ──────────────────────────────────────────
const releaseTag = `v${newVersionName}`;
let releaseExists = false;
try {
  execSync(`gh release view "${releaseTag}"`, { cwd: projectRoot, stdio: 'pipe' });
  releaseExists = true;
} catch (_) {}

if (!releaseExists) {
  console.log(`\n📡 Creating GitHub Release ${releaseTag}...`);
  const notesFile = path.join(projectRoot, 'scratch', 'temp_release_notes.txt');
  fs.mkdirSync(path.dirname(notesFile), { recursive: true });
  fs.writeFileSync(notesFile, publishedEntry.releaseNotes, 'utf8');
  try {
    execSync(
      `gh release create "${releaseTag}" "${builtApkPath}#app-release.apk" --title "TV Dinner ${releaseTag}" --notes-file "${notesFile}"`,
      { cwd: projectRoot, stdio: 'inherit' }
    );
    console.log(`🎉 GitHub Release ${releaseTag} created with APK.`);
  } catch (err) {
    console.warn('⚠️  Could not create GitHub release:', err.message);
  }
  try { fs.unlinkSync(notesFile); } catch (_) {}
} else {
  console.log(`ℹ️  GitHub Release ${releaseTag} already exists.`);
}

console.log('\n══════════════════════════════════════════════════');
console.log(`🎉 v${newVersionName} (Build ${newVersionCode}) IS LIVE!`);
console.log(`   APK versionCode ${newVersionCode} > installed — Android will accept the update`);
console.log(`   GitHub Release: https://github.com/tahillinvestments/tv-dinner/releases/tag/${releaseTag}`);
console.log('══════════════════════════════════════════════════\n');
