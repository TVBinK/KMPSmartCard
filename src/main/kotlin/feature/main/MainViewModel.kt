package feature.main

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import core.model.CardType
import core.model.Customer
import core.model.ExtensionRequest
import core.database.DatabaseManager
import smartcard.BusCardManager
import utils.AppConstants
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ViewModel cho MainScreen
 * Quản lý tất cả logic nghiệp vụ và state của màn hình chính
 */
class MainViewModel {
    
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State Flow - reactive state management
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()
    
    init {
        // Khởi tạo và load dữ liệu
        viewModelScope.launch {
            loadInitialData()
            startPeriodicRefresh()
        }
    }
    
    /**
     * Load dữ liệu ban đầu
     */
    private suspend fun loadInitialData() {
        delay(AppConstants.INITIAL_LOAD_DELAY_MS)
        refreshCustomers()
        refreshCardStatus()
    }
    
    /**
     * Refresh customers từ database và tự động chuyển thẻ hết hạn
     */
    fun refreshCustomers() {
        viewModelScope.launch(Dispatchers.IO) {
            val loadedCustomers = DatabaseManager.getAllCustomers()
            val updatedCustomers = convertExpiredMonthlyCards(loadedCustomers)
            
            withContext(Dispatchers.Main) {
                _state.update { it.copy(customers = updatedCustomers, isLoading = false) }
                updateRecentActivities(updatedCustomers)
            }
        }
    }
    
    /**
     * Tự động chuyển thẻ tháng hết hạn về thẻ thường
     */
    private fun convertExpiredMonthlyCards(customers: List<Customer>): List<Customer> {
        return customers.map { customer ->
            val updated = customer.checkAndConvertExpiredMonthlyCard()
            if (updated.cardType != customer.cardType) {
                DatabaseManager.updateCustomer(updated)
                updated
            } else {
                customer
            }
        }
    }
    
    /**
     * Cập nhật hoạt động gần đây
     */
    private suspend fun updateRecentActivities(customers: List<Customer>) {
        val activities = customers.flatMap { customer ->
            DatabaseManager.getTransactionsByCardId(customer.cardId).map { trans ->
                trans.toMutableMap().apply {
                    put("customer_name", customer.fullName)
                    put("card_id", customer.cardId)
                }
            }
        }.sortedByDescending { 
            parseTransactionDate(it["transaction_date"] as? String ?: "")
        }.take(5)
        
        _state.update { it.copy(recentActivities = activities) }
    }
    
    /**
     * Parse transaction date an toàn
     */
    private fun parseTransactionDate(dateStr: String): java.time.LocalDateTime = try {
        java.time.LocalDateTime.parse(dateStr.replace(" ", "T"))
    } catch (e: Exception) {
        java.time.LocalDateTime.MIN
    }
    
    /**
     * Refresh trạng thái kết nối thẻ
     */
    suspend fun refreshCardStatus() {
        val isConnected = BusCardManager.isConnected
        val hasData = if (isConnected) {
            withContext(Dispatchers.IO) {
                BusCardManager.checkCardCreated().getOrNull() ?: false
            }
        } else {
            false
        }
        
        _state.update { 
            it.copy(
                isCardConnected = isConnected,
                isCardHasData = hasData
            )
        }
        println("Card status refreshed - Connected: $isConnected, Has Data: $hasData")
    }
    
    /**
     * Xử lý quẹt thẻ
     */
    suspend fun handleCardTap(cardId: String) {
        val customer = _state.value.customers.find { it.cardId == cardId } ?: return
        
        when (customer.cardType) {
            CardType.MONTHLY -> {
                val updatedCustomer = customer.checkAndConvertExpiredMonthlyCard()
                
                if (updatedCustomer.cardType != customer.cardType) {
                    // Thẻ tháng đã hết hạn, chuyển về thẻ thường
                    DatabaseManager.updateCustomer(updatedCustomer)
                    refreshCustomers()
                    val convertedCustomer = _state.value.customers.find { it.cardId == cardId }
                    if (convertedCustomer != null && convertedCustomer.balance >= AppConstants.NORMAL_CARD_TAP_AMOUNT) {
                        processNormalCardTap(convertedCustomer, "đã chuyển từ thẻ tháng")
                    }
                } else {
                    // Thẻ tháng còn hạn, miễn phí
                    DatabaseManager.insertTransaction(
                        cardId = cardId,
                        transactionType = AppConstants.TRANSACTION_TYPE_TAP,
                        amount = 0.0,
                        balanceBefore = customer.balance,
                        balanceAfter = customer.balance,
                        description = "Quẹt thẻ tháng - Miễn phí (còn hạn đến ${customer.expiryDate})"
                    )
                    refreshCustomers()
                    updateSelectedCustomer(cardId)
                }
            }
            CardType.NORMAL -> {
                if (customer.balance >= AppConstants.NORMAL_CARD_TAP_AMOUNT) {
                    processNormalCardTap(customer)
                }
            }
        }
    }
    
    /**
     * Xử lý quẹt thẻ thường
     */
    private suspend fun processNormalCardTap(customer: Customer, prefix: String = "") {
        val tapAmount = AppConstants.NORMAL_CARD_TAP_AMOUNT
        val balanceBefore = customer.balance
        val balanceAfter = balanceBefore - tapAmount
        
        // Cập nhật database
        DatabaseManager.updateCustomerBalance(customer.cardId, balanceAfter)
        
        // Trừ tiền trên smart card nếu đã kết nối
        if (BusCardManager.isConnected) {
            GlobalScope.launch(Dispatchers.IO) {
                BusCardManager.deductBalance(tapAmount)
                    .onSuccess { println("Da tru tien tu Smart Card: ${String.format("%,.0f", tapAmount)} VND") }
                    .onFailure { println("Loi tru tien tu Smart Card: ${it.message}") }
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
        
        // Reload data
        refreshCustomers()
        updateSelectedCustomer(customer.cardId)
    }
    
    /**
     * Xử lý nạp tiền
     */
    fun handleTopUpTransaction(cardId: String, amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val customer = DatabaseManager.getCustomerByCardId(cardId)
            if (customer == null) {
                println("Khong tim thay khach hang de cap nhat nap tien: $cardId")
                return@launch
            }
            
            val balanceBefore = customer.balance
            val balanceAfter = balanceBefore + amount
            
            DatabaseManager.updateCustomerBalance(cardId, balanceAfter)
            DatabaseManager.insertTransaction(
                cardId = cardId,
                transactionType = AppConstants.TRANSACTION_TYPE_TOP_UP,
                amount = amount,
                balanceBefore = balanceBefore,
                balanceAfter = balanceAfter,
                description = "Nạp ${String.format("%,.0f", amount)} VNĐ vào thẻ"
            )
            
            withContext(Dispatchers.Main) {
                refreshCustomers()
            }
        }
    }
    
    /**
     * Xử lý gia hạn thẻ tháng
     */
    fun handleMonthlyExtension(request: ExtensionRequest) {
        viewModelScope.launch(Dispatchers.IO) {
            val customer = DatabaseManager.getCustomerByCardId(request.cardId)
            if (customer == null) {
                println("Khong tim thay khach hang de gia han: ${request.cardId}")
                return@launch
            }
            if (request.quantity <= 0) {
                println("So thang gia han khong hop le: ${request.quantity}")
                return@launch
            }
            if (customer.balance < request.amount) {
                println("So du khong du de gia han thang cho the: ${request.cardId}")
                return@launch
            }

            val balanceBefore = customer.balance
            val balanceAfter = balanceBefore - request.amount
            val today = LocalDate.now()
            val baseDate = if (customer.cardType == CardType.MONTHLY && customer.expiryDate.isAfter(today)) {
                customer.expiryDate
            } else {
                today
            }
            val newExpiry = baseDate.plusMonths(request.quantity.toLong())
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            val actionText = if (customer.cardType == CardType.MONTHLY && !customer.isMonthlyCardExpired()) {
                "gia hạn vé tháng thêm ${request.quantity} tháng"
            } else {
                "mua vé tháng ${request.quantity} tháng"
            }
            val description = "${customer.fullName} $actionText (hết hạn ${newExpiry.format(formatter)})"

            val updatedCustomer = customer.copy(
                balance = balanceAfter,
                cardType = CardType.MONTHLY,
                expiryDate = newExpiry
            )
            DatabaseManager.updateCustomer(updatedCustomer)
            DatabaseManager.insertTransaction(
                cardId = request.cardId,
                transactionType = AppConstants.TRANSACTION_TYPE_EXTEND_MONTHLY,
                amount = request.amount,
                balanceBefore = balanceBefore,
                balanceAfter = balanceAfter,
                description = description
            )

            // Cập nhật thông tin lên smart card nếu có
            if (!BusCardManager.isConnected) {
                val connectResult = BusCardManager.connect()
                if (connectResult.isFailure) {
                    println("Khong the ket noi the khi gia han: ${connectResult.exceptionOrNull()?.message}")
                }
            }
            if (BusCardManager.isConnected) {
                val infoResult = BusCardManager.updateCustomerInfo(
                    fullName = customer.fullName,
                    customerType = customer.customerType.displayName,
                    expiryDate = newExpiry.format(formatter),
                    cardType = CardType.MONTHLY.displayName,
                    linkedCustomerId = customer.linkedCustomerCode
                )
                if (infoResult.isFailure) {
                    println("Khong the cap nhat thong tin gia han len the: ${infoResult.exceptionOrNull()?.message}")
                }
                val balanceResult = BusCardManager.updateBalance(balanceAfter)
                if (balanceResult.isFailure) {
                    println("Khong the cap nhat so du sau gia han tren the: ${balanceResult.exceptionOrNull()?.message}")
                }
            }

            withContext(Dispatchers.Main) {
                refreshCustomers()
                updateSelectedCustomer(request.cardId)
            }
        }
    }
    
    /**
     * Cập nhật search query
     */
    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }
    
    /**
     * Cập nhật selected customer
     */
    fun updateSelectedCustomer(cardId: String) {
        val customer = _state.value.customers.find { it.cardId == cardId }
        _state.update { it.copy(selectedCustomer = customer) }
    }
    
    /**
     * Clear selected customer
     */
    fun clearSelectedCustomer() {
        _state.update { it.copy(selectedCustomer = null) }
    }
    
    /**
     * Xóa customer
     */
    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch(Dispatchers.IO) {
            DatabaseManager.deleteCustomer(customer.cardId)
            withContext(Dispatchers.Main) {
                refreshCustomers()
                if (_state.value.selectedCustomer?.cardId == customer.cardId) {
                    clearSelectedCustomer()
                }
            }
        }
    }
    
    /**
     * Bắt đầu refresh định kỳ
     */
    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(AppConstants.STATS_RELOAD_INTERVAL_MS)
                refreshCardStatus()
                val currentCustomers = _state.value.customers
                if (currentCustomers.isNotEmpty()) {
                    updateRecentActivities(currentCustomers)
                }
            }
        }
    }
    
    /**
     * Cleanup khi ViewModel bị destroy
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}

