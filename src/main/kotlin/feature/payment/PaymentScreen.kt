package feature.payment

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
import core.model.CardType
import core.model.Customer
import core.model.ExtensionRequest
import core.ui.components.*
import feature.pinverification.PinVerificationDialog
import kotlinx.coroutines.*
import java.time.format.DateTimeFormatter

/**
 * Màn hình Nạp tiền - Gia hạn
 * Sử dụng MVVM pattern
 */
@Composable
fun PaymentDialog(
    onDismiss: () -> Unit,
    customers: List<Customer>,
    onTopUp: (String, Double) -> Unit,
    onExtension: (ExtensionRequest) -> Unit
) {
    // Khởi tạo ViewModel
    val paymentViewModel = remember { PaymentViewModel(customers) }
    val state by paymentViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            paymentViewModel.onCleared()
        }
    }
    
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
                selectedTabIndex = state.selectedTab,
                backgroundColor = Color.White,
                contentColor = Color(0xFF2196F3)
            ) {
                Tab(
                    selected = state.selectedTab == 0,
                    onClick = { paymentViewModel.selectTab(0) },
                    text = { Text("Nạp tiền", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = state.selectedTab == 1,
                    onClick = { paymentViewModel.selectTab(1) },
                    text = { Text("Mua vé tháng / Gia hạn vé tháng", fontWeight = FontWeight.Bold) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tab Content
            when (state.selectedTab) {
                0 -> TopUpTab(
                    paymentViewModel = paymentViewModel,
                    state = state.topUpState,
                    customers = customers,
                    onTopUp = { cardId, amount ->
                        onTopUp(cardId, amount)
                        onDismiss()
                    },
                    coroutineScope = coroutineScope
                )
                1 -> ExtensionTab(
                    paymentViewModel = paymentViewModel,
                    state = state.extensionState,
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
 * Tab Nạp tiền vào thẻ - Sử dụng MVVM
 */
@Composable
fun ColumnScope.TopUpTab(
    paymentViewModel: PaymentViewModel,
    state: TopUpState,
    customers: List<Customer>,
    onTopUp: (String, Double) -> Unit,
    coroutineScope: CoroutineScope
) {
    val filteredCustomers = remember(state.cardId, customers) {
        paymentViewModel.getFilteredCustomers(state.cardId, isTopUp = true)
    }
    
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
        Box {
            CustomTextField(
                label = "Card ID",
                value = state.cardId,
                onValueChange = { paymentViewModel.updateTopUpCardId(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Nhập Card ID hoặc tên khách hàng"
            )
            
            // Suggestion dropdown
            if (state.showSuggestions && filteredCustomers.isNotEmpty() && state.selectedCustomer == null) {
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
                                        paymentViewModel.selectTopUpCustomer(customer)
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
        state.selectedCustomer?.let { customer ->
            CustomCard(backgroundColor = Color(0xFFF5F5F5)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoLabel(
                        label = "Tên khách hàng",
                        value = customer.fullName,
                        valueFontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        InfoLabel(
                            label = "Loại thẻ",
                            value = customer.cardType.displayName,
                            modifier = Modifier.weight(1f)
                        )
                        
                        InfoLabel(
                            label = "Số dư hiện tại",
                            value = "${String.format("%,.0f", customer.balance)} VNĐ",
                            valueColor = if (customer.balance > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
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
            value = state.topUpAmount,
            onValueChange = { paymentViewModel.updateTopUpAmount(it) },
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
            paymentViewModel.quickAmounts.forEach { amount ->
                Button(
                    onClick = { paymentViewModel.selectQuickAmount(amount) },
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
        state.selectedCustomer?.let { customer ->
            val amount = state.topUpAmount.toDoubleOrNull() ?: 0.0
            if (amount > 0) {
                CustomCard(backgroundColor = Color(0xFFE8F5E9)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoLabel(
                            label = "Số dư hiện tại",
                            value = "${String.format("%,.0f", customer.balance)} VNĐ",
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
                            value = "${String.format("%,.0f", customer.balance + amount)} VNĐ",
                            valueColor = Color(0xFF2E7D32),
                            valueFontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        
        // Thông báo trạng thái
        if (state.statusMessage.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = if (state.statusMessage.contains("✓")) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
            ) {
                Text(
                    text = state.statusMessage,
                    modifier = Modifier.padding(12.dp),
                    color = if (state.statusMessage.contains("✓")) Color(0xFF4CAF50) else Color(0xFFF44336)
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
            onClick = { paymentViewModel.showTopUpPinDialog() },
            modifier = Modifier.weight(1f),
            enabled = !state.isLoading && state.selectedCustomer != null && state.topUpAmount.isNotEmpty()
        )
    }
    
    // PIN Verification Dialog
    if (state.showPinDialog) {
        PinVerificationDialog(
            title = "Xác thực PIN để nạp tiền",
            onVerified = { pin ->
                coroutineScope.launch {
                    paymentViewModel.processTopUp(onTopUp)
                }
            },
            onDismiss = { paymentViewModel.dismissTopUpPinDialog() }
        )
    }
}

/**
 * Tab Gia hạn thẻ - Sử dụng MVVM
 */
@Composable
fun ColumnScope.ExtensionTab(
    paymentViewModel: PaymentViewModel,
    state: ExtensionState,
    customers: List<Customer>,
    onExtension: (ExtensionRequest) -> Unit
) {
    val filteredCustomers = remember(state.cardId, customers) {
        paymentViewModel.getFilteredCustomers(state.cardId, isTopUp = false)
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
        Box {
            CustomTextField(
                label = "Card ID",
                value = state.cardId,
                onValueChange = { paymentViewModel.updateExtensionCardId(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Nhập Card ID hoặc tên khách hàng"
            )
            
            // Suggestion dropdown
            if (state.showSuggestions && filteredCustomers.isNotEmpty() && state.selectedCustomer == null) {
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
                                        paymentViewModel.selectExtensionCustomer(customer)
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
        state.selectedCustomer?.let { customer ->
            CustomCard(backgroundColor = Color(0xFFF5F5F5)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoLabel(
                        label = "Tên khách hàng",
                        value = customer.fullName,
                        valueFontWeight = FontWeight.Bold
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        InfoLabel(
                            label = "Loại thẻ",
                            value = customer.cardType.displayName,
                            modifier = Modifier.weight(1f)
                        )
                        
                        InfoLabel(
                            label = "Ngày hết hạn",
                            value = customer.expiryDate.format(
                                DateTimeFormatter.ofPattern("dd/MM/yyyy")
                            ),
                            valueColor = Color.Black,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    InfoLabel(
                        label = "Số dư hiện tại",
                        value = "${String.format("%,.0f", customer.balance)} VNĐ",
                        valueColor = if (customer.balance > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                        valueFontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        CustomDivider()
        
        // Hiển thị trạng thái thẻ hiện tại
        state.selectedCustomer?.let { customer ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = when (customer.cardType) {
                    CardType.NORMAL -> Color(0xFFFFF3E0)
                    CardType.MONTHLY -> Color(0xFFE8F5E9)
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
                            text = when (customer.cardType) {
                                CardType.NORMAL -> "Thẻ Thường - Mua vé tháng lần đầu"
                                CardType.MONTHLY -> "Thẻ Tháng - Gia hạn thêm 30 ngày"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )
                    }
                    if (customer.cardType == CardType.MONTHLY) {
                        Text(
                            text = "Ngày hết hạn hiện tại: ${customer.expiryDate.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
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
            value = state.quantity,
            onValueChange = { paymentViewModel.updateExtensionQuantity(it) },
            placeholder = "1"
        )
        
        // Số tiền cần thanh toán
        CustomCard(backgroundColor = Color(0xFFE3F2FD)) {
            InfoLabel(
                label = "Số tiền cần thanh toán",
                value = "${String.format("%,.0f", state.amount)} VNĐ",
                valueColor = Color(0xFF2196F3),
                valueFontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Giá: 100,000 VNĐ/tháng",
                fontSize = 12.sp,
                color = Color.Gray
            )
            
            // Hiển thị số dư sau khi thanh toán
            state.selectedCustomer?.let { customer ->
                if (state.amount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color(0xFFBDBDBD), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    InfoLabel(
                        label = "Số dư hiện tại",
                        value = "${String.format("%,.0f", customer.balance)} VNĐ",
                        valueFontWeight = FontWeight.Bold
                    )
                    
                    InfoLabel(
                        label = "Số tiền thanh toán",
                        value = "- ${String.format("%,.0f", state.amount)} VNĐ",
                        valueColor = Color(0xFFF44336),
                        valueFontWeight = FontWeight.Bold
                    )
                    
                    val remainingBalance = customer.balance - state.amount
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
            onClick = { paymentViewModel.showExtensionPinDialog() },
            modifier = Modifier.weight(1f),
            enabled = state.selectedCustomer != null && 
                      state.quantity.toIntOrNull() != null && 
                      state.quantity.toInt() > 0 &&
                      (state.selectedCustomer?.balance ?: 0.0) >= state.amount
        )
    }
    
    // PIN Verification Dialog
    if (state.showPinDialog && state.pendingRequest != null) {
        PinVerificationDialog(
            title = "Xác thực PIN để thanh toán",
            onVerified = { pin ->
                paymentViewModel.processExtension(onExtension)
            },
            onDismiss = { paymentViewModel.dismissExtensionPinDialog() }
        )
    }
}

