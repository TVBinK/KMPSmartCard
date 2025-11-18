package com.buscardmanagement.simulator;

import com.licel.jcardsim.io.JavaxSmartCardInterface;
import javacard.framework.AID;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Chạy jCardSim như một server lắng nghe trên port 9025
 * Xử lý APDU commands và trả về response cho client
 */
public class JCardSimServer {
    private static final int PORT = 9025;
    private static final byte[] APPLET_AID = {0x11, 0x22, 0x33, 0x44, 0x55, 0x00, 0x01};
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  jCardSim Server Starting");
        System.out.println("========================================");
        System.out.println("Port: " + PORT);
        System.out.println("AID: " + bytesToHex(APPLET_AID));
        System.out.println();
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✓ Server listening on port " + PORT);
            System.out.println("Waiting for client connection...");
            System.out.println();
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("✓ Client connected: " + clientSocket.getInetAddress());
                
                // Handle client in new thread
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("✗ Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void handleClient(Socket socket) {
        // Simulated card storage - BAN ĐẦU RỖNG
        Map<String, String> cardData = new HashMap<>();
        boolean isInitialized = false; // Thẻ chưa được khởi tạo
        boolean appletSelected = false;
        
        try (
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {
            System.out.println("✓ Card simulator ready");
            System.out.println();
            
            // Process APDU commands
            while (true) {
                // Đọc length (2 bytes, big-endian) theo protocol của client
                int length = in.readUnsignedShort();
                if (length <= 0) break;
                
                byte[] apduBytes = new byte[length];
                in.readFully(apduBytes);
                
                System.out.println("→ Received APDU: " + bytesToHex(apduBytes));
                
                // Xử lý APDU với trạng thái khởi tạo
                Object[] result = processAPDU(apduBytes, cardData, isInitialized);
                byte[] response = (byte[]) result[0];
                isInitialized = (boolean) result[1];
                
                // Gửi length (2 bytes, big-endian) theo protocol của client
                out.writeShort(response.length);
                out.write(response);
                out.flush();
                
                // Extract status word (last 2 bytes)
                String sw = String.format("%02X%02X", 
                    response[response.length-2] & 0xFF, 
                    response[response.length-1] & 0xFF);
                System.out.println("← Sent response: " + bytesToHex(response) + " (SW: " + sw + ")");
            }
        } catch (IOException e) {
            System.out.println("Client disconnected");
        }
    }
    
    private static Object[] processAPDU(byte[] apdu, Map<String, String> cardData, boolean isInitialized) {
        if (apdu.length < 4) {
            return new Object[]{new byte[]{(byte)0x6F, 0x00}, isInitialized}; // SW_UNKNOWN
        }
        
        byte cla = apdu[0];
        byte ins = apdu[1];
        byte p1 = apdu[2];
        byte p2 = apdu[3];
        
        // SELECT APPLET (00 A4 04 00) - Luôn cho phép
        if (ins == (byte)0xA4 && p1 == 0x04) {
            System.out.println("   → SELECT APPLET");
            return new Object[]{new byte[]{(byte)0x90, 0x00}, isInitialized};
        }
        
        // Check Card Created (00 29 00 00) - Luôn cho phép kiểm tra
        if (ins == 0x29) {
            System.out.println("   → CHECK CARD CREATED");
            if (isInitialized) {
                return new Object[]{new byte[]{(byte)0x90, 0x00}, isInitialized}; // 9000 = đã khởi tạo
            } else {
                return new Object[]{new byte[]{(byte)0x6A, (byte)0x88}, isInitialized}; // 6A88 = chưa khởi tạo
            }
        }
        
        // Update Customer Info (00 20 00 00) - Dùng để KHỞI TẠO thẻ
        if (ins == 0x20) {
            System.out.println("   → UPDATE CUSTOMER INFO (INITIALIZE CARD)");
            if (apdu.length > 5) {
                int dataLen = apdu[4] & 0xFF;
                int dataOffset = 5;
                
                // Check for extended length
                if (dataLen == 0 && apdu.length > 6) {
                    // Extended length (3 bytes)
                    dataLen = ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF);
                    dataOffset = 7;
                }
                
                // Tính actual data length có sẵn trong APDU
                int actualDataLen = apdu.length - dataOffset;
                
                // Kiểm tra xem có đủ dữ liệu không
                if (actualDataLen <= 0) {
                    System.out.println("   → ERROR: No data in APDU");
                    return new Object[]{new byte[]{(byte)0x6A, (byte)0x80}, isInitialized}; // 6A80 = Incorrect parameters
                }
                
                // Sử dụng actual length thay vì declared length để tránh lỗi
                int lengthToCopy = Math.min(dataLen, actualDataLen);
                System.out.println("   → APDU length: " + apdu.length + ", declared dataLen: " + dataLen + ", actual data: " + actualDataLen + ", copying: " + lengthToCopy);
                
                byte[] data = new byte[lengthToCopy];
                System.arraycopy(apdu, dataOffset, data, 0, lengthToCopy);
                String info = new String(data);
                cardData.put("customerInfo", info);
                isInitialized = true; // Đánh dấu đã khởi tạo
                System.out.println("   → Card INITIALIZED with: " + info);
            }
            return new Object[]{new byte[]{(byte)0x90, 0x00}, isInitialized};
        }
        
        // Các lệnh khác YÊU CẦU thẻ phải đã khởi tạo
        if (!isInitialized) {
            System.out.println("   → ERROR: Card not initialized (SW: 6A88)");
            return new Object[]{new byte[]{(byte)0x6A, (byte)0x88}, isInitialized}; // 6A88 = Data not found
        }
        
        // Get Customer Info (00 13 00 00)
        if (ins == 0x13) {
            System.out.println("   → GET CUSTOMER INFO");
            String info = cardData.get("customerInfo");
            if (info == null) {
                return new Object[]{new byte[]{(byte)0x6A, (byte)0x88}, isInitialized};
            }
            byte[] data = info.getBytes();
            byte[] response = new byte[data.length + 2];
            System.arraycopy(data, 0, response, 0, data.length);
            response[data.length] = (byte)0x90;
            response[data.length + 1] = 0x00;
            return new Object[]{response, isInitialized};
        }
        
        // Get Balance (00 14 00 00)
        if (ins == 0x14) {
            System.out.println("   → GET BALANCE");
            String balance = cardData.getOrDefault("balance", "0");
            byte[] data = balance.getBytes();
            byte[] response = new byte[data.length + 2];
            System.arraycopy(data, 0, response, 0, data.length);
            response[data.length] = (byte)0x90;
            response[data.length + 1] = 0x00;
            return new Object[]{response, isInitialized};
        }
        
        // Update Balance (00 16 00 00)
        if (ins == 0x16) {
            System.out.println("   → UPDATE BALANCE");
            if (apdu.length > 5) {
                int dataLen = apdu[4] & 0xFF;
                byte[] data = new byte[dataLen];
                System.arraycopy(apdu, 5, data, 0, dataLen);
                String newBalance = new String(data);
                cardData.put("balance", newBalance);
                System.out.println("   → New balance: " + newBalance);
            }
            return new Object[]{new byte[]{(byte)0x90, 0x00}, isInitialized};
        }
        
        // Get Card ID (00 27 00 00)
        if (ins == 0x27) {
            System.out.println("   → GET CARD ID");
            String cardId = cardData.getOrDefault("cardId", "");
            byte[] data = cardId.getBytes();
            byte[] response = new byte[data.length + 2];
            System.arraycopy(data, 0, response, 0, data.length);
            response[data.length] = (byte)0x90;
            response[data.length + 1] = 0x00;
            return new Object[]{response, isInitialized};
        }
        
        // Update Card ID (00 26 00 00)
        if (ins == 0x26) {
            System.out.println("   → UPDATE CARD ID");
            if (apdu.length > 5) {
                int dataLen = apdu[4] & 0xFF;
                byte[] data = new byte[dataLen];
                System.arraycopy(apdu, 5, data, 0, dataLen);
                String newCardId = new String(data);
                cardData.put("cardId", newCardId);
                System.out.println("   → New Card ID: " + newCardId);
            }
            return new Object[]{new byte[]{(byte)0x90, 0x00}, isInitialized};
        }
        
        // Update PIN (00 21 00 00)
        if (ins == 0x21) {
            System.out.println("   → UPDATE PIN");
            if (apdu.length > 5) {
                int dataLen = apdu[4] & 0xFF;
                byte[] data = new byte[dataLen];
                System.arraycopy(apdu, 5, data, 0, dataLen);
                String newPin = new String(data);
                cardData.put("pin", newPin);
                System.out.println("   → New PIN: " + newPin);
            }
            return new Object[]{new byte[]{(byte)0x90, 0x00}, isInitialized};
        }
        
        // Update Picture (00 22 00 00) - Lưu ảnh dạng Base64 string
        if (ins == 0x22) {
            System.out.println("   → UPDATE PICTURE");
            if (apdu.length > 5) {
                int dataLen = apdu[4] & 0xFF;
                int dataOffset = 5;
                
                // Check for extended length (3 bytes)
                if (dataLen == 0 && apdu.length > 6) {
                    dataLen = ((apdu[5] & 0xFF) << 8) | (apdu[6] & 0xFF);
                    dataOffset = 7;
                }
                
                // Tính actual data length
                int actualDataLen = apdu.length - dataOffset;
                
                if (actualDataLen <= 0) {
                    System.out.println("   → ERROR: No picture data");
                    return new Object[]{new byte[]{(byte)0x6A, (byte)0x80}, isInitialized};
                }
                
                // Copy picture data
                int lengthToCopy = Math.min(dataLen, actualDataLen);
                byte[] pictureData = new byte[lengthToCopy];
                System.arraycopy(apdu, dataOffset, pictureData, 0, lengthToCopy);
                
                // Lưu ảnh dạng Base64 để dễ xử lý
                String pictureBase64 = java.util.Base64.getEncoder().encodeToString(pictureData);
                cardData.put("picture", pictureBase64);
                System.out.println("   → Picture updated: " + lengthToCopy + " bytes");
            }
            return new Object[]{new byte[]{(byte)0x90, 0x00}, isInitialized};
        }
        
        // Get Picture (00 23 00 00)
        if (ins == 0x23) {
            System.out.println("   → GET PICTURE");
            String pictureBase64 = cardData.get("picture");
            if (pictureBase64 == null || pictureBase64.isEmpty()) {
                System.out.println("   → No picture stored");
                return new Object[]{new byte[]{(byte)0x6A, (byte)0x88}, isInitialized}; // No data
            }
            
            // Decode Base64 to bytes
            byte[] pictureData = java.util.Base64.getDecoder().decode(pictureBase64);
            byte[] response = new byte[pictureData.length + 2];
            System.arraycopy(pictureData, 0, response, 0, pictureData.length);
            response[pictureData.length] = (byte)0x90;
            response[pictureData.length + 1] = 0x00;
            System.out.println("   → Returning picture: " + pictureData.length + " bytes");
            return new Object[]{response, isInitialized};
        }
        
        // Unknown command - just return success (để client có thể tiếp tục)
        System.out.println("   → UNKNOWN COMMAND, returning success");
        return new Object[]{new byte[]{(byte)0x90, 0x00}, isInitialized};
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}

