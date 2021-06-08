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

package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.api.Language.AFRIKAANS
import com.github.pemistahl.lingua.api.Language.ALBANIAN
import com.github.pemistahl.lingua.api.Language.AZERBAIJANI
import com.github.pemistahl.lingua.api.Language.BASQUE
import com.github.pemistahl.lingua.api.Language.BELARUSIAN
import com.github.pemistahl.lingua.api.Language.BOKMAL
import com.github.pemistahl.lingua.api.Language.BOSNIAN
import com.github.pemistahl.lingua.api.Language.BULGARIAN
import com.github.pemistahl.lingua.api.Language.CATALAN
import com.github.pemistahl.lingua.api.Language.CHINESE
import com.github.pemistahl.lingua.api.Language.CROATIAN
import com.github.pemistahl.lingua.api.Language.CZECH
import com.github.pemistahl.lingua.api.Language.DANISH
import com.github.pemistahl.lingua.api.Language.DUTCH
import com.github.pemistahl.lingua.api.Language.ESTONIAN
import com.github.pemistahl.lingua.api.Language.FINNISH
import com.github.pemistahl.lingua.api.Language.FRENCH
import com.github.pemistahl.lingua.api.Language.GERMAN
import com.github.pemistahl.lingua.api.Language.HUNGARIAN
import com.github.pemistahl.lingua.api.Language.ICELANDIC
import com.github.pemistahl.lingua.api.Language.IRISH
import com.github.pemistahl.lingua.api.Language.ITALIAN
import com.github.pemistahl.lingua.api.Language.JAPANESE
import com.github.pemistahl.lingua.api.Language.KAZAKH
import com.github.pemistahl.lingua.api.Language.KOREAN
import com.github.pemistahl.lingua.api.Language.LATVIAN
import com.github.pemistahl.lingua.api.Language.LITHUANIAN
import com.github.pemistahl.lingua.api.Language.MACEDONIAN
import com.github.pemistahl.lingua.api.Language.MAORI
import com.github.pemistahl.lingua.api.Language.MONGOLIAN
import com.github.pemistahl.lingua.api.Language.NYNORSK
import com.github.pemistahl.lingua.api.Language.POLISH
import com.github.pemistahl.lingua.api.Language.PORTUGUESE
import com.github.pemistahl.lingua.api.Language.ROMANIAN
import com.github.pemistahl.lingua.api.Language.RUSSIAN
import com.github.pemistahl.lingua.api.Language.SERBIAN
import com.github.pemistahl.lingua.api.Language.SLOVAK
import com.github.pemistahl.lingua.api.Language.SLOVENE
import com.github.pemistahl.lingua.api.Language.SPANISH
import com.github.pemistahl.lingua.api.Language.SWEDISH
import com.github.pemistahl.lingua.api.Language.TURKISH
import com.github.pemistahl.lingua.api.Language.UKRAINIAN
import com.github.pemistahl.lingua.api.Language.VIETNAMESE
import com.github.pemistahl.lingua.api.Language.YORUBA
import com.github.pemistahl.lingua.internal.util.extension.setOfEnum

internal object Constant {
    val CHARS_TO_LANGUAGES_MAPPING = mapOf(
        "Ãã" to setOfEnum(PORTUGUESE, VIETNAMESE),
        "ĄąĘę" to setOfEnum(LITHUANIAN, POLISH),
        "Żż" to setOfEnum(POLISH, ROMANIAN),
        "Îî" to setOfEnum(FRENCH, ROMANIAN),
        "Ññ" to setOfEnum(BASQUE, SPANISH),
        "ŇňŤť" to setOfEnum(CZECH, SLOVAK),
        "Ăă" to setOfEnum(ROMANIAN, VIETNAMESE),
        "İıĞğ" to setOfEnum(AZERBAIJANI, TURKISH),
        "ЈјЉљЊњ" to setOfEnum(MACEDONIAN, SERBIAN),
        "ẸẹỌọ" to setOfEnum(VIETNAMESE, YORUBA),
        "ÐðÞþ" to setOfEnum(ICELANDIC, TURKISH),
        "Ûû" to setOfEnum(FRENCH, HUNGARIAN),
        "Ōō" to setOfEnum(MAORI, YORUBA),

        "ĀāĒēĪī" to setOfEnum(LATVIAN, MAORI, YORUBA),
        "Şş" to setOfEnum(AZERBAIJANI, ROMANIAN, TURKISH),
        "Ďď" to setOfEnum(CZECH, ROMANIAN, SLOVAK),
        "Ćć" to setOfEnum(BOSNIAN, CROATIAN, POLISH),
        "Đđ" to setOfEnum(BOSNIAN, CROATIAN, VIETNAMESE),
        "Іі" to setOfEnum(BELARUSIAN, KAZAKH, UKRAINIAN),
        "Ìì" to setOfEnum(ITALIAN, VIETNAMESE, YORUBA),

        "Ūū" to setOfEnum(LATVIAN, LITHUANIAN, MAORI, YORUBA),
        "Ëë" to setOfEnum(AFRIKAANS, ALBANIAN, DUTCH, FRENCH),
        "ÈèÙù" to setOfEnum(FRENCH, ITALIAN, VIETNAMESE, YORUBA),
        "Êê" to setOfEnum(AFRIKAANS, FRENCH, PORTUGUESE, VIETNAMESE),
        "Õõ" to setOfEnum(ESTONIAN, HUNGARIAN, PORTUGUESE, VIETNAMESE),
        "Ôô" to setOfEnum(FRENCH, PORTUGUESE, SLOVAK, VIETNAMESE),
        "Øø" to setOfEnum(BOKMAL, DANISH, NYNORSK),
        "ЁёЫыЭэ" to setOfEnum(BELARUSIAN, KAZAKH, MONGOLIAN, RUSSIAN),
        "ЩщЪъ" to setOfEnum(BULGARIAN, KAZAKH, MONGOLIAN, RUSSIAN),
        "Òò" to setOfEnum(CATALAN, ITALIAN, VIETNAMESE, YORUBA),
        "Ââ" to setOfEnum(PORTUGUESE, ROMANIAN, TURKISH, VIETNAMESE),

        "Ýý" to setOfEnum(CZECH, ICELANDIC, SLOVAK, TURKISH, VIETNAMESE),
        "Ää" to setOfEnum(ESTONIAN, FINNISH, GERMAN, SLOVAK, SWEDISH),
        "Àà" to setOfEnum(CATALAN, FRENCH, ITALIAN, PORTUGUESE, VIETNAMESE),
        "Ææ" to setOfEnum(BOKMAL, DANISH, ICELANDIC, NYNORSK),
        "Åå" to setOfEnum(BOKMAL, DANISH, NYNORSK, SWEDISH),

        "Üü" to setOfEnum(AZERBAIJANI, CATALAN, ESTONIAN, GERMAN, HUNGARIAN, SPANISH, TURKISH),
        "ČčŠšŽž" to setOfEnum(BOSNIAN, CZECH, CROATIAN, LATVIAN, LITHUANIAN, SLOVAK, SLOVENE),
        "Çç" to setOfEnum(ALBANIAN, AZERBAIJANI, BASQUE, CATALAN, FRENCH, PORTUGUESE, TURKISH),

        "Öö" to setOfEnum(AZERBAIJANI, ESTONIAN, FINNISH, GERMAN, HUNGARIAN, ICELANDIC, SWEDISH, TURKISH),

        "Óó" to setOfEnum(CATALAN, HUNGARIAN, ICELANDIC, IRISH, POLISH, PORTUGUESE, SLOVAK, SPANISH, VIETNAMESE, YORUBA),
        "ÁáÍíÚú" to setOfEnum(CATALAN, CZECH, ICELANDIC, IRISH, HUNGARIAN, PORTUGUESE, SLOVAK, SPANISH, VIETNAMESE, YORUBA),

        "Éé" to setOfEnum(
            CATALAN, CZECH, FRENCH, HUNGARIAN, ICELANDIC, IRISH,
            ITALIAN, PORTUGUESE, SLOVAK, SPANISH, VIETNAMESE, YORUBA
        )
    )

    /** Indexer for all Languages which are the values of [CHARS_TO_LANGUAGES_MAPPING] */
    val languagesWithCharsIndexer = KeyIndexer.fromEnumConstants(CHARS_TO_LANGUAGES_MAPPING.flatMap { it.value })

    fun isJapaneseScript(script: Character.UnicodeScript): Boolean {
        return script == Character.UnicodeScript.HIRAGANA
            || script == Character.UnicodeScript.KATAKANA
            || script == Character.UnicodeScript.HAN
    }

    val LANGUAGES_SUPPORTING_LOGOGRAMS = setOfEnum(CHINESE, JAPANESE, KOREAN)
    val MULTIPLE_WHITESPACE = Regex("\\s+")
    val NUMBERS = Regex("\\p{N}")
    val PUNCTUATION = Regex("\\p{P}")
}
