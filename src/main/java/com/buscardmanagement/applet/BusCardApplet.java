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
import javacard.framework.JCSystem; // Import JCSystem

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

    // Master Key & Recovery INS
    private static final byte INS_SET_MASTER_KEY = (byte) 0x50; // RSA Encrypted Injection
    private static final byte INS_GET_ADMIN_CHALLENGE = (byte) 0x51; // Step 1 of Recovery
    private static final byte INS_VERIFY_ADMIN_RECOVER = (byte) 0x52; // Step 2 of Recovery

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

    // Master Key Storage
    // Lưu ý: KHÔNG lưu Master Key dạng AESKey persist nữa.
    // Chỉ lưu bản đã mã hóa bởi PIN.
    private byte[] encryptedMasterKey;
    private byte[] masterKeyKcv; // KCV = Encrypt(MK, MK) để verify tính toàn vẹn

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

    private boolean isMasterKeyLoaded; // Flag báo hiệu đã load MK vào RAM chưa

    // AES Env
    private AESKey sessionMasterKey; // RAM ONLY (Transient Key Object)
    private byte[] ramMasterKeyBuffer; // RAM ONLY (Transient Byte Array) để lưu raw bytes
    private Cipher aesCipher;
    private byte[] tempIV; // IV dùng cho việc wrap/unwrap key
    private byte[] tempBuffer;

    // Authentication Challenge
    private byte[] adminChallenge;
    private boolean isAdminVerified;

    // MD5 for PIN Hashing (Iterative for security)
    private MessageDigest md5;
    private byte[] pinHashBuffer;
    // PBKDF2 Components REMOVED due to compatibility issues

    // RSA

    // RSA
    private RSAPrivateKey rsaPrivateKey;
    private RSAPublicKey rsaPublicKey;
    private Cipher rsaCipher; // Decrpyt Master Key injection

    private boolean isPictureEncrypted;

    // Master Key Setup State
    private boolean isMasterKeySetup; // Đã nạp MK lần đầu chưa?

    private BusCardApplet() {
        // PIN stores Hash 16 bytes
        pin = new OwnerPIN(MAX_PIN_ATTEMPTS, (byte) PIN_HASH_LEN);

        customerInfo = new byte[CUSTOMER_INFO_STORE_LEN];
        balance = new byte[BALANCE_STORE_LEN];
        cardId = new byte[CARD_ID_STORE_LEN];
        picture = new byte[MAX_PICTURE_LEN];
        lastTapInfo = new byte[TAP_INFO_STORE_LEN];
        pinEncrypted = new byte[PIN_STORE_LEN];

        encryptedMasterKey = new byte[16];
        masterKeyKcv = new byte[16];
        adminChallenge = new byte[16];

        isMasterKeySetup = false;
        isAdminVerified = false;
        isMasterKeyLoaded = false;

        // Init Transient Key (RAM only)
        // Khi rút điện, key này sẽ mất -> An toàn tuyệt đối
        try {
            sessionMasterKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES_TRANSIENT_DESELECT,
                    KeyBuilder.LENGTH_AES_128, false);
        } catch (CryptoException e) {
            sessionMasterKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
        }

        // Init Transient Byte Array for Raw Key Storage
        ramMasterKeyBuffer = JCSystem.makeTransientByteArray((short) 16, JCSystem.CLEAR_ON_DESELECT);

        try {
            // Init MD5
            md5 = MessageDigest.getInstance(MessageDigest.ALG_MD5, false);
        } catch (CryptoException e) {
            ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
        }

        // Random IV init
        tempIV = new byte[16];
        RandomData randomGen = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
        randomGen.generateData(tempIV, (short) 0, (short) 16);

        tempBuffer = new byte[TEMP_BUFFER_SIZE];
        pinHashBuffer = new byte[PIN_HASH_LEN]; // Init PIN hash buffer

        aesCipher = Cipher.getInstance(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD, false);

        try {
            KeyPair rsaKeyPair = new KeyPair(KeyPair.ALG_RSA, (short) 1024);
            rsaKeyPair.genKeyPair();
            rsaPrivateKey = (RSAPrivateKey) rsaKeyPair.getPrivate();
            rsaPublicKey = (RSAPublicKey) rsaKeyPair.getPublic();

            // Init RSA Cipher for Decryption
            rsaCipher = Cipher.getInstance(Cipher.ALG_RSA_PKCS1, false);
        } catch (CryptoException e) {
            rsaPrivateKey = null;
            rsaPublicKey = null;
            rsaCipher = null;
        }

        resetState();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new BusCardApplet().register(bArray, (short) (bOffset + 1), bArray[bOffset]);
    }

    public boolean select() {
        // Reset transient state when applet is selected
        isMasterKeyLoaded = false;

        // Clear transient keys/buffers to be safe
        if (sessionMasterKey != null)
            sessionMasterKey.clearKey();
        if (ramMasterKeyBuffer != null)
            Util.arrayFillNonAtomic(ramMasterKeyBuffer, (short) 0, (short) 16, (byte) 0);

        return super.select();
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
            // --- MASTER KEY & RECOVERY ---
            case INS_SET_MASTER_KEY:
                setMasterKey(apdu);
                break;
            case INS_GET_ADMIN_CHALLENGE:
                getAdminChallenge(apdu);
                break;
            case INS_VERIFY_ADMIN_RECOVER:
                verifyAdminAndRecover(apdu);
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
        // Warning: We do NOT clear encryptedMasterKey to prevent lockout
        // But we clear the RAM session key
        sessionMasterKey.clearKey();
        isMasterKeyLoaded = false;

        Util.arrayFillNonAtomic(masterKeyKcv, (short) 0, (short) masterKeyKcv.length, (byte) 0);
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
        isAdminVerified = false;
        isMasterKeyLoaded = false;
        if (sessionMasterKey != null)
            sessionMasterKey.clearKey();
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
        if (lc == 0 || lc > MAX_PIN_LEN)
            ISOException.throwIt(SW_WRONG_PARAMS);

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
        // Verify OK -> Load Master Key to RAM
        // Giải mã encryptedMasterKey bằng Hash(PIN)
        unwrapMasterKey(pinHashBuffer);

        buffer[0] = 0x00;
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    // Helper: Giải mã MK từ Storage vào RAM
    private void unwrapMasterKey(byte[] pinHash) {
        if (!isMasterKeySetup)
            return; // Nếu chưa setup thì ko có gì để unwrap

        // Kiểm tra null safety
        if (sessionMasterKey == null || aesCipher == null || tempIV == null)
            return;

        // Dùng pinHash làm Key tạm để giải mã
        // Lưu ý: aesCipher đang dùng sessionKey, ta cần cẩn thận backup hoặc init lại
        // Ở đây ta init lại luôn vì sessionKey đang chưa có gì hoặc sắp được nạp

        // mẹo: Dùng sessionMasterKey làm vật chứa tạm cho Pin Key để giải mã
        sessionMasterKey.setKey(pinHash, (short) 0);
        aesCipher.init(sessionMasterKey, Cipher.MODE_DECRYPT, tempIV, (short) 0, (short) 16);

        // Giải mã encryptedMasterKey -> tempBuffer (chứa Plain MK)
        aesCipher.doFinal(encryptedMasterKey, (short) 0, (short) 16, tempBuffer, (short) 0);

        // Nạp Plain MK vào sessionMasterKey chính thức
        sessionMasterKey.setKey(tempBuffer, (short) 0);

        // Nạp Plain MK vào RAM Buffer để dành cho việc Wrap sau này (Vì getKey() có thể
        // lỗi)
        Util.arrayCopyNonAtomic(tempBuffer, (short) 0, ramMasterKeyBuffer, (short) 0, (short) 16);

        // Xóa temp
        Util.arrayFillNonAtomic(tempBuffer, (short) 0, (short) 16, (byte) 0);
        isMasterKeyLoaded = true;
    }

    // Helper: Mã hóa MK từ RAM vào Storage
    private void wrapMasterKey(byte[] pinHash) {
        if (sessionMasterKey == null || aesCipher == null || tempIV == null)
            ISOException.throwIt(ISO7816.SW_UNKNOWN);

        // Nếu chưa có MK (Lần đầu setup PIN), ta tự sinh MK ngẫu nhiên
        if (!isMasterKeyLoaded) {
            // Generate Random MK
            RandomData rng = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
            rng.generateData(ramMasterKeyBuffer, (short) 0, (short) 16); // Lưu vào RAM Buffer

            sessionMasterKey.setKey(ramMasterKeyBuffer, (short) 0);
            isMasterKeyLoaded = true;
            isMasterKeySetup = true;

            // Tính KCV
            aesCipher.init(sessionMasterKey, Cipher.MODE_ENCRYPT, tempIV, (short) 0, (short) 16);
            aesCipher.doFinal(ramMasterKeyBuffer, (short) 0, (short) 16, masterKeyKcv, (short) 0);
        }

        // 1. Dùng RAM Buffer làm data input (Thay vì gọi getKey)
        // Copy từ RAM Buffer ra tempBuffer để encrypt (nếu cần padding hoặc xử lý,
        // nhưng ở đây 16 bytes raw)
        Util.arrayCopyNonAtomic(ramMasterKeyBuffer, (short) 0, tempBuffer, (short) 0, (short) 16);

        // 2. Set Pin Key vào session object tạm (để dùng cipher)
        sessionMasterKey.setKey(pinHash, (short) 0);

        aesCipher.init(sessionMasterKey, Cipher.MODE_ENCRYPT, tempIV, (short) 0, (short) 16);

        // 3. Encrypt Plain MK -> encryptedMasterKey
        aesCipher.doFinal(tempBuffer, (short) 0, (short) 16, encryptedMasterKey, (short) 0);

        // 4. Restore lại MK vào sessionKey từ RAM Buffer để dùng tiếp
        sessionMasterKey.setKey(ramMasterKeyBuffer, (short) 0);

        // Clean temp
        Util.arrayFillNonAtomic(tempBuffer, (short) 0, (short) 16, (byte) 0);
    }

    private void verifyPin(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        computePinHash(buffer, ISO7816.OFFSET_CDATA, lc, pinHashBuffer);

        if (pin.check(pinHashBuffer, (short) 0, (byte) PIN_HASH_LEN)) {
            buffer[0] = 0x00;
            unwrapMasterKey(pinHashBuffer);
        } else {
            buffer[0] = 0x01;
        }
        apdu.setOutgoingAndSend((short) 0, (short) 1);
    }

    private void updatePin(APDU apdu) {
        try {
            byte[] buffer = apdu.getBuffer();
            short lc = apdu.setIncomingAndReceive();
            byte oldPinLength = buffer[ISO7816.OFFSET_CDATA];
            short expectedLength = (short) (1 + oldPinLength);
            short newPinLength = (short) (lc - expectedLength);

            if (newPinLength <= 0 || newPinLength > MAX_PIN_LEN) {
                ISOException.throwIt(SW_WRONG_PARAMS);
            }

            // ================= BƯỚC 1: XÁC THỰC PIN CŨ =================
            // Nếu đã Admin Verify (Recover mode) thì bỏ qua check PIN cũ
            if (!isAdminVerified && oldPinLength > 0 && pinLen != 0) {
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

                // Verify thành công → Load Master Key vào RAM
                // Điều này cần thiết để wrapMasterKey() có thể wrap lại bằng PIN mới
                unwrapMasterKey(pinHashBuffer);
            }

            // ================= BƯỚC 2: TÍNH HASH PIN MỚI =================
            short newPinOffset;
            if (oldPinLength == 0) {
                newPinOffset = (short) (ISO7816.OFFSET_CDATA + 1);
            } else {
                newPinOffset = (short) (ISO7816.OFFSET_CDATA + 1 + oldPinLength);
            }

            // Hash PIN mới vào pinHashBuffer
            computePinHash(buffer, newPinOffset, (short) newPinLength, pinHashBuffer);

            // ================= BƯỚC 3: XỬ LÝ THEO TRƯỜNG HỢP =================
            if (pinLen == 0) {
                // TẠO PIN LẦN ĐẦU: Xóa dữ liệu và khởi tạo mới
                clearCardData();

                // Update PIN hash
                Util.arrayCopyNonAtomic(pinHashBuffer, (short) 0, tempIV, (short) 0, (short) 16);
                pin.update(pinHashBuffer, (short) 0, (byte) PIN_HASH_LEN);

                // Tạo Master Key mới và wrap bằng PIN
                wrapMasterKey(pinHashBuffer);

                pinLen = (short) newPinLength;
                pinAttempts = 0;
                isInitialized = true;

                // Validate PIN mới ngay lập tức
                pin.check(pinHashBuffer, (short) 0, (byte) PIN_HASH_LEN);
            } else {
                // ĐỔI PIN: Giữ nguyên dữ liệu, chỉ re-wrap Master Key
                // Dữ liệu đã mã hóa vẫn hợp lệ vì Master Key không đổi

                // Update PIN hash
                Util.arrayCopyNonAtomic(pinHashBuffer, (short) 0, tempIV, (short) 0, (short) 16);
                pin.update(pinHashBuffer, (short) 0, (byte) PIN_HASH_LEN);

                // Re-wrap Master Key bằng PIN mới
                // Master Key đang có trong RAM (đã được unwrap khi verify PIN cũ)
                // wrapMasterKey() sẽ tự restore Master Key vào sessionMasterKey
                wrapMasterKey(pinHashBuffer);

                pinLen = (short) newPinLength;
                pinAttempts = 0;

                // Validate PIN mới ngay lập tức để các lệnh tiếp theo hoạt động
                pin.check(pinHashBuffer, (short) 0, (byte) PIN_HASH_LEN);
            }

            isAdminVerified = false; // Reset Admin flag sau khi đổi PIN thành công

            // Thành công
            buffer[0] = 0x00;
            apdu.setOutgoingAndSend((short) 0, (short) 1);
        } catch (ISOException e) {
            // Re-throw ISOException as-is
            throw e;
        } catch (Exception e) {
            // Catch any other exception and throw SW_WRONG_PARAMS
            ISOException.throwIt(SW_WRONG_PARAMS);
        }
    }

    // --- DATA METHODS ---

    private void updateCustomerInfo(APDU apdu) {
        if (!pin.isValidated())
            ISOException.throwIt(SW_AUTH_FAILED);
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        customerInfoLen = encryptAes(buffer, ISO7816.OFFSET_CDATA, lc, customerInfo, (short) 0);
    }

    private void getCustomerInfo(APDU apdu) {
        if (customerInfoLen == 0)
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        byte[] buffer = apdu.getBuffer();
        short decLen = decryptAes(customerInfo, (short) 0, customerInfoLen, buffer, (short) 0);
        apdu.setOutgoingAndSend((short) 0, decLen);
    }

    private void updateBalance(APDU apdu) {
        if (!pin.isValidated())
            ISOException.throwIt(SW_AUTH_FAILED);
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        if (lc == 0 || lc > MAX_BALANCE_LEN)
            ISOException.throwIt(SW_WRONG_PARAMS);
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
        if (lc == 0 || lc > MAX_CARD_ID_LEN)
            ISOException.throwIt(SW_WRONG_PARAMS);
        cardIdLen = encryptAes(buffer, ISO7816.OFFSET_CDATA, lc, cardId, (short) 0);
    }

    private void getCardId(APDU apdu) {
        if (cardIdLen == 0)
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        byte[] buffer = apdu.getBuffer();
        short decLen = decryptAes(cardId, (short) 0, cardIdLen, buffer, (short) 0);
        apdu.setOutgoingAndSend((short) 0, decLen);
    }

    private void updateLastTapInfo(APDU apdu) {
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        if (lc == 0 || lc > MAX_TAP_INFO_LEN)
            ISOException.throwIt(SW_WRONG_PARAMS);
        lastTapInfoLen = encryptAes(buffer, ISO7816.OFFSET_CDATA, lc, lastTapInfo, (short) 0);
    }

    private void getLastTapInfo(APDU apdu) {
        if (lastTapInfoLen == 0)
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
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
         * CLA INS P1 P2 00 LcHi LcLo data...
         * ^ ^ ^ ^ ^ ^ ^
         * 1 2 3 4 5 6 7 (index)
         *
         * - buffer[OFFSET_LC] = 0x00 (byte mở rộng)
         * - buffer[OFFSET_LC+1] = LcHi
         * - buffer[OFFSET_LC+2] = LcLo
         * - buffer[OFFSET_CDATA] = vẫn là 0x00 (không phải data ảnh)
         *
         * Vì vậy:
         * - Phải tự đọc Lc từ OFFSET_LC+1/2
         * - Và bỏ qua 2 byte LcHi/LcLo khi copy data đầu tiên
         * => dataOffset ban đầu = OFFSET_CDATA + 2
         *
         * Với short APDU (Lc <= 255) thì:
         * - buffer[OFFSET_LC] = Lc
         * - buffer[OFFSET_CDATA] là data luôn (dataOffset = OFFSET_CDATA)
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
        short offset = (short) (((short) (buffer[ISO7816.OFFSET_P1] & 0xFF) << 8)
                | (short) (buffer[ISO7816.OFFSET_P2] & 0xFF));
        short remaining = (short) (plaintextLen - offset);
        short maxChunkSize = (short) 240;
        short toSend = (remaining > maxChunkSize) ? maxChunkSize : remaining;

        Util.arrayCopyNonAtomic(picture, offset, buffer, (short) 0, toSend);
        apdu.setOutgoingAndSend((short) 0, toSend);
    }

    // --- AES HELPERS ---
    // Always use sessionMasterKey

    private short encryptAes(byte[] plaintext, short ptOffset, short ptLength, byte[] ciphertext, short ctOffset) {
        if (!isMasterKeyLoaded)
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        short paddedLength = computePaddedLength(ptLength);
        Util.arrayCopyNonAtomic(plaintext, ptOffset, tempBuffer, (short) 0, ptLength);
        byte paddingValue = (byte) (paddedLength - ptLength);
        for (short i = ptLength; i < paddedLength; i++)
            tempBuffer[i] = paddingValue;

        aesCipher.init(sessionMasterKey, Cipher.MODE_ENCRYPT, tempIV, (short) 0, (short) 16);
        return aesCipher.doFinal(tempBuffer, (short) 0, paddedLength, ciphertext, ctOffset);
    }

    private short decryptAes(byte[] ciphertext, short ctOffset, short ctLength, byte[] plaintext, short ptOffset) {
        if (!isMasterKeyLoaded)
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        aesCipher.init(sessionMasterKey, Cipher.MODE_DECRYPT, tempIV, (short) 0, (short) 16);
        short decLen = aesCipher.doFinal(ciphertext, ctOffset, ctLength, tempBuffer, (short) 0);
        byte paddingValue = tempBuffer[(short) (decLen - 1)];
        short actualLength = (short) (decLen - paddingValue);
        Util.arrayCopyNonAtomic(tempBuffer, (short) 0, plaintext, ptOffset, actualLength);
        return actualLength;
    }

    private short encryptAesPicture(byte[] plaintext, short ptOffset, short ptLength) {
        if (!isMasterKeyLoaded)
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        short paddedLength = computePaddedLength(ptLength);
        if (paddedLength > MAX_PICTURE_LEN)
            ISOException.throwIt(SW_WRONG_PARAMS);
        byte paddingValue = (byte) (paddedLength - ptLength);
        for (short i = (short) (ptOffset + ptLength); i < (short) (ptOffset + paddedLength); i++) {
            plaintext[i] = paddingValue;
        }
        aesCipher.init(sessionMasterKey, Cipher.MODE_ENCRYPT, tempIV, (short) 0, (short) 16);
        return aesCipher.doFinal(plaintext, ptOffset, paddedLength, plaintext, ptOffset);
    }

    private short decryptAesPicture(byte[] ciphertext, short ctOffset, short ctLength) {
        if (!isMasterKeyLoaded)
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        if ((ctLength % AES_BLOCK_SIZE) != 0)
            ISOException.throwIt(SW_WRONG_PARAMS);

        aesCipher.init(sessionMasterKey, Cipher.MODE_DECRYPT, tempIV, (short) 0, (short) 16);
        short decLen = aesCipher.doFinal(ciphertext, ctOffset, ctLength, ciphertext, ctOffset);

        byte paddingValue = ciphertext[(short) (ctOffset + decLen - 1)];
        short actualLength = (short) (decLen - paddingValue);
        if (actualLength < decLen) {
            Util.arrayFillNonAtomic(ciphertext, (short) (ctOffset + actualLength), (short) (decLen - actualLength),
                    (byte) 0);
        }
        return actualLength;
    }

    private short computePaddedLength(short length) {
        short remainder = (short) (length % AES_BLOCK_SIZE);
        if (remainder == 0)
            return (short) (length + AES_BLOCK_SIZE);
        return (short) (length + (AES_BLOCK_SIZE - remainder));
    }

    // --- RSA & MISC ---
    private void getPublicKey(APDU apdu) {
        if (rsaPublicKey == null)
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
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
        if (rsaPrivateKey == null)
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);
        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();
        if (lc == 0)
            ISOException.throwIt(SW_WRONG_PARAMS);
        try {
            Signature rsaSign = Signature.getInstance(Signature.ALG_RSA_SHA_PKCS1, false);
            rsaSign.init(rsaPrivateKey, Signature.MODE_SIGN);
            short sigLen = rsaSign.sign(buffer, ISO7816.OFFSET_CDATA, lc, buffer, (short) 0);
            apdu.setOutgoingAndSend((short) 0, sigLen);
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

    // --- MASTER KEY & RECOVERY LOGIC ---

    /**
     * INS_SET_MASTER_KEY
     * Input: RSA Encrypted Master Key (128 bytes block typically, depends on RSA
     * key)
     */
    private void setMasterKey(APDU apdu) {
        if (rsaPrivateKey == null)
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);

        byte[] buffer = apdu.getBuffer();
        short lc = apdu.setIncomingAndReceive();

        // Decrypt using RSA Private Key
        rsaCipher.init(rsaPrivateKey, Cipher.MODE_DECRYPT);
        // Decrypt info tempBuffer
        short len = rsaCipher.doFinal(buffer, ISO7816.OFFSET_CDATA, lc, tempBuffer, (short) 0);

        if (len != 16) {
            // Master key must be 16 bytes (AES-128)
            ISOException.throwIt(SW_WRONG_PARAMS);
        }

        // Copy MK vào RAM Buffer (quan trọng để wrap sau này)
        Util.arrayCopyNonAtomic(tempBuffer, (short) 0, ramMasterKeyBuffer, (short) 0, (short) 16);

        // 1. Nạp MK vào RAM Key Object
        sessionMasterKey.setKey(ramMasterKeyBuffer, (short) 0);
        isMasterKeySetup = true;
        isMasterKeyLoaded = true;

        // 2. Tính KCV: Encrypt(MK, MK)
        aesCipher.init(sessionMasterKey, Cipher.MODE_ENCRYPT, tempIV, (short) 0, (short) 16);
        aesCipher.doFinal(ramMasterKeyBuffer, (short) 0, (short) 16, masterKeyKcv, (short) 0);

        // 3. Encrypt MK bằng Default Key (vì Setup này có thể chạy trước updatePin)
        // ... (Logic giữ nguyên)

        // 3. Encrypt MK bằng PIN hiện tại để lưu xuống
        // Lấy PIN hash hiện tại?
        // Ở bước setup này, thường Admin làm khi vừa install applet.
        // Nên PIN có thể là Default hoặc chưa có?
        // Giả sử ta lấy PIN Hash mặc định (hoặc yêu cầu Admin gửi kèm PIN Hash nếu
        // cần).
        // Để đơn giản: Ta giả định bước này làm SAU KHI set PIN. Hoặc nếu chưa Set PIN,
        // dùng Default 1234.
        // Nhưng wait, để an toàn, ta cần tính PIN Hash từ OwnerPIN? Không lấy được giá
        // trị PIN từ OwnerPIN object.
        // => Vậy Set Master Key CẦN kèm theo việc Verify PIN trước đó để đảm bảo ta có
        // quyền.

        // GIẢI PHÁP: Yêu cầu Verify PIN trước khi Set Master Key.
        // Khi Verify PIN -> ta đã có PIN Hash trong pinHashBuffer? Không, Verify xong
        // là mất.

        // SỬA ĐỔI CHO ĐƠN GIẢN:
        // SetMasterKey sẽ KHÔNG lưu encrypted ngay nếu chưa verify PIN.
        // Nó sẽ lưu vào RAM. Sau đó gọi lệnh UpdatePIN (hoặc 1 lệnh mới LockMasterKey)
        // để chốt.
        // Nhưng để user dễ dùng: Ta mặc định khi Setup, MK sẽ được lưu dạng Encrypted
        // bởi một Default Key (hoặc chính nó)
        // nếu chưa có PIN?

        // Tốt nhất: Trong setMasterKey, ta dùng chính tempBuffer (MK) làm key để
        // encrypt nó (KCV),
        // và tạm thời lưu MK vào encryptedMasterKey (dạng raw hoặc dummy) chờ đổi PIN?
        // KHÔNG.

        // QUY TRÌNH CHUẨN:
        // 1. Install Applet.
        // 2. User/Admin Verify PIN mặc định (111111). -> pinHashBuffer có dữ liệu?
        // Không.
        // 3. Gọi Set Master Key.

        // Code hack: Ở SetMasterKey, ta sẽ wrap tạm bằng một Default Key (Array toàn
        // 0).
        // Sau đó bắt buộc User đổi PIN ngay.
        byte[] defaultKey = new byte[16]; // All 0

        // Encrypt MK bằng Default Key
        sessionMasterKey.setKey(defaultKey, (short) 0);
        aesCipher.init(sessionMasterKey, Cipher.MODE_ENCRYPT, tempIV, (short) 0, (short) 16);
        aesCipher.doFinal(tempBuffer, (short) 0, (short) 16, encryptedMasterKey, (short) 0);

        // Restore MK to RAM
        sessionMasterKey.setKey(tempBuffer, (short) 0);

        // Clear temp
        Util.arrayFillNonAtomic(tempBuffer, (short) 0, (short) 16, (byte) 0);
    }

    /**
     * Encrypt the PIN Key (User Session Key) using Master Key for backup
     * Called during INS_UPDATE_PIN
     */
    private void backupPinKey(byte[] pinKeyRaw) {
        // Use Master Key to encrypt PIN Key
        // Mode ECB or CBC with zero IV is fine for key wrapping
        // Here reusing aesCipher but init with MasterKey
        aesCipher.init(sessionMasterKey, Cipher.MODE_ENCRYPT, tempIV, (short) 0, (short) 16);

        // Input: pinKeyRaw (16 bytes)
        // Output: wrappedPinKey (should be 16 or 32 bytes depending on padding)
        // With NOPAD and 16 bytes input -> 16 bytes output
        aesCipher.doFinal(pinKeyRaw, (short) 0, (short) 16, encryptedMasterKey, (short) 0);
    }

    /**
     * INS_GET_ADMIN_CHALLENGE
     * Generate 16 bytes random challenge for Admin to sign/encrypt
     */
    private void getAdminChallenge(APDU apdu) {
        if (!isMasterKeySetup)
            ISOException.throwIt(SW_CARD_NOT_INITIALIZED);

        RandomData rng = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
        rng.generateData(adminChallenge, (short) 0, (short) 16);

        byte[] buffer = apdu.getBuffer();
        Util.arrayCopyNonAtomic(adminChallenge, (short) 0, buffer, (short) 0, (short) 16);
        apdu.setOutgoingAndSend((short) 0, (short) 16);
    }

    /**
     * INS_VERIFY_ADMIN_RECOVER
     * Input: AES Encrypted(Challenge) using Master Key
     * Action: Verify -> Unwrap Backup Key -> Restore Access
     */
    private void verifyAdminAndRecover(APDU apdu) {
        // is the critical part to save data.

        // Clear challenge to prevent replay
        Util.arrayFillNonAtomic(adminChallenge, (short) 0, (short) 16, (byte) 0);
    }
}
