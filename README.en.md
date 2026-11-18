# Xiaoman

[简体中文](README.md) | English

**Xiaoman** is a **local-first** multi-platform app (Android / Web / Desktop) for tracking the things you own. It records assets, wishlists and daily cost, so the value of every item stays visible. All features are available, with no membership screens or paywalls; data stays on your device by default.

- Package: `com.xiaoman.app` — Version 1.0.0 (versionCode 1)
- Minimum OS: Android 7.0 (API 24), portrait UI for tablets and phones
- Stack: Kotlin UI + Rust core; Web uses Vite/TypeScript + WASM; Desktop uses Tauri

## Platforms

- **Android**: Kotlin UI + Rust core (available now)
- **Web**: Vite + TypeScript consuming the Rust core via WASM
- **Desktop**: Tauri shell loading the web build
- Business logic (data, backup, brand matching, cloud sync) lives in Rust; each platform only provides UI and system integration

## Features

- **Assets & wishes**: total price, daily cost, days in service; retired / sold / excluded-from-total states
- **Categories & tags**: unlimited, drag to reorder, rename, delete
- **Details**: notes, cover and note images, second-hand value with residual deduction, expiry reminders, app lock
- **Insights**: daily-cost chart with category and status filters
- **Brands & models**: 70+ brands and 2000+ models across digital, cars and bicycles, grouped by product line; automatic brand and icon detection
- **Backup & restore**: import and export encrypted `.ias` backups (WinZip AES-256)

## Download & install

Build from source for now (no APK is shipped with the repository):

```bash
git clone git@github.com:HanYunChenLuo/xiaoman.git
cd xiaoman/apps/android
echo "sdk.dir=/path/to/android-sdk" > local.properties   # or export ANDROID_SDK_ROOT
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Requirements:

- JDK 17+ (developed on JDK 27)
- Android SDK Platform 34 and Build-Tools 34, NDK 30
- Rust (`aarch64-linux-android` target) and the Gradle wrapper

Web / Desktop:
```bash
cd web && npm install && npm run dev      # web dev
npm run build                             # web build
cd ../desktop/src-tauri && cargo build    # desktop (build web first)
```
Requires Node 22+, Rust `wasm32-unknown-unknown` and the `wasm-bindgen` CLI.

Signing: if `replica.keystore` exists at the repository root and `local.properties` provides `signing.storePassword`, `signing.keyPassword` and `signing.keyAlias`, both debug and release builds are signed with it so `adb install -r` can upgrade in place. Neither the keystore nor the passwords are committed; without them the release APK is unsigned.

## Brand & model data

Brands, aliases and models are remotely updatable data. The single source of truth is [`data/brands.json`](data/brands.json) — updates do not require a new app release.

```json
{
  "schema": 1,
  "dataVersion": 20261119,
  "dataVersionName": "2026.11.19",
  "updatedAt": "2026-11-19",
  "supportedApp": { "minVersionCode": 40, "maxVersionCode": 0 },
  "brands": { "digital": [], "car": [], "bike": [] }
}
```

- Brand entry: `[displayName, id, aliases[], [[productLine, [models...]], ...]]`
- `schema`: data-structure version; the app ignores data with a higher schema
- `supportedApp`: app version range allowed to consume the data (`maxVersionCode: 0` means unlimited)
- On launch the app fetches via the GitHub Contents API (falling back to raw), validates the version range, and applies newer `dataVersion` immediately. Manual check: Settings → Data → Brand & model data
- At build time the `syncBrandData` task copies `data/brands.json` into assets as the offline fallback

## Project layout

```
apps/android/        # Android project (Kotlin UI + Gradle)
core/                # Rust core (calc, backup, JNI/WASM exports)
web/                 # Web client (Vite + TypeScript + WASM)
desktop/             # Desktop shell (Tauri)
data/brands.json     # brand/model data (remote update entry point)
AGENTS.md            # development conventions
```

## Data & privacy

- Android stores data locally (SharedPreferences + app-private storage); web uses localStorage; desktop reuses web storage
- No accounts or cloud sync by default; nothing is uploaded (GitHub/Google sign-in and sync are upcoming)
- Backups are exported by the user as encrypted `.ias` files that can be exchanged with compatible backup tools

## Third-party assets & trademarks

- Brand logos and trademarks belong to their respective owners and are used for local personal display only; some logos come from Simple Icons, worldvectorlogo, logo.wine and official brand sites
- Item icons are rendered locally from macOS Apple Color Emoji and are for personal local use only — see `app/src/main/assets/apple/NOTICE.txt`
- This project is provided for learning and personal use
