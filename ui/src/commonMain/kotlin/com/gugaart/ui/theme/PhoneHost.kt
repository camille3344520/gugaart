package com.gugaart.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow

/*
 * 手机宿主样式（ADR 0005 §2.1，M4-1）：`UiMode.Phone` 不加档，iOS 和安卓手机的差异只经这两个由壳提供的值，
 * 和电脑的 `LocalDesktopHost` 同一个做法。**默认值 = 安卓的现状**：安卓壳什么都不给，像素一个不变。
 * 规则：`:ui` 里不出现「是不是 iOS」的判断，只读 [LocalPhoneHost] / [LocalPhoneInsets]。
 */

/** 触感的四种（design/ios/notes.md §1 第 9 条）：翻期号 / 换标签 = 选择，擦章 / 校对勾 = 轻，长按 / 下拉拉满 = 中，落章 / 付印 = 成功。 */
enum class HapticStyle { Selection, Light, Medium, Success }

/**
 * 新版从哪来（号外和设置 › 关于怎么写）：
 * - [Install]：应用自己下载并安装（安卓、Windows）；
 * - [Sideload]：只查、不下载——号外「打开发布页」（GitHub 上这一版的 Release），用户自己用自签的工具装（iOS 1.3，D-lead-22 / 26）；
 * - [AppStore] / [TestFlight]：系统商店管（留着，1.3 不用）；
 * - [None]：不查。
 */
enum class Distribution { Install, Sideload, AppStore, TestFlight, None }

@Immutable
data class PhoneHost(
    /** 系统有返回键 / 返回手势交给应用：有 = 安卓（「再按一次返回」小条）；iOS 没有，应用不拦退出。 */
    val systemBack: Boolean = true,
    /** 左边缘右滑返回（iOS 惯例；安卓的系统自己有，不装）。 */
    val edgeBack: Boolean = false,
    /** 触感；null = 没有。 */
    val haptic: ((HapticStyle) -> Unit)? = null,
    /** AirPlay 路由选择器（系统的，只换声音的输出设备，D-lead-35）；null = 不画这个钮。 */
    val airPlay: (() -> Unit)? = null,
    /**
     * 画中画用应用自己的钮和纸面说明（iOS M5-10，D-lead-32 / 24：画面搬进系统的小窗，这一页留着说明）。false = 安卓的现状：
     * 安卓的画中画是整个页面缩进小窗，不画钮、不换说明。
     */
    val pipInPage: Boolean = false,
    val distribution: Distribution = Distribution.Install,
    /** 设置 › 关于、备份文件里的平台名。 */
    val platformName: String = "安卓手机",
    /** 系统的「点状态栏」（iOS，D-lead-27）：每来一个，当前页的主滚动区滚回顶；null = 没有这回事。 */
    val statusBarTaps: Flow<Unit>? = null,
)

val LocalPhoneHost = staticCompositionLocalOf { PhoneHost() }

/** 安全区（dp）。默认 = 安卓手机的现状：状态栏占位 46、标签栏离底 18、左右 0。 */
@Immutable
data class PhoneInsets(val top: Dp = 46.dp, val bottom: Dp = 18.dp, val left: Dp = 0.dp, val right: Dp = 0.dp)

/** 壳给（iOS：系统的安全区；安卓：不给 = 默认）。离屏测试直接 provide 一份。 */
val LocalPhoneInsets = staticCompositionLocalOf { PhoneInsets() }

/** 触感：有就调，没有就算了（安卓壳不给时这里什么都不做）。 */
fun PhoneHost.feel(style: HapticStyle) {
    haptic?.invoke(style)
}

/**
 * 矮屏收紧封面区（D-lead-23，design/ios/notes.md §1 第 3 条）：首页封面块按可用高度缩放，字号不变。
 * 可用高 = 屏幕高 − 状态栏占位：≥ 905 − 46（参考安卓机）→ 1；402 × 874（可用 812）→ 0.96；375 × 812（可用 762）→ 0.86；中间线性，两头封住。
 * 纯函数，有单测。
 */
fun coverScale(availableHeightDp: Float): Float {
    val full = 905f - 46f
    return when {
        availableHeightDp >= full -> 1f
        availableHeightDp >= 812f -> 0.96f + (availableHeightDp - 812f) * (1f - 0.96f) / (full - 812f)
        availableHeightDp >= 762f -> 0.86f + (availableHeightDp - 762f) * (0.96f - 0.86f) / (812f - 762f)
        else -> 0.86f
    }
}
