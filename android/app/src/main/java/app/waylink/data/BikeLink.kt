package app.waylink.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import app.waylink.core.Protocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/** A bike seen while scanning: its Bluetooth address, name and signal. */
data class FoundBike(val address: String, val name: String, val rssi: Int)

/**
 * The real Bluetooth link to the OTC cluster, ported from web-v0/js/link.js (itself after PulsarNav's
 * BajajBleManager): scan, connect, find the cluster's service, then one write at a time from a queue.
 * Handlebar buttons come by notification, or a read every 0.7 s when the cluster has none. A drop that
 * the rider didn't ask for retries after 4 s, then every 15 s.
 */
@SuppressLint("MissingPermission") // every entry point checks [hasPermissions] first
class BikeLink(
    private val ctx: Context,
    private val scope: CoroutineScope,
    private val onState: (LinkState, String?) -> Unit,
    private val onLog: (String) -> Unit,
    private val onFound: (List<FoundBike>) -> Unit,
    private val onButton: (String) -> Unit,
) {
    private val adapter: BluetoothAdapter? = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    val supported get() = adapter != null && ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    val enabled get() = adapter?.isEnabled == true

    private var gatt: BluetoothGatt? = null
    private val chars = mutableMapOf<String, BluetoothGattCharacteristic>()
    private var userDisconnect = false
    private var address: String? = null
    var deviceName: String = "Bike cluster"; private set
    var state = LinkState.IDLE; private set

    private fun set(s: LinkState, why: String? = null) { state = s; why?.let(onLog); onState(s, why) }

    // ---------- Scanning ----------
    private val found = linkedMapOf<String, FoundBike>()
    private var scanJob: Job? = null
    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, r: ScanResult) {
            val name = r.scanRecord?.deviceName ?: runCatching { r.device.name }.getOrNull() ?: return
            found[r.device.address] = FoundBike(r.device.address, name, r.rssi)
            onFound(found.values.sortedByDescending { it.rssi })
        }
        override fun onScanFailed(errorCode: Int) { onLog("Scan failed ($errorCode)"); stopScan(); set(LinkState.IDLE) }
    }

    /** Looks for bikes for 15 s. [showAll] lists every Bluetooth LE device nearby, not just clusters. */
    fun scan(showAll: Boolean) {
        val sc = adapter?.bluetoothLeScanner ?: run { onLog("Bluetooth is off."); return }
        stopScan()
        found.clear(); onFound(emptyList())
        userDisconnect = false
        set(LinkState.SCANNING, if (showAll) "Looking for every Bluetooth device…" else "Looking for your bike…")
        val filters = if (showAll) emptyList() else listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(Protocol.Uuid.SERVICE))).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        runCatching { sc.startScan(filters, settings, scanCb) }.onFailure { onLog("Scan failed: ${it.message}"); set(LinkState.IDLE); return }
        scanJob = scope.launch {
            delay(SCAN_MS)
            stopScan()
            if (state == LinkState.SCANNING) set(LinkState.IDLE, if (found.isEmpty()) "No bike found. Switch on the ignition and try again." else null)
        }
    }

    fun stopScan() {
        scanJob?.cancel(); scanJob = null
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCb) }
    }

    // ---------- Connection ----------
    fun connect(addr: String, name: String?) {
        val a = adapter ?: return
        stopScan()
        teardown()
        userDisconnect = false
        address = addr
        deviceName = name?.takeIf { it.isNotBlank() } ?: "Bike cluster"
        set(LinkState.CONNECTING, "Connecting to $deviceName…")
        val dev: BluetoothDevice = runCatching { a.getRemoteDevice(addr) }.getOrElse { onLog("Unknown address $addr"); set(LinkState.IDLE); return }
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) dev.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE) else dev.connectGatt(ctx, false, cb)
        // A cluster that never answers: give up this try and go back to searching.
        scope.launch {
            delay(20_000)
            if (state == LinkState.CONNECTING && address == addr) { onLog("No answer from the bike."); drop() }
        }
    }

    /** The rider pressed Disconnect, Stop or Cancel: clear the cluster's nav screen, then leave. */
    fun disconnect() {
        userDisconnect = true
        stopScan()
        reconnectJob?.cancel()
        val g = gatt
        val tbt = chars["tbt"]
        if (state == LinkState.CONNECTED && g != null && tbt != null) scope.launch {
            withTimeoutOrNull(600) { writeNow(g, tbt, Protocol.tbtStopFrame()) }
            teardown()
        } else teardown()
        set(LinkState.IDLE, "Disconnected by you. Tap Reconnect when you want it back.")
    }

    fun forget() { disconnect(); address = null }

    private fun teardown() {
        pollJob?.cancel(); pumpJob?.cancel()
        queue.tryReceiveAll()
        chars.clear()
        runCatching { gatt?.disconnect(); gatt?.close() }
        gatt = null
    }

    private var reconnectJob: Job? = null
    private var tries = 0
    private fun drop() {
        teardown()
        if (userDisconnect || address == null) { set(LinkState.IDLE, "Disconnected."); return }
        val wait = if (tries++ < 2) RECONNECT_MS else SCAN_PAUSE_MS
        set(LinkState.RECONNECTING, "Bike went quiet. Retrying in ${wait / 1000} s.")
        reconnectJob?.cancel()
        reconnectJob = scope.launch { delay(wait); address?.let { if (!userDisconnect) connect(it, deviceName) } }
    }

    // ---------- GATT ----------
    private var pendingWrite: CompletableDeferred<Boolean>? = null
    private var pendingRead: CompletableDeferred<ByteArray?>? = null

    private val cb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            scope.launch {
                if (g != gatt) return@launch
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    onLog("Connected. Asking for a bigger MTU…")
                    if (!g.requestMtu(256)) g.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    pendingWrite?.complete(false); pendingRead?.complete(null)
                    if (state == LinkState.CONNECTED) onLog("Link dropped (status $status).")
                    drop()
                }
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            scope.launch { onLog("MTU $mtu."); g.discoverServices() }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            scope.launch {
                val svc = g.getService(UUID.fromString(Protocol.Uuid.SERVICE))
                if (svc == null) { onLog("This device isn't a Waylink-compatible cluster."); userDisconnect = true; teardown(); set(LinkState.IDLE); return@launch }
                chars.clear()
                for ((k, u) in KEYS) {
                    val ch = svc.getCharacteristic(UUID.fromString(u))
                    if (ch != null) chars[k] = ch else onLog("Characteristic $k not offered by this cluster")
                }
                onLog("Services found.")
                tries = 0
                startButtons(g)
                startPump(g)
                set(LinkState.CONNECTED, "Linked to $deviceName.")
            }
        }

        @Deprecated("Deprecated in API 33") override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Deprecated("Deprecated in API 33") override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION") pendingRead?.complete(if (status == BluetoothGatt.GATT_SUCCESS) c.value else null)
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            pendingRead?.complete(if (status == BluetoothGatt.GATT_SUCCESS) value else null)
        }

        @Deprecated("Deprecated in API 33") override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION") val v = c.value ?: return
            scope.launch { handleControl(v) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            scope.launch { handleControl(value) }
        }
    }

    // ---------- Writes: one at a time, turns jump the queue ----------
    private val queue = Channel<Pair<String, ByteArray>>(capacity = 30, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    private var pumpJob: Job? = null
    var frames = 0; private set

    fun write(key: String, bytes: ByteArray) {
        if (state != LinkState.CONNECTED || chars[key] == null) return
        queue.trySend(key to bytes)
    }

    private fun startPump(g: BluetoothGatt) {
        pumpJob?.cancel()
        pumpJob = scope.launch {
            for ((k, b) in queue) {
                val ch = chars[k] ?: continue
                val ok = withTimeoutOrNull(1500) { writeNow(g, ch, b) } ?: false
                if (ok) frames++ else onLog("Write to $k failed")
            }
        }
    }

    private suspend fun writeNow(g: BluetoothGatt, ch: BluetoothGattCharacteristic, b: ByteArray): Boolean {
        val noResp = ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val type = if (noResp) BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val done = CompletableDeferred<Boolean>().also { pendingWrite = it }
        val started = if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(ch, b, type) == BluetoothGatt.GATT_SUCCESS
        else { @Suppress("DEPRECATION") run { ch.writeType = type; ch.value = b; g.writeCharacteristic(ch) } }
        if (!started) return false
        return done.await()
    }

    private suspend fun readNow(g: BluetoothGatt, ch: BluetoothGattCharacteristic): ByteArray? {
        val done = CompletableDeferred<ByteArray?>().also { pendingRead = it }
        if (!g.readCharacteristic(ch)) return null
        return withTimeoutOrNull(1000) { done.await() }
    }

    // ---------- Handlebar buttons ----------
    private var pollJob: Job? = null
    private var last: ByteArray? = null
    private var booted = false
    private var acceptAck = 0
    private var rejectAck = 0

    private suspend fun startButtons(g: BluetoothGatt) {
        val ch = chars["buttons"] ?: return
        booted = false; last = null
        if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 && g.setCharacteristicNotification(ch, true)) {
            ch.getDescriptor(CCCD)?.let { d ->
                // Wait for the cluster to take it, so the first status write doesn't collide with it.
                val done = CompletableDeferred<Boolean>().also { pendingWrite = it }
                val started = if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothGatt.GATT_SUCCESS
                else @Suppress("DEPRECATION") run { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
                if (started) withTimeoutOrNull(1500) { done.await() }
            }
            onLog("Listening to handlebar buttons.")
            return
        }
        pollJob = scope.launch {
            while (true) {
                delay(Protocol.BUTTON_POLL_MS)
                if (state != LinkState.CONNECTED || !queue.isEmpty) continue
                readNow(g, ch)?.let { handleControl(it) }
            }
        }
    }

    private fun handleControl(b: ByteArray) {
        if (b.size < 18) return
        if (last?.contentEquals(b) == true) return
        last = b.copyOf()
        val accept = b[1].toInt() and 0xff
        val reject = b[2].toInt() and 0xff
        if (!booted) { booted = true; acceptAck = accept; rejectAck = reject; return }
        if (accept != acceptAck) { acceptAck = accept; onLog("Handlebar tap"); onButton("tap") }
        if (reject != rejectAck) { rejectAck = reject; onLog("Handlebar long press"); onButton("long") }
    }

    companion object {
        private val KEYS = listOf(
            "tbt" to Protocol.Uuid.TBT, "general" to Protocol.Uuid.GENERAL, "missedCall" to Protocol.Uuid.MISSED_CALL,
            "alerts" to Protocol.Uuid.ALERTS, "buttons" to Protocol.Uuid.BUTTONS,
        )
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val SCAN_MS = 15_000L
        const val RECONNECT_MS = 4_000L
        const val SCAN_PAUSE_MS = 15_000L

        /** What Bluetooth needs on this Android version: nearby devices (12+), or location before that. */
        val PERMISSIONS: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

        fun hasPermissions(ctx: Context) = PERMISSIONS.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    }
}

private fun <T> Channel<T>.tryReceiveAll() { while (tryReceive().isSuccess) Unit }
