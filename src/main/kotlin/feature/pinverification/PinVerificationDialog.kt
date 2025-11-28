package feature.pinverification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

/**
 * Dialog xác thực PIN với giới hạn số lần nhập sai
 * Sử dụng MVVM pattern
 * 
 * @param onVerified Callback khi PIN đúng
 * @param onDismiss Callback khi đóng dialog
 * @param maxAttempts Số lần nhập sai tối đa (mặc định 4)
 */
@Composable
fun PinVerificationDialog(
    onVerified: (String) -> Unit,
    onDismiss: () -> Unit,
    maxAttempts: Int = 4,
    title: String = "Xác thực mã PIN"
) {
    // Khởi tạo ViewModel
    val pinVerificationViewModel = remember { 
        PinVerificationViewModel(
            maxAttempts = maxAttempts,
            onVerified = onVerified
        )
    }
    val state by pinVerificationViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            pinVerificationViewModel.onCleared()
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(400.dp)
                .padding(16.dp),
            elevation = 8.dp,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2196F3)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Icon
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = if (state.isCardBlocked) Color(0xFFF44336) else Color(0xFF2196F3)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Thông báo thẻ bị khóa
                if (state.isCardBlocked) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFFFFEBEE),
                        elevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF44336),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Thẻ đã bị khóa!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF44336)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Bạn đã nhập sai PIN quá nhiều lần.\nVui lòng liên hệ quản trị viên để mở khóa.",
                                fontSize = 12.sp,
                                color = Color(0xFF757575),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF757575)
                        )
                    ) {
                        Text("Đóng", color = Color.White)
                    }
                } else {
                    // Form nhập PIN
                    Text(
                        text = "Vui lòng nhập mã PIN để tiếp tục",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // PIN Input
                    OutlinedTextField(
                        value = state.pin,
                        onValueChange = { pinVerificationViewModel.updatePin(it) },
                        label = { Text("Mã PIN") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { pinVerificationViewModel.togglePinVisibility() }) {
                                Text(
                                    text = if (state.pinVisible) "Ẩn" else "Hiện",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (state.pinVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        isError = state.errorMessage.isNotEmpty(),
                        enabled = !state.isLoading && state.attemptsRemaining > 0
                    )
                    
                    // Thông báo lỗi
                    if (state.errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Color(0xFFFFEBEE),
                            elevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFF44336),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = state.errorMessage,
                                    fontSize = 12.sp,
                                    color = Color(0xFFF44336)
                                )
                            }
                        }
                    }
                    
                    // Hiển thị số lần còn lại
                    if (state.attemptsRemaining < maxAttempts) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Còn ${state.attemptsRemaining} lần thử",
                            fontSize = 12.sp,
                            color = if (state.attemptsRemaining <= 1) Color(0xFFF44336) else Color(0xFFFF9800),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF9E9E9E)
                            )
                        ) {
                            Text("Hủy", color = Color.White)
                        }
                        
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    pinVerificationViewModel.verifyPin()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF4CAF50)
                            ),
                            enabled = !state.isLoading && state.pin.length >= 4 && state.attemptsRemaining > 0
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Xác nhận", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

