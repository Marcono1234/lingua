package com.github.pemistahl.lingua.app.multilanguage

import java.awt.Font
import java.awt.event.MouseEvent
import java.util.Locale
import javax.swing.JTextArea
import javax.swing.ToolTipManager
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Text area which contains the text whose languages should be detected and which highlights the
 * text based on the language detection results.
 */
internal class MultiLanguageTextArea(
    private val multiLanguageModel: MultiLanguageModel,
    private val languageColorMap: LanguageColorMap,
) : JTextArea() {
    init {
        font = Font(Font.SANS_SERIF, Font.PLAIN, 20)
        lineWrap = true

        document.addDocumentListener(object : DocumentListener {
            override fun removeUpdate(e: DocumentEvent) {
                detectLanguages()
            }

            override fun insertUpdate(e: DocumentEvent) {
                detectLanguages()
            }

            override fun changedUpdate(e: DocumentEvent) {
                // Not interested in attribute changes
            }
        })
        ToolTipManager.sharedInstance().registerComponent(this)

        multiLanguageModel.addListener {
            updateHighlightedSections()
        }
        languageColorMap.addListener { changedColors ->
            if (changedColors.keys.any(multiLanguageModel::hasEntryForLanguage)) {
                // Update highlighted sections because their color has changed
                updateHighlightedSections()
            }
        }
    }

    private fun MultiLanguageModel.DetectionEntry.getRenderedString(): String {
        val builder = StringBuilder()
        builder.append("<html><b>Language:</b> $language\n")
        builder.append("<p>\n")
        // Only show at most five languages
        confidenceValues.entries.take(5).forEach {
            builder.append("${it.key}: ${String.format(Locale.ENGLISH, "%.2f", it.value)}<br>\n")
        }
        builder.append("</p></html>\n")

        return builder.toString()
    }

    override fun getToolTipText(event: MouseEvent): String? {
        val offset = viewToModel2D(event.point)
        if (offset == -1) return null

        return multiLanguageModel.getEntryForIndex(offset)?.getRenderedString()
    }

    private fun detectLanguages() {
        multiLanguageModel.updateText(text)
    }

    private fun updateHighlightedSections() {
        highlighter.removeAllHighlights()
        multiLanguageModel.getSections().forEach { section ->
            val painter = BorderedHighlightPainter(languageColorMap.getColor(section.language))
            highlighter.addHighlight(section.start, section.end, painter)
        }
        // Trigger complete repaint because BorderedHighlightPainter seems to paint outside area expected by
        // Highlighter, causing rendering artifacts otherwise
        repaint()
    }
}
