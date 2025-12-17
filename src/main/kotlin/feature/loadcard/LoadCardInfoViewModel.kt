package feature.loadcard

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import androidx.compose.ui.text.input.TextFieldValue
import core.model.CardType
import core.model.Customer
import core.database.DatabaseManager
import smartcard.BusCardManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.io.File
import javax.imageio.ImageIO
import com.buscardmanagement.client.util.HelpMethod

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
    private fun checkInitialConnection() {
        if (BusCardManager.isConnected) {
            _state.update {
                it.copy(
                    isConnected = true,
                    statusMessage = " Đã kết nối với thẻ"
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
                    statusMessage = "Đã kết nối với thẻ"
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
                // Thẻ rỗng → Sinh Card ID tự động theo quy tắc
                val latestId = withContext(Dispatchers.IO) {
                    DatabaseManager.getLatestCitizenId()
                }
                val newCardId = generateCardId(latestId)
                
                _state.update {
                    it.copy(
                        statusMessage = "✓ Thẻ rỗng, sẵn sàng nạp dữ liệu",
                        isCardEmpty = true,
                        currentStep = LoadStep.INPUT_INFO,
                        cardId = newCardId
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

    fun updatePhoto(value: ByteArray?) {
        _state.update { it.copy(photoBytes = value) }
    }

    /**
     * Xử lý file ảnh đã chọn từ Desktop
     * Đọc file, chuyển đổi sang BufferedImage rồi sang ByteArray bằng HelpMethod
     */
    fun processImageFile(file: File) {
        try {
            val image = ImageIO.read(file)
            if (image == null) {
                _state.update {
                    it.copy(statusMessage = "✗ Không thể đọc file ảnh")
                }
                return
            }

            // Chuyển đổi + resize nếu cần để đảm bảo <= MAX_PHOTO_SIZE_BYTES
            val processedBytes = HelpMethod.resizeImageToMaxSize(
                image,
                MAX_PHOTO_SIZE_BYTES,
                400,   // maxWidth
                400    // maxHeight
            )
                ?: run {
                    _state.update {
                        it.copy(statusMessage = "✗ Không thể xử lý ảnh")
                    }
                    return
                }

            println("Anh da xu ly: ${processedBytes.size} bytes (${image.width}x${image.height})")

            _state.update {
                it.copy(
                    photoBytes = processedBytes,
                    statusMessage = "✓ Đã xử lý ảnh (${processedBytes.size} bytes, giới hạn ${MAX_PHOTO_SIZE_BYTES} bytes)"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _state.update {
                it.copy(statusMessage = "✗ Lỗi xử lý ảnh: ${e.message}")
            }
        }
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
        val fullName = state.fullName.trim()
        val cccd = state.cccd.trim()
        val phone = state.phone.trim()
        val dob = state.dob.text.trim()
        val address = state.address.trim()
        val balanceStr = state.balance.trim()
        val pin = state.pin.trim()

        // Card ID: được sinh tự động, chỉ cần khác rỗng
        val isValidCardId = state.cardId.isNotBlank()

        // Họ tên: không rỗng, tối thiểu 3 ký tự
        val isValidFullName = fullName.length >= 3

        // CCCD: đúng 12 chữ số
        val isValidCccd = cccd.length == 12 && cccd.all { it.isDigit() }

        // SĐT: đúng 10 chữ số
        val isValidPhone = phone.length == 10 && phone.all { it.isDigit() }

        // DOB: định dạng dd/MM/yyyy và < ngày hiện tại
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

        // Địa chỉ: không rỗng
        val isValidAddress = address.isNotEmpty()

        // Số dư: là số >= 0
        val balanceValue = balanceStr.toDoubleOrNull()
        val isValidBalance = balanceValue != null && balanceValue >= 0.0

        // PIN: 4-6 ký tự số
        val isValidPin = pin.length in 4..6 && pin.all { it.isDigit() }

        return isValidCardId &&
                isValidFullName &&
                isValidCccd &&
                isValidDob &&
                isValidPhone &&
                isValidAddress &&
                isValidBalance &&
                isValidPin
    }
    
    /**
     * Sinh Card ID mới theo quy tắc:
     *  - prefix = ngày hiện tại dạng ddMMyy
     *  - suffix = 6 số cuối tăng dần từ latestId
     */
    private fun generateCardId(latestId: String?): String {
        val dateFormat = SimpleDateFormat("ddMMyy")
        val prefix = dateFormat.format(Date())

        val nextSuffix = if (latestId.isNullOrBlank() || latestId.length < 6) {
            1
        } else {
            val last6 = latestId.takeLast(6)
            val current = last6.toIntOrNull() ?: 0
            current + 1
        }

        val suffixStr = String.format("%06d", nextSuffix)
        return prefix + suffixStr
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
                cardType = state.cardType,
                expiryDate = state.expiryDate,
                balance = state.balance.toDoubleOrNull() ?: 0.0,
                pin = state.pin,
                cccd = state.cccd,
                dob = state.dob.text,
                address = state.address,
                phone = state.phone,
                photoBytes = state.photoBytes
            )
        }

        if (writeSuccess) {
            // Lấy public key RSA do thẻ sinh ra để lưu vào DB (dạng HEX)
            val publicKeyHex = withContext(Dispatchers.IO) {
                try {
                    val pkResult = BusCardManager.getPublicKey()
                    if (pkResult.isSuccess) {
                        pkResult.getOrNull()
                            ?.joinToString(separator = "") { "%02X".format(it) }
                            ?: ""
                    } else {
                        ""
                    }
                } catch (e: Exception) {
                    ""
                }
            }

            val newCustomer = Customer(
                id = state.cardId,
                cardId = state.cardId,
                fullName = state.fullName,
                cccd = state.cccd,
                dob = state.dob.text,
                address = state.address,
                phone = state.phone,
                cardType = state.cardType,
                expiryDate = state.expiryDate,
                balance = state.balance.toDoubleOrNull() ?: 0.0,
                photoBytes = state.photoBytes,
                publicKey = publicKeyHex
            )

            _state.update {
                it.copy(statusMessage = "💾 Đang lưu vào database...")
            }

            val existing = withContext(Dispatchers.IO) {
                DatabaseManager.getCustomerByCardId(state.cardId)
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
    private fun writeDataToCardInternal(
        cardId: String,
        fullName: String,
        cardType: CardType,
        expiryDate: LocalDate,
        balance: Double,
        pin: String,
        cccd: String = "",
        dob: String = "",
        address: String = "",
        phone: String = "",
        photoBytes: ByteArray? = null
    ): Boolean {
        return try {
            // QUAN TRỌNG: Clear card trước khi nạp dữ liệu mới
            // Đảm bảo thẻ ở trạng thái sạch trước khi tạo PIN và nạp dữ liệu
            println("Dang xoa du lieu cu tren the (neu co)...")
            val clearResult = BusCardManager.clearCard()
            if (clearResult.isFailure) {
                val errorMsg = clearResult.exceptionOrNull()?.message ?: "Unknown error"
                println("Canh bao: Khong the xoa du lieu cu tren the: $errorMsg")
                // Vẫn tiếp tục vì có thể thẻ đã rỗng, nhưng sẽ thử clear lại nếu cần
            } else {
                println("Da xoa du lieu cu tren the thanh cong")
            }

            // Đợi một chút để đảm bảo clear card hoàn tất
            Thread.sleep(200)

            // Kiểm tra lại xem thẻ đã được clear chưa
            val checkResult = BusCardManager.checkCardCreated()
            if (checkResult.isSuccess && checkResult.getOrNull() == true) {
                println("Canh bao: The van con du lieu sau khi clear. Thu clear lai...")
                val retryClear = BusCardManager.clearCard()
                if (retryClear.isSuccess) {
                    Thread.sleep(200)
                }
            }

            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val expiryString = expiryDate.format(formatter)

            // QUAN TRỌNG: Set PIN trước tiên để tạo AES key
            // Sau đó mới có thể mã hóa và lưu các thông tin khác
            // Lưu ý: Nếu vẫn gặp lỗi 6A88, có thể do applet yêu cầu thẻ phải được initialized trước
            // Trong trường hợp đó, cần sửa applet để cho phép UPDATE_PIN khi chưa initialized
            val pinResult = BusCardManager.updatePin("", pin) // Tạo PIN lần đầu, không cần PIN cũ
            if (pinResult.isFailure) {
                val errorMsg = pinResult.exceptionOrNull()?.message ?: "Unknown error"
                println("Loi: Khong the thiet lap PIN: $errorMsg")
                println("Giai phap: Can dam bao the da duoc clear hoan toan. " +
                        "Neu van gap loi, co the can sua applet de cho phep UPDATE_PIN khi chua initialized.")
                return false
            }

            // Verify PIN sau khi set để đảm bảo trạng thái validated được thiết lập
            val verifyPinResult = BusCardManager.checkPin(pin)
            if (verifyPinResult.isFailure) {
                println("Canh bao: Khong the verify PIN sau khi set: ${verifyPinResult.exceptionOrNull()?.message}")
                // Vẫn tiếp tục vì có thể applet tự động validate khi set PIN lần đầu
            }

            // Sau khi có PIN và đã verify -> Có AES Key -> Có thể mã hóa và lưu thông tin
            val infoResult = BusCardManager.updateCustomerInfo(
                fullName = fullName,
                customerType = "Khách hàng",
                expiryDate = expiryString,
                cardType = cardType.displayName,
                linkedCustomerId = "",
                cccd = cccd,
                dob = dob,
                address = address,
                phone = phone,
                pin = pin  // Sử dụng PIN vừa set để verify trước khi cập nhật
            )
            if (infoResult.isFailure) {
                println("Loi: Khong the cap nhat thong tin khach hang: ${infoResult.exceptionOrNull()?.message}")
                return false
            }

            val cardIdResult = BusCardManager.updateCardId(cardId)
            if (cardIdResult.isFailure) {
                println("Loi: Khong the cap nhat Card ID")
                return false
            }

            val balanceResult = BusCardManager.updateBalance(balance, pin) // Sử dụng PIN để verify
            if (balanceResult.isFailure) {
                println("Loi: Khong the cap nhat so du: ${balanceResult.exceptionOrNull()?.message}")
                return false
            }

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

