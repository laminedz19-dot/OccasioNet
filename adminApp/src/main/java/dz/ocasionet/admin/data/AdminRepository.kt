package dz.ocasionet.admin.data

import dz.ocasionet.core.data.AlgeriaGeographyCatalog
import dz.ocasionet.core.model.AdminFinancialSummary
import dz.ocasionet.core.model.AppSettingsData
import dz.ocasionet.core.model.AuditLogItem
import dz.ocasionet.core.model.CategoryItem
import dz.ocasionet.core.model.ListingItem
import dz.ocasionet.core.model.PaymentRequestItem
import dz.ocasionet.core.model.ReportItem
import dz.ocasionet.core.model.SupabaseConfigStatus
import dz.ocasionet.core.model.UserProfile
import dz.ocasionet.core.network.AdminBanUserRpcBody
import dz.ocasionet.core.network.AdminModerateListingRpcBody
import dz.ocasionet.core.network.AdminUpdateSettingsRpcBody
import dz.ocasionet.core.network.AuthSignInRequest
import dz.ocasionet.core.network.ReviewPaymentRpcBody
import dz.ocasionet.core.network.SupabaseClientProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * مستودع تطبيق الإدارة المستقل (OccasioNet Admin Repository).
 * يوجد حصرياً داخل وحدة :adminApp (dz.ocasionet.admin) ولا يدخل في بناء تطبيق المستخدم إطلاقاً.
 */
class AdminRepository {

    private val _configStatus = MutableStateFlow(SupabaseClientProvider.inspectConfig())
    val configStatus: StateFlow<SupabaseConfigStatus> = _configStatus.asStateFlow()

    private val _isVerifiedAdmin = MutableStateFlow(false)
    val isVerifiedAdmin: StateFlow<Boolean> = _isVerifiedAdmin.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettingsData())
    val appSettings: StateFlow<AppSettingsData> = _appSettings.asStateFlow()

    private val _categories = MutableStateFlow(AlgeriaGeographyCatalog.defaultCategories)
    val categories: StateFlow<List<CategoryItem>> = _categories.asStateFlow()

    private val _adminPendingPayments = MutableStateFlow<List<PaymentRequestItem>>(emptyList())
    val adminPendingPayments: StateFlow<List<PaymentRequestItem>> = _adminPendingPayments.asStateFlow()

    private val _adminAllPayments = MutableStateFlow<List<PaymentRequestItem>>(emptyList())
    val adminAllPayments: StateFlow<List<PaymentRequestItem>> = _adminAllPayments.asStateFlow()

    private val _adminUsers = MutableStateFlow<List<UserProfile>>(emptyList())
    val adminUsers: StateFlow<List<UserProfile>> = _adminUsers.asStateFlow()

    private val _adminAllListings = MutableStateFlow<List<ListingItem>>(emptyList())
    val adminAllListings: StateFlow<List<ListingItem>> = _adminAllListings.asStateFlow()

    private val _adminReports = MutableStateFlow<List<ReportItem>>(emptyList())
    val adminReports: StateFlow<List<ReportItem>> = _adminReports.asStateFlow()

    private val _adminAuditLogs = MutableStateFlow<List<AuditLogItem>>(emptyList())
    val adminAuditLogs: StateFlow<List<AuditLogItem>> = _adminAuditLogs.asStateFlow()

    @Volatile
    private var actionInFlight = false

    private fun requireConfiguredService() =
        SupabaseClientProvider.createServiceOrNull()

    suspend fun signInAdmin(email: String, password: String): Result<UserProfile> = executeSingleFlight {
        _configStatus.value = SupabaseClientProvider.inspectConfig()
        val service = requireConfiguredService()
            ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))

        val resp = service.signInWithPassword(AuthSignInRequest(email.trim(), password))
        if (!resp.isSuccessful) {
            return@executeSingleFlight Result.failure(
                IllegalStateException("بيانات الدخول غير صحيحة (${resp.code()}).")
            )
        }
        val session = resp.body()
        val token = session?.accessToken
        val userDto = session?.user
        if (token.isNullOrBlank() || userDto == null) {
            return@executeSingleFlight Result.failure(IllegalStateException("جلسة غير صالحة من Supabase Auth."))
        }
        SupabaseClientProvider.currentAccessToken = token

        val verified = verifyAdminRoleFromServer()
        if (!verified) {
            signOut()
            return@executeSingleFlight Result.failure(
                SecurityException("رفض الوصول: هذا الحساب لا يملك صلاحيات المشرف (admin) الموثقة خادمياً.")
            )
        }

        val profile = service.getProfiles(idEq = "eq.${userDto.id}").body()?.firstOrNull()
            ?: return@executeSingleFlight Result.failure(IllegalStateException("تعذر جلب ملف المشرف."))

        if (profile.isBanned) {
            signOut()
            return@executeSingleFlight Result.failure(IllegalStateException("هذا الحساب محظور."))
        }

        refreshAdminDashboardData()
        Result.success(profile)
    }

    suspend fun verifyAdminRoleFromServer(): Boolean {
        val service = requireConfiguredService() ?: return false
        return try {
            val resp = service.rpcIsAdmin()
            val allowed = resp.isSuccessful && (resp.body() == true)
            _isVerifiedAdmin.value = allowed
            if (!allowed) {
                clearAdminSensitiveState()
            }
            allowed
        } catch (_: Exception) {
            _isVerifiedAdmin.value = false
            false
        }
    }

    suspend fun refreshAdminDashboardData(): Result<Unit> {
        if (!verifyAdminRoleFromServer()) {
            return Result.failure(SecurityException("غير مصرح بالوصول إلى بيانات الإدارة."))
        }
        val service = requireConfiguredService()
            ?: return Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))
        return try {
            service.getPaymentRequests(statusEq = "eq.pending").body()?.let { _adminPendingPayments.value = it }
            service.getPaymentRequests().body()?.let { _adminAllPayments.value = it }
            service.getProfiles().body()?.let { _adminUsers.value = it }
            service.getListings(statusEq = null).body()?.let { _adminAllListings.value = it }
            service.getReports().body()?.let { _adminReports.value = it }
            service.getAuditLogs().body()?.let { _adminAuditLogs.value = it }
            service.getAppSettings().body()?.firstOrNull()?.let { _appSettings.value = it }
            service.getCategories().body()?.takeIf { it.isNotEmpty() }?.let { _categories.value = it }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adminReviewPayment(
        requestId: String,
        decision: String,
        rejectionReason: String?
    ): Result<Boolean> = executeSingleFlight {
        if (!verifyAdminRoleFromServer()) {
            return@executeSingleFlight Result.failure(SecurityException("تم رفض العملية: صلاحيات المشرف غير متوفرة."))
        }
        val service = requireConfiguredService()
            ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))
        val resp = service.rpcReviewPaymentRequest(
            ReviewPaymentRpcBody(
                requestId = requestId,
                decision = decision,
                rejectionReason = rejectionReason
            )
        )
        if (resp.isSuccessful && resp.body() == true) {
            refreshAdminDashboardData()
            Result.success(true)
        } else {
            Result.failure(IllegalStateException("فشل تنفيذ مراجعة طلب الدفع (${resp.code()})."))
        }
    }

    suspend fun adminGetSignedReceiptUrl(receiptStoragePath: String): Result<String> = executeSingleFlight {
        if (!verifyAdminRoleFromServer()) {
            return@executeSingleFlight Result.failure(SecurityException("صلاحيات المشرف مطلوبة لعرض إثبات الدفع."))
        }
        val service = requireConfiguredService()
            ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))
        val resp = service.createSignedReceiptUrl(receiptStoragePath)
        val signedPath = resp.body()?.signedUrl
        val status = _configStatus.value
        if (resp.isSuccessful && !signedPath.isNullOrBlank() && status is SupabaseConfigStatus.Configured) {
            val fullUrl = status.url.trimEnd('/') + "/storage/v1" + signedPath
            Result.success(fullUrl)
        } else {
            Result.failure(IllegalStateException("تعذر إنشاء رابط موقع مؤقت لإثبات الدفع (${resp.code()})."))
        }
    }

    suspend fun adminSetUserBanStatus(targetUserId: String, isBanned: Boolean, reason: String?): Result<Boolean> =
        executeSingleFlight {
            if (!verifyAdminRoleFromServer()) {
                return@executeSingleFlight Result.failure(SecurityException("صلاحيات المشرف مطلوبة."))
            }
            val service = requireConfiguredService()
                ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))
            val resp = service.rpcAdminSetUserBanStatus(AdminBanUserRpcBody(targetUserId, isBanned, reason))
            if (resp.isSuccessful && resp.body() == true) {
                refreshAdminDashboardData()
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("تعذر تحديث حالة حساب المستخدم (${resp.code()})."))
            }
        }

    suspend fun adminUpdateSettings(
        listingFeeDzd: Long,
        ccpInstructionsAr: String,
        baridimobInstructionsAr: String,
        paymentNoticeAr: String
    ): Result<Boolean> = executeSingleFlight {
        if (!verifyAdminRoleFromServer()) {
            return@executeSingleFlight Result.failure(SecurityException("صلاحيات المشرف مطلوبة."))
        }
        val service = requireConfiguredService()
            ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))
        val resp = service.rpcAdminUpdateAppSettings(
            AdminUpdateSettingsRpcBody(
                listingFeeDzd = listingFeeDzd,
                ccpInstructionsAr = ccpInstructionsAr,
                baridimobInstructionsAr = baridimobInstructionsAr,
                paymentNoticeAr = paymentNoticeAr
            )
        )
        if (resp.isSuccessful && resp.body() == true) {
            refreshAdminDashboardData()
            Result.success(true)
        } else {
            Result.failure(IllegalStateException("تعذر حفظ إعدادات التطبيق (${resp.code()})."))
        }
    }

    suspend fun adminModerateListing(listingId: String, newStatus: String, reason: String): Result<Boolean> =
        executeSingleFlight {
            if (!verifyAdminRoleFromServer()) {
                return@executeSingleFlight Result.failure(SecurityException("صلاحيات المشرف مطلوبة."))
            }
            val service = requireConfiguredService()
                ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))
            val resp = service.rpcAdminModerateListing(AdminModerateListingRpcBody(listingId, newStatus, reason))
            if (resp.isSuccessful && resp.body() == true) {
                refreshAdminDashboardData()
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("تعذر تحديث حالة الإعلان (${resp.code()})."))
            }
        }

    suspend fun adminAddCategory(slug: String, nameAr: String, nameFr: String): Result<Boolean> =
        executeSingleFlight {
            if (!verifyAdminRoleFromServer()) {
                return@executeSingleFlight Result.failure(SecurityException("صلاحيات المشرف مطلوبة."))
            }
            val service = requireConfiguredService()
                ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))
            val resp = service.createCategory(
                mapOf(
                    "slug" to slug.trim().lowercase(),
                    "name_ar" to nameAr.trim(),
                    "name_fr" to nameFr.trim(),
                    "is_active" to true
                )
            )
            if (resp.isSuccessful) {
                service.getCategories().body()?.let { _categories.value = it }
                Result.success(true)
            } else {
                Result.failure(IllegalStateException("تعذر إضافة الفئة الجديدة (${resp.code()})."))
            }
        }

    fun computeAdminFinancialSummary(): AdminFinancialSummary {
        val all = _adminAllPayments.value
        val pending = all.count { it.status == "pending" }
        val approved = all.count { it.status == "approved" }
        val consumed = all.count { it.status == "consumed" }
        val rejected = all.count { it.status == "rejected" }
        val totalSubmitted = all.sumOf { it.amountDzd }
        val totalApprovedAndConsumed = all.filter { it.status == "approved" || it.status == "consumed" }.sumOf { it.amountDzd }
        return AdminFinancialSummary(
            pendingCount = pending,
            approvedUnusedCount = approved,
            consumedCount = consumed,
            rejectedCount = rejected,
            totalSubmittedDzd = totalSubmitted,
            totalApprovedAndConsumedDzd = totalApprovedAndConsumed
        )
    }

    suspend fun signOut() {
        try {
            requireConfiguredService()?.signOut()
        } catch (_: Exception) {
        } finally {
            SupabaseClientProvider.currentAccessToken = null
            _isVerifiedAdmin.value = false
            clearAdminSensitiveState()
        }
    }

    private fun clearAdminSensitiveState() {
        _adminPendingPayments.value = emptyList()
        _adminAllPayments.value = emptyList()
        _adminUsers.value = emptyList()
        _adminAllListings.value = emptyList()
        _adminReports.value = emptyList()
        _adminAuditLogs.value = emptyList()
    }

    private suspend fun <T> executeSingleFlight(block: suspend () -> Result<T>): Result<T> {
        if (actionInFlight) {
            return Result.failure(IllegalStateException("جاري تنفيذ العملية الحالية، يرجى الانتظار لمنع تكرار الطلبات."))
        }
        actionInFlight = true
        return try {
            block()
        } catch (e: IOException) {
            Result.failure(IOException("انقطع الاتصال بالشبكة. يرجى التحقق من الإنترنت وإعادة المحاولة."))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            actionInFlight = false
        }
    }
}
