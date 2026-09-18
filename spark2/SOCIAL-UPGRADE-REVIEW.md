# Spark 2 — 0.3.0 change review

Updated 18 September 2026. The user explicitly approved the named changes. Source was uploaded to `spark-2-preview`, the social migration was applied successfully, and deletion handler version 2 is ACTIVE. Android build verification is tracked in `VERIFICATION.md`.

## Requested behavior implemented in local source

- Facebook-inspired layout with Spark branding, top navigation, feed cards and profile controls.
- Friend requests, recipient acceptance, cancellation/unfriend; followers and following remain separate.
- Photo posts, short MP4 Reels, likes, comments, bookmarks and sharing posts inside friend conversations.
- Friend-only private text, image and video messages with history pagination.
- Foreground audio/video calls using WebRTC and Supabase signaling.
- Owner-only visitor dashboard, visible visits for 30 days, Ghost mode enabled by default and visitor notifications/activity opt-out.

## Approved external changes

1. Upload the new source and tests to the **public** repository `emonahmedea127-rgb/Spark`, branch `spark-2-preview`, under `spark2/`. Public code can be read by anyone. The default branch `main` is not part of this change. The existing workflow will run tests, Android compilation and lint and create a debug APK artifact. No private credentials are included.
2. Apply `supabase/social-upgrade.sql` only to **spark 2**, project `twywavuyghftkzsflfrf`. It creates six tables (`friendships`, `user_preferences`, `profile_visits`, `conversations`, `messages`, `call_sessions`), two private media buckets (`spark-videos`, `spark-chat`), supporting indexes, RLS policies, invoker views and tightly scoped helper functions. It alters posts to support videos and replaces selected follow/post visibility policies so accepted friends can access private posts. Existing base tables and user data are retained. Old project `daqsuqluwhzhymglceua` is outside this change.
3. Deploy the updated `supabase/functions/delete-account/index.ts` to the same project. The existing authenticated, current-password-confirmed deletion flow gains cleanup for the two new media buckets. It continues using server-only service-role access; account/media deletion is irreversible when a user confirms it. Deployment itself does not delete accounts.

An earlier automatic approval review rejected these operations. After the user explicitly approved this exact scope, they succeeded through the original GitHub and Supabase tools. A later verification query was interrupted by a temporary usage limit; it succeeded when work resumed. No review was bypassed.

## Evidence and limitations

- 75 local database/security/handler tests pass; test definitions are in `tests/`.
- See `VERIFICATION.md` for the current Android build and APK status. The previously built 0.2.0 APK does not contain these social features.
- The live backend has the 0.3.0 schema and deletion handler v2. Twelve social authorization checks passed in a rolled-back transaction. All 14 tables have RLS, all 4 views are security invoker, and all 4 media buckets are private.
- SMTP remains unconfigured. Real signup, password recovery and two-user device acceptance are outstanding.
- Calls are foreground-only, with no TURN relay, background ringing or push notifications. Some networks will not connect. Calls have not been tested on phones.
- Profile activity appears while the app is open. Ghost visitors are never listed; enabling Ghost mode does not delete a previously visible visit.
- Reels/chat video accepts MP4 up to 20 MiB, with a client-side duration cap of 60 seconds. Server-side transcoding, duration enforcement and video metadata stripping are not implemented.
- No paid resources have been requested. Free service quotas are limited and cannot support unlimited growth at zero cost.

The deployment is complete. Complete two-device acceptance where device/account access is available. Build and database checks do not prove production readiness.
