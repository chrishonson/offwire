package com.chrishonson.gemmavoice

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

private const val AAA = 7.0
private const val AA = 4.5

class ThemeContrastTest {

    private fun channel(argb: Long, shift: Int): Double {
        val c = ((argb shr shift) and 0xFF).toDouble() / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(argb: Long): Double =
        0.2126 * channel(argb, 16) + 0.7152 * channel(argb, 8) + 0.0722 * channel(argb, 0)

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun assertContrast(fg: Long, bg: Long, minimum: Double) {
        val ratio = contrast(fg, bg)
        assertTrue(ratio >= minimum, "${fg.toString(16)} on ${bg.toString(16)} is $ratio, needs $minimum")
    }

    @Test
    fun contrastFormulaMatchesWcagExtremes() {
        assertTrue(contrast(0xFF000000L, 0xFFFFFFFFL) in 20.99..21.01)
        assertTrue(contrast(Lcars.BLACK, Lcars.BLACK) in 0.99..1.01)
    }

    @Test
    fun lcarsTextReachesAaa() {
        listOf(Lcars.BLACK, Lcars.PANEL, Lcars.RAISED).forEach { bg ->
            assertContrast(Lcars.TEXT, bg, AAA)
            assertContrast(Lcars.TEXT_SECONDARY, bg, AAA)
        }
    }

    // Black labels on the LCARS block colors: every pill in that skin.
    @Test
    fun lcarsBlackOnAccentBlocksReachesAaa() {
        assertContrast(Lcars.BLACK, Lcars.ORANGE, AAA)
        assertContrast(Lcars.BLACK, Lcars.SUNFLOWER, AAA)
        assertContrast(Lcars.BLACK, Lcars.ICE, AAA)
        assertContrast(Lcars.BLACK, Lcars.VIOLET, AAA)
    }

    @Test
    fun lcarsOtherPairingsReachAa() {
        assertContrast(Lcars.BLACK, Lcars.TOMATO, AA)
        assertContrast(Lcars.ORANGE, Lcars.BLACK, AA)
        assertContrast(Lcars.ORANGE, Lcars.PANEL, AA)
    }

    @Test
    fun phaseWordColorsOnBlackReachAa() {
        assertContrast(Lcars.TOMATO, Lcars.BLACK, AA)
        assertContrast(Lcars.ICE, Lcars.BLACK, AA)
        assertContrast(Lcars.SUNFLOWER, Lcars.BLACK, AA)
        assertContrast(Lcars.ORANGE, Lcars.BLACK, AA)
    }
}
