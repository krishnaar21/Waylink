// Waylink — OTC Engineering cluster protocol.
// Byte-for-byte port of app/src/main/java/com/pulsarnav/app/protocol/OtcPacketBuilder.kt
// and model/Maneuver.kt. Do not change layouts here without verifying on a cluster.

export const UUID = {
  service: '0010676e-6972-6565-6e69-676e4543544f',
  tbt: '0110676e-6972-6565-6e69-676e4543544f',      // 48 B turn-by-turn, write w/o response
  general: '0210676e-6972-6565-6e69-676e4543544f',  // 89 B status heartbeat (+55 B legacy)
  missedCall: '0310676e-6972-6565-6e69-676e4543544f', // 63 B missed call
  alerts: '0410676e-6972-6565-6e69-676e4543544f',   // 44 B WhatsApp alert
  buttons: '0a10676e-6972-6565-6e69-676e4543544f',  // handlebar buttons, read/poll
};

export const SIZES = { TBT: 48, GENERAL: 89, LEGACY: 55, ALERT: 44, MISSED: 63 };
export const TIMING = { TBT_STREAM_MS: 800, HEARTBEAT_MS: 5000, BUTTON_POLL_MS: 700, RECONNECT_MS: 4000, SCAN_PAUSE_MS: 15000, ALERT_HOLD_MS: 6000 };

export const CallState = { NO_CALL: 0, INCOMING_CALL: 1, OUTGOING_CALL: 2, ACTIVE_CALL: 3, END_CALL: 4 };
export const AlertType = { SMS: 1, WHATSAPP: 2 };

// Verified console icon opcodes. `glyph` drives the on-screen arrow drawing.
export const MANEUVERS = [
  { key: 'STRAIGHT', op: 71, label: 'Continue straight', glyph: 'straight' },
  { key: 'TURN_LEFT', op: 73, label: 'Turn left', glyph: 'left' },
  { key: 'TURN_RIGHT', op: 74, label: 'Turn right', glyph: 'right' },
  { key: 'TURN_SLIGHT_LEFT', op: 67, label: 'Slight left', glyph: 'slight-left' },
  { key: 'TURN_SLIGHT_RIGHT', op: 68, label: 'Slight right', glyph: 'slight-right' },
  { key: 'TURN_SHARP_LEFT', op: 69, label: 'Sharp left', glyph: 'sharp-left' },
  { key: 'TURN_SHARP_RIGHT', op: 70, label: 'Sharp right', glyph: 'sharp-right' },
  { key: 'KEEP_LEFT', op: 90, label: 'Keep left', glyph: 'keep-left' },
  { key: 'KEEP_RIGHT', op: 88, label: 'Keep right', glyph: 'keep-right' },
  { key: 'FORK_LEFT', op: 81, label: 'Fork left', glyph: 'fork-left' },
  { key: 'FORK_RIGHT', op: 82, label: 'Fork right', glyph: 'fork-right' },
  { key: 'RAMP_LEFT', op: 75, label: 'Ramp left', glyph: 'ramp-left' },
  { key: 'RAMP_RIGHT', op: 76, label: 'Ramp right', glyph: 'ramp-right' },
  { key: 'MERGE', op: 86, label: 'Merge', glyph: 'merge' },
  { key: 'ROUNDABOUT_LEFT', op: 85, label: 'Roundabout left', glyph: 'roundabout-left' },
  { key: 'ROUNDABOUT_RIGHT', op: 78, label: 'Roundabout right', glyph: 'roundabout-right' },
  { key: 'U_TURN_LEFT', op: 79, label: 'U-turn left', glyph: 'uturn-left' },
  { key: 'U_TURN_RIGHT', op: 80, label: 'U-turn right', glyph: 'uturn-right' },
  { key: 'DESTINATION_REACHED', op: 72, label: 'Destination', glyph: 'destination' },
  { key: 'FERRY', op: 89, label: 'Ferry', glyph: 'ferry' },
  { key: 'FERRY_TRAIN', op: 84, label: 'Ferry train', glyph: 'train' },
  { key: 'WRONG_WAY', op: 66, label: 'Wrong way', glyph: 'wrong-way' },
];
export const maneuverByKey = (k) => MANEUVERS.find((m) => m.key === k) || MANEUVERS[0];

const sum8 = (b, n) => { let s = 0; for (let i = 0; i < n; i++) s = (s + b[i]) & 0xff; return s; };
const ascii = (s) => Uint8Array.from([...s].map((c) => c.charCodeAt(0) & 0x7f));

export function sanitizeAscii(input, max) {
  if (!input || !String(input).trim()) return '';
  const c = String(input).replace(/[^\x20-\x7E]/g, ' ').trim();
  return c.length > max ? c.substring(0, max) : c;
}
function sanitizePhone(p) {
  if (!p || !String(p).trim()) return '';
  const d = [...String(p)].filter((c) => /[0-9+]/.test(c)).join('');
  return d.length > 18 ? d.substring(0, 18) : d;
}

/** [unit, iHigh, iLow, fracHigh, fracLow]; metres below 1000 (unit 1), else km with 2 decimals (unit 0). */
export function formatDistance(d) {
  let v, unit;
  if (d < 1000) { v = Math.trunc(d); unit = 1; } else { v = d / 1000; unit = 0; }
  const i = Math.trunc(v);
  const frac = Math.trunc((v - i) * 100);
  return [unit, (i >> 8) & 0xff, i & 0xff, (frac >> 8) & 0xff, frac & 0xff];
}

/** 48-byte TBT frame. See OtcPacketBuilder.buildTbtFrame for the byte map. */
export function buildTbtFrame({ type = 1, maneuverIcon, isAm = true, stepMeters, totalMeters, etaHour = 12, etaMinute = 0, roundaboutExit = 0, streetText = '' }) {
  const step = formatDistance(Math.max(0, stepMeters));
  const total = formatDistance(Math.max(0, totalMeters));
  let t = String(streetText || '').replace(/[^0-9a-zA-Z.]/g, ' ').trim();
  if (t.length > 31) t = t.substring(0, 31) + '.';
  const b = new Uint8Array(SIZES.TBT);
  let b0 = type & 1;
  b0 |= (step[0] & 1) << 4;
  b0 |= (isAm ? 0 : 1) << 7;
  b[0] = b0 & 0xff;
  let icon = maneuverIcon;
  if (stepMeters >= 0 && stepMeters <= 99 && icon !== 72 && icon < 98) icon += 32; // blink under 100 m
  b[1] = icon & 0xff;
  b[2] = step[4]; b[3] = step[3]; b[4] = step[2]; b[5] = step[1];
  b[6] = etaMinute & 0xff;
  const h = etaHour >= 1 && etaHour <= 12 ? etaHour : 12;
  b[7] = ((h & 0x0f) | ((roundaboutExit & 0x0f) << 4)) & 0xff;
  b[8] = total[4]; b[9] = total[3]; b[10] = total[2]; b[11] = total[1];
  b[12] = ((total[0] & 1) | (1 << 2)) & 0xff; // GPS active
  b[13] = 0;
  if (t.length) { const tb = ascii(t); const n = Math.min(tb.length, 31); b[14] = n; b.set(tb.subarray(0, n), 15); }
  b[46] = 0;
  b[47] = sum8(b, 47);
  return b;
}
export const buildTbtStopFrame = () => buildTbtFrame({ type: 0, maneuverIcon: 0, isAm: true, stepMeters: 0, totalMeters: 0, etaHour: 0, etaMinute: 0, roundaboutExit: 0, streetText: '' });

let heartbeat = 0;
const batBars4 = (p) => (p >= 75 ? 3 : p >= 50 ? 2 : p >= 25 ? 1 : 0);

/** 89-byte General Status (V2), sent after link-up and every 5 s. */
export function buildGeneralPacketV2({ batteryPercent, volumeLevel = 8, signalBars = 4, callState = 0, callerName = null, callAcceptAck = 0, callRejectAck = 0, callRejectSmsAck = 0, missedCallCount = 0, missedCallGetAck = 0, callProgress = 0, phoneModel = 'Web' }) {
  heartbeat = (heartbeat + 1) & 0xff;
  const b = new Uint8Array(SIZES.GENERAL);
  b[0] = ((Math.min(15, Math.max(0, volumeLevel)) & 0x0f) | 0xc0) & 0xff;
  b[1] = (((batBars4(batteryPercent) & 3) << 4) | (callState & 0x0f)) & 0xff;
  b[2] = Math.min(7, Math.max(0, signalBars)) & 7;
  b[3] = 0;
  b[4] = callAcceptAck & 0xff; b[5] = callRejectAck & 0xff; b[6] = callRejectSmsAck & 0xff;
  b[13] = Math.min(255, Math.max(0, missedCallCount));
  b[14] = missedCallGetAck & 0xff;
  b[17] = 0;
  const hasCaller = callState === CallState.INCOMING_CALL || callState === CallState.ACTIVE_CALL;
  if (hasCaller && callerName && String(callerName).trim()) {
    b[18] = 1;
    let clean = sanitizeAscii(callerName, 32);
    if (clean.length && clean.length <= 6) clean = clean + '   '; // stops "DADADAD" scroll
    const bytes = ascii(clean); const len = Math.min(bytes.length, 32);
    b.set(bytes.subarray(0, len), 20); b[19] = len;
  } else b[18] = 0;
  b[52] = callProgress & 0xff; b[53] = (callProgress >> 8) & 0xff;
  b[54] = heartbeat;
  const mb = ascii(sanitizeAscii(phoneModel || 'Android', 32)); const ml = Math.min(mb.length, 32);
  b[55] = ml; b.set(mb.subarray(0, ml), 56);
  b[88] = sum8(b, 88);
  return b;
}

/** 55-byte legacy status, sent alongside V2 during calls for older firmware. */
export function buildGeneralPacketLegacy({ batteryPercent, volumeLevel = 8, callState = 0, callerName = null, callRejectAck = 0, callRejectSmsAck = 0, missedCallCount = 0, missedCallGetAck = 0 }) {
  heartbeat = (heartbeat + 1) & 0xff;
  const b = new Uint8Array(SIZES.LEGACY);
  b[0] = ((Math.min(15, Math.max(0, volumeLevel)) & 0x0f) | 0xc0) & 0xff;
  const p = batteryPercent; const bars = p >= 80 ? 4 : p >= 60 ? 3 : p >= 40 ? 2 : p >= 20 ? 1 : 0;
  b[1] = (((bars & 7) << 3) | (callState & 0x0f)) & 0xff;
  b[2] = 4;
  b[3] = callState === CallState.ACTIVE_CALL ? 1 : 0;
  b[4] = callState === CallState.ACTIVE_CALL ? 0 : 1;
  b[5] = callRejectAck & 0xff; b[6] = callRejectSmsAck & 0xff;
  b[13] = Math.min(255, Math.max(0, missedCallCount)); b[14] = missedCallGetAck & 0xff; b[17] = 0;
  const hasCaller = callState === CallState.INCOMING_CALL || callState === CallState.ACTIVE_CALL;
  if (hasCaller && callerName && String(callerName).trim()) {
    b[18] = 1;
    let clean = sanitizeAscii(callerName, 30);
    if (clean.length && clean.length <= 6) clean = clean + '   ';
    const bytes = ascii(clean); const len = Math.min(bytes.length, 30);
    b.set(bytes.subarray(0, len), 21); b[20] = len;
  } else b[18] = 0;
  b[53] = heartbeat;
  return b;
}

function putEpoch(b, at) {
  const s = Math.floor(Date.now() / 1000);
  b[at] = (s >>> 24) & 0xff; b[at + 1] = (s >>> 16) & 0xff; b[at + 2] = (s >>> 8) & 0xff; b[at + 3] = s & 0xff;
}

/** 44-byte WhatsApp alert — sender name only, never the message. */
export function buildWhatsAppAlertPacket(senderName) {
  const b = new Uint8Array(SIZES.ALERT);
  b[0] = AlertType.WHATSAPP;
  const nb = ascii(sanitizeAscii(senderName, 32)); const n = Math.min(nb.length, 32);
  b[1] = n; b.set(nb.subarray(0, n), 2);
  putEpoch(b, 34);
  return b;
}

/** 63-byte missed call. */
export function buildMissedCallPacket(callerName, callerNumber) {
  const b = new Uint8Array(SIZES.MISSED);
  b[0] = 0x01;
  const nb = ascii(sanitizeAscii(callerName, 32)); const n = Math.min(nb.length, 32);
  b[1] = n; b.set(nb.subarray(0, n), 2);
  const pb = ascii(sanitizePhone(callerNumber)); const pn = Math.min(pb.length, 18);
  b[34] = pn; b.set(pb.subarray(0, pn), 35);
  putEpoch(b, 53);
  return b;
}

export const toHex = (bytes) => Array.from(bytes, (x) => x.toString(16).toUpperCase().padStart(2, '0')).join(' ');
