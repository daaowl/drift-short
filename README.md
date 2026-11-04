# Vanta (v1.20 — Version Code 21)

Complete native Android source implementing the `Vanta v1.19` handoff specification (`package com.vanta.player`, `minSdk = 28`, `targetSdk = 35`, `versionCode = 21`, `versionName = "1.20"`).

## Build & Install Instructions
1. **GitHub Actions (Automated)**: Push the contents of this archive to a GitHub repository and run the `Build Vanta Signed APK` workflow in the **Actions** tab, then download `vanta-v1.20-debug-apk` (`app-debug.apk`).
2. **Local CLI Build**: With JDK 17 and Android SDK installed, run `gradle assembleDebug` (using Gradle 8.7) to produce `app/build/outputs/apk/debug/app-debug.apk`.
3. **Signing Key Note**: Because no existing v1.19 release keystore binary was attached, this build signs with the standard Android debug keystore. Installing over a differently-signed existing `com.vanta.player` build requires signing with your original release keystore or uninstalling the previous signature first.
