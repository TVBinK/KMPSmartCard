import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import core.database.DatabaseManager
import feature.main.MainApp

/**
 * Entry point của ứng dụng
 * Hệ thống quản lý thẻ xe bus
 */
fun main() = application {
    // Khởi tạo database khi ứng dụng khởi động
    DatabaseManager.initialize()
    
    Window(
        onCloseRequest = {
            // Đóng database connection khi thoát
            DatabaseManager.close()
            exitApplication()
        },
        title = "Hệ thống quản lý thẻ xe bus - Bus Card Management System",
        state = rememberWindowState(
            placement = WindowPlacement.Maximized,
            width = 1400.dp,
            height = 900.dp
        )
    ) {
        MainApp()
    }
}
