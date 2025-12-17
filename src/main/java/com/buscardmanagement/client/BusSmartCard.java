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
 */
public class BusSmartCard {

    public static final byte[] APPLET_AID = { 
        (byte)0x11, (byte)0x22, (byte)0x33, (byte)0x44, (byte)0x55, (byte)0x00 
    };

    private Card card;
    private TerminalFactory factory;
    private CardChannel channel;
    private CardTerminal terminal;
    private List<CardTerminal> terminals;
    private ResponseAPDU response;
    private String protocol;

    public static boolean isCardBlocked = false;
    public static byte counter = 0;
    public static boolean unknownIssue = false;
    public boolean isConnected = false;

    private static BusSmartCard instance;

    private BusSmartCard() {
    }

    public static BusSmartCard getInstance() {
        if (instance == null) {
            instance = new BusSmartCard();
        }
        return instance;
    }

    public boolean connectCard() {
        if (isConnected && card != null) {
            String currentProtocol = card.getProtocol();
            if ("T=1".equals(currentProtocol)) {
                return true;
            } else {
                disconnect();
            }
        }
        return connectToRealCard();
    }

    private boolean connectToRealCard() {
        try {
            // Dọn dẹp kết nối cũ
            if (card != null) {
                try {
                    card.disconnect(false);
                } catch (Exception e) {
                }
                card = null;
                channel = null;
            }

            // Lấy terminal đầu tiên
            factory = TerminalFactory.getDefault();
            terminals = factory.terminals().list();

            if (terminals.isEmpty()) {
                return false;
            }

            terminal = terminals.get(0);

            // Kết nối với protocol T=1
            card = terminal.connect("T=1");
            protocol = "T=1";

            if (card == null) {
                return false;
            }

            // Lấy channel để gửi APDU
            channel = card.getBasicChannel();
            if (channel == null) {
                return false;
            }

            // SELECT applet bằng AID
            response = channel.transmit(new CommandAPDU(0x00, (byte) 0xA4, 0x04, 0x00, APPLET_AID));
            String statusWord = Integer.toHexString(response.getSW());

            if (statusWord.equals("9000")) {
                isConnected = true;
                return true;
            } else if (statusWord.equals("6400")) {
                isConnected = true;
                System.err.println("Canh bao: The da bi vo hieu hoa");
                return true;
            } else {
                return false;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    public boolean disconnect() {
        try {
            if (channel != null) {
                channel = null;
            }
            
            if (card != null) {
                try {
                    card.disconnect(false);
                } catch (CardException e) {
                }
                card = null;
            }
            
            if (terminal != null) {
                terminal = null;
            }
            
            isConnected = false;
            protocol = null;
            return true;
        } catch (Exception e) {
            System.err.println("Loi ngat ket noi the: " + e.getMessage());
            card = null;
            channel = null;
            terminal = null;
            isConnected = false;
            protocol = null;
            return false;
        }
    }

    public boolean isCardPresent() {
        try {
            if (terminal != null) {
                return terminal.isCardPresent();
            }
        } catch (CardException e) {
        }
        return isConnected;
    }

    public ResponseAPDU sendCommandAPDU(byte[] command) {
        try {
            CommandAPDU commandAPDU = new CommandAPDU(command);
            response = channel.transmit(commandAPDU);
            return response;
        } catch (CardException e) {
            System.err.println("Loi gui APDU: " + e.getMessage());
            return null;
        }
    }

    // ========== THÔNG TIN KHÁCH HÀNG ==========

    public String[] getCustomerInfo() {
        byte[] command = {(byte) 0x00, (byte) 0x13, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            byte[] data = response.getData();
            return HelpMethod.convertByteToStringArr(data, '.');
        } else {
            if (response != null && response.getSW() == 0x6400) {
                isCardBlocked = true;
            }
            System.err.println("Khong the lay thong tin khach hang, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }

    public boolean updateCustomerInfo(String fullData) {
        try {
            byte[] dataBytes = HelpMethod.ConvertStringToByteArr(fullData);

            if ("T=0".equals(protocol) && dataBytes.length > 255) {
                System.err.println("Loi: Du lieu qua dai cho T=0 protocol");
                return false;
            }

            byte[] command;
            if ("T=1".equals(protocol) && dataBytes.length > 255) {
                command = new byte[7 + dataBytes.length];
                command[0] = (byte) 0x00;
                command[1] = (byte) 0x20;
                command[2] = (byte) 0x00;
                command[3] = (byte) 0x00;
                command[4] = (byte) 0x00;
                command[5] = (byte) ((dataBytes.length >> 8) & 0xFF);
                command[6] = (byte) (dataBytes.length & 0xFF);
                System.arraycopy(dataBytes, 0, command, 7, dataBytes.length);
            } else {
                command = new byte[5 + dataBytes.length];
                command[0] = (byte) 0x00;
                command[1] = (byte) 0x20;
                command[2] = (byte) 0x00;
                command[3] = (byte) 0x00;
                command[4] = (byte) dataBytes.length;
                System.arraycopy(dataBytes, 0, command, 5, dataBytes.length);
            }

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
                return true;
            } else {
                int sw = response != null ? response.getSW() : 0;
                if (sw == 0x6983) {
                    System.err.println("Khong the cap nhat thong tin khach hang, SW: 6983 (Security status not satisfied). " +
                        "Vui long xac thuc PIN truoc khi cap nhat thong tin khach hang.");
                } else {
                    System.err.println("Khong the cap nhat thong tin khach hang, SW: " + 
                        (response != null ? Integer.toHexString(response.getSW()) : "null"));
                }
                return false;
            }
        } catch (Exception e) {
            System.err.println("Loi cap nhat thong tin khach hang: " + e.getMessage());
            return false;
        }
    }

    public boolean updateCustomerInfo(String hoTen, String loaiDoiTuong, String ngayHetHan, 
                                      String loaiThe, String linkedCustomerId, 
                                      String cccd, String dob, String address, String phone) {
        return updateCustomerInfo(hoTen, loaiDoiTuong, ngayHetHan, loaiThe, linkedCustomerId, 
                                 cccd, dob, address, phone, null);
    }

    public boolean updateCustomerInfo(String hoTen, String loaiDoiTuong, String ngayHetHan, 
                                      String loaiThe, String linkedCustomerId, 
                                      String cccd, String dob, String address, String phone, String pin) {
        try {
            // Nếu có PIN, verify PIN trước khi cập nhật
            if (pin != null && !pin.isEmpty()) {
                if (!checkPin(pin)) {
                    System.err.println("PIN khong dung, khong the cap nhat thong tin khach hang");
                    return false;
                }
            }

            String dataBuilder = hoTen + "." +
                                loaiDoiTuong + "." +
                                ngayHetHan + "." +
                                loaiThe + "." +
                                (linkedCustomerId != null ? linkedCustomerId : "") + "." +
                                (cccd != null ? cccd : "") + "." +
                                (dob != null ? dob : "") + "." +
                                (address != null ? address : "") + "." +
                                (phone != null ? phone : "");

            byte[] dataBytes = HelpMethod.ConvertStringToByteArr(dataBuilder);

            if ("T=0".equals(protocol) && dataBytes.length > 255) {
                System.err.println("Loi: Du lieu qua dai cho T=0 protocol");
                return false;
            }

            byte[] command;
            if ("T=1".equals(protocol) && dataBytes.length > 255) {
                command = new byte[7 + dataBytes.length];
                command[0] = (byte) 0x00;
                command[1] = (byte) 0x20;
                command[2] = (byte) 0x00;
                command[3] = (byte) 0x00;
                command[4] = (byte) 0x00;
                command[5] = (byte) ((dataBytes.length >> 8) & 0xFF);
                command[6] = (byte) (dataBytes.length & 0xFF);
                System.arraycopy(dataBytes, 0, command, 7, dataBytes.length);
            } else {
                command = new byte[5 + dataBytes.length];
                command[0] = (byte) 0x00;
                command[1] = (byte) 0x20;
                command[2] = (byte) 0x00;
                command[3] = (byte) 0x00;
                command[4] = (byte) dataBytes.length;
                System.arraycopy(dataBytes, 0, command, 5, dataBytes.length);
            }

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
                return true;
            } else {
                int sw = response != null ? response.getSW() : 0;
                if (sw == 0x6983) {
                    System.err.println("Khong the cap nhat thong tin khach hang, SW: 6983 (Security status not satisfied). " +
                        "Vui long xac thuc PIN truoc khi cap nhat thong tin khach hang.");
                } else {
                    System.err.println("Khong the cap nhat thong tin khach hang, SW: " + 
                        (response != null ? Integer.toHexString(response.getSW()) : "null"));
                }
                return false;
            }
        } catch (Exception e) {
            System.err.println("Loi cap nhat thong tin khach hang: " + e.getMessage());
            return false;
        }
    }

    // ========== CARD ID ==========

    public String[] getCardId() {
        byte[] command = {(byte) 0x00, (byte) 0x27, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            byte[] data = response.getData();
            return HelpMethod.convertByteToStringArr(data, '.');
        } else {
            if (response != null && response.getSW() == 0x6400) {
                isCardBlocked = true;
            }
            System.err.println("Khong the lay card ID, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }

    public boolean updateCardId(String newCardId) {
        try {
            byte[] cardBytes = HelpMethod.ConvertStringToByteArr(newCardId);

            byte[] command = new byte[5 + cardBytes.length];
            command[0] = (byte) 0x00;
            command[1] = (byte) 0x26;
            command[2] = (byte) 0x00;
            command[3] = (byte) 0x00;
            command[4] = (byte) cardBytes.length;
            System.arraycopy(cardBytes, 0, command, 5, cardBytes.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
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

    public boolean updatePin(String oldPin, String newPin) {
        try {
            byte[] oldPinBytes = HelpMethod.ConvertStringToByteArr(oldPin);
            byte[] newPinBytes = HelpMethod.ConvertStringToByteArr(newPin);

            // Format: oldPinLength (1 byte) + oldPin + newPin
            byte[] data = new byte[1 + oldPinBytes.length + newPinBytes.length];
            data[0] = (byte) oldPinBytes.length;
            System.arraycopy(oldPinBytes, 0, data, 1, oldPinBytes.length);
            System.arraycopy(newPinBytes, 0, data, 1 + oldPinBytes.length, newPinBytes.length);

            byte[] command = new byte[5 + data.length];
            command[0] = (byte) 0x00;
            command[1] = (byte) 0x21;
            command[2] = (byte) 0x00;
            command[3] = (byte) 0x00;
            command[4] = (byte) data.length;
            System.arraycopy(data, 0, command, 5, data.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null) {
                int sw = response.getSW();
                if (sw == 0x9000) {
                    // Kiểm tra response data để xem có bị khóa không
                    byte[] responseData = response.getData();
                    // Khi tạo PIN lần đầu (oldPin rỗng), response có thể không có data
                    if (oldPin.isEmpty()) {
                        // Tạo PIN lần đầu thành công
                        isCardBlocked = false;
                        counter = 0;
                        return true;
                    }
                    // Khi đổi PIN (oldPin không rỗng), kiểm tra response data
                    if (responseData != null && responseData.length > 0) {
                        byte firstByte = responseData[0];
                        if (firstByte == (byte) 0x00) {
                            // PIN cũ đúng, đổi PIN thành công
                            isCardBlocked = false;
                            counter = 0;
                            return true;
                        } else {
                            // PIN cũ sai
                            counter = (byte)(firstByte & 0xFF);
                            if (counter >= 4) {
                                isCardBlocked = true;
                                System.err.println("The da bi khoa do nhap sai PIN cu qua nhieu lan");
                            } else {
                                System.err.println("PIN cu khong dung. So lan con lai: " + (4 - counter));
                            }
                            return false;
                        }
                    }
                    // Nếu không có response data nhưng SW = 9000, coi như thành công
                    return true;
                } else if (sw == 0x6983) {
                    // Thẻ bị khóa
                    isCardBlocked = true;
                    System.err.println("The da bi khoa do nhap sai PIN cu qua nhieu lan");
                    return false;
                } else if (sw == 0x6A88) {
                    // Data object not found - Thẻ chưa được khởi tạo hoặc cần clear trước
                    System.err.println("Khong the cap nhat PIN, SW: 6A88 (Data object not found). " +
                        "Vui long xoa du lieu the (clear card) truoc khi tao PIN lan dau.");
                    return false;
                } else {
                    System.err.println("Khong the cap nhat PIN, SW: " + Integer.toHexString(sw));
                    return false;
                }
            } else {
                System.err.println("Khong the cap nhat PIN, khong co response");
                return false;
            }
        } catch (Exception e) {
            System.err.println("Loi cap nhat PIN: " + e.getMessage());
            return false;
        }
    }

    public boolean checkPin(String userPin) {
        try {
            byte[] pinBytes = HelpMethod.ConvertStringToByteArr(userPin);

            byte[] command = new byte[5 + pinBytes.length];
            command[0] = (byte) 0x00;
            command[1] = (byte) 0x19;
            command[2] = (byte) 0x00;
            command[3] = (byte) 0x00;
            command[4] = (byte) pinBytes.length;
            System.arraycopy(pinBytes, 0, command, 5, pinBytes.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null) {
                byte[] responseData = response.getData();
                int sw = response.getSW();

                if (sw == 0x9000) {
                    if (responseData != null && responseData.length > 0) {
                        byte firstByte = responseData[0];
                        
                        if (firstByte == (byte) 0x00) {
                            unknownIssue = false;
                            isCardBlocked = false;
                            counter = 0;
                            return true;
                        } else {
                            unknownIssue = false;
                            counter = (byte)(firstByte & 0xFF);
                            if (counter >= 4) {
                                isCardBlocked = true;
                                System.err.println("The da bi khoa do nhap sai PIN qua nhieu lan");
                            } else {
                                System.err.println("PIN khong dung. So lan con lai: " + (4 - counter));
                            }
                            return false;
                        }
                    }
                } else if (sw == 0x6983) {
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
        return false;
    }

    // ========== SỐ DƯ ==========

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

    public boolean updateBalance(String balance) {
        return updateBalance(balance, null);
    }

    public boolean updateBalance(String balance, String pin) {
        try {
            // Nếu có PIN, verify PIN trước khi cập nhật
            if (pin != null && !pin.isEmpty()) {
                if (!checkPin(pin)) {
                    System.err.println("PIN khong dung, khong the cap nhat so du");
                    return false;
                }
            }

            byte[] balanceBytes = HelpMethod.ConvertStringToByteArr(balance);

            byte[] command = new byte[5 + balanceBytes.length];
            command[0] = (byte) 0x00;
            command[1] = (byte) 0x16;
            command[2] = (byte) 0x00;
            command[3] = (byte) 0x00;
            command[4] = (byte) balanceBytes.length;
            System.arraycopy(balanceBytes, 0, command, 5, balanceBytes.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
                return true;
            } else {
                int sw = response != null ? response.getSW() : 0;
                if (sw == 0x6983) {
                    System.err.println("Khong the cap nhat so du, SW: 6983 (Security status not satisfied). " +
                        "Vui long xac thuc PIN truoc khi cap nhat so du.");
                } else {
                    System.err.println("Khong the cap nhat so du, SW: " + 
                        (response != null ? Integer.toHexString(response.getSW()) : "null"));
                }
                return false;
            }
        } catch (Exception e) {
            System.err.println("Loi cap nhat so du: " + e.getMessage());
            return false;
        }
    }
    public boolean updatePicture(byte[] pictureBytes) {
        try {
            if (!"T=1".equals(protocol)) {
                System.err.println("LOI: He thong chi ho tro T=1 protocol");
                return false;
            }

            byte[] command;
            if (pictureBytes.length > 255) {
                int lcHigh = (pictureBytes.length >> 8) & 0xFF;
                int lcLow = pictureBytes.length & 0xFF;
                
                command = new byte[7 + pictureBytes.length];
                command[0] = (byte) 0x00;
                command[1] = (byte) 0x22;
                command[2] = (byte) 0x00;
                command[3] = (byte) 0x00;
                command[4] = (byte) 0x00;
                command[5] = (byte) lcHigh;
                command[6] = (byte) lcLow;
                System.arraycopy(pictureBytes, 0, command, 7, pictureBytes.length);
            } else {
                command = new byte[5 + pictureBytes.length];
                command[0] = (byte) 0x00;
                command[1] = (byte) 0x22;
                command[2] = (byte) 0x00;
                command[3] = (byte) 0x00;
                command[4] = (byte) pictureBytes.length;
                System.arraycopy(pictureBytes, 0, command, 5, pictureBytes.length);
            }

            ResponseAPDU response = sendCommandAPDU(command);

            if (response == null || response.getSW() != 0x9000) {
                System.err.println("Khong the cap nhat anh, SW: " +
                    (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return false;
            }

            return true;
        } catch (Exception e) {
            System.err.println("Loi cap nhat anh: " + e.getMessage());
            return false;
        }
    }

    /**
     * Đọc ảnh khách hàng từ thẻ
     * Trả về byte array của ảnh, hoặc null nếu lỗi
     */
    public byte[] getPicture() {
        try {
            if (!"T=1".equals(protocol)) {
                System.err.println("LOI: He thong chi ho tro T=1 protocol");
                return null;
            }

            //  Đọc ảnh theo CHUNK, phù hợp với applet mới (P1|P2 = offset)
            java.util.List<byte[]> chunks = new java.util.ArrayList<>();

            int offset = 0;
            int totalRead = 0;
            final int MAX_TOTAL = 32767;   // đồng bộ với MAX_PICTURE_LEN của applet
            final int MAX_CHUNKS = 240;    // giới hạn an toàn để tránh vòng lặp vô hạn

            System.out.println("[BusSmartCard] Bat dau doc anh theo chunk...");

            for (int i = 0; i < MAX_CHUNKS && totalRead < MAX_TOTAL; i++) {
                // Tính P1, P2 từ offset
                // p1: dịch phải 8 bit để lấy byte cao và giữ lại 8 bit thấp
                // p2: lấy 8 bit thấp
                byte p1 = (byte) ((offset >> 8) & 0xFF);
                byte p2 = (byte) (offset & 0xFF);

                // Le = 0x00: xin tối đa card cho phép trong 1 response
                byte[] command = new byte[]{(byte) 0x00, (byte) 0x23, p1, p2, (byte) 0x00};
                ResponseAPDU resp = sendCommandAPDU(command);

                int sw = resp.getSW();
                byte[] data = resp.getData();

                System.out.println("[BusSmartCard] GET_PICTURE chunk #" + i +
                        " - offset=" + offset +
                        ", SW=0x" + String.format("%04X", sw) +
                        ", len=" + (data != null ? data.length : 0));

                // Nếu SW không phải 0x9000 hoặc data rỗng, dừng lại
                if (sw != 0x9000) {
                    System.out.println("[BusSmartCard] GET_PICTURE dừng lại do SW khác 0x9000: 0x" + String.format("%04X", sw));
                    break;
                }
                
                // Nếu data rỗng và offset = 0, có nghĩa là thẻ chưa có ảnh
                if (data == null || data.length == 0) {
                    if (offset == 0) {
                        System.out.println("[BusSmartCard] Thẻ chưa có ảnh (pictureLen = 0)");
                        return null; // Trả về null để báo là chưa có ảnh
                    }
                    // Nếu offset > 0 và data rỗng, có nghĩa là đã đọc hết
                    break;
                }

                chunks.add(data);
                totalRead += data.length;
                offset += data.length;

                // Không dừng theo kích thước chunk; tiếp tục cho tới khi thẻ trả SW khác 0x9000
                // hoặc data.length == 0 ở vòng lặp sau.
            }

            System.out.println("[BusSmartCard] Tong so chunks: " + chunks.size() +
                    ", tong kich thuoc: " + totalRead + " bytes");
            // Gom các chunk lại
            byte[] pictureBytes = new byte[totalRead];
            int pos = 0;
            for (byte[] chunk : chunks) {
                System.arraycopy(chunk, 0, pictureBytes, pos, chunk.length);
                pos += chunk.length;
            }

            return pictureBytes;
        } catch (Exception e) {
            System.err.println("Loi doc anh: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Đọc ảnh khách hàng từ thẻ, trả về BufferedImage
     * @return BufferedImage nếu thành công, null nếu thất bại hoặc chưa có ảnh
     */
    public BufferedImage GetPatientPicture() {
        try {
            byte[] imageData = getPicture();
            
            if (imageData == null || imageData.length == 0) {
                System.out.println("Khong co du lieu anh tu the");
                return null;
            }
            
            // Debug: In số bytes nhận được
            System.out.println("Nhan duoc " + imageData.length + " bytes tu the");
            
            // Debug: In nội dung bytes (hex)
            System.out.print("Noi dung du lieu (hex): ");
            int printLen = Math.min(imageData.length, 20); // Chỉ in 20 bytes đầu
            for (int i = 0; i < printLen; i++) {
                System.out.printf("%02X ", imageData[i] & 0xFF);
            }
            if (imageData.length > 20) {
                System.out.print("...");
            }
            System.out.println();
            
            // Chuyển đổi byte array thành BufferedImage
            BufferedImage image = HelpMethod.convertByteArrayToImage(imageData);
            if (image == null) {
                System.err.println("Khong the chuyen doi byte array sang BufferedImage");
                return null;
            }
            
            System.out.println("Doc anh thanh cong, kich thuoc: " + image.getWidth() + "x" + image.getHeight());
            return image;
        } catch (Exception e) {
            System.err.println("Loi doc anh tu the: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ========== BẢO MẬT ==========

    public byte[] getPublicKey() {
        byte[] command = {(byte) 0x00, (byte) 0x24, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);

        if (response != null && response.getSW() == 0x9000) {
            byte[] data = response.getData();
            return data;
        } else {
            System.err.println("Khong the lay public key, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }
    
    /**
     * Gửi challenge xuống thẻ để thẻ ký (RSA SHA1withRSA)
     *
     * @param challengeBytes dữ liệu challenge (ví dụ chuỗi 6 chữ số ở dạng bytes)
     * @return chữ ký do thẻ trả về (byte[]), hoặc null nếu lỗi
     */
    public byte[] signChallenge(byte[] challengeBytes) {
        if (challengeBytes == null || challengeBytes.length == 0) {
            System.err.println("Challenge rong, khong the ky");
            return null;
        }
        
        try {
            // INS_GET_SIGN = 0x25, P1 = 0x00, P2 = 0x00
            byte[] command = new byte[5 + challengeBytes.length];
            command[0] = (byte) 0x00;
            command[1] = (byte) 0x25;
            command[2] = (byte) 0x00;
            command[3] = (byte) 0x00;
            command[4] = (byte) challengeBytes.length;
            System.arraycopy(challengeBytes, 0, command, 5, challengeBytes.length);

            ResponseAPDU response = sendCommandAPDU(command);
            if (response != null && response.getSW() == 0x9000) {
                return response.getData();
            } else {
                System.err.println("Khong the lay chu ky RSA, SW: " +
                        (response != null ? Integer.toHexString(response.getSW()) : "null"));
                return null;
            }
        } catch (Exception e) {
            System.err.println("Loi gui challenge RSA: " + e.getMessage());
            return null;
        }
    }

    // ========== QUẢN LÝ THẺ ==========

    public boolean checkCardCreated() {
        byte[] command = {(byte) 0x00, (byte) 0x29, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            return true;
        } else if (response != null && response.getSW() == 0x6A88) {
            return false;
        } else {
            System.err.println("Khong the kiem tra trang thai the");
            return false;
        }
    }

    public boolean clearCard() {
        byte[] command = {(byte) 0x00, (byte) 0x18, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            return true;
        } else {
            System.err.println("Khong the xoa the, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return false;
        }
    }
    
    public boolean unlockCard() {
        byte[] command = {(byte) 0x00, (byte) 0x11, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            isCardBlocked = false;
            counter = 0;
            return true;
        } else {
            System.err.println("Khong the mo khoa the, SW: " +
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return false;
        }
    }

    // ========== INIT CARD HELPER (FIX THỨ TỰ GỌI) ==========



    /**
     * Hàm khởi tạo thẻ trọn gói.
     * Tự động thực hiện đúng thứ tự:
     * 1. Set PIN (để sinh Key AES).
     * 2. Nạp Info, Balance, ID, Picture (được mã hóa bởi Key vừa sinh).
     */
    public boolean initCard(String pin, String cardId, String customerInfo, String balance, byte[] pictureData) {
        if (pin == null || pin.isEmpty()) {
            System.err.println("Init: PIN khong duoc de trong");
            return false;
        }

        // 1. QUAN TRỌNG: Cập nhật PIN trước tiên
        // Với thẻ mới, oldPin là chuỗi rỗng
        System.out.println("Init: Dang thiet lap PIN...");
        if (!updatePin("", pin)) {
            System.err.println("Init: Loi thiet lap PIN. Huy qua trinh.");
            return false;
        }

        // 2. Sau khi có PIN -> Có Key -> Nạp các thông tin khác
        System.out.println("Init: Dang nap thong tin...");
        
        if (cardId != null && !cardId.isEmpty()) {
            if (!updateCardId(cardId)) {
                System.err.println("Init: Loi nap Card ID");
                return false;
            }
        }

        if (customerInfo != null && !customerInfo.isEmpty()) {
            if (!updateCustomerInfo(customerInfo)) {
                System.err.println("Init: Loi nap Thong tin khach hang");
                return false;
            }
        }

        if (balance != null && !balance.isEmpty()) {
            if (!updateBalance(balance)) {
                System.err.println("Init: Loi nap So du");
                return false;
            }
        }

        if (pictureData != null && pictureData.length > 0) {
            System.out.println("Init: Dang nap anh...");
            if (!updatePicture(pictureData)) {
                System.err.println("Init: Loi nap Anh");
                return false;
            }
        }

        System.out.println("Init: Nap the thanh cong!");
        return true;
    }
}
