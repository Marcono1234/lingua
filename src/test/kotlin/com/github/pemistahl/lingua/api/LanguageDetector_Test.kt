package com.github.pemistahl.lingua.api

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

// Different class name to avoid clashes with original Lingua test class when merging upstream changes
@Suppress("ClassName")
abstract class LanguageDetector_Test {
    private lateinit var executor: ExecutorService
    private lateinit var languageDetector: LanguageDetector

    // Workaround because JUnit 5 does not support class level parameterization yet, see
    // https://github.com/junit-team/junit5/issues/878
    abstract fun LanguageDetectorBuilder.configureForTest(): LanguageDetectorBuilder

    @BeforeEach
    fun setupLanguageDetector() {
        val defaultThreadFactory = Executors.defaultThreadFactory()
        // Run single threaded to detect deadlock issues
        executor = Executors.newSingleThreadExecutor(
            ThreadFactory { runnable ->
                val thread = defaultThreadFactory.newThread(runnable)
                // Don't prevent JVM exit
                thread.isDaemon = true
                return@ThreadFactory thread
            }
        )
        languageDetector = LanguageDetectorBuilder.fromAllLanguages()
            .withExecutor(executor)
            .configureForTest()
            .build()
    }

    @AfterEach
    fun shutDownExecutor() {
        executor.shutdown()
    }

    /* ktlint-disable max-line-length */
    companion object {
        @JvmStatic
        fun getConfidenceValueArguments(): Stream<Arguments> {
            return Stream.of(
                ""
                    to "",
                "..."
                    to "",
                "this is a short test"
                    to "ENGLISH (100.00%), LATIN (93.09%), ESPERANTO (89.03%), TAGALOG (84.56%), DANISH (83.91%), FRENCH (83.75%), PORTUGUESE (83.62%), GERMAN (83.33%), BOKMAL (83.31%), NYNORSK (82.81%), DUTCH (82.49%), ALBANIAN (81.46%), SPANISH (81.18%), CATALAN (81.00%), WELSH (80.74%), BASQUE (80.54%), ROMANIAN (80.37%), ITALIAN (79.13%), SWEDISH (78.53%), ESTONIAN (78.33%), POLISH (76.92%), TURKISH (76.74%), HUNGARIAN (76.61%), AFRIKAANS (76.23%), SOMALI (76.14%), FINNISH (76.03%), IRISH (75.75%), YORUBA (74.96%), SLOVAK (74.80%), CROATIAN (74.07%), BOSNIAN (73.80%), CZECH (73.56%), XHOSA (73.21%), SWAHILI (72.89%), SHONA (72.45%), SLOVENE (72.14%), INDONESIAN (71.81%), LATVIAN (71.78%), ZULU (71.73%), LITHUANIAN (71.55%), SOTHO (70.42%)",
                "Ein kurzer Satz"
                    to "GERMAN (100.00%), LATVIAN (84.17%), BASQUE (80.38%), MAORI (76.24%), YORUBA (74.55%), POLISH (73.29%), DUTCH (71.35%), LATIN (70.14%)",
                // These are from the accuracyReport resources
                "Lederen underretter løbende bestyrelsen om personaleforholdene i institutionen."
                    to "DANISH (100.00%), BOKMAL (91.44%), NYNORSK (85.60%), GERMAN (77.01%), SWEDISH (73.51%), ALBANIAN (72.84%), LATIN (71.53%)",
                "Actualmente esta alquilado con buena renta."
                    to "SPANISH (100.00%), PORTUGUESE (89.04%), CATALAN (84.74%), TAGALOG (76.31%), ITALIAN (75.18%), LATIN (71.60%), BASQUE (71.13%), ENGLISH (70.05%)",
                "A dirlo è Jamil Sadegholvaad, assessore alla Sicurezza, in relazione agli atti vandalici e l’occupazione della palazzina ex Sert tra lanci di sedie e biciclette nella notte tra sabato e domenica."
                    to "ITALIAN (100.00%), LATIN (88.52%), ENGLISH (82.94%), FRENCH (80.72%), PORTUGUESE (78.98%), WELSH (77.55%), SPANISH (77.27%), TAGALOG (76.61%), CATALAN (76.24%), ROMANIAN (75.20%), SWEDISH (75.19%), YORUBA (75.07%), BOKMAL (73.94%), ESPERANTO (73.52%), ALBANIAN (73.11%), CROATIAN (72.72%), ESTONIAN (72.60%), DUTCH (72.47%), TURKISH (72.42%), NYNORSK (72.24%), HUNGARIAN (72.03%), DANISH (71.89%), VIETNAMESE (71.54%), GERMAN (71.25%), AFRIKAANS (71.02%), FINNISH (71.00%), BASQUE (70.75%), POLISH (70.63%), INDONESIAN (70.24%), SLOVENE (70.20%)",
                "口コミサイトには、審査に関しての細かい内容を口コミと一緒に記載していることがよくありますので、消費者金融の審査の詳細に興味をひかれている人は、ぜひ見ておいてください。"
                    to "JAPANESE (100.00%)",
                "Alijipangia kulinganisha uaminifu kwa kanuni na mabadiliko ya shirika, akionyesha hayo hayaendi kinyume cha nia ya mwanzilishi."
                    to "SWAHILI (100.00%), XHOSA (75.54%), ZULU (71.08%)",
            ).map { Arguments.of(it.first, it.second) }
        }

        @JvmStatic
        fun getMultiLanguageArguments(): Stream<Arguments> {
            // Some of these are based on multilingual bug reports from https://bugs.mojang.com
            return Stream.of(
                ""
                    to "",
                "...."
                    to "",
                "a"
                    to "0-1 (1): SOMALI; SOMALI (100%), SWAHILI (87%), TAGALOG (86%), MALAY (83%), INDONESIAN (80%), MAORI (79%), SHONA (77%), GANDA (77%), TSONGA (75%), TSWANA (74%), SOTHO (73%), IRISH (72%), BASQUE (72%) \n" +
                        "  a",
                "this is a test"
                    to "0-14 (11): ENGLISH; ENGLISH (100%), LATIN (97%), ESPERANTO (90%), ESTONIAN (88%), GERMAN (83%), PORTUGUESE (83%), FRENCH (83%), NYNORSK (83%), TAGALOG (82%), BOKMAL (82%), SPANISH (81%), DUTCH (81%), DANISH (81%), CATALAN (81%), FINNISH (81%), ROMANIAN (80%), WELSH (79%), POLISH (79%), ITALIAN (79%), XHOSA (79%), BOSNIAN (78%), SLOVAK (78%), ALBANIAN (78%), SWAHILI (78%), ZULU (78%), SWEDISH (77%), CZECH (76%), BASQUE (76%), SOTHO (76%), TURKISH (74%), AFRIKAANS (74%), SLOVENE (74%), CROATIAN (74%), HUNGARIAN (74%), YORUBA (72%), IRISH (72%), TSONGA (71%), SOMALI (71%), LITHUANIAN (71%), LATVIAN (70%), SHONA (70%), INDONESIAN (70%) \n" +
                        "  this is a test",
                "Hallo das ist ein Test mit ein paar Wörtern: But what if the text also contained English as part of the sentence?"
                    to "0-43 (35): GERMAN; GERMAN (100%), SWEDISH (86%), ESPERANTO (85%), DUTCH (85%), ALBANIAN (85%), FINNISH (84%), LATIN (84%), WELSH (84%), ESTONIAN (84%), NYNORSK (83%), YORUBA (82%), SOTHO (80%), CATALAN (80%), SPANISH (79%), PORTUGUESE (79%), HUNGARIAN (79%), ITALIAN (79%), TSONGA (77%), SHONA (76%), FRENCH (76%), CROATIAN (76%), POLISH (76%), CZECH (76%), SLOVAK (76%), ZULU (75%), XHOSA (75%), ROMANIAN (74%), SLOVENE (74%), TURKISH (72%), MAORI (72%), GANDA (70%), MALAY (70%), INDONESIAN (70%) \n" +
                        "  Hallo das ist ein Test mit ein paar Wörtern\n" +
                        "45-112 (55): ENGLISH; ENGLISH (100%), TAGALOG (79%), GERMAN (77%), FRENCH (76%), YORUBA (75%), ESPERANTO (75%), LATIN (75%), DUTCH (75%), ALBANIAN (74%), WELSH (74%), PORTUGUESE (74%), DANISH (73%), SPANISH (73%), SOTHO (73%), BOKMAL (72%), SWAHILI (72%), ITALIAN (72%), SWEDISH (72%), XHOSA (72%), CATALAN (72%), ROMANIAN (71%), TURKISH (71%), TSONGA (71%), ZULU (70%) \n" +
                        "  But what if the text also contained English as part of the sentence",
                "He turned around and asked: \"Entschuldigen Sie, sprechen Sie Deutsch?\""
                    to "0-26 (22): ENGLISH; ENGLISH (100%), DANISH (78%), SWEDISH (73%), TAGALOG (73%), BOKMAL (72%), NYNORSK (72%), WELSH (71%), YORUBA (71%) \n" +
                        "  He turned around and asked\n" +
                        "29-68 (34): GERMAN; GERMAN (100%), DUTCH (76%) \n" +
                        "  Entschuldigen Sie, sprechen Sie Deutsch",
                "When he came into the room, he greeted the others with \"Hallo zusammen, wie geht es euch?\" and sat down on a free chair."
                    to "0-54 (43): ENGLISH; ENGLISH (100%), TAGALOG (78%), XHOSA (77%), WELSH (76%), DUTCH (76%), GANDA (75%), YORUBA (75%), TSONGA (74%), AFRIKAANS (74%), LATIN (74%), ZULU (74%), BOKMAL (74%), DANISH (73%), TSWANA (73%), SOMALI (72%), ESPERANTO (72%), SLOVAK (71%), HUNGARIAN (71%), SPANISH (71%), ALBANIAN (71%), SWAHILI (71%), FRENCH (71%), INDONESIAN (71%), SHONA (70%), SWEDISH (70%), GERMAN (70%) \n" +
                        "  When he came into the room, he greeted the others with\n" +
                        "56-88 (26): GERMAN; GERMAN (100%), DUTCH (73%), DANISH (72%), SOTHO (71%), BOKMAL (71%), ITALIAN (70%) \n" +
                        "  Hallo zusammen, wie geht es euch\n" +
                        "91-119 (22): ENGLISH; ENGLISH (100%), TAGALOG (84%), DUTCH (80%), IRISH (80%), WELSH (80%), SHONA (80%), GERMAN (79%), TSONGA (79%), AFRIKAANS (78%), YORUBA (77%), FRENCH (77%), GANDA (77%), DANISH (76%), INDONESIAN (76%), ESPERANTO (76%), MALAY (75%), XHOSA (75%), MAORI (75%), ZULU (75%), SWAHILI (74%), SOMALI (74%), LATIN (74%), ROMANIAN (74%), SOTHO (73%), BASQUE (73%), ITALIAN (73%), SWEDISH (73%), BOKMAL (73%), PORTUGUESE (72%), TURKISH (72%), POLISH (72%), ALBANIAN (72%), SPANISH (72%), NYNORSK (71%), FINNISH (71%), CATALAN (71%), ESTONIAN (70%) \n" +
                        "  and sat down on a free chair",
                // Test apostrophe usage
                "Don't isn't it won't do they don't 'und hier ein Teil der in Deutsch geschrieben ist' can't it doesn't couldn't"
                    to "0-34 (24): ENGLISH; ENGLISH (100%), WELSH (94%), YORUBA (90%), IRISH (89%), SOMALI (89%), TAGALOG (85%), AFRIKAANS (84%), FRENCH (84%), ALBANIAN (83%), MALAY (83%), SHONA (82%), XHOSA (82%), TURKISH (81%), SPANISH (80%), LATIN (80%), ESPERANTO (80%), TSONGA (80%), SOTHO (80%), AZERBAIJANI (80%), DUTCH (80%), FINNISH (80%), INDONESIAN (79%), PORTUGUESE (79%), ZULU (79%), NYNORSK (79%), DANISH (79%), BOKMAL (78%), SWEDISH (78%), HUNGARIAN (78%), GERMAN (78%), GANDA (77%), CZECH (77%), TSWANA (77%), SLOVAK (77%), POLISH (77%), ICELANDIC (76%), ROMANIAN (76%), SWAHILI (76%), BASQUE (75%), LITHUANIAN (75%), CATALAN (75%), ITALIAN (75%), CROATIAN (74%), SLOVENE (74%), BOSNIAN (73%), VIETNAMESE (72%), MAORI (72%), LATVIAN (71%), ESTONIAN (71%) \n" +
                        "  Don't isn't it won't do they don't\n" +
                        "36-84 (40): GERMAN; GERMAN (100%), DUTCH (71%) \n" +
                        "  und hier ein Teil der in Deutsch geschrieben ist\n" +
                        "86-111 (19): ENGLISH; ENGLISH (100%), WELSH (88%), TAGALOG (86%), SOMALI (82%), NYNORSK (81%), SWEDISH (80%), IRISH (79%), YORUBA (79%), BASQUE (79%), ESPERANTO (78%), FRENCH (78%), SLOVENE (77%), LATIN (77%), BOKMAL (77%), MAORI (76%), GERMAN (76%), ESTONIAN (75%), PORTUGUESE (75%), SPANISH (74%), ALBANIAN (74%), SLOVAK (73%), CROATIAN (73%), DANISH (73%), ICELANDIC (73%), DUTCH (72%), AFRIKAANS (72%), HUNGARIAN (72%), CATALAN (72%), INDONESIAN (72%), LITHUANIAN (71%), ITALIAN (71%), TSWANA (70%), FINNISH (70%) \n" +
                        "  can't it doesn't couldn't",
                "First sentence\nsecond sentence\nthird sentence\nAber der letzte Satz ist in Deutsch"
                    to "0-45 (40): ENGLISH; ENGLISH (100%), TAGALOG (86%), DUTCH (79%), ESPERANTO (79%), FRENCH (78%), ITALIAN (76%), YORUBA (73%), XHOSA (73%), DANISH (72%), GERMAN (72%), LATIN (72%), WELSH (72%), SPANISH (71%), ROMANIAN (71%), BOKMAL (71%), PORTUGUESE (71%), TURKISH (71%), ZULU (70%) \n" +
                        "  First sentence\n" +
                        "  second sentence\n" +
                        "  third sentence\n" +
                        "46-81 (29): GERMAN; GERMAN (100%) \n" +
                        "  Aber der letzte Satz ist in Deutsch",
                "Frost walker's Turkish should be \"Buzlaştırıcı Yürüyücü\""
                    to "0-32 (27): ENGLISH; ENGLISH (100%), DUTCH (82%), ESPERANTO (81%), GERMAN (81%), WELSH (80%), DANISH (80%), TAGALOG (80%), SWEDISH (79%), YORUBA (79%), AFRIKAANS (78%), ICELANDIC (78%), LATIN (77%), NYNORSK (77%), BOKMAL (77%), ALBANIAN (76%), TURKISH (76%), SLOVENE (74%), SOMALI (74%), ITALIAN (74%), FRENCH (73%), LITHUANIAN (73%), BASQUE (73%), FINNISH (73%), CROATIAN (73%), IRISH (72%), SLOVAK (72%), POLISH (72%), ROMANIAN (72%), SPANISH (72%), INDONESIAN (72%), BOSNIAN (72%), HUNGARIAN (72%), ESTONIAN (72%), CATALAN (71%), PORTUGUESE (71%), MALAY (71%), AZERBAIJANI (71%) \n" +
                        "  Frost walker's Turkish should be\n" +
                        "34-55 (20): TURKISH; TURKISH (100%), AZERBAIJANI (80%) \n" +
                        "  Buzlaştırıcı Yürüyücü",
                "выдает такую ошибку Error Code: UNKNOWN code: Deep Ocean"
                    to "0-20 (17): RUSSIAN; RUSSIAN (100%), BELARUSIAN (75%), UKRAINIAN (73%) \n" +
                        "  выдает такую ошибку \n" +
                        "20-56 (30): ENGLISH; ENGLISH (100%), TAGALOG (84%), POLISH (83%), WELSH (82%), LATIN (81%), GERMAN (81%), ESPERANTO (81%), BOKMAL (80%), DUTCH (79%), ALBANIAN (78%), INDONESIAN (78%), SPANISH (78%), DANISH (78%), BASQUE (78%), SWEDISH (77%), YORUBA (77%), NYNORSK (77%), MAORI (76%), FRENCH (76%), ZULU (76%), MALAY (75%), IRISH (75%), ITALIAN (74%), AFRIKAANS (74%), XHOSA (74%), SOTHO (74%), CROATIAN (74%), PORTUGUESE (74%), ROMANIAN (74%), CATALAN (74%), HUNGARIAN (73%), TSONGA (73%), FINNISH (73%), SOMALI (73%), SLOVENE (72%), BOSNIAN (72%), ESTONIAN (71%), TURKISH (71%), CZECH (71%), ICELANDIC (70%), SLOVAK (70%) \n" +
                        "  Error Code: UNKNOWN code: Deep Ocean",
                "it is a pity that a moderator does not know that the 1.6.1 is not beta, but official, or read \"minimum requirements\". I say in any version of minecraft in the oneplus 6 of 8 of ram, it presents performance flaws \"lag or delays\".\nes una lástima que un moderador no sepa que la vercion 1.6.1 no es beta, sino oficial, ni sepa leer \"requisitos mínimos\". digo en cualquier versión de minecraft en el oneplus 6 de 8 de ram, presenta fallas de rendimiento \"lag o retrasos\"."
                    to "0-226 (169): ENGLISH; ENGLISH (100%), TAGALOG (86%), LATIN (79%), GERMAN (76%), FRENCH (76%), ESPERANTO (76%), INDONESIAN (75%), ITALIAN (75%), DUTCH (74%), DANISH (74%), PORTUGUESE (74%), CATALAN (74%), ALBANIAN (72%), MALAY (72%), SWEDISH (72%), NYNORSK (72%), ROMANIAN (72%), XHOSA (72%), BOKMAL (71%), SPANISH (71%), CZECH (71%), TURKISH (71%), POLISH (71%), WELSH (71%), SLOVAK (70%) \n" +
                        "  it is a pity that a moderator does not know that the 1.6.1 is not beta, but official, or read \"minimum requirements\". I say in any version of minecraft in the oneplus 6 of 8 of ram, it presents performance flaws \"lag or delays\n" +
                        "229-465 (180): SPANISH; SPANISH (100%), PORTUGUESE (88%), ITALIAN (86%), CATALAN (84%), TAGALOG (83%), ALBANIAN (82%), LATIN (82%), ESPERANTO (81%), ENGLISH (79%), NYNORSK (79%), BOSNIAN (78%), LITHUANIAN (78%), BASQUE (77%), LATVIAN (76%), INDONESIAN (76%), GERMAN (76%), SLOVAK (76%), SWEDISH (75%), WELSH (75%), DUTCH (75%), BOKMAL (75%), SHONA (75%), YORUBA (74%), DANISH (74%), FRENCH (74%), CROATIAN (74%), SWAHILI (74%), HUNGARIAN (74%), SLOVENE (74%), ROMANIAN (73%), MALAY (72%), AFRIKAANS (72%), CZECH (71%), FINNISH (71%), TURKISH (70%) \n" +
                        "  es una lástima que un moderador no sepa que la vercion 1.6.1 no es beta, sino oficial, ni sepa leer \"requisitos mínimos\". digo en cualquier versión de minecraft en el oneplus 6 de 8 de ram, presenta fallas de rendimiento \"lag o retrasos",
            ).map { Arguments.of(it.first, it.second) }
        }
    }
    /* ktlint-enable max-line-length */

    // Run with timeout to fail if single threaded executor gets stuck in deadlock
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @MethodSource("getConfidenceValueArguments")
    @ParameterizedTest
    fun testComputeConfidenceValues(text: String, expectedConfidenceValues: String) {
        val confidenceValues = languageDetector.computeLanguageConfidenceValues(text)
        assertFalse(confidenceValues.containsKey(Language.UNKNOWN))

        val confidenceValuesString = confidenceValues.filter { it.value > 0.7 }.map { e ->
            "${e.key} (${String.format(Locale.ENGLISH, "%.2f%%", e.value * 100)})"
        }.joinToString(", ")
        assertEquals(expectedConfidenceValues, confidenceValuesString)
    }

    // Run with timeout to fail if single threaded executor gets stuck in deadlock
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    @MethodSource("getMultiLanguageArguments")
    @ParameterizedTest
    fun testDetectMultiLanguageOf(text: String, expectedSectionsString: String) {
        val sections = languageDetector.detectMultiLanguageOf(text)
        val sectionsString = sections.joinToString("\n") {
            val confidenceValueString = it.confidenceValues.filter { e -> e.value > 0.7 }.map { e ->
                "${e.key} (${String.format(Locale.ENGLISH, "%.0f%%", e.value * 100)})"
            }.joinToString(", ")
            val sectionText = ("\n" + it.sectionText).replace(Regex("\\R"), "\n  ")
            "${it.start}-${it.end} (${it.lettersCount}): ${it.language}; $confidenceValueString $sectionText"
        }
        assertEquals(expectedSectionsString, sectionsString)
    }

    internal class RegularSpeed : LanguageDetector_Test() {
        override fun LanguageDetectorBuilder.configureForTest(): LanguageDetectorBuilder {
            return this
        }
    }

    internal class IncreasedSpeed : LanguageDetector_Test() {
        override fun LanguageDetectorBuilder.configureForTest(): LanguageDetectorBuilder {
            return withIncreasedDetectionSpeed()
        }
    }
}
