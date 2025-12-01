package smartcard

import com.buscardmanagement.client.BusSmartCard
import java.awt.image.BufferedImage

/**
 * BusCardManager - Kotlin wrapper cho BusSmartCard Java client
 *
 * Singleton object để quản lý kết nối và giao tiếp với smart card
 * Cung cấp interface dễ sử dụng cho Kotlin/Compose UI
 */
object BusCardManager {

    private val smartCard: BusSmartCard = BusSmartCard.getInstance()

    // Trạng thái kết nối
    val isConnected: Boolean
        get() = smartCard.isConnected

    val isCardBlocked: Boolean
        get() = BusSmartCard.isCardBlocked

    val pinAttempts: Int
        get() = BusSmartCard.counter.toInt()

    val isCardPresent: Boolean
        get() = smartCard.isCardPresent()

    // ========== KẾT NỐI ==========

    /**
     * Kết nối với smart card
     */
    fun connect(): Result<Boolean> = executeSafe("Kết nối") {
        if (smartCard.connectCard()) {
            Result.success(true)
        } else {
            Result.failure(
                Exception(
                    "Không thể kết nối với thẻ. Vui lòng đảm bảo:\n" +
                            "1. JCIDE Simulator đang chạy\n" +
                            "2. Applet đã được cài đặt trên simulator\n" +
                            "3. Hoặc kết nối Java Card thật qua PC/SC reader"
                )
            )
        }
    }

    // ========== THÔNG TIN KHÁCH HÀNG ==========

    /**
     * Lấy thông tin khách hàng từ thẻ
     */
    fun getCustomerInfo(): Result<CustomerInfo> = executeSafe("Đọc thông tin khách hàng") {
        val info = smartCard.customerInfo
        if (info != null && info.size >= 5) {
            Result.success(
                CustomerInfo(
                    fullName = info.getOrNull(0) ?: "",
                    customerType = info.getOrNull(1) ?: "",
                    expiryDate = info.getOrNull(2) ?: "",
                    cardType = info.getOrNull(3) ?: "",
                    linkedCustomerId = info.getOrNull(4) ?: "",
                    cccd = info.getOrNull(5) ?: "",
                    dob = info.getOrNull(6) ?: "",
                    address = info.getOrNull(7) ?: "",
                    phone = info.getOrNull(8) ?: ""
                )
            )
        } else {
            val message = if (BusSmartCard.isCardBlocked) {
                "Thẻ đã bị khóa do nhập sai PIN quá nhiều lần. Vui lòng mở khóa thẻ trước khi đọc."
            } else {
                "Không thể đọc thông tin khách hàng từ thẻ"
            }
            Result.failure(Exception(message))
        }
    }

    /**
     * Cập nhật thông tin khách hàng lên thẻ
     */
    fun updateCustomerInfo(
        fullName: String,
        customerType: String,
        expiryDate: String,
        cardType: String,
        linkedCustomerId: String = "",
        cccd: String = "",
        dob: String = "",
        address: String = "",
        phone: String = ""
    ): Result<Boolean> = executeSafe("Cập nhật thông tin khách hàng") {
        val result = smartCard.updateCustomerInfo(
            fullName, customerType, expiryDate, cardType, linkedCustomerId,
            cccd, dob, address, phone
        )
        if (result) Result.success(true) else Result.failure(Exception("Không thể cập nhật thông tin khách hàng"))
    }

    // ========== CARD ID ==========

    /**
     * Lấy Card ID từ thẻ
     */
    fun getCardId(): Result<String> = executeSafe("Đọc Card ID") {
        val cardIdArray = smartCard.cardId
        if (cardIdArray != null && cardIdArray.isNotEmpty()) {
            Result.success(cardIdArray[0])
        } else {
            val message = if (BusSmartCard.isCardBlocked) {
                "Thẻ đã bị khóa do nhập sai PIN quá nhiều lần. Vui lòng mở khóa thẻ trước khi đọc."
            } else {
                "Không thể đọc Card ID"
            }
            Result.failure(Exception(message))
        }
    }

    /**
     * Cập nhật Card ID
     */
    fun updateCardId(cardId: String): Result<Boolean> = executeSafe("Cập nhật Card ID") {
        if (smartCard.updateCardId(cardId)) {
            Result.success(true)
        } else {
            Result.failure(Exception("Không thể cập nhật Card ID"))
        }
    }

    // ========== PIN ==========

    /**
     * Cập nhật PIN
     */
    fun updatePin(oldPin: String, newPin: String): Result<Boolean> {
        return executeSafe("Cập nhật PIN") {
            // Kiểm tra thẻ có bị khóa không (trừ khi tạo PIN lần đầu)
            if (isCardBlocked && oldPin.isNotEmpty()) {
                return Result.failure(Exception("Thẻ đã bị khóa do nhập sai PIN quá nhiều lần. Vui lòng mở khóa thẻ trước."))
            }

            // Validation cho PIN mới
            if (newPin.length < 4 || newPin.length > 6) {
                return Result.failure(Exception("PIN mới phải có từ 4-6 chữ số"))
            }

            // Validation cho đổi PIN (không áp dụng cho tạo PIN lần đầu)
            if (oldPin.isNotEmpty()) {
                if (oldPin == newPin) {
                    return Result.failure(Exception("PIN mới không được trùng với PIN hiện tại"))
                }
            }

            val result = smartCard.updatePin(oldPin, newPin)
            if (result) {
                Result.success(true)
            } else {
                // Kiểm tra lại trạng thái thẻ sau khi update
                val errorMsg = if (isCardBlocked) {
                    "Thẻ đã bị khóa do nhập sai PIN hiện tại quá nhiều lần"
                } else if (oldPin.isNotEmpty()) {
                    val remaining = 4 - pinAttempts
                    if (remaining > 0) {
                        "PIN hiện tại không đúng. Còn $remaining lần thử trước khi bị khóa"
                    } else {
                        "PIN hiện tại không đúng"
                    }
                } else {
                    "Không thể tạo PIN"
                }
                Result.failure(Exception(errorMsg))
            }
        }
    }

    /**
     * Kiểm tra PIN (với counter, khóa thẻ sau 4 lần sai)
     */
    fun checkPin(pin: String): Result<Boolean> {
        return try {
            val result = smartCard.checkPin(pin)
            if (result) {
                Result.success(true)
            } else {
                if (BusSmartCard.isCardBlocked) {
                    Result.failure(Exception("Thẻ đã bị khóa do nhập sai PIN quá nhiều lần"))
                } else {
                    val remaining = 4 - BusSmartCard.counter
                    Result.failure(Exception("PIN không đúng. Còn $remaining lần thử"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi kiểm tra PIN: ${e.message}", e))
        }
    }

    // ========== SỐ DƯ ==========

    /**
     * Lấy số dư
     */
    fun getBalance(): Result<Double> = executeSafe("Đọc số dư") {
        val balanceArray = smartCard.balance
        if (balanceArray != null && balanceArray.isNotEmpty()) {
            Result.success(balanceArray[0].toDoubleOrNull() ?: 0.0)
        } else {
            Result.failure(Exception("Không thể đọc số dư"))
        }
    }

    /**
     * Cập nhật số dư
     */
    fun updateBalance(balance: Double): Result<Boolean> = executeSafe("Cập nhật số dư") {
        if (smartCard.updateBalance(balance.toString())) {
            Result.success(true)
        } else {
            Result.failure(Exception("Không thể cập nhật số dư"))
        }
    }

    /**
     * Trừ tiền (deduction)
     */
    fun deductBalance(amount: Double): Result<Double> {
        return try {
            val currentBalance = getBalance().getOrElse { 0.0 }
            if (currentBalance < amount) {
                return Result.failure(Exception("Số dư không đủ"))
            }
            val newBalance = currentBalance - amount
            val updateResult = updateBalance(newBalance)
            if (updateResult.isSuccess) {
                Result.success(newBalance)
            } else {
                Result.failure(Exception("Không thể trừ tiền"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi trừ tiền: ${e.message}", e))
        }
    }

    /**
     * Nạp tiền (top-up)
     */
    fun topUpBalance(amount: Double): Result<Double> {
        return try {
            val currentBalance = getBalance().getOrElse { 0.0 }
            val newBalance = currentBalance + amount
            val updateResult = updateBalance(newBalance)
            if (updateResult.isSuccess) {
                Result.success(newBalance)
            } else {
                Result.failure(Exception("Không thể nạp tiền"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi nạp tiền: ${e.message}", e))
        }
    }

    // ========== ẢNH ==========

    /**
     * Cập nhật ảnh từ byte array
     *
     * @param photoBytes Byte array của ảnh
     * @return Result<Boolean> - Success(true) nếu cập nhật thành công
     */
    fun updatePhotoBytes(photoBytes: ByteArray?): Result<Boolean> {
        return try {
            val result = smartCard.updatePicture(photoBytes)
            if (result) {
                Result.success(true)
            } else {
                Result.failure(Exception("Không thể cập nhật ảnh"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi cập nhật ảnh từ bytes: ${e.message}", e))
        }
    }

    // ========== ẢNH ==========

    /**
     * Đọc ảnh khách hàng từ thẻ
     * Trả về Result.success với ByteArray nếu có ảnh, hoặc Result.failure nếu lỗi
     * Lưu ý: Nếu thẻ chưa có ảnh, sẽ trả về Result.failure với message phù hợp
     */
    fun getPicture(): Result<ByteArray> = executeSafe("Đọc ảnh khách hàng") {
        val pictureBytes = smartCard.getPicture()
        if (pictureBytes != null && pictureBytes.isNotEmpty()) {
            Result.success(pictureBytes)
        } else {
            // Thẻ chưa có ảnh hoặc không thể đọc - không phải lỗi nghiêm trọng
            Result.failure(Exception("Thẻ chưa có ảnh hoặc không thể đọc ảnh"))
        }
    }

    // ========== BẢO MẬT ==========

    /**
     * Lấy Public Key RSA
     */
    fun getPublicKey(): Result<ByteArray> {
        return try {
            val publicKey = smartCard.publicKey
            if (publicKey != null) {
                Result.success(publicKey)
            } else {
                Result.failure(Exception("Không thể đọc public key"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi đọc public key: ${e.message}", e))
        }
    }

    // ========== QUẢN LÝ THẺ ==========

    /**
     * Kiểm tra thẻ đã khởi tạo chưa
     */
    fun checkCardCreated(): Result<Boolean> {
        return try {
            val result = smartCard.checkCardCreated()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi kiểm tra thẻ: ${e.message}", e))
        }
    }

    /**
     * Xóa toàn bộ dữ liệu trên thẻ
     */
    fun clearCard(): Result<Boolean> {
        return try {
            println("BusCardManager.clearCard() - Kiem tra ket noi: $isConnected")
            if (!isConnected) {
                return Result.failure(Exception("Chưa kết nối với thẻ. Vui lòng kết nối trước khi xóa."))
            }

            // Kiểm tra thẻ có dữ liệu trước khi xóa
            println("BusCardManager.clearCard() - Kiem tra the co du lieu truoc khi xoa...")
            val hasDataBefore = smartCard.checkCardCreated()
            println("The co du lieu truoc khi xoa: $hasDataBefore")

            if (!hasDataBefore) {
                println("The da rong, khong can xoa")
                return Result.success(true)
            }

            println("BusCardManager.clearCard() - Goi smartCard.clearCard()...")
            val result = smartCard.clearCard()
            println("BusCardManager.clearCard() - Ket qua tu Java clearCard(): $result")

            Thread.sleep(100)
            val hasDataAfter = smartCard.checkCardCreated()
            println("The co du lieu sau khi xoa: $hasDataAfter")

            if (result) {
                if (!hasDataAfter) {
                    println("Xoa the thanh cong - Da xac nhan the rong!")
                    Result.success(true)
                } else {
                    println("clearCard() tra ve true nhung the van con du lieu!")
                    Result.failure(Exception("Xoa the khong hoan toan. The van co du lieu sau khi xoa."))
                }
            } else {
                println("Java clearCard() tra ve false")
                Result.failure(Exception("Khong the xoa du lieu the. Kiem tra console de xem chi tiet loi SW code."))
            }
        } catch (e: Exception) {
            println("Exception khi xoa the: ${e.message}")
            e.printStackTrace()
            Result.failure(Exception("Lỗi xóa thẻ: ${e.message}", e))
        }
    }

    /**
     * Mở khóa thẻ khi bị nhập sai PIN quá nhiều lần
     */
    fun unlockCard(): Result<Boolean> = executeSafe("Mở khóa thẻ") {
        if (smartCard.unlockCard()) {
            Result.success(true)
        } else {
            Result.failure(Exception("Không thể mở khóa thẻ"))
        }
    }
}

/**
 * Data class: Thông tin khách hàng
 */
data class CustomerInfo(
    val fullName: String,
    val customerType: String,    // Khách hàng
    val expiryDate: String,       // dd/MM/yyyy
    val cardType: String,         // Thẻ Thường, Vé Tháng
    val linkedCustomerId: String,
    val cccd: String = "",         // Số CCCD
    val dob: String = "",          // Ngày sinh (dd/MM/yyyy)
    val address: String = "",      // Địa chỉ
    val phone: String = ""         // Số điện thoại
)

/**
 * Data class: Thông tin quẹt thẻ
 */
data class TapInfo(
    val routeId: String,
    val tapType: String,          // TAP_ON, TAP_OFF
    val timestamp: String
)

/**
 * Helper: Thực thi operation an toàn với error handling
 */
private inline fun <T> executeSafe(operationName: String, block: () -> Result<T>): Result<T> {
    return try {
        block()
    } catch (e: Exception) {
        Result.failure(Exception("Lỗi $operationName: ${e.message}", e))
    }
}

