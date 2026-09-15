# Free Models Finder

Android app (Kotlin) بيوريكي المودلز المجانية دلوقتي على "OpenRouter" بس.

## OpenRouter
لفتة حقيقية ودينامكية من `GET /api/v1/models`: أي model `pricing.prompt == 0 && pricing.completion == 0`. مفيش hardcode لأسماء موديلات.

## الشات
دوس على أي موديل في الليستة يفتحلك شاشة شات بتستخدم الموديل ده بالظبط على OpenRouter (محتاج تحط مفتاح OpenRouter API من قايمة الـ ⋮ فوق).

## MCP (Composio)
من نفس قايمة الـ ⋮ في شاشة الشات، ضيف رابط MCP server بتاعك (زي رابط Composio) مع Authorization header لو محتاج — الشات هيقدر يستخدم أدوات (tools) السيرفر ده أثناء المحادثة تلقائيًا.

## البناء
GitHub Actions بيبني debug APK تلقائيًا على كل push لـ `main`، وبيرفعه كـ artifact في كل workflow run.

## تشغيل محلي
```
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```
