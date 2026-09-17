# Spark 0.2.0 verification

Date: 17 September 2026. This distinguishes automated build/tests, live backend checks and pending device acceptance.

## Created and deployed

- Supabase project **spark 2**, organization **AI Business Copilot**, region **ap-southeast-1** (Singapore).
- Project ref: `twywavuyghftkzsflfrf`. API URL: `https://twywavuyghftkzsflfrf.supabase.co`.
- Initial migration `spark_photo_app_initial` successfully applied through Supabase.
- Eight application tables with RLS, 27 application/Storage policies, two security-invoker views and two private JPEG buckets verified on the live database.
- `delete-account` Edge Function version 1 deployed and ACTIVE. Gateway JWT verification is disabled only because the handler validates the token with Auth and re-verifies the current password.
- Android source preconfigured with this project's public URL and modern publishable key. No service-role key or database password is in the source.
- The earlier Spark project was not changed.

## Executed local tests

`npm ci --ignore-scripts && npm test` completed using Node 24 and pinned PGlite 0.3.14:

- 34 PostgreSQL authorization/integrity subtests plus their parent test.
- Five source assertions for Android permissions, encrypted token storage, private media caching, deletion order and PKCE.
- Eight executable deletion-handler tests with mocked Auth/Storage HTTPS responses.

**48 tests passed, 0 failed.** Database cases execute real PostgreSQL RLS, triggers, privileges, constraints and invoker views against minimal local Auth/Storage metadata stubs. They cover private accounts, target-only follow approval, user spoofing, block visibility, bookmarks, comment ownership, orphan isolation, upload paths, tombstones and write limits. Handler tests execute the actual TypeScript source but mock outbound network responses.

## Executed live backend checks

`supabase/verify-rls-rollback.sql` passed all **12 assertions** against the new hosted database:

1. Owner can read own private post.
2. Stranger cannot read that post.
3. Private follow starts pending.
4. Requester cannot self-approve.
5. Target approval unlocks the invoker feed.
6. Like and save are reflected in the caller's feed.
7. Another user's bookmark remains hidden.
8. Blocking removes the follow edge.
9. Blocked profile is hidden.
10. Blocked feed is hidden.
11. A tombstoned identity is inactive.
12. A tombstoned identity cannot read its own posts.

The test created synthetic Auth/profile/post rows within one transaction and rolled them all back. A follow-up query confirmed **0 Auth users, 0 profiles and 0 posts**. No test signup email was sent, no token was issued, and no physical image was uploaded.

Live HTTPS checks using the public client key:

| Check | Observed result |
| --- | --- |
| Auth settings | HTTP 200; email signup enabled, email confirmation required |
| Anonymous profile read | HTTP 401; permission denied |
| Request using `Accept-Profile: spark_private` | HTTP 406 / PGRST106; private schema not exposed |
| Delete account without bearer token | HTTP 401; rejected by handler |
| Delete account with invalid bearer token | HTTP 401; rejected after Auth validation |

The Supabase **Security Advisor returned no findings**. Performance Advisor reported only informational unused indexes on the freshly created, empty tables. These indexes support authorization, foreign keys and pagination; they were retained. [Supabase unused-index advisor documentation](https://supabase.com/docs/guides/database/database-linter?lint=0005_unused_index).

## Android build verification

[GitHub Actions run 35245217789](https://github.com/emonahmedea127-rgb/Spark/actions/runs/35245217789) succeeded at source commit `9a5e16e2bc40d1d961c937b1dd059558b1c0290c` on `spark-2-preview`.

- Android Kotlin compilation and `:app:assembleDebug`: passed.
- `:core:test`: 25 tests, 0 failures, 0 ignored (10 RulesTest and 15 SparkApiTest).
- Node/PostgreSQL/handler tests: 48 passed, 0 failed.
- `:app:lintDebug`: passed with 0 errors and 23 warnings. Warnings include dependency updates, style suggestions, target API and backup configuration; no lint baseline or suppression was added to pass the gate.
- Fixed JUnit's non-void test signature and scoped light navigation-bar styling to API 27+ while retaining API 26 support.
- APK artifact `10506649874`, reports artifact `10507053150`.
- Delivered `Spark-2-v0.2.0-debug.apk`: 19,921,264 bytes; SHA-256 `23601cff853bd70c0861b084f89fa289e656498d93ae8c8ce8f492fbf18dcee8`.
- Downloaded APK archive and inner ZIP integrity checked. This is a debug build; it has not been installed on a phone or emulator in this task.

### Earlier local build limitations

- Gradle 8.13 was downloaded from its official distribution and verified against the checked-in SHA-256.
- `gradle --version` succeeded using the system Java 17 runtime.
- The checked-in wrapper's initial download failed with `Network is unreachable`.
- `:core:test` using the extracted Gradle failed during root-project configuration: Android Gradle Plugin `com.android.application:8.13.2` could not be resolved from Google/Maven/plugin repositories. An attempt using the environment's configured HTTP proxy failed the same way. A direct HTTPS check of the official Google Maven artifact timed out at proxy CONNECT.
- A separate official Temurin 17.0.20.1 JDK failed at JVM startup with an instrumentation assertion and `processing of -javaagent failed`.
- Runtime instrumentation and network controls were not disabled or patched.

Those local runtime limitations were resolved for build verification by using GitHub Actions, without disabling runtime instrumentation or network controls.

## Device acceptance not executed

- Two-account device acceptance checklist in `README.md`.

## Pending configuration and acceptance

- Hosted Auth configuration completed through the signed-in dashboard: exact redirect `spark://auth/callback`, Site URL `spark://auth/callback`, and minimum password length 12. Saved URLs were verified in the form; the password-length value was verified visually after reopening the provider. Native Site URLs are supported by [Supabase redirect documentation](https://supabase.com/docs/guides/auth/redirect-urls). Confirmation email must be opened on the phone with Spark installed, followed by password sign-in. No live confirmation/recovery email was exercised.
- Custom SMTP has not been configured. No mail-provider credentials were supplied, and the default Supabase service restricts recipients and sending volume.
- No real signup, confirmation email, password recovery, authenticated photo upload/download or successful account deletion has been tested against the live services.
- Device checks remain required, including small screens, large fonts, dark mode, rotation, camera, picker, offline errors and session restoration.
- Stories, Reels, chat, push notifications, Google login and video processing are not implemented in this photo-sharing preview.
- Release signing and public launch preparation remain outside the completed preview.

The backend deployment is real and verified within the scopes above. It is not a claim that the complete Android application is production-ready.
