# 第三方声明（咕嘎画报 · EMBY，Windows 安装包 / macOS 应用）

打包时这个文件被复制到安装目录的 `app/resources/NOTICE.md`（Windows）/ `咕咕嘎嘎.app/Contents/app/resources/NOTICE.md`（macOS），
由 `shell-desktop/build.gradle.kts` 的 `prepareMpv` 放进去。下面「mpv（随包的播放内核）」一节只说 Windows 安装包；macOS 应用随包的内核
见「macOS：随包的 libmpv（MPVKit 1.0.0，GPL 构建）」一节。

## mpv（随包的播放内核）

安装包在 `resources/mpv/` 里带一份**未经修改**的 `mpv.exe` 和它自带的 DLL。

- 应用把它当独立进程启动（`mpv.exe --wid=<宿主窗口> --input-ipc-server=…`），不链接、不修改它，属于与应用并列分发的独立程序（aggregate）。
- 不带也不读任何 mpv 用户配置（`mpv.conf`、`input.conf`、`portable_config`、脚本、着色器）：应用总是以 `--no-config` 启动 mpv。

**许可证**

- 项目：https://mpv.io/ ，源码：https://github.com/mpv-player/mpv
- mpv 默认是 GNU GPL 2.0 或更新版本；以 `-Dgpl=false` 构建的是 LGPL 2.1 或更新版本。
- 所带这份二进制的确切条款以它的构建来源为准，见 `resources/mpv/BUNDLE.json`：`source` 是来源，另有 `version`、`sha256`。
- 构建里的 FFmpeg / libass / libplacebo / dav1d 等各自沿用自己的许可证：
  - LGPL-2.1+ / GPL-2.0+、ISC、LGPL-2.1+、BSD-2-Clause；
  - 源码见各上游：https://ffmpeg.org/ 、https://github.com/libass/libass 、https://code.videolan.org/videolan/libplacebo 。

## macOS：随包的 libmpv（MPVKit 1.0.0，GPL 构建）

macOS 应用在 `Contents/app/resources/libmpv/libmpv.dylib` 带**一个**动态库：把 MPVKit 1.0.0（GPL 构建）各仓库正式发布的静态库链在一起，
只导出 libmpv 的公开 C 接口（`mpv_*`），其余（FFmpeg、libplacebo、MoltenVK、libass ……）静态链入、不对外导出；除了 macOS 系统库不依赖别的。
应用进程内经 JNA 加载它（不是子进程）。同一目录的 `BUNDLE.json` 写着它的大小、SHA-256 和来源版本。

**许可证**：构建里有 GPL 的部分（mpv 以 GPL 选项构建、FFmpeg 以 GPL 选项构建、Samba 的 libsmbclient 是 GPL-3.0-or-later），所以这个库
整体按 **GNU GPL 第 3 版或更新版本**分发——和本应用自己的许可证相同。下表每一项的对应源码都可以取得：

- 二进制的出处：<https://github.com/mpvkit> 下各仓库的正式发布（版本见下表「发布标签」；每个文件的地址和 SHA-256 钉在上游
  `native/player-apple/binaries.lock`）；MPVKit 对 mpv 的补丁在 MPVKit 1.0.0 源码的 `Sources/BuildScripts/patch/libmpv/`；
- 各库的源码：下表「上游」一栏的项目在对应版本的源码归档；MPVKit 各构建仓库的构建脚本即「控制编译和安装的脚本」；
- 把这些静态库链成 `libmpv.dylib` 的命令（上游 `native/player-apple/tools/link-libmpv-macos.sh`，只用 Xcode 自带的工具；这里写出原文，
  因为上游仓库不公开）：

```sh
xcrun clang -dynamiclib -arch arm64 -isysroot "$(xcrun --sdk macosx --show-sdk-path)" -mmacosx-version-min=12.0 \
  -o libmpv.dylib -install_name @rpath/libmpv.dylib -Wl,-rpath,/usr/lib/swift \
  -Wl,-force_load,Libmpv.xcframework/macos-arm64_x86_64/Libmpv.framework/Libmpv \
  <其余每个 xcframework 的 macOS 切片里的静态库> \
  -Wl,-exported_symbols_list,libmpv.exports.txt -Wl,-dead_strip \
  -L"$SDK/usr/lib/swift" -L"$TOOLCHAIN/lib/swift/macosx" \
  -lbz2 -lc++ -lexpat -liconv -lresolv -lxml2 -lz \
  -framework AudioToolbox -framework AVFoundation -framework CoreAudio -framework CoreFoundation -framework CoreMedia \
  -framework CoreVideo -framework Metal -framework VideoToolbox -framework AppKit -framework Cocoa -framework Foundation \
  -framework QuartzCore -framework IOKit -framework IOSurface -framework CoreGraphics -framework CoreText \
  -framework CoreServices -framework Security -framework SystemConfiguration -framework MediaPlayer -framework AudioUnit \
  -framework OpenGL -framework Carbon -framework CoreImage -framework UniformTypeIdentifiers
# libmpv.exports.txt = nm -gU Libmpv 里所有以 _mpv_ 开头的导出函数
```

| 库 | 版本（发布标签） | 许可证 | 上游 |
|---|---|---|---|
| mpv（libmpv） | 0.41.0 + MPVKit 的补丁（MPVKit 1.0.0） | GPL-2.0-or-later（GPL 构建） | <https://mpv.io/> |
| FFmpeg（libavcodec、libavdevice、libavfilter、libavformat、libavutil、libswresample、libswscale） | n8.1.2（MPVKit 1.0.0） | GPL-2.0-or-later（GPL 构建；部分文件 LGPL-2.1-or-later） | <https://ffmpeg.org/> |
| libass | 0.17.5（libass-build） | ISC | <https://github.com/libass/libass> |
| FreeType | libass-build 0.17.5 里的版本 | FreeType License（FTL） | <https://freetype.org/> |
| FriBidi | libass-build 0.17.5 里的版本 | LGPL-2.1-or-later | <https://github.com/fribidi/fribidi> |
| HarfBuzz | libass-build 0.17.5 里的版本 | MIT（Old MIT） | <https://harfbuzz.github.io/> |
| libunibreak | libass-build 0.17.5 里的版本 | zlib | <https://github.com/adah1972/libunibreak> |
| libplacebo | 7.360.1（libplacebo-build） | LGPL-2.1-or-later | <https://code.videolan.org/videolan/libplacebo> |
| MoltenVK | 1.4.2（moltenvk-build） | Apache-2.0 | <https://github.com/KhronosGroup/MoltenVK> |
| shaderc（含 glslang、SPIRV-Tools） | 2025.5.0（libshaderc-build） | Apache-2.0（glslang 另有 BSD-3-Clause 等） | <https://github.com/google/shaderc> |
| Little CMS 2 | 2.17.0（lcms2-build） | MIT | <https://www.littlecms.com/> |
| libdovi | 3.3.2（libdovi-build） | MIT | <https://github.com/quietvoid/dovi_tool> |
| dav1d | 1.5.3（libdav1d-build） | BSD-2-Clause | <https://code.videolan.org/videolan/dav1d> |
| GnuTLS | 3.8.11（gnutls-build） | LGPL-2.1-or-later | <https://gnutls.org/> |
| Nettle、Hogweed | gnutls-build 3.8.11 里的版本 | LGPL-3.0-or-later 或 GPL-2.0-or-later | <https://www.lysator.liu.se/~nisse/nettle/> |
| GMP | gnutls-build 3.8.11 里的版本 | LGPL-3.0-or-later 或 GPL-2.0-or-later | <https://gmplib.org/> |
| libsmbclient（Samba） | 4.15.13（libsmbclient-build 4.15.13-2512） | GPL-3.0-or-later | <https://www.samba.org/> |
| libbluray | 1.4.0（libbluray-build） | LGPL-2.1-or-later | <https://www.videolan.org/developers/libbluray.html> |
| uavs3d | 1.2.1（libuavs3d-build 1.2.1-fix） | BSD-3-Clause | <https://github.com/uavs3/uavs3d> |
| uchardet | 0.0.8（libuchardet-build） | MPL-1.1 或 GPL-2.0-or-later 或 LGPL-2.1-or-later | <https://www.freedesktop.org/wiki/Software/uchardet/> |
| LuaJIT | 2.1.0（libluajit-build 2.1.0-fix） | MIT | <https://luajit.org/> |

> 「…里的版本」那几行：构建仓库把它们和 libass / GnuTLS 一起发布，确切的上游版本号以那个发布的归档为准。
>
> 上表 = `libmpv.dylib` 里实际有的库（`nm` 查符号表核对，2026-11-19）。MPVKit 1.0.0 的发布里还有 OpenSSL（openssl-build 3.3.5）的静态库，
> 链接时一并给了，但 FFmpeg 的 TLS 走 GnuTLS，`-dead_strip` 之后 `libmpv.dylib` 里一个 OpenSSL 的符号都没有（`SSL_*` / `EVP_*` / `CRYPTO_*`），
> 所以它不在随包的组件里。LuaJIT 在（这份 macOS 的 mpv 带 Lua）。

## 画质增强着色器（随包：Anime4K / FSR）

播放器「画质 › 着色器增强」用的 GLSL 着色器随包带在应用资源里（`files/shaders/`，原件与 SHA-256 在仓库 `third_party/shaders/`），
由应用显式设置 mpv 的 `glsl-shaders` 属性加载——不是 mpv 的用户配置（上面「不读任何 mpv 用户配置」说的仍是用户自己的 `mpv.conf` / 着色器目录）。
Windows / macOS / 安卓 / iPhone（带播放内核的包）带的是同一份；默认关，用户在画质面板里选。

| 库 | 版本（发布标签） | 许可证 | 上游 |
|---|---|---|---|
| Anime4K | v4.0.1（GLSL 版） | MIT | <https://github.com/bloc97/Anime4K> |
| FSR（FidelityFX Super Resolution） | v1.0.2（mpv 移植 by agyild） | MIT | <https://gist.github.com/agyild/82219c545228d70c5604f865ce0b0ce5> |

> FSR 是 AMD FidelityFX FSR v1.0.2 的 mpv 移植（agyild，MIT；gist 修订 f1794d720013ccd9c5c86a0f20fddd71e0375f94），
> AMD 上游 <https://github.com/GPUOpen-Effects/FidelityFX-FSR>。Anime4K Copyright (c) 2019 bloc97；FSR Copyright (c) 2021 Advanced Micro Devices, Inc.。
> 许可证原文随原件放在 `third_party/shaders/Anime4K/LICENSE`、`third_party/shaders/FSR/LICENSE`。
## 安装目录 `app/` 与 `runtime/` 里的运行时和库

| 组件 | 许可证 |
|---|---|
| Java 运行时（Eclipse Temurin / OpenJDK，jlink 裁剪），在 `runtime/` | GPL-2.0 with the Classpath Exception |
| macOS：运行时 `Contents/runtime/Contents/Home/lib/` 里随带的图形库（构建机的 OpenJDK 依赖它们）：FreeType、HarfBuzz、GLib、Graphite2、Brotli、gettext 的 libintl、PCRE2、libpng、libjpeg-turbo、Little CMS 2、GIFLIB | FreeType License 或 GPL-2.0；MIT（HarfBuzz、Brotli、Little CMS 2、GIFLIB）；LGPL-2.1-or-later（GLib、libintl、Graphite2 亦可 MPL-2.0 / GPL-2.0）；BSD-3-Clause（PCRE2）；libpng License；IJG / BSD-3-Clause（libjpeg-turbo）。源码见各项目官网 |
| Kotlin 标准库、kotlinx.coroutines / serialization | Apache-2.0 |
| Compose Multiplatform、Skiko（Skia 绑定） | Apache-2.0（Skia：BSD-3-Clause） |
| Ktor client、OkHttp、Okio | Apache-2.0 |
| JNA | Apache-2.0 或 LGPL-2.1-or-later（双许可） |
| Coil、Kermit、multiplatform-settings、Kyant backdrop / shapes | Apache-2.0 |
| 液态玻璃着色器（移植自 liquid_glass_widgets、liquid_glass_renderer：上游 designsystem 的玻璃材质与着色器；Copyright (c) 2024–2026 Sebastian Degenaar；Copyright 2025 Tim Lehmann for whynotmake.it） | MIT |
| MeiamSubtitles（字幕搜索：迅雷 CID / 射手哈希的算法、两家接口的用法、下载内容的解码与校验，移植成 Kotlin 并有改动；Copyright Meiam；<https://github.com/91270/MeiamSubtitles> 提交 b0427939） | Apache-2.0（原文 `third_party/meiamsub/LICENSE`） |
| Lucide 图标（矢量路径） | ISC |
| 思源宋体子集 Noto Serif SC（界面字体） | SIL Open Font License 1.1（`third_party/fonts/noto-serif-sc/OFL.txt`） |
| 思源黑体子集 Noto Sans SC（iOS 内嵌字幕字体） | SIL Open Font License 1.1（`third_party/fonts/noto-sans-sc/OFL.txt`） |

> 应用里「设置 › 关于 › 开源许可」列的是同一份内容（`ui/…/screens/settings/OpenSourceLicenses.kt`，单测逐行对这里的表）；改一边要改另一边。

> 这是善意的声明，不是法律意见。向第三方分发前，请再核对所带 mpv 构建的许可证。
