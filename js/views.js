// Waylink — views. Pure functions: state in, HTML out.
import { MANEUVERS, maneuverByKey, toHex, UUID } from './protocol.js';
import { ICONS as HUGE_ICONS, SET as HUGE_SET } from './icons-huge.js';
import * as S from './store.js';

// ---------- Format helpers (Indian English by default) ----------
export const esc = (x) => String(x ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
let LOCALE = 'en-IN';
export const setLocale = (l) => { LOCALE = l || 'en-IN'; };
const DL = () => (LOCALE.startsWith('en') ? 'en-IN' : LOCALE);
export const num = (n, d = 0) => (n == null || isNaN(n) ? '—' : Number(n).toLocaleString(LOCALE, { minimumFractionDigits: d, maximumFractionDigits: d }));
// Compact, never rounds up: 1,99,999.9 → "1.99 L"; 45,280 → "45.2K"; 9,999.9 → "9,999".
const trunc = (x, d) => Math.floor(x * 10 ** d + 1e-9) / 10 ** d;
const sig3 = (x) => (x >= 100 ? trunc(x, 0) : x >= 10 ? trunc(x, 1) : trunc(x, 2)).toLocaleString('en-US', { maximumFractionDigits: 2 });
// Names on cards: the full name when it fits in 14 letters, else the first name (cut at 14).
export const shortName = (n) => { const t = (n || '').trim().replace(/\s+/g, ' '); if (t.length <= 14) return t; const f = t.split(' ')[0]; return f.length <= 14 ? f : f.slice(0, 13) + '…'; };
export const short = (n) => {
  if (n == null || isNaN(n)) return '—';
  const a = Math.abs(n), sg = n < 0 ? '−' : '';
  if (a < 1e4) return num(Math.floor(a)) === '0' && a > 0 && a < 1 ? num(n, 1) : sg + num(Math.floor(a));
  const IN = LOCALE.endsWith('IN');
  const steps = IN ? [[1e7, ' Cr'], [1e5, ' L'], [1e3, 'K']] : [[1e9, 'B'], [1e6, 'M'], [1e3, 'K']];
  for (const [v, u] of steps) if (a >= v) return sg + sig3(a / v) + u;
  return sg + num(Math.floor(a));
};
// Trip meters: one decimal while small, whole km from 1,000, compact from 10,000.
export const tripv = (km) => (km < 1000 ? num(km, 1) : short(km));
export const clip = (t, n = 18) => { t = String(t ?? ''); return t.length > n ? t.slice(0, n - 1).trimEnd() + '…' : t; };
export const LIMITS = { nickname: 20, plate: 13, person: 30, item: 40, title: 60, place: 40, city: 24 };
export const dShort = (ts) => new Date(ts).toLocaleDateString(DL(), { day: 'numeric', month: 'short' });
export const dMed = (ts) => new Date(ts).toLocaleDateString(DL(), { weekday: 'short', day: 'numeric', month: 'short' });
export const dLong = (ts) => new Date(ts).toLocaleDateString(DL(), { weekday: 'long', day: 'numeric', month: 'long' });
export const dFull = (ts) => new Date(ts).toLocaleDateString(DL(), { day: 'numeric', month: 'short', year: 'numeric' });
export const tShort = (ts) => new Date(ts).toLocaleTimeString(DL(), { hour: 'numeric', minute: '2-digit' }).replace(/\s?(am|pm)$/i, (m) => ' ' + m.trim().toLowerCase());
export const dur = (sec) => { sec = Math.round(sec); const h = Math.floor(sec / 3600), m = Math.round((sec % 3600) / 60); return h ? `${h} h ${String(m).padStart(2, '0')}` : `${m} min`; };
export const clock = (sec) => { sec = Math.max(0, Math.floor(sec)); const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60; return (h ? h + ':' + String(m).padStart(2, '0') : m) + ':' + String(s).padStart(2, '0'); };
export const ago = (ts) => { const m = Math.round((Date.now() - ts) / 60000); if (m < 1) return 'just now'; if (m < 60) return `${m} min ago`; const h = Math.round(m / 60); if (h < 24) return `${h} h ago`; const d = Math.round(h / 24); return d === 1 ? 'yesterday' : `${d} days ago`; };
const plural = (n, a, b) => `${n} ${n === 1 ? a : b}`;
const words = ['No', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine'];
const W = (n) => words[n] || String(n);
const nb = (s) => String(s).replace(/ (\S+)$/, ' $1'); // keep the last two words together
export const dist = (m) => (m < 1000 ? { v: num(Math.max(0, Math.round(m / 10) * 10)), u: 'm' } : { v: num(m / 1000, m < 10000 ? 1 : 0), u: 'km' });
export const isoDateTime = (ts) => { const d = new Date(ts); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16); };
export const isoDate = (ts) => { const d = new Date(ts); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 10); };

export function segs(n, filled, cls = 's', extra = '') {
  let h = `<div class="segs ${extra}">`;
  for (let i = 0; i < n; i++) h += `<i class="${i < filled ? cls : ''}"></i>`;
  return h + '</div>';
}

// ---------- Icons ----------
export const selmark = (inline = false) => `<span class="selmark ${inline ? 'inline' : ''}" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg></span>`;
export const BODY_FONTS = [['system', 'System', "-apple-system, system-ui", 400], ['atkinson', 'Atkinson', "'Atkinson Hyperlegible Next'", 450], ['public', 'Public Sans', "'Public Sans'", 450]];
export const THEMES = [['auto', 'Auto', '#EDEFF2', '#F9FAFB', '#12151A'], ['paper', 'Light', '#EDEFF2', '#F9FAFB', '#12151A'], ['obsidian', 'Dark', '#0E1013', '#171A1F', '#EDEFF3'], ['amoled', 'Navy', '#0B1530', '#13203F', '#EAF0FF'], ['sketch', 'Paper', '#EEE7DA', '#F7F1E6', '#2A2620']];
export const ACCENTS = [['rust', '#BF4128', 'Rust'], ['wine', '#B3172A', 'Wine'], ['blue', '#1D56CF', 'Blue'], ['slate', '#5D697D', 'Stealth'], ['amber', '#C98316', 'Amber'], ['mint', '#2A8570', 'Mint']];
// Icons: Hugeicons (MIT), credited in About.
export const ICON_CREDIT = '<a href="https://hugeicons.com" target="_blank" rel="noopener">Hugeicons</a>';
// One icon family everywhere: Hugeicons (stroke). Unknown keys draw nothing rather than mixing styles.
// Duotone: the same drawing again underneath, thick and soft, so every icon gets an even second tone.
let ICON_STYLE = 'line';
export const setIconStyle = (v) => { ICON_STYLE = v === 'duo' || v === 'fill' ? v : 'line'; };
export const icon = (n, cls = '') => { const b = HUGE_ICONS[n] || ''; return `<svg class="i ${ICON_STYLE === 'fill' ? 'ifill ' : ''}${cls}" viewBox="-1 -1 26 26" ${HUGE_SET.attrs} aria-hidden="true">${ICON_STYLE === 'duo' && b ? `<g class="duo">${b}</g>` : ''}${b}</svg>`; };

// ---------- Vehicle pictures ----------
const lum = (h) => { const n = [1, 3, 5].map((i) => parseInt(String(h || '#888888').slice(i, i + 2), 16)); return (n[0] * 299 + n[1] * 587 + n[2] * 114) / 255000; };
// Bump when bike pictures change, so phones fetch the new ones instead of a saved copy.
export const ART_V = '75';
export function vehicleArt(s, v, size = 'thumb') {
  const cls = size === 'hero' ? 'art hero-art' : size === 'model' ? 'art model-art' : size === 'card' ? 'art card-art' : size === 'lg' ? 'art thumb lg' : 'art thumb';
  // Bikes without a photo: a soft tile tinted with the bike's colour, so it sits with the theme instead of shouting.
  const tile = () => `<div class="${cls} icon-mode" style="--bc:${v.colour}">${icon('bike')}</div>`;
  if (v.photo) return `<div class="${cls}"><img class="photo" src="${v.photo}" alt=""></div>`;
  const img = S.MODEL_IMAGES[v.model];
  if (img) return `<div class="${cls}"><img class="${img.fit === 'cover' ? 'photo' : 'model'}" src="${img.src}${img.src.includes('?') ? '&' : '?'}v=${ART_V}" onload="this.classList.add('ld')" alt="${esc(S.MODELS.find((m) => m.id === v.model)?.name || '')}" decoding="async"></div>`;
  return tile();
}
export function avatar(p, xl = false) {
  const init = (p.name || '?').trim().split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase() || '?';
  return `<div class="avatar ${xl ? 'xl' : ''}${p.avatar && !p.photo ? ' pix' : ''}">${p.photo ? `<img src="${p.photo}" alt="">` : p.avatar ? `<img src="img/avatars/${p.avatar}.svg" alt="">` : esc(init)}</div>`;
}


// ---------- Cluster maneuver arrows ----------
const G = {
  straight: { b: 'M50 112V18', h: 'M27 41L50 18L73 41' },
  right: { b: 'M34 112V66Q34 44 56 44H84', h: 'M66 26L84 44L66 62' },
  'slight-right': { b: 'M40 112V74L73 41', h: 'M51 37H77V63' },
  'sharp-right': { b: 'M34 112V40L72 78', h: 'M50 82H76V56' },
  'uturn-left': { b: 'M72 112V50Q72 22 49 22Q26 22 26 50V84', h: 'M10 68L26 84L42 68' },
  'keep-right': { f: 'M36 112V12', b: 'M36 112V82L64 54V22', h: 'M46 40L64 22L82 40' },
  'fork-right': { f: 'M50 112V66L28 22', b: 'M50 112V66L72 22', h: 'M53 30L73 19L79 41' },
  'ramp-right': { f: 'M38 112V12', b: 'M38 112V80Q38 62 54 52L78 36', h: 'M58 31L81 34L74 56' },
  merge: { f: 'M22 112Q22 74 50 56', b: 'M78 112Q78 74 50 56V18', h: 'M27 41L50 18L73 41' },
  'roundabout-right': { c: true, b: 'M50 112V80A20 20 0 1 1 72 58H90', h: 'M75 42L91 58L75 74' },
  destination: { flag: true },
  ferry: { ferry: true }, train: { train: true }, 'wrong-way': { wrong: true },
};
export function arrow(glyph, exit = 0, faint = 0.32, sw = 12) {
  const out = arrowRaw(glyph, exit, faint);
  return sw === 12 ? out : out.replace(/stroke-width="([\d.]+)"/g, (m, v) => `stroke-width="${(v * sw / 12).toFixed(1)}"`);
}
function arrowRaw(glyph, exit = 0, faint = 0.32) {
  let g = glyph, mirror = false;
  const m = glyph.match(/^(.*)-left$/);
  if (m && glyph !== 'uturn-left') { g = m[1] + '-right'; mirror = true; }
  if (glyph === 'left') { g = 'right'; mirror = true; }
  if (glyph === 'uturn-right') { g = 'uturn-left'; mirror = true; }
  const d = G[g] || G.straight;
  const st = 'fill="none" stroke="var(--accent)" stroke-width="12" stroke-linecap="round" stroke-linejoin="round"';
  let inner = '';
  if (d.flag) inner = `<path d="M30 112V14" ${st}/><path d="M30 16H82L70 34L82 52H30Z" fill="var(--accent)"/>`;
  else if (d.ferry) inner = `<path d="M14 72H86L74 94H26Z" fill="var(--accent)"/><path d="M36 72V54H64V72M50 54V34" ${st} stroke-width="9"/><path d="M8 110q10-8 21 0t21 0t21 0t21 0" ${st} stroke-width="6" opacity="${faint}"/>`;
  else if (d.train) inner = `<rect x="24" y="18" width="52" height="66" rx="12" ${st} stroke-width="9"/><path d="M34 38H66M34 66h4M62 66h4" ${st} stroke-width="8"/><path d="M8 110q10-8 21 0t21 0t21 0t21 0" ${st} stroke-width="6" opacity="${faint}"/>`;
  else if (d.wrong) inner = `<circle cx="50" cy="62" r="36" ${st} stroke-width="10"/><path d="M30 62H70" ${st}/>`;
  else {
    inner = (d.f ? `<path d="${d.f}" ${st} opacity="${faint}"/>` : '') + (d.c ? `<circle cx="50" cy="58" r="22" fill="none" stroke="var(--accent)" stroke-width="8" opacity="${faint}"/>` : '') + `<path d="${d.b}" ${st}/><path d="${d.h}" ${st}/>`;
  }
  const body = mirror ? `<g transform="translate(100 0) scale(-1 1)">${inner}</g>` : inner;
  const ex = d.c && exit ? `<text x="50" y="65" text-anchor="middle" font-family="var(--mono)" font-size="20" font-weight="600" fill="var(--ink)">${exit}</text>` : '';
  return `<svg viewBox="0 0 100 124" aria-hidden="true">${body}${ex}</svg>`;
}


// ---------- Shared bits ----------
const topbar = (left, right = '') => `<div class="topbar">${left}${right || '<span></span>'}</div>`;
const backBtn = (to = 'today') => `<button class="circle" data-act="go" data-arg="${to}" aria-label="Back">${icon('back')}</button>`;
const sh = (left, right = '', help = '') => `<div class="section-h"><span class="sh-l"><span class="eyebrow">${left}</span>${help ? `<button class="info" data-act="sheet" data-arg="help:${help}" aria-label="About ${left}">${icon('info')}</button>` : ''}</span>${right}</div>`;

// What a section is for, in a sheet. Opened from the (i) beside a section title.
export const HELP = {
  readings: ['Today', 'Readings', ['These numbers come from your bike’s cluster over Bluetooth. When the bike isn’t linked, rides you record keep the odometer and trips moving.', 'Tap Trip A or Trip B to reset it. Range is the fuel left times your recent mileage, so it gets better after a few full tanks.']],
  reminders: ['Logbook', 'Reminders', ['Each job is due after a distance or a time, whichever comes first.', 'The bar shows how much is left and empties as the job gets closer. Green is fine, amber means due soon, and a full red bar means overdue. Tap a job to mark it done or change how often it repeats.']],
  mechanic: ['Logbook', 'For the mechanic', ['Notes to show at your next service: a noise, a loose part, a question about price. Tick each one off once it’s sorted.']],
  visits: ['Logbook', 'Workshop visits', ['A record of each service: what was done, where, the odometer and the bill. Handy when you sell the bike or claim warranty.']],
  mileage: ['Fuel', 'How mileage is worked out', ['Fill the tank to the brim, ride, then fill to the brim again. The litres of the second fill, plus any top-ups in between, divided by the kilometres ridden, is your mileage.', 'Top-ups alone can’t give a figure, so log them as top-ups and the next full tank does the maths.']],
  cost: ['Fuel', 'Cost', ['What you’ve spent on fuel and what each kilometre costs you, from the fill-ups you logged.']],
  rehearse: ['Route', 'Rehearse a route', ['Plays a sample ride on your bike’s display, turn by turn, without moving. Use it to learn how the arrows and countdown look before a real trip.']],
  testturn: ['Route', 'Test one turn', ['Sends a single arrow to the bike. Pick a turn, slide the distance and add a street name to see exactly how it shows.', 'Under 100 m the arrow blinks, just as it will on the road.']],
  display: ['Route', 'Display', ['Choices for what the bike shows while you navigate.']],
  showbike: ['Alerts', 'Show on the bike', ['Choose which alerts reach your bike’s cluster. Only a name and a count are sent, never the message itself.']],
  send: ['Alerts', 'Send to the bike', ['Browsers can’t see your phone’s calls or chats, so this lets you send one by hand to try the cluster.', 'Ring shows an incoming call, Missed adds a missed call, and Chat shows a WhatsApp alert with the name you typed.']],
  handlebar: ['Alerts', 'Handlebar button', ['The bike’s handlebar button reports presses to this app.', 'A tap answers a call shown here; a long press declines or ends it. With no call, a tap means play or pause and a long press means next track. A browser can’t control your music app, so those are logged in Recent.']],
  riding: ['Settings', 'Riding', ['How ride mode behaves: whether it opens by itself when you start moving, whether rides record themselves, and whether the screen stays on.']],
  papers: ['Settings', 'Records', ['Your bike’s service logbook, fuel log and documents, all kept on this phone.']],
  backup: ['Settings', 'Data & region', ['Your country sets the currency, fuel price and emergency numbers. Everything else lives on this phone only. Export a backup before you change phones or clear your browser, then restore it on the new one.']],
  preview: ['Developer', 'Preview states', ['Shows the app as a brand-new rider would see it, or with extreme data: very long names, huge numbers and everything overdue. Your real data is put aside and comes back when you tap Exit.']],
  developer: ['Settings', 'Developer', ['Tools for trying the app without a bike, and for seeing the raw Bluetooth messages it sends.']],
  medical: ['Profile', 'Medical ID', ['Shown on the SOS screen so whoever helps you knows your blood group, allergies and who to call. It stays on this phone.']],
};
const sw = (on, act, arg = '', dis = false) => `<button class="switch ${on ? 'on' : ''} ${dis ? 'dis' : ''}" data-act="${act}" data-arg="${arg}" role="switch" aria-checked="${on}" aria-label="Toggle" ${dis ? 'disabled' : ''}></button>`;
const li = ({ ic, lead, t, d, end = '', act = '', arg = '', cls = '', chev = true, tag = 'button', href = '' }) =>
  `<${tag} class="li ${cls} ${ic || lead ? '' : 'noicon'}" ${act ? `data-act="${act}" data-arg="${esc(arg)}"` : ''} ${href ? `href="${href}" target="_blank" rel="noopener"` : ''}>${lead || (ic ? `<span class="ic">${icon(ic)}</span>` : '')}<span class="tx"><div class="t">${t}</div>${d ? `<div class="d">${d}</div>` : ''}</span><span class="end">${end}${chev && (act || href) ? icon('chev') : ''}</span></${tag}>`;

// ---------- Logbook helpers ----------
export function sortedService(s) {
  const rank = { overdue: 0, soon: 1, ok: 2 };
  return s.service.map((it) => ({ it, st: S.serviceStatus(s, it) }))
    .sort((a, b) => rank[a.st.status] - rank[b.st.status] || b.st.frac - a.st.frac);
}
const everyText = (it) => {
  if (it.milestone) return `next at ${num(it.targetKm)} km`;
  const k = it.everyKm > 0 ? `${num(it.everyKm)} km` : '';
  const months = it.everyDays >= 28 ? Math.round(it.everyDays / 30.4) : 0;
  const t = it.everyDays > 0 ? (months ? plural(months, 'month', 'months') : it.everyDays % 7 === 0 ? plural(it.everyDays / 7, 'week', 'weeks') : plural(it.everyDays, 'day', 'days')) : '';
  return 'every ' + [k, t].filter(Boolean).join(' or ');
};
function rowValue(st) {
  if (st.status === 'overdue') {
    if (st.overdueKm > 0) return { v: short(st.overdueKm), u: 'km overdue', cls: 'red' };
    return { v: short(st.overdueDays), u: st.overdueDays === 1 ? 'day overdue' : 'days overdue', cls: 'red' };
  }
  if (st.byTime || st.remKm == null) return { v: short(st.remDays), u: st.remDays === 1 ? 'day' : 'days', cls: '' };
  return { v: short(st.remKm), u: 'km', cls: '' };
}
// Progress style for service jobs. Each reads "how much is left" a different way.
export const PROGRESS_STYLES = [
  ['gauge', 'Fuel gauge', 'Bars from E to F, like the bike'],
  ['road', 'On the road', 'The road travelled since the last service'],
  ['tread', 'Tyre tread', 'Tall blocks are tread left; they wear flat'],
  ['dipstick', 'Dipstick', 'Oil level between MIN and MAX'],
];
let PROGRESS = 'gauge';
export const setProgress = (v) => { PROGRESS = PROGRESS_STYLES.some(([k]) => k === v) ? v : 'gauge'; };
function progressBar(st, extra = '') {
  const over = st.status === 'overdue', left = over ? 0 : Math.max(0.03, 1 - st.frac), pct = Math.round(left * 100);
  const cls = over ? 'r' : st.status === 'soon' ? 'a' : 'k', label = over ? 'Overdue' : `${pct}% left`;
  const wrap = (kind, inner) => `<div class="pg ${kind} ${cls} ${extra}" role="img" aria-label="${label}">${inner}</div>`;
  if (PROGRESS === 'road') {
    // The bike has covered `frac` of the way since the last service; the flag is the next one.
    const at = over ? 100 : Math.min(96, Math.max(4, st.frac * 100));
    return wrap('p-road', `<div class="rd"><i class="done" style="width:${at}%"></i></div><div class="pl"><span>Last service</span><span>${over ? 'Overdue' : 'Next'}</span></div>`);
  }
  if (PROGRESS === 'tread') {
    const n = 16, f = over ? 0 : Math.max(1, Math.round(left * n)); let t = '';
    for (let i = 0; i < n; i++) t += `<i class="${i < f ? 'on' : ''}"></i>`;
    return wrap('p-tread', `<div class="tb">${t}</div><div class="pl"><span>${over ? 'Worn out. Time to service' : `${pct}% tread left`}</span><span></span></div>`);
  }
  if (PROGRESS === 'dipstick') {
    const lvl = over ? 4 : 8 + left * 78;
    return wrap('p-dip', `<div class="ds"><i class="oil" style="width:${lvl}%"></i><span class="mk mn"></span><span class="mk mx"></span><span class="hd"></span></div><div class="pl"><span style="margin-left:6%">MIN</span><span style="margin-right:12%">MAX</span></div>`);
  }
  return null;
}
function serviceSegs(st, n = 10, extra = '') {
  const alt = progressBar(st, extra); if (alt) return alt;
  const fresh = st.status === 'overdue' ? 0 : Math.max(1, Math.round((1 - st.frac) * n));
  const cls = st.status === 'overdue' ? 'r' : st.status === 'soon' ? 'a' : 'k';
  let h = '';
  for (let i = 0; i < n; i++) h += `<i class="${i < fresh ? cls : st.status === 'overdue' ? 'ro' : ''}"></i>`;
  return `<div class="fgauge ${extra}" role="img" aria-label="${st.status === 'overdue' ? 'Overdue' : Math.round((1 - st.frac) * 100) + '% left'}"><span class="${st.status === 'overdue' ? 'red' : ''}">E</span><div class="fg">${h}</div><span>F</span></div>`;
}
export function dueSentence(st) {
  if (st.status === 'overdue') {
    const parts = [];
    if (st.overdueKm > 0) parts.push(`${num(st.overdueKm)} km`);
    if (st.overdueDays > 0) parts.push(plural(st.overdueDays, 'day', 'days'));
    return `Overdue by ${parts.join(' and ')}. Do it on your next stop.`;
  }
  const km = st.remKm != null ? `in ${num(st.remKm)} km` : null;
  const days = st.remDays != null ? `by ${dShort(st.dueDate)}` : null;
  if (km && days) return nb(`Due ${st.byTime ? days + ' or ' + km : km + ' or ' + days}, whichever comes first.`);
  return nb(`Due ${km ? km + ', at ' + num(st.dueKm) + ' km' : days}.`);
}

function verdictOf(s, list) {
  const over = list.filter((x) => x.st.status === 'overdue').length;
  const soon = list.filter((x) => x.st.status === 'soon').length;
  const ok = list.length - over - soon;
  let word, line, cls;
  if (over) { word = 'Needs care'; cls = 'bad'; line = over === 1 ? 'One job is overdue.' : `${W(over)} jobs are overdue.`; }
  else if (soon) { word = 'Almost due'; cls = 'soon'; line = soon === 1 ? 'One job is due soon. The rest look good.' : `${W(soon)} jobs are due soon.`; }
  else { word = 'Road-ready'; cls = ''; line = 'Everything is in good shape.'; }
  const bar = list.map((x) => x.st.status === 'overdue' ? 'r' : x.st.status === 'soon' ? 'a' : 's');
  return { word, line, cls, over, soon, ok, bar };
}
const heroBar = (bar) => `<div class="segs thick">${bar.map((c) => `<i class="${c}"></i>`).join('')}</div>`;
const caption = (v) => `<div class="eyebrow caption">${v.ok} on track · ${v.soon} due soon · ${v.over} overdue</div>`;

// ---------- TODAY ----------
// ---------- Weather: header on Today, full forecast in a sheet ----------
const hourFmt = (ts) => new Date(ts).toLocaleTimeString('en-IN', { hour: 'numeric' }).replace(/\s?(am|pm)/i, (m) => m.trim().toLowerCase());
export function weatherHead(c) {
  const w = c.live.weather || { state: 'idle' }, d = w.data;
  let ic = 'wxPart', temp = '', sub = '';
  if (d) {
    const rain = Math.max(0, ...d.hours.map((h) => h.rain || 0)), temps = d.hours.map((h) => h.temp).filter((t) => t != null);
    ic = S.wmo(d.code, d.day).ic; temp = `${Math.round(d.temp)}°`;
    sub = `${esc(d.place || 'Near you')} · ${rain >= 20 ? `${rain}% rain` : temps.length ? `${Math.round(Math.min(...temps))}–${Math.round(Math.max(...temps))}° today` : `feels ${Math.round(d.feels)}°`}`;
  } else sub = w.state === 'need' ? 'Add weather for your rides' : w.state === 'err' ? 'Weather offline' : 'Checking the weather…';
  return `<span class="wh-ic">${icon(ic)}</span>${temp ? `<span class="wh-t">${temp}</span>` : ''}<span class="wh-s">${sub}</span>${icon('chev')}`;
}
export function weatherSheet(c) {
  const w = c.live.weather || { state: 'idle' }, d = w.data;
  const loc = `<div class="btns mt16" style="gap:8px"><button class="btn line sm grow" data-act="wx-locate">${icon('pin')} Use my location</button><button class="btn line sm grow" data-act="go" data-arg="profile">${icon('profile')} Set city</button></div>`;
  if (!d) {
    const msg = w.state === 'need' ? 'Add your city in Profile, or share your location, to see rain, heat and wind for your ride.'
      : w.state === 'err' ? esc(w.msg || 'Couldn’t reach the weather service.') : 'Checking the sky…';
    return `<div class="eyebrow">Weather</div><h2>${w.state === 'err' ? 'Weather offline' : w.state === 'need' ? 'Weather for your ride' : 'Loading'}</h2><p class="lead">${msg}</p>
      ${w.state === 'err' ? `<button class="btn ink block mt16" data-act="wx-refresh">${icon('refresh')} Try again</button>` : ''}${w.state === 'need' || w.state === 'err' ? loc : ''}`;
  }
  const cond = S.wmo(d.code, d.day), hint = S.rideHint(d);
  const tone = d.code >= 61 || /Rain likely|Thunder|Fog|Gusty|Hot/.test(hint) ? 'warn' : 'ok';
  const rainMax = Math.max(0, ...d.hours.map((h) => h.rain || 0));
  return `<div class="eyebrow">Weather · ${esc(d.place || 'Near you')}</div>
    <div class="wx-big"><span class="wx-ic big-ic">${icon(cond.ic)}</span><div><div><span class="big wx-temp">${Math.round(d.temp)}°</span></div><div class="wx-cond">${cond.t}</div></div></div>
    <p class="wx-hint ${tone}">${icon(tone === 'warn' ? 'drops' : 'check')}<span>${hint}</span></p>
    <div class="card stat3 mt16"><div><span class="eyebrow">Feels like</span><span class="big">${Math.round(d.feels)}°</span></div><div><span class="eyebrow">Wind</span><span class="big">${Math.round(d.wind)}<span class="unit">km/h</span></span></div><div><span class="eyebrow">Rain, 12 h</span><span class="big">${rainMax}<span class="unit">%</span></span></div></div>
    <div class="eyebrow mt20">Next 12 hours</div>
    <div class="wx-hours scroll">${d.hours.slice(0, 12).map((h, i) => `<div><span class="eyebrow">${i ? hourFmt(h.ts) : 'Now'}</span>${icon(S.wmo(h.code, h.day).ic)}<b>${Math.round(h.temp)}°</b><span class="rp ${h.rain >= 50 ? 'hi' : ''}">${h.rain >= 20 ? h.rain + '%' : ''}</span></div>`).join('')}</div>
    <p class="hint">Updated ${ago(d.ts)} · Open-Meteo</p>
    <button class="btn line block mt12 ${w.state === 'loading' ? 'busy' : ''}" data-act="wx-refresh">${icon('refresh')} Refresh</button>
    <details class="wx-place"><summary>${icon('pin')}<span class="grow">Showing ${esc(d.place || 'your location')}</span><span class="link">Change</span></summary>${loc}</details>`;
}

export function today(c) {
  const { s, link, live } = c;
  const v0 = S.veh(s);
  const list = sortedService(s);
  const v = verdictOf(s, list);
  const fs = S.fuelStats(s);
  let line = v.line;
  if (fs.onReserve) line = `On reserve, about ${num(fs.range)} km left.` + (v.over || v.soon ? ' ' + line : '');
  const missed = s.alerts.missed;
  const urgent = list.filter((x) => x.st.status !== 'ok').slice(0, 4);
  const good = !v.over && !v.soon && !fs.onReserve;
  const cc = connCard(c, good), ccIn = cc.startsWith('<div class="bc-conn');
  return `<div class="stagger">
  <div class="pagehead today-head"><div class="th-row"><div style="min-width:0"><h1 class="title">${new Date().toLocaleDateString(DL(), { weekday: 'long' })}</h1><div class="subtitle">${s.sample || live.scenario || link.simulated ? '<span class="demo-pill"><i></i>Demo mode</span>' : new Date().toLocaleDateString(DL(), { day: 'numeric', month: 'long' })}</div></div>
    <div class="head-actions"><button class="circle" data-act="sheet" data-arg="sos" aria-label="Emergency SOS" style="color:var(--red)">${icon('sos')}</button></div></div></div>
  ${live.scenario ? previewBar(live) : ''}
  <div class="bikecard">
    <button class="bc-main" data-act="sheet" data-arg="garage" aria-label="Switch vehicle">${vehicleArt(s, v0, 'lg')}<div class="grow"><div class="t">${esc(S.vehicleTitle(s))}</div><div class="m">${esc(v0.nickname ? S.bikeName(s) : S.model(s).series)} · ${short(s.odo)} km</div><div class="d">${esc(v0.plate || 'No number plate')}</div></div><span class="bc-swap">${icon('updown')}</span></button>
    ${live.rec ? recRow(live, ccIn ? 'link ok' : '') : ccIn ? cc : good ? `<div class="bc-conn conn-compact"><button class="cc-main" data-act="go" data-arg="logbook"><span class="cc-ic good">${icon('ok')}</span><span class="cc-tx"><span class="cc-t">Road ready</span><span class="cc-s">All ${v.ok} jobs on track</span></span></button></div>` : ''}
    <button class="bc-wx" data-act="sheet" data-arg="weather" aria-label="Weather details">${weatherHead(c)}</button>
  </div>
  ${good ? '' : `<button class="hero ${fs.onReserve && !v.over ? 'soon' : v.cls}" data-act="go" data-arg="logbook">
    <div class="eyebrow">Verdict</div>
    <div class="verdict">${v.word}</div>
    <p>${line}</p>
    ${v.over || v.soon ? heroBar(v.bar) + caption(v) : `<div class="eyebrow caption">All ${v.ok} jobs on track</div>`}
  </button>`}
  <div class="section" style="margin-top:22px">${sh(urgent.length ? 'Do these first' : 'Coming up', '<button class="link" data-act="go" data-arg="logbook">See all</button>')}
    <div class="upnext two-up">${(urgent.length ? urgent : list).slice(0, 4).map(({ it, st }) => upCard(it, st)).join('')}</div></div>
  ${missed.length ? `<button class="banner" data-act="go" data-arg="alerts">${icon('missed')}<span class="t"><span class="big" style="font-size:20px">${missed.length}</span> missed ${missed.length === 1 ? 'call' : 'calls'} · ${esc(missed[missed.length - 1].name)}</span>${icon('chev', 'faint')}</button>` : ''}
  ${s.parking && !link.connected ? `<button class="banner plain" data-act="sheet" data-arg="parking">${icon('pin')}<span class="t">Parked ${ago(s.parking.ts)} <span class="faint small">· walk back</span></span>${icon('chev', 'faint')}</button>` : ''}
  ${ccIn ? '' : cc}
  <div class="section">${sh('Readings', `<span class="eyebrow">${esc(S.model(s).series)}</span>`, 'readings')}
    <div class="card readings">
      <div class="odo"><span class="eyebrow">Odometer</span><span><span class="big" data-live="odo">${num(Math.floor(s.odo))}</span><span class="unit">km</span></span></div>
      <div class="grid3">
        <button data-act="sheet" data-arg="trip:A"><span class="eyebrow">Trip A</span><span class="big">${tripv(s.tripA.km)}<span class="unit">km</span></span></button>
        <button data-act="sheet" data-arg="trip:B"><span class="eyebrow">Trip B</span><span class="big">${tripv(s.tripB.km)}<span class="unit">km</span></span></button>
        <div><span class="eyebrow">Speed</span><span class="big"><span data-live="speed">${num(live.speed)}</span><span class="unit">km/h</span></span></div>
        <button data-act="go" data-arg="fuel"><span class="eyebrow">Fuel</span>${fuelBars(fs)}</button>
        <button data-act="go" data-arg="fuel"><span class="eyebrow">Range</span><span class="big">${fs.range != null ? '≈' + num(fs.range) : '—'}<span class="unit">km</span></span></button>
        ${live.engine != null ? `<div><span class="eyebrow">Engine</span><span class="big"><span data-live="engine">${live.engine}</span><span class="unit">°C</span></span></div>` : `<div><span class="eyebrow">Phone</span><span class="big">${live.battery ?? '—'}<span class="unit">%</span></span></div>`}
      </div>
    </div></div>
  <div class="section">${sh('Shortcuts')}
    <div class="hscroll">
      ${tile('fuel', 'Fuel', fs.range != null ? (fs.onReserve ? 'r' : 's') : 'k', fs.range != null ? `≈${num(fs.range)} km left` : 'No tanks yet', segs(6, fs.bars ?? 0, fs.onReserve ? 'r' : 'k'))}
      ${tile('logbook', 'Logbook', v.over ? 'r' : v.soon ? 'a' : 's', v.over + v.soon ? `${v.over + v.soon} need care` : 'All in order', `<div class="segs">${v.bar.map((x) => `<i class="${x}"></i>`).join('')}</div>`)}
      ${tile('trips', 'Rides', 's', `${s.trips.length} logged`, segs(6, Math.min(6, S.periodStats(s.trips, S.weekStart()).rides), 'k'))}
      ${tile('glovebox', 'Glovebox', live.gloveHere.length ? 's' : 'k', live.gloveHere.length ? `${live.gloveHere.length} ${live.gloveHere.length === 1 ? 'paper' : 'papers'}` : 'Empty', segs(4, Math.min(4, live.gloveHere.length), 'k'))}
    </div></div>
  <p class="note mt24">Everything stays on this phone. No account, no cloud.</p>
  </div>`;
}
export function inbox(c) {
  const { s, live } = c;
  const out = [];
  for (const { it, st } of sortedService(s)) if (st.status !== 'ok') out.push({ ic: it.icon || 'wrench', tone: st.status === 'overdue' ? 'red' : 'amber', t: `${it.name} ${st.status === 'overdue' ? 'is overdue' : 'is due soon'}`, d: rowValue(st).v + ' ' + rowValue(st).u, act: 'sheet', arg: 'svc:' + it.id });
  const fs = S.fuelStats(s);
  if (fs.onReserve) out.push({ ic: 'fuel', tone: 'red', t: 'You’re on reserve', d: `About ${num(fs.range)} km left`, act: 'go', arg: 'fuel' });
  if (s.alerts.missed.length) out.push({ ic: 'missed', tone: 'red', t: `${plural(s.alerts.missed.length, 'missed call', 'missed calls')}`, d: `Last from ${esc(s.alerts.missed[s.alerts.missed.length - 1].name)}`, act: 'go', arg: 'alerts' });
  if (s.alerts.wa.length) out.push({ ic: 'chat', tone: 'sage', t: `${plural(s.alerts.wa.length, 'WhatsApp message', 'WhatsApp messages')}`, d: `Last from ${esc(s.alerts.wa[s.alerts.wa.length - 1].name)}`, act: 'go', arg: 'alerts' });
  const soon = (ts) => ts && ts - Date.now() < 30 * 86400000;
  for (const d of live.gloveHere || []) if (soon(d.expiry)) out.push({ ic: 'doc', tone: d.expiry < Date.now() ? 'red' : 'amber', t: `${{ rc: 'RC', insurance: 'Insurance', puc: 'PUC', licence: 'Licence', other: 'A paper' }[d.slot.split(':')[1]] || 'A paper'} ${d.expiry < Date.now() ? 'has expired' : 'expires soon'}`, d: dFull(d.expiry), act: 'sheet', arg: 'doc:' + d.slot });
  if (soon(s.profile.licenceExpiry)) out.push({ ic: 'idcard', tone: 'amber', t: 'Your licence needs renewing', d: dFull(s.profile.licenceExpiry), act: 'go', arg: 'profile' });
  return out;
}
export const previewBar = (live) => `<div class="preview-bar">${icon('flask')}<span>Previewing ${live.scenario === 'empty' ? 'the empty state' : 'edge cases'}. Nothing is saved.</span><button class="chip" data-act="scenario" data-arg="real">Exit</button></div>`;
// Ride in progress, as the row inside the Today bike card.
function recRow(live) {
  const r = live.rec;
  return `<div class="bc-conn conn-compact rec-row"><button class="cc-main" data-act="go" data-arg="trips"><span class="cc-ic rec"><span class="dot x pulse"></span>${icon('trips')}</span><span class="cc-tx"><span class="cc-t">Ride in progress</span><span class="cc-s"><span data-live="rec-km">${num(r.km, 2)}</span> km · <span data-live="rec-time">${clock((Date.now() - r.start) / 1000)}</span> · <span data-live="speed">${num(live.speed)}</span> km/h</span></span></button><button class="btn sm cc-act" data-act="sheet" data-arg="rec-end" style="background:var(--accent);color:var(--on-accent)">${icon('stop')}End</button></div>`;
}
function recCard(live) {
  const r = live.rec;
  return `<div class="card pad mt12 flex gap12" style="align-items:center"><div class="grow"><span class="tag x"><span class="dot x pulse"></span>Ride in progress</span><div class="flex gap12 mt12" style="align-items:baseline"><span class="big" style="font-size:34px"><span data-live="rec-km">${num(r.km, 2)}</span><span class="unit">km</span></span><span class="small muted"><span data-live="rec-time">${clock((Date.now() - r.start) / 1000)}</span> · <span data-live="speed">${num(live.speed)}</span> km/h</span></div></div><button class="circle" data-act="sheet" data-arg="rec-end" aria-label="End ride" style="background:var(--accent);color:var(--on-accent)">${icon('stop')}</button></div>`;
}
function tile(to, name, dot, status, bar) {
  const ic = { fuel: 'fuel', logbook: 'wrench', trips: 'trips', glovebox: 'folder' }[to];
  return `<button class="card tile" data-act="${to === 'glovebox' ? 'sheet' : 'go'}" data-arg="${to}"><span class="tile-ic ${dot}">${icon(ic)}</span><div class="serif">${name}</div><div class="status"><span class="dot ${dot === 's' ? '' : dot}"></span>${status}</div>${bar}</button>`;
}
export function fuelBars(fs, big = false) {
  const n = fs.bars;
  let h = `<div class="fuelbars ${fs.onReserve ? 'low' : ''}" ${big ? 'style="height:28px"' : ''}>`;
  for (let i = 0; i < 6; i++) h += `<i class="${n != null && i < n ? 'on' : ''}" style="height:${45 + i * 11}%"></i>`;
  return h + `<span class="faint small" style="margin-left:6px">${n != null ? n + '/6' : '—'}</span></div>`;
}
function connCard(c, good = false) {
  const { s, link } = c;
  const st = link.state;
  const name = esc(S.vehicleTitle(s));
  const known = !!(link.device || s.bike?.id || link.simulated);
  let title, sub, dot, mode, body;
  if (st === 'connected') {
    title = 'Connected'; dot = ''; mode = 'on'; sub = link.simulated ? `${name} · simulated` : `${name} · ${esc(link.deviceName)}`;
    body = `<button class="btn accent block" data-act="ride">${icon('speed')} Ride mode</button><p class="note">${s.settings.autoRide ? 'Opens by itself once you start riding.' : 'Turns, calls and the time go to the display.'}</p>`;
  } else if (st === 'connecting') {
    title = 'Connecting'; dot = 'a pulse'; mode = 'connecting'; sub = `Linking with ${name}`;
    body = `<div class="btns"><button class="btn line" data-act="cancel-link">Cancel</button><button class="btn ghost" data-act="ride">${icon('speed')} Ride mode</button></div>`;
  } else if (st === 'reconnecting' || st === 'picking') {
    title = st === 'picking' ? 'Pick your bike' : 'Searching'; dot = 'a pulse'; mode = 'search';
    sub = st === 'picking' ? 'Choose it in the Bluetooth list' : 'Switch on the ignition. It links by itself.';
    body = `<div class="btns"><button class="btn line" data-act="cancel-link">Stop</button><button class="btn ghost" data-act="ride">${icon('speed')} Ride mode</button></div>`;
  } else if (known) {
    title = 'Not connected'; dot = 'k'; mode = 'idle'; sub = `${name} · paired`;
    body = `<div class="btns"><button class="btn ink" data-act="reconnect">${icon('bt')} Reconnect</button><button class="btn ghost" data-act="ride">${icon('speed')} Ride mode</button></div>`;
  } else {
    title = 'No bike linked'; dot = 'k'; mode = 'idle'; sub = 'Pair once. It reconnects by itself.';
    body = link.supported
      ? `<button class="btn ink block" data-act="pair">${icon('bt')} Pair your bike</button><button class="btn line block mt8" data-act="sim-on">Try the simulated bike</button>`
      : isIOS() ? `<button class="btn ink block" data-act="sheet" data-arg="ios-pair">${icon('bt')} Pair on iPhone</button><button class="btn line block mt8" data-act="sim-on">${icon('bolt')} Try the simulated bike</button><p class="note">iPhone browsers need one extra app for Bluetooth. It takes a minute.</p>`
      : `<button class="btn ink block" data-act="sim-on">${icon('bolt')} Try the simulated bike</button><p class="note">To pair a real bike, open this in Chrome on Android.</p>`;
  }
  // Linked, linking or paired: one compact row. Only a bike that was never paired gets the full card.
  if (true) {
    const act = st === 'connected' ? `<button class="btn accent sm cc-act" data-act="ride">${icon('speed')}Ride</button>`
      : !known ? `<button class="btn ink sm cc-act" data-act="sheet" data-arg="pair-intro">${icon('bt')}Pair</button>`
      : st === 'idle' || st === 'error' ? `<button class="btn ink sm cc-act" data-act="reconnect">${icon('bt')}Connect</button>`
      : `<button class="btn line sm cc-act" data-act="cancel-link">${st === 'connecting' ? 'Cancel' : 'Stop'}</button>`;
    const t = good ? 'Road ready' : title, sb = good ? `${title} · ${sub}` : sub;
    return `<div class="bc-conn conn-compact"><button class="cc-main" data-act="sheet" data-arg="bike" aria-label="Bike link details"><span class="cc-ic ${mode}${good ? ' good' : ''}">${icon(good ? 'ok' : 'signal')}<span class="dot ${dot}"></span></span><span class="cc-tx"><span class="cc-t">${t}</span><span class="cc-s">${sb}</span></span></button>${act}</div>`;
  }
  return `<div class="card conn mt12">
    <div class="conn-head"><div><div class="serif">${title}</div><div class="status mt8"><span class="dot ${dot}"></span>${sub}</div></div>
    <button class="icon-btn" data-act="sheet" data-arg="bike" aria-label="Bluetooth details">${icon(st === 'connected' ? 'btOn' : mode === 'search' ? 'btSearch' : 'bt')}</button></div>
    <div class="link-line ${mode}"><span class="end-ic dev">${icon('phoneDev')}</span><div class="track ${mode}"></div><span class="end-ic bk">${icon('bike')}</span></div>
    ${body}</div>`;
}
// A compact card for the Today strip: icon, what's left, name, and a thick bar of what remains.
export function upCard(it, st) {
  const v = rowValue(st);
  const tone = st.status === 'overdue' ? 'r' : st.status === 'soon' ? 'a' : 's';
  const left = st.status === 'overdue' ? 1 : Math.max(0.04, 1 - st.frac);
  return `<button class="upcard ${tone}" data-act="sheet" data-arg="svc:${it.id}">
    <div class="uc-top"><span class="row-ic ${tone}">${icon(it.icon || 'wrench')}</span><span class="uc-tag">${st.status === 'overdue' ? 'Overdue' : st.status === 'soon' ? 'Due soon' : 'On track'}</span></div>
    <div class="uc-v"><span class="big">${v.v}</span><span class="unit">${v.u.replace(' overdue', '')}${st.status === 'overdue' ? ' late' : ' left'}</span></div>
    <div class="uc-n">${esc(it.name)}</div>
    ${st.status === 'soon' || st.status === 'overdue' ? serviceSegs(st, 8, 'sm') : ''}</button>`;
}
export function serviceRow(it, st) {
  const v = rowValue(st);
  const tone = st.status === 'overdue' ? 'r' : st.status === 'soon' ? 'a' : 's';
  return `<button class="row has-ic" data-act="sheet" data-arg="svc:${it.id}" data-flip="${it.id}">
    <div class="svc-grid"><span class="row-ic ${tone}">${icon(it.icon || 'wrench')}</span><div class="row-title">${esc(it.name)}</div>
    <div class="row-val"><span class="big">${v.v}</span><span class="unit">${v.u.replace(' overdue', '')}</span></div>
    <div class="row-sub">${st.status === 'overdue' ? '<span class="opill">Overdue</span>' : ''}${everyText(it)}</div></div>
    ${st.status === 'soon' || st.status === 'overdue' ? serviceSegs(st) : ''}</button>`;
}

// ---------- LOGBOOK ----------
export function logbook(c) {
  const { s } = c;
  const list = sortedService(s);
  const v = verdictOf(s, list);
  const spent = s.records.reduce((a, r) => a + (+r.cost || 0), 0);
  const cur = S.country(s).cur;
  return `<div class="stagger">
  <header class="pagehead">${topbar(backBtn(), `<button class="circle" data-act="sheet" data-arg="svc-edit:new" aria-label="Add reminder">${icon('plus')}</button>`)}
  <h1 class="title">Logbook</h1><div class="subtitle">Service for ${esc(clip(S.vehicleTitle(s), 24))}</div></header>
  <div class="hero ${v.cls}"><div class="eyebrow">Verdict</div><div class="verdict">${v.word}</div><p>${v.line}</p>${v.over || v.soon ? heroBar(v.bar) + caption(v) : `<div class="eyebrow caption">All ${v.ok} jobs on track</div>`}</div>
  <button class="card pad mt12 flex between" style="width:100%" data-act="sheet" data-arg="odo"><span class="eyebrow">Odometer now</span><span class="flex gap8"><span><span class="big" style="font-size:30px">${num(Math.floor(s.odo))}</span><span class="unit">km</span></span>${icon('edit', 'faint')}</span></button>
  <div class="section">${sh('Reminders · most urgent first', '', 'reminders')}
    <div class="rows" id="svc-rows">${list.map(({ it, st }) => serviceRow(it, st)).join('') || '<div class="empty">No reminders yet. Tap + to add one.</div>'}</div></div>
  <div class="section">${sh('Workshop visits', '<button class="link" data-act="sheet" data-arg="rec:new">Add visit</button>', 'visits')}
    ${s.records.length ? `<div class="list">${[...s.records].sort((a, b) => b.date - a.date).map((r) => li({ ic: 'wrench', t: esc(r.title), d: `${dFull(r.date)} · ${num(r.odo)} km`, end: r.cost ? `${cur}${num(r.cost)}` : '', act: 'sheet', arg: 'rec:' + r.id })).join('')}</div>
    <div class="flex between mt12 small muted" style="padding:0 4px"><span>Spent on service</span><b style="font-weight:500;color:var(--ink)">${cur}${num(spent)}</b></div>` : '<div class="card empty">Log each visit. A full history helps at resale.</div>'}
  </div>
  <div class="section">${sh('For the mechanic', `<span class="eyebrow">${s.checklist.filter((x) => !x.done).length} open</span>`, 'mechanic')}
    <div class="list">
      ${s.checklist.map((x) => `<div class="li noicon"><button class="check ${x.done ? 'on' : ''}" data-act="check" data-arg="${x.id}" aria-label="Mark done">${x.done ? icon('check') : ''}</button><span class="tx"><div class="t" style="${x.done ? 'text-decoration:line-through;color:var(--ink-3)' : ''}">${esc(x.text)}</div></span><button class="icon-btn" data-act="check-del" data-arg="${x.id}" aria-label="Remove">${icon('x')}</button></div>`).join('')}
      <form class="li noicon" data-form="check-add"><span class="check" style="box-shadow:inset 0 0 0 1.5px var(--ink-4);opacity:.6"></span><input class="grow" name="text" placeholder="A rattle, a squeak, a part to fix…" style="border:0;background:none;outline:none;font-size:15.5px;height:28px" autocomplete="off"></form>
    </div></div>
  </div>`;
}

// ---------- FUEL ----------
export function fuel(c) {
  const { s } = c;
  const fs = S.fuelStats(s);
  const cur = S.country(s).cur;
  const hasRange = fs.range != null;
  const entries = [...s.fuel].sort((a, b) => b.odo - a.odo || b.ts - a.ts);
  const kmplById = Object.fromEntries(fs.tanks.map((t) => [t.id, t.kmpl]));
  const tankL = num(fs.tank, fs.tank % 1 ? 1 : 0);
  return `<div class="stagger">
  <header class="pagehead">${topbar(backBtn(), `<button class="circle" data-act="sheet" data-arg="fuel:new" aria-label="Log fuel">${icon('plus')}</button>`)}
  <h1 class="title">Fuel</h1><div class="subtitle">Fill-ups for ${esc(clip(S.vehicleTitle(s), 24))}</div></header>
  <div class="hero ${!hasRange ? 'off' : fs.onReserve ? 'bad' : ''}">
    <div class="eyebrow">Range left</div>
    <div class="verdict">${hasRange ? `≈${num(fs.range)}<span class="unit" style="font-size:16px">km</span>` : '—'}</div>
    <p>${hasRange ? `Based on your last ${fs.tanks.length >= 3 ? 'three' : W(fs.tanks.length).toLowerCase()} full ${fs.tanks.length === 1 ? 'tank' : 'tanks'}: ${num(fs.avg, 1)} km/L.${fs.onReserve ? ' You’re on reserve.' : ''}` : s.fuel.length ? 'Fill to the brim once more to see your range.' : 'Fill to the brim and log it. Range appears after the next full tank.'}</p>
    ${segs(6, fs.bars ?? 0, fs.onReserve ? 'r' : 'k', 'thick')}
    <div class="eyebrow caption">${fs.litresLeft != null ? `≈${num(fs.litresLeft, 1)} of ${tankL} L` : `${tankL} L tank · ${num(fs.reserve, 1)} L reserve`}${fs.sinceFull != null ? ` · ${num(fs.sinceFull)} km since full` : ''}</div>
  </div>
  <button class="btn ink block mt12" data-act="sheet" data-arg="fuel:new">${icon('fuel')} Log fuel</button>
  <div class="section">${sh('Mileage', '<span class="eyebrow">Full tank to full tank</span>', 'mileage')}
    ${mileageScale(fs)}
    <div class="card stat3 mt12">
      <div><span class="eyebrow">Average</span><span class="big">${num(fs.avg, 1)}</span><span class="unit" style="margin:0">km/L, last 3</span></div>
      <div><span class="eyebrow">Last tank</span><span class="big">${num(fs.lastTank, 1)}</span><span class="unit" style="margin:0">km/L</span></div>
      <div><span class="eyebrow">Lifetime</span><span class="big">${num(fs.lifetime, 1)}</span><span class="unit" style="margin:0">${plural(fs.tanks.length, 'tank', 'tanks')}</span></div>
    </div>
    ${fs.tanks.length >= 2 ? `<div class="card chart mt12" id="mileage-chart">${mileageChart(fs)}<div class="tip"></div></div>` : ''}
    <p class="hint" style="padding:0 4px;margin-top:12px">Mileage counts only between full tanks. Top-ups roll into the next full one.</p>
  </div>
  <div class="section">${sh('Cost', '', 'cost')}
    <div class="card" style="padding:2px 20px">
      <div class="kv"><span>Cost per km</span><b>${fs.costPerKm != null ? cur + num(fs.costPerKm, 2) : '—'}</b></div>
      <div class="kv"><span>Last 30 days</span><b>${plural(fs.last30.count, 'fill', 'fills')} · ${num(fs.last30.litres, 1)} L · ${cur}${num(fs.last30.cost)}</b></div>
      <div class="kv"><span>All fuel</span><b>${cur}${num(fs.totalSpent)}</b></div>
      <button class="kv" data-act="sheet" data-arg="fuelprice"><span>Price per litre</span><b class="flex gap8">${cur}${num(S.fuelPriceOf(s), 2)}${icon('edit', 'faint')}</b></button>
    </div></div>
  <div class="section">${sh('Fill-ups', `<span class="eyebrow">${entries.length}</span>`)}
    ${entries.length ? `<div class="rows">${entries.map((e) => `<button class="row" data-act="sheet" data-arg="fuel:${e.id}"><div class="row-top"><div><div class="row-title">${dMed(e.ts)}</div><div class="row-sub">${e.full ? 'Full' : 'Top-up'} · ${num(e.odo)} km · ${cur}${num(e.cost)}</div></div><div class="row-val">${kmplById[e.id] ? `<span class="big">${num(kmplById[e.id], 1)}</span><span class="unit">km/L</span>` : `<span class="big">${num(e.litres, 1)}</span><span class="unit">L</span>`}</div></div></button>`).join('')}</div>` : '<div class="card empty"><div class="serif">No fill-ups yet</div>Tap Log fuel at the pump.</div>'}
  </div></div>`;
}
export function mileageChart(fs) {
  const t = fs.tanks.slice(-8);
  const Wd = 340, H = 168, pl = 30, pr = 16, pt = 22, pb = 30;
  const vals = t.map((x) => x.kmpl);
  let lo = Math.min(...vals, fs.avg ?? Infinity), hi = Math.max(...vals, fs.avg ?? -Infinity);
  const padv = Math.max(0.6, (hi - lo) * 0.25); lo = Math.floor(lo - padv); hi = Math.ceil(hi + padv);
  const x = (i) => pl + (t.length === 1 ? (Wd - pl - pr) / 2 : (i * (Wd - pl - pr)) / (t.length - 1));
  const y = (v) => pt + ((hi - v) * (H - pt - pb)) / (hi - lo || 1);
  const ticks = [lo, (lo + hi) / 2, hi];
  const grid = ticks.map((v) => `<line x1="${pl}" x2="${Wd - pr}" y1="${y(v)}" y2="${y(v)}" stroke="var(--line)" stroke-width="1"/><text x="${pl - 8}" y="${y(v) + 3.5}" text-anchor="end" font-family="var(--mono)" font-size="9" fill="var(--ink-3)">${num(v, 0)}</text>`).join('');
  const path = t.map((p, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)} ${y(p.kmpl).toFixed(1)}`).join(' ');
  const avg = fs.avg != null ? `<line x1="${pl}" x2="${Wd - pr}" y1="${y(fs.avg)}" y2="${y(fs.avg)}" stroke="var(--accent)" stroke-width="1.5" stroke-dasharray="5 5"/><rect x="${pl + 2}" y="${y(fs.avg) - 17}" width="56" height="13" rx="3" fill="var(--card)"/><text x="${pl + 6}" y="${y(fs.avg) - 7}" font-family="var(--mono)" font-size="9.5" fill="var(--accent)">avg ${num(fs.avg, 1)}</text>` : '';
  const iMax = vals.indexOf(Math.max(...vals)), iMin = vals.indexOf(Math.min(...vals)), iLast = vals.length - 1;
  const labelled = new Set([iMax, iMin, iLast]);
  const pts = t.map((p, i) => `<circle cx="${x(i)}" cy="${y(p.kmpl)}" r="4.5" fill="var(--card)" stroke="var(--ink)" stroke-width="2"/>${labelled.has(i) ? `<text x="${x(i)}" y="${y(p.kmpl) + (i === iMin && i !== iMax ? 18 : -10)}" text-anchor="middle" font-family="var(--serif)" font-size="13" fill="var(--ink)">${num(p.kmpl, 1)}</text>` : ''}`).join('');
  const xl = t.map((p, i) => `<text x="${x(i)}" y="${H - 8}" text-anchor="middle" font-family="var(--mono)" font-size="9" fill="var(--ink-3)">${dShort(p.ts)}</text>`).join('');
  const hits = t.map((p, i) => `<rect data-i="${i}" data-x="${x(i)}" data-y="${y(p.kmpl)}" data-tip="${dShort(p.ts)} · ${num(p.kmpl, 1)} km/L · ${num(p.dist)} km on ${num(p.litres, 1)} L" x="${x(i) - (Wd - pl - pr) / Math.max(1, t.length - 1) / 2}" y="0" width="${(Wd - pl - pr) / Math.max(1, t.length - 1)}" height="${H}" fill="transparent"/>`).join('');
  return `<div class="eyebrow" style="padding:0 6px 6px">km per litre, by tank</div><svg viewBox="0 0 ${Wd} ${H}" role="img" aria-label="Mileage per full tank">${grid}${avg}<line class="xh" x1="0" x2="0" y1="${pt - 8}" y2="${H - pb}" stroke="var(--ink-3)" stroke-width="1" opacity="0"/><path d="${path}" fill="none" stroke="var(--ink)" stroke-width="2" stroke-linejoin="round" stroke-linecap="round"/>${pts}${xl}${hits}</svg>`;
}

// ---------- TRIPS ----------
export function sketch(pts, lg = false) {
  const Wd = lg ? 340 : 74, H = lg ? 210 : 74, pad = lg ? 26 : 12;
  const grid = [];
  const step = lg ? 24 : 12;
  for (let gx = step; gx < Wd; gx += step) grid.push(`<line x1="${gx}" y1="0" x2="${gx}" y2="${H}"/>`);
  for (let gy = step; gy < H; gy += step) grid.push(`<line x1="0" y1="${gy}" x2="${Wd}" y2="${gy}"/>`);
  let line = '', ends = '';
  if (pts && pts.length > 1) {
    const lat0 = pts[0][0] * Math.PI / 180;
    const xy = pts.map((p) => [p[1] * Math.cos(lat0), -p[0]]);
    const xs = xy.map((p) => p[0]), ys = xy.map((p) => p[1]);
    const minx = Math.min(...xs), maxx = Math.max(...xs), miny = Math.min(...ys), maxy = Math.max(...ys);
    const sc = Math.min((Wd - pad * 2) / (maxx - minx || 1e-9), (H - pad * 2) / (maxy - miny || 1e-9));
    const ox = (Wd - (maxx - minx) * sc) / 2, oy = (H - (maxy - miny) * sc) / 2;
    const P2 = xy.map((p) => [ox + (p[0] - minx) * sc, oy + (p[1] - miny) * sc]);
    line = `<path d="${P2.map((p, i) => (i ? 'L' : 'M') + p[0].toFixed(1) + ' ' + p[1].toFixed(1)).join(' ')}" fill="none" stroke="var(--ink)" stroke-width="${lg ? 2.6 : 2}" stroke-linecap="round" stroke-linejoin="round"/>`;
    const a = P2[0], b = P2[P2.length - 1], r = lg ? 6 : 4;
    ends = `<circle cx="${a[0]}" cy="${a[1]}" r="${r}" fill="var(--sage-ink)" stroke="var(--bg)" stroke-width="2"/><rect x="${b[0] - r}" y="${b[1] - r}" width="${r * 2}" height="${r * 2}" transform="rotate(45 ${b[0]} ${b[1]})" fill="var(--accent)" stroke="var(--bg)" stroke-width="2"/>`;
  }
  return `<svg class="sketch ${lg ? 'lg' : ''}" viewBox="0 0 ${Wd} ${H}" preserveAspectRatio="xMidYMid meet"><g stroke="var(--line)" stroke-width="1">${grid.join('')}</g>${line}${ends}</svg>`;
}

// ---------- TRIPS ----------
// Where this tank sits between your thirstiest and thriftiest: a segmented scale with a marker bubble.
export function mileageScale(fs) {
  if (!fs.tanks || fs.tanks.length < 2 || fs.lastTank == null) return '';
  const vals = fs.tanks.map((t) => t.kmpl), lo = Math.min(...vals), hi = Math.max(...vals);
  const span = Math.max(0.1, hi - lo), pos = Math.max(0, Math.min(1, (fs.lastTank - lo) / span));
  const n = 10, segsHtml = Array.from({ length: n }, (_, i) => `<i style="--k:${(i / (n - 1)).toFixed(2)}"></i>`).join('');
  const verdict = pos > 0.66 ? 'One of your best tanks.' : pos < 0.33 ? 'Thirstier than usual. Check tyre pressure and chain.' : 'About your usual.';
  return `<div class="card pad mscale">
    <div class="flex between" style="align-items:flex-start"><div><div class="ms-t">This tank</div><div class="small muted">${verdict}</div></div><div><span class="big ms-v">${num(fs.lastTank, 1)}</span><span class="unit">km/L</span></div></div>
    <div class="ms-bar"><span class="ms-pin" style="left:${(pos * 100).toFixed(1)}%">${num(fs.lastTank, 1)}</span>${segsHtml}</div>
    <div class="ms-lab"><span>${num(lo, 1)} thirstiest</span><span>Usual</span><span>${num(hi, 1)} best</span></div></div>`;
}
// Riding days over the last 12 weeks, one square per day, darker for longer rides.
export function rideHeatmap(trips) {
  const DAYMS = 86400000, today = new Date(); today.setHours(0, 0, 0, 0);
  const start = new Date(today.getTime() - (today.getDay() + 7 * 11) * DAYMS);
  const kmByDay = {};
  for (const t of trips || []) { const d = new Date(t.start || t.ts || 0); d.setHours(0, 0, 0, 0); kmByDay[d.getTime()] = (kmByDay[d.getTime()] || 0) + (t.km || 0); }
  const max = Math.max(1, ...Object.values(kmByDay));
  let cols = '', months = '', lastM = -1;
  for (let w = 0; w < 12; w++) {
    let cells = '';
    const wkStart = new Date(start.getTime() + w * 7 * DAYMS);
    if (wkStart.getMonth() !== lastM) { months += `<span style="grid-column:${w + 1}">${wkStart.toLocaleDateString('en-IN', { month: 'short' })}</span>`; lastM = wkStart.getMonth(); }
    for (let d = 0; d < 7; d++) {
      const ts = start.getTime() + (w * 7 + d) * DAYMS, km = kmByDay[ts] || 0, future = ts > today.getTime();
      const lv = km ? Math.min(4, 1 + Math.floor((km / max) * 3.999)) : 0;
      cells += `<i class="${future ? 'f' : `l${lv}`}" title="${new Date(ts).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}${km ? ` · ${num(km, 1)} km` : ''}"></i>`;
    }
    cols += `<div class="hm-col">${cells}</div>`;
  }
  return `<div class="hm"><div class="hm-months">${months}</div><div class="hm-body"><div class="hm-days"><span>S</span><span></span><span>T</span><span></span><span>T</span><span></span><span>S</span></div><div class="hm-grid">${cols}</div></div></div>`;
}

export function trips(c) {
  const { s, live } = c;
  const wk = S.periodStats(s.trips, S.weekStart());
  const mo = S.periodStats(s.trips, Date.now() - 30 * 86400000);
  const r = live.rec;
  const stat = (lab, p) => `<div><span class="eyebrow">${lab}</span><span class="big">${num(p.km, p.km < 100 ? 1 : 0)}<span class="unit">km</span></span><div class="small muted">${plural(p.rides, 'ride', 'rides')} · ${dur(p.sec)}</div>${p.top ? `<div class="small faint">top ${num(p.top)} km/h</div>` : ''}</div>`;
  return `<div class="stagger">
  <div class="head pagehead"><div style="min-width:0"><h1 class="title">Rides</h1><div class="subtitle">${plural(s.trips.length, 'ride', 'rides')} on ${esc(clip(S.vehicleTitle(s), 20))}</div></div></div>
  ${r ? `<div class="card rec-card mt20"><div class="flex between"><span class="tag x"><span class="dot x pulse"></span>Recording</span><span class="mono small faint" data-live="rec-time">${clock((Date.now() - r.start) / 1000)}</span></div>
      <div class="serif mt12" style="font-size:24px">Today’s ride, in progress.</div>
      <div class="flex gap12 mt12" style="align-items:baseline"><span class="big"><span data-live="rec-km">${num(r.km, 2)}</span><span class="unit">km</span></span><span class="small muted"><span data-live="speed">${num(live.speed)}</span> km/h now · top <span data-live="rec-top">${num(r.top)}</span></span></div>
      <button class="btn ink block mt16" data-act="sheet" data-arg="rec-end">${icon('stop')} End ride</button></div>`
    : `<div class="card pad mt20 flex gap12"><div class="grow"><div class="serif" style="font-size:22px">Ready when you are</div><div class="small muted mt8">${s.settings.autoRecord ? 'Rides record by themselves once the bike links.' : 'Tap play to record with your phone’s GPS.'}</div></div><button class="circle ink" data-act="rec-start" aria-label="Record a ride">${icon('play')}</button></div>`}
  ${(() => { const tot = s.trips.reduce((a, t) => a + (t.km || 0), 0); return `<div class="card pad rides-hero mt12"><div class="ms-t">All rides</div><div class="big rh-n">${s.trips.length}</div><p class="rh-line">${s.trips.length ? `You’ve ridden <u>${num(tot, tot < 100 ? 1 : 0)} km</u> on ${esc(clip(S.vehicleTitle(s), 20))}${s.trips.length > 1 ? `, about ${num(tot / s.trips.length, 0)} km a ride` : ''}.` : 'Your rides will fill this in, one square a day.'}</p>${rideHeatmap(s.trips)}</div>`; })()}
  <div class="card stat2 mt12">${stat('This week', wk)}${stat('Last 30 days', mo)}</div>
  <div class="section">${sh('Rides', '<span class="eyebrow">Newest first</span>')}
    ${s.trips.length ? `<div class="card" style="padding:4px">${s.trips.map((t) => `<button class="ride" data-act="sheet" data-arg="trip-detail:${t.id}">${sketch(t.pts)}<div class="grow"><div class="when">${dMed(t.start)} · ${tShort(t.start)}</div><div class="t">${esc(t.title)}</div><div class="meta"><span><span class="big">${num(t.km, 1)}</span><span class="unit">km</span></span><span>${dur(t.sec)}</span><span>top ${num(t.top)}</span></div></div><span class="no">№${String(t.n).padStart(3, '0')}</span></button>`).join('')}</div>`
    : '<div class="card empty"><div class="serif">No rides yet</div>Your first ride shows up here, route and all.</div>'}
  </div></div>`;
}

// ---------- ROUTE ----------
export const REHEARSAL = {
  name: 'Bengaluru → Nandi Hills', steps: [
    { maneuver: 'STRAIGHT', m: 1400, street: 'Ballari Road', say: 'Continue on Ballari Road' },
    { maneuver: 'KEEP_LEFT', m: 800, street: 'Hebbal Flyover', say: 'Keep left onto Hebbal Flyover' },
    { maneuver: 'MERGE', m: 600, street: 'NH 44', say: 'Merge onto NH 44' },
    { maneuver: 'KEEP_RIGHT', m: 14200, street: 'NH 44', say: 'Keep right to stay on NH 44' },
    { maneuver: 'TURN_LEFT', m: 9600, street: 'Devanahalli', say: 'Turn left after Devanahalli' },
    { maneuver: 'ROUNDABOUT_RIGHT', m: 3400, street: 'Karahalli Cross', say: 'At Karahalli Cross, take the 2nd exit', exit: 2 },
    { maneuver: 'TURN_SLIGHT_RIGHT', m: 4800, street: 'Nandi Hills Road', say: 'Slight right onto Nandi Hills Road' },
    { maneuver: 'TURN_SHARP_LEFT', m: 1200, street: 'Ghat Road', say: 'Sharp left up the ghat road' },
    { maneuver: 'DESTINATION_REACHED', m: 300, street: 'Nandi Hills', say: 'Arrive at Nandi Hills' },
  ],
};
export function route(c) {
  const { s, live, link } = c;
  const n = live.nav;
  if (n) {
    const man = maneuverByKey(n.maneuver);
    const d = dist(n.stepMeters), tot = dist(n.totalMeters);
    return `<div class="stagger">
    <div class="head pagehead"><div><h1 class="title">En route</h1><div class="subtitle">${n.mode === 'rehearse' ? 'Rehearsing a route' : 'Testing one turn'}</div></div><span class="tag ${link.connected ? '' : 'k'}" style="margin-top:6px">${link.connected ? 'On the bike' : 'Phone only'}</span></div>
    <div class="card nav-card mt20">
      <div class="nav-top"><div class="maneuver">${arrow(man.glyph, n.exit)}</div><div class="grow"><div><span class="big" data-live="nav-dist">${d.v}</span><span class="serif" style="font-size:22px;margin-left:4px;color:var(--ink-2)" data-live="nav-unit">${d.u}</span></div><div class="instr">${esc(n.say || man.label)}</div></div></div>
      <div style="height:var(--hair);background:var(--line);margin:20px 0 2px"></div>
      <div class="kv"><span>Arrive at</span><b data-live="nav-eta">${tShort(n.eta)}</b></div>
      <div class="kv"><span>Distance left</span><b data-live="nav-total">${tot.v} ${tot.u}</b></div>
      <p class="hint" style="margin-top:6px">${s.settings.streetNames ? 'The bike shows the arrow, distances, arrival time and street.' : 'Street names stay on your phone. The bike shows the arrow, distances and arrival time.'}</p>
    </div>
    ${n.mode === 'rehearse' ? `<div class="flex between mt16"><div class="chips">${[1, 4, 16].map((x) => `<button class="chip ${live.navSpeed === x ? 'on' : ''}" data-act="nav-speed" data-arg="${x}">×${x}</button>`).join('')}</div><button class="chip" data-act="nav-next">Next turn ${icon('chev')}</button></div>`
      : `<div class="card pad mt12"><div class="flex between"><span class="eyebrow">Distance to turn</span><span class="mono small" data-live="nav-raw">${num(Math.round(n.stepMeters))} m</span></div><input class="range mt12" type="range" min="0" max="3000" step="10" value="${Math.round(n.stepMeters)}" style="--p:${Math.min(100, n.stepMeters / 30)}%" data-input="nav-dist" aria-label="Distance to turn"><div class="range-scale"><span>0</span><span>1.5 km</span><span>3 km</span></div><p class="hint">Under 100 m, the bike blinks the arrow.</p></div>`}
    <button class="btn line block mt16" data-act="nav-end">${icon('x')} End navigation</button>
    ${n.mode === 'rehearse' ? `<div class="section">${sh('Turns')}<div class="list steps">${REHEARSAL.steps.map((st, i) => `<div class="li ${i < n.idx ? 'done' : i === n.idx ? 'now' : ''}"><span class="ic">${arrow(maneuverByKey(st.maneuver).glyph, st.exit)}</span><span class="tx"><div class="t">${esc(st.say)}</div><div class="d">${dist(st.m).v} ${dist(st.m).u}</div></span></div>`).join('')}</div></div>` : ''}
    ${s.settings.devFrames ? frameCard(link) : ''}
    </div>`;
  }
  const sel = maneuverByKey(live.turn.maneuver);
  const isRb = sel.glyph.startsWith('roundabout');
  const totalKm = REHEARSAL.steps.reduce((a, x) => a + x.m, 0) / 1000;
  return `<div class="stagger">
  <div class="head pagehead"><div><h1 class="title">Route</h1><div class="subtitle">Turns on your bike</div></div></div>
  <div class="section">${sh('Where to?')}<p class="sec-note">Plan the ride in Google Maps. Its turns show on your bike.</p><form class="card pad" data-form="maps">
    <label class="field" style="margin-top:0"><span class="eyebrow">Destination</span><input class="input" name="dest" placeholder="Nandi Hills" value="${esc(live.dest || '')}" autocomplete="off"></label>
    <button class="btn ink block mt12" type="submit">${icon('map')} Open in Google Maps</button>
  </form></div>
  <div class="section">${sh('How this works')}<div class="card pad howto">
    <div><span><b>Plan the ride in Google Maps.</b> It draws the map and gives voice directions on your phone.</span></div>
    <div><span><b>Rehearse a route</b> to see how turns look on your bike’s display before you ride.</span></div>
    <div><span><b>Test one turn</b> to check a single arrow and distance on the display.</span></div>
  </div><p class="hint" style="padding:0 4px">A browser can’t read Google Maps’ turn alerts, so turns don’t reach the bike by themselves here. The Android app does that.</p></div>
  <div class="section">${sh('Rehearse a route', '', 'rehearse')}
    <div class="card pad"><div class="serif" style="font-size:24px">${REHEARSAL.name}</div><div class="small muted mt8">${num(totalKm, 1)} km · ${REHEARSAL.steps.length} turns · NH 44</div>
    <div class="flex gap8 mt16" style="overflow:hidden">${REHEARSAL.steps.map((st) => `<span style="width:24px;flex:none">${arrow(maneuverByKey(st.maneuver).glyph, 0, .25, 8)}</span>`).join('')}</div>
    <button class="btn accent block mt16" data-act="nav-rehearse">${icon('play')} Start rehearsal</button></div></div>
  <div class="section">${sh('Test one turn', '', 'testturn')}
    <div class="card tt-card"><button class="tt-main" data-act="sheet" data-arg="turn-test"><span class="tt-arrow">${arrow(sel.glyph, isRb ? live.turn.exit : 0, .3, 7.5)}</span><span class="tt-tx"><span class="tt-t">${sel.label}</span><span class="tt-s">${num(live.turn.m)} m${isRb ? ` · exit ${live.turn.exit}` : ''}${live.turn.street ? ` · ${esc(live.turn.street)}` : ''}</span></span><span class="tt-change">Change${icon('chev')}</span></button>
    <button class="btn accent block" data-act="nav-single">Send to bike</button></div></div>
  <div class="section">${sh('Display')}<div class="list">${li({ ic: 'eye', t: 'Street names on the bike', d: 'Road name beside the arrow', end: sw(s.settings.streetNames, 'toggle', 'streetNames'), chev: false, tag: 'div' })}</div></div>
  ${s.settings.devFrames ? frameCard(link) : ''}
  </div>`;
}
function frameCard(link) {
  const f = link.lastFrame;
  if (!f) return '';
  const names = { tbt: 'Turn-by-turn', general: 'Status', missedCall: 'Missed call', alerts: 'Alert', buttons: 'Buttons' };
  return `<div class="section">${sh(`Last Bluetooth message · <span data-live="frame-len">${f.bytes.length}</span> bytes`, `<span class="eyebrow" data-live="frame-name">${names[f.key]}</span>`)}<div class="card pad"><div class="hex" data-live="frame">${hexHtml(f.bytes)}</div><p class="hint">${link.connected ? `${num(link.frames)} frames sent this session.` : 'Not linked. Frames are built, not sent.'}</p></div></div>`;
}
export const hexHtml = (b) => toHex(b).split(' ').map((h, i) => (i === 1 && b.length === 48) || i === b.length - 1 ? `<b>${h}</b>` : h).join(' ');

// ---------- ALERTS ----------
// iOS Safari can't open the Phone app without a number, so the button only shows where it works.
function alertsTabs(c, needs) {
  const { s, live } = c, tab = live.alertsTab === 'recent' ? 'recent' : 'needs', n = s.alerts.missed.length + s.alerts.wa.length;
  return `<div class="ltabs" role="tablist"><button role="tab" class="${tab === 'needs' ? 'on' : ''}" aria-selected="${tab === 'needs'}" data-act="alerts-tab" data-arg="needs">Status${needs.length ? `<span class="lt-n">${needs.length}</span>` : ''}</button><button role="tab" class="${tab === 'recent' ? 'on' : ''}" aria-selected="${tab === 'recent'}" data-act="alerts-tab" data-arg="recent">Notifications${n ? `<span class="lt-n">${n}</span>` : ''}</button><span class="grow"></span><span class="eyebrow lt-aside">${short(s.alerts.sent)} sent</span></div>`;
}
const IS_IOS = typeof navigator !== 'undefined' && (/iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1));
export function alerts(c) {
  const { s, link, live } = c;
  const a = s.settings.alerts;
  const m = s.alerts.missed, w = s.alerts.wa;
  const card = (ic, lab, n, sub, act, open) => /* calls & chats */ `<div class="card pad"><div class="eyebrow flex gap8">${icon(ic).replace('<svg', '<svg style="width:14px;height:14px"')}${lab}</div><div class="big mt12" style="font-size:56px">${short(n)}</div><div class="small muted mt8 clamp1">${sub}</div><div class="push"></div><div class="ac-btns mt16">${open}<button class="btn line sm" data-act="sheet" data-arg="clear:${act}" ${n ? '' : 'disabled'}>Clear</button></div></div>`;
  const needs = inbox(c).filter((x) => x.ic !== 'missed' && x.ic !== 'chat');
  return `<div class="stagger">
  <div class="head pagehead"><div><h1 class="title">Alerts</h1><div class="subtitle">${needs.length ? `${plural(needs.length, 'thing needs', 'things need')} you` : 'Nothing needs you right now'}</div></div>${alertsTabs(c, needs)}</div>
  ${(() => { const tab = live.alertsTab === 'recent' ? 'recent' : 'needs';
    const needsHTML = needs.length ? `<div class="list">${needs.map((x) => li({ lead: `<span class="ic${x.tone === 'red' ? ' hot' : ''}">${icon(x.ic)}</span>`, t: x.t, d: x.tone === 'red' ? `<span class="opill">Overdue</span>${String(x.d).replace(/ overdue$/, ' late')}` : x.d, act: x.act, arg: x.arg, cls: x.tone === 'red' ? 'is-over' : '' })).join('')}</div>` : '<div class="card empty">All clear. Service, papers and fuel show up here when they need you.</div>';
    // Recent: a notification feed, grouped by day. What reached the bike, when.
    const day = (ts) => { const d = new Date(ts), t = new Date(); t.setHours(0, 0, 0, 0); const y = t - 864e5; return ts >= +t ? 'Today' : ts >= y ? 'Yesterday' : d.toLocaleDateString(DL(), { day: 'numeric', month: 'short' }); };
    const NT = { wa: ['chat', 'WhatsApp message', 'Shown on your bike'], missed: ['missed', 'Missed call', 'Count sent to your bike'], call: ['phone', 'Incoming call', 'Name shown on your bike'], button: ['hand', 'Handlebar button', 'Pressed on the bike'] };
    let lastDay = '';
    const recentHTML = s.alerts.log.length ? s.alerts.log.slice(0, 30).map((x) => { const [ic, what, sub] = NT[x.kind] || NT.call, dd = day(x.ts), head = dd !== lastDay ? `<div class="nf-day">${dd}</div>` : ''; lastDay = dd;
      return `${head}<div class="nf"><span class="nf-ic">${icon(ic)}</span><div class="nf-tx"><div class="nf-t"><b>${esc(x.kind === 'button' ? 'Handlebar' : shortName(x.name))}</b><span class="nf-time">${new Date(x.ts).toLocaleTimeString(DL(), { hour: "numeric", minute: "2-digit" })}</span></div><div class="nf-d">${what} · ${sub}</div></div></div>`; }).join('') : '<div class="card empty">No notifications yet.</div>';
    const callsHTML = `<div class="two">${card('missed', 'Missed calls', m.length, m.length ? 'On your bike' : 'None waiting', 'clear-missed', IS_IOS ? '' : `<a class="btn line sm" href="tel:" aria-label="Open the Phone app">${icon('phone')}Phone</a>`)}${card('chat', 'WhatsApp', w.length, w.length ? 'On your bike' : 'None waiting', 'clear-wa', `<a class="btn line sm" href="whatsapp://" aria-label="Open WhatsApp">${icon('chat')}Chats</a>`)}</div>`;
    const body = tab === 'needs' ? needsHTML : `${callsHTML}<div class="nf-list mt20">${recentHTML}</div>`;
    return `<div class="section" style="margin-top:14px"><div class="lt-body" data-swipe-tabs="alerts-tab" data-tabs="needs,recent" data-cur="${tab}">${body}</div></div>`; })()}
  ${live.alertsTab !== 'recent' ? '' : `
  <div class="section"><div class="dashed">${icon('lock')}<span>Only <b>names and counts</b> reach your bike. Message text never leaves this phone.</span></div></div>
  <div class="section">${sh('Show on the bike', '', 'showbike')}<div class="list">
    ${li({ ic: 'phone', t: 'Phone calls', d: 'Caller name and missed calls', end: sw(a.calls, 'toggle', 'alerts.calls'), chev: false, tag: 'div' })}
    ${li({ ic: 'waCall', t: 'WhatsApp calls', d: 'Caller name while ringing', end: sw(a.waCalls, 'toggle', 'alerts.waCalls'), chev: false, tag: 'div' })}
    ${li({ ic: 'chat', t: 'WhatsApp messages', d: 'Sender name and count', end: sw(a.waMsgs, 'toggle', 'alerts.waMsgs'), chev: false, tag: 'div' })}
    ${li({ ic: 'mail', t: 'SMS', d: 'Coming soon', end: sw(false, 'noop', '', true), chev: false, cls: 'disabled', tag: 'div' })}
  </div></div>
  <div class="section">${sh('Handlebar button', '', 'handlebar')}<div class="list">
    ${li({ ic: 'hand', t: 'Tap', d: 'Answer, or play and pause', end: link.simulated && link.connected ? '<button class="chip" data-act="press" data-arg="tap">Press</button>' : '', chev: false, tag: 'div' })}
    ${li({ ic: 'hand', t: 'Long press', d: 'Decline, or next track', end: link.simulated && link.connected ? '<button class="chip" data-act="press" data-arg="long">Press</button>' : '', chev: false, tag: 'div' })}
  </div></div>
  <div class="section">${sh('Send to the bike', `<span class="eyebrow">${link.connected ? 'Linked' : 'Not linked'}</span>`, 'send')}
    <div class="card pad">
      <label class="field" style="margin-top:0"><span class="eyebrow">Name</span><input class="input" placeholder="Rohan" value="${esc(live.relayName)}" data-input="relay-name" maxlength="32" autocomplete="off"></label>
      <div class="btns mt12" style="gap:8px">
        <button class="btn ghost sm" data-act="relay" data-arg="ring">${icon('phone')} Ring</button>
        <button class="btn ghost sm" data-act="relay" data-arg="missed">${icon('missed')} Missed</button>
        <button class="btn ghost sm" data-act="relay" data-arg="wa">${icon('chat')} Chat</button>
      </div>
      <p class="hint" style="margin-top:12px">Browsers can’t see your calls or chats. Send them here by hand to test.</p>
    </div></div>
  `}
  </div>`;
}

// ---------- SETTINGS ----------
export function settings(c) {
  const { s, link, live } = c;
  const st = s.settings;
  const co = S.country(s), md = S.model(s), v = S.veh(s);
  const connText = { connected: 'Connected', connecting: 'Connecting', reconnecting: 'Searching', picking: 'Choosing', idle: link.device || v.bike?.id ? 'Paired' : 'Not paired', error: 'Error' }[link.state];
  return `<div class="stagger">
  <div class="head pagehead"><div><h1 class="title">Settings</h1><div class="subtitle">Profile, bikes and look</div></div></div>
  ${live.scenario ? previewBar(live) : ''}
  ${(() => { const P = s.profile, rows = [P.blood ? ['drop', 'Blood', esc(P.blood)] : null, P.iceName ? ['phone', 'Emergency contact', esc(P.iceName)] : null, P.medical ? ['hospital', 'Medical notes', esc(P.medical)] : null].filter(Boolean);
    const sum = [P.blood ? `Blood ${esc(P.blood)}` : '', P.iceName ? 'ICE' : '', P.medical ? 'Medical' : ''].filter(Boolean).join(' · ');
    return `<div class="card profile-card mt20"><button class="pc-top flex gap12" data-act="go" data-arg="profile">${avatar(P)}<div class="grow" style="min-width:0"><div class="serif pc-name">${esc(shortName(P.name) || 'Add your name')}</div><div class="small faint">${rows.length ? 'Rider profile' : 'Licence, blood group, emergency contact'}</div></div>${icon('chev', 'faint')}</button>${rows.length ? `<details class="pc-more"${live.pcOpen ? ' open' : ''}><summary data-act="pc-toggle">${icon('shield')}<span class="pcm-t">Emergency info</span><span class="pcm-s">${sum}</span>${icon('chev')}</summary><div class="pc-grid">${rows.map(([ic, k, v]) => `<div class="pc-cell${k === 'Medical notes' ? ' wide' : ''}"><span class="pc-k">${icon(ic)}${k}</span><span class="pc-v">${v}</span></div>`).join('')}</div></details>` : ''}</div>`; })()}
  <div class="section">${sh('Garage', s.vehicles.length > 1 ? '<button class="link" data-act="sheet" data-arg="garage">Switch</button>' : '')}
    <div class="card vcard"><button class="flex gap12" style="width:100%;text-align:left;padding:12px 16px 12px 12px" data-act="sheet" data-arg="garage" aria-label="Switch vehicle">
      ${vehicleArt(s, v, 'card')}
      <div class="grow vc-tx"><div class="serif vc-name">${esc(S.vehicleTitle(s))}</div><div class="small muted vc-line">${v.nickname ? esc(S.bikeName(s)) + ' · ' : ''}${esc(v.plate || 'No plate')}</div><div class="eyebrow vc-line" style="margin-top:8px">${short(s.odo)} km · ${connText}</div></div>
    </button><button class="circle edit" data-act="sheet" data-arg="vehicle:${v.id}" aria-label="Edit vehicle">${icon('edit')}</button>
      <div class="vc-rows">
      ${li({ ic: 'spec', t: 'Spec sheet', d: 'Oil, tyres, chain, plugs', act: 'sheet', arg: 'specs' })}
      ${s.vehicles.length > 1 ? li({ ic: 'swap', t: 'Switch vehicle', end: `${s.vehicles.length} bikes`, act: 'sheet', arg: 'garage' }) : ''}
      ${li({ ic: 'grid', t: 'All bike models', d: `${S.MODELS.length} models · ${Object.keys(S.MODEL_IMAGES).length} with photos`, act: 'sheet', arg: 'models' })}
      ${li({ ic: 'gauge', t: 'Odometer', d: 'Correct it if it drifts', end: num(Math.floor(s.odo)) + ' km', act: 'sheet', arg: 'odo' })}
      ${li({ ic: 'bt', t: 'Bluetooth', d: 'Pair and check the link', end: connText, act: 'sheet', arg: 'bike' })}
    </div></div></div>
  <button class="support-card mt16" data-act="sheet" data-arg="support"><span class="sh-heart">${icon('love')}</span><span class="grow"><b>Support Waylink</b><span>Chip in if it helps</span></span>${icon('chev')}</button>
  <div class="section">${sh('Records & safety', '', 'papers')}<div class="list">
    ${li({ ic: 'wrench', t: 'Logbook', d: 'Reminders and visits', end: S.needsYou(s) ? `${S.needsYou(s)} due` : 'All good', act: 'go', arg: 'logbook' })}
    ${li({ ic: 'fuel', t: 'Fuel', d: 'Fill-ups and petrol price', end: `${s.fuel.length} fills`, act: 'go', arg: 'fuel' })}
    ${li({ ic: 'folder', t: 'Digital glovebox', d: 'RC, insurance, PUC, licence', end: `${live.gloveHere.length}`, act: 'sheet', arg: 'glovebox' })}
    ${li({ ic: 'pin', t: 'Parked bike', d: 'Find your way back to it', end: s.parking ? ago(s.parking.ts) : 'Not saved', act: 'sheet', arg: 'parking' })}
  </div></div>
  <div class="section" id="sec-riding">${sh('Riding', '', 'riding')}<div class="list">
    ${li({ ic: 'speed', t: 'Open ride mode by itself', d: 'Above 15 km/h', end: sw(st.autoRide, 'toggle', 'autoRide'), chev: false, tag: 'div' })}
    ${li({ ic: 'route', t: 'Record rides by themselves', d: 'When the bike links', end: sw(st.autoRecord, 'toggle', 'autoRecord'), chev: false, tag: 'div' })}
    ${li({ ic: 'sparkles', t: 'Keep the screen on', d: 'While in ride mode', end: sw(st.wakeLock, 'toggle', 'wakeLock'), chev: false, tag: 'div' })}
    ${li({ ic: 'eye', t: 'Street names on the bike', d: 'Road name beside the arrow', end: sw(st.streetNames, 'toggle', 'streetNames'), chev: false, tag: 'div' })}
  </div></div>
  <div class="section">${sh('Look & sound')}<div class="card themecard"><div class="tc-top">
    <div class="themes">${THEMES.map(([k, l, bg, card, ink]) => `<button class="themeopt ${st.theme === k ? 'on' : ''}" data-act="theme" data-arg="${k}" aria-pressed="${st.theme === k}">
      <span class="tp ${k}" style="--tbg:${bg};--tcard:${card};--tink:${ink}"><i></i><i></i><b></b></span><span class="nm">${l}</span></button>`).join('')}</div>
    <div class="eyebrow mt24">Accent</div>
    <div class="accents mt12">${ACCENTS.map(([k, col, l]) => `<button class="accentopt ${st.accent === k ? 'on' : ''}" data-act="accent" data-arg="${k}" aria-pressed="${st.accent === k}" aria-label="${l}" title="${l}"><span class="dot" style="background-color:${col};--c:${col}"></span></button>`).join('')}</div>
  </div>
  <details class="tc-more"${live.lookOpen ? ' open' : ''}><summary data-act="look-toggle"><span>Sound, icons & fonts</span>${icon('chev')}</summary><div class="vc-rows">
    ${li({ ic: 'volume', t: 'Sound effects', d: st.sounds !== false ? 'Engine start, clicks, revs' : 'Off. The app stays silent', end: sw(st.sounds !== false, 'toggle', 'sounds'), chev: false, tag: 'div' })}
    ${li({ ic: 'sparkles', t: 'Icons', end: `<span class="pick-val">${(ICON_STYLES.find(([k]) => k === st.iconStyle) || ICON_STYLES[0])[1]} · ${{ duo: 'Glow', fill: 'Duotone' }[st.icons] || 'Line'}</span>`, act: 'sheet', arg: 'pick-brand' })}
    ${li({ ic: 'font', t: 'Fonts', end: `<span class="pick-val"><span style="font-family:${(FONTS.find(([k]) => k === st.font) || FONTS[0])[2]}">${(FONTS.find(([k]) => k === st.font) || FONTS[0])[1]}</span> · ${(BODY_FONTS.find(([k]) => k === (st.bodyFont || 'system')) || BODY_FONTS[0])[1]}</span>`, act: 'sheet', arg: 'pick-font' })}
    ${li({ ic: 'progress', t: 'Progress style', end: `<span class="pick-val">${(PROGRESS_STYLES.find(([k]) => k === st.progress) || PROGRESS_STYLES[0])[1]}</span>`, act: 'sheet', arg: 'pick-progress' })}
  </div></details></div></div>
  <div class="section">${sh('Data & region', '', 'backup')}<div class="list">
    ${li({ ic: 'globe', t: 'Country', d: 'Currency and SOS', end: esc(co.name), act: 'sheet', arg: 'pick:country' })}
    ${li({ ic: 'down', t: 'Backup & restore', d: 'Save, move or bring back your data', act: 'sheet', arg: 'backup' })}
    ${li({ ic: 'trash', t: s.sample ? 'Erase sample data' : 'Erase everything', cls: 'danger', act: 'sheet', arg: 'erase' })}
    ${li({ ic: 'refresh', t: 'Reset the app', d: 'Like a fresh install', cls: 'danger', act: 'sheet', arg: 'reset' })}
  </div><input type="file" id="import-file" accept=".json,.pnav,application/json" hidden></div>
  <div class="section">${sh('Help & about')}<div class="list">
    ${li({ ic: 'info', t: 'Questions and answers', d: 'Pairing, fuel, weather, privacy', act: 'sheet', arg: 'faq' })}
    ${li({ ic: 'flag', t: 'Report a problem', d: 'Copy your app details and tell us', act: 'sheet', arg: 'report' })}
    ${li({ ic: 'heart', t: 'About Waylink', d: `Version ${APP_VERSION}, credits and licences`, act: 'sheet', arg: 'about' })}
    ${li({ ic: 'refresh', t: 'Refresh the app', d: 'Latest version, data kept', act: 'hard-refresh' })}
  </div></div>
  <div class="section">${sh('Developer', '', 'developer')}<div class="list">
    ${li({ ic: 'flag', t: 'Show onboarding', d: 'Preview first launch', act: 'onb-test' })}
    ${li({ ic: 'sparkles', t: 'Show the splash screen', d: 'The logo animation. Tap to close.', act: 'show-loader' })}
    ${li({ ic: 'refresh', t: 'Show the loading screen', d: 'Turn signs while something loads', act: 'show-loading' })}
    ${li({ ic: 'bolt', t: 'Simulated bike', d: 'Try it all without a bike', end: sw(link.simulated && link.state !== 'idle', 'sim-toggle'), chev: false, tag: 'div' })}
    ${li({ ic: 'btMsgs', t: 'Show Bluetooth messages', d: 'Raw bytes on the Route tab', end: sw(!!st.devFrames, 'toggle', 'devFrames'), chev: false, tag: 'div' })}
    ${li({ ic: 'bt', t: 'Pair from all devices', d: 'If your bike isn’t listed', act: 'pair-all', cls: link.supported ? '' : 'disabled' })}
    ${li({ ic: 'list', t: 'Connection log', d: 'Each step of the Bluetooth link', end: `${link.log.length}`, act: 'sheet', arg: 'log' })}
    ${li({ ic: 'protocol', t: 'Protocol', d: 'Service, frames and timings', act: 'sheet', arg: 'protocol' })}
  </div>
    <div class="card pad mt12"><div class="flex between"><span class="ms-t">Preview states</span><span class="eyebrow">Your data is safe</span></div>
    <div class="mt12"><div class="seg">${[['real', 'My data'], ['empty', 'Empty'], ['edge', 'Edge cases']].map(([k, l]) => `<button class="${(live.scenario || 'real') === k ? 'on' : ''}" data-act="scenario" data-arg="${k}">${l}</button>`).join('')}</div></div>
    <p class="hint">Empty shows a brand-new rider. Edge cases shows long names, huge numbers and everything overdue. Nothing is saved while previewing.</p></div></div>
  <footer class="about">
    <div class="about-brand">${TURNS}${LOOP}</div>
    <div class="about-meta">Version ${APP_VERSION} · MIT licence</div>
    <p class="about-legal">Not affiliated with Bajaj Auto, OTC, Google or WhatsApp.</p>
  </footer>
  </div>`;
}

export const FONTS = [
  ['system', 'System', "-apple-system, system-ui", 700, 'sans'],
  ['onest', 'Onest', "'Onest'", 600, 'sans'],
  ['geist', 'Geist', "'Geist'", 600, 'sans'],
  ['garamond', 'EB Garamond', "'EB Garamond'", 500, 'serif'],
  ['crimson', 'Crimson', "'Crimson Pro'", 500, 'serif'],
  ['fraunces', 'Fraunces', "'Fraunces'", 600, 'serif'],
];
export const APP_NAME = 'Waylink';
// Wordmark-only logos: a slot that cycles through turn signs, and a w drawn as one looping road with the rest typed in.
const TURN_SIGNS = [['turnRight', '#F28A1E'], ['turnLeft', '#3E8EF7'], ['straight', '#2BB673'], ['turnU', '#7C5CFF'], ['bearRight', '#E5484D'], ['pinSimple', '#0EA5A5']];
export const TURNS = `<span class="wl-turns" aria-label="waylink"><span aria-hidden="true">way</span><span class="slot" aria-hidden="true">${TURN_SIGNS.map(([k, col, f], i) => `<svg class="tg tg${i} ${f || ''}" viewBox="0 0 24 24" fill="none" style="color:${col}">${HUGE_ICONS[k]}</svg>`).join('')}</span><span aria-hidden="true">link</span></span>`;
// In-app loading screen: the turn signs take turns in a circle, with a line of text below.
export const loader = (text = 'Loading') => `<div class="wl-loader" role="status" aria-live="polite"><span class="ld-ring">${TURN_SIGNS.map(([k], i) => `<svg class="ld-i ld${i}" viewBox="0 0 24 24" fill="none" aria-hidden="true">${HUGE_ICONS[k]}</svg>`).join('')}</span><div class="ld-t">${esc(text)}<span class="ld-dots"><i>.</i><i>.</i><i>.</i></span></div></div>`;
export const LOOP = '<span class="wl-loop" aria-label="waylink"><svg class="lw-w" viewBox="0 0 100 100" aria-hidden="true"><defs><mask id="wl-taper" maskUnits="userSpaceOnUse" x="-20" y="-20" width="140" height="140"><rect x="-20" y="-20" width="140" height="140" fill="#fff"/><polygon points="-20,-20 6,-20 6,44 3.8,48.5 2.1,52.5 0.9,56 0.2,59 -0.1,61.5 -20,61.5" fill="#000"/></mask></defs><path class="lw" mask="url(#wl-taper)" pathLength="100" d="M2.5 44C3 64 12 80 25 80C37 80 42 64 46 50C48.8 40.5 54.5 31 54 22C53.6 13.5 39 12.5 38.6 22C38.2 31 43.4 40.5 46.8 50C51 64 58 80 71 80C84 80 90 56 91.5 26L91.5 14"/></svg><span class="rest" aria-hidden="true"><i class="ty ty0">a</i><i class="ty ty1">y</i><i class="ty ty2">l</i><i class="ty ty3">i</i><i class="ty ty4">n</i><i class="ty ty5">k</i></span></span>';
export const BRANDS = [['loop', 'Loop']];
export const brandOf = () => 'loop';
export const APP_VERSION = '1.0';
// Add-a-bike glyph: a motorbike whose front wheel is a plus.
export const ADD_BIKE = '<svg class="i" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="5" cy="17" r="3"/><circle cx="17.5" cy="17" r="3"/><path d="M5 17l3.6-5.2h4.6l1.6 2.2"/><path d="M8.6 11.8l2.2 4.6h3.6"/><path d="M17.5 17l-2.6-7.4h-1.8"/><path d="M7.2 9.4h3.4"/><path d="M19.5 2.5v5.5M16.75 5.25h5.5"/></svg>';
export const isIOS = () => typeof navigator !== 'undefined' && (/iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1));
const BLUEFY_STORE = 'https://apps.apple.com/app/id1492822055';
export const REPO_URL = 'https://github.com/krishnaar21/Waylink';
export const SUPPORT_EMAIL = 'support@krishnaar.work';
// Donations. Fill in to switch them on: a UPI ID (e.g. name@okicici) and any of Buy Me a Coffee, Ko-fi or GitHub Sponsors.
export const SUPPORT = { upi: '', payee: 'Waylink', links: [/* ['Buy Me a Coffee', 'https://buymeacoffee.com/…'], ['Ko-fi', 'https://ko-fi.com/…'], ['GitHub Sponsors', 'https://github.com/sponsors/…'] */] };
const UPI_AMOUNTS = [49, 99, 199, 499];
// What each amount buys, shown as a small picture on its button.
const AMT_ICON = { 49: ['Chai', 'tea', 'A cutting chai for the developer'], 99: ['Coffee', 'coffee', 'A coffee for a late-night fix'], 199: ['Fuel', 'fuel', 'Two litres of petrol for test rides'], 499: ['Service', 'wrench', 'Towards a month of upkeep'], 0: ['Any', 'love', 'You choose the amount in your UPI app'] };
const amtIcon = (a) => { const g = AMT_ICON[a][1]; return g.startsWith('<svg') ? g : icon(g); };
// The app icon: one route mark, tinted to match the accent colour.
export const ICON_STYLES = [['classic', 'Classic'], ['chrome', 'Chrome'], ['retro', 'Retro'], ['leather', 'Leather'], ['ascii', 'ASCII'], ['wool', 'Wool'], ['oil', 'Oil slick'], ['stitch', 'Stitch']];
export const appIcon = (s, png = false) => {
  const st = ICON_STYLES.some(([k]) => k === s.settings.iconStyle) ? s.settings.iconStyle : 'classic', a = s.settings.accent || 'wine';
  if (st !== 'classic') return png ? `img/logos/png/wl-icon-${st}-180.png` : `img/logos/wl-icon-${st}.svg`;
  return png ? `img/logos/png/wl-loop-${a}-180.png` : `img/logos/wl-loop-${a}.svg`;
};
export const appName = () => APP_NAME;

// ---------- PROFILE ----------
export function profile(c) {
  const { s } = c;
  const p = s.profile, co = S.country(s);
  const totalKm = s.vehicles.reduce((a, v) => a + v.odo, 0);
  const rides = s.vehicles.reduce((a, v) => a + v.trips.length, 0);
  const f = (name, label, val, type = 'text', ph = '', extra = '') => `<label class="field"><span class="eyebrow">${label}</span><input class="input" name="${name}" type="${type}" value="${esc(val ?? '')}" placeholder="${ph}" autocomplete="off" ${extra}></label>`;
  return `<div class="stagger">
  <header class="pagehead">${topbar(backBtn('settings'), `<span class="saved" id="saved">${icon('check')} Saved</span>`)}</header>
  <div class="prof-hero">
    <button style="position:relative" data-act="sheet" data-arg="avatar" aria-label="Change picture">${avatar(p, true)}<span class="circle" style="position:absolute;right:-4px;bottom:-4px;width:32px;height:32px">${icon('edit').replace('<svg', '<svg style="width:15px;height:15px"')}</span></button>
    <div class="ph-tx"><h1 class="title">${esc(shortName(p.name) || 'Your profile')}</h1><div class="subtitle">Rider since ${new Date(p.since || Date.now()).toLocaleDateString('en-IN', { month: 'short', year: 'numeric' })}</div></div>
  </div>
  <div class="card stat3 mt20"><div><span class="eyebrow">Bikes</span><span class="big">${s.vehicles.length}</span></div><div><span class="eyebrow">Ridden</span><span class="big">${short(totalKm)}</span><span class="unit" style="margin:0">km</span></div><div><span class="eyebrow">Rides</span><span class="big">${short(rides)}</span></div></div>
  ${(() => { const row = (k, v, ph) => `<div class="pr-row"><span class="pr-k">${k}</span><span class="pr-v ${v ? '' : 'faint'}">${v ? esc(v) : ph}</span></div>`;
    const card = (key, title, help, rows) => `<div class="section">${sh(title, '<button class="link" data-act="sheet" data-arg="prof:' + key + '">Edit</button>', help)}<button class="card pr-card" data-act="sheet" data-arg="prof:${key}">${rows}</button></div>`;
    const exp = p.licenceExpiry ? dFull(p.licenceExpiry) : '';
    return card('about', 'About you', '', row('Name', p.name, 'Add') + row('Phone', p.phone, 'Add') + row('Email', p.email, 'Add') + row('City', p.city, 'Add'))
      + card('licence', 'Driving licence', '', row('Licence number', p.licenceNo, 'Add') + row('Valid till', exp, 'Add'))
      + (p.licenceExpiry && p.licenceExpiry - Date.now() < 60 * 86400000 ? `<p class="hint red" style="margin:8px 4px 0">${p.licenceExpiry < Date.now() ? 'Your licence has expired.' : 'Your licence expires soon.'} Renew it on Parivahan.</p>` : '')
      + card('medical', 'Medical ID', 'medical', row('Blood group', p.blood, 'Add') + row('Allergies & notes', p.medical, 'None') + row('Emergency contact', [p.iceName, p.icePhone].filter(Boolean).join(' · '), 'Add')); })()}
  <div class="dashed">${icon('lock')}<span>Your profile stays on this phone. It’s in your backup, and nowhere else.</span></div>
  </div>`;
}

// ---------- Tab bar ----------
export function tabbar(c, current) {
  const { s, live } = c;
  const badge = s.alerts.missed.length + s.alerts.wa.length + inbox(c).filter((x) => x.ic !== 'missed' && x.ic !== 'chat').length;
  const needs = S.needsYou(s) > 0 || S.fuelStats(s).onReserve;
  const tabs = [
    ['today', 'Today', 'today', needs ? '<span class="pip"></span>' : ''],
    ['route', 'Route', 'route', live.nav ? '<span class="pip g"></span>' : ''],
    ['trips', 'Rides', 'trips', live.rec ? '<span class="pip x"></span>' : ''],
    ['alerts', 'Alerts', 'alerts', badge ? '<span class="pip"></span>' : ''],
    ['settings', 'Settings', 'settings', ''],
  ];
  const cur = ['fuel', 'logbook'].includes(current) ? 'today' : current === 'profile' ? 'settings' : current;
  return tabs.map(([k, l, ic, b]) => `<button class="tab ${cur === k ? 'on' : ''}" data-act="tab" data-arg="${k}" aria-label="${l}" ${cur === k ? 'aria-current="page"' : ''}>${icon(ic)}${b}<span>${l}</span></button>`).join('');
}

// ---------- Ride mode ----------
export const MAX_KMH = 160;
// Dial range follows the bike: 400cc tops out near 160, so the dial reads to 180; 200–250cc to 160; smaller bikes to 140.
export const maxKmh = (md) => { const cc = parseFloat(String(md?.engine || '').match(/[\d.]+(?=cc)/)?.[0]) || 200; return cc >= 350 ? 180 : cc >= 190 ? 160 : 140; };
const rmBars = (fs) => { let h = `<div class="rm-fuelbars ${fs.onReserve ? 'low' : ''}">`; for (let i = 0; i < 6; i++) h += `<i class="${fs.bars != null && i < fs.bars ? 'on' : ''}" style="height:${40 + i * 12}%"></i>`; return h + '</div>'; };
function rideBits(c) {
  const { s, live } = c;
  const fs = S.fuelStats(s);
  const now = new Date();
  const time = now.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  const temp = live.engine != null ? { lab: 'Engine', v: `<span data-live="engine">${live.engine}</span><small>°C</small>` } : { lab: 'Phone', v: `${live.battery ?? '—'}<small>%</small>` };
  const trip = live.rec ? { lab: 'This ride', v: `<span data-live="rec-km">${num(live.rec.km, 1)}</span><small>km</small>`, lab2: 'Riding for', v2: `<span data-live="rec-time">${clock((Date.now() - live.rec.start) / 1000)}</span>` }
    : { lab: 'Trip A', v: `${tripv(s.tripA.km)}<small>km</small>`, lab2: 'Odometer', v2: `${num(Math.floor(s.odo))}<small>km</small>` };
  const range = fs.range != null ? `≈${num(fs.range)}<small>km</small>` : '—';
  let nav = '';
  if (live.nav) { const d = dist(live.nav.stepMeters); nav = `<div class="rm-box rm-nav2">${arrow(maneuverByKey(live.nav.maneuver).glyph, live.nav.exit)}<div class="grow"><div class="rm-val"><span data-live="rm-dist">${d.v}</span><small data-live="rm-unit">${d.u}</small></div><div class="small muted" style="margin-top:4px">${esc(live.nav.say || '')}</div></div></div>`; }
  return { max: maxKmh(S.model(s)),  fs, time, date: now.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' }), temp, trip, range, nav, speed: num(live.speed) };
}
function layoutA(b) {
  return `<section class="rm-page lay-a" aria-label="Speed first">
    <div class="speed"><div class="rm-dial" id="rm-dial"><svg viewBox="0 0 200 200" aria-hidden="true"><defs><radialGradient id="rmd-glow" cx="50%" cy="50%" r="50%"><stop offset="0" stop-color="var(--accent)" stop-opacity=".38"/><stop offset=".55" stop-color="var(--accent)" stop-opacity=".12"/><stop offset="1" stop-color="var(--accent)" stop-opacity="0"/></radialGradient><radialGradient id="rmd-band" cx="50%" cy="50%" r="50%"><stop offset=".66" stop-color="var(--accent)" stop-opacity=".05"/><stop offset="1" stop-color="var(--accent)" stop-opacity=".55"/></radialGradient></defs>
      <circle cx="100" cy="100" r="66" fill="url(#rmd-glow)"/>
      <path class="d-base" d="M28.1 141.5A83 83 0 1 1 171.9 141.5" pathLength="100"/>
      <path class="d-fill" id="rm-dfill" d="M28.1 141.5A83 83 0 1 1 171.9 141.5" pathLength="100" stroke-dasharray="0 100"/>
      <path class="d-rim" id="rm-drim" d="M16.0 148.5A97 97 0 1 1 184.0 148.5" pathLength="100" stroke-dasharray="0 100"/>
      <path class="d-sep" d="M34.3 117.6L4.4 125.6M32.0 100.0L1.0 100.0M34.3 82.4L4.4 74.4M41.1 66.0L14.3 50.5M51.9 51.9L30.0 30.0M66.0 41.1L50.5 14.3M82.4 34.3L74.4 4.4M100.0 32.0L100.0 1.0M117.6 34.3L125.6 4.4M134.0 41.1L149.5 14.3M148.1 51.9L170.0 30.0M158.9 66.0L185.7 50.5M165.7 82.4L195.6 74.4M168.0 100.0L199.0 100.0M165.7 117.6L195.6 125.6"/>
      <g id="rm-dneedle" style="transform-origin:100px 100px;transform:rotate(0deg)"><path class="d-needle" d="M28.1 141.5L16.0 148.5"/></g>
      <text class="d-u" x="100" y="62" text-anchor="middle">km/h</text>
      <text class="d-lab" x="44" y="146">0</text><text class="d-lab" x="156" y="146" text-anchor="end">${b.max}</text>
      <text class="d-sub" x="100" y="146" text-anchor="middle">${b.trip.v.replace(/<[^>]+>/g, ' ')}</text>
    </svg><div class="d-mid"><span class="big" data-live="speed">${b.speed}</span></div></div>${b.tags ? `<div class="rm-tags">${b.tags}</div>` : ''}</div>
    ${b.nav ? b.nav + '<div style="height:10px"></div>' : ''}
    <div class="rm-grid">
      <div class="rm-box"><div class="rm-lab">${b.date}</div><div class="rm-val" data-live="clock">${b.time}</div></div>
      <div class="rm-box"><div class="rm-lab">${b.temp.lab}</div><div class="rm-val">${b.temp.v}</div></div>
      <div class="rm-box"><div class="rm-lab">Fuel · ${b.range.replace(/<small>km<\/small>/, ' km')}</div>${rmBars(b.fs)}</div>
      <div class="rm-box"><div class="rm-lab">${b.trip.lab}</div><div class="rm-val">${b.trip.v}</div></div>
    </div></section>`;
}
export function rideMode(c) {
  const { s, live, link } = c;
  const b = rideBits(c);
  return `<div class="ride-mode" id="ride-mode" data-page="0">
    <div class="rm-pages">${layoutA({ ...b, tags: `${live.rec ? '<span class="tag x"><span class="dot x pulse"></span>Recording</span>' : ''}${link.connected ? '' : '<span class="tag k">Bike not linked</span>'}${live.gpsErr ? '<span class="tag a">No GPS</span>' : ''}` })}</div>
    <button class="rm-note" data-act="goto-riding">${s.settings.autoRide ? 'Opens by itself. Change in Settings › Riding' : 'Auto-open: Settings › Riding'}</button>
    <div class="rm-foot2">
      <div class="hold"><svg viewBox="0 0 26 26"><circle cx="13" cy="13" r="11" fill="none" stroke="var(--seg-off)" stroke-width="2"/><circle class="p" cx="13" cy="13" r="11" fill="none" stroke="var(--ink)" stroke-width="2" stroke-dasharray="69.1" stroke-dashoffset="69.1" transform="rotate(-90 13 13)"/><circle cx="13" cy="13" r="4" fill="var(--ink-3)"/></svg><span class="eyebrow" data-live="rm-hint">Hold to leave</span></div>
    </div></div>`;
}

export function callOverlay(c) {
  const k = c.live.call;
  if (!k) return '';
  const ringing = k.state === 'ringing';
  return `<div class="call"><div><div class="eyebrow">${ringing ? 'Ringing on your bike' : 'On a call'}</div><div class="serif">${esc(k.name)}</div><div class="mono mt16" style="color:#9C968C;font-size:13px" data-live="call-time">${ringing ? 'Tap the handlebar button to answer' : clock((Date.now() - k.since) / 1000)}</div></div>
    <div class="actions">${ringing
      ? `<button class="cbtn" data-act="call-decline"><span style="background:#C2412D">${icon('x')}</span>Decline</button><button class="cbtn" data-act="call-answer"><span style="background:#4E7A4A">${icon('phone')}</span>Answer</button>`
      : `<button class="cbtn" data-act="call-end"><span style="background:#C2412D">${icon('x')}</span>End</button>`}</div></div>`;
}

// ---------- Help ----------
export const FAQ = [
  ['Which bikes work?', 'Pulsar and Dominar models with the Bluetooth digital cluster. Settings, Garage, All bike models lists every model Waylink knows. If your cluster can show calls from your phone, it should work.'],
  ['How do I pair on an iPhone?', 'Safari can’t talk to Bluetooth devices from a web page. On Today, tap Pair on iPhone: it walks you through opening Waylink in the free Bluefy browser, which can. On Android, Chrome pairs directly. Fuel, the logbook, rides and weather work in any browser.'],
  ['How do turn arrows reach the bike?', 'Plan your ride in Google Maps on the same phone, then keep Waylink open in Route. Each turn is sent to the cluster with the distance, and the arrow blinks in the last 100 m.'],
  ['How is mileage worked out?', 'From full tank to full tank: fill to the brim, ride, fill to the brim again. The litres of the second fill, plus any top-ups between, divided by the distance ridden.'],
  ['Where does the petrol price come from?', 'State rates that come with the app, from published city prices. Tap the price in Fuel to use your state’s rate or type your pump’s price.'],
  ['Why does weather say offline?', 'Weather needs the internet and either your city or your location. Previews inside other apps can block it; open Waylink from its own link or your Home Screen.'],
  ['Is my data shared?', 'No. Everything stays on this phone. Weather and the location lookup send only your rough position to Open-Meteo and OpenStreetMap. Use Backup in Settings to move to a new phone.'],
  ['The app looks stuck on an old version', 'Close it fully and open it again. If that doesn’t help, remove it from the Home Screen and add it again; your data stays.'],
];
export function helpSheet(c, k) {
  const { s, link } = c;
  if (k === 'faq') return `<div class="eyebrow">Help</div><h2>Questions and answers</h2><div class="faq mt16">${FAQ.map(([q, a]) => `<details class="faq-i" name="faq"><summary>${q}${icon('chev')}</summary><p>${a}</p></details>`).join('')}</div>`;
  if (k === 'report') {
    const errs = (typeof window !== 'undefined' && window.__wlLog) || [];
    const info = [`App: Waylink ${APP_VERSION}`, `Bike: ${S.model(s).name || S.model(s).id}`, `Link: ${link.state}${link.simulated ? ' (simulated)' : ''}`, `Bluetooth in browser: ${link.supported ? 'yes' : 'no'}`, `Screen: ${typeof window !== 'undefined' ? `${window.innerWidth}×${window.innerHeight}` : 'n/a'}`, `Theme: ${s.settings.theme}, icons: ${s.settings.icons || 'line'}`, `Browser: ${typeof navigator !== 'undefined' ? navigator.userAgent : 'n/a'}`, '', `Recent errors (${errs.length}):`, ...(errs.length ? errs.slice(-15) : ['none'])].join('\n');
    return `<div class="eyebrow">Help</div><h2>Report a problem</h2><p class="lead">Say what you tapped, what you expected and what happened. The app log is added for you. Nothing personal is included.</p>
      <label class="field"><span class="eyebrow">Subject</span><input class="input" id="rp-subj" maxlength="80" placeholder="Turn arrows stop after a few minutes" autocomplete="off"></label>
      <label class="field"><span class="eyebrow">What happened</span><textarea class="input" id="rp-body" style="height:110px" placeholder="Steps, what you expected, what you saw"></textarea></label>
      <details class="rp-log mt12"><summary>${icon('doc')} App log <span class="faint">· added to your report</span>${icon('chev')}</summary><textarea class="input mt8" id="backup-text" readonly style="height:150px;font-family:var(--mono);font-size:11.5px">${esc(info)}</textarea></details>
      <button class="btn ink block mt16" data-act="report-send" data-arg="email">${icon('mail')} Email support</button>
      <button class="btn line block mt8" data-act="report-send" data-arg="github">${icon('code')} Open on GitHub</button>
      <button class="link rp-save mt16" data-act="report-send" data-arg="file">Save log as a file</button>
      <p class="hint" style="text-align:center">${SUPPORT_EMAIL} · ${REPO_URL.replace('https://', '')}</p>`;
  }
  if (k === 'about') return `<div class="about-sheet"><h2 class="sr-only">Waylink</h2><div class="splash-brand about-hero anim">${TURNS}${LOOP}</div><p class="lead">Turns, calls and your bike’s care, on one screen. Version ${APP_VERSION}.</p></div>
    <div class="list flat mt16">
      ${li({ ic: 'love', t: 'Support Waylink', d: 'Chip in towards development', act: 'sheet', arg: 'support' })}
      ${li({ ic: 'code', t: 'Source code', d: 'MIT licence', end: '', tag: 'a', href: REPO_URL })}
      ${li({ ic: 'bt', t: 'Bike protocol', d: 'PulsarNav on GitHub', tag: 'a', href: 'https://github.com/nikhilmaity00/pulsarnav' })}
      ${li({ ic: 'grid', t: 'Icons', d: 'Hugeicons', tag: 'a', href: 'https://hugeicons.com' })}
      ${li({ ic: 'image', t: 'Photo credits', d: 'Authors and licences of bike photos', act: 'sheet', arg: 'credits' })}
      ${li({ ic: 'wxPart', t: 'Weather', d: 'Open-Meteo, free and open data', chev: false, tag: 'div' })}
      ${li({ ic: 'pin', t: 'Places', d: 'OpenStreetMap Nominatim', chev: false, tag: 'div' })}
      ${li({ ic: 'lock', t: 'Privacy', d: 'Your data stays on this phone', chev: false, tag: 'div' })}
    </div>
    <p class="hint">Not affiliated with Bajaj Auto, OTC, Google or WhatsApp. Names and marks belong to their owners.</p>`;
  return null;
}

// ---------- Sheets ----------
export function sheetHTML(c, key) {
  const html = sheetInner(c, key);
  return html == null ? html : html.replace(/<div class="eyebrow">((?:(?!<\/div>)[\s\S])*?)<\/div>\s*<h2>((?:(?!<\/h2>)[\s\S])*?)<\/h2>/, '<h2>$2</h2><div class="sheet-sub">$1</div>');
}
function sheetInner(c, key) {
  const { s, link, live } = c;
  const [k, arg] = key.split(/:(.*)/s);
  const co = S.country(s), md = S.model(s), cur = co.cur, v0 = S.veh(s);
  switch (k) {
    case 'svc': {
      const it = s.service.find((x) => x.id === arg); if (!it) return null;
      const st = S.serviceStatus(s, it);
      const v = rowValue(st);
      const hint = { oil: md.oil, tyres: `Cold: ${md.psi[0]} front, ${md.psi[2]} rear psi solo. ${md.psi[1]} / ${md.psi[3]} with a pillion.`, chain: `Slack ${md.chain.replace(' slack', '')}.`, service: md.oil, coolant: md.coolant }[it.kind];
      return `<div class="eyebrow">${esc(clip(S.vehicleTitle(s)))} · ${everyText(it)}</div><h2>${esc(it.name)}</h2>
        <div class="flex between mt16" style="align-items:baseline"><span><span class="big ${v.cls}" style="font-size:52px">${v.v}</span><span class="unit ${v.cls}" style="font-size:14px">${st.status === 'overdue' ? v.u : v.u + ' left'}</span></span><span class="small muted">Last done ${dMed(it.lastDate)}</span></div>
        <div class="mt12">${serviceSegs(st, 10, 'thick')}</div>
        <p class="lead">${dueSentence(st)}</p>${hint ? `<p class="hint">${esc(hint)}</p>` : ''}
        <button class="btn ink block mt20" data-act="svc-done" data-arg="${it.id}">${icon('check')} Mark as done</button>
        <div class="eyebrow mt24">Done earlier?</div>
        <div class="chips mt8"><button class="chip" data-act="svc-earlier" data-arg="${it.id}|1">Yesterday</button><button class="chip" data-act="svc-earlier" data-arg="${it.id}|7">Last week</button><button class="chip" data-act="svc-pick" data-arg="${it.id}">${icon('cal')} Pick a date</button></div>
        <div id="svc-pick-${it.id}"></div>
        <div class="divider"></div>
        <button class="sheet-row" data-act="sheet" data-arg="svc-edit:${it.id}"><span class="flex gap8">${icon('edit').replace('<svg', '<svg style="width:16px;height:16px;color:var(--ink-2)"')} Edit reminder</span>${icon('chev')}</button>`;
    }
    case 'svc-edit': {
      const isNew = arg === 'new';
      const it = isNew ? { name: '', everyKm: 1000, everyDays: 0, lastKm: s.odo, lastDate: Date.now() } : s.service.find((x) => x.id === arg);
      if (!it) return null;
      return `<form data-form="svc-edit" data-arg="${isNew ? 'new' : it.id}"><div class="eyebrow">Logbook</div><h2>${isNew ? 'New reminder' : 'Edit reminder'}</h2>
        <label class="field"><span class="eyebrow">Name</span><input class="input" name="name" required value="${esc(it.name)}" placeholder="Clutch cable lube" maxlength="${LIMITS.item}"></label>
        ${it.milestone ? `<label class="field"><span class="eyebrow">Next service at</span><div class="input-wrap"><input class="input" name="targetKm" type="number" inputmode="numeric" min="0" value="${it.targetKm}"><span class="suffix">km</span></div></label><p class="hint">Bajaj schedule: 500 km, 5,000 km, 10,000 km, then every 5,000.</p>` : `
        <div class="two"><label class="field"><span class="eyebrow">Every</span><div class="input-wrap"><input class="input" name="everyKm" type="number" inputmode="numeric" min="0" value="${it.everyKm || ''}" placeholder="0"><span class="suffix">km</span></div></label>
        <label class="field"><span class="eyebrow">Or every</span><div class="input-wrap"><input class="input" name="everyMonths" type="number" inputmode="decimal" min="0" step="0.5" value="${it.everyDays ? +(it.everyDays / 30.4).toFixed(1) : ''}" placeholder="0"><span class="suffix">months</span></div></label></div>
        <p class="hint">Leave one blank to go by distance or time alone.</p>`}
        <div class="two"><label class="field"><span class="eyebrow">Last done at</span><div class="input-wrap"><input class="input" name="lastKm" type="number" inputmode="numeric" min="0" value="${Math.round(it.lastKm)}"><span class="suffix">km</span></div></label>
        <label class="field"><span class="eyebrow">On</span><input class="input" name="lastDate" type="date" value="${isoDate(it.lastDate)}"></label></div>
        <button class="btn ink block mt24" type="submit">Save</button>
        ${isNew ? '' : `<button class="btn danger block mt8" type="button" data-act="svc-del" data-arg="${it.id}">Delete reminder</button>`}</form>`;
    }
    case 'rec': {
      const isNew = arg === 'new';
      const r = isNew ? { date: Date.now(), odo: s.odo, title: '', workshop: '', cost: '', notes: '' } : s.records.find((x) => x.id === arg);
      if (!r) return null;
      return `<form data-form="rec" data-arg="${isNew ? 'new' : r.id}"><div class="eyebrow">Workshop visit</div><h2>${isNew ? 'Log a visit' : esc(r.title)}</h2>
        <label class="field"><span class="eyebrow">What was done</span><input class="input" name="title" required value="${esc(r.title)}" placeholder="4th paid service" maxlength="${LIMITS.title}"></label>
        <div class="two"><label class="field"><span class="eyebrow">Date</span><input class="input" type="date" name="date" value="${isoDate(r.date)}"></label>
        <label class="field"><span class="eyebrow">Odometer</span><div class="input-wrap"><input class="input" type="number" inputmode="numeric" name="odo" value="${Math.round(r.odo)}"><span class="suffix">km</span></div></label></div>
        <div class="two"><label class="field"><span class="eyebrow">Workshop</span><input class="input" name="workshop" value="${esc(r.workshop)}" placeholder="Sri Ganesh Bajaj" maxlength="${LIMITS.place}"></label>
        <label class="field"><span class="eyebrow">Cost</span><div class="input-wrap"><input class="input" type="number" inputmode="decimal" name="cost" value="${r.cost}" placeholder="0"><span class="suffix">${cur}</span></div></label></div>
        <label class="field"><span class="eyebrow">Notes</span><textarea class="input" name="notes" placeholder="Oil, filter, chain set…">${esc(r.notes)}</textarea></label>
        <button class="btn ink block mt24" type="submit">Save</button>
        ${isNew ? '' : `<button class="btn danger block mt8" type="button" data-act="rec-del" data-arg="${r.id}">Delete visit</button>`}</form>`;
    }
    case 'fuel': {
      const isNew = arg === 'new';
      const e = isNew ? { odo: s.odo, litres: '', cost: '', full: true, ts: Date.now() } : s.fuel.find((x) => x.id === arg);
      if (!e) return null;
      const price = S.fuelPriceOf(s);
      return `<form data-form="fuel" data-arg="${isNew ? 'new' : e.id}"><div class="eyebrow">Fuel · ${esc(clip(S.vehicleTitle(s)))}</div><h2>${isNew ? 'Log fuel' : 'Fill-up'}</h2>
        <label class="field"><span class="eyebrow">Odometer</span><div class="input-wrap"><input class="input" name="odo" type="number" inputmode="decimal" step="0.1" required value="${e.odo}"><span class="suffix">km</span></div></label>
        <div class="two"><label class="field"><span class="eyebrow">Litres</span><div class="input-wrap"><input class="input" name="litres" type="number" inputmode="decimal" step="0.01" min="0.1" required value="${e.litres}" placeholder="0.00" data-input="fuel-l"><span class="suffix">L</span></div></label>
        <label class="field"><span class="eyebrow">Paid</span><div class="input-wrap"><input class="input" name="cost" type="number" inputmode="decimal" step="0.01" min="0" value="${e.cost}" placeholder="0" data-input="fuel-c"><span class="suffix">${cur}</span></div></label></div>
        <p class="hint">${cur}${num(price, 2)} a litre. Fill either one; the other follows.</p>
        <input type="hidden" name="price" value="${price}">
        <div class="list flat mt16">${li({ t: 'Filled to the brim', d: 'Needed to work out mileage', end: `<button type="button" class="switch ${e.full ? 'on' : ''}" data-act="fuel-full" aria-label="Full tank"></button>`, chev: false, tag: 'div' })}</div>
        <input type="hidden" name="full" value="${e.full ? 1 : 0}">
        <label class="field"><span class="eyebrow">Date and time</span><input class="input" type="datetime-local" name="date" value="${isoDateTime(e.ts)}" max="${isoDateTime(Date.now() + 3600000)}"></label>
        <button class="btn ink block mt24" type="submit">${icon('check')} Save</button>
        ${isNew ? '' : `<button class="btn danger block mt8" type="button" data-act="fuel-del" data-arg="${e.id}">Delete fill-up</button>`}</form>`;
    }
    case 'ios-pair': {
      const here = typeof location !== 'undefined' ? location.origin + location.pathname : REPO_URL;
      const step = (n, t, body) => `<div class="pstep"><span class="pn">${n}</span><div class="grow"><div class="pt">${t}</div>${body}</div></div>`;
      return `<div class="eyebrow">Bluetooth on iPhone</div><h2>Pair your bike</h2><p class="lead">Safari can’t reach Bluetooth from a web page. The free Bluefy browser can, so pairing happens there.</p>
        <div class="psteps mt16">
          ${step(1, 'Get Bluefy', `<p>A free browser that adds Bluetooth for web apps.</p><a class="btn line sm mt8" href="${BLUEFY_STORE}" target="_blank" rel="noopener">${icon('down')} App Store</a>`)}
          ${step(2, 'Copy your Waylink link', `<textarea class="input mt8" id="backup-text" readonly rows="1" style="font-family:var(--mono);font-size:12px;height:52px;resize:none">${esc(here)}</textarea><button class="btn line sm mt8" data-act="copy-text">${icon('copy')} Copy link</button>`)}
          ${step(3, 'Open it in Bluefy', `<p>Paste the link into Bluefy’s address bar, then tap <b>Pair your bike</b> on Today and pick your bike from the list.</p><a class="btn ink sm mt8" href="bluefy://">${icon('nav')} Open Bluefy</a>`)}
          ${step(4, 'Bring your data along', `<p>Bluefy keeps its own copy of the app. In Safari, use Settings, Data & region, <b>Copy as text</b>; then in Bluefy, <b>Restore a backup</b> and paste it.</p>`)}
        </div>
        <p class="hint">Switch on the bike’s ignition and keep it within a few metres while pairing. After the first time, Bluefy reconnects by itself.</p>`;
    }
    case 'pick-font':
    case 'pick-body': {
      const st = s.settings, cur = st.bodyFont || 'system';
      const opt = ([k, nm, fam, w]) => `<button class="ftile ${st.font === k ? 'on' : ''}" data-act="font" data-arg="${k}"><span class="ag" style="font-family:${fam}, Georgia, system-ui, sans-serif;font-weight:${w}">Ag</span><span class="nm">${nm}</span></button>`;
      return `<div class="eyebrow">Appearance</div><h2>Fonts</h2>
        <div class="ms-t mt20">Titles</div>
        <div class="eyebrow mt12">Sans serif</div><div class="ftiles mt8">${FONTS.filter((f) => f[4] === 'sans').map(opt).join('')}</div>
        <div class="eyebrow mt16">Serif</div><div class="ftiles mt8">${FONTS.filter((f) => f[4] === 'serif').map(opt).join('')}</div>
        <div class="ms-t mt32">Body text</div>
        <div class="ftiles mt12">${BODY_FONTS.map(([k, nm, fam, w]) => `<button class="ftile ${cur === k ? 'on' : ''}" data-act="body-font" data-arg="${k}"><span class="ag" style="font-family:${fam}, system-ui, sans-serif;font-weight:${w || 400}">Aa</span><span class="nm">${nm}</span></button>`).join('')}</div>`;
    }
    case 'backup':
      return `<div class="eyebrow">Data</div><h2>Backup & restore</h2><p class="lead">Everything stays on this phone. Keep a copy, or move it to a new phone.</p>
        <div class="list flat mt16">
          ${li({ ic: 'down', t: 'Export backup', d: 'Save one .json file', act: 'export' })}
          ${li({ ic: 'up', t: 'Restore a backup', d: 'From an exported file', act: 'import' })}
          ${li({ ic: 'copy', t: 'Copy as text', d: 'Paste it on your new phone', act: 'copy-backup' })}
        </div>`;
    case 'pick-icons': {
      const was = ICON_STYLE, cur = ['duo', 'fill'].includes(s.settings.icons) ? s.settings.icons : 'line';
      const card = (k, nm, d) => { ICON_STYLE = k; const h = ['fuel', 'wrench', 'bell', 'bike'].map((n) => icon(n)).join(''); return `<button class="icoopt ${cur === k ? 'on' : ''}" data-act="icon-style" data-arg="${k}"><span class="io-row">${h}</span><span class="nm">${nm}</span></button>`; };
      const out = `<div class="eyebrow">Appearance</div><h2>Icon style</h2><p class="lead">How icons are drawn across the app.</p>
        <div class="icoopts mt16">${card('line', 'Line', 'Clean single strokes')}${card('duo', 'Glow', 'A soft glow under each line')}${card('fill', 'Duotone', 'Shapes filled with a second tone')}</div>`;
      ICON_STYLE = was; return out;
    }
    case 'pick-brand': {
      const was = ICON_STYLE, cur = ['duo', 'fill'].includes(s.settings.icons) ? s.settings.icons : 'line';
      const card = (k, nm, d) => { ICON_STYLE = k; const h = ['fuel', 'wrench', 'bell', 'bike'].map((n) => icon(n)).join(''); return `<button class="icoopt ${cur === k ? 'on' : ''}" data-act="icon-style" data-arg="${k}"><span class="io-row">${h}</span><span class="nm">${nm}</span></button>`; };
      const icoHTML = card('line', 'Line', 'Clean single strokes') + card('duo', 'Glow', 'A soft glow under each line') + card('fill', 'Duotone', 'Shapes filled with a second tone'); ICON_STYLE = was;
      return `<div class="eyebrow">Appearance</div><h2>Icons</h2>
        <button class="brand-final mt12 anim" data-act="replay-logo" aria-label="Play the logo animation">${LOOP}</button>
        <div class="ms-t mt24">App icon</div><p class="small muted mt8">On your Home Screen. Remove Waylink and add it again to change an installed icon.</p>
        <div class="appicons mt12">${ICON_STYLES.map(([k, nm]) => `<button class="appicon ${(s.settings.iconStyle || 'classic') === k ? 'on' : ''}" data-act="app-icon" data-arg="${k}"><img src="${appIcon({ settings: { ...s.settings, iconStyle: k } })}" alt="" width="56" height="56"><span>${nm}</span></button>`).join('')}</div>
        <div class="ms-t mt24">Icon style</div>
        <div class="icoopts mt12">${icoHTML}</div>`;
    }
    case 'pick-progress': {
      const cur = s.settings.progress || 'gauge', was = PROGRESS;
      const ok = { status: 'ok', frac: 0.35, dueDate: Date.now() + 40 * 86400000 }, soon = { status: 'soon', frac: 0.86, dueDate: Date.now() + 6 * 86400000 };
      const card = ([k, nm, d]) => { PROGRESS = k; const h = serviceSegs(ok, 10, 'prev') + serviceSegs(soon, 10, 'prev'); return `<button class="progopt ${cur === k ? 'on' : ''}" data-act="progress-style" data-arg="${k}"><div class="pv">${h}</div><div class="nm">${nm}</div></button>`; };
      const out = `<div class="eyebrow">Appearance</div><h2>Progress style</h2><p class="lead">How service jobs show what’s left.</p>
        <div class="progs mt16">${PROGRESS_STYLES.map(card).join('')}</div>`;
      PROGRESS = was; return out;
    }
    case 'support': {
      const amt = live.supportAmt ?? 99, has = SUPPORT.upi || SUPPORT.links.length;
      const upi = SUPPORT.upi ? `upi://pay?pa=${encodeURIComponent(SUPPORT.upi)}&pn=${encodeURIComponent(SUPPORT.payee)}&cu=INR&tn=${encodeURIComponent('Support Waylink')}${amt ? `&am=${amt}` : ''}` : '';
      return `<div class="support-hero"><h2>Support Waylink</h2><p class="lead">Waylink is free, with no ads and no tracking. If it makes your rides easier, you can chip in towards new features and upkeep.</p></div>
        <div class="give mt20">
          <div class="give-hero"><span class="gi">${amtIcon(amt)}</span><div class="grow"><div class="gv">${amt ? `₹${amt}` : 'Any amount'}</div><div class="gl">${AMT_ICON[amt][2]}</div></div></div>
          <div class="give-chips">${[...UPI_AMOUNTS, 0].map((a) => `<button class="${amt === a ? 'on' : ''}" data-act="support-amt" data-arg="${a}">${a ? `₹${a}` : 'Other'}</button>`).join('')}</div>
          ${SUPPORT.upi ? `<a class="btn ink block mt16" href="${upi}">Pay ${amt ? `₹${amt} ` : ''}with any UPI app</a>
          <button class="give-copy" data-act="copy-upi">${icon('copy')} ${esc(SUPPORT.upi)}</button>` : `<button class="btn ink block mt16" disabled>Pay ${amt ? `₹${amt} ` : ''}with any UPI app</button>
          <div class="give-note">${icon('info')} Payments open once a UPI ID is added.</div>`}
        </div>
        ${SUPPORT.links.length ? `<div class="list mt12">${SUPPORT.links.map(([n, u]) => li({ ic: 'love', t: esc(n), d: esc(u.replace(/^https?:\/\//, '')), tag: 'a', href: u })).join('')}</div>` : ''}
        <div class="eyebrow mt24">Free ways to help</div>
        <div class="list mt8">
          ${li({ ic: 'sparkles', t: 'Star it on GitHub', d: 'Helps other riders find it', tag: 'a', href: REPO_URL })}
          ${li({ ic: 'copy', t: 'Share Waylink', d: 'Copy the link for a riding group', act: 'copy-app-link' })}
          ${li({ ic: 'flag', t: 'Report a problem or idea', d: 'Every report makes it better', act: 'sheet', arg: 'report' })}
        </div>
        <p class="hint">Payments go straight to the developer through your UPI app or the linked site. Waylink never sees your payment details.</p>`;
    }
    case 'weather': return weatherSheet(c);
    case 'faq': case 'report': case 'about': return helpSheet(c, k);
    case 'help': {
      const h = HELP[arg]; if (!h) return null;
      return `<div class="eyebrow">${h[0]}</div><h2>${h[1]}</h2>${h[2].map((t, i) => `<p class="${i ? 'help-p' : 'lead'}">${t}</p>`).join('')}<button class="btn ink block mt24" type="button" data-act="close">Got it</button>`;
    }
    case 'fuelprice': {
      const price = S.fuelPriceOf(s);
      if (co.code !== 'IN') return fieldForm('fuelPrice', 'Fuel', 'Price per litre', price, 'number', cur, 'Fills in the cost when you log fuel.');
      const area = s.profile.fuelArea, found = S.fuelLookup(area?.state, area?.place, s.profile.city);
      const asOf = dShort(new Date(S.FUEL_PRICES.asOf).getTime());
      const local = found?.price
        ? `<div class="local-price"><div><span class="eyebrow">Petrol in ${esc(found.state)}</span><div class="lp-v"><span class="big">${cur}${num(found.price, 2)}</span><span class="unit">a litre</span></div><div class="small faint">${esc(area?.place || s.profile.city || found.state)} · rate on ${asOf}</div></div>
          ${Math.abs(found.price - price) < 0.005 ? `<span class="chip on">${icon('check')} In use</span>` : `<button class="chip ink" type="button" data-act="fuel-use" data-arg="${found.price}">Use</button>`}</div>`
        : `<p class="small muted">${found ? `No rate for ${esc(found.state)} yet. Enter the pump price below.` : 'Find the petrol price where you ride.'}</p>`;
      return `<form data-form="field" data-arg="fuelPrice"><div class="eyebrow">Fuel</div><h2>Price per litre</h2><p class="lead">Fills in the cost when you log fuel.</p>
        <div class="card pad mt16">${local}
          <button class="btn line block mt12" type="button" data-act="fuel-locate" ${live.locating ? 'disabled' : ''}>${icon('pin')} ${live.locating ? 'Finding you…' : found ? 'Update from my location' : 'Use my location'}</button></div>
        <label class="field"><span class="eyebrow">Your price</span><div class="input-wrap"><input class="input" name="v" type="number" inputmode="decimal" step="any" min="0" value="${esc(price)}"><span class="suffix">${cur}</span></div></label>
        <p class="hint">Rates are state averages that come with the app. Your pump may differ by a rupee or two, and logging a fill-up with litres and cost updates this for you.</p>
        <button class="btn ink block mt24" type="submit">Save</button></form>`;
    }
    case 'odo':
      return fieldForm('odo', S.vehicleTitle(s), 'Odometer', s.odo, 'number', 'km', 'Recorded rides add to this. Fix it here if it drifts.');
    case 'trip': {
      const t = arg === 'A' ? s.tripA : s.tripB;
      return `<div class="eyebrow">Trip ${arg}</div><h2>${num(t.km, 1)} km</h2><p class="lead">Since ${dFull(t.since)}. Every ride you record adds to it.</p><button class="btn ink block mt24" data-act="trip-reset" data-arg="${arg}">Reset trip ${arg}</button>`;
    }
    case 'pick':
      return `<div class="eyebrow">Region</div><h2>Country</h2><p class="lead">Sets currency, fuel price and emergency numbers.</p><div class="list flat mt16">${S.COUNTRIES.map((x) => li({ t: esc(x.name), d: `${x.cur} ${x.curCode} · SOS ${x.sos}`, end: x.code === s.profile.country ? selmark(true) : '', act: 'set-country', arg: x.code, chev: false })).join('')}</div>`;
    case 'garage':
      return `<div class="eyebrow">Garage</div><h2>Your bikes</h2>
        <div class="list flat mt16">${s.vehicles.map((v) => li({ lead: vehicleArt(s, v, 'lg'), t: esc(S.vehicleTitle(s, v)), d: `${esc(S.nameOfModel(s, S.modelOf(v)))} · ${num(Math.floor(v.odo))} km`, end: v.id === s.active ? selmark(true) : '', act: 'switch-vehicle', arg: v.id, chev: v.id !== s.active })).join('')}</div>
        <button class="btn ink block mt16" data-act="sheet" data-arg="vehicle:new">${icon('plus')} Add a vehicle</button>
        <p class="hint" style="text-align:center">Each bike keeps its own fuel, rides and service.</p>`;
    case 'vehicle': {
      const isNew = arg === 'new';
      const v = isNew ? { model: 'ns200', nickname: '', plate: '', odo: '', colour: S.COLOURS[s.vehicles.length % S.COLOURS.length][0], photo: null } : s.vehicles.find((x) => x.id === arg);
      if (!v) return null;
      const groups = [...new Set(S.MODELS.map((m) => m.series))];
      const preview = { ...v, photo: live.draftPhoto === undefined ? v.photo : live.draftPhoto };
      return `<form data-form="vehicle" data-arg="${isNew ? 'new' : v.id}"><div class="eyebrow">Garage</div><h2>${isNew ? 'Add a vehicle' : 'Edit vehicle'}</h2>
        <div class="mt16" id="veh-preview">${vehicleArt({ settings: { art: 'photo' } }, preview, 'hero')}</div>
        ${!preview.photo && S.MODEL_IMAGES[v.model]?.credit ? `<p class="hint" style="margin-top:8px">${esc(S.MODEL_IMAGES[v.model].credit)}</p>` : ''}
        <label class="field"><span class="eyebrow">Model</span><select class="input" name="model" data-input="veh-model">${groups.map((g) => `<optgroup label="${g}">${S.MODELS.filter((m) => m.series === g).map((m) => `<option value="${m.id}" ${m.id === v.model ? 'selected' : ''}>${esc(S.nameOfModel(s, m))}</option>`).join('')}</optgroup>`).join('')}</select></label>
        <div class="two"><label class="field"><span class="eyebrow">Nickname</span><input class="input" name="nickname" value="${esc(v.nickname)}" placeholder="Daily" maxlength="${LIMITS.nickname}"></label>
        <label class="field"><span class="eyebrow">Number plate</span><input class="input" name="plate" value="${esc(v.plate)}" placeholder="KA 05 MX 2481" maxlength="${LIMITS.plate}" style="text-transform:uppercase"></label></div>
        <label class="field"><span class="eyebrow">Odometer</span><div class="input-wrap"><input class="input" name="odo" type="number" inputmode="numeric" min="0" value="${v.odo}" placeholder="0" required><span class="suffix">km</span></div></label>
        <div class="field"><span class="eyebrow">Colour</span><div class="swatches">${S.COLOURS.map(([hex, l]) => `<button type="button" class="swatch ${hex === v.colour ? 'on' : ''}" style="background:${hex}" data-act="veh-colour" data-arg="${hex}" aria-label="${l}" title="${l}"></button>`).join('')}</div><input type="hidden" name="colour" value="${v.colour}"></div>
        <div class="field"><span class="eyebrow">Photo</span><div class="btns"><label class="btn line sm">${icon('camera')} ${preview.photo ? 'Replace' : 'Add photo'}<input type="file" accept="image/*" hidden data-input="veh-photo"></label>${preview.photo ? '<button type="button" class="btn line sm" data-act="veh-photo-clear">Remove</button>' : ''}</div><p class="hint">Side-on photos look best. Cropped to fit.</p></div>
        <button class="btn ink block mt24" type="submit">${isNew ? 'Add to garage' : 'Save'}</button>
        ${!isNew && s.vehicles.length > 1 ? `<button class="btn danger block mt8" type="button" data-act="veh-del" data-arg="${v.id}">Remove from garage</button>` : ''}</form>`;
    }
    case 'veh-del': {
      const v = s.vehicles.find((x) => x.id === arg); if (!v) return null;
      return `<div class="eyebrow">Garage</div><h2>Remove ${esc(S.vehicleTitle(s, v))}?</h2><p class="lead">Its fuel log, rides and service history go with it. Export a backup first if you might need them.</p><button class="btn block mt24" style="background:var(--red);color:#fff" data-act="veh-del-yes" data-arg="${v.id}">Remove</button><button class="btn line block mt8" data-act="close">Keep it</button>`;
    }
    case 'specs':
      return `<div class="eyebrow">${esc(md.series)}</div><h2>${esc(S.bikeName(s))}</h2><p class="lead">${esc(md.engine)}</p>
      <div class="card mt16" style="padding:2px 18px;box-shadow:none;background:var(--raised)">
      ${[['Fuel tank', `${num(md.tank, 1)} L · ${num(md.reserve, 1)} L reserve`], ['Engine oil', md.oil], ['Coolant', md.coolant], ['Tyres', `${md.tyres[0]} front · ${md.tyres[1]} rear`], ['Pressure, solo', `${md.psi[0]} front · ${md.psi[2]} rear psi`], ['With pillion', `${md.psi[1]} front · ${md.psi[3]} rear psi`], ['Chain', md.chain + ', lube every 500 km'], ['Spark plug', md.plug]].map(([a, b]) => `<div class="kv"><span class="muted" style="flex:none">${a}</span><b>${esc(b)}</b></div>`).join('')}
      </div><p class="hint">Cold tyre pressures. Check your owner’s manual for your variant.</p>`;
    case 'bike': {
      const stt = link.state;
      return `<div class="eyebrow">Bluetooth · ${esc(clip(S.vehicleTitle(s)))}</div><h2>${stt === 'connected' ? 'Connected' : stt === 'idle' ? (link.device || v0.bike?.id ? 'Paired' : 'Not paired') : stt === 'connecting' ? 'Connecting' : 'Searching'}</h2>
      <p class="lead">${stt === 'connected' ? `${esc(link.deviceName)}. ${num(link.frames)} frames sent. Status every 5 s, turns every 0.8 s.` : link.supported ? 'Switch on the ignition first, so the display is awake. Then pick your bike from the list.' : (isIOS() ? 'iPhone browsers have no Bluetooth for web pages. Bluefy, a free browser, adds it.' : 'This browser has no Bluetooth. Use Chrome on Android or a computer, or try the simulated bike.')}</p>
      <div class="mt24">${stt === 'connected' ? `<button class="btn line block" data-act="disconnect">Disconnect</button>` : link.supported ? `<button class="btn ink block" data-act="pair">${icon('bt')} Pair a bike</button>${link.device || v0.bike?.id ? '<button class="btn line block mt8" data-act="reconnect">Reconnect</button>' : ''}` : isIOS() ? `<button class="btn ink block" data-act="sheet" data-arg="ios-pair">${icon('bt')} Pair on iPhone</button>` : ''}
      ${!link.simulated || stt !== 'connected' ? `<button class="btn line block mt8" data-act="sim-on">Use the simulated bike</button>` : ''}
      ${link.device || v0.bike?.id ? `<button class="btn danger block mt8" data-act="forget">Forget this bike</button>` : ''}</div>
      <div class="divider"></div><button class="sheet-row" data-act="sheet" data-arg="log"><span>Connection log</span>${icon('chev')}</button>`;
    }
    case 'log':
      return `<div class="eyebrow">Developer</div><h2>Connection log</h2>${link.log.length ? `<div class="mt16">${link.log.slice(0, 80).map((l) => `<div class="kv" style="font-size:13px;gap:12px"><span class="mono faint" style="flex:none;font-size:11px">${new Date(l.t).toLocaleTimeString('en-GB')}</span><span class="${l.kind === 'warn' ? 'amber' : ''}" style="flex:1">${esc(l.msg)}</span></div>`).join('')}</div>` : '<p class="lead">Nothing yet.</p>'}`;
    case 'protocol':
      return `<div class="eyebrow">Developer</div><h2>Protocol</h2><p class="lead">A byte-for-byte port of PulsarNav’s packet builder. Writes go out one at a time.</p>
      <div class="card mt16" style="padding:2px 18px;box-shadow:none;background:var(--raised)">${[['Service', UUID.service], ['Turn-by-turn · 48 B', UUID.tbt], ['Status · 89 B, +55 B legacy', UUID.general], ['Missed call · 63 B', UUID.missedCall], ['Alerts · 44 B', UUID.alerts], ['Handlebar · read every 0.7 s', UUID.buttons]].map(([a, b]) => `<div class="kv" style="flex-direction:column;align-items:flex-start;gap:4px"><span class="small muted">${a}</span><span class="mono" style="font-size:11.5px;word-break:break-all">${b}</span></div>`).join('')}</div>
      <div class="eyebrow mt24">Turn frame</div><div class="card mt8" style="padding:12px 18px;box-shadow:none;background:var(--raised);font-size:13.5px;line-height:1.8">
      <div><b class="mono">0</b> type, step unit, PM flag</div><div><b class="mono">1</b> arrow id, +32 to blink under 100 m</div><div><b class="mono">2–5</b> step distance</div><div><b class="mono">6–7</b> ETA minute, hour and exit</div><div><b class="mono">8–11</b> total distance</div><div><b class="mono">12</b> total unit, GPS on</div><div><b class="mono">14–45</b> street name</div><div><b class="mono">47</b> checksum</div></div>`;
    case 'sos': {
      const near = live.pos ? `https://www.google.com/maps/search/hospital/@${live.pos[0]},${live.pos[1]},14z` : 'https://www.google.com/maps/search/hospitals+near+me';
      const tel = (n, t, d, ic) => `<a class="li" href="tel:${esc(n.replace(/\s/g, ''))}"><span class="ic" style="background:var(--tint-red);color:var(--red)">${icon(ic)}</span><span class="tx"><div class="t">${t}</div><div class="d">${d}</div></span><span class="end"><span class="big" style="font-size:24px;color:var(--ink)">${esc(n)}</span></span></a>`;
      return `<div class="eyebrow">Emergency · ${esc(co.name)}</div><h2>SOS</h2><p class="lead">Tap to call. If it doesn’t dial, the number is right there.</p>
      <div class="list flat mt16">${tel(co.amb, 'Ambulance', 'Medical emergency', 'ambulance')}${tel(co.sos, 'Emergency', 'All services', 'siren')}${tel(co.police, 'Police', 'Police help', 'shield')}${tel(co.rsa, 'Highway help', 'Roadside assistance', 'wrench')}
      ${s.profile.icePhone ? tel(s.profile.icePhone, esc(s.profile.iceName || 'Emergency contact'), 'Your emergency contact', 'heart') : li({ ic: 'heart', t: 'Add an emergency contact', d: 'Someone to call first', act: 'go', arg: 'profile' })}
      ${li({ ic: 'hospital', t: 'Nearest hospitals', d: 'Opens Google Maps', href: near, tag: 'a' })}</div>
      <button class="card pad mt12 flex between gap12" style="width:100%;box-shadow:none;background:var(--raised);text-align:left" data-act="go" data-arg="profile"><span class="grow"><span class="tag r">Blood group ${esc(s.profile.blood || '—')}</span><span class="small muted mt8" style="display:block">${esc(s.profile.medical || 'No medical notes yet. Tap to add.')}</span></span>${icon('chev', 'faint')}</button>`;
    }
    case 'parking': {
      const p = s.parking;
      return `<div class="eyebrow">${esc(clip(S.vehicleTitle(s)))}</div><h2>Parked bike</h2>${p ? `<p class="lead">Saved ${ago(p.ts)}, at ${tShort(p.ts)}.</p><p class="mono small faint">${p.lat.toFixed(5)}, ${p.lng.toFixed(5)}</p>
      <a class="btn ink block mt20" href="https://www.google.com/maps/dir/?api=1&destination=${p.lat},${p.lng}&travelmode=walking" target="_blank" rel="noopener">${icon('map')} Walk back with Google Maps</a>
      <div class="btns mt8"><button class="btn line" data-act="park-save">Update</button><button class="btn line" data-act="park-clear">Clear</button></div>` : `<p class="lead">Saves where you are now. It also saves by itself when the bike disconnects.</p><button class="btn ink block mt24" data-act="park-save">${icon('pin')} Save where I parked</button>`}`;
    }
    case 'glovebox': {
      const slots = [[`${v0.id}:rc`, 'Registration (RC)', S.vehicleTitle(s)], [`${v0.id}:insurance`, 'Insurance', S.vehicleTitle(s)], [`${v0.id}:puc`, 'Pollution (PUC)', S.vehicleTitle(s)], ['rider:licence', 'Driving licence', 'You'], [`${v0.id}:other`, 'Other', S.vehicleTitle(s)]];
      return `<div class="eyebrow">Papers · ${esc(clip(S.vehicleTitle(s)))}</div><h2>Digital glovebox</h2><p class="lead">Copies for when there’s no signal. Kept only on this phone.</p>
      <div class="list flat mt16">${slots.map(([k2, l, who]) => { const d = live.glove.find((x) => x.slot === k2); const exp = d?.expiry ? new Date(d.expiry) : null; const soon = exp && exp - Date.now() < 30 * 86400000; return li({ ic: 'doc', t: l, d: d ? (exp ? `${exp < Date.now() ? 'Expired' : 'Valid till'} ${dFull(exp)}` : `Added ${dShort(d.added)}`) : `Not added · ${esc(who)}`, end: d ? (soon ? '<span class="dot r"></span>' : '<span class="dot"></span>') : '', act: 'sheet', arg: 'doc:' + k2 }); }).join('')}</div>`;
    }
    case 'doc': {
      const label = { rc: 'Registration (RC)', insurance: 'Insurance', puc: 'Pollution (PUC)', licence: 'Driving licence', other: 'Other' }[arg.split(':')[1]] || 'Document';
      const d = live.glove.find((x) => x.slot === arg);
      const url = d ? live.docUrls[arg] : null;
      return `<div class="eyebrow">Glovebox</div><h2>${label}</h2>
      ${d ? (d.type.startsWith('image/') ? `<img class="doc-thumb" src="${url}" alt="${label}">` : `<a class="card pad mt16 flex gap12" href="${url}" target="_blank" rel="noopener" style="text-decoration:none;box-shadow:none;background:var(--raised)">${icon('doc').replace('<svg', '<svg style="width:20px;height:20px"')}<span class="grow">${esc(d.name)}</span><span class="chip">Open</span></a>`) : '<p class="lead">Add a clear photo or a PDF.</p>'}
      <label class="btn ${d ? 'line' : 'ink'} block mt16">${icon('up')} ${d ? 'Replace' : 'Choose photo or PDF'}<input type="file" accept="image/*,application/pdf" hidden data-input="doc-file" data-arg="${arg}"></label>
      ${d ? `<label class="field"><span class="eyebrow">Valid till</span><input class="input" type="date" value="${d.expiry ? isoDate(d.expiry) : ''}" data-input="doc-expiry" data-arg="${arg}"></label><button class="btn danger block mt16" data-act="doc-del" data-arg="${arg}">Remove</button>` : ''}`;
    }
    case 'trip-detail': {
      const t = s.trips.find((x) => x.id === arg); if (!t) return null;
      return `${sketch(t.pts, true)}<div class="eyebrow mt16">№${String(t.n).padStart(3, '0')} · ${dMed(t.start)} · ${tShort(t.start)} to ${tShort(t.end)}</div><h2>${esc(t.title)}</h2>
      <div class="card stat3 mt16" style="box-shadow:none;background:var(--raised)"><div><span class="eyebrow">Distance</span><span class="big">${num(t.km, 1)}</span><span class="unit" style="margin:0">km</span></div><div><span class="eyebrow">Time</span><span class="big">${t.sec < 3600 ? Math.round(t.sec / 60) : dur(t.sec)}</span><span class="unit" style="margin:0">${t.sec < 3600 ? 'min' : ''}</span></div><div><span class="eyebrow">Average</span><span class="big">${num(t.avg)}</span><span class="unit" style="margin:0">km/h</span></div></div>
      <div class="kv mt8"><span>Top speed</span><b>${num(t.top)} km/h</b></div>
      <div class="btns mt16"><button class="btn line sm" data-act="trip-gpx" data-arg="${t.id}">${icon('down')} GPX</button><button class="btn line sm" data-act="trip-rename" data-arg="${t.id}">${icon('edit')} Rename</button><button class="btn danger sm" data-act="trip-del" data-arg="${t.id}" aria-label="Delete ride" style="flex:0 0 48px;padding:0">${icon('trash')}</button></div>`;
    }
    case 'trip-rename': {
      const t = s.trips.find((x) => x.id === arg);
      return fieldForm('trip.' + arg, 'Ride', 'Name this ride', t.title, 'text', '');
    }
    case 'quick': {
      const fs = S.fuelStats(s);
      const lastFill = [...s.fuel].sort((a, b) => b.ts - a.ts)[0];
      const due = sortedService(s).filter((x) => x.st.status !== 'ok').slice(0, 2);
      const tile = (act, ic, tone, t, d, cls = '', arg2 = '') => `<button class="qtile ${cls}" data-act="${act}" ${arg2 ? `data-arg="${arg2}"` : ''}><span class="qi ${tone}">${icon(ic)}</span><span><div class="t">${t}</div><div class="d">${d}</div></span></button>`;
      return `<div class="flex between"><div><div class="eyebrow">${esc(clip(S.vehicleTitle(s)))} · ${short(s.odo)} km</div><h2>What now?</h2></div></div>
      <div class="qgrid">
        ${tile('sheet', 'fuel', 'amber', 'Log fuel', lastFill ? `Last: ${dShort(lastFill.ts)}` : 'At the pump', '', 'fuel:new')}
        ${live.rec ? tile('sheet', 'stop', '', 'End ride', `${num(live.rec.km, 1)} km so far`, 'live', 'rec-end') : tile('rec-start', 'play', 'acc', 'Record a ride', 'Uses phone GPS')}
        ${tile('ride', 'speed', 'ink', 'Ride mode', 'Big speed, next turn')}
        ${tile('park-save', 'pin', 'sage', 'Save parking', s.parking ? `Saved ${ago(s.parking.ts)}` : 'Find it later')}
      </div>
      ${due.length ? `<div class="eyebrow mt24" style="margin-bottom:10px">Due on this bike</div>${due.map(({ it, st }) => { const rv = rowValue(st); return `<div class="qdue"><span class="qi ${st.status === 'overdue' ? 'red' : 'amber'}" style="width:34px;height:34px;border-radius:12px;display:grid;place-items:center">${icon(it.icon || 'wrench').replace('<svg', '<svg style="width:17px;height:17px"')}</span><span class="tx"><div class="t">${esc(it.name)}</div><div class="d ${rv.cls || 'faint'}">${rv.v} ${rv.u}</div></span><button class="chip" data-act="svc-done" data-arg="${it.id}">${icon('check')} Done</button></div>`; }).join('')}` : ''}
      <button class="sosbar" data-act="sheet" data-arg="sos">${icon('sos')}<span>Emergency SOS <small>· ${co.amb} ambulance</small></span>${icon('chev')}</button>`;
    }
    case 'rec-end': {
      const r = live.rec; if (!r) return null;
      const sec = (Date.now() - r.start) / 1000, short = r.km < 0.1;
      return `<div class="eyebrow">${esc(clip(S.vehicleTitle(s)))} · recording</div><h2>End this ride?</h2>
      <div class="card stat3 mt16" style="box-shadow:none;background:var(--raised)"><div><span class="eyebrow">Distance</span><span class="big">${num(r.km, 1)}</span><span class="unit" style="margin:0">km</span></div><div><span class="eyebrow">Time</span><span class="big" style="font-size:26px">${clock(sec)}</span></div><div><span class="eyebrow">Top</span><span class="big">${num(r.top)}</span><span class="unit" style="margin:0">km/h</span></div></div>
      <p class="lead">${short ? 'This ride is under 100 m, so it won’t be kept.' : `Saving adds ${num(r.km, 1)} km to the odometer and both trips.`}</p>
      <button class="btn accent block mt20" data-act="rec-stop">${icon('check')} ${short ? 'End ride' : 'Save ride'}</button>
      <button class="btn line block mt8" data-act="close">Keep riding</button>
      ${short ? '' : '<button class="btn danger block mt8" data-act="rec-discard">Discard ride</button>'}`;
    }
    case 'inbox': {
      const items = inbox(c);
      return `<div class="eyebrow">${esc(clip(S.vehicleTitle(s)))}</div><h2>Notifications</h2>
      ${items.length ? `<div class="list flat mt16">${items.map((x) => li({ lead: `<span class="ic" style="background:var(--tint-${x.tone});color:var(--${x.tone === 'sage' ? 'sage-ink' : x.tone})">${icon(x.ic)}</span>`, t: x.t, d: x.d, act: x.act, arg: x.arg })).join('')}</div>` : `<div class="empty"><div class="serif">All caught up</div>Nothing needs you right now.</div>`}`;
    }
    case 'models':
      return `<div class="eyebrow">Garage</div><h2>Bike models</h2><p class="lead">Every model ${appName(s)} knows. Models without a photo show a plain tile.</p>
      <div class="models mt16">${S.MODELS.map((m, i) => `<div class="m">${vehicleArt({ settings: { art: 'photo' } }, { model: m.id, colour: S.COLOURS[i % S.COLOURS.length][0] }, 'model')}<div class="t">${esc(S.nameOfModel(s, m))}</div><div class="d">${num(m.tank, m.tank % 1 ? 1 : 0)} L · ${esc(m.series)}${S.MODEL_IMAGES[m.id] ? ' · art' : ''}</div></div>`).join('')}</div>
      <p class="hint">Artwork credits are in About Waylink, Photo credits.</p>`;
    case 'credits': {
      const rows = Object.entries(S.MODEL_IMAGES);
      return `<div class="eyebrow">About</div><h2>Photo credits</h2><p class="lead">Bike photos are used under their open licences. Tap one to see its source.</p>
      ${rows.length ? `<div class="list flat mt16">${rows.map(([k, m]) => li({ lead: vehicleArt({ settings: { art: 'photo' } }, { model: k, colour: '#888888' }), t: esc(S.MODELS.find((x) => x.id === k)?.name || k), d: esc(m.credit || 'No credit recorded'), href: m.source || '', tag: m.source ? 'a' : 'div', chev: !!m.source })).join('')}</div>` : '<div class="empty">No model photos yet.</div>'}
      <p class="hint">Add more with tools/fetch_bike_photos.py. It only uses CC0, public-domain, CC BY and CC BY-SA photos from Wikimedia Commons.</p>`;
    }
    case 'clear': {
      const wa = arg === 'clear-wa', n = wa ? s.alerts.wa.length : s.alerts.missed.length;
      return `<div class="eyebrow">Alerts</div><h2>${wa ? 'Clear WhatsApp alerts?' : 'Clear missed calls?'}</h2><p class="lead">${plural(n, wa ? 'chat' : 'call', wa ? 'chats' : 'calls')} will be removed here and the count on your bike goes to zero. Your phone keeps its own history.</p><button class="btn block mt24" style="background:var(--red);color:#fff" data-act="${arg}">Clear</button><button class="btn line block mt8" data-act="close">Keep them</button>`;
    }
    case 'reset':
      return `<div class="eyebrow">Backup</div><h2>Reset Waylink?</h2><p class="lead">Everything on this phone goes: bikes, rides, fuel, service, papers, your profile, theme and every setting. The app starts again like a fresh install. Export a backup first if you might want any of it.</p><button class="btn block mt24" style="background:var(--red);color:#fff" data-act="reset-all">Reset everything</button><button class="btn line block mt8" data-act="close">Cancel</button>`;
    case 'turn-test': {
      const sel = maneuverByKey(live.turn.maneuver), isRb = sel.glyph.startsWith('roundabout');
      const groups = [['Turns', ['TURN_LEFT', 'TURN_RIGHT', 'TURN_SLIGHT_LEFT', 'TURN_SLIGHT_RIGHT', 'TURN_SHARP_LEFT', 'TURN_SHARP_RIGHT']], ['Lanes', ['KEEP_LEFT', 'KEEP_RIGHT', 'FORK_LEFT', 'FORK_RIGHT', 'RAMP_LEFT', 'RAMP_RIGHT', 'MERGE']], ['Roundabouts & U-turns', ['ROUNDABOUT_LEFT', 'ROUNDABOUT_RIGHT', 'U_TURN_LEFT', 'U_TURN_RIGHT']], ['Other', ['STRAIGHT', 'DESTINATION_REACHED', 'FERRY', 'FERRY_TRAIN', 'WRONG_WAY']]];
      return `<div class="eyebrow">Route</div><h2>Test one turn</h2>
        ${groups.map(([g, ks]) => `<div class="ms-t mt20">${g}</div><div class="grid-man mt8">${ks.map(maneuverByKey).map((m) => `<button class="${m.key === sel.key ? 'on' : ''}" data-act="turn-pick" data-arg="${m.key}" aria-label="${m.label}" aria-pressed="${m.key === sel.key}">${arrow(m.glyph, 0, .3, 7.5)}<span>${m.label}</span></button>`).join('')}</div>`).join('')}
        <div class="card pad mt20">
          <div class="flex between"><span class="eyebrow">Distance</span><span class="mono small" data-live="turn-dist">${num(live.turn.m)} m</span></div>
          <input class="range mt12" type="range" min="0" max="3000" step="10" value="${live.turn.m}" style="--p:${live.turn.m / 30}%" data-input="turn-dist" aria-label="Distance"><div class="range-scale"><span>0</span><span>1.5 km</span><span>3 km</span></div>
          ${isRb ? `<div class="eyebrow mt16">Roundabout exit</div><div class="chips mt8">${[1, 2, 3, 4, 5, 6, 7].map((e) => `<button class="chip ${live.turn.exit === e ? 'on' : ''}" data-act="turn-exit" data-arg="${e}">${e}</button>`).join('')}</div>` : ''}
          <label class="field"><span class="eyebrow flex between">Street <span data-live="turn-count">${(live.turn.street || '').length}/31</span></span><input class="input" maxlength="31" placeholder="Outer Ring Road" value="${esc(live.turn.street)}" data-input="turn-street" autocomplete="off"></label>
        </div>
        <div class="sheet-cta"><button class="btn accent block" data-act="nav-single">Send to bike</button></div>`;
    }
    case 'pair-intro':
      return `<div class="eyebrow">Your bike</div><h2>No bike linked</h2><p class="lead">Pair once and Waylink reconnects by itself whenever the ignition is on. Turns, callers and the time then show on your display.</p>
        ${link.supported ? `<button class="btn ink block mt20" data-act="pair">${icon('bt')} Pair your bike</button><button class="btn line block mt8" data-act="sim-on">${icon('bolt')} Try the simulated bike</button>`
        : isIOS() ? `<button class="btn ink block mt20" data-act="sheet" data-arg="ios-pair">${icon('bt')} Pair on iPhone</button><button class="btn line block mt8" data-act="sim-on">${icon('bolt')} Try the simulated bike</button><p class="hint">iPhone browsers need one extra app for Bluetooth. It takes a minute.</p>`
        : `<button class="btn ink block mt20" data-act="sim-on">${icon('bolt')} Try the simulated bike</button><p class="hint">To pair a real bike, open this in Chrome on Android.</p>`}
        <button class="btn ghost block mt8" data-act="close">Not now</button>`;
    case 'auto-ask':
      return `<div class="eyebrow">Linked</div><h2>Start rides by themselves?</h2><p class="lead">Waylink can record a ride as soon as the bike links, and open ride mode once you're moving. Both are off unless you turn them on.</p>
        <div class="list mt16">${li({ ic: 'route', t: 'Record rides by themselves', d: 'When the bike links', end: sw(s.settings.autoRecord, 'toggle', 'autoRecord'), chev: false, tag: 'div' })}${li({ ic: 'speed', t: 'Open ride mode by itself', d: 'Above 15 km/h', end: sw(s.settings.autoRide, 'toggle', 'autoRide'), chev: false, tag: 'div' })}</div>
        <button class="btn ink block mt20" data-act="close">Done</button><p class="hint" style="text-align:center">You can change these later in Settings, under Riding.</p>`;
    case 'avatar': {
      const P = s.profile, cur = P.photo ? 'photo' : P.avatar || '';
      return `<div class="eyebrow">Profile</div><h2>Your picture</h2><p class="lead">Pick a rider, or add a photo.</p>
        <div class="av-grid mt16">${Array.from({ length: 11 }, (_, i) => `a${i + 1}`).map((k) => `<button class="av-opt ${cur === k ? 'on' : ''}" data-act="pick-avatar" data-arg="${k}" aria-label="Avatar ${k.slice(1)}"><img src="img/avatars/${k}.svg" alt=""></button>`).join('')}<label class="av-opt av-photo ${cur === 'photo' ? 'on' : ''}" aria-label="Use a photo">${P.photo ? `<img src="${P.photo}" alt="">` : `<span>${icon('camera')}<b>Photo</b></span>`}<input type="file" accept="image/*" hidden data-input="avatar"></label></div>`;
    }
    case 'prof': {
      const p = s.profile, co = S.country(s);
      const f = (name, label, val, type = 'text', ph = '', extra = '') => `<label class="field"><span class="eyebrow">${label}</span><input class="input" name="${name}" type="${type}" value="${esc(val ?? '')}" placeholder="${ph}" autocomplete="off" ${extra}></label>`;
      const body = arg === 'about' ? `${f('name', 'Full name', p.name, 'text', 'Arjun Rao', `maxlength="${LIMITS.person}"`)}${f('phone', 'Phone', p.phone, 'tel', co.dial + ' 98450 12345')}${f('email', 'Email', p.email, 'email', 'you@example.in')}${f('city', 'City', p.city, 'text', 'Bengaluru', `maxlength="${LIMITS.city}"`)}`
        : arg === 'licence' ? `${f('licenceNo', 'Licence number', p.licenceNo, 'text', 'KA05 20190012345', 'maxlength="20"')}${f('licenceExpiry', 'Valid till', p.licenceExpiry ? isoDate(p.licenceExpiry) : '', 'date')}`
        : `<label class="field"><span class="eyebrow">Blood group</span><select class="input" name="blood">${['', 'A+', 'A−', 'B+', 'B−', 'AB+', 'AB−', 'O+', 'O−'].map((b) => `<option ${b === p.blood ? 'selected' : ''} value="${b}">${b || 'Not set'}</option>`).join('')}</select></label><label class="field"><span class="eyebrow">Allergies and notes</span><textarea class="input" name="medical" placeholder="Allergic to penicillin">${esc(p.medical)}</textarea></label>${f('iceName', 'Emergency contact', p.iceName, 'text', 'Amma', `maxlength="${LIMITS.person}"`)}${f('icePhone', 'Their phone', p.icePhone, 'tel', co.dial + ' 98860 55443')}`;
      return `<div class="eyebrow">Profile</div><h2>${{ about: 'About you', licence: 'Driving licence', medical: 'Medical ID' }[arg] || 'Profile'}</h2><form data-form="profile" data-autosave>${body}</form><div class="sheet-cta"><button class="btn ink block" data-act="close">Done</button></div>`;
    }
    case 'erase':
      return `<div class="eyebrow">Backup</div><h2>${s.sample ? 'Erase sample data?' : 'Erase everything?'}</h2><p class="lead">Bikes, rides, fuel, service, alerts and papers on this phone will go. Export a backup first if you want to keep them.</p><button class="btn block mt24" style="background:var(--red);color:#fff" data-act="erase">Erase</button><button class="btn line block mt8" data-act="close">Keep my data</button>`;
    case 'paste':
      return `<form data-form="paste"><div class="eyebrow">Backup</div><h2>Restore from text</h2><p class="lead">Paste a backup you copied from ${appName(s)}.</p><label class="field"><textarea class="input" name="text" style="height:160px;font-family:var(--mono);font-size:12px" placeholder='{"format":"pulsarnav-vault", …}'></textarea></label><button class="btn ink block mt16" type="submit">Restore</button></form>`;
    case 'copy':
      return `<div class="eyebrow">Backup</div><h2>Your backup</h2><p class="lead">Copy it somewhere safe, or paste it into ${appName(s)} on your new phone.</p><textarea class="input mt16" readonly style="height:180px;font-family:var(--mono);font-size:11px" id="backup-text">${esc(S.exportVault(s))}</textarea><button class="btn ink block mt12" data-act="copy-text">${icon('copy')} Copy</button>`;
  }
  return null;
}
function fieldForm(key, eb, label, val, type, suffix, hint = '') {
  return `<form data-form="field" data-arg="${key}"><div class="eyebrow">${esc(eb)}</div><h2>${label}</h2>
    <label class="field"><div class="input-wrap"><input class="input" name="v" type="${type}" ${type === 'number' ? 'inputmode="decimal" step="any" min="0"' : ''} value="${esc(val)}" autofocus><span class="suffix">${suffix}</span></div></label>
    ${hint ? `<p class="hint">${hint}</p>` : ''}<button class="btn ink block mt24" type="submit">Save</button></form>`;
}

// ---------- Onboarding ----------
// Onboarding: a short intro, then the rider and bike, then linking the bike. Every step can be skipped.
// The intro is one story told along a single road: dawn, the ride, speed, a pit stop, night.
// The story is one painted picture, made by tools/onb_scene.py; phones only slide it.
const SCENE = '<div class="onb-scene onb-img" aria-hidden="true"></div>';
const ONB_SLIDES = [
  ['Navigate', 'Turns on your display', 'Plan in Google Maps. Arrows show on the cluster.'],
  ['Stay reachable', 'Calls, without your phone', 'Caller names and missed calls, on the bike.'],
  ['Ride', 'Everything at a glance', 'Speed, time, fuel and the next turn.'],
  ['Look after it', 'Service and fuel, sorted', 'A nudge before anything is due.'],
  ['Yours alone', 'Private by design', 'No account, no cloud. It stays on this phone.'],
];
export function onboarding(s, step = 0, link = {}) {
  const sample = '<button class="link onb-sample" type="button" data-act="onboard-sample">Explore with sample data</button>';
  if (step === 0) return `<div class="onb onb-intro">
    <div class="onb-top"><span class="onb-mark">${LOOP}</span><button class="link" data-act="onb-step" data-arg="1">Skip</button></div>
    <div class="onb-stage" data-onb-stage>${SCENE}</div>
    <div class="onb-slides" data-onb-slides>${ONB_SLIDES.map(([e, t, d]) => `<section class="onb-slide"><div class="eyebrow onb-eb">${e}</div><h1 class="title">${t}</h1><p class="onb-p">${d}</p></section>`).join('')}</div>
    <div class="onb-dots">${ONB_SLIDES.map((_, i) => `<button class="${i ? '' : 'on'}" data-act="onb-go" data-arg="${i}" aria-label="Slide ${i + 1}"><i></i></button>`).join('')}</div>
    <div class="onb-foot"><button class="btn ink block" data-act="onb-next">Next</button>${sample}</div>
  </div>`;
  if (step === 2) {
    const pair = link.supported ? `<button class="btn ink block" data-act="onb-finish" data-arg="pair">${icon('bt')} Pair your bike</button>`
      : isIOS() ? `<button class="btn ink block" data-act="onb-finish" data-arg="ios">${icon('bt')} Pair on iPhone</button>` : '';
    return `<div class="onb onb-link">
      <div class="onb-top"><button class="circle" data-act="onb-step" data-arg="1" aria-label="Back">${icon('back')}</button><button class="link" data-act="onb-finish" data-arg="later">Skip</button></div>
      <div class="onb-body"><div class="onb-stage still" style="--p:2">${SCENE}<img class="link-rider" src="img/rider.png" alt=""><svg class="link-wave" viewBox="0 0 120 30" aria-hidden="true"><path d="M2 15C12 3 22 3 32 15S52 27 62 15S82 3 92 15S112 27 118 15"/></svg><span class="link-badge">${icon('bt')}</span></div><div class="eyebrow mt24">Step 2 of 2</div><h1 class="title">Link your bike</h1><p class="onb-p">Pair once with the ignition on. It reconnects by itself after that.</p>
      <div class="onb-gets2">${[['turnRight', 'Turns'], ['phone', 'Callers'], ['clock', 'Time'], ['fuel', 'Fuel']].map(([ic, t]) => `<span>${icon(ic)}${t}</span>`).join('')}</div></div>
      <div class="onb-foot">${pair}<button class="btn ${pair ? 'line' : 'ink'} block mt8" data-act="onb-finish" data-arg="sim">${icon('bolt')} Try the simulated bike</button><button class="link onb-sample" data-act="onb-finish" data-arg="later">I’ll do this later</button></div>
    </div>`;
  }
  return `<div class="onb onb-reg">
  <div class="onb-top"><button class="circle" data-act="onb-step" data-arg="0" aria-label="Back">${icon('back')}</button><button class="link" data-act="onb-skip-reg">Skip</button></div>
  <div class="eyebrow">Step 1 of 2</div>
  <h1 class="title">About you and your bike</h1>
  <p class="onb-p">So reminders and the display fit your bike.</p>
  <form data-form="onboard" class="mt16">
    <label class="field"><span class="eyebrow">Your name</span><input class="input" name="name" placeholder="Arjun" maxlength="${LIMITS.person}" autocomplete="given-name"></label>
    <label class="field"><span class="eyebrow">Your bike</span><select class="input" name="model">${S.MODELS.map((m) => `<option value="${m.id}">${m.name}</option>`).join('')}</select></label>
    <div class="two"><label class="field"><span class="eyebrow">Number plate</span><input class="input" name="plate" placeholder="KA 05 MX 2481" maxlength="${LIMITS.plate}" style="text-transform:uppercase"></label>
    <label class="field"><span class="eyebrow">Odometer</span><div class="input-wrap"><input class="input" name="odo" type="number" inputmode="numeric" min="0" placeholder="0"><span class="suffix">km</span></div></label></div>
    <label class="field"><span class="eyebrow">Country</span><select class="input" name="country">${S.COUNTRIES.map((c) => `<option value="${c.code}">${c.name}</option>`).join('')}</select></label>
    <button class="btn ink block mt24" type="submit">Continue</button>
    ${sample}
  </form>
    </div>`;
}
