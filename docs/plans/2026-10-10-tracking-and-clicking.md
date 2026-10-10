# Precision tracking and deliberate clicks

Baseline resolved from GitHub before edits: `baseline-successful-apk` and main
both `f742b197e1051162f4628e30eeb409c2922d6bae`. The user reports jitter, missed or
accidental clicks, and lag/loss on a Samsung Galaxy A54. No physical A54 is attached.

Design: retain index-tip authority, calibration, hand ownership, live mirror and
all existing action gates. Add a causal screen-space precision filter with a
speed-adaptive cutoff and confirmation of large isolated jumps. Compare against
the existing CURRENT pipeline at 15/30/60 fps on stationary and travel traces;
require reduced jitter and at least 45% lower travel error before selecting it as
the default. Preserve CURRENT/VC49 choices and record all candidates for device
comparison. No prediction beyond the observed target and no invented frames.

Correct pinch/pose distances for camera aspect ratio, and evaluate drag motion
with screen aspect ratio. A small one-frame excursion must not start a drag;
sustained movement or a large deliberate move may. Gate native held touches on
pointer enablement at dispatch/callback time as well as action epoch, including
an analyzer frame finishing after the toggle.

Verification: failing regression tests for geometry, spikes, slow/fast movement,
clock anomalies, click-vs-drag intent and late native dispatch; integrated engine
and recording coverage; full unit tests, lint, debug/minified release builds and
APK/signature checks. Synthetic ratios are algorithm evidence, not measured A54
camera/display latency or proof of superiority over competing apps. Physical
trials must cover lighting, distance, thumb-middle contact, long press/drag,
rotation, CPU/GPU fallback, and sustained operation.

Execution results:

- Added PRECISION as the default, preserving CURRENT/VC49 and recording all
  three outputs. Camera rotation/aspect changes reset tracking history.
- Corrected camera geometry, screen drag distances, near-threshold drag
  confirmation, release while fingers remain close, and native pointer-disable
  gating. Rejected positions retain the displayed pinch anchor and cannot start
  or move a touch; finger-up remains possible and freshness cancellation remains
  active.
- Regressions were observed failing before fixes, including stationary jitter,
  slow fine aim at 60 fps, geometry, accidental drag, late dispatch, retained drag
  state, and displayed-anchor jumps. Final Android suite: 239 passed; Python
  analyzer/APK/signing suite: 11 passed.
- At 15/30/60 fps, stationary precision/CURRENT RMS ratios were
  0.794/0.769/0.734. Precision mean travel errors were
  0.00168/0.00408/0.00605 versus 0.03504/0.03323/0.03076 for CURRENT.
  The initial adaptive gain and fixed-length fine-aim window failed explicit
  targets; the final filter uses a time-based coherence window and passes all
  three frame rates. Synthetic traces and thresholds are in
  `PrecisionPointerFilterTest`; CI exports `precision-benchmark.json`.
- Physical Galaxy A54 testing remains pending. The benchmark specification
  includes a reproducible device campaign and limits on interpreting replay
  improvements. No competing-app or camera-to-display performance claim is made.
- Final local verification: `./gradlew test :app:lint :app:assembleDebug
  :app:assembleRelease` succeeded. Lint reported zero errors and 46 warnings.
  Debug and minified release APK package/version/model checks passed; the debug
  APK signature matches the persistent preview certificate. `git diff --check`
  passed. Publication targets a feature branch; the successful-main baseline
  remains under CI control.
