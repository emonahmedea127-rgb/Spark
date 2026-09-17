# Spark 0.2.0 verification

Date: 17 September 2026. This distinguishes local tests, live backend checks and the unbuilt Android client.

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

## Android build attempts

- Gradle 8.13 was downloaded from its official distribution and verified against the checked-in SHA-256.
- `gradle --version` succeeded using the system Java 17 runtime.
- The checked-in wrapper's initial download failed with `Network is unreachable`.
- `:core:test` using the extracted Gradle failed during root-project configuration: Android Gradle Plugin `com.android.application:8.13.2` could not be resolved from Google/Maven/plugin repositories. An attempt using the environment's configured HTTP proxy failed the same way. A direct HTTPS check of the official Google Maven artifact timed out at proxy CONNECT.
- A separate official Temurin 17.0.20.1 JDK failed at JVM startup with an instrumentation assertion and `processing of -javaagent failed`.
- Runtime instrumentation and network controls were not disabled or patched.

**No Kotlin compilation, JVM test execution, Android lint, APK, emulator run or phone test has succeeded.** A successful Gradle version command does not establish that the app compiles. The original source had a syntax-only parse on 16 September; that is not a build and is not presented as verification of this updated source.

## Provided but not executed

- 25 Kotlin/JVM tests for validation, RFC 7636 PKCE, request authentication, refresh races, pagination and upload ordering.
- Manual GitHub Actions workflow for Node tests, JVM tests, Android lint and a debug APK. No repository was created or workflow started.
- Two-account device acceptance checklist in `README.md`.

## Pending configuration and acceptance

- Dashboard sign-in is required to add the exact `spark://auth/callback` recovery redirect, configure a real confirmation Site URL, and verify/set the server minimum password length to 12. The local `config.toml` does not update hosted Auth settings.
- Custom SMTP has not been configured. No mail-provider credentials were supplied, and the default Supabase service restricts recipients and sending volume.
- No real signup, confirmation email, password recovery, authenticated photo upload/download or successful account deletion has been tested against the live services.
- Android compilation and device checks remain required, including small screens, large fonts, dark mode, rotation, camera, picker, offline errors and session restoration.
- Stories, Reels, chat, push notifications, Google login and video processing are not implemented in this photo-sharing preview.
- Release signing and public launch preparation remain outside the completed preview.

The backend deployment is real and verified within the scopes above. It is not a claim that the complete Android application is production-ready.
