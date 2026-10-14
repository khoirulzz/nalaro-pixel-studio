package com.nalaro.pixelstudio.core.studio

import android.graphics.*
import kotlin.math.cos
import kotlin.math.sin

sealed class BackgroundStyle {
    data class Solid(val color: Int) : BackgroundStyle()
    data class Gradient(
        val startColor: Int,
        val endColor: Int,
        val angleDegrees: Float = 0f
    ) : BackgroundStyle()
    data class Image(
        val backgroundBitmap: Bitmap,
        val blurRadius: Float = 0f
    ) : BackgroundStyle()
}

data class SubjectShadow(
    val color: Int = Color.BLACK,
    val opacity: Float = 0.4f,
    val radius: Float = 16f,
    val dx: Float = 0f,
    val dy: Float = 12f
)

data class SubjectPlacement(
    val scale: Float = 1.0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f
)

class BackgroundStudioEngine {

    fun composite(
        subjectCutout: Bitmap,
        background: BackgroundStyle,
        placement: SubjectPlacement = SubjectPlacement(),
        shadow: SubjectShadow? = null,
        canvasWidth: Int = subjectCutout.width,
        canvasHeight: Int = subjectCutout.height
    ): Bitmap {
        val safeW = canvasWidth.coerceAtLeast(1)
        val safeH = canvasHeight.coerceAtLeast(1)
        val result = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 1. Draw Background
        when (background) {
            is BackgroundStyle.Solid -> {
                canvas.drawColor(background.color)
            }
            is BackgroundStyle.Gradient -> {
                val rad = Math.toRadians(background.angleDegrees.toDouble())
                val x1 = safeW / 2f - (cos(rad) * safeW / 2f).toFloat()
                val y1 = safeH / 2f - (sin(rad) * safeH / 2f).toFloat()
                val x2 = safeW / 2f + (cos(rad) * safeW / 2f).toFloat()
                val y2 = safeH / 2f + (sin(rad) * safeH / 2f).toFloat()

                val shader = LinearGradient(
                    x1, y1, x2, y2,
                    background.startColor, background.endColor,
                    Shader.TileMode.CLAMP
                )
                val paint = Paint().apply { this.shader = shader }
                canvas.drawRect(0f, 0f, safeW.toFloat(), safeH.toFloat(), paint)
            }
            is BackgroundStyle.Image -> {
                val srcRect = Rect(0, 0, background.backgroundBitmap.width, background.backgroundBitmap.height)
                val dstRect = Rect(0, 0, safeW, safeH)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(background.backgroundBitmap, srcRect, dstRect, paint)
            }
        }

        // 2. Calculate Subject Transformation
        val scaledW = subjectCutout.width * placement.scale
        val scaledH = subjectCutout.height * placement.scale
        val posX = (safeW - scaledW) / 2f + placement.offsetX
        val posY = (safeH - scaledH) / 2f + placement.offsetY

        val destRect = RectF(posX, posY, posX + scaledW, posY + scaledH)

        // 3. Draw Shadow if requested
        if (shadow != null && shadow.opacity > 0f) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                val alpha = (shadow.opacity * 255).toInt().coerceIn(0, 255)
                color = Color.argb(alpha, Color.red(shadow.color), Color.green(shadow.color), Color.blue(shadow.color))
                maskFilter = BlurMaskFilter(shadow.radius.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            }
            val shadowRect = RectF(
                destRect.left + shadow.dx,
                destRect.top + shadow.dy,
                destRect.right + shadow.dx,
                destRect.bottom + shadow.dy
            )
            val alphaMask = subjectCutout.extractAlpha()
            try {
                canvas.drawBitmap(alphaMask, null, shadowRect, shadowPaint)
            } finally {
                alphaMask.recycle()
            }
        }

        // 4. Draw Subject Cutout
        val subjectPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmap(subjectCutout, null, destRect, subjectPaint)

        return result
    }
}
