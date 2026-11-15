/* 09 记录、收藏 · 10 我的 · 11 设置 · 12 服务器 / 登录 · 13 更新提示 */
(function () {
'use strict';
const { ic, im, pad, esc, D, clamp } = APP, DB = window.DB;
const glyph = (r) => r.ser ? (APP.D[r.t] && APP.kind(APP.D[r.t]) === '动画' ? '动' : '剧') : '影';

// ================================================================== 记录
const DAYS = [['今天', 0, 3], ['昨天', 3, 6], ['本周', 6, 9], ['更早', 9, 12]];   // 日期分组是编的（导出的数据里没有 lastPlayed）
const histHTML = (f) => DAYS.map((d) => { const rows = K.CW.slice(d[1], d[2]).filter((r) => f === 'tv' ? r.ser : f === 'mv' ? !r.ser : f === 'un' ? r.pct < 50 : true); return rows.length ? `<div class="b-day">${d[0]}</div>` + rows.map((r) => K.li(glyph(r), esc(r.t), `${r.ser ? r.ep + ' · ' : ''}${r.pct ? '看到 ' + r.pct + '% · ' + r.left : '还没开始'}`, r.img, r.pct, 'sm hist', '', r.t)).join('') : ''; }).join('');
APP.reg('hist', '记录 · 收藏', {
  title: '记录', note: '观看记录按天分栏，全部 12 条是真实的继续观看（0 % 到 79 % 都有，两条没有任何图片 → 兜底成带剧名的空版）。日期分组是编的。',
  states: [['', '全部'], ['un', '没看完的'], ['end', '滚到底'], ['empty', '空']],
  build: () => ({ content: `
<div class="scroll">
  <div class="b-set s-head"><div class="b-kick">记录 · HISTORY</div><h3>记录</h3><div class="b-idx h-idx"><a data-hf="" class="on">全部 <i class="num">12</i></a><a data-hf="tv">剧集 <i class="num">9</i></a><a data-hf="mv">电影 <i class="num">3</i></a><a data-hf="un">不到一半 <i class="num">${K.CW.filter((r) => r.pct < 50).length}</i></a><span>清空</span></div></div>
  <div class="h-list b-inset"></div>
  <div class="h-none">${K.none('empty', '还没有观看记录', '看过的每一集都会记在这一页，换台设备也能接着看。')}</div>
  <div class="b-colo short h-colo" style="margin-top:30px">— 记录跟着账号走，保存在服务器上 —</div>
</div>${K.top('记录', `<a>${ic('trash')}</a>`, 'paper')}` }),
  init(root) { const set = (f) => { root.querySelector('.h-list').innerHTML = f === 'empty' ? '' : histHTML(f); root.classList.toggle('nores', f === 'empty'); root.querySelectorAll('[data-hf]').forEach((a) => a.classList.toggle('on', a.dataset.hf === f)); APP.fill(root); };
    root.addEventListener('click', (e) => { const a = e.target.closest('[data-hf]'); if (a) set(a.dataset.hf); }); root.__set = set; set(''); },
  setState(root, st) { root.__set(st === 'end' ? '' : st); root.querySelector('.scroll').scrollTop = st === 'end' ? 99999 : 0; },
});

// ================================================================== 收藏（哪些片被收藏是编的；服务器上真实的收藏只有颜心记第 5 集）
const FAVS = ['颜心记', '大话西游之月光宝盒', '航海王', '盗梦空间', '头文字D', '我们仍未知道那天所看见的花的名字。', '琅琊榜', '鹿鼎记', '四月是你的谎言', '女子当参政', '新世纪福音战士剧场版：Air／真心为你'];
APP.reg('favs', '记录 · 收藏', {
  title: '收藏', note: '收藏 = 盖过章的。海报墙上每张海报下面压着一枚小印；分 片 / 单集 / 人物 / 合集 四栏。',
  states: [['', '片'], ['ep', '单集'], ['people', '人物'], ['empty', '空']],
  build: () => ({ content: `
<div class="scroll">
  <div class="b-set s-head"><div class="b-kick">收藏 · SEALED</div><h3>收藏</h3><div class="b-idx f-idx"><a data-ff="" class="on">片 <i class="num">${FAVS.length}</i></a><a data-ff="ep">单集 <i class="num">1</i></a><a data-ff="people">人物 <i class="num">3</i></a><a data-ff="sets">合集 <i class="num">0</i></a><span>按盖章时间 ↓</span></div></div>
  <div class="f-body"></div>
  <div class="b-colo short" style="margin-top:20px">— 在任何一页点「收藏」，主编就盖一枚章 —</div>
</div>${K.top('收藏', `<a>${ic('sort')}</a>`, 'paper')}` }),
  init(root) {
    const body = root.querySelector('.f-body');
    const set = (f) => { root.querySelectorAll('[data-ff]').forEach((a) => a.classList.toggle('on', a.dataset.ff === f));
      if (f === 'ep') { const e = DB.series['颜心记'].episodes[4]; body.innerHTML = `<div class="b-inset">` + K.li('E5', esc(e.name), '颜心记 · 第 1 季 · 看到 59%', e.thumb, 59, 'sm', '<i class="seal s">藏</i>') + `</div>`; }
      else if (f === 'people') body.innerHTML = `<div class="b-inset">` + [['周星驰', '39947-primary', '片库里 23 部'], ['宋轶', '39526-primary', '片库里 3 部'], ['罗云熙', '54529-primary', '片库里 1 部']].map((p) => `<a class="b-who">${im(p[1])}<div><b>${p[0]}</b><span>演员 · ${p[2]}</span></div><i class="seal s">藏</i></a><div class="b-rule"></div>`).join('') + `</div>`;
      else if (f === 'empty' || f === 'sets') body.innerHTML = K.none('empty', f === 'sets' ? '还没有收藏合集' : '还没有盖过章', '在详情页点「收藏」，主编会在这一页给你留一枚「藏」字章。');
      else body.innerHTML = `<div class="b-wall">` + FAVS.map((n) => { const t = APP.byName(n); return K.po(t, `${t.year || '—'} · ${APP.kind(t)}`, '<i class="seal">藏</i>'); }).join('') + `</div>`;
      APP.fill(root); };
    root.addEventListener('click', (e) => { const a = e.target.closest('[data-ff]'); if (a) set(a.dataset.ff); }); root.__set = set; set('');
  },
  setState(root, st) { root.__set(st); root.querySelector('.scroll').scrollTop = 0; },
});

// ================================================================== 我的
const SERVERS = [['家里的片库', 'Emby 4.9 · 局域网 · 12 ms', 1], ['朋友的服务器', 'Emby 4.8 · 外网 · 186 ms', 0], ['测试机', '上次连接失败 · 3 天前', 0]];
const ACCTS = [['影迷', '管理员 · 正在使用', 1], ['家人', '上次登录 周二', 0], ['访客', '登录已失效 · 需要重新输入密码', 0]];
const swp = (list, add, extra) => `<div class="b-swp">${list.map((s) => `<a class="${s[2] ? 'on' : ''}"><u></u><b>${s[0]}</b><span>${s[1]}</span></a>`).join('')}<a class="add">${ic('plus')}<b>${add}</b></a>${extra || ''}</div>`;
APP.reg('me', '我的', {
  title: '我的', note: '读者证（她的署名照 + 你的订阅号）、三格数字、服务器 / 账号两行。点一行，切换面板在这一行下面展开，把下面的内容推开，不盖东西。',
  states: [['', '读者证'], ['server', '服务器切换面板'], ['account', '账号切换面板'], ['end', '滚到底']],
  build: () => ({
    chrome: K.tabbar(3, 'tabs-me'),
    content: `
<div class="scroll below b-me">
  <div class="b-shead">${K.mast('READER', '我的', '<a>' + ic('set', 'display:inline-block;font-size:17px;vertical-align:-3px') + '</a>')}</div>
  <div class="b-card">${APP.msc('round', 76, 'data-say=".sB-me .say"')}<div><em>SUBSCRIBER · No. 000318</em><h4>读者证</h4><b class="who">影迷</b><p class="say">已订阅 318 期 · 主编咕咕嘎嘎为你编排</p></div><i class="perf"></i></div>
  <div class="b-stats"><a><b class="num">87</b><span>看过</span></a><a><b class="num">12</b><span>在看</span></a><a><b class="num">${FAVS.length + 4}</b><span>收藏</span></a></div>
  <a class="b-rowl" data-sw="server"><em>服务器</em><b>家里的片库</b><span>另有 2 台 · 切换${ic('chevd')}</span></a>
  <div class="sw-server">${swp(SERVERS, '添加服务器')}</div>
  <a class="b-rowl" data-sw="account"><em>账　号</em><b>影迷</b><span>另有 2 个 · 切换${ic('chevd')}</span></a>
  <div class="sw-account">${swp(ACCTS, '添加账号', '<a class="out"><b>退出登录</b><span>从这台手机上移除「影迷」</span></a>')}</div>
  <div style="height:30px"></div>
  ${K.head('翻阅', 'YOUR PAGES')}
  <a class="b-rowl big"><em>01</em><b>记录</b><span>12 条没看完${ic('chevr')}</span></a>
  <div class="hs m-strip">${K.CW.slice(1, 7).map((r) => `<a>${K.thumb(r.img, r.t, r.pct)}<b>${esc(r.t)}</b></a>`).join('')}</div>
  <a class="b-rowl big"><em>02</em><b>收藏</b><span>${FAVS.length + 4} 枚章${ic('chevr')}</span></a>
  <a class="b-rowl big"><em>03</em><b>下载</b><span>还没有${ic('chevr')}</span></a>
  <a class="b-rowl big"><em>04</em><b>设置</b><span>播放、字幕、弹幕、外观${ic('chevr')}</span></a>
  <a class="b-rowl big"><em>05</em><b>关于</b><span>0.2.13 · 已是最新${ic('chevr')}</span></a>
  <div class="b-colo" style="margin-top:30px">咕咕嘎嘎 · 由你的片库自动编排</div>
</div>${K.band()}`,
  }),
  init(root) { root.addEventListener('click', (e) => { const a = e.target.closest('[data-sw]'); if (a) { const k = a.dataset.sw, on = !root.classList.contains('open-' + k); root.classList.remove('open-server', 'open-account'); root.classList.toggle('open-' + k, on); APP.anim(root, 400); }
      const o = e.target.closest('.b-swp a:not(.add):not(.out)'); if (o) { o.parentElement.querySelectorAll('a').forEach((x) => x.classList.toggle('on', x === o)); const m = APP.mascotOf(root); if (m) { m.flap(0.6); m.say(`换到「${o.querySelector('b').textContent}」了，版面马上重排。`, 2400); } } }); },
  setState(root, st) { root.classList.remove('open-server', 'open-account', 'tabs-away'); if (st === 'server' || st === 'account') root.classList.add('open-' + st); const sc = root.querySelector('.scroll'); sc.scrollTop = st === 'end' ? 99999 : 0; sc.dispatchEvent(new Event('scroll')); },
});

// ================================================================== 设置。条目取自现在原生版的设置树（SettingsSections.kt / SettingsPages.kt），外观里画报专用的几项是新加的
const sw = (on) => `<span class="b-swi${on ? ' on' : ''}"><i>开</i><i>关</i></span>`;
const seg = (opts, i) => `<span class="b-seg">${opts.map((o, n) => `<i class="${n === i ? 'on' : ''}">${o}</i>`).join('')}</span>`;
const val = (v) => `<span class="val">${v}${ic('chevr')}</span>`;
const stp = (v) => `<span class="b-stp"><i data-d="-1">${ic('min')}</i><b class="num">${v}</b><i data-d="1">${ic('plus')}</i></span>`;
const act = (v) => `<span class="b-act">${v}</span>`;
const opt = (t, s, r, k) => `<a class="b-opt${r && r.includes('b-seg') ? ' wide' : ''}"${k ? ` data-k="${k}"` : ''}><div><b>${t}</b>${s ? `<small>${s}</small>` : ''}</div>${r || ''}</a>`;
const grp = (cn, en, items) => `<div class="b-grp"><b>${cn}</b><em>${en}</em>${items.join('')}</div>`;
const pick = (opts, i) => `<div class="b-pick">${opts.map((o, n) => `<a class="${n === i ? 'on' : ''}"><u></u><b>${o[0]}</b>${o[1] ? `<small>${o[1]}</small>` : ''}</a>`).join('')}</div>`;
const SECS = [
  ['look', '外观', 'LOOK', '深色 · 专色跟随封面', () => [
    grp('纸面', 'PAPER', [opt('主题', '只有深色：墨黑纸、米白字', act('深色')), opt('专色跟随封面', '翻到哪一期，栏目名和进度线换成那张海报的颜色', sw(1)), opt('纸纹', '', sw(1))]),
    grp('玻璃', 'GLASS', [opt('玻璃强度', '底部标签栏和搜索钮的折射强度', stp('100%')), opt('材质', '按设备自动选择', act('自动'))]),
    grp('主编', 'THE EDITOR', [opt('咕咕嘎嘎出现在版面上', '手记、读者证、空状态；播放时从不出现', sw(1)), opt('手记', '', seg(['常写', '少写', '不写'], 0))]),
  ]],
  ['ui', '界面', 'LAYOUT', '首页与详情页的显示方式', () => [
    grp('首页', 'HOME', [opt('首页样式', '', seg(['沉浸', '紧凑'], 0)), opt('首页轮播图', '本期封面的海报流', sw(1)), opt('媒体计数', '栏目里显示部数 / 集数', sw(1)), opt('海报卡片文字居中', '', sw(0)), opt('「最近添加」按媒体库显示', '', sw(1))]),
    grp('首页板块', 'SECTIONS', [['继续观看', 1], ['最近添加', 1], ['媒体库', 1], ['合集', 0]].map((r, i) => `<a class="b-opt ord"><em class="num">${pad(i + 1)}</em><div><b>${r[0]}</b></div><span class="mv"><i>${ic('chevd', 'transform:rotate(180deg)')}</i><i>${ic('chevd')}</i></span>${sw(r[1])}</a>`)),
    grp('详情页', 'DETAIL', [opt('详情页图片', '', seg(['海报', '横版背景'], 1)), opt('单集分页数量', '超过这个数的一季分页显示', val('每页 50 集'))]),
  ]],
  ['play', '播放', 'PLAYBACK', '引擎、画质和自动播放', (st) => [
    grp('播放引擎', 'ENGINE', [opt('首选引擎', 'ExoPlayer 放不了时换 VLC，再换 mpv，最后请服务器转码', val('自动'), 'engine') + (st === 'pick' ? pick([['自动（自适应双内核）', '推荐'], ['ExoPlayer', '系统硬解，最省电'], ['VLC', ''], ['mpv', '格式最全，字幕渲染最好'], ['外部播放器', '没有检测到可用的外部播放器']], 0) : ''), opt('码率上限', '超过时请服务器转码', val('不限'))]),
    grp('播放行为', 'BEHAVIOUR', [opt('自动播放下一集', '片尾出现时倒数 10 秒', sw(1)), opt('跳过片头 / 片尾', '', seg(['关闭', '显示按钮', '自动跳过'], 1)), opt('快进 / 快退步长', '', stp('10 秒')), opt('默认倍速', '', stp('1.0×'))]),
  ]],
  ['strm', 'strm 直链', 'DIRECT LINK', '自动 · 未检测到系统代理', () => [
    grp('直链', 'STRM', [opt('优先直连 strm 目标', '跳过 Emby 中转，由播放内核直接打开网盘 / CDN 地址', sw(1)), opt('线路', '', seg(['自动', '始终直连', '始终经代理'], 0)), `<p class="b-hint">未检测到系统代理：自动模式下直连。</p>`]),
    grp('请求头', 'HEADERS', [opt('User-Agent', '例如 Mozilla/5.0 …', val('默认')), opt('Referer', '例如 https://www.aliyundrive.com/', val('未设置'))]),
  ]],
  ['sub', '字幕与音轨', 'SUBS & AUDIO', '中文优先 · 字幕默认', () => [
    grp('音轨', 'AUDIO', [opt('首选音轨语言', '优先级：中文 › 日语 › 英语', val('3 种'))]),
    grp('字幕', 'SUBTITLES', [opt('字幕模式', '', seg(['默认', '始终', '仅外语', '关'], 0)), opt('首选字幕语言', '优先级：简体中文 › 繁體中文 › 英语', val('3 种')), opt('字号', '', stp('中')), opt('位置', '离画面下沿的距离', stp('8%')), opt('字幕底色', '', sw(0))]),
    `<div class="b-prev sub"><span>沈大人，今夜这场宴，怕是鸿门宴。</span><em>预览</em></div>`,
  ]],
  ['dm', '弹幕', 'DANMAKU', '开 · 适中 · 80%', () => [
    grp('显示', 'DISPLAY', [opt('显示弹幕', '', sw(1)), opt('不透明度', '', stp('80%')), opt('字号', '', stp('1.0×')), opt('滚动速度', '', stp('1.0×')), opt('显示区域', '', seg(['¼ 屏', '半屏', '¾ 屏', '全屏'], 1)), opt('同屏上限', '', val('自动')), opt('弹幕类型', '', `<span class="b-chk"><i class="on">滚动</i><i class="on">顶部</i><i>底部</i></span>`)]),
    `<div class="b-prev dm"><span class="a">前方高能</span><span class="b">这段配乐绝了</span><span class="c">二刷来了</span><em>预览</em></div>`,
    grp('样式', 'STYLE', [opt('字体', '', val('系统字体')), opt('粗细', '系统字体有常规、中等和真粗体', seg(['常规', '中等', '粗'], 1)), opt('描边粗细', '', stp('1.0 dp')), opt('阴影强度', '', stp('40%'))]),
    grp('匹配', 'MATCHING', [opt('播放时自动匹配弹幕', '按 TMDB 身份 + 集号匹配', sw(1)), opt('来源优先级', '', val('3 个来源')), opt('时间偏移', '', stp('0.0 秒')), opt('屏蔽词', '剧透，前方高能', val('2 个'))]),
    grp('性能', 'PERFORMANCE', [opt('性能档位', '', seg(['自动', '低', '中', '高'], 0)), opt('帧率上限', '', val('自动'))]),
    grp('服务与密钥', 'KEYS', [opt('弹幕服务器 API 地址', '令牌在路径里，请勿截图分享', act('已配置 · 隐藏')), opt('TMDB 令牌', '密钥保存在系统安全存储中', act('已配置 · 隐藏')), opt('测试连接', '', val(''))]),
  ]],
  ['cache', '缓存', 'CACHE', '图片 183 MB · 弹幕 4.2 MB', () => [
    grp('占用', 'USAGE', [`<div class="b-use"><b class="num">183</b><span>MB / 256 MB</span><i><u style="width:71%"></u></i></div>`, opt('图片缓存', '已缓存 183 MB（1,204 张海报、背景与剧照，最多 256 MB）', act('清除')), opt('弹幕缓存', '已缓存 4.2 MB（37 集）', act('清除')), opt('界面数据缓存', '首页、片库的上一次版面', act('清理')), opt('搜索历史', '最近搜索过的 4 个关键词', act('清除'))]),
    grp('维护', 'MAINTENANCE', [opt('恢复默认设置', '播放、弹幕、外观等偏好将恢复为默认值', act('恢复默认'))]),
  ]],
  ['acct', '账号与服务器', 'ACCOUNT', '影迷 · 家里的片库', () => [
    grp('当前账号', 'SIGNED IN', [opt('影迷', '已登录 · 管理员', act('切换')), opt('服务器', '家里的片库', val('Emby 4.9.1')), opt('操作系统', '', act('Linux')), opt('连接延迟', '', act('12 ms')), opt('退出登录', '将在服务器上注销本设备的登录令牌，并从本机移除该账号', val(''))]),
    grp('已保存', 'SAVED', [opt('家里的片库', '影迷 · 家人', act('2 个账号')), opt('朋友的服务器', '访客 · 该账号登录已失效', act('重新登录')), opt('添加账号', '', val(''))]),
    `<p class="b-hint">令牌保存在系统安全存储中（Android Keystore）。</p>`,
  ]],
  ['about', '关于与检查更新', 'ABOUT', '0.2.13 · 已是最新', () => [
    `<div class="b-about">${im('emblem')}<div><b>咕咕嘎嘎</b><span class="num">0.2.13 · 正式版</span></div></div>`,
    grp('更新', 'UPDATE', [opt('检查更新', '上次检查：今天 21:40 · 已是最新', act('检查')), opt('自动检查更新', '', sw(1)), opt('更新通道', '', seg(['正式', '测试'], 0))]),
    grp('这台设备', 'DEVICE', [opt('平台', '', act('Android 14')), opt('设备名称', '', act('影迷的手机')), opt('界面模式', '由设备决定', act('手机')), opt('令牌存储', '', act('系统安全存储')), opt('设备 ID', '', act('a3f1…9c2e'))]),
    grp('能力清单', 'CAPABILITIES', [opt('原生播放引擎', 'ExoPlayer · VLC · mpv', act('支持')), opt('外部播放器', '', act('未检测到')), opt('液态玻璃', 'Live · AGSL', act('支持'))]),
  ]],
];
const setHome = () => `<div class="b-kick">设置 · SETTINGS</div><h3>设置</h3><div class="b-sidx">${SECS.map((s, i) => `<a data-sec="${s[0]}"><em class="num">${pad(i + 1)}</em><div><b>${s[1]}</b><span>${s[3]}</span></div><i>${s[2]}</i>${ic('chevr')}</a>`).join('')}</div><div class="b-colo short" style="margin-top:34px">设置只保存在这台设备上</div>`;
const setSec = (k, st) => { const i = SECS.findIndex((s) => s[0] === k), s = SECS[i]; return `<div class="b-kick">设置 · ${pad(i + 1)} / ${pad(SECS.length)} · ${s[2]}</div><h3 class="${s[1].length > 5 ? 'long' : ''}">${s[1]}</h3><div class="hs b-idx s-idx">${SECS.map((x) => `<a data-sec="${x[0]}" class="${x[0] === k ? 'on' : ''}">${x[1]}</a>`).join('')}</div>${s[4](st).join('')}<div class="b-colo short" style="margin-top:36px">— ${s[1]} 完 —</div>`; };
APP.reg('settings', '设置', {
  title: '设置', note: '设置首页是一页带编号的目录；每个分区一屏，分区名是宋体大字，上面一行可以横向滑到别的分区。开关是两格的「开 / 关」，多选一是连排的格子，步进是 − / +。选择器在那一行下面展开。',
  states: [['', '设置首页'], ...SECS.map((s) => [s[0], s[1]]), ['pick', '选择器打开'], ['dm2', '弹幕 · 下半页']],
  build: () => ({ content: `<div class="scroll"><div class="b-set set-in"></div></div>${K.top('设置', `<a>${ic('search')}</a>`, 'paper')}` }),
  init(root) {
    const show = (k, st) => { root.querySelector('.set-in').innerHTML = k ? setSec(k, st) : setHome(); root.querySelector('.tt').textContent = k ? '设置' : ''; APP.fill(root); APP.wireScroll(root); const on = root.querySelector('.s-idx a.on'); if (on) { const pv = on.previousElementSibling || on; on.parentElement.scrollLeft = pv.offsetLeft - on.parentElement.firstElementChild.offsetLeft; } root.__k = k; };
    root.addEventListener('click', (e) => {
      const a = e.target.closest('[data-sec]'); if (a) { show(a.dataset.sec); root.querySelector('.scroll').scrollTop = 0; return; }
      if (e.target.closest('[data-back]') && root.__k) { show(''); return; }
      const s = e.target.closest('.b-opt'); if (!s) { const p = e.target.closest('.b-pick a'); if (p) p.parentElement.querySelectorAll('a').forEach((x) => x.classList.toggle('on', x === p)); return; }
      const w = e.target.closest('.b-swi'), sg = e.target.closest('.b-seg i'), ck = e.target.closest('.b-chk i');
      if (s.dataset.k === 'engine') { show('play', root.querySelector('.b-pick') ? '' : 'pick'); return; }
      if (w) w.classList.toggle('on'); else if (sg) sg.parentElement.querySelectorAll('i').forEach((x) => x.classList.toggle('on', x === sg)); else if (ck) ck.classList.toggle('on');
    });
    root.__show = show; show('');
  },
  setState(root, st) { root.__show(st === 'pick' ? 'play' : st === 'dm2' ? 'dm' : st, st === 'pick' ? 'pick' : ''); const sc = root.querySelector('.scroll'); sc.scrollTop = st === 'dm2' ? 99999 : 0; },
});

// ================================================================== 服务器列表 / 添加服务器 / 登录（不含任何真实地址和账号）
const field = (lab, v, ph, cls, r) => `<label class="b-fld ${cls || ''}"><em>${lab}</em><div><span class="${v ? '' : 'hint'}">${v || ph}</span>${r || ''}</div></label>`;
const SRV = {
  list: () => `<div class="b-kick">第一次见面 · WELCOME</div><h3>选一台<br>服务器</h3><p class="sv-p">咕咕嘎嘎是一本由你的片库自动编排的画报。先告诉主编，片子放在哪儿。</p>
    ${K.head('已保存', 'SAVED', '3 台')}
    ${SERVERS.map((s, i) => `<a class="b-rowl big sv${i === 2 ? ' bad' : ''}"><em>${pad(i + 1)}</em><b>${s[0]}</b><span>${s[1].split(' · ').slice(-1)[0]}${ic('chevr')}</span><small>${i === 0 ? '影迷 · 家人' : i === 1 ? '访客 · 登录已失效' : '连不上：地址也许换了'}</small></a>`).join('')}
    <a class="b-btn wide sv-add">${ic('plus')}添加服务器</a>
    <div class="b-note inl sv-note">${APP.msc('round', 46)}<div><em>主编手记 · EDITOR'S NOTE</em><p class="say">点一台就能进；失效的那台要重新输一次密码。</p></div></div>`,
  add: (st) => `<div class="b-kick">添加服务器 · STEP 1 / 2</div><h3>片子<br>放在哪儿</h3><p class="sv-p">填 Emby 服务器的地址。局域网里的也行，带不带端口都行。</p>
    ${field('地址', st === 'found' ? 'nas.local:8096' : '', '例如 nas.local:8096', 'big', st === 'found' ? `<i class="ok">${ic('check')}</i>` : '<i class="caret"></i>')}
    <div class="b-opt wide"><div><b>协议</b></div>${seg(['自动', 'http', 'https'], 0)}</div>
    ${st === 'found' ? `<div class="sv-found"><em>找到了</em><b>家里的片库</b><span>Emby 4.9.1 · 延迟 12 ms · 需要登录</span></div>` : `<p class="b-hint">不知道地址？在装着 Emby 的那台电脑上打开管理页面，浏览器地址栏里的就是。</p>`}
    <a class="b-btn wide sv-add${st === 'found' ? '' : ' dis'}">${st === 'found' ? '下一步：登录' : '连接'}${ic('arrow')}</a>
    ${K.head('附近', 'ON THIS NETWORK', '自动发现')}
    <a class="b-rowl big sv"><em>01</em><b>客厅的小主机</b><span>Emby 4.9${ic('chevr')}</span><small>在同一个 Wi-Fi 里找到的</small></a>`,
  login: (st) => `<div class="b-kick">登录 · STEP 2 / 2 · 家里的片库</div><h3>你是<br>哪一位</h3>
    <div class="sv-users">${[['影', '影迷', 1], ['家', '家人', 0], ['访', '访客', 0]].map((u) => `<a class="${u[2] ? 'on' : ''}"><i>${u[0]}</i><b>${u[1]}</b></a>`).join('')}<a class="oth"><i>${ic('plus')}</i><b>别的账号</b></a></div>
    ${field('用户名', '影迷', '', '')}
    ${field('密码', st === 'fail' ? '••••••' : st === 'busy' ? '••••••••' : '', '由你自己输入', st === 'fail' ? 'err' : '', `<i class="eye">${ic('eye')}</i>`)}
    ${st === 'fail' ? `<p class="b-hint err">${ic('warn')}用户名或密码不对。服务器说：Invalid username or password。</p>` : `<p class="b-hint">密码只发给你的服务器；登录后本机只保存令牌，放在系统安全存储里。</p>`}
    <a class="b-btn wide sv-add${st === 'busy' ? ' busy' : ''}">${st === 'busy' ? '正在登录……' : '登录'}${st === 'busy' ? '' : ic('arrow')}</a>
    <div class="sv-key">${K.poseBox('key', st === 'fail' ? '再试一次。大小写也算数。' : '密码我不看，也不记。')}</div>`,
};
APP.reg('servers', '服务器 · 登录', {
  title: '服务器 · 登录', note: '第一次打开和换服务器时的三步：选一台、填地址、登录。地址和账号都是示意，不含任何真实信息；密码由用户自己输入。',
  states: [['', '服务器列表'], ['add', '添加服务器'], ['found', '找到服务器'], ['login', '登录'], ['busy', '登录中'], ['fail', '密码不对']],
  build: () => ({ content: `<div class="scroll"><div class="b-set sv-in"></div></div><div class="b-top paper sv-top">${APP.status()}${K.masthead()}<div class="b-nav"><a data-back>${ic('back')}</a></div></div>` }),
  init(root) { root.__show = (st) => { root.querySelectorAll('.msc').forEach((m) => { m.__m = null; }); root.querySelector('.sv-in').innerHTML = st === 'add' || st === 'found' ? SRV.add(st) : st ? SRV.login(st) : SRV.list(); root.classList.toggle('first', !st); APP.fill(root); APP.mountMascots(root); }; root.__show(''); },
  setState(root, st) { root.__show(st); root.querySelector('.scroll').scrollTop = 0; },
});

// ================================================================== 更新提示：一张「号外」。底下是设置 · 关于
const LOG = ['详情页改成整幅铺满，剧照从屏幕最顶端开始', '航海王这种一千多集的剧，选季快了很多', '修好了 strm 直链偶尔要点两次才播放的问题', '电视端：遥控器长按确定键可以标记已看'];
const upHTML = (st) => { const force = st === 'force', dl = st === 'dl', done = st === 'done';
  return `<div class="up-slip${force ? ' force' : ''}">
    <div class="up-h"><em>号外</em><span>EXTRA · ${force ? '必须更新' : '新版本'}</span><i class="num">No. 0.2.14</i></div>
    <div class="up-t"><div><h4>${done ? '印好了，<br>重启就换新' : force ? '这一版<br>要先更新' : '0.2.14<br>到了'}</h4><p>${force ? '服务器已经升级，0.2.13 连不上了。更新大约一分钟，登录和设置都会留着。' : '38.6 MB · 11 月 14 日 · 签名已校验'}</p></div><div class="up-m">${K.poseArt('extra')}<small>轮廓占位 · 号外</small></div></div>
    ${done ? '' : `<ol class="up-l">${LOG.slice(0, force ? 2 : 4).map((l) => `<li>${l}</li>`).join('')}</ol>`}
    ${dl ? `<div class="up-pg"><i><u style="width:62%"></u></i><span class="num">23.9 / 38.6 MB · 62%</span><b>还要 9 秒</b></div>` : ''}
    <div class="up-b">${done ? `<a class="b-btn">${ic('replay')}现在重启</a><a class="b-link">下次打开再换</a>` : dl ? `<a class="b-btn dis">下载中……</a><a class="b-link">放到后台</a>` : `<a class="b-btn">${ic('dl')}现在更新</a>${force ? '<a class="b-link">退出应用</a>' : '<a class="b-link">稍后</a><a class="b-link dim">跳过这一版</a>'}`}</div>
  </div>`; };
APP.reg('update', '更新提示', {
  title: '更新提示', note: '更新提示是一张压在版面上的「号外」：版本号、改了什么、两个按钮。下载时按钮上方多一条进度线；强制更新没有「稍后」。她举着号外站在号外自己的一格里。',
  states: [['', '有新版本'], ['dl', '下载中'], ['done', '下载完成'], ['force', '强制更新']],
  build: () => ({ content: `<div class="scroll"><div class="b-set">${setSec('about')}</div></div>${K.top('设置', `<a>${ic('search')}</a>`, 'paper')}<div class="up-dim"></div><div class="up-host"></div>` }),
  init(root) { root.__show = (st) => { root.querySelector('.up-host').innerHTML = upHTML(st); }; root.__show(''); },
  setState(root, st) { root.__show(st); },
});
})();
