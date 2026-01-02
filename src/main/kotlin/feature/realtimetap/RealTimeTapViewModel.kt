package feature.realtimetap

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import core.model.CardType
import core.database.DatabaseManager
import smartcard.BusCardManager
import utils.AppConstants
import java.time.LocalDateTime

/**
 * ViewModel cho RealTimeTapScreen
 * Quản lý logic polling, phát hiện thẻ và xử lý quét thẻ
 */
class RealTimeTapViewModel(
    private val onTapDetected: (String) -> Unit
) {
    
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State Flow
    private val _state = MutableStateFlow(RealTimeTapState())
    val state: StateFlow<RealTimeTapState> = _state.asStateFlow()
    
    private var pollingJob: Job? = null
    
    init {
        startTap()
    }
    
    /**
     * Bắt đầu để phát hiện thẻ
     */
    fun startTap() {
        if (pollingJob?.isActive == true) return
        
        pollingJob = viewModelScope.launch {
            while (isActive && _state.value.isListening) {
                try {
                    // Kiểm tra kết nối
                    if (!BusCardManager.isConnected) {
                        _state.update { it.copy(statusMessage = "⚠️ Chưa kết nối với Java Card") }
                        delay(1000)
                        continue
                    }
                    
                    // Đọc Card ID
                    val cardIdResult = withContext(Dispatchers.IO) {
                        BusCardManager.getCardId()
                    }
                    
                    cardIdResult.onSuccess { cardId ->
                        // Kiểm tra lại thẻ có bị khóa sau khi đọc Card ID
                        if (BusCardManager.isCardBlocked) {
                            _state.update { 
                                it.copy(
                                    statusMessage = "⚠️ Thẻ đã bị khóa. Vui lòng mở khóa thẻ trước khi quẹt.",
                                    currentCardId = null,
                                    detectedCustomer = null,
                                    cardHandled = false
                                )
                            }
                            return@launch
                        }
                        
                        if (cardId.isNotEmpty() && cardId != _state.value.currentCardId) {
                            // Phát hiện thẻ mới!
                            handleNewCard(cardId)
                        } else if (cardId.isEmpty()) {
                            _state.update { 
                                it.copy(
                                    currentCardId = null,
                                    detectedCustomer = null,
                                    cardHandled = false
                                )
                            }
                        }
                    }.onFailure { error ->
                        // Kiểm tra nếu lỗi do thẻ bị khóa
                        val errorMessage = error.message ?: ""
                        if (errorMessage.contains("khóa", ignoreCase = true) || BusCardManager.isCardBlocked) {
                            _state.update { 
                                it.copy(
                                    statusMessage = "⚠️ Thẻ đã bị khóa. Vui lòng mở khóa thẻ trước khi quẹt.",
                                    currentCardId = null,
                                    detectedCustomer = null,
                                    cardHandled = false
                                )
                            }
                        } else {
                            _state.update { 
                                it.copy(
                                    statusMessage = "Chờ quẹt thẻ...",
                                    detectedCustomer = null,
                                    cardHandled = false
                                )
                            }
                        }
                    }
                    
                } catch (e: Exception) {
                    _state.update { it.copy(statusMessage = "Lỗi: ${e.message}") }
                }
                
                delay(500) // Poll mỗi 0.5 giây
            }
        }
    }
    
    /**
     * Xử lý khi phát hiện thẻ mới
     */
    private suspend fun handleNewCard(cardId: String) {
        _state.update { 
            it.copy(
                currentCardId = cardId,
                lastTapTime = LocalDateTime.now(),
                cardHandled = true
            )
        }
        
        // Load thông tin khách hàng từ DB
        val customer = withContext(Dispatchers.IO) {
            DatabaseManager.getAllCustomers().find { it.cardId == cardId }
        }
        
        if (customer != null) {
            _state.update { 
                it.copy(
                    detectedCustomer = customer,
                    statusMessage = "✓ ${customer.fullName}"
                )
            }
            
            // Xử lý quét thẻ
            handleCardTap(cardId, customer)
        }
        
        // Callback để parent refresh data nếu cần
        // Chỉ cần quẹt thẻ 1 lần khi lên xe
        onTapDetected(cardId)
    }
    
    /**
     * Xử lý quẹt thẻ
     */
    private suspend fun handleCardTap(cardId: String, customer: core.model.Customer) {
        // Kiểm tra thẻ có bị khóa không
        if (BusCardManager.isCardBlocked) {
            _state.update { 
                it.copy(
                    statusMessage = "⚠️ Không thể quẹt thẻ: Thẻ đã bị khóa"
                )
            }
            return
        }
        
        when (customer.cardType) {
            CardType.MONTHLY -> {
                val updatedCustomer = customer.checkAndConvertExpiredMonthlyCard()
                
                if (updatedCustomer.cardType != customer.cardType) {
                    // Thẻ tháng đã hết hạn, chuyển về thẻ thường
                    DatabaseManager.updateCustomer(updatedCustomer)
                    val convertedCustomer = withContext(Dispatchers.IO) {
                        DatabaseManager.getCustomerByCardId(cardId)
                    }
                    if (convertedCustomer != null && convertedCustomer.balance >= 7000.0) {
                        processNormalCardTap(convertedCustomer, "đã chuyển từ thẻ tháng")
                    } else {
                        _state.update { 
                            it.copy(
                                statusMessage = "⚠️ Số dư không đủ để quẹt thẻ"
                            )
                        }
                    }
                } else {
                    // Thẻ tháng còn hạn, miễn phí
                    DatabaseManager.insertTransaction(
                        cardId = cardId,
                        transactionType = AppConstants.TRANSACTION_TYPE_TAP,
                        amount = 0.0,
                        balanceBefore = customer.balance,
                        balanceAfter = customer.balance,
                        description = "Quẹt thẻ tháng (còn hạn đến ${customer.expiryDate})"
                    )
                    _state.update { 
                        it.copy(
                            statusMessage = "Quẹt thẻ tháng thành công"
                        )
                    }
                }
            }
            CardType.NORMAL -> {
                if (customer.balance >= 7000.0) {
                    processNormalCardTap(customer)
                } else {
                    _state.update { 
                        it.copy(
                            statusMessage = "⚠️ Số dư không đủ để quẹt thẻ (Cần: ${String.format("%,.0f", 7000.0)} VNĐ)"
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Xử lý quẹt thẻ thường
     */
    private suspend fun processNormalCardTap(customer: core.model.Customer, prefix: String = "") {
        val tapAmount = 7000.0
        val balanceBefore = customer.balance
        val balanceAfter = balanceBefore - tapAmount
        
        // Cập nhật database
        DatabaseManager.updateCustomerBalance(customer.cardId, balanceAfter)
        
        // Trừ tiền trên smart card nếu đã kết nối
        if (BusCardManager.isConnected) {
            withContext(Dispatchers.IO) {
                BusCardManager.deductBalance(tapAmount)
                    .onSuccess { 
                        println("Da tru tien tu Smart Card: ${String.format("%,.0f", tapAmount)} VND")
                    }
                    .onFailure { 
                        println("Loi tru tien tu Smart Card: ${it.message}")
                    }
            }
        }
        
        // Tạo description
        val description = "Quẹt thẻ thường - Trừ ${String.format("%,.0f", tapAmount)} VNĐ" +
                         if (prefix.isNotEmpty()) " ($prefix)" else ""
        
        // Lưu transaction
        DatabaseManager.insertTransaction(
            cardId = customer.cardId,
            transactionType = AppConstants.TRANSACTION_TYPE_TAP,
            amount = tapAmount,
            balanceBefore = balanceBefore,
            balanceAfter = balanceAfter,
            description = description
        )
        
        // Cập nhật state với thông tin mới
        val updatedCustomer = withContext(Dispatchers.IO) {
            DatabaseManager.getCustomerByCardId(customer.cardId)
        }
        if (updatedCustomer != null) {
            _state.update { 
                it.copy(
                    detectedCustomer = updatedCustomer,
                    statusMessage = "✓ Quẹt thẻ thành công - Trừ ${String.format("%,.0f", tapAmount)} VNĐ"
                )
            }
        }
    }
    
    /**
     * Dừng polling
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
    
    /**
     * Cleanup
     */
    fun onCleared() {
        stopPolling()
        viewModelScope.cancel()
    }
}

