// Unit tests: run with `node --test tests/` (Node 18+). No dependencies.
import test from 'node:test';
import assert from 'node:assert/strict';
import * as P from '../js/protocol.js';
import * as S from '../js/store.js';

const sum = (b, n) => { let x = 0; for (let i = 0; i < n; i++) x = (x + b[i]) & 255; return x; };

test('TBT frame: 48 bytes, byte map and checksum match OtcPacketBuilder.kt', () => {
  const f = P.buildTbtFrame({ maneuverIcon: 88, isAm: false, stepMeters: 7900, totalMeters: 34400, etaHour: 8, etaMinute: 59, streetText: 'NH 44' });
  assert.equal(f.length, 48);
  assert.deepEqual([...f.slice(0, 15)], [0x81, 88, 90, 0, 7, 0, 59, 8, 39, 0, 34, 0, 4, 0, 5]);
  assert.equal(String.fromCharCode(...f.slice(15, 20)), 'NH 44');
  assert.equal(f[47], sum(f, 47));
});

test('TBT frame: arrow blinks (+32) under 100 m, but not for destination', () => {
  assert.equal(P.buildTbtFrame({ maneuverIcon: 74, stepMeters: 80, totalMeters: 500 })[1], 106);
  assert.equal(P.buildTbtFrame({ maneuverIcon: 72, stepMeters: 50, totalMeters: 50 })[1], 72);
  assert.equal(P.buildTbtFrame({ maneuverIcon: 74, stepMeters: 100, totalMeters: 500 })[1], 74);
});

test('TBT frame: metres below 1 km, km with 2 decimals above', () => {
  const m = P.buildTbtFrame({ maneuverIcon: 71, stepMeters: 450, totalMeters: 1250 });
  assert.equal((m[0] >> 4) & 1, 1); assert.equal(m[5] * 256 + m[4], 450);
  assert.equal(m[12] & 1, 0); assert.equal(m[10], 1); assert.equal(m[8], 25);
});

test('TBT frame: street text is cleaned and capped at 31 characters', () => {
  const f = P.buildTbtFrame({ maneuverIcon: 71, stepMeters: 10, totalMeters: 10, streetText: 'Hosur Road — Electronic City Phase 1 Flyover' });
  assert.equal(f[14], 31);
  assert.ok(/^[0-9A-Za-z. ]+$/.test(String.fromCharCode(...f.slice(15, 46))));
});

test('Roundabout exit sits in the high nibble of byte 7', () => {
  const f = P.buildTbtFrame({ maneuverIcon: 78, stepMeters: 300, totalMeters: 900, etaHour: 9, roundaboutExit: 2 });
  assert.equal(f[7], (2 << 4) | 9);
});

test('Stop frame clears navigation (type 0) with a valid checksum', () => {
  const f = P.buildTbtStopFrame();
  assert.equal(f[0] & 1, 0); assert.equal(f[47], sum(f, 47));
});

test('Status packet V2: 89 bytes, caller padding, missed count, checksum', () => {
  const g = P.buildGeneralPacketV2({ batteryPercent: 80, callState: P.CallState.INCOMING_CALL, callerName: 'Amma', missedCallCount: 2, phoneModel: 'Pixel 8' });
  assert.equal(g.length, 89);
  assert.equal(g[0], 0xc8); assert.equal(g[1], (3 << 4) | 1);
  assert.equal(String.fromCharCode(...g.slice(20, 20 + g[19])), 'Amma   ');
  assert.equal(g[13], 2); assert.equal(g[88], sum(g, 88));
});

test('Legacy status, WhatsApp and missed-call packets have the right sizes', () => {
  assert.equal(P.buildGeneralPacketLegacy({ batteryPercent: 50, callState: 3, callerName: 'Rohan' }).length, 55);
  const w = P.buildWhatsAppAlertPacket('Priya'); assert.equal(w.length, 44); assert.equal(w[0], 2); assert.equal(w[1], 5);
  const m = P.buildMissedCallPacket('Rohan', '+91 98450-12345'); assert.equal(m.length, 63); assert.equal(String.fromCharCode(...m.slice(35, 35 + m[34])), '+919845012345');
});

test('Sample garage: two bikes, data kept apart, accessors follow the active bike', () => {
  const s = S.freshState(); S.seedSample(s);
  assert.equal(s.vehicles.length, 2);
  assert.equal(S.vehicleTitle(s), 'Daily'); assert.equal(s.odo, 12261);
  const daily = s.trips.length;
  s.active = s.vehicles[1].id;
  assert.equal(S.bikeName(s), 'Dominar 400'); assert.equal(s.odo, 8420);
  assert.notEqual(s.trips.length, daily);
  S.addDistance(s, 10);
  assert.equal(s.vehicles[1].odo, 8430); assert.equal(s.vehicles[0].odo, 12261);
});

test('Saved JSON holds vehicle data once, not duplicated at the top level', () => {
  const s = S.freshState(); S.seedSample(s);
  const j = JSON.parse(JSON.stringify(s));
  assert.ok(!('odo' in j) && !('fuel' in j));
  assert.equal(j.vehicles[0].odo, 12261);
});

test('Fuel: full-tank-to-full-tank mileage, range and reserve', () => {
  const s = S.freshState(); S.seedSample(s);
  const f = S.fuelStats(s);
  assert.equal(f.tanks.length, 6);
  assert.ok(Math.abs(f.avg - 29.58) < 0.05, f.avg);
  assert.ok(Math.abs(f.litresLeft - 9.13) < 0.05);
  assert.ok(f.range > 260 && f.range < 280);
  assert.equal(f.bars, 4); assert.equal(f.onReserve, false);
  s.odo = 12176 + 300; // ride far past the last fill
  assert.equal(S.fuelStats(s).onReserve, true);
});

test('Fuel: a top-up rolls into the next full tank', () => {
  const s = S.freshState();
  s.fuel = [{ id: 'a', ts: 1, odo: 1000, litres: 10, cost: 1, full: true }, { id: 'b', ts: 2, odo: 1150, litres: 3, cost: 1, full: false }, { id: 'c', ts: 3, odo: 1300, litres: 7, cost: 1, full: true }];
  const f = S.fuelStats(s);
  assert.equal(f.tanks.length, 1); assert.equal(f.tanks[0].kmpl, 30);
});

test('Logbook: overdue, due soon and on track, whichever comes first', () => {
  const s = S.freshState(); S.seedSample(s);
  const by = (k) => S.serviceStatus(s, s.service.find((i) => i.kind === k));
  assert.equal(by('chain').status, 'overdue'); assert.equal(Math.round(by('chain').overdueKm), 106);
  assert.equal(by('brakes').status, 'soon'); assert.equal(by('brakes').byTime, true);
  assert.equal(by('oil').status, 'ok'); assert.equal(by('oil').remKm, 613);
});

test('Logbook: Bajaj service milestones advance 500 → 5,000 → 10,000 → +5,000', () => {
  const s = S.freshState();
  const it = s.service.find((i) => i.milestone);
  assert.equal(it.targetKm, 500);
  S.markServiceDone(s, it); assert.equal(it.targetKm, 5000);
  S.markServiceDone(s, it); assert.equal(it.targetKm, 10000);
  s.odo = 10020; S.markServiceDone(s, it); assert.equal(it.targetKm, 15020);
});

test('Migration: a v1 single-bike save becomes a one-bike garage', () => {
  const v1 = { v: 1, onboarded: true, profile: { name: 'Arjun', model: 'n250', plate: 'KA 01 AB 1234', country: 'IN' }, settings: { theme: 'paper' }, odo: 4321, fuel: [{ id: 'x', ts: 1, odo: 4000, litres: 10, cost: 1000, full: true }], trips: [], service: [] };
  const s = S.migrate(v1);
  assert.equal(s.vehicles.length, 1); assert.equal(s.odo, 4321); assert.equal(S.veh(s).model, 'n250');
  assert.equal(S.veh(s).plate, 'KA 01 AB 1234'); assert.equal(s.fuel.length, 1); assert.equal(s.settings.theme, 'auto');
  assert.ok(!('model' in s.profile));
});

test('Backup round-trip keeps every bike and the profile, drops paired devices', () => {
  const s = S.freshState(); S.seedSample(s); s.vehicles[0].bike = { id: 'dev1', name: 'Cluster' };
  const back = S.importVault(S.exportVault(s));
  assert.equal(back.vehicles.length, 2); assert.equal(back.profile.name, 'Arjun');
  assert.equal(back.vehicles[0].bike.id, null); assert.equal(back.onboarded, true);
  assert.throws(() => S.importVault('{"hello":1}'), /backup/);
});

test('GPX export is valid-looking XML with every point', () => {
  const s = S.freshState(); S.seedSample(s);
  const t = s.trips[0]; const gpx = S.toGPX(t);
  assert.ok(gpx.startsWith('<?xml')); assert.equal((gpx.match(/<trkpt/g) || []).length, t.pts.length);
});
