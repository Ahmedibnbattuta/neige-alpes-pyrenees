package fr.neige.stations.data

enum class Range(val label: String) { ALPES("Alpes"), PYRENEES("Pyrénées") }

enum class Country(val label: String, val flag: String) {
    FR("France", "🇫🇷"),
    CH("Suisse", "🇨🇭"),
    AT("Autriche", "🇦🇹"),
    IT("Italie", "🇮🇹"),
    DE("Allemagne", "🇩🇪"),
    SI("Slovénie", "🇸🇮"),
    ES("Espagne", "🇪🇸"),
    AD("Andorre", "🇦🇩"),
}

/**
 * Une station de ski. Les altitudes (bas / haut du domaine skiable) sont indicatives
 * et servent à interroger le modèle météo à la bonne hauteur.
 */
data class Station(
    val id: String,
    val name: String,
    val country: Country,
    val range: Range,
    val massif: String,
    val lat: Double,
    val lon: Double,
    val baseAlt: Int,
    val topAlt: Int,
) {
    val midAlt: Int get() = (baseAlt + topAlt) / 2
}

private fun a(id: String, name: String, c: Country, massif: String, lat: Double, lon: Double, base: Int, top: Int) =
    Station(id, name, c, Range.ALPES, massif, lat, lon, base, top)

private fun p(id: String, name: String, c: Country, massif: String, lat: Double, lon: Double, base: Int, top: Int) =
    Station(id, name, c, Range.PYRENEES, massif, lat, lon, base, top)

val STATIONS: List<Station> = listOf(
    // ---------- Alpes françaises ----------
    a("chamonix", "Chamonix-Mont-Blanc", Country.FR, "Mont-Blanc", 45.9237, 6.8694, 1035, 3275),
    a("megeve", "Megève", Country.FR, "Mont-Blanc", 45.8567, 6.6175, 1113, 2350),
    a("laclusaz", "La Clusaz", Country.FR, "Aravis", 45.9045, 6.4239, 1100, 2600),
    a("avoriaz", "Avoriaz", Country.FR, "Portes du Soleil", 46.1914, 6.7745, 1800, 2466),
    a("lesgets", "Les Gets", Country.FR, "Portes du Soleil", 46.1586, 6.6697, 1172, 2002),
    a("tignes", "Tignes", Country.FR, "Tarentaise", 45.4683, 6.9056, 1550, 3456),
    a("valdisere", "Val d'Isère", Country.FR, "Tarentaise", 45.4481, 6.9798, 1785, 3456),
    a("lesarcs", "Les Arcs", Country.FR, "Tarentaise", 45.5725, 6.8283, 1200, 3226),
    a("laplagne", "La Plagne", Country.FR, "Tarentaise", 45.5058, 6.6772, 1250, 3250),
    a("courchevel", "Courchevel", Country.FR, "3 Vallées", 45.4153, 6.6347, 1100, 2738),
    a("meribel", "Méribel", Country.FR, "3 Vallées", 45.3967, 6.5656, 1400, 2952),
    a("menuires", "Les Menuires", Country.FR, "3 Vallées", 45.3236, 6.5381, 1450, 2850),
    a("valthorens", "Val Thorens", Country.FR, "3 Vallées", 45.2979, 6.5800, 1800, 3230),
    a("alpedhuez", "L'Alpe d'Huez", Country.FR, "Oisans", 45.0921, 6.0700, 1250, 3330),
    a("deuxalpes", "Les 2 Alpes", Country.FR, "Oisans", 45.0089, 6.1236, 1300, 3600),
    a("serreche", "Serre Chevalier", Country.FR, "Hautes-Alpes", 44.9440, 6.5590, 1200, 2800),
    a("vars", "Vars / Risoul", Country.FR, "Hautes-Alpes", 44.5800, 6.6900, 1650, 2750),
    a("isola", "Isola 2000", Country.FR, "Alpes-Maritimes", 44.1867, 7.1550, 1800, 2610),

    // ---------- Suisse ----------
    a("zermatt", "Zermatt", Country.CH, "Valais", 46.0207, 7.7491, 1620, 3883),
    a("saasfee", "Saas-Fee", Country.CH, "Valais", 46.1081, 7.9279, 1800, 3600),
    a("verbier", "Verbier", Country.CH, "Valais", 46.0961, 7.2286, 1500, 3330),
    a("crans", "Crans-Montana", Country.CH, "Valais", 46.3072, 7.4811, 1500, 3000),
    a("grindelwald", "Grindelwald", Country.CH, "Oberland bernois", 46.6242, 8.0414, 1034, 2501),
    a("engelberg", "Engelberg-Titlis", Country.CH, "Suisse centrale", 46.8211, 8.4013, 1050, 3020),
    a("laax", "Laax", Country.CH, "Grisons", 46.8094, 9.2583, 1100, 3018),
    a("davos", "Davos-Klosters", Country.CH, "Grisons", 46.8027, 9.8360, 1560, 2844),
    a("stmoritz", "St. Moritz", Country.CH, "Engadine", 46.4908, 9.8355, 1772, 3303),

    // ---------- Autriche ----------
    a("stanton", "St. Anton am Arlberg", Country.AT, "Arlberg", 47.1297, 10.2683, 1304, 2811),
    a("ischgl", "Ischgl", Country.AT, "Silvretta", 47.0122, 10.2908, 1377, 2872),
    a("solden", "Sölden", Country.AT, "Ötztal", 46.9655, 11.0076, 1350, 3340),
    a("obergurgl", "Obergurgl-Hochgurgl", Country.AT, "Ötztal", 46.8700, 11.0272, 1800, 3080),
    a("hintertux", "Hintertux", Country.AT, "Zillertal", 47.0950, 11.6869, 1500, 3250),
    a("mayrhofen", "Mayrhofen", Country.AT, "Zillertal", 47.1675, 11.8656, 630, 2500),
    a("kitzbuehel", "Kitzbühel", Country.AT, "Alpes de Kitzbühel", 47.4464, 12.3922, 800, 2000),
    a("saalbach", "Saalbach-Hinterglemm", Country.AT, "Salzbourg", 47.3912, 12.6365, 1003, 2096),

    // ---------- Italie ----------
    a("courmayeur", "Courmayeur", Country.IT, "Val d'Aoste", 45.7969, 6.9690, 1224, 2755),
    a("cervinia", "Breuil-Cervinia", Country.IT, "Val d'Aoste", 45.9336, 7.6314, 2050, 3480),
    a("sestriere", "Sestriere", Country.IT, "Piémont", 44.9580, 6.8790, 2035, 2823),
    a("livigno", "Livigno", Country.IT, "Lombardie", 46.5383, 10.1356, 1816, 2798),
    a("bormio", "Bormio", Country.IT, "Lombardie", 46.4669, 10.3700, 1225, 3012),
    a("campiglio", "Madonna di Campiglio", Country.IT, "Trentin", 46.2290, 10.8264, 1550, 2504),
    a("valgardena", "Val Gardena", Country.IT, "Dolomites", 46.5579, 11.6764, 1236, 2518),
    a("cortina", "Cortina d'Ampezzo", Country.IT, "Dolomites", 46.5405, 12.1357, 1224, 2930),

    // ---------- Allemagne / Slovénie ----------
    a("garmisch", "Garmisch-Partenkirchen", Country.DE, "Wetterstein", 47.4920, 11.0950, 720, 2720),
    a("kranjska", "Kranjska Gora", Country.SI, "Alpes juliennes", 46.4847, 13.7853, 800, 1295),

    // ---------- Pyrénées françaises ----------
    p("pierrestm", "La Pierre Saint-Martin", Country.FR, "Béarn", 42.9800, -0.7600, 1500, 2200),
    p("gourette", "Gourette", Country.FR, "Béarn", 42.9600, -0.3350, 1400, 2450),
    p("cauterets", "Cauterets", Country.FR, "Hautes-Pyrénées", 42.8890, -0.1150, 1730, 2415),
    p("tourmalet", "Grand Tourmalet", Country.FR, "Hautes-Pyrénées", 42.9100, 0.1800, 1400, 2500),
    p("piau", "Piau-Engaly", Country.FR, "Hautes-Pyrénées", 42.7850, 0.1580, 1420, 2600),
    p("saintlary", "Saint-Lary-Soulan", Country.FR, "Hautes-Pyrénées", 42.8170, 0.3220, 1700, 2515),
    p("peyragudes", "Peyragudes", Country.FR, "Hautes-Pyrénées", 42.7890, 0.4500, 1600, 2400),
    p("superbagneres", "Superbagnères", Country.FR, "Haute-Garonne", 42.7610, 0.5850, 1440, 2260),
    p("ax", "Ax 3 Domaines", Country.FR, "Ariège", 42.6900, 1.8300, 1400, 2400),
    p("lesangles", "Les Angles", Country.FR, "Pyrénées-Orientales", 42.5760, 2.0750, 1600, 2400),
    p("fontromeu", "Font-Romeu Pyrénées 2000", Country.FR, "Pyrénées-Orientales", 42.5058, 2.0361, 1750, 2213),

    // ---------- Pyrénées espagnoles ----------
    p("candanchu", "Candanchú", Country.ES, "Aragon", 42.7880, -0.5300, 1530, 2400),
    p("astun", "Astún", Country.ES, "Aragon", 42.8090, -0.5050, 1700, 2324),
    p("formigal", "Formigal-Panticosa", Country.ES, "Aragon", 42.7750, -0.3640, 1500, 2250),
    p("cerler", "Cerler", Country.ES, "Aragon", 42.5600, 0.5400, 1500, 2630),
    p("baqueira", "Baqueira-Beret", Country.ES, "Val d'Aran", 42.6990, 0.9340, 1500, 2610),
    p("lamolina", "La Molina", Country.ES, "Catalogne", 42.3400, 1.9400, 1700, 2537),

    // ---------- Andorre ----------
    p("arcalis", "Ordino Arcalís", Country.AD, "Andorre", 42.6290, 1.4880, 1940, 2625),
    p("palarinsal", "Pal Arinsal", Country.AD, "Andorre", 42.5500, 1.4800, 1550, 2560),
    p("grandvalira", "Grandvalira", Country.AD, "Andorre", 42.5420, 1.7330, 1710, 2640),
)
