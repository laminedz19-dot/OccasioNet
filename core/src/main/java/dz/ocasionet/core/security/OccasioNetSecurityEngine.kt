package dz.ocasionet.core.security

import dz.ocasionet.core.data.AlgeriaGeographyCatalog
import dz.ocasionet.core.model.AppSettingsData
import dz.ocasionet.core.model.AuditLogItem
import dz.ocasionet.core.model.FavoriteRecord
import dz.ocasionet.core.model.ListingItem
import dz.ocasionet.core.model.NotificationItem
import dz.ocasionet.core.model.PaymentRequestItem
import dz.ocasionet.core.model.ReportItem
import dz.ocasionet.core.model.UserProfile
import dz.ocasionet.core.model.UserRoleRecord
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * محرك التحقق الأمني ومحاكاة عقود RLS والدوال الخادمية وStorage.
 * أسماء الجداول والدوال والحاويات هنا مطابقة حرفياً لملفات SQL في supabase/migrations/.
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
 * محرك قواعد RLS والعمليات الذرية (In-Memory Reference Engine) مطابق لسلوك PostgreSQL RLS + PostgREST + Storage.
 */
class RlsPolicyVerifier {
    private val lock = ReentrantLock()

    private val profiles = mutableMapOf<String, UserProfile>()
    private val roles = mutableMapOf<String, UserRoleRecord>()
    private val listings = mutableMapOf<String, ListingItem>()
    private val paymentRequests = mutableMapOf<String, PaymentRequestItem>()
    private val favorites = mutableListOf<FavoriteRecord>()
    private val reports = mutableMapOf<String, ReportItem>()
    private val notifications = mutableMapOf<String, NotificationItem>()
    private val auditLogs = mutableListOf<AuditLogItem>()
    private val storageObjects = mutableMapOf<String, ByteArray>() // key: "bucket/path"
    private var appSettings = AppSettingsData(
        id = 1,
        listingFeeDzd = 500L,
        requireEmailConfirmation = true,
        maxImagesPerListing = 5
    )

    fun registerUser(
        userId: String,
        email: String,
        fullName: String,
        isBanned: Boolean = false,
        emailConfirmed: Boolean = true
    ) {
        lock.withLock {
            profiles[userId] = UserProfile(
                id = userId,
                email = email,
                fullName = fullName,
                isBanned = isBanned,
                emailConfirmed = emailConfirmed
            )
            // يحاكي سلوك handle_new_user trigger: ينشئ دور 'user' مع ON CONFLICT DO NOTHING
            roles.putIfAbsent(userId, UserRoleRecord(userId = userId, role = "user"))
        }
    }

    fun setUserEmailConfirmed(userId: String, confirmed: Boolean) = lock.withLock {
        val current = profiles[userId] ?: return@withLock
        profiles[userId] = current.copy(emailConfirmed = confirmed)
    }

    fun setRequireEmailConfirmation(required: Boolean) = lock.withLock {
        appSettings = appSettings.copy(requireEmailConfirmation = required)
    }

    /**
     * يحاكي خطأ استخدام ON CONFLICT DO NOTHING بعد إنشاء المستخدم (لا يرقّي الدور لأن صف 'user' موجود مسبقاً).
     */
    fun attemptSqlGrantAdminWithDoNothing(targetUserId: String): String = lock.withLock {
        roles.putIfAbsent(targetUserId, UserRoleRecord(userId = targetUserId, role = "admin"))
        roles[targetUserId]?.role ?: "user"
    }

    /**
     * منح دور المشرف من بيئة SQL الإدارية الخادمية فقط باستخدام ON CONFLICT (user_id) DO UPDATE.
     */
    fun grantAdminRoleFromSqlEditorOnly(targetUserId: String, sqlOperatorId: String) {
        lock.withLock {
            val profile = profiles[targetUserId]
                ?: throw SecurityViolationException("USER_NOT_FOUND", "المستخدم غير موجود.")
            if (!profile.emailConfirmed) {
                throw SecurityViolationException("EMAIL_NOT_CONFIRMED", "يجب تأكيد بريد المشرف قبل ترقيته.")
            }
            if (profile.isBanned) {
                throw SecurityViolationException("USER_BANNED", "لا يمكن ترقية حساب محظور إلى مشرف.")
            }
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

    fun attemptClientRoleChange(callerUserId: String?, targetUserId: String, newRole: String) {
        throw SecurityViolationException(
            "RLS_ROLE_ESCALATION_DENIED",
            "لا يملك العميل صلاحية تعديل جدول user_roles لتغيير الدور إلى $newRole."
        )
    }

    fun verifyAdminDashboardAccess(callerUserId: String?): Boolean {
        if (!isAdmin(callerUserId)) {
            throw SecurityViolationException(
                "ADMIN_ACCESS_DENIED",
                "رفض الوصول: الحساب الحالي لا يملك صلاحيات المشرف الموثقة خادمياً."
            )
        }
        return true
    }

    // --- استعلامات PostgREST المحكومة بسياسات RLS (تُرجع قائمة فارغة عندما تحجب السياسة الصفوف) ---

    fun queryProfiles(callerUserId: String?): List<UserProfile> = lock.withLock {
        if (callerUserId == null) return emptyList()
        if (isAdmin(callerUserId)) return profiles.values.toList()
        profiles[callerUserId]?.let { listOf(it) } ?: emptyList()
    }

    fun updateProfileByClient(
        callerUserId: String?,
        targetUserId: String,
        fullName: String? = null,
        attemptedIsBannedChange: Boolean? = null
    ): UserProfile = lock.withLock {
        if (callerUserId == null || callerUserId != targetUserId) {
            throw SecurityViolationException(
                "RLS_PROFILE_UPDATE_OTHER_DENIED",
                "لا يمكن للمستخدم تعديل ملف شخصي لمستخدم آخر."
            )
        }
        val existing = profiles[callerUserId]
            ?: throw SecurityViolationException("PROFILE_MISSING", "الملف غير موجود.")
        if (existing.isBanned || (attemptedIsBannedChange != null && attemptedIsBannedChange != existing.isBanned)) {
            throw SecurityViolationException(
                "RLS_PROFILE_BAN_BYPASS_DENIED",
                "لا يمكن للمستخدم المحظور تعديل ملفه أو تغيير حالة is_banned."
            )
        }
        val updated = existing.copy(fullName = fullName ?: existing.fullName)
        profiles[callerUserId] = updated
        updated
    }

    fun queryUserRoles(callerUserId: String?): List<UserRoleRecord> = lock.withLock {
        if (callerUserId == null) return emptyList()
        if (isAdmin(callerUserId)) return roles.values.toList()
        roles[callerUserId]?.let { listOf(it) } ?: emptyList()
    }

    fun queryVisibleListings(callerUserId: String?): List<ListingItem> = lock.withLock {
        val admin = isAdmin(callerUserId)
        listings.values.filter { item ->
            item.status == "published" || item.sellerId == callerUserId || admin
        }
    }

    fun queryPaymentRequests(callerUserId: String?): List<PaymentRequestItem> = lock.withLock {
        if (callerUserId == null) return emptyList()
        if (isAdmin(callerUserId)) return paymentRequests.values.toList()
        paymentRequests.values.filter { it.userId == callerUserId }
    }

    fun attemptDirectPaymentUpdateFromClient(callerUserId: String?, paymentId: String, newStatus: String) {
        throw SecurityViolationException(
            "RLS_PAYMENT_UPDATE_DENIED",
            "لا توجد سياسة UPDATE للعميل على جدول payment_requests."
        )
    }

    fun addFavorite(callerUserId: String?, targetUserId: String, listingId: String): FavoriteRecord = lock.withLock {
        if (callerUserId == null || callerUserId != targetUserId) {
            throw SecurityViolationException("RLS_FAVORITE_DENIED", "لا يمكن إضافة مفضلة باسم مستخدم آخر أو كزائر.")
        }
        val rec = FavoriteRecord(userId = callerUserId, listingId = listingId)
        favorites.removeAll { it.userId == callerUserId && it.listingId == listingId }
        favorites.add(rec)
        rec
    }

    fun removeFavorite(callerUserId: String?, targetUserId: String, listingId: String): Boolean = lock.withLock {
        if (callerUserId == null || callerUserId != targetUserId) {
            throw SecurityViolationException(
                "RLS_FAVORITES_DELETE_OTHER_DENIED",
                "لا يمكن للمستخدم حذف مفضلة تخص مستخدماً آخر."
            )
        }
        favorites.removeAll { it.userId == targetUserId && it.listingId == listingId }
    }

    fun queryFavorites(callerUserId: String?, targetOwnerId: String? = callerUserId): List<FavoriteRecord> = lock.withLock {
        if (callerUserId == null) return emptyList()
        if (targetOwnerId != null && targetOwnerId != callerUserId) {
            throw SecurityViolationException(
                "RLS_FAVORITES_SELECT_OTHER_DENIED",
                "لا يمكن للمستخدم قراءة مفضلة مستخدم آخر."
            )
        }
        favorites.filter { it.userId == callerUserId }
    }

    fun submitReport(
        callerUserId: String?,
        reporterId: String,
        listingId: String,
        reason: String,
        details: String = ""
    ): ReportItem = lock.withLock {
        if (callerUserId == null || callerUserId != reporterId) {
            throw SecurityViolationException("RLS_REPORT_INSERT_DENIED", "يجب أن يطابق reporter_id معرّف الجلسة الحالي.")
        }
        val id = UUID.randomUUID().toString()
        val item = ReportItem(id = id, listingId = listingId, reporterId = callerUserId, reason = reason, details = details)
        reports[id] = item
        item
    }

    fun queryReports(callerUserId: String?): List<ReportItem> = lock.withLock {
        if (callerUserId == null || !isAdmin(callerUserId)) {
            throw SecurityViolationException("RLS_REPORTS_SELECT_DENIED", "قراءة جدول البلاغات الكامل متاحة للمشرفين فقط.")
        }
        reports.values.toList()
    }

    fun queryNotifications(callerUserId: String?, targetUserId: String? = callerUserId): List<NotificationItem> = lock.withLock {
        if (callerUserId == null) return emptyList()
        if (targetUserId != null && targetUserId != callerUserId) {
            throw SecurityViolationException("RLS_NOTIFICATIONS_SELECT_OTHER_DENIED", "لا يمكن قراءة إشعارات مستخدم آخر.")
        }
        notifications.values.filter { it.userId == callerUserId }
    }

    fun markNotificationRead(callerUserId: String?, notificationId: String): NotificationItem = lock.withLock {
        val notif = notifications[notificationId]
            ?: throw SecurityViolationException("NOT_FOUND", "الإشعار غير موجود.")
        if (callerUserId == null || notif.userId != callerUserId) {
            throw SecurityViolationException("RLS_NOTIFICATIONS_UPDATE_OTHER_DENIED", "لا يمكن تحديث إشعارات مستخدم آخر.")
        }
        val updated = notif.copy(isRead = true)
        notifications[notificationId] = updated
        updated
    }

    // --- Supabase Storage (payment-receipts & listing-images) ---

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

    fun readListingImagePublic(objectPath: String): ByteArray = lock.withLock {
        storageObjects["listing-images/$objectPath"]
            ?: throw SecurityViolationException("NOT_FOUND", "صورة الإعلان غير موجودة.")
    }

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
     * حذف ملف من Storage وفق سياسات الحذف المحدودة:
     * - في `listing-images`: يقتصر على مجلد المالك `{callerUserId}/...`
     * - في `payment-receipts`: يقتصر على مجلد المالك بشرط عدم ارتباط الإيصال بأي طلب دفع قائم (تنظيف الإيصالات اليتيمة فقط).
     */
    fun deleteStorageFile(callerUserId: String?, bucket: String, objectPath: String): Boolean = lock.withLock {
        if (callerUserId == null) {
            throw SecurityViolationException("UNAUTHORIZED_STORAGE", "يجب تسجيل الدخول لحذف الملفات.")
        }
        val folderOwner = objectPath.substringBefore("/", missingDelimiterValue = "")
        if (folderOwner != callerUserId) {
            throw SecurityViolationException(
                "STORAGE_DELETE_FORBIDDEN",
                "لا يمكن للمستخدم حذف ملفات خارج مجلده الشخصي."
            )
        }
        if (bucket == "payment-receipts") {
            val isLinked = paymentRequests.values.any { it.receiptStoragePath == objectPath }
            if (isLinked) {
                throw SecurityViolationException(
                    "RECEIPT_LINKED_CANNOT_DELETE",
                    "يُمنع حذف إيصال دفع مرتبط بطلب دفع مسجل في قاعدة البيانات."
                )
            }
        }
        storageObjects.remove("$bucket/$objectPath") != null
    }

    fun hasStorageFile(bucket: String, objectPath: String): Boolean = lock.withLock {
        storageObjects.containsKey("$bucket/$objectPath")
    }

    /**
     * تدفق رفع إيصال الدفع مع الحذف التعويضي التلقائي في حال فشل RPC بعد نجاح رفع الملف إلى Storage.
     */
    fun uploadAndSubmitPaymentWithCompensation(
        callerUserId: String?,
        objectPath: String,
        mimeType: String,
        sizeBytes: Long,
        paymentMethod: String,
        simulateRpcFailureAfterUpload: Boolean = false
    ): PaymentRequestItem = lock.withLock {
        val uploadedPath = uploadStorageFile(
            callerUserId = callerUserId,
            bucket = "payment-receipts",
            objectPath = objectPath,
            mimeType = mimeType,
            sizeBytes = sizeBytes
        )
        try {
            if (simulateRpcFailureAfterUpload) {
                throw SecurityViolationException("RPC_TRANSIENT_FAILURE", "فشل استدعاء دالة تسجيل طلب الدفع الخادمية.")
            }
            submitPaymentRequestRpc(
                callerUserId = callerUserId,
                paymentMethod = paymentMethod,
                receiptStoragePath = uploadedPath,
                receiptMimeType = mimeType,
                receiptSizeBytes = sizeBytes
            )
        } catch (e: Exception) {
            // حذف تعويضي للملف اليتيم الذي رُفع للتو ولم يرتبط بأي صف في payment_requests
            runCatching { deleteStorageFile(callerUserId, "payment-receipts", uploadedPath) }
            throw e
        }
    }

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
        val notifId = UUID.randomUUID().toString()
        notifications[notifId] = NotificationItem(
            id = notifId,
            userId = existing.userId,
            titleAr = if (decision == "approved") "تمت الموافقة على طلب الدفع" else "تم رفض طلب الدفع",
            bodyAr = if (decision == "approved") {
                "تمت مراجعة إثبات الدفع والموافقة عليه. يمكنك الآن استخدامه لنشر إعلانك."
            } else {
                "تم رفض طلب الدفع: ${rejectionReason?.trim()}"
            },
            type = if (decision == "approved") "payment_approved" else "payment_rejected",
            relatedEntityId = requestId
        )
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
        contactPhone: String,
        imageUrls: List<String> = emptyList()
    ): ListingItem = lock.withLock {
        if (callerUserId == null) {
            throw SecurityViolationException("UNAUTHORIZED", "لا يمكن للزائر نشر إعلان دون تسجيل الدخول.")
        }
        val profile = profiles[callerUserId]
            ?: throw SecurityViolationException("PROFILE_MISSING", "الحساب غير موجود.")
        if (profile.isBanned) {
            throw SecurityViolationException("ACCOUNT_BANNED", "الحساب محظور.")
        }
        if (appSettings.requireEmailConfirmation && !profile.emailConfirmed) {
            throw SecurityViolationException("EMAIL_NOT_CONFIRMED", "يجب تأكيد البريد الإلكتروني أولاً قبل نشر إعلان.")
        }
        if (imageUrls.size > appSettings.maxImagesPerListing) {
            throw SecurityViolationException("TOO_MANY_IMAGES", "عدد صور الإعلان يتجاوز الحد الأقصى المسموح به.")
        }
        val hasForeignImage = imageUrls.any { img ->
            !img.startsWith("$callerUserId/") &&
                !img.contains("/storage/v1/object/public/listing-images/$callerUserId/") &&
                !img.startsWith("storage/v1/object/public/listing-images/$callerUserId/")
        }
        if (hasForeignImage) {
            throw SecurityViolationException(
                "FORBIDDEN_IMAGE_PATH",
                "جميع صور الإعلان يجب أن تقع داخل مجلد المستخدم المالك في listing-images."
            )
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
            contactPhone = contactPhone.trim(),
            imageUrls = imageUrls
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

    fun adminSetUserBanStatusRpc(
        callerUserId: String?,
        targetUserId: String,
        isBanned: Boolean,
        banReason: String? = null
    ): Boolean = lock.withLock {
        if (!isAdmin(callerUserId)) {
            throw SecurityViolationException("FORBIDDEN_ADMIN_ONLY", "صلاحيات المشرف مطلوبة.")
        }
        if (callerUserId == targetUserId) {
            throw SecurityViolationException("CANNOT_BAN_SELF", "لا يمكن للمشرف حظر حسابه الشخصي.")
        }
        val target = profiles[targetUserId]
            ?: throw SecurityViolationException("NOT_FOUND", "المستخدم المستهدف غير موجود.")
        profiles[targetUserId] = target.copy(isBanned = isBanned, banReason = if (isBanned) banReason else null)
        auditLogs.add(
            AuditLogItem(
                id = UUID.randomUUID().toString(),
                actorId = callerUserId!!,
                actionType = if (isBanned) "BAN_USER" else "UNBAN_USER",
                targetTable = "profiles",
                targetId = targetUserId
            )
        )
        true
    }

    fun adminUpdateAppSettingsRpc(
        callerUserId: String?,
        listingFeeDzd: Long,
        ccpInstructionsAr: String,
        baridimobInstructionsAr: String,
        paymentNoticeAr: String,
        requireEmailConfirmation: Boolean = appSettings.requireEmailConfirmation
    ): AppSettingsData = lock.withLock {
        if (!isAdmin(callerUserId)) {
            throw SecurityViolationException("FORBIDDEN_ADMIN_ONLY", "صلاحيات المشرف مطلوبة.")
        }
        if (listingFeeDzd <= 0) {
            throw SecurityViolationException("INVALID_FEE", "سعر النشر يجب أن يكون أكبر من صفر.")
        }
        appSettings = appSettings.copy(
            listingFeeDzd = listingFeeDzd,
            ccpInstructionsAr = ccpInstructionsAr,
            baridimobInstructionsAr = baridimobInstructionsAr,
            paymentNoticeAr = paymentNoticeAr,
            requireEmailConfirmation = requireEmailConfirmation
        )
        auditLogs.add(
            AuditLogItem(
                id = UUID.randomUUID().toString(),
                actorId = callerUserId!!,
                actionType = "UPDATE_APP_SETTINGS",
                targetTable = "app_settings",
                targetId = "1"
            )
        )
        appSettings
    }

    fun adminModerateListingRpc(
        callerUserId: String?,
        listingId: String,
        newStatus: String,
        reason: String
    ): ListingItem = lock.withLock {
        if (!isAdmin(callerUserId)) {
            throw SecurityViolationException("FORBIDDEN_ADMIN_ONLY", "صلاحيات المشرف مطلوبة.")
        }
        val existing = listings[listingId]
            ?: throw SecurityViolationException("NOT_FOUND", "الإعلان غير موجود.")
        val updated = existing.copy(status = newStatus)
        listings[listingId] = updated
        auditLogs.add(
            AuditLogItem(
                id = UUID.randomUUID().toString(),
                actorId = callerUserId!!,
                actionType = "MODERATE_LISTING_${newStatus.uppercase()}",
                targetTable = "listings",
                targetId = listingId
            )
        )
        updated
    }

    fun getAuditLogsForAdmin(callerUserId: String?): List<AuditLogItem> = lock.withLock {
        if (!isAdmin(callerUserId)) {
            throw SecurityViolationException("RLS_AUDIT_LOGS_DENIED", "سجل التدقيق متاح للمشرفين فقط.")
        }
        auditLogs.toList()
    }
}
