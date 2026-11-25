package com.gugaart.ui.data

import com.gugugaga.app.AppGraph
import com.gugugaga.core.ActiveSelection
import com.gugugaga.core.ConnectOptions
import com.gugugaga.core.KtorTransport
import com.gugugaga.core.StoredServer
import com.gugugaga.core.platformHttpClient
import com.gugugaga.core.signIn
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 开发运行 / debug 包专用（1.5.0 B 的真窗口 / 模拟器验收）：把几台本地 **Mock Emby** 预先存成几台服务器，
 * 用 Mock 的夹具账号 demo / demo 真登录拿令牌（tools/mock-emby 的夹具，从不是真实凭据）。
 *
 * 几台 Mock 共用同一个夹具 ServerId（上游按 ServerId 存会合并成一台），所以照 `ServerSwitcherMockTest`（A1）的做法：
 * 分别登录，再用不同的本地 id（`mock-<端口>`）各存一条。已经存过的不再登录。
 *
 * 只有壳在开发运行（桌面 `-Dgugaart.dev=true` + `--mock-servers=`）或 debug 包（`-Pgugaart.mockServers=`，写进只有 debug 才有值的
 * `BuildConfig.MOCK_ACCOUNT` 的 `servers` 字段）时才设 [urls]；发布物里永远是空的，这里什么都不做。
 */
object DevMockServers {
    /** 要预先存进来的 Mock 地址（按顺序；第一台没有当前会话时当当前）。 */
    var urls: List<String> = emptyList()

    private val URL = Regex("""^https?://[^\s"\\]+$""")

    /** 桌面参数：`a+b+c`（程序参数本身是逗号分隔的，所以这里用加号）。只收 http(s) 地址。 */
    fun parseArg(raw: String?): List<String> = raw.orEmpty().split('+').map { it.trim() }.filter { URL.matches(it) }

    /** debug 包：`MOCK_ACCOUNT` 里的 `servers` 数组（没有 = 空）；`server` 那一台排第一。 */
    fun fromMockAccountJson(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        val o = try {
            Json.parseToJsonElement(raw) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val list = (o["servers"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.trim() }.orEmpty()
        if (list.isEmpty()) return emptyList()
        val first = (o["server"] as? JsonPrimitive)?.content?.trim()
        return (listOfNotNull(first) + list).filter { URL.matches(it) }.distinct()
    }

    /** 本地 id：`mock-<端口>`。 */
    fun localId(url: String): String = "mock-" + (url.substringAfterLast(':').takeWhile { it.isDigit() }.ifEmpty { url.hashCode().toUInt().toString() })

    /** 应用启动、恢复会话之前调一次。[urls] 空 = 什么都不做。每台失败（Mock 没起）就跳过那一台。 */
    suspend fun seed(graph: AppGraph) {
        val list = urls
        if (list.isEmpty()) return
        val session = graph.session
        val opts = ConnectOptions(identity = session.identity, transport = graph.deps.transport ?: KtorTransport(platformHttpClient()))
        val have = session.store.listServers().map { it.id }.toSet()
        for (url in list) {
            val id = localId(url)
            if (id in have) continue
            try {
                val res = signIn(url, "demo", "demo", opts, null)
                session.store.saveServer(
                    StoredServer(
                        id = id,
                        name = "Mock ${id.removePrefix("mock-")}",
                        address = res.server.address,
                        accounts = listOf(res.account),
                        activeUserId = res.account.userId,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                graph.log.add("warn", "开发用 Mock 服务器没存上：$url（${e::class.simpleName}）")
            }
        }
        if (session.store.getActive() == null) {
            val first = session.store.getServer(localId(list.first())) ?: return
            val acc = first.accounts.firstOrNull() ?: return
            session.store.setActive(ActiveSelection(first.id, acc.userId))
        }
    }
}
