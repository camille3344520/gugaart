/*
 * fable-b1 / phone: shell. B1「头版」落成手机全部界面，画板 412 x 905（1440 x 3168 @3.5），横屏播放器 905 x 412。
 * The glass (bottom tab bar + the round search button) is NOT drawn here: engine.js + core.js + glass.js are byte-for-byte copies.
 * A screen with glass is a ".board" (content / canvas.gl / chrome); the others are plain ".scr". Screens register in APP.S (js/s-*.js).
 *
 * URL: index.html                              gallery of every screen (state buttons under each phone)
 *      ?p=<screen>&shot=1[&st=<state>][&scroll=<px>]   one screen at 0,0 with no page chrome (screenshots)
 */
(function () {
'use strict';
const Q = new URLSearchParams(location.search);
const APP = window.APP = { p: Q.get('p'), shot: Q.get('shot') ? (Q.get('p') || 'home') : null, st: Q.get('st') || '', Q, S: {}, ORDER: [], BASE: {}, W: 412, H: 905 };
const IMG = window.IMG, DB = window.DB;
const clamp = APP.clamp = (v, a, b) => Math.max(a, Math.min(b, v));
APP.lerp = (a, b, t) => a + (b - a) * t;
APP.esc = (s) => String(s == null ? '' : s).replace(/[<>&]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;' }[c]));
APP.ic = (n, s) => `<svg class="ic"${s ? ` style="${s}"` : ''}><use href="#i-${n}"/></svg>`;
APP.im = (n, c, s) => `<img class="${c || ''}" data-img="${n || ''}"${s ? ` style="${s}"` : ''} alt="" draggable="false">`;
APP.status = () => `<div class="status"><b>21:47</b><span>${APP.ic('wifi')}<i class="batt"><u></u></i></span></div>`;
APP.setImg = (el, key) => { if (!el || el.dataset.img === key) return; el.dataset.img = key; el.src = IMG[key] || ''; };
APP.fill = (root) => root.querySelectorAll('img[data-img]').forEach((im) => { if (!im.getAttribute('src') && IMG[im.dataset.img]) im.src = IMG[im.dataset.img]; });
APP.pad = (n) => ('0' + n).slice(-2);
APP.reg = (id, group, s) => { s.id = id; s.group = group; APP.S[id] = s; APP.ORDER.push(id); };

// ------------------------------------------------------------------ real data (js/data.js = ../data/*.json, picture paths turned into IMG keys)
const X = DB.extra || {}, X2 = DB.extra2 || {};
APP.D = {}; DB.details.forEach((d) => { APP.D[d.name] = d; });
APP.ALL = {}; [...DB.details, ...Object.values(DB.latest).flat(), ...DB.continueWatching].forEach((t) => { if (!APP.ALL[t.id]) APP.ALL[t.id] = t; });
APP.ART = X2.continueSeriesArt || {};
APP.X = X; APP.X2 = X2;
APP.dur = (sec) => { if (!sec) return ''; const m = Math.round(sec / 60); return m >= 60 ? `${Math.floor(m / 60)} 小时 ${APP.pad(m % 60)} 分` : `${m} 分`; };
APP.clock = (sec) => { sec = Math.round(sec || 0); const h = Math.floor(sec / 3600), m = Math.floor(sec / 60) % 60, s = sec % 60; return (h ? h + ':' + APP.pad(m) : m) + ':' + APP.pad(s); };
APP.score = (r) => r == null ? '—' : (r >= 10 ? '10' : (Math.round(r * 10) / 10).toFixed(1));
APP.genre = (t, n) => (t.genres || []).filter((g) => !/字幕/.test(g)).map((g) => ({ Animation: '动画', Comedy: '喜剧', 'Sci-Fi & Fantasy': '科幻奇幻' }[g] || g)).slice(0, n || 2).join(' · ');
APP.kind = (t) => t.type === 'Movie' ? '电影' : (t.genres || []).some((g) => /动画|Animation/.test(g)) ? '动画' : '剧集';
APP.len = (t) => t.type === 'Movie' ? APP.dur(t.runtimeSec) : `全 ${(t.episodeCount || 0).toLocaleString('en')} 集`;
APP.num = (n) => Number(n).toLocaleString('en');

// ------------------------------------------------------------------ derived pictures (made once at load, registered in window.IMG so the glass refracts the same picture the page shows)
APP.loadBase = () => Promise.all(Object.keys(IMG).map((k) => new Promise((res) => { const im = new Image(); im.onload = im.onerror = () => res(); im.src = IMG[k]; APP.BASE[k] = im; })));
APP.derive = (key, w, h, draw, type) => { const c = document.createElement('canvas'); c.width = w; c.height = h; const x = c.getContext('2d'); draw(x, w, h); IMG[key] = c.toDataURL(type || 'image/jpeg', 0.88); const im = new Image(); im.src = IMG[key]; APP.BASE[key] = im; return key; };
APP.cover = (x, im, dx, dy, dw, dh, px, py) => {
  if (!im || !im.naturalWidth) return; const k = Math.max(dw / im.naturalWidth, dh / im.naturalHeight), w = im.naturalWidth * k, h = im.naturalHeight * k;
  x.drawImage(im, dx + (dw - w) * (px === undefined ? 0.5 : px), dy + (dh - h) * (py === undefined ? 0.5 : py), w, h);
};
APP.blurOf = (key, src, w, h, o) => APP.derive(key, w, h, (x) => {
  x.fillStyle = '#000'; x.fillRect(0, 0, w, h); const m = o.b * 3;
  x.filter = `blur(${o.b}px) saturate(${o.s || 1}) brightness(${o.l || 1})`; APP.cover(x, APP.BASE[src], -m, -m, w + 2 * m, h + 2 * m, o.px, o.py); x.filter = 'none';
});
APP.palette = (src) => {
  const c = document.createElement('canvas'); c.width = c.height = 24; const x = c.getContext('2d', { willReadFrequently: true }); if (!APP.BASE[src] || !APP.BASE[src].naturalWidth) return { vib: [234, 78, 48] }; x.drawImage(APP.BASE[src], 0, 0, 24, 24);
  const d = x.getImageData(0, 0, 24, 24).data, bins = []; for (let i = 0; i < 12; i++) bins.push({ w: 0, r: 0, g: 0, b: 0 });
  let R = 0, G = 0, B = 0;
  for (let i = 0; i < d.length; i += 4) {
    const r = d[i], g = d[i + 1], b = d[i + 2], mx = Math.max(r, g, b), mn = Math.min(r, g, b), s = mx ? (mx - mn) / mx : 0, v = mx / 255; R += r; G += g; B += b;
    let h = 0; if (mx !== mn) { h = mx === r ? (g - b) / (mx - mn) : mx === g ? 2 + (b - r) / (mx - mn) : 4 + (r - g) / (mx - mn); h = (h * 60 + 360) % 360; }
    const wgt = s * s * (0.25 + v), bn = bins[Math.floor(h / 30) % 12]; bn.w += wgt; bn.r += r * wgt; bn.g += g * wgt; bn.b += b * wgt;
  }
  const n = d.length / 4, order = bins.map((b, i) => ({ i, w: b.w })).sort((a, b) => b.w - a.w);
  const col = (bn) => bn.w ? [bn.r / bn.w, bn.g / bn.w, bn.b / bn.w] : [R / n, G / n, B / n];
  return { avg: [R / n, G / n, B / n], vib: col(bins[order[0].i]) };
};
APP.tone = (c, light, sat) => {
  const mx = Math.max(c[0], c[1], c[2]), mn = Math.min(c[0], c[1], c[2]), mid = (mx + mn) / 2 || 1;
  let o = c.map((v) => mid + (v - mid) * (sat === undefined ? 1 : sat));
  const l = (Math.max(...o) + Math.min(...o)) / 2 || 1, k = (light * 255) / l; o = o.map((v) => clamp(v * k, 0, 255));
  return o.map(Math.round);
};
APP.rgb = (c, a) => a === undefined ? `rgb(${c.map(Math.round).join(',')})` : `rgba(${c.map(Math.round).join(',')},${a})`;
const PAL = {};
APP.spotOf = (key) => { if (!PAL[key]) { const p = APP.palette(key), mx = Math.max(...p.vib), mn = Math.min(...p.vib); let c = APP.tone(p.vib, 0.6, 1.25); const lum = 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]; if (lum < 128) { const k = (128 - lum) / 190; c = c.map((v) => v + (255 - v) * k); } PAL[key] = (mx - mn < 26) ? '#EA4E30' : APP.rgb(c); } return PAL[key]; };
// a clearlogo drawn in dark strokes disappears on ink-black paper: measure it once; dark ones are printed on a cream slip
const DARK = {};
APP.logoDark = (key) => {
  if (DARK[key] !== undefined) return DARK[key]; const im = APP.BASE[key]; if (!im || !im.naturalWidth) return (DARK[key] = false);
  const c = document.createElement('canvas'); c.width = 48; c.height = 24; const x = c.getContext('2d', { willReadFrequently: true }); x.drawImage(im, 0, 0, 48, 24);
  const d = x.getImageData(0, 0, 48, 24).data; let n = 0, dk = 0; for (let i = 0; i < d.length; i += 4) { if (d[i + 3] < 90) continue; n++; if (0.3 * d[i] + 0.59 * d[i + 1] + 0.11 * d[i + 2] < 62) dk++; }
  return (DARK[key] = n > 0 && dk / n > 0.3);
};
/** the title as the hero: TMDB clearlogo, on a cream slip when it is dark; no logo -> Songti text sized by length */
APP.logo = (t, cls) => {
  const k = t.images && t.images.logo;
  if (k) return `<span class="lg ${cls || ''}${APP.logoDark(k) ? ' slip' : ''}">${APP.im(k)}</span>`;
  const n = t.name.length; return `<span class="lg txt ${cls || ''}" style="--fs:${n <= 4 ? 1 : n <= 7 ? 0.78 : n <= 10 ? 0.6 : 0.47}">${APP.esc(t.name)}</span>`;
};
APP.heat = (n) => { const o = []; for (let i = 0; i < n; i++) { const x = i / n; let v = Math.abs(Math.sin(i * 0.37) + 0.6 * Math.sin(i * 0.11 + 1) + 0.35 * Math.sin(i * 1.3 + 2)) / 1.95; o.push(0.14 + 0.86 * v * (0.55 + 0.45 * Math.sin(x * 3.1))); } return o; };

// ------------------------------------------------------------------ glass refresh helpers
APP.boardOf = (el) => { const b = el.closest('.board'); return b && b.__board; };
APP.anim = (el, ms) => { const B = APP.boardOf(el); if (B) { B.animUntil = performance.now() + ms; B.sceneDirty = true; B.kick(ms + 120); } };
APP.dirty = (el) => { const B = APP.boardOf(el); if (B) { B.sceneDirty = true; B.kick(); } };
const scaleOf = APP.scaleOf = (el) => el.getBoundingClientRect().width / el.offsetWidth || 1;
function dragScroll(el) {            // mouse drag = touch scroll (the demo is viewed with a mouse). A .scroll with el.__pull gets pull-to-refresh.
  const horiz = el.classList.contains('hs'); let down = null, moved = false, pulling = false;
  el.addEventListener('pointerdown', (e) => { if (e.button !== 0 || e.target.closest('input')) return; down = { x: e.clientX, y: e.clientY, l: el.scrollLeft, t: el.scrollTop }; moved = false; pulling = false; });
  window.addEventListener('pointermove', (e) => {
    if (!down || el.__lock) return; const s = scaleOf(el), dx = (e.clientX - down.x) / s, dy = (e.clientY - down.y) / s;
    if (!moved) { if (Math.abs(horiz ? dx : dy) < 6 || Math.abs(horiz ? dy : dx) > Math.abs(horiz ? dx : dy)) return; moved = true; }
    if (horiz) { el.scrollLeft = down.l - dx; return; }
    if (el.__pull && down.t <= 0 && dy > 0) { pulling = true; el.__pull(dy); return; }
    if (pulling) { pulling = false; el.__pull(0); }
    if (e.pointerType === 'mouse') el.scrollTop = down.t - dy;
  });
  window.addEventListener('pointerup', () => { if (down && moved) el.__dragged = performance.now(); if (pulling) { pulling = false; el.__pull(null); } down = null; });
  el.addEventListener('click', (e) => { if (el.__dragged && performance.now() - el.__dragged < 80) { e.stopPropagation(); e.preventDefault(); } }, true);
}
APP.wireScroll = (root) => root.querySelectorAll('.scroll,.hs').forEach((s) => { if (s.__w) return; s.__w = 1; dragScroll(s); const B = APP.boardOf(root); if (B) s.addEventListener('scroll', () => { B.sceneDirty = true; B.kick(); }, { passive: true }); });
APP.wire = (root) => {
  root.querySelectorAll('.board').forEach((el) => {
    const B = el.__board; if (!B) return;
    B.hooks.push(() => { if (performance.now() < (B.animUntil || 0)) B.sceneDirty = true; });
    // the tab bar gets out of the way: scrolling down (reading) slides the bar and the search button off the bottom edge, any scroll up brings them back
    const sc = el.querySelector('.content > .scroll'); if (!sc) return; let last = 0;
    sc.addEventListener('scroll', () => { const t = sc.scrollTop, end = t > sc.scrollHeight - sc.clientHeight - 30; if (Math.abs(t - last) < 8) return; const away = t > last && t > 120 && !end; last = t; if (away !== el.classList.contains('tabs-away')) { el.classList.toggle('tabs-away', away); APP.anim(el, 520); } }, { passive: true });
  });
  root.querySelectorAll('.board,.scr').forEach((el) => APP.wireScroll(el));
  // headers that turn solid once the page has scrolled under them; [data-solid] = scroll offset. Full-bleed pages fade the vertical caption on the way.
  root.querySelectorAll('.b-top[data-solid],.b-band[data-solid]').forEach((h) => { const scr = h.parentElement, sc = scr.querySelector('.scroll'); const f = () => { const at = +h.dataset.solid; h.classList.toggle('solid', sc.scrollTop > at); scr.style.setProperty('--sc', clamp(sc.scrollTop / 70, 0, 1)); }; sc.addEventListener('scroll', f, { passive: true }); h.__f = f; f(); });
};
APP.spring = (fn, k, z) => {
  const s = { x: 0, v: 0, t: 0, raf: 0 }; k = k || 170; z = z || 0.82; let last = 0;
  const step = (now) => { s.raf = 0; let dt = Math.max(0.001, Math.min(0.04, (now - last) / 1000 || 0.016)); last = now; const n = 4, h = dt / n;
    for (let i = 0; i < n; i++) { s.v += (-k * (s.x - s.t) - 2 * z * Math.sqrt(k) * s.v) * h; s.x += s.v * h; }
    if (Math.abs(s.x - s.t) < 0.001 && Math.abs(s.v) < 0.01) { s.x = s.t; s.v = 0; fn(s.x); return; } fn(s.x); s.raf = requestAnimationFrame(step); };
  s.to = (t) => { s.t = t; if (APP.shot && !APP.Q.get('anim')) { s.x = t; s.v = 0; fn(t); return; } if (!s.raf) { last = performance.now(); s.raf = requestAnimationFrame(step); } };
  s.set = (x) => { s.x = s.t = x; s.v = 0; fn(x); };
  return s;
};

// ------------------------------------------------------------------ her. A reserved slot (<i class="msc" data-size=..>) gets the live layered sprite; under the canvas lies a still <img> so the glass adapter still refracts her.
APP.msc = (cls, size, extra) => `<i class="msc ${cls || ''}" data-size="${size || 56}" ${extra || ''}>${APP.im('msc_still')}</i>`;
APP.mountMascots = (root) => root.querySelectorAll('.msc').forEach((h) => {
  if (h.__m) return; const b = h.closest('.board'), scr = h.closest('.board,.scr');
  const m = h.__m = window.Mascot.create(h, { size: +h.dataset.size, hands: false, press: h.dataset.nopress === undefined, board: b && b.__board, mood: h.dataset.mood || 'idle' });
  m.sayEl = h.dataset.say ? scr.querySelector(h.dataset.say) : null; if (m.sayEl && m.sayEl.dataset.def === undefined) m.sayEl.dataset.def = m.sayEl.innerHTML;
});
APP.mascotOf = (root, sel) => { const h = root.querySelector(sel || '.msc'); return h && h.__m; };

// ------------------------------------------------------------------ page
APP.boot = async function () {
  const ids = APP.ORDER, S = APP.S;
  document.documentElement.style.background = '#0b0a08';
  const fail = (m) => { document.body.classList.add('nogl'); const e = document.getElementById('err'); if (e) { e.style.display = 'block'; e.textContent = '玻璃引擎没有启动（' + m + '）。底栏退回成了普通半透明，不是液态玻璃的效果。请用支持 WebGL2 的 Chromium 内核浏览器打开。'; } };
  await APP.loadBase();
  await window.Mascot.ready();
  IMG.msc_still = window.Mascot.snapshot(256);
  APP.derive('grain', 180, 180, (x, w, h) => { const d = x.createImageData(w, h); for (let i = 0; i < d.data.length; i += 4) { const v = 128 + (Math.random() - 0.5) * 90; d.data[i] = d.data[i + 1] = d.data[i + 2] = v; d.data[i + 3] = 60; } x.putImageData(d, 0, 0); }, 'image/png');
  // the penguin mark of the masthead: the hood of the brand picture, cut round
  APP.derive('emblem', 132, 132, (x, w) => { x.beginPath(); x.arc(w / 2, w / 2, w / 2, 0, 7); x.clip(); const b = APP.BASE.brand, n = b.naturalWidth; x.drawImage(b, n * (0.4865 - 0.112), n * (0.218 - 0.112), n * 0.224, n * 0.224, 0, 0, w, w); }, 'image/png');
  K.prepare();
  const only = APP.shot ? [APP.shot] : ids;
  only.forEach((id) => { const s = S[id]; if (s.build) Object.assign(s, s.build()); });
  const dpr = APP.shot ? (+Q.get('dpr') || 3.5) : 2;
  const mk = (id) => { const s = S[id], glass = s.chrome !== undefined;
    return `<div class="${glass ? 'board' : 'scr'} ${s.land ? 'pl' : 'ph'} sB v1 sB-${id}" id="s-${id}" data-dpr="${dpr}"><div class="content">${s.content}</div>${glass ? `<canvas class="gl"></canvas><div class="chrome">${s.chrome}</div>` : ''}<div class="fx"></div></div>`; };
  const main = document.getElementById('main');
  if (APP.shot) {
    document.body.classList.add('shot'); if (Q.get('anim')) document.body.classList.add('anim'); document.querySelector('aside').remove(); main.innerHTML = mk(APP.shot);
  } else {
    const groups = []; ids.forEach((id) => { let g = groups.find((x) => x.n === S[id].group); if (!g) groups.push(g = { n: S[id].group, ids: [] }); g.ids.push(id); });
    document.getElementById('toc').innerHTML = groups.map((g, i) => `<div class="tg"><em>${APP.pad(i + 1)}</em><b>${g.n}</b>` + g.ids.map((id) => `<a href="#f-${id}">${S[id].title}<i>${(S[id].states || [1]).length}</i></a>`).join('') + (g.n === '系统态' ? '' : '') + `</div>`).join('') +
      `<div class="tg"><em>${APP.pad(groups.length + 1)}</em><b>系统态</b><a href="#f-home" data-go="home:exit">再按一次返回退出</a><a href="#f-home" data-go="home:offline">网络断开</a></div>`;
    document.getElementById('plays').innerHTML = APP.PLAYS.map((p) => `<li><b>${p[0]}</b>${p[1]}</li>`).join('');
    const fig = (id) => { const s = S[id];
      return `<figure class="${s.land ? 'land' : 'port'}" id="f-${id}"><figcaption><b>${s.title}</b><span>${s.note || ''}</span></figcaption>` +
        `<div class="fit" data-w="${s.land ? 905 : 412}" data-h="${s.land ? 412 : 905}">${mk(id)}</div>` +
        (s.states ? `<nav class="states" data-id="${id}">${s.states.map((st, i) => `<a data-st="${st[0]}" class="${i === 0 ? 'on' : ''}">${st[1]}</a>`).join('')}</nav>` : '') + `</figure>`; };
    main.innerHTML = groups.map((g, i) => `<h3 class="grp"><em>${APP.pad(i + 1)}</em>${g.n}</h3><div class="row">${g.ids.map(fig).join('')}</div>`).join('');
    const fit = () => document.querySelectorAll('.fit').forEach((f) => { const w = +f.dataset.w, h = +f.dataset.h, s = Math.min(1, (main.clientWidth - 8) / w); f.style.width = w * s + 'px'; f.style.height = h * s + 'px'; f.firstElementChild.style.transform = `scale(${s})`; });
    fit(); addEventListener('resize', () => { fit(); requestAnimationFrame(() => window.G2.renderAll()); });
  }
  APP.fill(document);
  await window.G2.loadImages();
  await Promise.all([...document.images].map((im) => im.decode ? im.decode().catch(() => 0) : 0));
  if (document.fonts && document.fonts.ready) await document.fonts.ready;
  try {
    const t = document.createElement('canvas'); if (!t.getContext('webgl2')) fail('浏览器不支持 WebGL2');
    else window.G2.init();
    document.querySelectorAll('.sB > .fx').forEach((f) => { f.style.backgroundImage = `url(${IMG.grain})`; });
    APP.mountMascots(main);
    only.forEach((id) => { const el = document.getElementById('s-' + id); if (el && S[id].init) S[id].init(el); });
    APP.fill(main); APP.mountMascots(main);
    APP.wire(main);
    const setState = (id, st) => { const el = document.getElementById('s-' + id), s = S[id]; if (el && s.setState) { s.setState(el, st); APP.fill(el); APP.mountMascots(el); APP.wireScroll(el); el.querySelectorAll('.b-top[data-solid],.b-band[data-solid]').forEach((h) => h.__f && h.__f()); APP.anim(el, 500); } };
    APP.setState = setState;
    document.querySelectorAll('.states').forEach((n) => n.addEventListener('click', (e) => { const a = e.target.closest('a'); if (!a) return; n.querySelectorAll('a').forEach((x) => x.classList.toggle('on', x === a)); setState(n.dataset.id, a.dataset.st); }));
    document.querySelectorAll('[data-go]').forEach((a) => a.addEventListener('click', () => { const [id, st] = a.dataset.go.split(':'); const n = document.querySelector(`.states[data-id="${id}"] a[data-st="${st}"]`); if (n) n.click(); }));
    if (APP.shot) { if (APP.st) setState(APP.shot, APP.st); const sc = Q.get('scroll'); if (sc) { const el = main.querySelector('.scroll'); el.scrollTop = +sc; el.dispatchEvent(new Event('scroll')); } }
    await Promise.all([...document.images].map((im) => im.decode ? im.decode().catch(() => 0) : 0));
    window.G2.renderAll();
    const bad = window.G2.boards.filter((b) => b.error); if (bad.length) fail(bad[0].error);
  } catch (e) { fail(String(e.stack || e)); console.error(e); document.body.insertAdjacentHTML('beforeend', `<pre style="position:fixed;left:0;top:0;z-index:99;background:#400;color:#fff;font-size:11px;padding:6px;white-space:pre-wrap">${String(e.stack || e)}</pre>`); }
  requestAnimationFrame(() => requestAnimationFrame(() => { window.G2.boards.forEach((b) => { b.sceneDirty = true; }); window.G2.renderAll(); document.body.dataset.ready = '1'; }));
};
window.addEventListener('error', (e) => { document.body.insertAdjacentHTML('beforeend', `<pre style="position:fixed;left:0;top:0;z-index:99;background:#400;color:#fff;font-size:11px;padding:6px;white-space:pre-wrap">${e.message} @${(e.filename || '').split('/').pop()}:${e.lineno}</pre>`); });
})();
