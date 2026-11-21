// Cloudflare Worker entry: each pairing and each link gets its own Durable Object, so requests
// for one link are handled in order and nothing is shared between links.
import { DurableObject } from 'cloudflare:workers';
import { route, cors, LIMITS } from './core.js';

export class Box extends DurableObject {
  async fetch(req) {
    const r = route(req.url);
    if (!r) return new Response('Not found', { status: 404 });
    const s = this.ctx.storage;
    const store = { get: (k) => s.get(k), put: (k, v) => s.put(k, v), delete: (k) => s.delete(k), deleteAll: () => s.deleteAll() };
    const res = await r.run(req, store);
    // Clean up by itself: a pairing after its 10 minutes, a link after 120 quiet days.
    await s.setAlarm(Date.now() + (r.name.startsWith('pair:') ? LIMITS.pairTtl + 60e3 : LIMITS.linkTtl));
    return res;
  }
  async alarm() { await this.ctx.storage.deleteAll(); }
}

export default {
  async fetch(req, env) {
    const origins = (env.ORIGINS || '').split(',').map((x) => x.trim()).filter(Boolean);
    if (req.method === 'OPTIONS') return cors(req, new Response(null, { status: 204 }), origins);
    const r = route(req.url);
    if (!r) return cors(req, new Response(JSON.stringify({ error: 'Nothing here.' }), { status: 404, headers: { 'content-type': 'application/json' } }), origins);
    const stub = env.BOX.get(env.BOX.idFromName(r.name));
    return cors(req, await stub.fetch(req), origins);
  }
};
