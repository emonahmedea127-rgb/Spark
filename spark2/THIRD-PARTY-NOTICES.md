# Third-party components

The checked-in Gradle wrapper scripts and wrapper JAR originate from Gradle 8.13 under the Apache License 2.0. Their original script license headers are preserved. The wrapper distribution is downloaded from Gradle's official service and verified against its published SHA-256 digest.

Gradle wrapper JAR SHA-256: `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.

Kotlin, AndroidX/Compose, kotlinx libraries, OkHttp, Coil and test dependencies are referenced by their pinned coordinates in Gradle. PGlite is referenced in `package.json`/`package-lock.json`. Their packages and applicable notices/licenses are supplied by their respective publishers when dependencies are resolved. They are not bundled as manually copied application source.

- Gradle: https://github.com/gradle/gradle/tree/v8.13.0
- Kotlin: https://github.com/JetBrains/kotlin
- AndroidX: https://android.googlesource.com/platform/frameworks/support/
- OkHttp: https://github.com/square/okhttp
- Coil: https://github.com/coil-kt/coil
- PGlite: https://github.com/electric-sql/pglite

Spark's UI and application/backend implementation in this project were newly created for this request. No Instagram source code, logo, screenshots or branded assets are included.
