package com.gugaart.ui.data

/**
 * 整幅剧照的取景（docs/design-language.md §6.3，docs/decisions.md Q9）：图裁进版面时以哪一点为中心（0..1，相当于 CSS 的 object-position）。
 *
 * 默认 = **上三分之一居中**（[DEFAULT]）。不做显著性估计。个别图切到脸时，在 [OVERRIDES] 里按条目 id 手动覆盖
 * （id 是 Emby 的条目 id；表是本地的，换服务器后 id 对不上就自然回到默认）。
 */
data class Framing(
    /** 水平焦点：0 = 最左，0.5 = 居中，1 = 最右。 */
    val x: Float,
    /** 垂直焦点：0 = 最上，1/3 = 上三分之一，1 = 最下。 */
    val y: Float,
    /** 图整体下移的比例（剧照顶端就是人脸时，让顶栏落在纸面上）；0 = 不移。 */
    val shiftDown: Float = 0f,
    /** 右缘竖排图注从上往下挪的比例（右边缘有人脸时）；0 = 不挪。 */
    val captionDown: Float = 0f,
) {
    companion object {
        val DEFAULT = Framing(0.5f, 1f / 3f)

        /** 手动覆盖表。键 = 条目 id。加一条要在设备 / 截图上看过。 */
        val OVERRIDES: Map<String, Framing> = mapOf(
            // 网球王子：剧照顶端就是人脸 → 整体下移一行，取景再靠上
            "49118" to Framing(0.5f, 0.2f, shiftDown = 0.06f),
            // 颜心记：右边缘有人脸 → 右缘图注下移到衣服的位置
            "56342" to Framing(0.5f, 0.3f, captionDown = 0.35f),
        )

        fun of(itemId: String?, overrides: Map<String, Framing> = OVERRIDES): Framing = itemId?.let { overrides[it] } ?: DEFAULT
    }

    /**
     * 把 [imageW] × [imageH] 的图按「盖满」裁进 [boxW] × [boxH]：返回 (缩放, 左上角 x, 左上角 y)，单位和入参相同。
     * 焦点尽量落在盒子的同一相对位置，但图不露边。
     */
    fun cover(imageW: Float, imageH: Float, boxW: Float, boxH: Float): Triple<Float, Float, Float> {
        if (imageW <= 0f || imageH <= 0f || boxW <= 0f || boxH <= 0f) return Triple(1f, 0f, 0f)
        val scale = maxOf(boxW / imageW, boxH / imageH)
        val w = imageW * scale
        val h = imageH * scale
        val left = -(w - boxW) * x.coerceIn(0f, 1f)
        val top = (-(h - boxH) * y.coerceIn(0f, 1f) + boxH * shiftDown).coerceAtMost(boxH * shiftDown)
        return Triple(scale, left, top)
    }
}
