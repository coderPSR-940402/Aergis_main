# Touch-free control and production delivery

Build reference: baseline-successful-apk = a53596b2d2c25ced3026d42a31d9b10b60225b5f.
Device evidence remains in EVIDENCE_LEDGER; the current PR has no device acceptance.

1. Dedicated disarmed calibration input: stable aim, thumb-middle corner capture, ordered corners, held open palm to confirm, held fist to cancel. Reset evidence on loss, owner/epoch changes, lifecycle exit; preserve saved profile until valid explicit confirmation.
2. Preserve bounded same-hand identity through missing detections; reject ambiguity, replacement and expired history. Retain only motion history; reset action transactions on loss and ease cursor recovery.
3. Re-resolve unique collection-row content independently of list position; retain conservative whole-window identity without collection metadata. Reject duplicate/replaced/disabled/password targets and refresh highlight bounds.
4. Present honest, transient action outcomes with request correlation so old callbacks cannot overwrite a newer command. Retain aggregate telemetry.
5. Manual production candidate signing and forward-version rollback, protected from PR code and public preview keys. Verify source ancestry, version, package, models, private signing certificate and checksums; record provenance. No private key is currently available, so no production-signed artifact is claimed.

Validation: pure core JUnit, Android/Robolectric/Compose regressions, Python release-policy tests, lint and both APK builds in CI. Review sensitive action authority, accessibility identity, calibration persistence and signing boundaries. Keep PR unmerged; physical acceptance and production credentials remain separate requirements.

Implementation checks: 89 production-core JUnit tests pass (Android-only APIs use
host stubs); 17 Python tests pass. Regression failures first reproduced identity
loss, opposite-hand takeover and duplicate-selection retention. Added native
regressions cover calibration confirmation, epoch rejection, dynamic-row movement,
duplicate rows and request-specific native callbacks. First Android CI compiled the app and ran 540 tests; it exposed three recovery
assertions. The short-occlusion identity expectation now follows the requested
motion-retention contract, while the expired-history first frame fails closed.
Cursor catch-up retains IDLE for its first recovery frame, then held contact
remains AIMING without replay; native tests now assert both. A separate stale-aim
regression first failed, then passed with the pre-contact trust deadline.
A held-contact/palm-label confirmation regression also first failed, then passed
once explicit projected finger separation became required for confirmation.
The corrected full Android CI is pending.
Private production signing and exact-APK device acceptance remain unverified.
