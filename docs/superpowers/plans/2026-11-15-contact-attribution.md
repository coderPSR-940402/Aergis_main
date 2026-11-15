# Targeted pointer TAP contact attribution repair

Scope: observation-only engine-to-DevLoop correlation for actual held and pending-after-release pointer TAP requests. Preserve gesture dispatch, hotspot readiness, pending lifetime and deadline behavior.

## Plan

1. Trace the production engine, service request boundary, worker and recorder; preserve original frame/owner/action/input provenance.
2. Add real engine/service/recorder regression tests before production edits; obtain CI red evidence because this environment lacks Android SDK/kotlinc.
3. Carry explicit confirmed-contact provenance through the final pointer request and retain the diagnostic episode across bounded pending release.
4. Verify focused Android regressions and Python collector suite; record exact limits, request review and device acceptance of the exact APK via parent task.

## Progress

- Read AGENTS.md, evidence ledger and overlap review. Base main: `307f5e91ad9e84b9f924ef4ce07b86b8e56de636`; parent resolved `main` and dynamic `baseline-successful-apk` to that same commit. No new physical-device baseline identity is available.
- Root cause: the early `confirmed_or_pending_hotspot_check` observation is deliberately non-authoritative; no final recognized observation exists. Ordinary release finalizes contact before a deferred dispatch can correlate.
- Added test-only engine/service/worker recorder coverage for held and post-release native taps, and no recognition for unavailable hotspot, owner/action/input epoch loss, stale evidence or changed target. Observed RED on PR #103 head `16caebc44ac57c57bf8bc6aca02a45d686c0b4be`, CI run `43706655692`, before production edits: both real TAP tests failed their missing-contact assertion at line 158. The negative loop exposed a separate test fixture omission at line 122: service destruction restores READY, so subsequent loop scenarios must explicitly arm their new service. Fixed fixture setup without changing runtime safety.
- Android tests have not been run locally; no SDK or Kotlin compiler is available. No device outcome is claimed.

## Repair design

- Capture immutable confirmation provenance (camera frame, owner, action epoch and input epoch) alongside the existing pending target. It is diagnostic data only and never participates in dispatch eligibility.
- Mirror confirmation into the recorder without calling it recognized. The recorder binds that evidence to the exact same-frame geometric episode.
- Carry provenance through `dispatchPointerTap` into `openAction`, where the existing authoritative request id and frozen applied hotspot already exist. Emit the final recognized decision only when this exact request can link its matching evidence.
- A confirmed geometric episode released while delivery is pending is retained as one bounded diagnostic episode. Final request opening ends it as linked; cancellation, authority discontinuity, loss, expiry and capture stop end it without inventing a request. Keep ordinary unconfirmed releases unchanged.
- Preserve the existing synthetic recorder correlation tests for selection and same-frame matching; production pointer TAPs additionally use explicit request-bound evidence. Do not relax same-frame recognition or rely on action timing proximity.

## Test rationale

The production change that must fail these tests is removal of the explicit final request evidence, or finalizing the original episode before deferred release delivery. Held and release tests first require exactly one real native gesture; then require the original contact, owner and epochs, the confirmation frame, authoritative request id, and no erroneous no-action outcome. Negative cases require no final recognized event when readiness/authority prevents delivery. Wrong-owner geometry moves the palm beyond continuity bounds; a handedness-label-only change is intentionally a valid retained owner. Changed-target coverage moves the actual applied hotspot because the release filter intentionally retains its original camera anchor.

## Local verification

- `git diff --check`: passed in test-only state.
- `python -m unittest tools/test_aergis_live_collector.py`: 30 passed (0.571 seconds).

- `python -m unittest discover -s tools -p 'test_*.py'`: all 59 Python tooling tests passed (2.057 seconds).

## Implementation checkpoint

- Added immutable confirmation evidence and request-bound transport through the final pointer TAP open; the recorder emits authoritative recognition with the exact request id, frozen target, original source frame, owner and epochs.
- Retains at most one confirmed released contact. The existing camera pending budget (250 ms) and recorder observation timeout bound retention; resets, discontinuity, evidence loss and stop revoke it. This is diagnostic state only.
- No runtime dispatch predicates, hotspot readiness, pointer stabilization, tap deadlines, gesture dispatch, native touch or interpreter behavior changed.
- `git diff --check`: passed after production edits.
- `python -m unittest discover -s tools -p 'test_*.py'`: 59 passed after production edits (2.137 seconds).
- Corrected Android tests, full Android CI/lint/APK checks and exact-APK device acceptance remain pending; no local Android run or device success claimed.
