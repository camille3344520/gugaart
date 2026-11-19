package com.gugaart.ui.data

import androidx.compose.ui.text.font.FontFamily
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile

/** iOS（M3-3）：导入的字体文件（壳拷进应用目录的那份）读成字节交给 Skia，路径当字体的身份。读不了 / 不是字体 = null，同桌面。 */
@OptIn(ExperimentalForeignApi::class)
actual fun fileFontFamily(path: String): FontFamily? = try {
    val data = NSData.dataWithContentsOfFile(path)
    val n = data?.length?.toInt() ?: 0
    if (data == null || n == 0) {
        null
    } else {
        val bytes = data.bytes!!.reinterpret<UByteVar>().readBytes(n)
        FontFamily(androidx.compose.ui.text.platform.Font(path, bytes))
    }
} catch (_: Exception) {
    null
}
