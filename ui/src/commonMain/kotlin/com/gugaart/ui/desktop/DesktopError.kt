package com.gugaart.ui.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.gugaart.ui.data.ErrorKind
import com.gugaart.ui.data.errorKindOf
import com.gugaart.ui.mascot.LocalGugaPrefs
import com.gugaart.ui.mascot.MascotPoseNamed
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.icons.GuguIcon

/** 整页错误的版式数（设计稿 `desktop/css/d4.css` 的 `.errp`；单位 rem）。 */
object DeskErrorSpec {
    /** 大标题：8 rem、行高 1.2、字距 0.08 em（窄窗口里一行最多五个字）。 */
    const val H1 = 8f
    const val H1_LINE = 9.6f
    /** 导语：1.8 rem、行高 1.9、最宽 60 rem。 */
    const val LEAD = 1.8f
    const val LEAD_LINE = 3.4f
    const val LEAD_MAX = 60f
    /** 右页留给她的一格。 */
    const val POSE = 22f
    /** 页面下沿留白（`DesktopPage` 的 bottom）。 */
    val BOTTOM = 64.dp
}

/** 一张「通知」上写的东西。 */
internal class DeskErrorCopy(val kicker: String, val line1: String, val line2: String, val lead: String, val pose: String, val log: String)

/** 通知上的一个出路（第一个是主按钮，其余是文字链接）。 */
internal class DeskNoticeAction(val label: String, val icon: GuguIcon? = null, val onClick: () -> Unit)

/** 三种整页错误各写什么。[server] = 这台服务器的名字（没有会话 = null）。纯函数，有单测。 */
internal fun deskErrorCopy(kind: ErrorKind, server: String?): DeskErrorCopy {
    val name = server?.takeIf { it.isNotBlank() }?.let { "「$it」" } ?: "片库"
    return when (kind) {
        ErrorKind.Offline -> DeskErrorCopy(
            "OFFLINE · 暂停放映", "放映机", "连不上网",
            // 上游把「没收到任何应答」归成一类（断网、域名解析不了、连接被拒）：分不清是这台电脑断网还是服务器不在，两种都说
            "连不上${name}：可能是这台电脑的网络断了，也可能是服务器关机或者换了地址。网络回来以后点一下「重试」，就接着放。",
            "拔掉的电源插头", "没有收到服务器的应答",
        )
        ErrorKind.Server -> DeskErrorCopy(
            "SERVER · 片库没有应答", "这家片库", "没开门",
            "${name}迟迟没有应答。可能是服务器休眠了、正忙着，或者网络太慢。稍等一下再试；一直不行就换一条线路。",
            "摊手", "请求超时 / 服务器出错",
        )
        ErrorKind.Auth -> DeskErrorCopy(
            "SESSION · 观影卡过期", "观影卡", "过期了",
            "服务器说这张观影卡不能用了（可能在别处改了密码，或者管理员收回了）。重新登录一次就好，收藏和进度都还在服务器上。",
            "捧白纸", "401 Unauthorized · 令牌已失效",
        )
    }
}

/**
 * 一张「通知」（设计稿 `.errp`）：左页眉题、两行大标题、原因、能做什么、一行小字；右页是留给她的一格
 * （断网 G19 举着拔掉的插头、没应答 G08 摊手、读者证过期 G10 捧白纸；她关掉时只有左边）。
 */
@Composable
internal fun DeskNotice(kind: ErrorKind, copy: DeskErrorCopy, actions: List<DeskNoticeAction>, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val prefs = LocalGugaPrefs.current
    Row(modifier.fillMaxWidth().padding(horizontal = Guga.dimens.margin).testTag("desktop-error-${kind.name.lowercase()}"), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(7f)) {
            DeskKick(copy.kicker)
            BasicText(
                "${copy.line1}\n${copy.line2}",
                Modifier.padding(top = Guga.rem(1.6f)).testTag("desktop-error-h1"),
                style = Guga.song(rsp(DeskErrorSpec.H1), FontWeight.Black, letterSpacing = 0.08.em, lineHeight = rsp(DeskErrorSpec.H1_LINE)),
                maxLines = 2,
            )
            BasicText(
                copy.lead,
                Modifier.padding(top = Guga.rem(2.4f)).widthIn(max = Guga.rem(DeskErrorSpec.LEAD_MAX)),
                style = Guga.song(rsp(DeskErrorSpec.LEAD), color = colors.dim, lineHeight = rsp(DeskErrorSpec.LEAD_LINE)),
            )
            Row(Modifier.padding(top = Guga.rem(3.2f)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Guga.rem(1.8f))) {
                for ((i, a) in actions.withIndex()) {
                    if (i == 0) DeskButton(a.label, a.onClick, Modifier.testTag("desktop-error-primary"), icon = a.icon) else DeskLink(a.label, a.onClick)
                }
            }
            // 一行小字：出了什么事（分类，不是原文——原文可能带地址）
            BasicText(
                copy.log,
                Modifier
                    .padding(top = Guga.rem(2.8f))
                    .fillMaxWidth()
                    .drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                    .padding(top = Guga.rem(1.6f)),
                style = Guga.note(rsp(1.3f), colors.faint),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Guga.dimens.gutter * 2))
        Box(Modifier.weight(5f)) {
            if (prefs.showMascot) {
                Column(
                    Modifier
                        .drawBehind { drawLine(colors.line, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
                        .padding(start = Guga.dimens.gutter * 2, top = Guga.rem(1f), bottom = Guga.rem(1f))
                        .testTag("desktop-error-mascot"),
                ) {
                    // 只有她，不配台词：台词表里「断线」那几句说的是「架上的还能翻」（手机首页有旧版面垫着的时候用），整页错误时架上没有东西可翻
                    MascotPoseNamed(copy.pose, Guga.rem(DeskErrorSpec.POSE))
                }
            }
        }
    }
}

/**
 * 整页错误（设计稿 `desktop/index.html?p=errors`：断网 / `server` / `auth`）：一张 [DeskNotice]，外壳（报头 + 玻璃排）照常在。
 *
 * [message] = `friendlyError` 的那句话（原文不上版面）；属于哪一种由 [errorKindOf] 定。出路：重试（[onRetry]）/ 换一个服务器。
 * 读者证过期（401）在上游是「会话被收掉」，根界面会换成登录页——那张通知由登录页排（`DesktopLogin`）；这里的 `auth` 只在会话还在时兜底。
 * 设计稿里的「打开网络设置」「自动重试的倒计时」「离线也能翻」没有对应的功能，不画。
 */
@Composable
fun DesktopErrorPage(message: String, onRetry: () -> Unit) {
    val g = LocalGraph.current
    val session by g.session.session.collectAsState()
    val kind = errorKindOf(message)
    val copy = deskErrorCopy(kind, session?.serverName)
    val actions = if (kind == ErrorKind.Auth) {
        listOf(
            DeskNoticeAction("重新登录", GuguIcon.User) { g.nav.navigate(Route.Login(switch = true, server = session?.serverUrl, user = session?.userName)) },
            DeskNoticeAction("换一个账号") { g.nav.navigate(Route.Servers) },
            DeskNoticeAction("再试一次", onClick = onRetry),
        )
    } else {
        listOf(DeskNoticeAction("重试", GuguIcon.RotateCcw, onRetry), DeskNoticeAction("换一个服务器") { g.nav.navigate(Route.Servers) })
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val body = (maxHeight - Guga.rem(DesktopSpec.CONTENT_TOP) - DeskErrorSpec.BOTTOM).coerceAtLeast(0.dp)
        DesktopPage(bottom = DeskErrorSpec.BOTTOM) { DeskNotice(kind, copy, actions, Modifier.heightIn(min = body)) }
    }
}
