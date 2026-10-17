# Aergis repository instructions

## Build reference and device-verified functional baseline

The Aergis baseline is **dynamic**. Never use a fixed historical commit, APK filename, version label, or commit number as the development baseline.

The automatic CI build reference is:

`baseline-successful-apk`

It must point to the commit on `main` associated with the newest **successful Aergis CI run that produced and verified the APK artifacts**.

A build qualifies only when the Aergis CI workflow completes successfully after:
- unit tests pass;
- Android lint passes;
- debug APK assembly succeeds;
- release APK assembly succeeds;
- debug APK verification succeeds for the expected package/version;
- release APK verification succeeds;
- APK checksums are generated; and
- the workflow successfully updates `baseline-successful-apk`.

Failed, cancelled, incomplete, or unverified builds never become the baseline. Pull-request builds do not replace the `main` baseline.

## Before changing code

1. Resolve `baseline-successful-apk` from GitHub.
2. Treat that commit as the CI-verified build reference, not device verification.
   Resolve the latest explicit device confirmation separately in `docs/EVIDENCE_LEDGER.md`.
   If the tested APK cannot be tied to a commit/run, record the identity as unknown; do not guess.
3. Review changes against that baseline rather than against an arbitrary historical commit or remembered APK.
4. Do not manually move the baseline reference to an unverified commit.

## After changes

A successful `main` Aergis CI run automatically advances `baseline-successful-apk` to the newly verified commit. This advances the build reference only. The functional baseline requires explicit
device confirmation tied to the exact APK, commit and run; CI alone cannot advance it.
Do not reset current development to an older tested commit or overwrite newer work.

## Repository principle

Keep changes incremental and verifiable. Do not replace working Aergis implementation with unrelated historical repository code.
