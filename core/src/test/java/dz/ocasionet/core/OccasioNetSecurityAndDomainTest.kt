package dz.ocasionet.core

import dz.ocasionet.core.data.AlgeriaGeographyCatalog
import dz.ocasionet.core.model.SupabaseConfigStatus
import dz.ocasionet.core.network.AuthCallbackResult
import dz.ocasionet.core.network.InMemoryEncryptedSessionStore
import dz.ocasionet.core.network.SupabaseClient
import dz.ocasionet.core.network.SupabaseClientProvider
import dz.ocasionet.core.security.RlsPolicyVerifier
import dz.ocasionet.core.security.SecurityViolationException
import dz.ocasionet.core.security.SupabaseSchemaContract
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * اختبارات شاملة للـ 21 متطلباً أمنياً ووظيفياً لمنصة OccasioNet:
 * (RLS + RPC + Storage + Concurrency + Session Persistence + Deep Links + Orphan Cleanup).
 */
class OccasioNetSecurityAndDomainTest {

    private lateinit var engine: RlsPolicyVerifier
    private val userA = "11111111-1111-1111-1111-111111111111"
    private val userB = "22222222-2222-2222-2222-222222222222"
    private val adminUser = "99999999-9999-9999-9999-999999999999"

    @Before
    fun setUp() {
        engine = RlsPolicyVerifier()
        engine.registerUser(userA, "buyer_seller_a@ocasionet.dz", "أحمد بن علي")
        engine.registerUser(userB, "buyer_seller_b@ocasionet.dz", "ياسين بوعلام")
        engine.registerUser(adminUser, "admin@ocasionet.dz", "مشرف النظام")
        engine.grantAdminRoleFromSqlEditorOnly(adminUser, sqlOperatorId = "postgres_superuser")
        SupabaseClient.setSessionStoreForTesting(InMemoryEncryptedSessionStore())
        SupabaseClient.clearSession()
    }

    @Test
    fun `1 - visitor can read published listings without authentication`() {
        val receiptPath = engine.uploadStorageFile(
            callerUserId = userA,
            bucket = "payment-receipts",
            objectPath = "$userA/ccp_receipt_1.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 120_000L
        )
        val payment = engine.submitPaymentRequestRpc(userA, "ccp", receiptPath, "image/jpeg", 120_000L)
        engine.reviewPaymentRequestRpc(adminUser, payment.id, "approved")
        val communeId = AlgeriaGeographyCatalog.communesForWilaya(16).first().id

        engine.createListingWithPaidReceiptRpc(
            callerUserId = userA,
            paymentRequestId = payment.id,
            categoryId = 1,
            wilayaCode = 16,
            communeId = communeId,
            title = "هاتف سامسونج بحالة ممتازة",
            description = "هاتف مستعمل نظيف جداً مع العلبة والشاحن الأصلي في الجزائر الوسطى.",
            priceDzd = 42000L,
            condition = "like_new",
            contactPhone = "0550112233"
        )

        val visitorVisible = engine.queryVisibleListings(callerUserId = null)
        assertEquals(1, visitorVisible.size)
        assertEquals("هاتف سامسونج بحالة ممتازة", visitorVisible.first().title)
    }

    @Test
    fun `2 - visitor cannot create a listing`() {
        val communeId = AlgeriaGeographyCatalog.communesForWilaya(16).first().id
        try {
            engine.createListingWithPaidReceiptRpc(
                callerUserId = null,
                paymentRequestId = "fake-payment-id",
                categoryId = 1,
                wilayaCode = 16,
                communeId = communeId,
                title = "إعلان زائر غير مسجل",
                description = "وصف إعلان تجريبي طويل بما يكفي.",
                priceDzd = 10000L,
                condition = "good",
                contactPhone = "0550000000"
            )
            fail("يجب رفض إنشاء إعلان من زائر غير مسجل")
        } catch (e: SecurityViolationException) {
            assertEquals("UNAUTHORIZED", e.code)
        }
    }

    @Test
    fun `3 - unauthenticated visitor cannot submit payment request`() {
        try {
            engine.submitPaymentRequestRpc(
                callerUserId = null,
                paymentMethod = "baridimob",
                receiptStoragePath = "$userA/receipt.png",
                receiptMimeType = "image/png",
                receiptSizeBytes = 50_000L
            )
            fail("يجب منع الزائر من إرسال طلب دفع")
        } catch (e: SecurityViolationException) {
            assertEquals("UNAUTHORIZED", e.code)
        }
    }

    @Test
    fun `4 - user cannot read another user's private payment receipts`() {
        val pathA = engine.uploadStorageFile(
            callerUserId = userA,
            bucket = "payment-receipts",
            objectPath = "$userA/private_ccp.pdf",
            mimeType = "application/pdf",
            sizeBytes = 200_000L
        )

        // المالك نفسه يستطيع القراءة
        assertNotNull(engine.readReceiptFromPrivateStorage(userA, pathA))
        // المشرف الموثق يستطيع القراءة للمراجعة
        assertNotNull(engine.readReceiptFromPrivateStorage(adminUser, pathA))

        // مستخدم آخر (userB) يُمنع تماماً
        try {
            engine.readReceiptFromPrivateStorage(userB, pathA)
            fail("يجب منع المستخدم B من قراءة إيصال المستخدم A")
        } catch (e: SecurityViolationException) {
            assertEquals("RLS_RECEIPT_READ_DENIED", e.code)
        }
    }

    @Test
    fun `5 - regular user cannot escalate role to admin`() {
        try {
            engine.attemptClientRoleChange(callerUserId = userA, targetUserId = userA, newRole = "admin")
            fail("يجب منع المستخدم من ترقية نفسه إلى مشرف")
        } catch (e: SecurityViolationException) {
            assertEquals("RLS_ROLE_ESCALATION_DENIED", e.code)
        }
        assertFalse(engine.isAdmin(userA))
    }

    @Test
    fun `6 - user or admin cannot approve their own payment request`() {
        val pathA = engine.uploadStorageFile(userA, "payment-receipts", "$userA/r.jpg", "image/jpeg", 90_000L)
        val reqA = engine.submitPaymentRequestRpc(userA, "ccp", pathA, "image/jpeg", 90_000L)

        // محاولة المستخدم الموافقة على طلبه
        try {
            engine.reviewPaymentRequestRpc(userA, reqA.id, "approved")
            fail("يجب منع المستخدم العادي من الموافقة على طلبه")
        } catch (e: SecurityViolationException) {
            assertEquals("FORBIDDEN_ADMIN_ONLY", e.code)
        }

        // حتى لو أرسل المشرف طلب دفع خاصاً به، لا يجوز له الموافقة على دفعته الشخصية
        val pathAdmin = engine.uploadStorageFile(adminUser, "payment-receipts", "$adminUser/r.jpg", "image/jpeg", 90_000L)
        val reqAdmin = engine.submitPaymentRequestRpc(adminUser, "ccp", pathAdmin, "image/jpeg", 90_000L)
        try {
            engine.reviewPaymentRequestRpc(adminUser, reqAdmin.id, "approved")
            fail("يجب منع الموافقة الذاتية على طلب الدفع")
        } catch (e: SecurityViolationException) {
            assertEquals("SELF_APPROVAL_FORBIDDEN", e.code)
        }
    }

    @Test
    fun `7 - cannot publish listing without an approved payment request`() {
        val pathA = engine.uploadStorageFile(userA, "payment-receipts", "$userA/pending.jpg", "image/jpeg", 80_000L)
        val pendingReq = engine.submitPaymentRequestRpc(userA, "baridimob", pathA, "image/jpeg", 80_000L)
        val communeId = AlgeriaGeographyCatalog.communesForWilaya(31).first().id

        try {
            engine.createListingWithPaidReceiptRpc(
                callerUserId = userA,
                paymentRequestId = pendingReq.id,
                categoryId = 2,
                wilayaCode = 31,
                communeId = communeId,
                title = "حاسوب محمول للبيع في وهران",
                description = "حاسوب محمول بمعالج قوي وذاكرة 16 جيجا بحالة جيدة.",
                priceDzd = 85000L,
                condition = "good",
                contactPhone = "0661223344"
            )
            fail("يجب رفض نشر إعلان بطلب دفع لا يزال pending")
        } catch (e: SecurityViolationException) {
            assertEquals("PAYMENT_NOT_APPROVED", e.code)
        }
    }

    @Test
    fun `8 - cannot consume the same approved payment request twice`() {
        val pathA = engine.uploadStorageFile(userA, "payment-receipts", "$userA/once.jpg", "image/jpeg", 80_000L)
        val req = engine.submitPaymentRequestRpc(userA, "ccp", pathA, "image/jpeg", 80_000L)
        engine.reviewPaymentRequestRpc(adminUser, req.id, "approved")
        val communeId = AlgeriaGeographyCatalog.communesForWilaya(25).first().id

        val firstListing = engine.createListingWithPaidReceiptRpc(
            callerUserId = userA,
            paymentRequestId = req.id,
            categoryId = 1,
            wilayaCode = 25,
            communeId = communeId,
            title = "الإعلان الأول المسموح به",
            description = "تم نشر هذا الإعلان باستخدام الدفعة المعتمدة بنجاح.",
            priceDzd = 15000L,
            condition = "good",
            contactPhone = "0770112233"
        )
        assertNotNull(firstListing.id)

        try {
            engine.createListingWithPaidReceiptRpc(
                callerUserId = userA,
                paymentRequestId = req.id,
                categoryId = 1,
                wilayaCode = 25,
                communeId = communeId,
                title = "الإعلان الثاني المكرر بنفس الوصل",
                description = "محاولة إعادة استخدام نفس الوصل لنشر إعلان ثانٍ.",
                priceDzd = 20000L,
                condition = "good",
                contactPhone = "0770112233"
            )
            fail("يجب منع استخدام نفس الدفعة المعتمدة لنشر إعلان ثانٍ")
        } catch (e: SecurityViolationException) {
            assertEquals("PAYMENT_ALREADY_CONSUMED", e.code)
        }
    }

    @Test
    fun `9 - regular user cannot access admin dashboard`() {
        try {
            engine.verifyAdminDashboardAccess(userA)
            fail("يجب منع المستخدم العادي من دخول لوحة الإدارة")
        } catch (e: SecurityViolationException) {
            assertEquals("ADMIN_ACCESS_DENIED", e.code)
        }
        assertTrue(engine.verifyAdminDashboardAccess(adminUser))
    }

    @Test
    fun `10 - client cannot tamper with listing fee to bypass server 500 DZD price`() {
        val pathA = engine.uploadStorageFile(userA, "payment-receipts", "$userA/fee.jpg", "image/jpeg", 60_000L)
        val req = engine.submitPaymentRequestRpc(
            callerUserId = userA,
            paymentMethod = "ccp",
            receiptStoragePath = pathA,
            receiptMimeType = "image/jpeg",
            receiptSizeBytes = 60_000L,
            clientAttemptedAmountDzd = 1L
        )
        assertEquals(500L, req.amountDzd)
    }

    @Test
    fun `11 - admin can approve and reject payment requests with audit logging`() {
        val path1 = engine.uploadStorageFile(userA, "payment-receipts", "$userA/p1.jpg", "image/jpeg", 70_000L)
        val req1 = engine.submitPaymentRequestRpc(userA, "ccp", path1, "image/jpeg", 70_000L)
        val approved = engine.reviewPaymentRequestRpc(adminUser, req1.id, "approved")
        assertEquals("approved", approved.status)

        val path2 = engine.uploadStorageFile(userB, "payment-receipts", "$userB/p2.jpg", "image/jpeg", 70_000L)
        val req2 = engine.submitPaymentRequestRpc(userB, "baridimob", path2, "image/jpeg", 70_000L)
        val rejected = engine.reviewPaymentRequestRpc(adminUser, req2.id, "rejected", "صورة الوصل غير واضحة")
        assertEquals("rejected", rejected.status)
        assertEquals("صورة الوصل غير واضحة", rejected.rejectionReason)

        val logs = engine.getAuditLogsForAdmin(adminUser)
        assertEquals(2, logs.size)
        assertTrue(logs.any { it.actionType == "REVIEW_PAYMENT_APPROVED" && it.targetId == req1.id })
        assertTrue(logs.any { it.actionType == "REVIEW_PAYMENT_REJECTED" && it.targetId == req2.id })
    }

    @Test
    fun `12 - concurrent attempts to consume the same approved payment succeed only once`() {
        val pathA = engine.uploadStorageFile(userA, "payment-receipts", "$userA/race.jpg", "image/jpeg", 95_000L)
        val req = engine.submitPaymentRequestRpc(userA, "ccp", pathA, "image/jpeg", 95_000L)
        engine.reviewPaymentRequestRpc(adminUser, req.id, "approved")
        val communeId = AlgeriaGeographyCatalog.communesForWilaya(16).first().id

        val pool = Executors.newFixedThreadPool(2)
        val task = Callable {
            runCatching {
                engine.createListingWithPaidReceiptRpc(
                    callerUserId = userA,
                    paymentRequestId = req.id,
                    categoryId = 1,
                    wilayaCode = 16,
                    communeId = communeId,
                    title = "إعلان متزامن لاختبار القفل الذري",
                    description = "اختبار منع استهلاك الدفعة مرتين عند إرسال طلبين في نفس اللحظة.",
                    priceDzd = 30000L,
                    condition = "good",
                    contactPhone = "0550998877"
                )
            }
        }

        val results = pool.invokeAll(listOf(task, task)).map { it.get() }
        pool.shutdown()

        val successes = results.count { it.isSuccess }
        val failures = results.count { it.isFailure }
        assertEquals("يجب أن ينجح طلب واحد فقط", 1, successes)
        assertEquals("يجب أن يفشل الطلب المتزامن الثاني", 1, failures)
    }

    @Test
    fun `13 - handles unconfigured Supabase and rejects service_role key in Android`() {
        val emptyStatus = SupabaseClientProvider.inspectConfig(rawUrl = "", rawAnonKey = "")
        assertTrue(emptyStatus is SupabaseConfigStatus.NotConfigured)

        val forbiddenStatus = SupabaseClientProvider.inspectConfig(
            rawUrl = "https://xyz.supabase.co",
            rawAnonKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.service_role_secret"
        )
        assertTrue(forbiddenStatus is SupabaseConfigStatus.ForbiddenServiceRoleKey)
    }

    @Test
    fun `14 - SQL tables and RPC names match Kotlin schema contract and 58 Algerian wilayas`() {
        assertEquals(58, AlgeriaGeographyCatalog.wilayas.size)
        assertEquals(12, SupabaseSchemaContract.REQUIRED_TABLES.size)
        assertEquals(7, SupabaseSchemaContract.REQUIRED_RPCS.size)

        val rootDir = File("..").takeIf { File("../supabase/migrations").exists() } ?: File(".")
        val schemaFile = File(rootDir, "supabase/migrations/001_schema.sql")
        val funcFile = File(rootDir, "supabase/migrations/002_functions.sql")
        if (schemaFile.exists() && funcFile.exists()) {
            val schemaSql = schemaFile.readText()
            val funcSql = funcFile.readText()
            SupabaseSchemaContract.REQUIRED_TABLES.forEach { table ->
                assertTrue("الجدول $table يجب أن يكون معرفاً في 001_schema.sql", schemaSql.contains("public.$table"))
            }
            SupabaseSchemaContract.REQUIRED_RPCS.forEach { rpc ->
                assertTrue("الدالة $rpc يجب أن تكون معرفة في 002_functions.sql", funcSql.contains("public.$rpc"))
            }
        }
    }

    @Test
    fun `15 - disallowed MIME types, oversized files, and foreign folder paths are rejected in Storage`() {
        try {
            engine.uploadStorageFile(
                callerUserId = userA,
                bucket = "payment-receipts",
                objectPath = "$userA/malware.exe",
                mimeType = "application/x-msdownload",
                sizeBytes = 10_000L
            )
            fail("يجب رفض الملفات غير المسموح بها")
        } catch (e: SecurityViolationException) {
            assertEquals("STORAGE_MIME_REJECTED", e.code)
        }

        try {
            engine.uploadStorageFile(
                callerUserId = userA,
                bucket = "payment-receipts",
                objectPath = "$userA/huge.pdf",
                mimeType = "application/pdf",
                sizeBytes = 9_000_000L
            )
            fail("يجب رفض الملفات التي تتجاوز 5 ميجابايت")
        } catch (e: SecurityViolationException) {
            assertEquals("STORAGE_SIZE_EXCEEDED", e.code)
        }

        try {
            engine.uploadStorageFile(
                callerUserId = userA,
                bucket = "payment-receipts",
                objectPath = "$userB/spoofed.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 50_000L
            )
            fail("يجب رفض الرفع في مجلد مستخدم آخر")
        } catch (e: SecurityViolationException) {
            assertEquals("STORAGE_FOLDER_VIOLATION", e.code)
        }
    }

    @Test
    fun `16 - encrypted session persistence, expiration detection, and sign-out cleanup work reliably`() {
        SupabaseClient.persistSession(
            accessToken = "jwt_access_token_123",
            refreshToken = "jwt_refresh_token_456",
            userId = userA,
            email = "buyer_seller_a@ocasionet.dz",
            expiresInSeconds = 3600L,
            nowEpochSeconds = 1_000_000L
        )

        val restored = SupabaseClient.restorePersistedSession()
        assertNotNull(restored)
        assertEquals("jwt_access_token_123", restored?.accessToken)
        assertEquals("jwt_refresh_token_456", restored?.refreshToken)
        assertEquals(userA, restored?.userId)

        // قبل انتهاء الصلاحية
        assertFalse(SupabaseClient.isAccessTokenExpired(nowEpochSeconds = 1_001_000L))
        // بعد انتهاء الصلاحية
        assertTrue(SupabaseClient.isAccessTokenExpired(nowEpochSeconds = 1_004_000L))

        // مسح الجلسة عند تسجيل الخروج
        SupabaseClient.clearSession()
        assertNull(SupabaseClient.restorePersistedSession())
        assertNull(SupabaseClient.currentAccessToken)
    }

    @Test
    fun `17 - full RLS matrix for profiles, favorites, reports, notifications, and audit_logs`() {
        // 1. Profiles: المستخدم يقرأ ملفه فقط، والمشرف يقرأ الجميع
        assertEquals(1, engine.queryProfiles(userA).size)
        assertEquals(3, engine.queryProfiles(adminUser).size)

        // منع المستخدم A من تعديل ملف المستخدم B
        try {
            engine.updateProfileByClient(userA, userB, fullName = "اختراق")
            fail("يجب منع المستخدم A من تعديل ملف المستخدم B")
        } catch (e: SecurityViolationException) {
            assertEquals("RLS_PROFILE_UPDATE_OTHER_DENIED", e.code)
        }

        // منع المستخدم المحظور من فك الحظر عن نفسه
        engine.adminSetUserBanStatusRpc(adminUser, userB, isBanned = true, banReason = "إساءة استخدام")
        try {
            engine.updateProfileByClient(userB, userB, attemptedIsBannedChange = false)
            fail("يجب منع المستخدم المحظور من تغيير is_banned")
        } catch (e: SecurityViolationException) {
            assertEquals("RLS_PROFILE_BAN_BYPASS_DENIED", e.code)
        }
        engine.adminSetUserBanStatusRpc(adminUser, userB, isBanned = false, banReason = null)

        // 2. Favorites: منع القراءة أو الحذف عبر المستخدمين
        val receiptPath = engine.uploadStorageFile(userA, "payment-receipts", "$userA/fav_rec.jpg", "image/jpeg", 90_000L)
        val pay = engine.submitPaymentRequestRpc(userA, "ccp", receiptPath, "image/jpeg", 90_000L)
        engine.reviewPaymentRequestRpc(adminUser, pay.id, "approved")
        val communeId = AlgeriaGeographyCatalog.communesForWilaya(16).first().id
        val listing = engine.createListingWithPaidReceiptRpc(
            callerUserId = userA,
            paymentRequestId = pay.id,
            categoryId = 1,
            wilayaCode = 16,
            communeId = communeId,
            title = "إعلان لاختبار المفضلة والبلاغات",
            description = "وصف إعلان متكامل لاختبار صلاحيات المفضلة والبلاغات.",
            priceDzd = 22000L,
            condition = "good",
            contactPhone = "0550123456"
        )
        engine.addFavorite(userA, userA, listing.id)
        try {
            engine.queryFavorites(userB, targetOwnerId = userA)
            fail("يجب منع المستخدم B من قراءة مفضلة المستخدم A")
        } catch (e: SecurityViolationException) {
            assertEquals("RLS_FAVORITES_SELECT_OTHER_DENIED", e.code)
        }
        try {
            engine.removeFavorite(userB, targetUserId = userA, listingId = listing.id)
            fail("يجب منع المستخدم B من حذف مفضلة المستخدم A")
        } catch (e: SecurityViolationException) {
            assertEquals("RLS_FAVORITES_DELETE_OTHER_DENIED", e.code)
        }

        // 3. Reports: المستخدم يبلّغ عن إعلان منشور ولكن لا يستطيع قراءة جدول البلاغات
        val report = engine.submitReport(userB, userB, listing.id, "محتوى مخالف", "تفاصيل البلاغ")
        assertNotNull(report.id)
        try {
            engine.queryReports(userB)
            fail("يجب منع المستخدم العادي من قراءة جدول البلاغات")
        } catch (e: SecurityViolationException) {
            assertEquals("RLS_REPORTS_SELECT_DENIED", e.code)
        }
        assertEquals(1, engine.queryReports(adminUser).size)

        // 4. Notifications: المستخدم يقرأ ويحدث إشعاراته فقط
        val userANotifs = engine.queryNotifications(userA, userA)
        assertTrue(userANotifs.isNotEmpty())
        try {
            engine.markNotificationRead(userB, userANotifs.first().id)
            fail("يجب منع المستخدم B من تحديث إشعارات المستخدم A")
        } catch (e: SecurityViolationException) {
            assertEquals("RLS_NOTIFICATIONS_UPDATE_OTHER_DENIED", e.code)
        }

        // 5. Audit Logs: للمشرف فقط
        try {
            engine.getAuditLogsForAdmin(userA)
            fail("يجب منع المستخدم العادي من قراءة سجل التدقيق")
        } catch (e: SecurityViolationException) {
            assertEquals("RLS_AUDIT_LOGS_DENIED", e.code)
        }
    }

    @Test
    fun `18 - RPC security for admin_set_user_ban_status, admin_update_app_settings, admin_moderate_listing, and email_confirmed`() {
        // منع المستخدم العادي من استدعاء دوال الإدارة
        try {
            engine.adminSetUserBanStatusRpc(userA, userB, true, "محاولة غير مصرحة")
            fail("يجب منع غير المشرف من حظر المستخدمين")
        } catch (e: SecurityViolationException) {
            assertEquals("FORBIDDEN_ADMIN_ONLY", e.code)
        }

        // تحديث السعر الخادمي من قبل المشرف
        val updatedSettings = engine.adminUpdateAppSettingsRpc(
            callerUserId = adminUser,
            listingFeeDzd = 600L,
            ccpInstructionsAr = "حساب CCP محدّث",
            baridimobInstructionsAr = "حساب BaridiMob محدّث",
            paymentNoticeAr = "تنبيه محدّث",
            requireEmailConfirmation = true
        )
        assertEquals(600L, updatedSettings.listingFeeDzd)

        // التحقق من رفض نشر إعلان عندما يكون require_email_confirmation=true والحساب غير مؤكد البريد
        val unconfirmedUser = "33333333-3333-3333-3333-333333333333"
        engine.registerUser(unconfirmedUser, "unconfirmed@ocasionet.dz", "مستخدم غير مؤكد", emailConfirmed = false)
        val path = engine.uploadStorageFile(unconfirmedUser, "payment-receipts", "$unconfirmedUser/r.jpg", "image/jpeg", 50_000L)
        val req = engine.submitPaymentRequestRpc(unconfirmedUser, "ccp", path, "image/jpeg", 50_000L)
        engine.reviewPaymentRequestRpc(adminUser, req.id, "approved")
        val communeId = AlgeriaGeographyCatalog.communesForWilaya(16).first().id

        try {
            engine.createListingWithPaidReceiptRpc(
                callerUserId = unconfirmedUser,
                paymentRequestId = req.id,
                categoryId = 1,
                wilayaCode = 16,
                communeId = communeId,
                title = "إعلان من حساب غير مؤكد البريد",
                description = "محاولة نشر إعلان قبل تأكيد البريد الإلكتروني عندما يكون الشرط مفعلاً.",
                priceDzd = 12000L,
                condition = "good",
                contactPhone = "0550001122"
            )
            fail("يجب رفض النشر إذا لم يكن البريد مؤكداً وكان الشرط مفعلاً")
        } catch (e: SecurityViolationException) {
            assertEquals("EMAIL_NOT_CONFIRMED", e.code)
        }
    }

    @Test
    fun `19 - listing-images bucket supports owner upload, public read, folder ownership verification, and owner delete`() {
        val imgPath = engine.uploadStorageFile(
            callerUserId = userA,
            bucket = "listing-images",
            objectPath = "$userA/phone_front.webp",
            mimeType = "image/webp",
            sizeBytes = 150_000L
        )
        // القراءة العامة متاحة للزوار ولأي مستخدم
        assertNotNull(engine.readListingImagePublic(imgPath))

        // لا يمكن للمستخدم A تمرير مسار صورة يخص المستخدم B عند نشر الإعلان
        val receiptPath = engine.uploadStorageFile(userA, "payment-receipts", "$userA/r_img.jpg", "image/jpeg", 80_000L)
        val req = engine.submitPaymentRequestRpc(userA, "ccp", receiptPath, "image/jpeg", 80_000L)
        engine.reviewPaymentRequestRpc(adminUser, req.id, "approved")
        val communeId = AlgeriaGeographyCatalog.communesForWilaya(16).first().id

        try {
            engine.createListingWithPaidReceiptRpc(
                callerUserId = userA,
                paymentRequestId = req.id,
                categoryId = 1,
                wilayaCode = 16,
                communeId = communeId,
                title = "إعلان بصورة خارج مجلد المالك",
                description = "محاولة تمرير مسار صورة في مجلد مستخدم آخر.",
                priceDzd = 25000L,
                condition = "good",
                contactPhone = "0550112233",
                imageUrls = listOf("$userB/stolen_image.jpg")
            )
            fail("يجب رفض تمرير صورة خارج مجلد المستخدم المالك")
        } catch (e: SecurityViolationException) {
            assertEquals("FORBIDDEN_IMAGE_PATH", e.code)
        }

        // لا يمكن للمستخدم B حذف صورة المستخدم A
        try {
            engine.deleteStorageFile(userB, "listing-images", imgPath)
            fail("يجب منع المستخدم B من حذف صورة المستخدم A")
        } catch (e: SecurityViolationException) {
            assertEquals("STORAGE_DELETE_FORBIDDEN", e.code)
        }

        // المالك نفسه يستطيع حذف صورته
        assertTrue(engine.deleteStorageFile(userA, "listing-images", imgPath))
    }

    @Test
    fun `20 - deep link callback parser handles signup confirmation, password recovery, and expired links`() = runBlocking {
        val signupCallback =
            "occasionet://auth-callback#access_token=tok_signup_1&refresh_token=ref_signup_1&expires_in=3600&type=signup"
        val resSignup = SupabaseClient.handleAuthCallbackUri(signupCallback)
        assertTrue(resSignup is AuthCallbackResult.EmailConfirmed)
        assertEquals("tok_signup_1", SupabaseClient.currentAccessToken)

        val recoveryCallback =
            "occasionet://auth-callback#access_token=tok_rec_2&refresh_token=ref_rec_2&expires_in=3600&type=recovery"
        val resRecovery = SupabaseClient.handleAuthCallbackUri(recoveryCallback)
        assertTrue(resRecovery is AuthCallbackResult.PasswordRecovery)
        assertEquals("tok_rec_2", SupabaseClient.currentAccessToken)

        val expiredCallback =
            "occasionet://auth-callback?error=access_denied&error_description=Email+link+is+invalid+or+has+expired"
        val resExpired = SupabaseClient.handleAuthCallbackUri(expiredCallback)
        assertTrue(resExpired is AuthCallbackResult.Error)
    }

    @Test
    fun `21 - orphan receipt compensating delete cleans up unlinked file on RPC failure and forbids deleting linked receipt`() {
        val orphanObjectPath = "$userA/orphan_receipt.jpg"

        // 1. نجاح رفع الملف إلى Storage ثم فشل استدعاء RPC -> يتم حذف الملف اليتيم تعويضياً
        try {
            engine.uploadAndSubmitPaymentWithCompensation(
                callerUserId = userA,
                objectPath = orphanObjectPath,
                mimeType = "image/jpeg",
                sizeBytes = 100_000L,
                paymentMethod = "ccp",
                simulateRpcFailureAfterUpload = true
            )
            fail("يجب أن يرمي استثناء عند فشل RPC بعد الرفع")
        } catch (e: SecurityViolationException) {
            assertEquals("RPC_TRANSIENT_FAILURE", e.code)
        }
        assertFalse(
            "يجب حذف الملف اليتيم من Storage بعد فشل RPC",
            engine.hasStorageFile("payment-receipts", orphanObjectPath)
        )

        // 2. إذا نجح الرفع ونجح تسجيل طلب الدفع، يُمنع حذف الإيصال المرتبط بطلب دفع قائم
        val linkedReq = engine.uploadAndSubmitPaymentWithCompensation(
            callerUserId = userA,
            objectPath = "$userA/linked_receipt.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 100_000L,
            paymentMethod = "baridimob",
            simulateRpcFailureAfterUpload = false
        )
        assertTrue(engine.hasStorageFile("payment-receipts", linkedReq.receiptStoragePath))

        try {
            engine.deleteStorageFile(userA, "payment-receipts", linkedReq.receiptStoragePath)
            fail("يجب منع حذف إيصال دفع مرتبط بطلب دفع مسجل")
        } catch (e: SecurityViolationException) {
            assertEquals("RECEIPT_LINKED_CANNOT_DELETE", e.code)
        }
    }

    @Test
    fun `22 - DataStoreRepository manages local user preferences and encrypted auth tokens independently`() = runBlocking {
        val tempFile = java.io.File.createTempFile("occasionet_test_prefs_", ".preferences_pb").apply {
            deleteOnExit()
        }
        val repo = dz.ocasionet.core.repository.DataStoreRepository.createForTesting(tempFile)

        // 1. التحقق من القيم الافتراضية لتفضيلات المستخدم المحلية
        val initialPrefs = repo.getUserPreferences()
        assertFalse(initialPrefs.darkModeEnabled)
        assertNull(initialPrefs.preferredWilayaCode)
        assertTrue(initialPrefs.notificationsEnabled)
        assertEquals("ar", initialPrefs.languageCode)

        // 2. تحديث تفضيلات المستخدم المحلية والفلاتر (الولاية، البلدية، الفئة، ونطاق السعر) والتحقق من حفظها واسترجاعها
        repo.setDarkModeEnabled(true)
        repo.saveFilterPreferences(
            categoryId = 2,
            wilayaCode = 16,
            communeId = 1601,
            minPriceDzd = 5_000L,
            maxPriceDzd = 120_000L
        )
        repo.setAcceptedTermsAndPrivacy(true)

        val updatedPrefs = repo.getUserPreferences()
        assertTrue(updatedPrefs.darkModeEnabled)
        assertEquals(16, updatedPrefs.preferredWilayaCode)
        assertEquals(1601, updatedPrefs.preferredCommuneId)
        assertEquals(2, updatedPrefs.preferredCategoryId)
        assertEquals(5_000L, updatedPrefs.preferredMinPriceDzd)
        assertEquals(120_000L, updatedPrefs.preferredMaxPriceDzd)
        assertTrue(updatedPrefs.acceptedTermsAndPrivacy)

        // 3. حفظ رموز المصادقة المشفرة عبر DataStoreRepository والتكامل مع SupabaseClient
        SupabaseClient.setSessionStoreForTesting(repo)
        SupabaseClient.persistSession(
            accessToken = "ds_access_token_secret",
            refreshToken = "ds_refresh_token_secret",
            userId = userA,
            email = "amina@occasionet.dz",
            expiresInSeconds = 3600L,
            nowEpochSeconds = 2_000_000L
        )

        assertEquals("ds_access_token_secret", repo.getAccessToken())
        assertEquals("ds_refresh_token_secret", repo.getRefreshToken())
        val loadedSession = repo.getAuthSession()
        assertNotNull(loadedSession)
        assertEquals(userA, loadedSession?.userId)

        // التأكد من أن الملف المخزن على القرص لا يحتوي النص الصريح لرمز الوصول (مشفر بـ AES/GCM)
        val rawBytes = tempFile.readBytes().toString(Charsets.UTF_8)
        assertFalse(
            "يجب ألا يُخزن رمز الوصول كنص صريح داخل ملف DataStore",
            rawBytes.contains("ds_access_token_secret")
        )

        // 4. مسح رموز المصادقة عند تسجيل الخروج مع بقاء تفضيلات المستخدم المحلية سليمة
        SupabaseClient.clearSession()
        assertNull(repo.getAuthSession())
        assertNull(repo.getAccessToken())
        assertTrue(repo.getUserPreferences().darkModeEnabled)
        assertEquals(16, repo.getUserPreferences().preferredWilayaCode)
    }
}
