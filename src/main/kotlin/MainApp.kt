import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
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
import ui.AppSpacing
import ui.components.main.*
import utils.AppConstants
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Màn hình chính của ứng dụng
 * Tích hợp tất cả các màn hình con
 */
@Composable
fun MainApp() {
    // State quản lý danh sách khách hàng - Load từ database
    var customers by remember { mutableStateOf(emptyList<Customer>()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val uiScope = rememberCoroutineScope()
    
    // State cho hoạt động gần đây
    var recentActivities by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    
    // Helper: Tự động chuyển thẻ tháng hết hạn về thẻ thường
     fun convertExpiredMonthlyCards(customers: List<Customer>): List<Customer> = customers.map { customer ->
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
                    // Header
                    MainHeader(customerCount = customers.size)
                    
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    
                    // Menu buttons
                    MainMenu(
                        navController = navController,
                        isCardConnected = isCardConnected,
                        isCardHasData = isCardHasData,
                        selectedCustomer = selectedCustomer,
                        customers = customers
                    )
                    
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    
                    // Hoạt động gần đây
                    RecentActivitySection(recentActivities = recentActivities)
                }
                
                // Bên phải: Danh sách khách hàng (35%)
                Column(
                    modifier = Modifier
                        .weight(3.5f)
                        .fillMaxHeight()
                        .padding(start = 10.dp, top = 20.dp, end = 20.dp, bottom = 20.dp)
                ) {
                    CustomerListSection(
                        customers = customers,
                        isLoading = isLoading,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onCustomerClick = { customer ->
                            navController.navigateTo(Screen.CustomerCardInfo(customer))
                        }
                    )
                }
            }
        }
        // Navigation Host - Quản lý tất cả các screen
        NavigationHost(
            navController = navController,
            customers = customers,
            onCustomerUpdated = { reloadCustomers() },
            onCustomerDeleted = { customer ->
                database.DatabaseManager.deleteCustomer(customer.cardId)
                reloadCustomers()
                if (selectedCustomer?.cardId == customer.cardId) {
                    selectedCustomer = null
                }
            },
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
