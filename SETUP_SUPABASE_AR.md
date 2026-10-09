# دليل ربط OccasioNet بـ Supabase

## الحالة التي تم التحقق منها

- يوجد مشروع Supabase قائم باسم **OccasioNet**، ومعرّفه `oxdsyhvsntmvzeouqvrr`، والمنطقة `eu-west-3`، وحالته `ACTIVE_HEALTHY`.
- المشروع لا يحتوي حاليًا على جداول في مخطط `public`، ولا توجد ترحيلات مسجلة، ولا توجد Buckets في Storage.
- ملفات إعداد التطبيق تقبل `SUPABASE_URL` و`SUPABASE_ANON_KEY`. لم يتم الحصول على مفتاح النشر (publishable/anon)، ولم يوضع أي مفتاح حقيقي في المستودع.
- لم تُنفّذ ترحيلات على قاعدة الإنتاج خلال هذا العمل.

## 1. إعداد نسخة العمل

1. انسخ `.env.example` إلى `.env` في جذر المشروع.
2. احتفظ بعنوان المشروع الموجود في المثال، وتحقق منه في [لوحة مشروع OccasioNet](https://supabase.com/dashboard/project/oxdsyhvsntmvzeouqvrr).
3. من **Connect** أو **Project Settings → API Keys** انسخ **Publishable key**. إذا كان عميل Android الحالي لا يقبل المفتاح الجديد، استخدم مفتاح `anon` القديم مؤقتًا (لا تستخدم أبدًا `service_role` أو `sb_secret_`).
4. ضع المفتاح العام محليًا في `SUPABASE_ANON_KEY` داخل `.env` فقط. الملف مستثنى من Git. لا ترسله في المحادثة.
5. تحقق من أن الملف لا يحتوي على مفاتيح سرية، وأن قيمة المفتاح تبدأ بـ `sb_publishable_` أو أنها مفتاح anon القديم.

> المفتاح العام ليس سرًا، لكن سياسات RLS هي خط الدفاع الحقيقي. لا تضع `service_role` أو `sb_secret_` في Android أو ملفات التكوين العامة.

## 2. فحص مخطط قاعدة البيانات

تتضمن `supabase/migrations/` أربع ترحيلات مرتبة زمنيًا: المخطط والعلاقات، الدوال والمشغلات، RLS وStorage والصلاحيات، ثم البيانات المرجعية الجزائرية. راجعها قبل التنفيذ.

من جذر المستودع، بعد تثبيت Supabase CLI وتسجيل الدخول محليًا:

```bash
supabase login
supabase link --project-ref oxdsyhvsntmvzeouqvrr
supabase migration list
supabase db push --dry-run
```

افحص ناتج `--dry-run` وتأكد أنه يعرض الترحيلات الأربع بالترتيب المتوقع. عند الموافقة على تغييرات قاعدة البيانات:

```bash
supabase db push
supabase migration list
```

تحقق بعد التنفيذ من وجود الجداول الـ12، والدوال، وسياسات RLS، وBucketَي `payment-receipts` و`listing-images`. لا تستخدم `db reset` على المشروع السحابي. خذ نسخة احتياطية قبل أي تغيير على بيانات حقيقية. هذه الترحيلات لا تحذف الجداول، لكنها تضبط سياسات وخصائص Buckets التي تحمل الاسمين نفسيهما؛ لذلك راجعها إذا أُنشئت تلك الـBuckets يدويًا لاحقًا.

مرجع CLI الرسمي: [Supabase CLI](https://supabase.com/docs/guides/cli) و[Local development](https://supabase.com/docs/guides/cli/local-development).

## 3. ضبط المصادقة

في لوحة المشروع: **Authentication → URL Configuration**.

- اختبر التسجيل وتأكيد البريد أولًا مع إعداد تأكيد البريد الحالي.
- أضف مخطط Deep Link الذي سيعتمده التطبيق قبل تفعيل تدفق رابط الاستعادة. التطبيق الحالي لا يعرّف بعد مخطط Deep Link أو استعادة جلسة دائمة؛ لا تخترع redirect URL قبل إضافة ذلك إلى AndroidManifest واختباره على جهاز.
- راجع **Authentication → Providers → Email** وسياسة كلمات المرور وحدود إرسال البريد. لا تعطّل تأكيد البريد في الإنتاج لمجرد تجاوز حدود الاختبار.

الدليل الرسمي: [Supabase Auth](https://supabase.com/docs/guides/auth) و[Mobile deep linking](https://supabase.com/docs/guides/auth/native-mobile-deep-linking).

## 4. فحص Storage

بعد الترحيلات، تأكد من أن:

- `payment-receipts` خاص، ويقتصر الرفع والقراءة على مجلد صاحب الحساب، مع وصول الإدارة عبر آلية موثوقة.
- `listing-images` عام للقراءة فقط، والرفع يتطلب جلسة مصادقًا عليها ومجلدًا يطابق `auth.uid()`.
- لا توجد سياسة تسمح للجميع بالرفع أو التعديل أو الحذف.

مرجع رسمي: [Storage access control](https://supabase.com/docs/guides/storage/security/access-control).

## 5. البناء والتشغيل

```bash
gradle :core:testDebugUnitTest :app:testDebugUnitTest
gradle :userApp:assembleDebug
gradle :adminApp:assembleDebug
```

افتح `userApp` و`adminApp` في Android Studio عند الحاجة، وثبّت APK تجريبيًا على هاتف. اختبر التسجيل وتأكيد البريد وتسجيل الدخول والبيانات العامة ورفع إيصال تجريبي والمفضلة. لا تستخدم بيانات دفع أو معلومات شخصية حقيقية في الاختبار.

## 6. مفاتيح GitHub Actions

لا يحتاج Workflow البناء الحالي إلى الاتصال بقاعدة Supabase؛ لذلك لا تضف Supabase secrets إلى GitHub Actions. استخدم المفتاح العام محليًا فقط عند اختبار الاتصال الفعلي. لا تضف أي مفتاح إداري إلى CI أو APK.
