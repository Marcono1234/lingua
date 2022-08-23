package com.github.pemistahl.lingua.app.multilanguage

import com.github.pemistahl.lingua.api.Language
import java.awt.Color
import java.util.EnumMap

private const val ALPHA = 50

internal class LanguageColorMap(
    /** All languages except [Language.UNKNOWN] */
    allLanguages: List<Language>
) {
    private val colorsMap = EnumMap<Language, Color>(Language::class.java)
    private val listeners = mutableListOf<(Map<Language, Color>) -> Unit>()

    init {
        // Add separate entry for UNKNOWN
        colorsMap[Language.UNKNOWN] = Color.LIGHT_GRAY.withAlpha(ALPHA)
        reassignColors(allLanguages)
    }

    fun addListener(listener: (changedColors: Map<Language, Color>) -> Unit) {
        listeners.add(listener)
    }

    fun getColor(language: Language): Color {
        return colorsMap[language]!!
    }

    fun reassignColors(languages: List<Language>) {
        val changedColors = EnumMap<Language, Color>(Language::class.java)
        languages.forEachIndexed { index, language ->
            val color = Color.getHSBColor((index + 1) / languages.size.toFloat(), 1f, 1f)
            changedColors[language] = color.withAlpha(ALPHA)
        }
        // Overwrite only the colors for the specified languages
        colorsMap.putAll(changedColors)

        listeners.forEach { it(changedColors) }
    }
}
