// Waylink — offline shell. Caches the app's own files; fonts are cached as they load.
const CACHE = 'waylink-v15';
const SHELL = ['./', 'index.html', 'styles.css', 'js/app.js', 'js/views.js', 'js/store.js', 'js/link.js', 'js/protocol.js', 'js/icons.js', 'js/sound.js', 'js/icons-uicons.js', 'manifest.webmanifest', 'icon.svg', 'img/logos/wl-turn.svg', 'img/logos/wl-link.svg', 'img/logos/wl-sign.svg', 'img/logos/wl-swift.svg', 'img/logos/wl-beacon.svg', 'img/logos/png/wl-turn-180.png', 'img/bikes/manifest.json', 'img/bikes/ns200-art.webp', ];
self.addEventListener('install', (e) => { e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting())); });
self.addEventListener('activate', (e) => { e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET') return;
  const isFont = /fonts\.(googleapis|gstatic)\.com$/.test(url.hostname);
  if (url.origin !== location.origin && !isFont) return;
  // Network first for the app (so updates land), cache fallback offline.
  e.respondWith(fetch(e.request).then((res) => {
    if (res.ok || res.type === 'opaque') { const copy = res.clone(); caches.open(CACHE).then((c) => c.put(e.request, copy)); }
    return res;
  }).catch(() => caches.match(e.request).then((r) => r || caches.match('index.html'))));
});
