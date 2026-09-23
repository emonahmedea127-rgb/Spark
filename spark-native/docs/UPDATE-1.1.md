# Spark 1.1 — media and visual refresh

- Replaces VideoView with Media3 ExoPlayer, including buffering, playback controls, seek, decoder fallback, error and retry states.
- Private photos and videos use the current user Authorization header, refreshed before opening. Storage RLS remains enforced. No public bucket or service key is used.
- Videos no longer depend on 60-second signed URLs for later range requests. A failed authorization request refreshes access once; manual retries remain available.
- Tapping photos opens a fullscreen, fit-to-screen viewer with pinch and double-tap zoom.
- Stories open fullscreen with author, caption, progress, pause/resume and next/previous navigation. Images advance after eight visible seconds; videos advance on completion.
- Rounded feed cards, gradient story cards, video previews, redesigned composer, refined navigation and dark colors.

## Install
This is a development APK. The old 1.0 APK was signed using a disposable CI debug key, so Android may require uninstalling it before installing 1.1. Supabase account data remains on the server; sign in again. The workflow now caches its development signing key for subsequent builds. Production signing remains a separate release setup.

## Verification
The workflow compiles and lints the app, runs four call lifecycle checks, then tests MP4/H.264/AAC and WebM/VP8 rendering, seek and pause on an Android emulator. See VERIFICATION.md for actual run outcomes. Physical-device and real-account media checks remain separate.
