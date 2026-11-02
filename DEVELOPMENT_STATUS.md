# Development Status

_Last refreshed: 2026-11-02._

## Source of truth

- `main` is the current repository source.
- `baseline-successful-apk` is the newest **CI-verified APK-producing build reference**. Resolve it dynamically; never hard-code an old commit as the development baseline.
- Physical behavior is accepted only when device evidence is tied to the exact APK/commit/run. CI success is not device usability proof.
- `known-good/commit-50` remains historical rollback evidence, not the current development target.

## Current working architecture

- CameraX latest-frame analysis with prompt image cleanup and stale-frame protection.
- On-device MediaPipe vision with GPU-first / CPU-fallback behavior.
- Explicit control-hand ownership and camera-time observation authority.
- Gesture-first mapped actions that can operate independently of pointer rendering.
- Precision pointer mapping/filtering as a secondary control path.
- Thumb-to-middle-finger pinch interaction with anchored click coordinates and native accessibility dispatch.
- Accessibility global actions, bounded touch injection, foreground-safety checks, and action epochs.
- Guided calibration, persisted mappings/settings, diagnostics/replay tooling, and local evidence export.
- Debug and minified release APK production in GitHub Actions with package/model/signing/checksum verification.

## November 2026 stabilization pass

### Pinch, provenance, recovery, and precision

PR #75 merged the latest pinch-confirmation and tracking hardening work after successful Aergis CI and CodeQL. The change set includes brief-threshold-fluctuation tolerance with release-to-rearm protection, stronger frame/input provenance, controlling-hand continuity, edge/near-camera recovery work, and PRECISION/anchored-click protections.

This is **remotely verified code**, not a physical-device acceptance claim. The Galaxy A54 still needs exact-APK validation for deliberate clicks, false clicks, release/reclick, partial-hand recovery, edge motion, second-hand appearance, pointer-off gesture navigation, latency, and sustained use.

### Live diagnostics foundation

PR #77 added only Stage 1 of live diagnostics: a compact monotonic sample type and a hard-bounded in-memory timeline with boundary, eviction, ordering, and empty-buffer tests. It intentionally adds no disk I/O, relay, CameraX, gesture, pointer, or accessibility behavior.

The broader event-promotion/relay design remains staged work. Do not treat the presence of the timeline as completion of automatic live diagnosis.

### Pointer authority cleanup

PR #78 removed the unreferenced legacy `pointer/PointerController.kt` and `pointer/ClickManager.kt`. Current runtime pointer/click authority remains in the active recognition, mapping/filter, pinch, and accessibility-dispatch pipeline. This reduces ambiguity without intentionally changing runtime behavior.

## Verification posture

Every functional PR must keep these gates separate:

1. **Static/host evidence:** focused unit tests and deterministic replay where applicable.
2. **Aergis CI:** full unit suite, Android lint, debug APK, minified release APK, APK verification, signing smoke test, checksums, and artifacts.
3. **CodeQL:** Java/Kotlin analysis.
4. **Main verification:** the merged `main` commit must pass its APK-producing workflow before `baseline-successful-apk` advances.
5. **Device gate:** camera, MediaPipe delegate, pointer, gestures, click reliability, tracking recovery, latency, thermal behavior, and actual Android outcomes require physical-device evidence.

A change does not become a functional baseline merely because it compiles or CI is green.

## Current device evidence / unresolved behavior

The latest retained device evidence still shows substantial tracking interruption and latency under real use. Subsequent code changes may address parts of those failures, but they require a new exact-APK Galaxy A54 test before any improvement is claimed.

Highest-priority device checks:

- normal-posture repeated thumb-middle clicks on labelled targets;
- false-click rate, hold behavior, release-to-rearm, and rapid re-click;
- hand loss/reacquisition, partial-hand visibility, frame edges, and near-camera motion;
- stable controlling-hand ownership when a second hand appears;
- pointer-off gesture navigation, selection, scrolling, Back/Home/Recents, and pointer toggle;
- anchored click location and PRECISION smoothness;
- camera delivery age, inference age/result cadence, and sustained thermal behavior;
- pause/resume, protected screens, orientation change, service restart, and stale-action cancellation.

## Repository / release hygiene

- GitHub Actions are the build authority and use pinned action revisions.
- The public preview signing key is intentionally checked in and is **not** a production key.
- Production signing material belongs only in the restricted GitHub environment/secrets path; local private keystores are ignored by Git.
- Keep engine, dependency, UI, persistence, and release changes in logically coherent PRs; avoid unrelated rewrites.
- Do not delete or overwrite historical rollback/device-evidence refs merely to reduce branch count.

## Administrative gap

`main` currently has no GitHub branch-protection/ruleset enforcement. Repository administration should require pull requests plus successful Aergis CI and CodeQL checks and should block force-push/deletion of `main`. This is an account/repository setting; it is not implemented by application code.

## Next engineering gate

Finish the exact-APK Galaxy A54 acceptance pass for the merged pinch/tracking work before promoting physical behavior. Continue live diagnostics in small stages so observation can improve without gaining action authority or adding latency to CameraX/inference/accessibility paths.
