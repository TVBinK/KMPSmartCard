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
    
    // ========== KẾT NỐI ==========
    
    /**
     * Kết nối với smart card
     * 
     * @return Result<Boolean> - Success(true) nếu kết nối thành công
     */
    fun connect(): Result<Boolean> {
        return try {
            val result = smartCard.connectCard()
            if (result) {
                Result.success(true)
            } else {
                Result.failure(Exception("Không thể kết nối với thẻ. Vui lòng kiểm tra card simulator đang chạy tại localhost:9025"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi kết nối: ${e.message}", e))
        }
    }
    
    /**
     * Ngắt kết nối với smart card
     */
    fun disconnect(): Result<Boolean> {
        return try {
            val result = smartCard.disconnect()
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi ngắt kết nối: ${e.message}", e))
        }
    }
    
    // ========== THÔNG TIN KHÁCH HÀNG ==========
    
    /**
     * Lấy thông tin khách hàng từ thẻ
     * 
     * @return Result<CustomerInfo> - Thông tin khách hàng
     */
    fun getCustomerInfo(): Result<CustomerInfo> {
        return try {
            val info = smartCard.customerInfo
            if (info != null && info.size >= 5) {
                Result.success(
                    CustomerInfo(
                        fullName = info.getOrNull(0) ?: "",
                        customerType = info.getOrNull(1) ?: "",
                        expiryDate = info.getOrNull(2) ?: "",
                        cardType = info.getOrNull(3) ?: "",
                        linkedCustomerId = info.getOrNull(4) ?: ""
                    )
                )
            } else {
                Result.failure(Exception("Không thể đọc thông tin khách hàng từ thẻ"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi đọc thông tin: ${e.message}", e))
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
    ): Result<Boolean> {
        return try {
            val result = smartCard.updateCustomerInfo(
                fullName, customerType, expiryDate, cardType, linkedCustomerId
            )
            if (result) {
                Result.success(true)
            } else {
                Result.failure(Exception("Không thể cập nhật thông tin khách hàng"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi cập nhật thông tin: ${e.message}", e))
        }
    }
    
    // ========== CARD ID ==========
    
    /**
     * Lấy Card ID từ thẻ
     */
    fun getCardId(): Result<String> {
        return try {
            val cardIdArray = smartCard.cardId
            if (cardIdArray != null && cardIdArray.isNotEmpty()) {
                Result.success(cardIdArray[0])
            } else {
                Result.failure(Exception("Không thể đọc Card ID"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi đọc Card ID: ${e.message}", e))
        }
    }
    
    /**
     * Cập nhật Card ID
     */
    fun updateCardId(cardId: String): Result<Boolean> {
        return try {
            val result = smartCard.updateCardId(cardId)
            if (result) {
                Result.success(true)
            } else {
                Result.failure(Exception("Không thể cập nhật Card ID"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi cập nhật Card ID: ${e.message}", e))
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
    fun getBalance(): Result<Double> {
        return try {
            val balanceArray = smartCard.balance
            if (balanceArray != null && balanceArray.isNotEmpty()) {
                val balanceStr = balanceArray[0]
                Result.success(balanceStr.toDoubleOrNull() ?: 0.0)
            } else {
                Result.failure(Exception("Không thể đọc số dư"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi đọc số dư: ${e.message}", e))
        }
    }
    
    /**
     * Cập nhật số dư
     */
    fun updateBalance(balance: Double): Result<Boolean> {
        return try {
            val result = smartCard.updateBalance(balance.toString())
            if (result) {
                Result.success(true)
            } else {
                Result.failure(Exception("Không thể cập nhật số dư"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Lỗi cập nhật số dư: ${e.message}", e))
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
                // Convert byte array to BufferedImage
                val inputStream = java.io.ByteArrayInputStream(photoBytes)
                val image = javax.imageio.ImageIO.read(inputStream)
                updatePicture(image)
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
            val result = smartCard.clearCard()
            if (result) {
                Result.success(true)
            } else {
                Result.failure(Exception("Không thể xóa dữ liệu thẻ"))
            }
        } catch (e: Exception) {
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
    val customerType: String,    // HSSV, Người cao tuổi, Thông thường
    val expiryDate: String,       // dd/MM/yyyy
    val cardType: String,         // Vé Lượt, Vé Tháng
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

