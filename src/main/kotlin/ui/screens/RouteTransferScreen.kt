package ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

/**
 * Màn hình Chuyển tuyến
 * Hiển thị khi thẻ được quẹt trong thời gian chuyển tuyến
 */
@Composable
fun RouteTransferDialog(
    customer: Customer,
    currentTrip: Trip,
    availableRoutes: List<BusRoute>,
    onTransfer: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRoute by remember { mutableStateOf<BusRoute?>(null) }
    var remainingTime by remember { mutableStateOf(currentTrip.remainingTransferTime()) }
    
    // Đếm ngược thời gian
    LaunchedEffect(Unit) {
        while (remainingTime > 0) {
            delay(1000)
            remainingTime = currentTrip.remainingTransferTime()
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        CustomCard(
            modifier = Modifier.width(550.dp),
            backgroundColor = Color(0xFFFFF9E6)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFFFF9800)
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = "CHUYỂN TUYẾN",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9800)
                        )
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = Color.Gray
                        )
                    }
                }
                
                CustomDivider()
                
                // Thông tin khách hàng
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ImagePlaceholder(
                        photoPath = customer.photoPath,
                        photoBytes = customer.photoBytes,
                        size = Pair(70, 85)
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = customer.fullName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Text(
                            text = "Card ID: ${customer.cardId}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        
                        Text(
                            text = "Số dư: ${String.format("%,.0f", customer.balance)} VNĐ",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
                
                CustomDivider()
                
                // Thông tin tuyến hiện tại
                CustomCard(backgroundColor = Color(0xFFE3F2FD)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Tuyến hiện tại",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color(0xFF2196F3)
                            )
                            
                            Text(
                                text = currentTrip.tapOn.routeName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2196F3)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Chọn tuyến mới
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Chọn tuyến mới",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    CustomDropdown(
                        label = "Tuyến xe",
                        items = availableRoutes,
                        selectedItem = selectedRoute ?: availableRoutes.firstOrNull() ?: BusRoute("", ""),
                        onItemSelected = { selectedRoute = it },
                        itemLabel = { "${it.id} - ${it.name}" }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Thời gian quẹt gần nhất
                InfoLabel(
                    label = "Thời gian quẹt gần nhất",
                    value = currentTrip.tapOn.timestamp.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                )
                
                // Thời gian còn lại (với animation)
                CountdownTimer(
                    remainingMinutes = remainingTime,
                    totalMinutes = 30
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Quy tắc chuyển tuyến
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFFF5F5F5),
                    elevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFF2196F3)
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = "Lưu ý: Bạn có thể chuyển tuyến miễn phí trong vòng 30 phút kể từ lần quẹt thẻ đầu tiên",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )
                    }
                }
                
                CustomDivider()
                
                // Nút hành động
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CancelButton(
                        text = "Hủy",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                    
                    ConfirmButton(
                        text = "Xác nhận chuyển tuyến",
                        onClick = {
                            if (selectedRoute != null) {
                                onTransfer(selectedRoute!!.id)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = selectedRoute != null && remainingTime > 0
                    )
                }
                
                // Cảnh báo nếu hết thời gian
                if (remainingTime <= 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFFFFEBEE)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = Color(0xFFF44336)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = "Hết thời gian chuyển tuyến miễn phí. Bạn cần thanh toán phí mới.",
                                fontSize = 12.sp,
                                color = Color(0xFFC62828),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Component đếm ngược thời gian
 */
@Composable
private fun CountdownTimer(
    remainingMinutes: Long,
    totalMinutes: Long,
    modifier: Modifier = Modifier
) {
    val progress = (remainingMinutes.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f)
    val color = when {
        remainingMinutes > 15 -> Color(0xFF4CAF50)
        remainingMinutes > 5 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    
    // Animation cho text khi thời gian sắp hết
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = if (remainingMinutes <= 5) 0.3f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = Color(0xFFFFF9E6),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = color
                        )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = "Thời gian còn lại",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                Text(
                    text = formatTime(remainingMinutes),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = color.copy(alpha = if (remainingMinutes <= 5) alpha else 1f)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress bar
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = color,
                backgroundColor = Color.Gray.copy(alpha = 0.2f)
            )
        }
    }
}

/**
 * Format thời gian thành mm:ss
 */
private fun formatTime(totalMinutes: Long): String {
    val minutes = totalMinutes
    val seconds = 0  // Simplified - in real app would track seconds too
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Widget đơn giản hiển thị thông báo chuyển tuyến có sẵn
 */
@Composable
fun RouteTransferAvailableNotification(
    remainingMinutes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(8.dp),
        backgroundColor = Color(0xFFFFF3E0),
        elevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = Color(0xFFFF9800)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = "Chuyển tuyến miễn phí",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                    
                    Text(
                        text = "Còn $remainingMinutes phút",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
            
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFFF9800)
                )
            ) {
                Text("Chuyển", color = Color.White)
            }
        }
    }
}

