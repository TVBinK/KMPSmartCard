package ui.components

import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ui.AppTypography

/**
 * Animated customer count
 */
@Composable
fun AnimatedCustomerCount(count: Int) {
    var displayCount by remember { mutableStateOf(0) }

    LaunchedEffect(count) {
        if (count > displayCount) {
            // Count up animation
            while (displayCount < count) {
                displayCount++
                delay(50)
            }
        } else {
            displayCount = count
        }
    }

    Text(
        text = "$displayCount khách hàng",
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = AppTypography.bodyMediumSize
    )
}

