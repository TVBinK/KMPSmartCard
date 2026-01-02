package feature.readcard

import core.model.Customer
import core.model.CardType
import core.database.DatabaseManager
import smartcard.BusCardManager
import smartcard.CustomerInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ViewModel cho SmartCardManagementScreen
 * Quản lý logic nghiệp vụ cho quản lý smart card
 */
class ReadSmartCardViewModel {
    
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State Flow
    private val _state = MutableStateFlow(ReadSmartCardState())
    val state: StateFlow<ReadSmartCardState> = _state.asStateFlow()
    
    /**
     * Refresh lock status (public)
     */
    fun refreshLockStatus() {
        val isBlocked = BusCardManager.isCardBlocked
        _state.update { it.copy(isCardBlocked = isBlocked) }
    }
    
    private fun setActionMessage(message: String) {
        _state.update { it.copy(actionMessage = message) }
    }
    
    init {
        // Khi khởi động: chỉ kiểm tra kết nối + trạng thái khóa
        // Việc đọc thẻ sẽ được thực hiện khi dialog mở (onDialogOpened)
        viewModelScope.launch {
            checkConnectionStatusInternal()
            refreshLockStatus()
        }
    }
    
    /**
     * Được gọi khi dialog mở - tự động đọc thẻ nếu đã kết nối
     */
    fun onDialogOpened() {
        viewModelScope.launch {
            checkConnectionStatusInternal()
            refreshLockStatus()
            // Tự động đọc thẻ nếu đã kết nối
            if (_state.value.isConnected) {
                autoReadCardInternal()
            }
        }
    }
    
    /**
     * Kiểm tra trạng thái kết nối (internal)
     */
    private fun checkConnectionStatusInternal() {
        val isConnected = BusCardManager.isConnected
        val status = if (isConnected) {
            "✓ Đã kết nối với card reader"
        } else {
            "Chưa kết nối"
        }
        
        _state.update { 
            it.copy(
                isConnected = isConnected,
                cardReaderStatus = status
            )
        }
    }
    
    /**
     * Tự động đọc thẻ khi dialog mở và đã kết nối (internal)
     */
    private suspend fun autoReadCardInternal() {
        _state.update { it.copy(isReadingCard = true) }
        
        try {
            val infoResult = withContext(Dispatchers.IO) {
                BusCardManager.getCustomerInfo()
            }
            val cardIdResult = withContext(Dispatchers.IO) {
                BusCardManager.getCardId()
            }
            val balanceResult = withContext(Dispatchers.IO) {
                BusCardManager.getBalance()
            }
            val pictureResult = withContext(Dispatchers.IO) {
                println("[ReadSmartCard] Bắt đầu đọc ảnh từ thẻ (tự động)...")
                BusCardManager.getPicture()
            }
            
            // Log kết quả đọc ảnh
            pictureResult.onSuccess { photoBytes ->
                println("[ReadSmartCard] ✓ Đọc ảnh thành công (tự động): ${photoBytes.size} bytes")
            }.onFailure { error ->
                println("[ReadSmartCard] ✗ Không thể đọc ảnh từ thẻ (tự động): ${error.message}")
            }
            
            infoResult.onSuccess { info ->
                cardIdResult.onSuccess { cardId ->
                    balanceResult.onSuccess { balance ->
                        // Đọc ảnh (không bắt buộc, có thể không có ảnh)
                        val photoBytes = pictureResult.getOrNull()
                        
                        if (photoBytes != null) {
                            println("[ReadSmartCard] Ảnh đã được load (tự động): ${photoBytes.size} bytes, sẽ map vào customer")
                        } else {
                            println("[ReadSmartCard] Không có ảnh trên thẻ hoặc không thể đọc ảnh (tự động)")
                        }
                        
                        // Map trực tiếp dữ liệu từ thẻ sang model Customer cho UI,
                        // không còn phụ thuộc vào dữ liệu khách hàng trong DB
                        val customerFromCard = mapCardDataToCustomer(info, cardId, balance, photoBytes)

                        // Cập nhật khách hàng đang chọn + load lịch sử giao dịch (nếu còn dùng DB cho transaction)
                        _state.update { it.copy(selectedCustomer = customerFromCard) }
                        viewModelScope.launch {
                            loadTransactions(cardId)
                        }

                        println("[ReadSmartCard] Tu dong doc the thanh cong tu the: $cardId - ${customerFromCard.fullName}, ảnh: ${if (customerFromCard.photoBytes != null) "${customerFromCard.photoBytes!!.size} bytes" else "null"}")
                        setActionMessage("")
                    }.onFailure { error ->
                        println("Loi doc so du: ${error.message}")
                        setActionMessage(error.message ?: "Không thể đọc số dư từ thẻ")
                    }
                }.onFailure { error ->
                    println("Loi doc cardId: ${error.message}")
                    setActionMessage(error.message ?: "Không thể đọc Card ID từ thẻ")
                }
            }.onFailure { error ->
                println("Loi tu dong doc the: ${error.message}")
                setActionMessage(error.message ?: "Không thể đọc thông tin thẻ")
            }
        } finally {
            _state.update { it.copy(isReadingCard = false) }
            refreshLockStatus()
        }
    }
    
    /**
     * Đọc thẻ thủ công
     */
    suspend fun readCard() {
        _state.update { it.copy(isReadingCard = true) }
        
        try {
            val infoResult = withContext(Dispatchers.IO) {
                BusCardManager.getCustomerInfo()
            }
            val cardIdResult = withContext(Dispatchers.IO) {
                BusCardManager.getCardId()
            }
            val balanceResult = withContext(Dispatchers.IO) {
                BusCardManager.getBalance()
            }
            val pictureResult = withContext(Dispatchers.IO) {
                println("[ReadSmartCard] Bắt đầu đọc ảnh từ thẻ (thủ công)...")
                BusCardManager.getPicture()
            }
            
            // Log kết quả đọc ảnh
            pictureResult.onSuccess { photoBytes ->
                println("[ReadSmartCard] ✓ Đọc ảnh thành công (thủ công): ${photoBytes.size} bytes")
            }.onFailure { error ->
                println("[ReadSmartCard] ✗ Không thể đọc ảnh từ thẻ (thủ công): ${error.message}")
            }
            
            infoResult.onSuccess { info ->
                cardIdResult.onSuccess { cardId ->
                    balanceResult.onSuccess { balance ->
                        // Đọc ảnh (không bắt buộc, có thể không có ảnh)
                        val photoBytes = pictureResult.getOrNull()
                        
                        if (photoBytes != null) {
                            println("[ReadSmartCard] Ảnh đã được load (thủ công): ${photoBytes.size} bytes, sẽ map vào customer")
                        } else {
                            println("[ReadSmartCard] Không có ảnh trên thẻ hoặc không thể đọc ảnh (thủ công)")
                        }
                        
                        // Map trực tiếp dữ liệu từ thẻ sang model Customer cho UI,
                        // không còn phụ thuộc vào dữ liệu khách hàng trong DB
                        val customerFromCard = mapCardDataToCustomer(info, cardId, balance, photoBytes)

                        // Cập nhật khách hàng đang chọn + load lịch sử giao dịch (nếu còn dùng DB cho transaction)
                        _state.update { it.copy(selectedCustomer = customerFromCard) }
                        viewModelScope.launch {
                            loadTransactions(cardId)
                        }

                        println("[ReadSmartCard] Doc the thanh cong tu the: $cardId - ${customerFromCard.fullName}, ảnh: ${if (customerFromCard.photoBytes != null) "${customerFromCard.photoBytes!!.size} bytes" else "null"}")
                        setActionMessage("")
                    }.onFailure { error ->
                        println("Loi doc so du: ${error.message}")
                        setActionMessage(error.message ?: "Không thể đọc số dư từ thẻ")
                    }
                }.onFailure { error ->
                    println("Loi doc cardId: ${error.message}")
                    setActionMessage(error.message ?: "Không thể đọc Card ID từ thẻ")
                }
            }.onFailure { error ->
                println("Loi doc the: ${error.message}")
                setActionMessage(error.message ?: "Không thể đọc thông tin thẻ")
            }
        } finally {
            _state.update { it.copy(isReadingCard = false) }
            refreshLockStatus()
        }
    }
    
    /**
     * Chọn customer và load transactions
     */
    fun selectCustomer(customer: Customer) {
        viewModelScope.launch {
            _state.update { it.copy(selectedCustomer = customer) }
            loadTransactions(customer.cardId)
        }
    }
    
    /**
     * Load transactions cho customer
     */
    private suspend fun loadTransactions(cardId: String) {
        val transactions = withContext(Dispatchers.IO) {
            DatabaseManager.getTransactionsByCardId(cardId)
        }
        _state.update { it.copy(transactions = transactions) }
    }
    
    /**
     * Chuyển tab
     */
    fun selectTab(tabIndex: Int) {
        _state.update { it.copy(selectedTab = tabIndex) }
    }
    
    /**
     * Hiển thị Change PIN dialog
     */
    fun showChangePinDialog() {
        _state.update { it.copy(showChangePinDialog = true) }
    }
    
    /**
     * Ẩn Change PIN dialog
     */
    fun dismissChangePinDialog() {
        _state.update { it.copy(showChangePinDialog = false) }
    }
    
    /**
     * Ghi lại dữ liệu lên thẻ sau khi đổi PIN thành công
     * Vì applet đã xóa dữ liệu khi đổi PIN, cần ghi lại từ customer hiện tại
     */
    fun rewriteDataAfterPinChange(newPin: String) {
        val customer = _state.value.selectedCustomer ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                println("[RewriteData] Bat dau ghi lai du lieu sau khi doi PIN...")
                
                // Verify PIN mới để set trạng thái validated
                // Bỏ qua RSA verification vì đã verify khi đổi PIN rồi
                val verifyResult = BusCardManager.checkPin(newPin, skipRsaCheck = true)
                if (verifyResult.isFailure) {
                    println("[RewriteData] Loi verify PIN moi: ${verifyResult.exceptionOrNull()?.message}")
                    withContext(Dispatchers.Main) {
                        _state.update { it.copy(actionMessage = "Lỗi verify PIN mới") }
                    }
                    return@launch
                }
                
                // Ghi lại thông tin khách hàng
                val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                val infoResult = BusCardManager.updateCustomerInfo(
                    fullName = customer.fullName,
                    customerType = "Khách hàng",
                    expiryDate = customer.expiryDate.format(formatter),
                    cardType = customer.cardType.displayName,
                    linkedCustomerId = "",
                    cccd = customer.cccd,
                    dob = customer.dob,
                    address = customer.address,
                    phone = customer.phone,
                    pin = newPin
                )
                
                if (infoResult.isFailure) {
                    println("[RewriteData] Loi ghi thong tin KH: ${infoResult.exceptionOrNull()?.message}")
                } else {
                    println("[RewriteData] Da ghi lai thong tin KH")
                }
                
                // Ghi lại Card ID
                val cardIdResult = BusCardManager.updateCardId(customer.cardId)
                if (cardIdResult.isFailure) {
                    println("[RewriteData] Loi ghi Card ID: ${cardIdResult.exceptionOrNull()?.message}")
                } else {
                    println("[RewriteData] Da ghi lai Card ID: ${customer.cardId}")
                }
                
                // Ghi lại số dư
                val balanceResult = BusCardManager.updateBalance(customer.balance, newPin)
                if (balanceResult.isFailure) {
                    println("[RewriteData] Loi ghi so du: ${balanceResult.exceptionOrNull()?.message}")
                } else {
                    println("[RewriteData] Da ghi lai so du: ${customer.balance}")
                }
                
                // Ghi lại ảnh (nếu có)
                if (customer.photoBytes != null && customer.photoBytes.isNotEmpty()) {
                    val photoResult = BusCardManager.updatePhotoBytes(customer.photoBytes)
                    if (photoResult.isFailure) {
                        println("[RewriteData] Loi ghi anh: ${photoResult.exceptionOrNull()?.message}")
                    } else {
                        println("[RewriteData] Da ghi lai anh (${customer.photoBytes.size} bytes)")
                    }
                }
                
                println("[RewriteData] Hoan tat ghi lai du lieu sau khi doi PIN")
                
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(actionMessage = "✓ Đã đổi PIN và cập nhật dữ liệu thành công") }
                }
                
                // Tự động đọc lại thẻ để refresh UI
                delay(500)
                readCard()
                
            } catch (e: Exception) {
                println("[RewriteData] Loi: ${e.message}")
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _state.update { it.copy(actionMessage = "Lỗi ghi lại dữ liệu: ${e.message}") }
                }
            }
        }
    }
    
    /**
     * Mở khóa thẻ khi bị khóa do sai PIN
     */
    fun unlockCard() {
        if (_state.value.isUnlockingCard) return
        
        viewModelScope.launch {
            _state.update { it.copy(isUnlockingCard = true, actionMessage = "") }
            
            val result = withContext(Dispatchers.IO) {
                BusCardManager.unlockCard()
            }
            
            result.onSuccess {
                refreshLockStatus()
                _state.update { it.copy(
                    isUnlockingCard = false,
                    actionMessage = "Đã mở khóa thẻ. Nhấn \"Đọc thẻ\" để kiểm tra lại."
                ) }
            }.onFailure { error ->
                refreshLockStatus()
                _state.update { it.copy(
                    isUnlockingCard = false,
                    actionMessage = error.message ?: "Không thể mở khóa thẻ"
                ) }
            }
        }
    }
    
    /**
     * Cleanup
     */
    fun onCleared() {
        viewModelScope.cancel()
    }

    /**
     * Map dữ liệu đọc được trực tiếp từ thẻ sang model Customer dùng cho UI
     */
    private fun mapCardDataToCustomer(
        info: CustomerInfo,
        cardId: String,
        balance: Double,
        photoBytes: ByteArray? = null
    ): Customer {
        println("[ReadSmartCard] mapCardDataToCustomer: cardId=$cardId, photoBytes=${if (photoBytes != null) "${photoBytes.size} bytes" else "null"}")
        val cardType = when (info.cardType.trim().lowercase()) {
            "thẻ tháng", "the thang", "monthly" -> CardType.MONTHLY
            else -> CardType.NORMAL
        }

        val expiryDate = try {
            if (info.expiryDate.isNotBlank()) {
                LocalDate.parse(info.expiryDate, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            } else {
                LocalDate.now().plusMonths(1)
            }
        } catch (_: Exception) {
            LocalDate.now().plusMonths(1)
        }

        return Customer(
            id = "",
            fullName = info.fullName,
            cccd = info.cccd,
            dob = info.dob,
            address = info.address,
            phone = info.phone,
            cardType = cardType,
            expiryDate = expiryDate,
            balance = balance,
            cardId = cardId,
            photoPath = null,
            photoBytes = photoBytes
        )
    }
}
