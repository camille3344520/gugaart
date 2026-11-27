package com.gugaart.ui.screens.detail

import com.gugaart.ui.player.formatBadges
import com.gugaart.ui.player.FormatBadgeRow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.ConfirmSheet
import com.gugaart.ui.components.Chip
import com.gugaart.ui.components.EpisodeRow
import com.gugaart.ui.components.ErrorView
import com.gugaart.ui.components.FullBleed
import com.gugaart.ui.components.LoadingView
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SectionHeader
import com.gugaart.ui.components.Skeleton
import com.gugaart.ui.components.TextLink
import com.gugaart.ui.components.VolumeCell
import com.gugaart.ui.components.bleedSourceOf
import com.gugaart.ui.components.phoneHeroHeight
import com.gugaart.ui.components.BleedSource
import com.gugaart.ui.components.spot
import com.gugaart.ui.data.Framing
import com.gugaart.ui.data.Kind
import com.gugaart.ui.data.Rules
import com.gugaart.ui.data.Title
import com.gugaart.ui.data.rememberLoad
import com.gugaart.ui.data.titleOf
import com.gugaart.ui.data.getItemShared
import com.gugaart.ui.mascot.EditorNote
import com.gugaart.ui.mascot.LineFacts
import com.gugaart.ui.mascot.LocalHour
import com.gugaart.ui.mascot.MascotLines
import com.gugaart.ui.mascot.rememberNoteVoice
import com.gugaart.ui.screens.BackRow
import com.gugaart.ui.screens.LocalChrome
import com.gugaart.ui.screens.LocalPreset
import com.gugaart.ui.screens.open
import com.gugaart.ui.screens.play
import com.gugaart.ui.screens.toggleFavorite
import com.gugaart.ui.screens.togglePlayed
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.icons.GuguIcon
import com.gugugaga.app.data.rememberImage
import com.gugugaga.app.ui.LocalGraph
import com.gugugaga.ds.theme.UiMode

/** 整幅图的高度（手机）：剧集 420、电影 430、人物 540。 */
internal val SERIES_BLEED = 420.dp
internal val MOVIE_BLEED = 430.dp
internal val PERSON_BLEED = 540.dp

/**
 * `Route.Item(id)` 的入口：先取这个条目，再按种类分到 剧集 / 电影 / 单集 / 人物 / 合集。
 * 取数和状态在入口这一层；电脑 / 电视的版式在下一阶段，现在这两端不进这里（根界面拦着）。
 */
@Composable
fun ItemScreen(id: String, day: Int) {
    val graph = LocalGraph.current
    val item = rememberLoad("item", id) { c -> c.titleOf(c.getItemShared(id)) }
    val t = item.value
    when {
        t == null && item.error != null -> DetailShell("", { graph.nav.backOrHome() }) { ErrorView(item.error!!, item::retry) }
        t == null -> DetailShell("", { graph.nav.backOrHome() }) { if (item.showSkeleton) LoadingView() }
        t.kind == Kind.Series -> SeriesScreen(t.id, day)
        t.kind == Kind.Episode -> EpisodeScreen(t, day)
        t.kind == Kind.Person -> PersonScreen(t.id)
        t.kind == Kind.Movie -> MoviePhone(t, day)
        t.kind == Kind.BoxSet -> com.gugaart.ui.screens.sets.SetScreen(t, day)
        else -> DetailShell(t.name, { graph.nav.backOrHome() }) { com.gugaart.ui.components.StateView("state-coming", "摆稿纸", "这一场还在筹备", "这一种条目敬请期待。") }
    }
}

/** 只有顶栏的空壳（加载中 / 出错时）：返回在左上，永远能退出去。 */
/**
 * 手机详情页的播放那一行（P1-11）：主按钮占满；有进度时右边一个小按钮「从头」= **这一集 / 这一部从 0 开始**（全应用「从头播放」都是这个意思）。
 */
@Composable
internal fun PlayRow(button: @Composable (Modifier) -> Unit, restart: (() -> Unit)?) {
    Row(Modifier.padding(top = 18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        button(Modifier.weight(1f))
        if (restart != null) TextLink("从头", restart, Modifier.padding(start = 16.dp).testTag("play-restart"), icon = GuguIcon.RotateCcw)
    }
}

@Composable
internal fun DetailShell(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = Guga.dimens.statusBar + 50.dp)) { content() }
        BackRow(title, solid = true, onBack = onBack)
    }
}

@Composable
fun SeriesScreen(id: String, day: Int) {
    val graph = LocalGraph.current
    val head = rememberLoad("series", id) { loadSeriesHead(it, id) }
    val h = head.value
    when {
        h != null -> if (Guga.uiMode == UiMode.Phone) SeriesPhone(h, day) else SeriesPhone(h, day)
        head.error != null -> DetailShell("", { graph.nav.backOrHome() }) { ErrorView(head.error!!, head::retry) }
        else -> DetailShell("", { graph.nav.backOrHome() }) { if (head.showSkeleton) LoadingView() }
    }
}

/**
 * 剧集详情（手机，设计稿 `p=series`）：整幅剧照 420 从屏幕顶端铺起，返回 / 收藏两个方框按钮压在两个上角；片名区压在渐隐处；
 * 续看按钮 → 收藏（印章位）/ 整季已看 → 首字下沉的简介 → 主编按 → 分集（选季「卷册」推开、超过 50 集分页、四位集号）→ 演职员。
 */
@Composable
fun SeriesPhone(head: SeriesHead, day: Int) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val preset = LocalPreset.current
    val dimens = Guga.dimens
    val type = Guga.type
    val colors = Guga.colors
    val series = head.series
    val hour = LocalHour.current
    val scroll = rememberScrollState(preset.scroll)
    com.gugaart.ui.screens.ScrollsToTop { scroll.animateScrollTo(0) }
    val voice = rememberNoteVoice()
    // 剧照（演职员之后一栏；点开看大图）
    val stills = rememberItemStills(series.id)
    val autoStill = LocalAutoOpenStill.current
    var openStill by remember { mutableStateOf(autoStill) }

    // 专色跟这部片的海报走
    val poster = rememberImage(series.art.poster, keepPrevious = true)
    LaunchedEffect(poster) { chrome.spot = poster?.spot() }

    var season by remember(head) { mutableStateOf(initialSeason(head, preset.season)) }
    var page by remember(head) { mutableIntStateOf(preset.episodePage) }
    var volumesOpen by remember { mutableStateOf(preset.volumesOpen) }
    // P0-3：整季标已看 / 未看先弹确认框（截图预置 `markseason` 直接打开）
    var markSeason by remember { mutableStateOf(preset.state == "markseason") }
    val slice = rememberLoad("episodes", series.id, season?.id, page) { loadEpisodes(it, series.id, season?.id, page) }
    val pages = pagesOf(slice.value)
    val fav = chrome.isFavorite(series)
    // 手机详情页顶部 = 竖版海报（没海报退横版剧照，高度回到 SERIES_BLEED）
    val heroH = phoneHeroHeight(series.art, SERIES_BLEED)
    val solidAt = with(LocalDensity.current) { (heroH - dimens.statusBar - 50.dp - 70.dp).toPx() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("series-phone")) {
            FullBleed(
                series.art,
                heroH,
                framing = Framing.of(series.id),
                poster = true,
                caption = when (bleedSourceOf(series.art, poster = true)) {
                    BleedSource.Poster -> "海报 · ${series.name}"
                    BleedSource.Backdrop -> "剧照 · ${series.name}"
                    BleedSource.PosterBlur -> "海报 · ${series.name}"
                    BleedSource.None -> null
                },
                scrollPx = { scroll.value.toFloat() },
            ) {
                TitleBlock(
                    series,
                    series.kicker("剧集"),
                    listOfNotNull(series.status, series.officialRating).joinToString(" · ").takeIf { it.isNotEmpty() },
                    Modifier.align(Alignment.BottomStart).padding(horizontal = dimens.margin).padding(bottom = 4.dp),
                )
            }
            Column(Modifier.padding(horizontal = dimens.margin)) {
                FactsRow("主演", leads(series).joinToString(" ").takeIf { it.isNotEmpty() }, "全", series.count?.let { "$it 集" }, Modifier.padding(top = 12.dp))
                val (label, time) = resumeLabel(head.upNext)
                PlayRow(
                    { m ->
                        PrimaryButton(
                            label,
                            { head.upNext?.let { graph.play(it.id, it.positionSec?.times(1000)) } ?: slice.value?.episodes?.firstOrNull { !it.unaired }?.let { graph.play(it.id) } },
                            m,
                            progress = head.upNext?.progress ?: 0f,
                            trailing = time,
                        )
                    },
                    restart = head.upNext?.takeIf { it.progress > 0f }?.let { up -> { graph.play(up.id, 0L) } },
                )
                ActionsRow(
                    fav,
                    {
                        graph.toggleFavorite(chrome, series.id, fav)
                        voice.say(MascotLines.pick(if (fav) "fav.off" else "fav.on", hour = hour, seed = series.id.hashCode() + day))
                    },
                    "整季已看",
                    played = season?.let { chrome.isPlayed(it.id, it.unplayed == 0) } == true,
                    onPlayed = { if (season != null) markSeason = true },
                    modifier = Modifier.padding(top = 14.dp),
                )
                // Trakt 追更（连着 Trakt 才有）
                com.gugaart.ui.components.rememberTraktFollow(series.id)?.let { f ->
                    com.gugaart.ui.components.TextLink(if (f.following) "✓ 已追更 · Trakt" else "＋ 追更 · Trakt", f.toggle, Modifier.padding(top = 12.dp).testTag("trakt-follow"))
                }
                Overview(Rules.fold(series.overview, PHONE_FOLD_CHARS), Modifier.padding(top = 18.dp))
                Rule(Modifier.padding(top = 18.dp))
                EditorNote(seriesNote(head, hour, day), Modifier.padding(vertical = 14.dp), label = "看点", english = "HIGHLIGHTS", voice = voice, photo = 46.dp)
                Rule()

                // ---- 分集 ----
                val many = head.seasons.size > 1
                SectionHeader(
                    "分集",
                    "EPISODES",
                    Modifier.padding(top = 26.dp),
                    trailing = seasonTally(season.takeIf { head.seasons.isNotEmpty() }, slice.value?.total)?.let { if (many) "$it ▾" else it },
                    onTrailing = if (many) ({ volumesOpen = !volumesOpen }) else null,
                )
                // 选季「卷册」：推开式，6 列格子；只有一季不出现
                AnimatedVisibility(many && volumesOpen, enter = expandVertically(), exit = shrinkVertically()) {
                    Column(Modifier.padding(top = 10.dp).testTag("volumes")) {
                        Rule(strong = true)
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            com.gugaart.ui.components.Kicker(listOf("选季", "${head.seasons.size} 季") + listOfNotNull(series.count?.let { "$it 集" }), Modifier.weight(1f))
                            TextLink("收起", { volumesOpen = false }, color = colors.dim)
                        }
                        for (row in head.seasons.chunked(6)) {
                            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (s in row) {
                                    val (text, latin) = seasonLabel(s)
                                    VolumeCell(text, s.episodes?.let { "$it 集" }, s.id == season?.id, s.number != null && s.number == head.upNext?.seasonNo, {
                                        season = s
                                        page = 0
                                    }, Modifier.weight(1f), latin = latin)
                                }
                                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        Rule(Modifier.padding(top = 6.dp))
                    }
                }
                // 集号分页：一季超过 50 集才出现，标签是绝对集号
                if (pages.isNotEmpty()) {
                    Row(Modifier.padding(top = 12.dp).fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("episode-pager"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (p in pages) Chip(p.label, p.index == page, { page = p.index })
                    }
                }
                val episodes = slice.value?.episodes
                when {
                    episodes == null && slice.error != null -> ErrorView(slice.error!!, slice::retry)
                    episodes == null -> repeat(4) {
                        // 没到的集用骨架，不显示成「没有」
                        Skeleton(Modifier.padding(vertical = 14.dp).fillMaxWidth().height(58.dp))
                        Rule()
                    }
                    episodes.isEmpty() -> BasicText(if ((slice.value?.total ?: 0) > 0) "分集未加载" else "没有分集", Modifier.padding(vertical = 24.dp), style = Guga.song(type.caption, color = colors.dim))
                    else -> for (e in episodes) {
                        val n = e.episodeNo ?: 0
                        // 看到一半的那一集不算「已看」（服务器可能两个都标着）：它是「正在看」，不打勾
                        val played = chrome.isPlayed(e.id, e.played && e.progress <= 0f)
                        EpisodeRow(
                            number = n,
                            name = e.name,
                            subtitle = episodeSubtitle(e, played, series.runtimeSec),
                            // 没有分集图的季：缩略图位换成斜纹空版（不退到整部剧同一张图，免得 155 行一模一样）
                            thumbUrl = e.art.still,
                            current = e.id == head.upNext?.id,
                            played = played,
                            progress = e.progress,
                            onClick = { graph.play(e.id, e.positionSec?.times(1000)) },
                            unaired = e.unaired,
                            onTick = {
                                graph.togglePlayed(chrome, e.id, played)
                                voice.say(MascotLines.pick(if (played) "played.off" else "played.on", LineFacts(ep = n, next = n + 1), hour, e.id.hashCode() + day))
                            },
                        )
                        Rule()
                    }
                }
            }
            CastBand(series.people, { graph.open(it.id) }, Modifier.padding(top = dimens.sectionGap))
            StillsBand(stills, { openStill = it }, Modifier.padding(top = dimens.sectionGap))
            Spacer(Modifier.height(72.dp))
        }
        BackRow(
            series.name,
            solid = com.gugaart.ui.screens.scrolledPast(scroll, solidAt),
            onBack = { graph.nav.backOrHome() },
            favorite = fav,
            onFavorite = { graph.toggleFavorite(chrome, series.id, fav) },
        )
        openStill?.let { StillsViewer(stills, it) { openStill = null } }
        season?.takeIf { markSeason }?.let { s ->
            val seen = chrome.isPlayed(s.id, s.unplayed == 0)
            val copy = seasonMarkCopy(s, toPlayed = !seen)
            ConfirmSheet(
                title = copy.title,
                text = copy.note.orEmpty(),
                confirm = copy.confirm,
                onConfirm = {
                    markSeason = false
                    graph.togglePlayed(chrome, s.id, seen)
                },
                onDismiss = { markSeason = false },
            )
        }
    }
}

/**
 * 电影详情（手机，设计稿 `p=movie`）：整幅剧照 430；片名 + 评分 + 「2 小时 28 分 · PG-13」；宣传语；主演 / 联合主演；
 * 播放按钮（右端是规格，来自真实媒体流，没有就不写）；收藏 / 标记已看；简介；主编按；版本（多于一个才出现）；音轨 / 字幕；演职员。
 */
@Composable
fun MoviePhone(movie: Title, day: Int) {
    val graph = LocalGraph.current
    val chrome = LocalChrome.current
    val preset = LocalPreset.current
    val dimens = Guga.dimens
    val type = Guga.type
    val colors = Guga.colors
    val hour = LocalHour.current
    val scroll = rememberScrollState(preset.scroll)
    com.gugaart.ui.screens.ScrollsToTop { scroll.animateScrollTo(0) }
    val voice = rememberNoteVoice()
    // 剧照（演职员之后一栏；点开看大图）
    val stills = rememberItemStills(movie.id)
    val autoStill = LocalAutoOpenStill.current
    var openStill by remember { mutableStateOf(autoStill) }
    val poster = rememberImage(movie.art.poster, keepPrevious = true)
    LaunchedEffect(poster) { chrome.spot = poster?.spot() }
    val fav = chrome.isFavorite(movie)
    val played = chrome.isPlayed(movie.id, movie.played)
    // 手机详情页顶部 = 竖版海报（没海报退横版剧照，高度回到 MOVIE_BLEED）
    val heroH = phoneHeroHeight(movie.art, MOVIE_BLEED)
    val solidAt = with(LocalDensity.current) { (heroH - dimens.statusBar - 50.dp - 70.dp).toPx() }
    // 版本 = 「源」（本台的版本 + 没被合并的重复片）：选中的那个决定徽章、播放按钮右端的规格、音轨 / 字幕和开播
    val sources = rememberSourcesState(movie)
    val source = sources.selected?.mediaSource ?: movie.sources.firstOrNull()
    // 音轨 / 字幕跟着选中的版本走（换版本 = 回到默认）；开着哪一张纸片：ver / audio / sub
    val picks = rememberTrackPicks(source)
    var sheet by remember(movie.id) { mutableStateOf(preset.state?.takeIf { it == "ver" || it == "audio" || it == "sub" }) }
    // 验收运行器（iOS pickers151）在这一页时把选择和纸片开关交出去；应用里 sink = null，什么都不做
    LocalDetailPickerSink.current?.let { sink -> androidx.compose.runtime.LaunchedEffect(picks) { sink(DetailPickerDriver(picks) { sheet = it }) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).testTag("movie-phone")) {
            FullBleed(
                movie.art,
                heroH,
                framing = Framing.of(movie.id),
                poster = true,
                caption = when (bleedSourceOf(movie.art, poster = true)) {
                    BleedSource.Poster -> "海报 · ${movie.name}"
                    BleedSource.Backdrop -> "剧照 · ${movie.name}" + (movie.year?.let { " · $it" } ?: "")
                    BleedSource.PosterBlur -> "海报 · ${movie.name}"
                    BleedSource.None -> null
                },
                scrollPx = { scroll.value.toFloat() },
            ) {
                TitleBlock(
                    movie,
                    movie.kicker(if (movie.kind == Kind.Episode) "单集" else "电影"),
                    listOfNotNull(movie.runtimeSec?.let { Rules.minutes(it) }, movie.officialRating).joinToString(" · ").takeIf { it.isNotEmpty() },
                    Modifier.align(Alignment.BottomStart).padding(horizontal = dimens.margin).padding(bottom = 4.dp),
                )
            }
            Column(Modifier.padding(horizontal = dimens.margin)) {
                // 宣传语（过了脏词检查的 tagline；没有就退简介头一两句，那时不重复放在这里）
                movie.lead?.takeIf { it.length <= 40 && !movie.overview.startsWith(it) }?.let {
                    BasicText(it, Modifier.padding(top = 10.dp), style = Guga.song(type.row, androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 2)
                }
                val cast = leads(movie)
                FactsRow("主演", cast.getOrNull(0), if (cast.size > 1) "联合主演" else null, cast.getOrNull(1), Modifier.padding(top = 14.dp))
                // 片源的效果徽章（4K / 杜比视界 / 全景声…；用户 2026-11-16：「影片详情页也可以打上对应的图标」）：不看设备能力，照常亮（decisions.md D-mac-6）
                FormatBadgeRow(formatBadges(source, null, null), 1.15f, Modifier.padding(top = 16.dp), notes = false)
                PlayRow(
                    { m ->
                        PrimaryButton(
                            if (movie.progress > 0f) "续看" else "播放",
                            { graph.playback.play(sources.playRoute(movie.positionSec?.times(1000), picks.audioIndex, picks.subtitleIndex)) },
                            m,
                            progress = movie.progress,
                            trailing = if (movie.progress > 0f) Rules.progressLabel(movie.progress, movie.runtimeSec, true) else specLine(source),
                        )
                    },
                    restart = if (movie.progress > 0f) ({ graph.playback.play(sources.playRoute(0L, picks.audioIndex, picks.subtitleIndex)) }) else null,
                )
                ActionsRow(
                    fav,
                    {
                        graph.toggleFavorite(chrome, movie.id, fav)
                        voice.say(MascotLines.pick(if (fav) "fav.off" else "fav.on", hour = hour, seed = movie.id.hashCode() + day))
                    },
                    "标记已看",
                    played,
                    { graph.togglePlayed(chrome, movie.id, played) },
                    Modifier.padding(top = 14.dp),
                )
                // 版本 / 音轨 / 字幕：一组收起来的行（版本只有一个时不出现），点开是贴底的纸片；
                // 紧跟在收藏 / 已看下面（P2-11：开播前要选的东西不用滑过简介去找），简介往后放
                TrackPickerGroup(sources, picks, { sheet = it }, Modifier.padding(top = 18.dp))
                Overview(Rules.fold(movie.overview, PHONE_FOLD_CHARS), Modifier.padding(top = 18.dp))
                Rule(Modifier.padding(top = 18.dp))
                EditorNote(movieNote(movie, hour, day), Modifier.padding(vertical = 14.dp), label = "看点", english = "HIGHLIGHTS", voice = voice, photo = 46.dp)
                Rule()
                // 出品方太多：截 5 家 +「等 N 家」
                Rules.studios(movie.studios)?.let {
                    BasicText("出品  $it", Modifier.padding(top = 14.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 2)
                }
            }
            CastBand(movie.people, { graph.open(it.id) }, Modifier.padding(top = dimens.sectionGap))
            StillsBand(stills, { openStill = it }, Modifier.padding(top = dimens.sectionGap))
            Spacer(Modifier.height(72.dp))
        }
        BackRow(movie.name, solid = com.gugaart.ui.screens.scrolledPast(scroll, solidAt), onBack = { graph.nav.backOrHome() }, favorite = fav, onFavorite = { graph.toggleFavorite(chrome, movie.id, fav) })
        openStill?.let { StillsViewer(stills, it) { openStill = null } }
        TrackPickerSheets(sheet, sources, picks) { sheet = null }
    }
}
