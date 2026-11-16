package com.gugaart.ui.player

import com.gugugaga.app.player.PlayerSnapshot
import com.gugugaga.app.player.TrackOption
import com.gugugaga.core.MediaStream

/*
 * 中文字幕排在前面（用户 2026-11-16：「字幕自动把中文字幕的选项往前面排，可以用一些英文影片，日语动漫做测试」）。
 *
 * 播放器的字幕面板、详情页的字幕选择都按这个排：简体 → 中文（没说简繁）→ 繁体 → 其他语言；同一档里保持服务器给的顺序。
 * 判断看语言码（chs / zh-Hans / chi / zho / cht / zh-Hant …）和名字（「简体」「繁中」「中英双语」…）。只改排列顺序，不改默认选哪条
 * （默认选哪条是 设置 › 字幕与音轨 的「字幕默认」）。
 */

private val SIMPLIFIED = Regex("""(^|[^a-z])(chs|zhs|zh-hans|zh_hans|zh-cn|zh_cn|zh-sg|gb|gbk|gb2312|sc)([^a-z]|$)""")
private val TRADITIONAL = Regex("""(^|[^a-z])(cht|zht|zh-hant|zh_hant|zh-tw|zh_tw|zh-hk|zh_hk|big5|tc)([^a-z]|$)""")
private val CHINESE = Regex("""(^|[^a-z])(chi|zho|zh|chinese|mandarin|cmn|cantonese|yue)([^a-z]|$)""")

/** 0 = 简体，1 = 中文，2 = 繁体，3 = 其他。纯函数，有单测。 */
fun chineseRank(language: String?, title: String?): Int {
    val s = listOfNotNull(language, title).joinToString(" ").lowercase()
    if (s.isBlank()) return 3
    return when {
        '简' in s || SIMPLIFIED.containsMatchIn(s) -> 0
        '繁' in s || TRADITIONAL.containsMatchIn(s) -> 2
        "中文" in s || "中字" in s || "国语" in s || "粤语" in s || "汉语" in s || CHINESE.containsMatchIn(s) -> 1
        else -> 3
    }
}

/** 按 [chineseRank] 排，同一档保持原来的顺序。纯函数，有单测。 */
fun <T> chineseFirst(items: List<T>, language: (T) -> String?, title: (T) -> String?): List<T> =
    items.withIndex().sortedWith(compareBy({ chineseRank(language(it.value), title(it.value)) }, { it.index })).map { it.value }

/** 详情页的字幕流：中文在前。 */
fun chineseSubtitlesFirst(streams: List<MediaStream>): List<MediaStream> =
    chineseFirst(streams, { it.language }, { listOfNotNull(it.title, it.displayTitle).joinToString(" ") })

/** 播放器里的字幕选项（不含「关」）：中文在前。语言和名字从这条片源的流里查（选项上只有显示名）。 */
fun orderedSubtitles(snap: PlayerSnapshot): List<TrackOption> {
    val streams = snap.resolved?.mediaSource?.mediaStreams.orEmpty().associateBy { it.index }
    return chineseFirst(
        snap.subtitles.filter { it.index != null },
        { t -> streams[t.index]?.language },
        { t -> listOfNotNull(t.label, streams[t.index]?.title, streams[t.index]?.displayTitle).joinToString(" ") },
    )
}
