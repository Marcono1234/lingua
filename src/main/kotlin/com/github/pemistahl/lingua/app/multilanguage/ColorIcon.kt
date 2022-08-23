package com.github.pemistahl.lingua.app.multilanguage

import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

/**
 * Icon which is drawn with a single color.
 */
internal class ColorIcon(
    private val size: Int,
    private val color: Color,
    private val borderColor: Color = Color.BLACK,
) : Icon {
    init {
        check(size >= 3)
    }

    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
        g.color = borderColor
        // Create border
        g.fillRect(x, y, size, size)
        // Clear center area in case color has transparency
        g.clearRect(x + 1, y + 1, size - 2, size - 2)

        g.color = color
        g.fillRect(x + 1, y + 1, size - 2, size - 2)
    }

    override fun getIconWidth() = size

    override fun getIconHeight() = size
}
