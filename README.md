# Free Models Finder

Android app (Kotlin) بيوريك الموديلز المجانية دلوقتي على "OpenRouter" بس.

## OpenRouter
فلترة حقيقية وديناميكية من `GET /api/v1/models`: أي model `pricing.prompt == 0 && pricing.completion == 0`. مفيش hardcode لأسماء موديلات.

## البناء
GitHub Actions بيبني debug APK تلقائيًا على كل push لـ `main`، وبيرفعه كـ artifact في الـ workflow run.

## تشغيل محلي
```
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```
