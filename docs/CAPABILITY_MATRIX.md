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
| Protected contexts | Package/class-token policy; unknown contexts blocked | Protected-screen blocking inferred/observed | Validate sensitive-screen coverage across apps/devices; node retrieval is enabled separately for navigation | High | Safety tests; device campaign | baseline |
| Action dispatch | Final safety and epoch checks before queued main-thread dispatch; outcome telemetry | Action feedback observed | Preserve invalidation and distinguish submitted/completed input from target activation | Critical | Race tests; service/device evidence | device-unverified |
| Runtime state/UI | StateFlow-backed launcher status and controls | Multi-screen setup/practice/diagnostics workflow | UI must reflect actual runtime state; add workflow incrementally | Medium | Compose/UI tests where practical; device | proposed |
| Replay/diagnostics | Local recording/export/recovery, DevLoop correlation, persistence, relay and replay tooling | Technical console/benchmark workflow observed | Validate exact-build event correlation and bounded overhead on devices | Medium | JVM/Python tests; device evidence | device-unverified |
| CI/CD | Tests, lint, debug/release build, APK verification, CodeQL | Not applicable | Preserve gates and add benchmark/security checks only when deterministic | Medium | GitHub Actions | baseline |
| Privacy/permissions | Camera, foreground service, notifications, audio/settings controls and accessibility window-content retrieval; no Internet permission; cleartext disabled | Prototype permissions not authoritative | Disclose node-tree navigation and optional camera-image recordings; preserve protected/unknown-context checks | Critical | Manifest/service audit; CodeQL; CI | baseline |

Device-motion cancellation was removed at user request on 2026-10-16. Gyroscope and accelerometer availability are not requirements and do not gate control.
