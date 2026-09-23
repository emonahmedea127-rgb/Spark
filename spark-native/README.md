# Spark Android

Emon Ahmed-এর জন্য নতুন native Android social app project। Kotlin + Jetpack Compose। Backend: Supabase Auth, PostgreSQL এবং private Storage।

**বর্তমান অবস্থা:** APK তৈরি হয়েছে। GitHub Actions-এ `assembleDebug` ও `lintDebug` সফল; ২৭টি database/RLS test এবং ৪টি call logic test পাস। APK ফোনে ইনস্টল করার debug build। বাস্তব ফোনে end-to-end পরীক্ষা এখনও হয়নি।

## Windows-এ চালানো

1. ZIP extract করে Android Studio-তে `spark-native` folder **Open** করো।
2. Android SDK Platform **35**, Build Tools **35.0.0** এবং JDK **17** install/select করো। Android Studio-র Gradle JDK setting-এ JDK 17 দাও।
3. Gradle sync শেষ হতে দাও। প্রথমবার internet লাগবে।
4. Android phone-এ Developer options → USB debugging চালু করে USB দিয়ে connect করো।
5. Android Studio-র Run button চাপো।
6. Spark খুলে **Create account** দিয়ে signup করো। Email confirmation চালু থাকলে email confirm করে login করো। Existing spark 2 Auth account দিয়েও login করা যাবে, প্রথমবার নতুন Spark profile তৈরি হবে।

Supabase URL ও publishable key ইতিমধ্যে যুক্ত আছে। service-role/secret key দেওয়ার প্রয়োজন নেই। `backend/schema.sql` বর্তমান connected project-এ আবার চালাবে না, এটি ইতিমধ্যে deploy করা হয়েছে।

## APK build

Windows PowerShell, project folder থেকে:

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug --console=plain
```

macOS/Linux:

```bash
chmod +x gradlew
./gradlew :app:assembleDebug :app:lintDebug --console=plain
```

Successful build-এর পরে APK: `app/build/outputs/apk/debug/app-debug.apk`। Debug APK নিজের ফোনে পরীক্ষার জন্য। Play Store-এর release upload-এর আগে signing ও device verification প্রয়োজন।

## GitHub দিয়ে build

repository root-এর `.github/workflows/spark-native.yml` যুক্ত আছে। ZIP-এর `.github` এবং `spark-native` folder repository root-এ রাখো। Actions → **Build Spark Android** → **Run workflow**। সফল হলে **Spark-debug-APK** artifact download করো। এই workflow সংযুক্ত `emonahmedea127-rgb/Spark` repository-র `codex/spark-native-apk` branch-এ চালানো হয়েছে। Build run: https://github.com/emonahmedea127-rgb/Spark/actions/runs/35792630432

## কোডে যুক্ত ফিচার

- Email/password signup/login, email confirmation flow, recovery-code password reset, encrypted session persistence ও token refresh।
- Home feed, photo/video posts, public/friends/only-me audience, editing/deletion, six reactions, comments, text sharing, saved posts।
- Profile name/bio/avatar/cover, people search, friend requests/accept/decline/cancel/unfriend, follow/unfollow।
- Stories with database-controlled 24-hour expiry; video Reels with playback।
- One-to-one chat, photo/video attachments, paged message history।
- In-app notifications, bidirectional blocking, reports।
- Public groups and pages with membership and posts।
- Marketplace listing/photo/price/location, seller chat, sold status।
- Foreground audio/video calling through bundled WebRTC UI and Supabase signaling।
- Dark mode toggle।

## সীমাবদ্ধতা, যেগুলো জানা জরুরি

- Android compilation ও lint সফল। বাস্তব ফোনে UI, Auth, upload ও কলের end-to-end পরীক্ষা বাকি।
- Call logic tests বাস্তব দুই ফোনের audio/video test নয়। বর্তমানে STUN আছে; restrictive mobile/Wi-Fi networks-এর জন্য TURN service configuration প্রয়োজন।
- Calls শুধু app সামনে খোলা থাকলে আসে; call screen background-এ গেলে call শেষ হয়। Background push/ringing এবং foreground call service নেই।
- Chat প্রতি ৪ সেকেন্ডে এবং incoming calls প্রতি ৭ সেকেন্ডে foreground polling করে। Feed/notifications refresh button দিয়ে update হয়। Supabase Realtime subscriptions এই version-এ নেই।
- Groups/pages public; private groups, moderator dashboard, live streaming, ads, payments, encrypted messenger এবং Facebook-এর সম্পূর্ণ feature set যুক্ত করা হয়নি। Google/phone OTP login-ও নেই।
- Password recovery কোড email template-এ থাকলে app-এ reset করা যায়। বর্তমান template/email delivery এখানে end-to-end যাচাই হয়নি।
- Upload limit 25 MB; JPG/PNG/WebP/MP4/WebM। Video transcoding, resumable upload ও automatic unused-file cleanup নেই। Deleted/expired content-এর media স্বয়ংক্রিয়ভাবে storage থেকে মুছে যায় না।
- Most discovery lists have a bounded first page. Feed ও chat-এর load-more আছে। Millions of users-এর load test করা হয়নি।
- Reports database-এ জমা হয়; moderator review Supabase dashboard থেকে করতে হবে।
- Existing project-এর Auth tenant shared, কিন্তু পুরোনো social tables পরিবর্তন করা হয়নি।

## গুরুত্বপূর্ণ ফাইল

- `app/src/main/java/com/spark/social/Backend.kt`: REST/Auth/Storage client, secure local session।
- `MainActivity.kt`: Compose screens and app state।
- `CallActivity.kt`, `app/src/main/assets/call.*`: permission-controlled WebRTC call UI।
- `backend/schema.sql`: already deployed isolated schema, grants, RLS, private bucket।
- `backend/verify.sql`: repeatable, rollback-only database tests।
- `docs/VERIFICATION.md`: যাচাইয়ের ফলাফল ও বাকি কাজ।
- `docs/DEVICE_TESTS.md`: দুই ফোন দিয়ে ব্যবহারিক পরীক্ষার তালিকা।

## Tests

```bash
node --test tests/call.test.cjs
```

`backend/verify.sql` project-এর privileged SQL Editor থেকে চালালে synthetic test data transaction শেষে rollback হবে। Production application code-এর ভেতর থেকে চালাবে না।
