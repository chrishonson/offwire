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
        assertTrue(contrast(Hardware.PANEL, Hardware.PANEL) in 0.99..1.01)
    }

    @Test
    fun lightBodyTextReachesAaa() {
        listOf(Hardware.PUTTY, Hardware.PUTTY_RECESS, Hardware.PUTTY_DEEP, Hardware.LCD).forEach { bg ->
            assertContrast(Hardware.INK, bg, AAA)
        }
        listOf(Hardware.PUTTY, Hardware.PUTTY_RECESS, Hardware.PUTTY_DEEP).forEach { bg ->
            assertContrast(Hardware.INK_DIM, bg, AAA)
        }
        assertContrast(Hardware.INK, Hardware.STEEL_LIGHT_MID, AAA)
        assertContrast(Hardware.LCD, Hardware.INK, AAA)
    }

    @Test
    fun darkBodyTextReachesAaa() {
        listOf(Hardware.PANEL, Hardware.PANEL_RAISED, Hardware.PANEL_HIGH).forEach { bg ->
            assertContrast(Hardware.PUTTY, bg, AAA)
            assertContrast(Hardware.PUTTY_DIM, bg, AAA)
        }
        assertContrast(Hardware.PUTTY, Hardware.STEEL_DARK_MID, AAA)
        assertContrast(Hardware.VFD_CYAN, Hardware.VFD_GLASS, AAA)
        assertContrast(Hardware.VFD_GLASS, Hardware.VFD_CYAN, AAA)
    }

    @Test
    fun switchesAndLedsCarryInkText() {
        assertContrast(Hardware.INK, Hardware.SWITCH_AMBER, AAA)
        assertContrast(Hardware.INK, Hardware.LED_AMBER, AAA)
        assertContrast(Hardware.INK, Hardware.LED_GREEN, AAA)
        assertContrast(Hardware.INK, Hardware.VFD_CYAN, AAA)
        assertContrast(Hardware.INK, Hardware.LED_RED, AA)
    }

    @Test
    fun accentTextReachesAa() {
        listOf(Hardware.PUTTY, Hardware.PUTTY_RECESS).forEach { bg ->
            assertContrast(Hardware.RED_INK, bg, AA)
            assertContrast(Hardware.AMBER_INK, bg, AA)
        }
        assertContrast(Hardware.PUTTY, Hardware.AMBER_INK, AA)
        listOf(Hardware.PANEL, Hardware.PANEL_RAISED).forEach { bg ->
            assertContrast(Hardware.LED_RED, bg, AA)
            assertContrast(Hardware.LED_AMBER, bg, AA)
            assertContrast(Hardware.SWITCH_AMBER, bg, AA)
        }
        assertContrast(Hardware.LCD_INK_DIM, Hardware.LCD, AA)
        assertContrast(Hardware.VFD_DIM, Hardware.VFD_GLASS, AA)
        assertContrast(Hardware.VFD_DIM, Hardware.PANEL, AA)
    }
}
