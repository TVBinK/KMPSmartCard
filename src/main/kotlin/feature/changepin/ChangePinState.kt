package feature.changepin

/**
 * State của ChangePinDialog
 */
data class ChangePinState(
    val currentPin: String = "",
    val newPin: String = "",
    val confirmPin: String = "",
    val errorMessage: String = "",
    val successMessage: String = "",
    val isLoading: Boolean = false,
    val currentPinVisible: Boolean = false,
    val newPinVisible: Boolean = false,
    val confirmPinVisible: Boolean = false,
    val attemptsRemaining: Int = 4,
    val isCardBlocked: Boolean = false
)

