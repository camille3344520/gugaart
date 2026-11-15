package com.gugaart.ui.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugaart.ui.components.PrimaryButton
import com.gugaart.ui.components.SheetHost
import com.gugaart.ui.components.SlipHeader
import com.gugaart.ui.components.progressLine
import com.gugaart.ui.screens.update.LocalUpdateStateOverride
import com.gugaart.ui.screens.update.extraView
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.ui.LocalGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import com.gugugaga.app.platform.localUtcOffsetMs

/** 称号纸片的几个数。 */
object RankSpec {
    /** 启动后 / 从播放器回来后等这么久再数（让首页先取数；服务器把刚看完的记成已看也要一点时间）。 */
    const val CHECK_DELAY_MS = 4_000L
}

/** 「06」：级数补两位。 */
fun levelNo(level: Int): String = level.toString().padStart(2, '0')

/** 纸片的页眉签和那一行小字：升级 / 第一次（读者证上有了称号）/ 自己点开看的。纯函数，有单测。 */
fun rankHead(news: RankNews): Pair<String, String> = when {
    news.browse -> "称号" to "你现在的称号"
    news.first -> "称号" to "你有观影称号了"
    else -> "升级" to "升到第 ${news.level} 级"
}

/**
 * 称号这件事挂在根上的一处（不在播放画面上；号外在的时候先让号外）：
 * - [live]（应用里）：登录后、每次从播放器回来后过 [RankSpec.CHECK_DELAY_MS] 取一次看完的记录（[loadWatchFacts]，只读）：
 *   看过几部比上次报的级高 → 出等级纸片（[rankNews]）；新拿到特别称号 → 出特别称号纸片（[specialNews]，等级的先出）；
 * - 读者证上点那一行 / 「我的」里点一枚盾 → 出同一种纸片（browse）。
 * [tv] = 电视的那一张（遥控器焦点），别的端是纸片 [RankSlip]。
 */
@Composable
fun RankHost(live: Boolean, tv: Boolean) {
    val g = LocalGraph.current
    val session by g.session.session.collectAsState()
    val s = session ?: return
    if (live) {
        // 报头那枚称号：先用上次记下的部数，不等这一次数完
        LaunchedEffect(s.serverId, s.userId) {
            val account = RankStore.accountOf(s.serverId, s.userId)
            RankBoard.count = RankStore(g.deps.local).lastCount(account)
            RankBoard.owned = SpecialStore(g.deps.local).announced(account).orEmpty()
            // 报头展示哪个称号：每次打开应用（每个账号）挑一次，从播放器回来不重挑
            if (RankBoard.featuredFor != account) {
                val recentStore = RecentTitleStore(g.deps.local)
                var recent = recentStore.recent(account)
                if (recent == null) {
                    // 这个功能之前就拿到称号的：用现在拥有的凑一份「最近拿到的」
                    val level = RankStore(g.deps.local).announced(account) ?: 0
                    if (level > 0 || RankBoard.owned.isNotEmpty()) recent = seedRecent(level, RankBoard.owned).also { recentStore.set(account, it) }
                }
                RankBoard.featured = recent?.let { pickChipTitle(it, kotlin.random.Random.Default) }
                RankBoard.featuredFor = account
            }
        }
        // 根上这一处只在不播放时在组合里：从播放器回来 = 重新进组合 = 再数一次
        LaunchedEffect(s.serverId, s.userId) {
            delay(RankSpec.CHECK_DELAY_MS)
            val facts = try {
                loadWatchFacts(s.client, ::localUtcOffsetMs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                return@LaunchedEffect
            }
            val store = RankStore(g.deps.local)
            val account = RankStore.accountOf(s.serverId, s.userId)
            val before = store.announced(account)
            val total = facts.total
            val level = Ranks.levelOf(total)
            val news = rankNews(before, level)
            store.mark(account, level)
            // 报头那枚称号：部数涨了就浮出「+N 部」，进度线往前走
            RankBoard.counted(total, store.lastCount(account))
            store.setLastCount(account, total)
            if (news != null) RankBoard.open(RankNews(news, total, first = before == null))
            // 特别称号：新拿到的出一张纸片（第一次检查：一次报出已经拿到的）
            val specials = SpecialStore(g.deps.local)
            val unlocked = Specials.unlocked(facts)
            val fresh = specialNews(specials.announced(account), unlocked)
            specials.mark(account, unlocked)
            RankBoard.owned = specials.announced(account).orEmpty()
            if (fresh != null) RankBoard.special = fresh.copy(owned = RankBoard.owned.size)
            // 最近拿到的称号：新拿到的排到最前，报头换成最新的那个（第一次检查：特别称号在前、等级最新）
            val ranksGained = if (news != null) ((before ?: (level - 1)).coerceAtLeast(0) + 1..level).map(::rankKey) else emptyList()
            val specialsGained = fresh?.ids.orEmpty().map(::specialKey)
            val gained = if (before == null) specialsGained + ranksGained else ranksGained + specialsGained
            if (gained.isNotEmpty()) {
                RankBoard.featured = RecentTitleStore(g.deps.local).push(account, gained).firstOrNull()
                RankBoard.featuredFor = account
            }
        }
    }
    val news = RankBoard.news
    val special = RankBoard.special
    if (news == null && special == null) return
    // 号外先出：更新的纸片还在，称号等它收起再出
    val override = LocalUpdateStateOverride.current
    val update by g.update.state.collectAsState()
    val dismissed by g.update.dismissedVersion.collectAsState()
    if (extraView(override ?: update, com.gugaart.ui.screens.update.UpdateConsent.dismissedFor(dismissed), com.gugaart.ui.screens.update.UpdateConsent.ignored) != null) return
    // 等级的先出，收起后再出特别称号
    when {
        news != null -> if (tv) com.gugaart.ui.tv.system.TvRankSlip(news) else RankSlip(news)
        special != null -> if (tv) com.gugaart.ui.tv.system.TvSpecialSlip(special) else SpecialSlip(special)
    }
}

/**
 * 称号纸片（手机 / 电脑）：页眉「升级」+ 第几级；这一级的徽章（[RankBadge]）；称号、那句话、看过几部和离下一级还差几部；
 * 下面两排共十格的称号表（每格一枚徽章：拿到的彩色、现在这一级朱红框、没拿到的灰）；底下一行怎么数。点外面 / 返回 / 「收下」= 收起。
 */
@Composable
fun RankSlip(news: RankNews, onClose: () -> Unit = RankBoard::close) {
    val colors = Guga.colors
    val type = Guga.type
    val rank = Ranks.of(news.level)
    val (badge, head) = rankHead(news)
    SheetHost(onClose, tag = "rank-slip") {
        SlipHeader(badge, "RANK · 观影称号", "No. ${levelNo(news.level)} / ${Ranks.MAX}")
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(head, style = Guga.song(type.caption, FontWeight.Bold, colors.spot), maxLines = 1)
                BasicText(rank?.title ?: "还没有称号", Modifier.padding(top = 6.dp).testTag("rank-title"), style = Guga.song(34.sp, FontWeight.Black, lineHeight = 42.sp), maxLines = 1)
                BasicText(rank?.line ?: "看完第一部，就是「${Ranks.ALL.first().title}」。", Modifier.padding(top = 8.dp), style = Guga.song(type.caption, color = colors.dim, lineHeight = 20.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            // 这一级的徽章（还没有称号 = 第一级的灰章）
            RankBadge(news.level, 104.dp, Modifier.padding(start = 10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Bottom) {
            BasicText("看过 ${news.count} 部", style = Guga.song(type.row, FontWeight.Bold), maxLines = 1)
            Spacer(Modifier.weight(1f))
            BasicText(Ranks.nextLine(news.count), Modifier.padding(start = 12.dp), style = Guga.song(type.caption, color = colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(2.dp).progressLine(Ranks.progress(news.count), colors.spot, track = colors.line, outside = false).testTag("rank-progress"))
        RankLadder(news.level, Modifier.padding(top = 18.dp))
        BasicText(Ranks.RULE, Modifier.padding(top = 12.dp), style = Guga.song(type.fine, color = colors.faint), maxLines = 2)
        PrimaryButton(if (news.browse) "好" else "收下", onClose, Modifier.padding(top = 16.dp).testTag("rank-ok"), icon = null)
    }
}

/** 称号表：两排、每排五级——徽章、称号、几部。拿到的徽章彩色、墨色线框；现在这一级朱红框；没拿到的徽章灰、字浅。 */
@Composable
fun RankLadder(level: Int, modifier: Modifier = Modifier) {
    val colors = Guga.colors
    Column(modifier.fillMaxWidth().testTag("rank-ladder"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in Ranks.ALL.chunked(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (r in row) {
                    val reached = r.level <= level
                    val current = r.level == level
                    val ink = when {
                        current -> colors.ink
                        reached -> colors.ink
                        else -> colors.faint
                    }
                    Column(
                        Modifier.weight(1f)
                            .then(if (current) Modifier.background(colors.seal.copy(alpha = 0.22f)).border(1.5.dp, colors.seal) else Modifier.border(1.dp, if (reached) colors.ink.copy(alpha = 0.55f) else colors.line))
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        RankBadge(r.level, 38.dp, locked = !reached)
                        BasicText(r.title, Modifier.padding(top = 3.dp), style = Guga.song(12.sp, FontWeight.Bold, ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText("${r.at} 部", Modifier.padding(top = 2.dp), style = Guga.song(10.sp, color = if (current) colors.ink.copy(alpha = 0.8f) else colors.faint), maxLines = 1)
                    }
                }
            }
        }
    }
}
