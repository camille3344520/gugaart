package com.gugaart.ui.data

import com.gugugaga.core.EmbyClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile

/**
 * 池的公共部分（真实现 [OtherServerClients] 和假实现 [FakeOtherServerPool] 共用，行为一模一样）：
 * 已建的客户端 + 最近使用时间、闲 [idleMs] 就丢、401 → 要重新登录、ping 结果。
 *
 * 并发：表都是不可变 Map 整个换（`@Volatile`），读不加锁。两个协程同时现建同一台时可能各建一个、后写的留下——
 * 只是多建了一个只读客户端，没有别的后果，所以不加锁。
 */
internal class OtherPoolState(private val clock: () -> Long, private val idleMs: Long) {
    private class Entry(val client: EmbyClient, @Volatile var lastUsed: Long)

    @Volatile
    private var entries: Map<String, Entry> = emptyMap()

    @Volatile
    private var latency: Map<String, Int> = emptyMap()

    private val _health = MutableStateFlow<Map<String, OtherServerHealth>>(emptyMap())
    val health: StateFlow<Map<String, OtherServerHealth>> = _health.asStateFlow()

    /** 一共新建过几个客户端（单测看懒建 / 过期用）。 */
    @Volatile
    var built: Int = 0
        private set

    fun needsLogin(serverId: String): Boolean = _health.value[serverId] == OtherServerHealth.NeedsLogin

    /** 池里有的那个（续一次时间）；没有 = null。先顺手丢掉闲太久的。 */
    fun cached(serverId: String): EmbyClient? {
        sweep()
        val e = entries[serverId] ?: return null
        e.lastUsed = clock()
        return e.client
    }

    /** 有就给、没有就用 [build] 现建（要重新登录的那台一律 null，不建、不重试）。 */
    fun getOrBuild(serverId: String, build: () -> EmbyClient?): EmbyClient? {
        if (needsLogin(serverId)) return null
        cached(serverId)?.let { return it }
        val c = build() ?: return null
        entries = entries + (serverId to Entry(c, clock()))
        built++
        return c
    }

    /** 这台的令牌被拒了：丢掉客户端，标「要重新登录」。 */
    fun unauthorized(serverId: String) {
        entries = entries - serverId
        _health.update { it + (serverId to OtherServerHealth.NeedsLogin) }
    }

    /** 用户在那台重新登录过了（存着的令牌换了）：撤掉「要重新登录」。 */
    fun clearNeedsLogin(serverId: String) {
        if (!needsLogin(serverId)) return
        _health.update { it - serverId }
    }

    fun latencyMs(serverId: String): Int? = latency[serverId]

    fun report(serverId: String, ms: Int?) {
        if (needsLogin(serverId)) return
        if (ms != null) latency = latency + (serverId to ms)
        _health.update { it + (serverId to if (ms != null) OtherServerHealth.Online else OtherServerHealth.Offline) }
    }

    /** 丢掉闲了 [idleMs] 以上的（含正好 [idleMs]）。 */
    fun sweep(): Int {
        val now = clock()
        val dead = entries.filterValues { now - it.lastUsed >= idleMs }.keys
        if (dead.isEmpty()) return 0
        entries = entries - dead
        return dead.size
    }

    /** 池里现在有几个客户端。 */
    val size: Int get() = entries.size
}
