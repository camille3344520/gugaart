package com.gugaart.ios

import com.gugaart.ui.backup.BackupFileHost
import com.gugaart.ui.data.FontImportHost
import com.gugaart.ui.data.ImportedFontStore
import com.gugaart.ui.data.PickedFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeFont
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject
import kotlin.coroutines.resume

/*
 * iOS 的文件选择器（M4-8，对齐清单 T07、D08）：配置备份的导出 / 还原、导入弹幕字体。都是系统的「文件」选择器（UIDocumentPickerViewController），
 * 选中的文件按副本交过来（asCopy），读完就不再碰原文件，不用安全作用域。用户取消 = false / null（和桌面一样）。
 */

/** 导出时临时文件的名字：给的名字不是 .json 就补上；斜杠换掉（只是一个文件名）。纯函数，有单测。 */
internal fun exportFileName(suggested: String): String {
    val base = suggested.trim().replace('/', '-').replace(':', '-').ifEmpty { "gugaart-backup" }
    return if (base.endsWith(".json", ignoreCase = true)) base else "$base.json"
}

/** 导入的字体存在哪：`Application Support/GugaArt/fonts/`。 */
internal fun fontsDir(): String = "${appSupportDir()}/fonts".also { NSFileManager.defaultManager.createDirectoryAtPath(it, true, null, null) }

/** 内嵌字幕的 CJK 字体目录（只 iOS）：把随包的 Noto Serif SC（OFL，GB2312 全覆盖）从 compose 资源解到
 *  `Application Support/GugaArt/sub-fonts`，交给 mpv `sub-fonts-dir`。模拟器的系统中文字体在 cryptexd 挂载路径里、
 *  沙箱的 freetype 打不开（libass coretext 把 PingFang SC 解析成 Helvetica → 中文方框），随包字体是读得到的 CJK 来源。
 *  解过一次就复用（按文件存在判断）。macOS / Windows / 安卓不走这里。 */
internal fun subFontsDir(): String {
    val dir = "${appSupportDir()}/sub-fonts"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    val dest = "$dir/NotoSerifSC-400-subset.ttf"
    if (!NSFileManager.defaultManager.fileExistsAtPath(dest)) {
        val src = "${NSBundle.mainBundle.bundlePath}/compose-resources/composeResources/com.gugaart.ui.res/files/fonts/NotoSerifSC-400-subset.ttf"
        readBytes(src)?.let { writeBytes(dest, it) }
    }
    return dir
}

/** [path] 是不是 [dir] 里的一个文件（删字体只删自己目录里的）。纯函数，有单测。 */
internal fun isInside(dir: String, path: String): Boolean {
    val d = dir.trimEnd('/') + "/"
    return path.startsWith(d) && !path.removePrefix(d).contains('/') && path.length > d.length
}

private fun topController(): UIViewController? {
    @Suppress("DEPRECATION")
    var vc = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (vc?.presentedViewController != null) vc = vc.presentedViewController
    return vc
}

/** 弹一个选择器，等用户选好（选中的地址）或取消（null）。代理在弹着的时候被这里留着。 */
private suspend fun present(make: () -> UIViewController): List<NSURL>? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { cont ->
        val picker = make() as UIDocumentPickerViewController
        val delegate = PickerDelegate { urls -> if (cont.isActive) cont.resume(urls) }
        picker.delegate = delegate
        holder = delegate
        val top = topController()
        if (top == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        top.presentViewController(picker, animated = true, completion = null)
        cont.invokeOnCancellation { picker.dismissViewControllerAnimated(true, null) }
    }
}

/** 弹着的那个选择器的代理（UIKit 只弱引用它）。 */
private var holder: PickerDelegate? = null

private class PickerDelegate(private val done: (List<NSURL>?) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        holder = null
        done(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        holder = null
        done(null)
    }
}

private fun sizeOf(path: String): Long =
    (NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L

/** 配置备份：导出 = 先写到临时目录，再让用户在「文件」里选存哪；还原 = 选一份 .json 读出来。 */
internal fun iosBackupFileHost(): BackupFileHost = BackupFileHost(
    save = { name, text ->
        val path = NSTemporaryDirectory().trimEnd('/') + "/" + exportFileName(name)
        if (!writeBytes(path, text.encodeToByteArray())) {
            false
        } else {
            val picked = present { UIDocumentPickerViewController(forExportingURLs = listOf(NSURL.fileURLWithPath(path)), asCopy = true) }
            NSFileManager.defaultManager.removeItemAtPath(path, null)
            picked != null
        }
    },
    open = {
        val url = present { UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON), asCopy = true) }?.firstOrNull()
        val path = url?.path
        if (path == null) {
            null
        } else {
            // 备份文件很小；太大的不是备份
            val text = if (sizeOf(path) > 4L * 1024 * 1024) "" else readBytes(path)?.decodeToString().orEmpty()
            NSFileManager.defaultManager.removeItemAtPath(path, null)
            text
        }
    },
)

/** 导入弹幕字体：选一个 ttf / otf（系统的字体类型），字节交给界面那一半校验；存进 [fontsDir]（选中的副本过后就会被系统清掉）。 */
internal fun iosFontImportHost(): FontImportHost {
    val dir = fontsDir()
    return FontImportHost(
        pick = {
            val url = present { UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFont), asCopy = true) }?.firstOrNull()
            val path = url?.path
            if (path == null) {
                null
            } else {
                val name = url.lastPathComponent ?: "font.ttf"
                val picked = if (sizeOf(path) > ImportedFontStore.MAX_BYTES) PickedFont(name, ByteArray(0), tooLarge = true) else PickedFont(name, readBytes(path) ?: ByteArray(0))
                NSFileManager.defaultManager.removeItemAtPath(path, null)
                picked
            }
        },
        write = { name, bytes ->
            val path = "$dir/${name.replace('/', '-')}"
            writeBytes(path, bytes)
            path
        },
        delete = { path -> if (isInside(dir, path)) NSFileManager.defaultManager.removeItemAtPath(path, null) },
        exists = { path -> NSFileManager.defaultManager.fileExistsAtPath(path) },
    )
}
