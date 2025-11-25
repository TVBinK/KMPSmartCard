package utils

/**
 * Constants cho ứng dụng Bus Card Management
 */
object AppConstants {
    // Giá vé
    const val NORMAL_CARD_TAP_AMOUNT = 7000.0
    const val MONTHLY_CARD_PRICE = 100000.0
    
    // Timing
    const val INITIAL_LOAD_DELAY_MS = 500L
    const val STATS_RELOAD_INTERVAL_MS = 10000L
    
    // Chart
    const val CHART_DAYS_COUNT = 7
    
    // Transaction types
    const val TRANSACTION_TYPE_TOP_UP = "TOP_UP"
    const val TRANSACTION_TYPE_TAP = "TAP"
    const val TRANSACTION_TYPE_MONTHLY_PURCHASE = "MONTHLY_PURCHASE"
    const val TRANSACTION_TYPE_EXTEND_MONTHLY = "EXTEND_MONTHLY"
}










