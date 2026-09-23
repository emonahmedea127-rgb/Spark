# Spark 1.2 — faster photos and reference UI

## Changes
- Shared account-scoped cache retains original encoded image bytes in RAM for five minutes, bounded to 32 MiB. Simultaneous requests for the same object are coalesced. A bounded decoded-bitmap cache speeds repeat rendering. Logout/account changes clear caches; late responses after logout are rejected. No private images are written to disk.
- Feed prefetches at most two upcoming images after scrolling settles. Fullscreen uses cached encoded bytes instead of downloading the original again. Decoding sizes differ for avatars, story previews, feed and fullscreen.
- Video cards no longer download a whole video to extract a scrolling preview. Video playback still uses authenticated Media3 requests.
- New image uploads are resized to at most 2048 pixels on their longest edge, EXIF orientation is applied, and metadata is removed. JPEG/WebP quality is 82. Previously uploaded originals are unchanged; their first download still depends on size/network.
- Reference-led white/blue feed, six-icon bottom navigation, tall photo story cards, large profile cover/avatar, profile tabs and settings, large friend-request portraits and aligned Confirm/Delete buttons.
- Fullscreen post composer with attachment preview and audience selection; photo viewer author/caption/actions; story reply field; vertically swipeable reels with a single active player.
- Spark branding is retained. No fake online/follower counts, verification badges or unimplemented paid-service buttons.

## Verification scope
The workflow compiles and lints the application and runs Android tests for cache reuse, concurrent downloads, account isolation/logout, image resizing, orientation, bitmap decoding, MP4 and WebM playback. Outcome is recorded after the actual CI run. Physical-phone/network speed and visual acceptance remain separate.

Privacy: already viewed images can remain available in the same signed-in session for up to five minutes after remote permissions change. Logging out, changing accounts or blocking someone from this app clears the cache.
