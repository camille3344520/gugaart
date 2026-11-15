// node tools/sheets.js   -> shots/_sheets/NN.png : contact sheets of every screenshot (4 portrait or 2 landscape per sheet), for a quick layout pass
const { spawn } = require('child_process'); const fs = require('fs'); const path = require('path'); const os = require('os');
const ROOT = path.resolve(__dirname, '..').split(path.sep).join('/');
const UD = fs.mkdtempSync(path.join(os.tmpdir(), 'b1phone-sheet-'));
const PORT = 9750 + Math.floor(Math.random() * 100);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const LIST = require('./shotlist.js').map((l) => l[0]);
const DIR = process.env.QUICK ? '_quick/' : '';
(async () => {
  fs.mkdirSync(`${ROOT}/shots/_sheets`, { recursive: true });
  const port = LIST.filter((n) => !n.startsWith('player')), land = LIST.filter((n) => n.startsWith('player'));
  const sheets = []; for (let i = 0; i < port.length; i += 4) sheets.push({ w: 1680, h: 936, names: port.slice(i, i + 4), iw: 411 }); for (let i = 0; i < land.length; i += 4) sheets.push({ w: 1840, h: 900, names: land.slice(i, i + 4), iw: 905 });
  const chrome = spawn('C:/Program Files/Google/Chrome/Application/chrome.exe', ['--headless=new', `--user-data-dir=${UD}`, `--remote-debugging-port=${PORT}`, '--hide-scrollbars', '--allow-file-access-from-files', 'about:blank'], { stdio: 'ignore' });
  try {
    let tab; for (let i = 0; i < 60 && !tab; i++) { await sleep(200); try { tab = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find((t) => t.type === 'page'); } catch (e) { /* not up yet */ } }
    const ws = new WebSocket(tab.webSocketDebuggerUrl); await new Promise((r) => { ws.onopen = r; });
    let id = 0; const pend = {};
    ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && pend[d.id]) { pend[d.id](d); delete pend[d.id]; } };
    const send = (method, params) => new Promise((res) => { const i = ++id; pend[i] = res; ws.send(JSON.stringify({ id: i, method, params: params || {} })); });
    const only = process.argv[2];
    for (let s = 0; s < sheets.length; s++) {
      const S = sheets[s]; if (only && !S.names.some((n) => only.split(',').some((f) => n.includes(f)))) continue;
      const html = `<body style="margin:0;background:#222;display:flex;flex-wrap:wrap;gap:10px;font:14px sans-serif;color:#fff">` + S.names.map((n) => `<div><div style="padding:4px 6px">${n}</div><img src="file:///${ROOT}/shots/${DIR}${n}.png" style="width:${S.iw}px;display:block"></div>`).join('') + `</body>`;
      const f = `${UD}/s.html`; fs.writeFileSync(f, html);
      await send('Emulation.setDeviceMetricsOverride', { width: S.w, height: S.h, deviceScaleFactor: 1, mobile: false });
      await send('Page.navigate', { url: 'file:///' + f.split(path.sep).join('/') }); await sleep(1800);
      const r = await send('Page.captureScreenshot', { format: 'jpeg', quality: 88 });
      const name = String(s + 1).padStart(2, '0'); fs.writeFileSync(`${ROOT}/shots/_sheets/${name}.jpg`, Buffer.from(r.result.data, 'base64')); console.log('sheet', name, S.names.join(' '));
    }
    ws.close();
  } catch (e) { console.log('FAILED', e && e.stack || e); }
  chrome.kill(); await sleep(300); try { fs.rmSync(UD, { recursive: true, force: true }); } catch (e) { /* ignore */ }
})();
