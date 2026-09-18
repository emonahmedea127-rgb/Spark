# Spark 2 চালু করার বাংলা গাইড

## নতুন social version 0.3.0-এর বর্তমান অবস্থা

Friend request, Reels, private chat, ছবি–ভিডিও পাঠানো, foreground audio/video call এবং Ghost mode-সহ visitor dashboard-এর code তৈরি ও GitHub-এ upload হয়েছে। **৭৫টি local test এবং ১২টি live social permission check পাস করেছে।** Android build-এর সর্বশেষ ফল `VERIFICATION.md`-তে আছে। নিচের পুরোনো 0.2.0 তথ্য ঐ version-এর পরীক্ষার বিবরণ।

তোমার স্পষ্ট অনুমতির পরে `spark-2-preview` branch-এ source upload, শুধু `twywavuyghftkzsflfrf` project-এ social migration এবং updated `delete-account` version 2 deploy হয়েছে। ১৪টি table-এর RLS, চারটি view-এর permission এবং চারটি private media bucket যাচাই হয়েছে। SQL script আবার চালাবে না।

Backend update না হলে social feature unavailable বার্তা দেখাবে। SMTP এখনও configure করা হয়নি। Call-এর সময় দুইজনের app খোলা থাকতে হবে; TURN relay না থাকায় কিছু network-এ call চলবে না। Background ringing বা push notification নেই। কোনো paid service চালু করা হয়নি; free quota সীমিত।

১৭ সেপ্টেম্বর ২০২৬-এ **AI Business Copilot** organization-এ **spark 2** তৈরি করা হয়েছে। Region: Singapore। Project ID: `twywavuyghftkzsflfrf`।

## যা সম্পন্ন হয়েছে

- আটটি database table ও access rules, দুটি private photo bucket, দুটি feed/comment view।
- Password যাচাই করে account ও ছবি মুছে ফেলার `delete-account` function deploy।
- App-এ নতুন project-এর URL ও public publishable key বসানো। প্রথমবার আলাদা করে connection দিতে হবে না।
- ৪৮টি local test, live database-এ ১২টি permission test এবং login ছাড়া data/deletion বন্ধ থাকার HTTP পরীক্ষা।
- Supabase Security Advisor-এ কোনো finding নেই।

**এই project-এ `supabase/setup.sql` আবার চালাবে না। এটি ইতিমধ্যে প্রয়োগ করা হয়েছে।**

## Auth সেটিং

Dashboard-এ sign-in করে নিচের সেটিংগুলো সংরক্ষণ ও যাচাই করা হয়েছে:

1. Authentication → URL Configuration-এ ঠিক `spark://auth/callback` redirect।
2. Site URL-ও `spark://auth/callback`, যাতে confirmation শেষে Android app খোলে। একই ফোনে email খুলে তারপর email/password দিয়ে login করবে।
3. Server-এর minimum password length 12, app-এর নিয়মের সঙ্গে মিলিয়ে।

**বাকি:** সাধারণ ব্যবহারকারীদের email confirmation ও reset email পাঠাতে নিজের SMTP provider যুক্ত করা। SMTP account বা credentials দেওয়া হয়নি।

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
