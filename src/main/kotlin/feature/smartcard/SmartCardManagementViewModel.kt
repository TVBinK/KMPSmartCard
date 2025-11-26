package feature.smartcard

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import core.model.Customer
import core.database.DatabaseManager
import smartcard.BusCardManager

/**
 * ViewModel cho SmartCardManagementScreen
 * Quản lý logic nghiệp vụ cho quản lý smart card
 */
class SmartCardManagementViewModel {
    
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State Flow
    private val _state = MutableStateFlow(SmartCardManagementState())
    val state: StateFlow<SmartCardManagementState> = _state.asStateFlow()
    
    init {
        // Load customers khi khởi động
        viewModelScope.launch {
            loadCustomers()
            checkConnectionStatus()
            // Tự động đọc thẻ nếu đã kết nối
            if (_state.value.isConnected) {
                autoReadCard()
            }
        }
    }
    
    /**
     * Load customers từ database
     */
    private suspend fun loadCustomers() {
        val customers = withContext(Dispatchers.IO) {
            DatabaseManager.getAllCustomers()
        }
        _state.update { it.copy(customers = customers) }
    }
    
    /**
     * Kiểm tra trạng thái kết nối
     */
    private suspend fun checkConnectionStatus() {
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
     * Tự động đọc thẻ khi dialog mở và đã kết nối
     */
    private suspend fun autoReadCard() {
        _state.update { it.copy(isReadingCard = true) }
        
        try {
            val infoResult = withContext(Dispatchers.IO) {
                BusCardManager.getCustomerInfo()
            }
            val cardIdResult = withContext(Dispatchers.IO) {
                BusCardManager.getCardId()
            }
            
            infoResult.onSuccess { info ->
                cardIdResult.onSuccess { cardId ->
                    // Reload customers từ database
                    loadCustomers()
                    
                    // Tìm customer trong DB
                    val customer = _state.value.customers.find { it.cardId == cardId }
                    if (customer != null) {
                        selectCustomer(customer)
                        println("Tu dong doc the thanh cong: $cardId - ${customer.fullName}")
                    } else {
                        println("⚠ Thẻ $cardId chưa có trong database")
                    }
                }
            }.onFailure { error ->
                println("Loi tu dong doc the: ${error.message}")
            }
        } finally {
            _state.update { it.copy(isReadingCard = false) }
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
            
            infoResult.onSuccess { info ->
                cardIdResult.onSuccess { cardId ->
                    // Reload customers từ database
                    loadCustomers()
                    
                    // Tìm customer trong DB
                    val customer = _state.value.customers.find { it.cardId == cardId }
                    if (customer != null) {
                        selectCustomer(customer)
                        println("Doc the thanh cong: $cardId - ${customer.fullName}")
                    } else {
                        println("The $cardId chua co trong database")
                    }
                }
            }.onFailure { error ->
                println("Loi doc the: ${error.message}")
            }
        } finally {
            _state.update { it.copy(isReadingCard = false) }
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
     * Cleanup
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}

