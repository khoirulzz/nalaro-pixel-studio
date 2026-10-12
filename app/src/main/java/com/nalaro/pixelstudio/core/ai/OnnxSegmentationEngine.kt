package com.nalaro.pixelstudio.core.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer

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
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun segmentBackground(
        sourceBitmap: Bitmap,
        inputWidth: Int = 1024,
        inputHeight: Int = 1024
    ): Bitmap = withContext(Dispatchers.Default) {
        val session = currentSession

        if (session != null && ortEnvironment != null) {
            try {
                val resized = BitmapUtils.createResizedBitmap(sourceBitmap, inputWidth, inputHeight)
                val inputTensor = preprocessImage(resized, inputWidth, inputHeight, ortEnvironment!!)

                val inputNames = session.inputNames
                val inputName = inputNames.firstOrNull() ?: "input"

                val results = session.run(mapOf(inputName to inputTensor))
                val outputTensor = results.firstOrNull()?.value as? Array<Array<Array<FloatArray>>>

                if (outputTensor != null) {
                    val rawMask = FloatArray(inputWidth * inputHeight)
                    for (y in 0 until inputHeight) {
                        for (x in 0 until inputWidth) {
                            rawMask[y * inputWidth + x] = outputTensor[0][0][y][x].coerceIn(0f, 1f)
                        }
                    }
                    return@withContext BitmapUtils.applyAlphaMask(sourceBitmap, rawMask, inputWidth, inputHeight)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Offline Fallback Segmentation for mock/development/edge cases
        return@withContext generateFallbackCutout(sourceBitmap)
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

        // Standard ImageNet normalization: Mean = [0.485, 0.456, 0.406], Std = [0.229, 0.224, 0.225]
        val meanR = 0.485f
        val meanG = 0.456f
        val meanB = 0.406f
        val stdR = 0.229f
        val stdG = 0.224f
        val stdB = 0.225f

        // Planar format [1, 3, H, W]
        // R plane
        for (i in 0 until numPixels) {
            val color = pixels[i]
            val r = (Color.red(color) / 255.0f - meanR) / stdR
            floatBuffer.put(r)
        }
        // G plane
        for (i in 0 until numPixels) {
            val color = pixels[i]
            val g = (Color.green(color) / 255.0f - meanG) / stdG
            floatBuffer.put(g)
        }
        // B plane
        for (i in 0 until numPixels) {
            val color = pixels[i]
            val b = (Color.blue(color) / 255.0f - meanB) / stdB
            floatBuffer.put(b)
        }

        floatBuffer.flip()
        val shape = longArrayOf(1, 3, height.toLong(), width.toLong())
        return OnnxTensor.createTensor(env, floatBuffer, shape)
    }

    private fun generateFallbackCutout(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val centerX = width / 2f
        val centerY = height / 2f
        val radiusX = width * 0.44f
        val radiusY = height * 0.44f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = (x - centerX) / radiusX
                val dy = (y - centerY) / radiusY
                val dist = (dx * dx + dy * dy)
                val alphaFactor = (1f - dist.coerceIn(0f, 1f)).coerceIn(0f, 1f)

                val idx = y * width + x
                val originalColor = pixels[idx]
                val currentAlpha = Color.alpha(originalColor)
                val finalAlpha = (currentAlpha * alphaFactor).toInt()

                pixels[idx] = Color.argb(
                    finalAlpha,
                    Color.red(originalColor),
                    Color.green(originalColor),
                    Color.blue(originalColor)
                )
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    fun closeSession() {
        try {
            currentSession?.close()
            currentSession = null
            currentModelPath = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
