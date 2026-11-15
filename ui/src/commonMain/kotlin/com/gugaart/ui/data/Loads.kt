package com.gugaart.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.EmbyException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * 一次取数的状态。「不闪」的规则（docs/design-language.md §8）落在这里：
 * - 换了参数重新取时，**上一次的结果留着**（[value] 不清空，[refreshing] = true），新结果到了才换；
 * - [showSkeleton]：第一次取数超过 150 ms 还没回来才为 true（很快回来的请求不闪一下骨架）。
 *
 * 过渡实现：上游 `QueryClient` 的读取入口还是 internal（ADR 0002 §7.3），所以这里是「每个界面保留上一次结果」的最小版本，
 * 没有跨页面共享。上游公开读取入口后换回去，界面代码不用改（只认这个类）。
 */
@Stable
class Load<T> internal constructor() {
    var value: T? by mutableStateOf(null)
        internal set
    var refreshing: Boolean by mutableStateOf(true)
        internal set

    /** 失败时给人看的一句话（不带地址、token）；成功后清掉。 */
    var error: String? by mutableStateOf(null)
        internal set
    var showSkeleton: Boolean by mutableStateOf(false)
        internal set
    internal var generation by mutableIntStateOf(0)

    /** 这份结果是哪个账号取的（服务器 id / 用户 id）。换了账号 = 上一个账号的内容不能再显示。 */
    internal var account: String? = null

    /** 还没有任何结果、也没失败。 */
    val pending: Boolean get() = value == null && error == null

    /** 重新取一次（下拉刷新、「再试」）。 */
    fun retry() {
        generation++
    }
}

/** 骨架出现之前等的时间。 */
const val SKELETON_DELAY_MS = 150L

/** 服务器 / 网络的异常 → 一句人话。原文不上版面（可能带地址）。 */
fun friendlyError(e: Throwable): String {
    // 上游 :core 已经把服务器的应答分好类了：先按它的类别走，认不出来的再看原文
    if (e is EmbyException) {
        when (e.kind) {
            EmbyException.Kind.Unauthorized -> return "登录失效了，重新登录一次"
            EmbyException.Kind.Timeout -> return "服务器没有应答"
            else -> Unit
        }
    }
    val name = e::class.simpleName.orEmpty()
    val msg = e.message.orEmpty()
    return when {
        msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true) -> "登录失效了，重新登录一次"
        name.contains("Timeout", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) -> "服务器没有应答"
        msg.contains("Network", ignoreCase = true) -> "网络断开了"
        else -> "片库没连上"
    }
}

/** 整页错误是哪一种（设计稿 `errors` 的三张通知）：断网、片库没有应答、读者证过期。 */
enum class ErrorKind { Offline, Server, Auth }

/** [friendlyError] 的那句话属于哪一种整页错误。纯函数，有单测。 */
fun errorKindOf(message: String): ErrorKind = when {
    message.contains("登录失效") -> ErrorKind.Auth
    message.contains("网络") -> ErrorKind.Offline
    else -> ErrorKind.Server
}

/**
 * 用当前会话的 `EmbyClient`（上游 :core）取数。[keys] 变了就重取，旧结果保留到新结果到达。
 * 没有会话（没登录）→ 一直是 pending，由根界面去处理登录。
 * **换了服务器 / 账号**（用户 2026-11-15：「切换服务器的时候得马上更新主页画报」）：上一个账号的内容马上清掉（不再「旧内容保持」——
 * 那是另一台服务器的片子），骨架出来，按新的服务器取（[accountSwitched]）。
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, loader: suspend (EmbyClient) -> T): Load<T> {
    val graph = LocalGraph.current
    val session by graph.session.session.collectAsState()
    val client = session?.client
    val load = remember { Load<T>() }
    val account = session?.let { "${it.serverId}/${it.userId}" }
    LaunchedEffect(client, load.generation, *keys) {
        if (client == null) return@LaunchedEffect
        if (accountSwitched(load.account, account)) {
            load.value = null
            load.error = null
        }
        load.account = account
        load.refreshing = true
        try {
            val v = loader(client)
            load.value = v
            load.error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            load.error = friendlyError(e)
        } finally {
            load.refreshing = false
            load.showSkeleton = false
        }
    }
    LaunchedEffect(client, load.generation, *keys) {
        if (load.value == null) {
            delay(SKELETON_DELAY_MS)
            if (load.value == null && load.error == null) load.showSkeleton = true
        }
    }
    return load
}

/** 换了服务器 / 账号：上一份结果是另一个账号取的。第一次取（[previous] = null）不算。纯函数，有单测。 */
fun accountSwitched(previous: String?, now: String?): Boolean = previous != null && now != null && previous != now
