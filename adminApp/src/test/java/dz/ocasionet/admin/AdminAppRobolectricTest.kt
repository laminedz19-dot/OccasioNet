package dz.ocasionet.admin

import dz.ocasionet.admin.data.AdminRepository
import dz.ocasionet.admin.ui.AdminScreenRoute
import dz.ocasionet.admin.ui.AdminViewModel
import dz.ocasionet.core.network.InMemoryEncryptedSessionStore
import dz.ocasionet.core.network.SupabaseClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdminAppRobolectricTest {

    @Before
    fun setUp() {
        SupabaseClient.setSessionStoreForTesting(InMemoryEncryptedSessionStore())
        SupabaseClient.clearSession()
    }

    @Test
    fun adminMainActivity_launchesSuccessfully() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }

    @Test
    fun adminRepository_signInAndAllModerationWorkflows_succeedCleanly() = runBlocking {
        val repo = AdminRepository()
        val loginRes = repo.signInAdmin("admin@occasionet.dz", "Admin@2026")
        assertTrue("يجب نجاح تسجيل دخول المشرف", loginRes.isSuccess)
        assertTrue(repo.isVerifiedAdmin.value)
        assertNotNull(repo.currentAdminProfile.value)

        // التأكد من توفر بيانات المراجعة وإمكانية اعتماد ورفض الدفعات
        val pendingBefore = repo.adminPendingPayments.value
        assertTrue(pendingBefore.isNotEmpty())
        val firstPaymentId = pendingBefore.first().id

        val approveRes = repo.adminReviewPayment(firstPaymentId, "approved")
        assertTrue(approveRes.isSuccess)
        assertFalse(repo.adminPendingPayments.value.any { it.id == firstPaymentId })

        // اختبار حظر وفك حظر مستخدم
        val targetUser = repo.adminUsers.value.first { it.id != repo.currentAdminProfile.value?.id }
        assertTrue(repo.adminSetUserBan(targetUser.id, true, "مخالفة تجريبية").isSuccess)
        assertTrue(repo.adminUsers.value.first { it.id == targetUser.id }.isBanned)

        // اختبار تحديث سعر النشر 500 دج وتعليمات الدفع
        assertTrue(
            repo.adminUpdateSettings(
                listingFeeDzd = 500L,
                ccpInstructionsAr = "CCP 0023456789",
                baridimobInstructionsAr = "RIP 00799999002345678912",
                paymentNoticeAr = "مراجعة يدوية خلال ساعات العمل"
            ).isSuccess
        )
        assertEquals(500L, repo.appSettings.value.listingFeeDzd)

        // اختبار إضافة فئة جديدة
        val catCountBefore = repo.categories.value.size
        assertTrue(repo.adminAddCategory("books-study", "كتب ودراسة", "Livres & Études").isSuccess)
        assertEquals(catCountBefore + 1, repo.categories.value.size)

        // اختبار حفظ إعدادات Supabase أثناء التشغيل
        val configRes = repo.updateSupabaseConfig(
            "https://oxdsyhvsntmvzeouqvrr.supabase.co",
            "sb_publishable_test_public_key_12345"
        )
        assertTrue(configRes.isSuccess)
    }

    @Test
    fun adminViewModel_navigatesToDashboardAfterLogin() = runBlocking {
        val vm = AdminViewModel(AdminRepository())
        assertEquals(AdminScreenRoute.ADMIN_LOGIN, vm.currentRoute.value)
        vm.repository.signInAdmin("admin@occasionet.dz", "Admin@2026")
        vm.navigateProtected(AdminScreenRoute.DASHBOARD_STATS)
        assertEquals(AdminScreenRoute.DASHBOARD_STATS, vm.currentRoute.value)
    }
}
