# Aergis Capability Matrix

Status values are **baseline**, **proposed**, **device-unverified**, **deferred**, or **complete**. Prototype observations are not treated as exact implementation requirements unless independently verified.

| Capability | Current `main` | Prototype reference | Target behavior | Risk | Test/evidence | Status |
|---|---|---|---|---|---|---|
| Camera capture | CameraX latest-frame analysis; image closed in `finally` | Visible live tracking workflow | Preserve cleanup and add freshness telemetry | Medium | Unit policy tests; CI; device campaign | baseline |
| MediaPipe recognition | On-device GestureRecognizer with GPU-to-CPU fallback | Gesture recognition and tracking | Preserve model integrity and expose result health | Medium | Model checksum; CI; device traces | baseline |
| Pointer mapping | Mirrored normalized mapping with active bounds `0.02..0.98` | Calibration/profile workflow observed | Versioned calibration with safe fallback and display/orientation validation | High | Mapper tests; migration tests; device evidence | proposed |
| Pointer smoothing | Landmark smoothing plus adaptive alpha-beta filter | Tracking/filter diagnostics observed | Keep one bounded low-latency path and measure jitter/latency | High | Replay traces; device campaign | proposed |
| Click | Click hysteresis and accessibility tap dispatch | Pointer/click workflow observed | Require stable engagement, release-before-rearm, final safety validation | High | State-machine tests; device evidence | baseline |
| Long press | Accessibility implementation exists; mapping UI exists | Prototype action vocabulary reference | Explicitly validate duration and cancellation semantics | High | Unit policy; device test | device-unverified |
| Swipe scroll | Windowed directional detector and accessibility scroll | Broad swipe workflow observed | Add center-gate/deliberate initiation only if replay evidence improves safety | High | Replay precision/recall; device campaign | proposed |
| Static gestures | Mapped classifier gestures with transaction gate | Back/Home/Recents and other mappings observed | Keep configurable mappings; no hard-coded universal defaults | High | Mapping tests; false-positive campaign | baseline |
| Hand ownership | Continuity, preference, ambiguity rejection, velocity prediction | Tracking/hand selection workflow observed | Validate crossings, occlusion, scale changes on devices | High | Ownership tests; physical campaign | device-unverified |
| Motion cancellation | Gyro/accelerometer cancellation with hysteresis | Tracking safety behavior observed | Measure thresholds and escape rate on devices | High | Safety tests; motion campaign | device-unverified |
| Protected contexts | Token-based protected/unknown fail-closed policy | Protected-screen blocking inferred/observed | Expand evidence-backed coverage without window-content retrieval | High | Safety tests; device campaign | baseline |
| Action dispatch | Accessibility service final safety check, queued main-thread dispatch | Action feedback observed | Add epoch/session invalidation and dispatch outcome telemetry | Critical | Race tests; service/device evidence | proposed |
| Runtime state/UI | StateFlow-backed launcher status and controls | Multi-screen setup/practice/diagnostics workflow | UI must reflect actual runtime state; add workflow incrementally | Medium | Compose/UI tests where practical; device | proposed |
| Replay/diagnostics | Existing status/error fields; no first-class trace format | Technical console/benchmark workflow observed | Versioned, bounded, privacy-safe replay reports | Medium | JVM codec/invariant tests | proposed |
| CI/CD | Tests, lint, debug/release build, APK verification, CodeQL | Not applicable | Preserve gates and add benchmark/security checks only when deterministic | Medium | GitHub Actions | baseline |
| Privacy/permissions | Camera, foreground service, notifications; no cleartext; no window content | Prototype permissions not authoritative | Keep minimum permissions and fail closed | Critical | Manifest audit; CodeQL; CI | baseline |
