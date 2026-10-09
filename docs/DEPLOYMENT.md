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

لا تسمح للتطبيق بمنح دور مشرف. بعد إنشاء حساب إداري والتحقق من UUID الصحيح يدويًا، يمكن للمالك تنفيذ إدراج واحد في SQL Editor. راجع UUID قبل التشغيل، ولا تستخدم بريدًا عامًا أو عنوانًا افتراضيًا:

```sql
INSERT INTO public.user_roles (user_id, role)
VALUES ('<UUID_OF_VERIFIED_ADMIN>', 'admin')
ON CONFLICT (user_id) DO NOTHING;
```

هذا الاستعلام لا يغيّر دورًا قائمًا تلقائيًا. لا تمنح دورًا إداريًا قبل التحقق من صاحب الحساب.

## 4. الوظائف الخادمية

Edge Function `verify-admin-action` اختيارية، وليست لازمة لإعداد قاعدة البيانات الأساسية. لا تنشرها قبل مراجعة `verify-jwt` والتحقق من هوية المستدعي داخل الوظيفة، وإعداد الأسرار الخادمية المطلوبة بطريقة آمنة. لا تضع مفتاحًا سريًا في Android أو Git.

## 5. البناء

```bash
gradle :core:testDebugUnitTest :app:testDebugUnitTest
gradle :userApp:assembleDebug
gradle :adminApp:assembleDebug
```

يُبنى Debug في GitHub Actions دون الحاجة إلى مفاتيح Supabase. استخدم Keystore منفصلًا محفوظًا خارج المستودع لتوقيع Release، ولا تعتمد على APK التجريبي للنشر العام.
