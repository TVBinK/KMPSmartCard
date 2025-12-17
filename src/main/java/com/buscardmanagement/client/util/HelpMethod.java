package com.buscardmanagement.client.util;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
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
import java.util.List;

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
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // JPEG nén tốt hơn, phù hợp với giới hạn 32KB của smart card
            ImageIO.write(image, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            System.err.println("Loi chuyen doi image sang byte array: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Resize + nén ảnh để đảm bảo kích thước <= maxBytes
     * Chiến lược:
     *  1. Giới hạn kích thước tối đa (maxWidth, maxHeight) để tránh ảnh quá lớn.
     *  2. Giảm dần chất lượng JPEG (quality) từ 0.9 xuống 0.2.
     *  3. Nếu vẫn > maxBytes, tiếp tục giảm kích thước ảnh và lặp lại.
     */
    public static byte[] resizeImageToMaxSize(BufferedImage original, int maxBytes, int maxWidth, int maxHeight) {
        if (original == null || maxBytes <= 0) return null;

        // Bước 1: Scale về kích thước hợp lý trước (nếu quá to)
        BufferedImage current = scaleToMaxSize(original, maxWidth, maxHeight);

        // Vòng lặp: thử nhiều lần với giảm kích thước + giảm quality
        int width = current.getWidth();
        int height = current.getHeight();

        while (true) {
            // Thử các mức quality từ cao xuống thấp
            float[] qualities = new float[]{0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f, 0.25f, 0.2f};
            for (float q : qualities) {
                byte[] jpegBytes = encodeJpegWithQuality(current, q);
                if (jpegBytes == null || jpegBytes.length == 0) continue;
                if (jpegBytes.length <= maxBytes) {
                    return jpegBytes;
                }
            }

            // Nếu đã thử hết quality mà vẫn quá lớn → giảm kích thước và thử lại
            int newWidth = (int) (width * 0.8);
            int newHeight = (int) (height * 0.8);

            if (newWidth < 50 || newHeight < 50) {
                // Quá nhỏ rồi mà vẫn không đạt -> trả null
                System.err.println("Khong the giam anh xuong duoi " + maxBytes + " bytes ma khong mat qua nhieu chat luong.");
                return null;
            }

            width = newWidth;
            height = newHeight;
            current = scaleToExactSize(current, width, height);
        }
    }

    /** Scale ảnh về kích thước tối đa (giữ tỉ lệ) */
    private static BufferedImage scaleToMaxSize(BufferedImage src, int maxWidth, int maxHeight) {
        int width = src.getWidth();
        int height = src.getHeight();

        double ratio = Math.min(
                (double) maxWidth / width,
                (double) maxHeight / height
        );

        if (ratio >= 1.0) {
            // Ảnh đã nhỏ hơn max, giữ nguyên
            return src;
        }

        int newWidth = Math.max(1, (int) (width * ratio));
        int newHeight = Math.max(1, (int) (height * ratio));
        return scaleToExactSize(src, newWidth, newHeight);
    }

    /** Scale ảnh về kích thước chỉ định với chất lượng mượt */
    private static BufferedImage scaleToExactSize(BufferedImage src, int newWidth, int newHeight) {
        Image scaled = src.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resized.createGraphics();
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();
        return resized;
    }

    /** Mã hóa BufferedImage sang JPEG với quality tùy chỉnh */
    private static byte[] encodeJpegWithQuality(BufferedImage image, float quality) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
            ios.close();
            writer.dispose();
            return baos.toByteArray();
        } catch (Exception e) {
            System.err.println("Loi ma hoa JPEG voi chat luong " + quality + ": " + e.getMessage());
            return null;
        }
    }
}
