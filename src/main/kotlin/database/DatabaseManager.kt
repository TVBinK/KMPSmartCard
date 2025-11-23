package database

import models.Customer
import models.CustomerType
import models.CardType
import security.SecurityUtils
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * DatabaseManager - Quản lý kết nối và thao tác với SQLite database
 * 
 * Chức năng:
 * - Khởi tạo database và các bảng
 * - CRUD operations cho Customer
 * - CRUD operations cho Transaction
 * - CRUD operations cho Trip History
 */
object DatabaseManager {
    
    private const val DB_PATH = "bus_card_management.db"
    private const val JDBC_URL = "jdbc:sqlite:$DB_PATH"
    
    private var connection: Connection? = null
    
    /**
     * Khởi tạo database và tạo các bảng nếu chưa có
     */
    fun initialize() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC")
            
            // Kết nối database
            connection = DriverManager.getConnection(JDBC_URL)
            println("✅ Connected to SQLite database: $DB_PATH")
            
            // Tạo các bảng
            createTables()
            
            println("✅ Database initialized successfully")
        } catch (e: SQLException) {
            println("❌ Error initializing database: ${e.message}")
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            println("❌ SQLite JDBC driver not found: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Tạo các bảng trong database
     */
    private fun createTables() {
        val createCustomerTable = """
            CREATE TABLE IF NOT EXISTS customer (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                card_id TEXT UNIQUE NOT NULL,
                full_name TEXT NOT NULL,
                customer_type TEXT NOT NULL,
                card_type TEXT NOT NULL,
                balance REAL DEFAULT 0,
                expiry_date TEXT NOT NULL,
                status TEXT DEFAULT 'ACTIVE',
                pin_code TEXT,
                public_key TEXT,
                picture_url TEXT,
                photo_bytes BLOB,
                linked_customer_id TEXT,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                created_by TEXT,
                notes TEXT
            )
        """.trimIndent()
        
        val createBusRouteTable = """
            CREATE TABLE IF NOT EXISTS bus_route (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                route_id TEXT UNIQUE NOT NULL,
                route_name TEXT NOT NULL,
                description TEXT,
                start_location TEXT,
                end_location TEXT,
                total_stops INTEGER DEFAULT 0,
                base_fare REAL DEFAULT 5000,
                fare_per_stop REAL DEFAULT 3000,
                is_active INTEGER DEFAULT 1,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent()
        
        val createTransactionTable = """
            CREATE TABLE IF NOT EXISTS card_transaction (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER NOT NULL,
                card_id TEXT NOT NULL,
                transaction_type TEXT NOT NULL,
                amount REAL NOT NULL,
                balance_before REAL,
                balance_after REAL,
                description TEXT,
                transaction_date TEXT DEFAULT CURRENT_TIMESTAMP,
                created_by TEXT,
                FOREIGN KEY (customer_id) REFERENCES customer(id)
            )
        """.trimIndent()
        
        val createTripHistoryTable = """
            CREATE TABLE IF NOT EXISTS trip_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                customer_id INTEGER NOT NULL,
                card_id TEXT NOT NULL,
                route_id_on TEXT,
                tap_on_location TEXT,
                tap_on_time TEXT,
                route_id_off TEXT,
                tap_off_location TEXT,
                tap_off_time TEXT,
                stops_count INTEGER DEFAULT 0,
                fare_amount REAL DEFAULT 0,
                is_completed INTEGER DEFAULT 0,
                is_free_transfer INTEGER DEFAULT 0,
                created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (customer_id) REFERENCES customer(id)
            )
        """.trimIndent()
        
        connection?.createStatement()?.use { statement ->
            statement.execute(createCustomerTable)
            statement.execute(createBusRouteTable)
            statement.execute(createTransactionTable)
            statement.execute(createTripHistoryTable)
            
            // Tạo indexes
            statement.execute("CREATE INDEX IF NOT EXISTS idx_customer_card_id ON customer(card_id)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_customer_status ON customer(status)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_transaction_card_id ON card_transaction(card_id)")
            statement.execute("CREATE INDEX IF NOT EXISTS idx_trip_card_id ON trip_history(card_id)")
            
            // Migration: Thêm các cột mới nếu chưa có (backward compatible)
            migrateCustomerTable(statement)
            
            // Migration: Thêm các cột mã hóa
            migrateEncryptionColumns(statement)
            
            // Tạo bảng route_history cho module Lộ Trình
            val createRouteHistoryTable = """
                CREATE TABLE IF NOT EXISTS route_history (
                    route_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    card_id TEXT NOT NULL,
                    route_name TEXT NOT NULL,
                    start_point TEXT NOT NULL,
                    end_point TEXT NOT NULL,
                    timestamp TEXT DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (card_id) REFERENCES customer(card_id)
                )
            """.trimIndent()
            statement.execute(createRouteHistoryTable)
            statement.execute("CREATE INDEX IF NOT EXISTS idx_route_history_card_id ON route_history(card_id)")
            
            println("✅ Tables created successfully")
        }
    }
    
    /**
     * Migration: Thêm các cột mới vào bảng customer (backward compatible)
     */
    private fun migrateCustomerTable(statement: java.sql.Statement) {
        try {
            // Kiểm tra và thêm cột cccd
            try {
                statement.execute("ALTER TABLE customer ADD COLUMN cccd TEXT")
                println("✅ Added column: cccd")
            } catch (e: SQLException) {
                if (e.message?.contains("duplicate column") == true) {
                    println("⚠️ Column cccd already exists")
                }
            }
            
            // Kiểm tra và thêm cột dob
            try {
                statement.execute("ALTER TABLE customer ADD COLUMN dob TEXT")
                println("✅ Added column: dob")
            } catch (e: SQLException) {
                if (e.message?.contains("duplicate column") == true) {
                    println("⚠️ Column dob already exists")
                }
            }
            
            // Kiểm tra và thêm cột address
            try {
                statement.execute("ALTER TABLE customer ADD COLUMN address TEXT")
                println("✅ Added column: address")
            } catch (e: SQLException) {
                if (e.message?.contains("duplicate column") == true) {
                    println("⚠️ Column address already exists")
                }
            }
            
            // Kiểm tra và thêm cột phone
            try {
                statement.execute("ALTER TABLE customer ADD COLUMN phone TEXT")
                println("✅ Added column: phone")
            } catch (e: SQLException) {
                if (e.message?.contains("duplicate column") == true) {
                    println("⚠️ Column phone already exists")
                }
            }
        } catch (e: Exception) {
            println("⚠️ Migration error: ${e.message}")
        }
    }
    
    /**
     * Migration: Thêm các cột mã hóa cho dữ liệu khách hàng
     */
    private fun migrateEncryptionColumns(statement: java.sql.Statement) {
        val encryptedColumns = listOf(
            "encrypted_full_name",
            "encrypted_cccd",
            "encrypted_dob",
            "encrypted_address",
            "encrypted_phone",
            "public_key",
            "private_key"
        )
        
        encryptedColumns.forEach { columnName ->
            try {
                statement.execute("ALTER TABLE customer ADD COLUMN $columnName TEXT")
                println("✅ Added column: $columnName")
            } catch (e: SQLException) {
                if (e.message?.contains("duplicate column") == true) {
                    println("⚠️ Column $columnName already exists")
                }
            }
        }
    }
    
    // ==================== CUSTOMER OPERATIONS ====================
    
    /**
     * Thêm hoặc cập nhật khách hàng (UPSERT)
     * @param pin Mã PIN để mã hóa dữ liệu (optional, nếu không có thì không mã hóa)
     */
    fun insertCustomer(customer: Customer, pin: String? = null): Boolean {
        println("🔍 Checking if customer exists: ${customer.cardId}")
        
        // Check database connection
        if (connection == null || !isConnected()) {
            println("❌ Database connection is not available!")
            return false
        }
        
        // Check nếu customer đã tồn tại
        val existing = getCustomerByCardId(customer.cardId)
        
        return if (existing != null) {
            // Update customer hiện có
            println("⚠️ Customer ${customer.cardId} already exists, updating...")
            updateCustomer(customer)
        } else {
            // Insert customer mới
            println("➕ Inserting new customer: ${customer.cardId}")
            
            // Mã hóa dữ liệu nếu có PIN
            val encryptedData = if (pin != null && pin.isNotEmpty()) {
                try {
                    val encrypted = SecurityUtils.encryptCustomerData(
                        customer.fullName,
                        customer.cccd,
                        customer.dob,
                        customer.address,
                        customer.phone,
                        pin
                    )
                    println("   🔐 Data encrypted with PIN")
                    encrypted
                } catch (e: Exception) {
                    println("   ⚠️ Encryption failed: ${e.message}")
                    null
                }
            } else {
                null
            }
            
            // Tạo cặp khóa RSA cho giao dịch
            val keyPair = if (pin != null && pin.isNotEmpty()) {
                try {
                    SecurityUtils.generateRSAKeyPair()
                } catch (e: Exception) {
                    println("   ⚠️ RSA key generation failed: ${e.message}")
                    null
                }
            } else {
                null
            }
            
            val sql = """
                INSERT INTO customer (card_id, full_name, cccd, dob, address, phone, customer_type, card_type, balance, 
                                     expiry_date, status, pin_code, linked_customer_id, photo_bytes, created_by,
                                     encrypted_full_name, encrypted_cccd, encrypted_dob, encrypted_address, encrypted_phone,
                                     public_key, private_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            
            try {
                connection?.prepareStatement(sql)?.use { stmt ->
                    stmt.setString(1, customer.cardId)
                    stmt.setString(2, customer.fullName) // Giữ nguyên để backward compatibility
                    stmt.setString(3, customer.cccd)
                    stmt.setString(4, customer.dob)
                    stmt.setString(5, customer.address)
                    stmt.setString(6, customer.phone)
                    stmt.setString(7, customer.customerType.name)
                    stmt.setString(8, customer.cardType.name)
                    stmt.setDouble(9, customer.balance)
                    stmt.setString(10, customer.expiryDate.toString())
                    stmt.setString(11, if (customer.isValid()) "ACTIVE" else "EXPIRED")
                    stmt.setString(12, "****") // Không lưu PIN thật
                    stmt.setString(13, customer.linkedCustomerCode ?: "")
                    if (customer.photoBytes != null && customer.photoBytes.isNotEmpty()) {
                        stmt.setBytes(14, customer.photoBytes)
                        println("   📷 Photo attached (${customer.photoBytes.size} bytes)")
                    } else {
                        stmt.setNull(14, java.sql.Types.BLOB)
                        println("   📷 No photo attached")
                    }
                    stmt.setString(15, "SYSTEM")
                    
                    // Lưu dữ liệu đã mã hóa
                    if (encryptedData != null) {
                        stmt.setString(16, SecurityUtils.bytesToBase64(encryptedData["fullName"]!!))
                        stmt.setString(17, SecurityUtils.bytesToBase64(encryptedData["cccd"]!!))
                        stmt.setString(18, SecurityUtils.bytesToBase64(encryptedData["dob"]!!))
                        stmt.setString(19, SecurityUtils.bytesToBase64(encryptedData["address"]!!))
                        stmt.setString(20, SecurityUtils.bytesToBase64(encryptedData["phone"]!!))
                    } else {
                        stmt.setNull(16, java.sql.Types.VARCHAR)
                        stmt.setNull(17, java.sql.Types.VARCHAR)
                        stmt.setNull(18, java.sql.Types.VARCHAR)
                        stmt.setNull(19, java.sql.Types.VARCHAR)
                        stmt.setNull(20, java.sql.Types.VARCHAR)
                    }
                    
                    // Lưu khóa RSA
                    if (keyPair != null) {
                        stmt.setString(21, SecurityUtils.publicKeyToBase64(keyPair.public))
                        stmt.setString(22, SecurityUtils.privateKeyToBase64(keyPair.private))
                        println("   🔑 RSA keys generated and saved")
                    } else {
                        stmt.setNull(21, java.sql.Types.VARCHAR)
                        stmt.setNull(22, java.sql.Types.VARCHAR)
                    }
                    
                    println("   💾 Executing SQL INSERT...")
                    val rowsAffected = stmt.executeUpdate()
                    println("✅ Customer inserted: ${customer.cardId} (rows: $rowsAffected)")
                    rowsAffected > 0
                } ?: run {
                    println("❌ Failed to prepare SQL statement")
                    false
                }
            } catch (e: SQLException) {
                println("❌ SQLException inserting customer: ${e.message}")
                println("   SQL State: ${e.sqlState}")
                println("   Error Code: ${e.errorCode}")
                e.printStackTrace()
                false
            } catch (e: Exception) {
                println("❌ Unexpected error inserting customer: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }
    
    /**
     * Cập nhật thông tin khách hàng
     * @param pin Mã PIN để mã hóa dữ liệu (optional, nếu không có thì không mã hóa)
     */
    fun updateCustomer(customer: Customer, pin: String? = null): Boolean {
        // Mã hóa dữ liệu nếu có PIN
        val encryptedData = if (pin != null && pin.isNotEmpty()) {
            try {
                SecurityUtils.encryptCustomerData(
                    customer.fullName,
                    customer.cccd,
                    customer.dob,
                    customer.address,
                    customer.phone,
                    pin
                )
            } catch (e: Exception) {
                println("   ⚠️ Encryption failed: ${e.message}")
                null
            }
        } else {
            null
        }
        
        val sql = """
            UPDATE customer 
            SET full_name = ?, cccd = ?, dob = ?, address = ?, phone = ?, customer_type = ?, card_type = ?, balance = ?,
                expiry_date = ?, status = ?, linked_customer_id = ?, photo_bytes = ?,
                encrypted_full_name = ?, encrypted_cccd = ?, encrypted_dob = ?, encrypted_address = ?, encrypted_phone = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE card_id = ?
        """.trimIndent()
        
        return try {
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, customer.fullName)
                stmt.setString(2, customer.cccd)
                stmt.setString(3, customer.dob)
                stmt.setString(4, customer.address)
                stmt.setString(5, customer.phone)
                stmt.setString(6, customer.customerType.name)
                stmt.setString(7, customer.cardType.name)
                stmt.setDouble(8, customer.balance)
                stmt.setString(9, customer.expiryDate.toString())
                stmt.setString(10, if (customer.isValid()) "ACTIVE" else "EXPIRED")
                stmt.setString(11, customer.linkedCustomerCode)
                if (customer.photoBytes != null) {
                    stmt.setBytes(12, customer.photoBytes)
                } else {
                    stmt.setNull(12, java.sql.Types.BLOB)
                }
                
                // Cập nhật dữ liệu đã mã hóa
                if (encryptedData != null) {
                    stmt.setString(13, SecurityUtils.bytesToBase64(encryptedData["fullName"]!!))
                    stmt.setString(14, SecurityUtils.bytesToBase64(encryptedData["cccd"]!!))
                    stmt.setString(15, SecurityUtils.bytesToBase64(encryptedData["dob"]!!))
                    stmt.setString(16, SecurityUtils.bytesToBase64(encryptedData["address"]!!))
                    stmt.setString(17, SecurityUtils.bytesToBase64(encryptedData["phone"]!!))
                } else {
                    // Giữ nguyên dữ liệu mã hóa cũ nếu không có PIN mới
                    val existing = getCustomerByCardId(customer.cardId)
                    if (existing != null) {
                        // Giữ nguyên các giá trị mã hóa cũ
                        stmt.setString(13, null)
                        stmt.setString(14, null)
                        stmt.setString(15, null)
                        stmt.setString(16, null)
                        stmt.setString(17, null)
                    } else {
                        stmt.setNull(13, java.sql.Types.VARCHAR)
                        stmt.setNull(14, java.sql.Types.VARCHAR)
                        stmt.setNull(15, java.sql.Types.VARCHAR)
                        stmt.setNull(16, java.sql.Types.VARCHAR)
                        stmt.setNull(17, java.sql.Types.VARCHAR)
                    }
                }
                
                stmt.setString(18, customer.cardId)
                
                val rowsAffected = stmt.executeUpdate()
                println("✅ Customer updated: ${customer.cardId} (rows: $rowsAffected)")
                rowsAffected > 0
            } ?: false
        } catch (e: SQLException) {
            println("❌ Error updating customer: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Lấy tất cả khách hàng từ database
     */
    fun getAllCustomers(): List<Customer> {
        val customers = mutableListOf<Customer>()
        val sql = "SELECT * FROM customer ORDER BY created_at DESC"
        
        try {
            connection?.createStatement()?.use { stmt ->
                val rs = stmt.executeQuery(sql)
                while (rs.next()) {
                    customers.add(mapResultSetToCustomer(rs))
                }
            }
            println("✅ Loaded ${customers.size} customers from database")
        } catch (e: SQLException) {
            println("❌ Error loading customers: ${e.message}")
            e.printStackTrace()
        }
        
        return customers
    }
    
    /**
     * Lấy khách hàng theo Card ID
     */
    fun getCustomerByCardId(cardId: String): Customer? {
        val sql = "SELECT * FROM customer WHERE card_id = ?"
        
        return try {
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, cardId)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    mapResultSetToCustomer(rs)
                } else null
            }
        } catch (e: SQLException) {
            println("❌ Error getting customer: ${e.message}")
            null
        }
    }
    
    /**
     * Cập nhật số dư khách hàng
     */
    fun updateCustomerBalance(cardId: String, newBalance: Double): Boolean {
        val sql = """
            UPDATE customer 
            SET balance = ?, updated_at = CURRENT_TIMESTAMP 
            WHERE card_id = ?
        """.trimIndent()
        
        return try {
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setDouble(1, newBalance)
                stmt.setString(2, cardId)
                val rowsAffected = stmt.executeUpdate()
                rowsAffected > 0
            } ?: false
        } catch (e: SQLException) {
            println("❌ Error updating balance: ${e.message}")
            false
        }
    }
    
    /**
     * Xóa khách hàng theo Card ID
     */
    fun deleteCustomer(cardId: String): Boolean {
        val sql = "DELETE FROM customer WHERE card_id = ?"
        
        return try {
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, cardId)
                val rowsAffected = stmt.executeUpdate()
                println("✅ Deleted customer: $cardId (rows: $rowsAffected)")
                rowsAffected > 0
            } ?: false
        } catch (e: SQLException) {
            println("❌ Error deleting customer: ${e.message}")
            false
        }
    }
    
    /**
     * Xóa tất cả dữ liệu trong database
     */
    fun clearAllData(): Boolean {
        return try {
            connection?.createStatement()?.use { stmt ->
                stmt.execute("DELETE FROM trip_history")
                stmt.execute("DELETE FROM card_transaction")
                stmt.execute("DELETE FROM customer")
                stmt.execute("DELETE FROM bus_route")
                println("✅ All data cleared from database")
                true
            } ?: false
        } catch (e: SQLException) {
            println("❌ Error clearing data: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    // ==================== TRANSACTION OPERATIONS ====================
    
    /**
     * Thêm giao dịch mới
     */
    fun insertTransaction(
        cardId: String,
        transactionType: String,
        amount: Double,
        balanceBefore: Double,
        balanceAfter: Double,
        description: String
    ): Boolean {
        val sql = """
            INSERT INTO card_transaction (customer_id, card_id, transaction_type, amount, 
                                    balance_before, balance_after, description, created_by)
            SELECT id, ?, ?, ?, ?, ?, ?, ?
            FROM customer WHERE card_id = ?
        """.trimIndent()
        
        return try {
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, cardId)
                stmt.setString(2, transactionType)
                stmt.setDouble(3, amount)
                stmt.setDouble(4, balanceBefore)
                stmt.setDouble(5, balanceAfter)
                stmt.setString(6, description)
                stmt.setString(7, "SYSTEM")
                stmt.setString(8, cardId)
                
                val rowsAffected = stmt.executeUpdate()
                rowsAffected > 0
            } ?: false
        } catch (e: SQLException) {
            println("❌ Error inserting transaction: ${e.message}")
            false
        }
    }
    
    /**
     * Lấy lịch sử giao dịch theo Card ID
     */
    fun getTransactionsByCardId(cardId: String): List<Map<String, Any>> {
        val transactions = mutableListOf<Map<String, Any>>()
        val sql = """
            SELECT * FROM card_transaction 
            WHERE card_id = ? 
            ORDER BY transaction_date DESC
        """.trimIndent()
        
        try {
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, cardId)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    transactions.add(mapOf(
                        "id" to rs.getInt("id"),
                        "transaction_type" to rs.getString("transaction_type"),
                        "amount" to rs.getDouble("amount"),
                        "balance_before" to rs.getDouble("balance_before"),
                        "balance_after" to rs.getDouble("balance_after"),
                        "description" to rs.getString("description"),
                        "transaction_date" to rs.getString("transaction_date")
                    ))
                }
            }
        } catch (e: SQLException) {
            println("❌ Error loading transactions: ${e.message}")
        }
        
        return transactions
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Map ResultSet sang Customer object
     */
    private fun mapResultSetToCustomer(rs: ResultSet): Customer {
        val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
        
        val cardId = rs.getString("card_id")
        println("🔍 [Database] mapResultSetToCustomer cho card_id: $cardId")
        
        // Handle photo_bytes safely (có thể không tồn tại trong schema cũ)
        val photoBytes = try {
            rs.getBytes("photo_bytes")
        } catch (e: SQLException) {
            println("⚠️ Column photo_bytes not found, using null")
            null
        }
        
        // Đọc các trường mới (có thể null nếu là database cũ)
        val cccd = try { 
            val value = rs.getString("cccd")
            val isNull = value == null
            println("   📖 [Database] Đọc cccd từ DB: '$value' (null: $isNull)")
            value ?: "" 
        } catch (e: SQLException) { 
            println("   ⚠️ [Database] Lỗi đọc cccd: ${e.message}")
            "" 
        }
        val dob = try { 
            val value = rs.getString("dob")
            val isNull = value == null
            println("   📖 [Database] Đọc dob từ DB: '$value' (null: $isNull)")
            value ?: "" 
        } catch (e: SQLException) { 
            println("   ⚠️ [Database] Lỗi đọc dob: ${e.message}")
            "" 
        }
        val address = try { 
            val value = rs.getString("address")
            val isNull = value == null
            println("   📖 [Database] Đọc address từ DB: '$value' (null: $isNull)")
            value ?: "" 
        } catch (e: SQLException) { 
            println("   ⚠️ [Database] Lỗi đọc address: ${e.message}")
            "" 
        }
        val phone = try { 
            val value = rs.getString("phone")
            val isNull = value == null
            println("   📖 [Database] Đọc phone từ DB: '$value' (null: $isNull)")
            value ?: "" 
        } catch (e: SQLException) { 
            println("   ⚠️ [Database] Lỗi đọc phone: ${e.message}")
            "" 
        }
        
        return Customer(
            id = rs.getString("card_id"),
            cardId = rs.getString("card_id"),
            fullName = rs.getString("full_name"),
            cccd = cccd,
            dob = dob,
            address = address,
            phone = phone,
            customerType = CustomerType.valueOf(rs.getString("customer_type")),
            cardType = try {
                val cardTypeStr = rs.getString("card_type")
                // Migration: Chuyển SINGLE_TRIP thành NORMAL
                if (cardTypeStr == "SINGLE_TRIP") CardType.NORMAL else CardType.valueOf(cardTypeStr)
            } catch (e: Exception) {
                CardType.NORMAL // Default nếu lỗi
            },
            balance = rs.getDouble("balance"),
            expiryDate = LocalDate.parse(rs.getString("expiry_date"), dateFormatter),
            linkedCustomerCode = rs.getString("linked_customer_id") ?: "",
            photoPath = rs.getString("picture_url") ?: "",
            photoBytes = photoBytes
        )
    }
    
    /**
     * Đóng kết nối database
     */
    fun close() {
        try {
            connection?.close()
            println("✅ Database connection closed")
        } catch (e: SQLException) {
            println("❌ Error closing database: ${e.message}")
        }
    }
    
    /**
     * Kiểm tra kết nối database
     */
    fun isConnected(): Boolean {
        return try {
            connection?.isValid(5) ?: false
        } catch (e: SQLException) {
            false
        }
    }
    
    // ==================== ROUTE HISTORY OPERATIONS ====================
    
    /**
     * Thêm lịch sử lộ trình
     */
    fun insertRouteHistory(
        cardId: String,
        routeName: String,
        startPoint: String,
        endPoint: String
    ): Boolean {
        val sql = """
            INSERT INTO route_history (card_id, route_name, start_point, end_point, timestamp)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
        """.trimIndent()
        
        return try {
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, cardId)
                stmt.setString(2, routeName)
                stmt.setString(3, startPoint)
                stmt.setString(4, endPoint)
                val rowsAffected = stmt.executeUpdate()
                rowsAffected > 0
            } ?: false
        } catch (e: SQLException) {
            println("❌ Error inserting route history: ${e.message}")
            false
        }
    }
    
    /**
     * Lấy lịch sử lộ trình theo Card ID
     */
    fun getRouteHistoryByCardId(cardId: String): List<Map<String, Any>> {
        val routes = mutableListOf<Map<String, Any>>()
        val sql = """
            SELECT * FROM route_history 
            WHERE card_id = ? 
            ORDER BY timestamp DESC
        """.trimIndent()
        
        try {
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, cardId)
                val rs = stmt.executeQuery()
                while (rs.next()) {
                    routes.add(mapOf(
                        "route_id" to rs.getInt("route_id"),
                        "route_name" to rs.getString("route_name"),
                        "start_point" to rs.getString("start_point"),
                        "end_point" to rs.getString("end_point"),
                        "timestamp" to rs.getString("timestamp")
                    ))
                }
            }
        } catch (e: SQLException) {
            println("❌ Error loading route history: ${e.message}")
        }
        
        return routes
    }
}

