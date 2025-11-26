package feature.ticketvalidation

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import core.model.CardType
import core.model.Customer
import core.model.CustomerType
import smartcard.BusCardManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * ViewModel cho TicketValidationDialog
 * Quản lý logic xác thực thẻ
 */
class TicketValidationViewModel(
    initialCustomer: Customer
) {
    
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State Flow
    private val _state = MutableStateFlow(TicketValidationState(customer = initialCustomer))
    val state: StateFlow<TicketValidationState> = _state.asStateFlow()
    
    /**
     * Đọc và xác thực thẻ từ smart card
     */
    suspend fun readAndVerifyCard() {
        _state.update { 
            it.copy(
                isLoading = true,
                statusMessage = "Đang đọc từ thẻ..."
            )
        }
        
        // Kết nối
        if (!BusCardManager.isConnected) {
            val connectResult = withContext(Dispatchers.IO) {
                BusCardManager.connect()
            }
            if (connectResult.isFailure) {
                _state.update { 
                    it.copy(
                        isLoading = false,
                        statusMessage = "Lỗi kết nối: ${connectResult.exceptionOrNull()?.message}"
                    )
                }
                return
            }
        }
        
        // Đọc thông tin
        val infoResult = withContext(Dispatchers.IO) {
            BusCardManager.getCustomerInfo()
        }
        val balanceResult = withContext(Dispatchers.IO) {
            BusCardManager.getBalance()
        }
        val cardIdResult = withContext(Dispatchers.IO) {
            BusCardManager.getCardId()
        }
        
        _state.update { it.copy(isLoading = false) }
        
        if (infoResult.isSuccess && balanceResult.isSuccess && cardIdResult.isSuccess) {
            val info = infoResult.getOrNull()!!
            val balance = balanceResult.getOrNull()!!
            val cardId = cardIdResult.getOrNull()!!
            
            // Parse customer type
            val customerType = CustomerType.CUSTOMER
            
            // Parse card type
            val cardType = when {
                info.cardType.contains("Tháng") -> CardType.MONTHLY
                else -> CardType.NORMAL
            }
            
            // Parse expiry date
            val expiryDate = try {
                LocalDate.parse(info.expiryDate, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            } catch (e: Exception) {
                LocalDate.now().plusMonths(1)
            }
            
            // Update customer
            val updatedCustomer = _state.value.customer.copy(
                fullName = info.fullName,
                customerType = customerType,
                cardType = cardType,
                expiryDate = expiryDate,
                balance = balance,
                cardId = cardId
            )
            
            _state.update { 
                it.copy(
                    customer = updatedCustomer,
                    statusMessage = "✓ Đã xác thực thẻ thành công"
                )
            }
        } else {
            _state.update { 
                it.copy(
                    statusMessage = "Lỗi đọc thẻ: ${infoResult.exceptionOrNull()?.message}"
                )
            }
        }
    }
    
    /**
     * Cleanup
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}

