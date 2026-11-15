/* 06 剧集详情 + 单集详情 · 07 电影详情 · 08 人物页 —— 都是「整幅铺满」：剧照 / 肖像从屏幕最顶端铺下来，渐隐进墨黑纸 */
(function () {
'use strict';
const { ic, im, pad, esc, D, clamp } = APP, DB = window.DB, X = APP.X, X2 = APP.X2;

// where the subject sits in each backdrop (object-position), so the portrait crop keeps the faces and the corner buttons miss them
const POS = { 颜心记: '50% 25%', 航海王: '38% 30%', 龙珠大魔: '86% 30%', 网球王子: '50% 30%', 头文字D: '50% 40%', 谷雨: '50% 25%', 仙剑奇侠传三: '50% 25%', 盗梦空间: '50% 30%', '阿凡达：水之道': '50% 30%', 男人没有好东西: '50% 25%', 女子当参政: '50% 30%', 大话西游之月光宝盒: '50% 25%', '毒液：致命守护者': '50% 30%', 鹿鼎记: '50% 25%' };
const TYPE = { Actor: '饰', Director: '导演', Writer: '编剧', Producer: '监制', GuestStar: '客串' };
const role = (p) => p.type === 'Actor' || p.type === 'GuestStar' ? (p.role ? '饰 ' + p.role : '演员') : (TYPE[p.type] || p.type);
const cast = (t, n) => `<div class="b-cast">${t.people.slice(0, n || 6).map((p) => `<a>${p.image ? im(p.image) : `<i class="nop">${esc(p.name[0])}</i>`}<b>${esc(p.name)}</b><span>${esc(role(p))}</span></a>`).join('')}</div>` + (t.people.length > (n || 6) ? `<a class="b-all">全部 ${t.people.length} 位演职人员 ${ic('arrow')}</a>` : '');
const like = (t) => { const lib = (APP.byName(t.name) || {}).lib; return APP.POOL.filter((x) => x.lib === lib && x.name !== t.name).slice(0, 7); };
const lead = (ov, lim) => { ov = (ov || '').replace(/[\r\n\u3000]+/g, '').replace(/\s+/g, ' ').trim(); if (!ov) return `<p class="b-lead none">服务器上没有这一条的简介。</p>`; const long = ov.length > (lim || 170); return `<p class="b-lead${long ? ' clamp' : ''}"><span class="dc">${esc(ov[0])}</span>${esc(ov.slice(1))}</p>` + (long ? `<a class="b-more" data-more>展开全文（${ov.length} 字）</a>` : ''); };
const moreToggle = (root) => root.addEventListener('click', (e) => { const a = e.target.closest('[data-more]'); if (!a) return; const p = a.previousElementSibling; p.classList.toggle('clamp'); a.textContent = p.classList.contains('clamp') ? '展开全文' : '收起'; });
const tagline = (t) => t.tagline && !/配信|\d{4}-\d\d/.test(t.tagline) ? t.tagline : '';

// ================================================================== 剧集详情
const seasonEps = (name, idx) => {
  const S = DB.series[name]; let list = S.episodes.filter((e) => e.s === idx);
  if (name === '航海王' && idx === 22 && X.onePieceLatestSeason) list = X.onePieceLatestSeason.episodes;
  if (name === '航海王' && idx === 21 && X2.onePieceSeason21First30) list = X2.onePieceSeason21First30.episodes;
  return list;
};
const NOTES = { 颜心记: '第 5 集后半段，两个人第一次并肩查案。', 航海王: '一千二百一十五集，不用急，从哪一卷翻起都行。', 龙珠大魔: '变小了，也还是悟空。', 网球王子: '「你还差得远呢。」——这句话会出现很多次。', 头文字D: '下山那几场，建议把音量开大一格。', 谷雨: '第 11 集刚到，还没拆封。', 仙剑奇侠传三: '十几年了，片头一响还是会跟着哼。' };
function seriesHTML(name, st) {
  const t = D[name], S = DB.series[name], seasons = S.seasons.filter((s) => s.index != null), cw = DB.continueWatching.find((c) => c.seriesName === name);
  const cur = st.season || (cw ? cw.season : seasons[0].index), sea = seasons.find((s) => s.index === cur) || seasons[0], eps = seasonEps(name, sea.index), tg = tagline(t);
  const curEp = cw && cw.season === sea.index ? cw.episode : null, pos = cw ? cw.user.positionSec || 0 : 0, pct = cw && cw.user.playedPct ? Math.round(cw.user.playedPct) : 0;
  const first = eps[0] || { e: 1, name: '' }, playEp = cw ? { e: cw.episode, name: cw.name } : first, per = 50, pages = Math.ceil(sea.episodes / per);
  const epRow = (e) => { const isCur = e.e === curEp, seen = e.played && !isCur;
    return K.li(e.e, esc(e.name), (e.runtimeSec ? APP.dur(e.runtimeSec) : t.runtimeSec ? '约 ' + APP.dur(t.runtimeSec) : '时长未知') + (isCur && pos ? ` · 看到 ${APP.clock(pos)}` : seen ? ' · 已看' : ''), e.thumb, isCur ? pct : 0, 'ep' + (isCur ? ' cur' : '') + (seen ? ' seen' : ''), K.mk(), name); };
  return `
  ${K.hero(t.images.backdrop, { h: 420, pos: POS[name], cap: ['剧照', cw ? `第 ${cw.episode} 集《${esc(cw.name.slice(0, 9))}》` : `${esc(name)} · 题图`] })}
  <div class="b-dbody up">
    <div class="b-kick">${APP.kind(t)} · ${APP.genre(t, 4).split(' · ').filter((g) => g !== APP.kind(t)).slice(0, 3).join(' · ')} · ${t.year}</div>
    <div class="b-dhead">${APP.logo(t, 'hl')}<div class="b-score"><b class="num">${APP.score(t.rating)}</b><span>${t.status === 'Continuing' ? '连载中' : '已完结'} · ${t.officialRating || '未分级'}</span></div></div>
    <div class="b-by"><span>主演<b>${t.people.slice(0, 2).map((p) => esc(p.name)).join(' ')}</b></span><span>${seasons.length > 1 ? `<b style="margin:0">${seasons.length}</b> 季 · ` : ''}全<b>${APP.num(S.totalEpisodes)}</b> 集</span></div>
    <div class="b-play"><div><a class="b-btn wide">${ic('play')}<span class="cta-t" style="margin:0;font:inherit;opacity:1">${cw ? (pct ? '续看' : '看') : '从头看'} 第 ${playEp.e} 集${playEp.name ? ' · ' + esc(playEp.name.slice(0, 7)) + (playEp.name.length > 7 ? '…' : '') : ''}</span><span class="num cta-s">${cw && pos ? APP.clock(pos) + ' / ' + APP.clock(cw.runtimeSec) : APP.dur(t.runtimeSec)}</span></a><div class="b-pg"><u style="width:${pct}%"></u></div></div></div>
    <div class="b-links"><span class="fv"><a data-fav>${ic('heart')}<span class="ft">收藏</span></a>${K.favSlot()}</span><a>${ic('check')}整季已看</a><a>${ic('dl')}下载</a><a>${ic('more')}更多</a></div>
    ${lead(t.overview)}
    ${tg ? `<div class="b-quote">「${esc(tg)}」<cite>—— 宣传语</cite></div>` : '<div style="height:26px"></div>'}
    <div class="b-note inl">${APP.msc('round', 46, 'data-say=".sB-series .say"')}<div><em>主编按 · EDITOR'S NOTE</em><p class="say">${NOTES[name] || ''}</p></div></div>
    <section class="b-sec b-eps">
      ${K.head('分集', 'EPISODES', `<a data-seas class="${seasons.length > 1 ? '' : 'solo'}">第 ${sea.index} 季 · ${sea.episodes} 集${seasons.length > 1 ? ' ▾' : ''}</a>`)}
      ${seasons.length > 1 ? `<div class="b-seas"><div class="st"><em>卷册 · ${seasons.length} 季 · ${APP.num(S.totalEpisodes)} 集</em><a data-seas>收起</a></div><div class="sg">${seasons.map((s) => `<a data-season="${s.index}" class="${s.index === sea.index ? 'on' : ''}${cw && cw.season === s.index ? ' cw' : ''}"><b class="num">${s.index}</b><span class="num">${s.episodes} 集</span></a>`).join('')}</div></div>` : ''}
      ${pages > 1 ? `<div class="hs b-pages">${Array.from({ length: pages }, (_, i) => `<a class="${i === 0 ? 'on' : ''}">${(eps[0] ? eps[0].e : 1) + i * per}–${Math.min((eps[0] ? eps[0].e : 1) + (i + 1) * per - 1, (eps[0] ? eps[0].e : 1) + sea.episodes - 1)}</a>`).join('')}</div>` : ''}
      ${eps.length ? eps.slice(0, 12).map(epRow).join('') + (sea.episodes > 12 ? `<a class="b-all">这一季还有 ${sea.episodes - 12} 集，往下翻 ${ic('arrow')}</a>` : '') : `<div class="b-nodata">${[0, 1, 2].map(() => '<div class="sk-li"><u></u><i></i><s></s><b></b></div>').join('')}<small>示意：第 ${sea.index} 季共 ${sea.episodes} 集。数据包只带了第 1 / 21 / 22 季的分集，这里是「正在取这一季目录」的样子。</small></div>`}
    </section>
    <section class="b-sec" id="sec-cast">
      ${K.head('人物', 'CAST', t.people.length + ' 位')}
      ${cast(t)}
    </section>
    <section class="b-sec">
      ${K.head('同刊', 'MORE LIKE THIS')}
      <div class="hs b-new">${like(t).map((x, i) => K.nw(x, i + 1)).join('')}</div>
    </section>
    <div class="b-colo short">— 完 —</div>
  </div>`;
}
const SV = { one: '航海王', season: '航海王', s22: '航海王', nodata: '航海王', daima: '龙珠大魔', tennis: '网球王子', initiald: '头文字D' };
APP.reg('series', '剧集详情', {
  title: '剧集详情', note: '整幅铺满：剧照从状态栏下面一直铺到片名，右侧竖排小字说明是哪一集的剧照。点「第 N 季 ▾」翻卷册（航海王 22 季）；点一集 = 换集；点行尾小格 = 校对勾；点「收藏」盖章。',
  states: [['', '颜心记 · 顶部'], ['eps', '分集目录'], ['mark', '校对勾 + 换集'], ['fav', '盖章'], ['cast', '演职 · 相似'], ['one', '最难：航海王 1215 集'], ['season', '选季 · 22 季'], ['s22', '第 22 季 · 四位数集号'], ['nodata', '换季 · 目录在取'], ['daima', '长简介 566 字'], ['tennis', '黑笔画 logo'], ['initiald', '头文字D · 6 季']],
  build: () => ({ content: `<div class="scroll"><div class="ser-in"></div></div>${K.top('', undefined, 292)}` }),
  init(root) {
    K.common(root); moreToggle(root); const S = root.__st = { name: '颜心记', season: 0 };
    const show = () => { const t = D[S.name]; root.querySelectorAll('.msc').forEach((m) => { m.__m = null; }); root.querySelector('.ser-in').innerHTML = seriesHTML(S.name, S); root.querySelector('.tt').textContent = S.name; root.style.setProperty('--spot', APP.spotOf(t.images.poster)); K.setFav(root, false); APP.fill(root); APP.mountMascots(root); APP.wireScroll(root); };
    root.__show = (name, season) => { S.name = name; S.season = season || 0; root.classList.remove('seas-open'); show(); };
    root.addEventListener('click', (e) => {
      if (e.target.closest('[data-seas]:not(.solo)')) { root.classList.toggle('seas-open'); return; }
      const se = e.target.closest('[data-season]'); if (se) { S.season = +se.dataset.season; const open = true; show(); root.classList.toggle('seas-open', false); K.scrollTo(root, '.b-eps', 108); const m = APP.mascotOf(root); if (m) m.say(`翻到第 ${S.season} 卷了。`, 2200); return; }
      const pg = e.target.closest('.b-pages a'); if (pg) { root.querySelectorAll('.b-pages a').forEach((a) => a.classList.toggle('on', a === pg)); return; }
      const row = e.target.closest('.b-eps .b-li'); if (!row || e.target.closest('.mk')) return;
      root.querySelectorAll('.b-eps .b-li').forEach((r) => r.classList.toggle('cur', r === row));
      const no = row.querySelector('em').textContent, nm = row.querySelector('b').textContent;
      root.querySelector('.cta-t').textContent = `播放 第 ${no} 集 · ${nm.slice(0, 7)}${nm.length > 7 ? '…' : ''}`; root.querySelector('.cta-s').textContent = row.querySelector('span').textContent.split(' · ')[0];
      root.querySelector('.b-pg u').style.width = '0'; const m = APP.mascotOf(root); if (m) { m.flap(0.6); m.say(K.L.ep(no, nm), 2400); }
    });
    show();
  },
  setState(root, st) {
    root.__show(SV[st] || '颜心记', st === 's22' ? 22 : st === 'nodata' ? 13 : 0); const sc = root.querySelector('.scroll'); sc.scrollTop = 0;
    if (st === 'eps' || st === 's22' || st === 'nodata' || st === 'mark') K.scrollTo(root, '.b-eps', 108);
    if (st === 'season') { root.classList.add('seas-open'); K.scrollTo(root, '.b-eps', 108); }
    if (st === 'cast') K.scrollTo(root, '#sec-cast', 108);
    if (st === 'mark') { const rows = root.querySelectorAll('.b-eps .b-li'); rows[1].classList.add('seen'); rows[5].classList.remove('seen'); rows[6].querySelector('b').click(); }
    K.setFav(root, st === 'fav'); sc.dispatchEvent(new Event('scroll'));
  },
});

// ================================================================== 单集详情
function episodeHTML(k) {
  const hard = k === 'hard', name = hard ? '航海王' : '颜心记', t = D[name], eps = hard ? X.onePieceLatestSeason.episodes : DB.series[name].episodes, i = hard ? 0 : 4, e = eps[i], cw = hard ? null : DB.continueWatching.find((c) => c.seriesName === name);
  const pos = cw ? cw.user.positionSec : 0, pct = cw ? Math.round(cw.user.playedPct) : 0, run = e.runtimeSec || (cw && cw.runtimeSec) || t.runtimeSec, nb = (j) => eps[j] ? K.li(eps[j].e, esc(eps[j].name), (j < i ? '上一集' : '下一集') + (eps[j].runtimeSec ? ' · ' + APP.dur(eps[j].runtimeSec) : ''), eps[j].thumb, 0, 'sm', '', name) : '';
  return `
  ${K.hero(e.thumb, { h: 400, pos: '50% 30%', cap: ['本集剧照', `${esc(name)} · 第 ${e.s} 季`] })}
  <div class="b-dbody up">
    <div class="b-kick">${esc(name)} · 第 ${e.s} 季 · 第 ${e.e} 集</div>
    <h3 class="e-title${e.name.length > 10 ? ' long' : ''}">${esc(e.name)}</h3>
    <div class="b-by"><span>时长<b>${APP.dur(run)}</b></span><span>首播<b>${(e.premiere || '').slice(0, 10).replace(/-/g, '.')}</b></span><span>${e.played ? '看过<b>1</b> 遍' : '<b style="margin:0">未看</b>'}</span></div>
    <div class="b-play"><div><a class="b-btn wide">${ic('play')}<span class="cta-t" style="margin:0;font:inherit;opacity:1">${pos ? '续看' : '播放'} 第 ${e.e} 集</span><span class="num cta-s">${pos ? APP.clock(pos) + ' / ' : ''}${APP.clock(run)}</span></a><div class="b-pg"><u style="width:${pct}%"></u></div></div></div>
    <div class="b-links"><span class="fv"><a data-fav>${ic('heart')}<span class="ft">收藏</span></a>${K.favSlot()}</span><a>${ic('check')}${e.played ? '改回未看' : '标记已看'}</a><a>${ic('dl')}下载</a><a>${ic('more')}更多</a></div>
    ${lead(e.overview, 200)}
    <section class="b-sec" style="margin-top:34px">
      ${K.head('文件', 'MEDIA', '1 个版本')}
      <div class="b-cred">${(hard ? [['视频', '1080p · HEVC 10 bit · 23.976 帧'], ['音轨', '日语 AAC 2.0'], ['字幕', '简体中文（内封 ASS）· 繁體中文（内封 ASS）'], ['文件', 'mkv · 1.38 GB · strm 直链']] : [['视频', '4K · HEVC · HDR10 · 25 帧'], ['音轨', '国语 AAC 2.0（默认）'], ['字幕', '简体中文（内封 SRT）'], ['文件', 'mp4 · 2.61 GB']]).map((c) => `<div><em>${c[0]}</em><span>${c[1]}</span></div>`).join('')}</div>
    </section>
    <section class="b-sec">
      ${K.head('前后', 'NEXT TO IT')}
      ${nb(i - 1)}${nb(i + 1)}
      <a class="e-back">${APP.logo(t, 'el')}<div><b>回到《${esc(name)}》</b><span>全 ${APP.num(DB.series[name].totalEpisodes)} 集 · 第 ${e.s} 季</span></div>${ic('arrow')}</a>
    </section>
    <div class="b-colo short">— 本集完 —</div>
  </div>`;
}
APP.reg('episode', '剧集详情', {
  title: '单集详情', note: '整幅铺满这一集的剧照。集名是宋体大字（列表里的小片名不用 logo）；下面是文件信息、前后两集、回到整部剧。',
  states: [['', '颜心记 第 5 集 · 59%'], ['hard', '最难：第 1089 集 · 长集名 · 没有简介'], ['fav', '盖章']],
  build: () => ({ content: `<div class="scroll"><div class="ep-in"></div></div>${K.top('', undefined, 286)}` }),
  init(root) { K.common(root); root.__show = (k) => { root.querySelector('.ep-in').innerHTML = episodeHTML(k); root.querySelector('.tt').textContent = k === 'hard' ? '第 1089 集' : '第 5 集'; root.style.setProperty('--spot', APP.spotOf(D[k === 'hard' ? '航海王' : '颜心记'].images.poster)); K.setFav(root, false); APP.fill(root); }; root.__show(''); },
  setState(root, st) { root.__show(st === 'hard' ? 'hard' : ''); K.setFav(root, st === 'fav'); root.querySelector('.scroll').scrollTop = 0; },
});

// ================================================================== 电影详情
const gb = (n) => n > 1e9 ? (n / 1073741824).toFixed(2) + ' GB' : Math.round(n / 1048576) + ' MB';
const LANG = { zh: '中文', 'zh-CN': '简体中文', 'zh-TW': '繁體中文', en: '英语', eng: '英语', chi: '中文', jpn: '日语', und: '未标语言' };
function versions(t) {
  const real = t.sources.map((s) => { const v = s.streams.find((x) => x.type === 'Video') || {}, a = s.streams.filter((x) => x.type === 'Audio'), su = s.streams.filter((x) => x.type === 'Subtitle');
    const res = v.width >= 3000 ? '4K' : v.width >= 1800 ? '1080p' : v.width >= 1200 ? '720p' : '标清'; return { n: `${res} · ${(v.codec || '').toUpperCase()} · ${v.range || 'SDR'}`, size: gb(s.size), sm: `${s.container} · ${v.width}×${v.height} · ${(s.bitrate / 1e6).toFixed(1)} Mbps${s.isStrm ? ' · strm 直链' : ''}${/[.\u4e00-\u9fa5]/.test(s.name) && s.name.length > 6 ? '<br>文件名：' + esc(s.name) : ''}`, t: res + ' · ' + (v.range || 'SDR'), a, su, real: 1 }; });
  if (t.name === '盗梦空间') real.push(   // 编的：服务器上这部只有一个文件，另外两个版本用来试多版本
    { n: '4K · HEVC · 杜比视界 · REMUX', size: '58.30 GB', sm: 'mkv · 3840×1600 · 62.4 Mbps · strm 直链', t: '4K 杜比视界', a: [{ title: '英语 TrueHD 7.1 Atmos', isDefault: true }, { title: '国语 AC3 5.1' }, { title: '导演评论 AAC 2.0' }], su: [{ title: '简体中文（内封 PGS）' }, { title: '简英双语（内封 ASS）' }, { title: 'English SDH（内封 SRT）' }] },
    { n: '1080p · HEVC · IMAX 画幅', size: '12.64 GB', sm: 'mkv · 1920×1080 · 11.3 Mbps', t: '1080p IMAX', a: [{ title: '英语 DTS-HD MA 5.1', isDefault: true }], su: [{ title: '简体中文（外挂 SRT）' }] });
  return real;
}
const stream = (s, i) => `<div class="b-trk${s.isDefault || (!i && s.pick) ? ' on' : ''}"><u></u><b>${esc(s.title || '(未命名)')}</b><span>${s.codec ? s.codec.toUpperCase() : ''}${s.lang ? ' · ' + (LANG[s.lang] || s.lang) : ''}${s.external ? ' · 外挂' : ''}</span></div>`;
function movieHTML(name, vi) {
  const t = D[name], cw = DB.continueWatching.find((c) => c.name === name), pos = cw ? cw.user.positionSec || 0 : 0, pct = cw ? Math.round(cw.user.playedPct || 0) : 0, V = versions(t), v = V[vi || 0], tg = tagline(t);
  const dir = t.people.find((p) => p.type === 'Director');
  return `
  ${K.hero(t.images.backdrop, { h: 430, pos: POS[name], cap: ['剧照', `${esc(name.split('：')[0].slice(0, 8))} · ${t.year}`] })}
  <div class="b-dbody up">
    <div class="b-kick">电影 · ${APP.genre(t, 4)} · ${t.year}</div>
    <div class="b-dhead">${APP.logo(t, 'hl')}<div class="b-score"><b class="num">${APP.score(t.rating)}</b><span>${APP.dur(t.runtimeSec)}${t.officialRating ? ' · ' + t.officialRating : ''}</span></div></div>
    ${tg ? `<p class="b-tag">${esc(tg)}</p>` : ''}
    <div class="b-by"><span>${dir ? '导演' : '主演'}<b>${esc((dir || t.people[0]).name)}</b></span><span>${dir ? '主演' : '联合主演'}<b>${esc(t.people[dir ? 0 : 1].name)}</b></span></div>
    <div class="b-play"><div><a class="b-btn wide">${ic('play')}${pos ? '续看' : '播放'}<span class="ver-t">${pos ? APP.clock(pos) + ' / ' + APP.clock(t.runtimeSec) : v.t}</span></a><div class="b-pg"><u style="width:${pct}%"></u></div></div></div>
    <div class="b-links"><span class="fv"><a data-fav>${ic('heart')}<span class="ft">收藏</span></a>${K.favSlot()}</span><a>${ic('check')}标记已看</a><a>${ic('dl')}下载</a><a>${ic('more')}更多</a></div>
    ${lead(t.overview)}
    <section class="b-sec" id="sec-ver" style="margin-top:34px">
      ${K.head('版本', 'EDITIONS', V.length + ' 个文件')}
      ${V.map((x, i) => `<a class="b-ver${i === (vi || 0) ? ' on' : ''}" data-v="${i}" data-t="${x.t}"><u></u><b>${x.n}</b><span class="num">${x.size}</span><small>${x.sm}</small></a><div class="b-rule"></div>`).join('')}
    </section>
    <section class="b-sec" id="sec-trk">
      ${K.head('音轨', 'AUDIO', v.a.length + ' 条')}
      ${v.a.map(stream).join('')}
      <div style="height:22px"></div>
      ${K.head('字幕', 'SUBTITLES', v.su.length ? v.su.length + ' 条' : '没有')}
      ${v.su.length ? v.su.map((s, i) => stream(Object.assign({ pick: 1 }, s), i)).join('') : '<p class="b-lead none">这个文件没有字幕。播放时可以在线找。</p>'}
    </section>
    <section class="b-sec" id="sec-cast">
      ${K.head('人物', 'CAST', t.people.length + ' 位')}
      ${cast(t)}
    </section>
    <section class="b-sec">
      ${K.head('同刊', 'MORE LIKE THIS')}
      <div class="hs b-new">${like(t).map((x, i) => K.nw(x, i + 1)).join('')}</div>
    </section>
    <div class="b-colo short">— 完 —</div>
  </div>`;
}
const MV = { nologo: '男人没有好东西', short: '女子当参政', dahua: '大话西游之月光宝盒', avatar: '阿凡达：水之道' };
APP.reg('movie', '电影详情', {
  title: '电影详情', note: '整幅铺满。片名用 clearlogo，没有 logo 的退回宋体；宣传语接在片名下面。往下是版本（点一个就换，音轨 / 字幕跟着换）、音轨字幕、演职人员（彩色头像，缺照片的是姓氏方块）。',
  states: [['', '盗梦空间 · 顶部'], ['ver', '版本（多版本是编的）'], ['tracks', '音轨 / 字幕'], ['cast', '演职人员'], ['fav', '盖章'], ['nologo', '最难：没有 logo · 缺头像'], ['short', '38 字简介 · 10 分 · 看到 42%'], ['dahua', '6 个题材 · 1995'], ['avatar', '阿凡达：水之道']],
  build: () => ({ content: `<div class="scroll"><div class="mv-in"></div></div>${K.top('', undefined, 300)}` }),
  init(root) {
    K.common(root); moreToggle(root); const S = { name: '盗梦空间', v: 0 };
    const show = () => { root.querySelector('.mv-in').innerHTML = movieHTML(S.name, S.v); root.querySelector('.tt').textContent = S.name.length > 9 ? S.name.slice(0, 8) + '…' : S.name; root.style.setProperty('--spot', APP.spotOf(D[S.name].images.poster)); APP.fill(root); APP.wireScroll(root); };
    root.__show = (n, v) => { S.name = n; S.v = v || 0; show(); K.setFav(root, false); };
    root.addEventListener('click', (e) => { const v = e.target.closest('.b-ver'); if (!v) return; const sc = root.querySelector('.scroll'), y = sc.scrollTop, fav = root.classList.contains('is-fav'); S.v = +v.dataset.v; show(); K.setFav(root, fav); sc.scrollTop = y; }, true);
    show();
  },
  setState(root, st) { root.__show(MV[st] || '盗梦空间', st === 'tracks' ? 1 : 0); const sc = root.querySelector('.scroll'); sc.scrollTop = 0; if (st === 'ver') K.scrollTo(root, '#sec-ver', 108); if (st === 'tracks') K.scrollTo(root, '#sec-trk', 108); if (st === 'cast') K.scrollTo(root, '#sec-cast', 108); K.setFav(root, st === 'fav'); sc.dispatchEvent(new Event('scroll')); },
});

// ================================================================== 人物页
const PERS = {}; [...DB.persons, ...(X.persons || [])].forEach((p) => { PERS[p.name] = p; });
const ROLES = {}; (X2.zhouXingchiRoles || []).forEach((r) => { ROLES[r.id] = r; });
const large = (p) => (X.personLarge && X.personLarge[p.name] && X.personLarge[p.name].image) || p.image;
function personHTML(name, tab) {
  const p = PERS[name], all = p.works.slice().sort((a, b) => b.year - a.year), ys = all.map((w) => w.year);
  const rl = (w) => { const r = ROLES[w.id]; if (r) return r.personType === 'Actor' ? (r.role ? '饰 ' + r.role : '主演') : (TYPE[r.personType] || r.personType); const d = D[w.name], q = d && d.people.find((x) => x.name === name); return q ? role(q) : '演员'; };
  const typ = (w) => { const r = ROLES[w.id]; return r ? r.personType : 'Actor'; };
  const works = tab === 'dir' ? all.filter((w) => typ(w) !== 'Actor') : tab === 'act' ? all.filter((w) => typ(w) === 'Actor') : all;
  const best = all.slice().sort((a, b) => (b.rating || 0) - (a.rating || 0))[0], bd = D[best.name];
  const co = {}; DB.details.forEach((d) => { if (d.people.some((x) => x.name === name)) d.people.forEach((x) => { if (x.name !== name && x.image) (co[x.name] = co[x.name] || { p: x, n: [] }).n.push(d.name); }); });
  const mates = Object.values(co).sort((a, b) => b.n.length - a.n.length).slice(0, 3), nDir = all.filter((w) => typ(w) !== 'Actor').length;
  return `
  ${K.hero(large(p), { h: 540, pos: '50% 18%', cap: ['肖像', 'TMDB'] })}
  <div class="b-dbody up p-body">
    <div class="b-kick">人物 · PROFILE</div>
    <h3 class="p-name">${esc(name)}</h3>
    <p class="p-meta">${p.born ? esc(p.born) + ' · ' : ''}${p.place ? esc(p.place) + ' · ' : ''}演员${nDir ? ' / 导演 / 监制' : ''}</p>
    <div class="b-by"><span>片库里<b>${all.length}</b> 部</span><span>${all.length > 1 ? `跨度<b>${Math.min(...ys)} — ${Math.max(...ys)}</b>` : `年份<b>${ys[0]}</b>`}</span><span>最高<b>${APP.score(best.rating)}</b></span></div>
    ${p.overview ? lead(p.overview) : `<p class="b-lead none">服务器上还没有${esc(name)}的小传。下面只列你片库里有的作品。</p>`}
    <section class="b-sec" id="p-works" style="margin-top:30px">
      ${K.head('作品', 'IN YOUR LIBRARY', all.length + ' 部')}
      ${all.length > 3 ? `<div class="b-idx p-idx"><a data-pt="" class="${!tab ? 'on' : ''}">全部 <i class="num">${all.length}</i></a><a data-pt="act" class="${tab === 'act' ? 'on' : ''}">出演 <i class="num">${all.length - nDir}</i></a><a data-pt="dir" class="${tab === 'dir' ? 'on' : ''}">幕后 <i class="num">${nDir}</i></a><span>按年份 ↓</span></div>` : ''}
      ${!tab ? `<a class="b-best">${im(best.poster)}<div>${bd ? APP.logo(bd, 'bl') : `<span class="lg txt bl" style="--fs:.5">${esc(best.name)}</span>`}<p><b>${rl(best)}</b><br>${best.year} · ${APP.score(best.rating)} 分 · ${all.length > 1 ? '片库里评分最高的一部' : '片库里唯一的一部'}</p><span class="go">翻开 ${ic('arrow')}</span></div></a><div class="b-rule"></div>` : ''}
      ${(tab ? works : works.filter((w) => w !== best)).map((w, i) => K.li(String(w.year).slice(2), esc(w.name), `${w.year} · ${rl(w)}${w.rating ? ' · ' + APP.score(w.rating) + ' 分' : ''}`, w.poster, 0, 'po yr')).join('')}
      ${all.length === 1 ? `<p class="p-one">片库里只有这一部。去「发现」看看还有谁和${esc(name)}合作过。</p>` : ''}
    </section>
    ${mates.length ? `<section class="b-sec">
      ${K.head('同台', 'OFTEN WITH')}
      ${mates.map((m) => `<a class="b-who">${im(m.p.image)}<div><b>${esc(m.p.name)}</b><span>《${esc(m.n[0])}》${m.n.length > 1 ? `等 ${m.n.length} 部同台` : ' 同台'}</span></div><em>人物页 ›</em></a><div class="b-rule"></div>`).join('')}
    </section>` : ''}
    <div class="b-colo short">资料来自 TMDB · 只列出片库里有的作品</div>
  </div>`;
}
APP.reg('person', '人物页', {
  title: '人物页', note: '整幅铺满彩色肖像，大名压在渐隐处。作品很多（周星驰 23 部：按年份的目录，出演 / 幕后分开）和只有 1 部（罗云熙）两种；服务器上没有小传时有兜底。',
  states: [['', '周星驰 · 23 部'], ['works', '作品目录'], ['dir', '幕后'], ['one', '只有 1 部'], ['three', '宋轶 · 3 部']],
  build: () => ({ content: `<div class="scroll"><div class="pp-in"></div></div>${K.top('', `<a>${ic('share')}</a><a>${ic('more')}</a>`, 400)}` }),
  init(root) { const S = { n: '周星驰', tab: '' }; const show = () => { root.querySelector('.pp-in').innerHTML = personHTML(S.n, S.tab); root.querySelector('.tt').textContent = S.n; APP.fill(root); };
    root.__show = (n, tab) => { S.n = n; S.tab = tab || ''; show(); }; root.addEventListener('click', (e) => { const a = e.target.closest('[data-pt]'); if (a) { const sc = root.querySelector('.scroll'), y = sc.scrollTop; S.tab = a.dataset.pt; show(); sc.scrollTop = y; } }); show(); },
  setState(root, st) { root.__show(st === 'one' ? '罗云熙' : st === 'three' ? '宋轶' : '周星驰', st === 'dir' ? 'dir' : ''); const sc = root.querySelector('.scroll'); sc.scrollTop = 0; if (st === 'works' || st === 'dir') K.scrollTo(root, '#p-works', 108); sc.dispatchEvent(new Event('scroll')); },
});
})();
