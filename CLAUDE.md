# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android foreground service app for motorcycle navigation tablets. Manages screen brightness and power based on AC state:

- **On AC**: auto-brightness allowed up to 100%, screen turned on
- **On battery**: auto-brightness capped at configurable % (default 80%), optional auto-off timer after configurable minutes

Use case: tablets left in sunlight after the motorcycle is turned off drain the battery quickly at full brightness. This app prevents that.

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

- **Foreground Service**: Android `Service` with persistent notification; manages `Settings.System.SCREEN_BRIGHTNESS` and screen on/off state
- **AC detection**: `BroadcastReceiver` on `ACTION_POWER_CONNECTED` / `ACTION_POWER_DISCONNECTED` drives all state transitions
- **Auto-off timer**: scheduled via `Handler.postDelayed` after AC loss; cancelled on reconnect
- **Settings**: brightness cap % and timer duration are user-configurable
- No UI beyond the notification; service starts on boot

## Permissions needed in AndroidManifest

- `WRITE_SETTINGS` (requires user grant via system Settings screen — not a normal runtime permission; check `Settings.System.canWrite()` at startup)
- `FOREGROUND_SERVICE`
- `RECEIVE_BOOT_COMPLETED`
- `WAKE_LOCK` (needed to turn the screen on via `PowerManager`)
