package feature.readcard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import core.model.CardType
import core.model.Customer
import feature.changepin.ChangePinDialog
import kotlinx.coroutines.launch
import core.ui.components.ImagePlaceholder
import core.ui.components.InfoRow
import java.time.format.DateTimeFormatter

/**
 * ReadSmartCardScreen - Màn hình đọc thông tin thẻ
 * Sử dụng MVVM pattern
 */
@Composable
fun ReadSmartCardDialog(
    onDismiss: () -> Unit
) {
    // Khởi tạo ViewModel
    val readSmartCardViewModel = remember { ReadSmartCardViewModel() }
    val state by readSmartCardViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Auto đọc thẻ mỗi lần dialog mở
    LaunchedEffect(Unit) {
        readSmartCardViewModel.onDialogOpened()
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            readSmartCardViewModel.onCleared()
        }
    }
    
    val tabs = listOf("Thông tin", "Giao dịch")
    
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
                            "Đọc Thẻ",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Đóng")
                        }
                    }
                    
                    // Status indicator
                    state.selectedCustomer?.let { customer ->
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
                        selectedTabIndex = state.selectedTab,
                        backgroundColor = Color.Transparent
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = state.selectedTab == index,
                                onClick = { readSmartCardViewModel.selectTab(index) },
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
                    when (state.selectedTab) {
                        0 -> InformationTab(
                            customer = state.selectedCustomer,
                            isConnected = state.isConnected,
                            isReadingCard = state.isReadingCard,
                            isCardBlocked = state.isCardBlocked,
                            isUnlockingCard = state.isUnlockingCard,
                            actionMessage = state.actionMessage,
                            onReadCard = {
                                coroutineScope.launch {
                                    readSmartCardViewModel.readCard()
                                }
                            },
                            onUnlockCard = { readSmartCardViewModel.unlockCard() }
                        )
                        
                        1 -> TransactionTab(
                            transactions = state.transactions,
                            selectedCustomer = state.selectedCustomer
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
                    if (state.isConnected) {
                        Button(
                            onClick = { readSmartCardViewModel.showChangePinDialog() },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3)),
                            enabled = !state.isCardBlocked
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
        if (state.showChangePinDialog) {
            ChangePinDialog(
                onDismiss = { readSmartCardViewModel.dismissChangePinDialog() },
                onSuccess = {
                    println("PIN da doi thanh cong")
                    readSmartCardViewModel.dismissChangePinDialog()
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
    customer: Customer?,
    isConnected: Boolean,
    isReadingCard: Boolean,
    isCardBlocked: Boolean,
    isUnlockingCard: Boolean,
    actionMessage: String,
    onReadCard: () -> Unit,
    onUnlockCard: () -> Unit
) {
    if (isCardBlocked) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFFFFEBEE),
                    elevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFD32F2F),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Thẻ đang bị khóa",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD32F2F)
                            )
                            Text(
                                text = "Nhập sai PIN quá số lần cho phép. Vui lòng mở khóa thẻ để thao tác tiếp.",
                                fontSize = 12.sp,
                                color = Color(0xFFB71C1C)
                            )
                        }
                    }
                }
                
                Button(
                    onClick = onUnlockCard,
                    enabled = !isUnlockingCard,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F)),
                    modifier = Modifier
                        .width(220.dp)
                        .height(48.dp)
                ) {
                    if (isUnlockingCard) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Đang mở khóa...", color = Color.White, fontSize = 14.sp)
                    } else {
                        Icon(imageVector = Icons.Filled.Lock, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Mở khóa thẻ", color = Color.White, fontSize = 14.sp)
                    }
                }
                
                if (actionMessage.isNotEmpty()) {
                    Text(
                        text = actionMessage,
                        fontSize = 12.sp,
                        color = if (actionMessage.contains("mở khóa", ignoreCase = true)) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
        return
    }
    
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
                    enabled = isConnected && !isReadingCard,
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50)),
                    modifier = Modifier.width(200.dp).height(48.dp)
                ) {
                    if (isReadingCard) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isReadingCard) "Đang đọc..." else "Đọc thẻ",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                
                if (!isConnected) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "⚠️ Vui lòng kết nối card reader trước",
                        fontSize = 12.sp,
                        color = Color(0xFFFF9800),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
                
                if (actionMessage.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = actionMessage,
                        fontSize = 12.sp,
                        color = Color(0xFFF44336),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                        valueColor = if (customer.cardType == CardType.MONTHLY) Color(0xFF4CAF50) else Color(0xFF2196F3)
                    )
                }
            }
            
            if (actionMessage.isNotEmpty()) {
                Text(
                    text = actionMessage,
                    fontSize = 12.sp,
                    color = Color(0xFFF57C00)
                )
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
    selectedCustomer: Customer?
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
                        // TOP_UP = nạp tiền (cộng, màu xanh)
                        // TAP, EXTEND_MONTHLY, MONTHLY_PURCHASE = trừ tiền (trừ, màu đỏ)
                        val isDeduction = transactionType in listOf("TAP", "EXTEND_MONTHLY", "MONTHLY_PURCHASE")
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

