# Development Journal

## Software Stack

- **Platform**: Android (Java or Kotlin — TBD)
- **Build**: Gradle 8.8, JDK 17 (Temurin)
- **CI/CD**: GitHub Actions — nightly signed APK release on every push to `main`
- **Signing**: Keystore stored as base64-encoded GitHub secret; decoded at build time, never committed

## Key Decisions

### No gradle-wrapper.jar in git
Binary blobs in version control are a maintenance burden. The CI workflow generates the wrapper at build time with `gradle wrapper --gradle-version=8.8`, pinning the version without storing the binary.

### Rolling `nightly` pre-release tag
The nightly workflow deletes and recreates the `nightly` GitHub Release on every successful build, so there is always exactly one pre-release available. No tag accumulation, no manual cleanup.

### WRITE_SETTINGS permission model
`Settings.System.SCREEN_BRIGHTNESS` requires `WRITE_SETTINGS`, which is not grantable via the standard runtime-permission flow — the user must be sent to the system Settings screen (`Settings.ACTION_MANAGE_WRITE_SETTINGS`). This UX quirk needs to be handled at first launch.

## Core Features

- Foreground service that persists across app close and starts on boot
- On AC: brightness cap lifted to 100%, screen turned on
- On battery: brightness capped at configurable % (default 80%), optional auto-off timer after configurable idle minutes
- No UI beyond the persistent notification
