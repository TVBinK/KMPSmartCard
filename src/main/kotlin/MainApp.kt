import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import models.*
import ui.screens.*
import ui.components.*
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    
    // Load dữ liệu từ database khi khởi động với animation
    LaunchedEffect(Unit) {
        delay(500) // Delay nhỏ để animation mượt hơn
        val loadedCustomers = database.DatabaseManager.getAllCustomers()
        customers = loadedCustomers
        isLoading = false
        println("📊 Loaded ${loadedCustomers.size} customers from database")
    }
    
    // State quản lý dialog hiển thị
    var showLoadCardInfoDialog by remember { mutableStateOf(false) }
    var showCustomerInfoDialog by remember { mutableStateOf(false) }
    var showPaymentDialog by remember { mutableStateOf(false) }
    var showAutoDeductionDialog by remember { mutableStateOf(false) }
    var showRouteTransferDialog by remember { mutableStateOf(false) }
    var showSmartCardDialog by remember { mutableStateOf(false) }
    var showRealTimeTapDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var customerToDelete by remember { mutableStateOf<Customer?>(null) }
    var showEditCustomerDialog by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<Customer?>(null) }
    
    // State cho các màn hình
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var currentTrip by remember { mutableStateOf<Trip?>(null) }
    
    // State theo dõi trạng thái kết nối thẻ
    var isCardConnected by remember { mutableStateOf(false) }
    var isCardHasData by remember { mutableStateOf(false) }
    
    // Function để cập nhật trạng thái thẻ (gọi khi cần)
    suspend fun refreshCardStatus() {
        isCardConnected = smartcard.BusCardManager.isConnected
        
        if (isCardConnected) {
            val checkResult = withContext(Dispatchers.IO) {
                smartcard.BusCardManager.checkCardCreated()
            }
            isCardHasData = checkResult.getOrNull() ?: false
            println("🔄 Card status refreshed - Connected: $isCardConnected, Has Data: $isCardHasData")
        } else {
            isCardHasData = false
        }
    }
    
    // Kiểm tra trạng thái ban đầu
    LaunchedEffect(Unit) {
        refreshCardStatus()
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
                // Main content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(20.dp)
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
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF6366F1),
                                        Color(0xFF8B5CF6),
                                        Color(0xFFEC4899),
                                        Color(0xFF6366F1)
                                    ),
                                    startX = gradientShift,
                                    endX = gradientShift + 1000f
                                )
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(24.dp),
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
                            
                            Spacer(modifier = Modifier.width(20.dp))
                            
                            Column {
                                Text(
                                    text = "🚌 Hệ thống quản lý thẻ xe bus",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                Text(
                                    text = "Bus Card Management System",
                                    fontSize = 14.sp,
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
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.scale(badgeScale)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    
                                    // Animated counter
                                    AnimatedCustomerCount(count = customers.size)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Menu buttons
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 2.dp,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "MENU CHỨC NĂNG",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2196F3)
                                )
                            
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MenuButton(
                                        text = "Nạp thông tin vào thẻ",
                                        icon = Icons.Default.Add,
                                        onClick = { showLoadCardInfoDialog = true },
                                        modifier = Modifier.weight(1f),
                                        backgroundColor = Color(0xFF9C27B0)
                                        // Luôn enabled - đây là nơi kết nối Simulator
                                    )
                                    
                                    MenuButton(
                                        text = "Nạp tiền - Gia hạn",
                                        icon = Icons.Default.ShoppingCart,
                                        onClick = { 
                                            if (selectedCustomer == null) {
                                                selectedCustomer = customers.firstOrNull()
                                            }
                                            showPaymentDialog = true 
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = isCardConnected && isCardHasData // Phải có dữ liệu
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    MenuButton(
                                        text = "Quản lý Smart Card",
                                        icon = Icons.Default.AccountBox, // Icon thẻ/tài khoản
                                        onClick = { showSmartCardDialog = true },
                                        modifier = Modifier.weight(1f),
                                        backgroundColor = Color(0xFF9C27B0),
                                        enabled = isCardConnected && isCardHasData // Chỉ enabled khi đã nạp thẻ
                                    )
                                    
                                    MenuButton(
                                        text = "Chuyển tuyến",
                                        icon = Icons.Default.Send,
                                        onClick = {
                                            if (selectedCustomer == null) {
                                                selectedCustomer = customers.firstOrNull()
                                            }
                                            currentTrip = getSampleTrip()
                                            showRouteTransferDialog = true
                                        },
                                        modifier = Modifier.weight(1f),
                                        enabled = isCardConnected && isCardHasData // Phải có dữ liệu
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                MenuButton(
                                    text = "Quẹt thẻ tự động - Trừ tiền",
                                    icon = Icons.Default.Star, // Icon sao/điểm thưởng (giống quẹt thẻ)
                                    onClick = {
                                        showRealTimeTapDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    backgroundColor = Color(0xFF4CAF50),
                                    enabled = isCardConnected && isCardHasData
                                )
                            }
                        }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Danh sách khách hàng
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        elevation = 2.dp,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "DANH SÁCH KHÁCH HÀNG",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2196F3)
                                    )
                                    
                                    Text(
                                        text = "${customers.size} khách hàng",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                
                                Divider(modifier = Modifier.padding(vertical = 12.dp))
                            
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
                                            // Animated Icon với pulse effect
                                            val infiniteTransition = rememberInfiniteTransition()
                                            val scale by infiniteTransition.animateFloat(
                                                initialValue = 1f,
                                                targetValue = 1.1f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(1000, easing = FastOutSlowInEasing),
                                                    repeatMode = RepeatMode.Reverse
                                                )
                                            )
                                            
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .scale(scale),
                                                tint = Color(0xFF9C27B0)
                                            )
                                            
                                            Spacer(modifier = Modifier.height(16.dp))
                                            
                                            Text(
                                                text = "Chưa có khách hàng nào",
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF424242)
                                            )
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            Text(
                                                text = "Thẻ ban đầu đang rỗng",
                                                fontSize = 14.sp,
                                                color = Color.Gray
                                            )
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            
                                            Text(
                                                text = "Nhấn 'Nạp thông tin vào thẻ' để bắt đầu",
                                                fontSize = 13.sp,
                                                color = Color.Gray
                                            )
                                            
                                            Spacer(modifier = Modifier.height(24.dp))
                                            
                                            // Animated Button với hover effect
                                            val interactionSource = remember { MutableInteractionSource() }
                                            val isHovered by interactionSource.collectIsHoveredAsState()
                                            val buttonScale by animateFloatAsState(
                                                targetValue = if (isHovered) 1.05f else 1f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow
                                                )
                                            )
                                            
                                            Button(
                                                onClick = { showLoadCardInfoDialog = true },
                                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF9C27B0)),
                                                modifier = Modifier
                                                    .height(48.dp)
                                                    .scale(buttonScale),
                                                interactionSource = interactionSource
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = null,
                                                    tint = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Nạp thông tin vào thẻ",
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Customer list với staggered animation
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    customers.forEachIndexed { index, customer ->
                                        AnimatedCustomerListItem(
                                            customer = customer,
                                            index = index,
                                            onClick = {
                                                selectedCustomer = customer
                                            }
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Right panel - Ticket validation
                TicketValidationPanel(
                    customer = selectedCustomer,
                    onEdit = { customer ->
                        // Mở dialog edit khách hàng
                        customerToEdit = customer
                        showEditCustomerDialog = true
                    },
                    onDelete = { customer ->
                        // Hiển thị dialog xác nhận xóa
                        customerToDelete = customer
                        showDeleteConfirmDialog = true
                    }
                )
            }
            
            // Dialogs
            if (showLoadCardInfoDialog) {
                LoadCardInfoDialog(
                    onDismiss = { 
                        showLoadCardInfoDialog = false
                        kotlinx.coroutines.GlobalScope.launch {
                            refreshCardStatus()
                        }
                    },
                    onSuccess = { customer ->
                        println("📝 Attempting to save customer: ${customer.cardId} - ${customer.fullName}")
                        println("   Customer Type: ${customer.customerType}")
                        println("   Card Type: ${customer.cardType}")
                        println("   Balance: ${customer.balance}")
                        println("   Expiry Date: ${customer.expiryDate}")
                        
                        // Lưu vào database
                        val saved = database.DatabaseManager.insertCustomer(customer)
                        
                        if (saved) {
                            // Reload toàn bộ danh sách từ database để đảm bảo đồng bộ
                            customers = database.DatabaseManager.getAllCustomers()
                            println("✅ Customer saved to database and reloaded ${customers.size} customers")
                            
                            // Debug: In ra danh sách customers
                            customers.forEach { c ->
                                println("   - ${c.cardId}: ${c.fullName} (Balance: ${c.balance})")
                            }
                        } else {
                            println("❌ Failed to save customer to database")
                        }
                        
                        showLoadCardInfoDialog = false
                        kotlinx.coroutines.GlobalScope.launch {
                            refreshCardStatus()
                        }
                    }
                )
            }
            
            if (showCustomerInfoDialog) {
                CustomerInfoDialog(
                    onDismiss = { showCustomerInfoDialog = false },
                    onSave = { customer ->
                        customers = customers + customer
                        showCustomerInfoDialog = false
                    }
                )
            }
            
            if (showPaymentDialog) {
                PaymentDialog(
                    onDismiss = { showPaymentDialog = false },
                    customers = customers,
                    onTopUp = { cardId, amount ->
                        // Nạp tiền và cập nhật database (Smart card đã nạp trong PaymentScreen)
                        val customer = customers.find { it.cardId == cardId }
                        if (customer != null) {
                            val newBalance = customer.balance + amount
                            database.DatabaseManager.updateCustomerBalance(cardId, newBalance)
                            
                            // Insert transaction vào database
                            database.DatabaseManager.insertTransaction(
                                cardId = cardId,
                                transactionType = "TOP_UP",
                                amount = amount,
                                balanceBefore = customer.balance,
                                balanceAfter = newBalance,
                                description = "Nạp tiền vào thẻ: ${String.format("%,.0f", amount)} VNĐ"
                            )
                            
                            // Reload từ database
                            customers = database.DatabaseManager.getAllCustomers()
                            
                            // Cập nhật selectedCustomer nếu là khách hàng đang chọn
                            if (selectedCustomer?.cardId == cardId) {
                                selectedCustomer = customers.find { it.cardId == cardId }
                            }
                            
                            println("✅ Top-up completed and reloaded ${customers.size} customers")
                        }
                    },
                    onExtension = { request ->
                        // Gia hạn và cập nhật database + smart card
                        val customer = customers.find { it.cardId == request.cardId }
                        if (customer != null) {
                            when (request.extensionType) {
                                ExtensionType.MONTHLY -> {
                                    println("💳 ========== GIA HẠN VÉ THÁNG ==========")
                                    println("💰 Số tiền thanh toán: ${String.format("%,.0f", request.amount)} VNĐ")
                                    println("💵 Số dư trước: ${String.format("%,.0f", customer.balance)} VNĐ")
                                    
                                    // TRỪ TIỀN trước
                                    val newBalance = customer.balance - request.amount
                                    database.DatabaseManager.updateCustomerBalance(request.cardId, newBalance)
                                    
                                    // Trừ tiền từ Smart Card (nếu đã kết nối)
                                    if (smartcard.BusCardManager.isConnected) {
                                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val deductResult = smartcard.BusCardManager.deductBalance(request.amount)
                                            deductResult.onSuccess {
                                                println("✅ Đã trừ tiền từ Smart Card: ${String.format("%,.0f", request.amount)} VNĐ")
                                            }.onFailure { error ->
                                                println("⚠️ Lỗi trừ tiền từ Smart Card: ${error.message}")
                                            }
                                        }
                                    }
                                    
                                    // Gia hạn ngày hết hạn
                                    val newExpiryDate = customer.expiryDate.plusMonths(request.quantity.toLong())
                                    val updatedCustomer = customer.copy(
                                        expiryDate = newExpiryDate,
                                        balance = newBalance
                                    )
                                    database.DatabaseManager.updateCustomer(updatedCustomer)
                                    
                                    // Insert transaction
                                    database.DatabaseManager.insertTransaction(
                                        cardId = request.cardId,
                                        transactionType = "EXTEND_MONTHLY",
                                        amount = request.amount,
                                        balanceBefore = customer.balance,
                                        balanceAfter = newBalance,
                                        description = "Gia hạn vé tháng ${request.quantity} tháng - ${String.format("%,.0f", request.amount)} VNĐ"
                                    )
                                    
                                    customers = database.DatabaseManager.getAllCustomers()
                                    println("💵 Số dư sau: ${String.format("%,.0f", newBalance)} VNĐ")
                                    println("✅ ========== GIA HẠN THÀNH CÔNG ==========\n")
                                }
                                ExtensionType.TRIPS -> {
                                    val newBalance = customer.balance + request.amount
                                    
                                    // Cập nhật database
                                    database.DatabaseManager.updateCustomerBalance(request.cardId, newBalance)
                                    
                                    // Cập nhật smart card (nếu đã kết nối)
                                    if (smartcard.BusCardManager.isConnected) {
                                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            val topUpResult = smartcard.BusCardManager.topUpBalance(request.amount)
                                            topUpResult.onSuccess {
                                                println("✅ Top-up to smart card success: $it VNĐ")
                                            }.onFailure { error ->
                                                println("⚠️ Top-up to smart card failed: ${error.message}")
                                            }
                                        }
                                    }
                                    
                                    // Insert transaction
                                    database.DatabaseManager.insertTransaction(
                                        cardId = request.cardId,
                                        transactionType = "TOP_UP",
                                        amount = request.amount,
                                        balanceBefore = customer.balance,
                                        balanceAfter = newBalance,
                                        description = "Nạp thêm tiền - Gia hạn ${request.quantity} lượt"
                                    )
                                    
                                    customers = database.DatabaseManager.getAllCustomers()
                                    println("✅ Trips extension completed")
                                }
                            }
                            
                            // Cập nhật selectedCustomer nếu là khách hàng đang chọn
                            if (selectedCustomer?.cardId == customer.cardId) {
                                selectedCustomer = customers.find { it.cardId == customer.cardId }
                            }
                        }
                    }
                )
            }
            
            // AutoDeductionDialog đã được thay thế bằng RealTimeTapDialog
            
            
            if (showRouteTransferDialog && selectedCustomer != null && currentTrip != null) {
                RouteTransferDialog(
                    customer = selectedCustomer!!,
                    currentTrip = currentTrip!!,
                    availableRoutes = getSampleRoutes(),
                    onTransfer = { routeId ->
                        kotlinx.coroutines.GlobalScope.launch {
                            try {
                                println("🔄 ========== CHUYỂN TUYẾN ==========")
                                println("👤 Khách hàng: ${selectedCustomer?.fullName}")
                                println("📍 Từ tuyến: ${currentTrip?.tapOn?.routeName}")
                                println("🎯 Sang tuyến: $routeId")
                                
                                val customerCardId = selectedCustomer?.cardId ?: ""
                                val customerBalance = selectedCustomer?.balance ?: 0.0
                                
                                // Kiểm tra thời gian còn lại (30 phút miễn phí)
                                val remainingTime = currentTrip?.remainingTransferTime() ?: 0
                                println("⏰ Thời gian còn lại: $remainingTime phút")
                                
                                // Logic thẻ xe buýt thật: Quẹt 1 lần → Đi miễn phí 30 phút (chuyển tuyến thoải mái)
                                if (remainingTime <= 0) {
                                    println("❌ Hết thời gian hiệu lực! Vui lòng quẹt thẻ lại để tạo chuyến đi mới.")
                                    return@launch
                                }
                                
                                println("✅ Chuyển tuyến MIỄN PHÍ (trong 30 phút)")
                                val newBalance = customerBalance // Không trừ tiền
                                
                                // Cập nhật thông tin chuyến đi mới (giữ nguyên thời gian gốc)
                                currentTrip = currentTrip?.copy(
                                    tapOn = currentTrip!!.tapOn.copy(
                                        routeId = routeId,
                                        routeName = routeId
                                        // Giữ nguyên timestamp gốc để tiếp tục đếm 30 phút từ lần quẹt đầu
                                    )
                                )
                                
                                // Cập nhật smart card
                                if (smartcard.BusCardManager.isConnected) {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        val updateResult = smartcard.BusCardManager.updateLastTapInfo(
                                            routeId = routeId,
                                            tapType = "TRANSFER",
                                            timestamp = java.time.LocalDateTime.now().toString()
                                        )
                                        updateResult.onSuccess {
                                            println("✅ Đã cập nhật thông tin chuyển tuyến lên thẻ")
                                        }.onFailure { error ->
                                            println("⚠️ Lỗi cập nhật thẻ: ${error.message}")
                                        }
                                    }
                                }
                                
                                // Lưu transaction vào database
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    database.DatabaseManager.insertTransaction(
                                        cardId = customerCardId,
                                        transactionType = "ROUTE_TRANSFER",
                                        amount = 0.0, // Miễn phí 100%
                                        balanceBefore = customerBalance,
                                        balanceAfter = customerBalance, // Không đổi số dư
                                        description = "Chuyển tuyến miễn phí: $routeId (Còn $remainingTime phút)"
                                    )
                                    println("💾 Đã lưu giao dịch chuyển tuyến miễn phí")
                                }
                                
                                // Reload danh sách khách hàng
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    customers = database.DatabaseManager.getAllCustomers()
                                }
                                
                                // Cập nhật selection
                                selectedCustomer = customers.find { it.cardId == customerCardId }
                                
                                println("✅ ========== CHUYỂN TUYẾN THÀNH CÔNG ==========\n")
                                showRouteTransferDialog = false
                            } catch (e: Exception) {
                                println("❌ Lỗi chuyển tuyến: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    },
                    onDismiss = { showRouteTransferDialog = false }
                )
            }
            
            if (showSmartCardDialog) {
                SmartCardManagementDialog(
                    onDismiss = { showSmartCardDialog = false }
                )
            }
            
            // Real-time Tap Dialog
            if (showRealTimeTapDialog) {
                RealTimeTapDialog(
                    onDismiss = { showRealTimeTapDialog = false },
                    onTapDetected = { cardId, tapType ->
                        kotlinx.coroutines.GlobalScope.launch {
                            println("🎫 ========== QUẸT THẺ TỰ ĐỘNG ==========")
                            println("🎫 Card ID phát hiện: $cardId")
                            println("🎫 Loại quẹt: $tapType")
                            
                            // Tìm customer theo cardId
                            val customer = customers.find { it.cardId == cardId }
                            
                            if (customer != null) {
                                println("✅ Tìm thấy khách hàng: ${customer.fullName}")
                                println("🎫 Loại thẻ: ${customer.cardType.displayName}")
                                println("💰 Số dư hiện tại: ${String.format("%,.0f", customer.balance)} VNĐ")
                                
                                // Kiểm tra loại thẻ
                                when (customer.cardType) {
                                    models.CardType.MONTHLY -> {
                                        // ✅ VÉ THÁNG: Không trừ tiền, chỉ kiểm tra còn hạn
                                        println("🎫 Vé tháng - Không trừ tiền")
                                        
                                        if (customer.getCardStatus() == models.CardStatus.VALID) {
                                            // Ghi nhận lượt đi (không trừ tiền)
                                            database.DatabaseManager.insertTransaction(
                                                cardId = cardId,
                                                transactionType = "TAP",
                                                amount = 0.0,
                                                balanceBefore = customer.balance,
                                                balanceAfter = customer.balance,
                                                description = "Quẹt thẻ tháng - Miễn phí (còn hạn đến ${customer.expiryDate})"
                                            )
                                            println("📝 Đã ghi nhận lượt đi (miễn phí)")
                                            
                                            // Reload customers
                                            customers = database.DatabaseManager.getAllCustomers()
                                            selectedCustomer = customers.find { it.cardId == cardId }
                                            
                                            println("✅ ========== QUẸT THẺ THÀNH CÔNG (VÉ THÁNG) ==========")
                                            println("✅ Khách hàng: ${customer.fullName} ($cardId)")
                                            println("✅ Không trừ tiền (Vé tháng)")
                                            println("✅ Còn hạn đến: ${customer.expiryDate}")
                                            println("==========================================")
                                        } else {
                                            println("❌ VÉ THÁNG ĐÃ HẾT HẠN!")
                                            println("❌ Ngày hết hạn: ${customer.expiryDate}")
                                            println("==========================================")
                                        }
                                    }
                                    
                                    models.CardType.SINGLE_TRIP -> {
                                        // 💰 VÉ LƯỢT: Trừ tiền 7,000đ
                                        val fare = 7000.0
                                        println("💵 Vé lượt - Cước phí: ${String.format("%,.0f", fare)} VNĐ/lượt")
                                        
                                        // Kiểm tra số dư
                                        if (customer.balance >= fare) {
                                            println("🔄 Đang trừ tiền từ Smart Card...")
                                            
                                            // Trừ tiền từ smart card
                                            val deductResult = withContext(Dispatchers.IO) {
                                                smartcard.BusCardManager.deductBalance(fare)
                                            }
                                            
                                            if (deductResult.isSuccess) {
                                                val cardNewBalance = deductResult.getOrNull() ?: 0.0
                                                println("✅ Trừ tiền trên Smart Card thành công! Số dư mới: ${String.format("%,.0f", cardNewBalance)} VNĐ")
                                                
                                                // Cập nhật database
                                                val newBalance = customer.balance - fare
                                                database.DatabaseManager.updateCustomerBalance(cardId, newBalance)
                                                println("💾 Đã cập nhật database")
                                                
                                                // Insert transaction
                                                database.DatabaseManager.insertTransaction(
                                                    cardId = cardId,
                                                    transactionType = "DEDUCTION",
                                                    amount = fare,
                                                    balanceBefore = customer.balance,
                                                    balanceAfter = newBalance,
                                                    description = "Quẹt thẻ lượt - Cước xe buýt HN (7,000đ/lượt)"
                                                )
                                                println("📝 Đã ghi lại giao dịch")
                                                
                                                // Reload customers
                                                customers = database.DatabaseManager.getAllCustomers()
                                                selectedCustomer = customers.find { it.cardId == cardId }
                                                
                                                println("✅ ========== QUẸT THẺ THÀNH CÔNG (VÉ LƯỢT) ==========")
                                                println("✅ Khách hàng: ${customer.fullName} ($cardId)")
                                                println("✅ Đã trừ: ${String.format("%,.0f", fare)} VNĐ")
                                                println("✅ Số dư còn lại: ${String.format("%,.0f", newBalance)} VNĐ")
                                                println("==========================================")
                                            } else {
                                                println("❌ Lỗi trừ tiền từ Smart Card: ${deductResult.exceptionOrNull()?.message}")
                                            }
                                        } else {
                                            println("⚠️ SỐ DƯ KHÔNG ĐỦ!")
                                            println("⚠️ Card ID: $cardId")
                                            println("⚠️ Số dư hiện tại: ${String.format("%,.0f", customer.balance)} VNĐ")
                                            println("⚠️ Cần: ${String.format("%,.0f", fare)} VNĐ")
                                            println("==========================================")
                                        }
                                    }
                                }
                            } else {
                                println("❌ KHÔNG TÌM THẤY KHÁCH HÀNG!")
                                println("❌ Card ID: $cardId")
                                println("❌ Vui lòng kiểm tra lại hoặc nạp thông tin vào thẻ trước")
                                println("==========================================")
                            }
                        }
                    }
                )
            }
            
            // Edit Customer Dialog
            if (showEditCustomerDialog && customerToEdit != null) {
                EditCustomerDialog(
                    customer = customerToEdit!!,
                    onDismiss = { 
                        showEditCustomerDialog = false
                        customerToEdit = null
                    },
                    onSave = { updatedCustomer ->
                        // Cập nhật vào database
                        database.DatabaseManager.updateCustomer(updatedCustomer)
                        // Reload danh sách
                        customers = database.DatabaseManager.getAllCustomers()
                        // Cập nhật selection nếu đang chọn khách hàng này
                        if (selectedCustomer?.cardId == updatedCustomer.cardId) {
                            selectedCustomer = customers.find { it.cardId == updatedCustomer.cardId }
                        }
                        showEditCustomerDialog = false
                        customerToEdit = null
                        println("✅ Updated customer: ${updatedCustomer.cardId}")
                    }
                )
            }
            
            // Delete Confirmation Dialog
            if (showDeleteConfirmDialog && customerToDelete != null) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showDeleteConfirmDialog = false }
                ) {
                    Card(
                        modifier = Modifier
                            .width(400.dp)
                            .padding(16.dp),
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = Color(0xFFFF5252)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Xác nhận xóa khách hàng",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212121)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Bạn có chắc chắn muốn xóa khách hàng này không?",
                                fontSize = 14.sp,
                                color = Color(0xFF757575),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Card(
                                backgroundColor = Color(0xFFFFF5F5),
                                elevation = 0.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "Tên: ${customerToDelete!!.fullName}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Card ID: ${customerToDelete!!.cardId}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { showDeleteConfirmDialog = false },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = Color(0xFF9E9E9E)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Hủy", color = Color.White)
                                }
                                
                                Button(
                                    onClick = {
                                        customerToDelete?.let { customer ->
                                            // Xóa khách hàng khỏi database
                                            database.DatabaseManager.deleteCustomer(customer.cardId)
                                            // Reload danh sách
                                            customers = database.DatabaseManager.getAllCustomers()
                                            // Clear selection nếu đang chọn khách hàng này
                                            if (selectedCustomer?.cardId == customer.cardId) {
                                                selectedCustomer = null
                                            }
                                            println("✅ Deleted customer: ${customer.cardId}")
                                        }
                                        showDeleteConfirmDialog = false
                                        customerToDelete = null
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = Color(0xFFFF5252)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Xóa", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated customer count
 */
@Composable
private fun AnimatedCustomerCount(count: Int) {
    var displayCount by remember { mutableStateOf(0) }
    
    LaunchedEffect(count) {
        if (count > displayCount) {
            // Count up animation
            while (displayCount < count) {
                displayCount++
                delay(50)
            }
        } else {
            displayCount = count
        }
    }
    
    Text(
        text = "$displayCount khách hàng",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
}

/**
 * Menu button component với hover animation
 */
@Composable
private fun MenuButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF2196F3),
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isHovered && enabled) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isHovered && enabled) 8.dp else 2.dp,
        animationSpec = tween(200)
    )
    
    Surface(
        modifier = modifier
            .height(60.dp)
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        color = backgroundColor,
        elevation = elevation,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = Color.White.copy(alpha = if (enabled) 1f else 0.5f)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = if (enabled) 1f else 0.5f)
                )
            }
            
            // Overlay khi disabled
            if (!enabled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.3f))
                )
            }
        }
    }
}

/**
 * Customer list item với animation
 */
@Composable
private fun AnimatedCustomerListItem(
    customer: Customer,
    index: Int,
    onClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(key1 = customer.cardId) {
        delay(index * 50L) // Staggered animation
        isVisible = true
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(400)) + 
                slideInHorizontally(
                    initialOffsetX = { -it / 2 },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                )
    ) {
        CustomerListItem(
            customer = customer,
            onClick = onClick
        )
    }
}

/**
 * Customer list item
 */
@Composable
private fun CustomerListItem(
    customer: Customer,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isHovered) 6.dp else 2.dp,
        animationSpec = tween(200)
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        elevation = elevation,
        backgroundColor = if (customer.isValid()) Color.White else Color(0xFFFFF5F5),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onClick() }
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated Image với rotation
            val rotation by animateFloatAsState(
                targetValue = if (isHovered) 5f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy
                )
            )
            
            Box(modifier = Modifier.graphicsLayer { rotationZ = rotation }) {
                ImagePlaceholder(
                    photoPath = customer.photoPath,
                    photoBytes = customer.photoBytes,
                    size = Pair(50, 60)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.fullName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Card ID: ${customer.cardId}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
                
                // Animated balance
                AnimatedBalance(balance = customer.balance)
            }
            
            // Status indicator (chỉ icon, không cần text)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        color = if (customer.isValid()) Color(0xFF4CAF50) else Color(0xFFF44336),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (customer.isValid()) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = if (customer.isValid()) "Hợp lệ" else "Hết hạn",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Xem chi tiết",
                tint = Color.Gray,
                modifier = Modifier.graphicsLayer {
                    translationX = if (isHovered) 5f else 0f
                }
            )
        }
    }
}

/**
 * Animated Balance component
 */
@Composable
private fun AnimatedBalance(balance: Double) {
    var targetBalance by remember { mutableStateOf(0.0) }
    var currentBalance by remember { mutableStateOf(0.0) }
    
    LaunchedEffect(balance) {
        targetBalance = balance
        val duration = 800L
        val steps = 40
        val increment = (targetBalance - currentBalance) / steps
        
        repeat(steps) {
            currentBalance += increment
            delay(duration / steps)
        }
        currentBalance = targetBalance
    }
    
    Text(
        text = "${String.format("%,.0f", currentBalance)} VNĐ",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = if (currentBalance > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
    )
}

/**
 * Dữ liệu mẫu
 */
private fun getSampleCustomers(): List<Customer> {
    return listOf(
        Customer(
            id = "1",
            fullName = "Nguyễn Văn A",
            customerType = CustomerType.STUDENT,
            cardType = CardType.MONTHLY,
            expiryDate = LocalDate.now().plusMonths(2),
            balance = 150000.0,
            cardId = "CARD-001",
            linkedCustomerCode = "HSSV001"
        ),
        Customer(
            id = "2",
            fullName = "Trần Thị B",
            customerType = CustomerType.ELDERLY,
            cardType = CardType.SINGLE_TRIP,
            expiryDate = LocalDate.now().plusDays(5),
            balance = 50000.0,
            cardId = "CARD-002"
        ),
        Customer(
            id = "3",
            fullName = "Lê Văn C",
            customerType = CustomerType.NORMAL,
            cardType = CardType.SINGLE_TRIP,
            expiryDate = LocalDate.now().minusDays(10),
            balance = 0.0,
            cardId = "CARD-003"
        )
    )
}

private fun getSampleRoutes(): List<BusRoute> {
    return listOf(
        // Các tuyến xe buýt thật ở Hà Nội
        BusRoute("01", "Tuyến 01 - Yên Nghĩa → Bến xe Gia Lâm"),
        BusRoute("03", "Tuyến 03 - Bến xe Nước Ngầm → Hoàng Quốc Việt"),
        BusRoute("07", "Tuyến 07 - Bến xe Yên Nghĩa → Cầu Giấy"),
        BusRoute("09", "Tuyến 09 - Kim Mã → Bến xe Mỹ Đình"),
        BusRoute("14", "Tuyến 14 - Bến xe Giáp Bát → Long Biên"),
        BusRoute("18", "Tuyến 18 - Bến xe Yên Nghĩa → Bến xe Long Biên"),
        BusRoute("22", "Tuyến 22 - Sân Bay Nội Bài → Kim Mã"),
        BusRoute("32", "Tuyến 32 - Bến xe Yên Nghĩa → Bệnh viện E"),
        BusRoute("34", "Tuyến 34 - Đại học Công đoàn → Bưu điện Hà Đông"),
        BusRoute("40", "Tuyến 40 - Yên Nghĩa → Bến xe Gia Lâm"),
        BusRoute("86", "Tuyến 86 - Sân Bay Nội Bài → Hoàng Quốc Việt")
    )
}

private fun getSampleTrip(): Trip {
    return Trip(
        id = "TRIP-001",
        cardId = "CARD-001",
        tapOn = TapOn(
            cardId = "CARD-001",
            routeId = "01",
            routeName = "Tuyến 01 - Bến Thành",
            stationName = "Bến xe A",
            timestamp = LocalDateTime.now().minusMinutes(15)
        ),
        numberOfStops = 0,
        fare = 0.0,
        isCompleted = false
    )
}

/**
 * Right panel - Ticket Validation Info
 */
@Composable
private fun TicketValidationPanel(
    customer: Customer?,
    onEdit: (Customer) -> Unit = {},
    onDelete: (Customer) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .width(400.dp)
            .fillMaxHeight()
            .padding(20.dp),
        elevation = 4.dp,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "THÔNG TIN THẺ",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E88E5)
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (customer != null) {
                // Photo
                ImagePlaceholder(
                    photoPath = customer.photoPath,
                    photoBytes = customer.photoBytes,
                    size = Pair(120, 140)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Card ID
                Text(
                    text = customer.cardId,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF212121)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Customer Type Label
                Text(
                    text = when (customer.customerType) {
                        CustomerType.STUDENT -> "Thông thường"
                        CustomerType.ELDERLY -> "Người cao tuổi"
                        CustomerType.NORMAL -> "Thông thường"
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF757575)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Info Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Card Type
                    InfoRow(
                        label = "Loại vé",
                        value = when (customer.cardType) {
                            CardType.SINGLE_TRIP -> "Vé Lượt"
                            CardType.MONTHLY -> "Vé Tháng"
                        }
                    )
                    
                    // Expiry Date
                    InfoRow(
                        label = "Ngày hết hạn",
                        value = customer.expiryDate.toString().replace("-", "/")
                    )
                    
                    // Balance
                    InfoRow(
                        label = "Số dư",
                        value = "${String.format("%,.0f", customer.balance)} VND",
                        valueColor = Color(0xFF4CAF50)
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Status Badge
                val cardStatus = customer.getCardStatus()
                val statusText = when (cardStatus) {
                    models.CardStatus.VALID -> "Hợp lệ"
                    models.CardStatus.EXPIRED -> "Hết hạn"
                    models.CardStatus.INSUFFICIENT_BALANCE -> "Không đủ số dư"
                }
                StatusBadge(
                    text = statusText,
                    isValid = cardStatus == models.CardStatus.VALID,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action Buttons - Edit và Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Edit Button
                    Button(
                        onClick = { onEdit(customer) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF2196F3)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Sửa",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sửa",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Delete Button
                    Button(
                        onClick = { onDelete(customer) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFFF5252)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Xóa",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Xóa",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Empty state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFFE0E0E0)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Chọn khách hàng\nđể xem thông tin và\nsử dụng các chức năng",
                        fontSize = 14.sp,
                        color = Color(0xFF9E9E9E),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "💡 Các chức năng sẽ được kích hoạt\nsau khi chọn khách hàng",
                        fontSize = 12.sp,
                        color = Color(0xFFBDBDBD),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

/**
 * Info row component
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = Color(0xFF212121)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF757575)
        )
        
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}

/**
 * Edit Customer Dialog
 */
@Composable
private fun EditCustomerDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    var fullName by remember { mutableStateOf(customer.fullName) }
    var customerType by remember { mutableStateOf(customer.customerType) }
    var cardType by remember { mutableStateOf(customer.cardType) }
    var expiryDate by remember { mutableStateOf(customer.expiryDate) }
    var balance by remember { mutableStateOf(customer.balance.toInt().toString()) }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .width(600.dp)
                .padding(16.dp),
            elevation = 8.dp,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sửa thông tin khách hàng",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Card ID: ${customer.cardId}",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Form fields
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Họ tên *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Customer Type Dropdown
                var customerTypeExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = customerType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Loại đối tượng") },
                        trailingIcon = { 
                            IconButton(onClick = { customerTypeExpanded = !customerTypeExpanded }) {
                                Icon(
                                    imageVector = if (customerTypeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { customerTypeExpanded = !customerTypeExpanded }
                    )
                    DropdownMenu(
                        expanded = customerTypeExpanded,
                        onDismissRequest = { customerTypeExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CustomerType.values().forEach { type ->
                            DropdownMenuItem(
                                onClick = {
                                    customerType = type
                                    customerTypeExpanded = false
                                }
                            ) {
                                Text(type.displayName)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Card Type Dropdown
                var cardTypeExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = cardType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Loại thẻ") },
                        trailingIcon = { 
                            IconButton(onClick = { cardTypeExpanded = !cardTypeExpanded }) {
                                Icon(
                                    imageVector = if (cardTypeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cardTypeExpanded = !cardTypeExpanded }
                    )
                    DropdownMenu(
                        expanded = cardTypeExpanded,
                        onDismissRequest = { cardTypeExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CardType.values().forEach { type ->
                            DropdownMenuItem(
                                onClick = {
                                    cardType = type
                                    cardTypeExpanded = false
                                }
                            ) {
                                Text(type.displayName)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = balance,
                    onValueChange = { if (it.all { char -> char.isDigit() }) balance = it },
                    label = { Text("Số dư (VND) *") },
                    leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF9E9E9E)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Hủy", color = Color.White, fontSize = 15.sp)
                    }
                    
                    Button(
                        onClick = {
                            val updatedCustomer = customer.copy(
                                fullName = fullName,
                                customerType = customerType,
                                cardType = cardType,
                                expiryDate = expiryDate,
                                balance = balance.toDoubleOrNull() ?: customer.balance
                            )
                            onSave(updatedCustomer)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF4CAF50)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = fullName.isNotBlank() && balance.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lưu", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

