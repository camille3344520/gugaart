/* 发现 · 片库（媒体库列表 / 海报墙 / 筛选 / 加载 / 空）· 合集（列表 / 详情）· 搜索 */
(function () {
'use strict';
const APP = window.APP, U = window.U, L = window.LIB, X = window.EXTRA, { ic, im, esc, pad } = APP, F = APP.F;
const ph = (cn, en, right, mid) => `<header class="ph"><b>${cn}</b><i>${en}</i>${mid || ''}<span>${right || ''}</span><em>21:47</em></header>`;
U.ph = ph;

// ------------------------------------------------------------------ 发现（副刊）：左页一张整幅剧照的「今日一篇」，右页高分榜；下面题材 / 年代索引
const RANK = ['女子当参政', '尚公主', '颜心记', '谷雨', '琅琊榜', '葫芦小金刚'].map((n) => U.byName(n));
const PICKS = ['盗梦空间', '阿凡达：水之道', '航海王'].map((n) => U.det(n));
const GENRES = ['剧情', '喜剧', '悬疑', '动画', '科幻', '动作冒险', '爱情', '奇幻', '历史', '音乐', '纪录'];
APP.SCREENS.discover = {
  title: '发现', nav: 'discover', note: '副刊：左页是一张整幅剧照的「今日一篇」，右页是高分榜。焦点走到榜上哪一部，左页就换成那一部。',
  path: '<b>方向键路径</b>　默认焦点在「播放」。左右：播放 ↔ 换一篇 ↔ 高分榜；高分榜里上下换名次（左页跟着换图）；下：题材索引 → 年代索引（左右走格子）。最左再按左 = 进导航。',
  states: [['', '今日一篇'], ['rank', '焦点在高分榜（10 分）'], ['genre', '题材索引']],
  render(st) {
    const o = st === 'rank' ? RANK[0] : PICKS[0];
    return `<div class="dc"><div class="dc-bd"><img class="bd" data-img="${APP.key(U.I(o, 'backdrop'))}" data-pre="${RANK.concat(PICKS).map((x) => [APP.key(U.I(x, 'backdrop')), APP.key(U.I(x, 'logo'))].filter(Boolean).join(',')).join(',')}" alt=""><div class="sh1"></div><div class="sh2"></div></div>
    ${U.mast()}
    <section class="dc-lead"><div class="kick k1">今日一篇 · TODAY'S PICK</div><div class="lgw"></div><div class="kick k2"></div><p class="stand"></p>
      <div class="cta" data-fg>${U.btn('播放', { ic: 'play', cls: 'pri', attr: st === '' ? 'data-f0' : '' })}${U.btn('换一篇', { ic: 'replay', attr: 'data-act="shuffle"' })}${U.btn('详情', { ic: 'info' })}</div></section>
    <aside class="dc-rank"><div class="sec-h"><em>壹</em><b>高分榜</b><i>TOP RATED</i><u></u></div><div data-fg>${RANK.map((r, i) => `<a class="li rk" data-f ${st === 'rank' && i === 0 ? 'data-f0' : ''} data-id="${r.id}"><em>${i + 1}</em>${im(U.I(r, 'poster'), 'tp')}<b>${esc(r.name)}</b><small>${U.kind(r)} · ${r.year}</small><span class="num">${U.rate(r.rating)}</span></a>`).join('')}</div></aside>
    <section class="dc-idx"><div class="sec-h"><em>贰</em><b>题材</b><i>BY GENRE</i><u></u></div><div class="chips" data-fg>${GENRES.map((g, i) => `<a class="chip" data-f ${st === 'genre' && i === 2 ? 'data-f0' : ''}>${g}</a>`).join('')}</div>
      <div class="sec-h"><em>叁</em><b>年代</b><i>BY DECADE</i><u></u><span>1989 — 2026</span></div><div class="chips" data-fg>${['1980s', '1990s', '2000s', '2010s', '2020s'].map((g) => `<a class="chip num" data-f>${g}</a>`).join('')}<a class="chip" data-f>${ic('replay')}随手翻一部</a></div></section></div>`;
  },
  init(root, st) { root.__pick = 0; this.put(root, st === 'rank' ? RANK[0] : PICKS[0], st === 'rank'); },
  put(root, o, ranked) { const $ = (s) => root.querySelector(s); $('.lgw').innerHTML = U.logo(o); $('.k1').textContent = ranked ? `高分榜 · ★ ${U.rate(o.rating)}` : "今日一篇 · TODAY'S PICK"; $('.k2').textContent = U.kick(o) + (o.rating ? ` · ★ ${U.rate(o.rating)}` : ''); $('.stand').textContent = U.stand(o, 70);
    APP.need([APP.key(U.I(o, 'logo')), APP.key(U.I(o, 'poster'))]).then(() => { APP.fill(root); APP.fixLogos(root); root.style.setProperty('--spot', APP.spotOf(U.I(o, 'poster'))); }); APP.setImg($('.bd'), U.I(o, 'backdrop')); },
  onFocus(root, el) { if (el.classList.contains('rk')) this.put(root, U.BY[el.dataset.id], true); },
  ok(root, c) { if (c.dataset.act === 'shuffle') { root.__pick = (root.__pick + 1) % PICKS.length; this.put(root, PICKS[root.__pick]); } else APP.toast('打开 →（见详情 / 片库画板）'); },
};

// ------------------------------------------------------------------ 片库：媒体库列表
const LIBS = L.libraries, LATEST = (n) => L.latest[n] && L.latest[n].length ? L.latest[n] : X.collections.map((c) => ({ id: c.id, name: c.name, images: { poster: L.collections.find((x) => x.id === c.id).poster } }));
APP.SCREENS.libs = {
  title: '片库 · 媒体库列表', nav: 'lib', note: '五个媒体库排成一页带编号的目录。右页跟着焦点换：那一库的题图和最新四张海报。',
  path: '<b>方向键路径</b>　上下换媒体库（右页跟着换）；确定 = 进海报墙；左 = 进导航。右页只是预览，不接焦点。',
  states: [['', '焦点在「电影」'], ['anime', '动漫 · 4,805 集']],
  render(st) { const f = st === 'anime' ? 2 : 1;
    return `<div class="lbs">${ph('片库', 'LIBRARY', '5 个媒体库 · 232 部')}
      <div class="lb-l" data-fg>${LIBS.map((l, i) => `<a class="li big" data-f ${i === f ? 'data-f0' : ''} data-lib="${i}"><em>${pad(i + 1)}</em><b>${esc(l.name)}</b><span><i class="num">${l.titles}</i> 部${l.episodes > l.titles ? `<i class="num">${l.episodes.toLocaleString()}</i> 集` : ''}</span></a>`).join('')}</div>
      <aside class="lb-r"><div class="frame"><img alt="" data-pre="${LIBS.map((l) => APP.key(l.image)).join(',')}"></div><div class="cap"><b></b><span></span></div><div class="four"></div></aside></div>`; },
  onFocus(root, el) { if (el.dataset.lib === undefined) return; const l = LIBS[+el.dataset.lib], $ = (s) => root.querySelector(s); APP.setImg($('.frame img'), l.image); $('.lb-r .cap b').textContent = '最新入库'; $('.lb-r .cap span').textContent = l.type === 'boxsets' ? '12 套合集' : '最近添加的四部';
    const four = LATEST(l.name).slice(0, 4); $('.four').innerHTML = four.map((o) => `<span>${im(U.I(o, 'poster'))}<b>${esc(o.name)}</b></span>`).join(''); APP.need(four.map((o) => APP.key(U.I(o, 'poster')))).then(() => APP.fill(root)); },
  ok() { APP.toast('进海报墙 →（见「片库 · 海报墙」画板）'); },
};

// ------------------------------------------------------------------ 片库：海报墙（7 列）/ 筛选排序 / 加载中 / 筛不到 / 长按菜单
const MOV = U.MOVIES();
const FILTERS = [['排序', ['加入时间', '片名', '年份', '评分', '最近播放'], 0], ['题材', ['全部', '动作', '科幻', '喜剧', '剧情', '动画', '爱情'], 0], ['年代', ['全部', '2020s', '2010s', '2000s', '更早'], 0], ['状态', ['全部', '未看', '在看', '已看', '已收藏'], 0]];
const MENU = (o, kind) => `<div class="cmenu scope open"><div class="cm-h"><em>批注 · MARGINALIA</em><b>${esc(o)}</b></div><div data-fg>` +
  (kind === 'ep' ? [['play', '从这一集播放'], ['replay', '从头播放'], ['check', '标记已看（画校对勾）'], ['list', '标记到这一集为止都已看'], ['dl', '下载这一集'], ['info', '这一集的详情']] : [['play', '播放'], ['info', '查看详情'], ['heart', '收藏（盖章）'], ['check', '标记已看（画校对勾）'], ['layers', '所属合集'], ['dl', '下载']])
    .map((x, i) => `<a class="li" data-f ${i === 0 ? 'data-f0' : ''}>${ic(x[0])}<b>${x[1]}</b></a>`).join('') + `</div><p>返回键收起</p></div>`;
U.MENU = MENU;
const wall = (list, o) => `<div class="wall${o && o.cls ? ' ' + o.cls : ''}">${list.map((m, i) => U.poster(m, Object.assign({ attr: `data-menu="menu" ${o && o.f0 === i ? 'data-f0' : ''}` }, o && o.card ? o.card(m, i) : {}))).join('')}</div>`;
APP.SCREENS.lib = {
  title: '片库 · 海报墙', nav: 'lib', menu: 'filter', note: '7 列海报墙，用的是全库片名最长的几部。长按确定出这张海报的菜单，菜单键出筛选 / 排序：筛选栏把墙挤成 5 列，不盖海报。',
  path: '<b>方向键路径</b>　四个方向在墙上走，焦点所在的一排吸到顶；最左再按左 = 进导航；菜单键（M）= 筛选 / 排序（焦点进右栏，上下换格子、确定选中，返回收起）；长按确定 = 这张海报的菜单。',
  states: [['', '海报墙 · 最长片名'], ['row2', '第二排'], ['filter', '筛选 / 排序'], ['loading', '加载中'], ['empty', '筛不到'], ['menu', '长按确定的菜单']],
  render(st) {
    const filt = st === 'filter' || st === 'empty', sel = st === 'empty' ? [3, 5, 4, 3] : [0, 0, 0, 0];
    const head = ph('电影', 'MOVIES', st === 'empty' ? '筛出 0 部 · 共 118 部' : `118 部 · 按${FILTERS[0][1][sel[0]]} · 全部题材`, U.hint([['菜单键', '筛选 / 排序'], ['长按确定', '菜单']]).replace('class="hint"', 'class="hint inl"'));
    const panel = filt ? `<aside class="fl scope open"><div class="sec-h"><em>¶</em><b>筛选</b><i>FILTER &amp; SORT</i><u></u></div>${FILTERS.map((g, gi) => `<h4>${g[0]}</h4><div class="chips" data-fg>${g[1].map((c, i) => `<a class="chip${i === sel[gi] ? ' on' : ''}" data-f ${st === 'filter' && gi === 1 && i === 2 ? 'data-f0' : ''} data-g="${gi}">${c}${gi === 0 && i === sel[0] ? '<small>↓</small>' : ''}</a>`).join('')}</div>`).join('')}
      <div class="fl-b">${U.btn('清除筛选', { ic: 'x', attr: st === 'empty' ? 'data-f0 data-go=""' : 'data-go=""' })}</div></aside>` : '';
    let body;
    if (st === 'loading') body = `<div class="wall sk">${Array.from({ length: 14 }, (_, i) => `<span class="skc"><i class="num">${pad(i + 1)}</i></span>`).join('')}</div><div class="wait">${U.pose({ wl: 25, wr: 55, eyes: '-', props: ['sheet'] }, '排版：低头摆稿纸', '正在排版，118 张海报马上印好。')}</div>`;
    else if (st === 'empty') body = `<div class="none">${U.pose({ wl: 78, wr: 78, eyes: '-', mouth: 'o', tilt: -6, props: ['sweat'] }, '摊手：两翅摊开、歪头', '「评分最高 · 动画 · 更早 · 已看」一部都没有。把筛选放宽一点试试。')}</div>`;
    else body = `<div class="vs wv" data-rs="34">${wall(MOV, { f0: st === 'row2' ? 10 : st === 'menu' ? 2 : 0 })}<div style="height:700px"></div></div>`;
    return `<div class="lw${filt ? ' filt' : ''}">${head}${body}${panel}${st === 'menu' ? MENU(MOV[2].name) : ''}</div>`;
  },
  init(root, st) { if (st === 'menu') { const c = root.querySelector('.wall .card:nth-child(3)'); c.classList.add('held'); const m = root.querySelector('.cmenu'); m.style.left = '900px'; m.style.top = '190px'; root.classList.add('dim'); } },
  ok(root, c) { if (c.classList.contains('chip')) { c.parentElement.querySelectorAll('.chip').forEach((x) => x.classList.toggle('on', x === c)); return; } APP.toast(c.closest('.cmenu') ? '已执行（演示）' : '打开详情 →（见「电影详情」画板）'); },
};

// ------------------------------------------------------------------ 合集
const COLL = L.collections;
APP.SCREENS.colls = {
  title: '合集列表', nav: 'coll', note: '12 套合集。服务器上的合集名很多是英文长名（真实数据），两行排得下，第三行截断。',
  path: '<b>方向键路径</b>　同海报墙：四个方向走格子，最左再按左 = 进导航，确定 = 打开合集。',
  states: [['', '焦点在最长的中文名'], ['en', '英文长名']],
  render(st) { return `<div class="lw">${ph('合集', 'COLLECTIONS', '12 套 · 共 34 部')}<div class="vs wv" data-rs="34">${wall(COLL.map((c) => ({ id: c.id, name: c.name, images: { poster: c.poster }, count: c.count })), { f0: st === 'en' ? 7 : 0, card: (m) => ({ sub: `${m.count} 部`, no: 1 }) })}<div style="height:700px"></div></div></div>`; },
  ok() { APP.toast('打开合集 →（见「合集详情」画板）'); },
};
const mem = (c) => c.members.map((m) => U.BY[m.id]);
APP.SCREENS.coll = {
  title: '合集详情（整幅铺满）', nav: 'coll', note: '合集的横版剧照从屏幕最顶端铺满，往下渐隐进纸面；片名、简介压在左下的暗角里，成员按年份编号排在下面。合集没有 logo（真实数据），片名用宋体。',
  path: '<b>方向键路径</b>　默认焦点在「从第一部看起」。左右：按钮之间；下：成员一排（左右走，焦点卡停在左起第一格）；上回按钮。左到头 = 进导航。',
  states: [['', '复仇者联盟 · 4 部'], ['eva', '超长片名 + 280 字简介'], ['two', '只有 2 部']],
  render(st) { const c = X.collections[st === 'eva' ? 0 : st === 'two' ? 1 : 2], ms = mem(c), ys = ms.map((m) => m.year);
    return `<div class="dt full"><div class="dt-bd co"><img class="bd" data-img="${APP.key(c.backdrop)}" alt=""><div class="sh1"></div><div class="sh2"></div></div>
      ${U.head('合集', 'COLLECTION', `<span>剧照｜${c.backdropFrom === 'collection' ? '合集题图' : '《' + esc(c.backdropFrom) + '》'}</span>`)}
      <section class="co-h"><div class="kick">合集 · ${c.members.length} 部 · ${Math.min(...ys)} — ${Math.max(...ys)} · 共 ${U.dur(ms.reduce((a, m) => a + (m.runtimeSec || 0), 0))}</div>
        ${U.logo({ name: c.name })}<p class="lead">${esc(U.cut(c.overview, 150))}</p>
        <div class="cta" data-fg>${U.btn('从第一部看起', { ic: 'play', cls: 'pri', attr: st === 'eva' ? '' : 'data-f0' })}${U.favBtn(false)}${U.btn('全部标记已看', { ic: 'check' })}</div></section>
      <section class="co-m">${U.sec('', '本合集', 'IN THIS SET', `${c.members.length} 部`)}<div class="hs" data-fg data-pad="136">${ms.map((m, i) => U.poster(m, { attr: st === 'eva' && i === 2 ? 'data-f0' : '', badge: `<i class="idx num">${i + 1}</i>`, no: 1, sub: `${m.year} · ${Math.round(m.runtimeSec / 60)} 分钟` })).join('')}</div></section></div>`; },
  init(root) { root.style.setProperty('--spot', APP.spotOf(root.querySelector('.co-m img').dataset.img)); },
  ok(root, c) { if (c.dataset.act === 'fav') U.toggleFav(c); else APP.toast('播放 / 打开 →'); },
};

// ------------------------------------------------------------------ 搜索：自带拼音首字母键盘
const KEYS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');
const ALL = () => { const seen = {}, out = []; Object.values(U.BY).forEach((o) => { if ((o.type === 'Series' || o.type === 'Movie') && !seen[o.name] && U.I(o, 'poster')) { seen[o.name] = 1; out.push(o); } }); return out; };
const find = (q) => { q = q.toLowerCase(); if (!q) return []; return ALL().map((o) => { const py = U.PY[o.name] || ''; const s = py.startsWith(q) ? 0 : py.includes(q) ? 1 : o.name.toLowerCase().includes(q) ? 2 : -1; return { o, s, py }; }).filter((x) => x.s >= 0).sort((a, b) => a.s - b.s || (b.o.rating || 0) - (a.o.rating || 0)); };
const PEOPLE = [...L.persons, ...X.persons];
const hl = (name, py, q) => { const i = py.indexOf(q.toLowerCase()); if (i < 0 || py.length !== [...name].filter((c) => /[一-龥A-Za-z0-9]/.test(c)).length) return esc(name); let k = 0; return [...name].map((c) => { if (!/[一-龥A-Za-z0-9]/.test(c)) return esc(c); const on = k >= i && k < i + q.length; k++; return on ? `<u>${esc(c)}</u>` : esc(c); }).join(''); };
const results = (q) => {
  if (!q) return `<div class="sec-h"><em>¶</em><b>最近搜过</b><i>RECENT</i><u></u></div><div data-fg>${[['YXJ', '颜心记'], ['ZXC', '周星驰'], ['HHW', '航海王'], ['2024', '2024 年的片子']].map((r) => `<a class="li" data-f>${ic('hist')}<b class="num">${r[0]}</b><small>${r[1]}</small></a>`).join('')}</div>
    <div class="ask">${U.pose({ wr: 70, eyes: '^', mouth: 'D', props: ['pen', 'qm'] }, '举笔：等你报题', '片名、人物、年份都行。打拼音首字母最快：YXJ = 颜心记。')}</div>`;
  const r = find(q), ps = PEOPLE.filter((p) => (U.PY[p.name] || '').startsWith(q.toLowerCase()));
  if (!r.length && !ps.length) return `<div class="none s">${U.pose({ wl: 78, wr: 78, eyes: '-', mouth: 'o', tilt: -6, props: ['sweat'] }, '摊手：两翅摊开、歪头', `片库里没有「${esc(q)}」开头的。删一个字母，或者按左下角「用手机输入」打全名。`)}</div>`;
  if (r.length && r[0].s === 0 && r[0].py === q.toLowerCase()) { const o = r[0].o, d = U.det(o.name) || o, c = U.cta(d), cast = (d.people || []).slice(0, 5);
    return `<div class="sec-h"><em>¶</em><b>最佳匹配</b><i>BEST MATCH</i><u></u><span>共 ${r.length + cast.length} 条</span></div>
      <div class="best">${im(U.I(d, 'poster'), 'po')}<div>${U.logo(d)}<div class="kick">${U.kick(d)}${d.rating ? ` · ★ ${U.rate(d.rating)}` : ''}</div><p>${esc(U.stand(d, 74))}</p><div class="cta" data-fg>${U.btn(c.t, { ic: 'play', cls: 'pri', sub: c.left, p: c.p })}${U.btn('详情', { ic: 'info' })}</div></div></div>
      ${cast.length ? `<div class="sec-h sm"><em>¶</em><b>相关人物</b><i>PEOPLE</i><u></u></div><div class="hs ppl" data-fg data-pad="26">${cast.map((p) => `<a class="card pp" data-f><span class="pic">${im(p.image)}</span><span class="cap"><b>${esc(p.name)}</b><small>饰 ${esc(p.role)}</small></span></a>`).join('')}</div>` : ''}`; }
  return `<div class="sec-h"><em>¶</em><b>含「${esc(q)}」</b><i>MATCHES</i><u></u><span>${r.length + ps.length} 条</span></div><div class="rl" data-fg>${r.slice(0, 5).map((x) => `<a class="li res" data-f>${im(U.I(x.o, 'poster'), 'tp')}<b>${hl(x.o.name, x.py, q)}</b><small>${U.kind(x.o)} · ${x.o.year || ''}${x.o.rating ? ' · ★ ' + U.rate(x.o.rating) : ''}</small><span class="num">${x.py.toUpperCase()}</span></a>`).join('')}${ps.slice(0, 2).map((p) => `<a class="li res" data-f>${im(p.image, 'tp')}<b>${esc(p.name)}</b><small>人物 · 片库里 ${p.works.length} 部</small><span class="num">${(U.PY[p.name] || '').toUpperCase()}</span></a>`).join('')}</div>`;
};
APP.SCREENS.search = {
  title: '搜索', nav: 'search', note: '左页是字母盘：打片名的拼音首字母（YXJ → 颜心记），右页实时出结果。可以真的用方向键打字。不想按遥控器，左下角有「用手机输入」。',
  path: '<b>方向键路径</b>　字母盘 6 列，四个方向走键，确定 = 输入；最左一列再按左 = 进导航；最右一列再按右 = 进结果（上下走，左回字母盘原来的键）。删除 / 清空 / 空格在最后一行；再往下是「用手机输入」。',
  states: [['', '空'], ['typing', '输入中 · YX'], ['results', '结果 · YXJ'], ['none', '无结果 · ZZQ'], ['phone', '用手机输入']],
  render(st) { const q = this.q = { typing: 'YX', results: 'YXJ', none: 'ZZQ' }[st] || '', fk = { typing: 'X', results: 'J', none: 'Q' }[st] || 'Y';
    const left = st === 'phone' ? `<div class="qrbox">${U.qr(11)}<ol><li>手机和电视连同一个网络</li><li>用相机或任何扫码工具扫左边的码</li><li>在手机上打字，这里同步出现</li></ol><p>不想扫了？按返回键回字母盘。</p></div>`
      : `<div class="kb" data-fg>${KEYS.map((k) => `<a class="key" data-f ${k === fk ? 'data-f0' : ''} data-k="${k}">${k}</a>`).join('')}<a class="key w2" data-f data-k="del">${ic('back')}删除</a><a class="key w2" data-f data-k="clr">清空</a><a class="key w2" data-f data-k=" ">空格</a><a class="key w2" data-f data-k="123">123</a></div>
        <a class="li phone" data-f data-go="phone">${ic('tv')}<b>用手机输入</b><small>扫码后在手机上打字</small>${ic('chevr')}</a>`;
    return `<div class="sr">${ph('搜索', 'SEARCH', '在 232 部、4 位以上人物里找')}
      <section class="sr-l"><div class="inp"><span class="q num">${esc(q)}</span><i class="caret"></i><em>${q ? '' : '拼音首字母 · 全拼 · 片名里的字'}</em></div>${left}</section>
      <section class="sr-r">${results(q)}</section></div>`; },
  ok(root, c) { const k = c.dataset.k; if (k === undefined) return APP.toast('打开 →');
    if (k === 'del') this.q = this.q.slice(0, -1); else if (k === 'clr') this.q = ''; else if (k === '123') return APP.toast('切到数字盘（演示里没有画）'); else if (this.q.length < 12) this.q += k;
    root.querySelector('.inp .q').textContent = this.q; root.querySelector('.inp em').textContent = this.q ? '' : '拼音首字母 · 全拼 · 片名里的字';
    const r = root.querySelector('.sr-r'); r.innerHTML = results(this.q.trim()); APP.need(APP.keysIn(r)).then(() => { APP.fill(r); APP.fixLogos(r); APP.dirty(300); }); },
};
})();
