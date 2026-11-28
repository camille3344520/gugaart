package com.gugaart.ui.download

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSURLVolumeAvailableCapacityForImportantUsageKey
import platform.Foundation.dataWithContentsOfFile
import platform.posix.FILE
import platform.posix.S_IFMT
import platform.posix.S_IFREG
import platform.posix.errno
import platform.posix.fclose
import platform.posix.fflush
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.lstat
import platform.posix.stat
import platform.posix.unlink

/**
 * Offline downloads on iPhone (1.6 batch F3): one folder, the shell passes `Application Support/GugaArt/downloads` - removed with the
 * app, never purged like Caches, and marked `isExcludedFromBackup` (its files stay out of iCloud / computer backups). Same behaviour as
 * the JVM `FileDownloadStorage`: only our own file names ([safeFileName] / [INDEX_FILE] / [REPORTS_FILE]); POSIX I/O, `lstat` for
 * lengths and `unlink` for deletes, so a symbolic link is never followed.
 */
@OptIn(ExperimentalForeignApi::class)
class IosDownloadStorage(private val dir: String) : DownloadStorage {
    init {
        ensureDir()
    }

    private fun path(name: String): String {
        require(name == INDEX_FILE || name == REPORTS_FILE || safeFileName(name.removeSuffix(".part").removeSuffix(".tmp"))) { "bad download file name" }
        return "$dir/$name"
    }

    /** The folder exists (re-created if it was removed) and is excluded from backups. */
    private fun ensureDir() {
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        NSURL.fileURLWithPath(dir, isDirectory = true).setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
    }

    /** Size of a regular file; missing or not a regular file (folder, symbolic link) = -1. */
    private fun sizeOf(p: String): Long = memScoped {
        val st = alloc<stat>()
        if (lstat(p, st.ptr) != 0) return -1L
        if ((st.st_mode.toInt() and S_IFMT) != S_IFREG) -1L else st.st_size
    }

    override fun length(name: String): Long = sizeOf(path(name))

    override fun open(name: String, append: Boolean): DownloadSink {
        ensureDir()
        val f = fopen(path(name), if (append) "ab" else "wb") ?: error("open failed (errno $errno)")
        return FileSink(f)
    }

    private class FileSink(private var f: CPointer<FILE>?) : DownloadSink {
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            if (length <= 0) return
            val file = f ?: error("closed")
            val n = bytes.usePinned { fwrite(it.addressOf(offset), 1.convert(), length.convert(), file) }
            if (n.toLong() != length.toLong()) error("write failed (errno $errno)")
        }

        override fun close() {
            val file = f ?: return
            f = null
            fclose(file)
        }
    }

    override fun rename(from: String, to: String): Boolean {
        val src = path(from)
        val dst = path(to)
        if (sizeOf(src) < 0) return sizeOf(dst) >= 0
        // rename(2) replaces an existing target atomically
        return platform.posix.rename(src, dst) == 0
    }

    override fun delete(name: String) {
        unlink(path(name))
    }

    override fun readText(name: String): String? {
        val p = path(name)
        if (sizeOf(p) < 0) return null
        val data = NSData.dataWithContentsOfFile(p) ?: return null
        val n = data.length.toInt()
        return if (n == 0) "" else data.bytes!!.reinterpret<ByteVar>().readBytes(n).decodeToString()
    }

    override fun writeText(name: String, text: String) {
        ensureDir()
        val target = path(name)
        val tmp = "$target.tmp"
        val bytes = text.encodeToByteArray()
        val f = fopen(tmp, "wb") ?: error("open failed (errno $errno)")
        val n = if (bytes.isEmpty()) 0uL else bytes.usePinned { fwrite(it.addressOf(0), 1.convert(), bytes.size.convert(), f) }
        fflush(f)
        fclose(f)
        if (n.toLong() != bytes.size.toLong()) {
            unlink(tmp)
            error("write failed")
        }
        if (platform.posix.rename(tmp, target) != 0) {
            unlink(target)
            if (platform.posix.rename(tmp, target) != 0) error("rename failed (errno $errno)")
        }
    }

    /** `file:///…/Application%20Support/GugaArt/downloads/<name>` (URL-escaped; mpv unescapes it itself). */
    override fun fileUrl(name: String): String {
        val p = path(name)
        return NSURL.fileURLWithPath(p).absoluteString ?: "file://$p"
    }

    override fun freeBytes(): Long? {
        val url = NSURL.fileURLWithPath(dir, isDirectory = true)
        val important = runCatching {
            (url.resourceValuesForKeys(listOf(NSURLVolumeAvailableCapacityForImportantUsageKey), null)?.get(NSURLVolumeAvailableCapacityForImportantUsageKey) as? NSNumber)?.longLongValue
        }.getOrNull()
        if (important != null && important > 0) return important
        val plain = (NSFileManager.defaultManager.attributesOfFileSystemForPath(dir, null)?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue
        return plain?.takeIf { it > 0 }
    }

    /** Tests: is the folder excluded from backups. */
    internal fun excludedFromBackup(): Boolean {
        val v = NSURL.fileURLWithPath(dir, isDirectory = true).resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), null)?.get(NSURLIsExcludedFromBackupKey)
        return (v as? NSNumber)?.boolValue ?: (v as? Boolean) ?: false
    }
}
