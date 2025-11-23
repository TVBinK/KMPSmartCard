package ui.screens

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
import smartcard.BusCardManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog thay đổi mã PIN
 */
@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var currentPinVisible by remember { mutableStateOf(false) }
    var newPinVisible by remember { mutableStateOf(false) }
    var confirmPinVisible by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
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
                if (successMessage.isNotEmpty()) {
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
                                text = successMessage,
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
                            value = currentPin,
                            onValueChange = { value ->
                                if (value.all { it.isDigit() } && value.length <= 6) {
                                    currentPin = value
                                    errorMessage = ""
                                }
                            },
                            label = { Text("Mã PIN hiện tại *") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { currentPinVisible = !currentPinVisible }) {
                                    Text(
                                        text = if (currentPinVisible) "Ẩn" else "Hiện",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (currentPinVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            isError = errorMessage.contains("PIN hiện tại"),
                            enabled = !isLoading
                        )
                        
                        // PIN mới
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { value ->
                                if (value.all { it.isDigit() } && value.length <= 6) {
                                    newPin = value
                                    errorMessage = ""
                                }
                            },
                            label = { Text("Mã PIN mới (4-6 số) *") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { newPinVisible = !newPinVisible }) {
                                    Text(
                                        text = if (newPinVisible) "Ẩn" else "Hiện",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (newPinVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            isError = errorMessage.contains("PIN mới"),
                            enabled = !isLoading
                        )
                        
                        // Xác nhận PIN mới
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { value ->
                                if (value.all { it.isDigit() } && value.length <= 6) {
                                    confirmPin = value
                                    errorMessage = ""
                                }
                            },
                            label = { Text("Xác nhận PIN mới *") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { confirmPinVisible = !confirmPinVisible }) {
                                    Text(
                                        text = if (confirmPinVisible) "Ẩn" else "Hiện",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (confirmPinVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            isError = errorMessage.contains("xác nhận"),
                            enabled = !isLoading
                        )
                        
                        // Thông báo lỗi
                        if (errorMessage.isNotEmpty()) {
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
                                        text = errorMessage,
                                        fontSize = 12.sp,
                                        color = Color(0xFFF44336)
                                    )
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
                                // Validation
                                if (currentPin.isEmpty()) {
                                    errorMessage = "Vui lòng nhập PIN hiện tại"
                                    return@Button
                                }
                                
                                if (newPin.length < 4 || newPin.length > 6) {
                                    errorMessage = "PIN mới phải có từ 4-6 chữ số"
                                    return@Button
                                }
                                
                                if (newPin == currentPin) {
                                    errorMessage = "PIN mới không được trùng với PIN hiện tại"
                                    return@Button
                                }
                                
                                if (newPin != confirmPin) {
                                    errorMessage = "PIN xác nhận không khớp với PIN mới"
                                    return@Button
                                }
                                
                                scope.launch {
                                    isLoading = true
                                    errorMessage = ""
                                    
                                    // Xác thực PIN hiện tại trước - PHẢI ĐÚNG mới cho phép đổi
                                    val verifyResult = withContext(Dispatchers.IO) {
                                        BusCardManager.checkPin(currentPin)
                                    }
                                    
                                    verifyResult.onSuccess { isCorrect ->
                                        // Chỉ tiếp tục nếu PIN đúng
                                        if (!isCorrect) {
                                            isLoading = false
                                            errorMessage = "PIN hiện tại không đúng"
                                            return@launch
                                        }
                                        
                                        // PIN đúng, tiếp tục đổi PIN
                                        val updateResult = withContext(Dispatchers.IO) {
                                            BusCardManager.updatePin(newPin)
                                        }
                                        
                                        isLoading = false
                                        
                                        updateResult.onSuccess {
                                            successMessage = "Đã thay đổi PIN thành công!"
                                            currentPin = ""
                                            newPin = ""
                                            confirmPin = ""
                                        }.onFailure { error ->
                                            errorMessage = error.message ?: "Không thể thay đổi PIN"
                                        }
                                    }.onFailure { error ->
                                        // PIN sai hoặc có lỗi
                                        isLoading = false
                                        errorMessage = error.message ?: "PIN hiện tại không đúng"
                                        currentPin = "" // Xóa PIN để nhập lại
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = Color(0xFF4CAF50)
                            ),
                            enabled = !isLoading && 
                                     currentPin.isNotEmpty() && 
                                     newPin.length >= 4 && 
                                     confirmPin.isNotEmpty()
                        ) {
                            if (isLoading) {
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

