package app.waylink.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * What the phone sends to a linked computer, and how it applies the changes made there.
 * Same rules as relay/PROTOCOL.md and web/js/ops.js; the phone stays the one source of truth.
 */
object LinkSync {
    val COLLS = setOf("fuel", "records", "service", "checklist", "trips")
    private val VEHICLE_TEXT = mapOf("nickname" to 20, "plate" to 13, "model" to 20)
    private val PROFILE_TEXT = mapOf("name" to 30, "city" to 24, "country" to 2)
    private val COLOUR = Regex("^#[0-9A-Fa-f]{6}$")
    /** Kept on the phone only: Medical ID, contact details, documents, call alerts, places, pairing and parking. */
    private val PRIVATE_PROFILE = setOf("blood", "medical", "iceName", "icePhone", "phone", "email", "licenceNo", "photo")

    /** The vault: the app state minus private fields, with the last op applied and who sent it. */
    fun vault(s: AppState, ackSeq: Long, device: String, now: Long = System.currentTimeMillis()): String {
        val st = Vault.json.encodeToJsonElement(AppState.serializer(), s).jsonObject
        val profile = JsonObject(st["profile"]!!.jsonObject.filterKeys { it !in PRIVATE_PROFILE })
        val vehicles = JsonArray(st["vehicles"]!!.jsonArray.map { v -> JsonObject(v.jsonObject.filterKeys { it != "bike" && it != "parking" }) })
        val state = JsonObject(st.filterKeys { it !in setOf("alerts", "glove", "places") } + mapOf("profile" to profile, "vehicles" to vehicles))
        val out = JsonObject(mapOf(
            "format" to JsonPrimitive("waylink-sync"), "v" to JsonPrimitive(1), "state" to state,
            "ackSeq" to JsonPrimitive(ackSeq), "device" to JsonPrimitive(device.take(60)), "at" to JsonPrimitive(now),
        ))
        return Vault.json.encodeToString(JsonObject.serializer(), out)
    }

    /** Applies one op from the web. Anything unknown or invalid leaves the state as it was. */
    fun apply(s: AppState, opJson: String): AppState = try {
        val op = Vault.json.parseToJsonElement(opJson).jsonObject
        val st = Vault.json.encodeToJsonElement(AppState.serializer(), s).jsonObject
        val next = applyOne(st, op, depth = 0) ?: return s
        Vault.normalise(Vault.json.decodeFromJsonElement(AppState.serializer(), next))
    } catch (e: Exception) { s }

    private fun str(e: JsonElement?) = (e as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun num(e: JsonElement?) = (e as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    private fun applyOne(st: JsonObject, op: JsonObject, depth: Int): JsonObject? {
        val kind = str(op["op"]) ?: return null
        if (kind == "batch") {
            if (depth > 0) return null
            var cur = st; var any = false
            (op["ops"] as? JsonArray)?.take(50)?.forEach { o -> (o as? JsonObject)?.let { applyOne(cur, it, 1) }?.let { cur = it; any = true } }
            return if (any) cur else null
        }
        val vid = str(op["vid"])
        if (kind == "patch" && vid == null) {
            val patch = op["patch"] as? JsonObject ?: return null
            val p = st["profile"]!!.jsonObject.toMutableMap()
            for ((k, max) in PROFILE_TEXT) str(patch[k])?.let { p[k] = JsonPrimitive(it.trim().take(max)) }
            if (str(patch["country"])?.let { c -> Catalog.COUNTRIES.none { it.code == c } } == true) return null
            return JsonObject(st + ("profile" to JsonObject(p)))
        }
        val vehicles = st["vehicles"]?.jsonArray ?: return null
        if (vehicles.none { str(it.jsonObject["id"]) == vid }) return null
        var changed = false
        val out = vehicles.map { ve ->
            val v = ve.jsonObject
            if (str(v["id"]) != vid) return@map v
            val nv = when (kind) {
                "upsert", "delete" -> {
                    val coll = str(op["coll"])?.takeIf { it in COLLS } ?: return null
                    val list = (v[coll] as? JsonArray) ?: JsonArray(emptyList())
                    val items = if (kind == "delete") {
                        val id = str(op["itemId"]) ?: return null
                        list.filter { str(it.jsonObject["id"]) != id }.also { if (it.size == list.size) return null }
                    } else {
                        val item = op["item"] as? JsonObject ?: return null
                        val id = str(item["id"])?.takeIf { it.isNotEmpty() && it.length <= 64 } ?: return null
                        val i = list.indexOfFirst { str(it.jsonObject["id"]) == id }
                        if (i >= 0) list.toMutableList().also { it[i] = JsonObject(list[i].jsonObject + item) } else list + item
                    }
                    JsonObject(v + (coll to JsonArray(items)))
                }
                "patch" -> {
                    val patch = op["patch"] as? JsonObject ?: return null
                    val m = v.toMutableMap()
                    for ((k, max) in VEHICLE_TEXT) str(patch[k])?.let { m[k] = JsonPrimitive(it.trim().take(max)) }
                    str(patch["colour"])?.takeIf { COLOUR.matches(it) }?.let { m["colour"] = JsonPrimitive(it) }
                    num(patch["fuelPrice"])?.takeIf { it > 0 && it < 100_000 }?.let { m["fuelPrice"] = JsonPrimitive(it) }
                    if (str(patch["model"])?.let { md -> Catalog.MODELS.none { it.id == md } } == true) m["model"] = v["model"] ?: JsonNull
                    JsonObject(m)
                }
                "odo" -> {
                    val km = num(op["km"])?.takeIf { it >= 0 && it < 10_000_000 } ?: return null
                    JsonObject(v + ("odo" to JsonPrimitive(Math.round(km * 10) / 10.0)))
                }
                else -> return null
            }
            changed = true; nv
        }
        return if (changed) JsonObject(st + ("vehicles" to JsonArray(out))) else null
    }
}
