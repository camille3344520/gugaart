package com.gugaart.desktop.mac

/**
 * M2-10（P-mac-11，D-lead-11）：Dock 图标菜单的条目（纯函数，单测）。
 * 继续观看最多 3 条 + 「搜索…」+ 「付印新刊」；再往下是系统自己的项，不写。
 */
fun dockMenuEntries(resumeTitles: List<String>): List<String> =
    resumeTitles.take(3).map { "继续观看 · $it" } + listOf("搜索…", "付印新刊")

/** 续看条目 → Dock 上印的标题：集名优先，空了退剧名（会话没就绪 / 没有续看 = 空列表）。纯函数，有单测。 */
fun resumeTitlesOf(items: List<com.gugugaga.core.BaseItemDto>): List<String> =
    items.map { it.name.ifBlank { it.seriesName.orEmpty() } }

/** 号外出现且应用不在前台时发的那一条系统通知（纯函数，单测；第一次可能弹通知权限框，由人点）。 */
fun notificationCommand(title: String, body: String): List<String> =
    listOf("osascript", "-e", "display notification \"$body\" with title \"$title\"")