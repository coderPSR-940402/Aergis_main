# Repository follow-up and archived branch cleanup

The owner authorized the audit follow-ups on 15 November 2026. All 53 reviewed branch tips and complete history were preserved in a verified Git bundle before any deletion.

Archive: [Aergis_Branch_Archive_2026-11-15.zip](https://chatgpt.com/library?file_id=libfile_4d395af8a02c8191b2cf63a52eeb04ea&account_id=personal). It includes the bundle, reviewed inventory, SHA-256 and restore commands.

## Status and access boundary

No old remote branch has been deleted and no repository rule has been changed. Connected GitHub tools expose no rule/settings/deletion mutation. Browser passkey sign-in reported partial support and Authentication failed. Administrative access is therefore blocked; do not present these changes as applied.

## Main protection

Add a main-only rule requiring PRs, up-to-date GitHub Actions checks named **Build and test Aergis** and **CodeQL Java/Kotlin**, while keeping non-fast-forward/deletion protection. Do not require the main-only baseline-advancement job on PRs, or apply PR requirements to baseline-successful-apk. Preserve known-good/commit-50 as rollback evidence.

The previously inspected global deletion rule may block feature-branch cleanup. Do not disable repository-wide security to force cleanup. Any deliberate rescoping of deletion protection requires owner review and must keep the main/build/rollback refs protected. Configure automatic deletion of merged feature branches only once it can operate within the intended rules.

## Exact-SHA cleanup candidates

The 48 branches below are integrated or superseded in main307f5e9 according to the completed ancestry, stable-patch and implementation review. Before deleting any one, verify its remote tip still matches the archived SHA; a moved tip requires a fresh review. Preserve any branch attached to an open PR. No branch created after the snapshot is included. Unique voice and prototype branches are retained pending their replacement/evidence decisions.

| Branch | Reviewed and archived SHA |
|---|---|
| `audit/bound-free-frame-buffer-cache` | `9b24b2646c32e13247e4f2604933a72cf1d0237f` |
| `audit/frame-buffer-reuse-by-size` | `316f4d3808a187f9d561491ecb30500aa8949ef0` |
| `audit/frame-failure-callback-isolation` | `da2a71fb52a0d62eaea04850fc9d5d735b9be983` |
| `audit/worker-scheduling-recovery` | `70c9e43875fbdcd541acf2b5334661f6048bacef` |
| `cleanup/remove-unused-pointer-authorities` | `43bb76b6f1b45c94d3525077ba71c876fed7cea5` |
| `cleanup/repo-hygiene-2026-11-02` | `5e62a9a3578ef34b0e9fc418dd004bf3d123d014` |
| `cleanup/repository-hygiene` | `b605cd58f14a43bae4c341a9bfd63a0aa6b78619` |
| `feat/gaze-lab-guided-inapp-20261113` | `711cb2066a5e6260b9b65cde14b9a89cbada2987` |
| `feat/palm-relative-tracking-recovery-20261111` | `ed86c740e629295b747c7875a1e3abebe99419ef` |
| `feat/testing-mirror-recording` | `c85aa093378beba2e4851b023ece6236c3192c1a` |
| `feature/aergis-evolution-phase0` | `aaa283452b684137a3dcb317feb5c1219d796009` |
| `feature/improve-repository-hand-pref-and-build-3030686209234475684` | `7fada08e5b4129e77556c8110fc82e0d655d6b78` |
| `feature/live-diagnostics-relay` | `2c72d059e54ece6b96ee82af497fc11bc1b22b61` |
| `feature/mapping-comparison` | `6e0e8e96dfb098ebc2058dc2ec87c193bb2567d5` |
| `feature/replay-benchmark` | `9e17b72e7999b2936182a6c4d9e7e1992608c883` |
| `fix/audit-docs-apk-models` | `9f16188bb407b177d221313aa3b538879de61e48` |
| `fix/bounded-pinch-command` | `88d7177b1c5ccfb62f83be1d1ed40bdd6eb5d090` |
| `fix/camera-landmark-orientation` | `d3ce143cf52f1c678429789c1e60f9d946b88f1e` |
| `fix/classifier-geometry-continuity` | `264fca3b96ea3af793eb0eb7b97fd4baddbe7269` |
| `fix/command-deadlines-capture-safety` | `c684e2ddd7d2f2c596c8677acc586ff58f6c2340` |
| `fix/devloop-observation-gaps` | `5d1dd4d1eda736444e12b9f3d31f55fb2259d61e` |
| `fix/foreshortened-thumb-middle-contact-20261113` | `1f1f463b8e3b546d29d45d67b07c9cd2b5767f70` |
| `fix/live-calibration-comfort-reach` | `e8540aa59ff1239c7129f7525f891c98cb339815` |
| `fix/natural-hand-pointer-arbitration` | `2f42432452bc327aa0d08b2cb2cc0a02eaa6b4eb` |
| `fix/partial-hand-fallback-seeding` | `30a8cd070f1817f6c36767e13a95ddf582905ddb` |
| `fix/phase0-benchmark-fail-closed` | `28a6e93c65c05c14ccad9134eea4832cedc71e78` |
| `fix/phase0-test-discovery` | `2a9a92d542bbf353066b00e684acd59d68668881` |
| `fix/phase1-command-geometry-gate` | `2a58d771b910fa1ca497776f9dd9a409ca505abd` |
| `fix/phase1-owned-gesture-provenance` | `275d61526de1a07680dc46cc53ef551286be333c` |
| `fix/pinch-click-hotspot-race` | `bbc31e453e4240db34698575ecf39116c8e1d0d5` |
| `fix/pinch-confirmation-latency` | `2b3489a59e7b19cb4dc644983538214356612279` |
| `fix/pinch-post-release-delivery-20261113` | `2d85c0e1122723c1f760638f464b95e3aec7df3e` |
| `fix/pinch-tap-pending-delivery` | `57529047e4839b90bc4acfaaadc7baebaaf741a4` |
| `fix/pointer-edge-motion-continuity` | `bd5e970807623ed62ce3bb1b626cb72db8cd630a` |
| `fix/pointer-mapping-tracking` | `74caa7e3ce101ad8fbffbacf6b0458b9db3da3aa` |
| `fix/pointer-nonfinite-recovery` | `10e057d9954e7ea254b7d6989c989dff44e146e0` |
| `fix/share-recovery-preview-updates` | `356f9d8ec14dac658dd293a0d39f2b6f1e9cc869` |
| `fix/swipe-gesture-test-and-improvements` | `91d4f0afc66d15b3257b312a15630d66c716da4d` |
| `fix/swipe-test-and-optimization` | `5a9504ac52b17f635c342cba469f85246d208b59` |
| `fix/visible-hotspot-viewport` | `af38cc7a31dc960156cf29a5bc58185fb6b63225` |
| `gradle/pr1-wrapper-ci` | `da316fcf08a73c62c17eff50fdedbf0ff7b645ee` |
| `improve/gesture-recording-evidence` | `ebb35284fa20e4f931383ac0a37f9b26b6df1bc3` |
| `repair/live-diagnostics-relay-rebase` | `2d39257ea5841af9d860eb90a0d21073dd86b270` |
| `toolchain/phase-0-gradle-wrapper` | `41207fee52b1747347d5542cf91cde44bfbd92bb` |
| `upgrade/gesture-first-reliability` | `fe300e3e4cf81c2c491b195169924f0a617e0ff3` |
| `ux/control-cockpit-refresh-20261111` | `7199348b6467901be0a8a8b4ec86020276697f2c` |
| `ux/gesture-control-redesign` | `17a78488f1120196cd8ec8290f11fb9485fd6bc6` |
| `ux/practical-controls-and-build-id-20261111` | `2ef78119aba25dd71aa548993314395b098438bf` |

## Dependency verification

The review-only generator runs on PRs changing its workflow or verifier. It never automatically commits/trusts checksums. The reviewed current-main candidate is now checked in as `gradle/verification-metadata.xml`: SHA256 `0add1c09aee6f9e9decd4df9a8f7e49153b7ab7e9b7589b9686aaa44121adae9`, 434 components and743 artifacts. No trusted/ignored bypass entries or alternate artifact hashes were added.

CI43739293642 independently downloaded708 matching artifacts. The other35 initially failed because33 Gradle logical filenames differ from their declared module URLs and2 large artifacts follow a Central mirror redirect. Follow-up fresh official HTTPS downloads verified all35 against the same candidate. Alias locations require freshly downloaded candidate-pinned module bytes and remain in the same repository/group/module. Central mirror redirects require the exact source, fixed target and unchanged path; direct mirror starting URLs remain rejected. No size/time limits were relaxed. See [the split CI/local review record](evidence/dependency-bootstrap-2026-11-15.json) for exact provenance and hashes.

The verifier has24 focused tests; the full Python suite passes83. Fresh strict CI and the deliberate AGP checksum-tamper probe remain required before calling dependency enforcement complete. The probe restores the metadata in a finally block and retains its log in CI reports. Ordinary CI never regenerates trusted metadata.

This candidate covers current-main dependencies only. Optional voice/JitPack dependencies require separate capture and review before combining with strict metadata. Repository byte comparison does not establish publisher identity or vulnerability status.

## Device and provider gates

No attached Android device or emulator was available. Actual A54 labelled click/false-action, frame-loss/reacquisition, protected-screen, independent-channel, rotation and thermal checks remain physical-device gates. An exact SHA/run/version/checksum and observed target outcomes are required, not only dispatch callbacks.

Voice private-agent access additionally needs an operator-owned configured agent and an authenticated HTTPS token broker with server-held provider credentials and usage limits. No account, permanent key or anonymous public broker is created by these changes. The default app fails closed before microphone startup when unconfigured.
