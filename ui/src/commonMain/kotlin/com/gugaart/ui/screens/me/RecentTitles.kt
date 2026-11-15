package com.gugaart.ui.screens.me

import com.gugaart.ui.data.GugaPrefs
import com.gugugaga.app.platform.LocalStore
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlin.random.Random

/*
 * 报头上展示哪个称号（用户 2026-11-15：「主页的称号重新打开软件时随机展示最近新获得的3个称号之一」）。
 *
 * 等级称号和特别称号排在同一条「最近拿到的」里（新的在前），键是 `rank:<级>` / `special:<id>`。
 * 每次打开应用（每个账号一次）从最前面的 [CHIP_PICK_FROM] 个里随机挑一个；这一次运行里又拿到新的 = 换成新拿到的那个。
 * 从播放器回来不重挑（报头不能一回来就变）。
 */

/** 从最近拿到的几个里挑。 */
const val CHIP_PICK_FROM = 3

/** 最近拿到的记几个。 */
private const val RECENT_KEEP = 10

fun rankKey(level: Int): String = "rank:$level"

fun specialKey(id: String): String = "special:$id"

/** 键 → 等级（不是等级称号 = null）。 */
fun levelOfKey(key: String): Int? = key.removePrefix("rank:").takeIf { key.startsWith("rank:") }?.toIntOrNull()

/** 键 → 特别称号 id（不是特别称号 = null）。 */
fun specialOfKey(key: String): String? = key.removePrefix("special:").takeIf { key.startsWith("special:") && Specials.of(it) != null }

/** 这一次打开应用展示哪一个：最近拿到的前 [from] 个里随机一个；还没有记录 = null（报头画当前等级）。纯函数，有单测。 */
fun pickChipTitle(recent: List<String>, random: Random, from: Int = CHIP_PICK_FROM): String? =
    recent.filter { levelOfKey(it) != null || specialOfKey(it) != null }.take(from).takeIf { it.isNotEmpty() }?.random(random)

/** 把这一次新拿到的（按拿到的先后，旧的在前）排到最前面：新的在前、不重复、最多 [keep] 个。纯函数，有单测。 */
fun pushRecent(recent: List<String>, gained: List<String>, keep: Int = RECENT_KEEP): List<String> =
    (gained.reversed() + recent).distinct().take(keep)

/**
 * 还没有记录的老用户（这个功能之前拿到的）：用现在拥有的凑一份——特别称号按表里的顺序在后、当前等级在最前。纯函数，有单测。
 */
fun seedRecent(level: Int, owned: Collection<String>): List<String> =
    pushRecent(emptyList(), Specials.ALL.map { it.id }.filter { it in owned }.map(::specialKey) + listOfNotNull(rankKey(level).takeIf { level > 0 }))

/** 每个账号最近拿到的称号，存在本机的 `gugaart.titles.recent.v1`（`{"<服务器 id>/<用户 id>": ["special:night", "rank:3", …]}`）。 */
class RecentTitleStore(private val local: LocalStore) {
    fun recent(account: String): List<String>? =
        (GugaPrefs.parseObject(local.get(KEY))?.get(account) as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }

    fun set(account: String, list: List<String>) {
        val old = GugaPrefs.parseObject(local.get(KEY))
        local.set(KEY, buildJsonObject {
            old?.forEach { (k, v) -> if (k != account) put(k, v) }
            putJsonArray(account) { list.forEach { add(JsonPrimitive(it)) } }
        }.toString())
    }

    /** 记下新拿到的（旧的在前），返回新的列表。 */
    fun push(account: String, gained: List<String>): List<String> {
        val next = pushRecent(recent(account).orEmpty(), gained)
        if (gained.isNotEmpty()) set(account, next)
        return next
    }

    companion object {
        const val KEY = "gugaart.titles.recent.v1"
    }
}
