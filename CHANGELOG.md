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
