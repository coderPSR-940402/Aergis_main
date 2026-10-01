# Aergis Pointer Tracking Upgrade

## Intent
Upgrade pointer tracking and gesture control for the Android app, with emphasis on low latency, fast-motion response, stable cursor placement, and reliable gesture injection.

## Findings

### Critical
- **The production pointer was filtered twice.** `GestureInterpreter` produced a filtered point and `GestureRecognitionEngine` filtered that result again. This compounds latency and can make rapid motion appear frozen.
- **The shipped filter used an incomplete covariance update.** Cross-covariance terms were declared but not propagated, while a fixed outlier gate rejected legitimate fast motion. Rejected samples could freeze the pointer for multiple frames.
- **Tests shadowed production code.** `app/src/test/.../AdaptiveKalmanFilterTest.kt` declared its own `AdaptiveKalmanFilter`, so the tests did not exercise the production implementation.
- **Queued actions used a later cursor position.** Accessibility actions read the current pointer only when the main-thread callback ran. A click detected at one point could land at a later point after cursor movement.

### Major
- Hand-loss recovery did not explicitly reset gesture edge state, so a gesture could remain logically held across disappearance/reappearance.

## Changes

1. Replaced the incomplete covariance implementation with a bounded constant-velocity alpha-beta tracker:
   - normalized coordinate clamping;
   - bounded time-step handling and reset on stale frames;
   - speed-adaptive position/velocity gains;
   - robust innovation gate for landmark spikes;
   - no velocity learning from rejected outliers;
   - sustained fast motion follows on the next frame instead of freezing.
2. Removed the second pointer filter from `GestureRecognitionEngine`.
3. Snapshot gesture target coordinates before posting accessibility work to the main thread.
4. Anchor scroll gestures to the detection-time cursor position.
5. Reset gesture action edge/cooldown state on hand loss.
6. Replaced shadow tests with tests that exercise the production tracker, including bounds, one-frame spikes, recovery, and sustained fast motion.

## Validation

- `git diff --check`: passed.
- Numerical tracker simulation: passed for spike containment, recovery, and sustained-motion behavior.
- Full Gradle command was attempted with Gradle 8.11.1, but this sandbox has no Android SDK (`SDK location not found`). CI remains the authoritative APK/lint validation path.

## Manual APK acceptance test

1. Install the CI debug APK on a physical device.
2. Test slow motion: cursor should remain stable without visible jitter.
3. Test fast left/right and diagonal motion: cursor should follow immediately without freezing or large lag.
4. Hold the index finger still for 5–10 seconds: cursor should not drift materially.
5. Move hand out of frame and back: cursor should hide, then resume without a phantom click or stale gesture.
6. Perform a pinch/click while moving the hand: click should occur at the detection-time cursor location.
7. Perform repeated swipe gestures with pauses: exactly one scroll action per swipe, no repeats while stationary.
8. Test both hands and the configured hand preference.
