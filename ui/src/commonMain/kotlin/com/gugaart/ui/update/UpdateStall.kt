package com.gugaart.ui.update

import com.gugaart.ui.screens.update.UpdateConsent
import com.gugugaga.app.AppGraph
import com.gugugaga.app.update.InstallOutcome
import com.gugugaga.app.update.UpdateState

/*
 * 安装确认被放到后台以后卡在「正在安装……」（用户 2026-11-16：「安卓更新的时候点后台，再次点击没反应，要应用重开才可以」）。
 *
 * 上游在安卓上的安装：下完 → 提交 PackageInstaller 会话 → 系统广播「要用户确认」→ 从广播里打开系统的安装确认。两种情况这一步落空，
 * 上游一直停在 `Installing`（号外的按钮是灰的「正在安装……」，只有重开应用才回来）：
 *  - 下完的那一刻应用在后台（点了「放到后台」再回桌面）：安卓不许后台应用弹界面，安装确认根本没出来；
 *  - 安装确认出来了，用户按了主页键：确认在系统安装器自己的任务里，回到应用看不到它。
 * 这里（不改上游）：应用回到前台时，交给系统安装器已经超过 [GRACE_MS] 还在 `Installing` = 确认没被回答，
 * 按上游自己的「安装失败」那条路报一次（[InstallOutcome.Failed]）→ 号外变成「再试一次」；再试一次时文件还在，验过 SHA-256 直接重新交给安装器，
 * 不用重新下载。号外被「放到后台」收起过的，也重新叫出来。
 */
object UpdateStall {
    /** 交给安装器之后多久还没有回音才算被放下了（安装确认弹出来要几百毫秒）。 */
    const val GRACE_MS = 1_500L

    const val MESSAGE = "安装没有完成（安装确认被关掉或放到了后台）。点「再试一次」接着装，不用重新下载。"

    /** 上游状态机听安装结果的那个回调（经 [GithubUpdate.wrap] 截下来）。 */
    private var listener: ((InstallOutcome) -> Unit)? = null

    /** 最近一次交给系统安装器（`Started`）的时间；0 = 没有在等。 */
    private var handedOverAt = 0L

    internal fun listen(l: ((InstallOutcome) -> Unit)?) {
        listener = l
    }

    internal fun handedOver(at: Long) {
        handedOverAt = at
    }

    /** 安装器自己回了话（装好 / 取消 / 失败）：不再等。 */
    internal fun answered() {
        handedOverAt = 0L
    }

    /** 现在是不是卡住了：还在 `Installing`、交给安装器已经超过 [grace]。纯函数，有单测。 */
    fun stalled(state: UpdateState, handedOverAt: Long, now: Long, grace: Long = GRACE_MS): Boolean =
        state is UpdateState.Installing && handedOverAt > 0L && now - handedOverAt >= grace

    /**
     * 应用回到前台（安卓壳的 onResume）或用户在设置里点「检查更新」：卡住了就报「安装没有完成」，号外重新出来、按钮变成「再试一次」。
     * 返回 true = 报了。
     */
    fun check(graph: AppGraph, now: Long = com.gugaart.ui.screens.update.UpdateNudge.nowMs()): Boolean {
        val st = graph.update.state.value
        if (st !is UpdateState.Installing) {
            handedOverAt = 0L
            return false
        }
        if (!stalled(st, handedOverAt, now)) return false
        val report = listener ?: return false
        handedOverAt = 0L
        UpdateConsent.reshow(st.release.versionCode)
        report(InstallOutcome.Failed(MESSAGE))
        return true
    }
}
