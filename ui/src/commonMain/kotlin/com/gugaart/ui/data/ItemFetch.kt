package com.gugaart.ui.data

import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.currentTimeMillis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 详情页对同一个条目的 `getItem` 去重（1.5.1 perf-win）。
 *
 * 进一个详情页时，入口（[com.gugaart.ui.screens.detail.ItemScreen] / [com.gugaart.ui.desktop.DesktopItemScreen]）、
 * 剧集头部（[com.gugaart.ui.screens.detail.loadSeriesHead]）、剧照（[com.gugaart.ui.screens.detail.rememberItemStills]）
 * 会各取一次同一个条目——同接口、同参数，短时间内 2–3 次（Mock 请求日志实测：电影 2 次、剧集 3 次）。这里把
 * 「同账号 + 同 id」的默认 `getItem`（全字段）合并：正在取的共享同一个 [CompletableDeferred]；取完后结果只留
 * [TTL_MS]（很短），紧接着的几次直接用，不再发请求。超过 TTL、换了账号、或上一次取失败，都重新取——不影响返回
 * 页面后的正常刷新，也不把一次失败缓存住。
 *
 * 完成时间在锁内、`complete` 之前同步记下：紧接着的调用一定看得到，不会因异步回调晚到而漏合并。
 * 只用于默认参数的 `getItem`；带自定义 fields 的调用仍直接走 `client.getItem`。取数在 [Dispatchers.Default]，不占界面线程。
 * 纯逻辑（键 / TTL / 合并 / 失败不缓存）有单测。
 */
object ItemFetch {
    /** 取完的结果留多久：只合并「同一拨」的重复请求，不做长缓存。 */
    const val TTL_MS = 3_000L

    /** 时钟（单测可换）。 */
    var now: () -> Long = { currentTimeMillis() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private class Entry(val job: CompletableDeferred<BaseItemDto>, var doneAt: Long = Long.MIN_VALUE)
    private val map = HashMap<String, Entry>()

    /**
     * 账号键：服务器 id（没有就地址）+ 地址 + 用户 id + 条目 id。换了账号不会拿到别的服务器的条目。
     * 1.6 A1：别台的详情页和本台的同时在取时，服务器 id 一样的两台（同一份 Emby 的两个地址、几台 Mock）也不串。
     */
    fun keyOf(client: EmbyClient, id: String): String =
        "${client.session?.serverId ?: client.address.baseUrl}@${client.address.baseUrl}/${client.userId.orEmpty()}|$id"

    /** 取一个条目：同键正在取 / 刚取完（TTL 内）→ 共享；否则真取一次。 */
    suspend fun get(key: String, loader: suspend () -> BaseItemDto): BaseItemDto {
        val entry = lock.withLock {
            val e = map[key]
            val reusable = e != null && (e.doneAt == Long.MIN_VALUE || now() - e.doneAt <= TTL_MS)
            if (reusable) {
                e!!
            } else {
                val ne = Entry(CompletableDeferred())
                map[key] = ne
                scope.launch {
                    try {
                        val v = loader()
                        lock.withLock { ne.doneAt = now() }
                        ne.job.complete(v)
                    } catch (e: Throwable) {
                        lock.withLock { if (map[key] === ne) map.remove(key) }
                        ne.job.completeExceptionally(e)
                    }
                }
                ne
            }
        }
        return entry.job.await()
    }

    /** 测试用：丢掉全部缓存。 */
    fun clear() {
        map.clear()
    }
}

/** 默认参数 `getItem` 的去重版（详情页各处用）：同账号同 id 短时间内只真取一次。 */
suspend fun EmbyClient.getItemShared(id: String): BaseItemDto =
    ItemFetch.get(ItemFetch.keyOf(this, id)) { getItem(id) }
