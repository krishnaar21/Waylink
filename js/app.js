// Waylink — controller: routing, sheets, sensors, bike link, ride mode, calls, navigation.
import { BikeLink } from './link.js';
import { CallState, maneuverByKey } from './protocol.js';
import * as S from './store.js';
import * as V from './views.js';
import * as SND from './sound.js';

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const DAY = 86400000;
const SCREENS = ['today', 'route', 'trips', 'alerts', 'settings', 'fuel', 'logbook', 'profile'];
const PUSHED = ['fuel', 'logbook', 'profile'];
const inFrame = (() => { try { return window.self !== window.top; } catch { return true; } })();

let s = S.load();
const link = new BikeLink();
// A short log of runtime errors, added to problem reports.
window.__wlLog = [];
const logErr = (m) => { try { window.__wlLog.push(`${new Date().toISOString().slice(0, 19)} ${String(m).slice(0, 240)}`); if (window.__wlLog.length > 30) window.__wlLog.shift(); } catch {} };
window.addEventListener('error', (e) => logErr(e.message + (e.filename ? ` @ ${e.filename.split('/').pop()}:${e.lineno}` : '')));
window.addEventListener('unhandledrejection', (e) => logErr('Promise: ' + (e.reason?.message || e.reason)));
const live = {
  speed: 0, pos: null, gpsErr: false, battery: null, engine: null,
  rec: null, nav: null, navSpeed: 1, ride: { open: false, auto: false, dismissedAt: 0 },
  call: null, relayName: 'Rohan', dest: '', turn: { maneuver: 'TURN_RIGHT', m: 400, street: '', exit: 2 },
  glove: [], gloveHere: [], docUrls: {}, draftPhoto: undefined,
};
let screen = 'today';
let sheetKey = null;
const ctx = () => ({ s, link, live });
const commit = () => { S.save(s); render(); };

// ---------- Theme ----------
const mqDark = matchMedia('(prefers-color-scheme: dark)');
const FONT_CSS = {
  onest: 'Onest:wght@400;500;600',
  geist: 'Geist:wght@400;500;600',
  garamond: 'EB+Garamond:wght@400;500;600',
  crimson: 'Crimson+Pro:wght@400;500;600',
  fraunces: 'Fraunces:opsz,wght@9..144,500;9..144,600',
};
const fontHref = (keys) => 'https://fonts.googleapis.com/css2?' + keys.map((k) => 'family=' + FONT_CSS[k]).join('&') + '&display=swap';
const BODY_CSS = { atkinson: 'Atkinson+Hyperlegible+Next:wght@400..700', public: 'Public+Sans:wght@400..700' };
function applyBody() {
  const k = BODY_CSS[s.settings.bodyFont] ? s.settings.bodyFont : 'system';
  document.documentElement.dataset.body = k;
  if (k === 'system') return;
  let el = $('#font-body'); if (!el) { el = Object.assign(document.createElement('link'), { rel: 'stylesheet', id: 'font-body' }); document.head.appendChild(el); }
  const href = `https://fonts.googleapis.com/css2?family=${BODY_CSS[k]}&display=swap`; if (el.getAttribute('href') !== href) el.setAttribute('href', href);
}
function applyFont() {
  applyBody();
  if (s.settings.font !== 'system' && !FONT_CSS[s.settings.font]) s.settings.font = 'system';
  const k = s.settings.font;
  document.documentElement.dataset.font = k;
  if (k === 'system') { $('#font-serif')?.remove(); return; }
  let el = $('#font-serif'); const href = fontHref([k]);
  if (!el) { el = Object.assign(document.createElement('link'), { rel: 'stylesheet', id: 'font-serif' }); document.head.appendChild(el); }
  if (el.getAttribute('href') !== href) el.setAttribute('href', href);
}
let allFontsLoaded = false;
function loadAllFonts() {
  if (allFontsLoaded) return; allFontsLoaded = true;
  document.head.appendChild(Object.assign(document.createElement('link'), { rel: 'stylesheet', href: fontHref(Object.keys(FONT_CSS)) }));
  document.head.appendChild(Object.assign(document.createElement('link'), { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?' + Object.values(BODY_CSS).map((f) => 'family=' + f).join('&') + '&display=swap' }));
}
function applyLogo() {
  const href = V.appIcon(s), name = V.appName(s);
  if (document.title !== name) document.title = name;
  $('meta[name=apple-mobile-web-app-title]')?.setAttribute('content', name);
  const cr = $('.sp-credit'); if (cr) cr.textContent = name.toUpperCase();
  const icons = { 'link[rel=icon]': href, 'link[rel=apple-touch-icon]': V.appIcon(s, true) };
  for (const [sel, h] of Object.entries(icons)) { const l = $(sel); if (l && l.getAttribute('href') !== h) l.setAttribute('href', h); }
}
function applyTheme() {
  applyFont(); applyLogo();
  SND.setEnabled(s.settings.sounds !== false);
  const t = s.settings.theme === 'auto' ? (mqDark.matches ? 'obsidian' : 'paper') : s.settings.theme;
  document.documentElement.dataset.theme = t;
  // Launch images follow the app's theme, not just the phone's, so a dark theme never opens on white.
  const mode = t === 'paper' || t === 'sketch' ? 'light' : 'dark';
  $$('link[rel=apple-touch-startup-image]').forEach((l) => { const h = l.getAttribute('href'), n = h.replace(/\/(light|dark)-/, `/${mode}-`); if (n !== h) l.setAttribute('href', n); });
  document.documentElement.dataset.accent = s.settings.accent; document.documentElement.dataset.brand = V.brandOf(s); document.documentElement.dataset.icons = s.settings.icons === 'duo' ? 'duo' : 'line';
  const bg = getComputedStyle(document.documentElement).getPropertyValue('--bg').trim();
  $('meta[name=theme-color]')?.setAttribute('content', bg || '#EDEFF2');
}
mqDark.addEventListener?.('change', applyTheme);

// ---------- Weather (Open-Meteo, no key). Cached for 30 minutes. ----------
const WX_KEY = 'waylink-weather';
try { const w = JSON.parse(localStorage.getItem(WX_KEY) || 'null'); if (w?.data) live.weather = { state: 'ok', data: w.data, key: w.key }; } catch {}
let wxBusy = false;
const fetchT = (url, ms = 10000) => { const a = new AbortController(); const t = setTimeout(() => a.abort(), ms); return fetch(url, { signal: a.signal }).finally(() => clearTimeout(t)); };
function wxTarget() {
  const at = s.profile.weatherAt;
  if (at?.lat != null) return { key: `${at.lat.toFixed(2)},${at.lon.toFixed(2)}`, ...at };
  const city = (s.profile.city || '').trim();
  return city ? { key: 'city:' + city.toLowerCase(), city } : null;
}
async function loadWeather(force = false) {
  const t = wxTarget();
  if (!t) { if (live.weather?.state !== 'need') { live.weather = { state: 'need' }; refreshWeather(); } return; }
  const w = live.weather;
  if (!force && w?.data && w.key === t.key && Date.now() - w.data.ts < 30 * 60e3) return;
  if (wxBusy) return; wxBusy = true;
  live.weather = { ...(w?.key === t.key ? w : {}), state: 'loading', key: t.key }; refreshWeather();
  try {
    let { lat, lon, place } = t;
    if (lat == null) {
      const g = await (await fetchT(`https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=${encodeURIComponent(t.city)}`)).json();
      const r = g?.results?.[0]; if (!r) throw new Error(`Couldn’t find “${t.city}”. Check the city in Profile.`);
      lat = r.latitude; lon = r.longitude; place = r.name;
    }
    const q = `latitude=${lat.toFixed(3)}&longitude=${lon.toFixed(3)}&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day&hourly=temperature_2m,precipitation_probability,weather_code,is_day&forecast_hours=13&timezone=auto&timeformat=unixtime&wind_speed_unit=kmh`;
    const res = await fetchT('https://api.open-meteo.com/v1/forecast?' + q);
    if (!res.ok) throw new Error('The weather service didn’t answer.');
    const data = S.parseWeather(await res.json(), place);
    live.weather = { state: 'ok', data, key: t.key };
    try { localStorage.setItem(WX_KEY, JSON.stringify({ data, key: t.key })); } catch {}
  } catch (e) {
    const net = e?.name === 'TypeError' || e?.name === 'AbortError';
    const msg = net && inFrame ? 'This preview can’t reach the internet. Weather works when you open Waylink from its own link or your Home Screen.'
      : net ? 'Couldn’t reach the weather service. Check your connection.' : e?.message || 'Couldn’t reach the weather service.';
    live.weather = { ...(live.weather?.data ? live.weather : {}), state: 'err', key: t.key, msg };
  } finally { wxBusy = false; refreshWeather(); }
}
function refreshWeather() {
  if (sheetKey === 'weather') refreshSheet();
  if (screen !== 'today') return;
  const h = $('.view .bc-wx'); if (h) h.innerHTML = V.weatherHead(ctx());
}

// ---------- Pull to refresh ----------
// Pull down at the top of any tab: a motorcycle wheel (knobby tyre, alloy spokes) draws and rolls as you pull, then spins.
const PTR_MARK = () => '<svg viewBox="0 0 100 100" aria-hidden="true"><g class="wh"><circle class="band" cx="50" cy="50" r="38"/><circle class="tyre" cx="50" cy="50" r="44.5" pathLength="100" transform="rotate(-90 50 50)"/><circle class="rim" cx="50" cy="50" r="31.5"/><path class="tread" d="M83.5 50.0L91.9 57.2M82.1 59.4L88.2 68.7M78.2 68.1L81.4 78.7M71.9 75.3L72.0 86.4M63.9 80.5L60.9 91.1M54.8 83.2L48.8 92.5M45.2 83.2L36.9 90.4M36.1 80.5L26.1 85.1M28.1 75.3L17.1 76.9M21.8 68.1L10.9 66.6M17.9 59.4L7.8 54.9M16.5 50.0L8.1 42.8M17.9 40.6L11.8 31.3M21.8 31.9L18.6 21.3M28.1 24.7L28.0 13.6M36.1 19.5L39.1 8.9M45.2 16.8L51.2 7.5M54.8 16.8L63.1 9.6M63.9 19.5L73.9 14.9M71.9 24.7L82.9 23.1M78.2 31.9L89.1 33.4M82.1 40.6L92.2 45.1"/></g></svg>';
(function pullToRefresh() {
  const host = $('#views'); if (!host) return;
  const ind = Object.assign(document.createElement('div'), { id: 'ptr', innerHTML: PTR_MARK() });
  document.querySelector('.app')?.appendChild(ind);
  const TRIGGER = 72, HOLD = 58;
  let y0 = null, x0 = 0, d = 0, pulling = false, busy = false, armed = false, view = null;
  const set = (px, anim) => {
    if (!view) return;
    view.style.animation = 'none';
    view.style.transition = ind.style.transition = anim ? 'transform .42s cubic-bezier(.2,.9,.25,1.15), opacity .3s' : 'none';
    view.style.transform = px ? `translateY(${px}px)` : '';
    const p = Math.min(1, px / TRIGGER);
    ind.style.transform = `translate(-50%, ${px * 0.55 - 34}px)`;
    ind.style.opacity = px ? Math.min(1, p * 1.4) : 0;
    if (!busy) { ind.style.setProperty('--draw', String(100 - p * 100)); ind.style.setProperty('--roll', `${px * 3}deg`); }
  };
  // If the page re-renders mid-pull, the finger's touchend goes to a detached node and never reaches us.
  // A watchdog lets the pull go if the finger has been still for a moment, and a new touch always starts clean.
  let dog = 0;
  const watch = () => { clearTimeout(dog); dog = setTimeout(() => { if (pulling) end(); }, 700); };
  host.addEventListener('touchstart', (e) => {
    if (pulling && !busy) { pulling = false; set(0, true); }
    view = host.firstElementChild;
    if (busy || !view || view.scrollTop > 0 || sheetKey || e.touches.length > 1 || !s.onboarded) { y0 = null; return; }
    y0 = e.touches[0].clientY; x0 = e.touches[0].clientX; pulling = false; armed = false;
  }, { passive: true });
  host.addEventListener('touchmove', (e) => {
    if (y0 == null || busy) return;
    const dy = e.touches[0].clientY - y0, dx = e.touches[0].clientX - x0;
    if (!pulling) { if (dy < 6 || Math.abs(dx) > dy || view.scrollTop > 0) { if (dy < 0 || Math.abs(dx) > 10) y0 = null; return; } pulling = true; }
    e.preventDefault();
    d = Math.max(0, 120 * (1 - Math.exp(-Math.max(0, dy) / 190)));
    if (d >= TRIGGER && !armed) { armed = true; buzz(6); }
    else if (d < TRIGGER) armed = false;
    set(d, false); watch();
  }, { passive: false });
  const end = async () => {
    clearTimeout(dog);
    if (!pulling) { y0 = null; return; }
    pulling = false; y0 = null;
    if (d < TRIGGER) { set(0, true); return; }
    busy = true; ind.classList.add('spin'); set(HOLD, true);
    const t0 = Date.now();
    try {
      navigator.serviceWorker?.getRegistration?.().then((r) => r?.update()).catch(() => {});
      if (screen === 'today') await loadWeather(true);
    } catch {}
    await new Promise((r) => setTimeout(r, Math.max(0, 900 - (Date.now() - t0))));
    ind.classList.remove('spin'); ind.classList.add('done');
    set(0, true);
    setTimeout(() => { ind.classList.remove('done'); busy = false; render(); }, 380);
  };
  host.addEventListener('touchend', end); host.addEventListener('touchcancel', end);
  window.addEventListener('touchend', () => { if (pulling) end(); }, true);
})();

// FAQ: one answer open at a time (the name attribute does this in new browsers; this covers the rest).
document.addEventListener('toggle', (e) => { const d = e.target; if (d?.matches?.('details.faq-i') && d.open) $$('details.faq-i[open]').forEach((o) => { if (o !== d) o.open = false; }); }, true);

// ---------- Render ----------
// A small DOM patcher: brings `to` into `from` node by node, touching only what differs.
function morphChildren(from, to) {
  const a = [...from.childNodes], b = [...to.childNodes];
  b.forEach((nb, i) => {
    const na = a[i];
    if (!na) { from.appendChild(nb); return; }
    if (na.nodeType !== nb.nodeType || na.nodeName !== nb.nodeName || (na.nodeType === 1 && na.id !== nb.id)) { from.replaceChild(nb, na); return; }
    if (na.nodeType !== 1) { if (na.nodeValue !== nb.nodeValue) na.nodeValue = nb.nodeValue; return; }
    for (const { name, value } of [...nb.attributes]) if (na.getAttribute(name) !== value) na.setAttribute(name, value);
    for (const { name } of [...na.attributes]) if (!nb.hasAttribute(name)) na.removeAttribute(name);
    if (na.tagName === 'INPUT' || na.tagName === 'TEXTAREA') { if (document.activeElement !== na && na.value !== (nb.getAttribute('value') ?? '')) na.value = nb.getAttribute('value') ?? ''; if (na.type === 'checkbox' || na.type === 'radio') na.checked = nb.hasAttribute('checked'); return; }
    if (na.tagName === 'SELECT') { na.innerHTML = nb.innerHTML; return; }
    if (na.tagName === 'svg' || na.namespaceURI === 'http://www.w3.org/2000/svg') { if (na.innerHTML !== nb.innerHTML) na.innerHTML = nb.innerHTML; return; }
    morphChildren(na, nb);
  });
  for (let i = a.length - 1; i >= b.length; i--) a[i].remove();
}
function render(anim = '') {
  applyTheme();
  const vid = S.veh(s).id;
  live.gloveHere = live.glove.filter((d) => d.slot.startsWith(vid + ':') || d.slot.startsWith('rider:'));
  V.setLocale(S.country(s).locale); V.setProgress(s.settings.progress); V.setIconStyle(s.settings.icons);
  const views = $('#views');
  if (!s.onboarded || live.onbTest) {
    views.innerHTML = V.onboarding(s, live.onbStep || 0, link);
    for (const id of ['#tabbar', '#fab', '#pill']) { const el = $(id); el.innerHTML = ''; el._h = ''; }
    return;
  }
  const prev = views.firstElementChild;
  const same = prev && prev.dataset.screen === screen;
  let html = V[screen](ctx());
  if (same) {
    // Same screen: patch only what changed, so toggles and edits don't flash the page or reload pictures.
    html = html.replace('class="stagger"', 'class=""');
    const tpl = document.createElement('template'); tpl.innerHTML = html;
    morphChildren(prev, tpl.content);
    $('.pagehead', prev)?.classList.toggle('stuck', prev.scrollTop > 6);
  } else {
    views.innerHTML = `<main class="view ${anim || 'fade-in'}" data-screen="${screen}">${html}</main>`;
    const vEl = views.firstElementChild;
    const ph = () => $('.pagehead', vEl);
    const upd = () => ph()?.classList.toggle('stuck', vEl.scrollTop > 6); upd(); vEl.addEventListener('scroll', upd, { passive: true });
    // The FAB steps aside while you scroll and comes back when you stop.
    let fabT; vEl.addEventListener('scroll', () => { const f = $('#fab'); f.classList.add('away'); clearTimeout(fabT); fabT = setTimeout(() => f.classList.remove('away'), 420); }, { passive: true });
  }
  const setHTML = (el, h) => { if (el && el._h !== h) { el.innerHTML = h; el._h = h; } };
  setHTML($('#tabbar'), V.tabbar(ctx(), screen));
  setHTML($('#fab'), screen === 'today' ? `<button class="fab" data-act="sheet" data-arg="quick" aria-label="Quick actions">${V.icon('plus')}</button>`
    : '');
  renderPill(); fitArch();
  renderOverlay();
  if (screen === 'fuel') wireChart();
  if (screen === 'today' && s.onboarded) loadWeather();
  if (screen === 'settings') { loadAllFonts(); }
  sizeTracks();
  syncOrient();
}
// Portrait everywhere except Route and ride mode. Android honours the lock in standalone mode;
// iOS ignores it, so the CSS cover (.rotate-lock) does the work there.
function syncOrient() {
  const free = live.ride.open || screen === 'route';
  document.documentElement.classList.toggle('land-ok', !!free);
  const o = screen && window.screen?.orientation;
  try { if (o?.lock) { if (free) o.unlock?.(); else o.lock('portrait').catch(() => {}); } } catch {}
}
function sizeTracks() { $$('.track').forEach((el) => el.style.setProperty('--w', el.offsetWidth + 'px')); }
addEventListener('resize', sizeTracks);
function renderPill() {
  const n = live.nav, r = live.rec;
  const show = (n || r) && !live.ride.open && s.onboarded && !(n ? screen === 'route' : screen === 'trips' || screen === 'today');
  $('#app').classList.toggle('has-pill', !!show);
  // The pill lives in the page header, on the right of the title block.
  const head = $('.view .pagehead');
  const sub = !!(head && $('.topbar', head));
  if (sub) { $('.ph-pill', head)?.remove(); $('#pill').innerHTML = ''; $('#app').classList.remove('has-pill'); return; }
  let slot = head && $('.ph-pill', head);
  if (head && !slot) {
    slot = Object.assign(document.createElement('div'), { className: 'ph-pill' });
    const bar = $('.topbar', head);
    if (bar) bar.insertBefore(slot, bar.lastElementChild); else head.insertBefore(slot, head.children[1] || null);
  }
  const host = slot || $('#pill');
  if (!show) { $('#pill').innerHTML = ''; if (slot) slot.innerHTML = ''; return; }
  if (slot) $('#pill').innerHTML = '';
  let html;
  if (n) {
    const d = V.dist(n.stepMeters);
    html = `<button class="live-pill" data-act="tab" data-arg="route">${r ? '<span class="dot x pulse"></span>' : ''}${V.arrow(maneuverByKey(n.maneuver).glyph, 0, .5).replace('<svg', '<svg style="--accent:#fff"')}<span data-live="pill">${d.v} ${d.u}</span></button>`;
  } else {
    html = `<button class="live-pill rec" data-act="tab" data-arg="trips"><span class="dot x pulse"></span><span data-live="pill-rec">${V.num(r.km, 1)} km · ${V.clock((Date.now() - r.start) / 1000)}</span></button>`;
  }
  const cur = $('.live-pill', host);
  if (!cur) host.innerHTML = html;
  else cur.outerHTML = html.replace('class="live-pill', 'style="animation:none" class="live-pill');
}
function renderOverlay() {
  const o = $('#overlay');
  if (live.call) o.innerHTML = V.callOverlay(ctx());
  else if (live.ride.open) { if (!$('#ride-mode')) { o.innerHTML = V.rideMode(ctx()); wireHold(); } }
  else o.innerHTML = '';
}
function refreshRide() { if (live.ride.open && !live.call) { $('#overlay').innerHTML = V.rideMode(ctx()); wireHold(); } }

function navigate(to, anim) {
  if (!SCREENS.includes(to)) to = 'today';
  const pushed = PUSHED.includes(to) && !PUSHED.includes(screen);
  screen = to;
  render(anim || (pushed ? 'push-in' : 'fade-in'));
}
window.addEventListener('hashchange', () => navigate(location.hash.slice(1)));
function go(to) { if (location.hash.slice(1) === to) navigate(to); else location.hash = to; }

// ---------- Live ticks ----------
function setLive(key, val) { $$(`[data-live="${key}"]`).forEach((el) => { if (el.innerHTML !== String(val)) el.innerHTML = val; }); }
function updateRideGauges() {
  const f = Math.max(0, Math.min(1, live.speed / V.maxKmh(S.model(s))));
  const arc = $('#rm-arc'); if (arc) arc.setAttribute('stroke-dasharray', `${(arc.dataset.len * f).toFixed(1)} ${arc.dataset.len}`);
  const nd = $('#rm-needle'); if (nd) nd.style.transform = `rotate(${(f * 180).toFixed(1)}deg)`;
  const hb = $('#rm-hud'); if (hb) hb.style.width = (f * 100).toFixed(1) + '%';
  const df = $('#rm-dfill'), dr = $('#rm-drim'), dn = $('#rm-dneedle');
  if (df) df.setAttribute('stroke-dasharray', `${(f * 100).toFixed(2)} 100`);
  if (dr) dr.setAttribute('stroke-dasharray', `${(f * 100).toFixed(2)} 100`);
  if (dn) dn.style.transform = `rotate(${(f * 240).toFixed(1)}deg)`;
}
function tickLive() {
  updateRideGauges();
  const sb = $('#sb-time'); if (sb) sb.textContent = new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  setLive('speed', V.num(live.speed));
  setLive('odo', V.num(Math.floor(s.odo)));
  if (live.engine != null) setLive('engine', live.engine);
  setLive('clock', new Date().toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' }));
  if (live.rec) { setLive('pill-rec', `${V.num(live.rec.km, 1)} km · ${V.clock((Date.now() - live.rec.start) / 1000)}`); setLive('rec-km', V.num(live.rec.km, live.ride.open ? 1 : 2)); setLive('rec-time', V.clock((Date.now() - live.rec.start) / 1000)); setLive('rec-top', V.num(live.rec.top)); }
  if (live.call && live.call.state === 'active') setLive('call-time', V.clock((Date.now() - live.call.since) / 1000));
  const n = live.nav;
  if (n) {
    const d = V.dist(n.stepMeters), t = V.dist(n.totalMeters);
    setLive('nav-dist', d.v); setLive('nav-unit', d.u); setLive('rm-dist', d.v); setLive('rm-unit', d.u);
    setLive('nav-total', `${t.v} ${t.u}`); setLive('nav-eta', V.tShort(n.eta)); setLive('pill', `${d.v} ${d.u}`);
    setLive('nav-raw', `${V.num(Math.round(n.stepMeters))} m`);
  }
}

// ---------- Sheets ----------
let splashTpl = '';
let realState = null;
// In-app loading screen: the turn signs take turns, with a line of text under them.
function showLoading(text = 'Loading', ms = 0) {
  $('#loading')?.remove();
  const el = Object.assign(document.createElement('div'), { id: 'loading', className: 'loading-screen', innerHTML: V.loader(text) });
  $('#app').appendChild(el);
  const hide = () => { if (!el.isConnected) return; el.classList.add('out'); setTimeout(() => el.remove(), 320); };
  el.addEventListener('click', hide);
  if (ms) setTimeout(hide, ms);
  return hide;
}
// The Loop wordmark: the arch from the w lands on the y, wherever the font puts it.
function fitArch(root = document) {
  for (const w of root.querySelectorAll('.wl-arch')) {
    const p = w.querySelector('.lw'), y = w.querySelector('.ty1'), rest = w.querySelector('.rest');
    const em = parseFloat(getComputedStyle(w).fontSize); if (!p || !y || !rest || !em) continue;
    const yx = rest.offsetLeft + y.offsetLeft + y.offsetWidth * 0.2; if (!yx) continue;
    const u = 100 / (0.98 * em), X = Math.round(yx * u * 10) / 10;
    p.setAttribute('d', p.getAttribute('d').replace(/C92 -?[\d.]+ [\d.]+ -?[\d.]+ [\d.]+ [\d.]+$/, `C92 -44 ${X} -44 ${X} 24`));
  }
}
function showLoader() {
  if (!splashTpl) return;
  SND.engineStart();
  $('#splash')?.remove(); document.documentElement.classList.remove('no-splash');
  $('#app').insertAdjacentHTML('beforeend', splashTpl);
  const sp = $('#splash'); fitArch(sp);
  const hide = () => { if (!sp.isConnected) return; sp.classList.add('out'); setTimeout(() => sp.remove(), 600); };
  sp.addEventListener('click', hide);
  setTimeout(hide, 6000);
}
let sheetStack = [];
const sheetHead = () => `${sheetStack.length ? `<button class="circle" data-act="sheet-back" aria-label="Back">${V.icon('back')}</button>` : ''}<div class="sheet-title"></div><button class="circle" data-act="close" aria-label="Close">${V.icon('x')}</button>`;
// The sheet's title lives in the head bar, beside back and close, so scrolled content passes under it cleanly.
function liftTitle(sheet) {
  const h = $('.sheet-body h2:not(.sr-only)', sheet), t = $('.sheet-title', sheet);
  if (!t) return;
  if (h && !h.closest('.card, .list, .about-hero')) { t.innerHTML = h.innerHTML; h.classList.add('lifted'); } else t.innerHTML = '';
  sheet.classList.toggle('has-title', !!t.innerHTML);
}
function openSheet(key, rawHTML, { push = true } = {}) {
  const html = rawHTML ?? V.sheetHTML(ctx(), key);
  if (html == null) return;
  const host = $('#sheets');
  let sheet = $('.sheet', host);
  const open = sheet && !sheet.classList.contains('out');
  if (open && push && sheetKey && sheetKey !== key && !rawHTML) sheetStack.push(sheetKey);
  if (!open) sheetStack = [];
  sheetKey = key;
  if (open) {
    const body = $('.sheet-body', sheet);
    $('.sheet-head', sheet).innerHTML = sheetHead();
    body.innerHTML = html; body.scrollTop = 0; liftTitle(sheet);
    body.animate([{ opacity: 0, transform: `translateX(${push ? 18 : -18}px)` }, { opacity: 1, transform: 'none' }], { duration: 280, easing: 'cubic-bezier(.32,.72,0,1)' });
  } else {
    host.innerHTML = `<div class="backdrop" data-act="close"></div><div class="sheet" role="dialog" aria-modal="true"><div class="grab"></div><div class="sheet-head">${sheetHead()}</div><div class="sheet-body">${html}</div></div>`;
    sheet = $('.sheet', host);
    liftTitle(sheet);
    wireDrag(sheet);
  }
  const af = $('[autofocus]', sheet); if (af) setTimeout(() => af.focus(), 350);
  fitArch();
}
function sheetBack() {
  const prev = sheetStack.pop();
  if (!prev) return closeSheet();
  if (prev.startsWith('vehicle:')) live.draftPhoto = undefined;
  openSheet(prev, undefined, { push: false });
}
function refreshSheet() {
  if (!sheetKey) return;
  const html = V.sheetHTML(ctx(), sheetKey);
  const body = $('#sheets .sheet-body');
  if (html == null) return closeSheet();
  // Patch in place so images and animations aren't rebuilt (no height jump, no replay).
  if (body) { const st = body.scrollTop, tmp = document.createElement('div'); tmp.innerHTML = html; morphChildren(body, tmp); body.scrollTop = st; liftTitle($('#sheets .sheet')); }
  fitArch();
}
function closeSheet() {
  sheetKey = null; sheetStack = [];
  const host = $('#sheets');
  const sh = $('.sheet', host), bd = $('.backdrop', host);
  if (!sh) return;
  sh.classList.add('out'); bd?.classList.add('out');
  setTimeout(() => { if (sh.classList.contains('out')) host.innerHTML = ''; }, 300);
}
function wireDrag(sheet) {
  const grab = $('.grab', sheet);
  let y0 = null, dy = 0, t0 = 0;
  const down = (e) => { y0 = e.clientY; dy = 0; t0 = performance.now(); sheet.style.transition = 'none'; grab.setPointerCapture?.(e.pointerId); };
  const move = (e) => { if (y0 == null) return; dy = Math.max(0, e.clientY - y0); sheet.style.transform = `translateY(${dy}px)`; };
  const up = () => {
    if (y0 == null) return;
    const v = dy / Math.max(1, performance.now() - t0);
    y0 = null; sheet.style.transition = 'transform .35s cubic-bezier(.32,.72,0,1)';
    if (dy > 110 || v > 0.6) { sheet.style.transform = 'translateY(100%)'; setTimeout(closeSheet, 200); }
    else sheet.style.transform = '';
  };
  grab.addEventListener('pointerdown', down); grab.addEventListener('pointermove', move);
  grab.addEventListener('pointerup', up); grab.addEventListener('pointercancel', up);
}

// ---------- Toasts ----------
function toast(msg, ms = 2600) {
  const el = document.createElement('div');
  el.className = 'toast'; el.textContent = msg;
  $('#toasts').appendChild(el);
  while ($('#toasts').children.length > 3) $('#toasts').firstElementChild.remove();
  setTimeout(() => { el.classList.add('out'); setTimeout(() => el.remove(), 260); }, ms);
}
const buzz = (p = 8) => { try { navigator.vibrate?.(p); } catch {} };

// ---------- FLIP for reordering lists ----------
function flip(fn) {
  const before = new Map($$('[data-flip]').map((el) => [el.dataset.flip, el.getBoundingClientRect().top]));
  fn();
  requestAnimationFrame(() => {
    $$('[data-flip]').forEach((el) => {
      const t = before.get(el.dataset.flip);
      if (t == null) return;
      const dy = t - el.getBoundingClientRect().top;
      if (Math.abs(dy) < 1) return;
      el.animate([{ transform: `translateY(${dy}px)` }, { transform: 'none' }], { duration: 520, easing: 'cubic-bezier(.32,.72,0,1)' });
    });
  });
}

// ---------- Files ----------
function download(name, text, mime) {
  if (inFrame) { // downloads are blocked inside embedded previews; offer the text instead
    openSheet('text', `<div class="eyebrow">Export</div><h2>${V.esc(name)}</h2><p class="lead">Downloads are blocked in this preview. Copy the text instead, or open ${V.appName(s)} on its own page.</p><textarea class="input mt16" id="backup-text" readonly style="height:180px;font-family:var(--mono);font-size:11px">${V.esc(text)}</textarea><button class="btn ink block mt12" data-act="copy-text">${V.icon('copy')} Copy</button>`);
    return;
  }
  const url = URL.createObjectURL(new Blob([text], { type: mime }));
  const a = Object.assign(document.createElement('a'), { href: url, download: name });
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
  toast(`Saved ${name}`);
}
async function loadGlove() {
  Object.values(live.docUrls).forEach((u) => URL.revokeObjectURL(u));
  live.glove = await S.gloveList();
  live.docUrls = Object.fromEntries(live.glove.map((d) => [d.slot, URL.createObjectURL(d.blob)]));
}

// ---------- GPS ----------
let watchId = null, simTimer = null, lastFix = null, fastSince = null, gpsWarned = false;
const sim = { lat: 12.9716, lng: 77.5946, head: 0.6, v: 0, t: 0 };
function needGeo() { return link.connected || !!live.rec || live.ride.open; }
function ensureGeo() {
  const want = needGeo();
  const useSim = link.simulated && link.state !== 'idle';
  if (want && useSim) {
    if (watchId != null) { navigator.geolocation.clearWatch(watchId); watchId = null; }
    if (!simTimer) simTimer = setInterval(simStep, 1000);
  } else if (want) {
    clearInterval(simTimer); simTimer = null;
    if (watchId == null && navigator.geolocation) {
      watchId = navigator.geolocation.watchPosition(
        (p) => onFix(p.coords.latitude, p.coords.longitude, p.coords.speed, p.coords.accuracy, p.timestamp),
        (e) => { live.gpsErr = true; if (!gpsWarned && live.rec) { gpsWarned = true; toast(e.code === 1 ? 'Location is blocked. Allow it to record rides, or use the simulated bike.' : 'No GPS fix yet. Keep the phone where it can see the sky.', 4200); } },
        { enableHighAccuracy: true, maximumAge: 1000, timeout: 20000 });
    } else if (!navigator.geolocation) live.gpsErr = true;
  } else {
    if (watchId != null) { navigator.geolocation.clearWatch(watchId); watchId = null; }
    clearInterval(simTimer); simTimer = null;
    if (!link.simulated) live.engine = null;
    live.speed = 0; fastSince = null;
  }
}
function simStep() {
  sim.t += 1;
  const target = Math.max(0, 42 + 18 * Math.sin(sim.t / 23) + 8 * Math.sin(sim.t / 7) - (sim.t % 90 > 82 ? 60 : 0));
  sim.v += (target - sim.v) * 0.25;
  sim.head += (Math.sin(sim.t / 11) * 0.12);
  const m = sim.v / 3.6;
  sim.lat += (Math.cos(sim.head) * m) / 111320;
  sim.lng += (Math.sin(sim.head) * m) / (111320 * Math.cos(sim.lat * Math.PI / 180));
  live.engine = Math.round(80 + 6 * Math.sin(sim.t / 40) + sim.v / 25);
  onFix(sim.lat, sim.lng, m, 5, Date.now());
}
function onFix(lat, lng, speedMs, acc, ts) {
  live.gpsErr = false;
  live.pos = [lat, lng];
  let kmh = speedMs != null && !isNaN(speedMs) ? speedMs * 3.6 : null;
  if (lastFix) {
    const d = S.haversine([lastFix.lat, lastFix.lng], [lat, lng]);
    const dt = (ts - lastFix.ts) / 1000;
    if (kmh == null && dt > 0) kmh = (d / dt) * 3.6;
    if (live.rec && acc <= 40 && dt > 0 && (d / dt) * 3.6 < 220 && d > 2) {
      live.rec.km += d / 1000;
      const lp = live.rec.pts[live.rec.pts.length - 1];
      if (!lp || S.haversine(lp, [lat, lng]) >= 12) live.rec.pts.push([+lat.toFixed(6), +lng.toFixed(6), ts]);
    }
  } else if (live.rec && !live.rec.pts.length) live.rec.pts.push([+lat.toFixed(6), +lng.toFixed(6), ts]);
  lastFix = { lat, lng, ts };
  live.speed = Math.max(0, Math.round(kmh || 0));
  if (live.rec) live.rec.top = Math.max(live.rec.top, live.speed);
  // Ride mode opens by itself after 5 s above 15 km/h
  if (s.settings.autoRide && !live.ride.open && !live.call && document.visibilityState === 'visible' && s.onboarded) {
    if (live.speed >= 15) { fastSince ??= Date.now(); if (Date.now() - fastSince >= 5000 && Date.now() - live.ride.dismissedAt > 120000) openRide(true); }
    else fastSince = null;
  }
  tickLive();
}

// ---------- Rides ----------
function startRecording(auto = false) {
  if (live.rec) return;
  lastFix = null;
  live.rec = { start: Date.now(), km: 0, top: 0, pts: [], auto };
  SND.revUp();
  ensureGeo();
  if (!auto) toast('Recording. Ride safe.');
  render();
}
let autoStopTimer = null;
function stopRecording(keep = true) {
  const r = live.rec;
  if (!r) return;
  live.rec = null;
  SND.engineOff();
  clearTimeout(autoStopTimer);
  if (keep && r.km >= 0.1) {
    s.rideNo = (s.rideNo || s.trips.length) + 1;
    const end = Date.now(), sec = Math.max(1, (end - r.start) / 1000);
    s.trips.unshift({ id: S.uid(), n: s.rideNo, start: r.start, end, km: +r.km.toFixed(2), sec, top: r.top, avg: r.km / (sec / 3600), title: S.tripTitle(r.start), pts: r.pts });
    S.addDistance(s, r.km);
    toast(`Saved · ${V.num(r.km, 1)} km`);
  } else toast(keep ? 'Too short to keep.' : 'Ride discarded.');
  ensureGeo();
  commit();
}

// ---------- Ride mode ----------
let wakeLock = null;
async function keepAwake(on) {
  try {
    if (on && s.settings.wakeLock && navigator.wakeLock) wakeLock = await navigator.wakeLock.request('screen');
    else if (!on && wakeLock) { await wakeLock.release(); wakeLock = null; }
  } catch {}
}
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible' && live.ride.open) keepAwake(true); });
function openRide(auto = false) {
  closeSheet();
  live.ride = { open: true, auto, dismissedAt: live.ride.dismissedAt };
  fastSince = null;
  ensureGeo(); keepAwake(true);
  $('#overlay').innerHTML = ''; renderOverlay(); renderPill(); syncOrient();
  buzz(12);
}
function closeRide() {
  live.ride = { open: false, auto: false, dismissedAt: Date.now() };
  keepAwake(false); ensureGeo(); render();
}
function wireHold() {
  const el = $('#ride-mode'); if (!el) return;
  const ring = $('circle.p', el);
  let timer = null, x0 = 0, y0 = 0;
  const start = (e) => { x0 = e.clientX; y0 = e.clientY; ring.style.strokeDashoffset = '0'; timer = setTimeout(() => { buzz(20); closeRide(); }, 900); };
  const stop = () => { clearTimeout(timer); timer = null; ring.style.transition = 'stroke-dashoffset .2s'; ring.style.strokeDashoffset = '69.1'; setTimeout(() => (ring.style.transition = ''), 220); };
  el.addEventListener('pointerdown', start);
  el.addEventListener('pointermove', (e) => { if (timer && Math.hypot(e.clientX - x0, e.clientY - y0) > 10) stop(); });
  ['pointerup', 'pointercancel', 'pointerleave'].forEach((t) => el.addEventListener(t, stop));
  el.addEventListener('contextmenu', (e) => e.preventDefault());
}

// ---------- Navigation (rehearsal / single turn) ----------
const R = V.REHEARSAL;
function startRehearsal() {
  const st = R.steps[0];
  live.nav = { mode: 'rehearse', idx: 0, maneuver: st.maneuver, stepMeters: st.m, totalMeters: R.steps.reduce((a, x) => a + x.m, 0), street: st.street, say: st.say, exit: st.exit || 0, eta: new Date() };
  live.navSpeed = 1;
  updateEta();
  link.setNav(live.nav);
  toast(link.connected ? 'Turns are streaming to your bike.' : 'Rehearsing on the phone. Link the bike to see it on the cluster.');
  render();
}
function startSingle() {
  const t = live.turn, m = maneuverByKey(t.maneuver);
  live.nav = { mode: 'single', idx: 0, maneuver: t.maneuver, stepMeters: t.m, totalMeters: t.m + 1200, street: t.street, say: t.street ? `${m.label} · ${t.street}` : m.label, exit: m.glyph.startsWith('roundabout') ? t.exit : 0, eta: new Date(Date.now() + 6 * 60000) };
  link.setNav(live.nav);
  toast(link.connected ? 'Sent. The cluster repeats it every 800 ms.' : 'Not linked — showing the frame only.');
  render();
}
function nextStep() {
  const n = live.nav; if (!n || n.mode !== 'rehearse') return;
  const leftover = Math.max(0, n.stepMeters);
  n.totalMeters = Math.max(0, n.totalMeters - leftover);
  n.idx++;
  if (n.idx >= R.steps.length) { endNav(); toast('You’ve arrived.'); return; }
  const st = R.steps[n.idx];
  Object.assign(n, { maneuver: st.maneuver, stepMeters: st.m, street: st.street, say: st.say, exit: st.exit || 0 });
  buzz(10);
  render();
  if (live.ride.open) refreshRide();
}
function updateEta() {
  const n = live.nav; if (!n) return;
  const v = live.speed > 5 ? live.speed / 3.6 : 11.1;
  n.eta = new Date(Date.now() + (n.totalMeters / v) * 1000);
}
function endNav() { link.setNav(null); live.nav = null; render(); if (live.ride.open) refreshRide(); }
function navTick(dt) {
  const n = live.nav; if (!n || n.mode !== 'rehearse') return;
  const v = (live.speed > 5 ? live.speed / 3.6 : 11.1) * live.navSpeed;
  n.stepMeters -= v * dt; n.totalMeters = Math.max(0, n.totalMeters - v * dt);
  if (n.stepMeters <= 0) nextStep(); else updateEta();
}

// ---------- Calls & alerts ----------
let ringTimer = null;
function logAlert(kind, name) { s.alerts.log.unshift({ kind, name, ts: Date.now() }); s.alerts.log.length = Math.min(s.alerts.log.length, 50); }
function sent() { if (link.connected) s.alerts.sent++; }
function ring(name, source = 'Phone') {
  live.call = { state: 'ringing', name, since: Date.now(), source };
  link.setCall(CallState.INCOMING_CALL, name);
  logAlert('call', name); sent();
  clearTimeout(ringTimer);
  ringTimer = setTimeout(() => { if (live.call?.state === 'ringing') missedFrom(live.call.name); }, 25000);
  buzz([30, 60, 30]); if (s.settings.sounds !== false) SND.horn(2);
  S.save(s); renderOverlay();
}
function missedFrom(name) {
  live.call = null; clearTimeout(ringTimer);
  link.setCall(CallState.NO_CALL);
  s.alerts.missed.push({ name, ts: Date.now() });
  link.sendMissedCall(name, '', s.alerts.missed.length);
  logAlert('missed', name); sent();
  commit();
}
function answer() { clearTimeout(ringTimer); link.ackCall({ accept: true }); link.setCall(CallState.ACTIVE_CALL, live.call.name); live.call = { ...live.call, state: 'active', since: Date.now() }; renderOverlay(); }
function decline() { clearTimeout(ringTimer); link.ackCall({ reject: true }); link.setCall(CallState.NO_CALL); live.call = null; render(); }
function endCall() { const n = live.call?.name; link.setCall(CallState.END_CALL, n); setTimeout(() => link.setCall(CallState.NO_CALL), 1500); live.call = null; render(); }

link.addEventListener('button', ({ detail }) => {
  const { kind, action } = detail;
  if (live.call?.state === 'ringing') { kind === 'tap' ? answer() : decline(); return; }
  if (live.call?.state === 'active' && kind === 'long') { endCall(); return; }
  const label = { 'play-pause': 'Play / pause', 'next-track': 'Next track', answer: 'Answer', decline: 'Decline' }[action] || action;
  logAlert('button', `${kind === 'tap' ? 'Tap' : 'Long press'} · ${label}`); S.save(s);
  toast(`Handlebar · ${label}`); buzz();
  if (screen === 'alerts') render();
});

// ---------- Link state ----------
let wasConnected = false;
link.addEventListener('state', () => {
  const st = link.state;
  if (st === 'connected') {
    clearTimeout(autoStopTimer);
    if (!link.simulated && link.device) s.bike = { id: link.device.id, name: link.device.name || 'Bike' };
    s.settings.simulated = link.simulated;
    S.save(s);
    if (!wasConnected) { buzz(15); toast(link.simulated ? 'Simulated cluster linked.' : `Linked to ${link.deviceName}.`); }
    if (s.settings.autoRecord && !live.rec) startRecording(true);
    if (!wasConnected && !s.settings.autoAsked && !navigator.webdriver) { s.settings.autoAsked = true; S.save(s); setTimeout(() => { if (!sheetKey) A.sheet('auto-ask'); }, 900); }
    if (live.nav) link.setNav(live.nav);
    link.setMissedCount(s.alerts.missed.length);
  } else if (wasConnected) {
    if (live.pos) { s.parking = { lat: live.pos[0], lng: live.pos[1], ts: Date.now() }; S.save(s); }
    if (live.rec?.auto) autoStopTimer = setTimeout(() => stopRecording(true), st === 'idle' ? 0 : 30000);
  }
  wasConnected = st === 'connected';
  ensureGeo();
  render();
  if (sheetKey === 'bike' || sheetKey === 'log') refreshSheet();
});
link.addEventListener('log', () => { if (sheetKey === 'log') refreshSheet(); });
link.addEventListener('frame', ({ detail }) => {
  const el = $('[data-live="frame"]');
  if (el) { el.innerHTML = V.hexHtml(detail.bytes); setLive('frame-len', detail.bytes.length); setLive('frame-name', { tbt: 'Turn-by-turn', general: 'Status heartbeat', missedCall: 'Missed call', alerts: 'Alert' }[detail.key] || ''); }
});

// ---------- Phone telemetry for the status frame ----------
(async () => {
  try {
    const b = await navigator.getBattery?.();
    if (b) { const upd = () => { live.battery = Math.round(b.level * 100); link.setPhone({ battery: live.battery }); }; upd(); b.addEventListener('levelchange', upd); }
  } catch {}
  const c = navigator.connection;
  const sig = () => { const m = { '4g': 4, '3g': 3, '2g': 2, 'slow-2g': 1 }; link.setPhone({ signal: navigator.onLine ? (m[c?.effectiveType] ?? 4) : 0 }); };
  sig(); c?.addEventListener?.('change', sig); addEventListener('online', sig); addEventListener('offline', sig);
})();

// ---------- Actions ----------
const A = {
  go: (a) => { closeSheet(); go(a); },
  tab: (a) => {
    // Double-tap Today for a hard refresh (fresh app files, data kept).
    const now = Date.now();
    if (a === 'today' && now - (A._lastToday || 0) < 400) { A._lastToday = 0; return A['hard-refresh'](); }
    if (a === 'today') A._lastToday = now;
    if (screen === a) $('.view')?.scrollTo({ top: 0, behavior: 'smooth' }); else go(a);
  },
  'wx-refresh': () => { buzz(6); loadWeather(true); },
  'wx-locate': () => {
    if (!navigator.geolocation) return toast('This browser can’t share your location. Add your city in Profile.');
    navigator.geolocation.getCurrentPosition(async (p) => {
      const lat = p.coords.latitude, lon = p.coords.longitude; let place = '';
      try { const a = (await (await fetch(`https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&accept-language=en&lat=${lat.toFixed(3)}&lon=${lon.toFixed(3)}`)).json()).address || {}; place = a.city || a.town || a.village || a.state_district || ''; } catch {}
      s.profile.weatherAt = { lat, lon, place }; S.save(s); loadWeather(true);
    }, () => toast('Location is off. Allow it, or add your city in Profile.'), { timeout: 12000, maximumAge: 600000 });
  },
  'fuel-use': (p) => { const n = +p; if (!(n > 0)) return; s.vehicles.forEach((v) => (v.fuelPrice = n)); buzz(6); commit(); refreshSheet(); toast(`Petrol set to ${S.country(s).cur}${V.num(n, 2)} a litre.`); },
  'fuel-locate': () => {
    if (!navigator.geolocation) return toast('This browser can’t share your location. Add your city in Profile instead.');
    live.locating = true; refreshSheet();
    const done = (msg) => { live.locating = false; refreshSheet(); if (msg) toast(msg); };
    navigator.geolocation.getCurrentPosition(async (pos) => {
      try {
        const { latitude: la, longitude: lo } = pos.coords;
        const r = await fetch(`https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&accept-language=en&lat=${la.toFixed(3)}&lon=${lo.toFixed(3)}`, { headers: { Accept: 'application/json' } });
        if (!r.ok) throw new Error(r.status);
        const a = (await r.json()).address || {};
        const place = a.city || a.town || a.village || a.state_district || a.county || '';
        const hit = S.fuelLookup(a.state, place);
        if (!hit) return done('Couldn’t match your area to a state rate. Enter your pump price.');
        s.profile.fuelArea = { state: hit.state, place: place || hit.state, ts: Date.now() };
        if (hit.price) s.vehicles.forEach((v) => (v.fuelPrice = hit.price));
        S.save(s); render();
        done(hit.price ? `Petrol in ${hit.state}: ${S.country(s).cur}${V.num(hit.price, 2)} a litre.` : `No rate for ${hit.state} yet. Enter your pump price.`);
      } catch { done('Couldn’t look up your area right now. Your profile city is used instead.'); }
    }, () => done('Location is off. Allow it, or add your city in Profile.'), { timeout: 12000, maximumAge: 600000 });
  },
  sheet: (a) => { if (a.startsWith('pick-')) loadAllFonts(); if (a.startsWith('vehicle:')) live.draftPhoto = undefined; openSheet(a); },
  close: () => closeSheet(),
  'sheet-back': () => sheetBack(),
  font: (k) => { s.settings.font = k; buzz(6); commit(); refreshSheet(); },
  'support-amt': (a) => { live.supportAmt = +a; buzz(6); refreshSheet(); },
  'copy-upi': async () => { try { await navigator.clipboard.writeText(V.SUPPORT.upi); toast('UPI ID copied.'); } catch { toast(`UPI ID: ${V.SUPPORT.upi}`); } },
  'hard-refresh': async () => {
    // Fresh copy of the app's own files; saved rides, fuel and settings are untouched.
    try { sessionStorage.setItem('wl-splashed', '1'); } catch {}
    showLoading('Getting the latest version');
    try { const ks = await caches.keys(); await Promise.all(ks.map((k) => caches.delete(k))); } catch {}
    try { const rs = await navigator.serviceWorker?.getRegistrations?.(); await Promise.all((rs || []).map((r) => r.unregister())); } catch {}
    try { sessionStorage.setItem('waylink-updated', '1'); } catch {}
    location.replace(location.pathname + '?r=' + Date.now() + location.hash);
  },
  'copy-app-link': async () => { const u = location.origin + location.pathname; try { await navigator.clipboard.writeText(u); toast('Link copied. Thanks for sharing!'); } catch { toast(u); } },
  'body-font': (k) => { s.settings.bodyFont = k; buzz(6); commit(); refreshSheet(); },
  'show-loader': () => showLoader(),
  scenario: (k) => {
    if (live.rec) stopRecording(false);
    if (live.nav) endNav();
    if (k === 'real') { if (realState) { s = realState; realState = null; } live.scenario = null; S.setSaveEnabled(true); }
    else { if (!realState) realState = s; S.setSaveEnabled(false); s = S.scenario(k, realState); live.scenario = k; }
    closeSheet(); render(); toast(k === 'real' ? 'Back to your data.' : `Previewing ${k === 'empty' ? 'the empty state' : 'edge cases'}.`);
  },
  noop: () => {},
  ride: () => openRide(false),
  pair: async () => { closeSheet(); try { await link.pair(); } catch (e) { toast(e.message, 4000); } },
  'pair-all': async () => { closeSheet(); try { await link.pair({ showAll: true }); } catch (e) { toast(e.message, 4000); } },
  reconnect: async () => {
    closeSheet(); link.userDisconnect = false;
    if (s.settings.simulated && !link.device) return link.startSimulated();
    if (link.device) return link.connectGatt();
    if (!(await link.restore(s.bike.id))) { try { await link.pair(); } catch (e) { toast(e.message, 4000); } }
  },
  'cancel-link': () => link.disconnect(),
  disconnect: () => { closeSheet(); link.disconnect(); },
  forget: () => { closeSheet(); link.forget(); s.bike = { id: null, name: null }; s.settings.simulated = false; commit(); },
  'sim-on': () => { closeSheet(); s.settings.simulated = true; S.save(s); link.startSimulated(); },
  'sim-toggle': () => { SND.click(!(link.simulated && link.state !== 'idle')); if (link.simulated && link.state !== 'idle') { s.settings.simulated = false; link.disconnect(); link.simulated = false; commit(); } else A['sim-on'](); },
  toggle: (path) => {
    const parts = path.split('.'); let o = s.settings;
    while (parts.length > 1) o = o[parts.shift()];
    o[parts[0]] = !o[parts[0]];
    if (path === 'sounds') SND.setEnabled(o[parts[0]]);
    SND.click(o[parts[0]]);
    if (path === 'streetNames') link.sendStreetNames = s.settings.streetNames;
    buzz(6); commit();
  },
  'progress-style': (k) => { s.settings.progress = V.PROGRESS_STYLES.some(([x]) => x === k) ? k : 'gauge'; SND.click(true); commit(); refreshSheet(); },
  brand: (k) => { s.settings.brand = V.BRANDS.some(([x]) => x === k) ? k : 'loop'; document.documentElement.dataset.brand = s.settings.brand; buzz(6); commit(); refreshSheet(); },
  'icon-style': (k) => { s.settings.icons = ['duo', 'fill'].includes(k) ? k : 'line'; SND.click(true); commit(); refreshSheet(); },
  theme: (k) => { s.settings.theme = k; commit(); },
  accent: (k) => { s.settings.accent = k; commit(); },
  'switch-vehicle': (id) => {
    if (id === s.active) return closeSheet();
    if (live.rec) stopRecording(true);
    s.active = id; closeSheet(); buzz(10); commit();
    toast(`Now riding ${S.vehicleTitle(s)}.`);
  },
  'veh-colour': (hex, el) => { const f = el.closest('form'); f.colour.value = hex; $$('.swatch', f).forEach((x) => x.classList.toggle('on', x === el)); vehPreview(f); },
  'veh-photo-clear': (_, el) => { live.draftPhoto = null; const f = el.closest('form'); refreshVehicleSheet(f); },
  'veh-del': (id) => openSheet('veh-del:' + id),
  'veh-del-yes': async (id) => {
    if (s.vehicles.length < 2) return;
    if (live.rec) stopRecording(false);
    for (const d of live.glove.filter((x) => x.slot.startsWith(id + ':'))) await S.gloveDelete(d.slot);
    await loadGlove();
    const gone = s.vehicles.find((v) => v.id === id);
    s.vehicles = s.vehicles.filter((v) => v.id !== id);
    if (s.active === id) s.active = s.vehicles[0].id;
    closeSheet(); commit(); toast(`${S.vehicleTitle(s, gone)} removed.`);
  },
  'set-country': (c) => { s.profile.country = c; s.fuelPrice = null; closeSheet(); commit(); },
  'svc-done': (id) => {
    const it = s.service.find((x) => x.id === id); if (!it) return;
    closeSheet(); buzz(12);
    flip(() => { S.markServiceDone(s, it); S.save(s); render(); });
    toast(`${it.name} · done at ${V.num(s.odo)} km`);
  },
  'svc-earlier': (arg) => {
    const [id, days] = arg.split('|'); const it = s.service.find((x) => x.id === id); if (!it) return;
    const kmPerDay = S.periodStats(s.trips, Date.now() - 30 * DAY).km / 30;
    closeSheet();
    flip(() => { S.markServiceDone(s, it); it.lastDate = Date.now() - +days * DAY; it.lastKm = Math.max(0, Math.round(s.odo - kmPerDay * +days)); S.save(s); render(); });
    toast(`${it.name} · logged ${+days === 1 ? 'yesterday' : 'last week'}`);
  },
  'svc-pick': (id) => {
    const box = $(`#svc-pick-${id}`); if (!box) return;
    box.innerHTML = `<div class="two"><label class="field"><span class="eyebrow">Date</span><input class="input" type="date" id="pick-date" max="${new Date().toISOString().slice(0, 10)}" value="${new Date().toISOString().slice(0, 10)}"></label><label class="field"><span class="eyebrow">At</span><div class="input-wrap"><input class="input" id="pick-km" type="number" inputmode="numeric" value="${Math.round(s.odo)}"><span class="suffix">km</span></div></label></div><button class="btn ink block mt12" data-act="svc-pick-save" data-arg="${id}">Save</button>`;
    box.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 250 });
  },
  'svc-pick-save': (id) => {
    const it = s.service.find((x) => x.id === id); if (!it) return;
    const d = new Date($('#pick-date').value).getTime(), km = +$('#pick-km').value;
    closeSheet();
    flip(() => { S.markServiceDone(s, it); if (!isNaN(d)) it.lastDate = d; if (km >= 0) it.lastKm = km; S.save(s); render(); });
  },
  'svc-del': (id) => { s.service = s.service.filter((x) => x.id !== id); closeSheet(); commit(); toast('Reminder removed.'); },
  'rec-del': (id) => { s.records = s.records.filter((x) => x.id !== id); closeSheet(); commit(); },
  'fuel-del': (id) => { s.fuel = s.fuel.filter((x) => x.id !== id); closeSheet(); commit(); toast('Fill-up removed.'); },
  'fuel-full': (_, el) => { el.classList.toggle('on'); el.closest('form').full.value = el.classList.contains('on') ? 1 : 0; buzz(6); },
  check: (id) => { const x = s.checklist.find((c) => c.id === id); if (x) { x.done = !x.done; buzz(6); commit(); } },
  'check-del': (id) => { s.checklist = s.checklist.filter((c) => c.id !== id); commit(); },
  'trip-reset': (k) => { s['trip' + k] = { km: 0, since: Date.now() }; closeSheet(); commit(); toast(`Trip ${k} reset.`); },
  'rec-start': () => { closeSheet(); startRecording(false); },
  'rec-stop': () => { closeSheet(); stopRecording(true); },
  'rec-discard': () => { closeSheet(); stopRecording(false); },
  'trip-gpx': (id) => { const t = s.trips.find((x) => x.id === id); if (t) download(`${V.appName(s).toLowerCase()}-ride-${t.n}.gpx`, S.toGPX(t), 'application/gpx+xml'); },
  'trip-rename': (id) => openSheet('trip-rename:' + id),
  'trip-del': (id) => { s.trips = s.trips.filter((x) => x.id !== id); closeSheet(); commit(); toast('Ride deleted. The odometer is unchanged.'); },
  'nav-rehearse': () => startRehearsal(),
  'nav-single': () => { if (sheetKey) closeSheet(); startSingle(); },
  'nav-end': () => { endNav(); toast('Navigation ended. The cluster is cleared.'); },
  'nav-speed': (x) => { live.navSpeed = +x; render(); },
  'nav-next': () => nextStep(),
  'turn-pick': (k) => { live.turn.maneuver = k; buzz(5); render(); if (sheetKey === 'turn-test') refreshSheet(); },
  'turn-exit': (e) => { live.turn.exit = +e; render(); if (sheetKey === 'turn-test') refreshSheet(); },
  relay: (kind) => {
    const name = (live.relayName || '').trim();
    if (!name) return toast('Add a name first.');
    const a = s.settings.alerts;
    if (kind === 'ring') { if (!a.calls) return toast('Phone calls are switched off above.'); ring(name); }
    else if (kind === 'missed') { if (!a.calls) return toast('Phone calls are switched off above.'); missedFrom(name); toast(link.connected ? `Missed call from ${name} sent.` : 'Saved. Link the bike to send it.'); }
    else if (kind === 'wa') {
      if (!a.waMsgs) return toast('WhatsApp messages are switched off above.');
      s.alerts.wa.push({ name, ts: Date.now() }); link.sendWhatsApp(name); logAlert('wa', name); sent(); commit();
      toast(link.connected ? `${name} on the cluster for 6 s.` : 'Saved. Link the bike to send it.');
    }
  },
  'clear-missed': () => { s.alerts.missed = []; link.setMissedCount(0); closeSheet(); commit(); toast('Missed calls cleared.'); },
  'clear-wa': () => { s.alerts.wa = []; closeSheet(); commit(); toast('WhatsApp alerts cleared.'); },
  'alerts-tab': (k) => { if (live.alertsTab === k) return; live.alertsTab = k === 'recent' ? 'recent' : 'needs'; if (!A._swiped) SND.click(true); render(); },
  'goto-riding': () => {
    closeRide(); go('settings');
    setTimeout(() => { const el = $('#sec-riding'); if (!el) return; el.scrollIntoView({ behavior: 'smooth', block: 'center' }); el.classList.add('flash'); setTimeout(() => el.classList.remove('flash'), 1600); }, 450);
  },
  'look-toggle': () => { live.lookOpen = !live.lookOpen; SND.click(live.lookOpen); render(); },
  'pc-toggle': () => { live.pcOpen = !live.pcOpen; SND.click(live.pcOpen); render(); },
  'show-loading': () => showLoading('Loading your garage', 3200),
  'report-send': async (k) => {
    const subj = ($('#rp-subj')?.value || '').trim() || 'Problem report', body = ($('#rp-body')?.value || '').trim(), log = $('#backup-text')?.value || '';
    const title = `Waylink: ${subj}`, text = `${body || '(Describe what happened)'}\n\n--- App log ---\n${log}`;
    const enc = encodeURIComponent;
    if (k === 'email') { location.href = `mailto:${V.SUPPORT_EMAIL}?subject=${enc(title)}&body=${enc(text)}`; return; }
    if (k === 'github') { window.open(`${V.REPO_URL}/issues/new?title=${enc(title)}&body=${enc(text)}`, '_blank', 'noopener'); return; }
    const file = new File([`${title}\n\n${text}\n`], 'waylink-log.txt', { type: 'text/plain' });
    try { if (navigator.canShare && navigator.canShare({ files: [file] })) { await navigator.share({ files: [file], title }); return; } } catch (e) { if (e?.name === 'AbortError') return; }
    const a = Object.assign(document.createElement('a'), { href: URL.createObjectURL(file), download: file.name }); a.click(); setTimeout(() => URL.revokeObjectURL(a.href), 4000); toast('Log saved.');
  },
  press: (k) => link.pressed(k),
  'call-answer': () => answer(),
  'call-decline': () => decline(),
  'call-end': () => endCall(),
  export: () => download(`${V.appName(s).toLowerCase()}-${new Date().toISOString().slice(0, 10)}.json`, S.exportVault(s), 'application/json'),
  import: () => { if (inFrame) openSheet('paste'); else $('#import-file')?.click(); },
  'copy-backup': () => openSheet('copy'),
  'copy-text': async () => { const t = $('#backup-text'); try { await navigator.clipboard.writeText(t.value); toast('Copied.'); } catch { t.select(); toast('Selected — copy it from the menu.'); } },
  // Start over: every setting, ride, paper and saved app file goes, as if freshly installed.
  'reset-all': async () => {
    closeSheet(); link.disconnect(); stopRecording(false);
    showLoading('Resetting Waylink');
    try { for (const d of live.glove) await S.gloveDelete(d.slot); } catch {}
    try { S.wipe(); } catch {}
    try { localStorage.clear(); sessionStorage.clear(); } catch {}
    try { const dbs = await indexedDB.databases?.(); await Promise.all((dbs || []).map((d) => new Promise((r) => { const q = indexedDB.deleteDatabase(d.name); q.onsuccess = q.onerror = q.onblocked = r; }))); } catch {}
    try { const ks = await caches.keys(); await Promise.all(ks.map((k) => caches.delete(k))); } catch {}
    try { const rs = await navigator.serviceWorker?.getRegistrations?.(); await Promise.all((rs || []).map((r) => r.unregister())); } catch {}
    location.replace(location.pathname);
  },
  erase: async () => {
    closeSheet(); link.disconnect(); stopRecording(false);
    for (const d of live.glove) await S.gloveDelete(d.slot);
    await loadGlove();
    const keep = { theme: s.settings.theme, accent: s.settings.accent };
    s = S.freshState(); Object.assign(s.settings, keep); S.wipe(); S.saveNow(s);
    live.nav = null; screen = 'today'; render(); toast('Erased.');
  },
  'park-save': () => {
    closeSheet();
    const saveAt = (lat, lng) => { s.parking = { lat, lng, ts: Date.now() }; commit(); toast('Parking spot saved.'); };
    if (live.pos && (link.simulated || Date.now() - (lastFix?.ts || 0) < 60000)) return saveAt(live.pos[0], live.pos[1]);
    if (!navigator.geolocation) return toast('This browser has no location.');
    navigator.geolocation.getCurrentPosition((p) => saveAt(p.coords.latitude, p.coords.longitude), () => toast('Couldn’t get your location. Allow it and try again.'), { enableHighAccuracy: true, timeout: 15000 });
  },
  'park-clear': () => { s.parking = null; closeSheet(); commit(); },
  'doc-del': async (slot) => { await S.gloveDelete(slot); await loadGlove(); openSheet('glovebox'); render(); },
  'quick-chain': () => { const it = s.service.find((x) => x.kind === 'chain'); closeSheet(); if (it) { S.markServiceDone(s, it); commit(); toast(`Chain lubed at ${V.num(s.odo)} km.`); } },
  'replay-logo': (_, el) => { const b = el || $('.brand-final'); if (!b) return; b.classList.remove('anim'); void b.offsetWidth; b.classList.add('anim'); },
  'pick-avatar': (k) => { s.profile.avatar = k || null; s.profile.photo = null; SND.click(true); commit(); refreshSheet(); },
  'app-icon': (k) => { s.settings.iconStyle = k; SND.click(true); commit(); refreshSheet(); },
  'onb-test': () => { live.onbTest = true; live.onbStep = 0; render('fade-in'); },
  'onb-step': (k) => { live.onbStep = +k; SND.click(true); render('fade-in'); },
  'onb-go': (i) => { const el = $('[data-onb-slides]'); if (el) el.scrollTo({ left: +i * el.clientWidth, behavior: 'smooth' }); },
  'onb-next': () => {
    const el = $('[data-onb-slides]'); if (!el) return;
    const i = Math.round(el.scrollLeft / el.clientWidth), n = el.children.length;
    if (i >= n - 1) return A['onb-step'](1);
    SND.swipe(1); el.scrollTo({ left: (i + 1) * el.clientWidth, behavior: 'smooth' });
  },
  'onb-skip-reg': () => {
    if (live.onbTest) { live.onbStep = 2; render('fade-in'); return; }
    if (!s.vehicles.length) { const v = S.newVehicle({}); s.vehicles = [v]; s.active = v.id; }
    s.profile.since = s.profile.since || Date.now(); S.saveNow(s); live.onbStep = 2; render('fade-in');
  },
  'onb-finish': (how) => {
    if (live.onbTest) { live.onbTest = false; live.onbStep = 0; screen = 'settings'; render('fade-in'); toast('Onboarding preview closed.'); return; }
    if (!s.vehicles.length) { const v = S.newVehicle({}); s.vehicles = [v]; s.active = v.id; }
    s.profile.since = s.profile.since || Date.now();
    s.onboarded = true; live.onbStep = 0; S.saveNow(s); screen = 'today'; render('fade-in');
    if (how === 'pair') A.pair?.(); else if (how === 'sim') A['sim-on'](); else if (how === 'ios') A.sheet('ios-pair');
    else { try { localStorage.setItem('wl-pair-nudge', new Date().toDateString()); } catch {} }
  },
  'onboard-sample': () => { if (live.onbTest) return A['onb-finish']('later'); S.seedSample(s); s.onboarded = true; S.saveNow(s); screen = 'today'; render('fade-in'); toast('Sample data loaded. Erase it any time in Settings.', 3600); },
};

const FORMS = {
  onboard: (f) => {
    if (live.onbTest) { live.onbStep = 2; SND.click(true); render('fade-in'); return; }
    s.profile.name = f.name.value.trim(); s.profile.country = f.country.value; s.profile.since = Date.now();
    const v = S.newVehicle({ model: f.model.value, plate: f.plate.value.trim().toUpperCase(), odo: Math.max(0, +f.odo.value || 0) });
    s.vehicles = [v]; s.active = v.id; S.saveNow(s);
    live.onbStep = 2; SND.click(true); render('fade-in');
  },
  field: (f) => {
    const key = f.dataset.arg, v = f.v.value;
    if (key === 'odo') { const n = +v; if (!(n >= 0)) return toast('Enter a number.'); S.setOdometer(s, n); }
    else if (key === 'fuelPrice') { const n = +v; if (!(n > 0)) return toast('Enter a price.'); s.fuelPrice = n; }
    else if (key.startsWith('profile.')) s.profile[key.slice(8)] = v.trim();
    else if (key.startsWith('trip.')) { const t = s.trips.find((x) => x.id === key.slice(5)); if (t) t.title = v.trim() || t.title; }
    closeSheet(); commit();
  },
  'svc-edit': (f) => {
    const isNew = f.dataset.arg === 'new';
    const it = isNew ? { id: S.uid(), kind: 'custom', icon: 'wrench' } : s.service.find((x) => x.id === f.dataset.arg);
    it.name = f.name.value.trim() || 'Reminder';
    if (it.milestone) it.targetKm = Math.max(0, +f.targetKm.value || 0);
    else {
      it.everyKm = Math.max(0, +f.everyKm.value || 0);
      it.everyDays = Math.round(Math.max(0, +f.everyMonths.value || 0) * 30.4);
      if (!it.everyKm && !it.everyDays) return toast('Set a distance or a time.');
    }
    it.lastKm = Math.max(0, +f.lastKm.value || 0);
    const d = new Date(f.lastDate.value).getTime(); it.lastDate = isNaN(d) ? Date.now() : d;
    if (isNew) s.service.push(it);
    closeSheet(); commit(); toast(isNew ? 'Reminder added.' : 'Saved.');
  },
  rec: (f) => {
    const isNew = f.dataset.arg === 'new';
    const r = isNew ? { id: S.uid() } : s.records.find((x) => x.id === f.dataset.arg);
    Object.assign(r, { title: f.title.value.trim(), date: new Date(f.date.value).getTime() || Date.now(), odo: +f.odo.value || 0, workshop: f.workshop.value.trim(), cost: +f.cost.value || 0, notes: f.notes.value.trim() });
    if (isNew) s.records.push(r);
    closeSheet(); commit();
  },
  fuel: (f) => {
    const odo = +f.odo.value, litres = +f.litres.value, price = +f.price.value;
    let cost = +f.cost.value; if (!cost && litres) cost = +(litres * price).toFixed(2);
    if (!(odo > 0) || !(litres > 0)) return toast('Odometer and litres, please.');
    const ts = (() => { const d = new Date(f.date.value); return isNaN(d) ? Date.now() : d.getTime(); })();
    const isNew = f.dataset.arg === 'new';
    const e = isNew ? { id: S.uid() } : s.fuel.find((x) => x.id === f.dataset.arg);
    Object.assign(e, { odo, litres, cost, full: f.full.value === '1', ts });
    if (isNew) s.fuel.push(e);
    if (litres && cost) s.fuelPrice = +(cost / litres).toFixed(2);
    if (odo > s.odo) S.setOdometer(s, odo);
    closeSheet(); commit();
    const fs = S.fuelStats(s); const t = fs.tanks.find((x) => x.id === e.id);
    toast(t ? `${V.num(t.kmpl, 1)} km/L on that tank.` : e.full ? 'Logged. Next full tank gives you mileage.' : 'Top-up logged.');
  },
  medical: (f) => { Object.assign(s.profile, { blood: f.blood.value, medical: f.medical.value.trim(), iceName: f.iceName.value.trim(), icePhone: f.icePhone.value.trim() }); closeSheet(); commit(); },
  'check-add': (f) => { const t = f.text.value.trim(); if (!t) return; s.checklist.push({ id: S.uid(), text: t, done: false }); commit(); setTimeout(() => $('[data-form="check-add"] input')?.focus(), 30); },
  maps: (f) => {
    const dest = f.dest.value.trim(); live.dest = dest;
    if (!dest) return toast('Where to?');
    const a = Object.assign(document.createElement('a'), { href: `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(dest)}&travelmode=two-wheeler&dir_action=navigate`, target: '_blank', rel: 'noopener' });
    document.body.appendChild(a); a.click(); a.remove();
  },
  paste: (f) => { try { s = S.importVault(f.text.value); S.saveNow(s); closeSheet(); render(); toast('Restored.'); } catch (e) { toast(e.message); } },
  vehicle: (f) => {
    const isNew = f.dataset.arg === 'new';
    const odo = +f.odo.value;
    if (!(odo >= 0) || f.odo.value === '') return toast('Add the odometer reading.');
    const fields = { model: f.model.value, nickname: f.nickname.value.trim(), plate: f.plate.value.trim().toUpperCase(), colour: f.colour.value };
    if (isNew) {
      const v = S.newVehicle({ ...fields, odo, photo: live.draftPhoto || null });
      if (live.rec) stopRecording(true);
      s.vehicles.push(v); s.active = v.id;
      
      closeSheet(); commit(); toast(`${S.vehicleTitle(s)} added. You’re riding it now.`);
    } else {
      const v = s.vehicles.find((x) => x.id === f.dataset.arg); if (!v) return;
      Object.assign(v, fields); v.odo = Math.round(odo * 10) / 10;
      if (live.draftPhoto !== undefined) { v.photo = live.draftPhoto;  }
      closeSheet(); commit(); toast('Saved.');
    }
  },
  profile: () => {},
};

const INPUTS = {
  'veh-model': (el) => vehPreview(el.form),
  'turn-dist': (el) => { SND.tick(el.value / el.max); el.style.setProperty('--p', (el.value / el.max) * 100 + '%'); live.turn.m = +el.value; setLive('turn-dist', `${V.num(+el.value)} m`); },
  'turn-street': (el) => { live.turn.street = el.value.replace(/[^\x20-\x7E]/g, ''); setLive('turn-count', `${el.value.length}/31`); },
  'nav-dist': (el) => { el.style.setProperty('--p', (el.value / el.max) * 100 + '%'); if (live.nav) { const d = +el.value - live.nav.stepMeters; live.nav.stepMeters = +el.value; live.nav.totalMeters = Math.max(+el.value, live.nav.totalMeters + d); tickLive(); } },
  'relay-name': (el) => { live.relayName = el.value; },
  'fuel-l': (el) => { const f = el.form; const p = +f.price.value; if (+el.value && p) f.cost.value = (+el.value * p).toFixed(2); },
  'fuel-c': (el) => { const f = el.form; const p = +f.price.value; if (+el.value && p) f.litres.value = (+el.value / p).toFixed(2); },
};

document.addEventListener('click', (e) => {
  const di = e.target.closest('input[type=date], input[type=datetime-local], input[type=time]');
  if (di && !di.readOnly) { try { di.showPicker(); } catch {} }
  const el = e.target.closest('[data-act]');
  if (!el || el.disabled) return;
  if (Date.now() - (A._skipTap || 0) < 450 && el.closest('#tabbar')) return;
  const fn = A[el.dataset.act];
  if (!fn) return;
  if (el.tagName === 'A') return;
  e.preventDefault();
  fn(el.dataset.arg, el, e);
});
document.addEventListener('submit', (e) => {
  const f = e.target.closest('form[data-form]'); if (!f) return;
  e.preventDefault(); FORMS[f.dataset.form]?.(f);
});
document.addEventListener('input', (e) => {
  const el = e.target.closest('[data-input]'); if (el) INPUTS[el.dataset.input]?.(el);
  const pf = e.target.closest('form[data-autosave]'); if (pf) autosaveProfile(pf);
});
let profTimer;
function autosaveProfile(f) {
  clearTimeout(profTimer);
  profTimer = setTimeout(() => {
    const p = s.profile, oldCity = p.city;
    for (const k of ['name', 'phone', 'email', 'city', 'licenceNo', 'blood', 'medical', 'iceName', 'icePhone']) if (f[k]) p[k] = f[k].value.trim();
    if (f.licenceExpiry) { const d = f.licenceExpiry.value ? new Date(f.licenceExpiry.value).getTime() : null; p.licenceExpiry = isNaN(d) ? null : d; }
    if (p.city !== oldCity) delete p.weatherAt; // a new city wins over an old location fix
    S.save(s); if (screen === 'profile') render();
    const el = $('#saved'); if (el) { el.classList.add('show'); clearTimeout(el._t); el._t = setTimeout(() => el.classList.remove('show'), 1400); }
  }, 350);
}
async function resizeImage(file, w, h) {
  const bmp = await createImageBitmap(file);
  const c = Object.assign(document.createElement('canvas'), { width: w, height: h });
  const g = c.getContext('2d');
  const sc = Math.max(w / bmp.width, h / bmp.height);
  const dw = bmp.width * sc, dh = bmp.height * sc;
  g.drawImage(bmp, (w - dw) / 2, (h - dh) / 2, dw, dh);
  return c.toDataURL('image/jpeg', 0.82);
}
function vehPreview(f) {
  const cur = s.vehicles.find((x) => x.id === f.dataset.arg);
  const photo = live.draftPhoto === undefined ? cur?.photo : live.draftPhoto;
  const tmp = { model: f.model.value, colour: f.colour.value, photo };
  const box = $('#veh-preview'); if (box) box.innerHTML = V.vehicleArt({ settings: { art: 'photo' } }, tmp, 'hero');
}
function refreshVehicleSheet(f) {
  const keep = { model: f.model.value, nickname: f.nickname.value, plate: f.plate.value, odo: f.odo.value, colour: f.colour.value };
  refreshSheet();
  const nf = $('form[data-form="vehicle"]'); if (!nf) return;
  for (const [k, v] of Object.entries(keep)) nf[k].value = v;
  $$('.swatch', nf).forEach((x) => x.classList.toggle('on', x.dataset.arg === keep.colour));
  vehPreview(nf);
}
document.addEventListener('change', async (e) => {
  const el = e.target;
  if (el.id === 'import-file' && el.files[0]) {
    try { s = S.importVault(await el.files[0].text()); S.saveNow(s); render(); toast('Restored from backup.'); } catch (err) { toast(err.message); }
    el.value = '';
  } else if (el.dataset.input === 'doc-file' && el.files[0]) {
    const file = el.files[0];
    if (file.size > 15 * 1024 * 1024) return toast('That file is over 15 MB.');
    const prev = live.glove.find((x) => x.slot === el.dataset.arg);
    try { await S.glovePut({ slot: el.dataset.arg, name: file.name, type: file.type || 'application/octet-stream', blob: file, added: Date.now(), expiry: prev?.expiry || null }); await loadGlove(); refreshSheet(); render(); toast('Stored on this phone.'); }
    catch { toast('Couldn’t store that file in this browser.'); }
  } else if (el.dataset.input === 'avatar' && el.files[0]) {
    try { s.profile.photo = await resizeImage(el.files[0], 320, 320); if (sheetKey === 'avatar') closeSheet(); commit(); toast('Photo updated.'); } catch { toast('Couldn’t read that image.'); }
  } else if (el.dataset.input === 'veh-photo' && el.files[0]) {
    try { live.draftPhoto = await resizeImage(el.files[0], 1000, 600); refreshVehicleSheet(el.closest('form')); } catch { toast('Couldn’t read that image.'); }
  } else if (el.dataset.input === 'doc-expiry') {
    const d = live.glove.find((x) => x.slot === el.dataset.arg); if (!d) return;
    d.expiry = el.value ? new Date(el.value).getTime() : null;
    await S.glovePut(d); await loadGlove(); render();
  }
});
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') { if (sheetStack.length) sheetBack(); else if (sheetKey || $('.sheet')) closeSheet(); else if (live.ride.open) closeRide(); } });

// ---------- Mileage chart hover ----------
function wireChart() {
  const box = $('#mileage-chart'); if (!box) return;
  const svg = $('svg', box), tip = $('.tip', box), xh = $('.xh', svg);
  const show = (r) => {
    const vb = svg.viewBox.baseVal, k = svg.clientWidth / vb.width;
    const x = +r.dataset.x * k + svg.offsetLeft, y = +r.dataset.y * k + svg.offsetTop;
    tip.textContent = r.dataset.tip; tip.style.left = x + 'px'; tip.style.top = y + 'px'; tip.classList.add('show');
    xh.setAttribute('x1', r.dataset.x); xh.setAttribute('x2', r.dataset.x); xh.setAttribute('opacity', '.5');
  };
  svg.addEventListener('pointermove', (e) => { const r = e.target.closest('rect[data-tip]'); if (r) show(r); });
  svg.addEventListener('pointerleave', () => { tip.classList.remove('show'); xh.setAttribute('opacity', '0'); });
}

// ---------- Main loop ----------
let lastT = performance.now();
setInterval(() => {
  const now = performance.now(), dt = Math.min(3, (now - lastT) / 1000); lastT = now;
  navTick(dt);
  tickLive();
}, 1000);

// ---------- Boot ----------
// Launch sound: only while the splash is on screen, and only if audio is truly playing. iPhones report the audio
// clock as running but hold the sound back until the first tap, which would make it play later, so check the clock moves.
let launched = false;
function launchSound() {
  if (launched || s.settings.sounds === false || document.documentElement.classList.contains('no-splash')) return; launched = true;
  // iPhone holds back sound until a tap, then plays what was queued, so the engine would start late. Only play
  // when sound is genuinely allowed now: never on iPhone before a tap.
  if (V.isIOS() && !navigator.userActivation?.hasBeenActive) return;
  SND.whenLive(() => { const sp = $('#splash'); if (sp && !sp.classList.contains('out')) SND.engineStart(); });
}
setTimeout(() => { SND.unlock(); launchSound(); }, 60);
// Every tap re-wakes audio (iPhone suspends it in the background); it's a no-op when already running.
const firstTap = () => { SND.unlock(); };
['touchend', 'click', 'keydown'].forEach((t) => addEventListener(t, firstTap, true));
const within = (p, ms) => Promise.race([p, new Promise((r) => setTimeout(r, ms))]);
// First launch: keep the splash up until the onboarding pictures are ready (at most ~3 s more).
const onbReady = () => {
  if (s.onboarded) return Promise.resolve();
  const dark = ['obsidian', 'amoled'].includes(document.documentElement.dataset.theme), m = dark ? 'dark' : 'light';
  const load = (src) => new Promise((r) => { const i = new Image(); i.onload = i.onerror = r; i.src = src; (i.decode?.() || Promise.resolve()).then(r, r); });
  return Promise.race([Promise.all([load(`img/onb/scene-${m}.webp`), load(`img/onb/link-${m}.webp`), load('img/rider.png')]), new Promise((r) => setTimeout(r, 3000))]);
};
function hideSplash() {
  const sp0 = $('#splash'); if (sp0 && !sp0._waited && !s.onboarded) { sp0._waited = true; sp0.classList.add('waiting'); onbReady().then(() => hideSplash()); return; }
  const sp = $('#splash'); if (!sp || location.hash === '#loading') return;
  if (document.documentElement.classList.contains('no-splash')) { sp.remove(); return; }
  setTimeout(() => { sp.classList.add('out'); setTimeout(() => sp.remove(), 380); }, Math.max(300, (document.documentElement.dataset.brand === 'turns' ? 2250 : 2050) - performance.now()));
}
(async function boot() {
  // New bike pictures: clear saved copies once, so every phone shows them.
  try { if (localStorage.getItem('waylink-art') !== V.ART_V) { localStorage.setItem('waylink-art', V.ART_V); if (localStorage.getItem('pulsarnav-web-v2')) { A['hard-refresh'](); return; } } } catch {}
  fitArch(); document.fonts?.ready.then(() => fitArch());
  splashTpl = $('#splash')?.outerHTML || '';
  try {
    applyTheme();
    link.sendStreetNames = s.settings.streetNames;
    screen = SCREENS.includes(location.hash.slice(1)) ? location.hash.slice(1) : 'today';
    render('fade-in');
    window.__waylinkReady = true; // the page is up; the safety net in index.html stands down
    // Neither of these may hold the loader: each gets a short time limit and fails quietly.
    await within(loadGlove().catch(() => {}), 2500);
    await within(fetch(`img/bikes/manifest.json?v=${V.ART_V}`, { cache: 'no-cache' }).then((r) => r.json()).then(S.setModelImages).catch(() => {}), 2500);
    if (s.onboarded) render();
    if (s.settings.simulated) link.startSimulated();
    else if (s.bike.id) link.restore(s.bike.id);
  } catch (err) {
    console.error(err);
    toast('Something went wrong while starting. Try Settings, Help, Report a problem.');
  } finally {
    window.__waylinkReady = true;
    hideSplash();
  }
  registerUpdates();
})();

// ---------- Updates: a new version installs in the background and the app reloads itself ----------
function registerUpdates() {
  if (!('serviceWorker' in navigator) || inFrame || !/^https:|localhost|127\.0\.0\.1/.test(location.origin)) return;
  const hadController = !!navigator.serviceWorker.controller;
  let reloading = false;
  const reloadWhenIdle = () => {
    if (reloading) return;
    if (live.ride.open || live.call) { setTimeout(reloadWhenIdle, 15000); return; } // never mid-ride or mid-call
    reloading = true;
    try { sessionStorage.setItem('waylink-updated', '1'); } catch {}
    location.reload();
  };
  navigator.serviceWorker.addEventListener('controllerchange', () => { if (hadController) reloadWhenIdle(); });
  navigator.serviceWorker.register('sw.js', { updateViaCache: 'none' }).then((reg) => {
    const check = () => reg.update().catch(() => {});
    document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible') check(); });
    setInterval(check, 30 * 60e3);
  }).catch(() => {});
  // No bike linked yet: offer pairing once a day, in a sheet.
  setTimeout(() => { try {
    const known = !!(link.device || s.bike?.id || link.simulated || S.veh(s).bike?.id), day = new Date().toDateString();
    if (!navigator.webdriver && s.onboarded && !known && !sheetKey && screen === 'today' && localStorage.getItem('wl-pair-nudge') !== day) { localStorage.setItem('wl-pair-nudge', day); A.sheet('pair-intro'); }
  } catch {} }, 2600);
  try { if (sessionStorage.getItem('waylink-updated')) { sessionStorage.removeItem('waylink-updated'); setTimeout(() => toast('Updated to the latest version.'), 1200); } } catch {}
}

document.addEventListener('scroll', (e) => {
  const el = e.target; if (!el?.matches?.('[data-onb-slides]')) return;
  $('[data-onb-stage]')?.style.setProperty('--p', (el.scrollLeft / el.clientWidth).toFixed(3));
  const i = Math.round(el.scrollLeft / el.clientWidth), btn = $('[data-act="onb-next"]');
  $$('.onb-dots button').forEach((d, k) => d.classList.toggle('on', k === i));
  if (btn) btn.textContent = i >= el.children.length - 1 ? 'Set up my bike' : 'Next';
}, true);
// The picture above the onboarding text swipes the slides too.
{
  let sx = 0, sy = 0, on = false;
  document.addEventListener('touchstart', (e) => { on = !!e.target.closest('[data-onb-stage]'); sx = e.touches[0].clientX; sy = e.touches[0].clientY; }, { passive: true });
  document.addEventListener('touchend', (e) => {
    if (!on) return; on = false; const t = e.changedTouches[0], dx = t.clientX - sx;
    if (Math.abs(dx) < 40 || Math.abs(dx) < Math.abs(t.clientY - sy)) return;
    const el = $('[data-onb-slides]'); if (!el) return;
    const i = Math.round(el.scrollLeft / el.clientWidth) + (dx < 0 ? 1 : -1);
    if (i >= 0 && i < el.children.length) { SND.swipe(dx < 0 ? 1 : -1); A['onb-go'](i); }
  }, { passive: true });
}
// Swipe sideways on a tabbed list (Alerts: Needs you / Recent) to switch tabs.
{
  let sx = 0, sy = 0, el = null;
  document.addEventListener('touchstart', (e) => { const v = e.target.closest('.view'); el = v && !e.target.closest('input, textarea, .switch, .seg') ? v.querySelector('[data-swipe-tabs]') : null; if (!el) return; sx = e.touches[0].clientX; sy = e.touches[0].clientY; }, { passive: true });
  document.addEventListener('touchcancel', () => { el = null; }, { passive: true });
  document.addEventListener('touchend', (e) => {
    if (!el) return; const t = e.changedTouches[0], dx = t.clientX - sx, dy = t.clientY - sy; const box = el; el = null;
    if (Math.abs(dx) < 50 || Math.abs(dx) < Math.abs(dy) * 1.3) return;
    const tabs = box.dataset.tabs.split(','), i = tabs.indexOf(box.dataset.cur), n = tabs[i + (dx < 0 ? 1 : -1)];
    if (n) { A._swiped = true; SND.swipe(dx < 0 ? 1 : -1); A[box.dataset.swipeTabs](n); A._swiped = false; requestAnimationFrame(() => $('[data-swipe-tabs]')?.classList.add(dx < 0 ? 'in-l' : 'in-r')); }
  }, { passive: true });
}
// Swipe on the tab bar to page between tabs, like swiping between pages:
// swipe left for the next tab, right for the previous one. One step per swipe.
{
  let on = false, sx = 0, sy = 0, t0 = 0;
  document.addEventListener('touchstart', (e) => { on = !!e.target.closest('#tabbar'); if (!on) return; sx = e.touches[0].clientX; sy = e.touches[0].clientY; t0 = Date.now(); }, { passive: true });
  document.addEventListener('touchend', (e) => {
    if (!on) return; on = false;
    const t = e.changedTouches[0], dx = t.clientX - sx, dy = t.clientY - sy;
    if (Math.abs(dx) < 36 || Math.abs(dx) < Math.abs(dy) * 1.4 || Date.now() - t0 > 700) return;
    const order = $$('#tabbar .tab').map((b) => b.dataset.arg), i = order.indexOf($('#tabbar .tab.on')?.dataset.arg || screen);
    const next = order[(i < 0 ? 0 : i) + (dx < 0 ? 1 : -1)];
    A._skipTap = Date.now();
    if (next) { A._lastToday = 0; buzz(4); SND.swipe(dx < 0 ? 1 : -1); go(next); }
  }, { passive: true });
}
