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
            RandomData randomGen = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
            randomGen.generateData(aesKeyBytes, (short)0, (short)aesKeyBytes.length);
            randomGen.generateData(aesIV, (short)0, (short)aesIV.length);

            aesKey = (AESKey)KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
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

        if (isCardBlocked) {
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
    }

    private void updateCustomerInfo(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        if (lc == 0 || lc > MAX_CUSTOMER_INFO_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

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
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        if (lc == 0 || lc > MAX_PIN_LEN) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

        if (!pin.isValidated() && pinLen != 0) {
            ISOException.throwIt(SW_AUTH_FAILED);
        }

        pin.update(buffer, ISO7816.OFFSET_CDATA, (byte)lc);
        pinLen = encryptAes(buffer, ISO7816.OFFSET_CDATA, lc, pinEncrypted, (short)0);
        pinAttempts = 0;
    }

    private void updatePicture(APDU apdu) {
        byte[] buffer = apdu.getBuffer();

        short lc = (short)(buffer[ISO7816.OFFSET_LC] & 0xFF);
        if (lc == 0) {
            lc = readExtendedLc(buffer);
        }

        short totalRead = 0;
        short bytesRead = apdu.setIncomingAndReceive();

        while (bytesRead > 0) {
            if ((short)(totalRead + bytesRead) > MAX_PICTURE_LEN) {
                ISOException.throwIt(SW_WRONG_PARAMS);
            }

            Util.arrayCopyNonAtomic(buffer, ISO7816.OFFSET_CDATA, picture, totalRead, bytesRead);
            totalRead = (short)(totalRead + bytesRead);

            if (totalRead >= lc) {
                break;
            }

            bytesRead = apdu.receiveBytes(ISO7816.OFFSET_CDATA);
        }

        if (totalRead != lc) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

        pictureLen = lc;

        if (pictureLen < picture.length) {
            Util.arrayFillNonAtomic(picture, pictureLen, (short)(picture.length - pictureLen), (byte)0);
        }
    }

    private void getPicture(APDU apdu) {
        if (pictureLen == 0) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }

        byte[] buffer = apdu.getBuffer();
        short offset = 0;
        short remaining = pictureLen;
        short chunkSize = (short)256;

        while (remaining > 0) {
            short toSend = remaining > chunkSize ? chunkSize : remaining;
            Util.arrayCopyNonAtomic(picture, offset, buffer, (short)0, toSend);
            apdu.setOutgoingAndSend((short)0, toSend);
            offset = (short)(offset + toSend);
            remaining = (short)(remaining - toSend);
        }
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
        if (aesCipher == null || aesKey == null) {
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        }

        short paddedLength = computePaddedLength(ptLength);
        if (paddedLength > TEMP_BUFFER_SIZE) {
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

        Util.arrayCopyNonAtomic(plaintext, ptOffset, tempBuffer, (short)0, ptLength);
        byte paddingValue = (byte)(paddedLength - ptLength);
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
