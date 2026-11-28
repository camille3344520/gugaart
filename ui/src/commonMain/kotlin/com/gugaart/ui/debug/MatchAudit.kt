package com.gugaart.ui.debug

import com.gugaart.ui.player.DanmakuSeasonFacts
import com.gugaart.ui.player.danmakuSeasonQuery
import com.gugaart.ui.player.genericSeasonName
import com.gugaart.ui.player.pickSeasonEntry
import com.gugaart.ui.player.seasonQueries
import com.gugaart.ui.player.wrongSeason
import com.gugaart.ui.subtitle.KtorSubtitleNet
import com.gugaart.ui.subtitle.SourceState
import com.gugaart.ui.subtitle.SubtitleAliases
import com.gugaart.ui.subtitle.SubtitleSearchBoard
import com.gugaart.ui.subtitle.SubtitleSearchSession
import com.gugaart.ui.subtitle.SubtitleTarget
import com.gugugaga.app.AppGraph
import com.gugugaga.app.danmaku.DanmakuService
import com.gugugaga.app.danmakuUserAgent
import com.gugugaga.core.BaseItemDto
import com.gugugaga.core.KtorTransport
import com.gugugaga.core.MemoryKeyValueStore
import com.gugugaga.core.platformTmdbHttpClient
import com.gugugaga.danmaku.match.MatchRunOptions
import com.gugugaga.danmaku.match.MatchStatus
import com.gugugaga.danmaku.match.toMatchInput
import com.gugugaga.danmaku.settings.parseDanmakuPrefsFromBlob
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/*
 * 调试构建（-Pgugaart.audit=true）才有的「逐集核对」：用这台设备上已经登录的会话，把几部剧的每一集按应用真实的流程跑一遍
 * 字幕搜索（服务器 / 迅雷 / 射手 + 智能匹配）和弹幕匹配（上游匹配器 + 画报的季守卫），一集一段写进日志，人来判对错。
 *
 * 只读：对 Emby 只有 GET（条目、季、集、远程字幕搜索、读片头指纹）；不收藏、不标记、不存字幕、不上报进度。
 * 弹幕匹配用一个**另起的、只在内存里的**匹配器（同一个弹幕服务器、同一个 TMDB），不读也不写这台设备上已有的匹配记录和绑定。
 * 日志里不写服务器地址、令牌、弹幕服务器地址。
 */
object MatchAudit {
    private var real: com.gugugaga.danmaku.match.MatcherPort? = null

    suspend fun run(g: AppGraph, series: List<String>, out: (String) -> Unit) {
        val client = g.session.session.value?.client ?: return out("AUDIT no session")
        val uid = client.userId ?: return out("AUDIT no user")
        val online = SubtitleSearchBoard.online(g)
        val net = KtorSubtitleNet()
        val prefs = parseDanmakuPrefsFromBlob(g.settings.value)
        val service = DanmakuService(
            g.secrets,
            MemoryKeyValueStore(),
            tmdbTransport = lazy { KtorTransport(platformTmdbHttpClient()) },
            tmdbBaseUrl = g.deps.debug.defaultSecrets?.tmdbBaseUrl,
            userAgent = danmakuUserAgent(g.deps),
        )
        val matcher = runCatching { service.matcher(prefs.sourcePriority) }.getOrNull()
        // 这台设备上真在用的匹配器（带主理人以前挑过的绑定）：照常匹配一次，看实际会给哪一部（和播放时一样，只读弹幕服务器）
        real = runCatching { g.danmaku.matcher(prefs.sourcePriority) }.getOrNull()
        out("AUDIT start online=$online danmaku=${matcher != null} sources=${prefs.sourcePriority.joinToString(",")}")
        for (name in series) {
            // 「id:44970」= 直接按条目 id（adb 传中文容易乱码）
            if (name.startsWith("id:")) {
                val id = name.removePrefix("id:")
                val it = runCatching { client.getItem(id) }.getOrNull()
                if (it == null) {
                    out("SERIES $name not found")
                    continue
                }
                seriesAudit(g, client, id, it.name, it.productionYear?.toString(), it.originalTitle, online, net, matcher, prefs.sourcePriority, out)
                continue
            }
            val found = runCatching {
                client.getJson(
                    "/Users/$uid/Items",
                    mapOf("SearchTerm" to name, "IncludeItemTypes" to "Series", "Recursive" to "true", "Fields" to "ProductionYear,OriginalTitle"),
                )
            }.getOrNull()
            val items = ((found as? JsonObject)?.get("Items") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val hit = items.firstOrNull { (it["Name"] as? JsonPrimitive)?.contentOrNull == name } ?: items.firstOrNull()
            val seriesId = (hit?.get("Id") as? JsonPrimitive)?.contentOrNull
            if (seriesId == null) {
                out("SERIES $name not found")
                continue
            }
            seriesAudit(
                g, client, seriesId, name, (hit["ProductionYear"] as? JsonPrimitive)?.contentOrNull, (hit["OriginalTitle"] as? JsonPrimitive)?.contentOrNull,
                online, net, matcher, prefs.sourcePriority, out,
            )
        }
        out("AUDIT done")
    }

    private suspend fun seriesAudit(
        g: AppGraph,
        client: com.gugugaga.core.EmbyClient,
        seriesId: String,
        name: String,
        year: String?,
        original: String?,
        online: Boolean,
        net: KtorSubtitleNet,
        matcher: com.gugugaga.danmaku.match.MatcherPort?,
        sourcePriority: List<String>,
        out: (String) -> Unit,
    ) {
        out("SERIES $name id=$seriesId year=$year original=$original")
        val seriesItem = runCatching { client.getItem(seriesId) }.getOrNull()
        out("GENRES ${seriesItem?.genres.orEmpty().joinToString("|")}")
        val seasons = runCatching { client.getSeasons(seriesId).items }.getOrDefault(emptyList())
        for (s in seasons) out("SEASON ${s.indexNumber} name=${s.name} premiere=${s.premiereDate?.take(10)} children=${s.childCount}")
        val eps = runCatching { client.getEpisodes(seriesId).items }.getOrDefault(emptyList())
        out("EPISODES ${eps.size}")
        out("ALIASES ${SubtitleAliases.of(g, name).joinToString("|")}")
        for ((sn, list) in eps.groupBy { it.parentIndexNumber }) {
            out("SEASONEPS $sn count=${list.size} firstAir=${list.mapNotNull { it.premiereDate?.take(10) }.minOrNull()}")
        }
        // 弹幕服务器上这部剧的全部条目（原样），离线回放测试用
        if (matcher != null) {
            for (q in listOfNotNull(name, original).distinct()) {
                val hits = runCatching { matcher.searchAnime(q) }.getOrDefault(emptyList())
                out("ANIMEQ \"$q\" ${hits.size}")
                for (a in hits) out("ANIME ${a.animeId}\t${a.title}\t${a.type}\t${a.typeDescription}\t${a.startDate.take(10)}\t${a.episodeCount}\t${a.source}\t${a.aliases.joinToString("|")}")
            }
        }
        for (ep in eps) {
            try {
                episode(g, client, ep, online, net, matcher, sourcePriority, out)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                out("EP ${tag(ep)} error ${e::class.simpleName}: ${e.message?.take(160)}")
            }
        }
    }

    private fun tag(ep: BaseItemDto): String = "S${ep.parentIndexNumber ?: "?"}E${ep.indexNumber ?: "?"}"

    private suspend fun episode(
        g: AppGraph,
        client: com.gugugaga.core.EmbyClient,
        ep: BaseItemDto,
        online: Boolean,
        net: KtorSubtitleNet,
        matcher: com.gugugaga.danmaku.match.MatcherPort?,
        sourcePriority: List<String>,
        out: (String) -> Unit,
    ) {
        val t = tag(ep)
        val item = client.getItem(ep.id)
        val source = item.mediaSources.firstOrNull()
        out("EP $t name=${item.name} runtime=${(item.runTimeTicks ?: 0) / 10_000_000}s premiere=${item.premiereDate?.take(10)} file=${source?.path?.substringAfterLast('/')?.substringAfterLast('\\')?.take(120)} remote=${source?.isRemote}")

        // ---- 字幕：和播放器外「搜索字幕…」同一条路（SubtitleSearchBoard.openForItem）
        val target = SubtitleTarget.of(item, source, client)
        val s = SubtitleSearchSession(target, client, net, online, SubtitleSearchBoard.langs(g), SubtitleSearchBoard.aliasesFor(g, item))
        s.run()
        fun st(x: SourceState): String = when (x) {
            is SourceState.Done -> "${x.count}"
            is SourceState.Off -> "off(${x.why})"
            is SourceState.Failed -> "fail(${x.why})"
            else -> x::class.simpleName ?: "?"
        }
        out("SUB $t query=\"${target.nameQuery}\" fileName=\"${target.fileName.take(100)}\" server=${st(s.server)} thunder=${st(s.thunder)} shooter=${st(s.shooter)}")
        out("TARGET $t\t${target.fileName}\t${target.name}\t${target.season}\t${target.episode}\t${target.year}\t${target.runtimeMs}\t${target.isStrm}\t${target.nameQuery}\t${target.altQuery}")
        for (h in s.smartHits) out("HIT $t\t${h.hit.source.name}\t${h.hit.lang.name}\t${h.hit.format}\t${h.hit.match.name}\t${h.hit.durationMs}\t${h.hit.downloadCount}\t${h.score.points}\t${h.hit.name}")
        val best = s.best
        out("SUB $t best=${best?.let { "\"${it.hit.name.take(90)}\" ${it.hit.source.label} ${it.score.points} [${it.score.summary().take(80)}]" } ?: "none"}")
        for ((i, h) in s.smartHits.take(6).withIndex()) {
            out("SUB $t #${i + 1} ${h.score.points}${if (h.score.passes) "" else "x"} ${h.hit.source.label} \"${h.hit.name.take(90)}\"")
        }

        // ---- 弹幕：上游匹配器（不用已存的绑定）+ 画报的季守卫
        if (matcher == null) {
            out("DM $t no danmaku server")
            return
        }
        val raw = client.getDanmakuMatchInputWithSeasons(ep.id)
        val facts = runCatching { DanmakuSeasonFacts.of(client, raw) }.getOrNull()
        val r = matcher.match(raw.toMatchInput(), MatchRunOptions(ignoreBinding = true))
        val b = r.best
        out("DM $t status=${r.status.id} best=${b?.let { "\"${it.title}\" ${it.source} y=${it.year} eps=${it.episodeCount} score=${it.score.toInt()}" } ?: "none"} ep=${r.episode?.let { "#${it.number} \"${it.title.take(70)}\" ${it.mapping.id}" } ?: "none"} lead=${r.lead?.toInt()}")
        for (c in r.candidates.take(4)) out("DM $t cand \"${c.title}\" ${c.source} y=${c.year} eps=${c.episodeCount} score=${c.score.toInt()}")
        real?.let { rm ->
            val rr = runCatching { rm.match(raw.toMatchInput()) }.getOrNull()
            out("REAL $t status=${rr?.status?.id} binding=${rr?.fromBinding} best=\"${rr?.best?.title}\" ep=${rr?.episode?.let { "#${it.number} ${it.mapping.id}" }}")
        }
        for (line in r.explain.take(14)) out("DM $t why $line")
        // 季守卫（DanmakuSeasonGuard.check 的同一套判断，不改应用里的状态）
        val season = raw.seasonNumber
        val multi = season != null && raw.kind == "episode" && !raw.isSpecial && !(season < 2 && (facts?.lastSeason ?: 1) < 2)
        if (r.status == MatchStatus.Matched && b != null && multi && season != null) {
            val wrong = wrongSeason(b.title, raw.title, season, facts?.year, facts?.lastSeason)
            if (!wrong) {
                out("GUARD $t keep (facts year=${facts?.year} eps=${facts?.episodes} last=${facts?.lastSeason} name=${facts?.name})")
            } else {
                val named = danmakuSeasonQuery(raw.title, season, facts?.name).takeIf { !genericSeasonName(facts?.name, raw.title) }
                val queries = (listOfNotNull(named) + seasonQueries(raw.title, season) + listOfNotNull(raw.title, raw.originalTitle)).distinct()
                val hits = queries.flatMap { q -> runCatching { matcher.searchAnime(q) }.getOrDefault(emptyList()) }.distinctBy { it.animeId }
                val right = pickSeasonEntry(hits, raw.title, season, raw.episodeNumber ?: 1, facts?.year, facts?.episodes, facts?.lastSeason, sourcePriority)
                out("GUARD $t wrong queries=$queries hits=${hits.size} -> ${right?.let { "\"${it.title}\" ${it.source} start=${it.startDate.take(10)} eps=${it.episodeCount}" } ?: "BLOCK (没有第 $season 季的弹幕)"}")
                if (right != null) {
                    matcher.bindAnime(r, right)
                    val again = matcher.match(raw.toMatchInput())
                    out("GUARD $t after pick ep=${again.episode?.let { "#${it.number} \"${it.title.take(70)}\" ${it.mapping.id}" } ?: "none"} best=\"${again.best?.title}\"")
                }
            }
        } else if (r.status != MatchStatus.Matched) {
            out("GUARD $t (not matched: ${r.status.id})")
        }
    }
}
