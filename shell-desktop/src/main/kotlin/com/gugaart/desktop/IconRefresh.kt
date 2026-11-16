package com.gugaart.desktop

import java.io.File
import java.util.concurrent.TimeUnit

/*
 * 覆盖安装后桌面图标没变（用户 2026-11-15：「windows覆盖安装的情况下桌面图标没变嘞」；2026-11-16：「桌面图标也还没修复」）。
 *
 * 查过：装好的 GugaArt.exe 里已经是新图标，桌面 / 开始菜单的快捷方式指向它（图标位置 = 「,0」：用程序自己的第 0 个图标）——
 * 是资源管理器的图标缓存按「路径 + 序号」记着旧图：同一个 exe 换了图标它不一定重读。只叫 `ie4uinit.exe -show`（1.2.0 起的做法）不够稳。
 * 现在：换了版本后第一次启动——
 *  1. 把应用图标写成一个**带版本号的** .ico（应用数据目录 icons/gugaart-<版本>.ico，旧版本的删掉）；
 *  2. 桌面、开始菜单里指向本程序的快捷方式，图标位置改成这个文件——路径是新的，资源管理器一定重新读；
 *  3. 再叫一次 `ie4uinit.exe -show` 让桌面马上刷新。
 * 每个版本只做一次（记在应用数据目录的 icon-refreshed.txt）；不是 Windows、开发运行不做。只改指向本程序的快捷方式，别的不碰。
 */
internal object IconRefresh {
    private const val MARK = "icon-refreshed.txt"

    /** 这个版本还没刷过图标。纯函数，有单测。 */
    fun due(markText: String?, version: String): Boolean = markText?.trim() != version

    /** 带版本号的图标文件名（路径变了资源管理器才会重读）。纯函数，有单测。 */
    fun iconFileName(version: String): String = "gugaart-" + version.replace(Regex("[^0-9A-Za-z._-]"), "_") + ".ico"

    fun runOnce(dataDir: File, version: String, dev: Boolean) {
        if (dev || !System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        val mark = File(dataDir, MARK)
        val before = runCatching { mark.readText() }.getOrNull()
        if (!due(before, version)) return
        // 后台做，不拖慢启动
        Thread({
            val ico = runCatching { writeIcon(dataDir, version) }.getOrNull()
            val exe = ProcessHandle.current().info().command().orElse(null)
            if (ico != null && exe != null && exe.endsWith(".exe", ignoreCase = true) && !exe.endsWith("java.exe", ignoreCase = true) && !exe.endsWith("javaw.exe", ignoreCase = true)) {
                runCatching { repointShortcuts(exe, ico) }
            }
            val refresh = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\ie4uinit.exe")
            val started = runCatching {
                if (refresh.isFile) ProcessBuilder(refresh.path, "-show").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                true
            }.getOrDefault(false)
            if (started) runCatching {
                dataDir.mkdirs()
                mark.writeText(version)
            }
        }, "gugaart-icon-refresh").apply { isDaemon = true }.start()
    }

    /** 写出这个版本的 .ico，删掉旧版本的；返回文件。 */
    private fun writeIcon(dataDir: File, version: String): File? {
        val dir = File(dataDir, "icons").apply { mkdirs() }
        val name = iconFileName(version)
        dir.listFiles()?.forEach { if (it.name != name && it.name.startsWith("gugaart-") && it.name.endsWith(".ico")) it.delete() }
        val out = File(dir, name)
        val bytes = IconRefresh::class.java.getResourceAsStream("/gugaart/icon.ico")?.use { it.readBytes() } ?: return null
        out.writeBytes(bytes)
        return out
    }

    /** 桌面、开始菜单（本用户 + 所有用户）里指向 [exe] 的快捷方式：图标位置改成 [ico]。没权限改的（所有用户那几个）跳过。 */
    private fun repointShortcuts(exe: String, ico: File) {
        val script = """
            ${'$'}ws = New-Object -ComObject WScript.Shell
            ${'$'}dirs = @([Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('Programs'), [Environment]::GetFolderPath('CommonDesktopDirectory'), [Environment]::GetFolderPath('CommonPrograms'))
            foreach (${'$'}d in ${'$'}dirs) {
              if (-not ${'$'}d) { continue }
              Get-ChildItem -LiteralPath ${'$'}d -Recurse -Filter *.lnk -ErrorAction SilentlyContinue | ForEach-Object {
                try {
                  ${'$'}s = ${'$'}ws.CreateShortcut(${'$'}_.FullName)
                  if (${'$'}s.TargetPath -ieq ${'$'}env:GUGA_EXE) { ${'$'}s.IconLocation = ${'$'}env:GUGA_ICO + ',0'; ${'$'}s.Save() }
                } catch {}
              }
            }
        """.trimIndent()
        val pb = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script)
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.environment()["GUGA_EXE"] = exe
        pb.environment()["GUGA_ICO"] = ico.absolutePath
        pb.start().waitFor(30, TimeUnit.SECONDS)
    }
}
