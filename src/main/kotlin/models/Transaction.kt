package models

import java.time.LocalDateTime

/**
 * Loại giao dịch
 */
enum class TransactionType(val displayName: String) {
    TOP_UP("Nạp tiền"),
    EXTENSION_MONTHLY("Mua vé tháng / Gia hạn tháng"),
    REFUND("Hoàn tiền"),
    TAP("Quẹt thẻ"),
    ROUTE_HISTORY("Lộ trình")
}

/**
 * Model giao dịch
 */
data class Transaction(
    val id: String = "",
    val cardId: String = "",
    val type: TransactionType,
    val amount: Double,
    val balanceBefore: Double,
    val balanceAfter: Double,
    val description: String = "",
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val tripId: String? = null
)

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

