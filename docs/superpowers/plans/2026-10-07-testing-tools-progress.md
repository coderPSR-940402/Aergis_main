# Inline execution ledger — testing tools

Baseline: 1f3ac6a051acea938fb3b79ebce91d4f5c0838ee, verified main and baseline-successful-apk.
Execution uses the isolated GitHub feature branch and local source mirror. No Android SDK is installed locally; Android compilation and tests run in CI. No subagents, preserving the user's preference. Standing implementation authority overrides repeated skill approval gates.

- Task 1 initial RED: run 37586002243, 146 tests / five missing-feature assertion failures; existing 141 passed.
- Implemented incremental JSONL and events, bounded queue, sampled JPEGs, summary PDF and ZIP.
- Compile check exposed Android PdfDocument does not implement Closeable; explicit try/finally resolves it.
- Full-suite regression exposed eager JSON construction while diagnostic recording is off; gate before constructing event records.
- Robolectric 4.17 has no native PdfDocument implementation. Tests substitute only the platform PDF container and preserve native Canvas/Bitmap report drawing. Layout QA uses the generated fixture; native Android PDF smoke test remains a phone check.
- Review RED: moved-mirror rotation test failed before bounds recovery was implemented.
- Run 37587741840: 149 tests, 148 passed. Recorder stop ordering, no-hand/JPEG/event preservation, aspect fit and padded RGBA rotation passed. Export-duration retry regression failed as expected.
- Fix: retain the first stop timestamp across PDF export retries. Final checks pending.
- Inline whole-branch review covered permission/URI grants, queue ordering and overload, data preservation, service/overlay lifecycle, configuration changes, and save-picker activity restoration. No unrelated tracking behavior was changed.
