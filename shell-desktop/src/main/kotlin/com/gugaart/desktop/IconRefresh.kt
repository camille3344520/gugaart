package com.gugaart.desktop

import java.io.File

/*
 * 覆盖安装后桌面图标没变（用户 2026-11-15：「windows覆盖安装的情况下桌面图标没变嘞」）。
 *
 * 查过：装好的 GugaArt.exe 里已经是新图标（GUGA TV），桌面快捷方式指向它（图标位置 = 程序自己的第 0 个图标）——
 * 是 Windows 资源管理器的图标缓存还记着旧的：同一个路径的程序，换了图标它不会自己重读。
 * 做法：换了版本后第一次启动，叫一次系统自带的 `ie4uinit.exe -show`（Windows 10 / 11 刷新当前用户的图标缓存，安装程序常用的做法），
 * 桌面、开始菜单、任务栏上的图标就换成新的。每个版本只做一次（记在应用数据目录的 icon-refreshed.txt）；不是 Windows、开发运行不做。
 */
internal object IconRefresh {
    private const val MARK = "icon-refreshed.txt"

    /** 这个版本还没刷过图标缓存。纯函数，有单测。 */
    fun due(markText: String?, version: String): Boolean = markText?.trim() != version

    fun runOnce(dataDir: File, version: String, dev: Boolean) {
        if (dev || !System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        val mark = File(dataDir, MARK)
        val before = runCatching { mark.readText() }.getOrNull()
        if (!due(before, version)) return
        // 第一次装（以前没跑过任何版本）也刷一次：快捷方式是这次装的，缓存里本来就没有旧图标，刷了也无害
        val exe = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32\\ie4uinit.exe")
        val started = runCatching {
            if (exe.isFile) ProcessBuilder(exe.path, "-show").redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            true
        }.getOrDefault(false)
        if (started) runCatching {
            dataDir.mkdirs()
            mark.writeText(version)
        }
    }
}
