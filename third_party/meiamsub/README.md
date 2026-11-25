# MeiamSubtitles（字幕搜索的移植来源）

播放器「字幕 › 搜索字幕…」（docs/subtitle-search.md）里迅雷 / 射手两家字幕源的做法移植自 MeiamSubtitles。

- 来源：<https://github.com/91270/MeiamSubtitles>，提交 `b0427939fad525a19dd738b57fd23c2842490dfd`（2026-11-23 读取）。
- 许可证：Apache License 2.0（Copyright Meiam），原文 `LICENSE`（未修改的副本）。
- 原件是 Emby / Jellyfin 的**服务器插件**（C#）；这里没有拷贝任何源文件，只把下面这些做法用 Kotlin 重写，文件头写明出处和改动：

| 本仓库的文件 | 移植自（MeiamSubtitles） | 移植了什么 | 改了什么 |
|---|---|---|---|
| `ui/…/subtitle/Fingerprint.kt` | `Emby.MeiamSub.Thunder/ThunderProvider.cs` `GetCidByFileAsync`；`Emby.MeiamSub.Shooter/ShooterProvider.cs` `ComputeFileHashAsync` | 迅雷 CID（SHA-1，三段各 0x5000 字节；小于 0xF000 取整个文件）、射手哈希（四块 4096 字节的 MD5，偏移 4096 / size/3*2 / size/3 / size−8192，`;` 连接） | 不读本地文件：描述要哪几段，字节由 HTTP Range 从 Emby 原始流 / strm 目标取；两种指纹合起来只发 4 个请求 |
| `ui/…/subtitle/Providers.kt` | 两个 Provider 的 `SearchSubtitlesAsync` 与 `Model/` | 接口地址、参数、回包字段、过滤（格式 srt/ass/ssa、按地址去重）、迅雷的排序与前 20 条 | 只发文件名（不发服务器上的完整路径）；迅雷多用 `duration` 排序；三路结果合成一张单子统一排 |
| `ui/…/subtitle/SubtitleText.kt` | `Shared/SubtitleContentValidator.cs` | 解码顺序（BOM → 严格 UTF-8 → UTF-16LE → UTF-16BE → GB18030）、像不像字幕的判断、压缩包 / 错误页拒收 | GB18030 是平台缝（JVM `Charset` / iOS `NSString`） |

声明同步在 `shell-desktop/packaging/NOTICE.md` 与 `ui/…/screens/settings/OpenSourceLicenses.kt`（改一边要改另一边）。
