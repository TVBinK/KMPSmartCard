package feature.route

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import core.model.BusRoute
import core.ui.components.*
import java.awt.Desktop
import java.net.URI

/**
 * Màn hình Lộ Trình - Xem tuyến đường xe buýt
 * Sử dụng MVVM pattern
 */
@Composable
fun RouteDialog(
    onDismiss: () -> Unit,
    onRouteSelected: (String, String, String) -> Unit  // routeName, startPoint, endPoint
) {
    // Khởi tạo ViewModel
    val routeViewModel = remember { RouteViewModel() }
    val state by routeViewModel.state.collectAsState()
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            routeViewModel.onCleared()
        }
    }
    
    // Điểm dừng của tuyến được chọn
    val routeStations = remember(state.selectedRoute) {
        routeViewModel.getRouteStations()
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
                    items = routeViewModel.availableRoutes,
                    selectedItem = state.selectedRoute ?: BusRoute("", "", emptyList()),
                    onItemSelected = { routeViewModel.selectRoute(it) },
                    itemLabel = { "${it.id} - ${it.name}" }
                )
                
                // Chọn điểm đi và điểm đến
                if (state.selectedRoute != null && routeStations.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CustomDropdown(
                            label = "Điểm đi *",
                            items = routeStations,
                            selectedItem = state.startPoint.ifEmpty { routeStations.first() },
                            onItemSelected = { routeViewModel.updateStartPoint(it) },
                            itemLabel = { it },
                            modifier = Modifier.weight(1f)
                        )
                        
                        CustomDropdown(
                            label = "Điểm đến *",
                            items = routeStations.filter { it != state.startPoint },
                            selectedItem = state.endPoint.ifEmpty { routeStations.last() },
                            onItemSelected = { routeViewModel.updateEndPoint(it) },
                            itemLabel = { it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Thông tin tuyến đường
                if (state.selectedRoute != null && state.startPoint.isNotEmpty() && state.endPoint.isNotEmpty()) {
                    CustomCard(backgroundColor = Color(0xFFE3F2FD)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoLabel(
                                label = "Tuyến",
                                value = state.selectedRoute!!.name,
                                valueFontWeight = FontWeight.Bold
                            )
                            InfoLabel(
                                label = "Điểm đi",
                                value = state.startPoint
                            )
                            InfoLabel(
                                label = "Điểm đến",
                                value = state.endPoint
                            )
                        }
                    }
                    
                    // Link mở Google Maps trong trình duyệt
                    val mapUrl = remember(state.startPoint, state.endPoint) {
                        routeViewModel.getMapUrl()
                    }
                    
                    Button(
                        onClick = {
                            try {
                                Desktop.getDesktop().browse(URI(mapUrl))
                            } catch (e: Exception) {
                                routeViewModel.updateStatusMessage("❌ Không thể mở trình duyệt")
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
            if (state.statusMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    backgroundColor = if (state.statusMessage.contains("✓")) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ) {
                    Text(
                        text = state.statusMessage,
                        modifier = Modifier.padding(12.dp),
                        color = if (state.statusMessage.contains("✓")) Color(0xFF4CAF50) else Color(0xFFF44336)
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
                        if (state.selectedRoute != null && state.startPoint.isNotEmpty() && state.endPoint.isNotEmpty()) {
                            onRouteSelected(state.selectedRoute!!.name, state.startPoint, state.endPoint)
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

