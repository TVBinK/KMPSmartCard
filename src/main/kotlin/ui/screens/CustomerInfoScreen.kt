package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import models.*
import ui.components.*
import smartcard.BusCardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Màn hình Lưu thông tin khách hàng
 * Dạng popup hoặc form riêng
 */
@Composable
fun CustomerInfoDialog(
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit,
    existingCustomer: Customer? = null
) {
    var fullName by remember { mutableStateOf(existingCustomer?.fullName ?: "") }
    var cccd by remember { mutableStateOf(existingCustomer?.cccd ?: "") }
    var dob by remember { mutableStateOf(TextFieldValue(existingCustomer?.dob ?: "")) }
    var address by remember { mutableStateOf(existingCustomer?.address ?: "") }
    var phone by remember { mutableStateOf(existingCustomer?.phone ?: "") }
    var selectedCustomerType by remember { mutableStateOf(existingCustomer?.customerType ?: CustomerType.NORMAL) }
    var expiryDate by remember { mutableStateOf(existingCustomer?.expiryDate ?: LocalDate.now().plusMonths(1)) }
    var selectedCardType by remember { mutableStateOf(existingCustomer?.cardType ?: CardType.NORMAL) }
    var balance by remember { mutableStateOf(existingCustomer?.balance?.toString() ?: "0") }
    var cardId by remember { mutableStateOf(existingCustomer?.cardId ?: generateCardId()) }
    var linkedCustomerCode by remember { mutableStateOf(existingCustomer?.linkedCustomerCode ?: "") }
    var photoPath by remember { mutableStateOf(existingCustomer?.photoPath) }
    var photoBytes by remember { mutableStateOf(existingCustomer?.photoBytes) }
    var showDatePicker by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    Dialog(onDismissRequest = onDismiss) {
        CustomCard(
            modifier = Modifier
                .width(700.dp)
                .heightIn(max = 650.dp)
        ) {
            // Header
            DialogHeader(
                title = if (existingCustomer != null) "Cập nhật thông tin khách hàng" else "Lưu thông tin khách hàng",
                onClose = onDismiss
            )
            
            CustomDivider()
            
            // Content - Scrollable
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Phần bên trái: Ảnh khách hàng
                    Column(
                        modifier = Modifier.width(170.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ImagePlaceholder(
                            photoPath = photoPath,
                            photoBytes = photoBytes,
                            modifier = Modifier,
                            size = Pair(150, 180)
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        CustomButton(
                            text = "Đọc từ thẻ",
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    statusMessage = "Đang đọc từ thẻ..."
                                    
                                    // Kết nối nếu chưa kết nối
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
                                    
                                    // Kiểm tra thẻ đã khởi tạo chưa
                                    val checkResult = withContext(Dispatchers.IO) {
                                        BusCardManager.checkCardCreated()
                                    }
                                    
                                    if (checkResult.isFailure || !checkResult.getOrNull()!!) {
                                        statusMessage = "⚠ Thẻ chưa được khởi tạo! Vui lòng nhập thông tin và click 'Khởi tạo thẻ mới'"
                                        isLoading = false
                                        return@launch
                                    }
                                    
                                    // Đọc thông tin
                                    val infoResult = withContext(Dispatchers.IO) {
                                        BusCardManager.getCustomerInfo()
                                    }
                                    val cardIdResult = withContext(Dispatchers.IO) {
                                        BusCardManager.getCardId()
                                    }
                                    val balanceResult = withContext(Dispatchers.IO) {
                                        BusCardManager.getBalance()
                                    }
                                    
                                    isLoading = false
                                    
                                    infoResult.onSuccess { info ->
                                        fullName = info.fullName
                                        selectedCustomerType = when(info.customerType) {
                                            "HSSV" -> CustomerType.STUDENT
                                            "Người cao tuổi" -> CustomerType.ELDERLY
                                            else -> CustomerType.NORMAL
                                        }
                                        selectedCardType = when(info.cardType) {
                                            "Vé Tháng", "Ve Thang", "Thẻ Tháng" -> CardType.MONTHLY
                                            else -> CardType.NORMAL
                                        }
                                        linkedCustomerCode = info.linkedCustomerId
                                        
                                        // Parse expiry date
                                        try {
                                            expiryDate = LocalDate.parse(info.expiryDate, 
                                                DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                        } catch (e: Exception) {
                                            // Keep current date
                                        }
                                        
                                        statusMessage = "✓ Đã đọc thông tin từ thẻ"
                                    }.onFailure { error ->
                                        statusMessage = "Lỗi đọc: ${error.message}"
                                    }
                                    
                                    cardIdResult.onSuccess { id ->
                                        cardId = id
                                    }
                                    
                                    balanceResult.onSuccess { bal ->
                                        balance = bal.toInt().toString()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Color(0xFF4CAF50),
                            icon = Icons.Default.Person,
                            enabled = !isLoading
                        )
                    }
                    
                    // Phần bên phải: Các trường nhập liệu
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Họ tên
                        CustomTextField(
                            label = "Họ tên *",
                            value = fullName,
                            onValueChange = { fullName = it },
                            placeholder = "Nhập họ tên khách hàng"
                        )
                        
                        // CCCD
                        CustomTextField(
                            label = "CCCD (12 số) *",
                            value = cccd,
                            onValueChange = { value -> 
                                if (value.all { it.isDigit() } && value.length <= 12) {
                                    cccd = value
                                }
                            },
                            placeholder = "123456789012"
                        )
                        
                        // Ngày sinh
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "Ngày sinh (dd/MM/yyyy) *",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = dob,
                                onValueChange = { value ->
                                    // Format tự động: dd/MM/yyyy
                                    val formatted = ui.components.formatDateOfBirth(value.text)
                                    // Đặt con trỏ về cuối sau khi format
                                    val cursorPosition = formatted.length
                                    dob = TextFieldValue(formatted, TextRange(cursorPosition))
                                },
                                placeholder = { Text("01/01/2000", fontSize = 14.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    backgroundColor = Color.White,
                                    focusedBorderColor = Color(0xFF2196F3),
                                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        
                        // Địa chỉ
                        CustomTextField(
                            label = "Địa chỉ hiện tại *",
                            value = address,
                            onValueChange = { address = it },
                            placeholder = "Số nhà, đường, phường/xã, quận/huyện"
                        )
                        
                        // Số điện thoại
                        CustomTextField(
                            label = "Số điện thoại (10 số) *",
                            value = phone,
                            onValueChange = { value -> 
                                if (value.all { it.isDigit() } && value.length <= 10) {
                                    phone = value
                                }
                            },
                            placeholder = "0912345678"
                        )
                        
                        // Loại đối tượng
                        CustomDropdown(
                            label = "Loại đối tượng *",
                            items = CustomerType.values().toList(),
                            selectedItem = selectedCustomerType,
                            onItemSelected = { selectedCustomerType = it },
                            itemLabel = { it.displayName }
                        )
                        
                        // Ngày hết hạn
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "Ngày hết hạn *",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = expiryDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                        fontSize = 14.sp,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                        
                        // Loại thẻ
                        CustomDropdown(
                            label = "Loại thẻ *",
                            items = listOf(CardType.NORMAL, CardType.MONTHLY),  // Chỉ hiển thị NORMAL và MONTHLY
                            selectedItem = selectedCardType,
                            onItemSelected = { selectedCardType = it },
                            itemLabel = { it.displayName }
                        )
                        
                        // Số dư
                        NumericTextField(
                            label = "Số dư (VNĐ) *",
                            value = balance,
                            onValueChange = { balance = it },
                            placeholder = "0"
                        )
                        
                        // Card ID
                        CustomTextField(
                            label = "Card ID",
                            value = cardId,
                            onValueChange = { cardId = it },
                            readOnly = true,
                            placeholder = "Tự động sinh"
                        )
                        
                        // Mã khách hàng liên kết
                        CustomTextField(
                            label = "Mã khách hàng liên kết",
                            value = linkedCustomerCode,
                            onValueChange = { linkedCustomerCode = it },
                            placeholder = "Tùy chọn"
                        )
                    }
                }
            }
            
            CustomDivider()
            
            // Status message
            if (statusMessage.isNotEmpty()) {
                Text(
                    text = statusMessage,
                    fontSize = 12.sp,
                    color = if (statusMessage.startsWith("✓")) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Đang xử lý...", fontSize = 12.sp)
                }
            }
            
            CustomDivider()
            
            // Footer - Nút hành động
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CancelButton(
                    text = "Hủy",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                
                ConfirmButton(
                    text = if (existingCustomer != null) "Cập nhật & Lưu lên thẻ" else "Lưu lên thẻ",
                    onClick = {
                        // Validate
                        if (fullName.isBlank()) {
                            statusMessage = "Vui lòng nhập đầy đủ thông tin"
                            return@ConfirmButton
                        }
                        
                        scope.launch {
                            isLoading = true
                            statusMessage = "Đang lưu lên thẻ..."
                            
                            // Kết nối nếu chưa kết nối
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
                            
                            val balanceValue = balance.toDoubleOrNull() ?: 0.0
                            
                            // Lưu thông tin lên thẻ
                            val customerTypeStr = when(selectedCustomerType) {
                                CustomerType.STUDENT -> "HSSV"
                                CustomerType.ELDERLY -> "Người cao tuổi"
                                else -> "Thông thường"
                            }
                            
                            val cardTypeStr = when(selectedCardType) {
                                CardType.MONTHLY -> "Vé Tháng"
                                else -> "Thẻ Thường"
                            }
                            
                            val expiryDateStr = expiryDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            
                            // Cập nhật thông tin
                            val updateInfoResult = withContext(Dispatchers.IO) {
                                BusCardManager.updateCustomerInfo(
                                    fullName = fullName,
                                    customerType = customerTypeStr,
                                    expiryDate = expiryDateStr,
                                    cardType = cardTypeStr,
                                    linkedCustomerId = linkedCustomerCode
                                )
                            }
                            
                            // Cập nhật số dư
                            val updateBalanceResult = withContext(Dispatchers.IO) {
                                BusCardManager.updateBalance(balanceValue)
                            }
                            
                            // Cập nhật Card ID
                            val updateCardIdResult = withContext(Dispatchers.IO) {
                                BusCardManager.updateCardId(cardId)
                            }
                            
                            isLoading = false
                            
                            if (updateInfoResult.isSuccess && updateBalanceResult.isSuccess && updateCardIdResult.isSuccess) {
                                statusMessage = "✓ Đã lưu thông tin lên thẻ thành công!"
                                
                                // Lưu vào database local
                                val customer = Customer(
                                    id = existingCustomer?.id ?: UUID.randomUUID().toString(),
                                    fullName = fullName,
                                    cccd = cccd,
                                    dob = dob.text,
                                    address = address,
                                    phone = phone,
                                    customerType = selectedCustomerType,
                                    expiryDate = expiryDate,
                                    cardType = selectedCardType,
                                    balance = balanceValue,
                                    cardId = cardId,
                                    linkedCustomerCode = linkedCustomerCode,
                                    photoPath = photoPath,
                                    photoBytes = photoBytes
                                )
                                
                                onSave(customer)
                                
                                // Đợi 1 giây rồi đóng
                                kotlinx.coroutines.delay(1000)
                                onDismiss()
                            } else {
                                statusMessage = "Lỗi lưu thẻ: ${updateInfoResult.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = fullName.isNotBlank() && balance.isNotBlank() && !isLoading
                )
            }
        }
    }
    
    // DatePicker Dialog (simplified)
    if (showDatePicker) {
        DatePickerDialog(
            currentDate = expiryDate,
            onDateSelected = { 
                expiryDate = it
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

/**
 * DatePicker Dialog đơn giản
 */
@Composable
fun DatePickerDialog(
    currentDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedYear by remember { mutableStateOf(currentDate.year) }
    var selectedMonth by remember { mutableStateOf(currentDate.monthValue) }
    var selectedDay by remember { mutableStateOf(currentDate.dayOfMonth) }
    
    Dialog(onDismissRequest = onDismiss) {
        CustomCard(modifier = Modifier.width(400.dp)) {
            Text(
                text = "Chọn ngày hết hạn",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2196F3)
            )
            
            CustomDivider()
            
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Năm
                CustomDropdown(
                    label = "Năm",
                    items = (2024..2030).toList(),
                    selectedItem = selectedYear,
                    onItemSelected = { selectedYear = it }
                )
                
                // Tháng
                CustomDropdown(
                    label = "Tháng",
                    items = (1..12).toList(),
                    selectedItem = selectedMonth,
                    onItemSelected = { selectedMonth = it },
                    itemLabel = { "Tháng $it" }
                )
                
                // Ngày
                CustomDropdown(
                    label = "Ngày",
                    items = (1..31).toList(),
                    selectedItem = selectedDay,
                    onItemSelected = { selectedDay = it },
                    itemLabel = { "Ngày $it" }
                )
            }
            
            CustomDivider()
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CancelButton(
                    text = "Hủy",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                
                ConfirmButton(
                    text = "Chọn",
                    onClick = {
                        try {
                            val date = LocalDate.of(selectedYear, selectedMonth, selectedDay)
                            onDateSelected(date)
                        } catch (e: Exception) {
                            // Invalid date
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Sinh Card ID tự động
 */
private fun generateCardId(): String {
    return "CARD-${System.currentTimeMillis()}-${Random().nextInt(9999)}"
}

