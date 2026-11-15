/*
 * fable-b1 / phone kit: the pieces every screen shares. The cover flow (drag follows the finger, tilt, snap), pull-to-refresh,
 * the full-bleed hero (a picture from the very top of the screen fading into the ink-black paper), numbered lists, the seal and the proof tick.
 */
(function () {
'use strict';
const { ic, im, status, clamp, pad, esc } = APP;
const K = window.K = {};

// her lines and outline poses (placeholders for art that will be generated, see notes.md)
K.L = { fav: '盖章了：这一期归进你的收藏。', unfav: '章擦掉了。', load: '正在排版，片单马上印好。', none: '换个说法试试，或者把筛选放宽一点。', ask: '片名、人物、年份、题材都行，报个题我去翻。', ep: (n, nm) => `第 ${n} 集《${nm}》的稿子给你翻好了。` };
K.POSES = {
  load: { o: { wl: 25, wr: 55, eyes: '-', props: ['sheet'] }, cap: '排版：低头摆稿纸' },
  none: { o: { wl: 78, wr: 78, eyes: '-', mouth: 'o', tilt: -6, props: ['sweat'] }, cap: '摊手：两翅摊开、歪头' },
  ask: { o: { wr: 70, eyes: '^', mouth: 'D', props: ['pen', 'qm'] }, cap: '举笔：等你报题' },
  empty: { o: { eyes: 'u', mouth: 'o', props: ['sheet'] }, cap: '捧着一张白稿纸' },
  end: { o: { eyes: '-', tilt: 9, dy: 3, props: ['zzz'] }, cap: '截稿后趴着打盹' },
  off: { o: { wl: 60, wr: -10, eyes: 'x', mouth: 'o', tilt: 5, props: ['plug'] }, cap: '举着拔掉的电话线' },
  key: { o: { wr: 64, eyes: '^', mouth: '-', props: ['key'] }, cap: '递钥匙：举着一把钥匙' },
  extra: { o: { wl: 70, wr: 70, eyes: '^', mouth: 'D', props: ['paper'] }, cap: '号外：双手举着一张报' },
  bow: { o: { eyes: '-', mouth: '-', tilt: 0, dy: 5, wl: 10, wr: 10, props: ['fin'] }, cap: '谢幕：鞠躬' },
  err: { o: { wl: 40, wr: 40, eyes: 'x', mouth: 'o', tilt: -4, props: ['sweat'] }, cap: '稿子印坏了：抱头' },
};
K.poseArt = (name) => `<div class="pose-art" data-pose="${name}">${APP.pose(K.POSES[name].o)}</div>`;
K.poseBox = (name, text) => `<div class="pose">${K.poseArt(name)}<small><i>轮廓占位 · ${K.POSES[name].cap}</i>${text || ''}</small></div>`;
K.none = (pose, title, text, extra) => `<div class="b-none">${K.poseArt(pose)}<div><b>${title}</b><span>${text}</span><i>轮廓占位 · ${K.POSES[pose].cap}</i>${extra || ''}</div></div>`;

// ------------------------------------------------------------------ chrome
K.TABS = ['首页', '发现', '片库', '我的'];
K.tabbar = (sel, id, red) =>
  `<div class="g tabs" data-mat="bar" data-ind="tab" id="${id}" style="left:24px;top:831px;width:292px;height:56px">` +
  K.TABS.map((n, i) => `<a data-cell class="tab${i === sel ? ' on' : ''}"><span>${n}</span><i></i></a>`).join('') + `</div>` +
  `<div class="g${red ? ' on-red' : ''}" data-mat="clear" data-press style="left:332px;top:831px;width:56px;height:56px">${ic('search', 'font-size:24px')}</div>`;
/** the masthead. Same on every end: VOL. issue | 咕 咕 (penguin mark) 嘎 嘎 | date */
K.masthead = () => `<div class="b-mast app"><span>VOL. <i class="vol"><u>318<br>319</u></i></span><b>咕咕${im('emblem', 'emb')}嘎嘎</b><span>十一月十四日</span></div>`;
K.mast = (l, t, r) => `<div class="b-mast"><span>${l}</span><b>${t}</b><span>${r}</span></div>`;
K.band = (solidAt) => `<div class="b-band"${solidAt !== undefined ? ` data-solid="${solidAt}"` : ''}>${status()}</div>`;
K.top = (title, right, solid) => `<div class="b-top${solid === 'paper' ? ' paper' : ''}" ${solid === 'paper' ? '' : `data-solid="${solid || 150}"`}>${status()}<div class="b-nav"><a data-back>${ic('back')}</a><b class="tt">${title}</b><span>${right === undefined ? `<a data-favtop>${ic('heart')}</a><a>${ic('more')}</a>` : right}</span></div></div>`;
K.head = (cn, en, r) => `<div class="b-h"><b>${cn}</b><em>${en}</em><i></i>${r ? `<span>${r}</span>` : ''}</div>`;
K.thumb = (key, name, p, cls) => `<div class="th ${cls || ''}${key ? '' : ' none'}">${key ? im(key) : `<b>${esc(name || '')}</b>`}${p ? `<u style="width:${p}%"></u>` : ''}</div>`;
K.li = (no, t, s, img, p, cls, extra, alt) => `<a class="b-li ${cls || ''}"><em class="num${String(no).length > 3 ? ' n4' : String(no).length > 2 ? ' n3' : ''}">${no}</em><div><b>${t}</b><span>${s}</span></div>${K.thumb(img, alt, p)}${extra || ''}</a><div class="b-rule"></div>`;
K.nw = (t, i, sub) => `<a class="b-nw">${im(t.images ? t.images.poster : t.poster)}<div class="cap"><em class="num">${i}</em><b>${esc(t.name)}</b><span>${sub || `${t.year || '—'} · ${t.rating ? APP.score(t.rating) + ' 分' : '暂无评分'}`}</span></div></a>`;
K.po = (t, sub, extra) => `<a class="b-po">${im(t.images ? t.images.poster : t.poster)}${extra || ''}<b>${esc(t.name)}</b><span>${sub}</span></a>`;
K.favSlot = () => `<i class="fav-slot"><b>藏</b><u></u></i>`;
K.mk = () => `<i class="mk"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"><path pathLength="1" d="M3 12.5c2.5 2 4.200 4.500 5.500 7.500C11.500 13 15 8 21.500 3.500"/></svg><u></u></i>`;
/** the full-bleed hero: the picture starts at the very top of the screen (status bar and the buttons sit on it) and fades into the paper.
 *  o: { h (px), pos ("x% y%" = where the subject is, so the crop and the buttons miss the faces), cap (vertical caption), dim } */
K.hero = (key, o) => `<section class="b-hero" style="height:${o.h || 400}px">${im(key, 'hp', `object-position:${o.pos || '50% 30%'}`)}<i class="h-top"></i><i class="h-bot"></i>${o.cap ? `<div class="h-cap"><span>${o.cap[0]}</span>${o.cap[1]}</div>` : ''}</section>`;

// ------------------------------------------------------------------ the cover flow. o: { start, step (px per card), lay(card, d, tilt), onPos(pos, tilt), onIndex(i, first), onOpen(i) }
K.flow = (root, stage, o) => {
  const cards = [...stage.querySelectorAll('.cv')], n = cards.length, sc = root.querySelector('.scroll'), tilt = { x: 0, y: 0 }; let cur = -1, d = null;
  const draw = (pos) => {
    cards.forEach((c, j) => { o.lay(c, j - pos, tilt); c.classList.toggle('f', Math.abs(j - pos) < 0.5); });
    if (o.onPos) o.onPos(pos, tilt);
    const i = clamp(Math.round(pos), 0, n - 1); if (i !== cur) { const first = cur < 0; cur = i; o.onIndex(i, first); }
  };
  const s = APP.spring(draw, 150, 0.8), tx = APP.spring((v) => { tilt.x = v; draw(s.x); }, 240, 0.9), ty = APP.spring((v) => { tilt.y = v; draw(s.x); }, 240, 0.9);
  s.set(o.start);
  stage.addEventListener('pointerdown', (e) => { if (e.button === 0) d = { x: e.clientX, y: e.clientY, p: s.x, t: performance.now(), lx: e.clientX, v: 0, horiz: false }; });
  stage.addEventListener('pointermove', (e) => { if (d && d.horiz) return; const r = stage.getBoundingClientRect(); tx.to(clamp(((e.clientX - r.left) / r.width - 0.5) * 2, -1, 1)); ty.to(clamp(((e.clientY - r.top) / r.height - 0.5) * 2, -1, 1)); });
  stage.addEventListener('pointerleave', () => { tx.to(0); ty.to(0); });
  window.addEventListener('pointermove', (e) => {
    if (!d) return; const k = APP.scaleOf(stage), dx = (e.clientX - d.x) / k, dy = (e.clientY - d.y) / k;
    if (!d.horiz) { if (Math.abs(dy) > 8 && Math.abs(dy) > Math.abs(dx)) { d = null; return; } if (Math.abs(dx) < 6) return; d.horiz = true; if (sc) sc.__lock = true; stage.classList.add('drag'); }
    const now = performance.now(); d.v = 0.6 * d.v + 0.4 * ((e.clientX - d.lx) / k) / Math.max(1, now - d.t); d.lx = e.clientX; d.t = now;
    let p = d.p - dx / o.step; if (p < 0) p *= 0.35; if (p > n - 1) p = n - 1 + (p - (n - 1)) * 0.35;
    s.set(p); tx.to(clamp(-d.v * 1.2, -1, 1));
  });
  window.addEventListener('pointerup', () => {
    if (d && d.horiz) { s.to(clamp(Math.round(s.x - d.v * 170 / o.step), 0, n - 1)); stage.__swiped = performance.now(); if (sc) sc.__lock = false; stage.classList.remove('drag'); tx.to(0); }
    d = null;
  });
  cards.forEach((c, j) => c.addEventListener('click', () => { if (performance.now() - (stage.__swiped || 0) < 120) return; if (j !== cur) s.to(j); else if (o.onOpen) o.onOpen(j); }));
  return { go: (i) => s.to(clamp(i, 0, n - 1)), cur: () => cur, s, n, tilt };
};

// ------------------------------------------------------------------ pull to refresh. o: { pose(k) -> pose options, t: [pulling, armed, working], done() }
K.pullStrip = () => `<div class="b-pull"><div class="in"><div class="pose-art"></div><div><b></b><span>轮廓占位 · 待生图</span><div class="bar"><u></u></div></div></div></div>`;
K.pull = (root, o) => {
  const sc = root.querySelector('.scroll'), strip = root.querySelector('.b-pull'), art = strip.querySelector('.pose-art'), bar = strip.querySelector('.bar u'), tt = strip.querySelector('b'), cap = strip.querySelector('span');
  let h = 0, busy = false;
  const show = (hh) => { h = hh; strip.style.height = h + 'px'; root.classList.toggle('pulling', h > 0); const k = clamp(h / 72, 0, 1); bar.style.width = (k * 100) + '%'; art.innerHTML = APP.pose(o.pose(k, 0)); tt.textContent = k >= 1 ? o.t[1] : o.t[0]; cap.textContent = '轮廓占位 · ' + o.cap; APP.dirty(root); };
  const close = () => { strip.classList.add('snap'); strip.style.height = '0px'; h = 0; APP.anim(root, 500); setTimeout(() => { strip.classList.remove('snap'); root.classList.remove('pulling'); busy = false; }, 480); };
  sc.__pull = (dy) => {
    if (busy) return;
    if (dy === null) {
      if (h < 72) { close(); return; }
      busy = true; strip.classList.add('snap'); strip.style.height = '84px'; tt.textContent = o.t[2]; let f = 0;
      const iv = setInterval(() => { art.innerHTML = APP.pose(o.pose(1, ++f)); }, 150);
      setTimeout(() => { clearInterval(iv); o.done(); close(); }, 1200); return;
    }
    strip.classList.remove('snap'); show(Math.min(100, dy * 0.55));
  };
  root.__pullShow = show;
};

// ------------------------------------------------------------------ shared small behaviours: favourite = the editor's seal, watched = a proof-reader's tick, option rows
K.common = (root) => {
  if (root.__common) return; root.__common = 1;
  root.addEventListener('click', (e) => {
    const fav = e.target.closest('[data-fav],[data-favtop]');
    if (fav) {
      const on = !root.classList.contains('is-fav'); K.setFav(root, on, true); return;
    }
    const mk = e.target.closest('.mk');
    if (mk) { const row = mk.closest('.b-li'); row.classList.toggle('seen'); const m = APP.mascotOf(root); if (m) { m.lookAtEl(mk); m.blink(); setTimeout(() => m.lookAt(null), 1200); } e.stopPropagation(); return; }
    const ver = e.target.closest('.b-ver');
    if (ver) { root.querySelectorAll('.b-ver').forEach((x) => x.classList.toggle('on', x === ver)); const t = root.querySelector('.ver-t'); if (t) t.textContent = ver.dataset.t; return; }
  });
};
K.setFav = (root, on, talk) => {
  root.classList.toggle('is-fav', on);
  root.querySelectorAll('.fav-slot').forEach((s) => { s.classList.remove('on', 'off'); void s.offsetWidth; s.classList.add(on ? 'on' : (talk ? 'off' : 'x')); });
  root.querySelectorAll('[data-fav]').forEach((a) => { a.classList.toggle('on', on); const t = a.querySelector('.ft') || a; if (t.dataset.t0 === undefined) t.dataset.t0 = t.textContent; t.textContent = on ? '已收藏' : t.dataset.t0; });
  root.querySelectorAll('[data-favtop]').forEach((a) => { a.classList.toggle('faved', on); a.querySelector('use').setAttribute('href', on ? '#i-heartf' : '#i-heart'); });
  const m = APP.mascotOf(root); if (m && talk) { if (on) { m.setMood('cheer', 900); m.say(K.L.fav, 2600); } else m.press(); }
  APP.anim(root, 900);
};
K.scrollTo = (root, sel, off) => { const sc = root.querySelector('.scroll'), el = root.querySelector(sel); sc.scrollTop = 0; if (el) sc.scrollTop = (el.getBoundingClientRect().top - sc.getBoundingClientRect().top) / APP.scaleOf(sc) - (off === undefined ? 108 : off); sc.dispatchEvent(new Event('scroll')); };

// ------------------------------------------------------------------ derived pictures
K.prepare = () => {};
K.wash = (src) => { const key = 'wash_' + src; if (!window.IMG[key]) APP.blurOf(key, src, 618, 840, { b: 7, s: 1.15, l: 0.5 }); return key; };
})();
