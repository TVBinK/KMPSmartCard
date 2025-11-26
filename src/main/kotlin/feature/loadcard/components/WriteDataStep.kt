package feature.loadcard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.model.CardType
import core.model.CustomerType

@Composable
fun WriteDataStepContent(
    cardId: String,
    fullName: String,
    cccd: String,
    dob: String,
    address: String,
    phone: String,
    customerType: CustomerType,
    cardType: CardType,
    balance: String,
    photoSizeBytes: Int,
    photoSizeLimitBytes: Int,
    isPhotoTooLarge: Boolean,
    onWrite: () -> Unit
) {
    val sizeLimitKb = photoSizeLimitBytes / 1024.0
    val currentSizeKb = photoSizeBytes / 1024.0
    val photoInfoText = if (photoSizeBytes > 0) {
        String.format("Ảnh đính kèm: %.1f KB / %.1f KB", currentSizeKb, sizeLimitKb)
    } else {
        String.format("Chưa đính kèm ảnh (giới hạn ≤ %.1f KB)", sizeLimitKb)
    }
    val photoInfoColor = if (isPhotoTooLarge) Color(0xFFD32F2F) else Color.Gray

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Bước 4: Xác nhận và ghi dữ liệu",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3)
        )
        Text("Kiểm tra lại thông tin trước khi ghi lên thẻ:", fontSize = 13.sp, color = Color.Gray)

        Card {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow("Mã thẻ", cardId)
                Divider()
                InfoRow("Họ tên", fullName)
                InfoRow("CCCD", cccd)
                InfoRow("Ngày sinh", dob)
                InfoRow("Địa chỉ", address)
                InfoRow("Số điện thoại", phone)
                Divider()
                InfoRow("Loại đối tượng", customerType.displayName)
                InfoRow("Loại thẻ", cardType.displayName)
                InfoRow("Số dư", String.format("%,d VNĐ", balance.toLongOrNull() ?: 0))
            }
        }

        Text(
            text = photoInfoText,
            fontSize = 13.sp,
            color = photoInfoColor,
            fontWeight = FontWeight.Medium
        )

        Card(backgroundColor = Color(0xFFFFF3E0)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lưu ý", fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Sau khi ghi dữ liệu, muốn thay đổi cần xóa và nạp lại.",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
            }
        }

        Button(
            onClick = onWrite,
            enabled = !isPhotoTooLarge,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF4CAF50))
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ghi dữ liệu lên thẻ", color = Color.White)
        }

        if (isPhotoTooLarge) {
            Text(
                text = "Ảnh vượt quá giới hạn 32KB. Hãy chọn ảnh nhỏ hơn để bật nút ghi thẻ.",
                color = Color(0xFFD32F2F),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
