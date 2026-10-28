# Production candidates and forward-version rollback

The committed preview keystore is public and must never sign production APKs.
The production workflow is manual, runs from `main`, and creates an **unpublished
draft release**. Tests, lint and unsigned minified assembly run in a job without
signing credentials. A separate protected job signs and verifies the package,
version, model, signer fingerprint and APK checksum. It does not run Gradle or
source-revision scripts. Previous drafts/artifacts are not deleted. This workflow
never advances the functional or CI baseline.

## One-time private signing setup

Create the GitHub environment `production-signing`, restricted to the `main`
branch. Add environment secrets `PRODUCTION_KEYSTORE_BASE64`,
`PRODUCTION_STORE_PASSWORD`, `PRODUCTION_KEY_PASSWORD`, and `PRODUCTION_KEY_ALIAS`.
Set environment variable `PRODUCTION_CERT_SHA256` to the independently checked
64-digit production certificate fingerprint. The verifier refuses the public
preview certificate, missing pins, wrong certificates and multiple signers.
Do not paste private keys or passwords into chat, issues, repository files or logs.
Keep an offline backup of the original private signing key. If a private release
identity already exists, reuse it; generating a replacement breaks upgrades.
No private key or production environment was available during implementation.

Environment branch restrictions are required repository configuration, not
something declaring an environment in YAML enforces. Configure them before
adding secrets. GitHub describes the protection boundary at
https://docs.github.com/en/actions/concepts/workflows-and-actions/deployment-environments .
Android signing uses environment-backed password inputs:
https://developer.android.com/tools/apksigner .

## Produce a candidate

After merging and accepting the exact source on a device, dispatch **Aergis
production candidate** from `main`. Supply its full commit SHA, mode `release`,
and leave data compatibility review false unless performing rollback. The source
must be an ancestor of the workflow's main commit. All source tests, lint,
minified assembly and APK verification must pass again. The first supported
production source must contain the production version override added with this
workflow; earlier source without it fails metadata verification.

Each new workflow run uses version code `2000000000 + run_number`. This distinct
namespace exceeds current previews and increases for normal releases and
rollbacks alike. A rerun retains its version; rerun the whole workflow so build
and signing provenance have the same attempt. A rerun after a draft was already
created does not replace the draft. Review the existing exact artifact instead.
This workflow has a fixed maximum code of 2100000000; it fails closed at the
Android publishing limit. Do not reset/copy this workflow's run-number sequence
or silently change the version namespace after production starts.

The draft retains the APK, SHA-256, public certificate report and release manifest
with source/workflow commits, run, attempt, version and candidate kind. Promote
only after exact-APK physical acceptance and checking that its version exceeds
all distributed production APKs. This implementation does not auto-publish a
public release or modify an installed phone.

## Roll back behavior while preserving Android upgrade direction

Choose an earlier **accepted production source** from its retained manifest.
Review that revision against current preferences/schema and export current
settings if supported; do not assume old code safely reads future data. Dispatch
the same workflow from current `main`, with that older full SHA, mode `rollback`,
and the explicit data compatibility review checked. It rebuilds the older source
with a fresh higher version code and the same private certificate. Verify the
new manifest and candidate on a device before promotion. No `adb -d`, uninstall,
data clearing or automatic retry is used. If old code cannot read current data,
produce a compatible recovery change instead; this workflow does not migrate or
restore app data automatically.

A private production certificate cannot update an installation signed with the
public preview certificate. Plan the initial migration separately; this pipeline
never uninstalls the preview app or clears its settings to bypass Android checks.

## Signing smoke coverage

Normal CI also runs the signing CLI against a disposable SDK-built fixture APK
and a newly generated ephemeral test certificate. It checks real signing,
signature verification, metadata/checksums, wrong private signer, public preview
pin, provenance rejection and temporary-key cleanup. Fixtures live outside the
workspace artifact paths and are deleted; they are never releases or install
candidates. This does not configure production credentials, exercise the manual
production workflow end to end, install an APK, or verify device/data rollback.
