# Spark 1.8 verification — 26 September 2026 (Asia/Dhaka)

Build commit: 2d615b779e60d94b89528b27e61067e41d2ce495
Run: https://github.com/emonahmedea127-rgb/Spark/actions/runs/36186716985
Job: 108241720424

- APK build and lint succeeded; no lint errors. Existing dependency/SDK/Exif warnings and deprecated audio routing warnings remain.
- Android API 29 emulator: 34/34 tests passed, zero failures/skips. Five new tests cover tapping comments, reaction/reply menu, ownership-specific comment edit/delete and message edit/unsend controls, receipt formatting, and preventing reopening an opened photo. The previous 29 tests remain passing.
- JavaScript call tests: 6/6 passed, including remote audio element playback and Android speaker-bridge invocation. These are signaling/audio-routing tests, not a two-device physical microphone conversation.
- Current backend verification: 91 checks passed (43 base, 26 social/chat, 22 connection regression). The base suite now expects all 22 Spark tables to have RLS. The previously verified discovery/picker/highlight suites were not changed.
- Live HTTP integration succeeded with two isolated synthetic Auth users: password sign-in, conversation creation, image upload, view-once send, rejection of direct recipient Storage access, rejection of sender consumption, first recipient download with exact matching bytes, second download rejected with HTTP 410, persisted seen/opened timestamps readable by sender. Test users and test Storage objects were removed and zero remaining objects/users verified.
- Deployed Edge Function spark-view-once version 1, verify_jwt=true. Handler additionally verifies user JWT with Auth. Function's service credential remains server-side. Atomic claim is executable only by service_role.
- Security advisor: the once-media vault has RLS and no client grants or policies deliberately (deny by default); an informational no-policy finding is expected. Pre-existing leaked-password protection warning remains. See https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection and https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy

APK package com.spark.social, versionName 1.8.0, versionCode 9; 22,053,051 bytes.
SHA256: 2b3705257364fbf82adb90341f0f3dd52be5028df92501339b2335f91631007e
Embedded signer certificate SHA256: e9fdc8807f4a53342b345600971dfc873907f420959ac3425fa5c30665fe35d8 (matches v1.7).
APK artifact 10887135596; reports artifact 10886475940. Both archive digests and ZIP integrity verified, APK binary manifest and v2 certificate checked locally.

Limits and behavior are in UPDATE-1.8.md: no configured TURN relay or background call push, no physical two-phone audio verification, view-once consumes access on request even if delivery fails, and no scheduled orphan-upload cleanup yet. This build must not be described as universally reliable calling across mobile/restrictive NATs. Same signer and higher version code support an update, but no physical in-place installation was tested.

The verification follow-up commit changes only documentation and rollback SQL tests. APK application code matches the build commit.
