# Spark 1.4 verification

Changes:
- Tap the reaction count or emoji summary to open a paged people list, filter by reaction, and open a person's profile. Tap the thumb to like; hold the thumb to select a reaction. The count and thumb have separate touch areas.
- Chat consumes Scaffold system insets and applies IME padding, keeping the composer above the software keyboard. Text and cursor colors are explicit.
- Profile/cover photos have a crop preview with drag, pinch and slider zoom, Reset, cancel, upload progress and save error feedback. Avatar export is 1024 square with a circle preview. Cover export is 1600 by 900 and uses a matching 16:9 profile header.
- Updating a photo also creates a public image post. Both operations happen in one database transaction through a security-invoker trigger. Unchanged photo paths do not create duplicate announcements.

Database verification: 43 rollback-only checks passed on the connected project. This includes the existing permissions suite and new avatar/cover announcements, same-path idempotency, rejection of another user's media and preservation of the previous photo after a failed update. Synthetic data is rolled back. No new database security advisory was reported; the existing leaked-password-protection setting warning remains: https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection

Four call lifecycle JavaScript tests passed. Android build and lint passed on the initial implementation. New keyboard visibility, reaction-list filtering/profile navigation and pixel-level crop tests passed in the first emulator run; that run caught an existing reaction-picker gesture regression, which was corrected by separating touch areas. The final run passed all 17 Android tests, including the corrected reaction picker.

Physical-phone validation of the entire authenticated photo upload flow and this device's specific keyboard remains a practical follow-up. These automated tests do not prove identical rendering on every Android phone. Crop zoom uses the normalized image; it does not retain an uncropped original in a separate photo album.

Build commit: `b8e1142c7d6b263b2703e687dfcfe91f36ff1ef2`. Workflow: https://github.com/emonahmedea127-rgb/Spark/actions/runs/35921029028

APK SHA256: `56ad35cfd727824ae0e087a7fa2fa8815a6fc3d68cde0ca33045a02c36dba06e`; 21,757,783 bytes. APK signing certificate matches the delivered 1.3 build. Android build and lint passed.

Final workflow conclusion: success. All 17 Android tests passed with zero failures and zero skips, including real software keyboard visibility with Bengali input, reaction filtering/profile navigation, crop output/pan bounds, image cache and media playback.
