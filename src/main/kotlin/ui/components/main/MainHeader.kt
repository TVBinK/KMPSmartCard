package ui.components.main

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.*
import ui.components.AnimatedCustomerCount

@Composable
fun MainHeader(
    customerCount: Int
) {
    val infiniteTransition = rememberInfiniteTransition()
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(AppElevation.lg, RoundedCornerShape(AppRadius.lg))
            .clip(RoundedCornerShape(AppRadius.lg))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        AppColors.PrimaryGradientStart,
                        AppColors.PrimaryGradientEnd,
                        AppColors.PrimaryAccent,
                        AppColors.PrimaryGradientStart
                    ),
                    startX = gradientShift,
                    endX = gradientShift + 1000f
                )
            )
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon với background và rotation animation
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer { rotationZ = rotation },
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(AppSpacing.md + AppSpacing.xs))
            Column {
                Text(
                    text = "🚌 Hệ thống quản lý thẻ xe bus",
                    fontSize = AppTypography.h2Size,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                Text(
                    text = "Bus Card Management System",
                    fontSize = AppTypography.bodyMediumSize,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // Stats badge với animation
            val badgeScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Card(
                backgroundColor = Color.White.copy(alpha = 0.2f),
                elevation = 0.dp,
                shape = RoundedCornerShape(AppRadius.md),
                modifier = Modifier.scale(badgeScale)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                    // Animated counter
                    AnimatedCustomerCount(count = customerCount)
                }
            }
        }
    }
}
