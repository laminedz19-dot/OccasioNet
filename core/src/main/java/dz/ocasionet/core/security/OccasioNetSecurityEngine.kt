package dz.ocasionet.core.security

import dz.ocasionet.core.data.AlgeriaGeographyCatalog
import dz.ocasionet.core.model.AppSettingsData
import dz.ocasionet.core.model.AuditLogItem
import dz.ocasionet.core.model.ListingItem
import dz.ocasionet.core.model.PaymentRequestItem
import dz.ocasionet.core.model.UserProfile
import dz.ocasionet.core.model.UserRoleRecord
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * محرك التحقق الأمني ومحاكاة عقود RLS والدوال الخادمية (يُستخدم أيضاً للتحقق المسبق
 * ولاختبار الـ 15 سيناريو الأمني الإلزامي بما فيها التزامن ومنع الاستهلاك المزدوج).
 *
 * أسماء الجداول والدوال هنا مطابقة حرفياً لملفات SQL في supabase/migrations/.
 */
object SupabaseSchemaContract {
    val REQUIRED_TABLES: Set<String> = setOf(
        "profiles",
        "user_roles",
        "categories",
        "wilayas",
        "communes",
        "listings",
        "payment_requests",
        "favorites",
        "reports",
        "notifications",
        "app_settings",
        "audit_logs"
    )

    val REQUIRED_RPCS: Set<String> = setOf(
        "is_admin",
        "submit_payment_request",
        "review_payment_request",
        "create_listing_with_paid_receipt",
        "admin_set_user_ban_status",
        "admin_update_app_settings",
        "admin_moderate_listing"
    )

    val REQUIRED_BUCKETS: Set<String> = setOf(
        "payment-receipts",
        "listing-images"
    )

    val ALLOWED_RECEIPT_MIMES: Set<String> = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "application/pdf"
    )

    val ALLOWED_LISTING_IMAGE_MIMES: Set<String> = setOf(
        "image/jpeg",
        "image/png",
        "image/webp"
    )

    const val MAX_UPLOAD_SIZE_BYTES: Long = 5L * 1024L * 1024L // 5 MB
}

class SecurityViolationException(val code: String, override val message: String) : Exception("$code: $message")

/**
 * محرك قواعد RLS والعمليات الذرية (In-Memory Reference Engine) مطابق لسلوك PostgreSQL RLS + RPC
 * للتحقق من الصلاحيات واختبار التزامن ومنع التلاعب بالسعر أو الأدوار أو الإيصالات.
 */
class RlsPolicyVerifier {
    private val lock = ReentrantLock()

    private val profiles = mutableMapOf<String, UserProfile>()
    private val roles = mutableMapOf<String, UserRoleRecord>()
    private val listings = mutableMapOf<String, ListingItem>()
    private val paymentRequests = mutableMapOf<String, PaymentRequestItem>()
    private val auditLogs = mutableListOf<AuditLogItem>()
    private val storageObjects = mutableMapOf<String, ByteArray>() // key: "bucket/path"
    private var appSettings = AppSettingsData(id = 1, listingFeeDzd = 500L)

    fun registerUser(userId: String, email: String, fullName: String, isBanned: Boolean = false) {
        lock.withLock {
            profiles[userId] = UserProfile(
                id = userId,
                email = email,
                fullName = fullName,
                isBanned = isBanned,
                emailConfirmed = true
            )
            roles.putIfAbsent(userId, UserRoleRecord(userId = userId, role = "user"))
        }
    }

    /**
     * منح دور المشرف من بيئة SQL الإدارية الخادمية فقط (وليس من العميل).
     */
    fun grantAdminRoleFromSqlEditorOnly(targetUserId: String, sqlOperatorId: String) {
        lock.withLock {
            roles[targetUserId] = UserRoleRecord(
                userId = targetUserId,
                role = "admin",
                grantedBy = sqlOperatorId
            )
        }
    }

    fun isAdmin(callerUserId: String?): Boolean = lock.withLock {
        if (callerUserId == null) return false
        val profile = profiles[callerUserId] ?: return false
        val role = roles[callerUserId]?.role ?: "user"
        !profile.isBanned && role == "admin"
    }

    /**
     * محاولة المستخدم تغيير دوره من العميل (ممنوعة تماماً بواسطة RLS).
     */
    fun attemptClientRoleChange(callerUserId: String?, targetUserId: String, newRole: String) {
        throw SecurityViolationException(
            "RLS_ROLE_ESCALATION_DENIED",
            "لا يملك العميل صلاحية تعديل جدول user_roles لتغيير الدور إلى $newRole."
        )
    }

    /**
     * الوصول إلى لوحة الإدارة (يتحقق من الخادم).
     */
    fun verifyAdminDashboardAccess(callerUserId: String?): Boolean {
        if (!isAdmin(callerUserId)) {
            throw SecurityViolationException(
                "ADMIN_ACCESS_DENIED",
                "رفض الوصول: الحساب الحالي لا يملك صلاحيات المشرف الموثقة خادمياً."
            )
        }
        return true
    }

    /**
     * قراءة الإعلانات العامة (متاحة للزوار والمستخدمين للإعلانات المنشورة فقط).
     */
    fun queryVisibleListings(callerUserId: String?): List<ListingItem> = lock.withLock {
        val admin = isAdmin(callerUserId)
        listings.values.filter { item ->
            item.status == "published" || item.sellerId == callerUserId || admin
        }
    }

    /**
     * رفع ملف إلى Supabase Storage مع فحص المسار ونوع MIME والحجم.
     */
    fun uploadStorageFile(
        callerUserId: String?,
        bucket: String,
        objectPath: String,
        mimeType: String,
        sizeBytes: Long,
        bytes: ByteArray = byteArrayOf(1)
    ): String = lock.withLock {
        if (callerUserId == null) {
            throw SecurityViolationException("UNAUTHORIZED_STORAGE", "يجب تسجيل الدخول لرفع الملفات.")
        }
        if (bucket !in SupabaseSchemaContract.REQUIRED_BUCKETS) {
            throw SecurityViolationException("INVALID_BUCKET", "اسم الحاوية غير صالح.")
        }
        val folderOwner = objectPath.substringBefore("/", missingDelimiterValue = "")
        if (folderOwner != callerUserId) {
            throw SecurityViolationException(
                "STORAGE_FOLDER_VIOLATION",
                "لا يمكن للمستخدم الرفع خارج مجلده الخاص ($callerUserId)."
            )
        }
        if (sizeBytes <= 0 || sizeBytes > SupabaseSchemaContract.MAX_UPLOAD_SIZE_BYTES) {
            throw SecurityViolationException(
                "STORAGE_SIZE_EXCEEDED",
                "حجم الملف غير مقبول (الحد الأقصى 5 ميجابايت)."
            )
        }
        val allowedMimes = if (bucket == "payment-receipts") {
            SupabaseSchemaContract.ALLOWED_RECEIPT_MIMES
        } else {
            SupabaseSchemaContract.ALLOWED_LISTING_IMAGE_MIMES
        }
        if (mimeType !in allowedMimes) {
            throw SecurityViolationException(
                "STORAGE_MIME_REJECTED",
                "نوع الملف ($mimeType) غير مسموح به في $bucket."
            )
        }
        val fullKey = "$bucket/$objectPath"
        storageObjects[fullKey] = bytes
        objectPath
    }

    /**
     * قراءة إيصال دفع من Storage الخاص (مسموح لمالك الإيصال أو المشرف فقط).
     */
    fun readReceiptFromPrivateStorage(callerUserId: String?, objectPath: String): ByteArray = lock.withLock {
        if (callerUserId == null) {
            throw SecurityViolationException("UNAUTHORIZED_RECEIPT_READ", "الزوار لا يمكنهم قراءة إيصالات الدفع.")
        }
        val folderOwner = objectPath.substringBefore("/", missingDelimiterValue = "")
        if (folderOwner != callerUserId && !isAdmin(callerUserId)) {
            throw SecurityViolationException(
                "RLS_RECEIPT_READ_DENIED",
                "ممنوع الوصول إلى إيصالات دفع تخص مستخدمين آخرين."
            )
        }
        storageObjects["payment-receipts/$objectPath"]
            ?: throw SecurityViolationException("NOT_FOUND", "ملف الإيصال غير موجود.")
    }

    /**
     * إرسال طلب دفع (تفرض الدالة الخادمية السعر الرسمي من app_settings وتتجاهل أي سعر مرسل من العميل).
     */
    fun submitPaymentRequestRpc(
        callerUserId: String?,
        paymentMethod: String,
        receiptStoragePath: String,
        receiptMimeType: String,
        receiptSizeBytes: Long,
        clientAttemptedAmountDzd: Long? = null
    ): PaymentRequestItem = lock.withLock {
        if (callerUserId == null) {
            throw SecurityViolationException("UNAUTHORIZED", "يجب تسجيل الدخول لإرسال طلب دفع.")
        }
        val profile = profiles[callerUserId]
            ?: throw SecurityViolationException("PROFILE_MISSING", "الملف الشخصي غير موجود.")
        if (profile.isBanned) {
            throw SecurityViolationException("ACCOUNT_BANNED", "الحساب محظور.")
        }
        if (!receiptStoragePath.startsWith("$callerUserId/")) {
            throw SecurityViolationException("FORBIDDEN_PATH", "مسار الإيصال لا يخص المستخدم الحالي.")
        }
        if (receiptMimeType !in SupabaseSchemaContract.ALLOWED_RECEIPT_MIMES) {
            throw SecurityViolationException("INVALID_MIME", "امتداد أو نوع ملف الإيصال مرفوض.")
        }
        if (receiptSizeBytes <= 0 || receiptSizeBytes > SupabaseSchemaContract.MAX_UPLOAD_SIZE_BYTES) {
            throw SecurityViolationException("INVALID_SIZE", "حجم ملف الإيصال غير مسموح.")
        }

        // تجاهل clientAttemptedAmountDzd تماماً واعتماد السعر الخادمي الرسمي من app_settings
        val enforcedServerAmount = appSettings.listingFeeDzd
        val id = UUID.randomUUID().toString()
        val req = PaymentRequestItem(
            id = id,
            userId = callerUserId,
            amountDzd = enforcedServerAmount,
            paymentMethod = paymentMethod,
            receiptStoragePath = receiptStoragePath,
            receiptMimeType = receiptMimeType,
            receiptSizeBytes = receiptSizeBytes,
            status = "pending"
        )
        paymentRequests[id] = req
        req
    }

    /**
     * مراجعة طلب الدفع (للمشرف فقط، ويُمنع المشرف أو المستخدم من الموافقة على دفعة تخصه).
     */
    fun reviewPaymentRequestRpc(
        callerUserId: String?,
        requestId: String,
        decision: String,
        rejectionReason: String? = null
    ): PaymentRequestItem = lock.withLock {
        if (!isAdmin(callerUserId)) {
            throw SecurityViolationException("FORBIDDEN_ADMIN_ONLY", "مراجعة الدفعات مخصصة للمشرفين فقط.")
        }
        val existing = paymentRequests[requestId]
            ?: throw SecurityViolationException("NOT_FOUND", "طلب الدفع غير موجود.")
        if (existing.userId == callerUserId) {
            throw SecurityViolationException("SELF_APPROVAL_FORBIDDEN", "لا يمكنك الموافقة على طلب دفع يخصك.")
        }
        if (existing.status != "pending") {
            throw SecurityViolationException("INVALID_STATE", "الطلب تمت مراجعته مسبقاً.")
        }
        if (decision != "approved" && decision != "rejected") {
            throw SecurityViolationException("INVALID_DECISION", "قرار غير صالح.")
        }
        if (decision == "rejected" && (rejectionReason == null || rejectionReason.trim().length < 3)) {
            throw SecurityViolationException("REASON_REQUIRED", "سبب الرفض إلزامي.")
        }

        val updated = existing.copy(
            status = decision,
            rejectionReason = if (decision == "rejected") rejectionReason?.trim() else null,
            reviewedBy = callerUserId,
            reviewedAt = "2026-10-09T10:00:00Z"
        )
        paymentRequests[requestId] = updated
        auditLogs.add(
            AuditLogItem(
                id = UUID.randomUUID().toString(),
                actorId = callerUserId!!,
                actionType = "REVIEW_PAYMENT_${decision.uppercase()}",
                targetTable = "payment_requests",
                targetId = requestId
            )
        )
        updated
    }

    /**
     * الدالة الذرية لنشر الإعلان مقابل استهلاك دفعة معتمدة واحدة فقط (مع قفل تزامن صارم).
     */
    fun createListingWithPaidReceiptRpc(
        callerUserId: String?,
        paymentRequestId: String,
        categoryId: Int,
        wilayaCode: Int,
        communeId: Int,
        title: String,
        description: String,
        priceDzd: Long,
        condition: String,
        contactPhone: String
    ): ListingItem = lock.withLock {
        if (callerUserId == null) {
            throw SecurityViolationException("UNAUTHORIZED", "لا يمكن للزائر نشر إعلان دون تسجيل الدخول.")
        }
        val profile = profiles[callerUserId]
            ?: throw SecurityViolationException("PROFILE_MISSING", "الحساب غير موجود.")
        if (profile.isBanned) {
            throw SecurityViolationException("ACCOUNT_BANNED", "الحساب محظور.")
        }
        if (!AlgeriaGeographyCatalog.isCommuneInWilaya(communeId, wilayaCode)) {
            throw SecurityViolationException("INVALID_LOCATION", "البلدية لا تتبع الولاية المختارة.")
        }
        val payment = paymentRequests[paymentRequestId]
            ?: throw SecurityViolationException("PAYMENT_NOT_FOUND", "طلب الدفع غير موجود.")
        if (payment.userId != callerUserId) {
            throw SecurityViolationException("PAYMENT_OWNERSHIP_ERROR", "لا تملك طلب الدفع هذا.")
        }
        if (payment.status == "consumed" || payment.consumedListingId != null) {
            throw SecurityViolationException("PAYMENT_ALREADY_CONSUMED", "تم استهلاك هذه الدفعة مسبقاً.")
        }
        if (payment.status != "approved") {
            throw SecurityViolationException("PAYMENT_NOT_APPROVED", "لا يمكن نشر إعلان بدفعة غير معتمدة.")
        }

        val listingId = UUID.randomUUID().toString()
        val listing = ListingItem(
            id = listingId,
            sellerId = callerUserId,
            paymentRequestId = paymentRequestId,
            categoryId = categoryId,
            wilayaCode = wilayaCode,
            communeId = communeId,
            title = title.trim(),
            description = description.trim(),
            priceDzd = priceDzd,
            condition = condition,
            status = "published",
            contactPhone = contactPhone.trim()
        )
        listings[listingId] = listing
        paymentRequests[paymentRequestId] = payment.copy(
            status = "consumed",
            consumedListingId = listingId,
            consumedAt = "2026-10-09T10:05:00Z"
        )
        auditLogs.add(
            AuditLogItem(
                id = UUID.randomUUID().toString(),
                actorId = callerUserId,
                actionType = "CREATE_LISTING_WITH_RECEIPT",
                targetTable = "listings",
                targetId = listingId
            )
        )
        listing
    }

    fun getAuditLogsForAdmin(callerUserId: String?): List<AuditLogItem> = lock.withLock {
        if (!isAdmin(callerUserId)) {
            throw SecurityViolationException("FORBIDDEN_ADMIN_ONLY", "سجل التدقيق متاح للمشرفين فقط.")
        }
        auditLogs.toList()
    }
}
