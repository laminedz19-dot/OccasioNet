package dz.ocasionet.admin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dz.ocasionet.core.model.PaymentMethodType
import dz.ocasionet.core.model.PaymentStatus
import dz.ocasionet.core.model.SupabaseConfigStatus
import dz.ocasionet.core.ui.theme.OccasioNetTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OccasioNetAdminAppRoot(
    adminViewModel: AdminViewModel = viewModel()
) {
    val currentRoute by adminViewModel.currentRoute.collectAsState()
    val isVerifiedAdmin by adminViewModel.repository.isVerifiedAdmin.collectAsState()
    val feedback by adminViewModel.feedback.collectAsState()
    val isBusy by adminViewModel.isBusy.collectAsState()

    BackHandler(enabled = currentRoute != AdminScreenRoute.ADMIN_LOGIN && currentRoute != AdminScreenRoute.DASHBOARD_STATS) {
        adminViewModel.navigateProtected(AdminScreenRoute.DASHBOARD_STATS)
    }

    OccasioNetTheme(isAdminTheme = true) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            topBar = {
                TopAppBar(
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    title = {
                        Column {
                            Text(
                                text = "OccasioNet Admin • ${currentRoute.titleAr}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (isVerifiedAdmin) "حالة الصلاحيات: مشرف موثق خادمياً (is_admin = true)" else "غير مسجل كمشرف",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    },
                    actions = {
                        if (isVerifiedAdmin) {
                            IconButton(onClick = { adminViewModel.refreshDashboard() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "تحديث")
                            }
                            IconButton(
                                onClick = { adminViewModel.signOutAdmin() },
                                modifier = Modifier.testTag("admin_logout_button")
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = "تسجيل خروج المشرف")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (isVerifiedAdmin && currentRoute != AdminScreenRoute.ADMIN_LOGIN) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == AdminScreenRoute.DASHBOARD_STATS || currentRoute == AdminScreenRoute.FINANCIAL_SUMMARY,
                            onClick = { adminViewModel.navigateProtected(AdminScreenRoute.DASHBOARD_STATS) },
                            icon = { Icon(Icons.Default.Analytics, contentDescription = "الإحصائيات") },
                            label = { Text("الإحصائيات") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == AdminScreenRoute.PENDING_PAYMENTS || currentRoute == AdminScreenRoute.RECEIPT_INSPECTOR,
                            onClick = { adminViewModel.navigateProtected(AdminScreenRoute.PENDING_PAYMENTS) },
                            icon = { Icon(Icons.Default.Payment, contentDescription = "مراجعة الدفعات") },
                            label = { Text("الدفعات") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == AdminScreenRoute.USERS_MANAGEMENT,
                            onClick = { adminViewModel.navigateProtected(AdminScreenRoute.USERS_MANAGEMENT) },
                            icon = { Icon(Icons.Default.People, contentDescription = "المستخدمون") },
                            label = { Text("المستخدمون") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == AdminScreenRoute.LISTINGS_MODERATION || currentRoute == AdminScreenRoute.REPORTS_REVIEW,
                            onClick = { adminViewModel.navigateProtected(AdminScreenRoute.LISTINGS_MODERATION) },
                            icon = { Icon(Icons.Default.Gavel, contentDescription = "الإعلانات والبلاغات") },
                            label = { Text("الرقابة") }
                        )
                        NavigationBarItem(
                            selected = currentRoute == AdminScreenRoute.APP_SETTINGS_AND_FEE || currentRoute == AdminScreenRoute.AUDIT_LOGS,
                            onClick = { adminViewModel.navigateProtected(AdminScreenRoute.APP_SETTINGS_AND_FEE) },
                            icon = { Icon(Icons.Default.Settings, contentDescription = "الإعدادات والتدقيق") },
                            label = { Text("الإعدادات") }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (feedback != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = feedback ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { adminViewModel.clearFeedback() }) {
                                Text("إغلاق")
                            }
                        }
                    }
                }

                if (isBusy) {
                    Box(modifier = Modifier.fillMaxWidth().padding(4.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                    }
                }

                if (!isVerifiedAdmin || currentRoute == AdminScreenRoute.ADMIN_LOGIN) {
                    AdminLoginScreen(adminViewModel)
                } else {
                    when (currentRoute) {
                        AdminScreenRoute.ADMIN_LOGIN -> AdminLoginScreen(adminViewModel)
                        AdminScreenRoute.DASHBOARD_STATS -> AdminDashboardStatsScreen(adminViewModel)
                        AdminScreenRoute.PENDING_PAYMENTS -> AdminPendingPaymentsScreen(adminViewModel)
                        AdminScreenRoute.RECEIPT_INSPECTOR -> AdminReceiptInspectorScreen(adminViewModel)
                        AdminScreenRoute.FINANCIAL_SUMMARY -> AdminFinancialSummaryScreen(adminViewModel)
                        AdminScreenRoute.CONSUMED_PAYMENTS_LOG -> AdminConsumedPaymentsLogScreen(adminViewModel)
                        AdminScreenRoute.USERS_MANAGEMENT -> AdminUsersManagementScreen(adminViewModel)
                        AdminScreenRoute.LISTINGS_MODERATION -> AdminListingsModerationScreen(adminViewModel)
                        AdminScreenRoute.REPORTS_REVIEW -> AdminReportsReviewScreen(adminViewModel)
                        AdminScreenRoute.CATEGORIES_MANAGEMENT -> AdminCategoriesManagementScreen(adminViewModel)
                        AdminScreenRoute.APP_SETTINGS_AND_FEE -> AdminAppSettingsAndFeeScreen(adminViewModel)
                        AdminScreenRoute.AUDIT_LOGS -> AdminAuditLogsScreen(adminViewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminLoginScreen(viewModel: AdminViewModel) {
    val configStatus by viewModel.repository.configStatus.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("OccasioNet Admin", style = MaterialTheme.typography.headlineLarge)
                    Text("تطبيق الإدارة المستقل • معرّف الحزمة: dz.ocasionet.admin")
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("سياسة حماية لوحة الإدارة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "• لا يُسمح بإنشاء حساب مشرف من التطبيق.\n" +
                            "• يتحقق الخادم من دور المشرف عبر دالة PostgreSQL المحمية public.is_admin().\n" +
                            "• يُرفض دخول أي مستخدم عادي حتى لو كان يملك حساباً صالحاً في Supabase Auth.\n" +
                            "• لا يحتوي التطبيق على مفتاح service_role نهائياً.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (configStatus !is SupabaseConfigStatus.Configured) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "تنبيه: لم يتم ضبط SUPABASE_URL و SUPABASE_ANON_KEY بعد.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "أدخل بيانات مشروع Supabase في لوحة Secrets أو ملف الإعداد المحلي لتفعيل تسجيل دخول المشرفين الفعلي.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("البريد الإلكتروني للمشرف") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_login_email_input")
            )
        }

        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("كلمة المرور") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_login_password_input")
            )
        }

        item {
            Button(
                onClick = { viewModel.signInAdmin(email, password) },
                enabled = email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_login_submit_button")
            ) {
                Text("تحقق من صلاحيات المشرف وتسجيل الدخول")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminDashboardStatsScreen(viewModel: AdminViewModel) {
    val pending by viewModel.repository.adminPendingPayments.collectAsState()
    val users by viewModel.repository.adminUsers.collectAsState()
    val listings by viewModel.repository.adminAllListings.collectAsState()
    val reports by viewModel.repository.adminReports.collectAsState()
    val settings by viewModel.repository.appSettings.collectAsState()
    val summary = viewModel.repository.computeAdminFinancialSummary()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("لوحة الإحصائيات والمراقبة المركزية", style = MaterialTheme.typography.headlineMedium)
            Text("رسم النشر الحالي: ${settings.listingFeeDzd} دج لكل إعلان")
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ElevatedCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("دفعات معلقة", style = MaterialTheme.typography.bodyMedium)
                        Text("${pending.size}", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                ElevatedCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("إجمالي الإعلانات", style = MaterialTheme.typography.bodyMedium)
                        Text("${listings.size}", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ElevatedCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("المستخدمون", style = MaterialTheme.typography.bodyMedium)
                        Text("${users.size}", style = MaterialTheme.typography.headlineLarge)
                    }
                }
                ElevatedCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("البلاغات المفتوحة", style = MaterialTheme.typography.bodyMedium)
                        Text("${reports.count { it.status == "open" }}", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item {
            Text("الأقسام الإدارية السريعة (18 وظيفة إدارية):", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { viewModel.navigateProtected(AdminScreenRoute.PENDING_PAYMENTS) },
                    label = { Text("مراجعة طلبات الدفع المعلقة (${pending.size})") }
                )
                AssistChip(
                    onClick = { viewModel.navigateProtected(AdminScreenRoute.FINANCIAL_SUMMARY) },
                    label = { Text("الإحصائيات المالية (${summary.totalApprovedAndConsumedDzd} دج)") }
                )
                AssistChip(
                    onClick = { viewModel.navigateProtected(AdminScreenRoute.CONSUMED_PAYMENTS_LOG) },
                    label = { Text("سجل الإعلانات المنشورة بالدفعات المعتمدة") }
                )
                AssistChip(
                    onClick = { viewModel.navigateProtected(AdminScreenRoute.USERS_MANAGEMENT) },
                    label = { Text("إدارة المستخدمين والحظر") }
                )
                AssistChip(
                    onClick = { viewModel.navigateProtected(AdminScreenRoute.LISTINGS_MODERATION) },
                    label = { Text("إدارة الإعلانات") }
                )
                AssistChip(
                    onClick = { viewModel.navigateProtected(AdminScreenRoute.REPORTS_REVIEW) },
                    label = { Text("مراجعة البلاغات") }
                )
                AssistChip(
                    onClick = { viewModel.navigateProtected(AdminScreenRoute.CATEGORIES_MANAGEMENT) },
                    label = { Text("إدارة الفئات") }
                )
                AssistChip(
                    onClick = { viewModel.navigateProtected(AdminScreenRoute.APP_SETTINGS_AND_FEE) },
                    label = { Text("ضبط سعر 500 دج وتعليمات CCP و BaridiMob") }
                )
                AssistChip(
                    onClick = { viewModel.navigateProtected(AdminScreenRoute.AUDIT_LOGS) },
                    label = { Text("سجل التدقيق (Audit Logs)") },
                    leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                )
            }
        }
    }
}

@Composable
fun AdminPendingPaymentsScreen(viewModel: AdminViewModel) {
    val pending by viewModel.repository.adminPendingPayments.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("طلبات الدفع المعلقة للمراجعة اليدوية", style = MaterialTheme.typography.headlineMedium)
        }
        if (pending.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("لا توجد طلبات دفع معلقة حالياً.")
                    }
                }
            }
        } else {
            items(pending, key = { it.id }) { req ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("طلب #${req.id.take(8)} • ${req.amountDzd} دج", fontWeight = FontWeight.Bold)
                        Text("المستخدم: ${req.userId}")
                        Text("الوسيلة: ${PaymentMethodType.fromDb(req.paymentMethod).labelAr}")
                        Text("رقم الوصل / المرجع: ${req.transactionReference}")
                        Button(
                            onClick = { viewModel.inspectPaymentReceipt(req) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("فحص إثبات الدفع من Storage الخاص والموافقة أو الرفض")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminReceiptInspectorScreen(viewModel: AdminViewModel) {
    val req by viewModel.selectedPayment.collectAsState()
    val signedUrl by viewModel.signedReceiptUrl.collectAsState()
    var rejectionReason by remember { mutableStateOf("") }

    val item = req
    if (item == null) {
        Text("لم يتم اختيار طلب دفع للمراجعة.", modifier = Modifier.padding(16.dp))
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("مراجعة إثبات الدفع والبت في الطلب", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("معرّف الطلب: ${item.id}", fontWeight = FontWeight.Bold)
                    Text("المستخدم: ${item.userId}")
                    Text("المبلغ المطلوب: ${item.amountDzd} دج")
                    Text("وسيلة الدفع: ${PaymentMethodType.fromDb(item.paymentMethod).labelAr}")
                    Text("رقم المرجع البريدي: ${item.transactionReference}")
                    Text("مسار الإيصال الخاص: ${item.receiptStoragePath}")
                    Text(
                        text = if (signedUrl != null) "رابط موقع مؤقت (صالح لـ 120 ثانية): $signedUrl" else "جاري توليد رابط موقع قصير الصلاحية من bucket (payment-receipts)...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        item {
            Button(
                onClick = { viewModel.approvePayment(item.id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_approve_payment_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("الموافقة على طلب الدفع (تفعيل نشر إعلان واحد)")
            }
        }
        item {
            OutlinedTextField(
                value = rejectionReason,
                onValueChange = { rejectionReason = it },
                label = { Text("سبب الرفض (إلزامي عند رفض الطلب)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_rejection_reason_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.rejectPayment(item.id, rejectionReason) },
                enabled = rejectionReason.trim().length >= 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_reject_payment_button")
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("رفض طلب الدفع مع تسجيل السبب")
            }
        }
    }
}

@Composable
fun AdminFinancialSummaryScreen(viewModel: AdminViewModel) {
    val summary = viewModel.repository.computeAdminFinancialSummary()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("الإحصائيات المالية الخاصة بطلبات الدفع", style = MaterialTheme.typography.headlineMedium)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "تنبيه محاسبي صريح: تعكس هذه الأرقام حالات طلبات الدفع المسجلة والمراجعة داخل النظام فقط، ولا تُعد كشف حساب بنكي أو تأكيداً مصرفياً آلياً خارجياً من بريد الجزائر.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("طلبات قيد المراجعة (pending): ${summary.pendingCount}")
                    Text("طلبات معتمدة غير مستهلكة بعد (approved): ${summary.approvedUnusedCount}")
                    Text("طلبات معتمدة ومستهلكة في إعلانات (consumed): ${summary.consumedCount}")
                    Text("طلبات مرفوضة (rejected): ${summary.rejectedCount}")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("إجمالي مبالغ الطلبات المعتمدة والمستهلكة: ${summary.totalApprovedAndConsumedDzd} دج", fontWeight = FontWeight.Bold)
                    Text("إجمالي مبالغ جميع الطلبات المرسلة: ${summary.totalSubmittedDzd} دج")
                }
            }
        }
    }
}

@Composable
fun AdminConsumedPaymentsLogScreen(viewModel: AdminViewModel) {
    val allPayments by viewModel.repository.adminAllPayments.collectAsState()
    val consumed = remember(allPayments) { allPayments.filter { it.status == "consumed" } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("سجل الإعلانات المنشورة بواسطة الدفعات المعتمدة", style = MaterialTheme.typography.headlineMedium)
        }
        if (consumed.isEmpty()) {
            item { Text("لا توجد دفعات مستهلكة حتى الآن.") }
        } else {
            items(consumed, key = { it.id }) { req ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("دفعة #${req.id.take(8)} (${req.amountDzd} دج)", fontWeight = FontWeight.Bold)
                        Text("معرّف الإعلان المنشور المرتبط: ${req.consumedListingId}")
                        Text("تاريخ الاستهلاك الذري: ${req.consumedAt ?: ""}")
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUsersManagementScreen(viewModel: AdminViewModel) {
    val users by viewModel.repository.adminUsers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var banReason by remember { mutableStateOf("مخالفة شروط الاستخدام في المنصة") }

    val filtered = remember(users, searchQuery) {
        users.filter {
            searchQuery.isBlank() ||
                it.email.contains(searchQuery, ignoreCase = true) ||
                it.fullName.contains(searchQuery, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("إدارة المستخدمين والبحث والحظر", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("ابحث بالبريد الإلكتروني أو الاسم") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = banReason,
                onValueChange = { banReason = it },
                label = { Text("سبب الحظر الافتراضي عند إيقاف حساب") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        items(filtered, key = { it.id }) { u ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(u.fullName.ifBlank { "بدون اسم" }, fontWeight = FontWeight.Bold)
                    Text("البريد: ${u.email} • الهاتف: ${u.phone}")
                    Text("الحالة: ${if (u.isBanned) "محظور (${u.banReason})" else "نشط"}")
                    Button(
                        onClick = { viewModel.setUserBan(u.id, !u.isBanned, banReason) }
                    ) {
                        Text(if (u.isBanned) "إلغاء الحظر وتفعيل الحساب" else "حظر الحساب")
                    }
                }
            }
        }
    }
}

@Composable
fun AdminListingsModerationScreen(viewModel: AdminViewModel) {
    val listings by viewModel.repository.adminAllListings.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("إدارة ومراجعة الإعلانات", style = MaterialTheme.typography.headlineMedium)
                OutlinedButton(onClick = { viewModel.navigateProtected(AdminScreenRoute.REPORTS_REVIEW) }) {
                    Text("عرض البلاغات")
                }
            }
        }
        items(listings, key = { it.id }) { item ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${item.title} (${item.priceDzd} دج)", fontWeight = FontWeight.Bold)
                    Text("الحالة الحالية: ${item.status} • الدفعة المرتبطة: ${item.paymentRequestId.take(8)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.moderateListing(item.id, "published", "إعادة إظهار") }) {
                            Text("نشر")
                        }
                        OutlinedButton(onClick = { viewModel.moderateListing(item.id, "hidden_by_admin", "إخفاء إداري") }) {
                            Text("إخفاء")
                        }
                        OutlinedButton(onClick = { viewModel.moderateListing(item.id, "rejected", "مخالف") }) {
                            Text("رفض")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminReportsReviewScreen(viewModel: AdminViewModel) {
    val reports by viewModel.repository.adminReports.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("مراجعة البلاغات على الإعلانات", style = MaterialTheme.typography.headlineMedium)
        }
        if (reports.isEmpty()) {
            item { Text("لا توجد بلاغات مسجلة حالياً.") }
        } else {
            items(reports, key = { it.id }) { rep ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("بلاغ على الإعلان: ${rep.listingId}", fontWeight = FontWeight.Bold)
                        Text("السبب: ${rep.reason}")
                        Text("التفاصيل: ${rep.details}")
                        Text("الحالة: ${rep.status}")
                    }
                }
            }
        }
    }
}

@Composable
fun AdminCategoriesManagementScreen(viewModel: AdminViewModel) {
    val categories by viewModel.repository.categories.collectAsState()
    var slug by remember { mutableStateOf("") }
    var nameAr by remember { mutableStateOf("") }
    var nameFr by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("إدارة فئات السوق", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(value = slug, onValueChange = { slug = it }, label = { Text("المعرف النصي (slug)") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(value = nameAr, onValueChange = { nameAr = it }, label = { Text("الاسم بالعربية") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(value = nameFr, onValueChange = { nameFr = it }, label = { Text("الاسم بالفرنسية") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.addCategory(slug, nameAr, nameFr) },
                enabled = slug.isNotBlank() && nameAr.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("إضافة فئة جديدة")
            }
        }
        items(categories, key = { it.id }) { cat ->
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${cat.id}. ${cat.nameAr} (${cat.slug})", fontWeight = FontWeight.Bold)
                    Text(cat.nameFr)
                }
            }
        }
    }
}

@Composable
fun AdminAppSettingsAndFeeScreen(viewModel: AdminViewModel) {
    val settings by viewModel.repository.appSettings.collectAsState()
    var feeText by remember(settings) { mutableStateOf(settings.listingFeeDzd.toString()) }
    var ccpAr by remember(settings) { mutableStateOf(settings.ccpInstructionsAr) }
    var baridimobAr by remember(settings) { mutableStateOf(settings.baridimobInstructionsAr) }
    var noticeAr by remember(settings) { mutableStateOf(settings.paymentNoticeAr) }
    val parsedFee = feeText.toLongOrNull()
    val isFeeValid = parsedFee != null && parsedFee >= 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("إعدادات التطبيق وسعر النشر وتعليمات الدفع", style = MaterialTheme.typography.headlineMedium)
            Text("القيمة الابتدائية: 500 دج لكل إعلان. لا تضع أرقام حسابات غير حقيقية قبل التحقق منها.")
        }
        item {
            OutlinedTextField(
                value = feeText,
                onValueChange = { feeText = it },
                label = { Text("سعر نشر الإعلان الواحد بالدينار الجزائري (دج)") },
                isError = !isFeeValid,
                supportingText = {
                    if (!isFeeValid) Text("أدخل مبلغًا صحيحًا غير سالب.")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_fee_dzd_input")
            )
        }
        item {
            OutlinedTextField(
                value = ccpAr,
                onValueChange = { ccpAr = it },
                label = { Text("تعليمات التحويل البريدي (CCP) الرسمية") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = baridimobAr,
                onValueChange = { baridimobAr = it },
                label = { Text("تعليمات الدفع عبر BaridiMob (RIP)") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = noticeAr,
                onValueChange = { noticeAr = it },
                label = { Text("تنبيه الدفع العام للمستخدمين") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = {
                    parsedFee?.takeIf { it >= 0 }?.let { fee ->
                        viewModel.updateAppSettings(fee, ccpAr, baridimobAr, noticeAr)
                    }
                },
                enabled = isFeeValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_save_settings_button")
            ) {
                Text("حفظ السعر وتعليمات الدفع في قاعدة البيانات")
            }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.navigateProtected(AdminScreenRoute.AUDIT_LOGS) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("عرض سجل عمليات المراجعة والتدقيق (Audit Logs)")
            }
        }
    }
}

@Composable
fun AdminAuditLogsScreen(viewModel: AdminViewModel) {
    val logs by viewModel.repository.adminAuditLogs.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("سجل عمليات المراجعة والتدقيق (Audit Logs)", style = MaterialTheme.typography.headlineMedium)
        }
        if (logs.isEmpty()) {
            item { Text("لا توجد سجلات تدقيق حالياً.") }
        } else {
            items(logs, key = { it.id }) { log ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(log.actionType, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("الجدول: ${log.targetTable} • السجل: ${log.targetId}")
                        Text("المشرف/المنفذ: ${log.actorId} • الوقت: ${log.createdAt}")
                    }
                }
            }
        }
    }
}
