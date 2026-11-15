package com.gugaart.ui.screens.update

import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SheetHost
import com.gugaart.ui.components.SlipHeader
import com.gugaart.ui.components.TextLink
import com.gugaart.ui.components.progressLine
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.MascotPoseNamed
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.app.update.ReleaseInfo
import com.gugugaga.app.update.UpdateState
import com.gugugaga.app.update.releaseNotesText
import com.gugugaga.ds.icons.GuguIcon
import kotlin.math.roundToInt

/** 测试缝：直接给一个更新状态（离屏测试没有签过名的清单可取）。应用里永远是 null = 读上游的 `UpdateController`。 */
val LocalUpdateStateOverride = staticCompositionLocalOf<UpdateState?> { null }

/** 号外现在在哪一步。 */
enum class ExtraPhase { Offer, Downloading, Ready, Installing, Permission, Failed }

/** 号外上要印的东西。全部来自上游的更新状态（验过签的清单）；没有的不写。 */
data class ExtraView(
    val version: String,
    val forced: Boolean,
    val phase: ExtraPhase,
    /** 「38.6 MB · 2026-11-14 · 签名已校验」。 */
    val meta: String,
    /** 改了什么：一行一条，最多 4 条（强制更新 2 条）。 */
    val notes: List<String>,
    val done: Long = 0,
    val total: Long = 0,
    /** 失败的原因（上游给的中文）；移动网络的提醒。 */
    val message: String? = null,
) {
    val progress: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/** 字节 → 「38.6 MB」。 */
fun megabytes(bytes: Long): String {
    val tenths = (bytes / 104_857.6).roundToInt()
    return "${tenths / 10}.${tenths % 10} MB"
}

/** 更新说明 → 号外上的几条：按行拆，去掉行首的「-」「*」「1.」这类记号和空行。 */
fun noteLines(notes: String, limit: Int): List<String> =
    notes.lines().map { it.trim().replace(Regex("^([-*•·]|\\d+[.、)])\\s*"), "").trim() }.filter { it.isNotEmpty() }.take(limit)

/**
 * 上游的更新状态 → 号外。null = 不出号外：没有要处理的更新，或者这一版用户按过「稍后」（[dismissed] = 那一版的版本号）。
 * 强制更新（`forced`）收不掉。纯函数，有单测。
 */
fun extraView(state: UpdateState, dismissed: Int, ignored: Int = 0): ExtraView? {
    fun view(r: ReleaseInfo, forced: Boolean, phase: ExtraPhase, size: Long? = null, done: Long = 0, total: Long = 0, message: String? = null): ExtraView? {
        if (!forced && r.versionCode == dismissed) return null
        // 「忽略这一版」：这一版的提示不再出（用户自己点了下载的那几步照常显示）
        if (!forced && phase == ExtraPhase.Offer && ignored != 0 && r.versionCode == ignored) return null
        val meta = listOfNotNull(size?.takeIf { it > 0 }?.let(::megabytes), r.publishedAt.take(10).takeIf { it.length == 10 }, "签名已校验").joinToString(" · ")
        return ExtraView(r.version, forced, phase, meta, noteLines(releaseNotesText(r.notes), if (forced) 2 else 4), done, total, message)
    }
    return when (state) {
        is UpdateState.Available -> view(state.release, state.forced, ExtraPhase.Offer, state.asset.size, message = if (state.mobileData) "现在是移动网络，更新会用流量。" else null)
        is UpdateState.Downloading -> view(state.release, state.forced, ExtraPhase.Downloading, state.total, state.done, state.total)
        is UpdateState.Downloaded -> view(state.release, state.forced, ExtraPhase.Ready, state.asset.size)
        is UpdateState.Installing -> view(state.release, state.forced, ExtraPhase.Installing)
        is UpdateState.NeedsPermission -> view(state.release, state.forced, ExtraPhase.Permission)
        is UpdateState.Failed -> state.release?.let { view(it, state.forced, ExtraPhase.Failed, message = state.message) }
        else -> null
    }
}

/**
 * 更新提示「号外」（设计稿 `p=update`：默认、`dl`、`done`、`force`）。挂在根上一处；检查、下载（断点续传 + SHA-256）、安装全部是上游
 * `UpdateController` 的——这里只把它的状态排成一张纸片，按钮调它的公开方法。
 * 壳里没有更新地址 / 公钥（`UpdateConfig.enabled = false`）时上游从不发请求，这里也就什么都不出。
 */
@Composable
fun UpdateExtra() {
    val graph = LocalGraph.current
    val override = LocalUpdateStateOverride.current
    // 和上游根界面一样：启动 10 秒后查一次（一天最多一次）；Windows 上一次安装的结果说一遍
    LaunchedEffect(graph) {
        if (override != null) return@LaunchedEffect
        graph.update.startAutoCheck()
        graph.deps.updateInstallProblem?.invoke()?.let { graph.update.reportInstallProblem(it) }
        // 上游一天最多自己查一次——画报发版勤，新版出来以后要等到第二天才看得见（用户 2026-11-15：「现在还是需要手动覆盖安装」）。
        // 补上：启动后距上次检查满 1 小时就再查；开着不关的（电视）每 30 分钟看一眼，满 3 小时再查。清单是一个很小的签名文件。
        // 这一次运行里点过「稍后」就不再追着提醒。
        kotlinx.coroutines.delay(UpdateNudge.FIRST_MS)
        var wait = UpdateNudge.AFTER_START_MS
        while (true) {
            if (graph.update.enabled && graph.update.dismissedVersion.value == 0 && UpdateNudge.due(graph.update.status.value.lastCheckAt, UpdateNudge.nowMs(), wait)) {
                graph.update.checkNow()
            }
            kotlinx.coroutines.delay(UpdateNudge.TICK_MS)
            wait = UpdateNudge.EVERY_MS
        }
    }
    val live by graph.update.state.collectAsState()
    val dismissed by graph.update.dismissedVersion.collectAsState()
    val view = extraView(override ?: live, UpdateConsent.dismissedFor(dismissed), UpdateConsent.ignored) ?: return
    var permissionHint by remember { mutableStateOf(false) }
    val release = ((override ?: live) as? UpdateState.Available)?.release
    ExtraSlip(
        view,
        permissionHint = permissionHint,
        onUpdate = {
            when (view.phase) {
                // 下好了：用户点「现在安装」才交给安装器
                ExtraPhase.Ready -> graph.update.installDownloaded()
                ExtraPhase.Permission -> permissionHint = !graph.update.requestInstallPermission()
                // 「下载并安装」/「再试一次」：挑了它就是同意装
                else -> UpdateConsent.downloadAndInstall(graph)
            }
        },
        onLater = { UpdateConsent.hideAgain(); graph.update.dismiss() },
        onIgnore = release?.let { r -> { UpdateConsent.ignore(graph, r.versionCode) } },
    )
}

/** 号外这张纸片（只画）。强制更新没有「稍后」，点外面 / 返回也收不掉。 */
@Composable
fun ExtraSlip(view: ExtraView, permissionHint: Boolean, onUpdate: () -> Unit, onLater: () -> Unit, onIgnore: (() -> Unit)? = null) {
    val colors = Guga.colors
    val canDismiss = !view.forced
    SheetHost(if (canDismiss) onLater else null, tag = "update-extra") {
        SlipHeader("号外", "EXTRA · ${if (view.forced) "必须更新" else "新版本"}", "No. ${view.version}")
        Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val head = when {
                    view.phase == ExtraPhase.Ready -> "下好了，\n现在安装？"
                    view.phase == ExtraPhase.Failed -> "这一版\n没下好"
                    view.forced -> "这一版\n要先更新"
                    else -> "${view.version}\n到了"
                }
                BasicText(head, style = Guga.song(34.sp, FontWeight.Black, lineHeight = 42.sp), maxLines = 2)
                val sub = when {
                    view.phase == ExtraPhase.Failed -> view.message
                    permissionHint -> "请在系统设置里允许「咕咕嘎嘎」安装应用，再回来点一次。"
                    view.phase == ExtraPhase.Permission -> "需要先允许这个应用安装更新。"
                    view.forced && view.phase == ExtraPhase.Offer -> "这一版不能再用了。更新大约一分钟，登录和设置都会留着。"
                    else -> listOfNotNull(view.meta.takeIf { it.isNotEmpty() }, view.message).joinToString("\n")
                }
                if (!sub.isNullOrEmpty()) BasicText(sub, Modifier.padding(top = 10.dp), style = Guga.song(Guga.type.caption, color = if (view.phase == ExtraPhase.Failed) colors.seal else colors.dim, lineHeight = 20.sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            // 她举着号外，站在号外自己的一格里（G17）
            if (LocalGugaPrefs.current.showMascot) MascotPoseNamed("号外", 84.dp, Modifier.padding(start = 12.dp))
        }
        if (view.phase != ExtraPhase.Ready && view.notes.isNotEmpty()) {
            Rule(Modifier.padding(top = 18.dp))
            for ((i, line) in view.notes.withIndex()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    BasicText("${i + 1}", Modifier.width(28.dp), style = Guga.numeral(15.sp, colors.spot), maxLines = 1)
                    BasicText(line, Modifier.weight(1f), style = Guga.song(14.sp, lineHeight = 22.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Rule()
            }
        }
        if (view.phase == ExtraPhase.Downloading) {
            Box(Modifier.padding(top = 18.dp).fillMaxWidth().height(2.dp).progressLine(view.progress, colors.spot, track = colors.line, outside = false).testTag("update-progress"))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(if (view.total > 0) "${megabytes(view.done)} / ${megabytes(view.total)} · ${(view.progress * 100).roundToInt()}%" else megabytes(view.done), style = Guga.numeral(Guga.type.caption, colors.dim), maxLines = 1)
            }
        }
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            val (label, enabled) = when (view.phase) {
                ExtraPhase.Offer -> "下载并安装" to true
                ExtraPhase.Downloading -> "下载中……" to false
                ExtraPhase.Ready -> "现在安装" to true
                ExtraPhase.Installing -> "正在安装……" to false
                ExtraPhase.Permission -> "去允许" to true
                ExtraPhase.Failed -> "再试一次" to true
            }
            PrimaryButton(label, onUpdate, Modifier.testTag("update-go"), icon = GuguIcon.Download.takeIf { view.phase == ExtraPhase.Offer }, enabled = enabled)
            // 忽略这一版 · 稍后（强制更新没有）
            if (canDismiss) {
                if (view.phase == ExtraPhase.Offer && onIgnore != null) TextLink("忽略这一版", onIgnore, Modifier.testTag("update-ignore"), color = colors.dim)
                TextLink(
                    when (view.phase) {
                        ExtraPhase.Downloading -> "放到后台"
                        ExtraPhase.Ready -> "下次打开再装"
                        else -> "稍后"
                    },
                    onLater,
                    Modifier.testTag("update-later"),
                )
            }
        }
    }
}
