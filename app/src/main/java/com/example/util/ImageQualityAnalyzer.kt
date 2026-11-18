package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import kotlin.math.abs
import kotlin.math.roundToInt

data class ImageQualityReport(
    val sharpnessScore: Double = 100.0,
    val isBlurry: Boolean = false,
    val meanBrightness: Double = 128.0,
    val isTooDark: Boolean = false,
    val isTooBright: Boolean = false,
    val exifRotationDegrees: Float = 0f,
    val qualityWarning: String? = null
)

object ImageQualityAnalyzer {

    /**
     * Reads EXIF orientation rotation from a content/file Uri so rotated photos auto-orient before OCR.
     */
    fun getExifRotationDegrees(context: Context, uri: Uri): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }
    }

    /**
     * Measures image sharpness (Laplacian edge variance) and brightness on a scaled grayscale sample.
     * Detects blurry receipts, dark booths, or glare/overexposure before OCR trusts numbers.
     */
    fun analyzeBitmapQuality(bitmap: Bitmap, exifRotation: Float = 0f): ImageQualityReport {
        val sampleWidth = minOf(320, bitmap.width).coerceAtLeast(16)
        val ratio = sampleWidth.toFloat() / bitmap.width.coerceAtLeast(1).toFloat()
        val sampleHeight = (bitmap.height * ratio).toInt().coerceAtLeast(16)

        val scaled = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, true)
        val pixels = IntArray(sampleWidth * sampleHeight)
        scaled.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)

        val gray = DoubleArray(pixels.size)
        var sumLum = 0.0
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            gray[i] = lum
            sumLum += lum
        }
        val meanBrightness = sumLum / gray.size.coerceAtLeast(1)

        // Laplacian variance for focus/sharpness detection
        var lapSum = 0.0
        var lapSqSum = 0.0
        var count = 0
        for (y in 1 until sampleHeight - 1) {
            val rowOffset = y * sampleWidth
            for (x in 1 until sampleWidth - 1) {
                val idx = rowOffset + x
                val lap = 4.0 * gray[idx] -
                    gray[idx - 1] -
                    gray[idx + 1] -
                    gray[idx - sampleWidth] -
                    gray[idx + sampleWidth]
                lapSum += lap
                lapSqSum += lap * lap
                count++
            }
        }

        val variance = if (count > 0) {
            val meanLap = lapSum / count
            (lapSqSum / count) - (meanLap * meanLap)
        } else 0.0

        // Normalize sharpness into 0-100+ range. Thermal slips/LCDs with clear edges normally score >= 45
        val sharpnessScore = (variance / 8.0).coerceIn(0.0, 200.0)
        val isBlurry = sharpnessScore < 18.0
        val isTooDark = meanBrightness < 55.0
        val isTooBright = meanBrightness > 235.0

        val warnings = mutableListOf<String>()
        if (isBlurry) {
            warnings.add("फोटो धुंधली (Blurry) है (Sharpness: ${sharpnessScore.toInt()}) — कृपया स्थिर और साफ फोटो लें")
        }
        if (isTooDark) {
            warnings.add("रोशनी बहुत कम है (Low Brightness) — फ्लैश चालू करें या Brightness सुधारें")
        }
        if (isTooBright) {
            warnings.add("रोशनी/चमक बहुत अधिक है (Overexposed Glare)")
        }

        return ImageQualityReport(
            sharpnessScore = Math.round(sharpnessScore * 10.0) / 10.0,
            isBlurry = isBlurry,
            meanBrightness = Math.round(meanBrightness * 10.0) / 10.0,
            isTooDark = isTooDark,
            isTooBright = isTooBright,
            exifRotationDegrees = exifRotation,
            qualityWarning = if (warnings.isNotEmpty()) warnings.joinToString(" | ") else null
        )
    }

    /**
     * Enhances bitmap for OCR: applies rotation, adaptive brightness lift, grayscale, and high-contrast boost.
     */
    fun enhanceForOcr(
        original: Bitmap,
        totalRotationDegrees: Float = 0f,
        enhanceContrast: Boolean = true,
        brightnessOffset: Float = 0f,
        contrastScale: Float = 1.38f,
        autoBrightnessLift: Boolean = true
    ): Bitmap {
        val rotated = if (abs(totalRotationDegrees % 360f) > 0.5f) {
            val matrix = Matrix().apply { postRotate(totalRotationDegrees) }
            Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        } else {
            original
        }

        if (!enhanceContrast && abs(brightnessOffset) < 1f) return rotated

        val quality = analyzeBitmapQuality(rotated)
        val adaptiveLift = when {
            !autoBrightnessLift -> 0f
            quality.isTooDark -> 45f
            quality.meanBrightness < 95.0 -> 20f
            else -> 0f
        }

        val output = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val cm = ColorMatrix()
        if (enhanceContrast) {
            cm.setSaturation(0f) // Grayscale for thermal receipts & digital segments
        }
        val scale = if (enhanceContrast) contrastScale else 1.0f
        val translate = (-0.5f * scale + 0.5f) * 255f + brightnessOffset + adaptiveLift
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(rotated, 0f, 0f, paint)
        return output
    }

    /**
     * Crops a bitmap using normalized [0f..1f] coordinates so operators can isolate a milk analyzer
     * screen, thermal slip, or monthly report table cleanly before running OCR.
     */
    fun cropBitmap(
        original: Bitmap,
        leftFraction: Float = 0f,
        topFraction: Float = 0f,
        rightFraction: Float = 1f,
        bottomFraction: Float = 1f
    ): Bitmap {
        val l = leftFraction.coerceIn(0f, 0.9f)
        val t = topFraction.coerceIn(0f, 0.9f)
        val r = rightFraction.coerceIn(l + 0.1f, 1f)
        val b = bottomFraction.coerceIn(t + 0.1f, 1f)

        if (l <= 0.01f && t <= 0.01f && r >= 0.99f && b >= 0.99f) return original

        val x = (original.width * l).roundToInt().coerceIn(0, original.width - 1)
        val y = (original.height * t).roundToInt().coerceIn(0, original.height - 1)
        val w = (original.width * (r - l)).roundToInt().coerceIn(1, original.width - x)
        val h = (original.height * (b - t)).roundToInt().coerceIn(1, original.height - y)
        return Bitmap.createBitmap(original, x, y, w, h)
    }
}
