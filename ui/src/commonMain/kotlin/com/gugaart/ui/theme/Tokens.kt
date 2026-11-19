package com.gugaart.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gugugaga.ds.theme.UiMode

/**
 * 「咕嘎画报」的颜色 token（docs/design-language.md §1）。只有深色；数值只在这里出现，界面代码一律写 `Guga.colors.xxx`。
 *
 * 专色 [spot] 是唯一会变的颜色：取自当前海报（[spotOf]），进详情页跟那部片走。印章、焦点、校对勾【不】跟专色，永远是 [seal]。
 */
@Immutable
data class GugaColors(
    /** 墨黑纸 `#100E0B`：所有页面的底。 */
    val paper: Color = Color(0xFF100E0B),
    /** 米白字 `#EFE6D4`：正文、主线、按钮实底。 */
    val ink: Color = Color(0xFFEFE6D4),
    /** 次要字：米白 64 %。 */
    val dim: Color = Color(0xFFEFE6D4).copy(alpha = 0.64f),
    /** 弱字：米白 40 %（只用于非必读信息）。 */
    val faint: Color = Color(0xFFEFE6D4).copy(alpha = 0.40f),
    /** 细线：米白 18 %。 */
    val line: Color = Color(0xFFEFE6D4).copy(alpha = 0.18f),
    /** 压在米白实底上的字（按钮、焦点块、衬纸上的黑笔画 logo）。 */
    val onInk: Color = Color(0xFF141109),
    /** 专色：栏目名、编号、进度线、热力点、海报投影。默认 = 兜底朱红。 */
    val spot: Color = SPOT_FALLBACK,
    /** 印章 / 校对勾 / 电视焦点的校对角 / 玻璃导航的选中小圆点：固定朱红，不跟专色。 */
    val seal: Color = Color(0xFFD8432A),
    /** 压在图上的按钮墨底：纸色 52 %。 */
    val inkWell: Color = Color(0xFF100E0B).copy(alpha = 0.52f),
) {
    companion object {
        /** 灰海报 / 取不到色时的专色。 */
        val SPOT_FALLBACK = Color(0xFFEA4E30)

        /** 压在米白焦点块上的朱红（电视：焦点行上的编号、记号、提示）：比 [seal] 深一号，米白底上才读得清。固定色，不跟专色。 */
        val SEAL_ON_INK = Color(0xFFB4381F)
    }
}

/**
 * 三端的网格与间距（docs/design-language.md §3）。手机按 412 × 905 dp、电视按 1920 × 1080 px（= dp，电视密度按 1 设计）给死数；
 * 电脑是「1rem = 窗口宽 / 144，夹在 9.4 – 13」的弹性网格，所以电脑的数由 [desktop] 按窗口宽算。
 */
@Immutable
data class GugaDimens(
    val uiMode: UiMode,
    /** 页边。 */
    val margin: Dp,
    /** 栏间距 / 列距。 */
    val gutter: Dp,
    /** 报头高度。 */
    val mastheadHeight: Dp,
    /** 报头上实线的粗细（电视 2 dp，保证 3 米外看得见）。 */
    val ruleStrong: Dp,
    /** 分隔细线。 */
    val ruleHair: Dp,
    /** 状态栏占位（只有手机有）。 */
    val statusBar: Dp,
    /** 区块之间的竖向间距。 */
    val sectionGap: Dp,
    /** 最小可点 / 可聚焦目标。 */
    val minTarget: Dp,
    /** 电脑的 1 rem（dp）：1440 宽 = 10，随窗口宽在 9.4–13 之间变；手机 / 电视恒为 10（不缩放）。电脑的版式数一律写 `Guga.rem(x)`。 */
    val rem: Dp = 10.dp,
    /** 手机：标签栏离底（`max(18, home 条)`，D-lead-25）。安卓 = 18；iOS 有 home 条时 34。 */
    val bottomBar: Dp = 18.dp,
    /** 手机横屏：控件让出的左右安全区（iOS 的岛那一侧 62；安卓 = 0）。画面本身不让（铺满）。 */
    val sideInset: Dp = 0.dp,
) {
    /** 内容末尾比安卓多留的那一截（标签栏抬高了多少）：安卓 = 0，所以安卓的留白一个不变。 */
    val tabLift: Dp get() = bottomBar - 18.dp

    companion object {
        val Phone = GugaDimens(UiMode.Phone, margin = 24.dp, gutter = 16.dp, mastheadHeight = 44.dp, ruleStrong = 1.dp, ruleHair = 1.dp, statusBar = 46.dp, sectionGap = 36.dp, minTarget = 48.dp)

        /**
         * 手机按安全区算（M4-1，ADR 0005 §2.2）：状态栏占位 = `max(46, 上)`、标签栏离底 = `max(18, 下)`、横屏左右 = `max(左, 右)`。
         * 默认的 [PhoneInsets]（安卓）算出来和 [Phone] 逐字段相同。
         */
        fun phone(insets: PhoneInsets): GugaDimens = Phone.copy(
            statusBar = maxOf(46.dp, insets.top),
            bottomBar = maxOf(18.dp, insets.bottom),
            sideInset = maxOf(insets.left, insets.right),
        )
        val Tv = GugaDimens(UiMode.Tv, margin = 136.dp, gutter = 32.dp, mastheadHeight = 64.dp, ruleStrong = 2.dp, ruleHair = 1.dp, statusBar = 0.dp, sectionGap = 48.dp, minTarget = 64.dp)

        /** 电脑：`rem = clamp(9.4, 窗口宽 / 144, 13)`；页边 1100 → 32、1440 → 49、3168 → 136（两段线性）。 */
        fun desktop(windowWidthDp: Float): GugaDimens {
            val rem = desktopRem(windowWidthDp)
            val margin = when {
                windowWidthDp <= 1440f -> 32f + (windowWidthDp - 1100f).coerceAtLeast(0f) * (49f - 32f) / (1440f - 1100f)
                else -> 49f + (windowWidthDp - 1440f).coerceAtMost(3168f - 1440f) * (136f - 49f) / (3168f - 1440f)
            }
            return GugaDimens(UiMode.Desktop, margin = margin.dp, gutter = (2.4f * rem).dp, mastheadHeight = 44.dp, ruleStrong = 1.dp, ruleHair = 1.dp, statusBar = 0.dp, sectionGap = (5.6f * rem).dp, minTarget = 32.dp, rem = rem.dp)
        }

        fun of(uiMode: UiMode, windowWidthDp: Float, insets: PhoneInsets = PhoneInsets()): GugaDimens = when (uiMode) {
            UiMode.Phone -> phone(insets)
            UiMode.Tv -> Tv
            UiMode.Desktop -> desktop(windowWidthDp)
        }
    }
}

/** 电脑的 rem（dp）：1440 宽 = 10，1100 → 9.4 封底，3168 → 13 封顶。 */
fun desktopRem(windowWidthDp: Float): Float = (windowWidthDp / 144f).coerceIn(9.4f, 13f)

/**
 * 字号阶梯（docs/design-language.md §2）。中文 = 宋体（[GugaFonts.song]），数字和英文小标 = Georgia 斜体（[GugaFonts.numeral]），
 * 字幕 / 弹幕是画面里的字，用黑体，不在这张表里。
 */
@Immutable
data class GugaType(
    /** 期号、评分等大号斜体数字。 */
    val issue: TextUnit,
    /** 人物大名 / 页面大标题。 */
    val display: TextUnit,
    /** 无 logo 的片名、单集名。 */
    val title: TextUnit,
    /** 区块标题「目录」「分集」。 */
    val section: TextUnit,
    /** 正文导语。 */
    val lead: TextUnit,
    /** 导语行高。 */
    val leadLine: TextUnit,
    /** 列表行标题。 */
    val row: TextUnit,
    /** 列表副行、说明。 */
    val caption: TextUnit,
    /** 栏目行（专色、字距 0.3 em）、报头两侧小字。 */
    val kicker: TextUnit,
    /** 最小字：图注、占位说明；只给非必读信息。 */
    val fine: TextUnit,
    /** 报头正中「咕 咕 ◉ 嘎 嘎」。 */
    val masthead: TextUnit,
) {
    fun scaled(k: Float): GugaType = GugaType(issue * k, display * k, title * k, section * k, lead * k, leadLine * k, row * k, caption * k, kicker * k, fine * k, masthead * k)

    companion object {
        val Phone = GugaType(issue = 66.sp, display = 54.sp, title = 34.sp, section = 22.sp, lead = 15.sp, leadLine = 29.sp, row = 17.sp, caption = 12.5.sp, kicker = 10.5.sp, fine = 9.5.sp, masthead = 21.sp)
        val Desktop = GugaType(issue = 96.sp, display = 80.sp, title = 52.sp, section = 26.sp, lead = 17.sp, leadLine = 30.sp, row = 16.5.sp, caption = 12.5.sp, kicker = 11.sp, fine = 10.5.sp, masthead = 21.sp)
        val Tv = GugaType(issue = 150.sp, display = 124.sp, title = 88.sp, section = 38.sp, lead = 30.sp, leadLine = 46.sp, row = 26.sp, caption = 22.sp, kicker = 22.sp, fine = 22.sp, masthead = 34.sp)

        /** [rem]：电脑的 rem（dp）。电脑的字号跟着 rem 走（1440 宽 = 这张表的数，1100 → ×0.94，3168 → ×1.3）；手机 / 电视不缩放。 */
        fun of(uiMode: UiMode, rem: Float = 10f): GugaType = when (uiMode) {
            UiMode.Phone -> Phone
            UiMode.Desktop -> if (rem == 10f) Desktop else Desktop.scaled(rem / 10f)
            UiMode.Tv -> Tv
        }
    }
}

/** 动效时长（docs/design-language.md §8）。弹簧一律用上游的按压物理（`com.gugugaga.ds.physics`），这里只有时间常数。 */
object GugaMotion {
    /** 专色换色。 */
    const val SPOT_MS = 700
    /** 整幅剧照交叉淡入。 */
    const val COVER_FADE_MS = 420
    /** 她说的话恢复原句之前停留的时间。 */
    const val MASCOT_LINE_HOLD_MS = 3200
    /** 顶栏从透明变实心。 */
    const val BAND_SOLID_MS = 250
}

/**
 * 播放器里的字号（页边面板、电脑的顶栏和控制坞）。面板是手机和电脑共用的同一份内容，字号和尺寸从这里取——
 * 之前面板里写死了手机的数（13 sp 一行），电脑按密度 1 出图，2560 × 1440 的窗口里只有十一二个像素（用户 2026-11-15：「字体太小了看不清」）。
 *
 * 电脑：面板的正文 1.7 rem（1440 宽 = 17 sp，和设置页正文 16.5 sp 一档；2560 宽 = 22 sp），顶栏 / 控制坞跟着放大。
 * 电视的播放器有自己的版式（`tv/player/`，10 英尺字号），不用这张表。
 */
@Immutable
data class GugaPlayerType(
    /** 面板标题上面的英文小标、标题。 */
    val panelKicker: TextUnit,
    val panelTitle: TextUnit,
    /** 面板的一行（选集的集名、字幕 / 音轨 / 倍速 / 画质的选项）。 */
    val option: TextUnit,
    /** 一行前面的集号；四位数以上的集号用小一号的。 */
    val number: TextUnit,
    val numberLong: TextUnit,
    /** 一行右端的小字、步进的标签、小节名、说明文字。 */
    val note: TextUnit,
    val label: TextUnit,
    val section: TextUnit,
    val body: TextUnit,
    /** 面板里的尺寸（行高、间距、记号、步进键）相对手机的倍数。 */
    val k: Float,
    /** 电脑顶栏：片名 / 集名、规格那一行（「4K · HDR 10 · …」）。 */
    val headline: TextUnit,
    val spec: TextUnit,
    /** 电脑控制坞：时间、功能词、功能词后面的当前值、快捷键提示。 */
    val clock: TextUnit,
    val dock: TextUnit,
    val dockValue: TextUnit,
    val hint: TextUnit,
) {
    companion object {
        val Phone = GugaPlayerType(
            panelKicker = 10.sp, panelTitle = 24.sp, option = 13.sp, number = 13.sp, numberLong = 10.5.sp, note = 10.5.sp, label = 12.5.sp, section = 10.5.sp, body = 12.5.sp, k = 1f,
            headline = 16.sp, spec = 12.5.sp, clock = 15.sp, dock = 13.5.sp, dockValue = 12.sp, hint = 12.sp,
        )

        /** [rem]：电脑的 rem（dp，1440 宽 = 10）。 */
        fun desktop(rem: Float): GugaPlayerType = GugaPlayerType(
            panelKicker = (1.25f * rem).sp, panelTitle = (3f * rem).sp, option = (1.7f * rem).sp, number = (1.7f * rem).sp, numberLong = (1.4f * rem).sp,
            note = (1.4f * rem).sp, label = (1.6f * rem).sp, section = (1.3f * rem).sp, body = (1.6f * rem).sp, k = 1.3f * rem / 10f,
            headline = (1.9f * rem).sp, spec = (1.55f * rem).sp, clock = (1.75f * rem).sp, dock = (1.6f * rem).sp, dockValue = (1.5f * rem).sp, hint = (1.4f * rem).sp,
        )

        fun of(uiMode: UiMode, rem: Float = 10f): GugaPlayerType = if (uiMode == UiMode.Desktop) desktop(rem) else Phone
    }
}
