package feature.pinverification

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import smartcard.BusCardManager

/**
 * ViewModel cho PinVerificationDialog
 * Quản lý logic xác thực PIN
 */
class PinVerificationViewModel(
    private val maxAttempts: Int = 4,
    private val onVerified: (String) -> Unit
) {
    
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State Flow
    private val _state = MutableStateFlow(PinVerificationState(attemptsRemaining = maxAttempts))
    val state: StateFlow<PinVerificationState> = _state.asStateFlow()
    
    init {
        // Kiểm tra trạng thái thẻ khi khởi động
        viewModelScope.launch {
            checkCardStatus()
        }
    }
    
    /**
     * Kiểm tra trạng thái thẻ
     */
    private suspend fun checkCardStatus() {
        val isBlocked = BusCardManager.isCardBlocked
        val attempts = maxAttempts - BusCardManager.pinAttempts
        
        _state.update { 
            it.copy(
                isCardBlocked = isBlocked,
                attemptsRemaining = attempts
            )
        }
    }
    
    /**
     * Cập nhật PIN
     */
    fun updatePin(value: String) {
        if (value.all { it.isDigit() } && value.length <= 6) {
            _state.update { 
                it.copy(
                    pin = value,
                    errorMessage = ""
                )
            }
        }
    }
    
    /**
     * Toggle PIN visibility
     */
    fun togglePinVisibility() {
        _state.update { it.copy(pinVisible = !it.pinVisible) }
    }
    
    /**
     * Xác thực PIN
     */
    suspend fun verifyPin() {
        val state = _state.value
        
        if (state.pin.length < 4) {
            _state.update { it.copy(errorMessage = "PIN phải có ít nhất 4 ký tự") }
            return
        }
        
        _state.update { 
            it.copy(
                isLoading = true,
                errorMessage = ""
            )
        }
        
        val result = withContext(Dispatchers.IO) {
            BusCardManager.checkPin(state.pin)
        }
        
        _state.update { it.copy(isLoading = false) }
        
        result.onSuccess { isCorrect ->
            if (isCorrect) {
                // PIN đúng - gọi callback
                onVerified(state.pin)
            } else {
                // PIN không đúng
                updateAttemptsAndStatus()
                _state.update { 
                    it.copy(
                        errorMessage = if (_state.value.isCardBlocked) {
                            "Thẻ đã bị khóa do nhập sai PIN quá nhiều lần"
                        } else {
                            "PIN không đúng. Vui lòng thử lại."
                        },
                        pin = ""
                    )
                }
            }
        }.onFailure { error ->
            // PIN sai hoặc có lỗi
            updateAttemptsAndStatus()
            _state.update { 
                it.copy(
                    errorMessage = if (_state.value.isCardBlocked) {
                        "Thẻ đã bị khóa do nhập sai PIN quá nhiều lần"
                    } else {
                        error.message ?: "PIN không đúng"
                    },
                    pin = ""
                )
            }
        }
    }
    
    /**
     * Cập nhật số lần thử và trạng thái thẻ
     */
    private suspend fun updateAttemptsAndStatus() {
        val attempts = maxAttempts - BusCardManager.pinAttempts
        val isBlocked = BusCardManager.isCardBlocked
        
        _state.update { 
            it.copy(
                attemptsRemaining = attempts,
                isCardBlocked = isBlocked
            )
        }
    }
    
    /**
     * Cleanup
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}

