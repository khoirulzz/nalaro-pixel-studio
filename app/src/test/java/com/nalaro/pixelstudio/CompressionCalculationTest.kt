package com.nalaro.pixelstudio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.max

class CompressionCalculationTest {

    private fun calculateSavingsPct(inputBytes: Long, outputBytes: Long): Float {
        return if (inputBytes > 0) {
            max(0f, (1f - (outputBytes.toFloat() / inputBytes.toFloat())) * 100f)
        } else {
            0f
        }
    }

    @Test
    fun testByteSavingsFormulaTruthfulness() {
        val input = 1_000_000L
        val outputSaved = 250_000L

        // 75% savings
        val savings = calculateSavingsPct(input, outputSaved)
        assertEquals(75.0f, savings, 0.01f)

        // When output is larger than input, savings must be 0% (never negative fake savings)
        val outputLarger = 1_200_000L
        val noSavings = calculateSavingsPct(input, outputLarger)
        assertEquals(0.0f, noSavings, 0.01f)
    }
}
