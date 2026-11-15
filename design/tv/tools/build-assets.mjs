// node tools/build-assets.mjs
// One small script per picture (assets/<key>.js sets IMG[key] to a data: URL) so a screen only loads what it shows.
// (The glass engine reads pictures through a canvas; under file:// that only works for data: URLs.)
// Also writes js/data.js = library.json + extra.json from ../data (read-only source).
import fs from 'node:fs'; import path from 'node:path'; import { fileURLToPath } from 'node:url';
const root = path.join(path.dirname(fileURLToPath(import.meta.url)), '..'), data = path.join(root, '../data'), uw = path.join(root, '../..');
const mime = { '.jpg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp' };
let n = 0, bytes = 0; const keys = [];
const add = (key, file) => { const b = fs.readFileSync(file); fs.writeFileSync(path.join(root, 'assets', key + '.js'), `IMG[${JSON.stringify(key)}]="data:${mime[path.extname(file)]};base64,${b.toString('base64')}";\n`); n++; bytes += b.length; keys.push(key); };
for (const f of fs.readdirSync(path.join(data, 'img'))) add(path.basename(f, path.extname(f)), path.join(data, 'img', f));
for (const f of fs.readdirSync(path.join(uw, 'design/brand/mascot'))) add(path.basename(f, '.webp'), path.join(uw, 'design/brand/mascot', f));
add('brand_icon', path.join(uw, 'design/brand/icon-512.png'));
const lib = JSON.parse(fs.readFileSync(path.join(data, 'library.json'), 'utf8')), extra = JSON.parse(fs.readFileSync(path.join(data, 'extra.json'), 'utf8')), extra2 = JSON.parse(fs.readFileSync(path.join(data, 'extra2.json'), 'utf8'));
fs.writeFileSync(path.join(root, 'js/data.js'), 'window.IMG={};\nwindow.LIB=' + JSON.stringify(lib) + ';\nwindow.EXTRA=' + JSON.stringify(extra) + ';\nwindow.EXTRA2=' + JSON.stringify(extra2) + ';\nwindow.IMGKEYS=' + JSON.stringify(keys) + ';\n');
console.log(n, 'pictures', Math.round(bytes / 1e6) + ' MB');
