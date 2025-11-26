package feature.smartcard

import core.model.Customer

/**
 * State của SmartCardManagementScreen
 */
data class SmartCardManagementState(
    val customers: List<Customer> = emptyList(),
    val selectedCustomer: Customer? = null,
    val transactions: List<Map<String, Any>> = emptyList(),
    val selectedTab: Int = 0,
    val isConnected: Boolean = false,
    val cardReaderStatus: String = "Chưa kết nối",
    val isReadingCard: Boolean = false,
    val showChangePinDialog: Boolean = false
)

