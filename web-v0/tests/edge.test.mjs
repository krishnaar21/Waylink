// Edge cases and fuzzing: node --test tests/
import test from 'node:test';
import assert from 'node:assert/strict';
import * as P from '../js/protocol.js';
import * as S from '../js/store.js';
import * as V from '../js/views.js';

const sum = (b, n) => { let x = 0; for (let i = 0; i < n; i++) x = (x + b[i]) & 255; return x; };
const fakeLink = { state: 'idle', supported: false, simulated: false, connected: false, device: null, deviceName: 'x', log: [], frames: 0, lastFrame: null };
const liveOf = (over = {}) => ({ speed: 0, pos: null, gpsErr: false, battery: null, engine: null, rec: null, nav: null, navSpeed: 1, ride: { open: false, auto: false }, call: null, relayName: '', dest: '', turn: { maneuver: 'TURN_RIGHT', m: 400, street: '', exit: 2 }, glove: [], gloveHere: [], docUrls: {}, ...over });

test('TBT frames never throw and always checksum, whatever the input', () => {
  const vals = [0, 1, 99, 100, 999, 1000, 1001, 65535, 999999, 70_000_000, -5, NaN, Infinity, 0.4];
  for (const step of vals) for (const total of vals) {
    const f = P.buildTbtFrame({ maneuverIcon: 74, stepMeters: step, totalMeters: total, etaHour: 13, etaMinute: 75, roundaboutExit: 9, streetText: 'हिंदी रोड — NH 44 🚦' });
    assert.equal(f.length, 48); assert.equal(f[47], sum(f, 47));
    assert.ok(f[14] <= 31);
  }
});

test('Non-ASCII caller names and plates are cleaned, never crash', () => {
  for (const name of ['', '   ', '🏍️', 'प्रिया', 'Dr. Venkataraghavan Subramaniam Iyer of Thiruvananthapuram', null, undefined]) {
    const g = P.buildGeneralPacketV2({ batteryPercent: 150, callState: 1, callerName: name, missedCallCount: 999, volumeLevel: 99, signalBars: -3 });
    assert.equal(g.length, 89); assert.equal(g[88], sum(g, 88));
    assert.ok(g[19] <= 32); assert.equal(g[13], 255);
    for (let i = 20; i < 20 + g[19]; i++) assert.ok(g[i] >= 0x20 && g[i] <= 0x7e);
    assert.equal(P.buildWhatsAppAlertPacket(name).length, 44);
    assert.equal(P.buildMissedCallPacket(name, '+91 (98450) 12345 ext 9').length, 63);
  }
});

test('Fuel maths survives empty, single, top-ups only, backwards odometer and zero litres', () => {
  const s = S.freshState();
  const cases = [
    [],
    [{ id: 'a', ts: 1, odo: 100, litres: 10, cost: 1000, full: true }],
    [{ id: 'a', ts: 1, odo: 100, litres: 3, cost: 300, full: false }, { id: 'b', ts: 2, odo: 300, litres: 4, cost: 400, full: false }],
    [{ id: 'a', ts: 1, odo: 500, litres: 10, cost: 1000, full: true }, { id: 'b', ts: 2, odo: 400, litres: 10, cost: 1000, full: true }],
    [{ id: 'a', ts: 1, odo: 100, litres: 0, cost: 0, full: true }, { id: 'b', ts: 2, odo: 400, litres: 0, cost: 0, full: true }],
  ];
  for (const fuel of cases) {
    s.fuel = fuel; s.odo = 450;
    const f = S.fuelStats(s);
    for (const k of ['avg', 'lifetime', 'lastTank', 'range', 'litresLeft', 'costPerKm']) assert.ok(f[k] == null || Number.isFinite(f[k]), `${k}=${f[k]}`);
    assert.ok(f.bars == null || (f.bars >= 0 && f.bars <= 6));
  }
});

test('Service reminders with no interval, future dates or a passed milestone stay sane', () => {
  const s = S.freshState(); s.odo = 12000;
  const items = [
    { name: 'x', everyKm: 0, everyDays: 0, lastKm: 0, lastDate: Date.now() },
    { name: 'y', everyKm: 500, everyDays: 0, lastKm: 20000, lastDate: Date.now() },
    { name: 'z', everyKm: 0, everyDays: 30, lastKm: 0, lastDate: Date.now() + 86400000 * 400 },
    { name: 'm', milestone: true, targetKm: 500, everyKm: 5000, everyDays: 365, lastKm: 0, lastDate: 0 },
  ];
  for (const it of items) {
    const st = S.serviceStatus(s, it);
    assert.ok(['ok', 'soon', 'overdue'].includes(st.status));
    assert.ok(st.frac >= 0 && st.frac <= 1);
    assert.equal(typeof V.dueSentence(st), 'string');
  }
});

test('Migration and restore reject junk safely', () => {
  for (const raw of [null, 'text', 42, [], {}, { vehicles: [] }, { vehicles: [{}] }, { settings: { theme: 'carbon', logo: 'night', font: 'playfair' } }]) {
    const s = S.migrate(raw);
    assert.ok(s.vehicles.length >= 1); assert.ok(Number.isFinite(s.odo));
    assert.notEqual(s.settings.theme, 'carbon');
    assert.equal(s.settings.logo, undefined);
  }
  for (const bad of ['', 'not json', '{"format":"kickstart-vault","data":null}', '[]']) assert.throws(() => S.importVault(bad));
});

test('Stats, distance and GPX on empty data', () => {
  assert.deepEqual(S.periodStats([], 0), { km: 0, rides: 0, sec: 0, top: 0 });
  assert.equal(S.haversine([12, 77], [12, 77]), 0);
  assert.ok(S.toGPX({ title: '<&>', pts: [] }).includes('&lt;&amp;&gt;'));
});

const SCREENS = ['today', 'logbook', 'fuel', 'trips', 'route', 'alerts', 'settings', 'profile'];
const SHEETS = (s) => ['quick', 'garage', 'vehicle:new', `vehicle:${s.vehicles[0].id}`, 'inbox', 'sos', 'parking', 'glovebox', 'specs', 'bike', 'log', 'protocol', 'models', 'credits', 'fuel:new', 'fuelprice', 'odo', 'trip:A', 'trip:B', 'svc-edit:new', 'rec:new', 'pick:country', 'erase', 'paste', 'copy',
  ...s.service.map((x) => `svc:${x.id}`), ...s.service.map((x) => `svc-edit:${x.id}`), ...s.trips.map((x) => `trip-detail:${x.id}`), ...s.fuel.map((x) => `fuel:${x.id}`), ...s.records.map((x) => `rec:${x.id}`)];

for (const kind of ['empty', 'edge', 'sample']) {
  test(`Every screen and sheet renders in the ${kind} state`, () => {
    const s = kind === 'sample' ? S.seedSample(S.freshState()) : S.scenario(kind);
    if (kind === 'sample') s.onboarded = true;
    const states = [liveOf(), liveOf({ speed: 230, engine: 120, battery: 3, rec: { start: Date.now() - 7200000, km: 999.99, top: 230, pts: [] }, nav: { mode: 'single', idx: 0, maneuver: 'ROUNDABOUT_LEFT', stepMeters: 50, totalMeters: 1e7, street: 'x', say: 'Turn', exit: 7, eta: new Date() }, call: { state: 'ringing', name: 'प्रिया', since: Date.now() } })];
    for (const live of states) {
      const c = { s, link: fakeLink, live };
      for (const sc of SCREENS) { const h = V[sc](c); assert.ok(h.length > 200, sc); assert.ok(!/undefined|NaN|\[object Object\]/.test(h.replace(/data-[a-z-]+="[^"]*"/g, '')), `${sc} shows a raw value in ${kind}`); }
      for (const key of SHEETS(s)) { const h = V.sheetHTML(c, key); if (h != null) assert.ok(!/>undefined<|>NaN<|\[object Object\]/.test(h), `${key} in ${kind}`); }
      assert.ok(V.rideMode(c).includes('rm-page')); V.tabbar(c, 'today');
      if (live.rec) assert.ok(V.sheetHTML(c, 'rec-end').includes('End this ride'));
    }
  });
}

test('Edge preview: odometer never rounds up, badges cap at 99+, header has no name', () => {
  const s = S.scenario('edge');
  const h = V.today({ s, link: fakeLink, live: liveOf() });
  assert.ok(h.includes('1.99L') && !h.includes('2L<'), 'odometer floors');
  assert.ok(!h.includes('2,00,000'));
  assert.ok(V.tabbar({ s, live: liveOf() }, 'today').includes('class="pip"'));
  const head = h.slice(h.indexOf('pagehead'), h.indexOf('</h1>')); assert.ok(!head.includes('Venkatarag'), 'header has no name'); assert.ok(h.includes('bc-wx'), 'bike card carries weather');
});

test('Short numbers never round up and stay readable', () => {
  V.setLocale('en-IN');
  assert.equal(V.short(199999.9), '1.99 L');
  assert.equal(V.short(45280), '45.2K');
  assert.equal(V.short(9999.9), '9,999');
  assert.equal(V.short(12345678), '1.23 Cr');
  assert.equal(V.short(0), '0');
  assert.equal(V.short(null), '—');
  assert.equal(V.tripv(9999.9), '9,999');
  assert.equal(V.tripv(12.34), '12.3');
  V.setLocale('en-GB');
  assert.equal(V.short(1999999), '1.99M');
  V.setLocale('en-IN');
  assert.equal(V.clip('Long-distance tourer and then some', 18), 'Long-distance tou…');
  assert.equal(V.clip('Short'), 'Short');
});

test('Petrol price lookup: city or state, whole words only, never invents a rate', () => {
  assert.equal(S.fuelLookup('Bengaluru').state, 'Karnataka');
  assert.ok(S.fuelLookup('Pune, Maharashtra').price > 100);
  assert.equal(S.fuelLookup('Dakota'), null);
  assert.equal(S.fuelLookup('', null, undefined), null);
  assert.equal(S.fuelLookup('Gujarat'), null);
  const s = S.migrate(null); s.profile.city = 'Kochi';
  assert.equal(S.fuelPriceOf(s), S.FUEL_PRICES.states.Kerala);
  s.fuelPrice = 99; assert.equal(S.fuelPriceOf(s), 99);
});

test('Every section help key has a sheet', () => {
  const s = S.seedSample(S.migrate(null));
  for (const k of Object.keys(V.HELP)) {
    const h = V.sheetHTML({ s, link: fakeLink, live: liveOf() }, 'help:' + k);
    assert.ok(h && h.includes('<h2>'), k);
  }
});

test('Weather: codes map, hints are actionable, bad payloads throw', () => {
  assert.equal(S.wmo(0).ic, 'wxSun'); assert.equal(S.wmo(0, false).ic, 'wxMoon'); assert.equal(S.wmo(95).t, 'Thunderstorm'); assert.equal(S.wmo(999).ic, 'wxCloud');
  const now = Math.floor(Date.now() / 1000), t = [...Array(13)].map((_, i) => now + i * 3600);
  const mk = (rain, code = 2, extra = {}) => ({ current: { temperature_2m: 30, apparent_temperature: 32, weather_code: code, wind_speed_10m: 10, is_day: 1, ...extra }, hourly: { time: t, temperature_2m: t.map(() => 29), precipitation_probability: t.map((_, i) => rain(i)), weather_code: t.map(() => 2), is_day: t.map(() => 1) } });
  assert.match(S.rideHint(S.parseWeather(mk(() => 5), 'X')), /Dry/);
  assert.match(S.rideHint(S.parseWeather(mk((i) => (i === 4 ? 80 : 5)), 'X')), /Rain likely around/);
  assert.match(S.rideHint(S.parseWeather(mk(() => 5, 63), 'X')), /Raining now/);
  assert.match(S.rideHint(S.parseWeather(mk(() => 5, 1, { wind_speed_10m: 40 }), 'X')), /Gusty/);
  assert.equal(S.rideHint(null), '');
  assert.throws(() => S.parseWeather({}, 'X'));
  for (const st of [{ state: 'need' }, { state: 'loading' }, { state: 'err', msg: 'x' }, { state: 'ok', data: S.parseWeather(mk(() => 5), 'Kochi') }, undefined])
    { const cx = { s: S.migrate(null), live: liveOf({ weather: st }) }; assert.ok(V.weatherHead(cx).includes('wh-ic')); assert.ok(V.weatherSheet(cx).includes('<h2>') || V.weatherSheet(cx).includes('Weather')); }
});

test('Every icon the app draws exists in Hugeicons', async () => {
  const src = (await import('node:fs')).readFileSync(new URL('../js/views.js', import.meta.url), 'utf8') + (await import('node:fs')).readFileSync(new URL('../js/app.js', import.meta.url), 'utf8');
  const used = [...new Set([...src.matchAll(/icon\('([a-zA-Z]+)'/g)].map((m) => m[1]))];
  const hi = (await import('../js/icons-huge.js')).ICONS;
  assert.deepEqual(used.filter((k) => !hi[k]), []);
  assert.ok(V.icon('bike').includes('<path') && V.icon('bt').includes('<path'));
});

test('Help sheets render and the FAQ has answers', () => {
  const s = S.seedSample(S.migrate(null));
  for (const k of ['faq', 'report', 'about']) assert.ok(V.sheetHTML({ s, link: fakeLink, live: liveOf() }, k).match(/<h2|about-sheet/), k);
  assert.ok(V.FAQ.length >= 6 && V.FAQ.every(([q, a]) => q.endsWith('?') || a.length > 20));
});

test('iPhone pairing guide renders with all four steps', () => {
  const s = S.seedSample(S.migrate(null));
  const h = V.sheetHTML({ s, link: fakeLink, live: liveOf() }, 'ios-pair');
  assert.equal((h.match(/class="pstep"/g) || []).length, 4);
  assert.ok(h.includes('apps.apple.com') && h.includes('bluefy://'));
});

test('Support sheet: free ways to help always, UPI only when configured', () => {
  const s = S.seedSample(S.migrate(null));
  const ctx = { s, link: fakeLink, live: liveOf() };
  let h = V.sheetHTML(ctx, 'support');
  assert.ok(h.includes('Support Waylink') && h.includes('Star it on GitHub') && !h.includes('upi://'));
  V.SUPPORT.upi = 'rider@okicici';
  h = V.sheetHTML({ ...ctx, live: liveOf({ supportAmt: 199 }) }, 'support');
  assert.ok(h.includes('upi://pay?pa=rider%40okicici') && h.includes('am=199') && h.includes('cu=INR'));
  V.SUPPORT.upi = '';
});
