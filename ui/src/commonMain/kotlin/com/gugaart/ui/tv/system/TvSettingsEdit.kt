package com.gugaart.ui.tv.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.TvSpec
import com.gugaart.ui.tv.detail.TvActionButton
import com.gugaart.ui.tv.detail.TvBlockFocus
import com.gugaart.ui.tv.detail.TvDir
import com.gugaart.ui.tv.TvFocusKind
import com.gugaart.ui.tv.detail.TvGroup
import com.gugaart.ui.tv.detail.TvGroups
import com.gugaart.ui.tv.detail.enterWhenReady
import com.gugugaga.ds.focus.FocusRegistry

/**
 * 设置里要打字的一项（[TvText]：弹幕服务器地址、TMDB 令牌、strm 的请求头）：和登录页一样用**电视自己的输入法**
 * （主理人 2026-11-28：「弹幕服务器地址输入怎么被改了，又改成隐藏，然后不能用电视输入法搜索了」）。
 *
 * 进来就是编辑：真文本框拿到焦点、弹系统输入法（[TvEditText]，登录页同一个）；输入法的「完成」= 保存。
 * 按下 = 离开文本框到下面一排「保存 / 清空」（密钥多一个「显示 / 隐藏」）；在那一排按上 = 回到文本框接着编辑。返回 = 不改。
 * 弹幕服务器地址不打码（地址里的令牌主理人要看得见、能改）；TMDB 令牌照旧默认打码。
 */
object TvSettingsEdit {
    const val FIELD = "edit-field"
    const val ACTIONS = "edit-actions"
    const val SAVE = "edit-save"
    const val CLEAR = "edit-clear"
    const val REVEAL = "edit-reveal"

    /** 测试看：现在是不是在文本框里（输入法开着）。 */
    var typing: Boolean = false
        internal set

    /** 测试用：往文本框里填字（离屏测试没有真的输入法）。打字那一屏开着时才有。 */
    var input: ((String) -> Unit)? = null
        internal set

    /** 地址类的项用网址键盘（输入法会给「/」「.」「.com」）。 */
    fun keyboardOf(edit: TvText): KeyboardType = if (edit.hint.startsWith("http")) KeyboardType.Uri else KeyboardType.Ascii
}

@Composable
internal fun TvSettingsEditPane(
    title: String,
    edit: TvText,
    draft: String,
    onDraft: (String) -> Unit,
    typing: Boolean,
    onTyping: (Boolean) -> Unit,
    reveal: Boolean,
    onReveal: () -> Unit,
    onSave: () -> Unit,
    groups: TvGroups,
    focus: FocusRegistry,
) {
    val colors = Guga.colors
    val mask = edit.secret && !reveal
    LaunchedEffect(typing) {
        TvSettingsEdit.typing = typing
        if (!typing) {
            // 打完字往下 = 落在「保存」（不记上一回停在哪个键）
            groups.forget(TvSettingsEdit.ACTIONS)
            groups.enterWhenReady(TvSettingsEdit.ACTIONS, focus)
        }
    }
    DisposableEffect(onDraft) {
        TvSettingsEdit.input = onDraft
        onDispose { TvSettingsEdit.input = null }
    }
    Column(Modifier.fillMaxSize().padding(start = TvSpec.LEFT, end = TvSpec.RIGHT, top = 44.dp).testTag("tv-settings-edit")) {
        BasicText(title, Modifier.padding(bottom = 22.dp), style = Guga.song(38.sp, FontWeight.Black), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(edit.english.ifEmpty { edit.label }, Modifier.padding(bottom = 10.dp), style = Guga.song(22.sp, color = colors.spot), maxLines = 1)
        TvGroup(TvSettingsEdit.FIELD, listOf(TvSettingsEdit.FIELD), horizontal = true, default = TvSettingsEdit.FIELD)
        TvBlockFocus(
            TvSettingsEdit.FIELD,
            { onTyping(true) },
            Modifier.fillMaxWidth().then(if (typing) Modifier.background(colors.paper).border(3.dp, colors.spot) else Modifier),
            label = edit.label,
            kind = TvFocusKind.Row,
        ) { focused ->
            Box(Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterStart) {
                if (typing) {
                    TvEditText(
                        draft, onDraft, mask, TvSettingsEdit.keyboardOf(edit), ImeAction.Done,
                        onIme = onSave,
                        onLeave = { dir -> if (dir == TvDir.Down || dir == TvDir.Up) onTyping(false) },
                    )
                } else {
                    val shown = if (mask) "•".repeat(draft.length) else draft
                    BasicText(
                        shown.ifEmpty { edit.hint },
                        style = Guga.numeral(40.sp, if (focused) colors.onInk else if (shown.isEmpty()) colors.faint else colors.ink),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        edit.note?.let { BasicText(it, Modifier.padding(top = 14.dp), style = Guga.song(24.sp, color = colors.dim), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.height(36.dp))
        val actions = listOfNotNull(TvSettingsEdit.SAVE, TvSettingsEdit.REVEAL.takeIf { edit.secret }, TvSettingsEdit.CLEAR)
        TvGroup(TvSettingsEdit.ACTIONS, actions, horizontal = true, default = TvSettingsEdit.SAVE)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            for (id in actions) {
                val label = when (id) {
                    TvSettingsEdit.SAVE -> "保存"
                    TvSettingsEdit.REVEAL -> if (reveal) "隐藏" else "显示"
                    else -> "清空"
                }
                TvActionButton(
                    id,
                    label,
                    {
                        when (id) {
                            TvSettingsEdit.SAVE -> onSave()
                            TvSettingsEdit.REVEAL -> onReveal()
                            else -> onDraft("")
                        }
                    },
                    Modifier.width(200.dp),
                    primary = id == TvSettingsEdit.SAVE,
                )
            }
        }
    }
}
