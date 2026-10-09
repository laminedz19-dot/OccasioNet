package dz.ocasionet.user.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dz.ocasionet.core.model.ListingCondition
import dz.ocasionet.core.model.ListingFilter
import dz.ocasionet.core.model.ListingItem
import dz.ocasionet.core.model.PaymentMethodType
import dz.ocasionet.core.model.PaymentRequestItem
import dz.ocasionet.core.repository.OccasioNetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * جميع الشاشات الـ 29 المطلوبة في تطبيق المستخدم OccasioNet User.
 */
enum class UserScreenRoute(val titleAr: String) {
    SPLASH("شاشة البداية"),
    WELCOME_AUTH("مرحباً بك في OccasioNet"),
    HOME("الرئيسية - OccasioNet"),
    VISITOR_BROWSE("تصفح الإعلانات للزوار"),
    SEARCH_AND_FILTER("البحث والتصفية المتقدمة"),
    FILTER_BY_DETAILS("التصفية حسب الولاية والبلدية والفئة"),
    LISTING_DETAILS("تفاصيل الإعلان والصور"),
    SIGN_UP("إنشاء حساب جديد"),
    SIGN_IN("تسجيل الدخول"),
    EMAIL_CONFIRMATION("تأكيد البريد الإلكتروني"),
    RESEND_CONFIRMATION("إعادة إرسال رسالة التأكيد"),
    FORGOT_PASSWORD("استعادة كلمة المرور"),
    RESET_PASSWORD("إعادة تعيين كلمة المرور"),
    PROFILE("الملف الشخصي"),
    EDIT_PROFILE("تعديل بيانات المستخدم"),
    CREATE_LISTING("نشر إعلان جديد"),
    EDIT_LISTING("تعديل إعلان المستخدم"),
    MY_LISTINGS("قائمة إعلاناتي"),
    MANAGE_LISTING_STATUS("إدارة حالة الإعلان"),
    PAYMENT_FEE("دفع رسوم نشر الإعلان (500 دج)"),
    PAYMENT_INSTRUCTIONS("تعليمات التحويل البريدي و BaridiMob"),
    UPLOAD_RECEIPT("رفع إثبات الدفع"),
    TRACK_PAYMENT("متابعة حالة الطلب"),
    PAYMENT_HISTORY("سجل طلبات الدفع"),
    FAVORITES("المفضلة"),
    REPORT_LISTING("الإبلاغ عن إعلان"),
    NOTIFICATIONS("الإشعارات"),
    SETTINGS("الإعدادات"),
    TERMS_AND_PRIVACY("شروط الاستخدام وسياسة الخصوصية"),
    SYSTEM_STATES("حالات الاتصال والبيانات")
}

data class PendingListingDraft(
    val title: String,
    val description: String,
    val priceDzd: Long,
    val categoryId: Int,
    val wilayaCode: Int,
    val communeId: Int,
    val condition: ListingCondition,
    val contactPhone: String
)

class UserViewModel(
    val repository: OccasioNetRepository = OccasioNetRepository()
) : ViewModel() {

    private val _currentRoute = MutableStateFlow(UserScreenRoute.SPLASH)
    val currentRoute: StateFlow<UserScreenRoute> = _currentRoute.asStateFlow()

    private val _splashLaunchCount = MutableStateFlow(0)
    val splashLaunchCount: StateFlow<Int> = _splashLaunchCount.asStateFlow()

    private val _backStack = mutableListOf<UserScreenRoute>()

    fun completeSplash() {
        _backStack.clear()
        _currentRoute.value = UserScreenRoute.WELCOME_AUTH
    }

    fun replaySplash() {
        _splashLaunchCount.value += 1
        _currentRoute.value = UserScreenRoute.SPLASH
    }

    private val _filter = MutableStateFlow(ListingFilter())
    val filter: StateFlow<ListingFilter> = _filter.asStateFlow()

    private val _selectedListing = MutableStateFlow<ListingItem?>(null)
    val selectedListing: StateFlow<ListingItem?> = _selectedListing.asStateFlow()

    private val _pendingListingDraft = MutableStateFlow<PendingListingDraft?>(null)
    val pendingListingDraft: StateFlow<PendingListingDraft?> = _pendingListingDraft.asStateFlow()

    private val _selectedPaymentRequest = MutableStateFlow<PaymentRequestItem?>(null)
    val selectedPaymentRequest: StateFlow<PaymentRequestItem?> = _selectedPaymentRequest.asStateFlow()

    private val _selectedPaymentMethod = MutableStateFlow(PaymentMethodType.CCP)
    val selectedPaymentMethod: StateFlow<PaymentMethodType> = _selectedPaymentMethod.asStateFlow()

    private val _feedbackBanner = MutableStateFlow<String?>(null)
    val feedbackBanner: StateFlow<String?> = _feedbackBanner.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        refreshPublicData()
    }

    fun navigateTo(route: UserScreenRoute) {
        if (_currentRoute.value != route) {
            _backStack.add(_currentRoute.value)
            _currentRoute.value = route
        }
    }

    fun navigateBack(): Boolean {
        return if (_backStack.isNotEmpty()) {
            _currentRoute.value = _backStack.removeAt(_backStack.lastIndex)
            true
        } else if (_currentRoute.value != UserScreenRoute.HOME) {
            _currentRoute.value = UserScreenRoute.HOME
            true
        } else {
            false
        }
    }

    fun clearFeedback() {
        _feedbackBanner.value = null
    }

    fun postFeedback(msg: String) {
        _feedbackBanner.value = msg
    }

    fun selectListing(item: ListingItem, nextRoute: UserScreenRoute = UserScreenRoute.LISTING_DETAILS) {
        _selectedListing.value = item
        navigateTo(nextRoute)
    }

    fun selectPaymentRequest(item: PaymentRequestItem, nextRoute: UserScreenRoute = UserScreenRoute.TRACK_PAYMENT) {
        _selectedPaymentRequest.value = item
        navigateTo(nextRoute)
    }

    fun selectPaymentMethod(method: PaymentMethodType) {
        _selectedPaymentMethod.value = method
    }

    fun updateFilter(
        query: String = _filter.value.query,
        categoryId: Int? = _filter.value.categoryId,
        wilayaCode: Int? = _filter.value.wilayaCode,
        communeId: Int? = _filter.value.communeId,
        condition: ListingCondition? = _filter.value.condition,
        minPriceDzd: Long? = _filter.value.minPriceDzd,
        maxPriceDzd: Long? = _filter.value.maxPriceDzd
    ) {
        val validCommune = if (wilayaCode != _filter.value.wilayaCode) null else communeId
        _filter.value = ListingFilter(
            query = query,
            categoryId = categoryId,
            wilayaCode = wilayaCode,
            communeId = validCommune,
            condition = condition,
            minPriceDzd = minPriceDzd,
            maxPriceDzd = maxPriceDzd
        )
    }

    fun resetFilter() {
        _filter.value = ListingFilter()
    }

    fun refreshPublicData() {
        viewModelScope.launch {
            repository.refreshPublicData()
            repository.refreshUserPrivateData()
        }
    }

    fun signUp(email: String, password: String, fullName: String, phone: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.signUpUser(email, password, fullName, phone)
            _isBusy.value = false
            res.onSuccess { msg ->
                _feedbackBanner.value = msg
                navigateTo(UserScreenRoute.HOME)
            }.onFailure { err ->
                _feedbackBanner.value = err.message ?: "فشل إنشاء الحساب."
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.signInUser(email, password)
            _isBusy.value = false
            res.onSuccess {
                _feedbackBanner.value = "مرحباً بك، تم تسجيل الدخول بنجاح."
                navigateTo(UserScreenRoute.HOME)
            }.onFailure { err ->
                _feedbackBanner.value = err.message ?: "فشل تسجيل الدخول."
            }
        }
    }

    fun resendConfirmation(email: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.resendEmailConfirmation(email)
            _isBusy.value = false
            _feedbackBanner.value = res.getOrElse { it.message ?: "تعذر إعادة الإرسال." }
        }
    }

    fun recoverPassword(email: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.requestPasswordRecovery(email)
            _isBusy.value = false
            _feedbackBanner.value = res.getOrElse { it.message ?: "تعذر إرسال رابط الاستعادة." }
        }
    }

    fun resetPassword(newPassword: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.resetPassword(newPassword)
            _isBusy.value = false
            _feedbackBanner.value = res.getOrElse { it.message ?: "تعذر تعيين كلمة المرور." }
        }
    }

    fun updateProfile(fullName: String, phone: String, wilayaCode: Int?, communeId: Int?) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateProfile(fullName, phone, wilayaCode, communeId)
            _isBusy.value = false
            res.onSuccess {
                _feedbackBanner.value = "تم حفظ تعديلات الملف الشخصي بنجاح."
                navigateTo(UserScreenRoute.PROFILE)
            }.onFailure { err ->
                _feedbackBanner.value = err.message ?: "تعذر حفظ البيانات."
            }
        }
    }

    fun submitPaymentReceipt(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray,
        transactionRef: String,
        note: String
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.uploadReceiptAndSubmitPaymentRequest(
                paymentMethod = _selectedPaymentMethod.value.dbValue,
                fileName = fileName,
                mimeType = mimeType,
                fileBytes = fileBytes,
                transactionReference = transactionRef,
                userNote = note
            )
            _isBusy.value = false
            res.onSuccess {
                _feedbackBanner.value = "تم رفع إثبات الدفع بنجاح وتسجيل الطلب بحالة (قيد المراجعة - pending)."
                navigateTo(UserScreenRoute.PAYMENT_HISTORY)
            }.onFailure { err ->
                _feedbackBanner.value = err.message ?: "فشل رفع إثبات الدفع."
            }
        }
    }

    fun requestPublishListing(
        draft: PendingListingDraft,
        approvedPaymentRequestId: String? = null
    ) {
        _pendingListingDraft.value = draft
        if (!approvedPaymentRequestId.isNullOrBlank()) {
            publishListing(
                paymentRequestId = approvedPaymentRequestId,
                categoryId = draft.categoryId,
                wilayaCode = draft.wilayaCode,
                communeId = draft.communeId,
                title = draft.title,
                description = draft.description,
                priceDzd = draft.priceDzd,
                condition = draft.condition,
                contactPhone = draft.contactPhone
            )
        } else {
            val fee = repository.appSettings.value.listingFeeDzd
            _feedbackBanner.value = "تم تجهيز إعلانك «${draft.title}». يرجى إتمام دفع رسوم النشر ($fee دج) لإرسال الإعلان للمراجعة والنشر."
            navigateTo(UserScreenRoute.PAYMENT_FEE)
        }
    }

    fun publishListing(
        paymentRequestId: String,
        categoryId: Int,
        wilayaCode: Int,
        communeId: Int,
        title: String,
        description: String,
        priceDzd: Long,
        condition: ListingCondition,
        contactPhone: String
    ) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.publishListingWithApprovedReceipt(
                paymentRequestId = paymentRequestId,
                categoryId = categoryId,
                wilayaCode = wilayaCode,
                communeId = communeId,
                title = title,
                description = description,
                priceDzd = priceDzd,
                condition = condition.dbValue,
                contactPhone = contactPhone
            )
            _isBusy.value = false
            res.onSuccess {
                _pendingListingDraft.value = null
                _feedbackBanner.value = "تم نشر الإعلان بنجاح واستهلاك الدفعة المعتمدة ذرياً."
                navigateTo(UserScreenRoute.MY_LISTINGS)
            }.onFailure { err ->
                _feedbackBanner.value = err.message ?: "تعذر نشر الإعلان."
            }
        }
    }

    fun updateListing(listingId: String, title: String, description: String, priceDzd: Long, status: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateMyListingStatusOrDetails(listingId, title, description, priceDzd, status)
            _isBusy.value = false
            res.onSuccess {
                _feedbackBanner.value = "تم تحديث الإعلان وحالته بنجاح."
                navigateTo(UserScreenRoute.MY_LISTINGS)
            }.onFailure { err ->
                _feedbackBanner.value = err.message ?: "تعذر تحديث الإعلان."
            }
        }
    }

    fun toggleFavorite(listingId: String) {
        viewModelScope.launch {
            val res = repository.toggleFavorite(listingId)
            res.onFailure { err ->
                _feedbackBanner.value = err.message
            }
        }
    }

    fun submitReport(listingId: String, reason: String, details: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.reportListing(listingId, reason, details)
            _isBusy.value = false
            res.onSuccess {
                _feedbackBanner.value = "تم إرسال بلاغك إلى الإدارة للمراجعة."
                navigateBack()
            }.onFailure { err ->
                _feedbackBanner.value = err.message ?: "تعذر إرسال البلاغ."
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            repository.signOut()
            _feedbackBanner.value = "تم تسجيل الخروج بنجاح."
            navigateTo(UserScreenRoute.HOME)
        }
    }
}
