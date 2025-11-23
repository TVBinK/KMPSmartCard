package models

import java.time.LocalDate

/**
 * Loại đối tượng khách hàng
 */
enum class CustomerType(val displayName: String) {
    STUDENT("HSSV"),
    ELDERLY("Người cao tuổi"),
    NORMAL("Thông thường")
}

/**
 * Loại thẻ
 */
enum class CardType(val displayName: String) {
    NORMAL("Thẻ Thường"),
    MONTHLY("Thẻ Tháng")
}

/**
 * Trạng thái thẻ
 */
enum class CardStatus {
    VALID,      // Hợp lệ
    EXPIRED,    // Hết hạn
    INSUFFICIENT_BALANCE  // Không đủ số dư
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
    val customerType: CustomerType = CustomerType.NORMAL,
    val cardType: CardType = CardType.NORMAL,  // Mặc định Thẻ Thường
    val expiryDate: LocalDate = LocalDate.now().plusMonths(1),
    val balance: Double = 0.0,
    val cardId: String = "",
    val linkedCustomerCode: String = "",
    val photoPath: String? = null,
    val photoBytes: ByteArray? = null
) {
    /**
     * Kiểm tra trạng thái thẻ
     */
    fun getCardStatus(): CardStatus {
        // Kiểm tra hết hạn TRƯỚC (quan trọng nhất)
        if (LocalDate.now().isAfter(expiryDate)) {
            return CardStatus.EXPIRED
        }
        
        // Nếu còn hạn → Kiểm tra loại thẻ
        return when (cardType) {
            CardType.MONTHLY -> {
                // Thẻ tháng: Chỉ cần kiểm tra ngày hết hạn (đã check ở trên)
                CardStatus.VALID
            }
            CardType.NORMAL -> {
                // Thẻ thường: Cần kiểm tra số dư
                if (balance <= 0) CardStatus.INSUFFICIENT_BALANCE else CardStatus.VALID
            }
        }
    }

    /**
     * Kiểm tra thẻ có hợp lệ không
     */
    fun isValid(): Boolean = getCardStatus() == CardStatus.VALID
}

