package com.gugaart.ui.tv.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.theme.Guga
import com.gugaart.ui.tv.detail.LocalTvGroups
import com.gugaart.ui.tv.detail.TvBlockFocus
import com.gugaart.ui.tv.detail.TvDir
import com.gugaart.ui.tv.detail.TvGroup
import com.gugaart.ui.tv.detail.TvGroups
import com.gugugaga.ds.focus.FocusRegistry

/**
 * 遥控器全键盘（设计稿 `st=kbd`：10 列）。几排字符 + 最下一行五个功能键。**不弹系统输入法**：登录页的地址 / 用户名 / 密码、
 * 设置里要打字的项（strm 的请求头、弹幕服务器地址、TMDB 令牌）用的是同一个键盘。
 */
object TvKeyboard {
    /** 登录用的五排。 */
    val ROWS = listOf("1234567890", "qwertyuiop", "asdfghjkl.", "zxcvbnm-_/", ":@#!?&=+%~")

    /** 设置里多一排：请求头里常见的括号、分号、逗号、引号（`Mozilla/5.0 (Windows NT 10.0; Win64; x64)`）。 */
    val ROWS_WIDE = ROWS + "()[];,'\"*$"

    /** 最下一行：大写 / 空格 / 删除 / 显示密码 / 下一项。 */
    val BOTTOM = listOf("shift", "space", "del", "show", "next")
    val BOTTOM_LABEL = mapOf("shift" to "大写", "space" to "空格", "del" to "删除", "show" to "显示密码", "next" to "下一项")

    fun key(row: Int, col: Int) = "k-$row-$col"

    /**
     * 从第 [row] 排第 [col] 个按上 / 下，落在哪个键上（最下一行 5 个键对上面 10 列：两列对一个）。到头 = null。
     * [rows] = 字符有几排（登录 5，设置 6）。纯函数，有单测。
     */
    fun vertical(row: Int, col: Int, down: Boolean, rows: Int = ROWS.size): String? {
        val to = row + if (down) 1 else -1
        return when {
            to < 0 || to > rows -> null
            to == rows -> "k-b-${BOTTOM[(col / 2).coerceIn(0, BOTTOM.lastIndex)]}"
            row == rows -> key(to, (col * 2).coerceIn(0, 9))
            else -> key(to, col)
        }
    }

    /** 按下一个键以后，输入框里的字。纯函数，有单测。 */
    fun type(text: String, key: String, upper: Boolean): String = when (key) {
        "space" -> "$text "
        "del" -> text.dropLast(1)
        else -> text + if (upper) key.uppercase() else key
    }

    /**
     * 键盘里的上 / 下（每一排是一个横向的组 `kb-<排>`，左右由 [TvGroups] 自己走；上 / 下落到正上 / 正下的键）。
     * 在这一屏的 `edge` 里调：[from] 是键盘的组并且方向是上 / 下 → 处理并返回 true（到头 = 不动）。
     */
    fun moveVertical(groups: TvGroups, registry: FocusRegistry, from: String, dir: TvDir, rows: Int = ROWS.size): Boolean {
        if (!from.startsWith("kb-") || (dir != TvDir.Up && dir != TvDir.Down)) return false
        val cur = groups.current.orEmpty()
        val row = from.removePrefix("kb-").toIntOrNull() ?: return false
        val col = if (row == rows) BOTTOM.indexOf(cur.removePrefix("k-b-")) else cur.substringAfterLast('-').toIntOrNull() ?: 0
        vertical(row, col, dir == TvDir.Down, rows)?.let { registry.request(it) }
        return true
    }
}

/**
 * 键盘本身：[rows] 排字符键（88 × 78）+ 最下一行五个功能键。每一排登记成一个组 `kb-<排>`；默认落在第二排第一个键上。
 * [upper] = 大写锁着；[bottomLabel] 给最下一行每个键的字（「下一项」在不同的步骤叫法不同）；[onKey] 拿到按下的字符或功能键的名字。
 */
@Composable
fun TvKeyboardPad(upper: Boolean, bottomLabel: (String) -> String, onKey: (String) -> Unit, modifier: Modifier = Modifier, rows: List<String> = TvKeyboard.ROWS, hint: String? = null) { // 电视不显示按键提示：默认不写「方向键选字」
    val colors = Guga.colors
    Column(modifier.testTag("tv-keyboard"), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        rows.forEachIndexed { r, chars ->
            TvGroup("kb-$r", chars.indices.map { TvKeyboard.key(r, it) }, default = if (r == 1) TvKeyboard.key(1, 0) else null)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                chars.forEachIndexed { c, ch ->
                    val shown = if (upper) ch.uppercaseChar() else ch
                    TvKey(TvKeyboard.key(r, c), shown.toString(), Modifier.width(88.dp)) { onKey(ch.toString()) }
                }
            }
        }
        TvGroup("kb-${rows.size}", TvKeyboard.BOTTOM.map { "k-b-$it" })
        Row(Modifier.width(961.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            for (k in TvKeyboard.BOTTOM) {
                TvKey("k-b-$k", bottomLabel(k), Modifier.weight(if (k == "space" || k == "next") 1.5f else 1f), word = true) { onKey(k) }
            }
        }
        if (hint != null) BasicText(hint, Modifier.padding(top = 14.dp), style = Guga.song(22.sp, color = colors.faint), maxLines = 1)
    }
}

/** 输入框（设计稿 `.kb-f label`）：小标 + 大号斜体的字 + 朱红光标（正在输入的那一格）；下面一条线。字太长只显示末尾 [tail] 个。 */
@Composable
fun TvField(label: String, value: String, active: Boolean, hint: String, tail: Int = 22) {
    val colors = Guga.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp).height(120.dp).drawBehind { drawLine(if (active) colors.ink else colors.line, Offset(0f, size.height), Offset(size.width, size.height), 2.dp.toPx()) }.testTag(if (active) "tv-field-active" else "tv-field")) {
        BasicText(label, style = Guga.song(22.sp, color = colors.spot, letterSpacing = 0.2.em), maxLines = 1)
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(value.takeLast(tail), style = Guga.numeral(54.sp, if (value.isEmpty()) colors.faint else colors.ink), maxLines = 1)
            if (active) Box(Modifier.padding(start = 6.dp).width(5.dp).height(56.dp).background(colors.seal))
            if (value.isEmpty() && hint.isNotEmpty()) BasicText(hint, Modifier.padding(start = 16.dp), style = Guga.song(22.sp, color = colors.faint), maxLines = 1)
        }
    }
}

/** 一个键（88 × 78）：细线框；焦点 = 米白实底 + 校对角。 */
@Composable
private fun TvKey(key: String, label: String, modifier: Modifier = Modifier, word: Boolean = false, onClick: () -> Unit) {
    val colors = Guga.colors
    val groups = LocalTvGroups.current
    TvBlockFocus(key, onClick, modifier.then(if (groups.current == key) Modifier else Modifier.border(1.5.dp, colors.line)), label = label) { focused ->
        Box(Modifier.fillMaxWidth().height(78.dp), contentAlignment = Alignment.Center) {
            BasicText(label, style = if (word) Guga.song(23.sp, FontWeight.Bold, if (focused) colors.onInk else colors.ink) else Guga.song(36.sp, color = if (focused) colors.onInk else colors.ink), maxLines = 1)
        }
    }
}
