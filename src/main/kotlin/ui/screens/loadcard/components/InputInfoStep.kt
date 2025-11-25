package ui.screens.loadcard.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import models.CardType
import models.Customer
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun InputInfoStepContent(
    existingCustomers: List<Customer>,
    useExistingData: Boolean,
    onUseExistingDataChange: (Boolean) -> Unit,
    selectedExistingCustomer: Customer?,
    onSelectExistingCustomer: (Customer) -> Unit,
    cardId: String,
    onCardIdChange: (String) -> Unit,
    fullName: String,
    onFullNameChange: (String) -> Unit,
    cccd: String,
    onCccdChange: (String) -> Unit,
    dob: String,
    onDobChange: (String) -> Unit,
    address: String,
    onAddressChange: (String) -> Unit,
    phone: String,
    onPhoneChange: (String) -> Unit,
    cardType: CardType,
    onCardTypeChange: (CardType) -> Unit,
    expiryDate: LocalDate,
    onExpiryDateChange: (LocalDate) -> Unit,
    balance: String,
    onBalanceChange: (String) -> Unit,
    pin: String,
    onPinChange: (String) -> Unit,
    photoBytes: ByteArray?,
    onPhotoChange: (ByteArray?) -> Unit,
    photoSizeLimitBytes: Int,
    onNext: () -> Unit
) {
    var pinVisible by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val photoSizeBytes = photoBytes?.size ?: 0
    val sizeLimitKb = photoSizeLimitBytes / 1024.0
    val currentSizeKb = photoSizeBytes / 1024.0
    val photoSizeText = if (photoBytes != null) {
        String.format("Kích thước ảnh: %.1f KB / %.1f KB", currentSizeKb, sizeLimitKb)
    } else {
        String.format("Giới hạn kích thước ảnh: ≤ %.1f KB", sizeLimitKb)
    }
    val isPhotoTooLarge = photoBytes != null && photoSizeBytes > photoSizeLimitBytes
    val photoSizeColor = if (isPhotoTooLarge) Color(0xFFD32F2F) else Color.Gray
    
    // Local state cho TextFieldValue để quản lý cursor
    var dobTextFieldValue by remember { mutableStateOf(TextFieldValue(dob)) }
    
    // Đồng bộ dobTextFieldValue khi dob thay đổi từ bên ngoài
    LaunchedEffect(dob) {
        if (dobTextFieldValue.text != dob) {
            dobTextFieldValue = TextFieldValue(dob, TextRange(dob.length))
        }
    }
    
    // Tự động điền thông tin khi chọn khách hàng có sẵn
    LaunchedEffect(selectedExistingCustomer) {
        selectedExistingCustomer?.let { customer ->
            println("[LoadCardInfo] LaunchedEffect: Bat dau dien thong tin tu khach hang da chon")
            onFullNameChange(customer.fullName)
            onCccdChange(customer.cccd)
            onDobChange(customer.dob)
            onAddressChange(customer.address)
            onPhoneChange(customer.phone)
            onCardTypeChange(customer.cardType)
            onExpiryDateChange(customer.expiryDate)
            onBalanceChange(customer.balance.toInt().toString())
            onPhotoChange(customer.photoBytes)
            
            println("[LoadCardInfo] LaunchedEffect: Da goi tat ca callbacks de dien thong tin")
            // Card ID và PIN để trống để user nhập mới cho thẻ này
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Bước 3: Nhập thông tin khách hàng",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3)
        )

        if (existingCustomers.isNotEmpty()) {
            Card(backgroundColor = Color(0xFFE3F2FD)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Có ${existingCustomers.size} khách hàng trong database",
                            fontSize = 13.sp,
                            color = Color(0xFF1976D2),
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = useExistingData,
                            onCheckedChange = onUseExistingDataChange,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF4CAF50))
                        )
                    }
                    Text(
                        text = if (useExistingData) "Chọn khách hàng có sẵn" else "Nhập thông tin mới",
                        fontSize = 11.sp,
                        color = Color(0xFF666666)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (useExistingData && existingCustomers.isNotEmpty()) {
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedExistingCustomer?.let { "${it.fullName} (${it.cardId})" } ?: "Chọn khách hàng...",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Khách hàng có sẵn") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.heightIn(max = 220.dp)
                    ) {
                        existingCustomers.take(6).forEach { customer ->
                            DropdownMenuItem(
                                onClick = {
                                    onSelectExistingCustomer(customer)
                                    expanded = false
                                }
                            ) {
                                Column {
                                    Text(customer.fullName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        "${customer.customerType.displayName} - ${customer.cardType.displayName}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                        if (existingCustomers.size > 6) {
                            Divider()
                            DropdownMenuItem(onClick = {}) {
                                Text(
                                    text = "... và ${existingCustomers.size - 6} khách hàng khác",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                if (selectedExistingCustomer != null) {
                    // Kiểm tra xem khách hàng có đầy đủ thông tin không
                    val hasCompleteInfo = selectedExistingCustomer.cccd.isNotBlank() && 
                                         selectedExistingCustomer.dob.isNotBlank() && 
                                         selectedExistingCustomer.address.isNotBlank() && 
                                         selectedExistingCustomer.phone.isNotBlank()
                    
                    Card(backgroundColor = if (hasCompleteInfo) Color(0xFFF1F8E9) else Color(0xFFFFF3E0)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (hasCompleteInfo) Icons.Default.Check else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (hasCompleteInfo) Color(0xFF558B2F) else Color(0xFFFF9800),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Đã chọn: ${selectedExistingCustomer.fullName}",
                                    fontSize = 13.sp,
                                    color = if (hasCompleteInfo) Color(0xFF558B2F) else Color(0xFFFF9800),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (hasCompleteInfo) {
                                Text(
                                    "✓ Tất cả thông tin đã được điền tự động từ khách hàng đã chọn",
                                    fontSize = 11.sp,
                                    color = Color(0xFF558B2F),
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text(
                                    "⚠️ Khách hàng này thiếu một số thông tin (CCCD, Ngày sinh, Địa chỉ, Số điện thoại)",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFF9800),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "• Vui lòng điền đầy đủ các thông tin còn thiếu",
                                    fontSize = 11.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                            Text(
                                "• Bạn có thể chỉnh sửa bất kỳ thông tin nào nếu cần",
                                fontSize = 11.sp,
                                color = Color(0xFF666666)
                            )
                            Text(
                                "• Chỉ cần nhập Card ID mới và PIN mới cho thẻ này",
                                fontSize = 11.sp,
                                color = Color(0xFF666666)
                            )
                            Text(
                                "• Khách hàng có thể có nhiều thẻ với ID khác nhau",
                                fontSize = 11.sp,
                                color = Color(0xFF666666),
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
            }

            // Cho phép chỉnh sửa tất cả các trường ngay cả khi chọn khách hàng có sẵn
            val editable = true

            // Validation states - hiển thị màu đỏ khi thiếu hoặc không hợp lệ
            val isCardIdError = cardId.isBlank()
            val isFullNameError = fullName.isBlank()
            val isCccdError = cccd.isBlank() || cccd.length != 12 || !cccd.all { it.isDigit() }
            val isPhoneError = phone.isBlank() || phone.length != 10 || !phone.all { it.isDigit() }
            val isDobError = try {
                if (dob.isBlank() || !dob.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))) {
                    true
                } else {
                    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    val dobDate = LocalDate.parse(dob, formatter)
                    !dobDate.isBefore(LocalDate.now())
                }
            } catch (e: Exception) {
                true
            }
            val isAddressError = address.isBlank()
            val isPinError = pin.isBlank() || pin.length < 4 || pin.length > 6

            // Row 1: Mã thẻ + Họ tên
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = cardId,
                    onValueChange = onCardIdChange,
                    label = { Text("Mã thẻ *") },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    isError = isCardIdError,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = if (isCardIdError) Color(0xFFF44336) else Color(0xFF2196F3),
                        unfocusedBorderColor = if (isCardIdError) Color(0xFFF44336) else Color.Gray
                    )
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = onFullNameChange,
                    label = { Text("Họ tên *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = editable,
                    isError = isFullNameError,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        disabledTextColor = Color.Black,
                        disabledLabelColor = Color.Gray,
                        focusedBorderColor = if (isFullNameError) Color(0xFFF44336) else Color(0xFF2196F3),
                        unfocusedBorderColor = if (isFullNameError) Color(0xFFF44336) else Color.Gray
                    )
                )
            }

            // Row 1.5: CCCD + Số điện thoại
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = cccd,
                    onValueChange = { value -> 
                        // Chỉ cho phép số và tối đa 12 ký tự
                        if (value.all { it.isDigit() } && value.length <= 12) {
                            onCccdChange(value)
                        }
                    },
                    label = { Text("CCCD (12 số) *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = editable,
                    isError = isCccdError,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        disabledTextColor = Color.Black,
                        disabledLabelColor = Color.Gray,
                        focusedBorderColor = if (isCccdError) Color(0xFFF44336) else Color(0xFF2196F3),
                        unfocusedBorderColor = if (isCccdError) Color(0xFFF44336) else Color.Gray
                    ),
                    placeholder = { Text("123456789012") }
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { value -> 
                        // Chỉ cho phép số và tối đa 10 ký tự
                        if (value.all { it.isDigit() } && value.length <= 10) {
                            onPhoneChange(value)
                        }
                    },
                    label = { Text("Số điện thoại (10 số) *") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = editable,
                    isError = isPhoneError,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        disabledTextColor = Color.Black,
                        disabledLabelColor = Color.Gray,
                        focusedBorderColor = if (isPhoneError) Color(0xFFF44336) else Color(0xFF2196F3),
                        unfocusedBorderColor = if (isPhoneError) Color(0xFFF44336) else Color.Gray
                    ),
                    placeholder = { Text("0912345678") }
                )
            }

            // Row 1.6: Ngày sinh + Địa chỉ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = dobTextFieldValue,
                    onValueChange = { value ->
                        // Format tự động: dd/MM/yyyy
                        val formatted = ui.components.formatDateOfBirth(value.text)
                        // Đặt con trỏ về cuối sau khi format
                        val cursorPosition = formatted.length
                        dobTextFieldValue = TextFieldValue(formatted, TextRange(cursorPosition))
                        onDobChange(formatted)
                    },
                    label = { Text("Ngày sinh (dd/MM/yyyy) *") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = editable,
                    isError = isDobError,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        disabledTextColor = Color.Black,
                        disabledLabelColor = Color.Gray,
                        focusedBorderColor = if (isDobError) Color(0xFFF44336) else Color(0xFF2196F3),
                        unfocusedBorderColor = if (isDobError) Color(0xFFF44336) else Color.Gray
                    ),
                    placeholder = { Text("01/01/2000") }
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = onAddressChange,
                    label = { Text("Địa chỉ hiện tại *") },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = editable,
                    isError = isAddressError,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        disabledTextColor = Color.Black,
                        disabledLabelColor = Color.Gray,
                        focusedBorderColor = if (isAddressError) Color(0xFFF44336) else Color(0xFF2196F3),
                        unfocusedBorderColor = if (isAddressError) Color(0xFFF44336) else Color.Gray
                    ),
                    placeholder = { Text("Số nhà, đường, phường/xã, quận/huyện") }
                )
            }

            // Row 2: Loại thẻ (đã xóa Loại đối tượng)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var cardTypeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = cardTypeExpanded,
                    onExpandedChange = { if (editable) cardTypeExpanded = !cardTypeExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = cardType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Loại thẻ") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cardTypeExpanded) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editable
                    )
                    ExposedDropdownMenu(
                        expanded = cardTypeExpanded,
                        onDismissRequest = { cardTypeExpanded = false }
                    ) {
                        CardType.values().forEach { type ->
                            DropdownMenuItem(onClick = {
                                onCardTypeChange(type)
                                cardTypeExpanded = false
                            }) {
                                Text(type.displayName)
                            }
                        }
                    }
                }
                
                // Spacer để giữ layout đẹp
                Spacer(modifier = Modifier.weight(1f))
            }

            // Row 3: Số dư + Mã PIN
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = balance,
                    onValueChange = { value -> onBalanceChange(value.filter { it.isDigit() }) },
                    label = { Text("Số dư ban đầu (VNĐ)") },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = editable
                )

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) onPinChange(it) },
                    label = { Text("Mã PIN (4-6 số) *") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { pinVisible = !pinVisible }) {
                            Text(
                                text = if (pinVisible) "Ẩn" else "Hiện",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = isPinError,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = if (isPinError) Color(0xFFF44336) else Color(0xFF2196F3),
                        unfocusedBorderColor = if (isPinError) Color(0xFFF44336) else Color.Gray
                    )
                )
            }

            // Ngày hết hạn
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Ngày hết hạn: ${expiryDate.format(dateFormatter)}",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            // Photo picker
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFFF5F5F5),
                elevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Preview ảnh hoặc placeholder
                    if (photoBytes != null) {
                        val imageBitmap = remember(photoBytes) {
                            try {
                                org.jetbrains.skia.Image.makeFromEncoded(photoBytes).asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        
                        if (imageBitmap != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = "Photo",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .border(2.dp, Color(0xFF4CAF50), RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    text = photoSizeText,
                                    fontSize = 12.sp,
                                    color = photoSizeColor,
                                    fontWeight = FontWeight.Medium
                                )
                                if (isPhotoTooLarge) {
                                    Text(
                                        text = "Ảnh vượt quá giới hạn, vui lòng chọn ảnh khác.",
                                        fontSize = 12.sp,
                                        color = Color(0xFFD32F2F)
                                    )
                                }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(Color(0xFFE0E0E0), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Chưa chọn ảnh",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = photoSizeText,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    // Buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val fileChooser = JFileChooser()
                                fileChooser.fileFilter = FileNameExtensionFilter(
                                    "Image files", "jpg", "jpeg", "png", "gif"
                                )
                                val result = fileChooser.showOpenDialog(null)
                                if (result == JFileChooser.APPROVE_OPTION) {
                                    val file = fileChooser.selectedFile
                                    try {
                                        val image = ImageIO.read(file)
                                        // Không resize nữa, chỉ nén JPEG để giữ kích thước hợp lý
                                        val baos = ByteArrayOutputStream()
                                        ImageIO.write(image, "jpg", baos)
                                        val bytes = baos.toByteArray()
                                        println("Anh JPEG goc: ${bytes.size} bytes (${image.width}x${image.height})")
                                        
                                        if (bytes.size > 51200) {
                                            println("Canh bao: Anh co kich thuoc ${bytes.size} bytes (> 50KB), co the cham khi ghi vao the")
                                        }
                                        
                                        onPhotoChange(bytes)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chọn ảnh", color = Color.White, fontSize = 12.sp)
                        }
                        
                        if (photoBytes != null) {
                            Button(
                                onClick = { onPhotoChange(null) },
                                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF5252))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
            ) {
                Text("Tiếp tục", color = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
            }
        }
    }
}
