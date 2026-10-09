# توثيق الأمان وسياسات RLS وحماية الجلسات (docs/SECURITY.md)

## 1. المبادئ الأمنية الصارمة
1. **منع مفتاح `service_role` في Android**: يستخدم كلا التطبيقين (`userApp` و `adminApp`) المفتاح العام (`SUPABASE_ANON_KEY` / `ANON_KEY`) فقط، ويرفض `SupabaseClient` التشغيل إذا اكتشف مفتاح `service_role`.
2. **حفظ الجلسة المشفرة محلياً (`AndroidKeystoreSessionStore`)**:
   - تُشفّر رموز الجلسة (`access_token` و `refresh_token`) محلياً باستخدام مفتاح AES/GCM محفوظ داخل **Android Keystore** (`AES/GCM/NoPadding`).
   - لا يتم تسجيل أي رمز جلسة في `Logcat` تحت أي ظرف.
   - عند انتهاء صلاحية `access_token`، يقوم `SupabaseClient.refreshSessionIfNeeded()` بتجديده تلقائياً عبر نقطة النهاية `auth/v1/token?grant_type=refresh_token`، وعند تسجيل الخروج يتم مسح الجلسة من الذاكرة والتخزين المشفر فوراً.
3. **فصل الأدوار الإدارية**: يتم تخزين الأدوار في جدول مستقل `public.user_roles` لا يملك العميل عليه أي صلاحية `INSERT` أو `UPDATE` أو `DELETE`.
4. **التحقق الخادمي من المشرف**: تتحقق دالة `public.is_admin()` في PostgreSQL من أن `auth.uid()` مسجل بدور `'admin'` في `public.user_roles` وأن حسابه غير محظور (`is_banned = false`).
5. **حماية الدوال ذات الصلاحيات المرتفعة (`SECURITY DEFINER`)**: جميع الدوال الخادمية تضبط `SET search_path = public, pg_temp` وتلغي صلاحيات التنفيذ العامة `REVOKE ALL ... FROM PUBLIC`.

## 2. حماية نظام الدفع ونشر الإعلانات (500 دج)
- يتم جلب سعر نشر الإعلان خادمياً من `public.app_settings` داخل دالة `submit_payment_request` لمنع أي تلاعب بالسعر من جهة العميل.
- لا يملك العميل صلاحية `UPDATE` على جدول `payment_requests`، لذا لا يستطيع المستخدم الموافقة على دفعته.
- تمنع دالة `review_payment_request` المشرف أو المستخدم من الموافقة على طلب دفع يخصه (`SELF_APPROVAL_FORBIDDEN`).
- تتحقق دالة `create_listing_with_paid_receipt` خادمياً من:
  - عدم حظر الحساب (`ACCOUNT_BANNED`).
  - تأكيد البريد الإلكتروني إذا كان الخيار `require_email_confirmation` مفعلاً في `public.app_settings` (`EMAIL_NOT_CONFIRMED`).
  - وقوع جميع صور الإعلان المرفقة داخل مجلد المستخدم المالك `{user_id}/...` في حاوية `listing-images` وعدم تجاوز الحد الأقصى (`max_images_per_listing = 5`).
  - قفل صف الدفعة ذرياً عبر `SELECT ... FOR UPDATE` لمنع الاستهلاك المزدوج للدفعة نفسها حتى في حال إرسال طلبين متزامنين.

## 3. حماية حاويات التخزين (Supabase Storage) والتنظيف التعويضي
- **حاوية `payment-receipts` (خاصة `public = false`)**:
  - يُجبر المستخدم على الرفع داخل مجلده الخاص `{user_id}/...` بصيغ `JPG/PNG/WEBP/PDF` وبحجم أقصى `5MB`.
  - لا يمكن لأي مستخدم قراءة إيصالات مستخدم آخر، ويستعرض المشرف الإيصالات عبر روابط موقعة قصيرة الصلاحية (`120` ثانية) فقط.
  - **حذف الملفات اليتيمة (Orphan Receipt Cleanup)**: في حال نجح رفع ملف الإيصال إلى Storage ثم فشل استدعاء الدالة الخادمية `submit_payment_request`، يقوم العميل فوراً بحذف تعويضي للملف اليتيم بموجب السياسة الآمنة `receipts_delete_unlinked_own_folder` التي تسمح للمالك بحذف ملفه فقط إذا لم يكن مرتبطاً بأي صف في `public.payment_requests`.
- **حاوية `listing-images` (عامة للقراءة `public = true`)**:
  - متاحة للقراءة العامة لعرض صور الإعلانات، بينما يقتصر الرفع والتحديث والحذف على المستخدم المالك داخل مجلده `{user_id}/...` فقط بصيغ `JPG/PNG/WEBP` وبحجم أقصى `5MB`.
