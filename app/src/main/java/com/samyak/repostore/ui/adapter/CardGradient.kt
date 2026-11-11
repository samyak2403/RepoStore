package com.samyak.repostore.ui.adapter

import android.graphics.drawable.GradientDrawable
import kotlin.math.abs
import kotlin.math.roundToInt

/** The gradient and matching circle tint for one featured/trending card. */
data class CardPalette(
    val gradient: GradientDrawable,
    val circleTint: Int
)

/**
 * Three-stop diagonal shade specification (`BL` -> center -> `TR`) for one
 * card palette. Exposed for pure-JVM verification alongside [CardGradient].
 */
data class ShadeSpec(
    val startColor: Int,
    val centerColor: Int,
    val endColor: Int
)

/**
 * Builds the luminous multi-shade colour scheme shown behind a featured or
 * trending card when the repository has no banner image.
 *
 * Each card uses a 3-stop bottom-left to top-right (`BL_TR`) gradient that
 * starts from a rich, saturated base behind the app icon and title and
 * transitions smoothly into a lighter, analogous hue toward the top-right.
 * Including an explicit midpoint stop prevents sRGB interpolation from losing
 * saturation on cross-hue transitions (such as emerald-to-mint or teal-to-aqua).
 *
 * Shades are ordered across the full colour wheel so adjacent cards in a
 * scrolling list stay visually varied before the cycle repeats.
 */
object CardGradient {

    /**
     * Light scrim alpha applied to the card overlay when showing a gradient
     * fallback (instead of a repository banner screenshot). Keeps white metadata
     * text legible on brighter shades without muddying the luminous gradient.
     */
    const val GRADIENT_OVERLAY_ALPHA = 0.24f

    /** Lightness of the concentric decorative circles on the right edge. */
    private const val CIRCLE_LIGHTNESS = 0.91f

    /** Maximum saturation for the decorative circle wash. */
    private const val CIRCLE_MAX_SATURATION = 0.75f

    /** Scale applied to the end-stop saturation when tinting the circles. */
    private const val CIRCLE_SATURATION_SCALE = 0.65f

    private val SHADES = listOf(
        // Rose -> Peach
        ShadeSpec(0xFFFD286B.toInt(), 0xFFFC637B.toInt(), 0xFFFDAD8E.toInt()),
        // Ocean Blue -> Sky
        ShadeSpec(0xFF1562F5.toInt(), 0xFF3296FC.toInt(), 0xFF74D6FF.toInt()),
        // Emerald -> Mint
        ShadeSpec(0xFF088F53.toInt(), 0xFF1FC276.toInt(), 0xFF76EEAA.toInt()),
        // Violet -> Orchid
        ShadeSpec(0xFF7B22CC.toInt(), 0xFFAB55F2.toInt(), 0xFFE299FF.toInt()),
        // Coral -> Warm Gold
        ShadeSpec(0xFFEE3E26.toInt(), 0xFFFA7842.toInt(), 0xFFFFC46B.toInt()),
        // Teal -> Aqua
        ShadeSpec(0xFF007F8C.toInt(), 0xFF12B8BC.toInt(), 0xFF72F2E8.toInt()),
        // Indigo -> Periwinkle
        ShadeSpec(0xFF4136C7.toInt(), 0xFF6970FA.toInt(), 0xFFA5B4FC.toInt()),
        // Fuchsia -> Blush
        ShadeSpec(0xFFD61877.toInt(), 0xFFF053A3.toInt(), 0xFFFF9DD1.toInt()),
        // Amber -> Sunburst
        ShadeSpec(0xFFD45A02.toInt(), 0xFFF08E1E.toInt(), 0xFFFFD563.toInt()),
        // Forest -> Fresh Lime
        ShadeSpec(0xFF1F871E.toInt(), 0xFF48C23B.toInt(), 0xFFA3EF71.toInt()),
        // Ruby -> Apricot
        ShadeSpec(0xFFD91B36.toInt(), 0xFFF24D4D.toInt(), 0xFFFF9673.toInt()),
        // Plum -> Lavender
        ShadeSpec(0xFF9B168B.toInt(), 0xFFD645AC.toInt(), 0xFFF48FD8.toInt())
    )

    val shadeCount: Int get() = SHADES.size

    fun shadeForPosition(position: Int): ShadeSpec =
        SHADES[Math.floorMod(position, SHADES.size)]

    /**
     * Derives a pale wash from the lighter (right-side) gradient hue so the
     * right-edge decorative circles blend softly with the card's glow.
     */
    fun circleTintForEndColor(endColor: Int): Int {
        val r = ((endColor ushr 16) and 0xFF) / 255f
        val g = ((endColor ushr 8) and 0xFF) / 255f
        val b = (endColor and 0xFF) / 255f

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        val lightness = (max + min) / 2f
        val saturation = if (delta == 0f) {
            0f
        } else {
            delta / (1f - abs(2f * lightness - 1f))
        }

        val targetSat = (saturation * CIRCLE_SATURATION_SCALE).coerceAtMost(CIRCLE_MAX_SATURATION)
        return hslToArgb(hue, targetSat, CIRCLE_LIGHTNESS)
    }

    private fun hslToArgb(hue: Float, saturation: Float, lightness: Float): Int {
        val c = (1f - abs(2f * lightness - 1f)) * saturation
        val x = c * (1f - abs((hue / 60f) % 2f - 1f))
        val m = lightness - c / 2f

        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun forPosition(position: Int): CardPalette {
        val spec = shadeForPosition(position)
        val circleTint = circleTintForEndColor(spec.endColor)

        val gradient = GradientDrawable(
            GradientDrawable.Orientation.BL_TR,
            intArrayOf(spec.startColor, spec.centerColor, spec.endColor)
        ).apply { cornerRadius = 0f }

        return CardPalette(gradient, circleTint)
    }
}
