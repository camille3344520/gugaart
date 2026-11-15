/* 02 发现 · 03 片库（媒体库列表 + 海报墙）· 04 合集（列表 + 整幅铺满的详情）· 05 搜索 */
(function () {
'use strict';
const { ic, im, pad, esc, D, clamp } = APP, DB = window.DB, X = APP.X;

// ------------------------------------------------------------------ the pool: every real title we have (details + 最近添加 of each library + the long-title samples)
const POOL = [], seen = {};
const add = (t, lib) => { if (seen[t.name]) { if (!seen[t.name].lib && lib) seen[t.name].lib = lib; return; } const o = Object.assign({ lib }, t, APP.D[t.name] || {}); if (!o.images) o.images = { poster: t.poster }; seen[t.name] = o; POOL.push(o); };
Object.keys(DB.latest).forEach((lib) => DB.latest[lib].forEach((t) => add(t, lib)));
DB.details.forEach((t) => add(t, t.type === 'Movie' ? '电影' : APP.kind(t) === '动画' ? '动漫' : '电视剧'));
DB.stress.longestTitles.forEach((t, i) => add(Object.assign({ type: i === 1 ? 'Series' : 'Movie', genres: i === 1 || i === 0 || i === 3 || i === 5 ? ['动画'] : ['音乐'], rating: [8.3, 8.2, 8.1, 7.6, 8.0, 7.2][i], runtimeSec: [5220, 0, 10140, 6060, 7800, 6480][i] }, t), i === 1 ? '动漫' : '电影'));
APP.POOL = POOL; APP.byName = (n) => seen[n];
const AZ = 'ABCDEFGHJKLMNOPQRSTWXYZ', BND = '阿八嚓哒妸发旮哈讥咔垃痳拏噢妑七呥扨它穵夕丫帀';
const initial = (s) => { const c = s[0]; if (/[a-z]/i.test(c)) return c.toUpperCase(); if (/[0-9]/.test(c)) return '#'; let r = '#'; for (let i = 0; i < AZ.length; i++) if (c.localeCompare(BND[i], 'zh-CN-u-co-pinyin') >= 0) r = AZ[i]; return r; };
const sub = (t) => `${t.year || '—'} · ${t.rating ? APP.score(t.rating) + ' 分' : '暂无评分'}`;

// ================================================================== 发现
const RANKS = {
  hot: { n: '热播', en: 'MOST PLAYED', note: '按本周播放次数', list: ['航海王', '谷雨', '颜心记', '走马上任', '周处除三害', '盗梦空间', '头文字D', '琅琊榜', '荒野机器人', '士兵突击'], d: [0, 2, -1, 1, 5, 0, -2, 1, 0, -3] },
  top: { n: '高分', en: 'TOP RATED', note: '按评分，真实数据', list: POOL.filter((t) => t.rating).sort((a, b) => b.rating - a.rating).slice(0, 10).map((t) => t.name), d: [] },
  fresh: { n: '新到', en: 'JUST IN', note: '按入库时间，真实数据', list: [...DB.latest.emby302.slice(0, 4), ...DB.latest['电影'].slice(0, 3), ...DB.latest['电视剧'].slice(0, 3)].map((t) => t.name), d: [] },
  cold: { n: '冷门', en: 'UNOPENED', note: '入库半年还没人点开', list: ['超凡动物奇观', '手卷烟', '中国游戏纪事', '局内人', '葫芦小金刚', '围棋少年', '理想照耀中国', '命案', '天行九歌', '森中有林'], d: [] },
};
const LISTS = [['周星驰看一晚上', '23 部 · 从《赌侠》到《新喜剧之王》', ['大话西游之月光宝盒', '鹿鼎记']], ['一个周末补完', '5 部 · 每部不到 24 集', ['龙珠大魔', '谷雨', '子夜归']], ['还剩最后几集', '4 部 · 差一口气看完', ['走马上任', '颜心记', '士兵突击']]];
const rankHTML = (k) => { const R = RANKS[k], top = seen[R.list[0]];
  return R.list.slice(1).map((n, i) => { const t = seen[n], d = R.d[i + 1] || 0; return K.li(pad(i + 2), esc(t.name), `${APP.kind(t)} · ${sub(t)}${d ? ` · <i class="tr ${d > 0 ? 'up' : 'dn'}">${d > 0 ? '▲' : '▼'} ${Math.abs(d)}</i>` : ''}`, t.images.poster, 0, 'po'); }).join(''); };
APP.reg('discover', '发现', {
  title: '发现', note: '榜单页（编的，但用的是真实条目和真实评分）：榜首整幅铺满，下面是带名次的目录；切「热播 / 高分 / 新到 / 冷门」换榜。再往下是年代索引、主编片单、随手翻一部。',
  states: [['', '热播榜'], ['top', '高分榜'], ['mid', '片单 · 年代'], ['dice', '随手翻一部']],
  build: () => ({
    chrome: K.tabbar(1, 'tabs-discover'),
    content: `
<div class="scroll disc">
  <section class="d-hero">${im(D['航海王'].images.backdrop, 'hp', 'object-position:38% 30%')}<i class="h-top"></i><i class="h-bot"></i>
    <div class="d-mast">${K.mast('DISCOVER', '发现', '第 46 周')}</div>
    <div class="d-one"><b class="num">1</b><div><div class="b-kick dk"></div>${APP.logo(D['航海王'], 'dl')}</div></div>
    <p class="d-line"></p>
  </section>
  <div class="b-tabs d-tabs">${Object.keys(RANKS).map((k, i) => `<a data-rank="${k}" class="${i ? '' : 'on'}">${RANKS[k].n}</a>`).join('')}<span class="rn"></span></div>
  <section class="b-sec d-list"></section>
  <section class="b-sec" id="d-dec">
    ${K.head('年代', 'BY DECADE', '232 部')}
    <div class="d-dec">${[['1980s', 4], ['1990s', 31], ['2000s', 38], ['2010s', 67], ['2020s', 92]].map((d) => `<a><b class="num">${d[0]}</b><i style="height:${Math.round(d[1] * 0.7)}px"></i><span>${d[1]} 部</span></a>`).join('')}</div>
  </section>
  <section class="b-sec">
    ${K.head('片单', 'EDITOR’S LISTS', '主编选')}
    ${LISTS.map((l, i) => `<a class="d-pl"><em class="num">${pad(i + 1)}</em><div><b>${l[0]}</b><span>${l[1]}</span></div><div class="fan">${l[2].map((n) => im(seen[n].images.poster)).join('')}</div></a><div class="b-rule"></div>`).join('')}
  </section>
  <section class="b-sec" id="d-dice">
    ${K.head('随手翻', 'LUCKY DIP', '不知道看什么')}
    <div class="d-dice"><div class="dp">${im(seen['荒野机器人'].images.poster)}</div><div><div class="b-kick">翻到第 <span class="num dn">117</span> 页</div><h4 class="dt">荒野机器人</h4><p class="ds">电影 · 2024 · 8.3 分 · 1 小时 42 分</p><a class="b-btn dice">再翻一部</a></div></div>
  </section>
  <div class="b-colo">榜单每周一早上重排 · 只统计这台服务器</div>
</div>${K.band(2)}`,
  }),
  init(root) {
    const set = (k) => { const R = RANKS[k], t = seen[R.list[0]]; root.querySelectorAll('[data-rank]').forEach((a) => a.classList.toggle('on', a.dataset.rank === k)); root.querySelector('.d-list').innerHTML = rankHTML(k); root.querySelector('.rn').textContent = R.note;
      root.querySelector('.dk').textContent = `${R.n}榜 · 第 1 名 · ${APP.kind(t)}`; const d = D[t.name] || t; APP.setImg(root.querySelector('.d-hero .hp'), (d.images.backdrop) || K.wash(t.images.poster)); root.querySelector('.d-hero .hp').style.objectPosition = d.images.backdrop ? (t.name === '航海王' ? '38% 30%' : '50% 20%') : '50% 30%';
      const old = root.querySelector('.d-one .lg'); old.outerHTML = APP.logo(t, 'dl'); root.querySelector('.d-line').textContent = `${t.year} · ${APP.score(t.rating)} 分 · ${APP.len(t)}` + (k === 'hot' ? ' · 本周 37 次播放' : ''); root.style.setProperty('--spot', APP.spotOf(t.images.poster)); APP.fill(root); APP.anim(root, 300); };
    root.addEventListener('click', (e) => { const a = e.target.closest('[data-rank]'); if (a) set(a.dataset.rank);
      if (e.target.closest('.dice')) { const t = POOL[Math.floor(Math.random() * POOL.length)]; root.__dice(t); } });
    root.__dice = (t) => { APP.setImg(root.querySelector('.dp img'), t.images.poster); root.querySelector('.dt').textContent = t.name; root.querySelector('.ds').textContent = `${APP.kind(t)} · ${sub(t)}${t.runtimeSec ? ' · ' + APP.dur(t.runtimeSec) : ''}`; root.querySelector('.dn').textContent = 1 + POOL.indexOf(t) * 3; APP.anim(root, 300); };
    root.__set = set; set('hot');
  },
  setState(root, st) { root.__set(st === 'top' ? 'top' : 'hot'); root.classList.remove('tabs-away'); const sc = root.querySelector('.scroll'); sc.scrollTop = 0; if (st === 'mid') K.scrollTo(root, '#d-dec', 70); if (st === 'dice') { root.__dice(seen['我们仍未知道那天所看见的花的名字。']); sc.scrollTop = 99999; } sc.dispatchEvent(new Event('scroll')); },
});

// ================================================================== 片库：媒体库列表
const LIBEN = { 电视剧: 'SERIES', 电影: 'FILM', 动漫: 'ANIME', emby302: 'DIRECT LINK', 合集: 'COLLECTIONS' };
APP.reg('libs', '片库', {
  title: '媒体库列表', note: '5 个媒体库的真实数量。每个库是目录里的一栏：编号、库名、部数 / 集数、最近入库的三部。emby302 这种英文库名、4,805 集这种大数都在这一屏。',
  states: [['', '媒体库']],
  build: () => ({
    chrome: K.tabbar(2, 'tabs-libs'),
    content: `
<div class="scroll below">
  <div class="b-shead">${K.mast('LIBRARY', '片库', '共 232 部')}</div>
  <div class="l-sum"><span><b class="num">232</b>部</span><span><b class="num">7,985</b>集</span><span><b class="num">12</b>个合集</span><span><b class="num">227</b>部有片名 logo</span></div>
  ${DB.libraries.map((l, i) => { const lt = DB.latest[l.name] || []; return `<a class="l-row"><em class="num">${pad(i + 1)}</em><div><i>${LIBEN[l.name]}</i><b>${l.name}</b><span class="cnt"><u class="num">${APP.num(l.titles)}</u> ${l.type === 'boxsets' ? '个' : '部'}${l.episodes > l.titles ? ` · <u class="num">${APP.num(l.episodes)}</u> 集` : ''}</span><span class="lt">${lt.length ? '新到：' + lt.slice(0, 3).map((t) => esc(t.name)).join(' / ') : '新世纪福音战士新剧场版 / 复仇者联盟 / 逃学威龙'}</span></div><div class="lp">${im(l.image)}</div></a><div class="b-rule"></div>`; }).join('')}
  <div class="b-colo" style="margin-top:26px">— 媒体库由服务器管理员设置 —</div>
</div>${K.band()}`,
  }),
  init() {}, setState() {},
});

// ================================================================== 片库：海报墙
const LIBS = ['电影', '电视剧', '动漫', 'emby302'], CNT = { 电影: 118, 电视剧: 22, 动漫: 32, emby302: 60 };
const FIL = { genre: ['剧情', '喜剧', '动作', '科幻', '动画', '悬疑', '爱情', '音乐'], era: ['2020s', '2010s', '2000s', '更早'], state: ['未看', '在看', '看完', '收藏'], q: ['4K', 'HDR', '杜比视界', 'strm 直链'] };
const SORTS = ['最近添加', '片名', '年份', '评分', '时长'];
const inCW = (t) => DB.continueWatching.some((c) => (c.seriesName || c.name) === t.name);
APP.reg('wall', '片库', {
  title: '海报墙', note: '电影 / 电视剧 / 动漫 / emby302 四面墙。「筛选」「排序」把海报往下推开而不是盖上去，选项是真的在筛；按片名排序时右缘出现字母索引，按住拖动跳转。',
  states: [['', '电影'], ['tv', '电视剧'], ['anime', '动漫 · 长片名'], ['filter', '筛选打开'], ['sort', '排序菜单'], ['az', '字母索引'], ['loading', '加载中'], ['empty', '筛不到']],
  build: () => ({
    chrome: K.tabbar(2, 'tabs-wall'),
    content: `
<div class="scroll below">
  <div class="b-shead">
    ${K.mast('LIBRARY', '片库', '<a class="mback">' + ic('back', 'display:inline-block;font-size:13px;vertical-align:-2px') + ' 媒体库</a>')}
    <div class="b-tabs">${LIBS.map((t, i) => `<a data-tab="${i}" class="${i === 0 ? 'on' : ''}">${t}<sup>${CNT[t]}</sup></a>`).join('')}</div>
    <div class="b-tool"><a data-ftog>${ic('filter')}筛选<em class="fn"></em></a><a data-stog>${ic('sort')}<span class="sn"></span></a><span class="cnt"></span></div>
    <div class="b-sortm">${SORTS.map((s, i) => `<a data-sort="${i}"><i></i>${s}</a>`).join('')}<a data-dir class="dir">${ic('sort')}<span></span></a></div>
    <div class="b-filter">
      ${[['genre', '题材'], ['era', '年代'], ['state', '状态'], ['q', '画质']].map((g) => `<div class="fr"><em>${g[1]}</em><div>${FIL[g[0]].map((o, i) => `<a data-f="${g[0]}:${i}"><i></i>${o}</a>`).join('')}</div></div>`).join('')}
      <div class="ft"><a class="b-link" data-reset>全部重置</a><a class="b-btn" data-ftog><span class="fb"></span></a></div>
    </div>
  </div>
  <div class="b-wait">${K.poseBox('load', K.L.load)}</div>
  <div class="b-skel">${'<div><i></i><u></u><s></s></div>'.repeat(9)}</div>
  <div class="b-wall"></div>
  <div class="b-none b-nores">${K.poseArt('none')}<div><b>这一栏里没有<br>符合条件的片子</b><span>${K.L.none}</span><i>轮廓占位 · ${K.POSES.none.cap}</i><a class="b-link" data-reset style="display:inline-block;margin-top:12px">清掉 <u class="nf"></u> 个筛选条件</a></div></div>
  <div class="b-colo wend"></div>
</div>${K.band()}
<div class="b-az"></div><div class="b-azb"><b></b><span></span></div>`,
  }),
  init(root) {
    const S = { tab: 0, sort: 0, desc: true, genre: -1, era: -1, state: -1, q: -1 }, wall = root.querySelector('.b-wall'), az = root.querySelector('.b-az'), sc = root.querySelector('.scroll');
    const draw = () => {
      let list = POOL.filter((t) => t.lib === LIBS[S.tab]);
      if (S.genre >= 0) list = list.filter((t) => APP.genre(t, 9).includes(FIL.genre[S.genre]));
      if (S.era >= 0) list = list.filter((t) => { const y = t.year || 0; return S.era === 0 ? y >= 2020 : S.era === 1 ? y >= 2010 && y < 2020 : S.era === 2 ? y >= 2000 && y < 2010 : y < 2000; });
      if (S.state === 1) list = list.filter(inCW); if (S.state === 0) list = list.filter((t) => !inCW(t)); if (S.state === 2) list = list.filter((t) => t.user && t.user.played); if (S.state === 3) list = list.filter((t) => t.user && t.user.favorite);
      if (S.q >= 0) list = list.filter((t) => S.q === 3 ? t.lib === 'emby302' : (t.name.length + S.q) % 3 === 0);
      const cmp = [null, (a, b) => a.name.localeCompare(b.name, 'zh-CN-u-co-pinyin'), (a, b) => (a.year || 0) - (b.year || 0), (a, b) => (a.rating || 0) - (b.rating || 0), (a, b) => (a.runtimeSec || 0) - (b.runtimeSec || 0)][S.sort];
      if (cmp) { list.sort(cmp); if (S.desc) list.reverse(); } else if (!S.desc) list.reverse();
      const second = (t) => S.sort === 4 ? (t.type === 'Movie' ? APP.dur(t.runtimeSec) : `${t.episodeCount || '—'} 集`) : S.sort === 0 ? `${t.year || '—'} · ${APP.genre(t, 1) || APP.kind(t)}` : sub(t);
      let last = '';
      wall.innerHTML = list.map((t) => { const L = initial(t.name), first = S.sort === 1 && L !== last; last = L; return `<a class="b-po"${first ? ` data-az="${L}"` : ''}>${im(t.images.poster)}${inCW(t) ? '<i class="pg"></i>' : ''}<b>${esc(t.name)}</b><span>${second(t)}</span></a>`; }).join(''); APP.fill(wall);
      const letters = [...new Set(list.map((t) => initial(t.name)))]; az.innerHTML = S.sort === 1 ? ['#', ...AZ].map((l) => `<a class="${letters.includes(l) ? 'has' : ''}">${l}</a>`).join('') : ''; root.classList.toggle('has-az', S.sort === 1);
      const nf = (S.genre >= 0) + (S.era >= 0) + (S.state >= 0) + (S.q >= 0), total = CNT[LIBS[S.tab]];
      root.querySelector('.fn').textContent = nf ? ' ' + nf : ''; root.querySelector('.sn').textContent = SORTS[S.sort] + (S.desc ? ' ↓' : ' ↑'); root.querySelector('.nf').textContent = nf;
      root.querySelector('.cnt').textContent = nf ? `筛出 ${list.length} / ${total} 部` : `${total} 部`; root.querySelector('.fb').textContent = `看这 ${nf ? list.length : total} 部`;
      root.querySelector('.dir span').textContent = S.sort === 1 ? (S.desc ? 'Z → A' : 'A → Z') : (S.desc ? '从高到低' : '从低到高');
      root.querySelector('.wend').textContent = nf || !list.length ? '— 本栏到此 —' : `— 已印出 ${list.length} / ${total} 部，往下拉继续 —`;
      root.querySelectorAll('[data-f]').forEach((a) => { const [g, i] = a.dataset.f.split(':'); a.classList.toggle('on', S[g] === +i); });
      root.querySelectorAll('[data-sort]').forEach((a) => a.classList.toggle('on', +a.dataset.sort === S.sort));
      root.querySelectorAll('[data-tab]').forEach((a) => a.classList.toggle('on', +a.dataset.tab === S.tab));
      root.querySelector('[data-ftog]').classList.toggle('on', root.classList.contains('filter')); root.querySelector('[data-stog]').classList.toggle('on', root.classList.contains('sorting'));
      root.classList.toggle('nores', !list.length); APP.anim(root, 300);
    };
    const jump = (L, show) => { const el = wall.querySelector(`[data-az="${L}"]`); az.querySelectorAll('a').forEach((a) => a.classList.toggle('on', a.textContent === L)); if (el) sc.scrollTop += (el.getBoundingClientRect().top - sc.getBoundingClientRect().top) / APP.scaleOf(sc) - 8; const b = root.querySelector('.b-azb'); b.querySelector('b').textContent = L; b.querySelector('span').textContent = el ? el.querySelector('b').textContent : '这个字母下没有'; root.classList.toggle('az-on', !!show); APP.dirty(root); };
    root.addEventListener('click', (e) => {
      const f = e.target.closest('[data-f]'), tb = e.target.closest('[data-tab]'), so = e.target.closest('[data-sort]');
      if (f) { const [g, i] = f.dataset.f.split(':'); S[g] = S[g] === +i ? -1 : +i; draw(); }
      else if (so) { S.sort = +so.dataset.sort; S.desc = S.sort !== 1; draw(); }
      else if (e.target.closest('[data-dir]')) { S.desc = !S.desc; draw(); }
      else if (tb) { S.tab = +tb.dataset.tab; sc.scrollTop = 0; draw(); }
      else if (e.target.closest('[data-reset]')) { S.genre = S.era = S.state = S.q = -1; draw(); }
      else if (e.target.closest('[data-ftog]')) { root.classList.toggle('filter'); root.classList.remove('sorting'); draw(); }
      else if (e.target.closest('[data-stog]')) { root.classList.toggle('sorting'); root.classList.remove('filter'); draw(); }
    });
    az.addEventListener('pointerdown', (e) => { const at = (ev) => { const a = document.elementFromPoint(ev.clientX, ev.clientY); if (a && a.parentElement === az) jump(a.textContent, true); }; at(e); const up = () => { root.classList.remove('az-on'); window.removeEventListener('pointermove', at); window.removeEventListener('pointerup', up); }; window.addEventListener('pointermove', at); window.addEventListener('pointerup', up); e.preventDefault(); });
    root.__lib = { S, draw, jump }; draw();
  },
  setState(root, st) {
    const { S, draw, jump } = root.__lib; S.genre = S.era = S.state = S.q = -1; S.sort = 0; S.desc = true; S.tab = st === 'tv' ? 1 : st === 'anime' ? 2 : 0;
    root.classList.toggle('filter', st === 'filter' || st === 'empty'); root.classList.toggle('sorting', st === 'sort'); root.classList.toggle('loading', st === 'loading'); root.classList.remove('az-on', 'tabs-away');
    if (st === 'filter') { S.genre = 1; S.era = 3; } if (st === 'empty') { S.genre = 7; S.state = 1; S.q = 2; } if (st === 'sort') { S.sort = 3; } if (st === 'az' || st === 'anime') { S.sort = 1; S.desc = false; }
    root.querySelector('.scroll').scrollTop = 0; draw(); if (st === 'az') jump('H', true);
  },
});

// ================================================================== 合集
const SETS = DB.collections.map((c) => Object.assign({}, c, (X.collections || []).find((x) => x.id === c.id) || {}));
APP.reg('sets', '合集', {
  title: '合集列表', note: '12 个合集（真实）。合集名有中文长名，也有「Fight Back to School Collection」这种英文原名，两行排完，不截断。',
  states: [['', '合集']],
  build: () => ({
    content: `
<div class="scroll">
  <div class="b-set s-head"><div class="b-kick">合集 · COLLECTIONS</div><h3>合集</h3><div class="b-idx"><a class="on">全部 <i class="num">12</i></a><a>电影系列 <i class="num">10</i></a><a>动画 <i class="num">2</i></a><span>共 34 部影片</span></div></div>
  <div class="s-grid">${SETS.map((c, i) => `<a class="s-card"><div class="pp">${im(c.poster)}<i></i><i></i></div><em class="num">${pad(i + 1)}</em><b>${esc(c.name)}</b><span>${c.count} 部</span></a>`).join('')}</div>
  <div class="b-colo short" style="margin-top:30px">— 合集由服务器按系列自动归并 —</div>
</div>${K.top('合集', `<a>${ic('search')}</a>`, 'paper')}`,
  }),
  init() {}, setState() {},
});
const SETK = { evA: '40818', school: '42532', avengers: '41634' };
const setHTML = (c) => { const ms = c.members, ys = ms.map((m) => m.year), tot = ms.reduce((a, m) => a + (m.runtimeSec || 0), 0), cn = /[一-龥]/.test(c.name);
  return `
  ${K.hero(c.backdrop, { h: 430, pos: c.id === '40818' ? '50% 30%' : c.id === '42532' ? '58% 20%' : '50% 20%', cap: ['剧照', c.backdropFrom === 'collection' ? '合集题图' : `取自《${esc(ms[0].name)}》`] })}
  <div class="b-dbody up">
    <div class="b-kick">合集 · ${ms.length} 部 · ${Math.min(...ys)} — ${Math.max(...ys)}</div>
    <div class="b-dhead"><span class="lg txt${cn ? '' : ' en'}" style="--fs:${cn ? (c.name.length > 10 ? 0.5 : 0.7) : 0.5}">${esc(c.name)}</span><div class="b-score"><b class="num">${ms.length}</b><span>部 · 共 ${APP.dur(tot)}</span></div></div>
    <div class="b-by"><span>均分<b>${APP.score(ms.reduce((a, m) => a + m.rating, 0) / ms.length)}</b></span><span>已看<b>0 / ${ms.length}</b></span><span>最新<b>${Math.max(...ys)}</b></span></div>
    <div class="b-play"><div><a class="b-btn wide">${ic('play')}<span class="cta-t" style="margin:0;font:inherit;opacity:1">从第一部看起 · ${esc(ms[0].name.split('：').pop())}</span><span class="num cta-s">${APP.dur(ms[0].runtimeSec)}</span></a><div class="b-pg"><u style="width:0"></u></div></div></div>
    <div class="b-links"><span class="fv"><a data-fav>${ic('heart')}<span class="ft">收藏</span></a>${K.favSlot()}</span><a>${ic('check')}整套已看</a><a>${ic('more')}更多</a></div>
    <p class="b-lead clamp"><span class="dc">${esc(c.overview.trim()[0])}</span>${esc(c.overview.trim().slice(1).replace(/[\r\n\u3000]+/g, '').replace(/\s+/g, ' '))}</p><a class="b-more" data-more>展开全文（${c.overview.length} 字）</a>
    <section class="b-sec" style="margin-top:34px">
      ${K.head('本辑', 'IN THIS SET', '按上映顺序')}
      ${ms.map((m, i) => K.li(pad(i + 1), esc(m.name), `${m.year} · ${APP.score(m.rating)} 分 · ${APP.dur(m.runtimeSec)}`, m.poster, 0, 'po ep', K.mk())).join('')}
    </section>
    <div class="b-colo short">— 本辑完 —</div>
  </div>`; };
APP.reg('set', '合集', {
  title: '合集详情', note: '整幅铺满：合集的剧照从屏幕最顶端铺下来。合集没有 logo，片名退回宋体；14 个字的中文名、英文原名、600 多字的简介都在这里试。',
  states: [['', '新世纪福音战士（长名 · 长简介）'], ['list', '本辑 4 部'], ['school', '英文名 · 只有 2 部'], ['avengers', '复仇者联盟'], ['fav', '盖章']],
  build: () => ({ content: `<div class="scroll"><div class="set-in">${setHTML(SETS.find((c) => c.id === SETK.evA))}</div></div>${K.top('', undefined, 300)}` }),
  init(root) { K.common(root); root.addEventListener('click', (e) => { if (e.target.closest('[data-more]')) { root.querySelector('.b-lead').classList.toggle('clamp'); e.target.closest('[data-more]').textContent = root.querySelector('.b-lead').classList.contains('clamp') ? '展开全文' : '收起'; } });
    root.__show = (k) => { const c = SETS.find((x) => x.id === SETK[k]); root.querySelector('.set-in').innerHTML = setHTML(c); root.querySelector('.tt').textContent = c.name.length > 12 ? c.name.slice(0, 11) + '…' : c.name; root.style.setProperty('--spot', APP.spotOf(c.poster)); K.setFav(root, false); APP.fill(root); }; root.__show('evA'); },
  setState(root, st) { root.__show(SETK[st] ? st : 'evA'); K.setFav(root, st === 'fav'); root.querySelector('.scroll').scrollTop = 0; if (st === 'list') K.scrollTo(root, '.b-sec', 110); },
});

// ================================================================== 搜索
const EPS = []; Object.keys(DB.series).forEach((sn) => DB.series[sn].episodes.forEach((e) => EPS.push(Object.assign({ sn }, e))));
const PEOPLE = {}; DB.details.forEach((d) => d.people.forEach((p) => { (PEOPLE[p.name] = PEOPLE[p.name] || { name: p.name, image: p.image, in: [] }).in.push(d.name); if (p.image) PEOPLE[p.name].image = p.image; }));
const hl = (s, q) => esc(s).split(esc(q)).join(`<mark>${esc(q)}</mark>`);
function results(q) {
  q = q.trim(); if (!q) return { h: '', n: -1 };
  const ts = POOL.filter((t) => t.name.includes(q) || String(t.year) === q || APP.genre(t, 9).includes(q)), es = EPS.filter((e) => e.name.includes(q)), ps = Object.values(PEOPLE).filter((p) => p.name.includes(q));
  const n = ts.length + es.length + ps.length; let h = '';
  if (!n) return { n: 0, h: K.none('none', `片库里没有<br>「${esc(q)}」`, K.L.none, `<span class="alt">试试：<a>星际</a> / <a>诺兰</a> / <a>科幻</a></span>`) };
  if (ts.length) { const b = ts.find((t) => D[t.name]) || ts[0], d = D[b.name] || b, rest = ts.filter((t) => t !== b);
    h += `<section class="b-sec">${K.head('片', 'TITLES', String(ts.length))}<a class="b-best">${im(b.images.poster)}<div>${APP.logo(d, 'bl')}<p><b>${d.year} · ${APP.genre(d)} · ${APP.len(d)}</b>${d.overview ? '<br>' + esc(d.overview.slice(0, 44)) + '……' : ''}</p><span class="go">${inCW(b) ? '续看' : '翻开'} ${ic('arrow')}</span></div></a><div class="b-rule"></div>` +
      rest.slice(0, 4).map((t, i) => K.li(pad(i + 2), hl(t.name, q), `${APP.kind(t)} · ${sub(t)}`, t.images.poster, 0, 'po')).join('') + (rest.length > 4 ? `<a class="b-all">还有 ${rest.length - 4} 部 ${ic('arrow')}</a>` : '') + `</section>`; }
  if (es.length) h += `<section class="b-sec">${K.head('单集', 'EPISODES', String(es.length))}` + es.slice(0, 3).map((e) => K.li('E' + e.e, hl(e.name, q), `${esc(e.sn)} · 第 ${e.s} 季${e.played ? ' · 已看' : ''}`, e.thumb, 0, 'sm', '', e.sn)).join('') + (es.length > 3 ? `<a class="b-all">还有 ${es.length - 3} 集 ${ic('arrow')}</a>` : '') + `</section>`;
  if (ps.length) h += `<section class="b-sec">${K.head('演员', 'PEOPLE', String(ps.length))}` + ps.slice(0, 4).map((p) => `<a class="b-who" style="margin:0 24px">${p.image ? im(p.image) : '<i class="nop"></i>'}<div><b>${hl(p.name, q)}</b><span>演员 · 《${esc(p.in[0])}》${p.in.length > 1 ? `等 ${p.in.length} 部` : ''}</span></div><em>人物页 ›</em></a><div class="b-rule"></div>`).join('') + `</section>`;
  return { h, n, c: [ts.length, es.length, ps.length] };
}
const suggest = (q) => { const ts = POOL.filter((t) => t.name.includes(q)).slice(0, 4), ps = Object.values(PEOPLE).filter((p) => p.name.includes(q)).slice(0, 2);
  return `<div class="b-sug">` + ts.map((t) => `<a>${ic('search')}<b>${hl(t.name, q)}</b><span>${APP.kind(t)} · ${t.year || ''}</span></a>`).join('') + ps.map((p) => `<a>${ic('user')}<b>${hl(p.name, q)}</b><span>演员</span></a>`).join('') + `<a class="all">${ic('arrow')}<b>搜「${esc(q)}」的全部结果</b><span>回车</span></a></div>`; };
const KEYS = ['qwertyuiop', 'asdfghjkl', 'zxcvbnm'];
APP.reg('search', '搜索', {
  title: '搜索', note: '一页索引，大字输入框可以真的打字（试试 航海 / 周星驰 / 2026 / 喜剧）。空状态是找过的和大家在找的；输入中出联想，键盘弹起时标签栏让开；结果按 片 / 单集 / 演员 分组。',
  states: [['empty', '空状态'], ['typing', '输入中'], ['results', '有结果'], ['results2', '结果 · 单集 / 演员'], ['long', '最难：长片名'], ['none', '搜不到']],
  build: () => ({
    chrome: K.tabbar(0, 'tabs-search', true),
    content: `
<div class="scroll below">
  <div class="b-shead">
    ${K.mast('INDEX', '检索', '共 232 部')}
    <div class="b-input"><input value="" placeholder="找点什么看" spellcheck="false"><a class="clr">清除</a></div>
    <div class="b-thick"></div>
    <div class="b-count"></div>
  </div>
  <div class="b-res"></div>
  <div class="b-idle">
    ${K.none('ask', '想看点什么？', K.L.ask)}
    <section class="b-sec b-recent">${K.head('找过', 'RECENT', '清空')}${[['颜心记', '昨天'], ['周星驰', '周二'], ['头文字', '上周'], ['2026', '上周']].map((r, i) => `<a><em>${i + 1}</em>${r[0]}<span>${r[1]}</span></a><div class="b-rule"></div>`).join('')}</section>
    <section class="b-sec">
      ${K.head('大家在找', 'TRENDING')}
      <div class="b-cloud"><a class="l">航海王</a><i>/</i><a class="m">喜剧</a><i>/</i><a class="s">2026</a><i>/</i><a class="l r">周星驰</a><i>/</i><a class="s">动画</a><i>/</i><a class="m">科幻</a><i>/</i><a class="l">仙剑</a><i>/</i><a class="s">悬疑</a><i>/</i><a class="m">福音战士</a><i>/</i><a class="s">星</a></div>
    </section>
    <div class="b-colo">按片名、单集名、演员、年份、题材检索</div>
  </div>
</div>${K.band()}
<div class="b-kbd"><div class="cand"><b>hang hai</b><span>航海</span><span>行害</span><span>航</span><span>行</span></div>${KEYS.map((r, i) => `<div class="kr r${i}">${i === 2 ? '<i class="w">⇧</i>' : ''}${[...r].map((k) => `<i>${k}</i>`).join('')}${i === 2 ? '<i class="w">⌫</i>' : ''}</div>`).join('')}<div class="kr"><i class="w">符</i><i class="w">123</i><i class="sp">系统键盘（示意）</i><i class="w go">搜索</i></div></div>`,
  }),
  init(root) {
    const inp = root.querySelector('input'), res = root.querySelector('.b-res'), cnt = root.querySelector('.b-count'), idle = root.querySelector('.b-idle');
    const run = (typing) => {
      const q = inp.value.trim(); root.classList.toggle('typing', !!typing);
      if (typing && q) { res.innerHTML = suggest(q); idle.style.display = 'none'; cnt.textContent = '输入中 · 点联想词或回车'; APP.anim(root, 200); return; }
      const r = results(q); res.innerHTML = r.h; APP.fill(res); idle.style.display = r.n < 0 ? '' : 'none';
      cnt.textContent = r.n < 0 ? '输入片名、单集名、演员、年份或题材' : r.n ? `${r.n} 条结果：片 ${r.c[0]} · 单集 ${r.c[1]} · 演员 ${r.c[2]}` : '0 条结果 · 来自 5 个媒体库';
      APP.anim(root, 200);
    };
    inp.addEventListener('input', () => run(true)); inp.addEventListener('keydown', (e) => { if (e.key === 'Enter') run(false); });
    root.querySelector('.clr').addEventListener('click', () => { inp.value = ''; run(); inp.focus(); });
    root.addEventListener('click', (e) => { const a = e.target.closest('.b-cloud a, .b-recent a, .b-sug a, .alt a'); if (a) { const b = a.querySelector('b'); inp.value = a.classList.contains('all') ? inp.value : b ? b.textContent : a.childNodes[a.childNodes.length > 1 ? 1 : 0].textContent.trim(); run(); root.querySelector('.scroll').scrollTop = 0; } });
    root.__run = (q, typing) => { inp.value = q; run(typing); }; run();
  },
  setState(root, st) { root.classList.remove('tabs-away'); root.__run({ results: '星', results2: '星', long: '新世纪', none: '星际穿越', typing: '航海' }[st] || '', st === 'typing'); const sc = root.querySelector('.scroll'); sc.scrollTop = 0; if (st === 'results2') K.scrollTo(root, '.b-res .b-sec:nth-child(2)', 60); sc.dispatchEvent(new Event('scroll')); },
});
})();
