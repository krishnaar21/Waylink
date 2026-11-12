// Waylink — offline shell. Caches the app's own files; fonts are cached as they load.
const CACHE = 'waylink-v115';
const SHELL = ['./', 'index.html', 'styles.css', 'js/app.js', 'js/views.js', 'js/store.js', 'js/link.js', 'js/protocol.js', 'js/sound.js', 'js/icons-huge.js', 'img/onb/scene-light.webp', 'img/onb/scene-dark.webp', 'img/onb/link-light.webp', 'img/onb/link-dark.webp', 'img/grain.png', 'img/rider.png', 'manifest.webmanifest', 'icon.svg', 'img/logos/wl-loop-rust.svg', 'img/logos/wl-turns-rust.svg', 'img/logos/png/wl-loop-rust-180.png', 'img/bikes/manifest.json', 'img/bikes/ns200-art.webp', ];
// Cache each file on its own, so one missing file can't block an update.
self.addEventListener('install', (e) => { e.waitUntil(caches.open(CACHE).then((c) => Promise.all(SHELL.map((u) => c.add(new Request(u, { cache: 'reload' })).catch(() => {})))).then(() => self.skipWaiting())); });
self.addEventListener('activate', (e) => { e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET') return;
  const isFont = /fonts\.(googleapis|gstatic)\.com$/.test(url.hostname);
  if (url.origin !== location.origin && !isFont) return;
  // Saved copy first, so the app opens instantly (also when coming back from another app);
  // refresh the copy in the background. New versions arrive as a new worker and reload the app.
  const same = url.origin === location.origin;
  const net = (same ? fetch(url.href, { cache: 'no-cache', credentials: 'same-origin' }) : fetch(e.request)).then((res) => {
    if (res.ok || res.type === 'opaque') { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
    return res;
  });
  const key = e.request.mode === 'navigate' ? caches.match(e.request, { ignoreSearch: true }).then((r) => r || caches.match('index.html')) : caches.match(e.request);
  e.waitUntil(net.catch(() => {}));
  e.respondWith(key.then((r) => r || net).catch(() => caches.match('index.html')));
});
