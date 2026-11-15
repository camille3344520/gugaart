# 咕咕嘎嘎 · 3D 立绘生图提示词（第三版，2026-11-15）

用户 2026-11-15：「这些图片不好看，不精致呢？给你原型图，你的提示词详细点。图标啊，里面所有的都重新改一下。」

- 原型图：`design/brand/proto-3d.png`。3D 渲染的手办 / 潮玩质感，毛绒企鹅连帽衣。每张图都上传它作为角色和画风参考。
- 用 Gemini 网页版生成（用户的会员），1:1。
- 背景要纯绿实色，本地用 `scripts/art/chroma_cut.py` 抠成透明 PNG：去绿边、羽化，缩到 1024 / 512。
- 每条提示词 = **角色块** + **姿势块** + **背景块**，三段原样拼接，英文写（生图模型对英文描述更稳）。
- 先出 3 张试样（G03 徽记 / 图标、G15 递钥匙、G08 摊手），用户同意后再出其余的（2026-11-15 全部出完）。
- **表情一律可爱**（用户：「表情可爱点的吧，不要这么凶的」）：原型图的嘟嘴不高兴只留在原型里。姿势块里写明大眼睛、眉毛柔和、红晕、小小的笑；不写皱眉、半闭的眼皮。G03 的嘟嘴试样作废，报头徽记改用 G01 署名照。
- 抓图：图在网页里是 blob，下载会落到 Mac 上。做法是用脚本把原图按 1:1 铺在页面左上角（512 CSS px，DPR 2），再用浏览器扩展的区域截图存盘，得到 1024 × 1024 的 PNG（`design/generated/v3/raw/`）。

## 角色块（每张都放最前面）

```
Use the attached image as the exact reference for the character design, proportions, materials and rendering style. Draw the SAME character:
a chibi girl collectible figure, high-end 3D render (designer-toy / Pop Mart blind-box quality, Pixar-like), big head and small body, about 2.5 heads tall.
Hair: glossy jet-black bob with blunt straight bangs and a few loose strands framing the face.
Face: large grey eyes with dark grey-brown irises, bright catch-lights and thick dark upper lashes, thin soft eyebrows, soft pink blush on the cheeks, small mouth, smooth porcelain skin with gentle subsurface scattering.
Outfit: an oversized black plush penguin costume hoodie. The hood is a penguin head: two round white cartoon eyes with black pupils on top of the hood, and a rounded glossy yellow-orange beak sticking out over her bangs. A beige cross-shaped adhesive bandage on the right side of the hood. A small silver zipper pull hanging at the left edge of the hood. A blue bandana scarf and a thin silver chain around her neck. White plush belly patch. The sleeves are rounded black penguin flippers (no human hands, no fingers). Orange-yellow penguin feet with three rounded toes. A short black penguin tail.
Materials: velvety plush fabric with fine visible fuzz and subtle stitching, glossy hair strands, glossy beak, polished metal chain.
Lighting: soft neutral studio lighting, key light from the upper left, gentle rim light, soft ambient occlusion; clean, premium, crisp micro-detail, sharp focus on the whole figure.
```

## 背景块（每张都放最后）

```
Background: a perfectly flat, uniform, solid pure green (#00FF00) backdrop filling the entire frame. No floor, no ground plane, no cast shadow, no reflection, no gradient, no vignette, no bokeh, no extra props. Keep the lighting on the character neutral white — no green light or green reflections on the character.
The whole character fits inside the frame with comfortable empty margin on every side (nothing cropped).
No text, no letters, no numbers, no logo, no watermark, no signature. Square 1:1 image.
```

## 姿势块

### G03 · 报头徽记 / 应用图标（试样）

用在：三端报头正中的圆徽（24–40 px），并由 `scripts/art/make-icons.py` 生成手机 / 电视 / Windows 的应用图标和电视横幅。

```
Composition: head-and-shoulders portrait, perfectly centered, facing straight at the viewer, almost symmetrical. The penguin hood and her face fill about 80% of the frame width; the top of the hood beak and the shoulders with the blue bandana are visible.
Expression: the same pouty, slightly grumpy look as the reference — cheeks a little puffed, lips pressed into a small pout, eyes looking straight at the viewer with heavy upper lids; cute, unimpressed, very characterful.
This image will become an app icon: bold readable silhouette at small sizes, strong clear shapes, high contrast between the black hood, her face and the yellow beak. No hands in the frame.
```

### G15 · 登录页：递钥匙（试样）

用在：手机 / 电脑 / 电视登录页（电视在右栏 240 dp）。

```
Pose: half-body down to the knees, facing the viewer with a slight three-quarter turn to her right. She holds out an antique brass key toward the viewer, balanced on the tip of her right flipper and pushed forward into the foreground (the key is polished gold with an ornate round bow and a simple bit, clearly visible and in focus). Her left flipper is tucked behind her back.
Expression: a warm, friendly closed-eye smile with the eyes curved into happy crescents, cheeks blushing, head tilted slightly — a little proud, as if saying "here you go".
```

### G15b · 登录页：密码不对

```
Pose: exactly the same pose, framing, key and lighting as G15 (same image, only the expression and head angle change).
Expression: head tilted further to the side, eyes open and looking at the key with mild confusion, eyebrows raised, mouth a small wavy line, one small clear sweat drop on her temple. Puzzled, not sad.
```

### G08 · 搜不到 / 筛不到 / 服务器没应答（试样）

```
Pose: half-body down to the knees, facing the viewer. Both flippers spread out to the sides at shoulder height in a shrug gesture.
Expression: head tilted to one side, mouth a small round "o", eyebrows raised, big eyes glancing up and to the side in mild confusion, one small clear sweat drop on her temple. Cute and slightly helpless, not sad.
```

### 其余各张的姿势块（角色块之后、背景块之前）

| 编号 | 姿势块要点 |
|---|---|
| G01 署名照 | head-and-shoulders, perfectly centered, facing the viewer, sweet friendly closed-mouth smile, bright sparkling eyes; a red pencil tucked into the side of the hood above her right ear; hood and face fill ~70 %, even space for a circular crop |
| G01b 眨眼帧 | 「take your G01 … keep EVERYTHING pixel-identical … changing ONLY her eyes: both eyes gently closed in a natural blink」（在同一个对话里改上一张，构图对得上） |
| G04 付印新刊 | full body, side three-quarter view on the left, cheerfully turning the big crank of a small vintage hand-cranked printing press (cream iron, vermilion accents, brass) on the right, a fresh sheet sliding out; delighted |
| G07 加载 | sitting, holding a small stack of cream paper upright against her chest and tapping it straight, eyes peacefully closed into crescents, calm content smile |
| G08 摊手 | full body, both flippers spread wide at shoulder height in a shrug; head tilted, mouth a small round "o", eyebrows raised, a small sweat drop; helpless but cute（试样） |
| G09 搜索 | sitting, raising a red pencil high like a raised hand, hopeful sparkling eyes, small eager smile; one small glossy 3D question mark above her head |
| G10 空的列表 | sitting, holding up one completely blank cream sheet in front of her chest, curious eyes glancing up, mouth a soft "oh" |
| G11 末尾打盹 | （重做）same proportions as G15/G07, SITTING, leaning forward to rest her cheek on a small stack of three colourful closed magazines in her lap, eyes closed into crescents, tiny content smile; one small crescent moon; no bubble on her nose; natural peach skin, no green tint. 第一版趴着、鼻子上有睡泡，被绿幕映绿，作废 |
| G12 盖章 | sitting, pressing a small square wooden seal stamp (vermilion base) down onto a cream sheet; a red heart-shaped stamp mark; cheeks puffed with concentration, proud little smile |
| G13 看完一集 | sitting, clapping, eyes closed into big happy crescents, wide delighted laugh, tiny sparkle stars; simple silhouette for 44 px |
| G15b 密码不对 | 「take your very FIRST image (G15) … keep EVERYTHING the same … changing ONLY her face and head angle」: head tilted further, puzzled eyes on the key, small wavy mouth, sweat drop |
| G17 号外 | （重做）SITTING like the others; the right flipper raised high, firmly gripping a folded newspaper (tip wrapped by the flipper, physically held), waving it like a newsboy; the left flipper cupped beside her open mouth, shouting; joyful crescent eyes. 第一版报纸飘在头后、站姿比例不对，作废 |
| G19 断网 | sitting, holding the end of an unplugged cable with a small cream two-pin plug, puzzled innocent face, mouth "o", a small glossy question mark |
| G21 播放出错 | sitting, holding up a misprinted sheet (a smudged ink blot, blurry grey stripes, no letters), calm gentle slightly sheepish smile — "oops, let's try again" |

## 应用图标（用户选了第 ④ 个，再加「GUGA TV」）

四个方向都是可爱表情的 3D 图，全幅方图（不带圆角，启动器自己裁）：① 画报封面后探头 ② 帽子上的喙做成播放键 ③ 抱着印章 ④ 从老电视里探出来。对比图 `design/generated/v3/icon-concepts.png`。

④ 的提示词要点：the SAME chibi girl happily leans out of the screen of a small cute cream-colored retro CRT television with rounded corners and two little antennas; both flippers rest on the bottom edge of the screen frame; delighted open-mouth smile; vermilion-red knobs and speaker grille; background deep warm charcoal (#100E0B) with a soft warm glow; subject inside the central 70 %.

加牌子（在 ④ 上改）：「keep EVERYTHING identical … Add ONE thing: a small vintage TV brand nameplate on the front bottom bezel, centered below the screen, in the free strip between her two flippers: a rounded-rectangle badge of polished chrome with a vermilion-red enamel inlay, the brand name GUGA TV in clean bold retro sans-serif capitals, cream-colored, slightly embossed」。

位置的理由：老电视的牌子就在屏幕下面的边框上；她两只翅膀搭在左右，正中那一段空着，牌子放在那里居中、不挡她。原图存为 `design/brand/icon-guga-tv.png`，`scripts/art/make-icons.py` 从它生成手机 / 电视的启动图标（含自适应图标的前景）、电视横幅（左边电视、右边「GUGA TV」字标和「咕嘎画报」；用户：不要特意加 EMBY）和 Windows 的 ico / 512 PNG。

## 称号徽章（十级，2026-11-15）

用户：「称号图标，等级都要设计好logo」→ 先出 1 / 6 / 10 级三张试样，用户：「图标logo ok直接做」。原图 `design/generated/v3/badges/raw/R01–R10.png`，抠图后 `badges/R01–R10.png`，总览 `badges/all-badges.png`；应用里是 `drawable/rank_01–10.png`（384 px）。同一个 Gemini 对话里一张接一张出（后面的写「EXACTLY the same style … as the previous badge」），画风才一致。

**通用块**（第一张原样，后面的用「same style as the previous badges」代替）：a single collectible achievement badge for a movie-watching app, high-end 3D render (premium enamel-and-metal pin / designer-toy quality), straight front view, centered and symmetrical; a thick round medallion with a raised beveled metal rim, a glossy cream hard-enamel field (#F1E6D0), ONE bold chunky rounded raised symbol in vermilion (#D9452B) and charcoal (#1A1712); readable at 48 px (one clear symbol about 60 % of the medallion, no clutter); soft studio light from the upper left; pure green #00FF00 background, no shadow, central 75 %, no text / letters / numbers.

**档位**：1–3 铜（暖铜边，素面，无装饰）· 4–6 银（亮银边带一圈圆齿，底部一条空白朱红飘带）· 7–9 金（金边 + 细朱红内圈 + 下半圈月桂 + 空白飘带，无皇冠无宝石）· 10 宗师（同金档再加小皇冠和三颗红宝石、暖光）。

| 级 | 称号 | 图案 |
|---|---|---|
| 1 | 入场观众 | 斜放的电影票根，一边撕口，中间打一个星形孔 |
| 2 | 爆米花党 | 朱红米白竖条纹的爆米花桶，上面堆满爆米花 |
| 3 | 周末影迷 | 朱红双人小沙发，扶手搭一条米白毯子，座上一个遥控器 |
| 4 | 夜场常客 | 炭黑月牙，下角挂一截卷起的朱红胶片，旁边两颗金星（第一版是月亮 + 影院座椅，和 3 级的沙发太像，重做） |
| 5 | 追片达人 | 半开的场记板（朱红米白斜纹拍杆、炭黑板身无字），旁边三道速度线 |
| 6 | 资深影迷 | 胶片盘（五个圆孔），中心一颗金星 |
| 7 | 阅片无数 | 朱红胶片拧成的 ∞，交叉处一颗金星 |
| 8 | 片库行家 | 两层炭黑小片架，放着胶片盒和录像带，顶上一颗金星 |
| 9 | 骨灰影迷 | 胶片围成的心（心里炭黑珐琅），顶上一簇小火苗 |
| 10 | 观影宗师 | 复古放映机（两个片盘），镜头射出一道光，光的尽头一颗金星 |

## 特别称号徽章（十二个，2026-11-15）

盾形，和圆形的等级章分开。先出夜猫子 / 二刷 / 跨年放映三张样稿（`badges/special-sample.png`），用户之前说「都按你的来」，接着出完；总览 `badges/all-specials.png`，应用里 `drawable/special_<id>.png`（320 px）。

**盾的写法**：a rounded heraldic SHIELD (wide at the top, pointed at the bottom, soft rounded corners), thick polished gold rim, thin vermilion enamel inner border, deep CHARCOAL glossy enamel field (#1A1712), one small polished gold star on the top edge; symbol chunky and raised in cream, vermilion and gold；其余（材质、光、绿底、无字）同等级章。

| id | 称号 | 图案 |
|---|---|---|
| night | 夜猫子 | 探头的黑猫脸（米白金色大眼睛）+ 米白月牙 |
| binge | 一口气 | 米白复古小电视，朱红屏幕上金色快进键 |
| triple | 连场王 | 三张扇形展开的电影票（朱红 / 米白 / 金） |
| rewatch | 二刷 | 米白胶片盘 + 朱红循环箭头 + 小金星 |
| fresh | 尝鲜客 | 米白朱红闹钟（两个小铃）+ 金色闪光 |
| classic | 怀旧派 | 复古录像带（空白标签、两个金色带轮） |
| anime | 动画迷 | 米白五角星卡通脸（大眼睛、腮红）+ 两颗小星 |
| marathon | 长跑选手 | 朱红米白跑鞋 + 三道速度线 |
| highscore | 高分猎手 | 米白朱红箭靶，靶心一颗金星，斜插一支箭 |
| omnivore | 题材通吃 | 六色扇形拼成的圆，中心一颗金星 |
| weekends | 周末全勤 | 台历（朱红顶、两个环），最后一列四个金勾，无数字 |
| newyear | 跨年放映 | 朱红金色烟花，中心一个米白胶片盘 |
| morning | 早场观众 | 米白朱红咖啡杯冒热气，后面一轮金色朝阳 |
| daily | 天天见 | 朱红火苗（米白内焰），下面一串七颗金珠 |
| seasondone | 完结撒花 | 朱红米白条纹礼花筒，喷出彩纸、金星、彩带 |
| seasons10 | 追剧王 | 三个叠起的胶片盒（米白 / 朱红 / 米白），顶上一顶金皇冠 |
| epic | 史诗观众 | 金框沙漏、朱红的沙，腰上绕一截米白胶片 |
| brave | 烂片勇士 | 朱红番茄脸，脸上贴创可贴，笑得很勇敢 |
| horror | 胆大包天 | 米白小幽灵（大眼睛、腮红、张嘴「哇」），挥手 |
| comedy | 笑口常开 | 米白喜剧面具大笑，侧边一截朱红飘带 |
| docu | 求知者 | 米白朱红地球仪，顶上一台复古小摄影机 |
| scifi | 科幻迷 | 米白圆顶朱红飞碟，一圈金灯，下面一束光 |
| detective | 神探 | 米白朱红猎鹿帽，前面一把金放大镜，镜里一个脚印 |
| collector | 收藏家 | 朱红金边宝箱，装满米白胶片盘 |

第二批十二个（用户：「特别称号再多一些」）按同一个盾的写法接着出，没有再出样稿。
