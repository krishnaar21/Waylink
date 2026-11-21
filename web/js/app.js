// Waylink for desktop: routing, sheets and changes. The data comes from the phone through the link (sync.js);
// nothing here works until a phone has scanned the QR code.
import * as S from './store.js';
import * as V from './views.js';
import * as P from './pages.js';
import * as L from './sync.js';

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const DAY = 86400000;
const PAGE_KEYS = P.PAGES.map(([k]) => k);

let page = PAGE_KEYS.includes(location.hash.slice(1)) ? location.hash.slice(1) : 'overview';
let s = null; // the state on screen: the phone's data with our waiting changes on top
let sheetKey = null, sheetStack = [];
let qrSvg = '';
const live = { draftPhoto: undefined };

// ---------- This computer's look (starts from the phone's, then kept here) ----------
const LOOK_KEY = 'waylink-web-look';
let lookLocal = (() => { try { return JSON.parse(localStorage.getItem(LOOK_KEY) || '{}') || {}; } catch { return {}; } })();
const saveLook = () => { try { localStorage.setItem(LOOK_KEY, JSON.stringify(lookLocal)); } catch {} };
function look() {
  const ps = L.link.base?.settings || {};
  return {
    theme: lookLocal.theme ?? ps.theme ?? 'auto',
    accent: lookLocal.accent ?? ps.accent ?? 'wine',
    progress: lookLocal.progress ?? ps.progress ?? 'gauge',
    rideChart: lookLocal.rideChart ?? ps.rideChart ?? 0,
    font: ps.font || 'system', bodyFont: ps.bodyFont || 'system', icons: ps.icons || 'line',
    supporter: !!(ps.supporter || ps.supporterPlus),
  };
}
const ctx = () => ({ s, link: L.link, live, look: look() });

const mqDark = matchMedia('(prefers-color-scheme: dark)');
// Same rule as the phone (ui/theme/Theme.kt resolveTheme): Auto is Paper for supporters, Light and Dark for everyone else.
function resolveTheme(t, sup, dark) {
  const premium = t === 'sketch' || t === 'sketchdark';
  if (t === 'auto' || (premium && !sup)) return sup ? (dark ? 'sketchdark' : 'sketch') : (dark ? 'obsidian' : 'paper');
  return ['paper', 'obsidian', 'amoled', 'sketch', 'sketchdark'].includes(t) ? t : 'paper';
}
const FONT_CSS = { onest: 'Onest:wght@400;500;600', garamond: 'EB+Garamond:wght@400;500;600', crimson: 'Crimson+Pro:wght@400;500;600', fraunces: 'Fraunces:opsz,wght@9..144,500;9..144,600' };
const BODY_CSS = { atkinson: 'Atkinson+Hyperlegible+Next:wght@400..700', public: 'Public+Sans:wght@400..700' };
function fontLink(id, href) {
  let el = document.getElementById(id);
  if (!href) { el?.remove(); return; }
  if (!el) { el = Object.assign(document.createElement('link'), { rel: 'stylesheet', id }); document.head.appendChild(el); }
  if (el.getAttribute('href') !== href) el.setAttribute('href', href);
}
function applyTheme() {
  const lk = look(), h = document.documentElement;
  let t = resolveTheme(lk.theme, lk.supporter, mqDark.matches);
  const night = t === 'sketchdark'; if (night) t = 'sketch';
  h.dataset.theme = t;
  if (night) h.dataset.night = ''; else delete h.dataset.night;
  h.dataset.accent = V.ACCENTS.some(([k]) => k === lk.accent) ? lk.accent : 'wine';
  h.dataset.brand = 'loop'; h.dataset.icons = lk.icons === 'duo' ? 'duo' : 'line';
  const font = ['system', 'geist', ...Object.keys(FONT_CSS)].includes(lk.font) ? lk.font : 'system';
  h.dataset.font = font; fontLink('font-title', FONT_CSS[font] ? `https://fonts.googleapis.com/css2?family=${FONT_CSS[font]}&display=swap` : '');
  const body = BODY_CSS[lk.bodyFont] ? lk.bodyFont : 'system';
  h.dataset.body = body; fontLink('font-body', BODY_CSS[body] ? `https://fonts.googleapis.com/css2?family=${BODY_CSS[body]}&display=swap` : '');
  V.setProgress(lk.progress); V.setIconStyle(lk.icons);
  const bg = getComputedStyle(h).getPropertyValue('--bg').trim();
  $('meta[name=theme-color]')?.setAttribute('content', bg || '#EDEFF2');
}
mqDark.addEventListener?.('change', () => render());

// ---------- State from the link ----------
function rebuild() {
  const raw = L.view();
  if (!raw) { s = null; return; }
  const keepActive = lookLocal.active;
  s = S.migrate(raw);
  s.onboarded = true;
  if (keepActive && s.vehicles.some((v) => v.id === keepActive)) s.active = keepActive;
  s.settings.rideChart = look().rideChart;
  V.setLocale(S.country(s).locale);
}

// ---------- Render ----------
function morphChildren(from, to) {
  const a = [...from.childNodes], b = [...to.childNodes];
  b.forEach((nb, i) => {
    const na = a[i];
    if (!na) { from.appendChild(nb); return; }
    if (na.nodeType !== nb.nodeType || na.nodeName !== nb.nodeName || (na.nodeType === 1 && na.id !== nb.id)) { from.replaceChild(nb, na); return; }
    if (na.nodeType !== 1) { if (na.nodeValue !== nb.nodeValue) na.nodeValue = nb.nodeValue; return; }
    for (const { name, value } of [...nb.attributes]) if (na.getAttribute(name) !== value) na.setAttribute(name, value);
    for (const { name } of [...na.attributes]) if (!nb.hasAttribute(name)) na.removeAttribute(name);
    if (na.tagName === 'INPUT' || na.tagName === 'TEXTAREA') { if (document.activeElement !== na && na.value !== (nb.getAttribute('value') ?? '')) na.value = nb.getAttribute('value') ?? ''; return; }
    if (na.tagName === 'SELECT') { na.innerHTML = nb.innerHTML; return; }
    if (na.namespaceURI === 'http://www.w3.org/2000/svg') { if (na.innerHTML !== nb.innerHTML) na.innerHTML = nb.innerHTML; return; }
    morphChildren(na, nb);
  });
  for (let i = a.length - 1; i >= b.length; i--) a[i].remove();
}
const setHTML = (el, h) => { if (el && el._h !== h) { el.innerHTML = h; el._h = h; } };
let shown = '';
function render() {
  applyTheme();
  const app = $('#app');
  const st = L.link.status;
  if (st === 'loading') return;
  if (st !== 'linked' || !s) {
    app.dataset.mode = 'gate';
    if (st === 'linked' && !s) { setHTML($('#main'), `<div class="gate"><div class="gt-wait">${V.loader('Waiting for your phone to send your data')}<p class="small muted">Keep Waylink open on ${V.esc(L.link.info?.device || 'your phone')} for a moment.</p><button class="btn line sm mt16" data-act="sheet" data-arg="unlink">Start over</button></div></div>`); shown = 'wait'; }
    else { setHTML($('#main'), P.gate(ctx(), qrSvg)); shown = 'gate'; }
    setHTML($('#bar'), ''); closePop();
    document.title = 'Link Waylink';
    return;
  }
  app.dataset.mode = 'app';
  const html = `<div class="wpage" data-page="${page}">${P[page](ctx())}</div>`;
  const main = $('#main');
  if (shown === page) { const tpl = document.createElement('template'); tpl.innerHTML = html; morphChildren(main, tpl.content); }
  else { main.innerHTML = html; main.scrollTop = 0; main.firstElementChild?.classList.add('fade-in'); shown = page; }
  setHTML($('#bar'), P.toolbar(ctx(), page));
  if (popKey) refreshPop();
  document.title = `${P.PAGES.find(([k]) => k === page)[1]} · Waylink`;
  if (page === 'fuel') wireChart();
  if (sheetKey) refreshSheet();
}
function go(to) { if (location.hash.slice(1) === to) navigate(to); else location.hash = to; }
function navigate(to) {
  // Old links to the Settings page open the Settings window instead.
  if (to === 'settings') { history.replaceState(null, '', '#' + page); render(); if (s) openPrefs(); return; }
  page = PAGE_KEYS.includes(to) ? to : 'overview'; closeSheet(); closePop(); render(); $('#main')?.focus({ preventScroll: true });
}
addEventListener('hashchange', () => navigate(location.hash.slice(1)));

// ---------- QR code ----------
async function newCode() {
  qrSvg = ''; render();
  const { default: qrcode } = await import('./vendor/qrcode.mjs');
  L.startPairing((text) => {
    if (navigator.webdriver) window.__wlQr = text; // lets the end-to-end test play the phone
    const q = qrcode(0, 'M'); q.addData(text); q.make();
    const n = q.getModuleCount(); let d = '';
    for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) if (q.isDark(y, x)) d += `M${x} ${y}h1v1h-1z`;
    qrSvg = `<svg viewBox="-4 -4 ${n + 8} ${n + 8}" role="img" aria-label="QR code to scan with the Waylink app" shape-rendering="crispEdges"><rect x="-4" y="-4" width="${n + 8}" height="${n + 8}" fill="#fff"/><path d="${d}" fill="#111"/></svg>`;
    render();
  });
}

// ---------- Sheets (a centred dialog on desktop, a bottom sheet on phones) ----------
const sheetHead = () => `${sheetStack.length ? `<button class="circle" data-act="sheet-back" aria-label="Back">${V.icon('back')}</button>` : ''}<div class="sheet-title"></div><button class="circle" data-act="close" aria-label="Close">${V.icon('x')}</button>`;
// Only the sheets that make sense here; anything else (pairing, ride mode, SOS…) is never opened.
const SHEETS = ['prefs', 'svc', 'svc-edit', 'rec', 'fuel', 'fuelprice', 'odo', 'trip-detail', 'trip-rename', 'garage', 'vehicle', 'specs', 'pick', 'pick-progress', 'help'];
function sheetHTML(key) {
  if (!s && key !== 'unlink') return null;
  if (key.startsWith('prefs')) return P.prefs(ctx(), key.split(':')[1]);
  const own = P.webSheet(ctx(), key); if (own != null) return own;
  if (!SHEETS.includes(key.split(':')[0])) return null;
  let h = V.sheetHTML({ s, link: { state: 'idle', log: [] }, live }, key);
  if (h == null) return h;
  // The web can't add or remove bikes, or take their photos: those stay on the phone.
  if (key.startsWith('garage')) h = h.replace(/<button class="btn ink block mt16" data-act="sheet" data-arg="vehicle:new">[\s\S]*?<\/button>/, '');
  if (key.startsWith('vehicle:')) h = h.replace(/<div class="field"><span class="eyebrow">Photo<\/span>[\s\S]*?<\/p><\/div>/, '').replace(/<button class="btn danger block mt8" type="button" data-act="veh-del"[\s\S]*?<\/button>/, '');
  if (key === 'fuelprice') h = h.replace(/<button class="btn line block mt12" type="button" data-act="fuel-locate"[\s\S]*?<\/button>/, '');
  if (key.startsWith('pick:')) h = h.replace(/Sets currency, fuel price and emergency numbers\./, 'Sets the currency and fuel price.');
  return h;
}
// Desktop dialogs: a form's Save and Delete move into a footer row (Delete left, Cancel and Save right),
// and each dialog gets a size that suits it (Settings is a wide two-pane window, confirmations are narrow).
function footer(root) {
  for (const f of $$('form[data-form]', root)) {
    if ($('.dlg-foot', f)) continue;
    const save = $(':scope > .btn[type=submit]', f); if (!save) continue;
    const del = $(':scope > .btn.danger', f);
    const foot = Object.assign(document.createElement('div'), { className: 'dlg-foot' });
    if (del) { del.classList.remove('block', 'mt8'); foot.appendChild(del); }
    const cancel = Object.assign(document.createElement('button'), { type: 'button', className: 'btn line', textContent: 'Cancel' });
    cancel.dataset.act = 'close';
    save.classList.remove('block', 'mt24', 'mt16');
    foot.append(Object.assign(document.createElement('span'), { className: 'grow' }), cancel, save);
    f.appendChild(foot);
  }
}
function dress(sheet, key) {
  const k = key.split(':')[0];
  if (k === 'prefs') { sheet.removeAttribute('aria-labelledby'); sheet.setAttribute('aria-label', 'Settings'); } else if (!sheet.hasAttribute('aria-labelledby')) { sheet.removeAttribute('aria-label'); sheet.setAttribute('aria-labelledby', 'sheet-title'); }
  sheet.dataset.kind = k === 'prefs' ? 'prefs' : ['unlink', 'how'].includes(k) ? 'narrow' : $('form[data-form]', sheet) ? 'form' : 'plain';
  footer($('.sheet-body', sheet));
}
function liftTitle(sheet) {
  const h = $('.sheet-body h2:not(.sr-only)', sheet), t = $('.sheet-title', sheet);
  if (!t) return;
  if (h && !h.closest('.card, .list')) { t.innerHTML = h.innerHTML; h.classList.add('lifted'); } else t.innerHTML = '';
  sheet.classList.toggle('has-title', !!t.innerHTML);
}
let lastFocus = null;
function openSheet(key, { push = true } = {}) {
  const html = sheetHTML(key);
  if (html == null) return;
  const host = $('#sheets');
  let sheet = $('.sheet', host);
  const open = sheet && !sheet.classList.contains('out');
  if (open && push && sheetKey && sheetKey !== key) sheetStack.push(sheetKey);
  if (!open) { sheetStack = []; lastFocus = document.activeElement; }
  sheetKey = key;
  if (open) {
    $('.sheet-head', sheet).innerHTML = sheetHead();
    const body = $('.sheet-body', sheet); body.innerHTML = html; body.scrollTop = 0;
  } else {
    host.innerHTML = `<div class="backdrop" data-act="close"></div><div class="sheet" role="dialog" aria-modal="true" aria-labelledby="sheet-title"><div class="sheet-head">${sheetHead()}</div><div class="sheet-body"></div></div>`;
    sheet = $('.sheet', host);
    $('.sheet-body', sheet).innerHTML = html;
  }
  dress(sheet, key);
  liftTitle(sheet); $('.sheet-title', sheet).id = 'sheet-title';
  closePop();
  $('#app').inert = false; $('#main').inert = true; $('#bar').inert = true;
  const af = $('[autofocus]', sheet) || $('input:not([type=hidden]), select, textarea', sheet) || $('.sheet-head .circle:last-child', sheet);
  setTimeout(() => af?.focus(), 60);
}
function refreshSheet() {
  if (!sheetKey) return;
  const html = sheetHTML(sheetKey);
  if (html == null) return closeSheet();
  const body = $('#sheets .sheet-body'); if (!body) return;
  if (body.contains(document.activeElement) && document.activeElement.matches('input, textarea, select')) return; // never pull a form out from under the keyboard
  const tmp = document.createElement('div'); tmp.innerHTML = html; footer(tmp); morphChildren(body, tmp); liftTitle($('#sheets .sheet'));
}
function sheetBack() { const prev = sheetStack.pop(); if (!prev) return closeSheet(); openSheet(prev, { push: false }); }
function closeSheet() {
  sheetKey = null; sheetStack = []; live.draftPhoto = undefined;
  for (const id of ['#main', '#bar']) { const el = $(id); if (el) el.inert = false; }
  const host = $('#sheets'), sh = $('.sheet', host);
  if (!sh) return;
  sh.classList.add('out'); $('.backdrop', host)?.classList.add('out');
  setTimeout(() => { if (sh.classList.contains('out')) host.innerHTML = ''; }, 260);
  if (lastFocus?.isConnected) lastFocus.focus({ preventScroll: true });
}

// ---------- Settings window ----------
let prefTab = 'phone';
function openPrefs(tab) { if (tab) prefTab = tab; openSheet('prefs:' + prefTab); }

// ---------- Popovers (bike switcher, sync status), anchored to their toolbar button ----------
let popKey = null, popAnchor = null;
function openPop(key, anchor) {
  if (popKey === key) return closePop();
  const html = P.popover(ctx(), key); if (!html) return;
  popKey = key; popAnchor = anchor;
  $('#pop').innerHTML = `<div class="pop" role="dialog" aria-label="${key === 'bike' ? 'Your bikes' : 'Sync'}" data-kind="${key}">${html}</div>`;
  anchor.setAttribute('aria-expanded', 'true');
  placePop();
  setTimeout(() => $('#pop .pop button')?.focus({ preventScroll: true }), 30);
}
function refreshPop() {
  const html = P.popover(ctx(), popKey), box = $('#pop .pop'); if (!html || !box) return closePop();
  const tmp = document.createElement('div'); tmp.innerHTML = html; morphChildren(box, tmp);
  popAnchor = $(`#bar [data-act="pop"][data-arg="${popKey}"]`) || popAnchor; popAnchor?.setAttribute('aria-expanded', 'true'); placePop();
}
function placePop() {
  const box = $('#pop .pop'); if (!box || !popAnchor?.isConnected) return;
  const r = popAnchor.getBoundingClientRect(), w = box.offsetWidth, h = box.offsetHeight;
  const below = r.bottom + 10 + h < innerHeight;
  box.style.left = Math.max(12, Math.min(innerWidth - w - 12, r.left + r.width / 2 - w / 2)) + 'px';
  box.style.top = (below ? r.bottom + 10 : Math.max(12, r.top - h - 10)) + 'px';
  box.dataset.side = below ? 'below' : 'above';
}
function closePop(refocus) {
  if (!popKey) return;
  const a = popAnchor; popKey = null; popAnchor = null;
  const box = $('#pop .pop'); if (box) { box.classList.add('out'); setTimeout(() => { if (!popKey) $('#pop').innerHTML = ''; }, 160); }
  a?.setAttribute('aria-expanded', 'false');
  if (refocus && a?.isConnected) a.focus({ preventScroll: true });
}
document.addEventListener('pointerdown', (e) => { if (popKey && !e.target.closest('#pop, [data-act="pop"]')) closePop(); });
addEventListener('resize', () => placePop());

// ---------- Toasts ----------
function toast(msg, ms = 2800) {
  const el = Object.assign(document.createElement('div'), { className: 'toast', textContent: msg });
  $('#toasts').appendChild(el);
  while ($('#toasts').children.length > 3) $('#toasts').firstElementChild.remove();
  setTimeout(() => { el.classList.add('out'); setTimeout(() => el.remove(), 260); }, ms);
}

// ---------- Changes: each becomes an op the phone applies ----------
async function change(ops, msg) {
  ops = (Array.isArray(ops) ? ops : [ops]).filter(Boolean);
  try {
    if (ops.length) await L.send(ops.length === 1 ? ops[0] : { op: 'batch', ops });
    if (msg) toast(msg);
  } catch (e) { if (e.message !== 'unlinked') toast(e.message || 'Couldn’t save that. Check your connection.', 4000); }
}
const vid = () => S.veh(s).id;
const item = (coll, id) => s[coll].find((x) => x.id === id);
const plain = (x) => JSON.parse(JSON.stringify(x));

const A = {
  go: (a) => go(a),
  sheet: (a) => openSheet(a),
  close: () => closeSheet(),
  pop: (k, el) => openPop(k, el),
  'close-pop': () => closePop(true),
  prefs: (tab) => { closePop(); openPrefs(tab); },
  'prefs-tab': (tab) => { prefTab = tab; sheetKey = 'prefs:' + tab; refreshSheet(); $(`#sheets .pf-i[data-arg="${tab}"]`)?.focus(); },
  'sheet-back': () => sheetBack(),
  'new-code': () => newCode(),
  'sync-now': async () => { await L.pull(true); toast(L.link.online ? 'Up to date.' : 'Offline. Showing saved data.'); },
  unlink: async () => { closeSheet(); await L.unlink(); toast('Unlinked. Your data is gone from this browser.'); },
  export: () => {
    const name = `waylink-${new Date().toISOString().slice(0, 10)}.json`;
    const url = URL.createObjectURL(new Blob([S.exportVault(s)], { type: 'application/json' }));
    const a = Object.assign(document.createElement('a'), { href: url, download: name });
    document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 4000);
    toast(`Saved ${name}`);
  },
  // Look: kept on this computer.
  'match-system': () => { const lk = look(); if (lk.theme === 'auto') { lookLocal.theme = resolveTheme('auto', lk.supporter, mqDark.matches); } else lookLocal.theme = 'auto'; saveLook(); render(); },
  theme: (k) => { lookLocal.theme = k; saveLook(); render(); },
  accent: (k) => { lookLocal.accent = k; saveLook(); render(); },
  'progress-style': (k) => { lookLocal.progress = V.PROGRESS_STYLES.some(([x]) => x === k) ? k : 'gauge'; saveLook(); render(); refreshSheet(); },
  'ride-chart': (k) => {
    lookLocal.rideChart = Math.min(V.RC_NAMES.length - 1, Math.max(0, +k || 0)); saveLook(); rebuild(); render();
    const rc = $('[data-rc]'); if (rc) rc.scrollTo({ left: lookLocal.rideChart * rc.clientWidth, behavior: 'smooth' });
  },
  'switch-vehicle': (id) => { lookLocal.active = id; saveLook(); rebuild(); closeSheet(); closePop(true); render(); toast(`Showing ${S.vehicleTitle(s)}.`); },
  // Service
  'svc-done': (id) => { const it = plain(item('service', id)); if (!it) return; S.markServiceDone(s, it); closeSheet(); change({ op: 'upsert', vid: vid(), coll: 'service', item: it }, `${it.name} · done at ${V.num(s.odo)} km`); },
  'svc-earlier': (arg) => {
    const [id, days] = arg.split('|'); const it = plain(item('service', id)); if (!it) return;
    const kmPerDay = S.periodStats(s.trips, Date.now() - 30 * DAY).km / 30;
    S.markServiceDone(s, it); it.lastDate = Date.now() - +days * DAY; it.lastKm = Math.max(0, Math.round(s.odo - kmPerDay * +days));
    closeSheet(); change({ op: 'upsert', vid: vid(), coll: 'service', item: it }, `${it.name} · logged ${+days === 1 ? 'yesterday' : 'last week'}`);
  },
  'svc-pick': (id) => {
    const box = $(`#svc-pick-${CSS.escape(id)}`); if (!box) return;
    const today = new Date().toISOString().slice(0, 10);
    box.innerHTML = `<div class="two"><label class="field"><span class="eyebrow">Date</span><input class="input" type="date" id="pick-date" max="${today}" value="${today}"></label><label class="field"><span class="eyebrow">At</span><div class="input-wrap"><input class="input" id="pick-km" type="number" inputmode="numeric" min="0" value="${Math.round(s.odo)}"><span class="suffix">km</span></div></label></div><button class="btn ink block mt12" data-act="svc-pick-save" data-arg="${V.esc(id)}">Save</button>`;
  },
  'svc-pick-save': (id) => {
    const it = plain(item('service', id)); if (!it) return;
    const d = new Date($('#pick-date').value).getTime(), km = +$('#pick-km').value;
    S.markServiceDone(s, it); if (!isNaN(d)) it.lastDate = d; if (km >= 0) it.lastKm = km;
    closeSheet(); change({ op: 'upsert', vid: vid(), coll: 'service', item: it }, 'Saved.');
  },
  'svc-del': (id) => { closeSheet(); change({ op: 'delete', vid: vid(), coll: 'service', itemId: id }, 'Reminder removed.'); },
  'rec-del': (id) => { closeSheet(); change({ op: 'delete', vid: vid(), coll: 'records', itemId: id }, 'Visit removed.'); },
  check: (id) => { const x = plain(item('checklist', id)); if (!x) return; x.done = !x.done; change({ op: 'upsert', vid: vid(), coll: 'checklist', item: x }); },
  'check-del': (id) => change({ op: 'delete', vid: vid(), coll: 'checklist', itemId: id }),
  // Fuel
  'fuel-del': (id) => { closeSheet(); change({ op: 'delete', vid: vid(), coll: 'fuel', itemId: id }, 'Fill-up removed.'); },
  'fuel-full': (_, el) => { el.classList.toggle('on'); el.setAttribute('aria-checked', el.classList.contains('on')); el.closest('form').full.value = el.classList.contains('on') ? 1 : 0; },
  'fuel-use': (p) => { const n = +p; if (!(n > 0)) return; change(s.vehicles.map((v) => ({ op: 'patch', vid: v.id, patch: { fuelPrice: n } })), `Petrol set to ${S.country(s).cur}${V.num(n, 2)} a litre.`); },
  'set-country': (c) => { closeSheet(); change({ op: 'patch', patch: { country: c } }); },
  // Rides
  'trip-gpx': (id) => {
    const t = item('trips', id); if (!t) return;
    const url = URL.createObjectURL(new Blob([S.toGPX(t)], { type: 'application/gpx+xml' }));
    const a = Object.assign(document.createElement('a'), { href: url, download: `waylink-ride-${t.n}.gpx` });
    document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 4000);
  },
  'trip-rename': (id) => openSheet('trip-rename:' + id),
  'trip-del': (id) => { closeSheet(); change({ op: 'delete', vid: vid(), coll: 'trips', itemId: id }, 'Ride deleted. The odometer is unchanged.'); },
  // Garage
  'veh-colour': (hex, el) => { const f = el.closest('form'); f.colour.value = hex; $$('.swatch', f).forEach((x) => x.classList.toggle('on', x === el)); vehPreview(f); },
  noop: () => {},
};

const FORMS = {
  field: (f) => {
    const key = f.dataset.arg, v = f.v.value;
    if (key === 'odo') { const n = +v; if (!(n >= 0) || v === '') return toast('Enter a number.'); closeSheet(); return change({ op: 'odo', vid: vid(), km: n }, 'Odometer saved.'); }
    if (key === 'fuelPrice') { const n = +v; if (!(n > 0)) return toast('Enter a price.'); closeSheet(); return change({ op: 'patch', vid: vid(), patch: { fuelPrice: n } }, 'Price saved.'); }
    if (key.startsWith('trip.')) { const t = plain(item('trips', key.slice(5))); if (!t) return; t.title = v.trim().slice(0, V.LIMITS.title) || t.title; closeSheet(); return change({ op: 'upsert', vid: vid(), coll: 'trips', item: t }); }
    closeSheet();
  },
  'svc-edit': (f) => {
    const isNew = f.dataset.arg === 'new';
    const it = isNew ? { id: S.uid(), kind: 'custom', icon: 'wrench' } : plain(item('service', f.dataset.arg));
    if (!it) return;
    it.name = f.name.value.trim().slice(0, V.LIMITS.item) || 'Reminder';
    if (it.milestone) it.targetKm = Math.max(0, +f.targetKm.value || 0);
    else {
      it.everyKm = Math.max(0, +f.everyKm.value || 0);
      it.everyDays = Math.round(Math.max(0, +f.everyMonths.value || 0) * 30.4);
      if (!it.everyKm && !it.everyDays) return toast('Set a distance or a time.');
    }
    it.lastKm = Math.max(0, +f.lastKm.value || 0);
    const d = new Date(f.lastDate.value).getTime(); it.lastDate = isNaN(d) ? Date.now() : d;
    closeSheet(); change({ op: 'upsert', vid: vid(), coll: 'service', item: it }, isNew ? 'Reminder added.' : 'Saved.');
  },
  rec: (f) => {
    const isNew = f.dataset.arg === 'new';
    const r = isNew ? { id: S.uid() } : plain(item('records', f.dataset.arg));
    if (!r) return;
    const title = f.title.value.trim(); if (!title) return toast('Say what was done.');
    Object.assign(r, { title: title.slice(0, V.LIMITS.title), date: new Date(f.date.value).getTime() || Date.now(), odo: Math.max(0, +f.odo.value || 0), workshop: f.workshop.value.trim().slice(0, V.LIMITS.place), cost: Math.max(0, +f.cost.value || 0), notes: f.notes.value.trim().slice(0, 2000) });
    closeSheet(); change({ op: 'upsert', vid: vid(), coll: 'records', item: r }, isNew ? 'Visit logged.' : 'Saved.');
  },
  fuel: (f) => {
    const odo = +f.odo.value, litres = +f.litres.value, price = +f.price.value;
    let cost = +f.cost.value; if (!cost && litres) cost = +(litres * price).toFixed(2);
    if (!(odo > 0) || !(litres > 0)) return toast('Odometer and litres, please.');
    if (litres > 60) return toast('That’s more than any bike tank holds.');
    const ts = (() => { const d = new Date(f.date.value); return isNaN(d) ? Date.now() : d.getTime(); })();
    const isNew = f.dataset.arg === 'new';
    const e = isNew ? { id: S.uid() } : plain(item('fuel', f.dataset.arg));
    if (!e) return;
    Object.assign(e, { odo, litres, cost, full: f.full.value === '1', ts });
    const ops = [{ op: 'upsert', vid: vid(), coll: 'fuel', item: e }];
    if (litres && cost) ops.push({ op: 'patch', vid: vid(), patch: { fuelPrice: +(cost / litres).toFixed(2) } });
    if (odo > s.odo) ops.push({ op: 'odo', vid: vid(), km: odo });
    closeSheet(); change(ops, isNew ? (e.full ? 'Logged. Full tanks give you mileage.' : 'Top-up logged.') : 'Saved.');
  },
  'check-add': (f) => { const t = f.text.value.trim().slice(0, V.LIMITS.title); if (!t) return; f.text.value = ''; change({ op: 'upsert', vid: vid(), coll: 'checklist', item: { id: S.uid(), text: t, done: false } }).then(() => setTimeout(() => $('[data-form="check-add"] input')?.focus(), 30)); },
  vehicle: (f) => {
    const v = s.vehicles.find((x) => x.id === f.dataset.arg); if (!v) return;
    const odo = +f.odo.value;
    if (!(odo >= 0) || f.odo.value === '') return toast('Add the odometer reading.');
    const patch = { model: f.model.value, nickname: f.nickname.value.trim().slice(0, V.LIMITS.nickname), plate: f.plate.value.trim().toUpperCase().slice(0, V.LIMITS.plate), colour: f.colour.value };
    const ops = [{ op: 'patch', vid: v.id, patch }];
    if (Math.abs(odo - v.odo) >= 0.1) ops.push({ op: 'odo', vid: v.id, km: odo });
    closeSheet(); change(ops, 'Saved.');
  },
};
const INPUTS = {
  'veh-model': (el) => vehPreview(el.form),
  'fuel-l': (el) => { const f = el.form; const p = +f.price.value; if (+el.value && p) f.cost.value = (+el.value * p).toFixed(2); },
  'fuel-c': (el) => { const f = el.form; const p = +f.price.value; if (+el.value && p) f.litres.value = (+el.value / p).toFixed(2); },
};
function vehPreview(f) {
  const cur = s.vehicles.find((x) => x.id === f.dataset.arg);
  const box = $('#veh-preview'); if (box) box.innerHTML = V.vehicleArt(s, { model: f.model.value, colour: f.colour.value, photo: cur?.photo }, 'hero');
}

document.addEventListener('click', (e) => {
  const el = e.target.closest('[data-act]');
  if (!el || el.disabled) return;
  const fn = A[el.dataset.act]; if (!fn) return;
  e.preventDefault();
  fn(el.dataset.arg, el, e);
});
document.addEventListener('submit', (e) => { const f = e.target.closest('form[data-form]'); if (!f) return; e.preventDefault(); FORMS[f.dataset.form]?.(f); });
document.addEventListener('input', (e) => { const el = e.target.closest('[data-input]'); if (el) INPUTS[el.dataset.input]?.(el); });
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && popKey && !sheetKey) { e.preventDefault(); closePop(true); return; }
  if (e.key === 'Escape' && sheetKey) { e.preventDefault(); if (sheetStack.length) sheetBack(); else closeSheet(); }
  // Keep Tab inside an open sheet.
  if (e.key === 'Tab' && sheetKey) {
    const f = $$('#sheets .sheet button, #sheets .sheet input:not([type=hidden]), #sheets .sheet select, #sheets .sheet textarea, #sheets .sheet a[href]').filter((x) => !x.disabled && x.offsetParent);
    if (!f.length) return;
    if (e.shiftKey && document.activeElement === f[0]) { e.preventDefault(); f[f.length - 1].focus(); }
    else if (!e.shiftKey && document.activeElement === f[f.length - 1]) { e.preventDefault(); f[0].focus(); }
  }
});
// Ride charts: swiping (or the tabs) picks which one shows; remembered here.
document.addEventListener('scroll', (e) => {
  const el = e.target; if (!el?.matches?.('[data-rc]')) return;
  clearTimeout(el._t); el._t = setTimeout(() => { const i = Math.round(el.scrollLeft / el.clientWidth); if (lookLocal.rideChart !== i) { lookLocal.rideChart = i; saveLook(); rebuild(); render(); } }, 300);
}, true);
function wireChart() {
  const box = $('#mileage-chart'); if (!box || box._w) return; box._w = true;
  const svg = $('svg', box), tip = $('.tip', box), xh = $('.xh', svg);
  if (!svg || !tip || !xh) return;
  svg.addEventListener('pointermove', (e) => {
    const r = e.target.closest('rect[data-tip]'); if (!r) return;
    const k = svg.clientWidth / svg.viewBox.baseVal.width;
    tip.textContent = r.dataset.tip; tip.style.left = (+r.dataset.x * k + svg.offsetLeft) + 'px'; tip.style.top = (+r.dataset.y * k + svg.offsetTop) + 'px'; tip.classList.add('show');
    xh.setAttribute('x1', r.dataset.x); xh.setAttribute('x2', r.dataset.x); xh.setAttribute('opacity', '.5');
  });
  svg.addEventListener('pointerleave', () => { tip.classList.remove('show'); xh.setAttribute('opacity', '0'); });
}
// Ride chart opens on the one picked.
new MutationObserver(() => { const rc = $('[data-rc]'); if (rc && !rc._set) { rc._set = true; rc.style.scrollBehavior = 'auto'; rc.scrollLeft = look().rideChart * rc.clientWidth; rc.style.scrollBehavior = ''; } }).observe(document.getElementById('main'), { childList: true, subtree: true });

// ---------- Boot ----------
let wasLinked = false;
L.onChange((lk) => {
  rebuild();
  if (lk.status === 'linked' && !wasLinked) { wasLinked = true; if (shown === 'gate' || shown === 'wait') shown = ''; }
  if (lk.status === 'unlinked' && wasLinked) { wasLinked = false; closeSheet(); newCode(); }
  render();
});
(async function boot() {
  try {
    await L.restore();
    if (L.link.status === 'unlinked') newCode();
    L.keepFresh();
  } catch (e) { console.error(e); toast('Something went wrong while starting. Reload the page.'); }
  finally { window.__waylinkReady = true; $('#boot')?.remove(); }
})();
