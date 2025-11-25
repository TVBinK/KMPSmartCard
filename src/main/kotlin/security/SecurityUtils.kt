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
    private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
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
     * Giải mã dữ liệu bằng AES với khóa từ PIN
     * @return String đã giải mã
     */
    fun decryptWithPin(encryptedData: ByteArray, pin: String): String = try {
        val secretKey = deriveKeyFromPin(pin)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        
        // Tách IV và encrypted data
        val iv = encryptedData.sliceArray(0 until GCM_IV_LENGTH)
        val encrypted = encryptedData.sliceArray(GCM_IV_LENGTH until encryptedData.size)
        
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        
        String(cipher.doFinal(encrypted), UTF_8)
    } catch (e: Exception) {
        throw SecurityException("Lỗi giải mã dữ liệu: ${e.message}", e)
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
    
    /**
     * Giải mã dữ liệu khách hàng
     */
    fun decryptCustomerData(
        encryptedData: Map<String, ByteArray>, pin: String
    ): Map<String, String> = encryptedData.mapValues { (_, value) -> decryptWithPin(value, pin) }
    
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
     * Mã hóa dữ liệu bằng RSA public key
     */
    fun encryptWithRSA(data: String, publicKey: PublicKey): ByteArray = try {
        Cipher.getInstance(RSA_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, publicKey)
        }.doFinal(data.toByteArray(UTF_8))
    } catch (e: Exception) {
        throw SecurityException("Lỗi mã hóa RSA: ${e.message}", e)
    }
    
    /**
     * Giải mã dữ liệu bằng RSA private key
     */
    fun decryptWithRSA(encryptedData: ByteArray, privateKey: PrivateKey): String = try {
        String(Cipher.getInstance(RSA_TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, privateKey)
        }.doFinal(encryptedData), UTF_8)
    } catch (e: Exception) {
        throw SecurityException("Lỗi giải mã RSA: ${e.message}", e)
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
     * Chuyển Base64 string thành PublicKey
     */
    fun base64ToPublicKey(base64Key: String): PublicKey = try {
        val keyBytes = java.util.Base64.getDecoder().decode(base64Key)
        KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(X509EncodedKeySpec(keyBytes))
    } catch (e: Exception) {
        throw SecurityException("Lỗi chuyển đổi PublicKey: ${e.message}", e)
    }
    
    /**
     * Chuyển Base64 string thành PrivateKey
     */
    fun base64ToPrivateKey(base64Key: String): PrivateKey = try {
        val keyBytes = java.util.Base64.getDecoder().decode(base64Key)
        KeyFactory.getInstance(RSA_ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    } catch (e: Exception) {
        throw SecurityException("Lỗi chuyển đổi PrivateKey: ${e.message}", e)
    }
    
    /**
     * Mã hóa thông tin giao dịch bằng RSA
     * Tạo JSON string và mã hóa
     */
    fun encryptTransactionData(
        cardId: String,
        transactionType: String,
        amount: Double,
        timestamp: String,
        publicKey: PublicKey
    ): ByteArray {
        val transactionJson = """
            {
                "cardId": "$cardId",
                "transactionType": "$transactionType",
                "amount": $amount,
                "timestamp": "$timestamp"
            }
        """.trimIndent()
        
        return encryptWithRSA(transactionJson, publicKey)
    }
    
    /**
     * Tạo chữ ký số cho giao dịch (sử dụng private key)
     */
    fun signTransaction(
        transactionData: String,
        privateKey: PrivateKey
    ): ByteArray {
        return try {
            val signature = Signature.getInstance("SHA256withRSA")
            signature.initSign(privateKey)
            signature.update(transactionData.toByteArray(UTF_8))
            signature.sign()
        } catch (e: Exception) {
            throw SecurityException("Lỗi tạo chữ ký số: ${e.message}", e)
        }
    }
    
    /**
     * Xác thực chữ ký số (sử dụng public key)
     */
    fun verifySignature(
        transactionData: String,
        signature: ByteArray,
        publicKey: PublicKey
    ): Boolean {
        return try {
            val sig = Signature.getInstance("SHA256withRSA")
            sig.initVerify(publicKey)
            sig.update(transactionData.toByteArray(UTF_8))
            sig.verify(signature)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Chuyển ByteArray thành Base64 string
     */
    fun bytesToBase64(bytes: ByteArray): String = 
        java.util.Base64.getEncoder().encodeToString(bytes)
    
    /**
     * Chuyển Base64 string thành ByteArray
     */
    fun base64ToBytes(base64: String): ByteArray = 
        java.util.Base64.getDecoder().decode(base64)
}

