// :shell-desktop —— Windows 壳。造上游的 PlatformDeps / AppGraph、开窗口栈、调用 :ui 的 GugaartApp。
// 播放：上游壳 :desktopApp 里的窗口栈装配和桌面 PlayerPlatform 复制在 src/…/upstream/（docs/adr/0002 §6，登记在 docs/upstream-sync.md §5.1）；
// 内核和窗口栈本身（mpv / VLC 引擎、VideoStage）是上游的 :player-desktop，直接依赖。
// 发版（docs/release.md）：packageMsi 出 Windows 安装包（随包 mpv、图标、固定升级码、按用户安装）；开发开关不进包。
import java.security.MessageDigest
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val gugaartVersion = rootProject.extra["gugaartVersion"] as String

// 版本号进代码：build/generated/buildinfo/…/BuildInfo.kt
// Trakt 应用（用户 2026-11-15：自己注册，Client ID / Secret 放 .env，正式包内嵌）：环境变量 > 仓库根 .env；没有 = 空 = 这一版不能连 Trakt
fun traktValue(key: String): String = providers.environmentVariable(key).orNull?.trim()?.takeIf { it.isNotEmpty() }
    ?: rootProject.file(".env").takeIf { it.isFile }?.readLines()?.firstNotNullOfOrNull { line ->
        Regex("""^\s*${Regex.escape(key)}\s*=\s*(.*)$""").find(line)?.groupValues?.get(1)?.trim()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() }
    }.orEmpty()

val generateBuildInfo = tasks.register("generateBuildInfo") {
    val out = layout.buildDirectory.dir("generated/buildinfo")
    val version = gugaartVersion
    val code = rootProject.extra["gugaartVersionCode"] as Int
    val traktId = traktValue("TRAKT_CLIENT_ID")
    val traktSecret = traktValue("TRAKT_CLIENT_SECRET")
    inputs.property("version", version)
    inputs.property("code", code)
    inputs.property("trakt", (traktId + traktSecret).hashCode())
    outputs.dir(out)
    doLast {
        val f = out.get().file("com/gugaart/desktop/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        // VERSION_CODE：应用内更新拿它和清单比（和安卓的 versionCode 同一个算法）
        f.writeText("package com.gugaart.desktop\n\ninternal object BuildInfo {\n    const val APP_VERSION = \"$version\"\n    const val VERSION_CODE = $code\n    const val TRAKT_CLIENT_ID = \"$traktId\"\n    const val TRAKT_CLIENT_SECRET = \"$traktSecret\"\n}\n")
    }
}
// JVM 目标不设：和上游的 :player-desktop / :desktopApp 一样用运行 Gradle 的那个 JDK（21）
kotlin {
    sourceSets.main { kotlin.srcDir(generateBuildInfo) }
}

dependencies {
    implementation(project(":ui"))
    // 上游的 Windows 播放：mpv / VLC 引擎 + 窗口栈（VideoStage）
    implementation(project(":player-desktop"))
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    // 窗口栈宿主要的 Win32（输入法上下文、防休眠）——和 :player-desktop 用的是同一个 JNA
    implementation(libs.jna.platform)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

// ---- 开发运行（:shell-desktop:run）。和上游壳同一套约定：
//   -Pgugu.mpv=<mpv.exe>                        内核的位置（二进制不入库；不给时按上游的顺序找：环境变量 GUGU_MPV → PATH → 常见安装目录）
//   -Pgugaart.mockAccount=http://127.0.0.1:<p>  Mock Emby 的夹具账号（demo / demo，tools/mock-emby 的夹具，从不是真实凭据），第一次启动静默登录
// Compose 的 application.jvmArgs 同时进 run 和打出来的包，所以开发开关（-Dgugaart.dev / Mock / 探针 / 程序参数）只在【不打包】的构建里加；
// 打包的构建带了 -Pgugaart.mockAccount / -Pgugaart.args 直接失败（docs/release-standard.md S2）。
val mpvPath: String? = providers.gradleProperty("gugu.mpv").orNull?.trim()?.takeIf { it.isNotEmpty() }
val mockAccountUrl: String? = providers.gradleProperty("gugaart.mockAccount").orNull?.trim()?.takeIf { it.isNotEmpty() }
if (mockAccountUrl != null) require(Regex("""^https?://[^\s"\\]+$""").matches(mockAccountUrl)) { "-Pgugaart.mockAccount 必须是 http(s) 地址：$mockAccountUrl" }
val packagingBuild: Boolean = gradle.startParameter.taskNames.any { t ->
    val n = t.substringAfterLast(':')
    n.startsWith("package", ignoreCase = true) || n.contains("Distributable", ignoreCase = true) || n == "verifyDesktopPackage"
}
if (packagingBuild && (mockAccountUrl != null || providers.gradleProperty("gugaart.args").isPresent || providers.gradleProperty("gugaart.probe.waitMs").isPresent)) {
    throw GradleException("打包的构建不能带开发开关（-Pgugaart.mockAccount / -Pgugaart.args / -Pgugaart.probe.waitMs）")
}

// ---- 随包的 mpv 内核：二进制永不入库，打包时从上游主检出的 apps/desktop/resources/mpv 复制（和上游 :desktopApp 的 prepareMpv 同一套文件）----
// 来源顺序：-Pgugu.mpvDir=<有 mpv.exe 的目录> → 环境变量 GUGU_MPV_DIR → <上游 native 的上一级>/apps/desktop/resources/mpv。找不到时打包失败。
abstract class PrepareMpvTask : DefaultTask() {
    @get:Optional @get:InputDirectory @get:PathSensitive(PathSensitivity.NONE) abstract val sourceDir: DirectoryProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val noticeFile: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun prepare() {
        val resources = File(outputDir.get().asFile, "windows")
        resources.deleteRecursively()
        val target = File(resources, "mpv").apply { mkdirs() }
        noticeFile.get().asFile.copyTo(File(resources, "NOTICE.md"), overwrite = true)
        if (!sourceDir.isPresent) throw GradleException("找不到 mpv 内核：给 -Pgugu.mpvDir=<有 mpv.exe 的目录>（或环境变量 GUGU_MPV_DIR）")
        val src = sourceDir.get().asFile
        val files = mutableListOf<String>()
        fun copyOne(rel: String) {
            val from = File(src, rel)
            if (!from.isFile) return
            from.copyTo(File(target, rel).apply { parentFile.mkdirs() }, overwrite = true)
            files += rel.replace('\\', '/')
        }
        copyOne("mpv.exe")
        src.listFiles { f -> f.isFile && f.extension.equals("dll", ignoreCase = true) }?.sortedBy { it.name }?.forEach { copyOne(it.name) }
        copyOne("mpv/fonts.conf")
        val digest = MessageDigest.getInstance("SHA-256")
        File(target, "mpv.exe").inputStream().use { ins -> val buf = ByteArray(1 shl 20); while (true) { val n = ins.read(buf); if (n < 0) break; digest.update(buf, 0, n) } }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        @Suppress("UNCHECKED_CAST")
        val old = File(src, "BUNDLE.json").takeIf { it.isFile }?.let { runCatching { groovy.json.JsonSlurper().parse(it) as Map<String, Any?> }.getOrNull() }
        val bundle = linkedMapOf<String, Any?>(
            "version" to (old?.get("version") ?: "unknown (run mpv.exe --version)"),
            "source" to (old?.get("source") ?: "upstream apps/desktop/resources/mpv"),
            "files" to files,
            "size" to File(target, "mpv.exe").length(),
            "sha256" to sha,
            "note" to "mpv is GPL-2.0-or-later / LGPL-2.1-or-later depending on the build: see ../NOTICE.md",
        )
        File(target, "BUNDLE.json").writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(bundle)) + "\n")
        logger.lifecycle("mpv 内核：${files.size} 个文件（mpv.exe ${File(target, "mpv.exe").length() / 1024 / 1024} MiB）")
    }
}

fun resolveMpvSourceDir(): File? {
    fun hasMpv(d: File?): Boolean = d != null && File(d, "mpv.exe").isFile
    listOf(providers.gradleProperty("gugu.mpvDir").orNull, providers.environmentVariable("GUGU_MPV_DIR").orNull)
        .mapNotNull { it?.trim()?.takeIf { v -> v.isNotEmpty() } }
        .forEach { v -> val d = File(v).absoluteFile; if (hasMpv(d)) return d else throw GradleException("-Pgugu.mpvDir / GUGU_MPV_DIR 指向的目录里没有 mpv.exe") }
    val upstreamNative = (gradle.extra.properties["gugaartNativeDir"] as? File) ?: rootProject.file((findProperty("gugaart.nativeDir") as String?) ?: "../gugugagaemby/native")
    return File(upstreamNative.absoluteFile.parentFile, "apps/desktop/resources/mpv").takeIf { hasMpv(it) }
}

val prepareMpv = tasks.register<PrepareMpvTask>("prepareMpv") {
    group = "distribution"
    description = "把 mpv 内核（mpv.exe + DLL + fonts.conf）和第三方声明复制进安装包的资源目录（不入库）"
    resolveMpvSourceDir()?.let { sourceDir.set(it) }
    noticeFile.set(layout.projectDirectory.file("packaging/NOTICE.md"))
    outputDir.set(layout.buildDirectory.dir("release/appResources"))
}

compose.desktop {
    application {
        mainClass = "com.gugaart.desktop.MainKt"
        // 跟随 Windows 系统代理（和上游一样；Main.kt 里也装了 SystemProxySelector）
        jvmArgs += listOf("-Djava.net.useSystemProxies=true", "-Dfile.encoding=UTF-8")
        if (!packagingBuild) {
            // 程序参数：-Pgugaart.args=--ui=phone,--route=/play/1822（逗号分隔；经全局锁再经 cmd 转两手之后，带空格的 --args="…" 会被拆开。
            // Compose 的 run 任务自己设 args，所以要从这里给，不能在 JavaExec 上加）
            // JVM 参数也从这里给（Compose 的 run 任务不认 JavaExec 上后加的 jvmArgs）。Mock 只传地址：JSON 的引号过不了 Windows 的命令行
            jvmArgs += listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dgugaart.dev=true")
            mpvPath?.let { jvmArgs += "-Dgugu.mpv.bundled=$it" }
            mockAccountUrl?.let { jvmArgs += "-Dgugaart.mockAccount=$it" }
            providers.gradleProperty("gugaart.probe.waitMs").orNull?.let { jvmArgs += "-Dgugaart.probe.waitMs=$it" }
            providers.gradleProperty("gugaart.args").orNull?.split(",")?.filter { it.isNotBlank() }?.let { args += it }
        }
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            // ASCII：jpackage 把它写进 exe 的版本资源、安装目录、MSI 产品名、开始菜单；中文会乱码（上游 N12 实测）。窗口标题和界面写「咕嘎画报 · EMBY」。
            packageName = "GugaArt"
            packageVersion = gugaartVersion
            description = "GugaArt Player"
            vendor = "GugaArt"
            copyright = "GugaArt"
            // resources/mpv（内核）+ resources/NOTICE.md：播放器经 compose.application.resources.dir 找到（上游 MpvLocate.kt）
            appResourcesRootDir.set(prepareMpv.flatMap { it.outputDir })
            // jlink 运行时：和上游 :desktopApp 同一份清单（反射 / ServiceLoader 用到的 jdeps 看不见，见上游注释）
            modules(
                "java.instrument",
                "java.net.http",
                "jdk.management",
                "java.naming",
                "java.management",
                "jdk.crypto.ec", // ECDHE 的 TLS 套件：没有它 https 服务器握手失败
                "jdk.localedata", // 中文排序（拼音）
                "jdk.unsupported",
            )
            windows {
                iconFile.set(layout.projectDirectory.file("packaging/icon.ico"))
                menuGroup = "GugaArt"
                shortcut = true
                dirChooser = true
                perUserInstall = true // 不要管理员权限：%LOCALAPPDATA%\GugaArt
                // 产品终身固定：Windows Installer 靠它找到并升级已装的 GugaArt。永远不要重新生成。和上游「咕咕嘎嘎」的升级码不同，两者可以同时装。
                upgradeUuid = "5e583908-1116-4f56-89e8-567d9df7aa78"
            }
        }
    }
}

// WiX 3：不下载（gradle.properties 关了 downloadWix）。发版脚本给 WIX_PATH（上游构建目录里已有的 wix311）；没有 WIX_PATH 时 jpackage 的
// Windows 任务也要一个 wixToolsetDir，给一个空目录让 createDistributable 照常能跑（和上游同一个做法）。
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>().configureEach {
    if (!wixToolsetDir.isPresent) {
        val wix = providers.environmentVariable("WIX_PATH").orNull?.takeIf { File(it, "candle.exe").isFile }
        wixToolsetDir.set(if (wix != null) layout.dir(provider { File(wix) }).get() else layout.projectDirectory.dir("packaging/no-wix"))
    }
}
