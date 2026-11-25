package com.gugaart.ui.data

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File

actual fun fileFontFamily(path: String): FontFamily? = try {
    val f = File(path)
    if (f.isFile) FontFamily(Font(f)) else null
} catch (_: Exception) {
    null
}

actual fun readFontFile(path: String): ByteArray? = try {
    File(path).takeIf { it.isFile && it.length() <= ImportedFontStore.MAX_BYTES }?.readBytes()
} catch (_: Exception) {
    null
}
