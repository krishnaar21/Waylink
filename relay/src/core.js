// Waylink relay: the logic for one pairing or one link, independent of where it runs.
// The relay only ever sees encrypted bytes. It can't read rides, fuel or anything else:
// the phone and the browser share a key the relay never learns (see PROTOCOL.md).
//
// Each pairing and each link is its own small object with its own storage, so the
// Cloudflare version gives each one a Durable Object and the local dev server a Map.

export const LIMITS = {
  vault: 4 * 1024 * 1024, // one encrypted copy of the phone's data (photos included)
  chunk: 1900 * 1024, // Durable Object values top out at 2 MB, so the vault is stored in pieces
  op: 64 * 1024, // one change made on the web
  ops: 500, // changes waiting for the phone
  hello: 4 * 1024, // the phone's answer to a QR code
  pairTtl: 10 * 60e3, // a QR code works for 10 minutes
  linkTtl: 120 * 24 * 3600e3, // a link nobody uses for 120 days is wiped
};

const B64URL = /^[A-Za-z0-9_-]+$/;
export const isId = (x, min = 16, max = 64) => typeof x === 'string' && x.length >= min && x.length <= max && B64URL.test(x);

const json = (body, status = 200, headers = {}) => new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json', 'cache-control': 'no-store', ...headers } });
const err = (status, message) => json({ error: message }, status);

export function b64url(bytes) {
  let s = '';
  for (const b of new Uint8Array(bytes)) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
export function unb64url(s) {
  const bin = atob(s.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((s.length + 3) % 4));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

// A link's id is the first 32 characters of base64url(SHA-256(token)). Whoever holds the token
// (the phone and the browser, from the key exchange) can use the link; the relay keeps only the id.
export async function linkIdOf(token) {
  return b64url(await crypto.subtle.digest('SHA-256', unb64url(token))).slice(0, 32);
}
async function authorised(req, linkId) {
  const m = /^Bearer ([A-Za-z0-9_-]{40,64})$/.exec(req.headers.get('authorization') || '');
  if (!m) return false;
  const id = await linkIdOf(m[1]);
  // Constant-time compare: the ids are the same length when they could match.
  if (id.length !== linkId.length) return false;
  let d = 0;
  for (let i = 0; i < id.length; i++) d |= id.charCodeAt(i) ^ linkId.charCodeAt(i);
  return d === 0;
}

async function readBody(req, max) {
  const len = +req.headers.get('content-length');
  if (len > max) return null;
  const buf = new Uint8Array(await req.arrayBuffer());
  return buf.length > max ? null : buf;
}

// ---------- Pairing: the browser shows a QR code, the phone answers it once ----------
// POST from the phone: { pub, box } (its public key and an encrypted hello). GET from the browser polls for it.
export async function pair(req, store, now = Date.now()) {
  const made = await store.get('made');
  if (made && now - made > LIMITS.pairTtl) { await store.deleteAll(); return err(410, 'This code has expired. Show a new one.'); }
  if (req.method === 'GET') {
    const hello = await store.get('hello');
    if (!hello) { if (!made) await store.put('made', now); return new Response(null, { status: 204, headers: { 'cache-control': 'no-store' } }); }
    await store.deleteAll(); // read once, then gone
    return json(hello);
  }
  if (req.method === 'POST') {
    if (await store.get('hello')) return err(409, 'This code has already been used.');
    const raw = await readBody(req, LIMITS.hello);
    if (!raw) return err(413, 'Too big.');
    let hello;
    try { hello = JSON.parse(new TextDecoder().decode(raw)); } catch { return err(400, 'Not JSON.'); }
    if (!isId(hello?.pub, 80, 100) || !isId(hello?.box, 20, 3000)) return err(400, 'Missing pub or box.');
    await store.put('hello', { pub: hello.pub, box: hello.box });
    if (!made) await store.put('made', now);
    return json({ ok: true }, 201);
  }
  return err(405, 'Use GET or POST.');
}

// ---------- A link: the phone's encrypted data, and changes made on the web waiting for the phone ----------
export async function link(req, store, linkId, rest, now = Date.now()) {
  if (!(await authorised(req, linkId))) return err(401, 'Not linked.');
  const seen = await store.get('seen');
  if (seen && now - seen > LIMITS.linkTtl) { await store.deleteAll(); return err(410, 'This link expired. Scan a new code.'); }
  if (req.method === 'DELETE' && rest === '') { await store.deleteAll(); await store.put('gone', now); return json({ ok: true }); }
  if (await store.get('gone')) return err(410, 'This computer was unlinked.');
  await store.put('seen', now);

  if (rest === '/vault') {
    if (req.method === 'GET') {
      const meta = await store.get('vault');
      if (!meta) return new Response(null, { status: 204, headers: { 'cache-control': 'no-store' } });
      const etag = `"${meta.ver}"`;
      if (req.headers.get('if-none-match') === etag) return new Response(null, { status: 304, headers: { etag, 'cache-control': 'no-store' } });
      const parts = [];
      for (let i = 0; i < meta.parts; i++) parts.push(await store.get('vault:' + i));
      const body = new Uint8Array(meta.size); let o = 0;
      for (const p of parts) { body.set(p, o); o += p.length; }
      return new Response(body, { headers: { 'content-type': 'application/octet-stream', etag, 'x-updated': String(meta.at), 'cache-control': 'no-store' } });
    }
    if (req.method === 'PUT') {
      const body = await readBody(req, LIMITS.vault);
      if (!body) return err(413, 'Your data is over 4 MB. Remove some photos and try again.');
      if (body.length < 29) return err(400, 'Too small to be encrypted data.');
      const old = await store.get('vault');
      const parts = Math.ceil(body.length / LIMITS.chunk);
      for (let i = 0; i < parts; i++) await store.put('vault:' + i, body.slice(i * LIMITS.chunk, (i + 1) * LIMITS.chunk));
      for (let i = parts; i < (old?.parts || 0); i++) await store.delete('vault:' + i);
      const meta = { ver: (old?.ver || 0) + 1, size: body.length, parts, at: now };
      await store.put('vault', meta);
      return json({ ver: meta.ver }, 200, { etag: `"${meta.ver}"` });
    }
    return err(405, 'Use GET or PUT.');
  }

  if (rest === '/ops') {
    const seq = (await store.get('seq')) || 0;
    const list = (await store.get('ops')) || [];
    if (req.method === 'POST') {
      const body = await readBody(req, LIMITS.op);
      if (!body || body.length < 29) return err(413, 'That change is too big.');
      if (list.length >= LIMITS.ops) return err(429, 'Too many changes waiting. Open Waylink on your phone to sync them.');
      const n = seq + 1;
      list.push({ seq: n, data: b64url(body), at: now });
      await store.put('ops', list); await store.put('seq', n);
      return json({ seq: n }, 201);
    }
    if (req.method === 'GET') {
      const after = +new URL(req.url).searchParams.get('after') || 0;
      return json({ ops: list.filter((x) => x.seq > after), seq });
    }
    if (req.method === 'DELETE') {
      // The phone has applied everything up to `upto` and put it in a new vault.
      const upto = +new URL(req.url).searchParams.get('upto') || 0;
      const keep = list.filter((x) => x.seq > upto);
      await store.put('ops', keep);
      return json({ left: keep.length });
    }
    return err(405, 'Use GET, POST or DELETE.');
  }
  return err(404, 'Nothing here.');
}

// ---------- Routing ----------
// /v1/pair/:pairId and /v1/link/:linkId[/vault|/ops]. Returns the object name and the handler to run inside it.
export function route(url) {
  const u = new URL(url);
  let m = /^\/v1\/pair\/([A-Za-z0-9_-]{22})$/.exec(u.pathname);
  if (m) return { name: 'pair:' + m[1], run: (req, store) => pair(req, store) };
  m = /^\/v1\/link\/([A-Za-z0-9_-]{32})(\/vault|\/ops)?$/.exec(u.pathname);
  if (m) return { name: 'link:' + m[1], run: (req, store) => link(req, store, m[1], m[2] || '') };
  return null;
}

export function cors(req, res, origins) {
  const o = req.headers.get('origin');
  const h = new Headers(res.headers);
  if (o && origins.includes(o)) {
    h.set('access-control-allow-origin', o);
    h.set('access-control-allow-methods', 'GET, POST, PUT, DELETE');
    h.set('access-control-allow-headers', 'authorization, content-type, if-none-match');
    h.set('access-control-expose-headers', 'etag, x-updated');
    h.set('access-control-max-age', '86400');
    h.set('vary', 'origin');
  }
  h.set('x-content-type-options', 'nosniff');
  return new Response(res.body, { status: res.status, headers: h });
}
