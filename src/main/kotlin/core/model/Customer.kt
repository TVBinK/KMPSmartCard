package core.model

import java.time.LocalDate

/**
 * Loại thẻ
 */
enum class CardType(val displayName: String) {
    NORMAL("Thẻ Thường"),
    MONTHLY("Thẻ Tháng")
}

/**
 * Model thông tin khách hàng
 */
data class Customer(
    val id: String = "",
    val fullName: String = "",
    val cccd: String = "",  // 12-digit CCCD
    val dob: String = "",  // Ngày sinh (dd/MM/yyyy)
    val address: String = "",  // Địa chỉ hiện tại
    val phone: String = "",  // Số điện thoại (10 digits)
    val cardType: CardType = CardType.NORMAL,  // Mặc định Thẻ Thường
    val expiryDate: LocalDate = LocalDate.now().plusMonths(1),
    val balance: Double = 0.0,
    val cardId: String = "",
    val photoPath: String? = null,
    val photoBytes: ByteArray? = null,
    val publicKey: String = ""          // RSA public key (PEM / hex / base64)
) {
    /**
     * Kiểm tra và tự động chuyển thẻ tháng hết hạn về thẻ thường
     * Trả về Customer đã được cập nhật nếu cần
     */
    fun checkAndConvertExpiredMonthlyCard(): Customer {
        // Nếu là thẻ tháng và đã hết hạn → chuyển về thẻ thường
        if (cardType == CardType.MONTHLY && LocalDate.now().isAfter(expiryDate)) {
            return this.copy(
                cardType = CardType.NORMAL,
                expiryDate = LocalDate.now().plusYears(100) // Thẻ thường không có hạn, đặt xa trong tương lai
            )
        }
        return this
    }
    
    /**
     * Kiểm tra thẻ tháng có còn hạn không
     */
    fun isMonthlyCardExpired(): Boolean {
        return cardType == CardType.MONTHLY && LocalDate.now().isAfter(expiryDate)
    }
}

