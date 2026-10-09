package dz.ocasionet.core.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * حالات الاتصال بمشروع Supabase.
 * إذا لم يقم المالك بإدخال SUPABASE_URL و SUPABASE_ANON_KEY، يظهر تنبيه صريح دون ادعاء الاتصال.
 */
sealed class SupabaseConfigStatus {
    data object NotConfigured : SupabaseConfigStatus()
    data class ForbiddenServiceRoleKey(val reasonAr: String) : SupabaseConfigStatus()
    data class Configured(val url: String, val anonKeyMasked: String) : SupabaseConfigStatus()
}

sealed class ResourceState<out T> {
    data object Idle : ResourceState<Nothing>()
    data object Loading : ResourceState<Nothing>()
    data class Success<T>(val data: T) : ResourceState<T>()
    data class Error(val messageAr: String, val isNetworkError: Boolean = false, val isSessionExpired: Boolean = false) : ResourceState<Nothing>()
}

@JsonClass(generateAdapter = true)
data class UserProfile(
    @Json(name = "id") val id: String,
    @Json(name = "email") val email: String,
    @Json(name = "full_name") val fullName: String = "",
    @Json(name = "phone") val phone: String = "",
    @Json(name = "wilaya_code") val wilayaCode: Int? = null,
    @Json(name = "commune_id") val communeId: Int? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "is_banned") val isBanned: Boolean = false,
    @Json(name = "ban_reason") val banReason: String? = null,
    @Json(name = "email_confirmed") val emailConfirmed: Boolean = false,
    @Json(name = "created_at") val createdAt: String = ""
)

@JsonClass(generateAdapter = true)
data class UserRoleRecord(
    @Json(name = "user_id") val userId: String,
    @Json(name = "role") val role: String, // "user" | "admin"
    @Json(name = "granted_by") val grantedBy: String? = null,
    @Json(name = "granted_at") val grantedAt: String = ""
)

@JsonClass(generateAdapter = true)
data class CategoryItem(
    @Json(name = "id") val id: Int,
    @Json(name = "slug") val slug: String,
    @Json(name = "name_ar") val nameAr: String,
    @Json(name = "name_fr") val nameFr: String = "",
    @Json(name = "icon_name") val iconName: String = "category",
    @Json(name = "is_active") val isActive: Boolean = true,
    @Json(name = "sort_order") val sortOrder: Int = 0
)

@JsonClass(generateAdapter = true)
data class Wilaya(
    @Json(name = "code") val code: Int,
    @Json(name = "name_ar") val nameAr: String,
    @Json(name = "name_latin") val nameLatin: String
)

@JsonClass(generateAdapter = true)
data class Commune(
    @Json(name = "id") val id: Int,
    @Json(name = "wilaya_code") val wilayaCode: Int,
    @Json(name = "postal_code") val postalCode: String,
    @Json(name = "name_ar") val nameAr: String,
    @Json(name = "name_latin") val nameLatin: String
)

enum class ListingCondition(val dbValue: String, val labelAr: String) {
    NEW("new", "جديد بالكرتونة"),
    LIKE_NEW("like_new", "شبه جديد"),
    GOOD("good", "حالة جيدة"),
    FAIR("fair", "مستعمل - حالة مقبولة");

    companion object {
        fun fromDb(value: String): ListingCondition =
            entries.firstOrNull { it.dbValue == value } ?: GOOD
    }
}

enum class ListingStatus(val dbValue: String, val labelAr: String) {
    PUBLISHED("published", "منشور"),
    SOLD("sold", "تم البيع"),
    PAUSED("paused", "موقوف مؤقتاً"),
    HIDDEN_BY_ADMIN("hidden_by_admin", "مخفي من الإدارة"),
    REJECTED("rejected", "مرفوض");

    companion object {
        fun fromDb(value: String): ListingStatus =
            entries.firstOrNull { it.dbValue == value } ?: PUBLISHED
    }
}

enum class PaymentStatus(val dbValue: String, val labelAr: String) {
    PENDING("pending", "قيد المراجعة"),
    APPROVED("approved", "معتمد وجاهز للنشر"),
    REJECTED("rejected", "مرفوض"),
    CONSUMED("consumed", "مستهلك لإعلان منشور");

    companion object {
        fun fromDb(value: String): PaymentStatus =
            entries.firstOrNull { it.dbValue == value } ?: PENDING
    }
}

enum class PaymentMethodType(val dbValue: String, val labelAr: String) {
    CCP("ccp", "التحويل البريدي (CCP)"),
    BARIDIMOB("baridimob", "تطبيق بريدي موب (BaridiMob)");

    companion object {
        fun fromDb(value: String): PaymentMethodType =
            entries.firstOrNull { it.dbValue == value } ?: CCP
    }
}

@JsonClass(generateAdapter = true)
data class ListingItem(
    @Json(name = "id") val id: String,
    @Json(name = "seller_id") val sellerId: String,
    @Json(name = "payment_request_id") val paymentRequestId: String,
    @Json(name = "category_id") val categoryId: Int,
    @Json(name = "wilaya_code") val wilayaCode: Int,
    @Json(name = "commune_id") val communeId: Int,
    @Json(name = "title") val title: String,
    @Json(name = "description") val description: String,
    @Json(name = "price_dzd") val priceDzd: Long,
    @Json(name = "condition") val condition: String,
    @Json(name = "status") val status: String = "published",
    @Json(name = "contact_phone") val contactPhone: String = "",
    @Json(name = "image_urls") val imageUrls: List<String> = emptyList(),
    @Json(name = "views_count") val viewsCount: Int = 0,
    @Json(name = "created_at") val createdAt: String = ""
)

@JsonClass(generateAdapter = true)
data class PaymentRequestItem(
    @Json(name = "id") val id: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "amount_dzd") val amountDzd: Long,
    @Json(name = "payment_method") val paymentMethod: String,
    @Json(name = "receipt_storage_path") val receiptStoragePath: String,
    @Json(name = "receipt_mime_type") val receiptMimeType: String,
    @Json(name = "receipt_size_bytes") val receiptSizeBytes: Long,
    @Json(name = "transaction_reference") val transactionReference: String = "",
    @Json(name = "user_note") val userNote: String = "",
    @Json(name = "status") val status: String = "pending",
    @Json(name = "rejection_reason") val rejectionReason: String? = null,
    @Json(name = "reviewed_by") val reviewedBy: String? = null,
    @Json(name = "reviewed_at") val reviewedAt: String? = null,
    @Json(name = "consumed_listing_id") val consumedListingId: String? = null,
    @Json(name = "consumed_at") val consumedAt: String? = null,
    @Json(name = "created_at") val createdAt: String = ""
)

@JsonClass(generateAdapter = true)
data class FavoriteRecord(
    @Json(name = "user_id") val userId: String,
    @Json(name = "listing_id") val listingId: String,
    @Json(name = "created_at") val createdAt: String = ""
)

@JsonClass(generateAdapter = true)
data class ReportItem(
    @Json(name = "id") val id: String,
    @Json(name = "listing_id") val listingId: String,
    @Json(name = "reporter_id") val reporterId: String,
    @Json(name = "reason") val reason: String,
    @Json(name = "details") val details: String = "",
    @Json(name = "status") val status: String = "open",
    @Json(name = "reviewed_by") val reviewedBy: String? = null,
    @Json(name = "reviewed_at") val reviewedAt: String? = null,
    @Json(name = "admin_note") val adminNote: String? = null,
    @Json(name = "created_at") val createdAt: String = ""
)

@JsonClass(generateAdapter = true)
data class NotificationItem(
    @Json(name = "id") val id: String,
    @Json(name = "user_id") val userId: String,
    @Json(name = "title_ar") val titleAr: String,
    @Json(name = "body_ar") val bodyAr: String,
    @Json(name = "type") val type: String = "general",
    @Json(name = "is_read") val isRead: Boolean = false,
    @Json(name = "related_entity_id") val relatedEntityId: String? = null,
    @Json(name = "created_at") val createdAt: String = ""
)

@JsonClass(generateAdapter = true)
data class AppSettingsData(
    @Json(name = "id") val id: Int = 1,
    @Json(name = "listing_fee_dzd") val listingFeeDzd: Long = 500L,
    @Json(name = "ccp_instructions_ar") val ccpInstructionsAr: String = "يرجى التواصل مع الإدارة أو انتظار ضبط بيانات الحساب البريدي الجاري (CCP) الرسمي من لوحة التحكم.",
    @Json(name = "baridimob_instructions_ar") val baridimobInstructionsAr: String = "يرجى التواصل مع الإدارة أو انتظار ضبط رقم RIP الخاص بـ BaridiMob من لوحة التحكم.",
    @Json(name = "payment_notice_ar") val paymentNoticeAr: String = "رسوم نشر الإعلان الواحد هي 500 دج. يتم تفعيل النشر بعد مراجعة المشرف لإثبات الدفع.",
    @Json(name = "require_email_confirmation") val requireEmailConfirmation: Boolean = true,
    @Json(name = "max_images_per_listing") val maxImagesPerListing: Int = 5,
    @Json(name = "updated_at") val updatedAt: String = ""
)

@JsonClass(generateAdapter = true)
data class AuditLogItem(
    @Json(name = "id") val id: String,
    @Json(name = "actor_id") val actorId: String,
    @Json(name = "action_type") val actionType: String,
    @Json(name = "target_table") val targetTable: String,
    @Json(name = "target_id") val targetId: String,
    @Json(name = "created_at") val createdAt: String = ""
)

data class ListingFilter(
    val query: String = "",
    val categoryId: Int? = null,
    val wilayaCode: Int? = null,
    val communeId: Int? = null,
    val condition: ListingCondition? = null,
    val minPriceDzd: Long? = null,
    val maxPriceDzd: Long? = null
)

data class AdminFinancialSummary(
    val pendingCount: Int,
    val approvedUnusedCount: Int,
    val consumedCount: Int,
    val rejectedCount: Int,
    val totalSubmittedDzd: Long,
    val totalApprovedAndConsumedDzd: Long
)
