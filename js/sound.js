// Waylink — vehicle sound effects, synthesised live with Web Audio (no audio files).
// The engine is a single-cylinder model: firing pulses (one every two turns of the crank) excite an exhaust
// resonance, with mechanical noise riding on each pulse. Clicks and ticks are short filtered bursts.
let ctx = null, master = null, comp = null, enabled = true, unlocked = false;

export const setEnabled = (v) => { enabled = !!v; };
function ac() {
  if (!enabled) return null;
  if (!ctx) {
    const C = window.AudioContext || window.webkitAudioContext; if (!C) return null;
    try {
      ctx = new C({ latencyHint: 'interactive' });
      comp = ctx.createDynamicsCompressor(); comp.threshold.value = -14; comp.knee.value = 10; comp.ratio.value = 4; comp.attack.value = 0.004; comp.release.value = 0.2;
      master = ctx.createGain(); master.gain.value = 0.34; master.connect(comp); comp.connect(ctx.destination);
      // A small, dark room around everything, so sounds don't feel dry.
      const verb = ctx.createConvolver(), wet = ctx.createGain(); wet.gain.value = 0.08;
      const len = Math.floor(ctx.sampleRate * 0.9), ir = ctx.createBuffer(2, len, ctx.sampleRate);
      for (let ch = 0; ch < 2; ch++) { const d = ir.getChannelData(ch); for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 3.2) * (i < 40 ? i / 40 : 1); }
      verb.buffer = ir; const damp = ctx.createBiquadFilter(); damp.type = 'lowpass'; damp.frequency.value = 3800;
      master.connect(verb); verb.connect(damp); damp.connect(wet); wet.connect(comp);
    } catch { return null; }
  }
  if (ctx.state !== 'running') ctx.resume().catch(() => {});
  return ctx;
}
export const ready = () => !!ctx && ctx.state === 'running';
// Runs fn only if the audio clock is actually advancing (sound will be heard now, not queued for later).
export function whenLive(fn, wait = 160) {
  const c = ac(); if (!c) return;
  const t0 = c.currentTime;
  setTimeout(() => { if (ctx && ctx.state === 'running' && ctx.currentTime > t0 + 0.04) fn(); }, wait);
}
// iOS starts audio only inside a finished tap: resume and play one silent sample right there.
export function unlock() {
  const c = ac(); if (!c) return false; if (unlocked && c.state === 'running') return true;
  try { const b = c.createBuffer(1, 1, 22050), s = c.createBufferSource(); s.buffer = b; s.connect(c.destination); s.start(0); } catch {}
  if (c.state === 'running') unlocked = true;
  return true;
}

let NOISE = null;
function noise(c) {
  if (NOISE && NOISE.sampleRate === c.sampleRate) return NOISE;
  const b = c.createBuffer(1, c.sampleRate * 2, c.sampleRate), d = b.getChannelData(0);
  let last = 0; for (let i = 0; i < d.length; i++) { const w = Math.random() * 2 - 1; last = (last + 0.04 * w) / 1.04; d[i] = w * 0.6 + last * 3; } // a little brown in the white
  return (NOISE = b);
}
// A narrow pulse: one cylinder firing. Built once as a periodic wave.
let PULSE = null;
function pulseWave(c) {
  if (PULSE) return PULSE;
  const n = 48, re = new Float32Array(n), im = new Float32Array(n), duty = 0.16;
  for (let k = 1; k < n; k++) { im[k] = (2 / (k * Math.PI)) * Math.sin(k * Math.PI * duty) * Math.exp(-k / 26); }
  return (PULSE = c.createPeriodicWave(re, im, { disableNormalization: false }));
}
// A cleaner, sportier rev: a stack of detuned saws at the firing rate of a twin (rpm / 60) with its octave,
// a filter that opens with the revs, low end trimmed so it never "putts", and a breath of intake noise.
function revRise(c, t0, curve, dur, vol = 0.5) {
  const f = (rpm) => rpm / 60;
  const out = c.createGain(); out.gain.setValueAtTime(0, t0); out.gain.linearRampToValueAtTime(vol, t0 + 0.04);
  out.gain.setValueAtTime(vol, t0 + Math.max(0.05, dur - 0.3)); out.gain.linearRampToValueAtTime(0, t0 + dur);
  const hp = c.createBiquadFilter(); hp.type = 'highpass'; hp.frequency.value = 70; hp.Q.value = 0.7;
  const lp = c.createBiquadFilter(); lp.type = 'lowpass'; lp.Q.value = 3.2;
  const body = c.createBiquadFilter(); body.type = 'peaking'; body.frequency.value = 420; body.Q.value = 1; body.gain.value = 5;
  const sh = c.createWaveShaper(); const k = new Float32Array(1024); for (let i = 0; i < 1024; i++) { const x = i / 512 - 1; k[i] = Math.tanh(2.2 * x); } sh.curve = k; sh.oversample = '4x';
  const mix = c.createGain(); mix.gain.value = 0.32;
  const oscs = [];
  for (const [type, mult, det, g] of [['sawtooth', 1, -7, 0.5], ['sawtooth', 1, 6, 0.5], ['square', 2, 0, 0.22], ['sawtooth', 0.5, 0, 0.18]]) {
    const o = c.createOscillator(); o.type = type; o.detune.value = det; const og = c.createGain(); og.gain.value = g;
    o.connect(og); og.connect(mix); oscs.push([o, mult]);
  }
  const nz = c.createBufferSource(); nz.buffer = noise(c); nz.loop = true;
  const nb = c.createBiquadFilter(); nb.type = 'bandpass'; nb.Q.value = 1.1; const ng = c.createGain(); ng.gain.value = 0.05;
  for (const [o, m] of oscs) o.frequency.setValueAtTime(f(curve[0][1]) * m, t0);
  lp.frequency.setValueAtTime(500 + curve[0][1] * 0.45, t0); nb.frequency.setValueAtTime(1200 + curve[0][1] * 0.2, t0);
  for (const [t, rpm] of curve) {
    const at = t0 + t;
    for (const [o, m] of oscs) o.frequency.exponentialRampToValueAtTime(f(rpm) * m, at);
    lp.frequency.exponentialRampToValueAtTime(Math.min(6000, 500 + rpm * 0.45), at);
    nb.frequency.exponentialRampToValueAtTime(Math.min(5000, 1200 + rpm * 0.2), at);
  }
  mix.connect(sh); sh.connect(hp); hp.connect(body); body.connect(lp); lp.connect(out);
  nz.connect(nb); nb.connect(ng); ng.connect(out); out.connect(master);
  for (const [o] of oscs) { o.start(t0); o.stop(t0 + dur + 0.05); } nz.start(t0); nz.stop(t0 + dur + 0.05);
}
// rpm curve: [[seconds, rpm], ...]. Firing rate = rpm / 120 for a four-stroke single.
function engine(c, t0, curve, dur, vol = 0.7) {
  const f = (rpm) => Math.max(6, rpm / 120);
  const out = c.createGain(); out.gain.setValueAtTime(0, t0); out.gain.linearRampToValueAtTime(vol, t0 + 0.05);
  out.gain.setValueAtTime(vol, t0 + Math.max(0.06, dur - 0.22)); out.gain.linearRampToValueAtTime(0, t0 + dur);
  // Firing pulses
  const fire = c.createOscillator(); fire.setPeriodicWave(pulseWave(c));
  // Exhaust: a resonant body around 110–180 Hz plus a bark band that opens with revs
  const body = c.createBiquadFilter(); body.type = 'peaking'; body.frequency.value = 140; body.Q.value = 1.4; body.gain.value = 9;
  const bark = c.createBiquadFilter(); bark.type = 'lowpass'; bark.Q.value = 2.2;
  const shaper = c.createWaveShaper(); const k = new Float32Array(1024); for (let i = 0; i < 1024; i++) { const x = i / 512 - 1; k[i] = Math.tanh(3 * x) * 0.9; } shaper.curve = k; shaper.oversample = '2x';
  const fireG = c.createGain(); fireG.gain.value = 0.9;
  // Mechanical noise riding on each pulse (valvetrain and intake)
  const nz = c.createBufferSource(); nz.buffer = noise(c); nz.loop = true;
  const nzBand = c.createBiquadFilter(); nzBand.type = 'bandpass'; nzBand.Q.value = 0.8;
  const nzG = c.createGain(); nzG.gain.value = 0;
  const mod = c.createGain(); mod.gain.value = 0.22; fire.connect(mod); mod.connect(nzG.gain); // pulses open the noise
  // A low sub layer for weight at idle
  const sub = c.createOscillator(); sub.type = 'sine'; const subG = c.createGain(); subG.gain.value = 0.35;
  for (const [t, rpm] of curve) {
    const at = t0 + t;
    fire.frequency.linearRampToValueAtTime(f(rpm), at);
    sub.frequency.linearRampToValueAtTime(f(rpm), at);
    bark.frequency.linearRampToValueAtTime(Math.min(5200, 260 + rpm * 0.42), at);
    nzBand.frequency.linearRampToValueAtTime(Math.min(4000, 700 + rpm * 0.25), at);
    subG.gain.linearRampToValueAtTime(Math.max(0.05, 0.45 - rpm / 20000), at);
  }
  fire.frequency.setValueAtTime(f(curve[0][1]), t0); sub.frequency.setValueAtTime(f(curve[0][1]), t0);
  bark.frequency.setValueAtTime(260 + curve[0][1] * 0.42, t0); nzBand.frequency.setValueAtTime(700 + curve[0][1] * 0.25, t0);
  fire.connect(fireG); fireG.connect(shaper); shaper.connect(body); body.connect(bark); bark.connect(out);
  nz.connect(nzBand); nzBand.connect(nzG); nzG.connect(out);
  sub.connect(subG); subG.connect(out);
  out.connect(master);
  for (const s of [fire, sub, nz]) { s.start(t0); s.stop(t0 + dur + 0.05); }
}
function burst(c, t0, dur, freq, vol, type = 'bandpass', q = 1.2) {
  const n = c.createBufferSource(), fl = c.createBiquadFilter(), g = c.createGain();
  n.buffer = noise(c); fl.type = type; fl.frequency.value = freq; fl.Q.value = q;
  g.gain.setValueAtTime(0.0001, t0); g.gain.exponentialRampToValueAtTime(vol, t0 + 0.002); g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
  n.connect(fl); fl.connect(g); g.connect(master); n.start(t0, Math.random()); n.stop(t0 + dur + 0.01);
}
function thud(c, t0, f0, f1, dur, vol) {
  const o = c.createOscillator(), g = c.createGain(); o.type = 'sine';
  o.frequency.setValueAtTime(f0, t0); o.frequency.exponentialRampToValueAtTime(f1, t0 + dur);
  g.gain.setValueAtTime(vol, t0); g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
  o.connect(g); g.connect(master); o.start(t0); o.stop(t0 + dur + 0.01);
}
function starter(c, t0, dur) {
  // Starter motor whine, loaded by compression strokes
  const o = c.createOscillator(), g = c.createGain(), lp = c.createBiquadFilter(), chop = c.createOscillator(), cg = c.createGain();
  o.type = 'sawtooth'; o.frequency.setValueAtTime(180, t0); o.frequency.linearRampToValueAtTime(240, t0 + dur);
  lp.type = 'bandpass'; lp.frequency.value = 900; lp.Q.value = 1.6;
  chop.type = 'sine'; chop.frequency.setValueAtTime(6.5, t0); chop.frequency.linearRampToValueAtTime(10, t0 + dur); cg.gain.value = 0.07;
  g.gain.setValueAtTime(0, t0); g.gain.linearRampToValueAtTime(0.12, t0 + 0.04); g.gain.setValueAtTime(0.12, t0 + dur - 0.05); g.gain.linearRampToValueAtTime(0, t0 + dur);
  chop.connect(cg); cg.connect(g.gain); o.connect(lp); lp.connect(g); g.connect(master);
  o.start(t0); chop.start(t0); o.stop(t0 + dur + 0.02); chop.stop(t0 + dur + 0.02);
  for (let t = 0.08; t < dur; t += 0.13) burst(c, t0 + t, 0.05, 260, 0.12, 'lowpass', 0.7); // compression chuffs
}

// App launch: starter, catch, one blip, settle to idle (about 1.9 s).
export function engineStart() {
  const c = ac(); if (!c) return; const t = c.currentTime + 0.03;
  burst(c, t, 0.02, 2400, 0.06); // the starter button
  // Catch, a quick blip, then one clean rising pull before it settles.
  revRise(c, t + 0.06, [[0, 1600], [0.12, 3400], [0.28, 2200], [0.95, 9800], [1.08, 9400], [1.5, 2600], [1.8, 1700]], 1.85, 0.3);
}
// Ride starts: clutch in, into first, a clean pull away.
function _revUp() {
  const c = ac(); if (!c) return; const t = c.currentTime + 0.02;
  thud(c, t, 120, 55, 0.1, 0.28); burst(c, t, 0.04, 900, 0.08, 'lowpass', 0.8); // gear goes in
  revRise(c, t + 0.08, [[0, 1700], [0.5, 7800], [0.62, 4300], [1.2, 8200], [1.6, 5200]], 1.6, 0.26);
  thud(c, t + 1.3, 110, 50, 0.08, 0.2); // up into second
}
// Ride ends: off the throttle, down to idle, key off, the exhaust ticking as it cools.
function _engineOff() {
  const c = ac(); if (!c) return; const t = c.currentTime + 0.02;
  revRise(c, t, [[0, 3600], [0.4, 1800], [0.8, 1500], [1.05, 700], [1.15, 400]], 1.15, 0.24);
  crackles(c, t + 0.12, 3);
  burst(c, t + 1.12, 0.02, 2600, 0.07); // key turns
  [1.5, 1.95, 2.55].forEach((d, i) => burst(c, t + d, 0.02, 4200 - i * 300, 0.07, 'bandpass', 6));
}
// Switch: a relay. Two contacts close a few milliseconds apart; brighter when switching on.
function _click(on = true) {
  const c = ac(); if (!c) return; const t = c.currentTime + 0.004;
  burst(c, t, 0.02, on ? 3600 : 2600, on ? 0.72 : 0.5, 'bandpass', 2.2);
  burst(c, t + 0.009, 0.024, on ? 1800 : 1300, 0.50, 'bandpass', 1.8);
  thud(c, t, on ? 220 : 170, 90, 0.04, 0.14);
}
// Horn: the two-tone electric horn of an Indian commuter bike, two short beeps.
function _horn(beeps = 2) {
  const c = ac(); if (!c) return; let t = c.currentTime + 0.02;
  for (let b = 0; b < beeps; b++) {
    const len = b === beeps - 1 ? 0.42 : 0.16;
    const g = c.createGain(), bp = c.createBiquadFilter(), sh = c.createWaveShaper();
    const k = new Float32Array(512); for (let i = 0; i < 512; i++) { const x = i / 256 - 1; k[i] = Math.tanh(4 * x); } sh.curve = k;
    bp.type = 'bandpass'; bp.frequency.value = 1200; bp.Q.value = 0.7;
    g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.14, t + 0.012); g.gain.setValueAtTime(0.14, t + len - 0.04); g.gain.exponentialRampToValueAtTime(0.0001, t + len);
    for (const f of [410, 505]) { const o = c.createOscillator(); o.type = 'square'; o.frequency.value = f; o.detune.value = (Math.random() - 0.5) * 8; o.connect(sh); o.start(t); o.stop(t + len + 0.02); }
    sh.connect(bp); bp.connect(g); g.connect(master);
    t += len + 0.09;
  }
}
// A single throttle blip at idle.
function _blip() {
  const c = ac(); if (!c) return; const t = c.currentTime + 0.02;
  engine(c, t, [[0, 1350], [0.1, 4800], [0.34, 2100], [0.6, 1350]], 0.75, 0.38);
}
// Rising revs that follow a slider: one running engine, its revs set by the value (0..1), fading out when you stop.
let live = null, liveStop = 0, lastP = null, lastAt = 0;
function _revTo(p = 0) {
  const c = ac(); if (!c) return;
  p = Math.max(0, Math.min(1, p));
  const rpm = 1300 + p * 6200, f = rpm / 120, now = c.currentTime;
  // Quiet when nudged, a little fuller when dragged fast.
  const dt = Math.max(0.016, now - lastAt), speed = lastP == null ? 0 : Math.abs(p - lastP) / dt;
  lastP = p; lastAt = now;
  const lvl = 0.08 + Math.min(1, speed / 3) * 0.17;
  if (!live) {
    const out = c.createGain(); out.gain.setValueAtTime(0, now); out.gain.linearRampToValueAtTime(lvl, now + 0.012);
    const fire = c.createOscillator(); fire.setPeriodicWave(pulseWave(c)); fire.frequency.setValueAtTime(f, now);
    const sh = c.createWaveShaper(); const k = new Float32Array(1024); for (let i = 0; i < 1024; i++) { const x = i / 512 - 1; k[i] = Math.tanh(3 * x) * 0.9; } sh.curve = k;
    const body = c.createBiquadFilter(); body.type = 'peaking'; body.frequency.value = 140; body.Q.value = 1.4; body.gain.value = 9;
    const bark = c.createBiquadFilter(); bark.type = 'lowpass'; bark.Q.value = 2.2;
    fire.connect(sh); sh.connect(body); body.connect(bark); bark.connect(out); out.connect(master); fire.start();
    bark.frequency.setValueAtTime(Math.min(5200, 260 + rpm * 0.42), now);
    live = { out, fire, bark };
  }
  live.fire.frequency.setTargetAtTime(f, now, 0.03);
  live.bark.frequency.setTargetAtTime(Math.min(5200, 260 + rpm * 0.42), now, 0.06);
  live.out.gain.cancelScheduledValues(now); live.out.gain.setTargetAtTime(lvl, now, 0.05);
  clearTimeout(liveStop);
  liveStop = setTimeout(() => {
    lastP = null; if (!live) return; const l = live; live = null; const t = c.currentTime;
    l.fire.frequency.setTargetAtTime(1300 / 120, t, 0.12); l.out.gain.setTargetAtTime(0, t + 0.15, 0.1); l.fire.stop(t + 0.8);
  }, 260);
}
// Kept for older callers: a slider move now revs the engine.

// Exhaust crackles: a few pops as the throttle closes.
function crackles(c, t0, n = 6) {
  for (let i = 0; i < n; i++) { const t = t0 + i * (0.05 + Math.random() * 0.07); burst(c, t, 0.03 + Math.random() * 0.03, 300 + Math.random() * 500, 0.18 + Math.random() * 0.14, 'lowpass', 0.9); thud(c, t, 160, 60, 0.04, 0.1); }
}

// iPhone pauses audio when the app goes to the background or sits silent; a sound asked for then would be lost.
// Each sound waits for the audio to resume before it plays.
// A sound that can't start within 150 ms is dropped, so nothing ever plays late or out of step with the tap.
const whenAwake = (fn) => (...a) => { const c = ac(); if (!c) return; if (c.state === 'running') return fn(...a); const t0 = performance.now(); c.resume().then(() => { if (performance.now() - t0 < 150) fn(...a); }).catch(() => {}); };
export const revUp = whenAwake(_revUp), engineOff = whenAwake(_engineOff), click = whenAwake(_click), horn = whenAwake(_horn), blip = whenAwake(_blip), revTo = whenAwake(_revTo);
export const tick = revTo;
