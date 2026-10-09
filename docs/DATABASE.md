# توثيق قاعدة البيانات والدوال الخادمية (docs/DATABASE.md)

## الجداول الـ 12 في قاعدة بيانات Supabase PostgreSQL

| اسم الجدول | الوظيفة | حالة RLS |
|---|---|---|
| `public.profiles` | بيانات المستخدمين المرتبطة بـ `auth.users` | مفعلة |
| `public.user_roles` | الأدوار الأمنية (`user` / `admin`) | مفعلة (قراءة فقط للعميل) |
| `public.categories` | فئات الإعلانات بالعربية والفرنسية | مفعلة |
| `public.wilayas` | الولايات الجزائرية الـ 58 الرسمية | مفعلة |
| `public.communes` | البلديات الجزائرية المرتبطة بالولايات | مفعلة |
| `public.app_settings` | السعر الرسمي للإعلان (500 دج) وتعليمات CCP و BaridiMob | مفعلة |
| `public.payment_requests` | طلبات الدفع (`pending`, `approved`, `rejected`, `consumed`) | مفعلة |
| `public.listings` | الإعلانات المنشورة والمرتبطة بدفعة معتمدة فريدة (`UNIQUE(payment_request_id)`) | مفعلة |
| `public.favorites` | قائمة الإعلانات المفضلة لكل مستخدم | مفعلة |
| `public.reports` | بلاغات المستخدمين عن الإعلانات المخالفة | مفعلة |
| `public.notifications` | إشعارات قبول/رفض الدفعات وتحديثات الحساب | مفعلة |
| `public.audit_logs` | سجل التدقيق الأمني لجميع قرارات المشرفين ونشر الإعلانات | مفعلة (للمشرفين فقط) |

## الدوال الخادمية (PostgreSQL RPCs)

1. `public.is_admin()`: تتحقق خادمياً من صلاحية المشرف.
2. `public.submit_payment_request(...)`: تنشئ طلب دفع بحالة `pending` بالسعر الخادمي المعتمد (500 دج).
3. `public.review_payment_request(...)`: للمشرفين فقط للموافقة أو الرفض مع إلزامية السبب عند الرفض وتسجيل التدقيق.
4. `public.create_listing_with_paid_receipt(...)`: معاملة ذرية تقفل صف الدفعة (`FOR UPDATE`)، تتحقق من الملكية والحالة `approved` وعدم الاستهلاك المسبق، ثم تنشئ الإعلان وتحول الدفعة إلى `consumed`.
5. `public.admin_set_user_ban_status(...)`: حظر أو تفعيل حساب مستخدم مع تسجيل التدقيق.
6. `public.admin_update_app_settings(...)`: تحديث سعر نشر الإعلان وتعليمات التحويل البريدي و BaridiMob.
7. `public.admin_moderate_listing(...)`: إخفاء أو رفض أو إعادة نشر إعلان بواسطة المشرف.
