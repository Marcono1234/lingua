package com.github.pemistahl.lingua.app.multilanguage

import com.github.pemistahl.lingua.api.Language
import java.awt.Color
import javax.swing.JCheckBox

private const val SIZE = 10
private val ICON_UNSELECTED = ColorIcon(SIZE, Color.LIGHT_GRAY, Color.DARK_GRAY)
private val TEXT_COLOR = Color.BLACK
private val TEXT_COLOR_UNSELECTED = Color.LIGHT_GRAY

/**
 * Checkbox which represents a language in the language selection list.
 */
internal class LanguageCheckbox(
    val language: Language,
    iconColor: Color
) : JCheckBox(language.name) {

    private var colorIcon = ColorIcon(SIZE, iconColor)

    init {
        addItemListener {
            updateIconAndTextColor()
        }
        isSelected = true
    }

    private fun updateIconAndTextColor() {
        if (isSelected) {
            icon = colorIcon
            foreground = TEXT_COLOR
        } else {
            icon = ICON_UNSELECTED
            foreground = TEXT_COLOR_UNSELECTED
        }
    }

    fun setIconColor(iconColor: Color) {
        colorIcon = ColorIcon(SIZE, iconColor)
        updateIconAndTextColor()
    }
}
