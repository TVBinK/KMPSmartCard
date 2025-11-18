package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import models.*
import ui.components.*
import smartcard.BusCardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Màn hình Xác thực loại vé & thời hạn
 * Hiển thị dưới dạng popup nhỏ hoặc panel bên phải
 */
@Composable
fun TicketValidationDialog(
    customer: Customer,
    onDismiss: () -> Unit
) {
    var currentCustomer by remember { mutableStateOf(customer) }
    var statusMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val cardStatus = currentCustomer.getCardStatus()
    val isValid = cardStatus == CardStatus.VALID
    val daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(), currentCustomer.expiryDate)
    
    Dialog(onDismissRequest = onDismiss) {
        CustomCard(
            modifier = Modifier.width(450.dp),
            backgroundColor = if (isValid) Color.White else Color(0xFFFFF5F5)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header với icon trạng thái
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (cardStatus) {
                            CardStatus.VALID -> Icons.Default.CheckCircle
                            CardStatus.EXPIRED -> Icons.Default.Close
                            CardStatus.INSUFFICIENT_BALANCE -> Icons.Default.Warning
                        },
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = when (cardStatus) {
                            CardStatus.VALID -> Color(0xFF4CAF50)
                            CardStatus.EXPIRED -> Color(0xFFF44336)
                            CardStatus.INSUFFICIENT_BALANCE -> Color(0xFFFF9800)
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = "XÁC THỰC THẺ",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3)
                    )
                }
                
                CustomDivider()
                
                // Thông tin khách hàng
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tên khách hàng
                    InfoCard(
                        icon = Icons.Default.Person,
                        label = "Khách hàng",
                        value = customer.fullName,
                        backgroundColor = Color(0xFFF5F5F5)
                    )
                    
                    // Card ID
                    InfoCard(
                        icon = Icons.Default.Info,
                        label = "Card ID",
                        value = customer.cardId,
                        backgroundColor = Color(0xFFF5F5F5)
                    )
                    
                    // Loại vé
                    InfoCard(
                        icon = Icons.Default.Info,
                        label = "Loại vé",
                        value = customer.cardType.displayName,
                        backgroundColor = Color(0xFFE3F2FD)
                    )
                    
                    // Đối tượng
                    InfoCard(
                        icon = Icons.Default.Person,
                        label = "Đối tượng",
                        value = customer.customerType.displayName,
                        backgroundColor = Color(0xFFE3F2FD)
                    )
                    
                    // Ngày hết hạn
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = when {
                            daysUntilExpiry < 0 -> Color(0xFFFFEBEE)
                            daysUntilExpiry <= 7 -> Color(0xFFFFF3E0)
                            else -> Color(0xFFE8F5E9)
                        },
                        elevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = when {
                                    daysUntilExpiry < 0 -> Color(0xFFF44336)
                                    daysUntilExpiry <= 7 -> Color(0xFFFF9800)
                                    else -> Color(0xFF4CAF50)
                                }
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ngày hết hạn",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                
                                Text(
                                    text = customer.expiryDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        daysUntilExpiry < 0 -> Color(0xFFC62828)
                                        daysUntilExpiry <= 7 -> Color(0xFFE65100)
                                        else -> Color(0xFF2E7D32)
                                    }
                                )
                                
                                Text(
                                    text = when {
                                        daysUntilExpiry < 0 -> "Đã hết hạn ${-daysUntilExpiry} ngày"
                                        daysUntilExpiry == 0L -> "Hết hạn hôm nay"
                                        daysUntilExpiry <= 7 -> "Còn $daysUntilExpiry ngày"
                                        else -> "Còn $daysUntilExpiry ngày"
                                    },
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    
                    // Số dư
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = if (customer.balance > 0) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                        elevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (customer.balance > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Số dư",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                
                                Text(
                                    text = "${String.format("%,.0f", customer.balance)} VNĐ",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (customer.balance > 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                                )
                            }
                        }
                    }
                }
                
                CustomDivider()
                
                // Trạng thái tổng quát
                StatusBadge(
                    text = when (cardStatus) {
                        CardStatus.VALID -> "Thẻ hợp lệ"
                        CardStatus.EXPIRED -> "Thẻ đã hết hạn"
                        CardStatus.INSUFFICIENT_BALANCE -> "Số dư không đủ"
                    },
                    isValid = isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .height(48.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Status message
                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        fontSize = 12.sp,
                        color = if (statusMessage.startsWith("✓")) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                
                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Đang xác thực...", fontSize = 12.sp)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Nút đọc từ thẻ
                CustomButton(
                    text = "Đọc & Xác thực từ thẻ",
                    onClick = {
                        scope.launch {
                            isLoading = true
                            statusMessage = "Đang đọc từ thẻ..."
                            
                            // Kết nối
                            if (!BusCardManager.isConnected) {
                                val connectResult = withContext(Dispatchers.IO) {
                                    BusCardManager.connect()
                                }
                                if (connectResult.isFailure) {
                                    statusMessage = "Lỗi kết nối: ${connectResult.exceptionOrNull()?.message}"
                                    isLoading = false
                                    return@launch
                                }
                            }
                            
                            // Đọc thông tin
                            val infoResult = withContext(Dispatchers.IO) {
                                BusCardManager.getCustomerInfo()
                            }
                            val balanceResult = withContext(Dispatchers.IO) {
                                BusCardManager.getBalance()
                            }
                            val cardIdResult = withContext(Dispatchers.IO) {
                                BusCardManager.getCardId()
                            }
                            
                            isLoading = false
                            
                            if (infoResult.isSuccess && balanceResult.isSuccess && cardIdResult.isSuccess) {
                                val info = infoResult.getOrNull()!!
                                val balance = balanceResult.getOrNull()!!
                                val cardId = cardIdResult.getOrNull()!!
                                
                                // Parse customer type
                                val customerType = when(info.customerType) {
                                    "HSSV" -> CustomerType.STUDENT
                                    "Người cao tuổi" -> CustomerType.ELDERLY
                                    else -> CustomerType.NORMAL
                                }
                                
                                // Parse card type
                                val cardType = when {
                                    info.cardType.contains("Tháng") -> CardType.MONTHLY
                                    else -> CardType.SINGLE_TRIP
                                }
                                
                                // Parse expiry date
                                val expiryDate = try {
                                    LocalDate.parse(info.expiryDate, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                } catch (e: Exception) {
                                    LocalDate.now().plusMonths(1)
                                }
                                
                                // Update customer
                                currentCustomer = currentCustomer.copy(
                                    fullName = info.fullName,
                                    customerType = customerType,
                                    cardType = cardType,
                                    expiryDate = expiryDate,
                                    balance = balance,
                                    cardId = cardId
                                )
                                
                                statusMessage = "✓ Đã xác thực thẻ thành công"
                            } else {
                                statusMessage = "Lỗi đọc thẻ: ${infoResult.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF4CAF50),
                    enabled = !isLoading
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Nút đóng
                CustomButton(
                    text = "Đóng",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Component hiển thị thông tin dạng card
 */
@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = backgroundColor,
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color(0xFF2196F3)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}

/**
 * Panel xác thực vé (không phải dialog)
 * Dùng để hiển thị bên cạnh màn hình chính
 */
@Composable
fun TicketValidationPanel(
    customer: Customer?,
    modifier: Modifier = Modifier
) {
    if (customer == null) {
        // Hiển thị placeholder khi chưa có khách hàng
        Card(
            modifier = modifier.width(350.dp).padding(8.dp),
            elevation = 4.dp,
            backgroundColor = Color(0xFFF5F5F5)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Quẹt thẻ để xem thông tin",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        val cardStatus = customer.getCardStatus()
        val isValid = cardStatus == CardStatus.VALID
        
        Card(
            modifier = modifier.width(350.dp).padding(8.dp),
            elevation = 4.dp,
            backgroundColor = if (isValid) Color.White else Color(0xFFFFF5F5)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "THÔNG TIN THẺ",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )
                
                CustomDivider()
                
                // Ảnh và tên
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ImagePlaceholder(
                        photoPath = customer.photoPath,
                        photoBytes = customer.photoBytes,
                        size = Pair(80, 100)
                    )
                    
                    Column {
                        Text(
                            text = customer.fullName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = customer.customerType.displayName,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
                
                CustomDivider()
                
                // Thông tin chi tiết
                InfoLabel(
                    label = "Loại vé",
                    value = customer.cardType.displayName
                )
                
                InfoLabel(
                    label = "Ngày hết hạn",
                    value = customer.expiryDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    valueColor = if (isValid) Color.Black else Color(0xFFF44336)
                )
                
                InfoLabel(
                    label = "Số dư",
                    value = "${String.format("%,.0f", customer.balance)} VNĐ",
                    valueColor = if (customer.balance > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    valueFontWeight = FontWeight.Bold
                )
                
                CustomDivider()
                
                // Trạng thái
                StatusBadge(
                    text = when (cardStatus) {
                        CardStatus.VALID -> "Hợp lệ"
                        CardStatus.EXPIRED -> "Hết hạn"
                        CardStatus.INSUFFICIENT_BALANCE -> "Không đủ số dư"
                    },
                    isValid = isValid,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

