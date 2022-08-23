package com.github.pemistahl.lingua.app.multilanguage

import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.JCheckBox
import javax.swing.UIManager

/**
 * A checkbox with three states:
 * - not selected
 * - some selected
 * - selected
 *
 * The selection state is represented by the [selectionState] property.
 */
// Based on https://stackoverflow.com/a/26749506
internal class TriStateCheckbox(text: String) : JCheckBox(text) {
    enum class SelectionState {
        NOT_SELECTED,
        SOME_SELECTED,
        SELECTED
    }

    var selectionState: SelectionState = SelectionState.NOT_SELECTED
        set(selectionState) {
            if (field != selectionState) {
                field = selectionState

                isSelected = selectionState == SelectionState.SELECTED

                repaint()
            }
        }

    private var listeners: MutableList<(isSelected: Boolean) -> Unit> = mutableListOf()

    init {
        val delegateIcon = UIManager.getIcon("CheckBox.icon")

        icon = object : Icon {
            override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
                delegateIcon.paintIcon(c, g, x, y)
                if (this@TriStateCheckbox.selectionState != SelectionState.SOME_SELECTED) {
                    return
                }

                g.color = if (c.isEnabled) Color(51, 51, 51) else Color(122, 138, 153)

                val w = iconWidth
                val xInset = w / 3
                val h = iconHeight
                val yInset = h / 3
                g.fillRect(x + xInset, y + yInset, w - 2 * xInset, h - 2 * yInset)
            }

            override fun getIconWidth() = delegateIcon.iconWidth

            override fun getIconHeight() = delegateIcon.iconHeight
        }

        addActionListener {
            selectionState = when (selectionState) {
                // Toggle between selected / not-selected; cannot reach 'some selected' here
                SelectionState.SELECTED -> SelectionState.NOT_SELECTED
                SelectionState.NOT_SELECTED -> SelectionState.SELECTED
                SelectionState.SOME_SELECTED -> SelectionState.SELECTED
            }
            listeners.forEach { it(selectionState == SelectionState.SELECTED) }
        }

        // Update selection state; must not match initial selectionState value to cause component to update
        selectionState = (SelectionState.SELECTED)
    }

    fun addListener(listener: (isSelected: Boolean) -> Unit) {
        listeners.add(listener)
    }
}
