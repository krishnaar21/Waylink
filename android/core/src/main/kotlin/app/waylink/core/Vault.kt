package app.waylink.core

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant

/** Saving, loading and backups. The JSON is the same shape the web app writes, so backups move both ways. */
object Vault {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    fun encode(s: AppState): String = json.encodeToString(AppState.serializer(), s)

    /** Reads saved state; anything unreadable starts fresh rather than crashing. */
    fun decode(text: String?): AppState {
        if (text.isNullOrBlank()) return freshState()
        return try { normalise(json.decodeFromString(AppState.serializer(), text)) } catch (e: SerializationException) { freshState() } catch (e: IllegalArgumentException) { freshState() }
    }

    /** A backup file: { format: "waylink-vault", version: 2, exported, data }. Pairing is left out. */
    fun export(s: AppState, now: Long = System.currentTimeMillis()): String {
        val data = s.copy(vehicles = s.vehicles.map { it.copy(bike = PairedBike()) })
        val obj = buildJsonObject {
            put("format", "waylink-vault")
            put("version", 2)
            put("exported", Instant.ofEpochMilli(now).toString())
            put("data", json.encodeToJsonElement(AppState.serializer(), data))
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    class NotABackup : Exception("That isn’t a backup from this app.")

    private val FORMATS = setOf("waylink-vault", "rev-vault", "kickstart-vault", "pulsarnav-vault")

    /** Accepts a wrapped backup or bare state from the web app or this app. Old single-bike (v1) backups are not supported here yet. */
    fun import(text: String): AppState {
        val root = try { json.parseToJsonElement(text).jsonObject } catch (e: Exception) { throw NotABackup() }
        val fmt = root["format"]?.jsonPrimitive?.contentOrNull
        val data = if (fmt in FORMATS) (root["data"] as? JsonObject) ?: throw NotABackup() else root
        val vehicles = try { data["vehicles"]?.jsonArray } catch (e: IllegalArgumentException) { null }
        if (vehicles.isNullOrEmpty()) throw NotABackup()
        val s = try { json.decodeFromJsonElement(AppState.serializer(), data) } catch (e: Exception) { throw NotABackup() }
        return normalise(s).copy(onboarded = true)
    }

    /** Keeps state valid: a vehicle always exists, the active one is real, the theme is one Android offers. */
    fun normalise(s: AppState): AppState {
        var out = s
        if (out.vehicles.isEmpty()) { val v = newVehicle(); out = out.copy(vehicles = listOf(v), active = v.id) }
        if (out.vehicles.none { it.id == out.active }) out = out.copy(active = out.vehicles.first().id)
        if (out.settings.theme !in setOf("auto", "sketch", "sketchdark")) out = out.copy(settings = out.settings.copy(theme = "auto"))
        if (!Regex("^a([1-9]|1[01])$").matches(out.profile.avatar)) out = out.copy(profile = out.profile.copy(avatar = "a1"))
        return out
    }
}
