// node tools/shots.js [filter[,filter]] [--out=<目录>] [--ud=<Chrome 用户数据目录>]
// macOS 版截图：本机 Chrome 无头（SwiftShader WebGL）走 CDP，做法同 ../../desktop/tools/shots.js，只换了 Chrome 路径和输出位置。
// 清单 = tools/shotlist.js：[name, 'p=..&st=..' 或 '@screen.html?..', w, h, async steps(page)?]。等 body[data-ready]。
// 默认出到 ../shots/（.gitignore 里）；跑验证时用 --out 指到会话的临时目录，截图不进仓库（画面是用户片库的真实海报）。
const { spawn } = require('child_process'); const fs = require('fs'); const path = require('path'); const os = require('os');
const ROOT = path.resolve(__dirname, '..');
const arg = (k) => { const a = process.argv.find((x) => x.startsWith('--' + k + '=')); return a ? a.slice(k.length + 3) : null; };
const OUT = arg('out') || path.join(ROOT, 'shots'); fs.mkdirSync(OUT, { recursive: true });
const UD = arg('ud') ? fs.mkdtempSync(path.join(arg('ud'), 'chrome-')) : fs.mkdtempSync(path.join(os.tmpdir(), 'b1mac-chrome-'));
const CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const PORT = 9700 + Math.floor(Math.random() * 200);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const LIST = require('./shotlist.js');
(async () => {
  const filter = process.argv.slice(2).find((x) => !x.startsWith('--'));
  const chrome = spawn(CHROME, ['--headless=new', `--user-data-dir=${UD}`, `--remote-debugging-port=${PORT}`, '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist', '--hide-scrollbars', '--allow-file-access-from-files', '--no-first-run', '--no-default-browser-check', 'about:blank'], { stdio: 'ignore' });
  const summary = [];
  try {
    let tab; for (let i = 0; i < 60 && !tab; i++) { await sleep(200); try { tab = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find((t) => t.type === 'page'); } catch (e) { /* not up yet */ } }
    if (!tab) throw new Error('Chrome did not come up');
    const ws = new WebSocket(tab.webSocketDebuggerUrl); await new Promise((r) => { ws.onopen = r; });
    let id = 0; const pend = {}; const errors = [];
    ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && pend[d.id]) { pend[d.id](d); delete pend[d.id]; } else if (d.method === 'Runtime.exceptionThrown') errors.push(JSON.stringify(d.params.exceptionDetails).slice(0, 300)); else if (d.method === 'Log.entryAdded' && d.params.entry.level === 'error') errors.push(d.params.entry.text.slice(0, 200)); };
    const send = (method, params) => new Promise((res) => { const i = ++id; pend[i] = res; ws.send(JSON.stringify({ id: i, method, params: params || {} })); });
    await send('Runtime.enable'); await send('Log.enable');
    const page = {
      async open(file, q, w, h) { errors.length = 0; await send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 1, mobile: false }); await send('Page.navigate', { url: `file://${ROOT}/${file}?${q}` }); let ok = false; for (let i = 0; i < 300 && !ok; i++) { await sleep(150); ok = await page.eval('!!(document.body && document.body.dataset.ready === "1")'); } if (!ok) console.log('NOT READY', q); await sleep(350); },
      async eval(expr) { const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true }); if (r.result && r.result.exceptionDetails) console.log('EVAL ERROR', JSON.stringify(r.result.exceptionDetails).slice(0, 300)); return r.result && r.result.result && r.result.result.value; },
      mouse: (type, x, y, b) => send('Input.dispatchMouseEvent', { type, x, y, button: b === 0 ? 'none' : (b === 2 ? 'right' : 'left'), buttons: b === 0 ? 0 : (b === 2 ? 2 : 1), clickCount: 1, pointerType: 'mouse' }),
      async hover(x, y) { await page.mouse('mouseMoved', x, y, 0); },
      async click(x, y, b) { await page.mouse('mouseMoved', x, y, 0); await page.mouse('mousePressed', x, y, b); await sleep(40); await page.mouse('mouseReleased', x, y, b); },
      async at(sel, n) { return page.eval(`(() => { const e = document.querySelectorAll(${JSON.stringify(sel)})[${n || 0}]; if (!e) return null; const r = e.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; })()`); },
      async clickSel(sel, n, b) { const r = await page.at(sel, n); if (!r) { console.log('NO ELEMENT', sel); return; } await page.click(r[0], r[1], b); },
      async hoverSel(sel, n, dx, dy) { const r = await page.at(sel, n); if (!r) { console.log('NO ELEMENT', sel); return; } await page.hover(r[0] + (dx || 0), r[1] + (dy || 0)); },
      async key(key, mods) { await send('Input.dispatchKeyEvent', { type: 'keyDown', key, modifiers: mods || 0 }); await send('Input.dispatchKeyEvent', { type: 'keyUp', key, modifiers: mods || 0 }); },
      wait: sleep,
      /** 按了会换页的键之后，等新页就绪 */
      async ready() { await sleep(300); for (let i = 0; i < 200; i++) { if (await page.eval('!!(document.body && document.body.dataset.ready === "1")')) return; await sleep(150); } console.log('NOT READY after nav'); },
      async shot(name) { const r = await send('Page.captureScreenshot', { format: 'png' }); fs.writeFileSync(path.join(OUT, `${name}.png`), Buffer.from(r.result.data, 'base64')); console.log('shot', name); },
    };
    for (const [name, q, w, h, steps] of LIST) {
      if (filter && !filter.split(',').some((f) => name.includes(f))) continue;
      const file = q.startsWith('@') ? q.slice(1).split('?')[0] : 'board.html', query = (q.startsWith('@') ? q.split('?')[1] || '' : q) + '&shot=1';
      await page.open(file, query, w || 1470, h || 956);
      // 检查：页面错误、玻璃没起来（nogl）、图片有没有解码完（窗口壳 screen.html 里要穿到 iframe 看）
      const probe = `(() => { const docs = [document]; const fr = document.getElementById('win'); try { if (fr && fr.contentDocument) docs.push(fr.contentDocument); } catch (e) {}
        return docs.map((d) => { const ims = [...d.images]; return { err: [...d.querySelectorAll('pre.err')].map((p) => p.textContent).join(' | '), nogl: d.body.classList.contains('nogl'), img: ims.filter((i) => i.complete && i.naturalWidth > 0).length + '/' + ims.length, tl: d.querySelectorAll('.tlights a').length }; }); })()`;
      const info = await page.eval(probe);
      const bad = info.filter((x) => x.err || x.nogl).map((x) => (x.err || '') + (x.nogl ? ' NOGL' : '')).join(' | ');
      if (bad) console.log('PAGE ERROR', name, bad.slice(0, 400));
      if (steps) await steps(page);
      await page.shot(name);
      summary.push({ name, img: info.map((x) => x.img).join(' + '), tl: info.map((x) => x.tl).join('+'), errors: errors.slice(), bad });
    }
    ws.close();
  } catch (e) { console.log('FAILED', e && e.stack || e); }
  chrome.kill(); await sleep(300); try { fs.rmSync(UD, { recursive: true, force: true }); } catch (e) { /* chrome may still hold a file */ }
  fs.writeFileSync(path.join(OUT, '_summary.json'), JSON.stringify(summary, null, 1));
  const errs = summary.filter((s) => s.errors.length || s.bad); console.log(`\n${summary.length} shots, ${errs.length} with errors`); errs.forEach((s) => console.log(' ', s.name, s.bad, s.errors.join(' | ').slice(0, 300)));
})();
