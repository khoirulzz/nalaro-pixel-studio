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
        val result = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // 1. Draw Background
        when (background) {
            is BackgroundStyle.Solid -> {
                canvas.drawColor(background.color)
            }
            is BackgroundStyle.Gradient -> {
                val rad = Math.toRadians(background.angleDegrees.toDouble())
                val x1 = canvasWidth / 2f - (cos(rad) * canvasWidth / 2f).toFloat()
                val y1 = canvasHeight / 2f - (sin(rad) * canvasHeight / 2f).toFloat()
                val x2 = canvasWidth / 2f + (cos(rad) * canvasWidth / 2f).toFloat()
                val y2 = canvasHeight / 2f + (sin(rad) * canvasHeight / 2f).toFloat()

                val shader = LinearGradient(
                    x1, y1, x2, y2,
                    background.startColor, background.endColor,
                    Shader.TileMode.CLAMP
                )
                val paint = Paint().apply { this.shader = shader }
                canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), paint)
            }
            is BackgroundStyle.Image -> {
                val srcRect = Rect(0, 0, background.backgroundBitmap.width, background.backgroundBitmap.height)
                val dstRect = Rect(0, 0, canvasWidth, canvasHeight)
                val paint = Paint(Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(background.backgroundBitmap, srcRect, dstRect, paint)
            }
        }

        // 2. Calculate Subject Transformation
        val scaledW = subjectCutout.width * placement.scale
        val scaledH = subjectCutout.height * placement.scale
        val posX = (canvasWidth - scaledW) / 2f + placement.offsetX
        val posY = (canvasHeight - scaledH) / 2f + placement.offsetY

        val destRect = RectF(posX, posY, posX + scaledW, posY + scaledH)

        // 3. Draw Shadow if requested
        if (shadow != null && shadow.opacity > 0f) {
            val shadowPaint = Paint().apply {
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
            // Extract alpha mask of subject for shadow
            val alphaMask = subjectCutout.extractAlpha()
            canvas.drawBitmap(alphaMask, null, shadowRect, shadowPaint)
        }

        // 4. Draw Subject Cutout
        val subjectPaint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(subjectCutout, null, destRect, subjectPaint)

        return result
    }
}
