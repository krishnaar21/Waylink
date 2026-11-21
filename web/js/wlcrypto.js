// Waylink link crypto, shared by the web app and the tests (plain Web Crypto, no libraries).
// The full protocol, which the Android app follows byte for byte, is in relay/PROTOCOL.md.
const enc = new TextEncoder(), dec = new TextDecoder();
const subtle = globalThis.crypto.subtle;

export function b64url(bytes) {
  let s = '';
  for (const b of new Uint8Array(bytes)) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
export function unb64url(s) {
  const bin = atob(String(s).replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((String(s).length + 3) % 4));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}
export const randomId = (n = 16) => b64url(crypto.getRandomValues(new Uint8Array(n)));

/** A fresh P-256 key pair; the public half as 65 raw bytes in base64url. */
export async function newKeyPair() {
  const kp = await subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, false, ['deriveBits']);
  return { privateKey: kp.privateKey, pub: b64url(await subtle.exportKey('raw', kp.publicKey)) };
}

/**
 * Both sides end up with the same two secrets:
 *   bits  = ECDH(own private, other public)               32 bytes
 *   okm   = HKDF-SHA256(ikm = bits, salt = QR secret, info = "waylink-link-v1", 64 bytes)
 *   key   = okm[0..32)  AES-256-GCM key for everything sent through the relay
 *   token = okm[32..64) bearer token for the relay; linkId = base64url(SHA-256(token))[0..32)
 * The QR secret never goes to the relay, so the relay can't stand in for either side.
 */
export async function deriveLink(privateKey, otherPub, qrSecret) {
  const pub = await subtle.importKey('raw', unb64url(otherPub), { name: 'ECDH', namedCurve: 'P-256' }, false, []);
  const bits = await subtle.deriveBits({ name: 'ECDH', public: pub }, privateKey, 256);
  const ikm = await subtle.importKey('raw', bits, 'HKDF', false, ['deriveBits']);
  const okm = new Uint8Array(await subtle.deriveBits({ name: 'HKDF', hash: 'SHA-256', salt: unb64url(qrSecret), info: enc.encode('waylink-link-v1') }, ikm, 512));
  const key = await subtle.importKey('raw', okm.slice(0, 32), 'AES-GCM', false, ['encrypt', 'decrypt']);
  const token = b64url(okm.slice(32));
  return { key, token, linkId: await linkIdOf(token) };
}
export async function linkIdOf(token) {
  return b64url(await subtle.digest('SHA-256', unb64url(token))).slice(0, 32);
}

/** AES-256-GCM. Output: 12-byte IV then ciphertext and tag. AAD = "<linkId>:<kind>", so a blob can't be replayed as another kind or onto another link. */
export async function seal(key, linkId, kind, obj) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ct = new Uint8Array(await subtle.encrypt({ name: 'AES-GCM', iv, additionalData: enc.encode(`${linkId}:${kind}`) }, key, enc.encode(JSON.stringify(obj))));
  const out = new Uint8Array(12 + ct.length); out.set(iv); out.set(ct, 12);
  return out;
}
export async function open(key, linkId, kind, bytes) {
  const b = new Uint8Array(bytes);
  if (b.length < 29) throw new Error('Too short');
  const pt = await subtle.decrypt({ name: 'AES-GCM', iv: b.slice(0, 12), additionalData: enc.encode(`${linkId}:${kind}`) }, key, b.slice(12));
  return JSON.parse(dec.decode(pt));
}

/** What the QR code holds. v=1; p = pairing id; s = QR secret; k = browser's public key; r = relay host. */
export const qrText = ({ pairId, secret, pub, relay }) => `waylink://link?v=1&p=${pairId}&s=${secret}&k=${pub}&r=${encodeURIComponent(relay)}`;
export function parseQr(text) {
  const u = new URL(text);
  if (u.protocol !== 'waylink:' || u.searchParams.get('v') !== '1') throw new Error('Not a Waylink code');
  const g = (k) => u.searchParams.get(k) || '';
  return { pairId: g('p'), secret: g('s'), pub: g('k'), relay: g('r') };
}
