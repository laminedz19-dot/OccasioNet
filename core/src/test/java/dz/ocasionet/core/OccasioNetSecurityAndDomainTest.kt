package dz.ocasionet.core

import dz.ocasionet.core.data.AlgeriaGeographyCatalog
import dz.ocasionet.core.model.SupabaseConfigStatus
import dz.ocasionet.core.network.SupabaseClientProvider
import dz.ocasionet.core.network.authorizationHeaderForApiKey
import dz.ocasionet.core.security.RlsPolicyVerifier
import dz.ocasionet.core.security.SecurityViolationException
import dz.ocasionet.core.security.SupabaseSchemaContract
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
 * اختبارات شاملة للـ 15 متطلباً أمنياً ووظيفياً لمنصة OccasioNet (RLS + RPC + Storage + Concurrency).
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
            clientAttemptedAmountDzd = 1L // محاولة تلاعب من العميل لإرسال 1 دج بدلاً من 500 دج
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
        val sampleStatus = SupabaseClientProvider.inspectConfig(
            rawUrl = "https://example.supabase.co",
            rawAnonKey = "REPLACE_WITH_SUPABASE_PUBLISHABLE_KEY"
        )
        assertTrue(sampleStatus is SupabaseConfigStatus.NotConfigured)
        val publishableStatus = SupabaseClientProvider.inspectConfig(
            rawUrl = "https://example.supabase.co",
            rawAnonKey = "sb_publishable_public-example"
        )
        assertTrue(publishableStatus is SupabaseConfigStatus.Configured)

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

        // التحقق من ملفات Supabase CLI ذات الطابع الزمني ومطابقة الجداول والدوال
        val rootDir = File("..").takeIf { File("../supabase/migrations").exists() } ?: File(".")
        val migrationsDir = File(rootDir, "supabase/migrations")
        val migrations = migrationsDir.listFiles { file -> file.extension == "sql" }?.sortedBy { it.name }.orEmpty()
        assertEquals("يجب أن توجد أربع ترحيلات مرتبة", 4, migrations.size)
        val schemaSql = migrations.first { it.name.endsWith("_schema.sql") }.readText()
        val funcSql = migrations.first { it.name.endsWith("_functions.sql") }.readText()
        SupabaseSchemaContract.REQUIRED_TABLES.forEach { table ->
            assertTrue("الجدول $table يجب أن يكون معرفاً في الترحيل الأساسي", schemaSql.contains("public.$table"))
        }
        SupabaseSchemaContract.REQUIRED_RPCS.forEach { rpc ->
            assertTrue("الدالة $rpc يجب أن تكون معرفة في ترحيل الدوال", funcSql.contains("public.$rpc"))
        }
    }

    @Test
    fun `publishable API key is never sent as a Bearer JWT`() {
        assertNull(authorizationHeaderForApiKey("sb_publishable_public-example", null))
        assertEquals("Bearer user-access-token", authorizationHeaderForApiKey("sb_publishable_public-example", "user-access-token"))
        assertEquals("Bearer legacy-anon-jwt", authorizationHeaderForApiKey("legacy-anon-jwt", null))
    }

    @Test
    fun `15 - disallowed MIME types, oversized files, and foreign folder paths are rejected in Storage`() {
        // 1. نوع ملف تنفيذي غير مسموح به
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

        // 2. حجم ملف أكبر من 5MB
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

        // 3. محاولة الرفع داخل مجلد مستخدم آخر
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
}
