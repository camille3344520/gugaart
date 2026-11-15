package com.gugaart.ui.mascot

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.gugaart.ui.res.Res
import com.gugaart.ui.res.pose_g01
import com.gugaart.ui.res.pose_g01b
import com.gugaart.ui.res.pose_g03
import com.gugaart.ui.res.pose_g04
import com.gugaart.ui.res.pose_g07
import com.gugaart.ui.res.pose_g08
import com.gugaart.ui.res.pose_g09
import com.gugaart.ui.res.pose_g10
import com.gugaart.ui.res.pose_g11
import com.gugaart.ui.res.pose_g13
import com.gugaart.ui.res.pose_g15
import com.gugaart.ui.res.pose_g15b
import com.gugaart.ui.res.pose_g17
import com.gugaart.ui.res.pose_g19
import com.gugaart.ui.res.pose_g21
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * 她的姿势图（docs/mascot.md §5 的生图清单，编号 G01–G21）。已经生成的在 `composeResources/drawable/pose_gNN.png`
 * （512 × 512 透明 PNG；2026-11-15 起是 3D 手办质感的第三版，来源 `design/generated/v3/`，由 `scripts/art/export-poses.py` 导出）；
 * 还没生成的编号退回轮廓占位（[PosePlaceholder]）。G01 / G01b / G03 是圆形署名照和报头徽记（不透明方图，界面裁成圆）。
 *
 * 她的硬规则（docs/mascot.md §2）由调用方保证：只放在留给她的版面格里、不叠在内容上、**正在播放的画面上不出现**。
 * 设置里把她关掉（`showMascot = false`）时这里什么都不画，格子由调用方收掉。
 */
object Poses {
    /** 编号 → 资源。新图到了在这里加一行。 */
    private val table: Map<String, DrawableResource> = mapOf(
        "G01" to Res.drawable.pose_g01,
        "G01B" to Res.drawable.pose_g01b,
        "G03" to Res.drawable.pose_g03,
        "G04" to Res.drawable.pose_g04,
        "G07" to Res.drawable.pose_g07,
        "G08" to Res.drawable.pose_g08,
        "G09" to Res.drawable.pose_g09,
        "G10" to Res.drawable.pose_g10,
        "G11" to Res.drawable.pose_g11,
        "G13" to Res.drawable.pose_g13,
        "G15" to Res.drawable.pose_g15,
        "G15B" to Res.drawable.pose_g15b,
        "G17" to Res.drawable.pose_g17,
        "G19" to Res.drawable.pose_g19,
        "G21" to Res.drawable.pose_g21,
    )

    fun of(code: String): DrawableResource? = table[code.uppercase()]

    val generated: Set<String> get() = table.keys

    /**
     * 读屏念的那句。动作名是图片的编号（「摆稿纸」「看印花稿纸」这些画报时期的叫法），不改；念出来换成看片的说法
     * （用户 2026-11-15：措辞改成观影风格）。
     */
    fun spoken(pose: String): String = when (pose) {
        "摇印刷机" -> "在换片"
        "摆稿纸" -> "在排片"
        "看印花稿纸" -> "看着卡住的胶片"
        "捧白纸" -> "捧着空片单"
        "举笔" -> "举着笔帮你找"
        "号外" -> "举着新版通知"
        else -> pose
    }

    /** 动作名（界面里历史上用的叫法）→ 编号。没有对应的编号 → null（一直是占位）。 */
    fun codeOf(pose: String): String? = when (pose) {
        "摇印刷机" -> "G04"
        "摆稿纸" -> "G07"
        "摊手" -> "G08"
        "举笔" -> "G09"
        "捧白纸" -> "G10"
        "打盹" -> "G11"
        "鼓掌" -> "G13"
        "递钥匙" -> "G15"
        "拿钥匙歪头" -> "G15B"
        "号外" -> "G17"
        "拔掉的电源插头" -> "G19"
        "看印花稿纸" -> "G21"
        else -> null
    }
}

/**
 * 画一张姿势图。[code] = "G07" 这样的编号；[label] = 动作名（读屏念它；没有图时也是占位下面那行小字）。
 */
@Composable
fun MascotPose(code: String, size: Dp, modifier: Modifier = Modifier, label: String = "") {
    if (!LocalGugaPrefs.current.showMascot) return
    val res = Poses.of(code)
    if (res == null) {
        PosePlaceholder(label.ifEmpty { code }, modifier, size)
        return
    }
    Image(
        painterResource(res),
        contentDescription = null,
        modifier = modifier.size(size).testTag("mascot-pose-$code").semantics { if (label.isNotEmpty()) contentDescription = "咕咕嘎嘎 · ${Poses.spoken(label)}" },
        contentScale = ContentScale.Fit,
    )
}

/** 按动作名画：有编号且已生成 → 真图；否则轮廓占位。状态视图用这个（它们只知道动作名）。 */
@Composable
fun MascotPoseNamed(pose: String, size: Dp, modifier: Modifier = Modifier) {
    val code = Poses.codeOf(pose)
    if (code != null) MascotPose(code, size, modifier, pose) else PosePlaceholder(pose, modifier, size)
}
