// Runs before anything else (a plain script, so it works even if a module fails to load).
// Paints this computer's saved look so there's no flash, and offers a reload if the app hasn't started in 8 s.
try {
  var l = JSON.parse(localStorage.getItem('waylink-web-look') || '{}'), h = document.documentElement, dk = matchMedia('(prefers-color-scheme: dark)').matches, t = l.theme || 'auto';
  if (t === 'auto') t = dk ? 'obsidian' : 'paper';
  if (t === 'sketchdark') { t = 'sketch'; h.dataset.night = ''; }
  h.dataset.theme = t; if (l.accent) h.dataset.accent = l.accent;
} catch (e) {}
setTimeout(function () {
  if (window.__waylinkReady) return;
  var b = document.getElementById('boot'); if (!b) return;
  b.textContent = 'Waylink didn’t start. ';
  var r = document.createElement('button'); r.type = 'button'; r.textContent = 'Reload';
  r.addEventListener('click', function () { location.reload(); });
  b.appendChild(r);
}, 8000);
