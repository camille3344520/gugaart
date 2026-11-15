/* 剧集详情 / 电影详情 / 人物 / 我的（记录、收藏）/ 账号 */
(function () {
'use strict';
const { ic, im, pad, esc, LIB, ITEM, X1, X2 } = APP, S = APP.S;
const day = (iso) => { if (!iso) return ''; const d = new Date(iso); return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日`; };
const who = (it, type, n) => (it.people || []).filter((p) => p.type === type).slice(0, n || 3);
const pLink = (p) => `<a data-go="person:${p.id}">${esc(p.name)}</a>`;
const castRow = (it) => `<div class="hs castrow">${(it.people || []).map((p) => `<a class="cast" data-go="person:${p.id}">${p.image ? im(APP.key(p.image)) : APP.ph(p.name[0], '', '')}<b>${esc(p.name)}</b><span>${p.type === 'Actor' ? (p.role ? '饰 ' + esc(p.role) : '演员') : { Director: '导演', Writer: '编剧', Producer: '制片' }[p.type] || p.type}</span></a>`).join('')}</div>`;
const similar = (it) => APP.ALL.filter((x) => x.id !== it.id && x.type === it.type && APP.genres(x, 9).some((g) => APP.genres(it, 9).includes(g))).sort((a, b) => (b.rating || 0) - (a.rating || 0)).slice(0, 14);
const heroOf = (it, o) => { const bd = APP.bd(it); return `<section class="hero ${o.cls || ''}" style="--hh:${o.hh || '72vh'};--fy:${o.fy || '18%'};--sh:${o.sh || '0rem'}"><div class="bleed${bd ? '' : ' soft'}">${bd ? im(bd) : `<img data-blur="${APP.poster(it)}" alt="">`}</div><span class="vert"${o.capTop ? ` style="${o.capTop}"` : ''}>${bd ? o.cap : '没有剧照 · 用海报虚化代替'}</span>
  <div class="in" data-t0><div><div class="kick">${o.kick}</div><div class="lgbox${it.name.length > 9 ? ' long' : ''}">${APP.title(it)}</div>${o.under || ''}</div>
  <div class="side"><div class="rate">${it.rating ? APP.score(it) : ''}<small>${it.rating ? '评分' : '暂无评分'}${it.officialRating ? ' · ' + it.officialRating : ''}</small></div></div></div></section>`; };
const overview = (it) => { const o = (it.overview || '').replace(/^\s+/, ''), n = o.length; return o ? `<p class="ov${n > 330 ? ' clamp' : ''}${n < 60 ? ' short' : ''}">${esc(o)}</p>${n > 330 ? `<a class="lnk more" data-more-ov>展开全文 · 共 ${n} 字</a>` : ''}` : '<p class="ov short" style="color:var(--faint)">服务器上没有这部片子的简介。</p>'; };

// ------------------------------------------------------------------ 剧集详情
const EPSRC = (it, ser, s) => { if (it.id === '44978' && s === 21) return (X2.onePieceSeason21First30 || {}).episodes || []; if (it.id === '44978' && s === 22) return (X1.onePieceLatestSeason || {}).episodes || []; return ser.episodes.filter((e) => e.s === s); };
const NOTE = { '56342': '第 5 集后半段的烛光，是全剧最好看的一场。', '44978': '一千两百一十五集，我按季给你折好了页角。', '44992': '二十集都画了校对勾，可以放心收藏了。', '44983': '「你还差得远呢」在第 1 集就出现了。', '60104': '这部还在连载，更新了我会在报头写一笔。' };
S.series = {
  g: '剧集详情', title: '剧集详情', sizes: 1, note: '整幅铺满：剧照从窗口顶端铺下来，报头、返回、导航、搜索都压在图上；片名 logo、评分接在渐隐处，右缘竖排小字是图注。',
  states: [['', '颜心记'], ['eps', '分集目录'], ['mark', '校对勾 + 换集'], ['ep', '单集详情'], ['cast', '演职 / 相似'], ['op', '航海王 1215 集'], ['seasons', '选季打开（22 季）'], ['op21', '第 21 季 · 197 集'], ['daima', '龙珠大魔 · 566 字简介'], ['tennis', '网球王子 · 黑笔画 logo'], ['guyu', '谷雨 · 连载、没有分集图']],
  build(st, Q) {
    const id = Q.get('id') || { op: '44978', seasons: '44978', op21: '44978', daima: '44992', tennis: '44983', guyu: '60104' }[st] || '56342', it = ITEM[id] || ITEM['56342'];
    const ser = LIB.series[it.name] || { seasons: [{ name: '第 1 季', index: 1, episodes: it.episodeCount || 0 }], episodes: [], totalEpisodes: it.episodeCount || 0 };
    const seasons = ser.seasons.filter((s) => s.index != null), w = APP.CW.find((x) => x.host.id === it.id);
    const D = this.D = { it, ser, seasons, w, s: st === 'op21' ? 21 : (w && w.c.season) || (seasons[0] || {}).index || 1, range: 0, sel: null };
    const seen = ser.episodes.filter((e) => e.played).length, cur = w ? w.c : null, first = ser.episodes.find((e) => !e.played) || ser.episodes[0];
    const cta = cur ? `续看 第 ${cur.episode} 集 · ${esc(cur.name)}` : first ? `播放 第 ${first.e} 集 · ${esc(first.name)}` : '播放', pos = cur && w.pct ? `${APP.clock(cur.user.positionSec)} / ${APP.clock(cur.runtimeSec)}` : (it.runtimeSec ? APP.dur(it.runtimeSec) : '');
    const dir = who(it, 'Director'), act = who(it, 'Actor', 4), next = ser.episodes.filter((e) => !cur || e.e > cur.episode).slice(0, 4);
    return { tab: 2, sub: 1, hero: 1, html: `<div class="scroll"><div class="page flush">
${heroOf(it, { cap: `剧照 ·《${esc(it.name)}》${seasons.length > 1 ? `共 ${seasons.length} 季` : '第 1 季'}`, kick: `${APP.kind(it)} · ${APP.genres(it, 3).join(' / ')} · ${it.year}${it.status === 'Continuing' ? ' · 连载中' : ''}`, fy: it.id === '56342' ? '8%' : '18%', capTop: it.id === '56342' ? 'top:auto;bottom:22%' : '', sh: it.id === '44983' ? '11.6rem' : '' })}
<section class="dbody"><div>
  <div class="cta favbox"><a class="btn" data-nav="player">${ic('play')}<span class="cta-t">${cta}</span><small class="cta-p">${pos}</small>${w && w.pct ? `<u style="width:${w.pct}%"></u>` : ''}</a>
    <a class="ib" data-fav><span class="ft">收藏</span></a>${APP.favSlot()}<a class="ib">${APP.mk(seen && seen === ser.totalEpisodes)}整部已看</a><a class="ib" data-more data-ctx="${it.id}">${ic('more')}更多</a></div>
  ${overview(it)}
  <div class="note">${APP.msc('round', 52, 'data-say=".dbody .say"')}<div><em>主编按 · EDITOR'S NOTE</em><p class="say">${NOTE[it.id] || '这一部我还没来得及写按语，先看再说。'}</p></div></div></div>
 <dl class="facts">${dir.length ? `<div><dt>导演</dt><b>${dir.map(pLink).join('　')}</b></div>` : ''}${act.length ? `<div><dt>主演</dt><b>${act.map(pLink).join('　')}</b></div>` : ''}
  <div><dt>集数</dt><b>${seasons.length > 1 ? seasons.length + ' 季 · ' : ''}${ser.totalEpisodes.toLocaleString()} 集${it.runtimeSec ? ' · 每集约 ' + APP.dur(it.runtimeSec) : ''}<div class="bar"><u style="width:${ser.totalEpisodes ? Math.max(seen ? 1 : 0, 100 * seen / ser.totalEpisodes) : 0}%"></u></div></b></div>
  <div><dt>进度</dt><b>已看 ${seen} 集${cur ? ` · 停在第 ${cur.episode} 集` : ''}</b></div>
  <div><dt>首播</dt><b>${day(it.premiere)}${it.ended ? ' — ' + day(it.ended) : ''}</b></div><div><dt>状态</dt><b>${it.status === 'Continuing' ? '连载中' : '已完结'}</b></div>
  ${(it.studios || []).length ? `<div><dt>出品</dt><b>${it.studios.slice(0, 2).map(esc).join(' / ')}</b></div>` : ''}<div><dt>类型</dt><b>${APP.genres(it, 6).join(' / ')}</b></div></dl>
 <div class="aside3">${APP.hd('', '接下来', 'UP NEXT', '')}${next.map((e) => `<a class="li" style="grid-template-columns:4.6rem 1fr"><em>${pad(e.e)}</em><div><b>${esc(e.name)}</b><span>${APP.dur(e.runtimeSec || it.runtimeSec)}${e.played ? ' · 已看' : ''}</span></div></a>`).join('')}</div>
</section>
<section class="sec" id="eps">${APP.hd('01', '分集目录', 'EPISODES', `<span id="epsum"></span>`)}<div class="sctl" id="sctl"></div><div id="epd"></div><div class="ranges" id="ranges"></div><div id="epl"></div></section>
<section class="sec" id="cast">${APP.hd('02', '演职人员', 'CAST & CREW', `<span>${(it.people || []).length} 位 · 彩色照片来自服务器</span>`)}${castRow(it)}</section>
<section class="sec">${APP.hd('03', '相似', 'MORE LIKE THIS', '')}<div class="hs row">${similar(it).map((x, i) => APP.pc(x, { no: pad(i + 1) })).join('')}</div></section>
<div class="end"><span class="kick">— 本篇完 —</span><div class="col">${esc(it.name)} · ${it.originalTitle && it.originalTitle !== it.name ? esc(it.originalTitle) + ' · ' : ''}${it.year}</div></div>
</div></div>` };
  },
  init(root, st) {
    const D = this.D, { it, ser, seasons, w } = D, $ = (s) => root.querySelector(s), multi = seasons.length > 1, PER = 50;
    const epCard = (e, rows) => { const cur = w && w.c.episode === e.e && w.c.season === e.s, th = APP.key(e.thumb), sub = `${APP.dur(e.runtimeSec || it.runtimeSec)}${e.premiere ? ' · ' + day(e.premiere).replace(/^\d+ 年 /, '') : ''}${cur ? ' · 看到 ' + APP.clock(w.c.user.positionSec) : ''}`;
      return rows ? `<a class="epc${e.played ? ' seen' : ''}${cur ? ' cur' : ''}" data-e="${e.e}"><em>${e.e}</em><div><b>${esc(e.name)}</b><span>${sub}</span></div>${APP.mk(e.played)}</a>`
        : `<a class="epc${e.played ? ' seen' : ''}${cur ? ' cur' : ''}" data-e="${e.e}"><div class="th">${th ? im(th) : APP.ph('第 ' + e.e + ' 集', 'NO STILL', 'position:absolute;inset:0;font-size:2rem')}${cur && w.pct ? `<u style="width:${w.pct}%"></u>` : ''}</div><div class="cap"><em>${pad(e.e)}</em><b>${esc(e.name)}</b><span>${sub}</span>${APP.mk(e.played)}</div><p>${esc(e.overview || '')}</p></a>`; };
    const draw = () => {
      const S0 = seasons.find((x) => x.index === D.s) || seasons[0] || { name: '第 1 季', episodes: ser.totalEpisodes }, eps = EPSRC(it, ser, D.s), total = S0.episodes, first = eps.length ? eps[0].e : 1;
      $('#epsum').textContent = multi ? `${seasons.length} 季 · 共 ${ser.totalEpisodes.toLocaleString()} 集` : `共 ${ser.totalEpisodes} 集 · 已看 ${ser.episodes.filter((e) => e.played).length}`;
      $('#sctl').innerHTML = (multi ? `<span class="dd${D.open ? ' open' : ''}" id="sdd"><b>${esc(S0.name)}</b><small>${total} 集</small>${ic('chevd')}<div class="ctx l${seasons.length > 8 ? ' cols' : ''}">${seasons.map((x) => `<a data-s="${x.index}" class="${x.index === D.s ? 'on' : ''}">${esc(x.name)}<small>${x.episodes} 集</small></a>`).join('')}</div></span>
        <div class="sruler" title="每一格是一季，宽度 = 集数">${seasons.map((x) => `<a data-s="${x.index}" class="${x.index === D.s ? 'on' : ''}" style="--w:${x.episodes}" title="${esc(x.name)} · ${x.episodes} 集">${seasons.length <= 12 || x.index % 5 === 0 || x.index === 1 || x.index === D.s ? `<b>${x.index}</b>` : ''}</a>`).join('')}</div><span class="kbd">[ ] 换季</span>` : `<span class="dd" style="cursor:default"><b>${esc(S0.name)}</b><small>${total} 集</small></span>`) +
        `<a class="dd" style="margin-left:${multi ? '0' : 'auto'}" data-allseen>${APP.mk()}本季全部标为已看</a>`;
      const nr = Math.ceil(total / PER); D.range = Math.min(D.range, Math.max(0, nr - 1));
      $('#ranges').innerHTML = nr > 1 ? Array.from({ length: nr }, (_, i) => `<a class="chip${i === D.range ? ' on' : ''}" data-r="${i}">${first + i * PER} – ${Math.min(first + total - 1, first + i * PER + PER - 1)}</a>`).join('') + `<span style="margin-left:1.2rem">跳到第</span><input value="" placeholder="${first + total - 1}"><span>集</span><span class="kbd">G</span>` : '';
      $('#ranges').style.display = nr > 1 ? '' : 'none';
      const page = eps.slice(D.range * PER, D.range * PER + PER), rows = !page.some((e) => e.thumb), missing = Math.min(PER, total - D.range * PER) - page.length;
      $('#epl').innerHTML = page.length || missing > 0 ? `<div class="eps${rows ? ' rows' : ''}">${page.map((e) => epCard(e, rows)).join('')}${missing > 0 ? Array(Math.min(missing, rows ? 9 : 4)).fill(0).map(() => rows ? `<div class="epc"><i class="sk" style="height:2rem;width:3rem"></i><i class="sk" style="height:1.6rem;width:70%"></i></div>` : `<div><i class="sk" style="aspect-ratio:16/9"></i><i class="sk" style="height:1.4rem;width:70%;margin-top:1.2rem"></i></div>`).join('') : ''}</div>${missing > 0 ? `<p class="kick" style="margin-top:2rem;color:var(--faint)">这一页还有 ${missing} 集，滚到这里时再向服务器取（本稿只带了前 ${page.length} 集的真实数据）</p>` : ''}` : `<div class="empty" style="min-height:16rem"><p class="kick" style="color:var(--faint)">这一季的分集还没取到</p></div>`;
      const e = D.sel != null && [...eps, ...ser.episodes].find((x) => x.e === D.sel);
      $('#epd').innerHTML = e ? `<div class="epd" style="position:relative"><div class="th">${e.thumb ? im(APP.key(e.thumb)) : (APP.bd(it) ? im(APP.bd(it)) : '')}</div><div><div class="kick">${esc(S0.name)} · 第 ${e.e} 集${e.played ? ' · 已看' : ''}</div><h4>${esc(e.name)}</h4>
        <div class="m">${APP.dur(e.runtimeSec || it.runtimeSec)} · ${day(e.premiere)} 首播${w && w.c.episode === e.e ? ` · 看到 ${APP.clock(w.c.user.positionSec)}（${w.pct}%）` : ''}</div><p>${esc(e.overview || '这一集没有简介。')}</p>
        <div class="cta"><a class="btn sm" data-nav="player">${ic('play')}${w && w.c.episode === e.e ? '续看' : '播放这一集'}</a><a class="btn sm line">${ic('replay')}从头看</a><a class="ib">${APP.mk(e.played)}标记已看</a><a class="ib">${ic('dl')}下载</a></div>
        <div class="tech">1080p · H.264 · 8.2 Mbps　AAC 2.0 国语　内封字幕：简体中文 / 繁體中文（规格为示意）</div></div><a class="x" data-epx>${ic('x')}</a></div>` : '';
      APP.fill(root.querySelector('#eps'));
    };
    root.addEventListener('click', (e) => { const c = (s) => e.target.closest(s);
      if (c('.mk') || c('[data-fav]')) return;
      if (c('[data-s]')) { D.s = +c('[data-s]').dataset.s; D.open = false; D.range = 0; D.sel = null; draw(); return; }
      if (c('#sdd')) { D.open = !D.open; draw(); return; }
      if (c('[data-r]')) { D.range = +c('[data-r]').dataset.r; draw(); return; }
      if (c('[data-epx]')) { D.sel = null; draw(); return; }
      if (c('[data-more-ov]')) { root.querySelector('.ov').classList.remove('clamp'); c('[data-more-ov]').remove(); APP.dirty(); return; }
      const ep = c('.epc[data-e]'); if (ep) { D.sel = +ep.dataset.e; const x = [...EPSRC(it, ser, D.s)].find((q) => q.e === D.sel); draw(); root.querySelectorAll('.epc').forEach((q) => q.classList.toggle('cur', +q.dataset.e === D.sel));
        $('.cta-t').textContent = `播放 第 ${x.e} 集 · ${x.name}`; $('.cta-p').textContent = APP.dur(x.runtimeSec || it.runtimeSec); const m = APP.mascot(); if (m) { m.blink(); m.say(`第 ${x.e} 集《${x.name}》的稿子给你翻好了。`, 3000); } }
    }, true);
    window.addEventListener('keydown', (e) => { if (!multi) return; const i = seasons.findIndex((x) => x.index === D.s); if (e.key === ']' && i < seasons.length - 1) { D.s = seasons[i + 1].index; D.range = 0; draw(); } if (e.key === '[' && i > 0) { D.s = seasons[i - 1].index; D.range = 0; draw(); } });
    if (st === 'seasons') D.open = true; if (st === 'ep') D.sel = (w && w.c.episode) || 1;
    draw(); return APP.setSpot(APP.poster(it));
  },
  after(root, st) { const sc = root.querySelector('.scroll'), to = (sel, d) => { sc.scrollTop = root.querySelector(sel).getBoundingClientRect().top + sc.scrollTop - (d || 106); sc.dispatchEvent(new Event('scroll')); };
    if (['eps', 'mark', 'ep', 'seasons', 'op21'].includes(st)) to('#eps'); if (st === 'cast') to('#cast');
    if (st === 'mark') { const c = root.querySelectorAll('.epc'); c[9].querySelector('.mk').click(); c[10].querySelector('.mk').click(); c[6].click(); } },
};

// ------------------------------------------------------------------ 电影详情
const FAKEV = [['4K · HDR10 · REMUX', '58.3 GB · HEVC · 76 Mbps', '编的'], ['1080p · 导演评论音轨', '9.8 GB · H.264 · 9 Mbps', '编的']];
S.movie = {
  g: '电影详情', title: '电影详情', note: '整幅铺满 + 海报压在渐隐处（悬停时跟着鼠标倾）；版本 / 音轨 / 字幕是三行选择器，下面是完整的媒体信息表。',
  states: [['', '盗梦空间'], ['ver', '版本选择器打开'], ['streams', '音轨字幕 / 媒体信息'], ['cast', '演职人员'], ['nologo', '没有 logo（男人没有好东西）'], ['short', '10 分 · 38 字简介'], ['long', '超长片名 · 没有剧照']],
  build(st, Q) {
    const id = Q.get('id') || { nologo: '57454', short: '57449', long: '39852' }[st] || '39778', it = this.it = ITEM[id] || ITEM['39778'], w = APP.CW.find((x) => x.host.id === it.id);
    const src = (it.sources || [])[0], V = src ? src.streams.filter((s) => s.type === 'Video') : [], A = src ? src.streams.filter((s) => s.type === 'Audio') : [], T = src ? src.streams.filter((s) => s.type === 'Subtitle') : [];
    const gb = (n) => (n / 1073741824).toFixed(2) + ' GB', v0 = V[0], real = src ? [`${src.name} · ${(v0 && v0.codec || '').toUpperCase()}${v0 && v0.range ? ' · ' + v0.range : ''}`, `${gb(src.size)} · ${src.container.toUpperCase()} · ${(src.bitrate / 1e6).toFixed(1)} Mbps${src.isStrm ? ' · strm 直链' : ''}`, ''] : ['默认版本', '媒体信息还没取到', ''];
    const vers = [real, ...(st === 'ver' || id === '39778' ? FAKEV : [])], dir = who(it, 'Director'), act = who(it, 'Actor', 4), wr = who(it, 'Writer', 2);
    const row = (label, cur, opts, open, idn) => `<div><dt>${label}</dt><dd><span class="dd${open ? ' open' : ''}" ${idn ? `id="${idn}"` : ''}><b>${cur[0]}</b><small>${cur[1] || ''}</small>${opts.length > 1 ? ic('chevd') : ''}${opts.length > 1 ? `<div class="ctx">${opts.map((o, i) => `<a class="${i === 0 ? 'on' : ''}"><b>${o[0]}${o[2] ? `<i>${o[2]}</i>` : ''}</b><small>${o[1] || ''}</small></a>`).join('')}</div>` : ''}</span></dd></div>`;
    const aOpt = A.map((a) => [esc(a.title || a.codec), `${a.codec.toUpperCase()} · ${a.channels || 2} 声道${a.lang && a.lang !== 'und' ? ' · ' + a.lang : ''}`]), tOpt = [['关闭', ''], ...T.map((t) => [esc(t.title || t.codec), `${t.codec.toUpperCase()}${t.external ? ' · 外挂' : ' · 内封'}`])];
    return { tab: 2, sub: 1, hero: 1, html: `<div class="scroll"><div class="page flush">
${heroOf(it, { cls: 'mv', hh: '66vh', cap: `剧照 ·《${esc(it.name)}》`, kick: ['电影', APP.genres(it, 3).join(' / '), it.year, APP.dur(it.runtimeSec)].filter(Boolean).join(' · '), under: it.tagline && /[一-龥]/.test(it.tagline) && !/配信/.test(it.tagline) ? `<p class="st">「${esc(it.tagline)}」</p><div style="height:1.6rem"></div>` : '' })}
<section class="dbody mv"><div class="float tiltable"><div class="po">${im(APP.poster(it))}</div></div>
 <div><div class="cta favbox"><a class="btn" data-nav="player&mv=1">${ic('play')}${w && w.pct ? '续看' : '播放'}<small>${w && w.pct ? `${APP.clock(w.c.user.positionSec)} / ${APP.clock(it.runtimeSec)}` : APP.dur(it.runtimeSec)}</small>${w && w.pct ? `<u style="width:${w.pct}%"></u>` : ''}</a>
    <a class="ib" data-fav><span class="ft">收藏</span></a>${APP.favSlot()}<a class="ib">${APP.mk()}已看</a><a class="ib">${ic('dl')}下载</a><a class="ib" data-more data-ctx="${it.id}">${ic('more')}更多</a></div>
  <dl class="sel">${row('版本', vers[0], vers, st === 'ver', 'vdd')}${row('音轨', aOpt[0] || ['默认音轨', ''], aOpt, false)}${row('字幕', tOpt[1] || tOpt[0], tOpt, false)}</dl>
  ${overview(it)}</div>
 <dl class="facts">${dir.length ? `<div><dt>导演</dt><b>${dir.map(pLink).join('　')}</b></div>` : ''}${wr.length ? `<div><dt>编剧</dt><b>${wr.map(pLink).join('　')}</b></div>` : ''}${act.length ? `<div><dt>主演</dt><b>${act.map(pLink).join('　')}</b></div>` : ''}
  <div><dt>片长</dt><b>${APP.dur(it.runtimeSec) || '—'}</b></div><div><dt>上映</dt><b>${day(it.premiere) || it.year}</b></div>${it.originalTitle && it.originalTitle !== it.name ? `<div><dt>原名</dt><b>${esc(it.originalTitle)}</b></div>` : ''}
  ${(it.studios || []).length ? `<div><dt>出品</dt><b>${it.studios.slice(0, 2).map(esc).join(' / ')}</b></div>` : ''}<div><dt>类型</dt><b>${APP.genres(it, 6).join(' / ') || '—'}</b></div><div><dt>分级</dt><b>${it.officialRating || '未分级'}</b></div></dl>
 <div class="aside3">${APP.hd('', '同题材', 'SEE ALSO', '')}${similar(it).slice(0, 4).map((x, i) => `<a class="li po" ${APP.go(x)} style="grid-template-columns:5.4rem 1fr auto">${im(APP.poster(x), 'p')}<div><b>${esc(x.name)}</b><span>${APP.meta(x)}</span></div><span class="sc">${APP.score(x)}</span></a>`).join('')}</div>
</section>
${src ? `<section class="sec" id="streams">${APP.hd('01', '媒体信息', 'STREAMS', `<span>${src.container.toUpperCase()} · ${gb(src.size)} · ${src.streams.length} 条流</span>`)}<table class="tb"><tr><th>类型</th><th>标题</th><th>编码</th><th>规格</th><th>语言</th><th>来源</th><th>默认</th></tr>
  ${src.streams.map((s) => `<tr><td class="n">${{ Video: '视频', Audio: '音轨', Subtitle: '字幕' }[s.type]}</td><td class="t" style="font-size:1.4rem">${esc(s.title || '—')}</td><td>${(s.codec || '').toUpperCase()}</td><td>${s.type === 'Video' ? `${s.width} × ${s.height} · ${s.range || 'SDR'}` : s.type === 'Audio' ? (s.channels || 2) + ' 声道' : '—'}</td><td>${s.lang && s.lang !== 'und' ? s.lang : '未标注'}</td><td>${s.external ? '外挂文件' : '内封'}</td><td>${s.isDefault ? '✓' : ''}</td></tr>`).join('')}</table></section>` : ''}
${(it.people || []).length ? `<section class="sec" id="cast">${APP.hd('02', '演职人员', 'CAST & CREW', `<span>${it.people.length} 位${it.people.some((p) => !p.image) ? ' · 没有照片的用姓氏代替' : ''}</span>`)}${castRow(it)}</section>` : ''}
<section class="sec">${APP.hd('03', '相似', 'MORE LIKE THIS', '')}<div class="hs row">${similar(it).map((x, i) => APP.pc(x, { no: pad(i + 1) })).join('')}</div></section>
<div class="end"><span class="kick">— 本篇完 —</span><div class="col">${esc(it.name)} · ${it.year}</div></div></div></div>` };
  },
  init(root) { root.addEventListener('click', (e) => { const d = e.target.closest('.sel .dd'); root.querySelectorAll('.sel .dd.open').forEach((x) => { if (x !== d) x.classList.remove('open'); }); if (d && d.querySelector('.ctx')) { const a = e.target.closest('.ctx a'); if (a) { d.querySelectorAll('.ctx a').forEach((x) => x.classList.toggle('on', x === a)); d.querySelector(':scope > b').innerHTML = a.querySelector('b').innerHTML; d.querySelector(':scope > small').textContent = a.querySelector('small').textContent; d.classList.remove('open'); } else d.classList.toggle('open'); } if (e.target.closest('[data-more-ov]')) { root.querySelector('.ov').classList.remove('clamp'); e.target.closest('[data-more-ov]').remove(); } });
    return APP.setSpot(APP.poster(this.it)); },
  after(root, st) { const sc = root.querySelector('.scroll'), el = root.querySelector(st === 'streams' ? '#streams' : st === 'cast' ? '#cast' : '#none'); if (el) { sc.scrollTop = el.getBoundingClientRect().top - 106; sc.dispatchEvent(new Event('scroll')); } },
};

// ------------------------------------------------------------------ 人物
const PEOPLE = [...(X1.persons || []), ...LIB.persons];
const ROLES = {}; (X2.zhouXingchiRoles || []).forEach((r) => { ROLES[r.id] = r; });
const PT = { Actor: '演员', Director: '导演', Writer: '编剧', Producer: '制片' };
S.person = {
  g: '人物', title: '人物页', note: '整幅铺满：大照片占右半个窗口、从顶端铺到底，向左渐隐进纸面；名字是这一页最大的字。服务器上没有简介时，用片库里的作品统计代替。',
  states: [['', '作品多（周星驰 · 23 部）'], ['works', '作品年表'], ['one', '只有 1 部（罗云熙）']],
  build(st, Q) {
    const id = Q.get('id') || (st === 'one' ? '54529' : '39947'), p = this.p = PEOPLE.find((x) => x.id === id) || PEOPLE[0], works = p.works.slice().sort((a, b) => (b.year || 0) - (a.year || 0));
    const big = Object.values(X1.personLarge || {}).find((x) => x.id === p.id), key = APP.key(big ? big.image.replace(/\.$/, '.jpg') : p.image);
    const ys = works.map((x) => x.year).filter(Boolean), jobs = [...new Set(works.map((x) => (ROLES[x.id] || {}).personType).filter(Boolean))].map((t) => PT[t] || t), avg = works.filter((x) => x.rating).reduce((a, x, _, l) => a + x.rating / l.length, 0);
    const top = works.slice().sort((a, b) => (b.rating || 0) - (a.rating || 0)).slice(0, 4), one = works.length === 1, full = one && (ITEM[works[0].id] || works[0]);
    const decades = {}; works.forEach((x) => { const d = Math.floor((x.year || 0) / 10) * 10; (decades[d] = decades[d] || []).push(x); });
    return { tab: 2, sub: 1, hero: 1, html: `<div class="scroll"><div class="page flush">
<section class="phero"><div class="bleed">${im(key)}</div><span class="vert">照片 · ${esc(p.name)}</span>
 <div class="in" data-t0><div class="kick">PEOPLE · 人物${jobs.length ? '　' + jobs.join(' / ') : ''}</div><h1>${esc(p.name)}</h1>
  <p class="bio">${p.overview ? esc(p.overview) : `服务器上没有这位的简介和生日。下面是从你的片库里数出来的：${one ? '目前只收了一部作品。' : `${ys.length ? Math.min(...ys) + ' 到 ' + Math.max(...ys) + ' 年，' : ''}共 ${works.length} 部${jobs.length > 1 ? '，身份有' + jobs.join('、') : ''}。`}`}</p>
  <div class="stats"><div><b>${works.length}</b><span>片库里的作品</span></div>${one ? '' : `<div><b>${Math.min(...ys)}–${String(Math.max(...ys)).slice(2)}</b><span>年份跨度</span></div><div><b>${avg.toFixed(1)}</b><span>平均评分</span></div>`}<div><b>${works.filter((x) => x.type === 'Movie').length}<small style="font-size:.5em"> / ${works.filter((x) => x.type !== 'Movie').length}</small></b><span>电影 / 剧集</span></div></div>
  ${one ? `<a class="btn" ${APP.go(full)}>${ic('play')}去看《${esc(full.name)}》</a>` : `<div class="kick" style="margin-bottom:1.2rem">KNOWN FOR · 评分最高的四部</div><div class="known">${top.map((x) => `<div class="pc" ${APP.go(x)} data-ctx="${x.id}"><div class="po">${im(APP.poster(x))}<i class="sheen"></i></div></div>`).join('')}</div>`}</div></section>
${one ? `<section class="sec" id="works" style="margin-top:0">${APP.hd('01', '作品', 'WORKS', '<span>片库里只有这一部</span>')}<div class="sgrid" style="margin-top:0"><a class="best" ${APP.go(full)}>${im(APP.bd(full) || APP.poster(full))}<div class="t">${APP.title(full)}<span>${APP.kind(full)} · ${APP.meta(full)}<br>${APP.len(full)} · ${APP.score(full)} 分</span></div></a>
  <div><p class="ov" style="margin-top:0;font-size:1.6rem">${esc(APP.stand(full, 150))}</p><div class="note" style="margin-top:2.4rem"><div><em>主编按 · EDITOR'S NOTE</em><p>片库里暂时只有这一部。等有新片入库，我会把这一页补上。</p></div></div></div></div></section>
 <section class="sec">${APP.hd('02', '同剧演员', 'CO-STARS', '')}${castRow({ people: (full.people || []).filter((x) => x.id !== p.id) })}</section>`
 : `<section class="sec" id="works" style="margin-top:0">${APP.hd('01', '作品年表', 'FILMOGRAPHY', `<span>${works.length} 部 · 新 → 旧</span>`)}
  ${Object.keys(decades).sort((a, b) => b - a).map((d) => `<div class="grp">${d} 年代<small>${decades[d].length} 部</small></div><div class="cols2">${decades[d].map((x) => { const r = ROLES[x.id] || {}; return `<a class="li po" ${APP.go(x)} data-ctx="${x.id}" style="grid-template-columns:5.6rem 5.4rem 1fr auto"><em style="font-size:2rem">${x.year || ''}</em>${im(APP.poster(x), 'p')}<div><b>${esc(x.name)}</b><span>${r.personType === 'Actor' && r.role ? '饰 ' + esc(r.role) : PT[r.personType] || '参演'}${r.genres ? ' · ' + r.genres.slice(0, 2).join(' / ') : ''} · ${APP.dur(x.runtimeSec)}</span></div><span class="sc">${APP.score(x)}</span></a>`; }).join('')}</div>`).join('')}</section>`}
<div class="end"><span class="kick">— 本篇完 —</span><div class="col">${esc(p.name)} · 片库里 ${works.length} 部</div></div></div></div>` };
  },
  init() { return APP.setSpot(APP.key(this.p.image)); },
  after(root, st) { if (st === 'works') { const sc = root.querySelector('.scroll'); sc.scrollTop = root.querySelector('#works').getBoundingClientRect().top - 106; sc.dispatchEvent(new Event('scroll')); } },
};

// ------------------------------------------------------------------ 我的：读者证 + 记录 / 收藏
const FAVS = ['39778', '56342', '44978', '39806', '44970', '39840', '57449', '44992', '39831'];
const meHtml = (st) => { const G = [['今天', APP.CW.slice(0, 3)], ['昨天', APP.CW.slice(3, 7)], ['本周早些时候', APP.CW.slice(7)]], fav = st === 'fav' || st === 'favempty';
  return `<div class="scroll"><div class="page"><div class="card">${APP.msc('round', 84, 'data-say=".card .say"')}<div><em>SUBSCRIBER · No. 000318</em><h4>读者证 · 影迷</h4><p class="say">已订阅 318 期 · 主编咕咕为你编排</p></div>
  <div class="stats"><div><b>${APP.CW.length}</b><span>在看</span></div><div><b>${FAVS.length}</b><span>收藏</span></div><div><b>41</b><span>本月看完（集）</span></div><div style="align-self:center"><a class="lnk" data-nav="account&st=switch">${ic('user')}家里的片库 · 切换</a><br><a class="lnk" data-nav="settings" style="margin-top:1rem">${ic('set')}设置</a></div></div></div>
 <div class="tools" style="margin-top:1.6rem"><div class="seg" style="font-size:1.8rem;font-weight:900;letter-spacing:.1em"><a class="${fav ? '' : 'on'}" data-nav="me">记录</a><a class="${fav ? 'on' : ''}" data-nav="me&st=fav">收藏</a></div><div class="r" style="color:var(--faint);font-style:italic">${fav ? `${st === 'favempty' ? 0 : FAVS.length} 部 · 盖过章的` : `${APP.CW.length} 条 · 按最后观看时间（分组日期为示意）`}</div></div><div class="rule ink"></div>
 ${st === 'favempty' ? `<div class="empty">${APP.pbox({ eyes: 'u', mouth: 'o', props: ['sheet'] }, '捧着一张白稿纸', '<b>收藏夹还是一张白纸</b>在任何一张海报上点「藏」字印章位，或者右键 → 收藏，我就给它盖章归档。', 12)}</div>`
  : fav ? `<div class="grid" style="margin-top:2.8rem">${FAVS.map((id) => { const it = ITEM[id]; return APP.pc(Object.assign({}, it, { user: Object.assign({}, it.user, { favorite: true }) }), { cls: 'wrap', sub: `${APP.favSlot(1, 1)}　${it.year} · ${APP.kind(it)}` }); }).join('')}</div>`
  : `<div class="hist">${G.map(([d, list], gi) => list.length ? `<div class="grp">${d}<small>${list.length} 条</small></div>${list.map((w) => `<a class="li" ${w.go} data-ctx="${w.host.id || ''}"><div class="th">${w.th ? im(w.th) : APP.ph(w.name, 'NO STILL', 'position:absolute;inset:0;font-size:1.5rem')}${w.pct ? `<u style="width:${w.pct}%"></u>` : ''}</div><div><b>${esc(w.name)}</b><span>${esc(w.sub)} · ${w.left}</span></div><i class="pc2 num">${w.pct ? w.pct + '%' : '未开始'}</i><i class="rm" title="从记录里移除">${ic('x')}</i></a>`).join('')}` : '').join('')}</div>`}
 </div></div>`; };
S.me = { g: '记录 · 收藏', title: '我的：记录 / 收藏', note: '读者证是她的固定位置之一；记录按天分组，进度是真实的 0 %–79 %；收藏的每一张都带着「藏」字章。', states: [['', '记录'], ['fav', '收藏'], ['favempty', '收藏为空']],
  build(st) { return { tab: 4, solid: 1, html: meHtml(st) }; } };
S.account = { g: '账号', title: '账号菜单 / 切换', note: '账号菜单从右上角的玻璃圆钮里长出来（引擎的变形，仍是固定层的一部分）；切换服务器 / 账号是一张纸质对话框。', states: [['', '账号菜单'], ['switch', '服务器 / 账号切换']],
  build(st) { const sw = st === 'switch'; return { tab: 4, solid: 1, menu: !sw, html: meHtml('') + (sw ? `<div class="dim"><div class="sheet" style="width:64rem"><a class="x">${ic('x')}</a><h3>换一个片库<em>SERVERS & ACCOUNTS</em></h3>
   ${[['家里的片库', '局域网 · Emby 4.9 · 232 部', '已连接', ['影迷', '小满', '访客'], 1], ['办公室 NAS', '外网 · Emby 4.8 · 上次连接 3 天前', '可连接', ['影迷'], 0], ['朋友的服务器', '外网 · 延迟 180 ms', '连不上', ['guest'], 0]].map((s) => `<div class="srv${s[4] ? ' on' : ''}"><b>${s[0]}</b><span>${s[1]}</span><div class="st">${s[4] ? APP.mk(1) : ''}${s[2]}</div><div class="who">${s[3].map((u, i) => `<a class="chip${s[4] && i === 0 ? ' on' : ''}">${u}</a>`).join('')}<a class="chip">+ 登录别的账号</a></div></div>`).join('')}
   <div class="acts"><a class="lnk" data-nav="servers&st=add" style="margin-right:auto">+ 添加服务器</a><a class="lnk" data-nav="servers">管理服务器</a><a class="btn sm">完成</a></div><p style="font-size:1.15rem;color:var(--faint)">服务器名、账号名均为示意，不显示地址。</p></div></div>` : '') }; } };
})();
