package app.waylink.core

/** Country settings: currency, fuel price fallback and emergency numbers. Mirrors COUNTRIES in web-v0/js/store.js. */
data class Country(
    val code: String,
    val name: String,
    val dial: String,
    val sos: String,
    val amb: String,
    val police: String,
    val rsa: String,
    val cur: String,
    val curCode: String,
    val fuel: Double,
    val locale: String,
    /** Pulsars are sold as "Rouser" here. */
    val rouser: Boolean = false,
)

/** A supported bike. Mirrors MODELS in web-v0/js/store.js. */
data class BikeModel(
    val id: String,
    val name: String,
    val series: String,
    val engine: String,
    val tank: Double,
    val reserve: Double,
    val psi: List<Int>,
    val tyres: List<String>,
    val oil: String,
    val coolant: String,
    val chain: String,
    val plug: String,
    val liquid: Boolean,
)

object Catalog {
    val COUNTRIES = listOf(
        Country("IN", "India", "+91", "112", "108", "100", "1033", "₹", "INR", 102.9, "en-IN"),
        Country("CO", "Colombia", "+57", "123", "132", "112", "#767", "$", "COP", 4150.0, "es-CO", rouser = true),
        Country("MX", "Mexico", "+52", "911", "065", "911", "078", "$", "MXN", 24.5, "es-MX", rouser = true),
        Country("AR", "Argentina", "+54", "911", "107", "911", "0800-888-2677", "$", "ARS", 1180.0, "es-AR", rouser = true),
        Country("PE", "Peru", "+51", "911", "106", "105", "110", "S/", "PEN", 4.8, "es-PE", rouser = true),
        Country("BR", "Brazil", "+55", "190", "192", "190", "191", "R$", "BRL", 6.2, "pt-BR"),
        Country("PH", "Philippines", "+63", "911", "143", "117", "911", "₱", "PHP", 65.5, "en-PH"),
        Country("TR", "Turkey", "+90", "112", "112", "112", "112", "₺", "TRY", 44.0, "tr-TR"),
        Country("EG", "Egypt", "+20", "122", "123", "122", "126", "E£", "EGP", 15.0, "en-EG"),
        Country("ZA", "South Africa", "+27", "112", "10177", "10111", "112", "R", "ZAR", 24.2, "en-ZA"),
        Country("NP", "Nepal", "+977", "100", "102", "100", "103", "Rs", "NPR", 175.0, "en-NP"),
        Country("BD", "Bangladesh", "+880", "999", "999", "999", "999", "৳", "BDT", 125.0, "en-BD"),
        Country("GB", "United Kingdom / EU", "+44", "999", "999", "112", "112", "£", "GBP", 1.45, "en-GB"),
    )

    val MODELS = listOf(
        BikeModel("ns200", "Pulsar NS200", "NS Series", "199.5cc triple spark, liquid-cooled · 24.5 PS / 18.7 Nm", 12.0, 2.4, listOf(28, 28, 32, 36), listOf("100/80-17", "130/70-17"), "10W-50 semi/fully synthetic · 1200 ml", "Ready-mix glycol · 1000 ml", "25–35 mm slack", "Champion RG4HC + RG6HCC ×2", true),
        BikeModel("rs200", "Pulsar RS200", "NS Series", "199.5cc triple spark, liquid-cooled · 24.5 PS / 18.7 Nm", 13.0, 2.5, listOf(28, 28, 32, 36), listOf("100/80-17", "130/70-17"), "10W-50 semi/fully synthetic · 1200 ml", "Ready-mix glycol · 1000 ml", "25–35 mm slack", "Champion RG4HC + RG6HCC ×2", true),
        BikeModel("ns400z", "Pulsar NS400Z", "NS Series", "373.3cc DOHC, liquid-cooled · 40 PS / 35 Nm", 12.0, 2.0, listOf(29, 29, 33, 37), listOf("110/70-17", "140/70-17"), "10W-50 fully synthetic · 1700 ml", "Glycol · 1200 ml", "20–30 mm slack", "Bosch VR5NEU", true),
        BikeModel("ns160", "Pulsar NS160", "NS Series", "160.3cc twin spark, oil-cooled · 17.2 PS / 14.6 Nm", 12.0, 2.4, listOf(28, 28, 32, 36), listOf("100/80-17", "130/70-17"), "10W-40 / 20W-50 · 1150 ml", "Oil cooler", "25–35 mm slack", "Champion PRG6C", false),
        BikeModel("ns125", "Pulsar NS125", "NS Series", "124.45cc DTS-i · 12 PS / 11 Nm", 12.0, 2.0, listOf(25, 25, 28, 32), listOf("80/100-17", "100/90-17"), "20W-50 4T · 1000 ml", "Air-cooled", "25–35 mm slack", "Champion RG4HC", false),
        BikeModel("n250", "Pulsar N250 / F250", "N Series", "249.07cc twin spark, oil-cooled · 24.5 PS / 21.5 Nm", 14.0, 2.5, listOf(28, 28, 32, 36), listOf("110/70-17", "140/70-17"), "10W-50 semi-synthetic · 1300 ml", "Oil cooler", "25–35 mm slack", "Champion PRG6C", false),
        BikeModel("n160", "Pulsar N160 / N150", "N Series", "164.82cc DTS-i, oil-cooled · 16 PS / 14.65 Nm", 14.0, 2.5, listOf(25, 25, 28, 32), listOf("100/80-17", "130/70-17"), "10W-40 semi-synthetic · 1200 ml", "Oil cooler", "25–35 mm slack", "Champion RG4HC", false),
        BikeModel("pulsar150", "Pulsar 150 / 125", "Classic Series", "149.5cc twin spark DTS-i · 14 PS / 13.25 Nm", 11.5, 2.0, listOf(25, 25, 28, 32), listOf("80/100-17", "100/90-17"), "20W-50 4T · 1000 ml", "Air-cooled", "25–35 mm slack", "Champion RG4HC", false),
        BikeModel("pulsar220f", "Pulsar 220F", "Classic Series", "220cc DTS-i, oil-cooled · 20.4 PS / 18.5 Nm", 15.0, 3.2, listOf(28, 28, 32, 36), listOf("90/90-17", "120/80-17"), "20W-50 semi-synthetic · 1150 ml", "Oil cooler", "25–35 mm slack", "Champion RG4HC", false),
        BikeModel("dominar400", "Dominar 400", "Dominar Series", "373.3cc DOHC, liquid-cooled · 40 PS / 35 Nm", 13.0, 2.5, listOf(29, 29, 33, 36), listOf("110/70-17", "150/60-17"), "10W-50 fully synthetic · 1700 ml", "Glycol · 1200 ml", "20–30 mm slack", "Bosch VR5NEU", true),
        BikeModel("dominar250", "Dominar 250", "Dominar Series", "248.77cc DOHC, liquid-cooled · 27 PS / 23.5 Nm", 13.0, 2.5, listOf(29, 29, 33, 36), listOf("100/80-17", "130/70-17"), "10W-50 semi-synthetic · 1500 ml", "Glycol · 1100 ml", "20–30 mm slack", "Champion RG4HC", true),
    )

    /** Body colours offered for a vehicle: hex and name. */
    val COLOURS = listOf(
        "#B3261E" to "Racing red", "#1F3A5F" to "Midnight blue", "#2F4F3E" to "Forest green", "#E9E6DF" to "Pearl white",
        "#2A2A2A" to "Graphite black", "#C9822B" to "Copper", "#5B6470" to "Slate grey",
    )

    val BLOOD_GROUPS = listOf("", "A+", "A−", "B+", "B−", "AB+", "AB−", "O+", "O−")

    fun country(code: String?): Country = COUNTRIES.firstOrNull { it.code == code } ?: COUNTRIES[0]
    fun model(id: String?): BikeModel = MODELS.firstOrNull { it.id == id } ?: MODELS[0]
}
