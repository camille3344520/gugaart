package com.gugaart.ui.data

import com.gugaart.ui.subtitle.KtorSubtitleNet
import com.gugaart.ui.subtitle.NetBytes
import com.gugugaga.app.platform.LocalStore
import com.gugugaga.danmaku.settings.DanmakuFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.ExperimentalResourceApi

/*
 * 字幕和弹幕共用的字体（plan-1.4.1 §3 第 3 条，主理人定）：只用 OFL 的五款——思源黑体、思源宋体、霞鹜文楷、得意黑、站酷快乐体——加「系统字体」。
 * **已经随包的照旧**：思源黑体 / 霞鹜文楷 / 得意黑 是上游 `:danmaku` 的随包子集，思源宋体是画报界面自己的随包子集（GugaFonts）；
 * 字幕内核（mpv / ExoPlayer）要的是磁盘上的文件，所以第一次用到时把**同一份字节**从资源里解到应用的字体目录（和导入的字体同一个目录，
 * FontImportHost.write），弹幕画面直接用资源里的那份——不多带一个字节。**其余用到时再下载**：站酷快乐体，从官方发布处（Google Fonts 的
 * GitHub 仓库，按提交号固定）下，校验大小和 SHA-256，存进同一个目录；国内连不上 GitHub 时换 jsDelivr 的同一提交（逐字节相同，同样校验）。
 * 下载失败 / 校验不对 → 不存，这一次用系统字体，下次用到再试。安装包大小不变。来源与许可证：docs/subtitle-style.md §3。
 */

/** 一款字体在磁盘上要的文件（第一个 = 主文件：ExoPlayer 用它、弹幕用它造 FontFamily）。 */
sealed interface StyleFontFile {
    /** 应用字体目录里的文件名（`style-` 开头，和导入的 `f<哈希>.ttf` 不会撞）。 */
    val fileName: String

    /** 上游 `:danmaku` 的随包资源（`com.gugugaga.danmaku.res.Res` 下的路径）。 */
    class Danmaku(val res: String, override val fileName: String) : StyleFontFile

    /** 画报自己的随包资源（`com.gugaart.ui.res.Res` 下的路径）。 */
    class Guga(val res: String, override val fileName: String) : StyleFontFile

    /** 用到时再下载：[urls] 按顺序试，[size] 和 [sha256] 都对上才存。 */
    class Download(val urls: List<String>, val size: Long, val sha256: String, override val fileName: String) : StyleFontFile
}

/** 站酷快乐体：Google Fonts 官方仓库，钉在这个提交上（改版要连 SHA-256 一起改）。 */
private const val ZCOOL_COMMIT = "a606d6f3181c62b792c8c1463d309c6a8abb8a12"
private const val ZCOOL_PATH = "ofl/zcoolkuaile/ZCOOLKuaiLe-Regular.ttf"

enum class StyleFont(
    val id: String,
    val label: String,
    /** 文件里的家族名（name ID 1）= mpv 的 `sub-font`；null = 系统字体（不换）。 */
    val family: String?,
    /** 上游 `:danmaku` 里对应的那一款（弹幕选它时写上游的键 `danmaku.render.font`）；null = 上游没有，弹幕走开缝（LocalDanmakuFontOverride）。 */
    val danmaku: DanmakuFont?,
    val files: List<StyleFontFile>,
) {
    System("system", "系统字体", null, DanmakuFont.System, emptyList()),
    NotoSansSC(
        "notoSansSC", "思源黑体", "Noto Sans SC Medium", DanmakuFont.NotoSansSC,
        listOf(StyleFontFile.Danmaku("font/danmaku_noto_sans_sc_medium.ttf", "style-noto-sans-sc-medium.ttf")),
    ),
    NotoSerifSC(
        "notoSerifSC", "思源宋体", "Noto Serif SC ExtraLight", null,
        // 界面宋体的 400 / 600 两档（子集是从可变字体取的实例，name 表沿用了原来的「ExtraLight」，字重本身是对的；libass 按字重挑粗体那份）
        listOf(
            StyleFontFile.Guga("files/fonts/NotoSerifSC-400-subset.ttf", "style-noto-serif-sc-400.ttf"),
            StyleFontFile.Guga("files/fonts/NotoSerifSC-600-subset.ttf", "style-noto-serif-sc-600.ttf"),
        ),
    ),
    LxgwWenKai(
        "lxgwWenKai", "霞鹜文楷", "LXGW WenKai Medium", DanmakuFont.LxgwWenKai,
        listOf(StyleFontFile.Danmaku("font/danmaku_lxgw_wenkai.ttf", "style-lxgw-wenkai.ttf")),
    ),
    SmileySans(
        "smileySans", "得意黑", "Smiley Sans Oblique", DanmakuFont.SmileySans,
        listOf(StyleFontFile.Danmaku("font/danmaku_smiley_sans.ttf", "style-smiley-sans.ttf")),
    ),
    ZcoolKuaiLe(
        "zcoolKuaiLe", "站酷快乐体", "ZCOOL KuaiLe", null,
        listOf(
            StyleFontFile.Download(
                listOf(
                    "https://raw.githubusercontent.com/google/fonts/$ZCOOL_COMMIT/$ZCOOL_PATH",
                    "https://cdn.jsdelivr.net/gh/google/fonts@$ZCOOL_COMMIT/$ZCOOL_PATH",
                ),
                size = 1_514_968,
                sha256 = "812a6fc1fe54b6d73a419245c32dfeba8aa33104d5be90d1cf6af082007cb71d",
                fileName = "style-zcool-kuaile.ttf",
            ),
        ),
    );

    /** 要下载的字节数（0 = 随包）。 */
    val downloadBytes: Long get() = files.sumOf { (it as? StyleFontFile.Download)?.size ?: 0L }

    companion object {
        fun of(id: String?): StyleFont? = entries.firstOrNull { it.id == id }

        /** 上游弹幕那一款 → 这张单子里的同一款。 */
        fun of(font: DanmakuFont): StyleFont = entries.first { it.danmaku == font }
    }
}

/** 一款字体的文件现在怎样。 */
sealed interface FontFileState {
    data object Absent : FontFileState
    data object Busy : FontFileState
    /** [paths]：和 [StyleFont.files] 一一对应的绝对路径。 */
    data class Ready(val paths: List<String>) : FontFileState
    data class Failed(val reason: String) : FontFileState
}

/** 「1.5 MB」。 */
fun megabytes(bytes: Long): String {
    val tenths = (bytes * 10 + 524_288) / 1_048_576
    return "${tenths / 10}.${tenths % 10} MB"
}

/** 一款字体的状态写成一句小字（选择器右边那一格）。 */
fun fontNote(font: StyleFont, state: FontFileState?): String? = when {
    font == StyleFont.System -> null
    font.downloadBytes == 0L -> null
    state is FontFileState.Ready -> "已下载"
    state is FontFileState.Busy -> "下载中…"
    state is FontFileState.Failed -> "下载失败，点一下重试"
    else -> "用到时下载 ${megabytes(font.downloadBytes)}"
}

/** 一个路径所在的目录（Windows 的 `\` 和 `/` 都认）。 */
fun parentDir(path: String): String = path.substring(0, maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\')).coerceAtLeast(0))

/**
 * 字体文件仓：把一款字体的文件放进应用的字体目录（随包的从资源解出来，要下载的下载并校验），记住路径（键 [KEY]，上游 LocalStore 里画报自己的键）。
 * 平台没有文件缝（[host] 为 null）→ 什么都放不了，字幕 / 弹幕就用系统字体。全进程一个（[StyleFonts.of]），同一款不会同时下两次。
 */
class StyleFontStore(
    private val local: LocalStore,
    private val host: FontImportHost?,
    private val fetch: suspend (String) -> NetBytes = { url -> fontNet.getBytes(url, emptyMap(), 60_000) },
    private val readResource: suspend (StyleFontFile) -> ByteArray? = ::readBundledFont,
) {
    private val lock = Mutex()
    private val _states = MutableStateFlow(initial())
    val states: StateFlow<Map<StyleFont, FontFileState>> = _states

    /** 能不能放文件（有没有文件缝）。 */
    val available: Boolean get() = host != null

    private fun stored(): Map<String, String> = (GugaPrefs.parseObject(local.get(KEY)) ?: JsonObject(emptyMap()))
        .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { k to it } }.toMap()

    private fun initial(): Map<StyleFont, FontFileState> {
        val known = stored()
        return StyleFont.entries.associateWith { f ->
            val paths = f.files.map { known[it.fileName] }
            if (f.files.isNotEmpty() && host != null && paths.all { it != null && host.exists(it) }) FontFileState.Ready(paths.map { it!! }) else FontFileState.Absent
        }
    }

    fun stateOf(font: StyleFont): FontFileState = _states.value[font] ?: FontFileState.Absent

    private fun mark(font: StyleFont, s: FontFileState) {
        _states.value = _states.value + (font to s)
    }

    /**
     * 确保 [font] 的文件都在磁盘上，返回路径（和 [StyleFont.files] 一一对应）；系统字体 / 放不了 / 失败 → null（失败的原因在 [states]）。
     */
    suspend fun ensure(font: StyleFont): List<String>? {
        val h = host ?: return null
        if (font.files.isEmpty()) return null
        (stateOf(font) as? FontFileState.Ready)?.let { r -> if (r.paths.all { h.exists(it) }) return r.paths }
        return lock.withLock {
            (stateOf(font) as? FontFileState.Ready)?.let { r -> if (r.paths.all { h.exists(it) }) return@withLock r.paths }
            mark(font, FontFileState.Busy)
            try {
                val known = stored().toMutableMap()
                val paths = ArrayList<String>()
                for (file in font.files) {
                    val have = known[file.fileName]?.takeIf { h.exists(it) }
                    if (have != null) {
                        paths += have
                        continue
                    }
                    when (val r = place(h, file)) {
                        is Fail -> {
                            mark(font, FontFileState.Failed(r.reason))
                            return@withLock null
                        }
                        is Placed -> {
                            known[file.fileName] = r.path
                            paths += r.path
                        }
                    }
                }
                local.set(KEY, buildJsonObject { known.forEach { (k, v) -> put(k, v) } }.toString())
                mark(font, FontFileState.Ready(paths))
                paths
            } catch (e: CancellationException) {
                // 叫它的协程被取消（离开了这一页 / 焦点移走）：不能停在「下载中…」——退回「没有」，下次用到再下
                if (stateOf(font) is FontFileState.Busy) mark(font, FontFileState.Absent)
                throw e
            }
        }
    }

    private sealed interface Outcome
    private class Placed(val path: String) : Outcome
    private class Fail(val reason: String) : Outcome

    /** 一个文件：读随包资源 / 下载并校验 → 写进字体目录（都在后台线程）。 */
    private suspend fun place(h: FontImportHost, file: StyleFontFile): Outcome = withContext(Dispatchers.Default) {
        val bytes = when (file) {
            is StyleFontFile.Download -> when (val d = download(file)) {
                is ByteArray -> d
                else -> return@withContext Fail(d as String)
            }
            else -> readResource(file) ?: return@withContext Fail("随包字体读不出来")
        }
        try {
            Placed(h.write(file.fileName, bytes))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Fail("没能存进应用的存储")
        }
    }

    /** 测试用：放一个文件，返回路径（失败 = null）。 */
    internal suspend fun placeFile(file: StyleFontFile): String? = (host?.let { place(it, file) } as? Placed)?.path

    /** 按顺序试每个地址；大小和 SHA-256 都对上才算数（镜像只能原样转发，改不了内容）。返回字节，或失败原因（String）。 */
    private suspend fun download(file: StyleFontFile.Download): Any {
        var reason = "下载失败"
        for (url in file.urls) {
            val got = try {
                fetch(url)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            when {
                got == null || got.status != 200 -> reason = "下载失败"
                got.bytes.size.toLong() != file.size || Sha256.hex(got.bytes) != file.sha256 -> reason = "下载的文件校验不对"
                else -> return got.bytes
            }
        }
        return reason
    }

    companion object {
        const val KEY = "gugaart.styleFonts.v1"
    }
}

/** 下字体用的 HTTP（平台的栈，系统代理照常生效；和字幕搜索同一个实现，各自一个连接池）。 */
private val fontNet by lazy { KtorSubtitleNet() }

/** 随包字体的字节（上游 `:danmaku` 的资源 / 画报自己的资源）。 */
@OptIn(ExperimentalResourceApi::class)
suspend fun readBundledFont(file: StyleFontFile): ByteArray? = try {
    when (file) {
        is StyleFontFile.Danmaku -> com.gugugaga.danmaku.res.Res.readBytes(file.res)
        is StyleFontFile.Guga -> com.gugaart.ui.res.Res.readBytes(file.res)
        is StyleFontFile.Download -> null
    }
} catch (e: CancellationException) {
    throw e
} catch (_: Throwable) {
    null
}

/** 全进程一个字体文件仓（同一个文件缝 = 同一个仓）。 */
object StyleFonts {
    private var cached: Triple<LocalStore, FontImportHost?, StyleFontStore>? = null

    fun of(local: LocalStore, host: FontImportHost?): StyleFontStore {
        cached?.let { (l, h, s) -> if (l === local && h === host) return s }
        return StyleFontStore(local, host).also { cached = Triple(local, host, it) }
    }
}
