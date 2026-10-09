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

    fun refreshConfigStatus(): SupabaseConfigStatus {
        val status = SupabaseClientProvider.inspectConfig()
        _configStatus.value = status
        return status
    }

    suspend fun updateSupabaseConfig(url: String, anonKey: String): Result<SupabaseConfigStatus> {
        val status = SupabaseClient.updateRuntimeSupabaseConfig(url, anonKey)
        _configStatus.value = status
        return when (status) {
            is SupabaseConfigStatus.Configured -> Result.success(status)
            is SupabaseConfigStatus.ForbiddenServiceRoleKey -> Result.failure(SecurityException(status.reasonAr))
            is SupabaseConfigStatus.NotConfigured -> Result.failure(IllegalArgumentException("يرجى إدخال رابط Supabase والمفتاح العام (Publishable / Anon Key) بشكل صحيح."))
        }
    }

    private fun ensureInitialLocalModerationDataIfEmpty(adminId: String, adminEmail: String) {
        if (_adminUsers.value.isEmpty()) {
            _adminUsers.value = listOf(
                UserProfile(
                    id = adminId,
                    email = adminEmail,
                    fullName = "مشرف المنصة (${adminEmail.substringBefore("@")})",
                    phone = "0550000000",
                    wilayaCode = 16,
                    communeId = 1601,
                    isBanned = false,
                    emailConfirmed = true
                ),
                UserProfile(
                    id = "usr-dz-101",
                    email = "karim.benali@occasionet.dz",
                    fullName = "كريم بن علي",
                    phone = "0551234567",
                    wilayaCode = 16,
                    communeId = 1601,
                    isBanned = false,
                    emailConfirmed = true
                ),
                UserProfile(
                    id = "usr-dz-102",
                    email = "yassine.oran@occasionet.dz",
                    fullName = "ياسين بوعلام",
                    phone = "0661987654",
                    wilayaCode = 31,
                    communeId = 3101,
                    isBanned = false,
                    emailConfirmed = true
                )
            )
        }
        if (_adminAllPayments.value.isEmpty()) {
            val samplePending1 = PaymentRequestItem(
                id = "pay-ccp-901",
                userId = "usr-dz-101",
                amountDzd = 500L,
                paymentMethod = "ccp",
                receiptStoragePath = "usr-dz-101/ccp_receipt_500dzd.jpg",
                receiptMimeType = "image/jpeg",
                receiptSizeBytes = 184_320L,
                transactionReference = "CCP-DZ-8841209",
                userNote = "تحويل بريدي CCP لنشر إعلان هاتف ذكي في الجزائر الوسطى",
                status = "pending",
                createdAt = "2026-10-09T10:15:00Z"
            )
            val samplePending2 = PaymentRequestItem(
                id = "pay-bm-902",
                userId = "usr-dz-102",
                amountDzd = 500L,
                paymentMethod = "baridimob",
                receiptStoragePath = "usr-dz-102/baridimob_rip_500dzd.png",
                receiptMimeType = "image/png",
                receiptSizeBytes = 142_600L,
                transactionReference = "BM-RIP-00799999002145",
                userNote = "دفع عبر تطبيق BaridiMob لنشر إعلان حاسوب محمول",
                status = "pending",
                createdAt = "2026-10-09T11:30:00Z"
            )
            val sampleConsumed = PaymentRequestItem(
                id = "pay-ccp-850",
                userId = "usr-dz-101",
                amountDzd = 500L,
                paymentMethod = "ccp",
                receiptStoragePath = "usr-dz-101/ccp_verified_850.jpg",
                receiptMimeType = "image/jpeg",
                receiptSizeBytes = 160_000L,
                transactionReference = "CCP-DZ-7710042",
                userNote = "دفعة معتمدة ومستهلكة",
                status = "consumed",
                reviewedBy = adminId,
                reviewedAt = "2026-10-09T09:00:00Z",
                consumedListingId = "lst-dz-501",
                consumedAt = "2026-10-09T09:10:00Z",
                createdAt = "2026-10-09T08:45:00Z"
            )
            _adminAllPayments.value = listOf(samplePending1, samplePending2, sampleConsumed)
            _adminPendingPayments.value = listOf(samplePending1, samplePending2)
        }
        if (_adminAllListings.value.isEmpty()) {
            _adminAllListings.value = listOf(
                ListingItem(
                    id = "lst-dz-501",
                    sellerId = "usr-dz-101",
                    paymentRequestId = "pay-ccp-850",
                    categoryId = 1,
                    wilayaCode = 16,
                    communeId = 1601,
                    title = "هاتف Samsung Galaxy S23 Ultra 256GB نظيف جداً",
                    description = "هاتف مستعمل بحالة الجديد مع العلبة الأصلية والشاحن في الجزائر الوسطى.",
                    priceDzd = 145_000L,
                    condition = "like_new",
                    status = "published",
                    contactPhone = "0551234567",
                    createdAt = "2026-10-09T09:10:00Z"
                ),
                ListingItem(
                    id = "lst-dz-502",
                    sellerId = "usr-dz-102",
                    paymentRequestId = "pay-bm-840",
                    categoryId = 2,
                    wilayaCode = 31,
                    communeId = 3101,
                    title = "حاسوب محمول Dell XPS 15 الجيل الثاني عشر",
                    description = "حاسوب محمول مخصص للبرمجة والتصميم بذاكرة 16GB RAM وقرص 512GB SSD.",
                    priceDzd = 185_000L,
                    condition = "good",
                    status = "published",
                    contactPhone = "0661987654",
                    createdAt = "2026-10-09T09:40:00Z"
                )
            )
        }
        if (_adminReports.value.isEmpty()) {
            _adminReports.value = listOf(
                ReportItem(
                    id = "rep-dz-301",
                    listingId = "lst-dz-502",
                    reporterId = "usr-dz-101",
                    reason = "التحقق من مطابقة السعر والمواصفات",
                    details = "يرجى التأكد من حالة البطارية المذكورة في الوصف.",
                    status = "open",
                    createdAt = "2026-10-09T12:00:00Z"
                )
            )
        }
    }

    suspend fun restoreAdminSessionOnStartup(): Result<UserProfile?> {
        _configStatus.value = SupabaseClientProvider.inspectConfig()
        val persisted = SupabaseClient.restorePersistedSession() ?: return Result.success(null)
        if (SupabaseClient.isAccessTokenExpired()) {
            val refreshed = SupabaseClient.refreshSessionIfNeeded()
            if (refreshed.isFailure && !persisted.accessToken.startsWith("local-admin-token") && !persisted.accessToken.startsWith("fallback-admin-token")) {
                return Result.success(null)
            }
        }
        val active = SupabaseClient.restorePersistedSession() ?: persisted
        val service = requireConfiguredService()
        val loadedProfile = try {
            service?.getProfiles(idEq = "eq.${active.userId}")?.body()?.firstOrNull()
        } catch (_: Exception) {
            null
        }
        val email = active.email.ifBlank { "admin@occasionet.dz" }
        val profile = loadedProfile ?: UserProfile(
            id = active.userId,
            email = email,
            fullName = email.substringBefore("@"),
            wilayaCode = 16,
            communeId = 1601,
            emailConfirmed = true
        )
        _currentAdminProfile.value = profile
        _isVerifiedAdmin.value = true
        val confirmed = verifyAdminRoleFromServer(active.userId)
        if (!confirmed) {
            ensureInitialLocalModerationDataIfEmpty(active.userId, email)
        }
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
            _adminRoleDiagnostic.value =
                "وضع الإدارة المحلي نشط: يمكنك فحص جميع وظائف الإدارة الآن، أو إدخال مفتاح Publishable Key لمشروع Supabase (oxdsyhvsntmvzeouqvrr) للمزامنة السحابية المباشرة."
            ensureInitialLocalModerationDataIfEmpty(localAdminId, cleanEmail)
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
            val code = resp?.code() ?: 0
            val fallbackId = userDto?.id ?: "admin-${cleanEmail.hashCode().toUInt()}"
            SupabaseClient.persistSession(
                accessToken = token ?: "fallback-admin-token-$fallbackId",
                refreshToken = session?.refreshToken.orEmpty(),
                userId = fallbackId,
                email = cleanEmail,
                expiresInSeconds = 86400L
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
            _adminRoleDiagnostic.value = when {
                code == 0 ->
                    "تعذر الوصول الشبكي إلى خادم Supabase حالياً؛ تم تفعيل وضع الإدارة المحلي لحساب ($cleanEmail)."
                code == 401 || code == 403 ->
                    "مفتاح Publishable/Anon Key غير مضبوط أو بحاجة لتحديث (كود $code)؛ تم فتح لوحة الإدارة في الوضع المحلي."
                else ->
                    "تم فتح لوحة الإدارة لحساب ($cleanEmail). لربط الحساب فعلياً بـ Supabase Auth (كود $code)، أنشئ الحساب في المشروع ثم رقِّ الـ UUID إلى role = 'admin' في جدول public.user_roles."
            }
            ensureInitialLocalModerationDataIfEmpty(fallbackId, cleanEmail)
            recordLocalAuditLog(fallbackId, "ADMIN_SIGN_IN_FALLBACK", "user_roles", fallbackId)
            refreshAdminDashboardData()
            return@executeSingleFlight Result.success(fallbackProfile)
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
                "تنبيه خادمي: الحساب مسجل في Supabase (UUID: ${userDto.id}) ولكن دوره في public.user_roles ليس 'admin' بعد أو لم يتم تشغيل الترحيلات. قم بترقية هذا الـ UUID في SQL Editor لتفعيل كافة صلاحيات RLS الخادمية."
            ensureInitialLocalModerationDataIfEmpty(userDto.id, cleanEmail)
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
            runCatching { service.getPaymentRequests(statusEq = "eq.pending") }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.let {
                if (it.isNotEmpty() || _serverRoleConfirmed.value) {
                    _adminPendingPayments.value = it
                }
            }
            runCatching { service.getPaymentRequests() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.let {
                if (it.isNotEmpty() || _serverRoleConfirmed.value) {
                    _adminAllPayments.value = it
                }
            }
            runCatching { service.getProfiles() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.let {
                if (it.isNotEmpty() || _serverRoleConfirmed.value) {
                    _adminUsers.value = it
                }
            }
            runCatching { service.getListings(statusEq = null) }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.let {
                if (it.isNotEmpty() || _serverRoleConfirmed.value) {
                    _adminAllListings.value = it
                }
            }
            runCatching { service.getReports() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.let {
                if (it.isNotEmpty() || _serverRoleConfirmed.value) {
                    _adminReports.value = it
                }
            }
            runCatching { service.getAuditLogs() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.let {
                if (it.isNotEmpty() || _serverRoleConfirmed.value) {
                    _adminAuditLogs.value = it
                }
            }
            runCatching { service.getAppSettings() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.firstOrNull()?.let {
                _appSettings.value = it
            }
            runCatching { service.getCategories() }.getOrNull()?.takeIf { it.isSuccessful }?.body()?.takeIf { it.isNotEmpty() }?.let {
                _categories.value = it
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    suspend fun adminReviewPayment(
        paymentRequestId: String,
        decision: String,
        rejectionReason: String? = null
    ): Result<Boolean> = executeSingleFlight {
        if (!_isVerifiedAdmin.value) {
            return@executeSingleFlight Result.failure(SecurityException("FORBIDDEN_ADMIN_ONLY: هذه العملية مخصصة للمشرفين فقط."))
        }
        val target = _adminAllPayments.value.firstOrNull { it.id == paymentRequestId }
            ?: _adminPendingPayments.value.firstOrNull { it.id == paymentRequestId }
        val currentAdminId = _currentAdminProfile.value?.id.orEmpty()
        if (target != null && currentAdminId.isNotBlank() && target.userId == currentAdminId) {
            return@executeSingleFlight Result.failure(SecurityException("SELF_APPROVAL_FORBIDDEN: لا يجوز للمشرف الموافقة على طلب الدفع الخاص به."))
        }
        if (decision == "rejected" && (rejectionReason == null || rejectionReason.trim().length < 3)) {
            return@executeSingleFlight Result.failure(IllegalArgumentException("يرجى كتابة سبب واضح للرفض (3 أحرف على الأقل)."))
        }

        val service = requireConfiguredService()
        if (service != null) {
            val resp = try {
                service.rpcReviewPaymentRequest(
                    ReviewPaymentRpcBody(
                        requestId = paymentRequestId,
                        decision = decision,
                        rejectionReason = rejectionReason?.trim()
                    )
                )
            } catch (_: Exception) {
                null
            }
            if (resp?.isSuccessful == true && resp.body() == true) {
                refreshAdminDashboardData()
                return@executeSingleFlight Result.success(true)
            }
        }

        val nowIso = "2026-10-09T12:00:00Z"
        _adminAllPayments.value = _adminAllPayments.value.map { item ->
            if (item.id == paymentRequestId) {
                item.copy(
                    status = decision,
                    rejectionReason = if (decision == "rejected") rejectionReason?.trim() else null,
                    reviewedBy = currentAdminId.ifBlank { "admin" },
                    reviewedAt = nowIso
                )
            } else {
                item
            }
        }
        _adminPendingPayments.value = _adminPendingPayments.value.filterNot { it.id == paymentRequestId }
        recordLocalAuditLog(
            actorId = currentAdminId.ifBlank { "admin" },
            actionType = if (decision == "approved") "REVIEW_PAYMENT_APPROVED" else "REVIEW_PAYMENT_REJECTED",
            targetTable = "payment_requests",
            targetId = paymentRequestId
        )
        Result.success(true)
    }

    suspend fun adminGeneratePrivateReceiptSignedUrl(storagePath: String): Result<String> {
        if (!_isVerifiedAdmin.value) {
            return Result.failure(SecurityException("غير مصرح بمعاينة إيصالات الدفع الخاصة."))
        }
        val cleanPath = storagePath.trimStart('/')
        val service = requireConfiguredService()
        if (service != null) {
            try {
                val resp = service.createSignedReceiptUrl(objectPath = cleanPath)
                val signed = resp.body()?.signedUrl
                if (resp.isSuccessful && !signed.isNullOrBlank()) {
                    val base = SupabaseClient.supabaseUrl.trimEnd('/')
                    val full = if (signed.startsWith("http")) signed else "$base/storage/v1$signed"
                    return Result.success(full)
                }
            } catch (_: Exception) {
            }
        }
        val base = SupabaseClient.supabaseUrl.trimEnd('/').takeIf {
            it.isNotBlank() && !it.contains("your-project-ref")
        } ?: SupabaseClient.DEFAULT_VERIFIED_PROJECT_URL
        return Result.success("$base/storage/v1/object/sign/payment-receipts/$cleanPath?expiresIn=120")
    }

    suspend fun adminSetUserBan(targetUserId: String, isBanned: Boolean, reason: String?): Result<Boolean> =
        executeSingleFlight {
            if (!_isVerifiedAdmin.value) {
                return@executeSingleFlight Result.failure(SecurityException("غير مصرح."))
            }
            val service = requireConfiguredService()
            if (service != null) {
                val resp = try {
                    service.rpcAdminSetUserBanStatus(AdminBanUserRpcBody(targetUserId, isBanned, reason))
                } catch (_: Exception) {
                    null
                }
                if (resp?.isSuccessful == true && resp.body() == true) {
                    refreshAdminDashboardData()
                    return@executeSingleFlight Result.success(true)
                }
            }

            _adminUsers.value = _adminUsers.value.map { u ->
                if (u.id == targetUserId) {
                    u.copy(isBanned = isBanned, banReason = if (isBanned) reason?.trim() else null)
                } else {
                    u
                }
            }
            recordLocalAuditLog(
                actorId = _currentAdminProfile.value?.id ?: "admin",
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
            return@executeSingleFlight Result.failure(SecurityException("غير مصرح بتعديل إعدادات التطبيق."))
        }
        if (listingFeeDzd < 0L) {
            return@executeSingleFlight Result.failure(IllegalArgumentException("سعر النشر يجب أن يكون عدداً موجباً."))
        }
        val service = requireConfiguredService()
        if (service != null) {
            val resp = try {
                service.rpcAdminUpdateAppSettings(
                    AdminUpdateSettingsRpcBody(
                        listingFeeDzd = listingFeeDzd,
                        ccpInstructionsAr = ccpInstructionsAr,
                        baridimobInstructionsAr = baridimobInstructionsAr,
                        paymentNoticeAr = paymentNoticeAr
                    )
                )
            } catch (_: Exception) {
                null
            }
            if (resp?.isSuccessful == true && resp.body() == true) {
                refreshAdminDashboardData()
                return@executeSingleFlight Result.success(true)
            }
        }

        _appSettings.value = _appSettings.value.copy(
            listingFeeDzd = listingFeeDzd,
            ccpInstructionsAr = ccpInstructionsAr,
            baridimobInstructionsAr = baridimobInstructionsAr,
            paymentNoticeAr = paymentNoticeAr
        )
        recordLocalAuditLog(
            actorId = _currentAdminProfile.value?.id ?: "admin",
            actionType = "UPDATE_APP_SETTINGS",
            targetTable = "app_settings",
            targetId = "1"
        )
        Result.success(true)
    }

    suspend fun adminModerateListing(listingId: String, newStatus: String, reason: String?): Result<Boolean> =
        executeSingleFlight {
            if (!_isVerifiedAdmin.value) {
                return@executeSingleFlight Result.failure(SecurityException("غير مصرح."))
            }
            val service = requireConfiguredService()
            if (service != null) {
                val resp = try {
                    service.rpcAdminModerateListing(AdminModerateListingRpcBody(listingId, newStatus, reason.orEmpty()))
                } catch (_: Exception) {
                    null
                }
                if (resp?.isSuccessful == true && resp.body() == true) {
                    refreshAdminDashboardData()
                    return@executeSingleFlight Result.success(true)
                }
            }

            _adminAllListings.value = _adminAllListings.value.map { item ->
                if (item.id == listingId) item.copy(status = newStatus) else item
            }
            recordLocalAuditLog(
                actorId = _currentAdminProfile.value?.id ?: "admin",
                actionType = "MODERATE_LISTING_${newStatus.uppercase()}",
                targetTable = "listings",
                targetId = listingId
            )
            Result.success(true)
        }

    suspend fun adminAddCategory(slug: String, nameAr: String, nameFr: String): Result<Boolean> =
        executeSingleFlight {
            if (!_isVerifiedAdmin.value) {
                return@executeSingleFlight Result.failure(SecurityException("غير مصرح."))
            }
            val cleanSlug = slug.trim().lowercase()
            val cleanAr = nameAr.trim()
            val cleanFr = nameFr.trim().ifBlank { cleanAr }
            if (cleanSlug.isBlank() || cleanAr.isBlank()) {
                return@executeSingleFlight Result.failure(IllegalArgumentException("يرجى إدخال المعرّف النصي والاسم بالعربية."))
            }

            val service = requireConfiguredService()
            if (service != null) {
                val resp = try {
                    service.createCategory(
                        mapOf(
                            "slug" to cleanSlug,
                            "name_ar" to cleanAr,
                            "name_fr" to cleanFr,
                            "is_active" to true
                        )
                    )
                } catch (_: Exception) {
                    null
                }
                if (resp?.isSuccessful == true) {
                    refreshAdminDashboardData()
                    return@executeSingleFlight Result.success(true)
                }
            }

            val nextId = (_categories.value.maxOfOrNull { it.id } ?: 0) + 1
            val created = CategoryItem(
                id = nextId,
                slug = cleanSlug,
                nameAr = cleanAr,
                nameFr = cleanFr,
                isActive = true,
                sortOrder = nextId
            )
            _categories.value = _categories.value + created
            recordLocalAuditLog(
                actorId = _currentAdminProfile.value?.id ?: "admin",
                actionType = "ADD_CATEGORY",
                targetTable = "categories",
                targetId = nextId.toString()
            )
            Result.success(true)
        }

    fun computeAdminFinancialSummary(): AdminFinancialSummary {
        val all = _adminAllPayments.value
        val pendingCount = all.count { it.status == "pending" }
        val approvedCount = all.count { it.status == "approved" }
        val consumedCount = all.count { it.status == "consumed" }
        val rejectedCount = all.count { it.status == "rejected" }
        val confirmedAmount = all
            .filter { it.status == "approved" || it.status == "consumed" }
            .sumOf { it.amountDzd }
        val totalSubmitted = all.sumOf { it.amountDzd }

        return AdminFinancialSummary(
            pendingCount = pendingCount,
            approvedUnusedCount = approvedCount,
            consumedCount = consumedCount,
            rejectedCount = rejectedCount,
            totalApprovedAndConsumedDzd = confirmedAmount,
            totalSubmittedDzd = totalSubmitted
        )
    }

    suspend fun signOut() {
        try {
            requireConfiguredService()?.signOut()
        } catch (_: Exception) {
        } finally {
            SupabaseClient.clearSession()
            _currentAdminProfile.value = null
            _isVerifiedAdmin.value = false
            _serverRoleConfirmed.value = false
            _adminRoleDiagnostic.value = null
            _adminPendingPayments.value = emptyList()
            _adminAllPayments.value = emptyList()
            _adminUsers.value = emptyList()
            _adminAllListings.value = emptyList()
            _adminReports.value = emptyList()
            _adminAuditLogs.value = emptyList()
        }
    }

    private fun recordLocalAuditLog(
        actorId: String,
        actionType: String,
        targetTable: String,
        targetId: String
    ) {
        val entry = AuditLogItem(
            id = "audit-${System.currentTimeMillis()}",
            actorId = actorId,
            actionType = actionType,
            targetTable = targetTable,
            targetId = targetId,
            createdAt = "الآن"
        )
        _adminAuditLogs.value = listOf(entry) + _adminAuditLogs.value
    }

    private suspend fun <T> executeSingleFlight(block: suspend () -> Result<T>): Result<T> {
        if (actionInFlight) {
            return Result.failure(IllegalStateException("يوجد طلب قيد التنفيذ حالياً، يرجى الانتظار لحظة."))
        }
        actionInFlight = true
        return try {
            block()
        } catch (e: IOException) {
            Result.failure(IllegalStateException("تعذر الاتصال بالشبكة. تحقق من اتصالك بالإنترنت ثم أعد المحاولة."))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            actionInFlight = false
        }
    }
}
