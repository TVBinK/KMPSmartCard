package models

/**
 * Model yêu cầu gia hạn
 */
data class ExtensionRequest(
    val cardId: String,
    val extensionType: ExtensionType,
    val quantity: Int,  // Số tháng hoặc số lượt
    val amount: Double
)

/**
 * Loại gia hạn (chỉ còn MONTHLY)
 */
enum class ExtensionType(val displayName: String) {
    MONTHLY("Mua vé tháng / Gia hạn tháng")
    // Đã bỏ TRIPS - không còn vé lượt
}

