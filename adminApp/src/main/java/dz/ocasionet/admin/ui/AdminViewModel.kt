package dz.ocasionet.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dz.ocasionet.admin.data.AdminRepository
import dz.ocasionet.core.model.PaymentRequestItem
import dz.ocasionet.core.network.SupabaseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AdminScreenRoute(val titleAr: String) {
    ADMIN_LOGIN("تسجيل دخول المشرف"),
    DASHBOARD_STATS("لوحة الإحصائيات والمراقبة"),
    PENDING_PAYMENTS("طلبات الدفع المعلقة"),
    RECEIPT_INSPECTOR("فحص إثبات الدفع والموافقة"),
    FINANCIAL_SUMMARY("الإحصائيات المالية للدفعات"),
    CONSUMED_PAYMENTS_LOG("الإعلانات المنشورة بالدفعات المعتمدة"),
    USERS_MANAGEMENT("إدارة المستخدمين والحظر"),
    LISTINGS_MODERATION("إدارة الإعلانات"),
    REPORTS_REVIEW("مراجعة البلاغات"),
    CATEGORIES_MANAGEMENT("إدارة الفئات"),
    APP_SETTINGS_AND_FEE("إعدادات التطبيق وسعر النشر 500 دج"),
    AUDIT_LOGS("سجل عمليات التدقيق (Audit Logs)")
}

class AdminViewModel(
    val repository: AdminRepository = AdminRepository()
) : ViewModel() {

    private val _currentRoute = MutableStateFlow(AdminScreenRoute.ADMIN_LOGIN)
    val currentRoute: StateFlow<AdminScreenRoute> = _currentRoute.asStateFlow()

    private val _selectedPayment = MutableStateFlow<PaymentRequestItem?>(null)
    val selectedPayment: StateFlow<PaymentRequestItem?> = _selectedPayment.asStateFlow()

    private val _signedReceiptUrl = MutableStateFlow<String?>(null)
    val signedReceiptUrl: StateFlow<String?> = _signedReceiptUrl.asStateFlow()

    private val _feedback = MutableStateFlow<String?>(null)
    val feedback: StateFlow<String?> = _feedback.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isDarkAdminTheme = MutableStateFlow(true)
    val isDarkAdminTheme: StateFlow<Boolean> = _isDarkAdminTheme.asStateFlow()

    init {
        restoreAdminSessionOnStartup()
    }

    private fun restoreAdminSessionOnStartup() {
        viewModelScope.launch {
            runCatching {
                val prefs = SupabaseClient.dataStoreRepository?.getUserPreferences()
                if (prefs != null) {
                    _isDarkAdminTheme.value = prefs.darkModeEnabled || true
                }
            }
            val restored = repository.restoreAdminSessionOnStartup().getOrNull()
            if (restored != null && repository.isVerifiedAdmin.value) {
                _currentRoute.value = AdminScreenRoute.DASHBOARD_STATS
            }
        }
    }

    fun toggleAdminTheme() {
        val next = !_isDarkAdminTheme.value
        _isDarkAdminTheme.value = next
        viewModelScope.launch {
            runCatching {
                SupabaseClient.dataStoreRepository?.setDarkModeEnabled(next)
            }
        }
    }

    fun saveSupabaseConnectionConfig(url: String, anonKey: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.updateSupabaseConfig(url, anonKey)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "تم حفظ إعدادات اتصال Supabase بنجاح في DataStore المشفر."
            }.onFailure { err ->
                _feedback.value = err.message ?: "تعذر حفظ إعدادات الاتصال."
            }
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun navigateProtected(route: AdminScreenRoute) {
        if (route == AdminScreenRoute.ADMIN_LOGIN) {
            _currentRoute.value = route
            return
        }
        if (!repository.isVerifiedAdmin.value) {
            _feedback.value = "الوصول مرفوض: يجب تسجيل الدخول بحساب مشرف أولاً."
            _currentRoute.value = AdminScreenRoute.ADMIN_LOGIN
            return
        }
        _currentRoute.value = route
        viewModelScope.launch {
            repository.verifyAdminRoleFromServer()
        }
    }

    fun signInAdmin(email: String, password: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.signInAdmin(email, password)
            _isBusy.value = false
            res.onSuccess { profile ->
                _feedback.value = "مرحباً بك في لوحة الإدارة (${profile.email})."
                _currentRoute.value = AdminScreenRoute.DASHBOARD_STATS
            }.onFailure { err ->
                _feedback.value = err.message ?: "فشل تسجيل دخول المشرف."
            }
        }
    }

    fun refreshDashboard() {
        viewModelScope.launch {
            _isBusy.value = true
            repository.verifyAdminRoleFromServer()
            val res = repository.refreshAdminDashboardData()
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "تم تحديث بيانات لوحة الإدارة."
            }.onFailure { err ->
                _feedback.value = err.message
            }
        }
    }

    fun inspectPaymentReceipt(item: PaymentRequestItem) {
        _selectedPayment.value = item
        _signedReceiptUrl.value = null
        navigateProtected(AdminScreenRoute.RECEIPT_INSPECTOR)
        viewModelScope.launch {
            val signed = repository.adminGeneratePrivateReceiptSignedUrl(item.receiptStoragePath)
            _signedReceiptUrl.value = signed.getOrNull()
        }
    }

    fun approvePayment(paymentRequestId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminReviewPayment(paymentRequestId, decision = "approved")
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "تمت الموافقة على طلب الدفع بنجاح وتحديث سجل التدقيق."
                navigateProtected(AdminScreenRoute.PENDING_PAYMENTS)
            }.onFailure { err ->
                _feedback.value = err.message ?: "فشل اعتماد طلب الدفع."
            }
        }
    }

    fun rejectPayment(paymentRequestId: String, reason: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminReviewPayment(
                paymentRequestId = paymentRequestId,
                decision = "rejected",
                rejectionReason = reason
            )
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "تم رفض طلب الدفع مع تسجيل سبب الرفض في سجل التدقيق."
                navigateProtected(AdminScreenRoute.PENDING_PAYMENTS)
            }.onFailure { err ->
                _feedback.value = err.message ?: "فشل رفض طلب الدفع."
            }
        }
    }

    fun setUserBan(userId: String, isBanned: Boolean, reason: String?) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminSetUserBan(userId, isBanned, reason)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = if (isBanned) "تم حظر المستخدم بنجاح." else "تم إلغاء حظر المستخدم."
            }.onFailure { err ->
                _feedback.value = err.message ?: "تعذر تحديث حالة المستخدم."
            }
        }
    }

    fun moderateListing(listingId: String, newStatus: String, reason: String?) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminModerateListing(listingId, newStatus, reason)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "تم تحديث حالة الإعلان إلى ($newStatus) وتسجيل العملية."
            }.onFailure { err ->
                _feedback.value = err.message ?: "تعذر تحديث حالة الإعلان."
            }
        }
    }

    fun addCategory(slug: String, nameAr: String, nameFr: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminAddCategory(slug, nameAr, nameFr)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "تمت إضافة الفئة الجديدة ($nameAr) بنجاح."
            }.onFailure { err ->
                _feedback.value = err.message ?: "تعذر إضافة الفئة."
            }
        }
    }

    fun updateAppSettings(
        feeDzd: Long,
        ccpAr: String,
        baridimobAr: String,
        noticeAr: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminUpdateSettings(feeDzd, ccpAr, baridimobAr, noticeAr)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "تم حفظ إعدادات التطبيق وسعر النشر ($feeDzd دج) بنجاح."
            }.onFailure { err ->
                _feedback.value = err.message ?: "تعذر حفظ الإعدادات."
            }
        }
    }

    fun signOutAdmin() {
        viewModelScope.launch {
            repository.signOut()
            _currentRoute.value = AdminScreenRoute.ADMIN_LOGIN
            _feedback.value = "تم تسجيل خروج المشرف."
        }
    }
}
