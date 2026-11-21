// Checks a deployed relay end to end with the web app's own crypto and a pretend phone:
// pair by QR, upload, a change from the web, the phone applying it, unlink. Usage: node relay/smoke.mjs https://<relay>
import * as C from '../web/js/wlcrypto.js';
import { linkPhone } from '../web/tests/phone.mjs';
const B = process.argv[2]; if (!B) throw new Error('relay URL?');
const ORIGIN = { origin: 'https://krishnaar21.github.io' };
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const ok = (c, m) => { if (!c) { console.error('FAIL', m); process.exit(1); } console.log('ok  ', m); };

const kp = await C.newKeyPair(); const pairId = C.randomId(), secret = C.randomId();
ok((await fetch(`${B}/v1/pair/${pairId}`, { headers: ORIGIN })).status === 204, 'code is waiting for a phone');
const qr = C.qrText({ pairId, secret, pub: kp.pub, relay: new URL(B).host });
const phone = await linkPhone(qr, B, 'Smoke phone');
const r = await fetch(`${B}/v1/pair/${pairId}`, { headers: ORIGIN });
ok(r.status === 200 && r.headers.get('access-control-allow-origin') === ORIGIN.origin, 'web sees the answer (CORS allowed)');
const hello = await r.json();
const L = await C.deriveLink(kp.privateKey, hello.pub, secret);
ok((await C.open(L.key, L.linkId, 'hello', C.unb64url(hello.box))).device === 'Smoke phone', 'both sides derived the same key');
ok((await fetch(`${B}/v1/pair/${pairId}`, { headers: ORIGIN })).status !== 200, 'code works only once');

const state = { active: 'v1', profile: { name: 'Smoke' }, vehicles: [{ id: 'v1', odo: 100, fuel: [], records: [], service: [], checklist: [], trips: [] }] };
await phone.push(state);
const auth = { authorization: 'Bearer ' + L.token, ...ORIGIN };
const vault = async () => C.open(L.key, L.linkId, 'vault', new Uint8Array(await (await fetch(`${B}/v1/link/${L.linkId}/vault`, { headers: auth })).arrayBuffer()));
ok((await vault()).state.vehicles[0].odo === 100, 'web opens the phone’s upload');
ok((await fetch(`${B}/v1/link/${L.linkId}/vault`, { headers: { authorization: 'Bearer ' + C.randomId(32) } })).status === 401, 'a wrong token is refused');
const op = { id: 'o1', at: Date.now(), op: 'odo', vid: 'v1', km: 150 };
ok((await fetch(`${B}/v1/link/${L.linkId}/ops`, { method: 'POST', headers: auth, body: await C.seal(L.key, L.linkId, 'op', op) })).status === 201, 'web sends a change');
ok(await phone.applyOps(state) === 1 && state.vehicles[0].odo === 150, 'phone applies it');
const v = await vault();
ok(v.ackSeq === 1 && v.state.vehicles[0].odo === 150, 'web sees the phone caught up');
await phone.unlink(); await wait(500);
ok((await fetch(`${B}/v1/link/${L.linkId}/vault`, { headers: auth })).status === 410, 'unlink wipes the link');
