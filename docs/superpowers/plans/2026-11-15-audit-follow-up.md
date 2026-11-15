# Audit follow-up implementation plan

Goal: correct current capability documentation and verify both bundled models in current APKs.

Scope: Python artifact verification, its tests, the historical-source verification command, and documentation. No Android runtime changes, dependency upgrades, signing-key changes, release or baseline promotion.

## Tasks

- [x] Add failing APK-contract tests for valid, missing, partial, duplicate and corrupt face/gesture assets, CLI wiring and pins matching Gradle.
- [x] Keep the existing gesture-only `verify_model` interface for historical signing fixtures. Add `verify_models` for current APK CLI verification, requiring both pinned assets.
- [x] Verify historical production candidates with the current verifier, selecting explicit gesture-only mode only when their integrated source has no face-model declaration.
- [x] Correct README interaction/channel/neutral-rearm descriptions, accessibility disclosure and stale capability/status statements using current implementations.
- [x] Record exact branch-rule and dependency-verification prerequisites and device acceptance steps without claiming they were performed.
- [x] Run the focused and full Python suites, XML parsing, whitespace and diff review; report Android/SDK checks as blocked if unavailable.

Review focus: duplicate ZIP entries; correct-size corrupt model; one valid asset masking the other; CLI bypass of the full check; historical rollback compatibility. Tests cover each except physical SDK signing, which remains a CI gate.

## Execution record

- Base/main/build reference: c6aa7f4ad13500c40aaf8674f8bbcadf1ec68e02, freshly resolved remotely.
- Device acceptance of that exact APK: unavailable; no physical acceptance claimed.
- Working in a clean local clone on a dedicated branch; no shared branch mutation.
- Repository instructions require owner approval before commit. The owner authorized commit, push and draft PR publication on 15 November 2026 after reviewing the prepared changes.
- Dependency checksum generation is blocked by external Gradle/network access; no trusted hashes will be invented. GitHub tools expose no workflow dispatch or branch-rule mutation.
- Independent review: identified historical post-Gaze sources bypassing the face check through an older verifier. Tests reproduced the gap; explicit legacy mode with the current verifier corrects it. Final suite: 59 tests pass; focused suite: eight tests pass. YAML/XML and whitespace checks pass.
- Ruling: keep the isolated signing helper's gesture-only compatibility interface. Current build/unsigned candidate gates require both models; an independent face-model signing contract is deferred until it can preserve authenticated historical-source provenance. Cost if wrong: the signing step alone does not detect a removed/corrupt face asset after unsigned verification.
- Initial handoff left work uncommitted for owner review as the README requests; owner approval now permits commit and draft PR publication. Settings, merge and release remain outside this publication step.
