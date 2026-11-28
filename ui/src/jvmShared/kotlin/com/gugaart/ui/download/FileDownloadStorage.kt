package com.gugaart.ui.download

import java.io.File
import java.io.FileOutputStream

/**
 * 离线下载的存储（安卓手机 / 电脑共用的 JVM 实现，1.6 批次 E）：一个目录，文件名只认我们自己起的（[safeFileName]）。
 * 安卓壳给 `filesDir/downloads`（应用内部存储、卸载即清；不经系统 FUSE）。
 */
class FileDownloadStorage(private val dir: File) : DownloadStorage {
    private fun file(name: String): File {
        require(name == INDEX_FILE || name == REPORTS_FILE || safeFileName(name.removeSuffix(".part").removeSuffix(".tmp"))) { "bad download file name" }
        return File(dir, name)
    }

    override fun length(name: String): Long = file(name).let { if (it.isFile) it.length() else -1L }

    override fun open(name: String, append: Boolean): DownloadSink {
        dir.mkdirs()
        val out = FileOutputStream(file(name), append)
        return object : DownloadSink {
            override fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
            override fun close() = out.close()
        }
    }

    override fun rename(from: String, to: String): Boolean {
        val src = file(from)
        val dst = file(to)
        if (!src.isFile) return dst.isFile
        if (dst.exists()) dst.delete()
        return src.renameTo(dst)
    }

    override fun delete(name: String) {
        file(name).delete()
    }

    override fun readText(name: String): String? = file(name).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    override fun writeText(name: String, text: String) {
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        val target = file(name)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    override fun fileUrl(name: String): String = file(name).toURI().toString().replaceFirst(Regex("^file:/(?!/)"), "file:///")

    override fun freeBytes(): Long? = runCatching { (dir.takeIf { it.exists() } ?: dir.parentFile)?.usableSpace }.getOrNull()?.takeIf { it > 0 }
}
