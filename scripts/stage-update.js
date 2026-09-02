import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');

const gradlePath = path.join(projectRoot, 'android-app', 'app', 'build.gradle.kts');
const gradleContent = fs.readFileSync(gradlePath, 'utf8');
const codeMatch = gradleContent.match(/versionCode\s*=\s*(\d+)/);
const nameMatch = gradleContent.match(/versionName\s*=\s*"([^"]+)"/);
const versionCode = codeMatch ? parseInt(codeMatch[1], 10) : 206;
const versionName = nameMatch ? nameMatch[1] : '2.0.6';

const releaseTitle = 'Live TV Navigation Fix, Backup Portal Failover & Priority Card Art Loading';
const releaseNotes = [
  '• Live TV: Seamless right navigation from categories into channels across all categories (not just Favorites & History)',
  '• Backup Portal Failover: Plugged in http://vpn.uhd4.top:80 as official failover for bad HTTP status / stream drop recovery',
  '• In-App Stream Reconnect: Added clean socket eviction and Reconnect Stream button so app restart is never needed',
  '• Movies & Series Poster Priority: Proactive preloading of visible cards in viewing order with dedicated fast Coil timeouts',
  '• Settings: Added Primary and Backup Portal URL configuration fields'
].join('\n');

const releaseApkSource = path.join(projectRoot, 'android-app', 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
const debugApkSource = path.join(projectRoot, 'android-app', 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
const sourceApk = fs.existsSync(releaseApkSource) ? releaseApkSource : debugApkSource;

if (!fs.existsSync(sourceApk)) {
  console.error('❌ No compiled APK found. Please build the APK first with: ./gradlew.bat assembleRelease');
  process.exit(1);
}

const publicApksDir = path.join(projectRoot, 'public', 'apks');
fs.mkdirSync(publicApksDir, { recursive: true });

const targetVersionApk = path.join(publicApksDir, `tv-dinner-v${versionName}.apk`);
const targetLatestApk = path.join(publicApksDir, 'tv-dinner-latest.apk');
const targetAppReleaseApk = path.join(publicApksDir, 'app-release.apk');
const rootReleaseApk = path.join(projectRoot, 'tv-dinner-release.apk');

fs.copyFileSync(sourceApk, targetVersionApk);
fs.copyFileSync(sourceApk, targetLatestApk);
fs.copyFileSync(sourceApk, targetAppReleaseApk);
fs.copyFileSync(sourceApk, rootReleaseApk);

console.log(`✅ Staged APK: ${targetVersionApk} (${(fs.statSync(targetVersionApk).size / 1024 / 1024).toFixed(2)} MB)`);

const updatesJsonPath = path.join(projectRoot, 'updates.json');
let updatesData = {
  currentVersion: {
    versionCode: 203,
    versionName: "2.0.3",
    title: "Direct Server Migration & Fast Playback",
    releaseNotes: "Stable release with direct stream connectivity.",
    apkUrl: "https://github.com/tahillinvestments/tv-dinner/releases/download/v2.0.3/app-release.apk",
    mandatory: false,
    publishedAt: "2026-08-28T12:00:00Z"
  },
  releases: [
    {
      id: "rel-203",
      versionCode: 203,
      versionName: "2.0.3",
      title: "Direct Server Migration & Fast Playback",
      status: "PUBLISHED",
      releaseNotes: "• Migrated to direct high-speed server\n• Removed legacy proxy and VPN layers\n• Modernized Subscription settings",
      apkPath: "android-app/app/build/outputs/apk/release/app-release.apk",
      localApkUrl: "public/apks/tv-dinner-latest.apk",
      apkUrl: "https://github.com/tahillinvestments/tv-dinner/releases/download/v2.0.3/app-release.apk",
      mandatory: false,
      createdAt: "2026-08-28T12:00:00Z",
      publishedAt: "2026-08-28T12:00:00Z"
    }
  ]
};

if (fs.existsSync(updatesJsonPath)) {
  try {
    let raw = fs.readFileSync(updatesJsonPath, 'utf8');
    if (raw.charCodeAt(0) === 0xFEFF) {
      raw = raw.slice(1);
    }
    const parsed = JSON.parse(raw);
    if (parsed && Array.isArray(parsed.releases)) {
      updatesData = parsed;
    }
  } catch (e) {
    console.warn('Could not parse updates.json, keeping default base');
  }
}

if (!updatesData.currentVersion || !updatesData.currentVersion.versionCode) {
  updatesData.currentVersion = {
    versionCode: 203,
    versionName: "2.0.3",
    title: "Direct Server Migration & Fast Playback",
    releaseNotes: "Stable release with direct stream connectivity.",
    apkUrl: "https://github.com/tahillinvestments/tv-dinner/releases/download/v2.0.3/app-release.apk",
    localApkUrl: "public/apks/tv-dinner-latest.apk",
    mandatory: false,
    publishedAt: "2026-08-28T12:00:00Z"
  };
}

const releaseId = `rel-${versionCode}`;
const stagedRelease = {
  id: releaseId,
  versionCode: versionCode,
  versionName: versionName,
  title: releaseTitle,
  status: 'STAGED',
  releaseNotes: releaseNotes,
  apkPath: 'android-app/app/build/outputs/apk/release/app-release.apk',
  localApkUrl: `public/apks/tv-dinner-v${versionName}.apk`,
  apkUrl: `https://github.com/tahillinvestments/tv-dinner/releases/download/v${versionName}/app-release.apk`,
  mandatory: false,
  createdAt: new Date().toISOString(),
  publishedAt: null
};

const existingIndex = updatesData.releases.findIndex(r => r.versionCode === versionCode || r.id === releaseId);
if (existingIndex >= 0) {
  updatesData.releases[existingIndex] = stagedRelease;
} else {
  updatesData.releases.unshift(stagedRelease);
}

fs.writeFileSync(updatesJsonPath, JSON.stringify(updatesData, null, 2), 'utf8');
console.log(`✅ Updated ${updatesJsonPath} with staged build v${versionName} (Build ${versionCode})`);

const dashboardHtmlPath = path.join(projectRoot, 'update-dashboard.html');
if (fs.existsSync(dashboardHtmlPath)) {
  let html = fs.readFileSync(dashboardHtmlPath, 'utf8');
  const manifestJsonStr = JSON.stringify(updatesData, null, 2)
    .split('\n')
    .map((line, idx) => idx === 0 ? line : '    ' + line)
    .join('\n');
  
  html = html.replace(/const DEFAULT_MANIFEST = \{[\s\S]*?\n    \};/, `const DEFAULT_MANIFEST = ${manifestJsonStr};`);
  fs.writeFileSync(dashboardHtmlPath, html, 'utf8');
  console.log(`✅ Synced update-dashboard.html with latest manifest`);
}

console.log('\n======================================================');
console.log(`🎉 VERSION v${versionName} (Build ${versionCode}) STAGED SUCCESSFULLY!`);
console.log('======================================================');
console.log(`1. Test APK Locally:`);
console.log(`   - Direct download file: public/apks/tv-dinner-v${versionName}.apk`);
console.log(`   - Install on Android TV via ADB:`);
console.log(`     adb install -r "android-app/app/build/outputs/apk/release/app-release.apk"`);
console.log(`2. Open Update Dashboard in Browser:`);
console.log(`   - Run: npm run update:dashboard (or open update-dashboard.html)`);
console.log(`   - Click 'Download APK' to test the file on any device`);
console.log(`   - Click 'Publish' whenever you are ready to push update to production!`);
console.log('======================================================\n');
