package com.gugaart.ui.data

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import com.gugugaga.app.platform.LocalStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 导入的弹幕字体（用户 2026-11-14：「弹幕支持导入字体，入口放在最上面」）。
 *
 * 字体文件由用户从本机选（Windows 文件对话框 / 安卓系统文件选择器），**复制进应用自己的存储**（壳给的目录），和 `:danmaku` 自带的四款并列可选；
 * 可以删除；选中状态存本地（键 [ImportedFontStore.KEY]，和画报的其它本地状态一样放在上游的 `LocalStore` 里，不动上游的 `gugu.settings.v1`）。
 *
 * **画面上用不用得上**：上游 `:danmaku` 的 `DanmakuLayer` 只认 `DanmakuFont` 这个封闭的枚举（四款），没有接受外部 `FontFamily` 的入口
 * （`docs/adr/0003-danmaku-custom-font.md`）。所以现在：导入、预览、选中、删除、持久化都是真的；弹幕画面在上游开缝之前仍用内置的那一款。
 * [ImportedFontStore.rendererReady] 是这道缝的开关。
 */
data class ImportedFont(
    /** 稳定的 id（文件内容的哈希的前几位 + 序号），也是应用存储里的文件名（不带扩展名）。 */
    val id: String,
    /** 给人看的名字 = 原文件名去掉扩展名。 */
    val name: String,
    /** 应用存储里的绝对路径。 */
    val path: String,
)

/** 选了一个文件之后拿到的东西。 */
/** [tooLarge]：文件超过上限，壳没有把它读进内存（[bytes] 是空的）。 */
class PickedFont(val fileName: String, val bytes: ByteArray, val tooLarge: Boolean = false)

/**
 * 平台那一半（壳提供；`null` = 这个平台没有文件选择，例如电视）：选文件、把字节写进应用存储、删文件。
 * 界面这一半（校验、列表、选中、持久化）在 [ImportedFontStore]，三端共用。
 */
class FontImportHost(
    /** 打开平台的文件选择器（只列 ttf / otf）。用户取消 → null。 */
    val pick: suspend () -> PickedFont?,
    /** 把字节写成应用存储里的 `<id>.<ext>`，返回绝对路径。 */
    val write: (fileName: String, bytes: ByteArray) -> String,
    /** 删掉应用存储里的那个文件（不存在也算成功）。 */
    val delete: (path: String) -> Unit,
    /** 那个文件还在不在（被系统清掉 / 用户手动删了 → 列表里自动摘掉）。 */
    val exists: (path: String) -> Boolean,
)

val LocalFontImport = staticCompositionLocalOf<FontImportHost?> { null }

/** 导入失败的原因（给人看的话，不带路径）。 */
enum class FontImportError(val text: String) {
    NotAFont("这不是字体文件（要 ttf / otf）"),
    TooLarge("字体文件太大了（上限 40 MB）"),
    Duplicate("这款已经导入过了"),
    WriteFailed("没能存进应用的存储"),
}

class ImportedFontState(val fonts: List<ImportedFont> = emptyList(), val selected: String? = null) {
    val current: ImportedFont? get() = fonts.firstOrNull { it.id == selected }
}

class ImportedFontStore(private val local: LocalStore, private val host: FontImportHost?) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ImportedFontState> = _state

    /** 这个平台能不能导入（有文件选择器）。 */
    val canImport: Boolean get() = host != null

    private fun load(): ImportedFontState {
        val parsed = decode(local.get(KEY))
        // 文件已经不在了的摘掉（有壳才查得了）
        val alive = if (host == null) parsed.fonts else parsed.fonts.filter { host.exists(it.path) }
        return ImportedFontState(alive, parsed.selected?.takeIf { id -> alive.any { it.id == id } })
    }

    private fun save(next: ImportedFontState) {
        local.set(KEY, encode(next))
        _state.value = next
    }

    /** 导入一个选好的文件：校验 → 复制进应用存储 → 进列表并选中。 */
    fun import(picked: PickedFont): FontImportError? {
        val h = host ?: return FontImportError.WriteFailed
        if (picked.tooLarge) return FontImportError.TooLarge
        fontFileProblem(picked.bytes)?.let { return it }
        val id = fontId(picked.bytes)
        if (_state.value.fonts.any { it.id == id }) return FontImportError.Duplicate
        val ext = if (isOpenTypeCff(picked.bytes)) "otf" else "ttf"
        val path = try {
            h.write("$id.$ext", picked.bytes)
        } catch (_: Exception) {
            return FontImportError.WriteFailed
        }
        val font = ImportedFont(id, fontDisplayName(picked.fileName), path)
        save(ImportedFontState(_state.value.fonts + font, id))
        return null
    }

    /** 选中一款导入的；null = 回到内置的那一款（上游的 `danmaku.render.font`）。 */
    fun select(id: String?) = save(ImportedFontState(_state.value.fonts, id?.takeIf { want -> _state.value.fonts.any { it.id == want } }))

    fun remove(id: String) {
        val font = _state.value.fonts.firstOrNull { it.id == id } ?: return
        runCatching { host?.delete(font.path) }
        save(ImportedFontState(_state.value.fonts - font, _state.value.selected.takeIf { it != id }))
    }

    companion object {
        const val KEY = "gugaart.danmakuFonts.v1"
        const val MAX_BYTES = 40L * 1024 * 1024

        /**
         * 弹幕画面能不能用导入的字体：要等上游 `:danmaku` 给 `DanmakuLayer` 开一个外部 `FontFamily` 的入口（ADR 0003）。
         * 开了之后把这里改成 true，并在 `player/PlaySession.kt` 把选中的字体交进去。
         */
        const val rendererReady = false

        fun encode(s: ImportedFontState): String = buildJsonObject {
            put("fonts", buildJsonArray {
                for (f in s.fonts) add(buildJsonObject { put("id", f.id); put("name", f.name); put("path", f.path) })
            })
            s.selected?.let { put("selected", it) }
        }.toString()

        fun decode(raw: String?): ImportedFontState {
            val o = GugaPrefs.parseObject(raw) ?: return ImportedFontState()
            val fonts = (o["fonts"] as? JsonArray).orEmpty().mapNotNull { e ->
                val f = e as? JsonObject ?: return@mapNotNull null
                fun str(k: String) = (f[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
                ImportedFont(str("id") ?: return@mapNotNull null, str("name") ?: return@mapNotNull null, str("path") ?: return@mapNotNull null)
            }
            val selected = (o["selected"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            return ImportedFontState(fonts, selected?.takeIf { id -> fonts.any { it.id == id } })
        }
    }
}

/** 字体文件的开头四个字节：TrueType `00 01 00 00` / `true`，OpenType CFF `OTTO`。字体集合（`ttcf`）不收：里面是好几款，选不了。 */
fun fontFileProblem(bytes: ByteArray): FontImportError? {
    if (bytes.size.toLong() > ImportedFontStore.MAX_BYTES) return FontImportError.TooLarge
    if (bytes.size < 12) return FontImportError.NotAFont
    val tag = String(CharArray(4) { (bytes[it].toInt() and 0xFF).toChar() })
    val trueType = bytes[0].toInt() == 0 && bytes[1].toInt() == 1 && bytes[2].toInt() == 0 && bytes[3].toInt() == 0
    return if (trueType || tag == "true" || tag == "OTTO") null else FontImportError.NotAFont
}

fun isOpenTypeCff(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes[0].toInt().toChar() == 'O' && bytes[1].toInt().toChar() == 'T' && bytes[2].toInt().toChar() == 'T' && bytes[3].toInt().toChar() == 'O'

/** 内容哈希（FNV-1a 64 位）→ 16 位十六进制。同一个文件导入两次得到同一个 id（查重）。 */
fun fontId(bytes: ByteArray): String {
    var h = -0x340d631b7bdddcdbL
    for (b in bytes) {
        h = h xor (b.toLong() and 0xFF)
        h *= 0x100000001b3L
    }
    return "f" + h.toULong().toString(16).padStart(16, '0')
}

/** 「LXGWWenKai-Regular.ttf」→「LXGWWenKai-Regular」；空的给个名字。最长 40 个字。 */
fun fontDisplayName(fileName: String): String {
    val base = fileName.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.').trim()
    return base.ifEmpty { "导入的字体" }.take(40)
}

/** 从应用存储里的字体文件造一个 `FontFamily`（预览那一行用；上游开缝之后弹幕画面也用它）。读不了 → null。 */
expect fun fileFontFamily(path: String): FontFamily?
