-- ===============================================
-- SCHEMA DATABASE - Hệ thống quản lý thẻ xe bus
-- ===============================================

-- Xóa các bảng cũ nếu tồn tại (theo thứ tự foreign key)
DROP TABLE IF EXISTS "trip_history" CASCADE;
DROP TABLE IF EXISTS "transaction" CASCADE;
DROP TABLE IF EXISTS "bus_route" CASCADE;
DROP TABLE IF EXISTS "customer" CASCADE;

-- ===============================================
-- BẢNG: customer - Thông tin khách hàng
-- ===============================================
CREATE TABLE "customer" (
    "id" SERIAL PRIMARY KEY,
    "card_id" VARCHAR(20) UNIQUE NOT NULL,           -- Card ID (CARD-001, CARD-002, ...)
    "full_name" VARCHAR(100) NOT NULL,                -- Họ tên
    "customer_type" VARCHAR(50) NOT NULL,             -- Loại đối tượng: HSSV, Người cao tuổi, Thông thường
    "card_type" VARCHAR(50) NOT NULL,                 -- Loại thẻ: Vé Lượt, Vé Tháng
    "balance" DECIMAL(10,2) DEFAULT 0,                -- Số dư tài khoản
    "expiry_date" DATE NOT NULL,                      -- Ngày hết hạn thẻ
    "status" VARCHAR(20) DEFAULT 'ACTIVE',            -- Trạng thái: ACTIVE, EXPIRED, BLOCKED
    "pin_code" VARCHAR(100),                          -- Mã PIN (đã mã hóa)
    "public_key" TEXT,                                -- Public key RSA để xác thực
    "picture_url" VARCHAR(255),                       -- URL ảnh khách hàng
    "linked_customer_id" VARCHAR(50),                 -- Mã khách hàng liên kết (nếu có)
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Ngày tạo
    "updated_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Ngày cập nhật
    "created_by" VARCHAR(50),                         -- Người tạo
    "notes" TEXT                                      -- Ghi chú
);

-- Index cho customer
CREATE INDEX "idx_customer_card_id" ON "customer" ("card_id");
CREATE INDEX "idx_customer_type" ON "customer" ("customer_type");
CREATE INDEX "idx_customer_status" ON "customer" ("status");
CREATE INDEX "idx_customer_expiry_date" ON "customer" ("expiry_date");

-- ===============================================
-- BẢNG: bus_route - Thông tin tuyến xe bus
-- ===============================================
CREATE TABLE "bus_route" (
    "id" SERIAL PRIMARY KEY,
    "route_id" VARCHAR(20) UNIQUE NOT NULL,          -- Mã tuyến (T01, T02, ...)
    "route_name" VARCHAR(100) NOT NULL,               -- Tên tuyến
    "description" TEXT,                               -- Mô tả tuyến
    "start_location" VARCHAR(100),                    -- Điểm đầu
    "end_location" VARCHAR(100),                      -- Điểm cuối
    "total_stops" INTEGER DEFAULT 0,                  -- Tổng số chặng
    "base_fare" DECIMAL(10,2) DEFAULT 5000,           -- Giá cơ bản (VNĐ)
    "fare_per_stop" DECIMAL(10,2) DEFAULT 3000,       -- Giá mỗi chặng (VNĐ)
    "is_active" BOOLEAN DEFAULT TRUE,                 -- Tuyến có hoạt động không
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index cho bus_route
CREATE INDEX "idx_route_id" ON "bus_route" ("route_id");
CREATE INDEX "idx_route_active" ON "bus_route" ("is_active");

-- ===============================================
-- BẢNG: transaction - Giao dịch tài chính
-- ===============================================
CREATE TABLE "transaction" (
    "id" SERIAL PRIMARY KEY,
    "customer_id" INTEGER NOT NULL,                   -- ID khách hàng
    "card_id" VARCHAR(20) NOT NULL,                   -- Card ID
    "transaction_type" VARCHAR(50) NOT NULL,          -- Loại giao dịch
    "amount" DECIMAL(10,2) NOT NULL,                  -- Số tiền
    "balance_before" DECIMAL(10,2),                   -- Số dư trước giao dịch
    "balance_after" DECIMAL(10,2),                    -- Số dư sau giao dịch
    "description" TEXT,                               -- Mô tả
    "transaction_date" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    "created_by" VARCHAR(50),                         -- Người thực hiện
    
    CONSTRAINT "fk_transaction_customer" 
        FOREIGN KEY ("customer_id") 
        REFERENCES "customer"("id") 
        ON UPDATE CASCADE 
        ON DELETE RESTRICT
);

-- Index cho transaction
CREATE INDEX "idx_transaction_customer" ON "transaction" ("customer_id");
CREATE INDEX "idx_transaction_card_id" ON "transaction" ("card_id");
CREATE INDEX "idx_transaction_type" ON "transaction" ("transaction_type");
CREATE INDEX "idx_transaction_date" ON "transaction" ("transaction_date");

-- Comments cho transaction_type
COMMENT ON COLUMN "transaction"."transaction_type" IS 
'Loại giao dịch: TOP_UP (nạp tiền), DEDUCTION (trừ tiền), EXTENSION_MONTHLY (gia hạn tháng), EXTENSION_TRIPS (nạp lượt), REFUND (hoàn tiền)';

-- ===============================================
-- BẢNG: trip_history - Lịch sử quẹt thẻ/chuyến đi
-- ===============================================
CREATE TABLE "trip_history" (
    "id" SERIAL PRIMARY KEY,
    "customer_id" INTEGER NOT NULL,                   -- ID khách hàng
    "card_id" VARCHAR(20) NOT NULL,                   -- Card ID
    "route_id_on" VARCHAR(20),                        -- Tuyến quẹt lên
    "tap_on_location" VARCHAR(100),                   -- Vị trí quẹt lên
    "tap_on_time" TIMESTAMP,                          -- Thời gian quẹt lên
    "route_id_off" VARCHAR(20),                       -- Tuyến quẹt xuống
    "tap_off_location" VARCHAR(100),                  -- Vị trí quẹt xuống
    "tap_off_time" TIMESTAMP,                         -- Thời gian quẹt xuống
    "stops_count" INTEGER DEFAULT 0,                  -- Số chặng đi
    "fare_amount" DECIMAL(10,2) DEFAULT 0,            -- Số tiền cước phí
    "is_completed" BOOLEAN DEFAULT FALSE,             -- Chuyến đi hoàn tất chưa
    "is_free_transfer" BOOLEAN DEFAULT FALSE,         -- Có phải chuyển tuyến miễn phí không
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT "fk_trip_customer" 
        FOREIGN KEY ("customer_id") 
        REFERENCES "customer"("id") 
        ON UPDATE CASCADE 
        ON DELETE RESTRICT,
    CONSTRAINT "fk_trip_route_on" 
        FOREIGN KEY ("route_id_on") 
        REFERENCES "bus_route"("route_id") 
        ON UPDATE CASCADE 
        ON DELETE SET NULL,
    CONSTRAINT "fk_trip_route_off" 
        FOREIGN KEY ("route_id_off") 
        REFERENCES "bus_route"("route_id") 
        ON UPDATE CASCADE 
        ON DELETE SET NULL
);

-- Index cho trip_history
CREATE INDEX "idx_trip_customer" ON "trip_history" ("customer_id");
CREATE INDEX "idx_trip_card_id" ON "trip_history" ("card_id");
CREATE INDEX "idx_trip_completed" ON "trip_history" ("is_completed");
CREATE INDEX "idx_trip_tap_on_time" ON "trip_history" ("tap_on_time");
CREATE INDEX "idx_trip_tap_off_time" ON "trip_history" ("tap_off_time");

-- ===============================================
-- DỮ LIỆU MẪU
-- ===============================================

-- Insert bus routes
INSERT INTO "bus_route" ("route_id", "route_name", "description", "start_location", "end_location", "total_stops", "base_fare", "fare_per_stop", "is_active") VALUES
('T01', 'Tuyến 01 - Bến Thành', 'Tuyến từ Bến Thành đi Chợ Lớn', 'Bến Thành', 'Chợ Lớn', 15, 5000, 3000, TRUE),
('T02', 'Tuyến 02 - Tân Sơn Nhất', 'Tuyến từ Tân Sơn Nhất đi Q1', 'Sân bay Tân Sơn Nhất', 'Quận 1', 20, 5000, 3000, TRUE),
('T03', 'Tuyến 03 - Thủ Đức', 'Tuyến từ Thủ Đức đi Bình Thạnh', 'Thủ Đức', 'Bình Thạnh', 18, 5000, 3000, TRUE),
('T04', 'Tuyến 04 - Bình Tân', 'Tuyến từ Bình Tân đi Tân Bình', 'Bình Tân', 'Tân Bình', 12, 5000, 3000, TRUE),
('T05', 'Tuyến 05 - Gò Vấp', 'Tuyến từ Gò Vấp đi Q1', 'Gò Vấp', 'Quận 1', 22, 5000, 3000, TRUE);

-- Insert sample customers
INSERT INTO "customer" ("card_id", "full_name", "customer_type", "card_type", "balance", "expiry_date", "status", "pin_code", "linked_customer_id", "created_by") VALUES
('CARD-001', 'Nguyễn Văn A', 'HSSV', 'Vé Tháng', 150000, '2026-01-31', 'ACTIVE', '1234', 'SV001', 'ADMIN'),
('CARD-002', 'Trần Thị B', 'Người cao tuổi', 'Vé Tháng', 50000, '2025-12-31', 'ACTIVE', '5678', 'NCT001', 'ADMIN'),
('CARD-003', 'Lê Văn C', 'Thông thường', 'Vé Lượt', 0, '2025-06-30', 'EXPIRED', '9999', NULL, 'ADMIN');

-- Insert sample transactions
INSERT INTO "transaction" ("customer_id", "card_id", "transaction_type", "amount", "balance_before", "balance_after", "description", "created_by") VALUES
(1, 'CARD-001', 'TOP_UP', 150000, 0, 150000, 'Nạp tiền ban đầu', 'ADMIN'),
(2, 'CARD-002', 'TOP_UP', 50000, 0, 50000, 'Nạp tiền ban đầu', 'ADMIN'),
(1, 'CARD-001', 'DEDUCTION', -20000, 150000, 130000, 'Trừ tiền chuyến đi T01', 'SYSTEM'),
(2, 'CARD-002', 'DEDUCTION', -15000, 50000, 35000, 'Trừ tiền chuyến đi T02', 'SYSTEM');

-- Insert sample trip history
INSERT INTO "trip_history" ("customer_id", "card_id", "route_id_on", "tap_on_location", "tap_on_time", "route_id_off", "tap_off_location", "tap_off_time", "stops_count", "fare_amount", "is_completed") VALUES
(1, 'CARD-001', 'T01', 'Bến Thành', '2025-11-18 08:00:00', 'T01', 'Chợ Lớn', '2025-11-18 08:30:00', 5, 20000, TRUE),
(2, 'CARD-002', 'T02', 'Tân Sơn Nhất', '2025-11-18 09:00:00', 'T02', 'Quận 1', '2025-11-18 09:45:00', 3, 15000, TRUE);

-- ===============================================
-- VIEWS (Optional - để truy vấn dễ dàng hơn)
-- ===============================================

-- View: Thông tin khách hàng với số dư và trạng thái
CREATE OR REPLACE VIEW "v_customer_summary" AS
SELECT 
    c."id",
    c."card_id",
    c."full_name",
    c."customer_type",
    c."card_type",
    c."balance",
    c."expiry_date",
    CASE 
        WHEN c."expiry_date" < CURRENT_DATE THEN 'EXPIRED'
        WHEN c."expiry_date" < CURRENT_DATE + INTERVAL '7 days' THEN 'EXPIRING_SOON'
        WHEN c."balance" <= 0 THEN 'INSUFFICIENT_BALANCE'
        ELSE 'VALID'
    END AS "card_status",
    (c."expiry_date" - CURRENT_DATE) AS "days_until_expiry",
    COUNT(DISTINCT t."id") AS "total_transactions",
    COUNT(DISTINCT th."id") AS "total_trips"
FROM "customer" c
LEFT JOIN "transaction" t ON c."id" = t."customer_id"
LEFT JOIN "trip_history" th ON c."id" = th."customer_id"
GROUP BY c."id", c."card_id", c."full_name", c."customer_type", c."card_type", 
         c."balance", c."expiry_date";

-- View: Lịch sử giao dịch chi tiết
CREATE OR REPLACE VIEW "v_transaction_details" AS
SELECT 
    t."id",
    t."card_id",
    c."full_name",
    c."customer_type",
    t."transaction_type",
    t."amount",
    t."balance_before",
    t."balance_after",
    t."description",
    t."transaction_date",
    t."created_by"
FROM "transaction" t
JOIN "customer" c ON t."customer_id" = c."id"
ORDER BY t."transaction_date" DESC;

-- View: Lịch sử chuyến đi chi tiết
CREATE OR REPLACE VIEW "v_trip_details" AS
SELECT 
    th."id",
    th."card_id",
    c."full_name",
    c."customer_type",
    r_on."route_name" AS "route_on_name",
    th."tap_on_location",
    th."tap_on_time",
    r_off."route_name" AS "route_off_name",
    th."tap_off_location",
    th."tap_off_time",
    th."stops_count",
    th."fare_amount",
    th."is_completed",
    th."is_free_transfer",
    EXTRACT(EPOCH FROM (th."tap_off_time" - th."tap_on_time"))/60 AS "trip_duration_minutes"
FROM "trip_history" th
JOIN "customer" c ON th."customer_id" = c."id"
LEFT JOIN "bus_route" r_on ON th."route_id_on" = r_on."route_id"
LEFT JOIN "bus_route" r_off ON th."route_id_off" = r_off."route_id"
ORDER BY th."tap_on_time" DESC;

-- ===============================================
-- FUNCTIONS (Optional - các hàm tiện ích)
-- ===============================================

-- Function: Tính cước phí dựa trên số chặng
CREATE OR REPLACE FUNCTION calculate_fare(route_id_param VARCHAR, stops_count_param INTEGER)
RETURNS DECIMAL(10,2) AS $$
DECLARE
    base_fare_val DECIMAL(10,2);
    fare_per_stop_val DECIMAL(10,2);
BEGIN
    SELECT "base_fare", "fare_per_stop" 
    INTO base_fare_val, fare_per_stop_val
    FROM "bus_route"
    WHERE "route_id" = route_id_param;
    
    IF base_fare_val IS NULL THEN
        -- Giá mặc định nếu không tìm thấy tuyến
        base_fare_val := 5000;
        fare_per_stop_val := 3000;
    END IF;
    
    RETURN base_fare_val + (stops_count_param * fare_per_stop_val);
END;
$$ LANGUAGE plpgsql;

-- Function: Kiểm tra thẻ có hợp lệ không
CREATE OR REPLACE FUNCTION is_card_valid(card_id_param VARCHAR)
RETURNS BOOLEAN AS $$
DECLARE
    expiry_date_val DATE;
    balance_val DECIMAL(10,2);
    status_val VARCHAR(20);
BEGIN
    SELECT "expiry_date", "balance", "status"
    INTO expiry_date_val, balance_val, status_val
    FROM "customer"
    WHERE "card_id" = card_id_param;
    
    IF expiry_date_val IS NULL THEN
        RETURN FALSE;
    END IF;
    
    IF status_val = 'BLOCKED' THEN
        RETURN FALSE;
    END IF;
    
    IF expiry_date_val < CURRENT_DATE THEN
        RETURN FALSE;
    END IF;
    
    IF balance_val <= 0 THEN
        RETURN FALSE;
    END IF;
    
    RETURN TRUE;
END;
$$ LANGUAGE plpgsql;

-- ===============================================
-- TRIGGERS (Optional - tự động cập nhật)
-- ===============================================

-- Function cho trigger: Cập nhật updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW."updated_at" = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: Auto update updated_at khi customer thay đổi
CREATE TRIGGER trigger_customer_updated_at
    BEFORE UPDATE ON "customer"
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ===============================================
-- COMMENTS
-- ===============================================

COMMENT ON TABLE "customer" IS 'Bảng lưu thông tin khách hàng và thẻ xe bus';
COMMENT ON TABLE "bus_route" IS 'Bảng lưu thông tin các tuyến xe bus';
COMMENT ON TABLE "transaction" IS 'Bảng lưu lịch sử giao dịch tài chính';
COMMENT ON TABLE "trip_history" IS 'Bảng lưu lịch sử quẹt thẻ và chuyến đi';

COMMENT ON COLUMN "customer"."customer_type" IS 'HSSV: Học sinh sinh viên, Người cao tuổi, Thông thường';
COMMENT ON COLUMN "customer"."card_type" IS 'Vé Lượt: Trả theo chuyến, Vé Tháng: Không giới hạn';
COMMENT ON COLUMN "customer"."status" IS 'ACTIVE: Đang hoạt động, EXPIRED: Hết hạn, BLOCKED: Bị khóa';

-- ===============================================
-- END OF SCHEMA
-- ===============================================

