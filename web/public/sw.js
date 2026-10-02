const CACHE_NAME = 'plan-platform-shell-dev';
const SHELL = ['/', '/index.html', '/manifest.webmanifest', '/icon.svg'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(SHELL)));
});
self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key.startsWith('plan-platform-') && key !== CACHE_NAME).map((key) => caches.delete(key)))).then(() => self.clients.claim()));
});
self.addEventListener('message', (event) => { if (event.data === 'SKIP_WAITING') self.skipWaiting(); });
self.addEventListener('fetch', (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;
  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).then(async (response) => {
      if (response.ok && response.headers.get('content-type')?.includes('text/html')) {
        await (await caches.open(CACHE_NAME)).put('/index.html', response.clone());
      }
      return response;
    }).catch(async () => (await caches.match('/index.html')) ?? new Response('应用壳尚未缓存，请联网后重新打开。', { status: 503 })));
    return;
  }
  // Dynamic module imports may have an empty destination. Precached shell paths
  // must still be served offline, independently of the browser's classification.
  if (SHELL.includes(url.pathname) || ['script', 'style', 'image', 'font'].includes(request.destination)) {
    // Static public assets do not vary by account or Origin. A module fetch can
    // carry Origin while install-time fetches do not (e.g. Vite's Vary: Origin).
    event.respondWith(caches.match(request, { ignoreVary: true }).then(async (cached) => {
      if (cached) return cached;
      const response = await fetch(request);
      if (response.ok) await (await caches.open(CACHE_NAME)).put(request, response.clone());
      return response;
    }));
  }
});
