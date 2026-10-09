-- =============================================================================
-- OccasioNet — Safe Migration & RLS Verification Script (Read-Only)
-- يُشغّل على بيئة Supabase محلية أو مشروع اختبار منفصل فقط (لا يُعدّل أي بيانات)
-- =============================================================================

-- 1. التحقق من وجود الجداول الـ 12 وتفعيل RLS عليها جميعاً
SELECT
    c.relname AS table_name,
    c.relrowsecurity AS rls_enabled,
    c.relforcerowsecurity AS rls_forced
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind = 'r'
  AND c.relname IN (
      'profiles', 'user_roles', 'categories', 'wilayas', 'communes',
      'app_settings', 'payment_requests', 'listings', 'favorites',
      'reports', 'notifications', 'audit_logs'
  )
ORDER BY c.relname;

-- 2. التحقق من الدوال الخادمية الـ 7 وضبط SECURITY DEFINER و search_path
SELECT
    p.proname AS function_name,
    p.prosecdef AS is_security_definer,
    p.proconfig AS function_config
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname = 'public'
  AND p.proname IN (
      'is_admin',
      'handle_new_user',
      'submit_payment_request',
      'review_payment_request',
      'create_listing_with_paid_receipt',
      'admin_set_user_ban_status',
      'admin_update_app_settings',
      'admin_moderate_listing'
  )
ORDER BY p.proname;

-- 3. التحقق من سياسات RLS الفعالة على الجداول العامة و storage.objects
SELECT
    schemaname,
    tablename,
    policyname,
    permissive,
    roles,
    cmd
FROM pg_policies
WHERE schemaname IN ('public', 'storage')
ORDER BY schemaname, tablename, policyname;

-- 4. التحقق من حاويات التخزين (payment-receipts خاصة، listing-images عامة للقراءة فقط)
SELECT
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types
FROM storage.buckets
WHERE id IN ('payment-receipts', 'listing-images');

-- 5. التحقق من البيانات المرجعية (58 ولاية جزائرية وإعدادات التطبيق)
SELECT
    (SELECT COUNT(*) FROM public.wilayas) AS wilayas_count,
    (SELECT COUNT(*) FROM public.communes) AS communes_count,
    (SELECT COUNT(*) FROM public.categories) AS categories_count,
    (SELECT listing_fee_dzd FROM public.app_settings WHERE id = 1) AS listing_fee_dzd;
