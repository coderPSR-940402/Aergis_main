# Aergis repository and application audit

- **Repository:** `coderPSR-940402/Aergis_main`
- **Audited commit:** `13559604c35bfe0aea6f6cb90bc2ae55b5abc4ad`
- **Branch at audit start:** `main`
- **CI baseline reference:** `baseline-successful-apk` -> `13559604c35bfe0aea6f6cb90bc2ae55b5abc4ad`
- **Audit date:** 2026-11-19
- **Scope:** repository-wide engineering audit and relevant Android application-code audit, followed by authorized implementation of a narrow optimization and camera compatibility fix.

## Executive summary

The repository is a substantial Android accessibility and camera/MediaPipe application with strong deterministic JVM/Robolectric coverage, pinned dependency verification, CodeQL, APK verification, release-signing isolation, and explicit separation between CI evidence and physical-device acceptance. The audit found no P0/P1 issue in the inspected source. It found one confirmed P2 compatibility defect: capture unconditionally required a front camera even though the manifest declared camera features optional. It also confirmed a P2 frame-loop allocation hotspot in hand selection and found a strongly supported P2 Gaze Lab native-lifecycle risk that remains unimplemented pending device/native validation.

Implemented in this candidate:

1. Prefer the front camera but fall back to the rear camera, and fail with an explicit error when neither is available.
2. Reuse hand-observation and pointer-candidate lists in the recognition hot path.
3. Replace per-frame `filter`/`map`/`sorted` candidate processing with bounded scalar scans while preserving ownership, preference, and ambiguity rules.

No performance percentage is claimed because this sandbox has no Android SDK or device. The final proof must come from the Aergis CI workflow and a physical-device acceptance pass.

## Repository-wide audit

| Area | Paths/files | Inspection level | Result | Evidence / limitation |
|---|---|---:|---|---|
| Repository structure and maintainability | 351 tracked files; `app/src`, `tools`, `docs`, `.github` | targeted/full by relevant area | PASS with P2 hotspots | Application, tooling, docs, CI, and release areas inventoried; generated/vendor/binary assets were inspected at integration points rather than semantically reviewed. |
| Build and toolchain | `settings.gradle.kts`, root/app Gradle files, wrapper, `gradle.properties` | full | PASS | Java 17, SDK 36, AGP/Kotlin/Gradle versions and model preparation were inspected. Local Gradle execution was blocked by missing Android SDK. |
| Dependencies and supply chain | `app/build.gradle.kts`, `gradle/verification-metadata.xml`, dependency workflow | targeted/full config | PASS | Dependencies are pinned through version declarations and strict verification metadata; CI has a checksum-tamper probe. Fresh dependency resolution was not run locally. |
| Tests and quality gates | 90 Python tooling tests; 122 Kotlin test files; CI workflow | full config, baseline Python execution | PASS / BLOCKED locally | Python suite passed: 90 tests. Gradle tests could not start because `ANDROID_HOME`/`local.properties` SDK was unavailable. |
| CI/CD and release | `.github/workflows/*`, `docs/PRODUCTION_RELEASES.md`, signing scripts | full | PASS with administrative follow-up | Actions use pinned revisions; debug/release APK verification, checksums, CodeQL, and isolated signing smoke are wired. Production release remains manual and draft-only. |
| Security and privacy | manifest, FileProvider paths, accessibility provider, signing workflow, ProGuard | targeted/full config | PASS with operational caveats | Cleartext disabled, backup disabled, no Internet permission, production secrets isolated. Public preview keystore is intentionally non-production. Device behavior and runtime permission UX were not available. |
| Documentation and developer experience | `AGENTS.md`, `README.md`, `DEVELOPMENT_STATUS.md`, release/evidence docs | full | PASS | Baseline and physical-device evidence rules are explicit. Release-candidate branch still requires CI and device acceptance before promotion. |
| Product/platform fit | manifest, services, activities, Compose UI, camera lifecycle | targeted/full relevant modules | FINDINGS | Front-camera-only assumption fixed in this candidate. Physical camera, accessibility, thermal, and UX behavior remain unverified. |
| Infrastructure/data lifecycle | settings stores, recording/export/recovery docs, CI artifacts | targeted | PASS with device limitations | Local recording/export and recovery paths are documented and tested; no backend or migration service is present. |

## Application-code audit

### Module map and primary slices

| Module | Responsibility | Main callers/dependencies | State/side effects | Tests | Result |
|---|---|---|---|---|---|
| `GestureCaptureService` | Foreground camera lifecycle and CameraX analysis | Android service, CameraX, `GestureRecognitionEngine` | Camera binding, executors, runtime readiness, cleanup | lifecycle/restart tests; no camera-availability matrix | Front-camera compatibility defect fixed |
| `CameraFrameInput`, `FrameBufferPool`, `LatestFrameProcessor` | Image ownership, bounded buffers, latest-frame backpressure | Camera analyzer -> recognition worker | pooled pixel leases and frame release | frame/buffer/processor tests | PASS; deliberate bounded design |
| `GestureRecognitionEngine` | Timestamp admission, MediaPipe inference, pointer/gesture publication | camera worker, MediaPipe, runtime/action dispatch | model lifecycle, pointer state, diagnostics, epoch checks | extensive recognition, recovery, replay, calibration tests | PASS with allocation hotspot fixed in candidate |
| `HandOwnershipTracker`, `PointerHandFallback` | controlling-hand continuity and unambiguous pointer fallback | recognition engine | ownership identity, preference, ambiguity | ownership and pointer-continuity tests | Behavior-preserving allocation reduction applied |
| `AirRuntime`, action/accessibility classes | authority epochs, foreground safety, action dispatch | UI, service, accessibility service | StateFlow state, platform gestures/touches | extensive dispatch/safety tests | PASS by source/test evidence |
| Compose UI and activities | product navigation, settings, calibration, diagnostics, gaze lab | `MainActivity`, product screens | lifecycle collection, persisted settings, user feedback | UI/navigation and calibration tests | PASS by source/test evidence; no device rendering test |
| `GazeLab` | opt-in face/eye analysis isolated from hand action authority | `GestureRecognitionEngine`, `GazeLabActivity` | native model, worker, copied frames | Gaze unit tests; no stress/device test | P2 lifecycle/GC follow-up |
| Python tools and CI scripts | recording analysis, APK verification, release/signing checks | GitHub Actions and developer workflow | artifacts/reports/checksums | 90 Python tests | PASS |

Primary path traced: camera exposure -> `ImageAnalysis` analyzer -> `CameraFrameInput.capture` -> bounded latest-frame queue -> timestamp/epoch admission -> MediaPipe video inference -> hand ownership/pointer mapping -> gesture/action policy -> accessibility dispatch -> feedback/diagnostics. Negative paths reviewed include stale/out-of-order frames, source-context changes, model failure/retry, dropped/replaced frames, service shutdown, protected foreground contexts, permission denial, and partial-hand recovery.

## Findings and implementation status

### CAM-001 — front-camera-only assumption

- **Track:** application code
- **Severity/confidence:** P2 / CONFIRMED
- **Original evidence:** `GestureCaptureService.kt:121-123` selected `DEFAULT_FRONT_CAMERA` and called `.first()`. The manifest declares front and generic camera features optional.
- **Impact:** rear-only devices could never start capture; the error was caught but no compatible fallback existed.
- **Implemented fix:** `GestureCaptureService.kt` now prefers a front camera, falls back to a back camera, and reports an explicit no-compatible-camera error if neither is available.
- **Acceptance:** CI compile/lint/build plus rear-only emulator/device capture start, rotation, frame delivery, and pointer mirroring verification.

### PERF-002 — per-frame hand selection allocations

- **Track:** application code
- **Severity/confidence:** P2 / CONFIRMED
- **Original evidence:** `GestureRecognitionEngine.kt` built temporary candidate/observation collections; palm anchors used temporary arrays/lists; `HandOwnershipTracker` filtered, mapped, and sorted candidates each frame.
- **Impact:** avoidable allocation and sorting pressure on the camera/inference worker, especially during sustained tracking.
- **Implemented fix:** reusable bounded lists, direct palm-anchor arithmetic, direct candidate scan, and scalar best/runner-up tracking. Selection semantics remain preference-aware and ambiguity-safe.
- **Acceptance:** existing ownership/pointer tests plus same-condition Android replay/profile comparing GC pauses, queue age, and inference cadence. No numerical gain is claimed here.

### LIFE-003 — Gaze Lab worker disposal boundary

- **Track:** application code
- **Severity/confidence:** P2 / STRONGLY SUPPORTED
- **Location:** `app/src/main/java/com/airgesture/control/gaze/GazeLab.kt:91-95` and `GazeLabActivity.kt` resume/start path.
- **Evidence:** `close()` queues native model disposal and immediately shuts down the executor without an awaitable completion boundary; rapid pause/resume can overlap old disposal and new model creation.
- **Impact:** transient native resource overlap and possible memory pressure in the opt-in lab.
- **Status:** not implemented in this candidate. It needs a lifecycle stress test and device/native allocation validation before changing close semantics.
- **Recommendation:** make close idempotent and expose an awaitable/bounded disposal completion off the main thread; add a rapid pause/resume stress test.

## Prioritized improvement plan

| Priority | ID | Improvement | Status | Affected paths | Effort | Acceptance |
|---|---|---|---|---|---:|---|
| REQUIRED FIX | CAM-001 | Front-preferred, rear-compatible camera selection | Implemented | `GestureCaptureService.kt`, manifest/runtime camera path | S | CI plus rear-only device/emulator capture |
| HIGH-VALUE | PERF-002 | Remove bounded per-frame hand-selection collection/sort allocations | Implemented | recognition, ownership, pointer fallback | S/M | Unit suite plus replay/profile evidence |
| HIGH-VALUE | LIFE-003 | Strict, bounded Gaze Lab native-model disposal boundary | Proposed | `GazeLab.kt`, `GazeLabActivity.kt` | M | lifecycle stress test and native memory profile |
| OPTIONAL | OPS-004 | Add required CI/CodeQL status checks to repository ruleset | Proposed | GitHub repository administration | S | ruleset requires successful checks before merge |

## Validation and proof

| Check | Result | Evidence | Limitation |
|---|---|---|---|
| Baseline SHA and status | PASS | `git rev-parse HEAD` = `13559604c35bfe0aea6f6cb90bc2ae55b5abc4ad`; clean at clone; baseline ref resolved to same SHA | Working tree is intentionally dirty after candidate edits. |
| Python tooling tests | PASS | `python3 -m unittest discover -s tools -p 'test_*.py'`; 90 tests, OK | Does not compile Android/Kotlin code. |
| Gradle unit tests | BLOCKED | `./gradlew test --no-daemon --stacktrace --warning-mode all` | Sandbox has no Android SDK; failure occurred during task dependency resolution. |
| Android lint/APK builds | NOT RUN | Must run in Aergis CI | No SDK/device in sandbox. |
| CodeQL/dependency verification | NOT RUN locally | Configured in GitHub Actions | Requires CI environment and remote run. |
| Device/emulator behavior | NOT RUN | Required after CI candidate build | No Android device/emulator available. |
| Performance before/after numbers | NOT CLAIMED | Source-level hotspot identified and reduced | Same-condition profiling is still required. |

## Release-candidate gate

This audit and implementation are suitable for a release-candidate branch, not production promotion. The RC must remain unpublished until the branch CI run passes unit tests, lint, debug/release APK assembly, APK/model/signing/checksum verification, and CodeQL as configured. After that, the exact APK must pass the Galaxy A54/device acceptance checklist in `DEVELOPMENT_STATUS.md`, including camera fallback, pointer/click safety, recovery, latency, thermal behavior, and service restart.
