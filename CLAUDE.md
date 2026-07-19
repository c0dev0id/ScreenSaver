# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Android foreground service app for motorcycle navigation tablets. Manages screen brightness and power based on AC state:

- **On AC**: auto-brightness allowed up to 100%, screen woken up
- **On battery**: auto-brightness capped at configurable % (default 80%), optional auto-off timer after configurable minutes

Use case: tablets left in sunlight after the motorcycle is turned off drain the battery quickly at full brightness. This app prevents that.

## Build Constraints

**Do not build locally.** All builds run in CI/CD (GitHub Actions). The Gradle wrapper jar is intentionally not committed — it is generated during CI via `gradle wrapper --gradle-version=8.8`. There are no tests or linters configured; verification happens by pushing and watching CI.

- **Gradle**: 8.8
- **JDK**: 17 (Temurin)
- **Build target**: `./gradlew assembleRelease`
- **SDK**: minSdk = compileSdk = targetSdk = 34
- **Language**: Kotlin, sources under `app/src/main/kotlin/de/codevoid/screensaver/`

## CI/CD

Every push to `main` triggers `.github/workflows/nightly.yml`, which:
1. Generates the Gradle wrapper
2. Decodes the signing keystore from `SIGNING_KEYSTORE_BASE64` secret into `$RUNNER_TEMP/release.keystore`
3. Builds a signed release APK (`assembleRelease`, minified with ProGuard)
4. Publishes a rolling pre-release tagged `nightly` on GitHub Releases, deleting the previous one first so exactly one nightly exists

Required GitHub secrets: `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. The workflow maps these into the env vars `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, which `app/build.gradle.kts` reads for the release signing config (falling back to a placeholder so configuration doesn't fail without them).

## Architecture

Six classes, one package (`de.codevoid.screensaver`):

- **`BrightnessService`** — the core foreground service (`foregroundServiceType="specialUse"`). Owns all runtime state: registers a `BroadcastReceiver` for `ACTION_POWER_CONNECTED`/`DISCONNECTED`, listens to the light sensor, and runs a 5 s `Handler` tick loop (`BrightnessController.TICK_MS`) that drives `BrightnessController`. The notification shows the live state (smoothed lux → brightness target) and is reposted only when its text actually changes. Started sticky; a start intent with action `ACTION_STOP` stops it (used by both the notification's Stop button and MainActivity).
- **`BrightnessController`** — brightness logic, separated from Android service plumbing. **The app does not use system auto-brightness**: the service forces `SCREEN_BRIGHTNESS_MODE_MANUAL` and the controller implements its own curve — EMA-smoothed lux (`alpha` = user-configurable "reaction speed", applied per 5 s tick toward the latest sensor reading — the EMA must not be event-driven because light sensors stop reporting in static conditions; a catch-up mode boosts alpha 20× when the raw reading stays >2× away from the smoothed value for over 10 s, so permanent light changes settle in under a minute while brief ones stay smoothed) mapped log10-scale to brightness 1–255 (floor of 1, not 5 — the system slider is gamma-corrected, so even small linear values sit visibly high on it) between two user-configurable lux endpoints (`darkLux`, below which brightness is minimum; `brightLux`, above which it is maximum), with the log-fraction raised to gamma 2.2 so *perceived* brightness tracks the curve position, clamped by `capFraction` (1.0 on AC, `Prefs.brightnessCap` on battery). Each `tick()` steps current brightness half the remaining distance toward the target, so the target is reached within a few ticks without hard jumps.
- **`MainActivity`** — settings UI: a live light-sensor readout, plus sliders for the curve's dark point (1–1,000 lx) and light point (100–50,000 lx, capped at the sensor's `maximumRange`; both log-scale), brightness cap (50–100%), reaction speed (EMA alpha 0.025–0.25, log-scale), and auto-off minutes (0 = disabled), plus Start/Stop buttons. On resume it checks `Settings.System.canWrite()` and sends the user to `ACTION_MANAGE_WRITE_SETTINGS` if not granted — `WRITE_SETTINGS` cannot be requested via the normal runtime-permission flow — and then requests a battery-optimization exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) so Doze doesn't throttle the service's tick loop; one flow per resume.
- **`Prefs`** — `SharedPreferences` singleton; call `Prefs.init(context)` before any access (service and activity each do this). The service reads prefs live each time it needs them, so slider changes take effect without restarting the service. Keys: `brightness_cap` (Float 0.5–1.0, default 0.8), `ema_alpha` (Float 0.025–0.25, default 0.075), `dark_lux` (Float, default 10), `bright_lux` (Float, default 50 000), `auto_off_minutes` (Int, default 5).
- **`BootReceiver`** — starts the service on `BOOT_COMPLETED`.
- **`LuxFormat`** — top-level utility function; formats a lux `Float` for display across six orders of magnitude (`"5 lx"`, `"1.2k lx"`, `"50k lx"`). Used in the notification text and the live readout in `MainActivity`.

### Non-obvious mechanics

- **Auto-off** does not force the screen off directly. After the configured minutes on battery, the service temporarily sets `SCREEN_OFF_TIMEOUT` to 1 s, lets the screen time out naturally, then restores the previous timeout 5 s later (also restored on AC reconnect and in `onDestroy`).
- **Screen wake on AC** uses a deprecated-but-functional `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP` wake lock held for 3 s.
- Initial AC state is detected via a null-receiver `ACTION_BATTERY_CHANGED` query, since the connect/disconnect broadcasts only fire on transitions.
