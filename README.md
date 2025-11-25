# 🚌 Bus Smart Card Management System

Nền tảng quản lý vé xe buýt thông minh kết hợp **Java Card Applet** (lưu trữ dữ liệu bảo mật trên thẻ) và **Kotlin Compose Desktop** (ứng dụng điều hành tại bến). Giải pháp cho phép đăng ký khách hàng, nạp/gia hạn vé, trừ tiền khi quẹt thẻ và đồng bộ giao dịch với cơ sở dữ liệu cục bộ.

---

## Tổng Quan Kiến Trúc
- **Desktop Client (Kotlin + Compose Desktop):** giao diện điều hành, quản lý khách hàng, xử lý giao dịch, trực quan dữ liệu.
- **Smart Card Layer:**
  - `BusCardApplet` (Java Card 3.0.4) lưu thông tin thẻ, mã hóa AES-128-CBC toàn bộ dữ liệu EEPROM.
  - `BusSmartCard` client sử dụng PC/SC để gửi APDU đến thẻ.
- **Persistence:** SQLite (`bus_card_management.db`) truy cập thông qua `DatabaseManager`.
- **Security Utilities:** `security/SecurityUtils.kt` xử lý mã hóa phía desktop, khởi tạo key, kiểm tra toàn vẹn dữ liệu.

---

## Tính Năng Chính
- **Quản lý khách hàng:** tạo mới, cập nhật, xem lịch sử hành trình, nhập ảnh chân dung, phân loại (ưu tiên/thường).
- **Quản lý thẻ thông minh:** khởi tạo card ID, lưu thông tin cá nhân, đổi PIN, khóa/mở khóa thẻ, kiểm tra số dư trực tiếp trên thẻ.
- **Giao dịch & vé:**
  - Nạp tiền nhanh (50k/100k/200k/500k) hoặc nhập tùy ý.
  - Gia hạn vé tháng, tự động chuyển về vé thường nếu hết hạn.
  - Tính toán trừ tiền mỗi lần quẹt (7.000đ) với thẻ thường; miễn phí với thẻ tháng còn hạn.
- **Giám sát thời gian thực:** màn hình quẹt thẻ live, thống kê số lượng khách, biểu đồ mini, danh sách hoạt động gần nhất.
- **Lộ trình & kiểm tra vé:** hiển thị tuyến, chuyển tuyến, xác thực vé nhanh cho nhân viên soát vé.
- **Bảo mật:** PIN mã hóa AES, bộ đếm sai PIN, khóa thẻ sau 4 lần nhập sai, chỉ admin mới mở khóa.

---

## Thiết Lập & Chạy Ứng Dụng
1. **Clone dự án & cài dependency:**
   ```powershell
   git clone <repo>
   cd KmpUiSmartCard
   ```
2. **Copy file BusCardApplet vào jcide vào chạy cổng** 
3. **Chạy Desktop App:**
   ```powershell
   .\gradlew.bat run
   ```
   Ứng dụng sẽ mở cửa sổ Compose Desktop toàn màn hình, tự động load dữ liệu và kết nối reader nếu có.

---

## Build & Nạp Java Card Applet
1. Mở `src/main/java/com/buscardmanagement/applet/BusCardApplet.java` bằng JCIDE (hoặc IDE tương tự).
2. Cập nhật `AID`, key và tham số nếu cần đúng với card thực tế.
3. Dùng `build-applet.bat` (tuỳ chỉnh script) hoặc công cụ của JCIDE để compile và sinh CAP.
4. Nạp CAP lên thẻ:
   - Kết nối đầu đọc PC/SC.
   - Sử dụng JCIDE, GlobalPlatformPro hoặc tool của nhà cung cấp để install CAP và thiết lập `AES key`, `IV`, `PIN` mặc định.
5. Sau khi nạp thành công, dùng màn hình "Nạp thông tin vào thẻ" trong app desktop để khởi tạo dữ liệu khách hàng đầu tiên.

> Lưu ý: nếu đang sử dụng thẻ giả lập (JCIDE simulator), cần chạy simulator trước khi mở ứng dụng desktop để client có thể kết nối qua PC/SC.

---

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

## Cấu Trúc Thư Mục
```
src/
├─ main/
│  ├─ kotlin/
│  │  ├─ Main.kt, MainApp.kt
│  │  ├─ database/           # DatabaseManager + DAO SQLite
│  │  ├─ smartcard/          # BusCardManager: PC/SC + APDU helpers
│  │  ├─ ui/                 # Compose components, screens, dialogs
│  │  ├─ models/             # Customer, Transaction, Trip, CardType...
│  │  └─ security/, utils/   # Helper & constants
│  └─ java/com/buscardmanagement/
│     ├─ applet/             # BusCardApplet + biến thể clean/fixed
│     └─ client/             # BusSmartCard.java (thao tác APDU native)
└─ resources/icons/          # Icon sử dụng trong UI
```

Các script bổ trợ:
- `build-applet.bat`: build CAP nhanh.
- `start-system.bat`: khởi động đồng thời mô phỏng smart card + desktop (tuỳ chỉnh).

---

## Lược Đồ CSDL
- **customers:** thông tin cá nhân, loại vé, card_id, PIN mã hóa, ảnh (BLOB), số dư, ngày hết hạn.
- **transactions:** lịch sử nạp tiền, gia hạn, quẹt, gồm số dư trước/sau và mô tả giao dịch.
- **route_history:** lưu hành trình, tuyến, điểm lên/xuống để hỗ trợ thống kê và xác thực vé.

File schema mẫu: `db/schema_sqlite.sql`.

---

## Quy Tắc Vé
- **Vé thường (NORMAL):** trừ 7.000đ/lượt, yêu cầu số dư >= 7.000đ trước khi quẹt.
- **Vé tháng (MONTHLY):** phí 100.000đ/tháng, miễn phí khi quẹt trong thời gian còn hạn, tự động chuyển về vé thường khi hết hạn.
- **Gia hạn:** hệ thống trừ trước số dư cần thiết, cập nhật expiry date và đồng bộ lại lên thẻ.

---

## Ghi Chú Phát Triển
- Compose Desktop đã bật animation và hiệu ứng glassmorphism, máy cấu hình thấp có thể tắt bớt ở `ui/DesignSystem.kt`.
- `BusCardApplet_fixed.java` và `BusCardApplet_clean.java` dùng làm tài liệu so sánh/debug nếu cần refactor.
- Có thể thay thế SQLite bằng server từ xa bằng cách hiện thực lại `DatabaseManager`.

---

## License

MIT License © 2025 – được phép sử dụng cho mục đích học tập và triển khai thực tế.

---

**Made with ❤️ Kotlin + Java Card**
