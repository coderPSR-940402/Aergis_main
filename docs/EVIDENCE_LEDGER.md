# Aergis Evidence Ledger and Decision Log

## Current audit

The authoritative baseline is always the remote `baseline-successful-apk` ref.
For the 2026-10-08 audit it resolved to `008056747c93fb7f60f699b0c9eecf21d8dd09cd`.
See [the implementation and validation ledger](plans/2026-10-08-reliability-audit.md).
The current workspace has Java 17 and Android SDK 36 and can execute tests, lint
and both APK builds. Historical environment limitations below are not current.

## Historical Phase 0 evidence ledger

| ID | Evidence | Classification | Confidence | Decision/use |
|---|---|---|---:|---|
| E-001 | `main` and `baseline-successful-apk` both resolve to `602a26724728041d18b70838743906a61c0de1dc` | Verified | 1.00 | Historical audit reference only; resolve the remote baseline for new work |
| E-002 | Aergis CI run `37124289111` passed on baseline commit | Verified | 1.00 | Use as build/test/packaging baseline |
| E-003 | CodeQL run `37124289155` passed on baseline commit | Verified | 1.00 | Use as security-analysis baseline |
| E-004 | Manifest has cleartext disabled, no window-content retrieval, and non-exported capture service | Verified | 1.00 | Preserve as hard privacy/safety constraints |
| E-005 | Camera analysis uses `STRATEGY_KEEP_ONLY_LATEST` and closes `ImageProxy` in `finally` | Verified | 1.00 | Preserve; instrument freshness next |
| E-006 | Transaction gate rejects non-monotonic timestamps and resets after a large frame gap | Verified | 1.00 | Preserve; do not treat as complete pipeline freshness |
| E-007 | Prototype timing values around 220/450 ms freshness and 63 ms pointer latency | Directly observed/prototype reference | 0.65 | Treat as hypotheses requiring benchmark validation |
| E-008 | Physical-device campaign document requires zero unexplained actions in safety scenarios | Proposed acceptance rule | 0.90 | Use as release-gate target after device execution |
| E-009 | At Phase 0, the local sandbox did not provide the Android SDK | Verified | 1.00 | Historical environment limitation; superseded by the current audit |

## Decision log

### D-001 — Work from real remote `main`

**Decision:** Keep implementation branch based directly on remote `main` at `602a267`; do not use the earlier documentation/prototype-recovery branch as the implementation base.

**Reason:** The user requires changes to the actual Aergis repository and the directive requires comparison against the authoritative baseline.

### D-002 — First implementation slice is freshness measurement and rejection

**Decision:** Add a pure JVM-testable vision-result freshness policy before expanding gesture vocabulary or rebuilding the UI workflow.

**Reason:** It is a bounded safety/measurement slice that addresses a demonstrated architectural gap without changing normal valid-frame behavior.

### D-003 — Prototype timing values remain hypotheses

**Decision:** Do not hard-code prototype observations as product guarantees. Encode them as benchmark parameters or documented hypotheses until replay/device evidence supports them.

### D-004 — Device-dependent claims remain deferred

**Decision:** Do not claim production readiness for pointer smoothness, touch injection, IMU thresholds, camera latency, thermal impact, or service interruption until the physical-device campaign is executed.

### D-005 — No broad UI rewrite in Phase 0

**Decision:** Defer multi-screen prototype workflow recovery until the runtime safety and measurement boundaries are stronger.

**Reason:** A UI that visually resembles the prototype but lacks authoritative runtime state would violate the directive's truthfulness and safety gates.
