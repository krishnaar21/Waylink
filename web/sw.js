// Waylink for desktop doesn't use a service worker. This file replaces the one the earlier web app
// (now at /v0/) installed at this address, so browsers that kept it stop serving the old app from cache.
self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', (e) => e.waitUntil((async () => {
  for (const k of await caches.keys()) await caches.delete(k);
  await self.registration.unregister();
  for (const c of await self.clients.matchAll({ type: 'window' })) c.navigate(c.url);
})()));
