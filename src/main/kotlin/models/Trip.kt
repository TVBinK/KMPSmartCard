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
     * Thời gian còn lại để chuyển tuyến (phút)
     */
    fun remainingTransferTime(): Long {
        val now = LocalDateTime.now()
        val minutesSinceTapOn = ChronoUnit.MINUTES.between(tapOn.timestamp, now)
        return (30 - minutesSinceTapOn).coerceAtLeast(0)
    }
}

