# Custom Wallpaper (Android 16)

Android Studio project for selecting a custom image, previewing it, setting it as a static wallpaper, or activating a live wallpaper with launcher horizontal-offset parallax.

## Features
- Uses Android's system document picker (`OpenDocument`) to select an image without broad gallery permission.
- Requests a persistable read grant so the selected image remains available to the wallpaper service.
- Image preview and static wallpaper action.
- Live wallpaper service that shifts the image horizontally based on launcher wallpaper offsets.

## Build
1. Open this folder in a recent Android Studio version.
2. Install Android SDK Platform 36 if prompted and allow Gradle sync.
3. Select **Build > Build APK(s)**.
4. Debug APK normally appears at `app/build/outputs/apk/debug/app-debug.apk`.

## Important limitations
- This project source has not been compiled or device-tested in this environment because Android SDK/Gradle are unavailable here.
- Parallax currently uses launcher horizontal wallpaper offsets. Actual movement depends on launcher support; gyroscope-driven depth motion is not implemented.
- This is a replacement app, not a modification of the original APK.

## Build APK without a laptop (using GitHub Actions)

This project includes `.github/workflows/build-apk.yml`. From a phone:
1. Create a GitHub account or sign in, then create a new **public or private** repository.
2. Upload the contents of the `CustomWallpaper` folder to the repository (keep `.github/workflows/build-apk.yml` in that exact path).
3. Open the repository's **Actions** tab, choose **Build Android APK**, and tap **Run workflow** (or push to `main` to trigger it).
4. When the run completes successfully, open the run and download the `CustomWallpaper-debug-apk` artifact.
5. Extract the downloaded artifact ZIP on your phone; it contains `app-debug.apk`. Install it and allow installation from that source if Android asks.

This produces a debug-signed APK. It is not yet a release-signed app and has not been verified on a physical device. The parallax effect is basic launcher-offset movement; sensor-based depth effects are not included.
