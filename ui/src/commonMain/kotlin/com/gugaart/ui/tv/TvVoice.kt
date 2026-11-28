package com.gugaart.ui.tv

/**
 * 电视搜索的语音输入（1.6 B5）。听写是壳的事：安卓电视壳在系统有语音识别（`RecognizerIntent.ACTION_RECOGNIZE_SPEECH` 有人接）时
 * 把 [host] 设成「起系统的识别界面 → 返回识别出来的第一句（取消 / 没听清 = null）」；没有语音服务 = null，搜索页不显示入口。
 * 遥控器的麦克风键（`KEYCODE_SEARCH` / `KEYCODE_VOICE_ASSIST`，系统没有先拿走的话）在搜索页上也走这里。
 */
object TvVoice {
    var host: (suspend () -> String?)? = null
}

/**
 * 识别出来的一句话 → 搜索词：去掉标点和空白两头，字母转大写（和字母盘打出来的一样），最长 [TvSearchSpec.MAX_QUERY] 个字。
 * 什么都不剩 = null（不动原来的词）。纯函数，有单测。
 */
fun tvVoiceQuery(text: String?): String? {
    val kept = text.orEmpty().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
    val q = kept.trim().replace(Regex(" +"), " ").uppercase().take(TvSearchSpec.MAX_QUERY).trimEnd()
    return q.takeIf { it.isNotEmpty() }
}
