// node tools/shots.js [filter[,filter]]  -> shots/<name>.png. One headless Chrome over CDP (SwiftShader WebGL, its own user-data-dir). Waits for body[data-ready].
// List = tools/shotlist.js: [name, 'p=..&st=..', w, h, async steps(page)?]
const { spawn } = require('child_process'); const fs = require('fs'); const path = require('path'); const os = require('os');
const ROOT = path.resolve(__dirname, '..').split(path.sep).join('/');
const UD = fs.mkdtempSync(path.join(os.tmpdir(), 'b1desk-chrome-'));
const PORT = 9700 + Math.floor(Math.random() * 200);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const LIST = require('./shotlist.js');
(async () => {
  const filter = process.argv[2];
  const chrome = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', `--user-data-dir=${UD}`, `--remote-debugging-port=${PORT}`, '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--hide-scrollbars', '--allow-file-access-from-files', 'about:blank'], { stdio: 'ignore' });
  try {
    let tab; for (let i = 0; i < 60 && !tab; i++) { await sleep(200); try { tab = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find((t) => t.type === 'page'); } catch (e) { /* not up yet */ } }
    const ws = new WebSocket(tab.webSocketDebuggerUrl); await new Promise((r) => { ws.onopen = r; });
    let id = 0; const pend = {};
    ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && pend[d.id]) { pend[d.id](d); delete pend[d.id]; } };
    const send = (method, params) => new Promise((res) => { const i = ++id; pend[i] = res; ws.send(JSON.stringify({ id: i, method, params: params || {} })); });
    const page = {
      async open(file, q, w, h) { await send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 1, mobile: false }); await send('Page.navigate', { url: `file:///${ROOT}/${file}?${q}` }); let ok = false; for (let i = 0; i < 300 && !ok; i++) { await sleep(150); ok = await page.eval('!!(document.body && document.body.dataset.ready === "1")'); } if (!ok) console.log('NOT READY', q); await sleep(350); },
      async eval(expr) { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true }); if (r.result && r.result.exceptionDetails) console.log('EVAL ERROR', JSON.stringify(r.result.exceptionDetails).slice(0, 300)); return r.result && r.result.result && r.result.result.value; },
      mouse: (type, x, y, b) => send('Input.dispatchMouseEvent', { type, x, y, button: b === 0 ? 'none' : (b === 2 ? 'right' : 'left'), buttons: b === 0 ? 0 : (b === 2 ? 2 : 1), clickCount: 1, pointerType: 'mouse' }),
      async hover(x, y) { await page.mouse('mouseMoved', x, y, 0); },
      async click(x, y, b) { await page.mouse('mouseMoved', x, y, 0); await page.mouse('mousePressed', x, y, b); await sleep(40); await page.mouse('mouseReleased', x, y, b); },
      async at(sel, n) { return page.eval(`(() => { const e = document.querySelectorAll(${JSON.stringify(sel)})[${n || 0}]; if (!e) return null; const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; })()`); },
      async clickSel(sel, n, b) { const r = await page.at(sel, n); if (!r) { console.log('NO ELEMENT', sel); return; } await page.click(r[0], r[1], b); },
      async hoverSel(sel, n, dx, dy) { const r = await page.at(sel, n); if (!r) { console.log('NO ELEMENT', sel); return; } await page.hover(r[0] + (dx || 0), r[1] + (dy || 0)); },
      wait: sleep,
      async shot(name) { const r = await send('Page.captureScreenshot', { format: 'png' }); fs.writeFileSync(`${ROOT}/shots/${name}.png`, Buffer.from(r.result.data, 'base64')); console.log('shot', name); },
    };
    for (const [name, q, w, h, steps] of LIST) {
      if (filter && !filter.split(',').some((f) => name.includes(f))) continue;
      await page.open(q.startsWith('@') ? q.slice(1).split('?')[0] : 'board.html', (q.startsWith('@') ? q.split('?')[1] || '' : q) + '&shot=1', w || 1440, h || 900);
      const err = await page.eval('[...document.querySelectorAll("pre.err")].map((p) => p.textContent).join(" | ") + (document.body.classList.contains("nogl") ? " NOGL" : "")'); if (err) console.log('PAGE ERROR', name, err.slice(0, 400));
      if (steps) await steps(page);
      await page.shot(name);
    }
    ws.close();
  } catch (e) { console.log('FAILED', e && e.stack || e); }
  chrome.kill(); await sleep(300); try { fs.rmSync(UD, { recursive: true, force: true }); } catch (e) { /* chrome may still hold a file */ }
})();
