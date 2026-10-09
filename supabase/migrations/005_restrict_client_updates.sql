-- =============================================================================
-- OccasioNet — Migration 005: Restrict direct client updates to safe columns
-- Additive policy/grant correction only; does not alter or delete application data.
-- =============================================================================
BEGIN;

-- Profile owners may update only their public profile fields, never role/ban/auth fields.
REVOKE UPDATE ON TABLE public.profiles FROM PUBLIC, anon, authenticated;
REVOKE UPDATE (id, email, full_name, phone, wilaya_code, commune_id, avatar_url, is_banned, ban_reason, email_confirmed, created_at, updated_at)
    ON TABLE public.profiles FROM PUBLIC, anon, authenticated;
GRANT UPDATE (full_name, phone, wilaya_code, commune_id, avatar_url)
    ON TABLE public.profiles TO authenticated;

DROP POLICY IF EXISTS "profiles_update_own_safe" ON public.profiles;
CREATE POLICY "profiles_update_own_safe"
    ON public.profiles FOR UPDATE
    TO authenticated
    USING (auth.uid() = id AND is_banned = FALSE)
    WITH CHECK (auth.uid() = id AND is_banned = FALSE);

-- Listing owners may edit content/status only; payment ownership and seller identity are immutable to clients.
REVOKE UPDATE ON TABLE public.listings FROM PUBLIC, anon, authenticated;
REVOKE UPDATE (id, seller_id, payment_request_id, category_id, wilaya_code, commune_id, title, description, price_dzd, condition, status, contact_phone, image_urls, views_count, created_at, updated_at)
    ON TABLE public.listings FROM PUBLIC, anon, authenticated;
GRANT UPDATE (title, description, price_dzd, status)
    ON TABLE public.listings TO authenticated;

DROP POLICY IF EXISTS "listings_update_owner" ON public.listings;
CREATE POLICY "listings_update_owner"
    ON public.listings FOR UPDATE
    TO authenticated
    USING (auth.uid() = seller_id AND status IN ('published', 'sold', 'paused'))
    WITH CHECK (auth.uid() = seller_id AND status IN ('published', 'sold', 'paused'));

COMMIT;
