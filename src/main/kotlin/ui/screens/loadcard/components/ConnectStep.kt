package ui.screens.loadcard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConnectStepContent(
    isConnected: Boolean,
    onConnect: () -> Unit,
    onNext: () -> Unit
) {
    var isConnecting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Chưa kết nối") }
    
    // Update status message based on connection state
    LaunchedEffect(isConnected) {
        if (isConnected) {
            statusMessage = "✓ Đã kết nối với card reader"
            isConnecting = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Bước 1: Kết nối Java Card",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3)
        )

        // Status Card - Chỉ hiển thị khi chưa kết nối
        if (!isConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFFFFF3E0),
                elevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(32.dp)
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Java Card Reader",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = statusMessage,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Connect Button
        if (!isConnected) {
            Button(
                onClick = {
                    isConnecting = true
                    statusMessage = "Đang kết nối..."
                    onConnect()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3)),
                enabled = !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đang kết nối...", color = Color.White, fontSize = 14.sp)
                } else {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kết nối Java Card", color = Color.White, fontSize = 14.sp)
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFF4CAF50),
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Kết nối thành công!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Có thể tiếp tục bước kiểm tra thẻ",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Nút Tiếp tục
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
            ) {
                Text("Tiếp tục", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
            }
        }
        
        Divider()
        
        // Hướng dẫn
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color(0xFFE3F2FD),
            elevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Hướng dẫn sử dụng",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "1. Mở JCIDE Simulator",
                    fontSize = 12.sp,
                    color = Color(0xFF424242)
                )
                Text(
                    "2. Load và install applet lên simulator",
                    fontSize = 12.sp,
                    color = Color(0xFF424242)
                )
                Text(
                    "3. Nhấn 'Kết nối Java Card'",
                    fontSize = 12.sp,
                    color = Color(0xFF424242)
                )
                Text(
                    "4. Sau khi kết nối thành công, nhấn 'Tiếp tục'",
                    fontSize = 12.sp,
                    color = Color(0xFF424242)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "💡 Lưu ý: JCIDE Simulator phải đang chạy và applet đã được cài đặt",
                    fontSize = 11.sp,
                    color = Color(0xFF666666),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
