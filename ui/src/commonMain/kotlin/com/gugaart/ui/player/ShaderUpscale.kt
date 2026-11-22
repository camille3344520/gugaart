package com.gugaart.ui.player

import com.gugaart.ui.data.FontImportHost
import com.gugaart.ui.data.fontId
import com.gugaart.ui.res.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * 画质面板的「着色器增强」（Anime4K / FSR）。
 *
 * 预设链照 Anime4K 官方 v4.0.1 `GLSL_Instructions.md` 的 **HQ** 档（顺序即下发顺序）；FSR 是 AMD FSR v1.0.2 的 mpv 移植（单文件）。
 * 原件在 `third_party/shaders/`，构建时合进资源 `files/shaders/`（[SHADER_RES_DIR]）。选择记在画报自己的键 `gugaart.settings.v1`
 * （[com.gugaart.ui.data.GugaPrefs.shader]），下次开播沿用；应用时机在 [RememberShaderApply]。
 *
 * **只在 mpv 系内核出现**：引擎要有 [com.gugugaga.app.player.PlayerEngine.shaders]（ExoPlayer / VLC 没有），平台要能把字节写成
 * 缓存文件（[FontImportHost]，电视 / 没接文件缝的壳为 null）——两者缺一就不画入口（[shaderEntryVisible]）。
 */
enum class ShaderPreset(val id: String, val label: String, val note: String?, val files: List<String>) {
    Off("off", "关", null, emptyList()),
    Anime4kA(
        "anime4k-a", "Anime4K 模式 A", "较吃显卡",
        listOf(
            "Anime4K/Anime4K_Clamp_Highlights.glsl",
            "Anime4K/Anime4K_Restore_CNN_VL.glsl",
            "Anime4K/Anime4K_Upscale_CNN_x2_VL.glsl",
            "Anime4K/Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K/Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K/Anime4K_Upscale_CNN_x2_M.glsl",
        ),
    ),
    Anime4kB(
        "anime4k-b", "Anime4K 模式 B", "较吃显卡",
        listOf(
            "Anime4K/Anime4K_Clamp_Highlights.glsl",
            "Anime4K/Anime4K_Restore_CNN_Soft_VL.glsl",
            "Anime4K/Anime4K_Upscale_CNN_x2_VL.glsl",
            "Anime4K/Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K/Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K/Anime4K_Upscale_CNN_x2_M.glsl",
        ),
    ),
    Anime4kC(
        "anime4k-c", "Anime4K 模式 C", "较吃显卡",
        listOf(
            "Anime4K/Anime4K_Clamp_Highlights.glsl",
            "Anime4K/Anime4K_Upscale_Denoise_CNN_x2_VL.glsl",
            "Anime4K/Anime4K_AutoDownscalePre_x2.glsl",
            "Anime4K/Anime4K_AutoDownscalePre_x4.glsl",
            "Anime4K/Anime4K_Upscale_CNN_x2_M.glsl",
        ),
    ),
    Fsr("fsr", "FSR", null, listOf("FSR/FSR.glsl"));

    companion object {
        fun of(id: String?): ShaderPreset = entries.firstOrNull { it.id == id } ?: Off
    }
}

/** 资源目录（`gugaResources` 把 `third_party/shaders` 合进 `files/shaders/`，照 fonts 先例）。 */
const val SHADER_RES_DIR = "files/shaders"

/** 入口画不画：引擎有着色器链（mpv 系）**且**平台能把字节写成文件。纯函数，有单测。 */
fun shaderEntryVisible(engineHasShaders: Boolean, canWriteFiles: Boolean): Boolean = engineHasShaders && canWriteFiles

/** 缓存文件名：`shader-` 前缀 + 内容哈希（FNV-1a，复用导入字体那一个）+ `.glsl`。同内容同名 → 不重复写。 */
fun shaderFileName(bytes: ByteArray): String = "shader-" + fontId(bytes) + ".glsl"

/** 进程内记着的「文件名 → 绝对路径」：同内容的着色器只写一次；被系统清掉了（exists 不认）就再写一份。 */
private val shaderCache = mutableMapOf<String, String>()

/**
 * 把预设的着色器从资源读出来、写成应用缓存文件，返回**绝对路径**链（顺序 = 预设顺序，mpv 要真实文件路径）。
 * [host] 为 null（无能力）或预设为关 → 空链（= 清除）。任何一个文件读 / 写失败 → 整条链放弃（返回空链），不下发半条链。
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun extractShaders(preset: ShaderPreset, host: FontImportHost?): List<String> {
    if (preset == ShaderPreset.Off || host == null) return emptyList()
    val out = ArrayList<String>(preset.files.size)
    for (rel in preset.files) {
        val path = try {
            val bytes = Res.readBytes("$SHADER_RES_DIR/$rel")
            val name = shaderFileName(bytes)
            val cached = shaderCache[name]
            if (cached != null && host.exists(cached)) cached
            else host.write(name, bytes).also { shaderCache[name] = it }
        } catch (_: Exception) {
            return emptyList()
        }
        out += path
    }
    return out
}
