# 🚌 Bus Smart Card Management System

Nền tảng quản lý vé xe buýt thông minh kết hợp **Java Card Applet** (lưu trữ dữ liệu bảo mật trên thẻ) và **Kotlin Compose Desktop** (ứng dụng điều hành tại bến). Giải pháp cho phép đăng ký khách hàng, nạp/gia hạn vé, trừ tiền khi quẹt thẻ và đồng bộ giao dịch với cơ sở dữ liệu cục bộ.

---

## Tổng Quan Kiến Trúc

### 🏗️ Kiến Trúc MVVM
Ứng dụng sử dụng **MVVM (Model-View-ViewModel)** pattern:
- **Model:** `core/model/` - Customer, Transaction, Trip, CardType, CustomerType
- **View:** `feature/*/` - Các Composable screens và dialogs
- **ViewModel:** `feature/*/` - Quản lý state và business logic
- **State:** `feature/*/` - Data classes định nghĩa UI state

### 💾 Smart Card Layer
- **BusCardApplet** (Java Card 3.0.4): 
  - Lưu trữ thông tin thẻ trên EEPROM
  - Mã hóa AES-128-CBC toàn bộ dữ liệu
  - Xử lý PIN verification và counter
- **BusCardManager** (Kotlin): 
  - Sử dụng PC/SC để giao tiếp với thẻ
  - Gửi/nhận APDU commands
  - Quản lý kết nối và đọc/ghi dữ liệu

### 🔒 Security
- **SecurityUtils:** Xử lý mã hóa phía desktop, khởi tạo key
- **AES-128-CBC:** Mã hóa dữ liệu trên thẻ
- **PIN Protection:** Bộ đếm sai PIN, khóa thẻ sau 4 lần sai

## Thiết Lập & Chạy Ứng Dụng
1**Copy file** BusCardApplet **vào jcide vào chạy cổng** 
2**Chạy Desktop App:**
   ```powershell
   .\gradlew.bat run
   ```
   Ứng dụng sẽ mở cửa sổ Compose Desktop toàn màn hình, tự động load dữ liệu và kết nối reader nếu có.

---

## Các hàm chính
### Connect()

```
Khi user bấm click Kết nối Java Card -> gọi onConnect(trong ConnectStep) -> loadCardInfoViewModel.connect() 
                              -> BusCardManager.connect() -> connectCard (BusSmartCard.java)
                        
Khi user bấm đọc thẻ -> gọi readSmartCardViewModel.onDialogOpened (trong ReadSmartCardDialog) -> ReadSmartCardViewModel.autoReadCardInternal()
                     -> BusSmartCard.getCustomerInfo(),...
Buffer APDU là bộ nhớ tạm (RAM) để lưu dữ liệu tạm thời dưới dạng byte array
ISO7816.OFFSET_CDATA là một hằng số định nghĩa vị trí (offset) trong buffer APDU
Quy trình ghi ảnh:
- Client gửi 1 APDU command duy nhất
- Applet sẽ nhận APDU và Đọc dữ liệu theo chunk, Kiểm tra tính toàn vẹn xem dữ liệu bị đọc thiếu không,
  Mã hóa → ciphertext, cuối cùng lưu vào EPROM
Quy trình đọc ảnh: 
- Client gửi nhiều APDU commands, mỗi command yêu cầu một chunk bắt đầu từ offset cụ thể (tính P1,P2 từ offset)
- Applet sẽ nhận APDU và giải mã AES (chỉ lần đầu tiên), tính offset từ P1, P2, . Sau đó Copy picture[offset..offset+toSend-1] để gửi chunk về client
- Client sẽ lưu chunk vào list sau đó qua nhiều lần sẽ ghép các chunk lại và tạo lại ảnh từ bytes
Lưu SQL:
- AES-256/GCM cho dữ liệu khách hàng, RSA-2048 cho giao dịch
- Dùng khóa từ PIN để khởi tạo cipher AES-256/GCM
- Tạo khóa từ PIN
  PIN "1234" + Salt + PBKDF2 (65536 lần) 
  → Khóa AES 256-bit: [a1b2c3d4...]
- Mã hóa dữ liệu
```

## Luồng Trao Đổi Dữ Liệu
### Ghi dữ liệu xuống thẻ
```
Compose Desktop -> BusCardManager -> APDU
  ↓                             (dữ liệu plaintext theo từng chunk)
BusCardApplet -> gom chunk -> AES-128-CBC -> lưu EEPROM (customerInfo, balance, pin, ảnh, lịch sử quẹt)
```

### Đọc dữ liệu từ thẻ
```
Desktop gửi APDU READ
  ↓
Applet đọc EEPROM (đang mã hóa) -> giải mã AES -> trả về plaintext chunk -> Desktop ghép lại hiển thị
```

### Xử lý PIN
```
Nhập PIN trên Desktop -> gửi xuống thẻ -> Applet giải mã PIN lưu trữ, so sánh byte-by-byte
  - Đúng: reset counter, cho phép thao tác
  - Sai: tăng counter, >=4 lần sẽ set cờ khóa thẻ
```

Chi tiết triển khai có trong `BusCardApplet.java` và `smartcard/BusCardManager.kt`.

---

## Bảo Mật
- **Thuật toán:** AES-128-CBC, padding PKCS#7.
- **Key/IV:** sinh ngẫu nhiên 16 byte/thẻ, lưu EEPROM (không gửi ra khỏi thẻ).
- **Dữ liệu mã hóa:** thông tin khách, số dư, card ID, ảnh bytes, lịch sử quẹt, PIN, thông tin lần quẹt cuối.
- **Desktop SecurityUtils:** tạo khóa phiên khi đồng bộ, bảo vệ dữ liệu tạm trong RAM.
- **Counter PIN:** lưu trên thẻ, đảm bảo không thể brute-force từ desktop.
---

## Lược Đồ CSDL
- **customers:** thông tin cá nhân, loại vé, card_id, PIN mã hóa, ảnh (BLOB), số dư, ngày hết hạn.
- **transactions:** lịch sử nạp tiền, gia hạn, quẹt, gồm số dư trước/sau và mô tả giao dịch.
- **route_history:** lưu hành trình, tuyến, điểm lên/xuống để hỗ trợ thống kê và xác thực vé.

File schema mẫu: `db/schema_sqlite.sql`.

---

## License

MIT License © 2025 – được phép sử dụng cho mục đích học tập và triển khai thực tế.

---

**Made with ❤️ Kotlin + Java Card**
