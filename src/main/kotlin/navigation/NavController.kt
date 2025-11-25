package navigation

import androidx.compose.runtime.*

/**
 * Navigation Controller để quản lý navigation giữa các màn hình
 */
class NavController(
    initialScreen: Screen,
    private val onNavigate: (Screen) -> Unit = {}
) {
    private val _currentScreen = mutableStateOf(initialScreen)
    val currentScreen: State<Screen> = _currentScreen
    
    private val backStack = mutableListOf<Screen>()
    
    /**
     * Navigate đến một screen mới
     */
    fun navigateTo(screen: Screen) {
        backStack.add(_currentScreen.value)
        _currentScreen.value = screen
        onNavigate(screen)
    }
    
    /**
     * Navigate back về screen trước đó
     */
    fun navigateBack(): Boolean {
        return if (backStack.isNotEmpty()) {
            _currentScreen.value = backStack.removeAt(backStack.size - 1)
            onNavigate(_currentScreen.value)
            true
        } else {
            false
        }
    }

    
    /**
     * Replace current screen (không thêm vào back stack)
     */
    fun replace(screen: Screen) {
        _currentScreen.value = screen
        onNavigate(screen)
    }
}

/**
 * Sealed class định nghĩa các màn hình trong ứng dụng
 */
sealed class Screen(val route: String, val title: String) {
    object Home : Screen("home", "Trang chủ")
    object LoadCardInfo : Screen("load_card_info", "Nạp thông tin vào thẻ")
    object Payment : Screen("payment", "Nạp tiền - Gia hạn")
    object Route : Screen("route", "Lộ Trình")
    object SmartCardManagement : Screen("smart_card", "Quản lý Smart Card")
    object RealTimeTap : Screen("real_time_tap", "Quẹt thẻ tự động")
    
    data class CustomerCardInfo(val customer: models.Customer) : Screen("customer_card_info", "Thông tin thẻ")
    data class EditCustomer(val customer: models.Customer) : Screen("edit_customer", "Sửa thông tin khách hàng")
    data class DeleteConfirm(val customer: models.Customer) : Screen("delete_confirm", "Xác nhận xóa")
    data class TicketValidation(val customer: models.Customer) : Screen("ticket_validation", "Xác thực vé")
}

