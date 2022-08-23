package com.github.pemistahl.lingua.app.multilanguage

import java.awt.Color
import java.awt.Graphics
import java.awt.Shape
import javax.swing.text.Highlighter
import javax.swing.text.JTextComponent
import kotlin.math.max
import kotlin.math.min

private fun Graphics.drawPixel(x: Int, y: Int) {
    drawLine(x, y, x, y)
}

/**
 * Highlight painter which draws a border around highlights and also improves indication for wrapped
 * highlighted sections.
 */
// Note: If this code becomes too difficult to maintain, could switch back to
// javax.swing.text.DefaultHighlighter.DefaultHighlightPainter
internal class BorderedHighlightPainter(private val color: Color) : Highlighter.HighlightPainter {
    private val borderColor = color.multiplyHSBBrightness(0.5f).withAlpha(min(255, color.alpha * 2))

    // Note: Seems to cause issues for multiline highlights; apparently draws outside of area expected by default
    // highlighter; currently MultiLanguageTextArea works around this by calling `repaint()`
    override fun paint(g: Graphics, p0: Int, p1: Int, bounds: Shape, c: JTextComponent) {
        // Don't draw highlights for empty sections
        if (p0 >= p1) {
            return
        }

        /** Minimum x-coordinate; inclusive */
        val minX = bounds.bounds.minX.toInt()

        /** Maximum x-coordinate; inclusive */
        val maxX = bounds.bounds.maxX.toInt() - 1

        val lineHeight: Int
        val firstLineStartX: Int
        val firstLineStartY: Int
        val firstLineEndY: Int
        run {
            val sectionStartRect = c.modelToView2D(p0)
            firstLineStartX = sectionStartRect.minX.toInt()
            firstLineStartY = sectionStartRect.minY.toInt()
            // - 1 because otherwise seems to extend into drawing area of next line
            firstLineEndY = sectionStartRect.maxY.toInt() - 1

            lineHeight = firstLineEndY - firstLineStartY + 1 // + 1 because both positions are inclusive
        }

        val lastLineEndX: Int
        val lastLineStartY: Int
        val lastLineEndY: Int
        run {
            val lastCharRect = c.modelToView2D(p1 - 1)
            // Position behind the last char
            val nextCharRect = c.modelToView2D(p1)

            if (lastCharRect.minY == nextCharRect.minY) {
                lastLineEndX = nextCharRect.minX.toInt()
                lastLineStartY = nextCharRect.minY.toInt()
                // - 1 because otherwise seems to extend into drawing area of next line
                lastLineEndY = nextCharRect.maxY.toInt() - 1
            }
            // Next char position wrapped into next line; don't use it
            else {
                lastLineEndX = maxX
                lastLineStartY = lastCharRect.minY.toInt()
                // - 1 because otherwise seems to extend into drawing area of next line
                lastLineEndY = lastCharRect.maxY.toInt() - 1
            }
        }

        g.color = color

        if (firstLineStartY == lastLineStartY) {
            val width = lastLineEndX - firstLineStartX
            g.fillRect(firstLineStartX, firstLineStartY, width, lineHeight)

            // Draw border
            g.color = borderColor
            // - 1 because width and height are inclusive
            g.drawRect(firstLineStartX, firstLineStartY, width - 1, lineHeight - 1)
        }
        // Draw multiline
        else {
            // Calculate values for 'block' between first and last line which spans from beginning to end of lines
            val blockStartY = firstLineEndY + 1
            val blockWidth = maxX - minX + 1 // + 1 because both position values are inclusive
            val blockHeight = lastLineStartY - blockStartY

            /**
             * Whether to omit the border at the end of the first and the start of the last line to indicate
             * that the section is wrapped.
             */
            val showHorizontalContinuation = blockHeight == 0 && lastLineEndX - firstLineStartX < 30 &&
                // With the pixel offset for `lastLineEndX - firstLineStartX`, ignore if continuation would be
                // directly below first line / above second line
                !(firstLineStartX == minX || lastLineEndX == maxX)
            val continuationFillOffset = 1

            var firstLineWidth = maxX - firstLineStartX + 1 // + 1 because both position values are inclusive
            if (showHorizontalContinuation) {
                // Show some empty space at the end to indicate continuation
                firstLineWidth -= continuationFillOffset
            }

            g.fillRect(firstLineStartX, firstLineStartY, firstLineWidth, lineHeight)
            if (blockHeight > 0) {
                g.fillRect(minX, blockStartY, blockWidth, blockHeight)
            }

            var lastLineWidth = lastLineEndX - minX + 1 // + 1 because both position values are inclusive
            var lastLineFillStartX = minX
            if (showHorizontalContinuation) {
                // Show some empty space at the end to indicate continuation
                lastLineFillStartX += continuationFillOffset
                lastLineWidth -= continuationFillOffset
            }
            g.fillRect(lastLineFillStartX, lastLineStartY, lastLineWidth, lineHeight)

            // Draw border
            g.color = borderColor
            g.drawLine(firstLineStartX, firstLineStartY, maxX, firstLineStartY)
            // + 1 and - 1 to avoid painting twice at corners
            g.drawLine(firstLineStartX, firstLineStartY + 1, firstLineStartX, firstLineEndY - 1)

            if (blockHeight > 0) {
                val blockEndY = blockStartY + blockHeight - 1 // - 1 because position is inclusive
                // + 1 to avoid painting twice at corners
                g.drawLine(minX, blockStartY + 1, minX, blockEndY)
                // - 1 to avoid painting twice at corners
                g.drawLine(maxX, blockStartY, maxX, blockEndY - 1)

                // Draw border above and below block
                g.drawLine(minX, blockStartY, firstLineStartX, blockStartY)
                g.drawLine(lastLineEndX, blockEndY, maxX, blockEndY)

                // Need to fill pixels in gaps which were omitted to avoid painting corners twice
                g.drawPixel(firstLineStartX, firstLineEndY)
                g.drawPixel(maxX, firstLineEndY)
                g.drawPixel(minX, lastLineStartY)
                g.drawPixel(lastLineEndX, lastLineStartY)
            } else {
                val bottomLineStartX = max(firstLineStartX, lastLineEndX)
                if (bottomLineStartX < maxX) {
                    g.drawLine(bottomLineStartX, firstLineEndY, maxX, firstLineEndY)
                } else {
                    // Need to fill pixel in gap which was omitted to avoid painting corners twice
                    g.drawPixel(maxX, firstLineEndY)
                }

                val topLineEndX = min(firstLineStartX, lastLineEndX)
                if (topLineEndX > minX) {
                    g.drawLine(minX, lastLineStartY, topLineEndX, lastLineStartY)
                } else {
                    // Need to fill pixel in gap which was omitted to avoid painting corners twice
                    g.drawPixel(minX, lastLineStartY)
                }

                val areConnected = lastLineEndX - firstLineStartX > 1 // 1 because border is 1 pixel wide
                if (areConnected) {
                    // Need to fill pixels in gaps which were omitted to avoid painting corners twice
                    g.drawPixel(firstLineStartX, firstLineEndY)
                    g.drawPixel(lastLineEndX, lastLineStartY)
                }
            }

            // + 1 and - 1 to avoid painting twice at corners
            g.drawLine(lastLineEndX, lastLineStartY + 1, lastLineEndX, lastLineEndY - 1)
            g.drawLine(minX, lastLineEndY, lastLineEndX, lastLineEndY)

            if (!showHorizontalContinuation) {
                // + 1 and - 1 to avoid painting twice at corners
                g.drawLine(maxX, firstLineStartY + 1, maxX, firstLineEndY - 1)
                g.drawLine(minX, lastLineStartY + 1, minX, lastLineEndY - 1)
            }
        }
    }
}
