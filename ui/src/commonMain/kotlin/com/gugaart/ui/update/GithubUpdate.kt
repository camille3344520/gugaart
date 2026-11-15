package com.gugaart.ui.update

import com.gugugaga.app.update.DownloadTarget
import com.gugugaga.app.update.InstallOutcome
import com.gugugaga.app.update.RangeSource
import com.gugugaga.app.update.ReleaseAsset
import com.gugugaga.app.update.UpdateConfig
import com.gugugaga.app.update.UpdateHost

/*
 * 应用内更新走 GitHub（用户 2026-11-15：「现在仓库不是更新了，要支持自动更新呢」）。
 *
 * 检查、验签、比版本、断点续传下载、校验 SHA-256、调起系统安装器全部复用上游的更新器（UpdateController / UpdateHost），这里只换两样地址：
 * - 清单：签过名的 `update/v1/manifest/stable.json` 提交在本仓库里，经 raw.githubusercontent.com 取（上游拼的就是 `{base}/v1/manifest/stable.json`）；
 * - 安装包：上游拼 `{base}/v1/files/{path}`，`path` = `v0.1.4/gugaart-0.1.4-phone.apk`；下载那一层把它换成 GitHub Releases 的地址
 *   `https://github.com/<仓库>/releases/download/v0.1.4/gugaart-0.1.4-phone.apk`（会 302 到 GitHub 的文件服务器，上游的 HTTP 栈跟着跳）。
 * 清单由 scripts/release.mjs 在安装包全部传完之后才写、才推送（先有文件，后有清单）；私钥在仓库外（F:/guganewui-signing/update-ed25519.pem）。
 * 预发布也会推给大家：现在每一版都是预发布。
 */
object GithubUpdate {
    const val REPO = "camille3344520/gugaart"

    /** 清单所在的目录（raw）；上游在后面接 `/v1/manifest/stable.json`。 */
    const val BASE = "https://raw.githubusercontent.com/$REPO/main/update"

    /** 验清单签名的 Ed25519 公钥（原始 32 字节的 base64；公开信息，私钥在仓库外）。 */
    const val PUBLIC_KEY = "AF7oGsfylss5L+8wo5Hccd9jrMclhzLt9+K9TnYQv4c="

    private const val FILES = "$BASE/v1/files/"

    /** 上游拼出来的下载地址 → GitHub Releases 的地址；别的地址原样返回。 */
    fun rewrite(url: String): String =
        if (url.startsWith(FILES)) "https://github.com/$REPO/releases/download/" + url.removePrefix(FILES).trimStart('/') else url

    /** 这一端的更新配置。[assetKey]：`android-mobile` / `android-tv` / `windows-msi`。 */
    fun config(versionName: String, versionCode: Int, assetKey: String): UpdateConfig = UpdateConfig(
        baseUrl = BASE,
        publicKeyB64 = PUBLIC_KEY,
        channel = "stable",
        assetKey = assetKey,
        versionName = versionName,
        versionCode = versionCode,
    )

    /**
     * 把平台的安装宿主包一层：下载时换成 GitHub 的地址，别的（下载到哪、怎么装）都是上游原来的。
     * 另外记下「什么时候交给了系统安装器」和上游听安装结果的回调——安装确认被放到后台时由 [UpdateStall] 把卡住的状态报掉。
     */
    fun wrap(host: UpdateHost): UpdateHost = object : UpdateHost by host {
        override val rangeSource: RangeSource = RangeSource { url, from, to -> host.rangeSource.get(rewrite(url), from, to) }

        override fun setInstallListener(listener: ((InstallOutcome) -> Unit)?) {
            UpdateStall.listen(listener)
            host.setInstallListener(listener?.let { l -> { outcome: InstallOutcome -> UpdateStall.answered(); l(outcome) } })
        }

        override suspend fun install(asset: ReleaseAsset, target: DownloadTarget): InstallOutcome {
            UpdateStall.answered()
            val r = host.install(asset, target)
            if (r is InstallOutcome.Started) UpdateStall.handedOver(com.gugaart.ui.screens.update.UpdateNudge.nowMs())
            return r
        }
    }
}
