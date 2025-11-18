package database

import models.Customer
import models.CustomerType
import models.CardType
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
            
            println("✅ Tables created successfully")
        }
    }
    
    // ==================== CUSTOMER OPERATIONS ====================
    
    /**
     * Thêm hoặc cập nhật khách hàng (UPSERT)
     */
    fun insertCustomer(customer: Customer): Boolean {
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
            val sql = """
                INSERT INTO customer (card_id, full_name, customer_type, card_type, balance, 
                                     expiry_date, status, pin_code, linked_customer_id, photo_bytes, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            
            try {
                connection?.prepareStatement(sql)?.use { stmt ->
                    stmt.setString(1, customer.cardId)
                    stmt.setString(2, customer.fullName)
                    stmt.setString(3, customer.customerType.name)
                    stmt.setString(4, customer.cardType.name)
                    stmt.setDouble(5, customer.balance)
                    stmt.setString(6, customer.expiryDate.toString())
                    stmt.setString(7, if (customer.isValid()) "ACTIVE" else "EXPIRED")
                    stmt.setString(8, "****") // Không lưu PIN thật
                    stmt.setString(9, customer.linkedCustomerCode ?: "")
                    if (customer.photoBytes != null && customer.photoBytes.isNotEmpty()) {
                        stmt.setBytes(10, customer.photoBytes)
                        println("   📷 Photo attached (${customer.photoBytes.size} bytes)")
                    } else {
                        stmt.setNull(10, java.sql.Types.BLOB)
                        println("   📷 No photo attached")
                    }
                    stmt.setString(11, "SYSTEM")
                    
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
     */
    fun updateCustomer(customer: Customer): Boolean {
        val sql = """
            UPDATE customer 
            SET full_name = ?, customer_type = ?, card_type = ?, balance = ?,
                expiry_date = ?, status = ?, linked_customer_id = ?, photo_bytes = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE card_id = ?
        """.trimIndent()
        
        return try {
            connection?.prepareStatement(sql)?.use { stmt ->
                stmt.setString(1, customer.fullName)
                stmt.setString(2, customer.customerType.name)
                stmt.setString(3, customer.cardType.name)
                stmt.setDouble(4, customer.balance)
                stmt.setString(5, customer.expiryDate.toString())
                stmt.setString(6, if (customer.isValid()) "ACTIVE" else "EXPIRED")
                stmt.setString(7, customer.linkedCustomerCode)
                if (customer.photoBytes != null) {
                    stmt.setBytes(8, customer.photoBytes)
                } else {
                    stmt.setNull(8, java.sql.Types.BLOB)
                }
                stmt.setString(9, customer.cardId)
                
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
        
        // Handle photo_bytes safely (có thể không tồn tại trong schema cũ)
        val photoBytes = try {
            rs.getBytes("photo_bytes")
        } catch (e: SQLException) {
            println("⚠️ Column photo_bytes not found, using null")
            null
        }
        
        return Customer(
            id = rs.getString("card_id"),
            cardId = rs.getString("card_id"),
            fullName = rs.getString("full_name"),
            customerType = CustomerType.valueOf(rs.getString("customer_type")),
            cardType = CardType.valueOf(rs.getString("card_type")),
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
}

