/* 咕嘎画报（B1）· iOS —— 画廊启动（index.html）。左栏目录 + 全部画板；每块画板套一台 iPhone 外框；参考机 402 × 874 / 375 × 812 / 440 × 956 一键切换。
 * URL：index.html[?sz=pro|mini|max]        画廊
 *      index.html?p=<界面>&shot=1[&st=<状态>][&sz=…][&scroll=<px>][&bare=1]   单看一屏（截图），bare = 不画灵动岛 / home 条 */
(function () {
'use strict';
const APP = window.APP, S = APP.S, IOS = window.IOS, Q = APP.Q;
IOS.mode = 'gallery';
const SIZES = {
  pro: { w: 402, h: 874, sat: 62, sab: 34, sbc: 29, t: '16 / 17 Pro', d: '402 × 874' },
  mini: { w: 375, h: 812, sat: 50, sab: 34, sbc: 24, notch: 1, t: '小屏 · 13 mini', d: '375 × 812' },
  max: { w: 440, h: 956, sat: 62, sab: 34, sbc: 29, t: '大屏 · Pro Max', d: '440 × 956' },
};
const varsOf = (k, land) => { const z = SIZES[k]; return land ? `--vw:${z.h}px;--vh:${z.w}px;--sat:0px;--sab:21px;--sal:${z.sat}px;--sar:${z.sat}px;--sbc:0px` : `--vw:${z.w}px;--vh:${z.h}px;--sat:${z.sat}px;--sab:${z.sab}px;--sal:0px;--sar:0px;--sbc:${z.sbc}px`; };
IOS.PLAYS = [
  ['左边缘右滑返回', '任何被推入的页：从左边缘往右拖，页跟着手指走，底下那一页从 −30 % 跟回来。「iOS」一组里有进行到 12 / 40 / 80 % 三格。真机模式里是真的能拖的。'],
  ['长按 = 纸片菜单', '海报墙的海报、目录里的一行：按住半秒（触感 medium），一张米白纸片在那一行 / 那一排下面推开——播放、收藏（印章位）、标记已看（校对格）、详情、管理 › 删除（原地变红边二次确认）。不盖内容。'],
  ['触感', '落章 success、擦章 light、校对勾 light、翻期号 selection、换标签 selection、下拉拉满 medium、付印 success、长按 medium。走原生桥 postMessage({type:"haptic"})，demo 里只记日志。'],
  ['AirPlay 代替投屏', '播放器顶栏右半边：AirPlay（系统路由器选电视）、画中画。投到电视后画面拿掉，换成纸面说明 + 结束 AirPlay；锁屏 / 控制中心的「正在播放」由系统接管。'],
  ['号外 = App Store', 'iOS 不能自己下载安装：号外的按钮通向 App Store / TestFlight，没有下载进度，强制更新也没有「退出应用」。'],
  ['灵动岛 / 圆角 / home 条', '整幅剧照铺到灵动岛后面；返回 / 收藏 / 更多两个上角的方框钮落在岛的两侧；标签栏浮在 home 条上方 34；播放器画面铺满，控件让出左右 62、底部 21。'],
  ['参考机切换', '左栏「参考机」：402 × 874（16 / 17 Pro）、375 × 812（小屏，刘海）、440 × 956（Pro Max）。列宽、海报墙、封面区按视口重排，字号和页边不变。'],
  ['翻封面 / 盖章 / 校对勾 / 下拉付印 / 选季 / 筛选 / 搜索 / 玻璃标签栏', '全部沿用安卓手机 demo，鼠标 = 手指。'],
];
let sz = SIZES[Q.get('sz')] ? Q.get('sz') : 'pro';
const applySize = (el, k) => { const s = S[el.id.slice(2)], z = SIZES[k]; el.setAttribute('style', varsOf(k, !!s.land)); el.classList.toggle('notch', !!z.notch && !s.land); el.classList.toggle('mini', k === 'mini'); };

APP.boot = async function () {
  const ids = APP.ORDER;
  await IOS.prepare();
  const only = APP.shot ? [APP.shot] : ids;
  IOS.build(only);
  const dpr = APP.shot ? (+Q.get('dpr') || 3) : 2;
  const main = document.getElementById('main');
  let fit = () => {};
  if (APP.shot) {
    document.body.classList.add('shot'); if (Q.get('anim')) document.body.classList.add('anim'); const a = document.querySelector('aside'); if (a) a.remove();
    main.innerHTML = IOS.mk(APP.shot, dpr, !Q.get('bare')); applySize(main.firstElementChild, sz);
  } else {
    const groups = []; ids.forEach((id) => { let g = groups.find((x) => x.n === S[id].group); if (!g) groups.push(g = { n: S[id].group, ids: [] }); g.ids.push(id); });
    document.getElementById('toc').innerHTML = groups.map((g, i) => `<div class="tg"><em>${APP.pad(i + 1)}</em><b>${g.n}</b>` + g.ids.map((id) => `<a href="#f-${id}">${S[id].title}<i>${(S[id].states || [1]).length}</i></a>`).join('') + `</div>`).join('') +
      `<div class="tg"><em>${APP.pad(groups.length + 1)}</em><b>系统态</b><a href="#f-home" data-go="home:offline">网络断开</a><a href="#f-home" data-go="home:pip">画中画浮窗</a><a href="#f-player" data-go="player:airplay">AirPlay</a><a href="#f-update" data-go="update:force">必须更新</a></div>`;
    document.getElementById('plays').innerHTML = IOS.PLAYS.map((p) => `<li><b>${p[0]}</b>${p[1]}</li>`).join('');
    document.getElementById('sz').innerHTML = Object.keys(SIZES).map((k) => `<a data-sz="${k}" class="${k === sz ? 'on' : ''}"><i>${k === 'pro' ? '◉' : k === 'mini' ? '▭' : '▯'}</i><b>${SIZES[k].d}</b><span>${SIZES[k].t}</span></a>`).join('');
    const IOSST = { pip: 1, ctx: 1, menu: 1, airplay: 1, tf: 1, force: 1 };
    const fig = (id) => { const s = S[id];
      return `<figure class="${s.land ? 'land' : 'port'}" id="f-${id}"><figcaption><b>${s.title}</b><span>${s.group === 'iOS' ? '<i>iOS</i>' : ''}${s.note || ''}</span></figcaption>` +
        `<div class="fit${sz === 'mini' ? ' mini' : ''}">${IOS.mk(id, dpr, true)}</div>` +
        (s.states ? `<nav class="states" data-id="${id}">${s.states.map((st, i) => `<a data-st="${st[0]}" class="${i === 0 ? 'on' : ''}${IOSST[st[0]] || (id === 'update' || id === 'swipe') ? ' ios' : ''}">${st[1]}</a>`).join('')}</nav>` : '') + `</figure>`; };
    main.innerHTML = groups.map((g, i) => `<h3 class="grp"><em>${APP.pad(i + 1)}</em>${g.n}${g.n === 'iOS' ? '<small>只有 iOS 才有的状态；其余画板里的 iOS 状态用红框按钮标出</small>' : ''}</h3><div class="row">${g.ids.map(fig).join('')}</div>`).join('');
    main.querySelectorAll('.fit > .sB').forEach((el) => applySize(el, sz));
    fit = () => document.querySelectorAll('.fit').forEach((f) => { const el = f.firstElementChild, w = el.offsetWidth, h = el.offsetHeight, s = Math.min(1, (main.clientWidth - 8) / w); f.style.width = w * s + 'px'; f.style.height = h * s + 'px'; el.style.transform = `scale(${s})`; });
    fit(); addEventListener('resize', () => { fit(); requestAnimationFrame(() => window.G2.renderAll()); });
  }
  const setState = await IOS.initAll(main, only);
  document.querySelectorAll('.states').forEach((n) => n.addEventListener('click', (e) => { const a = e.target.closest('a'); if (!a) return; n.querySelectorAll('a').forEach((x) => x.classList.toggle('on', x === a)); setState(n.dataset.id, a.dataset.st); }));
  document.querySelectorAll('[data-go]').forEach((a) => a.addEventListener('click', () => { const [id, st] = a.dataset.go.split(':'); const n = document.querySelector(`.states[data-id="${id}"] a[data-st="${st}"]`); if (n) n.click(); }));
  const szEl = document.getElementById('sz');
  if (szEl) szEl.addEventListener('click', (e) => { const a = e.target.closest('a[data-sz]'); if (!a || a.dataset.sz === sz) return; sz = a.dataset.sz; szEl.querySelectorAll('a').forEach((x) => x.classList.toggle('on', x === a)); history.replaceState(null, '', '?sz=' + sz);
    document.querySelectorAll('.fit').forEach((f) => f.classList.toggle('mini', sz === 'mini'));
    main.querySelectorAll('.fit > .sB').forEach((el) => { applySize(el, sz); IOS.relayout(el); }); fit();
    window.G2.boards.forEach((B) => IOS.resizeBoard(B)); requestAnimationFrame(() => window.G2.renderAll()); });
  if (APP.shot) { if (APP.st) setState(APP.shot, APP.st); const sc = Q.get('scroll'); if (sc) { const el = main.querySelector('.scroll'); if (el) { el.scrollTop = +sc; el.dispatchEvent(new Event('scroll')); } } }
  IOS.ready();
};
})();
