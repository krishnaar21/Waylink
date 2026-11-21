// The web side of the link: show a QR code, wait for the phone, then keep a decrypted copy of
// the phone's data here and send changes back as encrypted ops. See relay/PROTOCOL.md.
import * as C from './wlcrypto.js';
import { applyOp } from './ops.js';

// Where the relay lives. A page served from localhost talks to the local dev relay.
export const RELAY = /^(localhost|127\.0\.0\.1)$/.test(location.hostname)
  ? `http://${location.hostname}:8787` : 'https://waylink-relay.krishnaar-work.workers.dev';

// ---------- Saved link (IndexedDB: the AES key is stored as a non-extractable CryptoKey) ----------
const DB = 'waylink-web', ST = 'kv';
function db() {
  return new Promise((ok, no) => {
    const r = indexedDB.open(DB, 1);
    r.onupgradeneeded = () => r.result.createObjectStore(ST);
    r.onsuccess = () => ok(r.result); r.onerror = () => no(r.error);
  });
}
async function idb(mode, fn) {
  const d = await db();
  return new Promise((ok, no) => { const t = d.transaction(ST, mode); const req = fn(t.objectStore(ST)); t.oncomplete = () => ok(req?.result); t.onerror = () => no(t.error); });
}
const get = (k) => idb('readonly', (s) => s.get(k));
const put = (k, v) => idb('readwrite', (s) => s.put(v, k));
const wipe = () => idb('readwrite', (s) => s.clear());

// ---------- Relay calls ----------
async function call(path, { method = 'GET', token, body, etag, timeout = 15000 } = {}) {
  const a = new AbortController(); const t = setTimeout(() => a.abort(), timeout);
  try {
    const headers = {};
    if (token) headers.authorization = 'Bearer ' + token;
    if (etag) headers['if-none-match'] = etag;
    if (body && !(body instanceof Uint8Array)) { headers['content-type'] = 'application/json'; body = JSON.stringify(body); }
    return await fetch(RELAY + path, { method, headers, body, signal: a.signal, cache: 'no-store' });
  } finally { clearTimeout(t); }
}

// ---------- State held by the page ----------
export const link = {
  status: 'loading', // loading | unlinked | pairing | linked
  info: null, // { linkId, token, key, device, linkedAt }
  base: null, // the phone's state, as last received
  ackSeq: 0, // ops the phone has applied
  pending: [], // { seq, op } sent from here, not yet applied by the phone
  updated: 0, // when the phone last sent its data
  checked: 0, // when we last asked the relay
  error: '',
  online: true,
};
const listeners = new Set();
export const onChange = (fn) => listeners.add(fn);
const emit = () => listeners.forEach((fn) => { try { fn(link); } catch (e) { console.error(e); } });

/** The data to show: the phone's copy with our unapplied changes on top. */
export function view() {
  if (!link.base) return null;
  const s = structuredClone(link.base);
  for (const p of link.pending) applyOp(s, p.op);
  return s;
}

export async function restore() {
  try {
    const info = await get('link');
    if (!info?.key) { link.status = 'unlinked'; return emit(); }
    Object.assign(link, { info, status: 'linked', base: (await get('base')) || null, ackSeq: (await get('ackSeq')) || 0, pending: (await get('pending')) || [], updated: (await get('updated')) || 0 });
    emit();
    await pull();
  } catch (e) {
    console.error(e); link.status = 'unlinked'; link.error = 'This browser blocks saved data, so Waylink can’t stay linked here.'; emit();
  }
}

// ---------- Pairing ----------
let pairing = null;
/** Starts a fresh QR code. `onQr(text, expiresAt)` is called with what to draw. */
export async function startPairing(onQr) {
  stopPairing();
  const kp = await C.newKeyPair();
  const p = { pairId: C.randomId(16), secret: C.randomId(16), kp, live: true, expires: Date.now() + 9.5 * 60e3 };
  pairing = p;
  link.status = 'pairing'; link.error = ''; emit();
  onQr(C.qrText({ pairId: p.pairId, secret: p.secret, pub: kp.pub, relay: new URL(RELAY).host }), p.expires);
  let wait = 1500;
  while (p.live && Date.now() < p.expires) {
    try {
      const r = await call('/v1/pair/' + p.pairId, { timeout: 10000 });
      link.online = true;
      if (r.status === 200) { const hello = await r.json(); if (p.live) await finishPairing(p, hello); return; }
      if (r.status === 410) break;
      wait = 1500;
    } catch { link.online = false; emit(); wait = Math.min(8000, wait * 1.6); }
    await new Promise((ok) => setTimeout(ok, wait));
  }
  if (p.live) { p.live = false; link.status = 'unlinked'; link.error = 'expired'; emit(); }
}
export function stopPairing() { if (pairing) pairing.live = false; pairing = null; }

async function finishPairing(p, hello) {
  p.live = false; pairing = null;
  try {
    const { key, token, linkId } = await C.deriveLink(p.kp.privateKey, hello.pub, p.secret);
    const h = await C.open(key, linkId, 'hello', C.unb64url(hello.box)); // proves the phone read our QR code
    const info = { linkId, token, key, device: String(h.device || 'your phone').slice(0, 60), linkedAt: Date.now() };
    await wipe(); await put('link', info);
    Object.assign(link, { info, status: 'linked', base: null, ackSeq: 0, pending: [], updated: 0, error: '' });
    emit();
    // The phone sends its first copy right after scanning: ask every 2 s until it's here.
    for (let i = 0; i < 150 && link.info === info && !link.base; i++) { await pull(true); if (!link.base) await new Promise((ok) => setTimeout(ok, 2000)); }
  } catch (e) {
    console.error(e); link.status = 'unlinked'; link.error = 'That didn’t match. Show a new code and scan again.'; emit();
  }
}

// ---------- Pull the phone's data ----------
let etag = null, pulling = null;
export function pull(force = false) {
  if (pulling) return pulling;
  pulling = (async () => {
    const i = link.info; if (!i) return;
    try {
      const r = await call(`/v1/link/${i.linkId}/vault`, { token: i.token, etag: force ? null : etag });
      link.online = true; link.checked = Date.now();
      if (r.status === 401 || r.status === 410) return unlinked('Your phone unlinked this computer.');
      if (r.status === 204 || r.status === 304) return;
      if (!r.ok) throw new Error('relay ' + r.status);
      const box = await C.open(i.key, i.linkId, 'vault', new Uint8Array(await r.arrayBuffer()));
      if (!box?.state?.vehicles?.length) throw new Error('No data in the vault');
      etag = r.headers.get('etag');
      link.base = box.state; link.ackSeq = +box.ackSeq || 0; link.updated = +box.at || Date.now();
      if (box.device) i.device = String(box.device).slice(0, 60);
      link.pending = link.pending.filter((p) => p.seq > link.ackSeq);
      await put('base', link.base); await put('ackSeq', link.ackSeq); await put('pending', link.pending); await put('updated', link.updated); await put('link', i);
    } catch (e) {
      if (e.name === 'AbortError' || e instanceof TypeError) link.online = false; else console.error(e);
    } finally { pulling = null; emit(); }
  })();
  return pulling;
}

// ---------- Send a change ----------
export async function send(op) {
  const i = link.info; if (!i) throw new Error('Not linked');
  op = { id: crypto.randomUUID(), at: Date.now(), ...op };
  const body = await C.seal(i.key, i.linkId, 'op', op);
  const r = await call(`/v1/link/${i.linkId}/ops`, { method: 'POST', token: i.token, body });
  if (r.status === 401 || r.status === 410) { await unlinked('Your phone unlinked this computer.'); throw new Error('unlinked'); }
  if (!r.ok) { const j = await r.json().catch(() => ({})); throw new Error(j.error || 'Couldn’t save that. Check your connection.'); }
  const { seq } = await r.json();
  link.pending.push({ seq, op }); await put('pending', link.pending); emit();
  return seq;
}

// ---------- Unlink ----------
async function unlinked(msg) {
  stopPairing(); await wipe().catch(() => {}); etag = null;
  Object.assign(link, { status: 'unlinked', info: null, base: null, pending: [], ackSeq: 0, updated: 0, error: msg || '' });
  emit();
}
export async function unlink() {
  const i = link.info;
  if (i) { try { await call(`/v1/link/${i.linkId}`, { method: 'DELETE', token: i.token }); } catch {} }
  await unlinked('');
}

// ---------- Keep fresh: on focus, coming back online, and every 30 s while visible ----------
export function keepFresh() {
  const tick = () => { if (link.status === 'linked' && document.visibilityState === 'visible') pull(); };
  document.addEventListener('visibilitychange', tick);
  addEventListener('online', tick); addEventListener('focus', tick);
  setInterval(tick, 30e3);
}
