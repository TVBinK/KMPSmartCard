package feature.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.ui.components.loadIconFromResource
import core.ui.AppColors
import core.ui.AppElevation
import core.ui.AppSpacing
import core.ui.AppTypography
import core.ui.components.ActivityItem
import core.ui.components.GlassCard

@Composable
fun RecentActivitySection(
    recentActivities: List<Map<String, Any>>
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = AppElevation.sm
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                // Icon từ resources
                val historyIcon = loadIconFromResource("icons/history.png")
                if (historyIcon != null) {
                    Image(
                        bitmap = historyIcon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "HOẠT ĐỘNG GẦN ĐÂY",
                    fontSize = AppTypography.h5size,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Primary
                )
            }
            
            Spacer(modifier = Modifier.height(AppSpacing.md))
            
            // Danh sách hoạt động - chiếm phần còn lại
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                if (recentActivities.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Chưa có hoạt động nào",
                            fontSize = 12.sp,
                            color = AppColors.TextSecondary
                        )
                    }
                } else {
                    recentActivities.forEach { activity ->
                        ActivityItem(activity)
                    }
                }
            }
        }
    }
}
