# In-App Updater Setup

Since this app isn't on the Play Store, it ships with its own lightweight updater
that checks a small JSON file in your GitHub repo and offers to download + install
new versions directly.

## How it works

1. `UpdateChecker` fetches `version.json` from your repo's raw GitHub URL
2. If the JSON's `versionCode` is higher than what's installed, a dialog pops up
3. Tapping **Update Now** downloads the APK via Android's DownloadManager
4. Once downloaded, the system package installer opens automatically

The check runs once automatically when the app opens, and can also be triggered
manually from **Settings → Updates → Check for updates**.

## One-time setup (2 minutes)

### 1. Point the checker at your repo
Edit `app/src/main/java/com/watercantracker/app/update/UpdateChecker.kt`:

```kotlin
const val VERSION_MANIFEST_URL =
    "https://raw.githubusercontent.com/YOUR_GITHUB_USERNAME/water-can/main/version.json"
```

Replace `YOUR_GITHUB_USERNAME` with your actual GitHub username.

### 2. Keep `version.json` up to date
At the repo root, `version.json` looks like this:

```json
{
  "versionCode": 4,
  "versionName": "1.4.0",
  "apkUrl": "https://github.com/YOUR_GITHUB_USERNAME/water-can/releases/download/v1.4.0/app-release.apk",
  "changelog": "- What's new in this release"
}
```

Every time you build a new release:
1. Bump `versionCode` in `app/build.gradle.kts` (must always increase)
2. Bump `versionName` to match
3. Update `version.json` with the new `versionCode`, `versionName`, and a link to
   the new APK (see below for hosting)
4. Commit and push `version.json` to the `main` branch

### 3. Host the APK as a GitHub Release
1. Go to your repo → **Releases → Draft a new release**
2. Tag it `v1.4.0` (matching versionName)
3. Upload the built `app-release.apk` as a release asset
4. Publish — GitHub gives you a permanent download URL like:
   `https://github.com/<user>/<repo>/releases/download/v1.4.0/app-release.apk`
5. Put that exact URL in `version.json`'s `apkUrl` field

### 4. Phone setting
The very first time a user taps "Update Now", Android will ask them to allow
"Install unknown apps" for whichever app is doing the install (Chrome/Files/etc).
This is normal for any sideloaded app and only needs to be granted once.

## Testing it
1. Build and install the *current* version on your phone
2. Bump `versionCode` in `app/build.gradle.kts`, rebuild, upload to a new GitHub
   Release, and update `version.json` to point at it
3. Open the app (or tap "Check for updates" in Settings) — the update dialog
   should appear
