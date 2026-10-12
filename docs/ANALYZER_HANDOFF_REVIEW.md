# Latest camera frame handoff — review before commit

Baseline verified from GitHub main and baseline-successful-apk: `06f534ecd98db4ce6e19fa08405cb682b5b0199b`.

## Evidence and purpose

The 2026-10-12 A54 recording contains 4,034 analyzed frames over 404 seconds, with recorded camera-to-pointer p50/p95 of 190/490 ms. Source inspection confirms the action freshness ceiling is 220 ms. All 26 tracked samples below the real 0.18 contact threshold were blocked: 25 were stale; one failed pose geometry. This does not establish that scheduling is the only cause of latency or that a faster pipeline alone resolves every click failure.

The current callback retains CameraX's ImageProxy until synchronous GPU inference, publication and diagnostic work finish. CameraX already uses KEEP_ONLY_LATEST. This change separates rapid capture from that slow worker so the next inference can choose the newest detached frame, without retaining a borrowed camera image during native work.

## Change

- A dedicated single capture executor copies RGBA pixels and metadata, then closes ImageProxy before handing the snapshot to the inference worker.
- LatestFrameProcessor owns one active frame and at most one pending frame. New arrivals replace and release pending work, without adding inference tasks for every camera frame.
- FrameBufferPool leases at most three reusable buffers: inference, pending, and capture. At 640×480 RGBA this is about 3.5 MiB of pixel storage.
- Native creation, VIDEO inference, MPImage close, and recognizer disposal remain on the original single executor. A frame lease is released after inference and diagnostic readers finish.
- Sensor timestamps, rotation, crop metadata and camera pixels travel together. The fallback clock uses capture arrival, not the later inference start. Added captureToAnalysisMs records the handoff delay.
- Stop disables engine publication, discards pending frames, shuts down capture, and queues native cleanup after active inference. Late captured frames are released by the closed handoff.
- Diagnostic images and export still use the same analyzed frame, now from owned storage.

## Scope and risks

This affects CameraX frame ownership, MediaPipe input lifetime, scheduling and capture-service shutdown. Pointer mapping, filter algorithms, calibration, pose checks, pinch thresholds, freshness limits and accessibility dispatch are unchanged.

Copying each incoming frame may increase capture CPU/bandwidth; device testing must compare latency, useful landmark FPS, thermal behaviour and sustained performance. A faster capture request does not prove 60 landmark updates/s. Sensor latency and inference cost can remain bottlenecks.

## Validation

- Six latest-frame handoff tests failed before implementation and pass afterward.
- Two buffer-pool tests failed before implementation and pass afterward.
- These eight plus existing clock/freshness tests: **18 host JVM tests passed** using the available standalone Kotlin compiler and JUnit.
- Updated Android snapshot test covers padded pixels, preserved metadata, camera closure and independent storage. Service tests cover capture-worker shutdown and disposal of pending frames. These Android/Robolectric tests have **not run locally**.
- `./gradlew testDebugUnitTest --offline --console=plain` could not bootstrap because Gradle 9.8.0 is not cached and services.gradle.org is unreachable. Full compilation, unit tests, lint and APK verification require CI. Host JVM tests are not an Android build.

## A54 acceptance after green CI

Use the same calibration and filter for comparison. First test without the mirror/recorder, then repeat with recording enabled. Check slow aim, fast travel, thumb–middle open/close/release taps, hand loss, disarm, stop/restart and rotation. Compare frameToPointerMs and action-freshness counts against baseline. Do not weaken stale-action safeguards to make counts improve.

No commit, push, PR, baseline update or physical-device success is claimed.
