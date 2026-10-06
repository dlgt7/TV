# TV

<p align="center">
  <img src="other/image/logo.svg" width="128" alt="TV Logo">
</p>

[简体中文](README.md) | English

An Android media app based on [FongMi/TV](https://github.com/FongMi/TV) and [CatVod](https://github.com/CatVodTVOfficial/CatVodTVJarLoader). It supports both **Android TV** and **Android phones**, and can extend VOD, live TV, parsers, danmaku, subtitles, and spiders through external configuration.

[Discussion Group](https://t.me/fongmi_official) | [Release Channel](https://t.me/fongmi_release)

[![Star History Chart](https://api.star-history.com/svg?repos=wobuhui666/TV&type=Date)](https://www.star-history.com/#wobuhui666/TV&Date)

---

## Contents

- [Project Structure](#project-structure)
- [Disclaimer](#disclaimer)
- [What This Fork Changes](#what-this-fork-changes)
- [Player](#player)
- [VOD](#vod)
- [Live TV](#live-tv)
- [Spider Engine](#spider-engine)
- [Networking](#networking)
- [DLNA Casting](#dlna-casting)
- [Android Auto](#android-auto)
- [Remote Control](#remote-control)
- [Configuration](#configuration)
- [Build Commands](#build-commands)
- [Further Reading](#further-reading)

---

## Disclaimer

This project is provided only for learning, research, and lawful personal use. It does not include, store, or distribute any media content, live TV sources, parser endpoints, or spider rules. External configurations, third-party APIs, and returned content are added and managed by users, who are responsible for related copyright, compliance, and usage risks.

Use this project only in compliance with applicable laws and copyright requirements, and support legitimate content sources.

---

## Project Structure

| Item | Value |
| --- | --- |
| package | `com.fongmi.android.tv` |
| minSdk | 24 (Android 7.0 Nougat) |
| abi | `arm64-v8a`, `armeabi-v7a` |
| flavor | `leanback` (TV), `mobile` (phone) |

```text
TV/
├── app/            Main app module with leanback and mobile UI flavors
├── catvod/         Spider abstraction layer, Spider API, and OkHttp stack
├── quickjs/        QuickJS JavaScript engine
├── chaquo/         Chaquopy Python engine
├── docs/           Configuration, Spider, local API, and live-source docs
└── Release/apk/    Output directory for CI or local release artifacts
```

Shared application logic lives in `app/src/main/`. TV-specific UI lives in `app/src/leanback/`, and phone-specific UI lives in `app/src/mobile/`.

---

## What This Fork Changes

This repository is the `wobuhui666/TV` maintained fork. The main work is focused on TV UI, mpv playback, subtitles and danmaku, spider compatibility, and build reliability.

### 1. JetStream-Style TV UI

- Added and refined a JetStream-style TV experience, covering the home screen, detail pages, playback controls, push screen, site selector, line selector, episode selector, speed dialog, UA dialog, subtitle dialog, and related sheets.
- Reworked many leanback layouts with `JetStream*` custom views, focus states, animations, rounded shapes, shadows, card ratios, poster metadata pills, and playback control styling.
- Switched TV controls and dialogs to Material Symbols Rounded icon assets for a more consistent large-screen visual system.
- Improved remote-control interaction with long-click command handling, focus movement, dialog dimensions, framed attributes, and safer margins for different dialogs.
- Added `FeaturedPosterCache` to reduce duplicate artwork requests and make poster loading more stable.

### 2. Player And mpv Improvements

- Enhanced the mpv playback path with file-loaded state tracking, first-frame render flags, seek handling, logging, and HTTP header forwarding.
- Added mpv settings for viewing, editing, and importing `mpv.conf`, plus Vulkan, `gpu-next`, and Anime4K shader level options.
- Added `MpvAnime4K` support for anime upscaling and sharpening use cases.
- Fixed and improved playback edge cases, including long external-player positions, repeat-control stack overflow, orientation handling, episode position normalization, and preserving the current episode when reversing episode order.
- Added control over top subtitle information visibility so the top playback info and subtitles can be adjusted separately.

### 3. Subtitles And Danmaku

- Reworked subtitle dialog layouts and controls, including subtitle size up/down, move up/down, and reset actions for both TV and mobile workflows.
- Added Logvar danmaku API support. Users can configure a Logvar root URL, match by playback URL or title and episode, and fall back to the legacy danmaku API when needed.
- Added unit tests for Logvar base URL normalization, request URL construction, episode matching, and response parsing.
- Kept remote subtitle and danmaku injection through the local HTTP API.

### 4. Spider, Parser, And Script Compatibility

- Added a drpy compatibility layer for QuickJS and adapted common helpers such as `pdfh`, `pdfa`, `pd`, and `pdfl`, making old scripts easier to migrate.
- Added `jsoup` and drpy-related parsing implementations for more stable HTML parsing.
- Enhanced `Connect` content handling and script patching so external scripts can be adjusted at runtime for compatibility.
- Integrated QuickLog for JavaScript spider logs, parser diagnostics, and failure investigation.

### 5. Networking, Data, And UX Fixes

- Improved HTTP response handling and playback fallback behavior when parser, line, or source switching fails.
- Adjusted hot-search and TMDB proxy endpoints to reduce breakage caused by upstream API changes.
- Fixed multiple large-screen sizing, progress indicator, card shape, dialog closing tag, spacing, and text-style issues.
- Added a crash-log copy action to make device-side error collection easier.

### 6. Build And Tests

- Added release APK build and signing workflows for `arm64-v8a` and `armeabi-v7a`.
- Aligned Gradle, AGP, AndroidX, and local Media3 composite build configuration to reduce local build differences.
- Added `testInstrumentationRunner`, JUnit dependencies, and focused tests for player state, playback, and danmaku behavior.

---

## Player

- **Core**: ExoPlayer (Media3) + FFmpeg software decoding, with automatic hardware/software fallback.
- **mpv**: mpv playback, config editing, Vulkan, `gpu-next`, Anime4K, playback logs, and HTTP headers.
- **Rendering**: SurfaceView / TextureView.
- **DRM**: Widevine, PlayReady, and ClearKey with `#KODIPROP` support.
- **Danmaku**: DanmakuFlameMaster synchronized with the playback timeline, with remote push and Logvar matching.
- **Subtitles**: External SRT / SSA / ASS subtitles, system CaptioningManager, and remote real-time injection.
- **Other**: Playback speed, aspect scaling, Picture-in-Picture, background audio, and intro/outro skipping.

---

## VOD

- Multi-site category browsing with filters for year, region, type, and more.
- Parallel multi-site search, with automatic Traditional-to-Simplified keyword conversion for better compatibility.
- Automatic fallback when playback fails: parser -> line -> search other sites -> next site.
- Watch history retained for 60 days, favorites, and incognito mode.
- TV flavor supports remote-control navigation. Mobile flavor supports gestures for brightness, volume, progress, episode switching, rotation, and screen locking.

---

## Live TV

- Supports M3U, TXT (`#genre#` grouping), and JSON live-source formats.
- **EPG**: XMLTV with `.gz` support and automatic refresh every 6 hours.
- **Catch-up / time shift**: supports `append`, `pltv`, and other types.
- Channel favorites and password-protected hidden groups.
- Special engines: TVBus and ForceTech.

---

## Spider Engine

Spiders can be written in three languages:

- Java JAR (DexClassLoader)
- JavaScript (QuickJS)
- Python (Chaquopy)

Use the `api` field to select the spider and the `ext` field to pass initialization parameters. See [SPIDER.md](docs/SPIDER.md) for the full API contract.

---

## Networking

- **DoH**: DNS over HTTPS with Bootstrap IP support.
- **Proxy**: HTTP / HTTPS / SOCKS4 / SOCKS5 with host-regex based routing.
- **Hosts**: DNS override with `*` wildcard support.
- **CORS injection**: inject custom response headers by host rule.
- **Ad blocking**: block matched domains through the `ads` blacklist.
- **WebView sniffing**: intercept media URLs with regex-based sniffers and custom UA support.

---

## DLNA Casting

- **DMC (controller)**: the mobile flavor can scan LAN DLNA devices and cast media.
- **DMR (renderer)**: the TV flavor can work as a DLNA renderer and receive casts from other devices.

The app uses JUPnP 3.0.4 (UPnP), supports play / pause / stop / seek / next / repeat controls, and can forward custom HTTP headers such as User-Agent and Referer to the target stream.

---

## Android Auto

The TV flavor supports Android Auto. `PlaybackService` implements `MediaLibraryService`, allowing car head units to browse watch history and live channels:

- **VOD**: resume history items from the last position.
- **Live TV**: browse channels by group and play directly.
- **Playback controls**: play / pause / prev / next / stop from the car UI.
- **Lazy loading**: Auto can stay connected after the app exits, and configuration reloads automatically.

---

## Remote Control

When the app starts, it binds a local HTTP server (NanoHTTPD). It scans ports from **9978** to **9998** and exposes playback control, subtitle / danmaku push, multi-device sync, and related actions. See [LOCAL.md](docs/LOCAL.md) for the full endpoint list.

---

## Configuration

VOD configuration is the main entry point. It can be loaded from a URL or local path, with top-level fields including:

- VOD sites (`sites`) and parse rules (`parses`)
- Live sources (`lives`)
- Network settings (`doh`, `proxy`, `hosts`, `ads`)
- Danmaku settings (`danmaku`, `logvar`)

Live configuration can be embedded or stored separately. See [CONFIG.md](docs/CONFIG.md) for all fields.

---

## Build Commands

```bash
chmod +x ./gradlew
./gradlew :app:assembleLeanbackArm64_v8aDebug
./gradlew :app:assembleMobileArm64_v8aDebug
./gradlew :app:testLeanbackArm64_v8aDebugUnitTest
```

To reproduce the CI release build:

```bash
./gradlew assembleLeanbackArm64_v8aRelease assembleLeanbackArmeabi_v7aRelease --no-daemon --build-cache --max-workers=2
```

Release builds require JDK 21, Python 3.10, Android SDK 37, signing values in `local.properties`, and `MEDIA3_SOURCE_DIR` when using the local Media3 composite build. See [LOCAL_BUILD_ENV.md](LOCAL_BUILD_ENV.md) for more local environment details.

---

## Further Reading

| Document | Description |
| --- | --- |
| [CONFIG.md](docs/CONFIG.md) | Full Vod / Live configuration field reference |
| [SPIDER.md](docs/SPIDER.md) | Spider method contract and response formats |
| [LOCAL.md](docs/LOCAL.md) | Full local HTTP API endpoint reference |
| [LIVE.md](docs/LIVE.md) | Live-source format reference |
