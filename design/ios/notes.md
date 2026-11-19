# 咕嘎画报（B1）· iOS（iPhone）

- 入口：`index.html`（双击即可，需要支持 WebGL2 的 Chromium 内核浏览器；真机请用 `device.html`）。左栏：参考机切换、真机模式入口、界面目录、「可以玩的」；右边全部画板，每块画板套一台 iPhone（圆角屏、灵动岛 / 刘海、iOS 状态栏、home 条——**系统外壳示意，不参与设计语言评审**）。鼠标 = 手指。
- 单看一屏：`index.html?p=<界面>&shot=1[&st=<状态>][&sz=pro|mini|max][&scroll=<px>][&bare=1]`。界面 id 同手机端（`home discover libs wall sets set search series episode movie person hist favs me settings servers update player`）+ iOS 独有的 `swipe`。`bare=1` 不画灵动岛 / home 条。
- **真机 / 模拟器模式：`device.html?p=<界面>[&st=…]`**（详见 §5）。
- 画板：竖屏 **402 × 874**（iPhone 16 / 17 Pro，状态栏安全区 62，home 条 34）；小屏 375 × 812（13 mini，刘海，安全区 50）；大屏 440 × 956（Pro Max）。播放器横屏 874 × 402（左右安全区 62、底 21）。截图 3×（1206 × 2622 / 2622 × 1206）。
- **这是安卓手机端（`../phone/`）的平台变体，不是新画的一套版式。** 版式、纸面语言、专色、字号 / 间距 token、玻璃范围、她的规矩全部沿用手机端；只画 iOS 真正不同的地方。`../phone/` 与 `../../docs/` 一个字节没动。

## 复用，不复制

`index.html` / `device.html` 直接引 `../phone/css/*` 和 `../phone/js/*`（含 48 MB 的图片包 `assets.js`、数据 `data.js`、玻璃引擎 `engine.js / core.js / glass.js`，全部原样），在它们之后加载本目录的覆盖层。本目录只有这些新文件：

| 文件 | 干什么 |
|---|---|
| `css/ios.css` | 共用覆盖：安全区变量（`--sat --sab --sal --sar`）替掉手机端写死的 46 / 18；随视口宽排的列（海报墙、合集格、封面区）；iOS 状态栏画法；标签栏按 home 条落位；长按纸片菜单、左滑返回、AirPlay / 画中画、App Store 号外、iOS 键盘示意、播放器横屏让位；`@font-face` 引仓库里的 Noto Serif SC 子集 |
| `js/ios.js` | 共用适配层：换掉 `K.tabbar`（不再写死 px）、`APP.status`（iOS 状态栏）、徽记（G03 v3）；改状态清单；触感钩子；文案替换；长按菜单；播放器顶栏加 AirPlay / 画中画；App Store 号外；合成画板 `swipe`；两种模式共用的启动顺序 |
| `js/ios-gallery.js` `css/ios-gallery.css` `index.html` | 画廊：iPhone 外框、参考机切换、状态按钮（iOS 独有的状态用红框标出） |
| `js/ios-device.js` `css/ios-device.css` `device.html` | 真机模式：真实视口、`env()` 安全区、每个标签一叠的导航、推入 / 弹出动画、左边缘右滑、原生桥、转屏、键盘让位 |
| `tools/shots.js` | 无头 Chrome（SwiftShader）截图：画廊单屏 + 真机模式的真实点按 / 边缘滑动流程；截图落在 scratch 目录，不入库 |

不复制图片包，不复制玻璃引擎；玻璃引擎的实例字段（`Board.W / H / out`）在改尺寸时由 `IOS.resizeBoard` 改一下，引擎代码本身没动。

## 1. 相对安卓手机改了什么（逐条）

| # | 改了什么 | 理由 |
|---|---|---|
| 1 | **尺寸与安全区**：画板 402 × 874；状态栏高度 = 安全区 62（不是 46），底部 home 条 34；横屏播放器左右各 62、底 21。手机端所有写死的 46 / 18 / 96 / 150 换成 `--sat / --sab` 的算式，412 宽时算出来的数和安卓一个不差 | iPhone 的状态栏比安卓高、底部多一条 home 指示条；真机上这些数来自 `env(safe-area-inset-*)`，不同机型不同 |
| 2 | **列宽随视口**：页边 24 不变，正文栏 = 视口 − 48（402 上是 354）；海报墙 3 列 × (栏宽 − 34) / 3（402 上 106.7 × 160；412 上正好 110 × 165）；合集格按 2 : 3 比例；字号一律不变 | 375–440 三种宽度都要能看；字号、页边是 token，不随宽度缩放；列数固定、卡片吃掉宽度差 |
| 3 | **封面区按高度收紧**：首页封面块（海报流 → 期号 → 续看 → 主编手记）在 402 × 874 上按 0.96、375 × 812 上按 0.86 收紧（海报 220 × 330 → 211 × 317 / 189 × 284，各区的 top 同比），字号不变 | iPhone 比参考安卓机矮 31、底部还多 16，不收紧「主编手记」一行会被标签栏盖住。B1 原稿在 390 × 844 上就是 196 × 294，这里是在两者之间插值 |
| 4 | **整幅剧照铺到灵动岛后面**：剧照从屏幕最顶端铺起，状态栏文字压在图上；返回 / 收藏 / 更多两个上角的方框钮落在岛的两侧（y = 64–104，岛在 11–48） | 手机端的规则「整幅铺满、按钮固定在两个上角」在 iOS 上正好避开岛；岛本身是系统画的 |
| 5 | **返回**：左上返回钮照旧（红线）+ **左边缘右滑返回**（跟手的交互式：页跟着手指走，底下那一页从 −30 % 跟回来、压暗 45 % 退去；过 1/3 或甩得快就完成，否则弹回）；**没有「再按一次返回退出」**（状态和小条都去掉） | iOS 没有系统返回键，边缘右滑是平台惯例；退出应用由系统手势管，应用不能也不该拦 |
| 6 | **标签栏落位**：玻璃标签栏 + 搜索圆钮浮在 `max(18, home 条安全区)` = 34 上方；往下读时仍然一起沉到屏幕外、回滚 8 回来；内容末尾留 home 条 + 56 + 112 | 不能压在 home 指示条上（那一带是系统的上滑手势区）；其余和手机端一样 |
| 7 | **下拉付印新刊**代替系统回弹：首页滚动区关掉 iOS 的橡皮筋（`overscroll-behavior-y:none`），滚到顶再拉用 touch 事件接管，报头上方拉开一条、她摇印刷机、拉满触感 medium、付印 success | 规范优先：下拉是画报自己的「付印」，不是系统的转圈；其余列表保留 iOS 自己的回弹手感 |
| 8 | **长按 = 纸片菜单（推开，不盖）**：海报墙的海报、目录里的一行按住半秒（触感 medium）→ 一张米白纸片在那一行 / 那一排海报下面展开：播放、收藏（带印章位）、标记已看（带校对格）、详情、管理 › 删除媒体…（点了原地变红边二次确认）。标题显示片名全名 | iOS 惯例是长按出上下文菜单；规范说菜单是纸片、展开是推开——两者合起来就是这样。安卓手机端 `platforms.md` 也写了「顶栏更多 / 长按」，只是 demo 没画；这一张两端通用 |
| 9 | **触感**：落章 success、擦章 light、校对勾 light、翻期号 selection、换标签 selection、下拉拉满 medium、付印 success、长按 medium。走原生桥 `postMessage({type:'haptic'})`，没有桥就只记日志 | iPhone 的 Taptic 是平台的一部分；安卓的振动策略另定 |
| 10 | **播放器横屏**：画面 `object-fit: contain` 铺满整屏（16:9 的画面在 19.5:9 的屏上本来就两侧留黑，岛和圆角落在黑边里，一个像素都不挡画面）；控件、字幕、弹幕让出左右 62 和底 21；面板打开时画面缩到按视口算出的比例（402 × 874 上 0.725、以内容区右缘不碰面板为准）而不是写死 0.737；进播放器隐藏状态栏和 home 条（`chrome` 桥） | `player.md` §1；灵动岛和 home 条由系统画，只能让 |
| 11 | **AirPlay 代替投屏**：顶栏右半边 **[AirPlay][她的一格][收藏][画中画][锁]**（她在收藏左边、画中画在锁左边，都按 `player.md` §6 / §8）。点 AirPlay 走系统路由器选电视；投上以后画面拿掉，换成纸面说明（正在投到 客厅的 Apple TV · 结束 AirPlay · 换一台），时间轴和功能词留着；锁屏 / 控制中心的「正在播放」由系统接管 | iOS 上投屏就是 AirPlay（系统提供，不画找电视的面板）；画面在电视上，所以这一屏没有她 |
| 12 | **画中画**：顶栏右半边的画中画钮；进画中画后播放器这一屏换成纸面说明「画面在画中画里 · 回到全幅」，首页上浮着系统的小窗（画廊里画了一个示意） | 系统功能；小窗是系统画的，不是版面元素 |
| 13 | **更新「号外」**：iOS 不能自己下载安装 → 号外只说「App Store 有新版」/「TestFlight 版」，按钮通向 App Store / TestFlight；去掉「下载中 / 下载完成」两个状态和进度线；强制更新只有「去 App Store 更新」，**没有「退出应用」** | App Store 规则：应用不能自更新、不能自己退出。设置 · 关于里「检查更新」改成「由 App Store 管理」，更新通道改成 App Store / TestFlight |
| 14 | **键盘**：搜索页的键盘是系统的（深色 iOS 键盘 + 拼音候选条，画廊里是示意）；弹起时标签栏沉下去、结果列表让出键盘高度（真机模式读 `visualViewport`） | 安卓示意的是安卓键盘；iOS 的键盘盖在内容上，不缩视口 |
| 15 | **字体**：demo 直接 `@font-face` 引仓库里入库的 Noto Serif SC 子集（400 / 600 / 900，`third_party/fonts/noto-serif-sc/subset/`，`decisions.md` Q3），字重映射照 `GugaFonts.snap`（≤ 450 → 400、≤ 750 → 600、其余 → 900）；数字 / 英文小标仍是 Georgia 斜体（iOS 自带）。拿不到时退回 iOS 自带的宋体-简（Songti SC，只有 400 / 700 / 900） | 真机实现随包内置同一套字体，不依赖系统宋体；demo 里也这样才对得上 |
| 16 | **文案**：Android 14 → iOS 18、「系统安全存储（Android Keystore）」→「钥匙串（Keychain）」、「这台手机」→「这台 iPhone」；**播放引擎 / 回退链不编**：首选引擎一行写「待定（上游决定）」，能力清单里「原生播放引擎 待定」「外部播放器 待定（Infuse / VLC 的 URL scheme）」，液态玻璃 Live · Metal | 内核是上游的事；iOS 的 ExoPlayer 等价物没有定 |
| 17 | **报头徽记**用第三版的她（`design/generated/v3/G03.png`） | `mascot.md` §1：手机 demo 里从品牌图裁的企鹅头是旧徽记 |
| 18 | **状态栏画法**（画廊）：时间在左耳 17 pt 半粗，右耳信号 / Wi-Fi / 电池；灵动岛黑胶囊 126 × 37、home 条 134 × 5；375 × 812 画刘海 | 系统外壳示意，真机不画 |

没改的（刻意）：页边 24、字号阶梯、栏目行、印章 / 校对勾、专色算法、海报流、「推开不盖」、顶栏实心阈值、她的格子、播放器面板的做法、设置树、服务器 / 登录三步、所有真实数据与编的内容（见 `../phone/notes.md` §6）。

## 2. 遵守的红线

- 只有深色；纸是墨黑的。返回永远在左上、选项在右；播放器里有收藏。
- 玻璃只在底部标签栏和搜索圆钮（手机端的两块），用 demo 自带的 WebGL 引擎原样；纸片菜单、号外、AirPlay / 画中画说明、键盘都是纸面或系统的，不是玻璃。左滑返回时底下那一页只压一层纸色，**没有投影 / 白雾 / 模糊**。
- 专色不当文字底色 / 按钮底色；纸片菜单上的印章、校对勾仍是固定朱红；菜单的小标在米白纸上用墨 55 %（不用专色压在浅色纸上）。
- 按下是放大（长按时那张海报 / 那一行放大 1.04；菜单项按下 1.03）；展开是推开（纸片菜单把后面的内容推下去）。
- 她只待在留给她的格子里；AirPlay / 画中画 / 正在播放的画面上没有她；暂停、片尾、出错时才在她的格子里。
- 颜色、字号、间距照手机端 token；本目录只换安全区和列宽的算式。

## 3. 哪些是示意、没做

- 画廊里的灵动岛、刘海、状态栏、home 条、画中画小窗、iOS 键盘、AirPlay 路由器选单（没画，点了直接当作已选中电视）都是**系统外壳示意**；真机上由系统画。
- 纸片菜单的「播放 / 详情」在画廊里只收起菜单；真机模式里会真的推入播放器 / 详情。「删除媒体…」只做到二次确认，不真的删。
- 触感只记日志（`IOS.log`），没有桥时没有任何震动。
- 真机模式的内容是 demo 数据包里的 14 部详情、7 部剧的分集：点到没有详情稿的片子，先翻开《颜心记》/《盗梦空间》的版式顶一下，她在主编按里说一句。
- 更新号外的「去 App Store」不真的跳转。
- 「正在播放」锁屏卡片、控制中心、Siri 遥控、CarPlay：系统功能，没画（真机实现接 `MPNowPlayingInfoCenter`，见 §6）。
- 竖屏播放：不做（进播放器锁横屏，同手机端）。
- 没画 rank-chip / 称号纸片（手机 demo 也没有，`decisions.md` D11–D12 在实现里）。

## 4. 验证（本机 Chrome 无头，SwiftShader WebGL2）

`node tools/shots.js all`（`QUICK=1` 出 1 倍快图；默认画廊 3×、真机 2×）。截图在 scratch 目录（不入库）。

- 画廊：全部 18 个界面 + `swipe` 合成画板，含 iOS 独有的状态（画中画浮窗、长按纸片菜单 × 2、AirPlay、画中画、App Store / TestFlight / 必须更新、左滑 12 / 40 / 80 %）；402 × 874 为主，另有 375 × 812 的首页 / 海报墙 / 剧集 / 播放器和 440 × 956 的首页 / 播放器。
- 真机模式（`device.html`，`mobile:true` + 触摸模拟）：首页 → 点目录行进《颜心记》→ 左边缘右滑拍到一半 → 松手回首页 → 再进详情 → 续看进播放器 → 视口转成 874 × 402 → 字幕面板 → AirPlay → 返回转回竖屏 → 返回首页 → 点玻璃标签「片库」→ 媒体库 → 海报墙 → 长按海报出纸片菜单 → 「我的」→ 设置 → 播放分区 → 返回 → 搜索圆钮进搜索。另：横屏直接进播放器的 ctl / 选集 / 画中画 / 看完一集；812 × 375 的字幕面板；375 × 812 的首页；440 × 956 的海报墙。
- 检查项：0 个 JS 异常 / 控制台错误、玻璃起来（`body` 没有 `nogl`，每块板报出 3 个玻璃形状）、`img[data-img]` 全部解码、桥的消息日志（orientation / chrome / haptic）、导航叠的内容。
- 结果（2026-11-18，Chrome 无头 + SwiftShader）：
  - 画廊 60 张（1×）+ 24 张（3×）：`Runtime.exceptionThrown` / 控制台错误 **0**；所有带玻璃的板 `glass-shapes:3`（标签栏 + 透镜 + 搜索钮），没有 `nogl`；没有 `noimg`；画廊页本身 7 块板全部起来，切到 375 × 812 后重排正常。
  - 真机模式 25 张（2×）：异常 **0**。流程走通：首页 → 目录行《颜心记》→ 左边缘右滑到 37 %（拍到底下首页的视差和压暗）→ 松手回首页 → 再进详情 → 续看 → 播放器（竖屏先自己转 90°）→ 视口 874 × 402 后重排 → 字幕面板（画面缩到 0.725，面板在右安全区内）→ AirPlay → 返回转回竖屏 → 返回首页 → 玻璃标签「片库」→ 媒体库 → 海报墙 → 长按出纸片菜单 → 「我的」→ 设置 → 播放分区（引擎写「待定」）→ 返回 ×2 → 搜索钮。结束时各叠 `{home:[home], libs:[libs, wall], me:[me], search:[search]}`、`tab=search`，和点过的路径一致。
  - 桥的日志依次是：`orientation landscape` → `chrome 隐藏状态栏 / home 条` → `haptic selection`（AirPlay 开）→ `orientation portrait` → `chrome 恢复` → `haptic selection`（换标签）→ `haptic medium`（长按）→ `haptic selection`（换标签）。
  - 和手机定稿（`../phone/shots-sample/`）并排看过 `home` / `home-toc` / `series` / `wall` / `me` / `player-sub`：版式一致，差别只有状态栏画法、顶栏高度、列宽和封面区的收紧。

**没验证的（尤其 WebKit）**：没有在 iOS Safari / WKWebView 里跑过——本机只有 Chrome 无头。担心的点见 §5「WebKit 风险」。

## 5. 真机 / 模拟器模式（`device.html`）

- 地址：`http://127.0.0.1:8765/design/ios/device.html?p=home`（静态服务器根 = 仓库根；`../phone/js/assets.js`、`../generated/v3/G03.png`、`../../third_party/fonts/...` 这些相对路径都从那里解析）。
- 参数：`p=`（任何界面 id；非根界面会自动垫上它所属标签的根页，`player` 底下垫 `series`）、`st=`（初始状态）、`sat= sab= sal= sar=`（没有 `env()` 时手填安全区，单位 pt）、`shot=1`（定格，不跑动画和眨眼，截图用）。
- 视口：`viewport-fit=cover`，按 `innerWidth / innerHeight` 排版，`env(safe-area-inset-*)` 用一个探针元素量出来写进 `:root`；不画假状态栏 / 灵动岛 / home 条；整幅剧照真的铺到屏幕最顶。页面本身 `position:fixed` 不滚、`overscroll-behavior:none`，只有 `.scroll` 滚。
- 导航：标签栏四格 + 搜索圆钮各自一叠（`home discover libs me search`）；推入 = 新页从右滑入、底下那页退 30 % 压暗；返回钮 / 左边缘右滑 / `.mback` 弹出；再点当前标签 = 回到那一叠的根（根上再点 = 滚到顶）。能走通的路径：首页封面正中那张 → 详情；目录行 / 新刊 / 栏目格 / 同刊 → 详情或海报墙；详情的续看 → 播放器（转横屏）→ 返回转回竖屏；演职人员 → 人物页（数据包里有的五位）；片库 → 媒体库 → 海报墙（`.mback` 回）；合集 → 合集详情 → 成员；搜索 → 结果 → 详情；我的 → 记录 / 收藏 / 设置（分区 / 关于）/ 服务器三步 / 号外；长按 → 纸片菜单 → 播放 / 详情。
- 原生桥（都判空，没有也照跑）：进播放器 `{type:'orientation', value:'landscape'}` + `{type:'chrome', statusBarHidden:true, homeIndicatorHidden:true}`，退出反过来；触感 `{type:'haptic', style}`。转屏后 `resize` 触发重排（所有可见板重量、玻璃板重新建纹理）。没有桥、视口没转（电脑浏览器预览）时播放器自己转 90°（这时时间轴拖动的坐标不对——只是预览退路）。
- 输入全是 pointer 事件；长按 480 ms；边缘手势区 22 pt 宽、`touch-action:none`；没拖就当点按透传给底下的按钮。
- **WebKit 风险**（没在真机验证过，按可能性排）：
  1. `@property --p` + `transition: --p`（推入 / 弹出动画）需要 Safari 16.4+；更早的只是没有动画，不影响功能。
  2. `env(safe-area-inset-*)` 只在 `viewport-fit=cover` 时非零；WKWebView 外壳要把 web view 铺到安全区之外（`contentInsetAdjustmentBehavior = .never`），否则量到 0，页面会按没有刘海排（可用 `?sat=62&sab=34` 手填兜底）。
  3. 玻璃引擎：每块板一个 WebGL2 上下文 + 多张 2× 画布（18 块板 ≈ 90 MB 位图）。iOS 的画布内存上限按设备算，老机型可能被回收（表现为玻璃消失、`nogl`）；demo 已把玻璃采样上限压到 2×。真机实现用上游 Metal 引擎，没有这一条。
  4. 首页下拉：靠 `touchmove` 的 `preventDefault`（非 passive）压住系统回弹；iOS 要求第一次 `touchmove` 就阻止，否则这一手势会被系统接走。`overscroll-behavior:none` 在 iOS 16+ 才生效。
  5. 长按：`-webkit-touch-callout:none` + `user-select:none` 已加，防止系统弹出图片保存菜单；长按后的 click 用一次性捕获压掉。
  6. 容器查询 `@container`（375 宽时报头、库名小字）需要 iOS 16+；`aspect-ratio` 需要 15+。
  7. `visualViewport` 键盘高度：WKWebView 里弹键盘可能把整个 web view 的滚动视图往上推，而不只是缩 `visualViewport`——真机上要看搜索页。
  8. 字体子集 3 × 3.4 MB 走 `font-display:swap`，首屏可能先用 Songti SC 闪一下；可以让外壳把字体预载或改成 `block`。
  9. 性能：`.scroll` 的滚动每帧让玻璃重新光栅化整页（demo 的做法），在 WebKit 里 `getComputedStyle` 遍历比 Chrome 慢，长列表上可能掉帧；真机实现不存在这个问题。

## 6. 需要主理人定的事

1. **标签栏离底多少**：这里取 `max(18, home 条安全区)` = 34（玻璃下沿贴着安全区线，离 home 条 21）。另一种是 Apple 自己浮动标签栏的做法（再抬 8，= 42）。
2. **长按纸片菜单是否也进安卓手机端**：`platforms.md` 手机一行写了「顶栏更多 / 长按」，这张纸片两端通用；要不要回写进 `components.md`「PaperMenu 📱」那一格（现在写的是「用推开式代替，基本不弹菜单」）。
3. **左滑返回底下那一页的处理**：这里是 −30 % 视差 + 45 % 纸色压暗、没有投影（守「不自己写投影」）。iOS 系统的推入动画在页边有一道淡影；要不要为「系统转场」开例外。
4. **封面区在矮屏上收紧**（0.86–0.96）还是保持 1 让主编手记被标签栏盖住、靠滚动看：这里选收紧。
5. **AirPlay 投上以后这一屏的样子**：画面拿掉换纸面说明（现在的做法），还是保留画面（iOS 原生播放器的做法是黑屏 + AirPlay 图标）。
6. **画中画时播放器这一屏**：现在是纸面说明「画面在画中画里」；也可以直接退回详情页（Apple 的 TV 应用是退回）。
7. **iOS 的播放内核与回退链**：全部标「待定」；设置 · 播放的「首选引擎」选择器内容要上游定（AVPlayer / mpv / VLCKit 之类）。
8. **强制更新**：iOS 不能退出应用，现在是只留「去 App Store 更新」、画面停在号外上；要不要允许「稍后」（App Store 审核对「必须更新」的拦截页有要求）。
9. **号外的「跳过这一版」**：iOS 上拿不到安装包，只是本机记一个忽略的版本号（和安卓 D15 一样）——要不要留。
10. **状态栏点一下滚到顶**：WKWebView 里这个手势到不了 DOM，需要外壳转发（`window.GUGU.scrollToTop()` 之类）；要不要做。
11. **字体**：demo 引的是仓库里的子集；真机实现是否随包内置同一份（Q3 已定内置，这里只是确认 iOS 目标也走 `GugaFonts`）。
12. **横屏播放器的画面让位比例**：这里按视口算（402 × 874 上 0.725），不再是写死的 0.737；电脑 / 电视不受影响。

## 7. 真机落地（Kotlin Multiplatform）——只写判断

- **`UiMode` 仍是 `Phone`**，不加 `Ios` 这一档。iOS 不是第四套版式，是手机版式在另一个平台上的安全区和输入差异。差异分三类落位：
  - **安全区 / 尺寸**：`WindowInsets.safeDrawing`（Compose Multiplatform 在 iOS 上给的就是 `safeAreaInsets`）喂进现有的 `theme/` 间距；手机端的「状态栏 46 / 底部 150」改成「inset + 常量」——这是 `:ui` 内部的事，三端共用，不需要平台分支。封面区的收紧系数按可用高度算（`theme/` 里一个纯函数）。
  - **输入 / 手势**：左边缘右滑返回、长按纸片菜单、触感 → 在 `:ui` 里用 `expect`/`actual` 开一个很小的 `PlatformFeel`（`haptic(style)`、`edgeBackEnabled`）；安卓的 `actual` 用 `HapticFeedback`，iOS 的 `actual` 用 `UIImpactFeedbackGenerator`。边缘手势用 Compose 的 `draggable` + 现有的 `BackDispatcher`（上游 `ds/focus`）——不自己写返回栈。
  - **系统功能**：AirPlay（`AVRoutePickerView`）、画中画（`AVPictureInPictureController`）、锁屏「正在播放」（`MPNowPlayingInfoCenter`）、转屏锁定、状态栏 / home 条隐藏 → 都是 **壳**（新建 `shell-ios/`）的事，经上游 `PlatformDeps` / `PlayerPlatform` 的能力位告诉界面画不画按钮（`player.md` §8「有能力才画」）。
- **需要上游开缝 / 新 ADR**：
  1. 上游 `native` 现在只有 Android / Desktop 目标；`:core` `:designsystem` `:danmaku` `:app` 要加 iOS 目标（KMP `iosArm64 / iosSimulatorArm64`），玻璃引擎的 Metal / Skia 后端、按压物理在 iOS 上的实现——这是上游的事，写新 ADR（`0003-ios-target.md`），本仓库不动上游。
  2. **播放内核**：上游没有 iOS 的引擎与回退链（AVPlayer / mpv-ios / VLCKit 未定）；`PlayController` 的 iOS `PlayerEngine` 实现是上游的，demo 里一律「待定」。
  3. `PlayerPlatform` 要多三个能力位：AirPlay 路由、画中画、正在播放卡片；安卓的「投屏（DLNA）」在 iOS 壳上报「不支持」，界面按能力位换成 AirPlay 钮。
  4. 更新器：iOS 壳不接上游更新器（`updateHost` 不传），号外改成「去 App Store」——`screens/update/` 加一个平台分支读壳的 `canSelfUpdate`。
  5. 会话 / 设置存储：上游 `LocalStore` / 安全存储在 iOS 上要有钥匙串实现（上游）。
- **本仓库要加的**：`shell-ios/`（造 `PlatformDeps`、`AppGraph`，起 `GugaartApp`，转屏、状态栏、home 条、触感、AirPlay / 画中画的胶水——和两个壳里的播放胶水同一性质，`upstream-sync.md` §5.1 的例外清单要加这一项）；`docs/platforms.md` §1 手机加「iOS 的差异」一小节（上面 §1 的表）；`docs/screens.md` 的 iOS 状态（`swipe ctx menu airplay pip tf`）。
- **不加新设置键**：iOS 的差异没有一项需要用户设置；触感跟系统的「系统触感」开关走。
