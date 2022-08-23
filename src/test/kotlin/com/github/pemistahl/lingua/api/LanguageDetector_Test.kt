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
class LanguageDetector_Test {
    private lateinit var executor: ExecutorService
    private lateinit var languageDetector: LanguageDetector

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
}
