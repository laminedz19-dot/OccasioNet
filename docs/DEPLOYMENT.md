# دليل النشر والإعداد

## 1. ربط Supabase

المشروع يستخدم مشروع OccasioNet الموجود بالمعرّف `oxdsyhvsntmvzeouqvrr` في المنطقة `eu-west-3`. لا تنشئ مشروعًا جديدًا. قاعدة المشروع كانت بلا جداول عامة أو ترحيلات مسجلة عند آخر فحص.

بعد مراجعة `supabase/migrations/`، سجّل الدخول واربط المشروع ثم راجع الخطة قبل التنفيذ:

```bash
supabase login
supabase link --project-ref oxdsyhvsntmvzeouqvrr
supabase migration list
supabase db push --dry-run
```

نفّذ `supabase db push` فقط بعد مراجعة الناتج والتأكد من المشروع المقصود. بعد ذلك تحقق من حالة الترحيلات والجداول وسياسات RLS وStorage. لا تستخدم `supabase db reset` على المشروع السحابي.

التفاصيل: [SETUP_SUPABASE_AR.md](../SETUP_SUPABASE_AR.md) و[قائمة الإجراءات اليدوية](../MY_ACTION_CHECKLIST_AR.md).

## 2. إعداد مفتاح Android

انسخ `.env.example` إلى `.env`، واحصل على المفتاح العام من **Connect** أو **Project Settings → API Keys** في لوحة Supabase. املأ `SUPABASE_ANON_KEY` محليًا فقط. لا تستخدم `service_role` أو `sb_secret_` ولا تحفظ المفاتيح السرية في Git.

## 3. منح أول حساب دور الإدارة

لا تسمح للتطبيق بمنح دور مشرف. ينشئ trigger دور `user` تلقائيًا لكل حساب جديد؛ لذلك لا تستخدم `INSERT ... ON CONFLICT DO NOTHING` لترقية حساب موجود، لأنه سيُبقي دوره `user`. على بيئة اختبار فقط، أنشئ حسابًا مخصصًا للمشرف وأكّد بريده، ثم تحقق يدويًا من UUID والدور والحظر:

```sql
SELECT u.id, u.email, u.email_confirmed_at, r.role, p.is_banned
FROM auth.users AS u
JOIN public.user_roles AS r ON r.user_id = u.id
JOIN public.profiles AS p ON p.id = u.id
WHERE u.id = '<UUID_OF_VERIFIED_ADMIN>'::uuid;
```

لا تتابع إلا إذا كان هذا هو الحساب المقصود، و`email_confirmed_at` غير فارغ و`is_banned = false`. ثم نفّذ التحديث المقصور على UUID الذي راجعته، وتأكد أن `RETURNING` أعاد صفًا واحدًا:

```sql
UPDATE public.user_roles AS r
SET role = 'admin', granted_at = NOW()
FROM auth.users AS u
JOIN public.profiles AS p ON p.id = u.id
WHERE r.user_id = u.id
  AND u.id = '<UUID_OF_VERIFIED_ADMIN>'::uuid
  AND u.email_confirmed_at IS NOT NULL
  AND p.is_banned = FALSE
  AND r.role = 'user'
RETURNING r.user_id, r.role;
```

نفّذ ذلك مرة واحدة فقط على حساب مخصص، من SQL Editor الموثوق؛ لا تستخدم بريدًا عامًا أو عنوانًا افتراضيًا. إذا لم يُعد صفًا، توقّف وافحص الحالة بدل توسيع شرط `WHERE`. SQL Editor يتجاوز RLS، فلا تستخدمه لإثبات أن سياسات RLS تعمل.

## 4. الوظائف الخادمية

Edge Function `verify-admin-action` اختيارية، وليست لازمة لإعداد قاعدة البيانات الأساسية. لا تنشرها قبل مراجعة `verify-jwt` والتحقق من هوية المستدعي داخل الوظيفة، وإعداد الأسرار الخادمية المطلوبة بطريقة آمنة. لا تضع مفتاحًا سريًا في Android أو Git.

## 5. البناء

```bash
gradle :core:testDebugUnitTest :app:testDebugUnitTest
gradle :userApp:assembleDebug
gradle :adminApp:assembleDebug
```

يُبنى Debug في GitHub Actions دون الحاجة إلى مفاتيح Supabase. استخدم Keystore منفصلًا محفوظًا خارج المستودع لتوقيع Release، ولا تعتمد على APK التجريبي للنشر العام.
