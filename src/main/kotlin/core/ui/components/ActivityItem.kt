package core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import core.ui.AppColors
import core.ui.AppSpacing
import java.time.LocalDateTime

/**
 * Activity Item - Hiển thị một hoạt động gần đây
 */
@Composable
fun ActivityItem(activity: Map<String, Any>) {
    val customerName = activity["customer_name"] as? String ?: ""
    val transactionType = activity["transaction_type"] as? String ?: ""
    val amount = activity["amount"] as? Double ?: 0.0
    val description = activity["description"] as? String ?: ""
    val transactionDate = activity["transaction_date"] as? String ?: ""
    
    val timeDisplay = formatTime(transactionDate)
    val displayText = formatActivityText(customerName, transactionType, amount, description)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(
                text = "•",
                fontSize = 16.sp,
                color = AppColors.Primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = displayText,
                fontSize = 12.sp,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            text = timeDisplay,
            fontSize = 11.sp,
            color = AppColors.TextSecondary
        )
    }
}

/**
 * Helper: Format thời gian hiển thị
 */
private fun formatTime(transactionDate: String): String = try {
    val dateTime = LocalDateTime.parse(transactionDate.replace(" ", "T"))
    val hour = dateTime.hour
    val minute = dateTime.minute
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    String.format("%02d:%02d %s", displayHour, minute, amPm)
} catch (e: Exception) {
    val timePart = transactionDate.split(" ").getOrNull(1) ?: transactionDate.takeLast(8)
    timePart.take(5) + " AM"
}

/**
 * Helper: Format text hoạt động
 */
private fun formatActivityText(name: String, type: String, amount: Double, description: String): String {
    val formattedAmount = String.format("%,.0f", amount)
    
    return when (type) {
        "TOP_UP" -> if (description.contains("Nạp tiền ban đầu") || description.contains("khởi tạo thẻ")) {
            "$name nạp tiền ban đầu $formattedAmount VNĐ khi khởi tạo thẻ"
        } else {
            "$name nạp $formattedAmount VNĐ"
        }
        
        "TAP" -> {
            val routeMatch = Regex("tuyến (\\d+)").find(description)
            if (routeMatch != null) {
                "$name quẹt thẻ tuyến ${routeMatch.groupValues[1]}"
            } else {
                "$name quẹt thẻ"
            }
        }
        
        "EXTEND_MONTHLY" -> {
            val months = Regex("(\\d+) tháng").find(description)?.groupValues?.getOrNull(1)
            val expiry = Regex("hết hạn ([0-9/]+)").find(description)?.groupValues?.getOrNull(1)
            val action = if (description.contains("gia hạn", ignoreCase = true)) {
                "gia hạn vé tháng"
            } else {
                "mua vé tháng"
            }
            when {
                months != null && expiry != null -> "$name $action $months tháng (hết hạn $expiry)"
                months != null -> "$name $action $months tháng"
                else -> "$name $action"
            }
        }
        
        else -> when {
            description.contains("Thẻ mới") -> "Thẻ mới tạo cho $name"
            description.contains("Nạp tiền ban đầu") || description.contains("khởi tạo thẻ") -> 
                "$name nạp tiền ban đầu $formattedAmount VNĐ khi khởi tạo thẻ"
            else -> description
        }
    }
}

