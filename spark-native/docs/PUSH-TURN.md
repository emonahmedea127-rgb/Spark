# Push and TURN setup — Spark 1.20

Integration code is present. Delivery and relay are **not active** without owner configuration. Existing foreground inbox polling and direct WebRTC remain available. Firebase client configuration now uses the owner's existing no-cost project `helloworld-d72dc`, Android registration `Spark Native` (`com.spark.social`). FCM HTTP v1 is enabled. Server credentials and dispatch activation are still pending.

## Android push

1. Completed: registered `com.spark.social` in the existing Spark Firebase project and added `app/google-services.json`. This client configuration is not a service-account private key.
2. Enable Firebase Cloud Messaging HTTP v1. Store a Firebase service account JSON in Supabase Edge Function secret `FIREBASE_SERVICE_ACCOUNT_JSON`. Never commit or send a service account private key in chat.
3. Generate a random secret of at least 32 characters. Store it as Edge secret `SPARK_PUSH_WEBHOOK_SECRET` and Supabase Vault secret `spark_push_webhook_secret` (same value).
4. Apply `backend/activate-push.sql` using a privileged database connection **after** configuration. It enables a queue insert hook and one-minute retry schedule. No schedule has been activated by this change. Disable with `select cron.unschedule('spark-push-retry');` and remove the Vault secret to disable immediate dispatch.
5. Open the app, sign in and allow Android notification permission. Registration retries on foreground resume and token rotation. Android's notification settings control background sound. App sounds control foreground effects.

FCM data messages contain only generic text and event IDs. They do not contain message bodies or profile pictures. The notification inbox still shows existing profile previews. Tapping a call notification checks signed-in recipient, call age and ringing status before opening the call. Background incoming calls currently use a notification, not a full-screen ringing foreground service. Force-stopped apps and phones without compatible Google Play services cannot be assumed to receive FCM.

Acceptance gate with two physical devices: foreground/background/locked message delivery; permission denial; logout/account switching; token refresh; duplicate notification delivery; call notification expired/already answered; both Wi-Fi and mobile data. These checks require actual Firebase configuration and have not been run.

## TURN

The deployed `spark-turn` function validates the current session and call access through RLS, accepts only recent ringing/accepted calls, enforces per-user/hour and project/month credential limits, then issues one-hour credentials. Provider API secrets remain on the server.

Set Supabase secrets `CLOUDFLARE_TURN_KEY_ID`, `CLOUDFLARE_TURN_API_TOKEN`, `TURN_MONTHLY_ISSUE_LIMIT` and finally `TURN_ENABLED=true` to opt in. It is disabled by default. The monthly credential limit is **not a bandwidth spending cap**. Do not activate billing or a paid provider plan without the owner's explicit budget approval. No paid service was activated here.

Cloudflare credentials expire after one hour; in-call renewal is not implemented. Long calls may disconnect at expiry. Direct STUN is used if the relay endpoint is unconfigured/unavailable. Cross-network call success is unverified until relay-only and real-device tests run with credentials.

## Verification

`node --test tests/call.test.cjs tests/edge.test.cjs` (Node 24) covers signaling, relay fallback, authentication, closed/inaccessible calls, allocation refusal and missing push configuration. `tests/push-turn.sql` is a transaction-only database check and rolls fixtures back. APK CI separately compiles, lints and runs existing device tests.

Server-only queue and allocation tables intentionally have RLS with no client policies. The pre-existing Supabase leaked-password-protection warning remains; see https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection .
