package com.gugaart.desktop.mac

/**
 * M2-10（P-mac-11，D-lead-11）：Dock 图标菜单的条目（纯函数，单测）。
 * 继续观看最多 3 条 + 「搜索…」+ 「付印新刊」；再往下是系统自己的项，不写。
 */
fun dockMenuEntries(resumeTitles: List<String>): List<String> =
    resumeTitles.take(3).map { "继续观看 · $it" } + listOf("搜索…", "付印新刊")

/** Dock 菜单的一条：标题 + 可不可点。 */
data class DockEntry(val label: String, val enabled: Boolean = true)

/**
 * Dock 菜单条目（带可用性）：继续观看 ≤3 + 搜索（恒可点）+ 付印新刊。
 * 「付印新刊」只在首页挂着（[reprintReady]，同应用菜单那条的待遇）时可点，别的页灰着。纯函数，单测。
 */
fun dockMenuItems(resumeTitles: List<String>, reprintReady: Boolean): List<DockEntry> =
    resumeTitles.take(3).map { DockEntry("继续观看 · $it") } +
        listOf(DockEntry("搜索…"), DockEntry("付印新刊", enabled = reprintReady))

/** 续看条目 → Dock 上印的标题：集名优先，空了退剧名（会话没就绪 / 没有续看 = 空列表）。纯函数，有单测。 */
fun resumeTitlesOf(items: List<com.gugugaga.core.BaseItemDto>): List<String> =
    items.map { it.name.ifBlank { it.seriesName.orEmpty() } }

/** 号外出现且应用不在前台时发的那一条系统通知（纯函数，单测；第一次可能弹通知权限框，由人点）。 */
fun notificationCommand(title: String, body: String): List<String> =
    listOf("osascript", "-e", "display notification \"$body\" with title \"$title\"")