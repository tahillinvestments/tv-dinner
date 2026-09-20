# Project Rules & Guidelines: TV Dinner

## Mandatory Staging Workflow: Updated APK in `/staging-apks`
Whenever staging a build or advancing to the next build (e.g., "stage to next build", "stage build", "create next build"):
1. **Never stop at git commit / push**: Staging is **NOT complete** until the updated APK is generated and placed into the `/staging-apks/` folder (`staging-apks/tv-dinner-v<versionName>-b<versionCode>.apk`) and `updates.json` is updated with `status: "STAGED"`.
2. **Standard Staging Command**:
   - Run `npm run update:build-and-stage` (or `./gradlew assembleRelease` followed by `node scripts/stage-update.js`).
   - If only debug build was assembled, ensure `node scripts/stage-update.js` runs to copy the newly generated APK into `staging-apks/` as `tv-dinner-v<versionName>-b<versionCode>.apk`.
3. **Always verify `staging-apks/`**:
   - Confirm that `staging-apks/tv-dinner-v<versionName>-b<versionCode>.apk` exists with the current `versionName` and `versionCode`.
   - Confirm that `updates.json` contains the staged entry.
   - Update `version-notes.json` with descriptive release notes for the new version.

## Platform Context
- **Android APK only**: No web deployment. Vite build bundles assets into the Android WebView.
- `staging-apks/` is gitignored — test APKs stay local for side-loading and OTA testing via dashboard.
