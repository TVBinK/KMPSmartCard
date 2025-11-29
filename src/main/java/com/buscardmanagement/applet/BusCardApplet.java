package BusCardApplet;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;
import javacard.security.AESKey;
import javacard.security.CryptoException;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.RSAPrivateKey;
import javacard.security.RSAPublicKey;
import javacard.security.RandomData;
import javacard.security.Signature;
import javacardx.crypto.Cipher;

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
    private static final short SW_CARD_BLOCKED = (short)0x6400;

    // AES constants
    private static final short AES_BLOCK_SIZE = (short)16;
    private static final short TEMP_BUFFER_SIZE = (short)256;

    // Plaintext maximum lengths (trước khi padding)
    private static final short MAX_CUSTOMER_INFO_LEN = (short)200;
    private static final short MAX_BALANCE_LEN = (short)20;
    private static final short MAX_CARD_ID_LEN = (short)50;
    private static final short MAX_TAP_INFO_LEN = (short)100;
    private static final short MAX_PIN_LEN = (short)8;
    private static final short MAX_PICTURE_LEN = (short)32767; // 32KB (giới hạn short)

    // Ciphertext storage lengths (đã căn theo block AES)
    private static final short CUSTOMER_INFO_STORE_LEN = (short)208; // 200 + padding
    private static final short BALANCE_STORE_LEN = (short)32;        // 20 + padding
    private static final short CARD_ID_STORE_LEN = (short)64;        // 50 + padding
    private static final short TAP_INFO_STORE_LEN = (short)112;      // 100 + padding
    private static final short PIN_STORE_LEN = (short)16;            // 8 + padding

    private static final byte MAX_PIN_ATTEMPTS = (byte)4;

    // Persistent storage
    private final OwnerPIN pin;
    private final byte[] customerInfo;
    private final byte[] balance;
    private final byte[] cardId;
    private final byte[] picture;
    private final byte[] lastTapInfo;
    private final byte[] pinEncrypted;

    // Độ dài dữ liệu đã mã hóa / plaintext
    private short customerInfoLen;
    private short balanceLen;
    private short cardIdLen;
    private short lastTapInfoLen;
    private short pinLen;
    private short pictureLen;

    private boolean isInitialized;
    private boolean isCardBlocked;
    private byte pinAttempts;

    // AES
    private AESKey aesKey;
    private Cipher aesCipher;
    private final byte[] aesKeyBytes;
    private final byte[] aesIV;
    private final byte[] tempBuffer;

    // RSA key pair
    private RSAPrivateKey rsaPrivateKey;
    private RSAPublicKey rsaPublicKey;

    // Trạng thái mã hóa của ảnh:
    // - true: dữ liệu trong picture hiện đang là ciphertext AES, độ dài pictureLen là độ dài ciphertext
    // - false: dữ liệu trong picture là plaintext, độ dài pictureLen là độ dài ảnh thật
    private boolean isPictureEncrypted;

    private BusCardApplet() {
        pin = new OwnerPIN(MAX_PIN_ATTEMPTS, (byte)MAX_PIN_LEN);

        customerInfo = new byte[CUSTOMER_INFO_STORE_LEN];
        balance = new byte[BALANCE_STORE_LEN];
        cardId = new byte[CARD_ID_STORE_LEN];
        picture = new byte[MAX_PICTURE_LEN];
        lastTapInfo = new byte[TAP_INFO_STORE_LEN];
        pinEncrypted = new byte[PIN_STORE_LEN];

        aesKeyBytes = new byte[16];
        aesIV = new byte[16];
        tempBuffer = new byte[TEMP_BUFFER_SIZE];

        try {
            // Lấy đối tượng sinh số ngẫu nhiên bảo mật (ALG_SECURE_RANDOM).
            RandomData randomGen = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
            // Tạo ngẫu nhiên 16 byte vào aesKeyBytes → đây là khóa AES bí mật, chỉ nằm trong thẻ.
            randomGen.generateData(aesKeyBytes, (short)0, (short)aesKeyBytes.length);
            // Ghi ngẫu nhiên 16 byte vào aesIV
            randomGen.generateData(aesIV, (short)0, (short)aesIV.length);
            // tạo vùng nhớ cho khóa AES-128
            aesKey = (AESKey)KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
            // nạp 16 byte sinh ngẫu nhiên vào đối tượng aesKey
            aesKey.setKey(aesKeyBytes, (short)0);
            aesCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);
        } catch (CryptoException e) {
            aesKey = null;
            aesCipher = null;
        }

        try {
            KeyPair rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, (short)1024);
            rsaKeyPair.genKeyPair();
            rsaPrivateKey = (RSAPrivateKey)rsaKeyPair.getPrivate();
            rsaPublicKey = (RSAPublicKey)rsaKeyPair.getPublic();
        } catch (CryptoException e) {
            rsaPrivateKey = null;
            rsaPublicKey = null;
        }

        resetState();
    }
    //Install applet
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new BusCardApplet().register(bArray, (short)(bOffset + 1), bArray[bOffset]);
    }

    public void process(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        if (selectingApplet()) {
            if (isCardBlocked) {
                ISOException.throwIt(SW_CARD_BLOCKED);
            }
            return;
        }

        if (buffer[ISO7816.OFFSET_CLA] != (byte)0x00) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        byte ins = buffer[ISO7816.OFFSET_INS];

        if (ins == (byte)0xA4) {
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
        }

        if (ins == INS_CHECK_CARD_CREATED) {
            ISOException.throwIt(isInitialized ? SW_SUCCESS : SW_CARD_NOT_INITIALIZED);
            return;
        }

        if (ins == INS_CLEAR_CARD) {
            clearCardData();
            ISOException.throwIt(SW_SUCCESS);
            return;
        }

        if (ins == INS_UPDATE_CUSTOMER_INFO) {
            updateCustomerInfo(apdu);
            isInitialized = true;
            pinAttempts = (byte)0;
            ISOException.throwIt(SW_SUCCESS);
            return;
        }

        if (!isInitialized) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }

        if (isCardBlocked && ins != INS_UNLOCK_CARD) {
            ISOException.throwIt(SW_CARD_BLOCKED);
        }

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

    private void clearCardData() {
        Util.arrayFillNonAtomic(customerInfo, (short)0, (short)customerInfo.length, (byte)0);
        Util.arrayFillNonAtomic(balance, (short)0, (short)balance.length, (byte)0);
        Util.arrayFillNonAtomic(cardId, (short)0, (short)cardId.length, (byte)0);
        Util.arrayFillNonAtomic(picture, (short)0, (short)picture.length, (byte)0);
        Util.arrayFillNonAtomic(lastTapInfo, (short)0, (short)lastTapInfo.length, (byte)0);
        Util.arrayFillNonAtomic(pinEncrypted, (short)0, (short)pinEncrypted.length, (byte)0);

        resetState();
    }

    private void resetState() {
        customerInfoLen = 0;
        balanceLen = 0;
        cardIdLen = 0;
        lastTapInfoLen = 0;
        pinLen = 0;
        pictureLen = 0;
        isInitialized = false;
        isCardBlocked = false;
        pinAttempts = 0;
        pin.reset();
        isPictureEncrypted = false;
    }

    private void updateCustomerInfo(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        //Plaintext: buffer[ISO7816.OFFSET_CDATA] đến buffer[ISO7816.OFFSET_CDATA + lc - 1]
        customerInfoLen = encryptAes(buffer, ISO7816.OFFSET_CDATA, lc, customerInfo, (short)0);
    }

    private void getCustomerInfo(APDU apdu) {
        if (customerInfoLen == 0) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }

        byte[] buffer = apdu.getBuffer();
        short decLen = decryptAes(customerInfo, (short)0, customerInfoLen, buffer, (short)0);
        apdu.setOutgoingAndSend((short)0, decLen);
    }

    private void updateBalance(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        if (lc == 0 || lc > MAX_BALANCE_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

        balanceLen = encryptAes(buffer, ISO7816.OFFSET_CDATA, lc, balance, (short)0);
    }

    private void getBalance(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        if (balanceLen == 0) {
            buffer[0] = (byte)'0';
            apdu.setOutgoingAndSend((short)0, (short)1);
            return;
        }

        short decLen = decryptAes(balance, (short)0, balanceLen, buffer, (short)0);
        apdu.setOutgoingAndSend((short)0, decLen);
    }

    private void updateCardId(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        if (lc == 0 || lc > MAX_CARD_ID_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

        cardIdLen = encryptAes(buffer, ISO7816.OFFSET_CDATA, lc, cardId, (short)0);
    }

    private void getCardId(APDU apdu) {
        if (cardIdLen == 0) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }

        byte[] buffer = apdu.getBuffer();
        short decLen = decryptAes(cardId, (short)0, cardIdLen, buffer, (short)0);
        apdu.setOutgoingAndSend((short)0, decLen);
    }

    private void checkPin(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        if (lc == 0 || lc > MAX_PIN_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

        if (!pin.check(buffer, ISO7816.OFFSET_CDATA, (byte)lc)) {
            pinAttempts++;
            if (pinAttempts >= MAX_PIN_ATTEMPTS) {
                isCardBlocked = true;
                ISOException.throwIt(SW_AUTH_FAILED);
            }
            buffer[0] = pinAttempts;
            apdu.setOutgoingAndSend((short)0, (short)1);
            return;
        }

        pinAttempts = 0;
        buffer[0] = 0x00;
        apdu.setOutgoingAndSend((short)0, (short)1);
    }

    private void verifyPin(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        if (lc == 0 || lc > MAX_PIN_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

        buffer[0] = pin.check(buffer, ISO7816.OFFSET_CDATA, (byte)lc) ? (byte)0x00 : (byte)0x01;
        apdu.setOutgoingAndSend((short)0, (short)1);
    }

    private void updatePin(APDU apdu) {
        // Format: oldPinLength (1 byte) + oldPin + newPin
        // Trường hợp tạo PIN lần đầu (pinLen == 0): oldPinLength = 0, chỉ có newPin
        // Trường hợp đổi PIN: oldPinLength > 0, có cả oldPin và newPin
        // Tối thiểu: 1 (oldPinLength) + 1 (newPin) = 2 bytes (khi tạo PIN lần đầu)
        // Tối đa: 1 + MAX_PIN_LEN + MAX_PIN_LEN = 1 + 8 + 8 = 17 bytes
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        // Đọc độ dài PIN cũ
        byte oldPinLength = buffer[ISO7816.OFFSET_CDATA];
        // Kiểm tra tổng độ dài hợp lệ
        short expectedLength = (short)(1 + oldPinLength);
        short newPinLength = (short)(lc - expectedLength);

        // Nếu thẻ đã có PIN (pinLen != 0), phải kiểm tra PIN cũ
        if (pinLen != 0) {
            // Kiểm tra PIN cũ trước
            short oldPinOffset = (short)(ISO7816.OFFSET_CDATA + 1);
            if (!pin.check(buffer, oldPinOffset, oldPinLength)) {
                // PIN cũ sai - tăng counter
                pinAttempts++;
                if (pinAttempts >= MAX_PIN_ATTEMPTS) {
                    isCardBlocked = true;
                    ISOException.throwIt(SW_AUTH_FAILED);
                }
                // Trả về số lần thử còn lại
                buffer[0] = pinAttempts;
                apdu.setOutgoingAndSend((short)0, (short)1);
                return;
            }
        }

        // PIN cũ đúng (hoặc tạo PIN lần đầu) - cho phép đổi/tạo PIN mới
        short newPinOffset;
        if (oldPinLength == 0) {
            // Tạo PIN lần đầu
            newPinOffset = (short)(ISO7816.OFFSET_CDATA + 1);
        } else {
            // Đổi PIN
            short oldPinOffset = (short)(ISO7816.OFFSET_CDATA + 1);
            newPinOffset = (short)(oldPinOffset + oldPinLength);
        }
        
        pin.update(buffer, newPinOffset, (byte)newPinLength);
        pinLen = encryptAes(buffer, newPinOffset, (byte)newPinLength, pinEncrypted, (short)0);
        pinAttempts = 0;
        buffer[0] = 0x00;
        apdu.setOutgoingAndSend((short)0, (short)1);
    }

    private void updatePicture(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        short lc = (short)(buffer[ISO7816.OFFSET_LC] & 0xFF);
        short dataOffset = ISO7816.OFFSET_CDATA;

        // Đọc plaintext image vào tempBuffer trước
        // Giới hạn plaintext để sau khi mã hóa vẫn nằm trong MAX_PICTURE_LEN
        // Plaintext tối đa: 32752 bytes (32767 - 15) để sau khi padding (16 bytes) = 32768 bytes
        short maxPlaintextLen = (short)(MAX_PICTURE_LEN - AES_BLOCK_SIZE + 1);
        if (lc > maxPlaintextLen) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

        // Buffer tạm để lưu plaintext image (tối đa 32752 bytes)
        // Sử dụng picture array làm buffer tạm vì nó đủ lớn
        short totalRead = 0;
        short bytesRead = apdu.setIncomingAndReceive();

        while (bytesRead > 0) {
            //Copy bytes từ buffer[] vào picture[]
            Util.arrayCopyNonAtomic(buffer, dataOffset, picture, totalRead, bytesRead);
            totalRead = (short)(totalRead + bytesRead);

            if (totalRead >= lc) {
                break;
            }

            // Các lần sau dữ liệu luôn nằm tại OFFSET_CDATA
            dataOffset = ISO7816.OFFSET_CDATA;
            bytesRead = apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }
        if (totalRead != lc) {  // Kiểm tra xem có bị đọc thiếu byte
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        // Mã hóa AES plaintext image
        // picture hiện tại chứa plaintext từ offset 0, length = totalRead
        // Mã hóa trực tiếp vào chính picture array (overwrite) với padding tại chỗ
        pictureLen = encryptAesPicture(picture, (short)0, totalRead);
        isPictureEncrypted = true;

        // Xóa phần còn lại của picture array nếu có
        if (pictureLen < picture.length) {
            Util.arrayFillNonAtomic(picture, pictureLen, (short)(picture.length - pictureLen), (byte)0);
        }
    }

    /**
     * Đọc ảnh theo CHUNK.
     *
     * - Ảnh được lưu trên thẻ ở dạng AES + padding (ciphertext) trong mảng picture, độ dài pictureLen.
     * - Khi đọc:
     *   + Giải mã toàn bộ ciphertext vào lại mảng picture (plaintext).
     *   + Dùng P1|P2 làm offset (big-endian) trong ảnh plaintext.
     *   + Mỗi APDU chỉ trả về tối đa min(remaining, maxLenCanSend) byte.
     *
     * Giao thức host:
     *   CLA  INS   P1   P2   Le
     *   00   23   offHi offLo 00/NN
     *
     *   - off = (offHi<<8) | offLo: offset trong ảnh plaintext.
     *   - Host lặp lại lệnh với offset tăng dần cho tới khi số byte trả về < kích thước chunk mong muốn.
     */
    private void getPicture(APDU apdu) {
        if (pictureLen == 0) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }

        // Nếu ảnh đang ở dạng ciphertext, giải mã một lần sang plaintext mỗi trunk lại giải mã lại
        if (isPictureEncrypted) {
            try {
                short plaintextLenDecoded = decryptAesPicture(picture, (short)0, pictureLen);
                // Từ giờ trở đi, picture chứa plaintext, pictureLen là độ dài thực
                pictureLen = plaintextLenDecoded;
                isPictureEncrypted = false;
            } catch (CryptoException e) {
                // Bất kỳ lỗi crypto nào cũng quy về tham số sai
                ISOException.throwIt(SW_WRONG_PARAMS);
            }
        }

        short plaintextLen = pictureLen;
        byte[] buffer = apdu.getBuffer();
        //Đọc P1, P2 từ APDU buffer, sau đó ghép lại thành offset
        // P1|P2 là offset trong ảnh plaintext
        short offset = (short)(((short)(buffer[ISO7816.OFFSET_P1] & 0xFF) << 8)
                             |  (short)(buffer[ISO7816.OFFSET_P2] & 0xFF));

        // Số byte còn lại từ offset đến cuối ảnh
        short remaining = (short)(plaintextLen - offset);

        // Không dùng setOutgoing() để tránh vấn đề với extended-length / case APDU.
        // Mỗi chunk gửi tối đa 240 byte (an toàn với kích thước buffer APDU).
        short maxChunkSize = (short)240;
        short toSend = remaining;
        if (toSend > maxChunkSize) {
            toSend = maxChunkSize;
        }

        // Copy từ picture[offset] -> buffer[0..toSend-1] và gửi
        Util.arrayCopyNonAtomic(picture, offset, buffer, (short)0, toSend);
        apdu.setOutgoingAndSend((short)0, toSend);
    }
    
    // Phương thức mã hóa AES cho ảnh lớn (mã hóa trực tiếp vào cùng array)
    private short encryptAesPicture(byte[] plaintext, short ptOffset, short ptLength) {
        // Tính độ dài sau khi padding (phải là bội số của 16)
        short paddedLength = computePaddedLength(ptLength);
        if (paddedLength > MAX_PICTURE_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        // Thêm padding trực tiếp vào plaintext array
        byte paddingValue = (byte)(paddedLength - ptLength);
        for (short i = (short)(ptOffset + ptLength); i < (short)(ptOffset + paddedLength); i++) {
            plaintext[i] = paddingValue;
        }
        
        // Mã hóa trực tiếp vào cùng array (overwrite plaintext với ciphertext)
        aesCipher.init(aesKey, Cipher.MODE_ENCRYPT, aesIV, (short)0, (short)aesIV.length);
        return aesCipher.doFinal(plaintext, ptOffset, paddedLength, plaintext, ptOffset);
    }
    
    // Phương thức giải mã AES cho ảnh lớn (giải mã trực tiếp vào cùng array)
    private short decryptAesPicture(byte[] ciphertext, short ctOffset, short ctLength) {
        if (aesCipher == null || aesKey == null) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }
        
        // Kiểm tra ctLength phải là bội số của AES_BLOCK_SIZE
        if ((ctLength % AES_BLOCK_SIZE) != 0) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        // Giải mã vào chính ciphertext array (overwrite)
        // Vì plaintext nhỏ hơn ciphertext (do loại bỏ padding), ta có thể giải mã trực tiếp
        aesCipher.init(aesKey, Cipher.MODE_DECRYPT, aesIV, (short)0, (short)aesIV.length);
        short decLen = aesCipher.doFinal(ciphertext, ctOffset, ctLength, ciphertext, ctOffset);
        
        // Kiểm tra decLen hợp lệ
        if (decLen <= 0 || decLen > ctLength) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        // Loại bỏ padding
        byte paddingValue = ciphertext[(short)(ctOffset + decLen - 1)];
        
        // Kiểm tra padding hợp lệ (từ 1 đến 16)
        if (paddingValue < 1 || paddingValue > AES_BLOCK_SIZE) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        short actualLength = (short)(decLen - paddingValue);
        
        // Kiểm tra actualLength hợp lệ
        if (actualLength < 0 || actualLength >= decLen) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        // Xóa phần padding còn lại
        if (actualLength < decLen) {
            Util.arrayFillNonAtomic(ciphertext, (short)(ctOffset + actualLength), 
                                   (short)(decLen - actualLength), (byte)0);
        }
        
        return actualLength;
    }

    private void getPublicKey(APDU apdu) {
        if (rsaPublicKey == null) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }

        byte[] buffer = apdu.getBuffer();
        short offset = 0;

        short modLen = rsaPublicKey.getModulus(buffer, offset);
        offset += modLen;

        short expLen = rsaPublicKey.getExponent(buffer, offset);
        offset += expLen;

        buffer[offset++] = (byte)((modLen >> 8) & 0xFF);
        buffer[offset++] = (byte)(modLen & 0xFF);
        buffer[offset++] = (byte)((expLen >> 8) & 0xFF);
        buffer[offset++] = (byte)(expLen & 0xFF);

        apdu.setOutgoingAndSend((short)0, offset);
    }

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
            short sigLen = rsaSign.sign(buffer, ISO7816.OFFSET_CDATA, lc, buffer, (short)0);
            apdu.setOutgoingAndSend((short)0, sigLen);
        } catch (CryptoException e) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
    }

    private void lockCard() {
        isCardBlocked = true;
    }

    private void unlockCard() {
        isCardBlocked = false;
        pinAttempts = 0;
        pin.reset();
    }

    private void updateLastTapInfo(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        if (lc == 0 || lc > MAX_TAP_INFO_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

        lastTapInfoLen = encryptAes(buffer, ISO7816.OFFSET_CDATA, lc, lastTapInfo, (short)0);
    }

    private void getLastTapInfo(APDU apdu) {
        if (lastTapInfoLen == 0) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }

        byte[] buffer = apdu.getBuffer();
        short decLen = decryptAes(lastTapInfo, (short)0, lastTapInfoLen, buffer, (short)0);
        apdu.setOutgoingAndSend((short)0, decLen);
    }

    private short encryptAes(byte[] plaintext, short ptOffset, short ptLength,
                             byte[] ciphertext, short ctOffset) {
        // Nếu aesCipher hoặc aesKey chưa init → ném SW_CARD_NOT_INITIALIZED.
        if (aesCipher == null || aesKey == null) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }
        //gọi hàm tính độ dài sau khi padding
        short paddedLength = computePaddedLength(ptLength);
        // Copy plaintext từ plaintext[ptOffset .. ptOffset+ptLength-1] sang tempBuffer[0 .. ptLength-1].
        Util.arrayCopyNonAtomic(plaintext, ptOffset, tempBuffer, (short)0, ptLength);
        // tính số byte padding
        byte paddingValue = (byte)(paddedLength - ptLength);
        //Fill phần còn lại tempBuffer[ptLength .. paddedLength-1] = paddingValue.
        for (short i = ptLength; i < paddedLength; i++) {
            tempBuffer[i] = paddingValue;
        }

        aesCipher.init(aesKey, Cipher.MODE_ENCRYPT, aesIV, (short)0, (short)aesIV.length);
        return aesCipher.doFinal(tempBuffer, (short)0, paddedLength, ciphertext, ctOffset);
    }

    private short decryptAes(byte[] ciphertext, short ctOffset, short ctLength,
                             byte[] plaintext, short ptOffset) {
        if (aesCipher == null || aesKey == null) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }

        aesCipher.init(aesKey, Cipher.MODE_DECRYPT, aesIV, (short)0, (short)aesIV.length);
        short decLen = aesCipher.doFinal(ciphertext, ctOffset, ctLength, tempBuffer, (short)0);
        byte paddingValue = tempBuffer[(short)(decLen - 1)];
        short actualLength = (short)(decLen - paddingValue);
        Util.arrayCopyNonAtomic(tempBuffer, (short)0, plaintext, ptOffset, actualLength);
        return actualLength;
    }

    private short computePaddedLength(short length) {
        //Nếu ptLength đã bội số của 16 → paddedLength = length + 16 (thêm 1 block padding).
        //Nếu không → cộng thêm cho đủ bội số 16.
        short remainder = (short)(length % AES_BLOCK_SIZE);
        short padded = (short)(length + (short)(AES_BLOCK_SIZE - remainder));
        if (remainder == 0) {
            padded = (short)(length + AES_BLOCK_SIZE);
        }
        return padded;
    }

    private short readExtendedLc(byte[] buffer) {
        short lcHigh = (short)(buffer[ISO7816.OFFSET_LC + 1] & 0xFF);
        short lcLow = (short)(buffer[ISO7816.OFFSET_LC + 2] & 0xFF);
        short lc = (short)((lcHigh << 8) | lcLow);

        if (lc == 0 || lc > MAX_PICTURE_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

        return lc;
    }
}
