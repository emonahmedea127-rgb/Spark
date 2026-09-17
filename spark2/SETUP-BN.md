# Spark 2 চালু করার বাংলা গাইড

১৭ সেপ্টেম্বর ২০২৬-এ **AI Business Copilot** organization-এ **spark 2** তৈরি করা হয়েছে। Region: Singapore। Project ID: `twywavuyghftkzsflfrf`।

## যা সম্পন্ন হয়েছে

- আটটি database table ও access rules, দুটি private photo bucket, দুটি feed/comment view।
- Password যাচাই করে account ও ছবি মুছে ফেলার `delete-account` function deploy।
- App-এ নতুন project-এর URL ও public publishable key বসানো। প্রথমবার আলাদা করে connection দিতে হবে না।
- ৪৮টি local test, live database-এ ১২টি permission test এবং login ছাড়া data/deletion বন্ধ থাকার HTTP পরীক্ষা।
- Supabase Security Advisor-এ কোনো finding নেই।

**এই project-এ `supabase/setup.sql` আবার চালাবে না। এটি ইতিমধ্যে প্রয়োগ করা হয়েছে।**

## যে Auth সেটিংগুলো বাকি

Dashboard-এ আলাদা sign-in প্রয়োজন বলে নিচের পরিবর্তনগুলো করা যায়নি:

1. Authentication → URL Configuration-এ ঠিক `spark://auth/callback` redirect যোগ করা।
2. Signup confirmation-এর জন্য নিজের নিয়ন্ত্রিত একটি বাস্তব Site URL দেওয়া।
3. Email/password-এর minimum password length 12 করা। App ইতিমধ্যে ১২ অক্ষর চায়, তবে server-এর সেটিংও প্রয়োজন।
4. সাধারণ ব্যবহারকারীদের email confirmation ও reset email পাঠাতে নিজের SMTP provider যুক্ত করা।

Email/password signup ও email confirmation বর্তমানে চালু আছে, যা API থেকে যাচাই করা হয়েছে। Default email service সব ঠিকানায় email পাঠায় না। কোনো test signup email পাঠানো হয়নি। Password recovery email একই ফোনে খুলতে হবে।

## Android app build

GitHub Actions-এ APK build সফল হয়েছে। ২৫টি Kotlin test, ৪৮টি Node test এবং Android lint পাস করেছে। [সফল build ও APK](https://github.com/emonahmedea127-rgb/Spark/actions/runs/35245217789)। এটি test করার debug APK; ফোনে পরীক্ষা এখনও বাকি।

1. GitHub-এর `spark-2-preview` branch download করে `spark2` folder Android Studio দিয়ে খোলো।
2. Gradle JDK 17, SDK Platform 36 ও Build-Tools 36.0.0 রাখো।
3. Terminal-এ চালাও:

```powershell
.\gradlew.bat :core:test :app:assembleDebug :app:lintDebug
```

সব সফল হলে `app/build/outputs/apk/debug/app-debug.apk` পাবে। Android Studio-এর Run দিয়েও test phone-এ চালাতে পারবে। Android 8 বা নতুন সংস্করণ প্রয়োজন। তোমার `Spark` repository-র `spark-2-preview` branch-এ নতুন source ও build workflow আছে। আগের `main` branch বদলানো হয়নি।

## App চালানো

Spark সরাসরি নতুন project-এর sign-in screen খুলবে। Signup করে email confirm করার পরে login করো। অন্য project ব্যবহার করলে **Change backend connection** আছে; **Use spark 2** দিয়ে এই project-এ ফিরতে পারবে। Database password বা server secret app-এ দেবে না।

## ফোনে বাকি পরীক্ষা

দুটি নিজের test account দিয়ে private photo, follow request, accept, like, comment, save, block, password reset এবং disposable account deletion পরীক্ষা করতে হবে। বাস্তব phone, signup email ও photo upload নিয়ে এই পরীক্ষা এখনো করা হয়নি।

Stories, Reels, chat, push notification ও Google login এই ভার্সনে নেই। পরীক্ষার পূর্ণ বিবরণ `VERIFICATION.md`-তে আছে।
