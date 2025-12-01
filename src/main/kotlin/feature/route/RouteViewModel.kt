package feature.route

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import core.model.BusRoute

/**
 * ViewModel cho RouteScreen
 * Quản lý logic chọn tuyến và điểm dừng
 */
class RouteViewModel {
    
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State Flow
    private val _state = MutableStateFlow(RouteState())
    val state: StateFlow<RouteState> = _state.asStateFlow()
    
    // Danh sách tuyến xe buýt Hà Nội
    val availableRoutes = listOf(
        BusRoute("01", "Tuyến 01 - Yên Nghĩa → Bến xe Gia Lâm", listOf("Yên Nghĩa", "Bến xe Gia Lâm")),
        BusRoute("03", "Tuyến 03 - Bến xe Nước Ngầm → Hoàng Quốc Việt", listOf("Bến xe Nước Ngầm", "Hoàng Quốc Việt")),
        BusRoute("07", "Tuyến 07 - Bến xe Yên Nghĩa → Cầu Giấy", listOf("Bến xe Yên Nghĩa", "Cầu Giấy")),
        BusRoute("09", "Tuyến 09 - Kim Mã → Bến xe Mỹ Đình", listOf("Kim Mã", "Bến xe Mỹ Đình")),
        BusRoute("14", "Tuyến 14 - Bến xe Giáp Bát → Long Biên", listOf("Bến xe Giáp Bát", "Long Biên")),
        BusRoute("18", "Tuyến 18 - Bến xe Yên Nghĩa → Bến xe Long Biên", listOf("Bến xe Yên Nghĩa", "Bến xe Long Biên")),
        BusRoute("22", "Tuyến 22 - Sân Bay Nội Bài → Kim Mã", listOf("Sân Bay Nội Bài", "Kim Mã")),
        BusRoute("32", "Tuyến 32 - Bến xe Yên Nghĩa → Bệnh viện E", listOf("Bến xe Yên Nghĩa", "Bệnh viện E")),
        BusRoute("34", "Tuyến 34 - Đại học Công đoàn → Bưu điện Hà Đông", listOf("Đại học Công đoàn", "Bưu điện Hà Đông")),
        BusRoute("40", "Tuyến 40 - Yên Nghĩa → Bến xe Gia Lâm", listOf("Yên Nghĩa", "Bến xe Gia Lâm")),
        BusRoute("86", "Tuyến 86 - Sân Bay Nội Bài → Hoàng Quốc Việt", listOf("Sân Bay Nội Bài", "Hoàng Quốc Việt"))
    )
    
    /**
     * Chọn tuyến
     */
    fun selectRoute(route: BusRoute) {
        val startPoint = if (route.stations.isNotEmpty()) route.stations.first() else ""
        val endPoint = if (route.stations.isNotEmpty()) route.stations.last() else ""
        
        _state.update { 
            it.copy(
                selectedRoute = route,
                startPoint = startPoint,
                endPoint = endPoint
            )
        }
    }
    
    /**
     * Cập nhật điểm đi
     */
    fun updateStartPoint(point: String) {
        _state.update { it.copy(startPoint = point) }
    }
    
    /**
     * Cập nhật điểm đến
     */
    fun updateEndPoint(point: String) {
        _state.update { it.copy(endPoint = point) }
    }
    
    /**
     * Lấy danh sách điểm dừng của tuyến được chọn
     */
    fun getRouteStations(): List<String> {
        return _state.value.selectedRoute?.stations ?: emptyList()
    }
    
    /**
     * Lấy URL Google Maps
     */
    fun getMapUrl(): String {
        val state = _state.value
        if (state.startPoint.isEmpty() || state.endPoint.isEmpty()) {
            return ""
        }
        val startPoint = state.startPoint.replace(" ", "+")
        val endPoint = state.endPoint.replace(" ", "+")
        return "https://www.google.com/maps/dir/$startPoint+Hanoi+Vietnam/$endPoint+Hanoi+Vietnam"
    }
    
    /**
     * Cleanup
     */
    fun onCleared() {
        viewModelScope.cancel()
    }
}

