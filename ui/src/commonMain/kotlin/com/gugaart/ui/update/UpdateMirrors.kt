package com.gugaart.ui.update

import com.gugugaga.app.update.parseReleaseInfo
import com.gugugaga.app.update.parseSignedManifest
import com.gugugaga.core.Transport
import com.gugugaga.core.TransportRequest
import com.gugugaga.core.TransportResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
 * 检查取「最新的那份」（[freshest]，2026-11-23 改）：主地址和实时代理（gh-proxy / ghproxy，每次回源）同时发，第一份验签通过的清单到了
 * 以后再等 [VERIFIED_GRACE_MS]，在它们所有验签通过的清单里取 versionCode 最高的；jsDelivr 缓存分支（刚发版的几个钟头里会落后一版，
 * 1.4.0 的模拟器验收里真遇到过：raw 已是 1.3.2、jsDelivr 还是 1.3.1，原来的「谁先到用谁」号外在两版之间跳）也同时发，但只在
 * 别的都没给出验签通过的清单时才用。各条自己的超时都是单次上限（7s），总耗时不超过它，不会顶到上游外层的 8s 而重新触发卡死。
 * 下载用「顺序兜底」（[firstOk]）：一个 chunk 先主地址、再镜像，第一个 ok 的赢（下载没有外层 8s 限制，且逐 chunk 续传，
 * 顺序兜底不浪费带宽；包有 SHA-256、版本由清单定，不用比新旧）。
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

    /** 第一份验签通过的清单到了以后，再等别的实时线路多久（看有没有更新的一版）。 */
    const val VERIFIED_GRACE_MS = 1_500L

    private const val RAW = "https://raw.githubusercontent.com/"
    private const val GITHUB = "https://github.com/"

    /** 主地址（清单 raw / Release 资产）对应的国内备用地址，按优先级；非 GitHub 地址返回空（不碰 Emby 等别的请求）。 */
    fun alternates(url: String): List<String> = cachedMirrors(url) + liveMirrors(url)

    /** 实时代理：每次回源，内容和主地址一样新（清单、Release 资产都 serve）。 */
    fun liveMirrors(url: String): List<String> =
        if (url.startsWith(RAW) || url.startsWith(GITHUB)) listOf("https://gh-proxy.com/$url", "https://ghproxy.net/$url") else emptyList()

    /** 有缓存的镜像：jsDelivr 缓存分支（刚发版的几个钟头里会落后），只 serve 仓库文件（清单），不 serve Release 资产。 */
    fun cachedMirrors(url: String): List<String> {
        if (!url.startsWith(RAW)) return emptyList()
        // raw.githubusercontent.com/<owner>/<repo>/<ref>/<path> -> cdn.jsdelivr.net/gh/<owner>/<repo>@<ref>/<path>
        val parts = url.removePrefix(RAW).split('/', limit = 4)
        return if (parts.size == 4) listOf("https://cdn.jsdelivr.net/gh/${parts[0]}/${parts[1]}@${parts[2]}/${parts[3]}") else emptyList()
    }

    private sealed interface Event<out T> {
        class Answer<out T>(val primary: Boolean, val result: Result<T>, val rank: Int?) : Event<T>
        data object GraceOver : Event<Nothing>
    }

    /**
     * 取最新的那份：[primary]（主地址 + 实时代理）和 [fallback]（有缓存的镜像）同时发，每条包 [attemptMs] 超时（转成普通的
     * [UpdateAttemptTimeout]，不是取消）。[rank]：这份回答验签通过 → 它的版本号（越大越新），否则 null（不参与比较）。
     *  - 主组里第一份验签通过的到了以后再等 [graceMs]（主组都回完了就不等），取主组里 [rank] 最高的（一样高取先到的）；
     *  - 主组一份验签通过的都没有：用 [fallback] 里验签通过的（等主组都回完，最多到 [attemptMs]）；
     *  - 谁都没有验签通过的：交回一份原样的回答（主组优先），上游据它报 HTTP 状态 / 格式不对 / 签名不对；
     *  - 一份回答都没有：抛最后一个错误（全超时 = [UpdateAttemptTimeout]，上游落成「连不上更新服务器」）。
     * 每条请求自己的超时兜着，总耗时 ≤ [attemptMs]（宽限期只在还有主组请求没回完时等）。真正的调用方取消原样抛。
     */
    suspend fun <T : Any> freshest(
        primary: List<String>,
        fallback: List<String>,
        attemptMs: Long,
        graceMs: Long,
        rank: (T) -> Int?,
        block: suspend (String) -> T,
    ): T = coroutineScope {
        val events = Channel<Event<T>>(Channel.UNLIMITED)
        val all = primary.map { it to true } + fallback.map { it to false }
        val jobs = all.map { (u, isPrimary) ->
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
                val score = r.getOrNull()?.let { v -> try { rank(v) } catch (_: Exception) { null } }
                events.send(Event.Answer(isPrimary, r, score))
            }
        }
        val answers = ArrayList<Event.Answer<T>>()
        var grace: Job? = null
        var graceOver = false
        while (answers.size < all.size) {
            val primaryVerified = answers.any { it.primary && it.rank != null }
            val primaryDone = answers.count { it.primary } == primary.size
            if (primaryVerified && (primaryDone || graceOver)) break
            if (!primaryVerified && primaryDone && answers.any { !it.primary && it.rank != null }) break
            when (val e = events.receive()) {
                is Event.GraceOver -> graceOver = true
                is Event.Answer -> {
                    answers += e
                    if (grace == null && e.primary && e.rank != null) grace = launch { delay(graceMs); events.send(Event.GraceOver) }
                }
            }
        }
        jobs.forEach { it.cancel() }
        grace?.cancel()
        pick(answers)
    }

    private fun <T : Any> pick(answers: List<Event.Answer<T>>): T {
        answers.filter { it.primary && it.rank != null }.maxByOrNull { it.rank!! }?.let { return it.result.getOrThrow() }
        answers.firstOrNull { !it.primary && it.rank != null }?.let { return it.result.getOrThrow() }
        (answers.firstOrNull { it.primary && it.result.isSuccess } ?: answers.firstOrNull { it.result.isSuccess })?.let { return it.result.getOrThrow() }
        throw answers.lastOrNull()?.result?.exceptionOrNull() ?: IllegalStateException("no update url")
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
 * 更新检查（清单请求）的传输层：主地址 + 实时代理同时发、取验签通过里最新的一份，jsDelivr 兜底，超时转普通异常（[UpdateMirrors.freshest]）。
 * 经 `PlatformDeps.updateTransport` 接进上游 `UpdateController`（三个壳），只影响更新检查，不碰 Emby / 图片 / 弹幕的请求。
 *
 * 这里的验签（[verify]，默认本仓库的纯 Kotlin [Ed25519]，三端都能跑）只用来挑哪一份；信不信它还是更新器自己再验一遍
 * （iOS 是同一个 Ed25519，别的端是上游 JVM 的 verifyManifest）。
 */
class UpdateCheckTransport(
    private val delegate: Transport,
    private val attemptMs: Long = UpdateMirrors.CHECK_ATTEMPT_MS,
    private val graceMs: Long = UpdateMirrors.VERIFIED_GRACE_MS,
    private val publicKeyB64: String = GithubUpdate.PUBLIC_KEY,
    private val verify: (payload: String, signatureB64: String, publicKeyB64: String) -> Boolean = Ed25519::verifyManifest,
) : Transport {
    override suspend fun execute(req: TransportRequest): TransportResponse =
        UpdateMirrors.freshest(
            primary = listOf(req.url) + UpdateMirrors.liveMirrors(req.url),
            fallback = UpdateMirrors.cachedMirrors(req.url),
            attemptMs = attemptMs,
            graceMs = graceMs,
            rank = ::verifiedVersionCode,
        ) { u -> delegate.execute(TransportRequest(u, req.method, req.headers, req.body, req.timeoutMs)) }

    /** 这份回答是验签通过的清单 → 它的 versionCode；不是（状态不对、格式不对、签名不对）→ null，不参与比较。 */
    fun verifiedVersionCode(res: TransportResponse): Int? {
        if (res.status != 200) return null
        val signed = parseSignedManifest(res.text) ?: return null
        if (!verify(signed.payload, signed.signatureB64, publicKeyB64)) return null
        return parseReleaseInfo(signed.payload)?.versionCode
    }
}
