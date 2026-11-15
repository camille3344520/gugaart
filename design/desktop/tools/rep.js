// node tools/rep.js <file> <json-file-with-[[from,to],...]>   (small exact-replace helper used while building; every pair must match)
const fs = require('fs'); const [, , file, pairsFile] = process.argv;
let s = fs.readFileSync(file, 'utf8'); const pairs = JSON.parse(fs.readFileSync(pairsFile, 'utf8'));
for (const [a, b] of pairs) { if (!s.includes(a)) { console.log('NO MATCH:', a.slice(0, 80)); process.exitCode = 1; continue; } s = s.split(a).join(b); }
fs.writeFileSync(file, s); console.log('ok', file);
