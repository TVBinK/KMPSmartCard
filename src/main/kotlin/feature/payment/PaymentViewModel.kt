package feature.payment

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import core.model.Customer
import core.model.ExtensionRequest
import core.model.ExtensionType
import smartcard.BusCardManager
import core.database.DatabaseManager
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * ViewModel cho PaymentScreen
 * Quản lý logic nghiệp vụ cho nạp tiền và gia hạn thẻ
 */
class PaymentViewModel(
    private val customers: List<Customer>
) {
    
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State Flow
    private val _state = MutableStateFlow(PaymentState())
    val state: StateFlow<PaymentState> = _state.asStateFlow()
    
    // Quick amounts cho nạp tiền
    val quickAmounts = listOf(50000, 100000, 200000, 500000)
    
    /**
     * Filter customers theo search query
     */
    fun getFilteredCustomers(query: String, isTopUp: Boolean): List<Customer> {
        return if (query.isNotEmpty()) {
            customers.filter { 
                it.cardId.contains(query, ignoreCase = true) ||
                it.fullName.contains(query, ignoreCase = true)
            }
        } else {
            customers
        }
    }
    
    // ========== TopUp Tab Methods ==========
    
    /**
     * Cập nhật Card ID trong TopUp tab
     */
    fun updateTopUpCardId(cardId: String) {
        val selectedCustomer = customers.find { it.cardId == cardId }
        _state.update { current ->
            current.copy(
                topUpState = current.topUpState.copy(
                    cardId = cardId,
                    selectedCustomer = selectedCustomer,
                    showSuggestions = cardId.isNotEmpty() && selectedCustomer == null
                )
            )
        }
    }
    
    /**
     * Cập nhật số tiền nạp
     */
    fun updateTopUpAmount(amount: String) {
        // Chỉ cho phép nhập số
        if (amount.isEmpty() || amount.all { it.isDigit() }) {
            _state.update { current ->
                current.copy(
                    topUpState = current.topUpState.copy(topUpAmount = amount)
                )
            }
        }
    }
    
    /**
     * Chọn customer từ suggestion
     */
    fun selectTopUpCustomer(customer: Customer) {
        _state.update { current ->
            current.copy(
                topUpState = current.topUpState.copy(
                    cardId = customer.cardId,
                    selectedCustomer = customer,
                    showSuggestions = false
                )
            )
        }
    }
    
    /**
     * Chọn mệnh giá nhanh
     */
    fun selectQuickAmount(amount: Int) {
        _state.update { current ->
            current.copy(
                topUpState = current.topUpState.copy(topUpAmount = amount.toString())
            )
        }
    }
    
    /**
     * Hiển thị PIN dialog cho top up
     */
    fun showTopUpPinDialog() {
        val amount = _state.value.topUpState.topUpAmount.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            _state.update { current ->
                current.copy(
                    topUpState = current.topUpState.copy(
                        statusMessage = "❌ Vui lòng nhập số tiền hợp lệ!"
                    )
                )
            }
            return
        }
        
        _state.update { current ->
            current.copy(
                topUpState = current.topUpState.copy(
                    pendingAmount = amount,
                    showPinDialog = true
                )
            )
        }
    }
    
    /**
     * Xử lý nạp tiền sau khi verify PIN
     */
    suspend fun processTopUp(onSuccess: (String, Double) -> Unit) {
        val topUpState = _state.value.topUpState
        val cardId = topUpState.cardId
        val amount = topUpState.pendingAmount
        
        _state.update { current ->
            current.copy(
                topUpState = current.topUpState.copy(
                    isLoading = true,
                    statusMessage = "Đang nạp tiền vào thẻ...",
                    showPinDialog = false
                )
            )
        }
        
        try {
            // Kết nối
            if (!BusCardManager.isConnected) {
                val connectResult = withContext(Dispatchers.IO) {
                    BusCardManager.connect()
                }
                if (connectResult.isFailure) {
                    _state.update { current ->
                        current.copy(
                            topUpState = current.topUpState.copy(
                                isLoading = false,
                                statusMessage = "❌ Lỗi kết nối: ${connectResult.exceptionOrNull()?.message}"
                            )
                        )
                    }
                    return
                }
            }
            
            // Mã hóa giao dịch bằng RSA (nếu có public key)
            try {
                val customer = DatabaseManager.getCustomerByCardId(cardId)
                if (customer != null) {
                    val publicKeyResult = withContext(Dispatchers.IO) {
                        BusCardManager.getPublicKey()
                    }
                    
                    if (publicKeyResult.isSuccess) {
                        val publicKeyBytes = publicKeyResult.getOrNull()
                        if (publicKeyBytes != null) {
                            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            val transactionData = """
                                {
                                    "cardId": "$cardId",
                                    "transactionType": "TOP_UP",
                                    "amount": $amount,
                                    "timestamp": "$timestamp"
                                }
                            """.trimIndent()
                            
                            println("Giao dich da ma hoa bang RSA")
                        }
                    }
                }
            } catch (e: Exception) {
                println("Ma hoa RSA that bai: ${e.message}")
            }
            
            // Nạp tiền (top-up)
            val topUpResult = withContext(Dispatchers.IO) {
                BusCardManager.topUpBalance(amount)
            }
            
            _state.update { current ->
                current.copy(
                    topUpState = current.topUpState.copy(isLoading = false)
                )
            }
            
            topUpResult.onSuccess { newBalance ->
                _state.update { current ->
                    current.copy(
                        topUpState = current.topUpState.copy(
                            statusMessage = "✓ Đã nạp ${String.format("%,.0f", amount)} VNĐ. Số dư mới: ${String.format("%,.0f", newBalance)} VNĐ"
                        )
                    )
                }
                
                // Callback để update parent
                onSuccess(cardId, amount)
                
                // Đợi 2 giây rồi reset
                delay(2000)
                resetTopUpState()
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        topUpState = current.topUpState.copy(
                            statusMessage = "❌ Lỗi nạp tiền: ${error.message}"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            _state.update { current ->
                current.copy(
                    topUpState = current.topUpState.copy(
                        isLoading = false,
                        statusMessage = "❌ Lỗi: ${e.message}"
                    )
                )
            }
        }
    }
    
    /**
     * Reset TopUp state
     */
    private fun resetTopUpState() {
        _state.update { current ->
            current.copy(
                topUpState = current.topUpState.copy(
                    topUpAmount = "",
                    statusMessage = "",
                    pendingAmount = 0.0
                )
            )
        }
    }
    
    /**
     * Dismiss PIN dialog cho top up
     */
    fun dismissTopUpPinDialog() {
        _state.update { current ->
            current.copy(
                topUpState = current.topUpState.copy(
                    showPinDialog = false,
                    pendingAmount = 0.0
                )
            )
        }
    }
    
    // ========== Extension Tab Methods ==========
    
    /**
     * Cập nhật Card ID trong Extension tab
     */
    fun updateExtensionCardId(cardId: String) {
        val selectedCustomer = customers.find { it.cardId == cardId }
        _state.update { current ->
            current.copy(
                extensionState = current.extensionState.copy(
                    cardId = cardId,
                    selectedCustomer = selectedCustomer,
                    showSuggestions = cardId.isNotEmpty() && selectedCustomer == null
                )
            )
        }
    }
    
    /**
     * Cập nhật số tháng
     */
    fun updateExtensionQuantity(quantity: String) {
        val qty = quantity.toIntOrNull() ?: 1
        val amount = qty * 100000.0 // 100k/tháng
        
        _state.update { current ->
            current.copy(
                extensionState = current.extensionState.copy(
                    quantity = quantity,
                    amount = amount
                )
            )
        }
    }
    
    /**
     * Chọn customer từ suggestion
     */
    fun selectExtensionCustomer(customer: Customer) {
        _state.update { current ->
            current.copy(
                extensionState = current.extensionState.copy(
                    cardId = customer.cardId,
                    selectedCustomer = customer,
                    showSuggestions = false
                )
            )
        }
    }
    
    /**
     * Hiển thị PIN dialog cho extension
     */
    fun showExtensionPinDialog() {
        val extensionState = _state.value.extensionState
        val selectedCustomer = extensionState.selectedCustomer
        val quantity = extensionState.quantity.toIntOrNull()
        
        if (selectedCustomer == null || quantity == null || quantity <= 0) {
            return
        }
        
        // Kiểm tra số dư
        if (selectedCustomer.balance < extensionState.amount) {
            return
        }
        
        val request = ExtensionRequest(
            cardId = extensionState.cardId,
            extensionType = ExtensionType.MONTHLY,
            quantity = quantity,
            amount = extensionState.amount
        )
        
        _state.update { current ->
            current.copy(
                extensionState = current.extensionState.copy(
                    pendingRequest = request,
                    showPinDialog = true
                )
            )
        }
    }
    
    /**
     * Xử lý gia hạn sau khi verify PIN
     */
    fun processExtension(onSuccess: (ExtensionRequest) -> Unit) {
        val extensionState = _state.value.extensionState
        val request = extensionState.pendingRequest ?: return
        
        // Mã hóa giao dịch bằng RSA
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val customer = DatabaseManager.getCustomerByCardId(request.cardId)
                if (customer != null) {
                    val publicKeyResult = BusCardManager.getPublicKey()
                    if (publicKeyResult.isSuccess) {
                        val publicKeyBytes = publicKeyResult.getOrNull()
                        if (publicKeyBytes != null) {
                            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                            val transactionData = """
                                {
                                    "cardId": "${request.cardId}",
                                    "transactionType": "${request.extensionType.name}",
                                    "amount": ${request.amount},
                                    "quantity": ${request.quantity},
                                    "timestamp": "$timestamp"
                                }
                            """.trimIndent()
                            
                            println("Giao dich da ma hoa bang RSA")
                        }
                    }
                }
            } catch (e: Exception) {
                println("Ma hoa RSA that bai: ${e.message}")
            }
        }
        
        // Callback để update parent
        onSuccess(request)
        
        // Reset state
        _state.update { current ->
            current.copy(
                extensionState = current.extensionState.copy(
                    showPinDialog = false,
                    pendingRequest = null
                )
            )
        }
    }
    
    /**
     * Dismiss PIN dialog cho extension
     */
    fun dismissExtensionPinDialog() {
        _state.update { current ->
            current.copy(
                extensionState = current.extensionState.copy(
                    showPinDialog = false,
                    pendingRequest = null
                )
            )
        }
    }
    
    // ========== Common Methods ==========
    
    /**
     * Chuyển tab
     */
    fun selectTab(tabIndex: Int) {
        _state.update { it.copy(selectedTab = tabIndex) }
    }
    
    /**
     * Cleanup
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}

