/*
 * 咕咕嘎嘎 · B1「头版」· 电脑端 —— shell. One board per page: board.html?p=<screen>[&st=<state>][&id=<item>][&shot=1]; the window size IS the viewport.
 * Glass (nav pill, round buttons, the account menu that grows out of its button, the player's floating pills) = engine.js + core.js + glass.js,
 * copied unmodified from ../../fable-huabao/js. Everything else is plain HTML in the magazine's own language.
 * Pictures: pack/<key>.js (one data URL each, made by tools/pack.mjs from ../data/img) loaded on demand; real data: js/lib.js (window.LIB).
 */
(function () {
'use strict';
const Q = new URLSearchParams(location.search), LIB = window.LIB, IMG = window.IMG = {};
const APP = window.APP = { Q, p: Q.get('p') || 'home', st: Q.get('st') || '', shot: Q.get('shot') ? 1 : null, S: {}, LIB };
const clamp = APP.clamp = (v, a, b) => Math.max(a, Math.min(b, v));
APP.pad = (n) => (n < 10 ? '0' : '') + n;
APP.esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
APP.ic = (n, s) => `<svg class="ic"${s ? ` style="${s}"` : ''}><use href="#i-${n}"/></svg>`;

// ------------------------------------------------------------------ pictures
const pend = {};
window.PACK = (k, v) => { IMG[k] = v; };
APP.need = (keys) => Promise.all([...new Set(keys)].filter((k) => k && !IMG[k]).map((k) => pend[k] || (pend[k] = new Promise((res) => { const s = document.createElement('script'); s.src = 'pack/' + k + '.js'; s.onload = s.onerror = () => res(); document.head.appendChild(s); }))));
APP.key = (path) => path ? String(path).replace(/^img\//, '').replace(/\.\w*$/, '') : null;
APP.im = (key, cls, style) => key ? `<img class="${cls || ''}" data-img="${key}"${style ? ` style="${style}"` : ''} alt="" draggable="false">` : '';
APP.fill = async (root) => {
  (root || document).querySelectorAll('.bleed').forEach((b) => { if (!b.querySelector('.sc')) b.insertAdjacentHTML('beforeend', '<i class="sc"></i>'); });
  const ims = [...(root || document).querySelectorAll('img[data-img]')].filter((im) => im.dataset.img !== im.dataset.got);
  await APP.need(ims.map((im) => im.dataset.img));
  ims.forEach((im) => { im.dataset.got = im.dataset.img; im.src = IMG[im.dataset.img] || ''; });
  await Promise.all(ims.map((im) => im.decode ? im.decode().catch(() => 0) : 0));
  if (APP.glassUp) { await window.G2.loadImages(); APP.dirty(); }
};
APP.setImg = (el, key) => { if (!el || el.dataset.img === key) return; el.dataset.img = key; APP.need([key]).then(() => { if (el.dataset.img === key) { el.src = IMG[key] || ''; el.dataset.got = key; if (APP.glassUp) window.G2.loadImages().then(APP.dirty); } }); };
const BASE = {};
APP.imgOf = (key) => BASE[key] || (BASE[key] = APP.need([key]).then(() => new Promise((res) => { const im = new Image(); im.onload = im.onerror = () => res(im); im.src = IMG[key] || ''; })));
// 专色: the strongest hue of the poster, pushed to a lightness that reads on ink-black paper (same rule as the phone B1)
const SPOT = {};
APP.spot = async (key) => {
  if (!key) return '#EA4E30'; if (SPOT[key]) return SPOT[key];
  const im = await APP.imgOf(key); if (!im.naturalWidth) return '#EA4E30';
  const c = document.createElement('canvas'); c.width = c.height = 24; const x = c.getContext('2d', { willReadFrequently: true }); x.drawImage(im, 0, 0, 24, 24);
  const d = x.getImageData(0, 0, 24, 24).data, bins = []; for (let i = 0; i < 12; i++) bins.push({ w: 0, r: 0, g: 0, b: 0 });
  for (let i = 0; i < d.length; i += 4) { const r = d[i], g = d[i + 1], b = d[i + 2], mx = Math.max(r, g, b), mn = Math.min(r, g, b), s = mx ? (mx - mn) / mx : 0, v = mx / 255;
    let h = 0; if (mx !== mn) { h = mx === r ? (g - b) / (mx - mn) : mx === g ? 2 + (b - r) / (mx - mn) : 4 + (r - g) / (mx - mn); h = (h * 60 + 360) % 360; }
    const w = s * s * (0.25 + v), bn = bins[Math.floor(h / 30) % 12]; bn.w += w; bn.r += r * w; bn.g += g * w; bn.b += b * w; }
  const top = bins.slice().sort((a, b) => b.w - a.w)[0]; if (top.w < 0.5) return (SPOT[key] = '#EA4E30');   // grey poster: fall back to vermilion
  let col = [top.r / top.w, top.g / top.w, top.b / top.w]; const mid = (Math.max(...col) + Math.min(...col)) / 2 || 1;
  col = col.map((v) => mid + (v - mid) * 1.25); const l = (Math.max(...col) + Math.min(...col)) / 2 || 1; col = col.map((v) => Math.round(clamp(v * 0.6 * 255 / l, 0, 255)));
  return (SPOT[key] = `rgb(${col.join(',')})`);
};
APP.setSpot = (key) => APP.spot(key).then((c) => { APP.root.style.setProperty('--spot', c); APP.anim(800); });

// ------------------------------------------------------------------ data helpers (window.LIB = ../data/library.json + extra*.json)
const X1 = LIB['extra:extra'] || {}, X2 = LIB['extra:extra2'] || {}; APP.X1 = X1; APP.X2 = X2;
const ITEM = APP.ITEM = {};
(function index(o, depth) { if (!o || typeof o !== 'object' || depth > 6) return; if (Array.isArray(o)) { o.forEach((x) => index(x, depth + 1)); return; }
  if (o.id && o.name && (o.type || o.poster || o.images)) { const old = ITEM[o.id]; if (!old || JSON.stringify(o).length > JSON.stringify(old).length) ITEM[o.id] = Object.assign({}, old, o); }
  Object.values(o).forEach((x) => index(x, depth + 1)); })(LIB, 0);
LIB.details.forEach((d) => { ITEM[d.id] = d; });
Object.entries(X2.continueSeriesArt || {}).forEach(([n, a]) => { const it = ITEM[a.id] || (ITEM[a.id] = { id: a.id, name: n, type: 'Series', images: {} }); it.images = Object.assign({}, it.images, { backdrop: a.backdrop, logo: a.logo }); });
APP.byName = (n) => Object.values(ITEM).filter((i) => i.name === n && i.type !== 'Episode').sort((a, b) => JSON.stringify(b).length - JSON.stringify(a).length)[0];
const GEN = { 'War & Politics': '战争政治', 'Action & Adventure': '动作冒险', Kids: '儿童', Mystery: '悬疑', Family: '家庭', Crime: '犯罪', Documentary: '纪录', Animation: '动画', Comedy: '喜剧', 'Sci-Fi & Fantasy': '科幻奇幻', '中文字幕': null, Drama: '剧情' };
APP.genres = (it, n) => (it.genres || []).map((g) => g in GEN ? GEN[g] : g).filter(Boolean).slice(0, n || 2);
APP.poster = (it) => APP.key(it.poster || (it.images && it.images.poster));
APP.bd = (it) => APP.key(it.images && (it.images.backdrop || it.images.thumb));
APP.isAnime = (it) => (it.genres || []).some((g) => g === '动画' || g === 'Animation');
APP.kind = (it) => it.type === 'Movie' ? '电影' : it.type === 'Episode' ? '单集' : APP.isAnime(it) ? '动画' : '剧集';
APP.dur = (s) => { if (!s) return ''; const m = Math.round(s / 60); return m >= 60 ? `${Math.floor(m / 60)} 小时 ${m % 60} 分` : `${m} 分`; };
APP.clock = (s) => { s = Math.round(s || 0); const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60); return (h ? h + ':' + APP.pad(m) : m) + ':' + APP.pad(s % 60); };
APP.len = (it) => it.type === 'Movie' ? APP.dur(it.runtimeSec) : it.episodeCount ? (it.status === 'Continuing' ? `更新至 ${it.episodeCount} 集` : `全 ${it.episodeCount} 集`) : '';
APP.score = (it) => it.rating ? (it.rating >= 10 ? '10' : (+it.rating).toFixed(1)) : '—';
APP.meta = (it) => [it.year, ...APP.genres(it, 2)].filter(Boolean).join(' · ');
APP.stand = (it, max) => { const t = it.tagline; if (t && /[一-龥]/.test(t) && !/配信/.test(t)) return t; const o = (it.overview || '').replace(/\s+/g, ''); if (!o) return ''; max = max || 70;
  let out = ''; for (const s of o.split(/(?<=[。！？])/)) { if (out && (out + s).length > max) break; out += s; } return out.length > max + 12 ? out.slice(0, max) + '……' : out; };
const DARK_LOGO = { 44983: 1, 44970: 1 };
APP.title = (it, cls, style) => it.images && it.images.logo ? APP.im(APP.key(it.images.logo), 'logo ' + (DARK_LOGO[it.id] ? 'dk ' : '') + (cls || ''), style) : `<b class="tname ${cls || ''}"${style ? ` style="${style}"` : ''}>${APP.esc(it.name)}</b>`;
APP.go = (it) => `data-go="${it.type === 'Movie' ? 'movie' : it.type === 'Person' ? 'person' : 'series'}:${it.id}"`;
APP.favSlot = (sm, on) => `<i class="slot${sm ? ' sm' : ''}${on ? ' on' : ''}"><b>藏</b></i>`;
APP.mk = (on) => `<i class="mk${on ? ' on' : ''}" title="标记已看"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path pathLength="1" d="M3 12.5c2.5 2 4.200 4.500 5.500 7.500C11.500 13 15 8 21.500 3.500"/></svg><u></u></i>`;
/** a poster card. o: { no, sub, pct, cls } */
APP.pc = (it, o) => { o = o || {}; const po = APP.poster(it), p = o.pct != null ? o.pct : it.user && it.user.playedPct;
  return `<div class="pc ${o.cls || ''}" ${APP.go(it)} data-ctx="${it.id}" tabindex="0" ${o.attr || ''}><div class="po">${po ? APP.im(po) : `<div class="ph" style="position:absolute;inset:0;font-size:1.8rem"><div><small>NO COVER</small>${APP.esc(it.name)}</div></div>`}<i class="sheen"></i>${p ? `<u style="width:${p}%"></u>` : ''}</div>` +
    `<div class="cap${o.no ? '' : ' nn'}">${o.no ? `<em>${o.no}</em>` : ''}<b>${APP.esc(it.name)}</b><span class="meta">${o.sub || APP.meta(it)}</span><span class="qa"><i class="q" data-play>${APP.ic('play')}播放</i><i class="q favbox${it.user && it.user.favorite ? ' faved' : ''}" data-fav title="收藏">${APP.favSlot(1, it.user && it.user.favorite)}</i>${APP.mk(it.user && it.user.played)}<i class="q" data-more title="更多">${APP.ic('more')}</i></span></div></div>`; };
APP.hd = (no, cn, en, right) => `<div class="hd">${no ? `<em>${no}</em>` : ''}<b>${cn}</b><em>${en}</em><i></i>${right || ''}</div>`;
APP.msc = (cls, size, extra) => `<i class="msc ${cls || ''}" data-size="${size || 56}" style="width:${(size || 56) / 10}rem;height:${(size || 56) / 10}rem" ${extra || ''}>${APP.im('msc_still')}</i>`;
APP.pbox = (o, cap, text, big) => `<div class="pose"><div class="pose-art"${big ? ` style="width:${big}rem;height:${big}rem"` : ''}>${APP.pose(o)}</div><small><i>轮廓占位 · ${cap}</i>${text || ''}</small></div>`;
APP.ph = (name, small, style) => `<div class="ph" style="${style || ''}"><div>${small ? `<small>${small}</small>` : ''}${APP.esc(name)}</div></div>`;

// ------------------------------------------------------------------ springs + glass refresh
APP.spring = (fn, k, z) => {
  const s = { x: 0, v: 0, t: 0, raf: 0 }; k = k || 170; z = z || 0.82; let last = 0;
  const step = (now) => { s.raf = 0; const dt = Math.max(0.001, Math.min(0.04, (now - last) / 1000 || 0.016)); last = now; const n = 4, h = dt / n;
    for (let i = 0; i < n; i++) { s.v += (-k * (s.x - s.t) - 2 * z * Math.sqrt(k) * s.v) * h; s.x += s.v * h; }
    if (Math.abs(s.x - s.t) < 0.001 && Math.abs(s.v) < 0.01) { s.x = s.t; s.v = 0; fn(s.x); return; } fn(s.x); s.raf = requestAnimationFrame(step); };
  s.to = (t) => { s.t = t; if (APP.shot && !Q.get('anim')) { s.x = t; s.v = 0; fn(t); return; } if (!s.raf) { last = performance.now(); s.raf = requestAnimationFrame(step); } };
  s.set = (x) => { s.x = s.t = x; s.v = 0; fn(x); };
  return s;
};
const B = () => APP.root && APP.root.__board;
APP.anim = (ms) => { const b = B(); if (b) { b.animUntil = performance.now() + ms; b.sceneDirty = true; b.kick(ms + 120); } };
APP.dirty = () => { const b = B(); if (b) { b.sceneDirty = true; b.kick(); } };

// ------------------------------------------------------------------ the cover flow (ported from the phone B1: continuous drag with inertia; plus hover tilt, horizontal wheel, arrow keys)
APP.flow = (stage, o) => {
  const cards = [...stage.querySelectorAll('.cv')], n = cards.length, tilt = { x: 0, y: 0 }; let cur = -1, d = null;
  const draw = (pos) => { cards.forEach((c, j) => { o.lay(c, j - pos, tilt); c.classList.toggle('f', Math.abs(j - pos) < 0.5); }); if (o.onPos) o.onPos(pos, tilt);
    const i = clamp(Math.round(pos), 0, n - 1); if (i !== cur) { const first = cur < 0; cur = i; o.onIndex(i, first); } };
  const s = APP.spring(draw, 150, 0.8), tx = APP.spring((v) => { tilt.x = v; draw(s.x); }, 240, 0.9), ty = APP.spring((v) => { tilt.y = v; draw(s.x); }, 240, 0.9);
  s.set(o.start);
  const step = () => o.step();
  stage.addEventListener('pointerdown', (e) => { if (e.button === 0) d = { x: e.clientX, p: s.x, t: performance.now(), lx: e.clientX, v: 0, horiz: false }; });
  stage.addEventListener('pointermove', (e) => { if (d && d.horiz) return; const r = stage.getBoundingClientRect(); tx.to(clamp(((e.clientX - r.left) / r.width - 0.5) * 2, -1, 1)); ty.to(clamp(((e.clientY - r.top) / r.height - 0.5) * 2, -1, 1)); });
  stage.addEventListener('pointerleave', () => { tx.to(0); ty.to(0); });
  window.addEventListener('pointermove', (e) => { if (!d) return; const dx = e.clientX - d.x; if (!d.horiz) { if (Math.abs(dx) < 6) return; d.horiz = true; stage.classList.add('drag'); }
    const now = performance.now(); d.v = 0.6 * d.v + 0.4 * (e.clientX - d.lx) / Math.max(1, now - d.t); d.lx = e.clientX; d.t = now;
    let p = d.p - dx / step(); if (p < 0) p *= 0.35; if (p > n - 1) p = n - 1 + (p - (n - 1)) * 0.35; s.set(p); tx.to(clamp(-d.v * 1.2, -1, 1)); });
  window.addEventListener('pointerup', () => { if (d && d.horiz) { s.to(clamp(Math.round(s.x - d.v * 170 / step()), 0, n - 1)); stage.__swiped = performance.now(); stage.classList.remove('drag'); tx.to(0); } d = null; });
  let acc = 0; stage.addEventListener('wheel', (e) => { const dx = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : (e.shiftKey ? e.deltaY : 0); if (!dx) return; e.preventDefault(); acc += dx; if (Math.abs(acc) > 60) { s.to(clamp(Math.round(s.t) + Math.sign(acc), 0, n - 1)); acc = 0; } }, { passive: false });
  cards.forEach((c, j) => c.addEventListener('click', () => { if (performance.now() - (stage.__swiped || 0) < 120) return; if (j !== cur) s.to(j); else if (o.onOpen) o.onOpen(j); }));
  return { go: (i) => s.to(clamp(i, 0, n - 1)), step: (k) => s.to(clamp(Math.round(s.t) + k, 0, n - 1)), cur: () => cur, s, n };
};

// ------------------------------------------------------------------ fixed layer: masthead (= window title bar) + the glass row
const PENGUIN = '<svg viewBox="0 0 24 24"><path d="M12 2.5c5.600 0 9.500 3.900 9.500 9.600 0 4.600-2.200 8.400-4.400 9.400H6.900c-2.200-1-4.400-4.800-4.400-9.400C2.500 6.400 6.400 2.500 12 2.500z" fill="#3b3d47" stroke="#EFE6D4" stroke-width=".9"/><ellipse cx="12" cy="16" rx="5.600" ry="5" fill="#EFE6D4"/><circle cx="8.200" cy="8" r="2.100" fill="#fff"/><circle cx="15.800" cy="8" r="2.100" fill="#fff"/><circle cx="8.500" cy="8.200" r=".95" fill="#111"/><circle cx="15.500" cy="8.200" r=".95" fill="#111"/><path d="M9.500 10.300h5L12 13.300z" fill="#F6C453"/></svg>';
APP.PENGUIN = PENGUIN;
APP.winKeys = () => `<div class="win"><a title="最小化"><svg viewBox="0 0 10 10"><path d="M1 5.500h8"/></svg></a><a title="最大化"><svg viewBox="0 0 10 10"><rect x="1.500" y="1.500" width="7" height="7"/></svg></a><a class="x" title="关闭"><svg viewBox="0 0 10 10"><path d="M1.500 1.500l7 7M8.500 1.500l-7 7"/></svg></a></div>`;
APP.mast = (solid) => `<header class="mast${solid ? ' solid' : ''}"><div class="in"><span>VOL. <i class="vol"><u>318<br>319</u></i></span><span>十一月十四日</span></div><div class="ttl"><b>咕咕${APP.im('emblem', 'emb')}嘎嘎</b></div>${APP.winKeys()}</header>`;
const TABS = [['首页', 'home'], ['发现', 'discover'], ['片库', 'libs'], ['合集', 'colls'], ['我的', 'me']];
APP.chrome = (o) => (o.sub ? `<a class="g rb" id="g-back" data-mat="clear" data-press title="返回（Alt + ←）">${APP.ic('back')}</a>` : '') +
  (o.tab == null ? '' : `<div class="g tabs${o.sub ? ' sub' : ''}" data-mat="bar" data-ind="tab" id="g-tabs">${TABS.map((t, i) => `<a data-cell class="tab${i === o.tab ? ' on' : ''}"><span>${t[0]}</span><i></i></a>`).join('')}</div>`) +
  `<a class="g rb" id="g-search" data-mat="clear" data-press>${APP.ic('search')}<span>搜索</span><kbd>Ctrl K</kbd></a>` +
  `<a class="g rb" id="g-me" data-mat="clear" data-press data-toggle="#g-menu">影</a>` +
  `<div class="g${o.menu ? ' open' : ''}" id="g-menu" data-mat="pop" data-from="#g-me"><div class="who"><i>影</i><div><b>影迷</b><small>家里的片库 · 已连接</small></div></div>` +
  [['hist', '观看记录', 'me', ''], ['heart', '我的收藏', 'me&st=fav', ''], ['user', '切换账号 / 服务器', 'switch', ''], 0, ['set', '设置', 'settings', 'Ctrl ,'], ['dl', '检查更新', 'dialogs', 'v0.2.13'], 0, ['x', '退出登录', 'login', '']]
    .map((r) => r ? `<a data-nav="${r[2]}">${APP.ic(r[0])}${r[1]}<em>${r[3]}</em></a>` : '<hr>').join('') + `</div>` + (o.extra || '');
APP.nav = (q) => { location.href = 'board.html?p=' + q + (APP.shot ? '&shot=1' : ''); };

// ------------------------------------------------------------------ right-click menu: daily actions first; 「管理」is a separate group, delete asks twice (the row itself turns into the confirmation)
APP.ctxHtml = (it, mng, del) => `<div class="ctx-h"><b>${APP.esc(it.name)}</b><span>${APP.kind(it)} · ${it.year || ''}</span></div>` +
  [['play', '播放', 'Enter'], ['replay', '从头播放', ''], ['heart', '收藏 · 盖章', 'F'], ['check', '标记已看', 'W'], ['list', '加入待看清单', ''], ['info', '查看详情', '']].map((r) => `<a>${APP.ic(r[0])}${r[1]}<em>${r[2]}</em></a>`).join('') +
  `<hr><a class="sub${mng ? ' on' : ''}" data-mng>${APP.ic('set')}管理<em>${APP.ic('chevr')}</em></a>` +
  (mng ? `<div class="ctx side"><div class="ctx-h"><span>管理 · 会改动服务器上的条目</span></div><a>${APP.ic('replay')}刷新元数据</a><a>${APP.ic('info')}编辑信息</a><a>${APP.ic('layers')}识别 / 重新匹配</a><a>${APP.ic('dl')}下载字幕</a><hr>` +
    (del ? `<div class="del"><b>确定删除「${APP.esc(it.name)}」？</b><span>会从服务器删除媒体文件，无法撤销。</span><p><a class="btn sm line" data-delno>取消</a><a class="btn sm danger">删除文件</a></p></div>` : `<a class="danger" data-del>${APP.ic('x')}删除媒体…</a>`) + `</div>` : '');
APP.ctx = (x, y, it, mng, del) => {
  const c = APP.root.querySelector('.content'); c.querySelectorAll(':scope > .ctx').forEach((e) => e.remove()); if (!it) return;
  c.insertAdjacentHTML('beforeend', `<div class="ctx" style="left:${x}px;top:${y}px">${APP.ctxHtml(it, mng, del)}</div>`);
  const el = c.lastElementChild, r = el.getBoundingClientRect(); if (r.bottom > innerHeight - 8) el.style.top = Math.max(8, innerHeight - 8 - r.height) + 'px'; if (r.right + (mng ? r.width : 0) > innerWidth - 8) { el.style.left = Math.max(8, x - r.width) + 'px'; el.classList.add('flip'); }
  el.addEventListener('click', (e) => { e.stopPropagation(); if (e.target.closest('[data-mng]')) APP.ctx(x, y, it, !mng); else if (e.target.closest('[data-del]')) APP.ctx(x, y, it, true, true); else if (e.target.closest('[data-delno]')) APP.ctx(x, y, it, true); else if (e.target.closest('a')) APP.ctx(); });
};

// ------------------------------------------------------------------ behaviours shared by every screen
APP.mountMascots = (root) => root.querySelectorAll('.msc').forEach((h) => { if (h.__m) return;
  const m = h.__m = window.Mascot.create(h, { size: +h.dataset.size, hands: false, board: B(), mood: h.dataset.mood || 'idle' }); m.sayEl = h.dataset.say ? root.querySelector(h.dataset.say) : null; });
APP.mascot = (sel) => { const h = APP.root.querySelector(sel || '.msc'); return h && h.__m; };
APP.wire = (root) => {
  const mast = root.querySelector('.mast'), sc = root.querySelector('.scroll');
  // 付印新刊 (the desktop pull-to-refresh): click VOL. in the masthead or press F5 -> the issue number rolls to 319, the home cover returns to the first issue
  const reprint = () => { const v = mast && mast.querySelector('.vol'); if (!v || v.classList.contains('next')) return; v.classList.add('next'); if (root.__flow) root.__flow.go(0); if (sc) sc.scrollTo({ top: 0, behavior: 'smooth' });
    setTimeout(() => { const m = APP.mascot(); if (m) { m.setMood('cheer', 1000); m.say('新刊到了：VOL. 319，封面换过一轮。', 3200); } }, 500); };
  if (mast) { const v = mast.querySelector('.in span'); v.style.cursor = 'pointer'; v.title = '付印新刊（F5）'; v.addEventListener('click', reprint); window.addEventListener('keydown', (e) => { if (e.key === 'F5') { e.preventDefault(); reprint(); } }); }
  if (sc) { const t0 = root.querySelector('[data-t0]'); const at = t0 ? Math.max(4, t0.getBoundingClientRect().top + sc.scrollTop - mast.offsetHeight - 12) : 4;
    const f = () => { if (mast) mast.classList.toggle('solid', sc.scrollTop > at); APP.dirty(); }; sc.addEventListener('scroll', f, { passive: true }); f(); }
  root.querySelectorAll('.hs').forEach((h) => { h.addEventListener('scroll', APP.dirty, { passive: true }); h.addEventListener('wheel', (e) => { if (Math.abs(e.deltaY) > Math.abs(e.deltaX) && e.shiftKey) { h.scrollLeft += e.deltaY; e.preventDefault(); } }, { passive: false }); });
  // hover: the poster leans towards the pointer
  root.addEventListener('pointermove', (e) => { const pc = e.target.closest && e.target.closest('.pc,.tiltable'); if (root.__pc && root.__pc !== pc) { root.__pc.querySelector('.po').style.transform = ''; root.__pc = null; }
    if (!pc) return; const po = pc.querySelector('.po'), r = po.getBoundingClientRect(), x = clamp((e.clientX - r.left) / r.width - 0.5, -0.5, 0.5), y = clamp((e.clientY - r.top) / r.height - 0.5, -0.5, 0.5);
    po.style.transform = `rotateY(${(x * 14).toFixed(2)}deg) rotateX(${(-y * 10).toFixed(2)}deg) translateZ(1rem)`; root.__pc = pc; });
  root.addEventListener('pointerover', (e) => { const pc = e.target.closest && e.target.closest('.pc[data-ctx]'); if (pc && pc !== root.__sp) { root.__sp = pc; const it = ITEM[pc.dataset.ctx]; if (it && APP.poster(it)) APP.spot(APP.poster(it)).then((c) => pc.style.setProperty('--spot', c)); } });
  root.addEventListener('click', (e) => {
    APP.ctx();
    const menu = root.querySelector('#g-menu'); if (menu && menu.classList.contains('open') && !e.target.closest('#g-menu,#g-me')) { menu.classList.remove('open'); root.classList.remove('panel-open'); B() && B().kick(900); }
    const nav = e.target.closest('[data-nav]'); if (nav) { APP.nav(nav.dataset.nav); return; }
    if (e.target.closest('#g-back')) { if (history.length > 1) history.back(); else APP.nav('home'); return; }
    if (e.target.closest('#g-search')) { APP.nav('search'); return; }
    const more = e.target.closest('[data-more]'); if (more) { const h = more.closest('[data-ctx]'), r = more.getBoundingClientRect(); APP.ctx(r.left, r.bottom + 6, ITEM[h.dataset.ctx]); e.preventDefault(); e.stopPropagation(); return; }
    const fav = e.target.closest('[data-fav]');
    if (fav) { const box = fav.closest('.favbox') || fav, on = !box.classList.contains('faved'); box.classList.toggle('faved', on); box.querySelectorAll('.slot').forEach((s) => s.classList.toggle('on', on));
      box.querySelectorAll('.ft').forEach((t) => { t.textContent = on ? '已收藏' : '收藏'; }); const m = APP.mascot(); if (m && !fav.closest('.pc')) { if (on) { m.setMood('cheer', 900); m.say('盖章了：这一期归进你的收藏。', 2600); } else m.press(); } APP.anim(900); e.preventDefault(); e.stopPropagation(); return; }
    const mk = e.target.closest('.mk'); if (mk) { const host = mk.closest('.pc,.li,.epc') || mk; (host === mk ? mk : host).classList.toggle(host === mk ? 'on' : 'seen'); if (host !== mk) mk.classList.remove('on'); const m = APP.mascot(); if (m) { m.lookAtEl(mk); m.blink(); setTimeout(() => m.lookAt(null), 1200); } e.preventDefault(); e.stopPropagation(); return; }
    const go = e.target.closest('[data-go]'); if (go && !e.target.closest('.qa')) { const [p, id] = go.dataset.go.split(':'); APP.nav(p + (id ? '&id=' + id : '')); }
  });
  root.addEventListener('contextmenu', (e) => { const h = e.target.closest('[data-ctx]'); if (!h) return; e.preventDefault(); APP.ctx(e.clientX, e.clientY, ITEM[h.dataset.ctx]); });
  const tabs = root.querySelector('#g-tabs'); if (tabs) tabs.addEventListener('select', (e) => { const t = TABS[e.detail]; if (t[1] !== APP.tabScreen) setTimeout(() => APP.nav(t[1]), 380); });
  window.addEventListener('keydown', (e) => { if (e.key === 'Tab' || e.key.startsWith('Arrow')) document.body.classList.add('kb');
    if ((e.ctrlKey && e.key.toLowerCase() === 'k') || (e.key === '/' && !e.target.closest('input'))) { e.preventDefault(); APP.nav('search'); }
    if (e.ctrlKey && e.key === ',') { e.preventDefault(); APP.nav('settings'); } if (e.key === 'Escape') APP.ctx(); });
  window.addEventListener('pointerdown', () => document.body.classList.remove('kb'));
};

// ------------------------------------------------------------------ boot
APP.boot = async function () {
  const S = APP.S[APP.p] || APP.S.home, fail = (m) => { document.body.classList.add('nogl'); console.warn('glass off:', m); };
  if (APP.shot) document.body.classList.add('shot');
  await APP.need(['mascot_body', 'mascot_head', 'mascot_wingl', 'mascot_wingr', 'mascot_backl', 'mascot_backr', 'mascot_irisl', 'mascot_irisr', 'mascot_blink', 'mascot_surprise']);
  await window.Mascot.ready(); IMG.msc_still = window.Mascot.snapshot(256);
  { // the shared masthead emblem (fable-b1/shared-masthead.md): the hood's penguin head cut out of the brand picture as a circle
    const b = await APP.imgOf('brand'), n = b.naturalWidth, c = document.createElement('canvas'); c.width = c.height = 132; const x = c.getContext('2d'); x.beginPath(); x.arc(66, 66, 66, 0, 7); x.clip();
    x.drawImage(b, n * (0.4865 - 0.112), n * (0.218 - 0.112), n * 0.224, n * 0.224, 0, 0, 132, 132); IMG.emblem = c.toDataURL('image/png'); }
  const gc = document.createElement('canvas'); gc.width = gc.height = 180; { const x = gc.getContext('2d'), d = x.createImageData(180, 180); for (let i = 0; i < d.data.length; i += 4) { const v = 128 + (Math.random() - 0.5) * 90; d.data[i] = d.data[i + 1] = d.data[i + 2] = v; d.data[i + 3] = 60; } x.putImageData(d, 0, 0); }
  const o = S.build(APP.st, Q) || {}; APP.tabScreen = o.tab != null ? TABS[o.tab][1] : null;
  document.body.insertAdjacentHTML('beforeend', `<div class="board sB s-${APP.p}${o.hero ? ' hero-on' : ''} ${o.cls || ''}" id="bd" data-dpr="1"><div class="content">${o.html}${o.bare ? '' : APP.mast(!o.hero && o.solid)}</div><canvas class="gl"></canvas><div class="chrome">${o.bare || o.noglass ? (o.extra || '') : APP.chrome(o)}</div><div class="fx"></div></div>`);
  const root = APP.root = document.getElementById('bd');
  await APP.fill(root);
  if (document.fonts && document.fonts.ready) await document.fonts.ready;
  try {
    APP.mountMascots(root);
    if (S.init) await S.init(root, APP.st, Q);
    await APP.fill(root);
    await window.G2.loadImages();
    if (!document.createElement('canvas').getContext('webgl2')) fail('no WebGL2'); else { window.G2.init(); APP.glassUp = true; root.querySelectorAll('.msc').forEach((h) => { if (h.__m) h.__m.board = B(); }); }
    root.querySelector('.fx').style.backgroundImage = `url(${gc.toDataURL('image/png')})`;
    APP.wire(root);
    if (B()) B().hooks.push(() => { if (performance.now() < (B().animUntil || 0)) B().sceneDirty = true; });
    const sc = +Q.get('scroll'); if (sc) { const el = root.querySelector('.scroll'); el.scrollTop = sc; el.dispatchEvent(new Event('scroll')); }
    if (S.after) await S.after(root, APP.st, Q);
    await Promise.all([...document.images].map((im) => im.decode ? im.decode().catch(() => 0) : 0));
    window.G2.renderAll(); const bad = window.G2.boards.filter((b) => b.error); if (bad.length) fail(bad[0].error);
  } catch (e) { fail(String(e.stack || e)); console.error(e); document.body.insertAdjacentHTML('beforeend', `<pre class="err">${APP.esc(e.stack || e)}</pre>`); }
  let rs; addEventListener('resize', () => { clearTimeout(rs); rs = setTimeout(() => location.reload(), 250); });
  requestAnimationFrame(() => requestAnimationFrame(() => { window.G2.boards.forEach((b) => { b.sceneDirty = true; }); window.G2.renderAll(); document.body.dataset.ready = '1'; }));
};
window.addEventListener('error', (e) => { document.body.insertAdjacentHTML('beforeend', `<pre class="err">${e.message} @${(e.filename || '').split('/').pop()}:${e.lineno}</pre>`); });
})();
