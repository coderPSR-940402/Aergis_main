# Aergis 0.10.0-preview release candidate

## Candidate scope

This release candidate contains the 2026-11-19 audit-backed stabilization changes:

- Prefer the front camera and fall back to the rear camera on camera-optional devices.
- Reduce per-frame hand-selection allocations by reusing bounded collections and replacing temporary filtering/sorting with direct scans.
- Preserve the existing hand ownership, ambiguity, pointer fallback, epoch, and stale-frame behavior.
- Record the full audit and the remaining Gaze Lab lifecycle follow-up in [the audit report](audits/2026-11-19-aergis-audit.md).

## Verification status

- Python tooling suite: passed, 90 tests.
- Android Gradle tests/lint/APK builds: pending branch CI; the local sandbox has no Android SDK.
- CodeQL and dependency verification: pending branch CI.
- Physical-device acceptance: required and not claimed by this source-only candidate.

## Promotion gate

Do not promote this candidate to production until the exact CI APK is accepted on a representative device, including rear-only camera fallback, pointer/click safety, tracking recovery, accessibility dispatch, orientation changes, service restart, sustained-use latency, and thermal behavior. Production signing remains the protected manual workflow described in `docs/PRODUCTION_RELEASES.md`.
