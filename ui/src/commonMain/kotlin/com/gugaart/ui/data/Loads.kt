package com.gugaart.ui.data

import androidx.compose.runtime.currentCompositeKeyHash
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

    /** 这份 [value] 是按哪组参数取来的（[LoadCache] 的种子也算）。返回一页时种子和当前参数一致，第一帧就能用，
     *  滚动位置才还原得了；换了参数（比如换了搜索词）旧结果不算数（1.3.1 返回保持位置）。 */
    var valueKeys: List<Any?>? = null
        internal set

    /** 还没有任何结果、也没失败。 */
    val pending: Boolean get() = value == null && error == null

    /** 重新取一次（下拉刷新、「再试」）。 */
    fun retry() {
        generation++
    }
}

/**
 * 页面数据的近期缓存（最多 [MAX] 份，最久没用的先丢）：回到一页时第一帧就是上次的内容，随后照常重新取。
 * 用户 2026-11-17：「片库返回：进详情再返回，回到原来的位置…三端都要实现」——手机 / 电脑的片库列表在数据到之前只有几行骨架，
 * 列表一短，存着的滚动位置就被压回顶部；第一帧有数据，位置才还原得了（电视的墙是数据到了才排，所以没这个问题）。
 * 键 = 账号 + 调用处在组合里的位置（同一页同一处 rememberLoad）+ 参数；换了账号不会拿到别的服务器的内容。都在主线程上读写。
 * 换服务器也用它（1.5.0 规划 §2.1「切回来秒开」）：切回看过的服务器，第一帧就是它上次的内容，再在后台刷新。
 */
internal object LoadCache {
    private const val MAX = 48
    private val map = LinkedHashMap<List<Any?>, Any?>()

    fun get(key: List<Any?>): Any? {
        val v = map.remove(key) ?: return null
        map[key] = v
        return v
    }

    fun put(key: List<Any?>, value: Any?) {
        map.remove(key)
        map[key] = value
        while (map.size > MAX) map.remove(map.keys.first())
    }

    fun clear() = map.clear()
}

/** 骨架出现之前等的时间。 */
const val SKELETON_DELAY_MS = 150L

/** 服务器 / 网络的异常 → 一句人话。原文不上版面（可能带地址）。 */
fun friendlyError(e: Throwable): String {
    // 上游 :core 已经把服务器的应答分好类了：先按它的类别走，认不出来的再看原文
    if (e is EmbyException) {
        when (e.kind) {
            EmbyException.Kind.Unauthorized -> return "登录失效"
            EmbyException.Kind.Timeout -> return "服务器没有应答"
            else -> Unit
        }
    }
    val name = e::class.simpleName.orEmpty()
    val msg = e.message.orEmpty()
    return when {
        msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true) -> "登录失效"
        name.contains("Timeout", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) -> "服务器没有应答"
        msg.contains("Network", ignoreCase = true) -> "网络断开"
        else -> "片库没连上"
    }
}

/** 整页错误是哪一种（设计稿 `errors` 的三张通知）：断网、片库没有应答、读者证过期。 */
enum class ErrorKind { Offline, Server, Auth }

/**
 * 服务器没有应答时整页错误的标题（1.5.1 审计 P2-2，三端一致）：直接写「连不上「服务器名」」；比喻放在她那一句（台词 `server`）。
 * 没有名字写「连不上服务器」。纯函数，有单测。
 */
fun unreachableTitle(server: String?): String = "连不上" + (server?.trim()?.takeIf { it.isNotEmpty() }?.let { "「$it」" } ?: "服务器")

/** [friendlyError] 的那句话属于哪一种整页错误。纯函数，有单测。 */
fun errorKindOf(message: String): ErrorKind = when {
    message.contains("登录失效") -> ErrorKind.Auth
    message.contains("网络") -> ErrorKind.Offline
    else -> ErrorKind.Server
}

/**
 * 用当前会话的 `EmbyClient`（上游 :core）取数。[keys] 变了就重取，旧结果保留到新结果到达。
 * 没有会话（没登录）→ 一直是 pending，由根界面去处理登录。
 * **换了服务器 / 账号**（1.5.0 规划 §2.1「切回来秒开」，细化 D14）：上一个账号的内容不能再显示（[accountSwitched]）——
 * 这个账号有近期缓存（[LoadCache]，按账号分键）→ 第一帧就是它上次的内容，随后照常后台刷新；
 * 没有缓存才清掉，骨架按 [SKELETON_DELAY_MS] 的规则出。显示的永远是这台服务器自己的内容，
 * 不会挂着另一台的片库（D14「切换服务器的时候得马上更新主页画报」的本意）。
 */
@Composable
fun <T> rememberLoad(vararg keys: Any?, loader: suspend (EmbyClient) -> T): Load<T> {
    val graph = LocalGraph.current
    val session by graph.session.session.collectAsState()
    val client = session?.client
    val account = session?.let { "${it.serverId}/${it.userId}" }
    val site = currentCompositeKeyHash
    val keyList = keys.toList()
    val offMain = com.gugaart.ui.idle.LocalIdleClock.current != null
    // 回到这一页：先拿上次的内容（LoadCache），随后照常重新取
    @Suppress("UNCHECKED_CAST")
    val load = remember { Load<T>().also { l -> (LoadCache.get(listOf(account, site) + keyList) as T?)?.let { l.value = it; l.account = account; l.valueKeys = keyList } } }
    LaunchedEffect(client, load.generation, *keys) {
        if (client == null) return@LaunchedEffect
        if (accountSwitched(load.account, account)) {
            // 切回来秒开（规划 §2.1）：先找这个账号的近期缓存——有，第一帧就是它上次的内容，后台刷新；
            // 没有才清掉（上一个账号的内容不能留在画面上，D14），骨架按 ≥150 ms 的规则出。
            @Suppress("UNCHECKED_CAST")
            val cached = LoadCache.get(listOf(account, site) + keyList) as T?
            load.value = cached
            load.error = null
            load.valueKeys = if (cached != null) keyList else null
        }
        load.account = account
        load.refreshing = true
        try {
            val v = if (offMain) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { loader(client) } else loader(client)
            load.value = v
            load.valueKeys = keyList
            load.error = null
            LoadCache.put(listOf(account, site) + keyList, v)
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
