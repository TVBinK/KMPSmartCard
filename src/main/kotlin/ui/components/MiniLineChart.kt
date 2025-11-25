package ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.AppColors
import ui.AppRadius
import ui.AppTypography

/**
 * Mini Bar Chart - Hiển thị biểu đồ cột đẹp với gradient và rounded corners
 */
@Composable
fun MiniLineChart(
    data: List<Double>,
    dates: List<String>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.md))
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        if (data.isEmpty() || data.all { it == 0.0 }) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📊 Chưa có dữ liệu",
                    fontSize = 11.sp,
                    color = AppColors.TextSecondary
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Chart area với Canvas được cải thiện
                Box(modifier = Modifier.weight(1f)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val maxValue = (data.maxOrNull() ?: 1.0).toFloat()
                        val minValue = (data.minOrNull() ?: 0.0).toFloat()
                        val range = if (maxValue > minValue) maxValue - minValue else 1.0f
                        
                        val width = size.width
                        val height = size.height
                        val padding = 8.dp.toPx()
                        val topPadding = 24.dp.toPx() // Padding phía trên để có chỗ cho text
                        val bottomPadding = padding
                        val bottomY = height - bottomPadding
                        val chartHeight = height - topPadding - bottomPadding
                        
                        // Tính toán kích thước cột
                        val barCount = data.size
                        val totalBarWidth = width - padding * 2
                        val barWidth = (totalBarWidth / barCount) * 0.65f // Chiếm 65% không gian
                        val barSpacing = (totalBarWidth / barCount) * 0.35f
                        val cornerRadius = 4.dp.toPx()
                        
                        // Vẽ các cột với gradient và rounded corners
                        data.forEachIndexed { index, value ->
                            val valueFloat = value.toFloat()
                            val normalizedValue = if (range > 0f) (valueFloat - minValue) / range else 0.0f
                            val barHeight = normalizedValue * chartHeight
                            
                            val x = padding + index * (barWidth + barSpacing) + barSpacing / 2
                            val barTop = bottomY - barHeight
                            
                            if (barHeight > 0) {
                                // Vẽ cột với gradient màu và rounded corners
                                drawRoundRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            AppColors.Primary,
                                            AppColors.Primary.copy(alpha = 0.85f),
                                            AppColors.Primary.copy(alpha = 0.75f)
                                        ),
                                        startY = barTop,
                                        endY = bottomY
                                    ),
                                    topLeft = Offset(x, barTop),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                                )
                                
                                // Vẽ highlight trên đỉnh cột
                                if (barHeight > cornerRadius * 2) {
                                    drawRoundRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.3f),
                                                Color.White.copy(alpha = 0.0f)
                                            )
                                        ),
                                        topLeft = Offset(x, barTop),
                                        size = Size(barWidth, cornerRadius * 2),
                                        cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Text overlay để hiển thị số tiền trên đỉnh cột
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val chartWidth = constraints.maxWidth.toFloat()
                        val chartHeight = constraints.maxHeight.toFloat()
                        
                        val paddingPx = with(density) { 8.dp.toPx() }
                        val topPaddingPx = with(density) { 24.dp.toPx() }
                        val bottomPaddingPx = paddingPx
                        val bottomY = chartHeight - bottomPaddingPx
                        val availableHeight = chartHeight - topPaddingPx - bottomPaddingPx
                        
                        val maxValue = (data.maxOrNull() ?: 1.0).toFloat()
                        val minValue = (data.minOrNull() ?: 0.0).toFloat()
                        val range = if (maxValue > minValue) maxValue - minValue else 1.0f
                        
                        val barCount = data.size
                        val totalBarWidth = chartWidth - paddingPx * 2
                        val barWidth = (totalBarWidth / barCount) * 0.65f
                        val barSpacing = (totalBarWidth / barCount) * 0.35f
                        
                        data.forEachIndexed { index, value ->
                            if (value > 0) {
                                // Format số tiền
                                val displayValue = if (value >= 1000000) {
                                    "${String.format("%.1f", value / 1000000)}M"
                                } else if (value >= 1000) {
                                    "${String.format("%.0f", value / 1000)}K"
                                } else {
                                    value.toInt().toString()
                                }
                                
                                val normalizedValue = if (range > 0f) ((value - minValue) / range).toFloat() else 0.0f
                                val barHeight = normalizedValue * availableHeight
                                val barTop = bottomY - barHeight
                                
                                val barCenterX = paddingPx + index * (barWidth + barSpacing) + barSpacing / 2 + barWidth / 2
                                val textY = (barTop - with(density) { 8.dp.toPx() }).coerceAtLeast(topPaddingPx)
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .absoluteOffset(
                                            x = with(density) { (barCenterX - chartWidth / 2).toDp() },
                                            y = with(density) { textY.toDp() }
                                        )
                                        .wrapContentSize(Alignment.Center)
                                ) {
                                    Text(
                                        text = displayValue,
                                        fontSize = 10.sp,
                                        color = AppColors.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Nhãn ngày (chiếm phần nhỏ ở dưới)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dates.forEachIndexed { index, date ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = date,
                                fontSize = 9.sp,
                                color = AppColors.TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

