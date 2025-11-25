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

    public static final byte[] AID_APPLET = {
        (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44, (byte) 0x55,
        (byte) 0x00, (byte) 0x01
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
            if (card != null) {
                try {
                    card.disconnect(false);
                } catch (Exception e) {
                }
                card = null;
                channel = null;
            }

            factory = TerminalFactory.getDefault();
            terminals = factory.terminals().list();

            if (terminals.isEmpty()) {
                return false;
            }

            for (CardTerminal term : terminals) {
                if (term.isCardPresent()) {
                    terminal = term;

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }

                    try {
                        if (!term.isCardPresent()) {
                            continue;
                        }
                    } catch (CardException e) {
                        continue;
                    }

                    String supportedProtocol = null;
                    try {
                        Card testCard = terminal.connect("*");
                        if (testCard != null) {
                            supportedProtocol = testCard.getProtocol();
                            testCard.disconnect(false);

                            if (supportedProtocol == null || !supportedProtocol.equals("T=1")) {
                                System.err.println("LOI: Card khong ho tro T=1 protocol");
                                continue;
                            }
                        }
                    } catch (CardException e) {
                    }

                    try {
                        card = terminal.connect("T=1");

                        if (card == null) {
                            continue;
                        }

                        protocol = "T=1";
                    } catch (CardException e) {
                        System.err.println("LOI: Khong the ket noi voi T=1 protocol - " + e.getMessage());
                        continue;
                    }

                    String actualProtocol = card.getProtocol();

                    if (actualProtocol == null || !actualProtocol.equals("T=1")) {
                        System.err.println("LOI: Card tra ve protocol " + actualProtocol + " thay vi T=1");
                        try {
                            card.disconnect(false);
                        } catch (Exception e) {
                        }
                        card = null;
                        channel = null;
                        continue;
                    }

                    protocol = "T=1";
                    channel = card.getBasicChannel();

                    if (channel == null) {
                        try {
                            card.disconnect(false);
                        } catch (Exception e) {
                        }
                        card = null;
                        continue;
                    }

                    response = channel.transmit(new CommandAPDU(0x00, (byte) 0xA4, 0x04, 0x00, AID_APPLET));
                    String statusWord = Integer.toHexString(response.getSW());

                    if (statusWord.equals("9000")) {
                        isConnected = true;
                        return true;
                    } else if (statusWord.equals("6400")) {
                        isConnected = true;
                        System.err.println("Canh bao: The da bi vo hieu hoa");
                        return true;
                    } else {
                        try {
                            card.disconnect(false);
                        } catch (Exception e) {
                        }
                        card = null;
                        channel = null;
                    }
                }
            }

            return false;
        } catch (CardException ex) {
            ex.printStackTrace();
            return false;
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
            System.err.println("Khong the lay thong tin khach hang, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }

    public boolean updateCustomerInfo(String hoTen, String loaiDoiTuong, String ngayHetHan, 
                                      String loaiThe, String linkedCustomerId) {
        try {
            String dataBuilder = hoTen + "." +
                                loaiDoiTuong + "." +
                                ngayHetHan + "." +
                                loaiThe + "." +
                                (linkedCustomerId != null ? linkedCustomerId : "");

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

    public boolean updatePin(String newPin) {
        try {
            byte[] pinBytes = HelpMethod.ConvertStringToByteArr(newPin);

            byte[] command = new byte[5 + pinBytes.length];
            command[0] = (byte) 0x00;
            command[1] = (byte) 0x21;
            command[2] = (byte) 0x00;
            command[3] = (byte) 0x00;
            command[4] = (byte) pinBytes.length;
            System.arraycopy(pinBytes, 0, command, 5, pinBytes.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
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

    public boolean verifyPin(String userPin) {
        try {
            byte[] pinBytes = HelpMethod.ConvertStringToByteArr(userPin);

            byte[] command = new byte[5 + pinBytes.length];
            command[0] = (byte) 0x00;
            command[1] = (byte) 0x30;
            command[2] = (byte) 0x00;
            command[3] = (byte) 0x00;
            command[4] = (byte) pinBytes.length;
            System.arraycopy(pinBytes, 0, command, 5, pinBytes.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null) {
                byte[] responseBytes = response.getBytes();
                int sw = response.getSW();

                if (responseBytes.length >= 1 && responseBytes[0] == (byte) 0x00 && sw == 0x9000) {
                    return true;
                } else if (responseBytes.length >= 1 && responseBytes[0] == (byte) 0x01 && sw == 0x9000) {
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
        try {
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

    public BufferedImage getPicture() {
        byte[] command = {
            (byte) 0x00, (byte) 0x23, (byte) 0x00, (byte) 0x00, 
            (byte) 0x00, (byte) 0x00, (byte) 0x00
        };

        ResponseAPDU response = sendCommandAPDU(command);

        if (response != null && response.getSW() == 0x9000) {
            byte[] imageData = response.getData();
            return HelpMethod.convertByteArrayToImage(imageData);
        } else {
            System.err.println("Khong the lay anh, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return null;
        }
    }

    public boolean updatePicture(BufferedImage image) {
        byte[] pictureBytes = HelpMethod.convertImageToByteArray(image);
        if (pictureBytes == null) {
            System.err.println("Khong the chuyen doi image sang byte array");
            return false;
        }
        return updatePicture(pictureBytes);
    }

    public boolean updatePicture(byte[] pictureBytes) {
        try {
            if (pictureBytes == null || pictureBytes.length == 0) {
                System.err.println("Anh rong, khong co gi de ghi len the");
                return false;
            }
            
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

    public boolean verifyCard(byte[] publicKey) {
        try {
            String randomData = HelpMethod.generateRandomString(20);
            byte[] dataToVerify = HelpMethod.ConvertStringToByteArr(randomData);

            byte[] command = new byte[5 + dataToVerify.length];
            command[0] = (byte) 0x00;
            command[1] = (byte) 0x25;
            command[2] = (byte) 0x00;
            command[3] = (byte) 0x00;
            command[4] = (byte) dataToVerify.length;
            System.arraycopy(dataToVerify, 0, command, 5, dataToVerify.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
                byte[] signedData = response.getData();
                boolean verified = HelpMethod.verifySignature(publicKey, dataToVerify, signedData);
                
                if (!verified) {
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

    public boolean lockCard() {
        byte[] command = {(byte) 0x00, (byte) 0x28, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        ResponseAPDU response = sendCommandAPDU(command);
        
        if (response != null && response.getSW() == 0x9000) {
            isCardBlocked = true;
            return true;
        } else {
            System.err.println("Khong the khoa the, SW: " + 
                (response != null ? Integer.toHexString(response.getSW()) : "null"));
            return false;
        }
    }

    // ========== QUẸT THẺ ==========

    public boolean updateLastTapInfo(String routeId, String tapType, String timestamp) {
        try {
            String tapInfo = routeId + "." + tapType + "." + timestamp;
            byte[] tapInfoBytes = HelpMethod.ConvertStringToByteArr(tapInfo);

            byte[] command = new byte[5 + tapInfoBytes.length];
            command[0] = (byte) 0x00;
            command[1] = (byte) 0x31;
            command[2] = (byte) 0x00;
            command[3] = (byte) 0x00;
            command[4] = (byte) tapInfoBytes.length;
            System.arraycopy(tapInfoBytes, 0, command, 5, tapInfoBytes.length);

            ResponseAPDU response = sendCommandAPDU(command);

            if (response != null && response.getSW() == 0x9000) {
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
