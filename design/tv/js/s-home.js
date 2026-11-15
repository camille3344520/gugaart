/* 首页：封面区（三维海报流；焦点在目录里时换成焦点预览）+ 目录区（继续观看 / 最近添加 / 媒体库）；导航展开；再按一次返回退出；网络断开 */
(function () {
'use strict';
const APP = window.APP, U = window.U, L = window.LIB, { ic, im, esc, pad } = APP, F = APP.F;
// 本期封面：10 期，故意混了难的：没有 logo（男人没有好东西）、黑色笔画 logo（头文字D、网球王子）、10 分、1215 集、超长简介
const ISS = [['谷雨', '张颂文一开口，九十年代就回来了。'], ['航海王', '一千二百一十五集，我按季给你码好了。'], ['颜心记', '你停在第 5 集的 26 分半，正要组队。'], ['男人没有好东西', '这部没有艺术字片名，我用宋体排了一个。'],
  ['女子当参政', '全库唯一的满分，还剩一个多小时。'], ['头文字D', '第一集才看了两分钟，豆腐还没送到。'], ['盗梦空间', '陀螺停下之前，别急着醒。'], ['大话西游之月光宝盒', '一九九五年的旧刊，越放越香。'], ['网球王子', '一百七十六集，你看到第 3 集。'], ['龙珠大魔', '二十集全看完了，要不要再翻一遍？']]
  .map((x) => ({ o: U.det(x[0]), note: x[1] }));
const START = 2;
const cwCard = (c, i) => { const still = U.I(c, 'thumb') || U.I(c, 'poster'), p = c.user.playedPct || 0, rem = c.user.positionSec != null ? `余 ${Math.round((c.runtimeSec - c.user.positionSec) / 60)} 分` : U.dur(c.runtimeSec);
  return U.wide({ img: still, no: c.episode ? 'E' + c.episode : '', alt: c.seriesName || c.name, title: c.seriesName || c.name, sub: (c.type === 'Episode' ? `第 ${c.episode} 集 · ` : '') + rem + (p ? ` · ${Math.round(p)}%` : ' · 未开始'), p, attr: `data-cw="${i}" data-menu="menu"` }); };
const row = (no, cn, en, right, cards, snap, cls) => `<section class="rw ${cls || ''}" data-snap="0">${U.sec(no, cn, en, right)}<div class="hs" data-lead data-pad="136" data-fg>${cards}</div></section>`;
const LIBS = L.libraries;
const render = (st) => {
  const c0 = ISS[START].o;
  const rows = row('01', '继续观看', 'CONTINUE', `${L.continueWatching.length} 部在读`, L.continueWatching.map(cwCard).join('')) +
    ['电视剧', '电影', '动漫', 'emby302'].map((n, i) => row(pad(i + 2), '最近添加 · ' + n, 'NEW IN', `${LIBS.find((l) => l.name === n).titles} 部`, L.latest[n].map((o) => U.poster(o, { attr: 'data-menu="menu"' })).join(''))).join('') +
    row('06', '媒体库', 'LIBRARIES', '5 个', LIBS.map((l, i) => `<a class="card wd lib" data-f data-lib="${i}"><span class="pic">${im(l.image)}</span><span class="cap"><b>${esc(l.name)}</b><small>${l.titles} 部${l.episodes > l.titles ? ` · ${l.episodes.toLocaleString()} 集` : ''}</small></span></a>`).join('')) +
    `<section class="rw end" data-snap="0"><div class="endline">${U.pose({ eyes: '-', tilt: 9, dy: 3, props: ['zzz'] }, '截稿后趴着打盹', '本期到此。共 232 部，由你的片库自动编排；主编咕咕，明晚九点见。')}</div></section>`;
  return `<div class="hm">
  <div class="bdw"><img class="bd" data-img="${APP.key(U.I(c0, 'backdrop'))}" data-pre="${ISS.map((x) => APP.key(U.I(x.o, 'backdrop'))).join(',')},${ISS.map((x) => APP.key(U.I(x.o, 'logo')) || '').join(',')}" alt=""><div class="sh1"></div><div class="sh2"></div></div>
  ${U.mast()}
  ${st === 'offline' ? `<div class="extra"><b>号外</b><span>与服务器的连接断开了，正在重连（第 2 次）。下面是上次印好的版面，海报可能不是最新。</span><a class="btn" data-f data-f0 id="retry">${ic('replay')}<span>立即重试</span></a><a class="btn" data-f>${ic('set')}<span>换服务器</span></a></div>` : ''}
  <section class="cover">
    <div class="glow"></div>
    <div class="stage" id="flow" data-f ${/^(|nav|next|fav|exit|press)$/.test(st) ? 'data-f0' : ''} data-down="#cta-play" data-up="#vol">${ISS.map((x, i) => `<div class="cv" data-i="${i}">${im(U.I(x.o, 'poster'))}</div>`).join('')}</div>
    <div class="ticks">${ISS.map(() => '<i></i>').join('')}</div>
    <div class="no"><small>本期封面 · COVER STORY</small><b class="num"><span class="roll">${ISS.map((_, i) => pad(i + 1)).join('<br>')}</span></b><em>/ ${pad(ISS.length)}</em></div>
    <div class="kick kt"></div>
    <div class="lgw"></div>
    <p class="stand"></p>
    <div class="cta" data-fg><a class="btn pri" data-f id="cta-play" data-up="#flow">${ic('play')}<span class="cta-t"></span><small class="left-t"></small><i class="pg"><u></u></i></a><a class="btn" data-f data-up="#flow" data-act="detail">${ic('info')}<span>详情</span></a>${U.favBtn(false).replace('data-f', 'data-f data-up="#flow"')}</div>
    <div class="nt">${U.note('', {})}<div class="pressbox">${U.pose({ wr: 60, wl: 10, eyes: '^', mouth: 'D', props: ['crank1'] }, '摇印刷机手柄', '付印中……新刊马上送到。')}<i class="bar"><u></u></i></div></div>
  </section>
  <section class="pv"><div class="pv-k kick"></div><div class="pv-lg"></div><div class="pv-m"></div><p class="pv-s"></p><div class="sheet"><img alt="" draggable="false"></div></section>
  <div class="rows vs">${rows}</div>
</div>`;
};
const init = (root, st) => {
  const $ = (s) => root.querySelector(s), cover = $('.cover'), roll = $('.roll'), ticks = [...root.querySelectorAll('.ticks i')], say = $('.cover .say'), bd = $('.bd');
  let m = APP.mascot(root, '.cover .msc');
  const put = (i) => { const x = ISS[i], o = x.o, c = U.cta(o);
    $('.lgw').innerHTML = U.logo(o); APP.fill(root); APP.fixLogos(root);
    $('.kt').textContent = U.kick(o) + (o.rating ? ` · ★ ${U.rate(o.rating)}` : ''); $('.stand').textContent = U.stand(o, 46);
    $('.cta-t').textContent = c.t; $('.left-t').textContent = c.left; $('#cta-play .pg').style.display = c.p ? '' : 'none'; $('#cta-play .pg u').style.width = c.p + '%';
    say.textContent = x.note; cover.classList.remove('sw'); APP.dirty(500); };
  const cards = [...root.querySelectorAll('.cv')], n = cards.length; let cur = -1;
  const lay = (pos) => {
    cards.forEach((c, j) => { const d = j - pos, ad = Math.abs(d), sg = Math.sign(d), near = Math.min(ad, 1);
      const x = sg * (ad <= 1 ? (sg < 0 ? ad * 300 : ad * 318) : (sg < 0 ? 300 + (ad - 1) * 64 : 318 + (ad - 1) * 132)), z = -near * 300 - Math.max(0, ad - 1) * 110, ry = -sg * near * 56;
      c.style.transform = `translateX(${x.toFixed(1)}px) translateZ(${z.toFixed(1)}px) rotateY(${ry.toFixed(2)}deg)`;
      c.style.opacity = ad > 3.4 ? 0 : 1; c.style.filter = ad > 0.05 ? `brightness(${(1 - Math.min(ad, 2.5) * 0.26).toFixed(2)})` : 'none'; c.style.zIndex = 100 - Math.round(ad * 10); c.classList.toggle('f', ad < 0.5); });
    roll.style.transform = `translateY(${(-pos * 180).toFixed(1)}px)`;
    const i = APP.clamp(Math.round(pos), 0, n - 1);
    if (i !== cur) { const first = cur < 0; cur = i; const o = ISS[i].o; root.style.setProperty('--spot', APP.spotOf(U.I(o, 'poster'))); ticks.forEach((t, k) => t.classList.toggle('on', k === i));
      if (!root.classList.contains('rowsm')) APP.setImg(bd, U.I(o, 'backdrop'));
      if (first || APP.shot) put(i); else { cover.classList.add('sw'); clearTimeout(root.__sw); root.__sw = setTimeout(() => put(i), 200); if (m) m.blink(); } }
    APP.dirty(300);
  };
  const s = APP.spring(lay, 150, 0.82); s.set(st === 'next' ? 3 : st === 'fav' ? 4 : START);
  root.__flow = { go: (i) => s.to(APP.clamp(i, 0, n - 1)), cur: () => cur };
  if (st === 'fav') U.toggleFav($('.btn.fav'));
  if (st === 'press') { root.classList.add('pressing'); }
  if (st === 'exit') { const t = $('.toast'); t.innerHTML = '再按一次返回键退出'; t.classList.add('on'); }
  root.__focusTo = st === 'toc' ? '[data-cw="1"]' : st === 'toc0' ? '[data-cw="0"]' : st === 'new' ? '.rw:nth-of-type(3) .card:nth-child(12)' : st === 'new2' ? '.rw:nth-of-type(2) .card:nth-child(4)' : st === 'libs' ? '[data-lib="2"]' : st === 'fav' ? '.btn.fav' : st === 'press' ? '#vol' : null;
  const vol = $('.mast .l'); vol.id = 'vol'; vol.dataset.f = ''; vol.dataset.down = '#flow'; vol.insertAdjacentHTML('beforeend', '<small class="rf">　付印新刊</small>');
  if (root.__focusTo) { const e = $(root.__focusTo); if (e) { root.querySelectorAll('[data-f0]').forEach((x) => x.removeAttribute('data-f0')); e.dataset.f0 = ''; } }
};
// 焦点预览：焦点到哪张卡，封面区就换成那部片
const preview = async (root, el) => {
  const $ = (s) => root.querySelector(s); let o, still = null, kick, stand, name;
  if (el.dataset.cw !== undefined) { const c = L.continueWatching[+el.dataset.cw]; o = c.type === 'Movie' ? (U.BY[c.id]) : (U.det(c.seriesName) || U.byName(c.seriesName)); still = U.I(c, 'thumb') || U.I(c, 'poster'); name = c.seriesName || c.name;
    kick = (c.type === 'Episode' ? `续看 · 第 ${c.season} 季 第 ${c.episode} 集《${c.name}》` : '续看 · 电影') + (c.user.positionSec != null ? ` · 停在 ${U.clock(c.user.positionSec)}` : ' · 还没开始'); stand = U.cut(c.overview || (o && o.overview), 86); }
  else if (el.dataset.lib !== undefined) { const l = LIBS[+el.dataset.lib]; o = null; name = l.name; still = l.image; kick = '媒体库 · LIBRARY'; stand = `${l.titles} 部${l.episodes > l.titles ? `，${l.episodes.toLocaleString()} 集` : ''}。按确定进入海报墙。`; }
  else { o = U.BY[el.dataset.id]; name = o.name; kick = U.kick(o) + (o.rating ? ` · ★ ${U.rate(o.rating)}` : ''); stand = U.cut(o.overview, 86); }
  const bdk = APP.key(o && U.I(o, 'backdrop')), pk = APP.key((o && U.I(o, 'poster')) || still), sheet = $('.sheet'), token = root.__pv = (root.__pv || 0) + 1;
  $('.pv-k').textContent = kick; $('.pv-lg').innerHTML = U.logo(o || { name }, ''); $('.pv-s').textContent = stand;
  $('.pv-m').textContent = o ? [o.officialRating, (o.studios || [])[0]].filter(Boolean).join(' · ') : '';
  await APP.need([bdk, pk, APP.key(o && U.I(o, 'logo'))]); if (token !== root.__pv) return;
  APP.fill(root); APP.fixLogos(root);
  if (pk) root.style.setProperty('--spot', APP.spotOf(pk));
  if (bdk) { sheet.classList.remove('on'); APP.setImg($('.bd'), bdk); }
  else { const stl = APP.key(still) || pk; sheet.classList.add('on'); sheet.classList.toggle('w', stl !== pk || el.dataset.lib !== undefined); APP.setImg(sheet.querySelector('img'), el.dataset.lib !== undefined ? still : (stl !== pk && !o ? stl : pk)); const b = APP.blurOf(pk || stl); if (b) APP.setImg($('.bd'), b); }
};
APP.SCREENS.home = {
  title: '首页', nav: 'home',
  note: '上半屏是本期封面：三维海报流压在整幅剧照上，正中一张正对。焦点进了目录，封面区就变成焦点那部片的预览。',
  path: '<b>方向键路径</b>　封面：左右 = 翻期号（第一期再按左 = 进导航）；上 = 报头「VOL.」付印新刊；下 = 续看 / 详情 / 收藏。按钮行：左右换按钮，下 = 目录第 01 栏。目录：左右在一栏里走（焦点卡停在左起第一格，栏内容滑动），上下换栏（回到上次停的那张卡）；任何一栏走到最左再按左 = 进导航。确定 = 播放 / 打开；长按确定 = 菜单。',
  states: [['', '封面'], ['next', '翻到没有 logo 的一期'], ['fav', '收藏盖章'], ['press', '付印新刊'], ['toc', '目录 · 焦点预览'], ['toc0', '预览 · 没有剧照的一集'], ['new', '最近添加 · 只有海报'], ['libs', '媒体库'], ['nav', '导航展开'], ['exit', '再按一次返回退出'], ['offline', '网络断开']],
  render, init,
  onFocus(root, el) {
    const inRows = !!el.closest('.rows'), was = root.classList.contains('rowsm'); root.classList.toggle('rowsm', inRows);
    root.classList.toggle('flow-on', el.id === 'flow');
    if (inRows) preview(root, el); else if (was) { const o = ISS[root.__flow.cur()].o; APP.setImg(root.querySelector('.bd'), U.I(o, 'backdrop')); root.style.setProperty('--spot', APP.spotOf(U.I(o, 'poster'))); }
    APP.dirty(900);
  },
  key(root, k) {
    const c = F.cur; if (!c || document.getElementById('navp').classList.contains('open')) return;
    if (c.id === 'flow' && (k === 'left' || k === 'right')) { const i = root.__flow.cur(); if (k === 'left' && i === 0) { APP.navOpen(true); return true; } root.__flow.go(i + (k === 'right' ? 1 : -1)); return true; }
    if (k === 'up' && c.closest('.rw') && !c.closest('.rw').previousElementSibling) { F.set(root.querySelector('#cta-play')); return true; }
    if (k === 'back' && root.classList.contains('rowsm')) { F.set(root.querySelector('#flow')); return true; }
  },
  ok(root, c) {
    if (c.dataset.act === 'fav') { const on = U.toggleFav(c), m = APP.mascot(root, '.cover .msc'); if (on && m) { m.setMood('cheer', 1200); root.querySelector('.cover .say').textContent = '盖章了：这一期归进你的收藏。'; } return; }
    if (c.id === 'vol') { if (root.classList.contains('pressing')) return; root.classList.add('pressing'); setTimeout(() => { root.classList.remove('pressing'); root.querySelector('.vol').classList.add('next'); root.__flow.go(0); F.set(root.querySelector('#flow')); root.querySelector('.cover .say').textContent = '新刊到了：本周新进 23 部，封面换过一轮。'; }, 1700); return; }
    APP.toast(c.id === 'cta-play' || c.dataset.cw !== undefined ? '开始播放 →（见「播放器」画板）' : '打开详情 →（见「剧集详情 / 电影详情」画板）');
  },
};
})();
