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
import javacard.security.MessageDigest;
import javacard.security.RSAPrivateKey;
import javacard.security.RSAPublicKey;
import javacard.security.RandomData;
import javacard.security.Signature;
import javacardx.crypto.Cipher;

/**
 * BusCardApplet - Java Card Applet cho hệ thống quản lý thẻ xe bus
 * Compatible: JavaCard 2.2.2
 */
public class BusCardApplet extends javacard.framework.Applet {

    // AID
    private static final byte[] AID_BYTES = {
            (byte) 0x11, (byte) 0x22, (byte) 0x33, (byte) 0x44,
            (byte) 0x55, (byte) 0x00, (byte) 0x01
    };

    // INS codes
    private static final byte INS_GET_CUSTOMER_INFO = (byte) 0x13;
    private static final byte INS_GET_BALANCE = (byte) 0x14;
    private static final byte INS_UPDATE_BALANCE = (byte) 0x16;
    private static final byte INS_CLEAR_CARD = (byte) 0x18;
    private static final byte INS_CHECK_PIN = (byte) 0x19;
    private static final byte INS_UPDATE_CUSTOMER_INFO = (byte) 0x20;
    private static final byte INS_UPDATE_PIN = (byte) 0x21;
    private static final byte INS_UPDATE_PICTURE = (byte) 0x22;
    private static final byte INS_GET_PICTURE = (byte) 0x23;
    private static final byte INS_GET_PUBLIC_KEY = (byte) 0x24;
    private static final byte INS_GET_SIGN = (byte) 0x25;
    private static final byte INS_UPDATE_CARD_ID = (byte) 0x26;
    private static final byte INS_GET_CARD_ID = (byte) 0x27;
    private static final byte INS_LOCK_CARD = (byte) 0x28;
    private static final byte INS_CHECK_CARD_CREATED = (byte) 0x29;
    private static final byte INS_VERIFY_PIN = (byte) 0x30;
    private static final byte INS_UPDATE_LAST_TAP = (byte) 0x31;
    private static final byte INS_GET_LAST_TAP_INFO = (byte) 0x32;
    private static final byte INS_UNLOCK_CARD = (byte) 0x11;

    // Status words
    private static final short SW_SUCCESS = (short) 0x9000;
    private static final short SW_CARD_NOT_INITIALIZED = (short) 0x6A88;
    private static final short SW_AUTH_FAILED = (short) 0x6983;
    private static final short SW_WRONG_PARAMS = (short) 0x6A80;
    private static final short SW_CARD_BLOCKED = (short) 0x6400;

    // AES constants
    private static final short AES_BLOCK_SIZE = (short) 16;
    private static final short TEMP_BUFFER_SIZE = (short) 256;

    // PBKDF2 Constants
    private static final short PBKDF2_ITERATIONS = 1000;
    private static final short SALT_LEN = (short) 16;

    // Plaintext maximum lengths
    private static final short MAX_CUSTOMER_INFO_LEN = (short) 200;
    private static final short MAX_BALANCE_LEN = (short) 20;
    private static final short MAX_CARD_ID_LEN = (short) 50;
    private static final short MAX_TAP_INFO_LEN = (short) 100;
    private static final short MAX_PIN_LEN = (short) 32;
    private static final short PIN_HASH_LEN = (short) 16;
    private static final short MAX_PICTURE_LEN = (short) 32767;

    // Storage lengths
    private static final short CUSTOMER_INFO_STORE_LEN = (short) 208;
    private static final short BALANCE_STORE_LEN = (short) 32;
    private static final short CARD_ID_STORE_LEN = (short) 64;
    private static final short TAP_INFO_STORE_LEN = (short) 112;
    private static final short PIN_STORE_LEN = (short) 16;

    private static final byte MAX_PIN_ATTEMPTS = (byte) 4;

    // Persistent storage
    private OwnerPIN pin;
    private byte[] customerInfo;
    private byte[] balance;
    private byte[] cardId;
    private byte[] picture;
    private byte[] lastTapInfo;
    private byte[] pinEncrypted;

    // Lengths
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
    private byte[] aesKeyBytes; // Buffer tạm cho Key
    private byte[] aesIV;
    private byte[] tempBuffer;
    
    // MD5 for PIN Hashing (Iterative for security)
    private MessageDigest md5;
    private byte[] pinHashBuffer;
    // PBKDF2 Components REMOVED due to compatibility issues



    // RSA
    private RSAPrivateKey rsaPrivateKey;
    private RSAPublicKey rsaPublicKey;

    private boolean isPictureEncrypted;

    private BusCardApplet() {
        // PIN stores Hash 16 bytes
        pin = new OwnerPIN(MAX_PIN_ATTEMPTS, (byte) PIN_HASH_LEN);

        customerInfo = new byte[CUSTOMER_INFO_STORE_LEN];
        balance = new byte[BALANCE_STORE_LEN];
        cardId = new byte[CARD_ID_STORE_LEN];
        picture = new byte[MAX_PICTURE_LEN];
        lastTapInfo = new byte[TAP_INFO_STORE_LEN];
        pinEncrypted = new byte[PIN_STORE_LEN];

        aesKeyBytes = new byte[16];
        aesIV = new byte[16];
        tempBuffer = new byte[TEMP_BUFFER_SIZE];
        aesKeyBytes = new byte[16];
        aesIV = new byte[16];
        tempBuffer = new byte[TEMP_BUFFER_SIZE];
        pinHashBuffer = new byte[PIN_HASH_LEN];

        try {
             // Init MD5
             md5 = MessageDigest.getInstance(MessageDigest.ALG_MD5, false);
        } catch (CryptoException e) {
            ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
        }

        // Random IV init
        RandomData randomGen = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
        randomGen.generateData(aesIV, (short) 0, (short) aesIV.length);
        
        // AES Key
        aesKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
        // Init temp random key (will be overwritten by PIN hash)
        randomGen.generateData(aesKeyBytes, (short) 0, (short) 16);
        aesKey.setKey(aesKeyBytes, (short) 0);
        
        aesCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);

        try {
            KeyPair rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, (short) 1024);
            rsaKeyPair.genKeyPair();
            rsaPrivateKey = (RSAPrivateKey) rsaKeyPair.getPrivate();
            rsaPublicKey = (RSAPublicKey) rsaKeyPair.getPublic();
        } catch (CryptoException e) {
            rsaPrivateKey = null;
            rsaPublicKey = null;
        }

        resetState();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new BusCardApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public void process(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        if (selectingApplet()) {
            if (isCardBlocked) {
                ISOException.throwIt(SW_CARD_BLOCKED);
            }
            return;
        }

        if (buffer[ISO7816.OFFSET_CLA] != (byte) 0x00) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }

        byte ins = buffer[ISO7816.OFFSET_INS];

        if (ins == (byte) 0xA4) {
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
            pinAttempts = (byte) 0;
            ISOException.throwIt(SW_SUCCESS);
            return;
        }

        // Xử lý UPDATE_PIN: Cho phép khi tạo PIN lần đầu mà không cần initialized
        if (ins == INS_UPDATE_PIN) {
            updatePin(apdu);
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
            // INS_UPDATE_PIN đã được xử lý trước khi kiểm tra initialized
            // Không cần xử lý lại ở đây
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
        Util.arrayFillNonAtomic(customerInfo, (short) 0, (short) customerInfo.length, (byte) 0);
        Util.arrayFillNonAtomic(balance, (short) 0, (short) balance.length, (byte) 0);
        Util.arrayFillNonAtomic(cardId, (short) 0, (short) cardId.length, (byte) 0);
        Util.arrayFillNonAtomic(picture, (short) 0, (short) picture.length, (byte) 0);
        Util.arrayFillNonAtomic(lastTapInfo, (short) 0, (short) lastTapInfo.length, (byte) 0);
        Util.arrayFillNonAtomic(pinEncrypted, (short) 0, (short) pinEncrypted.length, (byte) 0);
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

    // --- PIN & KEY LOGIC ---

    /**
     * Compute Hash(PIN)
     * Algo: MD5 Loop 1000 times
     * Output: 16 bytes
     */
    private void computePinHash(byte[] pinBuf, short pinOff, short length, byte[] outBuf) {
        if (md5 == null) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }

        // 1. First Hash: MD5(PIN) -> tempBuffer[0..15]
        md5.reset();
        md5.doFinal(pinBuf, pinOff, length, tempBuffer, (short) 0);

        // 2. Loop 999 times: MD5(prevHash) -> newHash
        for (short i = 1; i < PBKDF2_ITERATIONS; i++) {
            // Check for potential overlap issue: output to tempBuffer[16..31]
            md5.reset();
            md5.doFinal(tempBuffer, (short) 0, (short) 16, tempBuffer, (short) 16);
            // Copy back [16..31] to [0..15]
            Util.arrayCopyNonAtomic(tempBuffer, (short) 16, tempBuffer, (short) 0, (short) 16);
        }

        // 3. Result to outBuf
        Util.arrayCopyNonAtomic(tempBuffer, (short) 0, outBuf, (short) 0, (short) 16);
    }

    private void checkPin(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        if (lc == 0 || lc > MAX_PIN_LEN) ISOException.throwIt(SW_WRONG_PARAMS);

        computePinHash(buffer, ISO7816.OFFSET_CDATA, lc, pinHashBuffer);

        if (!pin.check(pinHashBuffer, (short) 0, (byte) PIN_HASH_LEN)) {
            pinAttempts++;
            if (pinAttempts >= MAX_PIN_ATTEMPTS) {
                isCardBlocked = true;
                ISOException.throwIt(SW_AUTH_FAILED);
            }
            buffer[0] = pinAttempts;
            apdu.setOutgoingAndSend((short) 0, (short) 1);
            return;
        }

        pinAttempts = 0;
        aesKey.setKey(pinHashBuffer, (short) 0); // Update Key on success
        buffer[0] = 0x00;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    private void verifyPin(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        
        computePinHash(buffer, ISO7816.OFFSET_CDATA, lc, pinHashBuffer);

        if (pin.check(pinHashBuffer, (short) 0, (byte) PIN_HASH_LEN)) {
             buffer[0] = 0x00;
             aesKey.setKey(pinHashBuffer, (short) 0);
        } else {
             buffer[0] = 0x01;
        }
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    private void updatePin(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        byte oldPinLength = buffer[ISO7816.OFFSET_CDATA];
        short expectedLength = (short) (1 + oldPinLength);
        short newPinLength = (short) (lc - expectedLength);

        if (newPinLength <= 0 || newPinLength > MAX_PIN_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

        // ================= BƯỚC 1: XÁC THỰC PIN CŨ =================
        if (oldPinLength > 0 && pinLen != 0) {
            if (!isInitialized) {
                ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
            }

            short oldPinOffset = (short) (ISO7816.OFFSET_CDATA + 1);
            computePinHash(buffer, oldPinOffset, oldPinLength, pinHashBuffer);

            if (!pin.check(pinHashBuffer, (short) 0, (byte) PIN_HASH_LEN)) {
                pinAttempts++;
                if (pinAttempts >= MAX_PIN_ATTEMPTS) {
                    isCardBlocked = true;
                    ISOException.throwIt(SW_AUTH_FAILED);
                }
                buffer[0] = pinAttempts;
                apdu.setOutgoingAndSend((short) 0, (short) 1);
                return;
            }
        }

        // Nếu chưa có PIN (tạo lần đầu), cho phép bỏ qua verify

        // ================= BƯỚC 3: TẠO AES KEY MỚI TỪ PIN MỚI =================
        short newPinOffset;
        if (oldPinLength == 0) {
            newPinOffset = (short) (ISO7816.OFFSET_CDATA + 1);
        } else {
            newPinOffset = (short) (ISO7816.OFFSET_CDATA + 1 + oldPinLength);
        }

        // Hash PIN mới vào pinHashBuffer
        computePinHash(buffer, newPinOffset, (short) newPinLength, pinHashBuffer);

        // ================= ĐƠN GIẢN HÓA: XÓA DỮ LIỆU, ĐỂ APP GHI LẠI =================
        // Thay vì cố gắng re-encrypt trong applet (dễ lỗi 6F00, dữ liệu rác),
        // ta xóa sạch dữ liệu và để app (Kotlin) ghi lại từ DB/UI
        clearCardData();

        // ================= BƯỚC 5: CẬP NHẬT PIN HASH MỚI =================
        // Lưu hash mới vào OwnerPIN và AES key
        Util.arrayCopyNonAtomic(pinHashBuffer, (short) 0, aesIV, (short) 0, (short) 16);
        pin.update(pinHashBuffer, (short) 0, (byte) PIN_HASH_LEN);
        aesKey.setKey(pinHashBuffer, (short) 0);

        // Lưu PIN raw được mã hóa để đánh dấu pinLen != 0
        pinLen = encryptAes(buffer, newPinOffset, (short) newPinLength, pinEncrypted, (short) 0);

        // Reset trạng thái thử PIN
        pinAttempts = 0;
        isInitialized = true;

        // Thành công
        buffer[0] = 0x00;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }
    
    // --- DATA METHODS ---

    private void updateCustomerInfo(APDU apdu) {
        if (!pin.isValidated()) ISOException.throwIt(SW_AUTH_FAILED);
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        customerInfoLen = encryptAes(buffer, ISO7816.OFFSET_CDATA, lc, customerInfo, (short) 0);
    }

    private void getCustomerInfo(APDU apdu) {
        if (customerInfoLen == 0) ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        byte[] buffer = apdu.getBuffer();
        short decLen = decryptAes(customerInfo, (short) 0, customerInfoLen, buffer, (short) 0);
        apdu.setOutgoingAndSend((short) 0, decLen);
    }

    private void updateBalance(APDU apdu) {
        if (!pin.isValidated()) ISOException.throwIt(SW_AUTH_FAILED);
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        if (lc == 0 || lc > MAX_BALANCE_LEN) ISOException.throwIt(SW_WRONG_PARAMS);
        balanceLen = encryptAes(buffer, ISO7816.OFFSET_CDATA, lc, balance, (short) 0);
    }

    private void getBalance(APDU apdu) {
        if (balanceLen == 0) {
            apdu.getBuffer()[0] = (byte) '0';
            apdu.setOutgoingAndSend((short) 0, (short) 1);
            return;
        }
        byte[] buffer = apdu.getBuffer();
        short decLen = decryptAes(balance, (short) 0, balanceLen, buffer, (short) 0);
        apdu.setOutgoingAndSend((short) 0, decLen);
    }

    private void updateCardId(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        if (lc == 0 || lc > MAX_CARD_ID_LEN) ISOException.throwIt(SW_WRONG_PARAMS);
        cardIdLen = encryptAes(buffer, ISO7816.OFFSET_CDATA, lc, cardId, (short) 0);
    }

    private void getCardId(APDU apdu) {
        if (cardIdLen == 0) ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        byte[] buffer = apdu.getBuffer();
        short decLen = decryptAes(cardId, (short) 0, cardIdLen, buffer, (short) 0);
        apdu.setOutgoingAndSend((short) 0, decLen);
    }
    
    private void updateLastTapInfo(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        if (lc == 0 || lc > MAX_TAP_INFO_LEN) ISOException.throwIt(SW_WRONG_PARAMS);
        lastTapInfoLen = encryptAes(buffer, ISO7816.OFFSET_CDATA, lc, lastTapInfo, (short) 0);
    }

    private void getLastTapInfo(APDU apdu) {
        if (lastTapInfoLen == 0) ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        byte[] buffer = apdu.getBuffer();
        short decLen = decryptAes(lastTapInfo, (short) 0, lastTapInfoLen, buffer, (short) 0);
        apdu.setOutgoingAndSend((short) 0, decLen);
    }

    private void updatePicture(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        /**
         * XỬ LÝ EXTENDED LENGTH ĐÚNG CHUẨN:
         *
         * APDU host đang gửi (T=1, extended length) có dạng:
         *   CLA INS  P1  P2  00  LcHi LcLo  data...
         *        ^   ^   ^   ^   ^    ^     ^
         *       1   2   3   4   5    6     7 (index)
         *
         * - buffer[OFFSET_LC]     = 0x00      (byte mở rộng)
         * - buffer[OFFSET_LC+1]   = LcHi
         * - buffer[OFFSET_LC+2]   = LcLo
         * - buffer[OFFSET_CDATA]  = vẫn là 0x00 (không phải data ảnh)
         *
         * Vì vậy:
         *  - Phải tự đọc Lc từ OFFSET_LC+1/2
         *  - Và bỏ qua 2 byte LcHi/LcLo khi copy data đầu tiên
         *    => dataOffset ban đầu = OFFSET_CDATA + 2
         *
         * Với short APDU (Lc <= 255) thì:
         *  - buffer[OFFSET_LC] = Lc
         *  - buffer[OFFSET_CDATA] là data luôn (dataOffset = OFFSET_CDATA)
         */

        short firstLcByte = (short) (buffer[ISO7816.OFFSET_LC] & 0xFF);
        short lc;
        short dataOffset;

        if (firstLcByte == 0) {
            // Extended length: 00 LcHi LcLo
            short lcHigh = (short) (buffer[(short) (ISO7816.OFFSET_LC + 1)] & 0xFF);
            short lcLow = (short) (buffer[(short) (ISO7816.OFFSET_LC + 2)] & 0xFF);
            lc = (short) ((lcHigh << 8) | lcLow);
            // Bỏ qua 2 byte LcHi/LcLo, data thực bắt đầu sau đó
            dataOffset = (short) (ISO7816.OFFSET_CDATA + 2);
        } else {
            // Short APDU: Lc nằm trong 1 byte tại OFFSET_LC
            lc = firstLcByte;
            dataOffset = ISO7816.OFFSET_CDATA;
        }

        short maxPlaintextLen = (short) (MAX_PICTURE_LEN - AES_BLOCK_SIZE + 1);
        if (lc == 0 || lc > maxPlaintextLen) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

        short totalRead = 0;
        short bytesRead = apdu.setIncomingAndReceive();

        while (bytesRead > 0) {
            Util.arrayCopyNonAtomic(buffer, dataOffset, picture, totalRead, bytesRead);
            totalRead += bytesRead;
            if (totalRead >= lc) {
                break;
            }
            // Các block tiếp theo: data luôn ở OFFSET_CDATA
            bytesRead = apdu.receiveBytes(ISO7816.OFFSET_CDATA);
            dataOffset = ISO7816.OFFSET_CDATA;
        }

        if (totalRead != lc) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
        
        pictureLen = encryptAesPicture(picture, (short) 0, totalRead);
        isPictureEncrypted = true;
        if (pictureLen < picture.length) {
            Util.arrayFillNonAtomic(picture, pictureLen, (short) (picture.length - pictureLen), (byte) 0);
        }
    }

    private void getPicture(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        
        // Nếu chưa có ảnh, trả về response rỗng thay vì lỗi
        // Để client có thể phân biệt giữa "chưa có ảnh" và "lỗi đọc ảnh"
        if (pictureLen == 0) {
            apdu.setOutgoingAndSend((short) 0, (short) 0);
            return;
        }

        if (isPictureEncrypted) {
            try {
                short plaintextLenDecoded = decryptAesPicture(picture, (short) 0, pictureLen);
                pictureLen = plaintextLenDecoded;
                isPictureEncrypted = false;
            } catch (CryptoException e) {
                ISOException.throwIt(SW_WRONG_PARAMS);
            }
        }

        short plaintextLen = pictureLen;
        short offset = (short) (((short) (buffer[ISO7816.OFFSET_P1] & 0xFF) << 8) | (short) (buffer[ISO7816.OFFSET_P2] & 0xFF));
        short remaining = (short) (plaintextLen - offset);
        short maxChunkSize = (short) 240;
        short toSend = (remaining > maxChunkSize) ? maxChunkSize : remaining;
        
        Util.arrayCopyNonAtomic(picture, offset, buffer, (short) 0, toSend);
        apdu.setOutgoingAndSend((short) 0, toSend);
    }

    // --- AES HELPERS ---

    private short encryptAes(byte[] plaintext, short ptOffset, short ptLength, byte[] ciphertext, short ctOffset) {
        if (aesCipher == null || aesKey == null) ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        short paddedLength = computePaddedLength(ptLength);
        Util.arrayCopyNonAtomic(plaintext, ptOffset, tempBuffer, (short) 0, ptLength);
        byte paddingValue = (byte) (paddedLength - ptLength);
        for (short i = ptLength; i < paddedLength; i++) tempBuffer[i] = paddingValue;

        aesCipher.init(aesKey, Cipher.MODE_ENCRYPT, aesIV, (short) 0, (short) aesIV.length);
        return aesCipher.doFinal(tempBuffer, (short) 0, paddedLength, ciphertext, ctOffset);
    }

    private short decryptAes(byte[] ciphertext, short ctOffset, short ctLength, byte[] plaintext, short ptOffset) {
        if (aesCipher == null || aesKey == null) ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        aesCipher.init(aesKey, Cipher.MODE_DECRYPT, aesIV, (short) 0, (short) aesIV.length);
        short decLen = aesCipher.doFinal(ciphertext, ctOffset, ctLength, tempBuffer, (short) 0);
        byte paddingValue = tempBuffer[(short) (decLen - 1)];
        short actualLength = (short) (decLen - paddingValue);
        Util.arrayCopyNonAtomic(tempBuffer, (short) 0, plaintext, ptOffset, actualLength);
        return actualLength;
    }

    private short encryptAesPicture(byte[] plaintext, short ptOffset, short ptLength) {
        short paddedLength = computePaddedLength(ptLength);
        if (paddedLength > MAX_PICTURE_LEN) ISOException.throwIt(SW_WRONG_PARAMS);
        byte paddingValue = (byte) (paddedLength - ptLength);
        for (short i = (short) (ptOffset + ptLength); i < (short) (ptOffset + paddedLength); i++) {
            plaintext[i] = paddingValue;
        }
        aesCipher.init(aesKey, Cipher.MODE_ENCRYPT, aesIV, (short) 0, (short) aesIV.length);
        return aesCipher.doFinal(plaintext, ptOffset, paddedLength, plaintext, ptOffset);
    }

    private short decryptAesPicture(byte[] ciphertext, short ctOffset, short ctLength) {
        if (aesCipher == null || aesKey == null) ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        if ((ctLength % AES_BLOCK_SIZE) != 0) ISOException.throwIt(SW_WRONG_PARAMS);
        
        aesCipher.init(aesKey, Cipher.MODE_DECRYPT, aesIV, (short) 0, (short) aesIV.length);
        short decLen = aesCipher.doFinal(ciphertext, ctOffset, ctLength, ciphertext, ctOffset);
        
        byte paddingValue = ciphertext[(short) (ctOffset + decLen - 1)];
        short actualLength = (short) (decLen - paddingValue);
        if (actualLength < decLen) {
            Util.arrayFillNonAtomic(ciphertext, (short) (ctOffset + actualLength), (short) (decLen - actualLength), (byte) 0);
        }
        return actualLength;
    }

    private short computePaddedLength(short length) {
        short remainder = (short) (length % AES_BLOCK_SIZE);
        if (remainder == 0) return (short) (length + AES_BLOCK_SIZE);
        return (short) (length + (AES_BLOCK_SIZE - remainder));
    }

    // --- RSA & MISC ---
    private void getPublicKey(APDU apdu) {
        if (rsaPublicKey == null) ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        byte[] buffer = apdu.getBuffer();
        short offset = 0;
        short modLen = rsaPublicKey.getModulus(buffer, offset);
        offset += modLen;
        short expLen = rsaPublicKey.getExponent(buffer, offset);
        offset += expLen;
        buffer[offset++] = (byte) ((modLen >> 8) & 0xFF);
        buffer[offset++] = (byte) (modLen & 0xFF);
        buffer[offset++] = (byte) ((expLen >> 8) & 0xFF);
        buffer[offset++] = (byte) (expLen & 0xFF);
        apdu.setOutgoingAndSend((short) 0, offset);
    }

    private void getSign(APDU apdu) {
        if (rsaPrivateKey == null) ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        if (lc == 0) ISOException.throwIt(SW_WRONG_PARAMS);
        try {
            Signature rsaSign = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
            rsaSign.init(rsaPrivateKey, Signature.MODE_SIGN);
            short sigLen = rsaSign.sign(buffer, ISO7816.OFFSET_CDATA, lc, buffer, (short) 0);
            apdu.setOutgoingAndSend((short) 0, sigLen);
        } catch (CryptoException e) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
    }

    private void lockCard() { isCardBlocked = true; }
    private void unlockCard() { isCardBlocked = false; pinAttempts = 0; pin.reset(); }
}
