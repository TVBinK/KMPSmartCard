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
    val tabs = listOf("Thông tin", "Giao dịch")
    
    // State cho đọc thẻ thật
    var isConnected by remember { mutableStateOf(false) }
    var cardReaderStatus by remember { mutableStateOf("Chưa kết nối") }
    var isReadingCard by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    // Check connection status và tự động đọc thẻ khi dialog mở
    LaunchedEffect(Unit) {
        isConnected = smartcard.BusCardManager.isConnected
        if (isConnected) {
            cardReaderStatus = "✓ Đã kết nối với card reader"
            // Tự động đọc thẻ khi đã kết nối
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
                        // Reload danh sách customers từ database
                        customers = withContext(Dispatchers.IO) {
                            database.DatabaseManager.getAllCustomers()
                        }
                        
                        // Tìm customer trong DB
                        val customer = customers.find { it.cardId == cardId }
                        if (customer != null) {
                            selectedCustomer = customer
                            println("Tu dong doc the thanh cong: $cardId - ${customer.fullName}")
                        } else {
                            println("⚠ Thẻ $cardId chưa có trong database")
                        }
                    }
                }.onFailure { error ->
                    println("Loi tu dong doc the: ${error.message}")
                }
            }
        }
        println("Kiem tra ket noi ban dau: isConnected = $isConnected")
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
                            text = "Đã đọc thẻ: ${customer.cardId}",
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
                    0 -> InformationTab(
                        customer = selectedCustomer,
                        isConnected = isConnected,
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
                                        // Reload danh sách customers từ database
                                        customers = withContext(Dispatchers.IO) {
                                            database.DatabaseManager.getAllCustomers()
                                        }
                                        
                                        // Tìm customer trong DB
                                        val customer = customers.find { it.cardId == cardId }
                                        if (customer != null) {
                                            selectedCustomer = customer
                                            println("Doc the thanh cong: $cardId - ${customer.fullName}")
                                        } else {
                                            println("The $cardId chua co trong database")
                                        }
                                    }
                                }.onFailure { error ->
                                    println("Loi doc the: ${error.message}")
                                }
                            }
                        }
                    )
                    
                    1 -> TransactionTab(
                        transactions = transactions,
                        selectedCustomer = selectedCustomer
                    )
                }
        }
        
        // Bottom buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Nút thay đổi PIN (chỉ hiển thị khi đã kết nối)
            if (isConnected) {
                Button(
                    onClick = { showChangePinDialog = true },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3))
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Thay đổi PIN", color = Color.White, fontSize = 14.sp)
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = Color(0xFFF44336), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        }
        }
        
        // Change PIN Dialog
        if (showChangePinDialog) {
            ChangePinDialog(
                onDismiss = { showChangePinDialog = false },
                onSuccess = {
                    println("PIN da doi thanh cong")
                }
            )
        }
    }
}

/**
 * Tab thông tin thẻ
 */
@Composable
private fun InformationTab(
    customer: models.Customer?,
    isConnected: Boolean,
    onReadCard: () -> Unit
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
                    "Chưa đọc thẻ nào",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nhấn nút bên dưới để đọc thông tin từ thẻ đã nạp",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(Modifier.height(24.dp))
                
                Button(
                    onClick = onReadCard,
                    enabled = isConnected,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)),
                    modifier = Modifier.width(200.dp).height(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Đọc thẻ", color = Color.White, fontSize = 14.sp)
                }
                
                if (!isConnected) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "⚠️ Vui lòng nạp thông tin vào thẻ trước",
                        fontSize = 12.sp,
                        color = Color(0xFFFF9800),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
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
                    
                    // Hiển thị các trường mới nếu có
                    if (customer.cccd.isNotEmpty()) {
                        InfoRow("CCCD", customer.cccd)
                    }
                    if (customer.dob.isNotEmpty()) {
                        InfoRow("Ngày sinh", customer.dob)
                    }
                    if (customer.address.isNotEmpty()) {
                        InfoRow("Địa chỉ", customer.address)
                    }
                    if (customer.phone.isNotEmpty()) {
                        InfoRow("Số điện thoại", customer.phone)
                    }
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
                        "Loại thẻ", 
                        customer.cardType.displayName,
                        valueColor = if (customer.cardType == models.CardType.MONTHLY) Color(0xFF4CAF50) else Color(0xFF2196F3)
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
                    "Vui lòng đọc thẻ trong tab 'Thông tin' để xem giao dịch",
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
                        val transactionType = transaction["transaction_type"] as? String ?: ""
                        
                        // Xác định loại giao dịch (cộng/trừ tiền)
                        val isDeduction = transactionType in listOf("EXTEND_MONTHLY", "MONTHLY_PURCHASE")
                        val displayAmount = if (isDeduction && amount > 0) -amount else amount
                        
                        Text(
                            text = "${if (displayAmount > 0) "+" else ""}${String.format("%,.0f", displayAmount)} VNĐ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (displayAmount > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
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
