// A pretend phone for tests: answers a QR code, uploads data and applies the web's changes,
// following relay/PROTOCOL.md exactly as the Android app does.
import * as C from '../js/wlcrypto.js';
import { applyOp } from '../js/ops.js';

export async function linkPhone(qr, relayBase, device = 'Test phone') {
  const q = C.parseQr(qr);
  const kp = await C.newKeyPair();
  const L = await C.deriveLink(kp.privateKey, q.pub, q.secret);
  const box = C.b64url(await C.seal(L.key, L.linkId, 'hello', { device, platform: 'android', at: Date.now() }));
  const r = await fetch(`${relayBase}/v1/pair/${q.pairId}`, { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ pub: kp.pub, box }) });
  if (r.status !== 201) throw new Error('pair ' + r.status);
  const auth = { authorization: 'Bearer ' + L.token };
  let ackSeq = 0;
  return {
    ...L,
    async push(state) {
      const body = await C.seal(L.key, L.linkId, 'vault', { format: 'waylink-sync', v: 1, state, ackSeq, device, at: Date.now() });
      const res = await fetch(`${relayBase}/v1/link/${L.linkId}/vault`, { method: 'PUT', headers: auth, body });
      if (!res.ok) throw new Error('push ' + res.status);
    },
    async applyOps(state) {
      const res = await fetch(`${relayBase}/v1/link/${L.linkId}/ops?after=${ackSeq}`, { headers: auth });
      const { ops } = await res.json();
      for (const o of ops) { applyOp(state, await C.open(L.key, L.linkId, 'op', C.unb64url(o.data))); ackSeq = o.seq; }
      await this.push(state);
      await fetch(`${relayBase}/v1/link/${L.linkId}/ops?upto=${ackSeq}`, { method: 'DELETE', headers: auth });
      return ops.length;
    },
    async unlink() { await fetch(`${relayBase}/v1/link/${L.linkId}`, { method: 'DELETE', headers: auth }); },
  };
}
