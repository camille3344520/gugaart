package com.gugaart.ui.player

import com.gugugaga.app.player.ErrorActionId
import com.gugugaga.app.player.ErrorState
import com.gugugaga.app.player.FailureStage
import com.gugugaga.app.player.PlayController
import com.gugugaga.app.player.PlayerSnapshot

/*
 * 播放出错页三端统一（审计 P2-1，1.5.1）：标题「播放失败」，下面一行写原因；按钮「重试」「返回」（上游 `ErrorView.actions` 的名字）。
 * 电视没有「复制错误信息」（复制了也没地方贴），手机和电脑保留。
 */

/** 出错页的标题。 */
const val PLAY_ERROR_TITLE = "播放失败"

/**
 * 标题下面那一行原因：找不到内核 = 上游那句；拿不到播放信息；有错误码 = 上游 `ErrorState.describeCode` 的短句（「网络连接失败」「加载超时」…）；
 * 都没有 = 上游安静出错页的那句。纯函数，有单测。
 */
fun playErrorReason(snap: PlayerSnapshot): String = when {
    snap.error == PlayController.NO_ENGINE_MESSAGE -> PlayController.NO_ENGINE_MESSAGE
    snap.failure?.stage == FailureStage.Prepare -> "无法获取播放信息"
    snap.failure?.code != null -> ErrorState.describeCode(snap.failure?.code).title
    else -> snap.errorView?.title ?: "无法播放此视频"
}

/** 出错页的按钮：上游给的出路（没有 = 重试 / 返回），去掉网页内核那一项，最多四个。 */
fun playErrorActions(snap: PlayerSnapshot): List<ErrorActionId> =
    (snap.errorView?.actions ?: listOf(ErrorActionId.Retry, ErrorActionId.Exit)).filter { it != ErrorActionId.UseWeb }.take(4)
