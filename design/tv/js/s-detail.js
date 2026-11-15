/* 剧集详情 · 电影详情 · 人物页 —— 都是整幅铺满：剧照从屏幕最顶端铺到底，往下渐隐进纸面；文字压在左下的暗角里，不压人脸 */
(function () {
'use strict';
const APP = window.APP, U = window.U, L = window.LIB, X = window.EXTRA, { ic, im, esc, pad } = APP, F = APP.F;
const day = (s) => { if (!s) return ''; const d = new Date(s); return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日`; };
const eh = (o, mid) => `<header class="eh">${U.logo(o, 'sm')}<span class="m">${mid || ''}</span><em>21:47</em></header>`;
const person = (p, attr) => `<a class="card pp" data-f ${attr || ''}><span class="pic">${p.image ? im(p.image) : `<span class="nopic pp"><b>${esc(p.name[0])}</b></span>`}</span><span class="cap"><b>${esc(p.name)}</b><small>${p.role ? '饰 ' + esc(p.role) : ''}</small></span></a>`;
const meta = (o, extra) => `<div class="meta">${o.rating ? `<b class="num">${U.rate(o.rating)}</b><small>TMDB<br>评分</small>` : '<small>暂无<br>评分</small>'}<span>${[extra, o.officialRating, (o.studios || [])[0]].filter(Boolean).join('　·　')}</span></div>`;
const hero = (o, cap, cta, down) => `<section class="hero" data-snap="0">${U.head('', o.type === 'Movie' ? 'FILM' : 'FEATURE', `<span>${cap}</span>`)}
  <div class="hb"><div class="kick">${U.kind(o)} · ${U.genres(o, 3).join(' ')} · ${o.year}${o.originalTitle && o.originalTitle !== o.name ? ' · ' + esc(o.originalTitle) : ''}</div>${U.logo(o)}${meta(o, U.len(o) + (o.type !== 'Movie' && o.runtimeSec ? ` · 每集约 ${Math.round(o.runtimeSec / 60)} 分` : ''))}
  <p class="lead l2">${esc(U.stand(o, 92))}</p><div class="cta" data-fg>${cta}</div></div><div class="down">${ic('chevd')}${down}</div></section>`;
const SIM = (names) => U.pool(names);

// ------------------------------------------------------------------ 剧集
const SER = { '': '颜心记', eps: '颜心记', menu: '颜心记', op: '航海王', op21: '航海王', guyu: '谷雨', cast: '网球王子', more: '龙珠大魔', dark: '网球王子' };
const epsOf = (name, season) => {
  const S = L.series[name], se = S.seasons.find((s) => s.index === season) || S.seasons[S.seasons.length - 1];
  let list = name === '航海王' && season === 22 ? X.onePieceLatestSeason.episodes : name === '航海王' && season === 21 ? window.EXTRA2.onePieceSeason21First30.episodes : S.episodes.filter((e) => e.s === (se.index == null ? 1 : se.index));
  const out = list.slice(); let first = 1; if (name === '航海王') first = S.seasons.filter((s) => s.index < season).reduce((a, s) => a + s.episodes, 0) + 1;
  for (let i = out.length; i < se.episodes; i++) out.push({ e: (out[0] ? out[0].e : first) + i, name: '', gen: true, runtimeSec: out[0] && out[0].runtimeSec });   // the export only has the first 60 episodes: the rest are numbered blanks
  return { se, list: out };
};
const epCard = (o, e, cur, f0) => { const p = e.positionSec && e.runtimeSec ? e.positionSec / e.runtimeSec * 100 : 0;
  return U.wide({ cls: 'ep' + (cur ? ' cur' : ''), img: e.thumb, no: e.e, alt: e.name || `第 ${e.e} 集`, pre: e.e, title: e.name || `第 ${e.e} 集`, sub: [e.runtimeSec ? Math.round(e.runtimeSec / 60) + ' 分' : '', p ? `看到 ${Math.round(p)}%` : (e.played ? '已看' : ''), cur ? '上次看到这里' : ''].filter(Boolean).join(' · '), p, seen: e.played && !p, attr: `data-ep="${e.e}" data-menu="menu" ${f0 ? 'data-f0' : ''}` }); };
const PAGE = 50;
const epSection = (o, season, page, st) => {
  const S = L.series[o.name], { se, list } = epsOf(o.name, season), c = U.cw(o), curE = c && c.episode, pages = Math.ceil(list.length / PAGE), shown = list.slice(page * PAGE, page * PAGE + PAGE);
  const f = /^(eps|op|guyu)$/.test(st) ? (shown.find((e) => e.e === curE) || shown[0]) : null;
  return `${eh(o, `${S.seasons.length > 1 ? S.seasons.length + ' 季 · ' : ''}共 ${S.totalEpisodes.toLocaleString()} 集${curE ? ` · 看到第 ${curE} 集` : ''}`)}
    <div class="hs sea" data-fg data-pad="136">${S.seasons.map((s) => `<a class="chip${s === se ? ' on' : ''}" data-f ${st === 'op21' && s === se ? 'data-f0' : ''} data-season="${s.index == null ? '' : s.index}" data-sname="${esc(s.name)}">${esc(s.name)}<small>${s.episodes}</small></a>`).join('')}</div>
    ${pages > 1 ? `<div class="hs pgr" data-fg data-pad="136">${Array.from({ length: pages }, (_, i) => `<a class="chip num${i === page ? ' on' : ''}" data-f data-page="${i}">${list[i * PAGE].e} – ${list[Math.min(list.length, (i + 1) * PAGE) - 1].e}</a>`).join('')}<span class="pgn">每页 50 集 · 共 ${list.length} 集</span></div>` : '<div class="pgr nopg"></div>'}
    <div class="hs epl" data-lead data-fg data-pad="136">${shown.map((e) => epCard(o, e, e.e === curE && season === (c.season || 1), f === e)).join('')}</div>
    <div class="epd"><div class="l"><em></em><b></b><p></p></div><div class="r">${U.note('', { k: '主编按 · EDITOR’S NOTE' })}</div></div><div class="down">${ic('chevd')}演职人员 ${(o.people || []).length}　·　相似</div>`;
};
APP.SCREENS.series = {
  title: '剧集详情（整幅铺满）', note: '剧照从屏幕最顶端铺满，往下渐隐进墨黑纸；片名 logo、评分、导语接在渐隐处，右上一行小字说明这是哪一集的剧照（电视上不用竖排）。往下一屏是选季 + 分集横排，再下一屏是演职人员（彩色）和相似。',
  path: '<b>方向键路径</b>　第一屏：默认焦点「续看」，左右在按钮间走，下 = 分集。第二屏从上到下三行：季（左右走，焦点到哪季分集就换成哪季）→ 分页（超过 50 集才有）→ 分集（左右走，焦点卡停在左起第一格，下面一栏换成这一集的简介）；长按确定 = 这一集的菜单（标记已看 = 校对勾）。再下 = 演职人员 → 相似。任何一行最左再按左 = 进导航；返回 = 回上一屏。',
  states: [['', '颜心记 · 开头'], ['eps', '分集（有剧照）'], ['op', '航海王 · 22 季 1215 集（没有剧照）'], ['op21', '航海王 · 选到第 21 季（197 集，分 4 页）'], ['guyu', '谷雨 · 季名乱码、缺时长'], ['cast', '网球王子 · 演职人员、相似'], ['more', '龙珠大魔 · 566 字简介全文'], ['dark', '网球王子 · 黑色笔画 logo'], ['menu', '长按确定：分集菜单']],
  render(st) {
    const o = this.o = U.det(SER[st]), c = U.cta(o), S = L.series[o.name], season = this.season = st === 'op21' ? 21 : st === 'op' ? 1 : (c.c && c.c.season) || (S.seasons[S.seasons.length - 1].index || 1); this.page = 0;
    const deep = /^(eps|op|op21|guyu|menu|cast)$/.test(st), people = o.people || [];
    const cta = U.btn(c.t, { ic: 'play', cls: 'pri', sub: c.left, p: c.p, attr: deep ? '' : 'data-f0' }) + U.btn('从头看', { ic: 'replay' }) + U.favBtn(false) + U.btn('整季已看', { ic: 'check' }) + U.btn('简介全文', { ic: 'list', attr: 'data-go="more"' });
    const capEp = c.c ? `剧照｜第 ${c.c.episode} 集《${esc(c.c.name)}》` : '剧照｜主视觉';
    if (st === 'more') return `<div class="dt full art-p"><div class="dt-bd dimmed"><img class="bd" data-img="${APP.key(U.I(o, 'backdrop'))}" alt=""><div class="sh1"></div><div class="sh2"></div></div>${eh(o, '简介全文')}
      <aside class="art-l">${im(U.I(o, 'poster'), 'po')}${meta(o, U.len(o))}<dl><dt>题材</dt><dd>${U.genres(o, 6).join(' · ')}</dd><dt>首播</dt><dd>${day(o.premiere)}</dd><dt>出品 · ${o.studios.length} 家</dt><dd class="clamp">${o.studios.slice(0, 5).map(esc).join('、')} 等 ${o.studios.length} 家</dd></dl></aside>
      <article class="art"><div class="sec-h"><em>¶</em><b>简介</b><i>SYNOPSIS</i><u></u><span>${(o.overview || '').length} 字</span></div><div class="cols">${o.overview.split(/\n+/).map((p) => `<p>${esc(p.trim())}</p>`).join('')}</div>
      <div class="cta">${U.btn('合上', { ic: 'back', attr: 'data-f0 data-go=""' })}${U.btn(c.t, { ic: 'play', cls: 'pri' })}</div></article></div>`;
    return `<div class="dt full"><div class="dt-bd"><img class="bd" data-img="${APP.key(U.I(o, 'backdrop'))}" alt=""><div class="sh1"></div><div class="sh2"></div></div>
      <div class="vs pgs">${hero(o, capEp, cta, `分集 ${S.totalEpisodes.toLocaleString()}　·　演职人员 ${people.length}　·　相似`)}
      <section class="epsec" data-snap="0">${epSection(o, season, 0, st)}</section>
      <section class="castsec" data-snap="0">${eh(o, `演职人员 ${people.length} 位`)}${U.sec('', '演职人员', 'CAST', `${people.length} 位`)}<div class="hs" data-lead data-fg data-pad="136">${people.map((p, i) => person(p, st === 'cast' && i === 5 ? 'data-f0' : '')).join('')}</div>
        ${U.sec('', '相似', 'MORE LIKE THIS', '')}<div class="hs" data-lead data-fg data-pad="136">${SIM(o.name === '颜心记' ? ['子夜归', '永夜星河', '琅琊榜', '仙剑奇侠传三', '神话', '射雕英雄传', '长风渡', '玉楼春'] : ['头文字D', '航海王', '龙珠大魔', '四月是你的谎言', '数码宝贝', '宝可梦', '中华小当家', '围棋少年']).filter((x) => x.name !== o.name).map((x) => U.poster(x)).join('')}</div></section></div>
      ${st === 'menu' ? U.MENU(`颜心记 · 第 6 集《${L.series['颜心记'].episodes[5].name}》`, 'ep') : ''}</div>`;
  },
  init(root, st) {
    root.style.setProperty('--spot', APP.spotOf(U.I(this.o, 'poster')));
    if (st === 'menu') { const c = root.querySelector('.epl .card:nth-child(6)'); root.querySelectorAll('[data-f0]').forEach((x) => { if (!x.closest('.cmenu')) x.removeAttribute('data-f0'); }); c.classList.add('held'); const m = root.querySelector('.cmenu'); m.style.left = '1010px'; m.style.top = '300px'; root.classList.add('dim', 'deep');
      const vs = root.querySelector('.pgs'), hs = root.querySelector('.epl'); vs.scrollTop = 1080; hs.scrollLeft = c.offsetLeft - hs.offsetLeft - 136 - 430; this.detail(root, c); }
    if (st === 'op21') { this.detail(root, root.querySelector('.epl .card')); }
  },
  detail(root, card) { if (!card) return; const o = this.o, { list } = epsOf(o.name, this.season), e = list.find((x) => String(x.e) === card.dataset.ep), $ = (s) => root.querySelector('.epd ' + s); if (!e) return;
    $('.l em').textContent = `第 ${e.e} 集${e.runtimeSec ? ' · ' + Math.round(e.runtimeSec / 60) + ' 分钟' : ''}${e.premiere ? ' · ' + day(e.premiere) : ''}`; $('.l b').textContent = e.name || `第 ${e.e} 集`;
    $('.l p').textContent = e.gen ? '（这一集的资料不在导出的数据包里，真机上这里是它的简介。）' : (e.overview ? U.cut(e.overview, 150) : '服务器上没有这一集的简介。');
    $('.say').textContent = e.positionSec ? `停在 ${U.clock(e.positionSec)}，接着看正好。` : e.played ? '这一集画过校对勾了。' : e.thumb ? '剧照是服务器给的原图，我没有裁。' : '没给剧照，我用集号排了个版。'; },
  onFocus(root, el) {
    root.classList.toggle('deep', !el.closest('.hero') && !el.closest('.art-p'));
    if (el.dataset.ep) this.detail(root, el);
    const reload = () => { const sec = root.querySelector('.epsec'), keep = el.dataset.season !== undefined ? `[data-sname="${el.dataset.sname}"]` : `[data-page="${el.dataset.page}"]`; const sl = sec.querySelector('.sea').scrollLeft; sec.innerHTML = epSection(this.o, this.season, this.page, 'op21');
      sec.querySelectorAll('[data-f0]').forEach((x) => x.removeAttribute('data-f0')); const n = sec.querySelector(keep); sec.querySelector('.sea').scrollLeft = sl; APP.need(APP.keysIn(sec)).then(async () => { APP.fill(sec); APP.mountMascots(sec); APP.fixLogos(sec); this.detail(root, sec.querySelector('.epl .card')); APP.dirty(300); }); F.cur = n; n.classList.add('fc'); const g = n.closest('[data-fg]'); g.__last = n; };
    if (el.dataset.season !== undefined && !el.classList.contains('on')) { this.season = el.dataset.season === '' ? 1 : +el.dataset.season; this.sname = el.dataset.sname; this.page = 0; reload(); }
    else if (el.dataset.page !== undefined && !el.classList.contains('on')) { this.page = +el.dataset.page; reload(); }
  },
  key(root, k) { if (k === 'back' && root.classList.contains('deep') && !root.querySelector('.cmenu')) { F.set(root.querySelector('.hero .btn')); return true; } },
  ok(root, c) { if (c.dataset.act === 'fav') { U.toggleFav(c); return; } if (c.closest('.cmenu')) { APP.back(); return; } APP.toast(c.dataset.ep ? `播放第 ${c.dataset.ep} 集 →（见「播放器」画板）` : '已执行（演示）'); },
};

// ------------------------------------------------------------------ 电影
const LANG = { chi: '中文', zh: '中文', 'zh-CN': '简体中文', eng: 'English', spa: 'Español', und: '', null: '' };
const CH = { 2: '立体声', 6: '5.1', 8: '7.1' };
const stream = (s) => { const l = LANG[s.lang] !== undefined ? LANG[s.lang] : (s.lang || ''); if (s.type === 'Audio') return [(l || '未标注语言') + ' · ' + s.codec.toUpperCase() + ' ' + (CH[s.channels] || ''), s.isDefault ? '默认' : ''];
  return [(l || '未命名') + ' · ' + s.codec.toUpperCase(), [s.external ? '外挂' : '内封', s.isDefault ? '默认' : '', /强制/.test(s.title || '') ? '强制' : '', /SDH/.test(s.title || '') ? 'SDH' : ''].filter(Boolean).join(' · ')]; };
const gb = (b) => (b / 1073741824).toFixed(1) + ' GB', mbps = (b) => (b / 1e6).toFixed(1) + ' Mbps';
const MOVS = { '': '盗梦空间', ver: '盗梦空间', tracks: '鹿鼎记', spec: '男人没有好东西', short: '女子当参政' };
const opt = (t, sub, on, f0) => `<a class="li opt${on ? ' on' : ''}" data-f ${f0 ? 'data-f0' : ''}><i class="sq"></i><b>${t}</b><small>${sub || ''}</small></a>`;
APP.SCREENS.movie = {
  title: '电影详情（整幅铺满）', note: '同一种铺法。版本、音轨、字幕各是一个按钮，按下去在右边页边排出选项，剧照和片名不被盖。数据里没有 logo 的那部（男人没有好东西）退回宋体片名。',
  path: '<b>方向键路径</b>　默认焦点「播放」；左右：播放 → 版本 → 音轨 → 字幕 → 收藏 → 已看；在版本 / 音轨 / 字幕上按确定，右边页边出选项（上下走、确定选中、返回或左收起）。下 = 规格与演职人员 → 相似。最左再按左 = 进导航。',
  states: [['', '盗梦空间'], ['ver', '版本（多版本是编的）'], ['tracks', '鹿鼎记 · 音轨字幕'], ['short', '女子当参政 · 38 字简介、10 分、看到 42%'], ['spec', '男人没有好东西 · 没有 logo、缺演员照']],
  render(st) {
    const o = this.o = U.det(MOVS[st]), c = U.cta(o), src = o.sources[0], au = src.streams.filter((s) => s.type === 'Audio'), su = src.streams.filter((s) => s.type === 'Subtitle'), v = src.streams.find((s) => s.type === 'Video');
    const dA = au.find((s) => s.isDefault) || au[0], dS = su.find((s) => s.isDefault);
    const vname = v.title.replace(/ H264| HEVC/, '');
    const cta = U.btn(c.t, { ic: 'play', cls: 'pri', sub: c.left, p: c.p, attr: st === '' || st === 'short' ? 'data-f0' : '' }) + U.btn('版本', { ic: 'layers', sub: vname, attr: 'data-go="ver"' }) + U.btn('音轨', { ic: 'audio', sub: stream(dA)[0].split(' · ')[1], attr: 'data-go="tracks"' }) + U.btn('字幕', { ic: 'sub', sub: dS ? stream(dS)[0].split(' · ')[0] : (su.length ? `关 · 有 ${su.length} 条` : '无'), attr: 'data-go="tracks"' }) + U.favBtn(false) + U.btn(o.user.played ? '已看' : '标记已看', { ic: 'check' });
    const side = st === 'ver' ? `<aside class="side scope open"><div class="sec-h"><em>¶</em><b>版本</b><i>VERSIONS</i><u></u><span>3 个</span></div><div data-fg>${opt('1080p · MP4', `H.264 SDR · ${gb(src.size)} · ${mbps(src.bitrate)}`, true, true)}${opt('2160p · MKV', 'HEVC HDR10 · 58.2 GB · 54.0 Mbps')}${opt('1080p · MKV', 'H.264 SDR · 12.4 GB · 11.6 Mbps · 国英双语')}</div><p class="sn">码率超过设置里的上限时，会先问你要不要服务器转码。</p></aside>`
      : st === 'tracks' ? `<aside class="side scope open"><div class="sec-h"><em>¶</em><b>音轨</b><i>AUDIO</i><u></u><span>${au.length} 条</span></div><div data-fg>${au.map((s, i) => opt(stream(s)[0], stream(s)[1], s.isDefault, i === 1)).join('')}</div>
        <div class="sec-h"><em>¶</em><b>字幕</b><i>SUBTITLES</i><u></u><span>${su.length} 条</span></div><div data-fg>${opt('关', '', !dS)}${su.map((s) => opt(stream(s)[0], stream(s)[1], s.isDefault)).join('')}</div></aside>` : '';
    const sim = SIM(o.name === '男人没有好东西' ? ['女子当参政', '挽救计划', '森中有林', '芳华', '第二十条', '周处除三害', '命案', '手卷烟'] : ['阿凡达：水之道', '毒液：致命守护者', '银河护卫队', '黑豹', '雷神3：诸神黄昏', '荒野机器人', '猫猫的奇幻漂流', '局内人']);
    const spec = [['文件', esc(src.name) + (src.isStrm ? '　<i class="tag">strm 直链</i>' : '')], ['容器 · 大小', `${src.container.toUpperCase()} · ${gb(src.size)} · ${mbps(src.bitrate)}`], ['视频', `${v.width} × ${v.height} · ${v.codec.toUpperCase()} · ${v.range}`], ['音轨', au.map((s) => stream(s)[0]).join('　/　')], ['字幕', su.length ? su.map((s) => stream(s)[0]).join('　/　') : '没有字幕（服务器上这部片子一条都没有）']];
    return `<div class="dt full${side ? ' sided' : ''}"><div class="dt-bd"><img class="bd" data-img="${APP.key(U.I(o, 'backdrop'))}" alt=""><div class="sh1"></div><div class="sh2"></div></div>
      <div class="vs pgs">${hero(o, '剧照｜主视觉', cta, `规格　·　演职人员 ${o.people.length}　·　相似`)}
      <section class="castsec mv" data-snap="0">${eh(o, '规格 · 演职人员 · 相似')}${U.sec('', '印刷规格', 'SPECS', '')}<dl class="spec">${spec.map((r) => `<dt>${r[0]}</dt><dd>${r[1]}</dd>`).join('')}</dl>
        ${U.sec('', '演职人员', 'CAST', `${o.people.length} 位`)}<div class="hs" data-lead data-fg data-pad="136">${o.people.map((p, i) => person(p, st === 'spec' && i === 2 ? 'data-f0' : '')).join('')}</div>
        ${U.sec('', '相似', 'MORE LIKE THIS', '')}<div class="hs" data-lead data-fg data-pad="136">${sim.map((x) => U.poster(x)).join('')}</div></section></div>${side}</div>`;
  },
  init(root) { root.style.setProperty('--spot', APP.spotOf(U.I(this.o, 'poster'))); },
  onFocus(root, el) { root.classList.toggle('deep', !el.closest('.hero') && !el.closest('.side')); },
  key(root, k) { if (k === 'left' && F.cur && F.cur.closest('.side')) { APP.back(); return true; } if (k === 'back' && root.classList.contains('deep')) { F.set(root.querySelector('.hero .btn')); return true; } },
  ok(root, c) { if (c.dataset.act === 'fav') { U.toggleFav(c); return; } if (c.classList.contains('opt')) { c.parentElement.querySelectorAll('.opt').forEach((x) => x.classList.toggle('on', x === c)); return; } APP.toast('已执行（演示）'); },
};

// ------------------------------------------------------------------ 人物
const PERS = { '': ['周星驰', X.persons[0]], few: ['罗云熙', L.persons[0]] };
APP.SCREENS.person = {
  title: '人物页（整幅铺满）', note: '彩色大照片从右边整幅铺到屏幕边，往左渐隐进纸面；名字、作品压在左边。服务器上人物都没有生平（真实数据），这一格留给主编说一句。作品只有 1 部的人（罗云熙）换成一张大卡 + 同剧的人。',
  path: '<b>方向键路径</b>　默认焦点在第一部作品；左右在作品里走（焦点卡停在左起第一格）；作品少的那一版：续看 → 详情，下 = 同剧的人。最左再按左 = 进导航。',
  states: [['', '周星驰 · 23 部'], ['few', '罗云熙 · 只有 1 部']],
  render(st) {
    const [name, p] = PERS[st], big = X.personLarge[name].image, works = p.works.slice().sort((a, b) => b.year - a.year), ys = works.map((w) => w.year), top = works.slice().sort((a, b) => (b.rating || 0) - (a.rating || 0))[0];
    let body;
    if (works.length > 3) body = `<section class="pf-w">${U.sec('', '作品', 'IN YOUR LIBRARY', `按年份 · ${works.length} 部`)}<div class="hs" data-lead data-fg data-pad="136">${works.map((w, i) => U.poster(U.BY[w.id] || { id: w.id, name: w.name, year: w.year, rating: w.rating, images: { poster: w.poster } }, { attr: i === 0 ? 'data-f0' : '' })).join('')}</div></section>`;
    else { const d = U.det(works[0].name), c = U.cta(d), me = d.people.find((x) => x.name === name);
      body = `<section class="pf-one"><div class="sec-h"><em>¶</em><b>作品</b><i>IN YOUR LIBRARY</i><u></u><span>只有 1 部</span></div><div class="best">${im(U.I(d, 'poster'), 'po')}<div>${U.logo(d)}<div class="kick">饰 ${esc(me.role)} · ${U.kick(d)}</div><p>${esc(U.stand(d, 60))}</p><div class="cta" data-fg>${U.btn(c.t, { ic: 'play', cls: 'pri', sub: c.left, p: c.p, attr: 'data-f0' })}${U.btn('详情', { ic: 'info' })}</div></div></div>
        <div class="sec-h sm"><em>¶</em><b>同剧的人</b><i>CO-STARS</i><u></u></div><div class="chips cos" data-fg>${d.people.filter((x) => x.name !== name).slice(0, 5).map((x) => `<a class="chip co" data-f>${im(x.image)}${esc(x.name)}</a>`).join('')}</div></section>`; }
    return `<div class="dt full pf"><div class="pf-bd"><img class="bd" data-img="${APP.key(big)}" alt=""><div class="sh1"></div><div class="sh2"></div></div>${U.head('', 'PROFILE', '')}
      <section class="pf-h"><div class="kick">人物 · 演员</div><h1>${esc(name)}</h1><div class="meta"><b class="num">${works.length}</b><small>部<br>在库</small><span>${Math.min(...ys)}${Math.max(...ys) !== Math.min(...ys) ? ' — ' + Math.max(...ys) : ''}　·　最高分《${esc(top.name)}》${top.rating ? '★ ' + U.rate(top.rating) : ''}</span></div>
      ${U.note(works.length > 3 ? '服务器上没有他的生平，二十三部作品替他说话。' : '资料卡是空的，片库里也只有这一部。先把它看完吧。')}</section>${body}</div>`;
  },
  init(root) { root.style.setProperty('--spot', '#EA4E30'); },
  ok() { APP.toast('打开 →'); },
};
})();
