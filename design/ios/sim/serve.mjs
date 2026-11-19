// 设计 demo 的本地静态服务器：node serve.mjs <根目录> [端口]
// 只绑 127.0.0.1（模拟器和本机共用回环），不缓存，只读文件、不写任何东西。
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(process.argv[2] || '.');
const PORT = +(process.argv[3] || 8765);
const TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8', '.md': 'text/plain; charset=utf-8',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp', '.svg': 'image/svg+xml',
  '.gif': 'image/gif', '.woff2': 'font/woff2', '.woff': 'font/woff', '.ttf': 'font/ttf', '.otf': 'font/otf',
  '.mp4': 'video/mp4', '.webm': 'video/webm',
};
// 不对外提供的东西
const DENY = [/(^|\/)\.env/, /(^|\/)\.git(\/|$)/, /\.keystore$/, /local\.properties$/];

http.createServer((req, res) => {
  let rel;
  try { rel = decodeURIComponent(new URL(req.url, 'http://x').pathname); } catch { res.writeHead(400).end(); return; }
  const file = path.join(ROOT, rel);
  if (!file.startsWith(ROOT) || DENY.some((r) => r.test(rel))) { res.writeHead(403).end(); return; }
  fs.stat(file, (err, st) => {
    if (err) { res.writeHead(404).end('404'); return; }
    const f = st.isDirectory() ? path.join(file, 'index.html') : file;
    fs.stat(f, (e2, st2) => {
      if (e2) { res.writeHead(404).end('404'); return; }
      res.writeHead(200, {
        'Content-Type': TYPES[path.extname(f).toLowerCase()] || 'application/octet-stream',
        'Content-Length': st2.size,
        'Cache-Control': 'no-store',
      });
      if (req.method === 'HEAD') { res.end(); return; }
      fs.createReadStream(f).pipe(res);
    });
  });
}).listen(PORT, '127.0.0.1', () => console.log(`serving ${ROOT} at http://127.0.0.1:${PORT}/`));
