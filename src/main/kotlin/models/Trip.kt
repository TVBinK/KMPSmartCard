package models

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Model thông tin tuyến xe bus
 */
data class BusRoute(
    val id: String,
    val name: String,
    val stations: List<String> = emptyList()
)

/**
 * Model thông tin quẹt thẻ lên xe
 */
data class TapOn(
    val cardId: String,
    val routeId: String,
    val routeName: String,
    val stationName: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * Model thông tin quẹt thẻ xuống xe
 */
data class TapOff(
    val cardId: String,
    val routeId: String,
    val routeName: String,
    val stationName: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * Model chuyến đi hoàn chỉnh
 */
data class Trip(
    val id: String = "",
    val cardId: String = "",
    val tapOn: TapOn,
    val tapOff: TapOff? = null,
    val numberOfStops: Int = 0,
    val fare: Double = 0.0,
    val isCompleted: Boolean = false
) {
    /**
     * Tính số chặng dựa trên thời gian hoặc vị trí
     */
    fun calculateStops(): Int {
        if (tapOff == null) return 0
        
        // Tính số phút giữa 2 lần quẹt
        val minutes = ChronoUnit.MINUTES.between(tapOn.timestamp, tapOff.timestamp)
        
        // Giả định: mỗi 5 phút = 1 chặng (có thể điều chỉnh)
        return (minutes / 5).toInt().coerceAtLeast(1)
    }

    /**
     * Tính cước phí dựa trên số chặng
     */
    fun calculateFare(pricePerStop: Double = 3000.0, basePrice: Double = 5000.0): Double {
        if (tapOff == null) return 0.0
        
        val stops = calculateStops()
        return basePrice + (stops * pricePerStop)
    }

    /**
     * Kiểm tra có thể chuyển tuyến không
     * (trong vòng 30 phút kể từ lúc quẹt lên)
     */
    fun canTransfer(): Boolean {
        val now = LocalDateTime.now()
        val minutesSinceTapOn = ChronoUnit.MINUTES.between(tapOn.timestamp, now)
        return minutesSinceTapOn <= 30
    }

    /**
     * Thời gian còn lại để chuyển tuyến (phút)
     */
    fun remainingTransferTime(): Long {
        val now = LocalDateTime.now()
        val minutesSinceTapOn = ChronoUnit.MINUTES.between(tapOn.timestamp, now)
        return (30 - minutesSinceTapOn).coerceAtLeast(0)
    }
}

