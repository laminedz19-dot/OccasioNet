package dz.ocasionet.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dz.ocasionet.admin.data.AdminRepository
import dz.ocasionet.core.model.PaymentRequestItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * جميع الشاشات والوظائف الـ 18 المطلوبة لتطبيق الإدارة المستقل OccasioNet Admin.
 */
enum class AdminScreenRoute(val titleAr: String) {
    ADMIN_LOGIN("تسجيل دخول المشرفين (تحقق خادمي)"),
    DASHBOARD_STATS("لوحة الإحصائيات العامة"),
    PENDING_PAYMENTS("طلبات الدفع المعلقة للمراجعة"),
    RECEIPT_INSPECTOR("فحص إثبات الدفع والموافقة/الرفض"),
    FINANCIAL_SUMMARY("الإحصائيات المالية لطلبات الدفع"),
    CONSUMED_PAYMENTS_LOG("سجل الإعلانات المنشورة بالدفعات المعتمدة"),
    USERS_MANAGEMENT("إدارة المستخدمين والبحث والحظر"),
    LISTINGS_MODERATION("إدارة ومراجعة الإعلانات"),
    REPORTS_REVIEW("مراجعة الإبلاغات عن الإعلانات"),
    CATEGORIES_MANAGEMENT("إدارة الفئات"),
    APP_SETTINGS_AND_FEE("إعدادات التطبيق وسعر النشر وتعليمات CCP/BaridiMob"),
    AUDIT_LOGS("سجل التدقيق والمراجعة (Audit Logs)")
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

    /**
     * انتقال محمي: يعيد التحقق خادمياً من صلاحية المشرف عبر دالة is_admin() عند فتح أي شاشة حساسة.
     */
    fun navigateProtected(route: AdminScreenRoute) {
        if (route == AdminScreenRoute.ADMIN_LOGIN) {
            _currentRoute.value = AdminScreenRoute.ADMIN_LOGIN
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val stillAdmin = repository.verifyAdminRoleFromServer()
            _isBusy.value = false
            if (!stillAdmin) {
                _feedback.value = "تم رفض الوصول: فشلت إعادة التحقق الخادمي من صلاحيات المشرف (is_admin)."
                _currentRoute.value = AdminScreenRoute.ADMIN_LOGIN
            } else {
                _currentRoute.value = route
            }
        }
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun signInAdmin(email: String, password: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.signInAdmin(email, password)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "تم التحقق الخادمي من دور المشرف (admin) بنجاح."
                _currentRoute.value = AdminScreenRoute.DASHBOARD_STATS
            }.onFailure { err ->
                _feedback.value = err.message ?: "رفض الدخول إلى لوحة الإدارة."
                _currentRoute.value = AdminScreenRoute.ADMIN_LOGIN
            }
        }
    }

    fun refreshDashboard() {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.refreshAdminDashboardData()
            _isBusy.value = false
            res.onFailure { err ->
                _feedback.value = err.message ?: "تعذر تحديث بيانات الإدارة."
            }
        }
    }

    fun inspectPaymentReceipt(payment: PaymentRequestItem) {
        _selectedPayment.value = payment
        _signedReceiptUrl.value = null
        navigateProtected(AdminScreenRoute.RECEIPT_INSPECTOR)
        viewModelScope.launch {
            val signedRes = repository.adminGetSignedReceiptUrl(payment.receiptStoragePath)
            signedRes.onSuccess { url ->
                _signedReceiptUrl.value = url
            }.onFailure { err ->
                _feedback.value = err.message
            }
        }
    }

    fun approvePayment(requestId: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminReviewPayment(requestId = requestId, decision = "approved", rejectionReason = null)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "تمت الموافقة على طلب الدفع وتسجيل العملية في سجل التدقيق (audit_logs)."
                navigateProtected(AdminScreenRoute.PENDING_PAYMENTS)
            }.onFailure { err ->
                _feedback.value = err.message ?: "تعذرت الموافقة على الطلب."
            }
        }
    }

    fun rejectPayment(requestId: String, reason: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminReviewPayment(requestId = requestId, decision = "rejected", rejectionReason = reason)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "تم رفض طلب الدفع مع حفظ السبب وتسجيل المراجعة في سجل التدقيق."
                navigateProtected(AdminScreenRoute.PENDING_PAYMENTS)
            }.onFailure { err ->
                _feedback.value = err.message ?: "تعذر رفض الطلب."
            }
        }
    }

    fun setUserBan(userId: String, isBanned: Boolean, reason: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminSetUserBanStatus(userId, isBanned, reason)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = if (isBanned) "تم حظر الحساب وتسجيل العملية في التدقيق." else "تم إعادة تفعيل الحساب بنجاح."
            }.onFailure { err ->
                _feedback.value = err.message
            }
        }
    }

    fun moderateListing(listingId: String, newStatus: String, reason: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminModerateListing(listingId, newStatus, reason)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "تم تحديث الحالة الإدارية للإعلان وتسجيل العملية في التدقيق."
            }.onFailure { err ->
                _feedback.value = err.message
            }
        }
    }

    fun updateAppSettings(feeDzd: Long, ccpAr: String, baridimobAr: String, noticeAr: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminUpdateSettings(feeDzd, ccpAr, baridimobAr, noticeAr)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "تم تحديث سعر النشر وتعليمات التحويل البريدي و BaridiMob بنجاح."
            }.onFailure { err ->
                _feedback.value = err.message
            }
        }
    }

    fun addCategory(slug: String, nameAr: String, nameFr: String) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true
            val res = repository.adminAddCategory(slug, nameAr, nameFr)
            _isBusy.value = false
            res.onSuccess {
                _feedback.value = "تمت إضافة الفئة الجديدة بنجاح."
            }.onFailure { err ->
                _feedback.value = err.message
            }
        }
    }

    fun signOutAdmin() {
        viewModelScope.launch {
            repository.signOut()
            _feedback.value = "تم تسجيل خروج المشرف بأمان ومسح الجلسة."
            _currentRoute.value = AdminScreenRoute.ADMIN_LOGIN
        }
    }
}
