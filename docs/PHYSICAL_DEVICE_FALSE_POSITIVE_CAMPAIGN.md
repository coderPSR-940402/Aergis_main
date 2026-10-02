# Aergis Physical-Device False-Positive Campaign

## Purpose

Measure whether Aergis performs an unintended accessibility action when the user is not deliberately issuing a command. This campaign is required before expanding the gesture vocabulary or presenting numerical reliability claims.

The campaign tests the current safety architecture:

- `READY` mode must never inject gesture actions.
- `ARMED` mode must require stable gesture transactions.
- Ambiguous or lost hand ownership must cancel commands.
- Device motion must pause control.
- Protected or unknown foreground contexts must block actions.
- Pointer continuity must remain separate from one-shot command dispatch.

## Required device matrix

Run the minimum campaign on three physical Android devices:

| Device class | Required characteristics |
|---|---|
| Modern flagship | Android 14 or newer, gyroscope and accelerometer |
| Mid-range device | Android 12–14, representative OEM camera stack |
| Lower-performance device | Android 10–12 or lowest supported API, gyroscope preferred |

Record model, Android version, API level, camera resolution, refresh rate, available sensors, battery level, and ambient temperature before each run.

## Precondition checklist

1. Install the exact release candidate APK.
2. Grant camera permission.
3. Enable the Aergis Accessibility Service.
4. Confirm the foreground safety status is known and safe.
5. Start the capture session; verify it starts in `READY`.
6. Verify no action occurs while the app remains in `READY`.
7. Arm control deliberately and verify the UI reports `ARMED`.
8. Confirm the notification and in-app stop controls remain available.
9. Use a visible text editor or test app with an undo function for ordinary tap/scroll scenarios.
10. Use a disposable test account or offline mock screens; do not test against real banking, payment, authentication, or medical data.

## Scenario matrix

Each scenario is a 10-minute run unless otherwise specified. Record every intended action, unintended action, suppressed action, tracking interruption, and reason shown by the app.

| ID | Scenario | Mode | Expected result |
|---|---|---|---|
| FP-01 | Hands absent | READY and ARMED | Zero actions |
| FP-02 | Natural typing near the camera | ARMED | Zero actions |
| FP-03 | Eating or drinking | ARMED | Zero actions |
| FP-04 | Scratching face or adjusting glasses | ARMED | Zero actions |
| FP-05 | Picking up an object | ARMED | Zero actions |
| FP-06 | Palm, fist, peace, and pointing transitions | ARMED | No command until stable transaction completes |
| FP-07 | Two hands entering together | ARMED | Ambiguous ownership is rejected; zero commands |
| FP-08 | Hands crossing | ARMED | Original owner is retained or control is cancelled; no takeover |
| FP-09 | Primary hand occlusion and reacquisition | ARMED | Transaction is cancelled and requires fresh evidence |
| FP-10 | Phone rotation | ARMED | Motion cancellation pauses actions |
| FP-11 | Picking the phone up | ARMED | Motion cancellation pauses actions |
| FP-12 | Walking or vehicle vibration | ARMED | No unintended commands |
| FP-13 | Cable or desk bump | ARMED | Motion cancellation pauses actions |
| FP-14 | Screen rotation | ARMED | Pointer and command state reset safely |
| FP-15 | Screen off/on | ARMED | Actions remain blocked until valid recovery and re-arming if required |
| FP-16 | Permission dialog | ARMED | Action blocked as protected context |
| FP-17 | Lock screen or keyguard | ARMED | Action blocked as protected context |
| FP-18 | Installer or package resolver | ARMED | Action blocked as protected context |
| FP-19 | Password, PIN, biometric, or payment test screen | ARMED | Action blocked as protected context |
| FP-20 | Accessibility service interruption | ARMED | No queued action executes after interruption |
| FP-21 | Vision failure or camera denial | ARMED | Tracking and commands stop; recovery is explicit |
| FP-22 | Rapid arm/disarm toggling | READY/ARMED | No action leaks across mode transitions |
| FP-23 | Classifier lookalikes | ARMED | One-shot action requires stable evidence and release |
| FP-24 | Late or out-of-order frames | ARMED | Stale evidence cannot fire a command |

## Intentional-command runs

For each enabled mapped gesture, perform 30 deliberate repetitions per device:

1. Enter a neutral pose.
2. Form the gesture deliberately.
3. Hold until the action fires.
4. Release to neutral.
5. Wait for cooldown.
6. Repeat.

Record true positives, missed commands, duplicate commands, latency from stable formation to dispatch, and whether release was required before rearming.

## Motion tests

Measure the motion monitor separately:

- Rotate the device slowly, then rapidly, around each axis.
- Lift the device from a table.
- Walk for five minutes.
- Tap the table beside the device.
- Attach and remove a charging cable.
- Place the device on a moving vehicle surface where safe.

Record activation latency, release latency, false pauses while stationary, and any action that escaped during motion. The expected behavior is conservative cancellation, not uninterrupted control.

## Protected-context tests

Use safe mock or system screens that represent:

- Keyguard/lock screen
- Runtime permission dialog
- Package installer/resolver
- Password or PIN entry
- Biometric prompt
- Payment or wallet mock screen
- Emergency/system-critical screen

For each screen, verify that the service reports a protected or unknown context and that `dispatch()` refuses every configured action. Unknown context must fail closed.

## Measurements

Record these metrics per device and scenario:

| Metric | Definition |
|---|---|
| Unintended actions/hour | Actions observed during non-command activity |
| Precision | Intended actions divided by all fired actions |
| Recall | Fired intended actions divided by deliberate attempts |
| Duplicate-fire rate | Extra actions per deliberate gesture |
| Re-arm violations | Same gesture firing again without neutral release |
| Median / p95 latency | Stable evidence to accessibility dispatch |
| Ownership takeover rate | Commands caused by a competing hand |
| Motion escape rate | Actions fired while motion cancellation should be active |
| Protected-context escape rate | Actions fired in protected or unknown context |
| Pointer jitter | RMS displacement while hand is held stationary |
| Analyzer drop rate | Dropped or late frames during the run |
| Thermal/battery impact | Battery percentage and temperature change per hour |

## Evidence collection

For every run, retain:

- APK version and commit SHA
- Device and OS metadata
- Screen recording where privacy-safe
- A timestamped event log
- A manually annotated action log
- Screenshots of safety status when a block occurs
- Crash, ANR, and accessibility-service logs
- Battery and temperature readings

Do not collect camera frames, accessibility window text, or personal content unless a separate consented test protocol explicitly permits it. Prefer event counts and reason codes.

## Release gates

A release candidate should not advance until all of the following are true:

1. Zero unexplained actions occur in FP-01 through FP-24 on every required device.
2. Protected-context escape rate is zero.
3. Motion escape rate is zero in the defined motion suite.
4. No re-arm violation or duplicate-fire regression is observed.
5. Accessibility interruption does not deliver stale queued actions.
6. CI, CodeQL, and release APK validation are green.
7. Any remaining manual-only limitations are documented in `DEVELOPMENT_STATUS.md`.

Numerical product claims should be derived from this campaign only after the sample size, devices, lighting, distance, users, and scenario durations are recorded.
