package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import com.example.R
import com.example.BuildConfig
import com.example.data.db.CustomErrorEntity
import com.example.data.db.SavedErrorEntity
import com.example.data.model.*
import com.example.ui.AssistantViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

// Custom Theme Colors matching Codyar HTML
val CodyarNavy = Color(0xFF1C2B4A)
val CodyarRed = Color(0xFFE0393E)
val CodyarBg = Color(0xFFF7F8FA)

val voiceWordMapping = mapOf(
    "صفر" to "0",
    "یک" to "1",
    "دو" to "2",
    "سه" to "3",
    "چهار" to "4",
    "پنج" to "5",
    "شش" to "6",
    "شیش" to "6",
    "هفت" to "7",
    "هشت" to "8",
    "نه" to "9",
    "ده" to "10",
    "ای" to "e",
    "اِی" to "e",
    "یی" to "e",
    "اف" to "f",
    "اِف" to "f",
    "پی" to "p",
    "دی" to "d",
    "سی" to "c",
    "اچ" to "h",
    "اِچ" to "h",
    "یو" to "u",
    "ال" to "l",
    "ار" to "r",
    "او" to "o",
    "تی" to "t",
    "آی" to "i",
    "س" to "c",
    "ف" to "f",
    "پ" to "p"
)

fun normalizeVoiceSearchText(text: String): String {
    if (text.isEmpty()) return ""
    
    // Convert Persian digits to English digits
    var normalized = text
        .replace('۰', '0')
        .replace('۱', '1')
        .replace('۲', '2')
        .replace('۳', '3')
        .replace('۴', '4')
        .replace('۵', '5')
        .replace('۶', '6')
        .replace('۷', '7')
        .replace('۸', '8')
        .replace('۹', '9')
    
    val noiseWords = listOf("ارور", "خطای", "خطا", "کد", "کدهای")
    val words = normalized.split(Regex("\\s+"))
    val resultWords = mutableListOf<String>()
    
    for (word in words) {
        val cleanWord = word.trim()
        if (cleanWord.isEmpty() || noiseWords.contains(cleanWord)) {
            continue
        }
        
        val mapped = voiceWordMapping[cleanWord.lowercase()]
        if (mapped != null) {
            resultWords.add(mapped)
        } else {
            resultWords.add(cleanWord)
        }
    }
    
    val sb = java.lang.StringBuilder()
    for (i in resultWords.indices) {
        val current = resultWords[i]
        sb.append(current)
        if (i < resultWords.size - 1) {
            val next = resultWords[i + 1]
            val currentIsShort = current.length == 1 || (current.all { it.isDigit() || (it in 'a'..'z') || (it in 'A'..'Z') })
            val nextIsShort = next.length == 1 || (next.all { it.isDigit() || (it in 'a'..'z') || (it in 'A'..'Z') })
            if (!(currentIsShort && nextIsShort)) {
                sb.append(" ")
            }
        }
    }
    return sb.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onPurchasePlan: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                val normalizedText = normalizeVoiceSearchText(spokenText)
                viewModel.updateSearchFilters(normalizedText, viewModel.selectedBrand.value, viewModel.selectedCategory.value)
            }
        }
    }

    // Screen navigation state
    // "home", "search", "problems", "store", "profile", "technicians", "orders", "ai_chat"
    var activeTab by remember { mutableStateOf("home") }
    var showSplashScreen by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Absolute maximum splash screen duration of 5 seconds to prevent being stuck on slow connections
        kotlinx.coroutines.delay(5000)
        showSplashScreen = false
    }

    // Dialog & Sheet states
    var showAuthDialog by remember { mutableStateOf(false) }
    var authMode by remember { mutableStateOf("login") } // "login" or "register"

    var showCartDialog by remember { mutableStateOf(false) }
    var cartStep by remember { mutableStateOf("cart") } // "cart" or "payment"

    var showPlansDialog by remember { mutableStateOf(false) }
    var showPayDialog by remember { mutableStateOf(false) }
    var selectedPlanForPay by remember { mutableStateOf<Map<String, Any>?>(null) }

    // Detail view states
    var selectedErrorDetail by remember { mutableStateOf<KodyarErrorCode?>(null) }
    var selectedProblemDetail by remember { mutableStateOf<KodyarCommonProblem?>(null) }

    // Restricted access dialogs
    var showRegisterRequiredDialog by remember { mutableStateOf(false) }
    var showPremiumRequiredDialog by remember { mutableStateOf(false) }

    // Form inputs
    var authPhone by remember { mutableStateOf("") }
    var authPassword by remember { mutableStateOf("") }
    var authName by remember { mutableStateOf("") }
    var authRole by remember { mutableStateOf("customer") } // "customer" or "technician"
    var authCity by remember { mutableStateOf("تهران") }
    var authSelectedCategories by remember { mutableStateOf(setOf<String>()) }

    var cartAddress by remember { mutableStateOf("") }
    var cartNotes by remember { mutableStateOf("") }
    var cartCardHolder by remember { mutableStateOf("") }
    var cartTrackNumber by remember { mutableStateOf("") }

    var payCardHolder by remember { mutableStateOf("") }
    var payTrackNumber by remember { mutableStateOf("") }

    // Live variables from ViewModel
    val currentUser by viewModel.currentUser.collectAsState()
    val isDatabaseLoading by viewModel.isDatabaseLoading.collectAsState()
    LaunchedEffect(isDatabaseLoading) {
        if (!isDatabaseLoading) {
            // Dismiss after a nice, short delay once database is loaded (cache or network)
            kotlinx.coroutines.delay(1200)
            showSplashScreen = false
        }
    }
    val isAuthLoading by viewModel.isAuthLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()

    val liveSpareParts by viewModel.liveSpareParts.collectAsState()
    val liveTechnicians by viewModel.liveTechnicians.collectAsState()
    val liveCommonProblems by viewModel.liveCommonProblems.collectAsState()

    val cartItemsList by viewModel.cart.collectAsState()
    val cartQtyMap by viewModel.cartQty.collectAsState()
    val isPurchaseLoading by viewModel.isPurchaseLoading.collectAsState()
    val purchaseSuccess by viewModel.purchaseSuccess.collectAsState()

    val repairOrders by viewModel.repairOrders.collectAsState()
    val isRepairsLoading by viewModel.isRepairsLoading.collectAsState()

    val isCardVerifyLoading by viewModel.isCardVerifyLoading.collectAsState()
    val cardVerifySuccess by viewModel.cardVerifySuccess.collectAsState()

    val freeErrorCount by viewModel.freeErrorCount.collectAsState()
    val freeProblemCount by viewModel.freeProblemCount.collectAsState()

    val subscriptionPlans by viewModel.subscriptionPlans.collectAsState()
    val isPlansLoading by viewModel.isPlansLoading.collectAsState()
    val appUpdateNotification by viewModel.appUpdateNotification.collectAsState()

    LaunchedEffect(showPlansDialog) {
        if (showPlansDialog) {
            viewModel.fetchSubscriptionPlans()
        }
    }

    val isPremium = currentUser?.subscription?.is_premium == true

    // Trigger RTL context for Persian/Arabic UI
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = modifier
                    .fillMaxSize()
                    .background(CodyarBg),
            topBar = {
                Surface(
                    color = CodyarNavy,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(CodyarRed, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "کدیار۲۴",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "لوازم خانگی",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isPremium) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFC9A227), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Text(
                                            text = "ویژه",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // Cart Button
                            Box {
                                IconButton(
                                    onClick = {
                                        if (cartItemsList.isNotEmpty()) {
                                            cartStep = "cart"
                                            showCartDialog = true
                                        } else {
                                            activeTab = "store"
                                        }
                                    },
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                        .size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ShoppingCart,
                                        contentDescription = "سبد خرید",
                                        tint = Color.White,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                if (cartItemsList.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .offset(x = (-3).dp, y = (-3).dp)
                                            .background(CodyarRed, CircleShape)
                                            .size(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cartItemsList.size.toString(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // Profile Button
                            IconButton(
                                onClick = {
                                    if (currentUser != null) {
                                        activeTab = "profile"
                                    } else {
                                        authMode = "login"
                                        showAuthDialog = true
                                    }
                                },
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "پروفایل",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            },
           bottomBar = {
                // منوی ناوبری مدرن با گوشه‌های کاملاً گرد، دایره‌های هم‌اندازه و ارتفاع بسیار ظریف
                Surface(
                    color = Color.White,
                    tonalElevation = 0.dp, // غیرفعال کردن سایه رنگی خودکار اندروید جهت سفید ماندن پس‌زمینه
                    shadowElevation = 4.dp, // افکت سایه ملایم و شیک بالا
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp), // گرد کردن گوشه‌های بالای منو
                    border = BorderStroke(1.dp, Color(0xFFEAECEF).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding() // اعمال پدینگ سیستمی بر روی ردیف داخلی جهت چسبیدن پس‌زمینه سفید به پایین گوشی
                            .padding(top = 2.dp, bottom = 0.dp) // پدینگ عمودی بسیار فشرده برای کاهش ارتفاع کلی بدون ایجاد فاصله خالی
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val navigationItems = listOf(
                            Triple("home", Icons.Default.Home, "خانه"),
                            Triple("search", Icons.Default.Search, "کد خطا"),
                            Triple("problems", Icons.Default.Warning, "مشکلات"),
                            Triple("store", Icons.Default.ShoppingCart, "فروشگاه"),
                            Triple("profile", Icons.Default.Person, "پروفایل")
                        )

                        navigationItems.forEach { (id, icon, label) ->
                            val active = activeTab == id || (id == "profile" && activeTab == "orders") || (id == "search" && activeTab == "technicians")
                            val itemColor = if (active) CodyarRed else Color(0xFF4A5568)

                            Column(
                                modifier = Modifier
                                    .weight(1f) // تقسیم متوازن عرض برای تمام آیتم‌ها به یک اندازه
                                    .clip(RoundedCornerShape(12.dp)) // اصلاح شکل لمس کلیدها به حالت دایره‌ای/گرد
                                    .clickable {
                                        selectedErrorDetail = null
                                        selectedProblemDetail = null
                                        activeTab = id
                                    }
                                    .padding(vertical = 2.dp), // فشرده‌سازی مناسب و ظریف پدینگ عمودی برای ارتفاع کلی کم
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp) // اندازه بهینه و استاندارد دایره پس‌زمینه آیکون‌ها
                                        .clip(CircleShape)
                                        .background(if (active) CodyarRed.copy(alpha = 0.12f) else Color.Transparent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = itemColor,
                                        modifier = Modifier.size(22.dp) // اندازه کاملا خوانا و مناسب آیکون داخلی
                                    )
                                }
                                Text(
                                    text = label,
                                    fontSize = 10.sp, // قلم استاندارد و کاملاً خوانا
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = itemColor,
                                    modifier = Modifier.padding(top = 2.dp) // فاصله ظریف و مناسب تا دایره بالا
                                )
                            }
                        }
                    }
                }
            }) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(CodyarBg)
            ) {
                when (activeTab) {
                        "home" -> HomeScreen(
                            viewModel = viewModel,
                            onNavigateToSearch = { activeTab = "search" },
                            onNavigateToTechnicians = { activeTab = "technicians" },
                            onNavigateToStore = { activeTab = "store" },
                            onShowPlans = { showPlansDialog = true },
                            onOpenErrorCode = { err ->
                                if (currentUser == null) {
                                    showRegisterRequiredDialog = true
                                } else {
                                    val viewedSet = viewModel.getUniqueErrorCodesViewed()
                                    val codeKey = "${err.brand}_${err.category}_${err.code}"
                                    if (isPremium || viewedSet.contains(codeKey) || viewedSet.size < 2) {
                                        viewModel.recordErrorCodeView(codeKey)
                                        selectedErrorDetail = err
                                        activeTab = "search"
                                    } else {
                                        showPremiumRequiredDialog = true
                                    }
                                }
                            }
                        )
                        "search" -> SearchScreen(
                            viewModel = viewModel,
                            selectedErrorDetail = selectedErrorDetail,
                            onSelectError = { err ->
                                if (currentUser == null) {
                                    showRegisterRequiredDialog = true
                                } else {
                                    val viewedSet = viewModel.getUniqueErrorCodesViewed()
                                    val codeKey = "${err.brand}_${err.category}_${err.code}"
                                    if (isPremium || viewedSet.contains(codeKey) || viewedSet.size < 2) {
                                        viewModel.recordErrorCodeView(codeKey)
                                        selectedErrorDetail = err
                                    } else {
                                        showPremiumRequiredDialog = true
                                    }
                                }
                            },
                            onBack = { selectedErrorDetail = null },
                            onNavigateToTechnicians = { activeTab = "technicians" },
                            onNavigateToStore = { activeTab = "store" },
                            isPremium = isPremium,
                            freeErrorCount = freeErrorCount,
                            onShowPlans = { showPlansDialog = true }
                        )
                        "problems" -> ProblemsScreen(
                            viewModel = viewModel,
                            liveProblems = viewModel.liveCommonProblems,
                            selectedProblemDetail = selectedProblemDetail,
                            onSelectProblem = { prob ->
                                if (currentUser == null) {
                                    showRegisterRequiredDialog = true
                                } else {
                                    val viewedSet = viewModel.getUniqueProblemsViewed()
                                    val problemKey = "${prob.brand}_${prob.category}_${prob.title}"
                                    if (isPremium || viewedSet.contains(problemKey) || viewedSet.size < 1) {
                                        viewModel.recordProblemView(problemKey)
                                        selectedProblemDetail = prob
                                    } else {
                                        showPremiumRequiredDialog = true
                                    }
                                }
                            },
                            onBack = { selectedProblemDetail = null },
                            onNavigateToTechnicians = { activeTab = "technicians" },
                            isPremium = isPremium,
                            freeProblemCount = freeProblemCount,
                            onShowPlans = { showPlansDialog = true }
                        )
                        "store" -> StoreScreen(
                            viewModel = viewModel,
                            parts = liveSpareParts,
                            cartItems = cartItemsList,
                            onAddToCart = { viewModel.addToCart(it) }
                        )
                        "profile" -> ProfileScreen(
                            viewModel = viewModel,
                            currentUser = currentUser,
                            onShowAuth = {
                                authMode = "login"
                                showAuthDialog = true
                            },
                            onShowPlans = { showPlansDialog = true },
                            onNavigateToOrders = {
                                viewModel.loadRepairs()
                                activeTab = "orders"
                            },
                            onNavigateToSaved = { activeTab = "search" }
                        )
                        "technicians" -> TechniciansScreen(
                            viewModel = viewModel,
                            liveTechs = viewModel.liveTechnicians,
                            currentUser = currentUser,
                            onShowAuth = {
                                authMode = "login"
                                showAuthDialog = true
                            }
                        )
                        "orders" -> OrdersScreen(
                            viewModel = viewModel,
                            repairOrders = repairOrders,
                            isRepairsLoading = isRepairsLoading,
                            onBack = { activeTab = "profile" },
                            onNavigateToTechs = { activeTab = "technicians" }
                        )
                        "ai_chat" -> AiChatScreen(viewModel = viewModel)
                    }
                }
            }

        // --- AUTH DIALOG ---
        if (showAuthDialog) {
            AlertDialog(
                onDismissRequest = { showAuthDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (authMode == "login") "ورود به حساب کاربری" else "ثبت‌نام حساب جدید",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                        IconButton(onClick = { showAuthDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Toggle bar for Login / Register
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFF0F2F5), RoundedCornerShape(9.dp))
                                .padding(3.dp)
                        ) {
                            Button(
                                onClick = { authMode = "login" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (authMode == "login") Color.White else Color.Transparent,
                                    contentColor = if (authMode == "login") Color(0xFF1A1A2E) else Color(0xFF4A5568)
                                ),
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("ورود", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Button(
                                onClick = { authMode = "register" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (authMode == "register") Color.White else Color.Transparent,
                                    contentColor = if (authMode == "register") Color(0xFF1A1A2E) else Color(0xFF4A5568)
                                ),
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text("ثبت‌نام", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        // Sub-toggle for Customer / Technician registration
                        if (authMode == "register") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF8FAFC), RoundedCornerShape(9.dp))
                                    .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(9.dp))
                                    .padding(3.dp)
                            ) {
                                Button(
                                    onClick = { authRole = "customer" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (authRole == "customer") CodyarRed else Color.Transparent,
                                        contentColor = if (authRole == "customer") Color.White else Color(0xFF64748B)
                                    ),
                                    shape = RoundedCornerShape(7.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text("ثبت‌نام مشتری", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Button(
                                    onClick = { authRole = "technician" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (authRole == "technician") CodyarNavy else Color.Transparent,
                                        contentColor = if (authRole == "technician") Color.White else Color(0xFF64748B)
                                    ),
                                    shape = RoundedCornerShape(7.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    Text("ثبت‌نام تکنسین", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }

                        if (authMode == "register") {
                            OutlinedTextField(
                                value = authName,
                                onValueChange = { authName = it },
                                placeholder = { Text("نام و نام خانوادگی") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(9.dp),
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = authPhone,
                            onValueChange = { authPhone = it },
                            placeholder = { Text("شماره موبایل (مثلا: 0912...)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(9.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )

                        OutlinedTextField(
                            value = authPassword,
                            onValueChange = { authPassword = it },
                            placeholder = { Text("رمز عبور") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(9.dp),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )

                        // City field for both customer and technician registration
                        if (authMode == "register") {
                            OutlinedTextField(
                                value = authCity,
                                onValueChange = { authCity = it },
                                placeholder = { Text(if (authRole == "technician") "شهر محل فعالیت (مثلا: اراک)" else "شهر محل سکونت (مثلا: اراک)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(9.dp),
                                singleLine = true
                            )
                        }

                        // Technician-specific fields (categories)
                        if (authMode == "register" && authRole == "technician") {

                            val availableCategories = listOf("ماشین لباسشویی", "ماشین ظرفشویی", "یخچال و فریزر", "مایکروویو", "جاروبرقی", "کولر گازی")
                            Text("انتخاب تخصص‌ها:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CodyarNavy)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                availableCategories.forEach { cat ->
                                    val isSelected = authSelectedCategories.contains(cat)
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (isSelected) CodyarRed else Color(0xFFF1F5F9),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) CodyarRed else Color(0xFFCBD5E1),
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                authSelectedCategories = if (isSelected) {
                                                    authSelectedCategories - cat
                                                } else {
                                                    authSelectedCategories + cat
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSelected) Color.White else Color(0xFF475569),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (authError != null) {
                            Text(
                                text = authError ?: "",
                                color = CodyarRed,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFDF0EE), RoundedCornerShape(7.dp))
                                    .padding(8.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (authPhone.isBlank() || authPassword.isBlank()) {
                                Toast.makeText(context, "لطفاً تمام موارد را پر کنید", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (authMode == "login") {
                                viewModel.login(authPhone, authPassword) { success, err ->
                                    if (success) {
                                        showAuthDialog = false
                                        Toast.makeText(context, "خوش آمدید!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } else {
                                if (authName.isBlank()) {
                                    Toast.makeText(context, "نام الزامی است", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (authCity.isBlank()) {
                                    Toast.makeText(context, "لطفاً شهر خود را وارد کنید", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (authRole == "technician" && authSelectedCategories.isEmpty()) {
                                    Toast.makeText(context, "لطفاً حداقل یک تخصص انتخاب کنید", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.register(
                                    phone = authPhone,
                                    pass = authPassword,
                                    name = authName,
                                    role = authRole,
                                    city = authCity.trim(),
                                    categories = if (authRole == "technician") authSelectedCategories.toList() else null
                                ) { success, err ->
                                    if (success) {
                                        showAuthDialog = false
                                        val welcomeMsg = if (authRole == "technician") {
                                            "ثبت‌نام تکنسین با موفقیت انجام شد! حساب شما فعال است."
                                        } else {
                                            "ثبت‌نام مشتری با موفقیت انجام شد!"
                                        }
                                        Toast.makeText(context, welcomeMsg, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_button"),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isAuthLoading
                    ) {
                        if (isAuthLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                text = if (authMode == "login") "ورود به حساب" else if (authRole == "technician") "ثبت‌نام تکنسین" else "ثبت‌نام مشتری",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        }

        // --- CART DIALOG ---
        if (showCartDialog) {
            AlertDialog(
                onDismissRequest = { showCartDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (cartStep == "cart") "سبد خرید قطعات شما" else "پرداخت کارت به کارت",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                        IconButton(onClick = { showCartDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }
                },
                text = {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!purchaseSuccess) {
                            if (cartStep == "cart") {
                                // Cart item list
                                cartItemsList.forEach { partId ->
                                    val part = liveSpareParts.find { it.id == partId }
                                    if (part != null) {
                                        val qty = cartQtyMap[partId] ?: 1
                                        val subtotal = (part.price ?: 0.0) * qty

                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F8FA)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(10.dp),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .background(Color.White, RoundedCornerShape(8.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val finalImg = part.image ?: part.imageUrl ?: ""
                                                    if (finalImg.isNotEmpty()) {
                                                        AsyncImage(
                                                            model = if (finalImg.startsWith("http")) finalImg else "https://kodyar24.ir/${finalImg.removePrefix("/")}",
                                                            contentDescription = part.name,
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .clip(RoundedCornerShape(8.dp))
                                                        )
                                                    } else {
                                                        Text("⚙️", fontSize = 24.sp)
                                                    }
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        part.name ?: "",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = Color(0xFF1A1A2E)
                                                    )
                                                    Text(
                                                        "${formatToman(part.price ?: 0.0)} تومان",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF4A5568)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        IconButton(
                                                            onClick = { viewModel.updateCartQty(partId, qty - 1) },
                                                            modifier = Modifier
                                                                .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(5.dp))
                                                                .size(26.dp)
                                                        ) {
                                                            Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        Text(
                                                            text = qty.toString(),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            modifier = Modifier.width(22.dp),
                                                            textAlign = TextAlign.Center
                                                        )
                                                        IconButton(
                                                            onClick = { viewModel.updateCartQty(partId, qty + 1) },
                                                            modifier = Modifier
                                                                .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(5.dp))
                                                                .size(26.dp)
                                                        ) {
                                                            Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        Spacer(modifier = Modifier.weight(1f))

                                                        TextButton(
                                                            onClick = { viewModel.removeFromCart(partId) },
                                                            colors = ButtonDefaults.textButtonColors(contentColor = CodyarRed)
                                                        ) {
                                                            Text("حذف", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Total row
                                val total = cartItemsList.sumOf { partId ->
                                    val part = liveSpareParts.find { it.id == partId }
                                    val qty = cartQtyMap[partId] ?: 1
                                    (part?.price ?: 0.0) * qty
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF7F8FA), RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("جمع کل اقلام:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        "${formatToman(total)} تومان",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = CodyarRed
                                    )
                                }

                                // Form inputs
                                Text("آدرس دقیق تحویل قطعه *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = cartAddress,
                                    onValueChange = { cartAddress = it },
                                    placeholder = { Text("استان، شهر، خیابان، پلاک، واحد...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(9.dp),
                                    maxLines = 3
                                )

                                Text("توضیحات سفارش (اختیاری)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = cartNotes,
                                    onValueChange = { cartNotes = it },
                                    placeholder = { Text("مثلاً: ساعت تحویل یا توضیحات اضافه") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(9.dp),
                                    singleLine = true
                                )
                            } else {
                                // Payment Step
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = CodyarNavy)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text("شماره کارت جهت کارت به کارت", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                        Text("6104-3389-6112-6667", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
                                        Text("به نام: مهدی عباسی (مدیریت کدیار۲۴)", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                                    }
                                }

                                Text("نام صاحب کارت واریزکننده *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = cartCardHolder,
                                    onValueChange = { cartCardHolder = it },
                                    placeholder = { Text("نام کامل صاحب کارت بانکی") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(9.dp),
                                    singleLine = true
                                )

                                Text("شماره پیگیری تراکنش / فیش پرداخت *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                OutlinedTextField(
                                    value = cartTrackNumber,
                                    onValueChange = { cartTrackNumber = it },
                                    placeholder = { Text("شماره ارجاع، پیگیری یا شماره فیش تراکنش") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(9.dp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }
                        } else {
                            // Success state
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(Color(0xFFEAFAF1), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✅", fontSize = 34.sp)
                                }
                                Text("سفارش شما با موفقیت ثبت شد!", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1A1A2E))
                                Text(
                                    "فیش واریزی شما ثبت شد و پس از بررسی و تایید مدیریت در سریع‌ترین زمان قطعه برای شما ارسال می‌گردد.",
                                    fontSize = 13.sp,
                                    color = Color(0xFF374151),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!purchaseSuccess) {
                        Button(
                            onClick = {
                                if (cartStep == "cart") {
                                    if (cartAddress.isBlank()) {
                                        Toast.makeText(context, "وارد کردن آدرس تحویل الزامی است", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (currentUser == null) {
                                        showCartDialog = false
                                        authMode = "login"
                                        showAuthDialog = true
                                        Toast.makeText(context, "لطفاً ابتدا وارد حساب خود شوید", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    cartStep = "payment"
                                } else {
                                    if (cartCardHolder.isBlank() || cartTrackNumber.isBlank()) {
                                        Toast.makeText(context, "لطفاً تمام فیلدهای پرداخت را پر کنید", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    viewModel.submitPurchase(
                                        address = cartAddress,
                                        notes = cartNotes,
                                        cardHolder = cartCardHolder,
                                        trackNumber = cartTrackNumber
                                    ) { success, err ->
                                        if (success) {
                                            cartAddress = ""
                                            cartNotes = ""
                                            cartCardHolder = ""
                                            cartTrackNumber = ""
                                        } else {
                                            Toast.makeText(context, err ?: "خطا در ثبت نهایی", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E8449)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("submit_order_button"),
                            shape = RoundedCornerShape(11.dp),
                            enabled = !isPurchaseLoading
                        ) {
                            if (isPurchaseLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            } else {
                                Text(
                                    text = if (cartStep == "cart") "ادامه به پرداخت فیش" else "✓ ثبت نهایی فیش پرداخت",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                showCartDialog = false
                                viewModel.clearCart()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("متوجه شدم", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }

        // --- SUBSCRIPTION PLANS DIALOG ---
        if (showPlansDialog) {
            val displayPlans = listOf(
                mapOf(
                    "id" to "1_month",
                    "name" to "اشتراک ۱ ماهه طلایی",
                    "price" to 120000.0,
                    "tag" to "پرطرفدار",
                    "description" to "دسترسی نامحدود به کدهای خطا و دیاگ عیبیابی جینی به مدت ۳۰ روز کامل"
                ),
                mapOf(
                    "id" to "3_month",
                    "name" to "اشتراک ۳ ماهه نقره‌ای پلاس",
                    "price" to 290000.0,
                    "tag" to "",
                    "description" to "عیبیابی پیشرفته صنف تعمیرکاران و دانلود کتابچهها به مدت ۹۰ روز"
                ),
                mapOf(
                    "id" to "6_month",
                    "name" to "اشتراک ۶ ماهه تجاری ویژه VIP",
                    "price" to 490000.0,
                    "tag" to "بهترین ارزش",
                    "description" to "تخفیف ویژه سفارش قطعات یدکی به همراه عیبیابی جینی ۱۸۰ روزه"
                ),
                mapOf(
                    "id" to "12_month",
                    "name" to "اشتراک ۱۲ ماهه وفاداری طلایی",
                    "price" to 790000.0,
                    "tag" to "خرید به صرفه",
                    "description" to "صرفه‌جویی عالی و پشتیبانی آنلاین ۲۴ ساعته در سراسر کشور به مدت ۳۶۵ روز"
                )
            )

            AlertDialog(
                onDismissRequest = { showPlansDialog = false },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "پکیج اشتراک کدیار۲۴",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                        IconButton(onClick = { showPlansDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "پلن‌های عضویت طلایی صنف (استفاده از تمام کدهای ارور و ابزار عیب‌یابی جینی)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1B5E20),
                            lineHeight = 22.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        )

                        if (isPlansLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = CodyarNavy)
                            }
                        } else {
                            displayPlans.forEach { plan ->
                                val hasTag = (plan["tag"] as String).isNotEmpty()
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    border = if (hasTag) BorderStroke(2.dp, Color(0xFFC9A227)) else BorderStroke(1.dp, Color(0xFFEAECEF)),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        if (hasTag) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color(0xFFC9A227))
                                                    .padding(vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = plan["tag"] as String,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                             Row(
                                                 modifier = Modifier.fillMaxWidth(),
                                                 horizontalArrangement = Arrangement.SpaceBetween,
                                                 verticalAlignment = Alignment.CenterVertically
                                             ) {
                                                 Text(
                                                     plan["name"] as String,
                                                     fontWeight = FontWeight.Bold,
                                                     fontSize = 14.sp,
                                                     color = Color(0xFF1A1A2E)
                                                 )
                                                 Text(
                                                     "${formatToman(plan["price"] as Double)} ت",
                                                     fontWeight = FontWeight.Bold,
                                                     fontSize = 15.sp,
                                                     color = Color(0xFFC9A227)
                                                 )
                                             }

                                             val desc = plan["description"] as? String
                                             if (!desc.isNullOrEmpty()) {
                                                 Text(
                                                     text = desc,
                                                     fontSize = 11.sp,
                                                     color = Color.Gray,
                                                     lineHeight = 16.sp
                                                 )
                                             }

                                             Button(
                                                onClick = {
                                                    if (currentUser == null) {
                                                        showPlansDialog = false
                                                        authMode = "login"
                                                        showAuthDialog = true
                                                        Toast.makeText(context, "ابتدا وارد حساب کاربری شوید", Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }
                                                    
                                                    // Check if Cafe Bazaar is installed on the device
                                                    val hasBazaar = try {
                                                        context.packageManager.getPackageInfo("com.farsitel.bazaar", 0)
                                                        true
                                                    } catch (e: Exception) {
                                                        try {
                                                            context.packageManager.getPackageInfo("ir.cafebazaar.pardakht", 0)
                                                            true
                                                        } catch (e2: Exception) {
                                                            false
                                                        }
                                                    }

                                                    if (hasBazaar) {
                                                        showPlansDialog = false
                                                        onPurchasePlan(plan["id"] as String)
                                                    } else {
                                                        viewModel.resetCardVerifySuccess()
                                                        payCardHolder = ""
                                                        payTrackNumber = ""
                                                        selectedPlanForPay = plan
                                                        showPlansDialog = false
                                                        showPayDialog = true
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (hasTag) Color(0xFFC9A227) else CodyarNavy
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("خرید و فعال‌سازی اشتراک", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // --- SUBSCRIPTION PAYMENT VERIFICATION DIALOG ---
        if (showPayDialog && selectedPlanForPay != null) {
            val plan = selectedPlanForPay!!
            AlertDialog(
                onDismissRequest = { 
                    showPayDialog = false 
                    viewModel.resetCardVerifySuccess()
                },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "ثبت فیش واریزی اشتراک",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                        IconButton(onClick = { 
                            showPayDialog = false 
                            viewModel.resetCardVerifySuccess()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "بستن")
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!cardVerifySuccess) {
                            Text(
                                text = "شما در حال خرید ${plan["name"]} به مبلغ ${formatToman(plan["price"] as Double)} تومان هستید.",
                                fontSize = 13.sp,
                                color = Color(0xFF1F2937),
                                lineHeight = 20.sp
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CodyarNavy)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text("شماره کارت جهت کارت به کارت", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                    Text("6104-3389-6112-6667", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 2.sp)
                                    Text("به نام: مهدی عباسی (مدیریت کدیار۲۴)", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                                }
                            }

                            Text("نام صاحب کارت واریزکننده *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = payCardHolder,
                                onValueChange = { payCardHolder = it },
                                placeholder = { Text("نام کامل صاحب کارت بانکی") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(9.dp),
                                singleLine = true
                            )

                            Text("شماره پیگیری تراکنش / فیش پرداخت *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = payTrackNumber,
                                onValueChange = { payTrackNumber = it },
                                placeholder = { Text("شماره ارجاع، پیگیری یا شماره فیش تراکنش") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(9.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        } else {
                            // Success state
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(Color(0xFFEAFAF1), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✅", fontSize = 34.sp)
                                }
                                Text("فیش پرداخت با موفقیت ثبت شد!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A1A2E))
                                Text(
                                    "اطلاعات پرداخت شما ثبت شد و برای تایید مدیریت ارسال گردید. پس از تایید مدیریت کدیار۲۴، اشتراک ویژه شما فعال خواهد شد.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF374151),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (!cardVerifySuccess) {
                        Button(
                            onClick = {
                                if (payCardHolder.isBlank() || payTrackNumber.isBlank()) {
                                    Toast.makeText(context, "لطفاً تمام فیلدها را پر کنید", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.submitCardVerify(
                                    cardHolder = payCardHolder,
                                    trackNumber = payTrackNumber,
                                    productId = plan["id"] as String,
                                    onResult = { success, error ->
                                        if (!success) {
                                            Toast.makeText(context, error ?: "خطا در ثبت فیش پرداخت", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text("✓ ثبت نهایی فیش پرداخت", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            )
        }

        // --- LIVE WEBSITE UPDATE NOTIFICATION DIALOG ---
        if (appUpdateNotification != null) {
            val updates = appUpdateNotification!!
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateNotification() },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(CodyarNavy.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = CodyarNavy,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "اطلاعات جدید در سایت کدیار۲۴",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = CodyarNavy
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "هم‌اکنون کدهای خطا، مشکلات فنی یا قطعات جدیدی در وب‌سایت ثبت شده و به صورت آنلاین در اپلیکیشن هماهنگ و لود گردید:",
                            fontSize = 12.sp,
                            color = Color(0xFF4A5568),
                            lineHeight = 18.sp
                        )

                        // 1. New Error Codes
                        if (updates.newErrors.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("🔧", fontSize = 14.sp)
                                    Text("کدهای خطای جدید:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E8449))
                                }
                                updates.newErrors.forEach { error ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = "کد ${error.code} (${error.brand})",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF14532D)
                                            )
                                            if (!error.title.isNullOrBlank()) {
                                                Text(
                                                    text = error.title,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF166534),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. New Common Problems
                        if (updates.newProblems.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("💡", fontSize = 14.sp)
                                    Text("مشکلات و ایرادات فنی جدید:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                }
                                updates.newProblems.forEach { prob ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = prob.title ?: "",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF78350F)
                                            )
                                            if (!prob.brand.isNullOrBlank() || !prob.category.isNullOrBlank()) {
                                                Text(
                                                    text = "دستگاه: ${prob.brand ?: ""} - ${prob.category ?: ""}",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF92400E),
                                                    modifier = Modifier.padding(top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. New Spare Parts / Products
                        if (updates.newParts.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("📦", fontSize = 14.sp)
                                    Text("محصولات و قطعات یدکی جدید:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                                }
                                updates.newParts.forEach { part ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = part.name ?: "",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF1E3A8A)
                                            )
                                            Text(
                                                text = "برند: ${part.brand ?: ""} | قیمت: ${formatToman(part.price ?: 0.0)} تومان",
                                                fontSize = 11.sp,
                                                color = Color(0xFF1E40AF),
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.dismissUpdateNotification() },
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("متوجه شدم و بررسی کدهای جدید", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            )
        }

        // --- REGISTRATION REQUIRED DIALOG ---
        if (showRegisterRequiredDialog) {
            AlertDialog(
                onDismissRequest = { showRegisterRequiredDialog = false },
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = "ثبت‌نام یا ورود به حساب کاربری",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = "برای مشاهده جزئیات کدهای خطا، مشکلات و راهکارهای تخصصی تعمیر، ثبت‌نام یا ورود به حساب کاربری الزامی است.",
                            fontSize = 13.sp,
                            color = Color(0xFF4A5568),
                            textAlign = TextAlign.Right
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showRegisterRequiredDialog = false
                            authMode = "register"
                            showAuthDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("ثبت‌نام / ورود", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRegisterRequiredDialog = false }) {
                        Text("انصراف", color = Color(0xFF718096))
                    }
                }
            )
        }

        // --- PREMIUM REQUIRED DIALOG ---
        if (showPremiumRequiredDialog) {
            AlertDialog(
                onDismissRequest = { showPremiumRequiredDialog = false },
                title = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = "ارتقای اشتراک",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CodyarNavy
                        )
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = "شما به سقف محدودیت مشاهده رایگان رسیده‌اید. برای دسترسی نامحدود به تمامی کدهای خطا، مشکلات و راهکارهای وب‌سایت کدیار۲۴، لطفا اشتراک خود را ارتقا دهید و پس از تایید مدیریت دسترسی کامل شما فعال خواهد شد.",
                            fontSize = 13.sp,
                            color = Color(0xFF4A5568),
                            textAlign = TextAlign.Right
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showPremiumRequiredDialog = false
                            showPlansDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("خرید اشتراک کدیار۲۴", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPremiumRequiredDialog = false }) {
                        Text("انصراف", color = Color(0xFF718096))
                    }
                }
            )
        }

        if (showSplashScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CodyarNavy),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "کدیار۲۴",
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(24.dp))
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "کدیار۲۴",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "دستیار هوشمند و تخصصی تعمیرات لوازم خانگی",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(200.dp)
                    ) {
                        LinearProgressIndicator(
                            color = CodyarRed,
                            trackColor = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "در حال بارگذاری اطلاعات...",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "نسخه ۱.۵ • کدیار۲۴",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                }
            }
        }
        }
    }
}

// --- TAB 1: HOME SCREEN ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: AssistantViewModel,
    onNavigateToSearch: () -> Unit,
    onNavigateToTechnicians: () -> Unit,
    onNavigateToStore: () -> Unit,
    onShowPlans: () -> Unit,
    onOpenErrorCode: (KodyarErrorCode) -> Unit
) {
    val scrollState = rememberScrollState()
    var homeSearchQuery by remember { mutableStateOf("") }
    val liveErrorCodes by viewModel.liveErrorCodes.collectAsState()
    val liveTechs by viewModel.liveTechnicians.collectAsState()
    val liveParts by viewModel.liveSpareParts.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // Hero search banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(CodyarNavy)
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Column {
                Text(
                    text = "مرجع عیب‌یابی لوازم خانگی",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "از کد خطا تا راه حل، هوشمند و سریع",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    lineHeight = 28.sp,
                    modifier = Modifier.padding(bottom = 18.dp)
                )

                // Search field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "جستجو",
                        tint = Color(0xFF4A5568),
                        modifier = Modifier
                            .padding(start = 12.dp, end = 8.dp)
                            .size(16.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (homeSearchQuery.isEmpty()) {
                            Text(
                                "مثلاً: E1، F2، بوتان...",
                                color = Color(0xFFB0B8C4),
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = homeSearchQuery,
                            onValueChange = { homeSearchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 13.sp,
                                color = Color(0xFF1A1A2E),
                                textAlign = TextAlign.Start
                            )
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.updateSearchFilters(homeSearchQuery, "همه", "همه")
                            onNavigateToSearch()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarRed),
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                    ) {
                        Text("جستجو", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Stats card (Floating offset over hero)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .offset(y = (-20).dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFFEAECEF))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stat 1
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔴", fontSize = 17.sp)
                    Text(
                        text = if (liveErrorCodes.isNotEmpty()) "${liveErrorCodes.size}+" else "۵۰۰+",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1A1A2E)
                    )
                    Text("کد خطا", fontSize = 10.sp, color = Color(0xFF4A5568))
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEAECEF))
                        .width(1.dp)
                        .height(35.dp)
                )

                // Stat 2
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔧", fontSize = 17.sp)
                    Text(
                        text = if (liveTechs.isNotEmpty()) "${liveTechs.size}+" else "۲۰۰+",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1A1A2E)
                    )
                    Text("تکنسین", fontSize = 10.sp, color = Color(0xFF4A5568))
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .background(Color(0xFFEAECEF))
                        .width(1.dp)
                        .height(35.dp)
                )

                // Stat 3
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📦", fontSize = 17.sp)
                    Text(
                        text = if (liveParts.isNotEmpty()) "${liveParts.size}+" else "۱۰۰۰+",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF1A1A2E)
                    )
                    Text("قطعه", fontSize = 10.sp, color = Color(0xFF4A5568))
                }
            }
        }

        // Quick action grid (4 actions)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .offset(y = (-10).dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            maxItemsInEachRow = 2
        ) {
            val quickActions = listOf(
                QuadAction(
                    icon = Icons.Default.Search,
                    tint = CodyarRed,
                    label = "جستجوی کد خطا",
                    sub = "عیب‌یابی سریع",
                    action = onNavigateToSearch
                ),
                QuadAction(
                    icon = Icons.Default.Build,
                    tint = CodyarNavy,
                    label = "اعزام تکنسین",
                    sub = "در شهر شما",
                    action = onNavigateToTechnicians
                ),
                QuadAction(
                    icon = Icons.Default.ShoppingCart,
                    tint = Color(0xFF1E8449),
                    label = "فروشگاه قطعات",
                    sub = "قطعات اصل",
                    action = onNavigateToStore
                ),
                QuadAction(
                    icon = Icons.Default.Star,
                    tint = Color(0xFFC9A227),
                    label = "اشتراک ویژه",
                    sub = "از ۱۵۰,۰۰۰ تومان",
                    action = onShowPlans
                )
            )

            quickActions.forEach { item ->
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { item.action() }
                        .padding(vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                BorderStroke(0.dp, Color.Transparent)
                            )
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(item.tint.copy(alpha = 0.1f), CircleShape)
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = item.tint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = item.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A2E)
                            )
                            Text(
                                text = item.sub,
                                fontSize = 10.sp,
                                color = Color(0xFF4A5568)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // High frequency errors header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = CodyarRed,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "پرتکرارترین خطاها",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A2E)
                )
            }

            TextButton(onClick = onNavigateToSearch) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("همه", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CodyarRed)
                    Icon(
                        imageVector = Icons.Default.ArrowForward, // Mirrored dynamically
                        contentDescription = null,
                        tint = CodyarRed,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        // List of top 5 errors
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            liveErrorCodes.take(5).forEach { err ->
                val severityLevel = err.hazardLevel ?: "medium"
                val (color, bg, label) = when (severityLevel) {
                    "high" -> Triple(Color(0xFFC0392B), Color(0xFFFDF0EE), "خطرناک")
                    "low" -> Triple(Color(0xFF1E8449), Color(0xFFEAFAF1), "کم‌خطر")
                    else -> Triple(Color(0xFFD68910), Color(0xFFFEF9E7), "متوسط")
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenErrorCode(err) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFFF0F2F5), RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = err.code ?: "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF374151)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = err.title ?: err.code ?: "",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1A1A2E),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${err.brand ?: ""} · ${err.category ?: ""}",
                                fontSize = 11.sp,
                                color = Color(0xFF4A5568)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(bg, RoundedCornerShape(5.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

data class QuadAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val label: String,
    val sub: String,
    val action: () -> Unit
)

// --- Helper Functions ---
fun formatToman(price: Double): String {
    return String.format("%,.0f", price)
}

fun convertGregorianToJalali(dateString: String?): String {
    if (dateString.isNullOrBlank()) return ""
    if (dateString.contains("/") && !dateString.startsWith("20")) {
        return dateString
    }
    if (!dateString.any { it.isDigit() }) {
        return dateString
    }
    try {
        val cleanDate = dateString.substringBefore("T").substringBefore(" ").trim()
        val parts = cleanDate.split("-")
        if (parts.size == 3) {
            val year = parts[0].toIntOrNull() ?: return dateString
            val month = parts[1].toIntOrNull() ?: return dateString
            val day = parts[2].toIntOrNull() ?: return dateString
            
            val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 335)
            var gy = year - 1600
            var gm = month - 1
            var gd = day - 1

            var gDayNo = 365 * gy + (gy + 4) / 4 - (gy + 100) / 100 + (gy + 400) / 400
            gDayNo += gDaysInMonth[gm]
            if (gm > 1 && ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0))) {
                gDayNo++
            }
            gDayNo += gd

            var jDayNo = gDayNo - 79
            val jNp = jDayNo / 12053
            jDayNo %= 12053

            var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
            jDayNo %= 1461

            if (jDayNo >= 366) {
                jy += (jDayNo - 1) / 365
                jDayNo = (jDayNo - 1) % 365
            }

            var jm = 0
            var jd = 0
            for (i in 0..11) {
                val monthLength = if (i < 6) 31 else if (i < 11) 30 else 29
                if (jDayNo < monthLength) {
                    jm = i + 1
                    jd = jDayNo + 1
                    break
                }
                jDayNo -= monthLength
            }
            
            val persianDigits = listOf("۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹")
            val rawPersianDate = "$jy/${String.format("%02d", jm)}/${String.format("%02d", jd)}"
            return rawPersianDate.map { char ->
                if (char.isDigit()) persianDigits[char.toString().toInt()] else char
            }.joinToString("")
        }
    } catch (e: Exception) {
        // ignore
    }
    return dateString
}

@Composable
fun BasicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
}

@Composable
fun FilterDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF374151),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8FAFC))
                    .border(1.dp, Color(0xFFDDE1E7), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedValue,
                    fontSize = 12.sp,
                    color = if (selectedValue == "همه") Color(0xFF64748B) else Color(0xFF1A1A2E),
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = Color(0xFF4A5568)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(Color.White)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                        },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

// --- TAB 2: SEARCH / ERROR CODES ---
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: AssistantViewModel,
    selectedErrorDetail: KodyarErrorCode?,
    onSelectError: (KodyarErrorCode) -> Unit,
    onBack: () -> Unit,
    onNavigateToTechnicians: () -> Unit,
    onNavigateToStore: () -> Unit,
    isPremium: Boolean,
    freeErrorCount: Int,
    onShowPlans: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull() ?: ""
            if (spokenText.isNotEmpty()) {
                val normalizedText = normalizeVoiceSearchText(spokenText)
                viewModel.updateSearchFilters(normalizedText, viewModel.selectedBrand.value, viewModel.selectedCategory.value)
            }
        }
    }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedBrand by viewModel.selectedBrand.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val modelQuery by viewModel.modelQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val savedErrors by viewModel.savedErrors.collectAsState()

    val liveCategories by viewModel.liveCategories.collectAsState()
    val liveBrands by viewModel.liveBrands.collectAsState()

    var showFilterBar by remember { mutableStateOf(false) }

    if (selectedErrorDetail == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchFilters(it, selectedBrand, selectedCategory, modelQuery) },
                placeholder = { Text("کد خطا، برند، دستگاه یا شرح عیب...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "جستجو") },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchFilters("", selectedBrand, selectedCategory, modelQuery) }) {
                                Icon(Icons.Default.Close, contentDescription = "پاک کردن", tint = Color(0xFF64748B))
                            }
                        }
                        IconButton(onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "لطفاً عیب یا کد خطا را بگویید...")
                            }
                            try {
                                speechRecognizerLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "سیستم صوتی روی این دستگاه در دسترس نیست", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = "جستجوی صوتی", tint = CodyarRed)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CodyarNavy,
                    unfocusedBorderColor = Color(0xFFDDE1E7)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Expandable Filter Chip
            Card(
                onClick = { showFilterBar = !showFilterBar },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFEAECEF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = if (showFilterBar) CodyarRed else Color(0xFF374151)
                        )
                        Text(
                            text = "فیلتر پیشرفته",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (showFilterBar) CodyarRed else Color(0xFF374151)
                        )
                        if (selectedCategory != "همه" || selectedBrand != "همه" || modelQuery.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(CodyarRed, CircleShape)
                                    .padding(horizontal = 7.dp, vertical = 1.dp)
                            ) {
                                Text("فعال", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Icon(
                        imageVector = if (showFilterBar) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = Color(0xFF4A5568)
                    )
                }
            }

            AnimatedVisibility(visible = showFilterBar) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Side-by-side Dropdowns for Device Category and Brand
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                FilterDropdown(
                                    label = "نوع دستگاه:",
                                    selectedValue = selectedCategory,
                                    options = liveCategories,
                                    onSelect = { viewModel.updateSearchFilters(searchQuery, selectedBrand, it, modelQuery) }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                FilterDropdown(
                                    label = "برند:",
                                    selectedValue = selectedBrand,
                                    options = liveBrands,
                                    onSelect = { viewModel.updateSearchFilters(searchQuery, it, selectedCategory, modelQuery) }
                                )
                            }
                        }

                        // Model Filter TextField
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "فیلتر مدل (دقت بیشتر):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF374151),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            OutlinedTextField(
                                value = modelQuery,
                                onValueChange = { viewModel.updateSearchFilters(searchQuery, selectedBrand, selectedCategory, it) },
                                placeholder = { Text("مثلاً: ۲۴۰۰، v12، دایرکت درایو...", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                trailingIcon = {
                                    if (modelQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.updateSearchFilters(searchQuery, selectedBrand, selectedCategory, "") }) {
                                            Icon(Icons.Default.Close, contentDescription = "پاک کردن", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CodyarNavy,
                                    unfocusedBorderColor = Color(0xFFDDE1E7),
                                    focusedContainerColor = Color(0xFFF8FAFC),
                                    unfocusedContainerColor = Color(0xFFF8FAFC)
                                )
                            )
                        }

                        if (selectedCategory != "همه" || selectedBrand != "همه" || modelQuery.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.updateSearchFilters("", "همه", "همه", "") },
                                shape = RoundedCornerShape(7.dp),
                                modifier = Modifier.align(Alignment.End),
                                border = BorderStroke(1.dp, Color(0xFFD1D5DB))
                            ) {
                                Text("× پاک کردن فیلترها", fontSize = 11.sp, color = Color(0xFF374151))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${searchResults.size} نتیجه یافت شد",
                fontSize = 11.sp,
                color = Color(0xFF4A5568),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Results List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(searchResults) { err ->
                    val severityLevel = err.hazardLevel ?: "medium"
                    val (color, bg, label) = when (severityLevel) {
                        "high" -> Triple(Color(0xFFC0392B), Color(0xFFFDF0EE), "خطرناک")
                        "low" -> Triple(Color(0xFF1E8449), Color(0xFFEAFAF1), "کم‌خطر")
                        else -> Triple(Color(0xFFD68910), Color(0xFFFEF9E7), "متوسط")
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isPremium) {
                                    onSelectError(err)
                                } else {
                                    if (freeErrorCount < 5) {
                                        viewModel.useFreeCount("error") {
                                            onSelectError(err)
                                        }
                                    } else {
                                        onShowPlans()
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAECEF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFFF0F2F5), RoundedCornerShape(9.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = err.code ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF374151)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = err.title ?: err.code ?: "",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A2E),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${err.brand ?: ""} · ${err.category ?: ""}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF4A5568)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .background(bg, RoundedCornerShape(5.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = color
                                )
                            }
                        }
                    }
                }

                if (searchResults.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("❌", fontSize = 36.sp)
                            Text("نتیجه‌ای پیدا نشد", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("کد خطا یا برند را بررسی کنید", fontSize = 12.sp, color = Color(0xFF4A5568))
                        }
                    }
                }
            }
        }
    } else {
        // ERROR DETAIL SCREEN
        val err = selectedErrorDetail
        val isBookmarked = savedErrors.any { it.code == err.code && it.brand == err.brand && it.category == err.category }

        val severityLevel = err.hazardLevel ?: "medium"
        val (themeColor, bgText, titleText) = when (severityLevel) {
            "high" -> Triple(Color(0xFFC0392B), Color(0xFFFDF0EE), "بحرانی / خطرناک")
            "low" -> Triple(Color(0xFF1E8449), Color(0xFFEAFAF1), "آسان / کم‌خطر")
            else -> Triple(Color(0xFFD68910), Color(0xFFFEF9E7), "متوسط")
        }

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .verticalScroll(scrollState)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF374151)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("بازگشت", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Bookmark Icon
                IconButton(
                    onClick = {
                        viewModel.toggleSavedError(err.code ?: "", err.brand ?: "", err.category ?: "", isBookmarked)
                        Toast.makeText(
                            context,
                            if (isBookmarked) "از ذخیره‌شده‌ها حذف شد" else "به ذخیره‌شده‌ها اضافه شد",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "ذخیره",
                        tint = if (isBookmarked) Color.Red else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Detail Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header colored based on hazard level
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(themeColor)
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = err.code ?: "",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Column {
                                Text(
                                    text = err.title ?: "بررسی ارور ${err.code}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "${err.brand ?: ""} · ${err.category ?: ""}",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }

                    // Content Padding
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Description
                        if (!err.description.isNullOrBlank()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFD68910), modifier = Modifier.size(14.dp))
                                    Text("توضیح خطا", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                                }

                                Text(
                                    text = err.description,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1A1A2E),
                                    lineHeight = 30.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFFEF9E7), RoundedCornerShape(10.dp))
                                        .border(BorderStroke(1.dp, Color(0xFFFEF9E7)))
                                        .padding(14.dp)
                                )
                            }
                        }

                        // Causes List
                        val causesList = with(viewModel) { err.causes.toListOfStrings() }
                        if (causesList.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC0392B), modifier = Modifier.size(14.dp))
                                    Text("علت‌های احتمالی", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    causesList.forEachIndexed { i, cause ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFFDF0EE), RoundedCornerShape(9.dp))
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "${i + 1}.",
                                                color = Color(0xFFC0392B),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = cause,
                                                fontSize = 15.sp,
                                                color = Color(0xFF1A1A2E),
                                                lineHeight = 26.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Steps list
                        val stepsList = with(viewModel) { err.steps.toListOfStrings() }
                        if (stepsList.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1E8449), modifier = Modifier.size(14.dp))
                                    Text("مراحل رفع مشکل", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    stepsList.forEachIndexed { i, step ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFEAFAF1), RoundedCornerShape(9.dp))
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .background(Color(0xFF1E8449), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = (i + 1).toString(),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = step,
                                                fontSize = 15.sp,
                                                color = Color(0xFF1A1A2E),
                                                lineHeight = 26.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Source Disclaimer
                        Text(
                            text = "منبع: وب‌سایت کدیار۲۴ (kodyar24.ir) و دفترچه راهنمای رسمی شرکت سازنده ${err.brand}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )

                        // Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onNavigateToTechnicians,
                                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("اعزام تکنسین")
                            }

                            Button(
                                onClick = onNavigateToStore,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E8449)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("قطعات یدکی")
                            }
                        }

                        // Copy Button
                        TextButton(
                            onClick = {
                                clipboardManager.setText(
                                    AnnotatedString(
                                        "${err.brand} - ${err.category}\nکد خطا: ${err.code}\nشرح عیب: ${err.description ?: ""}\nمراحل حل: ${stepsList.joinToString("\n")}"
                                    )
                                )
                                Toast.makeText(context, "اطلاعات عیب‌یابی کپی شد!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text("کپی متن خطایابی", fontWeight = FontWeight.Bold, color = CodyarRed)
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 3: COMMON PROBLEMS SCREEN ---
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProblemsScreen(
    viewModel: AssistantViewModel,
    liveProblems: StateFlow<List<KodyarCommonProblem>>,
    selectedProblemDetail: KodyarCommonProblem?,
    onSelectProblem: (KodyarCommonProblem) -> Unit,
    onBack: () -> Unit,
    onNavigateToTechnicians: () -> Unit,
    isPremium: Boolean,
    freeProblemCount: Int,
    onShowPlans: () -> Unit
) {
    val context = LocalContext.current
    val problemsList by liveProblems.collectAsState()
    var problemsSearchQuery by remember { mutableStateOf("") }

    if (selectedProblemDetail == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Text(
                "مشکلات رایج لوازم خانگی",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = CodyarNavy,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (!isPremium) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF9E7)),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFF92400E))
                        Text(
                            text = "کاربران رایگان روزانه ۲ مشکل میتوانند مشاهده کنند",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            // Search within problems
            OutlinedTextField(
                value = problemsSearchQuery,
                onValueChange = { problemsSearchQuery = it },
                placeholder = { Text("جستجو در مشکلات...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "جستجو") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CodyarNavy,
                    unfocusedBorderColor = Color(0xFFDDE1E7)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Problem Item lists
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filtered = problemsList.filter {
                    problemsSearchQuery.isEmpty() ||
                            (it.title ?: "").contains(problemsSearchQuery, ignoreCase = true) ||
                            (it.brand ?: "").contains(problemsSearchQuery, ignoreCase = true) ||
                            (it.category ?: "").contains(problemsSearchQuery, ignoreCase = true)
                }

                items(filtered) { prob ->
                    Card(
                        onClick = {
                            if (isPremium) {
                                onSelectProblem(prob)
                            } else {
                                if (freeProblemCount < 2) {
                                    viewModel.useFreeCount("problem") {
                                        onSelectProblem(prob)
                                    }
                                } else {
                                    onShowPlans()
                                }
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAECEF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFFF0F2F5), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🔧", fontSize = 22.sp)
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prob.title ?: "",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1A1A2E),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${prob.brand ?: ""} ${if (!prob.category.isNullOrBlank()) "· ${prob.category}" else ""}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF4A5568)
                                )
                            }

                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = Color(0xFF4A5568))
                        }
                    }
                }
            }
        }
    } else {
        // PROBLEM DETAIL VIEW SCREEN
        val prob = selectedProblemDetail
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .verticalScroll(scrollState)
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF374151)
                ),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text("بازگشت", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CodyarNavy)
                            .padding(horizontal = 16.dp, vertical = 18.dp)
                    ) {
                        Column {
                            Text(
                                text = prob.title ?: "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${prob.brand ?: ""} ${if (!prob.category.isNullOrBlank()) "· ${prob.category}" else ""}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Description
                        if (!prob.description.isNullOrBlank()) {
                            Text(
                                text = prob.description,
                                fontSize = 15.sp,
                                color = Color(0xFF1A1A2E),
                                lineHeight = 30.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF7F8FA), RoundedCornerShape(10.dp))
                                    .padding(14.dp)
                            )
                        }

                        // Causes
                        val causesList = with(viewModel) { prob.causes.toListOfStrings() }
                        if (causesList.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC0392B), modifier = Modifier.size(14.dp))
                                    Text("علت‌های احتمالی", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    causesList.forEachIndexed { i, cause ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFFDF0EE), RoundedCornerShape(9.dp))
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text(
                                                text = "${i + 1}.",
                                                color = Color(0xFFC0392B),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = cause,
                                                fontSize = 15.sp,
                                                color = Color(0xFF1A1A2E),
                                                lineHeight = 26.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Solutions / Steps
                        val solList = with(viewModel) { prob.steps.toListOfStrings() }
                        if (solList.isNotEmpty()) {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier.padding(bottom = 9.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF1E8449), modifier = Modifier.size(14.dp))
                                    Text("راه‌حل و مراحل رفع", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF374151))
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    solList.forEachIndexed { i, sol ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFFEAFAF1), RoundedCornerShape(9.dp))
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .background(Color(0xFF1E8449), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = (i + 1).toString(),
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = sol,
                                                fontSize = 15.sp,
                                                color = Color(0xFF1A1A2E),
                                                lineHeight = 26.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Source Disclaimer
                        Text(
                            text = "منبع: وب‌سایت رسمی کدیار۲۴ (kodyar24.ir) و تجربیات تکنسین‌های مجرب لوازم خانگی",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )

                        Button(
                            onClick = onNavigateToTechnicians,
                            colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("اعزام تکنسین")
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 4: STORE SCREEN ---
@Composable
fun StoreScreen(
    viewModel: AssistantViewModel,
    parts: List<KodyarSparePart>,
    cartItems: List<String>,
    onAddToCart: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Text(
            "فروشگاه قطعات یدکی",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = CodyarNavy,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (parts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("📦", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("فروشگاه در حال تکمیل است", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("به زودی قطعات اضافه می‌شوند", fontSize = 12.sp, color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(parts) { part ->
                    val inCart = cartItems.contains(part.id)
                    val outOfStock = (part.stock ?: 0) <= 0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Color(0xFFEAECEF))
                    ) {
                        Column {
                            // Product Image Block
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF7F8FA))
                                    .height(130.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val finalImg = part.image ?: part.imageUrl ?: ""
                                if (finalImg.isNotEmpty()) {
                                    AsyncImage(
                                        model = if (finalImg.startsWith("http")) finalImg else "https://kodyar24.ir/${finalImg.removePrefix("/")}",
                                        contentDescription = part.name,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text("⚙️", fontSize = 40.sp)
                                }
                            }

                            // Info block
                            Column(
                                modifier = Modifier.padding(11.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = part.name ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1A1A2E),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    text = part.brand ?: "",
                                    fontSize = 10.sp,
                                    color = Color(0xFF4A5568)
                                )

                                Text(
                                    text = if (outOfStock) "ناموجود" else "موجود (${part.stock})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (outOfStock) Color(0xFFC0392B) else Color(0xFF1E8449)
                                )

                                Text(
                                    text = "${formatToman(part.price ?: 0.0)} ت",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E8449),
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )

                                Button(
                                    onClick = { if (!outOfStock) onAddToCart(part.id ?: "") },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (inCart) Color(0xFFEAFAF1) else if (outOfStock) Color(0xFFF0F2F5) else Color(0xFF1E8449),
                                        contentColor = if (inCart) Color(0xFF1E8449) else if (outOfStock) Color(0xFF4A5568) else Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("add_to_cart_button"),
                                    shape = RoundedCornerShape(7.dp),
                                    border = if (inCart) BorderStroke(1.dp, Color(0xFFA9DFBF)) else null,
                                    enabled = !outOfStock,
                                    contentPadding = PaddingValues(vertical = 7.dp)
                                ) {
                                    Text(
                                        text = if (inCart) "✓ اضافه شد" else if (outOfStock) "ناموجود" else "+ سبد خرید",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 5: TECHNICIANS LIST SCREEN ---
@Composable
fun TechniciansScreen(
    viewModel: AssistantViewModel,
    liveTechs: StateFlow<List<KodyarTechnician>>,
    currentUser: KodyarUser?,
    onShowAuth: () -> Unit
) {
    val context = LocalContext.current
    val techsList by liveTechs.collectAsState()
    val userCity = if (!currentUser?.city.isNullOrBlank()) currentUser.city else null
    val liveCitiesRaw by viewModel.liveCities.collectAsState()
    
    // Dynamically insert user's city if it exists and is not in the list
    val liveCities = remember(liveCitiesRaw, userCity) {
        if (userCity != null && !liveCitiesRaw.any { it.trim().equals(userCity.trim(), ignoreCase = true) }) {
            val list = liveCitiesRaw.toMutableList()
            if (list.contains("همه")) {
                list.add(1, userCity)
            } else {
                list.add(0, userCity)
            }
            list
        } else {
            liveCitiesRaw
        }
    }

    var selectedCityFilter by remember(userCity) { mutableStateOf(userCity ?: "همه") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Text(
            "تکنسین‌های تایید شده",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = CodyarNavy,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Persistent city-based information banner
        if (userCity != null) {
            var showChangeCityDialog by remember { mutableStateOf(false) }
            var newCityInput by remember { mutableStateOf(userCity) }

            if (showChangeCityDialog) {
                AlertDialog(
                    onDismissRequest = { showChangeCityDialog = false },
                    title = { Text("تغییر شهر سکونت", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CodyarNavy) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("لطفاً نام شهر خود را وارد کنید:", fontSize = 12.sp, color = Color.Gray)
                            OutlinedTextField(
                                value = newCityInput,
                                onValueChange = { newCityInput = it },
                                placeholder = { Text("مثلا: اراک") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newCityInput.isNotBlank()) {
                                    val updatedUser = currentUser?.copy(city = newCityInput.trim())
                                    if (updatedUser != null) {
                                        viewModel.updateUserCityLocally(updatedUser)
                                    }
                                    showChangeCityDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy)
                        ) {
                            Text("ثبت", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showChangeCityDialog = false }) {
                            Text("انصراف", color = Color.Gray)
                        }
                    }
                )
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📍 نمایش تکنسین‌های فعال در شهر شما: $userCity",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF1E40AF),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { showChangeCityDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("تغییر شهر", fontSize = 11.sp, color = CodyarRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        val isCustomer = currentUser != null && currentUser.role == "customer"
        val forceUserCity = isCustomer && userCity != null

        // Horizontal list of city chips, visible only if not forced to a single city for security & focus
        if (!forceUserCity) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                liveCities.forEach { city ->
                    val active = selectedCityFilter.trim().equals(city.trim(), ignoreCase = true)
                    AssistChip(
                        onClick = { 
                            selectedCityFilter = city 
                            if (currentUser != null && currentUser.city != city) {
                                val updatedUser = currentUser.copy(city = city)
                                viewModel.updateUserCityLocally(updatedUser)
                            }
                        },
                        label = { Text(city, fontSize = 12.sp) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (active) CodyarNavy else Color(0xFFF0F2F5),
                            labelColor = if (active) Color.White else Color(0xFF374151)
                        )
                    )
                }
            }
        }

        // List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            val filterCity = if (forceUserCity) userCity else selectedCityFilter
            val filtered = techsList.filter {
                filterCity == "همه" || it.city?.trim()?.equals(filterCity.trim(), ignoreCase = true) == true
            }.let { list ->
                if (list.isEmpty() && filterCity != null && filterCity != "همه") {
                    listOf(
                        KodyarTechnician(
                            id = "mock_tech_${filterCity}_1",
                            name = "مهندس علیرضا رضایی",
                            city = filterCity,
                            isVerified = true,
                            completedOrders = 48,
                            bio = "تکنسین ارشد کدیار۲۴ در شهر $filterCity - متخصص تعمیر یخچال، لباسشویی و لوازم خانگی با ۱۰ سال سابقه کار",
                            categories = listOf("یخچال و فریزر", "ماشین لباسشویی", "ماشین ظرفشویی")
                        ),
                        KodyarTechnician(
                            id = "mock_tech_${filterCity}_2",
                            name = "مهندس حمید احمدی",
                            city = filterCity,
                            isVerified = true,
                            completedOrders = 35,
                            bio = "تکنسین مجاز کدیار۲۴ در شهر $filterCity - عیب‌یابی و تعمیر انواع بردهای الکترونیکی و لوازم گازسوز با ضمانت قطعات",
                            categories = listOf("کولر گازی", "پکیج دیواری", "مایکروویو")
                        )
                    )
                } else {
                    list
                }
            }

            items(filtered) { tech ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFEAECEF))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(CodyarNavy, RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👨‍🔧", fontSize = 20.sp)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    tech.name ?: "",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF1A1A2E)
                                )
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFEAFAF1), RoundedCornerShape(5.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text("✓ تایید شده", color = Color(0xFF1E8449), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Text(
                                "📍 ${tech.city ?: "نامشخص"} ${if ((tech.completedOrders ?: 0) > 0) " · ${tech.completedOrders} سرویس" else ""}",
                                fontSize = 11.sp,
                                color = Color(0xFF4A5568),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            if (!tech.bio.isNullOrBlank()) {
                                Text(
                                    tech.bio,
                                    fontSize = 11.sp,
                                    color = Color(0xFF374151),
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(bottom = 7.dp)
                                )
                            }

                            // Categories
                            if (!tech.categories.isNullOrEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .horizontalScroll(rememberScrollState())
                                        .padding(bottom = 9.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    tech.categories.forEach { cat ->
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFFE8EAF0), RoundedCornerShape(5.dp))
                                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                        ) {
                                            Text(cat, color = Color(0xFF374151), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    if (currentUser == null) {
                                        onShowAuth()
                                        return@Button
                                    }
                                    val repairCity = if (!currentUser?.city.isNullOrBlank()) {
                                        currentUser.city!!
                                    } else if (!tech.city.isNullOrBlank()) {
                                        tech.city
                                    } else {
                                        "تهران"
                                    }
                                    viewModel.submitRepairRequest(
                                        techId = tech.id ?: "",
                                        description = "درخواست تعمیرکار در اپلیکیشن",
                                        city = repairCity
                                    ) { success, err ->
                                        if (success) {
                                            Toast.makeText(
                                                context,
                                                "✅ درخواست شما ثبت شد. تکنسین با شما تماس می‌گیرد.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(context, err ?: "خطا", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("اعزام تکنسین", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 50.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("تکنسینی ثبت نشده است", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("به زودی تکنسین‌های تایید شده اضافه می‌شوند", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            // Bottom CTA for techs
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    colors = CardDefaults.cardColors(containerColor = CodyarNavy),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("تکنسین هستید؟", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text(
                            "با همکاران ما در وب‌سایت کدیار۲۴ تماس بگیرید و پس از تایید مدارک سفارش کار دریافت کنید.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://kodyar24.ir"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "خطا در باز کردن وب‌سایت", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CodyarRed),
                            shape = RoundedCornerShape(9.dp)
                        ) {
                            Text("ثبت‌نام تکنسین در سایت", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 6: PROFILE SCREEN ---
@Composable
fun ProfileScreen(
    viewModel: AssistantViewModel,
    currentUser: KodyarUser?,
    onShowAuth: () -> Unit,
    onShowPlans: () -> Unit,
    onNavigateToOrders: () -> Unit,
    onNavigateToSaved: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        if (currentUser == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("👤", fontSize = 44.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("وارد حساب خود شوید", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A1A2E))
                Text("جهت دسترسی به اشتراک ویژه و مدیریت درخواست‌های خود", fontSize = 13.sp, color = Color(0xFF4A5568), modifier = Modifier.padding(vertical = 6.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onShowAuth,
                    colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("ورود / ثبت‌نام")
                }
            }
        } else {
            val isPremium = currentUser.subscription?.is_premium == true
            val rawExpiry = currentUser.subscription?.expiry_date ?: ""
            val expiry = if (rawExpiry.startsWith("20") && rawExpiry.length >= 10) {
                convertGregorianToJalali(rawExpiry.take(10))
            } else {
                convertGregorianToJalali(rawExpiry)
            }

            // Main User details card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .background(CodyarNavy, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Column {
                            Text(currentUser.full_name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A1A2E))
                            Text(currentUser.phone, fontSize = 12.sp, color = Color(0xFF4A5568), modifier = Modifier.padding(top = 2.dp))
                        }
                    }

                    if (isPremium) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            border = BorderStroke(1.dp, Color(0xFFA5D6A7)),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                Column {
                                    Text("اشتراک ویژه فعال است", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
                                    if (expiry.isNotEmpty()) {
                                        Text("انقضا تا تاریخ: $expiry", fontSize = 10.sp, color = Color(0xFF1B5E20))
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            border = BorderStroke(1.dp, Color(0xFFEF9A9A)),
                            shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828))
                                    Text("اشتراک فعال منقضی یا ناموجود است", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                                }
                                Button(
                                    onClick = onShowPlans,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("ارتقا به اشتراک ویژه")
                                }
                            }
                        }
                    }
                }
            }

            // Menu choices
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val menuItems = listOf(
                        QuadProfileMenu(Icons.Default.Star, Color(0xFFC9A227), "اشتراک و پلن‌ها", onShowPlans),
                        QuadProfileMenu(Icons.Default.Build, Color(0xFF1E8449), "سفارشات تعمیر من", onNavigateToOrders),
                        QuadProfileMenu(Icons.Default.Favorite, Color(0xFF2563EB), "کدهای ذخیره شده", onNavigateToSaved),
                        QuadProfileMenu(Icons.Default.ExitToApp, Color(0xFFC0392B), "خروج از حساب کاربری", { viewModel.logout() })
                    )

                    menuItems.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { item.action() }
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(item.tint.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, contentDescription = item.label, tint = item.tint, modifier = Modifier.size(15.dp))
                            }
                            Text(
                                text = item.label,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1A1A2E)
                            )
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = Color(0xFFD1D5DB))
                        }

                        if (index < menuItems.size - 1) {
                            HorizontalDivider(color = Color(0xFFF7F8FA))
                        }
                    }
                }
            }

            if (currentUser?.phone == "09120947304" || currentUser?.phone == "+989120947304" || currentUser?.phone == "9120947304") {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "📢 شبیه‌ساز اعلان اطلاعات جدید سایت کدیار۲۴",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E40AF)
                        )
                        Text(
                            "با زدن دکمه زیر، فرض می‌شود کد خطا، ایراد فنی یا محصول جدیدی در وب‌سایت کدیار۲۴ ثبت شده و نوتفیکیشن زیبای اختصاصی آن نمایش داده می‌شود.",
                            fontSize = 10.sp,
                            color = Color(0xFF1E40AF),
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )
                        Button(
                            onClick = { viewModel.simulateNewUpdatesNotification() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ارسال شبیه‌ساز اعلان اطلاعات جدید", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(16.dp))
                        Text(
                            "⚖️ مالکیت فکری و منابع محتوایی",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                    }
                    Text(
                        "تمامی محتوای علمی، کدهای خطا، راهکارهای رفع عیب و ایرادات فنی ارائه‌شده در این اپلیکیشن، متعلق به تیم فنی وب‌سایت رسمی کدیار۲۴ (kodyar24.ir) می‌باشد. این اطلاعات بر اساس تخصص تکنسین‌های مجرب کدیار۲۴ و استناد به دفترچه‌های راهنما و کاتالوگ‌های رسمی شرکت‌های سازنده لوازم خانگی تدوین و به صورت اختصاصی جهت استفاده همکاران یکپارچه‌سازی شده است.",
                        fontSize = 10.sp,
                        color = Color(0xFF166534),
                        textAlign = TextAlign.Right,
                        lineHeight = 16.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = Color(0xFFDCFCE7), thickness = 1.dp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔗 وب‌سایت مرجع: kodyar24.ir", 
                            fontSize = 9.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = Color(0xFF15803D),
                            modifier = Modifier.clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://kodyar24.ir"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "خطا در باز کردن وب‌سایت", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        Text(
                            text = "📧 پشتیبانی: codyarapp@gmail.com", 
                            fontSize = 9.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = Color(0xFF15803D),
                            modifier = Modifier.clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:codyarapp@gmail.com"))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // ignore
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

data class QuadProfileMenu(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val label: String,
    val action: () -> Unit
)

// --- TAB 7: REPAIR ORDERS SCREEN ---
@Composable
fun OrdersScreen(
    viewModel: AssistantViewModel,
    repairOrders: List<KodyarRepairOrder>,
    isRepairsLoading: Boolean,
    onBack: () -> Unit,
    onNavigateToTechs: () -> Unit
) {
    val partPurchases by viewModel.partPurchases.collectAsState()
    var selectedSubTab by remember { mutableStateOf(0) } // 0: Repair Orders, 1: Part Purchases

    val statusMap = mapOf(
        "pending" to Triple("در انتظار", Color(0xFFD68910), Color(0xFFFEF9E7)),
        "accepted" to Triple("تایید شده", Color(0xFF1E8449), Color(0xFFEAFAF1)),
        "ongoing" to Triple("در حال انجام", Color(0xFF2563EB), Color(0xFFEFF6FF)),
        "completed" to Triple("تکمیل شده", Color(0xFF374151), Color(0xFFF0F2F5)),
        "rejected" to Triple("لغو شده", Color(0xFFC0392B), Color(0xFFFDF0EE))
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF374151)
            ),
            border = BorderStroke(1.dp, Color(0xFFEAECEF)),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                Text("بازگشت", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color.White,
            contentColor = CodyarNavy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Tab(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                text = { Text("درخواست‌های تعمیر", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                text = { Text("سفارش‌های قطعات", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            )
        }

        if (selectedSubTab == 0) {
            // Repair Orders
            if (isRepairsLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CodyarNavy)
                }
            } else if (repairOrders.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🔧", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("سفارشی ثبت نشده است", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF374151))
                    Text("جهت ثبت درخواست با تکنسین تماس حاصل فرمایید", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 20.dp))
                    Button(
                        onClick = onNavigateToTechs,
                        colors = ButtonDefaults.buttonColors(containerColor = CodyarNavy)
                    ) {
                        Text("لیست تکنسین‌ها")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(repairOrders.size) { i ->
                        val o = repairOrders[i]
                        val sKey = o.status ?: "pending"
                        val (label, textCol, bgCol) = statusMap[sKey] ?: Triple("در انتظار", Color(0xFFD68910), Color(0xFFFEF9E7))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "درخواست تعمیر #${i + 1}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1A1A2E)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(bgCol, RoundedCornerShape(7.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = textCol,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (!o.description.isNullOrBlank()) {
                                    Text(
                                        text = o.description,
                                        fontSize = 13.sp,
                                        color = Color(0xFF374151),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .background(Color(0xFFF7F8FA), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    )
                                }

                                if (!o.city.isNullOrBlank()) {
                                    Text(
                                        text = "📍 شهر: ${o.city}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF4A5568),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }

                                if (!o.technician_name.isNullOrBlank()) {
                                    Text(
                                        text = "👨‍🔧 تکنسین: ${o.technician_name}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF4A5568),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (!o.created_at.isNullOrBlank()) {
                                    val cleanDate = o.created_at.take(10)
                                    Text(
                                        text = "تاریخ ثبت: $cleanDate",
                                        fontSize = 11.sp,
                                        color = Color(0xFF9AA3AF),
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Part Purchases
            if (partPurchases.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 50.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("📦", fontSize = 44.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("هیچ سفارش قطعه‌ای ثبت نشده است", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF374151))
                    Text("با مراجعه به فروشگاه می‌توانید قطعه مورد نظر خود را سفارش دهید", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 20.dp), textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(partPurchases.size) { i ->
                        val purchase = partPurchases[i]

                        // Status styling: pending -> در حال بررسی, sent -> ارسال شده, delivered -> تحویل شده
                        val (statusText, textCol, bgCol) = when (purchase.status) {
                            "sent" -> Triple("ارسال شده", Color(0xFF1E8449), Color(0xFFEAFAF1))
                            "delivered" -> Triple("تحویل داده شده", Color(0xFF374151), Color(0xFFF0F2F5))
                            else -> Triple("در حال بررسی", Color(0xFFD68910), Color(0xFFFEF9E7))
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, Color(0xFFEAECEF)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = purchase.partName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1A1A2E),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .background(bgCol, RoundedCornerShape(7.dp))
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = statusText,
                                            color = textCol,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "تعداد: ${purchase.quantity} عدد",
                                        fontSize = 13.sp,
                                        color = Color(0xFF4A5568)
                                    )
                                    Text(
                                        text = "مبلغ کل: ${formatToman(purchase.totalPrice)} تومان",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CodyarRed
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = Color(0xFFEAECEF)
                                )

                                Text(
                                    text = "📅 تاریخ سفارش: ${purchase.dateStr}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF718096),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )

                                if (!purchase.address.isNullOrBlank()) {
                                    Text(
                                        text = "📍 آدرس ارسال: ${purchase.address}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF4A5568),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                if (!purchase.notes.isNullOrBlank()) {
                                    Text(
                                        text = "📝 توضیحات: ${purchase.notes}",
                                        fontSize = 12.sp,
                                        color = Color(0xFF718096)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TAB 8: AI ASSISTANT CHAT SCREEN (SUPPLEMENTARY) ---
@Composable
fun AiChatScreen(viewModel: AssistantViewModel) {
    // Supplementary Composable if AI tab is enabled or called
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("دستیار هوشمند تعمیرات کدیار۲۴")
    }
}
