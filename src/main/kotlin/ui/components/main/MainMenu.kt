package ui.components.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import models.Customer
import navigation.NavController
import navigation.Screen
import ui.AppColors
import ui.AppElevation
import ui.AppSpacing
import ui.AppTypography
import ui.components.GlassCard
import ui.components.MenuButton

@Composable
fun MainMenu(
    navController: NavController,
    isCardConnected: Boolean,
    isCardHasData: Boolean,
    selectedCustomer: Customer?,
    customers: List<Customer>
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = AppElevation.sm
    ) {
        Text(
            text = "MENU CHỨC NĂNG",
            fontSize = AppTypography.h4Size,
            fontWeight = FontWeight.Bold,
            color = AppColors.Primary
        )
        Spacer(modifier = Modifier.height(AppSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            MenuButton(
                text = "Nạp thông tin vào thẻ",
                icon = Icons.Default.Add,
                onClick = { navController.navigateTo(Screen.LoadCardInfo) },
                modifier = Modifier.weight(1f),
                gradientStart = AppColors.PurpleGradientStart,
                gradientEnd = AppColors.PurpleGradientEnd
            )
            MenuButton(
                text = "Nạp tiền - Gia hạn",
                icon = Icons.Default.ShoppingCart,
                onClick = {
                    val customer = selectedCustomer ?: customers.firstOrNull()
                    // Nếu không có customer nào được chọn, vẫn cho vào màn hình Payment
                    // Màn hình Payment sẽ tự xử lý việc chọn customer
                    navController.navigateTo(Screen.Payment)
                },
                modifier = Modifier.weight(1f),
                enabled = isCardConnected && isCardHasData,
                gradientStart = AppColors.BlueGradientStart,
                gradientEnd = AppColors.BlueGradientEnd
            )
        }
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            MenuButton(
                text = "Quản lý Smart Card",
                icon = Icons.Default.AccountBox,
                onClick = { navController.navigateTo(Screen.SmartCardManagement) },
                modifier = Modifier.weight(1f),
                enabled = isCardConnected && isCardHasData,
                gradientStart = AppColors.IndigoGradientStart,
                gradientEnd = AppColors.IndigoGradientEnd
            )
            MenuButton(
                text = "Lộ Trình",
                icon = Icons.Default.Send,
                onClick = {
                    navController.navigateTo(Screen.Route)
                },
                modifier = Modifier.weight(1f),
                enabled = isCardConnected && isCardHasData,
                gradientStart = AppColors.GreenGradientStart,
                gradientEnd = AppColors.GreenGradientEnd
            )
        }
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            MenuButton(
                text = "Quẹt thẻ tự động",
                icon = Icons.Default.Star,
                onClick = { navController.navigateTo(Screen.RealTimeTap) },
                modifier = Modifier.weight(1f),
                enabled = isCardConnected && isCardHasData,
                gradientStart = AppColors.OrangeGradientStart,
                gradientEnd = AppColors.OrangeGradientEnd
            )
            MenuButton(
                text = "Xác thực vé",
                icon = Icons.Default.CheckCircle,
                onClick = {
                    val customer = selectedCustomer ?: customers.firstOrNull()
                    if (customer != null) {
                        navController.navigateTo(Screen.TicketValidation(customer))
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = isCardConnected && isCardHasData && (selectedCustomer != null || customers.isNotEmpty()),
                gradientStart = AppColors.TealGradientStart,
                gradientEnd = AppColors.TealGradientEnd
            )
        }
    }
}
