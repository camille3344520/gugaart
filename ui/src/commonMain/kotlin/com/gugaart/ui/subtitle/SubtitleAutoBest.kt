package com.gugaart.ui.subtitle

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gugaart.ui.player.SubTrack
import com.gugaart.ui.player.chineseKindOf
import com.gugaart.ui.player.isChineseLanguage
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.MediaStream
import kotlinx.coroutines.CancellationException

/*
 * 字幕智能匹配的「自动加载」（1.4.1 P2，docs/subtitle-search.md §2.4）。设置键 `subtitleAutoBest`（画报自己的键，**默认关**）。
 *
 * 规矩：开播时这一集**没有中文字幕**、而且「最合适」那条是**指纹精确**、**来自迅雷 / 射手**（仅本次播放），才自动加载；
 * 只是分数过门槛但不是指纹精确 → 只告诉界面「找到对得上的字幕」，不自己动手。
 * **自动加载不写服务器**（协调者 2026-11-25 定）：最合适那条来自服务器字幕源（选了会让 Emby 下载存进去）→ 只提示，
 * 存进 Emby 必须用户自己点「用上」。迅雷 / 射手 = 下载到本机、仅本次播放。
 * 在线字幕源（`subtitleOnline`）关着 → 后台那一次只问服务器（[SubtitleSearchSession.run] 不碰迅雷 / 射手、不取指纹）。
 *
 * 这里只有逻辑和可观察状态，**不画界面**：界面（Claude）读 [SubtitleAutoBest.state] 决定怎么显示，
 * 开播时调一次 [SubtitleAutoBest.consider]，把「选上第 n 条字幕」的 [select] 传进来（播放器里 = `SubtitleSearchBoard.select`）。
 */

/** 自动加载这一步该做什么。纯函数 [autoBestAction] 的结果，有单测。 */
enum class AutoBestAction {
    /** 设置里没开。 */
    Off,

    /** 这一集已经有中文字幕了，不插手。 */
    HasChinese,

    /** 搜完了，没有过门槛的。 */
    NoBest,

    /** 有对得上的，但不是指纹精确：只提示，不自动加载。 */
    Offer,

    /** 指纹精确 + 没有中文字幕 + 仅本次（迅雷 / 射手）：自动加载。 */
    Load,
}

/**
 * 该不该自动加载（纯函数，有单测）。[best] = [SmartMatch.bestOf] 的结果（null = 没过门槛）。
 * [offerWhenOff]（1.4.1 U2，主理人定「默认关；最合适那条是指纹精确时提示一句，一点即用」）：开关关着也搜，
 * 但只有**指纹精确**的才 [AutoBestAction.Offer]（只提示、不动手）；不是指纹精确的不打扰 = [AutoBestAction.NoBest]。
 * 最合适那条来自服务器字幕源（[SubtitleHit.toEmby]，选了会写服务器）：开着也只 [AutoBestAction.Offer]，永远不 [AutoBestAction.Load]。
 */
fun autoBestAction(enabled: Boolean, hasChineseSubtitle: Boolean, best: ScoredHit?, offerWhenOff: Boolean = false): AutoBestAction = when {
    !enabled && !offerWhenOff -> AutoBestAction.Off
    hasChineseSubtitle -> AutoBestAction.HasChinese
    best == null -> AutoBestAction.NoBest
    best.score.isExactHash -> if (enabled && !best.hit.toEmby) AutoBestAction.Load else AutoBestAction.Offer
    enabled -> AutoBestAction.Offer
    else -> AutoBestAction.NoBest
}

/**
 * 这一集有没有中文字幕（内嵌 / 外挂都算；[locals] = 已经搜来加载的本机字幕）。纯函数，有单测。
 * 判断用的是字幕自动选择那一套（`ui/player/SubtitlePick.kt` 的 [chineseKindOf]），不另起一套。
 */
fun hasChineseSubtitle(streams: List<MediaStream>, locals: List<LocalSubtitle> = emptyList()): Boolean =
    streams.any {
        it.type == "Subtitle" && chineseKindOf(SubTrack(it.index, it.language, it.title ?: it.displayTitle, it.isExternal == true)) != null
    } || locals.any {
        isChineseLanguage(it.lang) || chineseKindOf(SubTrack(it.index, it.lang, it.label, external = true)) != null
    }

/** 自动加载现在怎么样（界面读；怎么显示由界面定）。 */
sealed interface AutoBestState {
    data object Idle : AutoBestState

    /** 正在搜（只在设置开着、这一集没中文字幕时才会有）。 */
    data object Searching : AutoBestState

    /** 找到对得上的了，但不是指纹精确 → 不自动加载，界面可以问一句。[note] 是给用户看的一句话。 */
    data class Found(val hit: SubtitleHit, val score: SmartScore, val note: String) : AutoBestState

    /**
     * 已自动加载。[index] = 播放器里的字幕编号（进 Emby 的 = 服务器新存的那条；仅本次的 = 9000+；
     * null = 服务器收下了但还没刷新出来）。[toEmby] = 是不是存进了 Emby（自动加载不写服务器，现在恒为 false；字段留着给界面判断）。
     */
    data class Loaded(val hit: SubtitleHit, val score: SmartScore, val index: Int?, val toEmby: Boolean, val note: String) : AutoBestState

    /** 没做（[why] 给用户看：「这一集已经有中文字幕」「没有对得上的字幕」…）。 */
    data class Skipped(val why: String) : AutoBestState

    data class Failed(val hit: SubtitleHit?, val why: String) : AutoBestState
}

/**
 * 自动加载的状态 + 那一次搜索。界面只读 [state] / [session]；[consider] 由播放器在开播后调一次。
 * 换集 / 退出播放器时调 [reset]。
 */
@Stable
object SubtitleAutoBest {
    var state: AutoBestState by mutableStateOf<AutoBestState>(AutoBestState.Idle)
        private set

    /** 现在这一状态是给哪一集的（null = 没有）。 */
    var itemId: String? by mutableStateOf(null)
        private set

    /** 自动加载那一次的搜索（界面要看三路状态 / 全部结果时用；没搜过 = null）。 */
    var session: SubtitleSearchSession? by mutableStateOf(null)
        private set

    /** 第几轮（换集 / [reset] 加一）：上一集还没搜完的那一轮回来时不再改状态。 */
    private var round = 0

    fun reset() {
        round++
        state = AutoBestState.Idle
        itemId = null
        session = null
    }

    /** 测试缝：直接摆一个状态（离屏截图里的两种提示）。应用里不用。 */
    internal fun preview(item: String, s: AutoBestState, search: SubtitleSearchSession? = null) {
        round++
        itemId = item
        session = search
        state = s
    }

    /**
     * 开播时调一次：开着 + 这一集没中文字幕 → 搜一次 → 最合适那条是指纹精确、来自迅雷 / 射手就加载它（服务器源只提示）。
     * [online] 关着 = 只问服务器（不发迅雷 / 射手请求、不取指纹）。
     *
     * [target] = 这一集（`SubtitleTarget.of(item, source, client, strmTarget, remoteHeaders)`）；
     * [online] = 设置里的「在线字幕源」；[enabled] = 设置里的 `subtitleAutoBest`；[langs] = 设置里的 `subtitleLangs`；
     * [hasChinese] = 这一集已有的中文字幕（[hasChineseSubtitle] 算）；
     * [select] = 播放器里「选上第 n 条字幕」（播放器外传 null：只下载 / 存进 Emby，不选）。
     * 返回最后的状态（和 [state] 一样）。不抛异常（取消除外）。
     */
    suspend fun consider(
        target: SubtitleTarget,
        client: EmbyClient?,
        net: SubtitleNet,
        online: Boolean,
        enabled: Boolean,
        langs: List<String> = SmartMatch.DEFAULT_LANGS,
        hasChinese: Boolean = false,
        select: (suspend (Int) -> Unit)? = null,
        /** 开关关着也搜一次，指纹精确的只提示（[autoBestAction] 的 offerWhenOff）。 */
        offerWhenOff: Boolean = false,
        /** 剧的别名（[SubtitleAliases]）；null = 不问。 */
        aliases: (suspend () -> List<String>)? = null,
    ): AutoBestState {
        val mine = ++round
        itemId = target.itemId
        session = null
        if (!enabled && !offerWhenOff) return publish(AutoBestState.Skipped("自动加载已关"), mine)
        if (hasChinese) return publish(AutoBestState.Skipped("已有中文字幕"), mine)
        publish(AutoBestState.Searching, mine)
        val s = SubtitleSearchSession(target, client, net, online, langs, aliases)
        session = s
        try {
            s.run()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return publish(AutoBestState.Failed(null, "搜索失败：" + (e.message ?: e::class.simpleName.orEmpty())), mine)
        }
        // 下载下来是弹幕（§4.5）：它已经从单子里去掉了，换下一条「最合适」重新判（最多 [DANMAKU_RETRIES] 次）
        repeat(DANMAKU_RETRIES + 1) {
            val best = s.best ?: return publish(AutoBestState.Skipped("没有匹配的字幕"), mine)
            when (autoBestAction(enabled, hasChinese, best, offerWhenOff)) {
                AutoBestAction.Offer -> return publish(AutoBestState.Found(best.hit, best.score, foundNote(best)), mine)
                AutoBestAction.Load -> {
                    if (mine != round) return state
                    try {
                        return load(s, best, select, mine)
                    } catch (_: DanmakuFileException) {
                        // 下一轮
                    }
                }
                else -> return publish(AutoBestState.Skipped("没有精确匹配的字幕"), mine)
            }
        }
        return publish(AutoBestState.Skipped("没有匹配的字幕"), mine)
    }

    /** 自动加载时「最合适」那条下载下来是弹幕，最多再换几条。 */
    private const val DANMAKU_RETRIES = 2

    /** 加载 [best]；下载下来是弹幕 → 抛 [DanmakuFileException]（[consider] 换下一条）。 */
    private suspend fun load(s: SubtitleSearchSession, best: ScoredHit, select: (suspend (Int) -> Unit)?, mine: Int): AutoBestState {
        val hit = best.hit
        // 自动加载不写服务器：服务器源的只提示（autoBestAction 已经挡了；这里再挡一道，免得以后改了门槛漏过去）
        if (hit.toEmby) return publish(AutoBestState.Found(hit, best.score, foundNote(best)), mine)
        return try {
            val (text, format) = s.fetchText(hit)
            val sub = LocalSubtitles.add(s.target.itemId, text, format, autoLocalLabel(hit), if (hit.lang == SubLang.English) "eng" else "chi")
            if (LocalSubtitles.urlOf(sub) == null) {
                return publish(AutoBestState.Failed(hit, "加载失败"), mine)
            }
            if (mine == round) select?.invoke(sub.index)
            publish(AutoBestState.Loaded(hit, best.score, sub.index, false, "已自动加载：${hit.name} · 仅本次"), mine)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DanmakuFileException) {
            throw e
        } catch (e: SubtitleFetchException) {
            publish(AutoBestState.Failed(hit, e.message ?: "下载失败"), mine)
        } catch (e: Exception) {
            publish(AutoBestState.Failed(hit, "出错了：" + (e.message ?: e::class.simpleName.orEmpty())), mine)
        }
    }

    /** 只有还是这一轮（没换集、没 [reset]）才改状态。 */
    private fun publish(s: AutoBestState, mine: Int): AutoBestState {
        if (mine == round) state = s
        return s
    }

    /** 「找到…」那句话（1.5.1 m151：去向写在动作按钮上，不再另写「用上会存进 Emby」）。 */
    private fun foundNote(best: ScoredHit): String =
        (if (best.score.isExactHash) "找到精确匹配的字幕" else "找到匹配的字幕") + "：" + best.hit.name

    /** 自动加载的本机字幕在字幕面板里叫什么（和手动搜来的同一种写法）。 */
    fun autoLocalLabel(hit: SubtitleHit): String = "搜到的字幕 · ${hit.lang.short} · ${hit.source.label} · 仅本次"
}
