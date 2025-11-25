package ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design System - Color Palette và Constants
 * Theo UI_DESIGN_PROMPT.md
 */

object AppColors {
    // Primary Colors
    val Primary = Color(0xFF6366F1)           // Indigo-500
    val PrimaryDark = Color(0xFF4F46E5)       // Indigo-600
    val PrimaryLight = Color(0xFF818CF8)     // Indigo-400
    
    // Primary Gradient
    val PrimaryGradientStart = Color(0xFF6366F1)  // Indigo-500
    val PrimaryGradientEnd = Color(0xFF8B5CF6)     // Purple-500
    val PrimaryAccent = Color(0xFFEC4899)          // Pink-500
    
    // Secondary Colors
    val Success = Color(0xFF10B981)            // Emerald-500
    val SuccessLight = Color(0xFF34D399)      // Emerald-400
    val Warning = Color(0xFFF59E0B)           // Amber-500
    val Error = Color(0xFFEF4444)             // Red-500
    val Info = Color(0xFF3B82F6)              // Blue-500
    
    // Neutral Colors
    val Background = Color(0xFFF9FAFB)        // Gray-50
    val BackgroundDark = Color(0xFFF3F4F6)   // Gray-100
    val Surface = Color(0xFFFFFFFF)           // White
    val SurfaceElevated = Color(0xFFFAFAFA)    // Gray-50
    
    val TextPrimary = Color(0xFF111827)       // Gray-900
    val TextSecondary = Color(0xFF6B7280)      // Gray-500
    val TextDisabled = Color(0xFF9CA3AF)      // Gray-400
    
    val Border = Color(0xFFE5E7EB)            // Gray-200
    val Divider = Color(0xFFE5E7EB)           // Gray-200
    
    // Button Gradients
    val PurpleGradientStart = Color(0xFF9C27B0)
    val PurpleGradientEnd = Color(0xFFBA68C8)
    
    val BlueGradientStart = Color(0xFF2196F3)
    val BlueGradientEnd = Color(0xFF42A5F5)
    
    val GreenGradientStart = Color(0xFF10B981)
    val GreenGradientEnd = Color(0xFF34D399)
    
    val IndigoGradientStart = Color(0xFF6366F1)
    val IndigoGradientEnd = Color(0xFF818CF8)
    
    val OrangeGradientStart = Color(0xFFF59E0B)
    val OrangeGradientEnd = Color(0xFFFBBF24)
    
    val TealGradientStart = Color(0xFF14B8A6)
    val TealGradientEnd = Color(0xFF5EEAD4)
}

object AppSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

object AppRadius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val full = 999.dp
}

object AppElevation {
    val none = 0.dp
    val sm = 2.dp
    val md = 4.dp
    val lg = 8.dp
    val xl = 16.dp
}

object AppTypography {
    val h1Size = 32.sp
    val h2Size = 24.sp
    val h3Size = 20.sp
    val h4Size = 18.sp
    val h5size = 15.sp
    val bodyLargeSize = 16.sp
    val bodyMediumSize = 14.sp
    val bodySmallSize = 12.sp
    val captionSize = 12.sp
    val overlineSize = 10.sp
}

