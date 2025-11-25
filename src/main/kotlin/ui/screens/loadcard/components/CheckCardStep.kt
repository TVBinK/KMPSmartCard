package ui.screens.loadcard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CheckCardStepContent(
    onCheck: () -> Unit,
    onClearCard: () -> Unit,
    isCardEmpty: Boolean
) {
    var hasChecked by remember { mutableStateOf(false) }
    var showHasData by remember { mutableStateOf(false) }
    
    // Theo dõi khi isCardEmpty thay đổi (sau khi xóa thành công)
    LaunchedEffect(isCardEmpty) {
        if (isCardEmpty) {
            showHasData = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Bước 2: Kiểm tra trạng thái thẻ",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3)
        )
        Text(
            text = "Thẻ phải rỗng để nạp dữ liệu mới. Nếu thẻ có dữ liệu, cần xóa trước.",
            fontSize = 13.sp,
            color = Color.Gray
        )

        // Nút Kiểm tra thẻ
        Button(
            onClick = {
                hasChecked = true
                showHasData = !isCardEmpty
                onCheck()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3))
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Kiểm tra thẻ", color = Color.White)
        }

        // Hiển thị cảnh báo và nút xóa nếu thẻ có dữ liệu
        if (hasChecked && showHasData) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFFFFF3E0),
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Thẻ đã có dữ liệu!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                    
                    Text(
                        "Nhấn nút bên dưới để xóa toàn bộ dữ liệu trên thẻ. Sau khi xóa, dialog sẽ tự động đóng.",
                        fontSize = 13.sp,
                        color = Color(0xFF666666)
                    )
                    
                    // Nút Xóa dữ liệu thẻ - Chỉ hiển thị khi thẻ có dữ liệu
                    Button(
                        onClick = {
                            println("User nhan nut 'Xoa du lieu the'")
                            onClearCard()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFFF5252))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Xóa dữ liệu thẻ", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Hiển thị thông báo nếu thẻ rỗng
        if (hasChecked && !showHasData) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFFE8F5E9),
                elevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "✓ Thẻ rỗng, sẵn sàng nạp dữ liệu mới!",
                        fontSize = 13.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
