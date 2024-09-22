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

import com.github.pemistahl.lingua.api.Language.AFRIKAANS
import com.github.pemistahl.lingua.api.Language.ALBANIAN
import com.github.pemistahl.lingua.api.Language.ARABIC
import com.github.pemistahl.lingua.api.Language.AZERBAIJANI
import com.github.pemistahl.lingua.api.Language.BASQUE
import com.github.pemistahl.lingua.api.Language.BELARUSIAN
import com.github.pemistahl.lingua.api.Language.BOKMAL
import com.github.pemistahl.lingua.api.Language.BOSNIAN
import com.github.pemistahl.lingua.api.Language.BULGARIAN
import com.github.pemistahl.lingua.api.Language.CATALAN
import com.github.pemistahl.lingua.api.Language.CROATIAN
import com.github.pemistahl.lingua.api.Language.CZECH
import com.github.pemistahl.lingua.api.Language.DANISH
import com.github.pemistahl.lingua.api.Language.DUTCH
import com.github.pemistahl.lingua.api.Language.ENGLISH
import com.github.pemistahl.lingua.api.Language.ESPERANTO
import com.github.pemistahl.lingua.api.Language.ESTONIAN
import com.github.pemistahl.lingua.api.Language.FINNISH
import com.github.pemistahl.lingua.api.Language.FRENCH
import com.github.pemistahl.lingua.api.Language.GANDA
import com.github.pemistahl.lingua.api.Language.GERMAN
import com.github.pemistahl.lingua.api.Language.HUNGARIAN
import com.github.pemistahl.lingua.api.Language.ICELANDIC
import com.github.pemistahl.lingua.api.Language.INDONESIAN
import com.github.pemistahl.lingua.api.Language.IRISH
import com.github.pemistahl.lingua.api.Language.ITALIAN
import com.github.pemistahl.lingua.api.Language.KAZAKH
import com.github.pemistahl.lingua.api.Language.LATIN
import com.github.pemistahl.lingua.api.Language.LATVIAN
import com.github.pemistahl.lingua.api.Language.LITHUANIAN
import com.github.pemistahl.lingua.api.Language.MACEDONIAN
import com.github.pemistahl.lingua.api.Language.MALAY
import com.github.pemistahl.lingua.api.Language.MAORI
import com.github.pemistahl.lingua.api.Language.MONGOLIAN
import com.github.pemistahl.lingua.api.Language.NYNORSK
import com.github.pemistahl.lingua.api.Language.OROMO
import com.github.pemistahl.lingua.api.Language.PERSIAN
import com.github.pemistahl.lingua.api.Language.POLISH
import com.github.pemistahl.lingua.api.Language.PORTUGUESE
import com.github.pemistahl.lingua.api.Language.ROMANIAN
import com.github.pemistahl.lingua.api.Language.RUSSIAN
import com.github.pemistahl.lingua.api.Language.SERBIAN
import com.github.pemistahl.lingua.api.Language.SHONA
import com.github.pemistahl.lingua.api.Language.SLOVAK
import com.github.pemistahl.lingua.api.Language.SLOVENE
import com.github.pemistahl.lingua.api.Language.SOMALI
import com.github.pemistahl.lingua.api.Language.SOTHO
import com.github.pemistahl.lingua.api.Language.SPANISH
import com.github.pemistahl.lingua.api.Language.SWAHILI
import com.github.pemistahl.lingua.api.Language.SWEDISH
import com.github.pemistahl.lingua.api.Language.TAGALOG
import com.github.pemistahl.lingua.api.Language.TSONGA
import com.github.pemistahl.lingua.api.Language.TSWANA
import com.github.pemistahl.lingua.api.Language.TURKISH
import com.github.pemistahl.lingua.api.Language.UKRAINIAN
import com.github.pemistahl.lingua.api.Language.UNKNOWN
import com.github.pemistahl.lingua.api.Language.URDU
import com.github.pemistahl.lingua.api.Language.VIETNAMESE
import com.github.pemistahl.lingua.api.Language.WELSH
import com.github.pemistahl.lingua.api.Language.XHOSA
import com.github.pemistahl.lingua.api.Language.YORUBA
import com.github.pemistahl.lingua.api.Language.ZULU
import com.github.pemistahl.lingua.internal.util.WordList
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource

class LanguageDetectorTest {
    private val detectorForAllLanguages = LanguageDetectorBuilder.fromAllLanguages().build()
    private val detectorForEnglishAndGerman = LanguageDetectorBuilder.fromLanguages(ENGLISH, GERMAN).build()

    @AfterAll
    fun unloadLanguageModels() {
        // Unload language models to not affect subsequent test executions
        detectorForAllLanguages.unloadLanguageModels()
        detectorForEnglishAndGerman.unloadLanguageModels()
    }

    @Test
    fun `assert that text is cleaned up properly`() {
        assertThat(
            detectorForAllLanguages.cleanUpInputText(
                """
                Weltweit    gibt es ungefähr 6.000 Sprachen,
                wobei laut Schätzungen zufolge ungefähr 90  Prozent davon
                am Ende dieses Jahrhunderts verdrängt sein werden.
                """.trimIndent(),
            ).toString(),
        ).isEqualTo(
            listOf(
                "weltweit gibt es ungefähr sprachen wobei laut schätzungen zufolge ungefähr",
                "prozent davon am ende dieses jahrhunderts verdrängt sein werden",
            ).joinToString(separator = " "),
        )
    }

    // language detection with rules

    @ParameterizedTest
    @CsvSource(
        "məhərrəm, AZERBAIJANI",
        "substituïts, CATALAN",
        "rozdělit, CZECH",
        "tvořen, CZECH",
        "subjektů, CZECH",
        "nesufiĉecon, ESPERANTO",
        "intermiksiĝis, ESPERANTO",
        "monaĥinoj, ESPERANTO",
        "kreitaĵoj, ESPERANTO",
        "ŝpinante, ESPERANTO",
        "apenaŭ, ESPERANTO",
        "groß, GERMAN",
        "σχέδια, GREEK",
        "fekvő, HUNGARIAN",
        "meggyűrűzni, HUNGARIAN",
        // Note: For Japanese the current implementation actually treats each char as separate word
        "ヴェダイヤモンド, JAPANESE",
        "әлем, KAZAKH",
        "шаруашылығы, KAZAKH",
        "ақын, KAZAKH",
        "оның, KAZAKH",
        "шұрайлы, KAZAKH",
        "teoloģiska, LATVIAN",
        "blaķene, LATVIAN",
        "ceļojumiem, LATVIAN",
        "numuriņu, LATVIAN",
        "mergelės, LITHUANIAN",
        "įrengus, LITHUANIAN",
        "slegiamų, LITHUANIAN",
        "припаѓа, MACEDONIAN",
        "ѕидови, MACEDONIAN",
        "ќерка, MACEDONIAN",
        "џамиите, MACEDONIAN",
        "मिळते, MARATHI",
        "үндсэн, MONGOLIAN",
        "дөхөж, MONGOLIAN",
        "zmieniły, POLISH",
        "państwowych, POLISH",
        "mniejszości, POLISH",
        "groźne, POLISH",
        "ialomiţa, ROMANIAN",
        "наслеђивања, SERBIAN",
        "неисквареношћу, SERBIAN",
        "podĺa, SLOVAK",
        "pohľade, SLOVAK",
        "mŕtvych, SLOVAK",
        "ґрунтовому, UKRAINIAN",
        "пропонує, UKRAINIAN",
        "пристрої, UKRAINIAN",
        "cằm, VIETNAMESE",
        "thần, VIETNAMESE",
        "chẳng, VIETNAMESE",
        "quẩy, VIETNAMESE",
        "sẵn, VIETNAMESE",
        "nhẫn, VIETNAMESE",
        "dắt, VIETNAMESE",
        "chất, VIETNAMESE",
        "đạp, VIETNAMESE",
        "mặn, VIETNAMESE",
        "hậu, VIETNAMESE",
        "hiền, VIETNAMESE",
        "lẻn, VIETNAMESE",
        "biểu, VIETNAMESE",
        "kẽm, VIETNAMESE",
        "diễm, VIETNAMESE",
        "phế, VIETNAMESE",
        "việc, VIETNAMESE",
        "chỉnh, VIETNAMESE",
        "trĩ, VIETNAMESE",
        "ravị, VIETNAMESE",
        "thơ, VIETNAMESE",
        "nguồn, VIETNAMESE",
        "thờ, VIETNAMESE",
        "sỏi, VIETNAMESE",
        "tổng, VIETNAMESE",
        "nhở, VIETNAMESE",
        "mỗi, VIETNAMESE",
        "bỡi, VIETNAMESE",
        "tốt, VIETNAMESE",
        "giới, VIETNAMESE",
        "một, VIETNAMESE",
        "hợp, VIETNAMESE",
        "hưng, VIETNAMESE",
        "từng, VIETNAMESE",
        "của, VIETNAMESE",
        "sử, VIETNAMESE",
        "cũng, VIETNAMESE",
        "những, VIETNAMESE",
        "chức, VIETNAMESE",
        "dụng, VIETNAMESE",
        "thực, VIETNAMESE",
        "kỳ, VIETNAMESE",
        "kỷ, VIETNAMESE",
        "mỹ, VIETNAMESE",
        "mỵ, VIETNAMESE",
        "aṣiwèrè, YORUBA",
        "ṣaaju, YORUBA",
        "والموضوع, UNKNOWN",
        "сопротивление, UNKNOWN",
        "house, UNKNOWN",
    )
    fun `assert that language of single word with unique characters can be unambiguously identified with rules`(
        word: String,
        expectedLanguage: Language,
    ) {
        assertThat(
            detectorForAllLanguages.detectLanguageWithRules(WordList.build(word)),
        ).`as`(
            "word '$word'",
        ).isEqualTo(
            expectedLanguage,
        )
    }

    @ParameterizedTest
    @CsvSource(
        "ունենա, ARMENIAN",
        "জানাতে, BENGALI",
        "გარეუბან, GEORGIAN",
        "σταμάτησε, GREEK",
        "ઉપકરણોની, GUJARATI",
        "בתחרויות, HEBREW",
        // Note: For Japanese and Korean the current implementation actually treats each char as separate word
        "びさ, JAPANESE",
        "대결구도가, KOREAN",
        "ਮੋਟਰਸਾਈਕਲਾਂ, PUNJABI",
        "துன்பங்களை, TAMIL",
        "కృష్ణదేవరాయలు, TELUGU",
        "ในทางหลวงหมายเลข, THAI",
    )
    fun `assert that language of single word with unique alphabet can be unambiguously identified with rules`(
        word: String,
        expectedLanguage: Language,
    ) {
        assertThat(
            detectorForAllLanguages.detectLanguageWithRules(WordList.build(word)),
        ).`as`(
            "word '$word'",
        ).isEqualTo(
            expectedLanguage,
        )
    }

    // language filtering with rules

    private fun filteredLanguagesProvider() =
        listOf(
            arguments(
                "والموضوع",
                listOf(ARABIC, PERSIAN, URDU),
            ),
            arguments(
                "сопротивление",
                listOf(BELARUSIAN, BULGARIAN, KAZAKH, MACEDONIAN, MONGOLIAN, RUSSIAN, SERBIAN, UKRAINIAN),
            ),
            arguments(
                "раскрывае",
                listOf(BELARUSIAN, KAZAKH, MONGOLIAN, RUSSIAN),
            ),
            arguments(
                "этот",
                listOf(BELARUSIAN, KAZAKH, MONGOLIAN, RUSSIAN),
            ),
            arguments(
                "огнём",
                listOf(BELARUSIAN, KAZAKH, MONGOLIAN, RUSSIAN),
            ),
            arguments(
                "плаваща",
                listOf(BULGARIAN, KAZAKH, MONGOLIAN, RUSSIAN, UKRAINIAN),
            ),
            arguments(
                "довършат",
                listOf(BULGARIAN, KAZAKH, MONGOLIAN, RUSSIAN),
            ),
            arguments(
                "павінен",
                listOf(BELARUSIAN, KAZAKH, UKRAINIAN),
            ),
            arguments(
                "затоплување",
                listOf(MACEDONIAN, SERBIAN),
            ),
            arguments(
                "ректасцензија",
                listOf(MACEDONIAN, SERBIAN),
            ),
            arguments(
                "набљудувач",
                listOf(MACEDONIAN, SERBIAN),
            ),
            arguments(
                "aizklātā",
                listOf(LATVIAN, MAORI, YORUBA),
            ),
            arguments(
                "sistēmas",
                listOf(LATVIAN, MAORI, YORUBA),
            ),
            arguments(
                "palīdzi",
                listOf(LATVIAN, MAORI, YORUBA),
            ),
            arguments(
                "nhẹn",
                listOf(VIETNAMESE, YORUBA),
            ),
            arguments(
                "chọn",
                listOf(VIETNAMESE, YORUBA),
            ),
            arguments(
                "prihvaćanju",
                listOf(BOSNIAN, CROATIAN, POLISH),
            ),
            arguments(
                "nađete",
                listOf(BOSNIAN, CROATIAN, VIETNAMESE),
            ),
            arguments(
                "visão",
                listOf(PORTUGUESE, VIETNAMESE),
            ),
            arguments(
                "wystąpią",
                listOf(LITHUANIAN, POLISH),
            ),
            arguments(
                "budowę",
                listOf(LITHUANIAN, POLISH),
            ),
            arguments(
                "nebūsime",
                listOf(LATVIAN, LITHUANIAN, MAORI, YORUBA),
            ),
            arguments(
                "afişate",
                listOf(AZERBAIJANI, ROMANIAN, TURKISH),
            ),
            arguments(
                "kradzieżami",
                listOf(POLISH, ROMANIAN),
            ),
            arguments(
                "înviat",
                listOf(FRENCH, ROMANIAN),
            ),
            arguments(
                "venerdì",
                listOf(ITALIAN, VIETNAMESE, YORUBA),
            ),
            arguments(
                "años",
                listOf(BASQUE, SPANISH),
            ),
            arguments(
                "rozohňuje",
                listOf(CZECH, SLOVAK),
            ),
            arguments(
                "rtuť",
                listOf(CZECH, SLOVAK),
            ),
            arguments(
                "pregătire",
                listOf(ROMANIAN, VIETNAMESE),
            ),
            arguments(
                "jeďte",
                listOf(CZECH, ROMANIAN, SLOVAK),
            ),
            arguments(
                "minjaverðir",
                listOf(ICELANDIC, TURKISH),
            ),
            arguments(
                "þagnarskyldu",
                listOf(ICELANDIC, TURKISH),
            ),
            arguments(
                "nebûtu",
                listOf(FRENCH, HUNGARIAN),
            ),
            arguments(
                "hashemidëve",
                listOf(AFRIKAANS, ALBANIAN, DUTCH, FRENCH),
            ),
            arguments(
                "forêt",
                listOf(AFRIKAANS, FRENCH, PORTUGUESE, VIETNAMESE),
            ),
            arguments(
                "succèdent",
                listOf(FRENCH, ITALIAN, VIETNAMESE, YORUBA),
            ),
            arguments(
                "où",
                listOf(FRENCH, ITALIAN, VIETNAMESE, YORUBA),
            ),
            arguments(
                "tõeliseks",
                listOf(ESTONIAN, HUNGARIAN, PORTUGUESE, VIETNAMESE),
            ),
            arguments(
                "viòiem",
                listOf(CATALAN, ITALIAN, VIETNAMESE, YORUBA),
            ),
            arguments(
                "contrôle",
                listOf(FRENCH, PORTUGUESE, SLOVAK, VIETNAMESE),
            ),
            arguments(
                "direktør",
                listOf(BOKMAL, DANISH, NYNORSK),
            ),
            arguments(
                "vývoj",
                listOf(CZECH, ICELANDIC, SLOVAK, TURKISH, VIETNAMESE),
            ),
            arguments(
                "päralt",
                listOf(ESTONIAN, FINNISH, GERMAN, SLOVAK, SWEDISH),
            ),
            arguments(
                "labâk",
                listOf(FRENCH, PORTUGUESE, ROMANIAN, TURKISH, VIETNAMESE),
            ),
            arguments(
                "pràctiques",
                listOf(CATALAN, FRENCH, ITALIAN, PORTUGUESE, VIETNAMESE),
            ),
            arguments(
                "überrascht",
                listOf(AZERBAIJANI, CATALAN, ESTONIAN, GERMAN, HUNGARIAN, SPANISH, TURKISH),
            ),
            arguments(
                "indebærer",
                listOf(BOKMAL, DANISH, ICELANDIC, NYNORSK),
            ),
            arguments(
                "måned",
                listOf(BOKMAL, DANISH, NYNORSK, SWEDISH),
            ),
            arguments(
                "zaručen",
                listOf(BOSNIAN, CZECH, CROATIAN, LATVIAN, LITHUANIAN, SLOVAK, SLOVENE),
            ),
            arguments(
                "zkouškou",
                listOf(BOSNIAN, CZECH, CROATIAN, LATVIAN, LITHUANIAN, SLOVAK, SLOVENE),
            ),
            arguments(
                "navržen",
                listOf(BOSNIAN, CZECH, CROATIAN, LATVIAN, LITHUANIAN, SLOVAK, SLOVENE),
            ),
            arguments(
                "façonnage",
                listOf(ALBANIAN, AZERBAIJANI, BASQUE, CATALAN, FRENCH, PORTUGUESE, TURKISH),
            ),
            arguments(
                "höher",
                listOf(AZERBAIJANI, ESTONIAN, FINNISH, GERMAN, HUNGARIAN, ICELANDIC, SWEDISH, TURKISH),
            ),
            arguments(
                "catedráticos",
                listOf(CATALAN, CZECH, ICELANDIC, IRISH, HUNGARIAN, PORTUGUESE, SLOVAK, SPANISH, VIETNAMESE, YORUBA),
            ),
            arguments(
                "política",
                listOf(CATALAN, CZECH, ICELANDIC, IRISH, HUNGARIAN, PORTUGUESE, SLOVAK, SPANISH, VIETNAMESE, YORUBA),
            ),
            arguments(
                "música",
                listOf(CATALAN, CZECH, ICELANDIC, IRISH, HUNGARIAN, PORTUGUESE, SLOVAK, SPANISH, VIETNAMESE, YORUBA),
            ),
            arguments(
                "contradicció",
                listOf(CATALAN, HUNGARIAN, ICELANDIC, IRISH, POLISH, PORTUGUESE, SLOVAK, SPANISH, VIETNAMESE, YORUBA),
            ),
            arguments(
                "només",
                listOf(
                    CATALAN, CZECH, FRENCH, HUNGARIAN, ICELANDIC, IRISH, ITALIAN, PORTUGUESE, SLOVAK, SPANISH,
                    VIETNAMESE, YORUBA,
                ),
            ),
            arguments(
                "house",
                listOf(
                    AFRIKAANS, ALBANIAN, AZERBAIJANI, BASQUE, BOKMAL, BOSNIAN, CATALAN, CROATIAN, CZECH, DANISH,
                    DUTCH, ENGLISH, ESPERANTO, ESTONIAN, FINNISH, FRENCH, GANDA, GERMAN, HUNGARIAN, ICELANDIC,
                    INDONESIAN, IRISH, ITALIAN, LATIN, LATVIAN, LITHUANIAN, MALAY, MAORI, NYNORSK, OROMO, POLISH,
                    PORTUGUESE, ROMANIAN, SHONA, SLOVAK, SLOVENE, SOMALI, SOTHO, SPANISH, SWAHILI, SWEDISH,
                    TAGALOG, TSONGA, TSWANA, TURKISH, VIETNAMESE, WELSH, XHOSA, YORUBA, ZULU,
                ),
            ),
        )

    @ParameterizedTest
    @MethodSource("filteredLanguagesProvider")
    fun `assert that languages can be correctly filtered by rules`(
        word: String,
        expectedLanguages: List<Language>,
    ) {
        assertThat(
            detectorForAllLanguages.filterLanguagesByRules(WordList.build(word)).toList(),
        ).`as`(
            "word '$word'",
        ).containsExactlyInAnyOrderElementsOf(
            expectedLanguages,
        )
    }

    // language detection

    @ParameterizedTest
    @ValueSource(strings = ["", " \n  \t;", "3<856%)§"])
    fun `assert that strings without letters return unknown language`(invalidString: String) {
        assertThat(
            detectorForAllLanguages.detectLanguageOf(invalidString),
        ).isEqualTo(
            UNKNOWN,
        )
    }

    @Test
    fun `assert that unknown language is returned when no ngram probabilities are available`() {
        assertThat(
            detectorForEnglishAndGerman.detectLanguageOf("проарплап"),
        ).isEqualTo(
            UNKNOWN,
        )
    }

    @Test
    fun `assert that no confidence values are returned when no ngram probabilities are available`() {
        assertThat(
            detectorForEnglishAndGerman.computeLanguageConfidenceValues("проарплап"),
        ).isEmpty()
    }

    private fun ambiguousTextProvider() =
        listOf(
            arguments(
                "ام وی با نیکی میناج تیزر داشت؟؟؟؟؟؟ i vote for bts ( _ ) as the _ via ( _ )",
                arrayOf(ENGLISH, URDU),
            ),
            arguments(
                "Az elmúlt hétvégén 12-re emelkedett az elhunyt koronavírus-fertőzöttek száma Szlovákiában. " +
                    "Mindegyik szociális otthon dolgozóját letesztelik, " +
                    "Matovič szerint az ingázóknak még várniuk kellene a teszteléssel",
                arrayOf(HUNGARIAN, SLOVAK),
            ),
        )

    @ParameterizedTest
    @MethodSource("ambiguousTextProvider")
    fun `assert that language detection is deterministic`(
        text: String,
        languages: Array<Language>,
    ) {
        removeLanguageModelsFromDetector()

        assertThatAllLanguageModelsAreUnloaded()

        val detector =
            LanguageDetectorBuilder
                .fromLanguages(*languages)
                .withPreloadedLanguageModels()
                .build()
        val detectedLanguages = mutableSetOf<Language>()
        for (i in 0..100) {
            val language = detector.detectLanguageOf(text)
            detectedLanguages.add(language)
        }
        assertThat(detectedLanguages.size).isEqualTo(1)

        removeLanguageModelsFromDetector()

        assertThatAllLanguageModelsAreUnloaded()
    }

    @Test
    fun `assert that language models can be properly unloaded`() {
        val languageDetector = LanguageDetectorBuilder.fromAllLanguages().withPreloadedLanguageModels().build()
        assertThatAllLanguageModelsAreLoaded()

        languageDetector.unloadLanguageModels()

        assertThatAllLanguageModelsAreUnloaded()
    }

    @Test
    fun `assert that high accuracy mode can be properly disabled`() {
        removeLanguageModelsFromDetector()

        assertThatAllLanguageModelsAreUnloaded()

        val detector =
            LanguageDetectorBuilder
                .fromLanguages(ENGLISH, GERMAN)
                .withPreloadedLanguageModels()
                .withLowAccuracyMode()
                .build()

        assertThatOnlyUniBiTrigramLanguageModelsAreLoaded()

        detector.detectLanguageOf("short text")

        assertThatOnlyUniBiTrigramLanguageModelsAreLoaded()
    }

    @Test
    fun `assert that low accuracy mode reports unknown language for unigrams and bigrams`() {
        removeLanguageModelsFromDetector()

        val detector =
            LanguageDetectorBuilder
                .fromLanguages(ENGLISH, GERMAN)
                .withPreloadedLanguageModels()
                .withLowAccuracyMode()
                .build()

        assertThat(detector.detectLanguageOf("bed")).isNotEqualTo(UNKNOWN)
        assertThat(detector.detectLanguageOf("be")).isEqualTo(UNKNOWN)
        assertThat(detector.detectLanguageOf("b")).isEqualTo(UNKNOWN)
        assertThat(detector.detectLanguageOf("")).isEqualTo(UNKNOWN)
    }

    private fun assertThatAllLanguageModelsAreLoaded() {
        assertThat(LanguageDetector.languageModels.values).allMatch { it.hasLoadedQuadriFivegram() }
    }

    private fun assertThatAllLanguageModelsAreUnloaded() {
        assertThat(LanguageDetector.languageModels.values).noneMatch { it.isLoaded() }
    }

    private fun assertThatOnlyUniBiTrigramLanguageModelsAreLoaded() {
        assertThat(LanguageDetector.languageModels.values).noneMatch { it.hasLoadedQuadriFivegram() }
    }

    private fun removeLanguageModelsFromDetector() {
        detectorForAllLanguages.unloadLanguageModels()
    }
}
