# Aergis — Air Gesture Control

Aergis is an Android air-gesture control application. The repository is the active product source and is maintained as an independently buildable project.

## Project contract

| Property | Current value |
|---|---|
| Package | `com.airgesture.control` |
| Version | `0.10.0-preview` |
| Version code | `10` |
| Minimum SDK | `26` |
| Compile / target SDK | `36` |
| Java | `17` |
| Gradle | `8.11.1` |
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

The CI environment uses Java 17, Android SDK 36, and Gradle 8.11.1.

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
