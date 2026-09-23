# Device acceptance tests

Status: APK build and lint PASSED. Real-device checks below are NOT RUN and require two Android devices/accounts.

1. Build with JDK 17 / SDK 35; resolve all compilation and blocking lint failures.
2. Install on Android 8+ and Android 15+, including a small-screen phone. Verify keyboard, scrolling, rotation, Back and permission dialogs.
3. Register Alice and Bob with real accessible emails. Confirm email if enabled; sign in, close/reopen, then verify session refresh after expiry. Test invalid credentials and offline startup.
4. Alice: upload avatar/cover, edit name and bio; Bob: find Alice and send a friend request. Alice accepts. Verify cancel, decline, follow and unfriend separately.
5. Alice: create public, friends-only and only-me posts with image/video. Check visibility as Bob and a third account; edit/delete, react, comment and save.
6. Upload supported media near 25 MB and reject larger/unsupported files. Interrupt upload; ensure no false success or deletion of media belonging to a committed record.
7. Create a story and reel. Check story expiry in database and playback on actual devices. Test signed URL renewal and unsupported device codecs.
8. Chat Alice ↔ Bob: send text/photo/video; load earlier messages, background/resume and reconnect after network loss. Verify a third user cannot read messages or media.
9. Keep both apps foreground: test incoming audio/video call, decline, mute, camera toggle, hangup, permission denial, rotation and remote disconnect.
10. Test calls on Wi-Fi/Wi-Fi, Wi-Fi/mobile and mobile/mobile. Add a properly authenticated TURN credential endpoint before claiming reliable connectivity across networks. Never bundle permanent TURN credentials in the client.
11. Block Bob as Alice. Verify profile, posts, chat and new media access disappear in both directions. Previously issued media URLs may remain valid up to 60 seconds. Unblock and verify.
12. Create/join a public group, publish a post, leave and verify nonmember posting is denied. Create a page; only the owner should be able to post as its feed owner.
13. Create a marketplace listing with photo; contact seller, mark sold. Confirm there is no in-app payment claim.
14. Generate reactions/messages/requests and verify notifications; mark read. Submit a report and verify it is visible only to the reporter and privileged moderation access.
15. Log out and sign in as a different user. Verify no previous user's messages, session or private media remain visible.

Full Facebook parity, background notifications/calls, Google/phone login, moderation tooling and production load testing remain separate implementation work.
