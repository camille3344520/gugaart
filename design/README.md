# design/ —— 「咕嘎画报」（设计代号 B1）三端设计稿（定稿，2026-11-14）

这里是**设计的原件**：三端可交互的 HTML demo、各端说明、设计来源。正式规范在 [`../docs/`](../docs/README.md)，实现时以规范为准；
规范没写到的细节，回到这里的 demo 和 `notes.md` 看原件。规范和原件对不上，按 `AGENTS.md` §6 处理（先问，不要猜）。

| 路径 | 是什么 | 来自 |
|---|---|---|
| `phone/` | 手机端：竖屏 412 × 905，播放器横屏 905 × 412；18 个界面 | `fable-b1/phone/` |
| `desktop/` | 电脑端（Windows）：1100 × 720 / 1440 × 900 / 3168 × 1440 三种窗口 | `fable-b1/desktop/` |
| `tv/` | 电视端：1920 × 1080，遥控器 | `fable-b1/tv/` |
| `source/styles.md` | 设计来源：B1 / B2 两版的说明（只有 **B1 一节**是定稿；B2 没有采用，留作背景） | `fable-huabao/styles.md` |
| `source/shared-masthead.md` | 三端统一的报头 | `fable-b1/shared-masthead.md` |
| `source/data-README.md` | 真实数据包的结构说明（数据包本身不入库） | `fable-b1/data/README.md` |
| `brand/` | 品牌图 `gugaemby.jpg`、图标 `icon-512.png`、分层立绘 `mascot/*.webp` | `fable-styles-b/brand/` |

原路径都在 `F:/gugugagaemby/experiments/ui-wave/` 下（那边的 `experiments/` 不入库，只在主理人的机器上）。

每个端的目录里：`index.html`（入口，左栏界面目录，右边全部画板）、`notes.md`（这一端的说明：改了什么、布局规则、互动清单、她放在哪、
生图清单、哪些是编的、最难的数据）、`css/`、`js/`、`tools/`（打包图片、重拍截图）。

## 哪些不在 git 里，怎么还原

demo 要用真实数据包里的图片（海报、剧照、logo、演员照），打成 base64 后几十 MB；全量截图每端一百多张原尺寸 PNG。这些都**不入库**
（`.gitignore`），原因：体积大，而且数据包是从用户服务器只读导出的，只用于本地。

| 不入库的 | 大小 | 作用 |
|---|---|---|
| `data/`（`library.json`、`extra.json`、`extra2.json`、`img/`） | 36 MB | 真实数据包 |
| `phone/js/assets.js`、`phone/js/data.js` | 48 MB | 手机 demo 的图片包和数据 |
| `desktop/pack/`、`desktop/js/lib.js` | 50 MB | 电脑 demo 的图片包和数据 |
| `tv/assets/`、`tv/js/data.js`、`tv/img/` | 49 MB | 电视 demo 的图片包、数据、品牌图副本 |
| `phone/shots/`、`desktop/shots/`、`tv/shots/` | 约 620 MB | 全量截图（131 / 140 / 87 张） |
| `*/shots-sample/` | 约 1.4 MB | 每端 5 – 6 张缩小的代表截图，`scripts/design-samples.mjs` 从全量截图生成。画面是用户片库的真实海报和观看进度，仓库公开后不入库 |

**没还原之前双击 `index.html` 是空白的**（脚本找不到图片包）。还原：

```bash
node scripts/restore-design.mjs               # 从原路径 F:/gugugagaemby/experiments/ui-wave/fable-b1 复制（约 800 MB）
node scripts/restore-design.mjs --no-shots    # 不要全量截图（约 180 MB），demo 就能打开
node scripts/restore-design.mjs --check       # 只看缺什么
node scripts/restore-design.mjs --from=<目录> # 原路径换了地方：<目录>/fable-b1
```

原路径不在了：`data/` 要请主理人重新从服务器只读导出（结构见 `source/data-README.md`），然后用各端自己的脚本重建——

```bash
node design/phone/tools/build-assets.mjs      # -> phone/js/assets.js + data.js
node design/desktop/tools/pack.mjs            # -> desktop/pack/*.js + js/lib.js
node design/tv/tools/build-assets.mjs         # -> tv/assets/*.js + js/data.js（再把 brand/gugaemby.jpg 复制成 tv/img/brand.jpg）
node design/<端>/tools/shots.js [过滤词]       # 重拍截图（要本机的 Chrome；会开无头浏览器，经全局锁跑）
node scripts/design-samples.mjs               # 重新生成 shots-sample/ 和报头徽记 PNG
```

## 和原件的差别（迁移时改过的地方）

只改了三个打包脚本里品牌图的路径，其余文件一个字节没动：

- `phone/tools/build-assets.mjs`、`tv/tools/build-assets.mjs`：`fable-styles-b/brand` → `design/brand`
- `desktop/tools/pack.mjs`：`../../fable-styles-b/brand` → `../brand`

各端 `notes.md` 里写的 `../../fable-huabao/styles.md`、`../shared-masthead.md`、`fable-styles-b/brand/…` 是原路径的写法，
在这里分别是 `source/styles.md`、`source/shared-masthead.md`、`brand/…`。

## demo 里的玻璃不是真机的玻璃

`js/engine.js`、`core.js`、`glass.js` 是 WebGL 版的玻璃引擎，只为了在浏览器里看效果：它折射的是页面内容的一份**重绘**
（只认图片、纯色、线性渐变、文字、图标），所以虚化的剧照、三维倾斜的海报在玻璃里是近似的。真机上用上游的 `:designsystem`
玻璃引擎（着色器直接取屏），没有这个问题。**实现时不要移植这三个文件**，见 [`../docs/glass.md`](../docs/glass.md)。
