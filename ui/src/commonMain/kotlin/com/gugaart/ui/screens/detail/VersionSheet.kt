package com.gugaart.ui.screens.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gugaart.ui.components.RadioMark
import com.gugaart.ui.components.Rule
import com.gugaart.ui.components.SettingRow
import com.gugaart.ui.components.SheetHost
import com.gugaart.ui.components.SlipHeader
import com.gugaart.ui.components.ValueChevron
import com.gugaart.ui.theme.Guga
import com.gugugaga.ds.physics.pressGraphics
import com.gugugaga.ds.physics.pressable
import com.gugugaga.ds.physics.rememberPressState

/*
 * 手机详情页的「版本」（1.5.0 C2-UI；有别的服务器参与时叫「源」，B2-UI）：一行「版本 · 选中的那个 ›」，点开是贴底的纸片，列出全部的源（本台的版本 + 重复片）。
 * 点一行 = 选中 + 记住 + 收起；播放 / 续看放的就是它。只有一个源时不出现（和以前一样）。
 */

/** 「版本」那一行：左 = 「版本」+ 选中那个的规格，右 = 选中那个的名字 + ›。 */
@Composable
fun VersionRow(state: SourcesState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val s = state.selected ?: return
    val r = state.rowText(s)
    Column(modifier) {
        Rule()
        SettingRow(state.sectionTitle, note = r.spec.takeIf { it.isNotEmpty() }, tag = "phone-version-row", onClick = onOpen) { ValueChevron(r.title) }
    }
}

/** 贴底的纸片：每个源一行（选中 = 实心小方块 + 加粗米白；没选的 = 空心 + 次要字），标题后面是「推荐」「另一份文件」小签，第二行是规格。 */
@Composable
fun VersionSheet(state: SourcesState, onDismiss: () -> Unit) {
    val colors = Guga.colors
    val type = Guga.type
    SheetHost(onDismiss, tag = "version-sheet", bottom = true) {
        SlipHeader(state.sectionTitle, state.sectionEnglish, trailing = "${state.sources.size} 个")
        Column(Modifier.padding(top = 4.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            for (s in state.sources) {
                val r = state.rowText(s)
                val on = state.isSelected(s)
                val press = rememberPressState()
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 62.dp).pressGraphics(press)
                        .pressable(press, label = r.title, onClick = {
                            state.pick(s)
                            onDismiss()
                        })
                        .padding(vertical = 10.dp)
                        .testTag(if (on) "version-option-on" else "version-option"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioMark(on)
                    Column(Modifier.weight(1f).padding(start = 18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SourceDot(r, 7.dp, gap = 7.dp)
                            BasicText(r.title, Modifier.weight(1f, fill = false), style = Guga.song(type.row, if (on) FontWeight.Bold else FontWeight.Normal, if (on) colors.ink else colors.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            SourceTags(r, type.kicker, 1.dp, gap = 8.dp)
                        }
                        if (r.spec.isNotEmpty()) BasicText(r.spec, Modifier.padding(top = 3.dp), style = Guga.song(type.caption, color = colors.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Rule()
            }
            // 别的服务器还在找 / 没找到的那几台：排在单子最后，不挤动上面的行
            SourceProgress(state, type.caption, 6.dp, Modifier.padding(top = 12.dp, start = 27.dp))
        }
    }
}
