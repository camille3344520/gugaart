// Packs the real-data pictures (../data/img) + her layered sprite + the brand picture into js/assets.js as base64
// (the glass engine reads pictures through a canvas, which file:// only allows for data: URLs), and the data itself into js/data.js.
// node tools/build-assets.mjs
import fs from 'node:fs'; import path from 'node:path'; import { fileURLToPath } from 'node:url';
const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..'), data = path.join(root, '../data'), uw = path.join(root, '../..');
const out = {}; const mime = { '.jpg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp' };
const add = (key, file) => { if (!fs.existsSync(file)) { console.log('missing', file); return; } out[key] = `data:${mime[path.extname(file)]};base64,` + fs.readFileSync(file).toString('base64'); };
for (const f of fs.readdirSync(path.join(data, 'img'))) add(path.basename(f, path.extname(f)), path.join(data, 'img', f));
for (const f of fs.readdirSync(path.join(uw, 'design/brand/mascot'))) add(path.basename(f, '.webp'), path.join(uw, 'design/brand/mascot', f));
add('brand', path.join(uw, 'design/brand/gugaemby.jpg'));
fs.writeFileSync(path.join(root, 'js/assets.js'), 'window.IMG=' + JSON.stringify(out, null, 0).replace(/","/g, '",\n"') + ';\n');
const key = (v) => typeof v === 'string' && /^img\//.test(v) ? path.basename(v, path.extname(v)) : v;
const fix = (o) => { if (Array.isArray(o)) return o.map(fix); if (o && typeof o === 'object') { const r = {}; for (const k in o) r[k] = fix(o[k]); return r; } return key(o); };
const db = fix(JSON.parse(fs.readFileSync(path.join(data, 'library.json'), 'utf8')));
for (const f of fs.readdirSync(data)) if (/\.json$/.test(f) && f !== 'library.json') db[path.basename(f, '.json')] = fix(JSON.parse(fs.readFileSync(path.join(data, f), 'utf8')));
fs.writeFileSync(path.join(root, 'js/data.js'), 'window.DB=' + JSON.stringify(db) + ';\n');
console.log(Object.keys(out).length, 'images', Math.round(fs.statSync(path.join(root, 'js/assets.js')).size / 1e6) + ' MB; data keys', Object.keys(db).join(','));
