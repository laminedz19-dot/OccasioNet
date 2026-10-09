# دليل النشر والإعداد (docs/DEPLOYMENT.md)

## 1. إعداد مشروع Supabase
1. نفّذ ملفات SQL الموجودة في `supabase/migrations/` بالترتيب من `001` إلى `004`.
2. تأكد من عدم استخدام أي أوامر مدمرة (`DROP TABLE`) عند تطبيق تحديثات مستقبلية.
3. انشر الدالة الخادمية الاختيارية `supabase/functions/verify-admin-action` عبر:
   ```bash
   supabase functions deploy verify-admin-action
   ```

## 2. إنشاء أول مشرف (Admin Bootstrap)
لا يمكن إنشاء مشرف من داخل تطبيق Android. لترقية أول حساب إلى مشرف، نفّذ في Supabase SQL Editor:
```sql
INSERT INTO public.user_roles (user_id, role)
SELECT id, 'admin'
FROM auth.users
WHERE email = 'admin@yourdomain.dz'
ON CONFLICT (user_id) DO UPDATE SET role = 'admin';
```

## 3. بناء الحزم المستقلة
- لبناء تطبيق المستخدم (`dz.ocasionet.user`):
  ```bash
  gradle :userApp:assembleDebug
  ```
- لبناء تطبيق الإدارة (`dz.ocasionet.admin`):
  ```bash
  gradle :adminApp:assembleDebug
  ```
