import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.CardType
import models.Customer
import models.ExtensionRequest
import navigation.NavController
import navigation.NavigationHost
import navigation.Screen
import ui.*
import ui.components.*
import ui.screens.*
import utils.AppConstants
import java.time.LocalDate
import java.time.format.DateTimeFormatter
/**
 * Màn hình chính của ứng dụng
 * Tích hợp tất cả các màn hình con
 */
@Composable
@Preview
fun MainApp() {
    // State quản lý danh sách khách hàng - Load từ database
    var customers by remember { mutableStateOf(emptyList<Customer>()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val uiScope = rememberCoroutineScope()
    
    // State cho hoạt động gần đây
    var recentActivities by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    // Helper: Tự động chuyển thẻ tháng hết hạn về thẻ thường
    suspend fun convertExpiredMonthlyCards(customers: List<Customer>): List<Customer> = customers.map { customer ->
        val updated = customer.checkAndConvertExpiredMonthlyCard()
        if (updated.cardType != customer.cardType) {
            database.DatabaseManager.updateCustomer(updated)
            updated
        } else {
            customer
        }
    }
    
    // Helper: Parse transaction date an toàn
    fun parseTransactionDate(dateStr: String): java.time.LocalDateTime = try {
        java.time.LocalDateTime.parse(dateStr.replace(" ", "T"))
    } catch (e: Exception) {
        java.time.LocalDateTime.MIN
    }
    
    // Helper: Cập nhật hoạt động gần đây
    suspend fun updateRecentActivities(customers: List<Customer>) {
        // Lấy transactions và thêm thông tin customer
        recentActivities = customers.flatMap { customer ->
            database.DatabaseManager.getTransactionsByCardId(customer.cardId).map { trans ->
                trans.toMutableMap().apply {
                    put("customer_name", customer.fullName)
                    put("card_id", customer.cardId)
                }
            }
        }.sortedByDescending { 
            parseTransactionDate(it["transaction_date"] as? String ?: "")
        }.take(5)
    }
    
    // State cho các màn hình
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    
    // State theo dõi trạng thái kết nối thẻ
    var isCardConnected by remember { mutableStateOf(false) }
    var isCardHasData by remember { mutableStateOf(false) }
    
    // Helper function để reload customers từ database
    fun reloadCustomersFromDB() {
        customers = database.DatabaseManager.getAllCustomers()
    }
    
    // Helper: Cập nhật trạng thái kết nối thẻ
    suspend fun refreshCardStatus() {
        isCardConnected = smartcard.BusCardManager.isConnected
        isCardHasData = if (isCardConnected) {
            withContext(Dispatchers.IO) {
                smartcard.BusCardManager.checkCardCreated().getOrNull() ?: false
            }
        } else {
            false
        }
        println("Card status refreshed - Connected: $isCardConnected, Has Data: $isCardHasData")
    }
    
    // Helper: Xử lý quẹt thẻ thường
    suspend fun processNormalCardTap(customer: Customer, prefix: String = "") {
        val tapAmount = AppConstants.NORMAL_CARD_TAP_AMOUNT
        val balanceBefore = customer.balance
        val balanceAfter = balanceBefore - tapAmount
        
        // Cập nhật database
        database.DatabaseManager.updateCustomerBalance(customer.cardId, balanceAfter)
        
        // Trừ tiền trên smart card nếu đã kết nối
        if (smartcard.BusCardManager.isConnected) {
            GlobalScope.launch(Dispatchers.IO) {
                smartcard.BusCardManager.deductBalance(tapAmount)
                    .onSuccess { println("Da tru tien tu Smart Card: ${String.format("%,.0f", tapAmount)} VND") }
                    .onFailure { println("Loi tru tien tu Smart Card: ${it.message}") }
            }
        }
        
        // Tạo description
        val description = "Quẹt thẻ thường - Trừ ${String.format("%,.0f", tapAmount)} VNĐ" +
                         if (prefix.isNotEmpty()) " ($prefix)" else ""
        
        // Lưu transaction
        database.DatabaseManager.insertTransaction(
            cardId = customer.cardId,
            transactionType = AppConstants.TRANSACTION_TYPE_TAP,
            amount = tapAmount,
            balanceBefore = balanceBefore,
            balanceAfter = balanceAfter,
            description = description
        )
        
        // Reload data
        reloadCustomersFromDB()
        selectedCustomer = customers.find { it.cardId == customer.cardId }
    }
    
    // Helper: Xử lý quẹt thẻ
    suspend fun handleCardTap(cardId: String) {
        val customer = customers.find { it.cardId == cardId } ?: return
        
        when (customer.cardType) {
            CardType.MONTHLY -> {
                val updatedCustomer = customer.checkAndConvertExpiredMonthlyCard()
                
                if (updatedCustomer.cardType != customer.cardType) {
                    // Thẻ tháng đã hết hạn, chuyển về thẻ thường
                    database.DatabaseManager.updateCustomer(updatedCustomer)
                    reloadCustomersFromDB()
                    val convertedCustomer = customers.find { it.cardId == cardId }
                    if (convertedCustomer != null && convertedCustomer.balance >= AppConstants.NORMAL_CARD_TAP_AMOUNT) {
                        processNormalCardTap(convertedCustomer, "đã chuyển từ thẻ tháng")
                    }
                } else {
                    // Thẻ tháng còn hạn, miễn phí
                    database.DatabaseManager.insertTransaction(
                        cardId = cardId,
                        transactionType = AppConstants.TRANSACTION_TYPE_TAP,
                        amount = 0.0,
                        balanceBefore = customer.balance,
                        balanceAfter = customer.balance,
                        description = "Quẹt thẻ tháng - Miễn phí (còn hạn đến ${customer.expiryDate})"
                    )
                    reloadCustomersFromDB()
                    selectedCustomer = customers.find { it.cardId == cardId }
                }
            }
            CardType.NORMAL -> {
                if (customer.balance >= AppConstants.NORMAL_CARD_TAP_AMOUNT) {
                    processNormalCardTap(customer)
                }
            }
        }
    }
    
    // Function để reload thống kê và hoạt động gần đây
    suspend fun reloadStatsAndActivities() {
        val loadedCustomers = database.DatabaseManager.getAllCustomers()
        val updatedCustomers = convertExpiredMonthlyCards(loadedCustomers)
        updateRecentActivities(updatedCustomers)
    }
    
    // Function để reload customers và refresh stats
    fun reloadCustomers() {
        reloadCustomersFromDB()
        kotlinx.coroutines.GlobalScope.launch {
            reloadStatsAndActivities()
            refreshCardStatus()
        }
    }

    fun handleTopUpTransaction(cardId: String, amount: Double) {
        uiScope.launch(Dispatchers.IO) {
            val customer = database.DatabaseManager.getCustomerByCardId(cardId)
            if (customer == null) {
                println("Khong tim thay khach hang de cap nhat nap tien: $cardId")
                return@launch
            }
            val balanceBefore = customer.balance
            val balanceAfter = balanceBefore + amount
            database.DatabaseManager.updateCustomerBalance(cardId, balanceAfter)
            database.DatabaseManager.insertTransaction(
                cardId = cardId,
                transactionType = AppConstants.TRANSACTION_TYPE_TOP_UP,
                amount = amount,
                balanceBefore = balanceBefore,
                balanceAfter = balanceAfter,
                description = "Nạp ${String.format("%,.0f", amount)} VNĐ vào thẻ"
            )
            withContext(Dispatchers.Main) {
                reloadCustomers()
            }
        }
    }

    fun handleMonthlyExtension(request: ExtensionRequest) {
        uiScope.launch(Dispatchers.IO) {
            val customer = database.DatabaseManager.getCustomerByCardId(request.cardId)
            if (customer == null) {
                println("Khong tim thay khach hang de gia han: ${request.cardId}")
                return@launch
            }
            if (request.quantity <= 0) {
                println("So thang gia han khong hop le: ${request.quantity}")
                return@launch
            }
            if (customer.balance < request.amount) {
                println("So du khong du de gia han thang cho the: ${request.cardId}")
                return@launch
            }

            val balanceBefore = customer.balance
            val balanceAfter = balanceBefore - request.amount
            val today = LocalDate.now()
            val baseDate = if (customer.cardType == CardType.MONTHLY && customer.expiryDate.isAfter(today)) {
                customer.expiryDate
            } else {
                today
            }
            val newExpiry = baseDate.plusMonths(request.quantity.toLong())
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val actionText = if (customer.cardType == CardType.MONTHLY && !customer.isMonthlyCardExpired()) {
                "gia hạn vé tháng thêm ${request.quantity} tháng"
            } else {
                "mua vé tháng ${request.quantity} tháng"
            }
            val description = "${customer.fullName} $actionText (hết hạn ${newExpiry.format(formatter)})"

            val updatedCustomer = customer.copy(
                balance = balanceAfter,
                cardType = CardType.MONTHLY,
                expiryDate = newExpiry
            )
            database.DatabaseManager.updateCustomer(updatedCustomer)
            database.DatabaseManager.insertTransaction(
                cardId = request.cardId,
                transactionType = AppConstants.TRANSACTION_TYPE_EXTEND_MONTHLY,
                amount = request.amount,
                balanceBefore = balanceBefore,
                balanceAfter = balanceAfter,
                description = description
            )

            if (!smartcard.BusCardManager.isConnected) {
                val connectResult = smartcard.BusCardManager.connect()
                if (connectResult.isFailure) {
                    println("Khong the ket noi the khi gia han: ${connectResult.exceptionOrNull()?.message}")
                }
            }
            if (smartcard.BusCardManager.isConnected) {
                val infoResult = smartcard.BusCardManager.updateCustomerInfo(
                    fullName = customer.fullName,
                    customerType = customer.customerType.displayName,
                    expiryDate = newExpiry.format(formatter),
                    cardType = CardType.MONTHLY.displayName,
                    linkedCustomerId = customer.linkedCustomerCode
                )
                if (infoResult.isFailure) {
                    println("Khong the cap nhat thong tin gia han len the: ${infoResult.exceptionOrNull()?.message}")
                }
                val balanceResult = smartcard.BusCardManager.updateBalance(balanceAfter)
                if (balanceResult.isFailure) {
                    println("Khong the cap nhat so du sau gia han tren the: ${balanceResult.exceptionOrNull()?.message}")
                }
            }

            withContext(Dispatchers.Main) {
                reloadCustomers()
                selectedCustomer = customers.find { it.cardId == request.cardId }
            }
        }
    }
    
    // Navigation Controller
    val navController = remember { NavController(Screen.Home) }
    
    // Load dữ liệu từ database khi khởi động
    LaunchedEffect(Unit) {
        delay(AppConstants.INITIAL_LOAD_DELAY_MS)
        val loadedCustomers = database.DatabaseManager.getAllCustomers()
        customers = convertExpiredMonthlyCards(loadedCustomers)
        isLoading = false
        updateRecentActivities(customers)
    }
    
    // Auto reload stats và refresh card status
    LaunchedEffect(Unit) {
        refreshCardStatus()
        while (true) {
            delay(AppConstants.STATS_RELOAD_INTERVAL_MS)
            reloadStatsAndActivities()
        }
    }
    
    // Reload stats khi có thay đổi trong customers
    LaunchedEffect(customers.size) {
        reloadStatsAndActivities()
    }
    MaterialTheme(
        colors = lightColors(
            primary = Color(0xFF6366F1),
            primaryVariant = Color(0xFF4F46E5),
            secondary = Color(0xFF10B981),
            background = Color(0xFFF9FAFB)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFEEF2FF),
                            Color(0xFFF9FAFB)
                        )
                    )
                )
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Bên trái: Menu chức năng (65%)
                Column(
                    modifier = Modifier
                        .weight(6.5f)
                        .fillMaxHeight()
                        .padding(start = 20.dp, top = 20.dp, end = 10.dp, bottom = 20.dp)
                ) {
                    // Header với gradient và animation
                    val infiniteTransition = rememberInfiniteTransition()
                    val gradientShift by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 1000f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(3000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(AppElevation.lg, RoundedCornerShape(AppRadius.lg))
                            .clip(RoundedCornerShape(AppRadius.lg))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        AppColors.PrimaryGradientStart,
                                        AppColors.PrimaryGradientEnd,
                                        AppColors.PrimaryAccent,
                                        AppColors.PrimaryGradientStart
                                    ),
                                    startX = gradientShift,
                                    endX = gradientShift + 1000f
                                )
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(AppSpacing.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon với background và rotation animation
                            val rotation by infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 360f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(4000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Restart
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .graphicsLayer { rotationZ = rotation },
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(AppSpacing.md + AppSpacing.xs))
                            Column {
                                Text(
                                    text = "🚌 Hệ thống quản lý thẻ xe bus",
                                    fontSize = AppTypography.h2Size,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(AppSpacing.xs))
                                Text(
                                    text = "Bus Card Management System",
                                    fontSize = AppTypography.bodyMediumSize,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            // Stats badge với animation
                            val badgeScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.05f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                )
                            )
                            Card(
                                backgroundColor = Color.White.copy(alpha = 0.2f),
                                elevation = 0.dp,
                                shape = RoundedCornerShape(AppRadius.md),
                                modifier = Modifier.scale(badgeScale)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                                    // Animated counter
                                    AnimatedCustomerCount(count = customers.size)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    // Menu buttons
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = AppElevation.sm
                    ) {
                        Text(
                            text = "MENU CHỨC NĂNG",
                            fontSize = AppTypography.h4Size,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Primary
                        )
                        Spacer(modifier = Modifier.height(AppSpacing.md))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            MenuButton(
                                text = "Nạp thông tin vào thẻ",
                                icon = Icons.Default.Add,
                                onClick = { navController.navigateTo(Screen.LoadCardInfo) },
                                modifier = Modifier.weight(1f),
                                gradientStart = AppColors.PurpleGradientStart,
                                gradientEnd = AppColors.PurpleGradientEnd
                            )
                            MenuButton(
                                text = "Nạp tiền - Gia hạn",
                                icon = Icons.Default.ShoppingCart,
                                onClick = {
                                    if (selectedCustomer == null) {
                                        selectedCustomer = customers.firstOrNull()
                                    }
                                    navController.navigateTo(Screen.Payment)
                                },
                                modifier = Modifier.weight(1f),
                                enabled = isCardConnected && isCardHasData,
                                gradientStart = AppColors.BlueGradientStart,
                                gradientEnd = AppColors.BlueGradientEnd
                            )
                        }
                        Spacer(modifier = Modifier.height(AppSpacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            MenuButton(
                                text = "Quản lý Smart Card",
                                icon = Icons.Default.AccountBox,
                                onClick = { navController.navigateTo(Screen.SmartCardManagement) },
                                modifier = Modifier.weight(1f),
                                enabled = isCardConnected && isCardHasData,
                                gradientStart = AppColors.IndigoGradientStart,
                                gradientEnd = AppColors.IndigoGradientEnd
                            )
                            MenuButton(
                                text = "Lộ Trình",
                                icon = Icons.Default.Send,
                                onClick = {
                                    if (selectedCustomer == null) {
                                        selectedCustomer = customers.firstOrNull()
                                    }
                                    navController.navigateTo(Screen.Route)
                                },
                                modifier = Modifier.weight(1f),
                                enabled = isCardConnected && isCardHasData,
                                gradientStart = AppColors.GreenGradientStart,
                                gradientEnd = AppColors.GreenGradientEnd
                            )
                        }
                        Spacer(modifier = Modifier.height(AppSpacing.sm))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            MenuButton(
                                text = "Quẹt thẻ tự động",
                                icon = Icons.Default.Star,
                                onClick = { navController.navigateTo(Screen.RealTimeTap) },
                                modifier = Modifier.weight(1f),
                                enabled = isCardConnected && isCardHasData,
                                gradientStart = AppColors.OrangeGradientStart,
                                gradientEnd = AppColors.OrangeGradientEnd
                            )
                            MenuButton(
                                text = "Xác thực vé",
                                icon = Icons.Default.CheckCircle,
                                onClick = {
                                    val customer = selectedCustomer ?: customers.firstOrNull()
                                    if (customer != null) {
                                        navController.navigateTo(Screen.TicketValidation(customer))
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = isCardConnected && isCardHasData && (selectedCustomer != null || customers.isNotEmpty()),
                                gradientStart = AppColors.TealGradientStart,
                                gradientEnd = AppColors.TealGradientEnd
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    
                    // Hoạt động gần đây - full width
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = AppElevation.sm
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                // Icon từ resources
                                val historyIcon = ui.components.loadIconFromResource("icons/history.png")
                                if (historyIcon != null) {
                                    Image(
                                        bitmap = historyIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = "HOẠT ĐỘNG GẦN ĐÂY",
                                    fontSize = AppTypography.h5size,
                                    fontWeight = FontWeight.Bold,
                                    color = AppColors.Primary
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(AppSpacing.md))
                            
                            // Danh sách hoạt động - chiếm phần còn lại
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                if (recentActivities.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Chưa có hoạt động nào",
                                            fontSize = 12.sp,
                                            color = AppColors.TextSecondary
                                        )
                                    }
                                } else {
                                    recentActivities.forEach { activity ->
                                        ActivityItem(activity)
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Bên phải: Danh sách khách hàng (35%)
                Column(
                    modifier = Modifier
                        .weight(3.5f)
                        .fillMaxHeight()
                        .padding(start = 10.dp, top = 20.dp, end = 20.dp, bottom = 20.dp)
                ) {
                    // Danh sách khách hàng
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                        elevation = AppElevation.sm
                    ) {
                        // Header với title và search field
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DANH SÁCH KHÁCH HÀNG",
                                fontSize = AppTypography.h5size,
                                fontWeight = FontWeight.Bold,
                                color = AppColors.Primary
                            )
                            // Search field
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.width(200.dp),
                                placeholder = { Text("Tìm kiếm...", fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Tìm kiếm",
                                        tint = AppColors.TextSecondary
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Xóa",
                                                modifier = Modifier.size(20.dp),
                                                tint = AppColors.TextSecondary
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedBorderColor = AppColors.Primary,
                                    unfocusedBorderColor = AppColors.Border,
                                    backgroundColor = AppColors.Surface
                                ),
                                shape = RoundedCornerShape(AppRadius.md)
                            )
                        }
                        Divider(
                            modifier = Modifier.padding(vertical = AppSpacing.md),
                            color = AppColors.Divider
                        )
                        // Loading state với shimmer animation
                        if (isLoading) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                repeat(3) {
                                    LoadingCard()
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        } else if (customers.isEmpty()) {
                            // Empty state với animation
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(animationSpec = tween(800)) +
                                        expandVertically(animationSpec = tween(600))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = Color.Gray.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            text = "Chưa có khách hàng",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        } else {
                            // Filter customers theo search query
                            val filteredCustomers = remember(customers, searchQuery) {
                                if (searchQuery.isBlank()) {
                                    customers
                                } else {
                                    val query = searchQuery.lowercase().trim()
                                    customers.filter { customer ->
                                        customer.fullName.lowercase().contains(query) ||
                                        customer.cardId.lowercase().contains(query) ||
                                        customer.cccd.lowercase().contains(query)
                                    }
                                }
                            }
                            // Customer list với staggered animation
                            if (filteredCustomers.isEmpty() && searchQuery.isNotEmpty()) {
                                // Empty search result
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(AppSpacing.xl),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(64.dp),
                                            tint = AppColors.TextSecondary.copy(alpha = 0.5f)
                                        )
                                        Text(
                                            text = "Không tìm thấy khách hàng",
                                            fontSize = AppTypography.bodyLargeSize,
                                            color = AppColors.TextSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    filteredCustomers.forEachIndexed { index, customer ->
                                        AnimatedCustomerListItem(
                                            customer = customer,
                                            index = index,
                                            onClick = {
                                                navController.navigateTo(Screen.CustomerCardInfo(customer))
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Navigation Host - Quản lý tất cả các screen
        NavigationHost(
            navController = navController,
            customers = customers,
            selectedCustomer = selectedCustomer,
            onCustomerUpdated = { reloadCustomers() },
            onCustomerDeleted = { customer ->
                database.DatabaseManager.deleteCustomer(customer.cardId)
                reloadCustomers()
                if (selectedCustomer?.cardId == customer.cardId) {
                    selectedCustomer = null
                }
            },
            isCardConnected = isCardConnected,
            isCardHasData = isCardHasData,
            onTopUpCompleted = { cardId, amount ->
                handleTopUpTransaction(cardId, amount)
            },
            onTapDetected = { cardId, _ ->
                uiScope.launch {
                    handleCardTap(cardId)
                    reloadStatsAndActivities()
                }
            },
            onExtensionRequest = { request ->
                handleMonthlyExtension(request)
            }
        )
    }
}
