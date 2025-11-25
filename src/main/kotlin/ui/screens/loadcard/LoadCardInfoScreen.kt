package ui.screens.loadcard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.CardType
import models.Customer
import models.CustomerType
import smartcard.BusCardManager
import ui.screens.loadcard.components.CheckCardStepContent
import ui.screens.loadcard.components.ConnectStepContent
import ui.screens.loadcard.components.InputInfoStepContent
import ui.screens.loadcard.components.WriteDataStepContent
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val MAX_PHOTO_SIZE_BYTES = 32767

enum class LoadStep {
    CONNECT,
    CHECK_CARD,
    INPUT_INFO,
    WRITE_DATA
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LoadCardInfoDialog(
    onDismiss: () -> Unit,
    onSuccess: (Customer) -> Unit
) {
    var currentStep by remember { mutableStateOf(LoadStep.CONNECT) }
    var isConnected by remember { mutableStateOf(false) }
    var isCardEmpty by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Chưa kết nối với thẻ") }
    var isLoading by remember { mutableStateOf(false) }

    var existingCustomers by remember { mutableStateOf<List<Customer>>(emptyList()) }
    var selectedExistingCustomer by remember { mutableStateOf<Customer?>(null) }
    var useExistingData by remember { mutableStateOf(false) }

    var cardId by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var cccd by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf(TextFieldValue("")) }
    var address by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val customerType = CustomerType.CUSTOMER
    var cardType by remember { mutableStateOf(CardType.NORMAL) }  // Mặc định Thẻ Thường
    var expiryDate by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var balance by remember { mutableStateOf("100000") }
    var pin by remember { mutableStateOf("") }
    var linkedCustomerCode by remember { mutableStateOf("") }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    val isPhotoTooLarge = (photoBytes?.size ?: 0) > MAX_PHOTO_SIZE_BYTES

    val scope = rememberCoroutineScope()

    // Load danh sách khách hàng khi dialog mở
    LaunchedEffect(Unit) {
        existingCustomers = DatabaseManager.getAllCustomers()
        
        // Kiểm tra trạng thái kết nối hiện tại
        if (BusCardManager.isConnected) {
            isConnected = true
            statusMessage = "✓ Đã kết nối với thẻ"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Title Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nạp thông tin vào thẻ Smart Card",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                LinearProgressIndicator(
                    progress = when (currentStep) {
                        LoadStep.CONNECT -> 0.25f
                        LoadStep.CHECK_CARD -> 0.50f
                        LoadStep.INPUT_INFO -> 0.75f
                        LoadStep.WRITE_DATA -> 1f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = when {
                                    isLoading -> Color(0xFFFFC107)
                                    isConnected -> Color(0xFF4CAF50)
                                    else -> Color(0xFFFF5252)
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = statusMessage, fontSize = 12.sp, color = Color.Gray)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content Section with fixed height and scroll
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Đang xử lý...", color = Color.Gray)
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            when (currentStep) {
                                LoadStep.CONNECT -> ConnectStepContent(
                                    isConnected = isConnected,
                                    onConnect = {
                                        scope.launch {
                                            isLoading = true
                                            statusMessage = "Đang kết nối với thẻ..."
                                            val result = withContext(Dispatchers.IO) { BusCardManager.connect() }
                                            isLoading = false
                                            result.onSuccess {
                                                isConnected = true
                                                statusMessage = "✓ Đã kết nối với thẻ"
                                                // Không tự động chuyển, để user nhấn Tiếp tục
                                            }.onFailure { error ->
                                                statusMessage = "✗ ${error.message}"
                                            }
                                        }
                                    },
                                    onNext = {
                                        currentStep = LoadStep.CHECK_CARD
                                    }
                                )

                                LoadStep.CHECK_CARD -> CheckCardStepContent(
                                    onCheck = {
                                        scope.launch {
                                            isLoading = true
                                            statusMessage = "Đang kiểm tra thẻ..."
                                            val checkResult = withContext(Dispatchers.IO) {
                                                BusCardManager.checkCardCreated()
                                            }
                                            isLoading = false
                                            checkResult.onSuccess { hasData ->
                                                if (hasData) {
                                                    statusMessage = "⚠ Thẻ đã có dữ liệu. Vui lòng xóa dữ liệu cũ trước"
                                                    isCardEmpty = false
                                                } else {
                                                    statusMessage = "✓ Thẻ rỗng, sẵn sàng nạp dữ liệu"
                                                    isCardEmpty = true
                                                    currentStep = LoadStep.INPUT_INFO
                                                }
                                            }.onFailure { error ->
                                                statusMessage = "✗ ${error.message}"
                                            }
                                        }
                                    },
                                    onClearCard = {
                                        scope.launch {
                                            isLoading = true
                                            statusMessage = "Đang xóa dữ liệu thẻ..."
                                            println("Bat dau xoa du lieu the...")
                                            
                                            val result = withContext(Dispatchers.IO) { BusCardManager.clearCard() }
                                            
                                            println("Ket qua xoa: ${if (result.isSuccess) "Thanh cong" else "That bai - ${result.exceptionOrNull()?.message}"}")
                                            
                                            result.onSuccess {
                                                println("Xoa the thanh cong, dong dialog")
                                                statusMessage = "✓ Đã xóa dữ liệu thẻ"
                                                isLoading = false
                                                delay(500) // Delay ngắn để user thấy thông báo thành công
                                                onDismiss() // Đóng dialog
                                            }.onFailure { error ->
                                                isLoading = false
                                                statusMessage = "✗ ${error.message}"
                                                println("Loi xoa the: ${error.message}")
                                            }
                                        }
                                    },
                                    isCardEmpty = isCardEmpty
                                )

                                LoadStep.INPUT_INFO -> InputInfoStepContent(
                                    existingCustomers = existingCustomers,
                                    useExistingData = useExistingData,
                                    onUseExistingDataChange = { use ->
                                        useExistingData = use
                                        if (!use) selectedExistingCustomer = null
                                    },
                                    selectedExistingCustomer = selectedExistingCustomer,
                                    onSelectExistingCustomer = { customer ->
                                        selectedExistingCustomer = customer
                                        // Điền đầy đủ tất cả thông tin từ khách hàng đã chọn bằng cách gọi callbacks
                                        // Sử dụng các callback để cập nhật state đúng cách
                                    },
                                    cardId = cardId,
                                    onCardIdChange = { cardId = it },
                                    fullName = fullName,
                                    onFullNameChange = { fullName = it },
                                    cccd = cccd,
                                    onCccdChange = { cccd = it },
                                    dob = dob.text,
                                    onDobChange = { dob = TextFieldValue(it) },
                                    address = address,
                                    onAddressChange = { address = it },
                                    phone = phone,
                                    onPhoneChange = { phone = it },
                                    cardType = cardType,
                                    onCardTypeChange = { cardType = it },
                                    expiryDate = expiryDate,
                                    onExpiryDateChange = { expiryDate = it },
                                    balance = balance,
                                    onBalanceChange = { balance = it },
                                    pin = pin,
                                    onPinChange = { pin = it },
                                    photoBytes = photoBytes,
                                    onPhotoChange = { photoBytes = it },
                                    photoSizeLimitBytes = MAX_PHOTO_SIZE_BYTES,
                                    onNext = {
                                        when {
                                            isPhotoTooLarge -> {
                                                statusMessage = "⚠ Ảnh vượt quá 32KB. Vui lòng chọn ảnh nhỏ hơn"
                                            }
                                            validateInput(cardId, fullName, cccd, dob.text, phone, pin) -> {
                                                currentStep = LoadStep.WRITE_DATA
                                                statusMessage = "Sẵn sàng ghi dữ liệu lên thẻ"
                                            }
                                            else -> {
                                                statusMessage = "⚠ Vui lòng điền đầy đủ thông tin bắt buộc"
                                            }
                                        }
                                    }
                                )

                                LoadStep.WRITE_DATA -> WriteDataStepContent(
                                    cardId = cardId,
                                    fullName = fullName,
                                    cccd = cccd,
                                    dob = dob.text,
                                    address = address,
                                    phone = phone,
                                    customerType = customerType,
                                    cardType = cardType,
                                    balance = balance,
                                    photoSizeBytes = photoBytes?.size ?: 0,
                                    photoSizeLimitBytes = MAX_PHOTO_SIZE_BYTES,
                                    isPhotoTooLarge = isPhotoTooLarge,
                                    onWrite = {
                                        scope.launch {
                                            isLoading = true
                                            statusMessage = "Đang ghi dữ liệu lên thẻ..."
                                            val writeSuccess = withContext(Dispatchers.IO) {
                                                writeDataToCard(
                                                    cardId = cardId,
                                                    fullName = fullName,
                                                    customerType = customerType,
                                                    cardType = cardType,
                                                    expiryDate = expiryDate,
                                                    balance = balance.toDoubleOrNull() ?: 0.0,
                                                    pin = pin,
                                                    linkedCustomerCode = linkedCustomerCode,
                                                    photoBytes = photoBytes
                                                )
                                            }

                                            if (writeSuccess) {
                                                val newCustomer = Customer(
                                                    id = cardId,
                                                    cardId = cardId,
                                                    fullName = fullName,
                                                    cccd = cccd,
                                                    dob = dob.text,
                                                    address = address,
                                                    phone = phone,
                                                    customerType = customerType,
                                                    cardType = cardType,
                                                    expiryDate = expiryDate,
                                                    balance = balance.toDoubleOrNull() ?: 0.0,
                                                    linkedCustomerCode = linkedCustomerCode,
                                                    photoBytes = photoBytes
                                                )

                                                statusMessage = "💾 Đang lưu vào database..."
                                                val existing = withContext(Dispatchers.IO) {
                                                    DatabaseManager.getCustomerByCardId(cardId)
                                                }

                                                val insertSuccess = if (existing == null) {
                                                    withContext(Dispatchers.IO) {
                                                        // Lưu với mã hóa bằng PIN
                                                        DatabaseManager.insertCustomer(newCustomer, pin)
                                                    }
                                                } else {
                                                    false
                                                }

                                                if (insertSuccess) {
                                                    withContext(Dispatchers.IO) {
                                                        DatabaseManager.insertTransaction(
                                                            cardId = cardId,
                                                            transactionType = "INITIAL_TOP_UP",
                                                            amount = balance.toDoubleOrNull() ?: 0.0,
                                                            balanceBefore = 0.0,
                                                            balanceAfter = balance.toDoubleOrNull() ?: 0.0,
                                                            description = "Nạp tiền ban đầu khi khởi tạo thẻ"
                                                        )
                                                    }
                                                    statusMessage = "✅ Ghi dữ liệu và lưu database thành công!"
                                                } else if (existing != null) {
                                                    statusMessage = "✅ Ghi thẻ thành công! (Card ID đã có trong database)"
                                                } else {
                                                    statusMessage = "⚠️ Ghi thẻ thành công nhưng lỗi lưu database"
                                                }

                                                isLoading = false
                                                onSuccess(newCustomer)
                                                onDismiss()
                                            } else {
                                                isLoading = false
                                                statusMessage = "✗ Ghi dữ liệu thất bại"
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                // Buttons Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Hiển thị nút Quay lại nếu không phải bước đầu tiên và không loading
                    // Nếu ở CHECK_CARD và đã bỏ qua CONNECT thì không cho quay lại
                    val showBackButton = !isLoading && when (currentStep) {
                        LoadStep.CONNECT -> false
                        LoadStep.CHECK_CARD -> false // Đã bỏ qua CONNECT nên không cho quay lại
                        LoadStep.INPUT_INFO -> true
                        LoadStep.WRITE_DATA -> true
                    }
                    
                    if (showBackButton) {
                        Button(
                            onClick = {
                                currentStep = when (currentStep) {
                                    LoadStep.INPUT_INFO -> LoadStep.CHECK_CARD
                                    LoadStep.WRITE_DATA -> LoadStep.INPUT_INFO
                                    else -> currentStep
                                }
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF757575))
                        ) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Quay lại")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Quay lại", color = Color.White)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF5252))
                    ) {
                        Text("Đóng", color = Color.White)
                    }
                }
            }
        }
    }
}

private fun validateInput(cardId: String, fullName: String, cccd: String, dob: String, phone: String, pin: String): Boolean {
    // Validate CCCD: đúng 12 chữ số
    val isValidCccd = cccd.length == 12 && cccd.all { it.isDigit() }
    
    // Validate SĐT: đúng 10 chữ số
    val isValidPhone = phone.length == 10 && phone.all { it.isDigit() }
    
    // Validate DOB: định dạng dd/MM/yyyy và < ngày hiện tại
    var isValidDob = false
    try {
        if (dob.matches(Regex("\\d{2}/\\d{2}/\\d{4}"))) {
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val dobDate = LocalDate.parse(dob, formatter)
            isValidDob = dobDate.isBefore(LocalDate.now())
        }
    } catch (e: Exception) {
        isValidDob = false
    }
    
    return cardId.isNotBlank() && 
           fullName.isNotBlank() && 
           isValidCccd && 
           isValidDob && 
           isValidPhone && 
           pin.length in 4..6
}

private suspend fun writeDataToCard(
    cardId: String,
    fullName: String,
    customerType: CustomerType,
    cardType: CardType,
    expiryDate: LocalDate,
    balance: Double,
    pin: String,
    linkedCustomerCode: String,
    photoBytes: ByteArray? = null
): Boolean {
    return try {
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val expiryString = expiryDate.format(formatter)

        val infoResult = BusCardManager.updateCustomerInfo(
            fullName = fullName,
            customerType = customerType.displayName,
            expiryDate = expiryString,
            cardType = cardType.displayName,
            linkedCustomerId = linkedCustomerCode
        )
        if (infoResult.isFailure) return false

        val cardIdResult = BusCardManager.updateCardId(cardId)
        if (cardIdResult.isFailure) return false

        val balanceResult = BusCardManager.updateBalance(balance)
        if (balanceResult.isFailure) return false

        val pinResult = BusCardManager.updatePin(pin)
        if (pinResult.isFailure) return false

        // Ghi ảnh vào thẻ nếu có
        if (photoBytes != null) {
            val photoResult = BusCardManager.updatePhotoBytes(photoBytes)
            if (photoResult.isFailure) {
                println("Canh bao: Khong the ghi anh vao the - ${photoResult.exceptionOrNull()?.message}")
                // Không return false - cho phép tiếp tục nếu ảnh lỗi
            }
        }

        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
