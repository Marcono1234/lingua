package com.github.pemistahl.lingua.app.multilanguage

import java.awt.Color

internal fun Color.withAlpha(alpha: Int): Color {
    return Color(red, green, blue, alpha)
}

internal fun Color.multiplyHSBBrightness(factor: Float): Color {
    val hsb = Color.RGBtoHSB(red, green, blue, null)
    hsb[2] *= factor
    val color = Color.getHSBColor(hsb[0], hsb[1], hsb[2])
    // Restore original alpha
    return color.withAlpha(alpha)
}
