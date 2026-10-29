# Release Notes - RepoStore v1.0.22

**Release Version:** `v1.0.22`  
**Version Code:** `23`  
**Release Date:** October 29, 2026  

---

### Highlights & Key Improvements

#### 🚀 Resilient APK Downloader & "Failed to get file info" Fix
- **Eliminated Fatal Pre-flight Abort**: Fixed an issue where clicking the "Install" button would immediately fail with *"Failed to get file info"*. Pre-flight `HEAD` and Range probes are now treated as non-blocking optimizations. If a server, CDN, or proxy rejects or times out on `HEAD` requests, the downloader automatically falls back to standard single-part `GET` streaming.
- **Added Required Network Headers**: Added an OkHttp interceptor injecting `User-Agent: GitHubAppStore-Android` and `Accept: */*` across all requests, preventing HTTP 403 Forbidden responses from GitHub release endpoints and anti-bot filters.
- **Mirror Proxy Resilience**: If a selected mirror proxy is down, unreachable, or times out, the download automatically falls back to the direct GitHub release URL.
- **Multi-Part to Single-Part Fallback**: If multi-threaded chunk downloading encounters mid-stream connection issues, it automatically falls back to single-part streaming to guarantee successful download.
- **System DownloadManager Safety Net**: Added automatic fallback to Android's system `DownloadManager` in `AppInstaller` if parallel downloading fails.

#### 📦 Scoped Storage & Modern Android Compatibility (Android 10 - 16)
- **App-Specific Download Storage**: Saved APKs are now stored in `context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)` (with fallback to app cache directory). This eliminates `EACCES (Permission denied)` errors on Android 10+ without requiring broad storage permissions.
- **Clean FileProvider Integration**: Seamless URI generation via `FileProvider` ensures the system Package Installer can always read and install the downloaded APKs.

#### 🎨 Material 3 UI & Dialog Refinements
- **Theme Selection Cards**: Dynamic Material 3 container colors, selection outlines, and responsive iconography across Dark, Light, System, and "For You" theme options.
- **Searchable Language Dialog**: Clean surface and divider contrast with themed search field icons for faster language switching.

#### ⚖️ Open Source Licensing & Hygiene
- **Comprehensive License Directory**: Added accurate open-source license metadata (MIT, Apache 2.0, BSD-3-Clause, EPL) for all bundled dependencies.
- **Automated Verification**: Added unit test suite `MultiPartDownloaderTest` verifying download probe handling, headers, and fallback streaming.

---

### Full Changelog

- **Fix**: Resolve fatal "Failed to get file info" error on APK install button click (`MultiPartDownloader.kt`).
- **Fix**: Inject `User-Agent` and `Accept` headers into OkHttpClient download requests.
- **Fix**: Automatic mirror proxy failure fallback to direct GitHub URLs.
- **Fix**: Automatic parallel multi-part failure fallback to single-part GET streaming.
- **Fix**: Automatic fallback to system `DownloadManager` on download error.
- **Fix**: Prevent scoped storage permission denial on Android 10+ using app-specific external files directory.
- **Fix**: Fix download progress throttling logic.
- **Feat**: Refine Material 3 settings theme selector cards and language dialog color tokens.
- **Test**: Add `MultiPartDownloaderTest` unit test suite.
- **Chore**: Update Fastlane changelogs (`22.txt`, `23.txt`, `default.txt`) and `CHANGELOG.md`.
- **Chore**: Bump `versionCode` to 23 and `versionName` to 1.0.22.
