/*
 * 咕咕嘎嘎 · B1「头版」· 电视端 —— shell.
 *   index.html                 gallery: every screen / state is an <iframe> of this same page (loaded when it scrolls into view)
 *   index.html?p=<screen>[&st=<state>][&shot=1]   one 1920 x 1080 screen, scaled to the window. Arrow keys / Enter / Backspace = the remote.
 * The glass (side rail, its panel, the player's floating button) is engine.js + core.js + glass.js, copied unmodified from ../../fable-huabao/js.
 * Pictures: one script per picture in assets/ (data: URLs, see tools/build-assets.mjs); a screen loads only what it shows.
 */
(function () {
'use strict';
const Q = new URLSearchParams(location.search);
const APP = window.APP = { p: Q.get('p'), st: Q.get('st') || '', shot: !!Q.get('shot'), Q, SCREENS: {}, GROUPS: [], BASE: {}, PLAYS: [] };
const IMG = window.IMG;
const clamp = APP.clamp = (v, a, b) => Math.max(a, Math.min(b, v));
APP.pad = (n) => ('0' + n).slice(-2);
APP.key = (p) => p ? p.replace(/^img\//, '').replace(/\.\w+$/, '') : null;
APP.ic = (n, s) => `<svg class="ic"${s ? ` style="${s}"` : ''}><use href="#i-${n}"/></svg>`;
APP.im = (k, c, s) => k ? `<img class="${c || ''}" data-img="${APP.key(k)}"${s ? ` style="${s}"` : ''} alt="" draggable="false">` : '';
APP.esc = (s) => String(s == null ? '' : s).replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

// ------------------------------------------------------------------ pictures on demand
const asked = {};
APP.need = (keys) => Promise.all([...new Set(keys)].filter(Boolean).map((k) => asked[k] || (asked[k] = new Promise((res) => {
  const done = () => { const im = new Image(); im.onload = im.onerror = () => res(); im.src = IMG[k] || ''; APP.BASE[k] = im; };
  if (IMG[k]) return done();
  const s = document.createElement('script'); s.src = 'assets/' + k + '.js'; s.onload = done; s.onerror = () => { console.warn('missing picture', k); res(); }; document.head.appendChild(s);
}))));
APP.keysIn = (root) => { const ks = []; root.querySelectorAll('[data-img]').forEach((e) => ks.push(e.dataset.img)); root.querySelectorAll('[data-pre]').forEach((e) => ks.push(...e.dataset.pre.split(',').filter(Boolean))); if (root.querySelector('.msc')) ks.push(...MSC); return ks; };
const MSC = ['mascot_body', 'mascot_head', 'mascot_wingl', 'mascot_wingr', 'mascot_backl', 'mascot_backr', 'mascot_irisl', 'mascot_irisr', 'mascot_blink', 'mascot_surprise'];
APP.fill = (root) => root.querySelectorAll('img[data-img]').forEach((im) => { const s = IMG[im.dataset.img]; if (s && im.__k !== im.dataset.img) { im.__k = im.dataset.img; im.src = s; } });
APP.setImg = async (el, k) => { if (!el) return; k = APP.key(k); if (el.dataset.img === k) return; el.dataset.img = k || ''; if (!k) { el.removeAttribute('src'); return; } const had = !!APP.BASE[k]; await APP.need([k]); if (el.dataset.img !== k) return; el.__k = k; el.src = IMG[k] || ''; if (!had && window.G2 && APP.glOK) { await window.G2.loadImages(); } APP.dirty(600); };
APP.derive = (key, w, h, draw, type) => { const c = document.createElement('canvas'); c.width = w; c.height = h; draw(c.getContext('2d'), w, h); IMG[key] = c.toDataURL(type || 'image/jpeg', 0.86); const im = new Image(); im.src = IMG[key]; APP.BASE[key] = im; asked[key] = Promise.resolve(); return key; };
APP.blurOf = (src) => { const key = 'blur_' + src; if (IMG[key]) return key; const im = APP.BASE[src]; if (!im || !im.naturalWidth) return null;
  return APP.derive(key, 480, 270, (x, w, h) => { x.fillStyle = '#100E0B'; x.fillRect(0, 0, w, h); x.filter = 'blur(22px) saturate(1.15) brightness(.8)'; const k = Math.max(w / im.naturalWidth, h / im.naturalHeight) * 1.3, dw = im.naturalWidth * k, dh = im.naturalHeight * k; x.drawImage(im, (w - dw) / 2, (h - dh) * 0.3, dw, dh); }); };

// ------------------------------------------------------------------ 专色: the strongest hue of a poster, pushed to a lightness / saturation that reads on the ink-black paper
const PAL = {};
APP.palette = (src) => {
  if (PAL[src]) return PAL[src]; const im = APP.BASE[src]; if (!im || !im.naturalWidth) return null;
  const c = document.createElement('canvas'); c.width = c.height = 24; const x = c.getContext('2d', { willReadFrequently: true }); x.drawImage(im, 0, 0, 24, 24);
  const d = x.getImageData(0, 0, 24, 24).data, bins = []; for (let i = 0; i < 12; i++) bins.push({ w: 0, r: 0, g: 0, b: 0 }); let R = 0, G = 0, B = 0;
  for (let i = 0; i < d.length; i += 4) { const r = d[i], g = d[i + 1], b = d[i + 2], mx = Math.max(r, g, b), mn = Math.min(r, g, b), s = mx ? (mx - mn) / mx : 0, v = mx / 255; R += r; G += g; B += b;
    let h = 0; if (mx !== mn) { h = mx === r ? (g - b) / (mx - mn) : mx === g ? 2 + (b - r) / (mx - mn) : 4 + (r - g) / (mx - mn); h = (h * 60 + 360) % 360; }
    const wgt = s * s * (0.25 + v), bn = bins[Math.floor(h / 30) % 12]; bn.w += wgt; bn.r += r * wgt; bn.g += g * wgt; bn.b += b * wgt; }
  const n = d.length / 4, top = bins.slice().sort((a, b) => b.w - a.w)[0];
  return (PAL[src] = { avg: [R / n, G / n, B / n], vib: top.w ? [top.r / top.w, top.g / top.w, top.b / top.w] : [R / n, G / n, B / n], sat: top.w / n });
};
APP.tone = (c, light, sat) => { const mx = Math.max(...c), mn = Math.min(...c), mid = (mx + mn) / 2 || 1; let o = c.map((v) => mid + (v - mid) * sat); const l = (Math.max(...o) + Math.min(...o)) / 2 || 1, k = (light * 255) / l; return o.map((v) => Math.round(clamp(v * k, 0, 255))); };
APP.spotOf = (k) => { const p = APP.palette(APP.key(k)); if (!p || p.sat < 0.012) return '#EA4E30'; const c = APP.tone(p.vib, 0.62, 1.3); return `rgb(${c.join(',')})`; };   // grey posters fall back to 朱红
// a clearlogo drawn in dark strokes disappears on the dark paper: measure it; dark ones get a cream slip pasted under them (.onpaper)
const LUM = {};
APP.logoDark = (k) => { if (k in LUM) return LUM[k]; const im = APP.BASE[k]; if (!im || !im.naturalWidth) return false; const c = document.createElement('canvas'); c.width = 48; c.height = 24; const x = c.getContext('2d', { willReadFrequently: true }); x.drawImage(im, 0, 0, 48, 24);
  const d = x.getImageData(0, 0, 48, 24).data; let s = 0, n = 0, dark = 0; for (let i = 0; i < d.length; i += 4) { const a = d[i + 3] / 255; if (a < 0.3) continue; const l = Math.max(d[i], d[i + 1], d[i + 2]) / 255; s += l * a; n += a; if (l < 0.26) dark += a; }
  return (LUM[k] = n > 0 && dark / n > 0.36); };
APP.bright = (k) => { const p = APP.palette(k); return !!p && (p.avg[0] + p.avg[1] + p.avg[2]) / 765 > 0.46; };
APP.fixLogos = (root) => { root.querySelectorAll('.dt-bd .bd[data-img]').forEach((im) => im.parentElement.classList.toggle('bright', APP.bright(im.dataset.img))); return APP.fixLogos2(root); };
APP.fixLogos2 = (root) => root.querySelectorAll('img.logo[data-img]').forEach((im) => { const w = im.closest('.logo-w') || im; w.classList.toggle('onpaper', APP.logoDark(im.dataset.img)); });

// ------------------------------------------------------------------ glass refresh
APP.board = () => { const b = document.querySelector('.board'); return b && b.__board; };
APP.dirty = (ms) => { const B = APP.board(); if (B) { B.sceneDirty = true; B.animUntil = performance.now() + (ms || 0); B.kick((ms || 0) + 120); } };
APP.spring = (fn, k, z) => {
  const s = { x: 0, v: 0, t: 0, raf: 0 }; k = k || 170; z = z || 0.82; let last = 0;
  const step = (now) => { s.raf = 0; const dt = Math.max(0.001, Math.min(0.04, (now - last) / 1000 || 0.016)); last = now; const n = 4, h = dt / n;
    for (let i = 0; i < n; i++) { s.v += (-k * (s.x - s.t) - 2 * z * Math.sqrt(k) * s.v) * h; s.x += s.v * h; }
    if (Math.abs(s.x - s.t) < 0.001 && Math.abs(s.v) < 0.01) { s.x = s.t; s.v = 0; fn(s.x); return; } fn(s.x); s.raf = requestAnimationFrame(step); };
  s.to = (t) => { s.t = t; if (APP.shot) { s.x = t; s.v = 0; fn(t); return; } if (!s.raf) { last = performance.now(); s.raf = requestAnimationFrame(step); } };
  s.set = (x) => { s.x = s.t = x; s.v = 0; fn(x); };
  return s;
};

// ------------------------------------------------------------------ her
APP.msc = (size, extra) => `<i class="msc" data-size="${size}" ${extra || ''}></i>`;
APP.mountMascots = (root) => root.querySelectorAll('.msc').forEach((h) => { if (h.__m) return; h.__m = window.Mascot.create(h, { size: Math.round(+h.dataset.size * 1.42), hands: false, press: false, mood: h.dataset.mood || 'idle' }); });
APP.mascot = (root, sel) => { const h = root.querySelector(sel || '.msc'); return h && h.__m; };

// ------------------------------------------------------------------ focus: the remote. [data-f] = focusable; [data-fg] = a group that remembers its last focus (a row); .scope.open = a layer that traps focus
const F = APP.F = { cur: null };
const vis = (el) => el.getClientRects().length > 0 && getComputedStyle(el).visibility !== 'hidden';
F.scope = () => { const s = [...document.querySelectorAll('.scope.open')]; return s[s.length - 1] || document.querySelector('.board'); };
F.list = () => { const sc = F.scope(); return [...sc.querySelectorAll('[data-f]')].filter((e) => vis(e) && (sc.classList.contains('scope') || !e.closest('.scope:not(.open)'))); };
F.set = (el, o) => {
  if (!el) return false; const old = F.cur; if (old && old !== el) old.classList.remove('fc');
  F.cur = el; el.classList.add('fc'); const g = el.closest('[data-fg]'); if (g) g.__last = el;
  APP.reveal(el, o && o.instant);
  if (APP.S && APP.S.onFocus) APP.S.onFocus(APP.root, el, old);
  APP.dirty(700); return true;
};
F.first = () => { const l = F.list(); return F.set(l.find((e) => e.dataset.f0 !== undefined) || l[0], { instant: true }); };
F.move = (dir) => {
  const cur = F.cur; if (!cur || !cur.isConnected || !vis(cur)) return F.first();
  const o = cur.dataset[dir]; if (o) { if (o === 'none') return true; const t = document.querySelector(o); if (t && vis(t)) return F.set(t); }
  const bd = document.querySelector('.board').getBoundingClientRect(), sc = bd.width / 1920;
  const R = (e) => { const r = e.getBoundingClientRect(); const x0 = (r.left - bd.left) / sc, y0 = (r.top - bd.top) / sc; return { l: x0, t: y0, r: x0 + r.width / sc, b: y0 + r.height / sc, cx: x0 + r.width / sc / 2, cy: y0 + r.height / sc / 2 }; };
  const a = R(cur), H = dir === 'left' || dir === 'right'; let best = null, bs = 1e9;
  for (const el of F.list()) {
    if (el === cur) continue; const b = R(el); let prim, sec, ov;
    if (dir === 'right') { if (b.cx <= a.cx + 2 || b.l < a.r - 12) continue; prim = b.l - a.r; } else if (dir === 'left') { if (b.cx >= a.cx - 2 || b.r > a.l + 12) continue; prim = a.l - b.r; }
    else if (dir === 'down') { if (b.cy <= a.cy + 2 || b.t < a.b - 12) continue; prim = b.t - a.b; } else { if (b.cy >= a.cy - 2 || b.b > a.t + 12) continue; prim = a.t - b.b; }
    if (H) { ov = Math.min(a.b, b.b) - Math.max(a.t, b.t); sec = Math.abs(a.cy - b.cy); } else { ov = Math.min(a.r, b.r) - Math.max(a.l, b.l); sec = Math.abs(clamp(a.cx, 0, 1920) - clamp(b.cx, 0, 1920)); }
    const same = el.closest('[data-fg]') === cur.closest('[data-fg]') && cur.closest('[data-fg]');
    const s = Math.max(0, prim) + (ov > 0 ? sec * (H ? 0.4 : 0.25) : sec * 3 + 200) - (same && H ? 400 : 0);
    if (s < bs) { bs = s; best = el; }
  }
  if (!best) return false;
  const g = best.closest('[data-fg]');                                 // entering another row: go back to where that row was left
  if (g && g !== cur.closest('[data-fg]')) { const m = g.__last && g.__last.isConnected && vis(g.__last) ? g.__last : (g.querySelector('[data-f].on') || (g.dataset.fg === 'first' ? g.querySelector('[data-f]') : null)); if (m && vis(m)) best = m; }
  return F.set(best);
};
// keep the focus in view: rows scroll sideways (the focused card rests at the row's left pad), pages snap by section
APP.reveal = (el, instant) => {
  const beh = APP.shot || instant ? 'instant' : 'smooth';
  const hs = el.closest('.hs'); if (hs) { const pad = +(hs.dataset.pad || 0), er = el.getBoundingClientRect(), hr = hs.getBoundingClientRect(), k = hr.width / hs.offsetWidth || 1; const x = (er.left - hr.left) / k + hs.scrollLeft;
    let t = hs.scrollLeft; if (hs.dataset.lead !== undefined) t = x - pad; else { const w = er.width / k; if (x - pad < hs.scrollLeft) t = x - pad; else if (x + w + pad > hs.scrollLeft + hs.clientWidth) t = x + w + pad - hs.clientWidth; }
    t = clamp(t, 0, hs.scrollWidth - hs.clientWidth); hs.scrollTo({ left: t, behavior: beh });
    if (hs.dataset.lead !== undefined) for (const c of hs.children) c.classList.toggle('past', c !== el && c.offsetLeft - hs.offsetLeft < t + pad - 4); }
  const vs = el.closest('.vs'); if (vs) { const sn = el.closest('[data-snap]'); const vr = vs.getBoundingClientRect(), k = vr.width / vs.offsetWidth || 1; let t = vs.scrollTop;
    if (vs.dataset.rs !== undefined) t = (el.getBoundingClientRect().top - vr.top) / k + vs.scrollTop - +vs.dataset.rs;
    else if (sn) t = (sn.getBoundingClientRect().top - vr.top) / k + vs.scrollTop - +(sn.dataset.snap || 0);
    else { const er = el.getBoundingClientRect(), y = (er.top - vr.top) / k + vs.scrollTop, h = er.height / k, m = +(vs.dataset.m || 60); if (y - m < vs.scrollTop) t = y - m; else if (y + h + m > vs.scrollTop + vs.clientHeight) t = y + h + m - vs.clientHeight; }
    vs.scrollTo({ top: clamp(t, 0, vs.scrollHeight - vs.clientHeight), behavior: beh }); }
};

// the fixed side rail (glass). Any page: 左 at the left edge opens it.
APP.NAV = [['search', 'search', '搜索'], ['home', 'home', '首页'], ['discover', 'compass', '发现'], ['lib', 'grid', '片库'], ['coll', 'layers', '合集'], ['me', 'hist', '记录'], ['fav', 'heart', '收藏'], ['settings', 'set', '设置']];
APP.rail = (on, open) => {
  const n = APP.NAV.length, h = n * 66 + 20, top = Math.round((1080 - h) / 2);
  return `<div class="g rail" id="rail" data-mat="bar" style="left:28px;top:${top}px;width:76px;height:${h}px">${APP.NAV.map((x) => `<span class="rc${x[0] === on ? ' on' : ''}">${APP.ic(x[1])}<i></i></span>`).join('')}</div>` +
    `<div class="g navp scope${open ? ' open' : ''}" id="navp" data-from="#rail" data-mat="panel" style="left:28px;top:${top - 112}px;width:404px;height:${h + 224}px">` +
    `<div class="np-h">${APP.emblem(44)}<b>咕咕嘎嘎</b></div>` +
    `<div class="np-ln" style="margin-top:0"></div><div class="np-l" data-fg>${APP.NAV.map((x, i) => `<a class="ni${x[0] === on ? ' on' : ''}" data-f ${x[0] === on ? 'data-f0' : ''} data-nav="${x[0]}"><em>${APP.pad(i + 1)}</em>${APP.ic(x[1])}<b>${x[2]}</b><i></i></a>`).join('')}</div>` +
    `<div class="np-ln"></div><div class="np-u"><span>读者 · 影迷</span><span>家里的片库</span></div></div>`;
};
// the small penguin mark of the masthead (drawn after the hood on the brand picture: dark hood, two eyes, yellow beak)
APP.emblem = (s) => `<i class="emb" style="width:${s}px;height:${s}px"></i>`;   // the shared masthead mark: the hood's penguin head, a round crop of the brand picture (22.4 % of its width, centre 48.65 % / 21.8 %)
APP.navOpen = (on) => { const p = document.getElementById('navp'); if (!p) return false; if (on === p.classList.contains('open')) return true; p.classList.toggle('open', on); APP.root.classList.toggle('nav-open', on);
  if (on) { APP.__back = F.cur; if (F.cur) F.cur.classList.remove('fc'); F.cur = null; F.first(); } else { const b = APP.__back; F.cur && F.cur.classList.remove('fc'); F.cur = null; if (b && b.isConnected) F.set(b); else F.first(); } APP.dirty(900); return true; };

// ------------------------------------------------------------------ one screen
APP.toast = (t) => { let e = APP.root.querySelector('.toast'); if (!e) { e = document.createElement('div'); e.className = 'toast'; APP.root.querySelector('.content').appendChild(e); } e.innerHTML = t; e.classList.add('on'); clearTimeout(e.__t); e.__t = setTimeout(() => e.classList.remove('on'), 2400); APP.dirty(300); };
APP.show = async (st, keepHist) => {
  const S = APP.S, root = APP.root, content = root.querySelector('.content'), chrome = root.querySelector('.chrome');
  if (!keepHist && APP.st !== st) (APP.hist = APP.hist || []).push(APP.st);
  APP.st = st; F.cur = null; if (S.leave) S.leave(root);
  root.className = `board tv sB s-${APP.p} st-${st || 'base'}`; root.style.removeProperty('--spot');
  const h = S.render(st); content.innerHTML = (typeof h === 'string' ? h : h.content) + '<div class="scrim"></div><div class="toast"></div>';
  const navOpen = st === 'nav'; root.classList.toggle('nav-open', navOpen);
  chrome.innerHTML = (S.rail === false ? '' : APP.rail(S.nav || APP.p, navOpen)) + ((h && h.chrome) || '');
  await APP.need(APP.keysIn(root)); APP.fill(root);
  if (window.G2 && APP.glOK) await window.G2.loadImages();
  await Promise.all([...root.querySelectorAll('img')].map((im) => im.src && im.decode ? im.decode().catch(() => 0) : 0));
  if (root.querySelector('.msc')) { await window.Mascot.ready(); APP.mountMascots(root); }
  APP.fixLogos(root);
  if (S.init) await S.init(root, st);
  APP.fill(root);
  F.first(); APP.dirty(600);
};
APP.go = (st) => APP.show(st);
APP.back = () => {
  const S = APP.S; if (document.getElementById('navp') && document.getElementById('navp').classList.contains('open')) { if (APP.st === 'nav') return APP.show(''); return APP.navOpen(false); }
  if (S.back && S.back(APP.root, APP.st)) return;
  if (APP.hist && APP.hist.length) return APP.show(APP.hist.pop(), true);
  if (APP.st) return APP.show('', true);
  APP.toast(APP.p === 'home' ? '再按一次返回键退出' : '返回上一页（演示里每个画板是独立的一页）');
};
const onKey = (k, long) => {
  const S = APP.S, root = APP.root; if (!S) return;
  if (S.key && S.key(root, k, long) === true) return;
  if (k === 'back') return APP.back();
  if (k === 'menu') { const c = F.cur; if (c && c.dataset.menu) return APP.go(c.dataset.menu); if (S.menu) return APP.go(S.menu); return; }
  if (k === 'ok') { const c = F.cur; if (!c) return; if (c.dataset.nav) { if (c.dataset.nav === (S.nav || APP.p)) return APP.back(); location.search = '?p=' + ({ fav: 'me&st=fav' }[c.dataset.nav] || c.dataset.nav) + (Q.get('embed') ? '&embed=1' : ''); return; }
    if (c.dataset.go !== undefined) return APP.go(c.dataset.go); if (S.ok) S.ok(root, c); return; }
  if (!F.move(k) && k === 'left' && document.getElementById('rail')) APP.navOpen(true);
};
APP.bootScreen = async () => {
  const S = APP.S = APP.SCREENS[APP.p]; document.body.classList.add('one'); if (APP.shot) document.body.classList.add('shot');
  document.body.insertAdjacentHTML('beforeend', `<div id="stage"><div class="board tv sB" id="board" data-dpr="1"><div class="content"></div><canvas class="gl"></canvas><div class="chrome"></div><div class="fx"></div></div></div>`);
  const root = APP.root = document.getElementById('board');
  const fit = () => { const s = Math.min(innerWidth / 1920, innerHeight / 1080); root.style.transform = `scale(${s})`; document.getElementById('stage').style.cssText = `width:${1920 * s}px;height:${1080 * s}px`; };
  fit(); addEventListener('resize', () => { fit(); APP.dirty(100); });
  APP.derive('grain', 180, 180, (x, w, h) => { const d = x.createImageData(w, h); for (let i = 0; i < d.data.length; i += 4) { const v = 128 + (Math.random() - 0.5) * 90; d.data[i] = d.data[i + 1] = d.data[i + 2] = v; d.data[i + 3] = 60; } x.putImageData(d, 0, 0); }, 'image/png');
  root.querySelector('.fx').style.backgroundImage = `url(${IMG.grain})`; delete IMG.grain;
  if (document.fonts && document.fonts.ready) await document.fonts.ready;
  try { const t = document.createElement('canvas'); if (!t.getContext('webgl2')) throw new Error('no webgl2'); await window.G2.loadImages(); window.G2.init(); APP.glOK = true;
    const B = root.__board; B.hooks.push(() => { if (performance.now() < (B.animUntil || 0)) B.sceneDirty = true; });
  } catch (e) { document.body.classList.add('nogl'); console.error(e); }
  root.addEventListener('scroll', () => APP.dirty(200), { capture: true, passive: true });
  await APP.show(APP.st, true);
  // the remote: arrows, Enter (hold = 长按确定), Backspace / Esc = 返回, M = 菜单键
  let held = null, fired = false;
  addEventListener('keydown', (e) => {
    const m = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down', Backspace: 'back', Escape: 'back', m: 'menu', M: 'menu', ContextMenu: 'menu' }[e.key];
    if (e.key === 'Enter') { e.preventDefault(); if (e.repeat) return; fired = false; held = setTimeout(() => { fired = true; onKey('menu', true); }, 520); return; }
    if (m) { e.preventDefault(); onKey(m, e.repeat); }
  });
  addEventListener('keyup', (e) => { if (e.key === 'Enter') { clearTimeout(held); if (!fired) onKey('ok'); } if (APP.S.keyup) APP.S.keyup(APP.root, e.key); });
  root.addEventListener('click', (e) => { const f = e.target.closest('[data-f]'); if (f) { if (F.cur === f) onKey('ok'); else F.set(f); } });   // a mouse can point too (for the demo only)
  const B = root.__board; if (B) { B.sceneDirty = true; try { B.render(); } catch (e) { console.error(e); } if (B.error) document.body.classList.add('nogl'); }
  requestAnimationFrame(() => requestAnimationFrame(() => { APP.dirty(300); if (B) { B.sceneDirty = true; try { B.render(); } catch (e) { /* shown as nogl */ } } document.body.dataset.ready = '1'; }));
};

// ------------------------------------------------------------------ gallery
APP.bootGallery = () => {
  document.body.classList.add('gal');
  const ids = Object.keys(APP.SCREENS); let n = 0, m = 0;
  const toc = APP.GROUPS.map((g) => `<h3>${g[0]}</h3>` + g[1].map((id) => { const s = APP.SCREENS[id]; n++; m += s.states.length; return `<a href="#f-${id}"><em>${APP.pad(n)}</em>${s.title}<i>${s.states.length}</i></a>`; }).join('')).join('');
  document.body.insertAdjacentHTML('beforeend', `<aside><h1>${APP.emblem(34)}咕咕嘎嘎<small>B1「头版」· 电视端（1920 × 1080，遥控器）。点一个画板让它获得键盘，然后：方向键 = 遥控器方向键，Enter = 确定（按住 = 长按确定），Backspace = 返回，M = 菜单键。固定的侧栏和播放器里的悬浮按钮是液态玻璃引擎实时渲染的。</small></h1>
    <p class="cnt">${n} 个画板 · ${m} 个状态</p><h2>CONTENTS　界面</h2><nav id="toc">${toc}</nav><h2>PLAY　可以玩的</h2><ul id="plays">${APP.PLAYS.map((p) => `<li><b>${p[0]}</b>${p[1]}</li>`).join('')}</ul></aside>
    <main id="main">${APP.GROUPS.map((g) => `<h2 class="grp">${g[0]}</h2>` + g[1].map((id) => { const s = APP.SCREENS[id];
      return `<figure id="f-${id}"><figcaption><b>${s.title}</b><span>${s.note || ''}</span><a class="open" target="_blank" href="?p=${id}">单独打开 ↗</a></figcaption><div class="fit"><iframe data-p="${id}" data-st="${s.states[0][0]}" tabindex="0"></iframe></div>` +
        `<nav class="states">${s.states.map((x, i) => `<a data-st="${x[0]}" class="${i === 0 ? 'on' : ''}">${x[1]}</a>`).join('')}</nav><p class="path">${s.path || ''}</p></figure>`; }).join('')).join('')}</main>`);
  const main = document.getElementById('main');
  const fit = () => document.querySelectorAll('.fit').forEach((f) => { const s = Math.min(1, f.clientWidth / 1920); f.style.height = 1080 * s + 'px'; f.firstElementChild.style.transform = `scale(${s})`; });
  fit(); addEventListener('resize', fit);
  const src = (f) => `index.html?p=${f.dataset.p}&st=${f.dataset.st}&embed=1`;
  const io = new IntersectionObserver((es) => es.forEach((e) => { const f = e.target.querySelector('iframe'); if (e.isIntersecting) { if (!f.__on) { f.__on = true; f.src = src(f); } } else if (f.__on && Math.abs(e.boundingClientRect.top) > innerHeight * 2.5) { f.__on = false; f.src = 'about:blank'; } }), { root: null, rootMargin: '60% 0px', threshold: [0, 0.01] });
  document.querySelectorAll('figure').forEach((f) => io.observe(f));
  main.addEventListener('click', (e) => { const a = e.target.closest('.states a'); if (!a) return; const fig = a.closest('figure'), f = fig.querySelector('iframe'); fig.querySelectorAll('.states a').forEach((x) => x.classList.toggle('on', x === a)); f.dataset.st = a.dataset.st; f.__on = true; f.src = src(f); fig.querySelector('.open').href = `?p=${f.dataset.p}&st=${f.dataset.st}`; });
};
APP.boot = () => (APP.p && APP.SCREENS[APP.p] ? APP.bootScreen() : APP.bootGallery());
window.addEventListener('error', (e) => { document.body.insertAdjacentHTML('beforeend', `<pre style="position:fixed;left:0;top:0;z-index:99;background:#400;color:#fff;font-size:14px;padding:6px;white-space:pre-wrap">${e.message} @${(e.filename || '').split('/').pop()}:${e.lineno}</pre>`); });
})();
