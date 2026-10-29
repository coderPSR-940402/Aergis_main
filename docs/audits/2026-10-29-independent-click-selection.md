# Independent click, ownership and selection review

Source: PR #74 head b86e7f88139b7ad0c19a439ef08ce10b1c52ba22; main and build reference `baseline-successful-apk` resolved to a53596b2d2c25ced3026d42a31d9b10b60225b5f. Latest device recording identifies tested merge 6b5d1c8bf24eb36151feec5ba10392945c023b52. The older supplied draft patch was inspected; it is not applied over the existing fingertip implementation.

Latest device evidence: 1,944 frames and 43 action requests. The click segment has a completed TAP. NEXT/PREVIOUS requests execute the selection backend, but SELECT_ITEM returns PLATFORM_UNAVAILABLE. Cursor disabled on most frames is intentional. No images, labels, coordinates or recordings are committed. Measured pipeline publication age median/p95 209/253 ms, pre-inference age 127/151 ms, capture-worker wait 20/38 ms, inference 79/112 ms. These are instrumented pipeline timings.

## Root causes and changes

- Pointer-off branch previously resets contact state every frame. A separate thumb-middle contact state now activates the highlighted accessible target when the cursor is hidden. Pointer-on contact retains the existing anchored coordinate path. The clicking switch remains independent of mapped commands; hidden click needs thumb, middle and palm evidence, not index-tip visibility. No invisible coordinate tap is authorized.
- Palm continuity no longer changes owner IDs solely because handedness classification flips. Bounded ambiguous frames do not erase the previous owner's identity and allow the next hand to acquire it immediately. Ownership uses an aspect-correct palm center/scale, not moving fingertips. Missing frames now reach the tracker; pointer-off gap cleanup retains only bounded identity, never contact/action authority.
- Vision gaps still invalidate queued native actions and consume/rearm contact safely, but do not invalidate an otherwise valid selected target. Selection has its own epoch, revoked by session/settings/safety/calibration/orientation/owner changes. Confirmation still rechecks exact content, enabled state, window, duplicates and collection scope.
- Unchanged drawn highlights remain drawn across refreshes; pending first-draw refresh does not erase selection. Geometry-only layout movement no longer changes full-window identity. Content/path/container safety remains conservative.
- Movement, clearing and activation are recorded with opaque selection IDs, bounds and reasons. Requests/outcomes carry matching request IDs, including late callbacks and early rejection. The normal and recovered ZIP/PDF summarize this evidence automatically. NEXT/PREVIOUS completion means Aergis changed its local target; Android click acceptance remains a separately recorded response.
- Packed RGBA capture uses one bulk copy; padding-safe copying remains. Already-expired frames skip conversion/native inference and revoke actions. Armed custom-only mappings may use the existing hand-only model; canned mapped poses and post-occurrence neutral rearming retain the classifier. Full-recognizer fallback remains. No resolution/confidence/filter thresholds or camera request changes.

## Verification and review

Four reproduced host failures covered pointer-dependent click policy, hidden selection authorization, handedness ID refits and ambiguity recovery. The updated host core group passed 40 JUnit tests; an additional summary correlation test passed with a host JSON implementation before adding its Android runner. All 17 Python tooling tests pass. Android Gradle cannot run directly on this host without downloading the distribution/SDK; full native tests, lint and both APK builds are delegated to the existing GitHub CI workflow, not a coding agent.

Added native regressions cover hidden pinch with an invalid index and commands off, held-contact loss, owner retention, actual node activation after epoch revocation, disarm rejection, highlight draw retention, moved target identity, selection event correlation, packed/padded capture, classifier rearming, report/recovery evidence and session presentation. The replacement-hand fixture now moves its palm, rather than representing a new hand solely by a classifier label flip.

Author self-review only; no multi-agent review. Preserve the baseline and existing PR; no merge, baseline promotion or production publication. Device validation remains for the new APK's physical pinch comfort, second-hand/crossing behavior, dynamic Android targets, highlight rendering, inference switching and timing improvement. No measured speedup or new device success is claimed.

## Final race review

The initial complete Android unit step passed in CI #337. Subsequent author review added:
- Ignore Aergis feedback/highlight window events over another app, preserving both its foreground safety state and target.
- Recheck dispatch and selection authority immediately before ACTION_CLICK, after the bounded window scan.
- Require another visible confirmation when the target moves during the confirmation scan; keep its safety guard running.
- Record unchanged navigation at boundaries explicitly, and record pre-inference expired frames without converting their pixels.
Each path has a native regression. The resulting source needs the next full CI run before being treated as build-verified.
