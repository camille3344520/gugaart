// node tools/shots.js [filter[,filter]]   -> shots/<name>.png at 1440 x 3168 (portrait) / 3168 x 1440 (player)
// One headless Chrome driven over CDP; SwiftShader WebGL; its own user-data-dir. Waits for body[data-ready] (pictures decoded, glass engine up) before every shot.
const { spawn } = require('child_process'); const fs = require('fs'); const path = require('path'); const os = require('os');
const ROOT = path.resolve(__dirname, '..').split(path.sep).join('/');
const UD = fs.mkdtempSync(path.join(os.tmpdir(), 'b1phone-chrome-'));
const PORT = 9400 + Math.floor(Math.random() * 300);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const LIST = require('./shotlist.js');
const QUICK = !!process.env.QUICK;   // QUICK=1: 1x pictures into shots/_quick (fast layout pass, the glass at 1x)
(async () => {
  const filter = process.argv[2], exact = process.argv[3] === 'exact';
  const chrome = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', `--user-data-dir=${UD}`, `--remote-debugging-port=${PORT}`, '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--hide-scrollbars', '--allow-file-access-from-files', 'about:blank'], { stdio: 'ignore' });
  try {
    let tab; for (let i = 0; i < 60 && !tab; i++) { await sleep(200); try { tab = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find((t) => t.type === 'page'); } catch (e) { /* not up yet */ } }
    const ws = new WebSocket(tab.webSocketDebuggerUrl); await new Promise((r) => { ws.onopen = r; });
    let id = 0; const pend = {};
    ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && pend[d.id]) { pend[d.id](d); delete pend[d.id]; } };
    const send = (method, params) => new Promise((res) => { const i = ++id; pend[i] = res; ws.send(JSON.stringify({ id: i, method, params: params || {} })); });
    const page = {
      async open(q, w, h, dsf) { await send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: dsf || (QUICK ? 1 : 3.5), mobile: false }); await send('Page.navigate', { url: `file:///${ROOT}/index.html?${q}` }); let ok = false; for (let i = 0; i < 400 && !ok; i++) { await sleep(150); ok = await page.eval('!!(document.body && document.body.dataset.ready === "1")'); } if (!ok) console.log('NOT READY', q); await sleep(450); },
      async eval(expr) { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true }); if (r.result && r.result.exceptionDetails) console.log('EVAL ERROR', JSON.stringify(r.result.exceptionDetails).slice(0, 300)); return r.result && r.result.result && r.result.result.value; },
      mouse: (type, x, y, b) => send('Input.dispatchMouseEvent', { type, x, y, button: b === 0 ? 'none' : 'left', buttons: b === 0 ? 0 : 1, clickCount: 1, pointerType: 'mouse' }),
      async down(x, y) { await page.mouse('mouseMoved', x, y, 0); await page.mouse('mousePressed', x, y); },
      async move(x, y) { await page.mouse('mouseMoved', x, y); },
      async hover(x, y) { await page.mouse('mouseMoved', x, y, 0); },
      async up(x, y) { await page.mouse('mouseReleased', x, y); },
      async click(x, y) { await page.down(x, y); await sleep(40); await page.up(x, y); },
      async dragTo(x0, y0, x1, y1, n) { await page.down(x0, y0); for (let i = 1; i <= (n || 12); i++) { await page.move(x0 + (x1 - x0) * i / (n || 12), y0 + (y1 - y0) * i / (n || 12)); await sleep(16); } },
      wait: sleep,
      async clickSel(sel, n) { const r = await page.eval(`(() => { const e = document.querySelectorAll(${JSON.stringify(sel)})[${n || 0}]; if (!e) return null; const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; })()`); if (!r) { console.log('NO ELEMENT', sel); return; } await page.click(r[0], r[1]); },
      async shot(name, full) { const vp = await page.eval('[innerWidth, innerHeight]'); const r = await send('Page.captureScreenshot', full ? { format: 'png' } : { format: 'png', clip: { x: 0, y: 0, width: vp[0] > vp[1] ? 3168 / 3.5 : 1440 / 3.5, height: vp[0] > vp[1] ? 1440 / 3.5 : 3168 / 3.5, scale: 1 } }); fs.writeFileSync(`${ROOT}/shots/${QUICK ? '_quick/' : ''}${name}.png`, Buffer.from(r.result.data, 'base64')); console.log('shot', name); },
    };
    const errs = 'document.body.classList.contains("nogl") ? " NOGL" : "") + [...document.querySelectorAll("pre")].map((p) => p.textContent).join(" | "';
    for (const [name, p, q, steps] of LIST) {
      if (filter && !filter.split(',').some((f) => exact ? name === f : name.includes(f))) continue;
      const land = p === 'player';
      await page.open(`p=${p}&shot=1${QUICK ? '&dpr=1' : ''}${q ? '&' + q : ''}`, land ? 905 : 412, land ? 412 : 905);
      const err = await page.eval(`(${errs})`); if (err) console.log('PAGE ERROR', name, err);
      // layout lint: text clipped by its own box / pictures that did not load
      const lint = await page.eval(`(() => { const o = []; document.querySelectorAll('img[data-img]').forEach((i) => { if (i.getClientRects().length && !i.naturalWidth) o.push('noimg:' + i.dataset.img); }); return o.slice(0, 6).join(' '); })()`); if (lint) console.log('LINT', name, lint);
      if (steps) await steps(page);
      await page.shot(name);
    }
    if (!QUICK && (!filter || filter.includes('_gallery'))) { await page.open('', 1700, 1050, 1); const err = await page.eval(`(${errs})`); if (err) console.log('PAGE ERROR gallery', err); await page.shot('_gallery', true); }
    ws.close();
  } catch (e) { console.log('FAILED', e && e.stack || e); }
  chrome.kill(); await sleep(300); try { fs.rmSync(UD, { recursive: true, force: true }); } catch (e) { /* chrome may still hold a file */ }
})();
