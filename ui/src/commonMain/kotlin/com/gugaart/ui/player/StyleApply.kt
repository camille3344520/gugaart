package com.gugaart.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import com.gugaart.ui.data.FontFileState
import com.gugaart.ui.data.IMPORTED_PREFIX
import com.gugaart.ui.data.ImportedFontState
import com.gugaart.ui.data.LocalFontImport
import com.gugaart.ui.data.ResolvedFont
import com.gugaart.ui.data.StyleFont
import com.gugaart.ui.data.StyleFontStore
import com.gugaart.ui.data.StyleFonts
import com.gugaart.ui.data.fileFontFamily
import com.gugaart.ui.data.fontFamilyName
import com.gugaart.ui.data.parentDir
import com.gugaart.ui.data.readFontFile
import com.gugaart.ui.screens.settings.rememberGugaStore
import com.gugaart.ui.screens.settings.rememberImportedFonts
import com.gugaart.ui.theme.GugaFonts
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.danmaku.ui.rememberDanmakuFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/*
 * 字幕样式 / 弹幕字体落到播放器上（1.4.1 P3）：
 *  - 字幕：画报的 `subStyle` → 上游 `PlayerEngine.subtitleStyle`（引擎或样式一变就下发；字体要的文件先解包 / 下载好）；
 *  - 弹幕：选的不是上游 `:danmaku` 自带的那几款（导入的 / 思源宋体 / 站酷快乐体）→ 上游的缝 `LocalDanmakuFontOverride`。
 * 三端的播放器在 [GugaPlayerScreen] 一个地方接（不是每端各写一遍）。
 */

/** 全进程一个的字体文件仓（[StyleFonts.of]）。 */
@Composable
fun rememberStyleFonts(): StyleFontStore {
    val local = LocalGraph.current.deps.local
    val host = LocalFontImport.current
    return remember(local, host) { StyleFonts.of(local, host) }
}

/**
 * 一个字体选择（`system` / [StyleFont.id] / `imported:<id>`）→ 磁盘上的样子；要的文件先解包 / 下载（[StyleFontStore.ensure]）。
 * 系统字体 / 还在下载 / 失败 / 平台放不了文件 → null（内核用它自己的默认字体）。
 */
suspend fun resolveFont(choice: String, fonts: StyleFontStore, imported: ImportedFontState): ResolvedFont? {
    if (choice.startsWith(IMPORTED_PREFIX)) {
        val path = imported.fonts.firstOrNull { it.id == choice.removePrefix(IMPORTED_PREFIX) }?.path ?: return null
        val family = withContext(Dispatchers.Default) { readFontFile(path)?.let(::fontFamilyName) } ?: return null
        return ResolvedFont(family, path, parentDir(path))
    }
    val font = StyleFont.of(choice) ?: return null
    val family = font.family ?: return null
    val paths = fonts.ensure(font) ?: return null
    return ResolvedFont(family, paths.first(), parentDir(paths.first()))
}

@Composable
fun rememberResolvedFont(choice: String): ResolvedFont? {
    val fonts = rememberStyleFonts()
    val importedStore = rememberImportedFonts()
    val imported by importedStore.state.collectAsState()
    val importedPath = imported.fonts.firstOrNull { IMPORTED_PREFIX + it.id == choice }?.path
    return produceState<ResolvedFont?>(null, choice, importedPath, fonts) { value = resolveFont(choice, fonts, imported) }.value
}

/**
 * 把画报的字幕样式应用到当前引擎（照 [RememberShaderApply] 的做法）：引擎、样式或字体文件一变就下发；面板 / 设置页改的是同一个 store，
 * 所以改完立刻生效（mpv、ExoPlayer），下次开播 / 换内核也沿用。引擎没有这条缝（[com.gugugaga.app.player.PlayerEngine.subtitleStyle] 为 null）就什么都不做。
 */
@Composable
fun RememberSubtitleStyleApply(play: PlaySession) {
    val store = rememberGugaStore()
    val prefs by store.prefs.collectAsState()
    val engine by play.controller.engineFlow.collectAsState()
    val font = rememberResolvedFont(prefs.subStyle.font)
    val style = remember(prefs.subStyle, font) { prefs.subStyle.toEngine(font) }
    LaunchedEffect(engine, style) { engine?.subtitleStyle?.set(style) }
}

/**
 * 弹幕画面该用的「外部」字体：导入的选中 > 画报的 [com.gugaart.ui.data.GugaPrefs.danmakuFont]（思源宋体 = 界面那份宋体；站酷快乐体 = 下载的文件）；
 * 都没有 → null（上游按 `danmaku.render.font` 画）。站酷快乐体还没下好 → 先开始下，在那之前 null。
 */
@Composable
fun rememberDanmakuFontOverride(): FontFamily? {
    val prefs by rememberGugaStore().prefs.collectAsState()
    val imported by rememberImportedFonts().state.collectAsState()
    val fonts = rememberStyleFonts()
    val states by fonts.states.collectAsState()
    imported.current?.let { f -> return remember(f.path) { fileFontFamily(f.path) } }
    val extra = StyleFont.of(prefs.danmakuFont)?.takeIf { it.danmaku == null } ?: return null
    return rememberStyleFontFamily(extra, states[extra])
}

/**
 * 一款 [StyleFont] 在界面里（预览、弹幕）的 [FontFamily]：上游有的用上游 `:danmaku` 的资源；思源宋体用界面的宋体（[GugaFonts.song]，400 / 600 / 900 三档真字重）；
 * 要下载的用下载好的文件（还没下好 → 开始下，先 null）；系统字体 → null。
 */
@Composable
fun rememberStyleFontFamily(font: StyleFont, state: FontFileState?): FontFamily? {
    font.danmaku?.let { return if (font == StyleFont.System) null else rememberDanmakuFontFamily(it) }
    if (font == StyleFont.NotoSerifSC) return GugaFonts.song
    val fonts = rememberStyleFonts()
    // 只按字体起一次：以前键里带着「是不是 Absent」，ensure() 一标「下载中」键就变、把自己取消了，状态卡在「下载中…」
    // （电视字体选择器只把焦点停在站酷快乐体上就会这样）。开始下了就下完（NonCancellable），焦点移走也不白下。
    LaunchedEffect(font) { if (fonts.stateOf(font) !is FontFileState.Ready) withContext(NonCancellable) { fonts.ensure(font) } }
    val path = (state as? FontFileState.Ready)?.paths?.firstOrNull() ?: return null
    return remember(path) { fileFontFamily(path) }
}

/** 一个字体选择（`system` / [StyleFont.id] / `imported:<id>`）在界面里的 [FontFamily]（字幕预览用）；null = 平台黑体。 */
@Composable
fun rememberChoiceFamily(choice: String): FontFamily? {
    val imported by rememberImportedFonts().state.collectAsState()
    if (choice.startsWith(IMPORTED_PREFIX)) {
        val path = imported.fonts.firstOrNull { IMPORTED_PREFIX + it.id == choice }?.path ?: return null
        return remember(path) { fileFontFamily(path) }
    }
    val font = StyleFont.of(choice) ?: return null
    val states by rememberStyleFonts().states.collectAsState()
    return rememberStyleFontFamily(font, states[font])
}
