// Waylink web: the desktop screens. State in, HTML out, built from the same pieces as the phone app.
import * as S from './store.js';
import * as V from './views.js';

const { esc, num, short, clip, icon, dMed, dShort, tShort, dur, ago, sh, plural, li, sw } = V;

export const PAGES = [
  ['overview', 'Overview', 'today'],
  ['rides', 'Rides', 'trips'],
  ['service', 'Service', 'wrench'],
  ['fuel', 'Fuel', 'fuel'],
  ['settings', 'Settings', 'settings'],
];

// ---------- Shell: one floating toolbar (top on a computer, bottom on a phone-sized window) ----------
export function toolbar(c, page) {
  const { s } = c;
  const v = S.veh(s);
  const due = S.needsYou(s);
  const fs = S.fuelStats(s);
  const pip = { overview: due || fs.onReserve, service: due, fuel: fs.onReserve };
  const sy = syncState(c);
  return `<a class="tb-brand" href="#overview" aria-label="Waylink, overview">${V.LOOP}</a>
  <nav class="tb-nav" aria-label="Main">${PAGES.map(([k, l, ic]) => `<a class="tb-i ${page === k ? 'on' : ''}" href="#${k}" ${page === k ? 'aria-current="page"' : ''} title="${l}">${icon(ic)}<span class="tb-l">${l}</span>${pip[k] ? '<span class="pip" aria-label="needs you"></span>' : ''}</a>`).join('')}</nav>
  <div class="tb-end">
    <button class="tb-bike" data-act="pop" data-arg="bike" aria-haspopup="dialog" aria-label="Bike: ${esc(S.vehicleTitle(s))}">${V.vehicleArt(s, v, 'thumb')}<span class="tb-bt">${esc(clip(S.vehicleTitle(s), 16))}</span>${icon('updown', 'tb-chev')}</button>
    <button class="tb-sync" data-act="pop" data-arg="sync" aria-haspopup="dialog" aria-label="${sy.t}. ${sy.d}" title="${sy.t}"><span class="dot ${sy.tone}"></span><span class="tb-st">${sy.short}</span></button>
  </div>`;
}
// Where the data came from and how fresh it is, always in view so nobody mistakes old numbers for new.
function syncState(c) {
  const { link } = c;
  const n = link.pending.length;
  const dev = esc(link.info?.device || 'your phone');
  const when = link.updated ? ago(link.updated) : '';
  const stale = link.updated && Date.now() - link.updated > 6 * 3600e3;
  const tone = !link.online ? 'k' : n || stale ? 'a' : '';
  const t = !link.online ? 'Offline' : n ? `${plural(n, 'change', 'changes')} waiting` : `Synced ${when}`;
  const short = !link.online ? 'Offline' : n ? `${n} waiting` : when ? when.replace(' ago', '') : 'Synced';
  const d = !link.online ? `Showing data from ${when || 'earlier'}` : n ? `Saved. ${dev} picks ${n === 1 ? 'it' : 'them'} up when it’s online.` : stale ? `Open Waylink on ${dev} to refresh` : `From ${dev}`;
  return { tone, t, d, short, dev };
}

// ---------- Popovers ----------
export function popover(c, key) {
  const { s, link } = c;
  if (key === 'bike') {
    const cur = S.veh(s);
    return `<div class="pop-h">Your bikes</div>
    <div class="pop-list" role="listbox" aria-label="Show bike">${s.vehicles.map((v) => {
      const on = v.id === cur.id;
      const title = v.nickname || S.bikeName({ ...s, active: v.id });
      return `<button class="pop-bike ${on ? 'on' : ''}" role="option" aria-selected="${on}" data-act="${on ? 'close-pop' : 'switch-vehicle'}" data-arg="${v.id}">${V.vehicleArt(s, v, 'thumb')}<span class="pb-tx"><b>${esc(clip(title, 22))}</b><span>${num(Math.floor(v.odo))} km${v.plate ? ' · ' + esc(v.plate) : ''}</span></span>${on ? icon('check', 'pb-ok') : ''}</button>`; }).join('')}</div>
    <div class="pop-foot"><button class="pop-a" data-act="sheet" data-arg="vehicle:${cur.id}">${icon('edit')}Edit this bike</button><button class="pop-a" data-act="sheet" data-arg="specs">${icon('spec')}Spec sheet</button></div>`;
  }
  if (key === 'sync') {
    const sy = syncState(c);
    return `<div class="pop-sync"><span class="dot ${sy.tone}"></span><div><b>${sy.t}</b><p>${sy.d}</p></div></div>
    <div class="pop-kv"><span>Phone</span><b>${sy.dev}</b></div>
    <div class="pop-kv"><span>Linked</span><b>${link.info?.linkedAt ? V.dFull(link.info.linkedAt) : '—'}</b></div>
    <div class="pop-kv"><span>Lock</span><b>End-to-end encrypted</b></div>
    <div class="pop-foot"><button class="pop-a" data-act="sync-now">${icon('refresh')}Sync now</button><button class="pop-a" data-act="prefs" data-arg="phone">${icon('settings')}Link settings</button></div>`;
  }
  return null;
}
const head = (title, sub, actions = '') => `<header class="whead"><div class="wh-tx"><h1 class="title">${title}</h1><div class="subtitle">${sub}</div></div>${actions ? `<div class="wh-act">${actions}</div>` : ''}</header>`;
const btn = (label, act, arg = '', cls = 'ink', ic = '') => `<button class="btn ${cls} sm" data-act="${act}" data-arg="${esc(arg)}">${ic ? icon(ic) : ''}${label}</button>`;

// ---------- Overview ----------
export function overview(c) {
  const { s } = c;
  const list = V.sortedService(s);
  const v = V.verdictOf(s, list);
  const fs = S.fuelStats(s);
  const urgent = list.filter((x) => x.st.status !== 'ok');
  const show = (urgent.length ? urgent : list).slice(0, 5);
  const wk = S.periodStats(s.trips, S.weekStart());
  const cur = S.country(s).cur;
  const spent30 = fs.last30.cost + s.records.filter((r) => r.date > Date.now() - 30 * 86400000).reduce((a, r) => a + (+r.cost || 0), 0);
  let line = v.line;
  if (fs.onReserve) line = `On reserve, about ${num(fs.range)} km left.` + (v.over || v.soon ? ' ' + line : '');
  const stat = (lab, val, unit, sub, to) => `<a class="ov-stat" href="#${to}"><span class="eyebrow">${lab}</span><span class="ov-n">${val}${unit ? `<span class="unit">${unit}</span>` : ''}</span><span class="ov-sub">${sub}</span></a>`;
  const trips = [...s.trips].sort((a, b) => b.start - a.start).slice(0, 4);
  return `${head(new Date().toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'long' }), s.sample ? '<span class="demo-pill">Demo data<i></i></span>' : '')}
  <section class="ov-band">
    <a class="ov-verdict ${fs.onReserve && !v.over ? 'soon' : v.cls}" href="#service">
      <span class="eyebrow">Verdict</span><span class="verdict">${v.word}</span><p>${line}</p>
      ${list.length ? V.heroBar(v.bar) + V.caption(v) : '<span class="eyebrow caption">No reminders yet</span>'}
    </a>
    <div class="ov-stats">
      ${stat('Odometer', V.big(Math.floor(s.odo)), 'km', `Trip A ${V.tripv(s.tripA.km)} km`, 'service')}
      ${stat('Range', fs.range != null ? '≈' + num(fs.range) : '—', 'km', fs.avg != null ? `${num(fs.avg, 1)} km/L lately` : 'Log a full tank', 'fuel')}
      ${stat('This week', num(wk.km, wk.km < 100 ? 1 : 0), 'km', plural(wk.rides, 'ride', 'rides'), 'rides')}
      ${stat('Spent, 30 days', cur + num(spent30), '', 'Fuel and service', 'fuel')}
    </div>
  </section>
  <div class="wgrid ov-cols">
    <section class="col-7">${sh(urgent.length ? 'Needs you' : 'Coming up', '<a class="link" href="#service">All jobs</a>')}
      ${show.length ? `<div class="rows">${show.map(({ it, st }) => V.serviceRow(it, st)).join('')}</div>` : '<div class="card empty">No service reminders yet. Add them on the Service page.</div>'}</section>
    <section class="col-5">${sh('Latest rides', '<a class="link" href="#rides">All rides</a>')}
      ${trips.length ? `<div class="card ov-rides">${trips.map(rideRow).join('')}</div>` : '<div class="card empty">Rides you record on the phone show up here.</div>'}</section>
  </div>`;
}
function statBlock(lab, p) {
  return `<div><span class="eyebrow">${lab}</span><span class="big">${num(p.km, p.km < 100 ? 1 : 0)}<span class="unit">km</span></span><div class="small muted">${plural(p.rides, 'ride', 'rides')} · ${dur(p.sec)}</div>${p.top ? `<div class="small faint">top ${num(p.top)} km/h</div>` : ''}</div>`;
}
const rideRow = (t) => `<button class="ride" data-act="sheet" data-arg="trip-detail:${t.id}">${V.sketch(t.pts)}<div class="grow"><div class="when">${dMed(t.start)} · ${tShort(t.start)}</div><div class="t">${esc(t.title)}</div><div class="meta"><span><span class="big">${num(t.km, 1)}</span><span class="unit">km</span></span><span>${dur(t.sec)}</span><span>top ${num(t.top)}</span></div></div><span class="no">№${String(t.n).padStart(3, '0')}</span></button>`;

// ---------- Rides ----------
export function rides(c) {
  const { s } = c;
  const wk = S.periodStats(s.trips, S.weekStart());
  const mo = S.periodStats(s.trips, Date.now() - 30 * 86400000);
  const all = S.periodStats(s.trips, 0);
  const tot = s.trips.reduce((a, t) => a + (t.km || 0), 0);
  const trips = [...s.trips].sort((a, b) => b.start - a.start);
  const at = V.rideChartAt(s);
  return `${head('Rides', `${plural(s.trips.length, 'ride', 'rides')}, recorded on the phone`)}
  <div class="wgrid">
    <div class="col-7"><div class="card pad rides-hero flush">
      <div class="flex between"><div class="ms-t">All rides</div><div class="seg rc-tabs" role="tablist">${V.RC_NAMES.map((n, i) => `<button role="tab" class="${i === at ? 'on' : ''}" aria-selected="${i === at}" data-act="ride-chart" data-arg="${i}">${n.replace('Km ', '')}</button>`).join('')}</div></div>
      <div class="big rh-n">${s.trips.length}</div>
      <p class="rh-line">${s.trips.length ? `You’ve ridden <u>${num(tot, tot < 100 ? 1 : 0)} km</u> on ${esc(clip(S.vehicleTitle(s), 20))}${s.trips.length > 1 ? `, about ${num(tot / s.trips.length, 0)} km a ride` : ''}.` : 'Rides you record on the phone fill this in, one square a day.'}</p>
      ${V.rideCharts(s)}</div></div>
    <div class="col-5">
      <div class="card stat2 flush">${statBlock('This week', wk)}${statBlock('Last 30 days', mo)}</div>
      <div class="card mt12" style="padding:2px 20px">
        <div class="kv"><span>Longest ride</span><b>${s.trips.length ? num(Math.max(...s.trips.map((t) => t.km || 0)), 1) + ' km' : '—'}</b></div>
        <div class="kv"><span>Top speed</span><b>${all.top ? num(all.top) + ' km/h' : '—'}</b></div>
        <div class="kv"><span>Time riding</span><b>${all.sec ? dur(all.sec) : '—'}</b></div>
        <div class="kv"><span>Average ride</span><b>${s.trips.length ? num(tot / s.trips.length, 1) + ' km' : '—'}</b></div>
      </div>
      <p class="hint" style="padding:0 20px">Rides are recorded on the phone. Here you can rename them, export a GPX file or delete them.</p>
    </div>
  </div>
  <div class="section">${sh('Every ride', '<span class="eyebrow">Newest first</span>')}
    ${trips.length ? `<div class="card ride-list" style="padding:4px">${trips.map(rideRow).join('')}</div>` : '<div class="card empty"><div class="serif">No rides yet</div>Your first ride shows up here, route and all.</div>'}</div>`;
}

// ---------- Service ----------
export function service(c) {
  const { s } = c;
  const list = V.sortedService(s);
  const v = V.verdictOf(s, list);
  const cur = S.country(s).cur;
  const spent = s.records.reduce((a, r) => a + (+r.cost || 0), 0);
  return `${head('Service', 'Reminders, workshop visits and notes', `${btn('Log visit', 'sheet', 'rec:new', 'line', 'plus')}${btn('Add reminder', 'sheet', 'svc-edit:new', 'ink', 'plus')}`)}
  <div class="wgrid">
    <div class="col-7">
      <div class="hero ${v.cls} flush"><div class="eyebrow">Verdict</div><div class="verdict">${v.word}</div><p>${v.line}</p>${list.length ? (v.over || v.soon ? V.heroBar(v.bar) + V.caption(v) : `<div class="eyebrow caption">All ${v.ok} jobs on track</div>`) : '<div class="eyebrow caption">No reminders yet</div>'}</div>
      <div class="section">${sh('Reminders · most urgent first', '', 'reminders')}
        <div class="rows" id="svc-rows">${list.map(({ it, st }) => V.serviceRow(it, st)).join('') || '<div class="empty">No reminders yet. Add one above.</div>'}</div></div>
    </div>
    <div class="col-5">
      <button class="card pad flex between flush" style="width:100%" data-act="sheet" data-arg="odo"><span class="eyebrow">Odometer now</span><span class="flex gap8"><span><span class="big" style="font-size:30px">${num(Math.floor(s.odo))}</span><span class="unit">km</span></span>${icon('edit', 'faint')}</span></button>
      <div class="section">${sh('Workshop visits', '<button class="link" data-act="sheet" data-arg="rec:new">Add visit</button>', 'visits')}
        ${s.records.length ? `<div class="list">${[...s.records].sort((a, b) => b.date - a.date).map((r) => `<button class="rec-row" data-act="sheet" data-arg="rec:${r.id}"><span class="rec-ic">${icon('wrench')}</span><span class="rec-t">${esc(r.title)}</span><span class="rec-c">${r.cost ? cur + short(r.cost) : ''}</span><span class="rec-d">${dShort(r.date)} · ${short(r.odo)} km${r.workshop ? ' · ' + esc(clip(r.workshop, 22)) : ''}</span></button>`).join('')}</div>
        <div class="flex between mt12 small muted" style="padding:0 20px"><span>Spent on service</span><b style="font-weight:500;color:var(--ink)">${cur}${num(spent)}</b></div>` : '<div class="card empty">Log each visit. A full history helps at resale.</div>'}</div>
      <div class="section">${sh('For the mechanic', `<span class="eyebrow">${s.checklist.filter((x) => !x.done).length} open</span>`, 'mechanic')}
        <div class="list">
          ${s.checklist.map((x) => `<div class="li noicon"><button class="check ${x.done ? 'on' : ''}" data-act="check" data-arg="${x.id}" aria-label="${x.done ? 'Mark not done' : 'Mark done'}" aria-pressed="${x.done}">${x.done ? icon('check') : ''}</button><span class="tx"><div class="t" style="${x.done ? 'text-decoration:line-through;color:var(--ink-3)' : ''}">${esc(x.text)}</div></span><button class="icon-btn" data-act="check-del" data-arg="${x.id}" aria-label="Remove">${icon('x')}</button></div>`).join('')}
          <form class="li noicon" data-form="check-add"><span class="check" style="box-shadow:inset 0 0 0 1.5px var(--ink-4);opacity:.6"></span><input class="grow" name="text" placeholder="A rattle, a squeak, a part to fix…" aria-label="Add a note for the mechanic" maxlength="${V.LIMITS.title}" style="border:0;background:none;outline:none;font-size:15.5px;height:28px" autocomplete="off"></form>
        </div></div>
    </div>
  </div>`;
}

// ---------- Fuel ----------
export function fuel(c) {
  const { s } = c;
  const fs = S.fuelStats(s);
  const cur = S.country(s).cur;
  const hasRange = fs.range != null;
  const entries = [...s.fuel].sort((a, b) => b.odo - a.odo || b.ts - a.ts);
  const kmplById = Object.fromEntries(fs.tanks.map((t) => [t.id, t.kmpl]));
  const tankL = num(fs.tank, fs.tank % 1 ? 1 : 0);
  const three = fs.tanks.length >= 3 ? 'three' : String(fs.tanks.length);
  return `${head('Fuel', 'Range, mileage and what you spend', btn('Log fuel', 'sheet', 'fuel:new', 'ink', 'fuel'))}
  <div class="wgrid">
    <div class="col-7">
      <div class="hero ${!hasRange ? 'off' : fs.onReserve ? 'bad' : ''} flush">
        <div class="eyebrow">Range left</div>
        <div class="verdict">${hasRange ? `≈${num(fs.range)}<span class="unit" style="font-size:16px">km</span>` : '—'}</div>
        <p>${hasRange ? `Based on your last ${three} full ${fs.tanks.length === 1 ? 'tank' : 'tanks'}: ${num(fs.avg, 1)} km/L.${fs.onReserve ? ' You’re on reserve.' : ''}` : s.fuel.length ? 'Fill to the brim once more to see your range.' : 'Fill to the brim and log it. Range appears after the next full tank.'}</p>
        ${V.segs(6, fs.bars ?? 0, fs.onReserve ? 'r' : 'k', 'thick')}
        <div class="eyebrow caption">${fs.litresLeft != null ? `≈${num(fs.litresLeft, 1)} of ${tankL} L` : `${tankL} L tank · ${num(fs.reserve, 1)} L reserve`}${fs.sinceFull != null ? ` · ${num(fs.sinceFull)} km since full` : ''}</div>
      </div>
      <div class="section">${sh('Mileage', '<span class="eyebrow">Full tank to full tank</span>', 'mileage')}
        ${V.mileageScale(fs)}
        <div class="card stat3 mt12">
          <div><span class="eyebrow">Average</span><span class="big">${num(fs.avg, 1)}</span><span class="unit" style="margin:0">km/L, last 3</span></div>
          <div><span class="eyebrow">Last tank</span><span class="big">${num(fs.lastTank, 1)}</span><span class="unit" style="margin:0">km/L</span></div>
          <div><span class="eyebrow">Lifetime</span><span class="big">${num(fs.lifetime, 1)}</span><span class="unit" style="margin:0">${plural(fs.tanks.length, 'tank', 'tanks')}</span></div>
        </div>
        ${fs.tanks.length >= 2 ? `<div class="card chart mt12" id="mileage-chart">${V.mileageChart(fs)}<div class="tip"></div></div>` : ''}
        <p class="hint" style="padding:0 20px;margin-top:12px">Mileage counts only between full tanks. Top-ups roll into the next full one.</p></div>
    </div>
    <div class="col-5">
      <div class="card in-h flush" style="padding:2px 20px">${sh('Cost', '', 'cost')}
          <div class="kv"><span>Cost per km</span><b>${fs.costPerKm != null ? cur + num(fs.costPerKm, 2) : '—'}</b></div>
          <div class="kv"><span>Last 30 days</span><b>${plural(fs.last30.count, 'fill', 'fills')} · ${num(fs.last30.litres, 1)} L · ${cur}${num(fs.last30.cost)}</b></div>
          <div class="kv"><span>All fuel</span><b>${cur}${num(fs.totalSpent)}</b></div>
          <button class="kv" data-act="sheet" data-arg="fuelprice"><span>Price per litre</span><b class="flex gap8">${cur}${num(S.fuelPriceOf(s), 2)}${icon('edit', 'faint')}</b></button>
      </div>
      <div class="section">${sh('Fill-ups', `<span class="eyebrow">${entries.length}</span>`)}
        ${entries.length ? `<div class="rows">${entries.map((e) => `<button class="row" data-act="sheet" data-arg="fuel:${e.id}"><div class="row-top"><div><div class="row-title">${dMed(e.ts)}</div><div class="row-sub">${e.full ? 'Full' : 'Top-up'} · ${num(e.odo)} km · ${cur}${num(e.cost)}</div></div><div class="row-val">${kmplById[e.id] ? `<span class="big">${num(kmplById[e.id], 1)}</span><span class="unit">km/L</span>` : `<span class="big">${num(e.litres, 1)}</span><span class="unit">L</span>`}</div></div></button>`).join('')}</div>` : '<div class="card empty"><div class="serif">No fill-ups yet</div>Log one here or at the pump on your phone.</div>'}</div>
    </div>
  </div>`;
}

// ---------- Settings: its own page, sections on the left ----------
export function settings(c) { return `${head('Settings', 'Your linked phone, garage and how Waylink looks')}${prefs(c, c.prefTab)}`; }
export const PREF_TABS = [['phone', 'Linked phone', 'phone'], ['garage', 'Garage', 'garage'], ['look', 'Look', 'palette'], ['about', 'About', 'info']];
export function prefs(c, tab) {
  const t = PREF_TABS.some(([k]) => k === tab) ? tab : 'phone';
  return `<div class="prefs">
    <nav class="pf-nav" aria-label="Settings sections"><div class="pf-title">Settings</div>${PREF_TABS.map(([k, l, ic]) => `<button class="pf-i ${k === t ? 'on' : ''}" data-act="prefs-tab" data-arg="${k}" ${k === t ? 'aria-current="true"' : ''}>${icon(ic)}<span>${l}</span></button>`).join('')}</nav>
    <div class="pf-pane" data-pane="${t}">${PANES[t](c)}</div>
  </div>`;
}
const paneHead = (t, d) => `<div class="pf-h"><h3>${t}</h3><p>${d}</p></div>`;
const PANES = {
  phone(c) {
    const { link } = c;
    return `${paneHead('Linked phone', 'Your phone holds the data. This computer shows it and sends your changes back.')}
    <div class="pf-card pf-phone"><span class="lk-ic">${icon('phone')}</span><div class="grow"><b>${esc(link.info?.device || 'Your phone')}</b><span>Linked ${link.info?.linkedAt ? V.dFull(link.info.linkedAt) : ''} · end-to-end encrypted</span></div><button class="btn line sm" data-act="sync-now">${icon('refresh')}Sync now</button></div>
    <div class="pf-group">
      <div class="pf-row"><div><b>Last copy from the phone</b><span>${link.updated ? ago(link.updated) : 'Waiting for the first one'}</span></div></div>
      <div class="pf-row"><div><b>Download a backup</b><span>The same file the phone’s backup makes</span></div><button class="btn line sm" data-act="export">${icon('down')}Download</button></div>
      <div class="pf-row"><div><b>How linking works</b><span>What the phone sends and who can read it</span></div><button class="btn line sm" data-act="sheet" data-arg="how">Read</button></div>
    </div>
    <div class="pf-group pf-danger">
      <div class="pf-row"><div><b>Unlink this computer</b><span>Removes your data from this browser. Your phone keeps everything.</span></div><button class="btn sm pf-red" data-act="sheet" data-arg="unlink">Unlink…</button></div>
    </div>`;
  },
  garage(c) {
    const { s } = c;
    const v = S.veh(s);
    const co = S.country(s);
    return `${paneHead('Garage', s.vehicles.length > 1 ? `${s.vehicles.length} bikes. Switch from the toolbar.` : 'Your bike, as the phone has it.')}
    <div class="pf-card pf-bike">${V.vehicleArt(s, v, 'card')}<div class="grow"><b class="serif">${esc(S.vehicleTitle(s))}</b><span>${v.nickname ? esc(S.bikeName(s)) + ' · ' : ''}${esc(v.plate || 'No plate')}</span><span class="eyebrow">${num(Math.floor(s.odo))} km</span></div><button class="btn line sm" data-act="sheet" data-arg="vehicle:${v.id}">${icon('edit')}Edit</button></div>
    <div class="pf-group">
      <div class="pf-row"><div><b>Odometer</b><span>Correct it if it drifts</span></div><button class="btn line sm" data-act="sheet" data-arg="odo">${num(Math.floor(s.odo))} km</button></div>
      <div class="pf-row"><div><b>Spec sheet</b><span>Oil, tyres, chain, plugs</span></div><button class="btn line sm" data-act="sheet" data-arg="specs">Open</button></div>
      <div class="pf-row"><div><b>Country</b><span>Currency and fuel price</span></div><button class="btn line sm" data-act="sheet" data-arg="pick:country">${esc(co.name)}</button></div>
    </div>
    <p class="pf-note">Adding or removing a bike, and its photo, happen on the phone.</p>`;
  },
  look(c) {
    const { s, look } = c;
    const sup = !!look.supporter;
    const auto = look.theme === 'auto';
    const pair = sup ? ['sketch', 'sketchdark'] : ['paper', 'obsidian'];
    const sysDark = matchMedia('(prefers-color-scheme: dark)').matches;
    const shown = V.THEMES.filter(([k]) => k !== 'auto');
    const prog = V.PROGRESS_STYLES;
    return `${paneHead('Look', 'Starts from your phone’s look and is kept for this computer only.')}
    <div class="pf-group">
      <div class="pf-row"><div><b>Match the computer</b><span>${!auto ? 'Off. Pick a theme below' : sup ? 'Paper by day, Paper dark at night' : 'Light by day, Dark at night'}</span></div>${sw(auto, 'match-system')}</div>
      <div class="pf-row col"><b>Theme</b>
        <div class="pf-themes" role="radiogroup" aria-label="Theme">${shown.map(([k, l, bg, card, ink]) => {
          const locked = (k === 'sketch' || k === 'sketchdark') && !sup;
          const on = auto ? k === (sysDark ? pair[1] : pair[0]) : look.theme === k;
          const dim = auto && !pair.includes(k);
          return `<button class="pf-theme ${on ? 'on' : ''} ${dim ? 'dim' : ''}" role="radio" aria-checked="${on}" data-act="theme" data-arg="${k}" ${locked ? 'disabled title="Paper themes come with supporting Waylink on the phone"' : ''}><span class="pt-sw" style="--tbg:${bg};--tcard:${card};--tink:${ink}"><i></i><i></i>${locked ? icon('lock') : ''}</span><span>${l}</span></button>`; }).join('')}</div></div>
      <div class="pf-row col"><b>Accent</b>
        <div class="accents pf-acc">${V.ACCENTS.map(([k, col, l]) => `<button class="accentopt ${look.accent === k ? 'on' : ''}" data-act="accent" data-arg="${k}" aria-pressed="${look.accent === k}" aria-label="${l}" title="${l}"><span class="dot" style="background-color:${col};--c:${col}"></span></button>`).join('')}</div></div>
      <div class="pf-row col"><b>Progress style</b>
        <div class="seg pf-seg" role="radiogroup" aria-label="Progress style">${prog.map(([k, l]) => `<button role="radio" aria-checked="${look.progress === k}" class="${look.progress === k ? 'on' : ''}" data-act="progress-style" data-arg="${k}">${l}</button>`).join('')}</div></div>
    </div>`;
  },
  about() {
    return `${paneHead('About', 'Waylink for desktop shows what your phone records, on a bigger screen.')}
    <div class="pf-card pf-about">${V.LOOP}<div><b>Waylink for desktop</b><span>Version ${WEB_VERSION}</span></div></div>
    <div class="pf-group">
      <div class="pf-row"><div><b>How linking works</b><span>What the phone sends and who can read it</span></div><button class="btn line sm" data-act="sheet" data-arg="how">Read</button></div>
      <div class="pf-row"><div><b>Get the Android app</b><span>Rides, turns and call alerts live on the phone</span></div><a class="btn line sm" href="https://github.com/krishnaar21/Waylink/releases/tag/android-preview" target="_blank" rel="noopener">Download</a></div>
      <div class="pf-row"><div><b>Source code</b><span>Open on GitHub</span></div><a class="btn line sm" href="https://github.com/krishnaar21/Waylink" target="_blank" rel="noopener">GitHub</a></div>
    </div>`;
  },
};
export const WEB_VERSION = '0.1';

// ---------- Sheets only the web has ----------
export function webSheet(c, key) {
  const { link } = c;
  if (key === 'unlink') return `<div class="eyebrow">This computer</div><h2>Unlink this computer?</h2><p class="lead">Your data leaves this browser and the relay. Your phone keeps everything. To come back, scan a new code.</p>${link.pending.length ? `<p class="hint" style="color:var(--red)">${plural(link.pending.length, 'change hasn’t', 'changes haven’t')} reached your phone yet and will be lost.</p>` : ''}<button class="btn block mt24" style="background:var(--red);color:#fff" data-act="unlink">Unlink</button><button class="btn line block mt8" data-act="close">Keep it linked</button>`;
  if (key === 'how') return `<div class="eyebrow">Privacy</div><h2>How linking works</h2>
    <p class="lead">When you scan the code, your phone and this browser agree on a key that never leaves them. Your phone locks a copy of your data with it and sends it through a small relay. The relay can store and pass it on, but can’t open it.</p>
    <div class="card mt16" style="padding:2px 18px;box-shadow:none;background:var(--raised)">
      <div class="kv"><span class="muted">Sent</span><b>Bikes, service, fuel, rides, profile name</b></div>
      <div class="kv"><span class="muted">Never sent</span><b>Medical ID, documents, call alerts</b></div>
      <div class="kv"><span class="muted">Locked with</span><b>AES-256, key from the QR scan</b></div>
      <div class="kv"><span class="muted">Kept on the relay</span><b>Until you unlink, or 120 quiet days</b></div>
    </div>
    <p class="hint">Changes made here wait, locked, until your phone is online. Your phone stays the source of truth.</p>`;
  return null;
}

// ---------- Link screen: the only way in ----------
export function gate(c, qr) {
  const { link } = c;
  const exp = link.error === 'expired';
  const err = link.error && !exp ? `<p class="gt-err" role="alert">${esc(link.error)}</p>` : '';
  return `<div class="gate">
    <div class="gt-card">
      <div class="gt-l">
        <div class="gt-brand">${V.LOOP}</div>
        <h1 class="title">Your bike, on a bigger screen</h1>
        <p class="lead">Rides, service, fuel and costs from the Waylink app. Scan once and it stays linked.</p>
        <ol class="gt-steps">
          <li><i>1</i><span>Open <b>Waylink</b> on your phone</span></li>
          <li><i>2</i><span>Go to <b>Settings</b>, then <b>Linked computers</b></span></li>
          <li><i>3</i><span>Tap <b>Link a computer</b> and point your phone at this code</span></li>
        </ol>
        ${err}
        <p class="gt-note">${icon('shield')}<span>End-to-end encrypted. Only your phone and this browser can read your data.</span></p>
      </div>
      <div class="gt-r">
        <div class="gt-qr ${exp ? 'exp' : ''} ${qr ? '' : 'wait'}" aria-label="Link code for the Waylink app">${qr || V.loader('Making a code')}
          ${exp ? `<button class="gt-new" data-act="new-code">${icon('refresh')}<span>Show a new code</span></button>` : ''}</div>
        <div class="gt-cap">${!link.online ? 'Can’t reach Waylink. Check your connection.' : exp ? 'This code expired.' : 'The code changes every 10 minutes.'}</div>
      </div>
    </div>
    <p class="gt-foot">No app yet? <a href="https://github.com/krishnaar21/Waylink/releases/tag/android-preview" target="_blank" rel="noopener">Get Waylink for Android</a></p>
  </div>`;
}
