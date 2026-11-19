/* 咕嘎画报（B1）· iOS —— 真机 / 模拟器模式（device.html）。一个页面里原地换界面：
 *   标签栏切页（每个标签各自一叠）、点海报进详情、返回钮 / 左边缘右滑返回、进播放器转横屏再退出、搜索、设置、服务器、号外。
 * URL：device.html?p=<界面>[&st=<状态>][&sat=&sab=&sal=&sar=（没有 env 时手填安全区）][&shot=1（定格，截图用）]
 * 原生桥（可选）：orientation / haptic / chrome；没有桥时照常工作（播放器自己转 90°）。全部用 pointer 事件，不依赖 hover。 */
(function () {
'use strict';
const APP = window.APP, K = window.K, S = APP.S, IOS = window.IOS, Q = APP.Q, D = APP.D;
IOS.mode = 'device'; IOS.log = []; document.body.classList.add('dev');
const TAB_OF = { home: 'home', discover: 'discover', libs: 'libs', wall: 'libs', sets: 'libs', set: 'libs', search: 'search', series: 'home', episode: 'home', movie: 'home', person: 'home', player: 'home', hist: 'me', favs: 'me', me: 'me', settings: 'me', servers: 'me', update: 'me' };
const TABS = ['home', 'discover', 'libs', 'me'];
const stacks = { home: ['home'], discover: ['discover'], libs: ['libs'], me: ['me'], search: ['search'] };
let tab = 'home', busy = false;
const $s = (id) => document.getElementById('s-' + id);
const top = () => stacks[tab][stacks[tab].length - 1];
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const raf2 = () => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
const still = () => IOS.rm || !!APP.shot;

// ------------------------------------------------------------------ 视口与安全区 → :root 变量（env() 用探针量；URL 可覆盖）
const probe = document.getElementById('probe'), root = document.documentElement;
function measure() {
  const vw = innerWidth, vh = innerHeight, land = vw > vh;
  const env = { sat: probe.offsetTop, sab: probe.offsetHeight, sal: probe.offsetLeft, sar: probe.offsetWidth };
  const g = (k) => Q.get(k) !== null ? +Q.get(k) : env[k];
  const v = { sat: g('sat'), sab: g('sab'), sal: g('sal'), sar: g('sar') };
  if (Q.get('sat') !== null && land && Q.get('sal') === null) { v.sal = v.sar = v.sat; v.sat = 0; v.sab = Math.round(v.sab * 0.62); }   // 手填竖屏安全区、转横屏：岛到两侧
  root.style.setProperty('--vw', vw + 'px'); root.style.setProperty('--vh', vh + 'px');
  Object.keys(v).forEach((k) => root.style.setProperty('--' + k, v[k] + 'px'));
  IOS.vp = Object.assign({ vw, vh, land }, v);
}
function layoutPlayer() {
  const el = $s('player'); if (!el) return; const vp = IOS.vp;
  el.classList.toggle('rot', !vp.land);
  el.style.cssText = vp.land ? '' : `--vw:${vp.vh}px;--vh:${vp.vw}px;--sal:${vp.sat}px;--sar:${vp.sab}px;--sab:0px;--sat:0px`;
  IOS.relayout(el);
}
function relayoutAll() { measure(); layoutPlayer(); document.querySelectorAll('#stack > .sB:not(.hid)').forEach((el) => { if (el.id !== 's-player') IOS.relayout(el); if (el.__board) IOS.resizeBoard(el.__board); }); window.G2.renderAll(); }
let rt = 0; addEventListener('resize', () => { clearTimeout(rt); rt = setTimeout(relayoutAll, 60); });
if (window.visualViewport) visualViewport.addEventListener('resize', () => { const kb = innerHeight - visualViewport.height; root.style.setProperty('--kb', Math.max(0, kb) + 'px'); document.body.classList.toggle('kb', kb > 80); });

// ------------------------------------------------------------------ 叠（每个标签一叠）
function wake(el) { el.classList.remove('hid'); const B = el.__board; if (B) { IOS.resizeBoard(B); B.sceneDirty = true; B.kick(400); } if (el.id !== 's-player') IOS.relayout(el); }
function resetTab(el) { const B = el.__board, g = el.querySelector('.g[data-ind]'); if (!B || !g) return; const st = B.ind.get(g); if (st && st.sel !== +g.dataset.sel) { st.sel = +g.dataset.sel; B.sceneDirty = true; if (!el.classList.contains('hid')) B.kick(500); } }
function edge(el, on) { const e = el.querySelector(':scope > .ios-edge'); if (on && !e) el.insertAdjacentHTML('beforeend', '<div class="ios-edge"></div>'); if (!on && e) e.remove(); }
function enterPlayer() { IOS.bridge({ type: 'orientation', value: 'landscape' }); IOS.bridge({ type: 'chrome', statusBarHidden: true, homeIndicatorHidden: true }); layoutPlayer(); }
function leavePlayer() { IOS.bridge({ type: 'orientation', value: 'portrait' }); IOS.bridge({ type: 'chrome', statusBarHidden: false, homeIndicatorHidden: false }); }

async function push(id, before) {
  if (busy) return; const from = top(); if (id === from) return; busy = true;
  const el = $s(id), fromEl = $s(from); if (before) before(el);
  stacks[tab].push(id);
  wake(el); el.style.zIndex = 6; fromEl.style.zIndex = 5;
  if (id === 'player') {                       // 进播放器：转屏，淡入（不滑）
    if (!still()) { el.classList.add('fadeout'); await raf2(); el.classList.add('anim'); el.classList.remove('fadeout'); await sleep(340); el.classList.remove('anim'); }
    fromEl.classList.add('hid'); enterPlayer(); busy = false; return;
  }
  el.classList.add('top-swipe'); fromEl.classList.add('under-swipe');
  if (still()) { el.style.setProperty('--p', 0); fromEl.style.setProperty('--p', 0); }
  else { el.style.setProperty('--p', 1); fromEl.style.setProperty('--p', 1); await raf2(); el.classList.add('anim'); fromEl.classList.add('anim'); el.style.setProperty('--p', 0); fromEl.style.setProperty('--p', 0); await sleep(400); }
  el.classList.remove('anim', 'top-swipe'); fromEl.classList.remove('anim', 'under-swipe'); fromEl.classList.add('hid'); edge(el, true); busy = false;
}
function popNow() {
  const id = stacks[tab].pop(), el = $s(id); el.classList.add('hid'); el.classList.remove('anim', 'top-swipe'); el.style.removeProperty('--p'); edge(el, false); IOS.closeCtx(el);
  const t = $s(top()); t.classList.remove('under-swipe', 'anim'); t.style.removeProperty('--p'); t.style.zIndex = 5; wake(t); edge(t, stacks[tab].length > 1);
  if (id === 'player') { leavePlayer(); }
}
async function pop() {
  if (busy || stacks[tab].length < 2) return; busy = true;
  const el = $s(top()), under = $s(stacks[tab][stacks[tab].length - 2]);
  if (el.id === 's-player') { if (!still()) { el.classList.add('anim'); el.classList.add('fadeout'); wake(under); under.style.zIndex = 5; el.style.zIndex = 6; await sleep(320); el.classList.remove('anim', 'fadeout'); } popNow(); busy = false; return; }
  wake(under); under.style.zIndex = 5; el.style.zIndex = 6;
  el.classList.add('top-swipe'); under.classList.add('under-swipe'); el.style.setProperty('--p', 0); under.style.setProperty('--p', 0);
  if (!still()) { await raf2(); el.classList.add('anim'); under.classList.add('anim'); el.style.setProperty('--p', 1); under.style.setProperty('--p', 1); await sleep(400); }
  popNow(); busy = false;
}
function switchTab(t) {
  if (t === tab) { const st = stacks[t]; if (st.length > 1) { while (st.length > 1) { const id = st.pop(), el = $s(id); el.classList.add('hid'); edge(el, false); if (id === 'player') leavePlayer(); } wake($s(st[0])); } else { const sc = $s(st[0]).querySelector('.scroll'); if (sc) sc.scrollTo({ top: 0, behavior: still() ? 'auto' : 'smooth' }); } return; }
  const from = $s(top()); tab = t; const to = $s(top());
  from.classList.add('hid'); resetTab(from); wake(to); resetTab(to); to.style.zIndex = 5; edge(to, stacks[tab].length > 1);
}
IOS.nav = { push, pop, switchTab, stacks: () => stacks, tab: () => tab, top };

// ------------------------------------------------------------------ 打开一部片 / 一个库 / 一个人
const KNOWN_PEOPLE = ['周星驰', '罗云熙', '宋轶', '陈瑶', '丞磊'];
IOS.open = (name) => {
  const d = D[name], t = d || APP.byName(name);
  if (d) return push(d.type === 'Movie' ? 'movie' : 'series', (el) => el.__show(name));
  if (t) { const scr = t.type === 'Movie' ? 'movie' : 'series', sub = scr === 'movie' ? '盗梦空间' : '颜心记';
    return push(scr, (el) => { el.__show(sub); const m = APP.mascotOf(el); if (m) setTimeout(() => m.say(`数据包里没有《${name}》的详情稿，先拿《${sub}》的版式顶一下。`, 3200), 500); }); }
  return push('episode', (el) => el.__show(''));
};
IOS.openLib = (lib) => { if (tab !== 'libs') switchTab('libs'); if (lib === '合集') return push('sets'); const i = ['电影', '电视剧', '动漫', 'emby302'].indexOf(lib); return push('wall', (el) => { if (i >= 0 && el.__lib) { el.__lib.S.tab = i; el.__lib.S.genre = el.__lib.S.era = el.__lib.S.state = el.__lib.S.q = -1; el.__lib.draw(); el.querySelector('.scroll').scrollTop = 0; } }); };
IOS.openPerson = (name) => { if (!KNOWN_PEOPLE.includes(name)) return false; push('person', (el) => el.__show(name)); return true; };
IOS.play = () => push('player');
const SETKEY = (name) => /福音战士新剧场版/.test(name) ? 'evA' : /Fight Back/.test(name) ? 'school' : /Avengers/.test(name) ? 'avengers' : 'evA';
const titleOf = (el) => { const b = el.querySelector('b'); return b ? b.textContent.trim() : ''; };

// ------------------------------------------------------------------ 点击 → 路由（捕获：返回；冒泡：各页的链接，在各页自己的处理之后）
function wire(main) {
  main.addEventListener('click', (e) => {
    const scr = e.target.closest('#stack > .sB'); if (!scr) return; const id = scr.id.slice(2), T = e.target;
    const back = T.closest('[data-back], .mback, .p-end .x'); if (!back) return;
    if (id === 'settings' && scr.__k) return;                     // 设置的分区页：返回 = 回设置首页，由设置自己处理
    if (id === 'player' && scr.classList.contains('ended')) { /* 片尾的返回 */ }
    e.stopPropagation(); e.preventDefault(); pop();
  }, true);
  main.addEventListener('click', (e) => {
    const scr = e.target.closest('#stack > .sB'); if (!scr) return; const id = scr.id.slice(2), T = e.target;
    if (T.closest('.ios-ctx, .chrome, .ios-edge')) return;
    if (IOS.lpAt && performance.now() - IOS.lpAt < 1500 && !T.closest('.ios-ctx')) return;   // 长按松手后系统补发的 click 不当点按
    if (T.closest('.g')) return;
    const nw = T.closest('.b-nw'); if (nw) return IOS.open(titleOf(nw));
    const po = T.closest('.b-po'); if (po) return IOS.open(titleOf(po));
    const who = T.closest('.b-who'); if (who) { IOS.openPerson(titleOf(who)); return; }
    const cast = T.closest('.b-cast a'); if (cast) { IOS.openPerson(titleOf(cast)); return; }
    if (id === 'home') {
      const li = T.closest('.b-toc .b-li'); if (li) { const i = [...scr.querySelectorAll('.b-toc .b-li')].indexOf(li), r = K.CW[i]; return r ? IOS.open(r.t) : 0; }
      if (T.closest('.b-toc .b-all')) return push('hist');
      const c = T.closest('.b-cell'); if (c) return IOS.openLib(titleOf(c));
      if (T.closest('.b-cta .b-btn')) return IOS.play();
      return;
    }
    if (id === 'discover') { if (T.closest('.d-pl')) return push('sets'); const dp = T.closest('.d-dice'); if (dp && !T.closest('.dice')) return IOS.open(scr.querySelector('.dt').textContent); const li = T.closest('.b-li'); if (li) return IOS.open(titleOf(li)); if (T.closest('.d-one')) return IOS.open('航海王'); return; }
    if (id === 'libs') { const r = T.closest('.l-row'); if (r) return IOS.openLib(titleOf(r)); return; }
    if (id === 'sets') { const c = T.closest('.s-card'); if (c) return push('set', (el) => el.__show(SETKEY(titleOf(c)))); return; }
    if (id === 'set') { const li = T.closest('.b-li'); if (li && !T.closest('.mk')) return IOS.open(titleOf(li)); if (T.closest('.b-play .b-btn')) return IOS.play(); return; }
    if (id === 'search') { const best = T.closest('.b-best'); if (best) { const k = best.querySelector('img').dataset.img, t = APP.POOL.find((x) => x.images.poster === k); return t ? IOS.open(t.name) : 0; } const li = T.closest('.b-li'); if (li) return li.classList.contains('sm') ? push('episode', (el) => el.__show('')) : IOS.open(li.querySelector('b').textContent); return; }
    if (id === 'series') { const li = T.closest('.b-eps .b-li'); if (li) { if (T.closest('.th')) return push('episode', (el) => el.__show('')); return; } if (T.closest('.b-play .b-btn')) return IOS.play(); return; }
    if (id === 'episode') { if (T.closest('.b-play .b-btn')) return IOS.play(); if (T.closest('.e-back')) return stacks[tab][stacks[tab].length - 2] === 'series' ? pop() : push('series', (el) => el.__show('颜心记')); const li = T.closest('.b-li'); if (li) { scr.querySelector('.scroll').scrollTop = 0; return; } return; }
    if (id === 'movie') { if (T.closest('.b-play .b-btn')) return IOS.play(); return; }
    if (id === 'person') { const li = T.closest('.b-li, .b-best'); if (li) return IOS.open(titleOf(li) || ''); return; }
    if (id === 'hist' || id === 'favs') { const li = T.closest('.b-li'); if (li) return li.classList.contains('sm') && id === 'favs' ? push('episode', (el) => el.__show('')) : IOS.open(titleOf(li)); return; }
    if (id === 'me') {
      const big = T.closest('.b-rowl.big'); if (big) { const n = big.querySelector('em').textContent; if (n === '01') return push('hist'); if (n === '02') return push('favs'); if (n === '04') return push('settings', (el) => el.__show('')); if (n === '05') return push('settings', (el) => el.__show('about')); return; }
      if (T.closest('.b-shead a')) return push('settings', (el) => el.__show(''));
      const st = T.closest('.b-stats a'); if (st) return push(st.textContent.includes('收藏') ? 'favs' : 'hist');
      const strip = T.closest('.m-strip a'); if (strip) return IOS.open(titleOf(strip));
      const add = T.closest('.b-swp .add'); if (add) return push('servers', (el) => el.__show(add.closest('.sw-server') ? 'add' : 'login'));
      return;
    }
    if (id === 'settings') { const r = T.closest('.b-opt'); if (!r) return; const t = r.textContent; if (/检查更新/.test(t) && !/有新版/.test(t)) return push('update', (el) => el.__show('')); if (/添加账号|重新登录|切换/.test(t) && /账号|登录|切换/.test(r.querySelector('b').textContent)) return push('servers', (el) => el.__show('login')); return; }
    if (id === 'servers') {
      const sv = T.closest('.b-rowl.sv'); if (sv) { scr.__show('login'); scr.__st = 'login'; return; }
      const add = T.closest('.sv-add'); if (add) { const st = scr.__st || ''; const next = { '': 'add', add: 'found', found: 'login', login: 'busy', fail: 'busy' }[st]; if (next) { scr.__st = next; scr.__show(next); scr.querySelector('.scroll').scrollTop = 0; if (next === 'busy') setTimeout(() => { scr.__st = ''; scr.__show(''); pop(); }, 900); } return; }
      return;
    }
    if (id === 'update') { if (T.closest('.b-btn, .b-link')) return pop(); return; }
    if (id === 'player') { if (T.closest('.p-end .e2 a') && /回详情/.test(T.closest('a').textContent)) return pop(); return; }
  });
  main.addEventListener('select', (e) => { const g = e.target.closest && e.target.closest('.g[data-ind]'); if (!g) return; const t = TABS[e.detail]; if (t) switchTab(t); });
  main.addEventListener('click', (e) => { const f = e.target.closest('.g.ios-fab'); if (!f) return; if (tab === 'search') { const i = $s('search').querySelector('input'); if (i) i.focus(); } else switchTab('search'); });
  // 首页封面点正中那张：s-home.js 写的是 location.hash = '#f-series|movie'，这里接住换成推入
  addEventListener('hashchange', () => { const m = /^#f-(series|movie)$/.exec(location.hash); history.replaceState(null, '', location.pathname + location.search); if (!m) return; const key = ($s('home').querySelector('.b-logo img') || {}).dataset; const d = key && Object.values(D).find((x) => x.images.logo === key.img); IOS.open(d ? d.name : (m[1] === 'movie' ? '盗梦空间' : '颜心记')); });
  // 左边缘右滑返回（交互式；完成 = 过 1/3 或甩得快）
  main.addEventListener('pointerdown', (e) => {
    const ed = e.target.closest('.ios-edge'); if (!ed || busy || stacks[tab].length < 2) return;
    const el = $s(top()), under = $s(stacks[tab][stacks[tab].length - 2]), vw = innerWidth; let x0 = e.clientX, last = x0, lastT = e.timeStamp, v = 0, moved = false;
    try { ed.setPointerCapture(e.pointerId); } catch (x) { /* ignore */ } e.preventDefault();
    wake(under); under.style.zIndex = 5; el.style.zIndex = 6; busy = true;
    el.classList.add('top-swipe'); under.classList.add('under-swipe'); el.style.setProperty('--p', 0); under.style.setProperty('--p', 0);
    const move = (ev) => { const dx = Math.max(0, ev.clientX - x0); if (dx > 4) moved = true; v = 0.7 * v + 0.3 * (ev.clientX - last) / Math.max(1, ev.timeStamp - lastT); last = ev.clientX; lastT = ev.timeStamp; const p = Math.min(1, dx / vw); el.style.setProperty('--p', p); under.style.setProperty('--p', p); if (under.__board) { under.__board.sceneDirty = true; under.__board.kick(); } };
    const finish = (target) => { ed.removeEventListener('pointermove', move); ed.removeEventListener('pointerup', up); ed.removeEventListener('pointercancel', up);
      const end = () => { el.classList.remove('anim', 'top-swipe'); under.classList.remove('anim', 'under-swipe'); if (target === 1) popNow(); else { under.classList.add('hid'); el.style.removeProperty('--p'); under.style.removeProperty('--p'); } busy = false; };
      if (still()) { el.style.setProperty('--p', target); under.style.setProperty('--p', target); end(); return; }
      el.classList.add('anim'); under.classList.add('anim'); el.style.setProperty('--p', target); under.style.setProperty('--p', target); setTimeout(end, 320); };
    const up = (ev) => { const p = Math.min(1, Math.max(0, ev.clientX - x0) / vw);
      if (!moved) { finish(0); ed.style.pointerEvents = 'none'; const t = document.elementFromPoint(ev.clientX, ev.clientY); ed.style.pointerEvents = ''; if (t) t.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: ev.clientX, clientY: ev.clientY })); return; }
      finish(p > 0.34 || v > 0.6 ? 1 : 0); };
    ed.addEventListener('pointermove', move); ed.addEventListener('pointerup', up); ed.addEventListener('pointercancel', up);
  });
  // 触摸被系统滚动接走时 demo 里的指针状态要收尾（pointercancel → 当 pointerup）
  addEventListener('pointercancel', (e) => { window.dispatchEvent(new PointerEvent('pointerup', { clientX: e.clientX, clientY: e.clientY, pointerId: e.pointerId, pointerType: e.pointerType, bubbles: true })); });
  addEventListener('contextmenu', (e) => e.preventDefault());
  // 首页下拉付印：iOS 上用 touch 事件接管（滚到顶再往下拉时 preventDefault，系统就不回弹，由 kit.js 的 __pull 画下拉条）
  const home = $s('home'), sc = home && home.querySelector('.scroll');
  if (sc) { let t0 = null, pulling = false;
    sc.addEventListener('touchstart', (e) => { t0 = { y: e.touches[0].clientY, top: sc.scrollTop }; pulling = false; }, { passive: true });
    sc.addEventListener('touchmove', (e) => { if (!t0 || !sc.__pull) return; const dy = e.touches[0].clientY - t0.y; if (sc.scrollTop <= 0 && dy > 0 && (pulling || dy > 6)) { pulling = true; if (e.cancelable) e.preventDefault(); sc.__pull(dy); } else if (pulling) { pulling = false; sc.__pull(0); } }, { passive: false });
    const end = () => { if (pulling) { pulling = false; sc.__pull(null); } t0 = null; };
    sc.addEventListener('touchend', end); sc.addEventListener('touchcancel', end); }
}

// ------------------------------------------------------------------ 启动
APP.boot = async function () {
  measure();
  await IOS.prepare();
  const ids = APP.ORDER.filter((id) => id !== 'swipe');
  IOS.build(ids);
  const dpr = Math.min(2, window.devicePixelRatio || 1);   // 玻璃最多 2 倍采样（WebKit 的画布内存）；真机实现用上游引擎，没有这一条
  const main = document.getElementById('stack');
  main.innerHTML = ids.map((id) => IOS.mk(id, dpr, false)).join('');
  main.querySelectorAll('.sB').forEach((el) => el.insertAdjacentHTML('beforeend', '<div class="ios-veil"></div>'));
  let p = Q.get('p') || 'home'; if (!S[p] || p === 'swipe') p = 'home';
  tab = TAB_OF[p] || 'home'; if (p !== tab) { if (p === 'player') stacks[tab].push('series'); stacks[tab].push(p); }
  if (Q.get('shot')) { APP.shot = p; document.body.classList.add('shot'); }
  layoutPlayer();
  const setState = await IOS.initAll(main, ids);
  if (Q.get('st')) setState(p, Q.get('st'));
  ids.forEach((id) => { const el = $s(id); if (id !== p) el.classList.add('hid'); else { el.style.zIndex = 5; wake(el); edge(el, stacks[tab].length > 1); } });
  if (p === 'player') enterPlayer();
  wire(main);
  IOS.ready();
  // WKWebView 刚加载时 env(safe-area-inset-*) 还是 0，稍后才变成真值，而且安全区变化不触发 resize（模拟器里实测：
  // 报头钻到状态栏下、标签栏压在 home 条上）。启动后再量几次，探针尺寸一变也重量；有变化就整体重排。手填参数的不受影响。
  const reprobe = () => { const vp = IOS.vp || {}, e = { sat: probe.offsetTop, sab: probe.offsetHeight, sal: probe.offsetLeft, sar: probe.offsetWidth };
    if (Object.keys(e).some((k) => Q.get(k) === null && e[k] !== vp[k])) relayoutAll(); };
  [250, 800, 2000].forEach((t) => setTimeout(reprobe, t));
  if (window.ResizeObserver) new ResizeObserver(reprobe).observe(probe);
};
})();
