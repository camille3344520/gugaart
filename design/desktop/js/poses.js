/*
 * [huabao] copied from ../fable-styles-b/js/poses.js; only a few props were added (marked).
 * Outline placeholders for the poses the layered sprite cannot do (new art will be generated later, see styles.md "生图清单").
 * One parametric line drawing of her: penguin hood with beak and round eyes, fringe, chibi 1 : 1.3 head / body, two flippers.
 * A pose = a few numbers (flipper angles, eyes, tilt, a prop); an action = a few poses played as frames.
 * Every placeholder sits in a slot reserved for her and carries a small caption saying what she is doing.
 */
(function () {
'use strict';
const APP = window.APP;
const EYES = {
  o: '<circle cx="51" cy="58" r="3.2" fill="currentColor" stroke="none"/><circle cx="69" cy="58" r="3.2" fill="currentColor" stroke="none"/>',
  '-': '<path d="M47 58q4 3.500 8 0M65 58q4 3.500 8 0"/>',
  '^': '<path d="M47 60q4-5.500 8 0M65 60q4-5.500 8 0"/>',
  O: '<circle cx="51" cy="58" r="4.600"/><circle cx="69" cy="58" r="4.600"/>',
  x: '<path d="M47.500 55l7 6m0-6l-7 6M65.500 55l7 6m0-6l-7 6"/>',
  u: '<circle cx="51" cy="55.500" r="3.200" fill="currentColor" stroke="none"/><circle cx="69" cy="55.500" r="3.200" fill="currentColor" stroke="none"/>',
};
const PROPS = {
  // [huabao] props for the editorial poses
  sheet: '<rect x="45" y="86" width="30" height="24" fill="rgba(255,255,255,.14)"/><path d="M50 93h20M50 99h20M50 105h12"/>',
  pen: '<path d="M90 64l14-16 4 3.500-14 16-6 2.500z" fill="rgba(255,255,255,.14)"/>',
  book: '<path d="M38 90q11-5 22 0q11-5 22 0v20q-11-5-22 0q-11-5-22 0z" fill="rgba(255,255,255,.14)"/><path d="M60 90v20"/>',
  crank0: '<circle cx="100" cy="80" r="10" stroke-dasharray="3 3"/><path d="M100 80l8-6" stroke-width="3"/><circle cx="108" cy="74" r="2.500" fill="currentColor"/>',
  crank1: '<circle cx="100" cy="80" r="10" stroke-dasharray="3 3"/><path d="M100 80l-6 8" stroke-width="3"/><circle cx="94" cy="88" r="2.500" fill="currentColor"/>',
  seal: '<rect x="88" y="58" width="18" height="8" rx="1.500" fill="#EA4E30" stroke="#EA4E30"/><path d="M97 58V46m-5 0h10"/>',
  ribbon: '<path d="M92 40v34l6-5 6 5V40z" fill="#EA4E30" stroke="#EA4E30"/>',
  tab: '<path d="M84 96h26v12H84z" fill="rgba(255,255,255,.14)"/><path d="M97 99v6m-3-3l3 3 3-3"/>',
  qm: '<path d="M97 30q0-7 7-7t7 7q0 4-7 7v4M104 47v1"/>',

  popcorn: '<path d="M48 88h24l-3 22H51z" fill="rgba(255,255,255,.14)"/><path d="M53 88v22M60 88v22M67 88v22"/><circle cx="52" cy="85" r="3.500"/><circle cx="60" cy="83" r="4"/><circle cx="68" cy="85" r="3.500"/>',
  star: '<path d="M98 20c1 7 4 10 11 11-7 1-10 4-11 11-1-7-4-10-11-11 7-1 10-4 11-11z" fill="#f6c453" stroke="#f6c453"/>',
  starhand: '<path d="M22 44c1 6 3 8 9 9-6 1-8 3-9 9-1-6-3-8-9-9 6-1 8-3 9-9z" fill="#f6c453" stroke="#f6c453"/>',
  punch: '<path d="M92 60l12-8m-12 8l13 5m-13-5l-7 3" stroke-width="3"/><path d="M108 46l4-4M112 54h5M108 70l4 3" stroke="#f6c453"/>',
  torch: '<path d="M90 70l14-6v12z"/><path d="M104 62l12-8M105 70h13M104 78l12 7" stroke="#f6c453" stroke-dasharray="2 3"/>',
  glass: '<circle cx="98" cy="62" r="10"/><path d="M105 70l9 10" stroke-width="3"/>',
  fish: '<path d="M14 40c6-7 15-7 21 0-6 7-15 7-21 0zm21 0l7-5v10z" fill="rgba(255,255,255,.14)"/>',
  stamp: '<rect x="86" y="54" width="20" height="9" rx="2"/><path d="M96 54V44m-5 0h10"/>',
  zzz: '<path d="M90 30h9l-9 10h9M104 16h7l-7 8h7" stroke-width="1.600"/>',
  note: '<path d="M96 40V24l10-2v15"/><circle cx="93" cy="40" r="3" fill="currentColor"/><circle cx="103" cy="37" r="3" fill="currentColor"/>',
  sweat: '<path d="M88 34q4 7 0 9q-4-2 0-9z" fill="rgba(255,255,255,.3)"/>',
  spin: '<path d="M32 22a30 12 0 0 1 56 0" stroke-dasharray="3 4"/><path d="M36 14l3 3-4 2M82 14l-3 3 4 2" />',
  wave: '',
};
/** one pose -> svg markup. o: {wl, wr: flipper angles (deg, + = raised), eyes, mouth: '-'|'o'|'D', tilt, dy, sx, sy, props: [], dive: 0..1} */
APP.pose = function (o) {
  o = o || {}; const wl = o.wl || 0, wr = o.wr || 0, t = o.tilt || 0, sx = o.sx || 1, sy = o.sy || 1;
  const mouth = o.mouth === 'o' ? '<ellipse cx="60" cy="67" rx="3" ry="3.600"/>' : o.mouth === 'D' ? '<path d="M54 64h12q-1 8-6 8t-6-8z" fill="rgba(255,255,255,.3)"/>' : '<path d="M57 66q3 2.500 6 0"/>';
  const girl = `<g transform="translate(60 112) rotate(${t}) scale(${sx} ${sy}) translate(-60 ${-112 + (o.dy || 0)})">
    <g transform="rotate(${wl} 42 86)"><ellipse cx="35" cy="98" rx="8" ry="17" fill="rgba(255,255,255,.1)"/></g>
    <g transform="rotate(${-wr} 78 86)"><ellipse cx="85" cy="98" rx="8" ry="17" fill="rgba(255,255,255,.1)"/></g>
    <path d="M38 113Q35 82 60 80q25 2 22 33z" fill="rgba(255,255,255,.1)"/><ellipse cx="60" cy="101" rx="13" ry="11.500" stroke-dasharray="2 3"/>
    <ellipse cx="60" cy="48" rx="34" ry="33" fill="rgba(255,255,255,.1)"/>
    <ellipse cx="60" cy="56" rx="23.500" ry="20" fill="rgba(255,255,255,.12)"/>
    <path d="M37.500 52Q60 37 82.500 52"/><path d="M40 60q-5 10 2 16M80 60q5 10-2 16" stroke-dasharray="2 3"/>
    <circle cx="47" cy="24" r="3.400"/><circle cx="73" cy="24" r="3.400"/><circle cx="47" cy="24" r="1" fill="currentColor"/><circle cx="73" cy="24" r="1" fill="currentColor"/>
    <path d="M52.500 30h15l-7.500 8.500z" fill="#f6c453" stroke="#f6c453"/>
    ${EYES[o.eyes || 'o']}${mouth}
    ${(o.props || []).filter((p) => p !== 'zzz' && p !== 'star' && p !== 'fish' && p !== 'spin').map((p) => PROPS[p]).join('')}
  </g>`;
  let inner = girl;
  const cid = 'pc' + (APP.__pc = (APP.__pc || 0) + 1);
  if (o.dive) inner = `<clipPath id="${cid}"><rect x="0" y="0" width="120" height="100"/></clipPath><g clip-path="url(#${cid})"><g transform="translate(0 ${o.dive * 74}) rotate(${o.dive >= 0.5 ? 180 : 0} 60 66)">${girl}</g></g><ellipse cx="60" cy="100" rx="${30 + o.dive * 16}" ry="${5 + o.dive * 3}" stroke-dasharray="4 4"/>${o.dive >= 0.5 ? '<path d="M34 92l-6-10M86 92l6-10M60 88V76" stroke="#8fd3ff"/>' : ''}`;
  const out = (o.props || []).filter((p) => p === 'zzz' || p === 'star' || p === 'fish' || p === 'spin').map((p) => PROPS[p]).join('');
  return `<svg class="pose-svg" viewBox="0 0 120 120" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${inner}${out}</svg>`;
};
/** swap the sprite for an outline action inside HER OWN box (host), play the frames, swap back. The caption goes to her text slot. */
APP.act = function (host, m, frames, o) {
  o = o || {}; let art = host.querySelector(':scope > .pose-art.over'); if (!art) { art = document.createElement('div'); art.className = 'pose-art over'; host.appendChild(art); }
  m.el.style.visibility = 'hidden'; art.style.display = 'block'; if (o.cap) m.say('（轮廓占位）' + o.cap, 0);
  APP.poseSeq(art, frames, { ms: o.ms, done: () => setTimeout(() => { art.style.display = 'none'; m.el.style.visibility = ''; m.hush(); if (o.done) o.done(); }, o.hold || 320) });
};
/** a reserved slot with an outline + a caption. html string. */
APP.poseBox = (cls, o, cap) => `<div class="pose ${cls || ''}"><div class="pose-art">${APP.pose(o)}</div><small><i>轮廓占位 · 待生图</i>${cap}</small></div>`;
/** play poses as frames inside a .pose-art (or any element). Returns a stop function. */
APP.poseSeq = function (art, frames, o) {
  o = o || {}; let i = 0; clearInterval(art.__seq);
  const show = () => { art.innerHTML = APP.pose(frames[i]); };
  show(); if (APP.shot && o.shotFrame !== undefined) { i = o.shotFrame; show(); return; }
  art.__seq = setInterval(() => { i++; if (i >= frames.length) { if (o.loop) i = 0; else { clearInterval(art.__seq); if (o.done) o.done(); return; } } show(); }, o.ms || 260);
};
})();
