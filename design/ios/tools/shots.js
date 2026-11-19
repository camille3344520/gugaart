// node tools/shots.js [gallery|device|all] [filter]   -> <scratch>/ios-shots/<name>.png
// One headless Chrome over CDP (SwiftShader WebGL2, own user-data-dir under the scratchpad). Gallery shots: index.html?p=…&shot=1 at 402 × 874 (3×)
// and 874 × 402 for the player (+ the two other reference sizes). Device shots: device.html with mobile + touch emulation, real taps / edge swipes.
// Reports JS exceptions, console errors, glass failures (body.nogl) and pictures that did not decode for every page it opens.
const { spawn } = require('child_process'); const fs = require('fs'); const path = require('path');
const ROOT = path.resolve(__dirname, '..');                                   // design/ios
const SCRATCH = process.env.SCRATCH || path.join(require('os').tmpdir(), 'gugu-ios');   // 截图不进仓库
const OUT = path.join(SCRATCH, 'ios-shots'); fs.mkdirSync(OUT, { recursive: true });
const UD = fs.mkdtempSync(path.join(SCRATCH, 'chrome-ios-'));
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const PORT = 9500 + Math.floor(Math.random() * 300);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const QUICK = !!process.env.QUICK;
const SZ = { pro: [402, 874], mini: [375, 812], max: [440, 956] };
const st = (p, list, sz) => list.map((s) => [p + (s ? '-' + s : '') + (sz ? '@' + sz : ''), p, (s ? 'st=' + s : '') + (sz ? '&sz=' + sz : '')]);
const GAL = [
  ...st('home', ['', 'pip', 'toc', 'pull', 'first', 'offline']), ...st('discover', ['']), ...st('libs', ['']), ...st('wall', ['', 'menu', 'az', 'filter']), ...st('sets', ['']), ...st('set', ['']),
  ...st('search', ['empty', 'typing', 'results']), ...st('series', ['', 'ctx', 'eps', 'season']), ...st('episode', ['']), ...st('movie', ['', 'nologo']), ...st('person', ['']),
  ...st('hist', ['']), ...st('favs', ['']), ...st('me', ['', 'server']), ...st('settings', ['', 'about', 'play']), ...st('servers', ['', 'login']), ...st('update', ['', 'tf', 'force']),
  ...st('swipe', ['', 'start', 'almost']),
  ...st('player', ['ctl', 'idle', 'pause', 'sub', 'eps', 'airplay', 'pip', 'ended', 'error', 'lock']),
  ...st('home', [''], 'mini'), ...st('home', [''], 'max'), ...st('wall', [''], 'mini'), ...st('series', [''], 'mini'), ...st('player', ['sub'], 'mini'), ...st('player', ['ctl'], 'max'),
];
(async () => {
  const mode = process.argv[2] || 'all', filter = process.argv[3];
  const chrome = spawn(CHROME, ['--headless=new', `--user-data-dir=${UD}`, `--remote-debugging-port=${PORT}`, '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--hide-scrollbars', '--allow-file-access-from-files', '--disable-gpu-vsync', 'about:blank'], { stdio: 'ignore' });
  console.log('chrome pid', chrome.pid, 'out', OUT);
  const errs = [];
  try {
    let tab; for (let i = 0; i < 80 && !tab; i++) { await sleep(200); try { tab = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find((t) => t.type === 'page'); } catch (e) { /* not up yet */ } }
    const ws = new WebSocket(tab.webSocketDebuggerUrl); await new Promise((r) => { ws.onopen = r; });
    let id = 0; const pend = {};
    ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && pend[d.id]) { pend[d.id](d); delete pend[d.id]; }
      if (d.method === 'Runtime.exceptionThrown') errs.push('EXC ' + (d.params.exceptionDetails.exception && d.params.exceptionDetails.exception.description || d.params.exceptionDetails.text).slice(0, 300));
      if (d.method === 'Log.entryAdded' && d.params.entry.level === 'error') errs.push('LOG ' + (d.params.entry.text || '').slice(0, 200) + ' ' + (d.params.entry.url || '').split('/').pop()); };
    const send = (method, params) => new Promise((res) => { const i = ++id; pend[i] = res; ws.send(JSON.stringify({ id: i, method, params: params || {} })); });
    await send('Runtime.enable'); await send('Log.enable'); await send('Page.enable');
    const page = {
      async metrics(w, h, dsf, mobile) { await send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: dsf, mobile: !!mobile, screenOrientation: w > h ? { type: 'landscapePrimary', angle: 90 } : { type: 'portraitPrimary', angle: 0 } }); await send('Emulation.setTouchEmulationEnabled', { enabled: !!mobile, maxTouchPoints: 5 }); },
      async open(url, w, h, dsf, mobile) { await page.metrics(w, h, dsf, mobile); const n = errs.length; await send('Page.navigate', { url }); let ok = false; for (let i = 0; i < 600 && !ok; i++) { await sleep(150); ok = await page.eval('!!(document.body && document.body.dataset.ready === "1")'); } if (!ok) console.log('NOT READY', url); await sleep(400); return errs.slice(n); },
      async eval(expr) { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true }); if (r.result && r.result.exceptionDetails) console.log('EVAL ERROR', JSON.stringify(r.result.exceptionDetails).slice(0, 300)); return r.result && r.result.result && r.result.result.value; },
      async rect(sel, n) { const r = await page.eval(`(() => { const e = document.querySelectorAll(${JSON.stringify(sel)})[${n || 0}]; if (!e || !e.getClientRects().length) return null; let r = e.getBoundingClientRect(); if (r.bottom > innerHeight - 90 || r.top < 60) { const sc = e.closest('.scroll'); if (sc) { sc.scrollTop += r.top - innerHeight / 2; sc.dispatchEvent(new Event('scroll')); r = e.getBoundingClientRect(); } } return [r.left + r.width / 2, r.top + r.height / 2, 1]; })()`); if (r) await sleep(150); return r; },
      touch: (type, pts) => send('Input.dispatchTouchEvent', { type, touchPoints: pts.map((p) => ({ x: p[0], y: p[1], radiusX: 2, radiusY: 2, force: 1 })) }),
      async tap(x, y) { await page.touch('touchStart', [[x, y]]); await sleep(50); await page.touch('touchEnd', []); },
      async tapSel(sel, n) { const r = await page.rect(sel, n); if (!r) { console.log('NO ELEMENT', sel, n || 0); return false; } await page.tap(r[0], r[1]); return true; },
      async top() { await page.eval(`(() => { const s = document.querySelector('#stack > .sB:not(.hid) .content > .scroll'); if (s) { s.scrollTop = 0; s.dispatchEvent(new Event('scroll')); } })()`); await sleep(600); },   // 往回滚：标签栏回来
      async hold(x, y, ms) { await page.touch('touchStart', [[x, y]]); await sleep(ms); },
      async drag(x0, y0, x1, y1, n, mid) { await page.touch('touchStart', [[x0, y0]]); for (let i = 1; i <= n; i++) { await page.touch('touchMove', [[x0 + (x1 - x0) * i / n, y0 + (y1 - y0) * i / n]]); await sleep(16); if (mid && i === Math.floor(n / 2)) await mid(); } },
      async release() { await page.touch('touchEnd', []); },
      async mouse(type, x, y, b) { await send('Input.dispatchMouseEvent', { type, x, y, button: b === 0 ? 'none' : 'left', buttons: b === 0 ? 0 : 1, clickCount: 1, pointerType: 'mouse' }); },
      async click(x, y) { await page.mouse('mouseMoved', x, y, 0); await page.mouse('mousePressed', x, y); await sleep(40); await page.mouse('mouseReleased', x, y); },
      async shot(name, clip) { const r = await send('Page.captureScreenshot', clip ? { format: 'png', clip: { x: 0, y: 0, width: clip[0], height: clip[1], scale: 1 } } : { format: 'png' }); fs.writeFileSync(`${OUT}/${name}.png`, Buffer.from(r.result.data, 'base64')); console.log('shot', name); },
      async lint(name) { const r = await page.eval(`(() => { const o = []; if (document.body.classList.contains('nogl')) o.push('NOGL'); document.querySelectorAll('pre').forEach((p) => o.push('PRE:' + p.textContent.slice(0, 160))); document.querySelectorAll('img[data-img]').forEach((i) => { if (i.getClientRects().length && !i.naturalWidth) o.push('noimg:' + i.dataset.img); }); const n = window.G2 && G2.boards.filter((b) => b.el.getClientRects().length).map((b) => b.shapeCount).join('/'); return (o.length ? o.slice(0, 6).join(' ') : '') + (n ? ' glass-shapes:' + n : ''); })()`); if (r) console.log('  ', name, r); },
    };
    const dsf = QUICK ? 1 : 3;
    if (mode === 'gallery' || mode === 'all') {
      for (const [name, p, q] of GAL) {
        if (filter && !filter.split(',').some((f) => name.includes(f))) continue;
        const land = p === 'player', sz = (q.match(/sz=(\w+)/) || [])[1] || 'pro', [w, h] = SZ[sz];
        const W = land ? h : w, H = land ? w : h;
        const e = await page.open(`file://${ROOT}/index.html?p=${p}&shot=1${QUICK ? '&dpr=1' : ''}${q ? '&' + q : ''}`, W, H, dsf, false);
        if (e.length) console.log('  ERRORS', name, e.join(' | '));
        await page.lint(name); await page.shot(name, [W, H]);
      }
      if (!filter || filter.includes('_gallery')) { const e = await page.open(`file://${ROOT}/index.html`, 1700, 1050, 1, false); if (e.length) console.log('  ERRORS gallery', e.join(' | ')); await page.lint('_gallery'); await page.shot('_gallery');
        await page.click(60, 160); await sleep(1200); await page.lint('_gallery-mini'); await page.shot('_gallery-mini'); }   // 参考机切成 375 × 812
    }
    if (mode === 'device' || mode === 'all') {
      const dd = QUICK ? 1 : 2, W = 402, H = 874;
      // 竖屏流程：首页 → 目录行 → 剧集详情 → 左边缘右滑（拍一半）→ 回首页 → 再进详情 → 播放 → 转横屏 → 面板 → AirPlay → 返回 → 片库标签 → 海报墙 → 长按 → 我的 → 设置 → 搜索
      const e = await page.open(`file://${ROOT}/device.html?p=home&sat=62&sab=34&shot=1`, W, H, dd, true); if (e.length) console.log('  ERRORS device', e.join(' | '));
      await page.lint('dev-home'); await page.shot('dev-home');
      await page.tapSel('.b-toc .b-li', 1); await sleep(500); await page.lint('dev-series'); await page.shot('dev-series');
      await page.drag(6, 520, 300, 520, 14, async () => { await sleep(60); await page.shot('dev-swipe-mid'); }); await sleep(30); await page.release(); await sleep(600); await page.shot('dev-swipe-done');
      await page.tapSel('.b-toc .b-li', 1); await sleep(500); await page.tapSel('#s-series .b-play .b-btn'); await sleep(500); await page.shot('dev-player-portrait');
      await page.metrics(H, W, dd, true); await sleep(900); await page.lint('dev-player'); await page.shot('dev-player');
      await page.tapSel('#s-player [data-p="sub"]'); await sleep(700); await page.shot('dev-player-sub');
      await page.tapSel('#s-player [data-act="close"]'); await sleep(300); await page.tapSel('#s-player [data-act="airplay"]'); await sleep(400); await page.shot('dev-player-airplay');
      await page.tapSel('#s-player .ctl.top [data-back]'); await sleep(500); await page.metrics(W, H, dd, true); await sleep(900); await page.shot('dev-back-from-player');
      await page.tapSel('#s-series .b-top [data-back]'); await sleep(600); await page.top();
      await page.tapSel('#s-home [data-cell]', 2); await sleep(500); await page.lint('dev-libs'); await page.shot('dev-libs');
      await page.tapSel('#s-libs .l-row', 1); await sleep(500); await page.shot('dev-wall');
      const pc = await page.rect('#s-wall .b-wall .b-po', 1); if (pc) { await page.hold(pc[0], pc[1], 700); await sleep(200); await page.shot('dev-wall-menu'); await page.release(); await sleep(300); }
      await page.top(); await page.tapSel('#s-wall [data-cell]', 3); await sleep(500); await page.shot('dev-me');
      await page.tapSel('#s-me .b-rowl.big', 3); await sleep(500); await page.shot('dev-settings');
      await page.tapSel('#s-settings [data-sec]', 2); await sleep(400); await page.shot('dev-settings-play');
      await page.tapSel('#s-settings [data-back]'); await sleep(300); await page.tapSel('#s-settings [data-back]'); await sleep(500); await page.top();
      await page.tapSel('#s-me .g.ios-fab'); await sleep(500); await page.lint('dev-search'); await page.shot('dev-search');
      console.log('bridge log', await page.eval('JSON.stringify(IOS.log.slice(-12))'));
      console.log('stacks', await page.eval('JSON.stringify(IOS.nav.stacks()) + " tab=" + IOS.nav.tab()'));
      // 横屏直接进播放器 + 各状态；小屏横屏
      const e2 = await page.open(`file://${ROOT}/device.html?p=player&sat=0&sab=21&sal=62&sar=62&shot=1`, H, W, dd, true); if (e2.length) console.log('  ERRORS device-land', e2.join(' | '));
      for (const s of ['ctl', 'eps', 'pip', 'ended']) { await page.eval(`APP.setState('player', ${JSON.stringify(s)})`); await sleep(300); await page.shot('dev-land-' + s); }
      const e3 = await page.open(`file://${ROOT}/device.html?p=player&st=sub&sat=0&sab=21&sal=50&sar=50&shot=1`, 812, 375, dd, true); if (e3.length) console.log('  ERRORS device-mini', e3.join(' | '));
      await page.shot('dev-land-sub@mini');
      const e4 = await page.open(`file://${ROOT}/device.html?p=home&sat=50&sab=34&shot=1`, 375, 812, dd, true); if (e4.length) console.log('  ERRORS device-mini-home', e4.join(' | '));
      await page.lint('dev-home@mini'); await page.shot('dev-home@mini');
      const e5 = await page.open(`file://${ROOT}/device.html?p=wall&sat=62&sab=34&shot=1`, 440, 956, dd, true); if (e5.length) console.log('  ERRORS device-max', e5.join(' | '));
      await page.shot('dev-wall@max');
    }
    console.log('total console/exception entries:', errs.length); errs.slice(0, 20).forEach((x) => console.log('  ', x));
    ws.close();
  } catch (e) { console.log('FAILED', e && e.stack || e); }
  chrome.kill(); await sleep(300); try { fs.rmSync(UD, { recursive: true, force: true }); } catch (e) { /* chrome may still hold a file */ }
})();
