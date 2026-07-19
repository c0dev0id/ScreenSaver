# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Initial project skeleton with README
- GitHub Actions nightly release workflow (signed APK, rolling `nightly` pre-release tag)
- Full Android project scaffold: Gradle 8.8 / AGP 8.5.2 / Kotlin 1.9.25, minSdk 34
- `BrightnessController`: EMA low-pass filter on lux readings, log-scale lux→brightness curve, animated stepping toward target
- `BrightnessService`: foreground service managing light sensor, AC state detection, auto-off timer via `SCREEN_OFF_TIMEOUT`, screen wake on AC connect
- `BootReceiver`: starts service on device boot
- `MainActivity`: settings screen with sliders for brightness cap, reaction speed, and auto-off timer
- Live light-sensor readout in the settings screen
- Configurable dark point / light point for the brightness curve: below the dark point (default 10 lx) the screen stays at minimum brightness, above the light point (default 50k lx) it reaches maximum; both on log-scale sliders
- Notification shows the service's live state (smoothed lux → brightness target), reposted only when the text changes
- Battery-optimization exemption requested on launch so Doze doesn't throttle the service on battery
- Light-point slider is capped at the sensor's reported maximum range (`Sensor.maximumRange`, shown next to the live readout); a stored light point above it is clamped so max brightness stays reachable

### Changed
- Reaction speed range shifted 10× slower; the old slowest setting is the new fastest. Stored values from older versions are clamped into the new range
- Tick period raised from 200 ms to 5 s; per-tick EMA alpha rescaled ×25 (0.025–0.25, default 0.075) so wall-clock reaction speeds are unchanged (~20 s time constant at Fast, ~3 min at Slow). Brightness steps half the remaining distance per tick
- Brightness curve is gamma-corrected (log-fraction^2.2): the brightness setting is linear backlight power but perception is ~power^(1/2.2), so previously dim indoor light (40 lx) already looked ~65% bright — perceived brightness now tracks the position between the dark and light points

### Fixed
- Brightness could get stuck (e.g. never dimming to minimum in a dark room): the lux EMA was only advanced on sensor events, but light sensors stop reporting in static conditions. The EMA now advances every 200 ms tick toward the latest reading, making reaction speed time-based (~20 s time constant at Fast, ~3 min at Slow)
- Persistent light changes no longer take many minutes to settle: when the raw reading stays >2× away from the smoothed value for over 10 s, the filter speeds up 20× (catch-up mode) until caught up. Brief changes (shadows, headlights) are still smoothed at the configured reaction speed
- Minimum brightness lowered from 5/255 to 1/255: the Android brightness slider is gamma-corrected, so the old floor of 5 sat at ~20% slider position and immediately overrode any manually set lower brightness — the screen never went truly dim in the dark
