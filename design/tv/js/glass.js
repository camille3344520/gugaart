/*
 * demo-v2 glass adapter.
 *
 * The glass itself is NOT implemented here: every glass pixel comes from js/engine.js (the demo3 WebGL2 engine, copied unmodified,
 * shader = glass v4) with the materials of js/core.js (G.mat / G.matMix = GlassModel). This file only bridges HTML boards to that engine:
 *
 *   board
 *     .content   ordinary HTML (pictures, gradients, text). Rasterised into a 2D canvas = the scene the glass refracts.
 *     canvas.gl  receives the engine's output, clipped to the glass shapes (everything else stays HTML).
 *     .chrome    the glass layer. Each .g element = one engine shape (position / radius from the DOM, material from data-mat).
 *                Its children (icons, text, tracks) are rasterised into an overlay the engine composites, so lenses refract them.
 *                The layer is opacity:0 in the DOM (it only gives layout and pointer events); what you see is the engine's picture.
 *
 * Behaviour and numbers follow demo3/js/kit.js + morph.js: sliding indicator (resting clear lens, lift, magnified icons, jelly),
 * control -> panel morph (one shape, spring-interpolated rect, G.matMix material), press springs (PressConfig).
 */
(function (G, G2) {
'use strict';
const PRESS_IN = [0.75, 631.7], PRESS_OUT = [0.55, 503.6];
const POS = [0.85, 322.3], DRAG = [0.86, 1754.6], LIFT = [0.85, 438.65];
const BLUE = '#3D8BFF';
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const lerp = (a, b, t) => a + (b - a) * t;
const LIFTQ = /[?&]lift/.test(location.search);   // ?lift=1: freeze the lenses in their lifted state (for still screenshots)
const sstep = (a, b, x) => { const t = clamp((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t); };

// ------------------------------------------------------------------ images + icons
const IMGS = {};
G2.loadImages = function () {
  return Promise.all(Object.keys(window.IMG).map((k) => new Promise((res) => { const im = new Image(); im.onload = im.onerror = () => res(); im.src = window.IMG[k]; IMGS[k] = im; })));
};
const SYM = {};
function compileSymbols() {
  document.querySelectorAll('symbol').forEach((s) => {
    SYM[s.id] = [...s.children].map((ch) => {
      const t = ch.tagName.toLowerCase(), fill = ch.getAttribute('fill') === 'currentColor', stroke = ch.getAttribute('stroke') !== 'none';
      const n = (a) => parseFloat(ch.getAttribute(a) || 0);
      if (t === 'path') return { p: new Path2D(ch.getAttribute('d')), fill, stroke };
      if (t === 'rect') { const p = new Path2D(); p.roundRect(n('x'), n('y'), n('width'), n('height'), n('rx')); return { p, fill, stroke }; }
      if (t === 'circle') { const p = new Path2D(); p.arc(n('cx'), n('cy'), n('r'), 0, 7); return { p, fill, stroke }; }
      if (t === 'text') return { text: ch.textContent, x: n('x'), y: n('y'), size: n('font-size') };
      return null;
    }).filter(Boolean);
  });
}

// ------------------------------------------------------------------ a small HTML -> canvas rasteriser (boxes, pictures, gradients, text, icons)
function splitTop(s) { const out = []; let d = 0, cur = ''; for (const ch of s) { if (ch === '(') d++; if (ch === ')') d--; if (ch === ',' && d === 0) { out.push(cur.trim()); cur = ''; } else cur += ch; } if (cur.trim()) out.push(cur.trim()); return out; }
function gradient(ctx, def, r) {
  const parts = splitTop(def.slice(def.indexOf('(') + 1, def.lastIndexOf(')')));
  let ang = Math.PI; const f = parts[0];
  if (/deg$/.test(f)) { ang = parseFloat(f) * Math.PI / 180; parts.shift(); }
  else if (/^to /.test(f)) { ang = { 'to top': 0, 'to right': Math.PI / 2, 'to bottom': Math.PI, 'to left': -Math.PI / 2 }[f] || Math.PI; parts.shift(); }
  const dx = Math.sin(ang), dy = -Math.cos(ang), L = Math.abs(r.w * dx) + Math.abs(r.h * dy), cx = r.x + r.w / 2, cy = r.y + r.h / 2;
  const g = ctx.createLinearGradient(cx - dx * L / 2, cy - dy * L / 2, cx + dx * L / 2, cy + dy * L / 2);
  const stops = parts.map((p) => { const m = /^(.*?)(?:\s+(-?[\d.]+)(%|px))?$/.exec(p); return { c: m[1], o: m[2] === undefined ? null : (m[3] === '%' ? parseFloat(m[2]) / 100 : parseFloat(m[2]) / L) }; });
  stops.forEach((s, i) => { if (s.o === null) s.o = i === 0 ? 0 : (i === stops.length - 1 ? 1 : null); });
  for (let i = 0; i < stops.length; i++) if (stops[i].o === null) { let j = i; while (stops[j].o === null) j++; const a = stops[i - 1].o, b = stops[j].o; for (let k = i; k < j; k++) stops[k].o = a + (b - a) * (k - i + 1) / (j - i + 1); }
  try { stops.forEach((s) => g.addColorStop(clamp(s.o, 0, 1), s.c)); } catch (e) { return null; }
  return g;
}
function rr(ctx, x, y, w, h, r) { ctx.beginPath(); ctx.roundRect(x, y, w, h, Math.max(0, Math.min(r, w / 2, h / 2))); }

function makeRaster(board) {
  const br = board.getBoundingClientRect(), sc = br.width / board.offsetWidth || 1;
  const R = (el) => { const r = el.getBoundingClientRect(); return { x: (r.left - br.left) / sc, y: (r.top - br.top) / sc, w: r.width / sc, h: r.height / sc }; };
  const range = document.createRange();
  function text(ctx, node, alpha) {
    const raw = node.nodeValue; if (!raw || !raw.trim()) return;
    const el = node.parentElement, cs = getComputedStyle(el);
    range.selectNodeContents(node); const rects = range.getClientRects(); if (!rects.length) return;
    ctx.save();
    ctx.globalAlpha = alpha; ctx.fillStyle = cs.color; ctx.font = `${cs.fontStyle} ${cs.fontWeight} ${cs.fontSize} ${cs.fontFamily}`;
    ctx.textBaseline = 'middle'; ctx.textAlign = 'left';
    if (cs.letterSpacing !== 'normal') ctx.letterSpacing = cs.letterSpacing;
    if (cs.textShadow && cs.textShadow !== 'none') { ctx.shadowColor = 'rgba(0,0,0,0.7)'; ctx.shadowBlur = Math.max(3, parseFloat(cs.fontSize) * 0.35); ctx.shadowOffsetY = 1; }
    const put = (s, rc) => ctx.fillText(s, (rc.left - br.left) / sc, (rc.top + rc.height / 2 - br.top) / sc + parseFloat(cs.fontSize) * 0.04);
    if (rects.length === 1) {
      const lead = /^\s/.test(raw) && node.previousSibling ? ctx.measureText(' ').width : 0;   // a rendered leading space after an inline sibling
      ctx.fillText(raw.replace(/\s+/g, ' ').trim(), (rects[0].left - br.left) / sc + lead, (rects[0].top + rects[0].height / 2 - br.top) / sc + parseFloat(cs.fontSize) * 0.04);
    }
    else {
      let run = '', first = null, top = null;
      for (let i = 0; i < raw.length; i++) {
        range.setStart(node, i); range.setEnd(node, i + 1); const rc = range.getClientRects()[0]; if (!rc || rc.width === 0) continue;
        if (top === null || Math.abs(rc.top - top) > rc.height * 0.5) { if (run) put(run, first); run = ''; first = rc; top = rc.top; }
        run += raw[i];
      }
      if (run) put(run, first);
    }
    ctx.restore();
  }
  function icon(ctx, el, alpha) {
    const use = el.querySelector('use'); if (!use) return;
    const ops = SYM[(use.getAttribute('href') || '').slice(1)]; if (!ops) return;
    const r = R(el), cs = getComputedStyle(el), k = r.w / 24;
    ctx.save(); ctx.globalAlpha = alpha; ctx.translate(r.x, r.y); ctx.scale(k, k);
    ctx.strokeStyle = ctx.fillStyle = cs.color; ctx.lineWidth = parseFloat(cs.strokeWidth) || 1.75; ctx.lineCap = 'round'; ctx.lineJoin = 'round';
    if (cs.filter && cs.filter !== 'none') { ctx.shadowColor = 'rgba(0,0,0,0.5)'; ctx.shadowBlur = 3; ctx.shadowOffsetY = 1; }
    for (const o of ops) {
      if (o.text) { ctx.font = `700 ${o.size}px Arial`; ctx.textAlign = 'center'; ctx.textBaseline = 'alphabetic'; ctx.fillText(o.text, o.x, o.y); continue; }
      if (o.fill) ctx.fill(o.p); if (o.stroke && !o.fill) ctx.stroke(o.p);
    }
    ctx.restore();
  }
  function walk(ctx, el, alpha, skip) {
    if (el.nodeType === 3) { text(ctx, el, alpha); return; }
    if (el.nodeType !== 1 || (skip && skip(el))) return;
    const cs = getComputedStyle(el);
    if (cs.display === 'none' || cs.visibility === 'hidden') return;
    const a = alpha * parseFloat(cs.opacity); if (a <= 0.004) return;
    const tag = el.tagName.toLowerCase();
    if (tag === 'svg') { icon(ctx, el, a); return; }
    const r = R(el); if (r.w <= 0 || r.h <= 0) { if (tag !== 'span' && tag !== 'b' && tag !== 'em' && tag !== 'small') { for (const ch of el.childNodes) walk(ctx, ch, a, skip); return; } }
    const rad = parseFloat(cs.borderTopLeftRadius) || 0;
    ctx.save();
    if (el.__k && el.__k !== 1) { const cx = r.x + r.w / 2, cy = r.y + r.h / 2; ctx.translate(cx, cy); ctx.scale(el.__k, el.__k); ctx.translate(-cx, -cy); }
    ctx.globalAlpha = a;
    if (tag === 'img') {
      const im = IMGS[el.dataset.img];
      if (im && im.naturalWidth) {
        ctx.save(); rr(ctx, r.x, r.y, r.w, r.h, rad); ctx.clip();
        const iw = im.naturalWidth, ih = im.naturalHeight, fit = cs.objectFit;
        const k = fit === 'contain' ? Math.min(r.w / iw, r.h / ih) : Math.max(r.w / iw, r.h / ih);
        const dw = iw * k, dh = ih * k, op = cs.objectPosition.split(' ').map((v) => parseFloat(v) / 100);
        if (cs.filter && /drop-shadow/.test(cs.filter)) { ctx.restore(); ctx.save(); ctx.shadowColor = 'rgba(0,0,0,0.55)'; ctx.shadowBlur = 14; ctx.shadowOffsetY = 2; }
        ctx.drawImage(im, r.x + (r.w - dw) * (isNaN(op[0]) ? 0.5 : op[0]), r.y + (r.h - dh) * (isNaN(op[1]) ? 0.5 : op[1]), dw, dh);
        ctx.restore();
      }
      ctx.restore(); return;
    }
    const bc = cs.backgroundColor;
    if (bc && bc !== 'rgba(0, 0, 0, 0)' && bc !== 'transparent') { ctx.fillStyle = bc; rr(ctx, r.x, r.y, r.w, r.h, rad); ctx.fill(); }
    const bi = cs.backgroundImage;
    if (bi && bi !== 'none' && bi.length < 4000) {
      const layers = splitTop(bi).reverse();
      for (const l of layers) if (l.startsWith('linear-gradient')) { const g = gradient(ctx, l, r); if (g) { ctx.fillStyle = g; rr(ctx, r.x, r.y, r.w, r.h, rad); ctx.fill(); } }
    }
    const bw = parseFloat(cs.borderTopWidth) || 0;
    if (bw > 0 && cs.borderTopStyle === 'solid') { ctx.strokeStyle = cs.borderTopColor; ctx.lineWidth = bw; rr(ctx, r.x + bw / 2, r.y + bw / 2, r.w - bw, r.h - bw, rad - bw / 2); ctx.stroke(); }
    const bs = cs.boxShadow;
    if (bs && bs !== 'none') for (const sh of splitTop(bs)) {               // only solid rings: "color 0px 0px 0px Npx"
      const m = /^(rgba?\([^)]*\))\s+0px\s+0px\s+0px\s+([\d.]+)px$/.exec(sh);
      if (m) { const w = parseFloat(m[2]); ctx.strokeStyle = m[1]; ctx.lineWidth = w; rr(ctx, r.x - w / 2, r.y - w / 2, r.w + w, r.h + w, rad + w / 2); ctx.stroke(); }
    }
    if (cs.overflowX !== 'visible') { rr(ctx, r.x, r.y, r.w, r.h, rad); ctx.clip(); }
    for (const ch of el.childNodes) walk(ctx, ch, a, skip);
    ctx.restore();
  }
  return { R, walk };
}

// ------------------------------------------------------------------ engines (one WebGL context per board size)
const ENG = {};
function engineFor(W, H, dpr) {
  const key = W + 'x' + H + '@' + dpr;
  if (!ENG[key]) {
    const c = document.createElement('canvas'); const e = G.createEngine(c, W, H, dpr);
    if (!e) return null;
    const mk = () => { const x = document.createElement('canvas'); x.width = e.RW; x.height = e.RH; const g = x.getContext('2d'); g.setTransform(dpr, 0, 0, dpr, 0, 0); return g; };
    const bg = document.createElement('canvas'); bg.width = bg.height = 2; const b = bg.getContext('2d'); b.fillStyle = '#0A0A0C'; b.fillRect(0, 0, 2, 2); e.setBg(bg);
    ENG[key] = { e, c, scene: mk(), ovA: mk(), ovB: mk() };
  }
  return ENG[key];
}

// ------------------------------------------------------------------ boards
const BOARDS = [];
function Board(el) {
  this.el = el; this.W = el.offsetWidth; this.H = el.offsetHeight;
  this.tv = el.classList.contains('tv');
  this.dpr = +el.dataset.dpr || 1;
  this.content = el.querySelector('.content'); this.chrome = el.querySelector('.chrome');
  this.out = el.querySelector('canvas.gl'); this.out.width = Math.round(this.W * this.dpr); this.out.height = Math.round(this.H * this.dpr);
  this.octx = this.out.getContext('2d');
  this.sp = {}; this.active = false; this.raf = 0; this.last = 0; this.until = 0; this.pressEl = null;
  this.staticScene = null; this.sceneDirty = true; this.ind = new Map(); this.hooks = [];
  this.dyn = [...this.content.querySelectorAll('[data-dyn]')];
}
Board.prototype.spring = function (key, target, z, k, init) {
  let s = this.sp[key]; if (!s) s = this.sp[key] = { x: init !== undefined ? init : target, v: 0 };
  const dt = Math.min(this.dt, 0.25), n = Math.max(4, Math.ceil(dt / 0.006)), h = dt / n;
  for (let i = 0; i < n; i++) { const a = -k * (s.x - target) - 2 * z * Math.sqrt(k) * s.v; s.v += a * h; s.x += s.v * h; }
  if (Math.abs(s.x - target) > 0.0015 || Math.abs(s.v) > 0.01) this.active = true; else { s.x = target; s.v = 0; }
  return s.x;
};
Board.prototype.vel = function (key) { const s = this.sp[key]; return s ? s.v : 0; };
Board.prototype.kick = function (ms) { this.until = Math.max(this.until, performance.now() + (ms || 0)); if (!this.raf) { this.last = performance.now(); this.raf = requestAnimationFrame((t) => this.loop(t)); } };
Board.prototype.loop = function (t) {
  this.raf = 0; this.dt = Math.min(0.05, (t - this.last) / 1000) || 0.016; this.last = t;
  this.render();
  if (this.active || t < this.until) this.raf = requestAnimationFrame((x) => this.loop(x));
};

function shapeOf(m, r, o) {
  o = o || {};
  return { cx: r.x + r.w / 2, cy: r.y + r.h / 2, hw: r.w / 2, hh: r.h / 2, r: o.r !== undefined ? o.r : Math.min(r.w, r.h) / 2, sx: o.sx || 1, sy: o.sy || 1,
    vis: (o.vis !== undefined ? o.vis : 1) * m.vis, blur: m.blur, th: m.th, n: m.n, ca: m.ca, tint: m.tint, lit: m.lit, back: m.back, shade: null };
}
function radiusOf(el, r) { const v = parseFloat(getComputedStyle(el).borderTopLeftRadius); return Math.min(isNaN(v) ? 999 : v, r.w / 2, r.h / 2); }

Board.prototype.render = function () {
  const B = this, eng = engineFor(B.W, B.H, B.dpr); if (!eng) return;
  if (B.dt === undefined) B.dt = 0.016;
  B.active = false;
  const ras = makeRaster(B.el), R = ras.R;
  B.hooks.forEach((h) => h(B));                                   // page behaviours (focus slide, scrubbing ...) move DOM before it is read

  // ---- scene: the content layer (static part cached, [data-dyn] parts every frame)
  if (B.sceneDirty || !B.staticScene) {
    if (!B.staticScene) { const c = document.createElement('canvas'); c.width = eng.e.RW; c.height = eng.e.RH; B.staticScene = c.getContext('2d'); B.staticScene.setTransform(B.dpr, 0, 0, B.dpr, 0, 0); }
    const s = B.staticScene; s.save(); s.setTransform(1, 0, 0, 1, 0, 0); s.clearRect(0, 0, eng.e.RW, eng.e.RH); s.restore();
    s.fillStyle = getComputedStyle(B.el).backgroundColor; s.fillRect(0, 0, B.W, B.H);
    for (const ch of B.content.childNodes) ras.walk(s, ch, 1, (el) => el.dataset && el.dataset.dyn !== undefined);
    B.sceneDirty = false;
  }
  const sc = eng.scene; sc.save(); sc.setTransform(1, 0, 0, 1, 0, 0); sc.drawImage(B.staticScene.canvas, 0, 0); sc.restore();
  for (const d of B.dyn) ras.walk(sc, d, 1, null);
  eng.e.setScene(sc.canvas, B.H);

  // ---- glass layer -> shapes + label overlays
  const g1 = [], g2 = [], t1 = [], t2 = [], clipRects = [];
  const clear = (c) => { c.save(); c.setTransform(1, 0, 0, 1, 0, 0); c.clearRect(0, 0, eng.e.RW, eng.e.RH); c.restore(); };
  clear(eng.ovA); clear(eng.ovB);
  const morphT = {};                                               // panel id -> t
  const panels = [...B.chrome.querySelectorAll('.g[data-from]')];
  panels.forEach((p) => {
    const open = p.classList.contains('open'), z = B.tv ? [1, 440] : POS;
    morphT[p.id] = clamp(B.spring('mo:' + p.id, open ? 1 : 0, z[0], z[1], open ? 1 : 0), -0.05, 1.08);
    p.style.pointerEvents = open ? 'auto' : 'none';
  });
  const anyT = () => panels.reduce((m, p) => Math.max(m, clamp(morphT[p.id], 0, 1)), 0);
  const fadeOf = (el) => {
    let v = 1; const f = el.closest('[data-fade]');
    if (f) v *= 1 - (f.dataset.fade === 'any' ? anyT() : clamp(morphT[f.dataset.fade.slice(1)] || 0, 0, 1));
    const fi = el.closest('[data-fadein]'); if (fi) v *= clamp(((morphT[fi.dataset.fadein.slice(1)] || 0) - 0.6) / 0.4, 0, 1);
    const host = el.parentElement.closest('.g[data-from]'); if (host) v *= clamp((morphT[host.id] - 0.6) / 0.4, 0, 1);
    return v;
  };
  // focus rings / thumbs that follow a target element
  B.chrome.querySelectorAll('.g[data-on]').forEach((ring) => {
    const tg = B.el.querySelector(ring.dataset.on); if (!tg) return;
    const r = R(tg), e = +(ring.dataset.pad || (B.tv ? 12 : 6)), rad = (parseFloat(getComputedStyle(tg).borderTopLeftRadius) || 0);
    const key = 'ring:' + ring.dataset.on;
    const x = B.spring(key + 'x', r.x - e, 1, 300, r.x - e), y = B.spring(key + 'y', r.y - e, 1, 300, r.y - e), w = B.spring(key + 'w', r.w + 2 * e, 1, 300, r.w + 2 * e), h = B.spring(key + 'h', r.h + 2 * e, 1, 300, r.h + 2 * e);
    const vx = Math.min(1, Math.abs(B.vel(key + 'x')) / 2400);
    ring.style.left = x + 'px'; ring.style.top = y + 'px'; ring.style.width = w + 'px'; ring.style.height = h + 'px';
    ring.style.borderRadius = Math.min(rad + e, (r.h + 2 * e) / 2) + 'px'; ring.dataset.jx = 1 + 0.10 * vx; ring.dataset.jy = 1 - 0.05 * vx;
  });

  const drawLabels = (ctx, el, r, alpha, k, clip) => {
    if (alpha <= 0.004) return;
    ctx.save();
    if (clip) { rr(ctx, clip.x, clip.y, clip.w, clip.h, clip.r); ctx.clip(); }
    if (k && k !== 1) { const cx = r.x + r.w / 2, cy = r.y + r.h / 2; ctx.translate(cx, cy); ctx.scale(k, k); ctx.translate(-cx, -cy); }
    for (const ch of el.childNodes) ras.walk(ctx, ch, alpha, (e) => e.classList && e.classList.contains('g'));
    ctx.restore();
  };

  for (const el of B.chrome.querySelectorAll('.g')) {
    const matName = el.dataset.mat || 'clear', top = !!el.closest('[data-top]');
    const r = R(el); if (r.w <= 0) continue;
    const rad = radiusOf(el, r);
    const fromSel = el.dataset.from;
    let vis = fromSel ? 1 : fadeOf(el);
    // ---- a panel that grows out of its control (morph.js: one shape, interpolated rect + G.matMix material)
    if (fromSel) {
      const t = morphT[el.id], src = B.chrome.querySelector(fromSel);
      if (t <= 0.004 || !src) continue;
      const a = R(src), ar = radiusOf(src, a), k = t;
      const cur = { x: lerp(a.x, r.x, k), y: lerp(a.y, r.y, k), w: Math.max(4, lerp(a.w, r.w, k)), h: Math.max(4, lerp(a.h, r.h, k)) };
      cur.r = Math.min(lerp(ar, rad, clamp(k, 0, 1)), cur.w / 2, cur.h / 2);
      const m = G.matMix(src.dataset.mat || (src.closest('.g') && src.closest('.g').dataset.mat) || 'bar', matName, clamp(t, 0, 1), true);
      g2.push(shapeOf(m, cur, { r: cur.r })); clipRects.push(cur);
      drawLabels(eng.ovB, el, r, clamp((t - 0.5) / 0.5, 0, 1), 1, cur);
      continue;
    }
    // ---- thumbs (lenses): slider / scrub thumb, focus ring
    if (matName === 'thumb') {
      const key = 'th:' + (el.id || el.className), lift = B.spring(key, el.classList.contains('held') ? 1 : 0, LIFT[0], LIFT[1], LIFTQ && el.classList.contains('held') ? 1 : 0);
      const ex = (el.dataset.ring !== undefined ? 0 : 4) * clamp(lift, 0, 1.2);
      const rc = { x: r.x - ex, y: r.y - ex, w: r.w + ex * 2, h: r.h + ex * 2 };
      const jx = +el.dataset.jx || 1, jy = +el.dataset.jy || 1;
      (top ? t2 : t1).push(shapeOf(G.mat('thumb', true), rc, { r: el.dataset.ring !== undefined ? rad : Math.min(rc.w, rc.h) / 2, sx: jx, sy: jy, vis }));
      clipRects.push({ x: rc.x - rc.w * (jx - 1) / 2 - 2, y: rc.y - rc.h * (jy - 1) / 2 - 2, w: rc.w * jx + 4, h: rc.h * jy + 4, r: rad + ex + 2 });
      if (el.dataset.solid !== undefined) {                        // resting thumb: white solid, turns into a clear lens while held (ui-spec 2.5, Thumb)
        const c = top ? eng.ovB : eng.ovA; c.save(); c.globalAlpha = vis * (1 - clamp(lift, 0, 1)); c.fillStyle = '#fff'; rr(c, r.x, r.y, r.w, r.h, 999); c.fill(); c.restore();
      }
      continue;
    }
    // ---- ordinary glass: bar / clear / panel / pop
    let sx = 1, sy = 1;
    if (!B.tv && el.dataset.press !== undefined) {
      const down = B.pressEl === el, grow = Math.min(0.15 * Math.min(r.w, r.h), 8), z = down ? PRESS_IN : PRESS_OUT;
      const p = B.spring('ps:' + (el.id || (el.__id = el.__id || Math.random())), down ? 1 : 0, z[0], z[1], 0);
      sx = 1 + 2 * grow * p / r.w; sy = 1 + 2 * grow * p / r.h;
    }
    // a control whose panel is open hands its glass over to the morphing shape
    let own = vis, lab = vis;
    const myPanel = el.id && panels.find((p) => p.dataset.from === '#' + el.id);
    if (myPanel) { const t = clamp(morphT[myPanel.id], 0, 1); own *= t > 0.004 ? 0 : 1; lab *= 1 - clamp(t / 0.4, 0, 1); }
    if (own > 0.004 && matName !== 'none') {
      (top ? g2 : g1).push(shapeOf(G.mat(matName, true), r, { r: rad, sx, sy, vis: own }));
      clipRects.push({ x: r.x - r.w * (sx - 1) / 2, y: r.y - r.h * (sy - 1) / 2, w: r.w * sx, h: r.h * sy, r: rad * Math.max(sx, sy) });
    }
    // ---- sliding indicator (kit.js indicator): resting clear lens, lift, magnified cells, jelly
    const kind = el.dataset.ind;
    if (kind) {
      const cells = [...el.querySelectorAll(':scope > [data-cell]')], n = cells.length;
      let st = B.ind.get(el); if (!st) { st = { sel: Math.max(0, cells.findIndex((c) => c.classList.contains('on'))), pressed: false, dragging: false, drag: 0 }; B.ind.set(el, st);
        if (/[?&]lift/.test(location.search) && kind === 'tab') { st.pressed = st.dragging = true; st.drag = 0.6; } }
      const rects = cells.map(R), target = st.dragging ? st.drag : st.sel, kx = 'ind:' + (el.id || el.className);
      const z = st.dragging ? DRAG : POS, pos = B.spring(kx, target, z[0], z[1], target), v = B.vel(kx);
      const lt = B.tv ? 0 : ((st.pressed || Math.abs(pos - target) > 0.05) ? 1 : 0);
      const lift = B.spring(kx + ':l', lt, LIFT[0], LIFT[1], LIFTQ ? lt : 0), L = clamp(lift, 0, 1.2);
      const vT = Math.min(5, Math.abs(v) / Math.max(1, n - 1)), jx = 1 - 0.08 * vT, jy = 1 + 0.048 * vT;
      const i0 = clamp(Math.floor(pos), 0, n - 1), i1 = Math.min(n - 1, i0 + 1), f = clamp(pos - i0, 0, 1);
      const pc = pos < 0 ? pos : (pos > n - 1 ? pos - (n - 1) : 0);
      const cell = { x: lerp(rects[i0].x, rects[i1].x, f) + pc * rects[i0].w, y: rects[i0].y, w: lerp(rects[i0].w, rects[i1].w, f), h: rects[i0].h };
      cells.forEach((c, i) => { const d = i - pos, t = sstep(0, 1, 1 - Math.min(1, Math.max(0, (Math.abs(d) - 0.52) / 0.36))); c.__k = 1 + 0.25 * L * t; c.classList.toggle('on', Math.round(clamp(pos, 0, n - 1)) === i); });
      if (!B.tv) {
        const ex = (kind === 'tab' ? 11 : 12) * lift, ey = 8 * lift;
        const lr = { x: cell.x - ex, y: cell.y - ey, w: cell.w + ex * 2, h: cell.h + ey * 2 };
        (top ? t2 : t1).push(shapeOf(G.mat('thumb', true), lr, { sx: jx, sy: jy, vis }));
        clipRects.push({ x: lr.x - 3, y: lr.y - lr.h * (jy - 1) / 2 - 3, w: lr.w + 6, h: lr.h * jy + 6, r: 999 });
      } else {                                                    // TV: no lift; the selected cell is a resting pill (white 10 %)
        const c = top ? eng.ovB : eng.ovA; c.save(); c.globalAlpha = vis; c.fillStyle = 'rgba(255,255,255,0.10)'; rr(c, cell.x, cell.y, cell.w, cell.h, 999); c.fill(); c.restore();
      }
    }
    drawLabels(top ? eng.ovB : eng.ovA, el, r, lab, Math.max(sx, sy) > 1 ? 1 + (Math.min(sx, sy) - 1) : 1, null);
  }

  eng.e.setOverlay('A', eng.ovA.canvas); eng.e.setOverlay('B', eng.ovB.canvas);
  const stages = [];
  if (g1.length) stages.push({ t: 'glass', shapes: g1 });
  stages.push({ t: 'ov', k: 'A' });
  if (t1.length) stages.push({ t: 'glass', shapes: t1 });
  if (g2.length) stages.push({ t: 'glass', shapes: g2 });
  stages.push({ t: 'ov', k: 'B' });
  if (t2.length) stages.push({ t: 'glass', shapes: t2 });
  eng.e.render({ scroll: 0, stages });

  // ---- only the glass shapes go back to the page
  const o = B.octx; o.setTransform(1, 0, 0, 1, 0, 0); o.clearRect(0, 0, B.out.width, B.out.height);
  if (clipRects.length) {
    o.save(); o.setTransform(B.dpr, 0, 0, B.dpr, 0, 0); o.beginPath();
    for (const c of clipRects) o.roundRect(c.x - 0.5, c.y - 0.5, c.w + 1, c.h + 1, Math.max(0, Math.min(c.r + 0.5, (c.w + 1) / 2, (c.h + 1) / 2)));
    o.clip(); o.setTransform(1, 0, 0, 1, 0, 0); o.drawImage(eng.c, 0, 0); o.restore();
  }
  B.shapeCount = g1.length + g2.length + t1.length + t2.length;
};

// ------------------------------------------------------------------ input
function bindBoard(B) {
  const el = B.el;
  const pt = (e) => { const r = el.getBoundingClientRect(), s = r.width / B.W; return { x: (e.clientX - r.left) / s, y: (e.clientY - r.top) / s }; };
  el.addEventListener('pointerdown', (e) => {
    const g = e.target.closest('.g'); if (!g || !B.chrome.contains(g)) return;
    const ras = makeRaster(el), p = pt(e);
    const indEl = e.target.closest('.g[data-ind]');
    if (indEl) {
      const st = B.ind.get(indEl), cells = [...indEl.querySelectorAll(':scope > [data-cell]')], rects = cells.map(ras.R);
      const at = (x) => { for (let i = 0; i < rects.length; i++) if (x < rects[i].x + rects[i].w) return i; return rects.length - 1; };
      const ci = at(p.x);
      st.pressed = st.sel === ci; st.moved = false;
      const eq = rects.reduce((a, r) => a + r.w, 0) / rects.length, x0 = rects[0].x;
      const move = (ev) => { if (!st.pressed) return; const q = pt(ev); st.dragging = true; st.moved = true; let ps = (q.x - x0) / eq - 0.5; const max = rects.length - 1; if (ps < 0) ps = -Math.min(0.3, -ps * 0.4); else if (ps > max) ps = max + Math.min(0.3, (ps - max) * 0.4); st.drag = ps; B.kick(); };
      const up = (ev) => {
        const q = pt(ev);
        if (st.dragging) { const vv = B.vel('ind:' + (indEl.id || indEl.className)); st.sel = clamp(Math.round(st.drag + Math.sign(vv) * (Math.abs(vv) > 1.5 ? 0.5 : 0)), 0, rects.length - 1); }
        else st.sel = at(q.x);
        st.pressed = false; st.dragging = false; B.kick(700);
        indEl.dispatchEvent(new CustomEvent('select', { detail: st.sel, bubbles: true }));
        window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up);
      };
      window.addEventListener('pointermove', move); window.addEventListener('pointerup', up);
      B.kick(); e.preventDefault(); return;
    }
    const pr = e.target.closest('.g[data-press]');
    if (pr) { B.pressEl = pr; const up = () => { B.pressEl = null; B.kick(); window.removeEventListener('pointerup', up); }; window.addEventListener('pointerup', up); B.kick(); }
  });
  // control <-> panel morph
  el.addEventListener('click', (e) => {
    const t = e.target.closest('[data-toggle]'); if (!t) return;
    const p = el.querySelector(t.dataset.toggle); if (!p) return;
    const open = !p.classList.contains('open');
    el.querySelectorAll('.g[data-from].open').forEach((x) => x.classList.remove('open'));
    p.classList.toggle('open', open); el.classList.toggle('panel-open', open); el.dataset.open = open ? p.id : '';
    B.kick(900);
  });
}

// ------------------------------------------------------------------ page behaviours
// progress bar / slider: the thumb is a Thumb lens (white solid at rest, clear lens while held), positioned from the bar
function bindScrub(B) {
  B.el.querySelectorAll('[data-scrub]').forEach((zone) => {
    const bar = zone.querySelector('.bar') || zone, now = zone.querySelector('.now'), knob = B.el.querySelector(zone.dataset.knob); if (!knob) return;
    const heat = zone.querySelector('.heat'), tcur = B.el.querySelector('.tcur'), TOTAL = 45 * 60 + 1;
    let p = parseFloat(now.style.width) / 100, lastX = 0, lastT = 0;
    const place = () => {
      const br = B.el.getBoundingClientRect(), s = br.width / B.W, r = bar.getBoundingClientRect(), d = knob.offsetWidth;
      knob.style.left = ((r.left - br.left) / s + r.width / s * p - d / 2) + 'px'; knob.style.top = ((r.top - br.top) / s + r.height / s / 2 - d / 2) + 'px';
    };
    B.hooks.push(place);
    const set = (e) => {
      const r = bar.getBoundingClientRect(); p = clamp((e.clientX - r.left) / r.width, 0, 1);
      now.style.width = (p * 100) + '%';
      if (heat) { const sp = heat.children; for (let i = 0; i < sp.length; i++) sp[i].classList.toggle('on', i / sp.length < p); }
      if (tcur && heat) { const t = Math.round(p * TOTAL); tcur.textContent = Math.floor(t / 60) + ':' + ('0' + t % 60).slice(-2); }
      const v = (e.clientX - lastX) / Math.max(1, e.timeStamp - lastT); lastX = e.clientX; lastT = e.timeStamp;
      const k = Math.min(0.3, Math.abs(v) * 0.2); knob.dataset.jx = 1 + k; knob.dataset.jy = 1 - k * 0.4;
      B.kick();
    };
    const down = (e) => {
      knob.classList.add('held'); lastX = e.clientX; lastT = e.timeStamp; set(e);
      const up = () => { knob.classList.remove('held'); knob.dataset.jx = knob.dataset.jy = 1; B.kick(600); window.removeEventListener('pointermove', set); window.removeEventListener('pointerup', up); };
      window.addEventListener('pointermove', set); window.addEventListener('pointerup', up); e.preventDefault();
    };
    zone.addEventListener('pointerdown', down); knob.addEventListener('pointerdown', down);
    if (/[?&]lift/.test(location.search)) knob.classList.add('held');
  });
}
// TV home: the focus (a lens ring) slides between cards; the upper half follows the focus
function bindTvHome(B) {
  const wrap = B.el.querySelector('#tvh-cards'); if (!wrap) return;
  const cards = [...wrap.querySelectorAll('.tc')].filter((c) => c.dataset.t); let cur = 0;
  const $ = (id) => B.el.querySelector('#' + id);
  cards[0].classList.add('focus');
  const go = (i) => {
    if (i === cur || i < 0 || i >= cards.length) return; cur = i;
    wrap.querySelectorAll('.tc').forEach((c) => c.classList.remove('focus')); const c = cards[i]; c.classList.add('focus');
    B.el.classList.add('swap'); B.kick(900);
    setTimeout(() => {
      const m = c.dataset.m.split('|');
      $('tvh-logo').dataset.img = 'logo_' + c.dataset.t; $('tvh-logo').src = window.IMG['logo_' + c.dataset.t];
      $('tvh-k').textContent = c.dataset.k; $('tvh-p').textContent = c.dataset.p; $('tvh-m1').textContent = m[0]; $('tvh-m2').textContent = m[1]; $('tvh-m3').textContent = m[2];
      const pv = B.el.querySelector('.pv'); pv.dataset.img = c.dataset.i; pv.src = window.IMG[c.dataset.i];
      B.el.classList.remove('swap'); B.kick(700);
    }, 220);
  };
  cards.forEach((c, i) => c.addEventListener('click', () => { stop(); go(i); }));
  let over = false, timer = null, k = 0; const seq = [1, 2, 1, 0];
  const stop = () => { clearInterval(timer); timer = null; };
  B.el.addEventListener('pointerenter', () => { over = true; }); B.el.addEventListener('pointerleave', () => { over = false; });
  window.addEventListener('keydown', (e) => { if (!over) return; if (e.key === 'ArrowRight') { stop(); go(cur + 1); e.preventDefault(); } if (e.key === 'ArrowLeft') { stop(); go(cur - 1); e.preventDefault(); } });
  if (!/[?&]shot=/.test(location.search)) timer = setInterval(() => { if (B.el.getClientRects().length) go(seq[k++ % seq.length]); }, 2800);
}

// ------------------------------------------------------------------ public
G2.boards = BOARDS;
G2.init = function () {
  compileSymbols();
  document.querySelectorAll('.board').forEach((el) => {
    const open = el.querySelector('.g[data-from].open'); if (open) { el.classList.add('panel-open'); el.dataset.open = open.id; }
    const B = new Board(el); el.__board = B; BOARDS.push(B); bindBoard(B); bindScrub(B); bindTvHome(B);
  });
};
G2.renderAll = function () { BOARDS.forEach((B) => { if (B.el.offsetParent !== null || B.el.getClientRects().length) { try { B.render(); } catch (e) { console.error(B.el.id, e); B.error = String(e); } } }); };
G2.board = (id) => { const el = document.getElementById(id); return el && el.__board; };
})(window.G3, window.G2 = window.G2 || {});
