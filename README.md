# Free Models Finder

Android app (Kotlin) بيوريك الموديلز المجانية دلوقتي من مصدرين.

## OpenRouter
فلترة حقيقية وديناميكية على `GET /api/v1/models`: أي موديل `pricing.prompt == 0 && pricing.completion == 0`. مفيش hardcode لأسماء موديلات.

## Hugging Face
مفيهوش مفهوم "free models list" زي OpenRouter. كل حساب مجاني بياخد $0.10 credit شهري بيتصرف على أي موديل تستخدمه عبر الـ router. القايمة اللي في التطبيق دي لأشهر النماذج المتاحة على الـ Hub، مش ضمان تكلفة صفر — والتنبيه ده ظاهر جوه التطبيق نفسه.

## البناء
GitHub Actions بيبني debug APK تلقائيًا على كل push لـ `main`، وبيرفعه كـ artifact في الـ workflow run.

## تشغيل محلي
```
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```
