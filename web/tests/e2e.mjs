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
// Sync now lives in the toolbar's sync popover.
const syncNow = async (pg) => { if (!(await pg.$('#pop .pop[data-kind="sync"]'))) await pg.click('.tb-sync'); await pg.click('#pop [data-act="sync-now"]'); };
const syncLabel = () => page.evaluate(() => document.querySelector('.tb-sync')?.getAttribute('aria-label') || '');
await page.click('.tb-sync'); await page.waitForSelector('#pop .pop[data-kind="sync"]');
check((await page.textContent('#pop .pop')).includes('Pixel 8'), 'toolbar sync popover names the phone');
if (shots) await page.screenshot({ path: `${shots}/pop-sync.png` });
await page.keyboard.press('Escape');
check(!(await page.$('#pop .pop:not(.out)')), 'Escape closes the popover');

for (const k of ['overview', 'rides', 'service', 'fuel']) {
  await page.goto('http://localhost:8766/#' + k); await page.waitForSelector(`.wpage[data-page="${k}"]`);
  await page.waitForTimeout(400);
  if (shots) await page.screenshot({ path: `${shots}/${k}.png`, fullPage: false });
}

// Top tabs, and details open in a pane beside the page; the list stays usable.
check(await page.isVisible('.tb-nav .tb-i.on'), 'pages show as tabs in the header');
await page.goto('http://localhost:8766/#rides'); await page.waitForSelector('.wpage[data-page="rides"]');
await page.click('.ride >> nth=0'); await page.waitForSelector('#sheets .sheet[data-dock="1"]'); await page.waitForTimeout(450);
check(await page.evaluate(() => !document.querySelector('#main').inert && !!document.querySelector('#main [aria-current="true"]')), 'ride opens in the side pane, list stays usable');
if (shots) await page.screenshot({ path: `${shots}/rides-pane.png` });
const t1 = await page.textContent('#sheets .sheet-title');
await page.click('.ride >> nth=1'); await page.waitForTimeout(300);
check((await page.textContent('#sheets .sheet-title')) !== t1 || (await page.$$('#sheets .sheet')).length === 1, 'picking another ride swaps the pane');
await page.keyboard.press('Escape'); await page.waitForTimeout(350);
check(!(await page.$('#sheets .sheet:not(.out)')), 'Escape closes the pane');

// A change on the web: log fuel.
await page.goto('http://localhost:8766/#fuel'); await page.waitForSelector('.wpage[data-page="fuel"]');
const before = plain.vehicles.find((v) => v.id === plain.active).fuel.length;
await page.click('.wh-act [data-arg="fuel:new"]');
await page.waitForSelector('form[data-form="fuel"]'); await page.waitForTimeout(450);
if (shots) await page.screenshot({ path: `${shots}/sheet-fuel.png` });
await page.fill('form[data-form="fuel"] [name=odo]', String(Math.round(plain.vehicles[0].odo + 150)));
await page.fill('form[data-form="fuel"] [name=litres]', '5.2');
check(await page.isVisible('form[data-form="fuel"] .dlg-foot button[type=submit]'), 'form buttons sit in the dialog footer');
await page.click('form[data-form="fuel"] button[type=submit]');
await page.waitForFunction(() => (document.querySelector('.tb-sync')?.getAttribute('aria-label') || '').includes('waiting'), null, { timeout: 5000 });
check(true, 'web shows the change as waiting for the phone');
const n = await phone.applyOps(plain);
check(n >= 1, `phone applied ${n} op(s)`);
check(plain.vehicles.find((v) => v.id === plain.active).fuel.length === before + 1, 'fill-up is in the phone data');
await syncNow(page);
await page.waitForFunction(() => (document.querySelector('.tb-sync')?.getAttribute('aria-label') || '').includes('Synced'), null, { timeout: 8000 }).catch(async (e) => { console.log('LINE', await syncLabel(), errors); throw e; });
await page.keyboard.press('Escape');
check(true, 'web caught up after the phone synced');

// Dark theme, a sheet, and the phone-width layout while linked.
// Settings is its own tab and page.
await page.click('.tb-i[href="#settings"]'); await page.waitForSelector('.wpage[data-page="settings"] .prefs'); await page.waitForTimeout(400);
check(!(await page.$('#sheets .sheet')), 'Settings is a page, not a popup');
if (shots) await page.screenshot({ path: `${shots}/settings.png` });
await page.click('.pf-i[data-arg="look"]'); await page.waitForSelector('.pf-pane[data-pane="look"]'); await page.waitForTimeout(300);
if (shots) await page.screenshot({ path: `${shots}/settings-look.png` });
await page.click('[data-act="theme"][data-arg="obsidian"]');
check(await page.evaluate(() => document.documentElement.dataset.theme) === 'obsidian', 'theme switches to Dark');
check(await page.isVisible('.pf-pane[data-pane="look"]'), 'Settings stays on the Look section');
await page.goto('http://localhost:8766/#overview'); await page.waitForSelector('.wpage[data-page="overview"]'); await page.waitForTimeout(300);
if (shots) await page.screenshot({ path: `${shots}/overview-dark.png` });
await page.click('.tb-bike'); await page.waitForSelector('#pop .pop[data-kind="bike"]'); await page.waitForTimeout(250);
if (shots) await page.screenshot({ path: `${shots}/pop-bike-dark.png` });
await page.keyboard.press('Escape');
await page.goto('http://localhost:8766/#overview'); await page.waitForTimeout(400);
if (shots) await page.screenshot({ path: `${shots}/overview-dark.png` });
await page.goto('http://localhost:8766/#service'); await page.waitForSelector('.wpage[data-page="service"]');
await page.click('#svc-rows .row'); await page.waitForSelector('#sheets .sheet'); await page.waitForTimeout(500);
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
await syncNow(page);
await page.waitForSelector('.gt-qr', { timeout: 8000 });
check(true, 'unlinked from the phone: back to the QR code');
const leftover = await page.evaluate(() => new Promise((ok) => { const r = indexedDB.open('waylink-web'); r.onsuccess = () => { const t = r.result.transaction('kv').objectStore('kv').getAll(); t.onsuccess = () => ok(t.result.length); }; }));
check(leftover === 0, 'nothing left in this browser');

check(errors.length === 0, 'no page errors' + (errors.length ? ': ' + errors.join(' | ') : ''));
await browser.close(); relay.close(); site.close();
process.exit(fails ? 1 : 0);
