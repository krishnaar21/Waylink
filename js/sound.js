// Waylink — vehicle sound effects, synthesised live with Web Audio (no audio files).
// engineStart: starter crank, catch, rev blip, settle to idle. revUp / engineOff for rides.
// click: indicator-relay tick for switches. tick: gear-detent tick for sliders, pitched by value.
let ctx = null, master = null, enabled = true;

export const setEnabled = (v) => { enabled = !!v; };
function ac() {
  if (!enabled) return null;
  if (!ctx) {
    const C = window.AudioContext || window.webkitAudioContext; if (!C) return null;
    try { ctx = new C(); master = ctx.createGain(); master.gain.value = 0.55; master.connect(ctx.destination); } catch { return null; }
  }
  if (ctx.state === 'suspended') ctx.resume().catch(() => {});
  return ctx;
}
export const ready = () => !!ctx && ctx.state === 'running';
// Browsers start audio only after a tap. Call this from the first pointerdown.
export function unlock() { const c = ac(); return !!c; }

function noiseBuf(c, secs) {
  const b = c.createBuffer(1, Math.ceil(c.sampleRate * secs), c.sampleRate), d = b.getChannelData(0);
  for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
  return b;
}
// A single-cylinder engine voice: a pulse train (sawtooth through a low-pass) with firing-rate wobble.
function engine(c, t0, curve, dur, vol = 0.5) {
  const o1 = c.createOscillator(), o2 = c.createOscillator(), lp = c.createBiquadFilter(), g = c.createGain(), shaper = c.createWaveShaper();
  o1.type = 'sawtooth'; o2.type = 'square';
  const k = new Float32Array(256); for (let i = 0; i < 256; i++) { const x = i / 128 - 1; k[i] = Math.tanh(2.6 * x); } shaper.curve = k;
  lp.type = 'lowpass'; lp.Q.value = 4;
  for (const [t, hz] of curve) {
    o1.frequency.linearRampToValueAtTime(hz, t0 + t);
    o2.frequency.linearRampToValueAtTime(hz / 2, t0 + t);
    lp.frequency.linearRampToValueAtTime(Math.min(4200, hz * 7), t0 + t);
  }
  o1.frequency.setValueAtTime(curve[0][1], t0); o2.frequency.setValueAtTime(curve[0][1] / 2, t0); lp.frequency.setValueAtTime(curve[0][1] * 7, t0);
  // Firing pulses: amplitude LFO at the firing rate makes the "thump-thump".
  const lfo = c.createOscillator(), lg = c.createGain(); lfo.type = 'square'; lfo.frequency.value = curve[0][1] / 2; lg.gain.value = vol * 0.35;
  for (const [t, hz] of curve) lfo.frequency.linearRampToValueAtTime(hz / 2, t0 + t);
  lfo.connect(lg); lg.connect(g.gain);
  g.gain.setValueAtTime(0, t0); g.gain.linearRampToValueAtTime(vol, t0 + 0.04);
  g.gain.setValueAtTime(vol, t0 + dur - 0.25); g.gain.linearRampToValueAtTime(0, t0 + dur);
  const mix = c.createGain(); mix.gain.value = 0.5;
  o1.connect(mix); o2.connect(mix); mix.connect(shaper); shaper.connect(lp); lp.connect(g); g.connect(master);
  for (const o of [o1, o2, lfo]) { o.start(t0); o.stop(t0 + dur + 0.05); }
}
function crank(c, t0, dur) {
  // Starter motor: a whining buzz chopped by compression strokes.
  const o = c.createOscillator(), g = c.createGain(), lp = c.createBiquadFilter(), chop = c.createOscillator(), cg = c.createGain();
  o.type = 'sawtooth'; o.frequency.setValueAtTime(95, t0); o.frequency.linearRampToValueAtTime(130, t0 + dur);
  lp.type = 'lowpass'; lp.frequency.value = 900;
  chop.type = 'square'; chop.frequency.setValueAtTime(7, t0); chop.frequency.linearRampToValueAtTime(11, t0 + dur); cg.gain.value = 0.12;
  g.gain.setValueAtTime(0, t0); g.gain.linearRampToValueAtTime(0.16, t0 + 0.03); g.gain.setValueAtTime(0.16, t0 + dur - 0.05); g.gain.linearRampToValueAtTime(0, t0 + dur);
  chop.connect(cg); cg.connect(g.gain); o.connect(lp); lp.connect(g); g.connect(master);
  o.start(t0); chop.start(t0); o.stop(t0 + dur + 0.02); chop.stop(t0 + dur + 0.02);
}
function burst(c, t0, dur, freq, vol, type = 'bandpass') {
  const n = c.createBufferSource(), f = c.createBiquadFilter(), g = c.createGain();
  n.buffer = noiseBuf(c, dur); f.type = type; f.frequency.value = freq; f.Q.value = 1.2;
  g.gain.setValueAtTime(vol, t0); g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
  n.connect(f); f.connect(g); g.connect(master); n.start(t0); n.stop(t0 + dur);
}

// App launch: crank, catch, blip, idle. About 1.7 s, timed with the loader's rev.
export function engineStart() {
  const c = ac(); if (!c) return; const t = c.currentTime + 0.02;
  crank(c, t, 0.55);
  burst(c, t + 0.55, 0.12, 180, 0.35, 'lowpass');
  engine(c, t + 0.55, [[0, 34], [0.18, 70], [0.45, 92], [0.75, 40], [1.15, 30]], 1.2, 0.42);
}
// Ride starts: blip and pull away.
export function revUp() {
  const c = ac(); if (!c) return; const t = c.currentTime + 0.02;
  engine(c, t, [[0, 30], [0.12, 62], [0.3, 40], [0.55, 55], [0.95, 110], [1.2, 95]], 1.25, 0.4);
}
// Ride ends: roll off the throttle and cut the engine.
export function engineOff() {
  const c = ac(); if (!c) return; const t = c.currentTime + 0.02;
  engine(c, t, [[0, 60], [0.35, 34], [0.7, 26], [0.9, 12]], 0.95, 0.38);
  burst(c, t + 0.92, 0.08, 120, 0.18, 'lowpass');
  // Cooling tick of the exhaust.
  [1.25, 1.6].forEach((d) => burst(c, t + d, 0.03, 3200, 0.06));
}
// Switch: an indicator-relay click. Higher and brighter when turning on.
export function click(on = true) {
  const c = ac(); if (!c) return; const t = c.currentTime + 0.005;
  burst(c, t, 0.035, on ? 2600 : 1700, 0.32);
  const o = c.createOscillator(), g = c.createGain();
  o.type = 'triangle'; o.frequency.setValueAtTime(on ? 190 : 140, t); o.frequency.exponentialRampToValueAtTime(70, t + 0.05);
  g.gain.setValueAtTime(0.22, t); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.06);
  o.connect(g); g.connect(master); o.start(t); o.stop(t + 0.07);
}
// Slider: a gear-detent tick whose pitch climbs with the value (0..1), like rising revs.
let lastTick = 0;
export function tick(p = 0.5) {
  const c = ac(); if (!c) return;
  const now = performance.now(); if (now - lastTick < 45) return; lastTick = now;
  const t = c.currentTime + 0.003, o = c.createOscillator(), g = c.createGain();
  o.type = 'square'; o.frequency.value = 420 + p * 900;
  g.gain.setValueAtTime(0.13, t); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.025);
  o.connect(g); g.connect(master); o.start(t); o.stop(t + 0.03);
}
