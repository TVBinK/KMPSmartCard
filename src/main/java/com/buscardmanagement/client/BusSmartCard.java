package com.buscardmanagement.client;

import com.buscardmanagement.client.provider.SocketCardProvider;
import com.buscardmanagement.client.provider.SocketProviderParameter;
import com.buscardmanagement.client.util.HelpMethod;

import java.awt.HeadlessException;
import java.awt.image.BufferedImage;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Arrays;
import java.util.List;
import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;

/**
 * BusSmartCard - Client class cho hệ thống quản lý thẻ xe bus
 * 
 * Singleton pattern để quản lý kết nối duy nhất với smart card
 * 
 * Chức năng chính:
 * - Kết nối/ngắt kết nối với smart card
 * - Lưu/đọc thông tin khách hàng
 * - Quản lý số dư
 * - Xác thực PIN
 * - Lưu/đọc ảnh khách hàng
 * - Xác thực thẻ bằng chữ ký số RSA
 * - Quản lý thông tin quẹt thẻ
 */
public class BusSmartCard {

    // AID của applet (phải trùng với AID trong applet)
    public static final byte[] AID_APPLET = {
        (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44, (byte) 0x55,
        (byte) 0x00, (byte) 0x01  // Thay đổi byte cuối để phân biệt với Student applet
    };

    // Card components
    private Card card;
    private TerminalFactory factory;
    private CardChannel channel;
    private CardTerminal terminal;
    private List<CardTerminal> terminals;
    private ResponseAPDU response;

    // Status flags
    public static boolean isCardBlocked = false;
    public static byte counter = 0;
    public static byte[] publicKey;
    public static boolean unknownIssue = false;
    public boolean isConnected = false;

    // Singleton instance
    private static BusSmartCard instance;

    /**
     * Private constructor (Singleton pattern)
     */
    private BusSmartCard() {
    }

    /**
     * Lấy instance duy nhất của BusSmartCard
     * 
     * @return BusSmartCard instance
     */
    public static BusSmartCard getInstance() {
        if (instance == null) {
            instance = new BusSmartCard();
        }
        return instance;
    }

    /**
     * Kết nối với smart card qua SocketCardProvider
     * 
     * @return true nếu kết nối thành công
     * @throws NoSuchAlgorithmException Nếu không tìm thấy algorithm
     */
    public boolean connectCard() throws NoSuchAlgorithmException {
        try {
            if (isConnected) {
                HelpMethod.debugLog("Card already connected");
                return true;
            }

            // Đăng ký SocketCardProvider nếu chưa có
            if (Security.getProvider("SocketCardSim") == null) {
                SocketCardProvider provider = new SocketCardProvider();
                Security.addProvider(provider);
                HelpMethod.debugLog("SocketCardProvider registered");
            }

            // Tạo factory với localhost:9025
            factory = TerminalFactory.getInstance("SocketCardSim", 
                new SocketProviderParameter("localhost", 9025));
            
            // Lấy danh sách terminals
            terminals = factory.terminals().list();
            if (terminals.isEmpty()) {
                HelpMethod.debugLog("No card terminals found");
                return false;
            }

            terminal = terminals.get(0);
            HelpMethod.debugLog("Terminal found: " + terminal.getName());

            // Kết nối với card (T=1 protocol)
            card = terminal.connect("T=1");
            channel = card.getBasicChannel();
            
            if (channel == null) {
                HelpMethod.debugLog("Failed to get basic channel");
                return false;
            }

            // Select applet bằng AID
            response = channel.transmit(new CommandAPDU(0x00, (byte) 0xA4, 0x04, 0x00, AID_APPLET));
            String statusWord = Integer.toHexString(response.getSW());
            
            HelpMethod.debugLog("Select applet response SW: " + statusWord);

            if (statusWord.equals("9000")) {
                isConnected = true;
                HelpMethod.debugLog("Card connected successfully");
                return true;
            } else if (statusWord.equals("6400")) {
                isConnected = true;
                System.err.println("Warning: Card is disabled");
                return true;
            } else {
                HelpMethod.debugLog("Failed to select applet, SW: " + statusWord);
                return false;
            }
        } catch (HeadlessException | CardException ex) {
            System.err.println("Error connecting to card: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }

    /**
     * Ngắt kết nối với smart card
     * 
     * @return true nếu ngắt kết nối thành công
     */
    public boolean disconnect() {
        try {
            if (card != null) {
                card.disconnect(false);
                isConnected = false;
                HelpMethod.debugLog("Card disconnected");
                return true;
            }
            return false;
        } catch (CardException e) {
            System.err.println("Error disconnecting card: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gửi APDU command đến card
     * 
     * @param command Byte array chứa APDU command
     * @return ResponseAPDU từ card
     */
    public ResponseAPDU sendCommandAPDU(byte[] command) {
        try {
            HelpMethod.debugLog("Sending APDU command. Size: " + command.length + " bytes");
            HelpMethod.debugLog("Command: " + HelpMethod.bytesToHex(command));

            CommandAPDU commandAPDU = new CommandAPDU(command);
            response = channel.transmit(commandAPDU);

            String sw = Integer.toHexString(response.getSW());
            HelpMethod.debugLog("Response SW: " + sw);
            
            return response;
        } catch (CardException e) {
            System.err.println("Error sending APDU: " + e.getMessage());
            return null;
        }
    }

    // ========== THÔNG TIN KHÁCH HÀNG ==========

    /**
     * Lấy thông tin khách hàng từ thẻ
     * 
     * Format: hoTen.loaiDoiTuong.ngayHetHan.loaiThe.linkedCustomerId
     * 
     * @return String array chứa thông tin khách hàng, hoặc null nếu lỗi
     */
    public String[] getCustomerInfo() {
        byte[] command = {(byte) 0x00, (byte) 0x13, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            byte[] data = response.getData();
            return HelpMethod.convertByteToStringArr(data, '.');
        } else {
            System.err.println("Failed to get customer info, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }

    /**
     * Cập nhật thông tin khách hàng lên thẻ
     * 
     * @param hoTen Họ tên khách hàng
     * @param loaiDoiTuong Loại đối tượng (HSSV/Người cao tuổi/Thông thường)
     * @param ngayHetHan Ngày hết hạn (dd/MM/yyyy)
     * @param loaiThe Loại thẻ (Vé Lượt/Vé Tháng)
     * @param linkedCustomerId Mã khách hàng liên kết (optional)
     * @return true nếu cập nhật thành công
     */
    public boolean updateCustomerInfo(String hoTen, String loaiDoiTuong, String ngayHetHan, 
                                      String loaiThe, String linkedCustomerId) {
        try {
            // Xây dựng chuỗi dữ liệu với delimiter '.'
            String dataBuilder = hoTen + "." +
                                loaiDoiTuong + "." +
                                ngayHetHan + "." +
                                loaiThe + "." +
                                (linkedCustomerId != null ? linkedCustomerId : "");

            // Chuyển sang byte array
            byte[] dataBytes = HelpMethod.ConvertStringToByteArr(dataBuilder);

            // Tạo APDU command (Extended Length format)
            byte[] command = new byte[7 + dataBytes.length];
            command[0] = (byte) 0x00; // CLA
            command[1] = (byte) 0x20; // INS for UPDATE_INFO
            command[2] = (byte) 0x00; // P1
            command[3] = (byte) 0x00; // P2
            command[4] = (byte) 0x00; // Extended Length indicator
            command[5] = (byte) ((dataBytes.length >> 8) & 0xFF); // Lc high byte
            command[6] = (byte) (dataBytes.length & 0xFF); // Lc low byte
            System.arraycopy(dataBytes, 0, command, 7, dataBytes.length);

            // Gửi command
            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
                HelpMethod.debugLog("Customer info updated successfully");
                return true;
            } else {
                System.err.println("Failed to update customer info, SW: " + 
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error updating customer info: " + e.getMessage());
            return false;
        }
    }

    // ========== CARD ID ==========

    /**
     * Lấy Card ID từ thẻ
     * 
     * @return String array chứa Card ID, hoặc null nếu lỗi
     */
    public String[] getCardId() {
        byte[] command = {(byte) 0x00, (byte) 0x27, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            byte[] data = response.getData();
            return HelpMethod.convertByteToStringArr(data, '.');
        } else {
            System.err.println("Failed to get card ID, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }

    /**
     * Cập nhật Card ID
     * 
     * @param newCardId Card ID mới
     * @return true nếu cập nhật thành công
     */
    public boolean updateCardId(String newCardId) {
        try {
            byte[] cardBytes = HelpMethod.ConvertStringToByteArr(newCardId);

            byte[] command = new byte[5 + cardBytes.length];
            command[0] = (byte) 0x00; // CLA
            command[1] = (byte) 0x26; // INS for UPDATE_CARDID
            command[2] = (byte) 0x00; // P1
            command[3] = (byte) 0x00; // P2
            command[4] = (byte) cardBytes.length; // Lc
            System.arraycopy(cardBytes, 0, command, 5, cardBytes.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
                HelpMethod.debugLog("Card ID updated successfully: " + newCardId);
                return true;
            } else {
                System.err.println("Failed to update card ID, SW: " + 
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error updating card ID: " + e.getMessage());
            return false;
        }
    }

    // ========== PIN ==========

    /**
     * Lấy PIN từ thẻ (chỉ dùng cho admin)
     * 
     * @return String array chứa PIN
     */
    public String[] getPin() {
        byte[] command = {(byte) 0x00, (byte) 0x12, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            byte[] data = response.getData();
            return HelpMethod.convertByteToStringArr(data, '.');
        } else {
            System.err.println("Failed to get PIN, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }

    /**
     * Cập nhật PIN
     * 
     * @param newPin PIN mới
     * @return true nếu cập nhật thành công
     */
    public boolean updatePin(String newPin) {
        try {
            byte[] pinBytes = HelpMethod.ConvertStringToByteArr(newPin);

            byte[] command = new byte[5 + pinBytes.length];
            command[0] = (byte) 0x00; // CLA
            command[1] = (byte) 0x21; // INS for UPDATE_PIN
            command[2] = (byte) 0x00; // P1
            command[3] = (byte) 0x00; // P2
            command[4] = (byte) pinBytes.length; // Lc
            System.arraycopy(pinBytes, 0, command, 5, pinBytes.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
                HelpMethod.debugLog("PIN updated successfully");
                return true;
            } else {
                System.err.println("Failed to update PIN, SW: " + 
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error updating PIN: " + e.getMessage());
            return false;
        }
    }

    /**
     * Kiểm tra PIN với counter (khóa thẻ sau 4 lần sai)
     * 
     * @param userPin PIN người dùng nhập vào
     * @return true nếu PIN đúng, false nếu sai
     */
    public boolean checkPin(String userPin) {
        try {
            byte[] pinBytes = HelpMethod.ConvertStringToByteArr(userPin);

            byte[] command = new byte[5 + pinBytes.length];
            command[0] = (byte) 0x00; // CLA
            command[1] = (byte) 0x19; // INS for CHECK_PIN
            command[2] = (byte) 0x00; // P1
            command[3] = (byte) 0x00; // P2
            command[4] = (byte) pinBytes.length; // Lc
            System.arraycopy(pinBytes, 0, command, 5, pinBytes.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null) {
                byte[] responseBytes = response.getBytes();
                byte[] responseData = response.getData();
                int sw = response.getSW();

                // Log chi tiết để debug
                HelpMethod.debugLog("checkPin response - Data length: " + 
                    (responseData != null ? responseData.length : 0) + 
                    ", Total bytes: " + responseBytes.length + 
                    ", SW: " + Integer.toHexString(sw));
                
                // Log toàn bộ response bytes để debug
                if (responseBytes != null && responseBytes.length > 0) {
                    StringBuilder sb = new StringBuilder("Response bytes: ");
                    for (int i = 0; i < responseBytes.length; i++) {
                        sb.append(String.format("%02X ", responseBytes[i] & 0xFF));
                    }
                    HelpMethod.debugLog(sb.toString());
                }

                if (sw == 0x9000) {
                    // SW: 9000 là thành công
                    if (responseData != null && responseData.length > 0) {
                        // Có data trong response, kiểm tra byte đầu tiên
                        byte firstByte = responseData[0];
                        HelpMethod.debugLog("First data byte: " + String.format("%02X", firstByte & 0xFF));
                        
                        if (firstByte == (byte) 0x00) {
                            // PIN đúng
                            unknownIssue = false;
                            isCardBlocked = false;
                            counter = 0;
                            HelpMethod.debugLog("PIN verified successfully");
                            return true;
                        } else {
                            // PIN sai, byte đầu tiên chứa số lần sai
                            unknownIssue = false;
                            counter = (byte)(firstByte & 0xFF); // Đảm bảo là unsigned
                            if (counter >= 4) {
                                isCardBlocked = true;
                                System.err.println("Card is blocked due to too many incorrect PIN attempts");
                            } else {
                                System.err.println("Incorrect PIN. Attempts remaining: " + (4 - counter));
                            }
                            return false;
                        }
                    } else {
                        // Response không có data nhưng SW: 9000
                        // Có thể card trả về SW 9000 cho cả PIN đúng và sai
                        // Cần kiểm tra responseBytes để xem có byte nào khác không
                        // Nếu responseBytes chỉ có 2 bytes (SW), có thể là PIN đúng
                        // Nếu có thêm bytes, có thể là thông tin về số lần sai
                        if (responseBytes != null && responseBytes.length > 2) {
                            // Có thêm bytes ngoài SW, có thể là thông tin về PIN sai
                            // Byte cuối cùng là SW (90 00), byte trước đó có thể là số lần sai
                            int dataIndex = responseBytes.length - 3; // Trước SW
                            if (dataIndex >= 0) {
                                byte statusByte = responseBytes[dataIndex];
                                HelpMethod.debugLog("Status byte before SW: " + String.format("%02X", statusByte & 0xFF));
                                
                                if (statusByte == (byte) 0x00) {
                                    // PIN đúng
                                    unknownIssue = false;
                                    isCardBlocked = false;
                                    counter = 0;
                                    HelpMethod.debugLog("PIN verified successfully (status byte = 0x00)");
                                    return true;
                                } else {
                                    // PIN sai
                                    unknownIssue = false;
                                    counter = (byte)(statusByte & 0xFF);
                                    if (counter >= 4) {
                                        isCardBlocked = true;
                                        System.err.println("Card is blocked due to too many incorrect PIN attempts");
                                    } else {
                                        System.err.println("Incorrect PIN. Attempts remaining: " + (4 - counter));
                                    }
                                    return false;
                                }
                            }
                        }
                        
                        // Nếu chỉ có SW 9000 và không có data, không thể xác định chính xác PIN đúng hay sai
                        // Vì getCustomerInfo() không yêu cầu PIN đã được xác thực, nên không thể dùng để xác nhận
                        // Để an toàn, luôn coi như PIN sai nếu không có data xác nhận
                        // Card nên trả về data để phân biệt PIN đúng/sai (0x00 = đúng, >0x00 = số lần sai)
                        HelpMethod.debugLog("SW 9000 with no data - card should return data to distinguish correct/incorrect PIN");
                        HelpMethod.debugLog("Treating as incorrect PIN for security (no confirmation data)");
                        unknownIssue = false;
                        counter++;
                        if (counter >= 4) {
                            isCardBlocked = true;
                            System.err.println("Card is blocked due to too many incorrect PIN attempts");
                        } else {
                            System.err.println("Incorrect PIN (no confirmation data in response). Attempts remaining: " + (4 - counter));
                        }
                        return false;
                    }
                } else if (sw == 0x6983) {
                    // Thẻ bị khóa
                    unknownIssue = false;
                    isCardBlocked = true;
                    System.err.println("Card is blocked due to too many incorrect PIN attempts");
                    return false;
                } else {
                    unknownIssue = true;
                    System.err.println("Unexpected response. SW: " + Integer.toHexString(sw));
                    return false;
                }
            } else {
                System.err.println("No response from card");
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error verifying PIN: " + e.getMessage());
            return false;
        }
    }

    /**
     * Xác thực PIN đơn giản (không có counter, không khóa thẻ)
     * 
     * @param userPin PIN người dùng nhập vào
     * @return true nếu PIN đúng, false nếu sai
     */
    public boolean verifyPin(String userPin) {
        try {
            byte[] pinBytes = HelpMethod.ConvertStringToByteArr(userPin);

            byte[] command = new byte[5 + pinBytes.length];
            command[0] = (byte) 0x00; // CLA
            command[1] = (byte) 0x30; // INS for VERIFY_PIN
            command[2] = (byte) 0x00; // P1
            command[3] = (byte) 0x00; // P2
            command[4] = (byte) pinBytes.length; // Lc
            System.arraycopy(pinBytes, 0, command, 5, pinBytes.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null) {
                byte[] responseBytes = response.getBytes();
                int sw = response.getSW();

                if (responseBytes.length >= 1 && responseBytes[0] == (byte) 0x00 && sw == 0x9000) {
                    // PIN đúng
                    HelpMethod.debugLog("PIN verified successfully (simple mode)");
                    return true;
                } else if (responseBytes.length >= 1 && responseBytes[0] == (byte) 0x01 && sw == 0x9000) {
                    // PIN sai
                    HelpMethod.debugLog("Incorrect PIN (simple mode)");
                    return false;
                } else {
                    System.err.println("Unexpected response. SW: " + Integer.toHexString(sw));
                    return false;
                }
            } else {
                System.err.println("No response from card");
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error verifying PIN: " + e.getMessage());
            return false;
        }
    }

    // ========== SỐ DƯ ==========

    /**
     * Lấy số dư từ thẻ
     * 
     * @return String array chứa số dư
     */
    public String[] getBalance() {
        byte[] command = {(byte) 0x00, (byte) 0x14, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            byte[] data = response.getData();
            return HelpMethod.convertByteToStringArr(data, '.');
        } else {
            System.err.println("Failed to get balance, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }

    /**
     * Cập nhật số dư
     * 
     * @param balance Số dư mới (dạng String)
     * @return true nếu cập nhật thành công
     */
    public boolean updateBalance(String balance) {
        try {
            byte[] balanceBytes = HelpMethod.ConvertStringToByteArr(balance);

            byte[] command = new byte[5 + balanceBytes.length];
            command[0] = (byte) 0x00; // CLA
            command[1] = (byte) 0x16; // INS for UPDATE_BALANCE
            command[2] = (byte) 0x00; // P1
            command[3] = (byte) 0x00; // P2
            command[4] = (byte) balanceBytes.length; // Lc
            System.arraycopy(balanceBytes, 0, command, 5, balanceBytes.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
                HelpMethod.debugLog("Balance updated successfully: " + balance);
                return true;
            } else {
                System.err.println("Failed to update balance, SW: " + 
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error updating balance: " + e.getMessage());
            return false;
        }
    }

    // ========== ẢNH ==========

    /**
     * Lấy ảnh khách hàng từ thẻ
     * 
     * @return BufferedImage hoặc null nếu lỗi
     */
    public BufferedImage getPicture() {
        byte[] command = {
            (byte) 0x00, (byte) 0x23, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00
        };

        ResponseAPDU response = sendCommandAPDU(command);

        if (response != null && response.getSW() == 0x9000) {
            byte[] imageData = response.getData();
            HelpMethod.debugLog("Received picture: " + imageData.length + " bytes");
            return HelpMethod.convertByteArrayToImage(imageData);
        } else {
            System.err.println("Failed to get picture, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }

    /**
     * Cập nhật ảnh khách hàng lên thẻ
     * 
     * @param image BufferedImage cần lưu
     * @return true nếu cập nhật thành công
     */
    public boolean updatePicture(BufferedImage image) {
        try {
            byte[] pictureBytes = HelpMethod.convertImageToByteArray(image);
            if (pictureBytes == null) {
                System.err.println("Failed to convert image to byte array");
                return false;
            }

            // Extended Length format
            byte[] command = new byte[7 + pictureBytes.length];
            command[0] = (byte) 0x00; // CLA
            command[1] = (byte) 0x22; // INS for UPDATE_PICTURE
            command[2] = (byte) 0x00; // P1
            command[3] = (byte) 0x00; // P2
            command[4] = (byte) 0x00; // Extended length indicator
            command[5] = (byte) (pictureBytes.length >> 8); // Lc high byte
            command[6] = (byte) (pictureBytes.length & 0xFF); // Lc low byte
            System.arraycopy(pictureBytes, 0, command, 7, pictureBytes.length);

            HelpMethod.debugLog("Updating picture: " + pictureBytes.length + " bytes");

            ResponseAPDU response = sendCommandAPDU(command);

            if (response == null || response.getSW() != 0x9000) {
                System.err.println("Failed to update picture, SW: " +
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }

            HelpMethod.debugLog("Picture updated successfully");
            return true;
        } catch (Exception e) {
            System.err.println("Error updating picture: " + e.getMessage());
            return false;
        }
    }

    // ========== BẢO MẬT ==========

    /**
     * Lấy RSA Public Key từ thẻ
     * 
     * @return Byte array chứa public key (format: [Modulus][Exponent][ModLen][ExpLen])
     */
    public byte[] getPublicKey() {
        byte[] command = {(byte) 0x00, (byte) 0x24, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);

        if (response != null && response.getSW() == 0x9000) {
            byte[] data = response.getData();
            HelpMethod.debugLog("Public key received: " + data.length + " bytes");
            return data;
        } else {
            System.err.println("Failed to get public key, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }

    /**
     * Xác thực thẻ bằng chữ ký số RSA
     * 
     * @param publicKey Public key từ thẻ
     * @return true nếu thẻ hợp lệ
     */
    public boolean verifyCard(byte[] publicKey) {
        try {
            // Tạo dữ liệu ngẫu nhiên để ký
            String randomData = HelpMethod.generateRandomString(20);
            byte[] dataToVerify = HelpMethod.ConvertStringToByteArr(randomData);

            // Gửi lệnh ký dữ liệu
            byte[] command = new byte[5 + dataToVerify.length];
            command[0] = (byte) 0x00; // CLA
            command[1] = (byte) 0x25; // INS for GET_SIGN
            command[2] = (byte) 0x00; // P1
            command[3] = (byte) 0x00; // P2
            command[4] = (byte) dataToVerify.length; // Lc
            System.arraycopy(dataToVerify, 0, command, 5, dataToVerify.length);

            HelpMethod.debugLog("Verifying card with random data: " + randomData);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
                byte[] signedData = response.getData();
                boolean verified = HelpMethod.verifySignature(publicKey, dataToVerify, signedData);
                
                if (verified) {
                    HelpMethod.debugLog("Card verified successfully");
                } else {
                    System.err.println("Card verification failed");
                }
                
                return verified;
            } else {
                System.err.println("Failed to get signature, SW: " + 
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error verifying card: " + e.getMessage());
            return false;
        }
    }

    // ========== QUẢN LÝ THẺ ==========

    /**
     * Kiểm tra thẻ đã được khởi tạo chưa
     * 
     * @return true nếu thẻ đã khởi tạo
     */
    public boolean checkCardCreated() {
        byte[] command = {(byte) 0x00, (byte) 0x29, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            HelpMethod.debugLog("Card has been initialized");
            return true;
        } else if (response != null && response.getSW() == 0x6A88) {
            HelpMethod.debugLog("Card has not been initialized");
            return false;
        } else {
            System.err.println("Failed to check card status");
            return false;
        }
    }

    /**
     * Xóa toàn bộ dữ liệu trên thẻ
     * 
     * @return true nếu xóa thành công
     */
    public boolean clearCard() {
        System.out.println("🗑️ [Java] clearCard() called - Preparing CLEAR command...");
        byte[] command = {(byte) 0x00, (byte) 0x18, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        System.out.println("🗑️ [Java] Sending CLEAR command: 00 18 00 00 00");
        
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null) {
            System.out.println("🗑️ [Java] CLEAR Response SW: " + Integer.toHexString(response.getSW()));
        } else {
            System.err.println("🗑️ [Java] CLEAR Response is NULL!");
        }
        
        if (response != null && response.getSW() == 0x9000) {
            HelpMethod.debugLog("Card cleared successfully");
            System.out.println("✅ [Java] Card cleared successfully!");
            return true;
        } else {
            System.err.println("❌ [Java] Failed to clear card, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return false;
        }
    }

    /**
     * Mở khóa thẻ
     * 
     * @return true nếu mở khóa thành công
     */
    public boolean unlockCard() {
        byte[] command = {(byte) 0x00, (byte) 0x11, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            isCardBlocked = false;
            counter = 0;
            HelpMethod.debugLog("Card unlocked successfully");
            return true;
        } else {
            System.err.println("Failed to unlock card, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return false;
        }
    }

    /**
     * Khóa thẻ
     * 
     * @return true nếu khóa thành công
     */
    public boolean lockCard() {
        byte[] command = {(byte) 0x00, (byte) 0x28, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            isCardBlocked = true;
            HelpMethod.debugLog("Card locked successfully");
            return true;
        } else {
            System.err.println("Failed to lock card, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return false;
        }
    }

    // ========== QUẸT THẺ ==========

    /**
     * Cập nhật thông tin quẹt thẻ gần nhất
     * 
     * @param routeId ID tuyến xe
     * @param tapType Loại quẹt (TAP_ON/TAP_OFF)
     * @param timestamp Thời gian quẹt
     * @return true nếu cập nhật thành công
     */
    public boolean updateLastTapInfo(String routeId, String tapType, String timestamp) {
        try {
            String tapInfo = routeId + "." + tapType + "." + timestamp;
            byte[] tapInfoBytes = HelpMethod.ConvertStringToByteArr(tapInfo);

            byte[] command = new byte[5 + tapInfoBytes.length];
            command[0] = (byte) 0x00; // CLA
            command[1] = (byte) 0x31; // INS for UPDATE_LAST_TAP
            command[2] = (byte) 0x00; // P1
            command[3] = (byte) 0x00; // P2
            command[4] = (byte) tapInfoBytes.length; // Lc
            System.arraycopy(tapInfoBytes, 0, command, 5, tapInfoBytes.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
                HelpMethod.debugLog("Last tap info updated: " + tapInfo);
                return true;
            } else {
                System.err.println("Failed to update last tap info, SW: " + 
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }
        } catch (Exception e) {
            System.err.println("Error updating last tap info: " + e.getMessage());
            return false;
        }
    }

    /**
     * Lấy thông tin quẹt thẻ gần nhất
     * 
     * @return String array chứa thông tin quẹt thẻ (routeId.tapType.timestamp)
     */
    public String[] getLastTapInfo() {
        byte[] command = {(byte) 0x00, (byte) 0x32, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            byte[] data = response.getData();
            return HelpMethod.convertByteToStringArr(data, '.');
        } else {
            System.err.println("Failed to get last tap info, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }
}

