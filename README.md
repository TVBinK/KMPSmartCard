# 🚌 Hệ Thống Quản Lý Thẻ Xe Buýt Smart Card

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-blue.svg)](https://kotlinlang.org/)
[![Compose Desktop](https://img.shields.io/badge/Compose%20Desktop-1.6.0-orange.svg)](https://www.jetbrains.com/lp/compose-desktop/)
[![Java](https://img.shields.io/badge/Java-21-red.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

Hệ thống quản lý thẻ xe buýt thông minh cho thành phố Hà Nội, được xây dựng với **Java Card Applet** và **Kotlin Compose Desktop**. Hệ thống sử dụng Java Card applet chạy trên JCIDE Simulator hoặc Java Card thật qua PC/SC reader, mô phỏng đầy đủ quy trình quản lý thẻ xe buýt từ nạp thông tin, nạp tiền, gia hạn vé đến quẹt thẻ tự động trên xe.

---

## 📑 Mục Lục

- [Tính Năng](#-tính-năng)
- [Yêu Cầu Hệ Thống](#-yêu-cầu-hệ-thống)
- [Cài Đặt](#-cài-đặt)
- [Hướng Dẫn Sử Dụng](#-hướng-dẫn-sử-dụng)
- [Kiến Trúc Hệ Thống](#-kiến-trúc-hệ-thống)
- [Cấu Trúc Dự Án](#-cấu-trúc-dự-án)
- [Công Nghệ Sử Dụng](#-công-nghệ-sử-dụng)
- [Database Schema](#-database-schema)
- [Xử Lý Lỗi](#-xử-lý-lỗi)
- [Phát Triển](#-phát-triển)
- [License](#-license)

---

## ✨ Tính Năng

### 🎫 Quản Lý Khách Hàng
- ✅ Danh sách khách hàng với avatar và thông tin chi tiết
- ✅ Tìm kiếm theo tên, Card ID, CCCD
- ✅ Thêm, sửa, xóa khách hàng
- ✅ Thống kê tổng quan (tổng số khách hàng, tổng số dư)
- ✅ Xem lịch sử giao dịch của từng khách hàng

### 💳 Nạp Thông Tin Vào Thẻ (Wizard 4 Bước)
1. **Kết nối Java Card** - Kết nối với JCIDE Simulator hoặc Java Card thật qua PC/SC reader
2. **Kiểm tra thẻ** - Xác định thẻ rỗng/có dữ liệu, xóa dữ liệu cũ nếu cần
3. **Nhập thông tin** - Họ tên, CCCD, ngày sinh, địa chỉ, số điện thoại, loại thẻ, số dư, PIN, ảnh đại diện
4. **Ghi vào thẻ** - Lưu thông tin vào Smart Card và Database đồng bộ

**Tính năng đặc biệt:**
- ✅ Tự động resize và nén ảnh (< 20KB)
- ✅ Xóa dữ liệu thẻ cũ trước khi ghi mới
- ✅ Validation đầy đủ cho tất cả các trường
- ✅ Mã hóa PIN trước khi lưu

### 💰 Nạp Tiền & Gia Hạn
**Tab Nạp Tiền:**
- Nhập số tiền tùy ý hoặc sử dụng nút nạp nhanh (50K, 100K, 200K, 500K)
- Hiển thị số dư trước và sau khi nạp
- Đồng bộ dữ liệu giữa Smart Card và Database

**Tab Gia Hạn:**
- **Vé Tháng:** 100,000 đ/tháng
- Tự động phát hiện mua vé tháng lần đầu hay gia hạn
- Kiểm tra số dư trước khi gia hạn
- Cảnh báo nếu không đủ tiền
- Trừ tiền và cập nhật ngày hết hạn tự động

### 🚏 Chuyển Tuyến
- Hiển thị tuyến hiện tại của khách hàng
- Chọn tuyến mới từ danh sách tuyến xe buýt Hà Nội (11 tuyến)
- Chọn điểm đi và điểm đến
- Lưu lịch sử chuyển tuyến vào database
- Mở Google Maps trong trình duyệt để xem tuyến đường

### 🔐 Quản Lý Smart Card
**Tab Thông tin:**
- Đọc và hiển thị thông tin từ Smart Card
- Hiển thị: Tên, Card ID, CCCD, Ngày sinh, Địa chỉ, Số điện thoại, Số dư, Loại thẻ, Ngày hết hạn, Ảnh đại diện

**Tab Giao dịch:**
- Lịch sử giao dịch từ Database
- Các loại giao dịch: Nạp tiền, Gia hạn, Trừ tiền, Chuyển tuyến
- Hiển thị: Thời gian, Loại giao dịch, Số tiền, Số dư trước/sau, Mô tả

**Tab Đổi PIN:**
- Xác thực PIN hiện tại
- Đổi PIN mới với xác nhận
- Cập nhật PIN trên Smart Card

### 🚌 Quẹt Thẻ Tự Động (Real-time)
Mô phỏng máy quẹt thẻ trên xe buýt:
- Phát hiện thẻ tự động (polling mỗi 0.5 giây)
- Hiển thị avatar và thông tin khách hàng
- Hoạt động theo loại thẻ:
  - **Vé Tháng (MONTHLY):** Không trừ tiền, chỉ kiểm tra còn hạn
  - **Vé Lượt (NORMAL):** Trừ 7,000 đ/lần quẹt
- Ghi nhận giao dịch tự động
- Cập nhật số dư trên cả Smart Card và Database

### ✅ Xác Thực Vé
- Quét thẻ để kiểm tra tính hợp lệ
- Hiển thị thông tin khách hàng và trạng thái thẻ
- Kiểm tra số dư và ngày hết hạn

### 🎫 Trừ Tiền Tự Động
- Cấu hình số tiền trừ tự động
- Trừ tiền từ thẻ khi quẹt
- Ghi nhận giao dịch

---

## 💻 Yêu Cầu Hệ Thống

- **Java Development Kit (JDK):** Version 21 trở lên
- **Kotlin:** Version 1.9+ 
- **Gradle:** Version 8.0+ (được bao gồm trong dự án)
- **Hệ điều hành:** Windows 10/11, macOS, hoặc Linux
- **RAM:** Tối thiểu 2GB (khuyến nghị 4GB)
- **Ổ cứng:** Tối thiểu 500MB dung lượng trống

---

## 🚀 Cài Đặt

### 1. Clone Repository

```bash
git clone <repository-url>
cd KmpUiSmartCard
```

### 2. Kiểm Tra Java Version

```bash
java -version
# Phải là Java 21 trở lên
```

### 3. Build Project

```bash
# Windows
.\gradlew.bat build

# Linux/macOS
./gradlew build
```

---

## 📖 Hướng Dẫn Sử Dụng

### Khởi Động Hệ Thống

#### Bước 1: Chuẩn bị Java Card Applet trong JCIDE

1. Mở **JCIDE** và tạo project Java Card mới.
2. Sao chép nội dung file `src/main/java/com/buscardmanagement/applet/BusCardApplet.java` trong repo vào file applet của JCIDE (thay toàn bộ template mặc định).
3. Build project bên trong JCIDE để sinh file CAP.
4. Install applet vừa build lên simulator của JCIDE, sau đó **Run Simulator** để cấp card ảo (applet chạy hoàn toàn trong JCIDE).

➡️ **Lưu ý:** Nếu bạn dùng Java Card thật/PCSC reader thì chỉ cần cắm thẻ và cài CAP tương tự bước trên.

#### Bước 2: Khởi động Ứng dụng

```bash
# Windows
.\gradlew.bat run

# Linux/macOS
./gradlew run
```

### Quy Trình Sử Dụng

#### 1. Nạp Thông Tin Vào Thẻ Mới

1. Click nút **"Nạp thông tin vào thẻ"** trên màn hình chính
2. **Bước 1 - Kết nối:** Click "Kết nối Java Card" và đợi kết nối thành công
3. **Bước 2 - Kiểm tra thẻ:** Hệ thống sẽ kiểm tra thẻ. Nếu có dữ liệu cũ, click "Xóa dữ liệu"
4. **Bước 3 - Nhập thông tin:**
   - Họ và tên
   - Số CCCD
   - Ngày sinh
   - Địa chỉ
   - Số điện thoại
   - Loại thẻ (Thường hoặc Tháng)
   - Số dư ban đầu
   - PIN (6 chữ số)
   - Ảnh đại diện (tự động resize < 20KB)
5. **Bước 4 - Ghi vào thẻ:** Click "Ghi vào thẻ" và đợi hoàn tất

#### 2. Quản Lý Khách Hàng

- **Xem danh sách:** Danh sách khách hàng hiển thị trên màn hình chính
- **Tìm kiếm:** Nhập tên, Card ID hoặc CCCD vào ô tìm kiếm
- **Xem chi tiết:** Click vào khách hàng để xem thông tin chi tiết
- **Sửa thông tin:** Click "Sửa" trong màn hình chi tiết
- **Xóa khách hàng:** Click "Xóa" và xác nhận

#### 3. Nạp Tiền

1. Chọn khách hàng từ danh sách
2. Click nút **"Nạp tiền / Gia hạn"**
3. Chọn tab **"Nạp tiền"**
4. Nhập số tiền hoặc click nút nạp nhanh (50K, 100K, 200K, 500K)
5. Click **"Nạp tiền"** và xác nhận
6. Số dư sẽ được cập nhật trên cả Smart Card và Database

#### 4. Gia Hạn Vé Tháng

1. Chọn khách hàng có thẻ loại **"Thường"** hoặc **"Tháng"**
2. Click nút **"Nạp tiền / Gia hạn"**
3. Chọn tab **"Gia hạn"**
4. Hệ thống sẽ tự động phát hiện:
   - **Mua vé tháng lần đầu:** Nếu thẻ loại "Thường"
   - **Gia hạn:** Nếu thẻ loại "Tháng" và đã có ngày hết hạn
5. Kiểm tra số dư (cần ≥ 100,000 đ)
6. Click **"Gia hạn"** và xác nhận
7. Ngày hết hạn sẽ được cập nhật (+30 ngày)

#### 5. Chuyển Tuyến

1. Chọn khách hàng
2. Click nút **"Chuyển tuyến"**
3. Chọn tuyến xe buýt từ dropdown
4. Chọn điểm đi và điểm đến
5. Click **"Đóng"** để lưu
6. Click **"Mở trong trình duyệt"** để xem trên Google Maps

#### 6. Quẹt Thẻ Trên Xe

1. Click nút **"Quẹt thẻ tự động"**
2. Đưa thẻ vào máy đọc (hoặc quẹt trên JCIDE Simulator)
3. Hệ thống sẽ tự động:
   - Phát hiện thẻ
   - Hiển thị thông tin khách hàng
   - Kiểm tra loại thẻ:
     - **Vé Tháng:** Kiểm tra còn hạn → Ghi nhận giao dịch
     - **Vé Lượt:** Kiểm tra số dư → Trừ 7,000 đ → Ghi nhận giao dịch
4. Số dư được cập nhật tự động

#### 7. Xem Thông Tin Thẻ

1. Chọn khách hàng
2. Click nút **"Quản lý Smart Card"**
3. Xem thông tin trong tab **"Thông tin"**
4. Xem lịch sử giao dịch trong tab **"Giao dịch"**
5. Đổi PIN trong tab **"Đổi PIN"**

---

## 🏗️ Kiến Trúc Hệ Thống

```
┌─────────────────────────────────────┐
│      Compose Desktop UI             │
│   (Kotlin + Jetpack Compose)        │
├─────────────────────────────────────┤
│      Business Logic Layer           │
│   - BusCardManager (Kotlin)         │
│   - DatabaseManager (Kotlin)        │
├─────────────────────────────────────┤
│      Smart Card Client               │
│   - BusSmartCard (Java)              │
│   - APDU Communication              │
├─────────────────────────────────────┤
│      Java Card                        │
│   - BusCardApplet (Java Card)        │
│   - JCIDE Simulator / PC/SC Reader   │
└─────────────────────────────────────┘
```

### Luồng Dữ Liệu

1. **UI Layer:** Compose Desktop nhận input từ người dùng
2. **Business Logic:** BusCardManager xử lý logic nghiệp vụ
3. **Smart Card Client:** BusSmartCard gửi APDU commands
4. **Java Card:** BusCardApplet xử lý và trả về response
5. **Database:** DatabaseManager lưu trữ dữ liệu SQLite

---

## 📁 Cấu Trúc Dự Án

```
KmpUiSmartCard/
├── src/
│   ├── main/
│   │   ├── kotlin/
│   │   │   ├── Main.kt                    # Entry point
│   │   │   ├── MainApp.kt                 # Main UI application
│   │   │   ├── models/                    # Data models
│   │   │   │   ├── Customer.kt           # Customer model
│   │   │   │   ├── Transaction.kt        # Transaction model
│   │   │   │   └── Trip.kt               # Trip model
│   │   │   ├── database/                 # Database layer
│   │   │   │   └── DatabaseManager.kt    # SQLite operations
│   │   │   ├── smartcard/                # Smart Card layer
│   │   │   │   └── BusCardManager.kt     # Smart Card operations
│   │   │   ├── security/                 # Security utilities
│   │   │   │   └── SecurityUtils.kt    # PIN encryption
│   │   │   └── ui/
│   │   │       ├── screens/              # UI screens
│   │   │       │   ├── LoadCardInfoScreen.kt
│   │   │       │   ├── CustomerInfoScreen.kt
│   │   │       │   ├── PaymentScreen.kt
│   │   │       │   ├── RouteScreen.kt
│   │   │       │   ├── SmartCardManagementScreen.kt
│   │   │       │   ├── RealTimeTapScreen.kt
│   │   │       │   ├── TicketValidationScreen.kt
│   │   │       │   ├── AutoDeductionScreen.kt
│   │   │       │   ├── ChangePinDialog.kt
│   │   │       │   └── PinVerificationDialog.kt
│   │   │       └── components/          # Reusable UI components
│   │   │           └── CommonComponents.kt
│   │   └── java/
│   │       └── com/buscardmanagement/
│   │           ├── client/               # Smart Card client
│   │           │   ├── BusSmartCard.java
│   │           │   └── util/
│   │           │       └── HelpMethod.java
│   │           └── applet/               # Java Card Applet
│   │               └── BusCardApplet.java
│   └── test/                            # Test files
├── db/                                  # Database schemas
│   ├── schema.sql
│   └── schema_sqlite.sql
├── build.gradle.kts                     # Build configuration
├── settings.gradle.kts                  # Project settings
├── gradle.properties                    # Gradle properties
├── CHANGELOG.md                         # Changelog
└── README.md                            # This file
```

---

## 🛠️ Công Nghệ Sử Dụng

### Frontend
- **Kotlin** 1.9+ - Ngôn ngữ lập trình chính
- **Jetpack Compose Desktop** 1.6.0 - Framework UI hiện đại
- **Material Design** - Design system với animations
- **Kotlin Coroutines** - Xử lý bất đồng bộ

### Backend & Database
- **SQLite** - Database nhẹ, nhúng
- **SQLite JDBC** 3.45.0.0 - Driver kết nối database

### Smart Card
- **Java Card Applet** - BusCardApplet chạy trên Java Card
- **Java Smart Card I/O** - Giao tiếp với thẻ thông qua APDU và PC/SC reader
- **JCIDE** - Java Card Integrated Development Environment (để build và test applet)

### Utilities
- **SLF4J** 2.0.9 + **Logback** 1.4.11 - Logging
- **Gson** 2.10.1 - JSON processing (nếu cần)

### Build Tools
- **Gradle** 8.0+ - Build system
- **Kotlin JVM Toolchain** 21 - Compiler toolchain

---

## 🗄️ Database Schema

### Bảng `customers`

| Cột | Kiểu | Mô tả |
|-----|------|-------|
| `id` | TEXT PRIMARY KEY | ID khách hàng (UUID) |
| `full_name` | TEXT | Họ và tên |
| `cccd` | TEXT | Số CCCD/CMND |
| `date_of_birth` | TEXT | Ngày sinh (YYYY-MM-DD) |
| `address` | TEXT | Địa chỉ |
| `phone` | TEXT | Số điện thoại |
| `customer_type` | TEXT | Loại khách hàng (STUDENT/ELDERLY/NORMAL) |
| `card_type` | TEXT | Loại thẻ (NORMAL/MONTHLY) |
| `expiry_date` | TEXT | Ngày hết hạn (YYYY-MM-DD) |
| `balance` | REAL | Số dư (VND) |
| `card_id` | TEXT UNIQUE | Card ID trên thẻ |
| `pin` | TEXT | PIN đã mã hóa |
| `photo_bytes` | BLOB | Ảnh đại diện (đã nén) |

### Bảng `transactions`

| Cột | Kiểu | Mô tả |
|-----|------|-------|
| `id` | INTEGER PRIMARY KEY AUTOINCREMENT | ID giao dịch |
| `card_id` | TEXT | Card ID (Foreign Key) |
| `transaction_type` | TEXT | Loại giao dịch (TOP_UP/DEDUCTION/EXTEND_MONTHLY/TAP/ROUTE_TRANSFER) |
| `amount` | REAL | Số tiền |
| `balance_before` | REAL | Số dư trước giao dịch |
| `balance_after` | REAL | Số dư sau giao dịch |
| `description` | TEXT | Mô tả giao dịch |
| `timestamp` | TEXT | Thời gian (ISO 8601) |

### Bảng `route_history`

| Cột | Kiểu | Mô tả |
|-----|------|-------|
| `id` | INTEGER PRIMARY KEY AUTOINCREMENT | ID bản ghi |
| `card_id` | TEXT | Card ID (Foreign Key) |
| `route_name` | TEXT | Tên tuyến |
| `start_point` | TEXT | Điểm đi |
| `end_point` | TEXT | Điểm đến |
| `timestamp` | TEXT | Thời gian chuyển tuyến (ISO 8601) |

---

## 🎫 Quy Tắc Vé Xe Buýt Hà Nội

### Vé Thường (NORMAL)
- **Giá:** 7,000 đ/lượt
- **Cách dùng:** Trừ tiền mỗi khi quẹt thẻ
- **Điều kiện:** Số dư ≥ 7,000 đ
- **Hết tiền:** ❌ Không thể quẹt thẻ

### Vé Tháng (MONTHLY)
- **Giá:** 100,000 đ/tháng
- **Cách dùng:** Không trừ tiền khi quẹt thẻ
- **Điều kiện:** Còn hạn (ngày hiện tại ≤ ngày hết hạn)
- **Hết hạn:** ❌ Không thể quẹt thẻ (cần gia hạn)

### Chuyển Tuyến
- Chọn tuyến mới và điểm đi/đến
- Lưu lịch sử chuyển tuyến
- Mở Google Maps để xem tuyến đường

---

## 🐛 Xử Lý Lỗi

### Không Kết Nối Được Java Card

**Triệu chứng:** Lỗi khi kết nối với thẻ

**Giải pháp:**
1. Đảm bảo JCIDE Simulator đang chạy và applet đã được cài đặt
2. Hoặc kiểm tra Java Card thật đã được cắm vào PC/SC reader
3. Kiểm tra driver PC/SC đã được cài đặt (Windows Smart Card service)
4. Chạy lại wizard "Nạp thông tin vào thẻ"

### Thẻ Chưa Được Khởi Tạo

**Triệu chứng:** Lỗi khi đọc thông tin từ thẻ

**Giải pháp:**
1. Chạy lại wizard "Nạp thông tin vào thẻ"
2. Đảm bảo Java Card đã được kết nối
3. Kiểm tra applet đã được cài đặt trên card

### Build Lỗi

```bash
# Clean và build lại
.\gradlew.bat clean build

# Hoặc chỉ clean
.\gradlew.bat clean
```

### Lỗi Java Version

**Triệu chứng:** Lỗi "Unsupported class file major version"

**Giải pháp:**
- Đảm bảo đang sử dụng Java 21
- Kiểm tra: `java -version`
- Cập nhật JAVA_HOME nếu cần

### Database Locked

**Triệu chứng:** Lỗi "database is locked"

**Giải pháp:**
- Đóng tất cả kết nối database
- Khởi động lại ứng dụng
- Kiểm tra file `bus_card_management.db` không bị mở bởi ứng dụng khác

---

## 👨‍💻 Phát Triển

### Build Project

```bash
# Build
.\gradlew.bat build

# Clean build
.\gradlew.bat clean build

# Run tests
.\gradlew.bat test
```

### Chạy Ứng dụng

```bash
.\gradlew.bat run
```

### Tạo Distribution

```bash
# Tạo package cho hệ điều hành hiện tại
.\gradlew.bat packageDistributionForCurrentOS

# Tạo package cho tất cả platforms
.\gradlew.bat packageDistributionForAllPlatforms
```

### Cấu Trúc Code

- **Models:** Định nghĩa data classes (`Customer`, `Transaction`, `Trip`)
- **Database:** Tất cả operations với SQLite (`DatabaseManager`)
- **Smart Card:** Wrapper cho Smart Card operations (`BusCardManager`)
- **UI Screens:** Các màn hình Compose (`LoadCardInfoScreen`, `PaymentScreen`, ...)
- **UI Components:** Components tái sử dụng (`CustomCard`, `CustomDropdown`, ...)

### Coding Standards

- Sử dụng Kotlin coding conventions
- Tên biến và hàm bằng tiếng Việt có dấu cho UI
- Comment code phức tạp
- Xử lý exception đầy đủ
- Sử dụng Coroutines cho async operations

---

## 📝 Changelog

Xem [CHANGELOG.md](CHANGELOG.md) để biết chi tiết các thay đổi.

### Phiên Bản Hiện Tại: 1.0-SNAPSHOT

**Tính năng chính:**
- ✅ Quản lý khách hàng đầy đủ
- ✅ Nạp thông tin vào thẻ (Wizard 4 bước)
- ✅ Nạp tiền và gia hạn vé tháng
- ✅ Chuyển tuyến với Google Maps
- ✅ Quẹt thẻ tự động real-time
- ✅ Quản lý Smart Card với đổi PIN
- ✅ Xác thực vé
- ✅ Trừ tiền tự động

---

## 📄 License

MIT License

Copyright (c) 2025 Bus Card Management System

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

---

## 🙏 Acknowledgments

- **JetBrains** - Kotlin và Compose Desktop
- **JCIDE** - Java Card Integrated Development Environment
- **SQLite** - Database engine
- **Material Design** - Design system

---

**Built with ❤️ using Kotlin & Jetpack Compose Desktop**
