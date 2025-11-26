package feature.changepin

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import smartcard.BusCardManager

/**
 * ViewModel cho ChangePinDialog
 * Quản lý logic thay đổi PIN
 */
class ChangePinViewModel(
    private val onSuccess: () -> Unit
) {
    
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State Flow
    private val _state = MutableStateFlow(ChangePinState())
    val state: StateFlow<ChangePinState> = _state.asStateFlow()
    
    /**
     * Cập nhật PIN hiện tại
     */
    fun updateCurrentPin(value: String) {
        if (value.all { it.isDigit() } && value.length <= 6) {
            _state.update { 
                it.copy(
                    currentPin = value,
                    errorMessage = ""
                )
            }
        }
    }
    
    /**
     * Cập nhật PIN mới
     */
    fun updateNewPin(value: String) {
        if (value.all { it.isDigit() } && value.length <= 6) {
            _state.update { 
                it.copy(
                    newPin = value,
                    errorMessage = ""
                )
            }
        }
    }
    
    /**
     * Cập nhật PIN xác nhận
     */
    fun updateConfirmPin(value: String) {
        if (value.all { it.isDigit() } && value.length <= 6) {
            _state.update { 
                it.copy(
                    confirmPin = value,
                    errorMessage = ""
                )
            }
        }
    }
    
    /**
     * Toggle visibility của các PIN fields
     */
    fun toggleCurrentPinVisibility() {
        _state.update { it.copy(currentPinVisible = !it.currentPinVisible) }
    }
    
    fun toggleNewPinVisibility() {
        _state.update { it.copy(newPinVisible = !it.newPinVisible) }
    }
    
    fun toggleConfirmPinVisibility() {
        _state.update { it.copy(confirmPinVisible = !it.confirmPinVisible) }
    }
    
    /**
     * Validate và thay đổi PIN
     */
    suspend fun changePin() {
        val state = _state.value
        
        // Validation
        if (state.currentPin.isEmpty()) {
            _state.update { it.copy(errorMessage = "Vui lòng nhập PIN hiện tại") }
            return
        }
        
        if (state.newPin.length < 4 || state.newPin.length > 6) {
            _state.update { it.copy(errorMessage = "PIN mới phải có từ 4-6 chữ số") }
            return
        }
        
        if (state.newPin == state.currentPin) {
            _state.update { it.copy(errorMessage = "PIN mới không được trùng với PIN hiện tại") }
            return
        }
        
        if (state.newPin != state.confirmPin) {
            _state.update { it.copy(errorMessage = "PIN xác nhận không khớp với PIN mới") }
            return
        }
        
        _state.update { 
            it.copy(
                isLoading = true,
                errorMessage = ""
            )
        }
        
        // Xác thực PIN hiện tại trước
        val verifyResult = withContext(Dispatchers.IO) {
            BusCardManager.checkPin(state.currentPin)
        }
        
        verifyResult.onSuccess { isCorrect ->
            if (!isCorrect) {
                _state.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "PIN hiện tại không đúng",
                        currentPin = ""
                    )
                }
                return
            }
            
            // PIN đúng, tiếp tục đổi PIN
            val updateResult = withContext(Dispatchers.IO) {
                BusCardManager.updatePin(state.newPin)
            }
            
            _state.update { it.copy(isLoading = false) }
            
            updateResult.onSuccess {
                _state.update { 
                    it.copy(
                        successMessage = "Đã thay đổi PIN thành công!",
                        currentPin = "",
                        newPin = "",
                        confirmPin = ""
                    )
                }
                onSuccess()
            }.onFailure { error ->
                _state.update { 
                    it.copy(
                        errorMessage = error.message ?: "Không thể thay đổi PIN"
                    )
                }
            }
        }.onFailure { error ->
            _state.update { 
                it.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "PIN hiện tại không đúng",
                    currentPin = ""
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

