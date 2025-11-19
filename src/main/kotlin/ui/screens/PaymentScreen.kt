package ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
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
                title = "Nạp tiền - Gia hạn thẻ",
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
                    text = { Text("Gia hạn", fontWeight = FontWeight.Bold) }
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
                    
                    // Nạp tiền (top-up)
                    val topUpResult = withContext(Dispatchers.IO) {
                        BusCardManager.topUpBalance(amount)
                    }
                    
                    isLoading = false
                    
                    topUpResult.onSuccess { newBalance ->
                        statusMessage = "✓ Đã nạp ${String.format("%,.0f", amount)} VNĐ. Số dư mới: ${String.format("%,.0f", newBalance)} VNĐ"
                        
                        // Update local customer
                        if (selectedCustomer != null) {
                            onTopUp(cardId, amount)
                        }
                        
                        // Đợi 2 giây rồi reset
                        kotlinx.coroutines.delay(2000)
                        topUpAmount = ""
                        statusMessage = ""
                    }.onFailure { error ->
                        statusMessage = "❌ Lỗi nạp tiền: ${error.message}"
                    }
                }
            },
            modifier = Modifier.weight(1f),
            enabled = !isLoading && selectedCustomer != null && topUpAmount.isNotEmpty()
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
    var extensionType by remember { mutableStateOf(ExtensionType.MONTHLY) }
    var quantity by remember { mutableStateOf("1") }
    var amount by remember { mutableStateOf(0.0) }
    
    // Tính toán số tiền
    LaunchedEffect(extensionType, quantity) {
        val qty = quantity.toIntOrNull() ?: 0
        amount = when (extensionType) {
            ExtensionType.MONTHLY -> qty * 100000.0  // 100k/tháng (vé tháng Hà Nội)
            ExtensionType.TRIPS -> qty * 7000.0      // 7k/lượt
        }
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
        
        // Loại gia hạn
        CustomDropdown(
            label = "Loại gia hạn",
            items = ExtensionType.values().toList(),
            selectedItem = extensionType,
            onItemSelected = { extensionType = it },
            itemLabel = { it.displayName }
        )
        
        // Số tháng / số lượt
        NumericTextField(
            label = when (extensionType) {
                ExtensionType.MONTHLY -> "Số tháng"
                ExtensionType.TRIPS -> "Số lượt"
            },
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
                text = when (extensionType) {
                    ExtensionType.MONTHLY -> "Giá: 100,000 VNĐ/tháng"
                    ExtensionType.TRIPS -> "Giá: 7,000 VNĐ/lượt"
                },
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
                    
                    val request = ExtensionRequest(
                        cardId = cardId,
                        extensionType = extensionType,
                        quantity = quantity.toInt(),
                        amount = amount
                    )
                    onExtension(request)
                }
            },
            modifier = Modifier.weight(1f),
            enabled = selectedCustomer != null && 
                      quantity.toIntOrNull() != null && 
                      quantity.toInt() > 0 &&
                      selectedCustomer!!.balance >= amount  // Phải đủ tiền
        )
    }
}


