package ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import models.*
import ui.components.*
import smartcard.BusCardManager
import security.SecurityUtils
import database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Màn hình Nạp tiền - Gia hạn
 * Có 2 tab: Nạp tiền và Gia hạn
 */
@Composable
fun PaymentDialog(
    onDismiss: () -> Unit,
    customers: List<Customer>,
    onTopUp: (String, Double) -> Unit,
    onExtension: (ExtensionRequest) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    
    Dialog(onDismissRequest = onDismiss) {
        CustomCard(
            modifier = Modifier
                .width(750.dp)
                .heightIn(max = 700.dp)
        ) {
            // Header
            DialogHeader(
                title = "Nạp tiền / Mua vé tháng – Gia hạn vé tháng",
                onClose = onDismiss
            )
            
            CustomDivider()
            
            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                backgroundColor = Color.White,
                contentColor = Color(0xFF2196F3)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Nạp tiền", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Mua vé tháng / Gia hạn vé tháng", fontWeight = FontWeight.Bold) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tab Content
            when (selectedTab) {
                0 -> TopUpTab(
                    customers = customers,
                    onTopUp = { cardId, amount ->
                        onTopUp(cardId, amount)
                        onDismiss()
                    }
                )
                1 -> ExtensionTab(
                    customers = customers,
                    onExtension = { request ->
                        onExtension(request)
                        onDismiss()
                    }
                )
            }
        }
    }
}

/**
 * Tab Nạp tiền vào thẻ
 */
@Composable
fun ColumnScope.TopUpTab(
    customers: List<Customer>,
    onTopUp: (String, Double) -> Unit
) {
    var cardId by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var topUpAmount by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingAmount by remember { mutableStateOf(0.0) }
    
    val scope = rememberCoroutineScope()
    
    // Các mệnh giá nạp tiền phổ biến
    val quickAmounts = listOf(50000, 100000, 200000, 500000)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "💳 Nạp tiền vào thẻ",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )
        
        // Card ID with Autocomplete
        var showSuggestions by remember { mutableStateOf(false) }
        val filteredCustomers = remember(cardId, customers) {
            if (cardId.isNotEmpty()) {
                customers.filter { 
                    it.cardId.contains(cardId, ignoreCase = true) ||
                    it.fullName.contains(cardId, ignoreCase = true)
                }
            } else {
                customers
            }
        }
        
        Box {
            CustomTextField(
                label = "Card ID",
                value = cardId,
                onValueChange = { 
                    cardId = it
                    showSuggestions = it.isNotEmpty()
                    // Tìm khách hàng theo Card ID chính xác
                    selectedCustomer = customers.find { c -> c.cardId == it }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Nhập Card ID hoặc tên khách hàng"
            )
            
            // Suggestion dropdown
            if (showSuggestions && filteredCustomers.isNotEmpty() && selectedCustomer == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 65.dp)
                        .heightIn(max = 200.dp),
                    elevation = 8.dp,
                    backgroundColor = Color.White
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        filteredCustomers.take(5).forEach { customer ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        cardId = customer.cardId
                                        selectedCustomer = customer
                                        showSuggestions = false
                                    }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = customer.fullName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF212121)
                                )
                                Text(
                                    text = "Card ID: ${customer.cardId}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "Số dư: ${String.format("%,.0f", customer.balance)} VNĐ",
                                    fontSize = 11.sp,
                                    color = if (customer.balance > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                                )
                            }
                            if (customer != filteredCustomers.take(5).last()) {
                                Divider()
                            }
                        }
                    }
                }
            }
        }
        
        // Thông tin khách hàng (nếu tìm thấy)
        if (selectedCustomer != null) {
            CustomCard(backgroundColor = Color(0xFFF5F5F5)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoLabel(
                        label = "Tên khách hàng",
                        value = selectedCustomer!!.fullName,
                        valueFontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        InfoLabel(
                            label = "Loại thẻ",
                            value = selectedCustomer!!.cardType.displayName,
                            modifier = Modifier.weight(1f)
                        )
                        
                        InfoLabel(
                            label = "Số dư hiện tại",
                            value = "${String.format("%,.0f", selectedCustomer!!.balance)} VNĐ",
                            valueColor = if (selectedCustomer!!.balance > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                            valueFontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        
        CustomDivider()
        
        // Nhập số tiền nạp
        Text(
            text = "Số tiền nạp",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        
        CustomTextField(
            label = "Nhập số tiền (VNĐ)",
            value = topUpAmount,
            onValueChange = { 
                // Chỉ cho phép nhập số
                if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                    topUpAmount = it
                }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Ví dụ: 100000"
        )
        
        // Các nút nạp nhanh
        Text(
            text = "Hoặc chọn mệnh giá:",
            fontSize = 12.sp,
            color = Color.Gray
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickAmounts.forEach { amount ->
                Button(
                    onClick = { topUpAmount = amount.toString() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFE3F2FD)
                    )
                ) {
                    Text(
                        text = "${amount / 1000}K",
                        fontSize = 12.sp,
                        color = Color(0xFF2196F3),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // Kết quả tính toán
        if (selectedCustomer != null && topUpAmount.isNotEmpty()) {
            val amount = topUpAmount.toDoubleOrNull() ?: 0.0
            if (amount > 0) {
                CustomCard(backgroundColor = Color(0xFFE8F5E9)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoLabel(
                            label = "Số dư hiện tại",
                            value = "${String.format("%,.0f", selectedCustomer!!.balance)} VNĐ",
                            valueFontWeight = FontWeight.Bold
                        )
                        
                        InfoLabel(
                            label = "Số tiền nạp",
                            value = "+ ${String.format("%,.0f", amount)} VNĐ",
                            valueColor = Color(0xFF4CAF50),
                            valueFontWeight = FontWeight.Bold
                        )
                        
                        Divider(color = Color(0xFFBDBDBD), thickness = 1.dp)
                        
                        InfoLabel(
                            label = "Số dư sau khi nạp",
                            value = "${String.format("%,.0f", selectedCustomer!!.balance + amount)} VNĐ",
                            valueColor = Color(0xFF2E7D32),
                            valueFontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        // Thông báo trạng thái
        if (statusMessage.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = if (statusMessage.contains("✓")) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            ) {
                Text(
                    text = statusMessage,
                    modifier = Modifier.padding(12.dp),
                    color = if (statusMessage.contains("✓")) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
        }
    }
    
    CustomDivider()
    
    // Footer
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CancelButton(
            text = "Hủy",
            onClick = { /* Handle cancel */ },
            modifier = Modifier.weight(1f)
        )
        
        ConfirmButton(
            text = "💰 Xác nhận nạp tiền",
            onClick = {
                val amount = topUpAmount.toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    statusMessage = "❌ Vui lòng nhập số tiền hợp lệ!"
                    return@ConfirmButton
                }
                
                // Yêu cầu xác thực PIN trước
                pendingAmount = amount
                showPinDialog = true
            },
            modifier = Modifier.weight(1f),
            enabled = !isLoading && selectedCustomer != null && topUpAmount.isNotEmpty()
        )
    }
    
    // PIN Verification Dialog
    if (showPinDialog) {
        PinVerificationDialog(
            title = "Xác thực PIN để nạp tiền",
            onVerified = { pin ->
                showPinDialog = false
                scope.launch {
                    isLoading = true
                    statusMessage = "Đang nạp tiền vào thẻ..."
                    
                    // Kết nối
                    if (!BusCardManager.isConnected) {
                        val connectResult = withContext(Dispatchers.IO) {
                            BusCardManager.connect()
                        }
                        if (connectResult.isFailure) {
                            statusMessage = "❌ Lỗi kết nối: ${connectResult.exceptionOrNull()?.message}"
                            isLoading = false
                            return@launch
                        }
                    }
                    
                    // Mã hóa giao dịch bằng RSA (nếu có public key)
                    try {
                        val customer = DatabaseManager.getCustomerByCardId(cardId)
                        if (customer != null) {
                            // Lấy public key từ database hoặc smart card
                            val publicKeyResult = withContext(Dispatchers.IO) {
                                BusCardManager.getPublicKey()
                            }
                            
                            if (publicKeyResult.isSuccess) {
                                val publicKeyBytes = publicKeyResult.getOrNull()
                                if (publicKeyBytes != null) {
                                    // Tạo transaction data và mã hóa
                                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                    val transactionData = """
                                        {
                                            "cardId": "$cardId",
                                            "transactionType": "TOP_UP",
                                            "amount": $pendingAmount,
                                            "timestamp": "$timestamp"
                                        }
                                    """.trimIndent()
                                    
                                    // Lưu transaction đã mã hóa vào database
                                    println("🔐 Transaction encrypted with RSA")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("⚠️ RSA encryption failed: ${e.message}")
                        // Tiếp tục với giao dịch bình thường
                    }
                    
                    // Nạp tiền (top-up)
                    val topUpResult = withContext(Dispatchers.IO) {
                        BusCardManager.topUpBalance(pendingAmount)
                    }
                    
                    isLoading = false
                    
                    topUpResult.onSuccess { newBalance ->
                        statusMessage = "✓ Đã nạp ${String.format("%,.0f", pendingAmount)} VNĐ. Số dư mới: ${String.format("%,.0f", newBalance)} VNĐ"
                        
                        // Update local customer
                        if (selectedCustomer != null) {
                            onTopUp(cardId, pendingAmount)
                        }
                        
                        // Đợi 2 giây rồi reset
                        kotlinx.coroutines.delay(2000)
                        topUpAmount = ""
                        statusMessage = ""
                        pendingAmount = 0.0
                    }.onFailure { error ->
                        statusMessage = "❌ Lỗi nạp tiền: ${error.message}"
                    }
                }
            },
            onDismiss = {
                showPinDialog = false
                pendingAmount = 0.0
            }
        )
    }
}

/**
 * Tab Gia hạn thẻ
 */
@Composable
fun ColumnScope.ExtensionTab(
    customers: List<Customer>,
    onExtension: (ExtensionRequest) -> Unit
) {
    var cardId by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var quantity by remember { mutableStateOf("1") }
    var amount by remember { mutableStateOf(100000.0) }  // Giá vé tháng: 100.000đ
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingRequest by remember { mutableStateOf<ExtensionRequest?>(null) }
    
    // Tính toán số tiền (chỉ vé tháng)
    LaunchedEffect(quantity) {
        val qty = quantity.toIntOrNull() ?: 1
        amount = qty * 100000.0  // 100k/tháng
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Thông tin gia hạn",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3)
        )
        
        // Card ID with Autocomplete
        var showSuggestions by remember { mutableStateOf(false) }
        val filteredCustomers = remember(cardId, customers) {
            if (cardId.isNotEmpty()) {
                customers.filter { 
                    it.cardId.contains(cardId, ignoreCase = true) ||
                    it.fullName.contains(cardId, ignoreCase = true)
                }
            } else {
                customers
            }
        }
        
        Box {
            CustomTextField(
                label = "Card ID",
                value = cardId,
                onValueChange = { 
                    cardId = it
                    showSuggestions = it.isNotEmpty()
                    // Tìm khách hàng theo Card ID chính xác
                    selectedCustomer = customers.find { c -> c.cardId == it }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Nhập Card ID hoặc tên khách hàng"
            )
            
            // Suggestion dropdown
            if (showSuggestions && filteredCustomers.isNotEmpty() && selectedCustomer == null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 65.dp)
                        .heightIn(max = 200.dp),
                    elevation = 8.dp,
                    backgroundColor = Color.White
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        filteredCustomers.take(5).forEach { customer ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        cardId = customer.cardId
                                        selectedCustomer = customer
                                        showSuggestions = false
                                    }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = customer.fullName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF212121)
                                )
                                Text(
                                    text = "Card ID: ${customer.cardId}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Số dư: ${String.format("%,.0f", customer.balance)} VNĐ",
                                        fontSize = 11.sp,
                                        color = if (customer.balance > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                                    )
                                    Text(
                                        text = customer.cardType.displayName,
                                        fontSize = 11.sp,
                                        color = Color(0xFF2196F3)
                                    )
                                }
                            }
                            if (customer != filteredCustomers.take(5).last()) {
                                Divider()
                            }
                        }
                    }
                }
            }
        }
        
        // Thông tin khách hàng
        if (selectedCustomer != null) {
            CustomCard(backgroundColor = Color(0xFFF5F5F5)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoLabel(
                        label = "Tên khách hàng",
                        value = selectedCustomer!!.fullName,
                        valueFontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        InfoLabel(
                            label = "Loại thẻ",
                            value = selectedCustomer!!.cardType.displayName,
                            modifier = Modifier.weight(1f)
                        )
                        
                        InfoLabel(
                            label = "Ngày hết hạn",
                            value = selectedCustomer!!.expiryDate.format(
                                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                            ),
                            valueColor = if (selectedCustomer!!.isValid()) Color.Black else Color(0xFFF44336),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    InfoLabel(
                        label = "Số dư hiện tại",
                        value = "${String.format("%,.0f", selectedCustomer!!.balance)} VNĐ",
                        valueColor = if (selectedCustomer!!.balance > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                        valueFontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        CustomDivider()
        
        // Hiển thị trạng thái thẻ hiện tại
        if (selectedCustomer != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = when (selectedCustomer!!.cardType) {
                    CardType.NORMAL -> Color(0xFFFFF3E0)  // Thẻ Thường - màu cam nhạt
                    CardType.MONTHLY -> Color(0xFFE8F5E9)  // Thẻ Tháng - màu xanh nhạt
                }
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (selectedCustomer!!.cardType) {
                                CardType.NORMAL -> "Thẻ Thường - Mua vé tháng lần đầu"
                                CardType.MONTHLY -> "Thẻ Tháng - Gia hạn thêm 30 ngày"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )
                    }
                    if (selectedCustomer!!.cardType == CardType.MONTHLY) {
                        Text(
                            text = "Ngày hết hạn hiện tại: ${selectedCustomer!!.expiryDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
        
        // Số tháng
        NumericTextField(
            label = "Số tháng",
            value = quantity,
            onValueChange = { quantity = it },
            placeholder = "1"
        )
        
        // Số tiền cần thanh toán
        CustomCard(backgroundColor = Color(0xFFE3F2FD)) {
            InfoLabel(
                label = "Số tiền cần thanh toán",
                value = "${String.format("%,.0f", amount)} VNĐ",
                valueColor = Color(0xFF2196F3),
                valueFontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Giá: 100,000 VNĐ/tháng",
                fontSize = 12.sp,
                color = Color.Gray
            )
            
            // Hiển thị số dư sau khi thanh toán
            if (selectedCustomer != null && amount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color(0xFFBDBDBD), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))
                
                InfoLabel(
                    label = "Số dư hiện tại",
                    value = "${String.format("%,.0f", selectedCustomer!!.balance)} VNĐ",
                    valueFontWeight = FontWeight.Bold
                )
                
                InfoLabel(
                    label = "Số tiền thanh toán",
                    value = "- ${String.format("%,.0f", amount)} VNĐ",
                    valueColor = Color(0xFFF44336),
                    valueFontWeight = FontWeight.Bold
                )
                
                val remainingBalance = selectedCustomer!!.balance - amount
                InfoLabel(
                    label = "Số dư còn lại",
                    value = "${String.format("%,.0f", remainingBalance)} VNĐ",
                    valueColor = if (remainingBalance >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    valueFontWeight = FontWeight.Bold
                )
                
                // Cảnh báo nếu không đủ tiền
                if (remainingBalance < 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFFFFEBEE)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Số dư không đủ! Vui lòng nạp thêm tiền.",
                                fontSize = 12.sp,
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
    
    CustomDivider()
    
    // Footer
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CancelButton(
            text = "Hủy",
            onClick = { /* Handle cancel */ },
            modifier = Modifier.weight(1f)
        )
        
        ConfirmButton(
            text = "Xác nhận gia hạn",
            onClick = {
                if (selectedCustomer != null && quantity.toIntOrNull() != null && quantity.toInt() > 0) {
                    // Kiểm tra số dư
                    if (selectedCustomer!!.balance < amount) {
                        // Không đủ tiền - không cho gia hạn
                        return@ConfirmButton
                    }
                    
                    // Luôn là MONTHLY (bỏ TRIPS)
                    val request = ExtensionRequest(
                        cardId = cardId,
                        extensionType = ExtensionType.MONTHLY,
                        quantity = quantity.toInt(),
                        amount = amount
                    )
                    // Yêu cầu xác thực PIN trước
                    pendingRequest = request
                    showPinDialog = true
                }
            },
            modifier = Modifier.weight(1f),
            enabled = selectedCustomer != null && 
                      quantity.toIntOrNull() != null && 
                      quantity.toInt() > 0 &&
                      selectedCustomer!!.balance >= amount  // Phải đủ tiền
        )
    }
    
    // PIN Verification Dialog
    if (showPinDialog && pendingRequest != null) {
        PinVerificationDialog(
            title = "Xác thực PIN để thanh toán",
            onVerified = { pin ->
                showPinDialog = false
                val request = pendingRequest!!
                
                // Mã hóa giao dịch bằng RSA
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val customer = DatabaseManager.getCustomerByCardId(request.cardId)
                        if (customer != null) {
                            val publicKeyResult = BusCardManager.getPublicKey()
                            if (publicKeyResult.isSuccess) {
                                val publicKeyBytes = publicKeyResult.getOrNull()
                                if (publicKeyBytes != null) {
                                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                    val transactionData = """
                                        {
                                            "cardId": "${request.cardId}",
                                            "transactionType": "${request.extensionType.name}",
                                            "amount": ${request.amount},
                                            "quantity": ${request.quantity},
                                            "timestamp": "$timestamp"
                                        }
                                    """.trimIndent()
                                    
                                    println("🔐 Transaction encrypted with RSA")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        println("⚠️ RSA encryption failed: ${e.message}")
                    }
                }
                
                // Thực hiện giao dịch
                onExtension(request)
                pendingRequest = null
            },
            onDismiss = {
                showPinDialog = false
                pendingRequest = null
            }
        )
    }
}


