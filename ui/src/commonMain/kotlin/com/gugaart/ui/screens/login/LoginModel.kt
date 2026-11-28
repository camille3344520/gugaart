package com.gugaart.ui.screens.login

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gugugaga.app.session.SessionManager
import com.gugugaga.app.session.SignInInput
import com.gugugaga.app.session.TestExtras
import com.gugugaga.app.session.describeLoginError
import com.gugugaga.app.session.isUnreachable
import com.gugugaga.core.EmbyClient
import com.gugugaga.core.EmbyException
import com.gugugaga.core.EmbyJson
import com.gugugaga.core.ImageUrlOptions
import com.gugugaga.core.KtorTransport
import com.gugugaga.core.NO_RETRY
import com.gugugaga.core.ServerAddress
import com.gugugaga.core.StoredAccount
import com.gugugaga.core.StoredServer
import com.gugugaga.core.Transport
import com.gugugaga.core.UserDto
import com.gugugaga.core.apiBaseOf
import com.gugugaga.core.describeEmbyError
import com.gugugaga.core.identityForServer
import com.gugugaga.core.isSameAddress
import com.gugugaga.core.platformHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

@Immutable
data class PublicUser(
    val id: String,
    val name: String,
    val avatarUrl: String?,
)
/** 边打边找：地址停下这么久没再改就探测（P2-19）。 */
const val PROBE_PAUSE_MS = 600L

/** 登录流程的三步（设计稿 `servers`：列表 → 添加 / 找到 → 登录 / 登录中 / 密码不对）。 */
enum class LoginStep { List, Add, Login }

/** 「协议」分段：自动（让上游的探测自己试 https / http / `/emby`）或者写死。 */
val SCHEMES = listOf("自动", "http", "https")

/** 把用户填的地址和「协议」分段合成交给上游探测的输入。用户自己写了 `http(s)://` 时以他写的为准。纯函数，有单测。 */
fun addressInput(raw: String, scheme: Int): String {
    val t = raw.trim()
    if (t.isEmpty() || scheme == 0 || Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(t)) return t
    return "${SCHEMES[scheme]}://$t"
}

/** 找到服务器之后那一行：「Emby 4.9.1 · 延迟 12 ms」。版本、延迟都来自探测结果；没有的不写。 */
fun foundLine(product: String?, version: String, latencyMs: Long): String =
    listOfNotNull(listOfNotNull(product?.removeSuffix(" Server")?.takeIf { it.isNotBlank() } ?: "Emby", version.takeIf { it.isNotBlank() }?.let(::shortVersion)).joinToString(" "), "延迟 $latencyMs ms").joinToString(" · ")

/** 「4.9.0.0」→「4.9.0」（去掉结尾多余的 .0，至少留两段）。 */
fun shortVersion(v: String): String {
    val parts = v.split('.').toMutableList()
    while (parts.size > 3 && parts.last() == "0") parts.removeAt(parts.lastIndex)
    return parts.joinToString(".")
}

/** 账号方块里印的那个字：用户名的第一个字（字母转大写）。空名 →「？」。 */
fun userGlyph(name: String): String = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "？"

/** 要登录的那台服务器：已保存的（[stored] 不为空）或者刚探测到的。 */
data class LoginTarget(
    val name: String,
    /** 交给上游 `signIn` 的地址（api base）。 */
    val server: String,
    val resolved: ServerAddress?,
    val stored: StoredServer? = null,
) {
    val accounts: List<StoredAccount> get() = stored?.accounts.orEmpty()
}

/**
 * 登录 / 服务器页的状态。**所有会话动作都是上游 `SessionManager` 的**（探测、登录、已保存账号切换、移除、错误文案）；
 * 这里只管这一页显示到哪一步、字段里填了什么。密码只活在 [password] 这一个内存字段里：不写日志、不存盘、登录成功即清空。
 */
@Stable
class LoginModel(
    private val session: SessionManager,
    private val scope: CoroutineScope,
    private val transport: Transport? = null,
    private val lanDiscovery: LanDiscovery? = null,
) {
    var step: LoginStep by mutableStateOf(LoginStep.List)
        private set

    /** 已保存的服务器（null = 还没读出来）。 */
    var servers: List<StoredServer>? by mutableStateOf(null)
        private set

    /** 服务器 id → 延迟（毫秒）；-1 = 连不上；没有 = 还在量。 */
    val latency = mutableStateMapOf<String, Long>()

    /** 列表里正在进的那一台（按了之后、切换完成之前）。 */
    var entering: String? by mutableStateOf(null)
        private set

    // ---- 添加
    var address: String by mutableStateOf("")
    var scheme: Int by mutableStateOf(0)
    var probing: Boolean by mutableStateOf(false)
        private set
    var found: LoginTarget? by mutableStateOf(null)
        private set
    var foundInfo: String? by mutableStateOf(null)
        private set
    var addError: String? by mutableStateOf(null)
        private set
    /** 局域网发现的服务器（只在「添加服务器」显示；iPhone 或没找到时为空）。 */
    var discovered: List<DiscoveredServer> by mutableStateOf(emptyList())
        private set
    var target: LoginTarget? by mutableStateOf(null)
        private set
    var publicUsers: List<PublicUser> by mutableStateOf(emptyList())
        private set
    var username: String by mutableStateOf("")
    var password: String by mutableStateOf("")
    var busy: Boolean by mutableStateOf(false)
        private set
    var error: String? by mutableStateOf(null)
        private set

    /** 这次失败是不是「密码不对」（她换成歪头那句；密码框下划线变红）。 */
    var badPassword: Boolean by mutableStateOf(false)
        private set

    /**
     * 进成功了几次（登录 / 用保存的令牌切换）。没有会话时进成功，根界面自己就换成首页了，用不着它；**已登录时进来换账号**、
     * 选的又正好是现在这个账号，会话不变——这一页靠它知道「换好了，回首页」（`rememberLoginFlow`）。
     */
    var done: Int by mutableStateOf(0)
        private set

    private var job: Job? = null
    private var discoveryJob: Job? = null
    private var publicUsersJob: Job? = null
    private val fallbackTransport: Transport by lazy { transport ?: KtorTransport(platformHttpClient()) }

    /** 读已保存的服务器，并在后台量一下每台的延迟（只读的公开探测，和「添加」用的是同一个）。 */
    fun load() {
        scope.launch {
            val list = session.saved().servers
            servers = list
            for (s in list) {
                if (latency.containsKey(s.id)) continue
                launch {
                    latency[s.id] = try {
                        session.testServer(apiBaseOf(s.address), TestExtras(s.userAgent, s.headers)).latencyMs
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        -1L
                    }
                }
            }
        }
    }

    fun openList() {
        cancel()
        stopDiscovery()
        stopPublicUsers()
        step = LoginStep.List
        error = null
    }

    fun openAdd() {
        cancel()
        stopPublicUsers()
        step = LoginStep.Add
        addError = null
        // 新服务器从空表单开始（电视的一屏表单里用户名 / 密码和地址在一起；密码本来就只该活在这一次输入里）
        username = ""
        password = ""
        error = null
        badPassword = false
        startDiscovery()
    }

    private fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    /** 进「添加服务器」时广播一次 UDP 7359；安卓 / 电视 / Windows / Mac 做，iPhone 返回 null 不跑。 */
    private fun startDiscovery() {
        stopDiscovery()
        discovered = emptyList()
        val d = resolveLanDiscovery(lanDiscovery) ?: return
        discoveryJob = scope.launch {
            try {
                d.discover().collect { srv ->
                    if (discovered.none { it.address == srv.address || (it.id == srv.id && srv.id != srv.address) }) {
                        discovered = discovered + srv
                        if (srv.version.isBlank()) {
                            launch {
                                try {
                                    val r = session.testServer(srv.address)
                                    val v = r.info.version.takeIf { it.isNotBlank() }?.let(::shortVersion).orEmpty()
                                    val n = r.info.serverName.ifBlank { srv.name }
                                    discovered = discovered.map { if (it.address == srv.address) it.copy(name = n, version = v) else it }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** 点一下「这个网络里的服务器」：填好地址，探测后直接到登录。 */
    fun pickDiscovered(s: DiscoveredServer) {
        scheme = 0
        editAddress(s.address)
        connectThenNext()
    }

    fun editAddress(v: String) {
        // 地址改了：上一次（旧地址的）探测作废，结果不能落到新地址上
        if (probing) cancel()
        address = v
        found = null
        foundInfo = null
        addError = null
        nextWhenFound = false
    }

    /** 点了「下一步：登录」但还没找到：找到了直接进登录这一步（P2-19）。 */
    private var nextWhenFound = false

    /**
     * 边打边找（1.5.1 审计 P2-19）：地址停下 [PROBE_PAUSE_MS] 没再改就自己探测一次。界面在地址 / 协议变了时调
     * （`LaunchedEffect(address, scheme)` 里：再改一个字，上一次等待就被取消）。
     */
    suspend fun probeWhenIdle() {
        if (found != null || probing || addressInput(address, scheme).isEmpty()) return
        delay(PROBE_PAUSE_MS)
        connect()
    }

    /** 「下一步：登录」（添加服务器只有这一个按钮，P2-19）：找到了就进登录；还在找 / 还没找 = 找到了直接进。 */
    fun connectThenNext() {
        if (found != null) return next()
        if (addressInput(address, scheme).isEmpty()) return
        nextWhenFound = true
        connect()
    }

    /** 「连接」：上游的探测（协议 / `/emby` 前缀 / 延迟 / 服务器名和版本）。 */
    fun connect() {
        val input = addressInput(address, scheme)
        if (input.isEmpty() || probing) return
        cancel()
        probing = true
        addError = null
        var mine: Job? = null
        mine = scope.launch {
            try {
                val r = session.testServer(input)
                val known = session.saved().servers.firstOrNull { isSameAddress(it.address, r.address) }
                found = LoginTarget(r.info.serverName.ifBlank { "Emby 服务器" }, apiBaseOf(r.address), r.address, known)
                foundInfo = foundLine(r.info.productName, r.info.version, r.latencyMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                addError = describeEmbyError(e)
                nextWhenFound = false
            } finally {
                // 被新的探测顶掉的那一次不改「正在找」
                if (job === mine) probing = false
            }
            if (nextWhenFound && found != null) {
                nextWhenFound = false
                next()
            }
        }
        job = mine
    }

    /** 「下一步：登录」。 */
    fun next() {
        val t = found ?: return
        openLogin(t)
    }

    private fun stopPublicUsers() {
        publicUsersJob?.cancel()
        publicUsersJob = null
        publicUsers = emptyList()
    }

    private fun loadPublicUsers(t: LoginTarget) {
        stopPublicUsers()
        val addr = t.resolved ?: t.server.trim().trimEnd('/').takeIf { it.isNotEmpty() }?.let { ServerAddress(it) } ?: return
        val id = identityForServer(session.identity, t.stored?.userAgent, t.stored?.headers)
        val client = EmbyClient(
            transport = transport ?: fallbackTransport,
            address = addr,
            identity = id,
            session = null,
            timeoutMs = 4000,
            retry = NO_RETRY,
        )
        publicUsersJob = scope.launch {
            try {
                val el = client.getJson("/Users/Public")
                val list = EmbyJson.decodeFromJsonElement(ListSerializer(UserDto.serializer()), el)
                publicUsers = list
                    .filter { it.name.isNotBlank() }
                    .map { u ->
                        val tag = u.primaryImageTag?.takeIf { it.isNotBlank() }
                        val url = if (tag != null) client.userImage(u.id, tag, ImageUrlOptions(maxWidth = 160)) else null
                        PublicUser(id = u.id, name = u.name, avatarUrl = url)
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** 点一下公开用户：填好用户名、清掉密码，焦点交给密码框。 */
    fun pickPublicUser(u: PublicUser) {
        username = u.name
        password = ""
        error = null
        badPassword = false
    }

    /** 进登录这一步。已保存的账号排成方块；只有一个时把用户名填好。 */
    fun openLogin(t: LoginTarget, user: String? = null, note: String? = null) {
        cancel()
        stopDiscovery()
        target = t
        username = user ?: t.accounts.maxByOrNull { it.lastLoginAt }?.userName.orEmpty()
        password = ""
        error = note
        badPassword = false
        step = LoginStep.Login
        loadPublicUsers(t)
    }

    fun targetOf(s: StoredServer) = LoginTarget(s.remark?.takeIf { it.isNotBlank() } ?: s.name, apiBaseOf(s.address), s.address, s)

    /**
     * 点列表里的一台：只有一个已保存账号 → 直接用保存的令牌进（上游 `switchAccount` 会先验证）；令牌失效 → 进登录这一步，说清楚要重新输密码；
     * 连不上 → 留在列表，这一行标「连不上」。有多个账号或者没有账号 → 进登录这一步自己挑。
     */
    fun enter(s: StoredServer) {
        if (entering != null) return
        val only = s.accounts.singleOrNull()
        if (only == null) {
            openLogin(targetOf(s))
            return
        }
        switchTo(s, only)
    }

    /** 用已保存的令牌切到一个账号（登录这一步里点账号方块也是它）。 */
    fun switchTo(s: StoredServer, a: StoredAccount) {
        if (busy || entering != null) return
        cancel()
        entering = s.id
        if (step == LoginStep.Login) {
            busy = true
            username = a.userName
            error = null
            badPassword = false
        }
        job = scope.launch {
            try {
                session.switchAccount(s.id, a.userId)
                // 成功：会话已经换了，根界面马上把这一页换掉。这里**不**把「正在进……」改回去——同一帧里既重排这一行、又拆掉这一页，
                // 会撞上 Compose 1.12 的 RectList 问题（docs/impl-status.md §6）；这一页反正要走了。
                done++
                return@launch
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is EmbyException && e.kind == EmbyException.Kind.Unauthorized) {
                    openLogin(targetOf(s), a.userName, "「${a.userName}」的登录已失效")
                } else if (isUnreachable(e)) {
                    latency[s.id] = -1L
                    if (step == LoginStep.Login) error = describeEmbyError(e)
                } else {
                    openLogin(targetOf(s), a.userName, describeEmbyError(e))
                }
            }
            entering = null
            busy = false
        }
    }

    /** 「登录」：上游 `signIn`（主地址 + 备用线路、保存账号、会话生效）。成功后根界面自己换页；这里清掉密码。 */
    fun signIn() {
        val t = target ?: return
        if (busy || username.isBlank()) return
        cancel()
        startSignIn(t)
    }

    /**
     * 电视的一屏表单（用户 2026-11-15：「一个界面可以输入域名端口、账号密码」）：地址、用户名、密码一起交。
     * - 登录这一步（已保存的服务器）或者刚才已经找到了这台服务器（地址没改过）→ 直接登录；
     * - 否则按 [urls] 的顺序探测（[addressPlan] 拆出来的完整地址，每个都带协议和端口），第一个通的就接着登录；都不通 → [addError]。
     * 用户名空着不交（上游的提示「请填写用户名」）。
     */
    fun submitForm(urls: List<String>) {
        if (busy || probing) return
        if (username.isBlank()) {
            error = com.gugugaga.core.MSG_USER_REQUIRED
            badPassword = false
            return
        }
        val ready = if (step == LoginStep.Login) target else found
        if (ready != null) {
            cancel()
            target = ready
            startSignIn(ready)
            return
        }
        if (urls.isEmpty()) return
        cancel()
        probing = true
        addError = null
        error = null
        badPassword = false
        job = scope.launch {
            var last: Exception? = null
            for (u in urls) {
                try {
                    val r = session.testServer(u)
                    val known = session.saved().servers.firstOrNull { isSameAddress(it.address, r.address) }
                    val t = LoginTarget(r.info.serverName.ifBlank { "Emby 服务器" }, apiBaseOf(r.address), r.address, known)
                    found = t
                    foundInfo = foundLine(r.info.productName, r.info.version, r.latencyMs)
                    target = t
                    probing = false
                    startSignIn(t)
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    last = e
                }
            }
            addError = last?.let(::describeEmbyError)
            probing = false
        }
    }

    private fun startSignIn(t: LoginTarget) {
        busy = true
        error = null
        badPassword = false
        val name = username.trim()
        val secret = password
        job = scope.launch {
            try {
                session.signIn(SignInInput(server = t.server, username = name, password = secret, userAgent = t.stored?.userAgent, headers = t.stored?.headers, resolved = t.resolved))
                password = ""
                done++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                badPassword = e is EmbyException && e.kind == EmbyException.Kind.Unauthorized
                error = describeLoginError(e)
            } finally {
                busy = false
            }
        }
    }

    /** 从这台设备上移除一台服务器（连同它的账号）。 */
    fun forget(s: StoredServer) {
        scope.launch {
            try {
                session.forgetServer(s.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            latency.remove(s.id)
            load()
        }
    }

    private fun cancel() {
        job?.cancel()
        job = null
        probing = false
        busy = false
        entering = null
    }
}
