package dz.ocasionet.user.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dz.ocasionet.core.R
import dz.ocasionet.core.data.AlgeriaGeographyCatalog
import dz.ocasionet.core.model.ListingCondition
import dz.ocasionet.core.model.ListingItem
import dz.ocasionet.core.model.ResourceState
import dz.ocasionet.core.model.SupabaseConfigStatus
import dz.ocasionet.core.ui.theme.OccasioNetTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OccasioNetUserAppRoot(
    userViewModel: UserViewModel = viewModel(),
    initialDeepLinkUri: String? = null
) {
    val currentRoute by userViewModel.currentRoute.collectAsState()
    val currentUser by userViewModel.repository.currentUser.collectAsState()
    val feedback by userViewModel.feedbackBanner.collectAsState()
    val isBusy by userViewModel.isBusy.collectAsState()

    LaunchedEffect(initialDeepLinkUri) {
        if (!initialDeepLinkUri.isNullOrBlank()) {
            userViewModel.handleAuthCallbackDeepLink(initialDeepLinkUri)
        }
    }

    BackHandler(
        enabled = currentRoute != UserScreenRoute.HOME &&
            currentRoute != UserScreenRoute.SPLASH
    ) {
        if (currentRoute == UserScreenRoute.WELCOME_AUTH) {
            userViewModel.replaySplash()
        } else {
            userViewModel.navigateBack()
        }
    }

    OccasioNetTheme(isAdminTheme = false) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            topBar = {
                if (currentRoute != UserScreenRoute.SPLASH) {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_occasionet_logo),
                                    contentDescription = "شعار OccasioNet",
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Column {
                                    Text(
                                        text = currentRoute.titleAr,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Text(
                                        text = if (currentUser != null) "متصل: ${currentUser?.email}" else "وضع التصفح العام (زائر)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            if (currentRoute != UserScreenRoute.HOME && currentRoute != UserScreenRoute.WELCOME_AUTH) {
                                IconButton(
                                    onClick = { userViewModel.navigateBack() },
                                    modifier = Modifier.testTag("user_back_button")
                                ) {
                                    Icon(
                                        Icons.Default.ArrowBack,
                                        contentDescription = "رجوع",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { userViewModel.replaySplash() },
                                modifier = Modifier.testTag("user_replay_splash_button")
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "عرض شاشة البداية",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            IconButton(
                                onClick = { dz.ocasionet.core.ui.theme.OccasioNetPaletteStore.cycleNextPalette() },
                                modifier = Modifier.testTag("user_palette_button")
                            ) {
                                Icon(
                                    Icons.Default.Palette,
                                    contentDescription = "تبديل الألوان",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            IconButton(
                                onClick = { userViewModel.navigateTo(UserScreenRoute.NOTIFICATIONS) },
                                modifier = Modifier.testTag("user_notifications_button")
                            ) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = "الإشعارات",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            IconButton(
                                onClick = { userViewModel.navigateTo(UserScreenRoute.FAVORITES) },
                                modifier = Modifier.testTag("user_favorites_button")
                            ) {
                                Icon(
                                    Icons.Default.FavoriteBorder,
                                    contentDescription = "المفضلة",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            IconButton(
                                onClick = { userViewModel.navigateTo(UserScreenRoute.SETTINGS) },
                                modifier = Modifier.testTag("user_settings_button")
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "الإعدادات",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            titleContentColor = MaterialTheme.colorScheme.onPrimary,
                            actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            },
            bottomBar = {
                if (currentRoute != UserScreenRoute.SPLASH && currentRoute != UserScreenRoute.WELCOME_AUTH) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == UserScreenRoute.HOME || currentRoute == UserScreenRoute.VISITOR_BROWSE,
                            onClick = { userViewModel.navigateTo(UserScreenRoute.HOME) },
                            icon = { Icon(Icons.Default.Home, contentDescription = "الرئيسية") },
                            label = { Text("الرئيسية") },
                            modifier = Modifier.testTag("nav_user_home")
                        )
                        NavigationBarItem(
                            selected = currentRoute == UserScreenRoute.SEARCH_AND_FILTER || currentRoute == UserScreenRoute.FILTER_BY_DETAILS,
                            onClick = { userViewModel.navigateTo(UserScreenRoute.SEARCH_AND_FILTER) },
                            icon = { Icon(Icons.Default.Search, contentDescription = "البحث والتصفية") },
                            label = { Text("البحث") },
                            modifier = Modifier.testTag("nav_user_search")
                        )
                        NavigationBarItem(
                            selected = currentRoute in setOf(
                                UserScreenRoute.CREATE_LISTING,
                                UserScreenRoute.PAYMENT_FEE,
                                UserScreenRoute.PAYMENT_INSTRUCTIONS,
                                UserScreenRoute.UPLOAD_RECEIPT
                            ),
                            onClick = {
                                if (currentUser == null) {
                                    userViewModel.postFeedback("يجب تسجيل الدخول أولاً قبل نشر إعلان.")
                                    userViewModel.navigateTo(UserScreenRoute.SIGN_IN)
                                } else {
                                    userViewModel.navigateTo(UserScreenRoute.CREATE_LISTING)
                                }
                            },
                            icon = { Icon(Icons.Default.AddCircle, contentDescription = "نشر إعلان") },
                            label = { Text("نشر إعلان") },
                            modifier = Modifier.testTag("nav_user_create")
                        )
                        NavigationBarItem(
                            selected = currentRoute in setOf(
                                UserScreenRoute.MY_LISTINGS,
                                UserScreenRoute.EDIT_LISTING,
                                UserScreenRoute.MANAGE_LISTING_STATUS
                            ),
                            onClick = {
                                if (currentUser == null) {
                                    userViewModel.postFeedback("يرجى تسجيل الدخول لعرض إعلاناتك.")
                                    userViewModel.navigateTo(UserScreenRoute.SIGN_IN)
                                } else {
                                    userViewModel.navigateTo(UserScreenRoute.MY_LISTINGS)
                                }
                            },
                            icon = { Icon(Icons.Default.FilterList, contentDescription = "إعلاناتي") },
                            label = { Text("إعلاناتي") },
                            modifier = Modifier.testTag("nav_user_my_listings")
                        )
                        NavigationBarItem(
                            selected = currentRoute in setOf(
                                UserScreenRoute.PROFILE,
                                UserScreenRoute.SIGN_IN,
                                UserScreenRoute.SIGN_UP
                            ),
                            onClick = {
                                if (currentUser == null) {
                                    userViewModel.navigateTo(UserScreenRoute.SIGN_IN)
                                } else {
                                    userViewModel.navigateTo(UserScreenRoute.PROFILE)
                                }
                            },
                            icon = { Icon(Icons.Default.Person, contentDescription = "حسابي") },
                            label = { Text(if (currentUser == null) "دخول" else "حسابي") },
                            modifier = Modifier.testTag("nav_user_profile")
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
                            TextButton(onClick = { userViewModel.clearFeedback() }) {
                                Text("إغلاق")
                            }
                        }
                    }
                }

                if (isBusy) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(26.dp))
                    }
                }

                when (currentRoute) {
                    UserScreenRoute.SPLASH -> UserSplashScreen(userViewModel)
                    UserScreenRoute.WELCOME_AUTH -> UserWelcomeAuthScreen(userViewModel)
                    UserScreenRoute.HOME, UserScreenRoute.VISITOR_BROWSE -> UserHomeAndVisitorScreen(userViewModel)
                    UserScreenRoute.SEARCH_AND_FILTER, UserScreenRoute.FILTER_BY_DETAILS -> UserSearchAndFilterScreen(userViewModel)
                    UserScreenRoute.LISTING_DETAILS -> UserListingDetailsScreen(userViewModel)
                    UserScreenRoute.SIGN_UP -> UserSignUpScreen(userViewModel)
                    UserScreenRoute.SIGN_IN -> UserSignInScreen(userViewModel)
                    UserScreenRoute.EMAIL_CONFIRMATION, UserScreenRoute.RESEND_CONFIRMATION -> UserEmailConfirmationScreen(userViewModel)
                    UserScreenRoute.FORGOT_PASSWORD -> UserForgotPasswordScreen(userViewModel)
                    UserScreenRoute.RESET_PASSWORD -> UserResetPasswordScreen(userViewModel)
                    UserScreenRoute.PROFILE -> UserProfileScreen(userViewModel)
                    UserScreenRoute.EDIT_PROFILE -> UserEditProfileScreen(userViewModel)
                    UserScreenRoute.CREATE_LISTING -> UserCreateListingScreen(userViewModel)
                    UserScreenRoute.EDIT_LISTING, UserScreenRoute.MANAGE_LISTING_STATUS -> UserEditAndManageListingScreen(userViewModel)
                    UserScreenRoute.MY_LISTINGS -> UserMyListingsScreen(userViewModel)
                    UserScreenRoute.PAYMENT_FEE -> UserPaymentFeeScreen(userViewModel)
                    UserScreenRoute.PAYMENT_INSTRUCTIONS -> UserPaymentInstructionsScreen(userViewModel)
                    UserScreenRoute.UPLOAD_RECEIPT -> UserUploadReceiptScreen(userViewModel)
                    UserScreenRoute.TRACK_PAYMENT -> UserTrackPaymentScreen(userViewModel)
                    UserScreenRoute.PAYMENT_HISTORY -> UserPaymentHistoryScreen(userViewModel)
                    UserScreenRoute.FAVORITES -> UserFavoritesScreen(userViewModel)
                    UserScreenRoute.REPORT_LISTING -> UserReportListingScreen(userViewModel)
                    UserScreenRoute.NOTIFICATIONS -> UserNotificationsScreen(userViewModel)
                    UserScreenRoute.SETTINGS -> UserSettingsScreen(userViewModel)
                    UserScreenRoute.TERMS_AND_PRIVACY -> UserTermsAndPrivacyScreen(userViewModel)
                    UserScreenRoute.SYSTEM_STATES -> UserSystemStatesScreen(userViewModel)
                }
            }
        }
    }
}

@Composable
fun UserSplashScreen(viewModel: UserViewModel) {
    val splashLaunchCount by viewModel.splashLaunchCount.collectAsState()

    LaunchedEffect(splashLaunchCount) {
        if (splashLaunchCount == 0) {
            // انتظار اتصال بث المحاكي عند تشغيل الحزمة لأول مرة حتى تظهر شاشة البداية للمستخدم لمدة 5 ثوانٍ كاملة
            delay(4500L)
        }
        delay(5000L)
        viewModel.completeSplash()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F8FF))
            .testTag("user_splash_screen")
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_splash_occasionet),
            contentDescription = "خلفية OccasioNet",
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentScale = ContentScale.FillWidth
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Surface(
                color = Color(0xEB002B66),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "برمجة راهم محمد لمين",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("splash_developer_credit_text")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "جميع الحقوق محفوظة @2026",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFFFB74D),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("splash_copyright_text")
                    )
                }
            }
        }
    }
}

@Composable
fun UserWelcomeAuthScreen(viewModel: UserViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("user_welcome_auth_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_occasionet_logo),
            contentDescription = "شعار تطبيق OccasioNet",
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(28.dp))
                .clickable { viewModel.replaySplash() },
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "مرحباً بك في OccasioNet",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "منصة بيع وشراء الأشياء المستعملة في جميع ولايات الجزائر الـ 58",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.navigateTo(UserScreenRoute.SIGN_UP) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("welcome_signup_button")
        ) {
            Icon(Icons.Default.Person, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("التسجيل (إنشاء حساب جديد)", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.navigateTo(UserScreenRoute.SIGN_IN) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("welcome_signin_button")
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تسجيل الدخول", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.navigateTo(UserScreenRoute.HOME) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("welcome_browse_guest_button")
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تصفح الإعلانات دون تسجيل", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserHomeAndVisitorScreen(viewModel: UserViewModel) {
    val configStatus by viewModel.repository.configStatus.collectAsState()
    val listingsState by viewModel.repository.publicListingsState.collectAsState()
    val categories by viewModel.repository.categories.collectAsState()
    val settings by viewModel.repository.appSettings.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val favoriteIds by viewModel.repository.favoriteListingIds.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Image(
                        painter = painterResource(id = R.drawable.img_marketplace_banner),
                        contentDescription = "شعار سوق OccasioNet في الجزائر",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(155.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "سوق OccasioNet للأغراض المستعملة في الجزائر",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "تصفح مجاني للزوار • أنشئ إعلانك أولاً ثم أكمل دفع رسوم النشر (${settings.listingFeeDzd} دج) عبر CCP أو BaridiMob",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = { viewModel.navigateTo(UserScreenRoute.SEARCH_AND_FILTER) },
                                label = { Text("تصفية حسب الولاية والبلدية") },
                                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                                modifier = Modifier.testTag("home_filter_chip")
                            )
                            AssistChip(
                                onClick = { viewModel.navigateTo(UserScreenRoute.CREATE_LISTING) },
                                label = { Text("نشر إعلان جديد") },
                                leadingIcon = { Icon(Icons.Default.AddCircle, contentDescription = null) },
                                modifier = Modifier.testTag("home_create_listing_chip")
                            )
                            AssistChip(
                                onClick = { viewModel.navigateTo(UserScreenRoute.MY_LISTINGS) },
                                label = { Text("إعلاناتي") }
                            )
                        }
                    }
                }
            }
        }

        // الفئات السريعة
        item {
            Text("تصفح حسب الفئة", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter.categoryId == null,
                    onClick = { viewModel.updateFilter(categoryId = null) },
                    label = { Text("الكل") }
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = filter.categoryId == cat.id,
                        onClick = { viewModel.updateFilter(categoryId = cat.id) },
                        label = { Text(cat.nameAr) }
                    )
                }
            }
        }

        when (val st = listingsState) {
            is ResourceState.Loading, is ResourceState.Idle -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("جاري جلب الإعلانات المنشورة...")
                        }
                    }
                }
            }
            is ResourceState.Error -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("supabase_error_state_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = "تنبيه", tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "تنبيه الاتصال",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = st.messageAr,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.refreshPublicData() },
                                modifier = Modifier.testTag("retry_supabase_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("إعادة المحاولة")
                            }
                        }
                    }
                }
            }
            is ResourceState.Success -> {
                val filtered = viewModel.repository.filterListings(st.data, filter)
                if (filtered.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("empty_listings_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("لا توجد إعلانات منشورة مطابقة حالياً", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "بمجرد نشر إعلان جديد واعتماده سيظهر هنا فوراً.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { item ->
                        ListingCardItem(
                            item = item,
                            isFavorite = item.id in favoriteIds,
                            onToggleFavorite = { viewModel.toggleFavorite(item.id) },
                            onClick = { viewModel.selectListing(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SupabaseConnectionStatusCard(
    configStatus: SupabaseConfigStatus,
    onRetry: () -> Unit
) {
    // تم إخفاء بطاقة الاتصال التقنية بناءً على طلب المستخدم
}

@Composable
fun ListingCardItem(
    item: ListingItem,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("listing_card_${item.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "المفضلة",
                        tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "${item.priceDzd} دج",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${AlgeriaGeographyCatalog.getWilayaNameAr(item.wilayaCode)} • ${AlgeriaGeographyCatalog.getCommuneNameAr(item.communeId)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "الحالة: ${ListingCondition.fromDb(item.condition).labelAr}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserSearchAndFilterScreen(viewModel: UserViewModel) {
    val filter by viewModel.filter.collectAsState()
    val categories by viewModel.repository.categories.collectAsState()
    val wilayas by viewModel.repository.wilayas.collectAsState()
    val communes = remember(filter.wilayaCode) {
        viewModel.repository.communesForWilaya(filter.wilayaCode)
    }

    var minPriceText by remember(filter.minPriceDzd) { mutableStateOf(filter.minPriceDzd?.toString() ?: "") }
    var maxPriceText by remember(filter.maxPriceDzd) { mutableStateOf(filter.maxPriceDzd?.toString() ?: "") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedTextField(
                value = filter.query,
                onValueChange = { viewModel.updateFilter(query = it) },
                label = { Text("ابحث عن منتج (هاتف، حاسوب، سيارة، أثاث...)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_query_input")
            )
        }

        item {
            Text("تصفية حسب الفئة", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter.categoryId == null,
                    onClick = { viewModel.updateFilter(categoryId = null) },
                    label = { Text("جميع الفئات") }
                )
                categories.forEach { cat ->
                    FilterChip(
                        selected = filter.categoryId == cat.id,
                        onClick = { viewModel.updateFilter(categoryId = cat.id) },
                        label = { Text(cat.nameAr) }
                    )
                }
            }
        }

        item {
            Text("تصفية حسب حالة المنتج", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter.condition == null,
                    onClick = { viewModel.updateFilter(condition = null) },
                    label = { Text("الكل") }
                )
                ListingCondition.entries.forEach { cond ->
                    FilterChip(
                        selected = filter.condition == cond,
                        onClick = { viewModel.updateFilter(condition = cond) },
                        label = { Text(cond.labelAr) }
                    )
                }
            }
        }

        item {
            Text("نطاق السعر بالدينار الجزائري (دج)", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minPriceText,
                    onValueChange = {
                        minPriceText = it
                        viewModel.updateFilter(minPriceDzd = it.toLongOrNull())
                    },
                    label = { Text("السعر الأدنى (دج)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = maxPriceText,
                    onValueChange = {
                        maxPriceText = it
                        viewModel.updateFilter(maxPriceDzd = it.toLongOrNull())
                    },
                    label = { Text("السعر الأقصى (دج)") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Text("اختر الولاية (58 ولاية جزائرية)", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filter.wilayaCode == null,
                    onClick = { viewModel.updateFilter(wilayaCode = null, communeId = null) },
                    label = { Text("كل الولايات") }
                )
                wilayas.forEach { w ->
                    FilterChip(
                        selected = filter.wilayaCode == w.code,
                        onClick = { viewModel.updateFilter(wilayaCode = w.code, communeId = null) },
                        label = { Text("%02d - %s".format(w.code, w.nameAr)) },
                        modifier = Modifier.testTag("wilaya_chip_${w.code}")
                    )
                }
            }
        }

        if (filter.wilayaCode != null) {
            item {
                Text(
                    "اختر البلدية التابعة لولاية ${AlgeriaGeographyCatalog.getWilayaNameAr(filter.wilayaCode)}",
                    style = MaterialTheme.typography.titleMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filter.communeId == null,
                        onClick = { viewModel.updateFilter(communeId = null) },
                        label = { Text("كل بلديات الولاية") }
                    )
                    communes.forEach { c ->
                        FilterChip(
                            selected = filter.communeId == c.id,
                            onClick = { viewModel.updateFilter(communeId = c.id) },
                            label = { Text("${c.nameAr} (${c.postalCode})") },
                            modifier = Modifier.testTag("commune_chip_${c.id}")
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { viewModel.navigateTo(UserScreenRoute.HOME) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("apply_filter_button")
                ) {
                    Text("عرض النتائج")
                }
                OutlinedButton(
                    onClick = { viewModel.resetFilter() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("إعادة ضبط التصفية")
                }
            }
        }
    }
}

@Composable
fun UserListingDetailsScreen(viewModel: UserViewModel) {
    val item by viewModel.selectedListing.collectAsState()
    val currentUser by viewModel.repository.currentUser.collectAsState()

    val listing = item
    if (listing == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("لم يتم اختيار إعلان لعرض تفاصيله.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(listing.title, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "${listing.priceDzd} دج",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("الولاية: ${AlgeriaGeographyCatalog.getWilayaNameAr(listing.wilayaCode)}")
                    Text("البلدية: ${AlgeriaGeographyCatalog.getCommuneNameAr(listing.communeId)}")
                    Text("حالة المنتج: ${ListingCondition.fromDb(listing.condition).labelAr}")
                    Text("هاتف التواصل: ${listing.contactPhone.ifBlank { "متاح عند المراسلة" }}")
                    if (listing.imageUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "صور الإعلان المرفقة (${listing.imageUrls.size}):",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        listing.imageUrls.forEachIndexed { idx, url ->
                            Text(
                                text = "• صورة #${idx + 1}: ${url.substringAfterLast('/')}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("الوصف التفصيلي:", style = MaterialTheme.typography.titleMedium)
                    Text(listing.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { viewModel.toggleFavorite(listing.id) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حفظ في المفضلة")
                }
                OutlinedButton(
                    onClick = { viewModel.navigateTo(UserScreenRoute.REPORT_LISTING) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("report_listing_button")
                ) {
                    Icon(Icons.Default.Flag, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("الإبلاغ عن الإعلان")
                }
            }
        }

        if (currentUser?.id == listing.sellerId) {
            item {
                Button(
                    onClick = { viewModel.navigateTo(UserScreenRoute.EDIT_LISTING) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تعديل إعلاني أو تغيير حالته (مباع / موقوف)")
                }
            }
        }
    }
}

@Composable
fun UserSignInScreen(viewModel: UserViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("تسجيل الدخول إلى حسابك في OccasioNet", style = MaterialTheme.typography.headlineMedium)
            Text(
                "سجّل دخولك لإدارة إعلاناتك وطلبات الدفع والمفضلة.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("البريد الإلكتروني") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_login_email_input")
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
                    .testTag("user_login_password_input")
            )
        }
        item {
            Button(
                onClick = { viewModel.signIn(email, password) },
                enabled = email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_login_submit_button")
            ) {
                Text("تسجيل الدخول")
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { viewModel.navigateTo(UserScreenRoute.SIGN_UP) }) {
                    Text("إنشاء حساب جديد")
                }
                TextButton(onClick = { viewModel.navigateTo(UserScreenRoute.FORGOT_PASSWORD) }) {
                    Text("نسيت كلمة المرور؟")
                }
            }
        }
    }
}

@Composable
fun UserSignUpScreen(viewModel: UserViewModel) {
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("إنشاء حساب مستخدم جديد", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("الاسم الكامل") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("رقم الهاتف الجزائري (مثال: 0550123456)") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("البريد الإلكتروني") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("كلمة المرور (8 أحرف على الأقل)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Button(
                onClick = { viewModel.signUp(email, password, fullName, phone) },
                enabled = email.isNotBlank() && password.length >= 8 && fullName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_signup_submit_button")
            ) {
                Text("إنشاء الحساب")
            }
        }
    }
}

@Composable
fun UserEmailConfirmationScreen(viewModel: UserViewModel) {
    var email by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text("تأكيد البريد الإلكتروني وإعادة الإرسال", style = MaterialTheme.typography.headlineMedium)
        Text(
            "يرجى فتح الرابط المرسل إلى بريدك الإلكتروني لتأكيد حسابك في Supabase Auth. إذا لم تصلك الرسالة، يمكنك إعادة إرسالها من هنا:",
            style = MaterialTheme.typography.bodyLarge
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("أدخل بريدك الإلكتروني لإعادة إرسال التأكيد") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.resendConfirmation(email) },
            enabled = email.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("إعادة إرسال رسالة التأكيد")
        }
        OutlinedButton(
            onClick = { viewModel.navigateTo(UserScreenRoute.SIGN_IN) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("العودة إلى شاشة تسجيل الدخول")
        }
    }
}

@Composable
fun UserForgotPasswordScreen(viewModel: UserViewModel) {
    var email by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("استعادة كلمة المرور", style = MaterialTheme.typography.headlineMedium)
        Text("أدخل بريدك الإلكتروني المسجل لإرسال رابط استعادة كلمة المرور عبر Supabase Auth.")
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("البريد الإلكتروني") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.recoverPassword(email) },
            enabled = email.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("إرسال رابط الاستعادة")
        }
        TextButton(onClick = { viewModel.navigateTo(UserScreenRoute.RESET_PASSWORD) }) {
            Text("الانتقال إلى شاشة تعيين كلمة مرور جديدة")
        }
    }
}

@Composable
fun UserResetPasswordScreen(viewModel: UserViewModel) {
    var newPassword by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("إعادة تعيين كلمة المرور", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("كلمة المرور الجديدة (8 أحرف على الأقل)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { viewModel.resetPassword(newPassword) },
            enabled = newPassword.length >= 8,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("حفظ كلمة المرور الجديدة")
        }
    }
}

@Composable
fun UserProfileScreen(viewModel: UserViewModel) {
    val user by viewModel.repository.currentUser.collectAsState()
    val profile = user
    if (profile == null) {
        UserSignInScreen(viewModel)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(profile.fullName.ifBlank { "مستخدم OccasioNet" }, style = MaterialTheme.typography.headlineMedium)
                    Text("البريد الإلكتروني: ${profile.email}")
                    Text("الهاتف: ${profile.phone.ifBlank { "غير محدد" }}")
                    Text("الولاية: ${AlgeriaGeographyCatalog.getWilayaNameAr(profile.wilayaCode)}")
                    Text("البلدية: ${AlgeriaGeographyCatalog.getCommuneNameAr(profile.communeId)}")
                }
            }
        }
        item {
            Button(
                onClick = { viewModel.navigateTo(UserScreenRoute.EDIT_PROFILE) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("تعديل بيانات المستخدم")
            }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.navigateTo(UserScreenRoute.MY_LISTINGS) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("قائمة إعلاناتي وإدارة حالتها")
            }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.navigateTo(UserScreenRoute.PAYMENT_HISTORY) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("سجل طلبات الدفع ومتابعة حالتها")
            }
        }
        item {
            OutlinedButton(
                onClick = { viewModel.signOut() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("user_logout_button")
            ) {
                Text("تسجيل الخروج")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserEditProfileScreen(viewModel: UserViewModel) {
    val user by viewModel.repository.currentUser.collectAsState()
    var fullName by remember(user) { mutableStateOf(user?.fullName ?: "") }
    var phone by remember(user) { mutableStateOf(user?.phone ?: "") }
    var selectedWilaya by remember(user) { mutableStateOf(user?.wilayaCode ?: 16) }
    val communes = remember(selectedWilaya) { viewModel.repository.communesForWilaya(selectedWilaya) }
    var selectedCommune by remember(selectedWilaya) {
        mutableStateOf(user?.communeId ?: communes.firstOrNull()?.id)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("تعديل بيانات المستخدم", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("الاسم الكامل") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("رقم الهاتف") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text("الولاية:", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AlgeriaGeographyCatalog.wilayas.take(16).forEach { w ->
                    FilterChip(
                        selected = selectedWilaya == w.code,
                        onClick = {
                            selectedWilaya = w.code
                            selectedCommune = viewModel.repository.communesForWilaya(w.code).firstOrNull()?.id
                        },
                        label = { Text("${w.code} - ${w.nameAr}") }
                    )
                }
            }
        }
        item {
            Text("البلدية:", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                communes.forEach { c ->
                    FilterChip(
                        selected = selectedCommune == c.id,
                        onClick = { selectedCommune = c.id },
                        label = { Text(c.nameAr) }
                    )
                }
            }
        }
        item {
            Button(
                onClick = { viewModel.updateProfile(fullName, phone, selectedWilaya, selectedCommune) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("حفظ التعديلات")
            }
        }
    }
}
