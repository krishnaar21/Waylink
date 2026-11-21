// Local relay for development and tests: the same logic as the Worker, kept in memory.
//   node relay/dev.mjs [port]      (default 8787)
import http from 'node:http';
import { route, cors } from './src/core.js';

const ORIGINS = ['http://localhost:8766', 'http://127.0.0.1:8766'];
const boxes = new Map();
const storeFor = (name) => {
  if (!boxes.has(name)) boxes.set(name, new Map());
  const m = boxes.get(name);
  return { get: async (k) => m.get(k), put: async (k, v) => { m.set(k, structuredClone(v)); }, delete: async (k) => { m.delete(k); }, deleteAll: async () => { m.clear(); } };
};
// One request at a time per object, like a Durable Object.
const queues = new Map();
const serial = (name, fn) => { const p = (queues.get(name) || Promise.resolve()).then(fn, fn); queues.set(name, p.catch(() => {})); return p; };

export function start(port = 8787) {
  const server = http.createServer(async (rq, rs) => {
    const chunks = []; for await (const c of rq) chunks.push(c);
    const body = Buffer.concat(chunks);
    const req = new Request(`http://localhost:${port}${rq.url}`, { method: rq.method, headers: rq.headers, body: ['GET', 'HEAD', 'OPTIONS'].includes(rq.method) ? undefined : body });
    let res;
    if (rq.method === 'OPTIONS') res = new Response(null, { status: 204 });
    else { const r = route(req.url); res = r ? await serial(r.name, () => r.run(req, storeFor(r.name))) : new Response('{"error":"Nothing here."}', { status: 404 }); }
    res = cors(req, res, ORIGINS);
    rs.writeHead(res.status, Object.fromEntries(res.headers));
    rs.end(Buffer.from(await res.arrayBuffer()));
  });
  return new Promise((ok) => server.listen(port, () => ok(server)));
}
if (import.meta.url === `file://${process.argv[1]}`) start(+process.argv[2] || 8787).then(() => console.log('Relay on http://localhost:' + (+process.argv[2] || 8787)));
