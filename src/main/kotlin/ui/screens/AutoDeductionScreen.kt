package ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import models.*
import ui.components.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Màn hình Trừ tiền tự động theo chuyến/chặng
 * Hiển thị khi quẹt thẻ (tap on/tap off)
 */
@Composable
fun AutoDeductionDialog(
    customer: Customer,
    tapType: TapType,
    tapInfo: TapInfo,
    onDismiss: () -> Unit
) {
    // Animation cho icon
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Dialog(onDismissRequest = onDismiss) {
        CustomCard(
            modifier = Modifier.width(500.dp),
            backgroundColor = if (customer.isValid()) Color(0xFFF1F8F4) else Color(0xFFFFF1F1)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon trạng thái
                Surface(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(scale),
                    shape = CircleShape,
                    color = if (customer.isValid()) Color(0xFF4CAF50) else Color(0xFFF44336)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (customer.isValid()) Icons.Default.CheckCircle else Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp),
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Thông báo trạng thái
                Text(
                    text = when {
                        !customer.isValid() && customer.getCardStatus() == CardStatus.EXPIRED -> "THẺ ĐÃ HẾT HẠN"
                        !customer.isValid() && customer.getCardStatus() == CardStatus.INSUFFICIENT_BALANCE -> "SỐ DƯ KHÔNG ĐỦ"
                        tapType == TapType.TAP_ON -> "VÉ HỢP LỆ - ĐÃ QUẸT LÊN"
                        else -> "VÉ HỢP LỆ - ĐÃ TRỪ TIỀN"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (customer.isValid()) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
                
                CustomDivider()
                
                // Ảnh và thông tin khách hàng
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Ảnh khách hàng
                    ImagePlaceholder(
                        photoPath = customer.photoPath,
                        photoBytes = customer.photoBytes,
                        size = Pair(100, 120)
                    )
                    
                    // Thông tin
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = customer.fullName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )
                        
                        InfoLabel(
                            label = "Loại thẻ",
                            value = customer.cardType.displayName
                        )
                        
                        InfoLabel(
                            label = "Đối tượng",
                            value = customer.customerType.displayName
                        )
                        
                        InfoLabel(
                            label = "Số dư",
                            value = "${String.format("%,.0f", customer.balance)} VNĐ",
                            valueColor = if (customer.balance > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                            valueFontWeight = FontWeight.Bold
                        )
                    }
                }
                
                CustomDivider()
                
                // Thông tin chuyến đi
                when (tapType) {
                    TapType.TAP_ON -> TapOnInfo(tapInfo as TapOnInfo)
                    TapType.TAP_OFF -> TapOffInfo(tapInfo as TapOffInfo)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Nút đóng
                CustomButton(
                    text = "Đóng",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF2196F3)
                )
            }
        }
    }
    
    // Tự động đóng sau 5 giây
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5000)
        onDismiss()
    }
}

/**
 * Hiển thị thông tin khi quẹt lên
 */
@Composable
private fun TapOnInfo(info: TapOnInfo) {
    CustomCard(backgroundColor = Color(0xFFE3F2FD)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "THÔNG TIN QUẸT LÊN",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1976D2)
            )
            
            InfoLabel(
                label = "Tuyến/Vị trí",
                value = info.routeName,
                valueFontWeight = FontWeight.Bold
            )
            
            InfoLabel(
                label = "Thời gian quẹt",
                value = info.timestamp.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            )
        }
    }
}

/**
 * Hiển thị thông tin khi quẹt xuống
 */
@Composable
private fun TapOffInfo(info: TapOffInfo) {
    CustomCard(backgroundColor = Color(0xFFFFF3E0)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "THÔNG TIN CHUYẾN ĐI",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6F00)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    InfoLabel(
                        label = "Điểm lên",
                        value = info.tapOnRoute
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).align(Alignment.CenterVertically),
                    tint = Color(0xFFFF6F00)
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    InfoLabel(
                        label = "Điểm xuống",
                        value = info.tapOffRoute
                    )
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoLabel(
                    label = "Số chặng",
                    value = "${info.numberOfStops} chặng",
                    valueFontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                InfoLabel(
                    label = "Mức phí",
                    value = "${String.format("%,.0f", info.fare)} VNĐ",
                    valueColor = Color(0xFFF44336),
                    valueFontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            
            CustomDivider()
            
            // Số tiền bị trừ và số dư còn lại
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoLabel(
                    label = "Số tiền đã trừ",
                    value = "- ${String.format("%,.0f", info.deductedAmount)} VNĐ",
                    valueColor = Color(0xFFF44336),
                    valueFontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                InfoLabel(
                    label = "Số dư còn lại",
                    value = "${String.format("%,.0f", info.remainingBalance)} VNĐ",
                    valueColor = if (info.remainingBalance > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    valueFontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            
            InfoLabel(
                label = "Thời gian",
                value = info.timestamp.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            )
        }
    }
}

/**
 * Loại quẹt thẻ
 */
enum class TapType {
    TAP_ON,   // Quẹt lên
    TAP_OFF   // Quẹt xuống
}

/**
 * Interface cho thông tin quẹt thẻ
 */
sealed interface TapInfo

/**
 * Thông tin quẹt lên
 */
data class TapOnInfo(
    val routeName: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
) : TapInfo

/**
 * Thông tin quẹt xuống
 */
data class TapOffInfo(
    val tapOnRoute: String,
    val tapOffRoute: String,
    val numberOfStops: Int,
    val fare: Double,
    val deductedAmount: Double,
    val remainingBalance: Double,
    val timestamp: LocalDateTime = LocalDateTime.now()
) : TapInfo

/**
 * Widget hiển thị nhanh khi quẹt thẻ (không phải dialog)
 * Dùng cho UI nhúng vào màn hình chính
 */
@Composable
fun AutoDeductionWidget(
    customer: Customer?,
    tapType: TapType?,
    tapInfo: TapInfo?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = customer != null && tapType != null && tapInfo != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (customer != null && tapType != null && tapInfo != null) {
            Card(
                modifier = modifier
                    .width(400.dp)
                    .padding(16.dp),
                elevation = 8.dp,
                backgroundColor = if (customer.isValid()) Color(0xFFF1F8F4) else Color(0xFFFFF1F1)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (customer.isValid()) Icons.Default.CheckCircle else Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = if (customer.isValid()) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = customer.fullName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Text(
                                text = if (customer.isValid()) "Vé hợp lệ" else "Vé không hợp lệ",
                                fontSize = 12.sp,
                                color = if (customer.isValid()) Color(0xFF4CAF50) else Color(0xFFF44336)
                            )
                        }
                        
                        Text(
                            text = "${String.format("%,.0f", customer.balance)} VNĐ",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (customer.balance > 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }
            }
        }
    }
}

