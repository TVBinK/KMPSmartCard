package navigation

import androidx.compose.runtime.*
import feature.loadcard.LoadCardInfoDialog
import feature.payment.PaymentDialog
import feature.realtimetap.RealTimeTapDialog
import feature.route.RouteDialog
import feature.readcard.ReadSmartCardDialog
import feature.ticketvalidation.TicketValidationDialog
import core.model.Customer
import core.model.ExtensionRequest
import core.model.TapType
import core.ui.dialogs.CustomerCardInfoDialog
import core.ui.dialogs.DeleteConfirmDialog
import core.ui.dialogs.EditCustomerDialog


/**
 * Navigation Host - Quản lý việc hiển thị các screen dựa trên current screen
 */
@Composable
fun NavigationHost(
    navController: NavController,
    customers: List<Customer>,
    onCustomerUpdated: () -> Unit,
    onCustomerDeleted: (Customer) -> Unit,
    onTopUpCompleted: (String, Double) -> Unit,
    onTapDetected: (String, TapType) -> Unit,
    onExtensionRequest: (ExtensionRequest) -> Unit,
    onCardDataWritten: () -> Unit = {}
) {
    // selectedCustomer, isCardConnected, isCardHasData được truyền vào nhưng chưa sử dụng
    // Có thể dùng trong tương lai để enable/disable các tính năng
    val currentScreen = navController.currentScreen.value
    
    when (currentScreen) {
        is Screen.Home -> {
            // Home screen được render trong MainApp
            // Không cần render ở đây
        }
        
        is Screen.LoadCardInfo -> {
            LoadCardInfoDialog(
                onDismiss = { 
                    navController.navigateBack()
                },
                onSuccess = { customer ->
                    onCustomerUpdated()
                    onCardDataWritten() // Refresh trạng thái thẻ sau khi ghi thành công
                    navController.navigateBack()
                }
            )
        }
        
        is Screen.Payment -> {
            PaymentDialog(
                onDismiss = { navController.navigateBack() },
                customers = customers,
                onTopUp = { cardId, amount -> 
                    onTopUpCompleted(cardId, amount)
                },
                onExtension = { request -> 
                    onExtensionRequest(request)
                }
            )
        }
        
        is Screen.Route -> {
            RouteDialog(
                onDismiss = { navController.navigateBack() },
                onRouteSelected = { _, _, _ -> navController.navigateBack() }
            )
        }
        
        is Screen.SmartCardManagement -> {
            ReadSmartCardDialog(
                onDismiss = { navController.navigateBack() }
            )
        }
        
        is Screen.RealTimeTap -> {
            RealTimeTapDialog(
                onDismiss = { navController.navigateBack() },
                onTapDetected = { cardId, tapType -> 
                    onTapDetected(cardId, tapType)
                }
            )
        }
        
        is Screen.CustomerCardInfo -> {
            CustomerCardInfoDialog(
                customer = currentScreen.customer,
                onDismiss = { navController.navigateBack() },
                onEdit = { customer ->
                    navController.navigateTo(Screen.EditCustomer(customer))
                },
                onDelete = { customer ->
                    navController.navigateTo(Screen.DeleteConfirm(customer))
                }
            )
        }
        
        is Screen.EditCustomer -> {
            EditCustomerDialog(
                customer = currentScreen.customer,
                onDismiss = { navController.navigateBack() },
                onSave = { updatedCustomer ->
                    onCustomerUpdated()
                    navController.navigateBack()
                }
            )
        }
        
        is Screen.DeleteConfirm -> {
            DeleteConfirmDialog(
                customer = currentScreen.customer,
                onDismiss = { navController.navigateBack() },
                onConfirm = { customer ->
                    onCustomerDeleted(customer)
                    navController.navigateBack()
                }
            )
        }
        
        is Screen.TicketValidation -> {
            TicketValidationDialog(
                customer = currentScreen.customer,
                onDismiss = { navController.navigateBack() }
            )
        }
    }
}

