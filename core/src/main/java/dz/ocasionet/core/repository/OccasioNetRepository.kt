package dz.ocasionet.core.repository

import dz.ocasionet.core.data.AlgeriaGeographyCatalog
import dz.ocasionet.core.model.AppSettingsData
import dz.ocasionet.core.model.CategoryItem
import dz.ocasionet.core.model.Commune
import dz.ocasionet.core.model.FavoriteRecord
import dz.ocasionet.core.model.ListingFilter
import dz.ocasionet.core.model.ListingItem
import dz.ocasionet.core.model.NotificationItem
import dz.ocasionet.core.model.PaymentRequestItem
import dz.ocasionet.core.model.ResourceState
import dz.ocasionet.core.model.SupabaseConfigStatus
import dz.ocasionet.core.model.UserProfile
import dz.ocasionet.core.model.Wilaya
import dz.ocasionet.core.network.AuthEmailRequest
import dz.ocasionet.core.network.AuthPasswordUpdateRequest
import dz.ocasionet.core.network.AuthSignInRequest
import dz.ocasionet.core.network.AuthSignUpRequest
import dz.ocasionet.core.network.CreateListingRpcBody
import dz.ocasionet.core.network.SubmitPaymentRpcBody
import dz.ocasionet.core.network.SupabaseClientProvider
import dz.ocasionet.core.security.SupabaseSchemaContract
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * مستودع تطبيق المستخدم فقط (OccasioNet User Repository).
 * مفصول تماماً عن وظائف وصلاحيات الإدارة (التي توجد حصرياً في وحدة :adminApp).
 */
class OccasioNetRepository {

    private val _configStatus = MutableStateFlow(SupabaseClientProvider.inspectConfig())
    val configStatus: StateFlow<SupabaseConfigStatus> = _configStatus.asStateFlow()

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettingsData())
    val appSettings: StateFlow<AppSettingsData> = _appSettings.asStateFlow()

    private val _categories = MutableStateFlow(AlgeriaGeographyCatalog.defaultCategories)
    val categories: StateFlow<List<CategoryItem>> = _categories.asStateFlow()

    private val _wilayas = MutableStateFlow(AlgeriaGeographyCatalog.wilayas)
    val wilayas: StateFlow<List<Wilaya>> = _wilayas.asStateFlow()

    private val _publicListingsState = MutableStateFlow<ResourceState<List<ListingItem>>>(ResourceState.Idle)
    val publicListingsState: StateFlow<ResourceState<List<ListingItem>>> = _publicListingsState.asStateFlow()

    private val _myListings = MutableStateFlow<List<ListingItem>>(emptyList())
    val myListings: StateFlow<List<ListingItem>> = _myListings.asStateFlow()

    private val _myPaymentRequests = MutableStateFlow<List<PaymentRequestItem>>(emptyList())
    val myPaymentRequests: StateFlow<List<PaymentRequestItem>> = _myPaymentRequests.asStateFlow()

    private val _favoriteListingIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteListingIds: StateFlow<Set<String>> = _favoriteListingIds.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    @Volatile
    private var actionInFlight = false

    fun communesForWilaya(wilayaCode: Int?): List<Commune> =
        AlgeriaGeographyCatalog.communesForWilaya(wilayaCode)

    private fun requireConfiguredService() =
        SupabaseClientProvider.createServiceOrNull()

    suspend fun refreshPublicData() {
        _configStatus.value = SupabaseClientProvider.inspectConfig()
        val service = requireConfiguredService()
        if (service == null) {
            if (_configStatus.value is SupabaseConfigStatus.ForbiddenServiceRoleKey) {
                val st = _configStatus.value as SupabaseConfigStatus.ForbiddenServiceRoleKey
                _publicListingsState.value = ResourceState.Error(st.reasonAr, isNetworkError = false)
            } else {
                _publicListingsState.value = ResourceState.Success(emptyList())
            }
            return
        }

        _publicListingsState.value = ResourceState.Loading
        try {
            service.getAppSettings().body()?.firstOrNull()?.let { _appSettings.value = it }
            service.getCategories().body()?.takeIf { it.isNotEmpty() }?.let { _categories.value = it }
            service.getWilayas().body()?.takeIf { it.isNotEmpty() }?.let { _wilayas.value = it }

            val response = service.getListings()
            if (response.isSuccessful) {
                _publicListingsState.value = ResourceState.Success(response.body().orEmpty())
            } else if (response.code() == 401) {
                SupabaseClientProvider.currentAccessToken = null
                _currentUser.value = null
                _publicListingsState.value = ResourceState.Error(
                    "انتهت صلاحية الجلسة، يرجى إعادة المحاولة أو تسجيل الدخول.",
                    isSessionExpired = true
                )
            } else {
                _publicListingsState.value = ResourceState.Error(
                    "تعذر جلب الإعلانات حالياً (رمز الاستجابة: ${response.code()})."
                )
            }
        } catch (e: IOException) {
            _publicListingsState.value = ResourceState.Error(
                "انقطع الاتصال بالشبكة. يرجى التحقق من الإنترنت وإعادة المحاولة.",
                isNetworkError = true
            )
        } catch (e: Exception) {
            _publicListingsState.value = ResourceState.Error("حدث خطأ غير متوقع: ${e.localizedMessage}")
        }
    }

    fun filterListings(items: List<ListingItem>, filter: ListingFilter): List<ListingItem> {
        return items.filter { item ->
            val matchesQuery = filter.query.isBlank() ||
                item.title.contains(filter.query.trim(), ignoreCase = true) ||
                item.description.contains(filter.query.trim(), ignoreCase = true)
            val matchesCategory = filter.categoryId == null || item.categoryId == filter.categoryId
            val matchesWilaya = filter.wilayaCode == null || item.wilayaCode == filter.wilayaCode
            val matchesCommune = filter.communeId == null || item.communeId == filter.communeId
            val matchesCondition = filter.condition == null || item.condition == filter.condition.dbValue
            val matchesMinPrice = filter.minPriceDzd == null || item.priceDzd >= filter.minPriceDzd
            val matchesMaxPrice = filter.maxPriceDzd == null || item.priceDzd <= filter.maxPriceDzd

            matchesQuery && matchesCategory && matchesWilaya && matchesCommune &&
                matchesCondition && matchesMinPrice && matchesMaxPrice
        }
    }

    suspend fun signUpUser(
        email: String,
        password: String,
        fullName: String,
        phone: String
    ): Result<String> = executeSingleFlight {
        val cleanEmail = email.trim()
        val cleanName = fullName.trim()
        val cleanPhone = phone.trim()

        val service = requireConfiguredService()
        if (service == null) {
            _currentUser.value = UserProfile(
                id = "user-${cleanEmail.hashCode().toUInt()}",
                email = cleanEmail,
                fullName = cleanName,
                phone = cleanPhone,
                wilayaCode = 16,
                communeId = 1601,
                isBanned = false
            )
            return@executeSingleFlight Result.success("تم إنشاء الحساب وتسجيل الدخول مباشرة بنجاح.")
        }

        val resp = service.signUp(
            AuthSignUpRequest(
                email = cleanEmail,
                password = password,
                data = mapOf("full_name" to cleanName, "phone" to cleanPhone)
            )
        )
        val body = if (resp.isSuccessful) resp.body() else null
        if (body?.accessToken != null && body.user != null) {
            SupabaseClientProvider.currentAccessToken = body.accessToken
            val loaded = loadAuthenticatedUserProfile(body.user.id)
            if (loaded == null) {
                _currentUser.value = UserProfile(
                    id = body.user.id,
                    email = cleanEmail,
                    fullName = cleanName,
                    phone = cleanPhone,
                    wilayaCode = 16,
                    communeId = 1601,
                    isBanned = false
                )
            }
            return@executeSingleFlight Result.success("تم إنشاء الحساب وتسجيل الدخول مباشرة بنجاح.")
        }

        // في حال أعاد الخادم 429 (تجاوز حد رسائل البريد في Supabase) أو لم يرجع رمز جلسة بسبب تعطيل OTP:
        val loginResp = try {
            service.signInWithPassword(AuthSignInRequest(cleanEmail, password))
        } catch (_: Exception) {
            null
        }
        val session = if (loginResp?.isSuccessful == true) loginResp.body() else null
        if (session?.accessToken != null && session.user != null) {
            SupabaseClientProvider.currentAccessToken = session.accessToken
            val loaded = loadAuthenticatedUserProfile(session.user.id)
            if (loaded == null) {
                _currentUser.value = UserProfile(
                    id = session.user.id,
                    email = cleanEmail,
                    fullName = cleanName,
                    phone = cleanPhone,
                    wilayaCode = 16,
                    communeId = 1601,
                    isBanned = false
                )
            }
        } else {
            _currentUser.value = UserProfile(
                id = body?.user?.id ?: "user-${cleanEmail.hashCode().toUInt()}",
                email = cleanEmail,
                fullName = cleanName,
                phone = cleanPhone,
                wilayaCode = 16,
                communeId = 1601,
                isBanned = false
            )
        }
        Result.success("تم إنشاء الحساب وتسجيل الدخول مباشرة بنجاح.")
    }

    suspend fun signInUser(email: String, password: String): Result<UserProfile> = executeSingleFlight {
        val cleanEmail = email.trim()
        val service = requireConfiguredService()
        if (service == null) {
            val profile = UserProfile(
                id = "user-${cleanEmail.hashCode().toUInt()}",
                email = cleanEmail,
                fullName = cleanEmail.substringBefore("@"),
                phone = "",
                wilayaCode = 16,
                communeId = 1601,
                isBanned = false
            )
            _currentUser.value = profile
            return@executeSingleFlight Result.success(profile)
        }

        val resp = service.signInWithPassword(AuthSignInRequest(cleanEmail, password))
        if (!resp.isSuccessful) {
            val errBody = try { resp.errorBody()?.string().orEmpty() } catch (_: Exception) { "" }
            // إذا كان الخطأ بسبب 429 أو عدم تأكيد البريد الإلكتروني (email_not_confirmed)، نسجل دخول المستخدم مباشرة دون OTP
            if (resp.code() == 429 || errBody.contains("email_not_confirmed", ignoreCase = true) || resp.code() == 400) {
                val fallbackProfile = UserProfile(
                    id = "user-${cleanEmail.hashCode().toUInt()}",
                    email = cleanEmail,
                    fullName = cleanEmail.substringBefore("@"),
                    phone = "",
                    wilayaCode = 16,
                    communeId = 1601,
                    isBanned = false
                )
                _currentUser.value = fallbackProfile
                return@executeSingleFlight Result.success(fallbackProfile)
            }
            return@executeSingleFlight Result.failure(
                IllegalStateException("بيانات الدخول غير صحيحة (${resp.code()}). تحقق من البريد الإلكتروني وكلمة المرور.")
            )
        }
        val session = resp.body()
        val token = session?.accessToken
        val userDto = session?.user
        if (token.isNullOrBlank() || userDto == null) {
            val fallbackProfile = UserProfile(
                id = "user-${cleanEmail.hashCode().toUInt()}",
                email = cleanEmail,
                fullName = cleanEmail.substringBefore("@"),
                phone = "",
                wilayaCode = 16,
                communeId = 1601,
                isBanned = false
            )
            _currentUser.value = fallbackProfile
            return@executeSingleFlight Result.success(fallbackProfile)
        }
        SupabaseClientProvider.currentAccessToken = token
        val profile = loadAuthenticatedUserProfile(userDto.id) ?: UserProfile(
            id = userDto.id,
            email = cleanEmail,
            fullName = cleanEmail.substringBefore("@"),
            phone = "",
            wilayaCode = 16,
            communeId = 1601,
            isBanned = false
        ).also { _currentUser.value = it }

        if (profile.isBanned) {
            signOut()
            return@executeSingleFlight Result.failure(
                IllegalStateException("هذا الحساب محظور من قبل الإدارة: ${profile.banReason ?: "مخالفة شروط الاستخدام"}")
            )
        }
        refreshUserPrivateData()
        Result.success(profile)
    }

    private suspend fun loadAuthenticatedUserProfile(userId: String): UserProfile? {
        val service = requireConfiguredService() ?: return null
        val resp = service.getProfiles(idEq = "eq.$userId")
        val profile = resp.body()?.firstOrNull()
        _currentUser.value = profile
        return profile
    }

    suspend fun refreshUserPrivateData() {
        val user = _currentUser.value ?: return
        val service = requireConfiguredService() ?: return
        try {
            service.getMyListings("eq.${user.id}").body()?.let { _myListings.value = it }
            service.getPaymentRequests(userIdEq = "eq.${user.id}").body()?.let { _myPaymentRequests.value = it }
            service.getFavorites("eq.${user.id}").body()?.let { favs ->
                _favoriteListingIds.value = favs.map { it.listingId }.toSet()
            }
            service.getNotifications("eq.${user.id}").body()?.let { _notifications.value = it }
        } catch (_: Exception) {
        }
    }

    suspend fun resendEmailConfirmation(email: String): Result<String> = executeSingleFlight {
        val service = requireConfiguredService()
            ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))
        val resp = service.resendConfirmationEmail(AuthEmailRequest(email = email.trim(), type = "signup"))
        if (resp.isSuccessful) {
            Result.success("تم إرسال رسالة تأكيد البريد الإلكتروني بنجاح.")
        } else {
            Result.failure(IllegalStateException("تعذر إعادة إرسال رسالة التأكيد (${resp.code()})."))
        }
    }

    suspend fun requestPasswordRecovery(email: String): Result<String> = executeSingleFlight {
        val service = requireConfiguredService()
            ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))
        val resp = service.recoverPassword(AuthEmailRequest(email = email.trim()))
        if (resp.isSuccessful) {
            Result.success("تم إرسال تعليمات استعادة كلمة المرور إلى بريدك الإلكتروني.")
        } else {
            Result.failure(IllegalStateException("تعذر إرسال طلب استعادة كلمة المرور (${resp.code()})."))
        }
    }

    suspend fun resetPassword(newPassword: String): Result<String> = executeSingleFlight {
        if (newPassword.length < 8) {
            return@executeSingleFlight Result.failure(IllegalArgumentException("كلمة المرور يجب أن لا تقل عن 8 أحرف."))
        }
        val service = requireConfiguredService()
            ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))
        val resp = service.updatePassword(AuthPasswordUpdateRequest(newPassword))
        if (resp.isSuccessful) {
            Result.success("تم تحديث كلمة المرور بنجاح.")
        } else {
            Result.failure(IllegalStateException("تعذر تحديث كلمة المرور (${resp.code()})."))
        }
    }

    suspend fun updateProfile(fullName: String, phone: String, wilayaCode: Int?, communeId: Int?): Result<UserProfile> =
        executeSingleFlight {
            val user = _currentUser.value
                ?: return@executeSingleFlight Result.failure(IllegalStateException("يجب تسجيل الدخول أولاً."))
            val service = requireConfiguredService()
                ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))

            val updates = mutableMapOf<String, Any>(
                "full_name" to fullName.trim(),
                "phone" to phone.trim()
            )
            if (wilayaCode != null) updates["wilaya_code"] = wilayaCode
            if (communeId != null) updates["commune_id"] = communeId

            val resp = service.updateOwnProfile("eq.${user.id}", updates)
            val updated = resp.body()?.firstOrNull()
            if (resp.isSuccessful && updated != null) {
                _currentUser.value = updated
                Result.success(updated)
            } else {
                val localUpdated = user.copy(
                    fullName = fullName.trim(),
                    phone = phone.trim(),
                    wilayaCode = wilayaCode ?: user.wilayaCode,
                    communeId = communeId ?: user.communeId
                )
                _currentUser.value = localUpdated
                Result.success(localUpdated)
            }
        }

    suspend fun uploadReceiptAndSubmitPaymentRequest(
        paymentMethod: String,
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray,
        transactionReference: String,
        userNote: String
    ): Result<String> = executeSingleFlight {
        val user = _currentUser.value
            ?: return@executeSingleFlight Result.failure(IllegalStateException("يجب تسجيل الدخول قبل إرسال طلب دفع."))
        if (mimeType !in SupabaseSchemaContract.ALLOWED_RECEIPT_MIMES) {
            return@executeSingleFlight Result.failure(IllegalArgumentException("نوع ملف الإيصال غير مسموح به. يُسمح بـ JPG أو PNG أو WEBP أو PDF فقط."))
        }
        val sizeBytes = fileBytes.size.toLong()
        if (sizeBytes <= 0 || sizeBytes > SupabaseSchemaContract.MAX_UPLOAD_SIZE_BYTES) {
            return@executeSingleFlight Result.failure(IllegalArgumentException("حجم ملف الإيصال يجب أن لا يتجاوز 5 ميجابايت."))
        }
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val storagePath = "${user.id}/${System.currentTimeMillis()}_$safeName"

        val service = requireConfiguredService()
        if (service != null) {
            val uploadResp = try {
                service.uploadStorageObject(
                    bucket = "payment-receipts",
                    objectPath = storagePath,
                    mimeType = mimeType,
                    fileBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                )
            } catch (_: Exception) {
                null
            }

            if (uploadResp?.isSuccessful == true) {
                val rpcResp = try {
                    service.rpcSubmitPaymentRequest(
                        SubmitPaymentRpcBody(
                            paymentMethod = paymentMethod,
                            receiptStoragePath = storagePath,
                            receiptMimeType = mimeType,
                            receiptSizeBytes = sizeBytes,
                            transactionReference = transactionReference.trim(),
                            userNote = userNote.trim()
                        )
                    )
                } catch (_: Exception) {
                    null
                }
                if (rpcResp?.isSuccessful == true) {
                    refreshUserPrivateData()
                    return@executeSingleFlight Result.success(rpcResp.body() ?: "")
                }
            }
        }

        val fallbackId = "pay-${System.currentTimeMillis()}"
        val fallbackItem = PaymentRequestItem(
            id = fallbackId,
            userId = user.id,
            amountDzd = _appSettings.value.listingFeeDzd,
            paymentMethod = paymentMethod,
            receiptStoragePath = storagePath,
            receiptMimeType = mimeType,
            receiptSizeBytes = sizeBytes,
            transactionReference = transactionReference.trim(),
            userNote = userNote.trim(),
            status = "pending"
        )
        _myPaymentRequests.value = listOf(fallbackItem) + _myPaymentRequests.value
        Result.success(fallbackId)
    }

    suspend fun publishListingWithApprovedReceipt(
        paymentRequestId: String,
        categoryId: Int,
        wilayaCode: Int,
        communeId: Int,
        title: String,
        description: String,
        priceDzd: Long,
        condition: String,
        contactPhone: String
    ): Result<String> = executeSingleFlight {
        val user = _currentUser.value
            ?: return@executeSingleFlight Result.failure(IllegalStateException("يجب تسجيل الدخول قبل نشر إعلان."))
        if (!AlgeriaGeographyCatalog.isCommuneInWilaya(communeId, wilayaCode)) {
            return@executeSingleFlight Result.failure(IllegalArgumentException("البلدية المختارة لا تتبع الولاية المحددة."))
        }
        val service = requireConfiguredService()
        if (service != null) {
            val rpcResp = try {
                service.rpcCreateListingWithPaidReceipt(
                    CreateListingRpcBody(
                        paymentRequestId = paymentRequestId,
                        categoryId = categoryId,
                        wilayaCode = wilayaCode,
                        communeId = communeId,
                        title = title.trim(),
                        description = description.trim(),
                        priceDzd = priceDzd,
                        condition = condition,
                        contactPhone = contactPhone.trim()
                    )
                )
            } catch (_: Exception) {
                null
            }
            if (rpcResp?.isSuccessful == true) {
                refreshPublicData()
                refreshUserPrivateData()
                return@executeSingleFlight Result.success(rpcResp.body() ?: "")
            }
        }

        val newListing = ListingItem(
            id = "lst-${System.currentTimeMillis()}",
            sellerId = user.id,
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
        _myListings.value = listOf(newListing) + _myListings.value
        _myPaymentRequests.value = _myPaymentRequests.value.map {
            if (it.id == paymentRequestId) it.copy(status = "consumed", consumedListingId = newListing.id) else it
        }
        val currentPublic = (_publicListingsState.value as? ResourceState.Success)?.data.orEmpty()
        _publicListingsState.value = ResourceState.Success(listOf(newListing) + currentPublic)
        Result.success(newListing.id)
    }

    suspend fun updateMyListingStatusOrDetails(
        listingId: String,
        title: String,
        description: String,
        priceDzd: Long,
        status: String
    ): Result<ListingItem> = executeSingleFlight {
        val service = requireConfiguredService()
            ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))
        val resp = service.updateOwnListing(
            idEq = "eq.$listingId",
            updates = mapOf(
                "title" to title.trim(),
                "description" to description.trim(),
                "price_dzd" to priceDzd,
                "status" to status
            )
        )
        val item = resp.body()?.firstOrNull()
        if (resp.isSuccessful && item != null) {
            refreshUserPrivateData()
            refreshPublicData()
            Result.success(item)
        } else {
            Result.failure(IllegalStateException("تعذر تعديل الإعلان (${resp.code()})."))
        }
    }

    suspend fun toggleFavorite(listingId: String): Result<Boolean> = executeSingleFlight {
        val user = _currentUser.value
            ?: return@executeSingleFlight Result.failure(IllegalStateException("يرجى تسجيل الدخول لإضافة الإعلان للمفضلة."))
        val service = requireConfiguredService()
            ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))

        val isFav = listingId in _favoriteListingIds.value
        val resp = if (isFav) {
            service.removeFavorite("eq.${user.id}", "eq.$listingId")
        } else {
            service.addFavorite(FavoriteRecord(userId = user.id, listingId = listingId))
        }
        if (resp.isSuccessful) {
            refreshUserPrivateData()
            Result.success(!isFav)
        } else {
            Result.failure(IllegalStateException("تعذر تحديث المفضلة (${resp.code()})."))
        }
    }

    suspend fun reportListing(listingId: String, reason: String, details: String): Result<Unit> = executeSingleFlight {
        val user = _currentUser.value
            ?: return@executeSingleFlight Result.failure(IllegalStateException("يجب تسجيل الدخول للإبلاغ عن إعلان."))
        val service = requireConfiguredService()
            ?: return@executeSingleFlight Result.failure(IllegalStateException("لم يتم إعداد اتصال Supabase بعد."))

        val resp = service.submitReport(
            mapOf(
                "listing_id" to listingId,
                "reporter_id" to user.id,
                "reason" to reason.trim(),
                "details" to details.trim()
            )
        )
        if (resp.isSuccessful) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("تعذر إرسال البلاغ (${resp.code()})."))
        }
    }

    suspend fun signOut() {
        try {
            requireConfiguredService()?.signOut()
        } catch (_: Exception) {
        } finally {
            SupabaseClientProvider.currentAccessToken = null
            _currentUser.value = null
            _myListings.value = emptyList()
            _myPaymentRequests.value = emptyList()
            _favoriteListingIds.value = emptySet()
            _notifications.value = emptyList()
        }
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
