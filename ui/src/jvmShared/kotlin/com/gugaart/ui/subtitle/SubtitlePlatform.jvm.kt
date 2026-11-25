package com.gugaart.ui.subtitle

import com.gugaart.ui.danmaku.LoopbackRelayHost
import com.gugaart.ui.danmaku.RelayHost
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.SecureRandom

/** 桌面 / 安卓：JDK 的 GB18030，严格（解不了的字节 = null，不替换成问号）。 */
actual fun decodeGb18030(bytes: ByteArray): String? = try {
    Charset.forName("GB18030").newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    null
} catch (_: Exception) {
    null
}

/** 本机字幕的回环服务：和弹幕中转同一个类，另起一个实例（系统给端口）、另一段随机口令（只在这次运行里有效）。 */
actual fun subtitleLoopbackHost(): RelayHost? = try {
    val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
    LoopbackRelayHost(null, bytes.joinToString("") { "%02x".format(it) })
} catch (_: Exception) {
    null
}
