package com.gugaart.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gugaart.ui.backup.BackupController
import com.gugaart.ui.backup.LocalBackupFiles
import com.gugaart.ui.backup.WebDavConfig
import com.gugaart.ui.backup.pendingText
import com.gugaart.ui.components.ActionWord
import com.gugaart.ui.components.InputLine
import com.gugaart.ui.components.SettingGroup
import com.gugaart.ui.components.SettingRow
import com.gugaart.ui.data.GugaSettingsStore
import com.gugaart.ui.theme.Guga
import com.gugugaga.app.ui.LocalGraph

/**
 * 设置 › 账号与服务器 › 备份与还原（用户 2026-11-15：「配置备份还原功能，一份配置支持3端」「支持本地和webdav」）。
 * 本地文件：导出 / 从文件还原（壳给文件对话框；没有 = 这一块不出）。WebDAV：地址、用户名、密码（存在系统安全存储），测试、备份、还原。
 * 还原先读出来给人看是哪一份，确认了才覆盖。
 */
@Composable
internal fun BackupGroup() {
    val g = LocalGraph.current
    val colors = Guga.colors
    val scope = rememberCoroutineScope()
    val files = LocalBackupFiles.current
    val prefs = LocalGugaSettings.current ?: remember(g) { GugaSettingsStore(g.deps.local) }
    val platformName = com.gugaart.ui.desktop.LocalDesktopHost.current.platformName
    val c = remember(g, files, platformName) { BackupController(g, prefs, files, scope, platformName) }
    LaunchedEffect(c) { c.load() }

    SettingGroup("备份与还原", "BACKUP")
    BasicText(
        "含设置、弹幕服务器地址和令牌、TMDB 令牌、Emby 地址和用户名",
        Modifier.padding(vertical = 10.dp),
        style = Guga.note(Guga.type.caption, colors.dim),
        maxLines = 4,
    )

    val pending = c.pending
    if (pending != null) {
        SettingRow("用这份备份覆盖这台设备的设置？", note = pendingText(pending), tag = "backup-confirm") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionWord("取消", { c.cancel() }, enabled = c.busy == null)
                ActionWord("还原", { c.apply() }, enabled = c.busy == null, danger = true, modifier = Modifier.testTag("backup-apply"))
            }
        }
    }

    if (files != null) {
        SettingRow("导出到文件", note = "存一份 gugaart-backup.json", tag = "backup-export") {
            ActionWord(if (c.busy == "file-out") "导出中…" else "导出", { c.exportFile() }, enabled = c.busy == null)
        }
        SettingRow("从文件还原", tag = "backup-import") {
            ActionWord(if (c.busy == "file-in") "读取中…" else "选文件", { c.importFile() }, enabled = c.busy == null)
        }
    }

    // ---- WebDAV
    val saved = c.webdav
    var url by remember(saved) { mutableStateOf(saved.url) }
    var user by remember(saved) { mutableStateOf(saved.user) }
    var password by remember(saved) { mutableStateOf(saved.password) }
    val dirty = url != saved.url || user != saved.user || password != saved.password
    SettingRow("WebDAV", note = "存为文件夹里的 gugaart-backup.json", tag = "backup-dav")
    InputLine(url, { url = it }, "https://dav.example.com/backup/", Modifier.fillMaxWidth().padding(start = 20.dp), tag = "backup-dav-url")
    InputLine(user, { user = it }, "用户名", Modifier.fillMaxWidth().padding(start = 20.dp), tag = "backup-dav-user")
    InputLine(password, { password = it }, "密码 / 应用密码", Modifier.fillMaxWidth().padding(start = 20.dp), secret = true, tag = "backup-dav-pass")
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        if (dirty) ActionWord("保存", { c.saveWebdav(WebDavConfig(url, user, password)) }, enabled = c.busy == null, modifier = Modifier.testTag("backup-dav-save"))
        ActionWord(if (c.busy == "dav-test") "测试中…" else "测试连接", { c.testWebdav() }, enabled = !dirty && saved.complete && c.busy == null)
    }
    // 两个大动作单独一排（手机上三个放一排挤不下）
    val ready = !dirty && saved.complete && c.busy == null
    Row(Modifier.fillMaxWidth().padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        ActionWord(if (c.busy == "dav-up") "备份中…" else "备份到 WebDAV", { c.upload() }, enabled = ready, modifier = Modifier.testTag("backup-dav-up"))
        ActionWord(if (c.busy == "dav-down") "读取中…" else "从 WebDAV 还原", { c.download() }, enabled = ready, modifier = Modifier.testTag("backup-dav-down"))
    }
    val msg = c.message
    if (msg != null) BasicText(msg, Modifier.padding(start = 20.dp, top = 10.dp).testTag("backup-message"), style = Guga.note(Guga.type.caption, colors.ink), maxLines = 3)
}
