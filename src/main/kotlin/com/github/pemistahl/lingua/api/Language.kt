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
import com.github.pemistahl.lingua.internal.util.extension.setOfEnum
import java.lang.Character.UnicodeScript
import java.util.*

/**
 * The supported detectable languages.
 */
enum class Language(
    val isoCode639_1: IsoCode639_1,
    val isoCode639_3: IsoCode639_3,
    internal val unicodeScripts: Set<UnicodeScript>,
    internal val uniqueCharacters: String?
) {
    AFRIKAANS(AF, AFR, setOfEnum(UnicodeScript.LATIN), null),
    ALBANIAN(SQ, SQI, setOfEnum(UnicodeScript.LATIN), null),
    ARABIC(AR, ARA, setOfEnum(UnicodeScript.ARABIC), null),
    ARMENIAN(HY, HYE, setOfEnum(UnicodeScript.ARMENIAN), null),
    AZERBAIJANI(AZ, AZE, setOfEnum(UnicodeScript.LATIN), "Əə"),
    BASQUE(EU, EUS, setOfEnum(UnicodeScript.LATIN), null),
    BELARUSIAN(BE, BEL, setOfEnum(UnicodeScript.CYRILLIC), null),
    BENGALI(BN, BEN, setOfEnum(UnicodeScript.BENGALI), null),
    BOKMAL(NB, NOB, setOfEnum(UnicodeScript.LATIN), null),
    BOSNIAN(BS, BOS, setOfEnum(UnicodeScript.LATIN), null),
    BULGARIAN(BG, BUL, setOfEnum(UnicodeScript.CYRILLIC), null),
    CATALAN(CA, CAT, setOfEnum(UnicodeScript.LATIN), "Ïï"),
    CHINESE(ZH, ZHO, setOfEnum(UnicodeScript.HAN), null),
    CROATIAN(HR, HRV, setOfEnum(UnicodeScript.LATIN), null),
    CZECH(CS, CES, setOfEnum(UnicodeScript.LATIN), "ĚěŘřŮů"),
    DANISH(DA, DAN, setOfEnum(UnicodeScript.LATIN), null),
    DUTCH(NL, NLD, setOfEnum(UnicodeScript.LATIN), null),
    ENGLISH(EN, ENG, setOfEnum(UnicodeScript.LATIN), null),
    ESPERANTO(EO, EPO, setOfEnum(UnicodeScript.LATIN), "ĈĉĜĝĤĥĴĵŜŝŬŭ"),
    ESTONIAN(ET, EST, setOfEnum(UnicodeScript.LATIN), null),
    FINNISH(FI, FIN, setOfEnum(UnicodeScript.LATIN), null),
    FRENCH(FR, FRA, setOfEnum(UnicodeScript.LATIN), null),
    GANDA(LG, LUG, setOfEnum(UnicodeScript.LATIN), null),
    GEORGIAN(KA, KAT, setOfEnum(UnicodeScript.GEORGIAN), null),
    GERMAN(DE, DEU, setOfEnum(UnicodeScript.LATIN), "ß"),
    GREEK(EL, ELL, setOfEnum(UnicodeScript.GREEK), null),
    GUJARATI(GU, GUJ, setOfEnum(UnicodeScript.GUJARATI), null),
    HEBREW(HE, HEB, setOfEnum(UnicodeScript.HEBREW), null),
    HINDI(HI, HIN, setOfEnum(UnicodeScript.DEVANAGARI), null),
    HUNGARIAN(HU, HUN, setOfEnum(UnicodeScript.LATIN), "ŐőŰű"),
    ICELANDIC(IS, ISL, setOfEnum(UnicodeScript.LATIN), null),
    INDONESIAN(ID, IND, setOfEnum(UnicodeScript.LATIN), null),
    IRISH(GA, GLE, setOfEnum(UnicodeScript.LATIN), null),
    ITALIAN(IT, ITA, setOfEnum(UnicodeScript.LATIN), null),
    JAPANESE(JA, JPN, setOfEnum(UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA, UnicodeScript.HAN), null),
    KAZAKH(KK, KAZ, setOfEnum(UnicodeScript.CYRILLIC), "ӘәҒғҚқҢңҰұ"),
    KOREAN(KO, KOR, setOfEnum(UnicodeScript.HANGUL), null),
    LATIN(LA, LAT, setOfEnum(UnicodeScript.LATIN), null),
    LATVIAN(LV, LAV, setOfEnum(UnicodeScript.LATIN), "ĢģĶķĻļŅņ"),
    LITHUANIAN(LT, LIT, setOfEnum(UnicodeScript.LATIN), "ĖėĮįŲų"),
    MACEDONIAN(MK, MKD, setOfEnum(UnicodeScript.CYRILLIC), "ЃѓЅѕЌќЏџ"),
    MALAY(MS, MSA, setOfEnum(UnicodeScript.LATIN), null),
    MAORI(MI, MRI, setOfEnum(UnicodeScript.LATIN), null),
    MARATHI(MR, MAR, setOfEnum(UnicodeScript.DEVANAGARI), "ळ"),
    MONGOLIAN(MN, MON, setOfEnum(UnicodeScript.CYRILLIC), "ӨөҮү"),
    NYNORSK(NN, NNO, setOfEnum(UnicodeScript.LATIN), null),
    PERSIAN(FA, FAS, setOfEnum(UnicodeScript.ARABIC), null),
    POLISH(PL, POL, setOfEnum(UnicodeScript.LATIN), "ŁłŃńŚśŹź"),
    PORTUGUESE(PT, POR, setOfEnum(UnicodeScript.LATIN), null),
    PUNJABI(PA, PAN, setOfEnum(UnicodeScript.GURMUKHI), null),
    ROMANIAN(RO, RON, setOfEnum(UnicodeScript.LATIN), "Țţ"),
    RUSSIAN(RU, RUS, setOfEnum(UnicodeScript.CYRILLIC), null),
    SERBIAN(SR, SRP, setOfEnum(UnicodeScript.CYRILLIC), "ЂђЋћ"),
    SHONA(SN, SNA, setOfEnum(UnicodeScript.LATIN), null),
    SLOVAK(SK, SLK, setOfEnum(UnicodeScript.LATIN), "ĹĺĽľŔŕ"),
    SLOVENE(SL, SLV, setOfEnum(UnicodeScript.LATIN), null),
    SOMALI(SO, SOM, setOfEnum(UnicodeScript.LATIN), null),
    SOTHO(ST, SOT, setOfEnum(UnicodeScript.LATIN), null),
    SPANISH(ES, SPA, setOfEnum(UnicodeScript.LATIN), "¿¡"),
    SWAHILI(SW, SWA, setOfEnum(UnicodeScript.LATIN), null),
    SWEDISH(SV, SWE, setOfEnum(UnicodeScript.LATIN), null),
    TAGALOG(TL, TGL, setOfEnum(UnicodeScript.LATIN), null),
    TAMIL(TA, TAM, setOfEnum(UnicodeScript.TAMIL), null),
    TELUGU(TE, TEL, setOfEnum(UnicodeScript.TELUGU), null),
    THAI(TH, THA, setOfEnum(UnicodeScript.THAI), null),
    TSONGA(TS, TSO, setOfEnum(UnicodeScript.LATIN), null),
    TSWANA(TN, TSN, setOfEnum(UnicodeScript.LATIN), null),
    TURKISH(TR, TUR, setOfEnum(UnicodeScript.LATIN), null),
    UKRAINIAN(UK, UKR, setOfEnum(UnicodeScript.CYRILLIC), "ҐґЄєЇї"),
    URDU(UR, URD, setOfEnum(UnicodeScript.ARABIC), null),
    VIETNAMESE(
        VI,
        VIE,
        setOfEnum(UnicodeScript.LATIN),
        "ẰằẦầẲẳẨẩẴẵẪẫẮắẤấẠạẶặẬậỀềẺẻỂểẼẽỄễẾếỆệỈỉĨĩỊịƠơỒồỜờỎỏỔổỞởỖỗỠỡỐốỚớỘộỢợƯưỪừỦủỬửŨũỮữỨứỤụỰựỲỳỶỷỸỹỴỵ"
    ),
    WELSH(CY, CYM, setOfEnum(UnicodeScript.LATIN), null),
    XHOSA(XH, XHO, setOfEnum(UnicodeScript.LATIN), null),
    // TODO for YORUBA: "E̩e̩Ẹ́ẹ́É̩é̩Ẹ̀ẹ̀È̩è̩Ẹ̄ẹ̄Ē̩ē̩ŌōO̩o̩Ọ́ọ́Ó̩ó̩Ọ̀ọ̀Ò̩ò̩Ọ̄ọ̄Ō̩ō̩ṢṣS̩s̩"
    YORUBA(YO, YOR, setOfEnum(UnicodeScript.LATIN), "Ṣṣ"),
    ZULU(ZU, ZUL, setOfEnum(UnicodeScript.LATIN), null),

    /**
     * The imaginary unknown language.
     *
     * This value is returned if no language can be detected reliably.
     */
    UNKNOWN(IsoCode639_1.NONE, IsoCode639_3.NONE, emptySet(), null);

    companion object {
        internal val allScripts: Set<UnicodeScript> =
            EnumSet.copyOf(values().asSequence().flatMap(Language::unicodeScripts).toSet())
        internal val allScriptsIndexer = KeyIndexer.fromEnumConstants(allScripts)

        internal val scriptsSupportingExactlyOneLanguage: Map<UnicodeScript, Language>
        init {
            val encounteredScripts = EnumSet.noneOf(UnicodeScript::class.java)
            val scriptsMap = EnumMap<UnicodeScript, Language>(UnicodeScript::class.java)
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
