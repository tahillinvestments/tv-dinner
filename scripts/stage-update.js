import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const projectRoot = path.resolve(__dirname, '..');

// ── Read version from Gradle ────────────────────────────────────────────────
const gradlePath = path.join(projectRoot, 'android-app', 'app', 'build.gradle.kts');
const gradleContent = fs.readFileSync(gradlePath, 'utf8');
const codeMatch = gradleContent.match(/versionCode\s*=\s*(\d+)/);
const nameMatch = gradleContent.match(/versionName\s*=\s*"([^"]+)"/);
const versionCode = codeMatch ? parseInt(codeMatch[1], 10) : 1;
const versionName = nameMatch ? nameMatch[1] : '1.0.0';

// ── Read release notes from version-notes.json ──────────────────────────────
let releaseTitle = 'Performance improvements and bug fixes';
let releaseNotes = '• General stability enhancements and UI optimizations';
const versionNotesPath = path.join(projectRoot, 'version-notes.json');
if (fs.existsSync(versionNotesPath)) {
  try {
    const allNotes = JSON.parse(fs.readFileSync(versionNotesPath, 'utf8'));
    if (allNotes[versionName]) {
      releaseTitle = allNotes[versionName].title || releaseTitle;
      releaseNotes = allNotes[versionName].releaseNotes || releaseNotes;
    }
  } catch (e) {
    console.warn('⚠️  Could not parse version-notes.json:', e.message);
  }
}

// ── Locate compiled APK ──────────────────────────────────────────────────────
const releaseApk = path.join(projectRoot, 'android-app', 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk');
const debugApk   = path.join(projectRoot, 'android-app', 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk');
const sourceApk  = fs.existsSync(releaseApk) ? releaseApk : (fs.existsSync(debugApk) ? debugApk : null);

if (!sourceApk) {
  console.error('❌ No compiled APK found. Build first:\n   npm run android:build:release');
  process.exit(1);
}

// ── Copy to private staging-apks/ ───────────────────────────────────────────
const stagingDir = path.join(projectRoot, 'staging-apks');
fs.mkdirSync(stagingDir, { recursive: true });

const apkFileName = `tv-dinner-v${versionName}-b${versionCode}.apk`;
const stagedApkPath = path.join(stagingDir, apkFileName);
fs.copyFileSync(sourceApk, stagedApkPath);

const apkSizeMB = (fs.statSync(stagedApkPath).size / 1024 / 1024).toFixed(2);
console.log(`\n✅ APK staged: ${apkFileName} (${apkSizeMB} MB)`);

// ── Update updates.json ──────────────────────────────────────────────────────
const updatesJsonPath = path.join(projectRoot, 'updates.json');
let updatesData = { currentVersion: {}, releases: [] };

if (fs.existsSync(updatesJsonPath)) {
  try {
    let raw = fs.readFileSync(updatesJsonPath, 'utf8');
    if (raw.charCodeAt(0) === 0xFEFF) raw = raw.slice(1); // strip BOM
    const parsed = JSON.parse(raw);
    if (parsed && Array.isArray(parsed.releases)) updatesData = parsed;
  } catch (e) {
    console.warn('⚠️  Could not parse updates.json, starting fresh.');
  }
}

const releaseId = `rel-${versionCode}`;
const stagedEntry = {
  id: releaseId,
  versionCode,
  versionName,
  title: releaseTitle,
  status: 'STAGED',
  releaseNotes,
  stagedApk: `staging-apks/${apkFileName}`,   // private local path
  apkUrl: `https://github.com/tahillinvestments/tv-dinner/releases/download/v${versionName}/app-release.apk`,
  mandatory: false,
  createdAt: new Date().toISOString(),
  publishedAt: null
};

const existingIdx = updatesData.releases.findIndex(
  r => r.versionCode === versionCode || r.id === releaseId
);
if (existingIdx >= 0) {
  updatesData.releases[existingIdx] = stagedEntry;
  console.log(`🔄 Updated existing staged entry for v${versionName} (Build ${versionCode})`);
} else {
  updatesData.releases.unshift(stagedEntry);
}

fs.writeFileSync(updatesJsonPath, JSON.stringify(updatesData, null, 2), 'utf8');

console.log('\n══════════════════════════════════════════════════');
console.log(`🎉 v${versionName} (Build ${versionCode}) STAGED`);
console.log('══════════════════════════════════════════════════');
console.log(`🧪 TEST APK: staging-apks/${apkFileName}`);
console.log(`   Install via ADB:  adb install -r "${stagedApkPath}"`);
console.log('');
console.log('When ready → open the dashboard and click Publish:');
console.log('   npm run update:dashboard');
console.log('══════════════════════════════════════════════════\n');
