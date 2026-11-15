// node tools/pack.mjs  -> pack/<key>.js (one base64 data URL per picture; the glass engine can only read data URLs under file://) + js/lib.js (library.json as window.LIB)
import fs from 'fs'; import path from 'path'; import { fileURLToPath } from 'url';
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..'), DATA = path.resolve(ROOT, '../data'), BRAND = path.resolve(ROOT, '../brand');
const mime = { '.jpg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp' };
const put = (key, file) => { const out = path.join(ROOT, 'pack', key + '.js'); if (fs.existsSync(out) && fs.statSync(out).mtimeMs > fs.statSync(file).mtimeMs) return 0;
  fs.writeFileSync(out, `PACK(${JSON.stringify(key)},"data:${mime[path.extname(file).toLowerCase()]};base64,${fs.readFileSync(file).toString('base64')}");`); return 1; };
let n = 0;
for (const f of fs.readdirSync(path.join(DATA, 'img'))) if (mime[path.extname(f).toLowerCase()]) n += put(path.basename(f, path.extname(f)), path.join(DATA, 'img', f));
for (const f of fs.readdirSync(path.join(BRAND, 'mascot'))) n += put(path.basename(f, '.webp'), path.join(BRAND, 'mascot', f));
n += put('brand_icon', path.join(BRAND, 'icon-512.png')); n += put('brand', path.join(BRAND, 'gugaemby.jpg'));
const lib = JSON.parse(fs.readFileSync(path.join(DATA, 'library.json'), 'utf8'));
for (const x of fs.readdirSync(DATA)) if (/\.json$/.test(x) && x !== 'library.json') lib['extra:' + x.replace(/\.json$/, '')] = JSON.parse(fs.readFileSync(path.join(DATA, x), 'utf8'));
fs.writeFileSync(path.join(ROOT, 'js/lib.js'), 'window.LIB=' + JSON.stringify(lib) + ';');
console.log('packed', n, 'pictures; lib.js', fs.statSync(path.join(ROOT, 'js/lib.js')).size);
