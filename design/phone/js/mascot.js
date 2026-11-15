/*
 * [huabao] copied from ../fable-styles-b/js/mascot.js; one line added in frame() (marked).
 * 咕咕嘎嘎 mascot rig for the demo: a canvas port of apps/app/src/components/mascot (mascot-art / mascot-math / mascot-rig / mascot-driver).
 * Same sprite sheet (768 x 768 sprite space), same springs and numbers: eyes follow the pointer, blink every 3-6 s, a press flaps both
 * flippers and shows the surprised face, flippers swing towards the pointer.
 * Added for the demo (all built from the existing layers, no new drawing): moods
 *   'sleep'  = the blink patch held (eyes closed) + flippers down
 *   'cheer'  = both flippers up
 *   'cross'  = cross-eyed (the rig's hover glance, held)
 *   'gasp'   = the surprise patch held
 * and a fixed gaze (lookAt) so she can watch a card, a star, a seat ... instead of the finger.
 */
(function () {
'use strict';
const ART = {
  body: { k: 'mascot_body', x: 175, y: 491, w: 438, h: 277 },
  head: { k: 'mascot_head', x: 0, y: 0, w: 768, h: 768 },
  wingL: { k: 'mascot_wingl', x: 18, y: 410, w: 237, h: 219, pivot: [180.9, 624.1] },
  wingR: { k: 'mascot_wingr', x: 541, y: 318, w: 210, h: 308, pivot: [608.6, 621.8] },
  eyes: {
    L: { back: { k: 'mascot_backl', x: 244, y: 298, w: 102, h: 72 }, iris: { k: 'mascot_irisl', x: 269, y: 299, w: 70, h: 70 },
      pupil: { cx: 305.6, cy: 335.3, rx: 3.9, ry: 9.75, fill: '#38141e' },
      hl: [{ cx: 278.4, cy: 328.3, rx: 7.6, ry: 5.26, fill: '#fff' }, { cx: 319.3, cy: 315.2, rx: 1.79, ry: 1.79, fill: '#f5f7fc' }, { cx: 322, cy: 322, rx: 2.57, ry: 2.18, fill: '#fffffe' }] },
    R: { back: { k: 'mascot_backr', x: 424, y: 294, w: 103, h: 73 }, iris: { k: 'mascot_irisr', x: 433, y: 295, w: 70, h: 70 },
      pupil: { cx: 466.6, cy: 331.4, rx: 3.74, ry: 9.94, fill: '#2a101c' },
      hl: [{ cx: 443.1, cy: 324, rx: 8.38, ry: 5.46, fill: '#fff' }] },
  },
  penguin: [{ cx: 299.4, cy: 83.3, rx: 16.65, ry: 8.56, ang: -51.4 }, { cx: 462.8, cy: 77, rx: 15.96, ry: 9.05, ang: 47.8 }],
  blink: { k: 'mascot_blink', x: 213, y: 242, w: 346, h: 229 },
  surprise: { k: 'mascot_surprise', x: 214, y: 260, w: 345, h: 192 },
};
// mascot-math.ts
const clamp = (v, a, b) => (v < a ? a : v > b ? b : v);
const IRIS = { x: 13, y: 8 }, PUPIL_DEPTH = 1.14, HL_DEPTH = 0.32, PEN = { x: 5.5, y: 4.5 }, CROSS = { x: 7, y: 2.5 };
const HAND_UP = 14, HAND_DOWN = 8, HAND_SIDE = 3, FLAP_V = 440, BLINK_MS = 135, SURPRISE_MS = 700;
const HAND_SPRING = { k: 280, z: 0.55 }, EYE_SPRING = { k: 420, z: 0.8 }, CROSS_SPRING = { k: 220, z: 0.75 };
const motionScale = (px) => clamp(0.9 + ((px - 44) / (128 - 44)) * 0.45, 0.9, 1.35);
function gazeFrom(dx, dy, size) { const d = Math.hypot(dx, dy); if (!(d > 0)) return { x: 0, y: 0 }; const m = 1 - Math.exp(-d / (size * 1.25)); return { x: dx / d * m, y: dy / d * m }; }
function step(s, target, sp, dt) {
  const n = Math.max(1, Math.ceil(dt / (1 / 240))), h = dt / n, c = 2 * sp.z * Math.sqrt(sp.k);
  for (let i = 0; i < n; i++) { s.v += (-sp.k * (s.x - target) - c * s.v) * h; s.x += s.v * h; }
}
const smooth = (t) => { const c = clamp(t, 0, 1); return c * c * (3 - 2 * c); };
const blinkOp = (e) => (e <= 0 || e >= BLINK_MS ? 0 : e < 50 ? smooth(e / 50) : e < 65 ? 1 : 1 - smooth((e - 65) / 70));
const surpriseOp = (e) => (e <= 0 || e >= SURPRISE_MS ? 0 : e < 70 ? smooth(e / 70) : e > SURPRISE_MS - 220 ? 1 - smooth((e - (SURPRISE_MS - 220)) / 220) : 1);

const IM = {}; let loaded = false;
function load() {
  if (loaded) return; loaded = true;
  const keys = ['mascot_body', 'mascot_head', 'mascot_wingl', 'mascot_wingr', 'mascot_backl', 'mascot_backr', 'mascot_irisl', 'mascot_irisr', 'mascot_blink', 'mascot_surprise'];
  keys.forEach((k) => { const im = new Image(); im.src = window.IMG[k]; IM[k] = im; });
}
const ALL = []; let pointer = null, raf = 0, last = 0;
const FROZEN = /[?&]shot/.test(location.search);

function Mascot(host, o) {
  o = o || {}; load();
  this.size = o.size || 96; this.host = host;
  const c = this.el = document.createElement('canvas'); c.className = 'mascot'; c.dataset.dyn = '';
  const px = Math.round(this.size * (o.res || 2)); c.width = c.height = px; c.style.width = c.style.height = this.size + 'px';
  host.appendChild(c); this.ctx = c.getContext('2d');
  this.gx = { x: 0, v: 0 }; this.gy = { x: 0, v: 0 }; this.cr = { x: 0, v: 0 }; this.hl = { x: 0, v: 0 }; this.hr = { x: 0, v: 0 }; this.ey = { x: 0, v: 0 };
  this.blinkAt = -1e9; this.surpriseAt = -1e9; this.nextBlink = performance.now() + 1500 + Math.random() * 3000;
  this.mood = o.mood || 'idle'; this.follow = o.follow !== false; this.look = null; this.hands = o.hands !== false; this.wings = null;
  this.onpress = null; this.board = o.board || null;
  if (o.press !== false) c.addEventListener('pointerdown', (e) => { this.press(); if (this.onpress) this.onpress(e); });
  ALL.push(this); kick();
}
Mascot.prototype.press = function () { this.hl.v += FLAP_V; this.hr.v -= FLAP_V; this.surpriseAt = performance.now(); kick(); };
Mascot.prototype.flap = function (k) { this.hl.v += FLAP_V * (k || 1); this.hr.v -= FLAP_V * (k || 1); kick(); };
Mascot.prototype.blink = function () { this.blinkAt = performance.now(); kick(); };
Mascot.prototype.setMood = function (m, ms) { this.mood = m; clearTimeout(this._mt); if (ms) this._mt = setTimeout(() => { this.mood = 'idle'; kick(); }, ms); kick(); };
/** look at a client point (or null = follow the finger again) */
Mascot.prototype.lookAt = function (x, y) { this.look = x === null || x === undefined ? null : { x, y }; kick(); };
Mascot.prototype.lookAtEl = function (el) { if (!el) { this.look = null; return; } const r = el.getBoundingClientRect(); this.lookAt(r.left + r.width / 2, r.top + r.height / 2); };
Mascot.prototype.visible = function () { return this.el.getClientRects().length > 0; };
Mascot.prototype.frame = function (now, dt) {
  const r = this.el.getBoundingClientRect(), size = r.width || this.size, sc = motionScale(size);
  const p = this.look || (this.follow && pointer && now - pointer.t < 3000 ? pointer : null);
  let gaze = p ? gazeFrom(p.x - (r.left + r.width / 2), p.y - (r.top + r.height * 0.43), size) : { x: 0, y: 0 };
  const mood = this.mood;
  if (mood === 'sleep') gaze = { x: 0, y: 0.5 };
  const up = -gaze.y, lift = (up >= 0 ? up * HAND_UP : up * HAND_DOWN) * sc, side = gaze.x * HAND_SIDE * sc;
  let hL = this.hands ? lift - side : 0, hR = this.hands ? -(lift + side) : 0;
  if (mood === 'cheer') { hL = 24; hR = -24; } else if (mood === 'sleep') { hL = -9; hR = 9; } else if (mood === 'gasp') { hL = 12; hR = -12; }
  if (this.wings) { hL = this.wings[0]; hR = this.wings[1]; }
  const cross = mood === 'cross' ? 1.25 : 0;
  step(this.gx, gaze.x, EYE_SPRING, dt); step(this.gy, gaze.y, EYE_SPRING, dt); step(this.cr, cross, CROSS_SPRING, dt);
  step(this.hl, hL, HAND_SPRING, dt); step(this.hr, hR, HAND_SPRING, dt);
  if (now > this.nextBlink) { this.blinkAt = now; this.nextBlink = now + 3000 + Math.random() * 3000; }
  step(this.ey, mood === 'sleep' ? 1 : 0, CROSS_SPRING, dt);
  let su = surpriseOp(now - this.surpriseAt); if (mood === 'gasp') su = 1;
  let bl = su > 0 ? 0 : Math.max(blinkOp(now - this.blinkAt), clamp(this.ey.x, 0, 1));
  if (FROZEN && mood !== 'sleep') bl = 0;
  this.draw(this.gx.x, this.gy.x, this.cr.x, this.hl.x, this.hr.x, bl, su, sc);
  const moving = Math.abs(this.gx.v) + Math.abs(this.gy.v) + Math.abs(this.hl.v) + Math.abs(this.hr.v) + Math.abs(this.cr.v) + Math.abs(this.ey.v) > 0.02 || bl > 0 || (su > 0 && su < 1);
  if (this.host) this.host.classList.toggle('mv', Math.abs(this.hl.x) + Math.abs(this.hr.x) > 0.6);   // [huabao] hide the still copy under the canvas while the flippers are away from rest
  if (moving && this.board) this.board.kick(60);
  return moving;
};
Mascot.prototype.draw = function (gx, gy, cross, aL, aR, blink, surprise, sc) {
  const c = this.ctx, k = this.el.width / 768;
  c.setTransform(1, 0, 0, 1, 0, 0); c.clearRect(0, 0, this.el.width, this.el.height); c.setTransform(k, 0, 0, k, 0, 0);
  const img = (s, dx, dy) => { const im = IM[s.k]; if (im && im.naturalWidth) c.drawImage(im, s.x + (dx || 0), s.y + (dy || 0), s.w, s.h); };
  img(ART.body);
  for (const [w, a] of [[ART.wingL, aL], [ART.wingR, aR]]) { c.save(); c.translate(w.pivot[0], w.pivot[1]); c.rotate(a * Math.PI / 180); c.translate(-w.pivot[0], -w.pivot[1]); img(w); c.restore(); }
  for (const [e, side] of [[ART.eyes.L, 1], [ART.eyes.R, -1]]) {
    const ex = clamp(gx * IRIS.x + side * cross * CROSS.x, -IRIS.x * 1.35, IRIS.x * 1.35) * sc, ey = clamp(gy * IRIS.y + cross * CROSS.y, -IRIS.y * 1.35, IRIS.y * 1.35) * sc;
    img(e.back); img(e.iris, ex, ey);
    c.fillStyle = e.pupil.fill; c.beginPath(); c.ellipse(e.pupil.cx + ex * PUPIL_DEPTH, e.pupil.cy + ey * PUPIL_DEPTH, e.pupil.rx, e.pupil.ry, 0, 0, 7); c.fill();
    for (const h of e.hl) { c.fillStyle = h.fill; c.beginPath(); c.ellipse(h.cx + ex * HL_DEPTH, h.cy + ey * HL_DEPTH, h.rx, h.ry, 0, 0, 7); c.fill(); }
  }
  img(ART.head);
  c.fillStyle = '#190a1f';
  for (const p of ART.penguin) { c.beginPath(); c.ellipse(p.cx + gx * PEN.x * sc, p.cy + gy * PEN.y * sc, p.rx, p.ry, p.ang * Math.PI / 180, 0, 7); c.fill(); }
  if (blink > 0.01) { c.globalAlpha = blink; img(ART.blink); c.globalAlpha = 1; }
  if (surprise > 0.01) { c.globalAlpha = surprise; img(ART.surprise); c.globalAlpha = 1; }
};
/** her line of text. It goes into the caption slot reserved for her (this.sayEl) - she never opens a bubble over content. */
Mascot.prototype.say = function (text, ms) {
  const b = this.sayEl; if (!b) return;
  if (b.dataset.def === undefined) b.dataset.def = b.innerHTML;
  b.textContent = text; b.classList.remove('talk'); void b.offsetWidth; b.classList.add('talk');
  clearTimeout(this._st); if (ms !== 0) this._st = setTimeout(() => this.hush(), ms || 2200);
};
Mascot.prototype.hush = function () { const b = this.sayEl; clearTimeout(this._st); if (b && b.dataset.def !== undefined) { b.innerHTML = b.dataset.def; b.classList.remove('talk'); } };

function loop(t) {
  raf = 0; const dt = clamp((t - last) / 1000, 0.001, 0.05); last = t; let any = false;
  for (const m of ALL) if (m.visible()) { if (m.frame(t, dt)) any = true; }
  if (any || (pointer && t - pointer.t < 3200)) kick(); else setTimeout(kick, 400);   // idle: wake up for blinks
}
function kick() { if (!raf) { raf = requestAnimationFrame(loop); } }
addEventListener('pointermove', (e) => { pointer = { x: e.clientX, y: e.clientY, t: performance.now() }; kick(); }, { passive: true });
addEventListener('pointerdown', (e) => { pointer = { x: e.clientX, y: e.clientY, t: performance.now() }; kick(); }, { passive: true });
last = performance.now();
window.Mascot = { snapshot: (px) => { const c = document.createElement('canvas'); c.width = c.height = px; const m = Object.create(Mascot.prototype); m.el = c; m.ctx = c.getContext('2d'); m.draw(0, 0, 0, 0, 0, 0, 0, 1); return c.toDataURL('image/png'); }, create: (host, o) => new Mascot(host, o), all: ALL, kick, ready: () => { load(); return Promise.all(Object.values(IM).map((im) => im.decode ? im.decode().catch(() => 0) : 0)); } };
})();
