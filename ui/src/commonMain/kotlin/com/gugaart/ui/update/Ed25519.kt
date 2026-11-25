package com.gugaart.ui.update

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * 更新清单的 Ed25519 验签（RFC 8032 §5.1.7，只验不签），纯 Kotlin，三端共用的代码、给 iOS 用（1.4.0）。
 *
 * 为什么在这里写：上游的验签是 `expect fun ed25519Verify`，JVM 那一份（安卓 / Windows / macOS）用 BigInteger，iOS 那一份恒为 false
 * （上游 `Ed25519.ios.kt`：「iOS 没有应用内更新」）——于是 iPhone 上每次检查都是「更新清单签名校验失败，已忽略」，1.3.x 只好不在应用里查。
 * 上游只读（AGENTS.md §2.3），平台也没有现成的：Security.framework 没有 Ed25519；CryptoKit 的 `Curve25519.Signing` 只给 Swift，
 * 要经 Xcode 宿主写一个 @objc 桥、再从 Kotlin/Native 反查，而且没法在 JVM 的单测里跑。这里一百多行纯算术，不引依赖、不联网、
 * 同一份代码在桌面 JVM 和 iOS 模拟器上跑同一组测试（RFC 8032 的测试向量 + 我们真实的清单）。
 *
 * 只有 iOS 用它（[OwnVerifier]）：别的端照旧是上游的 JVM 实现，行为不变。
 *
 * 做法：域 GF(2^255 − 19) 的元素是 16 个 16 位的肢（TweetNaCl 的表示，每个肢放在 Long 里，乘法不溢出）；点用扩展坐标 (X:Y:Z:T)，
 * 加法 add-2008-hwcd-3（a = −1，倍点也用它）；标量乘按位 double-and-add（验签只碰公开数据，不需要常数时间）。
 * 判定是不乘余因子的 `[S]B == R + [k]A`（和上游 JVM 那一份同一个判定；RFC 8032 允许），另外照 RFC 拒绝 S ≥ L、拒绝不规范的 y（≥ p）
 * 和不在曲线上的点。SHA-512（FIPS 180-4）也在这里，常数按定义（前 80 个素数立方根 / 前 8 个素数平方根的小数部分）核过。
 */
object Ed25519 {
    /** `signature`（64 字节 R‖S）是 `publicKey`（32 字节）对 `message` 的有效签名吗。任何不对（长度、编码、点不在曲线上）都是 false，不抛。 */
    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        return try {
            verifyChecked(publicKey, message, signature)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 清单那一层：`payload` 的 UTF-8 字节、base64 的签名和公钥（标准字母表，`=` 补齐；中间的空白忽略）。
     * 签名和上游 `UpdateController(verifier = …)` 要的一样，iOS 上就把它交进去。
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun verifyManifest(payload: String, signatureB64: String, publicKeyB64: String): Boolean {
        fun decode(text: String): ByteArray? = try {
            Base64.Default.decode(text.filterNot { it.isWhitespace() })
        } catch (_: IllegalArgumentException) {
            null
        }
        val key = decode(publicKeyB64) ?: return false
        val sig = decode(signatureB64) ?: return false
        return verify(key, payload.encodeToByteArray(), sig)
    }

    private fun verifyChecked(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        val a = decodePoint(publicKey) ?: return false
        val rBytes = signature.copyOfRange(0, 32)
        val r = decodePoint(rBytes) ?: return false
        val s = signature.copyOfRange(32, 64)
        if (!lessThanL(s)) return false
        // k = SHA-512(R ‖ A ‖ M) 当小端整数，模 L
        val k = reduceModL(Sha512.digest(rBytes + publicKey + message))
        val left = encodePoint(scalarMult(BASE, s))
        val right = encodePoint(add(r, scalarMult(a, k)))
        return left.contentEquals(right)
    }

    /* ------------------------------------------------------------------ 域 GF(p)，p = 2^255 − 19：16 个 16 位的肢，小端 */

    private fun fe(): LongArray = LongArray(16)

    private fun fe(v: Long): LongArray = LongArray(16).also { it[0] = v }

    /** 进位，把每个肢压回 16 位；最高肢溢出的部分 × 38 回到最低肢（2^256 ≡ 38）。 */
    private fun carry(o: LongArray) {
        for (i in 0 until 16) {
            o[i] += 1L shl 16
            val c = o[i] shr 16
            if (i < 15) o[i + 1] += c - 1 else o[0] += 38 * (c - 1)
            o[i] -= c shl 16
        }
    }

    private fun add(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] + b[i]
    }

    private fun sub(o: LongArray, a: LongArray, b: LongArray) {
        for (i in 0 until 16) o[i] = a[i] - b[i]
    }

    private fun mul(o: LongArray, a: LongArray, b: LongArray) {
        val t = LongArray(31)
        for (i in 0 until 16) for (j in 0 until 16) t[i + j] += a[i] * b[j]
        for (i in 0 until 15) t[i] += 38 * t[i + 16]
        for (i in 0 until 16) o[i] = t[i]
        carry(o)
        carry(o)
    }

    private fun sq(o: LongArray, a: LongArray) = mul(o, a, a)

    /**
     * `a` 的 2^n − 1 型幂：从 c = a 起，对 `bits` 从高到低每一位先平方，位是 1（不在 [zeros] 里）再乘 a。
     * 指数 = 1 后面跟 `bits` 个位。下面三个幂都是这个形状。
     */
    private fun powChain(o: LongArray, a: LongArray, bits: Int, zeros: Set<Int>) {
        val c = a.copyOf()
        for (i in bits - 1 downTo 0) {
            sq(c, c)
            if (i !in zeros) mul(c, c, a)
        }
        c.copyInto(o)
    }

    /** a^(p−2) = a⁻¹：p − 2 = 2^255 − 21，二进制是 255 个 1，第 2、4 位是 0。 */
    private fun invert(o: LongArray, a: LongArray) = powChain(o, a, 254, setOf(2, 4))

    /** a^((p−5)/8)：(p − 5) / 8 = 2^252 − 3，252 个 1，第 1 位是 0。 */
    private fun pow2523(o: LongArray, a: LongArray) = powChain(o, a, 251, setOf(1))

    /** 规范的 32 字节小端编码（值先压到 [0, p)）。 */
    private fun pack(a: LongArray): ByteArray {
        val t = a.copyOf()
        carry(t)
        carry(t)
        carry(t)
        // 减两次 p（每次不够减就保留原值）
        repeat(2) {
            val m = fe()
            m[0] = t[0] - 0xffed
            for (i in 1 until 15) {
                m[i] = t[i] - 0xffff - ((m[i - 1] shr 16) and 1)
                m[i - 1] = m[i - 1] and 0xffff
            }
            m[15] = t[15] - 0x7fff - ((m[14] shr 16) and 1)
            val borrow = (m[15] shr 16) and 1
            m[14] = m[14] and 0xffff
            if (borrow == 0L) m.copyInto(t)
        }
        val out = ByteArray(32)
        for (i in 0 until 16) {
            out[2 * i] = (t[i] and 0xff).toByte()
            out[2 * i + 1] = ((t[i] shr 8) and 0xff).toByte()
        }
        return out
    }

    /** 32 字节小端 → 域元素（最高位不算：那是点编码里 x 的符号位）。 */
    private fun unpack(bytes: ByteArray): LongArray {
        val o = fe()
        for (i in 0 until 16) o[i] = (bytes[2 * i].toLong() and 0xff) + ((bytes[2 * i + 1].toLong() and 0xff) shl 8)
        o[15] = o[15] and 0x7fff
        return o
    }

    private fun equal(a: LongArray, b: LongArray): Boolean = pack(a).contentEquals(pack(b))

    private fun isOdd(a: LongArray): Boolean = (pack(a)[0].toInt() and 1) == 1

    private fun neg(a: LongArray): LongArray = fe().also { sub(it, fe(), a) }

    /** 压成规范形式再拆回肢（常数只算一次，存规范的）。 */
    private fun normalized(a: LongArray): LongArray = unpack(pack(a))

    /** d = −121665 / 121666（不抄常数表，按定义算）。 */
    private val D: LongArray = fe().let { d ->
        invert(d, fe(121666))
        mul(d, d, fe(121665))
        normalized(neg(d))
    }

    private val D2: LongArray = normalized(fe().also { add(it, D, D) })

    /** √−1 = 2^((p−1)/4)：(p − 1) / 4 = 2^253 − 5，253 个 1，第 2 位是 0。 */
    private val SQRT_M1: LongArray = normalized(fe().also { powChain(it, fe(2), 252, setOf(2)) })

    /* ------------------------------------------------------------------ 点：扩展坐标 (X:Y:Z:T)，x = X/Z，y = Y/Z，xy = T/Z */

    private class Point(val x: LongArray, val y: LongArray, val z: LongArray, val t: LongArray)

    private fun identity() = Point(fe(), fe(1), fe(1), fe())

    /** add-2008-hwcd-3（a = −1）：统一公式，p = q 时就是倍点。 */
    private fun add(p: Point, q: Point): Point {
        val a = fe()
        val b = fe()
        val c = fe()
        val d = fe()
        val t = fe()
        sub(a, p.y, p.x)
        sub(t, q.y, q.x)
        mul(a, a, t)
        add(b, p.x, p.y)
        add(t, q.x, q.y)
        mul(b, b, t)
        mul(c, p.t, q.t)
        mul(c, c, D2)
        mul(d, p.z, q.z)
        add(d, d, d)
        val e = fe().also { sub(it, b, a) }
        val f = fe().also { sub(it, d, c) }
        val g = fe().also { add(it, d, c) }
        val h = fe().also { add(it, b, a) }
        return Point(fe().also { mul(it, e, f) }, fe().also { mul(it, h, g) }, fe().also { mul(it, g, f) }, fe().also { mul(it, e, h) })
    }

    /** [k]p，k 是 32 字节小端。 */
    private fun scalarMult(p: Point, k: ByteArray): Point {
        var r = identity()
        for (i in 255 downTo 0) {
            r = add(r, r)
            if ((k[i ushr 3].toInt() ushr (i and 7)) and 1 == 1) r = add(r, p)
        }
        return r
    }

    /** RFC 8032 §5.1.3 解码；不是曲线上的点、y 不规范（≥ p）、x = 0 却带负号 → null。 */
    private fun decodePoint(bytes: ByteArray): Point? {
        if (bytes.size != 32) return null
        val sign = (bytes[31].toInt() ushr 7) and 1
        val y = unpack(bytes)
        // y < p：重新编码要和原样（去掉符号位）一模一样
        val canonical = bytes.copyOf().also { it[31] = (it[31].toInt() and 0x7f).toByte() }
        if (!pack(y).contentEquals(canonical)) return null
        val y2 = fe().also { sq(it, y) }
        val u = fe().also { sub(it, y2, fe(1)) } // y² − 1
        val v = fe().also { mul(it, D, y2); add(it, it, fe(1)) } // d y² + 1
        // x = u v³ (u v⁷)^((p−5)/8)
        val v3 = fe().also { sq(it, v); mul(it, it, v) }
        val v7 = fe().also { sq(it, v3); mul(it, it, v) }
        val x = fe().also { mul(it, u, v7); pow2523(it, it); mul(it, it, v3); mul(it, it, u) }
        val vx2 = fe().also { sq(it, x); mul(it, it, v) }
        if (!equal(vx2, u)) {
            if (!equal(vx2, neg(u))) return null
            mul(x, x, SQRT_M1)
        }
        val xIsZero = pack(x).all { it.toInt() == 0 }
        if (xIsZero && sign == 1) return null
        val xFinal = if (isOdd(x) != (sign == 1)) neg(x) else x
        return Point(xFinal, y, fe(1), fe().also { mul(it, xFinal, y) })
    }

    private fun encodePoint(p: Point): ByteArray {
        val zi = fe().also { invert(it, p.z) }
        val x = fe().also { mul(it, p.x, zi) }
        val y = fe().also { mul(it, p.y, zi) }
        val out = pack(y)
        if (isOdd(x)) out[31] = (out[31].toInt() or 0x80).toByte()
        return out
    }

    /** 基点 B：编码是 `58 66 66 … 66`（y = 4/5，x 取偶）。 */
    private val BASE: Point = decodePoint(ByteArray(32) { if (it == 0) 0x58.toByte() else 0x66.toByte() })!!

    /* ------------------------------------------------------------------ 模 L 的标量，L = 2^252 + 27742317777372353535851937790883648493 */

    /** L 的 32 字节小端。 */
    private val L_BYTES: ByteArray = intArrayOf(
        0xed, 0xd3, 0xf5, 0x5c, 0x1a, 0x63, 0x12, 0x58, 0xd6, 0x9c, 0xf7, 0xa2, 0xde, 0xf9, 0xde, 0x14,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x10,
    ).let { v -> ByteArray(32) { v[it].toByte() } }

    /** 小端的 32 字节无符号数 < L。 */
    private fun lessThanL(s: ByteArray): Boolean {
        for (i in 31 downTo 0) {
            val a = s[i].toInt() and 0xff
            val b = L_BYTES[i].toInt() and 0xff
            if (a != b) return a < b
        }
        return false
    }

    /**
     * 64 字节小端（SHA-512 的输出）模 L → 32 字节小端。逐位移入、够 L 就减（余数始终 < 2L < 2^254，放得进 9 个 32 位字）：
     * 512 轮的移位 / 比较 / 减法，慢但一眼能看出是对的。
     */
    private fun reduceModL(h: ByteArray): ByteArray {
        val l = IntArray(9)
        for (i in 0 until 32) l[i / 4] = l[i / 4] or ((L_BYTES[i].toInt() and 0xff) shl (8 * (i % 4)))
        val r = IntArray(9)
        for (bit in 511 downTo 0) {
            // r = r * 2 + 这一位
            var carryIn = (h[bit ushr 3].toInt() ushr (bit and 7)) and 1
            for (w in 0 until 9) {
                val v = r[w].toLong() and 0xffffffffL
                r[w] = ((v shl 1) or carryIn.toLong()).toInt()
                carryIn = ((v ushr 31) and 1).toInt()
            }
            if (!lessThan(r, l)) {
                var borrow = 0L
                for (w in 0 until 9) {
                    val d = (r[w].toLong() and 0xffffffffL) - (l[w].toLong() and 0xffffffffL) - borrow
                    r[w] = d.toInt()
                    borrow = if (d < 0) 1 else 0
                }
            }
        }
        return ByteArray(32) { i -> (r[i / 4] ushr (8 * (i % 4))).toByte() }
    }

    private fun lessThan(a: IntArray, b: IntArray): Boolean {
        for (w in a.indices.reversed()) {
            val x = a[w].toLong() and 0xffffffffL
            val y = b[w].toLong() and 0xffffffffL
            if (x != y) return x < y
        }
        return false
    }
}

/** SHA-512（FIPS 180-4），一次算完一整段（验签只哈希 R‖A‖清单，几 KB）。 */
internal object Sha512 {
    /** 前 80 个素数立方根小数部分的前 64 位。 */
    private val K: LongArray = (
        "428a2f98d728ae22 7137449123ef65cd b5c0fbcfec4d3b2f e9b5dba58189dbbc 3956c25bf348b538 59f111f1b605d019 923f82a4af194f9b ab1c5ed5da6d8118 " +
            "d807aa98a3030242 12835b0145706fbe 243185be4ee4b28c 550c7dc3d5ffb4e2 72be5d74f27b896f 80deb1fe3b1696b1 9bdc06a725c71235 c19bf174cf692694 " +
            "e49b69c19ef14ad2 efbe4786384f25e3 0fc19dc68b8cd5b5 240ca1cc77ac9c65 2de92c6f592b0275 4a7484aa6ea6e483 5cb0a9dcbd41fbd4 76f988da831153b5 " +
            "983e5152ee66dfab a831c66d2db43210 b00327c898fb213f bf597fc7beef0ee4 c6e00bf33da88fc2 d5a79147930aa725 06ca6351e003826f 142929670a0e6e70 " +
            "27b70a8546d22ffc 2e1b21385c26c926 4d2c6dfc5ac42aed 53380d139d95b3df 650a73548baf63de 766a0abb3c77b2a8 81c2c92e47edaee6 92722c851482353b " +
            "a2bfe8a14cf10364 a81a664bbc423001 c24b8b70d0f89791 c76c51a30654be30 d192e819d6ef5218 d69906245565a910 f40e35855771202a 106aa07032bbd1b8 " +
            "19a4c116b8d2d0c8 1e376c085141ab53 2748774cdf8eeb99 34b0bcb5e19b48a8 391c0cb3c5c95a63 4ed8aa4ae3418acb 5b9cca4f7763e373 682e6ff3d6b2b8a3 " +
            "748f82ee5defb2fc 78a5636f43172f60 84c87814a1f0ab72 8cc702081a6439ec 90befffa23631e28 a4506cebde82bde9 bef9a3f7b2c67915 c67178f2e372532b " +
            "ca273eceea26619c d186b8c721c0c207 eada7dd6cde0eb1e f57d4f7fee6ed178 06f067aa72176fba 0a637dc5a2c898a6 113f9804bef90dae 1b710b35131c471b " +
            "28db77f523047d84 32caab7b40c72493 3c9ebe0a15c9bebc 431d67c49c100d4c 4cc5d4becb3e42b6 597f299cfc657e2a 5fcb6fab3ad6faec 6c44198c4a475817"
        ).split(' ').map { it.toULong(16).toLong() }.toLongArray()

    /** 前 8 个素数平方根小数部分的前 64 位。 */
    private val H0: LongArray = "6a09e667f3bcc908 bb67ae8584caa73b 3c6ef372fe94f82b a54ff53a5f1d36f1 510e527fade682d1 9b05688c2b3e6c1f 1f83d9abfb41bd6b 5be0cd19137e2179"
        .split(' ').map { it.toULong(16).toLong() }.toLongArray()

    fun digest(message: ByteArray): ByteArray {
        // 补位：0x80、若干个 0、128 位大端的位长（这里的消息远小于 2^61 字节，高 64 位是 0）
        val padLen = ((message.size + 17 + 127) / 128) * 128
        val buf = message.copyOf(padLen)
        buf[message.size] = 0x80.toByte()
        val bits = message.size.toLong() * 8
        for (i in 0 until 8) buf[padLen - 1 - i] = (bits ushr (8 * i)).toByte()

        val h = H0.copyOf()
        val w = LongArray(80)
        for (block in 0 until padLen / 128) {
            val off = block * 128
            for (t in 0 until 16) {
                var v = 0L
                for (j in 0 until 8) v = (v shl 8) or (buf[off + 8 * t + j].toLong() and 0xff)
                w[t] = v
            }
            for (t in 16 until 80) {
                val s0 = w[t - 15].rotateRight(1) xor w[t - 15].rotateRight(8) xor (w[t - 15] ushr 7)
                val s1 = w[t - 2].rotateRight(19) xor w[t - 2].rotateRight(61) xor (w[t - 2] ushr 6)
                w[t] = w[t - 16] + s0 + w[t - 7] + s1
            }
            var a = h[0]
            var b = h[1]
            var c = h[2]
            var d = h[3]
            var e = h[4]
            var f = h[5]
            var g = h[6]
            var hh = h[7]
            for (t in 0 until 80) {
                val s1 = e.rotateRight(14) xor e.rotateRight(18) xor e.rotateRight(41)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[t] + w[t]
                val s0 = a.rotateRight(28) xor a.rotateRight(34) xor a.rotateRight(39)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g
                g = f
                f = e
                e = d + t1
                d = c
                c = b
                b = a
                a = t1 + t2
            }
            h[0] += a
            h[1] += b
            h[2] += c
            h[3] += d
            h[4] += e
            h[5] += f
            h[6] += g
            h[7] += hh
        }
        val out = ByteArray(64)
        for (i in 0 until 8) for (j in 0 until 8) out[8 * i + j] = (h[i] ushr (56 - 8 * j)).toByte()
        return out
    }
}
