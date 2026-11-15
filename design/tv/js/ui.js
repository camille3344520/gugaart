/* 咕咕嘎嘎 · B1 · 电视端 —— data helpers (real library export in js/data.js) and the parts every screen shares. */
(function () {
'use strict';
const APP = window.APP, L = window.LIB, X = window.EXTRA, { ic, im, esc, pad } = APP;
const U = window.U = {};
// ---- index of everything that has an id
const BY = U.BY = {};
const put = (o) => { if (o && o.id) BY[o.id] = Object.assign(BY[o.id] || {}, o); };
Object.values(L.latest).forEach((a) => a.forEach(put)); L.continueWatching.forEach(put); L.details.forEach(put);
L.stress.longestTitles.forEach((t) => put({ id: t.id, name: t.name, year: t.year, type: 'Movie', images: { poster: t.poster } }));
X.collections.forEach((c) => c.members.forEach((m) => put({ id: m.id, name: m.name, type: m.type, year: m.year, rating: m.rating, runtimeSec: m.runtimeSec, overview: m.overview, images: { poster: m.poster, logo: m.logo } })));
Object.entries(window.EXTRA2.continueSeriesArt).forEach(([n, a]) => put({ id: a.id, name: (BY[a.id] && BY[a.id].name) || n, type: 'Series', images: Object.assign({}, BY[a.id] && BY[a.id].images, { backdrop: a.backdrop, logo: a.logo }) }));
X.persons.forEach((p) => p.works.forEach((w) => { if (!BY[w.id]) put({ id: w.id, name: w.name, type: w.type, year: w.year, rating: w.rating, runtimeSec: w.runtimeSec, images: { poster: w.poster } }); }));
U.byName = (n) => Object.values(BY).find((o) => o.name === n && (o.type === 'Series' || o.type === 'Movie') && o.images && o.images.backdrop) || Object.values(BY).find((o) => o.name === n && o.type !== 'Episode');
U.det = (n) => L.details.find((d) => d.name === n);
U.I = (o, k) => (o && o.images && o.images[k]) || null;
// ---- wording
U.rate = (r) => r == null ? '' : (r >= 10 ? '10' : (Math.round(r * 10) / 10).toFixed(1));
U.dur = (s) => { if (!s) return ''; const m = Math.round(s / 60); return m >= 60 ? `${Math.floor(m / 60)} 小时 ${pad(m % 60)} 分` : `${m} 分钟`; };
U.clock = (s) => { s = Math.round(s || 0); const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60; return (h ? h + ':' + pad(m) : m) + ':' + pad(x); };
U.kind = (o) => o.type === 'Movie' ? '电影' : o.type === 'Episode' ? '剧集' : ((o.genres || []).some((g) => /动画|Animation/.test(g)) ? '动画' : '剧集');
const GEN = { Animation: '动画', Comedy: '喜剧', 'Sci-Fi & Fantasy': '科幻奇幻', '中文字幕': null };
U.genres = (o, n) => (o.genres || []).map((g) => g in GEN ? GEN[g] : g).filter(Boolean).slice(0, n || 3);
U.len = (o) => o.type === 'Movie' ? U.dur(o.runtimeSec) : (o.episodeCount ? `${o.status === 'Continuing' ? '更新至' : '全'} ${o.episodeCount} 集` : '');
U.kick = (o) => [U.kind(o), U.genres(o, 2).join(' '), o.year, U.len(o)].filter(Boolean).join(' · ');
// 导语: a usable tagline, else the first sentence of the synopsis (real data has junk taglines such as 「配信開始日 …」)
U.stand = (o, max) => { let t = o.tagline && !/配信|^\s*$/.test(o.tagline) ? o.tagline : ''; const ov = (o.overview || '').replace(/\s+/g, ' ').trim();
  if (!t) { const m = /^(.{12,}?[。！？!?])/.exec(ov); t = m ? m[1] : ov; } max = max || 64; return t.length > max ? t.slice(0, max - 1) + '…' : t; };
U.cut = (t, n) => { t = (t || '').replace(/\s+/g, ' ').trim(); return t.length > n ? t.slice(0, n - 1) + '…' : t; };
// 继续观看 of a title (by series name or movie id)
U.cw = (o) => L.continueWatching.find((c) => c.id === o.id || (c.seriesName && c.seriesName === o.name));
U.cta = (o) => { const c = U.cw(o); if (!c) return { t: o.type === 'Movie' ? '播放' : '播放 第 1 集', left: '', p: 0 };
  const p = c.user.playedPct || 0, rem = c.runtimeSec && c.user.positionSec != null ? Math.round((c.runtimeSec - c.user.positionSec) / 60) : null;
  return { t: (p ? '续看' : '播放') + (c.type === 'Episode' ? ` 第 ${c.episode} 集` : ''), left: rem != null ? `余 ${rem} 分` : '', p, c }; };

// ---- title art: the TMDB clearlogo; without one, the name set in 宋体 (size by length)
U.logo = (o, cls) => { const k = U.I(o, 'logo'); if (k) return `<span class="logo-w ${cls || ''}">${im(k, 'logo')}</span>`;
  const n = o.name.length; return `<span class="logo-w txt ${cls || ''} ${n <= 5 ? 's1' : n <= 7 ? 's2' : n <= 10 ? 's3' : 's4'}"><b>${esc(o.name)}</b></span>`; };

// ---- masthead: VOL. | 咕 咕 嘎 嘎 (with the penguin mark) | date. Identical on the three ends; TV adds the clock (there is no status bar).
U.mast = (o) => `<header class="mast${o && o.over ? ' over' : ''}"><span class="l">VOL. <i class="vol"><u>318<br>319</u></i></span><b><span>咕咕</span>${APP.emblem(46)}<span>嘎嘎</span></b><span class="r">十一月十四日　星期六　<em>21:47</em></span></header>`;
// a running head for inner pages (no masthead there): section name left, clock right
U.head = (cn, en, right) => `<header class="rhead"><span class="l">${APP.emblem(30)}<b>咕咕嘎嘎</b><i>${en}</i></span><span class="r">${right || ''}<em>21:47</em></span></header>`;
U.sec = (no, cn, en, right) => `<div class="sec-h"><em>${no}</em><b>${cn}</b><i>${en || ''}</i><u></u><span>${right || ''}</span></div>`;
U.hint = (items) => `<div class="hint">${items.map((x) => `<span><kbd>${x[0]}</kbd>${x[1]}</span>`).join('')}</div>`;

// ---- cards
U.prog = (p) => p ? `<i class="pg"><u style="width:${Math.max(3, Math.round(p))}%"></u></i>` : '';
U.poster = (o, x) => { x = x || {}; const k = U.I(o, 'poster');
  return `<a class="card po${x.cls ? ' ' + x.cls : ''}" data-f ${x.attr || ''} data-id="${o.id}"><span class="pic">${k ? im(k) : `<span class="nopic"><b>${esc(o.name)}</b></span>`}${x.badge || ''}${x.seal ? '<i class="seal">藏</i>' : ''}${U.prog(x.p)}</span>` +
    `<span class="cap"><b>${esc(o.name)}</b><small>${[x.no ? '' : o.year, x.sub !== undefined ? x.sub : (o.rating ? '★ ' + U.rate(o.rating) : '')].filter(Boolean).join(' · ')}</small></span></a>`; };
// a wide card (继续观看 / 分集). No still on the server = a typeset slip: big italic number + the name
U.wide = (o) => `<a class="card wd${o.img ? '' : ' np'}${o.cls ? ' ' + o.cls : ''}" data-f ${o.attr || ''}><span class="pic">${o.img ? im(o.img) : `<span class="nopic wide"><em>${o.no || ''}</em><b>${esc(o.alt || o.title)}</b></span>`}${o.badge || ''}${U.prog(o.p)}${o.seen ? U.tick() : ''}</span>` +
  `<span class="cap"><b>${o.pre ? `<em>${o.pre}</em>` : ''}${esc(o.title)}</b><small>${o.sub || ''}</small></span></a>`;
U.tick = () => `<i class="tick"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M3 12.500c2.500 2 4.200 4.500 5.500 7.500C11.500 13 15 8 21.500 3.500"/></svg></i>`;
U.btn = (t, o) => { o = o || {}; return `<a class="btn${o.cls ? ' ' + o.cls : ''}" data-f ${o.attr || ''}>${o.ic ? ic(o.ic) : ''}<span>${t}</span>${o.sub ? `<small>${o.sub}</small>` : ''}${o.p ? U.prog(o.p) : ''}</a>`; };
U.favBtn = (on) => `<a class="btn fav${on ? ' faved' : ''}" data-f data-act="fav">${ic('heart')}<span>${on ? '已收藏' : '收藏'}</span><i class="fav-slot${on ? ' on' : ''}"><b>藏</b></i></a>`;
U.toggleFav = (el) => { const on = !el.classList.contains('faved'); el.classList.toggle('faved', on); el.querySelector('span').textContent = on ? '已收藏' : '收藏'; const s = el.querySelector('.fav-slot'); s.classList.toggle('on', on); s.classList.toggle('off', !on); return on; };
// her reserved slot: round byline photo + one line of text
U.note = (text, o) => { o = o || {}; return `<div class="note${o.cls ? ' ' + o.cls : ''}">${APP.msc(o.size || 68)}<div><em>${o.k || '主编手记 · EDITOR’S NOTE'}</em><p class="say">${text}</p></div></div>`; };
U.pose = (o, cap, text) => `<div class="pose"><div class="pose-art">${APP.pose(o)}</div><div><i>轮廓占位 · ${cap}</i><p>${text}</p></div></div>`;
// a fake QR (the pattern is decoration only: it encodes nothing, no address is shown)
U.qr = (seed) => { let s = seed || 7, h = ''; const N = 25, rnd = () => (s = (s * 16807) % 2147483647) / 2147483647; const eye = (x, y) => x < 7 && y < 7 || x >= N - 7 && y < 7 || x < 7 && y >= N - 7;
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) { if (eye(x, y)) continue; if (rnd() > 0.52) h += `<rect x="${x}" y="${y}" width="1.02" height="1.02"/>`; }
  const e = (x, y) => `<path d="M${x} ${y}h7v7h-7z M${x + 1} ${y + 1}v5h5v-5z" fill-rule="evenodd"/><rect x="${x + 2}" y="${y + 2}" width="3" height="3"/>`;
  return `<svg class="qr" viewBox="-2 -2 ${N + 4} ${N + 4}"><rect x="-2" y="-2" width="${N + 4}" height="${N + 4}" fill="#EFE6D4"/><g fill="#100E0B">${h}${e(0, 0)}${e(N - 7, 0)}${e(0, N - 7)}</g></svg>`; };
// a pool of real titles for walls and rows
U.pool = (names) => names.map((n) => typeof n === 'string' ? (BY[n] || U.byName(n)) : n).filter(Boolean);
U.MOVIES = () => { const seen = {}, out = []; const add = (o) => { if (o && o.type === 'Movie' && !seen[o.name] && U.I(o, 'poster')) { seen[o.name] = 1; out.push(o); } };
  L.stress.longestTitles.forEach((t) => add(BY[t.id])); L.latest['电影'].forEach(add); X.persons[0].works.forEach((w) => add(BY[w.id])); X.collections.forEach((c) => c.members.forEach((m) => add(BY[m.id]))); L.details.forEach(add); return out; };
// pinyin initials of the titles the search demo knows (hand-written for the export)
U.PY = {'颜心记': 'yxj', '永夜星河': 'yyxh', '游戏王：怪兽之决斗': 'yxwgszjd', '英雄联盟：双城之战': 'yxlmsczz', '银河护卫队': 'yhhwd', '银河护卫队3': 'yhhwd3', '航海王': 'hhw', '头文字D': 'twzd', '谷雨': 'gy', '盗梦空间': 'dmkj', '女子当参政': 'nzdcz', '男人没有好东西': 'nrmyhdx', '网球王子': 'wqwz', '龙珠大魔': 'lzdm', '大话西游之月光宝盒': 'dhxyzygbh', '鹿鼎记': 'ldj', '仙剑奇侠传三': 'xjqxzs', '仙剑奇侠传': 'xjqxz', '子夜归': 'zyg', '琅琊榜': 'lyb', '亮剑': 'lj', '人民的名义': 'rmdmy', '射雕英雄传': 'sdyxz', '迪迦奥特曼': 'djatm', '最好的我们': 'zhdwm', '猫猫的奇幻漂流': 'mmdqhpl', '荒野机器人': 'hyjqr', '周处除三害': 'zccsh', '芳华': 'fh', '黑豹': 'hb', '毒液：致命守护者': 'dyzmshz', '阿凡达：水之道': 'afdszd', '四月是你的谎言': 'sysndhy', '宝可梦': 'bkm', '中华小当家': 'zhxdj', '数码宝贝': 'smbb', '围棋少年': 'wqsn', '走马上任': 'zmsr', '尚公主': 'sgz', '伟大的长征': 'wddcz', '士兵突击': 'sbtj', '仙逆': 'xn', '玉楼春': 'ylc', '挽救计划': 'wjjh', '一月是你': 'yysn', '罗云熙': 'lyx', '宋轶': 'sy', '周星驰': 'zxc', '陈瑶': 'cy' };
})();
