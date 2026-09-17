# Spark for Android

A native Kotlin + Jetpack Compose photo-sharing project connected by default to your new **spark 2** Supabase project. Package: `com.webgenius.spark`. This is a **0.2.0 source preview**, not an Instagram-scale production release.

## What is included

- Email/password signup, login, encrypted session persistence and token refresh.
- Email-confirmation flow and PKCE password recovery on the same Android device.
- Username, display name, bio, profile photo and private/public-on-Spark controls.
- Photo picker and camera capture through the installed camera app. Downsampled JPEG upload with EXIF/GPS removed.
- Chronological, paginated feed of authorized posts. Likes, comments, bookmarks and post deletion.
- Username-prefix search, follow/unfollow, private follow requests, accept/decline.
- Block/unblock, report queue and password-confirmed account/media deletion.
- Light/dark system theme, loading/errors, empty states and accessible button labels.
- Eight RLS-protected tables, two invoker-security views, two private buckets, basic database write limits and a deletion Edge Function.

Stories, Reels, direct messages, push notifications, video processing, recommendation ranking, Google login and a moderation dashboard are **not implemented**. Requests use explicit refresh, not Realtime. No dummy accounts or shared demo database are included.

## Status of this delivery

The **spark 2** project was created in **AI Business Copilot**, Singapore, on 17 September 2026. Project ref: `twywavuyghftkzsflfrf`. The database, private Storage buckets and `delete-account` Edge Function are deployed. The Android source includes only its public URL and publishable key in `BundledProject.kt`; it opens directly at sign-in and still allows changing projects.

**Do not rerun `supabase/setup.sql` on spark 2. It is already applied.** The hosted deployment passed 12 authorization assertions with all fixtures rolled back, anonymous HTTP denial checks, and the Security Advisor with no findings. The 48 local Node/PostgreSQL tests also passed. See `VERIFICATION.md` for scope and evidence.

**Still pending:** hosted Auth redirect/minimum-password settings and SMTP, an Android build and device acceptance test. The dashboard requires a separate sign-in. The available Java runtimes have not produced a successful build; no APK is included. The supplied manual GitHub workflow has not been run against your account.

## Quick start on Windows

1. Unzip the project. Open this folder in Android Studio, not just the `app` folder.
2. Use JDK 17 for Gradle. Install Android SDK Platform 36 and Android SDK Build-Tools 36.0.0 using SDK Manager. Android 8.0/API 26 or newer is supported by the app configuration.
3. Let Gradle sync. The checked-in wrapper downloads Gradle 8.13 and verifies its SHA-256 digest. Android Studio normally creates `local.properties` pointing to your local SDK automatically. Never copy a Linux SDK path onto Windows.
4. Use the already-created **spark 2** project. Complete the pending Auth settings in `SETUP-BN.md`; database and deletion setup are already done.
5. Run `gradlew.bat :core:test :app:assembleDebug :app:lintDebug` in the Android Studio terminal.
6. Install `app/build/outputs/apk/debug/app-debug.apk` on your test phone, or run from Android Studio. This is a debug build, not a Play Store release.
7. Spark opens at sign-in with **spark 2** preconfigured. The optional **Change backend connection** screen can select a different configured project or restore spark 2. Only modern publishable keys beginning `sb_publishable_` are accepted.
8. Sign up two test users, confirm their email addresses, then complete the two-account acceptance test below.

If you prefer CI, add this source to your own GitHub repository and manually run **Actions → Build Spark preview → Run workflow**. The workflow produces an APK only if compilation/tests succeed. No repository was created or pushed automatically.

## Supabase setup

### 1. Database and buckets

**Completed for spark 2.** The instructions in this section are for provisioning a different, empty project later. In that new project's SQL Editor, run `supabase/setup.sql` once. It is a single transaction and deliberately stops if table names already exist. Do not bypass that error by dropping existing tables.

The script creates tables, policies, indexes, views and bucket configuration. It does not create sample users or upload real images. Use only `public` as Spark's exposed application schema. **Never expose `spark_private` through the Data API.** It contains tightly scoped RLS lookup/trigger functions, not public RPC endpoints.

The setup includes explicit grants because newly created tables are not automatically exposed in newer Supabase projects. RLS and grants are separate layers. [Supabase Data API change](https://supabase.com/changelog/45329-breaking-change-tables-not-exposed-to-data-and-graphql-api-automatically).

### 2. Authentication

- Email/password signup and email confirmation are verified enabled on spark 2. Set the hosted minimum password length to 12; its current value has not been verified through the dashboard.
- Add the exact redirect URL `spark://auth/callback` to Authentication's allowed redirect URLs. Do not use a broad wildcard.
- Set Site URL to a real confirmation landing page you control. A signup email confirms the account; the user then signs into Spark. Recovery uses the app callback instead.
- Open password recovery emails on the same phone that requested them. PKCE stores the verifier only on that device; requests expire locally after one hour and codes are subject to Auth's shorter server limits.
- Configure your own SMTP provider before inviting general users. The default mail service has recipient restrictions and rate limits. This project does not send SMTP credentials from the app. [Supabase SMTP documentation](https://supabase.com/docs/guides/auth/auth-smtp).
- Do not enable CAPTCHA without adding its client flow. CAPTCHA integration is a launch hardening item in this source preview.

### 3. Account deletion

**Already deployed and ACTIVE on spark 2.** For future updates, deploy `supabase/functions/delete-account/index.ts` as an Edge Function named `delete-account`. It uses the server-provided `SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` environment variables. Never copy the service-role key into Android, a screenshot, a public repository or a chat message.

The function configuration sets `verify_jwt = false` because it performs its own mandatory Auth server validation of the caller's token, followed by current-password verification. This is **not an unauthenticated deletion endpoint**. Preserve both checks. If the dashboard offers automatic JWT verification, disable it for this function only after verifying that the supplied handler is deployed unchanged.

Using an installed Supabase CLI, inspect `supabase functions deploy --help`, then deploy to the explicitly chosen project:

```bash
supabase functions deploy delete-account --project-ref twywavuyghftkzsflfrf --no-verify-jwt --use-api
```

The command creates/updates this function remotely, so run it only for the confirmed new Spark project. It does not apply `setup.sql`. `supabase/config.toml` is a local configuration reference, not proof that hosted Auth settings were changed.

Deletion first marks the profile as deleting so existing access tokens lose data access through RLS. It revokes refresh sessions, removes actual files using the Storage API, then deletes the Auth user and cascaded profile data. Partial failures return an error, never a success message. Retry from settings. If the process is interrupted, sign in again and retry with the account password; an administrator may need to complete cleanup for unexpected storage paths. No permanent deletion occurs merely by opening settings.

### 4. Read-only verification

Run `supabase/verify.sql` in SQL Editor and review the Security Advisor. Expected: eight tables with RLS, two `security_invoker=true` views, private buckets, no missing post-media links, and no unexpected deleting profiles. The orphan query reports possible interrupted uploads without deleting anything.

## Where data is stored

| Information | Storage location |
| --- | --- |
| Email identity and password authentication | Supabase Auth, not public profiles |
| Username, name, bio, privacy choice, avatar path | `public.profiles` |
| Captions, author, file reference | `public.posts` |
| Photo bytes | Private `spark-media/<user UUID>/<post UUID>.jpg` |
| Avatar bytes | Private `spark-avatars/<user UUID>/<random UUID>.jpg` |
| Likes, comments, bookmarks | `public.likes`, `public.comments`, `public.saves` |
| Follow requests and accepted connections | `public.follows` |
| Block list and moderation reports | `public.blocks`, `public.reports` |
| Access/refresh tokens and reset verifier | Device-encrypted preferences using Android Keystore |

The Android code talks to the documented Supabase Auth, PostgREST and Storage HTTPS APIs through one `SparkApi` adapter; it does not require Firebase or a user-supplied AI API key. Private media requests carry the signed-in user's JWT, so changing privacy or blocking takes effect on subsequent requests. Already downloaded images/screenshots cannot be remotely recalled. [Private bucket access](https://supabase.com/docs/guides/storage/buckets/fundamentals), [Auth PKCE flow](https://supabase.com/docs/guides/auth/sessions/pkce-flow).

## Two-account acceptance test before inviting users

1. Sign up Alice and Blake with separate email addresses; confirm both and log in on two devices/emulators.
2. Leave Alice private and publish a photo. Confirm its actual object exists in `spark-media` and `posts.image_path` matches it exactly.
3. Blake finds Alice by username but cannot see her photo or retrieve its authenticated Storage URL.
4. Blake requests to follow. Alice refreshes Requests and accepts. Blake reopens the profile and can now see the photo.
5. Blake likes, comments and saves the photo. Verify feed counts. Alice must not see Blake's private saved list.
6. Alice blocks Blake. Refresh Blake's screen. Neither account should discover the other; the old photo request must fail.
7. Unblock and verify the old accepted follow is not restored automatically.
8. Test app restart, token refresh, airplane mode, upload rejection, email recovery, camera rotation and duplicate taps.
9. Report a post and verify the report is visible to the reporter/project administrator, not the reported account.
10. On a disposable test account, confirm deletion with its password. Verify Auth identity, profile, posts and both buckets' user-folder files are removed. Test a partial failure and retry too.

## Tests

```bash
npm ci --ignore-scripts
npm test
./gradlew :core:test :app:assembleDebug :app:lintDebug
```

Node 24 is used for the tests, including direct execution of the TypeScript deletion handler with mocked HTTPS responses. PGlite runs real PostgreSQL row-security rules against minimal Auth/Storage metadata stubs. Kotlin unit tests cover API request formatting, refresh races, upload ordering, validation and PKCE using an in-process fake transport. They are provided but were not executable in the authoring runtime.

## Important limits before production

- No Android build/device/visual QA or complete two-device Supabase end-to-end test has passed in this delivery environment. Database and unauthenticated HTTP checks have passed. Run and fix any build/device issues before distribution.
- Username search shows up to 40 results; Requests shows up to 40 pending users from a capped query. Comments show the latest 100. Feed/profile posts use composite cursor pagination. These are preview limits, not a claim of unrestricted scale.
- A failed/ambiguous upload may leave an unattached file. The app never deletes a file after an ambiguous insert result because the insert may have committed. Review the read-only orphan report; delete confirmed old orphan files through Storage API after a safe retention window. Automatic orphan cleanup, resumable background upload and idempotent upload retries are not implemented.
- The database has basic per-caller write limits. They are not a complete abuse-prevention system: storage quota enforcement, CAPTCHA, network rate limiting, malware/content review, monitoring and operational alerts still need work.
- Reports are stored for human review in the Supabase dashboard. There is no staffed moderation service or automated moderation. Arrange this before opening registration to the public.
- The age checkbox is self-attestation only. Production age-appropriate design, actual terms/privacy documents, safety reporting contacts and distribution requirements need dedicated review.
- Cloud storage, compute, bandwidth and SMTP can incur costs. No budget, free-tier availability, delivery SLA or thousands-of-users capacity is promised or assumed.
- A release build needs your own signing key, safe key storage, app-store metadata and release testing. No signing key is included.

## Source map

`app/` holds the native screens, state and Android platform features. `core/` holds the API client, models and JVM tests. `supabase/` holds one-time setup, read-only verification and deletion function. `tests/` holds executable database/security checks. `.github/workflows/android.yml` is a manually triggered build, not a deployment.
