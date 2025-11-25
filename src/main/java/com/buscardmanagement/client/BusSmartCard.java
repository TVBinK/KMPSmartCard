package com.buscardmanagement.client;

import com.buscardmanagement.client.util.HelpMethod;

import java.awt.image.BufferedImage;
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
    private String protocol; // Lưu protocol type: "T=0" hoặc "T=1"

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
     * Kết nối với smart card qua PC/SC reader
     * 
     * Theo tài liệu: Sử dụng TerminalFactory.getDefault() và terminal.connect("T=0")
     * 
     * @return true nếu kết nối thành công
     */
    public boolean connectCard() {
        // Nếu đã kết nối, kiểm tra lại protocol
        if (isConnected && card != null) {
            String currentProtocol = card.getProtocol();
            if ("T=1".equals(currentProtocol)) {
                HelpMethod.debugLog("The da ket noi voi T=1");
                return true;
            } else {
                // Protocol không đúng, disconnect và kết nối lại
                HelpMethod.debugLog("The dang ket noi voi protocol khac (" + currentProtocol + "), dang disconnect...");
                disconnect();
            }
        }

        // Kết nối với Java Card qua PC/SC reader - CHỈ T=1
        return connectToRealCard();
    }
    
    /**
     * Kết nối với Java Card qua PC/SC reader
     * 
     * CHỈ sử dụng T=1 protocol - không hỗ trợ T=0
     * 
     * @return true nếu kết nối thành công
     */
    private boolean connectToRealCard() {
        try {
            // Đảm bảo disconnect card hiện tại nếu có
            if (card != null) {
                try {
                    card.disconnect(false);
                    HelpMethod.debugLog(">>> Da disconnect card cu");
                } catch (Exception e) {
                    // Ignore
                }
                card = null;
                channel = null;
            }
            
            // Sử dụng PC/SC reader mặc định
            factory = TerminalFactory.getDefault();
            terminals = factory.terminals().list();
            
            if (terminals.isEmpty()) {
                HelpMethod.debugLog("Khong tim thay PC/SC terminals");
                return false;
            }

            // Tìm terminal có card
            for (CardTerminal term : terminals) {
                if (term.isCardPresent()) {
                    terminal = term;
                    HelpMethod.debugLog("Tim thay PC/SC Terminal: " + terminal.getName());
                    
                    // Đợi một chút để đảm bảo card sẵn sàng
                    try {
                        Thread.sleep(100); // Tăng thời gian đợi lên 100ms
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                    
                    // Kiểm tra card có sẵn sàng không
                    try {
                        if (!term.isCardPresent()) {
                            HelpMethod.debugLog(">>> Card khong con trong reader: " + terminal.getName());
                            continue;
                        }
                    } catch (CardException e) {
                        HelpMethod.debugLog(">>> Loi khi kiem tra card: " + e.getMessage());
                        continue;
                    }
                    
                    // Thử kết nối với "*" trước để kiểm tra card hỗ trợ protocol nào
                    String supportedProtocol = null;
                    try {
                        HelpMethod.debugLog(">>> Dang kiem tra protocol duoc ho tro...");
                        Card testCard = terminal.connect("*");
                        if (testCard != null) {
                            supportedProtocol = testCard.getProtocol();
                            HelpMethod.debugLog(">>> Card ho tro protocol: " + supportedProtocol);
                            testCard.disconnect(false);
                            
                            // Nếu card không hỗ trợ T=1, báo lỗi
                            if (supportedProtocol == null || !supportedProtocol.equals("T=1")) {
                                System.err.println("========================================");
                                System.err.println("LOI: Card khong ho tro T=1 protocol");
                                System.err.println("  - Terminal: " + terminal.getName());
                                System.err.println("  - Protocol duoc ho tro: " + (supportedProtocol != null ? supportedProtocol : "NULL"));
                                System.err.println("  - Yeu cau: Card PHẢI ho tro T=1 protocol");
                                System.err.println("");
                                System.err.println("GIAI PHAP CHO JAVACOS VIRTUAL READER:");
                                System.err.println("  1. Mo JAVACOS");
                                System.err.println("  2. Vao Card Profile hoac Card Configuration");
                                System.err.println("  3. Thay doi Protocol tu T=0 sang T=1");
                                System.err.println("  4. Hoac tao card profile moi voi T=1 protocol");
                                System.err.println("  5. Insert card lai vao Virtual Reader");
                                System.err.println("");
                                System.err.println("LY DO CAN T=1:");
                                System.err.println("  - T=1 ho tro Extended Length APDU (len den 32KB)");
                                System.err.println("  - Can thiet de luu anh khach hang (>255 bytes)");
                                System.err.println("  - T=0 chi ho tro toi da 255 bytes/APDU");
                                System.err.println("========================================");
                                continue; // Thử terminal khác
                            }
                        }
                    } catch (CardException e) {
                        HelpMethod.debugLog(">>> Loi khi kiem tra protocol: " + e.getMessage());
                        // Tiếp tục thử connect với T=1
                    }
                    
                    // CHỈ sử dụng T=1 protocol - KHÔNG fallback về T=0
                    try {
                        HelpMethod.debugLog(">>> Dang ket noi voi T=1 protocol...");
                        card = terminal.connect("T=1");
                        
                        if (card == null) {
                            HelpMethod.debugLog(">>> LOI: Card la null sau khi connect");
                            continue;
                        }
                        
                        protocol = "T=1";
                        HelpMethod.debugLog(">>> Ket noi thanh cong voi protocol: T=1");
                    } catch (CardException e) {
                        HelpMethod.debugLog(">>> LOI: Khong the ket noi voi T=1 protocol");
                        HelpMethod.debugLog(">>> Chi tiet loi: " + e.getMessage());
                        HelpMethod.debugLog(">>> Stack trace: " + java.util.Arrays.toString(e.getStackTrace()));
                        
                        System.err.println("========================================");
                        System.err.println("LOI KET NOI: Card reader hoac card khong ho tro T=1");
                        System.err.println("  - Terminal: " + terminal.getName());
                        System.err.println("  - Protocol duoc ho tro: " + (supportedProtocol != null ? supportedProtocol : "Chua xac dinh"));
                        System.err.println("  - Yeu cau: Card reader PHẢI ho tro T=1 protocol");
                        System.err.println("  - T=1 can thiet de ghi anh lon (>255 bytes)");
                        System.err.println("  - Chi tiet: " + e.getMessage());
                        System.err.println("");
                        if (terminal.getName().contains("JAVACOS")) {
                            System.err.println("GIAI PHAP CHO JAVACOS VIRTUAL READER:");
                            System.err.println("  1. Mo JAVACOS");
                            System.err.println("  2. Vao Card Profile hoac Card Configuration");
                            System.err.println("  3. Thay doi Protocol tu T=0 sang T=1");
                            System.err.println("  4. Hoac tao card profile moi voi T=1 protocol");
                            System.err.println("  5. Insert card lai vao Virtual Reader");
                            System.err.println("  6. Kiem tra card da duoc insert trong JAVACOS chua");
                        } else {
                            System.err.println("GIAI PHAP:");
                            System.err.println("  1. Kiem tra card da duoc insert vao reader chua");
                            System.err.println("  2. Kiem tra card reader co ho tro T=1");
                            System.err.println("  3. Kiem tra card co ho tro T=1 (co the card chi ho tro T=0)");
                            System.err.println("  4. Disconnect JCIDE simulator neu dang chay");
                            System.err.println("  5. Disconnect cac ung dung khac dang su dung card");
                        }
                        System.err.println("");
                        System.err.println("LY DO CAN T=1:");
                        System.err.println("  - T=1 ho tro Extended Length APDU (len den 32KB)");
                        System.err.println("  - Can thiet de luu anh khach hang (>255 bytes)");
                        System.err.println("  - T=0 chi ho tro toi da 255 bytes/APDU");
                        System.err.println("========================================");
                        continue; // Thử terminal khác
                    }
                    
                    // Kiểm tra protocol thực tế từ card
                    String actualProtocol = card.getProtocol();
                    HelpMethod.debugLog(">>> Protocol thuc te tu card: " + actualProtocol);
                    
                    // Đảm bảo protocol là T=1 - NẾU KHÔNG PHẢI T=1 THÌ DISCONNECT VÀ BÁO LỖI
                    if (actualProtocol == null || !actualProtocol.equals("T=1")) {
                        System.err.println("========================================");
                        System.err.println("LOI: Card tra ve protocol " + actualProtocol + " thay vi T=1");
                        System.err.println("  - He thong CHI ho tro T=1 protocol");
                        System.err.println("  - Dang disconnect card...");
                        System.err.println("========================================");
                        try {
                            card.disconnect(false);
                        } catch (Exception e) {
                            // Ignore
                        }
                        card = null;
                        channel = null;
                        continue; // Thử terminal khác
                    }
                    
                    protocol = "T=1";
                    HelpMethod.debugLog(">>> Protocol duoc su dung: " + protocol);
                    
                    channel = card.getBasicChannel();
                    
                    if (channel == null) {
                        HelpMethod.debugLog("Khong the lay basic channel");
                        try {
                            card.disconnect(false);
                        } catch (Exception e) {
                            // Ignore
                        }
                        card = null;
                        continue;
                    }

                    // Select applet bằng AID
                    response = channel.transmit(new CommandAPDU(0x00, (byte) 0xA4, 0x04, 0x00, AID_APPLET));
                    String statusWord = Integer.toHexString(response.getSW());
                    
                    HelpMethod.debugLog("Select applet response SW: " + statusWord);

                    if (statusWord.equals("9000")) {
                        isConnected = true;
                        HelpMethod.debugLog("Ket noi Java Card thanh cong voi T=1");
                        return true;
                    } else if (statusWord.equals("6400")) {
                        isConnected = true;
                        System.err.println("Canh bao: The da bi vo hieu hoa");
                        return true;
                    } else {
                        HelpMethod.debugLog("Select applet that bai voi SW: " + statusWord);
                        // Disconnect và thử terminal khác
                        try {
                            card.disconnect(false);
                        } catch (Exception e) {
                            // Ignore
                        }
                        card = null;
                        channel = null;
                    }
                }
            }
            
            HelpMethod.debugLog("Khong tim thay the trong PC/SC readers hoac khong the ket noi voi T=1");
            return false;
        } catch (CardException ex) {
            HelpMethod.debugLog("Loi ket noi den PC/SC: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        } catch (Exception ex) {
            HelpMethod.debugLog("Loi khong xac dinh: " + ex.getMessage());
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
            if (channel != null) {
                channel = null;
            }
            
            if (card != null) {
                try {
                    card.disconnect(false);
                    HelpMethod.debugLog(">>> Da disconnect card");
                } catch (CardException e) {
                    HelpMethod.debugLog(">>> Loi khi disconnect card: " + e.getMessage());
                }
                card = null;
            }
            
            if (terminal != null) {
                // Không disconnect terminal, chỉ disconnect card
                terminal = null;
            }
            
            isConnected = false;
            protocol = null;
            HelpMethod.debugLog("Ngat ket noi the hoan tat");
            return true;
        } catch (Exception e) {
            System.err.println("Loi ngat ket noi the: " + e.getMessage());
            e.printStackTrace();
            // Vẫn đặt lại các biến về null
            card = null;
            channel = null;
            terminal = null;
            isConnected = false;
            protocol = null;
            return false;
        }
    }

    /**
     * Kiểm tra card có đang trong reader không (không gửi APDU)
     */
    public boolean isCardPresent() {
        try {
            if (terminal != null) {
                return terminal.isCardPresent();
            }
        } catch (CardException e) {
            HelpMethod.debugLog("Loi kiem tra card present: " + e.getMessage());
        }
        return isConnected;
    }

    /**
     * Gửi APDU command đến card
     * 
     * @param command Byte array chứa APDU command
     * @return ResponseAPDU từ card
     */
    public ResponseAPDU sendCommandAPDU(byte[] command) {
        try {
            HelpMethod.debugLog("Gui APDU command. Kich thuoc: " + command.length + " bytes");
            HelpMethod.debugLog("Command: " + HelpMethod.bytesToHex(command));

            CommandAPDU commandAPDU = new CommandAPDU(command);
            response = channel.transmit(commandAPDU);

            String sw = Integer.toHexString(response.getSW());
            HelpMethod.debugLog("Response SW: " + sw);
            
            return response;
        } catch (CardException e) {
            System.err.println("Loi gui APDU: " + e.getMessage());
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
            System.err.println("Khong the lay thong tin khach hang, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }

    /**
     * Cập nhật thông tin khách hàng lên thẻ
     * 
     * @param hoTen Họ tên khách hàng
     * @param loaiDoiTuong Loại đối tượng (khách hàng)
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

            // Kiểm tra protocol và giới hạn data length cho T=0
            if ("T=0".equals(protocol) && dataBytes.length > 255) {
                System.err.println("Loi: Du lieu qua dai cho T=0 protocol (toi da 255 bytes, nhan duoc " + dataBytes.length + ")");
                return false;
            }

            byte[] command;
            if ("T=1".equals(protocol) && dataBytes.length > 255) {
                // Extended Length format cho T=1
                command = new byte[7 + dataBytes.length];
                command[0] = (byte) 0x00; // CLA
                command[1] = (byte) 0x20; // INS for UPDATE_INFO
                command[2] = (byte) 0x00; // P1
                command[3] = (byte) 0x00; // P2
                command[4] = (byte) 0x00; // Extended Length indicator
                command[5] = (byte) ((dataBytes.length >> 8) & 0xFF); // Lc high byte
                command[6] = (byte) (dataBytes.length & 0xFF); // Lc low byte
                System.arraycopy(dataBytes, 0, command, 7, dataBytes.length);
            } else {
                // Normal length format (1 byte Lc) cho T=0 hoặc data <= 255
                command = new byte[5 + dataBytes.length];
                command[0] = (byte) 0x00; // CLA
                command[1] = (byte) 0x20; // INS for UPDATE_INFO
                command[2] = (byte) 0x00; // P1
                command[3] = (byte) 0x00; // P2
                command[4] = (byte) dataBytes.length; // Lc (1 byte)
                System.arraycopy(dataBytes, 0, command, 5, dataBytes.length);
            }

            // Gửi command
            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
                HelpMethod.debugLog("Cap nhat thong tin khach hang thanh cong");
                return true;
            } else {
                System.err.println("Khong the cap nhat thong tin khach hang, SW: " + 
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }
        } catch (Exception e) {
            System.err.println("Loi cap nhat thong tin khach hang: " + e.getMessage());
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
            System.err.println("Khong the lay card ID, SW: " + 
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
                HelpMethod.debugLog("Cap nhat Card ID thanh cong: " + newCardId);
                return true;
            } else {
                System.err.println("Khong the cap nhat card ID, SW: " + 
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }
        } catch (Exception e) {
            System.err.println("Loi cap nhat card ID: " + e.getMessage());
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
            System.err.println("Khong the lay PIN, SW: " + 
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
                HelpMethod.debugLog("Cap nhat PIN thanh cong");
                return true;
            } else {
                System.err.println("Khong the cap nhat PIN, SW: " + 
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }
        } catch (Exception e) {
            System.err.println("Loi cap nhat PIN: " + e.getMessage());
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
                HelpMethod.debugLog("checkPin response - Do dai du lieu: " + 
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
                        HelpMethod.debugLog("Byte du lieu dau tien: " + String.format("%02X", firstByte & 0xFF));
                        
                        if (firstByte == (byte) 0x00) {
                            // PIN đúng
                            unknownIssue = false;
                            isCardBlocked = false;
                            counter = 0;
                            HelpMethod.debugLog("Xac thuc PIN thanh cong");
                            return true;
                        } else {
                            // PIN sai, byte đầu tiên chứa số lần sai
                            unknownIssue = false;
                            counter = (byte)(firstByte & 0xFF); // Đảm bảo là unsigned
                            if (counter >= 4) {
                                isCardBlocked = true;
                                System.err.println("The da bi khoa do nhap sai PIN qua nhieu lan");
                            } else {
                                System.err.println("PIN khong dung. So lan con lai: " + (4 - counter));
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
                                HelpMethod.debugLog("Status byte truoc SW: " + String.format("%02X", statusByte & 0xFF));
                                
                                if (statusByte == (byte) 0x00) {
                                    // PIN đúng
                                    unknownIssue = false;
                                    isCardBlocked = false;
                                    counter = 0;
                                    HelpMethod.debugLog("Xac thuc PIN thanh cong (status byte = 0x00)");
                                    return true;
                                } else {
                                    // PIN sai
                                    unknownIssue = false;
                                    counter = (byte)(statusByte & 0xFF);
                                    if (counter >= 4) {
                                        isCardBlocked = true;
                                        System.err.println("The da bi khoa do nhap sai PIN qua nhieu lan");
                                    } else {
                                        System.err.println("PIN khong dung. So lan con lai: " + (4 - counter));
                                    }
                                    return false;
                                }
                            }
                        }
                        
                        // Nếu chỉ có SW 9000 và không có data, không thể xác định chính xác PIN đúng hay sai
                        // Vì getCustomerInfo() không yêu cầu PIN đã được xác thực, nên không thể dùng để xác nhận
                        // Để an toàn, luôn coi như PIN sai nếu không có data xác nhận
                        // Card nên trả về data để phân biệt PIN đúng/sai (0x00 = đúng, >0x00 = số lần sai)
                        HelpMethod.debugLog("SW 9000 khong co du lieu - the nen tra ve du lieu de phan biet PIN dung/sai");
                        HelpMethod.debugLog("Xu ly nhu PIN sai de bao mat (khong co du lieu xac nhan)");
                        unknownIssue = false;
                        counter++;
                        if (counter >= 4) {
                            isCardBlocked = true;
                            System.err.println("The da bi khoa do nhap sai PIN qua nhieu lan");
                        } else {
                            System.err.println("PIN khong dung (khong co du lieu xac nhan trong response). So lan con lai: " + (4 - counter));
                        }
                        return false;
                    }
                } else if (sw == 0x6983) {
                    // Thẻ bị khóa
                    unknownIssue = false;
                    isCardBlocked = true;
                    System.err.println("The da bi khoa do nhap sai PIN qua nhieu lan");
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
                    HelpMethod.debugLog("Xac thuc PIN thanh cong (che do don gian)");
                    return true;
                } else if (responseBytes.length >= 1 && responseBytes[0] == (byte) 0x01 && sw == 0x9000) {
                    // PIN sai
                    HelpMethod.debugLog("PIN khong dung (che do don gian)");
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
            System.err.println("Khong the lay so du, SW: " + 
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
                HelpMethod.debugLog("Cap nhat so du thanh cong: " + balance);
                return true;
            } else {
                System.err.println("Khong the cap nhat so du, SW: " + 
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }
        } catch (Exception e) {
            System.err.println("Loi cap nhat so du: " + e.getMessage());
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
            HelpMethod.debugLog("Nhan duoc anh: " + imageData.length + " bytes");
            return HelpMethod.convertByteArrayToImage(imageData);
        } else {
            System.err.println("Khong the lay anh, SW: " + 
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
        byte[] pictureBytes = HelpMethod.convertImageToByteArray(image);
        if (pictureBytes == null) {
            System.err.println("Khong the chuyen doi image sang byte array");
            return false;
        }
        return updatePicture(pictureBytes);
    }

    /**
     * Cập nhật ảnh khách hàng lên thẻ (dữ liệu raw bytes)
     * 
     * @param pictureBytes Byte array dữ liệu ảnh (JPG/PNG tùy ý)
     * @return true nếu cập nhật thành công
     */
    public boolean updatePicture(byte[] pictureBytes) {
        try {
            if (pictureBytes == null || pictureBytes.length == 0) {
                System.err.println("Anh rong, khong co gi de ghi len the");
                return false;
            }

            HelpMethod.debugLog(">>> Dang su dung protocol: " + protocol);
            HelpMethod.debugLog(">>> Kich thuoc anh: " + pictureBytes.length + " bytes");
            
            // CHỈ cho phép T=1 protocol
            if (!"T=1".equals(protocol)) {
                System.err.println("========================================");
                System.err.println("LOI: He thong chi ho tro T=1 protocol");
                System.err.println("  - Protocol hien tai: " + protocol);
                System.err.println("  - Yeu cau: T=1 protocol");
                System.err.println("  - Kich thuoc anh: " + pictureBytes.length + " bytes");
                System.err.println("========================================");
                System.err.println("GIAI PHAP:");
                System.err.println("  1. Kiem tra card reader co ho tro T=1");
                System.err.println("  2. Kiem tra card co ho tro T=1");
                System.err.println("  3. Anh se duoc luu vao DATABASE thay vi the");
                System.err.println("========================================");
                return false;
            }

            byte[] command;
            // T=1 protocol: Luôn sử dụng Extended Length format nếu data > 255 bytes
            if (pictureBytes.length > 255) {
                // Extended Length format cho T=1 (lên đến 32KB)
                // Format: CLA INS P1 P2 00 Lc_High Lc_Low [Data]
                HelpMethod.debugLog(">>> Su dung Extended Length APDU (T=1, data > 255 bytes)");
                int lcHigh = (pictureBytes.length >> 8) & 0xFF;
                int lcLow = pictureBytes.length & 0xFF;
                HelpMethod.debugLog(">>> Lc High: " + lcHigh + ", Lc Low: " + lcLow + ", Total: " + pictureBytes.length);
                
                command = new byte[7 + pictureBytes.length];
                command[0] = (byte) 0x00; // CLA
                command[1] = (byte) 0x22; // INS for UPDATE_PICTURE
                command[2] = (byte) 0x00; // P1
                command[3] = (byte) 0x00; // P2
                command[4] = (byte) 0x00; // Extended length indicator
                command[5] = (byte) lcHigh; // Lc high byte
                command[6] = (byte) lcLow; // Lc low byte
                System.arraycopy(pictureBytes, 0, command, 7, pictureBytes.length);
            } else {
                // Standard format cho T=1 (data <= 255 bytes)
                HelpMethod.debugLog(">>> Su dung Standard APDU (T=1, data <= 255 bytes)");
                command = new byte[5 + pictureBytes.length];
                command[0] = (byte) 0x00; // CLA
                command[1] = (byte) 0x22; // INS for UPDATE_PICTURE
                command[2] = (byte) 0x00; // P1
                command[3] = (byte) 0x00; // P2
                command[4] = (byte) pictureBytes.length; // Lc (1 byte)
                System.arraycopy(pictureBytes, 0, command, 5, pictureBytes.length);
            }

            HelpMethod.debugLog("Cap nhat anh: " + pictureBytes.length + " bytes");
            HelpMethod.debugLog(">>> Command length: " + command.length + " bytes");
            HelpMethod.debugLog(">>> Command header: " + HelpMethod.bytesToHex(java.util.Arrays.copyOf(command, Math.min(10, command.length))));

            ResponseAPDU response = sendCommandAPDU(command);

            if (response == null || response.getSW() != 0x9000) {
                System.err.println("Khong the cap nhat anh, SW: " +
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }

            HelpMethod.debugLog("Cap nhat anh thanh cong");
            return true;
        } catch (Exception e) {
            System.err.println("Loi cap nhat anh: " + e.getMessage());
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
            HelpMethod.debugLog("Nhan duoc public key: " + data.length + " bytes");
            return data;
        } else {
            System.err.println("Khong the lay public key, SW: " + 
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

            HelpMethod.debugLog("Xac thuc the voi du lieu ngau nhien: " + randomData);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
                byte[] signedData = response.getData();
                boolean verified = HelpMethod.verifySignature(publicKey, dataToVerify, signedData);
                
                if (verified) {
                    HelpMethod.debugLog("Xac thuc the thanh cong");
                } else {
                    System.err.println("Xac thuc the that bai");
                }
                
                return verified;
            } else {
                System.err.println("Khong the lay chu ky, SW: " + 
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }
        } catch (Exception e) {
            System.err.println("Loi xac thuc the: " + e.getMessage());
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
            HelpMethod.debugLog("The da duoc khoi tao");
            return true;
        } else if (response != null && response.getSW() == 0x6A88) {
            HelpMethod.debugLog("The chua duoc khoi tao");
            return false;
        } else {
            System.err.println("Khong the kiem tra trang thai the");
            return false;
        }
    }

    /**
     * Xóa toàn bộ dữ liệu trên thẻ
     * 
     * @return true nếu xóa thành công
     */
    public boolean clearCard() {
        System.out.println("[Java] clearCard() duoc goi - Chuan bi CLEAR command...");
        byte[] command = {(byte) 0x00, (byte) 0x18, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        System.out.println("[Java] Gui CLEAR command: 00 18 00 00 00");
        
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null) {
            System.out.println("[Java] CLEAR Response SW: " + Integer.toHexString(response.getSW()));
        } else {
            System.err.println("[Java] CLEAR Response la NULL!");
        }
        
        if (response != null && response.getSW() == 0x9000) {
            HelpMethod.debugLog("Xoa the thanh cong");
            System.out.println("[Java] Xoa the thanh cong!");
            return true;
        } else {
            System.err.println("[Java] Khong the xoa the, SW: " + 
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
            HelpMethod.debugLog("Mo khoa the thanh cong");
            return true;
        } else {
            System.err.println("Khong the mo khoa the, SW: " + 
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
            HelpMethod.debugLog("Khoa the thanh cong");
            return true;
        } else {
            System.err.println("Khong the khoa the, SW: " + 
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
                HelpMethod.debugLog("Cap nhat thong tin quet the gan nhat: " + tapInfo);
                return true;
            } else {
                System.err.println("Khong the cap nhat thong tin quet the gan nhat, SW: " + 
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }
        } catch (Exception e) {
            System.err.println("Loi cap nhat thong tin quet the gan nhat: " + e.getMessage());
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
            System.err.println("Khong the lay thong tin quet the gan nhat, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }
}

