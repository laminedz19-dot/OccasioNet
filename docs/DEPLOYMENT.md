# دليل النشر، التحقق من قاعدة البيانات، وتوقيع الإصدارات (docs/DEPLOYMENT.md)

## 1. تطبيق ملفات الهجرة والتحقق الآمن (Safe Migrations & Verification)
1. على بيئة Supabase محلية (`supabase start`) أو مشروع اختبار منفصل، نفّذ ملفات الهجرة بالترتيب:
   - `supabase/migrations/001_schema.sql`
   - `supabase/migrations/002_functions.sql`
   - `supabase/migrations/003_rls_and_storage.sql`
   - `supabase/migrations/004_seed_algeria_data.sql`
2. لا تستخدم أي أوامر مدمرة (`DROP TABLE`, `TRUNCATE`, `supabase db reset` على قاعدة بيانات الإنتاج).
3. للتحقق غير المدمّر (Read-Only Verification) من الجداول الـ 12، وتفعيل `relrowsecurity` (RLS)، والدوال الـ 7 (`SECURITY DEFINER`)، وحاويات التخزين (`payment-receipts` و `listing-images`)، شغّل السكربت:
   - `supabase/tests/01_verify_migrations_and_rls.sql`
4. انشر الدالة الخادمية الاختيارية `supabase/functions/verify-admin-action` عبر:
   ```bash
   supabase functions deploy verify-admin-action
   ```

## 2. إنشاء أول مشرف بأمان (Admin Bootstrap & Test Provisioning)
- لا يمكن إنشاء أو ترقية مشرف من داخل تطبيق Android (جدول `public.user_roles` مغلق أمام تعديلات العميل).
- عند إنشاء مستخدم جديد عبر Supabase Auth، يقوم التريغر `on_auth_user_created` (`public.handle_new_auth_user`) تلقائياً بإدراج صف في `public.profiles` وصف في `public.user_roles` بدور `'user'`.
- لترقية حساب موجود إلى `'admin'` دون تعارض مع التريغر، استخدم `ON CONFLICT (user_id) DO UPDATE`:
  ```sql
  INSERT INTO public.user_roles (user_id, role)
  SELECT id, 'admin'
  FROM auth.users
  WHERE email = 'admin@yourdomain.dz'
  ON CONFLICT (user_id) DO UPDATE SET role = 'admin';
  ```
- لبيئات الاختبار فقط، راجع السكربت المعلّم بالمعاملات في `supabase/tests/02_provision_test_admin.sql`.

## 3. إعداد الروابط العميقة في Supabase Auth (Deep Links)
أضف رابط العودة التالي في إعدادات **Supabase Dashboard -> Authentication -> URL Configuration -> Redirect URLs**:
- `occasionet://auth-callback`

## 4. بناء الحزم المستقلة وتوقيع الإصدارات (Release Signing)
- لبناء نسخ التطوير (Debug):
  ```bash
  gradle :userApp:assembleDebug :adminApp:assembleDebug
  ```
- لبناء نسخ الإنتاج الموقعة (Release) دون تخزين أي مفاتيح في المستودع، مرّر متغيرات البيئة التالية قبل البناء:
  - `KEYSTORE_PATH`: المسار المحلي لملف Keystore الخاص بالإنتاج خارج المستودع.
  - `STORE_PASSWORD`: كلمة مرور ملف Keystore.
  - `KEY_PASSWORD`: كلمة مرور المفتاح (`upload`).
- جميع ملفات `*.jks` و `*.keystore` و `keystore.properties` مستبعدة في `.gitignore` لمنع تسرب مفاتيح التوقيع إلى Git.
