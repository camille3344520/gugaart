package com.gugaart.ui.subtitle

import com.gugaart.ui.danmaku.IosLoopbackRelayHost
import com.gugaart.ui.danmaku.RelayHost
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFStringConvertEncodingToNSStringEncoding
import platform.CoreFoundation.kCFStringEncodingGB_18030_2000
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

/** iOS：`NSString` 按 GB18030 解（解不了 Foundation 回 nil）。 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun decodeGb18030(bytes: ByteArray): String? {
    if (bytes.isEmpty()) return ""
    val enc = CFStringConvertEncodingToNSStringEncoding(kCFStringEncodingGB_18030_2000.toUInt())
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.convert()) }
    @Suppress("CAST_NEVER_SUCCEEDS")
    return NSString.create(data = data, encoding = enc) as String?
}

/** 本机字幕的回环服务：和弹幕中转同一个类（`IosLoopbackRelayHost`），另起一个实例、另一段随机口令。 */
@OptIn(ExperimentalForeignApi::class)
actual fun subtitleLoopbackHost(): RelayHost? {
    val bytes = ByteArray(16)
    val ok = bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, 16u, it.addressOf(0)) } == 0
    if (!ok) for (i in bytes.indices) bytes[i] = kotlin.random.Random.nextInt(256).toByte()
    return IosLoopbackRelayHost(null, bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') })
}
