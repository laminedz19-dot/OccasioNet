# دليل مشروع OccasioNet

منصة OccasioNet لبيع وشراء الأغراض المستعملة في الجزائر، مبنية باستخدام **Kotlin** و**Jetpack Compose**، وتتكون من تطبيق للمستخدم وآخر للإدارة.

## البنية

- `userApp/`: تطبيق المستخدم والبائع والمشتري (`dz.ocasionet.user`).
- `adminApp/`: تطبيق الإدارة (`dz.ocasionet.admin`).
- `core/`: النماذج وعميل Supabase المشترك والبيانات المرجعية.
- `supabase/migrations/`: ترحيلات قاعدة البيانات المرتبة زمنيًا، و`supabase/config.toml` إعداد CLI.
- `supabase/functions/verify-admin-action/`: وظيفة Edge اختيارية؛ راجع أمانها وإعداد أسرارها قبل نشرها.
- `.github/workflows/android-build.yml`: اختبارات وبناء APK للتطبيقين.

## مشروع Supabase الحالي

يوجد مشروع Supabase باسم **OccasioNet** ومعرّف `oxdsyhvsntmvzeouqvrr` في `eu-west-3`. كان المشروع نشطًا لكنه بلا جداول في `public`، ولا ترحيلات مسجلة أو Storage buckets عند آخر فحص. لم تُنفّذ ترحيلات على قاعدة الإنتاج في هذا العمل. **لا تنشئ مشروعًا جديدًا** لهذا المستودع دون سبب موثق.

ابدأ بدليل [SETUP_SUPABASE_AR.md](./SETUP_SUPABASE_AR.md) لمراجعة الترحيلات وتشغيل Supabase CLI، وراجع [MY_ACTION_CHECKLIST_AR.md](./MY_ACTION_CHECKLIST_AR.md) للإجراءات التي تتطلب دخول حسابك. مفتاح العميل العام يوضع محليًا في `.env` المنسوخ من `.env.example` فقط، ولا يُطلب منك إرساله.

## التطوير والبناء

المتطلبات: JDK 17 وAndroid SDK 36 وGradle متوافق مع إعدادات المشروع.

```bash
gradle :core:testDebugUnitTest :app:testDebugUnitTest
gradle :userApp:assembleDebug :adminApp:assembleDebug
```

تنتج ملفات Debug في `userApp/build/outputs/apk/` و`adminApp/build/outputs/apk/`. لا تستخدم ملفات Debug كتوزيعة إنتاجية؛ إعداد توقيع Release يحتاج إلى Keystore محفوظ خارج Git.

## الاختبارات والأمان

اقرأ [TEST_PLAN_AR.md](./TEST_PLAN_AR.md) لخطة التحقق، و[SECURITY_REVIEW_AR.md](./SECURITY_REVIEW_AR.md) لحالة الربط والثغرات المتبقية. الاختبارات الوحدوية الحالية لمحرك سياسات Kotlin لا تغني عن اختبار RLS فعلي بحسابات مختلفة على Supabase. لا تضع `service_role` أو `sb_secret_` في تطبيق Android أو GitHub أو ملفات عامة.
