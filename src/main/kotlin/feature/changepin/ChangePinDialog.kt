package feature.changepin

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
 * Dialog thay đổi mã PIN
 * Sử dụng MVVM pattern
 */
@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    // Khởi tạo ViewModel
    val changePinViewModel = remember { ChangePinViewModel(onSuccess) }
    val state by changePinViewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Tự động đóng dialog khi thẻ bị khóa
    LaunchedEffect(state.isCardBlocked) {
        if (state.isCardBlocked) {
            onDismiss()
        }
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            changePinViewModel.onCleared()
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(450.dp)
                .padding(16.dp),
            elevation = 8.dp,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Thay đổi mã PIN",
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
                    tint = Color(0xFF2196F3)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Thông báo thành công
                if (state.successMessage.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFFE8F5E9),
                        elevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = state.successMessage,
                                fontSize = 14.sp,
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            onSuccess()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Đóng", color = Color.White)
                    }
                } else {
                    // Form thay đổi PIN
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // PIN hiện tại
                        OutlinedTextField(
                            value = state.currentPin,
                            onValueChange = { changePinViewModel.updateCurrentPin(it) },
                            label = { Text("Mã PIN hiện tại *") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { changePinViewModel.toggleCurrentPinVisibility() }) {
                                    Text(
                                        text = if (state.currentPinVisible) "Ẩn" else "Hiện",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (state.currentPinVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            isError = state.errorMessage.contains("PIN hiện tại"),
                            enabled = !state.isLoading
                        )
                        
                        // PIN mới
                        OutlinedTextField(
                            value = state.newPin,
                            onValueChange = { changePinViewModel.updateNewPin(it) },
                            label = { Text("Mã PIN mới (4-6 số) *") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { changePinViewModel.toggleNewPinVisibility() }) {
                                    Text(
                                        text = if (state.newPinVisible) "Ẩn" else "Hiện",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (state.newPinVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            isError = state.errorMessage.contains("PIN mới"),
                            enabled = !state.isLoading
                        )
                        
                        // Xác nhận PIN mới
                        OutlinedTextField(
                            value = state.confirmPin,
                            onValueChange = { changePinViewModel.updateConfirmPin(it) },
                            label = { Text("Xác nhận PIN mới *") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { changePinViewModel.toggleConfirmPinVisibility() }) {
                                    Text(
                                        text = if (state.confirmPinVisible) "Ẩn" else "Hiện",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (state.confirmPinVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            isError = state.errorMessage.contains("xác nhận"),
                            enabled = !state.isLoading
                        )
                        
                        // Thông báo lỗi
                        if (state.errorMessage.isNotEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = if (state.isCardBlocked) Color(0xFFFFCDD2) else Color(0xFFFFEBEE),
                                elevation = 0.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (state.isCardBlocked) Color(0xFFD32F2F) else Color(0xFFF44336),
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
                        }
                        
                        // Lưu ý
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = Color(0xFFE3F2FD),
                            elevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF2196F3),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Lưu ý",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2196F3)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• PIN phải có từ 4-6 chữ số\n• Không được trùng với PIN hiện tại\n• Nhớ kỹ PIN mới để sử dụng",
                                    fontSize = 11.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
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
                                    changePinViewModel.changePin()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF4CAF50)
                            ),
                            enabled = !state.isLoading && 
                                     state.currentPin.isNotEmpty() && 
                                     state.newPin.length >= 4 && 
                                     state.confirmPin.isNotEmpty()
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
                                Text("Thay đổi", color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
