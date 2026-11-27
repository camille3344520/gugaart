package com.gugaart.ui.tv

/**
 * 电视首页翻封面的性能探针（批次 tvflip）：每个开关只关掉一个怀疑项，真电视上一次对比完。
 * 只有测试包（`.perf` / debug）的壳按启动参数（`--es flip3d off` 这类）改它们；正式包永远是默认值 = 现在的外观。
 * 标「修法」的三项（bleedcache / prefetch / govglass）默认就是修好的样子，参数只用来关掉它做对比。
 * 都是普通字段：壳在 `setContent` 之前写好，之后不变，读它们没有重组开销。
 */
object TvFlipProbe {
    /** `flip3d off`：海报流两侧不向后转（只留平移、缩放、压暗）。 */
    var flip3d: Boolean = true

    /** `bleed off|static|full|nograd`：背后整幅图不画 / 不跟着翻 / 要 1920 宽的原图（默认压到 [TV_BLEED_W] = 1280 宽）/ 不画两层暗角渐变。 */
    var bleed: String = "on"

    /** `xfade off`：图换的时候不交叉淡入（背后整幅图、海报、logo 全部直接换）。 */
    var xfade: Boolean = true

    /** `clip off`：海报流里的海报不裁边（3D 变换下的裁剪）。 */
    var clip: Boolean = true

    /** `border off`：海报流里的海报不画 1 px 米白边。 */
    var border: Boolean = true

    /** `dim off`：两侧海报不压暗。 */
    var dim: Boolean = true

    /** `posters off`：海报流里不画海报图（只剩斜纹空版）。 */
    var posters: Boolean = true

    /** `glow off`：海报背后那片专色的晕不画。 */
    var glow: Boolean = true

    /** `gov off`：帧率自适应降级（TvPerfGovernor）不挂。 */
    var governor: Boolean = true

    /** `cardlayer on`：每张海报先平着画进离屏层，再整体做 3D 变换。 */
    var cardLayer: Boolean = false

    /** `odo off`：期号里程表不跟着翻页滚（直接显示吸附到的那一期）。 */
    var odometer: Boolean = true

    /** 候选修法 `odolayer on`：里程表的大字各自进离屏层（外观不变）。 */
    var odoLayer: Boolean = false

    /** 修法（默认开，`prefetch off` 关）：左右邻居的整幅图 / 海报先解码并 `prepareToDraw()`（外观不变）。 */
    var prefetch: Boolean = true

    /** 修法（默认开，`bleedcache off` 关）：背后整幅图和两层暗角先合成一张，每帧只贴一张（外观不变；真电视 GPU 每帧 24 → 10 ms）。 */
    var bleedCache: Boolean = true

    /** 修法（默认关，`govglass on` 恢复旧做法）：降级器照常工作，但降级时不再把玻璃从 ControlledGl 换成 Plates（换后端本身就是卡顿源）。 */
    var govGlass: Boolean = false

    /** `autoflip off`：量的时候封面不自己翻（起点固定，不是性能项）。 */
    var autoFlip: Boolean = true

    /** 启动参数 → 开关。[get] = 按名字取 `--es` 的值（没有 = null）。返回一行摘要给日志。 */
    fun apply(get: (String) -> String?): String {
        fun off(name: String) = get(name)?.lowercase() == "off"
        flip3d = !off("flip3d")
        bleed = get("bleed")?.lowercase() ?: "on"
        xfade = !off("xfade")
        clip = !off("clip")
        border = !off("border")
        dim = !off("dim")
        posters = !off("posters")
        glow = !off("glow")
        governor = !off("gov")
        cardLayer = get("cardlayer")?.lowercase() == "on"
        odometer = !off("odo")
        odoLayer = get("odolayer")?.lowercase() == "on"
        prefetch = !off("prefetch")
        bleedCache = !off("bleedcache")
        autoFlip = !off("autoflip")
        govGlass = get("govglass")?.lowercase() == "on"
        return "govglass=$govGlass autoflip=$autoFlip bleedcache=$bleedCache odolayer=$odoLayer prefetch=$prefetch odo=$odometer flip3d=$flip3d bleed=$bleed xfade=$xfade clip=$clip border=$border dim=$dim posters=$posters glow=$glow gov=$governor cardlayer=$cardLayer"
    }
}
