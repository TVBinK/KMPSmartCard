package com.buscardmanagement.client.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;

/**
 * HelpMethod - Các hàm tiện ích cho Bus Card Management System
 * 
 * Chức năng:
 * - Chuyển đổi String <-> Byte Array
 * - Chuyển đổi Image <-> Byte Array
 * - Xác thực chữ ký số RSA
 * - Tạo chuỗi ngẫu nhiên
 */
public class HelpMethod {

    /**
     * Chuyển đổi String sang Byte Array (UTF-8)
     * 
     * @param input Chuỗi cần chuyển đổi
     * @return Byte array
     */
    public static byte[] ConvertStringToByteArr(String input) {
        if (input == null) {
            return new byte[0];
        }
        return input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Chuyển đổi Byte Array sang String Array
     * 
     * Dùng cho dữ liệu thẻ xe bus:
     * - String[0]: Họ tên
     * - String[1]: Loại đối tượng (HSSV/Người cao tuổi/Thông thường)
     * - String[2]: Ngày hết hạn (dd/MM/yyyy)
     * - String[3]: Loại thẻ (Vé Lượt/Vé Tháng)
     * - String[4]: Mã khách hàng liên kết (optional)
     * 
     * @param byteArray Byte array cần chuyển đổi
     * @param delimiter Ký tự phân cách (thường là '.')
     * @return String array
     */
    public static String[] convertByteToStringArr(byte[] byteArray, char delimiter) {
        if (byteArray == null || byteArray.length == 0) {
            return new String[0];
        }

        // Chuyển byte array sang string (UTF-8)
        String str = new String(byteArray, java.nio.charset.StandardCharsets.UTF_8);

        // Tách chuỗi theo delimiter
        List<String> parts = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        
        for (char c : str.toCharArray()) {
            if (c == delimiter) {
                parts.add(currentPart.toString());
                currentPart.setLength(0);
            } else {
                currentPart.append(c);
            }
        }
        
        // Thêm phần cuối cùng
        parts.add(currentPart.toString());

        return parts.toArray(new String[0]);
    }

    /**
     * Chuyển đổi Byte Array sang BufferedImage
     * 
     * @param byteArray Byte array chứa dữ liệu ảnh (PNG/JPEG)
     * @return BufferedImage hoặc null nếu lỗi
     */
    public static BufferedImage convertByteArrayToImage(byte[] byteArray) {
        try {
            if (byteArray == null || byteArray.length == 0) {
                return null;
            }
            ByteArrayInputStream bis = new ByteArrayInputStream(byteArray);
            return ImageIO.read(bis);
        } catch (Exception e) {
            System.err.println("Loi chuyen doi byte array sang image: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Chuyển đổi BufferedImage sang Byte Array (PNG format)
     * 
     * @param image BufferedImage cần chuyển đổi
     * @return Byte array hoặc null nếu lỗi
     */
    public static byte[] convertImageToByteArray(BufferedImage image) {
        try {
            if (image == null) {
                return null;
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            System.err.println("Loi chuyen doi image sang byte array: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Tạo chuỗi ngẫu nhiên
     * 
     * @param length Độ dài chuỗi cần tạo
     * @return Chuỗi ngẫu nhiên
     */
    public static String generateRandomString(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(characters.length());
            sb.append(characters.charAt(index));
        }
        
        return sb.toString();
    }

    /**
     * Xác thực chữ ký số RSA
     * 
     * @param publicKeyBytes Public key từ card (format: [Modulus][Exponent][ModLen(2)][ExpLen(2)])
     * @param dataToVerify Dữ liệu gốc
     * @param signedData Chữ ký cần xác thực
     * @return true nếu chữ ký hợp lệ, false nếu không
     * @throws Exception Nếu có lỗi trong quá trình xác thực
     */
    public static boolean verifySignature(byte[] publicKeyBytes, byte[] dataToVerify, byte[] signedData) throws Exception {
        try {
            // Parse độ dài modulus và exponent (4 bytes cuối)
            short expLen = ByteBuffer.wrap(new byte[]{
                publicKeyBytes[publicKeyBytes.length - 2], 
                publicKeyBytes[publicKeyBytes.length - 1]
            }).getShort();
            
            short modLen = ByteBuffer.wrap(new byte[]{
                publicKeyBytes[publicKeyBytes.length - 4], 
                publicKeyBytes[publicKeyBytes.length - 3]
            }).getShort();

            // Extract modulus và exponent
            byte[] modulusBytes = Arrays.copyOfRange(publicKeyBytes, 0, modLen);
            byte[] exponentBytes = Arrays.copyOfRange(publicKeyBytes, modLen, modLen + expLen);

            // Tạo BigInteger
            BigInteger modulus = new BigInteger(1, modulusBytes);
            BigInteger exponent = new BigInteger(1, exponentBytes);

            // Tạo RSA Public Key
            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(modulus, exponent);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);

            // Xác thực chữ ký
            Signature rsaSign = Signature.getInstance("MD5withRSA");
            rsaSign.initVerify(publicKey);
            rsaSign.update(dataToVerify);
            
            return rsaSign.verify(signedData);
        } catch (Exception e) {
            System.err.println("Loi xac thuc chu ky: " + e.getMessage());
            throw e;
        }
    }


    /**
     * Chuyển đổi byte array sang hex string (dùng cho debug)
     * 
     * @param bytes Byte array
     * @return Hex string
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    /**
     * In log debug với timestamp
     * 
     * @param message Thông điệp
     */
    public static void debugLog(String message) {
        System.out.println("[BusCard " + new Date() + "] " + message);
    }

}

