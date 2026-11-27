package com.gugaart.ui.screens.home

import com.gugaart.ui.data.Title
import com.gugugaga.app.media.imageIdentityKey
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.ImageType
import com.gugugaga.core.ImageUrlOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.random.Random

/*
 * 封面每次放映都从这部作品的图里随机挑一张（用户 2026-11-15：「每次放映的海报 都从作品的海报里面随机挑选，质量从高到底
 * 有4k选4k没有的就往下降」）。
 *
 * 封面整幅铺满的是剧照（Emby 的 Backdrop：一部作品可以有很多张；海报 Primary 只有一张）。每发新的一波封面：
 * 取这部作品的图片清单（只读 GET /Items/{id}/Images，每张带宽高），按清晰度分档（4K / 2K / 1080 / 720 / 更低），
 * 在最高的那一档里随机挑一张；同一波里回到首页的静默刷新不换（不闪）。取不到清单、只有一张 = 照旧用第一张。
 * 单集（继续观看）的剧照用整部剧的。请求的尺寸 [COVER_BACKDROP_W]：电视上整屏清晰显示，手机 / 电脑本来就虚化。
 *
 * 海报也一样（用户 2026-11-16：「有多张封面海报的时候，也可以随机的显示」）：同一份图片清单里海报（Primary）有两张以上时，
 * 封面卡上的海报也在最清晰的一档里随机挑一张（[COVER_POSTER_W] 宽）；只有一张 = 照旧。
 */

/** 封面卡上的海报要多宽（和片子卡片的海报一样）。 */
const val COVER_POSTER_W = 600

/** 封面剧照要多宽。 */
const val COVER_BACKDROP_W = 1920

/** 取一部作品的图片清单最多等多久（等不到就用第一张，不拖慢首页）。 */
private const val LIST_TIMEOUT_MS = 3_000L

/** 一张剧照：第几张（Emby 的 ImageIndex）、缓存标签、宽高（读不到 = 0）。 */
data class BackdropInfo(val index: Int, val tag: String?, val width: Int, val height: Int)

/** 清晰度档位：4 = 4K，3 = 2K，2 = 1080，1 = 720，0 = 更低或不知道。纯函数，有单测。 */
fun backdropTier(width: Int, height: Int): Int = when {
    width >= 3840 || height >= 2160 -> 4
    width >= 2560 || height >= 1440 -> 3
    width >= 1920 || height >= 1080 -> 2
    width >= 1280 || height >= 720 -> 1
    else -> 0
}

/** 在最高的那一档里随机挑一张；没有 = null。纯函数，有单测。 */
fun pickBackdrop(list: List<BackdropInfo>, random: Random): BackdropInfo? {
    if (list.isEmpty()) return null
    val best = list.maxOf { backdropTier(it.width, it.height) }
    return list.filter { backdropTier(it.width, it.height) == best }.random(random)
}

/** `/Items/{id}/Images` 的回应 → 剧照清单（别的类型不要）。纯函数，有单测。 */
fun parseBackdrops(json: JsonElement): List<BackdropInfo> = parseImages(json, "Backdrop")

/** `/Items/{id}/Images` 的回应 → 某一类图（Backdrop / Primary …）的清单。纯函数，有单测。 */
fun parseImages(json: JsonElement, type: String): List<BackdropInfo> = (json as? JsonArray).orEmpty().mapNotNull { e ->
    val o = e as? JsonObject ?: return@mapNotNull null
    fun str(k: String) = (o[k] as? JsonPrimitive)?.contentOrNull
    fun int(k: String) = (o[k] as? JsonPrimitive)?.intOrNull
    if (str("ImageType") != type) return@mapNotNull null
    BackdropInfo(index = int("ImageIndex") ?: 0, tag = str("ImageTag"), width = int("Width") ?: 0, height = int("Height") ?: 0)
}

/** 这一波封面挑中的剧照（按 [coverKey] 记；发新的一波时重挑）。 */
object CoverBackdrops {
    private val chosen = HashMap<String, String>()
    private val posters = HashMap<String, String>()

    /** [fresh] = 这是新发的一波（重挑）；否则沿用这一波挑好的。 */
    suspend fun apply(client: EmbyClient, covers: List<Title>, fresh: Boolean, random: Random = Random.Default): List<Title> {
        if (fresh) {
            val picks = coroutineScope { covers.map { t -> async { coverKey(t) to pickFor(client, t, random) } }.awaitAll() }
            chosen.clear()
            posters.clear()
            for ((key, pick) in picks) {
                pick.first?.let { chosen[key] = it }
                pick.second?.let { posters[key] = it }
            }
        }
        return covers.map { t ->
            val key = coverKey(t)
            val backdrop = chosen[key]
            val poster = posters[key]
            if (backdrop == null && poster == null) t else t.copy(art = t.art.copy(backdrop = backdrop ?: t.art.backdrop, poster = poster ?: t.art.poster))
        }
    }

    /** (剧照, 海报)：各自有两张以上才挑，否则 null = 照旧。 */
    private suspend fun pickFor(client: EmbyClient, t: Title, random: Random): Pair<String?, String?> {
        val id = t.seriesId ?: t.id
        return try {
            val json = withTimeoutOrNull(LIST_TIMEOUT_MS) { client.getJson("/Items/$id/Images") } ?: return null to null
            val backdrop = parseImages(json, "Backdrop").takeIf { it.size >= 2 }?.let { pickBackdrop(it, random) }
                ?.let { p ->
                    val built = client.imageUrl(id, ImageType.Backdrop, ImageUrlOptions(tag = p.tag, index = p.index, maxWidth = COVER_BACKDROP_W))
                    // 挑中的就是模型已有那张（去掉尺寸参数后同一条）→ 直接用模型那条地址。图片缓存的 key 是完整 URL，
                    // 封面要 1920、详情要 1600 会把同一张图各取一次（1.5.1 perf-win2 Mock 实测）。挑中别的张仍用高清的，不改清晰度。
                    val existing = t.art.backdrop
                    if (existing != null && imageIdentityKey(existing) == imageIdentityKey(built)) existing else built
                }
            val poster = parseImages(json, "Primary").takeIf { it.size >= 2 }?.let { pickBackdrop(it, random) }
                ?.let { p -> client.imageUrl(id, ImageType.Primary, ImageUrlOptions(tag = p.tag, index = p.index, maxWidth = COVER_POSTER_W)) }
            backdrop to poster
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null to null
        }
    }

    /** 测试用。 */
    fun reset() {
        chosen.clear()
        posters.clear()
    }
}
