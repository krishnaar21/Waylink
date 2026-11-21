// End to end: the web page shows a QR code, a pretend phone scans it, data appears, a change on the
//   node web/tests/e2e.mjs [shotsDir]
import { createRequire } from 'node:module';
const { chromium } = createRequire(import.meta.url)('playwright');
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { start as startRelay } from '../../relay/dev.mjs';
import { linkPhone } from './phone.mjs';
import * as S from '../js/store.js';

const ROOT = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const shots = process.argv[2];
const TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.webp': 'image/webp', '.png': 'image/png', '.woff2': 'font/woff2', '.json': 'application/json' };
const site = http.createServer((q, r) => {
  let p = decodeURIComponent(new URL(q.url, 'http://x').pathname); if (p.endsWith('/')) p += 'index.html';
  const f = path.join(ROOT, p);
  if (!f.startsWith(ROOT) || !fs.existsSync(f)) { r.writeHead(404); return r.end(); }
  r.writeHead(200, { 'content-type': TYPES[path.extname(f)] || 'application/octet-stream' }); fs.createReadStream(f).pipe(r);
});
await new Promise((ok) => site.listen(8766, ok));
const relay = await startRelay(8787);
const RELAY = 'http://localhost:8787';

let fails = 0;
const check = (ok, what) => { console.log(`${ok ? 'ok  ' : 'FAIL'} ${what}`); if (!ok) fails++; };

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 });
const page = await ctx.newPage();
const errors = [];
page.on('pageerror', (e) => errors.push(e.message));
page.on('console', (m) => { if (m.type() === 'error' && !/status of (410|401|204)/.test(m.text())) errors.push(m.text()); });
await page.goto('http://localhost:8766/');
await page.waitForFunction(() => window.__wlQr, null, { timeout: 10000 });

// The phone scans and sends its data.
const state = S.freshState(); S.seedSample(state); state.onboarded = true;
const plain = JSON.parse(JSON.stringify(state));
const phone = await linkPhone(await page.evaluate(() => window.__wlQr), RELAY, 'Pixel 8');
await page.waitForSelector('.wait, .gt-wait, .wpage', { timeout: 10000 });
await phone.push(plain);
// Layout mockups: the same linked app, dressed three ways by tests/layouts.css.
//   node web/tests/layouts.mjs <shotsDir>
await page.addStyleTag({ url: 'http://localhost:8766/tests/layouts.css' });
const go = async (k) => { await page.evaluate((k) => { location.hash = k; }, k); await page.waitForSelector(`.wpage[data-page="${k}"]`); await page.waitForTimeout(450); };
const shot = (n) => page.screenshot({ path: `${shots}/${n}.png` });
for (const L of ['tabs', 'side', 'split']) {
  await page.evaluate((L) => { document.documentElement.dataset.layout = L; }, L);
  await page.evaluate(() => { delete document.documentElement.dataset.theme; });
  await go('overview'); await shot(`${L}-overview`);
  await go('rides'); await shot(`${L}-rides`);
  if (L === 'split') { await page.click('.ride'); await page.waitForSelector('#sheets .sheet'); await page.waitForTimeout(500); await shot(`${L}-rides-detail`); await page.keyboard.press('Escape'); await page.waitForTimeout(300); }
  await go('service');
  await page.evaluate(() => { document.documentElement.dataset.theme = 'obsidian'; }); await page.waitForTimeout(200); await shot(`${L}-service-dark`);
}
console.log('errors', errors);
await browser.close(); relay.close?.(); site.close(); process.exit(0);
