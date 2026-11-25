package com.gugaart.ui.update

import com.gugugaga.app.AppGraph
import com.gugugaga.app.settings.updateAutoDownload
import com.gugugaga.app.update.UPDATE_START_DELAY_MS
import com.gugugaga.app.update.UpdateController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 界面用的更新器：`:ui` 里所有读更新状态、点「检查更新」的地方都经它，不直接读 `graph.update`。
 * 别的端没有登记过 → 就是上游的 `AppGraph.update`（行为不变）；iOS 登记过（[OwnVerifier.install]）→ 带本仓库验签的那一个。
 */
val AppGraph.updater: UpdateController get() = OwnVerifier.controllerFor(this) ?: update

/*
 * iOS 的更新器（1.4.0）。上游 `AppGraph.update` 在 iOS 上验签恒为 false（上游 `Ed25519.ios.kt`），上游只读、`AppGraph` 也不留验签的缝，
 * 所以 iOS 壳造好应用图后调 [install]：用上游同一个 `UpdateController`（公开构造，参数和 `AppGraph.update` 一模一样），只把
 * `verifier` 换成本仓库的纯 Kotlin [Ed25519.verifyManifest]。查清单、比版本、记状态、「稍后」都还是上游的；iOS 上不下载、不安装
 * （`IosUpdateHost` 什么都不装，号外走侧载分支「打开发布页」，D-lead-26）。
 * 上游的 `graph.update` 在 iOS 上就不再被碰（`:ui` 全经 [updater]），不会有第二个更新器在后台查。
 */
object OwnVerifier {
    private var graph: AppGraph? = null
    private var controller: UpdateController? = null

    /** 这个应用图登记过的更新器；没登记 = null（用上游的）。 */
    fun controllerFor(g: AppGraph): UpdateController? = controller?.takeIf { graph === g }

    /** 只用在 iOS 壳（整个进程一个应用图）；同一个图再调一次返回已有的那一个。 */
    fun install(g: AppGraph, verifier: (payload: String, signatureB64: String, publicKeyB64: String) -> Boolean = Ed25519::verifyManifest): UpdateController {
        controllerFor(g)?.let { return it }
        val deps = g.deps
        val c = UpdateController(
            config = deps.updateConfig,
            transport = deps.updateTransport ?: deps.transport ?: com.gugugaga.core.KtorTransport(com.gugugaga.core.platformHttpClient()),
            host = deps.updateHost,
            local = deps.local,
            log = g.log,
            scope = g.scope,
            autoDownloadEnabled = { g.settings.value.updateAutoDownload },
            meteredNetwork = { deps.meteredNetwork?.invoke() == true },
            verifier = verifier,
        )
        graph = g
        controller = c
        // 1.3.x 的 iPhone 每次检查都记下「签名校验失败」（那时 iOS 没有验签）：升上来以后的第一次启动就重查一次（和上游自动检查同一个时刻），
        // 关于页不挂着那句旧话，新版的号外也不用等到下一个钟头
        if (deps.updateConfig.enabled && staleFailure(c.status.value.lastResult)) {
            g.scope.launch {
                delay(UPDATE_START_DELAY_MS)
                // 这 10 秒里已经查过了（手动「检查」、上游的自动检查）就不再查：不然会盖掉刚查到的结果
                if (staleFailure(c.status.value.lastResult)) c.checkNow()
            }
        }
        return c
    }

    /** 上游「签名不对」时记的那句（`UpdateCheck.kt`）。 */
    const val SIGNATURE_FAILED = "更新清单签名校验失败，已忽略"

    /** 上次检查的结果是不是「签名不对」（1.3.x 的 iPhone 每次都是）：是就启动后重查一次。纯函数，有单测。 */
    fun staleFailure(lastResult: String): Boolean = lastResult == SIGNATURE_FAILED
}
