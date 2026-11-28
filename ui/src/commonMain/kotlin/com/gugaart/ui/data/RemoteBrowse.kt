package com.gugaart.ui.data

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.OutlineTag
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.NavController
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EmbyClient
import com.gugugaga.ds.theme.UiMode
import kotlinx.coroutines.CancellationException

/*
 * 打开别台服务器上的条目（1.6 A1「多台一起搜」）：搜索结果「其它服务器」那一组点开 = 进**那台**的详情，不切换当前服务器。
 *
 * - 路由模型是上游的（`Route.Item(id)` / `Route.Play(id)`），不能加字段：别台的条目写成一个带服务器的 id（[RemoteRef]），
 *   详情页三端的入口认出来，换成那台的客户端（[LocalBrowseServer]）再按原来的版式排。
 * - 这一页里的取数（`rememberLoad`）、收藏 / 已看 / 条目菜单、再点进去的条目（剧 → 单集、演员）都跟着那台（[RemoteBrowse.wrap]）。
 * - 播放照 1.5.0「别台的源」：源都是 `OtherServer`，`SourcesState.playRoute` 交一张 `RemoteHandoff`，路由里是这个带服务器的 id，
 *   播放器开控制器时用那台的客户端，进度只报给那台（`player/RemotePlay.kt`）。
 * - 客户端用 [OtherServers] 池里那一台的（令牌只在内存，`data/OtherServers.kt`）；那台要重新登录 / 连不上 = 整页「连不上「名字」」。
 */

/** 别台的一个条目：[serverId] = 上游已保存服务器的本地 id（同 `PlaySource.serverId`），[itemId] = 那台上的条目 id。 */
@Immutable
data class RemoteRef(val serverId: String, val itemId: String) {
    /** 写进路由的 id。 */
    val routeId: String get() = encode(serverId, itemId)

    companion object {
        /** 前缀：Emby 的条目 id 是十六进制 / 数字，不会以它开头，也不含 `~`。 */
        const val PREFIX = "srv~"

        fun encode(serverId: String, itemId: String): String = "$PREFIX$serverId~$itemId"

        /** 路由里的 id → 别台的条目；本台的（普通 id）= null。纯函数，有单测。 */
        fun parse(id: String?): RemoteRef? {
            if (id == null || !id.startsWith(PREFIX)) return null
            val rest = id.substring(PREFIX.length)
            val cut = rest.lastIndexOf('~')
            if (cut <= 0 || cut == rest.length - 1) return null
            return RemoteRef(rest.substring(0, cut), rest.substring(cut + 1))
        }

        /** 去掉服务器那一截：别台的 → 那台上的条目 id；本台的原样。 */
        fun plain(id: String): String = parse(id)?.itemId ?: id
    }
}

/** 这一页在看哪一台（null = 当前服务器）。[client] = null：还没连上（[failed] = 连不上 / 要重新登录）。 */
@Immutable
data class BrowseServer(val serverId: String, val name: String, val client: EmbyClient?, val failed: Boolean = false)

/** 详情页三端的入口（[RemoteBrowseHost]）和播放器给的；`rememberLoad` 和「源」都认它。 */
val LocalBrowseServer = staticCompositionLocalOf<BrowseServer?> { null }

object RemoteBrowse {
    /** 服务器名（搜索时记下的；池里读到的也记）。界面只拿来写小签，读不到写「别的服务器」。 */
    private val names = HashMap<String, String>()

    fun rememberName(serverId: String, name: String) {
        if (name.isNotBlank()) names[serverId] = name
    }

    fun nameOf(serverId: String): String = names[serverId] ?: "别的服务器"

    /** 现在最上面那一页（详情 / 播放）在看的是哪一台的条目（null = 本台）。 */
    fun activeOf(nav: NavController): RemoteRef? = when (val r = nav.routes().lastOrNull()) {
        is Route.Item -> RemoteRef.parse(r.id)
        is Route.Play -> RemoteRef.parse(r.id)
        else -> null
    }

    /**
     * 从这一页再打开 / 播放的条目 id：这一页在看别台 → 同一台的（带服务器）；本台 → 原样。已经带服务器的不再包。
     * 纯函数（只读返回栈），有单测。
     */
    fun wrap(nav: NavController, id: String): String {
        if (RemoteRef.parse(id) != null) return id
        val active = activeOf(nav) ?: return id
        return RemoteRef.encode(active.serverId, id)
    }

    /** 这一页的写操作（收藏 / 已看 / 条目菜单）该发给哪台：最上面一页在看别台 → 那台（池里的）；否则当前会话。连不上 = null。 */
    fun contentClient(g: AppGraph): EmbyClient? {
        val active = activeOf(g.nav) ?: return g.session.session.value?.client
        return OtherServers.poolFor(g).clientNow(active.serverId)
    }

    /** 那台的客户端（池里有就直接给，没有先读一次已保存的服务器）。要重新登录 / 不认识 = null。 */
    suspend fun resolve(pool: OtherServerPool, serverId: String): EmbyClient? = try {
        if (serverId !in names) pool.participants().forEach { rememberName(it.serverId, it.name) }
        pool.clientNow(serverId) ?: pool.client(serverId)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

/**
 * 详情页三端入口的外壳：[ref] 是别台的条目 → 换成那台的客户端排 [content]（content 拿到的是那台上的普通 id），顶上加一张写服务器名的小签。
 */
@Composable
fun RemoteBrowseHost(ref: RemoteRef, content: @Composable () -> Unit) {
    val g = LocalGraph.current
    val pool = OtherServers.poolFor(g)
    val server by produceState(BrowseServer(ref.serverId, RemoteBrowse.nameOf(ref.serverId), pool.clientNow(ref.serverId)), ref.serverId) {
        if (value.client == null) {
            val c = RemoteBrowse.resolve(pool, ref.serverId)
            value = BrowseServer(ref.serverId, RemoteBrowse.nameOf(ref.serverId), c, failed = c == null)
        }
    }
    CompositionLocalProvider(LocalBrowseServer provides server) {
        Box(Modifier.fillMaxSize()) {
            content()
            RemoteServerTag(server.name, Modifier.align(if (Guga.uiMode == UiMode.Tv) Alignment.TopCenter else Alignment.TopStart))
        }
    }
}

/**
 * 别台详情页顶上的小签：服务器名（米白细框 + 米白字，垫一层墨色压在图上读得清；纸面语言，不是玻璃）。
 * 位置各端不同：手机在返回钮右边，电脑在顶栏（导航胶囊）下面靠左，电视在页眉那一行正中。
 */
@Composable
fun RemoteServerTag(name: String, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    val (size, line, pad) = when (Guga.uiMode) {
        UiMode.Tv -> Triple(22.sp, 2.dp, Modifier.padding(top = 34.dp))
        UiMode.Phone -> Triple(12.sp, 1.dp, Modifier.padding(start = Guga.dimens.margin + 44.dp, top = Guga.dimens.statusBar + 15.dp))
        else -> Triple(13.sp, 1.dp, Modifier.padding(start = Guga.dimens.margin, top = 116.dp))
    }
    Box(modifier.then(pad).background(colors.inkWell).testTag("remote-server-tag")) {
        OutlineTag(name, size, line, colors.ink)
    }
}

/**
 * 别台详情页 / 播放器的「源」：那一台自己的版本（[inner] = 那台的 [EmbySourceRepository]），一律当「别台的源」——
 * 开播、换版本都走 1.5.0 的交接单 / `switchServer`，进度只报给那一台。
 */
class RemoteOwnSources(private val inner: SourceRepository) : SourceRepository {
    override suspend fun sourcesFor(item: com.gugugaga.core.BaseItemDto): List<PlaySource> =
        inner.sourcesFor(item).map { it.copy(kind = SourceKind.OtherServer) }
}
