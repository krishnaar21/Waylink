package app.waylink.core

/**
 * Petrol prices by Indian state, and which state a city is in. Copied from FUEL_PRICES and CITY_STATE in
 * web-v0/js/store.js (rates as of 2026-11-09). Pumps can differ by a rupee or two within a state.
 */
object FuelPrices {
    const val AS_OF = "2026-11-09"
    val STATES: Map<String, Double> = mapOf(
        "Andhra Pradesh" to 117.51,
        "Telangana" to 116.15,
        "Kerala" to 115.49,
        "Madhya Pradesh" to 114.54,
        "West Bengal" to 113.51,
        "Bihar" to 113.37,
        "Rajasthan" to 113.19,
        "Maharashtra" to 111.21,
        "Karnataka" to 110.82,
        "Odisha" to 108.97,
        "Tamil Nadu" to 107.76,
        "Punjab" to 105.56,
        "Haryana" to 102.97,
        "Delhi" to 102.12,
        "Uttar Pradesh" to 101.86,
        "Chandigarh" to 101.54,
        "Uttarakhand" to 99.54,
        "Dadra and Nagar Haveli and Daman and Diu" to 99.44,
        "Arunachal Pradesh" to 97.96,
        "Andaman and Nicobar Islands" to 88.66,
    )
    private val CITY_STATE: Map<String, String> = mapOf(
        "bengaluru" to "Karnataka",
        "bangalore" to "Karnataka",
        "mysuru" to "Karnataka",
        "mysore" to "Karnataka",
        "mangaluru" to "Karnataka",
        "mangalore" to "Karnataka",
        "hubballi" to "Karnataka",
        "hubli" to "Karnataka",
        "belagavi" to "Karnataka",
        "mumbai" to "Maharashtra",
        "pune" to "Maharashtra",
        "nagpur" to "Maharashtra",
        "nashik" to "Maharashtra",
        "thane" to "Maharashtra",
        "aurangabad" to "Maharashtra",
        "chennai" to "Tamil Nadu",
        "coimbatore" to "Tamil Nadu",
        "madurai" to "Tamil Nadu",
        "trichy" to "Tamil Nadu",
        "tiruchirappalli" to "Tamil Nadu",
        "salem" to "Tamil Nadu",
        "hyderabad" to "Telangana",
        "secunderabad" to "Telangana",
        "warangal" to "Telangana",
        "vijayawada" to "Andhra Pradesh",
        "visakhapatnam" to "Andhra Pradesh",
        "vizag" to "Andhra Pradesh",
        "tirupati" to "Andhra Pradesh",
        "guntur" to "Andhra Pradesh",
        "amaravati" to "Andhra Pradesh",
        "thiruvananthapuram" to "Kerala",
        "trivandrum" to "Kerala",
        "kochi" to "Kerala",
        "cochin" to "Kerala",
        "kozhikode" to "Kerala",
        "calicut" to "Kerala",
        "thrissur" to "Kerala",
        "kolkata" to "West Bengal",
        "calcutta" to "West Bengal",
        "siliguri" to "West Bengal",
        "durgapur" to "West Bengal",
        "howrah" to "West Bengal",
        "delhi" to "Delhi",
        "new delhi" to "Delhi",
        "gurugram" to "Haryana",
        "gurgaon" to "Haryana",
        "faridabad" to "Haryana",
        "panipat" to "Haryana",
        "ambala" to "Haryana",
        "noida" to "Uttar Pradesh",
        "lucknow" to "Uttar Pradesh",
        "kanpur" to "Uttar Pradesh",
        "ghaziabad" to "Uttar Pradesh",
        "agra" to "Uttar Pradesh",
        "varanasi" to "Uttar Pradesh",
        "prayagraj" to "Uttar Pradesh",
        "jaipur" to "Rajasthan",
        "jodhpur" to "Rajasthan",
        "udaipur" to "Rajasthan",
        "kota" to "Rajasthan",
        "ajmer" to "Rajasthan",
        "patna" to "Bihar",
        "gaya" to "Bihar",
        "muzaffarpur" to "Bihar",
        "bhopal" to "Madhya Pradesh",
        "indore" to "Madhya Pradesh",
        "gwalior" to "Madhya Pradesh",
        "jabalpur" to "Madhya Pradesh",
        "bhubaneswar" to "Odisha",
        "cuttack" to "Odisha",
        "rourkela" to "Odisha",
        "puri" to "Odisha",
        "chandigarh" to "Chandigarh",
        "ludhiana" to "Punjab",
        "amritsar" to "Punjab",
        "jalandhar" to "Punjab",
        "mohali" to "Punjab",
        "patiala" to "Punjab",
        "dehradun" to "Uttarakhand",
        "haridwar" to "Uttarakhand",
        "rishikesh" to "Uttarakhand",
        "haldwani" to "Uttarakhand",
        "daman" to "Dadra and Nagar Haveli and Daman and Diu",
        "silvassa" to "Dadra and Nagar Haveli and Daman and Diu",
        "itanagar" to "Arunachal Pradesh",
        "port blair" to "Andaman and Nicobar Islands",
        "sri vijaya puram" to "Andaman and Nicobar Islands",
    )

    data class Hit(val state: String, val place: String, val price: Double?)

    private fun norm(t: String?) = (t ?: "").lowercase().replace(Regex("[^a-z ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** The state rate for the first of [places] that names a known city or state. */
    fun lookup(vararg places: String?): Hit? {
        for (p in places) {
            val n = norm(p)
            if (n.isEmpty() || p == null) continue
            val st = STATES.keys.firstOrNull { norm(it) == n } ?: CITY_STATE[n]
                ?: CITY_STATE.entries.firstOrNull { " $n ".contains(" ${it.key} ") }?.value
                ?: STATES.keys.firstOrNull { " $n ".contains(" ${norm(it)} ") }
            if (st != null) return Hit(st, p.trim(), STATES[st])
        }
        return null
    }
}

/** The price used for new fill-ups: yours if set, else your area's rate, else the country default. */
fun fuelPriceOf(s: AppState): Double = s.vehicle.fuelPrice
    ?: (if (s.country.code == "IN") FuelPrices.lookup(s.profile.fuelArea?.state, s.profile.city)?.price else null)
    ?: s.country.fuel
