package ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import models.CardType
import models.Customer
import models.CustomerType
import ui.components.formatDateOfBirth

/**
 * Edit Customer Dialog - Sửa thông tin khách hàng
 */
@Composable
fun EditCustomerDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit
) {
    var fullName by remember { mutableStateOf(customer.fullName) }
    var cccd by remember { mutableStateOf(customer.cccd) }
    var dob by remember { mutableStateOf(TextFieldValue(customer.dob, TextRange(customer.dob.length))) }
    var address by remember { mutableStateOf(customer.address) }
    var phone by remember { mutableStateOf(customer.phone) }
    val customerType = CustomerType.CUSTOMER
    var cardType by remember { mutableStateOf(customer.cardType) }
    var expiryDate by remember { mutableStateOf(customer.expiryDate) }
    var balance by remember { mutableStateOf(customer.balance.toInt().toString()) }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .width(600.dp)
                .padding(16.dp),
            elevation = 8.dp,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sửa thông tin khách hàng",
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

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Card ID: ${customer.cardId}",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Form fields
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Họ tên *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Customer type (fixed)
                OutlinedTextField(
                    value = customerType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Loại đối tượng") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Card Type Dropdown
                var cardTypeExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = cardType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Loại thẻ") },
                        trailingIcon = {
                            IconButton(onClick = { cardTypeExpanded = !cardTypeExpanded }) {
                                Icon(
                                    imageVector = if (cardTypeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { cardTypeExpanded = !cardTypeExpanded }
                    )
                    DropdownMenu(
                        expanded = cardTypeExpanded,
                        onDismissRequest = { cardTypeExpanded = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Chỉ hiển thị NORMAL và MONTHLY
                        listOf(CardType.NORMAL, CardType.MONTHLY).forEach { type ->
                            DropdownMenuItem(
                                onClick = {
                                    cardType = type
                                    cardTypeExpanded = false
                                }
                            ) {
                                Text(type.displayName)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CCCD
                OutlinedTextField(
                    value = cccd,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() } && value.length <= 12) {
                            cccd = value
                        }
                    },
                    label = { Text("CCCD (12 số)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("123456789012") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Ngày sinh
                OutlinedTextField(
                    value = dob,
                    onValueChange = { value ->
                        // Format tự động: dd/MM/yyyy
                        val formatted = formatDateOfBirth(value.text)
                        // Đặt con trỏ về cuối sau khi format
                        val cursorPosition = formatted.length
                        dob = TextFieldValue(formatted, TextRange(cursorPosition))
                    },
                    label = { Text("Ngày sinh (dd/MM/yyyy)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("01/01/2000") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Địa chỉ
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Địa chỉ hiện tại") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Số nhà, đường, phường/xã, quận/huyện") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Số điện thoại
                OutlinedTextField(
                    value = phone,
                    onValueChange = { value ->
                        if (value.all { it.isDigit() } && value.length <= 10) {
                            phone = value
                        }
                    },
                    label = { Text("Số điện thoại (10 số)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("0912345678") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = balance,
                    onValueChange = { if (it.all { char -> char.isDigit() }) balance = it },
                    label = { Text("Số dư (VND) *") },
                    leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
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
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Hủy", color = Color.White, fontSize = 15.sp)
                    }

                    Button(
                        onClick = {
                            val updatedCustomer = customer.copy(
                                fullName = fullName,
                                cccd = cccd,
                                dob = dob.text,
                                address = address,
                                phone = phone,
                                customerType = customerType,
                                cardType = cardType,
                                expiryDate = expiryDate,
                                balance = balance.toDoubleOrNull() ?: customer.balance
                            )
                            // Cập nhật với mã hóa (cần PIN, nhưng ở đây không có PIN nên không mã hóa)
                            // Trong thực tế, cần yêu cầu PIN để mã hóa lại dữ liệu
                            onSave(updatedCustomer)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF4CAF50)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        enabled = fullName.isNotBlank() && balance.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lưu", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

