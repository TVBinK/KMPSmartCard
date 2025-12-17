package feature.changepin

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import smartcard.BusCardManager

/**
 * ViewModel cho ChangePinDialog
 * Quản lý logic thay đổi PIN
 */
class ChangePinViewModel(
    private val onSuccess: (newPin: String) -> Unit
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
        
        // Gọi updatePin với PIN cũ và PIN mới (logic checkPin đã được tích hợp trong updatePin)
        val updateResult = withContext(Dispatchers.IO) {
            BusCardManager.updatePin(state.currentPin, state.newPin)
        }
        
        // Cập nhật số lần thử còn lại và trạng thái khóa
        val attemptsRemaining = 4 - BusCardManager.pinAttempts
        val isBlocked = BusCardManager.isCardBlocked
        
        _state.update { 
            it.copy(
                isLoading = false,
                attemptsRemaining = attemptsRemaining,
                isCardBlocked = isBlocked
            )
        }
        
        updateResult.onSuccess {
            _state.update { 
                it.copy(
                    successMessage = "Đã thay đổi PIN thành công!",
                    currentPin = "",
                    newPin = "",
                    confirmPin = "",
                    attemptsRemaining = 4,
                    isCardBlocked = false
                )
            }
            // Truyền PIN mới ra ngoài để có thể ghi lại dữ liệu lên thẻ
            onSuccess(state.newPin)
        }.onFailure { error ->
            _state.update { 
                it.copy(
                    errorMessage = error.message ?: "Không thể thay đổi PIN",
                    currentPin = "",
                    attemptsRemaining = attemptsRemaining,
                    isCardBlocked = isBlocked
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

