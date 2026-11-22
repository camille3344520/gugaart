package com.gugaart.ui.update

import com.gugugaga.core.Transport
import com.gugugaga.core.TransportRequest
import com.gugugaga.core.TransportResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/*
 * 更新网络层：国内备用镜像 + 「超时不算取消」（2026-11-22 海信电视「点检查更新没反应」的修复）。
 *
 * 两个问题，都在这里解决，不改上游（docs/upstream-sync.md §2）：
 *  1. 卡死：上游 UpdateChecker.check 用 withTimeout(8s) 包清单请求，但它的 catch 先接 CancellationException 并重抛；
 *     withTimeout 超时抛的 TimeoutCancellationException 正是 CancellationException 的子类，于是超时被当成「调用方取消」
 *     一路重抛（UpdateController.runCheck  likewise），状态永久停在 Checking，之后每次「检查更新」都被 runCheck 的
 *     Checking 早退挡掉 = 点了没反应。这里给更新检查一个自己的传输层（经上游公开缝 PlatformDeps.updateTransport）：
 *     内层超时（比上游 8s 短）先触发，并把 TimeoutCancellationException 换成普通异常 [UpdateAttemptTimeout] 抛出去，
 *     上游的 catch (_: Exception) 就会落成 UpdateCheck.Failed（「连不上更新服务器」）——状态走到 Failed、关于页写明原因、
 *     手动「检查更新」可重试，不再卡死。真正的调用方取消（非超时的 CancellationException）原样透传，不吞。
 *  2. 可达性：raw.githubusercontent.com / github.com/releases 在国内电视上常连不上。给清单和安装包配国内备用镜像
 *     （[alternates]），主地址失败/挂起时自动换镜像。清单有 Ed25519 验签、安装包有 SHA-256 校验（都在上游），
 *     镜像只能serve原文、改不了内容，所以备用镜像不削弱安全性。
 *
 * 检查用「赛跑」（[firstSuccess]）：主地址和镜像同时发，谁先成功用谁，既加速又保证总耗时 ≤ 单次超时（7s），
 * 不会顶到上游外层的 8s 而重新触发卡死。下载用「顺序兜底」（[firstOk]）：一个 chunk 先主地址、再镜像，第一个 ok 的赢
 * （下载没有外层 8s 限制，且逐 chunk 续传，顺序兜底不浪费带宽）。
 *
 * 镜像实测（2026-11-22，本机=国内网络）：cdn.jsdelivr.net / gh-proxy.com / ghproxy.net 对清单字节一致、对 Release 包
 * HEAD 200 且 Content-Length 与清单相符；ghproxy.com / mirror.ghproxy.com / raw.gitmirror.com 已失效，不配。
 * jsDelivr 只serve仓库文件（清单），不serve Release 资产，故只进清单的备用列表。
 */
object UpdateMirrors {
    /** 检查单次尝试的上限；必须 < 上游 UPDATE_REQUEST_TIMEOUT_MS(8s)，保证外层超时不触发。 */
    const val CHECK_ATTEMPT_MS = 7_000L

    /** 下载单个 chunk 单次尝试的上限（chunk=1MiB，给足余量）；下载没有外层 8s 限制。 */
    const val DOWNLOAD_ATTEMPT_MS = 20_000L

    private const val RAW = "https://raw.githubusercontent.com/"
    private const val GITHUB = "https://github.com/"

    /** 主地址（清单 raw / Release 资产）对应的国内备用地址，按优先级；非 GitHub 地址返回空（不碰 Emby 等别的请求）。 */
    fun alternates(url: String): List<String> {
        val out = mutableListOf<String>()
        if (url.startsWith(RAW)) {
            // raw.githubusercontent.com/<owner>/<repo>/<ref>/<path> -> cdn.jsdelivr.net/gh/<owner>/<repo>@<ref>/<path>
            val parts = url.removePrefix(RAW).split('/', limit = 4)
            if (parts.size == 4) out += "https://cdn.jsdelivr.net/gh/${parts[0]}/${parts[1]}@${parts[2]}/${parts[3]}"
        }
        if (url.startsWith(RAW) || url.startsWith(GITHUB)) {
            out += "https://gh-proxy.com/$url"
            out += "https://ghproxy.net/$url"
        }
        return out
    }

    /**
     * 赛跑：[urls] 同时尝试，第一个成功的赢（其余取消）；全失败抛最后一个错误。
     * 每次尝试包 [attemptMs] 超时，超时转成普通 [UpdateAttemptTimeout]（不是取消）；真正的调用方取消原样抛。
     * 总耗时 ≤ [attemptMs]，故可安全放在上游外层 withTimeout 之内。
     */
    suspend fun <T> firstSuccess(urls: List<String>, attemptMs: Long, block: suspend (String) -> T): T = coroutineScope {
        val answers = Channel<Result<T>>(urls.size)
        val jobs = urls.map { u ->
            launch {
                val r = try {
                    Result.success(withTimeout(attemptMs) { block(u) })
                } catch (e: TimeoutCancellationException) {
                    // 只转换「我们自己这层」的超时；调用方真取消了的话 ensureActive 会把取消抛回去
                    currentCoroutineContext().ensureActive()
                    Result.failure<T>(UpdateAttemptTimeout("update request timed out: $u"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure<T>(e)
                }
                answers.send(r)
            }
        }
        var last: Throwable? = null
        repeat(urls.size) {
            val r = answers.receive()
            r.onSuccess { v ->
                jobs.forEach { it.cancel() }
                return@coroutineScope v
            }.onFailure { e ->
                if (e is CancellationException) throw e
                last = e
            }
        }
        throw last ?: IllegalStateException("no update url")
    }

    /**
     * 顺序兜底：[urls] 逐个尝试，第一个 [ok] 的赢；都不 ok 返回最后一个值（让上层按它的失败原因处理），
     * 全抛异常则抛最后一个。每次尝试包 [attemptMs] 超时并转成普通异常；真正的调用方取消原样抛。
     */
    suspend fun <T> firstOk(urls: List<String>, attemptMs: Long, ok: (T) -> Boolean, block: suspend (String) -> T): T {
        var lastValue: T? = null
        var lastError: Throwable? = null
        for (u in urls) {
            val v = try {
                withTimeout(attemptMs) { block(u) }
            } catch (e: TimeoutCancellationException) {
                currentCoroutineContext().ensureActive()
                lastError = UpdateAttemptTimeout("update request timed out: $u")
                continue
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                continue
            }
            if (ok(v)) return v
            lastValue = v
        }
        lastValue?.let { return it }
        throw lastError ?: IllegalStateException("no update url")
    }
}

/** 更新请求超时的「普通异常」：上游 catch (_: Exception) 能接住 → 落成 Failed，而不是被当取消重抛卡死 Checking。 */
class UpdateAttemptTimeout(message: String) : RuntimeException(message)

/**
 * 更新检查（清单请求）的传输层：主地址 + 国内镜像赛跑，超时转普通异常。经 `PlatformDeps.updateTransport` 接进上游
 * `UpdateController`（两个壳），只影响更新检查，不碰 Emby / 图片 / 弹幕的请求。
 */
class UpdateCheckTransport(
    private val delegate: Transport,
    private val attemptMs: Long = UpdateMirrors.CHECK_ATTEMPT_MS,
) : Transport {
    override suspend fun execute(req: TransportRequest): TransportResponse =
        UpdateMirrors.firstSuccess(listOf(req.url) + UpdateMirrors.alternates(req.url), attemptMs) { u ->
            delegate.execute(TransportRequest(u, req.method, req.headers, req.body, req.timeoutMs))
        }
}
