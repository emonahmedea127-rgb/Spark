# Spark verification report

Updated: 23 September 2026 (UTC)

## Delivered state

Native Kotlin/Compose Android source project, deployed Supabase backend and reproducible validation/build instructions. **APK built successfully in GitHub Actions. Android compilation and lint passed.** The local network restriction was resolved by using the connected GitHub runner. Real-device acceptance testing is still not performed. This is a debug build, not a production release or complete Facebook feature parity.

## Backend deployed

Project: `spark 2` (`twywavuyghftkzsflfrf`). Migration: `spark_new_android_backend`.

- 17 new `public.sparknew_*` tables.
- Isolated helper schema `sparknew_v1_private`.
- Private bucket `spark-media-v1`, 25 MiB maximum object size.
- Every new exposed table has RLS enabled and explicit authenticated grants.
- Existing social tables, storage buckets, and Auth settings were left unchanged.
- Authentication uses this project's existing shared Auth tenant. New-app profiles are created on first sign-in.

| Data | Location |
|---|---|
| Accounts/password authentication | Supabase Auth |
| Names/bios and avatar/cover references | `sparknew_profiles` |
| Posts, stories, reels and media references | `sparknew_posts` |
| Reactions, comments, bookmarks | `sparknew_reactions`, `sparknew_comments`, `sparknew_saved` |
| Friends, follows, blocks | `sparknew_friendships`, `sparknew_follows`, `sparknew_blocks` |
| Conversations and messages | `sparknew_conversations`, `sparknew_messages` |
| In-app notifications | `sparknew_notifications` |
| Groups/pages and memberships | `sparknew_communities`, `sparknew_memberships` |
| Marketplace and reports | `sparknew_listings`, `sparknew_reports` |
| Call negotiation and ICE | `sparknew_calls`, `sparknew_ice` |
| Images/videos | Private Storage bucket `spark-media-v1` |
| Session credentials on device | Android Keystore-encrypted app-private preferences |

Media paths contain the owner's UUID and generated filename. Read authorization checks the referencing row's RLS. Signed media URLs last 60 seconds; an already issued URL can remain usable until expiration after a block or visibility change. Calls use peer-to-peer WebRTC media, which is not recorded in Supabase. Only signaling records are stored.

## Tests actually executed

**27/27 database tests passed against the connected database.** `backend/verify.sql` used three synthetic users in a single transaction and rolled all fixtures back. The result is recorded in `database-tests.json`. It covers:

- RLS on all 17 tables and a private media bucket.
- Own/public/friends/private post visibility and server-controlled story expiry.
- Author impersonation and cross-owner media-reference rejection.
- Friend request role enforcement and accepted-friend visibility.
- Comment permissions and immutable post ownership.
- Recipient-only messages/media and rejection of third-party reads/writes.
- Server-created notifications and rejection of client-forged notices.
- Call/ICE access isolation and recipient acceptance.
- Bidirectional block enforcement for profiles, posts, conversations and media.

**4/4 JavaScript call tests passed.** `node --test tests/call.test.cjs`, recorded in `call-tests.log`, checks caller/recipient negotiation, ICE sequencing, cursor advancement, media cleanup, permission denial, mute/camera controls and duplicate-boot prevention. These tests use mocked media and signaling; they do not prove actual WebRTC interoperability.

Other checks completed: JavaScript syntax, parsing Android XML, and a comment/string-aware Kotlin delimiter scan. The delimiter scan found and enabled correction of missing UI braces. It is not a Kotlin compiler or type checker.

## Historical local build attempt (superseded by successful cloud build)

`./gradlew :app:assembleDebug --console=plain` failed before project compilation while downloading Gradle 8.11.1:

```text
java.net.SocketException: Network is unreachable
```

This environment has no Android SDK or complete JDK compiler. No emulator or attached Android phone is available. `build-attempt.log` preserves that earlier local failure. A later GitHub Actions build successfully completed compilation, APK packaging and Android lint. Installation and end-to-end UI/Auth/Storage/real-call tests remain unverified.

## Code corrections during review

- Repaired four missing Compose block delimiters.
- Derived token expiry from Auth `expires_in` when `expires_at` is absent.
- Shared one API/session instance between the main activity and call activity.
- Preserved uploaded media when a write has an uncertain network outcome, rather than deleting media that may already reference a committed record. Explicit validation/client rejections still clean up the upload.
- Reloaded all loaded feed pages on refresh to avoid stale reactions on earlier pages.
- Kept the call activity alive across rotation through manifest configuration handling.

## Security advisory

Supabase's security advisor reported one project-level warning: leaked-password protection is disabled. This existing Auth setting was not changed. The report returned no missing-RLS warning for the new schema.

Remediation reference: https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection

## Production gaps

See `README.md` and `DEVICE_TESTS.md`. In particular: no background push or incoming-call service, no TURN relay credentials, no actual device call test, no Realtime subscription, no moderation console, no automatic orphan/expired-media cleanup, no Google/phone login, and no performance/load test. Chat/call updates use foreground polling. User-facing lists have documented bounds. Full Facebook feature parity is not implemented.

## References used

- https://developer.android.com/build/releases/agp-8-9-0-release-notes
- https://developer.android.com/develop/ui/compose/bom/bom-mapping
- https://supabase.com/docs/guides/auth/passwords
- https://supabase.com/docs/guides/storage/serving/downloads
- https://supabase.com/changelog?types=breaking-change

## Successful APK build

- Run: https://github.com/emonahmedea127-rgb/Spark/actions/runs/35792630432
- App source commit: `cbe962346d2fe9be5f83690764fe3782fd5ee377`
- Command: `./gradlew :app:assembleDebug :app:lintDebug --console=plain`
- Result: `BUILD SUCCESSFUL in 3m 54s`
- APK: `Spark.apk`, 18,304,132 bytes.
- SHA-256: `5f0023e522bb546638d7062faa7888b844cd1e6a6ef3d795533828abed1d30f5`
- Downloaded artifact digest matched GitHub's SHA-256. Archive CRC checks passed; AndroidManifest.xml/classes.dex and an APK v2 signing block were present. No independent cryptographic verification or actual phone installation is claimed.
- Packaged source code hashes match the successfully built commit; documentation was updated afterwards.
