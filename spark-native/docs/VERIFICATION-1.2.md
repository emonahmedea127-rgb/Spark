# Spark 1.2 verification

## Delivered build
- Source commit: `9a2b9579f046737ef8dc16b0b92e7a87fe060418`.
- Workflow: https://github.com/emonahmedea127-rgb/Spark/actions/runs/35854097244
- Android compilation and lint: passed.
- Android 10 emulator: **7/7 tests passed** (5 image/cache tests, 2 video tests).
- JavaScript call lifecycle: **4/4 checks passed**.
- APK: `Spark-v1.2.apk`, 21,659,244 bytes.
- SHA-256: `23ca69272013c23e704eb3ba6e0f8c1415a068bcf2f8e3706fba37503942d18e`.
- Artifact SHA-256 matched GitHub; ZIP CRC checks passed. APK v2 signing block was present; no independent full cryptographic verification is claimed.

## Tests actually run
1. Eight simultaneous requests for one image result in one fetch; switching accounts does not reuse another account's bytes; cache clear forces another fetch.
2. An in-flight response arriving after logout/cache clear is rejected.
3. A 4096 x 3072 JPEG becomes 2048 x 1536 and uses fewer encoded bytes.
4. EXIF orientation 6 is applied correctly before metadata is removed.
5. ByteBuffer-backed images decode and the second identical request comes from Coil's bitmap memory cache.
6. MP4 video renders a frame, seeks, pauses/resumes, and completes without a playback error.
7. WebM video renders a frame, seeks, pauses/resumes, and completes without a playback error.

## Limits
- These are controlled emulator/fixture tests, not a measured speed benchmark on the user's phone or mobile connection.
- Previously uploaded originals are not rewritten. The first uncached fetch still depends on original size and network speed; repeats/fullscreen reuse encoded bytes for up to five minutes.
- Private media stays behind existing Supabase Storage policies. A read-only check confirmed the bucket is private and its limit is 25 MiB. No schema or user data changed.
- Cache is RAM-only, bounded and account-scoped. Already viewed content can remain cached for five minutes after a remote permission change; logout, account change and in-app blocking clear it.
- Layout follows the supplied references; no pixel-identical comparison or physical-device visual review is claimed.
- This is a development APK. Version 1.1 used a key that was not successfully cached; 1.2 needs uninstall/reinstall if Android rejects the update. Account data remains in Supabase. The new workflow explicitly creates and caches its debug key at `.signing/debug.keystore`; cache eviction can still require a new development key. Production release signing is separate.
