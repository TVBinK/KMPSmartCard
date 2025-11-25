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
 */
public class HelpMethod {

    public static byte[] ConvertStringToByteArr(String input) {
        if (input == null) {
            return new byte[0];
        }
        return input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static String[] convertByteToStringArr(byte[] byteArray, char delimiter) {
        if (byteArray == null || byteArray.length == 0) {
            return new String[0];
        }

        String str = new String(byteArray, java.nio.charset.StandardCharsets.UTF_8);

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
        
        parts.add(currentPart.toString());

        return parts.toArray(new String[0]);
    }

    public static BufferedImage convertByteArrayToImage(byte[] byteArray) {
        try {
            if (byteArray == null || byteArray.length == 0) {
                return null;
            }
            ByteArrayInputStream bis = new ByteArrayInputStream(byteArray);
            return ImageIO.read(bis);
        } catch (Exception e) {
            System.err.println("Loi chuyen doi byte array sang image: " + e.getMessage());
            return null;
        }
    }

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
            return null;
        }
    }

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

    public static boolean verifySignature(byte[] publicKeyBytes, byte[] dataToVerify, byte[] signedData) throws Exception {
        try {
            short expLen = ByteBuffer.wrap(new byte[]{
                publicKeyBytes[publicKeyBytes.length - 2], 
                publicKeyBytes[publicKeyBytes.length - 1]
            }).getShort();
            
            short modLen = ByteBuffer.wrap(new byte[]{
                publicKeyBytes[publicKeyBytes.length - 4], 
                publicKeyBytes[publicKeyBytes.length - 3]
            }).getShort();

            byte[] modulusBytes = Arrays.copyOfRange(publicKeyBytes, 0, modLen);
            byte[] exponentBytes = Arrays.copyOfRange(publicKeyBytes, modLen, modLen + expLen);

            BigInteger modulus = new BigInteger(1, modulusBytes);
            BigInteger exponent = new BigInteger(1, exponentBytes);

            RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(modulus, exponent);
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);

            Signature rsaSign = Signature.getInstance("MD5withRSA");
            rsaSign.initVerify(publicKey);
            rsaSign.update(dataToVerify);
            
            return rsaSign.verify(signedData);
        } catch (Exception e) {
            System.err.println("Loi xac thuc chu ky: " + e.getMessage());
            throw e;
        }
    }
}
