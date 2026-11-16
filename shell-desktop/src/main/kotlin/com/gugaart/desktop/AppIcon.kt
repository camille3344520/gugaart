package com.gugaart.desktop

import java.awt.Image
import java.awt.RenderingHints
import java.awt.Window
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/*
 * 窗口和任务栏的图标（用户 2026-11-16：「windows打开图标是这样的」——任务栏上是 Java 的 Duke）。
 *
 * Windows 的窗口栈是上游的 VideoStage：一个 AWT Frame（底板）+ 一个 ComposeDialog（界面），两个都没设图标，任务栏就拿 JVM 自带的 Duke。
 * 这里把应用图标（和安装包、快捷方式同一张，resources/gugaart/icon-512.png）缩成任务栏 / 标题栏 / Alt+Tab 要的几档，设到这两个窗口上。
 */
internal object AppIcon {
    private val SIZES = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    /** 几档尺寸的图标（读不到 = 空，窗口照旧用系统的）。 */
    val images: List<Image> by lazy {
        val src = runCatching { AppIcon::class.java.getResourceAsStream("/gugaart/icon-512.png")?.use { ImageIO.read(it) } }.getOrNull()
            ?: return@lazy emptyList()
        SIZES.map { s -> scaled(src, s) }
    }

    fun apply(vararg windows: Window) {
        val list = images
        if (list.isEmpty()) return
        for (w in windows) runCatching { w.iconImages = list }
    }

    private fun scaled(src: BufferedImage, size: Int): BufferedImage {
        val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.drawImage(src, 0, 0, size, size, null)
        g.dispose()
        return out
    }
}
