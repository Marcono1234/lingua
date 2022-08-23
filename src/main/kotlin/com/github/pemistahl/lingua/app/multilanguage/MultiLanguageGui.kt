package com.github.pemistahl.lingua.app.multilanguage

import com.github.pemistahl.lingua.api.Language
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Toolkit
import javax.swing.BorderFactory
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import javax.swing.WindowConstants

internal fun openMultiLanguageDetectionGui() {
    SwingUtilities.invokeAndWait {
        val languages = Language.values().toMutableList()
        languages.remove(Language.UNKNOWN)

        val model = MultiLanguageModel(languages)
        val colorMap = LanguageColorMap(languages)

        ToolTipManager.sharedInstance().dismissDelay = 4_000

        val frame = JFrame("Multi-language detection")
        frame.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE

        frame.layout = GridBagLayout()

        run {
            val c = GridBagConstraints()
            c.gridx = 0
            c.fill = GridBagConstraints.VERTICAL
            c.weighty = 1.0
            c.gridheight = 2 // To cover both text area and summary label below it
            frame.add(LanguageSelectionPanel(model, languages, colorMap), c)
        }
        run {
            val textArea = MultiLanguageTextArea(model, colorMap)
            textArea.border = BorderFactory.createEmptyBorder(3, 3, 3, 3)

            val scrollPane = JScrollPane(textArea)
            scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_ALWAYS

            val c = GridBagConstraints()
            c.gridx = 1
            c.fill = GridBagConstraints.BOTH
            c.weightx = 1.0
            c.weighty = 1.0
            frame.add(scrollPane, c)
        }
        run {
            val noSectionsText = "Sections: 0"
            val summaryLabel = JLabel(noSectionsText)
            summaryLabel.border = BorderFactory.createEmptyBorder(3, 3, 3, 3)
            model.addListener {
                val sections = it.getSections()
                if (sections.isEmpty()) {
                    summaryLabel.text = noSectionsText
                } else {
                    val detectedLanguages = sections.map { s -> s.language }
                        .groupingBy { l -> l }
                        .eachCount()
                        .entries
                        .sortedWith { a, b ->
                            // Sort highest count first
                            val diff = b.value - a.value
                            if (diff != 0) return@sortedWith diff

                            return@sortedWith a.key.compareTo(b.key)
                        }
                        .map { e -> "${e.key} (${e.value})" }
                    summaryLabel.text = "Sections: ${sections.size}; Languages: ${detectedLanguages.joinToString(", ")}"
                }
            }

            val c = GridBagConstraints()
            c.gridx = 1
            c.gridy = GridBagConstraints.RELATIVE
            c.fill = GridBagConstraints.HORIZONTAL
            frame.add(summaryLabel, c)
        }

        val screenSize: Dimension = Toolkit.getDefaultToolkit().screenSize
        frame.preferredSize = Dimension(screenSize.width / 3, screenSize.height / 3)
        frame.pack()
        // Center on screen
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

fun main() {
    openMultiLanguageDetectionGui()
}
