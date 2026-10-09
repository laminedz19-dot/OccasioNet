-- =============================================================================
-- OccasioNet — Safe Test Admin Provisioning Procedure (TEST ENVIRONMENT ONLY)
-- تنبيه: لا تشغّل هذا السكربت على قاعدة بيانات الإنتاج دون مراجعة وموافقة صريحة.
--
-- ملاحظة تقنية هامة:
-- عند تسجيل أي مستخدم جديد في Supabase Auth، يقوم الـ Trigger (public.handle_new_user)
-- تلقائياً بإدراج صف في جدول public.user_roles بالقيمة: role = 'user'.
-- لذلك فإن استخدام:
--   INSERT INTO public.user_roles (user_id, role) VALUES (..., 'admin') ON CONFLICT DO NOTHING;
-- لن يرقّي المستخدم إلى مشرف لأن الصف موجود مسبقاً بدور 'user'!
-- بدلاً من ذلك، يجب التحقق من UUID وتأكيد البريد وعدم الحظر ثم استخدام DO UPDATE.
-- =============================================================================

DO $$
DECLARE
    -- استبدل هذا المعرّف بمعرّف UUID لحساب المشرف الاختباري في بيئة الاختبار فقط:
    v_target_admin_uuid UUID := '00000000-0000-0000-0000-000000000000';
    v_email TEXT;
    v_email_confirmed_at TIMESTAMPTZ;
    v_is_banned BOOLEAN;
BEGIN
    IF v_target_admin_uuid = '00000000-0000-0000-0000-000000000000'::uuid THEN
        RAISE NOTICE 'Dry-run guard: يرجى وضع UUID حساب المشرف الاختباري أولاً قبل التنفيذ.';
        RETURN;
    END IF;

    -- 1. التحقق من وجود المستخدم في auth.users وأن بريده الإلكتروني مؤكد
    SELECT email, email_confirmed_at
    INTO v_email, v_email_confirmed_at
    FROM auth.users
    WHERE id = v_target_admin_uuid;

    IF v_email IS NULL THEN
        RAISE EXCEPTION 'USER_NOT_FOUND: لا يوجد مستخدم بالمعرف % في auth.users.', v_target_admin_uuid;
    END IF;

    IF v_email_confirmed_at IS NULL THEN
        RAISE EXCEPTION 'EMAIL_NOT_CONFIRMED: البريد الإلكتروني (%) غير مؤكد بعد.', v_email;
    END IF;

    -- 2. التحقق من عدم حظر الحساب في public.profiles
    SELECT is_banned INTO v_is_banned
    FROM public.profiles
    WHERE id = v_target_admin_uuid;

    IF v_is_banned IS TRUE THEN
        RAISE EXCEPTION 'USER_BANNED: لا يمكن منح صلاحية المشرف لحساب محظور.';
    END IF;

    -- 3. ترقية الدور من 'user' إلى 'admin' باستخدام ON CONFLICT DO UPDATE
    INSERT INTO public.user_roles (user_id, role, granted_at)
    VALUES (v_target_admin_uuid, 'admin', NOW())
    ON CONFLICT (user_id) DO UPDATE
    SET role = 'admin',
        granted_at = NOW();

    -- 4. تسجيل الترقية في سجل التدقيق
    INSERT INTO public.audit_logs (actor_id, action_type, target_table, target_id, metadata)
    VALUES (
        v_target_admin_uuid,
        'SQL_PROVISION_TEST_ADMIN',
        'user_roles',
        v_target_admin_uuid::text,
        jsonb_build_object('email', v_email, 'role', 'admin')
    );

    RAISE NOTICE 'تمت ترقية حساب الاختبار (%) إلى دور admin بنجاح.', v_email;
END $$;
