package com.gugaart.desktop.mac

import com.gugugaga.app.platform.DpapiKeyValueStore
import com.gugugaga.app.platform.PlainProtector
import com.gugugaga.core.KeyValueStore
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.security.MessageDigest

/*
 * macOS 的令牌存储（任务单 M2-5；decisions.md D-lead-6；release-1.3.md A9）：登录令牌、弹幕服务器地址、TMDB / WebDAV 密钥
 * 这些「安全存储」里的值放进用户的钥匙串（通用密码，服务名 com.gugugaga.art），不再明文躺在数据目录里。
 * JNA 直接调 Security.framework 的 SecItemAdd / CopyMatching / Update / Delete（+ CoreFoundation 造字典），无新依赖、无原生库。
 *
 * 账户名按数据目录分开：`<数据目录绝对路径的 SHA-256 前 12 位>:<键名>`——开发运行的 --data-dir（含验收运行器用的会话目录）和
 * 装好的应用共用一个服务名，不分开就会互相覆盖令牌。
 */

/** Security.framework 的 OSStatus（只列用得到的）。 */
internal object SecStatus {
    const val OK = 0
    const val ITEM_NOT_FOUND = -25300
    const val DUPLICATE_ITEM = -25299
}

/** 钥匙串里「通用密码」的四个调用。真的是 [SecurityFrameworkKeychain]；单测用假的。返回值都是 OSStatus。 */
internal interface KeychainApi {
    fun add(service: String, account: String, data: ByteArray): Int

    /** 状态 + 值（状态不是 [SecStatus.OK] 时值是 null）。 */
    fun copy(service: String, account: String): Pair<Int, ByteArray?>
    fun update(service: String, account: String, data: ByteArray): Int
    fun delete(service: String, account: String): Int
}

internal class KeychainException(val status: Int, op: String) : Exception("keychain $op: OSStatus $status")

/**
 * 钥匙串里的一组键（一个数据目录的）。[get] 读不到 / 被拒都返回 null（= 没有这个值，调用方当作没登录）；
 * [put] / [delete] 失败抛 [KeychainException]（迁移要知道成没成）。日志只写键名和状态码，从不写值。
 */
internal class MacKeychainStore(
    private val api: KeychainApi,
    dataDir: File,
    private val service: String = SERVICE,
    private val log: (String) -> Unit = {},
) {
    /** 这个数据目录的账户名前缀。 */
    val accountPrefix: String = accountPrefixOf(dataDir)

    fun account(key: String): String = "$accountPrefix:$key"

    suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        val (status, data) = runCatching { api.copy(service, account(key)) }.getOrElse { -1 to null }
        when (status) {
            SecStatus.OK -> data?.toString(Charsets.UTF_8)
            SecStatus.ITEM_NOT_FOUND -> null
            else -> {
                log("keychain: read of $key refused (OSStatus $status)")
                null
            }
        }
    }

    suspend fun put(key: String, value: String) = withContext(Dispatchers.IO) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        val a = account(key)
        var status = api.add(service, a, bytes)
        if (status == SecStatus.DUPLICATE_ITEM) status = api.update(service, a, bytes)
        if (status != SecStatus.OK) throw KeychainException(status, "write of $key")
    }

    suspend fun delete(key: String) = withContext(Dispatchers.IO) {
        val status = api.delete(service, account(key))
        if (status != SecStatus.OK && status != SecStatus.ITEM_NOT_FOUND) throw KeychainException(status, "delete of $key")
    }

    companion object {
        /** 钥匙串里的服务名（= 应用的 bundle id）。 */
        const val SERVICE = "com.gugugaga.art"

        /** `<数据目录绝对路径的 SHA-256 前 12 位>`。纯函数（单测）。 */
        fun accountPrefixOf(dataDir: File): String {
            val path = dataDir.absoluteFile.normalize().path
            val d = MessageDigest.getInstance("SHA-256").digest(path.toByteArray(Charsets.UTF_8))
            return d.joinToString("") { "%02x".format(it) }.take(12)
        }
    }
}

/**
 * 交给上游 `PlatformDeps.secure` 的那一个：钥匙串 + 第一次用时把原来的明文存储迁进来。
 *
 * 迁移（D-lead-6）：数据目录里有原来的明文文件（上游 `DpapiKeyValueStore(file)`，Mac 上是 `plain-fallback`）→ 钥匙串里还没有的键
 * 逐个写进去 → 逐个读回比对 → 全部一致才删掉明文文件。钥匙串里已经有的键以钥匙串为准（那是后来写的，或上一次迁移写的）。
 * 出任何错就不删、记日志；这次运行读的时候钥匙串没有的键回退去读明文文件，下次启动再迁。
 *
 * 读不到 / 被拒（例如换了签名的新版本没被允许）= 没有这个值 → 应用当作没登录走登录页，**不崩**；写失败只记日志，不抛。
 * 删除同时删明文里的同名键（否则退出登录后回退会把旧令牌读回来）；写入同时删明文里的同名键（新值作准，旧的不再被读到）。
 */
internal class MacSecureStore(
    private val keychain: MacKeychainStore,
    private val plainFile: File,
    private val log: (String) -> Unit = {},
) : KeyValueStore {
    private val lock = Mutex()
    private var checked = false

    /** 迁移没做完时的明文文件（只读回退 + 删同名键）；null = 没有明文文件了。 */
    private var fallback: DpapiKeyValueStore? = null

    /** `"Keychain"`：设置 › 关于的「令牌存储」照实显示。 */
    val mode: String get() = MODE

    override suspend fun get(key: String): String? = lock.withLock {
        ensureMigrated()
        keychain.get(key) ?: fallback?.let { runCatching { it.get(key) }.getOrNull() }
    }

    override suspend fun set(key: String, value: String) {
        lock.withLock {
            ensureMigrated()
            try {
                keychain.put(key, value)
            } catch (e: KeychainException) {
                log("${e.message}; the value is not kept")
            }
            fallback?.let { runCatching { it.remove(key) } }
        }
    }

    override suspend fun remove(key: String) {
        lock.withLock {
            ensureMigrated()
            try {
                keychain.delete(key)
            } catch (e: KeychainException) {
                log(e.message ?: "keychain delete failed")
            }
            fallback?.let { runCatching { it.remove(key) } }
        }
    }

    /** 测试 / 诊断：迁移做完、明文文件已删。 */
    suspend fun plainFileGone(): Boolean = lock.withLock {
        ensureMigrated()
        !plainFile.exists()
    }

    private suspend fun ensureMigrated() {
        if (checked) return
        checked = true
        if (!plainFile.exists()) return
        val plain = DpapiKeyValueStore(plainFile, PlainProtector)
        val keys = withContext(Dispatchers.IO) { plainKeys(plainFile) }
        if (keys == null) {
            // 不是我们认得的明文文件（坏的、别的模式写的）：不动它，也没有能回退读的东西
            log("keychain migration: ${plainFile.name} is not a plain-fallback store; left alone")
            return
        }
        var failed = 0
        var moved = 0
        for (key in keys) {
            val ok = runCatching {
                if (keychain.get(key) != null) return@runCatching true // 钥匙串里已有：它作准
                val value = plain.get(key) ?: return@runCatching false
                keychain.put(key, value)
                (keychain.get(key) == value).also { if (it) moved++ }
            }.getOrElse { e ->
                log("keychain migration of $key: ${e.message ?: e::class.simpleName}")
                false
            }
            if (!ok) failed++
        }
        if (failed == 0) {
            val deleted = withContext(Dispatchers.IO) { plainFile.delete() }
            if (deleted) {
                log("keychain migration: $moved key(s) moved, plain store deleted")
                return
            }
            log("keychain migration: $moved key(s) moved, but the plain store could not be deleted")
        } else {
            log("keychain migration: $failed of ${keys.size} key(s) failed; plain store kept, retried on the next start")
        }
        fallback = plain
    }

    companion object {
        const val MODE = "Keychain"

        /** 原来那个明文存储的文件名（上游 `DpapiKeyValueStore` 在数据目录里写的那个）。 */
        const val PLAIN_FILE = "gugu-secure-store.json"

        /**
         * 明文存储文件里的键名（不读值）；不是 `plain-fallback` 模式的文件、读不出来 → null。纯函数（单测）。
         */
        fun plainKeys(file: File): List<String>? = runCatching {
            val root = Json.parseToJsonElement(file.readText(Charsets.UTF_8)) as? JsonObject ?: return null
            val mode = (root["mode"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            if (mode != PlainProtector.mode) return null
            (root["entries"] as? JsonObject)?.keys?.toList() ?: emptyList()
        }.getOrNull()
    }
}

/**
 * 真的钥匙串：JNA → Security.framework 的 SecItem*（通用密码，`kSecClassGenericPassword`），字典用 CoreFoundation 造。
 * 默认钥匙串（用户的「登录」钥匙串），不指定访问组（ad-hoc 签名的应用拿不到数据保护钥匙串的权利）。
 * 任何线程都可以调；调用方（[MacKeychainStore]）都在 IO 线程上。
 *
 * 2026-11 起**不在启动路径上**：Mac 的令牌改存数据目录的文件（见 `MacDeps.kt`），ad-hoc 签名每次构建 cdhash 都变，
 * 读旧条目会反复弹「想访问您的钥匙串」。这套代码留着给单测和以后的诊断用；[callCount] 让验收运行器核对「一次都没调过」。
 */
internal object SecurityFrameworkKeychain : KeychainApi {
    /** 真的调过几次 SecItem*（四个操作都经 [query]）。只作诊断计数，不影响行为。 */
    private val secItemCalls = java.util.concurrent.atomic.AtomicInteger()

    /** 到这里为止调过几次钥匙串接口；启动路径应当一直是 0。 */
    val callCount: Int get() = secItemCalls.get()

    private val sec by lazy { NativeLibrary.getInstance("/System/Library/Frameworks/Security.framework/Security") }
    private val cf by lazy { NativeLibrary.getInstance("/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation") }

    private fun constant(lib: NativeLibrary, name: String): Pointer = lib.getGlobalVariableAddress(name).getPointer(0)
    private fun fn(lib: NativeLibrary, name: String): Function = lib.getFunction(name)

    private val kSecClass by lazy { constant(sec, "kSecClass") }
    private val kSecClassGenericPassword by lazy { constant(sec, "kSecClassGenericPassword") }
    private val kSecAttrService by lazy { constant(sec, "kSecAttrService") }
    private val kSecAttrAccount by lazy { constant(sec, "kSecAttrAccount") }
    private val kSecAttrLabel by lazy { constant(sec, "kSecAttrLabel") }
    private val kSecValueData by lazy { constant(sec, "kSecValueData") }
    private val kSecReturnData by lazy { constant(sec, "kSecReturnData") }
    private val kSecMatchLimit by lazy { constant(sec, "kSecMatchLimit") }
    private val kSecMatchLimitOne by lazy { constant(sec, "kSecMatchLimitOne") }
    private val kCFBooleanTrue by lazy { constant(cf, "kCFBooleanTrue") }
    private val keyCallBacks by lazy { cf.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks") }
    private val valueCallBacks by lazy { cf.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks") }

    private const val UTF8 = 0x08000100

    private fun cfString(s: String): Pointer {
        val b = s.toByteArray(Charsets.UTF_8)
        val m = Memory(maxOf(1, b.size).toLong()).apply { write(0, b, 0, b.size) }
        return fn(cf, "CFStringCreateWithBytes").invokePointer(arrayOf<Any?>(null, m, b.size.toLong(), UTF8, 0.toByte()))
            ?: error("CFStringCreateWithBytes")
    }

    private fun cfData(b: ByteArray): Pointer {
        val m = Memory(maxOf(1, b.size).toLong()).apply { write(0, b, 0, b.size) }
        return fn(cf, "CFDataCreate").invokePointer(arrayOf<Any?>(null, m, b.size.toLong())) ?: error("CFDataCreate")
    }

    private fun release(p: Pointer?) {
        if (p != null) fn(cf, "CFRelease").invokeVoid(arrayOf<Any?>(p))
    }

    /** 一个可变字典（键值由字典 retain；调用方造的 CF 对象自己 release），[block] 用完就放。 */
    private fun <T> dict(entries: List<Pair<Pointer, Pointer>>, block: (Pointer) -> T): T {
        val d = fn(cf, "CFDictionaryCreateMutable").invokePointer(arrayOf<Any?>(null, 0L, keyCallBacks, valueCallBacks)) ?: error("CFDictionaryCreateMutable")
        try {
            val set = fn(cf, "CFDictionarySetValue")
            for ((k, v) in entries) set.invokeVoid(arrayOf<Any?>(d, k, v))
            return block(d)
        } finally {
            release(d)
        }
    }

    /** class + service + account（[extra] 追加），字符串用完即放。 */
    private fun <T> query(service: String, account: String, extra: List<Pair<Pointer, Pointer>> = emptyList(), block: (Pointer) -> T): T {
        secItemCalls.incrementAndGet()
        val s = cfString(service)
        val a = cfString(account)
        try {
            return dict(listOf(kSecClass to kSecClassGenericPassword, kSecAttrService to s, kSecAttrAccount to a) + extra, block)
        } finally {
            release(s)
            release(a)
        }
    }

    override fun add(service: String, account: String, data: ByteArray): Int {
        val d = cfData(data)
        val label = cfString("咕咕嘎嘎")
        try {
            return query(service, account, listOf(kSecValueData to d, kSecAttrLabel to label)) { q ->
                fn(sec, "SecItemAdd").invokeInt(arrayOf<Any?>(q, null))
            }
        } finally {
            release(d)
            release(label)
        }
    }

    override fun copy(service: String, account: String): Pair<Int, ByteArray?> =
        query(service, account, listOf(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne)) { q ->
            val out = Memory(8).apply { clear() }
            val status = fn(sec, "SecItemCopyMatching").invokeInt(arrayOf<Any?>(q, out))
            val ref = out.getPointer(0)
            if (status != SecStatus.OK || ref == null) {
                release(ref)
                return@query status to null
            }
            try {
                val n = fn(cf, "CFDataGetLength").invokeLong(arrayOf<Any?>(ref))
                val p = fn(cf, "CFDataGetBytePtr").invokePointer(arrayOf<Any?>(ref))
                status to (if (n <= 0 || p == null) ByteArray(0) else p.getByteArray(0, n.toInt()))
            } finally {
                release(ref)
            }
        }

    override fun update(service: String, account: String, data: ByteArray): Int {
        val d = cfData(data)
        try {
            return query(service, account) { q ->
                dict(listOf(kSecValueData to d)) { attrs -> fn(sec, "SecItemUpdate").invokeInt(arrayOf<Any?>(q, attrs)) }
            }
        } finally {
            release(d)
        }
    }

    override fun delete(service: String, account: String): Int = query(service, account) { q -> fn(sec, "SecItemDelete").invokeInt(arrayOf<Any?>(q)) }
}
