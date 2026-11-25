package com.gugaart.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.ActionWord
import com.gugaart.ui.components.RadioMark
import com.gugaart.ui.components.SettingGroup
import com.gugaart.ui.components.SettingRow
import com.gugaart.ui.data.FontImportError
import com.gugaart.ui.data.ImportedFontStore
import com.gugaart.ui.data.LocalFontImport
import com.gugaart.ui.data.fileFontFamily
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.ui.LocalGraph
import kotlinx.coroutines.launch

/**
 * 导入字体仓（列表 + 选中，存在上游的 `LocalStore` 里；平台那一半由壳经 [LocalFontImport] 给）。全进程一个（[ImportedFontStore.of]）：
 * 设置页、播放器面板、弹幕画面看的是同一份选中状态，面板里一换，画面立刻跟着换。
 */
@Composable
fun rememberImportedFonts(): ImportedFontStore {
    val local = LocalGraph.current.deps.local
    val host = LocalFontImport.current
    return remember(local, host) { ImportedFontStore.of(local, host) }
}

/** 电视 / 没有文件选择器的平台上那一句说明。 */
const val FONT_IMPORT_UNAVAILABLE = "这台设备上没有文件选择器，导入不了；在手机或电脑上导入的字体存在各自的设备里，不会同步过来。"


/**
 * 设置 › 弹幕 **最上面**的一组：导入字体（用户 2026-11-14）。
 * 「导入字体…」→ 平台的文件选择器（ttf / otf）→ 复制进应用存储 → 进列表并选中；每款一行：单选记号 + 名字 + 用这款字体排的一句样张 + 「删除」。
 * 「用内置字体」= 取消选中导入的，回到下面「样式 · 字体」里上游内置的四款。
 */
@Composable
fun DanmakuFontImportGroup() {
    val colors = Guga.colors
    val store = rememberImportedFonts()
    val host = LocalFontImport.current
    val state by store.state.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<FontImportError?>(null) }
    var busy by remember { mutableStateOf(false) }

    SettingGroup("弹幕字体", "FONTS")
    SettingRow("导入字体", note = if (!store.canImport) FONT_IMPORT_UNAVAILABLE else "从本机选一个 ttf / otf，复制进应用；和内置的四款并列", tag = "set-dm-font-import") {
        if (store.canImport) {
            ActionWord(
                if (busy) "正在导入…" else "导入字体…",
                {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val picked = host?.pick()
                            if (picked != null) error = store.import(picked)
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.testTag("set-dm-font-pick"),
            )
        }
    }
    error?.let { BasicText(it.text, Modifier.padding(start = 20.dp, bottom = 8.dp).testTag("set-dm-font-error"), style = Guga.note(Guga.type.caption * 1.04f, colors.seal), maxLines = 2) }
    for (f in state.fonts) {
        val on = f.id == state.selected
        val family = remember(f.path) { fileFontFamily(f.path) }
        SettingRow(f.name, tag = if (on) "set-dm-font-on" else "set-dm-font-off", onClick = { store.select(f.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RadioMark(on)
                ActionWord("删除", { store.remove(f.id) }, danger = true)
            }
        }
        // 样张：用这款字体排（弹幕是画面里的字，样张也按弹幕的样子：米白、中等字重）
        BasicText(
            "弹幕预览 · 前方高能 233 awsl",
            Modifier.fillMaxWidth().padding(start = 20.dp, bottom = 10.dp).testTag("set-dm-font-sample"),
            style = Guga.song(Guga.type.row, color = if (on) colors.ink else colors.dim).let { if (family != null) it.copy(fontFamily = family) else it },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (state.fonts.isNotEmpty()) {
        SettingRow("用内置字体", note = "回到下面「样式 · 字体」选的那一款", tag = "set-dm-font-builtin", onClick = { store.select(null) }) { RadioMark(state.selected == null) }
        // 导入的字体字幕也能用（设置 › 字幕与音轨 › 字幕样式 · 字体）
    }
}
