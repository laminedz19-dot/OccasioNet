package dz.ocasionet.user.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.ocasionet.core.data.AlgeriaGeographyCatalog
import dz.ocasionet.core.model.ListingCondition
import dz.ocasionet.core.model.ListingStatus
import dz.ocasionet.core.model.PaymentMethodType
import dz.ocasionet.core.model.PaymentStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserCreateListingScreen(viewModel: UserViewModel) {
    val currentUser by viewModel.repository.currentUser.collectAsState()
    val myPayments by viewModel.repository.myPaymentRequests.collectAsState()
    val categories by viewModel.repository.categories.collectAsState()
    val wilayas by viewModel.repository.wilayas.collectAsState()
    val settings by viewModel.repository.appSettings.collectAsState()
    val pendingDraft by viewModel.pendingListingDraft.collectAsState()

    val approvedUnconsumedReceipts = remember(myPayments) {
        myPayments.filter { it.status == "approved" && it.consumedListingId == null }
    }

    var selectedPaymentId by remember(approvedUnconsumedReceipts) {
        mutableStateOf(approvedUnconsumedReceipts.firstOrNull()?.id ?: "")
    }
    var selectedCategoryId by remember(pendingDraft) {
        mutableStateOf(pendingDraft?.categoryId ?: categories.firstOrNull()?.id ?: 1)
    }
    var selectedWilayaCode by remember(pendingDraft) {
        mutableStateOf(pendingDraft?.wilayaCode ?: 16)
    }
    val communes = remember(selectedWilayaCode) {
        viewModel.repository.communesForWilaya(selectedWilayaCode)
    }
    var selectedCommuneId by remember(selectedWilayaCode, pendingDraft) {
        mutableStateOf(pendingDraft?.communeId ?: communes.firstOrNull()?.id ?: 1)
    }
    var selectedCondition by remember(pendingDraft) {
        mutableStateOf(pendingDraft?.condition ?: ListingCondition.GOOD)
    }
    var title by remember(pendingDraft) { mutableStateOf(pendingDraft?.title ?: "") }
    var description by remember(pendingDraft) { mutableStateOf(pendingDraft?.description ?: "") }
    var priceText by remember(pendingDraft) {
        mutableStateOf(pendingDraft?.priceDzd?.toString() ?: "")
    }
    var contactPhone by remember(pendingDraft, currentUser) {
        mutableStateOf(pendingDraft?.contactPhone ?: currentUser?.phone ?: "")
    }

    val isFormValid = title.trim().length >= 5 &&
        description.trim().length >= 10 &&
        (priceText.toLongOrNull() ?: -1L) >= 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("نشر إعلان جديد", style = MaterialTheme.typography.headlineMedium)
            Text(
                "أدخل تفاصيل إعلانك أولاً، وعند طلب النشر سيتم توجيهك لإتمام دفع رسوم النشر (${settings.listingFeeDzd} دج) عبر CCP أو BaridiMob.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (approvedUnconsumedReceipts.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            "لديك دفعة معتمدة جاهزة للنشر الفوري (تُستهلك لمرة واحدة):",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            approvedUnconsumedReceipts.forEach { req ->
                                FilterChip(
                                    selected = selectedPaymentId == req.id,
                                    onClick = { selectedPaymentId = req.id },
                                    label = { Text("إيصال معتمد #${req.id.take(6)} (${req.amountDzd} دج)") },
                                    leadingIcon = { Icon(Icons.Default.Verified, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان الإعلان (5 إلى 140 حرفاً)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_listing_title_input")
            )
        }

        item {
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("السعر المطلوب بالدينار الجزائري (دج)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_listing_price_input")
            )
        }

        item {
            Text("الفئة:", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = { selectedCategoryId = cat.id },
                        label = { Text(cat.nameAr) }
                    )
                }
            }
        }

        item {
            Text("حالة الغرض المعروض:", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ListingCondition.entries.forEach { cond ->
                    FilterChip(
                        selected = selectedCondition == cond,
                        onClick = { selectedCondition = cond },
                        label = { Text(cond.labelAr) }
                    )
                }
            }
        }

        item {
            Text("الولاية (58 ولاية):", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                wilayas.forEach { w ->
                    FilterChip(
                        selected = selectedWilayaCode == w.code,
                        onClick = {
                            selectedWilayaCode = w.code
                            selectedCommuneId = viewModel.repository.communesForWilaya(w.code).firstOrNull()?.id ?: 1
                        },
                        label = { Text("%02d - %s".format(w.code, w.nameAr)) }
                    )
                }
            }
        }

        item {
            Text("البلدية التابعة للولاية المختارة:", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                communes.forEach { c ->
                    FilterChip(
                        selected = selectedCommuneId == c.id,
                        onClick = { selectedCommuneId = c.id },
                        label = { Text(c.nameAr) }
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = contactPhone,
                onValueChange = { contactPhone = it },
                label = { Text("رقم هاتف البائع للتواصل") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("تفاصيل الإعلان ومواصفات المنتج (10 أحرف على الأقل)") },
                minLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_listing_desc_input")
            )
        }

        item {
            Button(
                onClick = {
                    val price = priceText.toLongOrNull() ?: 0L
                    val draft = PendingListingDraft(
                        title = title.trim(),
                        description = description.trim(),
                        priceDzd = price,
                        categoryId = selectedCategoryId,
                        wilayaCode = selectedWilayaCode,
                        communeId = selectedCommuneId,
                        condition = selectedCondition,
                        contactPhone = contactPhone.trim()
                    )
                    viewModel.requestPublishListing(
                        draft = draft,
                        approvedPaymentRequestId = selectedPaymentId.takeIf { it.isNotBlank() }
                    )
                },
                enabled = isFormValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("submit_create_listing_button")
            ) {
                Text(
                    if (selectedPaymentId.isNotBlank()) {
                        "نشر الإعلان الآن واستهلاك الدفعة المعتمدة"
                    } else {
                        "طلب نشر الإعلان والمتابعة لدفع الرسوم (${settings.listingFeeDzd} دج)"
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserEditAndManageListingScreen(viewModel: UserViewModel) {
    val selected by viewModel.selectedListing.collectAsState()
    val listing = selected
    if (listing == null) {
        Text("لم يتم تحديد إعلان لتعديله.", modifier = Modifier.padding(16.dp))
        return
    }

    var title by remember(listing) { mutableStateOf(listing.title) }
    var description by remember(listing) { mutableStateOf(listing.description) }
    var priceText by remember(listing) { mutableStateOf(listing.priceDzd.toString()) }
    var status by remember(listing) { mutableStateOf(listing.status) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("تعديل الإعلان وإدارة حالته", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("عنوان الإعلان") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("السعر (دج)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("الوصف") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text("حالة الإعلان:", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ListingStatus.PUBLISHED, ListingStatus.SOLD, ListingStatus.PAUSED).forEach { st ->
                    FilterChip(
                        selected = status == st.dbValue,
                        onClick = { status = st.dbValue },
                        label = { Text(st.labelAr) }
                    )
                }
            }
        }
        item {
            Button(
                onClick = {
                    viewModel.updateListing(
                        listingId = listing.id,
                        title = title,
                        description = description,
                        priceDzd = priceText.toLongOrNull() ?: listing.priceDzd,
                        status = status
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حفظ تعديلات الإعلان")
            }
        }
    }
}

@Composable
fun UserMyListingsScreen(viewModel: UserViewModel) {
    val myListings by viewModel.repository.myListings.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("قائمة إعلاناتي", style = MaterialTheme.typography.headlineMedium)
        }
        if (myListings.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("لا توجد لديك إعلانات منشورة بعد.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.navigateTo(UserScreenRoute.CREATE_LISTING) }) {
                            Text("إنشاء إعلان جديد")
                        }
                    }
                }
            }
        } else {
            items(myListings, key = { it.id }) { item ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${item.priceDzd} دج • الحالة: ${ListingStatus.fromDb(item.status).labelAr}")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.selectListing(item, UserScreenRoute.MANAGE_LISTING_STATUS) }
                        ) {
                            Text("تعديل الإعلان أو تغيير حالته")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserPaymentFeeScreen(viewModel: UserViewModel) {
    val settings by viewModel.repository.appSettings.collectAsState()
    val selectedMethod by viewModel.selectedPaymentMethod.collectAsState()
    val pendingDraft by viewModel.pendingListingDraft.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (pendingDraft != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pending_listing_draft_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "إتمام دفع رسوم نشر إعلانك المطلوب",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("عنوان الإعلان: ${pendingDraft?.title}")
                        Text("السعر المعروض: ${pendingDraft?.priceDzd} دج • الولاية: ${AlgeriaGeographyCatalog.getWilayaNameAr(pendingDraft?.wilayaCode)}")
                        Text(
                            text = "بعد رفع إيصال الدفع (${settings.listingFeeDzd} دج) واعتماده من الإدارة، يمكنك نشر هذا الإعلان مباشرة.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("شاشة دفع رسوم نشر الإعلان", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "الرسم الخادمي المعتمد لنشر إعلان واحد: ${settings.listingFeeDzd} دينار جزائري (دج)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(settings.paymentNoticeAr, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Text("1. اختر وسيلة الدفع المتاحة في الجزائر:", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaymentMethodType.entries.forEach { method ->
                    FilterChip(
                        selected = selectedMethod == method,
                        onClick = { viewModel.selectPaymentMethod(method) },
                        label = { Text(method.labelAr) },
                        modifier = Modifier.testTag("payment_method_chip_${method.dbValue}")
                    )
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.navigateTo(UserScreenRoute.PAYMENT_INSTRUCTIONS) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("view_payment_instructions_button")
            ) {
                Text("عرض تعليمات الدفع عبر ${selectedMethod.labelAr}")
            }
        }

        item {
            OutlinedButton(
                onClick = { viewModel.navigateTo(UserScreenRoute.UPLOAD_RECEIPT) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("go_to_upload_receipt_button")
            ) {
                Text("رفع إثبات الدفع (صورة الإيصال أو PDF)")
            }
        }

        item {
            OutlinedButton(
                onClick = { viewModel.navigateTo(UserScreenRoute.PAYMENT_HISTORY) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("عرض سجل طلبات الدفع ومتابعة حالة المراجعة")
            }
        }
    }
}

@Composable
fun UserPaymentInstructionsScreen(viewModel: UserViewModel) {
    val settings by viewModel.repository.appSettings.collectAsState()
    val selectedMethod by viewModel.selectedPaymentMethod.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("تعليمات التحويل البريدي (CCP) و BaridiMob", style = MaterialTheme.typography.headlineMedium)
            Text(
                "تُجلب هذه التعليمات مباشرة من جدول app_settings الذي تضبطه الإدارة. لا يتم عرض أي أرقام حسابات وهمية.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("تعليمات التحويل البريدي (CCP):", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(settings.ccpInstructionsAr)
                }
            }
        }

        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("تعليمات الدفع عبر بريدي موب (BaridiMob):", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(settings.baridimobInstructionsAr)
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.navigateTo(UserScreenRoute.UPLOAD_RECEIPT) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("الانتقال لرفع إثبات الدفع (${selectedMethod.labelAr})")
            }
        }
    }
}

@Composable
fun UserUploadReceiptScreen(viewModel: UserViewModel) {
    val context = LocalContext.current
    val settings by viewModel.repository.appSettings.collectAsState()
    val selectedMethod by viewModel.selectedPaymentMethod.collectAsState()

    var selectedFileName by remember { mutableStateOf("") }
    var selectedMimeType by remember { mutableStateOf("image/jpeg") }
    var selectedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var transactionRef by remember { mutableStateOf("") }
    var userNote by remember { mutableStateOf("") }

    // استخدام منتقي الملفات الرسمي في Android بدون طلب صلاحيات تخزين واسعة
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val cr = context.contentResolver
            val mime = cr.getType(uri) ?: "image/jpeg"
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) {
                selectedMimeType = mime
                selectedBytes = bytes
                selectedFileName = uri.lastPathSegment ?: "receipt.${mime.substringAfterLast('/')}"
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("رفع إثبات الدفع (${settings.listingFeeDzd} دج)", style = MaterialTheme.typography.headlineMedium)
            Text(
                "يتم حفظ الإيصال في حاوية خاصة (payment-receipts) غير متاحة للعامة، ويُسجل الطلب بحالة (قيد المراجعة - pending) حتى يراجعه المشرف.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            OutlinedButton(
                onClick = {
                    documentLauncher.launch(arrayOf("image/jpeg", "image/png", "image/webp", "application/pdf"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pick_receipt_file_button")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (selectedFileName.isEmpty()) "اختيار صورة الإيصال أو ملف PDF (بحد أقصى 5MB)" else "تم اختيار: $selectedFileName")
            }
        }

        item {
            OutlinedTextField(
                value = transactionRef,
                onValueChange = { transactionRef = it },
                label = { Text("رقم عملية التحويل أو رقم الوصل البريدي") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("receipt_transaction_ref_input")
            )
        }

        item {
            OutlinedTextField(
                value = userNote,
                onValueChange = { userNote = it },
                label = { Text("ملاحظة إضافية للمشرف (اختياري)") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = {
                    val bytes = selectedBytes ?: return@Button
                    viewModel.submitPaymentReceipt(
                        fileName = selectedFileName,
                        mimeType = selectedMimeType,
                        fileBytes = bytes,
                        transactionRef = transactionRef,
                        note = userNote
                    )
                },
                enabled = selectedBytes != null && transactionRef.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("submit_receipt_button")
            ) {
                Text("إرسال طلب الدفع للمراجعة (${selectedMethod.labelAr})")
            }
        }
    }
}

@Composable
fun UserTrackPaymentScreen(viewModel: UserViewModel) {
    val req by viewModel.selectedPaymentRequest.collectAsState()
    val item = req
    if (item == null) {
        UserPaymentHistoryScreen(viewModel)
        return
    }

    val statusEnum = PaymentStatus.fromDb(item.status)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("متابعة حالة طلب الدفع", style = MaterialTheme.typography.headlineMedium)
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("معرّف الطلب: ${item.id}", fontWeight = FontWeight.Bold)
                Text("المبلغ: ${item.amountDzd} دج")
                Text("وسيلة الدفع: ${PaymentMethodType.fromDb(item.paymentMethod).labelAr}")
                Text("الحالة الحالية: ${statusEnum.labelAr}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                if (!item.rejectionReason.isNullOrBlank()) {
                    Text("سبب الرفض من المشرف: ${item.rejectionReason}", color = MaterialTheme.colorScheme.error)
                }
                if (item.consumedListingId != null) {
                    Text("الإعلان المرتبط بهذه الدفعة: ${item.consumedListingId}")
                }
            }
        }
        if (item.status == "approved" && item.consumedListingId == null) {
            Button(
                onClick = { viewModel.navigateTo(UserScreenRoute.CREATE_LISTING) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("استخدام هذه الدفعة المعتمدة لنشر إعلان الآن")
            }
        }
        if (item.status == "rejected") {
            Button(
                onClick = { viewModel.navigateTo(UserScreenRoute.UPLOAD_RECEIPT) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("تقديم طلب دفع جديد")
            }
        }
    }
}

@Composable
fun UserPaymentHistoryScreen(viewModel: UserViewModel) {
    val payments by viewModel.repository.myPaymentRequests.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("سجل طلبات الدفع الخاصة بي", style = MaterialTheme.typography.headlineMedium)
        }
        if (payments.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("لا توجد طلبات دفع مسجلة في حسابك حتى الآن.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.navigateTo(UserScreenRoute.PAYMENT_FEE) }) {
                            Text("تقديم طلب دفع جديد (500 دج)")
                        }
                    }
                }
            }
        } else {
            items(payments, key = { it.id }) { req ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("طلب #${req.id.take(8)} • ${req.amountDzd} دج", fontWeight = FontWeight.Bold)
                        Text("الحالة: ${PaymentStatus.fromDb(req.status).labelAr}")
                        Text("الوسيلة: ${PaymentMethodType.fromDb(req.paymentMethod).labelAr}")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.selectPaymentRequest(req) }) {
                            Text("عرض تفاصيل ومتابعة الطلب")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserFavoritesScreen(viewModel: UserViewModel) {
    val favIds by viewModel.repository.favoriteListingIds.collectAsState()
    val listingsState by viewModel.repository.publicListingsState.collectAsState()
    val favItems = remember(favIds, listingsState) {
        val all = (listingsState as? dz.ocasionet.core.model.ResourceState.Success)?.data.orEmpty()
        all.filter { it.id in favIds }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("الإعلانات المفضلة", style = MaterialTheme.typography.headlineMedium)
        }
        if (favItems.isEmpty()) {
            item {
                Text("قائمة المفضلة فارغة حالياً.")
            }
        } else {
            items(favItems, key = { it.id }) { item ->
                ListingCardItem(
                    item = item,
                    isFavorite = true,
                    onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                    onClick = { viewModel.selectListing(item) }
                )
            }
        }
    }
}

@Composable
fun UserReportListingScreen(viewModel: UserViewModel) {
    val selected by viewModel.selectedListing.collectAsState()
    var reason by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("الإبلاغ عن إعلان مخالف", style = MaterialTheme.typography.headlineMedium)
        Text("الإعلان: ${selected?.title ?: "غير محدد"}")
        OutlinedTextField(
            value = reason,
            onValueChange = { reason = it },
            label = { Text("سبب البلاغ (مثال: محتوى مضلل، سعر غير حقيقي، سلعة ممنوعة)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = details,
            onValueChange = { details = it },
            label = { Text("تفاصيل إضافية للمشرفين") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val id = selected?.id ?: return@Button
                viewModel.submitReport(id, reason, details)
            },
            enabled = selected != null && reason.trim().length >= 5,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("إرسال البلاغ")
        }
    }
}

@Composable
fun UserNotificationsScreen(viewModel: UserViewModel) {
    val notifications by viewModel.repository.notifications.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("الإشعارات", style = MaterialTheme.typography.headlineMedium)
        }
        if (notifications.isEmpty()) {
            item {
                Text("لا توجد إشعارات جديدة في حسابك.")
            }
        } else {
            items(notifications, key = { it.id }) { n ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(n.titleAr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(n.bodyAr, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserSettingsScreen(viewModel: UserViewModel) {
    val activePalette by dz.ocasionet.core.ui.theme.OccasioNetPaletteStore.selectedPalette.collectAsState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("إعدادات تطبيق المستخدم (OccasioNet User)", style = MaterialTheme.typography.headlineMedium)
            Text("معرّف الحزمة المستقل: dz.ocasionet.user")
            Text("اللغة الافتراضية: العربية (دعم كامل لـ RTL)")
            Text("العملة المعتمدة: الدينار الجزائري (دج)")
        }
        item {
            Text("اختر لوحة الألوان المفضلة للتطبيق:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dz.ocasionet.core.ui.theme.AppColorPalette.entries.forEach { palette ->
                    FilterChip(
                        selected = activePalette == palette,
                        onClick = { dz.ocasionet.core.ui.theme.OccasioNetPaletteStore.selectPalette(palette) },
                        label = { Text(palette.titleAr) },
                        modifier = Modifier.testTag("palette_option_${palette.name}")
                    )
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.navigateTo(UserScreenRoute.TERMS_AND_PRIVACY) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("شروط الاستخدام وسياسة الخصوصية")
            }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.navigateTo(UserScreenRoute.SYSTEM_STATES) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("فحص حالات التحميل، الشبكة، وأمان RLS")
            }
        }
    }
}

@Composable
fun UserTermsAndPrivacyScreen(viewModel: UserViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("شروط الاستخدام وسياسة الخصوصية - OccasioNet", style = MaterialTheme.typography.headlineMedium)
            Text("1. تلتزم منصة OccasioNet بحماية بيانات المستخدمين في الجزائر عبر سياسات Row Level Security (RLS) في Supabase.")
            Text("2. إيصالات الدفع المرفوعة عبر CCP أو BaridiMob تُحفظ في حاوية خاصة (Private Bucket) ولا يمكن لأي مستخدم آخر الاطلاع عليها.")
            Text("3. رسوم نشر الإعلان (500 دج مبدئياً) تُراجع يدوياً من الإدارة، وتُستهلك كل دفعة معتمدة لنشر إعلان واحد فقط داخل معاملة ذرية.")
            Text("4. يمنع نشر أي سلع محظورة قانوناً في الجمهورية الجزائرية الديمقراطية الشعبية.")
        }
    }
}

@Composable
fun UserSystemStatesScreen(viewModel: UserViewModel) {
    val configStatus by viewModel.repository.configStatus.collectAsState()
    val listingsState by viewModel.repository.publicListingsState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("حالات الاتصال والبيانات", style = MaterialTheme.typography.headlineMedium)
        SupabaseConnectionStatusCard(configStatus = configStatus, onRetry = { viewModel.refreshPublicData() })
        Text("حالة جلب البيانات الحالية: ${listingsState::class.simpleName}")
        Button(onClick = { viewModel.refreshPublicData() }, modifier = Modifier.fillMaxWidth()) {
            Text("إعادة فحص الاتصال بـ Supabase")
        }
    }
}
