package com.gugaart.ui.subtitle

/*
 * 字幕站认的两种「文件指纹」（docs/subtitle-search.md §1、§3）。
 *
 * 移植自 MeiamSubtitles（https://github.com/91270/MeiamSubtitles ，提交 b0427939，Apache License 2.0，Copyright Meiam）：
 *  - 迅雷 CID：`Emby.MeiamSub.Thunder/ThunderProvider.cs` 的 `GetCidByFileAsync`；
 *  - 射手哈希：`Emby.MeiamSub.Shooter/ShooterProvider.cs` 的 `ComputeFileHashAsync`。
 * 改动：原版从服务器磁盘上的文件按偏移读；这里只描述「要哪几段」（[ByteSpan]），字节由调用方经 HTTP Range 取来
 * （[SubtitleSearch]：Emby 的原始流或 strm 目标），两种指纹合起来只发 4 个请求；算法、偏移、大小写和拼法与原版逐字节一致。
 */

/** 文件里的一段：从 [offset] 起 [length] 个字节。 */
data class ByteSpan(val offset: Long, val length: Int) {
    val end: Long get() = offset + length

    fun contains(o: ByteSpan): Boolean = o.offset >= offset && o.end <= end
}

/** 一个文件的两种指纹（取不到的那种是 null）。 */
data class FilePrints(val size: Long, val thunderCid: String?, val shooterHash: String?)

object Fingerprint {
    /** 迅雷：三段各 0x5000 字节；文件小于 0xF000 时整个文件。 */
    const val CID_BLOCK = 0x5000
    const val CID_WHOLE_BELOW = 0xF000L

    /** 射手：4 块各 4096 字节；文件小于 8 KB 不算。 */
    const val SHOOTER_BLOCK = 4096
    const val SHOOTER_MIN = 8L * 1024

    /** 迅雷 CID 要的段（顺序 = 拼接顺序）。 */
    fun thunderSpans(size: Long): List<ByteSpan> = when {
        size <= 0 -> emptyList()
        size < CID_WHOLE_BELOW -> listOf(ByteSpan(0, size.toInt()))
        else -> listOf(ByteSpan(0, CID_BLOCK), ByteSpan(size / 3, CID_BLOCK), ByteSpan(size - CID_BLOCK, CID_BLOCK))
    }

    /** 射手要的块（顺序 = 拼接顺序：4096、size/3*2、size/3、size−8192；整数除法先 /3 再 ×2，和 C# 的 long 运算一样）。 */
    fun shooterSpans(size: Long): List<ByteSpan> = if (size < SHOOTER_MIN) emptyList() else listOf(
        ByteSpan(4L * 1024, SHOOTER_BLOCK),
        ByteSpan(size / 3 * 2, SHOOTER_BLOCK),
        ByteSpan(size / 3, SHOOTER_BLOCK),
        ByteSpan(size - SHOOTER_MIN, SHOOTER_BLOCK),
    )

    /**
     * 真正要发的请求：迅雷的段，加上没落在它们里面的射手块。大文件 = 4 段（3 × 20 KB + 4 KB），小文件 = 1 段（整个文件）。
     * 纯函数，有单测。
     */
    fun plan(size: Long): List<ByteSpan> {
        val spans = thunderSpans(size).toMutableList()
        for (s in shooterSpans(size)) if (spans.none { it.contains(s) }) spans += s
        return spans
    }

    /** 从取回来的段（[got]：段 → 字节）里切出 [need]；没有覆盖它的段 = null。 */
    fun slice(got: Map<ByteSpan, ByteArray>, need: ByteSpan): ByteArray? {
        for ((span, bytes) in got) {
            if (!span.contains(need) || bytes.size < span.length) continue
            val from = (need.offset - span.offset).toInt()
            return bytes.copyOfRange(from, from + need.length)
        }
        return null
    }

    /** SHA-1(拼起来的段)，大写十六进制。 */
    fun thunderCid(blocks: List<ByteArray>): String {
        val all = ByteArray(blocks.sumOf { it.size })
        var off = 0
        for (b in blocks) {
            b.copyInto(all, off)
            off += b.size
        }
        return Hashes.hex(Hashes.sha1(all), upper = true)
    }

    /** 每块 MD5 小写十六进制，用 `;` 连起来。 */
    fun shooterHash(blocks: List<ByteArray>): String = blocks.joinToString(";") { Hashes.hex(Hashes.md5(it), upper = false) }

    /** [size] 的文件、取回来的段 [got] → 两种指纹（缺段的那种 = null）。 */
    fun prints(size: Long, got: Map<ByteSpan, ByteArray>): FilePrints {
        val cidBlocks = thunderSpans(size).map { slice(got, it) }
        val shooterBlocks = shooterSpans(size).map { slice(got, it) }
        return FilePrints(
            size,
            thunderCid = cidBlocks.takeIf { it.isNotEmpty() && it.all { b -> b != null } }?.let { thunderCid(it.filterNotNull()) },
            shooterHash = shooterBlocks.takeIf { it.isNotEmpty() && it.all { b -> b != null } }?.let { shooterHash(it.filterNotNull()) },
        )
    }

    /**
     * 整个过程：先取开头一段（顺便从 `Content-Range` 知道真实总长；[knownSize] 是 MediaSource.Size，只在服务器不说总长时用），
     * 再按 [plan] 取其余的段。[read] 拿不到（不认 Range、出错、超时）就返回 null，这时整次没有指纹。
     */
    suspend fun compute(knownSize: Long?, read: suspend (ByteSpan) -> RangeBytes?): FilePrints? {
        val head = read(ByteSpan(0, CID_BLOCK)) ?: return null
        val size = head.total ?: knownSize ?: return null
        if (size <= 0) return null
        val got = LinkedHashMap<ByteSpan, ByteArray>()
        // 开头这一段：文件比 0x5000 还小时服务器只回到文件尾
        val headSpan = ByteSpan(0, minOf(CID_BLOCK.toLong(), size).toInt())
        if (head.bytes.size >= headSpan.length) got[headSpan] = head.bytes.copyOf(headSpan.length)
        for (s in plan(size)) {
            if (got.keys.any { it.contains(s) }) continue
            val r = read(s) ?: return null
            if (r.bytes.size < s.length) return null
            got[s] = r.bytes.copyOf(s.length)
        }
        return prints(size, got)
    }
}

/** 一次 Range 读回来的字节，和服务器在 `Content-Range` 里说的文件总长（不说 = null）。 */
class RangeBytes(val bytes: ByteArray, val total: Long?)
