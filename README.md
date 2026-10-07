# Aergis — Air Gesture Control

Aergis is an Android air-gesture control application. The repository is the active product source and is maintained as an independently buildable project.

## Project contract

| Property | Current value |
|---|---|
| Package | `com.airgesture.control` |
| Version | *update with each build* |
| Version code | *update with each build* |
| Minimum SDK | `26` |
| Compile / target SDK | `36` |
| Java | `17` |
| Android Gradle Plugin | `9.4.0` |
| Gradle | `9.8.0` via the checked-in wrapper |
| Kotlin | `2.4.20` |
| CameraX | `1.6.2` |
| Compose BOM | `2026.06.00` |
| AndroidX Activity | `1.13.0` |
| AndroidX Lifecycle | `2.10.0` |
| AndroidX Core KTX | `1.18.0` |
| CI | GitHub Actions |

The version values above describe the current project configuration. **They are not the definition of the development baseline.**

## Authoritative baseline

The baseline is always the **newest successful APK-producing Aergis CI build on `main`**.

GitHub reference:

`baseline-successful-apk`

That reference is advanced automatically only after the CI workflow has successfully:

1. run unit tests;
2. run Android lint;
3. assembled the debug APK;
4. assembled the minified release APK;
5. verified the debug APK package and version;
6. verified the release APK exists;
7. generated APK SHA-256 checksums; and
8. completed the baseline-reference update.

This means a failed or cancelled build cannot silently become the new functional reference.

### What this means for development

- Always resolve `baseline-successful-apk` before making functional changes.
- Compare new work against that commit.
- Do not use an old APK, commit number, or historical “R-number” as the baseline.
- A newer successful `main` build automatically becomes the new baseline.
- Pull-request builds are validation builds; they do not replace the `main` baseline.

## Current application state

The project includes:

- Compose-based launcher UI
- persistent action mappings and pointer-mode state
- accessibility-service gesture injection and global navigation
- CameraX-based foreground camera capture
- runtime and session policy models
- MediaPipe-based vision processing
- unit tests
- GitHub Actions CI
- debug and minified release APK artifacts
- APK verification and SHA-256 reporting

## Build and verification

The CI environment uses Java 17, Android SDK 36, and the checked-in Gradle 9.8.0 wrapper. Run `./gradlew` for reproducible local builds; AGP 9 uses built-in Kotlin support and the project keeps the Compose compiler plugin aligned with Kotlin 2.4.20. AndroidX, CameraX, Lifecycle, Core KTX, and Compose dependencies remain on their upgraded stable lines.

The workflow is defined in:

`.github/workflows/ci.yml`

A successful CI run produces:

- debug APK
- minified unsigned release APK
- APK SHA-256 checksums
- test and lint reports
- machine-readable baseline metadata

The APK artifact name is tied to the commit SHA, making the build traceable to its source revision.

## Development workflow

1. Resolve the current `baseline-successful-apk` reference.
2. Make one controlled change.
3. Run tests and relevant validation locally when practical.
4. Push to `main` only when the change is ready for CI validation.
5. Treat the new commit as the baseline only after the complete Aergis CI workflow succeeds.
6. If CI fails, continue from the last successful baseline rather than treating the failed commit as a new reference.

## Repository rule

Do not replace the working implementation with unrelated historical Aergis repositories or assume that a remembered APK represents the current product state. The current repository plus its verified CI baseline is the source of truth.

## Installing preview updates

Use the **debug APK** from Aergis CI for device testing. Debug APKs use the
permanent public preview identity in `ci/aergis-preview.keystore`; CI verifies
its certificate before uploading an APK. Its password and key password are
`android`, and its alias is `androiddebugkey`. This is intentionally a public
test key, **never a production/release signing key**. Keep it unchanged across
preview builds. Release artifacts remain unsigned and require a separate
private production signing key.

CI preview version codes are `1000 + GITHUB_RUN_NUMBER`. Install newer CI builds
over older ones to retain app data, calibration and permissions; local builds
use version code 1000 and cannot replace a newer CI APK normally. Re-running the
same CI run retains the same version code and signing identity.

Previously generated CI debug keys were different on each clean runner. If the
currently installed APK uses one of those old keys, one final uninstall/install
is needed to enter the permanent preview update chain. An old APK certificate
alone cannot recover its private signing key.

When returning from diagnostics sharing or saving, Aergis refreshes foreground
safety when its own window actually regains focus. It does not automatically
arm control or enable gesture actions. Protected screens still block actions.
