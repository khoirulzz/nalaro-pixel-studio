package com.nalaro.pixelstudio.core.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import kotlin.coroutines.resume
import kotlin.math.exp
import kotlin.math.max

data class SegmentationOutcome(
    val bitmap: Bitmap,
    val engineLabel: String,
    val elapsedMs: Long
)

class OnnxSegmentationEngine(private val context: Context) {

    private var ortEnvironment: OrtEnvironment? = null
    private var currentSession: OrtSession? = null
    private var currentModelPath: String? = null

    suspend fun loadModel(modelFilePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(modelFilePath)
        if (!file.exists() || file.length() == 0L) {
            return@withContext false
        }

        try {
            if (currentModelPath == modelFilePath && currentSession != null) {
                return@withContext true
            }

            closeSession()

            val env = OrtEnvironment.getEnvironment()
            ortEnvironment = env

            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            currentSession = env.createSession(modelFilePath, sessionOptions)
            currentModelPath = modelFilePath
            true
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            t.printStackTrace()
            false
        }
    }

    suspend fun segmentBackground(
        sourceBitmap: Bitmap,
        inputWidth: Int = 1024,
        inputHeight: Int = 1024
    ): Bitmap {
        return segmentBackgroundDetailed(
            sourceBitmap = sourceBitmap,
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            edgeMode = EdgeRefinementMode.NATURAL,
            highPrecision = true
        ).bitmap
    }

    /**
     * Multi-tier offline segmentation pipeline:
     * 1. Downloaded Hugging Face ONNX Model (BiRefNet / U2Net) if installed + Guided Edge Matting.
     * 2. Built-in On-Device Neural Segmenter (Google ML Kit Subject Segmentation & Bundled 4.5MB Offline Neural Segmenter)
     *    fused with LocalSmartCutoutEngine (CIE Lab Geodesic + Border GMM + Guided Edge Feathering & Color Decontamination).
     * 3. Pure LocalSmartCutoutEngine fallback for 100% offline / non-Play-Services environments.
     */
    suspend fun segmentBackgroundDetailed(
        sourceBitmap: Bitmap,
        inputWidth: Int = 1024,
        inputHeight: Int = 1024,
        edgeMode: EdgeRefinementMode = EdgeRefinementMode.NATURAL,
        highPrecision: Boolean = true
    ): SegmentationOutcome = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val session = currentSession
        val env = ortEnvironment

        // Tier 1: Downloaded ONNX Model (BiRefNet / U2Net)
        if (session != null && env != null) {
            try {
                val resized = BitmapUtils.createResizedBitmap(sourceBitmap, inputWidth, inputHeight)
                val inputTensor = preprocessImage(resized, inputWidth, inputHeight, env)
                if (resized !== sourceBitmap) {
                    resized.recycle()
                }

                inputTensor.use { tensor ->
                    val inputName = session.inputNames.firstOrNull() ?: "input"
                    session.run(mapOf(inputName to tensor)).use { results ->
                        val firstOnnxValue = results.firstOrNull()?.value
                        val rawMask = extractNormalizedMask(firstOnnxValue, inputWidth, inputHeight)
                        if (rawMask != null) {
                            val cutout = LocalSmartCutoutEngine.refineAndApplyMask(
                                source = sourceBitmap,
                                rawMask = rawMask,
                                maskW = inputWidth,
                                maskH = inputHeight,
                                edgeMode = edgeMode
                            )
                            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                            return@withContext SegmentationOutcome(
                                bitmap = cutout,
                                engineLabel = "ONNX Neural Engine",
                                elapsedMs = elapsed
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }

        // Tier 2A: On-Device ML Kit Subject Segmentation (Objects, Products, Pets, People)
        val subjectMask = runMlKitSubjectSegmentation(sourceBitmap)
        if (subjectMask != null && isMaskValid(subjectMask.mask)) {
            val cutout = LocalSmartCutoutEngine.refineAndApplyMask(
                source = sourceBitmap,
                rawMask = subjectMask.mask,
                maskW = subjectMask.width,
                maskH = subjectMask.height,
                edgeMode = edgeMode
            )
            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            return@withContext SegmentationOutcome(
                bitmap = cutout,
                engineLabel = "On-Device AI Subject Segmenter",
                elapsedMs = elapsed
            )
        }

        // Tier 2B + Tier 3 Hybrid: Bundled 4.5MB Offline Neural Segmenter + CIE Lab Geodesic Smart Matting
        val bundledMask = runBundledSelfieSegmentation(sourceBitmap)
        val smartGrid = LocalSmartCutoutEngine.computeSmartSubjectMask(
            source = sourceBitmap,
            maxWorkDimension = if (highPrecision) 420 else 320
        )

        if (bundledMask != null && isMaskValid(bundledMask.mask, minCoverage = 0.03f, maxCoverage = 0.90f)) {
            val resampledNeural = resampleMask(
                bundledMask.mask,
                bundledMask.width,
                bundledMask.height,
                smartGrid.width,
                smartGrid.height
            )
            val fused = FloatArray(smartGrid.mask.size)
            for (i in fused.indices) {
                val nVal = resampledNeural[i]
                val sVal = smartGrid.mask[i]
                fused[i] = if (nVal >= 0.35f) {
                    max(nVal, sVal * 0.85f)
                } else {
                    nVal * 0.6f + sVal * 0.4f
                }
            }
            val cutout = LocalSmartCutoutEngine.refineAndApplyMask(
                source = sourceBitmap,
                rawMask = fused,
                maskW = smartGrid.width,
                maskH = smartGrid.height,
                edgeMode = edgeMode
            )
            val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
            return@withContext SegmentationOutcome(
                bitmap = cutout,
                engineLabel = "Built-in Offline AI Segmenter",
                elapsedMs = elapsed
            )
        }

        // Tier 3: Pure LocalSmartCutoutEngine (CIE Lab Geodesic + Multi-Cluster Border GMM + Guided Edge Feathering)
        val cutout = LocalSmartCutoutEngine.refineAndApplyMask(
            source = sourceBitmap,
            rawMask = smartGrid.mask,
            maskW = smartGrid.width,
            maskH = smartGrid.height,
            edgeMode = edgeMode
        )
        val elapsed = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
        return@withContext SegmentationOutcome(
            bitmap = cutout,
            engineLabel = "Smart Offline Cutout Engine",
            elapsedMs = elapsed
        )
    }

    private suspend fun runMlKitSubjectSegmentation(bitmap: Bitmap): LocalSmartCutoutEngine.MaskGrid? {
        return try {
            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundConfidenceMask()
                .build()
            val segmenter = SubjectSegmentation.getClient(options)
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            suspendCancellableCoroutine { cont ->
                segmenter.process(inputImage)
                    .addOnSuccessListener { result ->
                        try {
                            val maskBuf = result.foregroundConfidenceMask
                            if (maskBuf != null) {
                                maskBuf.rewind()
                                val w = bitmap.width
                                val h = bitmap.height
                                val count = w * h
                                if (maskBuf.remaining() >= count) {
                                    val arr = FloatArray(count)
                                    maskBuf.get(arr, 0, count)
                                    if (cont.isActive) {
                                        cont.resume(LocalSmartCutoutEngine.MaskGrid(arr, w, h))
                                    }
                                } else if (cont.isActive) {
                                    cont.resume(null)
                                }
                            } else if (cont.isActive) {
                                cont.resume(null)
                            }
                        } catch (_: Throwable) {
                            if (cont.isActive) cont.resume(null)
                        } finally {
                            try {
                                segmenter.close()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                    .addOnFailureListener {
                        try {
                            segmenter.close()
                        } catch (_: Throwable) {
                        }
                        if (cont.isActive) cont.resume(null)
                    }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun runBundledSelfieSegmentation(bitmap: Bitmap): LocalSmartCutoutEngine.MaskGrid? {
        return try {
            val options = SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                .enableRawSizeMask()
                .build()
            val segmenter = Segmentation.getClient(options)
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            suspendCancellableCoroutine { cont ->
                segmenter.process(inputImage)
                    .addOnSuccessListener { mask ->
                        try {
                            val w = mask.width
                            val h = mask.height
                            val count = w * h
                            val byteBuf = mask.buffer
                            byteBuf.rewind()
                            val floatBuf = byteBuf.asFloatBuffer()
                            if (w > 0 && h > 0 && floatBuf.remaining() >= count) {
                                val arr = FloatArray(count)
                                floatBuf.get(arr, 0, count)
                                if (cont.isActive) {
                                    cont.resume(LocalSmartCutoutEngine.MaskGrid(arr, w, h))
                                }
                            } else if (cont.isActive) {
                                cont.resume(null)
                            }
                        } catch (_: Throwable) {
                            if (cont.isActive) cont.resume(null)
                        } finally {
                            try {
                                segmenter.close()
                            } catch (_: Throwable) {
                            }
                        }
                    }
                    .addOnFailureListener {
                        try {
                            segmenter.close()
                        } catch (_: Throwable) {
                        }
                        if (cont.isActive) cont.resume(null)
                    }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun isMaskValid(
        mask: FloatArray,
        minCoverage: Float = 0.015f,
        maxCoverage: Float = 0.95f
    ): Boolean {
        if (mask.isEmpty()) return false
        var fgCount = 0
        for (v in mask) {
            if (v >= 0.45f) fgCount++
        }
        val ratio = fgCount.toFloat() / mask.size.toFloat()
        return ratio in minCoverage..maxCoverage
    }

    private fun resampleMask(
        src: FloatArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): FloatArray {
        if (srcW == dstW && srcH == dstH) return src
        val out = FloatArray(dstW * dstH)
        for (y in 0 until dstH) {
            val sy = (y * srcH / dstH).coerceIn(0, srcH - 1)
            val sRow = sy * srcW
            val dRow = y * dstW
            for (x in 0 until dstW) {
                val sx = (x * srcW / dstW).coerceIn(0, srcW - 1)
                out[dRow + x] = src[sRow + sx]
            }
        }
        return out
    }

    private fun extractNormalizedMask(
        onnxValue: ai.onnxruntime.OnnxValue?,
        inputWidth: Int,
        inputHeight: Int
    ): FloatArray? {
        val expectedSize = inputWidth * inputHeight
        val raw = FloatArray(expectedSize)

        if (onnxValue is OnnxTensor) {
            val floatBuf = onnxValue.floatBuffer ?: return null
            if (floatBuf.remaining() < expectedSize) return null
            floatBuf.get(raw, 0, expectedSize)
        } else {
            val valueObj = onnxValue?.value ?: return null
            @Suppress("UNCHECKED_CAST")
            when (valueObj) {
                is Array<*> -> {
                    val d4 = valueObj as? Array<Array<Array<FloatArray>>>
                    if (d4 != null && d4.isNotEmpty() && d4[0].isNotEmpty()) {
                        val plane = d4[0][0]
                        for (y in 0 until inputHeight.coerceAtMost(plane.size)) {
                            val row = plane[y]
                            for (x in 0 until inputWidth.coerceAtMost(row.size)) {
                                raw[y * inputWidth + x] = row[x]
                            }
                        }
                    } else {
                        return null
                    }
                }
                else -> return null
            }
        }

        var minVal = Float.POSITIVE_INFINITY
        var maxVal = Float.NEGATIVE_INFINITY
        for (v in raw) {
            if (v < minVal) minVal = v
            if (v > maxVal) maxVal = v
        }

        if (minVal < -0.01f || maxVal > 1.01f) {
            var sMin = Float.POSITIVE_INFINITY
            var sMax = Float.NEGATIVE_INFINITY
            for (i in raw.indices) {
                val sig = (1.0f / (1.0f + exp(-raw[i].coerceIn(-20f, 20f))))
                raw[i] = sig
                if (sig < sMin) sMin = sig
                if (sig > sMax) sMax = sig
            }
            val range = (sMax - sMin).takeIf { it > 1e-5f } ?: 1f
            for (i in raw.indices) {
                raw[i] = ((raw[i] - sMin) / range).coerceIn(0f, 1f)
            }
        } else {
            val range = (maxVal - minVal).takeIf { it > 1e-5f } ?: 1f
            for (i in raw.indices) {
                raw[i] = ((raw[i] - minVal) / range).coerceIn(0f, 1f)
            }
        }

        return raw
    }

    private fun preprocessImage(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        env: OrtEnvironment
    ): OnnxTensor {
        val numPixels = width * height
        val floatBuffer = FloatBuffer.allocate(3 * numPixels)

        val pixels = IntArray(numPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val meanR = 0.485f
        val meanG = 0.456f
        val meanB = 0.406f
        val stdR = 0.229f
        val stdG = 0.224f
        val stdB = 0.225f

        for (i in 0 until numPixels) {
            val color = pixels[i]
            val r = (Color.red(color) / 255.0f - meanR) / stdR
            floatBuffer.put(r)
        }
        for (i in 0 until numPixels) {
            val color = pixels[i]
            val g = (Color.green(color) / 255.0f - meanG) / stdG
            floatBuffer.put(g)
        }
        for (i in 0 until numPixels) {
            val color = pixels[i]
            val b = (Color.blue(color) / 255.0f - meanB) / stdB
            floatBuffer.put(b)
        }

        floatBuffer.flip()
        val shape = longArrayOf(1, 3, height.toLong(), width.toLong())
        return OnnxTensor.createTensor(env, floatBuffer, shape)
    }

    fun closeSession() {
        try {
            currentSession?.close()
            currentSession = null
            currentModelPath = null
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}
