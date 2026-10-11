# Aergis usability excellence

## Goal and authority
Deliver comfortable, precise, predictable air control on the Galaxy A54, with a premium, understandable interface. User approved implementing the proposed improvements on 2026-10-11, asked for cohesive PRs and continuous single-agent execution. Continue until a real access, CI, product decision, or physical-device gate requires a stop. Do not request repetitive design approvals. No multi-agent work.

Baseline resolved live: `baseline-successful-apk` = `0a86e482cb1aa77879dd28e8a29f539555f3ebfc`; main matched on inspection. Re-resolve before each subsequent PR. Never move the baseline manually. A successful build is not proof of device usability.

## Existing capabilities to preserve
PR #50 added precision filtering, aspect-correct pinch geometry and native touch safeguards. PR #51 refined the palette. PR #52 requests supported 60 FPS capture. The source already uses VIDEO mode, GPU initialization with CPU fallback, latest-frame CameraX analysis, ownership selection, calibration profiles, a live mirror, and touch continuation for pinch/long press/drag. The pasted replacement code is not the baseline.

## Product requirements
1. Stable fine aiming, responsive travel and full-screen reach without excessive hand movement. Preserve index-tip authority and verified coordinate transforms.
2. One deliberate pinch interaction; stable click target; retain intentional long press/drag. Show recognition, dispatch and completion as distinct facts; completion is not proof that the target app performed its intended business action.
3. Comfortable hand workspace, left/right preference, explicit profiles and clutch/reposition behavior with no surprise activation.
4. Real mirror image, aligned landmarks, active hand and calibrated area; explain only conditions supported by evidence. No invented lighting diagnosis or confidence percentage.
5. Guided calibration followed by actual target practice and an explicit save/cancel path. Keep advanced tuning and reset available.
6. Deliberate scroll entry/exit, neutral stop zone and controllable speed; normal pointer travel never scrolls.
7. Tracking loss blocks actions; reacquisition cannot transfer pending commands or touches to another hand or replay an already-fired held command.
8. Accessible pause/resume/stop and truthful Ready/Active/Paused/Tracking lost states. Capture start must not silently authorize actions.
9. Practice target selection, scrolling, dragging, stopping, recovery and completion feedback. Label touchscreen versus air testing honestly; never count recognized gestures as successful target clicks.
10. Explore optional precision lens after core tasks pass. Screen capture consent, protected content, secure windows and overhead are feasibility gates; no unsupported promise of universal magnification.
11. Three primary destinations: Control, Gestures, Settings. Place mirror/calibration/practice beneath Control; retain diagnostic access. Black/silver/yellow/purple, restrained depth, readable typography, large touch targets, text alongside status colors, reduced motion and optional feedback.
12. Measure task success, accidental actions, target acquisition time, recovery, comfort, latency and sustained thermal behavior. Camera FPS, inference/result FPS and display updates are different measurements.

## Boundaries
Keep UI changes separate from camera/tracking/gesture-engine PRs. Preserve current dependency versions and free tooling. CI is the Android build authority; the workflow remains usable from a phone. Do not rewrite working subsystems or change safety gates merely to make a demonstration pass. Physical GPU use, achievable landmark FPS, tactile comfort and real click reliability require device evidence. No automatic merge of untested engine changes.

## First reliability unit
Static classifier transactions currently advance by frame count: the same gesture fires sooner at 60 FPS than at 30 FPS. Replace confirmation and neutral release with elapsed-time windows (100 ms each), retain the 700 ms cooldown and 250 ms freshness limit, and require finite confidence and identifiable tracked ownership for action evidence. A discontinuity discards incomplete evidence; it must not erase the release requirement of an already-fired command. Genuine tracked neutral evidence, not low confidence or tracking loss, rearms it. Preserve the engine's process/reset interface and the pinch pipeline.
