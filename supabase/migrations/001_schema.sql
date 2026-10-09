-- =============================================================================
-- OccasioNet — Migration 001: Core Schema (12 Tables, Constraints & Indexes)
-- Safe idempotent migration (NO DROP TABLE or destructive operations)
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. profiles (مرتبط بـ auth.users)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    full_name TEXT NOT NULL DEFAULT '',
    phone TEXT NOT NULL DEFAULT '',
    wilaya_code INTEGER,
    commune_id INTEGER,
    avatar_url TEXT,
    is_banned BOOLEAN NOT NULL DEFAULT FALSE,
    ban_reason TEXT,
    email_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. user_roles (فصل الأدوار الإدارية عن ملف المستخدم لمنع تصعيد الصلاحيات)
CREATE TABLE IF NOT EXISTS public.user_roles (
    user_id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT NOT NULL CHECK (role IN ('user', 'admin')),
    granted_by UUID REFERENCES auth.users(id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. categories (فئات الإعلانات)
CREATE TABLE IF NOT EXISTS public.categories (
    id SERIAL PRIMARY KEY,
    slug TEXT NOT NULL UNIQUE,
    name_ar TEXT NOT NULL,
    name_fr TEXT NOT NULL DEFAULT '',
    icon_name TEXT NOT NULL DEFAULT 'category',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 4. wilayas (الولايات الجزائرية الـ 58)
CREATE TABLE IF NOT EXISTS public.wilayas (
    code INTEGER PRIMARY KEY CHECK (code BETWEEN 1 AND 58),
    name_ar TEXT NOT NULL UNIQUE,
    name_latin TEXT NOT NULL UNIQUE
);

-- 5. communes (البلديات الجزائرية المرتبطة بالولايات)
CREATE TABLE IF NOT EXISTS public.communes (
    id SERIAL PRIMARY KEY,
    wilaya_code INTEGER NOT NULL REFERENCES public.wilayas(code) ON DELETE RESTRICT,
    postal_code TEXT NOT NULL,
    name_ar TEXT NOT NULL,
    name_latin TEXT NOT NULL,
    UNIQUE (wilaya_code, name_ar)
);

-- 6. app_settings (إعدادات التطبيق وسعر نشر الإعلان وتعليمات الدفع)
CREATE TABLE IF NOT EXISTS public.app_settings (
    id INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    listing_fee_dzd BIGINT NOT NULL DEFAULT 500 CHECK (listing_fee_dzd >= 0),
    ccp_instructions_ar TEXT NOT NULL DEFAULT 'يرجى التواصل مع الإدارة أو انتظار ضبط بيانات الحساب البريدي الجاري (CCP) الرسمي من لوحة التحكم.',
    baridimob_instructions_ar TEXT NOT NULL DEFAULT 'يرجى التواصل مع الإدارة أو انتظار ضبط رقم RIP الخاص بـ BaridiMob من لوحة التحكم.',
    payment_notice_ar TEXT NOT NULL DEFAULT 'رسوم نشر الإعلان الواحد هي 500 دج. يتم تفعيل النشر بعد مراجعة المشرف لإثبات الدفع.',
    require_email_confirmation BOOLEAN NOT NULL DEFAULT TRUE,
    max_images_per_listing INTEGER NOT NULL DEFAULT 5 CHECK (max_images_per_listing BETWEEN 1 AND 10),
    updated_by UUID REFERENCES auth.users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 7. payment_requests (طلبات دفع رسوم نشر الإعلانات - 500 دج للإعلان)
CREATE TABLE IF NOT EXISTS public.payment_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE RESTRICT,
    amount_dzd BIGINT NOT NULL CHECK (amount_dzd > 0),
    payment_method TEXT NOT NULL CHECK (payment_method IN ('ccp', 'baridimob')),
    receipt_storage_path TEXT NOT NULL,
    receipt_mime_type TEXT NOT NULL CHECK (receipt_mime_type IN ('image/jpeg', 'image/png', 'image/webp', 'application/pdf')),
    receipt_size_bytes BIGINT NOT NULL CHECK (receipt_size_bytes > 0 AND receipt_size_bytes <= 5242880),
    transaction_reference TEXT NOT NULL DEFAULT '',
    user_note TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected', 'consumed')),
    rejection_reason TEXT,
    reviewed_by UUID REFERENCES public.profiles(id),
    reviewed_at TIMESTAMPTZ,
    consumed_listing_id UUID,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 8. listings (الإعلانات المنشورة والمرتبطة بدفعة معتمدة ومستهلكة واحدة فقط)
CREATE TABLE IF NOT EXISTS public.listings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    payment_request_id UUID NOT NULL UNIQUE REFERENCES public.payment_requests(id) ON DELETE RESTRICT,
    category_id INTEGER NOT NULL REFERENCES public.categories(id) ON DELETE RESTRICT,
    wilaya_code INTEGER NOT NULL REFERENCES public.wilayas(code) ON DELETE RESTRICT,
    commune_id INTEGER NOT NULL REFERENCES public.communes(id) ON DELETE RESTRICT,
    title TEXT NOT NULL CHECK (char_length(trim(title)) BETWEEN 5 AND 140),
    description TEXT NOT NULL CHECK (char_length(trim(description)) BETWEEN 10 AND 4000),
    price_dzd BIGINT NOT NULL CHECK (price_dzd >= 0),
    condition TEXT NOT NULL CHECK (condition IN ('new', 'like_new', 'good', 'fair')),
    status TEXT NOT NULL DEFAULT 'published' CHECK (status IN ('published', 'sold', 'paused', 'hidden_by_admin', 'rejected')),
    contact_phone TEXT NOT NULL DEFAULT '',
    image_urls JSONB NOT NULL DEFAULT '[]'::jsonb,
    views_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ربط consumed_listing_id في payment_requests بجدول listings
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_payment_requests_consumed_listing'
    ) THEN
        ALTER TABLE public.payment_requests
            ADD CONSTRAINT fk_payment_requests_consumed_listing
            FOREIGN KEY (consumed_listing_id) REFERENCES public.listings(id) ON DELETE SET NULL;
    END IF;
END $$;

-- 9. favorites (المفضلة)
CREATE TABLE IF NOT EXISTS public.favorites (
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    listing_id UUID NOT NULL REFERENCES public.listings(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, listing_id)
);

-- 10. reports (الإبلاغات عن الإعلانات)
CREATE TABLE IF NOT EXISTS public.reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL REFERENCES public.listings(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reason TEXT NOT NULL CHECK (char_length(trim(reason)) >= 5),
    details TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'resolved', 'dismissed')),
    reviewed_by UUID REFERENCES public.profiles(id),
    reviewed_at TIMESTAMPTZ,
    admin_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 11. notifications (إشعارات المستخدمين)
CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title_ar TEXT NOT NULL,
    body_ar TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'general' CHECK (type IN ('general', 'payment_approved', 'payment_rejected', 'listing_status', 'security')),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    related_entity_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 12. audit_logs (سجل التدقيق الإداري والأمني)
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE RESTRICT,
    action_type TEXT NOT NULL,
    target_table TEXT NOT NULL,
    target_id TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- الفهارس لتحسين أداء البحث والتصفية
CREATE INDEX IF NOT EXISTS idx_listings_status_created ON public.listings(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_listings_category ON public.listings(category_id);
CREATE INDEX IF NOT EXISTS idx_listings_wilaya_commune ON public.listings(wilaya_code, commune_id);
CREATE INDEX IF NOT EXISTS idx_listings_price ON public.listings(price_dzd);
CREATE INDEX IF NOT EXISTS idx_listings_seller ON public.listings(seller_id);
CREATE INDEX IF NOT EXISTS idx_payment_requests_user_status ON public.payment_requests(user_id, status);
CREATE INDEX IF NOT EXISTS idx_payment_requests_status_created ON public.payment_requests(status, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_communes_wilaya ON public.communes(wilaya_code);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON public.notifications(user_id, is_read, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_reports_status ON public.reports(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON public.audit_logs(created_at DESC);
