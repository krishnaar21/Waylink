// node tests/check_photos.mjs — every manifest entry points to a file and carries a credit.
import fs from 'node:fs';
const m = JSON.parse(fs.readFileSync(new URL('../img/bikes/manifest.json', import.meta.url)));
let bad = 0;
for (const [k, v] of Object.entries(m)) {
  if (k.startsWith('_')) continue;
  const ok = v.src && fs.existsSync(new URL('../' + v.src, import.meta.url)) && v.credit;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${k}: ${v.src} — ${v.credit || 'no credit'}`); if (!ok) bad++;
}
process.exit(bad ? 1 : 0);
