package app.waylink.core

/**
 * The OTC Engineering cluster protocol, ported byte for byte from web-v0/js/protocol.js
 * (itself a port of PulsarNav's OtcPacketBuilder). Do not change layouts without checking on a cluster.
 */
object Protocol {
    object Uuid {
        const val SERVICE = "0010676e-6972-6565-6e69-676e4543544f"
        const val TBT = "0110676e-6972-6565-6e69-676e4543544f"
        const val GENERAL = "0210676e-6972-6565-6e69-676e4543544f"
        const val MISSED_CALL = "0310676e-6972-6565-6e69-676e4543544f"
        const val ALERTS = "0410676e-6972-6565-6e69-676e4543544f"
        const val BUTTONS = "0a10676e-6972-6565-6e69-676e4543544f"
    }
    const val TBT = 48; const val GENERAL = 89; const val LEGACY = 55; const val ALERT = 44; const val MISSED = 63
    const val TBT_STREAM_MS = 800L; const val HEARTBEAT_MS = 5000L; const val BUTTON_POLL_MS = 700L

    object Call { const val NO_CALL = 0; const val INCOMING = 1; const val OUTGOING = 2; const val ACTIVE = 3; const val END = 4 }

    private fun sum8(b: ByteArray, n: Int): Byte { var s = 0; for (i in 0 until n) s = (s + (b[i].toInt() and 0xff)) and 0xff; return s.toByte() }
    private fun ascii(s: String) = ByteArray(s.length) { (s[it].code and 0x7f).toByte() }

    fun sanitizeAscii(input: String?, max: Int): String {
        if (input.isNullOrBlank()) return ""
        val c = input.replace(Regex("[^\\x20-\\x7E]"), " ").trim()
        return if (c.length > max) c.substring(0, max) else c
    }
    private fun sanitizePhone(p: String?): String {
        if (p.isNullOrBlank()) return ""
        val d = p.filter { it.isDigit() || it == '+' }
        return if (d.length > 18) d.substring(0, 18) else d
    }

    /** [unit, iHigh, iLow, fracHigh, fracLow]; metres below 1000 (unit 1), else km with 2 decimals (unit 0). */
    fun formatDistance(d: Double): IntArray {
        val unit: Int; val v: Double
        if (d < 1000) { v = Math.floor(d); unit = 1 } else { v = d / 1000; unit = 0 }
        val i = v.toLong().toInt()
        val frac = ((v - i) * 100).toLong().toInt()
        return intArrayOf(unit, (i shr 8) and 0xff, i and 0xff, (frac shr 8) and 0xff, frac and 0xff)
    }

    /** 48-byte turn-by-turn frame. */
    fun tbtFrame(
        maneuverIcon: Int, stepMeters: Double, totalMeters: Double, etaHour: Int = 12, etaMinute: Int = 0, isAm: Boolean = true,
        roundaboutExit: Int = 0, streetText: String = "", type: Int = 1,
    ): ByteArray {
        val step = formatDistance(maxOf(0.0, stepMeters))
        val total = formatDistance(maxOf(0.0, totalMeters))
        var t = streetText.replace(Regex("[^0-9a-zA-Z.]"), " ").trim()
        if (t.length > 31) t = t.substring(0, 31) + "."
        val b = ByteArray(TBT)
        var b0 = type and 1
        b0 = b0 or ((step[0] and 1) shl 4)
        b0 = b0 or ((if (isAm) 0 else 1) shl 7)
        b[0] = b0.toByte()
        var icon = maneuverIcon
        if (stepMeters in 0.0..99.0 && icon != 72 && icon < 98) icon += 32 // blinks under 100 m
        b[1] = icon.toByte()
        b[2] = step[4].toByte(); b[3] = step[3].toByte(); b[4] = step[2].toByte(); b[5] = step[1].toByte()
        b[6] = etaMinute.toByte()
        val h = if (etaHour in 1..12) etaHour else 12
        b[7] = ((h and 0x0f) or ((roundaboutExit and 0x0f) shl 4)).toByte()
        b[8] = total[4].toByte(); b[9] = total[3].toByte(); b[10] = total[2].toByte(); b[11] = total[1].toByte()
        b[12] = ((total[0] and 1) or (1 shl 2)).toByte() // GPS active
        if (t.isNotEmpty()) { val tb = ascii(t); val n = minOf(tb.size, 31); b[14] = n.toByte(); tb.copyInto(b, 15, 0, n) }
        b[47] = sum8(b, 47)
        return b
    }
    fun tbtStopFrame() = tbtFrame(0, 0.0, 0.0, 0, 0, true, 0, "", type = 0)

    private var heartbeat = 0
    private fun batBars4(p: Int) = if (p >= 75) 3 else if (p >= 50) 2 else if (p >= 25) 1 else 0

    /** 89-byte General Status (V2), sent after link-up and every 5 s. */
    fun generalV2(batteryPercent: Int, callState: Int = 0, callerName: String? = null, missedCallCount: Int = 0, volumeLevel: Int = 8, signalBars: Int = 4, phoneModel: String = "Android"): ByteArray {
        heartbeat = (heartbeat + 1) and 0xff
        val b = ByteArray(GENERAL)
        b[0] = ((volumeLevel.coerceIn(0, 15) and 0x0f) or 0xc0).toByte()
        b[1] = (((batBars4(batteryPercent) and 3) shl 4) or (callState and 0x0f)).toByte()
        b[2] = (signalBars.coerceIn(0, 7) and 7).toByte()
        b[13] = missedCallCount.coerceIn(0, 255).toByte()
        val hasCaller = callState == Call.INCOMING || callState == Call.ACTIVE
        if (hasCaller && !callerName.isNullOrBlank()) {
            b[18] = 1
            var clean = sanitizeAscii(callerName, 32)
            if (clean.isNotEmpty() && clean.length <= 6) clean += "   " // stops "DADADAD" scrolling
            val bytes = ascii(clean); val len = minOf(bytes.size, 32)
            bytes.copyInto(b, 20, 0, len); b[19] = len.toByte()
        }
        b[54] = heartbeat.toByte()
        val mb = ascii(sanitizeAscii(phoneModel, 32)); val ml = minOf(mb.size, 32)
        b[55] = ml.toByte(); mb.copyInto(b, 56, 0, ml)
        b[88] = sum8(b, 88)
        return b
    }

    private fun putEpoch(b: ByteArray, at: Int, nowMs: Long) {
        val s = nowMs / 1000
        b[at] = (s ushr 24).toByte(); b[at + 1] = (s ushr 16).toByte(); b[at + 2] = (s ushr 8).toByte(); b[at + 3] = s.toByte()
    }

    /** 44-byte WhatsApp alert: the sender's name only, never the message. */
    fun whatsAppAlert(sender: String, nowMs: Long = System.currentTimeMillis()): ByteArray {
        val b = ByteArray(ALERT); b[0] = 2
        val nb = ascii(sanitizeAscii(sender, 32)); val n = minOf(nb.size, 32)
        b[1] = n.toByte(); nb.copyInto(b, 2, 0, n); putEpoch(b, 34, nowMs)
        return b
    }

    /** 63-byte missed call. */
    fun missedCall(name: String, number: String? = null, nowMs: Long = System.currentTimeMillis()): ByteArray {
        val b = ByteArray(MISSED); b[0] = 1
        val nb = ascii(sanitizeAscii(name, 32)); val n = minOf(nb.size, 32)
        b[1] = n.toByte(); nb.copyInto(b, 2, 0, n)
        val pb = ascii(sanitizePhone(number)); val pn = minOf(pb.size, 18)
        b[34] = pn.toByte(); pb.copyInto(b, 35, 0, pn); putEpoch(b, 53, nowMs)
        return b
    }

    fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
}
