package com.gugaart.ui.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import com.gugugaga.app.AppGraph
import com.gugugaga.app.nav.Route
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.describeEmbyError
import kotlinx.coroutines.CancellationException

/*
 * 管理 Emby 上的条目（用户 2026-11-16：「现在没办法按住 资源，然后删除了，之前可以管理emby的」）。
 * 刷新元数据 / 扫描文件 / 删除，都是上游 EmbyClient 的公开方法（refreshItem / scanItem / deleteItem），上游的长按 / 右键菜单也是这几样。
 *  - 只给管理员看：服务器明说这个账号不是管理员（Policy.IsAdministrator = false）就不出「管理」；问不到当它是（服务器自己会拒）。
 *  - 删除会把服务器上的媒体文件一起删掉、不能撤销：界面上一律先二次确认，默认停在「取消」。
 *  - 删掉以后：记进 [deleted]，各页的列表先把它藏起来；查询缓存清掉，别的页下次进来重新取；正在看它的详情页就退回去。
 * 这些都是用户自己的操作；自动化测试只对夹具做（不碰真实服务器）。
 */
object ItemManage {
    /** 删掉了的条目 id（当集合用）：列表里先藏起来。 */
    val deleted = mutableStateMapOf<String, Boolean>()

    /** 「服务器 / 账号」→ 能不能管理。 */
    private val adminBy = mutableStateMapOf<String, Boolean>()

    fun isDeleted(id: String): Boolean = deleted.containsKey(id)

    /** 这个账号能不能看到「管理」（第一次用时问一次服务器）。 */
    @Composable
    fun canManage(): Boolean {
        val g = LocalGraph.current
        val s by g.session.session.collectAsState()
        val session = s ?: return false
        val key = "${session.serverId}/${session.userId}"
        LaunchedEffect(key) {
            if (!adminBy.containsKey(key)) {
                adminBy[key] = try {
                    session.client.getCurrentUser().policy?.isAdministrator != false
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    true
                }
            }
        }
        return adminBy[key] == true
    }

    /** 让服务器重新刮削这一条；返回给用户看的一句话。 */
    suspend fun refresh(g: AppGraph, id: String): String = run(g, "已让服务器刷新元数据，过一会儿再打开就是新的") { refreshItem(id) }

    /** 让服务器重新扫描这一条的文件（新加的集、换过的文件）。 */
    suspend fun scan(g: AppGraph, id: String): String = run(g, "已让服务器扫描文件") { scanItem(id) }

    /** 从服务器删除（连媒体文件）。成功返回 null；失败返回原因。 */
    suspend fun delete(g: AppGraph, t: Title): String? {
        val client = g.session.session.value?.client ?: return "没有登录"
        return try {
            client.deleteItem(t.id)
            deleted[t.id] = true
            g.queries.clear()
            LoadCache.clear()
            if ((g.nav.route as? Route.Item)?.id == t.id) g.nav.pop()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "删除失败：" + describeEmbyError(e)
        }
    }

    private suspend fun run(g: AppGraph, ok: String, call: suspend com.gugugaga.core.EmbyClient.() -> Unit): String {
        val client = g.session.session.value?.client ?: return "没有登录"
        return try {
            client.call()
            ok
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "没成功：" + describeEmbyError(e)
        }
    }
}
