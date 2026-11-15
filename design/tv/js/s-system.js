/* 记录 / 收藏 · 长按确定的菜单 · 设置（首页 + 9 个分区 + 选择器）· 服务器列表 / 登录 · 更新提示 */
(function () {
'use strict';
const APP = window.APP, U = window.U, L = window.LIB, X = window.EXTRA, { ic, im, esc, pad } = APP, F = APP.F;
const ph = U.ph;

// ------------------------------------------------------------------ 记录、收藏
const CW = L.continueWatching;
const DAYS = [['今天', '11 月 14 日 星期六', CW.slice(0, 4)], ['昨天', '11 月 13 日 星期五', CW.slice(4, 8)], ['更早', '本周', CW.slice(8, 12)]];
const FAVS = ['颜心记', '盗梦空间', '航海王', '大话西游之月光宝盒', '头文字D', '阿凡达：水之道', '琅琊榜', '猫猫的奇幻漂流', '四月是你的谎言', '鹿鼎记'].map((n) => U.byName(n)).filter(Boolean);
const hcard = (c) => { const p = c.user.playedPct || 0; return U.wide({ img: U.I(c, 'thumb') || U.I(c, 'poster'), no: c.episode ? 'E' + c.episode : '', alt: c.seriesName || c.name, title: c.seriesName || c.name, sub: (c.type === 'Episode' ? `第 ${c.episode} 集 · ` : '') + (p ? `看到 ${Math.round(p)}%` : '点开过，还没看'), p, attr: 'data-menu="menu"' }); };
APP.SCREENS.me = {
  title: '记录、收藏', nav: 'me', note: '左边一张读者证（她的署名照在这里），右边是按天分栏的观看记录；收藏是一面盖过章的海报墙。',
  path: '<b>方向键路径</b>　默认焦点在右边第一张卡。左右在一栏里走，上下换天；最左再按左 = 读者证下面的「记录 / 收藏」两行（上下换，确定切换），再按左 = 进导航。长按确定 = 菜单（从记录里移除 / 取消收藏）。',
  states: [['', '记录'], ['fav', '收藏（盖过章）'], ['favnone', '收藏为空']],
  render(st) { const fav = st !== ''; this.nav = fav ? 'fav' : 'me';
    const card = `<aside class="rcard"><div class="rc-t">${APP.msc(120)}<div><em>SUBSCRIBER · No. 000318</em><h4>读者证</h4><p>影迷</p></div></div><dl><dt>已订阅</dt><dd><i class="num">318</i> 期</dd><dt>在读</dt><dd><i class="num">${CW.length}</i> 部</dd><dt>收藏</dt><dd><i class="num">${st === 'favnone' ? 0 : FAVS.length}</i> 部</dd></dl>
      <div data-fg><a class="li${fav ? '' : ' on'}" data-f data-go="">${ic('hist')}<b>记录</b><span>${CW.length}</span></a><a class="li${fav ? ' on' : ''}" data-f data-go="fav">${ic('heart')}<b>收藏</b><span>${st === 'favnone' ? 0 : FAVS.length}</span></a></div><p class="say">主编咕咕为你编排 · 每晚九点出刊</p></aside>`;
    let body;
    if (st === '') body = `<div class="vs mev">${DAYS.map((d, i) => `<section data-snap="0"><div class="sec-h sm"><em>${pad(i + 1)}</em><b>${d[0]}</b><i>${d[1]}</i><u></u><span>${d[2].length} 条</span></div><div class="hs" data-lead data-fg data-pad="26">${d[2].map((c, k) => (i === 0 && k === 1 ? hcard(c).replace('data-f ', 'data-f data-f0 ') : hcard(c))).join('')}</div></section>`).join('')}<div style="height:500px"></div></div>`;
    else if (st === 'fav') body = `<div class="vs mev" data-rs="30"><div class="wall w5">${FAVS.map((o, i) => U.poster(o, { seal: 1, attr: `data-menu="menu" ${i === 0 ? 'data-f0' : ''}` })).join('')}</div><div style="height:500px"></div></div>`;
    else body = `<div class="mev none2">${U.pose({ eyes: 'u', mouth: 'o', props: ['sheet'] }, '捧着一张白稿纸', '还没有盖过章的。在详情页把焦点移到「收藏」按确定，章就落在这里。')}</div>`;
    return `<div class="mep">${ph(fav ? '收藏' : '记录', fav ? 'FAVOURITES' : 'HISTORY', fav ? '盖过章的都在这里' : '只记在这台服务器上')}${card}${body}</div>`; },
  ok() { APP.toast('播放 / 打开 →'); },
};

// ------------------------------------------------------------------ 长按确定的菜单（海报上 / 分集上）
APP.SCREENS.menu = {
  title: '长按确定的菜单', nav: 'lib', note: '一张贴在焦点旁边的米白批注条：朱红书脊、箭头指着被长按的那张卡，其余内容压暗。标记已看 = 画校对勾，收藏 = 盖章。不用玻璃。',
  path: '<b>方向键路径</b>　长按确定（或菜单键）打开，焦点落在第一行；上下换行，确定执行，返回收起并把焦点还给那张卡。左右不动（防止误出菜单）。',
  states: [['', '海报上'], ['ep', '分集上']],
  render(st) { this.t = APP.SCREENS[st === 'ep' ? 'series' : 'lib']; this.rail = undefined; return this.t.render('menu'); },
  init(root) { return this.t.init(root, 'menu'); },
  onFocus() {}, ok() { APP.toast('已执行（演示）'); }, key(root, k) { if (k === 'left' || k === 'right') return true; },
};

// ------------------------------------------------------------------ 设置
const sw = (on) => `<i class="sw${on ? ' on' : ''}"></i>`;
const R = (label, value, desc, kind) => ({ label, value, desc, kind });
const SEC = [
  ['appearance', '外观', 'APPEARANCE', [R('主题', '深色', '咕咕嘎嘎只有深色这一版纸', 'info'), R('专色跟随封面', true, '栏目名、进度线、编号换成当前海报的颜色'), R('玻璃质量', '高', '侧栏和悬浮按钮的液态玻璃', 'pick'), R('减弱动态效果', false, '关掉海报流的转动和盖章动画'), R('字号', '标准', '标准 / 大 / 特大', 'pick')]],
  ['ui', '界面', 'INTERFACE', [R('首页板块', '6 栏', '继续观看、最近添加、媒体库的顺序和显隐', 'go'), R('「最近添加」按媒体库显示', true, '每个媒体库各占一栏'), R('封面期数', '10', '首页海报流放几期', 'pick'), R('海报墙列数', '7', '', 'pick'), R('显示评分', true, ''), R('显示时钟', true, '报头和页眉右侧')]],
  ['playback', '播放', 'PLAYBACK', [R('首选引擎', '自动（自适应双内核）', 'ExoPlayer → VLC → mpv → 服务器转码', 'pick'), R('自动播放下一集', true, '片尾出现「下一集」时倒数 10 秒'), R('跳过片头 / 片尾', '显示按钮', '自动跳过 / 显示按钮 / 关闭', 'pick'), R('快进 / 快退步长', '10 秒', '长按时逐级加速到 ×3', 'pick'), R('默认倍速', '1.0×', '', 'pick'), R('码率上限', '不限', '超过时请求服务器转码', 'pick'), R('外部播放器', '没有检测到', '只在这里手动选择时才会用到', 'info')]],
  ['strm', 'strm 直链', 'STRM', [R('优先直连 strm 目标', true, '跳过 Emby 中转，由播放内核直接打开'), R('线路', '自动', '自动 / 始终直连 / 始终经代理', 'pick'), R('系统代理', '已检测到', '自动模式下，直连失败会改走代理', 'info'), R('自定义 User-Agent', '未设置', '有些网盘直链需要', 'go'), R('自定义 Referer', '未设置', '', 'go')]],
  ['subs', '字幕与音轨', 'SUBTITLES & AUDIO', [R('首选音轨语言', '中文 › 日语 › 英语', '按顺序匹配', 'go'), R('首选字幕语言', '简体中文 › 繁體中文', '', 'go'), R('字幕模式', '默认', '默认 / 总是显示 / 只显示强制 / 关闭', 'pick'), R('字体', '思源宋体', '', 'pick'), R('字号', '1.2×', '', 'pick'), R('描边粗细', '2', '', 'pick'), R('位置', '底部', '控件出现时字幕自动上移', 'pick')]],
  ['danmaku', '弹幕', 'DANMAKU', [R('开启弹幕', true, ''), R('播放时自动匹配弹幕', true, '按 TMDB 身份 + 集号匹配'), R('弹幕服务器', '已配置', '', 'go'), R('不透明度', '80%', '', 'pick'), R('字号', '中', '', 'pick'), R('显示区域', '上半屏', '不压字幕', 'pick'), R('同屏上限', '120 条', '', 'pick'), R('屏蔽词', '3 个', '', 'go')]],
  ['cache', '缓存', 'CACHE', [R('图片缓存', '212 MB / 256 MB', '海报、剧照、logo', 'info'), R('弹幕缓存', '3.4 MB（27 集）', '', 'info'), R('预加载下一集', true, '详情页和片尾提前取开头几秒'), R('清除图片缓存', '', '下次打开会重新下载', 'go'), R('清除弹幕缓存', '', '', 'go')]],
  ['account', '账号与服务器', 'ACCOUNT', [R('当前服务器', '家里的片库', '已连接 · 5 个媒体库 · 232 部', 'go'), R('账号', '影迷', '', 'go'), R('切换服务器 / 账号', '', '已保存 3 台服务器', 'go'), R('TMDB 令牌', '已配置', '密钥保存在系统安全存储中', 'go'), R('退出登录', '', '只退出这台电视', 'go')]],
  ['about', '关于与检查更新', 'ABOUT', [R('检查更新', '有新刊 0.2.14', '当前 0.2.13 · 稳定通道', 'go'), R('自动检查', true, '每天开机查一次'), R('设备', 'Android TV 9 · API 28', '玻璃后端：ControlledGl', 'info'), R('播放内核', 'ExoPlayer · VLC · mpv', '', 'info'), R('开源许可', '', '', 'go')]],
];
const ENGINES = [['自动（自适应双内核）', 'ExoPlayer 打不开就换 VLC，再换 mpv，最后请服务器转码', 1], ['ExoPlayer', '系统硬解，最省电'], ['VLC', '格式最全'], ['mpv', '画质选项最多'], ['外部播放器', '没有检测到可用的外部播放器']];
const page = (i, f0) => { const s = SEC[i]; return `<div class="sec-h"><em>${pad(i + 1)}</em><b>${s[1]}</b><i>${s[2]}</i><u></u><span>${s[3].length} 项</span></div>${s[0] === 'about' ? `<div class="abt">${APP.msc(96)}<div><b>咕咕嘎嘎</b><span>0.2.13 · 主编 咕咕</span></div></div>` : ''}<div data-fg>${s[3].map((r, k) => `<a class="li set" data-f ${f0 && k === 0 ? 'data-f0' : ''} ${s[0] === 'playback' && k === 0 ? 'data-go="picker"' : ''}><b>${r.label}</b><small>${r.desc || ''}</small><span>${typeof r.value === 'boolean' ? sw(r.value) : esc(r.value) + (r.kind === 'pick' ? ic('chevd') : r.kind === 'go' ? ic('chevr') : '')}</span></a>`).join('')}</div>`; };
APP.SCREENS.settings = {
  title: '设置', nav: 'settings', note: '左页是带编号的分区目录，右页是那一区的全部条目；焦点在左页上下走，右页跟着翻。选择器在右边页边排开，被改的那一行留在原处。',
  path: '<b>方向键路径</b>　左页：上下换分区（右页跟着翻），右 = 进右页第一行；右页：上下换行，确定 = 开关 / 打开选择器 / 进下一级，左 = 回左页原来的分区。选择器：上下换项，确定选中并收起，返回 / 左收起。左页再按左 = 进导航。',
  states: [['', '设置首页'], ...SEC.map((s) => [s[0], s[1]]), ['picker', '选择器打开（首选引擎）']],
  render(st) { const i = Math.max(0, SEC.findIndex((s) => s[0] === (st === 'picker' ? 'playback' : st))); this.i = i;
    return `<div class="stp">${ph('设置', 'SETTINGS', '咕咕嘎嘎 0.2.13')}<aside class="st-l" data-fg>${SEC.map((s, k) => `<a class="li${k === i ? ' on' : ''}" data-f ${st === '' && k === 0 ? 'data-f0' : ''} data-sec="${k}"><em>${pad(k + 1)}</em><b>${s[1]}</b>${ic('chevr')}</a>`).join('')}</aside>
      <section class="st-r${st === 'picker' ? ' pk' : ''}">${page(i, st !== '' && st !== 'picker')}</section>
      ${st === 'picker' ? `<aside class="side scope open"><div class="sec-h"><em>¶</em><b>首选引擎</b><i>ENGINE</i><u></u></div><div data-fg>${ENGINES.map((e, k) => `<a class="li opt${e[2] ? ' on' : ''}" data-f ${k === 1 ? 'data-f0' : ''}><i class="sq"></i><b>${e[0]}</b><small>${e[1]}</small></a>`).join('')}</div><p class="sn">自动回退链不会用到外部播放器。</p></aside>` : ''}</div>`; },
  onFocus(root, el) { if (el.dataset.sec === undefined || +el.dataset.sec === this.i) return; this.i = +el.dataset.sec; root.querySelectorAll('.st-l .li').forEach((x) => x.classList.toggle('on', x === el)); const r = root.querySelector('.st-r'); r.innerHTML = page(this.i, false);
    if (r.querySelector('.msc')) APP.need(APP.keysIn(r)).then(() => window.Mascot.ready()).then(() => APP.mountMascots(r)); },
  key(root, k) { const c = F.cur; if (!c) return; if (k === 'right' && c.dataset.sec !== undefined) { F.set(root.querySelector('.st-r [data-f]')); return true; } if (k === 'left' && c.closest('.st-r')) { F.set(root.querySelector('.st-l .li.on')); return true; } if (k === 'left' && c.closest('.side')) { APP.back(); return true; } },
  ok(root, c) { const s = c.querySelector('.sw'); if (s) { s.classList.toggle('on'); return; } if (c.classList.contains('opt')) { c.parentElement.querySelectorAll('.opt').forEach((x) => x.classList.toggle('on', x === c)); return; } if (c.dataset.sec !== undefined) { F.set(root.querySelector('.st-r [data-f]')); return; } APP.toast('进下一级（演示里没有画）'); },
};

// ------------------------------------------------------------------ 服务器列表、登录（电视上怎么输入）
const SRV = [['家里的片库', '已连接 · 影迷', '5 个媒体库 · 232 部', 1], ['朋友的片库', '已保存 · 访客', '上次连接 3 天前'], ['旧硬盘', '连不上', '最后一次成功是 10 月 28 日']];
const QW = ['1234567890', 'qwertyuiop', 'asdfghjkl@', 'zxcvbnm._-'];
APP.SCREENS.servers = {
  title: '服务器列表、登录', nav: 'settings', note: '电视上打字最费劲，所以登录有三条路，按省事排序：已保存的账号一键进；手机扫码在手机上输；实在要用遥控器，才是全键盘。演示里不出现任何服务器地址和真实账号。',
  path: '<b>方向键路径</b>　列表：上下换服务器，确定 = 连接 / 进登录；右 = 这台服务器的账号与操作。登录：左列扫码（不接焦点），右列上下走：已保存的账号 → 用遥控器输入。键盘：四个方向走键，确定输入；最下一行是 空格 / 显示密码 / 下一项。返回逐级退回。',
  states: [['', '服务器列表'], ['login', '登录 · 三条路'], ['kbd', '用遥控器输入'], ['fail', '连不上']],
  render(st) {
    if (st === '' || st === 'fail') { const f = st === 'fail' ? 2 : 0; return `<div class="svp">${ph('服务器', 'SERVERS', '已保存 3 台')}<div class="sv-l" data-fg>${SRV.map((s, i) => `<a class="li big sv${s[3] ? ' on' : ''}" data-f ${i === f ? 'data-f0' : ''} data-go="${i === 2 ? 'fail' : i === 1 ? 'login' : ''}"><em>${pad(i + 1)}</em><b>${s[0]}</b><span>${s[1]}</span></a>`).join('')}<a class="li big sv add" data-f>${ic('plus')}<b>添加服务器</b><span>扫码或输入地址</span></a></div>
      <aside class="sv-r">${st === 'fail' ? `<div class="extra2"><b>号外</b><span>连不上「旧硬盘」</span></div><p class="big">服务器没有回应（等了 15 秒）。它可能关机了，或者和电视不在同一个网络。</p><div class="cta" data-fg>${U.btn('再试一次', { ic: 'replay' })}${U.btn('移除这台', { ic: 'x' })}</div>${U.pose({ wl: 78, wr: 78, eyes: '-', mouth: 'o', tilt: -6, props: ['sweat'] }, '摊手：两翅摊开、歪头', '地址我替你记着，等它开机了再来。')}`
        : `<div class="sec-h"><em>¶</em><b>${SRV[0][0]}</b><i>CONNECTED</i><u></u></div><dl class="spec s2"><dt>状态</dt><dd>已连接 · Emby 4.9</dd><dt>内容</dt><dd>${SRV[0][2]}</dd><dt>地址</dt><dd>已保存（这里不显示）</dd></dl><div class="sec-h sm"><em>¶</em><b>这台服务器上的账号</b><i>ACCOUNTS</i><u></u></div><div data-fg><a class="li on" data-f><i class="sq on"></i><b>影迷</b><span>当前</span></a><a class="li" data-f><i class="sq"></i><b>家人</b><span>切换</span></a><a class="li" data-f data-go="login">${ic('plus')}<b>登录其他账号</b>${ic('chevr')}</a></div>`}</aside></div>`; }
    if (st === 'login') return `<div class="svp">${ph('登录', 'SIGN IN', '朋友的片库')}<section class="lg-l"><div class="sec-h"><em>壹</em><b>用手机扫码</b><i>EASIEST</i><u></u></div><div class="qrrow">${U.qr(29)}<ol><li>手机和电视连同一个网络</li><li>扫码，在手机上输入账号和密码</li><li>电视自动登录，密码不过遥控器</li></ol></div><p class="sn">二维码 3 分钟内有效，过期会自动换一张。</p></section>
      <section class="lg-r"><div class="sec-h"><em>贰</em><b>已保存的账号</b><i>ONE PRESS</i><u></u></div><div data-fg><a class="li" data-f data-f0><i class="sq"></i><b>访客</b><span>确定直接进</span></a></div><div class="sec-h" style="margin-top:44px"><em>叁</em><b>用遥控器输入</b><i>KEYBOARD</i><u></u></div><div data-fg><a class="li" data-f data-go="kbd">${ic('grid')}<b>打开全键盘</b><small>账号、密码一格一格按</small>${ic('chevr')}</a></div></section></div>`;
    return `<div class="svp">${ph('登录', 'SIGN IN', '朋友的片库 · 用遥控器输入')}<section class="kb-f"><label class="on"><em>账号</em><span class="num">yingmi</span><i class="caret"></i></label><label><em>密码</em><span class="dots">••••••</span><small>已隐藏</small></label><p class="sn">不想按了？返回上一页用手机扫码。</p></section>
      <section class="kb-k"><div class="kb q" data-fg>${QW.map((r) => r.split('').map((k) => `<a class="key" data-f ${k === 'i' ? 'data-f0' : ''} data-k="${k}">${k}</a>`).join('')).join('')}</div><div class="kb-b" data-fg><a class="key w2" data-f>⇧ 大写</a><a class="key w2 sp" data-f>空格</a><a class="key w2" data-f data-k="del">${ic('back')}删除</a><a class="key w2" data-f>显示密码</a><a class="key w2 go" data-f>下一项 ${ic('chevr')}</a></div></section></div>`; },
  ok(root, c) { const k = c.dataset.k, q = root.querySelector('.kb-f .on span'); if (k && q) { q.textContent = k === 'del' ? q.textContent.slice(0, -1) : (q.textContent + k).slice(0, 16); return; } APP.toast('已执行（演示）'); },
};

// ------------------------------------------------------------------ 更新提示
const NOTES = ['拖进度条后恢复播放更快（约 1 秒）', '电视端详情页：下一集提前预加载', '修复：部分 strm 链接要等很久才开始', '新增：片尾「下一集」倒数可以关'];
APP.SCREENS.update = {
  title: '更新提示对话框', nav: 'home', rail: false, note: '一张「号外」贴在压暗的首页上：版本号用斜体大数字，更新内容是带编号的四行。不用玻璃，不放全屏角色，她只在号外左下角的署名格里。',
  path: '<b>方向键路径</b>　默认焦点「立即更新」；左右：立即更新 ↔ 今晚再说 ↔ 跳过这一版；返回 = 今晚再说。下载中只有「转到后台」一个按钮。',
  states: [['', '有新刊'], ['dl', '下载中'], ['force', '必须更新']],
  render(st) { return `<div class="upd"><div class="up-bg">${U.mast()}<div class="up-rows">${[0, 1, 2].map(() => '<i></i>').join('')}</div></div><div class="up-dim"></div>
    <div class="sheet-u scope open"><div class="su-h"><b>号外</b><em>EXTRA · ${st === 'force' ? '这一版必须更新' : '新刊已付印'}</em><span>十一月十四日</span></div>
      <div class="su-b"><div class="ver"><small>当前 0.2.13</small><b class="num">0.2.14</b><small>38 MB · 稳定通道</small></div><ol>${NOTES.map((n) => `<li>${n}</li>`).join('')}</ol></div>
      ${st === 'dl' ? `<div class="su-p"><i class="bar"><u style="width:42%"></u></i><span><i class="num">42%</i>　16 / 38 MB · 下载完会校验签名再安装</span></div>` : ''}
      <div class="su-f">${U.note(st === 'dl' ? '正在往你这儿送，别关电视。' : st === 'force' ? '旧版连不上新的更新通道了，这次得装。' : '拖进度条修顺了，值得装。', { size: 68, k: '主编 · 咕咕' })}<div class="cta" data-fg>${st === 'dl' ? U.btn('转到后台', { ic: 'min', attr: 'data-f0' }) : U.btn('立即更新', { ic: 'dl', cls: 'pri', attr: 'data-f0 data-go="dl"' }) + (st === 'force' ? U.btn('退出应用', { ic: 'x' }) : U.btn('今晚再说', {}) + U.btn('跳过这一版', {}))}</div></div></div></div>`; },
  ok() { APP.toast('已执行（演示）'); },
};
})();
