package ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import models.*
import ui.components.*
import java.awt.Desktop
import java.net.URI

/**
 * Màn hình Lộ Trình - Xem tuyến đường xe buýt
 * Cho phép chọn tuyến xe buýt và mở Google Maps trong trình duyệt
 */
@Composable
fun RouteDialog(
    onDismiss: () -> Unit,
    onRouteSelected: (String, String, String) -> Unit  // routeName, startPoint, endPoint
) {
    var selectedRoute by remember { mutableStateOf<BusRoute?>(null) }
    var startPoint by remember { mutableStateOf("") }
    var endPoint by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    
    // Danh sách tuyến xe buýt Hà Nội
    val availableRoutes = remember {
        listOf(
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
    }
    
    // Điểm dừng của tuyến được chọn
    val routeStations = remember(selectedRoute) {
        selectedRoute?.stations ?: emptyList()
    }
    
    Dialog(onDismissRequest = onDismiss) {
        CustomCard(
            modifier = Modifier
                .width(1400.dp)
                .heightIn(max = 900.dp)
        ) {
            // Header
            DialogHeader(
                title = "Lộ Trình",
                onClose = onDismiss
            )
            
            CustomDivider()
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Xem tuyến đường xe buýt",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2196F3)
                )
                
                // Chọn tuyến
                CustomDropdown(
                    label = "Chọn tuyến xe buýt *",
                    items = availableRoutes,
                    selectedItem = selectedRoute ?: BusRoute("", "", emptyList()),
                    onItemSelected = { 
                        selectedRoute = it
                        // Tự động điền điểm đi và điểm đến từ tuyến
                        if (it.stations.isNotEmpty()) {
                            startPoint = it.stations.first()
                            endPoint = it.stations.last()
                        }
                    },
                    itemLabel = { "${it.id} - ${it.name}" }
                )
                
                // Chọn điểm đi và điểm đến
                if (selectedRoute != null && routeStations.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CustomDropdown(
                            label = "Điểm đi *",
                            items = routeStations,
                            selectedItem = startPoint.ifEmpty { routeStations.first() },
                            onItemSelected = { startPoint = it },
                            itemLabel = { it },
                            modifier = Modifier.weight(1f)
                        )
                        
                        CustomDropdown(
                            label = "Điểm đến *",
                            items = routeStations.filter { it != startPoint },
                            selectedItem = endPoint.ifEmpty { routeStations.last() },
                            onItemSelected = { endPoint = it },
                            itemLabel = { it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Thông tin tuyến đường
                if (selectedRoute != null && startPoint.isNotEmpty() && endPoint.isNotEmpty()) {
                    CustomCard(backgroundColor = Color(0xFFE3F2FD)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoLabel(
                                label = "Tuyến",
                                value = selectedRoute!!.name,
                                valueFontWeight = FontWeight.Bold
                            )
                            InfoLabel(
                                label = "Điểm đi",
                                value = startPoint
                            )
                            InfoLabel(
                                label = "Điểm đến",
                                value = endPoint
                            )
                        }
                    }
                    
                    // Link mở Google Maps trong trình duyệt
                    val mapUrl = remember(startPoint, endPoint) {
                        val origin = startPoint.replace(" ", "+")
                        val destination = endPoint.replace(" ", "+")
                        "https://www.google.com/maps/dir/$origin+Hanoi+Vietnam/$destination+Hanoi+Vietnam"
                    }
                    
                    Button(
                        onClick = {
                            try {
                                Desktop.getDesktop().browse(URI(mapUrl))
                            } catch (e: Exception) {
                                statusMessage = "❌ Không thể mở trình duyệt"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Mở trong trình duyệt", color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
            
            // Thông báo trạng thái
            if (statusMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    backgroundColor = if (statusMessage.contains("✓")) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ) {
                    Text(
                        text = statusMessage,
                        modifier = Modifier.padding(12.dp),
                        color = if (statusMessage.contains("✓")) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
                }
            }
            
            CustomDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CancelButton(
                    text = "Hủy",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                
                ConfirmButton(
                    text = "Đóng",
                    onClick = {
                        if (selectedRoute != null && startPoint.isNotEmpty() && endPoint.isNotEmpty()) {
                            onRouteSelected(selectedRoute!!.name, startPoint, endPoint)
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

