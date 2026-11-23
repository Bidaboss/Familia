package fr.familia.app.feature.trips.phrases

import java.util.Locale

/**
 * « Phrases utiles » (récit 105, règle 44): ten phrases of the language of a country, with the way to
 * read them as a French speaker. A fixed table: nothing is fetched, nothing is stored, and no
 * translation is made on the fly, so a phrase is the one that was checked.
 */
data class Phrase(val french: String, val local: String, val pronunciation: String)

/** A language of the table, [locale] being the voice the phone must speak it with. */
enum class PhraseLanguage(val frenchName: String, val locale: Locale) {
    Spanish("espagnol", Locale("es", "ES")),
    Portuguese("portugais", Locale("pt", "PT")),
    Italian("italien", Locale("it", "IT")),
    German("allemand", Locale("de", "DE")),
    English("anglais", Locale("en", "GB")),
    Dutch("néerlandais", Locale("nl", "NL")),
}

object PhraseBook {
    /** The language spoken in a country (ISO 3166 code, any case), or null: France, or no table yet. */
    fun languageOf(countryCode: String?): PhraseLanguage? = when (countryCode?.uppercase(Locale.ROOT)) {
        "ES", "MX", "AR", "CO", "PE", "CL", "UY", "EC", "CU", "DO", "GT", "CR", "PA", "BO", "PY", "VE" -> PhraseLanguage.Spanish
        "PT", "BR" -> PhraseLanguage.Portuguese
        "IT", "SM", "VA" -> PhraseLanguage.Italian
        "DE", "AT", "LI" -> PhraseLanguage.German
        "GB", "IE", "US", "AU", "NZ", "MT" -> PhraseLanguage.English
        "NL" -> PhraseLanguage.Dutch
        else -> null
    }

    /** The countries (codes) of [codes] that are not France and have a table, once each, in order. */
    fun abroad(codes: List<String>): List<String> =
        codes.map { it.uppercase(Locale.ROOT) }.filter { it != "FR" && languageOf(it) != null }.distinct()

    fun phrases(language: PhraseLanguage): List<Phrase> = when (language) {
        PhraseLanguage.Spanish -> SPANISH
        PhraseLanguage.Portuguese -> PORTUGUESE
        PhraseLanguage.Italian -> ITALIAN
        PhraseLanguage.German -> GERMAN
        PhraseLanguage.English -> ENGLISH
        PhraseLanguage.Dutch -> DUTCH
    }

    const val COUNT = 10

    private val SPANISH = listOf(
        Phrase("Bonjour", "Hola", "o-la"),
        Phrase("Merci", "Gracias", "gra-ci-asse"),
        Phrase("S'il vous plaît", "Por favor", "por fa-vor"),
        Phrase("Excusez-moi", "Perdón", "per-don"),
        Phrase("Combien ça coûte ?", "¿Cuánto cuesta?", "couan-to coues-ta"),
        Phrase("L'addition, s'il vous plaît", "La cuenta, por favor", "la couen-ta, por fa-vor"),
        Phrase("Où est… ?", "¿Dónde está…?", "don-dé es-ta"),
        Phrase("Je ne comprends pas", "No entiendo", "no en-tien-do"),
        Phrase("À l'aide !", "¡Socorro!", "so-co-rro"),
        Phrase("Une table pour deux", "Una mesa para dos", "ou-na mé-sa pa-ra doss"),
    )

    private val PORTUGUESE = listOf(
        Phrase("Bonjour", "Bom dia", "bon di-a"),
        Phrase("Merci", "Obrigado / Obrigada", "o-bri-ga-dou / o-bri-ga-da"),
        Phrase("S'il vous plaît", "Por favor", "pour fa-vor"),
        Phrase("Excusez-moi", "Desculpe", "dich-coul-pe"),
        Phrase("Combien ça coûte ?", "Quanto custa?", "couan-tou cous-ta"),
        Phrase("L'addition, s'il vous plaît", "A conta, por favor", "a con-ta, pour fa-vor"),
        Phrase("Où est… ?", "Onde fica…?", "on-de fi-ca"),
        Phrase("Je ne comprends pas", "Não percebo", "nan per-sé-bou"),
        Phrase("À l'aide !", "Socorro!", "sou-co-rrou"),
        Phrase("Une table pour deux", "Uma mesa para dois", "ou-ma mé-za pa-ra doïch"),
    )

    private val ITALIAN = listOf(
        Phrase("Bonjour", "Buongiorno", "bon-djor-no"),
        Phrase("Merci", "Grazie", "gra-tsi-é"),
        Phrase("S'il vous plaît", "Per favore", "pér fa-vo-ré"),
        Phrase("Excusez-moi", "Mi scusi", "mi skou-zi"),
        Phrase("Combien ça coûte ?", "Quanto costa?", "couan-to cos-ta"),
        Phrase("L'addition, s'il vous plaît", "Il conto, per favore", "il con-to, pér fa-vo-ré"),
        Phrase("Où est… ?", "Dov'è…?", "do-vé"),
        Phrase("Je ne comprends pas", "Non capisco", "non ca-pis-co"),
        Phrase("À l'aide !", "Aiuto!", "a-iou-to"),
        Phrase("Une table pour deux", "Un tavolo per due", "oun ta-vo-lo pér dou-é"),
    )

    private val GERMAN = listOf(
        Phrase("Bonjour", "Guten Tag", "gou-ten tag"),
        Phrase("Merci", "Danke", "dan-ke"),
        Phrase("S'il vous plaît", "Bitte", "bi-te"),
        Phrase("Excusez-moi", "Entschuldigung", "ent-choul-di-goung"),
        Phrase("Combien ça coûte ?", "Wie viel kostet das?", "vi fil kos-tet das"),
        Phrase("L'addition, s'il vous plaît", "Die Rechnung, bitte", "di rèch-noung, bi-te"),
        Phrase("Où est… ?", "Wo ist…?", "vo ist"),
        Phrase("Je ne comprends pas", "Ich verstehe nicht", "ich fer-chté-e nicht"),
        Phrase("À l'aide !", "Hilfe!", "hil-fe"),
        Phrase("Une table pour deux", "Einen Tisch für zwei", "aï-nen tich fur tsvaï"),
    )

    private val ENGLISH = listOf(
        Phrase("Bonjour", "Hello", "hè-lo"),
        Phrase("Merci", "Thank you", "sènk you"),
        Phrase("S'il vous plaît", "Please", "pliz"),
        Phrase("Excusez-moi", "Excuse me", "èks-kiouz mi"),
        Phrase("Combien ça coûte ?", "How much is it?", "hao motch iz it"),
        Phrase("L'addition, s'il vous plaît", "The bill, please", "the bil, pliz"),
        Phrase("Où est… ?", "Where is…?", "wèr iz"),
        Phrase("Je ne comprends pas", "I don't understand", "aï dount eun-der-stand"),
        Phrase("À l'aide !", "Help!", "hèlp"),
        Phrase("Une table pour deux", "A table for two", "eï téï-bel for tou"),
    )

    private val DUTCH = listOf(
        Phrase("Bonjour", "Goedendag", "rhou-den-darh"),
        Phrase("Merci", "Dank u wel", "dank u wèl"),
        Phrase("S'il vous plaît", "Alstublieft", "al-stu-blift"),
        Phrase("Excusez-moi", "Pardon", "par-don"),
        Phrase("Combien ça coûte ?", "Hoeveel kost het?", "hou-vél kost hèt"),
        Phrase("L'addition, s'il vous plaît", "De rekening, alstublieft", "de ré-ke-ning, al-stu-blift"),
        Phrase("Où est… ?", "Waar is…?", "var is"),
        Phrase("Je ne comprends pas", "Ik begrijp het niet", "ik be-rhraïp hèt nit"),
        Phrase("À l'aide !", "Help!", "hèlp"),
        Phrase("Une table pour deux", "Een tafel voor twee", "én ta-fel vor tvé"),
    )
}
