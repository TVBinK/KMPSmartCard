package security

import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.Charsets.UTF_8

/**
 * SecurityUtils - Utility class cho mã hóa và bảo mật
 * 
 * Chức năng:
 * - Mã hóa AES với khóa từ PIN
 * - Mã hóa RSA cho giao dịch
 * - Tạo và quản lý khóa RSA
 */
object SecurityUtils {
    
    private const val AES_ALGORITHM = "AES"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 16
    private const val RSA_ALGORITHM = "RSA"
    private const val RSA_KEY_SIZE = 2048
    
    // ==================== AES ENCRYPTION (Từ PIN) ====================
    
    /**
     * Tạo khóa AES từ PIN
     * Sử dụng PBKDF2 để tạo khóa 256-bit từ PIN
     */
    private fun deriveKeyFromPin(pin: String): SecretKey {
        val salt = "BUS_CARD_SALT_2025".toByteArray(UTF_8) // Salt cố định cho hệ thống
        val keySpec = javax.crypto.spec.PBEKeySpec(
            pin.toCharArray(),
            salt,
            65536, // 65536 iterations
            256 // 256-bit key
        )
        val keyFactory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = keyFactory.generateSecret(keySpec).encoded
        return SecretKeySpec(keyBytes, AES_ALGORITHM)
    }
    
    /**
     * Mã hóa dữ liệu bằng AES với khóa từ PIN
     * @return ByteArray đã mã hóa (IV + encrypted data)
     */
    fun encryptWithPin(data: String, pin: String): ByteArray = try {
        val secretKey = deriveKeyFromPin(pin)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        
        // Tạo IV ngẫu nhiên
        val iv = ByteArray(GCM_IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
        
        val encryptedData = cipher.doFinal(data.toByteArray(UTF_8))
        
        // Kết hợp IV + encrypted data
        iv + encryptedData
    } catch (e: Exception) {
        throw SecurityException("Lỗi mã hóa dữ liệu: ${e.message}", e)
    }
    
    /**
     * Mã hóa nhiều trường dữ liệu cùng lúc
     */
    fun encryptCustomerData(
        fullName: String, cccd: String, dob: String, address: String, phone: String, pin: String
    ): Map<String, ByteArray> = mapOf(
        "fullName" to encryptWithPin(fullName, pin),
        "cccd" to encryptWithPin(cccd, pin),
        "dob" to encryptWithPin(dob, pin),
        "address" to encryptWithPin(address, pin),
        "phone" to encryptWithPin(phone, pin)
    )
    
    // ==================== RSA ENCRYPTION (Cho giao dịch) ====================
    
    /**
     * Tạo cặp khóa RSA mới
     */
    fun generateRSAKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(RSA_ALGORITHM)
        keyPairGenerator.initialize(RSA_KEY_SIZE)
        return keyPairGenerator.generateKeyPair()
    }
    
    /**
     * Chuyển PublicKey thành Base64 string
     */
    fun publicKeyToBase64(publicKey: PublicKey): String = 
        java.util.Base64.getEncoder().encodeToString(publicKey.encoded)
    
    /**
     * Chuyển PrivateKey thành Base64 string
     */
    fun privateKeyToBase64(privateKey: PrivateKey): String = 
        java.util.Base64.getEncoder().encodeToString(privateKey.encoded)

    /**
     * Chuyển ByteArray thành Base64 string
     */
    fun bytesToBase64(bytes: ByteArray): String = 
        java.util.Base64.getEncoder().encodeToString(bytes)
}

