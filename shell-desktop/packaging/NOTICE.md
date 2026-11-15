# 第三方声明（咕嘎画报 · EMBY，Windows 安装包）

打包时这个文件被复制到安装目录的 `app/resources/NOTICE.md`（`shell-desktop/build.gradle.kts` 的 `prepareMpv`）。

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

## 安装目录 `app/` 与 `runtime/` 里的运行时和库

| 组件 | 许可证 |
|---|---|
| Java 运行时（Eclipse Temurin / OpenJDK，jlink 裁剪），在 `runtime/` | GPL-2.0 with the Classpath Exception |
| Kotlin 标准库、kotlinx.coroutines / serialization | Apache-2.0 |
| Compose Multiplatform、Skiko（Skia 绑定） | Apache-2.0（Skia：BSD-3-Clause） |
| Ktor client、OkHttp、Okio | Apache-2.0 |
| JNA | Apache-2.0 或 LGPL-2.1+（双许可） |
| Coil、Kermit、multiplatform-settings、Kyant backdrop / shapes | Apache-2.0 |
| Lucide 图标（矢量路径） | ISC |
| 思源宋体子集 Noto Serif SC（界面字体） | SIL Open Font License 1.1（`third_party/fonts/noto-serif-sc/OFL.txt`） |

> 这是善意的声明，不是法律意见。向第三方分发前，请再核对所带 mpv 构建的许可证。
