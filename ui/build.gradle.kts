import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :ui —— 「咕嘎画报」界面层，三端共用（docs/adr/0001-architecture.md §4）。
// 只放界面：主题 token、画报组件、各界面。逻辑（会话、设置、查询缓存、播放控制、弹幕、Emby 接口）全部来自上游的 :app / :core / :danmaku，
// 玻璃来自上游的 :designsystem。这里【不】写平台分支，只按 UiMode { Phone, Desktop, Tv } 分版式。
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.android.kmp.library)
}

val iosTargets = gradle.extra.properties["gugaartIos"] == true

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    android {
        namespace = "com.gugaart.ui"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdkLibs.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        androidResources { enable = true }
        withHostTest {}
    }
    // M3-3：iOS（iPhone + Apple 芯片的模拟器）。开不开由 settings.gradle.kts 定（macOS 主机 + 上游有 iOS 目标）；jvmShared 不挂到 iOS。
    if (iosTargets) {
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonMain.dependencies {
            // 上游：AppGraph / 会话 / 设置 / 导航模型 / 查询缓存 / 图片加载 / PlayController；api 把 :designsystem :core :danmaku 一起带出来
            api(project(":app"))
            implementation(libs.compose.components.resources)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        // 桌面和安卓共用的 JVM 代码（不是界面分支）：弹幕中转在本机回环上的小 HTTP 服务（danmaku/LoopbackRelayHost.kt）
        val jvmShared by creating { dependsOn(commonMain.get()) }
        getByName("desktopMain").dependsOn(jvmShared)
        getByName("androidMain").dependsOn(jvmShared)
        // iOS：上面手写了 dependsOn，默认的层级模板就不套了，iosMain / iosTest 也手写（两个 iOS 目标共用）
        if (iosTargets) {
            val iosMain by creating { dependsOn(commonMain.get()) }
            getByName("iosArm64Main").dependsOn(iosMain)
            getByName("iosSimulatorArm64Main").dependsOn(iosMain)
            val iosTest by creating { dependsOn(commonTest.get()) }
            getByName("iosArm64Test").dependsOn(iosTest)
            getByName("iosSimulatorArm64Test").dependsOn(iosTest)
        }
        getByName("desktopTest").dependencies {
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
            implementation(libs.junit)
        }
    }
}

// 资源 = 自己的 composeResources + 宋体子集（third_party/fonts/noto-serif-sc/subset → files/fonts/）。
// 字体只在 third_party/ 里存一份（带着授权和收录表），构建时合进资源目录；docs/decisions.md Q3。
val gugaResources = tasks.register<Sync>("gugaResources") {
    from(layout.projectDirectory.dir("src/commonMain/composeResources"))
    from(rootProject.layout.projectDirectory.dir("third_party/fonts/noto-serif-sc/subset")) {
        include("*.ttf", "OFL.txt")
        into("files/fonts")
    }
    into(layout.buildDirectory.dir("gugaResources/commonMain"))
}

compose.resources {
    customDirectory("commonMain", layout.dir(gugaResources.map { it.destinationDir }))
    publicResClass = true
    packageOfResClass = "com.gugaart.ui.res"
    generateResClass = always
}

tasks.withType<Test>().configureEach {
    // 界面测试才出截图：-Pgugaart.shots=<目录>
    providers.gradleProperty("gugaart.shots").orNull?.let { systemProperty("gugaart.shots", it) }
    // 带 Mock 的集成测试：不给就 SKIPPED（和上游同一个约定）
    providers.gradleProperty("gugu.mock.url").orNull?.let { systemProperty("gugu.mock.url", it) }
    // 用户真实服务器的离屏实拍（只读；运行时读仓库根的 .env）：默认不跑，-Pgugaart.realEmby=true 才跑
    providers.gradleProperty("gugaart.realEmby").orNull?.let { systemProperty("gugaart.realEmby", it) }
    // 换一个数据包（README 截图用 scripts/art/mock-pack.py 生成的全虚构数据包）：-Pgugaart.fixture=<目录>
    providers.gradleProperty("gugaart.fixture").orNull?.let { systemProperty("gugaart.fixture", it) }
    maxHeapSize = "2g"
    // 每 6 个测试类换一个新的测试 JVM（用户 2026-11-15：一个测试 JVM 涨到 16 GB，把机器 OOM 了）。
    // 堆只有 2 GB，涨的是本机内存：每一帧离屏渲染的整屏快照 / 位图是 Skia 的本机内存，Java 堆几乎不长、垃圾回收很少跑，
    // 全量 40 多个类、几百个测试在同一个 JVM 里跑完会一直攒着。换 JVM = 这些本机内存整块还给系统；多出的启动开销每次几秒。
    forkEvery = 6
}
