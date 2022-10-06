package com.github.pemistahl.lingua.internal

import com.github.pemistahl.lingua.api.Language
import com.github.pemistahl.lingua.api.LanguageDetector
import com.github.pemistahl.lingua.internal.Constant.isJapaneseScript
import com.github.pemistahl.lingua.internal.util.WordList
import com.github.pemistahl.lingua.internal.util.extension.isLogogram
import com.github.pemistahl.lingua.internal.util.lazyAssert
import it.unimi.dsi.fastutil.objects.Object2DoubleLinkedOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2DoubleSortedMap
import java.lang.Character.UnicodeScript
import java.util.EnumSet
import kotlin.math.min

/*
 * Multi-language detection is implemented like this:
 *
 * 1. Split text into sections where language switches might occur, this includes:
 *    - Unicode script changes (requires special casing for languages which use more than one script,
 *      e.g. Japanese)
 *    - Quotation marks
 *    - Colon (':')
 *    - Line and page breaks
 *    - ...
 * 2. For each section determine the set of languages by rules
 *    1. Try to detect the language with LanguageDetector.detectLanguageWithRules
 *    2. Otherwise, try to detect the possible languages with LanguageDetector.filterLanguagesByRules
 * 3. Merge adjacent sections whose language set has the size 1 and which have the same language
 * 4. For each section determine the confidence values
 *    - For sections which from the previous steps only have a single language detected by rules
 *      the confidence value can be set to 1.0
 *    - Because accuracy is not good for short texts, merge short texts with subsequent ones if the
 *      languages detected with rules permit this (i.e. there must be overlap).
 *      To avoid erroneously merging short sections, if there is a next (long) section determine if the
 *      current section rather belongs to the previous one or to the next one.
 * 5. Merge adjacent sections whose most common languages are also quite common in the respectively other
 *    section
 */

private fun Char.isPotentialLanguageBoundary(previousChar: Char, nextChar: Char): Boolean {
    when (this) {
        ':' -> return true
        // Based on https://en.wikipedia.org/wiki/Newline#Unicode
        // Code point categories checked below does not seem to cover them
        '\n', '\r', '\u000B', '\u000C', '\u0085' -> return true
        // Based on https://en.wikipedia.org/wiki/Quotation_mark#Unicode_code_point_table
        // Code point categories checked below does not seem to cover all of them
        '"',
        '\u00AB', '\u00BB',
        '\u2018', '\u201B', // U+2019 is covered separately below
        '\u201C', '\u201D', '\u201E',
        '\u201F',
        '\u2039', '\u203A',
        '\u2E42',
        '\u231C', '\u231D',
        '\u275B', '\u275C',
        '\u275D', '\u275E',
        '\u300C', '\u300D',
        '\u300E', '\u300F',
        '\u301D', '\u301E', '\u301F',
        '\uFE41', '\uFE42',
        '\uFE43', '\uFE44',
        '\uFF02', '\uFF07',
        '\uFF62', '\uFF63' -> return true
        // Cannot cover these supplementary code points here because function is for Char: U+1F676, U+1F677, U+1F678
    }

    // For chars which may also be used as apostrophe, ignore them if they appear between two letters
    // Note: In case this still causes too many false positives, might have to make this more strict by ignoring char
    //       if previous or next char is letter, or remove the check for these chars completely
    if ((this == '\'' || this == '\u2019')) {
        return !(previousChar.isLetter() && nextChar.isLetter())
    }

    when (this.category) {
        CharCategory.INITIAL_QUOTE_PUNCTUATION,
        CharCategory.FINAL_QUOTE_PUNCTUATION,
        CharCategory.LINE_SEPARATOR,
        CharCategory.PARAGRAPH_SEPARATOR -> return true
        else -> {}
    }

    // Note: Maybe also consider chars indicating or affecting directionality, but current detection of
    // script difference most likely suffices for now

    return false
}

private open class PotentialSection(
    /** Start index of the first letter; 0-based, inclusive */
    private val start: Int,
    /** End index of the last letter; 0-based, exclusive */
    private var end: Int,
    private var lettersCount: Int,
    private val fullText: String,
    // Cache text to reduce number of created substrings
    private var cachedText: String? = null,
) {
    fun getStart() = start
    fun getEnd() = end
    fun getLettersCount() = lettersCount

    fun containsNonLetter(): Boolean {
        return lettersCount != end - start
    }

    fun getText(): String {
        if (cachedText == null) {
            cachedText = fullText.substring(start, end)
        }
        return cachedText!!
    }

    /**
     * Merges this section with [other] by updating end position and letters count.
     */
    fun mergeWith(other: PotentialSection) {
        val otherEnd = other.end
        check(otherEnd > end)

        this.end = otherEnd
        this.lettersCount += other.lettersCount
        cachedText = null
    }

    /**
     * Gets the minimum confidence for this section for languages to be considered relevant.
     */
    fun getMinConfidence(): Double {
        // Note: These are estimated values and might have to be tweaked
        val minLettersDefiniteConfidence = 40.0
        return 0.6 + (min(1.0, getLettersCount() / minLettersDefiniteConfidence) * 0.38)
    }

    fun toLanguageSection(): LanguagePotentialSection {
        return LanguagePotentialSection(
            start,
            end,
            lettersCount,
            fullText,
            cachedText
        )
    }

    fun toConfidenceValuesSection(
        languageDetector: LanguageDetector,
        confidenceValues: Object2DoubleSortedMap<Language>
    ): ConfidenceValuesPotentialSection {
        return ConfidenceValuesPotentialSection(
            start,
            end,
            lettersCount,
            fullText,
            cachedText,
            languageDetector,
            confidenceValues
        )
    }

    override fun toString(): String {
        return "$start - $end ($lettersCount): ${getText()}"
    }
}

private fun UnicodeScript.belongsToSameLanguageAs(other: UnicodeScript): Boolean {
    return this == other || isJapaneseScript(this) && isJapaneseScript(other)
}

private fun splitPotentialSections(text: String): MutableList<PotentialSection> {
    // Impose minimum length to avoid creating sections for separate characters
    val minSectionLength = 3

    val sections = mutableListOf<PotentialSection>()

    var start = -1
    var end = -1
    var lastScript: UnicodeScript? = null
    var lettersCount = 0
    var hasLogograms = false

    text.forEachIndexed { index, char ->
        if (char.isLetter()) {
            val script = UnicodeScript.of(char.code)

            if (start != -1 && (hasLogograms || lettersCount >= minSectionLength) &&
                lastScript != null && !lastScript!!.belongsToSameLanguageAs(script)
            ) {
                sections.add(PotentialSection(start, index, lettersCount, text))

                // Current letter is start of new section
                start = index
                lettersCount = 1
                hasLogograms = false
            } else {
                if (start == -1) {
                    start = index
                }

                // Mark current letter as potential last letter
                end = index + 1
            }

            lastScript = script
            lettersCount++
            hasLogograms = hasLogograms || char.isLogogram()
        } else if (lettersCount >= minSectionLength && start != -1 &&
            char.isPotentialLanguageBoundary(
                    if (index == 0) '\u0000' else text[index - 1],
                    if (index + 1 >= text.length) '\u0000' else text[index + 1]
                )
        ) {
            sections.add(PotentialSection(start, end, lettersCount, text))
            start = -1
            lettersCount = 0
            hasLogograms = false
        }
    }

    // Add trailing section
    if (start != -1) {
        sections.add(PotentialSection(start, end, lettersCount, text))
    }

    return sections
}

private class LanguagePotentialSection(
    start: Int,
    end: Int,
    lettersCount: Int,
    fullText: String,
    cachedText: String?,
    val ruleBasedLanguages: EnumSet<Language> = EnumSet.noneOf(Language::class.java),
) : PotentialSection(start, end, lettersCount, fullText, cachedText) {

    fun canBeMergedWith(other: LanguagePotentialSection): Boolean {
        // Don't merge if exact language of subsequent section was determined
        return other.ruleBasedLanguages.size != 1 &&
            // Only merge if there is overlap between the languages
            other.ruleBasedLanguages.any(ruleBasedLanguages::contains)
    }

    fun mergeWith(other: LanguagePotentialSection) {
        // Call parent method
        mergeWith(other as PotentialSection)
        ruleBasedLanguages.addAll(other.ruleBasedLanguages)
    }

    override fun toString(): String {
        return super.toString() + "\n  $ruleBasedLanguages"
    }
}

private fun createSectionsWithRuleBasedLanguage(
    text: String,
    languageDetector: LanguageDetector
): MutableList<LanguagePotentialSection> {
    // Note: Could try to run rule-based detection concurrently, but overhead due to number of created
    // CompletableFuture instances and increased complexity might not justify this

    val sections = splitPotentialSections(text).map { it.toLanguageSection() }.toMutableList()
    val iterator = sections.iterator()
    var previousSection: LanguagePotentialSection? = null

    while (iterator.hasNext()) {
        val section = iterator.next()
        val sectionText = section.getText()
        val wordList = WordList.build(sectionText)

        val language = languageDetector.detectLanguageWithRules(wordList)
        if (language != Language.UNKNOWN) {
            // Check if section can be merged with previous one
            if (previousSection != null && previousSection.ruleBasedLanguages.singleOrNull() == language) {
                previousSection.mergeWith(section)
                // Remove this section
                iterator.remove()
            } else {
                section.ruleBasedLanguages.add(language)
                previousSection = section
            }
        } else {
            val languages = languageDetector.filterLanguagesByRules(wordList)

            // Check if section can be merged with previous one
            if (languages.size == 1 && previousSection != null &&
                previousSection.ruleBasedLanguages.singleOrNull() == languages.first()
            ) {
                previousSection.mergeWith(section)
                // Remove this section
                iterator.remove()
            } else {
                section.ruleBasedLanguages.addAll(languages)
                previousSection = section
            }
        }
    }

    return sections
}

private class ConfidenceValuesPotentialSection(
    start: Int,
    end: Int,
    lettersCount: Int,
    fullText: String,
    cachedText: String?,
    private val languageDetector: LanguageDetector,
    private var cachedConfidenceValues: Object2DoubleSortedMap<Language>?,
) : PotentialSection(start, end, lettersCount, fullText, cachedText) {

    fun mergeWith(other: ConfidenceValuesPotentialSection) {
        // Call parent method
        mergeWith(other as PotentialSection)
        cachedConfidenceValues = null
    }

    fun getConfidenceValues(): Object2DoubleSortedMap<Language> {
        if (cachedConfidenceValues == null) {
            // Must recompute confidence values; when merging sections cannot merge their confidence values
            // because section lengths differ, and also the longer the section the more reliable the confidence
            // values become
            // TODO: Don't join() here; refactor to run concurrently?
            cachedConfidenceValues = languageDetector.computeLanguageConfidenceValuesFuture(getText()).join()
        }
        return cachedConfidenceValues!!
    }

    fun getLanguagesWithMinConfidence(): EnumSet<Language> {
        val minConfidence = getMinConfidence()
        val languages = EnumSet.noneOf(Language::class.java)
        for ((language, confidence) in getConfidenceValues()) {
            if (confidence >= minConfidence) {
                languages.add(language)
            } else {
                // Map contains entries in descending order; can stop as soon as confidence is too low
                break
            }
        }

        return languages
    }

    fun toResultSection(): LanguageDetector.LanguageSection {
        val confidenceValues = getConfidenceValues()
        lazyAssert { !confidenceValues.contains(Language.UNKNOWN) }

        return LanguageDetector.LanguageSection(
            getStart(),
            getEnd(),
            getLettersCount(),
            getText(),
            languageDetector.getLanguageFromConfidenceValues(confidenceValues),
            confidenceValues
        )
    }

    override fun toString(): String {
        val confidenceValues = getConfidenceValues().keys.take(5).joinToString(", ")
        return super.toString() + "\n  $confidenceValues ..."
    }
}

private fun createSectionsWithConfidenceValues(
    text: String,
    languageDetector: LanguageDetector
): MutableList<ConfidenceValuesPotentialSection> {

    val sections = createSectionsWithRuleBasedLanguage(text, languageDetector)
    val confidenceSections = mutableListOf<ConfidenceValuesPotentialSection>()

    var index = 0
    while (index < sections.size) {
        val section = sections[index]
        val ruleBasedLanguage = section.ruleBasedLanguages.singleOrNull()

        if (ruleBasedLanguage != null) {
            // No need to use LanguageDetector.computeLanguageConfidenceValues if rules already determined language
            val confidenceValues = Object2DoubleLinkedOpenHashMap<Language>()
            confidenceValues.put(ruleBasedLanguage, 1.0)

            confidenceSections.add(section.toConfidenceValuesSection(languageDetector, confidenceValues))
        } else {
            // Try to merge sections until they contain enough letters for reliable detection
            while (index < sections.size - 1) {
                val nextSection = sections[index + 1]

                val shouldMerge = shouldMergeShortRuleBasedSection(
                    section,
                    sections[index + 1],
                    confidenceSections.lastOrNull(),
                    languageDetector
                )

                if (shouldMerge) {
                    section.mergeWith(nextSection)
                    // Skip merged section
                    index++
                } else {
                    // Stop merging
                    break
                }
            }

            val sectionText = section.getText()
            // TODO: Don't join() here; refactor to run concurrently?
            val confidenceValues = languageDetector.computeLanguageConfidenceValuesFuture(sectionText).join()

            confidenceSections.add(section.toConfidenceValuesSection(languageDetector, confidenceValues))
        }

        index++
    }

    return confidenceSections
}

internal fun LanguageDetector.internalDetectMultiLanguageOf(text: String): List<LanguageDetector.LanguageSection> {
    val sections = createSectionsWithConfidenceValues(text, this)

    if (sections.isEmpty()) {
        return emptyList()
    }

    var previousSection: ConfidenceValuesPotentialSection = sections.first()
    val iterator = sections.listIterator()
    // Skip first section because it is stored in previousSection already
    iterator.next()
    while (iterator.hasNext()) {
        val section = iterator.next()
        val nextSection = iterator.nextIndex()
            .let { if (it < sections.size) sections[it] else null }

        if (canMergeConfidenceSection(section, previousSection, nextSection)) {
            // Merge sections and confidence values
            previousSection.mergeWith(section)
            // Remove merged section
            iterator.remove()
        } else {
            previousSection = section
        }
    }

    return sections.map { it.toResultSection() }
}

private fun shouldMergeShortRuleBasedSection(
    current: LanguagePotentialSection,
    next: LanguagePotentialSection,
    previousConfidenceSection: ConfidenceValuesPotentialSection?,
    languageDetector: LanguageDetector
): Boolean {
    /** Estimated minimum number of letters to get reliable language detection results */
    val minReliableLettersCount = 15

    if (current.getLettersCount() >= minReliableLettersCount || !current.canBeMergedWith(next)) {
        return false
    }

    // Even though accuracy for current section is low, if next section is long enough try to determine
    // if current section really belongs to next section and can safely be merged
    if (next.getLettersCount() >= minReliableLettersCount &&
        // However, ignore if section is too short or consists only of a single word
        current.getLettersCount() >= 5 && current.containsNonLetter()
    ) {
        // TODO: Don't join() here; refactor to run concurrently?
        val confidenceValues = languageDetector.computeLanguageConfidenceValuesFuture(current.getText()).join()
        val minConfidence = current.getMinConfidence()
        val nextConfidenceValues = languageDetector.computeLanguageConfidenceValuesFuture(next.getText()).join()
        val minNextConfidence = next.getMinConfidence()

        val isConfidenceForMostCommonLanguagesTooLow = confidenceValues.object2DoubleEntrySet()
            .filter { it.doubleValue > minConfidence }
            .map { it.key }
            .all { nextConfidenceValues.getDouble(it) < minNextConfidence }

        if (isConfidenceForMostCommonLanguagesTooLow) {
            // Don't merge
            return false
        }

        if (previousConfidenceSection != null) {
            val previousConfidenceValues = previousConfidenceSection.getConfidenceValues()

            // Check if current section belongs more closely to previous section than to next one
            val nextConfidence = confidenceValues.getDouble(nextConfidenceValues.firstKey())
            val previousConfidence = confidenceValues.getDouble(previousConfidenceValues.firstKey())

            // Only merge with next section if current section belongs more closely to it
            return nextConfidence >= previousConfidence
        }
    }

    return true
}

/**
 * Checks whether [current] can be merged with [previous].
 */
private fun canMergeConfidenceSection(
    current: ConfidenceValuesPotentialSection,
    previous: ConfidenceValuesPotentialSection,
    next: ConfidenceValuesPotentialSection?
): Boolean {
    val currentRelevantLanguages = current.getLanguagesWithMinConfidence()
    val previousRelevantLanguages = previous.getLanguagesWithMinConfidence()

    // Check if both sections share languages with high confidence
    val previousCurrentOverlap = EnumSet.copyOf(currentRelevantLanguages).apply { retainAll(previousRelevantLanguages) }

    if (previousCurrentOverlap.isEmpty()) {
        return false
    }
    if (next == null) {
        return true
    }

    // Else make sure that section does not belong more closely to next section than to previous one

    val nextRelevantLanguages = next.getLanguagesWithMinConfidence()
    val currentNextOverlap = EnumSet.copyOf(currentRelevantLanguages).apply { retainAll(nextRelevantLanguages) }

    if (currentNextOverlap.isEmpty()) {
        // Can merge current and previous if there is no overlap between current and next
        return true
    }

    val previousNextOverlap = EnumSet.copyOf(previousRelevantLanguages).apply { retainAll(nextRelevantLanguages) }
    if (previousNextOverlap.isNotEmpty()) {
        // Can merge if previous and next can probably be merged, then it is not an issue if previous and current
        // are merged, and in the next step (previous + current) + next
        return true
    }

    val currentConfidenceValues = current.getConfidenceValues()
    val previousCurrentConfidence = previousCurrentOverlap.map(currentConfidenceValues::getDouble).average()
    val nextCurrentConfidence = currentNextOverlap.map(currentConfidenceValues::getDouble).average()

    return previousCurrentConfidence > nextCurrentConfidence
}
