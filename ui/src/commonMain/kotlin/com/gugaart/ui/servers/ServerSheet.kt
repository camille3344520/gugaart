package com.gugaart.ui.servers

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.components.SheetHost
import com.gugaart.ui.components.SlipHeader
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.ds.icons.Icon
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/**
 * 手机的「换一台服务器」（1.5.0 A2-UI）：贴底的纸片（和详情页「版本」同一种，拇指够得着），里面是 [ServerSwitcherList]。
 * 入口两个：首页报头下面那一行小字（[MastheadServerLine]）、「我的」页的「服务器」一行。[Host] 由手机的根画一次。
 */
object ServerSheet {
    var open: Boolean by mutableStateOf(false)

    fun show() {
        open = true
    }

    fun close() {
        open = false
    }

    @Composable
    fun Host() {
        if (!open) return
        val switcher = currentServerSwitcher()
        val state = rememberServerListState(switcher)
        state.onClose = { open = false }
        val entries by switcher.entries.collectAsState()
        // 返回键 / 点纸外：推开着管理时先收管理，再收纸片
        SheetHost({ if (state.managing != null) state.closeManage() else open = false }, tag = "server-sheet", bottom = true) {
            SlipHeader("服务器", "SERVERS", trailing = "${entries.size} 台")
            BasicText("切换服务器", Modifier.padding(top = 14.dp, bottom = 6.dp), style = Guga.song(Guga.type.section, FontWeight.Black, letterSpacing = 0.04.em), maxLines = 1)
            // 6 行半：多了的时候下面露半行（一看就知道能往下滑）
            ServerSwitcherList(state, ServerListLook.Phone, maxHeight = 440.dp)
        }
    }
}

/**
 * 首页报头下面的一行小字：当前服务器的名字 + 一个小折角（点它 = [ServerSheet]）。
 * 它自己**不占版面**（压在报头下线和海报流之间那一截空里），报头的刊名、海报流都不挪（1.4.0 刚把刊名摆正，不能动它）。
 * 热区（P1-5）：字只有 [SERVER_LINE_TEXT] 高；这一行对外只占这么高（报头的版面不变），里面按 [com.gugaart.ui.theme.GugaDimens.minTarget]
 * （手机 48 dp）排、字竖着居中——上下各透明扩出去 [SERVER_LINE_TOUCH_EXTRA]，字的大小和位置不变。
 */
@Composable
fun MastheadServerLine(modifier: Modifier = Modifier) {
    val name = currentServerTitle() ?: return
    val colors = Guga.colors
    val press = rememberPressState()
    Row(
        modifier
            .layout { m, c ->
                val p = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                val h = SERVER_LINE_TEXT.roundToPx()
                layout(p.width, h) { p.place(0, (h - p.height) / 2) }
            }
            .height(Guga.dimens.minTarget)
            .pressGraphics(press)
            .pressable(press, label = "换服务器：现在是 $name") { ServerSheet.show() }
            .testTag("masthead-server"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            name,
            Modifier.widthIn(max = 200.dp),
            style = Guga.song(Guga.type.kicker, FontWeight.Normal, colors.dim, letterSpacing = 0.14.em),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(GuguIcon.ChevronDown, colors.dim, 9.dp, Modifier.padding(start = 4.dp))
    }
}

/** 首页报头下那一行：字的高度（排版上它占的那一截）。 */
val SERVER_LINE_TEXT = 22.dp

/** 那一行的热区比字高出来的一半：热区 48 dp、字 22 dp → 上下各 13 dp（P1-5）。 */
val SERVER_LINE_TOUCH_EXTRA: androidx.compose.ui.unit.Dp @Composable get() = (Guga.dimens.minTarget - SERVER_LINE_TEXT) / 2
