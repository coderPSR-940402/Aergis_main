# Aergis Phase 0 Baseline

**Baseline captured:** 2026-10-04

## Repository identity

| Item | Value |
|---|---|
| Repository | `coderPSR-940402/Aergis_main` |
| Working branch | `feature/aergis-evolution-phase0` |
| Baseline commit | `602a26724728041d18b70838743906a61c0de1dc` |
| `main` ref | `602a26724728041d18b70838743906a61c0de1dc` |
| `baseline-successful-apk` ref | `602a26724728041d18b70838743906a61c0de1dc` |
| Package | `com.airgesture.control` |
| Version | `0.10.0-preview` / version code `10` |
| Minimum SDK | `26` |
| Compile/target SDK | `36` |
| Java | `17` |
| Gradle | `9.8.0` wrapper |
| AGP | `9.4.0` |
| Kotlin | `2.4.20` |

## Baseline CI evidence

The current `main` commit has successful Aergis CI and CodeQL runs:

- Aergis CI: run `37124289111`
- CodeQL: run `37124289155`
- Baseline and `main` point to the same commit.

The authoritative CI workflow verifies unit tests, Android lint, debug APK assembly, minified release APK assembly, APK metadata, and SHA-256 checksums. The sandbox does not provide the project Android SDK, so local Android build results are not substituted for CI evidence.

## Current architecture observed

Aergis is an on-device Android accessibility controller. The principal path is:

```text
CameraX ImageAnalysis
  -> GestureCaptureService
  -> GestureRecognitionEngine
  -> MediaPipe GestureRecognizer
  -> hand ownership / filtering / GestureInterpreter
  -> GestureTransactionStateMachine and ActionSafetyPolicy
  -> AirAccessibilityService
  -> pointer overlay or accessibility gesture dispatch
```

The repository already contains meaningful safety boundaries: explicit `READY`/`ARMED` modes, fail-closed unknown foreground context, motion cancellation, hand ownership arbitration, stable-frame transaction gating, stale-gap reset in the transaction gate, camera image cleanup, bounded vision retry, and release APK model checksum validation.

## Baseline risks requiring measured follow-up

1. The camera-to-result path has no centralized freshness/duplicate/out-of-order telemetry contract.
2. `GestureRecognitionEngine` dispatches through the accessibility service from the recognition path; queued main-thread actions need an explicit session/epoch cancellation contract.
3. Pointer and gesture behavior are implemented, but replayable timestamped traces and benchmark reports are not yet first-class repository artifacts.
4. Prototype calibration, diagnostic, and workflow behavior is not represented by a complete capability matrix in the current `main` branch.
5. Physical-device validation remains unavailable in the repository baseline; claims about camera latency, jitter, IMU thresholds, service interruption, and touch injection remain device-unverified.
6. The current UI exposes a control mode but does not yet provide the full prototype-oriented setup/practice/diagnostics workflow.

## Scope decision for the first implementation slice

The first slice is a **measurement/validation slice**: add a pure JVM-testable vision-result freshness policy and structured reason codes without changing production action mappings. This creates a measurable safety boundary before further prototype recovery.

The slice must preserve current behavior for valid, ordered, fresh results and reject only duplicate, out-of-order, stale, or excessive-gap results. It must include deterministic tests and be integrated at the result-publication boundary before any broader gesture vocabulary expansion.
