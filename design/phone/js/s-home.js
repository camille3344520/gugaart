/* 01 首页：本期封面（三维海报流，背后整幅铺满这一期的剧照）+ 目录（继续观看）+ 新刊 + 栏目。全部是服务器上的真实条目。 */
(function () {
'use strict';
const { ic, im, pad, esc, D } = APP, DB = window.DB;

// 导语 / 主编手记是编辑文案（真实客户端里：导语 = tagline，没有时退回简介第一句；手记按续看状态生成）
const COVERS = [
  { n: '盗梦空间', kick: '科幻 · 动作', stand: '你的梦境，就是犯罪现场。陀螺停下之前，别急着醒。', note: '找个有整块时间的晚上再看，别分两次。' },
  { n: '谷雨', kick: '剧情 · 新刊连载', stand: '1994 年，一桩假发票案把税务稽查、刑警和寻妻的外乡人拴到了一起。', note: '第 11 集昨晚到的，还没拆封。' },
  { n: '颜心记', kick: '悬疑 · 喜剧', stand: '脸盲的高冷郡王，遇上每个月都会「变身」的女游医。面具之下，谁在说谎？', note: '你停在第 5 集一半，两个人刚组上队。' },
  { n: '女子当参政', kick: '音乐 · 历史', stand: '她们走上台，要一张选票。', note: '评分 10.0，全库最高。还剩下半场。' },
  { n: '航海王', kick: '动画 · 冒险', stand: '我是路飞，将要成为海贼王的男人！二十二季、一千二百一十五集的大海。', note: '1,215 集。我替你数过了，慢慢来。' },
  { n: '阿凡达：水之道', kick: '科幻 · 冒险', stand: '回到潘多拉。这一次，故事发生在水下。', note: '三个多小时，记得先去倒杯水。' },
  { n: '头文字D', kick: '动画 · 公路', stand: '秋名山下山最快的，是豆腐店的那辆 86。', note: '戴耳机看，引擎声是另一位主角。' },
  { n: '大话西游之月光宝盒', kick: '喜剧 · 奇幻', stand: '月光宝盒一开，五百年就过去了。', note: '这部我看过十一遍，每次都说是最后一遍。' },
];
const START = 2;
const cwOf = (name) => DB.continueWatching.find((c) => (c.seriesName || c.name) === name);
// one 继续观看 entry -> row texts
K.cw = (c) => {
  const ser = c.type === 'Episode', t = ser ? c.seriesName : c.name, pos = c.user.positionSec || 0, pct = c.user.playedPct ? Math.round(c.user.playedPct) : 0;
  const left = pos ? `余 ${Math.max(1, Math.round((c.runtimeSec - pos) / 60))} 分` : '还没开始';
  const art = APP.ART && APP.ART[t], d = D[t];
  const img = c.images.thumb || (ser ? c.images.poster : null) || (d && d.images.thumb) || (art && art.backdrop) || null;
  return { t, ep: ser ? `第 ${c.episode} 集` : '电影', sub: ser ? `第 ${c.episode} 集 · ${c.name}` : `电影 · ${APP.dur(c.runtimeSec)}`, left, pct, img, ser, c };
};
K.CW = DB.continueWatching.map(K.cw);
const cta = (cv) => { const t = D[cv.n], c = cwOf(cv.n); if (!c) return { b: t.type === 'Movie' ? '播放' : '从第 1 集看起', l: APP.len(t), p: 0 }; const r = K.cw(c); return { b: (r.pct ? '续看' : '看') + (r.ser ? ` 第 ${c.episode} 集` : ''), l: r.pct ? `${r.left} · ${r.pct}%` : (r.ser ? '新到 · 还没开始' : r.left), p: r.pct }; };

const LIBEN = { 电视剧: 'SERIES', 电影: 'FILM', 动漫: 'ANIME', emby302: 'DIRECT', 合集: 'SETS' };
const row = (name, en, n) => `<section class="b-sec">${K.head(name === 'emby302' ? 'emby302' : name + '栏', en, '全部 ' + APP.num(n))}<div class="hs b-new">${DB.latest[name].slice(0, 9).map((t, i) => K.nw(t, i + 1)).join('')}</div></section>`;

APP.reg('home', '首页', {
  title: '首页', note: '上半屏是本期封面：三维海报流，背后整幅铺满这一期的剧照。按住海报左右拖；顶部往下拖出新刊。往下滚是目录（继续观看）、新刊和各媒体库，标签栏在往下读时让到屏幕外。',
  states: [['', '封面'], ['mid', '翻到一半'], ['hard', '最难：1215 集'], ['fav', '盖章'], ['toc', '目录 · 继续观看'], ['new', '最近添加 · 媒体库'], ['end', '滚到底'], ['pull', '下拉付印'], ['first', '首次加载'], ['exit', '再按一次返回退出'], ['offline', '网络断开']],
  build: () => ({
    chrome: K.tabbar(0, 'tabs-home'),
    content: `
<div class="scroll home">
  <section class="b1-cover">
    <div class="b1-bg">${COVERS.map((c, i) => im(K.wash(D[c.n].images.backdrop), '', '')).join('')}<i></i></div>
    <div class="b-off"><em>断线</em><b>连不上服务器，先给你看上次印好的版面</b><a>重试</a></div>
    ${K.pullStrip()}
    <div class="cv-in">
      ${K.masthead()}
      <div class="b-stage">${COVERS.map((c, i) => `<div class="cv" data-i="${i}">${im(D[c.n].images.poster)}<i class="sheen"></i></div>`).join('')}</div>
      <span class="b-logo lgw">${im(D[COVERS[START].n].images.logo)}</span>
      <div class="b-ticks">${COVERS.map(() => '<i></i>').join('')}</div>
      <div class="b-iss">
        <div class="b-no"><b class="num"><span class="roll">${COVERS.map((_, i) => pad(i + 1)).join('<br>')}</span></b><em>/ ${pad(COVERS.length)}</em></div>
        <div class="b-txt"><div class="b-kick kt"></div><p class="b-stand"></p></div>
      </div>
      <div class="b-cta"><a class="b-btn">${ic('play')}<span class="cta-t"></span></a><a class="b-link" data-fav>收藏</a>${K.favSlot()}<span class="r left-t"></span></div>
      <div class="b-pg cover-pg"><u></u></div>
      <div class="b-note">${APP.msc('round', 50, 'data-say=".b1-cover .say"')}<div><em>主编手记 · EDITOR'S NOTE</em><p class="say"></p></div></div>
      <div class="b1-skel"><i class="c l"></i><i class="c m"></i><i class="c r"></i><u style="top:486px;left:24px;width:86px;height:58px"></u><u style="top:490px;left:142px;width:150px;height:10px"></u><u style="top:512px;left:142px;width:240px;height:12px"></u><u style="top:534px;left:142px;width:200px;height:12px"></u><u style="top:598px;left:24px;width:168px;height:46px"></u>
        <div class="b-note" style="top:662px">${K.poseBox('load', '第一次见面。正在把你的片库排成今天的版面：已读到 232 部里的 80 部。')}</div></div>
    </div>
  </section>
  <div class="home-rest">
  <section class="b-sec b-toc">
    ${K.head('目录', 'CONTENTS', `继续观看 · ${K.CW.length}`)}
    ${K.CW.slice(0, 6).map((r, i) => K.li(pad(i + 1), esc(r.t), `${r.ep} · ${r.left}`, r.img, r.pct, '', '', r.t)).join('')}
    <a class="b-all">翻开全部 ${K.CW.length} 条观看记录 ${ic('arrow')}</a>
  </section>
  <section class="b-sec" id="h-new">
    ${K.head('新刊', 'NEW ARRIVALS', '电视剧 · 最近添加')}
    <div class="hs b-new">${DB.latest['电视剧'].slice(0, 9).map((t, i) => K.nw(t, i + 1)).join('')}</div>
  </section>
  <section class="b-sec">
    ${K.head('栏目', 'SECTIONS', DB.libraries.length + ' 个媒体库')}
    <div class="b-grid">${DB.libraries.map((l) => `<a class="b-cell"><em>${LIBEN[l.name]}</em><b>${l.name}</b><span class="num">${APP.num(l.titles)}</span>${l.episodes > l.titles ? `<small>${APP.num(l.episodes)} 集</small>` : ''}</a>`).join('')}</div>
  </section>
  ${row('电影', 'FILM', 118)}${row('动漫', 'ANIME', 32)}${row('emby302', 'DIRECT', 60)}
  <div class="b-end">${K.poseBox('end', '本期到此。共 232 部、7,985 集，由你的片库自动编排；主编咕咕嘎嘎，明晚九点见。')}</div>
  </div>
  <div class="home-wait">${[0, 1, 2, 3].map(() => '<div class="sk-li"><u></u><i></i><s></s><b></b></div>').join('')}</div>
</div>${K.band(2)}
<div class="b-toast"><em>EXIT</em>再按一次返回，合上本期</div>`,
  }),
  init(root) {
    K.common(root);
    const C = COVERS, $ = (s) => root.querySelector(s), cover = $('.b1-cover'), logo = $('.b-logo img'), lw = $('.b-logo'), roll = $('.roll'), ticks = [...root.querySelectorAll('.b-ticks i')], say = $('.b1-cover .say'), m = APP.mascotOf(root, '.b1-cover .msc'), bgs = [...root.querySelectorAll('.b1-bg img')];
    const put = (i) => { const c = C[i], t = D[c.n], a = cta(c); APP.setImg(logo, t.images.logo); lw.classList.toggle('slip', APP.logoDark(t.images.logo)); $('.kt').textContent = `${c.kick} · ${t.year} · ${APP.len(t)}`; $('.b-stand').textContent = c.stand; $('.cta-t').textContent = a.b; $('.left-t').textContent = a.l; $('.cover-pg u').style.width = a.p + '%'; $('.cover-pg').style.width = $('.b-cta .b-btn').offsetWidth + 'px'; say.textContent = c.note; say.dataset.def = c.note; cover.classList.remove('sw'); };
    const flow = K.flow(root, $('.b-stage'), {
      start: START, step: 130,
      lay(c, d, tilt) {
        const ad = Math.abs(d), sg = Math.sign(d), near = Math.min(ad, 1), f = Math.max(0, 1 - ad);
        const x = sg * (ad <= 1 ? ad * 136 : 136 + (ad - 1) * 52), z = -near * 160 - Math.max(0, ad - 1) * 60, ry = -sg * near * 54 + tilt.x * 11 * f, rx = -tilt.y * 8 * f;
        c.style.transform = `translateX(${x.toFixed(1)}px) translateZ(${z.toFixed(1)}px) rotateY(${ry.toFixed(2)}deg) rotateX(${rx.toFixed(2)}deg)`;
        c.style.opacity = ad > 2.6 ? 0 : 1; c.style.filter = ad > 0.05 ? `brightness(${(1 - Math.min(ad, 2) * 0.3).toFixed(2)})` : 'none'; c.style.zIndex = 100 - Math.round(ad * 10);
      },
      onPos(pos, tilt) { roll.style.transform = `translateY(${(-pos * 80).toFixed(1)}px)`; lw.style.transform = `translate(${(-tilt.x * 11).toFixed(1)}px,${(-tilt.y * 6).toFixed(1)}px)`; bgs.forEach((b, j) => { b.style.opacity = Math.max(0, 1 - Math.abs(j - pos)).toFixed(3); }); },
      onIndex(i, first) {
        root.style.setProperty('--spot', APP.spotOf(D[C[i].n].images.poster)); ticks.forEach((t, n) => t.classList.toggle('on', n === i));
        K.setFav(root, false);
        if (first || (APP.shot && !APP.Q.get('anim'))) put(i); else { cover.classList.add('sw'); clearTimeout(root.__sw); root.__sw = setTimeout(() => put(i), 210); if (m) m.blink(); }
        APP.anim(root, 800);
      },
      onOpen(i) { location.hash = '#f-' + (D[C[i].n].type === 'Movie' ? 'movie' : 'series'); },
    });
    ticks.forEach((t, n) => t.addEventListener('click', () => flow.go(n)));
    K.pull(root, { cap: '摇印刷机手柄', t: ['再拉一点 · 出新刊', '松手 · 付印', '付印中……'], pose: (k, f) => ({ wr: 20 + k * 60, wl: 10, eyes: k >= 1 ? '^' : 'o', mouth: k >= 1 ? 'D' : '-', props: [f % 2 ? 'crank1' : 'crank0'] }),
      done() { $('.vol').classList.add('next'); flow.go(0); setTimeout(() => { if (m) { m.setMood('cheer', 1000); m.say('新刊到了：今天新进 4 部，封面换过一轮。', 3200); } }, 500); } });
    root.addEventListener('click', (e) => { const r = e.target.closest('.b-toc .b-li'); if (!r || !m) return; const i = [...root.querySelectorAll('.b-toc .b-li')].indexOf(r), c = K.CW[i]; root.querySelectorAll('.b-toc .b-li').forEach((x) => x.classList.toggle('cur', x === r)); m.flap(0.6); m.say(`《${c.t}》${c.ser ? c.ep : ''}给你翻好了，${c.pct ? '接着上次的 ' + c.pct + '% 往下看。' : '从头开始。'}`, 2600); });
    root.__flow = flow;
  },
  setState(root, st) {
    const sc = root.querySelector('.scroll'); sc.scrollTop = 0; root.__pullShow(st === 'pull' ? 84 : 0);
    root.classList.remove('tabs-away'); ['first', 'exit', 'offline'].forEach((c) => root.classList.toggle(c, st === c));
    root.__flow.s.set(st === 'mid' ? 2.45 : st === 'hard' ? 4 : START);
    K.setFav(root, st === 'fav');
    if (st === 'toc') K.scrollTo(root, '.b-toc', 62); if (st === 'new') K.scrollTo(root, '#h-new', 62); if (st === 'end') sc.scrollTop = 99999;
    sc.dispatchEvent(new Event('scroll'));
  },
});
APP.PLAYS = [
  ['翻封面', '首页：按住海报流左右拖。期号像里程表一样跟着手指滚，背后的整幅剧照、专色、导语一起换；手指停在海报上，海报朝手指倾，logo 有视差。'],
  ['收藏 = 主编盖章', '点「收藏」或顶栏的爱心：朱红的「藏」字章“咚”地落进预留的印章位，她举起翅膀；再点一次章褪掉。首页、剧集、电影、单集、合集都有。'],
  ['标记已看 = 校对勾', '分集目录每行行尾的小方格：点一下划出一笔红色校对勾，集号被划掉；再点擦掉。'],
  ['下拉刷新 = 付印新刊', '首页顶部往下拖：报头上方拉开一条，她摇印刷机（轮廓占位），拉满松手，VOL. 318 滚成 319。'],
  ['选季 = 翻卷册', '剧集详情点「第 N 季 ▾」：分集目录上方展开一排卷册号（航海王 22 卷），点一卷换一季，目录换页。'],
  ['换集', '分集目录点任意一集：专色标题跳到那一行，续看按钮改成那一集，主编按里她说一句。'],
  ['字母索引', '片库海报墙右缘的字母条：按住上下拖，墙跳到那个拼音首字母，正中浮出大号字母。'],
  ['筛选是真的在筛', '片库点「筛选」「排序」：选项页把海报往下推开而不是盖在上面，勾哪个就按哪个重排。'],
  ['搜索可以打字', '搜索页的大字输入框是真的：试试 航海 / 周星驰 / 2026 / 喜剧。'],
  ['播放器让画面', '点「字幕」「选集」等：画面缩到左边，右侧页边排设置，一个像素都不挡；暂停时她才坐进顶栏的一格。'],
  ['玻璃标签栏', '按住选中的标签拖，透镜抬起、放大文字、松手回弹；往下滚动时标签栏和搜索钮一起沉到屏幕外，往回滚一点就回来。'],
];
})();
