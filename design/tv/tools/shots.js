// node tools/shots.js [filter[,filter]]   -> shots/<screen>[-<state>].png at 1920 x 1080
// One headless Chrome over CDP (SwiftShader WebGL, its own temporary user-data-dir). Waits for body[data-ready] (pictures decoded, glass engine up).
const { spawn } = require('child_process'); const fs = require('fs'); const path = require('path'); const os = require('os');
const ROOT = path.resolve(__dirname, '..').replace(/\\/g, '/');
const UD = fs.mkdtempSync(path.join(os.tmpdir(), 'b1tv-chrome-'));
const PORT = 9700 + Math.floor(Math.random() * 200);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
(async () => {
  const filter = process.argv[2];
  const chrome = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', `--user-data-dir=${UD}`, `--remote-debugging-port=${PORT}`, '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--hide-scrollbars', '--no-proxy-server', 'about:blank'], { stdio: 'ignore' });
  try {
    let tab; for (let i = 0; i < 80 && !tab; i++) { await sleep(200); try { tab = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find((t) => t.type === 'page'); } catch (e) { /* not up yet */ } }
    const ws = new WebSocket(tab.webSocketDebuggerUrl); await new Promise((r) => { ws.onopen = r; });
    let id = 0; const pend = {};
    ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && pend[d.id]) { pend[d.id](d); delete pend[d.id]; } if (d.method === 'Runtime.exceptionThrown') { const x = d.params.exceptionDetails; console.log('  EXC', (x.exception && x.exception.description || x.text).slice(0, 400), (x.url || '').split('/').pop(), x.lineNumber); } };
    const send = (method, params) => new Promise((res) => { const i = ++id; pend[i] = res; ws.send(JSON.stringify({ id: i, method, params: params || {} })); });
    const ev = async (expr) => { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true }); return r.result && r.result.result && r.result.result.value; };
    await send('Runtime.enable'); await send('Emulation.setDeviceMetricsOverride', { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false });
    await send('Page.navigate', { url: `file:///${ROOT}/index.html` }); await sleep(1200);
    const list = JSON.parse(await ev('JSON.stringify(Object.keys(APP.SCREENS).flatMap((p) => APP.SCREENS[p].states.map((s) => [p, s[0]])))'));
    let n = 0;
    for (const [p, st] of list) {
      const name = p + (st ? '-' + st : ''); if (filter && !filter.split(',').some((f) => (f.endsWith('$') ? name === f.slice(0, -1) : name.includes(f)))) continue;
      await send('Page.navigate', { url: `file:///${ROOT}/index.html?p=${p}&st=${st}&shot=1` });
      let ok = false; for (let i = 0; i < 200 && !ok; i++) { await sleep(150); ok = await ev('!!(document.body && document.body.dataset.ready === "1")'); }
      await sleep(400);
      const err = await ev('[...document.querySelectorAll("pre")].map((p) => p.textContent).join(" | ") + (document.body.classList.contains("nogl") ? " NOGL" : "")'); if (err || !ok) console.log('PAGE ERROR', name, ok ? '' : 'NOT READY', err);
      if (process.env.DUMP) console.log('  DUMP', await ev(process.env.DUMP));
      const r = await send('Page.captureScreenshot', { format: 'png' }); fs.writeFileSync(`${ROOT}/shots/${name}.png`, Buffer.from(r.result.data, 'base64')); console.log('shot', name); n++;
    }
    if (!filter || filter.includes('_gallery')) {       // the gallery page itself (iframes load when they scroll into view)
      await send('Emulation.setDeviceMetricsOverride', { width: 1640, height: 1000, deviceScaleFactor: 1, mobile: false });
      await send('Page.navigate', { url: `file:///${ROOT}/index.html` }); await sleep(9000);
      const r = await send('Page.captureScreenshot', { format: 'png' }); fs.writeFileSync(`${ROOT}/shots/_gallery.png`, Buffer.from(r.result.data, 'base64')); console.log('shot _gallery');
    }
    console.log(n, 'shots');
    ws.close();
  } catch (e) { console.log('FAILED', e && e.stack || e); }
  chrome.kill(); await sleep(400); try { fs.rmSync(UD, { recursive: true, force: true }); } catch (e) { /* chrome may still hold a file */ }
})();
