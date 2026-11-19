/* 咕嘎画报（B1）· iOS（iPhone）—— 共用适配层。在 ../phone/js/s-*.js 之后、启动之前加载。
 * 做的事：
 *   1. 标签栏 / 搜索圆钮不再写死 px，由 css 按安全区落位；状态栏换成 iOS 的画法（画廊）；报头徽记用 G03
 *   2. 状态清单：去掉「再按一次返回退出」；加 画中画浮窗 / 长按纸片菜单 / AirPlay / 画中画 / App Store 号外 / 左边缘右滑
 *   3. 触感：落章 success、擦章 light、校对勾 light、翻期 selection、换标签 selection、下拉拉满 medium、付印 success、长按 medium
 *   4. 文案：Android / Keystore / ExoPlayer 回退链 → iOS / 钥匙串 / 「待定」
 *   5. 启动：两种模式共用的 build → 装进 DOM → 玻璃 → 她 → init 的顺序（和 ../phone/js/app.js 的 boot 同一套步骤，只是画板尺寸来自变量）
 * 原生桥（外壳实现，没有也照常跑）：window.webkit.messageHandlers.gugu.postMessage({type:'orientation'|'haptic'|'chrome', ...}) */
(function () {
'use strict';
const APP = window.APP, K = window.K, S = APP.S, IMG = window.IMG, { ic, im, esc, pad } = APP;
const IOS = window.IOS = { mode: 'gallery', Q: APP.Q };
IOS.bridge = (msg) => { try { const h = window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.gugu; if (h) h.postMessage(msg); if (IOS.log) IOS.log.push(msg); } catch (e) { /* 没有桥 */ } };
IOS.haptic = (style) => IOS.bridge({ type: 'haptic', style });

// ------------------------------------------------------------------ WebKit：画布的 ctx.filter 接受但不生效
// iOS / macOS 的 WebKit 认 `ctx.filter = 'blur(…)'` 这个属性，但画的时候不虚化、不压暗——封面区背后的剧照（K.wash → APP.blurOf）
// 会变成原图、原亮度。检测一次；不生效就用像素运算做同一件事：三遍盒式模糊近似高斯（σ = blur 半径，同 CSS），再按 CSS 规范的
// saturate 矩阵和 brightness 乘法、每步夹到 0–255。只换 APP.blurOf，phone 原件不动。真机实现用上游的着色器，没有这一条。
IOS.canvasFilterWorks = (() => {
  try { const c = document.createElement('canvas'); c.width = c.height = 8; const x = c.getContext('2d', { willReadFrequently: true });
    x.filter = 'brightness(0)'; x.fillStyle = '#fff'; x.fillRect(0, 0, 8, 8); return x.getImageData(4, 4, 1, 1).data[0] < 128; } catch (e) { return true; }
})();
if (!IOS.canvasFilterWorks) {
  const boxes = (sigma) => { const ideal = Math.sqrt(4 * sigma * sigma + 1); let wl = Math.floor(ideal); if (wl % 2 === 0) wl--; const wu = wl + 2;
    const m = Math.round((12 * sigma * sigma - 3 * wl * wl - 12 * wl - 9) / (-4 * wl - 4)); return [0, 1, 2].map((i) => ((i < m ? wl : wu) - 1) / 2); };
  const pass = (src, dst, w, h, r, horiz) => {   // 一遍盒式模糊，边缘取最近的像素
    const n = horiz ? w : h, lines = horiz ? h : w, step = horiz ? 1 : w, ls = horiz ? w : 1, k = 1 / (2 * r + 1);
    for (let L = 0; L < lines; L++) { const b = L * ls; let acc = 0;
      for (let i = -r; i <= r; i++) acc += src[b + Math.min(n - 1, Math.max(0, i)) * step];
      for (let i = 0; i < n; i++) { dst[b + i * step] = acc * k; acc += src[b + Math.min(n - 1, i + r + 1) * step] - src[b + Math.max(0, i - r) * step]; } }
  };
  APP.blurOf = (key, src, w, h, o) => APP.derive(key, w, h, (x) => {
    const m = o.b * 3, W = w + 2 * m, H = h + 2 * m, c = document.createElement('canvas'); c.width = W; c.height = H;
    const y = c.getContext('2d', { willReadFrequently: true }); y.fillStyle = '#000'; y.fillRect(0, 0, W, H);
    APP.cover(y, APP.BASE[src], 0, 0, W, H, o.px, o.py);
    const d = y.getImageData(0, 0, W, H), p = d.data, N = W * H, ch = [0, 1, 2].map(() => new Float32Array(N)), tmp = new Float32Array(N);
    for (let i = 0; i < N; i++) { ch[0][i] = p[i * 4]; ch[1][i] = p[i * 4 + 1]; ch[2][i] = p[i * 4 + 2]; }
    const rs = boxes(o.b); ch.forEach((a) => rs.forEach((r) => { if (r > 0) { pass(a, tmp, W, H, r, true); pass(tmp, a, W, H, r, false); } }));
    const s = o.s || 1, l = o.l || 1, cl = (v) => (v < 0 ? 0 : v > 255 ? 255 : v);
    for (let i = 0; i < N; i++) { const R = ch[0][i], G = ch[1][i], B = ch[2][i];
      const r2 = cl((0.213 + 0.787 * s) * R + (0.715 - 0.715 * s) * G + (0.072 - 0.072 * s) * B);
      const g2 = cl((0.213 - 0.213 * s) * R + (0.715 + 0.285 * s) * G + (0.072 - 0.072 * s) * B);
      const b2 = cl((0.213 - 0.213 * s) * R + (0.715 - 0.715 * s) * G + (0.072 + 0.928 * s) * B);
      p[i * 4] = cl(r2 * l); p[i * 4 + 1] = cl(g2 * l); p[i * 4 + 2] = cl(b2 * l); p[i * 4 + 3] = 255; }
    y.putImageData(d, 0, 0); x.drawImage(c, m, m, w, h, 0, 0, w, h);
  });
}
IOS.rm =window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches;

// ------------------------------------------------------------------ 1. 图标、状态栏、标签栏、徽记
document.querySelector('svg defs').insertAdjacentHTML('beforeend',
  '<symbol id="i-airplay" viewBox="0 0 24 24"><path d="M6.5 16.5H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v8.5a2 2 0 0 1-2 2h-1.5"/><path d="M12 13l6 7.5H6z"/></symbol>' +
  '<symbol id="i-x" viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></symbol>');
APP.status = () => `<div class="status"><b>21:47</b><span><i class="sig"><u></u><u></u><u></u><u></u></i>${ic('wifi')}<i class="batt"><u></u></i></span></div>`;
K.tabbar = (sel, id, red) =>
  `<div class="g tabs ios-tabs" data-mat="bar" data-ind="tab" data-sel="${sel}" id="${id}">` +
  K.TABS.map((n, i) => `<a data-cell class="tab${i === sel ? ' on' : ''}"><span>${n}</span><i></i></a>`).join('') + `</div>` +
  `<div class="g ios-fab${red ? ' on-red' : ''}" data-mat="clear" data-press>${ic('search', 'font-size:24px')}</div>`;
IMG.emblem = '../generated/v3/G03.png';   // 报头徽记：第三版的她（mascot.md §1）；手机 demo 里从品牌图裁的企鹅头是旧徽记

// ------------------------------------------------------------------ 2. 状态清单
S.home.states = S.home.states.filter((s) => s[0] !== 'exit').concat([['pip', '画中画浮窗（系统）']]);
S.series.states.push(['ctx', '长按一集 · 纸片菜单']);
S.wall.states.push(['menu', '长按海报 · 纸片菜单']);
S.player.states.push(['airplay', 'AirPlay 投到电视'], ['pip', '画中画中']);
S.player.note = '横屏，画面铺到灵动岛和圆角底下（16:9 的画面本来就留黑边），控件、字幕、弹幕让出左右 62 和底部 21 的安全区。点「字幕」「选集」等画面缩到左边、面板排进右侧页边。顶栏右半边多了 AirPlay 和画中画；投屏在 iOS 上就是 AirPlay（系统路由器）。播放内核与回退链待定（上游的事）。';
S.update.states = [['', 'App Store 有新版'], ['tf', 'TestFlight 版'], ['force', '必须更新']];
S.update.note = 'iOS 不能自己下载安装：号外只说「App Store 有新版」，按钮通向 App Store（或 TestFlight）；没有下载进度、没有「下载完成」，强制更新也没有「退出应用」（iOS 不允许应用自己退出）。';
S.search.note = (S.search.note || '') + ' iOS：键盘是系统的，弹起时标签栏沉下去、结果列表让出键盘高度。';
S.home.note = (S.home.note || '') + ' iOS：剧照铺到灵动岛后面；下拉付印新刊代替系统的回弹；没有「再按一次返回」。';

// ------------------------------------------------------------------ 3. 触感
const setFav0 = K.setFav; K.setFav = (root, on, talk) => { setFav0(root, on, talk); if (talk) IOS.haptic(on ? 'success' : 'light'); };
const flow0 = K.flow; K.flow = (root, stage, o) => { const oi = o.onIndex; o.onIndex = (i, first) => { oi(i, first); if (!first) IOS.haptic('selection'); }; return flow0(root, stage, o); };
const pull0 = K.pull; K.pull = (root, o) => { const d = o.done; o.done = () => { d(); IOS.haptic('success'); }; pull0(root, o);
  const sc = root.querySelector('.scroll'), strip = root.querySelector('.b-pull'), p0 = sc.__pull; let armed = false;
  sc.__pull = (dy) => { p0(dy); if (dy === null) { armed = false; return; } const a = parseFloat(strip.style.height) >= 72; if (a && !armed) IOS.haptic('medium'); armed = a; }; };
document.addEventListener('click', (e) => { if (e.target.closest('.mk')) IOS.haptic('light'); }, true);
// 区块锚点：手机端按 96 高的顶栏调的 108，这里顶栏 = 安全区 + 50，差额补上
const scrollTo0 = K.scrollTo; K.scrollTo = (root, sel, off) => { const sat = IOS.vars(root.closest('.sB') || root).sat || 46; scrollTo0(root, sel, (off === undefined ? 108 : off) + (sat - 46)); };
document.addEventListener('select', (e) => { if (e.target.closest && e.target.closest('.g[data-ind]')) IOS.haptic('selection'); }, true);

// ------------------------------------------------------------------ 4. 文案：安卓的说法 → iOS 的说法（引擎回退链不编，标待定）
IOS.TXT = [
  [/Android 14/g, 'iOS 18'], [/影迷的手机/g, '影迷的 iPhone'], [/这台手机/g, '这台 iPhone'],
  [/令牌保存在系统安全存储中（Android Keystore）。/g, '令牌保存在 iOS 钥匙串（Keychain）里。'], [/放在系统安全存储里/g, '放在钥匙串里'], [/系统安全存储/g, '钥匙串'],
  [/ExoPlayer 放不了时换 VLC，再换 mpv，最后请服务器转码/g, 'iOS 的内核与回退链待定（上游决定），这里只画位置'], [/ExoPlayer · VLC · mpv/g, '待定 · 由上游定'],
  [/Live · AGSL/g, 'Live · Metal'], [/上次检查：今天 21:40 · 已是最新/g, '由 App Store 管理 · 当前 0.2.13'], [/自动检查更新/g, '有新版时在首页出号外'],
  [/未检测到可用的外部播放器/g, '待定：Infuse / VLC 的 URL scheme'], [/从这台 iPhone 上移除/g, '从这台 iPhone 上移除'],
];
IOS.swapText = (root) => {
  const w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT), nodes = []; while (w.nextNode()) nodes.push(w.currentNode);
  nodes.forEach((n) => { let t = n.nodeValue; IOS.TXT.forEach(([re, s]) => { t = t.replace(re, s); }); if (t !== n.nodeValue) n.nodeValue = t; });
  root.querySelectorAll('.b-seg').forEach((sg) => { const a = [...sg.children].map((i) => i.textContent); if (a[0] === '正式' && a[1] === '测试') { sg.children[0].textContent = 'App Store'; sg.children[1].textContent = 'TestFlight'; } });
  root.querySelectorAll('.b-act').forEach((a) => { if (a.textContent === '未检测到' && a.closest('.b-opt') && /外部播放器/.test(a.closest('.b-opt').textContent)) a.textContent = '待定'; if (a.textContent === '支持' && a.closest('.b-opt') && /原生播放引擎/.test(a.closest('.b-opt').textContent)) a.textContent = '待定'; });
  // 首选引擎的选择器：不编 iOS 的回退链，只留一行「待定」
  root.querySelectorAll('.b-pick').forEach((p) => { if (p.closest('.sB-settings') || p.closest('.sB-update')) { if (!p.dataset.ios) { p.dataset.ios = 1; p.innerHTML = '<a class="on"><u></u><b>待定</b><small>iOS 的内核与回退链由上游定，这里只画位置</small></a>'; } } });
  const eng = [...root.querySelectorAll('.b-opt[data-k="engine"] .val')]; eng.forEach((v) => { if (v.firstChild && v.firstChild.nodeType === 3) v.firstChild.nodeValue = '待定'; });
};

// ------------------------------------------------------------------ 5. 长按 = 纸片菜单（米白纸、墨字、直角；在那一行 / 那一排下面推开，不盖内容）
IOS.ctxHTML = (name, sub, kind) => `<div class="ios-ctx cn"><div class="ct"><em>MENU</em><b>${esc(name)}</b><i>${esc(sub || '')}</i></div>` +
  `<a data-cx="play">${ic('play')}<span>${kind === 'ep' ? '播放这一集' : '播放'}</span><small>${kind === 'ep' ? '接着上次' : '从头'}</small></a>` +
  `<a data-cx="fav">${ic('heart')}<span>收藏</span>${K.favSlot()}</a>` +
  `<a data-cx="seen">${ic('check')}<span>标记已看</span>${K.mk()}</a>` +
  `<a data-cx="open">${ic('arrow')}<span>详情</span><small>翻开</small></a>` +
  `<div class="grp">MANAGE · 管理</div><a data-cx="del" class="danger">${ic('trash')}<span>删除媒体…</span><small>会写服务器</small></a>` +
  `<a class="close" data-cx="close"><span>收起</span>${ic('chevd', 'transform:rotate(180deg)')}</a></div>`;
IOS.closeCtx = (root) => { root.querySelectorAll('.ios-ctx').forEach((x) => x.remove()); root.querySelectorAll('.held').forEach((x) => x.classList.remove('held')); };
IOS.openCtx = (root, el) => {
  IOS.closeCtx(root);
  const po = el.classList.contains('b-po'), name = (el.querySelector('b') || {}).textContent || '', sub = (el.querySelector('span') || {}).textContent || '';
  const kind = el.classList.contains('ep') ? 'ep' : po ? 'title' : 'row';
  const html = IOS.ctxHTML(name, sub, kind);
  if (po) { const cards = [...el.parentElement.querySelectorAll(':scope > .b-po')], i = cards.indexOf(el), last = cards[Math.min(cards.length - 1, (Math.floor(i / 3) + 1) * 3 - 1)]; last.insertAdjacentHTML('afterend', html); }
  else { const after = el.nextElementSibling && el.nextElementSibling.classList.contains('b-rule') ? el.nextElementSibling : el; after.insertAdjacentHTML('afterend', html); }
  const ctx = root.querySelector('.ios-ctx');
  ctx.addEventListener('click', (e) => { e.stopPropagation(); const a = e.target.closest('[data-cx]'); if (!a) return; const k = a.dataset.cx;
    if (k === 'fav') { const s = a.querySelector('.fav-slot'), on = !s.classList.contains('on'); s.classList.remove('on', 'off'); void s.offsetWidth; s.classList.add(on ? 'on' : 'off'); IOS.haptic(on ? 'success' : 'light'); }
    else if (k === 'seen') { a.classList.toggle('seen'); IOS.haptic('light'); }
    else if (k === 'del') { if (a.classList.contains('confirm')) { IOS.closeCtx(root); } else { a.classList.add('confirm'); a.innerHTML = '<span>再点一次，真的删除</span><small>会从服务器上删掉这个文件，不能恢复</small>'; IOS.haptic('medium'); } }
    else if (k === 'open' && IOS.open) { IOS.closeCtx(root); IOS.open(name); }
    else if (k === 'play' && IOS.play) { IOS.closeCtx(root); IOS.play(); }
    else IOS.closeCtx(root);
    APP.anim(root, 400); });
  el.classList.add('held'); setTimeout(() => el.classList.remove('held'), 300);
  APP.anim(root, 500);
};
IOS.longPress = (root) => {
  if (root.__lp) return; root.__lp = 1; let t = 0, start = null, target = null;
  const cancel = () => { clearTimeout(t); t = 0; start = null; target = null; };
  root.addEventListener('pointerdown', (e) => { const el = e.target.closest('.b-po, .b-li'); if (!el || e.button !== 0 || el.closest('.ios-ctx')) return; target = el; start = { x: e.clientX, y: e.clientY };
    t = setTimeout(() => { t = 0; IOS.haptic('medium'); IOS.lpAt = performance.now(); IOS.openCtx(root, target); const kill = (ev) => { if (!ev.target.closest('.ios-ctx')) { ev.stopPropagation(); ev.preventDefault(); } }; window.addEventListener('click', kill, true); setTimeout(() => window.removeEventListener('click', kill, true), 1500); target = null; }, 480); });
  root.addEventListener('pointermove', (e) => { if (start && Math.hypot(e.clientX - start.x, e.clientY - start.y) > 8) cancel(); });
  ['pointerup', 'pointercancel', 'pointerleave'].forEach((n) => root.addEventListener(n, cancel));
  root.addEventListener('click', (e) => { if (root.querySelector('.ios-ctx') && !e.target.closest('.ios-ctx')) { IOS.closeCtx(root); APP.anim(root, 300); } });
};

// ------------------------------------------------------------------ 6. 播放器：顶栏右半边 [AirPlay][她][收藏][画中画][锁]；AirPlay / 画中画的纸面说明；搜索：iOS 键盘示意；首页：画中画浮窗
IOS.kbd = () => `<div class="b-kbd ios"><div class="cand"><b>hang hai</b><span>航海</span><span>航海王</span><span>航</span><span>行</span><span>杭</span></div>` +
  ['qwertyuiop', 'asdfghjkl', 'zxcvbnm'].map((r, i) => `<div class="kr r${i}">${i === 2 ? '<i class="w">⇧</i>' : ''}${[...r].map((k) => `<i>${k}</i>`).join('')}${i === 2 ? '<i class="w">⌫</i>' : ''}</div>`).join('') +
  `<div class="kr"><i class="w">123</i><i class="w">🌐</i><i class="sp">空格</i><i class="w go">搜索</i></div></div>`;
IOS.patchBuilt = () => {
  const p = S.player;
  if (p.content && !p.__ios) { p.__ios = 1;
    p.content = p.content.replace('<div class="pm">', `<a data-act="airplay" title="AirPlay">${ic('airplay')}</a><div class="pm">`).replace('<a data-act="lock">', `<a data-act="pip" title="画中画">${ic('pip')}</a><a data-act="lock">`) +
      `<div class="p-air"><div class="ck">AIRPLAY · 系统路由</div><h4>正在投到<br>客厅的 Apple TV</h4><p>画面和声音都在电视上放；这里只留时间轴和功能词，锁屏和控制中心里也能暂停。</p><div class="dev"><i></i>已连接 · 1080p · 国语 AAC 2.0</div><div class="eb"><a class="b-btn" data-act="airplay">${ic('x')}结束 AirPlay</a><a class="b-link">换一台</a></div></div>` +
      `<div class="p-pip"><div class="ck">PICTURE IN PICTURE</div><h4>画面在画中画里</h4><p>回到首页、切到别的应用都接着放；点一下小窗回到全幅。字幕和弹幕跟着小窗走。</p><div class="eb"><a class="b-btn" data-act="pip">${ic('full')}回到全幅</a></div></div>`;
  }
  const s = S.search;
  if (s.content && !s.__ios) { s.__ios = 1; s.content = s.content.replace(/<div class="b-kbd">[\s\S]*$/, IOS.kbd()); }
  const h = S.home;
  if (h.content && !h.__ios) { h.__ios = 1; const T = APP.D['颜心记']; h.content += `<div class="ios-pipwin">${im(T.images.backdrop)}<i></i><svg class="a" viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg><svg class="b" viewBox="0 0 24 24"><path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/></svg><svg class="c" viewBox="0 0 24 24"><rect x="6" y="4.5" width="4.2" height="15" rx="1" fill="#fff" stroke="none"/><rect x="13.8" y="4.5" width="4.2" height="15" rx="1" fill="#fff" stroke="none"/></svg></div>`; }
};
const wrapState = (id, extra) => { const o = S[id].setState; S[id].setState = (root, st) => { root.classList.remove('airplay', 'pip', 'ios-pip'); IOS.closeCtx(root); o.call(S[id], root, st); if (extra) extra(root, st); }; };
wrapState('home', (root, st) => { root.classList.toggle('ios-pip', st === 'pip'); });
wrapState('series', (root, st) => { if (st === 'ctx') { K.scrollTo(root, '.b-eps', 108); const rows = root.querySelectorAll('.b-eps .b-li'); if (rows[1]) IOS.openCtx(root, rows[1]); } });
wrapState('wall', (root, st) => { if (st === 'menu') { const c = root.querySelectorAll('.b-wall .b-po')[1]; if (c) IOS.openCtx(root, c); } });
wrapState('player', (root, st) => { if (st === 'airplay' || st === 'pip') { root.classList.add(st); const a = root.querySelector(`[data-act="${st}"]`); if (a) a.classList.add('on'); } });
['discover', 'libs', 'sets', 'set', 'search', 'episode', 'movie', 'person', 'hist', 'favs', 'me', 'settings', 'servers'].forEach((id) => wrapState(id));
IOS.playerInit = (root) => {
  root.addEventListener('click', (e) => { const a = e.target.closest('[data-act="airplay"],[data-act="pip"]'); if (!a) return; const k = a.dataset.act, on = !root.classList.contains(k);
    root.classList.remove('airplay', 'pip'); root.querySelectorAll('[data-act="airplay"],[data-act="pip"]').forEach((x) => x.classList.remove('on'));
    if (on) { root.classList.add(k); root.querySelectorAll(`[data-act="${k}"]`).forEach((x) => x.classList.add('on')); IOS.haptic('selection'); } });
};

// ------------------------------------------------------------------ 7. 更新「号外」：App Store / TestFlight
const LOG = ['详情页改成整幅铺满，剧照从屏幕最顶端开始', '航海王这种一千多集的剧，选季快了很多', '修好了 strm 直链偶尔要点两次才播放的问题', 'iPhone：左边缘右滑返回、长按出纸片菜单'];
IOS.upHTML = (st) => { const force = st === 'force', tf = st === 'tf';
  return `<div class="up-slip${force ? ' force' : ''}">
    <div class="up-h"><em>号外</em><span>EXTRA · ${force ? '必须更新' : tf ? 'TESTFLIGHT' : 'APP STORE'}</span><i class="num">No. 0.2.14${tf ? ' (37)' : ''}</i></div>
    <div class="up-t"><div><h4>${force ? '这一版<br>要先更新' : tf ? '测试版<br>0.2.14 到了' : '0.2.14<br>到了'}</h4><p>${force ? '服务器已经升级，0.2.13 连不上了。去 App Store 更新完再回来，登录和设置都会留着。' : tf ? 'TestFlight · 11 月 14 日 · 这一版 90 天后过期' : 'App Store · 11 月 14 日 · 42.1 MB'}</p></div><div class="up-m">${K.poseArt('extra')}<small>轮廓占位 · 号外</small></div></div>
    <ol class="up-l">${LOG.slice(0, force ? 2 : 4).map((l) => `<li>${l}</li>`).join('')}</ol>
    <div class="up-store"><i>${tf ? 'TF' : 'A'}</i><span>${tf ? '更新在 <b>TestFlight</b> 里装，装完回来接着看。' : '下载和安装由 <b>App Store</b> 做；画报自己不下载、不覆盖安装。'}</span></div>
    <div class="up-b">${tf ? `<a class="b-btn">${ic('arrow')}打开 TestFlight</a><a class="b-link">稍后</a>` : `<a class="b-btn">${ic('arrow')}去 App Store</a>${force ? '' : '<a class="b-link">稍后</a><a class="b-link dim">跳过这一版</a>'}`}</div>
  </div>`; };
S.update.init = (root) => { root.__show = (st) => { root.querySelector('.up-host').innerHTML = IOS.upHTML(st); }; root.__show(''); IOS.swapText(root); };
S.update.setState = (root, st) => { root.__show(st); };

// ------------------------------------------------------------------ 8. 画廊里的合成画板：左边缘右滑返回进行到一半（底下是首页，上面是被推入的剧集详情）
APP.reg('swipe', 'iOS', {
  title: '左边缘右滑返回', deps: ['home', 'series'], contentCls: 'sB v1 sB-home', fixedStatus: 1,
  note: 'iOS 没有系统返回键：左上角的返回钮照旧（红线），再加上跟手的左边缘右滑。被推入的页跟着手指走，底下那一页从 −30 % 跟回来、压暗退去；过三分之一或甩得快就完成，否则弹回。没有投影，只有一层纸色的压暗。',
  states: [['', '进行到 40 %'], ['start', '刚开始 · 12 %'], ['almost', '快松手 · 80 %']],
  build: () => ({ chrome: K.tabbar(0, 'tabs-swipe'), content: S.home.content, over: `<div class="ios-veil"></div><div class="ios-page sB v1 sB-series scr">${S.series.content}</div>` }),
  init(root) { S.home.init(root.querySelector(':scope > .content')); S.series.init(root.querySelector('.ios-page')); root.style.setProperty('--p', 0.4); },
  setState(root, st) { root.style.setProperty('--p', { start: 0.12, almost: 0.8 }[st] || 0.4); },
});

// ------------------------------------------------------------------ 9. 画板尺寸 → 封面区收紧系数、播放器让位系数
IOS.vars = (el) => { const cs = getComputedStyle(el), n = (k) => parseFloat(cs.getPropertyValue(k)) || 0; return { vw: n('--vw'), vh: n('--vh'), sat: n('--sat'), sab: n('--sab'), sal: n('--sal'), sar: n('--sar') }; };
IOS.relayout = (el) => {
  const v = IOS.vars(el), port = v.vw <= v.vh;
  if (port) { const cvh = v.vh - v.sat - Math.max(18, v.sab) - 64; el.style.setProperty('--k', Math.max(0.84, Math.min(1, cvh / 742)).toFixed(3)); }
  else {   // 面板打开时：16:9 的画面缩到 右侧页边（164 + 20 + 16）和左安全区之间；垂直不压到时间轴
    const bars = Math.max(0, (v.vw - v.vh * 16 / 9) / 2), s = Math.min((v.vw - v.sal - v.sar - 232) / Math.max(1, v.vw - 2 * bars), (v.vh - 14 - 27 - 70) / v.vh);
    el.style.setProperty('--pk', s.toFixed(3)); el.style.setProperty('--px', (v.sal + 12 - bars * s).toFixed(1) + 'px');
  }
};
IOS.resizeBoard = (B) => { const el = B.el, W = el.offsetWidth, H = el.offsetHeight; if (!W || !H || (W === B.W && H === B.H)) return; B.W = W; B.H = H; B.out.width = Math.round(W * B.dpr); B.out.height = Math.round(H * B.dpr); B.staticScene = null; B.sceneDirty = true; B.kick(300); };
IOS.resetTab = (el) => { const B = el.__board, g = el.querySelector('.g[data-ind]'); if (!B || !g) return; const st = B.ind.get(g); if (st && st.sel !== +g.dataset.sel) { st.sel = +g.dataset.sel; B.kick(500); } };

// ------------------------------------------------------------------ 10. 启动（两种模式共用的部分）
IOS.mk = (id, dpr, fx) => { const s = S[id], glass = s.chrome !== undefined;
  return `<div class="${glass ? 'board' : 'scr'} ${s.land ? 'pl' : 'ph'} sB v1 sB-${id}" id="s-${id}" data-dpr="${dpr}"><div class="content${s.contentCls ? ' ' + s.contentCls : ''}">${s.content}</div>${glass ? `<canvas class="gl"></canvas><div class="chrome">${s.chrome}</div>` : ''}<div class="fx"></div>${s.over || ''}${fx ? `<div class="iosfx">${s.fixedStatus ? APP.status() : ''}<i class="isl"></i><i class="hm"></i></div>` : ''}</div>`; };
IOS.fail = (m) => { document.body.classList.add('nogl'); const e = document.getElementById('err'); if (e) { e.style.display = 'block'; e.textContent = '玻璃引擎没有启动（' + m + '）。底栏退回成了普通半透明，不是液态玻璃的效果。需要 WebGL2。'; } };
IOS.prepare = async () => {
  document.documentElement.style.background = '#0b0a08';
  await APP.loadBase(); await window.Mascot.ready(); IMG.msc_still = window.Mascot.snapshot(256);
  APP.derive('grain', 180, 180, (x, w, h) => { const d = x.createImageData(w, h); for (let i = 0; i < d.data.length; i += 4) { const v = 128 + (Math.random() - 0.5) * 90; d.data[i] = d.data[i + 1] = d.data[i + 2] = v; d.data[i + 3] = 60; } x.putImageData(d, 0, 0); }, 'image/png');
  K.prepare();
};
IOS.build = (ids) => { const done = {}; const one = (id) => { if (done[id]) return; done[id] = 1; const s = S[id]; (s.deps || []).forEach(one); if (s.build && s.content === undefined) Object.assign(s, s.build()); IOS.patchBuilt(); }; ids.forEach(one); };
/** after the boards are in the DOM: pictures, glass, her, each screen's init, wiring. Returns setState(id, st). */
IOS.initAll = async (main, ids) => {
  APP.fill(document);
  await window.G2.loadImages();
  await Promise.all([...document.images].map((im) => im.decode ? im.decode().catch(() => 0) : 0));
  if (document.fonts && document.fonts.ready) await document.fonts.ready;
  try {
    const t = document.createElement('canvas'); if (!t.getContext('webgl2')) IOS.fail('浏览器不支持 WebGL2'); else window.G2.init();
    document.querySelectorAll('.sB > .fx').forEach((f) => { f.style.backgroundImage = `url(${IMG.grain})`; });
    APP.mountMascots(main);
    ids.forEach((id) => { const el = document.getElementById('s-' + id); if (!el) return; if (S[id].init) S[id].init(el);
      if (id === 'player') IOS.playerInit(el);
      if (['settings', 'servers', 'me', 'series', 'movie', 'episode', 'set', 'person'].includes(id)) { const sh = el.__show; if (sh) el.__show = function () { const r = sh.apply(this, arguments); IOS.swapText(el); return r; }; IOS.swapText(el);
        if (id === 'settings' || id === 'servers') el.addEventListener('click', () => IOS.swapText(el)); }   // 页内自己换分区时（走它私有的 show）也换文案
      IOS.longPress(el); IOS.relayout(el); });
    APP.fill(main); APP.mountMascots(main); APP.wire(main);
    const setState = (id, st) => { const el = document.getElementById('s-' + id), s = S[id]; if (el && s.setState) { s.setState(el, st); APP.fill(el); APP.mountMascots(el); APP.wireScroll(el); el.querySelectorAll('.b-top[data-solid],.b-band[data-solid]').forEach((h) => h.__f && h.__f()); APP.anim(el, 500); } };
    APP.setState = setState;
    await Promise.all([...document.images].map((im) => im.decode ? im.decode().catch(() => 0) : 0));
    window.G2.renderAll();
    const bad = window.G2.boards.filter((b) => b.error); if (bad.length) IOS.fail(bad[0].error);
    return setState;
  } catch (e) { IOS.fail(String(e.stack || e)); console.error(e); document.body.insertAdjacentHTML('beforeend', `<pre style="position:fixed;left:0;top:0;z-index:99;background:#400;color:#fff;font-size:11px;padding:6px;white-space:pre-wrap">${String(e.stack || e)}</pre>`); return () => {}; }
};
IOS.ready = () => requestAnimationFrame(() => requestAnimationFrame(() => { window.G2.boards.forEach((b) => { b.sceneDirty = true; }); window.G2.renderAll(); document.body.dataset.ready = '1'; }));
})();
