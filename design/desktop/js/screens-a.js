/* 首页 / 发现 / 片库 / 合集 / 搜索 */
(function () {
'use strict';
const { ic, im, pad, esc, LIB, ITEM, X1 } = APP, S = APP.S;
const I = (id) => ITEM[id];

// ------------------------------------------------------------------ 继续观看: real rows; picture = own still -> series backdrop -> typographic tile
const CW = APP.CW = LIB.continueWatching.map((c) => {
  const ser = c.seriesName ? APP.byName(c.seriesName) : null, host = ser || c, pct = Math.round((c.user && c.user.playedPct) || 0), left = c.runtimeSec ? Math.max(1, Math.round(c.runtimeSec * (1 - pct / 100) / 60)) : 0;
  const th = APP.key(c.images && (c.images.thumb || (c.type === 'Episode' && c.images.poster))) || (ser && APP.bd(ser)) || null;
  return { c, host, pct, th, name: c.seriesName || c.name, sub: c.type === 'Episode' ? `第 ${c.episode} 集 · ${c.name}` : `电影 · ${APP.dur(c.runtimeSec)}`, left: pct ? `余 ${left} 分` : '还没开始', go: host.id ? APP.go(host) : '' };
});
const cwOf = (id) => CW.find((w) => w.host.id === id);
const thumb = (w) => w.th ? im(w.th) : APP.ph(w.name, 'NO STILL', 'position:absolute;inset:0;font-size:1.5rem');
const cwRow = (w, i) => `<a class="li" ${w.go} data-ctx="${w.host.id || ''}" tabindex="0"><em>${pad(i)}</em><div><b>${esc(w.name)}</b><span>${esc(w.sub)} · ${w.left}</span></div><div class="th">${thumb(w)}${w.pct ? `<u style="width:${w.pct}%"></u>` : ''}</div></a>`;

// ------------------------------------------------------------------ 首页
const COVERS = [['39778', '找个有整块时间的晚上再看，别分两次。'], ['60104', '第 11 集还没开看；本周更新到第 20 集。'], ['56342', '你停在第 5 集的 26 分 30 秒，后面正要组队。'], ['44978', '一千两百一十五集，不急，我替你记着页码。'],
  ['39806', '这部我看过十一遍，每次都说是最后一遍。'], ['44970', '戴耳机看，引擎声是另一位主角。'], ['57449', '满分十分的一场演出，下半场从 56 分开始。']].map(([id, note]) => {
  const it = I(id), w = cwOf(id); return { it, note, w, cta: w ? (w.c.type === 'Episode' ? `续看 第 ${w.c.episode} 集` : '续看') : '播放', left: w ? w.left : APP.len(it) }; });
const START = 2;
const LIBEN = { 电视剧: 'SERIES', 电影: 'FILM', 动漫: 'ANIME', emby302: 'STRM', 合集: 'SETS' };
const latestRow = (name, n) => (LIB.latest[name] || []).slice(0, n || 16).map((it, i) => APP.pc(it, { no: pad(i + 1) })).join('');
const homeSkeleton = () => `<div class="scroll"><section class="spread"><div class="stage"><i class="sk" style="position:absolute;left:calc(50% - var(--cw)/2);top:calc(50% - var(--ch)/2 - 1.6rem);width:var(--cw);height:var(--ch)"></i></div>
  <div class="rp"><i class="sk" style="width:16rem;height:9rem"></i><i class="sk" style="width:60%;height:8rem;margin:2.4rem 0"></i><i class="sk" style="width:90%;height:1.6rem"></i><i class="sk" style="width:84%;height:1.6rem;margin:1.2rem 0"></i><i class="sk" style="width:50%;height:1.6rem"></i>
  <div class="note" style="margin-top:3.2rem">${APP.pbox({ wl: 25, wr: 55, eyes: '-', props: ['sheet'] }, '排版：低头摆稿纸', '<b>正在排版</b>第一次打开要把片库过一遍：5 个媒体库、232 部。马上印好。')}</div></div>
  <div class="fold"><b>目录</b><em>CONTENTS</em><span class="sk" style="width:38rem;height:1.2rem"></span></div></section></div>`;
S.home = {
  g: '首页', title: '首页', sizes: 1, note: '第一屏是「本期封面」跨页：左页三维海报流，右页期号 / 片名 / 导语 / 主编手记；滚轮翻到目录区。',
  states: [['', '封面'], ['mid', '翻到一半'], ['fav', '盖章'], ['toc', '目录区'], ['hover', '悬停态'], ['focus', '键盘焦点'], ['rows', '各媒体库'], ['end', '末尾'], ['load', '首次加载']],
  build(st) {
    if (st === 'load') return { html: homeSkeleton(), tab: 0 };
    const feat = CW.find((w) => w.pct > 50 && w.th), rest = CW.filter((w) => w !== feat);
    return { tab: 0, hero: 1, html: `<div class="scroll" style="scroll-snap-type:y proximity">
<section class="spread" style="scroll-snap-align:start">
  <div class="bleed"><img class="b0" data-blur="${APP.bd(COVERS[START].it)}" alt=""><img class="b1 off" alt=""></div>
  <div class="stage">${COVERS.map((c, i) => `<div class="cv" data-i="${i}">${im(APP.poster(c.it))}<i class="sheen"></i></div>`).join('')}
    <div class="ticks"><span class="kbd">←</span>${COVERS.map(() => '<i></i>').join('')}<span class="kbd">→</span></div></div>
  <div class="rp favbox">
    <div class="iss"><div class="no"><span>${COVERS.map((_, i) => pad(i + 1)).join('<br>')}</span></div><em>/ ${pad(COVERS.length)}</em><div><small>COVER STORY · 本期封面</small><div class="kick sw-t kt"></div></div></div>
    <div class="lg sw-t" data-go=""></div>
    <p class="stand sw-t"></p>
    <div class="cta"><a class="btn" data-play>${ic('play')}<span class="cta-t sw-t"></span></a><a class="lnk" data-fav><span class="ft">收藏</span></a>${APP.favSlot()}<a class="lnk" data-info>详情</a><span class="r sw-t left-t"></span></div>
    <div class="note">${APP.msc('round', 56, 'data-say=".rp .say"')}<div><em>主编手记 · EDITOR'S NOTE</em><p class="say"></p></div></div>
  </div>
  <div class="rp3">${APP.hd('', '接着看', 'CONTINUE', '')}${CW.filter((w) => w.pct).slice(0, 4).map((w, i) => cwRow(w, i + 1)).join('')}</div>
  <div class="fold"><b>目录</b><em>CONTENTS</em><span><i>01</i>继续观看 ${CW.length}</span><span><i>02</i>最近添加</span><span><i>03</i>媒体库 ${LIB.libraries.length}</span><span><i>04</i>电影栏</span><span><i>05</i>动漫栏</span><span class="r"><span class="kbd">滚轮</span>翻页　<span class="kbd">← →</span>翻封面　<span class="kbd">Enter</span>播放</span></div>
</section>
<div class="page" style="padding-top:calc(var(--nav) + .4rem)">
  <section id="toc" style="scroll-snap-align:start;scroll-margin-top:calc(var(--nav) + .4rem)">${APP.hd('01', '继续观看', 'CONTINUE WATCHING', `<span>${CW.length} 部在看</span>`)}
    <div class="toc"><a class="feat" ${feat.go} data-ctx="${feat.host.id}" data-t0><div class="th">${thumb(feat)}<u style="width:${feat.pct}%"></u></div>
      <div class="cap"><em>01</em><div><b>${esc(feat.name)}</b><span>${esc(feat.sub)} · ${feat.left}</span></div><span class="btn sm">${ic('play')}续看</span></div></a>
    ${rest.map((w, i) => cwRow(w, i + 2)).join('')}</div>
    
  </section>
  <section class="sec" id="new">${APP.hd('02', '最近添加', 'NEW ARRIVALS', `<span class="seg">${Object.keys(LIB.latest).filter((k) => LIB.latest[k].length).map((k, i) => `<a data-lat="${k}" class="${i === 0 ? 'on' : ''}">${k}</a>`).join('')}</span>`)}
    <div class="hs row" id="latrow">${latestRow('电视剧')}</div></section>
  <section class="sec" id="libs">${APP.hd('03', '媒体库', 'SECTIONS', `<span>${LIB.libraries.length} 个媒体库</span>`)}
    <div class="cells">${LIB.libraries.map((l) => `<a class="cell" data-nav="${l.type === 'boxsets' ? 'colls' : 'wall&lib=' + encodeURIComponent(l.name)}"><em>${LIBEN[l.name] || 'LIBRARY'}</em><b>${esc(l.name)}</b><span class="num">${l.titles}</span><small>部${l.episodes > l.titles ? ` · ${l.episodes.toLocaleString()} 集` : ''}</small>
      <div class="mini">${((LIB.latest[l.name] || []).length ? LIB.latest[l.name] : LIB.collections).slice(0, 4).map((x) => im(APP.poster(x))).join('')}</div></a>`).join('')}</div></section>
  <section class="sec">${APP.hd('04', '电影栏', 'FILM', `<a data-nav="wall&lib=${encodeURIComponent('电影')}">全部 118 部 →</a>`)}<div class="hs row">${latestRow('电影')}</div></section>
  <section class="sec">${APP.hd('05', '动漫栏', 'ANIME', `<a data-nav="wall&lib=${encodeURIComponent('动漫')}">全部 32 部 →</a>`)}<div class="hs row">${latestRow('动漫')}</div></section>
  <div class="end">${APP.pbox({ eyes: '-', tilt: 9, dy: 3, props: ['zzz'] }, '截稿后趴着打盹', '本期到此。共 232 部、7,985 集，由你的片库自动编排；主编咕咕，明晚九点见。')}<div class="col">咕咕嘎嘎 · VOL. 318<br>十一月十四日</div></div>
</div></div>` };
  },
  init(root, st) {
    if (st === 'load') return;
    const $ = (s) => root.querySelector(s), rp = $('.rp'), roll = $('.no span'), ticks = [...root.querySelectorAll('.ticks i')], say = $('.rp .say'), stage = $('.stage'), b0 = $('.b0'), b1 = $('.b1'); let flip = 0;
    const put = (i) => { const c = COVERS[i], t = c.it; $('.lg').innerHTML = APP.title(t); $('.lg').dataset.go = APP.go(t).slice(9, -1); APP.fill($('.lg')); $('.kt').textContent = [...APP.genres(t, 2), t.year, APP.len(t)].join(' · ');
      $('.stand').textContent = APP.stand(t, 78); $('.cta-t').textContent = c.cta; $('.left-t').textContent = c.left + (c.w && c.w.pct ? ` · 已看 ${c.w.pct}%` : ''); say.textContent = c.note; delete say.dataset.def; rp.classList.remove('sw'); };
    const flow = root.__flow = APP.flow(stage, {
      start: START, step: () => stage.querySelector('.cv').offsetWidth * 0.66,
      lay(c, d, tilt) { const w = c.offsetWidth, ad = Math.abs(d), sg = Math.sign(d), near = Math.min(ad, 1), f = Math.max(0, 1 - ad), far = stage.offsetWidth > w * 4.6 ? 3.6 : 2.6;
        const x = sg * (ad <= 1 ? ad * w * 0.66 : w * 0.66 + (ad - 1) * w * 0.3), z = -near * w * 0.6 - Math.max(0, ad - 1) * w * 0.26, ry = -sg * near * 54 + tilt.x * 11 * f, rx = -tilt.y * 8 * f;
        c.style.transform = `translateX(${x.toFixed(1)}px) translateZ(${z.toFixed(1)}px) rotateY(${ry.toFixed(2)}deg) rotateX(${rx.toFixed(2)}deg)`;
        c.style.opacity = ad > far ? 0 : 1; c.style.filter = ad > 0.05 ? `brightness(${(1 - Math.min(ad, 2) * 0.3).toFixed(2)})` : 'none'; c.style.zIndex = 100 - Math.round(ad * 10); },
      onPos(pos, tilt) { roll.style.transform = `translateY(${(-pos * roll.parentElement.offsetHeight).toFixed(1)}px)`; $('.lg').style.transform = `translate(${(-tilt.x * 12).toFixed(1)}px,${(-tilt.y * 6).toFixed(1)}px)`; },
      onIndex(i, first) { const c = COVERS[i]; APP.setSpot(APP.poster(c.it)); ticks.forEach((t, n) => t.classList.toggle('on', n === i));
        const nb = flip ? b0 : b1, ob = flip ? b1 : b0; if (!first) { APP.blurKey(APP.bd(c.it)).then((k) => { nb.src = window.IMG[k]; nb.dataset.img = nb.dataset.got = k; if (APP.glassUp) window.G2.loadImages().then(APP.dirty); nb.classList.remove('off'); ob.classList.add('off'); flip = 1 - flip; }); }
        if (first || APP.shot) put(i); else { rp.classList.add('sw'); clearTimeout(root.__sw); root.__sw = setTimeout(() => put(i), 210); const m = APP.mascot('.rp .msc'); if (m) m.blink(); } },
      onOpen(i) { APP.nav(APP.go(COVERS[i].it).slice(9, -1).replace(':', '&id=')); },
    });
    ticks.forEach((t, n) => t.addEventListener('click', () => flow.go(n)));
    window.addEventListener('keydown', (e) => { if (root.querySelector('.scroll').scrollTop > 200) return; if (e.key === 'ArrowRight') flow.step(1); if (e.key === 'ArrowLeft') flow.step(-1); });
    $('[data-info]').addEventListener('click', () => flow && APP.nav(APP.go(COVERS[flow.cur()].it).slice(9, -1).replace(':', '&id=')));
    $('[data-play]').addEventListener('click', () => APP.nav('player'));
    root.querySelectorAll('[data-lat]').forEach((a) => a.addEventListener('click', () => { root.querySelectorAll('[data-lat]').forEach((x) => x.classList.toggle('on', x === a)); const r = $('#latrow'); r.innerHTML = latestRow(a.dataset.lat); r.scrollLeft = 0; APP.fill(r); }));
  },
  async after(root, st) {
    const sc = root.querySelector('.scroll'), top = (sel, d) => { sc.style.scrollSnapType = 'none'; sc.scrollTop = root.querySelector(sel).getBoundingClientRect().top + sc.scrollTop - (d || 0); sc.dispatchEvent(new Event('scroll')); };
    const nav = parseFloat(getComputedStyle(root).getPropertyValue('--nav')) * parseFloat(getComputedStyle(document.documentElement).fontSize) + 6;
    if (st === 'mid') root.__flow.s.set(START + 0.45);
    if (st === 'fav') root.querySelector('.rp [data-fav]').click();
    if (st === 'toc') top('#toc', nav);
    if (st === 'hover' || st === 'focus') { top('#new', nav); const pc = root.querySelectorAll('#latrow .pc')[2]; if (st === 'hover') { pc.classList.add('hov'); pc.querySelector('.po').style.transform = 'rotateY(7deg) rotateX(4deg) translateZ(1rem)'; pc.style.setProperty('--spot', await APP.spot(APP.poster(ITEM[pc.dataset.ctx]))); } else { document.body.classList.add('kb'); pc.classList.add('kbf'); } }
    if (st === 'rows') top('#libs', nav);
    if (st === 'end') { sc.style.scrollSnapType = 'none'; sc.scrollTop = 1e6; sc.dispatchEvent(new Event('scroll')); }
  },
};
})();
