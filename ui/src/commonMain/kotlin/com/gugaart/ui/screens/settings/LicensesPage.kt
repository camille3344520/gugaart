package com.gugaart.ui.screens.settings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SettingGroup
import com.gugaart.ui.screens.PageEnd
import com.gugaart.ui.screens.TitlePage
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.ui.LocalGraph

/*
 * 设置 › 关于 › 开源许可（D-lead-41）：手机 / iPhone 是一张标题页，电脑嵌在设置的右栏（[LicensesBody]），电视是右边页边的一列（`TvSettings`）。
 * 内容是纯数据 [OpenSourceLicenses]；这里只排版：纸面语言，分组标题 + 一行一个组件（左 名字和出处，右 许可证），行间细线。只读，不接按压。
 */

/** 这台设备是哪个包、列哪几组。 */
@Composable
internal fun rememberLicenses(): Pair<LicensePlatform, List<LicenseGroup>> {
    val g = LocalGraph.current
    val mac = com.gugaart.ui.desktop.LocalDesktopHost.current.platformName == "macOS"
    return remember(g, mac) {
        val p = OpenSourceLicenses.platformOf(g.deps.platformLabel, g.deps.uiMode, mac)
        p to OpenSourceLicenses.groups(p, iosPlayer = g.deps.player != null)
    }
}

/** 手机 / iPhone：`Route.Settings("licenses")` 的那一页（从「关于」进来，返回回「关于」）。 */
@Composable
internal fun LicensesPage() {
    val g = LocalGraph.current
    TitlePage(
        "设置 · 关于 · LICENSES",
        "开源许可",
        onBack = { g.nav.backOrHome() },
        barTitle = "关于",
        tag = "settings-licenses",
    ) {
        Column(Modifier.padding(horizontal = Guga.dimens.margin)) {
            Rule(strong = true)
            BasicText(OpenSourceLicenses.INTRO, Modifier.padding(top = 16.dp), style = Guga.song(Guga.type.caption, color = Guga.colors.dim, lineHeight = Guga.type.caption * 1.6f))
            LicensesBody()
        }
        PageEnd("开源许可")
    }
}

/** 各组 + 页尾一句（完整声明在哪）。手机页和电脑右栏共用。 */
@Composable
internal fun LicensesBody() {
    val colors = Guga.colors
    val type = Guga.type
    val (platform, groups) = rememberLicenses()
    // 许可证那一栏按自己的长短占宽，最多四成（短的「MIT」不把组件名挤到折三行）
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("licenses-body")) {
        val licenseMax = maxWidth * 0.42f
        Column(Modifier.fillMaxWidth()) {
            for (group in groups) {
                SettingGroup(group.title, group.english)
                if (group.note != null) {
                    BasicText(group.note, Modifier.padding(top = 6.dp, bottom = 6.dp), style = Guga.song(type.caption, color = colors.dim, lineHeight = type.caption * 1.6f))
                }
                Rule()
                for (e in group.entries) LicenseLine(e, licenseMax)
            }
            OpenSourceLicenses.footer(platform)?.let {
                BasicText(it, Modifier.padding(top = 18.dp), style = Guga.note(type.caption, colors.faint))
            }
        }
    }
}

/** 一行：上 组件名（左）+ 许可证（右，斜体，长了折行），下 出处（版本 · 上游，整行宽）。 */
@Composable
private fun LicenseLine(e: LicenseEntry, licenseMax: Dp) {
    val colors = Guga.colors
    val type = Guga.type
    Column(Modifier.fillMaxWidth().testTag("license-row")) {
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                BasicText(e.name, Modifier.weight(1f).padding(end = 14.dp), style = Guga.song(type.row, FontWeight.Medium))
                BasicText(e.license, Modifier.widthIn(max = licenseMax).padding(top = 2.dp), style = Guga.note(type.row * 0.9f, colors.dim).copy(textAlign = TextAlign.End))
            }
            // 出处那一句占满整行（长的说明——为什么整体按 GPL 分发——不被右边的许可证栏挤成窄条）
            if (!e.note.isNullOrEmpty()) BasicText(e.note, Modifier.padding(top = 4.dp), style = Guga.song(type.caption * 0.96f, color = colors.faint, lineHeight = type.caption * 1.5f))
        }
        Rule()
    }
}
