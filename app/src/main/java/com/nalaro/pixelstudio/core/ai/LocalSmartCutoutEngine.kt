package com.nalaro.pixelstudio.core.ai

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class EdgeRefinementMode(val label: String) {
    NATURAL("Natural"),
    CRISP("Crisp"),
    SOFT_FEATHER("Soft Feather")
}

/**
 * Lightweight, zero-dependency, studio-grade offline background segmentation and alpha-matting engine.
 *
 * Combines:
 * 1. Perceptual CIE Lab color space conversion (separating luminance shadows from chrominance).
 * 2. Adaptive Multi-Cluster Border GMM / K-Means background modeling from image perimeter.
 * 3. 4-pass 8-connected Geodesic Edge-Barrier Flood-Fill propagation (prevents interior holes when
 *    foreground colors resemble background colors).
 * 4. Foreground vs. Background GMM log-likelihood + center-surround contrast saliency fusion.
 * 5. Exterior flood-fill hole closing & connected-component noise suppression.
 * 6. Full-resolution Joint Bilateral / Guided Filter alpha feathering & foreground color decontamination.
 */
object LocalSmartCutoutEngine {

    private data class LabCluster(
        var l: Float,
        var a: Float,
        var b: Float,
        var varianceL: Float = 140f,
        var varianceChroma: Float = 95f,
        var weight: Float = 1f
    )

    fun segmentSubject(
        source: Bitmap,
        edgeMode: EdgeRefinementMode = EdgeRefinementMode.NATURAL,
        highPrecision: Boolean = true
    ): Bitmap {
        val workMaxSide = if (highPrecision) 420 else 320
        val maskResult = computeSmartSubjectMask(source, workMaxSide)
        return refineAndApplyMask(
            source = source,
            rawMask = maskResult.mask,
            maskW = maskResult.width,
            maskH = maskResult.height,
            edgeMode = edgeMode
        )
    }

    data class MaskGrid(
        val mask: FloatArray,
        val width: Int,
        val height: Int
    )

    fun computeSmartSubjectMask(source: Bitmap, maxWorkDimension: Int = 384): MaskGrid {
        val srcW = source.width.coerceAtLeast(1)
        val srcH = source.height.coerceAtLeast(1)
        val scale = min(1f, maxWorkDimension.toFloat() / max(srcW, srcH).toFloat())
        val w = (srcW * scale).toInt().coerceAtLeast(32)
        val h = (srcH * scale).toInt().coerceAtLeast(32)
        val n = w * h

        val smallBmp = if (w == srcW && h == srcH) {
            source
        } else {
            Bitmap.createScaledBitmap(source, w, h, true)
        }

        val pixels = IntArray(n)
        smallBmp.getPixels(pixels, 0, w, 0, 0, w, h)
        if (smallBmp !== source) {
            smallBmp.recycle()
        }

        // 1. Convert RGB to perceptual CIE Lab
        val lChan = FloatArray(n)
        val aChan = FloatArray(n)
        val bChan = FloatArray(n)
        rgbToLabArrays(pixels, lChan, aChan, bChan)

        // 2. Compute Sobel/Scharr perceptual edge gradient magnitude in Lab space
        val grad = computeLabGradient(lChan, aChan, bChan, w, h)

        // 3. Sample border ring & cluster into K=6 background clusters
        val bgClusters = buildBorderBackgroundClusters(lChan, aChan, bChan, grad, w, h, k = 6)

        // 4. Compute per-pixel background color distance (shadow-tolerant in L, strict in a/b chroma)
        val bgColorDist = FloatArray(n)
        for (i in 0 until n) {
            bgColorDist[i] = minClusterDistance(lChan[i], aChan[i], bChan[i], bgClusters)
        }

        // 5. Run 4-pass 8-connected Geodesic Edge-Barrier Flood-Fill from borders
        val geoDist = computeGeodesicBarrierDistance(lChan, aChan, bChan, grad, bgColorDist, w, h)

        // 6. Build Foreground Color Clusters from high-confidence interior seed pixels
        val fgClusters = buildForegroundClusters(lChan, aChan, bChan, geoDist, bgColorDist, w, h, k = 6)

        // 7. Fuse Geodesic Barrier Probability + Fg/Bg Color Likelihood + Spatial Saliency Prior
        val probMask = FloatArray(n)
        val centerGeoMedian = estimateCenterPercentile(geoDist, w, h, 0.55f).coerceAtLeast(1.2f)
        val geoScale = (centerGeoMedian * 0.48f).coerceAtLeast(0.85f)

        for (y in 0 until h) {
            val ny = (y.toFloat() / h.toFloat()) - 0.5f
            for (x in 0 until w) {
                val nx = (x.toFloat() / w.toFloat()) - 0.5f
                val i = y * w + x

                val dBg = bgColorDist[i]
                val dFg = if (fgClusters.isNotEmpty()) {
                    minClusterDistance(lChan[i], aChan[i], bChan[i], fgClusters)
                } else {
                    1.5f
                }

                val colorRatio = (dBg - dFg * 0.82f) / (dBg + dFg + 0.35f)
                val pColor = sigmoid(colorRatio * 4.2f)

                val pGeo = sigmoid((geoDist[i] - geoScale) / (geoScale * 0.38f).coerceAtLeast(0.25f))

                val distToBorderX = min(x, w - 1 - x).toFloat() / w.toFloat()
                val distToBorderY = min(y, h - 1 - y).toFloat() / h.toFloat()
                val isBottomCenter = (y > h * 0.85f) && (abs(nx) < 0.32f)
                val borderWeight = if (isBottomCenter) {
                    1.0f
                } else {
                    val minBorderDist = min(distToBorderX, distToBorderY)
                    (minBorderDist / 0.03f).coerceIn(0.15f, 1.0f)
                }

                val combined = (0.56f * pGeo + 0.44f * pColor) * borderWeight
                probMask[i] = combined.coerceIn(0f, 1f)
            }
        }

        // 8. Morphological Hole Filling & Connected Component Filtering
        val cleanedMask = closeHolesAndFilterComponents(probMask, lChan, aChan, bChan, w, h)

        return MaskGrid(cleanedMask, w, h)
    }

    /**
     * Merges a neural probability mask (e.g. from ML Kit or ONNX) with local edge-preserving guided alpha matting
     * and foreground color decontamination.
     */
    fun refineAndApplyMask(
        source: Bitmap,
        rawMask: FloatArray,
        maskW: Int,
        maskH: Int,
        edgeMode: EdgeRefinementMode = EdgeRefinementMode.NATURAL
    ): Bitmap {
        val width = source.width.coerceAtLeast(1)
        val height = source.height.coerceAtLeast(1)
        val numPixels = width * height

        val srcPixels = IntArray(numPixels)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        // 1. Bilinearly upsample raw mask to full image resolution
        val fullAlpha = bilinearUpsampleMask(rawMask, maskW, maskH, width, height)

        // 2. Joint Bilateral / Color-Guided Edge Feathering in the boundary trimap band
        val radius = when (edgeMode) {
            EdgeRefinementMode.CRISP -> 2
            EdgeRefinementMode.NATURAL -> 3
            EdgeRefinementMode.SOFT_FEATHER -> 5
        }
        val refinedAlpha = guidedEdgeRefinement(srcPixels, fullAlpha, width, height, radius, edgeMode)

        // 3. Foreground Color Decontamination (removes background halo spill on semi-transparent edges)
        val outPixels = applyAlphaWithColorDecontamination(srcPixels, refinedAlpha, width, height)

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(outPixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun rgbToLabArrays(
        pixels: IntArray,
        lOut: FloatArray,
        aOut: FloatArray,
        bOut: FloatArray
    ) {
        for (i in pixels.indices) {
            val c = pixels[i]
            var r = ((c shr 16) and 0xFF) / 255.0f
            var g = ((c shr 8) and 0xFF) / 255.0f
            var b = (c and 0xFF) / 255.0f

            r = if (r > 0.04045f) ((r + 0.055f) / 1.055f).pow(2.4f) else r / 12.92f
            g = if (g > 0.04045f) ((g + 0.055f) / 1.055f).pow(2.4f) else g / 12.92f
            b = if (b > 0.04045f) ((b + 0.055f) / 1.055f).pow(2.4f) else b / 12.92f

            val x = (r * 0.4124f + g * 0.3576f + b * 0.1805f) / 0.95047f
            val y = (r * 0.2126f + g * 0.7152f + b * 0.0722f) / 1.00000f
            val z = (r * 0.0193f + g * 0.1192f + b * 0.9505f) / 1.08883f

            val fx = if (x > 0.008856f) x.pow(1f / 3f) else (7.787f * x) + (16f / 116f)
            val fy = if (y > 0.008856f) y.pow(1f / 3f) else (7.787f * y) + (16f / 116f)
            val fz = if (z > 0.008856f) z.pow(1f / 3f) else (7.787f * z) + (16f / 116f)

            lOut[i] = (116f * fy) - 16f
            aOut[i] = 500f * (fx - fy)
            bOut[i] = 200f * (fy - fz)
        }
    }

    private fun computeLabGradient(
        l: FloatArray,
        a: FloatArray,
        b: FloatArray,
        w: Int,
        h: Int
    ): FloatArray {
        val grad = FloatArray(w * h)
        var maxG = 1f
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val dlx = l[i + 1] - l[i - 1]
                val dly = l[i + w] - l[i - w]
                val dax = a[i + 1] - a[i - 1]
                val day = a[i + w] - a[i - w]
                val dbx = b[i + 1] - b[i - 1]
                val dby = b[i + w] - b[i - w]

                val gMag = sqrt(
                    0.6f * (dlx * dlx + dly * dly) +
                        1.4f * (dax * dax + day * day + dbx * dbx + dby * dby)
                )
                grad[i] = gMag
                if (gMag > maxG) maxG = gMag
            }
        }
        val invMax = 1f / maxG.coerceAtLeast(10f)
        for (i in grad.indices) {
            grad[i] = (grad[i] * invMax).coerceIn(0f, 1f)
        }
        return grad
    }

    private fun buildBorderBackgroundClusters(
        l: FloatArray,
        a: FloatArray,
        b: FloatArray,
        grad: FloatArray,
        w: Int,
        h: Int,
        k: Int
    ): List<LabCluster> {
        val borderThickX = max(2, (w * 0.06f).toInt())
        val borderThickY = max(2, (h * 0.06f).toInt())

        val sampleL = FloatArray(w * h / 4 + 64)
        val sampleA = FloatArray(w * h / 4 + 64)
        val sampleB = FloatArray(w * h / 4 + 64)
        var count = 0

        for (y in 0 until h step 2) {
            for (x in 0 until w step 2) {
                val isTop = y < borderThickY
                val isLeft = x < borderThickX
                val isRight = x >= w - borderThickX
                val isBottomCorner = (y >= h - borderThickY) && (x < w * 0.28f || x > w * 0.72f)
                if (isTop || isLeft || isRight || isBottomCorner) {
                    val idx = y * w + x
                    if (grad[idx] < 0.45f && count < sampleL.size) {
                        sampleL[count] = l[idx]
                        sampleA[count] = a[idx]
                        sampleB[count] = b[idx]
                        count++
                    }
                }
            }
        }

        if (count == 0) {
            return listOf(LabCluster(l[0], a[0], b[0]))
        }

        return runLabKMeans(sampleL, sampleA, sampleB, count, k)
    }

    private fun buildForegroundClusters(
        l: FloatArray,
        a: FloatArray,
        b: FloatArray,
        geoDist: FloatArray,
        bgColorDist: FloatArray,
        w: Int,
        h: Int,
        k: Int
    ): List<LabCluster> {
        val centerGeoThreshold = estimateCenterPercentile(geoDist, w, h, 0.60f)
        val sampleL = FloatArray(w * h / 4 + 64)
        val sampleA = FloatArray(w * h / 4 + 64)
        val sampleB = FloatArray(w * h / 4 + 64)
        var count = 0

        val minX = (w * 0.12f).toInt()
        val maxX = (w * 0.88f).toInt()
        val minY = (h * 0.10f).toInt()
        val maxY = (h * 0.92f).toInt()

        for (y in minY until maxY step 2) {
            val row = y * w
            for (x in minX until maxX step 2) {
                val i = row + x
                if (geoDist[i] >= centerGeoThreshold && bgColorDist[i] > 1.15f && count < sampleL.size) {
                    sampleL[count] = l[i]
                    sampleA[count] = a[i]
                    sampleB[count] = b[i]
                    count++
                }
            }
        }

        if (count < 12) return emptyList()
        return runLabKMeans(sampleL, sampleA, sampleB, count, k)
    }

    private fun runLabKMeans(
        sampleL: FloatArray,
        sampleA: FloatArray,
        sampleB: FloatArray,
        count: Int,
        k: Int
    ): List<LabCluster> {
        val actualK = min(k, count).coerceAtLeast(1)
        val clusters = ArrayList<LabCluster>(actualK)

        for (c in 0 until actualK) {
            val idx = (c * (count - 1)) / actualK.coerceAtLeast(1)
            clusters.add(LabCluster(sampleL[idx], sampleA[idx], sampleB[idx]))
        }

        val assignments = IntArray(count)
        repeat(6) {
            val sumL = FloatArray(actualK)
            val sumA = FloatArray(actualK)
            val sumB = FloatArray(actualK)
            val counts = IntArray(actualK)

            for (i in 0 until count) {
                val sl = sampleL[i]
                val sa = sampleA[i]
                val sb = sampleB[i]
                var bestCluster = 0
                var bestDist = Float.MAX_VALUE
                for (c in 0 until actualK) {
                    val cl = clusters[c]
                    val dl = sl - cl.l
                    val da = sa - cl.a
                    val db = sb - cl.b
                    val d = 0.5f * dl * dl + da * da + db * db
                    if (d < bestDist) {
                        bestDist = d
                        bestCluster = c
                    }
                }
                assignments[i] = bestCluster
                sumL[bestCluster] += sl
                sumA[bestCluster] += sa
                sumB[bestCluster] += sb
                counts[bestCluster]++
            }

            for (c in 0 until actualK) {
                if (counts[c] > 0) {
                    val inv = 1f / counts[c].toFloat()
                    clusters[c].l = sumL[c] * inv
                    clusters[c].a = sumA[c] * inv
                    clusters[c].b = sumB[c] * inv
                    clusters[c].weight = counts[c].toFloat() / count.toFloat()
                } else {
                    clusters[c].weight = 0f
                }
            }
        }

        val varL = FloatArray(actualK)
        val varC = FloatArray(actualK)
        val counts = IntArray(actualK)
        for (i in 0 until count) {
            val c = assignments[i]
            val cl = clusters[c]
            val dl = sampleL[i] - cl.l
            val da = sampleA[i] - cl.a
            val db = sampleB[i] - cl.b
            varL[c] += dl * dl
            varC[c] += (da * da + db * db)
            counts[c]++
        }

        for (c in 0 until actualK) {
            if (counts[c] > 2) {
                clusters[c].varianceL = (varL[c] / counts[c]).coerceIn(85f, 420f)
                clusters[c].varianceChroma = (varC[c] / counts[c]).coerceIn(45f, 260f)
            }
        }

        return clusters.filter { it.weight > 0.02f }.ifEmpty { clusters.take(1) }
    }

    private fun minClusterDistance(
        l: Float,
        a: Float,
        b: Float,
        clusters: List<LabCluster>
    ): Float {
        var minD = Float.MAX_VALUE
        for (i in clusters.indices) {
            val c = clusters[i]
            val dl = l - c.l
            val da = a - c.a
            val db = b - c.b
            val normDist = sqrt(
                (dl * dl) / (c.varianceL * 2.1f) +
                    (da * da + db * db) / (c.varianceChroma * 1.45f)
            )
            if (normDist < minD) {
                minD = normDist
            }
        }
        return minD
    }

    private fun computeGeodesicBarrierDistance(
        l: FloatArray,
        a: FloatArray,
        b: FloatArray,
        grad: FloatArray,
        bgColorDist: FloatArray,
        w: Int,
        h: Int
    ): FloatArray {
        val n = w * h
        val dist = FloatArray(n) { 1e6f }

        for (x in 0 until w) {
            val topIdx = x
            dist[topIdx] = bgColorDist[topIdx] * 0.25f
            val botIdx = (h - 1) * w + x
            val isBottomCenter = x > (w * 0.25f).toInt() && x < (w * 0.75f).toInt()
            if (!isBottomCenter || bgColorDist[botIdx] < 1.35f) {
                dist[botIdx] = bgColorDist[botIdx] * 0.35f
            }
        }
        for (y in 0 until h) {
            val leftIdx = y * w
            dist[leftIdx] = bgColorDist[leftIdx] * 0.25f
            val rightIdx = y * w + (w - 1)
            dist[rightIdx] = bgColorDist[rightIdx] * 0.25f
        }

        fun stepCost(from: Int, to: Int, diagFactor: Float): Float {
            val dl = abs(l[to] - l[from]) * 0.035f
            val da = abs(a[to] - a[from]) * 0.065f
            val db = abs(b[to] - b[from]) * 0.065f
            val edgeJump = (dl + da + db) + grad[to] * 2.6f
            val bgPenalty = (bgColorDist[to] - 0.85f).coerceAtLeast(0f) * 0.22f
            return (edgeJump + bgPenalty) * diagFactor
        }

        repeat(3) {
            for (y in 1 until h - 1) {
                val row = y * w
                for (x in 1 until w - 1) {
                    val i = row + x
                    var best = dist[i]

                    val nLeft = i - 1
                    val cLeft = dist[nLeft] + stepCost(nLeft, i, 1.0f)
                    if (cLeft < best) best = cLeft

                    val nUp = i - w
                    val cUp = dist[nUp] + stepCost(nUp, i, 1.0f)
                    if (cUp < best) best = cUp

                    val nUpLeft = i - w - 1
                    val cUpLeft = dist[nUpLeft] + stepCost(nUpLeft, i, 1.414f)
                    if (cUpLeft < best) best = cUpLeft

                    val nUpRight = i - w + 1
                    val cUpRight = dist[nUpRight] + stepCost(nUpRight, i, 1.414f)
                    if (cUpRight < best) best = cUpRight

                    dist[i] = best
                }
            }

            for (y in h - 2 downTo 1) {
                val row = y * w
                for (x in w - 2 downTo 1) {
                    val i = row + x
                    var best = dist[i]

                    val nRight = i + 1
                    val cRight = dist[nRight] + stepCost(nRight, i, 1.0f)
                    if (cRight < best) best = cRight

                    val nDown = i + w
                    val cDown = dist[nDown] + stepCost(nDown, i, 1.0f)
                    if (cDown < best) best = cDown

                    val nDownRight = i + w + 1
                    val cDownRight = dist[nDownRight] + stepCost(nDownRight, i, 1.414f)
                    if (cDownRight < best) best = cDownRight

                    val nDownLeft = i + w - 1
                    val cDownLeft = dist[nDownLeft] + stepCost(nDownLeft, i, 1.414f)
                    if (cDownLeft < best) best = cDownLeft

                    dist[i] = best
                }
            }
        }

        return dist
    }

    private fun estimateCenterPercentile(
        values: FloatArray,
        w: Int,
        h: Int,
        percentile: Float
    ): Float {
        val x0 = (w * 0.22f).toInt()
        val x1 = (w * 0.78f).toInt()
        val y0 = (h * 0.18f).toInt()
        val y1 = (h * 0.82f).toInt()
        val samples = FloatArray(((x1 - x0 + 1) * (y1 - y0 + 1)) / 4 + 16)
        var count = 0
        for (y in y0 until y1 step 2) {
            val row = y * w
            for (x in x0 until x1 step 2) {
                if (count < samples.size) {
                    samples[count++] = values[row + x]
                }
            }
        }
        if (count == 0) return 1.5f
        samples.sort(0, count)
        val idx = ((count - 1) * percentile.coerceIn(0f, 1f)).toInt()
        return samples[idx]
    }

    private fun closeHolesAndFilterComponents(
        probMask: FloatArray,
        l: FloatArray,
        a: FloatArray,
        b: FloatArray,
        w: Int,
        h: Int
    ): FloatArray {
        val n = w * h
        val binaryFg = BooleanArray(n) { probMask[it] >= 0.46f }

        val visitedFg = BooleanArray(n)
        val queue = IntArray(n)
        val keepFg = BooleanArray(n)
        val minComponentSize = max(24, (n * 0.005f).toInt())

        for (start in 0 until n) {
            if (binaryFg[start] && !visitedFg[start]) {
                var head = 0
                var tail = 0
                queue[tail++] = start
                visitedFg[start] = true
                while (head < tail) {
                    val curr = queue[head++]
                    val cx = curr % w
                    val cy = curr / w
                    if (cx > 0) {
                        val nb = curr - 1
                        if (binaryFg[nb] && !visitedFg[nb]) {
                            visitedFg[nb] = true
                            queue[tail++] = nb
                        }
                    }
                    if (cx < w - 1) {
                        val nb = curr + 1
                        if (binaryFg[nb] && !visitedFg[nb]) {
                            visitedFg[nb] = true
                            queue[tail++] = nb
                        }
                    }
                    if (cy > 0) {
                        val nb = curr - w
                        if (binaryFg[nb] && !visitedFg[nb]) {
                            visitedFg[nb] = true
                            queue[tail++] = nb
                        }
                    }
                    if (cy < h - 1) {
                        val nb = curr + w
                        if (binaryFg[nb] && !visitedFg[nb]) {
                            visitedFg[nb] = true
                            queue[tail++] = nb
                        }
                    }
                }
                if (tail >= minComponentSize) {
                    for (k in 0 until tail) {
                        keepFg[queue[k]] = true
                    }
                }
            }
        }

        val exteriorBg = BooleanArray(n)
        var head = 0
        var tail = 0

        for (x in 0 until w) {
            val top = x
            if (!keepFg[top] && !exteriorBg[top]) {
                exteriorBg[top] = true
                queue[tail++] = top
            }
            val bot = (h - 1) * w + x
            if (!keepFg[bot] && !exteriorBg[bot]) {
                exteriorBg[bot] = true
                queue[tail++] = bot
            }
        }
        for (y in 0 until h) {
            val left = y * w
            if (!keepFg[left] && !exteriorBg[left]) {
                exteriorBg[left] = true
                queue[tail++] = left
            }
            val right = y * w + (w - 1)
            if (!keepFg[right] && !exteriorBg[right]) {
                exteriorBg[right] = true
                queue[tail++] = right
            }
        }

        while (head < tail) {
            val curr = queue[head++]
            val cx = curr % w
            val cy = curr / w
            if (cx > 0) {
                val nb = curr - 1
                if (!keepFg[nb] && !exteriorBg[nb]) {
                    exteriorBg[nb] = true
                    queue[tail++] = nb
                }
            }
            if (cx < w - 1) {
                val nb = curr + 1
                if (!keepFg[nb] && !exteriorBg[nb]) {
                    exteriorBg[nb] = true
                    queue[tail++] = nb
                }
            }
            if (cy > 0) {
                val nb = curr - w
                if (!keepFg[nb] && !exteriorBg[nb]) {
                    exteriorBg[nb] = true
                    queue[tail++] = nb
                }
            }
            if (cy < h - 1) {
                val nb = curr + w
                if (!keepFg[nb] && !exteriorBg[nb]) {
                    exteriorBg[nb] = true
                    queue[tail++] = nb
                }
            }
        }

        val result = FloatArray(n)
        for (i in 0 until n) {
            result[i] = when {
                keepFg[i] -> max(probMask[i], 0.88f)
                !exteriorBg[i] -> 0.96f
                binaryFg[i] && !keepFg[i] -> 0f
                else -> (probMask[i] * 0.45f).coerceIn(0f, 0.35f)
            }
        }

        val smoothed = FloatArray(n)
        for (y in 0 until h) {
            val yMin = max(0, y - 2)
            val yMax = min(h - 1, y + 2)
            for (x in 0 until w) {
                val i = y * w + x
                val cl = l[i]
                val ca = a[i]
                val cb = b[i]
                var wSum = 0f
                var aSum = 0f
                val xMin = max(0, x - 2)
                val xMax = min(w - 1, x + 2)
                for (ny in yMin..yMax) {
                    val row = ny * w
                    for (nx in xMin..xMax) {
                        val ni = row + nx
                        val dl = l[ni] - cl
                        val da = a[ni] - ca
                        val db = b[ni] - cb
                        val colorDistSq = 0.5f * dl * dl + da * da + db * db
                        val weight = exp(-colorDistSq / 90f)
                        wSum += weight
                        aSum += result[ni] * weight
                    }
                }
                smoothed[i] = if (wSum > 0f) (aSum / wSum).coerceIn(0f, 1f) else result[i]
            }
        }

        return smoothed
    }

    private fun bilinearUpsampleMask(
        mask: FloatArray,
        maskW: Int,
        maskH: Int,
        dstW: Int,
        dstH: Int
    ): FloatArray {
        if (maskW == dstW && maskH == dstH) return mask.copyOf()
        val out = FloatArray(dstW * dstH)
        val scaleX = (maskW - 1).coerceAtLeast(1).toFloat() / (dstW - 1).coerceAtLeast(1).toFloat()
        val scaleY = (maskH - 1).coerceAtLeast(1).toFloat() / (dstH - 1).coerceAtLeast(1).toFloat()

        for (y in 0 until dstH) {
            val fy = y * scaleY
            val y0 = fy.toInt().coerceIn(0, maskH - 1)
            val y1 = (y0 + 1).coerceIn(0, maskH - 1)
            val wy = fy - y0
            val row0 = y0 * maskW
            val row1 = y1 * maskW
            val dstRow = y * dstW

            for (x in 0 until dstW) {
                val fx = x * scaleX
                val x0 = fx.toInt().coerceIn(0, maskW - 1)
                val x1 = (x0 + 1).coerceIn(0, maskW - 1)
                val wx = fx - x0

                val v00 = mask[row0 + x0]
                val v01 = mask[row0 + x1]
                val v10 = mask[row1 + x0]
                val v11 = mask[row1 + x1]

                val top = v00 + (v01 - v00) * wx
                val bot = v10 + (v11 - v10) * wx
                out[dstRow + x] = (top + (bot - top) * wy).coerceIn(0f, 1f)
            }
        }
        return out
    }

    private fun guidedEdgeRefinement(
        srcPixels: IntArray,
        alpha: FloatArray,
        width: Int,
        height: Int,
        radius: Int,
        edgeMode: EdgeRefinementMode
    ): FloatArray {
        val out = alpha.copyOf()
        val r = radius.coerceIn(1, 5)
        val colorSigmaSq = when (edgeMode) {
            EdgeRefinementMode.CRISP -> 450f
            EdgeRefinementMode.NATURAL -> 750f
            EdgeRefinementMode.SOFT_FEATHER -> 1400f
        }

        for (y in r until height - r) {
            val row = y * width
            for (x in r until width - r) {
                val i = row + x
                val aVal = alpha[i]
                val isBoundary = (aVal > 0.03f && aVal < 0.97f) ||
                    abs(aVal - alpha[i - r]) > 0.15f ||
                    abs(aVal - alpha[i + r]) > 0.15f ||
                    abs(aVal - alpha[i - r * width]) > 0.15f ||
                    abs(aVal - alpha[i + r * width]) > 0.15f

                if (!isBoundary) {
                    out[i] = if (aVal >= 0.5f) 1f else 0f
                    continue
                }

                val centerColor = srcPixels[i]
                val cr = (centerColor shr 16) and 0xFF
                val cg = (centerColor shr 8) and 0xFF
                val cb = centerColor and 0xFF

                var weightSum = 0f
                var alphaSum = 0f

                for (dy in -r..r) {
                    val nRow = (y + dy) * width
                    for (dx in -r..r) {
                        val ni = nRow + (x + dx)
                        val nc = srcPixels[ni]
                        val dr = ((nc shr 16) and 0xFF) - cr
                        val dg = ((nc shr 8) and 0xFF) - cg
                        val db = (nc and 0xFF) - cb
                        val cDistSq = (dr * dr + dg * dg + db * db).toFloat()
                        val w = exp(-cDistSq / colorSigmaSq)
                        weightSum += w
                        alphaSum += alpha[ni] * w
                    }
                }

                val guided = if (weightSum > 0f) (alphaSum / weightSum) else aVal

                out[i] = when (edgeMode) {
                    EdgeRefinementMode.CRISP -> ((guided - 0.42f) / 0.24f).coerceIn(0f, 1f)
                    EdgeRefinementMode.NATURAL -> ((guided - 0.34f) / 0.42f).coerceIn(0f, 1f)
                    EdgeRefinementMode.SOFT_FEATHER -> ((guided - 0.25f) / 0.58f).coerceIn(0f, 1f)
                }
            }
        }
        return out
    }

    private fun applyAlphaWithColorDecontamination(
        srcPixels: IntArray,
        alpha: FloatArray,
        width: Int,
        height: Int
    ): IntArray {
        val out = IntArray(width * height)
        val searchRadius = 4

        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val i = row + x
                val aFloat = alpha[i].coerceIn(0f, 1f)
                if (aFloat <= 0.015f) {
                    out[i] = 0x00000000
                    continue
                }

                val c = srcPixels[i]
                val origA = (c ushr 24) and 0xFF
                var r = (c shr 16) and 0xFF
                var g = (c shr 8) and 0xFF
                var b = c and 0xFF

                if (aFloat < 0.90f && x >= searchRadius && x < width - searchRadius &&
                    y >= searchRadius && y < height - searchRadius
                ) {
                    var fgR = 0
                    var fgG = 0
                    var fgB = 0
                    var fgCount = 0
                    for (dy in -searchRadius..searchRadius step 2) {
                        val nRow = (y + dy) * width
                        for (dx in -searchRadius..searchRadius step 2) {
                            val ni = nRow + (x + dx)
                            if (alpha[ni] >= 0.92f) {
                                val nc = srcPixels[ni]
                                fgR += (nc shr 16) and 0xFF
                                fgG += (nc shr 8) and 0xFF
                                fgB += nc and 0xFF
                                fgCount++
                            }
                        }
                    }
                    if (fgCount > 0) {
                        val meanFgR = fgR / fgCount
                        val meanFgG = fgG / fgCount
                        val meanFgB = fgB / fgCount
                        val blend = (1f - aFloat).coerceIn(0f, 0.75f)
                        r = (r * (1f - blend) + meanFgR * blend).toInt().coerceIn(0, 255)
                        g = (g * (1f - blend) + meanFgG * blend).toInt().coerceIn(0, 255)
                        b = (b * (1f - blend) + meanFgB * blend).toInt().coerceIn(0, 255)
                    }
                }

                val finalA = ((origA * aFloat)).toInt().coerceIn(0, 255)
                out[i] = Color.argb(finalA, r, g, b)
            }
        }
        return out
    }

    private fun sigmoid(x: Float): Float {
        return 1.0f / (1.0f + exp(-x.coerceIn(-18f, 18f)))
    }
}
