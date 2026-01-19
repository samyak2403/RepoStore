<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="200" alt="Project logo"/>
</div>

<h1 align="center">RepoStore</h1>

<p align="center">
  <a href="https://opensource.org/licenses/MIT"><img alt="License" src="https://img.shields.io/badge/License-MIT-blue.svg"/></a>
  <a href="https://kotlinlang.org"><img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-1.9-7F52FF.svg?logo=kotlin&logoColor=white"/></a>
  <a href="#"><img alt="Platform" src="https://img.shields.io/badge/Platform-Android-brightgreen?logo=android"/></a>
  <a href="https://github.com/samyak2403/RepoStore/releases"><img alt="Release" src="https://img.shields.io/github/v/release/samyak2403/RepoStore?label=Release&logo=github"/></a>
  <a href="https://github.com/samyak2403/RepoStore/stargazers"><img alt="GitHub stars" src="https://img.shields.io/github/stars/samyak2403/RepoStore?style=social"/></a>
  <img alt="Material 3" src="https://img.shields.io/badge/Material-3-4285F4?logo=material-design&logoColor=white"/>
  <img alt="MVVM" src="https://img.shields.io/badge/Architecture-MVVM-orange"/>
</p>

<p align="center">
  RepoStore is a GitHub-powered Android app store that discovers repositories shipping real installable APKs and lets you install, track, and update them from one place.
</p>

<p align="center">
  <img src="screenshots/banner/1.png" />
</p>

---

### All screenshots can be found in [screenshots/](screenshots/) folder.

<img src="screenshots/preview.gif" align="right" width="320"/>

## ✨ What is RepoStore?

RepoStore is a native Android app that turns GitHub releases into a clean, Play Store style experience:

- Only shows repositories that actually provide installable APK assets.
- Detects installed apps and shows update availability.
- Always installs from the latest published release with changelog.
- Presents a polished details screen with stats, README, and developer info.

---

## 🔃 Download

<a href="https://github.com/samyak2403/RepoStore/releases">
  <img src="https://img.shields.io/badge/Download-APK-success?style=for-the-badge&logo=android" height="60"/>
</a>

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.samyak.repostore%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsamyak2403%2FRepoStore%22%2C%22author%22%3A%22samyak2403%22%2C%22name%22%3A%22RepoStore%22%2C%22preferredApkIndex%22%3A0%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22fallbackToOlderReleases%5C%22%3Atrue%2C%5C%22filterReleaseTitlesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22filterReleaseNotesByRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22verifyLatestTag%5C%22%3Afalse%2C%5C%22dontSortReleasesList%5C%22%3Afalse%2C%5C%22useLatestAssetDateAsReleaseDate%5C%22%3Afalse%2C%5C%22releaseTitleAsVersion%5C%22%3Afalse%2C%5C%22trackOnly%5C%22%3Afalse%2C%5C%22versionExtractionRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22matchGroupToUse%5C%22%3A%5C%22%5C%22%2C%5C%22versionDetection%5C%22%3Atrue%2C%5C%22releaseDateAsVersion%5C%22%3Afalse%2C%5C%22useVersionCodeAsOSVersion%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5C%22%2C%5C%22invertAPKFilter%5C%22%3Afalse%2C%5C%22autoApkFilterByArch%5C%22%3Atrue%2C%5C%22appName%5C%22%3A%5C%22%5C%22%2C%5C%22shizukuPretendToBeGooglePlay%5C%22%3Afalse%2C%5C%22allowInsecure%5C%22%3Afalse%2C%5C%22exemptFromBackgroundUpdates%5C%22%3Afalse%2C%5C%22skipUpdateNotifications%5C%22%3Afalse%2C%5C%22about%5C%22%3A%5C%22GitHub-powered%20app%20store%5C%22%7D%22%7D">
  <img src="screenshots/obtainium_badge.webp" height="60"/>
</a>

<a href="https://apt.izzysoft.de/packages/com.samyak.repostore">
  <img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroidButtonGreyBorder_nofont.png" height="80" alt="Get it at IzzyOnDroid"/>
</a>

---

## 🚀 Features

- **Smart Discovery**
  - Home sections for "Trending", "Recently Updated", and "Featured" projects.
  - Only repos with valid APK assets are shown.
  - Category-based filtering for apps.

- **Latest-Release Installs**
  - Fetches `/releases/latest` for each repo.
  - Shows only assets from the latest release.
  - Single "Install" action with download progress.

- **Rich Details Screen**
  - App name, version, "Install" button.
  - Stars, forks, language stats.
  - Rendered README content ("About this app").
  - Latest release notes with markdown formatting.
  - Screenshot gallery with fullscreen viewer.

- **Install & Update Tracking**
  - Opens APK downloads with the package installer.
  - Tracks installations and shows "Open" for installed apps.
  - Detects when updates are available.

- **Appearance & Theming**
  - Material 3 design with Material You support.
  - Dark mode with system theme support.
  - Clean, Play Store inspired UI.

- **GitHub Integration**
  - Optional GitHub sign-in via OAuth device flow.
  - Increases API rate limit from 60 to 5,000 requests/hour.
  - View developer profiles and repositories.

- **Support Developer**
  - UPI payment integration with QR code.
  - Detects installed UPI apps (GPay, PhonePe, Paytm, etc.).
  - One-tap payment support.

---

## � How doens an app appear in RepoStore?

RepoStore does not use any private indexing or manual curation. Your project can appear automatically if it follows these conditions:

1. **Public repository on GitHub**
   - Visibility must be `public`.

2. **At least one published release**
   - Created via GitHub Releases (not only tags).
   - The latest release must not be a draft or prerelease.

3. **APK assets in the latest release**
   - The latest release must contain at least one `.apk` file.
   - GitHub's auto-generated source artifacts are ignored.

4. **Discoverable by search / topics**
   - Repositories are fetched via the public GitHub Search API.
   - Topics like `android`, `mobile`, `apk` help ranking.
   - Having stars makes it more likely to appear in sections.

If your repo meets these conditions, RepoStore can find it through search and show it automatically—no manual submission required.

---

## 🧭 How RepoStore Works

1. **Search**
   - Uses GitHub's `/search/repositories` endpoint with Android-focused queries.
   - Applies scoring based on topics, language, and description.
   - Filters out archived repos.

2. **Release + Asset Check**
   - For candidate repos, calls `/repos/{owner}/{repo}/releases/latest`.
   - Checks the `assets` array for `.apk` files.
   - If no APK is found, the repo is excluded from results.

3. **Details Screen**
   - Repository info: name, owner, description, stars, forks.
   - Latest release: tag, published date, changelog, assets.
   - README: loaded and rendered as "About this app".
   - Screenshots: detected from repository contents.

4. **Install Flow**
   - When user taps "Install":
     - Downloads the APK with progress indicator.
     - Delegates to the system package installer.
     - Records installation in local database.
     - Shows "Open" button for installed apps.

---

## ⚙️ Tech Stack

- **Minimum Android SDK: 26 (Android 8.0)**

- **Language & Platform**
  - [Kotlin](https://kotlinlang.org/) with Coroutines & Flow
  - Android Native with ViewBinding

- **Architecture**
  - MVVM (Model-View-ViewModel)
  - Repository Pattern
  - Single Activity with Fragments

- **Networking & Data**
  - [Retrofit](https://square.github.io/retrofit/) + OkHttp
  - [Gson](https://github.com/google/gson) for JSON parsing
  - [Room](https://developer.android.com/jetpack/androidx/releases/room) for local database

- **UI & Design**
  - [Material 3](https://m3.material.io/) Components
  - [Glide](https://github.com/bumptech/glide) for image loading
  - [Markwon](https://github.com/noties/Markwon) for README rendering
  - [PhotoView](https://github.com/GetStream/photoview-android) for zoomable images

- **Auth & Security**
  - GitHub OAuth (Device Code flow)
  - SharedPreferences for token storage

---

## ✅ Why Use RepoStore?

- **No more hunting through GitHub releases**
  See only repos that actually ship APKs.

- **Knows what you installed**
  Tracks apps installed via RepoStore and shows when updates are available.

- **Always the latest release**
  Installs are guaranteed to come from the latest published release.

- **Play Store-like experience**
  Familiar UI with categories, search, and app details.

- **Open source & extensible**
  Written in Kotlin with clean architecture—easy to fork and extend.

---

## 💖 Support This Project

RepoStore is free and always will be. If it's helped you, consider:

- ⭐ **Star** this repository
- 🐛 **Report** bugs and issues
- 💡 **Suggest** new features
- 💳 **Donate** via UPI (in-app)

Your support helps maintain the app and build new features!

---

## 🔑 Configuration

### GitHub OAuth (Optional)

To enable GitHub sign-in for increased API limits:

1. Create a GitHub OAuth app at **GitHub → Settings → Developer settings → OAuth Apps**.
2. Copy the **Client ID** from the OAuth app.
3. Update in `GitHubAuth.kt`:

```kotlin
private const val CLIENT_ID = "your_client_id_here"
```

---

## ⚠️ Disclaimer

RepoStore only helps you discover and download release assets that are already published on GitHub by third-party developers.

The contents, safety, and behavior of those downloads are entirely the responsibility of their respective authors and distributors, not this project.

By using RepoStore, you understand and agree that you install and run any downloaded software at your own risk. This project does not review, validate, or guarantee that any APK is safe, free of malware, or fit for any particular purpose.

---

## 👨‍💻 Author

<div align="center">

**Samyak Kamble**

[![GitHub](https://img.shields.io/badge/GitHub-samyak2403-181717?style=for-the-badge&logo=github)](https://github.com/samyak2403)

</div>

---

## 📄 License

RepoStore is released under the **MIT License**.

```
MIT License

Copyright (c) 2026 Samyak Kamble

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

<div align="center">

**Made with ❤️ in India**

![Visitors](https://visitor-badge.laobi.icu/badge?page_id=samyak2403.RepoStore)

</div>
