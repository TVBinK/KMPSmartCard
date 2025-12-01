package feature.main

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.model.CardType
import core.model.Customer
import core.ui.AppColors
import core.ui.AppRadius
import core.ui.AppSpacing
import core.ui.AppTypography
import core.ui.components.AnimatedBalance
import core.ui.components.ImagePlaceholder
import java.time.LocalDate

/**
 * Customer list item với animation
 */
@Composable
fun AnimatedCustomerListItem(
    customer: Customer,
    index: Int,
    onClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = customer.cardId) {
        kotlinx.coroutines.delay(index * 50L) // Staggered animation
        isVisible = true
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = androidx.compose.animation.fadeIn(animationSpec = tween(400)) +
                androidx.compose.animation.slideInHorizontally(
                    initialOffsetX = { -it / 2 },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                )
    ) {
        CustomerListItem(
            customer = customer,
            onClick = onClick
        )
    }
}


/**
 * Customer list item
 */
@Composable
fun CustomerListItem(
    customer: Customer,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    val elevation by animateDpAsState(
        targetValue = if (isHovered) 6.dp else 2.dp,
        animationSpec = tween(200)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        shape = RoundedCornerShape(AppRadius.lg),
        backgroundColor = AppColors.Surface,
        elevation = elevation,
        border = BorderStroke(
            width = 1.dp,
            color = if (isHovered) AppColors.Primary.copy(alpha = 0.3f) else AppColors.Border.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.md + AppSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar với gradient border và shadow
            val rotation by animateFloatAsState(
                targetValue = if (isHovered) 5f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy
                )
            )

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .shadow(
                        elevation = if (isHovered) 8.dp else 4.dp,
                        shape = CircleShape,
                        spotColor = AppColors.Primary.copy(alpha = 0.3f)
                    )
                    .graphicsLayer { rotationZ = rotation }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    AppColors.PrimaryGradientStart,
                                    AppColors.PrimaryGradientEnd
                                )
                            )
                        )
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(AppColors.Surface)
                    ) {
                        ImagePlaceholder(
                            photoPath = customer.photoPath,
                            photoBytes = customer.photoBytes,
                            size = Pair(64, 64)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                // Name với better typography
                Text(
                    text = customer.fullName,
                    fontSize = AppTypography.bodyLargeSize,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    letterSpacing = 0.2.sp
                )

                // Card ID với icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = AppColors.TextSecondary
                    )
                    Text(
                        text = customer.cardId,
                        fontSize = AppTypography.bodySmallSize,
                        color = AppColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Balance với icon và better styling
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (customer.balance > 0) AppColors.Success else AppColors.Error
                    )
                    AnimatedBalance(balance = customer.balance)
                }
            }

            // Right side: Status icon và arrow
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Card type icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (customer.cardType == CardType.MONTHLY)
                                AppColors.Primary.copy(alpha = 0.15f)
                            else
                                AppColors.Info.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (customer.cardType == CardType.MONTHLY) Icons.Default.Star else Icons.Default.AccountBox,
                        contentDescription = customer.cardType.displayName,
                        modifier = Modifier.size(24.dp),
                        tint = if (customer.cardType == CardType.MONTHLY) AppColors.Primary else AppColors.Info
                    )
                }

                // Arrow icon với animation
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = "Xem chi tiết",
                    tint = if (isHovered) AppColors.Primary else AppColors.TextSecondary,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            translationX = if (isHovered) 6f else 0f
                            alpha = if (isHovered) 1f else 0.6f
                        }
                )
            }
        }
    }
}

@androidx.compose.desktop.ui.tooling.preview.Preview
@Composable
fun CustomerListItemPreview() {
    androidx.compose.material.MaterialTheme(
        colors = androidx.compose.material.lightColors(
            primary = androidx.compose.ui.graphics.Color(0xFF6366F1),
            primaryVariant = androidx.compose.ui.graphics.Color(0xFF4F46E5),
            secondary = androidx.compose.ui.graphics.Color(0xFF10B981),
            background = androidx.compose.ui.graphics.Color(0xFFF9FAFB)
        )
    ) {
        val sampleCustomer = Customer(
            id = "1",
            fullName = "Nguyễn Văn A",
            cccd = "012345678901",
            dob = "01/01/2000",
            address = "Hà Nội",
            phone = "0987654321",
            cardType = CardType.MONTHLY,
            expiryDate = LocalDate.now().plusMonths(6),
            balance = 150000.0,
            cardId = "CARD-001",
            photoPath = null,
            photoBytes = null
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0xFFF9FAFB))
                .padding(16.dp)
        ) {
            CustomerListItem(
                customer = sampleCustomer,
                onClick = {}
            )
        }
    }
}
