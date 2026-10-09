-- =============================================================================
-- OccasioNet — Migration 003: Row Level Security (RLS) & Storage Policies
-- Enforces strict Zero-Trust access control for Visitors, Users, and Admins
-- =============================================================================

-- تفعيل RLS على جميع الجداول الـ 12
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.wilayas ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.communes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payment_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.listings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.favorites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- 1. سياسات profiles
DROP POLICY IF EXISTS "profiles_select_own_or_admin" ON public.profiles;
CREATE POLICY "profiles_select_own_or_admin"
    ON public.profiles FOR SELECT
    USING (auth.uid() = id OR public.is_admin());

DROP POLICY IF EXISTS "profiles_update_own_safe" ON public.profiles;
CREATE POLICY "profiles_update_own_safe"
    ON public.profiles FOR UPDATE
    TO authenticated
    USING (auth.uid() = id AND is_banned = FALSE)
    WITH CHECK (auth.uid() = id AND is_banned = FALSE);

-- لا تسمح للعميل بتغيير أعمدة الهوية أو الحظر أو البريد المؤكد.
REVOKE UPDATE ON TABLE public.profiles FROM PUBLIC, anon, authenticated;
REVOKE UPDATE (id, email, full_name, phone, wilaya_code, commune_id, avatar_url, is_banned, ban_reason, email_confirmed, created_at, updated_at)
    ON TABLE public.profiles FROM PUBLIC, anon, authenticated;
GRANT UPDATE (full_name, phone, wilaya_code, commune_id, avatar_url)
    ON TABLE public.profiles TO authenticated;

-- 2. سياسات user_roles (ممنوع على أي مستخدم تعديل دوره من العميل)
DROP POLICY IF EXISTS "user_roles_select_own_or_admin" ON public.user_roles;
CREATE POLICY "user_roles_select_own_or_admin"
    ON public.user_roles FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id OR public.is_admin());

-- لا توجد سياسة INSERT/UPDATE/DELETE للعميل على user_roles لمنع تصعيد الصلاحيات.

-- 3. سياسات الجداول المرجعية العامة (categories, wilayas, communes, app_settings)
DROP POLICY IF EXISTS "categories_select_all" ON public.categories;
CREATE POLICY "categories_select_all"
    ON public.categories FOR SELECT
    USING (is_active = TRUE OR public.is_admin());

DROP POLICY IF EXISTS "categories_admin_manage" ON public.categories;
CREATE POLICY "categories_admin_manage"
    ON public.categories FOR ALL
    TO authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

DROP POLICY IF EXISTS "wilayas_select_all" ON public.wilayas;
CREATE POLICY "wilayas_select_all"
    ON public.wilayas FOR SELECT
    USING (TRUE);

DROP POLICY IF EXISTS "communes_select_all" ON public.communes;
CREATE POLICY "communes_select_all"
    ON public.communes FOR SELECT
    USING (TRUE);

DROP POLICY IF EXISTS "app_settings_select_all" ON public.app_settings;
CREATE POLICY "app_settings_select_all"
    ON public.app_settings FOR SELECT
    USING (TRUE);

-- 4. سياسات payment_requests (قراءة المالك أو المشرف فقط؛ التعديل والموافقة عبر الدوال الخادمية فقط)
DROP POLICY IF EXISTS "payment_requests_select_own_or_admin" ON public.payment_requests;
CREATE POLICY "payment_requests_select_own_or_admin"
    ON public.payment_requests FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id OR public.is_admin());

-- ملاحظة أمنية: لا يُسمح بـ UPDATE مباشر من العميل على payment_requests لمنع المستخدم من الموافقة على دفعته.

-- 5. سياسات listings
-- الزوار والمستخدمون يقرأون الإعلانات المنشورة فقط، والمالك يقرأ إعلاناته، والمشرف يقرأ الكل
DROP POLICY IF EXISTS "listings_select_published_or_owner_or_admin" ON public.listings;
CREATE POLICY "listings_select_published_or_owner_or_admin"
    ON public.listings FOR SELECT
    USING (
        status = 'published'
        OR auth.uid() = seller_id
        OR public.is_admin()
    );

-- لا توجد سياسة INSERT مباشرة على listings؛ الإدراج يتم حصراً عبر الدالة المحمية create_listing_with_paid_receipt
-- يُسمح للمالك بتحديث بيانات إعلانه أو حالته بين (published, sold, paused) فقط
DROP POLICY IF EXISTS "listings_update_owner" ON public.listings;
CREATE POLICY "listings_update_owner"
    ON public.listings FOR UPDATE
    TO authenticated
    USING (auth.uid() = seller_id AND status IN ('published', 'sold', 'paused'))
    WITH CHECK (auth.uid() = seller_id AND status IN ('published', 'sold', 'paused'));

-- امنع تغيير الملكية أو علاقة الدفع/الإعلان مباشرة؛ يسمح التطبيق فقط بتعديل المحتوى والحالة.
REVOKE UPDATE ON TABLE public.listings FROM PUBLIC, anon, authenticated;
REVOKE UPDATE (id, seller_id, payment_request_id, category_id, wilaya_code, commune_id, title, description, price_dzd, condition, status, contact_phone, image_urls, views_count, created_at, updated_at)
    ON TABLE public.listings FROM PUBLIC, anon, authenticated;
GRANT UPDATE (title, description, price_dzd, status)
    ON TABLE public.listings TO authenticated;

-- 6. سياسات favorites
DROP POLICY IF EXISTS "favorites_manage_own" ON public.favorites;
CREATE POLICY "favorites_manage_own"
    ON public.favorites FOR ALL
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- 7. سياسات reports
DROP POLICY IF EXISTS "reports_insert_authenticated" ON public.reports;
CREATE POLICY "reports_insert_authenticated"
    ON public.reports FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = reporter_id);

DROP POLICY IF EXISTS "reports_select_own_or_admin" ON public.reports;
CREATE POLICY "reports_select_own_or_admin"
    ON public.reports FOR SELECT
    TO authenticated
    USING (auth.uid() = reporter_id OR public.is_admin());

DROP POLICY IF EXISTS "reports_update_admin" ON public.reports;
CREATE POLICY "reports_update_admin"
    ON public.reports FOR UPDATE
    TO authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- 8. سياسات notifications
DROP POLICY IF EXISTS "notifications_select_own" ON public.notifications;
CREATE POLICY "notifications_select_own"
    ON public.notifications FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "notifications_update_read_own" ON public.notifications;
CREATE POLICY "notifications_update_read_own"
    ON public.notifications FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- 9. سياسات audit_logs (قراءة للمشرفين فقط)
DROP POLICY IF EXISTS "audit_logs_select_admin_only" ON public.audit_logs;
CREATE POLICY "audit_logs_select_admin_only"
    ON public.audit_logs FOR SELECT
    TO authenticated
    USING (public.is_admin());

-- =============================================================================
-- Supabase Storage Buckets & Policies
-- 1) payment-receipts (PRIVATE bucket, max 5MB, images/PDF only)
-- 2) listing-images (PUBLIC read bucket, owner write in own folder, max 5MB)
-- =============================================================================

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'payment-receipts',
    'payment-receipts',
    FALSE,
    5242880,
    ARRAY['image/jpeg', 'image/png', 'image/webp', 'application/pdf']
)
ON CONFLICT (id) DO UPDATE
SET public = FALSE,
    file_size_limit = 5242880,
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];

INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'listing-images',
    'listing-images',
    TRUE,
    5242880,
    ARRAY['image/jpeg', 'image/png', 'image/webp']
)
ON CONFLICT (id) DO UPDATE
SET public = TRUE,
    file_size_limit = 5242880,
    allowed_mime_types = ARRAY['image/jpeg', 'image/png', 'image/webp'];

-- سياسات Storage لـ payment-receipts (خاص)
DROP POLICY IF EXISTS "receipts_insert_own_folder" ON storage.objects;
CREATE POLICY "receipts_insert_own_folder"
    ON storage.objects FOR INSERT
    TO authenticated
    WITH CHECK (
        bucket_id = 'payment-receipts'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );

DROP POLICY IF EXISTS "receipts_select_own_or_admin" ON storage.objects;
CREATE POLICY "receipts_select_own_or_admin"
    ON storage.objects FOR SELECT
    TO authenticated
    USING (
        bucket_id = 'payment-receipts'
        AND (
            (storage.foldername(name))[1] = auth.uid()::text
            OR public.is_admin()
        )
    );

-- سياسات Storage لـ listing-images (عام للقراءة، مقيد بمجلد المستخدم للرفع)
DROP POLICY IF EXISTS "listing_images_select_public" ON storage.objects;
CREATE POLICY "listing_images_select_public"
    ON storage.objects FOR SELECT
    USING (bucket_id = 'listing-images');

DROP POLICY IF EXISTS "listing_images_insert_own_folder" ON storage.objects;
CREATE POLICY "listing_images_insert_own_folder"
    ON storage.objects FOR INSERT
    TO authenticated
    WITH CHECK (
        bucket_id = 'listing-images'
        AND (storage.foldername(name))[1] = auth.uid()::text
    );
