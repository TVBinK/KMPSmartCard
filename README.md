# 🚌 Hệ Thống Quản Lý Thẻ Xe Bus với Smart Card

Hệ thống quản lý thẻ xe bus tích hợp **JavaCard Smart Card Simulator** với **Kotlin Compose Desktop**.

---

## 🚀 Cách Chạy Hệ Thống

---

### 🔧 Cách 2: Chạy Thủ Công (Đầy đủ với Smart Card)

**Bước 1:** Mở Terminal 1 - Khởi động Simulator
```bash
.\gradlew.bat runSimulator
```
➡️ Simulator lắng nghe trên port **9025**

**Bước 2:** Mở Terminal 2 - Khởi động UI
```bash
.\gradlew.bat run
```

**Bước 3:** Nạp thông tin vào thẻ
1. Click **"Nạp thông tin vào thẻ"**
2. Làm theo 4 bước trong wizard
3. Thẻ sẽ có dữ liệu và sẵn sàng sử dụng!

#### Cách tìm và kill
```bash
netstat -ano | findstr :9025
taskkill /F /PID 16348
```


---

### 🎨 Cách 3: Chỉ Chạy UI (Demo Mode - Không cần Smart Card)

```bash
.\gradlew.bat run
```

UI sẽ hiển thị đầy đủ các chức năng nhưng không kết nối với smart card.

---

## 📋 Các Chức Năng Chính

### 🎯 1. Quản Lý Khách Hàng
- **Danh sách khách hàng**: Hiển thị tất cả khách hàng từ database với ảnh đại diện
- **Tìm kiếm & Filter**: Tìm theo tên, Card ID, loại đối tượng
- **Thống kê**: Tổng số khách hàng với animated counter
- **Xác thực thẻ**: Click vào khách hàng để xem chi tiết và trạng thái thẻ
- **UI hiện đại**: Animations (fade in, slide in, scale, pulse, shimmer loading)

### 💳 2. Nạp Thông Tin Vào Thẻ (Smart Card)
**Wizard 4 bước:**
- **Bước 1**: Kết nối card reader và kiểm tra trạng thái thẻ
- **Bước 2**: Nhập thông tin khách hàng (Tên, loại đối tượng, Card ID)
- **Bước 3**: Thiết lập thẻ (loại thẻ, số dư, PIN, ngày hết hạn, ảnh)
- **Bước 4**: Xem lại và xác nhận ghi vào thẻ

**Tính năng:**
- Kết nối JCardSimServer (simulator thẻ thông minh)
- Ghi thông tin lên thẻ qua APDU commands
- Upload ảnh đại diện (resize 200x200, JPG 75% quality)
- Lưu vào SQLite database tự động
- Validation đầy đủ (Card ID, số dư, PIN, ngày hết hạn)

### 🎴 3. Quản Lý Smart Card
**3 tabs chính:**

**Tab "Đọc thẻ":**
- Kết nối với JCardSimServer (simulator)
- Đọc thông tin từ thẻ ảo
- Tự động tìm khách hàng trong database theo Card ID
- Hiển thị trạng thái kết nối real-time

**Tab "Thông tin":**
- Hiển thị đầy đủ thông tin khách hàng đã đọc
- Ảnh đại diện, họ tên, Card ID, loại thẻ
- Số dư, ngày hết hạn, trạng thái
- Layout card đẹp mắt với icons

**Tab "Giao dịch":**
- Lịch sử giao dịch của thẻ
- Loại giao dịch, số tiền, thời gian
- LazyColumn với infinite scroll

### 💰 4. Thanh Toán Vé
- **Chọn tuyến xe**: Dropdown danh sách các tuyến
- **Tính giá tự động**: Dựa vào loại đối tượng và loại thẻ
- **Trừ tiền**: Cập nhật số dư trong database và trên thẻ
- **Gia hạn thẻ**: Tự động gia hạn thẻ tháng/quý nếu hết hạn
- **Lưu giao dịch**: Ghi vào bảng `card_transaction`
- **Validation**: Kiểm tra số dư, ngày hết hạn, trạng thái thẻ

### 🚀 5. Chuyển Tuyến
- Danh sách các tuyến xe có sẵn
- Chọn tuyến mới để chuyển
- Cập nhật thông tin chuyến đi
- (Tính năng mở rộng trong tương lai)

### 🎭 6. Demo
- Chạy các kịch bản demo tự động
- Test các chức năng chính
- Hiển thị flow hoàn chỉnh
- (Tính năng phát triển)

---

## 💾 Database (SQLite)

### Bảng `customer`
```sql
- card_id (PRIMARY KEY): Mã thẻ
- full_name: Họ tên khách hàng
- customer_type: STUDENT, ELDERLY, NORMAL
- card_type: MONTHLY, QUARTERLY, TICKET
- balance: Số dư (VND)
- expiry_date: Ngày hết hạn
- status: ACTIVE, EXPIRED
- pin_code: Mã PIN (encrypted)
- linked_customer_id: Mã liên kết (cho sinh viên)
- photo_bytes: Ảnh đại diện (BLOB)
- created_by: Người tạo
- created_at: Thời gian tạo
```

### Bảng `card_transaction`
```sql
- id (AUTO INCREMENT): ID giao dịch
- card_id: Mã thẻ
- transaction_type: PAYMENT, TOP_UP, EXTENSION
- amount: Số tiền
- old_balance: Số dư cũ
- new_balance: Số dư mới
- route_name: Tên tuyến (nếu thanh toán)
- transaction_date: Thời gian giao dịch
- notes: Ghi chú
```

**Lưu ý**: Database được tạo tự động khi khởi động app tại `bus_card_management.db`

---

## 🎨 UI/UX Features

### Animations
- **Fade in**: Khi load danh sách khách hàng
- **Slide in**: Menu buttons và customer cards
- **Scale & Pulse**: Hover effects trên buttons
- **Rotating icon**: Header gradient animation
- **Shimmer loading**: Skeleton loading cho customer list
- **Animated counter**: Số lượng khách hàng
- **Status badge**: Pulse animation cho trạng thái hợp lệ

### Design Patterns
- **Material Design**: Icons, colors, typography
- **Card-based layout**: Clean & modern
- **Responsive**: Tự động điều chỉnh kích thước
- **Dark mode ready**: Color scheme linh hoạt
- **Professional dialogs**: Custom size & layout

### Components Tái Sử Dụng
- `CustomButton`: Button với hover effects
- `CustomCard`: Card với elevation & animation
- `StatusBadge`: Badge trạng thái với icons
- `ImagePlaceholder`: Hiển thị ảnh với fallback
- `CustomTextField`: Text field với focus animation
- `ShimmerEffect`: Loading skeleton

---

## 🔐 Smart Card (Java Card Simulator)

### Protocol
- **Port**: 9025 (TCP Socket)
- **Format**: `[length (2 bytes)][APDU data]`
- **Max APDU size**: 65535 bytes (support large images)

### APDU Commands
```
- 0xA4: SELECT APPLET
- 0x29: CHECK CARD CREATED
- 0x20: UPDATE CUSTOMER INFO (initialize)
- 0x13: GET CUSTOMER INFO
- 0x14: GET BALANCE
- 0x16: UPDATE BALANCE
- 0x27: GET CARD ID
- 0x26: UPDATE CARD ID
- 0x21: UPDATE PIN
- 0x22: UPDATE PICTURE
- 0x23: GET PICTURE
```

### Data Storage (In-Memory)
- Customer info (name, type, expiry)
- Card ID
- Balance
- PIN (encrypted)
- Picture (Base64, up to 65KB)

---

## 🛠️ Technical Stack

### Frontend
- **Kotlin 2.1.0**: Modern JVM language
- **Jetpack Compose Desktop 1.7.1**: Declarative UI framework
- **Material Icons**: Rich icon library

### Backend
- **SQLite + JDBC**: Local database persistence
- **Kotlin Coroutines**: Async/await operations
- **Java Card Simulator**: Smart card emulation

### Smart Card Layer
- **JavaCard 3.0.4**: Java Card development kit
- **Socket Protocol**: TCP/IP communication (port 9025)
- **APDU Commands**: ISO 7816-4 standard
- **Extended Length APDU**: Support up to 65KB data transfer
- **Base64 Encoding**: Image data encoding

### Build & Dependencies
- **Gradle 8.11.1**: Build automation
- **Compose Gradle Plugin**: UI compilation
- **SQLite JDBC Driver**: Database connectivity

---

## 🔄 Luồng Hoạt Động

### **Ban đầu: Thẻ Rỗng**
Khi khởi động hệ thống, thẻ Smart Card chưa có dữ liệu khách hàng.

### **Bước 1: Nạp Thông Tin Vào Thẻ** 
1. Click **"Nạp thông tin vào thẻ"** trong menu chính
2. **Kết nối với thẻ** - Kết nối đến Card Simulator (localhost:9025)
3. **Kiểm tra trạng thái thẻ** - Xác nhận thẻ đang rỗng (nếu thẻ có dữ liệu cũ, có thể xóa)
4. **Nhập thông tin khách hàng:**
   
   **💡 2 cách nhập:**
   - **Chọn từ database** (nếu đã có khách hàng): Bật switch, chọn khách hàng → thông tin tự động điền
   - **Nhập mới**: Điền thủ công các trường
   
   **Thông tin cần thiết:**
   - Mã thẻ (Card ID) * - Luôn phải nhập mới
   - Họ tên *
   - Loại đối tượng (HSSV, Người cao tuổi, Thông thường)
   - Loại thẻ (Vé Lượt, Vé Tháng)
   - Số dư ban đầu
   - Mã PIN (4-6 số) * - Luôn phải nhập mới
   - Mã liên kết (tùy chọn)
5. **Ghi dữ liệu lên thẻ** - Lưu thông tin vào Smart Card + Database

### **Bước 2: Sử Dụng Các Tính Năng Khác**
Sau khi nạp thông tin thành công, thẻ đã có dữ liệu và có thể sử dụng:
- ✅ Thanh toán - Tính cước
- ✅ Quản lý Smart Card (đọc thông tin, quản lý số dư)
- ✅ Xác thực thẻ
- ✅ Chuyển tuyến
- ✅ Demo: Quẹt thẻ - Trừ tiền tự động

---

## 🔧 Troubleshooting

### ❌ Lỗi: Cannot connect to simulator
```bash
# Kiểm tra simulator đang chạy
netstat -an | findstr 9025

# Nếu không thấy, khởi động lại
.\gradlew.bat runSimulator
```

### ❌ Lỗi: Address already in use (Port 9025 bị chiếm)
```bash
# Tìm process đang dùng port 9025
netstat -ano | findstr :9025

# Kill process (thay PID bằng số thực tế)
taskkill /F /PID <PID>

# Chạy lại simulator
.\gradlew.bat runSimulator
```

### ❌ Lỗi: Thẻ chưa khởi tạo (SW: 6A88)
Trong UI, cập nhật thông tin khách hàng để khởi tạo dữ liệu.

### ❌ Lỗi: Thẻ bị khóa (SW: 6983)
Khởi động lại simulator: `Ctrl+C` rồi chạy lại `.\gradlew.bat runSimulator`

### 🧹 Clean Build
```bash
.\gradlew.bat clean build
```

---

## 💻 Yêu Cầu Hệ Thống

- **JDK:** 21 hoặc cao hơn
- **OS:** Windows (Linux/Mac cần dùng `./gradlew` thay vì `gradlew.bat`)
- **Port:** 9025 (phải trống cho simulator)

Kiểm tra Java version:
```bash
java -version
```

---

## 📂 Cấu Trúc Dự Án

```
KmpUiSmartCard/
├── src/main/
│   ├── java/com/buscardmanagement/
│   │   ├── client/              # Java client (giao tiếp card)
│   │   └── simulator/           # Card simulator
│   └── kotlin/
│       ├── Main.kt              # Entry point
│       ├── MainApp.kt           # Main UI
│       ├── database/            # SQLite database manager
│       ├── smartcard/           # Kotlin wrapper
│       ├── models/              # Data models
│       └── ui/screens/          # Màn hình chức năng
├── db/
│   ├── schema.sql               # PostgreSQL schema
│   └── schema_sqlite.sql        # SQLite schema
├── build.gradle.kts             # Build config
├── start-system.bat             # Script khởi động
├── BusCardApplet.cap            # JavaCard applet
└── bus_card_management.db       # SQLite database (auto-created)
```

## ✅ Tính Năng Đã Hoàn Thành

- ✅ **Quản lý khách hàng**: CRUD operations, search, filter, statistics
- ✅ **Nạp thông tin vào thẻ**: 4-step wizard, image upload, validation
- ✅ **Quản lý Smart Card**: Đọc thẻ, hiển thị thông tin, lịch sử giao dịch
- ✅ **Thanh Toán vé**: Auto pricing, balance deduction, extension
- ✅ **Database persistence**: SQLite with UPSERT logic
- ✅ **Smart Card Simulator**: JCardSimServer with APDU protocol
- ✅ **UI Animations**: Fade, slide, scale, pulse, shimmer, counter
- ✅ **Image handling**: Resize 200x200, JPG 75%, Base64 encoding
- ✅ **Extended APDU**: Support up to 65KB data transfer
- ✅ **Responsive UI**: Modern Material Design with hover effects
- ✅ **Error handling**: Comprehensive validation and user feedback

## 🚧 Tính Năng Đang Phát Triển

- 🚧 **Chuyển tuyến**: Route transfer logic
- 🚧 **Demo mode**: Automated testing scenarios
- 🚧 **Reports**: Transaction analytics & statistics
- 🚧 **Backup/Restore**: Database backup utilities
- 🚧 **Multi-language**: i18n support (EN/VI)

## 💡 Đồng Bộ Dữ Liệu

Dữ liệu được lưu **đồng thời** trên:
1. ✅ **Smart Card** (simulator) - In-memory storage
2. ✅ **SQLite Database** (`bus_card_management.db`) - Persistent storage

**Lợi ích:**
- 🔄 Dữ liệu không bị mất khi restart app
- 📊 Truy vấn lịch sử giao dịch đầy đủ
- 👥 Quản lý nhiều khách hàng dễ dàng
- 🔍 Tìm kiếm và filter nhanh chóng

---

## 🐛 Troubleshooting

### Port 9025 đã được sử dụng
```bash
# Windows: Tìm và kill process
netstat -ano | findstr :9025
taskkill /F /PID <PID>
```

### Database schema error (missing column)
- **Giải pháp**: Xóa file `bus_card_management.db` và restart app
- Database mới sẽ được tạo với schema đúng

### Không đọc được thông tin từ thẻ
1. Kiểm tra JCardSimServer đã chạy (`.\gradlew.bat runSimulator`)
2. Kiểm tra port 9025 có available
3. Thẻ phải được nạp thông tin trước (Bước "Nạp thông tin vào thẻ")

### Ảnh không hiển thị
- Ảnh phải có định dạng: JPG, PNG, JPEG
- Kích thước tối đa: ~65KB (sau khi resize 200x200)
- Database cũ có thể thiếu column `photo_bytes` → xóa `.db` file

### Build error
```bash
# Clean và rebuild
.\gradlew.bat clean build
```

### Lưu ý quan trọng
- ⚠️ **Luôn khởi động JCardSimServer trước** khi dùng tính năng Smart Card
- ⚠️ **Thẻ phải được nạp thông tin** trước khi thanh toán/đọc thông tin
- ⚠️ Database file (`bus_card_management.db`) lưu tại thư mục gốc project

---

## 📞 Support & Contact

**Version:** 1.0.0  
**Port:** 9025 (JCardSimServer)  
**AID:** `11:22:33:44:55:00:01`  
**Database:** SQLite (`bus_card_management.db`)

## 📚 Resources

- [Jetpack Compose Desktop](https://www.jetbrains.com/lp/compose-desktop/)
- [JavaCard Development](https://docs.oracle.com/javacard/)
- [SQLite Documentation](https://www.sqlite.org/docs.html)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

---

**Happy Coding! 🚀 Chúc bạn phát triển thành công!**
