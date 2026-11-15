package com.gugaart.ui.screens.login

import com.gugugaga.core.MSG_HOST_REQUIRED
import com.gugugaga.core.Protocol
import com.gugugaga.core.assembleUrl
import com.gugugaga.core.looksLikeDomain
import com.gugugaga.core.parseAddressInput
import com.gugugaga.core.validateAddress

/*
 * 地址框认 URL（用户 2026-11-15，电视登录页：「域名端口支持url识别填入」）。
 *
 * 拆地址全用上游现成的（`core/AddressFields.kt`：`parseAddressInput` 拆协议 / 主机 / 端口 / 子路径、去掉 `#!/home` 和 `/web/index.html`、
 * 去掉 `user:pass@`；`validateAddress` 给中文错误；`assembleUrl` + `defaultPortFor` 补端口），这里只把它们排成「先试哪个、再试哪个」，
 * 并写出框下面那一行「将连接 …」。
 *
 * 为什么不直接把用户打的字交给上游的探测（`parseServerInput`）：没写端口的局域网地址（`192.168.1.10`）它会去试 80 端口，
 * 而 Emby 默认在 8096——上游自己的「添加服务器」表单也是先按字段补好端口再探测的。补端口的规则和上游表单一样：
 * 局域网 / IP 是 8096，域名是 443 / 80（反代）。先试 https 还是 http 和上游 `parseServerInput` 一样：写了协议就只试它；
 * 没写时，端口是 443 / 8920、或者是没写端口的域名 → 先 https，否则先 http。
 */

/** 地址框里的字拆出来的结果。 */
data class AddressPlan(
    /** 按顺序试的完整地址（带协议，非默认端口写出来）；空 = 还不能连（看 [error]）。 */
    val urls: List<String>,
    /** 地址里带的用户名（`reader@host`）：用户名还空着时替用户填上。密码从不拿出来。 */
    val username: String? = null,
    /** 地址里的 `user:pass@` 已经去掉了（离开地址框时框里的字换成 [clean]）。 */
    val strippedCredentials: Boolean = false,
    /** 不能连的原因（主机 / 端口格式不对、不是 http / https）。 */
    val error: String? = null,
    /** 去掉账号密码以后的写法（协议照用户写的，有就留，没有就不加）。 */
    val clean: String = "",
)

/** 只支持 http / https。 */
const val MSG_SCHEME_UNSUPPORTED = "只支持 http:// 和 https:// 开头的地址"

private val SCHEME = Regex("^\\s*([a-z][a-z0-9+.-]*)://", RegexOption.IGNORE_CASE)

/** 拆地址。空 → null（框下面写提示，不写错误）。纯函数，有单测。 */
fun addressPlan(raw: String): AddressPlan? {
    if (raw.isBlank()) return null
    val p = parseAddressInput(raw)
    if (p == null) {
        val scheme = SCHEME.find(raw)?.groupValues?.get(1)?.lowercase()
        return AddressPlan(emptyList(), error = if (scheme != null && scheme != "http" && scheme != "https") MSG_SCHEME_UNSUPPORTED else MSG_HOST_REQUIRED)
    }
    val parts = p.parts
    val clean = (if (p.hadScheme) "${parts.protocol.scheme}://" else "") + parts.host + (if (parts.port.isNotEmpty()) ":${parts.port}" else "") + parts.path
    val errors = validateAddress(parts)
    val error = errors.host ?: errors.port ?: errors.path
    if (error != null) return AddressPlan(emptyList(), p.username, p.strippedCredentials, error, clean)
    val urls = if (p.hadScheme) {
        listOf(assembleUrl(parts))
    } else {
        // parseAddressInput 已经把没写协议的 443 / 8920 端口认成 https
        val httpsFirst = parts.protocol == Protocol.Https || (parts.port.isEmpty() && looksLikeDomain(parts.host))
        val order = if (httpsFirst) listOf(Protocol.Https, Protocol.Http) else listOf(Protocol.Http, Protocol.Https)
        order.map { assembleUrl(parts.copy(protocol = it)) }.distinct()
    }
    return AddressPlan(urls, p.username, p.strippedCredentials, null, clean)
}

/**
 * 地址框下面那一行：「将连接 http://192.168.1.10:8096　连不上再试 https」；出错写原因；空 = ""（界面换成提示）。纯函数，有单测。
 */
fun addressPreview(plan: AddressPlan?): String {
    if (plan == null) return ""
    plan.error?.let { return it }
    val first = plan.urls.firstOrNull() ?: return ""
    val retry = plan.urls.getOrNull(1)?.let { "　连不上再试 ${it.substringBefore("://")}" }.orEmpty()
    val creds = if (plan.strippedCredentials) "　（已去掉地址里的账号密码）" else ""
    return "将连接 $first$retry$creds"
}
