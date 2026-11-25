# Photon Audio

High-resolution galaxy-themed Android music player supporting MP3, FLAC, WAV, Opus, OGG, and M4A with a 10-band studio equalizer, ReplayGain 2.0 / ITU-R BS.1770-4 LUFS loudness normalization, live spectrum visualizer, gapless playback & crossfade curves, album/batch metadata editing, custom themes/wallpapers/fonts, and home screen widgets.

## Download APK from GitHub

1. Open the **Releases** page of this repository and download the latest `PhotonAudio-v*.apk` file under **Assets**.
2. Open the downloaded `.apk` on your Android device to install.

### Automatically Publishing New APK Downloads on GitHub

This repository includes an automated GitHub Actions workflow (`.github/workflows/build-apk.yml`) that builds and attaches the installable Android `.apk` automatically:

- **Automatic Release on Push:** Every push to `main` (including pushing from AI Studio) or any `v*` tag automatically builds `PhotonAudio-v<version>.apk` and publishes/updates the latest **GitHub Release** with the downloadable `.apk` attached.
- **One-Click Manual Release:** Open the **Actions** tab on GitHub, select **Build & Release Android APK**, and click **Run workflow** anytime to rebuild and update the release APK.

> **Optional Release Signing:** Out of the box, the workflow signs the APK using the included debug keystore so it works immediately with zero setup. To sign release builds with your own upload keystore, add `KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD` in your repository's **Settings → Secrets and variables → Actions**.
