package com.gugaart.ios

import com.gugugaga.app.platform.LocalStore
import com.gugugaga.core.KeyValueStore
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSLock
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/*
 * iOS 的两个存储（M4-6，U-ios-7：实现在壳里，上游只给接口）：
 * - 普通设置 = 应用支持目录下的一个 JSON 文件（[FileLocalStore]，同桌面壳的 local.json）；
 * - 令牌 = 钥匙串（[KeychainStore]，服务名 com.gugugaga.art，账户 = 键名，D-lead-6 同一个约定）。
 */

/** `…/Library/Application Support/GugaArt`（没有就建）。 */
internal fun appSupportDir(): String = userDir(NSApplicationSupportDirectory, "GugaArt")

/** `…/Library/Caches/GugaArt`：图片 / 片名 logo / 弹幕的磁盘缓存（系统空间紧时会清，清了就是没缓存）。 */
internal fun cachesDir(): String = userDir(NSCachesDirectory, "GugaArt")

private fun userDir(kind: ULong, name: String): String {
    val base = (NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).firstOrNull() as? String) ?: "/tmp"
    val dir = "$base/$name"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
    return dir
}

@OptIn(ExperimentalForeignApi::class)
internal fun readBytes(path: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    val n = data.length.toInt()
    return if (n == 0) ByteArray(0) else data.bytes!!.reinterpret<UByteVar>().readBytes(n)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData = usePinned { NSData.create(bytes = if (isEmpty()) null else it.addressOf(0), length = size.convert()) }

/** 整个文件原子地换掉（先写临时文件再改名，崩溃不会留下半个文件）。 */
internal fun writeBytes(path: String, bytes: ByteArray): Boolean = bytes.toNSData().writeToFile(path, atomically = true)

/** 一个 `{"键":"值"}` 的 JSON 文件：读进内存，每次改动整份写回。线程安全（一把锁）。 */
internal class JsonFileMap(private val path: String) {
    private val lock = NSLock()
    private val data: MutableMap<String, String> = load()

    private fun load(): MutableMap<String, String> {
        val text = readBytes(path)?.decodeToString() ?: return LinkedHashMap()
        val root = runCatching { Json.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return LinkedHashMap()
        val out = LinkedHashMap<String, String>()
        for ((k, v) in root) if (v is JsonPrimitive && v.isString) out[k] = v.content
        return out
    }

    private fun save() {
        writeBytes(path, buildJsonObject { for ((k, v) in data) put(k, v) }.toString().encodeToByteArray())
    }

    fun get(key: String): String? = locked { data[key] }

    fun set(key: String, value: String) = locked {
        if (data[key] != value) {
            data[key] = value
            save()
        }
    }

    fun remove(key: String) = locked { if (data.remove(key) != null) save() }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}

/** 普通设置（上游的 [LocalStore]）：`Application Support/GugaArt/local.json`。 */
internal class FileLocalStore(path: String) : LocalStore {
    private val map = JsonFileMap(path)
    override fun get(key: String): String? = map.get(key)
    override fun set(key: String, value: String) = map.set(key, value)
    override fun remove(key: String) = map.remove(key)
}

/**
 * 令牌（上游的 [KeyValueStore]）：钥匙串的通用密码项，服务名 [service]、账户 = 键名、首次解锁后可读（后台刷新会话也读得到）。
 * 钥匙串用不了（没签名的构建在模拟器上会拿到「缺少权利」）→ 退回 [fallback] 这个文件，[mode] 写明，关于页照实显示。
 */
@OptIn(ExperimentalForeignApi::class)
internal class KeychainStore(private val service: String, fallbackPath: String) : KeyValueStore {
    private val fallback = JsonFileMap(fallbackPath)
    private val lock = NSLock()

    /** 关于页的「令牌存储」：`Keychain`，或钥匙串用不了时的 `plain-fallback`。 */
    var mode: String = "Keychain"
        private set

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    /** 带服务名 + 账户的查询字典；[block] 往里加别的键。 */
    private fun <T> withQuery(key: String, block: (CFMutableDictionaryRef?) -> T): T {
        val cfService = CFBridgingRetain(service)
        val cfKey = CFBridgingRetain(key)
        val q = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
        try {
            CFDictionaryAddValue(q, kSecClass, kSecClassGenericPassword)
            CFDictionaryAddValue(q, kSecAttrService, cfService)
            CFDictionaryAddValue(q, kSecAttrAccount, cfKey)
            return block(q)
        } finally {
            CFRelease(q)
            CFBridgingRelease(cfService)
            CFBridgingRelease(cfKey)
        }
    }

    private fun usable(status: Int): Boolean = status == errSecSuccess || status == errSecItemNotFound

    private fun fallBack() {
        if (mode != FALLBACK) mode = FALLBACK
    }

    override suspend fun get(key: String): String? = locked {
        if (mode == FALLBACK) return@locked fallback.get(key)
        memScoped {
            withQuery(key) { q ->
                CFDictionaryAddValue(q, kSecReturnData, kCFBooleanTrue)
                CFDictionaryAddValue(q, kSecMatchLimit, kSecMatchLimitOne)
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(q, result.ptr)
                when {
                    status == errSecSuccess -> (CFBridgingRelease(result.value) as? NSData)?.let { d ->
                        val n = d.length.toInt()
                        if (n == 0) "" else d.bytes!!.reinterpret<UByteVar>().readBytes(n).decodeToString()
                    }
                    status == errSecItemNotFound -> null
                    else -> {
                        fallBack()
                        fallback.get(key)
                    }
                }
            }
        }
    }

    override suspend fun set(key: String, value: String) = locked {
        if (mode == FALLBACK) return@locked fallback.set(key, value)
        val ok = withQuery(key) { q ->
            val del = SecItemDelete(q)
            if (!usable(del)) return@withQuery false
            val cfData = CFBridgingRetain(value.encodeToByteArray().toNSData())
            try {
                CFDictionaryAddValue(q, kSecValueData, cfData)
                CFDictionaryAddValue(q, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
                SecItemAdd(q, null) == errSecSuccess
            } finally {
                CFBridgingRelease(cfData)
            }
        }
        if (!ok) {
            fallBack()
            fallback.set(key, value)
        }
    }

    override suspend fun remove(key: String) = locked {
        if (mode == FALLBACK) return@locked fallback.remove(key)
        withQuery(key) { q -> SecItemDelete(q) }
        Unit
    }

    companion object {
        const val FALLBACK = "plain-fallback"
    }
}
