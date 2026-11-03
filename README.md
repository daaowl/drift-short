# Hollow

A private, offline, Shorts-style player for videos in your own folders, including hidden `.nomedia` folders.

## Get the APK
Every push to `main` builds the app on GitHub Actions. Open the **Actions** tab, pick the latest green run, and download **Hollow-debug-apk**.

The repo includes a fixed debug signing key (`app/debug.keystore`), so each new build installs over the previous one.

## Build locally
Needs JDK 17 and the Android SDK. The Gradle wrapper is generated, not committed:

```
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

## Stack
Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer, DataStore, Storage Access Framework. minSdk 26, targetSdk 34.
