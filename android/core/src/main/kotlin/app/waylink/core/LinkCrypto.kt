package app.waylink.core

import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Linking a computer (Waylink for desktop) by QR code: the key exchange and the sealing, byte for byte
 * as relay/PROTOCOL.md and web/js/wlcrypto.js. P-256 ECDH, HKDF-SHA256, AES-256-GCM. Plain JDK crypto.
 */
object LinkCrypto {
    private val rnd = SecureRandom()
    // The curve's parameters, taken from a generated key (works the same on the JDK and on Android).
    private val P256: ECParameterSpec by lazy { (newKeyPair().public as ECPublicKey).params }

    fun b64url(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    fun unb64url(s: String): ByteArray = Base64.getUrlDecoder().decode(s.trimEnd('='))

    /** What the browser's QR code holds. */
    data class Qr(val pairId: String, val secret: String, val pub: String, val relay: String)

    private val ID = Regex("^[A-Za-z0-9_-]+$")
    private val HOST = Regex("^[A-Za-z0-9.-]{1,253}(:[0-9]{1,5})?$")

    /** Reads waylink://link?v=1&p=…&s=…&k=…&r=…, or null if it isn't one. */
    fun parseQr(text: String): Qr? {
        if (!text.startsWith("waylink://link?")) return null
        val q = text.substringAfter('?').split('&').mapNotNull { kv ->
            val i = kv.indexOf('='); if (i <= 0) null else kv.substring(0, i) to java.net.URLDecoder.decode(kv.substring(i + 1), "UTF-8")
        }.toMap()
        if (q["v"] != "1") return null
        val p = q["p"] ?: return null; val s = q["s"] ?: return null; val k = q["k"] ?: return null; val r = q["r"] ?: return null
        if (p.length != 22 || !ID.matches(p) || s.length != 22 || !ID.matches(s) || k.length != 87 || !ID.matches(k) || !HOST.matches(r)) return null
        return Qr(p, s, k, r)
    }

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), rnd) }.generateKeyPair()

    /** The 65-byte uncompressed point, base64url. */
    fun publicRaw(kp: KeyPair): String {
        val w = (kp.public as ECPublicKey).w
        return b64url(byteArrayOf(4) + fixed(w.affineX) + fixed(w.affineY))
    }
    private fun fixed(n: BigInteger): ByteArray {
        val b = n.toByteArray()
        return when {
            b.size == 32 -> b
            b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
            else -> ByteArray(32 - b.size) + b
        }
    }
    private fun importPublic(raw: String): java.security.PublicKey {
        val b = unb64url(raw)
        require(b.size == 65 && b[0] == 4.toByte()) { "Not a P-256 public key" }
        val pt = ECPoint(BigInteger(1, b.copyOfRange(1, 33)), BigInteger(1, b.copyOfRange(33, 65)))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(pt, P256))
    }
    /** For tests: a private key from its 32-byte scalar (a JWK's "d"). */
    fun privateFromD(d: String): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(BigInteger(1, unb64url(d)), P256))

    /** key: AES-256 key; token: relay bearer token (base64url); linkId: base64url(SHA-256(token))[0..32). */
    class Link(val key: ByteArray, val token: String, val linkId: String)

    fun derive(own: PrivateKey, otherPub: String, qrSecret: String): Link {
        val ka = KeyAgreement.getInstance("ECDH"); ka.init(own); ka.doPhase(importPublic(otherPub), true)
        val okm = hkdf(ka.generateSecret(), unb64url(qrSecret), "waylink-link-v1".toByteArray(), 64)
        val token = b64url(okm.copyOfRange(32, 64))
        return Link(okm.copyOfRange(0, 32), token, linkIdOf(token))
    }
    fun linkIdOf(token: String): String = b64url(MessageDigest.getInstance("SHA-256").digest(unb64url(token))).take(32)

    private fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, len: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = java.io.ByteArrayOutputStream(); var t = ByteArray(0); var i = 1
        while (out.size() < len) { mac.update(t); mac.update(info); mac.update(i.toByte()); t = mac.doFinal(); out.write(t); i++ }
        return out.toByteArray().copyOf(len)
    }

    /** 12-byte IV, then AES-GCM ciphertext and tag; additional data "<linkId>:<kind>". */
    fun seal(key: ByteArray, linkId: String, kind: String, json: String): ByteArray {
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD("$linkId:$kind".toByteArray())
        return iv + c.doFinal(json.toByteArray(Charsets.UTF_8))
    }
    /** Throws if the bytes were changed, sealed for another link or kind, or with another key. */
    fun open(key: ByteArray, linkId: String, kind: String, bytes: ByteArray): String {
        require(bytes.size >= 29) { "Too short" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes, 0, 12))
        c.updateAAD("$linkId:$kind".toByteArray())
        return String(c.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    }
}
