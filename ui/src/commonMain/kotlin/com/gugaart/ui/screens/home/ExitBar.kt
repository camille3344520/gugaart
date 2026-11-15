package com.gugaart.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.nav.ExitConfirm
import kotlinx.coroutines.delay

/**
 * 首页「再按一次返回退出」的小条（设计稿 `home-exit`）。**什么时候算「再按一次」是上游的 `ExitConfirm`**（两秒的窗口，壳里用它判断）；
 * 这里只有外观和「现在该不该显示」。
 *
 * 壳的接法（和上游壳一样，只是把上游的 Toast 换成这一条）：
 *
 *     val exitConfirm = ExitConfirm(clock = { SystemClock.uptimeMillis() })
 *     // 系统返回，应用自己的返回栈没有消费掉：
 *     if (exitConfirm.onRootBack()) finish() else ExitHint.show()
 *
 * 只有手机和电视用（Windows 的窗口没有「返回桌面」）。
 */
@Stable
object ExitHint {
    /** 小条现在显示着。 */
    var visible: Boolean by mutableStateOf(false)
        private set
    internal var shownCount by mutableIntStateOf(0)
        private set

    /** 第一次按返回：出小条，[ExitConfirm.EXIT_CONFIRM_WINDOW_MS] 之后自己收掉。 */
    fun show() {
        visible = true
        shownCount++
    }

    fun hide() {
        visible = false
    }
}

/** 画那一条：米白实底、墨字，「EXIT」小框 + 一句话；贴在标签栏上方正中，不挡封面和按钮。放在根的 Box 里（由根决定位置）。 */
@Composable
fun ExitBar(modifier: Modifier = Modifier) {
    val colors = Guga.colors
    LaunchedEffect(ExitHint.shownCount) {
        if (ExitHint.visible) {
            delay(ExitConfirm.EXIT_CONFIRM_WINDOW_MS)
            ExitHint.hide()
        }
    }
    AnimatedVisibility(ExitHint.visible, modifier, enter = fadeIn(), exit = fadeOut()) {
        Row(
            Modifier.background(colors.ink).padding(horizontal = 12.dp, vertical = 9.dp).testTag("exit-bar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BasicText("EXIT", Modifier.border(1.dp, colors.onInk).padding(horizontal = 6.dp, vertical = 1.dp), style = Guga.numeral(10.sp, colors.onInk, letterSpacing = 0.2.em), maxLines = 1)
            BasicText("再按一次返回就散场", style = Guga.song(14.sp, FontWeight.Bold, colors.onInk, letterSpacing = 0.06.em), maxLines = 1)
        }
    }
}
