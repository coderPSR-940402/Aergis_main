# Pinch, latency and recovery implementation plan

Goal: recover the recorded missed contact without replaying consumed clicks,
reduce avoidable capture/native work, recover visible fingertips at frame edges,
and quiet PRECISION during motion without moving frozen click targets.

Base/build reference: remotely resolved main and baseline-successful-apk
`1c5a41e67ddbaccc95eb8b37816306def664d9e0` (PR #74 merged).
Evidence: 2026-10-30 A54 recording, source `eaee3519`, 3/3 issued TAPs completed;
105.883–106.117 s contact interrupted by two measurements in the hysteresis band.
This is physical execution evidence, not acceptance of these new changes.

Constraints: preserve two-hand ownership, GPU/CPU fallback, VIDEO mode,
640x480 preference, latest-frame replacement, timestamps, existing age/gap bounds,
depth veto, explicit opening, exactly-once contact and anchored target coordinates.
Single-agent implementation and review; no merge or baseline promotion.

- [x] Replay exact recorded scalar inputs in a committed fixture; observe failure.
- [x] Bound pending-contact tolerance by 250 ms since the last actual contact.
      Only a fresh eligible contact can confirm; opening/invalid/depth/reset cancels.
- [x] Configure supported preview/stabilization latency options and avoid native
      submission if initialization/conversion has expired the exposure.
- [x] Use stable MCP palm ownership anchors; preserve edge patch evidence with an
      explicit fingertip-to-template offset and the existing ambiguity rejection.
- [x] Give each PRECISION axis its own speed/coherence gain, keeping the existing
      jump gate and final PointerPinchController anchor.
- [ ] Run core regressions, Python tools, native Android CI, diff/author review;
      publish one PR and report exact checks and remaining physical verification.

Review focus: isolated-contact false positives; long hysteresis-band dwell;
lost/changed owner and stale samples; border/small image reads; transverse noise
during travel and click-anchor invariance. Tests must cover these behaviors.
