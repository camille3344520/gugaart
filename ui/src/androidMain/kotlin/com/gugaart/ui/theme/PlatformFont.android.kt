package com.gugaart.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import java.io.File

/** Android 的字体要从文件读：把资源里的字节放进应用的缓存目录（`java.io.tmpdir` 在 Android 上就是它），同名同大小就不重写。 */
internal actual fun platformFont(identity: String, bytes: ByteArray, weight: FontWeight, style: FontStyle, variable: Boolean): Font? = try {
    val dir = File(System.getProperty("java.io.tmpdir") ?: return null, "guga-fonts").apply { mkdirs() }
    // 可变字体三档字重共用一份文件
    val name = identity.substringBeforeLast('-').replace(Regex("[^A-Za-z0-9._-]"), "_")
    val file = File(dir, name)
    if (!file.isFile || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
    if (variable) Font(file, weight, style, FontVariation.Settings(FontVariation.weight(weight.weight))) else Font(file, weight, style)
} catch (_: Exception) {
    null
}
