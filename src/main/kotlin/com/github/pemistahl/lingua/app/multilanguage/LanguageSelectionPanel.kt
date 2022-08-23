package com.github.pemistahl.lingua.app.multilanguage

import com.github.pemistahl.lingua.api.Language
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingConstants

/**
 * Panel for selecting the languages which should be considered for language detection.
 */
internal class LanguageSelectionPanel(
    private val multiLanguageModel: MultiLanguageModel,
    languages: List<Language>,
    private val languageColorMap: LanguageColorMap,
) : JPanel(GridBagLayout()) {
    @Suppress("JoinDeclarationAndAssignment")
    private val selectAllCheckbox: TriStateCheckbox
    private val tooFewLanguagesLabel: JLabel
    private val checkboxes: List<LanguageCheckbox>
    private val showUnselectedLanguagesCheckbox: JCheckBox

    init {
        selectAllCheckbox = TriStateCheckbox("Select all / none")
        run {
            val c = GridBagConstraints()
            c.gridx = 0
            c.gridy = GridBagConstraints.RELATIVE
            c.fill = GridBagConstraints.HORIZONTAL
            c.anchor = GridBagConstraints.LINE_START
            add(selectAllCheckbox, c)
        }

        tooFewLanguagesLabel = JLabel("Too few languages", SwingConstants.CENTER)
        tooFewLanguagesLabel.border = BorderFactory.createEmptyBorder(0, 3, 5, 3)
        tooFewLanguagesLabel.foreground = Color.RED.multiplyHSBBrightness(0.8f)
        tooFewLanguagesLabel.isVisible = false
        run {
            val c = GridBagConstraints()
            c.gridx = 0
            c.gridy = GridBagConstraints.RELATIVE
            c.fill = GridBagConstraints.HORIZONTAL
            c.anchor = GridBagConstraints.CENTER
            add(tooFewLanguagesLabel, c)
        }

        checkboxes = createCheckboxes(languages)
        val languagesPanel = JPanel(GridBagLayout())
        checkboxes.forEach {
            val c = GridBagConstraints()
            c.gridx = 0
            c.gridy = GridBagConstraints.RELATIVE
            c.fill = GridBagConstraints.HORIZONTAL
            languagesPanel.add(it, c)
        }

        val languagesScrollPane = JScrollPane(languagesPanel)
        languagesScrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        languagesScrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        // Increase scroll speed; scroll one element at a time
        val increment = checkboxes.first().preferredSize.height
        languagesScrollPane.verticalScrollBar.unitIncrement = increment
        languagesScrollPane.verticalScrollBar.blockIncrement = increment
        run {
            val c = GridBagConstraints()
            c.gridx = 0
            c.gridy = GridBagConstraints.RELATIVE
            c.fill = GridBagConstraints.BOTH
            c.weightx = 1.0
            c.weighty = 1.0
            add(languagesScrollPane, c)
        }

        selectAllCheckbox.addListener { isSelected ->
            checkboxes.forEach { it.isSelected = isSelected }
            onSelectedLanguagesChanged()
        }

        showUnselectedLanguagesCheckbox = JCheckBox("Show unselected languages", true)
        showUnselectedLanguagesCheckbox.addActionListener { updateLanguageCheckboxesVisibility() }
        run {
            val c = GridBagConstraints()
            c.gridx = 0
            c.gridy = GridBagConstraints.RELATIVE
            c.fill = GridBagConstraints.HORIZONTAL
            c.anchor = GridBagConstraints.LINE_START
            add(showUnselectedLanguagesCheckbox, c)
        }

        val reassignColorsButton = JButton("Reassign colors")
        reassignColorsButton.toolTipText = "Reassign colors to the currently selected languages"
        reassignColorsButton.addActionListener {
            languageColorMap.reassignColors(getSelectedLanguages())
        }
        run {
            val c = GridBagConstraints()
            c.gridx = 0
            c.gridy = GridBagConstraints.RELATIVE
            c.insets = Insets(2, 4, 4, 4)
            add(reassignColorsButton, c)
        }

        languageColorMap.addListener { changedColors ->
            checkboxes.forEach { checkbox ->
                val changedColor = changedColors[checkbox.language]
                if (changedColor != null) {
                    checkbox.setIconColor(changedColor)
                }
            }
        }
    }

    private fun createCheckboxes(languages: List<Language>): List<LanguageCheckbox> {
        return languages.map { language ->
            val languageColor = languageColorMap.getColor(language)
            val checkbox = LanguageCheckbox(language, languageColor)
            checkbox.addActionListener {
                onSelectedLanguagesChanged()
            }
            return@map checkbox
        }
    }

    private fun updateLanguageCheckboxesVisibility() {
        if (showUnselectedLanguagesCheckbox.isSelected) {
            checkboxes.forEach { it.isVisible = true }
        } else {
            checkboxes.forEach { it.isVisible = it.isSelected }
        }
    }

    private fun getSelectedLanguages() = checkboxes.filter { it.isSelected }.map { it.language }

    private fun onSelectedLanguagesChanged() {
        val languages = getSelectedLanguages()
        selectAllCheckbox.selectionState = when (languages.size) {
            0 -> TriStateCheckbox.SelectionState.NOT_SELECTED
            checkboxes.size -> TriStateCheckbox.SelectionState.SELECTED
            else -> TriStateCheckbox.SelectionState.SOME_SELECTED
        }

        val tooFewLanguages = !multiLanguageModel.setLanguages(languages)
        tooFewLanguagesLabel.isVisible = tooFewLanguages

        if (tooFewLanguages) {
            // If too few languages are selected, show all languages again to allow selecting more
            showUnselectedLanguagesCheckbox.isSelected = true
        }
        updateLanguageCheckboxesVisibility()
    }
}
