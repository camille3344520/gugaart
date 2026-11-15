// gugaart · 咕嘎画报 · EMBY（咕咕嘎嘎播放器的新界面）界面层。架构见 docs/adr/0001-architecture.md。
//
// 复用方式 = 「源码挂载」：播放器 / 弹幕 / Emby SDK / 液态玻璃引擎 / 无界面逻辑不拷贝，直接把上游仓库
// (gugugagaemby/native) 的模块当成本构建的子工程（projectDir 指过去），构建输出改道到本仓库的 build/upstream/，
// 所以这里的构建【不往上游目录写任何东西】，也不会和上游自己的构建抢同一个 build 目录。
// 用 scripts/gw.mjs 运行（自动选 JDK、写 sdk.dir、--and-stop）。
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

/** 上游 native 目录：-Pgugaart.nativeDir=… > gradle.properties > 默认的同级主检出。发版时指向锁定提交的干净检出（upstream.lock）。 */
val nativeDir: File = run {
    val raw = providers.gradleProperty("gugaart.nativeDir").orNull?.trim()?.takeIf { it.isNotEmpty() } ?: "../gugugagaemby/native"
    val f = File(raw).let { if (it.isAbsolute) it else File(rootDir, raw) }.canonicalFile
    require(File(f, "gradle/libs.versions.toml").isFile) {
        "找不到上游 native 工程：$f（用 -Pgugaart.nativeDir=<gugugagaemby/native 的路径> 指定，见 docs/upstream-sync.md）"
    }
    f
}
gradle.extra["gugaartNativeDir"] = nativeDir

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
    // 版本对齐：直接用上游的版本目录，两边永远是同一套 Kotlin / AGP / Compose / Ktor / Media3。
    versionCatalogs {
        create("libs") { from(files(File(nativeDir, "gradle/libs.versions.toml"))) }
    }
}

rootProject.name = "gugaart"

// ---- 上游模块（只读挂载；工程路径必须和上游一致，它们的构建脚本互相写的是 project(":core") 这样的名字） ----
val upstreamModules = listOf("core", "designsystem", "danmaku", "app", "player-desktop", "player-android")
// player-android 需要 Android SDK + 它自己的 AAR 依赖；只有带 Android 壳时才挂（-Pgugaart.android=false 可关掉，只编桌面）
val withAndroid = providers.gradleProperty("gugaart.android").orNull?.trim()?.lowercase() != "false"
val mounted = upstreamModules.filter { withAndroid || it != "player-android" }
for (name in mounted) {
    include(":$name")
    project(":$name").projectDir = File(nativeDir, name)
}
gradle.extra["gugaartUpstreamModules"] = mounted
// 构建输出改道：<本仓库>/build/upstream/<模块>/。必须在各模块的构建脚本求值之前设好。
gradle.beforeProject {
    if (name in mounted && projectDir.canonicalFile.startsWith(nativeDir)) {
        layout.buildDirectory.set(File(rootDir, "build/upstream/$name"))
    }
}

// ---- 本仓库自己的模块：只有界面层和壳 ----
include(":ui")            // 三端共用的「咕嘎画报」界面（KMP：desktop JVM + Android）
include(":shell-desktop") // Windows 壳
if (withAndroid) include(":shell-android") // Android 壳：mobile / tv 两个 flavor
