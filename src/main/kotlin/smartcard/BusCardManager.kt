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
            Result.failure(Exception(
                "Không thể kết nối với thẻ. Vui lòng đảm bảo:\n" +
                "1. JCIDE Simulator đang chạy\n" +
                "2. Applet đã được cài đặt trên simulator\n" +
                "3. Hoặc kết nối Java Card thật qua PC/SC reader"
            ))
        }
    }
    
    /**
     * Ngắt kết nối với smart card
     */
    fun disconnect(): Result<Boolean> = executeSafe("Ngắt kết nối") {
        Result.success(smartCard.disconnect())
    }
    
    // ========== THÔNG TIN KHÁCH HÀNG ==========
    
    /**
     * Lấy thông tin khách hàng từ thẻ
     */
    fun getCustomerInfo(): Result<CustomerInfo> = executeSafe("Đọc thông tin khách hàng") {
        val info = smartCard.customerInfo
        if (info != null && info.size >= 5) {
            Result.success(CustomerInfo(
                fullName = info.getOrNull(0) ?: "",
                customerType = info.getOrNull(1) ?: "",
                expiryDate = info.getOrNull(2) ?: "",
                cardType = info.getOrNull(3) ?: "",
                linkedCustomerId = info.getOrNull(4) ?: ""
            ))
        } else {
            Result.failure(Exception("Không thể đọc thông tin khách hàng từ thẻ"))
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
        linkedCustomerId: String = ""
    ): Result<Boolean> = executeSafe("Cập nhật thông tin khách hàng") {
        val result = smartCard.updateCustomerInfo(fullName, customerType, expiryDate, cardType, linkedCustomerId)
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
            Result.failure(Exception("Không thể đọc Card ID"))
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
    fun updatePin(newPin: String): Result<Boolean> {
        return try {
            if (newPin.length < 4) {
                return Result.failure(Exception("PIN phải có ít nhất 4 ký tự"))
            }
            val result = smartCard.updatePin(newPin)
            if (result) {
                Result.success(true)
            } else {
                Result.failure(Exception("Không thể cập nhật PIN"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi cập nhật PIN: ${e.message}", e))
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
    
    /**
     * Xác thực PIN đơn giản (không counter)
     */
    fun verifyPin(pin: String): Result<Boolean> {
        return try {
            val result = smartCard.verifyPin(pin)
            if (result) {
                Result.success(true)
            } else {
                Result.failure(Exception("PIN không đúng"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi xác thực PIN: ${e.message}", e))
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
     * Lấy ảnh khách hàng
     */
    fun getPicture(): Result<BufferedImage> {
        return try {
            val image = smartCard.picture
            if (image != null) {
                Result.success(image)
            } else {
                Result.failure(Exception("Không thể đọc ảnh"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi đọc ảnh: ${e.message}", e))
        }
    }
    
    /**
     * Cập nhật ảnh khách hàng
     */
    fun updatePicture(image: BufferedImage): Result<Boolean> {
        return try {
            val result = smartCard.updatePicture(image)
            if (result) {
                Result.success(true)
            } else {
                Result.failure(Exception("Không thể cập nhật ảnh"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi cập nhật ảnh: ${e.message}", e))
        }
    }
    
    /**
     * Cập nhật ảnh từ byte array
     * 
     * @param photoBytes Byte array của ảnh
     * @return Result<Boolean> - Success(true) nếu cập nhật thành công
     */
    fun updatePhotoBytes(photoBytes: ByteArray?): Result<Boolean> {
        return try {
            if (photoBytes == null || photoBytes.isEmpty()) {
                // Nếu không có ảnh, bỏ qua
                Result.success(true)
            } else {
                val result = smartCard.updatePicture(photoBytes)
                if (result) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Không thể cập nhật ảnh"))
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi cập nhật ảnh từ bytes: ${e.message}", e))
        }
    }
    
    /**
     * Đọc ảnh từ thẻ dạng byte array
     * 
     * @return Result<ByteArray?> - Byte array của ảnh hoặc null nếu không có
     */
    fun getPhotoBytes(): Result<ByteArray?> {
        return try {
            val picture = smartCard.picture
            if (picture != null) {
                // Convert BufferedImage to byte array
                val baos = java.io.ByteArrayOutputStream()
                javax.imageio.ImageIO.write(picture, "jpg", baos)
                Result.success(baos.toByteArray())
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi đọc ảnh từ thẻ: ${e.message}", e))
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
    
    /**
     * Xác thực thẻ bằng chữ ký số RSA
     */
    fun verifyCard(): Result<Boolean> {
        return try {
            val publicKey = getPublicKey().getOrElse { 
                return Result.failure(Exception("Không thể lấy public key để xác thực"))
            }
            val result = smartCard.verifyCard(publicKey)
            if (result) {
                Result.success(true)
            } else {
                Result.failure(Exception("Thẻ không hợp lệ"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi xác thực thẻ: ${e.message}", e))
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
     * Khóa thẻ
     */
    fun lockCard(): Result<Boolean> {
        return try {
            val result = smartCard.lockCard()
            if (result) {
                Result.success(true)
            } else {
                Result.failure(Exception("Không thể khóa thẻ"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi khóa thẻ: ${e.message}", e))
        }
    }
    
    /**
     * Mở khóa thẻ
     */
    fun unlockCard(): Result<Boolean> {
        return try {
            val result = smartCard.unlockCard()
            if (result) {
                Result.success(true)
            } else {
                Result.failure(Exception("Không thể mở khóa thẻ"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi mở khóa thẻ: ${e.message}", e))
        }
    }
    
    // ========== QUẸT THẺ ==========
    
    /**
     * Cập nhật thông tin quẹt thẻ gần nhất
     */
    fun updateLastTapInfo(routeId: String, tapType: String, timestamp: String): Result<Boolean> {
        return try {
            val result = smartCard.updateLastTapInfo(routeId, tapType, timestamp)
            if (result) {
                Result.success(true)
            } else {
                Result.failure(Exception("Không thể cập nhật thông tin quẹt thẻ"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi cập nhật quẹt thẻ: ${e.message}", e))
        }
    }
    
    /**
     * Lấy thông tin quẹt thẻ gần nhất
     */
    fun getLastTapInfo(): Result<TapInfo> {
        return try {
            val tapInfoArray = smartCard.lastTapInfo
            if (tapInfoArray != null && tapInfoArray.size >= 3) {
                Result.success(
                    TapInfo(
                        routeId = tapInfoArray[0],
                        tapType = tapInfoArray[1],
                        timestamp = tapInfoArray[2]
                    )
                )
            } else {
                Result.failure(Exception("Không có thông tin quẹt thẻ"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi đọc thông tin quẹt thẻ: ${e.message}", e))
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
    val linkedCustomerId: String
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

