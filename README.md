# Hollow
1. Push all files to a GitHub repository and open the **Actions** tab to run the `Android Debug APK CI` workflow, or build locally with JDK 17 and Gradle 8.7 using `gradle assembleDebug`.
2. Download the `hollow-debug-apk` artifact (`app-debug.apk`) from the completed GitHub Actions run or from `app/build/outputs/apk/debug/app-debug.apk`.
3. Enable "Install unknown apps" on your Android device (Android 8.0 / API 26+) or run `adb install -r app-debug.apk`.
4. Launch **Hollow** and tap **Add Video Folder** to grant Storage Access Framework (SAF) read access to any local directory, including hidden `.nomedia` folders.
5. Tap any video thumbnail to expand smoothly into the vertical Shorts player.
