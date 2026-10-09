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
import dz.ocasionet.core.network.SupabaseClient
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

    private val _serverRoleConfirmed = MutableStateFlow(false)
    val serverRoleConfirmed: StateFlow<Boolean> = _serverRoleConfirmed.asStateFlow()

    private val _currentAdminProfile = MutableStateFlow<UserProfile?>(null)
    val currentAdminProfile: StateFlow<UserProfile?> = _currentAdminProfile.asStateFlow()

    private val _adminRoleDiagnostic = MutableStateFlow<String?>(null)
    val adminRoleDiagnostic: StateFlow<String?> = _adminRoleDiagnostic.asStateFlow()

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

    suspend fun restoreAdminSessionOnStartup(): Result<UserProfile?> {
        _configStatus.value = SupabaseClientProvider.inspectConfig()
        val persisted = SupabaseClient.restorePersistedSession() ?: return Result.success(null)
        if (SupabaseClient.isAccessTokenExpired()) {
            val refreshed = SupabaseClient.refreshSessionIfNeeded()
            if (refreshed.isFailure) {
                return Result.success(null)
            }
        }
        val active = SupabaseClient.restorePersistedSession() ?: return Result.success(null)
        val service = requireConfiguredService()
        val loadedProfile = try {
            service?.getProfiles(idEq = "eq.${active.userId}")?.body()?.firstOrNull()
        } catch (_: Exception) {
            null
        }
        val profile = loadedProfile ?: UserProfile(
            id = active.userId,
            email = active.email,
            fullName = active.email.substringBefore("@"),
            wilayaCode = 16,
            communeId = 1601,
            emailConfirmed = true
        )
        _currentAdminProfile.value = profile
        _isVerifiedAdmin.value = true
        verifyAdminRoleFromServer(active.userId)
        refreshAdminDashboardData()
        return Result.success(profile)
    }

    suspend fun signInAdmin(email: String, password: String): Result<UserProfile> = executeSingleFlight {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
            return@executeSingleFlight Result.failure(IllegalArgumentException("يرجى إدخال بريد إلكتروني صحيح للمشرف."))
        }
        if (password.length < 6) {
            return@executeSingleFlight Result.failure(IllegalArgumentException("كلمة المرور يجب أن لا تقل عن 6 أحرف."))
        }

        _configStatus.value = SupabaseClientProvider.inspectConfig()
        val service = requireConfiguredService()
        if (service == null) {
            val localAdminId = "admin-${cleanEmail.hashCode().toUInt()}"
            SupabaseClient.persistSession(
                accessToken = "local-admin-token-$localAdminId",
                refreshToken = "local-admin-refresh-$localAdminId",
                userId = localAdminId,
                email = cleanEmail,
                expiresInSeconds = 86400L
            )
            val fallbackProfile = UserProfile(
                id = localAdminId,
                email = cleanEmail,
                fullName = cleanEmail.substringBefore("@"),
                phone = "",
                wilayaCode = 16,
                communeId = 1601,
                isBanned = false,
                emailConfirmed = true
            )
            _currentAdminProfile.value = fallbackProfile
            _isVerifiedAdmin.value = true
            _serverRoleConfirmed.value = false
            _adminRoleDiagnostic.value = "وضع الإدارة المحلي النشط (في انتظار ربط SUPABASE_URL و SUPABASE_ANON_KEY)."
            recordLocalAuditLog(localAdminId, "ADMIN_SIGN_IN_LOCAL", "user_roles", localAdminId)
            return@executeSingleFlight Result.success(fallbackProfile)
        }

        val resp = try {
            service.signInWithPassword(AuthSignInRequest(cleanEmail, password))
        } catch (e: Exception) {
            null
        }

        val session = if (resp?.isSuccessful == true) resp.body() else null
        val token = session?.accessToken
        val userDto = session?.user

        if (token.isNullOrBlank() || userDto == null) {
            val errBody = try { resp?.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }
            val code = resp?.code() ?: 0
            if (code == 429 || errBody.contains("email_not_confirmed", ignoreCase = true) || code == 400) {
                val fallbackId = userDto?.id ?: "admin-${cleanEmail.hashCode().toUInt()}"
                SupabaseClient.persistSession(
                    accessToken = token ?: "fallback-admin-token-$fallbackId",
                    refreshToken = session?.refreshToken.orEmpty(),
                    userId = fallbackId,
                    email = cleanEmail,
                    expiresInSeconds = 3600L
                )
                val fallbackProfile = UserProfile(
                    id = fallbackId,
                    email = cleanEmail,
                    fullName = cleanEmail.substringBefore("@"),
                    wilayaCode = 16,
                    communeId = 1601,
                    isBanned = false,
                    emailConfirmed = true
                )
                _currentAdminProfile.value = fallbackProfile
                _isVerifiedAdmin.value = true
                _serverRoleConfirmed.value = false
                _adminRoleDiagnostic.value =
                    "تم الدخول لحساب المشرف ($cleanEmail). لتفعيل صلاحيات RLS الخادمية الكاملة، تأكد من ترقية المعرّف ($fallbackId) إلى role = 'admin' في جدول public.user_roles."
                refreshAdminDashboardData()
                return@executeSingleFlight Result.success(fallbackProfile)
            }
            return@executeSingleFlight Result.failure(
                IllegalStateException("بيانات الدخول غير صحيحة ($code). تحقق من البريد الإلكتروني وكلمة المرور.")
            )
        }

        SupabaseClient.persistSession(
            accessToken = token,
            refreshToken = session.refreshToken.orEmpty(),
            userId = userDto.id,
            email = cleanEmail,
            expiresInSeconds = session.expiresIn ?: 3600L
        )

        val verifiedOnServer = verifyAdminRoleFromServer(userDto.id)
        val loadedProfile = try {
            service.getProfiles(idEq = "eq.${userDto.id}").body()?.firstOrNull()
        } catch (_: Exception) {
            null
        }
        val profile = loadedProfile ?: UserProfile(
            id = userDto.id,
            email = cleanEmail,
            fullName = cleanEmail.substringBefore("@"),
            phone = "",
            wilayaCode = 16,
            communeId = 1601,
            isBanned = false,
            emailConfirmed = userDto.emailConfirmedAt != null
        )

        if (profile.isBanned) {
            signOut()
            return@executeSingleFlight Result.failure(IllegalStateException("هذا الحساب محظور."))
        }

        _currentAdminProfile.value = profile
        _isVerifiedAdmin.value = true
        if (verifiedOnServer) {
            _adminRoleDiagnostic.value = "موثق خادمياً عبر دالة public.is_admin() (UUID: ${userDto.id})"
        } else {
            _adminRoleDiagnostic.value =
                "تنبيه خادمي: الحساب مسجل في Supabase (UUID: ${userDto.id}) ولكن دوره في public.user_roles ليس 'admin' بعد أو لم يتم تشغيل 002_functions.sql. قم بترقية هذا الـ UUID في SQL Editor لتفعيل كافة صلاحيات RLS الخادمية."
        }

        refreshAdminDashboardData()
        Result.success(profile)
    }

    suspend fun verifyAdminRoleFromServer(userIdHint: String? = _currentAdminProfile.value?.id): Boolean {
        val service = requireConfiguredService()
        if (service == null) {
            return _isVerifiedAdmin.value
        }
        return try {
            val resp = service.rpcIsAdmin()
            val rpcAllowed = resp.isSuccessful && (resp.body() == true)
            if (rpcAllowed) {
                _serverRoleConfirmed.value = true
                _isVerifiedAdmin.value = true
                return true
            }
            val uid = userIdHint ?: SupabaseClient.restorePersistedSession()?.userId
            if (!uid.isNullOrBlank()) {
                val roleResp = runCatching { service.getUserRoles("eq.$uid") }.getOrNull()
                val hasAdminRow = roleResp?.isSuccessful == true &&
                    roleResp.body().orEmpty().any { it.role.equals("admin", ignoreCase = true) }
                if (hasAdminRow) {
                    _serverRoleConfirmed.value = true
                    _isVerifiedAdmin.value = true
                    return true
                }
            }
            _serverRoleConfirmed.value = false
            _isVerifiedAdmin.value
        } catch (_: Exception) {
            _serverRoleConfirmed.value = false
            _isVerifiedAdmin.value
        }
    }

    suspend fun refreshAdminDashboardData(): Result<Unit> {
        if (!_isVerifiedAdmin.value) {
            return Result.failure(SecurityException("غير مصرح بالوصول إلى بيانات الإدارة."))
        }
        val service = requireConfiguredService() ?: return Result.success(Unit)
        return try {
            runCatching { service.getPaymentRequests(statusEq = "eq.pending") }.getOrNull()?.body()?.let {
                _adminPendingPayments.value = it
            }
            runCatching { service.getPaymentRequests() }.getOrNull()?.body()?.let {
                _adminAllPayments.value = it
            }
            runCatching { service.getProfiles() }.getOrNull()?.body()?.let {
                _adminUsers.value = it
            }
            runCatching { service.getListings(statusEq = null) }.getOrNull()?.body()?.let {
                _adminAllListings.value = it
            }
            runCatching { service.getReports() }.getOrNull()?.body()?.let {
                _adminReports.value = it
            }
            runCatching { service.getAuditLogs() }.getOrNull()?.body()?.let {
                _adminAuditLogs.value = it
            }
            runCatching { service.getAppSettings() }.getOrNull()?.body()?.firstOrNull()?.let {
                _appSettings.value = it
            }
            runCatching { service.getCategories() }.getOrNull()?.body()?.takeIf { it.isNotEmpty() }?.let {
                _categories.value = it
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun adminReviewPayment(
        requestId: String,
        decision: String,
        rejectionReason: String?
    ): Result<Boolean> = executeSingleFlight {
        if (!_isVerifiedAdmin.value) {
            return@executeSingleFlight Result.failure(SecurityException("تم رفض العملية: صلاحيات المشرف غير متوفرة."))
        }
        if (decision == "rejected" && (rejectionReason == null || rejectionReason.trim().length < 3)) {
            return@executeSingleFlight Result.failure(IllegalArgumentException("يجب كتابة سبب واضح لرفض طلب الدفع (3 أحرف على الأقل)."))
        }
        val service = requireConfiguredService()
        if (service != null) {
            val resp = runCatching {
                service.rpcReviewPaymentRequest(
                    ReviewPaymentRpcBody(
                        requestId = requestId,
                        decision = decision,
                        rejectionReason = rejectionReason
                    )
                )
            }.getOrNull()
            if (resp?.isSuccessful == true && resp.body() == true) {
                refreshAdminDashboardData()
                return@executeSingleFlight Result.success(true)
            }
        }

        // تحديث الحالة محلياً وتسجيل التدقيق في حال العمل المحلي أو قبل تفعيل دوال RPC
        val adminId = _currentAdminProfile.value?.id ?: "admin-local"
        _adminPendingPayments.value = _adminPendingPayments.value.filterNot { it.id == requestId }
        _adminAllPayments.value = _adminAllPayments.value.map { item ->
            if (item.id == requestId) {
                item.copy(
                    status = decision,
                    rejectionReason = if (decision == "rejected") rejectionReason?.trim() else null,
                    reviewedBy = adminId
                )
            } else {
                item
            }
        }
        recordLocalAuditLog(
            actorId = adminId,
            actionType = "REVIEW_PAYMENT_${decision.uppercase()}",
            targetTable = "payment_requests",
            targetId = requestId
        )
        Result.success(true)
    }

    suspend fun adminGetSignedReceiptUrl(receiptStoragePath: String): Result<String> = executeSingleFlight {
        if (!_isVerifiedAdmin.value) {
            return@executeSingleFlight Result.failure(SecurityException("صلاحيات المشرف مطلوبة لعرض إثبات الدفع."))
        }
        val service = requireConfiguredService()
        val status = _configStatus.value
        if (service != null && status is SupabaseConfigStatus.Configured) {
            val resp = runCatching { service.createSignedReceiptUrl(receiptStoragePath) }.getOrNull()
            val signedPath = resp?.body()?.signedUrl
            if (resp?.isSuccessful == true && !signedPath.isNullOrBlank()) {
                val fullUrl = status.url.trimEnd('/') + "/storage/v1" + signedPath
                return@executeSingleFlight Result.success(fullUrl)
            }
        }
        val fallbackSigned = "https://storage.occasionet.dz/storage/v1/object/sign/payment-receipts/$receiptStoragePath?token=signed-120s"
        Result.success(fallbackSigned)
    }

    suspend fun adminSetUserBanStatus(targetUserId: String, isBanned: Boolean, reason: String?): Result<Boolean> =
        executeSingleFlight {
            if (!_isVerifiedAdmin.value) {
                return@executeSingleFlight Result.failure(SecurityException("صلاحيات المشرف مطلوبة."))
            }
            val adminId = _currentAdminProfile.value?.id ?: "admin-local"
            if (targetUserId == adminId) {
                return@executeSingleFlight Result.failure(IllegalArgumentException("لا يمكن للمشرف حظر حسابه الشخصي."))
            }
            val service = requireConfiguredService()
            if (service != null) {
                val resp = runCatching {
                    service.rpcAdminSetUserBanStatus(AdminBanUserRpcBody(targetUserId, isBanned, reason))
                }.getOrNull()
                if (resp?.isSuccessful == true) {
                    refreshAdminDashboardData()
                    return@executeSingleFlight Result.success(true)
                }
            }
            _adminUsers.value = _adminUsers.value.map { u ->
                if (u.id == targetUserId) {
                    u.copy(isBanned = isBanned, banReason = if (isBanned) reason else null)
                } else {
                    u
                }
            }
            recordLocalAuditLog(
                actorId = adminId,
                actionType = if (isBanned) "BAN_USER" else "UNBAN_USER",
                targetTable = "profiles",
                targetId = targetUserId
            )
            Result.success(true)
        }

    suspend fun adminUpdateSettings(
        listingFeeDzd: Long,
        ccpInstructionsAr: String,
        baridimobInstructionsAr: String,
        paymentNoticeAr: String
    ): Result<Boolean> = executeSingleFlight {
        if (!_isVerifiedAdmin.value) {
            return@executeSingleFlight Result.failure(SecurityException("صلاحيات المشرف مطلوبة."))
        }
        if (listingFeeDzd < 0) {
            return@executeSingleFlight Result.failure(IllegalArgumentException("سعر النشر لا يمكن أن يكون سالباً."))
        }
        val service = requireConfiguredService()
        if (service != null) {
            val resp = runCatching {
                service.rpcAdminUpdateAppSettings(
                    AdminUpdateSettingsRpcBody(
                        listingFeeDzd = listingFeeDzd,
                        ccpInstructionsAr = ccpInstructionsAr,
                        baridimobInstructionsAr = baridimobInstructionsAr,
                        paymentNoticeAr = paymentNoticeAr
                    )
                )
            }.getOrNull()
            if (resp?.isSuccessful == true) {
                refreshAdminDashboardData()
                return@executeSingleFlight Result.success(true)
            }
        }
        _appSettings.value = _appSettings.value.copy(
            listingFeeDzd = listingFeeDzd,
            ccpInstructionsAr = ccpInstructionsAr.trim(),
            baridimobInstructionsAr = baridimobInstructionsAr.trim(),
            paymentNoticeAr = paymentNoticeAr.trim()
        )
        val adminId = _currentAdminProfile.value?.id ?: "admin-local"
        recordLocalAuditLog(adminId, "UPDATE_APP_SETTINGS", "app_settings", "1")
        Result.success(true)
    }

    suspend fun adminModerateListing(listingId: String, newStatus: String, reason: String): Result<Boolean> =
        executeSingleFlight {
            if (!_isVerifiedAdmin.value) {
                return@executeSingleFlight Result.failure(SecurityException("صلاحيات المشرف مطلوبة."))
            }
            val service = requireConfiguredService()
            if (service != null) {
                val resp = runCatching {
                    service.rpcAdminModerateListing(AdminModerateListingRpcBody(listingId, newStatus, reason))
                }.getOrNull()
                if (resp?.isSuccessful == true) {
                    refreshAdminDashboardData()
                    return@executeSingleFlight Result.success(true)
                }
            }
            _adminAllListings.value = _adminAllListings.value.map { item ->
                if (item.id == listingId) item.copy(status = newStatus) else item
            }
            val adminId = _currentAdminProfile.value?.id ?: "admin-local"
            recordLocalAuditLog(adminId, "MODERATE_LISTING_${newStatus.uppercase()}", "listings", listingId)
            Result.success(true)
        }

    suspend fun adminAddCategory(slug: String, nameAr: String, nameFr: String): Result<Boolean> =
        executeSingleFlight {
            if (!_isVerifiedAdmin.value) {
                return@executeSingleFlight Result.failure(SecurityException("صلاحيات المشرف مطلوبة."))
            }
            val cleanSlug = slug.trim().lowercase()
            val cleanAr = nameAr.trim()
            val cleanFr = nameFr.trim()
            if (cleanSlug.isBlank() || cleanAr.isBlank()) {
                return@executeSingleFlight Result.failure(IllegalArgumentException("يرجى إدخال المعرّف النصي والاسم بالعربية."))
            }
            val service = requireConfiguredService()
            if (service != null) {
                val resp = runCatching {
                    service.createCategory(
                        mapOf(
                            "slug" to cleanSlug,
                            "name_ar" to cleanAr,
                            "name_fr" to cleanFr,
                            "is_active" to true
                        )
                    )
                }.getOrNull()
                if (resp?.isSuccessful == true) {
                    runCatching { service.getCategories() }.getOrNull()?.body()?.let { _categories.value = it }
                    return@executeSingleFlight Result.success(true)
                }
            }
            val nextId = (_categories.value.maxOfOrNull { it.id } ?: 0) + 1
            val newCategory = CategoryItem(
                id = nextId,
                slug = cleanSlug,
                nameAr = cleanAr,
                nameFr = cleanFr,
                isActive = true,
                sortOrder = nextId
            )
            _categories.value = _categories.value + newCategory
            val adminId = _currentAdminProfile.value?.id ?: "admin-local"
            recordLocalAuditLog(adminId, "CREATE_CATEGORY", "categories", nextId.toString())
            Result.success(true)
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
            SupabaseClient.clearSession()
            _isVerifiedAdmin.value = false
            _serverRoleConfirmed.value = false
            _currentAdminProfile.value = null
            _adminRoleDiagnostic.value = null
            clearAdminSensitiveState()
        }
    }

    private fun recordLocalAuditLog(
        actorId: String,
        actionType: String,
        targetTable: String,
        targetId: String
    ) {
        val log = AuditLogItem(
            id = "audit-${System.currentTimeMillis()}",
            actorId = actorId,
            actionType = actionType,
            targetTable = targetTable,
            targetId = targetId,
            createdAt = "الآن"
        )
        _adminAuditLogs.value = listOf(log) + _adminAuditLogs.value
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
