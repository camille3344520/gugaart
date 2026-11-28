// :shell-android —— Android 壳：mobile（手机，minSdk 33）/ tv（电视，minSdk 28）两个 flavor。
// 只做三件事：造上游的 PlatformDeps / AppGraph、起 Activity、调用 :ui 的 GugaartApp。
//
// 播放：引擎宿主的接法复制自上游壳的 MainActivity（docs/adr/0002 §6，登记在 docs/upstream-sync.md §5.1）；引擎本身是上游的 :player-android。
//
// 发版（docs/release.md）：
//   - release 用专用证书签名，找不到就失败，不退回 debug 签名；
//   - verifyReleaseConfig 守着 release 的 BuildConfig（没有 Mock 账号）和手机 minSdk；
//   - 图标 / 电视横幅由 scripts/art/make-icons.py 从报头徽记 G03 生成；
//   - R8 不开（和上游默认一致）；
//   - 应用内更新暂不做（docs/decisions.md）。
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// debug 包的 Mock 自动登录（和上游同一个约定）：-Pgugaart.mockAccount=http://<主机>:<端口> → 只进 debug 的 BuildConfig.MOCK_ACCOUNT。
// 谷歌 AVD 上宿主机是 10.0.2.2。账号是 tools/mock-emby 的夹具（demo / demo），从不是真实凭据；release 的这个字段永远是空串。
val mockAccountUrl: String? = providers.gradleProperty("gugaart.mockAccount").orNull?.trim()?.takeIf { it.isNotEmpty() }
if (mockAccountUrl != null) require(Regex("""^https?://[^\s"\\]+$""").matches(mockAccountUrl)) { "-Pgugaart.mockAccount 必须是 http(s) 地址：$mockAccountUrl" }
// 1.5.0 跨服务器验收：-Pgugaart.mockServers=<地址>,<地址> → 同一个 debug 字段里多一个 servers 数组（只和 mockAccount 一起用；上游解析时忽略它，
// 画报的 DevMockServers 读它，把几台 Mock 存成几台服务器）。release 照旧是空串。
// 调试用的「逐集核对」（ui/.../debug/MatchAudit.kt）：只有 -Pgugaart.audit=true 编出来的包才认启动参数 gugaart.audit；正式发版不带
val auditOn: Boolean = providers.gradleProperty("gugaart.audit").orNull?.trim() == "true"
val mockServerUrls: List<String> = providers.gradleProperty("gugaart.mockServers").orNull?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
mockServerUrls.forEach { require(Regex("""^https?://[^\s"\\]+$""").matches(it)) { "-Pgugaart.mockServers 必须是 http(s) 地址：$it" } }
val mockServersJson: String = if (mockServerUrls.isEmpty()) "" else mockServerUrls.joinToString(",", prefix = ""","servers":[""", postfix = "]") { "\"$it\"" }
val mockAccountJson: String = if (mockAccountUrl == null) "" else """{"server":"$mockAccountUrl","username":"demo","password":"demo"$mockServersJson}"""
fun javaLiteral(v: String): String = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// ---- release 签名：gugaart 专用证书（2026-11-14 生成，在仓库外）----
// 格式和上游一样是 keystore.properties：storeFile / storePassword / keyAlias / keyPassword；storeFile 相对 properties 文件所在目录。
// 按这个顺序找：-Pgugaart.keystoreProps=<文件> → 环境变量 GUGAART_KEYSTORE_PROPS → 仓库根 .env 的 GUGAART_KEYSTORE_PROPS。
// 只读这一个键，路径和文件内容都不打印。找不到时 debug 照常，release 打包任务失败（verifyReleaseConfig）。
class ReleaseKeystore(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

fun envFileValue(key: String): String? {
    val f = rootProject.file(".env").takeIf { it.isFile } ?: return null
    val re = Regex("""^\s*${Regex.escape(key)}\s*=\s*(.*)$""")
    return f.readLines().firstNotNullOfOrNull { line ->
        re.find(line)?.groupValues?.get(1)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")?.takeIf { it.isNotEmpty() }
    }
}

fun loadReleaseKeystore(): Pair<ReleaseKeystore?, String?> {
    val path = providers.gradleProperty("gugaart.keystoreProps").orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.environmentVariable("GUGAART_KEYSTORE_PROPS").orNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: envFileValue("GUGAART_KEYSTORE_PROPS")
        ?: return null to "没有设置 GUGAART_KEYSTORE_PROPS（.env / 环境变量 / -Pgugaart.keystoreProps）"
    val propsFile = File(path)
    if (!propsFile.isFile) return null to "GUGAART_KEYSTORE_PROPS 指向的文件不存在"
    val p = Properties().apply { propsFile.inputStream().use { load(it) } }
    val store = p.getProperty("storeFile")?.trim()?.takeIf { it.isNotEmpty() }?.let { File(it).let { f -> if (f.isAbsolute) f else File(propsFile.parentFile, it) } }
    val sp = p.getProperty("storePassword")
    val alias = p.getProperty("keyAlias")
    val kp = p.getProperty("keyPassword")
    if (store == null || !store.isFile || sp.isNullOrEmpty() || alias.isNullOrEmpty() || kp.isNullOrEmpty()) {
        return null to "keystore.properties 不完整，或 storeFile 不存在（内容不打印）"
    }
    return ReleaseKeystore(store, sp, alias, kp) to null
}

val (releaseKeystore, keystoreProblem) = loadReleaseKeystore()

android {
    namespace = "com.gugaart.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        // 新应用自己的包名，和上游原生版（com.gugugaga.emby[.tv]）并存；正式定名是主理人的决定（docs/open-questions.md Q1）
        applicationId = "com.gugugaga.art"
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = rootProject.extra["gugaartVersionCode"] as Int
        versionName = rootProject.extra["gugaartVersion"] as String
        // Trakt 应用（用户 2026-11-15：自己注册，Client ID / Secret 放 .env，正式包内嵌）；没有 = 空 = 这一版不能连 Trakt
        buildConfigField("boolean", "AUDIT", auditOn.toString())
        buildConfigField("String", "TRAKT_CLIENT_ID", javaLiteral(providers.environmentVariable("TRAKT_CLIENT_ID").orNull?.trim()?.takeIf { it.isNotEmpty() } ?: envFileValue("TRAKT_CLIENT_ID").orEmpty()))
        buildConfigField("String", "TRAKT_CLIENT_SECRET", javaLiteral(providers.environmentVariable("TRAKT_CLIENT_SECRET").orNull?.trim()?.takeIf { it.isNotEmpty() } ?: envFileValue("TRAKT_CLIENT_SECRET").orEmpty()))
        // 和上游一致：libmpv 带的 ABI（去掉 x86 省 15 MB）
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore.storeFile
                storePassword = releaseKeystore.storePassword
                keyAlias = releaseKeystore.keyAlias
                keyPassword = releaseKeystore.keyPassword
                if (releaseKeystore.storeFile.extension.equals("p12", ignoreCase = true)) storeType = "PKCS12"
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    flavorDimensions += "device"
    productFlavors {
        create("mobile") {
            dimension = "device"
            minSdk = (project.findProperty("gugaart.mobileMinSdk") as String?)?.toInt() ?: libs.versions.androidMinSdkMobile.get().toInt()
            buildConfigField("String", "DEVICE_FLAVOR", "\"mobile\"")
        }
        create("tv") {
            dimension = "device"
            minSdk = libs.versions.androidMinSdkTv.get().toInt()
            applicationIdSuffix = ".tv"
            buildConfigField("String", "DEVICE_FLAVOR", "\"tv\"")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "MOCK_ACCOUNT", javaLiteral(mockAccountJson))
            ndk { abiFilters += "x86" }
        }
        release {
            buildConfigField("String", "MOCK_ACCOUNT", "\"\"")
            isMinifyEnabled = false
            isShrinkResources = false
            // 没有专用证书就不签：verifyReleaseConfig 让打包任务失败，绝不退回 debug 签名
            signingConfig = if (releaseKeystore != null) signingConfigs.getByName("release") else null
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs.useLegacyPackaging = true
        // dex 压缩存放（1.5.1 perf-win4）：classes*.dex 约 42 MB 以前不压缩（STORED）占着 APK，压缩后省约 26 MB；
        // 安装时系统照旧解到磁盘，运行期加载不受影响。只动这一项打包设置：不去 x86_64、不开 R8、不拆 ABI。
        dex.useLegacyPackaging = true
        // libmpv 和 libVLC 都带 libc++_shared.so，赢的必须是最新的那份（libmpv 的，见下面的 CxxSharedTask）：
        // VLC 那份旧，没有 std::to_chars(float)，libmpv 会加载失败（阶段三在模拟器上实测到过：mpv 直接出错页）
        jniLibs.pickFirsts += listOf("**/libc++_shared.so")
        resources.excludes += setOf("META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties", "META-INF/*.kotlin_module")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":ui"))
    // 上游的 Android 播放：ExoPlayer / VLC / mpv 引擎、视频层、预加载
    implementation(project(":player-android"))
    implementation(libs.androidx.activity.compose)
    // 1.6 B3：baseline profile 的安装器（src/main/baseline-prof.txt；协调人批准加这个依赖，版本在上游的版本目录）
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}

// ---- 所有原生库共用的 C++ 运行时：用 libmpv 那一份，从应用模块自己的 jniLibs 里给（和上游壳 androidApp/build.gradle.kts 同一条规则）----
// 应用模块自己的 jniLibs 在合并原生库时排第一，所以 pickFirst 取到的是这一份，而不是碰巧排在前面的哪个 AAR。
// 新的 libc++_shared 向后兼容（NDK 的规则：带上依赖里要求最新的那份）。
val libmpvAar: Configuration = configurations.create("libmpvAar") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
dependencies { add(libmpvAar.name, "dev.jdtech.mpv:libmpv:${libs.versions.libmpv.get()}@aar") }

abstract class CxxSharedTask : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NONE) abstract val aar: ConfigurableFileCollection
    @get:OutputDirectory abstract val jniDir: DirectoryProperty

    @TaskAction
    fun extract() {
        val out = jniDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        var n = 0
        ZipFile(aar.singleFile).use { zip ->
            for (e in zip.entries()) {
                if (e.isDirectory || !e.name.startsWith("jni/") || !e.name.endsWith("/libc++_shared.so")) continue
                val abi = e.name.split('/')[1]
                zip.getInputStream(e).use { inp -> File(out, "$abi/libc++_shared.so").apply { parentFile.mkdirs() }.outputStream().use { inp.copyTo(it) } }
                n++
            }
        }
        if (n == 0) throw GradleException("libmpv 的 AAR 里没有 libc++_shared.so 了：升级 libmpv 之前先查")
    }
}

val cxxShared = tasks.register<CxxSharedTask>("cxxSharedFromLibmpv") {
    aar.from(libmpvAar)
    jniDir.set(layout.buildDirectory.dir("generated/cxxShared"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(cxxShared, CxxSharedTask::jniDir)
    }
}

// ---- 发版闸门：release 打包之前检查（docs/release-standard.md S1–S3）----
//   1. 有专用证书（没有就失败，绝不出 debug 签名的 release）；
//   2. 两个 flavor 的 release BuildConfig 里 MOCK_ACCOUNT 是空串（Mock 自动登录只进 debug）；
//   3. 手机 minSdk 是 33（-Pgugaart.mobileMinSdk=28 只为夜神看布局，不能进发布物）。
// 产物里的密钥扫描在 scripts/release.mjs（把 .env 的每个值在 APK / 安装包解开后的全部内容里搜一遍）。
val releaseMobileMinSdk = (project.findProperty("gugaart.mobileMinSdk") as String?)?.toInt() ?: libs.versions.androidMinSdkMobile.get().toInt()
val verifyReleaseConfig = tasks.register("verifyReleaseConfig") {
    group = "verification"
    description = "发版闸门：专用证书、release BuildConfig 没有 Mock 账号、手机 minSdk 33"
    dependsOn("generateMobileReleaseBuildConfig", "generateTvReleaseBuildConfig")
    val problem = keystoreProblem
    val minSdk = releaseMobileMinSdk
    val genRoot = layout.buildDirectory.dir("generated/source/buildConfig")
    doLast {
        if (problem != null) throw GradleException("release 不能打包：$problem。见 docs/release.md")
        if (minSdk != 33) throw GradleException("release 的手机 minSdk 必须是 33，现在是 $minSdk（去掉 -Pgugaart.mobileMinSdk）")
        for (variant in listOf("mobile/release", "tv/release")) {
            val f = genRoot.get().dir(variant).asFileTree.matching { include("**/BuildConfig.java") }.singleFile
            val line = f.readLines().firstOrNull { "MOCK_ACCOUNT" in it } ?: throw GradleException("$variant 的 BuildConfig 里没有 MOCK_ACCOUNT 字段")
            if (!line.trimEnd().endsWith("MOCK_ACCOUNT = \"\";")) throw GradleException("$variant 的 BuildConfig.MOCK_ACCOUNT 不是空串：release 不能带 Mock 自动登录")
        }
        logger.lifecycle("verifyReleaseConfig: 专用证书在；release BuildConfig 没有 Mock 账号（2 个 flavor）；手机 minSdk 33")
    }
}
tasks.matching { Regex("^(assemble|bundle|package)(Mobile|Tv)Release$").matches(it.name) }.configureEach { dependsOn(verifyReleaseConfig) }
