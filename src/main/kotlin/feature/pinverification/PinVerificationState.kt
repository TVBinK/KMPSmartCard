package feature.pinverification

/**
 * State của PinVerificationDialog
 */
data class PinVerificationState(
    val pin: String = "",
    val errorMessage: String = "",
    val isLoading: Boolean = false,
    val attemptsRemaining: Int = 4,
    val isCardBlocked: Boolean = false,
    val pinVisible: Boolean = false
)

