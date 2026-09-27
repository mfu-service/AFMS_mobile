# AFMS Android (Kotlin)

## تحميل على الهاتف / Download

Latest APK: **https://github.com/mfu-service/AFMS_mobile/releases/latest**

1. Open that page on the phone.
2. Download `AFMS-1.0.1.apk`.
3. Allow **Install unknown apps** for Chrome or Files if Android asks.
4. Open the APK and tap **Install**.

تطبيق أصلي للأستاذ والطالب، يُفتح في **Android Studio**. اللغة: **Kotlin** + **Jetpack Compose**.

يتصل بنفس خادم الويب على Docker عبر `/api/mobile/v1` — لا اتصال مباشر بـ MySQL.

## فتح المشروع

1. ثبّت [Android Studio](https://developer.android.com/studio) (Koala / Ladybug أو أحدث) و **Android SDK 35**.
2. **File → Open** واختر المجلد:

`E:\My_Projects\univ_system_manage\univ_manage_latest\afms-android`

3. اترك Gradle يعمل Sync. إذا طلب Wrapper اضغط **OK**.
4. شغّل خادم الويب:

```powershell
cd E:\My_Projects\univ_system_manage\univ_manage_latest\afms-web
docker compose up -d
```

5. عنوان الخادم في `app/build.gradle.kts`:

```kotlin
buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3000\"")
```

| الجهاز | العنوان |
|---|---|
| محاكي Android | `http://10.0.2.2:3000` (الافتراضي) |
| هاتف حقيقي على نفس Wi‑Fi | `http://IP-جهاز-الويندوز:3000` |

بعد تغيير العنوان: **Sync** ثم **Run**.

6. اختر محاكياً أو هاتفاً ثم **Run ▶**.

## تسجيل الدخول

نفس حساب الأستاذ أو الطالب في منصة الويب.

## ملاحظات

- التطبيق يستخدم HTTP محلياً (`usesCleartextTraffic`) للاتصال بـ Docker.
- في الإنتاج ضع `https://` لنفس `AUTH_URL`.
