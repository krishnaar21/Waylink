// End to end: the web page shows a QR code, a pretend phone scans it, data appears, a change on the
// web reaches the phone, the phone unlinks. Also takes screenshots of each page.
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
check(await page.locator('.gt-qr svg[role=img]').count() === 1, 'gate shows a QR code');
if (shots) await page.screenshot({ path: `${shots}/gate.png` });

// The phone scans and sends its data.
const state = S.freshState(); S.seedSample(state); state.onboarded = true;
const plain = JSON.parse(JSON.stringify(state));
const phone = await linkPhone(await page.evaluate(() => window.__wlQr), RELAY, 'Pixel 8');
await page.waitForSelector('.wait, .gt-wait, .wpage', { timeout: 10000 });
await phone.push(plain);
await page.waitForSelector('.wpage[data-page="overview"]', { timeout: 15000 }).catch(async (e) => { console.log('ERRORS', errors); await page.screenshot({ path: '/tmp/claude-0/wshots/fail.png' }); console.log(await page.evaluate(() => document.querySelector('#main').innerText.slice(0,300))); throw e; });
check(true, 'linked and showing the overview');
const txt = await page.textContent('.side');
check(txt.includes('Pixel 8'), 'sidebar names the phone');

for (const k of ['overview', 'rides', 'service', 'fuel', 'settings']) {
  await page.goto('http://localhost:8766/#' + k); await page.waitForSelector(`.wpage[data-page="${k}"]`);
  await page.waitForTimeout(400);
  if (shots) await page.screenshot({ path: `${shots}/${k}.png`, fullPage: false });
}

// A change on the web: log fuel.
await page.goto('http://localhost:8766/#fuel'); await page.waitForSelector('.wpage[data-page="fuel"]');
const before = plain.vehicles.find((v) => v.id === plain.active).fuel.length;
await page.click('.wh-act [data-arg="fuel:new"]');
await page.waitForSelector('form[data-form="fuel"]');
if (shots) await page.screenshot({ path: `${shots}/sheet-fuel.png` });
await page.fill('form[data-form="fuel"] [name=odo]', String(Math.round(plain.vehicles[0].odo + 150)));
await page.fill('form[data-form="fuel"] [name=litres]', '5.2');
await page.click('form[data-form="fuel"] button[type=submit]');
await page.waitForFunction(() => document.querySelector('.sync-line')?.textContent.includes('waiting'), null, { timeout: 5000 });
check(true, 'web shows the change as waiting for the phone');
const n = await phone.applyOps(plain);
check(n >= 1, `phone applied ${n} op(s)`);
check(plain.vehicles.find((v) => v.id === plain.active).fuel.length === before + 1, 'fill-up is in the phone data');
await page.evaluate(() => document.querySelector('[data-act="sync-now"]').click());
await page.waitForFunction(() => document.querySelector('.sync-line')?.textContent.includes('Synced'), null, { timeout: 8000 }).catch(async (e) => { console.log('LINE', await page.textContent('.sync-line'), errors); throw e; });
check(true, 'web caught up after the phone synced');

// Dark theme, a sheet, and the phone-width layout while linked.
await page.goto('http://localhost:8766/#settings'); await page.waitForSelector('.wpage[data-page="settings"]');
await page.click('[data-act="theme"][data-arg="obsidian"]');
check(await page.evaluate(() => document.documentElement.dataset.theme) === 'obsidian', 'theme switches to Dark');
await page.goto('http://localhost:8766/#overview'); await page.waitForTimeout(400);
if (shots) await page.screenshot({ path: `${shots}/overview-dark.png` });
await page.goto('http://localhost:8766/#service'); await page.waitForSelector('.wpage[data-page="service"]');
await page.click('#svc-rows .row'); await page.waitForSelector('#sheets .sheet'); await page.waitForTimeout(400);
if (shots) await page.screenshot({ path: `${shots}/sheet-svc-dark.png` });
await page.keyboard.press('Escape');
await page.evaluate(() => localStorage.removeItem('waylink-web-look'));
const mp = await ctx.newPage(); await mp.setViewportSize({ width: 390, height: 844 });
await mp.goto('http://localhost:8766/#overview'); await mp.waitForSelector('.wpage[data-page="overview"]'); await mp.waitForTimeout(400);
if (shots) await mp.screenshot({ path: `${shots}/overview-phone.png`, fullPage: false });
await mp.close();
// Phone-width layout.
const m = await browser.newPage({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1 });
// (new context: not linked, shows the gate)
await m.goto('http://localhost:8766/'); await m.waitForSelector('.gt-qr');
if (shots) await m.screenshot({ path: `${shots}/gate-phone.png`, fullPage: true });

// Phone unlinks: the web drops the data and shows a new code.
await phone.unlink();
await page.evaluate(() => document.querySelector('[data-act="sync-now"]').click());
await page.waitForSelector('.gt-qr', { timeout: 8000 });
check(true, 'unlinked from the phone: back to the QR code');
const leftover = await page.evaluate(() => new Promise((ok) => { const r = indexedDB.open('waylink-web'); r.onsuccess = () => { const t = r.result.transaction('kv').objectStore('kv').getAll(); t.onsuccess = () => ok(t.result.length); }; }));
check(leftover === 0, 'nothing left in this browser');

check(errors.length === 0, 'no page errors' + (errors.length ? ': ' + errors.join(' | ') : ''));
await browser.close(); relay.close(); site.close();
process.exit(fails ? 1 : 0);
