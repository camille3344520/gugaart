package com.gugaart.ui.data

import androidx.compose.ui.text.font.FontFamily
import java.io.File

actual fun fileFontFamily(path: String): FontFamily? = try {
    val f = File(path)
    if (f.isFile) FontFamily(androidx.compose.ui.text.platform.Font(f)) else null
} catch (_: Exception) {
    null
}
