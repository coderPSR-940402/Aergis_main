# Aergis — Stable Product Repository

Aergis is maintained here as a clean, independent product repository.

## Baseline

- Version: `0.10.0-preview`
- Version code: `10`
- Package: `com.airgesture.control`
- Minimum SDK: `26`
- Target/compile SDK: `36`
- Baseline APK: `AirGestureControl-v0.10.0-preview.apk`
- Baseline SHA-256: `7b161e73cd6ac397362c2ee65e3c62bcaa9a14587cc09fd871df7ec72f8ba015`

The supplied APK is the functional reference for this repository. The repository is intentionally independent of historical Aergis repositories; their source is not used as an implementation dependency.

## Current repository state

The project now contains a complete Android application skeleton with:

- Compose launcher UI
- persistent action mapping and pointer-mode state
- accessibility service capable of global navigation and injected gestures
- foreground camera capture service using CameraX
- runtime/session policy models
- unit tests
- reproducible GitHub Actions CI
- debug APK artifact upload

## CI contract

Every push to `main`, pull request, or manual workflow dispatch runs Java 17, Android SDK 36, Gradle 8.11.1, unit tests, and a debug APK build. A successful run must produce `app/build/outputs/apk/debug/app-debug.apk` and upload it as a workflow artifact.

## Development rule

Do not replace the baseline with unrelated repository code. Functional changes should be made incrementally from this repository and verified by CI before the next feature or repair is introduced.
