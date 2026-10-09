package dz.ocasionet.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OccasioNetMigrationContractTest {
    private fun migration(name: String): File = listOf(
        File("supabase/migrations/$name"),
        File("../supabase/migrations/$name")
    ).firstOrNull { it.isFile } ?: error("Migration file not found: $name")

    @Test
    fun `profile updates cannot change protected fields or recurse through RLS`() {
        val sql = migration("005_restrict_client_updates.sql").readText()

        assertTrue(sql.contains("GRANT UPDATE (full_name, phone, wilaya_code, commune_id, avatar_url)"))
        assertTrue(sql.contains("WITH CHECK (auth.uid() = id AND is_banned = FALSE)"))
        assertFalse(sql.contains("SELECT p.is_banned FROM public.profiles"))
        assertFalse(sql.contains("GRANT UPDATE (id, email"))
    }

    @Test
    fun `listing updates cannot change ownership or payment reference`() {
        val sql = migration("005_restrict_client_updates.sql").readText()

        assertTrue(sql.contains("GRANT UPDATE (title, description, price_dzd, status)"))
        assertTrue(sql.contains("auth.uid() = seller_id AND status IN ('published', 'sold', 'paused')"))
        assertFalse(sql.contains("WHERE l.id = id"))
        assertFalse(sql.contains("GRANT UPDATE (seller_id, payment_request_id"))
    }

    @Test
    fun `administrative RPCs reject missing target rows before audit logging`() {
        val sql = migration("002_functions.sql").readText()

        assertTrue(sql.contains("NOT_FOUND: المستخدم المستهدف غير موجود."))
        assertTrue(sql.contains("NOT_FOUND: إعدادات التطبيق غير مهيأة."))
        assertTrue(sql.contains("NOT_FOUND: الإعلان المستهدف غير موجود."))
    }
}
