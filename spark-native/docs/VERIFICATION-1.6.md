# Spark 1.6.0 verification

- Package `com.spark.social`, version code 7.
- Build commit: `778e4d34854373847a3ff47b85e5a38e04c98b18`.
- Successful run: https://github.com/emonahmedea127-rgb/Spark/actions/runs/35956173647
- Build job: 107494860323. Assemble and lint passed.
- Android API 29 emulator: 24 tests, 0 failures, 0 skipped, including choice selection, audience preservation, map search/confirmation, dragging and coordinate projection. Existing keyboard, image, video, crop, reaction and discovery tests passed.
- JavaScript call lifecycle: 4 passed, 0 failed.
- Database: 75 rollback-only checks passed (43 core + 20 discovery + 12 structured metadata). No synthetic data retained.
- Category selection and new full-screen editor screenshots inspected; category typing/detail boxes are absent from the main editor.
- APK: 21,938,007 bytes.
- APK SHA-256: `4047dd1d65b3228b37e834ee03611ab7997c651b3f495610c2db25f70fea7e27`.
- APK archive artifact 10790960283, SHA-256 `383da2da75ab80e1901235125b7a33426031cc274125449b8e5cd1fb78d04c5c` verified.
- APK ZIP integrity passed; signer SHA-256 `e9fdc8807f4a53342b345600971dfc873907f420959ac3425fa5c30665fe35d8` matches delivered 1.5, supporting in-place update.
- Source files recovered from the exact build commit and verified against Git blob hashes before packaging.

Limits: physical handset testing is still needed. Map gesture/search UI tests use injected geocoder results and disable external tile downloads; live geocoder availability and map rendering on the user's handset have not been verified. Android Geocoder availability depends on the device. Map pin selection works independently of name lookup; tiles need internet. Curated option lists and public Spark entries are used, not Facebook's proprietary catalog. See UPDATE-1.6.md for provider usage details.

No new Supabase security advisor warnings. The existing [leaked-password protection warning](https://supabase.com/docs/guides/auth/password-security#password-strength-and-leaked-password-protection) remains.
