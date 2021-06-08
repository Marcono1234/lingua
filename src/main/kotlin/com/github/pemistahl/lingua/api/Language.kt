/*
 * Copyright © 2018-today Peter M. Stahl pemistahl@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.pemistahl.lingua.api

import com.github.pemistahl.lingua.api.IsoCode639_1.*
import com.github.pemistahl.lingua.api.IsoCode639_3.*
import com.github.pemistahl.lingua.internal.KeyIndexer
import java.lang.Character.UnicodeScript

/**
 * The supported detectable languages.
 */
enum class Language(
    val isoCode639_1: IsoCode639_1,
    val isoCode639_3: IsoCode639_3,
    internal val unicodeScripts: Set<UnicodeScript>,
    internal val uniqueCharacters: String?
) {
    AFRIKAANS(AF, AFR, setOf(UnicodeScript.LATIN), null),
    ALBANIAN(SQ, SQI, setOf(UnicodeScript.LATIN), null),
    ARABIC(AR, ARA, setOf(UnicodeScript.ARABIC), null),
    ARMENIAN(HY, HYE, setOf(UnicodeScript.ARMENIAN), null),
    AZERBAIJANI(AZ, AZE, setOf(UnicodeScript.LATIN), "Əə"),
    BASQUE(EU, EUS, setOf(UnicodeScript.LATIN), null),
    BELARUSIAN(BE, BEL, setOf(UnicodeScript.CYRILLIC), null),
    BENGALI(BN, BEN, setOf(UnicodeScript.BENGALI), null),
    BOKMAL(NB, NOB, setOf(UnicodeScript.LATIN), null),
    BOSNIAN(BS, BOS, setOf(UnicodeScript.LATIN), null),
    BULGARIAN(BG, BUL, setOf(UnicodeScript.CYRILLIC), null),
    CATALAN(CA, CAT, setOf(UnicodeScript.LATIN), "Ïï"),
    CHINESE(ZH, ZHO, setOf(UnicodeScript.HAN), null),
    CROATIAN(HR, HRV, setOf(UnicodeScript.LATIN), null),
    CZECH(CS, CES, setOf(UnicodeScript.LATIN), "ĚěŘřŮů"),
    DANISH(DA, DAN, setOf(UnicodeScript.LATIN), null),
    DUTCH(NL, NLD, setOf(UnicodeScript.LATIN), null),
    ENGLISH(EN, ENG, setOf(UnicodeScript.LATIN), null),
    ESPERANTO(EO, EPO, setOf(UnicodeScript.LATIN), "ĈĉĜĝĤĥĴĵŜŝŬŭ"),
    ESTONIAN(ET, EST, setOf(UnicodeScript.LATIN), null),
    FINNISH(FI, FIN, setOf(UnicodeScript.LATIN), null),
    FRENCH(FR, FRA, setOf(UnicodeScript.LATIN), null),
    GANDA(LG, LUG, setOf(UnicodeScript.LATIN), null),
    GEORGIAN(KA, KAT, setOf(UnicodeScript.GEORGIAN), null),
    GERMAN(DE, DEU, setOf(UnicodeScript.LATIN), "ß"),
    GREEK(EL, ELL, setOf(UnicodeScript.GREEK), null),
    GUJARATI(GU, GUJ, setOf(UnicodeScript.GUJARATI), null),
    HEBREW(HE, HEB, setOf(UnicodeScript.HEBREW), null),
    HINDI(HI, HIN, setOf(UnicodeScript.DEVANAGARI), null),
    HUNGARIAN(HU, HUN, setOf(UnicodeScript.LATIN), "ŐőŰű"),
    ICELANDIC(IS, ISL, setOf(UnicodeScript.LATIN), null),
    INDONESIAN(ID, IND, setOf(UnicodeScript.LATIN), null),
    IRISH(GA, GLE, setOf(UnicodeScript.LATIN), null),
    ITALIAN(IT, ITA, setOf(UnicodeScript.LATIN), null),
    JAPANESE(JA, JPN, setOf(UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA, UnicodeScript.HAN), null),
    KAZAKH(KK, KAZ, setOf(UnicodeScript.CYRILLIC), "ӘәҒғҚқҢңҰұ"),
    KOREAN(KO, KOR, setOf(UnicodeScript.HANGUL), null),
    LATIN(LA, LAT, setOf(UnicodeScript.LATIN), null),
    LATVIAN(LV, LAV, setOf(UnicodeScript.LATIN), "ĢģĶķĻļŅņ"),
    LITHUANIAN(LT, LIT, setOf(UnicodeScript.LATIN), "ĖėĮįŲų"),
    MACEDONIAN(MK, MKD, setOf(UnicodeScript.CYRILLIC), "ЃѓЅѕЌќЏџ"),
    MALAY(MS, MSA, setOf(UnicodeScript.LATIN), null),
    MAORI(MI, MRI, setOf(UnicodeScript.LATIN), null),
    MARATHI(MR, MAR, setOf(UnicodeScript.DEVANAGARI), "ळ"),
    MONGOLIAN(MN, MON, setOf(UnicodeScript.CYRILLIC), "ӨөҮү"),
    NYNORSK(NN, NNO, setOf(UnicodeScript.LATIN), null),
    PERSIAN(FA, FAS, setOf(UnicodeScript.ARABIC), null),
    POLISH(PL, POL, setOf(UnicodeScript.LATIN), "ŁłŃńŚśŹź"),
    PORTUGUESE(PT, POR, setOf(UnicodeScript.LATIN), null),
    PUNJABI(PA, PAN, setOf(UnicodeScript.GURMUKHI), null),
    ROMANIAN(RO, RON, setOf(UnicodeScript.LATIN), "Țţ"),
    RUSSIAN(RU, RUS, setOf(UnicodeScript.CYRILLIC), null),
    SERBIAN(SR, SRP, setOf(UnicodeScript.CYRILLIC), "ЂђЋћ"),
    SHONA(SN, SNA, setOf(UnicodeScript.LATIN), null),
    SLOVAK(SK, SLK, setOf(UnicodeScript.LATIN), "ĹĺĽľŔŕ"),
    SLOVENE(SL, SLV, setOf(UnicodeScript.LATIN), null),
    SOMALI(SO, SOM, setOf(UnicodeScript.LATIN), null),
    SOTHO(ST, SOT, setOf(UnicodeScript.LATIN), null),
    SPANISH(ES, SPA, setOf(UnicodeScript.LATIN), "¿¡"),
    SWAHILI(SW, SWA, setOf(UnicodeScript.LATIN), null),
    SWEDISH(SV, SWE, setOf(UnicodeScript.LATIN), null),
    TAGALOG(TL, TGL, setOf(UnicodeScript.LATIN), null),
    TAMIL(TA, TAM, setOf(UnicodeScript.TAMIL), null),
    TELUGU(TE, TEL, setOf(UnicodeScript.TELUGU), null),
    THAI(TH, THA, setOf(UnicodeScript.THAI), null),
    TSONGA(TS, TSO, setOf(UnicodeScript.LATIN), null),
    TSWANA(TN, TSN, setOf(UnicodeScript.LATIN), null),
    TURKISH(TR, TUR, setOf(UnicodeScript.LATIN), null),
    UKRAINIAN(UK, UKR, setOf(UnicodeScript.CYRILLIC), "ҐґЄєЇї"),
    URDU(UR, URD, setOf(UnicodeScript.ARABIC), null),
    VIETNAMESE(
        VI,
        VIE,
        setOf(UnicodeScript.LATIN),
        "ẰằẦầẲẳẨẩẴẵẪẫẮắẤấẠạẶặẬậỀềẺẻỂểẼẽỄễẾếỆệỈỉĨĩỊịƠơỒồỜờỎỏỔổỞởỖỗỠỡỐốỚớỘộỢợƯưỪừỦủỬửŨũỮữỨứỤụỰựỲỳỶỷỸỹỴỵ"
    ),
    WELSH(CY, CYM, setOf(UnicodeScript.LATIN), null),
    XHOSA(XH, XHO, setOf(UnicodeScript.LATIN), null),
    // TODO for YORUBA: "E̩e̩Ẹ́ẹ́É̩é̩Ẹ̀ẹ̀È̩è̩Ẹ̄ẹ̄Ē̩ē̩ŌōO̩o̩Ọ́ọ́Ó̩ó̩Ọ̀ọ̀Ò̩ò̩Ọ̄ọ̄Ō̩ō̩ṢṣS̩s̩"
    YORUBA(YO, YOR, setOf(UnicodeScript.LATIN), "Ṣṣ"),
    ZULU(ZU, ZUL, setOf(UnicodeScript.LATIN), null),

    /**
     * The imaginary unknown language.
     *
     * This value is returned if no language can be detected reliably.
     */
    UNKNOWN(IsoCode639_1.NONE, IsoCode639_3.NONE, emptySet(), null);

    companion object {
        internal val allScripts = values().asSequence().flatMap(Language::unicodeScripts).toSet()
        internal val allScriptsIndexer = KeyIndexer.fromEnumConstants(allScripts)

        internal val scriptsSupportingExactlyOneLanguage: Map<UnicodeScript, Language>
        init {
            val encounteredScripts = mutableSetOf<UnicodeScript>()
            val scriptsMap = mutableMapOf<UnicodeScript, Language>()
            for (language in values()) {
                language.unicodeScripts.forEach {
                    // If not encountered yet, add mapping
                    if (encounteredScripts.add(it)) {
                        scriptsMap[it] = language
                    }
                    // Otherwise remove existing mapping
                    else {
                        scriptsMap.remove(it)
                    }
                }
            }
            scriptsSupportingExactlyOneLanguage = scriptsMap
        }

        /**
         * Returns a list of all built-in languages.
         */
        @JvmStatic
        fun all() = filterOutLanguages(UNKNOWN)

        /**
         * Returns a list of all built-in languages that are still spoken today.
         */
        @JvmStatic
        fun allSpokenOnes() = filterOutLanguages(UNKNOWN, LATIN)

        /**
         * Returns a list of all built-in languages supporting the Arabic script.
         */
        @JvmStatic
        fun allWithArabicScript() = values().filter { it.unicodeScripts.contains(UnicodeScript.ARABIC) }

        /**
         * Returns a list of all built-in languages supporting the Cyrillic script.
         */
        @JvmStatic
        fun allWithCyrillicScript() = values().filter { it.unicodeScripts.contains(UnicodeScript.CYRILLIC) }

        /**
         * Returns a list of all built-in languages supporting the Devanagari script.
         */
        @JvmStatic
        fun allWithDevanagariScript() = values().filter { it.unicodeScripts.contains(UnicodeScript.DEVANAGARI) }

        /**
         * Returns a list of all built-in languages supporting the Latin script.
         */
        @JvmStatic
        fun allWithLatinScript() = values().filter { it.unicodeScripts.contains(UnicodeScript.LATIN) }

        /**
         * Returns the language for the given ISO 639-1 code.
         */
        @JvmStatic
        fun getByIsoCode639_1(isoCode: IsoCode639_1) = values().find { it.isoCode639_1 == isoCode }!!

        /**
         * Returns the language for the given ISO 639-3 code.
         */
        @JvmStatic
        fun getByIsoCode639_3(isoCode: IsoCode639_3) = values().find { it.isoCode639_3 == isoCode }!!

        private fun filterOutLanguages(vararg languages: Language) = values().filterNot { it in languages }
    }
}
