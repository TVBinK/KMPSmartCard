# 🚌 Hệ Thống Quản Lý Thẻ Xe Buýt Smart Card

Hệ thống quản lý thẻ xe buýt Hà Nội với **JavaCard Simulator** và **Kotlin Compose Desktop**.

---

## 🚀 Cách Chạy

### Bước 1: Khởi động Simulator
```bash
.\gradlew.bat runSimulator
```
➡️ Đợi loading xong rồi chạy bước 2 Simulator lắng nghe trên **port 9025**

### Bước 2: Khởi động App
```bash
.\gradlew.bat run
```

### Bước 3: Sử dụng
1. **Nạp thông tin vào thẻ** → Làm theo 4 bước wizard
2. Chọn khách hàng từ danh sách
3. Sử dụng các chức năng (Nạp tiền, Gia hạn, Quẹt thẻ...)

---

## 📋 Chức Năng Chính

### 1️⃣ Quản Lý Khách Hàng
- ✅ Danh sách khách hàng với avatar
- ✅ Tìm kiếm theo tên/Card ID
- ✅ Xem thông tin chi tiết
- ✅ Sửa & Xóa khách hàng
- ✅ Thống kê tổng quan

### 2️⃣ Nạp Thông Tin Vào Thẻ
**Wizard 4 bước:**
1. **Kết nối Simulator** → Kiểm tra kết nối
2. **Kiểm tra thẻ** → Thẻ rỗng/có dữ liệu + Xóa dữ liệu nếu cần
3. **Nhập thông tin** → Họ tên, loại thẻ, số dư, PIN, ảnh
4. **Ghi vào thẻ** → Lưu vào Smart Card + Database

**Tính năng:**
- ✅ Resize & nén ảnh tự động (< 20KB)
- ✅ Xóa dữ liệu thẻ cũ
- ✅ Validation đầy đủ

### 3️⃣ Nạp Tiền & Gia Hạn

#### **Tab Nạp Tiền:**
- Nhập số tiền tùy ý
- Nút nạp nhanh: **50K | 100K | 200K | 500K**
- Hiển thị số dư sau khi nạp
- Đồng bộ: Smart Card + Database

#### **Tab Gia Hạn:**
- **Vé Tháng:** 100,000 đ/tháng
- **Vé Lượt:** 7,000 đ/lượt
- ✅ Kiểm tra số dư trước khi gia hạn
- ✅ Cảnh báo nếu không đủ tiền
- ✅ Trừ tiền từ thẻ + Database

### 4️⃣ Chuyển Tuyến
- Hiển thị tuyến hiện tại
- Chọn tuyến mới (Tuyến xe buýt Hà Nội thật)
- **Miễn phí trong 30 phút** từ lần quẹt đầu
- Hết 30 phút → Không cho chuyển (phải quẹt lại)
- Countdown timer thời gian còn lại

### 5️⃣ Quản Lý Smart Card
**2 Tab:**

#### **Tab Thông tin:**
- Xem thông tin từ Smart Card
- Hiển thị: Tên, Card ID, Số dư, Loại thẻ, Ngày hết hạn, Ảnh

#### **Tab Giao dịch:**
- Lịch sử giao dịch từ Database
- Loại: Nạp tiền, Gia hạn, Trừ tiền, Chuyển tuyến
- Hiển thị: Thời gian, Số tiền, Số dư trước/sau

### 6️⃣ Quẹt Thẻ Tự Động (Real-time)
**Mô phỏng máy quẹt thẻ trên xe:**
- Phát hiện thẻ tự động (polling 0.5s)
- Hiển thị avatar khách hàng
- Hoạt động theo loại thẻ:

| Loại thẻ | Logic |
|----------|-------|
| **Vé Tháng** | ✅ Không trừ tiền, chỉ kiểm tra còn hạn |
| **Vé Lượt** | 💰 Trừ 7,000 đ/lần quẹt |

- ✅ Ghi nhận giao dịch
- ✅ Cập nhật số dư tự động

---

## 🎫 Quy Tắc Vé Xe Buýt Hà Nội

### Vé Lượt (SINGLE_TRIP)
- **Giá:** 7,000 đ/lượt
- **Cách dùng:** Trừ tiền mỗi khi quẹt
- **Điều kiện:** Số dư ≥ 7,000 đ
- **Hết tiền:** ❌ Không đi được

### Vé Tháng (MONTHLY)
- **Giá:** 100,000 đ/tháng
- **Cách dùng:** Không trừ tiền khi quẹt
- **Điều kiện:** Còn hạn
- **Hết tiền:** ✅ Vẫn đi được (nếu còn hạn)

### Chuyển Tuyến
- **Miễn phí:** Trong 30 phút kể từ lần quẹt đầu
- **Hết thời gian:** Không cho chuyển (phải quẹt lại)

---

## 🔧 Công Nghệ

### Frontend
- **Kotlin** + **Jetpack Compose Desktop**
- **Material Design** với animations
- **Coroutines** cho async operations

### Backend
- **SQLite** Database (quản lý khách hàng & giao dịch)
- **JCardSim** (Smart Card Simulator)
- **Java Smart Card I/O** (Giao tiếp với thẻ)

### Architecture
```
┌─────────────────┐
│  Compose UI     │ ← Kotlin/Compose Desktop
├─────────────────┤
│  BusCardManager │ ← Kotlin wrapper
├─────────────────┤
│  BusSmartCard   │ ← Java client (APDU)
├─────────────────┤
│  JCardSimServer │ ← JavaCard Simulator
└─────────────────┘
```

---

## 🗂️ Cấu Trúc Dự Án

```
src/main/
├── kotlin/
│   ├── MainApp.kt              # Main UI & Logic
│   ├── models/                 # Data models
│   │   ├── Customer.kt
│   │   ├── Transaction.kt
│   │   └── Trip.kt
│   ├── database/               # SQLite
│   │   └── DatabaseManager.kt
│   ├── smartcard/              # Smart Card
│   │   └── BusCardManager.kt
│   └── ui/
│       ├── screens/            # Các màn hình
│       └── components/         # UI components
│
└── java/
    └── com.buscardmanagement/
        ├── client/             # APDU Client
        │   ├── BusSmartCard.java
        │   └── HelpMethod.java
        └── simulator/          # JavaCard Simulator
            └── JCardSimServer.java
```

---

## 🎯 Quy Trình Hoạt Động

### 1. Nạp thông tin vào thẻ mới
```
Kết nối Simulator → Kiểm tra thẻ → Nhập thông tin 
→ Ghi vào Smart Card + Database → Hoàn tất
```

### 2. Nạp tiền vào thẻ
```
Chọn khách hàng → Nhập số tiền → Xác nhận 
→ Cập nhật Smart Card + Database
```

### 3. Gia hạn vé tháng
```
Chọn khách hàng → Chọn số tháng → Kiểm tra số dư 
→ Trừ 100,000đ → Gia hạn thêm 1 tháng
```

### 4. Quẹt thẻ lên xe
```
Quẹt thẻ → Đọc Card ID → Tìm khách hàng 
→ Kiểm tra loại thẻ:
   • Vé Tháng: Kiểm tra hạn → Ghi nhận (miễn phí)
   • Vé Lượt: Kiểm tra số dư → Trừ 7,000đ
```

### 5. Chuyển tuyến
```
Trong chuyến đi → Chọn tuyến mới → Kiểm tra thời gian:
   • < 30 phút: Chuyển miễn phí
   • ≥ 30 phút: Không cho chuyển
```

---

## 🐛 Xử Lý Lỗi

### Port 9025 bị chiếm
```bash
netstat -ano | findstr :9025
taskkill /F /PID <PID>
.\gradlew.bat runSimulator
```

### Thẻ chưa khởi tạo
➡️ Chạy lại "Nạp thông tin vào thẻ"

### Build lỗi
```bash
.\gradlew.bat clean build
```

---

## 📝 Database Schema

### Bảng `customers`
```sql
- id: TEXT PRIMARY KEY
- full_name: TEXT
- customer_type: TEXT (STUDENT/ELDERLY/NORMAL)
- card_type: TEXT (SINGLE_TRIP/MONTHLY)
- expiry_date: TEXT
- balance: REAL
- card_id: TEXT UNIQUE
- photo_bytes: BLOB
```

### Bảng `transactions`
```sql
- id: INTEGER PRIMARY KEY
- card_id: TEXT
- transaction_type: TEXT (TOP_UP/DEDUCTION/EXTEND_MONTHLY/TAP/ROUTE_TRANSFER)
- amount: REAL
- balance_before: REAL
- balance_after: REAL
- description: TEXT
- timestamp: TEXT
```

---

## 🎨 Features

✅ Real-time Smart Card simulation  
✅ Automatic balance deduction  
✅ Route transfer with time limit  
✅ Transaction history  
✅ Photo storage (auto resize < 20KB)  
✅ Modern UI with animations  
✅ Vietnamese localization  
✅ Hanoi bus routes  

---

## 📦 Dependencies

- Kotlin 1.9+
- Compose Desktop 1.6.0
- SQLite JDBC
- JCardSim 3.0.5
- Java Smart Card I/O

---

## 👨‍💻 Development

```bash
# Clean build
.\gradlew.bat clean build

# Run simulator
.\gradlew.bat runSimulator

# Run app
.\gradlew.bat run

# Create distribution
.\gradlew.bat packageDistributionForCurrentOS
```

---

## 📄 License

MIT License

---

**Built with ❤️ using Kotlin & Jetpack Compose**
