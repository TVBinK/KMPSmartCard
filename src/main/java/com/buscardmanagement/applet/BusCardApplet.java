package com.buscardmanagement.applet;

import javacard.framework.*;
import javacard.security.*;

/**
 * BusCardApplet - Java Card Applet cho hệ thống quản lý thẻ xe bus
 * 
 * AID: 11 22 33 44 55 00 01
 * 
 * Chức năng:
 * - Quản lý thông tin khách hàng
 * - Quản lý số dư
 * - Xác thực PIN với counter
 * - Lưu/đọc ảnh khách hàng (hỗ trợ T=1 Extended Length, lên đến 32KB)
 * - Xác thực thẻ bằng RSA signature
 * - Quản lý thông tin quẹt thẻ
 */

public class BusCardApplet extends Applet {
    
    // AID của applet
    private static final byte[] AID_BYTES = {
        (byte)0x11, (byte)0x22, (byte)0x33, (byte)0x44, 
        (byte)0x55, (byte)0x00, (byte)0x01
    };
    
    // INS codes
    private static final byte INS_GET_CUSTOMER_INFO = (byte)0x13;
    private static final byte INS_GET_BALANCE = (byte)0x14;
    private static final byte INS_UPDATE_BALANCE = (byte)0x16;
    private static final byte INS_CLEAR_CARD = (byte)0x18;
    private static final byte INS_CHECK_PIN = (byte)0x19;
    private static final byte INS_UPDATE_CUSTOMER_INFO = (byte)0x20;
    private static final byte INS_UPDATE_PIN = (byte)0x21;
    private static final byte INS_UPDATE_PICTURE = (byte)0x22;
    private static final byte INS_GET_PICTURE = (byte)0x23;
    private static final byte INS_GET_PUBLIC_KEY = (byte)0x24;
    private static final byte INS_GET_SIGN = (byte)0x25;
    private static final byte INS_UPDATE_CARD_ID = (byte)0x26;
    private static final byte INS_GET_CARD_ID = (byte)0x27;
    private static final byte INS_LOCK_CARD = (byte)0x28;
    private static final byte INS_CHECK_CARD_CREATED = (byte)0x29;
    private static final byte INS_VERIFY_PIN = (byte)0x30;
    private static final byte INS_UPDATE_LAST_TAP = (byte)0x31;
    private static final byte INS_GET_LAST_TAP_INFO = (byte)0x32;
    private static final byte INS_UNLOCK_CARD = (byte)0x11;
    
    // Status words
    private static final short SW_SUCCESS = (short)0x9000;
    private static final short SW_CARD_NOT_INITIALIZED = (short)0x6A88;
    private static final short SW_AUTH_FAILED = (short)0x6983;
    private static final short SW_WRONG_PARAMS = (short)0x6A80;
    private static final short SW_CARD_BLOCKED = (byte)0x6400;
    
    // Persistent storage
    private OwnerPIN pin;
    private byte[] customerInfo;      // Thông tin khách hàng
    private byte[] balance;           // Số dư
    private byte[] cardId;            // Card ID
    private byte[] picture;           // Ảnh khách hàng
    private byte[] lastTapInfo;       // Thông tin quẹt thẻ gần nhất
    private boolean isInitialized;    // Thẻ đã khởi tạo chưa
    private boolean isCardBlocked;    // Thẻ bị khóa chưa
    private byte pinAttempts;         // Số lần nhập PIN sai
    
    // RSA key pair cho xác thực thẻ
    private RSAPrivateKey rsaPrivateKey;
    private RSAPublicKey rsaPublicKey;
    
    // Constants cho kích thước buffer
    private static final short MAX_CUSTOMER_INFO_LEN = (short)200;
    private static final short MAX_BALANCE_LEN = (short)20;
    private static final short MAX_CARD_ID_LEN = (short)50;
    private static final short MAX_PICTURE_LEN = (short)32767;  // 32KB cho ảnh (T=1 Extended Length, max 32KB - giới hạn của short)
    private static final short MAX_TAP_INFO_LEN = (short)100;
    private static final byte MAX_PIN_ATTEMPTS = (byte)4;
    
    /**
     * Constructor
     */

    private BusCardApplet() {
        // Khởi tạo PIN với max attempts = 4
        pin = new OwnerPIN(MAX_PIN_ATTEMPTS, (byte)8);
        
        // Khởi tạo các buffer
        customerInfo = new byte[MAX_CUSTOMER_INFO_LEN];
        balance = new byte[MAX_BALANCE_LEN];
        cardId = new byte[MAX_CARD_ID_LEN];
        picture = new byte[MAX_PICTURE_LEN];
        lastTapInfo = new byte[MAX_TAP_INFO_LEN];
        
        // Khởi tạo RSA key pair (1024 bits)
        try {
            KeyPair rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, (short)1024);
            rsaKeyPair.genKeyPair();
            rsaPrivateKey = (RSAPrivateKey)rsaKeyPair.getPrivate();
            rsaPublicKey = (RSAPublicKey)rsaKeyPair.getPublic();
        } catch (CryptoException e) {
            // Nếu không tạo được RSA key, để null
            rsaPrivateKey = null;
            rsaPublicKey = null;
        }
        
        isInitialized = false;
        isCardBlocked = false;
        pinAttempts = (byte)0;
    }
    
    /**
     * Install applet
     */

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new BusCardApplet().register(bArray, (short)(bOffset + 1), bArray[bOffset]);
    }
    
    /**
     * Process APDU command
     */

    public void process(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        
        // Kiểm tra SELECT command
        if (selectingApplet()) {
            if (isCardBlocked) {
                ISOException.throwIt(SW_CARD_BLOCKED);
            }
            // Trả về response thành công khi SELECT applet
            // Java Card framework sẽ tự động trả về 9000 nếu không có exception
            return;
        }
        
        // Kiểm tra CLA - Chỉ chấp nhận CLA = 0x00
        if (buffer[ISO7816.OFFSET_CLA] != (byte)0x00) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        
        byte ins = buffer[ISO7816.OFFSET_INS];
        
        // SELECT APPLET command (INS = 0xA4) - Đã được xử lý bởi selectingApplet()
        // Nếu đến đây với INS = 0xA4, có thể là SELECT file/DF khác, không phải SELECT applet
        if (ins == (byte)0xA4) {
            // Không phải SELECT applet, trả về lỗi
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
        }
        
        // CHECK CARD CREATED - Luôn cho phép kiểm tra
        if (ins == INS_CHECK_CARD_CREATED) {
            if (isInitialized) {
                ISOException.throwIt(SW_SUCCESS);
            } else {
                ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
            }
            return;
        }
        
        // CLEAR CARD - Xóa toàn bộ dữ liệu
        if (ins == INS_CLEAR_CARD) {
            clearCardData();
            ISOException.throwIt(SW_SUCCESS);
            return;
        }
        
        // UPDATE CUSTOMER INFO - Khởi tạo thẻ
        if (ins == INS_UPDATE_CUSTOMER_INFO) {
            updateCustomerInfo(apdu);
            isInitialized = true;
            pinAttempts = (byte)0; // Reset PIN attempts khi khởi tạo
            ISOException.throwIt(SW_SUCCESS);
            return;
        }
        
        // Các lệnh khác yêu cầu thẻ đã khởi tạo
        if (!isInitialized) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }
        
        // Kiểm tra thẻ bị khóa
        if (isCardBlocked) {
            ISOException.throwIt(SW_CARD_BLOCKED);
        }
        
        // Xử lý các lệnh khác
        switch (ins) {
            case INS_GET_CUSTOMER_INFO:
                getCustomerInfo(apdu);
                break;
                
            case INS_GET_BALANCE:
                getBalance(apdu);
                break;
                
            case INS_UPDATE_BALANCE:
                updateBalance(apdu);
                break;
                
            case INS_GET_CARD_ID:
                getCardId(apdu);
                break;
                
            case INS_UPDATE_CARD_ID:
                updateCardId(apdu);
                break;
                
            case INS_CHECK_PIN:
                checkPin(apdu);
                break;
                
            case INS_VERIFY_PIN:
                verifyPin(apdu);
                break;
                
            case INS_UPDATE_PIN:
                updatePin(apdu);
                break;
                
            case INS_GET_PICTURE:
                getPicture(apdu);
                break;
                
            case INS_UPDATE_PICTURE:
                updatePicture(apdu);
                break;
                
            case INS_GET_PUBLIC_KEY:
                getPublicKey(apdu);
                break;
                
            case INS_GET_SIGN:
                getSign(apdu);
                break;
                
            case INS_LOCK_CARD:
                lockCard();
                break;
                
            case INS_UNLOCK_CARD:
                unlockCard();
                break;
                
            case INS_UPDATE_LAST_TAP:
                updateLastTapInfo(apdu);
                break;
                
            case INS_GET_LAST_TAP_INFO:
                getLastTapInfo(apdu);
                break;
                
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
    
    /**
     * Xóa toàn bộ dữ liệu trên thẻ
     */

    private void clearCardData() {
        Util.arrayFillNonAtomic(customerInfo, (short)0, (short)customerInfo.length, (byte)0);
        Util.arrayFillNonAtomic(balance, (short)0, (short)balance.length, (byte)0);
        Util.arrayFillNonAtomic(cardId, (short)0, (short)cardId.length, (byte)0);
        Util.arrayFillNonAtomic(picture, (short)0, (short)picture.length, (byte)0);
        Util.arrayFillNonAtomic(lastTapInfo, (short)0, (short)lastTapInfo.length, (byte)0);
        
        pin.reset();
        isInitialized = false;
        isCardBlocked = false;
        pinAttempts = (byte)0;
    }
    
    /**
     * Cập nhật thông tin khách hàng
     */

    private void updateCustomerInfo(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc > MAX_CUSTOMER_INFO_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, 
                               customerInfo, (short)0, lc);
    }
    
    /**
     * Lấy thông tin khách hàng
     */

    private void getCustomerInfo(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = findDataLength(customerInfo);
        
        if (len == 0) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }
        
        Util.arrayCopyNonAtomic(customerInfo, (short)0, 
                               buffer, (short)0, len);
        apdu.setOutgoingAndSend((short)0, len);
    }
    
    /**
     * Cập nhật số dư
     */

    private void updateBalance(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc > MAX_BALANCE_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, 
                               balance, (short)0, lc);
    }
    
    /**
     * Lấy số dư
     */

    private void getBalance(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = findDataLength(balance);
        
        if (len == 0) {
            // Nếu chưa có số dư, trả về "0"
            byte[] zero = {(byte)'0'};
            Util.arrayCopyNonAtomic(zero, (short)0, buffer, (short)0, (short)1);
            apdu.setOutgoingAndSend((short)0, (short)1);
            return;
        }
        
        Util.arrayCopyNonAtomic(balance, (short)0, 
                               buffer, (short)0, len);
        apdu.setOutgoingAndSend((short)0, len);
    }
    
    /**
     * Cập nhật Card ID
     */

    private void updateCardId(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc > MAX_CARD_ID_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, 
                               cardId, (short)0, lc);
    }
    
    /**
     * Lấy Card ID
     */

    private void getCardId(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = findDataLength(cardId);
        
        if (len == 0) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }
        
        Util.arrayCopyNonAtomic(cardId, (short)0, 
                               buffer, (short)0, len);
        apdu.setOutgoingAndSend((short)0, len);
    }
    
    /**
     * Kiểm tra PIN với counter
     */

    private void checkPin(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc == 0) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        // Kiểm tra PIN - signature: check(byte[] pin, short offset, byte length)
        if (pin.check(buffer, ISO7816.OFFSET_CDATA, (byte)lc)) {
            // PIN đúng
            pinAttempts = (byte)0;
            buffer[0] = (byte)0x00;
            apdu.setOutgoingAndSend((short)0, (short)1);
        } else {
            // PIN sai
            pinAttempts++;
            if (pinAttempts >= MAX_PIN_ATTEMPTS) {
                isCardBlocked = true;
                ISOException.throwIt(SW_AUTH_FAILED);
            }
            buffer[0] = pinAttempts;
            apdu.setOutgoingAndSend((short)0, (short)1);
        }
    }
    
    /**
     * Xác thực PIN đơn giản (không counter)
     */

    private void verifyPin(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc == 0) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        // Kiểm tra PIN - signature: check(byte[] pin, short offset, byte length)
        if (pin.check(buffer, ISO7816.OFFSET_CDATA, (byte)lc)) {
            buffer[0] = (byte)0x00;
        } else {
            buffer[0] = (byte)0x01;
        }
        apdu.setOutgoingAndSend((short)0, (short)1);
    }
    
    /**
     * Cập nhật PIN
     */

    private void updatePin(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc == 0 || lc > 8) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        // Kiểm tra PIN đã được xác thực hoặc đây là lần đầu set PIN
        if (!pin.isValidated() && pin.getTriesRemaining() < MAX_PIN_ATTEMPTS) {
            // PIN chưa được xác thực và đã có PIN cũ
            ISOException.throwIt(SW_AUTH_FAILED);
        }
        
        // Update PIN - signature: update(byte[] pin, short offset, byte length)
        pin.update(buffer, ISO7816.OFFSET_CDATA, (byte)lc);
        pinAttempts = (byte)0; // Reset attempts sau khi đổi PIN
    }
    
    /**
     * Cập nhật ảnh - Hỗ trợ T=1 Extended Length APDU
     * 
     * Format APDU:
     * - Standard (T=0): CLA INS P1 P2 Lc [Data] (max 255 bytes)
     * - Extended (T=1): CLA INS P1 P2 00 Lc_High Lc_Low [Data] (lên đến 32KB)
     * 
     * Hỗ trợ nhận dữ liệu lớn lên đến MAX_PICTURE_LEN (32KB - 32767 bytes)
     */

    private void updatePicture(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        
        // Đọc Lc (Length of Command data) từ buffer để kiểm tra trước
        // Format Extended Length: CLA INS P1 P2 00 Lc_High Lc_Low [Data]
        // Format Standard: CLA INS P1 P2 Lc [Data]
        short lc = (short)(buffer[ISO7816.OFFSET_LC] & 0xFF);
        
        // Kiểm tra Extended Length format (T=1)
        // Nếu Lc = 0x00, có thể là Extended Length format
        if (lc == 0x00) {
            // Kiểm tra Extended Length (3 bytes: 00 Lc_High Lc_Low)
            short lcHigh = (short)(buffer[ISO7816.OFFSET_LC + 1] & 0xFF);
            short lcLow = (short)(buffer[ISO7816.OFFSET_LC + 2] & 0xFF);
            
            // Tính toán giá trị Lc từ 2 bytes
            lc = (short)((lcHigh << 8) | lcLow);
            
            // Kiểm tra Extended Length hợp lệ
            if (lc == 0 || lc > MAX_PICTURE_LEN) {
                ISOException.throwIt(SW_WRONG_PARAMS);
            }
        } else {
            // Standard Length format (Lc <= 255)
            // Kiểm tra giá trị hợp lệ
            if (lc > MAX_PICTURE_LEN) {
                ISOException.throwIt(SW_WRONG_PARAMS);
            }
        }
        
        short totalRead = 0;
        short bytesRead = apdu.setIncomingAndReceive();
        
        while (true) {
            if (bytesRead <= 0) {
                break;
            }
            
            if ((short)(totalRead + bytesRead) > MAX_PICTURE_LEN) {
                ISOException.throwIt(SW_WRONG_PARAMS);
            }
            
            Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, 
                                    picture, totalRead, bytesRead);
            totalRead = (short)(totalRead + bytesRead);
            
            if (totalRead >= lc) {
                break;
            }
            
            bytesRead = apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }
        
        if (totalRead != lc) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        if (totalRead < picture.length) {
            short remaining = (short)(picture.length - totalRead);
            Util.arrayFillNonAtomic(picture, totalRead, remaining, (byte)0x00);
        }
    }
    
    /**
     * Lấy ảnh - Hỗ trợ chunking cho response lớn
     */

    private void getPicture(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = findDataLength(picture);
        
        if (len == 0) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }
        
        // Gửi dữ liệu theo chunks nếu quá lớn
        // T=0: max 256 bytes per response
        // T=1: có thể lớn hơn nhưng vẫn chunk để an toàn
        short offset = (short)0;
        short remaining = len;
        short chunkSize = (short)256; // Max response length an toàn
        
        while (remaining > 0) {
            short toSend = remaining > chunkSize ? chunkSize : remaining;
            Util.arrayCopyNonAtomic(picture, offset, buffer, (short)0, toSend);
            apdu.setOutgoingAndSend((short)0, toSend);
            offset = (short)(offset + toSend);
            remaining = (short)(remaining - toSend);
        }
    }
    
    /**
     * Lấy RSA Public Key
     */

    private void getPublicKey(APDU apdu) {
        if (rsaPublicKey == null) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }
        
        byte[] buffer = apdu.getBuffer();
        short offset = (short)0;
        
        // Lấy modulus
        short modLen = rsaPublicKey.getModulus(buffer, offset);
        offset = (short)(offset + modLen);
        
        // Lấy exponent
        short expLen = rsaPublicKey.getExponent(buffer, offset);
        offset = (short)(offset + expLen);
        
        // Thêm độ dài modulus và exponent (4 bytes)
        buffer[offset++] = (byte)((modLen >> 8) & 0xFF);
        buffer[offset++] = (byte)(modLen & 0xFF);
        buffer[offset++] = (byte)((expLen >> 8) & 0xFF);
        buffer[offset++] = (byte)(expLen & 0xFF);
        
        apdu.setOutgoingAndSend((short)0, offset);
    }
    
    /**
     * Ký dữ liệu bằng RSA
     */

    private void getSign(APDU apdu) {
        if (rsaPrivateKey == null) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }
        
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc == 0) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        try {
            Signature rsaSign = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
            rsaSign.init(rsaPrivateKey, Signature.MODE_SIGN);
            rsaSign.update(buffer, ISO7816.OFFSET_CDATA, lc);
            // Signature sign: sign(byte[] inBuff, short inOffset, short inLength, byte[] sigBuff, short sigOffset)
            short sigLen = rsaSign.sign(buffer, ISO7816.OFFSET_CDATA, lc, buffer, (short)0);
            apdu.setOutgoingAndSend((short)0, sigLen);
        } catch (CryptoException e) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
    }
    
    /**
     * Khóa thẻ
     */

    private void lockCard() {
        isCardBlocked = true;
    }
    
    /**
     * Mở khóa thẻ
     */

    private void unlockCard() {
        isCardBlocked = false;
        pinAttempts = (byte)0;
        pin.reset();
    }
    
    /**
     * Cập nhật thông tin quẹt thẻ
     */

    private void updateLastTapInfo(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        if (lc > MAX_TAP_INFO_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, 
                               lastTapInfo, (short)0, lc);
    }
    
    /**
     * Lấy thông tin quẹt thẻ
     */

    private void getLastTapInfo(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short len = findDataLength(lastTapInfo);
        
        if (len == 0) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }
        
        Util.arrayCopyNonAtomic(lastTapInfo, (short)0, 
                               buffer, (short)0, len);
        apdu.setOutgoingAndSend((short)0, len);
    }
    
    /**
     * Tìm độ dài thực tế của dữ liệu (bỏ qua các byte 0 ở cuối)
     */
    private short findDataLength(byte[] data) {
        for (short i = (short)(data.length - 1); i >= 0; i--) {
            if (data[i] != 0) {
                return (short)(i + 1);
            }
        }
        return (short)0;
    }
}
