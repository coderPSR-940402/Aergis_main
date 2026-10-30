# Audit of the touch-free control and release candidate

Starting source: 9890573089845af9f40c82c2fd7364ba111ab3be (PR #74).
Resolved build reference: baseline-successful-apk = a53596b2d2c25ced3026d42a31d9b10b60225b5f.
Device acceptance remains separate; no newer recording was supplied.

Reproduced before correction:
- A fist cancellation after stable aim emitted Capture; interrupting the fist could also consume the old aim. Give cancellation exclusive ownership of that pose and clear contact/aim evidence.
- Duplicate row identities trapped Next at the same identity. Navigate only unique identities; confirmation remains fail closed.
- Replacing a collection container ID at the same path retained an identical row and could click it. Bind collection-row identity to the ancestor container scope without binding it to unrelated row content or bounds.
- Rejected native cancellation was recorded as the original command's terminal rejection. Its eventual completion/cancellation then could not update feedback. Keep cancellation-attempt failure in telemetry; only authoritative original callbacks determine the original outcome. Keep native input occupied until acknowledgement.

Tests: production core and output controllers were reproduced failing in a host harness with small Android API doubles. Matching Robolectric tests exercise actual Android boundaries in CI. Native touch semantics still require device acceptance.

Release audit: previous green CI verified preview APKs and unsigned release; it did not execute a successful production signing operation. Add an isolated SDK signing smoke test using a temporary fixture APK and ephemeral test key, exercise success and wrong-certificate/provenance failures, and retain no fixture keys/APKs as release artifacts. This proves the signing plumbing only. Actual production credentials, signed production installation/rollback, and saved-data compatibility remain unverified.

Workflow validation also found an existing unsupported github.run_started_at
context, which generated an empty timestamp. Generate baseline metadata with an
explicit UTC timestamp and verify both workflows with actionlint.

Local corrected checks: 92 core JUnit tests and 3 callback/container tests pass;
17 Python tests pass. Android API doubles in the host harness do not establish
native behavior. Complete Android CI, lint, APK verification and the SDK signing
smoke are required before completion.

Scope: preserve mapped gestures, camera/inference/filter/contact math and the existing preview identity. No subagents, merge, baseline promotion or production publication.
