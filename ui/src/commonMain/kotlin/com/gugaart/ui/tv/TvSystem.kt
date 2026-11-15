package com.gugaart.ui.tv

import androidx.compose.runtime.Composable
import com.gugaart.ui.Today

/** 记录 / 收藏 + 读者证（`p=me`：默认 / `fav` / `favnone`）。[tab]："favorites" = 收藏。 */
@Composable
fun TvMeScreen(today: Today, tab: String?) {
    com.gugaart.ui.tv.system.TvMe(today, tab)
}

/** 设置（`p=settings`）：左页分区、右页条目、选择器。设置树复用 `screens/settings/`。 */
@Composable
fun TvSettingsScreen(section: String?, today: Today) {
    com.gugaart.ui.tv.system.TvSettings(section, today)
}

/** 服务器 / 登录（`p=servers`：默认 / `login` / `kbd` 遥控器键盘 / `fail`）。逻辑复用 `screens/login/LoginModel.kt`；不弹系统输入法。 */
@Composable
fun TvLoginScreen(today: Today) {
    com.gugaart.ui.tv.system.TvLogin(today)
}

/** 更新号外（`p=update`：默认 / `dl` / `force`）。 */
@Composable
fun TvUpdateExtra() {
    com.gugaart.ui.tv.system.TvUpdateSlip()
}
