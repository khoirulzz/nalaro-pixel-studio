package com.nalaro.pixelstudio.core.compression

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

object SSIMCalculator {

    private const val C1 = 6.5025 // (0.01 * 255)^2
    private const val C2 = 58.5225 // (0.03 * 255)^2

    fun calculateSSIM(original: Bitmap, candidate: Bitmap): Double {
        if (original.width != candidate.width || original.height != candidate.height) {
            return 0.0
        }

        // Sampling for fast calculation on mobile devices
        val sampleWidth = min(original.width, 256)
        val sampleHeight = min(original.height, 256)

        val bmp1 = if (original.width == sampleWidth) original else Bitmap.createScaledBitmap(original, sampleWidth, sampleHeight, true)
        val bmp2 = if (candidate.width == sampleWidth) candidate else Bitmap.createScaledBitmap(candidate, sampleWidth, sampleHeight, true)

        val n = sampleWidth * sampleHeight
        val p1 = IntArray(n)
        val p2 = IntArray(n)

        bmp1.getPixels(p1, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
        bmp2.getPixels(p2, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)

        var mean1 = 0.0
        var mean2 = 0.0

        for (i in 0 until n) {
            val lum1 = 0.299 * Color.red(p1[i]) + 0.587 * Color.green(p1[i]) + 0.114 * Color.blue(p1[i])
            val lum2 = 0.299 * Color.red(p2[i]) + 0.587 * Color.green(p2[i]) + 0.114 * Color.blue(p2[i])
            mean1 += lum1
            mean2 += lum2
        }

        mean1 /= n
        mean2 /= n

        var var1 = 0.0
        var var2 = 0.0
        var covar = 0.0

        for (i in 0 until n) {
            val lum1 = 0.299 * Color.red(p1[i]) + 0.587 * Color.green(p1[i]) + 0.114 * Color.blue(p1[i])
            val lum2 = 0.299 * Color.red(p2[i]) + 0.587 * Color.green(p2[i]) + 0.114 * Color.blue(p2[i])

            val diff1 = lum1 - mean1
            val diff2 = lum2 - mean2

            var1 += diff1 * diff1
            var2 += diff2 * diff2
            covar += diff1 * diff2
        }

        var1 /= (n - 1)
        var2 /= (n - 1)
        covar /= (n - 1)

        val numerator = (2 * mean1 * mean2 + C1) * (2 * covar + C2)
        val denominator = (mean1 * mean1 + mean2 * mean2 + C1) * (var1 + var2 + C2)

        return (numerator / denominator).coerceIn(0.0, 1.0)
    }
}
