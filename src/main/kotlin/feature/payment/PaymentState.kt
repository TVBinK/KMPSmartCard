package feature.payment

import core.model.Customer
import core.model.ExtensionRequest

/**
 * State cho TopUp Tab
 */
data class TopUpState(
    val cardId: String = "",
    val selectedCustomer: Customer? = null,
    val topUpAmount: String = "",
    val statusMessage: String = "",
    val isLoading: Boolean = false,
    val showPinDialog: Boolean = false,
    val pendingAmount: Double = 0.0,
    val showSuggestions: Boolean = false
)

/**
 * State cho Extension Tab
 */
data class ExtensionState(
    val cardId: String = "",
    val selectedCustomer: Customer? = null,
    val quantity: String = "1",
    val amount: Double = 100000.0, // Giá vé tháng: 100.000đ
    val showPinDialog: Boolean = false,
    val pendingRequest: ExtensionRequest? = null,
    val showSuggestions: Boolean = false
)

/**
 * State tổng của PaymentScreen
 */
data class PaymentState(
    val selectedTab: Int = 0,
    val topUpState: TopUpState = TopUpState(),
    val extensionState: ExtensionState = ExtensionState()
)

