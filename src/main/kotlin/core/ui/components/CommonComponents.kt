package core.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.ui.AppColors
import core.ui.AppElevation
import core.ui.AppRadius
import core.ui.AppSpacing
import core.ui.AppTypography

/**
 * Format ngày sinh tự động thêm dấu "/"
 * Format: dd/MM/yyyy
 * Ví dụ: "12121212" -> "12/12/1212"
 */
fun formatDateOfBirth(input: String): String {
    // Chỉ lấy số
    val digitsOnly = input.filter { it.isDigit() }
    
    // Giới hạn tối đa 8 số (ddMMyyyy)
    val limitedDigits = digitsOnly.take(8)
    
    return when {
        limitedDigits.isEmpty() -> ""
        limitedDigits.length <= 2 -> limitedDigits
        limitedDigits.length <= 4 -> "${limitedDigits.substring(0, 2)}/${limitedDigits.substring(2)}"
        else -> "${limitedDigits.substring(0, 2)}/${limitedDigits.substring(2, 4)}/${limitedDigits.substring(4)}"
    }
}

/**
 * Shimmer Effect cho loading
 */
@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val shimmer by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFE0E0E0),
                        Color(0xFFF5F5F5),
                        Color(0xFFE0E0E0)
                    ),
                    startX = shimmer - 200f,
                    endX = shimmer + 200f
                )
            )
    )
}

/**
 * Loading Placeholder với shimmer
 */
@Composable
fun LoadingCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShimmerEffect(modifier = Modifier.size(50.dp, 60.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                ShimmerEffect(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp))
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerEffect(modifier = Modifier.fillMaxWidth(0.5f).height(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerEffect(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp))
            }
            
            ShimmerEffect(modifier = Modifier.size(30.dp))
        }
    }
}

/**
 * Custom Button với màu sắc tùy chỉnh và animation
 */
@Composable
fun CustomButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF2196F3),
    textColor: Color = Color.White,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isHovered && enabled) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isHovered && enabled) 6.dp else 2.dp,
        animationSpec = tween(200)
    )
    
    Surface(
        modifier = modifier
            .height(48.dp)
            .padding(horizontal = 4.dp)
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f),
        elevation = elevation,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = textColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            }
        }
    }
}

/**
 * Nút Xác nhận (màu xanh)
 */
@Composable
fun ConfirmButton(
    text: String = "Xác nhận",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    CustomButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        backgroundColor = Color(0xFF4CAF50),
        enabled = enabled,
        icon = Icons.Default.Check
    )
}

/**
 * Nút Hủy (màu đỏ)
 */
@Composable
fun CancelButton(
    text: String = "Hủy",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    CustomButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        backgroundColor = Color(0xFFF44336),
        icon = Icons.Default.Close
    )
}

/**
 * Label với tiêu đề và nội dung
 */
@Composable
fun InfoLabel(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Black,
    valueFontWeight: FontWeight = FontWeight.Normal
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            color = valueColor,
            fontWeight = valueFontWeight
        )
    }
}

/**
 * TextField tùy chỉnh với label và focus animation
 */
@Composable
fun CustomTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    placeholder: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF2196F3) else Color.Gray.copy(alpha = 0.5f),
        animationSpec = tween(200)
    )
    
    val labelScale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (isFocused) Color(0xFF2196F3) else Color.Gray,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.scale(labelScale)
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, fontSize = 14.sp) },
            readOnly = readOnly,
            singleLine = singleLine,
            enabled = enabled,
            interactionSource = interactionSource,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                backgroundColor = if (readOnly) Color(0xFFF5F5F5) else Color.White,
                disabledTextColor = Color.Gray,
                focusedBorderColor = borderColor,
                unfocusedBorderColor = borderColor
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

/**
 * Numeric TextField
 */
@Composable
fun NumericTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    placeholder: String = "0"
) {
    CustomTextField(
        label = label,
        value = value,
        onValueChange = { newValue ->
            // Chỉ cho phép nhập số và dấu chấm
            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                onValueChange(newValue)
            }
        },
        modifier = modifier,
        readOnly = readOnly,
        placeholder = placeholder
    )
}

/**
 * ComboBox/Dropdown tùy chỉnh
 */
@Composable
fun <T> CustomDropdown(
    label: String,
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemLabel: (T) -> String = { it.toString() }
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = itemLabel(selectedItem),
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .width(500.dp)
                    .heightIn(max = 300.dp)
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        onClick = {
                            onItemSelected(item)
                            expanded = false
                        }
                    ) {
                        Text(text = itemLabel(item), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * Card chứa nội dung với viền bo tròn và animation
 */
@Composable
fun CustomCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.01f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isHovered) 8.dp else 4.dp,
        animationSpec = tween(200)
    )
    
    Card(
        modifier = modifier
            .scale(scale)
            .hoverable(interactionSource),
        shape = RoundedCornerShape(12.dp),
        backgroundColor = backgroundColor,
        elevation = elevation
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * Header của dialog/form
 */
@Composable
fun DialogHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2196F3)
        )
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Đóng",
                tint = Color.Gray
            )
        }
    }
}

/**
 * Divider mỏng
 */
@Composable
fun CustomDivider(modifier: Modifier = Modifier) {
    Divider(
        modifier = modifier.padding(vertical = 8.dp),
        color = Color.Gray.copy(alpha = 0.3f),
        thickness = 1.dp
    )
}

/**
 * Status Badge (hiển thị trạng thái) với animation
 */
@Composable
fun StatusBadge(
    text: String,
    isValid: Boolean,
    modifier: Modifier = Modifier
) {
    // Animated appearance
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        isVisible = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    
    // Pulse animation cho valid badge
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isValid) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Surface(
        modifier = modifier
            .scale(scale)
            .graphicsLayer {
                scaleX = if (isValid) pulse else 1f
                scaleY = if (isValid) pulse else 1f
            },
        shape = RoundedCornerShape(24.dp),
        color = if (isValid) Color(0xFF4CAF50) else Color(0xFFF44336)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isValid) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Image placeholder với border và animation
 */
@Composable
fun ImagePlaceholder(
    photoPath: String? = null,
    photoBytes: ByteArray? = null,
    modifier: Modifier = Modifier,
    size: Pair<Int, Int> = Pair(150, 180)
) {
    // Fade in animation
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        isVisible = true
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(400)
    )
    
    Box(
        modifier = modifier
            .size(size.first.dp, size.second.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5))
            .border(2.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Ưu tiên hiển thị từ photoBytes
        if (photoBytes != null && photoBytes.isNotEmpty()) {
            val imageBitmap = remember(photoBytes) {
                try {
                    // Kiểm tra JPEG header trước khi decode
                    val isValidJpeg = photoBytes.size >= 2 && 
                                     photoBytes[0] == 0xFF.toByte() && 
                                     photoBytes[1] == 0xD8.toByte()
                    
                    if (!isValidJpeg) {
                        println("[ImagePlaceholder] Cảnh báo: Dữ liệu không phải JPEG hợp lệ (size=${photoBytes.size} bytes, header=${if (photoBytes.size >= 2) "${photoBytes[0].toUByte().toString(16)} ${photoBytes[1].toUByte().toString(16)}" else "N/A"})")
                        null
                    } else {
                        org.jetbrains.skia.Image.makeFromEncoded(photoBytes).asImageBitmap()
                    }
                } catch (e: Exception) {
                    println("[ImagePlaceholder] Lỗi decode ảnh: ${e.message}")
                    e.printStackTrace()
                    null
                }
            }
            
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Ảnh khách hàng",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Fallback nếu không load được ảnh
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Ảnh khách hàng",
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray
                )
            }
        } else if (!photoPath.isNullOrEmpty()) {
            // Hiển thị từ photoPath (legacy)
            Text(
                text = "Ảnh: $photoPath",
                fontSize = 10.sp,
                color = Color.Gray
            )
        } else {
            // Placeholder khi không có ảnh
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Ảnh khách hàng",
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Chưa có ảnh",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

/**
 * Menu Button với gradient background và animations
 * Theo design spec - Height 64.dp, Border radius 12.dp
 */
@Composable
fun MenuButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gradientStart: Color = AppColors.PrimaryGradientStart,
    gradientEnd: Color = AppColors.PrimaryGradientEnd
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isHovered && enabled) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isHovered && enabled) 4.dp else AppElevation.sm,
        animationSpec = tween(200)
    )
    
    Card(
        modifier = modifier
            .height(64.dp)
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        shape = RoundedCornerShape(AppRadius.md),
        elevation = elevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (enabled) listOf(gradientStart, gradientEnd) 
                                else listOf(
                                    gradientStart.copy(alpha = 0.5f),
                                    gradientEnd.copy(alpha = 0.5f)
                                )
                    )
                )
                .padding(AppSpacing.md),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(AppSpacing.sm))
                Text(
                    text = text,
                    fontSize = AppTypography.bodyMediumSize,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Primary Button với gradient và improved animations
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    gradientStart: Color = AppColors.PrimaryGradientStart,
    gradientEnd: Color = AppColors.PrimaryGradientEnd
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isHovered && enabled) 1.02f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isHovered && enabled) 4.dp else AppElevation.sm,
        animationSpec = tween(200)
    )
    
    Card(
        modifier = modifier
            .height(56.dp)
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        shape = RoundedCornerShape(AppRadius.md),
        elevation = elevation
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = if (enabled) listOf(gradientStart, gradientEnd)
                                else listOf(
                                    gradientStart.copy(alpha = 0.5f),
                                    gradientEnd.copy(alpha = 0.5f)
                                )
                    )
                )
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                }
                Text(
                    text = text,
                    fontSize = AppTypography.bodyLargeSize,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Glassmorphism Card với improved design
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = AppColors.Surface.copy(alpha = 0.9f),
    elevation: androidx.compose.ui.unit.Dp = AppElevation.sm,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.01f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )
    
    val cardElevation by animateDpAsState(
        targetValue = if (isHovered) AppElevation.md else elevation,
        animationSpec = tween(200)
    )
    
    Card(
        modifier = modifier
            .scale(scale)
            .hoverable(interactionSource),
        shape = RoundedCornerShape(AppRadius.lg),
        backgroundColor = backgroundColor,
        elevation = cardElevation,
        border = BorderStroke(1.dp, AppColors.Border.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.md + AppSpacing.xs),
            content = content
        )
    }
}

