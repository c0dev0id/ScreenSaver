# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android foreground service app that caps automatic screen brightness at 80% while on battery and allows 100% when on AC power. Intended for motorcyclists who need brightness-limiting while riding.

## Build Constraints

**Do not build locally.** All builds run in CI/CD (GitHub Actions). The Gradle wrapper jar is intentionally not committed — it is generated during CI via `gradle wrapper --gradle-version=8.8`.

- **Gradle**: 8.8
- **JDK**: 17 (Temurin)
- **Build target**: `./gradlew assembleRelease`

## CI/CD

Every push to `main` triggers `.github/workflows/nightly.yml`, which:
1. Generates the Gradle wrapper
2. Decodes the signing keystore from `SIGNING_KEYSTORE_BASE64` secret into `$RUNNER_TEMP/release.keystore`
3. Builds a signed release APK (`assembleRelease`)
4. Publishes a rolling pre-release tagged `nightly` on GitHub Releases, replacing the previous one

Required GitHub secrets: `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

## Architecture (planned)

- **Foreground Service**: Android `Service` component that keeps a persistent notification and manages `Settings.System.SCREEN_BRIGHTNESS` or the `WindowManager` brightness flag
- **Battery/AC detection**: `BroadcastReceiver` listening for `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` (or `ACTION_BATTERY_CHANGED`) to switch brightness caps
- No UI beyond the notification; the service starts on boot

## Permissions needed in AndroidManifest

- `WRITE_SETTINGS` (requires user grant via system dialog — not a normal runtime permission)
- `FOREGROUND_SERVICE`
- `RECEIVE_BOOT_COMPLETED`
