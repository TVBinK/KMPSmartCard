package ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.jetbrains.skia.Image
import java.io.File

/**
 * Load icon từ resources hoặc file system (non-composable helper)
 */
fun loadIconBitmap(resourcePath: String): ImageBitmap? {
    return try {
        // Thử load từ resources trước
        val classLoader = Thread.currentThread().contextClassLoader
            ?: object {}.javaClass.classLoader
        val resourceStream = classLoader?.getResourceAsStream(resourcePath)
        if (resourceStream != null) {
            val imageBytes = resourceStream.readBytes()
            resourceStream.close()
            Image.makeFromEncoded(imageBytes).asImageBitmap()
        } else {
            // Nếu không có trong resources, thử load từ file system
            val file = File(resourcePath)
            if (file.exists()) {
                val imageBytes = file.readBytes()
                Image.makeFromEncoded(imageBytes).asImageBitmap()
            } else {
                null
            }
        }
    } catch (e: Exception) {
        println("⚠️ Không thể load icon từ: $resourcePath - ${e.message}")
        null
    }
}

/**
 * Load icon từ resources hoặc file system (composable wrapper)
 */
@Composable
fun loadIconFromResource(resourcePath: String): ImageBitmap? {
    return remember(resourcePath) {
        loadIconBitmap(resourcePath)
    }
}

