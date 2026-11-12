// Waylink — offline shell. Caches the app's own files; fonts are cached as they load.
const CACHE = 'waylink-v19';
const SHELL = ['./', 'index.html', 'styles.css', 'js/app.js', 'js/views.js', 'js/store.js', 'js/link.js', 'js/protocol.js', 'js/icons.js', 'js/sound.js', 'js/icons-uicons.js', 'manifest.webmanifest', 'icon.svg', 'img/logos/wl-turn.svg', 'img/logos/wl-road.svg', 'img/logos/wl-chain.svg', 'img/logos/wl-nav.svg', 'img/logos/wl-route.svg', 'img/logos/png/wl-turn-180.png', 'img/bikes/manifest.json', 'img/bikes/ns200-art.webp', ];
// Cache each file on its own, so one missing file can't block an update.
self.addEventListener('install', (e) => { e.waitUntil(caches.open(CACHE).then((c) => Promise.all(SHELL.map((u) => c.add(new Request(u, { cache: 'reload' })).catch(() => {})))).then(() => self.skipWaiting())); });
self.addEventListener('activate', (e) => { e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET') return;
  const isFont = /fonts\.(googleapis|gstatic)\.com$/.test(url.hostname);
  if (url.origin !== location.origin && !isFont) return;
  // Network first so updates land, but never wait more than 3.5 s: fall back to the saved copy on a weak signal.
  const same = url.origin === location.origin;
  const net = (same ? fetch(url.href, { cache: 'no-cache', credentials: 'same-origin' }) : fetch(e.request)).then((res) => {
    if (res.ok || res.type === 'opaque') { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
    return res;
  });
  const cached = caches.match(e.request);
  const timeout = new Promise((r) => setTimeout(r, 3500));
  e.respondWith(Promise.race([net, timeout.then(() => cached.then((r) => r || net))]).catch(() => cached.then((r) => r || caches.match('index.html'))));
});
