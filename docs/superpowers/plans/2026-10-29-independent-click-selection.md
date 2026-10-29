# Independent click and selection implementation plan

Goal: dependable thumb-middle clicking with either cursor setting, persistent controlling-hand identity and usable selection activation, with lower avoidable pipeline work.

Architecture: preserve current pointer/anchor pipeline. Hidden cursor pinch uses the existing selection backend; it never injects an invisible coordinate tap. Separate selection retention authority from queued-action epochs so brief vision gaps revoke input without deleting a valid target. Keep window/content identity and explicit safety revocation. Full classifier remains whenever a mapped gesture requires it or calibration/rehearsal needs it.

Constraints: build on PR 74 b86e7f8; main/build reference a53596b. No baseline promotion, merge, cosmetic redesign, thresholds/filter/calibration math, IMU, service restart, dependency/model upgrades or remote agent delegation. Native Android CI is build authority.

Review focus: consumed pinch on loss; second-hand replacement; selection content/window changes; highlight draw races; asynchronous request/outcome correlation.

- [x] Add regressions for hidden-pointer click channel, stable owner under label jitter/second-hand ambiguity, selection retention after vision loss, unchanged drawn highlights, correlated selection outcomes and mapped-pose inference choice. Observe failures where runnable.
- [x] Preserve owner generation across label refits; retain bounded ownership history through ambiguous candidates without granting action evidence. Use stable palm anchors and aspect-correct scale. Pass empty observations through ownership tracker.
- [x] Run hidden-pointer contact state independently of pointing fingertip/visibility, dispatch SELECT_ITEM on consumed contact; retain current pointer-on anchors and native epoch safety.
- [x] Add selection-retention epoch revoked by actual settings/session/safety changes; refresh stable semantic targets without geometry-only churn. Preserve existing duplicate/replacement/container rejection. Record selection movement, clearing and activation with opaque session-local target IDs, bounds, reason and request IDs; record outcomes with matching IDs.
- [x] Preserve unchanged drawn highlight; mapped-custom-only armed inference may use hand landmarker with full recognizer fallback. Reject already expired frames before model work; optimize packed RGBA copy.
- [ ] Run available full tooling tests, Android tests/lint/debug/minified-release in CI, review diff, update PR with exact evidence and limitations.
