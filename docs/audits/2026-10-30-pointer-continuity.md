# Pointer and click continuity review

Continues PR #74 source `3fdee36e9db9638a135e9e2ff52eb2a53e252406`.
Main and remotely resolved `baseline-successful-apk`: `a53596b`.
The latest supplied draft patch was inspected; its older image tracker is not
applied over the existing implementation. No new physical device evidence.

## Reproduced causes and corrections

- The fallback searched candidate centers at two-pixel intervals. Exact one-pixel
  translations failed, as did an unchanged center at x=9 when edge clipping changed
  search parity. Search every center within the existing 24-pixel radius. Reuse a
  65x65 luminance tile and cached candidate scores: at most 4,225 source-pixel reads
  instead of 50,625 for the original full interior search (194,481 for a naive
  full-pixel search). The number of matching operations increases; this is a
  source-read reduction, not a measured Android latency improvement. Immutable
  appearance, uniqueness/occlusion rejection and motion-only authority remain.
- A finite off-frame index was filtered before the engine rejected its raw
  coordinate. A regression moved the recovered stationary X from .60 to .998272.
  Reject these coordinates before either filter updates. Invalid coordinates
  cancel contact readiness; observed opening is still required before another
  click. Explicitly authorized refits continue using the last stable pointer
  without learning the rejected coordinate or replaying a held contact.
- Left/Right preference filtered out a continuously tracked palm as soon as its
  label flipped. Apply preference on acquisition; existing palm-continuity scoring
  retains an established owner across label refits. Preference changes, ambiguous
  geometry, expiry and opposite-label return after loss still revoke authority.

## Verification

Five new core assertions failed on the unchanged implementation: odd translation,
clipped-edge parity, fixed-preference refit, invalid-coordinate contact cancellation
and filter contamination. The luminance read-budget test failed on naive full-pixel
search, then passed with tile reuse. Core tests cover unchanged anchors, finite/NaN
refits, held-contact recovery, both hand preferences, detection reordering,
replacement after loss, small/rectangular image bounds and ambiguous/flat textures.

Local verification: 143 core JUnit tests and 18 Python tooling tests pass;
`git diff --check` passes. Host doubles cover Android clock, generated BuildConfig, the unused MediaPipe
smoother overload and mapping-store lookup; the exercised tracking/interpreter/
click/filter/ownership implementations are production code. Local Android Gradle
bootstrap cannot download its distribution (`Network is unreachable`).

Native engine regressions exercise one-pixel fallback without action authority,
recovery after an off-frame index and an anchored native tap through a fixed-hand
label refit. They run in the existing complete Android CI suite alongside lint,
debug/minified-release packaging and APK verification.

## Author review and acceptance

Single-agent author self-review as requested. Tile indexing was checked at minimum
17x17 size and all clipped edges. Matching still rejects competing texture peaks;
the expanded search does not authorize input. Fixed preference continues to filter
new acquisition and cannot reclaim opposite-label input after loss. Interpreter
validation leaves independently valid fingertip tracking and explicitly authorized
anchor protection intact. No other production code changed.

Sensitive scope: fingertip image fallback, pointer interpreter input and ownership.
CameraX, MediaPipe task/model/delegate, transforms, filter coefficients, pinch
thresholds/timings, calibration, mappings, accessibility backend and service
lifecycle retain their current implementations. Physical fine aiming, occlusion,
second-hand crossings, repeated thumb-middle contacts and recovery comfort require
the exact new APK on A54. No merge or build/functional-baseline promotion.
