package feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.lightColors
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import navigation.NavController
import navigation.NavigationHost
import navigation.Screen
import core.ui.AppSpacing

/**
 * Màn hình chính của ứng dụng
 */
@Composable
fun MainApp() {
    // Khởi tạo ViewModel - quản lý tất cả state và logic nghiệp vụ
    val mainViewModel = remember { MainViewModel() }
    
    // Collect state từ ViewModel
    val state by mainViewModel.state.collectAsState()
    
    // Navigation Controller
    val navController = remember { NavController(Screen.Home) }
    
    // Coroutine scope cho các operations
    val coroutineScope = rememberCoroutineScope()
    
    // Cleanup khi Composable bị dispose
    DisposableEffect(Unit) {
        onDispose {
            mainViewModel.onCleared()
        }
    }
    MaterialTheme(
        colors = lightColors(
            primary = Color(0xFF6366F1),
            primaryVariant = Color(0xFF4F46E5),
            secondary = Color(0xFF10B981),
            background = Color(0xFFF9FAFB)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFEEF2FF),
                            Color(0xFFF9FAFB)
                        )
                    )
                )
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Bên trái: Menu chức năng (65%)
                Column(
                    modifier = Modifier
                        .weight(6.5f)
                        .fillMaxHeight()
                        .padding(start = 20.dp, top = 20.dp, end = 10.dp, bottom = 20.dp)
                ) {
                    // Header
                    MainHeader(customerCount = state.customers.size)
                    
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    
                    // Menu buttons
                    MainMenu(
                        navController = navController,
                        isCardConnected = state.isCardConnected,
                        isCardHasData = state.isCardHasData,
                        selectedCustomer = state.selectedCustomer,
                        customers = state.customers
                    )
                    
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    
                    // Hoạt động gần đây
                    RecentActivitySection(recentActivities = state.recentActivities)
                }
                
                // Bên phải: Danh sách khách hàng (35%)
                Column(
                    modifier = Modifier
                        .weight(3.5f)
                        .fillMaxHeight()
                        .padding(start = 10.dp, top = 20.dp, end = 20.dp, bottom = 20.dp)
                ) {
                    CustomerListSection(
                        customers = state.customers,
                        isLoading = state.isLoading,
                        searchQuery = state.searchQuery,
                        onSearchQueryChange = { mainViewModel.updateSearchQuery(it) },
                        onCustomerClick = { customer ->
                            mainViewModel.updateSelectedCustomer(customer.cardId)
                            navController.navigateTo(Screen.CustomerCardInfo(customer))
                        }
                    )
                }
            }
        }
        // Navigation Host - Quản lý tất cả các screen
        NavigationHost(
            navController = navController,
            customers = state.customers,
            onCustomerUpdated = { mainViewModel.refreshCustomers() },
            onCustomerDeleted = { customer ->
                mainViewModel.deleteCustomer(customer)
            },
            onTopUpCompleted = { cardId, amount ->
                mainViewModel.handleTopUpTransaction(cardId, amount)
            },
            onTapDetected = { cardId ->
                // RealTimeTapViewModel đã tự xử lý quét thẻ
                // Chỉ cần refresh customers để cập nhật UI
                coroutineScope.launch {
                    mainViewModel.refreshCustomers()
                }
            },
            onExtensionRequest = { request ->
                mainViewModel.handleMonthlyExtension(request)
            },
            onCardDataWritten = {
                // Refresh trạng thái thẻ sau khi ghi dữ liệu lên thẻ thành công
                coroutineScope.launch {
                    mainViewModel.refreshCardStatus()
                }
            }
        )
    }
}
