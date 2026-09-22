# Spark verification report

Date: 22 September 2026 (UTC)

## Delivered state

Native Kotlin/Compose Android source project, deployed Supabase backend and reproducible validation/build instructions. **No APK was produced and no Android compilation or device test passed in this environment.** This is not a verified production release or complete Facebook feature parity.

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

## Build result and hard blocker

`./gradlew :app:assembleDebug --console=plain` failed before project compilation while downloading Gradle 8.11.1:

```text
java.net.SocketException: Network is unreachable
```

This environment has no Android SDK or complete JDK compiler. No emulator or attached Android phone is available. `build-attempt.log` preserves the actual failure. The included GitHub Actions workflow has not been executed. Compilation, Android lint, installation and end-to-end UI/Auth/Storage/real-call tests remain unverified.

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
