/* 设置 / 服务器与登录 / 对话框与右键菜单 / 播放器 / 错误态 */
(function () {
'use strict';
const { ic, im, pad, esc, LIB, ITEM } = APP, S = APP.S;
const sw = (on) => `<span class="swi${on ? ' on' : ''}"><i>开</i><i>关</i></span>`;
const val = (v, opts, open) => `<span class="dd${open ? ' open' : ''}"><b>${v}</b>${ic('chevd')}${opts ? `<div class="ctx">${opts.map((o) => `<a class="${o === v ? 'on' : ''}">${o}</a>`).join('')}</div>` : ''}</span>`;
const step = (v) => `<span class="step"><a>−</a><b>${v}</b><a>+</a></span>`;
const sld = (p, t) => `<span class="sld"><u style="width:${p}%"></u><i style="left:${p}%"></i><b>${t}</b></span>`;
const opt = (t, s, r) => `<div class="opt"><div><b>${t}</b>${s ? `<small>${s}</small>` : ''}</div>${r}</div>`;
const h5 = (t) => `<h5>${t}</h5>`;

// ------------------------------------------------------------------ 设置（两栏；分区名与原生应用的设置树一致，个别说明文字为示意）
const SUBDEMO = `<div class="prev">${im('56342-backdrop')}<div class="subline" style="font-size:2.4rem">江大人，这一路，怕是不太平。</div></div>`;
const SECS = [
  ['look', '外观', 'APPEARANCE', '只有深色一种纸。这里调的是纸上的东西：专色、动效、字号。', () => h5('PAPER · 版面') + opt('专色跟随封面', '翻到哪一期，栏目名、编号、进度线换成那张海报的专色；关掉后固定为朱红。', sw(1)) + opt('界面字号', '只放大正文和目录，报头与大号数字不变。', val('标准', ['紧凑', '标准', '大', '特大'])) + opt('海报悬停倾斜', '鼠标移到海报上，海报朝鼠标倾一点、带一道高光。', sw(1)) + opt('减少动效', '关掉翻封面的惯性、印章落下的回弹和里程表滚动。', sw(0)) +
    h5('GLASS · 固定层') + opt('导航与圆钮的液态玻璃', '只有报头下面那一排（导航、搜索、账号、返回）是玻璃；显卡不支持时自动换成半透明底。', val('自动', ['自动', '始终开启', '关闭'])) + opt('窗口控制', '最小化 / 最大化 / 关闭画在报头右端。', val('画在报头里', ['画在报头里', '系统标题栏']))],
  ['ui', '界面', 'INTERFACE', '首页的目录怎么排，卡片上显示什么。', () => h5('HOME · 首页板块') + ['本期封面（海报流）', '继续观看', '最近添加', '媒体库', '电影栏', '动漫栏'].map((n, i) => opt(`<span class="num" style="color:var(--faint);margin-right:1.2rem">${pad(i + 1)}</span>${n}`, '', `<span style="display:flex;gap:1.2rem;align-items:center"><a class="kbd">↑</a><a class="kbd">↓</a>${sw(i !== 5)}</span>`)).join('') + `<a class="lnk" style="margin-top:1.6rem">恢复默认顺序</a>` +
    h5('CARDS · 卡片') + opt('海报墙每行列数', '自动 = 跟窗口宽度走（1100 → 6 列，1440 → 7 列，3168 → 12 列以上）。', val('自动', ['自动', '少一列（大图）', '多一列（密）'])) + opt('卡片下显示评分', '', sw(1)) + opt('未看的条目显示集数角标', '', sw(0)) + opt('启动时打开', '', val('首页', ['首页', '片库', '继续上次的页面']))],
  ['play', '播放', 'PLAYBACK', '播放引擎、默认行为、快捷键。', () => h5('ENGINE · 播放引擎') + opt('首选引擎', 'Windows 上自动回退：mpv → VLC → mpv 重试 → 服务器转码。外部播放器只在这里手动选。', val('自动（mpv）', ['自动（mpv）', 'mpv', 'VLC（本机已安装）', '外部播放器…'])) + opt('码率上限', '超过上限时请求服务器转码。', val('不限（原画）', ['不限（原画）', '40 Mbps', '20 Mbps', '8 Mbps'])) + opt('帧率上限', '', val('跟随显示器', ['跟随显示器', '60', '30'])) +
    h5('BEHAVIOUR · 播放行为') + opt('自动播放下一集', '片尾出现「下一集」卡片，倒数 10 秒。', sw(1)) + opt('跳过片头 / 片尾', '服务器有章节标记时出现「跳过片头」；选「自动」就直接跳。', val('显示按钮', ['显示按钮', '自动跳过', '关'])) + opt('默认倍速', '', step('1.0×')) + opt('快进 / 快退步长', '← → 键每按一次。', step('10 秒')) + opt('记住每部剧的音轨和字幕', '', sw(1)) +
    h5('KEYS · 快捷键') + `<table class="tb" style="margin-top:.4rem">${[['空格 / K', '播放 / 暂停'], ['← →', '快退 / 快进'], ['↑ ↓', '音量'], ['F / 双击', '全屏'], ['[ ]', '倍速 − / +'], ['N', '下一集'], ['S / A / D', '字幕 / 音轨 / 弹幕面板'], ['Esc', '收起面板 / 退出全屏']].map((r) => `<tr><td class="n" style="width:16rem;color:var(--ink)">${r[0]}</td><td>${r[1]}</td></tr>`).join('')}</table>`],
  ['strm', 'strm 直链', 'STRM', '.strm 条目只是一行地址。直连时由播放内核自己打开，不再经过加速器。', () => h5('ROUTE · 线路') + opt('优先直连 strm 目标', '关掉后一律走服务器中转（慢，但不暴露你的地址）。', sw(1)) + opt('线路', '直连失败时的顺序。', val('直连 → 系统代理 → 服务器中转', ['直连 → 系统代理 → 服务器中转', '只用系统代理', '只走服务器中转'])) + opt('跟随 302 跳转', '网盘直链一般要开。', sw(1)) + opt('连接超时', '', step('15 秒')) +
    h5('TEST · 测试') + opt('测试连接', '用片库里的一个 strm 条目试一次，报告每一跳的耗时。', `<a class="btn sm line">开始测试</a>`) + `<p class="lead" style="margin-top:1.6rem;font-style:italic">上次测试：直连 76 ms · 302 一次 · 可拖动（数值为示意）</p>`],
  ['sub', '字幕与音轨', 'SUBTITLES & AUDIO', '默认选哪条，字幕长什么样。下面的预览跟着改。', () => SUBDEMO + h5('DEFAULTS · 默认选择') + opt('首选音轨语言', '', val('国语 → 原声', ['国语 → 原声', '原声', '粤语', '日语', '英语'])) + opt('首选字幕语言', '', val('简体中文', ['简体中文', '繁體中文', 'English', '双语（中上英下）'])) + opt('字幕模式', '', val('外语片才显示', ['始终显示', '外语片才显示', '只显示强制字幕', '关'])) +
    h5('STYLE · 样式') + opt('字体', '', val('微软雅黑', ['微软雅黑', '思源黑体', '霞鹜文楷', '跟随 ASS 样式'])) + opt('字号', '', sld(56, '中')) + opt('粗细', '', val('半粗', ['常规', '半粗', '粗'])) + opt('阴影强度', '', sld(70, '70%')) + opt('位置', '离画面下沿的距离。', sld(18, '6%')) + opt('时间偏移', '字幕早了或晚了时用。', step('0.0 秒'))],
  ['dm', '弹幕', 'DANMAKU', '弹幕来自弹弹 Play 兼容接口，按 TMDB 身份自动匹配。', () => h5('SOURCE · 弹幕内容') + opt('开启弹幕', '', sw(1)) + opt('自动匹配', '打开一集时自动找弹幕；匹配不准可以在播放器里手动换。', sw(1)) + opt('来源优先级', '', val('B 站 → 巴哈 → 其他', ['B 站 → 巴哈 → 其他', '只要 B 站', '全部合并'])) + opt('屏蔽词', '已有 12 条，「剧透」「前方高能」在内。', `<a class="lnk">编辑</a>`) +
    h5('DISPLAY · 弹幕显示') + opt('显示区域', '', val('上 1/4 屏', ['上 1/4 屏', '上半屏', '全屏'])) + opt('不透明度', '', sld(80, '0.80')) + opt('字号', '', sld(45, '中')) + opt('滚动速度', '', sld(50, '1.00')) + opt('同屏上限', '（受字号限制）', step('60')) + opt('弹幕类型', '', `<span style="display:flex;gap:.8rem"><a class="chip on">滚动</a><a class="chip on">顶部</a><a class="chip">底部</a></span>`) +
    h5('RENDER · 渲染性能') + opt('性能档位', '', val('自动', ['自动', '流畅优先', '画质优先'])) + opt('进度条上显示弹幕热力图', '', sw(1))],
  ['cache', '缓存', 'CACHE', '图片和弹幕存在本机，清掉不影响片库。', () => h5('USAGE · 占用') + `<div class="opt"><div><b>图片缓存</b><small>海报、剧照、演员照。上限 256 MB，满了先丢最久没看的。</small><div class="pbar" style="max-width:52rem"><u style="width:63%"></u></div></div><span style="display:flex;gap:1.6rem;align-items:center"><span class="num" style="font-size:2.4rem">161 <small style="font-size:1.2rem">/ 256 MB</small></span><a class="btn sm line">清除</a></span></div>` + opt('弹幕缓存', '248 集 · 18.4 MB', `<a class="btn sm line">清除弹幕缓存</a>`) + opt('查询缓存', '首页和片库的上一次结果，用来秒开；7 天不用自动过期。', `<a class="btn sm line">清除</a>`) + opt('缓存位置', '%LOCALAPPDATA% 下的应用目录', `<a class="lnk">打开文件夹</a>`) + `<p class="lead" style="margin-top:1.6rem;font-style:italic">占用数字为示意。</p>`],
  ['acct', '账号与服务器', 'ACCOUNT', '当前登录在哪一家片库。不显示地址，除非你点「显示地址」。', () => h5('CURRENT · 当前') + opt('影迷', '家里的片库 · Emby 4.9 · 已连接 · 延迟 4 ms', `<a class="lnk" data-nav="account&st=switch">切换</a>`) + opt('服务器地址', '••••••••••••••••', `<a class="lnk">显示地址</a>`) + opt('访问令牌', '••••••••••••', `<a class="lnk">显示令牌</a>`) + opt('上报播放进度', '看到哪儿会写回服务器，别的设备可以接着看。', sw(1)) +
    h5('SERVICES · 服务与密钥') + opt('TMDB', '片名 logo、弹幕匹配用。', `<span class="chip on">已配置</span>`) + opt('弹幕接口', '', `<span class="chip">未配置</span>`) + h5('SESSION · 会话') + opt('管理服务器', '', `<a class="lnk" data-nav="servers">打开</a>`) + opt('退出登录', '只清掉这台电脑上的会话。', `<a class="btn sm line" data-nav="servers&st=login">退出</a>`)],
  ['about', '关于与检查更新', 'ABOUT', '', () => `<div class="about"><div class="logo2">${im('brand_icon', '', 'width:100%;height:100%')}</div><div><div class="kick">VERSION 0.2.13 · Windows · x64</div><h2 style="margin:.8rem 0">咕咕嘎嘎</h2><p class="lead" style="margin:0">第三方 Emby 客户端。主编：咕咕。</p></div><div style="margin-left:auto;text-align:right"><a class="btn" data-nav="dialogs">${ic('dl')}检查更新</a><p class="lead" style="margin:1rem 0 0;font-style:italic">上次检查：今天 21:40 · 已是最新</p></div></div>` +
    opt('自动检查更新', '启动时看一眼有没有「号外」。', sw(1)) + opt('更新通道', '', val('正式版', ['正式版', '测试版'])) + opt('能力清单', 'mpv 内核 · HEVC / AV1 硬解 · HDR 直通 · 杜比视界：不支持', `<a class="lnk">查看</a>`) + opt('开源许可', '', `<a class="lnk">查看</a>`) + opt('导出诊断日志', '不含地址和令牌。', `<a class="btn sm line">导出</a>`)],
];
S.settings = {
  g: '设置', title: '设置', note: '两栏：左侧九个分区常驻（编号目录），右侧一屏一个分区。开关是「开 / 关」两个字，选择器是带下拉的细框。', states: [...SECS.map((s, i) => [i ? s[0] : '', s[1]]), ['dd', '选择器打开']],
  build(st) { const k = st === 'dd' ? 'play' : (st || 'look'), sec = SECS.find((s) => s[0] === k) || SECS[0];
    return { tab: 4, sub: 1, solid: 1, html: `<div class="scroll"><div class="page set"><nav>${SECS.map((s, i) => `<a data-nav="settings${i ? '&st=' + s[0] : ''}" class="${s === sec ? 'on' : ''}"><em>${pad(i + 1)}</em>${s[1]}<small>${s[2].split(' ')[0]}</small></a>`).join('')}<p style="font-size:1.15rem;color:var(--faint);font-style:italic;margin-top:1.6rem;line-height:1.8">Ctrl , 打开设置<br>↑ ↓ 换分区 · Esc 返回</p></nav>
      <div><h2>${sec[1]}<em>${sec[2]}</em></h2><p class="lead">${sec[3]}</p>${sec[4]()}</div></div></div>` }; },
  init(root, st) { root.addEventListener('click', (e) => { const s = e.target.closest('.swi'); if (s) s.classList.toggle('on'); const d = e.target.closest('.opt .dd'); root.querySelectorAll('.opt .dd.open').forEach((x) => { if (x !== d) x.classList.remove('open'); });
      if (d) { const a = e.target.closest('.ctx a'); if (a) { d.querySelector('b').textContent = a.textContent; d.querySelectorAll('.ctx a').forEach((x) => x.classList.toggle('on', x === a)); d.classList.remove('open'); } else d.classList.toggle('open'); } const c = e.target.closest('.opt .chip'); if (c) c.classList.toggle('on'); });
    if (st === 'dd') root.querySelector('.opt .dd').classList.add('open'); },
};

// ------------------------------------------------------------------ 服务器列表 / 添加服务器 / 登录（第一次打开；没有导航，只有报头）
const firstL = (k, h, lead) => `<div class="l"><div class="kick">${k}</div><h1>${h}</h1><p class="lead">${lead}</p><div class="wall3">${LIB.latest['电影'].slice(0, 8).map((x) => im(APP.poster(x))).join('')}</div></div>`;
S.servers = {
  g: '服务器 · 登录', title: '服务器列表 / 添加 / 登录', note: '第一次打开的三步：选片库 → 填地址 → 登录。输密码时她闭上眼睛。地址只有占位示例。', states: [['', '服务器列表'], ['add', '添加服务器'], ['login', '登录'], ['pwd', '输入密码（她闭眼）']],
  build(st) {
    const R = st === 'add' ? `<div class="kick">STEP 1 / 2 · 添加服务器</div><label class="field"><span>服务器地址</span><input placeholder="https://你的服务器:端口" value=""><small class="ok">✓ 找到了：Emby Server 4.9 · 延迟 4 ms（示意）</small></label><label class="field"><span>备注名（只在这台电脑上显示）</span><input value="家里的片库"></label>
      <div class="opt" style="margin-top:1.6rem"><div><b>在局域网里找一找</b><small>同一网络里的 Emby 会自己出现在这里。</small></div><a class="btn sm line">${ic('search')}扫描</a></div><div class="acts" style="display:flex;gap:1.6rem;margin-top:3.2rem;align-items:center"><a class="btn" data-nav="servers&st=login">下一步 · 登录</a><a class="lnk" data-nav="servers">返回列表</a></div>`
    : st === 'login' || st === 'pwd' ? `<div class="kick">STEP 2 / 2 · 登录「家里的片库」</div><div class="note" style="margin:2rem 0 0">${APP.msc('round', 64, st === 'pwd' ? 'data-mood="sleep"' : '')}<div><em>主编 · 咕咕</em><p class="say">${st === 'pwd' ? '我闭上眼了，你输。' : '办一张读者证，我好给你留页码。'}</p></div></div>
      <label class="field"><span>用户名</span><input value="影迷" id="u"></label><label class="field"><span>密码</span><input type="password" value="${st === 'pwd' ? '••••••' : ''}" placeholder="没有密码就留空" id="pw"></label>
      <div class="opt" style="margin-top:1.2rem"><div><b>记住我</b><small>令牌存在系统的凭据管理器里。</small></div>${sw(1)}</div><div style="display:flex;gap:1.6rem;margin-top:3.2rem;align-items:center"><a class="btn" data-nav="home">登录<small>Enter</small></a><a class="lnk" data-nav="servers">换一个服务器</a></div>`
    : `<div class="kick">SERVERS · 已保存的片库</div>${[['家里的片库', '局域网 · Emby 4.9 · 影迷', '可连接 · 4 ms', 1], ['办公室 NAS', '外网 · Emby 4.8 · 影迷', '可连接 · 38 ms', 0], ['朋友的服务器', '外网 · guest', '连不上 · 3 天前还在', 0]].map((s) => `<a class="srv${s[3] ? ' on' : ''}" data-nav="servers&st=login"><b>${s[0]}</b><span>${s[1]}</span><div class="st">${s[2]}${ic('chevr')}</div></a>`).join('')}
      <div style="display:flex;gap:1.6rem;margin-top:3.2rem;align-items:center"><a class="btn" data-nav="servers&st=add">${ic('plus')}添加服务器</a><span style="font-size:1.2rem;color:var(--faint);font-style:italic">右键一行：改备注名 / 移除</span></div>`;
    return { solid: 1, html: `<div class="scroll"><div class="first">${firstL('WELCOME · 创刊号', st === 'add' ? '告诉我<br>片库在哪儿' : st ? '办一张<br>读者证' : '订阅<br>哪一家片库？', '咕咕嘎嘎把你的 Emby 片库编成一本每天出刊的电影画报。连上之后，封面、目录、专色都是你自己的片子。')}<div class="r">${R}</div></div></div>`, noglass: 1 };
  },
  init(root) { const pw = root.querySelector('#pw'), m = APP.mascot(); if (pw && m) { pw.addEventListener('focus', () => { m.setMood('sleep'); m.say('我闭上眼了，你输。', 0); }); pw.addEventListener('blur', () => { m.setMood('idle'); m.hush(); }); } },
};

// ------------------------------------------------------------------ 更新提示（号外）+ 右键菜单
S.dialogs = {
  g: '对话框 · 右键菜单', title: '更新提示 / 右键菜单', note: '更新提示排成一张「号外」。右键菜单：日常操作在上，「管理」单独一组、子菜单里才有删除，删除在原地变成二次确认。', states: [['', '更新提示（号外）'], ['ctx', '右键菜单 · 日常'], ['mng', '右键菜单 · 管理'], ['del', '删除 · 二次确认']],
  build(st) { const base = S.wall.build('', new URLSearchParams('lib=' + encodeURIComponent('电影')));
    if (!st) base.html += `<div class="dim"><div class="sheet extra"><a class="x">${ic('x')}</a><div class="mastx"><b>号外</b><em>EXTRA · NEW VERSION</em><span>v0.2.13 → v0.3.0</span></div><h3>新版排好了，要现在换版吗？</h3>
      <ol>${['电脑端换成「头版」版式：封面跨页、报头即标题栏', '海报墙列数跟着窗口走，3168 宽排到十二列以上', '航海王这样的长剧：按季的标尺 + 每 50 集一页', '播放器面板不再挡画面，画面让到一边', '修好：strm 条目拖动后偶尔黑屏', '修好：字幕时间偏移不记忆'].map((t, i) => `<li><em>${i + 1}</em>${t}</li>`).join('')}</ol>
      <div class="note" style="margin-top:2rem;border-bottom:0;padding-bottom:0">${APP.pbox({ wl: 60, wr: 60, eyes: '^', mouth: 'D', props: ['sheet'] }, '举着刚印好的号外', '86 MB · 下载完重启一次就好，看到一半的进度我替你记着。（大小、条目为示意）', 7)}</div>
      <div class="acts"><a class="lnk" style="margin-right:auto">这一版先跳过</a><a class="btn line">稍后提醒</a><a class="btn">${ic('dl')}下载并更新</a></div></div></div>`;
    return base; },
  init(root, st) { return S.wall.init.call(S.wall, root, ''); },
  after(root, st) { if (!st) return; const pc = root.querySelectorAll('.pc')[9], r = pc.getBoundingClientRect(); pc.classList.add('hov'); APP.ctx(r.left + r.width * 0.6, r.top + r.height * 0.3, ITEM[pc.dataset.ctx], st !== 'ctx', st === 'del'); },
};

// ------------------------------------------------------------------ 播放器
const PAN = {
  sub: ['字幕', 'SUBTITLES', (S0) => [['关', ''], ['简体中文', 'ASS · 内封 · 默认'], ['繁體中文', 'ASS · 内封'], ['English', 'SRT · 外挂'], ['双语（中上英下）', '合成']].map((o, i) => `<a class="o${i === 1 ? ' on' : ''}"><div>${o[0]}<small>${o[1]}</small></div>${i === 1 ? '<span class="num">✓</span>' : ''}</a>`).join('') + '<h6>STYLE · 样式</h6>' + opt('字号', '', sld(56, '中')) + opt('位置', '', sld(18, '6%')) + opt('时间偏移', '', step('0.0 秒')) + `<a class="lnk" style="margin-top:1.6rem">在线搜索字幕…</a>`],
  audio: ['音轨', 'AUDIO', () => [['国语', 'AAC · 2 声道 · 默认'], ['国语 5.1', 'EAC3 · 6 声道'], ['粤语', 'AAC · 2 声道']].map((o, i) => `<a class="o${i === 0 ? ' on' : ''}"><div>${o[0]}<small>${o[1]}</small></div>${i === 0 ? '<span class="num">✓</span>' : ''}</a>`).join('') + '<h6>SYNC · 同步</h6>' + opt('音画同步', '声音早了往右调。', step('0 毫秒')) + opt('音量增强', '', sld(40, '100%')) + opt('响度均衡', '夜里看片时把对白和爆炸拉近。', sw(0))],
  speed: ['倍速', 'SPEED', () => `<div class="big">${['0.5', '0.75', '1.0', '1.25', '1.5', '2.0', '3.0', '4.0'].map((s, i) => `<a class="${i === 2 ? 'on' : ''}">${s}</a>`).join('')}</div><h6>FINE · 微调</h6>` + opt('精确倍速', '[ 和 ] 键每次 0.05。', step('1.00×')) + opt('长按 → 临时 2 倍', '松开恢复。', sw(1)) + opt('变速不变调', '', sw(1))],
  quality: ['画质 · 版本', 'QUALITY', () => [['原画 · 直接播放', '1080p · H.264 · 8.2 Mbps · mpv 内核', 1], ['转码 1080p', '8 Mbps · 服务器转码'], ['转码 720p', '4 Mbps · 服务器转码'], ['转码 480p', '1.5 Mbps · 省流量']].map((o, i) => `<a class="o${i === 0 ? ' on' : ''}"><div>${o[0]}<small>${o[1]}</small></div>${i === 0 ? '<span class="num">✓</span>' : ''}</a>`).join('') + '<h6>ENGINE · 播放引擎</h6>' + [['mpv', '当前 · 硬解 d3d11va'], ['VLC', '本机已安装'], ['外部播放器…', '手动选择']].map((o, i) => `<a class="o${i === 0 ? ' on' : ''}"><div>${o[0]}<small>${o[1]}</small></div></a>`).join('') + '<h6>STATS · 实时</h6><p style="font-size:1.25rem;line-height:1.9;color:var(--dim);font-style:italic">丢帧 0 · 缓冲 38 秒 · 网速 3.2 MB/s<br>视频 1920 × 1080 @ 25 · SDR（数值为示意）</p>'],
  dm: ['弹幕', 'DANMAKU', () => opt('显示弹幕', 'D 键开关。', sw(1)) + opt('密度', '', sld(55, '适中')) + opt('不透明度', '', sld(80, '0.80')) + opt('字号', '', sld(45, '中')) + opt('显示区域', '', val('上 1/4 屏')) + opt('滚动速度', '', sld(50, '1.00')) + '<h6>MATCH · 匹配</h6>' + `<a class="o on"><div>颜心记 第 5 集<small>弹弹 Play · 1,284 条 · 自动匹配</small></div><span class="num">✓</span></a><a class="lnk" style="margin-top:1.4rem">匹配不对？手动搜索…</a>` + '<h6>FILTER · 屏蔽</h6>' + `<div style="display:flex;gap:.8rem;flex-wrap:wrap">${['剧透', '前方高能', '第一', '+ 添加'].map((t) => `<a class="chip">${t}</a>`).join('')}</div>`],
};
const DM = [['这场戏的灯光绝了', 0, 4, 2], ['前方高能', 1, 30, 2], ['南星：我只是个大夫', 0, 58, 2], ['组队成功', 2, 12, 34], ['江大人又在装冷静', 0, 44, 34], ['二刷来了', 1, 76, 34], ['脸盲认人全靠衣服', 0, 22, 66], ['注意右边那位的眼神', 2, 62, 66]];
S.player = {
  g: '播放器', title: '播放器', sizes: 1, bareSize: 1, note: '控件出现时画面退一步：四边让出页边，顶栏、时间轴、功能键都排在页边里；打开面板，画面再往左让，面板排在右边空出来的一栏。画面和字幕一个像素都不挡。',
  states: [['', '控件显示'], ['idle', '播放中（控件隐藏）'], ['pause', '暂停'], ['hover', '进度条悬停预览'], ['eps', '选集'], ['sub', '字幕'], ['audio', '音轨'], ['speed', '倍速'], ['quality', '画质 / 版本'], ['dm', '弹幕设置'], ['skip', '跳过片头'], ['next', '片尾 · 下一集'], ['buf', '缓冲中'], ['err', '播放出错']],
  build(st, Q) {
    const mv = Q.get('mv'), it = this.it = mv ? ITEM['39778'] : ITEM['56342'], ser = LIB.series['颜心记'], ep = ser.episodes[4], nx = ser.episodes[5], total = mv ? it.runtimeSec : 2701, p = this.p = st === 'skip' ? 0.022 : st === 'next' ? 0.968 : mv ? 0.31 : 1590 / 2701;
    const heat = Array.from({ length: 140 }, (_, i) => { const x = i / 140, v = Math.abs(Math.sin(i * 0.37) + 0.6 * Math.sin(i * 0.11 + 1) + 0.35 * Math.sin(i * 1.3 + 2)) / 1.95; return 0.1 + 0.9 * v * (0.55 + 0.45 * Math.sin(x * 3.1)); });
    const btn = (k, icn, t, v) => `<a data-p="${k}" class="${st === k ? 'on' : ''}" title="${t}">${ic(icn)}<span>${t}</span>${v ? `<small>${v}</small>` : ''}</a>`;
    this.total = total; this.eps = ser.episodes.slice(0, 12);
    this.nextHtml = `<h4>下一集<em>UP NEXT · 片尾</em><a data-px>${ic('x')}</a></h4><div class="nx"><div class="th">${im(APP.key(nx.thumb))}</div><div class="kick" style="margin-top:1.6rem">第 ${nx.e} 集 · 8 秒后自动播放</div><h5>${esc(nx.name)}</h5><p>${esc(nx.overview || '')}</p><div class="pbar"><u style="width:22%"></u></div><div class="cta" style="margin-top:2rem;gap:1.2rem"><a class="btn sm">${ic('play')}现在看<small>Enter</small></a><a class="btn sm line">取消<small>Esc</small></a></div></div><h6>THEN · 再往后</h6>${ser.episodes.slice(6, 9).map((e) => `<a class="o"><em>${pad(e.e)}</em><div class="th">${e.thumb ? im(APP.key(e.thumb)) : ''}</div><div><b>${esc(e.name)}</b><small>${e.played ? '已看' : '45 分'}</small></div></a>`).join('')}<div class="note" style="margin-top:2rem;border-bottom:0">${APP.pbox({ wl: 50, wr: 50, eyes: '^', mouth: 'D' }, '看完一集：坐着鼓掌（H15）', '第 5 集看完了，校对勾我替你画上了。', 6)}</div>`;
    return { bare: 1, cls: 'pb', html: `<div class="pl${st === 'idle' ? ' idle' : ''}${st === 'pause' ? ' paused' : ''}${PAN[st] || st === 'eps' || st === 'next' ? ' open' : ''}${Q.get('full') ? ' full' : ''}" id="pl">
<div class="pv"><div class="fr">${im(APP.bd(it))}${st === 'err' ? '' : `<div class="dmk">${DM.map((d) => `<span class="c${d[1]}" style="--x:${d[2]}%;top:${d[3]}%">${d[0]}</span>`).join('')}</div><div class="subline">${mv ? '陀螺停下之前，别急着醒。' : '江大人，这一路，怕是不太平。'}</div>`}
  ${st === 'buf' ? `<div class="pmsg"><div><div class="ring"></div><b>缓冲中 · 62%</b><span>3.2 MB/s · 直连 · 再有 2 秒</span></div></div>` : ''}
  ${st === 'err' ? `<div class="perr"><div><div class="kick">PLAYBACK ERROR · 这一页印坏了</div><h3>这一集放不出来</h3><p>mpv 打不开这条 strm 直链：对方服务器返回 403。已经按顺序试过 mpv → VLC → mpv 重试，都不行。可以让服务器转码再放，或者换一条线路。</p>
    <div class="acts"><a class="btn">${ic('replay')}转码播放</a><a class="btn line">换线路：系统代理</a><a class="lnk">复制诊断信息</a><a class="lnk" data-nav="series">返回详情</a></div><div class="log">open: HTTP 403 Forbidden · 已重试 3 次 · 21:47:12（日志为示意，不含地址）</div></div></div>` : ''}</div></div>
<div class="pedge l"><span class="vert">${mv ? esc(it.name) + ' · 2010' : `第 1 季 · 第 ${ep.e} 集 ·《${esc(ep.name)}》`}</span></div>
<div class="pedge r"><div class="volm"><b>72</b><u style="height:72%"></u>${ic('vol')}</div></div>
<div class="ptop"><a class="pbtn" data-back title="返回（Esc）">${ic('back')}</a><div class="t">${APP.title(it)}<b>${mv ? '' : `第 ${ep.e} 集 · ${esc(ep.name)}`}</b><span>${mv ? '1080p · 直接播放 · mpv' : '1080p · 直接播放 · mpv'}</span></div>
  <div class="seat">${APP.msc('round', 44)}<div><b>PAUSED · 暂停</b>停在 <span class="tcur">${APP.clock(p * total)}</span>，我替你夹着页</div></div>
  <span class="favbox" style="display:flex;gap:1rem;align-items:center">${APP.favSlot()}<a class="pbtn" data-fav title="收藏（盖章）">${ic('heart')}</a></span><a class="pbtn" title="画中画">${ic('pip')}</a>${APP.winKeys()}</div>
<div class="pbot"><div class="rowb"><a class="big" data-act="play" title="播放 / 暂停（空格）">${ic(st === 'pause' ? 'play' : 'pause')}</a><a title="上一集">${ic('prev')}</a><a title="下一集（N）">${ic('next')}</a><a title="后退 10 秒（←）">${ic('rew')}</a><a title="前进 10 秒（→）">${ic('fwd')}</a>
  <span class="time"><span class="tcur">${APP.clock(p * total)}</span> <i>/ ${APP.clock(total)}</i></span><span class="kbd hintk">空格 暂停 · ← → 10 秒 · ↑ ↓ 音量</span><span class="sp"></span>
  ${mv ? '' : btn('eps', 'list', '选集', '5 / 40')}${btn('sub', 'sub', '字幕', '简中')}${btn('audio', 'audio', '音轨', '国语')}${btn('speed', 'speed', '倍速', '1.0×')}${btn('quality', 'hd', '画质', '原画')}${btn('dm', 'dm', '弹幕', '开')}<a data-act="full" title="全屏（F）">${ic('full')}</a></div>
  <div class="tl${st === 'hover' ? ' hov' : ''}"><div class="heat">${heat.map((h, i) => `<i class="${i / 140 < p ? 'on' : ''}" style="height:${(h * 100).toFixed(0)}%"></i>`).join('')}</div><div class="bar"><span class="bf" style="width:${Math.min(100, p * 100 + 9)}%"></span><u style="width:${p * 100}%"></u></div><i class="knob" style="left:${p * 100}%"></i>
   <span class="ch" style="left:0">片头</span><span class="ch" style="left:3.4%">正片</span><span class="ch" style="left:95.6%">片尾</span>
   <i class="hl" style="left:72%"></i><div class="pvw" style="left:72%">${im(mv ? '39778-thumb' : APP.key(ser.episodes[6].thumb))}<b>${APP.clock(0.72 * total)}</b><span>弹幕 86 条 / 分</span></div></div></div>
<div class="pside"><div class="in" id="side"></div></div></div>`,
      extra: (st === 'skip' ? `<a class="g pill" id="g-skip" data-mat="bar" data-press>${ic('fwd')}跳过片头<kbd style="font-family:Georgia;font-style:italic;font-size:1.2rem;opacity:.7">Enter</kbd></a>` : '') +
        (st === 'nextglass' ? `<div class="g" id="g-next" data-mat="pop"><div class="th">${im(APP.key(nx.thumb))}</div><div><small>NEXT · 8 秒后播放</small><b>第 ${nx.e} 集 · ${esc(nx.name)}</b><span><u>现在看</u><i style="font-style:normal">取消</i></span></div></div>` : '') };
  },
  init(root, st) {
    const pl = root.querySelector('#pl'), side = root.querySelector('#side'), T = this, tl = root.querySelector('.tl'); let open = PAN[st] || st === 'eps' || st === 'next' ? st : null, idleT;
    const panel = () => { pl.classList.toggle('open', !!open); root.querySelectorAll('[data-p]').forEach((a) => a.classList.toggle('on', a.dataset.p === open));
      side.innerHTML = !open ? '' : open === 'next' ? T.nextHtml : open === 'eps' ? `<h4>选集<em>EPISODES · 第 1 季</em><a data-px>${ic('x')}</a></h4>${T.eps.map((e) => `<a class="o${e.e === 5 ? ' on' : ''}${e.played ? ' seen' : ''}"><em>${pad(e.e)}</em><div class="th">${e.thumb ? im(APP.key(e.thumb)) : ''}</div><div><b>${esc(e.name)}</b><small>${e.e === 5 ? '正在播放 · 看到 26:30' : e.played ? '已看' : '45 分'}</small></div>${APP.mk(e.played)}</a>`).join('')}<p style="font-size:1.2rem;color:var(--faint);font-style:italic;padding:1.6rem 0">共 40 集 · 往下滚继续</p>`
        : `<h4>${PAN[open][0]}<em>${PAN[open][1]}</em><a data-px>${ic('x')}</a></h4>${PAN[open][2]()}`; APP.fill(side); APP.anim(700); };
    const pause = (on) => { pl.classList.toggle('paused', on); root.querySelector('[data-act="play"] use').setAttribute('href', on ? '#i-play' : '#i-pause'); };
    const wake = () => { pl.classList.remove('idle'); clearTimeout(idleT); if (!APP.shot && !open && !pl.classList.contains('paused')) idleT = setTimeout(() => pl.classList.add('idle'), 3200); APP.anim(600); };
    root.addEventListener('click', (e) => { const c = (s) => e.target.closest(s);
      if (c('[data-back]')) { history.length > 1 ? history.back() : APP.nav('series'); return; } if (c('[data-px]')) { open = null; panel(); return; }
      const p = c('[data-p]'); if (p) { open = open === p.dataset.p ? null : p.dataset.p; panel(); return; }
      if (c('[data-act="play"]') || (c('.fr') && !c('.perr'))) { pause(!pl.classList.contains('paused')); wake(); return; }
      if (c('[data-act="full"]')) { pl.classList.toggle('full'); return; }
      const o = c('.pside .o'); if (o && !c('.mk')) { o.parentElement.querySelectorAll('.o').forEach((x) => { if (x.previousElementSibling && x.previousElementSibling.tagName === 'H6') return; }); o.classList.add('on'); let s = o.previousElementSibling; while (s && s.classList.contains('o')) { s.classList.remove('on'); s = s.previousElementSibling; } s = o.nextElementSibling; while (s && s.classList.contains('o')) { s.classList.remove('on'); s = s.nextElementSibling; } }
      const b = c('.pside .big a'); if (b) b.parentElement.querySelectorAll('a').forEach((x) => x.classList.toggle('on', x === b)); const s = c('.pside .swi'); if (s) s.classList.toggle('on');
    });
    window.addEventListener('mousemove', wake);
    window.addEventListener('keydown', (e) => { if (e.key === ' ' || e.key.toLowerCase() === 'k') { e.preventDefault(); pause(!pl.classList.contains('paused')); } if (e.key === 'Escape') { if (open) { open = null; panel(); } } const m = { s: 'sub', a: 'audio', d: 'dm' }[e.key.toLowerCase()]; if (m) { open = open === m ? null : m; panel(); } if (e.key.toLowerCase() === 'f') pl.classList.toggle('full'); wake(); });
    tl.addEventListener('mousemove', (e) => { const r = tl.getBoundingClientRect(), x = APP.clamp((e.clientX - r.left) / r.width, 0, 1); tl.querySelector('.hl').style.left = tl.querySelector('.pvw').style.left = (x * 100) + '%'; tl.querySelector('.pvw b').textContent = APP.clock(x * T.total); });
    tl.addEventListener('click', (e) => { const r = tl.getBoundingClientRect(), x = APP.clamp((e.clientX - r.left) / r.width, 0, 1); tl.querySelector('.bar u').style.width = tl.querySelector('.knob').style.left = (x * 100) + '%'; tl.querySelectorAll('.heat i').forEach((h, i, a) => h.classList.toggle('on', i / a.length < x)); root.querySelectorAll('.tcur').forEach((t) => { t.textContent = APP.clock(x * T.total); }); });
    if (open) panel(); return APP.setSpot(APP.poster(this.it));
  },
};

// ------------------------------------------------------------------ 错误态
const ERR = {
  '': ['OFFLINE · 停刊通知', '印刷机<br>连不上网', '这台电脑现在没有网络。已经印好的还在：看过的封面和目录可以翻，但没法播放。网络回来后我会自己重新连接。', '网络不可用 · 已自动重试 3 次 · 下次重试 00:12', { wl: 20, wr: 70, eyes: 'x', mouth: 'o', props: ['qm'] }, '举着拔掉的插头（H16）', [['重试', 'replay'], ['打开网络设置', '']]],
  server: ['SERVER · 片库没有应答', '「家里的片库」<br>没开门', '网络是通的，但片库 15 秒没有应答。可能是服务器关机、休眠，或者换了地址。', '连接超时（15 秒）· 上次连上：今天 21:40', { wl: 78, wr: 78, eyes: '-', mouth: 'o', tilt: -6, props: ['sweat'] }, '摊手：两翅摊开、歪头', [['重试', 'replay'], ['换一个服务器', '']]],
  auth: ['SESSION · 读者证过期', '读者证<br>过期了', '服务器说这张读者证不能用了（可能在别处改了密码，或者管理员收回了）。重新登录一次就好，收藏和进度都还在服务器上。', '401 Unauthorized · 令牌已失效', { eyes: 'u', mouth: 'o', props: ['sheet'] }, '捧着一张作废的读者证', [['重新登录', 'user'], ['换一个账号', '']]],
  emptylib: ['EMPTY · 开天窗', '这一栏<br>还没有稿子', '「emby302」这个媒体库里现在一部片子都没有。可能是服务器还在扫描，也可能是这个账号没有权限看。', '0 部 · 服务器扫描进度 37%（示意）', { wl: 25, wr: 55, eyes: '-', props: ['sheet'] }, '排版：低头摆稿纸', [['刷新', 'replay'], ['回到片库', '']]],
};
S.errors = { g: '错误态', title: '网络断开等错误态', note: '整页的错误是一张「通知」：大标题、原因、能做什么；她在右页留给她的位置上（轮廓占位）。小错误是报头下的一条通栏。', states: [['', '网络断开'], ['server', '服务器没有应答'], ['auth', '登录失效'], ['emptylib', '媒体库是空的']],
  build(st) { const e = ERR[st] || ERR['']; return { tab: st === 'emptylib' ? 2 : 0, solid: 1, html: `<div class="scroll"><div class="errp"><div><div class="kick">${e[0]}</div><h1>${e[1]}</h1><p class="lead">${e[2]}</p><div class="acts">${e[6].map((a, i) => i ? `<a class="lnk">${a[0]}</a>` : `<a class="btn">${ic(a[1])}${a[0]}<small>R</small></a>`).join('')}</div><div class="log">${e[3]}</div></div>
    <div style="border-left:1px solid var(--line);padding-left:calc(var(--gut)*2)">${APP.pbox(e[4], e[5], '', 22)}${st ? '' : `<div style="margin-top:3.2rem">${APP.hd('', '离线也能翻', 'CACHED', '<span>不能播放</span>')}${APP.CW.slice(0, 3).map((w, i) => `<a class="li" style="opacity:.6"><em>${pad(i + 1)}</em><div><b>${esc(w.name)}</b><span>${esc(w.sub)}</span></div><div class="th">${w.th ? im(w.th) : ''}</div></a>`).join('')}</div>`}</div></div>
    ${st === 'server' ? `<div class="banner"><b>连接不稳</b>刚才有 2 张海报没取到，已经用文字版面顶上。<a class="lnk">重新取</a></div>` : ''}</div>` }; } };
})();
