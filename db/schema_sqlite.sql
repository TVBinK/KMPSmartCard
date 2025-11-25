-- ===============================================
-- SCHEMA DATABASE - Hệ thống quản lý thẻ xe bus (SQLite)
-- ===============================================

-- Xóa các bảng cũ nếu tồn tại
DROP TABLE IF EXISTS trip_history;
DROP TABLE IF EXISTS transaction;
DROP TABLE IF EXISTS bus_route;
DROP TABLE IF EXISTS customer;

-- ===============================================
-- BẢNG: customer - Thông tin khách hàng
-- ===============================================
CREATE TABLE customer (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    card_id TEXT UNIQUE NOT NULL,                 -- Card ID (CARD-001, CARD-002, ...)
    full_name TEXT NOT NULL,                       -- Họ tên
    customer_type TEXT NOT NULL,                   -- Loại đối tượng
    card_type TEXT NOT NULL,                       -- Loại thẻ: MONTHLY, SINGLE_TRIP
    balance REAL DEFAULT 0,                        -- Số dư tài khoản
    expiry_date TEXT NOT NULL,                     -- Ngày hết hạn thẻ (ISO format)
    status TEXT DEFAULT 'ACTIVE',                  -- Trạng thái: ACTIVE, EXPIRED, BLOCKED
    pin_code TEXT,                                 -- Mã PIN (đã mã hóa)
    public_key TEXT,                               -- Public key RSA để xác thực
    picture_url TEXT,                              -- URL ảnh khách hàng (legacy)
    photo_bytes BLOB,                              -- Ảnh khách hàng dạng byte array
    linked_customer_id TEXT,                       -- Mã khách hàng liên kết (nếu có)
    created_at TEXT DEFAULT CURRENT_TIMESTAMP,     -- Ngày tạo
    updated_at TEXT DEFAULT CURRENT_TIMESTAMP,     -- Ngày cập nhật
    created_by TEXT,                               -- Người tạo
    notes TEXT                                     -- Ghi chú
);

-- Index cho customer
CREATE INDEX idx_customer_card_id ON customer(card_id);
CREATE INDEX idx_customer_type ON customer(customer_type);
CREATE INDEX idx_customer_status ON customer(status);
CREATE INDEX idx_customer_expiry_date ON customer(expiry_date);

-- ===============================================
-- BẢNG: bus_route - Thông tin tuyến xe bus
-- ===============================================
CREATE TABLE bus_route (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    route_id TEXT UNIQUE NOT NULL,                 -- Mã tuyến (T01, T02, ...)
    route_name TEXT NOT NULL,                      -- Tên tuyến
    description TEXT,                              -- Mô tả tuyến
    start_location TEXT,                           -- Điểm đầu
    end_location TEXT,                             -- Điểm cuối
    total_stops INTEGER DEFAULT 0,                 -- Tổng số chặng
    base_fare REAL DEFAULT 5000,                   -- Giá cơ bản (VNĐ)
    fare_per_stop REAL DEFAULT 3000,               -- Giá mỗi chặng (VNĐ)
    is_active INTEGER DEFAULT 1,                   -- Tuyến có hoạt động không (0=false, 1=true)
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);

-- Index cho bus_route
CREATE INDEX idx_route_id ON bus_route(route_id);
CREATE INDEX idx_route_active ON bus_route(is_active);

-- ===============================================
-- BẢNG: transaction - Giao dịch tài chính
-- ===============================================
CREATE TABLE transaction (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER NOT NULL,                  -- ID khách hàng
    card_id TEXT NOT NULL,                         -- Card ID
    transaction_type TEXT NOT NULL,                -- Loại giao dịch (TOP_UP, DEDUCTION, EXTENSION, REFUND)
    amount REAL NOT NULL,                          -- Số tiền
    balance_before REAL,                           -- Số dư trước giao dịch
    balance_after REAL,                            -- Số dư sau giao dịch
    description TEXT,                              -- Mô tả
    transaction_date TEXT DEFAULT CURRENT_TIMESTAMP,
    created_by TEXT,                               -- Người thực hiện
    FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE
);

-- Index cho transaction
CREATE INDEX idx_transaction_customer ON transaction(customer_id);
CREATE INDEX idx_transaction_card_id ON transaction(card_id);
CREATE INDEX idx_transaction_type ON transaction(transaction_type);
CREATE INDEX idx_transaction_date ON transaction(transaction_date);

-- ===============================================
-- BẢNG: trip_history - Lịch sử quẹt thẻ/chuyến đi
-- ===============================================
CREATE TABLE trip_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    customer_id INTEGER NOT NULL,                  -- ID khách hàng
    card_id TEXT NOT NULL,                         -- Card ID
    route_id_on TEXT,                              -- Tuyến quẹt lên
    tap_on_location TEXT,                          -- Vị trí quẹt lên
    tap_on_time TEXT,                              -- Thời gian quẹt lên (ISO format)
    route_id_off TEXT,                             -- Tuyến quẹt xuống
    tap_off_location TEXT,                         -- Vị trí quẹt xuống
    tap_off_time TEXT,                             -- Thời gian quẹt xuống (ISO format)
    stops_count INTEGER DEFAULT 0,                 -- Số chặng đi
    fare_amount REAL DEFAULT 0,                    -- Số tiền cước phí
    is_completed INTEGER DEFAULT 0,                -- Chuyến đi hoàn tất chưa (0=false, 1=true)
    is_free_transfer INTEGER DEFAULT 0,            -- Có phải chuyển tuyến miễn phí không (0=false, 1=true)
    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(id) ON DELETE CASCADE
);

-- Index cho trip_history
CREATE INDEX idx_trip_customer ON trip_history(customer_id);
CREATE INDEX idx_trip_card_id ON trip_history(card_id);
CREATE INDEX idx_trip_completed ON trip_history(is_completed);
CREATE INDEX idx_trip_tap_on_time ON trip_history(tap_on_time);
CREATE INDEX idx_trip_tap_off_time ON trip_history(tap_off_time);

-- ===============================================
-- DỮ LIỆU MẪU (Optional - có thể bỏ comment để test)
-- ===============================================

-- Insert bus routes
INSERT INTO bus_route (route_id, route_name, description, start_location, end_location, total_stops, base_fare, fare_per_stop, is_active) VALUES
('T01', 'Tuyến 01 - Bến Thành', 'Tuyến từ Bến Thành đi Chợ Lớn', 'Bến Thành', 'Chợ Lớn', 15, 5000, 3000, 1),
('T02', 'Tuyến 02 - Tân Sơn Nhất', 'Tuyến từ Tân Sơn Nhất đi Q1', 'Sân bay Tân Sơn Nhất', 'Quận 1', 20, 5000, 3000, 1),
('T03', 'Tuyến 03 - Thủ Đức', 'Tuyến từ Thủ Đức đi Bình Thạnh', 'Thủ Đức', 'Bình Thạnh', 18, 5000, 3000, 1),
('T04', 'Tuyến 04 - Bình Tân', 'Tuyến từ Bình Tân đi Tân Bình', 'Bình Tân', 'Tân Bình', 12, 5000, 3000, 1),
('T05', 'Tuyến 05 - Gò Vấp', 'Tuyến từ Gò Vấp đi Q1', 'Gò Vấp', 'Quận 1', 22, 5000, 3000, 1);

-- ===============================================
-- VIEWS (SQLite hỗ trợ VIEW)
-- ===============================================

-- View: Thông tin khách hàng với số dư và trạng thái
CREATE VIEW v_customer_summary AS
SELECT 
    c.id,
    c.card_id,
    c.full_name,
    c.customer_type,
    c.card_type,
    c.balance,
    c.expiry_date,
    CASE 
        WHEN c.expiry_date < DATE('now') THEN 'EXPIRED'
        WHEN c.expiry_date < DATE('now', '+7 days') THEN 'EXPIRING_SOON'
        WHEN c.balance <= 0 THEN 'INSUFFICIENT_BALANCE'
        ELSE 'VALID'
    END AS card_status,
    CAST((JULIANDAY(c.expiry_date) - JULIANDAY('now')) AS INTEGER) AS days_until_expiry,
    COUNT(DISTINCT t.id) AS total_transactions,
    COUNT(DISTINCT th.id) AS total_trips
FROM customer c
LEFT JOIN transaction t ON c.id = t.customer_id
LEFT JOIN trip_history th ON c.id = th.customer_id
GROUP BY c.id, c.card_id, c.full_name, c.customer_type, c.card_type, 
         c.balance, c.expiry_date;

-- View: Lịch sử giao dịch chi tiết
CREATE VIEW v_transaction_details AS
SELECT 
    t.id,
    t.card_id,
    c.full_name,
    c.customer_type,
    t.transaction_type,
    t.amount,
    t.balance_before,
    t.balance_after,
    t.description,
    t.transaction_date,
    t.created_by
FROM transaction t
JOIN customer c ON t.customer_id = c.id
ORDER BY t.transaction_date DESC;

-- View: Lịch sử chuyến đi chi tiết
CREATE VIEW v_trip_details AS
SELECT 
    th.id,
    th.card_id,
    c.full_name,
    c.customer_type,
    r_on.route_name AS route_on_name,
    th.tap_on_location,
    th.tap_on_time,
    r_off.route_name AS route_off_name,
    th.tap_off_location,
    th.tap_off_time,
    th.stops_count,
    th.fare_amount,
    th.is_completed,
    th.is_free_transfer,
    CAST((JULIANDAY(th.tap_off_time) - JULIANDAY(th.tap_on_time)) * 24 * 60 AS INTEGER) AS trip_duration_minutes
FROM trip_history th
JOIN customer c ON th.customer_id = c.id
LEFT JOIN bus_route r_on ON th.route_id_on = r_on.route_id
LEFT JOIN bus_route r_off ON th.route_id_off = r_off.route_id
ORDER BY th.tap_on_time DESC;

-- ===============================================
-- END OF SCHEMA
-- ===============================================

