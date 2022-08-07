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

import com.github.pemistahl.lingua.api.IsoCode639_1.AF
import com.github.pemistahl.lingua.api.IsoCode639_1.AR
import com.github.pemistahl.lingua.api.IsoCode639_1.AZ
import com.github.pemistahl.lingua.api.IsoCode639_1.BE
import com.github.pemistahl.lingua.api.IsoCode639_1.BG
import com.github.pemistahl.lingua.api.IsoCode639_1.BN
import com.github.pemistahl.lingua.api.IsoCode639_1.BS
import com.github.pemistahl.lingua.api.IsoCode639_1.CA
import com.github.pemistahl.lingua.api.IsoCode639_1.CS
import com.github.pemistahl.lingua.api.IsoCode639_1.CY
import com.github.pemistahl.lingua.api.IsoCode639_1.DA
import com.github.pemistahl.lingua.api.IsoCode639_1.DE
import com.github.pemistahl.lingua.api.IsoCode639_1.EL
import com.github.pemistahl.lingua.api.IsoCode639_1.EN
import com.github.pemistahl.lingua.api.IsoCode639_1.EO
import com.github.pemistahl.lingua.api.IsoCode639_1.ES
import com.github.pemistahl.lingua.api.IsoCode639_1.ET
import com.github.pemistahl.lingua.api.IsoCode639_1.EU
import com.github.pemistahl.lingua.api.IsoCode639_1.FA
import com.github.pemistahl.lingua.api.IsoCode639_1.FI
import com.github.pemistahl.lingua.api.IsoCode639_1.FR
import com.github.pemistahl.lingua.api.IsoCode639_1.GA
import com.github.pemistahl.lingua.api.IsoCode639_1.GU
import com.github.pemistahl.lingua.api.IsoCode639_1.HE
import com.github.pemistahl.lingua.api.IsoCode639_1.HI
import com.github.pemistahl.lingua.api.IsoCode639_1.HR
import com.github.pemistahl.lingua.api.IsoCode639_1.HU
import com.github.pemistahl.lingua.api.IsoCode639_1.HY
import com.github.pemistahl.lingua.api.IsoCode639_1.ID
import com.github.pemistahl.lingua.api.IsoCode639_1.IS
import com.github.pemistahl.lingua.api.IsoCode639_1.IT
import com.github.pemistahl.lingua.api.IsoCode639_1.JA
import com.github.pemistahl.lingua.api.IsoCode639_1.KA
import com.github.pemistahl.lingua.api.IsoCode639_1.KK
import com.github.pemistahl.lingua.api.IsoCode639_1.KO
import com.github.pemistahl.lingua.api.IsoCode639_1.LA
import com.github.pemistahl.lingua.api.IsoCode639_1.LG
import com.github.pemistahl.lingua.api.IsoCode639_1.LT
import com.github.pemistahl.lingua.api.IsoCode639_1.LV
import com.github.pemistahl.lingua.api.IsoCode639_1.MI
import com.github.pemistahl.lingua.api.IsoCode639_1.MK
import com.github.pemistahl.lingua.api.IsoCode639_1.MN
import com.github.pemistahl.lingua.api.IsoCode639_1.MR
import com.github.pemistahl.lingua.api.IsoCode639_1.MS
import com.github.pemistahl.lingua.api.IsoCode639_1.NB
import com.github.pemistahl.lingua.api.IsoCode639_1.NL
import com.github.pemistahl.lingua.api.IsoCode639_1.NN
import com.github.pemistahl.lingua.api.IsoCode639_1.PA
import com.github.pemistahl.lingua.api.IsoCode639_1.PL
import com.github.pemistahl.lingua.api.IsoCode639_1.PT
import com.github.pemistahl.lingua.api.IsoCode639_1.RO
import com.github.pemistahl.lingua.api.IsoCode639_1.RU
import com.github.pemistahl.lingua.api.IsoCode639_1.SK
import com.github.pemistahl.lingua.api.IsoCode639_1.SL
import com.github.pemistahl.lingua.api.IsoCode639_1.SN
import com.github.pemistahl.lingua.api.IsoCode639_1.SO
import com.github.pemistahl.lingua.api.IsoCode639_1.SQ
import com.github.pemistahl.lingua.api.IsoCode639_1.SR
import com.github.pemistahl.lingua.api.IsoCode639_1.ST
import com.github.pemistahl.lingua.api.IsoCode639_1.SV
import com.github.pemistahl.lingua.api.IsoCode639_1.SW
import com.github.pemistahl.lingua.api.IsoCode639_1.TA
import com.github.pemistahl.lingua.api.IsoCode639_1.TE
import com.github.pemistahl.lingua.api.IsoCode639_1.TH
import com.github.pemistahl.lingua.api.IsoCode639_1.TL
import com.github.pemistahl.lingua.api.IsoCode639_1.TN
import com.github.pemistahl.lingua.api.IsoCode639_1.TR
import com.github.pemistahl.lingua.api.IsoCode639_1.TS
import com.github.pemistahl.lingua.api.IsoCode639_1.UK
import com.github.pemistahl.lingua.api.IsoCode639_1.UR
import com.github.pemistahl.lingua.api.IsoCode639_1.VI
import com.github.pemistahl.lingua.api.IsoCode639_1.XH
import com.github.pemistahl.lingua.api.IsoCode639_1.YO
import com.github.pemistahl.lingua.api.IsoCode639_1.ZH
import com.github.pemistahl.lingua.api.IsoCode639_1.ZU
import com.github.pemistahl.lingua.api.IsoCode639_3.AFR
import com.github.pemistahl.lingua.api.IsoCode639_3.ARA
import com.github.pemistahl.lingua.api.IsoCode639_3.AZE
import com.github.pemistahl.lingua.api.IsoCode639_3.BEL
import com.github.pemistahl.lingua.api.IsoCode639_3.BEN
import com.github.pemistahl.lingua.api.IsoCode639_3.BOS
import com.github.pemistahl.lingua.api.IsoCode639_3.BUL
import com.github.pemistahl.lingua.api.IsoCode639_3.CAT
import com.github.pemistahl.lingua.api.IsoCode639_3.CES
import com.github.pemistahl.lingua.api.IsoCode639_3.CYM
import com.github.pemistahl.lingua.api.IsoCode639_3.DAN
import com.github.pemistahl.lingua.api.IsoCode639_3.DEU
import com.github.pemistahl.lingua.api.IsoCode639_3.ELL
import com.github.pemistahl.lingua.api.IsoCode639_3.ENG
import com.github.pemistahl.lingua.api.IsoCode639_3.EPO
import com.github.pemistahl.lingua.api.IsoCode639_3.EST
import com.github.pemistahl.lingua.api.IsoCode639_3.EUS
import com.github.pemistahl.lingua.api.IsoCode639_3.FAS
import com.github.pemistahl.lingua.api.IsoCode639_3.FIN
import com.github.pemistahl.lingua.api.IsoCode639_3.FRA
import com.github.pemistahl.lingua.api.IsoCode639_3.GLE
import com.github.pemistahl.lingua.api.IsoCode639_3.GUJ
import com.github.pemistahl.lingua.api.IsoCode639_3.HEB
import com.github.pemistahl.lingua.api.IsoCode639_3.HIN
import com.github.pemistahl.lingua.api.IsoCode639_3.HRV
import com.github.pemistahl.lingua.api.IsoCode639_3.HUN
import com.github.pemistahl.lingua.api.IsoCode639_3.HYE
import com.github.pemistahl.lingua.api.IsoCode639_3.IND
import com.github.pemistahl.lingua.api.IsoCode639_3.ISL
import com.github.pemistahl.lingua.api.IsoCode639_3.ITA
import com.github.pemistahl.lingua.api.IsoCode639_3.JPN
import com.github.pemistahl.lingua.api.IsoCode639_3.KAT
import com.github.pemistahl.lingua.api.IsoCode639_3.KAZ
import com.github.pemistahl.lingua.api.IsoCode639_3.KOR
import com.github.pemistahl.lingua.api.IsoCode639_3.LAT
import com.github.pemistahl.lingua.api.IsoCode639_3.LAV
import com.github.pemistahl.lingua.api.IsoCode639_3.LIT
import com.github.pemistahl.lingua.api.IsoCode639_3.LUG
import com.github.pemistahl.lingua.api.IsoCode639_3.MAR
import com.github.pemistahl.lingua.api.IsoCode639_3.MKD
import com.github.pemistahl.lingua.api.IsoCode639_3.MON
import com.github.pemistahl.lingua.api.IsoCode639_3.MRI
import com.github.pemistahl.lingua.api.IsoCode639_3.MSA
import com.github.pemistahl.lingua.api.IsoCode639_3.NLD
import com.github.pemistahl.lingua.api.IsoCode639_3.NNO
import com.github.pemistahl.lingua.api.IsoCode639_3.NOB
import com.github.pemistahl.lingua.api.IsoCode639_3.PAN
import com.github.pemistahl.lingua.api.IsoCode639_3.POL
import com.github.pemistahl.lingua.api.IsoCode639_3.POR
import com.github.pemistahl.lingua.api.IsoCode639_3.RON
import com.github.pemistahl.lingua.api.IsoCode639_3.RUS
import com.github.pemistahl.lingua.api.IsoCode639_3.SLK
import com.github.pemistahl.lingua.api.IsoCode639_3.SLV
import com.github.pemistahl.lingua.api.IsoCode639_3.SNA
import com.github.pemistahl.lingua.api.IsoCode639_3.SOM
import com.github.pemistahl.lingua.api.IsoCode639_3.SOT
import com.github.pemistahl.lingua.api.IsoCode639_3.SPA
import com.github.pemistahl.lingua.api.IsoCode639_3.SQI
import com.github.pemistahl.lingua.api.IsoCode639_3.SRP
import com.github.pemistahl.lingua.api.IsoCode639_3.SWA
import com.github.pemistahl.lingua.api.IsoCode639_3.SWE
import com.github.pemistahl.lingua.api.IsoCode639_3.TAM
import com.github.pemistahl.lingua.api.IsoCode639_3.TEL
import com.github.pemistahl.lingua.api.IsoCode639_3.TGL
import com.github.pemistahl.lingua.api.IsoCode639_3.THA
import com.github.pemistahl.lingua.api.IsoCode639_3.TSN
import com.github.pemistahl.lingua.api.IsoCode639_3.TSO
import com.github.pemistahl.lingua.api.IsoCode639_3.TUR
import com.github.pemistahl.lingua.api.IsoCode639_3.UKR
import com.github.pemistahl.lingua.api.IsoCode639_3.URD
import com.github.pemistahl.lingua.api.IsoCode639_3.VIE
import com.github.pemistahl.lingua.api.IsoCode639_3.XHO
import com.github.pemistahl.lingua.api.IsoCode639_3.YOR
import com.github.pemistahl.lingua.api.IsoCode639_3.ZHO
import com.github.pemistahl.lingua.api.IsoCode639_3.ZUL
import com.github.pemistahl.lingua.internal.util.KeyIndexer
import com.github.pemistahl.lingua.internal.util.extension.enumSetOf
import java.lang.Character.UnicodeScript
import java.util.EnumMap
import java.util.EnumSet

/**
 * The supported detectable languages.
 */
enum class Language(
    val isoCode639_1: IsoCode639_1,
    val isoCode639_3: IsoCode639_3,
    internal val unicodeScripts: Set<UnicodeScript>,
    /** Same as [unicodeScripts], except stored as Array */
    internal val unicodeScriptsArray: Array<UnicodeScript>,
    internal val uniqueCharacters: String?
) {
    AFRIKAANS(AF, AFR, enumSetOf(UnicodeScript.LATIN)),
    ALBANIAN(SQ, SQI, enumSetOf(UnicodeScript.LATIN)),
    ARABIC(AR, ARA, enumSetOf(UnicodeScript.ARABIC)),
    ARMENIAN(HY, HYE, enumSetOf(UnicodeScript.ARMENIAN)),
    AZERBAIJANI(AZ, AZE, enumSetOf(UnicodeScript.LATIN), "Əə"),
    BASQUE(EU, EUS, enumSetOf(UnicodeScript.LATIN)),
    BELARUSIAN(BE, BEL, enumSetOf(UnicodeScript.CYRILLIC)),
    BENGALI(BN, BEN, enumSetOf(UnicodeScript.BENGALI)),
    BOKMAL(NB, NOB, enumSetOf(UnicodeScript.LATIN)),
    BOSNIAN(BS, BOS, enumSetOf(UnicodeScript.LATIN)),
    BULGARIAN(BG, BUL, enumSetOf(UnicodeScript.CYRILLIC)),
    CATALAN(CA, CAT, enumSetOf(UnicodeScript.LATIN), "Ïï"),
    CHINESE(ZH, ZHO, enumSetOf(UnicodeScript.HAN)),
    CROATIAN(HR, HRV, enumSetOf(UnicodeScript.LATIN)),
    CZECH(CS, CES, enumSetOf(UnicodeScript.LATIN), "ĚěŘřŮů"),
    DANISH(DA, DAN, enumSetOf(UnicodeScript.LATIN)),
    DUTCH(NL, NLD, enumSetOf(UnicodeScript.LATIN)),
    ENGLISH(EN, ENG, enumSetOf(UnicodeScript.LATIN)),
    ESPERANTO(EO, EPO, enumSetOf(UnicodeScript.LATIN), "ĈĉĜĝĤĥĴĵŜŝŬŭ"),
    ESTONIAN(ET, EST, enumSetOf(UnicodeScript.LATIN)),
    FINNISH(FI, FIN, enumSetOf(UnicodeScript.LATIN)),
    FRENCH(FR, FRA, enumSetOf(UnicodeScript.LATIN)),
    GANDA(LG, LUG, enumSetOf(UnicodeScript.LATIN)),
    GEORGIAN(KA, KAT, enumSetOf(UnicodeScript.GEORGIAN)),
    GERMAN(DE, DEU, enumSetOf(UnicodeScript.LATIN), "ß"),
    GREEK(EL, ELL, enumSetOf(UnicodeScript.GREEK)),
    GUJARATI(GU, GUJ, enumSetOf(UnicodeScript.GUJARATI)),
    HEBREW(HE, HEB, enumSetOf(UnicodeScript.HEBREW)),
    HINDI(HI, HIN, enumSetOf(UnicodeScript.DEVANAGARI)),
    HUNGARIAN(HU, HUN, enumSetOf(UnicodeScript.LATIN), "ŐőŰű"),
    ICELANDIC(IS, ISL, enumSetOf(UnicodeScript.LATIN)),
    INDONESIAN(ID, IND, enumSetOf(UnicodeScript.LATIN)),
    IRISH(GA, GLE, enumSetOf(UnicodeScript.LATIN)),
    ITALIAN(IT, ITA, enumSetOf(UnicodeScript.LATIN)),
    JAPANESE(JA, JPN, enumSetOf(UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA, UnicodeScript.HAN)),
    KAZAKH(KK, KAZ, enumSetOf(UnicodeScript.CYRILLIC), "ӘәҒғҚқҢңҰұ"),
    KOREAN(KO, KOR, enumSetOf(UnicodeScript.HANGUL)),
    LATIN(LA, LAT, enumSetOf(UnicodeScript.LATIN)),
    LATVIAN(LV, LAV, enumSetOf(UnicodeScript.LATIN), "ĢģĶķĻļŅņ"),
    LITHUANIAN(LT, LIT, enumSetOf(UnicodeScript.LATIN), "ĖėĮįŲų"),
    MACEDONIAN(MK, MKD, enumSetOf(UnicodeScript.CYRILLIC), "ЃѓЅѕЌќЏџ"),
    MALAY(MS, MSA, enumSetOf(UnicodeScript.LATIN)),
    MAORI(MI, MRI, enumSetOf(UnicodeScript.LATIN)),
    MARATHI(MR, MAR, enumSetOf(UnicodeScript.DEVANAGARI), "ळ"),
    MONGOLIAN(MN, MON, enumSetOf(UnicodeScript.CYRILLIC), "ӨөҮү"),
    NYNORSK(NN, NNO, enumSetOf(UnicodeScript.LATIN)),
    PERSIAN(FA, FAS, enumSetOf(UnicodeScript.ARABIC)),
    POLISH(PL, POL, enumSetOf(UnicodeScript.LATIN), "ŁłŃńŚśŹź"),
    PORTUGUESE(PT, POR, enumSetOf(UnicodeScript.LATIN)),
    PUNJABI(PA, PAN, enumSetOf(UnicodeScript.GURMUKHI)),
    ROMANIAN(RO, RON, enumSetOf(UnicodeScript.LATIN), "Țţ"),
    RUSSIAN(RU, RUS, enumSetOf(UnicodeScript.CYRILLIC)),
    SERBIAN(SR, SRP, enumSetOf(UnicodeScript.CYRILLIC), "ЂђЋћ"),
    SHONA(SN, SNA, enumSetOf(UnicodeScript.LATIN)),
    SLOVAK(SK, SLK, enumSetOf(UnicodeScript.LATIN), "ĹĺĽľŔŕ"),
    SLOVENE(SL, SLV, enumSetOf(UnicodeScript.LATIN)),
    SOMALI(SO, SOM, enumSetOf(UnicodeScript.LATIN)),
    SOTHO(ST, SOT, enumSetOf(UnicodeScript.LATIN)),
    SPANISH(ES, SPA, enumSetOf(UnicodeScript.LATIN), "¿¡"),
    SWAHILI(SW, SWA, enumSetOf(UnicodeScript.LATIN)),
    SWEDISH(SV, SWE, enumSetOf(UnicodeScript.LATIN)),
    TAGALOG(TL, TGL, enumSetOf(UnicodeScript.LATIN)),
    TAMIL(TA, TAM, enumSetOf(UnicodeScript.TAMIL)),
    TELUGU(TE, TEL, enumSetOf(UnicodeScript.TELUGU)),
    THAI(TH, THA, enumSetOf(UnicodeScript.THAI)),
    TSONGA(TS, TSO, enumSetOf(UnicodeScript.LATIN)),
    TSWANA(TN, TSN, enumSetOf(UnicodeScript.LATIN)),
    TURKISH(TR, TUR, enumSetOf(UnicodeScript.LATIN)),
    UKRAINIAN(UK, UKR, enumSetOf(UnicodeScript.CYRILLIC), "ҐґЄєЇї"),
    URDU(UR, URD, enumSetOf(UnicodeScript.ARABIC)),
    VIETNAMESE(
        VI,
        VIE,
        enumSetOf(UnicodeScript.LATIN),
        "ẰằẦầẲẳẨẩẴẵẪẫẮắẤấẠạẶặẬậỀềẺẻỂểẼẽỄễẾếỆệỈỉĨĩỊịƠơỒồỜờỎỏỔổỞởỖỗỠỡỐốỚớỘộỢợƯưỪừỦủỬửŨũỮữỨứỤụỰựỲỳỶỷỸỹỴỵ"
    ),
    WELSH(CY, CYM, enumSetOf(UnicodeScript.LATIN)),
    XHOSA(XH, XHO, enumSetOf(UnicodeScript.LATIN)),
    // TODO for YORUBA: "E̩e̩Ẹ́ẹ́É̩é̩Ẹ̀ẹ̀È̩è̩Ẹ̄ẹ̄Ē̩ē̩ŌōO̩o̩Ọ́ọ́Ó̩ó̩Ọ̀ọ̀Ò̩ò̩Ọ̄ọ̄Ō̩ō̩ṢṣS̩s̩"
    YORUBA(YO, YOR, enumSetOf(UnicodeScript.LATIN), "Ṣṣ"),
    ZULU(ZU, ZUL, enumSetOf(UnicodeScript.LATIN)),

    /**
     * The imaginary unknown language.
     *
     * This value is returned if no language can be detected reliably.
     */
    UNKNOWN(IsoCode639_1.NONE, IsoCode639_3.NONE, emptySet());

    constructor (
        isoCode639_1: IsoCode639_1,
        isoCode639_3: IsoCode639_3,
        unicodeScripts: Set<UnicodeScript>,
        uniqueCharacters: String
    ) : this(isoCode639_1, isoCode639_3, unicodeScripts, unicodeScripts.toTypedArray(), uniqueCharacters)

    constructor (
        isoCode639_1: IsoCode639_1,
        isoCode639_3: IsoCode639_3,
        unicodeScripts: Set<UnicodeScript>
    ) : this(isoCode639_1, isoCode639_3, unicodeScripts, unicodeScripts.toTypedArray(), null)

    companion object {
        private val allScriptsSet: Set<UnicodeScript> =
            EnumSet.copyOf(values().asSequence().flatMap(Language::unicodeScripts).toSet())
        // Is stored as Array to reduce object creation during iteration
        internal val allScripts = allScriptsSet.toTypedArray()
        internal val allScriptsIndexer = KeyIndexer.fromEnumConstants(allScriptsSet)

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
        @Suppress("FunctionName")
        @JvmStatic
        fun getByIsoCode639_1(isoCode: IsoCode639_1) = values().find { it.isoCode639_1 == isoCode }!!

        /**
         * Returns the language for the given ISO 639-3 code.
         */
        @Suppress("FunctionName")
        @JvmStatic
        fun getByIsoCode639_3(isoCode: IsoCode639_3) = values().find { it.isoCode639_3 == isoCode }!!

        private fun filterOutLanguages(vararg languages: Language) = values().filterNot { it in languages }
    }
}
