package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
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
 * Màn hình Thanh toán - Tính cước - Gia hạn
 * Có 2 tab: Tính cước và Gia hạn
 */
@Composable
fun PaymentDialog(
    onDismiss: () -> Unit,
    customers: List<Customer>,
    onDeduction: (String, Double) -> Unit,
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
                title = "Thanh toán - Tính cước - Gia hạn",
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
                    text = { Text("Tính cước", fontWeight = FontWeight.Bold) }
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
                0 -> FareCalculationTab(
                    customers = customers,
                    onDeduction = { cardId, amount ->
                        onDeduction(cardId, amount)
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
 * Tab Tính cước chuyến đi
 */
@Composable
fun ColumnScope.FareCalculationTab(
    customers: List<Customer>,
    onDeduction: (String, Double) -> Unit
) {
    var cardId by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var tapOnRoute by remember { mutableStateOf("Tuyến 01 - Bến xe A") }
    var tapOnTime by remember { mutableStateOf(LocalDateTime.now()) }
    var tapOffRoute by remember { mutableStateOf("Tuyến 01 - Bến xe C") }
    var tapOffTime by remember { mutableStateOf(LocalDateTime.now()) }
    var numberOfStops by remember { mutableStateOf(5) }
    var fareAmount by remember { mutableStateOf(20000.0) }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var currentBalance by remember { mutableStateOf(0.0) }
    
    val scope = rememberCoroutineScope()
    
    // Danh sách tuyến mẫu
    val routes = remember {
        listOf(
            "Tuyến 01 - Bến xe A",
            "Tuyến 01 - Trạm B1",
            "Tuyến 01 - Trạm B2",
            "Tuyến 01 - Bến xe C",
            "Tuyến 02 - Bến xe D",
            "Tuyến 02 - Trạm E1",
            "Tuyến 02 - Bến xe F"
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Thông tin chuyến đi",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3)
        )
        
        // Card ID
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CustomTextField(
                label = "Card ID",
                value = cardId,
                onValueChange = { 
                    cardId = it
                    // Tìm khách hàng theo Card ID
                    selectedCustomer = customers.find { c -> c.cardId == it }
                },
                modifier = Modifier.weight(1f),
                placeholder = "Nhập hoặc quẹt thẻ"
            )
            
            CustomButton(
                text = "Đọc thẻ",
                onClick = {
                    scope.launch {
                        isLoading = true
                        statusMessage = "Đang đọc từ thẻ..."
                        
                        // Kết nối
                        if (!BusCardManager.isConnected) {
                            val connectResult = withContext(Dispatchers.IO) {
                                BusCardManager.connect()
                            }
                            if (connectResult.isFailure) {
                                statusMessage = "Lỗi: ${connectResult.exceptionOrNull()?.message}"
                                isLoading = false
                                return@launch
                            }
                        }
                        
                        // Đọc Card ID
                        val cardIdResult = withContext(Dispatchers.IO) {
                            BusCardManager.getCardId()
                        }
                        
                        // Đọc số dư
                        val balanceResult = withContext(Dispatchers.IO) {
                            BusCardManager.getBalance()
                        }
                        
                        isLoading = false
                        
                        cardIdResult.onSuccess { id ->
                            cardId = id
                            // Tìm trong danh sách local
                            selectedCustomer = customers.find { it.cardId == id }
                        }
                        
                        balanceResult.onSuccess { bal ->
                            currentBalance = bal
                            statusMessage = "✓ Đã đọc thẻ. Số dư: ${String.format("%,.0f", bal)} VNĐ"
                        }
                    }
                },
                modifier = Modifier.width(120.dp),
                enabled = !isLoading
            )
        }
        
        // Status message
        if (statusMessage.isNotEmpty()) {
            Text(
                text = statusMessage,
                fontSize = 12.sp,
                color = if (statusMessage.startsWith("✓")) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.padding(vertical = 4.dp)
            )
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
        
        // Thông tin quẹt lên
        Text(
            text = "Thông tin quẹt lên",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        
        CustomDropdown(
            label = "Tuyến/Vị trí quẹt lên",
            items = routes,
            selectedItem = tapOnRoute,
            onItemSelected = { tapOnRoute = it }
        )
        
        InfoLabel(
            label = "Thời gian quẹt lên",
            value = tapOnTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
        )
        
        CustomDivider()
        
        // Thông tin quẹt xuống
        Text(
            text = "Thông tin quẹt xuống",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        
        CustomDropdown(
            label = "Tuyến/Vị trí quẹt xuống",
            items = routes,
            selectedItem = tapOffRoute,
            onItemSelected = { 
                tapOffRoute = it
                // Tính toán số chặng và cước phí
                numberOfStops = calculateStops(tapOnRoute, tapOffRoute)
                fareAmount = calculateFare(numberOfStops)
            }
        )
        
        InfoLabel(
            label = "Thời gian quẹt xuống",
            value = tapOffTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
        )
        
        // Kết quả tính toán
        CustomCard(backgroundColor = Color(0xFFFFF3E0)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    InfoLabel(
                        label = "Số chặng",
                        value = "$numberOfStops chặng",
                        valueFontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    InfoLabel(
                        label = "Số tiền phải trừ",
                        value = "${String.format("%,.0f", fareAmount)} VNĐ",
                        valueColor = Color(0xFFF44336),
                        valueFontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                if (selectedCustomer != null) {
                    InfoLabel(
                        label = "Số dư sau khi trừ",
                        value = "${String.format("%,.0f", selectedCustomer!!.balance - fareAmount)} VNĐ",
                        valueColor = if (selectedCustomer!!.balance - fareAmount >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                        valueFontWeight = FontWeight.Bold
                    )
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
            text = "Xác nhận trừ tiền từ thẻ",
            onClick = {
                scope.launch {
                    isLoading = true
                    statusMessage = "Đang trừ tiền từ thẻ..."
                    
                    // Kết nối
                    if (!BusCardManager.isConnected) {
                        val connectResult = withContext(Dispatchers.IO) {
                            BusCardManager.connect()
                        }
                        if (connectResult.isFailure) {
                            statusMessage = "Lỗi kết nối: ${connectResult.exceptionOrNull()?.message}"
                            isLoading = false
                            return@launch
                        }
                    }
                    
                    // Trừ tiền
                    val deductResult = withContext(Dispatchers.IO) {
                        BusCardManager.deductBalance(fareAmount)
                    }
                    
                    isLoading = false
                    
                    deductResult.onSuccess { newBalance ->
                        currentBalance = newBalance
                        statusMessage = "✓ Đã trừ ${String.format("%,.0f", fareAmount)} VNĐ. Số dư mới: ${String.format("%,.0f", newBalance)} VNĐ"
                        
                        // Update local customer
                        if (selectedCustomer != null) {
                            onDeduction(cardId, fareAmount)
                        }
                        
                        // Đợi 2 giây rồi reset
                        kotlinx.coroutines.delay(2000)
                        statusMessage = ""
                    }.onFailure { error ->
                        statusMessage = "Lỗi trừ tiền: ${error.message}"
                    }
                }
            },
            modifier = Modifier.weight(1f),
            enabled = !isLoading && cardId.isNotEmpty() && currentBalance >= fareAmount
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
            ExtensionType.MONTHLY -> qty * 200000.0  // 200k/tháng
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
        
        // Card ID
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CustomTextField(
                label = "Card ID",
                value = cardId,
                onValueChange = { 
                    cardId = it
                    selectedCustomer = customers.find { c -> c.cardId == it }
                },
                modifier = Modifier.weight(1f),
                placeholder = "Nhập hoặc quẹt thẻ"
            )
            
            CustomButton(
                text = "Tìm",
                onClick = {
                    selectedCustomer = customers.find { it.cardId == cardId }
                },
                modifier = Modifier.width(100.dp)
            )
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
                    ExtensionType.MONTHLY -> "Giá: 200,000 VNĐ/tháng"
                    ExtensionType.TRIPS -> "Giá: 7,000 VNĐ/lượt"
                },
                fontSize = 12.sp,
                color = Color.Gray
            )
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
            enabled = selectedCustomer != null && quantity.toIntOrNull() != null && quantity.toInt() > 0
        )
    }
}

/**
 * Tính số chặng giữa 2 vị trí (simplified)
 */
private fun calculateStops(from: String, to: String): Int {
    // Logic đơn giản: mỗi trạm = 1 chặng
    // Trong thực tế sẽ phức tạp hơn
    return kotlin.math.abs(from.hashCode() % 10 - to.hashCode() % 10).coerceAtLeast(1)
}

/**
 * Tính cước phí dựa trên số chặng
 */
private fun calculateFare(stops: Int): Double {
    val basePrice = 5000.0
    val pricePerStop = 3000.0
    return basePrice + (stops * pricePerStop)
}

