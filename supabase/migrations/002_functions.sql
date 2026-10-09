-- =============================================================================
-- OccasioNet — Migration 002: Server Functions & Atomic Payment/Listing RPCs
-- Security-hardened SECURITY DEFINER functions with explicit search_path
-- =============================================================================

-- 1. دالة التحقق الموثوقة من صلاحية المشرف (تتحقق من الدور ومن عدم حظر الحساب)
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid UUID;
    v_is_admin BOOLEAN := FALSE;
BEGIN
    v_uid := auth.uid();
    IF v_uid IS NULL THEN
        RETURN FALSE;
    END IF;

    SELECT EXISTS (
        SELECT 1
        FROM public.user_roles ur
        JOIN public.profiles p ON p.id = ur.user_id
        WHERE ur.user_id = v_uid
          AND ur.role = 'admin'
          AND p.is_banned = FALSE
    ) INTO v_is_admin;

    RETURN COALESCE(v_is_admin, FALSE);
END;
$$;

REVOKE ALL ON FUNCTION public.is_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_admin() TO authenticated, anon;

-- 2. Trigger لإنشاء ملف شخصي ودور مستخدم عادي تلقائياً عند التسجيل في Supabase Auth
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    INSERT INTO public.profiles (id, email, full_name, phone, email_confirmed)
    VALUES (
        NEW.id,
        COALESCE(NEW.email, ''),
        COALESCE(NEW.raw_user_meta_data->>'full_name', ''),
        COALESCE(NEW.raw_user_meta_data->>'phone', ''),
        NEW.email_confirmed_at IS NOT NULL
    )
    ON CONFLICT (id) DO UPDATE
    SET email = EXCLUDED.email,
        email_confirmed = (NEW.email_confirmed_at IS NOT NULL),
        updated_at = NOW();

    INSERT INTO public.user_roles (user_id, role)
    VALUES (NEW.id, 'user')
    ON CONFLICT (user_id) DO NOTHING;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT OR UPDATE OF email_confirmed_at ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- 3. دالة إنشاء طلب دفع جديد (تفرض السعر الخادمي الرسمي من app_settings)
CREATE OR REPLACE FUNCTION public.submit_payment_request(
    p_payment_method TEXT,
    p_receipt_storage_path TEXT,
    p_receipt_mime_type TEXT,
    p_receipt_size_bytes BIGINT,
    p_transaction_reference TEXT DEFAULT '',
    p_user_note TEXT DEFAULT ''
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid UUID;
    v_is_banned BOOLEAN;
    v_fee_dzd BIGINT;
    v_request_id UUID;
BEGIN
    v_uid := auth.uid();
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: يجب تسجيل الدخول لإرسال طلب دفع.';
    END IF;

    SELECT is_banned INTO v_is_banned
    FROM public.profiles
    WHERE id = v_uid;

    IF v_is_banned IS TRUE THEN
        RAISE EXCEPTION 'ACCOUNT_BANNED: تم إيقاف هذا الحساب من قبل الإدارة.';
    END IF;

    IF p_payment_method NOT IN ('ccp', 'baridimob') THEN
        RAISE EXCEPTION 'INVALID_METHOD: وسيلة الدفع غير صالحة.';
    END IF;

    -- التحقق من أن مسار الإيصال يبدأ بمعرّف المستخدم نفسه لمنع الإشارة إلى إيصالات الغير
    IF position(v_uid::text || '/' in p_receipt_storage_path) <> 1 THEN
        RAISE EXCEPTION 'FORBIDDEN_PATH: مسار ملف الإيصال غير مصرح به لهذا المستخدم.';
    END IF;

    IF p_receipt_mime_type NOT IN ('image/jpeg', 'image/png', 'image/webp', 'application/pdf') THEN
        RAISE EXCEPTION 'INVALID_MIME: نوع ملف الإيصال غير مسموح به.';
    END IF;

    IF p_receipt_size_bytes <= 0 OR p_receipt_size_bytes > 5242880 THEN
        RAISE EXCEPTION 'INVALID_SIZE: حجم الملف يتجاوز الحد المسموح به (5 ميجابايت).';
    END IF;

    -- جلب السعر الخادمي الرسمي من إعدادات التطبيق (لا يعتمد على قيمة من العميل)
    SELECT listing_fee_dzd INTO v_fee_dzd
    FROM public.app_settings
    WHERE id = 1;

    v_fee_dzd := COALESCE(v_fee_dzd, 500);

    INSERT INTO public.payment_requests (
        user_id,
        amount_dzd,
        payment_method,
        receipt_storage_path,
        receipt_mime_type,
        receipt_size_bytes,
        transaction_reference,
        user_note,
        status
    ) VALUES (
        v_uid,
        v_fee_dzd,
        p_payment_method,
        p_receipt_storage_path,
        p_receipt_mime_type,
        p_receipt_size_bytes,
        trim(COALESCE(p_transaction_reference, '')),
        trim(COALESCE(p_user_note, '')),
        'pending'
    )
    RETURNING id INTO v_request_id;

    RETURN v_request_id;
END;
$$;

REVOKE ALL ON FUNCTION public.submit_payment_request(TEXT, TEXT, TEXT, BIGINT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.submit_payment_request(TEXT, TEXT, TEXT, BIGINT, TEXT, TEXT) TO authenticated;

-- 4. دالة مراجعة طلب الدفع من قبل المشرف (موافقة أو رفض مع تسجيل السبب والتدقيق)
CREATE OR REPLACE FUNCTION public.review_payment_request(
    p_request_id UUID,
    p_decision TEXT,
    p_rejection_reason TEXT DEFAULT NULL
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_admin_id UUID;
    v_req RECORD;
BEGIN
    v_admin_id := auth.uid();
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'FORBIDDEN_ADMIN_ONLY: هذه العملية مخصصة للمشرفين المصرح لهم فقط.';
    END IF;

    IF p_decision NOT IN ('approved', 'rejected') THEN
        RAISE EXCEPTION 'INVALID_DECISION: القرار يجب أن يكون approved أو rejected.';
    END IF;

    SELECT * INTO v_req
    FROM public.payment_requests
    WHERE id = p_request_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'NOT_FOUND: طلب الدفع غير موجود.';
    END IF;

    IF v_req.user_id = v_admin_id THEN
        RAISE EXCEPTION 'SELF_APPROVAL_FORBIDDEN: لا يجوز للمستخدم أو المشرف الموافقة على طلب دفع يخصه شخصياً.';
    END IF;

    IF v_req.status <> 'pending' THEN
        RAISE EXCEPTION 'INVALID_STATE: لا يمكن مراجعة طلب دفع حالته الحالية هي %.', v_req.status;
    END IF;

    IF p_decision = 'rejected' AND (p_rejection_reason IS NULL OR char_length(trim(p_rejection_reason)) < 3) THEN
        RAISE EXCEPTION 'REASON_REQUIRED: يجب توضيح سبب رفض طلب الدفع.';
    END IF;

    UPDATE public.payment_requests
    SET status = p_decision,
        rejection_reason = CASE WHEN p_decision = 'rejected' THEN trim(p_rejection_reason) ELSE NULL END,
        reviewed_by = v_admin_id,
        reviewed_at = NOW(),
        updated_at = NOW()
    WHERE id = p_request_id;

    -- إرسال إشعار للمستخدم بنتيجة المراجعة
    INSERT INTO public.notifications (
        user_id,
        title_ar,
        body_ar,
        type,
        related_entity_id
    ) VALUES (
        v_req.user_id,
        CASE WHEN p_decision = 'approved' THEN 'تمت الموافقة على طلب الدفع' ELSE 'تم رفض طلب الدفع' END,
        CASE
            WHEN p_decision = 'approved' THEN 'تمت مراجعة إثبات الدفع والموافقة عليه. يمكنك الآن استخدامه لنشر إعلانك.'
            ELSE 'تم رفض طلب الدفع للسبب التالي: ' || trim(p_rejection_reason)
        END,
        CASE WHEN p_decision = 'approved' THEN 'payment_approved' ELSE 'payment_rejected' END,
        p_request_id
    );

    -- تسجيل العملية في سجل التدقيق
    INSERT INTO public.audit_logs (
        actor_id,
        action_type,
        target_table,
        target_id,
        metadata
    ) VALUES (
        v_admin_id,
        'REVIEW_PAYMENT_' || upper(p_decision),
        'payment_requests',
        p_request_id::text,
        jsonb_build_object(
            'user_id', v_req.user_id,
            'amount_dzd', v_req.amount_dzd,
            'decision', p_decision,
            'rejection_reason', p_rejection_reason
        )
    );

    RETURN TRUE;
END;
$$;

REVOKE ALL ON FUNCTION public.review_payment_request(UUID, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.review_payment_request(UUID, TEXT, TEXT) TO authenticated;

-- 5. الدالة الذرية المحمية لنشر الإعلان باستهلاك دفعة معتمدة واحدة فقط
CREATE OR REPLACE FUNCTION public.create_listing_with_paid_receipt(
    p_payment_request_id UUID,
    p_category_id INTEGER,
    p_wilaya_code INTEGER,
    p_commune_id INTEGER,
    p_title TEXT,
    p_description TEXT,
    p_price_dzd BIGINT,
    p_condition TEXT,
    p_contact_phone TEXT,
    p_image_urls JSONB DEFAULT '[]'::jsonb
)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_uid UUID;
    v_profile RECORD;
    v_payment RECORD;
    v_commune_wilaya INTEGER;
    v_listing_id UUID;
BEGIN
    v_uid := auth.uid();
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'UNAUTHORIZED: يجب تسجيل الدخول قبل نشر إعلان.';
    END IF;

    SELECT * INTO v_profile
    FROM public.profiles
    WHERE id = v_uid;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'PROFILE_MISSING: لم يتم العثور على الملف الشخصي للمستخدم.';
    END IF;

    IF v_profile.is_banned IS TRUE THEN
        RAISE EXCEPTION 'ACCOUNT_BANNED: حسابك موقوف ولا يمكنك نشر إعلانات.';
    END IF;

    -- التحقق من ارتباط البلدية بالولاية المختارة
    SELECT wilaya_code INTO v_commune_wilaya
    FROM public.communes
    WHERE id = p_commune_id;

    IF v_commune_wilaya IS NULL OR v_commune_wilaya <> p_wilaya_code THEN
        RAISE EXCEPTION 'INVALID_LOCATION: البلدية المختارة لا تتبع الولاية المحددة.';
    END IF;

    -- قفل صف الدفعة ذرياً (FOR UPDATE) لمنع الاستهلاك المزدوج عند الطلبات المتزامنة
    SELECT * INTO v_payment
    FROM public.payment_requests
    WHERE id = p_payment_request_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'PAYMENT_NOT_FOUND: طلب الدفع المحدد غير موجود.';
    END IF;

    IF v_payment.user_id <> v_uid THEN
        RAISE EXCEPTION 'PAYMENT_OWNERSHIP_ERROR: لا يمكنك استخدام طلب دفع يخص مستخدماً آخر.';
    END IF;

    IF v_payment.status = 'consumed' OR v_payment.consumed_listing_id IS NOT NULL THEN
        RAISE EXCEPTION 'PAYMENT_ALREADY_CONSUMED: تم استهلاك هذه الدفعة مسبقاً لنشر إعلان آخر.';
    END IF;

    IF v_payment.status <> 'approved' THEN
        RAISE EXCEPTION 'PAYMENT_NOT_APPROVED: لا يمكن نشر الإعلان لأن حالة الدفعة هي % وليست approved.', v_payment.status;
    END IF;

    -- إدراج الإعلان الجديد
    INSERT INTO public.listings (
        seller_id,
        payment_request_id,
        category_id,
        wilaya_code,
        commune_id,
        title,
        description,
        price_dzd,
        condition,
        status,
        contact_phone,
        image_urls
    ) VALUES (
        v_uid,
        p_payment_request_id,
        p_category_id,
        p_wilaya_code,
        p_commune_id,
        trim(p_title),
        trim(p_description),
        p_price_dzd,
        p_condition,
        'published',
        trim(p_contact_phone),
        COALESCE(p_image_urls, '[]'::jsonb)
    )
    RETURNING id INTO v_listing_id;

    -- استهلاك الدفعة ذرياً في نفس المعاملة
    UPDATE public.payment_requests
    SET status = 'consumed',
        consumed_listing_id = v_listing_id,
        consumed_at = NOW(),
        updated_at = NOW()
    WHERE id = p_payment_request_id;

    -- تسجيل العملية في سجل التدقيق
    INSERT INTO public.audit_logs (
        actor_id,
        action_type,
        target_table,
        target_id,
        metadata
    ) VALUES (
        v_uid,
        'CREATE_LISTING_WITH_RECEIPT',
        'listings',
        v_listing_id::text,
        jsonb_build_object(
            'payment_request_id', p_payment_request_id,
            'price_dzd', p_price_dzd,
            'wilaya_code', p_wilaya_code
        )
    );

    RETURN v_listing_id;
END;
$$;

REVOKE ALL ON FUNCTION public.create_listing_with_paid_receipt(UUID, INTEGER, INTEGER, INTEGER, TEXT, TEXT, BIGINT, TEXT, TEXT, JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.create_listing_with_paid_receipt(UUID, INTEGER, INTEGER, INTEGER, TEXT, TEXT, BIGINT, TEXT, TEXT, JSONB) TO authenticated;

-- 6. دوال إدارية محمية لإدارة المستخدمين والإعدادات والإعلانات مع تسجيل التدقيق
CREATE OR REPLACE FUNCTION public.admin_set_user_ban_status(
    p_target_user_id UUID,
    p_is_banned BOOLEAN,
    p_ban_reason TEXT DEFAULT NULL
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'FORBIDDEN_ADMIN_ONLY: صلاحيات المشرف مطلوبة.';
    END IF;

    IF p_target_user_id = auth.uid() THEN
        RAISE EXCEPTION 'CANNOT_BAN_SELF: لا يمكن للمشرف حظر حسابه الشخصي.';
    END IF;

    UPDATE public.profiles
    SET is_banned = p_is_banned,
        ban_reason = CASE WHEN p_is_banned THEN trim(COALESCE(p_ban_reason, 'مخالفة شروط الاستخدام')) ELSE NULL END,
        updated_at = NOW()
    WHERE id = p_target_user_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'NOT_FOUND: المستخدم المستهدف غير موجود.';
    END IF;

    INSERT INTO public.audit_logs (actor_id, action_type, target_table, target_id, metadata)
    VALUES (
        auth.uid(),
        CASE WHEN p_is_banned THEN 'BAN_USER' ELSE 'UNBAN_USER' END,
        'profiles',
        p_target_user_id::text,
        jsonb_build_object('is_banned', p_is_banned, 'ban_reason', p_ban_reason)
    );

    RETURN TRUE;
END;
$$;

REVOKE ALL ON FUNCTION public.admin_set_user_ban_status(UUID, BOOLEAN, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_set_user_ban_status(UUID, BOOLEAN, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.admin_update_app_settings(
    p_listing_fee_dzd BIGINT,
    p_ccp_instructions_ar TEXT,
    p_baridimob_instructions_ar TEXT,
    p_payment_notice_ar TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'FORBIDDEN_ADMIN_ONLY: صلاحيات المشرف مطلوبة.';
    END IF;

    IF p_listing_fee_dzd < 0 THEN
        RAISE EXCEPTION 'INVALID_FEE: سعر الإعلان لا يمكن أن يكون سالباً.';
    END IF;

    UPDATE public.app_settings
    SET listing_fee_dzd = p_listing_fee_dzd,
        ccp_instructions_ar = trim(p_ccp_instructions_ar),
        baridimob_instructions_ar = trim(p_baridimob_instructions_ar),
        payment_notice_ar = trim(p_payment_notice_ar),
        updated_by = auth.uid(),
        updated_at = NOW()
    WHERE id = 1;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'NOT_FOUND: إعدادات التطبيق غير مهيأة.';
    END IF;

    INSERT INTO public.audit_logs (actor_id, action_type, target_table, target_id, metadata)
    VALUES (
        auth.uid(),
        'UPDATE_APP_SETTINGS',
        'app_settings',
        '1',
        jsonb_build_object('listing_fee_dzd', p_listing_fee_dzd)
    );

    RETURN TRUE;
END;
$$;

REVOKE ALL ON FUNCTION public.admin_update_app_settings(BIGINT, TEXT, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_update_app_settings(BIGINT, TEXT, TEXT, TEXT) TO authenticated;

CREATE OR REPLACE FUNCTION public.admin_moderate_listing(
    p_listing_id UUID,
    p_new_status TEXT,
    p_reason TEXT DEFAULT ''
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'FORBIDDEN_ADMIN_ONLY: صلاحيات المشرف مطلوبة.';
    END IF;

    IF p_new_status NOT IN ('published', 'hidden_by_admin', 'rejected') THEN
        RAISE EXCEPTION 'INVALID_STATUS: الحالة الإدارية غير صالحة.';
    END IF;

    UPDATE public.listings
    SET status = p_new_status,
        updated_at = NOW()
    WHERE id = p_listing_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'NOT_FOUND: الإعلان المستهدف غير موجود.';
    END IF;

    INSERT INTO public.audit_logs (actor_id, action_type, target_table, target_id, metadata)
    VALUES (
        auth.uid(),
        'MODERATE_LISTING_' || upper(p_new_status),
        'listings',
        p_listing_id::text,
        jsonb_build_object('new_status', p_new_status, 'reason', p_reason)
    );

    RETURN TRUE;
END;
$$;

REVOKE ALL ON FUNCTION public.admin_moderate_listing(UUID, TEXT, TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_moderate_listing(UUID, TEXT, TEXT) TO authenticated;
