package feature.loadcard

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.compose.ui.text.input.TextFieldValue
import core.model.CardType
import core.model.Customer
import core.model.CustomerType
import core.database.DatabaseManager
import smartcard.BusCardManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ViewModel cho LoadCardInfoScreen
 * Quản lý logic nghiệp vụ cho quá trình nạp thông tin vào thẻ
 */
class LoadCardInfoViewModel(
    private val onSuccess: (Customer) -> Unit,
    private val onDismiss: () -> Unit
) {
    
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State Flow
    private val _state = MutableStateFlow(LoadCardInfoState())
    val state: StateFlow<LoadCardInfoState> = _state.asStateFlow()
    
    init {
        // Load danh sách khách hàng khi khởi động
        viewModelScope.launch {
            loadExistingCustomers()
            checkInitialConnection()
        }
    }
    
    /**
     * Load danh sách khách hàng hiện có
     */
    private suspend fun loadExistingCustomers() {
        val customers = withContext(Dispatchers.IO) {
            DatabaseManager.getAllCustomers()
        }
        _state.update { it.copy(existingCustomers = customers) }
    }
    
    /**
     * Kiểm tra trạng thái kết nối ban đầu
     */
    private suspend fun checkInitialConnection() {
        if (BusCardManager.isConnected) {
            _state.update { 
                it.copy(
                    isConnected = true,
                    statusMessage = "✓ Đã kết nối với thẻ"
                )
            }
        }
    }
    
    /**
     * Kết nối với thẻ
     */
    suspend fun connect() {
        _state.update { 
            it.copy(
                isLoading = true,
                statusMessage = "Đang kết nối với thẻ..."
            )
        }
        
        val result = withContext(Dispatchers.IO) { 
            BusCardManager.connect() 
        }
        
        _state.update { it.copy(isLoading = false) }
        
        result.onSuccess {
            _state.update { 
                it.copy(
                    isConnected = true,
                    statusMessage = "✓ Đã kết nối với thẻ"
                )
            }
        }.onFailure { error ->
            _state.update { 
                it.copy(statusMessage = "✗ ${error.message}") 
            }
        }
    }
    
    /**
     * Chuyển sang bước tiếp theo
     */
    fun nextStep() {
        val currentStep = _state.value.currentStep
        val nextStep = when (currentStep) {
            LoadStep.CONNECT -> LoadStep.CHECK_CARD
            LoadStep.CHECK_CARD -> LoadStep.INPUT_INFO
            LoadStep.INPUT_INFO -> LoadStep.WRITE_DATA
            LoadStep.WRITE_DATA -> LoadStep.WRITE_DATA
        }
        _state.update { it.copy(currentStep = nextStep) }
    }
    
    /**
     * Quay lại bước trước
     */
    fun previousStep() {
        val currentStep = _state.value.currentStep
        val prevStep = when (currentStep) {
            LoadStep.CONNECT -> LoadStep.CONNECT
            LoadStep.CHECK_CARD -> LoadStep.CONNECT
            LoadStep.INPUT_INFO -> LoadStep.CHECK_CARD
            LoadStep.WRITE_DATA -> LoadStep.INPUT_INFO
        }
        _state.update { it.copy(currentStep = prevStep) }
    }
    
    /**
     * Kiểm tra thẻ
     */
    suspend fun checkCard() {
        _state.update { 
            it.copy(
                isLoading = true,
                statusMessage = "Đang kiểm tra thẻ..."
            )
        }
        
        val checkResult = withContext(Dispatchers.IO) {
            BusCardManager.checkCardCreated()
        }
        
        _state.update { it.copy(isLoading = false) }
        
        checkResult.onSuccess { hasData ->
            if (hasData) {
                _state.update { 
                    it.copy(
                        statusMessage = "⚠ Thẻ đã có dữ liệu. Vui lòng xóa dữ liệu cũ trước",
                        isCardEmpty = false
                    )
                }
            } else {
                _state.update { 
                    it.copy(
                        statusMessage = "✓ Thẻ rỗng, sẵn sàng nạp dữ liệu",
                        isCardEmpty = true,
                        currentStep = LoadStep.INPUT_INFO
                    )
                }
            }
        }.onFailure { error ->
            _state.update { it.copy(statusMessage = "✗ ${error.message}") }
        }
    }
    
    /**
     * Xóa dữ liệu thẻ
     */
    suspend fun clearCard() {
        _state.update { 
            it.copy(
                isLoading = true,
                statusMessage = "Đang xóa dữ liệu thẻ..."
            )
        }
        
        val result = withContext(Dispatchers.IO) { 
            BusCardManager.clearCard() 
        }
        
        result.onSuccess {
            _state.update { 
                it.copy(
                    statusMessage = "✓ Đã xóa dữ liệu thẻ",
                    isLoading = false
                )
            }
            delay(500) // Delay ngắn để user thấy thông báo thành công
            onDismiss() // Đóng dialog
        }.onFailure { error ->
            _state.update { 
                it.copy(
                    isLoading = false,
                    statusMessage = "✗ ${error.message}"
                )
            }
        }
    }
    
    /**
     * Cập nhật các trường input
     */
    fun updateCardId(value: String) {
        _state.update { it.copy(cardId = value) }
    }
    
    fun updateFullName(value: String) {
        _state.update { it.copy(fullName = value) }
    }
    
    fun updateCccd(value: String) {
        _state.update { it.copy(cccd = value) }
    }
    
    fun updateDob(value: String) {
        _state.update { it.copy(dob = TextFieldValue(value)) }
    }
    
    fun updateAddress(value: String) {
        _state.update { it.copy(address = value) }
    }
    
    fun updatePhone(value: String) {
        _state.update { it.copy(phone = value) }
    }
    
    fun updateCardType(value: CardType) {
        _state.update { it.copy(cardType = value) }
    }
    
    fun updateExpiryDate(value: LocalDate) {
        _state.update { it.copy(expiryDate = value) }
    }
    
    fun updateBalance(value: String) {
        _state.update { it.copy(balance = value) }
    }
    
    fun updatePin(value: String) {
        _state.update { it.copy(pin = value) }
    }
    
    fun updateLinkedCustomerCode(value: String) {
        _state.update { it.copy(linkedCustomerCode = value) }
    }
    
    fun updatePhoto(value: ByteArray?) {
        _state.update { it.copy(photoBytes = value) }
    }
    
    /**
     * Sử dụng dữ liệu khách hàng hiện có
     */
    fun setUseExistingData(use: Boolean) {
        _state.update { 
            it.copy(
                useExistingData = use,
                selectedExistingCustomer = if (!use) null else it.selectedExistingCustomer
            )
        }
    }
    
    /**
     * Chọn khách hàng hiện có và điền dữ liệu
     */
    fun selectExistingCustomer(customer: Customer) {
        _state.update { 
            it.copy(
                selectedExistingCustomer = customer,
                cardId = customer.cardId,
                fullName = customer.fullName,
                cccd = customer.cccd,
                dob = TextFieldValue(customer.dob),
                address = customer.address,
                phone = customer.phone,
                cardType = customer.cardType,
                expiryDate = customer.expiryDate,
                balance = customer.balance.toInt().toString(),
                linkedCustomerCode = customer.linkedCustomerCode,
                photoBytes = customer.photoBytes
            )
        }
    }
    
    /**
     * Validate input và chuyển sang bước tiếp theo
     */
    fun validateAndNext() {
        val state = _state.value
        
        when {
            state.isPhotoTooLarge -> {
                _state.update { 
                    it.copy(statusMessage = "⚠ Ảnh vượt quá 32KB. Vui lòng chọn ảnh nhỏ hơn") 
                }
            }
            validateInput(state) -> {
                _state.update { 
                    it.copy(
                        currentStep = LoadStep.WRITE_DATA,
                        statusMessage = "Sẵn sàng ghi dữ liệu lên thẻ"
                    )
                }
            }
            else -> {
                _state.update { 
                    it.copy(statusMessage = "⚠ Vui lòng điền đầy đủ thông tin bắt buộc") 
                }
            }
        }
    }
    
    /**
     * Validate input
     */
    private fun validateInput(state: LoadCardInfoState): Boolean {
        val cccd = state.cccd
        val phone = state.phone
        val dob = state.dob.text
        
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
        
        return state.cardId.isNotBlank() && 
               state.fullName.isNotBlank() && 
               isValidCccd && 
               isValidDob && 
               isValidPhone && 
               state.pin.length in 4..6
    }
    
    /**
     * Ghi dữ liệu lên thẻ
     */
    suspend fun writeDataToCard() {
        val state = _state.value
        
        _state.update { 
            it.copy(
                isLoading = true,
                statusMessage = "Đang ghi dữ liệu lên thẻ..."
            )
        }
        
        val writeSuccess = withContext(Dispatchers.IO) {
            writeDataToCardInternal(
                cardId = state.cardId,
                fullName = state.fullName,
                customerType = state.customerType,
                cardType = state.cardType,
                expiryDate = state.expiryDate,
                balance = state.balance.toDoubleOrNull() ?: 0.0,
                pin = state.pin,
                linkedCustomerCode = state.linkedCustomerCode,
                photoBytes = state.photoBytes
            )
        }
        
        if (writeSuccess) {
            val newCustomer = Customer(
                id = state.cardId,
                cardId = state.cardId,
                fullName = state.fullName,
                cccd = state.cccd,
                dob = state.dob.text,
                address = state.address,
                phone = state.phone,
                customerType = state.customerType,
                cardType = state.cardType,
                expiryDate = state.expiryDate,
                balance = state.balance.toDoubleOrNull() ?: 0.0,
                linkedCustomerCode = state.linkedCustomerCode,
                photoBytes = state.photoBytes
            )
            
            _state.update { 
                it.copy(statusMessage = "💾 Đang lưu vào database...")
            }
            
            val existing = withContext(Dispatchers.IO) {
                DatabaseManager.getCustomerByCardId(state.cardId)
            }
            
            val insertSuccess = if (existing == null) {
                withContext(Dispatchers.IO) {
                    DatabaseManager.insertCustomer(newCustomer, state.pin)
                }
            } else {
                false
            }
            
            if (insertSuccess) {
                withContext(Dispatchers.IO) {
                    DatabaseManager.insertTransaction(
                        cardId = state.cardId,
                        transactionType = "INITIAL_TOP_UP",
                        amount = state.balance.toDoubleOrNull() ?: 0.0,
                        balanceBefore = 0.0,
                        balanceAfter = state.balance.toDoubleOrNull() ?: 0.0,
                        description = "Nạp tiền ban đầu khi khởi tạo thẻ"
                    )
                }
                _state.update { 
                    it.copy(statusMessage = "✅ Ghi dữ liệu và lưu database thành công!")
                }
            } else if (existing != null) {
                _state.update { 
                    it.copy(statusMessage = "✅ Ghi thẻ thành công! (Card ID đã có trong database)")
                }
            } else {
                _state.update { 
                    it.copy(statusMessage = "⚠️ Ghi thẻ thành công nhưng lỗi lưu database")
                }
            }
            
            _state.update { it.copy(isLoading = false) }
            onSuccess(newCustomer)
            onDismiss()
        } else {
            _state.update { 
                it.copy(
                    isLoading = false,
                    statusMessage = "✗ Ghi dữ liệu thất bại"
                )
            }
        }
    }
    
    /**
     * Ghi dữ liệu lên thẻ (internal)
     */
    private suspend fun writeDataToCardInternal(
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
    
    /**
     * Cleanup
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}

