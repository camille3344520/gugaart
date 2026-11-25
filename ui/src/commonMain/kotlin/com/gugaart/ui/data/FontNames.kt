package com.gugaart.ui.data

/**
 * 字体文件 `name` 表里的家族名（name ID 1）。libass 按这个名字找 `sub-fonts-dir` 里的字体（`sub-font=<它>`），
 * 所以导入的字体要从文件里读出来，不能用文件名。优先 Windows 平台（3）的英文（0x409），再任何 Windows 平台的，再 Mac 平台（1）的。
 * 读不出来 → null（字幕就用不上这款，回退默认字体）。只认单个字体（ttf / otf），字体集合（ttc）在导入时已经拒了。
 */
fun fontFamilyName(bytes: ByteArray): String? = try {
    fun u16(o: Int) = (bytes[o].toInt() and 0xff shl 8) or (bytes[o + 1].toInt() and 0xff)
    fun u32(o: Int) = (u16(o).toLong() shl 16) or u16(o + 2).toLong()
    val tables = u16(4)
    var name = -1
    for (i in 0 until tables) {
        val rec = 12 + i * 16
        if (bytes[rec].toInt() == 'n'.code && bytes[rec + 1].toInt() == 'a'.code && bytes[rec + 2].toInt() == 'm'.code && bytes[rec + 3].toInt() == 'e'.code) {
            name = u32(rec + 8).toInt()
        }
    }
    if (name < 0) null else {
        val count = u16(name + 2)
        val strings = name + u16(name + 4)
        var best: Pair<Int, String>? = null
        for (i in 0 until count) {
            val r = name + 6 + i * 12
            val platform = u16(r)
            val lang = u16(r + 4)
            if (u16(r + 6) != 1) continue
            val len = u16(r + 8)
            val at = strings + u16(r + 10)
            val text = when (platform) {
                0, 3 -> CharArray(len / 2) { k -> u16(at + k * 2).toChar() }.concatToString()
                1 -> CharArray(len) { k -> (bytes[at + k].toInt() and 0xff).toChar() }.concatToString()
                else -> continue
            }.trim()
            if (text.isEmpty()) continue
            val rank = when {
                platform == 3 && lang == 0x409 -> 0
                platform == 3 || platform == 0 -> 1
                else -> 2
            }
            if (best == null || rank < best.first) best = rank to text
        }
        best?.second
    }
} catch (_: IndexOutOfBoundsException) {
    null
}
