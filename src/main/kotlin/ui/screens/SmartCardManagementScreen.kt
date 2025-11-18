package ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ui.components.ImagePlaceholder
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

/**
 * SmartCardManagementScreen - Màn hình quản lý thẻ ảo (đọc từ database)
 */
@Composable
fun SmartCardManagementDialog(
    onDismiss: () -> Unit
) {
    // Load danh sách customers từ database
    var customers by remember { mutableStateOf(emptyList<models.Customer>()) }
    var selectedCustomer by remember { mutableStateOf<models.Customer?>(null) }
    var transactions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    
    // Load customers khi khởi động
    LaunchedEffect(Unit) {
        customers = database.DatabaseManager.getAllCustomers()
    }
    
    // Load transactions khi chọn customer
    LaunchedEffect(selectedCustomer) {
        selectedCustomer?.let { customer ->
            transactions = database.DatabaseManager.getTransactionsByCardId(customer.cardId)
        }
    }
    
    // Tab state
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Đọc thẻ", "Thông tin", "Giao dịch")
    
    // State cho đọc thẻ thật
    var isConnected by remember { mutableStateOf(false) }
    var cardReaderStatus by remember { mutableStateOf("Chưa kết nối") }
    var isReadingCard by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // Check connection status khi dialog mở
    LaunchedEffect(Unit) {
        isConnected = smartcard.BusCardManager.isConnected
        if (isConnected) {
            cardReaderStatus = "✓ Đã kết nối với card reader"
        }
        println("🔍 Initial connection check: isConnected = $isConnected")
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .width(700.dp)
                .heightIn(min = 500.dp, max = 650.dp),
            elevation = 8.dp,
            shape = RoundedCornerShape(12.dp)
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Title section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp)
        ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Quản lý Smart Card",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Đóng")
                    }
                }
                
                // Status indicator
                selectedCustomer?.let { customer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp)
                            .background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "✓ Đã chọn thẻ: ${customer.cardId}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
                
                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    backgroundColor = Color.Transparent
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 14.sp) }
                        )
                    }
                }
            }
        
        // Content section with tabs
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
                when (selectedTab) {
                    0 -> ReadSimulatorCardTab(
                        isConnected = isConnected,
                        cardReaderStatus = cardReaderStatus,
                        isReadingCard = isReadingCard,
                        onConnect = {
                            scope.launch {
                                isReadingCard = true
                                cardReaderStatus = "Đang kết nối..."
                                val result = withContext(Dispatchers.IO) {
                                    smartcard.BusCardManager.connect()
                                }
                                isReadingCard = false
                                result.onSuccess {
                                    isConnected = true  // Update state
                                    cardReaderStatus = "✓ Đã kết nối với card reader"
                                    println("✅ Connected successfully, isConnected = $isConnected")
                                }.onFailure { error ->
                                    isConnected = false
                                    cardReaderStatus = "✗ ${error.message}"
                                    println("❌ Connection failed: ${error.message}")
                                }
                            }
                        },
                        onDisconnect = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    smartcard.BusCardManager.disconnect()
                                }
                                isConnected = false
                                cardReaderStatus = "Đã ngắt kết nối"
                                selectedCustomer = null  // Clear selected customer
                                println("🔌 Disconnected, isConnected = $isConnected")
                            }
                        },
                        onReadCard = {
                            scope.launch {
                                isReadingCard = true
                                val infoResult = withContext(Dispatchers.IO) {
                                    smartcard.BusCardManager.getCustomerInfo()
                                }
                                val cardIdResult = withContext(Dispatchers.IO) {
                                    smartcard.BusCardManager.getCardId()
                                }
                                isReadingCard = false
                                
                                infoResult.onSuccess { info ->
                                    cardIdResult.onSuccess { cardId ->
                                        // Reload danh sách customers từ database (để có dữ liệu mới nhất)
                                        customers = withContext(Dispatchers.IO) {
                                            database.DatabaseManager.getAllCustomers()
                                        }
                                        
                                        // Tìm customer trong DB
                                        val customer = customers.find { it.cardId == cardId }
                                        if (customer != null) {
                                            selectedCustomer = customer
                                            cardReaderStatus = "✓ Đọc thẻ thành công: $cardId - ${customer.fullName}"
                                            selectedTab = 1 // Chuyển sang tab Thông tin
                                        } else {
                                            cardReaderStatus = "⚠ Thẻ $cardId chưa có trong database. Hãy nạp thẻ trước!"
                                        }
                                    }
                                }.onFailure { error ->
                                    cardReaderStatus = "✗ ${error.message}"
                                }
                            }
                        }
                    )
                    
                    1 -> InformationTab(
                        customer = selectedCustomer
                    )
                    
                    2 -> TransactionTab(
                        transactions = transactions,
                        selectedCustomer = selectedCustomer
                    )
                }
        }
        
        // Bottom button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = Color(0xFFF44336), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        }
        }
    }
}

/**
 * Tab đọc thẻ từ simulator (JCardSimServer)
 */
@Composable
private fun ReadSimulatorCardTab(
    isConnected: Boolean,
    cardReaderStatus: String,
    isReadingCard: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReadCard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = if (isConnected) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
            elevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    modifier = Modifier.size(28.dp)
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Simulator Card Reader",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = cardReaderStatus,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // Buttons
        if (!isConnected) {
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3)),
                enabled = !isReadingCard
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Kết nối Simulator", color = Color.White, fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = onReadCard,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)),
                enabled = !isReadingCard
            ) {
                if (isReadingCard) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Đang đọc...", color = Color.White, fontSize = 13.sp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Đọc thông tin từ Simulator", color = Color.White, fontSize = 13.sp)
                }
            }
            
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFF44336)),
                enabled = !isReadingCard
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Ngắt kết nối", color = Color.White, fontSize = 13.sp)
            }
        }
        
        Divider()
        
        // Hướng dẫn
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFFE3F2FD),
            elevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Hướng dẫn sử dụng",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3)
                    )
                }
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    "1. Chạy JCardSimServer (cổng 9025)",
                    fontSize = 11.sp,
                    color = Color(0xFF424242)
                )
                Text(
                    "2. Nhấn 'Kết nối Simulator'",
                    fontSize = 11.sp,
                    color = Color(0xFF424242)
                )
                Text(
                    "3. Nhấn 'Đọc thông tin từ Simulator'",
                    fontSize = 11.sp,
                    color = Color(0xFF424242)
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    "💡 Lưu ý:",
                    fontSize = 11.sp,
                    color = Color(0xFF757575),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "• Thẻ phải được nạp vào database trước",
                    fontSize = 10.sp,
                    color = Color(0xFF757575)
                )
                Text(
                    "• Dùng 'Nạp thông tin vào thẻ' để thêm mới",
                    fontSize = 10.sp,
                    color = Color(0xFF757575)
                )
            }
        }
    }
}

/**
 * Tab danh sách thẻ
 */
@Composable
private fun CardListTab(
    customers: List<models.Customer>,
    selectedCustomer: models.Customer?,
    onSelectCustomer: (models.Customer) -> Unit
) {
    if (customers.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.Gray
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Chưa có thẻ nào",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(customers) { customer ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectCustomer(customer) },
                    elevation = if (selectedCustomer?.cardId == customer.cardId) 6.dp else 2.dp,
                    backgroundColor = if (selectedCustomer?.cardId == customer.cardId) 
                        Color(0xFFE3F2FD) else Color.White,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ImagePlaceholder(
                            photoPath = customer.photoPath,
                            photoBytes = customer.photoBytes,
                            size = Pair(50, 60)
                        )
                        
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
                            Text(
                                text = "${String.format("%,.0f", customer.balance)} VNĐ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (customer.balance > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                        
                        if (selectedCustomer?.cardId == customer.cardId) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tab thông tin thẻ
 */
@Composable
private fun InformationTab(
    customer: models.Customer?
) {
    if (customer == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF2196F3).copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Chưa có thẻ nào được chọn",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Vui lòng vào tab 'Đọc thẻ' để đọc thông tin từ simulator",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Ảnh và tên
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ImagePlaceholder(
                        photoPath = customer.photoPath,
                        photoBytes = customer.photoBytes,
                        size = Pair(100, 120)
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = customer.fullName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = customer.customerType.displayName,
                            fontSize = 14.sp,
                            color = Color(0xFF2196F3)
                        )
                    }
                }
            }
            
            // Chi tiết thẻ
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoRow("Card ID", customer.cardId)
                    Divider()
                    InfoRow("Loại thẻ", customer.cardType.displayName)
                    InfoRow("Loại đối tượng", customer.customerType.displayName)
                    InfoRow(
                        "Ngày hết hạn", 
                        customer.expiryDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    )
                    if (customer.linkedCustomerCode.isNotEmpty()) {
                        InfoRow("Mã liên kết", customer.linkedCustomerCode)
                    }
                    Divider()
                    InfoRow(
                        "Số dư", 
                        "${String.format("%,.0f", customer.balance)} VNĐ",
                        valueColor = if (customer.balance > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                    InfoRow(
                        "Trạng thái", 
                        if (customer.isValid()) "Hợp lệ" else "Không hợp lệ",
                        valueColor = if (customer.isValid()) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            }
        }
    }
}

/**
 * Tab giao dịch
 */
@Composable
private fun TransactionTab(
    transactions: List<Map<String, Any>>,
    selectedCustomer: models.Customer?
) {
    if (selectedCustomer == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    Icons.Default.List,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFF2196F3).copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Chưa có thẻ nào được chọn",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Vui lòng vào tab 'Đọc thẻ' để đọc thông tin từ simulator",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else if (transactions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    Icons.Default.List,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = Color.Gray.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Chưa có giao dịch nào",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.DarkGray
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Thẻ ${selectedCustomer.cardId} chưa có lịch sử giao dịch",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(transactions.size) { index ->
                val transaction = transactions[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = transaction["transaction_type"] as? String ?: "N/A",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = transaction["description"] as? String ?: "",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = transaction["transaction_date"] as? String ?: "",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                        
                        val amount = transaction["amount"] as? Double ?: 0.0
                        Text(
                            text = "${if (amount > 0) "+" else ""}${String.format("%,.0f", amount)} VNĐ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (amount > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Component hiển thị thông tin dạng key-value
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.Black
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
