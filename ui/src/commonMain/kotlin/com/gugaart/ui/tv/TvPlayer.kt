package com.gugaart.ui.tv

import androidx.compose.runtime.Composable
import com.gugaart.ui.player.PlaySession
import com.gugaart.ui.tv.player.TvPlayerScreen
import com.gugugaga.app.player.PlayerDanmaku
import com.gugugaga.app.player.PlayerSlots

/**
 * 电视播放器的画报外观（`docs/player.md` §电视，设计稿 `tv/index.html?p=player`）：OSD 每个按钮有焦点；页边面板画面让位（左右改值、上下换功能）；
 * 快进带预览；跳过片头 / 下一集出现即获焦。逻辑（`PlayController`、引擎、弹幕）一行不动。版式和键位在 `tv/player/TvPlayerScreen.kt`。
 */
@Composable
fun TvPlayer(play: PlaySession, danmaku: PlayerDanmaku, slots: PlayerSlots) {
    TvPlayerScreen(play, danmaku, slots)
}
