# Spark 1.18

Monetization remains out of scope.

## Implemented and verified in this update
- Dashboard latest-content card opens complete insights, including reel watch metrics.
- Video thumbnails appear in content insights and pending-comment Community cards.
- Story reaction notifications open the story viewer; unavailable content gives a clear message.
- Reel reaction notifications open the relevant reel.
- Foreground chat typing indicator, scoped to chat participants, with seven-second expiry.
- Sending a message preserves text/attachments changed while the send is in flight.
- Twelve backend checks pass, including typing privacy, impersonation denial, delivery receipts and presence.

## Remaining work before production readiness
- Background push notifications and incoming calls require a configured push provider.
- Calls currently use STUN only. Reliable connectivity across restrictive networks needs authenticated TURN service configuration.
- Google and phone authentication are not implemented in this native version.
- Moderation administration and production-scale load testing remain unfinished.
- Reel ranking currently uses recency and engagement, not watch-retention signals.
- Two-account real-device tests (calls, audio routing, typing, delivery, uploads, navigation and small-screen UI) remain unrun.

Build/lint and emulator playback results are reported separately with the APK. Passing them does not establish full Facebook feature parity or production readiness.
