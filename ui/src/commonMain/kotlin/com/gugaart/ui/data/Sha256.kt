package com.gugaart.ui.data

/**
 * SHA-256（FIPS 180-4），纯 Kotlin：commonMain 里没有 `MessageDigest`（iOS 也要编）。只用来校验按需下载的字体文件（一两 MB），不追求速度。
 * 测试：FIPS 的标准向量（`StyleFontsTest`）。
 */
object Sha256 {
    private val K = intsOf(
        "428a2f98 71374491 b5c0fbcf e9b5dba5 3956c25b 59f111f1 923f82a4 ab1c5ed5",
        "d807aa98 12835b01 243185be 550c7dc3 72be5d74 80deb1fe 9bdc06a7 c19bf174",
        "e49b69c1 efbe4786 0fc19dc6 240ca1cc 2de92c6f 4a7484aa 5cb0a9dc 76f988da",
        "983e5152 a831c66d b00327c8 bf597fc7 c6e00bf3 d5a79147 06ca6351 14292967",
        "27b70a85 2e1b2138 4d2c6dfc 53380d13 650a7354 766a0abb 81c2c92e 92722c85",
        "a2bfe8a1 a81a664b c24b8b70 c76c51a3 d192e819 d6990624 f40e3585 106aa070",
        "19a4c116 1e376c08 2748774c 34b0bcb5 391c0cb3 4ed8aa4a 5b9cca4f 682e6ff3",
        "748f82ee 78a5636f 84c87814 8cc70208 90befffa a4506ceb bef9a3f7 c67178f2",
    )

    private fun intsOf(vararg rows: String): IntArray = rows.joinToString(" ").split(' ').map { it.toLong(16).toInt() }.toIntArray()

    fun digest(data: ByteArray): ByteArray {
        val h = intsOf("6a09e667 bb67ae85 3c6ef372 a54ff53a 510e527f 9b05688c 1f83d9ab 5be0cd19")
        val bitLen = data.size.toLong() * 8
        val padLen = ((data.size + 9 + 63) / 64) * 64
        val msg = ByteArray(padLen)
        data.copyInto(msg)
        msg[data.size] = 0x80.toByte()
        for (i in 0 until 8) msg[padLen - 1 - i] = (bitLen ushr (8 * i)).toByte()
        val w = IntArray(64)
        var off = 0
        while (off < padLen) {
            for (t in 0 until 16) {
                val j = off + t * 4
                w[t] = (msg[j].toInt() and 0xff shl 24) or (msg[j + 1].toInt() and 0xff shl 16) or (msg[j + 2].toInt() and 0xff shl 8) or (msg[j + 3].toInt() and 0xff)
            }
            for (t in 16 until 64) {
                val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
                val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h[0]; var b = h[1]; var c = h[2]; var d = h[3]
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (t in 0 until 64) {
                val t1 = hh + (e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)) + ((e and f) xor (e.inv() and g)) + K[t] + w[t]
                val t2 = (a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)) + ((a and b) xor (a and c) xor (b and c))
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
            off += 64
        }
        val out = ByteArray(32)
        for (i in 0 until 8) for (k in 0 until 4) out[i * 4 + k] = (h[i] ushr (24 - 8 * k)).toByte()
        return out
    }

    fun hex(data: ByteArray): String = digest(data).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
