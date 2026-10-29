# Changelog

All notable changes to the **RepoStore** project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.22] - 2026-10-29
### Added
- **Multi-Part Downloader Fallbacks**: Added automatic cascading fallback from HEAD/Range inspection to single-part GET streaming when servers or proxies do not support pre-flight probes.
- **Mirror Proxy Resilience**: Added automatic fallback to direct GitHub URLs when a configured mirror proxy is unreachable or times out.
- **Modern Storage Compatibility**: Migrated downloaded APK storage to app-specific directories, fully supporting scoped storage across Android 10 to Android 16.
- **DownloadManager Fallback**: Integrated system `DownloadManager` safety net in `AppInstaller` if parallel downloader fails.

### Fixed
- Fixed fatal "Failed to get file info" abort error when clicking the Install button.
- Added missing `User-Agent` and `Accept` headers to OkHttp downloader client to avoid HTTP 403 Forbidden responses.
- Fixed multi-part progress throttling logic.
- Refined Material 3 settings theme cards and searchable language dialog color styling.

---

## [1.0.21] - 2026-10-22
### Added
- **Play Store UI/UX Redesign**: Reworked app details layout with Google Play Store styling, structured metadata sections, and similar apps recommendation shelf.
- **Themed Launcher Icon**: Added dynamic monochrome launcher icon support for Android 13+ material theming (PR #40 by SpencerJung).
- **Ukrainian Localization**: Added complete Ukrainian (`uk`) language support (PR #44).
- **Background Update Notification Service**: Automated background update checks with `WorkManager`, new dedicated App Updates screen, and settings controls.
- **Dynamic Donation QR Code**: Integrated UPI dynamic QR code generator in profile and donation settings.
- **Rotating VirusTotal Key Pool**: Implemented rotating API key pool for resilient APK security scanning.

### Fixed
- Fixed bottom navigation bar overlap on smaller display aspect ratios.
- Fixed version update detection edge cases and concurrency issues in download manager.
- Corrected typos and spelling in UI string resources.

---

## [1.0.20] - 2026-10-18
### Fixed
- **F-Droid Reproducible Build**: Fixed F-Droid reproducible build verification by ensuring empty VirusTotal key defaults in release builds and excluding baseline profiles.

---

## [1.0.19] - 2026-10-15
### Added
- **VirusTotal APK Security Verification Badge**: Real-time hash analysis and safety score display directly on app detail pages.
- **Trendshift Badge**: Added Trendshift recognition badge in project documentation.
- **Download Statistics Counter**: Real-time release download counter across all GitHub releases.

---

## [1.0.18] - 2026-10-10
### Added
- Official community Telegram channel link in Settings.
### Changed
- Refined Settings UI and about dialogs.

---

## [1.0.17] - 2026-10-05
### Changed
- Bumped version code to 18 (1.0.17).
- Updated open-source contributing guidelines and issue templates.

---

## [1.0.16] - 2026-09-28
### Added
- **Multi-language Support**: Added support for Hinglish and Marathi (`mr`) localizations.
- **Searchable Language Dialog**: Fast in-app language picker with real-time search filtering.

---

## [1.0.14] - 2026-09-20
### Fixed
- Skip v1.0.13 release artifacts to address reproducible build failure reported in Issue #21.

---

## [1.0.13] - 2026-09-15
### Added
- Database-backed installed app detection using Room database mapping.
### Changed
- Improved APK installation flow and FileProvider URI handling.

---

## [1.0.12] - 2026-09-08
### Added
- **App Theme Selector**: User preference for Light, Dark, and System Default themes.
- Redesigned Settings screen with segmented preference cards.

---

## [1.0.11] - 2026-09-01
### Added
- **Manage Apps Section**: Dedicated installed applications management view in Settings.
- **Installed Favorites View**: `MyAppsActivity` displaying only installed favorite applications with `PackageManager` synchronization.
- Dark mode adaptive favorite app icons.
### Changed
- Renamed internal favorites activity from `MyAppsActivity` to `FavoriteActivity`.

---

## [1.0.10] - 2026-08-20
### Fixed
- Fixed search queries dropping Android platform filters and pagination offsets.
- Added preliminary VirusTotal security verification infrastructure.
