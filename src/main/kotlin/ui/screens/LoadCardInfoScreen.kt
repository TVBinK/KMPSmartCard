package ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import androidx.compose.material.Button
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import database.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import models.CardType
import models.Customer
import models.CustomerType
import smartcard.BusCardManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
    var customerType by remember { mutableStateOf(CustomerType.NORMAL) }
    var cardType by remember { mutableStateOf(CardType.SINGLE_TRIP) }
    var expiryDate by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var balance by remember { mutableStateOf("100000") }
    var pin by remember { mutableStateOf("") }
    var linkedCustomerCode by remember { mutableStateOf("") }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }

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
                                    println("🗑️ Bắt đầu xóa dữ liệu thẻ...")
                                    
                                    val result = withContext(Dispatchers.IO) { BusCardManager.clearCard() }
                                    
                                    println("🗑️ Kết quả xóa: ${if (result.isSuccess) "Thành công" else "Thất bại - ${result.exceptionOrNull()?.message}"}")
                                    
                                    result.onSuccess {
                                        println("✅ Xóa thẻ thành công, đóng dialog")
                                        statusMessage = "✓ Đã xóa dữ liệu thẻ"
                                        isLoading = false
                                        delay(500) // Delay ngắn để user thấy thông báo thành công
                                        onDismiss() // Đóng dialog
                                    }.onFailure { error ->
                                        isLoading = false
                                        statusMessage = "✗ ${error.message}"
                                        println("❌ Lỗi xóa thẻ: ${error.message}")
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
                                fullName = customer.fullName
                                customerType = customer.customerType
                                cardType = customer.cardType
                                expiryDate = customer.expiryDate
                                balance = customer.balance.toInt().toString()
                                linkedCustomerCode = customer.linkedCustomerCode
                            },
                            cardId = cardId,
                            onCardIdChange = { cardId = it },
                            fullName = fullName,
                            onFullNameChange = { fullName = it },
                            customerType = customerType,
                            onCustomerTypeChange = { customerType = it },
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
                            onNext = {
                                if (validateInput(cardId, fullName, pin)) {
                                    currentStep = LoadStep.WRITE_DATA
                                    statusMessage = "Sẵn sàng ghi dữ liệu lên thẻ"
                                } else {
                                    statusMessage = "⚠ Vui lòng điền đầy đủ thông tin bắt buộc"
                                }
                            }
                        )

                        LoadStep.WRITE_DATA -> WriteDataStepContent(
                            cardId = cardId,
                            fullName = fullName,
                            customerType = customerType,
                            cardType = cardType,
                            balance = balance,
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
                                            fullName = fullName,
                                            customerType = customerType,
                                            cardType = cardType,
                                            expiryDate = expiryDate,
                                            balance = balance.toDoubleOrNull() ?: 0.0,
                                            cardId = cardId,
                                            linkedCustomerCode = linkedCustomerCode,
                                            photoBytes = photoBytes
                                        )

                                        statusMessage = "💾 Đang lưu vào database..."
                                        val existing = withContext(Dispatchers.IO) {
                                            DatabaseManager.getCustomerByCardId(cardId)
                                        }

                                        val insertSuccess = if (existing == null) {
                                            withContext(Dispatchers.IO) {
                                                DatabaseManager.insertCustomer(newCustomer)
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
                                        delay(1200)
                                        onSuccess(newCustomer)
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

@Composable
private fun ConnectStepContent(
    isConnected: Boolean,
    onConnect: () -> Unit,
    onNext: () -> Unit
) {
    var isConnecting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Chưa kết nối") }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Bước 1: Kết nối Simulator",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3)
        )

                // Status Card - Chỉ hiển thị khi chưa kết nối
        if (!isConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFFFFF3E0),
                elevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Simulator Card Reader",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = statusMessage,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Connect Button
        if (!isConnected) {
            Button(
                onClick = {
                    isConnecting = true
                    statusMessage = "Đang kết nối..."
                    onConnect()
                    // Note: Status will be updated by parent
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3)),
                enabled = !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đang kết nối...", color = Color.White, fontSize = 14.sp)
                } else {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kết nối Simulator", color = Color.White, fontSize = 14.sp)
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF4CAF50),
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Kết nối thành công!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Có thể tiếp tục bước kiểm tra thẻ",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Nút Tiếp tục
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
            ) {
                Text("Tiếp tục", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
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
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Hướng dẫn sử dụng",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "1. Chạy JCardSimServer (cổng 9025)",
                    fontSize = 12.sp,
                    color = Color(0xFF424242)
                )
                Text(
                    "2. Nhấn 'Kết nối Simulator'",
                    fontSize = 12.sp,
                    color = Color(0xFF424242)
                )
                Text(
                    "3. Sau khi kết nối thành công, nhấn 'Tiếp tục'",
                    fontSize = 12.sp,
                    color = Color(0xFF424242)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "💡 Lưu ý: Simulator phải đang chạy trước khi kết nối",
                    fontSize = 11.sp,
                    color = Color(0xFF666666),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
    
    // Update status message based on connection state
    LaunchedEffect(isConnected) {
        if (isConnected) {
            statusMessage = "✓ Đã kết nối với card reader"
            isConnecting = false
        }
    }
}

@Composable
private fun CheckCardStepContent(
    onCheck: () -> Unit,
    onClearCard: () -> Unit,
    isCardEmpty: Boolean
) {
    var hasChecked by remember { mutableStateOf(false) }
    var showHasData by remember { mutableStateOf(false) }
    
    // Theo dõi khi isCardEmpty thay đổi (sau khi xóa thành công)
    LaunchedEffect(isCardEmpty) {
        if (isCardEmpty) {
            showHasData = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Bước 2: Kiểm tra trạng thái thẻ",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3)
        )
        Text(
            text = "Thẻ phải rỗng để nạp dữ liệu mới. Nếu thẻ có dữ liệu, cần xóa trước.",
            fontSize = 13.sp,
            color = Color.Gray
        )

        // Nút Kiểm tra thẻ
        Button(
            onClick = {
                hasChecked = true
                showHasData = !isCardEmpty
                onCheck()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3))
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Kiểm tra thẻ", color = Color.White)
        }

        // Hiển thị cảnh báo và nút xóa nếu thẻ có dữ liệu
        if (hasChecked && showHasData) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFFFFF3E0),
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Thẻ đã có dữ liệu!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                    
                    Text(
                        "Nhấn nút bên dưới để xóa toàn bộ dữ liệu trên thẻ. Sau khi xóa, dialog sẽ tự động đóng.",
                        fontSize = 13.sp,
                        color = Color(0xFF666666)
                    )
                    
                    // Nút Xóa dữ liệu thẻ - Chỉ hiển thị khi thẻ có dữ liệu
                    Button(
                        onClick = {
                            println("👆 User nhấn nút 'Xóa dữ liệu thẻ'")
                            onClearCard()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF5252))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Xóa dữ liệu thẻ", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Hiển thị thông báo nếu thẻ rỗng
        if (hasChecked && !showHasData) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFFE8F5E9),
                elevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "✓ Thẻ rỗng, sẵn sàng nạp dữ liệu mới!",
                        fontSize = 13.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun InputInfoStepContent(
    existingCustomers: List<Customer>,
    useExistingData: Boolean,
    onUseExistingDataChange: (Boolean) -> Unit,
    selectedExistingCustomer: Customer?,
    onSelectExistingCustomer: (Customer) -> Unit,
    cardId: String,
    onCardIdChange: (String) -> Unit,
    fullName: String,
    onFullNameChange: (String) -> Unit,
    customerType: CustomerType,
    onCustomerTypeChange: (CustomerType) -> Unit,
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
    onNext: () -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
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
                    Card(backgroundColor = Color(0xFFF1F8E9)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF558B2F),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Đã chọn: ${selectedExistingCustomer.fullName}",
                                    fontSize = 13.sp,
                                    color = Color(0xFF558B2F),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                "• Thông tin khách hàng đã được điền tự động",
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

            val editable = !useExistingData || selectedExistingCustomer == null

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
                    singleLine = true
                )

                OutlinedTextField(
                    value = fullName,
                    onValueChange = onFullNameChange,
                    label = { Text("Họ tên *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = editable,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        disabledTextColor = Color.Black,
                        disabledLabelColor = Color.Gray
                    )
                )
            }

            // Row 2: Loại đối tượng + Loại thẻ
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                var customerTypeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = customerTypeExpanded,
                    onExpandedChange = { if (editable) customerTypeExpanded = !customerTypeExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = customerType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Loại đối tượng") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(customerTypeExpanded) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editable
                    )
                    ExposedDropdownMenu(
                        expanded = customerTypeExpanded,
                        onDismissRequest = { customerTypeExpanded = false }
                    ) {
                        CustomerType.values().forEach { type ->
                            DropdownMenuItem(onClick = {
                                onCustomerTypeChange(type)
                                customerTypeExpanded = false
                            }) {
                                Text(type.displayName)
                            }
                        }
                    }
                }

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
                    modifier = Modifier.weight(1f),
                    singleLine = true
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
                                val image = ImageIO.read(photoBytes.inputStream())
                                org.jetbrains.skia.Image.makeFromEncoded(photoBytes).asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        
                        if (imageBitmap != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = "Photo",
                                    modifier = Modifier
                                        .size(80.dp)
                                        .border(2.dp, Color(0xFF4CAF50), RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "✅ Đã chọn ảnh",
                                        fontSize = 13.sp,
                                        color = Color(0xFF4CAF50),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "${photoBytes.size / 1024} KB",
                                        fontSize = 11.sp,
                                        color = Color.Gray
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
                            Text(
                                "Chưa chọn ảnh",
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
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
                                        // Resize image to max 200x200 for better quality (protocol supports up to 65KB)
                                        val targetSize = 200
                                        val resized = java.awt.image.BufferedImage(targetSize, targetSize, java.awt.image.BufferedImage.TYPE_INT_RGB)
                                        val graphics = resized.createGraphics()
                                        graphics.setRenderingHint(
                                            java.awt.RenderingHints.KEY_INTERPOLATION,
                                            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR
                                        )
                                        graphics.setRenderingHint(
                                            java.awt.RenderingHints.KEY_RENDERING,
                                            java.awt.RenderingHints.VALUE_RENDER_QUALITY
                                        )
                                        graphics.setRenderingHint(
                                            java.awt.RenderingHints.KEY_ANTIALIASING,
                                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON
                                        )
                                        graphics.drawImage(image, 0, 0, targetSize, targetSize, null)
                                        graphics.dispose()
                                        
                                        val baos = ByteArrayOutputStream()
                                        // Use good quality JPG (75%)
                                        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
                                        val param = writer.defaultWriteParam
                                        param.compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT
                                        param.compressionQuality = 0.75f // 75% quality (better than before)
                                        
                                        val ios = javax.imageio.stream.MemoryCacheImageOutputStream(baos)
                                        writer.output = ios
                                        writer.write(null, javax.imageio.IIOImage(resized, null, null), param)
                                        ios.close()
                                        writer.dispose()
                                        
                                        val bytes = baos.toByteArray()
                                        println("📸 Ảnh đã resize: ${bytes.size} bytes (${targetSize}x${targetSize}, quality 75%)")
                                        
                                        // Cảnh báo nếu ảnh quá lớn (> 50KB có thể chậm)
                                        if (bytes.size > 51200) {
                                            println("⚠️ Cảnh báo: Ảnh có kích thước ${bytes.size} bytes (> 50KB), có thể chậm khi ghi vào thẻ")
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

            Text("* Trường bắt buộc", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun WriteDataStepContent(
    cardId: String,
    fullName: String,
    customerType: CustomerType,
    cardType: CardType,
    balance: String,
    onWrite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Bước 4: Xác nhận và ghi dữ liệu",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3)
        )
        Text("Kiểm tra lại thông tin trước khi ghi lên thẻ:", fontSize = 13.sp, color = Color.Gray)

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Mã thẻ", cardId)
                Divider()
                InfoRow("Họ tên", fullName)
                InfoRow("Loại đối tượng", customerType.displayName)
                InfoRow("Loại thẻ", cardType.displayName)
                InfoRow("Số dư", String.format("%,d VNĐ", balance.toLongOrNull() ?: 0))
            }
        }

        Card(backgroundColor = Color(0xFFFFF3E0)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lưu ý", fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Sau khi ghi dữ liệu, muốn thay đổi cần xóa và nạp lại.",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
            }
        }

        Button(
            onClick = onWrite,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ghi dữ liệu lên thẻ", color = Color.White)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

private fun validateInput(cardId: String, fullName: String, pin: String): Boolean {
    return cardId.isNotBlank() && fullName.isNotBlank() && pin.length in 4..6
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
                println("⚠️ Cảnh báo: Không thể ghi ảnh vào thẻ - ${photoResult.exceptionOrNull()?.message}")
                // Không return false - cho phép tiếp tục nếu ảnh lỗi
            }
        }

        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
