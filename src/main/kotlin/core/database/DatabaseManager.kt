package core.database

import core.model.Customer
import core.model.CardType
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Types
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
    
    // ==================== CONSTANTS ====================
    
    private const val DB_PATH = "bus_card_management.db"
    private const val JDBC_URL = "jdbc:sqlite:$DB_PATH"
    private const val SQLITE_DRIVER = "org.sqlite.JDBC"
    private const val DEFAULT_STATUS = "ACTIVE"
    private const val MASKED_PIN = "****"
    private const val CONNECTION_TIMEOUT = 5
    
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    
    private var connection: Connection? = null
    
    // ==================== INITIALIZATION ====================
    
    /**
     * Khởi tạo database và tạo các bảng nếu chưa có
     */
    fun initialize() {
        try {
            Class.forName(SQLITE_DRIVER)
            connection = DriverManager.getConnection(JDBC_URL)
            logInfo("Kết nối thành công đến SQLite database: $DB_PATH")
            createTables()
            logInfo("Khởi tạo database thành công")
        } catch (e: SQLException) {
            logError("Lỗi khởi tạo database", e)
        } catch (e: ClassNotFoundException) {
            logError("Không tìm thấy SQLite JDBC driver", e)
        }
    }
    
    /**
     * Tạo các bảng trong database
     */
    private fun createTables() {
        connection?.createStatement()?.use { statement ->
            // Tạo các bảng chính
            statement.execute(getCreateCustomerTableSQL())
            statement.execute(getCreateBusRouteTableSQL())
            statement.execute(getCreateTransactionTableSQL())
            statement.execute(getCreateTripHistoryTableSQL())
            statement.execute(getCreateRouteHistoryTableSQL())
            
            // Tạo indexes
            createIndexes(statement)
            
            // Migration: Thêm các cột mới nếu chưa có
            migrateCustomerTable(statement)
            
            logInfo("Tạo bảng thành công")
        }
    }
    
    private fun getCreateCustomerTableSQL(): String = """
        CREATE TABLE IF NOT EXISTS customer (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            card_id TEXT UNIQUE NOT NULL,
            full_name TEXT NOT NULL,
            card_type TEXT NOT NULL,
            balance REAL DEFAULT 0,
            expiry_date TEXT NOT NULL,
            status TEXT DEFAULT '$DEFAULT_STATUS',
            pin_code TEXT,
            picture_url TEXT,
            photo_bytes BLOB,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP,
            updated_at TEXT DEFAULT CURRENT_TIMESTAMP
        )
    """.trimIndent()
    
    private fun getCreateBusRouteTableSQL(): String = """
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
    
    private fun getCreateTransactionTableSQL(): String = """
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
            FOREIGN KEY (customer_id) REFERENCES customer(id)
        )
    """.trimIndent()
    
    private fun getCreateTripHistoryTableSQL(): String = """
        CREATE TABLE IF NOT EXISTS trip_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            customer_id INTEGER NOT NULL,
            card_id TEXT NOT NULL,
            route_id TEXT,
            tap_location TEXT,
            tap_time TEXT,
            fare_amount REAL DEFAULT 0,
            created_at TEXT DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (customer_id) REFERENCES customer(id)
        )
    """.trimIndent()
    
    private fun getCreateRouteHistoryTableSQL(): String = """
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
    
    private fun createIndexes(statement: java.sql.Statement) {
        val indexes = listOf(
            "CREATE INDEX IF NOT EXISTS idx_customer_card_id ON customer(card_id)",
            "CREATE INDEX IF NOT EXISTS idx_customer_status ON customer(status)",
            "CREATE INDEX IF NOT EXISTS idx_transaction_card_id ON card_transaction(card_id)",
            "CREATE INDEX IF NOT EXISTS idx_trip_card_id ON trip_history(card_id)",
            "CREATE INDEX IF NOT EXISTS idx_route_history_card_id ON route_history(card_id)"
        )
        indexes.forEach { statement.execute(it) }
    }
    
    /**
     * Migration: Thêm các cột mới vào bảng customer (backward compatible)
     */
    private fun migrateCustomerTable(statement: java.sql.Statement) {
        val newColumns = listOf("cccd", "dob", "address", "phone")
        addColumnsIfNotExist(statement, "customer", newColumns)
    }
    
    /**
     * Helper: Thêm các cột vào bảng nếu chưa tồn tại
     */
    private fun addColumnsIfNotExist(statement: java.sql.Statement, tableName: String, columns: List<String>) {
        columns.forEach { columnName ->
            try {
                statement.execute("ALTER TABLE $tableName ADD COLUMN $columnName TEXT")
                logInfo("Đã thêm cột: $tableName.$columnName")
            } catch (e: SQLException) {
                if (e.message?.contains("duplicate column", ignoreCase = true) == true) {
                    // Cột đã tồn tại, bỏ qua
                } else {
                    logError("Lỗi thêm cột $tableName.$columnName", e)
                }
            }
        }
    }
    
    // ==================== CUSTOMER OPERATIONS ====================
    
    /**
     * Thêm hoặc cập nhật khách hàng (UPSERT)
     */
    fun insertCustomer(customer: Customer): Boolean {
        if (!ensureConnection()) return false
        
        val existing = getCustomerByCardId(customer.cardId)
        return if (existing != null) {
            logInfo("Khách hàng ${customer.cardId} đã tồn tại, đang cập nhật...")
            updateCustomer(customer)
        } else {
            logInfo("Thêm khách hàng mới: ${customer.cardId}")
            performInsertCustomer(customer)
        }
    }
    
    private fun performInsertCustomer(customer: Customer): Boolean {
        val sql = """
            INSERT INTO customer (card_id, full_name, cccd, dob, address, phone, card_type, balance, 
                                 expiry_date, status, pin_code, photo_bytes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        return try {
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, customer.cardId)
                stmt.setString(2, customer.fullName)
                stmt.setString(3, customer.cccd)
                stmt.setString(4, customer.dob)
                stmt.setString(5, customer.address)
                stmt.setString(6, customer.phone)
                stmt.setString(7, customer.cardType.name)
                stmt.setDouble(8, customer.balance)
                stmt.setString(9, customer.expiryDate.toString())
                stmt.setString(10, DEFAULT_STATUS)
                stmt.setString(11, MASKED_PIN)
                setPhotoBytes(stmt, 12, customer.photoBytes)
                
                val rowsAffected = stmt.executeUpdate()
                logInfo("Đã thêm khách hàng: ${customer.cardId} (rows: $rowsAffected)")
                rowsAffected > 0
            } ?: false
        } catch (e: SQLException) {
            logError("SQLException khi thêm khách hàng", e)
            false
        } catch (e: Exception) {
            logError("Lỗi không mong đợi khi thêm khách hàng", e)
            false
        }
    }
    
    private fun setPhotoBytes(stmt: java.sql.PreparedStatement, index: Int, photoBytes: ByteArray?) {
        if (photoBytes != null && photoBytes.isNotEmpty()) {
            stmt.setBytes(index, photoBytes)
            logInfo("   Đã đính kèm ảnh (${photoBytes.size} bytes)")
        } else {
            stmt.setNull(index, Types.BLOB)
        }
    }
    
    /**
     * Cập nhật thông tin khách hàng
     */
    fun updateCustomer(customer: Customer): Boolean {
        val sql = """
            UPDATE customer 
            SET full_name = ?, cccd = ?, dob = ?, address = ?, phone = ?, card_type = ?, balance = ?,
                expiry_date = ?, status = ?, photo_bytes = ?,
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
                stmt.setString(6, customer.cardType.name)
                stmt.setDouble(7, customer.balance)
                stmt.setString(8, customer.expiryDate.toString())
                stmt.setString(9, DEFAULT_STATUS)
                setPhotoBytes(stmt, 10, customer.photoBytes)
                stmt.setString(11, customer.cardId)
                
                val rowsAffected = stmt.executeUpdate()
                logInfo("Đã cập nhật khách hàng: ${customer.cardId} (rows: $rowsAffected)")
                rowsAffected > 0
            } ?: false
        } catch (e: SQLException) {
            logError("Lỗi cập nhật khách hàng", e)
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
                stmt.executeQuery(sql).use { rs ->
                    while (rs.next()) {
                        customers.add(mapResultSetToCustomer(rs))
                    }
                }
            }
        } catch (e: SQLException) {
            logError("Lỗi lấy danh sách khách hàng", e)
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
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        mapResultSetToCustomer(rs)
                    } else null
                }
            }
        } catch (e: SQLException) {
            logError("Lỗi lấy thông tin khách hàng", e)
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
                stmt.executeUpdate() > 0
            } ?: false
        } catch (e: SQLException) {
            logError("Lỗi cập nhật số dư", e)
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
                logInfo("Đã xóa khách hàng: $cardId (rows: $rowsAffected)")
                rowsAffected > 0
            } ?: false
        } catch (e: SQLException) {
            logError("Lỗi xóa khách hàng", e)
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
                                    balance_before, balance_after, description)
            SELECT id, ?, ?, ?, ?, ?, ?
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
                stmt.setString(7, cardId)
                stmt.executeUpdate() > 0
            } ?: false
        } catch (e: SQLException) {
            logError("Lỗi thêm giao dịch", e)
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
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        transactions.add(mapResultSetToTransaction(rs))
                    }
                }
            }
        } catch (e: SQLException) {
            logError("Lỗi tải giao dịch", e)
        }
        
        return transactions
    }
    
    private fun mapResultSetToTransaction(rs: ResultSet): Map<String, Any> = mapOf(
        "id" to rs.getInt("id"),
        "transaction_type" to rs.getString("transaction_type"),
        "amount" to rs.getDouble("amount"),
        "balance_before" to rs.getDouble("balance_before"),
        "balance_after" to rs.getDouble("balance_after"),
        "description" to rs.getString("description"),
        "transaction_date" to rs.getString("transaction_date")
    )
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Map ResultSet sang Customer object
     */
    private fun mapResultSetToCustomer(rs: ResultSet): Customer {
        val cardId = rs.getString("card_id")
        
        val photoBytes = getBytesSafely(rs, "photo_bytes")
        val cccd = getStringSafely(rs, "cccd")
        val dob = getStringSafely(rs, "dob")
        val address = getStringSafely(rs, "address")
        val phone = getStringSafely(rs, "phone")
        
        val cardTypeValue = getCardTypeSafely(rs)
        
        return Customer(
            id = cardId,
            cardId = cardId,
            fullName = rs.getString("full_name"),
            cccd = cccd,
            dob = dob,
            address = address,
            phone = phone,
            cardType = cardTypeValue,
            balance = rs.getDouble("balance"),
            expiryDate = LocalDate.parse(rs.getString("expiry_date"), dateFormatter),
            photoPath = rs.getString("picture_url") ?: "",
            photoBytes = photoBytes
        )
    }
    
    private fun getBytesSafely(rs: ResultSet, columnName: String): ByteArray? {
        return try {
            rs.getBytes(columnName)
        } catch (e: SQLException) {
            null
        }
    }
    
    private fun getStringSafely(rs: ResultSet, columnName: String): String {
        return try {
            rs.getString(columnName) ?: ""
        } catch (e: SQLException) {
            ""
        }
    }
    
    private fun getCardTypeSafely(rs: ResultSet): CardType {
        return try {
            val cardTypeStr = rs.getString("card_type")
            // Migration: Chuyển SINGLE_TRIP thành NORMAL
            if (cardTypeStr == "SINGLE_TRIP") CardType.NORMAL else CardType.valueOf(cardTypeStr)
        } catch (e: Exception) {
            CardType.NORMAL
        }
    }
    
    private fun ensureConnection(): Boolean {
        if (connection == null || !isConnected()) {
            logError("Kết nối database không khả dụng", null)
            return false
        }
        return true
    }
    
    /**
     * Đóng kết nối database
     */
    fun close() {
        try {
            connection?.close()
            logInfo("Đã đóng kết nối database")
        } catch (e: SQLException) {
            logError("Lỗi đóng database", e)
        }
    }
    
    /**
     * Kiểm tra kết nối database
     */
    fun isConnected(): Boolean {
        return try {
            connection?.isValid(CONNECTION_TIMEOUT) ?: false
        } catch (e: SQLException) {
            false
        }
    }
    
    // ==================== LOGGING HELPERS ====================
    
    private fun logInfo(message: String) {
        println(message)
    }
    
    private fun logError(message: String, exception: Throwable?) {
        println("$message: ${exception?.message ?: ""}")
        exception?.printStackTrace()
    }
}
