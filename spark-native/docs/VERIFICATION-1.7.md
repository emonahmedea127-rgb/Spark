# Spark 1.7 verification — 2026-09-25

Build commit: 6e662fba867cd9e399e3e4e5a16733d0746eb5c0
GitHub Actions run: https://github.com/emonahmedea127-rgb/Spark/actions/runs/36151629940
Job: 108126052204

- assembleDebug and lintDebug succeeded. Lint has no errors; existing dependency/target SDK/Exif warnings remain.
- Android emulator API 29: 29/29 instrumentation tests passed, none skipped. Coverage includes new independent Name/Bio save patches, clearing a bio without modifying name, required-name validation, discard confirmation, separate edit entry points, profile count navigation and camera/story controls. Existing media, image cache, crop, chat, discussion, suggestions and picker tests also passed.
- JavaScript call lifecycle: 4/4 passed.
- Supabase rollback-only checks: 106 passed (43 base, 20 profile discovery, 12 profile pickers, 22 connections, 9 highlights).
- The original base suite assumed exactly 20 Spark tables; updated to 21 after adding Highlights, then all 43 checks passed. No RLS test was removed.
- Supabase security advisor: no new issues; pre-existing leaked-password-protection warning remains. Remediation: https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection
- Both v1.7 upgrades applied to project twywavuyghftkzsflfrf. Public wrappers are security invoker; scoped private lookup checks auth and blocks. All 21 Spark tables have RLS enabled.
- Inspected emulator screenshots spark-profile-v1.7.png and spark-bio-editor-v1.7.png. Header fits a narrow 320dp test viewport and editor has a single Bio field, public-audience description and top Save action.

APK:
- Package com.spark.social; versionName 1.7.0; versionCode 8; 22,019,927 bytes.
- SHA256: 88dc6b8958af314a6c250c5750aa767e2dfc06d158225163070cb99244d02f10
- APK v2 signer certificate SHA256: e9fdc8807f4a53342b345600971dfc873907f420959ac3425fa5c30665fe35d8 — matches prior Spark builds.
- Archive digest verified against GitHub artifact metadata; ZIP integrity, binary manifest package/version and embedded signing certificate checked locally.
- APK artifact 10872155544; reports artifact 10871883138.

Limits: emulator verification, not a test on the user's physical phone. UI tests use synthetic local profiles; backend tests use temporary synthetic users and rollback. Same certificate and higher version code support updating prior compatible builds, but this run did not perform an in-place device upgrade. Highlights use one existing photo post per card and its original audience. Actual user-specific network/media speed depends on connection and files. Spark implements the requested follower semantics, not Facebook's private algorithms or all Facebook features.

The follow-up source commit changes only verification docs and rollback SQL tests, not the APK's application code.
