plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
}

// 版本号只在 gradle.properties 的 gugaart.version（x.y.z）。
val gugaartVersion: String = (findProperty("gugaart.version") as String?)?.trim().orEmpty()
require(Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}""").matches(gugaartVersion)) { "gugaart.version 必须是 x.y.z，现在是 '$gugaartVersion'" }
extra["gugaartVersion"] = gugaartVersion
extra["gugaartVersionCode"] = gugaartVersion.split('.').let { (a, b, c) -> a.toInt() * 10000 + b.toInt() * 100 + c.toInt() }

@Suppress("UNCHECKED_CAST")
val upstreamModules = gradle.extra["gugaartUpstreamModules"] as List<String>

subprojects {
    tasks.withType<Test>().configureEach {
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
    // 上游模块的测试属于上游仓库（它们要 Mock、金样目录、图标源目录），在那边跑；这里不跑，免得拿错路径得出假结论。
    // 例外（开发开关，默认关）：-Pgugaart.upstreamTests=app,core 让点名的上游模块的测试在这边跑——给在上游分支上改了上游模块、
    // 又没法在本机跑上游自己那套构建的情况用（Mac 上的 :player-apple 分支改 :app 的回退链）。只跑不依赖 Mock / 金样的测试类（--tests）。
    val upstreamTestsAllowed = (findProperty("gugaart.upstreamTests") as String?).orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (name in upstreamModules && name !in upstreamTestsAllowed) {
        tasks.withType<Test>().configureEach {
            onlyIf("上游模块的测试在 gugugagaemby/native 里跑（docs/upstream-sync.md）") { false }
        }
    }
}

// 当前挂载的上游提交 vs upstream.lock（只读 git，不联网）：node scripts/upstream.mjs
