# Waylink

**Your bike's display, connected.** A free companion app for Bajaj motorcycles with the Bluetooth digital cluster (Pulsar and Dominar): turn arrows, caller names and the time on the cluster, plus rides, fuel, service reminders, documents and an SOS card. No account, no cloud.

This repository holds every Waylink app:

| Folder | What it is | Status |
|---|---|---|
| [`web-v0/`](web-v0/) | The original web app. **The design reference** for the native apps: compare new screens against it. Also tagged `v0`. | Live at https://krishnaar21.github.io/Waylink/ |
| [`android/`](android/) | Native Android app (Kotlin, Jetpack Compose), a faithful port of v0. | In progress |
| `web/` | Dashboard and analytics, synced from the phone apps. | Later |
| `ios/` | Native iPhone app. | Later |

## Android

```
cd android
./gradlew :core:test           # shared data and maths, tested against the web app's numbers
./gradlew :app:assembleDebug   # build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17 and the Android SDK (Android Studio has both). Every push that touches `android/` builds a debug APK in GitHub Actions; download it from the run's *Artifacts*.

- `android/core`: plain Kotlin. The data model (same JSON as the web app's backup file, so backups move between them), fuel, service and trip maths, sample data. Built to be shared with the dashboard and iOS later.
- `android/app`: the Compose UI. Colours, type and spacing come from v0's computed styles; icons are generated from v0's icon set by `android/tools/icons_to_vectors.py`.

## Web v0

See [`web-v0/README.md`](web-v0/README.md). Pushing changes under `web-v0/` to `main` redeploys GitHub Pages.
