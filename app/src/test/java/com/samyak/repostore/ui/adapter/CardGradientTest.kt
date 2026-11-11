package com.samyak.repostore.ui.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class CardGradientTest {

    @Test
    fun shadeForPosition_coversAllDistinctShadesAndWrapsCleanly() {
        assertEquals(12, CardGradient.shadeCount)

        val shades = (0 until CardGradient.shadeCount).map { CardGradient.shadeForPosition(it) }
        assertEquals(CardGradient.shadeCount, shades.toSet().size)

        // Wraps cleanly for positive and negative indices
        assertEquals(shades[0], CardGradient.shadeForPosition(CardGradient.shadeCount))
        assertEquals(shades[CardGradient.shadeCount - 1], CardGradient.shadeForPosition(-1))
        assertEquals(shades[0], CardGradient.shadeForPosition(-CardGradient.shadeCount))

        // Adjacent cards never share start or end color
        for (i in 0 until CardGradient.shadeCount) {
            val current = CardGradient.shadeForPosition(i)
            val next = CardGradient.shadeForPosition(i + 1)
            assertNotEquals("Adjacent start colors at $i should differ", current.startColor, next.startColor)
            assertNotEquals("Adjacent end colors at $i should differ", current.endColor, next.endColor)
        }
    }

    @Test
    fun allShades_areOpaqueAndProgressFromRicherBottomLeftToLighterTopRight() {
        for (i in 0 until CardGradient.shadeCount) {
            val spec = CardGradient.shadeForPosition(i)
            assertEquals("startColor must be opaque at $i", 0xFF, (spec.startColor ushr 24) and 0xFF)
            assertEquals("centerColor must be opaque at $i", 0xFF, (spec.centerColor ushr 24) and 0xFF)
            assertEquals("endColor must be opaque at $i", 0xFF, (spec.endColor ushr 24) and 0xFF)

            val startLum = relativeLuminance(spec.startColor)
            val centerLum = relativeLuminance(spec.centerColor)
            val endLum = relativeLuminance(spec.endColor)

            assertTrue("startColor should be darker than centerColor at $i", startLum < centerLum)
            assertTrue("centerColor should be darker than endColor at $i", centerLum < endLum)
        }
    }

    @Test
    fun whiteTextOverScrimmedStartColor_meetsMinimumContrastAcrossAllShades() {
        // Bottom of bg_featured_overlay is #99000000 (60% black) scaled by GRADIENT_OVERLAY_ALPHA.
        val bottomScrimOpacity = 0.6f * CardGradient.GRADIENT_OVERLAY_ALPHA

        for (i in 0 until CardGradient.shadeCount) {
            val spec = CardGradient.shadeForPosition(i)
            val scrimmedStart = darken(spec.startColor, bottomScrimOpacity)
            val contrast = whiteContrastRatio(scrimmedStart)
            assertTrue(
                "Shade $i white contrast ($contrast) should be >= 4.5:1 over bottom-left",
                contrast >= 4.5
            )
        }
    }

    @Test
    fun circleTintForEndColor_returnsOpaquePaleHighlightForAllShadesAndExtremes() {
        for (i in 0 until CardGradient.shadeCount) {
            val spec = CardGradient.shadeForPosition(i)
            val tint = CardGradient.circleTintForEndColor(spec.endColor)
            assertEquals("circleTint must be opaque at $i", 0xFF, (tint ushr 24) and 0xFF)
            assertTrue(
                "circleTint should be brighter than endColor at $i",
                relativeLuminance(tint) > relativeLuminance(spec.endColor)
            )
        }

        // Edge cases: black, white, pure primary
        assertEquals(0xFF, (CardGradient.circleTintForEndColor(0xFF000000.toInt()) ushr 24) and 0xFF)
        assertEquals(0xFF, (CardGradient.circleTintForEndColor(0xFFFFFFFF.toInt()) ushr 24) and 0xFF)
        assertEquals(0xFF, (CardGradient.circleTintForEndColor(0xFF0000FF.toInt()) ushr 24) and 0xFF)
    }

    @Test
    fun forPosition_buildsPaletteWithoutErrorForAnyPosition() {
        for (pos in listOf(-100, -1, 0, 5, 11, 12, 1000)) {
            val palette = CardGradient.forPosition(pos)
            assertNotNull(palette.gradient)
            assertEquals(0xFF, (palette.circleTint ushr 24) and 0xFF)
        }
    }

    private fun darken(argb: Int, blackOpacity: Float): Int {
        val factor = 1f - blackOpacity
        val r = (((argb ushr 16) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val g = (((argb ushr 8) and 0xFF) * factor).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun relativeLuminance(argb: Int): Double {
        fun linearize(channel: Int): Double {
            val srgb = channel / 255.0
            return if (srgb <= 0.03928) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
        }
        val r = linearize((argb ushr 16) and 0xFF)
        val g = linearize((argb ushr 8) and 0xFF)
        val b = linearize(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun whiteContrastRatio(argb: Int): Double {
        return 1.05 / (relativeLuminance(argb) + 0.05)
    }
}

