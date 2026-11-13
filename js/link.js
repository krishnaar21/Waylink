// Waylink — Bluetooth link to the OTC cluster (Web Bluetooth), with a simulated bike.
// Mirrors ble/BajajBleManager.kt: serialized write queue, 800 ms TBT stream, 5 s heartbeat,
// 700 ms handlebar-button poll, 4 s reconnect backoff, 6 s alert hold.
import * as P from './protocol.js';

const KEYS = ['tbt', 'general', 'missedCall', 'alerts', 'buttons'];

export class BikeLink extends EventTarget {
  constructor() {
    super();
    this.state = 'idle'; // idle | picking | connecting | connected | reconnecting | error
    this.simulated = false;
    this.device = null;
    this.chars = {};
    this.queue = [];
    this.busy = false;
    this.userDisconnect = false;
    this.nav = null;           // active step { maneuver, stepMeters, totalMeters, street, exit, eta:Date }
    this.call = { state: P.CallState.NO_CALL, name: null };
    this.missedCount = 0;
    this.acceptAck = 0; this.rejectAck = 0;
    this.lastControl = null; this.bootstrapped = false;
    this.alertHoldUntil = 0;
    this.phone = { battery: 100, signal: 4, volume: 8, model: phoneModel() };
    this.sendStreetNames = false;
    this.frames = 0;
    this.lastFrame = null;
    this.log = [];
    this.timers = {};
    this.supported = typeof navigator !== 'undefined' && !!navigator.bluetooth;
  }

  get connected() { return this.state === 'connected'; }
  get deviceName() { return this.simulated ? 'Simulated cluster' : (this.device?.name || 'Bike cluster'); }

  emit(type, detail) { this.dispatchEvent(new CustomEvent(type, { detail })); }
  note(msg, kind = 'info') {
    this.log.unshift({ t: Date.now(), msg, kind });
    this.log.length = Math.min(this.log.length, 120);
    this.emit('log', { msg, kind });
  }
  setState(s, why) { this.state = s; if (why) this.note(why); this.emit('state', s); }

  // ---------- Connection ----------
  async pair({ showAll = false } = {}) {
    if (!this.supported) throw new Error('This browser has no Bluetooth. Use Chrome on Android, Windows, macOS or ChromeOS.');
    this.userDisconnect = false;
    this.setState('picking', 'Choosing a bike…');
    let device;
    try {
      device = await navigator.bluetooth.requestDevice(showAll
        ? { acceptAllDevices: true, optionalServices: [P.UUID.service] }
        : { filters: [{ services: [P.UUID.service] }], optionalServices: [P.UUID.service] });
    } catch (e) {
      this.setState(this.device ? 'reconnecting' : 'idle', e.name === 'NotFoundError' ? 'No bike chosen.' : e.message);
      if (this.device) this.scheduleReconnect(P.TIMING.RECONNECT_MS);
      return null;
    }
    await this.attach(device);
    return device;
  }

  async attach(device) {
    this.teardown(false);
    this.simulated = false;
    this.device = device;
    device.addEventListener('gattserverdisconnected', this.onDrop);
    await this.connectGatt();
  }

  /** Reconnect to a bike chosen in an earlier visit (Chrome exposes it through getDevices). */
  async restore(savedId) {
    if (!this.supported || !navigator.bluetooth.getDevices || !savedId) return false;
    try {
      const list = await navigator.bluetooth.getDevices();
      const dev = list.find((d) => d.id === savedId);
      if (!dev) return false;
      this.device = dev;
      dev.addEventListener('gattserverdisconnected', this.onDrop);
      if (dev.watchAdvertisements) {
        this.setState('reconnecting', 'Waiting for the bike to wake up…');
        dev.addEventListener('advertisementreceived', () => { if (!this.connected && this.state !== 'connecting') this.connectGatt(); }, { once: true });
        try { await dev.watchAdvertisements(); } catch { this.connectGatt(); }
      } else this.connectGatt();
      return true;
    } catch { return false; }
  }

  async connectGatt() {
    if (!this.device) return;
    clearTimeout(this.timers.reconnect);
    this.setState('connecting', `Connecting to ${this.deviceName}…`);
    try {
      const server = await this.device.gatt.connect();
      const svc = await server.getPrimaryService(P.UUID.service);
      this.chars = {};
      for (const k of KEYS) {
        try { this.chars[k] = await svc.getCharacteristic(P.UUID[k]); } catch { this.note(`Characteristic ${k} not offered by this cluster`, 'warn'); }
      }
      // Web Bluetooth cannot call requestMtu(256); Chrome negotiates the largest MTU itself (up to 517).
      this.note('Services found. MTU is negotiated by the browser.');
      await this.startButtons();
      this.onLinkReady();
    } catch (e) {
      this.note(`Connect failed: ${e.message}`, 'warn');
      this.setState('reconnecting');
      this.scheduleReconnect(P.TIMING.SCAN_PAUSE_MS);
    }
  }

  onDrop = () => {
    this.stopLoops();
    this.chars = {};
    if (this.simulated) return;
    if (this.userDisconnect) { this.setState('idle', 'Disconnected.'); return; }
    this.setState('reconnecting', 'Bike went quiet. Retrying in 4 s.');
    this.emit('dropped');
    this.scheduleReconnect(P.TIMING.RECONNECT_MS);
  };

  scheduleReconnect(ms) {
    clearTimeout(this.timers.reconnect);
    if (this.userDisconnect || !this.device) return;
    this.timers.reconnect = setTimeout(() => this.connectGatt(), ms);
  }

  startSimulated() {
    this.teardown(false);
    this.simulated = true;
    this.userDisconnect = false;
    this.setState('connecting', 'Starting the simulated cluster…');
    setTimeout(() => { this.chars = Object.fromEntries(KEYS.map((k) => [k, { sim: true }])); this.onLinkReady(); }, 650);
  }

  async disconnect() {
    this.userDisconnect = true;
    const c = this.chars.tbt;
    if (this.connected && c && !c.sim) { // clear the cluster's nav screen before we leave
      try { await (c.writeValueWithoutResponse ? c.writeValueWithoutResponse(P.buildTbtStopFrame()) : c.writeValue(P.buildTbtStopFrame())); } catch {}
    }
    this.teardown(true);
    this.setState('idle', 'Disconnected by you. Tap Reconnect when you want it back.');
  }

  forget() { this.disconnect(); this.device = null; this.simulated = false; this.emit('state', this.state); }

  teardown(keepDevice) {
    this.stopLoops();
    clearTimeout(this.timers.reconnect);
    try { if (!this.simulated && this.device?.gatt?.connected) this.device.gatt.disconnect(); } catch {}
    this.chars = {};
    this.queue = [];
    if (!keepDevice) { /* device kept for reconnects unless forget() */ }
  }

  onLinkReady() {
    this.bootstrapped = false; this.lastControl = null;
    this.setState('connected', `Linked to ${this.deviceName}.`);
    this.emit('ready');
    this.sendGeneral();
    this.timers.hb = setInterval(() => this.sendGeneral(), P.TIMING.HEARTBEAT_MS);
    if (this.nav) this.startStream();
  }

  stopLoops() {
    clearInterval(this.timers.hb); clearInterval(this.timers.poll); clearInterval(this.timers.stream);
  }

  // ---------- Handlebar buttons ----------
  async startButtons() {
    const c = this.chars.buttons;
    if (!c) return;
    if (c.properties?.notify) {
      try {
        c.addEventListener('characteristicvaluechanged', (e) => this.handleControl(new Uint8Array(e.target.value.buffer)));
        await c.startNotifications();
        this.note('Listening to handlebar buttons.');
        return;
      } catch {}
    }
    this.timers.poll = setInterval(async () => {
      if (this.busy || this.queue.length || !this.connected) return;
      try { const v = await c.readValue(); this.handleControl(new Uint8Array(v.buffer)); } catch {}
    }, P.TIMING.BUTTON_POLL_MS);
  }

  handleControl(b) {
    if (b.length < 18) return;
    if (this.lastControl && b.every((x, i) => x === this.lastControl[i])) return;
    this.lastControl = b.slice();
    const accept = b[1], reject = b[2];
    if (!this.bootstrapped) { this.bootstrapped = true; this.acceptAck = accept; this.rejectAck = reject; return; }
    if (accept !== this.acceptAck) { this.acceptAck = accept; this.pressed('tap'); }
    if (reject !== this.rejectAck) { this.rejectAck = reject; this.pressed('long'); }
  }

  /** tap = answer or play/pause; long = decline/end or next track. Simulated bikes call this directly. */
  pressed(kind) {
    const ringing = this.call.state === P.CallState.INCOMING_CALL;
    const inCall = ringing || this.call.state === P.CallState.ACTIVE_CALL;
    let action;
    if (kind === 'tap') action = ringing ? 'answer' : 'play-pause';
    else action = inCall ? 'decline' : 'next-track';
    if (this.simulated) { if (kind === 'tap') this.acceptAck = (this.acceptAck + 1) & 0xff; else this.rejectAck = (this.rejectAck + 1) & 0xff; }
    this.note(`Handlebar ${kind === 'tap' ? 'tap' : 'long press'} → ${action}`);
    this.emit('button', { kind, action });
  }

  // ---------- Writes ----------
  enqueue(key, bytes, priority = false) {
    this.lastFrame = { key, bytes, t: Date.now() };
    this.emit('frame', this.lastFrame);
    if (!this.connected || !this.chars[key]) return;
    if (priority && key === 'tbt') this.queue = this.queue.filter((q) => q.key !== 'tbt');
    while (this.queue.length >= 30) this.queue.shift();
    const item = { key, bytes };
    priority ? this.queue.unshift(item) : this.queue.push(item);
    this.pump();
  }
  sendNow(key, bytes) { this.enqueue(key, bytes, true); }

  async pump() {
    if (this.busy) return;
    const item = this.queue.shift();
    if (!item) return;
    this.busy = true;
    const c = this.chars[item.key];
    try {
      if (this.simulated || c.sim) await new Promise((r) => setTimeout(r, 12));
      else if (c.properties.writeWithoutResponse && c.writeValueWithoutResponse) await c.writeValueWithoutResponse(item.bytes);
      else if (c.writeValueWithResponse) await c.writeValueWithResponse(item.bytes);
      else await c.writeValue(item.bytes);
      this.frames++;
    } catch (e) { this.note(`Write to ${item.key} failed: ${e.message}`, 'warn'); }
    this.busy = false;
    if (this.queue.length) this.pump();
  }

  // ---------- Status heartbeat, calls, alerts ----------
  setPhone(p) { Object.assign(this.phone, p); }

  sendGeneral() {
    const args = {
      batteryPercent: this.phone.battery, volumeLevel: this.phone.volume, signalBars: this.phone.signal,
      callState: this.call.state, callerName: this.call.name, callAcceptAck: this.acceptAck, callRejectAck: this.rejectAck,
      missedCallCount: this.missedCount, phoneModel: this.phone.model,
    };
    const urgent = this.call.state !== P.CallState.NO_CALL;
    this.enqueue('general', P.buildGeneralPacketV2(args), urgent);
    if (urgent) this.enqueue('general', P.buildGeneralPacketLegacy({ ...args, callRejectAck: this.acceptAck, callRejectSmsAck: this.rejectAck }), true);
  }

  setCall(state, name = null) {
    this.call = { state, name };
    if (state === P.CallState.NO_CALL && this.nav) this.streamTick();
    this.sendGeneral();
  }

  ackCall({ accept = false, reject = false }) {
    if (accept) this.acceptAck = (this.acceptAck + 1) & 0xff;
    if (reject) this.rejectAck = (this.rejectAck + 1) & 0xff;
    this.sendGeneral();
  }

  sendMissedCall(name, number = '', count = this.missedCount) {
    this.missedCount = count;
    this.enqueue('missedCall', P.buildMissedCallPacket(name, number));
    this.sendGeneral();
  }

  setMissedCount(n) { this.missedCount = n; this.sendGeneral(); }

  sendWhatsApp(name) {
    this.alertHoldUntil = Date.now() + P.TIMING.ALERT_HOLD_MS; // keep TBT from overwriting the alert
    this.enqueue('alerts', P.buildWhatsAppAlertPacket(name), true);
  }

  // ---------- Turn-by-turn ----------
  setNav(step) {
    this.nav = step;
    if (!step) { clearInterval(this.timers.stream); this.sendNow('tbt', P.buildTbtStopFrame()); return; }
    this.startStream();
  }

  startStream() {
    clearInterval(this.timers.stream);
    this.streamTick();
    this.timers.stream = setInterval(() => this.streamTick(), P.TIMING.TBT_STREAM_MS);
  }

  streamTick() {
    const s = this.nav;
    if (!s) return;
    if (this.call.state === P.CallState.INCOMING_CALL || Date.now() < this.alertHoldUntil) return;
    const eta = s.eta || new Date();
    let h = eta.getHours() % 12; if (h === 0) h = 12;
    this.enqueue('tbt', P.buildTbtFrame({
      type: 1, maneuverIcon: P.maneuverByKey(s.maneuver).op, isAm: eta.getHours() < 12,
      stepMeters: Math.round(s.stepMeters), totalMeters: Math.round(s.totalMeters),
      etaHour: h, etaMinute: eta.getMinutes(), roundaboutExit: s.exit || 0,
      streetText: this.sendStreetNames ? s.street : '',
    }), true);
  }
}

function phoneModel() {
  try {
    const ua = navigator.userAgent;
    const m = ua.match(/Android [\d.]+; ([^;)]+)[;)]/);
    if (m && m[1] && m[1] !== 'K') return m[1].trim();
    if (/iPhone/.test(ua)) return 'iPhone';
    return navigator.userAgentData?.platform ? `${navigator.userAgentData.platform} browser` : 'Web browser';
  } catch { return 'Web browser'; }
}
