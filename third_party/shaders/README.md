# 随包的画质增强着色器（Anime4K / FSR）

播放器「画质 › 着色器增强」用的 GLSL 着色器原件。**只读参照，不要改这些文件**；构建时由 `ui/build.gradle.kts` 的
`gugaResources` 任务合进资源目录 `files/shaders/`（照 `third_party/fonts` 的先例），运行时经 `Res.readBytes` 读出、
写成应用缓存文件后交给 mpv 的 `glsl-shaders`。许可证声明同步在 `shell-desktop/packaging/NOTICE.md` 与
`ui/…/screens/settings/OpenSourceLicenses.kt`（改一边要改另一边）。

## Anime4K（`Anime4K/`，8 个 glsl + LICENSE）

- 来源：https://github.com/bloc97/Anime4K —— 官方 **v4.0.1** 发布（源码归档 `Anime4K-4.0.1/glsl/`，2021-09-17）。
- 许可证：MIT（Copyright (c) 2019 bloc97），原文 `Anime4K/LICENSE`。
- 预设链照官方 `GLSL_Instructions.md` 的 **HQ** 档（顺序即下发顺序）：
  - 模式 A：Clamp_Highlights → Restore_CNN_VL → Upscale_CNN_x2_VL → AutoDownscalePre_x2 → AutoDownscalePre_x4 → Upscale_CNN_x2_M
  - 模式 B：Clamp_Highlights → Restore_CNN_Soft_VL → Upscale_CNN_x2_VL → AutoDownscalePre_x2 → AutoDownscalePre_x4 → Upscale_CNN_x2_M
  - 模式 C：Clamp_Highlights → Upscale_Denoise_CNN_x2_VL → AutoDownscalePre_x2 → AutoDownscalePre_x4 → Upscale_CNN_x2_M

| 文件 | sha256 |
|---|---|
| Anime4K_AutoDownscalePre_x2.glsl | 8c58291740146bd766a4d73f132775a797fe80f7d07919b5d767e27a5dc85656 |
| Anime4K_AutoDownscalePre_x4.glsl | 5af62d8cd844916dc1126613e13bad3beab195787f93a71200b47c6ec78f2e41 |
| Anime4K_Clamp_Highlights.glsl | a2a9bf7fbc1d75d09660ca2e701e4d7fb0cf5457b94da47e1825032fa2b3671a |
| Anime4K_Restore_CNN_Soft_VL.glsl | 094334b0e20c1a201fe4941c7c68de72451e5aee9efb5524d7fb82b12dca64b9 |
| Anime4K_Restore_CNN_VL.glsl | 35036722733305cd4d4e57660b883bbe2569ba2914033c254327107d7b77e35e |
| Anime4K_Upscale_CNN_x2_M.glsl | 716e02098a68f0d648761f2b96b4dd139e1cb09b174bb369fca3aa34328fff7e |
| Anime4K_Upscale_CNN_x2_VL.glsl | 5638fe31c37c151a3443fea3451a3ef91af073f4dbb9615f6c0d1e29db11493d |
| Anime4K_Upscale_Denoise_CNN_x2_VL.glsl | 359c48fe5a317fbc6b706ce368401eef496e84ed98abac7a43efebca2b65d79b |
| LICENSE | 5bad448b737378e3d0c977ad0d0521fa37ad279a7e76ea9a31d9257eeb6953f5 |

> 注：下载时的 `manifest.sha256` 里 `Anime4K_Restore_CNN_VL.glsl` 一行误记成了空文件的哈希
> （e3b0c442…b7852b855）；上表是实际文件的哈希，已和 v4.0.1 官方归档里的同名文件核对一致。

## FSR（`FSR/`，FSR.glsl + LICENSE）

- 来源：AMD FidelityFX **FSR v1.0.2** 的 mpv 移植，作者 **agyild** ——
  https://gist.github.com/agyild/82219c545228d70c5604f865ce0b0ce5 （修订 f1794d720013ccd9c5c86a0f20fddd71e0375f94）。
  AMD 上游：https://github.com/GPUOpen-Effects/FidelityFX-FSR 。
- 许可证：MIT（Copyright (c) 2021 Advanced Micro Devices, Inc.），原文取自 `FSR.glsl` 头部，加一行移植署名，见 `FSR/LICENSE`。
- 单文件即一条链（EASU + RCAS 两个 pass 都在里面）。

| 文件 | sha256 |
|---|---|
| FSR.glsl | 56d8597fc6b7bf6d13f8c3b2bdf1cdc43b06175d51746aab44cf1dca16929b9e |
