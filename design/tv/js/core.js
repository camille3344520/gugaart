/*
 * demo3 core: tokens (native Tokens.kt), the material table (native GlassModel.kt, glass v4), text and icon drawing.
 * No glass numbers are invented here: every material is the row of docs/ui-wave brief section 1 = GlassModel.resolve().
 */
(function (G) {
'use strict';

// page registry (filled by phone.js / tv.js / desktop.js, used by app.js)
G.pages = [];
G.addPage = function (spec) { G.pages.push(spec); };

// ------------------------------------------------------------------ tokens (Tokens.kt, curated list)
G.TOK = {
  dark: {
    bg: '#08080A', bg2: '#101016', text: '#FFFFFF', dim: '#D6D6DE', faint: '#B4B4BE', disabled: '#6A6A74',
    border: 'rgba(255,255,255,0.12)', fill1: 'rgba(255,255,255,0.09)', fill2: 'rgba(255,255,255,0.16)', mask: 0.60,
    promTop: 'rgba(255,255,255,0.95)', promBot: 'rgba(255,255,255,0.86)', promFg: '#14141A',
    blue: '#3D8BFF', pill: 'rgba(255,255,255,0.10)', danger: '#FF453A', rating: '#F5C84B', ring: '#E6D9BF',
    card: 'rgba(255,255,255,0.09)', sep: 'rgba(255,255,255,0.10)',
  },
  light: {
    bg: '#EFEEEC', bg2: '#E4E3E0', text: '#1A1A1F', dim: '#3E3E46', faint: '#4C4C54', disabled: '#A0A0A8',
    border: 'rgba(0,0,0,0.09)', fill1: 'rgba(0,0,0,0.06)', fill2: 'rgba(255,255,255,0.7)', mask: 0.34,
    promTop: 'rgba(40,40,46,0.95)', promBot: 'rgba(18,18,22,0.93)', promFg: '#FFFFFF',
    blue: '#1F6FEB', pill: 'rgba(0,0,0,0.10)', danger: '#FF3B30', rating: '#C99A0A', ring: '#1A1A1F',
    card: 'rgba(255,255,255,0.72)', sep: 'rgba(0,0,0,0.08)',
  },
};
G.FONT = '"PingFang SC","HarmonyOS Sans SC","Noto Sans SC","Microsoft YaHei",system-ui,sans-serif';
G.hex = (c) => { const m = /^#([0-9a-f]{6})$/i.exec(c); return m ? [1, 3, 5].map((i) => parseInt(c.slice(i, i + 2), 16)) : [0, 0, 0]; };
G.mix = (a, b, t) => { const A = G.hex(a), B = G.hex(b); return `rgb(${A.map((v, i) => Math.round(v + (B[i] - v) * t)).join(',')})`; };
G.bgRgb = (dark) => { const a = G.hex(dark ? G.TOK.dark.bg : G.TOK.light.bg), b = G.hex(dark ? G.TOK.dark.bg2 : G.TOK.light.bg2); return a.map((v, i) => (v + b[i]) / 2 / 255); };

// ------------------------------------------------------------------ materials (GlassModel.kt; blur sigma dp, thickness library units)
const BACK_DARK = [0.50, 0.10, 0.70, 1];   // LEGIBILITY_BACKER.DARK  (darken)
const BACK_LIGHT = [0.50, 0.35, 0.85, -1]; // LEGIBILITY_BACKER.LIGHT (lighten)
G.MATERIALS = {
  bar:   { blur: 1.5, th: 30, n: 1.59, ca: 0.30, tint: [1, 1, 1, 0.12], lit: [0.6, 1.0, 0.7, 1.0], backer: true },
  clear: { blur: 2.5, th: 20, n: 1.20, ca: 0.01, tint: [1, 1, 1, 0.0],  lit: [0.5, 0.0, 1.5, 1.0], backer: false },
  thumb: { blur: 0,   th: 20, n: 1.10, ca: 0.00, tint: [1, 1, 1, 0.0],  lit: [2.0, 0.0, 1.5, 1.0], backer: false },
  panelD:{ blur: 20,  th: 30, n: 1.20, ca: 0.01, tint: [0.1, 0.1, 0.1, 0.70],    lit: [0.70, 0.0, 1.2, 1.0], backer: true },
  panelL:{ blur: 20,  th: 30, n: 1.20, ca: 0.02, tint: [0.97, 0.97, 0.97, 0.72], lit: [0.85, 0.15, 1.2, 1.0], backer: true },
};
// returns the engine fields of one named material in a theme (GlassModel.resolve)
G.mat = function (name, dark) {
  const M = G.MATERIALS; let m, vis = 1, blur;
  if (name === 'bar') m = M.bar;
  else if (name === 'clear') m = M.clear;
  else if (name === 'off') { m = M.clear; vis = 0.5; }
  else if (name === 'thumb') m = M.thumb;
  else if (name === 'panel') m = dark ? M.panelD : M.panelL;
  else if (name === 'pop') { m = dark ? M.panelD : M.panelL; blur = 24; }
  else throw new Error('unknown material ' + name);
  return { blur: blur !== undefined ? blur : m.blur, th: m.th, n: m.n, ca: m.ca, tint: m.tint, lit: m.lit, vis, back: m.backer ? (dark ? BACK_DARK : BACK_LIGHT) : null };
};

// the material BETWEEN two named materials (the glass morph): every uniform of the v4 shader is interpolated, the formula is not touched.
// k = 0 -> a (e.g. the circle's Clear), k = 1 -> b (the panel's Pop). The text-contrast backer's peak is interpolated too (0 when a material has none).
G.matMix = function (a, b, k, dark) {
  const A = G.mat(a, dark), B = G.mat(b, dark), L = (x, y) => x + (y - x) * k, V = (x, y) => x.map((v, i) => L(v, y[i]));
  const bk = A.back || B.back;
  return { blur: L(A.blur, B.blur), th: L(A.th, B.th), n: L(A.n, B.n), ca: L(A.ca, B.ca), tint: V(A.tint, B.tint), lit: V(A.lit, B.lit), vis: L(A.vis, B.vis),
    back: bk ? [L(A.back ? A.back[0] : 0, B.back ? B.back[0] : 0), bk[1], bk[2], bk[3]] : null };
};
G.slow = 1;

// ------------------------------------------------------------------ small 2D helpers
G.rr = function (c, x, y, w, h, r) { c.beginPath(); if (c.roundRect) c.roundRect(x, y, w, h, Math.min(r, w / 2, h / 2)); else c.rect(x, y, w, h); };
G.font = (size, weight) => `${weight || 400} ${size}px ${G.FONT}`;
G.text = function (c, s, x, y, o) {
  o = o || {};
  c.save();
  c.font = G.font(o.s || 14, o.w); c.fillStyle = o.c || '#fff'; c.textAlign = o.a || 'left'; c.textBaseline = o.b || 'middle';
  if (o.sh) { c.shadowColor = 'rgba(0,0,0,0.6)'; c.shadowBlur = o.sh; c.shadowOffsetY = 1; }
  if (o.alpha !== undefined) c.globalAlpha = o.alpha;
  if (o.max) s = G.fit(c, s, o.max);
  c.fillText(s, x, y);
  c.restore();
};
G.measure = function (c, s, size, weight) { c.save(); c.font = G.font(size, weight); const w = c.measureText(s).width; c.restore(); return w; };
G.fit = function (c, s, max) {
  if (c.measureText(s).width <= max) return s;
  let lo = 0, hi = s.length;
  while (lo < hi) { const mid = (lo + hi + 1) >> 1; if (c.measureText(s.slice(0, mid) + '…').width <= max) lo = mid; else hi = mid - 1; }
  return s.slice(0, lo) + '…';
};
// greedy wrap by character (Chinese) with an ellipsis on the last allowed line
G.wrap = function (c, s, size, weight, max, lines) {
  c.save(); c.font = G.font(size, weight);
  const out = []; let cur = '';
  for (const ch of s) {
    if (c.measureText(cur + ch).width > max) { out.push(cur); cur = ch; if (out.length === lines) break; } else cur += ch;
  }
  if (out.length < lines && cur) out.push(cur);
  if (out.length === lines && (cur && out.join('') !== s)) { let l = out[lines - 1]; while (c.measureText(l + '…').width > max && l.length) l = l.slice(0, -1); out[lines - 1] = l + '…'; }
  c.restore();
  return out;
};
G.sstep = (a, b, x) => { const t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
G.clamp = (v, a, b) => Math.max(a, Math.min(b, v));
G.rnd = function (seed) { let s = (seed * 9301 + 49297) % 233280; return () => (s = (s * 9301 + 49297) % 233280) / 233280; };

// ------------------------------------------------------------------ icons (24 x 24 box; our own simple line set, stand-ins for GuguIcon)
const gear = (() => {
  let d = ''; for (let i = 0; i < 8; i++) { const a = i * Math.PI / 4; d += `M${(12 + Math.cos(a) * 7.4).toFixed(2)} ${(12 + Math.sin(a) * 7.4).toFixed(2)} L${(12 + Math.cos(a) * 10).toFixed(2)} ${(12 + Math.sin(a) * 10).toFixed(2)} `; }
  return d + 'M18.3 12 A6.3 6.3 0 1 1 5.7 12 A6.3 6.3 0 1 1 18.3 12 Z M14.6 12 A2.6 2.6 0 1 1 9.4 12 A2.6 2.6 0 1 1 14.6 12 Z';
})();
const IC = {
  home: { s: 'M3.5 11.2 L12 3.8 L20.5 11.2 M5.8 9.6 V19.8 H18.2 V9.6 M9.8 19.8 V14 H14.2 V19.8' },
  history: { s: 'M3.6 12 A8.4 8.4 0 1 0 6.1 6.1 M3.4 3.6 V7.6 H7.4 M12 7.6 V12.4 L15.4 14.4' },
  server: { s: 'M5 4 H19 A1.5 1.5 0 0 1 20.5 5.5 V9.5 A1.5 1.5 0 0 1 19 11 H5 A1.5 1.5 0 0 1 3.5 9.5 V5.5 A1.5 1.5 0 0 1 5 4 Z M5 13 H19 A1.5 1.5 0 0 1 20.5 14.5 V18.5 A1.5 1.5 0 0 1 19 20 H5 A1.5 1.5 0 0 1 3.5 18.5 V14.5 A1.5 1.5 0 0 1 5 13 Z', d: [[7, 7.5, 1], [7, 16.5, 1]] },
  settings: { s: gear },
  search: { s: 'M18 11 A7 7 0 1 0 4 11 A7 7 0 1 0 18 11 Z M16.2 16.2 L21 21' },
  more: { d: [[5, 12, 1.9], [12, 12, 1.9], [19, 12, 1.9]] },
  back: { s: 'M19.5 12 H4.8 M10.8 6 L4.8 12 L10.8 18' },
  chevR: { s: 'M9 5.5 L15.5 12 L9 18.5' }, chevL: { s: 'M15 5.5 L8.5 12 L15 18.5' },
  chevD: { s: 'M5.5 9 L12 15.5 L18.5 9' }, chevU: { s: 'M5.5 15 L12 8.5 L18.5 15' },
  heart: { s: 'M12 20.4 C5 15.4 3 12.4 3 9.2 C3 6.4 5.1 4.5 7.6 4.5 C9.5 4.5 11 5.5 12 7 C13 5.5 14.5 4.5 16.4 4.5 C18.9 4.5 21 6.4 21 9.2 C21 12.4 19 15.4 12 20.4 Z' },
  heartF: { f: 'M12 20.4 C5 15.4 3 12.4 3 9.2 C3 6.4 5.1 4.5 7.6 4.5 C9.5 4.5 11 5.5 12 7 C13 5.5 14.5 4.5 16.4 4.5 C18.9 4.5 21 6.4 21 9.2 C21 12.4 19 15.4 12 20.4 Z', fs: 1 },
  check: { s: 'M4.8 12.6 L9.8 17.6 L19.4 7' },
  star: { f: 'M12 3 L14.6 9 L21 9.6 L16.1 13.9 L17.6 20.3 L12 17 L6.4 20.3 L7.9 13.9 L3 9.6 L9.4 9 Z', fs: 1 },
  play: { f: 'M7.5 4.8 L19 12 L7.5 19.2 Z', fs: 1 },
  pause: { f: 'M6.5 5 H10.2 V19 H6.5 Z M13.8 5 H17.5 V19 H13.8 Z', fs: 1 },
  skipN: { s: 'M6 5.5 L16.5 12 L6 18.5 Z M19 5 V19' },
  skipP: { s: 'M18 5.5 L7.5 12 L18 18.5 Z M5 5 V19' },
  shuffle: { s: 'M3 7 H6.5 C12 7 11.5 17 17.5 17 H21 M18.2 14.2 L21 17 L18.2 19.8 M3 17 H6.5 C8.2 17 9.3 16.2 10.2 15 M13.8 9 C14.7 7.8 15.8 7 17.5 7 H21 M18.2 4.2 L21 7 L18.2 9.8' },
  sort: { s: 'M7.5 19 V5 M3.8 8.7 L7.5 5 L11.2 8.7 M16.5 5 V19 M12.8 15.3 L16.5 19 L20.2 15.3' },
  filter: { s: 'M3.5 7 H6.6 M11.4 7 H20.5 M3.5 17 H12.6 M17.4 17 H20.5 M11.4 7 A2.4 2.4 0 1 0 6.6 7 A2.4 2.4 0 1 0 11.4 7 Z M17.4 17 A2.4 2.4 0 1 0 12.6 17 A2.4 2.4 0 1 0 17.4 17 Z' },
  lock: { s: 'M6 11 H18 A1.5 1.5 0 0 1 19.5 12.5 V19 A1.5 1.5 0 0 1 18 20.5 H6 A1.5 1.5 0 0 1 4.5 19 V12.5 A1.5 1.5 0 0 1 6 11 Z M8 11 V8 A4 4 0 0 1 16 8 V11' },
  rotate: { s: 'M20.2 12 A8.2 8.2 0 1 1 17.8 6.2 M20.4 3.6 V8.4 H15.6' },
  refresh: { s: 'M20.2 12 A8.2 8.2 0 1 1 17.8 6.2 M20.4 3.6 V8.4 H15.6' },
  chat: { s: 'M4.5 5 H19.5 A1.5 1.5 0 0 1 21 6.5 V15 A1.5 1.5 0 0 1 19.5 16.5 H12 L7.5 20.5 V16.5 H4.5 A1.5 1.5 0 0 1 3 15 V6.5 A1.5 1.5 0 0 1 4.5 5 Z M7.5 9.2 H16.5 M7.5 12.4 H13' },
  subs: { s: 'M5 5.5 H19 A2 2 0 0 1 21 7.5 V16.5 A2 2 0 0 1 19 18.5 H5 A2 2 0 0 1 3 16.5 V7.5 A2 2 0 0 1 5 5.5 Z M7 11 H10.5 M12.7 11 H17 M7 14.5 H8.8 M11 14.5 H17' },
  audio: { s: 'M4 10 V14 M7.5 6.5 V17.5 M11 3.8 V20.2 M14.5 7.5 V16.5 M18 5 V19 M21 10.5 V13.5' },
  speed: { s: 'M4.2 17.5 A8.8 8.8 0 1 1 19.8 17.5 M12 14.5 L16.2 9.5' },
  queue: { s: 'M3 6.5 H15 M3 11.5 H15 M3 16.5 H9.5 M13.5 13.8 L20.5 17.5 L13.5 21.2 Z' },
  camera: { s: 'M4.5 8 H8 L9.6 5.5 H14.4 L16 8 H19.5 A1.5 1.5 0 0 1 21 9.5 V18 A1.5 1.5 0 0 1 19.5 19.5 H4.5 A1.5 1.5 0 0 1 3 18 V9.5 A1.5 1.5 0 0 1 4.5 8 Z M15.2 13.6 A3.2 3.2 0 1 0 8.8 13.6 A3.2 3.2 0 1 0 15.2 13.6 Z' },
  volume: { s: 'M3.5 9.5 H7.5 L12.5 5.5 V18.5 L7.5 14.5 H3.5 Z M15.5 9 A4.2 4.2 0 0 1 15.5 15 M18.2 6.2 A8 8 0 0 1 18.2 17.8' },
  sidebar: { s: 'M5 4.5 H19 A1.5 1.5 0 0 1 20.5 6 V18 A1.5 1.5 0 0 1 19 19.5 H5 A1.5 1.5 0 0 1 3.5 18 V6 A1.5 1.5 0 0 1 5 4.5 Z M9.5 4.5 V19.5' },
  close: { s: 'M6 6 L18 18 M18 6 L6 18' },
  menu: { s: 'M4 7 H20 M4 12 H20 M4 17 H20' },
  user: { s: 'M16 8 A4 4 0 1 1 8 8 A4 4 0 1 1 16 8 Z M4.5 20 C4.8 16 8 13.8 12 13.8 C16 13.8 19.2 16 19.5 20' },
  replay: { s: 'M3.8 12 A8.2 8.2 0 1 0 6.2 6.2 M3.6 3.8 V8.8 H8.6' },
  film: { s: 'M5.5 4 H18.5 A1.5 1.5 0 0 1 20 5.5 V18.5 A1.5 1.5 0 0 1 18.5 20 H5.5 A1.5 1.5 0 0 1 4 18.5 V5.5 A1.5 1.5 0 0 1 5.5 4 Z M8.7 4 V20 M15.3 4 V20 M4 9.3 H8.7 M4 14.7 H8.7 M15.3 9.3 H20 M15.3 14.7 H20' },
  clapper: { s: 'M4 10 H20 V19 A1 1 0 0 1 19 20 H5 A1 1 0 0 1 4 19 Z M4 10 L3.6 6.8 L18.2 4.4 L19 7.6 M8 6.2 L10.2 9.8 M12.7 5.4 L14.8 9' },
  thumbU: { s: 'M7.5 10.5 V20 H4.5 A1 1 0 0 1 3.5 19 V11.5 A1 1 0 0 1 4.5 10.5 Z M7.5 10.5 L11.3 4 C13 4 14 5.4 13.6 7 L13 9.5 H18.8 C20.2 9.5 21.2 10.8 20.9 12.1 L19.7 18.3 C19.5 19.3 18.6 20 17.6 20 H7.5' },
  thumbD: { s: 'M7.5 10.5 V20 H4.5 A1 1 0 0 1 3.5 19 V11.5 A1 1 0 0 1 4.5 10.5 Z M7.5 10.5 L11.3 4 C13 4 14 5.4 13.6 7 L13 9.5 H18.8 C20.2 9.5 21.2 10.8 20.9 12.1 L19.7 18.3 C19.5 19.3 18.6 20 17.6 20 H7.5', flip: 1 },
  palette: { s: 'M12 3.5 A8.5 8.5 0 1 0 12 20.5 C13.6 20.5 14.2 19.4 13.6 18.4 C13 17.4 13.6 16 15.2 16 H17.5 A3.5 3.5 0 0 0 21 12.5 C21 7.5 17 3.5 12 3.5 Z', d: [[7.5, 12, 1.2], [9.5, 7.8, 1.2], [14.2, 7.4, 1.2], [17, 10.5, 1.2]] },
  bolt: { s: 'M13.2 3 L5.2 13.6 H11.2 L10.4 21 L18.8 10.2 H12.8 Z' },
  archive: { s: 'M4.5 4.5 H19.5 A1 1 0 0 1 20.5 5.5 V8.5 H3.5 V5.5 A1 1 0 0 1 4.5 4.5 Z M5 8.5 V18.5 A1 1 0 0 0 6 19.5 H18 A1 1 0 0 0 19 18.5 V8.5 M9.8 12.5 H14.2' },
  puzzle: { s: 'M10 4.5 V6.5 A1.8 1.8 0 1 0 13.6 6.5 V4.5 H19.5 V10.2 H17.5 A1.8 1.8 0 1 0 17.5 13.8 H19.5 V19.5 H13.6 V17.5 A1.8 1.8 0 1 0 10 17.5 V19.5 H4.5 V4.5 Z' },
  info: { s: 'M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 Z M12 11 V16.5', d: [[12, 7.8, 1.1]] },
  tv: { s: 'M5.5 7.5 H18.5 A1.5 1.5 0 0 1 20 9 V18.5 A1.5 1.5 0 0 1 18.5 20 H5.5 A1.5 1.5 0 0 1 4 18.5 V9 A1.5 1.5 0 0 1 5.5 7.5 Z M8.8 3.8 L12 7.5 L15.2 3.8' },
  monitor: { s: 'M5 4.5 H19 A1.5 1.5 0 0 1 20.5 6 V15 A1.5 1.5 0 0 1 19 16.5 H5 A1.5 1.5 0 0 1 3.5 15 V6 A1.5 1.5 0 0 1 5 4.5 Z M8.5 20 H15.5 M12 16.5 V20' },
  sun: { s: 'M16 12 A4 4 0 1 1 8 12 A4 4 0 1 1 16 12 Z M12 3 V5 M12 19 V21 M3 12 H5 M19 12 H21 M5.6 5.6 L7 7 M17 17 L18.4 18.4 M5.6 18.4 L7 17 M17 7 L18.4 5.6' },
  moon: { s: 'M20 14.8 A8.4 8.4 0 1 1 9.2 4 A6.6 6.6 0 0 0 20 14.8 Z' },
  folder: { s: 'M3.5 6.5 A1.5 1.5 0 0 1 5 5 H9.5 L11.5 7.5 H19 A1.5 1.5 0 0 1 20.5 9 V17.5 A1.5 1.5 0 0 1 19 19 H5 A1.5 1.5 0 0 1 3.5 17.5 Z' },
  plus: { s: 'M12 5 V19 M5 12 H19' },
  winMin: { s: 'M5 12 H19' },
  winMax: { s: 'M6 6 H18 V18 H6 Z' },
  logout: { s: 'M9 4.5 H5.5 A1 1 0 0 0 4.5 5.5 V18.5 A1 1 0 0 0 5.5 19.5 H9 M15 8 L19.5 12 L15 16 M19.5 12 H9' },
};
const PATHS = {};
G.icon = function (c, name, cx, cy, size, color, o) {
  const d = IC[name]; if (!d) return;
  o = o || {};
  if (!PATHS[name]) PATHS[name] = { s: d.s ? new Path2D(d.s) : null, f: d.f ? new Path2D(d.f) : null };
  c.save(); c.translate(cx, cy); const sc = size / 24; c.scale(sc, sc); if (d.flip) c.rotate(Math.PI); c.translate(-12, -12);
  if (o.alpha !== undefined) c.globalAlpha = o.alpha;
  c.strokeStyle = color; c.fillStyle = color; c.lineWidth = o.lw || 2; c.lineCap = 'round'; c.lineJoin = 'round';
  if (d.f) { c.fill(PATHS[name].f); if (d.fs) { c.lineWidth = o.lw ? o.lw * 0.8 : 1.6; c.stroke(PATHS[name].f); } }
  if (d.s) c.stroke(PATHS[name].s);
  if (o.fill && d.s) c.fill(PATHS[name].s);
  if (d.d) for (const [x, y, r] of d.d) { c.beginPath(); c.arc(x, y, r, 0, 7); c.fill(); }
  c.restore();
};
G.hasIcon = (n) => !!IC[n];

// avatar (a drawn mascot, not a server picture)
G.avatar = function (c, cx, cy, r) {
  c.save();
  const g = c.createLinearGradient(cx - r, cy - r, cx + r, cy + r); g.addColorStop(0, '#5fb35f'); g.addColorStop(1, '#2d6aa8');
  c.fillStyle = g; c.beginPath(); c.arc(cx, cy, r, 0, 7); c.fill();
  c.beginPath(); c.arc(cx, cy, r - 3, 0, 7); c.clip();
  c.fillStyle = '#e8f1f8'; c.fillRect(cx - r, cy - r, r * 2, r * 2);
  c.fillStyle = '#1d1d24'; c.beginPath(); c.ellipse(cx, cy - r * 0.22, r * 0.78, r * 0.62, 0, 0, 7); c.fill();           // hair
  c.fillStyle = '#f6d9c8'; c.beginPath(); c.ellipse(cx, cy - r * 0.05, r * 0.5, r * 0.42, 0, 0, 7); c.fill();           // face
  c.fillStyle = '#1d1d24'; c.beginPath(); c.arc(cx - r * 0.18, cy - r * 0.05, r * 0.06, 0, 7); c.arc(cx + r * 0.18, cy - r * 0.05, r * 0.06, 0, 7); c.fill();
  c.fillStyle = '#ff8aa0'; c.beginPath(); c.arc(cx - r * 0.3, cy + r * 0.1, r * 0.07, 0, 7); c.arc(cx + r * 0.3, cy + r * 0.1, r * 0.07, 0, 7); c.fill();
  c.fillStyle = '#2b2b33'; c.beginPath(); c.ellipse(cx, cy + r * 0.95, r * 0.85, r * 0.55, 0, Math.PI, 0); c.fill();     // body
  c.fillStyle = '#fff'; c.beginPath(); c.ellipse(cx, cy + r * 0.95, r * 0.35, r * 0.5, 0, Math.PI, 0); c.fill();
  c.restore();
};

// status bar (phone): time, wifi, signal, battery
G.statusBar = function (c, W, fg, time) {
  c.save(); c.fillStyle = fg; c.strokeStyle = fg;
  G.text(c, time || '11:19', 18, 24, { s: 14, w: 500, c: fg });
  const bx = W - 20;
  c.beginPath(); c.roundRect ? c.roundRect(bx - 8, 17, 8, 14, 2) : c.rect(bx - 8, 17, 8, 14); c.fill();      // battery
  c.beginPath(); c.moveTo(bx - 38, 31); c.lineTo(bx - 20, 31); c.lineTo(bx - 20, 17); c.closePath(); c.fill(); // signal
  c.beginPath(); c.moveTo(bx - 58, 20); c.arc(bx - 48, 31, 13, -2.35, -0.79); c.lineTo(bx - 48, 31); c.closePath(); c.fill(); // wifi
  c.restore();
};
})(window.G3 = window.G3 || {});
