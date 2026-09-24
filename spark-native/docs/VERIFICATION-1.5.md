# Spark 1.5.0 verification

- Version code: 6; package: `com.spark.social`.
- Build commit: `2e0dbf9904b0e4289f6eefa4df7d686c969f1eb0`.
- Successful GitHub Actions run: https://github.com/emonahmedea127-rgb/Spark/actions/runs/35953654606
- Build job: 107487289653.
- `assembleDebug` and `lintDebug`: passed.
- Android API 29 emulator: 20 tests, 0 failures, 0 skipped. Includes three new profile/discovery UI tests and all previous video playback, image, feed, crop, reaction and software-keyboard tests.
- JavaScript call lifecycle: 4 passed, 0 failed.
- Supabase rollback-only checks: 43 existing checks and 20 new profile/discovery checks passed. The tests leave no synthetic users or content behind.
- Editor and suggestions emulator screenshots were inspected. UI fixtures use placeholder portraits; they are not seed data in the live app.
- APK: 21,856,087 bytes.
- APK SHA-256: `cb317a987e5062485d35bb7647735879d681e2f124194309f933fee8c5df965f`.
- APK ZIP integrity: passed.
- APK artifact: 10789980573; archive SHA-256 `9bc070cf6b2f40c2ef24b9f65b1696673d1d95a44b7110011a59d13a2f0594ae`.
- APK v2 signer certificate SHA-256: `e9fdc8807f4a53342b345600971dfc873907f420959ac3425fa5c30665fe35d8`, identical to the delivered 1.4 APK. Version code increased, so in-place update is supported.

The security advisor found no new database warning. The pre-existing Auth warning remains: [leaked-password protection is disabled](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection).

Limits: the final APK has not been run on the user's physical handset. New Android UI tests use injected fixtures and callbacks; database behavior is verified separately through authenticated-role SQL transactions. No live friend requests were sent by tests. Ranking is a deterministic Spark heuristic, not Facebook's proprietary algorithm. It has not been load-tested at millions of users. See UPDATE-1.5.md for scoring, audiences and exclusions.
