package com.gugaart.ui.net

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.posix.AF_INET
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_RCVTIMEO
import platform.posix.SO_REUSEADDR
import platform.posix.SO_SNDTIMEO
import platform.posix.memset
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.timeval

/*
 * 一点 BSD 套接字的小工具（iOS 的 DLNA 投屏和弹幕中转共用；JVM 那边用 java.net）。只在 iosMain：Kotlin/Native 的 platform.posix。
 */

/** 网络字节序（大端）的 16 位 / 32 位：这台机器是小端（arm64），手工换。 */
internal fun hton16(v: Int): UShort = (((v and 0xff) shl 8) or ((v shr 8) and 0xff)).toUShort()

internal fun hton32(v: UInt): UInt = ((v and 0xffu) shl 24) or ((v and 0xff00u) shl 8) or ((v shr 8) and 0xff00u) or ((v shr 24) and 0xffu)

internal fun ntoh16(v: UShort): Int = hton16(v.toInt()).toInt()

/** a.b.c.d → 网络字节序的 in_addr_t。 */
internal fun ipv4(a: Int, b: Int, c: Int, d: Int): UInt = hton32(((a.toUInt() shl 24) or (b.toUInt() shl 16) or (c.toUInt() shl 8) or d.toUInt()))

/** 填一个 sockaddr_in（[ip] 已是网络字节序）。 */
@OptIn(ExperimentalForeignApi::class)
internal fun sockaddr_in.fill(ip: UInt, port: Int) {
    memset(ptr, 0, sizeOf<sockaddr_in>().convert())
    sin_len = sizeOf<sockaddr_in>().convert()
    sin_family = AF_INET.convert()
    sin_port = hton16(port)
    sin_addr.s_addr = ip
}

/** 收 / 发的超时（毫秒）；套接字关了不发 SIGPIPE。 */
@OptIn(ExperimentalForeignApi::class)
internal fun socketOptions(fd: Int, recvTimeoutMs: Long?, sendTimeoutMs: Long? = null, reuse: Boolean = false) = memScoped {
    val one = alloc<IntVar>()
    one.value = 1
    setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, one.ptr, sizeOf<IntVar>().convert())
    if (reuse) setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, one.ptr, sizeOf<IntVar>().convert())
    for ((opt, ms) in listOf(SO_RCVTIMEO to recvTimeoutMs, SO_SNDTIMEO to sendTimeoutMs)) {
        if (ms == null) continue
        val tv = alloc<timeval>()
        tv.tv_sec = (ms / 1000).convert()
        tv.tv_usec = ((ms % 1000) * 1000).convert()
        setsockopt(fd, SOL_SOCKET, opt, tv.ptr, sizeOf<timeval>().convert())
    }
}
