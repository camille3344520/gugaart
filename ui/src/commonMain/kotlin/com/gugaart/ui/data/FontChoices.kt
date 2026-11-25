package com.gugaart.ui.data

import com.gugugaga.danmaku.settings.DanmakuFont

/** 字体单子里的一项（设置页的选择器、播放器面板、电视的一行共用）。[key]：`system` / [StyleFont.id] / `imported:<id>`。 */
class FontChoice(val key: String, val label: String, val note: String?, val selected: Boolean, val pick: () -> Unit)

/**
 * 字幕的字体单子：系统 + 五款 + 导入的（导入的排最后，带「导入」）。选中 = [SubStylePrefs.font]。
 * 要下载的那款，选了就开始下（[prefetch]），右边小字写下载状态（[fontNote]）。
 */
fun subtitleFontChoices(
    current: String,
    imported: ImportedFontState,
    states: Map<StyleFont, FontFileState>,
    set: (String) -> Unit,
    prefetch: (StyleFont) -> Unit,
): List<FontChoice> =
    StyleFont.entries.map { f ->
        FontChoice(f.id, f.label, fontNote(f, states[f]), current == f.id) {
            set(f.id)
            if (f.downloadBytes > 0) prefetch(f)
        }
    } + imported.fonts.map { f ->
        val key = IMPORTED_PREFIX + f.id
        FontChoice(key, f.name, "导入", current == key) { set(key) }
    }

/**
 * 弹幕的字体单子：导入的排最上面（用户 2026-11-14「入口放在最上面」），再系统 + 五款。三处存着「选的是哪一款」：
 * 导入的选中（[ImportedFontState.selected]）> 画报的 [GugaPrefs.danmakuFont]（上游没有的那两款）> 上游的 `danmaku.render.font`。
 * 选一款就把另外两处清掉（上游有的那款写上游的键）。
 */
fun danmakuFontChoices(
    upstream: DanmakuFont,
    extra: String?,
    imported: ImportedFontState,
    states: Map<StyleFont, FontFileState>,
    setUpstream: (DanmakuFont) -> Unit,
    setExtra: (String?) -> Unit,
    selectImported: (String?) -> Unit,
    prefetch: (StyleFont) -> Unit,
): List<FontChoice> {
    val extraFont = StyleFont.of(extra)?.takeIf { it.danmaku == null }
    return imported.fonts.map { f ->
        FontChoice(IMPORTED_PREFIX + f.id, f.name, "导入", imported.selected == f.id) {
            setExtra(null)
            selectImported(f.id)
        }
    } + StyleFont.entries.map { f ->
        val on = imported.selected == null && if (extraFont != null) f == extraFont else f.danmaku == upstream
        FontChoice(f.id, f.label, fontNote(f, states[f]), on) {
            selectImported(null)
            if (f.danmaku != null) {
                setExtra(null)
                setUpstream(f.danmaku)
            } else {
                setExtra(f.id)
                if (f.downloadBytes > 0) prefetch(f)
            }
        }
    }
}

/** 单子里现在选中的那一项的名字（行右边的值）；都没选中（导入的被删了）→ 第一项。 */
fun List<FontChoice>.currentLabel(): String = (firstOrNull { it.selected } ?: firstOrNull())?.label.orEmpty()
