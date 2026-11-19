// :shell-desktop —— Windows 壳。造上游的 PlatformDeps / AppGraph、开窗口栈、调用 :ui 的 GugaartApp。
// 播放：上游壳 :desktopApp 里的窗口栈装配和桌面 PlayerPlatform 复制在 src/…/upstream/（docs/adr/0002 §6，登记在 docs/upstream-sync.md §5.1）；
// 内核和窗口栈本身（mpv / VLC 引擎、VideoStage）是上游的 :player-desktop，直接依赖。
// 发版（docs/release.md）：packageMsi 出 Windows 安装包（随包 mpv、图标、固定升级码、按用户安装）；开发开关不进包。
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

/**
 * 删一个目录树，**不跟随符号链接**：链接本身删掉，链接指向的东西一个字节不碰。
 *
 * 不用 Kotlin 的 `File.deleteRecursively()`——它遇到指向目录的符号链接会**钻进去删目标的内容**。
 * 2026-11-19 的事故：`packageMacDmg` 的 stage 里有 `Applications -> /Applications`（.dmg 里拖拽安装的快捷方式），
 * 第二次打包时 `work.deleteRecursively()` 顺着它把 /Applications 里当前用户删得动的应用全删了。
 */
object SafeFs {
    fun deleteTreeNoFollow(root: File) {
        val p = root.toPath()
        if (Files.isSymbolicLink(p)) { Files.delete(p); return }
        if (!Files.exists(p)) return
        Files.walkFileTree(p, object : SimpleFileVisitor<Path>() {   // walkFileTree 默认不跟随链接：链接作为普通条目交给 visitFile
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult { Files.delete(file); return FileVisitResult.CONTINUE }
            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult { if (exc != null) throw exc; Files.delete(dir); return FileVisitResult.CONTINUE }
        })
    }
}

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
// macOS 的播放内核是上游的 :player-apple（libmpv 进程内，ADR 0006）。它只在 macOS 主机、且上游那边有这个模块时挂（settings.gradle.kts），
// 所以 Mac 壳里用到它的那一个文件按构建二选一：挂了 = src/macLibmpv/，没挂（在 Windows 上编这个壳）= src/macNoLibmpv/ 的空实现。
val withPlayerApple: Boolean = (gradle.extra.properties["gugaartPlayerApple"] as? Boolean) == true
kotlin {
    sourceSets.main {
        kotlin.srcDir(generateBuildInfo)
        kotlin.srcDir(if (withPlayerApple) "src/macLibmpv/kotlin" else "src/macNoLibmpv/kotlin")
    }
}

dependencies {
    implementation(project(":ui"))
    // 上游的 Windows 播放：mpv / VLC 引擎 + 窗口栈（VideoStage）
    implementation(project(":player-desktop"))
    // 上游的 macOS 播放：libmpv 进程内 + 窗口层树里的 Metal 层（只在 macOS 主机上有这个模块）
    if (withPlayerApple) implementation(project(":player-apple"))
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
        SafeFs.deleteTreeNoFollow(resources)
        val target = File(resources, "mpv").apply { mkdirs() }
        noticeFile.get().asFile.copyTo(File(resources, "NOTICE.md"), overwrite = true)
        if (!sourceDir.isPresent) {
            // macOS：mpv 不随包（ADR 0004 §2.9，P-mac-7 用本机安装的）；run 也经 appResourcesRootDir 依赖本任务，
            // 所以没有 mpv.exe 时出一个空清单而不是失败（M2-9 的 prepareMpv 条款，M1-2 开窗就需要）。Windows 照旧失败。
            if (System.getProperty("os.name").startsWith("Mac")) {
                val bundle = linkedMapOf<String, Any?>("files" to emptyList<String>(), "note" to "macOS: mpv not bundled (ADR 0004 §2.9)")
                File(target, "BUNDLE.json").writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(bundle)) + "\n")
                logger.lifecycle("mpv 内核：macOS 不随包（用本机安装的 mpv）")
                return
            }
            throw GradleException("找不到 mpv 内核：给 -Pgugu.mpvDir=<有 mpv.exe 的目录>（或环境变量 GUGU_MPV_DIR）")
        }
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

// ---- macOS（任务单 M2-9；decisions.md D-lead-7 / D-lead-9；release-1.3.md A12）：只在 Mac 主机上 ----
// 随包的内核 = MPVKit 1.0.0（GPL）链成的 libmpv.dylib（上游 native/player-apple/tools/link-libmpv-macos.sh 的产物，不入库）：
//   -Pgugaart.mpvkitDir=<目录>（下面有 <arch>/libmpv.dylib），默认 ~/dev/gugu/third_party/mpvkit/1.0.0/macos。
// 打包时（packageMacDmg / createDistributable）放进 Contents/app/resources/libmpv/（MacMpvLocate 第一个找的地方），找不到就打包失败；
// 开发运行（run）不放——那里照旧用 -Pgugaart.libmpv / 开发机上链好的那一份，error-nolib 这类场景才测得到「没有内核」。
val isMacBuild: Boolean = System.getProperty("os.name").startsWith("Mac")
val macArch: String = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x86_64"

abstract class PrepareMacResourcesTask : DefaultTask() {
    @get:Optional @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val libmpv: RegularFileProperty
    @get:Input abstract val bundleLib: Property<Boolean>
    @get:Input abstract val source: Property<String>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val noticeFile: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun prepare() {
        val resources = File(outputDir.get().asFile, "macos")
        SafeFs.deleteTreeNoFollow(resources)
        resources.mkdirs()
        noticeFile.get().asFile.copyTo(File(resources, "NOTICE.md"), overwrite = true)
        if (!bundleLib.get()) {
            logger.lifecycle("libmpv：开发运行不随包（用 -Pgugaart.libmpv / 开发机上的那一份）")
            return
        }
        val lib = libmpv.orNull?.asFile?.takeIf { it.isFile }
            ?: throw GradleException("找不到随包的 libmpv.dylib：给 -Pgugaart.mpvkitDir=<有 <arch>/libmpv.dylib 的目录>（默认 ~/dev/gugu/third_party/mpvkit/1.0.0/macos）。不出没有内核的包")
        val target = File(resources, "libmpv").apply { mkdirs() }
        val copy = File(target, "libmpv.dylib")
        lib.copyTo(copy, overwrite = true)
        val digest = MessageDigest.getInstance("SHA-256")
        copy.inputStream().use { ins -> val buf = ByteArray(1 shl 20); while (true) { val n = ins.read(buf); if (n < 0) break; digest.update(buf, 0, n) } }
        val bundle = linkedMapOf<String, Any?>(
            "file" to "libmpv.dylib",
            "size" to copy.length(),
            "sha256" to digest.digest().joinToString("") { "%02x".format(it) },
            "source" to source.get(),
            "installName" to "@rpath/libmpv.dylib",
            "note" to "MPVKit 1.0.0, GPL build, linked into one dylib by native/player-apple/tools/link-libmpv-macos.sh (upstream): see ../NOTICE.md",
        )
        File(target, "BUNDLE.json").writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(bundle)) + "\n")
        logger.lifecycle("libmpv：随包 ${copy.length() / 1024 / 1024} MiB")
    }
}

val mpvkitDir: File = File(
    providers.gradleProperty("gugaart.mpvkitDir").orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: "${System.getProperty("user.home")}/dev/gugu/third_party/mpvkit/1.0.0/macos",
)
val prepareMacResources = tasks.register<PrepareMacResourcesTask>("prepareMacResources") {
    group = "distribution"
    description = "macOS：随包的 libmpv.dylib（只在打包时）+ 第三方声明放进 .app 的资源目录（不入库）"
    File(mpvkitDir, "$macArch/libmpv.dylib").takeIf { it.isFile }?.let { libmpv.set(it) }
    bundleLib.set(packagingBuild)
    source.set("MPVKit 1.0.0 (GPL) · $macArch")
    noticeFile.set(layout.projectDirectory.file("packaging/NOTICE.md"))
    outputDir.set(layout.buildDirectory.dir("release/macResources"))
}

compose.desktop {
    application {
        mainClass = "com.gugaart.desktop.MainKt"
        // 跟随 Windows 系统代理（和上游一样；Main.kt 里也装了 SystemProxySelector）
        jvmArgs += listOf("-Djava.net.useSystemProxies=true", "-Dfile.encoding=UTF-8")
        // macOS（MP-5 / MP-9）：画面在 Compose 之下要的开关。MacSession 在第一扇窗之前也会设；放进启动配置是为了装好的应用里
        // 一定在任何 AWT 初始化之前就有（release-1.3.md A12：查 Contents/app/*.cfg）。只在 Mac 主机上加，Windows 的 MSI 不变
        if (isMacBuild) jvmArgs += "-Dcompose.interop.blending=true"
        if (!packagingBuild) {
            // 程序参数：-Pgugaart.args=--ui=phone,--route=/play/1822（逗号分隔；经全局锁再经 cmd 转两手之后，带空格的 --args="…" 会被拆开。
            // Compose 的 run 任务自己设 args，所以要从这里给，不能在 JavaExec 上加）
            // JVM 参数也从这里给（Compose 的 run 任务不认 JavaExec 上后加的 jvmArgs）。Mock 只传地址：JSON 的引号过不了 Windows 的命令行
            jvmArgs += listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dgugaart.dev=true")
            mpvPath?.let { jvmArgs += "-Dgugu.mpv.bundled=$it" }
            // macOS：-Pgugaart.libmpv=<libmpv.dylib 或它所在的目录>（不给 = 本机 Homebrew 的）；-Pgugaart.mpvLog=<文件> 让内核写详细日志
            providers.gradleProperty("gugaart.libmpv").orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { jvmArgs += "-Dgugu.apple.libmpv=$it" }
            providers.gradleProperty("gugaart.mpvOpts").orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { jvmArgs += "-Dgugu.mpv.opts=$it" }
            providers.gradleProperty("gugaart.mpvHwdec").orNull?.let { jvmArgs += "-Dgugu.mpv.hwdec=$it" }
            providers.gradleProperty("gugaart.probe.pick").orNull?.let { jvmArgs += "-Dgugaart.probe.pick=$it" }
            providers.gradleProperty("gugaart.probe.start").orNull?.let { jvmArgs += "-Dgugaart.probe.start=$it" }
            providers.gradleProperty("gugaart.probe.keep").orNull?.let { jvmArgs += "-Dgugaart.probe.keep=$it" }
            providers.gradleProperty("gugaart.probe.shots").orNull?.let { jvmArgs += "-Dgugaart.probe.shots=$it" }
            providers.gradleProperty("gugaart.probe.nowPlaying").orNull?.let { jvmArgs += "-Dgugaart.probe.nowPlaying=$it" }
            providers.gradleProperty("gugaart.probe.pauseAt").orNull?.let { jvmArgs += "-Dgugaart.probe.pauseAt=$it" }
            providers.gradleProperty("gugaart.probe.click").orNull?.let { jvmArgs += "-Dgugaart.probe.click=$it" }
            providers.gradleProperty("gugaart.probe.urlFile").orNull?.let { jvmArgs += "-Dgugaart.probe.urlFile=$it" }
            providers.gradleProperty("gugaart.mpvLog").orNull?.trim()?.takeIf { it.isNotEmpty() }?.let { jvmArgs += "-Dgugu.mpv.log=$it" }
            mockAccountUrl?.let { jvmArgs += "-Dgugaart.mockAccount=$it" }
            providers.gradleProperty("gugaart.probe.waitMs").orNull?.let { jvmArgs += "-Dgugaart.probe.waitMs=$it" }
            providers.gradleProperty("gugaart.args").orNull?.split(",")?.filter { it.isNotBlank() }?.let { args += it }
        }
        nativeDistributions {
            // Windows 主机出 MSI；Mac 主机的 .dmg 不走 jpackage 的 dmg（它出包后签不了里面的 .app）：createDistributable → packageMacDmg
            targetFormats(if (isMacBuild) TargetFormat.Dmg else TargetFormat.Msi)
            // ASCII：jpackage 把它写进 exe 的版本资源、安装目录、MSI 产品名、开始菜单；中文会乱码（上游 N12 实测）。窗口标题和界面写「咕嘎画报 · EMBY」。
            packageName = "GugaArt"
            packageVersion = gugaartVersion
            description = "GugaArt Player"
            vendor = "GugaArt"
            copyright = "GugaArt"
            // resources/mpv（内核）+ resources/NOTICE.md：播放器经 compose.application.resources.dir 找到（上游 MpvLocate.kt）
            appResourcesRootDir.set(if (isMacBuild) prepareMacResources.flatMap { it.outputDir } else prepareMpv.flatMap { it.outputDir })
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
            // macOS（任务单 M2-9 / M2-13 / M2-15；D-lead-9 / D-lead-15）：bundle id、Dock 名、图标、Info.plist 的几个键。
            // 不签名、不公证：签名（ad-hoc）在 packageMacDmg 里由里到外自己做；LSMinimumSystemVersion 也在那里按包里 Mach-O 的 minos 改成实测值
            macOS {
                bundleID = "com.gugugaga.art"
                dockName = "咕咕嘎嘎"
                packageName = "GugaArt"
                appCategory = "public.app-category.entertainment"
                minimumSystemVersion = "12.0"
                layout.projectDirectory.file("packaging/icon.icns").asFile.takeIf { it.isFile }?.let { iconFile.set(it) }
                infoPlist {
                    extraKeysRawXml = listOf(
                        "CFBundleDisplayName" to "<string>咕咕嘎嘎</string>",
                        // 「本地网络」用途说明（D-lead-15 / M2-13）：第一次找 DLNA 电视时系统弹的框里就是这句
                        "NSLocalNetworkUsageDescription" to "<string>用来在同一个网络里找到可以投屏的电视。</string>",
                        // 支持深色外观（应用自己固定深色：apple.awt.application.appearance，M1-6）
                        "NSRequiresAquaSystemAppearance" to "<false/>",
                    ).joinToString("\n") { (k, v) -> "  <key>$k</key>\n  $v" }
                }
            }
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

// M1-4 探针门禁：-Dgugaart.macProbe=true 才跑（要开真窗口）；默认 Assume 跳过，不算失败。
// M2-5：-Pgugaart.keychainTest=true 才跑真的钥匙串往返（单独的服务名、假值、跑完删掉）。
tasks.withType<Test>().configureEach {
    systemProperty("gugaart.macProbe", System.getProperty("gugaart.macProbe", "false"))
    systemProperty("gugaart.keychainTest", providers.gradleProperty("gugaart.keychainTest").orNull ?: "false")
}

// ---- macOS 的 .dmg（任务单 M2-9；decisions.md D-lead-7 / D-lead-9；release-1.3.md A12）----
//   :shell-desktop:packageMacDmg   →  build/release/gugaart-<版本>-macos.dmg（只在 Mac 主机上有这个任务；产物不入库）
// 走 createDistributable → 签 → 自己 hdiutil：jpackage 的 packageDmg 出包之后签不了里面的 .app。步骤：
//   1. 把 createDistributable 出的 GugaArt.app 拷进暂存目录，改名「咕咕嘎嘎.app」（号外和发布说明里说的就是把它拖进「应用程序」）；
//   2. LSMinimumSystemVersion = 包里所有 Mach-O 的 minos 的最大值（otool -l），写进 Info.plist 并记进 MACOS.json；
//   3. ad-hoc 签名，由里到外：每个 Mach-O（libmpv、运行时里的库、启动器……）各自 codesign --force --sign -，嵌套的运行时包，最后整个 .app；
//      不开加固运行时（不公证就不需要；开了反而要给 JNA / Skiko 解出来的库开例外）；codesign --verify --deep --strict 必须通过；
//   4. hdiutil create（UDZO）：卷里放应用 + 「应用程序」的快捷方式。
abstract class PackageMacDmgTask : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val appDir: DirectoryProperty
    @get:Input abstract val version: Property<String>
    @get:Input abstract val appName: Property<String>
    @get:Input abstract val volumeName: Property<String>
    @get:Internal abstract val workDir: DirectoryProperty
    @get:OutputFile abstract val dmgFile: RegularFileProperty
    @get:OutputFile abstract val reportFile: RegularFileProperty

    private fun run(vararg cmd: String, allowFail: Boolean = false): Pair<Int, String> {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code != 0 && !allowFail) throw GradleException("${cmd.take(3).joinToString(" ")} … 失败（$code）：\n${out.takeLast(2000)}")
        return code to out
    }

    /** 文件开头是不是 Mach-O（含胖二进制；Java class 文件也以 CAFEBABE 开头，但后面跟的数大，用架构数 < 20 区分）。 */
    private fun isMachO(f: File): Boolean = runCatching {
        f.inputStream().use { ins ->
            val b = ByteArray(8)
            if (ins.read(b) < 8) return false
            val m = ((b[0].toInt() and 0xff) shl 24) or ((b[1].toInt() and 0xff) shl 16) or ((b[2].toInt() and 0xff) shl 8) or (b[3].toInt() and 0xff)
            val n = ((b[4].toInt() and 0xff) shl 24) or ((b[5].toInt() and 0xff) shl 16) or ((b[6].toInt() and 0xff) shl 8) or (b[7].toInt() and 0xff)
            m == 0xfeedfacf.toInt() || m == 0xcffaedfe.toInt() || m == 0xfeedface.toInt() || m == 0xcefaedfe.toInt() || (m == 0xcafebabe.toInt() && n in 1..19)
        }
    }.getOrDefault(false)

    /** `otool -l` 里的 minos（LC_BUILD_VERSION 的 minos / LC_VERSION_MIN_MACOSX 的 version）。 */
    private fun minosOf(f: File): String? {
        val text = run("otool", "-l", f.path, allowFail = true).second
        val v = Regex("""(?m)^\s*minos\s+([0-9.]+)""").findAll(text).map { it.groupValues[1] }.toList() +
            Regex("""cmd LC_VERSION_MIN_MACOSX[\s\S]*?\n\s*version\s+([0-9.]+)""").findAll(text).map { it.groupValues[1] }.toList()
        return v.maxWithOrNull(compareBy<String>({ it.split('.').getOrNull(0)?.toIntOrNull() ?: 0 }, { it.split('.').getOrNull(1)?.toIntOrNull() ?: 0 }))
    }

    /** `otool -L` 里的依赖（第一行是自己的安装名，不算）。 */
    private fun depsOf(f: File): List<String> =
        run("otool", "-L", f.path, allowFail = true).second.lines().drop(1).map { it.trim().substringBefore(" (") }.filter { it.isNotEmpty() }

    private fun idOf(f: File): String? = run("otool", "-D", f.path, allowFail = true).second.lines().drop(1).firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private fun isForeign(path: String): Boolean = path.startsWith("/opt/homebrew/") || path.startsWith("/usr/local/")

    /**
     * 把运行时引用的本机第三方库（Homebrew）连同它们自己的依赖拷进 `Contents/runtime/Contents/Home/lib/`，所有引用改成
     * `@loader_path/<名字>`；运行时里 JDK 自己的库的安装名（Homebrew 的绝对路径）改成 `@rpath/<名字>`。返回拷进来的库名。
     */
    private fun relocateForeignLibs(app: File): List<String> {
        val lib = File(app, "Contents/runtime/Contents/Home/lib").takeIf { it.isDirectory } ?: return emptyList()
        val machos = app.walkBottomUp().filter { it.isFile && !Files.isSymbolicLink(it.toPath()) && isMachO(it) }.toList()
        // 待拷的：(引用里写的字符串, 本机上的文件)。起点 = 包里 Mach-O 直接引用的 /opt/homebrew、/usr/local
        val queue = ArrayDeque<Pair<String, File>>()
        machos.flatMap { depsOf(it) }.filter { isForeign(it) }.distinct().forEach { queue.addLast(it to File(it)) }
        val copied = LinkedHashMap<String, File>() // 引用字符串 → 包里的文件
        val byName = HashMap<String, File>()
        while (queue.isNotEmpty()) {
            val (ref, file) = queue.removeFirst()
            if (ref in copied) continue
            val src = file.canonicalFile.takeIf { it.isFile } ?: throw GradleException("运行时引用的 $ref 在这台 Mac 上不存在")
            val dst = byName.getOrPut(File(ref).name) {
                File(lib, File(ref).name).also { d -> src.copyTo(d, overwrite = true); d.setWritable(true, true) }
            }
            copied[ref] = dst
            // 它自己的依赖：绝对路径的 Homebrew 库；@rpath / @loader_path 的按原文件的 LC_RPATH / 所在目录解析，落在 Homebrew 里的也拷
            val rpaths = rpathsOf(src).map { it.replace("@loader_path", src.parent) }
            for (d in depsOf(src)) {
                if (d in copied || File(d).name == dst.name) continue
                val resolved: File? = when {
                    isForeign(d) -> File(d)
                    d.startsWith("@rpath/") -> rpaths.map { File(it, d.removePrefix("@rpath/")) }.firstOrNull { it.isFile }
                    d.startsWith("@loader_path/") -> File(src.parentFile, d.removePrefix("@loader_path/")).takeIf { it.isFile }
                    else -> null
                }
                if (resolved != null && isForeign(resolved.canonicalPath)) queue.addLast(d to resolved)
            }
        }
        val all = machos + byName.values
        for (f in all) {
            val changes = depsOf(f).filter { it in copied }.flatMap { listOf("-change", it, "@loader_path/${copied.getValue(it).name}") }
            val id = idOf(f)
            val newId = if (id != null && (isForeign(id) || f in byName.values)) listOf("-id", "@rpath/${f.name}") else emptyList()
            // 拷进来的库带着指向 Homebrew 的 LC_RPATH：删掉（依赖都改成 @loader_path 了）
            val dropRpaths = if (f in byName.values) rpathsOf(f).filter { it.startsWith("@loader_path") || isForeign(it) }.flatMap { listOf("-delete_rpath", it) } else emptyList()
            if (changes.isNotEmpty() || newId.isNotEmpty() || dropRpaths.isNotEmpty()) run("install_name_tool", *(newId + changes + dropRpaths).toTypedArray(), f.path)
        }
        if (byName.isNotEmpty()) logger.lifecycle("运行时里依赖 Homebrew 的库随包：${byName.values.joinToString { it.name }}")
        return byName.values.map { it.name }
    }

    /** `otool -l` 里的 LC_RPATH。 */
    private fun rpathsOf(f: File): List<String> =
        Regex("""cmd LC_RPATH\s+cmdsize \d+\s+path (\S+)""").findAll(run("otool", "-l", f.path, allowFail = true).second).map { it.groupValues[1] }.toList()

    @TaskAction
    fun pack() {
        val src = appDir.get().asFile.listFiles { f -> f.name.endsWith(".app") }?.singleOrNull()
            ?: throw GradleException("createDistributable 没有出 .app：${appDir.get().asFile}")
        val work = workDir.get().asFile
        SafeFs.deleteTreeNoFollow(work)
        val stage = File(work, "stage").apply { mkdirs() }
        val app = File(stage, "${appName.get()}.app")
        run("ditto", src.path, app.path)

        // 1b. 运行时里依赖 Homebrew 的库（这台 Mac 只有 Homebrew 的 JDK：libfontmanager → harfbuzz / freetype，libjavajpeg → jpeg-turbo，
        //     liblcms → little-cms2，libsplashscreen → giflib / libpng，再往下 glib、pcre2 ……）：整串拷进运行时的 lib/，引用改成
        //     @loader_path，否则没装 Homebrew 的 Mac 上这些库加载失败。改完包里任何 Mach-O 都不许再引用 /opt/homebrew 或 /usr/local
        val bundled = relocateForeignLibs(app)
        // 1c. 随包 libmpv 是在这台 Mac 上由 MPVKit 的静态库链出来的：调试映射（N_OSO）里记着本机的绝对路径（/Users/<本机账号>/…）。
        //     strip -S 只去调试符号（导出符号不动、照常 dlopen），不让本机账号名进安装包（1.3.0 发版扫描命中 EMBY_USER 发现的）。签名在后面。
        File(app, "Contents/app/resources/libmpv/libmpv.dylib").takeIf { it.isFile }?.let { run("strip", "-S", it.path) }
        // 2. 系统要求 = 包里最高的 minos
        val machos = app.walkBottomUp().filter { it.isFile && !Files.isSymbolicLink(it.toPath()) && isMachO(it) }.toList()
        val foreign = machos.flatMap { f -> (depsOf(f) + rpathsOf(f)).filter { isForeign(it) }.map { "${f.relativeTo(app)} → $it" } }
        if (foreign.isNotEmpty()) throw GradleException("包里还有引用本机 Homebrew 的 Mach-O：\n" + foreign.take(20).joinToString("\n"))
        val minos = machos.mapNotNull { minosOf(it) }.maxWithOrNull(compareBy<String>({ it.split('.').getOrNull(0)?.toIntOrNull() ?: 0 }, { it.split('.').getOrNull(1)?.toIntOrNull() ?: 0 })) ?: "12.0"
        val plist = File(app, "Contents/Info.plist")
        run("plutil", "-replace", "LSMinimumSystemVersion", "-string", minos, plist.path)

        // 3. ad-hoc 签名，由里到外：路径越深越先签；嵌套的运行时包在它里面的文件之后；整个 .app 最后
        machos.sortedByDescending { it.path.count { c -> c == '/' } }.forEach { f ->
            run("codesign", "--force", "--sign", "-", "--timestamp=none", f.path)
        }
        // 随包 libmpv 的 BUNDLE.json：签名改了文件，补上签名后的 SHA-256（原来的 sha256 是链出来那一份的，留着对来源）
        File(app, "Contents/app/resources/libmpv/BUNDLE.json").takeIf { it.isFile }?.let { bj ->
            @Suppress("UNCHECKED_CAST")
            val m = (groovy.json.JsonSlurper().parse(bj) as Map<String, Any?>).toMutableMap()
            val lib = File(bj.parentFile, "libmpv.dylib")
            val d = MessageDigest.getInstance("SHA-256")
            lib.inputStream().use { ins -> val buf = ByteArray(1 shl 20); while (true) { val n = ins.read(buf); if (n < 0) break; d.update(buf, 0, n) } }
            m["sha256Signed"] = d.digest().joinToString("") { "%02x".format(it) }
            m["sizeSigned"] = lib.length()
            bj.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(m)) + "\n")
        }
        File(app, "Contents/runtime").takeIf { File(it, "Contents/Info.plist").isFile }?.let { rt ->
            run("codesign", "--force", "--sign", "-", "--timestamp=none", rt.path)
        }
        run("codesign", "--force", "--sign", "-", "--timestamp=none", app.path)
        val verify = run("codesign", "--verify", "--deep", "--strict", "--verbose=2", app.path).second
        val info = run("codesign", "-dv", app.path).second
        if (!info.contains("Signature=adhoc")) throw GradleException("签名不是 ad-hoc：\n$info")

        // 4. 镜像：应用 + 「应用程序」的快捷方式。指向 /Applications 的链接只在 hdiutil 这一步存在，做完（含失败）立刻删掉链接本身，
        //    stage 里永远不留一条通往 /Applications 的路（见 SafeFs 的事故说明）
        val appsLink = File(stage, "Applications").toPath()
        val dmg = dmgFile.get().asFile.apply { parentFile.mkdirs(); delete() }
        Files.createSymbolicLink(appsLink, File("/Applications").toPath())
        try {
            run("hdiutil", "create", "-volname", volumeName.get(), "-srcfolder", stage.path, "-ov", "-format", "UDZO", "-fs", "HFS+", dmg.path)
        } finally {
            if (Files.isSymbolicLink(appsLink)) Files.delete(appsLink)   // Files.delete 删的是链接，不跟随
        }
        val digest = MessageDigest.getInstance("SHA-256")
        dmg.inputStream().use { ins -> val buf = ByteArray(1 shl 20); while (true) { val n = ins.read(buf); if (n < 0) break; digest.update(buf, 0, n) } }
        val sha = digest.digest().joinToString("") { "%02x".format(it) }
        val report = linkedMapOf<String, Any?>(
            "file" to dmg.name,
            "size" to dmg.length(),
            "sha256" to sha,
            "version" to version.get(),
            "app" to app.name,
            "minos" to minos,
            "machO" to machos.size,
            "bundledFromHost" to bundled,
            "signature" to "adhoc",
            "verify" to verify.lines().filter { it.isNotBlank() }.takeLast(2),
        )
        reportFile.get().asFile.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(report)) + "\n")
        logger.lifecycle("macOS 安装包：${dmg.path}（${dmg.length() / 1024 / 1024} MiB，minos $minos，$sha）")
    }
}

if (isMacBuild) {
    tasks.register<PackageMacDmgTask>("packageMacDmg") {
        group = "distribution"
        description = "macOS：createDistributable → ad-hoc 签名（由里到外）→ hdiutil 出 .dmg（build/release/gugaart-<版本>-macos.dmg）"
        dependsOn("createDistributable")
        appDir.set(layout.buildDirectory.dir("compose/binaries/main/app"))
        version.set(gugaartVersion)
        appName.set("咕咕嘎嘎")
        volumeName.set("咕咕嘎嘎 $gugaartVersion")
        workDir.set(layout.buildDirectory.dir("release/macdmg"))
        dmgFile.set(layout.buildDirectory.file("release/gugaart-$gugaartVersion-macos.dmg"))
        reportFile.set(layout.buildDirectory.file("release/gugaart-$gugaartVersion-macos.json"))
    }
}
