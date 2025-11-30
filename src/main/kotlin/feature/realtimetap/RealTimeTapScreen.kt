package feature.realtimetap

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import core.ui.components.CustomCard
import core.ui.components.CustomDivider
import java.time.format.DateTimeFormatter

/**
 * Màn hình Quẹt thẻ thực tế - Tự động phát hiện khi có thẻ
 * Sử dụng MVVM pattern
 */
@Composable
fun RealTimeTapDialog(
    onDismiss: () -> Unit,
    onTapDetected: (String) -> Unit
) {
    // Khởi tạo ViewModel
    val realTimeTapViewModel = remember { RealTimeTapViewModel(onTapDetected) }
    val state by realTimeTapViewModel.state.collectAsState()
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            realTimeTapViewModel.onCleared()
        }
    }
    
    Dialog(
        onDismissRequest = {
            realTimeTapViewModel.stopPolling()
            onDismiss()
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        CustomCard(
            modifier = Modifier.width(500.dp),
            backgroundColor = Color(0xFFF5F5F5)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quẹt thẻ tự động",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3)
                    )
                    
                    IconButton(onClick = {
                        realTimeTapViewModel.stopPolling()
                        onDismiss()
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }
                
                CustomDivider()
                
                // Animated Scanner Icon
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Card Scanner Icon with Animation - Hiển thị avatar nếu có
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer pulsing ring
                    Surface(
                        modifier = Modifier
                            .size(140.dp)
                            .scale(scale),
                        shape = CircleShape,
                        color = if (state.detectedCustomer != null) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color(0xFF2196F3).copy(alpha = 0.15f)
                    ) {}
                    
                    // Middle ring
                    Surface(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(if (scale > 1f) scale - 0.1f else scale + 0.1f),
                        shape = CircleShape,
                        color = if (state.detectedCustomer != null) Color(0xFF4CAF50).copy(alpha = 0.25f) else Color(0xFF2196F3).copy(alpha = 0.25f)
                    ) {}
                    
                    // Avatar hoặc icon mặc định
                    if (state.detectedCustomer?.photoBytes != null) {
                        // Hiển thị ảnh avatar
                        val imageBitmap = remember(state.detectedCustomer?.photoBytes) {
                            try {
                                org.jetbrains.skia.Image.makeFromEncoded(state.detectedCustomer!!.photoBytes!!).asImageBitmap()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        
                        if (imageBitmap != null) {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = CircleShape,
                                color = Color.White,
                                elevation = 8.dp
                            ) {
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            // Fallback icon nếu load ảnh lỗi
                            DefaultCardIcon(state.detectedCustomer != null)
                        }
                    } else {
                        // Icon mặc định
                        DefaultCardIcon(state.detectedCustomer != null)
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Status
                Text(
                    text = state.statusMessage,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF424242)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Last tap info
                state.lastTapTime?.let { lastTapTime ->
                    Text(
                        text = "Lần quẹt gần nhất: ${lastTapTime.format(DateTimeFormatter.ofPattern("HH:mm:ss"))}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                CustomDivider()
                
                // Instructions
                CustomCard(backgroundColor = Color(0xFFE3F2FD)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF2196F3),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Hướng dẫn sử dụng",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2196F3)
                            )
                        }
                        
                        Text(
                            "1. Đảm bảo đã kết nối Java Card (JCIDE Simulator hoặc card thật)",
                            fontSize = 12.sp,
                            color = Color(0xFF424242)
                        )
                        Text(
                            "2. Nạp thông tin khách hàng vào thẻ (để có Card ID)",
                            fontSize = 12.sp,
                            color = Color(0xFF424242)
                        )
                        Text(
                            "3. Thẻ sẽ được tự động phát hiện và trừ tiền",
                            fontSize = 12.sp,
                            color = Color(0xFF424242)
                        )
                        
                        Spacer(Modifier.height(4.dp))
                        
                        Text(
                            "💡 Lưu ý: JCIDE Simulator chỉ lưu 1 thẻ tại 1 thời điểm. Mỗi lần nạp thông tin mới sẽ thay thế thẻ cũ.",
                            fontSize = 11.sp,
                            color = Color(0xFF666666),
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultCardIcon(isDetected: Boolean) {
    Surface(
        modifier = Modifier.size(80.dp),
        shape = CircleShape,
        color = if (isDetected) Color(0xFF4CAF50) else Color(0xFF2196F3),
        elevation = 8.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isDetected) Icons.Default.Check else Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.White
            )
        }
    }
}

