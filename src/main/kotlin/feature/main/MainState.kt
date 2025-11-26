package feature.main

import core.model.Customer

/**
 * State của MainScreen
 * Quản lý tất cả state cần thiết cho màn hình chính
 */
data class MainState(
    val customers: List<Customer> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val recentActivities: List<Map<String, Any>> = emptyList(),
    val selectedCustomer: Customer? = null,
    val isCardConnected: Boolean = false,
    val isCardHasData: Boolean = false
)

