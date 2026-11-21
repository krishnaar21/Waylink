package app.waylink.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.net.HttpURLConnection
import java.net.URL

/** A computer linked by QR code. [key] is the AES key, base64url. Never leaves the phone except as the relay's bearer [token]. */
@Serializable
data class Computer(
    val linkId: String,
    val token: String,
    val key: String,
    val relay: String,
    val linkedAt: Long,
    val ackSeq: Long = 0,
    val syncedAt: Long = 0,
)

/**
 * Talks to the relay (relay/PROTOCOL.md). Plain HttpURLConnection, blocking: call it off the main thread.
 * Everything sent is sealed first, so the relay only ever sees locked bytes.
 */
class LinkClient(private val timeoutMs: Int = 15_000) {
    /** The link is gone (unlinked from the computer, or unused for 120 days). */
    class Gone : Exception("unlinked")
    /** The QR code was already used or has expired. */
    class Expired : Exception("expired")

    private fun base(relay: String): String {
        val host = relay.substringBefore(':')
        // Only the local dev relay is plain http; Android blocks cleartext anyway.
        return if (host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2") "http://$relay" else "https://$relay"
    }

    private class Res(val code: Int, val body: ByteArray)

    private fun call(url: String, method: String, token: String? = null, body: ByteArray? = null, type: String = "application/octet-stream"): Res {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
            c.useCaches = false
            c.instanceFollowRedirects = false
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", type)
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val bytes = stream?.use { s -> s.readBytesCapped(5 * 1024 * 1024) } ?: ByteArray(0)
            if (code == 401 || code == 410) throw Gone()
            return Res(code, bytes)
        } finally { c.disconnect() }
    }

    private fun java.io.InputStream.readBytesCapped(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buf = ByteArray(16 * 1024)
        while (true) { val n = read(buf); if (n < 0) break; out.write(buf, 0, n); require(out.size() <= max) { "Too big" } }
        return out.toByteArray()
    }

    /** Answers the computer's QR code. Returns the new link; the caller uploads the first vault straight after. */
    fun pair(qr: LinkCrypto.Qr, device: String, now: Long = System.currentTimeMillis()): Computer {
        val kp = LinkCrypto.newKeyPair()
        val l = LinkCrypto.derive(kp.private, qr.pub, qr.secret)
        val hello = JsonObject(mapOf("device" to JsonPrimitive(device.take(60)), "platform" to JsonPrimitive("android"), "at" to JsonPrimitive(now)))
        val box = LinkCrypto.b64url(LinkCrypto.seal(l.key, l.linkId, "hello", hello.toString()))
        val body = JsonObject(mapOf("pub" to JsonPrimitive(LinkCrypto.publicRaw(kp)), "box" to JsonPrimitive(box))).toString()
        val r = try { call("${base(qr.relay)}/v1/pair/${qr.pairId}", "POST", body = body.toByteArray(), type = "application/json") } catch (e: Gone) { throw Expired() }
        if (r.code == 404 || r.code == 409) throw Expired()
        check(r.code in 200..299) { "Pairing failed (${r.code})" }
        return Computer(l.linkId, l.token, LinkCrypto.b64url(l.key), qr.relay, now)
    }

    fun push(c: Computer, vaultJson: String) {
        val r = call("${base(c.relay)}/v1/link/${c.linkId}/vault", "PUT", c.token, LinkCrypto.seal(LinkCrypto.unb64url(c.key), c.linkId, "vault", vaultJson))
        check(r.code in 200..299) { "Upload failed (${r.code})" }
    }

    /** The web's changes after [Computer.ackSeq], opened, in order. A change that won't open is skipped (and acknowledged). */
    fun pull(c: Computer): Pair<List<String>, Long> {
        val r = call("${base(c.relay)}/v1/link/${c.linkId}/ops?after=${c.ackSeq}", "GET", c.token)
        check(r.code == 200) { "Fetch failed (${r.code})" }
        val o = Vault.json.parseToJsonElement(String(r.body, Charsets.UTF_8)).jsonObject
        val key = LinkCrypto.unb64url(c.key)
        var last = c.ackSeq
        val ops = o["ops"]!!.jsonArray.mapNotNull { e ->
            val op = e.jsonObject
            val seq = op["seq"]!!.jsonPrimitive.long
            if (seq <= c.ackSeq) return@mapNotNull null
            last = maxOf(last, seq)
            runCatching { LinkCrypto.open(key, c.linkId, "op", LinkCrypto.unb64url(op["data"]!!.jsonPrimitive.content)) }.getOrNull()
        }
        return ops to last
    }

    /** Drops changes the phone has applied. */
    fun ack(c: Computer, upto: Long) {
        if (upto > 0) call("${base(c.relay)}/v1/link/${c.linkId}/ops?upto=$upto", "DELETE", c.token)
    }

    /** Wipes the link on the relay. The computer forgets the phone the next time it checks. */
    fun unlink(c: Computer) { try { call("${base(c.relay)}/v1/link/${c.linkId}", "DELETE", c.token) } catch (e: Gone) { } }
}
