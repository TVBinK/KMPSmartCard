package core.ui.components

import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import core.ui.AppColors
import core.ui.AppTypography

/**
 * Animated Balance component
 */
@Composable
fun AnimatedBalance(balance: Double) {
    var targetBalance by remember { mutableStateOf(0.0) }
    var currentBalance by remember { mutableStateOf(0.0) }

    LaunchedEffect(balance) {
        targetBalance = balance
        val duration = 800L
        val steps = 40
        val increment = (targetBalance - currentBalance) / steps

        repeat(steps) {
            currentBalance += increment
            delay(duration / steps)
        }
        currentBalance = targetBalance
    }

    Text(
        text = "${String.format("%,.0f", currentBalance)} VNĐ",
        fontSize = AppTypography.bodyMediumSize,
        fontWeight = FontWeight.Bold,
        color = if (currentBalance > 0) AppColors.Success else AppColors.Error
    )
}

