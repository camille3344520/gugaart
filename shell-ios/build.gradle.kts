// :shell-ios —— iOS 壳（M4-6，ADR 0005 §2.5）。一个 KMP 模块出静态 framework「GugaartShell」，Xcode 宿主在 shell-ios/xcode/ 里嵌它
// （Build Phase 跑 :shell-ios:embedAndSignAppleFrameworkForXcode）。这里只造上游的 PlatformDeps / AppGraph、iOS 的存储（文件 + 钥匙串）、
// 手机宿主（触感、左缘返回、点状态栏、侧载的号外）、文件选择器、播放（上游 :player-apple 的 iOS 一半，M5），然后调用 :ui 的 GugaartApp。
// 只在 macOS 主机、且挂的上游有 iOS 目标时才 include（settings.gradle.kts）。不加任何新依赖。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

val gugaartVersion = rootProject.extra["gugaartVersion"] as String

// 版本号和 Trakt 应用进代码（M4-11）：build/generated/buildinfo/…/BuildInfo.kt，和 :shell-desktop 同一个做法。
// Trakt：环境变量 > 仓库根 .env（读 .env 的是构建脚本，不打印）；没有 = 空 = 这一版不能连 Trakt
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
        val f = out.get().file("com/gugaart/ios/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("package com.gugaart.ios\n\ninternal object BuildInfo {\n    const val APP_VERSION = \"$version\"\n    const val VERSION_CODE = $code\n    const val TRAKT_CLIENT_ID = \"$traktId\"\n    const val TRAKT_CLIENT_SECRET = \"$traktSecret\"\n}\n")
    }
}

kotlin {
    compilerOptions {
        // UIKit / Security / Foundation 的 C 接口（钥匙串、选择器、uname）
        optIn.add("kotlinx.cinterop.ExperimentalForeignApi")
        optIn.add("kotlinx.cinterop.BetaInteropApi")
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "GugaartShell"
            isStatic = true
        }
    }
    sourceSets {
        iosMain {
            kotlin.srcDir(generateBuildInfo)
            // 模拟器验收的进程内运行器（B5，docs/acceptance-ios.md）：只在 -Pgugaart.accept=true（Xcode 的 Debug 构建）时编进来；
            // Release / IPA 里是空的 src/acceptOff（启动参数认不出来，代码根本不在包里）
            kotlin.srcDir(if (providers.gradleProperty("gugaart.accept").orNull == "true") "src/acceptMain/kotlin" else "src/acceptOff/kotlin")
            dependencies {
                implementation(project(":ui"))
                // 播放内核（M5-1）：上游 :player-apple 的 iOS 一半（libmpv 进程内；MPVKit 的静态库由 Xcode 工程链进应用）
                implementation(project(":player-apple"))
            }
        }
        iosTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// 壳的单测在模拟器上跑（`:shell-ios:iosSimulatorArm64Test`）；`-Pgugu.ios.simulator=<udid 或名字>` 选模拟器（和上游同一个属性）
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    providers.gradleProperty("gugu.ios.simulator").orNull?.let { device.set(it) }
}
