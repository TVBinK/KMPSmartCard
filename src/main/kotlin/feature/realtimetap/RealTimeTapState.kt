package feature.realtimetap

import core.model.Customer
import java.time.LocalDateTime

/**
 * State của RealTimeTapScreen
 */
data class RealTimeTapState(
    val isListening: Boolean = true,
    val statusMessage: String = "Đang chờ quẹt thẻ...",
    val lastTapTime: LocalDateTime? = null,
    val currentCardId: String? = null,
    val detectedCustomer: Customer? = null,
    val cardHandled: Boolean = false
)

