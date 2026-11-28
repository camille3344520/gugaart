package com.gugaart.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.gugaart.ui.data.GugaPrefs
import com.gugugaga.app.platform.LocalStore
import com.gugugaga.app.player.PlayPhase
import com.gugugaga.app.player.PlayerSnapshot
import com.gugugaga.app.player.TrackOption
import com.gugugaga.app.player.Tracks
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.core.EngineId
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/*
 * 双语字幕（1.6 C1）：字幕面板一行「第二字幕」（关 / 选一条），用 mpv 的 `secondary-sid`，第二条排在画面上方（mpv 自己的位置）。
 *
 *  - 只有 mpv 内核（引擎给了 [com.gugugaga.app.player.PlayerEngine.secondarySubtitle]）才有这一行；EXO / VLC 不显示。
 *  - 能选的：这一份片源里内核直接能开的字幕（内嵌 + 外挂，含「仅本次播放」那条——它也是一条外挂），去掉正在当第一字幕的那条；
 *    烧录 / 要服务器转换的不列（[Tracks.engineSubtitleId] 给不出内核编号的）。
 *  - 按片记住（剧按剧记，和第一字幕的「上次的选择」一样记语言 + 名字，不记序号：每一集的轨道序号不一定一样），键 `gugaart.subtitles2.v1`。
 */

/** 第二字幕能选的（不含「关」）：中文排前面，和第一字幕同一个顺序。[engine] 不是 mpv 时为空。纯函数，有单测。 */
fun secondSubtitleChoices(snap: PlayerSnapshot, engine: EngineId?): List<TrackOption> {
    if (engine != EngineId.Mpv) return emptyList()
    val r = snap.resolved ?: return emptyList()
    return orderedSubtitles(snap).filter { t ->
        val i = t.index ?: return@filter false
        i != snap.subtitleIndex && Tracks.engineSubtitleId(r.mediaSource, r, engine, i) != null
    }
}

/** 第二字幕 [index] 交给内核的编号（`s:<n>` / `x:<i>`）；关、和第一字幕同一条、内核开不了 = null。纯函数，有单测。 */
fun secondSubtitleEngineId(snap: PlayerSnapshot, engine: EngineId?, index: Int?): String? {
    if (index == null || engine == null || index == snap.subtitleIndex) return null
    val r = snap.resolved ?: return null
    return Tracks.engineSubtitleId(r.mediaSource, r, engine, index)
}

/**
 * 开播时第二字幕该开哪条：[remembered] = 这部上次的选择（没记 / 记的是关 = 不开）；按语言 + 名字找同一条，找不到按同一类中文、
 * 再按同一语言；[primary] 那条不算。纯函数，有单测。
 */
fun pickSecondSubtitle(remembered: SubChoice?, subs: List<SubTrack>, primary: Int?): Int? {
    if (remembered == null || remembered.off) return null
    val pool = subs.filter { it.index != primary }
    val kind = chineseKindOf(SubTrack(-1, remembered.language, remembered.title))
    val same = pool.firstOrNull { it.language == remembered.language && it.title == remembered.title }
        ?: kind?.let { k -> pool.firstOrNull { chineseKindOf(it) == k } }
        ?: pool.firstOrNull { kind == null && !remembered.language.isNullOrEmpty() && it.language == remembered.language && chineseKindOf(it) == null }
    return same?.index
}

/** 每部剧 / 电影上次的第二字幕（剧按剧记）。最多 [MAX] 部。 */
class SecondSubtitleMemory(private val local: LocalStore) {
    private val map = LinkedHashMap<String, String>()

    init {
        GugaPrefs.parseObject(local.get(KEY))?.forEach { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.let { map[k] = it.content } }
    }

    fun get(key: String): SubChoice? = SubChoice.decode(map[key])

    fun put(key: String, choice: SubChoice) {
        map.remove(key)
        map[key] = choice.encode()
        while (map.size > MAX) map.remove(map.keys.first())
        local.set(KEY, buildJsonObject { map.forEach { (k, v) -> put(k, v) } }.toString())
    }

    companion object {
        const val KEY = "gugaart.subtitles2.v1"
        const val MAX = 300
    }
}

/** 这条片源的字幕轨（选择用：编号 + 语言 + 名字）。 */
internal fun subTracksOf(snap: PlayerSnapshot): List<SubTrack> =
    snap.resolved?.mediaSource?.mediaStreams.orEmpty().filter { it.type == "Subtitle" }
        .map { SubTrack(it.index, it.language, it.title ?: it.displayTitle, it.isExternal == true, it.isDefault == true) }

/** 面板 / 电视页边里「第二字幕」一行的数据：[labels] 第一项是「关」；[at] = 现在选中的那一项。没有这一行 = null。 */
class SecondSubtitleRow(val labels: List<String>, val at: Int, val pick: (Int) -> Unit)

@Composable
fun rememberSecondSubtitleRow(play: PlaySession, snap: PlayerSnapshot): SecondSubtitleRow? {
    val engine by play.controller.engineFlow.collectAsState()
    if (engine?.secondarySubtitle == null) return null
    val choices = secondSubtitleChoices(snap, engine?.id)
    if (choices.isEmpty()) return null
    val current = play.secondSubtitleIndex
    return SecondSubtitleRow(
        listOf("关") + choices.map { trackLabel(snap, it, "Subtitle").text },
        choices.indexOfFirst { it.index == current }.let { if (it < 0) 0 else it + 1 },
    ) { i -> play.chooseSecondSubtitle(if (i <= 0) null else choices.getOrNull(i - 1)?.index) }
}

/**
 * 把第二字幕接到引擎上（三端的播放器各调一行，和 [RememberShaderApply] 一样）：
 * 开播 / 换片源时按「上次的选择」放回去；选的那条、第一字幕或引擎一变就重新下发（第一字幕换成了同一条 = 关掉第二字幕）。
 */
@Composable
fun RememberSecondSubtitleApply(play: PlaySession) {
    val g = LocalGraph.current
    val engine by play.controller.engineFlow.collectAsState()
    val snap by play.controller.snapshot.collectAsState()
    val memory = remember(g) { SecondSubtitleMemory(g.deps.local) }
    val control = engine?.secondarySubtitle
    // 记下来：用户在面板里选的（剧按剧记）
    play.onSecondSubtitleChosen = { index ->
        val s = play.controller.value
        memory.put(s.item?.seriesId ?: s.itemId, SubChoice.of(subTracksOf(s).firstOrNull { it.index == index }))
    }
    // 开播 / 换了片源：放回上次的选择（用户在这一份片源上自己选过就不动）
    val sourceKey = snap.itemId + "|" + snap.resolved?.mediaSource?.id
    val ready = snap.phase == PlayPhase.Ready && snap.resolved != null && snap.item != null
    LaunchedEffect(sourceKey, ready, control != null) {
        if (!ready || control == null) return@LaunchedEffect
        if (play.secondChosenFor == sourceKey) return@LaunchedEffect
        val s = play.controller.value
        play.secondSubtitleIndex = pickSecondSubtitle(memory.get(s.item?.seriesId ?: s.itemId), subTracksOf(s), s.subtitleIndex)
            ?.takeIf { secondSubtitleEngineId(s, engine?.id, it) != null }
    }
    // 下发：第一字幕换成了第二字幕那条 → 第二字幕关
    val id = secondSubtitleEngineId(snap, engine?.id, play.secondSubtitleIndex)
    LaunchedEffect(control, id, snap.resolved?.mediaSource?.id) {
        control?.select(id)
    }
}
