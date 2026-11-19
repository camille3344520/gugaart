/* 咕咕嘎嘎 · 咕嘎画报（B1）· macOS 端 —— 窗口本体的适配层。
 * 在 ../desktop/js/app.js 和各 screens-*.js 之后、APP.boot() 之前加载：
 *   1. 图片包路径指回 ../desktop/pack/（不复制 50 MB 素材）
 *   2. 报头右端的 Windows 窗口键 → 左端的红绿灯（系统元素的几何：12 pt / 8 pt / 左 12 pt）
 *   3. 键位：Ctrl → ⌘，Alt+← → ⌘[，F5 → ⌘R，多出 ⌘] 前进、⌘1–5 换页、⌘F 筛选（Ctrl 那套仍然能按）
 *   4. 窗口状态：?blur=1 失焦、?fs=1 系统全屏、?notch=1 刘海屏全屏播放、?tl=0 不画红绿灯（给 Chrome --app 的原生标题栏让位）、播放器 st=pip 画中画
 *   5. 启动后把正文里的 Windows 说法换成 macOS 的说法；播放内核相关的描述一律改成「待定（上游决定）」，不编 macOS 的回退链
 *   6. 接收 screen.html（菜单栏壳）用 postMessage 发来的菜单命令
 * 版式、玻璃引擎、纸面语言、她的一切规矩都不动。
 */
(function () {
'use strict';
const APP = window.APP;
if (!APP) return;
const Q = APP.Q, S = APP.S, key = (k, extra) => document.body.dispatchEvent(new KeyboardEvent('keydown', Object.assign({ key: k, bubbles: true, cancelable: true }, extra || {})));

// 1. 图片包：复用电脑端的 pack/（相对本文档的路径）
const pend = {};
APP.need = (keys) => Promise.all([...new Set(keys)].filter((k) => k && !window.IMG[k]).map((k) => pend[k] || (pend[k] = new Promise((res) => {
  const s = document.createElement('script'); s.src = '../desktop/pack/' + k + '.js'; s.onload = s.onerror = () => res(); document.head.appendChild(s);
}))));

// 2. 红绿灯：关闭 / 最小化 / 缩放（真机上由系统画；这里是视觉演示：悬停出符号、按下变暗、失焦变灰）
APP.winKeys = () => '<div class="tlights" role="group" aria-label="窗口控制（系统）">' +
  '<a class="c" title="关闭 ⌘W"><svg viewBox="0 0 12 12"><path d="M3.4 3.4l5.2 5.2M8.6 3.4l-5.2 5.2"/></svg></a>' +
  '<a class="m" title="最小化 ⌘M"><svg viewBox="0 0 12 12"><path d="M2.8 6h6.4"/></svg></a>' +
  '<a class="z" title="全屏 ⌃⌘F（按住 ⌥ 是缩放）"><svg viewBox="0 0 12 12"><path d="M3 7.6V3h4.6zM9 4.4V9H4.4z"/></svg></a></div>';
// 报头 = 透明标题栏：红绿灯在左，VOL. / 日期、居中刊名不变（三端统一报头）
APP.mast = (solid) => '<header class="mast' + (solid ? ' solid' : '') + '">' + APP.winKeys() +
  '<div class="in"><span>VOL. <i class="vol"><u>318<br>319</u></i></span><span>十一月十四日</span></div>' +
  '<div class="ttl"><b>咕咕' + APP.im('emblem', 'emb') + '嘎嘎</b></div></header>';

// 3. 玻璃排里的键位 hint 换成 ⌘；右键菜单里收藏的键按 decisions.md Q6 改成 V
const chrome0 = APP.chrome;
APP.chrome = (o) => chrome0(o).replace(/Ctrl K/g, '⌘ K').replace(/Ctrl ,/g, '⌘ ,').replace(/Alt \+ ←/g, '⌘ [');
const ctx0 = APP.ctxHtml;
APP.ctxHtml = (it, mng, del) => ctx0(it, mng, del).replace('收藏 · 盖章<em>F</em>', '收藏 · 盖章<em>V</em>');

// ⌘ 键位（Ctrl 那套由 app.js 继续处理，两套并存）。浏览器 / Chrome --app 里 ⌘W ⌘Q ⌘, 可能被 Chrome 自己接走，真机没有这个问题
const TABS = ['home', 'discover', 'libs', 'colls', 'me'];
window.addEventListener('keydown', (e) => {
  if (!e.metaKey || e.ctrlKey || e.altKey) return;
  const k = e.key.toLowerCase();
  if (k === 'k') { e.preventDefault(); APP.nav('search'); }
  else if (k === ',') { e.preventDefault(); APP.nav('settings'); }
  else if (k === 'r') { e.preventDefault(); key('F5'); }
  else if (k === 'f') { e.preventDefault(); key('f'); }
  else if (e.key === '[') { e.preventDefault(); if (history.length > 1) history.back(); else APP.nav('home'); }
  else if (e.key === ']') { e.preventDefault(); history.forward(); }
  else if (/^[1-5]$/.test(e.key)) { e.preventDefault(); APP.nav(TABS[+e.key - 1]); }
}, true);

// 4. 窗口状态
window.addEventListener('blur', () => document.body.classList.add('unfocused'));
window.addEventListener('focus', () => document.body.classList.remove('unfocused'));
if (Q.get('blur')) document.body.classList.add('unfocused');
if (Q.get('fs') || Q.get('full')) document.body.classList.add('fs');
if (Q.get('notch')) document.body.classList.add('notch');
if (Q.get('tl') === '0') document.body.classList.add('tl0');
// 画中画：画面搬去系统的小窗，这里留一页说明（能力待定，见 notes.md）
const pipOn = (root, on) => { const pl = root.querySelector('#pl'); if (!pl) return; const fr = pl.querySelector('.pv .fr'); pl.classList.toggle('pip', on);
  let n = fr.querySelector('.pip-note'); if (on && !n) fr.insertAdjacentHTML('beforeend', `<div class="pip-note"><div><div class="kick">PICTURE IN PICTURE · 画中画</div><h3>画面在小窗里</h3><p>这一集正在画中画窗口里继续放，字幕和弹幕跟着过去了。小窗是系统的：拖到哪个角都行，别的应用盖不住它。</p><a class="btn" data-pip-back>${APP.ic('pip')}回到这里</a></div></div>`); if (!on && n) n.remove(); APP.anim(400); };
if (S.player) {
  const init0 = S.player.init;
  S.player.init = function (root, st, Qs) { const r = init0.call(this, root, st, Qs); if (st === 'pip') pipOn(root, true);
    root.addEventListener('click', (e) => { if (e.target.closest('[data-pip-back]')) pipOn(root, false); else if (e.target.closest('.pbtn[title="画中画"]')) pipOn(root, true); });
    // 画质面板里的「播放引擎」一组：内核是上游的事，macOS 上用什么、怎么回退都待定，这里不编（面板一打开就改；st=quality 时它在 init 里已经开了，所以先改一次）
    const side = root.querySelector('#side');
    const engine = () => { if (!side) return; const h = [...side.querySelectorAll('h6')].find((x) => /ENGINE/.test(x.textContent)); if (!h || h.dataset.mac) return; h.dataset.mac = '1';
      let n = h.nextElementSibling; while (n && n.classList.contains('o')) { const nx = n.nextElementSibling; if (!/外部播放器/.test(n.textContent)) n.remove(); n = nx; }
      h.insertAdjacentHTML('afterend', '<p style="font-size:1.25rem;line-height:1.8;color:var(--dim);font-style:italic;margin:.6rem 0">macOS 上用哪个内核、怎么回退由上游决定（待定）；这里只列能手动选的。</p>');
      swapText(side); };
    if (side) { new MutationObserver(engine).observe(side, { childList: true }); engine(); }
    return r; };
}

// 5. 启动后把正文里的 Windows 说法改成 macOS 的说法（只改字，不改版式）
const MAP = [
  [/Ctrl\s*\+\s*F/g, '⌘ F'], [/Ctrl\s*K/g, '⌘ K'], [/Ctrl\s*,/g, '⌘ ,'], [/Alt\s*\+\s*←/g, '⌘ ['], [/Ctrl/g, '⌘'], [/F5/g, '⌘ R'],
  [/最小化 \/ 最大化 \/ 关闭画在报头右端。/g, '红绿灯（关闭 / 最小化 / 全屏）由系统画在报头左端：报头就是透明标题栏。'],
  [/Windows 上自动回退：mpv → VLC → mpv 重试 → 服务器转码。/g, 'macOS 上用哪个内核、怎么回退由上游决定（待定），这里不编。'],
  [/自动（mpv）/g, '自动（待定）'], [/· mpv 内核/g, ''], [/ · mpv$/g, ''], [/1080p · 直接播放 · mpv/g, '1080p · 直接播放'],
  [/VERSION 0\.2\.13 · Windows · x64/g, 'VERSION 0.2.13 · macOS · Apple Silicon / Intel'],
  [/mpv 内核 · HEVC \/ AV1 硬解 · HDR 直通 · 杜比视界：不支持/g, '待定：以 macOS 上实际接入的内核为准'],
  [/%LOCALAPPDATA% 下的应用目录/g, '~/Library/Application Support 下的应用目录'], [/打开文件夹/g, '在访达中显示'],
  [/令牌存在系统的凭据管理器里。/g, '令牌存在钥匙串里。'], [/只清掉这台电脑上的会话。/g, '只清掉这台 Mac 上的会话。'],
  [/mpv 打不开这条 strm 直链：对方服务器返回 403。已经按顺序试过 mpv → VLC → mpv 重试，都不行。/g, '这条 strm 直链打不开：对方服务器返回 403。内核的回退顺序由上游决定（macOS 待定）。'],
  [/86 MB · 下载完重启一次就好，/g, '92 MB · 下载的是 .dmg：打开后把「咕咕嘎嘎」拖进「应用程序」替换旧的，再打开一次就好；'],
  [/右键一行：/g, '右键（或 ⌃ 点按）一行：'],
  [/^滚轮$/, '双指 / 滚轮'], [/^下载并更新$/, '下载安装包'],
];
const swapText = (root) => { const w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT), nodes = []; while (w.nextNode()) nodes.push(w.currentNode);
  nodes.forEach((n) => { let t = n.nodeValue; MAP.forEach(([re, s]) => { t = t.replace(re, s); }); if (t !== n.nodeValue) n.nodeValue = t; });
  root.querySelectorAll('[title]').forEach((el) => { el.title = el.title.replace(/Alt \+ ←/g, '⌘ [').replace(/F5/g, '⌘ R').replace(/Ctrl/g, '⌘'); }); };
const boot0 = APP.boot;
APP.boot = async function () {
  await boot0();
  swapText(document.body);
  // 设置 · 播放：快捷键表下面接一张 macOS 的 ⌘ 键位表（播放器键位以上游 PlayerKeys 为准，不改）
  const tb = APP.p === 'settings' && document.querySelector('.set .tb');
  if (tb) tb.insertAdjacentHTML('afterend', `<div class="mac-keys"><h5>COMMAND · ⌘ 键位（macOS）</h5><table class="tb">${[['⌘ R', '付印新刊（刷新）'], ['⌘ K 或 /', '搜索片库'], ['⌘ ,', '设置'], ['⌘ [ 、⌘ ]', '返回 / 前进（触控板双指左右滑、鼠标侧键也行）'], ['⌘ 1 – 5', '首页 / 发现 / 片库 / 合集 / 我的'], ['⌘ F', '海报墙筛选'], ['V 、W', '收藏 · 盖章 / 标记已看'], ['⌃ ⌘ F', '系统全屏（绿灯）'], ['⌘ M 、⌘ W 、⌘ Q', '最小化 / 关闭窗口 / 退出（系统）']].map((r) => `<tr><td class="n" style="width:16rem;color:var(--ink)">${r[0]}</td><td>${r[1]}</td></tr>`).join('')}</table></div>`);
  if (APP.dirty) APP.dirty();
};

// 6. 来自菜单栏壳（screen.html）的命令
window.addEventListener('message', (e) => { const d = e.data; if (!d || d.mac !== 'cmd') return; const [c, arg] = String(d.cmd).split(/:(.*)/);
  if (c === 'nav') APP.nav(arg); else if (c === 'reprint') key('F5'); else if (c === 'back') { if (history.length > 1) history.back(); else APP.nav('home'); } else if (c === 'fwd') history.forward();
  else if (c === 'filter') key('f'); else if (c === 'key') key(arg); else if (c === 'panel') { const a = document.querySelector(`[data-p="${arg}"]`); if (a) a.click(); }
  else if (c === 'pip') pipOn(APP.root, !APP.root.querySelector('#pl.pip')); else if (c === 'fav') { const a = document.querySelector('[data-fav]'); if (a) a.click(); } });
})();
