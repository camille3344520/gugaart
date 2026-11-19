/* 咕咕嘎嘎 · 咕嘎画报（B1）· macOS 端 —— 应用菜单栏。
 * 菜单栏和下拉菜单是 macOS 画的（系统外观、系统字体、系统高亮色、系统动效），不是画报语言，也不是玻璃。
 * 我们能定的只有：菜单标题、每条命令、⌘ 键位、可用 / 勾选状态、子菜单。标 sys:1 的条目是系统自动加的（我们不写、也删不掉）。
 * 这个文件被画廊（index.html）和「整块屏幕」壳（screen.html）共用；窗口本体（board.html）不画菜单栏。
 * 键位和 ../../docs/platforms.md §2 的快捷键表、decisions.md Q6 一致；macOS 新增的只有 ⌘R / ⌘1–5 / ⌘[ ⌘]（见 notes.md「需要主理人定的事」）。
 */
(function () {
'use strict';
const SUB_SUBS = [['关', ''], ['简体中文', 'on'], ['繁體中文', ''], ['English', ''], ['双语（中上英下）', ''], '-', ['在线搜索字幕…', '']];
const AUDIO = [['国语', 'on'], ['国语 5.1', ''], ['粤语', '']];
const SPEED = [['0.5×', ''], ['0.75×', ''], ['1.0×', 'on'], ['1.25×', ''], ['1.5×', ''], ['2.0×', '']];
const sub = (list, cmd) => list.map((x) => x === '-' ? '-' : { l: x[0], on: x[1] === 'on', cmd, player: 1 });
const MENUS = [
  { t: '咕咕嘎嘎', app: 1, items: [
    { l: '关于咕咕嘎嘎', cmd: 'nav:settings&st=about' },
    '-',
    { l: '设置…', k: '⌘,', cmd: 'nav:settings' },
    { l: '检查更新…', cmd: 'nav:dialogs' },
    '-',
    { l: '服务', sys: 1, sub: [{ l: '（系统填充）', dis: 1 }] },
    '-',
    { l: '隐藏咕咕嘎嘎', k: '⌘H', sys: 1 },
    { l: '隐藏其他', k: '⌥⌘H', sys: 1 },
    { l: '全部显示', sys: 1 },
    '-',
    { l: '退出咕咕嘎嘎', k: '⌘Q', sys: 1 },
  ] },
  { t: '文件', items: [
    { l: '付印新刊（刷新）', k: '⌘R', cmd: 'reprint' },
    '-',
    { l: '切换账号 / 服务器…', cmd: 'nav:account&st=switch' },
    { l: '退出登录', cmd: 'nav:servers&st=login' },
    '-',
    { l: '关闭窗口', k: '⌘W', sys: 1 },
  ] },
  { t: '编辑', items: [
    { l: '撤销', k: '⌘Z', sys: 1, dis: 1 }, { l: '重做', k: '⇧⌘Z', sys: 1, dis: 1 },
    '-',
    { l: '剪切', k: '⌘X', sys: 1 }, { l: '拷贝', k: '⌘C', sys: 1 }, { l: '粘贴', k: '⌘V', sys: 1 }, { l: '全选', k: '⌘A', sys: 1 },
    '-',
    { l: '搜索片库…', k: '⌘K', cmd: 'nav:search' },
    '-',
    { l: '开始听写…', k: 'fn fn', sys: 1 }, { l: '表情与符号', k: 'fn E', sys: 1 },
  ] },
  { t: '显示', items: [
    { l: '首页', k: '⌘1', cmd: 'nav:home', radio: 1 }, { l: '发现', k: '⌘2', cmd: 'nav:discover', radio: 1 }, { l: '片库', k: '⌘3', cmd: 'nav:libs', radio: 1 }, { l: '合集', k: '⌘4', cmd: 'nav:colls', radio: 1 }, { l: '我的', k: '⌘5', cmd: 'nav:me', radio: 1 },
    '-',
    { l: '返回', k: '⌘[', cmd: 'back' }, { l: '前进', k: '⌘]', cmd: 'fwd' },
    '-',
    { l: '筛选', k: '⌘F', cmd: 'filter', wall: 1 },
    '-',
    { l: '进入全屏', k: '⌃⌘F', sys: 1 },
  ] },
  { t: '播放', items: [
    { l: '播放 / 暂停', k: '空格', cmd: 'key: ', player: 1 },
    { l: '快退 10 秒', k: '←', player: 1 }, { l: '快进 10 秒', k: '→', player: 1 },
    { l: '上一集', player: 1 }, { l: '下一集', k: 'N', player: 1 },
    '-',
    { l: '音量 +', k: '↑', player: 1 }, { l: '音量 −', k: '↓', player: 1 },
    '-',
    { l: '字幕', sub: sub(SUB_SUBS, 'key:s'), player: 1 }, { l: '音轨', sub: sub(AUDIO, 'key:a'), player: 1 }, { l: '倍速', sub: sub(SPEED, 'panel:speed'), player: 1 },
    { l: '弹幕', k: 'D', on: 1, cmd: 'key:d', player: 1 },
    '-',
    { l: '收藏 · 盖章', k: 'V', cmd: 'fav' }, { l: '标记已看', k: 'W' },
    '-',
    { l: '画中画', cmd: 'pip', player: 1, note: '能力待定' }, { l: '投屏（DLNA）…', player: 1 },
    { l: '播放器全屏', k: 'F', cmd: 'key:f', player: 1 },
  ] },
  { t: '窗口', items: [
    { l: '最小化', k: '⌘M', sys: 1 }, { l: '缩放', sys: 1 },
    '-',
    { l: '窗口置顶', note: '播放时', player: 1 },
    '-',
    { l: '前置全部窗口', sys: 1 },
    '-',
    { l: '咕咕嘎嘎', on: 1, sys: 1 },
  ] },
  { t: '帮助', items: [
    { l: '搜索', field: 1, sys: 1 },
    { l: '咕咕嘎嘎帮助' },
    { l: '快捷键一览', cmd: 'nav:settings&st=play' },
    '-',
    { l: '导出诊断日志…', cmd: 'nav:settings&st=about' },
    { l: '开源许可', cmd: 'nav:settings&st=about' },
  ] },
];
const APPLE = '<svg viewBox="0 0 16 16"><path d="M8.2 4.6c-.6 0-1.5-.5-2.5-.5-1.7 0-3.4 1.5-3.4 4.2 0 2.9 2.1 6.2 3.6 6.2.8 0 1.2-.5 2.1-.5s1.2.5 2.1.5c1.5 0 3.2-2.9 3.5-4.5-1.9-.8-2.4-3.3-.6-4.6-.8-1-2-1.3-2.9-1.3-.9 0-1.5.5-1.9.5z"/><path d="M8.3 4.1c-.1-1.5 1-2.8 2.5-3 .1 1.6-1.1 2.9-2.5 3z"/></svg>';
const ICONS = {
  np: '<svg viewBox="0 0 18 18"><path d="M5 3.8v10.4l8.2-5.2z" fill="currentColor" stroke="none"/></svg>',
  ime: '<i class="ime">拼</i>',
  wifi: '<svg viewBox="0 0 18 18"><path d="M2 6.5C4 4.6 6.4 3.6 9 3.6s5 1 7 2.9M4.3 9.2c1.3-1.2 3-1.9 4.7-1.9s3.4.7 4.7 1.9M6.6 11.9c.7-.6 1.5-.9 2.4-.9s1.7.3 2.4.9"/><circle cx="9" cy="14.4" r=".9" fill="currentColor" stroke="none"/></svg>',
  bat: '<svg viewBox="0 0 20 14"><rect x="1" y="3" width="15" height="8" rx="2"/><path d="M17.5 5.5v3"/><rect x="2.6" y="4.6" width="10" height="4.8" rx="1" fill="currentColor" stroke="none"/></svg>',
  cc: '<svg viewBox="0 0 18 18"><rect x="2" y="3.5" width="14" height="4.2" rx="2.1"/><rect x="2" y="10.3" width="14" height="4.2" rx="2.1"/><circle cx="5.6" cy="5.6" r="1.3" fill="currentColor" stroke="none"/><circle cx="12.4" cy="12.4" r="1.3" fill="currentColor" stroke="none"/></svg>',
};
const esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
// 键位：修饰键用系统的符号顺序 ⌃ ⌥ ⇧ ⌘，字母和符号靠右
const keyHtml = (k) => { if (!k) return ''; const m = k.match(/^([⌃⌥⇧⌘]*)(.*)$/); return `<kbd><i>${m[1]}</i>${esc(m[2])}</kbd>`; };
const itemsHtml = (items, ctx) => items.map((it) => {
  if (it === '-') return '<hr>';
  const dis = it.dis || (it.player && ctx.context !== 'player') || (it.wall && ctx.context && ctx.context !== 'wall');
  if (it.field) return `<div class="mf${it.sys ? ' sys' : ''}"><span>搜索</span></div>`;
  return `<a class="mi${dis ? ' dis' : ''}${it.on ? ' on' : ''}${it.sys ? ' sys' : ''}${it.sub ? ' has' : ''}"${it.cmd && !dis ? ` data-cmd="${esc(it.cmd)}"` : ''}><span class="ck">${it.on ? '✓' : ''}</span><span class="l">${esc(it.l)}</span>${it.note ? `<em>${esc(it.note)}</em>` : ''}${keyHtml(it.k)}${it.sub ? '<span class="ar">›</span><div class="mdd sub">' + itemsHtml(it.sub, ctx) + '</div>' : ''}</a>`;
}).join('');
const NP_DEFAULT = { title: '第 5 集 · 江心白南弈风组队', sub: '颜心记 — 咕咕嘎嘎', art: '56342-primary' };

window.MacMenu = {
  MENUS,
  /** mount(el, { context: 'player'|'wall'|'page'|null, annot, nowPlaying, notch, openTitle, openNP, onCommand }) */
  mount(el, o) {
    o = o || {}; const ctx = { context: o.context || null };
    el.classList.add('mb'); if (o.notch) el.classList.add('notch'); if (o.annot) el.classList.add('annot');
    el.innerHTML = `<span class="apple" title="苹果菜单（系统）">${APPLE}</span>` +
      MENUS.map((m, i) => `<span class="mt${m.app ? ' app' : ''}" data-i="${i}">${esc(m.t)}<div class="mdd">${itemsHtml(m.items, ctx)}</div></span>`).join('') +
      `<span class="r">${o.nowPlaying === false ? '' : `<span class="st np" title="正在播放（系统的「正在播放」，内容由我们报上去）">${ICONS.np}<div class="pop" id="mb-np"></div></span>`}` +
      `<span class="st">${ICONS.ime}</span><span class="st">${ICONS.cc}</span><span class="st">${ICONS.wifi}</span><span class="st">${ICONS.bat}</span><em id="mb-date"></em><em id="mb-time"></em></span>`;
    const np = o.nowPlaying === false ? null : Object.assign({}, NP_DEFAULT, o.nowPlaying || {});
    if (np) el.querySelector('#mb-np').innerHTML = `<div class="art" data-art="${esc(np.art)}"></div><div class="tx"><b>${esc(np.title)}</b><span>${esc(np.sub)}</span></div><div class="ctl"><a title="上一集"><svg viewBox="0 0 16 16"><path d="M3.5 3v10M13 3l-8 5 8 5z" fill="currentColor"/></svg></a><a class="pp" title="暂停"><svg viewBox="0 0 16 16"><path d="M4 3h3v10H4zM9 3h3v10H9z" fill="currentColor"/></svg></a><a title="下一集"><svg viewBox="0 0 16 16"><path d="M12.5 3v10M3 3l8 5-8 5z" fill="currentColor"/></svg></a></div>`;
    // 真实的系统日期时间（菜单栏右端是系统的）
    const tick = () => { const n = new Date(); el.querySelector('#mb-time').textContent = n.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }); el.querySelector('#mb-date').textContent = n.toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' }); };
    tick(); setInterval(tick, 10000);
    // 打开 / 切换 / 关闭：点标题打开，开着时滑到别的标题就换，点外面或 Esc 关；← → 换菜单
    let open = null;
    const set = (t) => { if (open) open.classList.remove('open'); open = t || null; if (open) open.classList.add('open'); el.classList.toggle('menu-open', !!open); };
    el.querySelectorAll('.mt').forEach((t) => {
      t.addEventListener('mousedown', (e) => { if (e.target.closest('.mdd')) return; e.preventDefault(); set(open === t ? null : t); });
      t.addEventListener('mouseenter', () => { if (open && open !== t) set(t); });
    });
    const npEl = el.querySelector('.np');
    if (npEl) npEl.addEventListener('mousedown', (e) => { if (e.target.closest('.pop')) return; e.preventDefault(); set(null); npEl.classList.toggle('open'); MacMenu.loadArt(npEl); });
    el.addEventListener('click', (e) => { const a = e.target.closest('a.mi'); if (!a || a.classList.contains('dis') || a.classList.contains('has')) return; const cmd = a.dataset.cmd; set(null); if (cmd && o.onCommand) o.onCommand(cmd); });
    window.addEventListener('mousedown', (e) => { if (!el.contains(e.target)) { set(null); if (npEl) npEl.classList.remove('open'); } });
    window.addEventListener('keydown', (e) => { if (e.key === 'Escape') { set(null); if (npEl) npEl.classList.remove('open'); } if (open && (e.key === 'ArrowLeft' || e.key === 'ArrowRight')) { const ts = [...el.querySelectorAll('.mt')], i = ts.indexOf(open); set(ts[(i + (e.key === 'ArrowRight' ? 1 : ts.length - 1)) % ts.length]); e.preventDefault(); } });
    if (o.openTitle != null) { const ts = [...el.querySelectorAll('.mt')], t = ts[o.openTitle] || ts.find((x) => x.firstChild.textContent === o.openTitle); if (t) set(t); }
    if (o.openNP && npEl) { npEl.classList.add('open'); MacMenu.loadArt(npEl); }
    return { set, el };
  },
  /** 「正在播放」里的封面：按需从电脑端的图片包读一张（不复制素材） */
  loadArt(npEl) { const art = npEl.querySelector('.art'); if (!art || art.dataset.done) return; art.dataset.done = '1';
    window.PACK = window.PACK || ((k, v) => { (window.IMG = window.IMG || {})[k] = v; });
    const k = art.dataset.art, s = document.createElement('script'); s.src = '../desktop/pack/' + k + '.js'; s.onload = () => { const v = (window.IMG || {})[k]; if (v) art.style.backgroundImage = `url(${v})`; }; document.head.appendChild(s); },
};
})();
