# MediaSaver Pro

Native Android social media downloader built with Kotlin, Jetpack Compose, Material Design 3, Room, DataStore, Retrofit, and OkHttp, powered by **SaveAPI v1**.

## Features

- **Automatic Platform Detection (13 Platforms):** TikTok, Instagram, Facebook, YouTube, X (Twitter), Snapchat, Pinterest, Threads, Twitch, VK, Likee, Reddit, and 9GAG.
- **SaveAPI v1 Integration:** Resolves public video, image, audio, and carousel links via `GET /v1/download`, plus live account status and remaining credits via `GET /v1/me` and `GET /v1/usage`.
- **Provider Abstraction:** `MediaDownloaderProvider` decouples the UI and download engine from SaveAPI so additional providers can be plugged in easily.
- **Streaming Download Manager:** Real OkHttp downloads with live progress, speed calculation, HTTP `Range` pause/resume, cancel, retry, and completion notifications.
- **MediaStore & FileProvider Library:** Saves finished files to Android `MediaStore` (Videos, Pictures, Music) or app-private storage with search, filtering, sorting, native open/share intents, rename, delete, and metadata inspection.
- **Local Download History:** Persisted locally with Room, supporting search, item deletion, clearing, and one-tap re-download.
- **Bilingual & Adaptive UI:** Full English (LTR) and Arabic (RTL) localization, Dark / Light / System themes, and adaptive Navigation Bar / Navigation Rail layouts.

## Configuring SaveAPI Key

1. Open the **Secrets** panel in AI Studio.
2. Add a secret named `SAVEAPI_KEY` with your SaveAPI Bearer token.
3. Rebuild/run the app. The Secrets Gradle Plugin injects `BuildConfig.SAVEAPI_KEY` into the app automatically.
