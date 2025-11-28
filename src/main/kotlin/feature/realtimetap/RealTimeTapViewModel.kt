package feature.realtimetap

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import core.model.TapType
import core.database.DatabaseManager
import smartcard.BusCardManager
import java.time.LocalDateTime

/**
 * ViewModel cho RealTimeTapScreen
 * Quản lý logic polling và phát hiện thẻ
 */
class RealTimeTapViewModel(
    private val onTapDetected: (String, TapType) -> Unit
) {
    
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State Flow
    private val _state = MutableStateFlow(RealTimeTapState())
    val state: StateFlow<RealTimeTapState> = _state.asStateFlow()
    
    private var pollingJob: Job? = null
    
    init {
        startPolling()
    }
    
    /**
     * Bắt đầu polling để phát hiện thẻ
     */
    fun startPolling() {
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

                    // Kiểm tra thẻ có bị khóa không
                    if (BusCardManager.isCardBlocked) {
                        _state.update { 
                            it.copy(
                                statusMessage = "⚠️ Thẻ đã bị khóa. Vui lòng mở khóa thẻ trước khi quẹt.",
                                currentCardId = null,
                                detectedCustomer = null,
                                cardHandled = false
                            )
                        }
                        delay(1000)
                        continue
                    }

                    val cardPresent = BusCardManager.isCardPresent
                    if (!cardPresent) {
                        if (_state.value.currentCardId != null) {
                            _state.update { 
                                it.copy(
                                    statusMessage = "Đang chờ quẹt thẻ...",
                                    currentCardId = null,
                                    detectedCustomer = null,
                                    cardHandled = false
                                )
                            }
                        }
                        delay(300)
                        continue
                    }
                    
                    if (_state.value.cardHandled) {
                        delay(300)
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
        
        // Load thông tin khách hàng
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
        } else {
            _state.update { 
                it.copy(
                    detectedCustomer = null,
                    statusMessage = "✓ Đã phát hiện thẻ: $cardId"
                )
            }
        }
        
        // Xác định loại tap (TAP_ON hoặc TAP_OFF)
        val tapType = TapType.TAP_ON // Simplified
        
        // Callback để parent xử lý
        onTapDetected(cardId, tapType)
    }
    
    /**
     * Toggle listening state
     */
    fun toggleListening() {
        val newState = !_state.value.isListening
        _state.update { it.copy(isListening = newState) }
        
        if (newState) {
            startPolling()
        } else {
            stopPolling()
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

