package com.github.pemistahl.lingua.app.multilanguage

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetector
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder
import java.util.SortedMap
import java.util.TreeMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingWorker

/**
 * Contains the logic for the language detection and stores the results of the
 * last detected languages.
 */
internal class MultiLanguageModel(
    initialLanguages: List<Language>
) {
    data class DetectionEntry(
        /** 0-based inclusive start index within the complete text */
        val startIndex: Int,
        /** 0-based exclusive end index within the complete text */
        val endIndex: Int,
        val language: Language,
        val confidenceValues: SortedMap<Language, Double>,
    )

    private var languageDetector: LanguageDetector? = null

    private var text: String = ""
    private val sectionsMap = TreeMap<Int, DetectionEntry>()
    private var sections = emptyList<LanguageDetector.LanguageSection>()
    private var maxSectionLength: Int = 0

    private val listeners: MutableList<(MultiLanguageModel) -> Unit> = mutableListOf()

    private var currentWorker: SwingWorker<*, *>? = null
    // Use separate AtomicBoolean because `SwingWorker.cancel` call has no effect if it already finished
    private var currentWorkerCancelled: AtomicBoolean? = null

    init {
        setLanguages(initialLanguages)
    }

    fun addListener(listener: (MultiLanguageModel) -> Unit) {
        listeners.add(listener)
    }

    fun getEntryForIndex(index: Int): DetectionEntry? {
        return sectionsMap.subMap(index - maxSectionLength, true, index, true).entries
            .stream()
            .filter { (_, value) ->
                val sectionEnd = value.endIndex
                index < sectionEnd
            }
            .map { it.value }
            .findAny()
            .orElse(null)
    }

    fun hasEntryForLanguage(language: Language): Boolean {
        return sectionsMap.values.any { it.language == language }
    }

    /**
     * Gets the detected language sections. The returned list can be empty.
     */
    fun getSections() = sections

    /**
     * Updates the text for which the languages should be detected.
     */
    fun updateText(text: String) {
        this.text = text
        detectLanguages()
    }

    private fun detectLanguages() {
        sectionsMap.clear()
        val languageDetector = this.languageDetector
        currentWorker?.cancel(true)
        currentWorkerCancelled?.set(true)

        if (languageDetector != null) {
            val currentWorkerCancelled = AtomicBoolean(false)
            this.currentWorkerCancelled = currentWorkerCancelled
            currentWorker = object : SwingWorker<List<LanguageDetector.LanguageSection>, Unit>() {
                override fun doInBackground(): List<LanguageDetector.LanguageSection> {
                    return languageDetector.detectMultiLanguageOf(text)
                }

                override fun done() {
                    if (!isCancelled && !currentWorkerCancelled.get()) {
                        val sections = get()
                        sections.forEach {
                            this@MultiLanguageModel.sectionsMap[it.start] = DetectionEntry(
                                it.start,
                                it.end,
                                it.language,
                                it.confidenceValues
                            )
                        }
                        maxSectionLength = sections.maxOfOrNull { it.end - it.start } ?: 0
                        this@MultiLanguageModel.sections = sections

                        listeners.forEach { it(this@MultiLanguageModel) }
                    }
                }
            }.also { it.execute() }
        } else {
            sections = emptyList()
            listeners.forEach { it(this) }
        }
    }

    /**
     * Sets the languages which can be detected by the language detector.
     *
     * @return
     *      false if too few languages were specified
     */
    fun setLanguages(languages: List<Language>): Boolean {
        @Suppress("LiftReturnOrAssignment")
        if (languages.size < 2) {
            languageDetector = null
            detectLanguages()
            return false
        } else {
            languageDetector = LanguageDetectorBuilder.fromLanguages(*languages.toTypedArray())
                .build()
            detectLanguages()
            return true
        }
    }
}
