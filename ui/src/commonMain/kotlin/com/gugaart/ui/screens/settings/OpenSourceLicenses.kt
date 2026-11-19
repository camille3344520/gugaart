package com.gugaart.ui.screens.settings

import com.gugugaga.ds.theme.UiMode

/*
 * 设置 › 关于 › 「开源许可」那一页的内容（D-lead-41）：这一版**随包分发**的第三方组件和它们的许可证。纯数据，有单测。
 *
 * 和随包的声明是同一份事实：Windows 安装包 / macOS 应用里的 `NOTICE.md`（源文件 `shell-desktop/packaging/NOTICE.md`）。
 * 改了这里的组件或许可证，`NOTICE.md` 要跟着改，反过来也一样（`OpenSourceLicensesTest` 逐行对 macOS 内核表和运行库表）。
 * 安卓、iPhone 包里没有 `NOTICE.md`：那两段按构建脚本里实际带的依赖写（上游 `player-android` / `player-apple`）。
 */

/** 一行：组件、许可证、一句出处（版本 · 上游；GPL 类的源码就从这里取）。 */
data class LicenseEntry(val name: String, val license: String, val note: String? = null)

/** 一组（本应用 / 播放内核 / 运行库与组件）：[note] = 这一组整体怎么分发、源码怎么取。 */
data class LicenseGroup(val title: String, val english: String, val entries: List<LicenseEntry>, val note: String? = null)

/** 按哪一个安装包列（每个包只列它自己带的）。 */
enum class LicensePlatform { Windows, MacOS, Ios, Android }

object OpenSourceLicenses {
    /** `Route.Settings` 的这一段：不是九个分区之一，是「关于」下面的一页（[GugaSection.of] 认不出它）。 */
    const val KEY = "licenses"

    /** 本应用（界面层）的源码。 */
    const val APP_SOURCE = "github.com/camille3344520/gugaart"

    /** 这台设备是哪个包：先看平台标签（壳给的），认不出（离屏测试的「test」）按界面模式：电脑 = Windows（宿主说是 macOS 就是 macOS），手机 / 电视 = 安卓。 */
    fun platformOf(platformLabel: String, uiMode: UiMode, macHost: Boolean): LicensePlatform = when {
        platformLabel.startsWith("macOS") -> LicensePlatform.MacOS
        platformLabel.startsWith("iOS") -> LicensePlatform.Ios
        platformLabel.startsWith("Android") -> LicensePlatform.Android
        platformLabel.startsWith("Windows") -> LicensePlatform.Windows
        uiMode == UiMode.Desktop -> if (macHost) LicensePlatform.MacOS else LicensePlatform.Windows
        else -> LicensePlatform.Android
    }

    /** 页首那一句。 */
    const val INTRO = "下面是这一版随包分发的第三方组件和它们的许可证。标着 GPL / LGPL 的，对应的源码在每一行写的上游取得。"

    /** 页尾：完整声明在哪（只有电脑的包里有那个文件）。 */
    fun footer(p: LicensePlatform): String? = when (p) {
        LicensePlatform.Windows -> "完整声明在安装目录的 app/resources/NOTICE.md。"
        LicensePlatform.MacOS -> "完整声明（含把静态库链成 libmpv.dylib 的命令）在 咕咕嘎嘎.app/Contents/app/resources/NOTICE.md。"
        else -> null
    }

    /**
     * 这个包的各组。[iosPlayer]：iPhone 的包带不带播放内核（壳给了 `PlayerPlatform` 才算带；没带就不列内核那一组）。
     */
    fun groups(p: LicensePlatform, iosPlayer: Boolean = false): List<LicenseGroup> = listOfNotNull(
        LicenseGroup("本应用", "THIS APP", listOf(LicenseEntry("咕嘎画报 · EMBY", "GPL-3.0", "界面层源码 · $APP_SOURCE"))),
        engine(p, iosPlayer),
        LicenseGroup("运行库与组件", "RUNTIME", runtime(p)),
    )

    private fun engine(p: LicensePlatform, iosPlayer: Boolean): LicenseGroup? = when (p) {
        LicensePlatform.Windows -> LicenseGroup(
            "播放内核", "ENGINE",
            listOf(
                LicenseEntry("mpv", "GPL-2.0-or-later（以 -Dgpl=false 构建的是 LGPL-2.1-or-later）", "mpv.io · 源码 github.com/mpv-player/mpv"),
                LicenseEntry("FFmpeg", "LGPL-2.1-or-later / GPL-2.0-or-later", "ffmpeg.org"),
                LicenseEntry("libass", "ISC", "github.com/libass/libass"),
                LicenseEntry("libplacebo", "LGPL-2.1-or-later", "code.videolan.org/videolan/libplacebo"),
                LicenseEntry("dav1d", "BSD-2-Clause", "code.videolan.org/videolan/dav1d"),
            ),
            note = "安装包在 resources/mpv/ 里带一份未经修改的 mpv.exe 和它自带的 DLL，应用把它当独立程序启动。" +
                "所带二进制的确切条款以它的构建来源为准，见同一目录的 BUNDLE.json（source、version、sha256）。",
        )
        LicensePlatform.MacOS -> LicenseGroup(
            "播放内核", "ENGINE", MPVKIT,
            note = "应用包里的 libmpv.dylib 由 MPVKit 1.0.0（GPL 构建）各仓库正式发布的静态库链成，整体按 GNU GPL 第 3 版或更新版本分发" +
                "（和本应用相同）。二进制出自 github.com/mpvkit 下各仓库的正式发布；各库的源码在每一行写的上游取得。",
        )
        LicensePlatform.Ios -> if (!iosPlayer) null else LicenseGroup(
            "播放内核", "ENGINE", MPVKIT.filter { it.name !in NOT_IN_IOS },
            note = "链进应用的 libmpv 来自 MPVKit 1.0.0（GPL 构建），整体按 GNU GPL 第 3 版或更新版本分发（和本应用相同）。" +
                "二进制出自 github.com/mpvkit 下各仓库的正式发布；各库的源码在每一行写的上游取得。",
        )
        // 安卓：Windows Pi 2026-11-19 按 Gradle 缓存里的 POM / AAR 和 APK 实带的 19 个 .so 核过（D-lead-41）
        LicensePlatform.Android -> LicenseGroup(
            "播放内核", "ENGINE",
            listOf(
                LicenseEntry("Media3 ExoPlayer", "Apache-2.0", "github.com/androidx/media"),
                LicenseEntry("NextLib", "GPL-3.0", "ExoPlayer 的 FFmpeg 解码器 · github.com/anilbeesetti/nextlib"),
                // 许可证那一栏写整体按什么分发；为什么写在出处那一行（栏窄，长句放左边）
                LicenseEntry(
                    "libmpv-android", "GPL-3.0-or-later",
                    "libmpv.so 整体按 GPL-3.0-or-later 分发：封装 MIT，mpv 0.41 是 LGPL-2.1-or-later，但同包的 FFmpeg n8.1 以 " +
                        "--enable-gpl --enable-version3 构建 · dev.jdtech.mpv:libmpv 1.0.0 · github.com/jarnedemeulemeester/libmpv-android",
                ),
                LicenseEntry(
                    "libVLC", "LGPL-2.1-or-later",
                    "整体含 GPL-2.0-or-later 组件：libvlc.so 里静态带 a52dec（liba52）、faad2（GPL-2.0-or-later）和 GnuTLS / Nettle / Hogweed / GMP（LGPL）；" +
                        "其中的 FFmpeg 4.4.5 是 LGPL 构建 · libvlc-all 3.6.2 · code.videolan.org/videolan/vlc-android",
                ),
                LicenseEntry("FFmpeg（libmpv 带的）", "GPL-3.0-or-later", "n8.1，静态在 libmpv.so 里 · ffmpeg.org"),
                LicenseEntry("FFmpeg（NextLib 带的）", "LGPL-3.0-or-later", "9.0.1，改名的 libnl*.so · ffmpeg.org"),
                LicenseEntry("libplacebo", "LGPL-2.1-or-later", "静态在 libmpv.so 里 · code.videolan.org/videolan/libplacebo"),
                LicenseEntry("FriBidi", "LGPL-2.1-or-later", "1.0.16（NextLib） · github.com/fribidi/fribidi"),
                LicenseEntry("a52dec（liba52）、faad2", "GPL-2.0-or-later", "静态在 libvlc.so 里 · code.videolan.org/videolan/vlc-android"),
                LicenseEntry("GnuTLS", "LGPL-2.1-or-later", "gnutls.org"),
                LicenseEntry("Nettle、Hogweed", "LGPL-3.0-or-later 或 GPL-2.0-or-later", "lysator.liu.se/~nisse/nettle"),
                LicenseEntry("GMP", "LGPL-3.0-or-later 或 GPL-2.0-or-later", "gmplib.org"),
                LicenseEntry("Mbed TLS", "Apache-2.0 或 GPL-2.0-or-later", "github.com/Mbed-TLS/mbedtls"),
                LicenseEntry("FreeType", "FreeType License（FTL）或 GPL-2.0", "freetype.org"),
                LicenseEntry("libass", "ISC", "github.com/libass/libass"),
                LicenseEntry("dav1d", "BSD-2-Clause", "code.videolan.org/videolan/dav1d"),
                LicenseEntry("其他宽松许可组件", "MIT / zlib 等宽松许可", "fontconfig、HarfBuzz、expat、libunibreak、libxml2"),
            ),
        )
    }

    /**
     * iPhone 应用里实际没有的 MPVKit 库（静态链接只拉进被引用的目标文件；按 Release 包的符号表核对，2026-11-19）：
     * LuaJIT（这份 mpv 不带 Lua，一个 `lua_*` 都没有）、OpenSSL（FFmpeg 的 TLS 走 GnuTLS，`SSL_*` / `EVP_*` / `CRYPTO_*` 一个都没被拉进来）。
     * 其余 20 个都在（mpv、FFmpeg 各库、libass / FreeType / FriBidi / HarfBuzz / libunibreak、libplacebo、MoltenVK、shaderc、lcms2、
     * libdovi、dav1d、GnuTLS / Nettle / Hogweed / GMP、libsmbclient、libbluray、uavs3d、uchardet）。
     */
    val NOT_IN_IOS = setOf("LuaJIT", "OpenSSL（libssl、libcrypto）") // OpenSSL 在 macOS 的包里也没有，已不在 MPVKIT 里（留着这一项不碍事）

    /**
     * macOS 应用的 `libmpv.dylib` 里实际有的 MPVKit 1.0.0（GPL 构建）各库：和 `NOTICE.md`「macOS：随包的 libmpv」那张表逐行一致。
     * 按随包 dylib 的符号表核过（2026-11-19）：OpenSSL 不在（FFmpeg 的 TLS 走 GnuTLS，`-dead_strip` 后没有一个 `SSL_*` / `EVP_*` / `CRYPTO_*`），
     * LuaJIT 在（`lua_*` / `lj_*` 都有）。
     */
    val MPVKIT: List<LicenseEntry> = listOf(
        LicenseEntry("mpv（libmpv）", "GPL-2.0-or-later（GPL 构建）", "0.41.0 + MPVKit 的补丁 · mpv.io"),
        LicenseEntry("FFmpeg", "GPL-2.0-or-later（GPL 构建；部分文件 LGPL-2.1-or-later）", "n8.1.2 · ffmpeg.org"),
        LicenseEntry("libass", "ISC", "0.17.5 · github.com/libass/libass"),
        LicenseEntry("FreeType", "FreeType License（FTL）", "libass-build 0.17.5 · freetype.org"),
        LicenseEntry("FriBidi", "LGPL-2.1-or-later", "libass-build 0.17.5 · github.com/fribidi/fribidi"),
        LicenseEntry("HarfBuzz", "MIT（Old MIT）", "libass-build 0.17.5 · harfbuzz.github.io"),
        LicenseEntry("libunibreak", "zlib", "libass-build 0.17.5 · github.com/adah1972/libunibreak"),
        LicenseEntry("libplacebo", "LGPL-2.1-or-later", "7.360.1 · code.videolan.org/videolan/libplacebo"),
        LicenseEntry("MoltenVK", "Apache-2.0", "1.4.2 · github.com/KhronosGroup/MoltenVK"),
        LicenseEntry("shaderc（含 glslang、SPIRV-Tools）", "Apache-2.0（glslang 另有 BSD-3-Clause 等）", "2025.5.0 · github.com/google/shaderc"),
        LicenseEntry("Little CMS 2", "MIT", "2.17.0 · littlecms.com"),
        LicenseEntry("libdovi", "MIT", "3.3.2 · github.com/quietvoid/dovi_tool"),
        LicenseEntry("dav1d", "BSD-2-Clause", "1.5.3 · code.videolan.org/videolan/dav1d"),
        LicenseEntry("GnuTLS", "LGPL-2.1-or-later", "3.8.11 · gnutls.org"),
        LicenseEntry("Nettle、Hogweed", "LGPL-3.0-or-later 或 GPL-2.0-or-later", "gnutls-build 3.8.11 · lysator.liu.se/~nisse/nettle"),
        LicenseEntry("GMP", "LGPL-3.0-or-later 或 GPL-2.0-or-later", "gnutls-build 3.8.11 · gmplib.org"),
        LicenseEntry("libsmbclient（Samba）", "GPL-3.0-or-later", "4.15.13 · samba.org"),
        LicenseEntry("libbluray", "LGPL-2.1-or-later", "1.4.0 · videolan.org/developers/libbluray.html"),
        LicenseEntry("uavs3d", "BSD-3-Clause", "1.2.1 · github.com/uavs3/uavs3d"),
        LicenseEntry("uchardet", "MPL-1.1 或 GPL-2.0-or-later 或 LGPL-2.1-or-later", "0.0.8 · freedesktop.org/wiki/Software/uchardet"),
        LicenseEntry("LuaJIT", "MIT", "2.1.0 · luajit.org"),
    )

    /** 运行库与组件：和 `NOTICE.md`「安装目录 app/ 与 runtime/ 里的运行时和库」那张表一致，按包取舍（安卓 / iPhone 没有 Java 运行时、JNA）。 */
    private fun runtime(p: LicensePlatform): List<LicenseEntry> {
        val desktop = p == LicensePlatform.Windows || p == LicensePlatform.MacOS
        return listOfNotNull(
            LicenseEntry("Java 运行时", "GPL-2.0 with the Classpath Exception", "OpenJDK，jlink 裁剪 · openjdk.org").takeIf { desktop },
            LicenseEntry(
                "运行时随带的图形库",
                "各按其许可证",
                "FreeType（FTL 或 GPL-2.0）；HarfBuzz、Brotli、Little CMS 2、GIFLIB（MIT）；GLib、libintl、Graphite2（LGPL-2.1-or-later）；" +
                    "PCRE2（BSD-3-Clause）；libpng（libpng License）；libjpeg-turbo（IJG / BSD-3-Clause）",
            ).takeIf { p == LicensePlatform.MacOS },
            LicenseEntry("Kotlin 标准库、kotlinx.coroutines / serialization", "Apache-2.0"),
            if (p == LicensePlatform.Android) LicenseEntry("Jetpack Compose、AndroidX", "Apache-2.0")
            else LicenseEntry("Compose Multiplatform、Skiko（Skia 绑定）", "Apache-2.0（Skia：BSD-3-Clause）"),
            LicenseEntry(if (p == LicensePlatform.Ios) "Ktor client、Okio" else "Ktor client、OkHttp、Okio", "Apache-2.0"),
            LicenseEntry("JNA", "Apache-2.0 或 LGPL-2.1-or-later（双许可）", "github.com/java-native-access/jna").takeIf { desktop },
            LicenseEntry("Coil、Kermit、multiplatform-settings、Kyant backdrop / shapes", "Apache-2.0"),
            LicenseEntry(
                "液态玻璃着色器",
                "MIT",
                "移植自 liquid_glass_widgets（Copyright (c) 2024–2026 Sebastian Degenaar）、liquid_glass_renderer（Copyright 2025 Tim Lehmann for whynotmake.it）",
            ),
            LicenseEntry("Lucide 图标", "ISC", "矢量路径 · lucide.dev"),
            LicenseEntry("思源宋体子集 Noto Serif SC", "SIL Open Font License 1.1", "界面字体"),
        )
    }
}
