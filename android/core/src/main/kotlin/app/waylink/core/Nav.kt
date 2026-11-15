package app.waylink.core

import kotlin.math.max
import kotlin.math.roundToLong

/** A turn the cluster can show. Mirrors MANEUVERS in web-v0/js/protocol.js (op is the cluster's code). */
data class Maneuver(val key: String, val op: Int, val label: String, val glyph: String)

object Maneuvers {
    val ALL = listOf(
        Maneuver("STRAIGHT", 71, "Continue straight", "straight"),
        Maneuver("TURN_LEFT", 73, "Turn left", "left"),
        Maneuver("TURN_RIGHT", 74, "Turn right", "right"),
        Maneuver("TURN_SLIGHT_LEFT", 67, "Slight left", "slight-left"),
        Maneuver("TURN_SLIGHT_RIGHT", 68, "Slight right", "slight-right"),
        Maneuver("TURN_SHARP_LEFT", 69, "Sharp left", "sharp-left"),
        Maneuver("TURN_SHARP_RIGHT", 70, "Sharp right", "sharp-right"),
        Maneuver("KEEP_LEFT", 90, "Keep left", "keep-left"),
        Maneuver("KEEP_RIGHT", 88, "Keep right", "keep-right"),
        Maneuver("FORK_LEFT", 81, "Fork left", "fork-left"),
        Maneuver("FORK_RIGHT", 82, "Fork right", "fork-right"),
        Maneuver("RAMP_LEFT", 75, "Ramp left", "ramp-left"),
        Maneuver("RAMP_RIGHT", 76, "Ramp right", "ramp-right"),
        Maneuver("MERGE", 86, "Merge", "merge"),
        Maneuver("ROUNDABOUT_LEFT", 85, "Roundabout left", "roundabout-left"),
        Maneuver("ROUNDABOUT_RIGHT", 78, "Roundabout right", "roundabout-right"),
        Maneuver("U_TURN_LEFT", 79, "U-turn left", "uturn-left"),
        Maneuver("U_TURN_RIGHT", 80, "U-turn right", "uturn-right"),
        Maneuver("DESTINATION_REACHED", 72, "Destination", "destination"),
        Maneuver("FERRY", 89, "Ferry", "ferry"),
        Maneuver("FERRY_TRAIN", 84, "Ferry train", "train"),
        Maneuver("WRONG_WAY", 66, "Wrong way", "wrong-way"),
    )
    fun of(key: String?): Maneuver = ALL.firstOrNull { it.key == key } ?: ALL[0]

    /** The groups on the "Test one turn" sheet. */
    val GROUPS = listOf(
        "Turns" to listOf("TURN_LEFT", "TURN_RIGHT", "TURN_SLIGHT_LEFT", "TURN_SLIGHT_RIGHT", "TURN_SHARP_LEFT", "TURN_SHARP_RIGHT"),
        "Lanes" to listOf("KEEP_LEFT", "KEEP_RIGHT", "FORK_LEFT", "FORK_RIGHT", "RAMP_LEFT", "RAMP_RIGHT", "MERGE"),
        "Roundabouts & U-turns" to listOf("ROUNDABOUT_LEFT", "ROUNDABOUT_RIGHT", "U_TURN_LEFT", "U_TURN_RIGHT"),
        "Other" to listOf("STRAIGHT", "DESTINATION_REACHED", "FERRY", "FERRY_TRAIN", "WRONG_WAY"),
    )
}

data class RouteStep(val maneuver: String, val m: Double, val street: String, val say: String, val exit: Int = 0)

/** The sample ride used to rehearse turns. Mirrors REHEARSAL in web-v0/js/views.js. */
object Rehearsal {
    const val NAME = "Bengaluru → Nandi Hills"
    val STEPS = listOf(
        RouteStep("STRAIGHT", 1400.0, "Ballari Road", "Continue on Ballari Road"),
        RouteStep("KEEP_LEFT", 800.0, "Hebbal Flyover", "Keep left onto Hebbal Flyover"),
        RouteStep("MERGE", 600.0, "NH 44", "Merge onto NH 44"),
        RouteStep("KEEP_RIGHT", 14200.0, "NH 44", "Keep right to stay on NH 44"),
        RouteStep("TURN_LEFT", 9600.0, "Devanahalli", "Turn left after Devanahalli"),
        RouteStep("ROUNDABOUT_RIGHT", 3400.0, "Karahalli Cross", "At Karahalli Cross, take the 2nd exit", 2),
        RouteStep("TURN_SLIGHT_RIGHT", 4800.0, "Nandi Hills Road", "Slight right onto Nandi Hills Road"),
        RouteStep("TURN_SHARP_LEFT", 1200.0, "Ghat Road", "Sharp left up the ghat road"),
        RouteStep("DESTINATION_REACHED", 300.0, "Nandi Hills", "Arrive at Nandi Hills"),
    )
    val totalMeters: Double get() = STEPS.sumOf { it.m }
}

/** A distance as the cluster shows it: "340" m (to 10 m) or "1.4" km (whole km from 10 km). */
fun distText(m: Double, f: Fmt = Fmt()): Pair<String, String> =
    if (m < 1000) f.num(max(0.0, (m / 10).roundToLong() * 10.0)) to "m"
    else f.num(m / 1000, if (m < 10000) 1 else 0) to "km"
