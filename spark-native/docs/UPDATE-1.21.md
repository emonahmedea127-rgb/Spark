# Spark 1.21.0

- Story views are recorded only after the image loads or the video renders its first frame while the app is resumed.
- Hold a story to pause; release resumes without advancing. Short left/right taps still navigate.
- Sending a story reply no longer clears a newer draft; quick replies preserve typed text.
- Story viewers now show loading and an explicit retry instead of a misleading zero count.
- Messages: conversation-name search, unread filter, initial loading state and manual retry.
- Content insights and Community: loading, failure/retry and honest empty/unavailable states.

Validation: Android build, lint and existing media emulator checks are run by CI for this release. The new interactive flows still need a two-device acceptance check.

Remaining: provider-side FCM credentials/dispatch and TURN credentials are not active; live background notifications and relay calls are not verified. Monetization remains deferred. This release does not claim complete Facebook feature parity. Content analytics still uses the existing bounded reporting response, and inbox search filters the loaded conversations.
