package ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import models.CardType
import models.Customer
import models.CustomerType
import ui.AppColors
import ui.AppSpacing
import ui.components.ImagePlaceholder
import ui.components.InfoRow
import ui.components.PrimaryButton

/**
 * Customer Card Info Dialog - Hiển thị thông tin thẻ khi click vào khách hàng
 */
@Composable
fun CustomerCardInfoDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onEdit: (Customer) -> Unit = {},
    onDelete: (Customer) -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .width(500.dp)
                .padding(16.dp),
            elevation = 8.dp,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "THÔNG TIN THẺ",
                        fontSize = 20.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = AppColors.Primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Photo
                ImagePlaceholder(
                    photoPath = customer.photoPath,
                    photoBytes = customer.photoBytes,
                    size = Pair(120, 140)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Card ID
                Text(
                    text = customer.cardId,
                    fontSize = 24.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = Color(0xFF212121)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Customer Type Label
                Text(
                    text = when (customer.customerType) {
                        CustomerType.STUDENT -> "Học sinh/Sinh viên"
                        CustomerType.ELDERLY -> "Người cao tuổi"
                        CustomerType.NORMAL -> "Thông thường"
                    },
                    fontSize = 14.sp,
                    color = Color(0xFF757575)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Info Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // CCCD
                    if (customer.cccd.isNotEmpty()) {
                        InfoRow(
                            label = "CCCD",
                            value = customer.cccd
                        )
                    }

                    // Ngày sinh
                    if (customer.dob.isNotEmpty()) {
                        InfoRow(
                            label = "Ngày sinh",
                            value = customer.dob
                        )
                    }

                    // Địa chỉ
                    if (customer.address.isNotEmpty()) {
                        InfoRow(
                            label = "Địa chỉ",
                            value = customer.address
                        )
                    }

                    // Số điện thoại
                    if (customer.phone.isNotEmpty()) {
                        InfoRow(
                            label = "Số điện thoại",
                            value = customer.phone
                        )
                    }

                    Divider()

                    // Card Type
                    InfoRow(
                        label = "Loại thẻ",
                        value = when (customer.cardType) {
                            CardType.NORMAL -> "Thẻ Thường"
                            CardType.MONTHLY -> "Thẻ Tháng"
                        }
                    )

                    // Expiry Date (chỉ hiển thị nếu là thẻ tháng)
                    if (customer.cardType == CardType.MONTHLY) {
                        InfoRow(
                            label = "Ngày hết hạn",
                            value = customer.expiryDate.toString().replace("-", "/")
                        )
                    }
                    // Thẻ thường không có ngày hết hạn

                    // Balance
                    InfoRow(
                        label = "Số dư",
                        value = "${String.format("%,.0f", customer.balance)} VND",
                        valueColor = Color(0xFF4CAF50)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons - Edit và Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                ) {
                    // Edit Button với gradient
                    PrimaryButton(
                        text = "Sửa",
                        onClick = { onEdit(customer) },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Edit,
                        gradientStart = AppColors.BlueGradientStart,
                        gradientEnd = AppColors.BlueGradientEnd
                    )

                    // Delete Button với gradient
                    PrimaryButton(
                        text = "Xóa",
                        onClick = { onDelete(customer) },
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Delete,
                        gradientStart = AppColors.Error,
                        gradientEnd = Color(0xFFFF5252)
                    )
                }
            }
        }
    }
}

