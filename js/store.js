// Waylink — on-device data: catalog, state, and the maths behind fuel, logbook and trips.
// Everything stays in this browser (localStorage + IndexedDB). No servers, no analytics.

export const COUNTRIES = [
  { code: 'IN', name: 'India', dial: '+91', sos: '112', amb: '108', police: '100', rsa: '1033', cur: '₹', curCode: 'INR', fuel: 102.9, locale: 'en-IN' },
  { code: 'CO', name: 'Colombia', dial: '+57', sos: '123', amb: '132', police: '112', rsa: '#767', cur: '$', curCode: 'COP', fuel: 4150, rouser: true, locale: 'es-CO' },
  { code: 'MX', name: 'Mexico', dial: '+52', sos: '911', amb: '065', police: '911', rsa: '078', cur: '$', curCode: 'MXN', fuel: 24.5, rouser: true, locale: 'es-MX' },
  { code: 'AR', name: 'Argentina', dial: '+54', sos: '911', amb: '107', police: '911', rsa: '0800-888-2677', cur: '$', curCode: 'ARS', fuel: 1180, rouser: true, locale: 'es-AR' },
  { code: 'PE', name: 'Peru', dial: '+51', sos: '911', amb: '106', police: '105', rsa: '110', cur: 'S/', curCode: 'PEN', fuel: 4.8, rouser: true, locale: 'es-PE' },
  { code: 'BR', name: 'Brazil', dial: '+55', sos: '190', amb: '192', police: '190', rsa: '191', cur: 'R$', curCode: 'BRL', fuel: 6.2, locale: 'pt-BR' },
  { code: 'PH', name: 'Philippines', dial: '+63', sos: '911', amb: '143', police: '117', rsa: '911', cur: '₱', curCode: 'PHP', fuel: 65.5, locale: 'en-PH' },
  { code: 'TR', name: 'Turkey', dial: '+90', sos: '112', amb: '112', police: '112', rsa: '112', cur: '₺', curCode: 'TRY', fuel: 44, locale: 'tr-TR' },
  { code: 'EG', name: 'Egypt', dial: '+20', sos: '122', amb: '123', police: '122', rsa: '126', cur: 'E£', curCode: 'EGP', fuel: 15, locale: 'en-EG' },
  { code: 'ZA', name: 'South Africa', dial: '+27', sos: '112', amb: '10177', police: '10111', rsa: '112', cur: 'R', curCode: 'ZAR', fuel: 24.2, locale: 'en-ZA' },
  { code: 'NP', name: 'Nepal', dial: '+977', sos: '100', amb: '102', police: '100', rsa: '103', cur: 'Rs', curCode: 'NPR', fuel: 175, locale: 'en-NP' },
  { code: 'BD', name: 'Bangladesh', dial: '+880', sos: '999', amb: '999', police: '999', rsa: '999', cur: '৳', curCode: 'BDT', fuel: 125, locale: 'en-BD' },
  { code: 'GB', name: 'United Kingdom / EU', dial: '+44', sos: '999', amb: '999', police: '112', rsa: '112', cur: '£', curCode: 'GBP', fuel: 1.45, locale: 'en-GB' },
];

export const MODELS = [
  { id: 'ns200', name: 'Pulsar NS200', series: 'NS Series', engine: '199.5cc triple spark, liquid-cooled · 24.5 PS / 18.7 Nm', tank: 12, reserve: 2.4, psi: [28, 28, 32, 36], tyres: ['100/80-17', '130/70-17'], oil: '10W-50 semi/fully synthetic · 1200 ml', coolant: 'Ready-mix glycol · 1000 ml', chain: '25–35 mm slack', plug: 'Champion RG4HC + RG6HCC ×2', liquid: true },
  { id: 'rs200', name: 'Pulsar RS200', series: 'NS Series', engine: '199.5cc triple spark, liquid-cooled · 24.5 PS / 18.7 Nm', tank: 13, reserve: 2.5, psi: [28, 28, 32, 36], tyres: ['100/80-17', '130/70-17'], oil: '10W-50 semi/fully synthetic · 1200 ml', coolant: 'Ready-mix glycol · 1000 ml', chain: '25–35 mm slack', plug: 'Champion RG4HC + RG6HCC ×2', liquid: true },
  { id: 'ns400z', name: 'Pulsar NS400Z', series: 'NS Series', engine: '373.3cc DOHC, liquid-cooled · 40 PS / 35 Nm', tank: 12, reserve: 2, psi: [29, 29, 33, 37], tyres: ['110/70-17', '140/70-17'], oil: '10W-50 fully synthetic · 1700 ml', coolant: 'Glycol · 1200 ml', chain: '20–30 mm slack', plug: 'Bosch VR5NEU', liquid: true },
  { id: 'ns160', name: 'Pulsar NS160', series: 'NS Series', engine: '160.3cc twin spark, oil-cooled · 17.2 PS / 14.6 Nm', tank: 12, reserve: 2.4, psi: [28, 28, 32, 36], tyres: ['100/80-17', '130/70-17'], oil: '10W-40 / 20W-50 · 1150 ml', coolant: 'Oil cooler', chain: '25–35 mm slack', plug: 'Champion PRG6C', liquid: false },
  { id: 'ns125', name: 'Pulsar NS125', series: 'NS Series', engine: '124.45cc DTS-i · 12 PS / 11 Nm', tank: 12, reserve: 2, psi: [25, 25, 28, 32], tyres: ['80/100-17', '100/90-17'], oil: '20W-50 4T · 1000 ml', coolant: 'Air-cooled', chain: '25–35 mm slack', plug: 'Champion RG4HC', liquid: false },
  { id: 'n250', name: 'Pulsar N250 / F250', series: 'N Series', engine: '249.07cc twin spark, oil-cooled · 24.5 PS / 21.5 Nm', tank: 14, reserve: 2.5, psi: [28, 28, 32, 36], tyres: ['110/70-17', '140/70-17'], oil: '10W-50 semi-synthetic · 1300 ml', coolant: 'Oil cooler', chain: '25–35 mm slack', plug: 'Champion PRG6C', liquid: false },
  { id: 'n160', name: 'Pulsar N160 / N150', series: 'N Series', engine: '164.82cc DTS-i, oil-cooled · 16 PS / 14.65 Nm', tank: 14, reserve: 2.5, psi: [25, 25, 28, 32], tyres: ['100/80-17', '130/70-17'], oil: '10W-40 semi-synthetic · 1200 ml', coolant: 'Oil cooler', chain: '25–35 mm slack', plug: 'Champion RG4HC', liquid: false },
  { id: 'pulsar150', name: 'Pulsar 150 / 125', series: 'Classic Series', engine: '149.5cc twin spark DTS-i · 14 PS / 13.25 Nm', tank: 11.5, reserve: 2, psi: [25, 25, 28, 32], tyres: ['80/100-17', '100/90-17'], oil: '20W-50 4T · 1000 ml', coolant: 'Air-cooled', chain: '25–35 mm slack', plug: 'Champion RG4HC', liquid: false },
  { id: 'pulsar220f', name: 'Pulsar 220F', series: 'Classic Series', engine: '220cc DTS-i, oil-cooled · 20.4 PS / 18.5 Nm', tank: 15, reserve: 3.2, psi: [28, 28, 32, 36], tyres: ['90/90-17', '120/80-17'], oil: '20W-50 semi-synthetic · 1150 ml', coolant: 'Oil cooler', chain: '25–35 mm slack', plug: 'Champion RG4HC', liquid: false },
  { id: 'dominar400', name: 'Dominar 400', series: 'Dominar Series', engine: '373.3cc DOHC, liquid-cooled · 40 PS / 35 Nm', tank: 13, reserve: 2.5, psi: [29, 29, 33, 36], tyres: ['110/70-17', '150/60-17'], oil: '10W-50 fully synthetic · 1700 ml', coolant: 'Glycol · 1200 ml', chain: '20–30 mm slack', plug: 'Bosch VR5NEU', liquid: true },
  { id: 'dominar250', name: 'Dominar 250', series: 'Dominar Series', engine: '248.77cc DOHC, liquid-cooled · 27 PS / 23.5 Nm', tank: 13, reserve: 2.5, psi: [29, 29, 33, 36], tyres: ['100/80-17', '130/70-17'], oil: '10W-50 semi-synthetic · 1500 ml', coolant: 'Glycol · 1100 ml', chain: '20–30 mm slack', plug: 'Champion RG4HC', liquid: true },
];

// ---------- Vehicles ----------
// Per-vehicle data lives in s.vehicles[]. For convenience the active vehicle's fields are also
// readable/writable straight off the state (s.odo, s.fuel, …) through non-enumerable accessors,
// so they never get saved twice.
export const VKEYS = ['odo', 'tripA', 'tripB', 'fuel', 'fuelPrice', 'service', 'records', 'checklist', 'trips', 'rideNo', 'parking', 'bike'];
export const veh = (s) => s.vehicles.find((v) => v.id === s.active) || s.vehicles[0];
export function bindVehicle(s) {
  for (const k of VKEYS) {
    Object.defineProperty(s, k, { get() { return veh(s)[k]; }, set(x) { veh(s)[k] = x; }, enumerable: false, configurable: true });
  }
  return s;
}
export const country = (s) => COUNTRIES.find((c) => c.code === s.profile.country) || COUNTRIES[0];
export const modelOf = (v) => MODELS.find((m) => m.id === v.model) || MODELS[0];
export const model = (s) => modelOf(veh(s));
export const nameOfModel = (s, m) => (country(s).rouser ? m.name.replace('Pulsar', 'Rouser') : m.name);
export const bikeName = (s) => nameOfModel(s, model(s));
export const vehicleTitle = (s, v = veh(s)) => v.nickname?.trim() || nameOfModel(s, modelOf(v));
export const COLOURS = [['#B3261E', 'Racing red'], ['#1F3A5F', 'Midnight blue'], ['#2F4F3E', 'Forest green'], ['#E9E6DF', 'Pearl white'], ['#2A2A2A', 'Graphite black'], ['#C9822B', 'Copper'], ['#5B6470', 'Slate grey']];

const KEY = 'pulsarnav-web-v2';
const OLD_KEY = 'pulsarnav-web-v1';
const DAY = 86400000;
export const uid = () => (globalThis.crypto?.randomUUID ? crypto.randomUUID() : Math.random().toString(36).slice(2) + Date.now().toString(36));

export function defaultServiceItems(odo = 0, now = Date.now()) {
  return [
    { id: uid(), kind: 'chain', name: 'Chain clean & lube', icon: 'chain', everyKm: 500, everyDays: 0, lastKm: odo, lastDate: now },
    { id: uid(), kind: 'service', name: 'Periodic service', icon: 'wrench', everyKm: 5000, everyDays: 365, lastKm: odo, lastDate: now, milestone: true, targetKm: odo < 750 ? 500 : odo < 5000 ? 5000 : odo < 10000 ? 10000 : (Math.floor(odo / 5000) + 1) * 5000 },
    { id: uid(), kind: 'oil', name: 'Engine oil', icon: 'drop', everyKm: 3000, everyDays: 182, lastKm: odo, lastDate: now },
    { id: uid(), kind: 'brakes', name: 'Brake pads check', icon: 'brake', everyKm: 5000, everyDays: 182, lastKm: odo, lastDate: now },
    { id: uid(), kind: 'tyres', name: 'Tyre pressure', icon: 'gauge', everyKm: 0, everyDays: 14, lastKm: odo, lastDate: now },
    { id: uid(), kind: 'air', name: 'Air filter', icon: 'wind', everyKm: 10000, everyDays: 365, lastKm: odo, lastDate: now },
  ];
}

export function newVehicle({ model = 'ns200', nickname = '', plate = '', odo = 0, colour = COLOURS[0][0], photo = null } = {}) {
  const now = Date.now();
  return {
    id: uid(), model, nickname, plate, colour, photo, addedAt: now,
    odo: Math.max(0, +odo || 0), tripA: { km: 0, since: now }, tripB: { km: 0, since: now },
    fuel: [], fuelPrice: null, service: defaultServiceItems(+odo || 0, now), records: [], checklist: [],
    trips: [], rideNo: 0, parking: null, bike: { id: null, name: null },
  };
}

export function freshState() {
  const v = newVehicle();
  return bindVehicle({
    v: 2, onboarded: false, sample: false,
    profile: { name: '', phone: '', email: '', city: '', country: 'IN', licenceNo: '', licenceExpiry: null, blood: '', medical: '', iceName: '', icePhone: '', photo: null, avatar: 'a' + (1 + Math.floor(Math.random() * 11)), since: Date.now() },
    settings: { theme: 'auto', icons: 'fill', accent: 'wine', font: 'system', devFrames: false, rideLayout: 0, art: 'photo', autoRide: false, autoRecord: false, wakeLock: true, streetNames: false, sounds: true, progress: 'gauge', brand: 'loop', bodyFont: 'system', simulated: false, alerts: { calls: true, waCalls: true, waMsgs: true } },
    alerts: { missed: [], wa: [], sent: 0, log: [] },
    vehicles: [v], active: v.id,
  });
}

/** Accepts any saved shape (v1 single-bike or v2 garage) and returns a bound v2 state. */
export function migrate(raw) {
  const f = freshState();
  if (!raw || typeof raw !== 'object') return f;
  let vehicles = raw.vehicles, active = raw.active;
  if (!Array.isArray(vehicles) || !vehicles.length) {
    const v = newVehicle({ model: raw.profile?.model || 'ns200', plate: raw.profile?.plate || '', odo: raw.odo || 0 });
    for (const k of VKEYS) if (raw[k] !== undefined) v[k] = raw[k];
    vehicles = [v]; active = v.id;
  }
  vehicles = vehicles.map((v) => ({ ...newVehicle(), ...v }));
  const p = { ...f.profile, ...(raw.profile || {}) }; delete p.model; delete p.plate;
  const s = {
    ...f, v: 2, onboarded: !!raw.onboarded, sample: !!raw.sample, profile: p,
    settings: { ...f.settings, ...(raw.settings || {}), alerts: { ...f.settings.alerts, ...(raw.settings?.alerts || {}) } },
    alerts: { ...f.alerts, ...(raw.alerts || {}) },
    vehicles, active: vehicles.some((v) => v.id === active) ? active : vehicles[0].id,
  };
  for (const k of VKEYS) delete s[k];
  // Pictures are always shown when a bike has one; the old 'icons only' choice is gone.
  s.settings.art = 'photo';
  if (s.settings.theme === 'carbon') s.settings.theme = 'obsidian';
  if (typeof s.settings.sounds !== 'boolean') s.settings.sounds = true;
  // New type defaults (Crimson titles, Atkinson body), applied once to existing installs too.
  // Rides no longer start by themselves unless the rider turns it on (asked once after pairing).
  // Navy is the default look; riders still on Auto (or moved to Paper by v102) move to it once.
  if (!s.settings.wineV1) { if (s.settings.accent === 'blue' || !s.settings.accent) s.settings.accent = 'wine'; s.settings.wineV1 = true; }
  if (!s.settings.navyV1) { if (s.settings.theme === 'auto' || (s.settings.paperV1 && s.settings.theme === 'sketch')) s.settings.theme = 'amoled'; s.settings.navyV1 = true; }
  // Paper is the look now: Auto follows the phone between Paper and Paper dark. Other themes stay behind a developer switch.
  if (!s.settings.paperV2) { if (!['sketch', 'sketchdark'].includes(s.settings.theme)) s.settings.theme = 'auto'; s.settings.paperV2 = true; }
  // Every rider gets a face: pick a default avatar for anyone without a photo or a chosen rider.
  if (!s.profile.photo && !/^a([1-9]|1[01])$/.test(s.profile.avatar || '')) s.profile.avatar = 'a' + (1 + ([...(s.profile.name || 'rider')].reduce((h, c) => h * 31 + c.charCodeAt(0), 7) >>> 0) % 11);
  // Duotone icons are the default look; riders on plain line icons move to it once.
  if (!s.settings.duoV1) { if (!s.settings.icons || s.settings.icons === 'line') s.settings.icons = 'fill'; s.settings.duoV1 = true; }
  if (!s.settings.autoV2) { s.settings.autoRide = false; s.settings.autoRecord = false; s.settings.autoV2 = true; }
  if (!s.settings.typeV5) { s.settings.font = 'system'; s.settings.bodyFont = 'system'; s.settings.typeV5 = true; }
  if (!['system', 'atkinson', 'public'].includes(s.settings.bodyFont)) s.settings.bodyFont = 'system';
  delete s.settings.logo;
  if (!s.settings.font || !['system','onest','geist','garamond','crimson','fraunces'].includes(s.settings.font)) s.settings.font = 'system';
  return bindVehicle(s);
}

export function load() {
  try {
    const raw = localStorage.getItem(KEY) ?? localStorage.getItem(OLD_KEY);
    return raw ? migrate(JSON.parse(raw)) : freshState();
  } catch { return freshState(); }
}
let saveTimer, canSave = true;
/** Turned off while previewing empty/edge-case states so real data is never overwritten. */
export function setSaveEnabled(v) { canSave = !!v; if (!canSave) clearTimeout(saveTimer); }
export function save(s) { if (!canSave) return; clearTimeout(saveTimer); saveTimer = setTimeout(() => saveNow(s), 120); }
export function saveNow(s) { if (!canSave) return; try { localStorage.setItem(KEY, JSON.stringify(s)); } catch {} }
export function wipe() { try { localStorage.removeItem(KEY); localStorage.removeItem(OLD_KEY); } catch {} }

// ---------- Odometer ----------
export function addDistance(s, km) {
  if (!(km > 0)) return;
  s.odo = round1(s.odo + km);
  s.tripA.km = round2(s.tripA.km + km);
  s.tripB.km = round2(s.tripB.km + km);
}
export function setOdometer(s, km) { if (km >= 0) s.odo = round1(km); }
const round1 = (x) => Math.round(x * 10) / 10;
const round2 = (x) => Math.round(x * 100) / 100;

// ---------- Fuel (full tank to full tank) ----------
export function fuelStats(s) {
  const tank = model(s).tank;
  const list = [...s.fuel].sort((a, b) => a.odo - b.odo || a.ts - b.ts);
  const tanks = [];
  let lastFullIdx = -1;
  list.forEach((e, i) => {
    if (!e.full) return;
    if (lastFullIdx >= 0) {
      const prev = list[lastFullIdx];
      const dist = e.odo - prev.odo;
      let litres = 0;
      for (let j = lastFullIdx + 1; j <= i; j++) litres += list[j].litres;
      if (dist > 1 && litres > 0) {
        const kmpl = dist / litres;
        if (kmpl > 3 && kmpl < 120) tanks.push({ ts: e.ts, id: e.id, dist, litres, kmpl });
      }
    }
    lastFullIdx = i;
  });
  const last3 = tanks.slice(-3);
  const avg = last3.length ? last3.reduce((a, t) => a + t.dist, 0) / last3.reduce((a, t) => a + t.litres, 0) : null;
  const lifetime = tanks.length ? tanks.reduce((a, t) => a + t.dist, 0) / tanks.reduce((a, t) => a + t.litres, 0) : null;
  const lastTank = tanks.length ? tanks[tanks.length - 1].kmpl : null;

  let litresLeft = null, sinceFull = null;
  const lastFull = [...list].reverse().find((e) => e.full);
  if (lastFull && avg) {
    sinceFull = Math.max(0, s.odo - lastFull.odo);
    const topUps = list.filter((e) => !e.full && e.odo > lastFull.odo).reduce((a, e) => a + e.litres, 0);
    litresLeft = Math.min(tank, Math.max(0, tank + topUps - sinceFull / avg));
  } else if (lastFull) sinceFull = Math.max(0, s.odo - lastFull.odo);
  const range = litresLeft != null ? litresLeft * avg : null;
  const bars = litresLeft == null ? null : litresLeft <= 0 ? 0 : Math.max(1, Math.floor((litresLeft / tank) * 6 + 1e-9));

  let costPerKm = null;
  if (list.length > 1) {
    const span = list[list.length - 1].odo - list[0].odo;
    const spent = list.slice(1).reduce((a, e) => a + e.cost, 0);
    if (span > 0) costPerKm = spent / span;
  }
  const cut = Date.now() - 30 * DAY;
  const recent = list.filter((e) => e.ts >= cut);
  const totalSpent = list.reduce((a, e) => a + e.cost, 0);
  return {
    tank, reserve: model(s).reserve, tanks, avg, lifetime, lastTank, litresLeft, sinceFull, range, bars, costPerKm, totalSpent,
    last30: { count: recent.length, litres: recent.reduce((a, e) => a + e.litres, 0), cost: recent.reduce((a, e) => a + e.cost, 0) },
    onReserve: litresLeft != null && litresLeft <= model(s).reserve,
  };
}

// ---------- Logbook ----------
export function serviceStatus(s, item, now = Date.now()) {
  const odo = s.odo;
  let dueKm = null, remKm = null, fracKm = 0;
  if (item.milestone) { dueKm = item.targetKm; const span = dueKm <= 750 ? 500 : 5000; remKm = dueKm - odo; fracKm = 1 - remKm / span; }
  else if (item.everyKm > 0) { dueKm = item.lastKm + item.everyKm; remKm = dueKm - odo; fracKm = (odo - item.lastKm) / item.everyKm; }
  let dueDate = null, remDays = null, fracT = 0;
  if (item.everyDays > 0 && !item.milestone) {
    dueDate = item.lastDate + item.everyDays * DAY;
    remDays = Math.ceil((dueDate - now) / DAY);
    fracT = (now - item.lastDate) / (item.everyDays * DAY);
  }
  const overdueKm = remKm != null && remKm < 0 ? -remKm : 0;
  const overdueDays = remDays != null && remDays < 0 ? -remDays : 0;
  const overdue = overdueKm > 0 || overdueDays > 0;
  const soonKm = remKm != null && item.everyKm > 0 && remKm <= Math.max(50, (item.milestone ? 200 : 0.15 * item.everyKm));
  const soonDays = remDays != null && remDays <= Math.min(21, Math.max(2, item.everyDays * 0.15));
  const status = overdue ? 'overdue' : soonKm || soonDays ? 'soon' : 'ok';
  const byTime = fracT > fracKm;
  return { dueKm, remKm, dueDate, remDays, overdueKm, overdueDays, status, frac: Math.max(0, Math.min(1, Math.max(fracKm, fracT))), byTime };
}

export function markServiceDone(s, item) {
  item.lastKm = s.odo; item.lastDate = Date.now();
  if (item.milestone) {
    const t = item.targetKm;
    item.targetKm = t <= 750 ? 5000 : t <= 5000 ? 10000 : s.odo + 5000;
  }
}

export const needsYou = (s) => s.service.filter((i) => serviceStatus(s, i).status !== 'ok').length;

// ---------- Trips ----------
export function haversine(a, b) {
  const R = 6371e3, toR = Math.PI / 180;
  const dLat = (b[0] - a[0]) * toR, dLng = (b[1] - a[1]) * toR;
  const x = Math.sin(dLat / 2) ** 2 + Math.cos(a[0] * toR) * Math.cos(b[0] * toR) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(x));
}

export function tripTitle(ts) {
  const h = new Date(ts).getHours();
  return h < 5 ? 'Night ride' : h < 12 ? 'Morning ride' : h < 17 ? 'Afternoon ride' : h < 21 ? 'Evening ride' : 'Night ride';
}

export function periodStats(trips, from) {
  const t = trips.filter((x) => x.start >= from);
  return { km: t.reduce((a, x) => a + x.km, 0), rides: t.length, sec: t.reduce((a, x) => a + x.sec, 0), top: t.reduce((a, x) => Math.max(a, x.top), 0) };
}
export function weekStart(now = new Date()) {
  const d = new Date(now); d.setHours(0, 0, 0, 0);
  const day = (d.getDay() + 6) % 7; d.setDate(d.getDate() - day); return d.getTime();
}

export function toGPX(trip) {
  const pts = (trip.pts || []).map((p) => `      <trkpt lat="${p[0].toFixed(6)}" lon="${p[1].toFixed(6)}">${p[2] ? `<time>${new Date(p[2]).toISOString()}</time>` : ''}</trkpt>`).join('\n');
  return `<?xml version="1.0" encoding="UTF-8"?>\n<gpx version="1.1" creator="Waylink" xmlns="http://www.topografix.com/GPX/1/1">\n  <trk><name>${escapeXml(trip.title)}</name>\n    <trkseg>\n${pts}\n    </trkseg>\n  </trk>\n</gpx>\n`;
}
const escapeXml = (x) => String(x).replace(/[<>&"]/g, (c) => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', '"': '&quot;' }[c]));

// ---------- Data vault ----------
export function exportVault(s) {
  const data = JSON.parse(JSON.stringify(s));
  data.vehicles.forEach((v) => { v.bike = { id: null, name: null }; });
  return JSON.stringify({ format: 'waylink-vault', version: 2, exported: new Date().toISOString(), data }, null, 2);
}
export function importVault(text) {
  let j;
  try { j = JSON.parse(text); } catch { throw new Error('That isn’t a backup from this app.'); }
  const data = ['waylink-vault', 'rev-vault', 'kickstart-vault', 'pulsarnav-vault'].includes(j?.format) ? j.data : j;
  const ok = data && typeof data === 'object' && (Array.isArray(data.vehicles) || ('odo' in data && Array.isArray(data.fuel)));
  if (!ok) throw new Error('That isn’t a backup from this app.');
  const s = migrate(data); s.onboarded = true;
  return s;
}

// ---------- Glovebox (IndexedDB) ----------
const DB = 'pulsarnav-glovebox';
function db() {
  return new Promise((res, rej) => {
    const r = indexedDB.open(DB, 1);
    r.onupgradeneeded = () => r.result.createObjectStore('docs', { keyPath: 'slot' });
    r.onsuccess = () => res(r.result); r.onerror = () => rej(r.error);
  });
}
export async function gloveList() {
  try { const d = await db(); return await new Promise((res) => { const q = d.transaction('docs').objectStore('docs').getAll(); q.onsuccess = () => res(q.result || []); q.onerror = () => res([]); }); } catch { return []; }
}
export async function glovePut(doc) {
  const d = await db();
  return new Promise((res, rej) => { const tx = d.transaction('docs', 'readwrite'); tx.objectStore('docs').put(doc); tx.oncomplete = res; tx.onerror = () => rej(tx.error); });
}
export async function gloveDelete(slot) {
  const d = await db();
  return new Promise((res) => { const tx = d.transaction('docs', 'readwrite'); tx.objectStore('docs').delete(slot); tx.oncomplete = res; tx.onerror = res; });
}

// ---------- Sample data (clearly labelled; erase any time in Settings) ----------
export function seedSample(s) {
  const now = Date.now();
  const d = (n) => now - n * DAY;
  s.sample = true;
  Object.assign(s.profile, {
    name: s.profile.name || 'Arjun', phone: '+91 98450 12345', email: 'arjun.rao@example.in', city: 'Bengaluru', country: 'IN',
    licenceNo: 'KA05 20190012345', licenceExpiry: new Date(new Date().getFullYear() + 9, 2, 14).getTime(), blood: 'B+', medical: 'No known allergies',
    iceName: 'Amma', icePhone: '+91 98860 55443', since: d(410),
  });
  const price = 102.9;
  const blr = [12.9716, 77.5946];

  // --- Daily: Pulsar NS200 ---
  const a = newVehicle({ model: 'ns200', nickname: 'Daily', plate: 'KA 05 MX 2481', odo: 12261, colour: '#B3261E' });
  a.addedAt = d(400);
  a.tripA = { km: 84.6, since: d(5) }; a.tripB = { km: 462.3, since: d(24) };
  a.fuel = [[-63, 10474, 11.2, true], [-56, 10795, 11.18, true], [-49, 11113, 11.52, true], [-35, 11441, 11.12, true], [-28, 11552, 3.0, false], [-21, 11764, 7.98, true], [-11, 11995, 7.8, true], [-5, 12176, 6.07, true]]
    .map(([dd, odo, l, full]) => ({ id: uid(), ts: now + dd * DAY, odo, litres: l, cost: Math.round(l * price), full }));
  a.fuelPrice = price;
  a.service = [
    { id: uid(), kind: 'chain', name: 'Chain clean & lube', icon: 'chain', everyKm: 500, everyDays: 0, lastKm: 11655, lastDate: d(19) },
    { id: uid(), kind: 'brakes', name: 'Brake pads check', icon: 'brake', everyKm: 5000, everyDays: 182, lastKm: 8402, lastDate: d(166) },
    { id: uid(), kind: 'oil', name: 'Engine oil', icon: 'drop', everyKm: 3000, everyDays: 182, lastKm: 9874, lastDate: d(102) },
    { id: uid(), kind: 'tyres', name: 'Tyre pressure', icon: 'gauge', everyKm: 0, everyDays: 14, lastKm: 12010, lastDate: d(4) },
    { id: uid(), kind: 'service', name: 'Periodic service', icon: 'wrench', everyKm: 5000, everyDays: 365, lastKm: 10020, lastDate: d(120), milestone: true, targetKm: 15000 },
    { id: uid(), kind: 'air', name: 'Air filter', icon: 'wind', everyKm: 10000, everyDays: 365, lastKm: 10020, lastDate: d(120) },
  ];
  a.records = [
    { id: uid(), date: d(120), odo: 10020, title: '3rd paid service', workshop: 'Sri Ganesh Bajaj, Jayanagar', cost: 2850, notes: 'Oil and oil filter, air filter, chain adjusted' },
    { id: uid(), date: d(300), odo: 5040, title: '2nd service', workshop: 'Sri Ganesh Bajaj, Jayanagar', cost: 1980, notes: 'Oil and filter, brake fluid top-up' },
  ];
  a.checklist = [
    { id: uid(), text: 'Horn sounds weak in the rain', done: false },
    { id: uid(), text: 'Ask for the chain-sprocket kit price', done: false },
    { id: uid(), text: 'Replace the left mirror boot', done: true },
  ];
  const today = new Date(); today.setHours(19, 2, 0, 0);
  const t0 = today.getTime() - now;
  const mk = (n, off, km, min, top, seed, title, start = blr) => {
    const st = now + off;
    return { id: uid(), n, start: st, end: st + min * 60000, km, sec: min * 60, top, avg: km / (min / 60), title: title || tripTitle(st), pts: squiggle(start, km, seed, st, min) };
  };
  a.trips = [
    mk(16, t0 - 1 * DAY, 10.0, 24, 62, 1, 'Office to home'), mk(15, t0 - 2 * DAY - 38 * 60000, 10.0, 26, 61, 1, 'Office to home'),
    mk(14, t0 - 2 * DAY - 610 * 60000, 9.5, 31, 58, 2, 'Home to office'), mk(13, t0 - 3 * DAY - 25 * 60000, 31.4, 52, 88, 3, 'Kanakapura Road loop'),
    mk(12, t0 - 4 * DAY - 640 * 60000, 9.7, 29, 57, 2, 'Home to office'), mk(11, t0 - 5 * DAY, 10.2, 25, 60, 1, 'Office to home'),
    mk(10, t0 - 7 * DAY - 300 * 60000, 121.6, 168, 104, 4, 'Nandi Hills sunrise'), mk(9, t0 - 10 * DAY, 9.9, 27, 59, 1, 'Office to home'),
    mk(8, t0 - 12 * DAY - 610 * 60000, 9.4, 30, 55, 2, 'Home to office'), mk(7, t0 - 14 * DAY, 48.0, 81, 96, 5, 'Bannerghatta Road run'),
    mk(6, t0 - 17 * DAY, 10.1, 25, 61, 1, 'Office to home'), mk(5, t0 - 19 * DAY - 610 * 60000, 9.6, 30, 57, 2, 'Home to office'),
    mk(4, t0 - 21 * DAY, 22.3, 44, 79, 3), mk(3, t0 - 23 * DAY, 10.0, 24, 63, 1, 'Office to home'), mk(2, t0 - 26 * DAY, 41.7, 70, 92, 5, 'Hosur Road errands'), mk(1, t0 - 33 * DAY, 18.2, 40, 74, 3),
  ].sort((x, y) => y.start - x.start);
  a.rideNo = 16;

  // --- Tourer: Dominar 400 ---
  const b = newVehicle({ model: 'dominar400', nickname: 'Tourer', plate: 'KA 03 NB 7719', odo: 8420, colour: '#2F4F3E' });
  b.addedAt = d(220);
  b.tripA = { km: 268.4, since: d(12) }; b.tripB = { km: 1140.2, since: d(60) };
  b.fuel = [[-40, 7480, 12.6, true], [-26, 7840, 13.0, true], [-12, 8195, 12.4, true], [-4, 8395, 7.1, true]]
    .map(([dd, odo, l, full]) => ({ id: uid(), ts: now + dd * DAY, odo, litres: l, cost: Math.round(l * price), full }));
  b.fuelPrice = price;
  b.service = defaultServiceItems(8420).map((it) => ({ ...it, lastKm: it.kind === 'chain' ? 8120 : it.kind === 'tyres' ? 8395 : 6050, lastDate: it.kind === 'tyres' ? d(9) : it.kind === 'chain' ? d(10) : d(88), ...(it.milestone ? { targetKm: 10000 } : {}) }));
  b.records = [{ id: uid(), date: d(88), odo: 6050, title: '2nd service', workshop: 'Bajaj Probiking, Indiranagar', cost: 3420, notes: 'Oil, coolant check, chain set' }];
  b.trips = [
    mk(3, t0 - 12 * DAY - 700 * 60000, 268.4, 330, 118, 6, 'Coorg weekend, day 1', [12.95, 77.4]),
    mk(2, t0 - 11 * DAY - 200 * 60000, 271.0, 345, 121, 7, 'Coorg weekend, day 2', [12.4, 75.8]),
    mk(1, t0 - 30 * DAY - 500 * 60000, 64.2, 96, 102, 4, 'Nandi Hills with the gang'),
  ];
  b.rideNo = 3;

  s.vehicles = [a, b];
  s.active = a.id;
  s.alerts = {
    missed: [{ name: 'Rohan', ts: now - 3600000 }, { name: 'Rohan', ts: now - 3000000 }], wa: [], sent: 6, log: [
      { kind: 'missed', name: 'Rohan', ts: now - 3000000 }, { kind: 'missed', name: 'Rohan', ts: now - 3600000 },
      { kind: 'wa', name: 'Priya', ts: now - 2 * 3600000 }, { kind: 'call', name: 'Amma', ts: now - DAY },
    ],
  };
  return s;
}

function squiggle([lat, lng], km, seed, start, min) {
  let r = seed * 9301 + 49297;
  const rnd = () => ((r = (r * 9301 + 49297) % 233280) / 233280);
  const n = 40, pts = [];
  let a = rnd() * Math.PI * 2, p = [lat, lng];
  const step = km / n / 111;
  for (let i = 0; i <= n; i++) {
    pts.push([+p[0].toFixed(6), +p[1].toFixed(6), Math.round(start + (i / n) * min * 60000)]);
    a += (rnd() - 0.5) * 0.9;
    p = [p[0] + Math.cos(a) * step, p[1] + Math.sin(a) * step];
  }
  return pts;
}

// ---------- Preview states for design and QA (never saved) ----------
export function scenario(kind, base) {
  const s = freshState();
  s.onboarded = true;
  s.settings = { ...s.settings, ...(base?.settings || {}) };
  if (kind === 'empty') { s.profile.name = ''; return s; }
  // Edge cases: long text, huge and tiny numbers, everything due at once.
  const now = Date.now(), DAYMS = 86400000;
  s.profile = { ...s.profile, name: 'Venkataraghavan Subramaniam', city: 'Thiruvananthapuram', blood: 'AB−', medical: 'Allergic to penicillin, sulfa drugs and bee stings. Carries an EpiPen in the left jacket pocket.', iceName: 'Lakshmi Narayanan (Amma)', icePhone: '+91 98450 00000', licenceNo: 'KA05 20190012345', licenceExpiry: now - 3 * DAYMS };
  const v = newVehicle({ model: 'dominar400', nickname: 'Long-distance tourer', plate: '22 BH 1234 AA', odo: 199999.9, colour: '#E9E6DF' });
  v.tripA = { km: 9999.9, since: now - 400 * DAYMS }; v.tripB = { km: 0, since: now };
  v.fuel = [
    { id: uid(), ts: now - 40 * DAYMS, odo: 199400, litres: 13, cost: 1338, full: true },
    { id: uid(), ts: now - 30 * DAYMS, odo: 199402, litres: 0.4, cost: 41, full: false },
    { id: uid(), ts: now - 20 * DAYMS, odo: 199760, litres: 12.6, cost: 99999, full: true },
    { id: uid(), ts: now - 2 * DAYMS, odo: 199995, litres: 1.1, cost: 113, full: false },
  ];
  v.service = defaultServiceItems(150000, now - 900 * DAYMS);
  v.service.push({ id: uid(), kind: 'custom', name: 'Steering bearings and swingarm re-grease', icon: 'wrench', everyKm: 1, everyDays: 1, lastKm: 0, lastDate: now - 999 * DAYMS });
  v.records = [{ id: uid(), date: now - 10 * DAYMS, odo: 199900, title: 'Engine rebuild after a long Western Ghats road trip', workshop: 'Sri Venkateshwara Motorcycle Works', cost: 1234567, notes: '' }];
  v.checklist = Array.from({ length: 6 }, (_, i) => ({ id: uid(), text: i === 0 ? 'A very long mechanic note that keeps going to check how the list wraps on small screens without breaking the layout' : `Item ${i + 1}`, done: i % 2 === 0 }));
  v.trips = [
    { id: uid(), n: 9999, start: now - 3600000, end: now - 3500000, km: 0.1, sec: 60, top: 3, avg: 6, title: 'A', pts: [[12.97, 77.59, now - 3600000]] },
    { id: uid(), n: 9998, start: now - 5 * DAYMS, end: now - 4 * DAYMS, km: 1240.6, sec: 86000, top: 162, avg: 52, title: 'Bengaluru to Leh in one go, a ride with a title long enough to wrap twice', pts: [] },
  ];
  v.rideNo = 9999;
  v.parking = { lat: 34.1526, lng: 77.5771, ts: now - 40 * DAYMS };
  s.vehicles = [v]; s.active = v.id;
  s.alerts = { missed: Array.from({ length: 128 }, (_, i) => ({ name: i === 127 ? 'Dr. Venkataraghavan Subramaniam' : 'Rohan', ts: now - i * 60000 })), wa: Array.from({ length: 12 }, () => ({ name: 'Priya 🌸', ts: now })), sent: 99999, log: [] };
  return s;
}

// ---------- Local petrol prices (India) ----------
// State rates from published city prices (HDFC Sky 9 Nov 2026, The Hans India 7 Nov 2026,
// Business Today state table of 27 Oct 2026). Pumps can differ by a rupee or two within a state.
export const FUEL_PRICES = {
  asOf: '2026-11-09',
  states: {
    'Andhra Pradesh': 117.51, 'Telangana': 116.15, 'Kerala': 115.49, 'Madhya Pradesh': 114.54, 'West Bengal': 113.51,
    'Bihar': 113.37, 'Rajasthan': 113.19, 'Maharashtra': 111.21, 'Karnataka': 110.82, 'Odisha': 108.97,
    'Tamil Nadu': 107.76, 'Punjab': 105.56, 'Haryana': 102.97, 'Delhi': 102.12, 'Uttar Pradesh': 101.86,
    'Chandigarh': 101.54, 'Uttarakhand': 99.54, 'Dadra and Nagar Haveli and Daman and Diu': 99.44,
    'Arunachal Pradesh': 97.96, 'Andaman and Nicobar Islands': 88.66,
  },
};
const CITY_STATE = {
  bengaluru: 'Karnataka', bangalore: 'Karnataka', mysuru: 'Karnataka', mysore: 'Karnataka', mangaluru: 'Karnataka', mangalore: 'Karnataka', hubballi: 'Karnataka', hubli: 'Karnataka', belagavi: 'Karnataka',
  mumbai: 'Maharashtra', pune: 'Maharashtra', nagpur: 'Maharashtra', nashik: 'Maharashtra', thane: 'Maharashtra', aurangabad: 'Maharashtra',
  chennai: 'Tamil Nadu', coimbatore: 'Tamil Nadu', madurai: 'Tamil Nadu', trichy: 'Tamil Nadu', tiruchirappalli: 'Tamil Nadu', salem: 'Tamil Nadu',
  hyderabad: 'Telangana', secunderabad: 'Telangana', warangal: 'Telangana',
  vijayawada: 'Andhra Pradesh', visakhapatnam: 'Andhra Pradesh', vizag: 'Andhra Pradesh', tirupati: 'Andhra Pradesh', guntur: 'Andhra Pradesh', amaravati: 'Andhra Pradesh',
  thiruvananthapuram: 'Kerala', trivandrum: 'Kerala', kochi: 'Kerala', cochin: 'Kerala', kozhikode: 'Kerala', calicut: 'Kerala', thrissur: 'Kerala',
  kolkata: 'West Bengal', calcutta: 'West Bengal', siliguri: 'West Bengal', durgapur: 'West Bengal', howrah: 'West Bengal',
  delhi: 'Delhi', 'new delhi': 'Delhi',
  gurugram: 'Haryana', gurgaon: 'Haryana', faridabad: 'Haryana', panipat: 'Haryana', ambala: 'Haryana',
  noida: 'Uttar Pradesh', lucknow: 'Uttar Pradesh', kanpur: 'Uttar Pradesh', ghaziabad: 'Uttar Pradesh', agra: 'Uttar Pradesh', varanasi: 'Uttar Pradesh', prayagraj: 'Uttar Pradesh',
  jaipur: 'Rajasthan', jodhpur: 'Rajasthan', udaipur: 'Rajasthan', kota: 'Rajasthan', ajmer: 'Rajasthan',
  patna: 'Bihar', gaya: 'Bihar', muzaffarpur: 'Bihar',
  bhopal: 'Madhya Pradesh', indore: 'Madhya Pradesh', gwalior: 'Madhya Pradesh', jabalpur: 'Madhya Pradesh',
  bhubaneswar: 'Odisha', cuttack: 'Odisha', rourkela: 'Odisha', puri: 'Odisha',
  chandigarh: 'Chandigarh', ludhiana: 'Punjab', amritsar: 'Punjab', jalandhar: 'Punjab', mohali: 'Punjab', patiala: 'Punjab',
  dehradun: 'Uttarakhand', haridwar: 'Uttarakhand', rishikesh: 'Uttarakhand', haldwani: 'Uttarakhand',
  daman: 'Dadra and Nagar Haveli and Daman and Diu', silvassa: 'Dadra and Nagar Haveli and Daman and Diu',
  itanagar: 'Arunachal Pradesh', 'port blair': 'Andaman and Nicobar Islands', 'sri vijaya puram': 'Andaman and Nicobar Islands',
};
const norm = (t) => String(t || '').toLowerCase().replace(/[^a-z ]/g, ' ').replace(/\s+/g, ' ').trim();
// Finds the state rate for a city or state name. Returns { state, place, price } or null.
export function fuelLookup(...places) {
  for (const p of places) {
    const n = norm(p); if (!n) continue;
    const st = Object.keys(FUEL_PRICES.states).find((k) => norm(k) === n) || CITY_STATE[n]
      || Object.entries(CITY_STATE).find(([c]) => ` ${n} `.includes(` ${c} `))?.[1]
      || Object.keys(FUEL_PRICES.states).find((k) => ` ${n} `.includes(` ${norm(k)} `));
    if (st && FUEL_PRICES.states[st]) return { state: st, place: String(p).trim(), price: FUEL_PRICES.states[st] };
    if (st) return { state: st, place: String(p).trim(), price: null };
  }
  return null;
}
// The price used for new fill-ups: yours if you set one, else your area's rate, else the country default.
export const fuelPriceOf = (s) => s.fuelPrice ?? ((country(s).code === 'IN' && fuelLookup(s.profile?.fuelArea?.state, s.profile?.city)?.price) || country(s).fuel);

// ---------- Weather (Open-Meteo: free, no key) ----------
// WMO weather codes → label and icon key.
export function wmo(code, day = true) {
  const c = +code;
  if (c === 0) return { t: day ? 'Clear' : 'Clear night', ic: day ? 'wxSun' : 'wxMoon' };
  if (c <= 2) return { t: 'Partly cloudy', ic: day ? 'wxPart' : 'wxPartNight' };
  if (c === 3) return { t: 'Overcast', ic: 'wxCloud' };
  if (c === 45 || c === 48) return { t: 'Fog', ic: 'wxFog' };
  if (c >= 51 && c <= 57) return { t: 'Drizzle', ic: 'wxDrizzle' };
  if ((c >= 61 && c <= 67) || (c >= 80 && c <= 82)) return { t: c >= 65 || c === 82 ? 'Heavy rain' : 'Rain', ic: 'wxRain' };
  if ((c >= 71 && c <= 77) || c === 85 || c === 86) return { t: 'Snow', ic: 'wxSnow' };
  if (c >= 95 && c <= 99) return { t: 'Thunderstorm', ic: 'wxStorm' };
  return { t: 'Cloudy', ic: 'wxCloud' };
}
// One line a rider can act on, from the next 12 hours.
export function rideHint(w) {
  if (!w?.hours?.length) return '';
  const fmt = (ts) => new Date(ts).toLocaleTimeString('en-IN', { hour: 'numeric' }).replace(/\s?(am|pm)/i, (m) => ' ' + m.trim().toLowerCase());
  const wet = w.hours.find((h) => h.rain >= 50 || [61, 63, 65, 80, 81, 82, 95, 96, 99].includes(h.code));
  if (w.code >= 95 && w.code <= 99) return 'Thunderstorm now. Wait it out if you can.';
  if (w.code >= 61 && w.code <= 82) return 'Raining now. Roads are slippery; leave more gap.';
  if (wet) return wet === w.hours[0] ? `Rain likely soon (${wet.rain}%). Pack a rain liner.` : `Rain likely around ${fmt(wet.ts)} (${wet.rain}%). Pack a rain liner.`;
  if (w.code === 45 || w.code === 48) return 'Fog about. Lights on, speed down.';
  if (w.wind >= 35) return `Gusty at ${Math.round(w.wind)} km/h. Hold the line on open roads.`;
  if (w.temp >= 36) return 'Hot out. Carry water and take breaks.';
  const max = Math.max(...w.hours.map((h) => h.rain || 0));
  return max < 20 ? 'Dry for the next 12 hours. Good riding weather.' : `Low chance of rain (${max}%) through the day.`;
}
// Shapes an Open-Meteo forecast response into what the card needs.
export function parseWeather(j, place) {
  const cur = j?.current; if (!cur) throw new Error('no current');
  const H = j.hourly || {}, now = Date.now();
  const hours = (H.time || []).map((t, i) => ({ ts: typeof t === 'number' ? t * 1000 : new Date(t).getTime(), temp: H.temperature_2m?.[i], rain: H.precipitation_probability?.[i] ?? 0, code: H.weather_code?.[i], day: H.is_day?.[i] !== 0 }))
    .filter((h) => h.ts >= now - 3600e3).slice(0, 12);
  return { place, ts: now, temp: cur.temperature_2m, feels: cur.apparent_temperature, code: cur.weather_code, wind: cur.wind_speed_10m, day: cur.is_day !== 0, hours };
}
