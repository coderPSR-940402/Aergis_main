# AERMOTUS Screen-Recording Evidence Report

## Source archive

- Archive: `aergis_update.zip`
- Archive SHA-256: `21fc1dbf982dc7415b5398dbc7bc896fc91144b31d717427b40ce956b6ff8f1c`
- Recording 1: `Screen_Recording_20261004_044026_AERMOTUS_1.mp4`
  - 648×1404, HEVC, 60 fps nominal, 210.6 seconds
- Recording 2: `Screen_Recording_20261004_044159_AERMOTUS_1.mp4`
  - 864×1872, H.264, 120 fps nominal, 76.5 seconds

## Evidence quality

The recordings were analyzed as visual product evidence. They are useful for recovering UI/workflow contracts and visible telemetry, but they do not prove the underlying algorithm, exact threshold, or causality. OCR/visual analysis can misread labels; ambiguous labels must be verified from frame-level inspection before becoming user-visible strings or test contracts.

## Observed product structure

- Four persistent bottom tabs: **Control**, **Practice**, **Setup**, **More**.
- Control dashboard: active/paused status, last action, hand ownership, confidence, and tracking status.
- Practice: Swipe Left, Swipe Right, Swipe Up, Swipe Down, and Fine Scroll/Double Gun training.
- Setup: accessibility/camera status, Air Pointer, pointer activation, mapping, and calibration.
- More: system health, `READY`, `RESTORE SAFE GESTURE`, and technical console.
- Tracking overlay persists while the service operates outside the app, including Android Home.

## Observed tracking overlay

- States resembling `WAITING`, `STARTING`, `SYNCING`, `STATIC`/`STABLE`, and a transitional tracking state with an uncertain OCR label.
- Hand identity and confidence: examples include `Left 98%`, `Right 99%`, and `None 0%`.
- Gesture and confidence: examples include `Victory 85%`, `Closed fist 90%`, and `Thumb up 90%`.
- Visible pointer-tip marker and tracking engine/status identifiers.

The current implementation should reproduce the observable status model only after naming is verified. The overlay must remain privacy-minimized and must not expose other-app window content.

## Observed pointer and calibration behavior

- Air Pointer uses index fingertip landmark **8**.
- Index/middle fingertip contact appears to support click and long-press semantics.
- Control-hand options: `Auto`, `Left`, `Right`.
- Explicit pointer activation through an `OPEN POINTER`-type action.
- Calibration separates raw fingertip, validated aim, and final calibrated/smoothed cursor.
- Observed calibration bounds:
  - Left: `0.10`
  - Right: `0.90`
  - Top: `0.08`
  - Bottom: `0.92`
- Observed horizontal and vertical center precision defaults: approximately `1.00`.

These are recording-derived defaults, not proven optimal constants. They should be configurable, versioned, validated, replay-tested, and device-tested.

## Observed gesture mapping

- Broad swipes map to directional/incremental scroll.
- Double Gun/Fine Scroll maps to small up/down/left/right scroll increments.
- Other gesture actions visibly include Back, Home, Recent apps, Select/click focused item, Copy, Paste, Volume up/down, Notifications, Quick settings, and Media play/pause.
- Example mappings observed:
  - Closed fist → Back
  - Thumb up/down → Volume up/down
  - Victory → Recent apps
  - I-love-you → Media play/pause
  - Pinky up → Notifications

Treat these as configurable mapping examples, not universal defaults. All dispatch must remain behind ownership, confidence, READY/ARMED, cooldown, release/re-arm, protected-foreground, and final policy gates.

## Observed diagnostics

- Pointer cadence warning when tracking smoothness falls below a target.
- Intent states resembling `TRACKING`, `ARMING`, `POINTING`, and `HIDDEN`.
- Confidence plus motion/scale telemetry.
- Local app-private landmark audit/corpus recording toggle.
- Source-to-pointer and source-to-action latency panels; examples approximately `63 ms` and `110/370 ms`.
- Submitted/result rates approximately `6–12 fps`; result yield near `100%` in the recordings.

These values are observations rather than acceptance thresholds. The implementation should measure them first and establish targets using replay and physical-device benchmarks. Any audit corpus must be bounded, user-controlled, app-private, and never upload window content or raw data externally.

## Prompt update

The observations above were incorporated into `AUTONOMOUS_AGENT_EXECUTION_PROMPT.md` under **Recording-derived behavior evidence**, with explicit instructions to distinguish observations from implementation specifications.
