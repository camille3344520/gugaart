package com.gugaart.ui.subtitle

/*
 * 字幕指纹要的两种摘要（docs/subtitle-search.md §1）：迅雷 CID = SHA-1，射手 = MD5。
 * commonMain 里没有 `MessageDigest`（iOS 也要用），这里是两段照 RFC 3174 / RFC 1321 写的纯 Kotlin 实现；只算几十 KB，不追求速度。
 * 测试：RFC 的标准向量（`SubtitleFingerprintTest`）。
 */
internal object Hashes {
    fun sha1(data: ByteArray): ByteArray {
        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0.toInt()
        val msg = pad(data, bigEndian = true)
        val w = IntArray(80)
        var off = 0
        while (off < msg.size) {
            for (i in 0 until 16) {
                val j = off + i * 4
                w[i] = ((msg[j].toInt() and 0xff) shl 24) or ((msg[j + 1].toInt() and 0xff) shl 16) or
                    ((msg[j + 2].toInt() and 0xff) shl 8) or (msg[j + 3].toInt() and 0xff)
            }
            for (i in 16 until 80) w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotateLeft(1)
            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4
            for (i in 0 until 80) {
                val f: Int
                val k: Int
                when {
                    i < 20 -> { f = (b and c) or (b.inv() and d); k = 0x5A827999 }
                    i < 40 -> { f = b xor c xor d; k = 0x6ED9EBA1 }
                    i < 60 -> { f = (b and c) or (b and d) or (c and d); k = 0x8F1BBCDC.toInt() }
                    else -> { f = b xor c xor d; k = 0xCA62C1D6.toInt() }
                }
                val t = a.rotateLeft(5) + f + e + k + w[i]
                e = d
                d = c
                c = b.rotateLeft(30)
                b = a
                a = t
            }
            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
            off += 64
        }
        val out = ByteArray(20)
        for ((n, h) in intArrayOf(h0, h1, h2, h3, h4).withIndex()) {
            out[n * 4] = (h ushr 24).toByte()
            out[n * 4 + 1] = (h ushr 16).toByte()
            out[n * 4 + 2] = (h ushr 8).toByte()
            out[n * 4 + 3] = h.toByte()
        }
        return out
    }

    private val MD5_S = intArrayOf(
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
    )

    private val MD5_K = longArrayOf(
        0xd76aa478, 0xe8c7b756, 0x242070db, 0xc1bdceee, 0xf57c0faf, 0x4787c62a, 0xa8304613, 0xfd469501,
        0x698098d8, 0x8b44f7af, 0xffff5bb1, 0x895cd7be, 0x6b901122, 0xfd987193, 0xa679438e, 0x49b40821,
        0xf61e2562, 0xc040b340, 0x265e5a51, 0xe9b6c7aa, 0xd62f105d, 0x02441453, 0xd8a1e681, 0xe7d3fbc8,
        0x21e1cde6, 0xc33707d6, 0xf4d50d87, 0x455a14ed, 0xa9e3e905, 0xfcefa3f8, 0x676f02d9, 0x8d2a4c8a,
        0xfffa3942, 0x8771f681, 0x6d9d6122, 0xfde5380c, 0xa4beea44, 0x4bdecfa9, 0xf6bb4b60, 0xbebfbc70,
        0x289b7ec6, 0xeaa127fa, 0xd4ef3085, 0x04881d05, 0xd9d4d039, 0xe6db99e5, 0x1fa27cf8, 0xc4ac5665,
        0xf4292244, 0x432aff97, 0xab9423a7, 0xfc93a039, 0x655b59c3, 0x8f0ccc92, 0xffeff47d, 0x85845dd1,
        0x6fa87e4f, 0xfe2ce6e0, 0xa3014314, 0x4e0811a1, 0xf7537e82, 0xbd3af235, 0x2ad7d2bb, 0xeb86d391,
    ).map { it.toInt() }.toIntArray()

    fun md5(data: ByteArray): ByteArray {
        var a0 = 0x67452301
        var b0 = 0xefcdab89.toInt()
        var c0 = 0x98badcfe.toInt()
        var d0 = 0x10325476
        val msg = pad(data, bigEndian = false)
        val m = IntArray(16)
        var off = 0
        while (off < msg.size) {
            for (i in 0 until 16) {
                val j = off + i * 4
                m[i] = (msg[j].toInt() and 0xff) or ((msg[j + 1].toInt() and 0xff) shl 8) or
                    ((msg[j + 2].toInt() and 0xff) shl 16) or ((msg[j + 3].toInt() and 0xff) shl 24)
            }
            var a = a0
            var b = b0
            var c = c0
            var d = d0
            for (i in 0 until 64) {
                var f: Int
                val g: Int
                when {
                    i < 16 -> { f = (b and c) or (b.inv() and d); g = i }
                    i < 32 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                    i < 48 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                    else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
                }
                f += a + MD5_K[i] + m[g]
                a = d
                d = c
                c = b
                b += f.rotateLeft(MD5_S[i])
            }
            a0 += a
            b0 += b
            c0 += c
            d0 += d
            off += 64
        }
        val out = ByteArray(16)
        for ((n, h) in intArrayOf(a0, b0, c0, d0).withIndex()) {
            out[n * 4] = h.toByte()
            out[n * 4 + 1] = (h ushr 8).toByte()
            out[n * 4 + 2] = (h ushr 16).toByte()
            out[n * 4 + 3] = (h ushr 24).toByte()
        }
        return out
    }

    /** 补位：0x80、补零到 56 mod 64、再写 64 位的比特长度（SHA-1 大端，MD5 小端）。 */
    private fun pad(data: ByteArray, bigEndian: Boolean): ByteArray {
        val bits = data.size.toLong() * 8
        var len = data.size + 1
        while (len % 64 != 56) len++
        val out = data.copyOf(len + 8)
        out[data.size] = 0x80.toByte()
        for (i in 0 until 8) {
            val b = (bits ushr (8 * i)).toByte()
            if (bigEndian) out[len + 7 - i] = b else out[len + i] = b
        }
        return out
    }

    fun hex(bytes: ByteArray, upper: Boolean): String {
        val digits = if (upper) "0123456789ABCDEF" else "0123456789abcdef"
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(digits[(b.toInt() shr 4) and 0xf])
            sb.append(digits[b.toInt() and 0xf])
        }
        return sb.toString()
    }
}
