package app.waylink.core

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Linking a computer: the phone's side must match the web's (web/js/wlcrypto.js) byte for byte. */
class LinkTest {
    // Made by the web code (WebCrypto) for this test: the web's public key, the phone's private scalar, the QR secret,
    // what both sides must derive, and an op the web sealed.
    private val phoneD = "BwRz7qCfRi82Y4l9HbLQp_M-magccJX2P2p7hcAS8-4"
    private val webPub = "BCzf4gII-_bOg5htQOotZF9e-eBlkgtqshUZ1ALf4BwZegksoENsZLa7je1zLMdGJBfUgIfwhskOLvKoEsRiRYs"
    private val secret = "2cCNr5-tr-gom2uw2K7rug"
    private val key = "1k7jb4aqTCexTev26ZeKKk4kXn7OEWlnFeYR-GneAb4"
    private val token = "D0msuzvqeGXVhmkdCCstb7IrRhVFVHLQ-I4I4mOr5EI"
    private val linkId = "RxzRtpxfuNDoEnaQr55pH4HAiR7L4tLk"
    private val sealedOp = "xPNnsNay3VAZaDgsY94ASOiuwQK8vzx3OMshNzcrutYFlhBY32O8cSk4sf88vj36ukThl9YaFXgjAxPUsrPhznfM9Rgab4vztfO7lYxwF60SpUC79DTz8XkdY8btZ1PAgrPU6_v07G51AnX2Pul6JnkvoNlSP-QgrWVKStX6qhyVLMlyt4lhnglPf7jsvmxAZ0YKxwYCnWIQUE4qqwJlD39MpoQ3dUqLFVBF67XmyJTYEiei3rYqXcEQvzgHebkjVSR_DywmZrxaddnAhqeaQ_TSgFqg_eqdzq4TsDvdeU-zXsGkWKC8j2pRL1HTTWZt_-s5gXuJdsHtadkqvget6DX63gUtP4lXQFpVa9x-jd1yl1yQ-bes5eVVt6XPhDwa5k0hspm0UHN27dVts7pGDJQFmhcszGNqJm1KlUhp1w"

    @Test fun derivesTheSameLinkAsTheWeb() {
        val l = LinkCrypto.derive(LinkCrypto.privateFromD(phoneD), webPub, secret)
        assertEquals(key, LinkCrypto.b64url(l.key))
        assertEquals(token, l.token)
        assertEquals(linkId, l.linkId)
    }

    @Test fun opensWhatTheWebSealed() {
        val json = LinkCrypto.open(LinkCrypto.unb64url(key), linkId, "op", LinkCrypto.unb64url(sealedOp))
        assertTrue(json.contains("\"op\":\"batch\""))
        // Another kind, another link or a changed byte must all fail.
        assertFailsWith<Exception> { LinkCrypto.open(LinkCrypto.unb64url(key), linkId, "vault", LinkCrypto.unb64url(sealedOp)) }
        assertFailsWith<Exception> { LinkCrypto.open(LinkCrypto.unb64url(key), "x" + linkId.drop(1), "op", LinkCrypto.unb64url(sealedOp)) }
        val bad = LinkCrypto.unb64url(sealedOp).also { it[20] = (it[20].toInt() xor 1).toByte() }
        assertFailsWith<Exception> { LinkCrypto.open(LinkCrypto.unb64url(key), linkId, "op", bad) }
    }

    @Test fun sealsAndOpensBothWays() {
        val a = LinkCrypto.newKeyPair(); val b = LinkCrypto.newKeyPair()
        val s = LinkCrypto.b64url(ByteArray(16) { it.toByte() })
        val la = LinkCrypto.derive(a.private, LinkCrypto.publicRaw(b), s)
        val lb = LinkCrypto.derive(b.private, LinkCrypto.publicRaw(a), s)
        assertEquals(la.token, lb.token)
        assertEquals(87, LinkCrypto.publicRaw(a).length)
        assertEquals("{\"x\":1}", LinkCrypto.open(lb.key, lb.linkId, "vault", LinkCrypto.seal(la.key, la.linkId, "vault", "{\"x\":1}")))
    }

    @Test fun readsOnlyRealCodes() {
        val qr = "waylink://link?v=1&p=AAAAAAAAAAAAAAAAAAAAAA&s=BBBBBBBBBBBBBBBBBBBBBB&k=$webPub&r=waylink-relay.example.workers.dev"
        val q = assertNotNull(LinkCrypto.parseQr(qr))
        assertEquals("waylink-relay.example.workers.dev", q.relay)
        assertEquals("localhost:8787", LinkCrypto.parseQr(qr.replace("waylink-relay.example.workers.dev", "localhost%3A8787"))?.relay)
        assertNull(LinkCrypto.parseQr("https://example.com"))
        assertNull(LinkCrypto.parseQr(qr.replace("v=1", "v=2")))
        assertNull(LinkCrypto.parseQr(qr.replace("r=waylink", "r=evil/path?x=waylink")))
    }

    @Test fun appliesTheWebsChanges() {
        val s = Sample.seed(AppState())
        val v = s.vehicle
        val json = LinkCrypto.open(LinkCrypto.unb64url(key), linkId, "op", LinkCrypto.unb64url(sealedOp)).replace("\"v1\"", "\"${v.id}\"")
        val out = LinkSync.apply(s, json)
        assertEquals(v.fuel.size + 1, out.vehicle.fuel.size)
        assertEquals(12411.0, out.vehicle.odo)
        assertEquals(102.9, out.vehicle.fuelPrice)
        assertEquals("Daily", out.vehicle.nickname)
        // Delete, checklist, unknown op, unknown vehicle, bad colour.
        val id = out.vehicle.fuel.first().id
        assertEquals(v.fuel.size, LinkSync.apply(out, """{"op":"delete","vid":"${v.id}","coll":"fuel","itemId":"$id"}""").vehicle.fuel.size)
        assertEquals(out, LinkSync.apply(out, """{"op":"launch","vid":"${v.id}"}"""))
        assertEquals(out, LinkSync.apply(out, """{"op":"odo","vid":"nope","km":5}"""))
        assertEquals(out.vehicle.colour, LinkSync.apply(out, """{"op":"patch","vid":"${v.id}","patch":{"colour":"red; x"}}""").vehicle.colour)
        val c = LinkSync.apply(out, """{"op":"upsert","vid":"${v.id}","coll":"checklist","item":{"id":"c1","text":"Horn","done":false}}""")
        assertTrue(c.vehicle.checklist.any { it.id == "c1" && it.text == "Horn" })
    }

    @Test fun vaultLeavesPrivateThingsOnThePhone() {
        val s = Sample.seed(AppState()).let { it.copy(profile = it.profile.copy(blood = "B+", icePhone = "+91 98450 00000", medical = "Penicillin")) }
        val text = LinkSync.vault(s, 3, "Pixel 8", 1L)
        for (x in listOf("B+", "98450", "Penicillin", "\"parking\"", "\"glove\"", "\"places\"")) assertTrue(!text.contains(x), x)
        // The call-alert log stays on the phone (settings.alerts, the on/off switches, is fine to show).
        val st = Vault.json.parseToJsonElement(text).jsonObject["state"]!!.jsonObject
        assertTrue("alerts" !in st && "bike" !in st["vehicles"]!!.jsonArray[0].jsonObject)
        assertTrue(text.contains("\"ackSeq\":3") && text.contains("\"waylink-sync\""))
    }
}
