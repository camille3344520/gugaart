/* 发现 / 片库（媒体库列表、海报墙）/ 合集 / 搜索 */
(function () {
'use strict';
const { ic, im, pad, esc, LIB, ITEM, X1 } = APP, S = APP.S;
// every title we have real data for (latest + details + collection members + filmographies), one per name
const ALL = APP.ALL = Object.values(Object.values(ITEM).filter((i) => (i.type === 'Movie' || i.type === 'Series') && APP.poster(i)).reduce((m, i) => { const o = m[i.name]; if (!o || JSON.stringify(i).length > JSON.stringify(o).length) m[i.name] = i; return m; }, {}));
const BOUND = '阿八嚓哒妸发旮哈讥咔垃妈拏噢妑七呥仨他穵夕丫帀', LET = 'ABCDEFGHJKLMNOPQRSTWXYZ';
const initial = (s) => { const c = (s || '').trim()[0] || '#'; if (/[a-z]/i.test(c)) return c.toUpperCase(); if (!/[一-龥]/.test(c)) return '#'; let k = 0; for (let i = 0; i < BOUND.length; i++) if (c.localeCompare(BOUND[i], 'zh-CN') >= 0) k = i; return LET[k]; };
const GCOUNT = {}; ALL.forEach((i) => APP.genres(i, 9).forEach((g) => { GCOUNT[g] = (GCOUNT[g] || 0) + 1; }));
const GENRES = Object.entries(GCOUNT).sort((a, b) => b[1] - a[1]);
const poseNone = (t) => APP.pbox({ wl: 78, wr: 78, eyes: '-', mouth: 'o', tilt: -6, props: ['sweat'] }, '摊手：两翅摊开、歪头', t, 12);

// ------------------------------------------------------------------ 发现
const FEATS = ['44978', '39831', '44992', '39778', '37649', '39864'];
const featHtml = (it) => `<div class="bleed">${im(APP.bd(it))}</div><span class="vert">今日专题 · 剧照 ·《${esc(it.name)}》</span><div class="in" data-t0><div><div class="kick">FEATURE · 今日专题　${[APP.kind(it), ...APP.genres(it, 2), it.year].join(' · ')}</div>
  <div class="lgbox" ${APP.go(it)}>${APP.title(it)}</div><p class="st">${esc(APP.stand(it, 64))}</p>
  <div style="display:flex;gap:1.8rem;align-items:center;margin-top:2.2rem"><a class="btn" data-nav="player">${ic('play')}${it.type === 'Movie' ? '播放' : '从第 1 集看起'}<small>${APP.len(it)}</small></a><a class="lnk" ${APP.go(it)}>详情</a><a class="lnk" data-shuffle>${ic('replay')}换一部</a><span class="kbd">R</span></div></div>
  <div class="side"><div class="rate">${APP.score(it)}<small>评分 · ${it.officialRating || '未分级'}</small></div></div></div>`;
S.discover = {
  g: '发现', title: '发现', note: '整幅铺满的「今日专题」+ 题材索引、年代、评分榜。「换一部」会换题图和专色。', states: [['', '专题'], ['idx', '题材 / 年代'], ['top', '评分榜 / 长片名']],
  build() {
    const years = {}; ALL.forEach((i) => { if (i.year) years[i.year] = (years[i.year] || 0) + 1; }); const ys = Object.keys(years).map(Number), y0 = Math.min(...ys), y1 = Math.max(...ys), mx = Math.max(...Object.values(years));
    const top = ALL.filter((i) => i.rating).sort((a, b) => b.rating - a.rating).slice(0, 12);
    let era = ''; for (let y = y0; y <= y1; y++) era += `<a style="height:${years[y] ? 8 + 92 * years[y] / mx : 1}%" title="${y} · ${years[y] || 0} 部">${y % 5 === 0 || y === y0 ? `<b>${y}</b>` : ''}<i>${years[y] || ''}</i></a>`;
    return { tab: 1, hero: 1, html: `<div class="scroll"><div class="page flush"><section class="hero" id="feat" style="--hh:76vh">${featHtml(ITEM[FEATS[0]])}</section>
<section class="sec" id="idx" style="margin-top:3.2rem">${APP.hd('01', '题材', 'GENRES', `<span>${GENRES.length} 个题材 · ${ALL.length} 部</span>`)}<div class="idx">${GENRES.slice(0, 18).map((g) => `<a data-nav="wall&lib=${encodeURIComponent('电影')}">${esc(g[0])}<em>${g[1]}</em></a>`).join('')}</div></section>
<section class="sec">${APP.hd('02', '年代', 'TIMELINE', `<span>${y0} — ${y1}</span>`)}<div class="era">${era}</div></section>
<section class="sec" id="top">${APP.hd('03', '评分榜', 'TOP RATED', '<span>按评分 · 前 12</span>')}<div class="cols2 x3">${top.map((it, i) => `<a class="li po" ${APP.go(it)} data-ctx="${it.id}"><em>${pad(i + 1)}</em>${im(APP.poster(it), 'p')}<div><b>${esc(it.name)}</b><span>${APP.kind(it)} · ${APP.meta(it)}</span></div><span class="sc">${APP.score(it)}</span></a>`).join('')}</div></section>
<section class="sec">${APP.hd('04', '编辑部推荐', "EDITOR'S PICKS", '<span>演唱会、剧场版与长片名</span>')}<div class="hs row">${[...LIB.stress.longestTitles.map((x) => ITEM[x.id] || x), ITEM['57454'], ITEM['57449'], ITEM['39840']].filter(Boolean).map((it, i) => APP.pc(it, { no: pad(i + 1), cls: 'wrap' })).join('')}</div></section>
</div></div>` };
  },
  init(root) { let k = 0; const go = () => { k = (k + 1) % FEATS.length; const it = ITEM[FEATS[k]], f = root.querySelector('#feat'); f.innerHTML = featHtml(it); APP.fill(f); APP.setSpot(APP.poster(it)); };
    root.addEventListener('click', (e) => { if (e.target.closest('[data-shuffle]')) go(); }); window.addEventListener('keydown', (e) => { if (e.key.toLowerCase() === 'r' && !e.ctrlKey) go(); }); return APP.setSpot(APP.poster(ITEM[FEATS[0]])); },
  after(root, st) { const sc = root.querySelector('.scroll'); if (st) { sc.scrollTop = root.querySelector('#' + st).getBoundingClientRect().top - 106; sc.dispatchEvent(new Event('scroll')); } },
};

// ------------------------------------------------------------------ 片库 · 媒体库列表
const LIBEN = { 电视剧: 'SERIES', 电影: 'FILM', 动漫: 'ANIME', emby302: 'STRM', 合集: 'SETS' };
S.libs = {
  g: '片库', title: '媒体库列表', note: '五个媒体库是五个栏目：编号、题图、部数 / 集数、最近入库的六张。',
  build() { return { tab: 2, solid: 1, html: `<div class="scroll"><div class="page libs"><div class="lhead"><h2>片库</h2><em class="en">SECTIONS</em><div class="r"><small>${LIB.libraries.length} 个媒体库 · 232 部 · 7,985 集 · 上次同步 21:40</small></div></div>
${LIB.libraries.map((l, i) => `<a class="lrow" data-nav="${l.type === 'boxsets' ? 'colls' : 'wall&lib=' + encodeURIComponent(l.name)}"><em>${pad(i + 1)}</em><div class="im">${im(APP.key(l.image))}</div><div><h3>${esc(l.name)}</h3><em>${LIBEN[l.name]}</em><p><b>${l.titles}</b>部${l.episodes > l.titles ? `　<b>${l.episodes.toLocaleString()}</b>集` : ''}</p></div>
  <div class="mini">${((LIB.latest[l.name] || []).length ? LIB.latest[l.name] : LIB.collections).slice(0, 6).map((x) => im(APP.poster(x))).join('')}</div></a>`).join('')}</div></div>` }; },
};

// ------------------------------------------------------------------ 片库 · 海报墙
const WALL = { 电影: (i) => i.type === 'Movie', 电视剧: (i) => i.type === 'Series' && !APP.isAnime(i), 动漫: (i) => i.type === 'Series' && APP.isAnime(i), emby302: (i) => LIB.latest.emby302.some((x) => x.id === i.id) };
const SORTS = [['add', '加入时间'], ['name', '片名'], ['year', '年份'], ['rate', '评分'], ['len', '时长']];
S.wall = {
  g: '片库', title: '海报墙', sizes: 1, note: '列数跟窗口走（1100 → 6 列，1440 → 7 列，3168 → 12 列以上）；筛选条把墙往下推，不盖海报；右缘是字母索引。',
  states: [['', '电影'], ['tv', '电视剧'], ['anime', '动漫'], ['filter', '筛选打开'], ['sort', '排序打开'], ['az', '字母索引'], ['list', '列表视图'], ['loading', '加载中'], ['empty', '筛不到']],
  build(st, Q) {
    const lib = st === 'tv' ? '电视剧' : st === 'anime' ? '动漫' : (Q.get('lib') || '电影'), L = LIB.libraries.find((l) => l.name === lib) || LIB.libraries[1];
    this.lib = lib; this.items = ALL.filter(WALL[lib] || WALL.电影);
    return { tab: 2, sub: 1, solid: 1, html: `<div class="scroll"><div class="page"><div class="lhead"><h2>${esc(lib)}</h2><em class="en">${LIBEN[lib] || ''}</em><span class="num">${L.titles}</span><small>部${L.episodes > L.titles ? ` · ${L.episodes.toLocaleString()} 集` : ''}　本稿收录 <span id="cnt"></span> 部</small>
  <div class="r"><span class="kbd">F</span><a class="dd" id="fbtn">${ic('filter')}筛选<small id="fnum"></small></a><span class="dd" id="sbtn">${ic('sort')}<span id="sname">加入时间</span><small id="sdir">新 → 旧</small>${ic('chevd')}<div class="ctx">${SORTS.map((s, i) => `<a data-sort="${s[0]}" class="${i === 0 ? 'on' : ''}">${s[1]}</a>`).join('')}<hr><a data-dir="1">倒序</a></div></span>
  <a class="dd" id="vbtn" title="海报 / 列表">${ic('grid')}${ic('rows')}</a></div></div>
<div class="tools"><div class="seg">${['全部', '未看', '在看', '看过', '收藏'].map((s, i) => `<a class="${i === 0 ? 'on' : ''}" data-seg="${i}">${s}</a>`).join('')}</div><div class="r" id="active"></div></div>
<div class="fpanel" id="fp" style="display:none"><div><h5>GENRE · 题材</h5><div class="chips">${GENRES.slice(0, 14).map((g) => `<a class="chip" data-g="${esc(g[0])}">${esc(g[0])}</a>`).join('')}</div></div>
  <div><h5>DECADE · 年代</h5><div class="chips">${['2020 年代', '2010 年代', '2000 年代', '90 年代', '更早'].map((d, i) => `<a class="chip" data-d="${i}">${d}</a>`).join('')}</div></div>
  <div><h5>RATING · 评分</h5><div class="chips">${['9 分以上', '8 分以上', '7 分以上'].map((d, i) => `<a class="chip" data-r="${9 - i}">${d}</a>`).join('')}</div></div>
  <div><h5>QUALITY · 规格（示意）</h5><div class="chips">${['4K', 'HDR', '杜比视界', '内封字幕', 'strm'].map((d) => `<a class="chip">${d}</a>`).join('')}</div></div>
  <div class="ft"><span id="fsum"></span><a class="lnk" data-clear>清空筛选</a><a class="btn sm" data-fclose>收起<small>Esc</small></a></div></div>
<div id="wall"></div></div><nav class="az" id="az"></nav></div>` };
  },
  init(root, st) {
    const $ = (s) => root.querySelector(s), F = { g: new Set(), d: null, r: null, sort: 'add', dir: 0, seg: 0, view: st === 'list' ? 'list' : 'grid', az: st === 'az' };
    const items = this.items, wall = $('#wall');
    const render = () => {
      let L = items.filter((i) => (!F.g.size || APP.genres(i, 9).some((g) => F.g.has(g))) && (F.d === null || [i.year >= 2020, i.year >= 2010 && i.year < 2020, i.year >= 2000 && i.year < 2010, i.year >= 1990 && i.year < 2000, i.year < 1990][F.d]) && (!F.r || (i.rating || 0) >= F.r));
      if (F.seg === 1) L = L.filter((i) => !(i.user && i.user.played)); if (F.seg === 2) L = L.filter((i) => APP.CW.some((w) => w.host.id === i.id)); if (F.seg === 3) L = L.filter((i) => i.user && i.user.played); if (F.seg === 4) L = L.filter((i) => i.user && i.user.favorite);
      if (st === 'empty') L = [];
      const by = { name: (a, b) => a.name.localeCompare(b.name, 'zh-CN'), year: (a, b) => (b.year || 0) - (a.year || 0), rate: (a, b) => (b.rating || 0) - (a.rating || 0), len: (a, b) => (b.runtimeSec || 0) - (a.runtimeSec || 0) }[F.az ? 'name' : F.sort]; if (by) L = L.slice().sort(by); if (F.dir) L.reverse();
      $('#cnt').textContent = items.length; const nf = F.g.size + (F.d !== null) + (F.r ? 1 : 0); $('#fnum').textContent = nf ? ` ${nf}` : ''; $('#fsum').textContent = `筛到 ${L.length} 部`;
      $('#active').innerHTML = [...F.g].map((g) => `<a class="chip on" data-g="${g}">${g} ×</a>`).join('') + (F.d !== null ? `<a class="chip on" data-d="${F.d}">${['2020 年代', '2010 年代', '2000 年代', '90 年代', '更早'][F.d]} ×</a>` : '') + (F.r ? `<a class="chip on" data-r="${F.r}">${F.r} 分以上 ×</a>` : '') + `<span style="color:var(--faint);font-style:italic">${L.length} 部</span>`;
      root.querySelectorAll('#fp .chip').forEach((c) => c.classList.toggle('on', (c.dataset.g && F.g.has(c.dataset.g)) || (c.dataset.d !== undefined && +c.dataset.d === F.d) || (c.dataset.r !== undefined && +c.dataset.r === F.r)));
      const used = new Set(L.map((i) => initial(i.name)));
      $('#az').innerHTML = F.az ? ['#', ...LET].map((l) => `<a class="${used.has(l) ? '' : 'no'}${l === (F.cur || [...used].sort()[0]) ? ' on' : ''}" data-l="${l}">${l}</a>`).join('') : '';
      if (st === 'loading') { wall.innerHTML = `<div class="note" style="margin-bottom:2.4rem">${APP.pbox({ wl: 25, wr: 55, eyes: '-', props: ['sheet'] }, '排版：低头摆稿纸', '<b>正在排版</b>片单马上印好；已经取到 0 / ' + items.length + ' 部。')}</div><div class="grid">${Array(21).fill(0).map(() => `<div><i class="sk" style="aspect-ratio:2/3"></i><i class="sk" style="height:1.4rem;width:70%;margin-top:1.2rem"></i><i class="sk" style="height:1rem;width:40%;margin-top:.8rem"></i></div>`).join('')}</div>`; return; }
      if (!L.length) { wall.innerHTML = `<div class="empty">${poseNone('<b>这一栏今天开天窗</b>当前筛选下一部都没有：电影 · 90 年代 · 9 分以上。<br><a class="lnk" data-clear style="margin-top:1.2rem">清空筛选</a>　或者把评分放宽到 8 分。')}</div>`; return; }
      if (F.view === 'list') wall.innerHTML = `<table class="tb"><tr><th>NO.</th><th>片名</th><th>年份</th><th>题材</th><th>时长 / 集数</th><th>评分</th><th>分级</th><th>已看</th></tr>${L.map((i, n) => `<tr ${APP.go(i)} data-ctx="${i.id}" style="cursor:pointer"><td class="n">${pad(n + 1)}</td><td class="t">${esc(i.name)}</td><td class="n">${i.year || ''}</td><td>${APP.genres(i, 3).join(' / ')}</td><td>${APP.len(i)}</td><td class="n" style="color:var(--ink);font-size:1.7rem">${APP.score(i)}</td><td>${i.officialRating || '—'}</td><td>${APP.mk(i.user && i.user.played)}</td></tr>`).join('')}</table>`;
      else { let last = ''; wall.innerHTML = `<div class="grid">${L.map((i, n) => { const l = initial(i.name), f1 = F.az && l !== last; last = l; return APP.pc(i, { cls: 'wrap' + (f1 ? ' l1' : ''), no: F.az ? l : '', attr: f1 ? `id="l-${l}"` : '', sub: `${i.year || ''} · ${APP.score(i)} 分` }); }).join('')}</div>`; }
      APP.fill(wall);
    };
    root.addEventListener('click', (e) => { const t = e.target, c = (s) => t.closest(s);
      if (c('#fbtn') || c('[data-fclose]')) { const fp = $('#fp'), on = fp.style.display === 'none'; fp.style.display = on ? '' : 'none'; $('#fbtn').classList.toggle('open', on); APP.dirty(); return; }
      if (c('[data-sort]')) { F.sort = c('[data-sort]').dataset.sort; root.querySelectorAll('[data-sort]').forEach((a) => a.classList.toggle('on', a.dataset.sort === F.sort)); $('#sname').textContent = SORTS.find((s) => s[0] === F.sort)[1]; $('#sdir').textContent = { add: '新 → 旧', name: 'A → Z', year: '新 → 旧', rate: '高 → 低', len: '长 → 短' }[F.sort]; F.az = F.sort === 'name'; $('#sbtn').classList.remove('open'); render(); return; }
      if (c('[data-dir]')) { F.dir = 1 - F.dir; c('[data-dir]').classList.toggle('on', !!F.dir); render(); return; }
      if (c('#sbtn')) { $('#sbtn').classList.toggle('open'); return; } $('#sbtn').classList.remove('open');
      if (c('#vbtn')) { F.view = F.view === 'grid' ? 'list' : 'grid'; render(); return; }
      if (c('[data-seg]')) { F.seg = +c('[data-seg]').dataset.seg; root.querySelectorAll('[data-seg]').forEach((a) => a.classList.toggle('on', +a.dataset.seg === F.seg)); render(); return; }
      if (c('[data-clear]')) { F.g.clear(); F.d = F.r = null; st = ''; render(); return; }
      const ch = c('.chip'); if (ch) { if (ch.dataset.g) { F.g.has(ch.dataset.g) ? F.g.delete(ch.dataset.g) : F.g.add(ch.dataset.g); } if (ch.dataset.d !== undefined) F.d = F.d === +ch.dataset.d ? null : +ch.dataset.d; if (ch.dataset.r !== undefined) F.r = F.r === +ch.dataset.r ? null : +ch.dataset.r; render(); return; }
      const l = c('[data-l]'); if (l) { const h = $('#l-' + l.dataset.l); if (h) { F.cur = l.dataset.l; root.querySelector('.scroll').scrollTop += h.getBoundingClientRect().top - 106; render(); } }
    });
    window.addEventListener('keydown', (e) => { if (e.key.toLowerCase() === 'f' && !e.ctrlKey && !e.target.closest('input')) $('#fbtn').click(); });
    if (st === 'filter') { F.g.add(GENRES[0][0]); F.d = 0; $('#fp').style.display = ''; $('#fbtn').classList.add('open'); }
    if (st === 'sort') $('#sbtn').classList.add('open');
    if (st === 'az') { $('#sname').textContent = '片名'; $('#sdir').textContent = 'A → Z'; }
    if (st === 'empty') { F.r = 9; F.d = 3; }
    render();
  },
};

// ------------------------------------------------------------------ 合集
const XC = (X1.collections || []);
S.colls = {
  g: '合集', title: '合集列表', note: '合集是「专辑」：封面、名字、部数。英文名是服务器上的真实名字。',
  build() { return { tab: 3, solid: 1, html: `<div class="scroll"><div class="page"><div class="lhead"><h2>合集</h2><em class="en">SETS</em><span class="num">${LIB.collections.length}</span><small>套</small></div><div style="height:3.2rem"></div>
<div class="grid big">${LIB.collections.map((c, i) => `<div class="pc wrap" data-go="coll:${c.id}" tabindex="0"><div class="po">${im(APP.poster(c))}<i class="sheen"></i></div><div class="cap"><em>${pad(i + 1)}</em><b>${esc(c.name)}</b><span class="meta">共 ${c.count} 部</span></div></div>`).join('')}</div></div></div>` }; },
};
S.coll = {
  g: '合集', title: '合集详情', note: '整幅铺满：合集自己的剧照从窗口顶端铺下来，名字和简介接在渐隐处；成员是带大编号的目录。',
  states: [['', '新世纪福音战士（4 部，长名）'], ['av', 'The Avengers（4 部）'], ['fb', 'Fight Back to School（2 部）']],
  build(st, Q) {
    const id = Q.get('id') || { av: '41634', fb: '42532' }[st] || '40818', c = XC.find((x) => x.id === id) || Object.assign({ members: [] }, LIB.collections.find((x) => x.id === id) || XC[0]); this.c = c;
    const mins = c.members.reduce((a, m) => a + (m.runtimeSec || 0), 0);
    return { tab: 3, sub: 1, hero: 1, html: `<div class="scroll"><div class="page flush"><section class="hero" style="--hh:70vh;--fy:30%"><div class="bleed${c.backdrop ? '' : ' soft'}">${im(APP.key(c.backdrop) || APP.poster(c))}</div>
  <span class="vert">合集题图 · ${c.backdropFrom && c.backdropFrom !== 'collection' ? '取自成员影片' : '合集剧照'}</span>
  <div class="in" data-t0><div><div class="kick">SET · 合集　共 ${c.members.length || c.count} 部${mins ? ' · 连看 ' + APP.dur(mins) : ''}</div><div class="lgbox long"><b class="tname">${esc(c.name)}</b></div>
  <div style="display:flex;gap:1.8rem;align-items:center" class="favbox"><a class="btn">${ic('play')}从第一部看起</a><a class="lnk" data-fav><span class="ft">收藏</span></a>${APP.favSlot()}</div></div>
  <div class="side"><div class="rate">${c.members.length ? (c.members.reduce((a, m) => a + (m.rating || 0), 0) / c.members.length).toFixed(1) : '—'}<small>成员平均分</small></div></div></div></section>
<section style="display:grid;grid-template-columns:minmax(0,4fr) minmax(0,8fr);gap:calc(var(--gut)*2);margin-top:3.2rem"><div><div class="kick" style="margin-bottom:1.2rem">ABOUT · 关于这一套</div><p style="font-family:var(--cn);font-size:1.5rem;line-height:1.9;color:var(--dim)">${esc(c.overview || '服务器上没有这套合集的简介。')}</p></div>
<div>${APP.hd('', '成员', 'IN THIS SET', `<span>按上映年份</span>`)}${c.members.length ? c.members.slice().sort((a, b) => a.year - b.year).map((m, i) => `<div class="mrow" data-go="movie:${m.id}" data-ctx="${m.id}"><em>${pad(i + 1)}</em><div class="po">${im(APP.poster(m))}</div><div><b>${esc(m.name)}</b><span class="m">${m.year} · ${APP.dur(m.runtimeSec)} · ${APP.score(m)} 分</span><p>${esc(m.overview || '')}</p></div><div class="a"><span class="btn sm">${ic('play')}播放</span>${APP.mk()}</div></div>`).join('') : `<div class="empty" style="min-height:20rem">${poseNone('<b>成员清单还没取到</b>这套合集共 ' + c.count + ' 部。')}</div>`}</div></section></div></div>` };
  },
  init() { return APP.setSpot(APP.poster(this.c)); },
};

// ------------------------------------------------------------------ 搜索
const PEOPLE = [...LIB.persons, ...(X1.persons || [])].reduce((a, p) => (a.some((x) => x.id === p.id) ? a : a.concat(p)), []);
const hit = (q) => { q = q.trim().toLowerCase(); if (!q) return { t: [], p: [], c: [] }; const m = (s) => (s || '').toLowerCase().includes(q);
  return { t: ALL.filter((i) => m(i.name) || m(i.originalTitle) || String(i.year) === q || APP.genres(i, 9).some(m)).sort((a, b) => (m(b.name) - m(a.name)) || ((b.rating || 0) - (a.rating || 0))), p: PEOPLE.filter((p) => m(p.name)), c: LIB.collections.filter((c) => m(c.name)) }; };
const mark = (s, q) => { const i = s.toLowerCase().indexOf(q.toLowerCase()); return i < 0 ? esc(s) : esc(s.slice(0, i)) + '<mark>' + esc(s.slice(i, i + q.length)) + '</mark>' + esc(s.slice(i + q.length)); };
S.search = {
  g: '搜索', title: '搜索', note: 'Ctrl K 或 / 打开。输入中左栏是联想（↑↓ 选、Enter 进），右栏直接出结果；真的在这份数据里搜。', states: [['', '空'], ['typing', '输入中'], ['results', '结果'], ['long', '结果 · 长片名'], ['none', '无结果']],
  build() { return { sub: 1, solid: 1, html: `<div class="scroll"><div class="page"><div class="sbox">${ic('search')}<input id="q" placeholder="片名、人物、年份、题材……" spellcheck="false" autocomplete="off"><span class="kbd">Esc 清空</span><span class="kbd">Enter 搜索</span></div><div id="out"></div></div></div>` }; },
  init(root, st) {
    const q = root.querySelector('#q'), out = root.querySelector('#out'); let sel = 0;
    const draw = (full) => { const v = q.value.trim(), H = hit(v), n = H.t.length + H.p.length + H.c.length;
      if (!v) { out.innerHTML = `<div class="sgrid"><div>${APP.hd('', '最近搜过', 'RECENT', '<a>清空</a>')}<div class="sug">${['航海王', '周星驰', '2024', '悬疑', '福音战士'].map((s) => `<a data-q="${s}">${s}<span>${hit(s).t.length + hit(s).p.length + hit(s).c.length} 条</span></a>`).join('')}</div>
        <div class="note" style="margin-top:3.2rem">${APP.pbox({ wr: 70, eyes: '^', mouth: 'D', props: ['pen', 'qm'] }, '举笔：等你报题', '片名、人物、年份、题材都行，报个题我去翻。')}</div></div>
        <div>${APP.hd('', '按题材翻', 'BROWSE', '')}<div class="idx" style="grid-template-columns:repeat(4,1fr)">${GENRES.slice(0, 12).map((g) => `<a data-q="${esc(g[0])}">${esc(g[0])}<em>${g[1]}</em></a>`).join('')}</div></div></div>`; APP.dirty(); return; }
      if (!n) { out.innerHTML = `<div class="empty">${poseNone(`<b>没有找到「${esc(v)}」</b>换个说法试试：只打片名里的一两个字，或者搜人物、年份、题材。<br>在 5 个媒体库、${ALL.length} 部里都翻过了。`)}</div>`; APP.dirty(); return; }
      const best = H.t[0], sug = [...H.t.slice(0, 7).map((i) => [i.name, `${APP.kind(i)} · ${i.year || ''}`, APP.go(i)]), ...H.p.slice(0, 2).map((p) => [p.name, '人物', `data-go="person:${p.id}"`]), ...H.c.slice(0, 2).map((c) => [c.name, `合集 · ${c.count} 部`, `data-go="coll:${c.id}"`])]; sel = Math.min(sel, sug.length - 1);
      const bd = best && APP.bd(best);
      out.innerHTML = `<div class="sgrid"><div>${full ? '' : `${APP.hd('', '联想', 'SUGGEST', '<span>↑ ↓ 选择 · Enter 打开</span>')}<div class="sug">${sug.map((s, i) => `<a ${s[2]} class="${i === sel ? 'on' : ''}"><b style="font-weight:400">${mark(s[0], v)}</b><span>${s[1]}</span>${i === sel ? '<em class="kbd">Enter</em>' : ''}</a>`).join('')}</div>`}
        ${full || !best ? '' : ''}${full && best ? `${APP.hd('', '最佳匹配', 'BEST MATCH', '')}<a class="best" ${APP.go(best)}>${bd ? im(bd) : im(APP.poster(best))}<div class="t">${APP.title(best)}<span>${APP.kind(best)} · ${APP.meta(best)}<br>${APP.len(best)} · ${APP.score(best)} 分</span></div></a>` : ''}
        ${full && H.p.length ? `<div class="sec" style="margin-top:3.2rem">${APP.hd('', '人物', 'PEOPLE', `<span>${H.p.length}</span>`)}${H.p.map((p) => `<a class="li po" data-go="person:${p.id}" style="grid-template-columns:5.4rem 1fr auto">${im(APP.key(p.image), 'p')}<div><b>${mark(p.name, v)}</b><span>片库里 ${p.works.length} 部作品</span></div></a>`).join('')}</div>` : ''}
        ${full && H.c.length ? `<div class="sec" style="margin-top:3.2rem">${APP.hd('', '合集', 'SETS', `<span>${H.c.length}</span>`)}${H.c.map((c) => `<a class="li po" data-go="coll:${c.id}" style="grid-template-columns:5.4rem 1fr auto">${im(APP.poster(c), 'p')}<div><b>${mark(c.name, v)}</b><span>共 ${c.count} 部</span></div></a>`).join('')}</div>` : ''}</div>
        <div>${APP.hd('', '影片与剧集', 'TITLES', `<span>${H.t.length} 部 · 共 ${n} 条结果</span>`)}<div class="grid" style="grid-template-columns:repeat(auto-fill,minmax(13rem,1fr))">${H.t.slice(0, 24).map((i) => APP.pc(i, { cls: 'wrap' })).join('')}</div></div></div>`;
      APP.fill(out); };
    q.addEventListener('input', () => { sel = 0; draw(false); });
    q.addEventListener('keydown', (e) => { if (e.key === 'ArrowDown') { sel++; draw(false); e.preventDefault(); } if (e.key === 'ArrowUp') { sel = Math.max(0, sel - 1); draw(false); e.preventDefault(); } if (e.key === 'Enter') { const on = out.querySelector('.sug a.on'); if (on && sel > 0) on.click(); else draw(true); } if (e.key === 'Escape') { q.value = ''; draw(); } });
    out.addEventListener('click', (e) => { const a = e.target.closest('[data-q]'); if (a) { q.value = a.dataset.q; draw(true); } });
    q.value = { typing: '王', results: '王', long: '剧场版', none: '量子速读' }[st] || ''; draw(st === 'results' || st === 'long'); if (st === 'typing') sel = 1, draw(false); if (!APP.shot || st === 'typing') q.focus();
  },
};
})();
