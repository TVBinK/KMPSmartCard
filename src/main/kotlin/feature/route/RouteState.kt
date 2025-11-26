package feature.route

import core.model.BusRoute

/**
 * State của RouteScreen
 */
data class RouteState(
    val selectedRoute: BusRoute? = null,
    val startPoint: String = "",
    val endPoint: String = "",
    val statusMessage: String = ""
)

