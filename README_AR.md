# دليل مشروع OccasioNet الكامل (README_AR.md)

منصة **OccasioNet** لبيع وشراء الأغراض المستعملة في الجزائر (58 ولاية)، مبنية باستخدام **Kotlin** و **Jetpack Compose (Material 3)** و **Supabase (Auth + PostgreSQL + RLS + Storage + RPC)**، ومقسمة إلى تطبيقين مستقلين تماماً:

1. **OccasioNet User (`dz.ocasionet.user`)**: تطبيق المستخدمين والمشترين والبائعين (`OccasioNet-User-debug.apk`).
2. **OccasioNet Admin (`dz.ocasionet.admin`)**: تطبيق المشرفين والإدارة (`OccasioNet-Admin-debug.apk`).

---

## البنية المعمارية للمشروع

- `core/`: مكتبة مشتركة غير حساسة تضم نماذج البيانات، دليل الولايات الـ 58 والبلديات الجزائرية، طبقة الاتصال بـ Supabase، محرك فحص الصلاحيات، والثيم العربي (RTL).
- `userApp/`: وحدة تطبيق المستخدم المستقل (`dz.ocasionet.user`) بـ 29 شاشة كاملة.
- `adminApp/`: وحدة تطبيق الإدارة المستقل (`dz.ocasionet.admin`) بـ 18 وظيفة وشاشة إدارية محمية خادمياً.
- `app/`: مضيف المعاينة المباشرة في بيئة المحاكي السحابي.
- `supabase/migrations/`: ملفات ترحيل قاعدة البيانات (`001_schema.sql`, `002_functions.sql`, `003_rls_and_storage.sql`, `004_seed_reference_data.sql`).
- `supabase/functions/verify-admin-action/`: دالة خادمية (Edge Function) للتحقق الإداري وتوليد الروابط الموقعة قصيرة الصلاحية.
- `.github/workflows/android-build.yml`: بناء آلي للتطبيقين ورفع ملفي APK كـ Artifacts.

---

## الدليل العملي خطوة بخطوة (20 خطوة)

### 1. فتح المشروع في Android Studio
افتح مجلد المشروع الجذري في Android Studio (إصدار Ladybug أو أحدث) وانتظر انتهاء Gradle Sync.

### 2. تثبيت JDK و Android SDK المطلوبين
تأكد من تثبيت **JDK 17** و **Android SDK 36** من خلال `Settings > Languages & Frameworks > Android SDK`.

### 3. إنشاء مشروع Supabase جديد
ادخل إلى لوحة تحكم [Supabase](https://supabase.com/dashboard) وأنشئ مشروعاً جديداً خاصاً بـ OccasioNet، واحتفظ بكلمة مرور قاعدة البيانات في مكان آمن.

### 4. الحصول على URL والمفتاح العام الصحيحين
من داخل مشروعك في Supabase توجه إلى `Project Settings > API`:
- انسخ **Project URL** (`SUPABASE_URL`).
- انسخ المفتاح العام **anon / public key** (`SUPABASE_ANON_KEY`).
- **تحذير أمني صارم**: لا تنسخ مفتاح `service_role` ولا تضعه في أي ملف داخل Android.

### 5. إعداد `local.properties` أو `.env` محلياً
انسخ القيم إلى ملف `.env` (أو عبر لوحة **Secrets** في AI Studio):
```properties
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-public-anon-key
```

### 6. تنفيذ ملفات SQL بالترتيب
افتح **SQL Editor** في لوحة تحكم Supabase ونفّذ ملفات الترحيل بالترتيب التالي:
1. `supabase/migrations/001_schema.sql` (إنشاء الجداول الـ 12 والفهارس والقيود).
2. `supabase/migrations/002_functions.sql` (إنشاء الدوال الخادمية المحمية والـ Triggers).
3. `supabase/migrations/003_rls_and_storage.sql` (تفعيل RLS وإنشاء سياسات الجداول وحاويات Storage).
4. `supabase/migrations/004_seed_reference_data.sql` (إدراج الـ 58 ولاية جزائرية والبلديات والفئات والإعدادات الابتدائية).

### 7. إنشاء Storage Buckets وسياساتها
يقوم ملف `003_rls_and_storage.sql` بإنشاء الحاويتين تلقائياً:
- `payment-receipts`: حاوية خاصة (`public = false`) بحد أقصى 5 ميجابايت، تقبل `image/jpeg`, `image/png`, `image/webp`, `application/pdf`.
- `listing-images`: حاوية عامة للقراءة (`public = true`) ومقيدة بالمسار الشخصي للمستخدم عند الرفع.

### 8. إعداد Supabase Auth وتأكيد البريد الإلكتروني
من `Authentication > Providers > Email` فعّل خيار **Confirm email** لضمان تأكيد البريد الإلكتروني قبل السماح بنشر الإعلانات.

### 9. إعداد عناوين إعادة التوجيه (Redirect URLs)
من `Authentication > URL Configuration` أضف رابط إعادة التوجيه الخاص بتأكيد البريد واستعادة كلمة المرور.

### 10. إنشاء أول حساب مشرف ومنحه الدور بطريقة آمنة
1. أنشئ حساب المشرف من شاشة التسجيل أو من `Authentication > Users` في Supabase.
2. افتح **SQL Editor** في Supabase ونفّذ الاستعلام التالي لمنحه دور `admin` (لا يمكن منح هذا الدور من تطبيق الهاتف):
```sql
INSERT INTO public.user_roles (user_id, role)
SELECT id, 'admin'
FROM auth.users
WHERE email = 'admin@yourdomain.dz'
ON CONFLICT (user_id) DO UPDATE SET role = 'admin';
```
- لإلغاء صلاحية مشرف لاحقاً:
```sql
UPDATE public.user_roles SET role = 'user' WHERE user_id = 'UUID_OF_ADMIN';
```

### 11. ضبط السعر الافتراضي إلى 500 دج
السعر الابتدائي مضبوط تلقائياً على `500` دج في جدول `public.app_settings`، ويمكن للمشرف تعديله في أي وقت من شاشة الإعدادات في تطبيق **OccasioNet Admin**.

### 12. ضبط تعليمات التحويل البريدي (CCP) و BaridiMob
سجّل الدخول في تطبيق **OccasioNet Admin**، وانتقل إلى شاشة **إعدادات التطبيق وسعر النشر**، وأدخل رقم الحساب البريدي الجاري (CCP) ورقم الـ RIP الحقيقي لـ BaridiMob، ثم اضغط **حفظ**.

### 13. تشغيل التطبيقين محلياً
- لتشغيل تطبيق المستخدم: اختر Configuration `userApp` في Android Studio.
- لتشغيل تطبيق الإدارة: اختر Configuration `adminApp` في Android Studio.

### 14. تشغيل الاختبارات
لتشغيل حزمة الاختبارات الأمنية والوظيفية (15 سيناريو):
```bash
gradle :core:testDebugUnitTest :app:testDebugUnitTest
```

### 15. بناء APK للمستخدم و APK للإدارة
```bash
gradle :userApp:assembleDebug :adminApp:assembleDebug
```
ينتج عن ذلك ملفا APK مستقلان:
- `userApp/build/outputs/apk/debug/userApp-debug.apk` (`OccasioNet-User-debug.apk`)
- `adminApp/build/outputs/apk/debug/adminApp-debug.apk` (`OccasioNet-Admin-debug.apk`)

### 16. تنزيل ملفات البناء من GitHub Actions
عند كل `push`، يقوم Workflow `.github/workflows/android-build.yml` ببناء التطبيقين ورفع `OccasioNet-User-debug-apk` و `OccasioNet-Admin-debug-apk` في قسم **Artifacts**.

### 17. إعداد توقيع Release بصورة آمنة
استخدم متغيرات البيئة `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` في GitHub Secrets لتوقيع نسخ الإنتاج دون رفع ملف الـ Keystore إلى المستودع.

### 18. نشر التطبيقين بالطريقة المناسبة
انشر `OccasioNet User` (`dz.ocasionet.user`) للمستخدمين عبر متجر Google Play، ووزّع `OccasioNet Admin` (`dz.ocasionet.admin`) داخلياً للمشرفين المصرح لهم فقط.

### 19. مراقبة السجلات ومعالجة الأخطاء
راجع جدول `public.audit_logs` من تطبيق الإدارة أو من لوحة Supabase لمتابعة جميع عمليات الموافقة والرفض والحظر ونشر الإعلانات.

### 20. أخذ نسخ احتياطية للبيانات وإجراء تحديثات SQL بأمان
فعّل النسخ الاحتياطي اليومي في Supabase، واستخدم دائماً ملفات ترحيل تراكمية (`CREATE TABLE IF NOT EXISTS`, `ALTER TABLE`) دون استخدام `DROP TABLE`.
